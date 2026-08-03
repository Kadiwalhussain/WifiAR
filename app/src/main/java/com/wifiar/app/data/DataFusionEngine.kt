package com.wifiar.app.data

import android.util.Log
import com.wifiar.app.ar.ARSessionManager
import com.wifiar.app.ar.Pose3D
import com.wifiar.app.ar.TrackingQuality
import com.wifiar.app.data.local.RssiSampleDao
import com.wifiar.app.data.local.RssiSampleEntity
import com.wifiar.app.scanner.RssiSample
import com.wifiar.app.scanner.WifiScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.ArrayDeque

/**
 * Fuses WiFi scans (Part 1) with AR pose (Part 2) and persists results to Room.
 *
 * Pose and scan clocks are not synchronous: recent poses are kept in a rolling
 * buffer (~2 s). Each new scan batch is tagged with the nearest pose by
 * wall-clock timestamp.
 *
 * **Multi-network (Part 8):** every AP in the scan batch is persisted as its own
 * [RssiSampleEntity] (ssid + bssid + rssi at the same pose). No filtering to the
 * connected network — required for per-SSID heatmaps and comparison.
 */

class DataFusionEngine(
    private val wifiScanner: WifiScanner,
    private val arSessionManager: ARSessionManager,
    private val sampleDao: RssiSampleDao,
    private val scope: CoroutineScope,
) {
    private val _currentSessionSamples =
        MutableStateFlow<List<RssiSampleEntity>>(emptyList())
    val currentSessionSamples: StateFlow<List<RssiSampleEntity>> =
        _currentSessionSamples.asStateFlow()

    private val _isFusing = MutableStateFlow(false)
    val isFusing: StateFlow<Boolean> = _isFusing.asStateFlow()

    private val _lastFusionError = MutableStateFlow<String?>(null)
    val lastFusionError: StateFlow<String?> = _lastFusionError.asStateFlow()

    private val poseBuffer = ArrayDeque<Pose3D>()
    private val poseLock = Any()

    private var poseJob: Job? = null
    private var scanJob: Job? = null
    private var dbObserveJob: Job? = null
    private var activeSessionId: String? = null

    /** Fingerprint of last fused scan batch to avoid re-inserting cached results. */
    private var lastScanFingerprint: String? = null

    /**
     * Begin fusing for [sessionId]. Collects pose + scan streams until [stop].
     */
    fun start(sessionId: String) {
        if (_isFusing.value && activeSessionId == sessionId) return
        stop()

        activeSessionId = sessionId
        _isFusing.value = true
        _lastFusionError.value = null
        lastScanFingerprint = null
        synchronized(poseLock) { poseBuffer.clear() }
        _currentSessionSamples.value = emptyList()

        dbObserveJob = scope.launch {
            sampleDao.getAllForSession(sessionId).collect { list ->
                _currentSessionSamples.value = list
            }
        }

        poseJob = scope.launch {
            arSessionManager.poseFlow.collect { pose ->
                if (pose.timestampMs <= 0L) return@collect
                // Only buffer when AR is tracking so we don't tag with stale zeros.
                val quality = arSessionManager.trackingFeedback.value.quality
                if (quality != TrackingQuality.TRACKING) return@collect
                pushPose(pose)
            }
        }

        scanJob = scope.launch {
            wifiScanner.scanResultsFlow
                .distinctUntilChanged { old, new -> scanFingerprint(old) == scanFingerprint(new) }
                .collectLatest { batch ->
                    if (!_isFusing.value) return@collectLatest
                    fuseAndPersist(batch)
                }
        }

        Log.d(TAG, "Fusion started for session $sessionId")
    }

    fun stop() {
        poseJob?.cancel()
        scanJob?.cancel()
        dbObserveJob?.cancel()
        poseJob = null
        scanJob = null
        dbObserveJob = null
        activeSessionId = null
        _isFusing.value = false
        synchronized(poseLock) { poseBuffer.clear() }
        Log.d(TAG, "Fusion stopped")
    }

    /**
     * Load samples for a historical session into [currentSessionSamples]
     * without enabling live fusion.
     */
    fun loadSessionForReview(sessionId: String) {
        stop()
        dbObserveJob = scope.launch {
            sampleDao.getAllForSession(sessionId).collect { list ->
                _currentSessionSamples.value = list
            }
        }
    }

    fun clearReviewSamples() {
        if (!_isFusing.value) {
            dbObserveJob?.cancel()
            dbObserveJob = null
            _currentSessionSamples.value = emptyList()
        }
    }

    private fun pushPose(pose: Pose3D) {
        synchronized(poseLock) {
            poseBuffer.addLast(pose)
            val cutoff = pose.timestampMs - POSE_BUFFER_WINDOW_MS
            while (poseBuffer.isNotEmpty() && poseBuffer.first().timestampMs < cutoff) {
                poseBuffer.removeFirst()
            }
        }
    }

    private suspend fun fuseAndPersist(batch: List<RssiSample>) {
        val sessionId = activeSessionId ?: return
        if (batch.isEmpty()) return

        val fingerprint = scanFingerprint(batch)
        if (fingerprint == lastScanFingerprint) return

        val quality = arSessionManager.trackingFeedback.value.quality
        if (quality != TrackingQuality.TRACKING) {
            _lastFusionError.value = "Waiting for AR tracking before saving samples"
            return
        }

        // Use scan timestamp (or now) for nearest-neighbor pose match.
        val referenceTs = batch.maxOfOrNull { it.timestampMs }
            ?.takeIf { it > 0L }
            ?: System.currentTimeMillis()

        val pose = nearestPose(referenceTs)
            ?: arSessionManager.pose.value.takeIf {
                it.timestampMs > 0L &&
                    arSessionManager.trackingFeedback.value.quality == TrackingQuality.TRACKING
            }

        if (pose == null) {
            _lastFusionError.value = "No recent pose for scan — keep moving slowly"
            Log.w(TAG, "Dropping scan batch: empty pose buffer")
            return
        }

        val entities = batch.map { sample ->
            RssiSampleEntity(
                sessionId = sessionId,
                timestampMs = sample.timestampMs.takeIf { it > 0L } ?: referenceTs,
                poseX = pose.x,
                poseY = pose.y,
                poseZ = pose.z,
                ssid = sample.ssid,
                bssid = sample.bssid,
                rssiDbm = sample.rssiDbm,
                frequencyMhz = sample.frequencyMhz,
            )
        }

        runCatching {
            sampleDao.insertAll(entities)
            lastScanFingerprint = fingerprint
            _lastFusionError.value = null
            Log.d(TAG, "Fused ${entities.size} samples at (${pose.x}, ${pose.y}, ${pose.z})")
        }.onFailure {
            Log.e(TAG, "Failed to persist fused samples", it)
            _lastFusionError.value = it.message ?: "DB write failed"
        }
    }

    /**
     * Nearest-neighbor match in the rolling pose buffer.
     */
    private fun nearestPose(timestampMs: Long): Pose3D? {
        synchronized(poseLock) {
            if (poseBuffer.isEmpty()) return null
            var best: Pose3D? = null
            var bestDelta = Long.MAX_VALUE
            for (pose in poseBuffer) {
                val delta = kotlin.math.abs(pose.timestampMs - timestampMs)
                if (delta < bestDelta) {
                    bestDelta = delta
                    best = pose
                }
            }
            // Reject if pose is older than the buffer window (stale).
            if (best != null && bestDelta > POSE_BUFFER_WINDOW_MS) {
                return poseBuffer.lastOrNull()
            }
            return best
        }
    }

    private fun scanFingerprint(batch: List<RssiSample>): String {
        if (batch.isEmpty()) return "empty"
        // Order-independent fingerprint of network identities + RSSI + coarse time.
        return batch
            .sortedBy { it.bssid }
            .joinToString("|") { "${it.bssid}:${it.rssiDbm}:${it.timestampMs / 1000}" }
    }

    companion object {
        private const val TAG = "DataFusionEngine"

        /** Keep ~2 seconds of poses for nearest-neighbor matching. */
        const val POSE_BUFFER_WINDOW_MS: Long = 2_000L
    }
}
