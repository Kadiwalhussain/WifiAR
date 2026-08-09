package com.wifiar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wifiar.app.ui.theme.GlassStroke
import com.wifiar.app.ui.theme.NeonMint

enum class AppTab {
    AR_SCAN,
    NETWORKS,
    SPEED,
    HISTORY,
    SETTINGS,
}

/**
 * Mock-style bottom navigation (Wi‑Fi AR Analyzer).
 * No AnimatedContent — AR tab must not be torn down mid-frame.
 */
@Composable
fun AppShell(
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.AR_SCAN) }

    Scaffold(
        modifier = modifier,
        containerColor = Color(0xFF0B0D12),
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(Color(0xF012151C))
                    .border(width = 0.5.dp, color = GlassStroke)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavIconItem(
                        selected = tab == AppTab.AR_SCAN,
                        icon = Icons.Outlined.Radar,
                        label = "AR Scan",
                        onClick = { tab = AppTab.AR_SCAN },
                    )
                    NavIconItem(
                        selected = tab == AppTab.NETWORKS,
                        icon = Icons.Outlined.Wifi,
                        label = "Networks",
                        onClick = { tab = AppTab.NETWORKS },
                    )
                    NavIconItem(
                        selected = tab == AppTab.SPEED,
                        icon = Icons.Outlined.Speed,
                        label = "Speed",
                        onClick = { tab = AppTab.SPEED },
                    )
                    NavIconItem(
                        selected = tab == AppTab.HISTORY,
                        icon = Icons.Outlined.History,
                        label = "History",
                        onClick = { tab = AppTab.HISTORY },
                    )
                    NavIconItem(
                        selected = tab == AppTab.SETTINGS,
                        icon = Icons.Outlined.Settings,
                        label = "Settings",
                        onClick = { tab = AppTab.SETTINGS },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            when (tab) {
                AppTab.AR_SCAN -> {
                    PermissionGate {
                        LiveMappingScreen(modifier = Modifier.fillMaxSize())
                    }
                }
                AppTab.NETWORKS -> {
                    PermissionGate {
                        ScannerDebugScreen(modifier = Modifier.fillMaxSize())
                    }
                }
                AppTab.SPEED -> {
                    PermissionGate {
                        // Same live map with speed panel focus (session + speed test).
                        LiveMappingScreen(modifier = Modifier.fillMaxSize())
                    }
                }
                AppTab.HISTORY -> SessionHistoryScreen(modifier = Modifier.fillMaxSize())
                AppTab.SETTINGS -> SettingsScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun RowScope.NavIconItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val tint = if (selected) NeonMint else Color.White.copy(alpha = 0.45f)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
