package com.wifiar.app.ar

import android.graphics.Bitmap
import android.graphics.Color
import com.wifiar.app.AppConfig
import com.wifiar.app.data.UserPreferences
import com.wifiar.app.data.interpolation.InterpolationGrid
import com.wifiar.app.data.local.RssiSampleEntity
import kotlin.math.roundToInt

/**
 * Result of turning an [InterpolationGrid] into a single textured floor plane.
 */
data class HeatmapPlane(
    val bitmap: Bitmap,
    val centerX: Float,
    val centerZ: Float,
    val floorY: Float,
    val widthMeters: Float,
    val depthMeters: Float,
    val sampleCount: Int,
    val version: Long,
)

/**
 * Heatmap texture builder.
 *
 * Color scale (strong → weak): **green → yellow → orange → red**.
 * Dead zones (≤ threshold) use a dark-red hatch so unusable areas are obvious.
 * Floor plane is placed using sample poses so the map sits where you walked.
 */
class HeatmapMeshBuilder(
    private val alpha: Float = DEFAULT_ALPHA,
    private val deviceHeightEstimateM: Float = DEFAULT_DEVICE_HEIGHT_M,
    private val maxTextureAxis: Int = DEFAULT_MAX_TEXTURE_AXIS,
    private val deadZoneThresholdDbm: Float = AppConfig.DEAD_ZONE_THRESHOLD_DBM.toFloat(),
) {

    private var versionCounter = 0L

    fun build(grid: InterpolationGrid, samples: List<RssiSampleEntity>): HeatmapPlane? {
        if (grid.cols <= 0 || grid.rows <= 0 || grid.values.isEmpty()) return null
        if (grid.widthMeters <= 0f || grid.depthMeters <= 0f) return null

        val deadTh = runCatching { UserPreferences.rssiDeadDbm.toFloat() }
            .getOrDefault(deadZoneThresholdDbm)

        val (texW, texH) = textureDimensions(grid.cols, grid.rows)
        val pixels = IntArray(texW * texH)
        val a = (alpha.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val deadAlpha = DEAD_ZONE_ALPHA

        for (py in 0 until texH) {
            val gy = ((1f - (py + 0.5f) / texH) * grid.rows).toInt().coerceIn(0, grid.rows - 1)
            for (px in 0 until texW) {
                val gx = (((px + 0.5f) / texW) * grid.cols).toInt().coerceIn(0, grid.cols - 1)
                val rssi = grid.valueAt(gx, gy)
                pixels[py * texW + px] = when {
                    rssi.isNaN() -> Color.argb(0, 0, 0, 0)
                    rssi <= deadTh -> deadZoneColorArgb(px, py, deadAlpha)
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
        const val DEFAULT_ALPHA: Float = 0.72f
        const val DEFAULT_DEVICE_HEIGHT_M: Float = 1.3f
        const val DEFAULT_MAX_TEXTURE_AXIS: Int = 256

        private const val DEAD_ZONE_ALPHA: Int = 230

        // Green → yellow → orange → red (weak is red, not purple).
        private val COLOR_STRONG = floatArrayOf(0x00 / 255f, 0xC8 / 255f, 0x53 / 255f) // green
        private val COLOR_MEDIUM = floatArrayOf(0xFF / 255f, 0xD6 / 255f, 0x00 / 255f) // yellow
        private val COLOR_WEAK = floatArrayOf(0xFF / 255f, 0x6D / 255f, 0x00 / 255f) // orange
        private val COLOR_VERY_WEAK = floatArrayOf(0xE5 / 255f, 0x1C / 255f, 0x23 / 255f) // red

        private const val RSSI_STRONG = -50f
        private const val RSSI_MEDIUM = -70f

        fun deadZoneColorArgb(px: Int, py: Int, alpha: Int = DEAD_ZONE_ALPHA): Int {
            val hatch = ((px + py) / 3) % 2 == 0
            return if (hatch) {
                Color.argb(alpha, 0xB7, 0x1C, 0x1C)
            } else {
                Color.argb(alpha, 0x4A, 0x00, 0x00)
            }
        }

        fun rssiToColorArgb(rssi: Float, alpha: Int = (DEFAULT_ALPHA * 255).roundToInt()): Int {
            val strong = runCatching { UserPreferences.rssiStrongDbm.toFloat() }
                .getOrDefault(RSSI_STRONG)
            val medium = runCatching { UserPreferences.rssiMediumDbm.toFloat() }
                .getOrDefault(RSSI_MEDIUM)
            val dead = runCatching { UserPreferences.rssiDeadDbm.toFloat() }
                .getOrDefault(-80f)
            val scheme = runCatching { UserPreferences.colorScheme }.getOrDefault("default")
            // Midpoint between medium and dead → orange→red transition.
            val weakMid = (medium + dead) * 0.5f

            val (cStrong, cMedium, cWeak, cVeryWeak) = when (scheme) {
                "thermal" -> listOf(COLOR_STRONG, COLOR_WEAK, COLOR_VERY_WEAK, floatArrayOf(0.4f, 0f, 0f))
                "mono" -> listOf(
                    floatArrayOf(0.3f, 0.6f, 1f),
                    floatArrayOf(0.2f, 0.4f, 0.8f),
                    floatArrayOf(0.12f, 0.25f, 0.55f),
                    floatArrayOf(0.05f, 0.1f, 0.25f),
                )
                else -> listOf(COLOR_STRONG, COLOR_MEDIUM, COLOR_WEAK, COLOR_VERY_WEAK)
            }

            val (r, g, b) = when {
                rssi >= strong -> cStrong
                rssi >= medium -> {
                    val t = (strong - rssi) / (strong - medium).coerceAtLeast(1f)
                    lerpColor(cStrong, cMedium, t.coerceIn(0f, 1f))
                }
                rssi >= weakMid -> {
                    val t = (medium - rssi) / (medium - weakMid).coerceAtLeast(1f)
                    lerpColor(cMedium, cWeak, t.coerceIn(0f, 1f))
                }
                else -> {
                    val t = (weakMid - rssi) / (weakMid - dead).coerceAtLeast(1f)
                    lerpColor(cWeak, cVeryWeak, t.coerceIn(0f, 1f))
                }
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
