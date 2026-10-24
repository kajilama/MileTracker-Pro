package com.example.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.service.DiagnosticLevel
import com.example.data.service.DiagnosticLogEntry
import com.example.data.service.DriveTrackingService
import com.example.data.service.TrackingDiagnosticLogger

@Composable
fun PermissionsHealthSubmenu(
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }

    fun checkPermission(perm: String): Boolean {
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    val fineLocation = remember(refreshTrigger) { checkPermission(Manifest.permission.ACCESS_FINE_LOCATION) }
    val coarseLocation = remember(refreshTrigger) { checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION) }
    val activityRecognition = remember(refreshTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkPermission(Manifest.permission.ACTIVITY_RECOGNITION)
        } else true
    }
    val backgroundLocation = remember(refreshTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else true
    }
    val notifications = remember(refreshTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else true
    }

    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }
    val isGpsEnabled = remember(refreshTrigger) {
        locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
    }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val isBatteryOptimized = remember(refreshTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else true
    }

    val isServiceRunning by DriveTrackingService.isServiceRunning.collectAsState()
    val motionState by DriveTrackingService.motionState.collectAsState()
    val liveDriveState by DriveTrackingService.driveState.collectAsState()
    val diagnosticLogs by TrackingDiagnosticLogger.logs.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshTrigger++
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshTrigger++
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Overall System Health Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (fineLocation && activityRecognition && isGpsEnabled) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (fineLocation && activityRecognition && isGpsEnabled) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
            ),
            modifier = Modifier.fillMaxWidth().testTag("tracking_health_summary_card")
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (fineLocation && activityRecognition && isGpsEnabled) Color(0xFF16A34A) else Color(0xFFDC2626),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (fineLocation && activityRecognition && isGpsEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (fineLocation && activityRecognition && isGpsEnabled) "Tracking Engine Ready" else "Attention Required",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (fineLocation && activityRecognition && isGpsEnabled) Color(0xFF166534) else Color(0xFF991B1B)
                    )
                    Text(
                        text = "Motion State: ${motionState.name} • Service: ${if (isServiceRunning) "Running" else "Idle"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { refreshTrigger++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // 2. Android System Permissions Checklist
        Text(
            text = "Android System Permissions",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PermissionStatusRow(
                    name = "Precise Location (GPS)",
                    description = "Required to calculate exact mileage and IRS routes",
                    isGranted = fineLocation,
                    onFix = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )

                HorizontalDivider()

                PermissionStatusRow(
                    name = "Physical Activity Recognition",
                    description = "Required to detect IN_VEHICLE and stop transitions",
                    isGranted = activityRecognition,
                    onFix = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            permissionLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
                        }
                    }
                )

                HorizontalDivider()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    PermissionStatusRow(
                        name = "Background Location (Always)",
                        description = "Required to track drives when screen is locked or using navigation",
                        isGranted = backgroundLocation,
                        onFix = {
                            if (fineLocation) {
                                backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            } else {
                                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                            }
                        }
                    )
                    HorizontalDivider()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionStatusRow(
                        name = "Post Notifications",
                        description = "Displays the persistent drive recording HUD & End Trip button",
                        isGranted = notifications,
                        onFix = {
                            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        }
                    )
                }
            }
        }

        // 3. Hardware & Background Execution Health
        Text(
            text = "Hardware & Background Services",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // GPS Provider Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isGpsEnabled) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                            contentDescription = null,
                            tint = if (isGpsEnabled) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("GPS Location Provider", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                text = if (isGpsEnabled) "Hardware GPS active and responding" else "GPS disabled in device settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isGpsEnabled) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                } catch (e: Exception) {
                                    onShowMessage("Could not open location settings")
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Turn On", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        BadgePill("Enabled", Color(0xFF10B981))
                    }
                }

                HorizontalDivider()

                // Battery Optimization Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = if (isBatteryOptimized) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Battery Optimization", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                text = if (isBatteryOptimized) "Unrestricted (Safe for long background drives)" else "Battery optimized (OS may sleep tracking)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isBatteryOptimized) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    onShowMessage("Open device Battery Settings to disable optimization for MileTracker Pro")
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Unrestrict", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        BadgePill("Unrestricted", Color(0xFF10B981))
                    }
                }

                HorizontalDivider()

                // Tracking Service Lifecycle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Foreground Tracking Service", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            text = if (isServiceRunning) "Active in background with location notification" else "Stopped (Will start on motion or manual drive)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            DriveTrackingService.startService(context, manual = false)
                            onShowMessage("DriveTrackingService restarted!")
                            refreshTrigger++
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ) {
                        Text("Restart", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        // 4. Interactive Telemetry Simulator / Test Buttons
        Text(
            text = "Telemetry & Motion Testing",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Verify motion trigger and stationary stop debounce logic directly without driving:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            DriveTrackingService.simulateMotion(context, 25)
                            onShowMessage("Simulated 25 mph vehicular motion! Check state machine response below.")
                        },
                        modifier = Modifier.weight(1f).testTag("simulate_speed_25_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulate 25 mph", style = MaterialTheme.typography.labelSmall)
                    }

                    Button(
                        onClick = {
                            DriveTrackingService.simulateMotion(context, 0)
                            onShowMessage("Simulated 0 mph stationary stop! Check possible stop debounce below.")
                        },
                        modifier = Modifier.weight(1f).testTag("simulate_speed_0_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulate 0 mph", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            onShowMessage("Could not open App Settings")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Android App Settings (Permissions & Battery)", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 5. Live Telemetry & Diagnostic Event Logs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Telemetry Diagnostics (${diagnosticLogs.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            IconButton(onClick = { TrackingDiagnosticLogger.clear() }) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 280.dp)
        ) {
            if (diagnosticLogs.isEmpty()) {
                Box(
                    modifier = Modifier.padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No diagnostic events logged yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items = diagnosticLogs, key = { it.id }) { log ->
                        val color = when (log.level) {
                            DiagnosticLevel.INFO -> Color(0xFF94A3B8)
                            DiagnosticLevel.SUCCESS -> Color(0xFF4ADE80)
                            DiagnosticLevel.WARNING -> Color(0xFFFBBF24)
                            DiagnosticLevel.ERROR -> Color(0xFFF87171)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text(
                                text = log.formattedTime,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.width(60.dp)
                            )
                            Text(
                                text = "[${log.tag}]",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = color
                                ),
                                modifier = Modifier.width(90.dp)
                            )
                            Text(
                                text = log.message,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionStatusRow(
    name: String,
    description: String,
    isGranted: Boolean,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.width(10.dp))

        if (isGranted) {
            BadgePill("Granted", Color(0xFF10B981))
        } else {
            Button(
                onClick = onFix,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Enable", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun BadgePill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
        }
    }
}
