package com.wifiar.app.ui

import android.net.wifi.WifiManager
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiar.app.data.UserPreferences
import com.wifiar.app.data.local.RssiSampleEntity
import com.wifiar.app.data.local.WifiArDatabase
import com.wifiar.app.scanner.RssiSample
import com.wifiar.app.scanner.WifiChannelUtils
import com.wifiar.app.scanner.WifiScanner
import com.wifiar.app.ui.components.AnalyzerCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

private enum class InsightsTab { OVERVIEW, CHANNELS, HEATMAP }
private enum class BandFilter { GHZ_24, GHZ_5 }

private val ChartColors = listOf(
    Color(0xFF4CAF50),
    Color(0xFF42A5F5),
    Color(0xFFFFEB3B),
    Color(0xFFAB47BC),
    Color(0xFFFF7043),
    Color(0xFF26C6DA),
    Color(0xFFEF5350),
    Color(0xFF8D6E63),
)

/**
 * Detailed Insights — Networks page (channels graph, list, details gauge).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkInsightsScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { WifiScanner(context, scope) }

    DisposableEffect(scanner) {
        scanner.start()
        scanner.triggerScan()
        onDispose { scanner.stop() }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            val intervalMs = (UserPreferences.scanIntervalSec.coerceIn(2, 120) * 1_000L)
            delay(intervalMs)
            if (UserPreferences.autoScan && scanner.cooldownSeconds.value == 0L) {
                scanner.triggerScan()
            }
        }
    }

    val results by scanner.scanResults.collectAsStateWithLifecycle()
    val isScanning by scanner.isScanning.collectAsStateWithLifecycle()
    val cooldown by scanner.cooldownSeconds.collectAsStateWithLifecycle()
    val connected = remember(results) { scanner.connectedNetwork() }

    var tab by remember { mutableStateOf(InsightsTab.CHANNELS) }
    var band by remember { mutableStateOf(BandFilter.GHZ_24) }
    var selectedBssid by remember { mutableStateOf<String?>(null) }

    val filtered = remember(results, band) {
        results.filter {
            when (band) {
                BandFilter.GHZ_24 ->
                    WifiChannelUtils.bandOf(it.frequencyMhz) == WifiChannelUtils.Band.BAND_2_4
                BandFilter.GHZ_5 ->
                    WifiChannelUtils.bandOf(it.frequencyMhz) == WifiChannelUtils.Band.BAND_5 ||
                        WifiChannelUtils.bandOf(it.frequencyMhz) == WifiChannelUtils.Band.BAND_6
            }
        }.sortedByDescending { it.rssiDbm }
    }

    // Auto-select connected or strongest
    LaunchedEffect(filtered, connected.bssid) {
        if (selectedBssid == null || filtered.none { it.bssid == selectedBssid }) {
            selectedBssid = filtered.firstOrNull {
                it.bssid.equals(connected.bssid, ignoreCase = true)
            }?.bssid ?: filtered.firstOrNull()?.bssid
        }
    }

    val selected = filtered.firstOrNull { it.bssid == selectedBssid }
        ?: filtered.firstOrNull()

    val linkSpeedMbps = remember(connected) {
        runCatching {
            val wm = context.applicationContext
                .getSystemService(android.content.Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.connectionInfo?.linkSpeed?.takeIf { it > 0 }
        }.getOrNull()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0B0D12),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Wi‑Fi AR Analyzer",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Text(
                            "Detailed Insights",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scanner.triggerScan() },
                        enabled = cooldown == 0L && !isScanning,
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Scan",
                            tint = Color(0xFF69F0AE),
                        )
                    }
                    IconButton(onClick = { /* share hook */ }) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = Color.White.copy(alpha = 0.7f),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0D12),
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InsightsTabRow(tab = tab, onSelect = { tab = it })
            }

            when (tab) {
                InsightsTab.OVERVIEW -> {
                    item {
                        OverviewSection(
                            networks = results.sortedByDescending { it.rssiDbm },
                            connectedBssid = connected.bssid,
                            connectedSsid = connected.ssid,
                            onSelect = {
                                selectedBssid = it.bssid
                                tab = InsightsTab.CHANNELS
                            },
                        )
                    }
                }
                InsightsTab.CHANNELS -> {
                    item {
                        ChannelsSection(
                            networks = filtered,
                            band = band,
                            onBand = { band = it },
                            selectedBssid = selected?.bssid,
                            connectedBssid = connected.bssid,
                            onSelect = { selectedBssid = it.bssid },
                        )
                    }
                    item {
                        selected?.let { net ->
                            NetworkDetailsCard(
                                sample = net,
                                connected = net.bssid.equals(connected.bssid, true),
                                ipAddress = if (net.bssid.equals(connected.bssid, true)) {
                                    connected.ipAddress
                                } else {
                                    "—"
                                },
                                linkSpeedMbps = if (net.bssid.equals(connected.bssid, true)) {
                                    linkSpeedMbps
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    item { RouterTipCard() }
                }
                InsightsTab.HEATMAP -> {
                    item {
                        HeatmapInsightsSection(
                            networks = results,
                            connected = connected,
                            scanner = scanner,
                            isScanning = isScanning,
                            canScan = cooldown == 0L && !isScanning,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightsTabRow(
    tab: InsightsTab,
    onSelect: (InsightsTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF161A22))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        InsightsTabChip(
            selected = tab == InsightsTab.OVERVIEW,
            icon = Icons.Outlined.GridView,
            label = "Overview",
            onClick = { onSelect(InsightsTab.OVERVIEW) },
            modifier = Modifier.weight(1f),
        )
        InsightsTabChip(
            selected = tab == InsightsTab.CHANNELS,
            icon = Icons.Outlined.SignalCellularAlt,
            label = "Channels",
            onClick = { onSelect(InsightsTab.CHANNELS) },
            modifier = Modifier.weight(1f),
        )
        InsightsTabChip(
            selected = tab == InsightsTab.HEATMAP,
            icon = Icons.Outlined.Thermostat,
            label = "Heatmap",
            onClick = { onSelect(InsightsTab.HEATMAP) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InsightsTabChip(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF1B5E20) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = if (selected) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun OverviewSection(
    networks: List<RssiSample>,
    connectedBssid: String,
    connectedSsid: String,
    onSelect: (RssiSample) -> Unit,
) {
    AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Nearby networks",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "${networks.size} AP(s) · Connected: $connectedSsid",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
            )
            if (networks.isEmpty()) {
                Text(
                    "No scan results yet. Pull to refresh or wait for a scan.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                networks.take(20).forEach { n ->
                    NetworkListRow(
                        sample = n,
                        color = Color(WifiChannelUtils.qualityColorArgb(n.rssiDbm)),
                        selected = false,
                        connected = n.bssid.equals(connectedBssid, true),
                        onClick = { onSelect(n) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelsSection(
    networks: List<RssiSample>,
    band: BandFilter,
    onBand: (BandFilter) -> Unit,
    selectedBssid: String?,
    connectedBssid: String,
    onSelect: (RssiSample) -> Unit,
) {
    AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Wi‑Fi Channels",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                BandToggle(band = band, onBand = onBand)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (band == BandFilter.GHZ_24) "2.4 GHz" else "5 GHz",
                color = Color(0xFF69F0AE),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            ChannelGraph(
                networks = networks.take(8),
                band = band,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))
            networks.take(12).forEachIndexed { index, n ->
                NetworkListRow(
                    sample = n,
                    color = ChartColors[index % ChartColors.size],
                    selected = n.bssid == selectedBssid,
                    connected = n.bssid.equals(connectedBssid, true),
                    onClick = { onSelect(n) },
                )
                if (index < networks.take(12).lastIndex) {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
            if (networks.isEmpty()) {
                Text(
                    "No ${if (band == BandFilter.GHZ_24) "2.4" else "5"} GHz networks found. Try scanning again.",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun BandToggle(
    band: BandFilter,
    onBand: (BandFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0E1218))
            .padding(2.dp),
    ) {
        listOf(BandFilter.GHZ_24 to "2.4 GHz", BandFilter.GHZ_5 to "5 GHz").forEach { (b, label) ->
            val sel = band == b
            Text(
                text = label,
                color = if (sel) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sel) Color(0xFF1B5E20) else Color.Transparent)
                    .clickable { onBand(b) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ChannelGraph(
    networks: List<RssiSample>,
    band: BandFilter,
    modifier: Modifier = Modifier,
) {
    val channelRange = if (band == BandFilter.GHZ_24) 1f..14f else 36f..165f
    val minCh = channelRange.start
    val maxCh = channelRange.endInclusive
    val span = (maxCh - minCh).coerceAtLeast(1f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0E1218))
            .padding(8.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = 36f
            val right = size.width - 8f
            val top = 16f
            val bottom = size.height - 28f
            val w = right - left
            val h = bottom - top

            // Grid lines for -20, -40, -60, -80, -100
            val rssiMarks = listOf(-20, -40, -60, -80, -100)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(120, 255, 255, 255)
                textSize = 22f
                isAntiAlias = true
            }
            rssiMarks.forEach { rssi ->
                val t = ((rssi + 100f) / 80f).coerceIn(0f, 1f)
                val y = bottom - t * h
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 1f,
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "$rssi",
                    4f,
                    y + 8f,
                    paint,
                )
            }

            fun xForChannel(ch: Float): Float {
                val t = ((ch - minCh) / span).coerceIn(0f, 1f)
                return left + t * w
            }
            fun yForRssi(rssi: Int): Float {
                val t = ((rssi + 100f) / 80f).coerceIn(0f, 1f)
                return bottom - t * h
            }

            // Bell curves centered on each AP's channel
            networks.forEachIndexed { index, n ->
                val ch = WifiChannelUtils.channelOf(n.frequencyMhz).toFloat()
                    .coerceIn(minCh, maxCh)
                val color = ChartColors[index % ChartColors.size]
                val peak = n.rssiDbm.coerceIn(-100, -20)
                val sigma = if (band == BandFilter.GHZ_24) 1.6f else 8f
                val path = Path()
                val steps = 48
                for (i in 0..steps) {
                    val c = minCh + (span * i / steps)
                    val d = (c - ch) / sigma
                    val rssi = -100f + (peak + 100f) * exp(-0.5f * d * d).toFloat()
                    val x = xForChannel(c)
                    val y = yForRssi(rssi.toInt())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                // Fill under curve
                val fill = Path().apply {
                    addPath(path)
                    lineTo(xForChannel(maxCh), bottom)
                    lineTo(xForChannel(minCh), bottom)
                    close()
                }
                drawPath(
                    path = fill,
                    brush = Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f)),
                        startY = top,
                        endY = bottom,
                    ),
                )
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = 3f, cap = StrokeCap.Round),
                )
                // Peak dot + label
                val px = xForChannel(ch)
                val py = yForRssi(peak)
                drawCircle(color = color, radius = 7f, center = Offset(px, py))
                drawCircle(color = Color.White, radius = 3f, center = Offset(px, py))
            }

            // X labels
            val xLabels = if (band == BandFilter.GHZ_24) {
                listOf(1, 6, 11)
            } else {
                listOf(36, 100, 149)
            }
            val xPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(140, 255, 255, 255)
                textSize = 24f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            xLabels.forEach { ch ->
                val x = xForChannel(ch.toFloat())
                drawContext.canvas.nativeCanvas.drawText(
                    "$ch",
                    x,
                    size.height - 4f,
                    xPaint,
                )
            }
        }
    }
}

@Composable
private fun NetworkListRow(
    sample: RssiSample,
    color: Color,
    selected: Boolean,
    connected: Boolean,
    onClick: () -> Unit,
) {
    val ch = WifiChannelUtils.channelOf(sample.frequencyMhz)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF1A2330) else Color(0xFF12161E))
            .border(
                1.dp,
                if (selected) Color(0xFF2E7D32) else Color(0x22FFFFFF),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sample.ssid.ifBlank { "<hidden>" },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (connected) {
                    Text(
                        " (Connected)",
                        color = Color(0xFF69F0AE),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Text(
            text = "Ch $ch",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(
            text = "${sample.rssiDbm} dBm",
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun NetworkDetailsCard(
    sample: RssiSample,
    connected: Boolean,
    ipAddress: String,
    linkSpeedMbps: Int?,
) {
    val quality = WifiChannelUtils.qualityLabel(sample.rssiDbm)
    val accent = Color(WifiChannelUtils.qualityColorArgb(sample.rssiDbm))
    val ch = WifiChannelUtils.channelOf(sample.frequencyMhz)
    val band = WifiChannelUtils.bandLabel(WifiChannelUtils.bandOf(sample.frequencyMhz))

    AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Wifi,
                    contentDescription = null,
                    tint = Color(0xFF69F0AE),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Network Details",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    DetailRow("SSID", sample.ssid.ifBlank { "<hidden>" })
                    DetailRow("BSSID", sample.bssid.ifBlank { "—" })
                    DetailRow("Frequency", "${sample.frequencyMhz} MHz (Channel $ch)")
                    DetailRow("Band", band)
                    DetailRow(
                        "Security",
                        sample.capabilities.takeIf { it.isNotBlank() }?.let {
                            it.replace("[", "").replace("]", "").take(28)
                        } ?: "—",
                    )
                    DetailRow("IP Address", if (connected) ipAddress else "—")
                    DetailRow(
                        "Signal Strength",
                        "${sample.rssiDbm} dBm ($quality)",
                        valueColor = accent,
                    )
                    DetailRow(
                        "Link Speed",
                        linkSpeedMbps?.let { "$it Mbps" } ?: "—",
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                RssiGauge(
                    rssiDbm = sample.rssiDbm,
                    quality = quality,
                    accent = accent,
                    modifier = Modifier.size(110.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.White.copy(alpha = 0.9f),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.45f),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            value,
            color = valueColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun RssiGauge(
    rssiDbm: Int,
    quality: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    // Map −100…−30 → 0…1 for arc progress
    val progress = ((rssiDbm + 100f) / 70f).coerceIn(0.05f, 1f)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(modifier = Modifier.size(96.dp)) {
            val stroke = 12f
            val inset = stroke / 2f
            // background arc
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    size.width - stroke,
                    size.height - stroke,
                ),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = 135f,
                sweepAngle = 270f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    size.width - stroke,
                    size.height - stroke,
                ),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "$rssiDbm",
            color = accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = "dBm",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = quality,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RouterTipCard() {
    AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = Color(0xFFFFD54F),
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Tip: Move your router to a central location and elevate it for better coverage.",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
            )
        }
    }
}

/**
 * Heatmap tab: 2D coverage map, live RSSI chart, recommendations.
 */
@Composable
private fun HeatmapInsightsSection(
    networks: List<RssiSample>,
    connected: WifiScanner.ConnectedNetwork,
    scanner: WifiScanner,
    isScanning: Boolean,
    canScan: Boolean,
) {
    val context = LocalContext.current
    val db = remember { WifiArDatabase.getInstance(context) }

    // Live RSSI history (last ~60s)
    val history = remember { mutableStateListOf<Pair<Long, Int>>() }
    val liveRssi = remember(networks, connected.bssid) {
        networks.firstOrNull { it.bssid.equals(connected.bssid, true) }?.rssiDbm
            ?: networks.maxByOrNull { it.rssiDbm }?.rssiDbm
    }
    LaunchedEffect(liveRssi) {
        val r = liveRssi ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        history.add(now to r)
        val cutoff = now - 60_000L
        while (history.isNotEmpty() && history.first().first < cutoff) {
            history.removeAt(0)
        }
    }
    // Also tick every second so chart animates even if RSSI unchanged.
    // Re-read live value from networks each tick (do not capture a stale Int?).
    LaunchedEffect(networks, connected.bssid) {
        while (isActive) {
            delay(1_000)
            val r = networks.firstOrNull { it.bssid.equals(connected.bssid, true) }?.rssiDbm
                ?: networks.maxByOrNull { it.rssiDbm }?.rssiDbm
            if (r != null) {
                val now = System.currentTimeMillis()
                history.add(now to r)
                val cutoff = now - 60_000L
                while (history.isNotEmpty() && history.first().first < cutoff) {
                    history.removeAt(0)
                }
            }
        }
    }

    // Mapped session samples for real spatial heatmap (if any)
    var mappedSamples by remember { mutableStateOf<List<RssiSampleEntity>>(emptyList()) }
    var mapLabel by remember { mutableStateOf("Live estimate") }
    LaunchedEffect(Unit) {
        mappedSamples = withContext(Dispatchers.IO) {
            runCatching {
                val sessions = db.mappingSessionDao().getResumableSessions("")
                val latest = sessions.firstOrNull()
                    ?: return@runCatching emptyList()
                val all = db.rssiSampleDao().getAllForSessionOnce(latest.sessionId)
                if (all.size >= 5) {
                    mapLabel = latest.locationName.ifBlank { "Last session" }
                    all
                } else {
                    emptyList()
                }
            }.getOrDefault(emptyList())
        }
    }

    val peakRssi = liveRssi ?: -65
    val heatmapBitmap = remember(mappedSamples, peakRssi, networks.size) {
        if (mappedSamples.size >= 5) {
            buildSpatialHeatmapBitmap(mappedSamples, 220)
        } else {
            buildRadialHeatmapBitmap(peakRssi.coerceIn(-95, -30), 220)
        }
    }

    val quality = WifiChannelUtils.qualityLabel(peakRssi)
    val accent = Color(WifiChannelUtils.qualityColorArgb(peakRssi))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Heatmap map card ─────────────────────────────────────────────
        AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Wi‑Fi Signal Heatmap",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "Visualize signal strength around you",
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    CompactRescanChip(
                        enabled = canScan,
                        scanning = isScanning,
                        onClick = { scanner.triggerScan() },
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(220.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0E1218)),
                    ) {
                        Image(
                            bitmap = heatmapBitmap.asImageBitmap(),
                            contentDescription = "Signal heatmap",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Router pin
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offsetRouterPin(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xEE12151C))
                                    .border(1.dp, Color(0x4469F0AE), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Wifi,
                                        contentDescription = null,
                                        tint = Color(0xFF69F0AE),
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Router",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        Text(
                            mapLabel,
                            color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x99000000))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.width(88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HeatLegendDot(Color(0xFF4CAF50), "Strong", "−30 to −60 dBm")
                        HeatLegendDot(Color(0xFFFFEB3B), "Fair", "−60 to −75 dBm")
                        HeatLegendDot(Color(0xFFAB47BC), "Weak", "−75 to −90 dBm")
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF161A22))
                                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Floor 1",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                if (mappedSamples.size < 5) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tip: Map a room in AR Scan for a walk-based spatial heatmap. " +
                            "Showing live radial estimate from current RSSI until then.",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        // ── Live signal chart ────────────────────────────────────────────
        AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Signal Strength at This Location",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF69F0AE)),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Live",
                            color = Color(0xFF69F0AE),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.padding(end = 12.dp)) {
                        Text(
                            text = if (liveRssi != null) "$liveRssi dBm" else "— dBm",
                            color = accent,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            quality,
                            color = accent,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    LiveRssiChart(
                        points = history.toList(),
                        accent = accent,
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp),
                    )
                }
            }
        }

        // ── Recommendations ──────────────────────────────────────────────
        AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Recommendations",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                buildRecommendations(networks, peakRssi).forEach { rec ->
                    RecommendationRow(
                        title = rec.title,
                        body = rec.body,
                        tint = rec.tint,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactRescanChip(
    enabled: Boolean,
    scanning: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1B5E20).copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Refresh,
            contentDescription = null,
            tint = Color(0xFF69F0AE),
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            if (scanning) "Scanning…" else "Rescan",
            color = Color(0xFF69F0AE),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HeatLegendDot(color: Color, title: String, range: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(title, color = Color.White, style = MaterialTheme.typography.labelMedium)
            Text(
                range,
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Small offset helper so router pin sits cleanly above center. */
private fun Modifier.offsetRouterPin(): Modifier = this.padding(bottom = 8.dp)

@Composable
private fun LiveRssiChart(
    points: List<Pair<Long, Int>>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0E1218))
            .padding(6.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = 28f
            val right = size.width - 4f
            val top = 6f
            val bottom = size.height - 18f
            val w = right - left
            val h = bottom - top
            // grid
            listOf(-20, -40, -60, -80, -100).forEach { rssi ->
                val t = ((rssi + 100f) / 80f).coerceIn(0f, 1f)
                val y = bottom - t * h
                drawLine(
                    Color.White.copy(alpha = 0.06f),
                    Offset(left, y),
                    Offset(right, y),
                    1f,
                )
            }
            if (points.size < 2) return@Canvas
            val t0 = points.first().first
            val t1 = points.last().first.coerceAtLeast(t0 + 1)
            val path = Path()
            points.forEachIndexed { i, (ts, rssi) ->
                val x = left + ((ts - t0).toFloat() / (t1 - t0)) * w
                val y = bottom - (((rssi + 100f) / 80f).coerceIn(0f, 1f)) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(right, bottom)
                lineTo(left, bottom)
                close()
            }
            drawPath(
                fill,
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.02f)),
                ),
            )
            drawPath(path, accent, style = Stroke(3f, cap = StrokeCap.Round))
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("−60s", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
            Text("Now", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RecommendationRow(
    title: String,
    body: String,
    tint: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF12161E))
            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Wifi, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(body, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
        )
    }
}

private data class Rec(val title: String, val body: String, val tint: Color)

private fun buildRecommendations(networks: List<RssiSample>, peakRssi: Int): List<Rec> {
    val list = ArrayList<Rec>(3)
    if (peakRssi < -65) {
        list.add(
            Rec(
                "Move your router to a more central location",
                "This can help improve overall coverage.",
                Color(0xFF42A5F5),
            ),
        )
    } else {
        list.add(
            Rec(
                "Keep the router elevated and unobstructed",
                "Avoid cabinets and thick walls near the AP.",
                Color(0xFF42A5F5),
            ),
        )
    }
    list.add(
        Rec(
            "Consider upgrading your router",
            "A dual-band or Wi‑Fi 6 router can provide better performance.",
            Color(0xFFFFA726),
        ),
    )
    val ch24 = networks
        .filter { WifiChannelUtils.bandOf(it.frequencyMhz) == WifiChannelUtils.Band.BAND_2_4 }
        .map { WifiChannelUtils.channelOf(it.frequencyMhz) }
    val crowded = ch24.groupingBy { it }.eachCount().any { it.value >= 3 }
    list.add(
        Rec(
            if (crowded) "Use less congested channels" else "Prefer channels 1, 6, or 11",
            "Try channels 1, 6 or 11 for 2.4 GHz band.",
            Color(0xFFAB47BC),
        ),
    )
    return list
}

/** Radial heatmap from a single peak RSSI (live mode). */
private fun buildRadialHeatmapBitmap(peakRssi: Int, sizePx: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    // Multi-stop radial: strong center → fair → weak edges
    val strength = ((peakRssi + 100f) / 70f).coerceIn(0.2f, 1f)
    val colors = intArrayOf(
        android.graphics.Color.argb(230, 0x4C, 0xAF, 0x50), // green
        android.graphics.Color.argb(200, 0xFF, 0xEB, 0x3B), // yellow
        android.graphics.Color.argb(210, 0xAB, 0x47, 0xBC), // purple
        android.graphics.Color.argb(220, 0x6A, 0x1B, 0x9A),
    )
    val stops = floatArrayOf(0f, 0.35f * strength, 0.7f, 1f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(cx, cy, sizePx * 0.62f, colors, stops, Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
    // Soft room grid overlay
    val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(40, 255, 255, 255)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    val step = sizePx / 6f
    for (i in 1 until 6) {
        canvas.drawLine(i * step, 0f, i * step, sizePx.toFloat(), grid)
        canvas.drawLine(0f, i * step, sizePx.toFloat(), i * step, grid)
    }
    return bmp
}

/** Spatial IDW-style heatmap from mapped session samples. */
private fun buildSpatialHeatmapBitmap(samples: List<RssiSampleEntity>, sizePx: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    var minX = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY
    for (s in samples) {
        if (s.poseX < minX) minX = s.poseX
        if (s.poseX > maxX) maxX = s.poseX
        if (s.poseZ < minZ) minZ = s.poseZ
        if (s.poseZ > maxZ) maxZ = s.poseZ
    }
    val pad = 0.4f
    minX -= pad
    maxX += pad
    minZ -= pad
    maxZ += pad
    if (maxX - minX < 1f) {
        val m = (minX + maxX) / 2f
        minX = m - 0.5f
        maxX = m + 0.5f
    }
    if (maxZ - minZ < 1f) {
        val m = (minZ + maxZ) / 2f
        minZ = m - 0.5f
        maxZ = m + 0.5f
    }
    val pixels = IntArray(sizePx * sizePx)
    val power = 2.0
    val eps = 1e-4
    for (py in 0 until sizePx) {
        val z = minZ + (1f - (py + 0.5f) / sizePx) * (maxZ - minZ)
        for (px in 0 until sizePx) {
            val x = minX + ((px + 0.5f) / sizePx) * (maxX - minX)
            var num = 0.0
            var den = 0.0
            for (s in samples) {
                val d = hypot((x - s.poseX).toDouble(), (z - s.poseZ).toDouble())
                val w = 1.0 / (d.pow(power) + eps)
                num += w * s.rssiDbm
                den += w
            }
            val rssi = if (den > 0) (num / den).toFloat() else -90f
            pixels[py * sizePx + px] = rssiToHeatArgb(rssi)
        }
    }
    bmp.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    return bmp
}

private fun rssiToHeatArgb(rssi: Float): Int {
    // green ≥ −60, yellow −60…−75, purple ≤ −75
    val a = 210
    return when {
        rssi >= -50f -> android.graphics.Color.argb(a, 0x2E, 0x7D, 0x32)
        rssi >= -60f -> android.graphics.Color.argb(a, 0x4C, 0xAF, 0x50)
        rssi >= -68f -> android.graphics.Color.argb(a, 0xFF, 0xEB, 0x3B)
        rssi >= -75f -> android.graphics.Color.argb(a, 0xFF, 0xA0, 0x00)
        rssi >= -85f -> android.graphics.Color.argb(a, 0xAB, 0x47, 0xBC)
        else -> android.graphics.Color.argb(a, 0x6A, 0x1B, 0x9A)
    }
}
