package com.wifiar.app.data.analysis

import com.wifiar.app.AppConfig
import com.wifiar.app.data.interpolation.IdwInterpolator
import com.wifiar.app.data.interpolation.InterpolationGrid
import com.wifiar.app.data.local.RssiSampleEntity
import kotlin.math.sqrt

/**
 * Identity of a WiFi network for comparison (prefer BSSID; SSID is display-only).
 */
data class NetworkKey(
    val ssid: String,
    val bssid: String,
) {
    val displayName: String
        get() = ssid.ifBlank { "<hidden>" }
}

/**
 * Per-network coverage stats + its own IDW grid.
 */
data class NetworkCoverageAnalysis(
    val key: NetworkKey,
    val sampleCount: Int,
    val averageRssiDbm: Float,
    /** Fraction of interpolated cells ≥ [AppConfig.COVERAGE_THRESHOLD_DBM] (0..1). */
    val coverageFraction: Float,
    /** Fraction of cells ≤ [AppConfig.DEAD_ZONE_THRESHOLD_DBM] (0..1). */
    val deadZoneFraction: Float,
    val grid: InterpolationGrid,
)

data class NetworkComparisonResult(
    val networks: List<NetworkCoverageAnalysis>,
    val totalSamples: Int,
)

/**
 * Groups session samples by BSSID and runs independent IDW heatmaps so the
 * user can compare coverage of home Wi‑Fi vs neighbors / mesh nodes.
 */
class NetworkComparisonEngine(
    private val interpolator: IdwInterpolator = IdwInterpolator(),
    private val coverageThresholdDbm: Float = AppConfig.COVERAGE_THRESHOLD_DBM.toFloat(),
    private val deadZoneThresholdDbm: Float = AppConfig.DEAD_ZONE_THRESHOLD_DBM.toFloat(),
    private val minSamples: Int = AppConfig.NETWORK_COMPARE_MIN_SAMPLES,
) {

    /**
     * Build per-network stats + grids. Safe to call off the main thread.
     */
    fun compare(samples: List<RssiSampleEntity>): NetworkComparisonResult {
        if (samples.isEmpty()) {
            return NetworkComparisonResult(emptyList(), 0)
        }

        val byNetwork = samples.groupBy { NetworkKey(it.ssid, it.bssid) }
        val analyses = ArrayList<NetworkCoverageAnalysis>()

        for ((key, netSamples) in byNetwork) {
            if (netSamples.size < minSamples) continue
            val grid = interpolator.interpolate(netSamples)
            if (grid.cols == 0 || grid.values.isEmpty()) continue

            var sum = 0.0
            var valid = 0
            var covered = 0
            var dead = 0
            for (v in grid.values) {
                if (v.isNaN()) continue
                valid++
                sum += v
                if (v >= coverageThresholdDbm) covered++
                if (v <= deadZoneThresholdDbm) dead++
            }
            if (valid == 0) continue

            analyses.add(
                NetworkCoverageAnalysis(
                    key = key,
                    sampleCount = netSamples.size,
                    averageRssiDbm = (sum / valid).toFloat(),
                    coverageFraction = covered.toFloat() / valid,
                    deadZoneFraction = dead.toFloat() / valid,
                    grid = grid,
                ),
            )
        }

        // Strongest average first.
        analyses.sortByDescending { it.averageRssiDbm }
        return NetworkComparisonResult(
            networks = analyses,
            totalSamples = samples.size,
        )
    }

    /**
     * Which network is strongest at world (x, z) using IDW per BSSID group.
     * Returns null if no network has enough samples or estimates.
     */
    fun bestNetworkAt(
        x: Float,
        z: Float,
        samples: List<RssiSampleEntity>,
    ): BestNetworkEstimate? {
        if (samples.isEmpty()) return null
        val byNetwork = samples.groupBy { NetworkKey(it.ssid, it.bssid) }
        var best: BestNetworkEstimate? = null

        for ((key, netSamples) in byNetwork) {
            if (netSamples.isEmpty()) continue
            val estimate = if (netSamples.size == 1) {
                val s = netSamples.first()
                // Distance-weighted: if we're far from the only sample, still report it.
                val dx = x - s.poseX
                val dz = z - s.poseZ
                val dist = sqrt(dx * dx + dz * dz)
                // Soften: use raw RSSI but ignore if sample is very far (> 8 m) and alone.
                if (dist > 8f) continue
                s.rssiDbm.toFloat()
            } else {
                interpolator.estimateAtSamples(x, z, netSamples)
            }
            if (estimate.isNaN()) continue
            if (best == null || estimate > best.rssiDbm) {
                best = BestNetworkEstimate(
                    key = key,
                    rssiDbm = estimate,
                    sampleCount = netSamples.size,
                )
            }
        }
        return best
    }
}

data class BestNetworkEstimate(
    val key: NetworkKey,
    val rssiDbm: Float,
    val sampleCount: Int,
)
