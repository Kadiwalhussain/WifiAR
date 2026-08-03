package com.wifiar.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiar.app.R
import com.wifiar.app.scanner.RssiSample
import com.wifiar.app.scanner.WifiScanner
import com.wifiar.app.ui.theme.RssiMedium
import com.wifiar.app.ui.theme.RssiStrong
import com.wifiar.app.ui.theme.RssiWeak

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerDebugScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember {
        WifiScanner(context = context, scope = scope)
    }

    DisposableEffect(scanner) {
        scanner.start()
        onDispose { scanner.stop() }
    }

    val results by scanner.scanResults.collectAsStateWithLifecycle()
    val isScanning by scanner.isScanning.collectAsStateWithLifecycle()
    val cooldownSeconds by scanner.cooldownSeconds.collectAsStateWithLifecycle()
    val lastError by scanner.lastError.collectAsStateWithLifecycle()

    val canScan = cooldownSeconds == 0L && !isScanning

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("WiFiAR · Scanner Debug") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { scanner.triggerScan() },
                enabled = canScan,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (isScanning) {
                        stringResource(R.string.scanning)
                    } else {
                        stringResource(R.string.scan_now)
                    },
                )
            }

            if (cooldownSeconds > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.cooldown_format, cooldownSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            when (lastError) {
                WifiScanner.ERROR_WIFI_DISABLED -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.wifi_disabled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                WifiScanner.ERROR_LOCATION_DISABLED -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.location_disabled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                WifiScanner.ERROR_SCAN_FAILED -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.scan_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (isScanning) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (results.isEmpty()) {
                    stringResource(R.string.no_results)
                } else {
                    stringResource(R.string.networks_found, results.size)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Column headers
            if (results.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    HeaderCell("SSID", Modifier.weight(1.4f))
                    HeaderCell("BSSID", Modifier.weight(1.3f))
                    HeaderCell("RSSI", Modifier.weight(0.7f))
                    HeaderCell("MHz", Modifier.weight(0.6f))
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = results,
                    key = { "${it.bssid}|${it.ssid}" },
                ) { sample ->
                    ScanResultRow(sample = sample)
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        maxLines = 1,
    )
}

@Composable
private fun ScanResultRow(
    sample: RssiSample,
) {
    val rssiColor = when {
        sample.rssiDbm >= -55 -> RssiStrong
        sample.rssiDbm >= -70 -> RssiMedium
        else -> RssiWeak
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1.4f)) {
                Text(
                    text = sample.ssid.ifBlank { "<hidden>" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = sample.bssid,
                modifier = Modifier.weight(1.3f),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .weight(0.7f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(rssiColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${sample.rssiDbm}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = rssiColor,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${sample.frequencyMhz}",
                modifier = Modifier.weight(0.6f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}
