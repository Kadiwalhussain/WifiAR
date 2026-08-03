package com.wifiar.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ar.core.Config
import com.wifiar.app.R
import com.wifiar.app.ar.ARSessionManager
import com.wifiar.app.ar.TrackingQuality
import io.github.sceneview.ar.ARSceneView

/**
 * Live AR camera passthrough with device pose debug HUD.
 *
 * Independent of the WiFi scanner (Part 1) — no coupling to `scanner/` yet.
 */
@Composable
fun ARScannerScreen(
    modifier: Modifier = Modifier,
) {
    val sessionManager = remember { ARSessionManager() }
    var sessionGeneration by remember { mutableIntStateOf(0) }

    DisposableEffect(sessionManager) {
        onDispose { sessionManager.close() }
    }

    val pose by sessionManager.pose.collectAsStateWithLifecycle()
    val feedback by sessionManager.trackingFeedback.collectAsStateWithLifecycle()
    val hasAchievedTracking by sessionManager.hasAchievedTracking.collectAsStateWithLifecycle()
    val sessionError by sessionManager.sessionError.collectAsStateWithLifecycle()

    val showCalibrationOverlay =
        !hasAchievedTracking && sessionError == null

    Box(modifier = modifier.fillMaxSize()) {
        // Remount ARSceneView on Reset Origin for a fresh ARCore session + origin.
        key(sessionGeneration) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                planeRenderer = true,
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
                onSessionCreated = { session ->
                    sessionManager.onSessionCreated(session)
                },
                onSessionResumed = { session ->
                    sessionManager.onSessionResumed(session)
                },
                onSessionPaused = {
                    sessionManager.onSessionPaused()
                },
                onSessionFailed = { error ->
                    sessionManager.onSessionFailed(error)
                },
                onSessionUpdated = { _, frame ->
                    sessionManager.onFrame(frame)
                },
                onTrackingFailureChanged = { reason ->
                    sessionManager.onTrackingFailureChanged(reason)
                },
            )
        }

        // Debug HUD — pose + tracking state
        PoseDebugHud(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
            x = pose.x,
            y = pose.y,
            z = pose.z,
            stateLabel = feedback.stateLabel,
            quality = feedback.quality,
        )

        // First-launch / lost-tracking guidance
        AnimatedVisibility(
            visible = showCalibrationOverlay ||
                (feedback.quality != TrackingQuality.TRACKING && hasAchievedTracking),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
        ) {
            val message = when {
                showCalibrationOverlay ->
                    stringResource(R.string.ar_calibration_hint)
                feedback.userMessage != null ->
                    feedback.userMessage!!
                else ->
                    stringResource(R.string.ar_tracking_lost_generic)
            }
            TrackingHintBanner(message = message)
        }

        sessionError?.let { error ->
            Text(
                text = stringResource(R.string.ar_session_error, error),
                color = Color(0xFFFFCDD2),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                textAlign = TextAlign.Center,
            )
        }

        FloatingActionButton(
            onClick = {
                // Logical origin reset + full session remount for a clean mapping start.
                sessionManager.close()
                sessionManager.resetSessionState()
                sessionGeneration += 1
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text(
                text = stringResource(R.string.ar_reset_origin),
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PoseDebugHud(
    x: Float,
    y: Float,
    z: Float,
    stateLabel: String,
    quality: TrackingQuality,
    modifier: Modifier = Modifier,
) {
    val stateColor = when (quality) {
        TrackingQuality.TRACKING -> Color(0xFF69F0AE)
        TrackingQuality.PAUSED -> Color(0xFFFFF176)
        TrackingQuality.LIMITED -> Color(0xFFFFAB40)
        TrackingQuality.STOPPED -> Color(0xFFFF8A80)
    }

    Column(
        modifier = modifier
            .background(Color(0xBB000000), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringResource(R.string.ar_pose_debug_title),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "x %+7.3f m".format(x),
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "y %+7.3f m".format(y),
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "z %+7.3f m".format(z),
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stateLabel,
            color = stateColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun TrackingHintBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xCC0D47A1), RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.ar_calibration_title),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

