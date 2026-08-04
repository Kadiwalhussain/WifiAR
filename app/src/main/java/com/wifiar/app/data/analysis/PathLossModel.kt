package com.wifiar.app.data.analysis

import com.wifiar.app.AppConfig
import com.wifiar.app.ar.Pose3D
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Log-distance path-loss model for simplified RF prediction.
 *
 * ```
 * PL(d) = PL(d0) + 10 · n · log10(d / d0)
 * RSSI(d) ≈ P_tx − PL(d)
 * ```
 *
 * ## Honest limitations (student-project scope)
 * - **No wall / obstruction detection** — n is a single scalar for the whole space.
 * - Does not model multipath, antenna patterns, or frequency-selective fading.
 * - Recommendations are **heuristic estimates**, not a full RF simulation.
 * - Suitable for relative ranking of placement candidates, not absolute dBm accuracy.
 */
class PathLossModel(
    /** Path-loss exponent n (default indoor with walls). */
    val exponent: Float = AppConfig.PATH_LOSS_EXPONENT_INDOOR,
    /** Reference distance d0 in metres. */
    val d0Meters: Float = AppConfig.PATH_LOSS_D0_M,
    /** Path loss at d0 (dB). ~40 dB free-space @ 2.4 GHz / 1 m. */
    val plD0Db: Float = AppConfig.PATH_LOSS_PL_D0_DB,
) {

    /**
     * Predicted RSSI (dBm) at [targetPosition] if a router with [txPowerDbm]
     * were placed at [candidatePosition].
     *
     * Uses **horizontal (x, z) distance** only (single-floor), consistent with
     * the rest of WifiAR's 2D mapping assumptions.
     */
    fun predictedRssi(
        candidatePosition: Pose3D,
        txPowerDbm: Float,
        targetPosition: Pose3D,
    ): Float {
        val d = horizontalDistance(candidatePosition, targetPosition)
            .coerceAtLeast(d0Meters * 0.1f) // avoid log10(0)
        val pl = pathLossDb(d)
        return txPowerDbm - pl
    }

    fun pathLossDb(distanceMeters: Float): Float {
        val d = distanceMeters.coerceAtLeast(d0Meters * 0.1f)
        return plD0Db + 10f * exponent * log10(d / d0Meters)
    }

    fun horizontalDistance(a: Pose3D, b: Pose3D): Float {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return sqrt(dx * dx + dz * dz)
    }

    companion object {
        fun indoor() = PathLossModel(exponent = AppConfig.PATH_LOSS_EXPONENT_INDOOR)
        fun openSpace() = PathLossModel(exponent = AppConfig.PATH_LOSS_EXPONENT_OPEN)
    }
}
