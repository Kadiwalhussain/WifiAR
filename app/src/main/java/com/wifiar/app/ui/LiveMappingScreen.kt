package com.wifiar.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ar.core.Config
import com.wifiar.app.AppConfig
import com.wifiar.app.R
import com.wifiar.app.ar.ARSessionManager
import com.wifiar.app.ar.HeatmapMeshBuilder
import com.wifiar.app.ar.HeatmapPlane
import com.wifiar.app.ar.TrackingQuality
import com.wifiar.app.data.DataFusionEngine
import com.wifiar.app.data.SessionManager
import com.wifiar.app.data.analysis.BestNetworkEstimate
import com.wifiar.app.data.analysis.DeadZoneDetector
import com.wifiar.app.data.analysis.DeadZoneRegion
import com.wifiar.app.data.analysis.NetworkComparisonEngine
import com.wifiar.app.data.interpolation.HeatmapRecomputeGate
import com.wifiar.app.data.interpolation.IdwInterpolator
import com.wifiar.app.data.local.SpeedTestEntity
import com.wifiar.app.data.local.WifiArDatabase
import com.wifiar.app.data.speedtest.SpeedTestError
import com.wifiar.app.data.speedtest.SpeedTestManager
import com.wifiar.app.data.speedtest.SpeedTestOutcome
import com.wifiar.app.data.sync.SyncManager
import com.wifiar.app.scanner.WifiScanner


