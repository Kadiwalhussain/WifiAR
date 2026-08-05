package com.wifiar.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ar.core.Config
import com.wifiar.app.AppConfig
import com.wifiar.app.R
import com.wifiar.app.ar.HeatmapMeshBuilder
import com.wifiar.app.ar.HeatmapPlane
import com.wifiar.app.data.analysis.NetworkComparisonEngine
import com.wifiar.app.data.analysis.NetworkComparisonResult
import com.wifiar.app.data.analysis.NetworkCoverageAnalysis
import com.wifiar.app.data.local.WifiArDatabase
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Per-network coverage comparison + optional AR heatmap for one SSID/BSSID (Part 8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkComparisonScreen(
    sessionId: String,
    sessionLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val db = remember { WifiArDatabase.getInstance(context) }
    val engine = remember { NetworkComparisonEngine() }
    val heatmapBuilder = remember { HeatmapMeshBuilder() }

    val samples by db.rssiSampleDao()
        .getAllForSession(sessionId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var comparison by remember { mutableStateOf<NetworkComparisonResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedBssid by remember { mutableStateOf<String?>(null) }
    var showAr by remember { mutableStateOf(false) }
    var heatmapPlane by remember { mutableStateOf<HeatmapPlane?>(null) }
    var dropdownOpen by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId, samples) {
        loading = true
        comparison = withContext(Dispatchers.Default) {
            engine.compare(samples)
        }
        loading = false
        // Auto-select strongest network if none chosen.
        if (selectedBssid == null) {
            selectedBssid = comparison?.networks?.firstOrNull()?.key?.bssid
        }
    }

    val selectedNetwork = comparison?.networks?.firstOrNull { it.key.bssid == selectedBssid }

    // Rebuild AR heatmap when selection changes.
    LaunchedEffect(selectedNetwork?.key?.bssid, samples, showAr) {
        if (!showAr || selectedNetwork == null) {
            heatmapPlane = null
            return@LaunchedEffect
        }
        val plane = withContext(Dispatchers.Default) {
            heatmapBuilder.build(selectedNetwork.grid, samples.filter {
                it.bssid == selectedNetwork.key.bssid
            })
        }
        // Do not recycle — Filament may still hold the previous texture.
        heatmapPlane = plane
    }

    if (showAr) {
        NetworkArHeatmapView(
            sessionLabel = sessionLabel,
            networks = comparison?.networks.orEmpty(),
            selectedBssid = selectedBssid,
            onSelectBssid = { selectedBssid = it },
            heatmapPlane = heatmapPlane,
            onBackToList = { showAr = false },
            dropdownOpen = dropdownOpen,
            onDropdownOpenChange = { dropdownOpen = it },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.network_compare_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = sessionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            comparison == null || comparison!!.networks.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(
                            R.string.network_compare_empty,
                            AppConfig.NETWORK_COMPARE_MIN_SAMPLES,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            text = stringResource(
                                R.string.network_compare_subtitle,
                                comparison!!.networks.size,
                                AppConfig.COVERAGE_THRESHOLD_DBM,
                                AppConfig.DEAD_ZONE_THRESHOLD_DBM,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Table header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            HeaderCell("Network", Modifier.weight(1.4f))
                            HeaderCell("Avg", Modifier.weight(0.7f))
                            HeaderCell("Cover%", Modifier.weight(0.7f))
                            HeaderCell("Dead%", Modifier.weight(0.7f))
                        }
                    }

                    items(comparison!!.networks, key = { it.key.bssid }) { net ->
                        NetworkStatsRow(
                            analysis = net,
                            selected = net.key.bssid == selectedBssid,
                            onClick = {
                                selectedBssid = net.key.bssid
                                showAr = true
                            },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.network_compare_tap_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

@Composable
private fun NetworkStatsRow(
    analysis: NetworkCoverageAnalysis,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1.4f)) {
                    Text(
                        text = analysis.key.displayName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = analysis.key.bssid,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Text(
                    text = "%.0f".format(analysis.averageRssiDbm),
                    modifier = Modifier.weight(0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "%.0f%%".format(analysis.coverageFraction * 100f),
                    modifier = Modifier.weight(0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2E7D32),
                )
                Text(
                    text = "%.0f%%".format(analysis.deadZoneFraction * 100f),
                    modifier = Modifier.weight(0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFC62828),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.network_compare_samples, analysis.sampleCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun NetworkArHeatmapView(
    sessionLabel: String,
    networks: List<NetworkCoverageAnalysis>,
    selectedBssid: String?,
    onSelectBssid: (String) -> Unit,
    heatmapPlane: HeatmapPlane?,
    onBackToList: () -> Unit,
    dropdownOpen: Boolean,
    onDropdownOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = networks.firstOrNull { it.key.bssid == selectedBssid }

    Box(modifier = modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            planeRenderer = true,
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
        ) {
            heatmapPlane?.let { plane ->
                key(plane.version, selectedBssid) {
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
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .background(Color(0xBB000000), RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onBackToList) {
                    Text("←", color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.network_ar_title),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = sessionLabel,
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Box {
                FilterChip(
                    selected = true,
                    onClick = { onDropdownOpenChange(true) },
                    label = {
                        Text(
                            selected?.key?.displayName
                                ?: stringResource(R.string.network_select),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                DropdownMenu(
                    expanded = dropdownOpen,
                    onDismissRequest = { onDropdownOpenChange(false) },
                ) {
                    networks.forEach { net ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(net.key.displayName, fontWeight = FontWeight.Medium)
                                    Text(
                                        net.key.bssid,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            },
                            onClick = {
                                onSelectBssid(net.key.bssid)
                                onDropdownOpenChange(false)
                            },
                        )
                    }
                }
            }

            selected?.let { net ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.network_ar_stats,
                        net.averageRssiDbm,
                        net.coverageFraction * 100f,
                        net.deadZoneFraction * 100f,
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
