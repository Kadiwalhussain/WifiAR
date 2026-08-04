package com.wifiar.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wifiar.app.AppConfig
import com.wifiar.app.R
import com.wifiar.app.data.UserPreferences

/**
 * User-tunable thresholds and model presets (Part 10).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    var strong by remember { mutableIntStateOf(UserPreferences.rssiStrongDbm) }
    var medium by remember { mutableIntStateOf(UserPreferences.rssiMediumDbm) }
    var dead by remember { mutableIntStateOf(UserPreferences.rssiDeadDbm) }
    var indoor by remember { mutableStateOf(UserPreferences.pathLossIndoor) }
    var gridCell by remember { mutableFloatStateOf(UserPreferences.gridCellSizeM) }
    var cloudAck by remember { mutableStateOf(UserPreferences.cloudApiAcknowledged) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_rssi_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ThresholdSlider(
                label = stringResource(R.string.settings_strong, strong),
                value = strong.toFloat(),
                valueRange = -40f..-20f,
                onChange = {
                    strong = it.toInt()
                    UserPreferences.rssiStrongDbm = strong
                },
            )
            ThresholdSlider(
                label = stringResource(R.string.settings_medium, medium),
                value = medium.toFloat(),
                valueRange = -80f..-50f,
                onChange = {
                    medium = it.toInt()
                    UserPreferences.rssiMediumDbm = medium
                },
            )
            ThresholdSlider(
                label = stringResource(R.string.settings_dead, dead),
                value = dead.toFloat(),
                valueRange = -95f..-70f,
                onChange = {
                    dead = it.toInt()
                    UserPreferences.rssiDeadDbm = dead
                },
            )

            Text(
                text = stringResource(R.string.settings_path_loss_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = indoor,
                    onClick = {
                        indoor = true
                        UserPreferences.pathLossIndoor = true
                    },
                    label = {
                        Text(
                            stringResource(
                                R.string.settings_path_indoor,
                                AppConfig.PATH_LOSS_EXPONENT_INDOOR,
                            ),
                        )
                    },
                )
                FilterChip(
                    selected = !indoor,
                    onClick = {
                        indoor = false
                        UserPreferences.pathLossIndoor = false
                    },
                    label = {
                        Text(
                            stringResource(
                                R.string.settings_path_open,
                                AppConfig.PATH_LOSS_EXPONENT_OPEN,
                            ),
                        )
                    },
                )
            }
            Text(
                text = stringResource(R.string.settings_path_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )

            Text(
                text = stringResource(R.string.settings_grid_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ThresholdSlider(
                label = stringResource(R.string.settings_grid_cell, gridCell),
                value = gridCell,
                valueRange = 0.15f..1.0f,
                steps = 16,
                onChange = {
                    gridCell = (Math.round(it * 20) / 20f)
                    UserPreferences.gridCellSizeM = gridCell
                },
            )

            Text(
                text = stringResource(R.string.settings_cloud_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_cloud_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            FilterChip(
                selected = cloudAck,
                onClick = {
                    cloudAck = !cloudAck
                    UserPreferences.cloudApiAcknowledged = cloudAck
                },
                label = { Text(stringResource(R.string.settings_cloud_ack)) },
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_version, "1.0.0"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}
