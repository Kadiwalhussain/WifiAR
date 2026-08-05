package com.wifiar.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ar.core.Config
import com.wifiar.app.AppConfig
import com.wifiar.app.R
import com.wifiar.app.ar.ARSessionManager
import com.wifiar.app.ar.CloudAnchorManager
import com.wifiar.app.ar.HeatmapMeshBuilder
import com.wifiar.app.ar.HeatmapPlane
import com.wifiar.app.ar.TrackingQuality
import com.wifiar.app.data.DataFusionEngine
import com.wifiar.app.data.SessionManager
import com.wifiar.app.data.UserPreferences
import com.wifiar.app.data.analysis.BestNetworkEstimate
import com.wifiar.app.data.analysis.DeadZoneDetector
import com.wifiar.app.data.analysis.DeadZoneRegion
import com.wifiar.app.data.analysis.NetworkComparisonEngine
import com.wifiar.app.data.analysis.RecommendationCache
import com.wifiar.app.data.interpolation.HeatmapRecomputeGate
import com.wifiar.app.data.interpolation.IdwInterpolator
import com.wifiar.app.data.local.MappingSessionEntity
import com.wifiar.app.data.local.RssiSampleEntity
import com.wifiar.app.data.local.SpeedTestEntity
import com.wifiar.app.data.local.WifiArDatabase
import com.wifiar.app.data.speedtest.SpeedTestError
import com.wifiar.app.data.speedtest.SpeedTestManager
import com.wifiar.app.data.speedtest.SpeedTestOutcome
import com.wifiar.app.data.sync.SyncManager
import com.wifiar.app.scanner.WifiScanner
import com.wifiar.app.ui.components.CompactPrimaryButton
import com.wifiar.app.ui.components.GlassPanel
import com.wifiar.app.ui.components.SegmentedControl
import com.wifiar.app.ui.components.StatusPill
import com.wifiar.app.ui.theme.NeonCyan
import com.wifiar.app.ui.theme.NeonMint
import com.wifiar.app.ui.theme.PanelDark
import com.wifiar.app.util.SpeedFormat
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.rememberOnGestureListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot


/** Visualization mode for Live Mapping. */
enum class MappingViewMode {
    RAW_POINTS,
    HEATMAP,
    BOTH,
}

private const val DEAD_ZONE_NODE_PREFIX = "deadzone:"
private const val SPEED_TEST_NODE_PREFIX = "speedtest:"

/**
 * Live mapping: AR camera + fusion + heatmap + dead zones + speed tests (Part 6).
 */

