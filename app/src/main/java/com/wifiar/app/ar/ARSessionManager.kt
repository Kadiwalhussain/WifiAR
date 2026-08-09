package com.wifiar.app.ar

import android.util.Log
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages ARCore session state for WifiAR.
 *
 * SceneView's [io.github.sceneview.ar.ARSceneView] owns the native [Session]
 * (the camera cannot be shared across two sessions). Wire SceneView lifecycle
 * callbacks into this manager:
 *
 * - [onSessionCreated] ← `ARSceneView(onSessionCreated = …)`
 * - [onSessionResumed] ← `onSessionResumed`
 * - [onSessionPaused]  ← `onSessionPaused`
 * - [onFrame]          ← `onSessionUpdated`
 * - [close]            ← when leaving the AR screen / disposing
 *
 * Exposes device pose as [pose] / [poseFlow] and tracking quality for the UI.
 * [resetOrigin] re-bases the coordinate frame so the current position becomes
 * `(0, 0, 0)` without tearing down the camera feed (pair with remounting
 * `ARSceneView` via a Compose key for a full session restart).
 */
class ARSessionManager {

    private val _pose = MutableStateFlow(Pose3D.ZERO)
    val pose: StateFlow<Pose3D> = _pose.asStateFlow()

    /** Same stream as [pose], typed as [Flow] for collectors that prefer the interface. */
    val poseFlow: Flow<Pose3D> = pose

    private val _trackingFeedback = MutableStateFlow(
        trackingFeedback(TrackingState.STOPPED, null),
    )
    val trackingFeedback: StateFlow<TrackingFeedback> = _trackingFeedback.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _hasAchievedTracking = MutableStateFlow(false)
    /** Becomes true once [TrackingState.TRACKING] has been seen this session. */
    val hasAchievedTracking: StateFlow<Boolean> = _hasAchievedTracking.asStateFlow()

    private val _sessionError = MutableStateFlow<String?>(null)
    val sessionError: StateFlow<String?> = _sessionError.asStateFlow()

    @Volatile
    private var session: Session? = null

    /** World-space origin offset (metres). Subtracted from camera translation. */
    @Volatile
    private var originX = 0f

    @Volatile
    private var originY = 0f

    @Volatile
    private var originZ = 0f

    /** Last absolute camera translation (before origin subtract), used by [resetOrigin]. */
    @Volatile
    private var lastAbsX = 0f

    @Volatile
    private var lastAbsY = 0f

    @Volatile
    private var lastAbsZ = 0f

    @Volatile
    private var lastFailureReason: TrackingFailureReason? = null

    // region Lifecycle (bind to SceneView / Activity)

    /**
     * Called when ARCore [Session] is created (e.g. SceneView `onSessionCreated`).
     */
    fun onSessionCreated(session: Session) {
        this.session = session
        _sessionError.value = null
        _hasAchievedTracking.value = false
        Log.d(TAG, "AR session created")
    }

    /**
     * Called when the session is resumed (camera running).
     */
    fun onSessionResumed(session: Session) {
        this.session = session
        _isSessionActive.value = true
        _sessionError.value = null
        Log.d(TAG, "AR session resumed")
    }

    /**
     * Called when the session is paused (Activity onPause / composable leave).
     */
    fun onSessionPaused() {
        _isSessionActive.value = false
        publishTracking(TrackingState.PAUSED, lastFailureReason)
        Log.d(TAG, "AR session paused")
    }

    /**
     * Release references. Does not call [Session.close] when SceneView owns the
     * session — SceneView closes it. Safe to call on screen dispose.
     */
    fun close() {
        session = null
        _isSessionActive.value = false
        _hasAchievedTracking.value = false
        publishTracking(TrackingState.STOPPED, null)
        _pose.value = Pose3D.ZERO
        originX = 0f
        originY = 0f
        originZ = 0f
        Log.d(TAG, "AR session manager closed")
    }

    fun onSessionFailed(error: Exception) {
        Log.e(TAG, "AR session failed", error)
        _sessionError.value = error.message ?: error.javaClass.simpleName
        _isSessionActive.value = false
        publishTracking(TrackingState.STOPPED, TrackingFailureReason.BAD_STATE)
    }

