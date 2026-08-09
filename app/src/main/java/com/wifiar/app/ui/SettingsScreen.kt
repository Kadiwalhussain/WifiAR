@file:OptIn(ExperimentalMaterial3Api::class)

package com.wifiar.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wifiar.app.data.UserPreferences
import com.wifiar.app.data.export.HeatmapExporter
import com.wifiar.app.data.local.WifiArDatabase
import com.wifiar.app.ui.components.AnalyzerCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Analyzer-style Settings matching the mock: scan, visualization,
 * notifications, data & privacy, about.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { WifiArDatabase.getInstance(context) }
    val exporter = remember { HeatmapExporter(context) }

    var autoScan by remember { mutableStateOf(UserPreferences.autoScan) }
    var scanInterval by remember { mutableStateOf(UserPreferences.scanIntervalSec) }
    var includeHidden by remember { mutableStateOf(UserPreferences.includeHiddenNetworks) }
    var arSmoothing by remember { mutableStateOf(UserPreferences.arSignalSmoothing) }
    var signalUnits by remember { mutableStateOf(UserPreferences.signalUnits) }
    var colorScheme by remember { mutableStateOf(UserPreferences.colorScheme) }
    var density by remember { mutableStateOf(UserPreferences.particleDensity) }
    var scanAlerts by remember { mutableStateOf(UserPreferences.scanAlerts) }
    var weeklyReports by remember { mutableStateOf(UserPreferences.weeklyReports) }

    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    dialog?.let { d ->
        SettingsChoiceDialog(
            dialog = d,
            onDismiss = { dialog = null },
            onPick = { value ->
                when (d) {
                    is SettingsDialog.ScanInterval -> {
                        scanInterval = value as Int
                        UserPreferences.scanIntervalSec = scanInterval
                    }
                    is SettingsDialog.SignalUnits -> {
                        signalUnits = value as String
                        UserPreferences.signalUnits = signalUnits
                    }
                    is SettingsDialog.ColorScheme -> {
                        colorScheme = value as String
                        UserPreferences.colorScheme = colorScheme
                    }
                    is SettingsDialog.ParticleDensity -> {
                        density = value as String
                        UserPreferences.particleDensity = density
                    }
                    is SettingsDialog.ClearHistory -> { /* confirm handled below */ }
                }
                dialog = null
            },
            onConfirmClear = {
                dialog = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            db.speedTestDao().deleteAll()
                            db.rssiSampleDao().deleteAll()
                            db.mappingSessionDao().deleteAll()
                        }
                    }
                    statusMsg = "Scan history cleared"
                    Toast.makeText(context, "All local sessions deleted", Toast.LENGTH_SHORT).show()
                }
            },
        )
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
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Settings",
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0D12),
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            statusMsg?.let {
                Text(it, color = Color(0xFF69F0AE), style = MaterialTheme.typography.labelMedium)
            }

            // ── SCAN SETTINGS ────────────────────────────────────────────
            SettingsSectionHeader("SCAN SETTINGS", Color(0xFF69F0AE))
            AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsToggleRow(
                        icon = Icons.Outlined.Radar,
                        iconTint = Color(0xFF69F0AE),
                        title = "Auto Scan",
                        subtitle = "Automatically scan for Wi‑Fi networks",
                        checked = autoScan,
                        onChecked = {
                            autoScan = it
                            UserPreferences.autoScan = it
                        },
                    )
                    SettingsNavRow(
                        icon = Icons.Outlined.Schedule,
                        iconTint = Color(0xFF64B5F6),
                        title = "Scan Interval",
                        value = "$scanInterval seconds",
                        onClick = { dialog = SettingsDialog.ScanInterval },
                    )
                    SettingsToggleRow(
                        icon = Icons.Outlined.VisibilityOff,
                        iconTint = Color(0xFFAB47BC),
                        title = "Include Hidden Networks",
                        subtitle = "Show hidden Wi‑Fi networks in scan",
                        checked = includeHidden,
                        onChecked = {
                            includeHidden = it
                            UserPreferences.includeHiddenNetworks = it
                        },
                    )
                    SettingsToggleRow(
                        icon = Icons.Outlined.BlurOn,
                        iconTint = Color(0xFFFFA726),
                        title = "AR Signal Smoothing",
                        subtitle = "Smooth signal changes for better visualization",
                        checked = arSmoothing,
                        onChecked = {
                            arSmoothing = it
                            UserPreferences.arSignalSmoothing = it
                        },
                    )
                }
            }

            // ── VISUALIZATION ────────────────────────────────────────────
            SettingsSectionHeader("VISUALIZATION", Color(0xFF69F0AE))
            AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsNavRow(
                        icon = Icons.Outlined.SignalCellularAlt,
                        iconTint = Color(0xFF42A5F5),
                        title = "Signal Units",
                        value = if (signalUnits == "percent") "% (relative)" else "dBm (decibel-milliwatts)",
                        onClick = { dialog = SettingsDialog.SignalUnits },
                    )
                    SettingsNavRow(
                        icon = Icons.Outlined.Palette,
                        iconTint = Color(0xFFFFEB3B),
                        title = "Color Scheme",
                        value = colorSchemeLabel(colorScheme),
                        onClick = { dialog = SettingsDialog.ColorScheme },
                    )
                    SettingsNavRow(
                        icon = Icons.Outlined.Grain,
                        iconTint = Color(0xFFE040FB),
                        title = "AR Particle Density",
                        value = density.replaceFirstChar { it.uppercase() },
                        onClick = { dialog = SettingsDialog.ParticleDensity },
                    )
                }
            }

            // ── NOTIFICATIONS ────────────────────────────────────────────
            SettingsSectionHeader("NOTIFICATIONS", Color(0xFF69F0AE))
            AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsToggleRow(
                        icon = Icons.Outlined.Notifications,
                        iconTint = Color(0xFFEF5350),
                        title = "Scan Alerts",
                        subtitle = "Get notified when weak spots are detected",
                        checked = scanAlerts,
                        onChecked = {
                            scanAlerts = it
                            UserPreferences.scanAlerts = it
                        },
                    )
                    SettingsToggleRow(
                        icon = Icons.Outlined.MailOutline,
                        iconTint = Color(0xFF42A5F5),
                        title = "Weekly Reports",
                        subtitle = "Receive weekly Wi‑Fi performance reports",
                        checked = weeklyReports,
                        onChecked = {
                            weeklyReports = it
                            UserPreferences.weeklyReports = it
                            if (it) {
                                Toast.makeText(
                                    context,
                                    "Weekly reports saved as preference (local only)",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }
            }

            // ── DATA & PRIVACY ───────────────────────────────────────────
            SettingsSectionHeader("DATA & PRIVACY", Color(0xFF69F0AE))
            AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsNavRow(
                        icon = Icons.Outlined.FileUpload,
                        iconTint = Color(0xFF26C6DA),
                        title = "Export Scan History",
                        value = "Save your scan data to a file",
                        onClick = {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    exportAllSessions(db, exporter)
                                }
                                statusMsg = if (ok) {
                                    "Export ready — pick an app to share"
                                } else {
                                    "Nothing to export yet. Map a session first."
                                }
                                Toast.makeText(context, statusMsg, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    SettingsNavRow(
                        icon = Icons.Outlined.DeleteForever,
                        iconTint = Color(0xFFEF5350),
                        title = "Clear Scan History",
                        value = "Remove all saved scan data",
                        onClick = { dialog = SettingsDialog.ClearHistory },
                    )
                    SettingsNavRow(
                        icon = Icons.Outlined.Policy,
                        iconTint = Color(0xFF5C6BC0),
                        title = "Privacy Policy",
                        value = "Learn how we protect your data",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://developers.google.com/ar/data-privacy"),
                                    ),
                                )
                            }
                        },
                    )
                }
            }

            // ── ABOUT ────────────────────────────────────────────────────
            SettingsSectionHeader("ABOUT", Color(0xFF69F0AE))
            AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
                SettingsNavRow(
                    icon = Icons.Outlined.Info,
                    iconTint = Color(0xFFAB47BC),
                    title = "Wi‑Fi AR Analyzer",
                    value = "Version 1.2.0 (WifiAR)",
                    onClick = {
                        Toast.makeText(
                            context,
                            "WifiAR · spatial Wi‑Fi survey for Android",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }

            // Advanced RSSI thresholds (kept for power users)
            SettingsSectionHeader("ADVANCED · RSSI THRESHOLDS", Color(0xFF78909C))
            AnalyzerCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Strong ≥ ${UserPreferences.rssiStrongDbm} · Fair ≥ ${UserPreferences.rssiMediumDbm} · Dead ≤ ${UserPreferences.rssiDeadDbm} dBm",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "Grid ${UserPreferences.gridCellSizeM} m · Path-loss ${if (UserPreferences.pathLossIndoor) "indoor" else "open"}",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        "Cloud Anchors: ${if (UserPreferences.cloudApiAcknowledged) "acknowledged" else "not set"}",
                        color = Color.White.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Row widgets ──────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String, color: Color) {
    Text(
        text = title,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon, iconTint)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF43A047),
                uncheckedTrackColor = Color(0xFF2A2F38),
            ),
        )
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon, iconTint)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(value, color = Color(0xFF69F0AE).copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
        )
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

