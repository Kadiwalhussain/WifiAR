package com.wifiar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifiar.app.util.SpeedFormat

/** Dark glass card used across the analyzer-style HUD. */
@Composable
fun AnalyzerCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE61C1F26))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        content()
    }
}

@Composable
fun AnalyzerTopBar(
    live: Boolean,
    title: String = "Wi‑Fi AR Analyzer",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xEE12151C))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (live) Color(0xFF69F0AE) else Color(0xFFFFAB40)),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (live) "Live Scan" else "Waiting for AR…",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.Wifi,
            contentDescription = null,
            tint = Color(0xFF69F0AE),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun ConnectedNetworkCard(
    ssid: String,
    connected: Boolean,
    detail: String,
    modifier: Modifier = Modifier,
) {
    AnalyzerCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x224CAF50)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Wifi,
                    contentDescription = null,
                    tint = Color(0xFF69F0AE),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = ssid.ifBlank { "No network" },
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = if (connected) "Connected" else "Not connected",
                    color = if (connected) Color(0xFF69F0AE) else Color(0xFFFFAB40),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun SignalLegendCard(modifier: Modifier = Modifier) {
    AnalyzerCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendRow(Color(0xFF4CAF50), "Strong (−30 to −60 dBm)")
            LegendRow(Color(0xFFFFEB3B), "Fair (−60 to −75 dBm)")
            LegendRow(Color(0xFFAB47BC), "Weak (−75 to −90 dBm)")
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.88f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun ScanHintCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    AnalyzerCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = body,
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
fun SpeedTestPanel(
    downloadMbps: Float?,
    uploadMbps: Float?,
    running: Boolean,
    enabled: Boolean,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnalyzerCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Speed,
                    contentDescription = null,
                    tint = Color(0xFF69F0AE),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Speed Test",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (running) "Running…" else if (downloadMbps != null) "Just now" else "Ready",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SpeedMetric(
                    label = "Download",
                    value = downloadMbps?.let { SpeedFormat.formatMbps(it) } ?: "—",
                    accent = Color(0xFF69F0AE),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .background(Color.White.copy(alpha = 0.12f)),
                )
                SpeedMetric(
                    label = "Upload",
                    value = uploadMbps?.let { SpeedFormat.formatMbps(it) } ?: "—",
                    accent = Color(0xFFCE93D8),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            CompactPrimaryButton(
                text = if (running) "Testing…" else "Run Speed Test",
                onClick = onRun,
                enabled = enabled && !running,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color(0xFF1B5E20),
                contentColor = Color(0xFF69F0AE),
            )
        }
    }
}

@Composable
private fun SpeedMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value,
            color = accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
