package com.wifiar.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiar.app.AppConfig
import com.wifiar.app.R
import com.wifiar.app.data.analysis.DeadZoneDetector
import com.wifiar.app.data.analysis.DeadZoneRegion
import com.wifiar.app.data.export.HeatmapExporter
import com.wifiar.app.data.interpolation.IdwInterpolator
import com.wifiar.app.data.local.RssiSampleEntity
import com.wifiar.app.data.local.SessionSummary
import com.wifiar.app.data.local.SpeedTestEntity
import com.wifiar.app.data.local.WifiArDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date



/**
 * Past mapping sessions with sample counts and dead-zone summaries (Part 5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistoryScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val db = remember { WifiArDatabase.getInstance(context) }

    val summaries by db.mappingSessionDao()
        .observeSessionSummaries()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    val selectedSummary = summaries.firstOrNull { it.sessionId == selectedSessionId }

    if (selectedSessionId != null && selectedSummary != null) {
        val samples by db.rssiSampleDao()
            .getAllForSession(selectedSessionId!!)
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val speedTests by db.speedTestDao()
            .getAllForSession(selectedSessionId!!)
            .collectAsStateWithLifecycle(initialValue = emptyList())

        var showNetworkCompare by remember(selectedSessionId) { mutableStateOf(false) }
        var showRouterPlacement by remember(selectedSessionId) { mutableStateOf(false) }

        when {
            showRouterPlacement -> {
                RouterPlacementScreen(
                    sessionId = selectedSessionId!!,
                    sessionLabel = selectedSummary.locationName,
                    onBack = { showRouterPlacement = false },
                    modifier = modifier,
                )
            }
            showNetworkCompare -> {
                NetworkComparisonScreen(
                    sessionId = selectedSessionId!!,
                    sessionLabel = selectedSummary.locationName,
                    onBack = { showNetworkCompare = false },
                    modifier = modifier,
                )
            }
            else -> {
                SessionDetailScreen(
                    summary = selectedSummary,
                    samples = samples,
                    speedTests = speedTests,
                    onBack = { selectedSessionId = null },
                    onCompareNetworks = { showNetworkCompare = true },
                    onRouterPlacement = { showRouterPlacement = true },
                    modifier = modifier,
                )
            }
        }

    } else {


        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.history_title)) },
                )
            },
        ) { padding ->
            if (summaries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    com.wifiar.app.ui.components.EmptyStateView(
                        title = stringResource(R.string.history_empty_title),
                        body = stringResource(R.string.history_empty),
                        hint = stringResource(R.string.history_empty_hint),
                        iconRes = R.drawable.ic_empty_sessions,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(summaries, key = { it.sessionId }) { summary ->
                        SessionSummaryCard(
                            summary = summary,
                            onClick = { selectedSessionId = summary.sessionId },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionSummaryCard(
    summary: SessionSummary,
    onClick: () -> Unit,
) {
    val dateText = remember(summary.startTimeMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(summary.startTimeMs))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = summary.locationName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.history_sample_count, summary.sampleCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (summary.endTimeMs == null) {
                Text(
                    text = stringResource(R.string.history_in_progress),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                Text(
                    text = if (summary.synced) {
                        stringResource(R.string.history_synced)
                    } else {
                        stringResource(R.string.history_pending_sync)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (summary.synced) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.history_tap_for_dead_zones),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetailScreen(
    summary: SessionSummary,
    samples: List<RssiSampleEntity>,
    speedTests: List<SpeedTestEntity>,
    onBack: () -> Unit,
    onCompareNetworks: () -> Unit = {},
    onRouterPlacement: () -> Unit = {},
    modifier: Modifier = Modifier,
) {



    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember { HeatmapExporter(context) }
    val idw = remember { IdwInterpolator() }
    val detector = remember { DeadZoneDetector() }
    var deadZones by remember(summary.sessionId) {
        mutableStateOf<List<DeadZoneRegion>?>(null)
    }
    var analysing by remember(summary.sessionId) { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }


    LaunchedEffect(summary.sessionId, samples) {
        if (samples.isEmpty()) {
            deadZones = emptyList()
            return@LaunchedEffect
        }
        analysing = true
        deadZones = withContext(Dispatchers.Default) {
            val grid = idw.interpolate(samples)
            if (grid.cols == 0) emptyList() else detector.detect(grid)
        }
        analysing = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = summary.locationName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.history_sample_count, samples.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Button(
                    onClick = onCompareNetworks,
                    enabled = samples.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.network_compare_open))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRouterPlacement,
                    enabled = samples.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.router_open))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            exporting = true
                            exportMessage = null
                            val files = withContext(Dispatchers.IO) {
                                exporter.exportSession(
                                    locationName = summary.locationName,
                                    startTimeMs = summary.startTimeMs,
                                    samples = samples,
                                    speedTests = speedTests,
                                )
                            }
                            exporting = false
                            if (files.pngUri == null && files.csvUri == null) {
                                exportMessage = context.getString(R.string.export_failed)
                            } else {
                                exporter.shareBoth(files.pngUri, files.csvUri)
                                exportMessage = context.getString(R.string.export_ok)
                            }
                        }
                    },
                    enabled = samples.isNotEmpty() && !exporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (exporting) {
                            stringResource(R.string.export_working)
                        } else {
                            stringResource(R.string.export_share)
                        },
                    )
                }
                exportMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }



            item {
                Text(
                    text = stringResource(R.string.history_dead_zones_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(4.dp))
                when {
                    analysing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(18.dp)
                                    .padding(end = 4.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = stringResource(R.string.history_dead_zones_analysing),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    deadZones == null -> Unit
                    deadZones!!.isEmpty() -> {
                        Text(
                            text = stringResource(
                                R.string.history_dead_zones_none,
                                AppConfig.DEAD_ZONE_THRESHOLD_DBM,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(
                                R.string.history_dead_zones_count,
                                deadZones!!.size,
                                AppConfig.DEAD_ZONE_THRESHOLD_DBM,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            items(deadZones.orEmpty(), key = { "dz-${it.id}" }) { zone ->
                DeadZoneHistoryCard(zone)
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.history_speed_tests_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (speedTests.isEmpty()) {
                    Text(
                        text = stringResource(R.string.history_speed_tests_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.history_speed_tests_count,
                            speedTests.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            items(speedTests, key = { "st-${it.id}" }) { test ->
                SpeedTestHistoryCard(test)
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.history_samples_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            items(samples, key = { it.id }) { sample ->
                SampleRow(sample)
            }
        }
    }
}

@Composable
private fun SpeedTestHistoryCard(test: SpeedTestEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(
                    R.string.speed_test_history_title,
                    test.downloadMbps,
                    test.uploadMbps,
                ),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.speed_test_detail_ping, test.pingMs),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.speed_test_detail_pose,
                    test.poseX,
                    test.poseY,
                    test.poseZ,
                ),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = stringResource(R.string.speed_test_detail_backend, test.backend),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}


@Composable
private fun DeadZoneHistoryCard(zone: DeadZoneRegion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.dead_zone_detail_title, zone.id),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.dead_zone_detail_worst,
                    zone.worstRssiDbm,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.dead_zone_detail_avg,
                    zone.averageRssiDbm,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.dead_zone_detail_area,
                    zone.areaSqM,
                    zone.cellCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.dead_zone_detail_location,
                    zone.relativeDescriptionFromOrigin(),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SampleRow(sample: RssiSampleEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = sample.ssid.ifBlank { "<hidden>" },
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sample.bssid,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${sample.rssiDbm} dBm · ${sample.frequencyMhz} MHz",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "xyz (%.2f, %.2f, %.2f)".format(
                        sample.poseX,
                        sample.poseY,
                        sample.poseZ,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
