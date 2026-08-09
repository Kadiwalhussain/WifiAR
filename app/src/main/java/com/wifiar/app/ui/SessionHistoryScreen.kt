@file:OptIn(ExperimentalMaterial3Api::class)

package com.wifiar.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.wifiar.app.scanner.WifiChannelUtils
import com.wifiar.app.ui.components.AnalyzerCard
import com.wifiar.app.util.SpeedFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF0B0D12)
private val CardBg = Color(0xE61C1F26)
private val AccentGreen = Color(0xFF69F0AE)
private val AccentBlue = Color(0xFF42A5F5)
private val AccentYellow = Color(0xFFFFEB3B)
private val AccentPurple = Color(0xFFAB47BC)
private val AccentOrange = Color(0xFFFFA726)

private val SeriesColors = listOf(
    AccentGreen,
    AccentBlue,
    AccentYellow,
    AccentPurple,
    AccentOrange,
    Color(0xFF26C6DA),
)

private enum class HistoryTab { SCAN, SPEED, REPORTS }
private enum class ChartRange(val days: Int, val label: String) {
    D7(7, "7D"),
    D30(30, "30D"),
    D90(90, "90D"),
}

private data class ScanRowUi(
    val summary: SessionSummary,
    val bestRssi: Int?,
    val primarySsid: String?,
)

private data class ChartSeries(
    val name: String,
    val color: Color,
    /** Day bucket epoch day → average RSSI */
    val points: List<Pair<Long, Float>>,
)

/**
 * Analyzer-style History: Scan / Speed / Reports tabs, summary cards,
 * multi-network signal chart, recent sessions — matching the mock UI.
 */
