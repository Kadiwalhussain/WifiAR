package com.wifiar.app.data.analysis

import com.wifiar.app.ar.Pose3D

/**
 * In-memory cache so Live Mapping can show the last #1 router recommendation
 * AR marker when the user returns to mapping (same process session).
 */
object RecommendationCache {
    @Volatile
    var lastSessionId: String? = null
        private set

    @Volatile
    var topPosition: Pose3D? = null
        private set

    @Volatile
    var predictedCoverageFraction: Float? = null
        private set

    fun put(sessionId: String, top: RouterCandidate?) {
        lastSessionId = sessionId
        topPosition = top?.position
        predictedCoverageFraction = top?.predictedCoverageFraction
    }

    fun clear() {
        lastSessionId = null
        topPosition = null
        predictedCoverageFraction = null
    }
}
