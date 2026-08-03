package com.wifiar.app.data.interpolation

import com.wifiar.app.data.local.RssiSampleEntity
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

/**
 * Inverse Distance Weighting (IDW) interpolator for RSSI heatmaps.
 *
 * ## Single-floor simplification
 * Interpolation is performed in the **horizontal (x, z) plane only**. The ARCore
 * Y axis (height) is ignored so a single-floor walkthrough stays tractable and
 * the heatmap can be projected onto an approximate floor plane. Multi-floor
 * support would need a 3D IDW or per-floor slices (future work).
 *
 * Formula:
 * ```
 * estimated(q) = Σ (w_i * rssi_i) / Σ w_i
 * w_i = 1 / (distance(q, s_i)^p + ε)
 * ```
 */
class IdwInterpolator(
    /** Grid spacing in metres (default 0.3 m). */
    val cellSize: Float = DEFAULT_CELL_SIZE_M,
    /** IDW power parameter (default 2). */
    val power: Float = DEFAULT_POWER,
    /** Padding added around the sample bounding box (metres). */
    val paddingMeters: Float = DEFAULT_PADDING_M,
    /** Avoid division by zero when a query lands on a sample. */
    val epsilon: Float = DEFAULT_EPSILON,
) {

    /**
     * Build a dense [InterpolationGrid] covering all [samples].
     *
     * Safe to call off the main thread. Returns [InterpolationGrid.EMPTY] when
     * there are fewer than 1 usable sample or the spatial extent is degenerate.
     */
    fun interpolate(samples: List<RssiSampleEntity>): InterpolationGrid {
        if (samples.isEmpty()) return InterpolationGrid.EMPTY

        // Horizontal control points only (x, z, rssi) — see class KDoc.
        val points = ArrayList<ControlPoint>(samples.size)
        for (s in samples) {
            points.add(ControlPoint(s.poseX, s.poseZ, s.rssiDbm.toFloat()))
        }

        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.z < minZ) minZ = p.z
            if (p.z > maxZ) maxZ = p.z
        }

        // Expand slightly so edge samples still have surrounding cells.
        minX -= paddingMeters
        maxX += paddingMeters
        minZ -= paddingMeters
        maxZ += paddingMeters

        // Ensure a minimum footprint so a single sample still yields a patch.
        val minSpan = cellSize * 2f
        if (maxX - minX < minSpan) {
            val mid = (minX + maxX) * 0.5f
            minX = mid - minSpan * 0.5f
            maxX = mid + minSpan * 0.5f
        }
        if (maxZ - minZ < minSpan) {
            val mid = (minZ + maxZ) * 0.5f
            minZ = mid - minSpan * 0.5f
            maxZ = mid + minSpan * 0.5f
        }

        val cols = ((maxX - minX) / cellSize).toInt().coerceAtLeast(1) + 1
        val rows = ((maxZ - minZ) / cellSize).toInt().coerceAtLeast(1) + 1

        // Cap grid size for real-time budgets (very large rooms).
        val safeCols = cols.coerceAtMost(MAX_GRID_AXIS)
        val safeRows = rows.coerceAtMost(MAX_GRID_AXIS)
        val stepX = (maxX - minX) / safeCols.coerceAtLeast(1)
        val stepZ = (maxZ - minZ) / safeRows.coerceAtLeast(1)
        val effectiveCell = maxOf(stepX, stepZ, 1e-4f)

        val values = FloatArray(safeCols * safeRows)
        val elapsed = measureTimeMillis {
            var i = 0
            for (row in 0 until safeRows) {
                val z = minZ + (row + 0.5f) * stepZ
                for (col in 0 until safeCols) {
                    val x = minX + (col + 0.5f) * stepX
                    values[i++] = estimateAtControlPoints(x, z, points)

                }
            }
        }

        return InterpolationGrid(
            minX = minX,
            maxX = maxX,
            minZ = minZ,
            maxZ = maxZ,
            cellSize = effectiveCell,
            cols = safeCols,
            rows = safeRows,
            values = values,
            sampleCount = points.size,
            computeTimeMs = elapsed,
        )
    }

    /**
     * IDW estimate at a single horizontal query point from raw entities.
     */
    fun estimateAtSamples(x: Float, z: Float, samples: List<RssiSampleEntity>): Float {
        if (samples.isEmpty()) return Float.NaN
        val points = samples.map { ControlPoint(it.poseX, it.poseZ, it.rssiDbm.toFloat()) }
        return estimateAtControlPoints(x, z, points)
    }

    private fun estimateAtControlPoints(
        x: Float,
        z: Float,
        points: List<ControlPoint>,
    ): Float {
        if (points.isEmpty()) return Float.NaN
        if (points.size == 1) return points[0].rssi

        var num = 0.0
        var den = 0.0
        val p = power.toDouble()
        val eps = epsilon.toDouble()

        for (pt in points) {
            val dx = (x - pt.x).toDouble()
            val dz = (z - pt.z).toDouble()
            val distSq = dx * dx + dz * dz
            // Exact hit (or numerically zero distance): use the sample value.
            if (distSq <= eps * eps) {
                return pt.rssi
            }
            val dist = sqrt(distSq)
            val w = 1.0 / (dist.pow(p) + eps)
            num += w * pt.rssi
            den += w
        }

        if (den <= 0.0) return Float.NaN
        return (num / den).toFloat()
    }


    private data class ControlPoint(
        val x: Float,
        val z: Float,
        val rssi: Float,
    )

    companion object {
        const val DEFAULT_CELL_SIZE_M: Float = 0.3f
        const val DEFAULT_POWER: Float = 2f
        const val DEFAULT_PADDING_M: Float = 0.5f
        const val DEFAULT_EPSILON: Float = 1e-6f

        /** Hard cap so a huge bounding box cannot explode frame time. */
        const val MAX_GRID_AXIS: Int = 128

        /**
         * Recompute the heatmap only every N new samples (and always when
         * first reaching [MIN_SAMPLES_FOR_HEATMAP]).
         */
        const val RECOMPUTE_EVERY_N_SAMPLES: Int = 5
        const val MIN_SAMPLES_FOR_HEATMAP: Int = 3
    }
}

/**
 * Decides when to re-run IDW so we stay under the real-time budget.
 */
class HeatmapRecomputeGate(
    private val everyN: Int = IdwInterpolator.RECOMPUTE_EVERY_N_SAMPLES,
    private val minSamples: Int = IdwInterpolator.MIN_SAMPLES_FOR_HEATMAP,
) {
    private var lastComputedSampleCount: Int = 0

    fun shouldRecompute(sampleCount: Int): Boolean {
        if (sampleCount < minSamples) return false
        if (sampleCount < lastComputedSampleCount) {
            // Session reset / fewer samples — allow a fresh compute.
            lastComputedSampleCount = 0
        }
        if (lastComputedSampleCount == 0) return true
        return sampleCount - lastComputedSampleCount >= everyN
    }

    fun markComputed(sampleCount: Int) {
        lastComputedSampleCount = sampleCount
    }

    fun reset() {
        lastComputedSampleCount = 0
    }
}