@Composable
fun SessionHistoryScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val db = remember { WifiArDatabase.getInstance(context) }

    val summaries by db.mappingSessionDao()
        .observeSessionSummaries()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allSpeeds by db.speedTestDao()
        .observeAllRecent()
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
        return
    }

    var tab by remember { mutableStateOf(HistoryTab.SCAN) }
    var chartRange by remember { mutableStateOf(ChartRange.D7) }
    var scanRows by remember { mutableStateOf<List<ScanRowUi>>(emptyList()) }
    var chartSeries by remember { mutableStateOf<List<ChartSeries>>(emptyList()) }
    var bestSignal by remember { mutableStateOf<Int?>(null) }
    var bestDownload by remember { mutableStateOf<Float?>(null) }
    var totalScanMs by remember { mutableStateOf(0L) }
    var loadingChart by remember { mutableStateOf(false) }

    // Enrich sessions + load chart when range / data changes
    LaunchedEffect(summaries, chartRange, allSpeeds) {
        loadingChart = true
        val since = System.currentTimeMillis() - chartRange.days * 24L * 60L * 60L * 1000L
        val result = withContext(Dispatchers.IO) {
            val rows = summaries.map { s ->
                val best = db.rssiSampleDao().maxRssiForSession(s.sessionId)
                val ssid = db.rssiSampleDao().dominantSsidForSession(s.sessionId)
                ScanRowUi(s, best, ssid)
            }
            val samples = db.rssiSampleDao().getSince(since)
            val series = buildChartSeries(samples, chartRange.days)
            val peakRssi = samples.maxOfOrNull { it.rssiDbm }
            val peakDl = db.speedTestDao().maxDownloadSince(since)
            val duration = summaries
                .filter { it.startTimeMs >= since }
                .sumOf { s ->
                    val end = s.endTimeMs ?: System.currentTimeMillis()
                    (end - s.startTimeMs).coerceAtLeast(0L)
                }
            HistoryAgg(rows, series, peakRssi, peakDl, duration)
        }
        scanRows = result.rows
        chartSeries = result.series
        bestSignal = result.bestRssi
        bestDownload = result.bestDownload
        totalScanMs = result.totalScanMs
        loadingChart = false
    }

    val scansInRange = remember(summaries, chartRange) {
        val since = System.currentTimeMillis() - chartRange.days * 24L * 60L * 60L * 1000L
        summaries.count { it.startTimeMs >= since }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Wi‑Fi AR Analyzer",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "History",
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HistoryTabRow(tab = tab, onTab = { tab = it })
            }

            when (tab) {
                HistoryTab.SCAN -> {
                    item {
                        ScanSummarySection(
                            scans = scansInRange,
                            bestSignal = bestSignal,
                            bestDownload = bestDownload,
                            totalScanMs = totalScanMs,
                            rangeLabel = "Last ${chartRange.days} Days",
                        )
                    }
                    item {
                        SignalOverTimeCard(
                            series = chartSeries,
                            range = chartRange,
                            loading = loadingChart,
                            onRange = { chartRange = it },
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Recent Scans",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "All Scans",
                                color = AccentGreen,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (scanRows.isEmpty()) {
                        item {
                            EmptyHistoryHint(
                                title = "No scan history yet",
                                body = "Walk a room in AR Scan to create mapping sessions.",
                            )
                        }
                    } else {
                        items(scanRows, key = { it.summary.sessionId }) { row ->
                            RecentScanCard(
                                row = row,
                                onClick = { selectedSessionId = row.summary.sessionId },
                            )
                        }
                    }
                }

                HistoryTab.SPEED -> {
                    item {
                        Text(
                            "Speed Test History",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    if (allSpeeds.isEmpty()) {
                        item {
                            EmptyHistoryHint(
                                title = "No speed tests yet",
                                body = "Run a speed test from the Speed Test tab during a mapping session.",
                            )
                        }
                    } else {
                        items(allSpeeds, key = { "spd-${it.id}" }) { test ->
                            AnalyzerSpeedCard(test)
                        }
                    }
                }

                HistoryTab.REPORTS -> {
                    item {
                        ReportsTab(
                            sessionCount = summaries.size,
                            sampleEstimate = scanRows.sumOf { it.summary.sampleCount },
                            bestSignal = bestSignal,
                            bestDownload = bestDownload,
                            totalScanMs = totalScanMs,
                            speedCount = allSpeeds.size,
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

private data class HistoryAgg(
    val rows: List<ScanRowUi>,
    val series: List<ChartSeries>,
    val bestRssi: Int?,
    val bestDownload: Float?,
    val totalScanMs: Long,
)

/** Build up to 4 SSID series, daily average RSSI. */
private fun buildChartSeries(samples: List<RssiSampleEntity>, daySpan: Int): List<ChartSeries> {
    if (samples.isEmpty()) return emptyList()
    val topSsids = samples
        .map { it.ssid.ifBlank { "Hidden" } }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(4)
        .map { it.key }
    if (topSsids.isEmpty()) return emptyList()

    val dayMs = 24L * 60L * 60L * 1000L
    return topSsids.mapIndexed { index, name ->
        val byDay = samples
            .filter { (it.ssid.ifBlank { "Hidden" }) == name }
            .groupBy { it.timestampMs / dayMs }
            .map { (day, list) ->
                day to list.map { it.rssiDbm }.average().toFloat()
            }
            .sortedBy { it.first }
        ChartSeries(name = name, color = SeriesColors[index % SeriesColors.size], points = byDay)
    }.filter { it.points.isNotEmpty() }
}

// ── Tabs ─────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryTabRow(tab: HistoryTab, onTab: (HistoryTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryTabChip(
            label = "Scan History",
            icon = Icons.Outlined.Timeline,
            selected = tab == HistoryTab.SCAN,
            onClick = { onTab(HistoryTab.SCAN) },
        )
        HistoryTabChip(
            label = "Speed Test History",
            icon = Icons.Outlined.Speed,
            selected = tab == HistoryTab.SPEED,
            onClick = { onTab(HistoryTab.SPEED) },
        )
        HistoryTabChip(
            label = "Reports",
            icon = Icons.Outlined.Description,
            selected = tab == HistoryTab.REPORTS,
            onClick = { onTab(HistoryTab.REPORTS) },
        )
    }
}

@Composable
private fun HistoryTabChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) Color(0xFF1B3A2A) else Color(0xFF161A22)
    val border = if (selected) AccentGreen else Color(0x33FFFFFF)
    val fg = if (selected) AccentGreen else Color.White.copy(alpha = 0.65f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

// ── Summary ──────────────────────────────────────────────────────────────────

@Composable
private fun ScanSummarySection(
    scans: Int,
    bestSignal: Int?,
    bestDownload: Float?,
    totalScanMs: Long,
    rangeLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Scan Summary",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                rangeLabel,
                color = Color.White.copy(alpha = 0.45f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryStatCard(
                icon = Icons.Outlined.Wifi,
                tint = AccentGreen,
                value = scans.toString(),
                label = "Scans",
                modifier = Modifier.weight(1f),
            )
            SummaryStatCard(
                icon = Icons.Outlined.Timeline,
                tint = AccentBlue,
                value = bestSignal?.let { "$it dBm" } ?: "—",
                label = "Best Signal",
                modifier = Modifier.weight(1f),
            )
            SummaryStatCard(
                icon = Icons.Outlined.Speed,
                tint = AccentPurple,
                value = bestDownload?.let { SpeedFormat.formatMbps(it) } ?: "—",
                label = "Best Download",
                modifier = Modifier.weight(1f),
            )
            SummaryStatCard(
                icon = Icons.Outlined.AccessTime,
                tint = AccentOrange,
                value = formatDuration(totalScanMs),
                label = "Total Scanning",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryStatCard(
    icon: ImageVector,
    tint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                value,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Chart ────────────────────────────────────────────────────────────────────

@Composable
private fun SignalOverTimeCard(
    series: List<ChartSeries>,
    range: ChartRange,
    loading: Boolean,
    onRange: (ChartRange) -> Unit,
) {
    AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Signal Strength Over Time",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "(dBm)",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                ChartRange.entries.forEach { r ->
                    val selected = r == range
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFF1B3A2A) else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) AccentGreen else Color(0x33FFFFFF),
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { onRange(r) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            r.label,
                            color = if (selected) AccentGreen else Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = AccentGreen,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else if (series.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Map a few sessions to see signal trends",
                        color = Color.White.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                MultiSeriesRssiChart(
                    series = series,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    series.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(s.color),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                s.name,
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiSeriesRssiChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
) {
    val minRssi = -100f
    val maxRssi = -20f
    val dayMs = 24L * 60L * 60L * 1000L
    val allDays = series.flatMap { it.points.map { p -> p.first } }.distinct().sorted()
    if (allDays.isEmpty()) return

    val labelFmt = remember {
        SimpleDateFormat("MMM d", Locale.getDefault())
    }

    Canvas(modifier = modifier) {
        val leftPad = 36.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val topPad = 8.dp.toPx()
        val plotW = size.width - leftPad - 8.dp.toPx()
        val plotH = size.height - topPad - bottomPad
        if (plotW <= 0f || plotH <= 0f) return@Canvas

        val yLabels = listOf(-20, -40, -60, -80, -100)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(120, 255, 255, 255)
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }

        yLabels.forEach { yVal ->
            val t = ((yVal - minRssi) / (maxRssi - minRssi)).coerceIn(0f, 1f)
            val y = topPad + plotH * (1f - t)
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1f,
            )
            drawContext.canvas.nativeCanvas.drawText(
                yVal.toString(),
                2.dp.toPx(),
                y + 4.dp.toPx(),
                paint,
            )
        }

        fun xFor(day: Long): Float {
            val minD = allDays.first()
            val maxD = allDays.last()
            val span = (maxD - minD).coerceAtLeast(1L).toFloat()
            return leftPad + ((day - minD) / span) * plotW
        }

        fun yFor(rssi: Float): Float {
            val t = ((rssi - minRssi) / (maxRssi - minRssi)).coerceIn(0f, 1f)
            return topPad + plotH * (1f - t)
        }

        series.forEach { s ->
            if (s.points.isEmpty()) return@forEach
            val path = Path()
            s.points.forEachIndexed { i, (day, rssi) ->
                val x = xFor(day)
                val y = yFor(rssi)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = s.color,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
            s.points.forEach { (day, rssi) ->
                drawCircle(
                    color = s.color,
                    radius = 3.5.dp.toPx(),
                    center = Offset(xFor(day), yFor(rssi)),
                )
            }
        }

        // X labels (first, mid, last)
        val xDays = listOf(allDays.first(), allDays[allDays.size / 2], allDays.last()).distinct()
        xDays.forEach { day ->
            val label = labelFmt.format(Date(day * dayMs))
            val x = xFor(day)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x - 16.dp.toPx(),
                size.height - 4.dp.toPx(),
                paint,
            )
        }
    }
}

// ── Recent scan cards ────────────────────────────────────────────────────────

@Composable
private fun RecentScanCard(row: ScanRowUi, onClick: () -> Unit) {
    val summary = row.summary
    val dateText = remember(summary.startTimeMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(summary.startTimeMs))
    }
    val rssi = row.bestRssi
    val quality = rssi?.let { WifiChannelUtils.qualityLabel(it) } ?: "—"
    val qColor = when {
        rssi == null -> Color.White.copy(alpha = 0.5f)
        rssi >= -50 -> AccentGreen
        rssi >= -60 -> AccentGreen
        rssi >= -70 -> AccentYellow
        rssi >= -80 -> AccentPurple
        else -> Color(0xFFEF5350)
    }
    val ssid = row.primarySsid?.ifBlank { null } ?: "—"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeatmapThumb(rssi = rssi)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    summary.locationName.ifBlank { "Unnamed session" },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    dateText,
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(qColor),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        rssi?.let { "$it dBm" } ?: "— dBm",
                        color = qColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        quality,
                        color = qColor.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Wifi,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        ssid,
                        color = AccentBlue.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (summary.endTimeMs == null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "In progress",
                            color = AccentOrange,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun HeatmapThumb(rssi: Int?) {
    val c1 = when {
        rssi == null -> Color(0xFF37474F)
        rssi >= -55 -> AccentGreen
        rssi >= -70 -> AccentYellow
        else -> AccentPurple
    }
    val c2 = when {
        rssi == null -> Color(0xFF263238)
        rssi >= -55 -> Color(0xFF1B5E20)
        rssi >= -70 -> Color(0xFFE65100)
        else -> Color(0xFF4A148C)
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(c1.copy(alpha = 0.85f), c2, Color(0xFF12151C)),
                    radius = 80f,
                ),
            )
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp)),
    )
}

// ── Speed cards ──────────────────────────────────────────────────────────────

@Composable
private fun AnalyzerSpeedCard(test: SpeedTestEntity) {
    val dateText = remember(test.timestampMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(test.timestampMs))
    }
    AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    SpeedFormat.formatDetailLine(test.downloadMbps, test.uploadMbps, test.pingMs),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                dateText,
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                "↓ ${SpeedFormat.formatMbps(test.downloadMbps)}  ·  ↑ ${SpeedFormat.formatMbps(test.uploadMbps)}  ·  ${test.pingMs} ms",
                color = AccentGreen,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "Backend: ${test.backend}",
                color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// ── Reports ──────────────────────────────────────────────────────────────────

@Composable
private fun ReportsTab(
    sessionCount: Int,
    sampleEstimate: Int,
    bestSignal: Int?,
    bestDownload: Float?,
    totalScanMs: Long,
    speedCount: Int,
) {
    AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Local Performance Report",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Generated on-device from your mapping sessions. No cloud account required.",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
            )
            ReportLine("Total sessions", sessionCount.toString())
            ReportLine("RSSI samples (listed)", sampleEstimate.toString())
            ReportLine("Speed tests", speedCount.toString())
            ReportLine("Best signal", bestSignal?.let { "$it dBm" } ?: "—")
            ReportLine("Best download", bestDownload?.let { SpeedFormat.formatMbps(it) } ?: "—")
            ReportLine("Total scan time", formatDuration(totalScanMs))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Tip: Export scan history from Settings to share PNG + CSV heatmaps.",
                color = AccentGreen.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ReportLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color.White, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyHistoryHint(title: String, body: String) {
    AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                body,
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0m"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> {
            val secs = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
            if (minutes > 0) "${minutes}m" else "${secs}s"
        }
    }
}

// ── Session detail (kept functional) ─────────────────────────────────────────

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
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = summary.locationName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
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
                    color = Color.White,
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
                        color = AccentGreen,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Text(
                    text = stringResource(R.string.history_dead_zones_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(4.dp))
                when {
                    analysing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp,
                                color = AccentGreen,
                            )
                            Text(
                                text = stringResource(R.string.history_dead_zones_analysing),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
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
                            color = Color.White.copy(alpha = 0.7f),
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
                            color = Color(0xFFEF5350),
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
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (speedTests.isEmpty()) {
                    Text(
                        text = stringResource(R.string.history_speed_tests_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.history_speed_tests_count,
                            speedTests.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentGreen,
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
                    color = Color.White,
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
            containerColor = Color(0xFF1C1F26),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(
                    R.string.speed_test_history_title,
                    SpeedFormat.formatMbps(test.downloadMbps),
                    SpeedFormat.formatMbps(test.uploadMbps),
                ),
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.speed_test_detail_ping, test.pingMs),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
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
                color = Color.White.copy(alpha = 0.55f),
            )
            Text(
                text = stringResource(R.string.speed_test_detail_backend, test.backend),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun DeadZoneHistoryCard(zone: DeadZoneRegion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x44B71C1C),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.dead_zone_detail_title, zone.id),
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFEF5350),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.dead_zone_detail_worst, zone.worstRssiDbm),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = stringResource(R.string.dead_zone_detail_avg, zone.averageRssiDbm),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = stringResource(
                    R.string.dead_zone_detail_area,
                    zone.areaSqM,
                    zone.cellCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = stringResource(
                    R.string.dead_zone_detail_location,
                    zone.relativeDescriptionFromOrigin(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun SampleRow(sample: RssiSampleEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = sample.ssid.ifBlank { "<hidden>" },
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
            )
            Text(
                text = sample.bssid,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.55f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${sample.rssiDbm} dBm · ${sample.frequencyMhz} MHz",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentGreen,
                )
                Text(
                    text = "xyz (%.2f, %.2f, %.2f)".format(
                        sample.poseX,
                        sample.poseY,
                        sample.poseZ,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }
    }
}
