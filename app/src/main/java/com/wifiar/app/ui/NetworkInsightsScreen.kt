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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiar.app.scanner.RssiSample
import com.wifiar.app.scanner.WifiChannelUtils
import com.wifiar.app.scanner.WifiScanner
import com.wifiar.app.ui.components.AnalyzerCard
import com.wifiar.app.ui.components.CompactPrimaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.PI

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
            delay(15_000)
            if (scanner.cooldownSeconds.value == 0L) {
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
                    item { HeatmapHintCard() }
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

@Composable
private fun HeatmapHintCard() {
    AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Spatial heatmap",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Open the AR Scan tab, start a mapping session, and walk the room. " +
                    "WifiAR builds a floor heatmap from fused RSSI + AR pose. " +
                    "Use Points / Heatmap / Both to visualize coverage in 3D.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Green = strong · Yellow = fair · Purple = weak · Red = dead zones.",
                color = Color(0xFF69F0AE),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
