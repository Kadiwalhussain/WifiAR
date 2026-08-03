package com.wifiar.app.ar

import android.graphics.Bitmap
import android.graphics.Color
import com.wifiar.app.AppConfig
import com.wifiar.app.data.interpolation.InterpolationGrid
import com.wifiar.app.data.local.RssiSampleEntity
import kotlin.math.roundToInt

/**
 * Result of turning an [InterpolationGrid] into a single textured floor plane.
 *
 * Prefer one textured plane over per-cell meshes — far cheaper in SceneView/Filament.
 */
data class HeatmapPlane(
    val bitmap: Bitmap,
    val centerX: Float,
    val centerZ: Float,
    /** Approximate floor height (world Y) for AR placement. */
    val floorY: Float,
    val widthMeters: Float,
    val depthMeters: Float,
    val sampleCount: Int,
    val version: Long,
)

/**
 * Builds a semi-transparent heatmap [Bitmap] + placement metadata for SceneView.
 *
 * Color mapping reuses Part 3 tiers (green ≥ −50, yellow −50…−70, purple < −70)
 * with smooth RGB blends. Cells at or below [deadZoneThresholdDbm] use a
 * high-opacity dark-red hatch so true dead zones stand out from merely weak purple.
 */
class HeatmapMeshBuilder(
    /** Overall texture opacity (0..1) for non-dead cells. */
    private val alpha: Float = DEFAULT_ALPHA,
    /**
     * Assumed phone height above the floor when estimating floorY from poses
     * (device pose is chest/head height, not floor contact).
     */
    private val deviceHeightEstimateM: Float = DEFAULT_DEVICE_HEIGHT_M,
    /** Max bitmap dimension on either axis (keeps GPU upload cheap). */
    private val maxTextureAxis: Int = DEFAULT_MAX_TEXTURE_AXIS,
    /** RSSI at or below this is painted as a dead-zone warning pattern. */
    private val deadZoneThresholdDbm: Float = AppConfig.DEAD_ZONE_THRESHOLD_DBM.toFloat(),
) {

    private var versionCounter = 0L

    /**
     * Convert [grid] to a [HeatmapPlane]. [samples] are only used to estimate
     * floor height from measured device poses.
     */
    fun build(grid: InterpolationGrid, samples: List<RssiSampleEntity>): HeatmapPlane? {
        if (grid.cols <= 0 || grid.rows <= 0 || grid.values.isEmpty()) return null
        if (grid.widthMeters <= 0f || grid.depthMeters <= 0f) return null

        val (texW, texH) = textureDimensions(grid.cols, grid.rows)
        val pixels = IntArray(texW * texH)
        val a = (alpha.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val deadAlpha = DEAD_ZONE_ALPHA

        for (py in 0 until texH) {
            // Map texture row → grid row (flip Y so +Z maps upward on the texture).
            val gy = ((1f - (py + 0.5f) / texH) * grid.rows).toInt().coerceIn(0, grid.rows - 1)
            for (px in 0 until texW) {
                val gx = (((px + 0.5f) / texW) * grid.cols).toInt().coerceIn(0, grid.cols - 1)
                val rssi = grid.valueAt(gx, gy)
                pixels[py * texW + px] = when {
                    rssi.isNaN() -> Color.argb(0, 0, 0, 0)
                    rssi <= deadZoneThresholdDbm -> deadZoneColorArgb(px, py, deadAlpha)
                    else -> rssiToColorArgb(rssi, a)
                }
            }
        }

        val bitmap = Bitmap.createBitmap(texW, texH, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, texW, 0, 0, texW, texH)

        val floorY = estimateFloorY(samples)
        versionCounter += 1L

        return HeatmapPlane(
            bitmap = bitmap,
            centerX = grid.centerX,
            centerZ = grid.centerZ,
            floorY = floorY,
            widthMeters = grid.widthMeters,
            depthMeters = grid.depthMeters,
            sampleCount = grid.sampleCount,
            version = versionCounter,
        )
    }

    private fun textureDimensions(cols: Int, rows: Int): Pair<Int, Int> {
        var w = cols.coerceAtLeast(8)
        var h = rows.coerceAtLeast(8)
        val maxDim = maxOf(w, h)
        if (maxDim > maxTextureAxis) {
            val scale = maxTextureAxis.toFloat() / maxDim
            w = (w * scale).roundToInt().coerceAtLeast(1)
            h = (h * scale).roundToInt().coerceAtLeast(1)
        } else if (maxDim < 32) {
            val scale = 32f / maxDim
            w = (w * scale).roundToInt().coerceAtMost(maxTextureAxis)
            h = (h * scale).roundToInt().coerceAtMost(maxTextureAxis)
        }
        return w to h
    }

    private fun estimateFloorY(samples: List<RssiSampleEntity>): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        var minY = Float.POSITIVE_INFINITY
        for (s in samples) {
            sum += s.poseY
            if (s.poseY < minY) minY = s.poseY
        }
        val avgY = (sum / samples.size).toFloat()
        val estimated = avgY - deviceHeightEstimateM
        return minOf(estimated, minY - 0.05f)
    }

    companion object {
        const val DEFAULT_ALPHA: Float = 0.6f
        const val DEFAULT_DEVICE_HEIGHT_M: Float = 1.3f
        const val DEFAULT_MAX_TEXTURE_AXIS: Int = 256

        /** Higher opacity so dead zones read clearly against the room. */
        private const val DEAD_ZONE_ALPHA: Int = 210

        // Tier anchors (same hues as Part 3 spheres).
        private val COLOR_STRONG = floatArrayOf(0x2E / 255f, 0x7D / 255f, 0x32 / 255f)
        private val COLOR_MEDIUM = floatArrayOf(0xF9 / 255f, 0xA8 / 255f, 0x25 / 255f)
        private val COLOR_WEAK = floatArrayOf(0x6A / 255f, 0x1B / 255f, 0x9A / 255f)

        private const val RSSI_STRONG = -50f
        private const val RSSI_MEDIUM = -70f
        private const val RSSI_WEAK = -90f

        /**
         * Dark red / black diagonal hatch — distinct from purple weak-signal gradient.
         */
        fun deadZoneColorArgb(px: Int, py: Int, alpha: Int = DEAD_ZONE_ALPHA): Int {
            val hatch = ((px + py) / 3) % 2 == 0
            return if (hatch) {
                Color.argb(alpha, 0x8B, 0x00, 0x00) // dark red
            } else {
                Color.argb(alpha, 0x1A, 0x00, 0x00) // near-black red
            }
        }

        /**
         * Smooth RGB blend across tier boundaries (non-dead cells).
         */
        fun rssiToColorArgb(rssi: Float, alpha: Int = (DEFAULT_ALPHA * 255).roundToInt()): Int {
            val (r, g, b) = when {
                rssi >= RSSI_STRONG -> COLOR_STRONG
                rssi >= RSSI_MEDIUM -> {
                    val t = (RSSI_STRONG - rssi) / (RSSI_STRONG - RSSI_MEDIUM)
                    lerpColor(COLOR_STRONG, COLOR_MEDIUM, t.coerceIn(0f, 1f))
                }
                rssi >= RSSI_WEAK -> {
                    val t = (RSSI_MEDIUM - rssi) / (RSSI_MEDIUM - RSSI_WEAK)
                    lerpColor(COLOR_MEDIUM, COLOR_WEAK, t.coerceIn(0f, 1f))
                }
                else -> COLOR_WEAK
            }
            return Color.argb(
                alpha.coerceIn(0, 255),
                (r * 255f).roundToInt().coerceIn(0, 255),
                (g * 255f).roundToInt().coerceIn(0, 255),
                (b * 255f).roundToInt().coerceIn(0, 255),
            )
        }

        private fun lerpColor(a: FloatArray, b: FloatArray, t: Float): FloatArray {
            return floatArrayOf(
                a[0] + (b[0] - a[0]) * t,
                a[1] + (b[1] - a[1]) * t,
                a[2] + (b[2] - a[2]) * t,
            )
        }
    }
}