// ── Dialogs ──────────────────────────────────────────────────────────────────

private sealed class SettingsDialog {
    data object ScanInterval : SettingsDialog()
    data object SignalUnits : SettingsDialog()
    data object ColorScheme : SettingsDialog()
    data object ParticleDensity : SettingsDialog()
    data object ClearHistory : SettingsDialog()
}

@Composable
private fun SettingsChoiceDialog(
    dialog: SettingsDialog,
    onDismiss: () -> Unit,
    onPick: (Any) -> Unit,
    onConfirmClear: () -> Unit,
) {
    when (dialog) {
        SettingsDialog.ClearHistory -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Clear scan history?") },
                text = {
                    Text("This permanently deletes all mapping sessions, RSSI samples, and speed tests on this device.")
                },
                confirmButton = {
                    TextButton(onClick = onConfirmClear) {
                        Text("Delete all", color = Color(0xFFEF5350))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                },
            )
        }
        SettingsDialog.ScanInterval -> {
            ChoiceDialog(
                title = "Scan Interval",
                options = listOf(
                    2 to "2 seconds",
                    5 to "5 seconds",
                    15 to "15 seconds",
                    30 to "30 seconds",
                    60 to "60 seconds",
                ),
                onDismiss = onDismiss,
                onPick = { onPick(it) },
            )
        }
        SettingsDialog.SignalUnits -> {
            ChoiceDialog(
                title = "Signal Units",
                options = listOf(
                    "dbm" to "dBm (decibel-milliwatts)",
                    "percent" to "% (relative)",
                ),
                onDismiss = onDismiss,
                onPick = { onPick(it) },
            )
        }
        SettingsDialog.ColorScheme -> {
            ChoiceDialog(
                title = "Color Scheme",
                options = listOf(
                    "default" to "Default (Green-Yellow-Purple)",
                    "thermal" to "Thermal (Green-Orange-Red)",
                    "mono" to "Mono (Blue scale)",
                ),
                onDismiss = onDismiss,
                onPick = { onPick(it) },
            )
        }
        SettingsDialog.ParticleDensity -> {
            ChoiceDialog(
                title = "AR Particle Density",
                options = listOf(
                    "low" to "Low (smoother)",
                    "medium" to "Medium",
                    "high" to "High (more balls)",
                ),
                onDismiss = onDismiss,
                onPick = { onPick(it) },
            )
        }
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Text(
                        text = label,
                        color = Color(0xFF69F0AE),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(value) }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun colorSchemeLabel(key: String): String = when (key) {
    "thermal" -> "Thermal (Green-Orange-Red)"
    "mono" -> "Mono (Blue scale)"
    else -> "Default (Green-Yellow-Purple)"
}

/** Exports the newest session that has RSSI samples (PNG + CSV share sheet). */
private suspend fun exportAllSessions(
    db: WifiArDatabase,
    exporter: HeatmapExporter,
): Boolean {
    return runCatching {
        val sessions = db.mappingSessionDao().getRecentSessions()
            .ifEmpty { db.mappingSessionDao().getResumableSessions("") }
        for (session in sessions) {
            val samples = db.rssiSampleDao().getAllForSessionOnce(session.sessionId)
            if (samples.isEmpty()) continue
            val speeds = db.speedTestDao().getAllForSessionOnce(session.sessionId)
            val files = exporter.exportSession(
                locationName = session.locationName,
                startTimeMs = session.startTimeMs,
                samples = samples,
                speedTests = speeds,
            )
            withContext(Dispatchers.Main) {
                exporter.shareBoth(files.pngUri, files.csvUri)
            }
            return@runCatching true
        }
        false
    }.getOrDefault(false)
}
