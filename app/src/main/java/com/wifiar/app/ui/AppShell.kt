package com.wifiar.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wifiar.app.R
import com.wifiar.app.ui.auth.AuthScreen

enum class AppTab {
    LIVE_MAPPING,
    HISTORY,
    ACCOUNT,
    SETTINGS,
    WIFI_DEBUG,
}

/**
 * App shell with Map, History, Account, Settings, and WiFi debug.
 */
@Composable
fun AppShell(
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.LIVE_MAPPING) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == AppTab.LIVE_MAPPING,
                    onClick = { tab = AppTab.LIVE_MAPPING },
                    icon = { Text("Map") },
                    label = { Text(stringResource(R.string.tab_map)) },
                )
                NavigationBarItem(
                    selected = tab == AppTab.HISTORY,
                    onClick = { tab = AppTab.HISTORY },
                    icon = { Text("Hist") },
                    label = { Text(stringResource(R.string.tab_history)) },
                )
                NavigationBarItem(
                    selected = tab == AppTab.ACCOUNT,
                    onClick = { tab = AppTab.ACCOUNT },
                    icon = { Text("Acc") },
                    label = { Text(stringResource(R.string.tab_account)) },
                )
                NavigationBarItem(
                    selected = tab == AppTab.SETTINGS,
                    onClick = { tab = AppTab.SETTINGS },
                    icon = { Text("Set") },
                    label = { Text(stringResource(R.string.tab_settings)) },
                )
                NavigationBarItem(
                    selected = tab == AppTab.WIFI_DEBUG,
                    onClick = { tab = AppTab.WIFI_DEBUG },
                    icon = { Text("WiFi") },
                    label = { Text(stringResource(R.string.tab_wifi)) },
                )
            }
        },
    ) { innerPadding ->
        when (tab) {
            AppTab.LIVE_MAPPING -> {
                PermissionGate {
                    LiveMappingScreen(
                        modifier = Modifier.padding(
                            bottom = innerPadding.calculateBottomPadding(),
                        ),
                    )
                }
            }
            AppTab.HISTORY -> {
                SessionHistoryScreen(
                    modifier = Modifier.padding(innerPadding),
                )
            }
            AppTab.ACCOUNT -> {
                AuthScreen(
                    modifier = Modifier.padding(innerPadding),
                )
            }
            AppTab.SETTINGS -> {
                SettingsScreen(
                    modifier = Modifier.padding(innerPadding),
                )
            }
            AppTab.WIFI_DEBUG -> {
                PermissionGate {
                    ScannerDebugScreen(
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
