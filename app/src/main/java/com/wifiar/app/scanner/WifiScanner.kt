package com.wifiar.app.scanner

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.util.Log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Wraps [WifiManager] and exposes live scan results as a [Flow].
 *
 * Android 9+ throttles WiFi scans (~4 scans per 2 minutes for foreground apps).
 * This class enforces a client-side cooldown so rapid "Scan Now" taps never crash
 * or silently spam the platform API. Remaining cooldown seconds are exposed for UI.
 */
class WifiScanner(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _scanResults = MutableStateFlow<List<RssiSample>>(emptyList())

    /** Live scan results as [RssiSample]s, sorted by RSSI descending when emitted. */
    val scanResults: StateFlow<List<RssiSample>> = _scanResults.asStateFlow()

    /** Same stream typed as [Flow] for collectors that prefer the interface. */
    val scanResultsFlow: Flow<List<RssiSample>> = scanResults

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** Remaining cooldown in whole seconds (0 = ready to scan). */
    private val _cooldownSeconds = MutableStateFlow(0L)
    val cooldownSeconds: StateFlow<Long> = _cooldownSeconds.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * Connected network summary for the analyzer HUD (SSID + link IP when available).
     * Best-effort; Android may blank SSID without location permission.
     */
    data class ConnectedNetwork(
        val ssid: String,
        val bssid: String,
        val ipAddress: String,
        val connected: Boolean,
    )

    fun connectedNetwork(): ConnectedNetwork {
        return runCatching {
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            val rawSsid = info?.ssid
                ?.removePrefix("\"")
                ?.removeSuffix("\"")
                .orEmpty()
            val ssid = when {
                rawSsid.isBlank() || rawSsid == "<unknown ssid>" -> "Unknown network"
                else -> rawSsid
            }
            val bssid = info?.bssid.orEmpty()
            @Suppress("DEPRECATION")
            val ipInt = info?.ipAddress ?: 0
            val ip = if (ipInt != 0) {
                String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff,
                )
            } else {
                "—"
            }
            val connected = wifiManager.isWifiEnabled && ipInt != 0
            ConnectedNetwork(ssid = ssid, bssid = bssid, ipAddress = ip, connected = connected)
        }.getOrElse {
            ConnectedNetwork("Wi‑Fi", "", "—", false)
        }
    }

    private var receiverRegistered = false
    private var cooldownJob: Job? = null

    /** ElapsedRealtime deadline; 0 means no active cooldown. */
    private var cooldownUntilElapsedMs: Long = 0L

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return

            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, true)
            } else {
                true
            }

            _isScanning.value = false

            if (!success) {
                Log.w(
                    TAG,
                    "SCAN_RESULTS_AVAILABLE with EXTRA_RESULTS_UPDATED=false; using cached results",
                )
            }

            emitCurrentScanResults()
        }
    }

    val isWifiEnabled: Boolean
        get() = wifiManager.isWifiEnabled

    /** Android suppresses WiFi scan results when system location is off. */
    val isLocationEnabled: Boolean
        get() {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lm.isLocationEnabled
            } else {
                @Suppress("DEPRECATION")
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        }

    fun start() {
        if (receiverRegistered) return
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(scanReceiver, filter)
        }
        receiverRegistered = true

        // Surface any cached results immediately (helps after permission grant).
        emitCurrentScanResults()
    }

    fun stop() {
        cooldownJob?.cancel()
        cooldownJob = null
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(scanReceiver) }
            receiverRegistered = false
        }
        _isScanning.value = false
    }

    /**
     * Triggers a WiFi scan if the cooldown has elapsed and Wi‑Fi is enabled.
     *
     * @return true if a platform scan was requested, false if throttled / failed / wifi off
     */
    @SuppressLint("MissingPermission")
    fun triggerScan(): Boolean {
        _lastError.value = null

        if (!wifiManager.isWifiEnabled) {
            _lastError.value = ERROR_WIFI_DISABLED
            return false
        }

        if (!isLocationEnabled) {
            _lastError.value = ERROR_LOCATION_DISABLED
            return false
        }

        val remaining = remainingCooldownMs()
        if (remaining > 0L) {
            _cooldownSeconds.value = (remaining + 999L) / 1000L
            _lastError.value = ERROR_THROTTLED
            ensureCooldownTicker()
            return false
        }

        @Suppress("DEPRECATION")
        val started = runCatching { wifiManager.startScan() }.getOrDefault(false)
        if (!started) {
            // Platform may refuse due to system throttling even if our timer is clear.
            Log.w(TAG, "WifiManager.startScan() returned false (likely system throttling)")
            beginCooldown(SYSTEM_THROTTLE_FALLBACK_MS)
            _lastError.value = ERROR_SCAN_FAILED
            // Still emit any cached results so the UI is not empty.
            emitCurrentScanResults()
            return false
        }

        beginCooldown(MIN_SCAN_INTERVAL_MS)
        _isScanning.value = true
        return true
    }

    fun clearError() {
        _lastError.value = null
    }

    @SuppressLint("MissingPermission")
    private fun emitCurrentScanResults() {
        // IMPORTANT (Part 8): return EVERY visible AP from WifiManager.scanResults.
        // Do NOT filter to the currently-connected network — multi-network comparison
        // and fusion require one sample per BSSID at each pose.
        val raw: List<ScanResult> = runCatching { wifiManager.scanResults.orEmpty() }
            .getOrElse {
                Log.e(TAG, "Failed to read scanResults", it)
                emptyList()
            }

        val now = System.currentTimeMillis()
        val samples = raw
            .asSequence()
            .map { it.toRssiSample(fallbackTimestampMs = now) }
            // Keep all SSIDs (including empty/hidden); only drop rows with no BSSID.
            .filter { it.bssid.isNotBlank() }
            .sortedByDescending { it.rssiDbm }
            .toList()

        _scanResults.value = samples
        Log.d(TAG, "Scan emitted ${samples.size} AP(s) (all visible networks)")
    }


    private fun remainingCooldownMs(): Long {
        if (cooldownUntilElapsedMs <= 0L) return 0L
        return (cooldownUntilElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    private fun beginCooldown(durationMs: Long) {
        cooldownUntilElapsedMs = SystemClock.elapsedRealtime() + durationMs
        _cooldownSeconds.value = (durationMs + 999L) / 1000L
        ensureCooldownTicker()
    }

    private fun ensureCooldownTicker() {
        if (cooldownJob?.isActive == true) return
        cooldownJob = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                val remaining = remainingCooldownMs()
                val seconds = (remaining + 999L) / 1000L
                _cooldownSeconds.value = seconds
                if (remaining <= 0L) {
                    cooldownUntilElapsedMs = 0L
                    break
                }
                delay(250L)
            }
            _cooldownSeconds.value = 0L
        }
    }

    companion object {
        private const val TAG = "WifiScanner"

        /**
         * Android 9+ foreground throttle is roughly 4 scans / 2 minutes → 30s spacing.
         * Client-side interval keeps UX predictable and avoids silent startScan failures.
         */
        const val MIN_SCAN_INTERVAL_MS: Long = 30_000L

        /** When the platform refuses a scan, back off briefly before allowing another try. */
        private const val SYSTEM_THROTTLE_FALLBACK_MS: Long = 10_000L

        const val ERROR_WIFI_DISABLED = "wifi_disabled"
        const val ERROR_LOCATION_DISABLED = "location_disabled"
        const val ERROR_THROTTLED = "throttled"
        const val ERROR_SCAN_FAILED = "scan_failed"
    }
}

@SuppressLint("MissingPermission")
private fun ScanResult.toRssiSample(fallbackTimestampMs: Long): RssiSample {
    val ssidValue = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            wifiSsid?.toString()?.trim('"')
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                ?: @Suppress("DEPRECATION") SSID.takeIf { !it.isNullOrBlank() }
                ?: "<hidden>"
        }
        else -> {
            @Suppress("DEPRECATION")
            SSID.takeIf { !it.isNullOrBlank() } ?: "<hidden>"
        }
    }

    // ScanResult.timestamp is microseconds since boot; convert to wall-clock approx.
    val wallClockMs = if (timestamp > 0L) {
        val bootTimeMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        bootTimeMs + (timestamp / 1000L)
    } else {
        fallbackTimestampMs
    }

    return RssiSample(
        ssid = ssidValue,
        bssid = BSSID.orEmpty(),
        rssiDbm = level,
        frequencyMhz = frequency,
        timestampMs = wallClockMs,
        capabilities = capabilities.orEmpty(),
    )
}