import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.SphereNode
import io.github.sceneview.rememberOnGestureListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


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
    val idw = remember { IdwInterpolator() }
    val heatmapBuilder = remember { HeatmapMeshBuilder() }
    val deadZoneDetector = remember { DeadZoneDetector() }
    val networkCompareEngine = remember { NetworkComparisonEngine() }
    val recomputeGate = remember { HeatmapRecomputeGate() }
    val speedTestManager = remember {
        SpeedTestManager(context, db.speedTestDao())
    }
    val syncManager = remember { SyncManager(context) }



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
            heatmapPlane?.bitmap?.takeIf {
                it !== result.plane.bitmap && !it.isRecycled
            }?.recycle()
            heatmapPlane = result.plane
            deadZones = result.zones
            lastComputeMs = result.computeMs
            recomputeGate.markComputed(snapshot.size)
            // Drop selection if the zone disappeared.
            selectedDeadZone?.let { sel ->
                if (result.zones.none { it.id == sel.id }) selectedDeadZone = null
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

    if (showStartDialog) {
        StartSessionDialog(
            locationName = locationName,
            onLocationNameChange = { locationName = it },
            onDismiss = { showStartDialog = false },
            onConfirm = {
                showStartDialog = false
                scope.launch {
                    heatmapPlane?.bitmap?.takeIf { !it.isRecycled }?.recycle()
                    heatmapPlane = null
                    deadZones = emptyList()
                    selectedDeadZone = null
                    recomputeGate.reset()
                    val session = sessionManager.startSession(locationName)
                    fusionEngine.start(session.sessionId)
                    wifiScanner.triggerScan()
                }
            },
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
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            planeRenderer = true,
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
            onSessionCreated = { arSessionManager.onSessionCreated(it) },
            onSessionResumed = { arSessionManager.onSessionResumed(it) },
            onSessionPaused = { arSessionManager.onSessionPaused() },
            onSessionFailed = { arSessionManager.onSessionFailed(it) },
            onSessionUpdated = { _, frame -> arSessionManager.onFrame(frame) },
            onTrackingFailureChanged = { arSessionManager.onTrackingFailureChanged(it) },
            onGestureListener = gestureListener,
        ) {
            val loader = materialLoader
            val floorY = heatmapPlane?.floorY ?: 0f

            if (showHeatmap) {
                heatmapPlane?.let { plane ->
                    key(plane.version) {
                        ImageNode(
                            bitmap = plane.bitmap,
                            size = Size(
                                x = plane.widthMeters,
                                y = 0.002f,
                                z = plane.depthMeters,
                            ),
                            position = Position(
                                x = plane.centerX,
                                y = plane.floorY,
                                z = plane.centerZ,
                            ),
                            normal = Direction(y = 1.0f),
                        )
                    }
                }

                // Dead-zone markers: red sphere + floating label (tappable via name).
                deadZones.forEach { zone ->
                    key(zone.id) {
                        val markerY = floorY + AppConfig.DEAD_ZONE_LABEL_HEIGHT_M
                        val nodeName = "$DEAD_ZONE_NODE_PREFIX${zone.id}"
                        val redMaterial = remember(loader) {
                            runCatching {
                                loader.createUnlitColorInstance(Color(0xFFB71C1C))
                            }.getOrElse {
                                loader.createColorInstance(
                                    color = Color(0xFFB71C1C),
                                    metallic = 0f,
                                    roughness = 0.5f,
                                )
                            }
                        }
                        SphereNode(
                            radius = AppConfig.DEAD_ZONE_MARKER_RADIUS_M,
                            position = Position(
                                x = zone.centroidX,
                                y = markerY,
                                z = zone.centroidZ,
                            ),
                            materialInstance = redMaterial,
                            apply = { name = nodeName },
                        )
                        TextNode(
                            text = "Dead Zone\n%.0f dBm".format(zone.worstRssiDbm),
                            fontSize = 36f,
                            textColor = android.graphics.Color.WHITE,
                            backgroundColor = 0xCCB71C1C.toInt(),
                            widthMeters = 0.55f,
                            heightMeters = 0.28f,
                            position = Position(
                                x = zone.centroidX,
                                y = markerY + 0.22f,
                                z = zone.centroidZ,
                            ),
                            apply = { name = nodeName },
                        )
                    }
                }
            }

            if (showRaw) {
                samples.forEach { sample ->
                    key(sample.id) {
                        val color = rssiTierColor(sample.rssiDbm)
                        val material = remember(loader, color) {
                            runCatching { loader.createUnlitColorInstance(color) }
                                .getOrElse {
                                    loader.createColorInstance(
                                        color = color,
                                        metallic = 0f,
                                        roughness = 0.8f,
                                    )
                                }
                        }
                        SphereNode(
                            radius = 0.06f,
                            position = Position(
                                x = sample.poseX,
                                y = sample.poseY,
                                z = sample.poseZ,
                            ),
                            materialInstance = material,
                        )
                    }
                }
            }

            // Speed-test checkpoints — cyan markers (distinct from red dead zones).
            speedTests.forEach { test ->
                key("st-${test.id}") {
                    val markerY = test.poseY
                    val nodeName = "$SPEED_TEST_NODE_PREFIX${test.id}"
                    val cyanMaterial = remember(loader) {
                        runCatching {
                            loader.createUnlitColorInstance(Color(0xFF00BCD4))
                        }.getOrElse {
                            loader.createColorInstance(
                                color = Color(0xFF00BCD4),
                                metallic = 0.1f,
                                roughness = 0.4f,
                            )
                        }
                    }
                    SphereNode(
                        radius = AppConfig.SPEED_TEST_MARKER_RADIUS_M,
                        position = Position(
                            x = test.poseX,
                            y = markerY,
                            z = test.poseZ,
                        ),
                        materialInstance = cyanMaterial,
                        apply = { name = nodeName },
                    )
                    TextNode(
                        text = "↓%.0f ↑%.0f\n%dms".format(
                            test.downloadMbps,
                            test.uploadMbps,
                            test.pingMs,
                        ),
                        fontSize = 32f,
                        textColor = android.graphics.Color.WHITE,
                        backgroundColor = 0xCC006064.toInt(),
                        widthMeters = 0.50f,
                        heightMeters = 0.26f,
                        position = Position(
                            x = test.poseX,
                            y = markerY + AppConfig.SPEED_TEST_LABEL_HEIGHT_M * 0.5f,
                            z = test.poseZ,
                        ),
                        apply = { name = nodeName },
                    )
                }
            }
        }


        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(12.dp)
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
            Spacer(modifier = Modifier.height(8.dp))
            ViewModeRow(
                mode = viewMode,
                onModeChange = { viewMode = it },
            )

            if (deadZones.isNotEmpty() && showHeatmap) {
                Spacer(modifier = Modifier.height(8.dp))
                DeadZoneChipRow(
                    zones = deadZones,
                    onZoneClick = { selectedDeadZone = it },
                )
            }
            speedTestError?.let { err ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = err,
                    color = Color(0xFFFF8A80),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = !hasAchievedTracking,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.ar_calibration_hint),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color(0xCC0D47A1), RoundedCornerShape(12.dp))
                    .padding(16.dp),
            )
        }

        // Non-blocking speed-test progress (scan/heatmap keep running).
        if (speedTestRunning) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xEE004D40)),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.speed_test_running),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.speed_test_running_hint),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (activeSession != null) {
                Button(
                    onClick = {
                        val session = activeSession ?: return@Button
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
                                }
                                is SpeedTestOutcome.Failure -> {
                                    speedTestError = speedTestErrorMessage(outcome.error)
                                }
                            }
                        }
                    },
                    enabled = canRunSpeedTest,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00838F),
                    ),
                ) {
                    Text(
                        if (speedTestRunning) {
                            stringResource(R.string.speed_test_running_short)
                        } else {
                            stringResource(R.string.speed_test_run_here)
                        },
                    )
                }
                if (!trackingStable && !speedTestRunning) {
                    Text(
                        text = stringResource(R.string.speed_test_needs_tracking),
                        color = Color(0xFFFFF176),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (activeSession == null) {
                    Button(
                        onClick = {
                            locationName = ""
                            showStartDialog = true
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.session_start))
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                fusionEngine.stop()
                                val closed = sessionManager.endSession()
                                // Background bulk upload when network + auth available.
                                closed?.let { syncManager.enqueueSessionSync(it.sessionId) }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.session_end))
                    }

                    OutlinedButton(
                        onClick = { wifiScanner.triggerScan() },
                        enabled = cooldown == 0L && !isScanning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (isScanning) {
                                stringResource(R.string.scanning)
                            } else {
                                stringResource(R.string.scan_now)
                            },
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

@Composable
private fun DeadZoneChipRow(
    zones: List<DeadZoneRegion>,
    onZoneClick: (DeadZoneRegion) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(zones, key = { it.id }) { zone ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xCCB71C1C)),
                modifier = Modifier.clickable { onZoneClick(zone) },
            ) {
                Text(
                    text = stringResource(
                        R.string.dead_zone_chip,
                        zone.id,
                        zone.worstRssiDbm,
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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
                    stringResource(
                        R.string.speed_test_detail_down,
                        test.downloadMbps,
                    ),
                )
                Text(
                    stringResource(
                        R.string.speed_test_detail_up,
                        test.uploadMbps,
                    ),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x99000000), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = mode == MappingViewMode.RAW_POINTS,
            onClick = { onModeChange(MappingViewMode.RAW_POINTS) },
            label = { Text(stringResource(R.string.view_raw_points)) },
        )
        FilterChip(
            selected = mode == MappingViewMode.HEATMAP,
            onClick = { onModeChange(MappingViewMode.HEATMAP) },
            label = { Text(stringResource(R.string.view_heatmap)) },
        )
        FilterChip(
            selected = mode == MappingViewMode.BOTH,
            onClick = { onModeChange(MappingViewMode.BOTH) },
            label = { Text(stringResource(R.string.view_both)) },
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xBB000000), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(
            text = stringResource(R.string.samples_collected, sampleCount),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (sessionLabel != null) {
            Text(
                text = sessionLabel,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        bestNetwork?.let { best ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.best_network_here,
                    best.key.displayName,
                    best.rssiDbm,
                ),
                color = Color(0xFF80D8FF),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = best.key.bssid,
                color = Color(0xFF80D8FF).copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isFusing) {
                    stringResource(R.string.fusion_active)
                } else {
                    stringResource(R.string.fusion_idle)
                },
                color = if (isFusing) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
            )

            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = trackingLabel,
                color = if (trackingOk) Color(0xFF69F0AE) else Color(0xFFFFAB40),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            if (cooldownSeconds > 0L) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.cooldown_format, cooldownSeconds),
                    color = Color(0xFFFFF176),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (deadZoneCount > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.dead_zones_found, deadZoneCount),
                color = Color(0xFFFF8A80),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        fusionError?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                color = Color(0xFFFF8A80),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        heatmapInfo?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                color = Color(0xFFB3E5FC),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LegendRow()
    }
}

@Composable
private fun LegendRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LegendSwatch(Color(0xFF2E7D32), "≥ -50")
        LegendSwatch(Color(0xFFF9A825), "-50…-70")
        LegendSwatch(Color(0xFF6A1B9A), "< -70")
        LegendSwatch(Color(0xFF8B0000), "≤ ${AppConfig.DEAD_ZONE_THRESHOLD_DBM}")
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(10.dp)
                .background(color, RoundedCornerShape(50)),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
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

private fun rssiTierColor(rssiDbm: Int): Color {
    return when {
        rssiDbm >= -50 -> Color(0xFF2E7D32)
        rssiDbm >= -70 -> Color(0xFFF9A825)
        else -> Color(0xFF6A1B9A)
    }
}
