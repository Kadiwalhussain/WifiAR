package com.wifiar.app.data.analysis

import com.wifiar.app.AppConfig
import com.wifiar.app.data.interpolation.InterpolationGrid
import java.util.ArrayDeque

/**
 * Finds contiguous dead-zone regions on an IDW [InterpolationGrid].
 *
 * A cell is "dead" when its interpolated RSSI is ≤ [thresholdDbm]. Adjacent
 * dead cells (4-connected) are flood-filled into regions.
 */
class DeadZoneDetector(
    private val thresholdDbm: Float = AppConfig.DEAD_ZONE_THRESHOLD_DBM.toFloat(),
    private val minCells: Int = AppConfig.DEAD_ZONE_MIN_CELLS,
) {

    /**
     * Detect all dead-zone regions on [grid].
     *
     * Safe to call off the main thread.
     */
    fun detect(grid: InterpolationGrid): List<DeadZoneRegion> {
        if (grid.cols <= 0 || grid.rows <= 0 || grid.values.isEmpty()) {
            return emptyList()
        }

        val cols = grid.cols
        val rows = grid.rows
        val visited = BooleanArray(cols * rows)
        val regions = ArrayList<DeadZoneRegion>()
        var nextId = 1

        val stepX = if (cols > 0) grid.widthMeters / cols else grid.cellSize
        val stepZ = if (rows > 0) grid.depthMeters / rows else grid.cellSize
        val cellArea = stepX * stepZ

        fun index(c: Int, r: Int) = r * cols + c

        fun isDead(c: Int, r: Int): Boolean {
            val v = grid.valueAt(c, r)
            return !v.isNaN() && v <= thresholdDbm
        }

        fun worldX(c: Int) = grid.minX + (c + 0.5f) * stepX
        fun worldZ(r: Int) = grid.minZ + (r + 0.5f) * stepZ

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val i = index(c, r)
                if (visited[i] || !isDead(c, r)) continue

                // Flood-fill (BFS) this connected component.
                val queue = ArrayDeque<Pair<Int, Int>>()
                queue.add(c to r)
                visited[i] = true

                var sumX = 0.0
                var sumZ = 0.0
                var sumRssi = 0.0
                var worst = Float.POSITIVE_INFINITY
                var count = 0

                while (queue.isNotEmpty()) {
                    val (cc, rr) = queue.removeFirst()
                    val rssi = grid.valueAt(cc, rr)
                    sumX += worldX(cc)
                    sumZ += worldZ(rr)
                    sumRssi += rssi
                    if (rssi < worst) worst = rssi
                    count++

                    // 4-connected neighbours
                    val neighbors = arrayOf(
                        cc - 1 to rr,
                        cc + 1 to rr,
                        cc to rr - 1,
                        cc to rr + 1,
                    )
                    for ((nc, nr) in neighbors) {
                        if (nc !in 0 until cols || nr !in 0 until rows) continue
                        val ni = index(nc, nr)
                        if (visited[ni]) continue
                        if (!isDead(nc, nr)) continue
                        visited[ni] = true
                        queue.add(nc to nr)
                    }
                }

                if (count < minCells) continue

                regions.add(
                    DeadZoneRegion(
                        id = nextId++,
                        centroidX = (sumX / count).toFloat(),
                        centroidZ = (sumZ / count).toFloat(),
                        areaSqM = count * cellArea,
                        worstRssiDbm = worst,
                        averageRssiDbm = (sumRssi / count).toFloat(),
                        cellCount = count,
                    ),
                )
            }
        }

        // Largest / worst first for UI.
        return regions.sortedWith(
            compareBy<DeadZoneRegion> { it.worstRssiDbm }
                .thenByDescending { it.areaSqM },
        )
    }
}
