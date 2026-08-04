package com.wifiar.app.data.analysis

import com.wifiar.app.AppConfig
import com.wifiar.app.ar.Pose3D
import com.wifiar.app.data.interpolation.IdwInterpolator
import com.wifiar.app.data.local.RssiSampleEntity
import kotlin.math.max
import kotlin.system.measureTimeMillis

/**
 * Candidate router placement with predicted coverage metrics.
 */
data class RouterCandidate(
    val rank: Int,
    val position: Pose3D,
    /** score = coverageFraction − penalty × deadZoneFraction */
    val score: Float,
    val predictedCoverageFraction: Float,
    val predictedDeadZoneFraction: Float,
    val predictedAverageRssiDbm: Float,
)

/**
 * Current measured coverage (from real session samples via IDW) vs predicted
 * coverage if a router were moved to the #1 recommended spot.
 */
data class PlacementComparison(
    val currentCoverageFraction: Float,
    val currentDeadZoneFraction: Float,
    val recommendedCoverageFraction: Float,
    val recommendedDeadZoneFraction: Float,
    /** recommended − current (positive = improvement). */
    val coverageImprovementFraction: Float,
)

data class PlacementRecommendationResult(
    val candidates: List<RouterCandidate>,
    val comparison: PlacementComparison?,
    val sampleCount: Int,
    val computeTimeMs: Long,
    val pathLossExponent: Float,
    val notes: String =
        "Simplified log-distance model without wall/obstruction sensing — heuristic estimate only.",
)

/**
 * Grid-search optimizer for a single-router placement using [PathLossModel].
 *
 * Does **not** use measured wall geometry — only free-space-style path loss
 * with an indoor exponent. Best for relative ranking of positions inside the
 * already-mapped floor polygon (bounding box of samples).
 */