@Composable
fun LiveMappingScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { WifiArDatabase.getInstance(context) }

    val wifiScanner = remember { WifiScanner(context, scope) }
    val arSessionManager = remember { ARSessionManager() }
    val sessionManager = remember { SessionManager(db.mappingSessionDao()) }
    val fusionEngine = remember {
        DataFusionEngine(
            wifiScanner = wifiScanner,
            arSessionManager = arSessionManager,
            sampleDao = db.rssiSampleDao(),
            scope = scope,
        )
    }
    val idw = remember(UserPreferences.gridCellSizeM) {
        IdwInterpolator(cellSize = UserPreferences.gridCellSizeM)
    }
    val heatmapBuilder = remember(UserPreferences.rssiDeadDbm) {
        HeatmapMeshBuilder(deadZoneThresholdDbm = UserPreferences.rssiDeadDbm.toFloat())
    }
    val deadZoneDetector = remember(UserPreferences.rssiDeadDbm) {
        DeadZoneDetector(thresholdDbm = UserPreferences.rssiDeadDbm.toFloat())
    }
    val networkCompareEngine = remember { NetworkComparisonEngine() }
    val recomputeGate = remember { HeatmapRecomputeGate() }
    val cloudAnchors = remember { CloudAnchorManager(context) }
    val speedTestManager = remember {
        SpeedTestManager(context, db.speedTestDao())
    }
    val syncManager = remember { SyncManager(context) }
    var arSession by remember { mutableStateOf<com.google.ar.core.Session?>(null) }
    var resumeCandidates by remember {
        mutableStateOf<List<MappingSessionEntity>>(emptyList())
    }
    var showResumeDialog by remember { mutableStateOf(false) }
    var cloudStatus by remember { mutableStateOf<String?>(null) }




    DisposableEffect(wifiScanner, arSessionManager, fusionEngine) {
        wifiScanner.start()
        onDispose {
            fusionEngine.stop()
            wifiScanner.stop()
            arSessionManager.close()
        }
    }

    val activeSession by sessionManager.activeSession.collectAsStateWithLifecycle()
    val samples by fusionEngine.currentSessionSamples.collectAsStateWithLifecycle()
    val isFusing by fusionEngine.isFusing.collectAsStateWithLifecycle()
    val fusionError by fusionEngine.lastFusionError.collectAsStateWithLifecycle()
    val cooldown by wifiScanner.cooldownSeconds.collectAsStateWithLifecycle()
    val isScanning by wifiScanner.isScanning.collectAsStateWithLifecycle()
    val tracking by arSessionManager.trackingFeedback.collectAsStateWithLifecycle()
    val hasAchievedTracking by arSessionManager.hasAchievedTracking.collectAsStateWithLifecycle()
    val pose by arSessionManager.pose.collectAsStateWithLifecycle()
    val speedTestRunning by speedTestManager.isRunning.collectAsStateWithLifecycle()

    val sessionId = activeSession?.sessionId
    val speedTests by remember(sessionId) {
        if (sessionId != null) {
            speedTestManager.observeForSession(sessionId)
        } else {
            flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    var showStartDialog by remember { mutableStateOf(false) }
    var locationName by remember { mutableStateOf("") }
    var viewMode by rememberSaveable { mutableStateOf(MappingViewMode.BOTH) }
    var heatmapPlane by remember { mutableStateOf<HeatmapPlane?>(null) }
    var deadZones by remember { mutableStateOf<List<DeadZoneRegion>>(emptyList()) }
    var lastComputeMs by remember { mutableStateOf<Long?>(null) }
    var selectedDeadZone by remember { mutableStateOf<DeadZoneRegion?>(null) }
    var selectedSpeedTest by remember { mutableStateOf<SpeedTestEntity?>(null) }
    var speedTestError by remember { mutableStateOf<String?>(null) }
    var bestNetwork by remember { mutableStateOf<BestNetworkEstimate?>(null) }

    LaunchedEffect(activeSession?.sessionId, samples.size) {
        if (activeSession == null && samples.isEmpty()) {
            heatmapPlane = null
            deadZones = emptyList()
            recomputeGate.reset()
            lastComputeMs = null
            selectedDeadZone = null
            selectedSpeedTest = null
            speedTestError = null
            bestNetwork = null
        }
    }

    // "Best Network Here" — recompute as the user walks (throttled).
    LaunchedEffect(samples, pose.x, pose.z, tracking.quality) {
        if (samples.isEmpty() || tracking.quality != TrackingQuality.TRACKING) {
            bestNetwork = null
            return@LaunchedEffect
        }
        val estimate = withContext(Dispatchers.Default) {
            networkCompareEngine.bestNetworkAt(pose.x, pose.z, samples)
        }
        bestNetwork = estimate
        // Don't recompute every frame: brief pause so UI stays smooth.
        delay(350L)
    }



    // Throttled IDW + dead-zone recompute.
    LaunchedEffect(samples, viewMode) {
        val needsAnalysis =
            viewMode == MappingViewMode.HEATMAP || viewMode == MappingViewMode.BOTH
        if (!needsAnalysis) return@LaunchedEffect
        if (!recomputeGate.shouldRecompute(samples.size)) return@LaunchedEffect

        val snapshot = samples.toList()
        val result = withContext(Dispatchers.Default) {
            val grid = idw.interpolate(snapshot)
            if (grid.cols == 0) {
                AnalysisResult(plane = null, zones = emptyList(), computeMs = 0L)
            } else {
                val plane = heatmapBuilder.build(grid, snapshot)
                val zones = deadZoneDetector.detect(grid)
                AnalysisResult(plane, zones, grid.computeTimeMs)
            }
        }
        if (result.plane != null) {
            val previous = heatmapPlane
            // Swap first — never recycle a bitmap still bound to ImageNode (native crash).
            heatmapPlane = result.plane
            deadZones = result.zones
            lastComputeMs = result.computeMs
            recomputeGate.markComputed(snapshot.size)
            selectedDeadZone?.let { sel ->
                if (result.zones.none { it.id == sel.id }) selectedDeadZone = null
            }
            if (previous != null && previous.bitmap !== result.plane.bitmap) {
                scope.launch {
                    delay(750L)
                    runCatching {
                        val stillOld = previous.bitmap
                        if (!stillOld.isRecycled && stillOld !== heatmapPlane?.bitmap) {
                            stillOld.recycle()
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(isFusing) {
        if (!isFusing) return@LaunchedEffect
        while (isActive) {
            val cd = wifiScanner.cooldownSeconds.value
            val scanning = wifiScanner.isScanning.value
            if (cd == 0L && !scanning) {
                wifiScanner.triggerScan()
            }
            delay(1_000L)
        }
    }

    var lastSpeedBanner by remember { mutableStateOf<String?>(null) }

    /** Cap AR spheres — one ball per spatial cell, stable node count. */
    val arDisplaySamples = remember(samples) {
        downsampleSamplesForAr(samples, AppConfig.AR_MAX_SAMPLE_SPHERES)
    }

    suspend fun beginFreshSession(name: String) {
        val oldPlane = heatmapPlane
        heatmapPlane = null
        deadZones = emptyList()
        selectedDeadZone = null
        recomputeGate.reset()
        cloudStatus = null
        lastSpeedBanner = null
        fusionEngine.stop()
        // Recycle after unbinding from AR scene.
        if (oldPlane != null) {
            scope.launch {
                delay(400L)
                runCatching { if (!oldPlane.bitmap.isRecycled) oldPlane.bitmap.recycle() }
            }
        }
        val session = sessionManager.startSession(name)
        if (session == null) {
            cloudStatus = "Could not start session — try again"
            return
        }
        fusionEngine.start(session.sessionId)
        wifiScanner.triggerScan()
    }

    suspend fun beginResumedSession(past: MappingSessionEntity) {
        val oldPlane = heatmapPlane
        heatmapPlane = null
        deadZones = emptyList()
        recomputeGate.reset()
        lastSpeedBanner = null
        fusionEngine.stop()
        if (oldPlane != null) {
            scope.launch {
                delay(400L)
                runCatching { if (!oldPlane.bitmap.isRecycled) oldPlane.bitmap.recycle() }
            }
        }
        val resumed = sessionManager.resumeSession(past.sessionId)
        if (resumed == null) {
            cloudStatus = context.getString(R.string.cloud_resume_failed)
            beginFreshSession(past.locationName)
            return
        }
        fusionEngine.start(resumed.sessionId)
        wifiScanner.triggerScan()
        val cloudId = past.cloudAnchorId
        val sess = arSession
        if (!cloudId.isNullOrBlank() && sess != null && cloudAnchors.isApiKeyConfigured()) {
            cloudStatus = context.getString(R.string.cloud_resolving)
            val result = runCatching {
                withTimeoutOrNull(AppConfig.CLOUD_ANCHOR_TIMEOUT_SEC * 1_000) {
                    cloudAnchors.resolve(sess, cloudId)
                }
            }.getOrNull()
            when (result) {
                is CloudAnchorManager.ResolveResult.Success -> {
                    arSessionManager.resetOrigin()
                    cloudStatus = context.getString(R.string.cloud_resume_ok)
                    runCatching { result.anchor.detach() }
                }
                is CloudAnchorManager.ResolveResult.Failure -> {
                    cloudStatus = context.getString(
                        R.string.cloud_resume_fallback,
                        result.reason,
                    )
                }
                null -> {
                    cloudStatus = context.getString(
                        R.string.cloud_resume_fallback,
                        "timeout",
                    )
                }
            }
        } else {
            cloudStatus = context.getString(R.string.cloud_resume_local_only)
        }
    }

    if (showStartDialog) {
        StartSessionDialog(
            locationName = locationName,
            onLocationNameChange = { locationName = it },
            onDismiss = { showStartDialog = false },
            onConfirm = {
                showStartDialog = false
                scope.launch {
                    resumeCandidates = cloudAnchors.findResumableSessions(locationName)
                    if (resumeCandidates.isNotEmpty()) {
                        showResumeDialog = true
                    } else {
                        beginFreshSession(locationName)
                    }
                }
            },
        )
    }

    if (showResumeDialog) {
        ResumeMappingDialog(
            candidates = resumeCandidates,
            cloudReady = cloudAnchors.isApiKeyConfigured(),
            onResume = { past ->
                showResumeDialog = false
                scope.launch { beginResumedSession(past) }
            },
            onNewSession = {
                showResumeDialog = false
                scope.launch { beginFreshSession(locationName) }
            },
            onDismiss = { showResumeDialog = false },
        )
    }

    selectedDeadZone?.let { zone ->
        DeadZoneDetailDialog(
            zone = zone,
            onDismiss = { selectedDeadZone = null },
        )
    }

    selectedSpeedTest?.let { test ->
        SpeedTestDetailDialog(
            test = test,
            onDismiss = { selectedSpeedTest = null },
        )
    }

    val showRaw =
        viewMode == MappingViewMode.RAW_POINTS || viewMode == MappingViewMode.BOTH
    val showHeatmap =
        viewMode == MappingViewMode.HEATMAP || viewMode == MappingViewMode.BOTH

    val trackingStable = tracking.quality == TrackingQuality.TRACKING
    val canRunSpeedTest =
        activeSession != null && trackingStable && !speedTestRunning

    val gestureListener = rememberOnGestureListener(
        onSingleTapUp = { _, node ->
            val name = node?.name ?: return@rememberOnGestureListener
            when {
                name.startsWith(DEAD_ZONE_NODE_PREFIX) -> {
                    val id = name.removePrefix(DEAD_ZONE_NODE_PREFIX).toIntOrNull()
                        ?: return@rememberOnGestureListener
                    selectedDeadZone = deadZones.firstOrNull { it.id == id }
                }
                name.startsWith(SPEED_TEST_NODE_PREFIX) -> {
                    val id = name.removePrefix(SPEED_TEST_NODE_PREFIX).toLongOrNull()
                        ?: return@rememberOnGestureListener
                    selectedSpeedTest = speedTests.firstOrNull { it.id == id }
                }
            }
        },
    )

    Box(modifier = modifier.fillMaxSize()) {

        // Ground first: no signal overlays until AR tracking is solid.
        val groundReady = hasAchievedTracking &&
            tracking.quality == TrackingQuality.TRACKING

        val pathSamples = remember(arDisplaySamples) {
            arDisplaySamples
                .filter {
                    it.poseX.isFinite() && it.poseZ.isFinite() &&
                        abs(it.poseX) < 100f && abs(it.poseZ) < 100f
                }
                .sortedBy { it.timestampMs }
                .takeLast(AppConfig.AR_MAX_SAMPLE_SPHERES)
        }
        val floorYHint = remember(heatmapPlane?.floorY, pose.y, groundReady) {
            when {
                heatmapPlane?.floorY?.isFinite() == true -> heatmapPlane!!.floorY
                groundReady && pose.y.isFinite() ->
                    pose.y - AppConfig.AR_BALL_HEIGHT_ABOVE_FLOOR_M
                else -> 0f
            }
        }
        val pathFloorY = floorYHint + 0.015f
        val ballHeight = pathFloorY + AppConfig.AR_BALL_HEIGHT_ABOVE_FLOOR_M
        val pathPoints = remember(pathSamples, pathFloorY) {
            buildWalkPathPoints(
                pathSamples.takeLast(AppConfig.AR_MAX_PATH_POINTS),
                pathFloorY,
            )
        }
        val labeledIds = remember(pathSamples) {
            pathSamples.takeLast(AppConfig.AR_MAX_RSSI_LABELS).map { it.id }.toSet()
        }
        val showSignals = groundReady && activeSession != null

        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            // Planes help user understand the ground first; light cost vs hundreds of nodes.
            planeRenderer = true,
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL,
            sessionConfiguration = { session, config ->
                cloudAnchors.applyCloudConfig(session, config)
            },
            onSessionCreated = {
                arSession = it
                arSessionManager.onSessionCreated(it)
            },
            onSessionResumed = {
                arSession = it
                arSessionManager.onSessionResumed(it)
            },
            onSessionPaused = { arSessionManager.onSessionPaused() },
            onSessionFailed = { arSessionManager.onSessionFailed(it) },
            onSessionUpdated = { _, frame ->
                runCatching { arSessionManager.onFrame(frame) }
            },
            onTrackingFailureChanged = {
                runCatching { arSessionManager.onTrackingFailureChanged(it) }
            },
            onGestureListener = gestureListener,
        ) {
            val loader = materialLoader
            fun unlit(color: Color) = runCatching {
                loader.createUnlitColorInstance(color)
            }.getOrElse {
                loader.createColorInstance(color = color, metallic = 0f, roughness = 0.5f)
            }
            val matStrong = remember(loader) { unlit(Color(0xFF66BB6A)) }
            val matMedium = remember(loader) { unlit(Color(0xFFFFEE58)) }
            val matWeak = remember(loader) { unlit(Color(0xFFFF7043)) }
            val matDead = remember(loader) { unlit(Color(0xFFE53935)) }
            val matPath = remember(loader) { unlit(Color(0xFF00E5C3)) }
            val matRouter = remember(loader) { unlit(Color(0xFFFF6F00)) }
            val matSpeed = remember(loader) { unlit(Color(0xFF00B8D4)) }

            if (showSignals) {
                // 1) Path on floor
                if (pathPoints.size >= 2) {
                    key("walk-path") {
                        PathNode(
                            points = pathPoints,
                            closed = false,
                            materialInstance = matPath,
                        )
                    }
                }

                // 2) Heatmap
                if (showHeatmap) {
                    heatmapPlane?.let { plane ->
                        val bmp = plane.bitmap
                        if (!bmp.isRecycled &&
                            plane.widthMeters.isFinite() &&
                            plane.depthMeters.isFinite() &&
                            plane.widthMeters > 0.1f &&
                            plane.depthMeters > 0.1f
                        ) {
                            key(plane.version) {
                                ImageNode(
                                    bitmap = bmp,
                                    size = Size(
                                        x = plane.widthMeters.coerceIn(0.1f, 40f),
                                        y = 0.002f,
                                        z = plane.depthMeters.coerceIn(0.1f, 40f),
                                    ),
                                    position = Position(
                                        x = plane.centerX.safeCoord(),
                                        y = plane.floorY.safeCoord(pathFloorY),
                                        z = plane.centerZ.safeCoord(),
                                    ),
                                    normal = Direction(y = 1.0f),
                                )
                            }
                        }
                    }
                    deadZones.take(3).forEach { zone ->
                        key("dz-${zone.id}") {
                            SphereNode(
                                radius = AppConfig.DEAD_ZONE_MARKER_RADIUS_M,
                                position = Position(
                                    x = zone.centroidX.safeCoord(),
                                    y = (pathFloorY + 0.2f).safeCoord(),
                                    z = zone.centroidZ.safeCoord(),
                                ),
                                materialInstance = matDead,
                                apply = { name = "$DEAD_ZONE_NODE_PREFIX${zone.id}" },
                            )
                        }
                    }
                }

                // 3) Signal balls (RSSI). Yellow = medium strength.
                if (showRaw) {
                    pathSamples.forEach { sample ->
                        key("b-${sample.id}") {
                            val mat = when {
                                sample.rssiDbm >= UserPreferences.rssiStrongDbm -> matStrong
                                sample.rssiDbm >= UserPreferences.rssiMediumDbm -> matMedium
                                sample.rssiDbm >= UserPreferences.rssiDeadDbm -> matWeak
                                else -> matDead
                            }
                            val x = sample.poseX.safeCoord()
                            val z = sample.poseZ.safeCoord()
                            val y = ballHeight.safeCoord(1.1f)
                            SphereNode(
                                radius = AppConfig.SAMPLE_SPHERE_RADIUS_M,
                                position = Position(x = x, y = y, z = z),
                                materialInstance = mat,
                            )
                            if (sample.id in labeledIds) {
                                TextNode(
                                    text = "${sample.rssiDbm} dBm",
                                    fontSize = 40f,
                                    textColor = android.graphics.Color.WHITE,
                                    backgroundColor = 0x99000000.toInt(),
                                    widthMeters = 0.38f,
                                    heightMeters = 0.12f,
                                    position = Position(x = x, y = y + 0.14f, z = z),
                                )
                            }
                        }
                    }
                }

                RecommendationCache.topPosition?.let { rec ->
                    key("router-rec") {
                        SphereNode(
                            radius = AppConfig.ROUTER_MARKER_RADIUS_M,
                            position = Position(
                                x = rec.x.safeCoord(),
                                y = rec.y.safeCoord(ballHeight),
                                z = rec.z.safeCoord(),
                            ),
                            materialInstance = matRouter,
                        )
                    }
                }

                // 4) Speed tests — cyan balls with Mbps/kbps (not RSSI)
                speedTests.take(3).forEach { test ->
                    key("st-${test.id}") {
                        val sx = test.poseX.safeCoord()
                        val sy = test.poseY.safeCoord(ballHeight)
                        val sz = test.poseZ.safeCoord()
                        SphereNode(
                            radius = AppConfig.SPEED_TEST_MARKER_RADIUS_M,
                            position = Position(x = sx, y = sy, z = sz),
                            materialInstance = matSpeed,
                            apply = { name = "$SPEED_TEST_NODE_PREFIX${test.id}" },
                        )
                        TextNode(
                            text = "↓ ${SpeedFormat.formatMbps(test.downloadMbps)}\n↑ ${SpeedFormat.formatMbps(test.uploadMbps)}",
                            fontSize = 34f,
                            textColor = android.graphics.Color.WHITE,
                            backgroundColor = 0xDD006064.toInt(),
                            widthMeters = 0.44f,
                            heightMeters = 0.22f,
                            position = Position(x = sx, y = sy + 0.18f, z = sz),
                        )
                    }
                }
            }
        }


        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .fillMaxWidth(),
        ) {
            MappingStatusCard(
                sampleCount = samples.size,
                sessionLabel = activeSession?.locationName,
                isFusing = isFusing,
                trackingLabel = tracking.stateLabel,
                trackingOk = tracking.quality == TrackingQuality.TRACKING,
                cooldownSeconds = cooldown,
                fusionError = fusionError,
                heatmapInfo = when {
                    heatmapPlane != null && lastComputeMs != null ->
                        stringResource(
                            R.string.heatmap_compute_info,
                            heatmapPlane!!.sampleCount,
                            lastComputeMs!!,
                        )
                    samples.size in 1 until IdwInterpolator.MIN_SAMPLES_FOR_HEATMAP ->
                        stringResource(
                            R.string.heatmap_need_samples,
                            IdwInterpolator.MIN_SAMPLES_FOR_HEATMAP,
                        )
                    else -> null
                },
                deadZoneCount = deadZones.size,
                bestNetwork = bestNetwork,
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Always-visible guide: what balls / colors / path / speed mean
            SignalGuideCard(
                groundReady = groundReady,
                isSessionActive = activeSession != null,
                lastSpeed = lastSpeedBanner,
            )
            Spacer(modifier = Modifier.height(6.dp))
            ViewModeRow(
                mode = viewMode,
                onModeChange = { viewMode = it },
            )

            if (deadZones.isNotEmpty() && showHeatmap) {
                Spacer(modifier = Modifier.height(6.dp))
                DeadZoneChipRow(
                    zones = deadZones,
                    onZoneClick = { selectedDeadZone = it },
                )
            }
            speedTestError?.let { err ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = err,
                    color = Color(0xFFFF8A80),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
            cloudStatus?.let { msg ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = msg,
                    color = NeonCyan,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x99000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
            lastSpeedBanner?.let { msg ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = msg,
                    color = NeonMint,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC004D40), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = !hasAchievedTracking,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(20.dp),
        ) {
            GlassPanel(tint = PanelDark.copy(alpha = 0.88f), contentPadding = 14.dp) {
                Text(
                    text = stringResource(R.string.ar_calibration_title),
                    color = NeonCyan,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.ar_calibration_hint),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }

        AnimatedVisibility(
            visible = speedTestRunning,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            GlassPanel(tint = Color(0xEE004D40), contentPadding = 16.dp) {
                CircularProgressIndicator(
                    color = NeonCyan,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(28.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.speed_test_running),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.speed_test_running_hint),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (activeSession != null) {
                CompactPrimaryButton(
                    text = if (speedTestRunning) {
                        stringResource(R.string.speed_test_running_short)
                    } else {
                        stringResource(R.string.speed_test_run_here)
                    },
                    onClick = {
                        val session = activeSession ?: return@CompactPrimaryButton
                        val currentPose = pose
                        speedTestError = null
                        scope.launch {
                            when (val outcome = speedTestManager.runSpeedTest()) {
                                is SpeedTestOutcome.Success -> {
                                    speedTestManager.saveResult(
                                        sessionId = session.sessionId,
                                        poseX = currentPose.x,
                                        poseY = currentPose.y,
                                        poseZ = currentPose.z,
                                        result = outcome.result,
                                    )
                                    speedTestError = null
                                    lastSpeedBanner = SpeedFormat.formatDetailLine(
                                        outcome.result.downloadMbps,
                                        outcome.result.uploadMbps,
                                        outcome.result.pingMs,
                                    )
                                    // Auto-clear banner after a few seconds.
                                    launch {
                                        delay(8_000)
                                        if (lastSpeedBanner?.contains(
                                                SpeedFormat.formatMbps(outcome.result.downloadMbps),
                                            ) == true
                                        ) {
                                            lastSpeedBanner = null
                                        }
                                    }
                                }
                                is SpeedTestOutcome.Failure -> {
                                    speedTestError = speedTestErrorMessage(outcome.error)
                                    lastSpeedBanner = null
                                }
                            }
                        }
                    },
                    enabled = canRunSpeedTest,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color(0xFF00838F),
                )
                if (!trackingStable && !speedTestRunning) {
                    Text(
                        text = stringResource(R.string.speed_test_needs_tracking),
                        color = Color(0xFFFFF176),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (activeSession == null) {
                    CompactPrimaryButton(
                        text = stringResource(R.string.session_start),
                        onClick = {
                            locationName = ""
                            showStartDialog = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    CompactPrimaryButton(
                        text = stringResource(R.string.session_end),
                        onClick = {
                            scope.launch {
                                val sess = arSession
                                val active = activeSession
                                if (sess != null && active != null && cloudAnchors.isApiKeyConfigured()) {
                                    cloudStatus = context.getString(R.string.cloud_hosting)
                                    val hostResult = withTimeoutOrNull(
                                        AppConfig.CLOUD_ANCHOR_TIMEOUT_SEC * 1_000,
                                    ) {
                                        // Prefer pose from our frame pipeline (avoids fighting SceneView update).
                                        val p = pose
                                        val arPose = com.google.ar.core.Pose.makeTranslation(p.x, p.y, p.z)
                                        val local = cloudAnchors.createLocalAnchor(sess, arPose)
                                            ?: cloudAnchors.createLocalAnchorAtCamera(sess)
                                        if (local == null) {
                                            CloudAnchorManager.HostResult.Failure("no tracking")
                                        } else {
                                            try {
                                                cloudAnchors.host(sess, local)
                                            } finally {
                                                runCatching { local.detach() }
                                            }
                                        }
                                    }
                                    when (hostResult) {
                                        is CloudAnchorManager.HostResult.Success -> {
                                            sessionManager.attachCloudAnchor(
                                                active.sessionId,
                                                hostResult.cloudAnchorId,
                                            )
                                            cloudStatus = context.getString(R.string.cloud_host_ok)
                                        }
                                        is CloudAnchorManager.HostResult.Failure -> {
                                            cloudStatus = context.getString(
                                                R.string.cloud_host_fail,
                                                hostResult.reason,
                                            )
                                        }
                                        null -> {
                                            cloudStatus = context.getString(
                                                R.string.cloud_host_fail,
                                                "timeout",
                                            )
                                        }
                                    }
                                }
                                fusionEngine.stop()
                                val closed = sessionManager.endSession()
                                closed?.let { syncManager.enqueueSessionSync(it.sessionId) }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                    OutlinedButton(
                        onClick = { wifiScanner.triggerScan() },
                        enabled = cooldown == 0L && !isScanning,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            if (isScanning) {
                                stringResource(R.string.scanning)
                            } else {
                                stringResource(R.string.scan_now)
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

private fun speedTestErrorMessage(error: SpeedTestError): String {
    return when (error) {
        is SpeedTestError.NoInternet -> error.message ?: "No internet"
        is SpeedTestError.ServerUnreachable -> error.message ?: "Server unreachable"
        is SpeedTestError.BackendUnavailable -> error.message ?: "Backend unavailable"
        is SpeedTestError.Cancelled -> error.message ?: "Cancelled"
        is SpeedTestError.Unknown -> error.message ?: "Speed test failed"
    }
}


private data class AnalysisResult(
    val plane: HeatmapPlane?,
    val zones: List<DeadZoneRegion>,
    val computeMs: Long,
)

/**
 * Spatial downsample so AR never draws thousands of spheres.
 * One ball per floor cell at the **exact saved pose** of the strongest RSSI
 * sample in that cell (balls stay where you walked).
 */
private fun downsampleSamplesForAr(
    samples: List<RssiSampleEntity>,
    maxPoints: Int,
): List<RssiSampleEntity> {
    if (samples.isEmpty()) return emptyList()
    val cell = 0.35f
    val best = LinkedHashMap<Long, RssiSampleEntity>(samples.size.coerceAtMost(512))
    for (s in samples) {
        if (!s.poseX.isFinite() || !s.poseZ.isFinite()) continue
        val cx = (s.poseX / cell).toInt()
        val cz = (s.poseZ / cell).toInt()
        val key = (cx.toLong() shl 32) xor (cz.toLong() and 0xffffffffL)
        val prev = best[key]
        if (prev == null || s.rssiDbm > prev.rssiDbm ||
            (s.rssiDbm == prev.rssiDbm && s.timestampMs > prev.timestampMs)
        ) {
            best[key] = s
        }
    }
    val reduced = best.values.sortedBy { it.timestampMs }
    if (reduced.size <= maxPoints) return reduced
    return reduced.takeLast(maxPoints)
}

/**
 * Build a smooth-ish walk polyline on the floor, dropping tiny jitter segments.
 */
private fun buildWalkPathPoints(
    samples: List<RssiSampleEntity>,
    floorY: Float,
): List<Position> {
    if (samples.isEmpty()) return emptyList()
    val out = ArrayList<Position>(samples.size.coerceAtMost(AppConfig.AR_MAX_PATH_POINTS))
    var lastX = Float.NaN
    var lastZ = Float.NaN
    val minStep = 0.18f // coarser = fewer path segments = less lag
    for (s in samples) {
        val x = s.poseX.safeCoord()
        val z = s.poseZ.safeCoord()
        if (lastX.isFinite()) {
            val d = hypot((x - lastX).toDouble(), (z - lastZ).toDouble()).toFloat()
            if (d < minStep) continue
        }
        out.add(Position(x = x, y = floorY, z = z))
        lastX = x
        lastZ = z
        if (out.size >= AppConfig.AR_MAX_PATH_POINTS) break
    }
    return out
}

@Composable
private fun DeadZoneChipRow(
    zones: List<DeadZoneRegion>,
    onZoneClick: (DeadZoneRegion) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(zones, key = { it.id }) { zone ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xCCB71C1C)),
                modifier = Modifier.clickable { onZoneClick(zone) },
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.dead_zone_chip,
                        zone.id,
                        zone.worstRssiDbm,
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun DeadZoneDetailDialog(
    zone: DeadZoneRegion,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.dead_zone_detail_title, zone.id))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(
                        R.string.dead_zone_detail_worst,
                        zone.worstRssiDbm,
                    ),
                )
                Text(
                    stringResource(
                        R.string.dead_zone_detail_avg,
                        zone.averageRssiDbm,
                    ),
                )
                Text(
                    stringResource(
                        R.string.dead_zone_detail_area,
                        zone.areaSqM,
                        zone.cellCount,
                    ),
                )
                Text(
                    stringResource(
                        R.string.dead_zone_detail_location,
                        zone.relativeDescriptionFromOrigin(),
                    ),
                )
                Text(
                    stringResource(
                        R.string.dead_zone_detail_centroid,
                        zone.centroidX,
                        zone.centroidZ,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    stringResource(
                        R.string.dead_zone_threshold_note,
                        AppConfig.DEAD_ZONE_THRESHOLD_DBM,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        },
    )
}

@Composable
private fun SpeedTestDetailDialog(
    test: SpeedTestEntity,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.speed_test_detail_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(
                        R.string.speed_test_detail_down,
                        SpeedFormat.formatMbps(test.downloadMbps),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonMint,
                )
                Text(
                    text = stringResource(
                        R.string.speed_test_detail_up,
                        SpeedFormat.formatMbps(test.uploadMbps),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonCyan,
                )
                Text(
                    stringResource(
                        R.string.speed_test_detail_ping,
                        test.pingMs,
                    ),
                )
                Text(
                    stringResource(
                        R.string.speed_test_detail_pose,
                        test.poseX,
                        test.poseY,
                        test.poseZ,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.speed_test_detail_backend, test.backend),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        },
    )
}


@Composable
private fun ViewModeRow(
    mode: MappingViewMode,
    onModeChange: (MappingViewMode) -> Unit,
) {
    val options = listOf(
        stringResource(R.string.view_raw_points),
        stringResource(R.string.view_heatmap),
        stringResource(R.string.view_both),
    )
    val index = when (mode) {
        MappingViewMode.RAW_POINTS -> 0
        MappingViewMode.HEATMAP -> 1
        MappingViewMode.BOTH -> 2
    }
    SegmentedControl(
        options = options,
        selectedIndex = index,
        onSelect = {
            onModeChange(
                when (it) {
                    0 -> MappingViewMode.RAW_POINTS
                    1 -> MappingViewMode.HEATMAP
                    else -> MappingViewMode.BOTH
                },
            )
        },
    )
}

/**
 * Explains balls, colors (incl. yellow), path, and speed — always readable in the HUD.
 */
@Composable
private fun SignalGuideCard(
    groundReady: Boolean,
    isSessionActive: Boolean,
    lastSpeed: String?,
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        tint = PanelDark.copy(alpha = 0.92f),
        contentPadding = 10.dp,
        corner = 12.dp,
    ) {
        Text(
            text = when {
                !groundReady -> "① Move phone slowly until AR locks the floor (planes appear)."
                !isSessionActive -> "② Floor locked. Tap Start Session, then walk the room."
                else -> "③ Mapping: cyan line = path · balls = Wi‑Fi strength at that spot"
            },
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GuideChip(Color(0xFF66BB6A), "Green strong")
            GuideChip(Color(0xFFFFEE58), "Yellow medium")
            GuideChip(Color(0xFFFF7043), "Orange weak")
            GuideChip(Color(0xFFE53935), "Red dead")
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Ball number = RSSI in dBm (higher is better, e.g. −45 > −70). " +
                "Yellow means medium coverage — usable but not peak. " +
                "Cyan ball = speed test (Mbps/kbps).",
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.labelSmall,
        )
        if (lastSpeed != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Last speed: $lastSpeed",
                color = NeonMint,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun GuideChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(50)),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MappingStatusCard(
    sampleCount: Int,
    sessionLabel: String?,
    isFusing: Boolean,
    trackingLabel: String,
    trackingOk: Boolean,
    cooldownSeconds: Long,
    fusionError: String?,
    heatmapInfo: String?,
    deadZoneCount: Int,
    bestNetwork: BestNetworkEstimate? = null,
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        tint = PanelDark,
        contentPadding = 10.dp,
        corner = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.samples_collected, sampleCount),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (sessionLabel != null) {
                    Text(
                        text = sessionLabel,
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            StatusPill(label = trackingLabel, ok = trackingOk)
        }

        bestNetwork?.let { best ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.best_network_here,
                    best.key.displayName,
                    best.rssiDbm,
                ),
                color = NeonCyan,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(
                label = if (isFusing) {
                    stringResource(R.string.fusion_active)
                } else {
                    stringResource(R.string.fusion_idle)
                },
                ok = isFusing,
            )
            if (cooldownSeconds > 0L) {
                Text(
                    text = stringResource(R.string.cooldown_format, cooldownSeconds),
                    color = Color(0xFFFFF176),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (deadZoneCount > 0) {
                Text(
                    text = stringResource(R.string.dead_zones_found, deadZoneCount),
                    color = Color(0xFFFF8A80),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        fusionError?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = it, color = Color(0xFFFF8A80), style = MaterialTheme.typography.labelSmall)
        }
        heatmapInfo?.let {
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = it, color = NeonMint.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LegendRow()
    }
}

@Composable
private fun LegendRow() {
    val strong = UserPreferences.rssiStrongDbm
    val medium = UserPreferences.rssiMediumDbm
    val dead = UserPreferences.rssiDeadDbm
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LegendSwatch(Color(0xFF00C853), "≥$strong")
        LegendSwatch(Color(0xFFFFD600), "$strong…$medium")
        LegendSwatch(Color(0xFFFF6D00), "<$medium")
        LegendSwatch(Color(0xFFE51C23), "≤$dead red")
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .background(color, RoundedCornerShape(50)),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun StartSessionDialog(
    locationName: String,
    onLocationNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_start_title)) },
        text = {
            Column {
                Text(stringResource(R.string.session_start_body))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = locationName,
                    onValueChange = onLocationNameChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.session_location_label)) },
                    placeholder = { Text(stringResource(R.string.session_location_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.session_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ResumeMappingDialog(
    candidates: List<MappingSessionEntity>,
    cloudReady: Boolean,
    onResume: (MappingSessionEntity) -> Unit,
    onNewSession: () -> Unit,
    onDismiss: () -> Unit,
) {
    val top = candidates.firstOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_resume_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.cloud_resume_body))
                if (!cloudReady) {
                    Text(
                        stringResource(R.string.cloud_api_missing),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                top?.let {
                    Text(
                        stringResource(
                            R.string.cloud_resume_candidate,
                            it.locationName,
                            it.cloudAnchorId?.take(12) ?: "—",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { top?.let(onResume) },
                enabled = top != null,
            ) {
                Text(stringResource(R.string.cloud_resume_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onNewSession) {
                Text(stringResource(R.string.cloud_resume_no))
            }
        },
    )
}

/** Green → yellow → orange → red (weak is red). */
private fun rssiTierColor(rssiDbm: Int): Color {
    val strong = UserPreferences.rssiStrongDbm
    val medium = UserPreferences.rssiMediumDbm
    val dead = UserPreferences.rssiDeadDbm
    return when {
        rssiDbm >= strong -> Color(0xFF00C853)
        rssiDbm >= medium -> Color(0xFFFFD600)
        rssiDbm >= dead -> Color(0xFFFF6D00)
        else -> Color(0xFFE51C23)
    }
}

/** Guard NaN/Inf positions that crash Filament transforms. */
private fun Float.safeCoord(fallback: Float = 0f): Float {
    return if (isFinite()) this else fallback
}

