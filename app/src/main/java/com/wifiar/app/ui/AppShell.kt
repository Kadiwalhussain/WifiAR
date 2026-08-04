package com.wifiar.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wifiar.app.R
import com.wifiar.app.ui.auth.AuthScreen
import com.wifiar.app.ui.theme.GlassStroke
import com.wifiar.app.ui.theme.NeonCyan

enum class AppTab {
    LIVE_MAPPING,
    HISTORY,
    ACCOUNT,
    SETTINGS,
    WIFI_DEBUG,
}

/**
 * Compact bottom nav shell with icon tabs and animated content.
 */
@Composable
fun AppShell(
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.LIVE_MAPPING) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                        .border(1.dp, GlassStroke, RoundedCornerShape(18.dp))
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavIconItem(
                        selected = tab == AppTab.LIVE_MAPPING,
                        iconRes = R.drawable.ic_nav_map,
                        label = stringResource(R.string.tab_map),
                        onClick = { tab = AppTab.LIVE_MAPPING },
                    )
                    NavIconItem(
                        selected = tab == AppTab.HISTORY,
                        iconRes = R.drawable.ic_nav_history,
                        label = stringResource(R.string.tab_history),
                        onClick = { tab = AppTab.HISTORY },
                    )
                    NavIconItem(
                        selected = tab == AppTab.ACCOUNT,
                        iconRes = R.drawable.ic_nav_account,
                        label = stringResource(R.string.tab_account),
                        onClick = { tab = AppTab.ACCOUNT },
                    )
                    NavIconItem(
                        selected = tab == AppTab.SETTINGS,
                        iconRes = R.drawable.ic_nav_settings,
                        label = stringResource(R.string.tab_settings),
                        onClick = { tab = AppTab.SETTINGS },
                    )
                    NavIconItem(
                        selected = tab == AppTab.WIFI_DEBUG,
                        iconRes = R.drawable.ic_nav_wifi,
                        label = stringResource(R.string.tab_wifi),
                        onClick = { tab = AppTab.WIFI_DEBUG },
                    )
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                (slideInHorizontally { it / 12 * dir } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 12 * dir } + fadeOut())
            },
            label = "tabContent",
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) { current ->
            when (current) {
                AppTab.LIVE_MAPPING -> {
                    PermissionGate {
                        LiveMappingScreen(modifier = Modifier.fillMaxSize())
                    }
                }
                AppTab.HISTORY -> SessionHistoryScreen(modifier = Modifier.fillMaxSize())
                AppTab.ACCOUNT -> AuthScreen(modifier = Modifier.fillMaxSize())
                AppTab.SETTINGS -> SettingsScreen(modifier = Modifier.fillMaxSize())
                AppTab.WIFI_DEBUG -> {
                    PermissionGate {
                        ScannerDebugScreen(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavIconItem(
    selected: Boolean,
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    val tint = if (selected) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) NeonCyan.copy(alpha = 0.14f) else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
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
