package com.wifiar.app.data.interpolation

/**
 * Dense IDW result over a horizontal (x, z) region.
 *
 * [values] is row-major: index = row * cols + col, where
 * row increases with +z and col with +x.
 */
data class InterpolationGrid(
    val minX: Float,
    val maxX: Float,
    val minZ: Float,
    val maxZ: Float,
    val cellSize: Float,
    val cols: Int,
    val rows: Int,
    /** Interpolated RSSI (dBm) per cell; NaN means no estimate. */
    val values: FloatArray,
    val sampleCount: Int,
    val computeTimeMs: Long,
) {
    val widthMeters: Float get() = maxX - minX
    val depthMeters: Float get() = maxZ - minZ
    val centerX: Float get() = (minX + maxX) * 0.5f
    val centerZ: Float get() = (minZ + maxZ) * 0.5f

    fun valueAt(col: Int, row: Int): Float {
        if (col !in 0 until cols || row !in 0 until rows) return Float.NaN
        return values[row * cols + col]
    }

    companion object {
        val EMPTY = InterpolationGrid(
            minX = 0f,
            maxX = 0f,
            minZ = 0f,
            maxZ = 0f,
            cellSize = 0.3f,
            cols = 0,
            rows = 0,
            values = FloatArray(0),
            sampleCount = 0,
            computeTimeMs = 0L,
        )
    }
}
