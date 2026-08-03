package com.wifiar.app.ar

import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState

/**
 * UI-facing tracking status derived from ARCore [TrackingState] + failure reason.
 */
enum class TrackingQuality {
    /** Camera pose is updating reliably. */
    TRACKING,

    /** Temporary pause (e.g. app backgrounded or ARCore paused tracking). */
    PAUSED,

    /** Tracking not available / never started. */
    STOPPED,

    /** Tracking lost with a recoverable reason — show guidance. */
    LIMITED,
}

/**
 * User-facing guidance when tracking is not solid.
 */
data class TrackingFeedback(
    val quality: TrackingQuality,
    val trackingState: TrackingState,
    val failureReason: TrackingFailureReason?,
    /** Short label for debug HUD (TRACKING / PAUSED / …). */
    val stateLabel: String,
    /** Actionable hint for the user, or null when tracking is healthy. */
    val userMessage: String?,
)

fun trackingFeedback(
    trackingState: TrackingState,
    failureReason: TrackingFailureReason?,
): TrackingFeedback {
    val quality = when (trackingState) {
        TrackingState.TRACKING -> TrackingQuality.TRACKING
        TrackingState.PAUSED -> TrackingQuality.PAUSED
        TrackingState.STOPPED -> {
            if (failureReason != null && failureReason != TrackingFailureReason.NONE) {
                TrackingQuality.LIMITED
            } else {
                TrackingQuality.STOPPED
            }
        }
    }

    val label = when (quality) {
        TrackingQuality.TRACKING -> "TRACKING"
        TrackingQuality.PAUSED -> "PAUSED"
        TrackingQuality.STOPPED -> "STOPPED"
        TrackingQuality.LIMITED -> "LIMITED"
    }

    val message = when {
        trackingState == TrackingState.TRACKING -> null
        failureReason == TrackingFailureReason.INSUFFICIENT_LIGHT ->
            "Not enough light — try a brighter area"
        failureReason == TrackingFailureReason.EXCESSIVE_MOTION ->
            "Moving too fast — move the device slowly"
        failureReason == TrackingFailureReason.INSUFFICIENT_FEATURES ->
            "Point at a textured surface"
        failureReason == TrackingFailureReason.CAMERA_UNAVAILABLE ->
            "Camera unavailable — close other camera apps"
        failureReason == TrackingFailureReason.BAD_STATE ->
            "AR session error — try Reset Origin"
        trackingState == TrackingState.PAUSED ->
            "Tracking paused"
        else ->
            "Slowly move your phone to help it understand the space"
    }

    return TrackingFeedback(
        quality = quality,
        trackingState = trackingState,
        failureReason = failureReason,
        stateLabel = label,
        userMessage = message,
    )
}
