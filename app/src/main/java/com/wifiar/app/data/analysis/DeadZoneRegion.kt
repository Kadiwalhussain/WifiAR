package com.wifiar.app.data.analysis

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * One contiguous below-threshold region on the IDW grid.
 */
data class DeadZoneRegion(
    val id: Int,
    /** Centroid X in AR world metres. */
    val centroidX: Float,
    /** Centroid Z in AR world metres. */
    val centroidZ: Float,
    /** Approximate area in m² (cell count × cell area). */
    val areaSqM: Float,
    /** Worst (lowest / most negative) RSSI in the region. */
    val worstRssiDbm: Float,
    /** Mean interpolated RSSI across cells in the region. */
    val averageRssiDbm: Float,
    /** Number of connected grid cells. */
    val cellCount: Int,
) {
    /**
     * Human-readable offset from session origin (0,0) on the horizontal plane.
     * Uses +Z as "forward" and +X as "right" (ARCore camera start convention).
     */
    fun relativeDescriptionFromOrigin(): String {
        val dist = sqrt(centroidX * centroidX + centroidZ * centroidZ)
        if (dist < 0.15f) return "near session origin (%.1f m)".format(dist)

        val deg = Math.toDegrees(atan2(centroidX.toDouble(), centroidZ.toDouble()))
        val direction = when {
            deg >= -22.5 && deg < 22.5 -> "forward (+Z)"
            deg >= 22.5 && deg < 67.5 -> "forward-right"
            deg >= 67.5 && deg < 112.5 -> "right (+X)"
            deg >= 112.5 && deg < 157.5 -> "back-right"
            deg >= 157.5 || deg < -157.5 -> "back (−Z)"
            deg >= -157.5 && deg < -112.5 -> "back-left"
            deg >= -112.5 && deg < -67.5 -> "left (−X)"
            else -> "forward-left"
        }
        return "%.1f m %s of origin".format(dist, direction)
    }
}