class RouterPlacementOptimizer(
    private val pathLoss: PathLossModel = PathLossModel.indoor(),
    private val idw: IdwInterpolator = IdwInterpolator(),
    private val candidateSpacingM: Float = AppConfig.ROUTER_CANDIDATE_SPACING_M,
    private val targetSpacingM: Float = AppConfig.ROUTER_TARGET_SPACING_M,
    private val txPowerDbm: Float = AppConfig.ROUTER_TX_POWER_DBM,
    private val coverageThreshold: Float = AppConfig.COVERAGE_THRESHOLD_DBM.toFloat(),
    private val deadThreshold: Float = AppConfig.DEAD_ZONE_THRESHOLD_DBM.toFloat(),
    private val deadPenalty: Float = AppConfig.ROUTER_DEAD_ZONE_PENALTY,
    private val topK: Int = 3,
) {

    /**
     * Run optimization over the session sample footprint.
     * Safe to call off the main thread.
     */
    fun optimize(samples: List<RssiSampleEntity>): PlacementRecommendationResult {
        if (samples.size < AppConfig.ROUTER_RECOMMEND_MIN_SAMPLES) {
            return PlacementRecommendationResult(
                candidates = emptyList(),
                comparison = null,
                sampleCount = samples.size,
                computeTimeMs = 0L,
                pathLossExponent = pathLoss.exponent,
            )
        }

        var result: PlacementRecommendationResult
        val elapsed = measureTimeMillis {
            val bounds = boundsOf(samples)
            val targets = buildGridPoints(
                bounds,
                targetSpacingM,
                y = samples.map { it.poseY }.average().toFloat(),
            )
            val candidates = buildGridPoints(
                bounds,
                candidateSpacingM,
                y = samples.map { it.poseY }.average().toFloat(),
            )

            val scored = ArrayList<RouterCandidate>(candidates.size)
            for (cand in candidates) {
                var covered = 0
                var dead = 0
                var sumRssi = 0.0
                val n = targets.size
                for (t in targets) {
                    val rssi = pathLoss.predictedRssi(cand, txPowerDbm, t)
                    sumRssi += rssi
                    if (rssi >= coverageThreshold) covered++
                    if (rssi <= deadThreshold) dead++
                }
                val coverF = if (n == 0) 0f else covered.toFloat() / n
                val deadF = if (n == 0) 0f else dead.toFloat() / n
                val score = coverF - deadPenalty * deadF
                scored.add(
                    RouterCandidate(
                        rank = 0,
                        position = cand,
                        score = score,
                        predictedCoverageFraction = coverF,
                        predictedDeadZoneFraction = deadF,
                        predictedAverageRssiDbm = if (n == 0) Float.NaN else (sumRssi / n).toFloat(),
                    ),
                )
            }

            scored.sortByDescending { it.score }
            val top = scored.take(topK).mapIndexed { i, c -> c.copy(rank = i + 1) }

            val comparison = buildComparison(samples, top.firstOrNull(), targets)

            result = PlacementRecommendationResult(
                candidates = top,
                comparison = comparison,
                sampleCount = samples.size,
                computeTimeMs = 0L, // filled below
                pathLossExponent = pathLoss.exponent,
            )
        }
        return result.copy(computeTimeMs = elapsed)
    }

    private fun buildComparison(
        samples: List<RssiSampleEntity>,
        best: RouterCandidate?,
        targets: List<Pose3D>,
    ): PlacementComparison? {
        if (best == null || targets.isEmpty()) return null

        // Current: real measured IDW over all session samples.
        val grid = idw.interpolate(samples)
        var valid = 0
        var covered = 0
        var dead = 0
        for (v in grid.values) {
            if (v.isNaN()) continue
            valid++
            if (v >= coverageThreshold) covered++
            if (v <= deadThreshold) dead++
        }
        val currentCover = if (valid == 0) 0f else covered.toFloat() / valid
        val currentDead = if (valid == 0) 0f else dead.toFloat() / valid

        return PlacementComparison(
            currentCoverageFraction = currentCover,
            currentDeadZoneFraction = currentDead,
            recommendedCoverageFraction = best.predictedCoverageFraction,
            recommendedDeadZoneFraction = best.predictedDeadZoneFraction,
            coverageImprovementFraction =
                best.predictedCoverageFraction - currentCover,
        )
    }

    private data class Bounds(
        val minX: Float,
        val maxX: Float,
        val minZ: Float,
        val maxZ: Float,
    )

    private fun boundsOf(samples: List<RssiSampleEntity>): Bounds {
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        for (s in samples) {
            if (s.poseX < minX) minX = s.poseX
            if (s.poseX > maxX) maxX = s.poseX
            if (s.poseZ < minZ) minZ = s.poseZ
            if (s.poseZ > maxZ) maxZ = s.poseZ
        }
        // Slight padding so candidates aren't only on measured footprints.
        val pad = candidateSpacingM
        return Bounds(minX - pad, maxX + pad, minZ - pad, maxZ + pad)
    }

    private fun buildGridPoints(bounds: Bounds, spacing: Float, y: Float): List<Pose3D> {
        val spanX = max(bounds.maxX - bounds.minX, spacing)
        val spanZ = max(bounds.maxZ - bounds.minZ, spacing)
        val cols = ((spanX / spacing).toInt() + 1).coerceIn(1, 80)
        val rows = ((spanZ / spacing).toInt() + 1).coerceIn(1, 80)
        val stepX = spanX / cols.coerceAtLeast(1)
        val stepZ = spanZ / rows.coerceAtLeast(1)
        val out = ArrayList<Pose3D>(cols * rows)
        val now = System.currentTimeMillis()
        for (r in 0 until rows) {
            val z = bounds.minZ + (r + 0.5f) * stepZ
            for (c in 0 until cols) {
                val x = bounds.minX + (c + 0.5f) * stepX
                out.add(Pose3D(x, y, z, now))
            }
        }
        return out
    }
}
