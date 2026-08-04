package com.wifiar.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wifiar.app.R
import com.wifiar.app.ui.auth.AuthScreen
import com.wifiar.app.ui.components.NavGlyph
import com.wifiar.app.ui.theme.GlassStroke

enum class AppTab {
    LIVE_MAPPING,
    HISTORY,
    ACCOUNT,
    SETTINGS,
    WIFI_DEBUG,
}

/**
 * Compact bottom nav shell with animated tab transitions.
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
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavGlyph(
                        selected = tab == AppTab.LIVE_MAPPING,
                        glyph = "◈",
                        label = stringResource(R.string.tab_map),
                        onClick = { tab = AppTab.LIVE_MAPPING },
                    )
                    NavGlyph(
                        selected = tab == AppTab.HISTORY,
                        glyph = "☰",
                        label = stringResource(R.string.tab_history),
                        onClick = { tab = AppTab.HISTORY },
                    )
                    NavGlyph(
                        selected = tab == AppTab.ACCOUNT,
                        glyph = "◎",
                        label = stringResource(R.string.tab_account),
                        onClick = { tab = AppTab.ACCOUNT },
                    )
                    NavGlyph(
                        selected = tab == AppTab.SETTINGS,
                        glyph = "⚙",
                        label = stringResource(R.string.tab_settings),
                        onClick = { tab = AppTab.SETTINGS },
                    )
                    NavGlyph(
                        selected = tab == AppTab.WIFI_DEBUG,
                        glyph = "≋",
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
