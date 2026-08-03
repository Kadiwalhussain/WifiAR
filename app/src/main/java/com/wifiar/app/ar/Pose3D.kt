package com.wifiar.app.ar

/**
 * Device position in the current AR coordinate frame (metres).
 *
 * Coordinates are relative to the session origin (or the last
 * [ARSessionManager.resetOrigin] call). Units match ARCore: 1 unit ≈ 1 metre.
 */
data class Pose3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampMs: Long,
) {
    companion object {
        val ZERO = Pose3D(0f, 0f, 0f, 0L)
    }
}