    // endregion

    // region Frame updates

    /**
     * Ingest one ARCore [Frame]. Extract camera translation → [Pose3D] and
     * update tracking quality. Safe under TRACKING / PAUSED / STOPPED.
     */
    fun onFrame(frame: Frame) {
        try {
            val camera = frame.camera
            val trackingState = camera.trackingState
            val failure = camera.trackingFailureReason
                .takeUnless { it == TrackingFailureReason.NONE }

            lastFailureReason = failure
            publishTracking(trackingState, failure)

            if (trackingState == TrackingState.TRACKING) {
                _hasAchievedTracking.value = true
                val t = camera.pose.translation
                lastAbsX = t[0]
                lastAbsY = t[1]
                lastAbsZ = t[2]
                _pose.value = Pose3D(
                    x = lastAbsX - originX,
                    y = lastAbsY - originY,
                    z = lastAbsZ - originZ,
                    timestampMs = System.currentTimeMillis(),
                )
            }
            // When not TRACKING, keep last known pose (do not zero — less jarring in UI).
        } catch (t: Throwable) {
            // Covering the camera / session glitches must never crash the app.
            Log.w(TAG, "onFrame swallowed error: ${t.message}")
        }
    }

    /**
     * Optional: SceneView [onTrackingFailureChanged] feed for finer failure reasons
     * between frames.
     */
    fun onTrackingFailureChanged(reason: TrackingFailureReason?) {
        lastFailureReason = reason
        val state = _trackingFeedback.value.trackingState
        if (state != TrackingState.TRACKING) {
            publishTracking(state, reason)
        }
    }

    // endregion

    // region Origin

    /**
     * Rebases the coordinate origin to the current camera position so the next
     * pose reads approximately `(0, 0, 0)`. Does not destroy the AR session.
     *
     * For a full ARCore session restart, the UI should also remount [ARSceneView]
     * (Compose `key`) and call [close] + re-bind lifecycle.
     */
    fun resetOrigin() {
        setWorldOrigin(lastAbsX, lastAbsY, lastAbsZ)
        Log.d(TAG, "Origin reset at ($originX, $originY, $originZ)")
    }

    /**
     * ARCore world-space position of the mapping origin (where relative pose is 0).
     * Use this when hosting a Cloud Anchor so multi-day resolve can re-align samples.
     */
    fun worldOriginTranslation(): FloatArray = floatArrayOf(originX, originY, originZ)

    /** Last camera translation in ARCore world space (before origin subtract). */
    fun absoluteCameraTranslation(): FloatArray = floatArrayOf(lastAbsX, lastAbsY, lastAbsZ)

    /**
     * Set the mapping origin to an absolute ARCore world translation
     * (e.g. after resolving a Cloud Anchor). Relative [pose] is recomputed from
     * the last known camera absolute position.
     */
    fun setWorldOrigin(worldX: Float, worldY: Float, worldZ: Float) {
        originX = worldX
        originY = worldY
        originZ = worldZ
        _pose.value = Pose3D(
            x = lastAbsX - originX,
            y = lastAbsY - originY,
            z = lastAbsZ - originZ,
            timestampMs = System.currentTimeMillis(),
        )
        Log.d(TAG, "World origin set to ($originX, $originY, $originZ)")
    }

    /**
     * Full logical reset used when remounting the AR view: clears origin offset
     * and tracking-achieved flag so onboarding can show again.
     */
    fun resetSessionState() {
        originX = 0f
        originY = 0f
        originZ = 0f
        lastAbsX = 0f
        lastAbsY = 0f
        lastAbsZ = 0f
        lastFailureReason = null
        _hasAchievedTracking.value = false
        _pose.value = Pose3D.ZERO
        _sessionError.value = null
        publishTracking(TrackingState.STOPPED, null)
        Log.d(TAG, "Session state fully reset")
    }

    // endregion

    private fun publishTracking(
        state: TrackingState,
        failure: TrackingFailureReason?,
    ) {
        _trackingFeedback.value = trackingFeedback(state, failure)
    }

    companion object {
        private const val TAG = "ARSessionManager"
    }
}
