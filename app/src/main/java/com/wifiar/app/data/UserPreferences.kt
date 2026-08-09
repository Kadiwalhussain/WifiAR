package com.wifiar.app.data

import android.content.Context
import android.content.SharedPreferences
import com.wifiar.app.AppConfig

/**
 * User-tunable settings. Safe defaults if [init] was not called yet (never crash).
 */
object UserPreferences {

    private const val PREFS = "wifiar_user_prefs"
    private const val KEY_ONBOARDING = "onboarding_done"
    private const val KEY_STRONG = "rssi_strong"
    private const val KEY_MEDIUM = "rssi_medium"
    private const val KEY_DEAD = "rssi_dead"
    private const val KEY_PATH_LOSS_INDOOR = "path_loss_indoor"
    private const val KEY_GRID_CELL = "grid_cell_m"
    private const val KEY_CLOUD_API_CONFIGURED = "cloud_api_ack"

    // Analyzer settings (mock UI)
    private const val KEY_AUTO_SCAN = "auto_scan"
    private const val KEY_SCAN_INTERVAL_SEC = "scan_interval_sec"
    private const val KEY_INCLUDE_HIDDEN = "include_hidden"
    private const val KEY_AR_SMOOTHING = "ar_signal_smoothing"
    private const val KEY_SIGNAL_UNITS = "signal_units" // dbm | percent
    private const val KEY_COLOR_SCHEME = "color_scheme" // default | thermal | mono
    private const val KEY_PARTICLE_DENSITY = "particle_density" // low | medium | high
    private const val KEY_SCAN_ALERTS = "scan_alerts"
    private const val KEY_WEEKLY_REPORTS = "weekly_reports"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    prefs = context.applicationContext
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                }
            }
        }
    }

    private fun p(): SharedPreferences? = prefs

    var onboardingDone: Boolean
        get() = p()?.getBoolean(KEY_ONBOARDING, false) ?: false
        set(v) {
            p()?.edit()?.putBoolean(KEY_ONBOARDING, v)?.apply()
        }

    var rssiStrongDbm: Int
        get() = p()?.getInt(KEY_STRONG, -50) ?: -50
        set(v) {
            p()?.edit()?.putInt(KEY_STRONG, v)?.apply()
        }

    var rssiMediumDbm: Int
        get() = p()?.getInt(KEY_MEDIUM, AppConfig.COVERAGE_THRESHOLD_DBM)
            ?: AppConfig.COVERAGE_THRESHOLD_DBM
        set(v) {
            p()?.edit()?.putInt(KEY_MEDIUM, v)?.apply()
        }

    var rssiDeadDbm: Int
        get() = p()?.getInt(KEY_DEAD, AppConfig.DEAD_ZONE_THRESHOLD_DBM)
            ?: AppConfig.DEAD_ZONE_THRESHOLD_DBM
        set(v) {
            p()?.edit()?.putInt(KEY_DEAD, v)?.apply()
        }

    var pathLossIndoor: Boolean
        get() = p()?.getBoolean(KEY_PATH_LOSS_INDOOR, true) ?: true
        set(v) {
            p()?.edit()?.putBoolean(KEY_PATH_LOSS_INDOOR, v)?.apply()
        }

    val pathLossExponent: Float
        get() = if (pathLossIndoor) {
            AppConfig.PATH_LOSS_EXPONENT_INDOOR
        } else {
            AppConfig.PATH_LOSS_EXPONENT_OPEN
        }

    var gridCellSizeM: Float
        get() = p()?.getFloat(KEY_GRID_CELL, 0.3f) ?: 0.3f
        set(v) {
            p()?.edit()?.putFloat(KEY_GRID_CELL, v.coerceIn(0.15f, 1.0f))?.apply()
        }

    var cloudApiAcknowledged: Boolean
        get() = p()?.getBoolean(KEY_CLOUD_API_CONFIGURED, false) ?: false
        set(v) {
            p()?.edit()?.putBoolean(KEY_CLOUD_API_CONFIGURED, v)?.apply()
        }

    // ── Scan ────────────────────────────────────────────────────────────────

    var autoScan: Boolean
        get() = p()?.getBoolean(KEY_AUTO_SCAN, true) ?: true
        set(v) {
            p()?.edit()?.putBoolean(KEY_AUTO_SCAN, v)?.apply()
        }

    /** Preferred scan interval in seconds (UI); Android may throttle further. */
    var scanIntervalSec: Int
        get() = p()?.getInt(KEY_SCAN_INTERVAL_SEC, 15) ?: 15
        set(v) {
            p()?.edit()?.putInt(KEY_SCAN_INTERVAL_SEC, v.coerceIn(2, 120))?.apply()
        }

    var includeHiddenNetworks: Boolean
        get() = p()?.getBoolean(KEY_INCLUDE_HIDDEN, false) ?: false
        set(v) {
            p()?.edit()?.putBoolean(KEY_INCLUDE_HIDDEN, v)?.apply()
        }

    var arSignalSmoothing: Boolean
        get() = p()?.getBoolean(KEY_AR_SMOOTHING, true) ?: true
        set(v) {
            p()?.edit()?.putBoolean(KEY_AR_SMOOTHING, v)?.apply()
        }

    // ── Visualization ───────────────────────────────────────────────────────

    /** "dbm" or "percent" */
    var signalUnits: String
        get() = p()?.getString(KEY_SIGNAL_UNITS, "dbm") ?: "dbm"
        set(v) {
            p()?.edit()?.putString(KEY_SIGNAL_UNITS, v)?.apply()
        }

    /** "default" | "thermal" | "mono" */
    var colorScheme: String
        get() = p()?.getString(KEY_COLOR_SCHEME, "default") ?: "default"
        set(v) {
            p()?.edit()?.putString(KEY_COLOR_SCHEME, v)?.apply()
        }

    /** "low" | "medium" | "high" */
    var particleDensity: String
        get() = p()?.getString(KEY_PARTICLE_DENSITY, "medium") ?: "medium"
        set(v) {
            p()?.edit()?.putString(KEY_PARTICLE_DENSITY, v)?.apply()
        }

    val particleDensityMaxSpheres: Int
        get() = when (particleDensity) {
            "low" -> 8
            "high" -> 24
            else -> 16
        }

    // ── Notifications ───────────────────────────────────────────────────────

    var scanAlerts: Boolean
        get() = p()?.getBoolean(KEY_SCAN_ALERTS, true) ?: true
        set(v) {
            p()?.edit()?.putBoolean(KEY_SCAN_ALERTS, v)?.apply()
        }

    var weeklyReports: Boolean
        get() = p()?.getBoolean(KEY_WEEKLY_REPORTS, false) ?: false
        set(v) {
            p()?.edit()?.putBoolean(KEY_WEEKLY_REPORTS, v)?.apply()
        }
}
