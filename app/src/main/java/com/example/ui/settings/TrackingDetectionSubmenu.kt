package com.example.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile
import com.example.data.repository.MileageRepository
import com.example.data.util.AppConstants

@Composable
fun TrackingDetectionSubmenu(
    userProfile: UserProfile,
    repository: MileageRepository,
    onShowMessage: (String) -> Unit
) {
    var autoStart by remember { mutableStateOf(userProfile.autoStartDriveGps) }
    var autoStop by remember { mutableStateOf(userProfile.autoStopDriveGps) }
    var timeoutMinutes by remember { mutableFloatStateOf(userProfile.inactivityTimeoutMinutes.toFloat()) }
    var speedThreshold by remember { mutableFloatStateOf(userProfile.speedTriggerThresholdMph.toFloat().coerceAtLeast(5f)) }
    var minutesPerDropoffStop by remember(userProfile.minutesPerDropoffStop) {
        mutableIntStateOf(userProfile.minutesPerDropoffStop.coerceIn(0, 15))
    }
    var minutesPerStopText by remember(minutesPerDropoffStop) {
        mutableStateOf(minutesPerDropoffStop.toString())
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // CONFIGURABLE MINUTES PER DROPOFF STOP CARD
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Minutes per Dropoff Stop",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Persisted in DataStore (0–15 min, default ${AppConstants.DEFAULT_MINUTES_PER_STOP} min)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "Used whenever route timing is automatically calculated:\n" +
                        "• Total Route Duration = driving time + (stops × minutes per stop)\n" +
                        "• Start Time = End Time − Total Route Duration",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Minutes per dropoff stop",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Range: 0 to 15 minutes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val updated = (minutesPerDropoffStop - 1).coerceIn(0, 15)
                                    minutesPerDropoffStop = updated
                                    minutesPerStopText = updated.toString()
                                    repository.preferencesManager.setMinutesPerDropoffStop(updated)
                                },
                                enabled = minutesPerDropoffStop > 0,
                                modifier = Modifier.testTag("minutes_per_stop_decrement")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease minutes per stop")
                            }

                            OutlinedTextField(
                                value = minutesPerStopText,
                                onValueChange = { raw ->
                                    minutesPerStopText = raw
                                    val parsed = raw.toIntOrNull()
                                    if (parsed != null) {
                                        val clamped = parsed.coerceIn(0, 15)
                                        minutesPerDropoffStop = clamped
                                        repository.preferencesManager.setMinutesPerDropoffStop(clamped)
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .width(72.dp)
                                    .testTag("minutes_per_stop_input")
                            )

                            IconButton(
                                onClick = {
                                    val updated = (minutesPerDropoffStop + 1).coerceIn(0, 15)
                                    minutesPerDropoffStop = updated
                                    minutesPerStopText = updated.toString()
                                    repository.preferencesManager.setMinutesPerDropoffStop(updated)
                                },
                                enabled = minutesPerDropoffStop < 15,
                                modifier = Modifier.testTag("minutes_per_stop_increment")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase minutes per stop")
                            }
                        }
                    }
                }

                Slider(
                    value = minutesPerDropoffStop.toFloat(),
                    onValueChange = {
                        val updated = it.toInt().coerceIn(0, 15)
                        minutesPerDropoffStop = updated
                        minutesPerStopText = updated.toString()
                        repository.preferencesManager.setMinutesPerDropoffStop(updated)
                    },
                    valueRange = 0f..15f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0, 2, AppConstants.DEFAULT_MINUTES_PER_STOP, 6, 10, 15).forEach { mins ->
                        FilterChip(
                            selected = minutesPerDropoffStop == mins,
                            onClick = {
                                minutesPerDropoffStop = mins
                                minutesPerStopText = mins.toString()
                                repository.preferencesManager.setMinutesPerDropoffStop(mins)
                                onShowMessage("Minutes per dropoff stop set to $mins min")
                            },
                            label = {
                                Text(
                                    if (mins == AppConstants.DEFAULT_MINUTES_PER_STOP) {
                                        "${mins}m (Default)"
                                    } else {
                                        "${mins}m"
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "GPS Telemetry & Drive Detection",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Configure automated trip logging using local Android hardware sensors with zero external API fees.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // Auto Start Drive Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Start Drive via GPS Telemetry", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Automatically starts recording when movement exceeds speed threshold", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoStart,
                        onCheckedChange = { autoStart = it }
                    )
                }

                HorizontalDivider()

                // Auto Stop Drive Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Stop Drive via GPS Telemetry", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Automatically finalizes and logs trip when vehicle remains stationary", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoStop,
                        onCheckedChange = { autoStop = it }
                    )
                }

                HorizontalDivider()

                // Speed Trigger Threshold Slider (Starts at 5 mph, default 10 mph)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Speed Trigger Threshold", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            "${speedThreshold.toInt()} mph (Default: 10 mph)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Text(
                        "Starts tracking only when driving faster than this speed. Eliminates false triggers from walking or cycling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = speedThreshold,
                        onValueChange = { speedThreshold = it },
                        valueRange = 5f..35f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Quick Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5, 10, 15, 20).forEach { speed ->
                            FilterChip(
                                selected = speedThreshold.toInt() == speed,
                                onClick = { speedThreshold = speed.toFloat() },
                                label = { Text(if (speed == 10) "10 mph (Default)" else "$speed mph") }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Inactivity Timeout Slider (1 to 15 minutes, default 3 minutes)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Inactivity Timeout", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            "${timeoutMinutes.toInt()} minutes (Default: 3m)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Text(
                        "Duration stationary before trip is automatically concluded. Traffic lights and brief delivery stops do not end the drive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = timeoutMinutes,
                        onValueChange = { timeoutMinutes = it },
                        valueRange = 1f..15f,
                        steps = 13,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Quick Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1, 3, 5, 10).forEach { mins ->
                            FilterChip(
                                selected = timeoutMinutes.toInt() == mins,
                                onClick = { timeoutMinutes = mins.toFloat() },
                                label = { Text(if (mins == 3) "3m (Default)" else "${mins}m") }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            speedThreshold = 10f
                            timeoutMinutes = 3f
                            minutesPerDropoffStop = AppConstants.DEFAULT_MINUTES_PER_STOP
                            minutesPerStopText = AppConstants.DEFAULT_MINUTES_PER_STOP.toString()
                            repository.preferencesManager.setTrackingParameters(
                                autoStart = autoStart,
                                autoStop = autoStop,
                                speedThresholdMph = 10,
                                timeoutMinutes = 3,
                                minutesPerDropoffStop = AppConstants.DEFAULT_MINUTES_PER_STOP
                            )
                            onShowMessage("Reset to Defaults: 10 mph trigger, 3 min timeout, ${AppConstants.DEFAULT_MINUTES_PER_STOP} min/stop")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reset Defaults")
                    }

                    Button(
                        onClick = {
                            repository.preferencesManager.setTrackingParameters(
                                autoStart = autoStart,
                                autoStop = autoStop,
                                speedThresholdMph = speedThreshold.toInt(),
                                timeoutMinutes = timeoutMinutes.toInt(),
                                minutesPerDropoffStop = minutesPerDropoffStop
                            )
                            onShowMessage("Saved: ${speedThreshold.toInt()} mph trigger, ${timeoutMinutes.toInt()}m timeout, ${minutesPerDropoffStop}m/stop")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Settings")
                    }
                }
            }
        }
    }
}
