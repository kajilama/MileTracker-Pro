package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.UserProfile
import com.example.data.repository.MileageRepository
import com.example.data.service.DriveTrackingService
import com.example.data.service.TrackingDiagnosticLogger
import com.example.data.util.AppConstants
import com.example.ui.theme.ThemePresets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AboutAppSubmenu(
    userProfile: UserProfile,
    repository: MileageRepository,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val trips by repository.allTrips.collectAsState(initial = emptyList())
    val liveDriveState by DriveTrackingService.driveState.collectAsState()
    val diagnosticLogs by TrackingDiagnosticLogger.logs.collectAsState()
    val activePreset = remember(userProfile.themePreset) { ThemePresets.getPreset(userProfile.themePreset) }

    // Check battery optimization status
    val isBatteryOptimizationIgnored = remember {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else true
    }

    val versionName = BuildConfig.VERSION_NAME
    val versionCode = BuildConfig.VERSION_CODE
    val buildType = BuildConfig.BUILD_TYPE

    // Formatted diagnostic debug log string including the last 50 tracking events
    val debugLog = remember(userProfile, trips.size, liveDriveState.isActive, diagnosticLogs) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        val now = dateFormat.format(Date())
        val last50Events = diagnosticLogs.take(50)
        val eventsSection = if (last50Events.isEmpty()) {
            "No tracking events recorded in this session yet."
        } else {
            last50Events.joinToString("\n") { entry ->
                "[${entry.formattedTime}] [${entry.tag}] ${entry.message}"
            }
        }

        """
        =======================================================
        MileTracker Pro System Diagnostic & Telemetry Log
        =======================================================
        Generated: $now
        Application: MileTracker Pro
        Version: $versionName (Build $versionCode)
        Build Type: $buildType
        Package: ${context.packageName}

        [DEVICE & OS]
        Manufacturer: ${Build.MANUFACTURER}
        Device Model: ${Build.MODEL} (${Build.PRODUCT})
        Android Version: ${Build.VERSION.RELEASE} (API Level ${Build.VERSION.SDK_INT})
        Display Density: ${density.density} (Font Scale: ${density.fontScale})
        Battery Optimization Ignored: $isBatteryOptimizationIgnored

        [USER CONFIGURATION]
        Account Mode: ${if (userProfile.isSignedInWithGoogle) "Google Cloud (${userProfile.email})" else "Guest Offline Mode (Not Signed In)"}
        Distance Unit: ${userProfile.distanceUnit}
        Currency: ${userProfile.currencySymbol}
        IRS Business Rate: $${userProfile.irsBusinessRate}/mi
        Active Theme Preset: ${activePreset.name} (${activePreset.id})
        Auto Drive Detection: ${userProfile.autoDriveDetectionEnabled}
        Speed Trigger Threshold: ${userProfile.speedTriggerThresholdMph} mph
        Inactivity Stop Timeout: ${userProfile.inactivityTimeoutMinutes} mins
        Minutes per Dropoff Stop: ${userProfile.minutesPerDropoffStop} mins

        [DATABASE & STORAGE]
        Total Trips Logged: ${trips.size}
        Total Miles Logged: ${String.format(Locale.US, "%.1f", trips.sumOf { it.distanceMiles })} mi
        Total IRS Deductions: $${String.format(Locale.US, "%.2f", trips.sumOf { it.totalDeduction })}

        [TELEMETRY SERVICE STATUS]
        Drive In Progress: ${liveDriveState.isActive}
        Elapsed Drive Time: ${liveDriveState.elapsedSeconds}s
        Live Tracked Distance: ${String.format(Locale.US, "%.2f", liveDriveState.distanceMiles)} mi
        Live Speed: ${liveDriveState.currentSpeedMph} mph
        Dropoff Stops in Active Batch: ${liveDriveState.stops.size}

        [LAST 50 TRACKING EVENTS (${last50Events.size} entries)]
        $eventsSection
        =======================================================
        """.trimIndent()
    }

    fun copyDebugLogToClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("MileTracker Pro Debug Log", debugLog)
            clipboard.setPrimaryClip(clip)
            onShowMessage("Debug log copied to clipboard! Ready to paste.")
        } catch (e: Exception) {
            onShowMessage("Unable to copy debug log: ${e.message}")
        }
    }

    fun sendDebugLogsEmail() {
        try {
            val subject = "[MileTracker Pro Debug Logs] v$versionName (Build $versionCode) - ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
            val mailtoUri = Uri.parse("mailto:${AppConstants.SUPPORT_EMAIL}")
            val intent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(AppConstants.SUPPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, debugLog)
            }
            context.startActivity(Intent.createChooser(intent, "Send Debug Logs via..."))
        } catch (e: Exception) {
            copyDebugLogToClipboard()
            onShowMessage("No email client detected. Debug logs copied to clipboard for ${AppConstants.SUPPORT_EMAIL}.")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. APP HERO HEADER
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.primary, Color(0xFF10B981))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "MileTracker Pro Logo",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Text(
                    text = "MileTracker Pro",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Automated IRS Mileage Tracker & Telemetry",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Version & Build Number Badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VERSION $versionName • BUILD $versionCode",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }
            }
        }

        // 2. VERSION & BUILD DETAILS CARD
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Build & Release Information",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                AboutInfoRow("Application Name", "MileTracker Pro")
                AboutInfoRow("Release Version", versionName)
                AboutInfoRow("Build Number", "$versionCode")
                AboutInfoRow("Build Type", buildType.replaceFirstChar { it.uppercase() })
                AboutInfoRow("Support Contact", AppConstants.SUPPORT_EMAIL)
                AboutInfoRow("Active Theme", activePreset.name)
            }
        }

        // 3. DEVICE & TELEMETRY ENVIRONMENT CARD
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Device & Telemetry Environment",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                AboutInfoRow("Device Hardware", "${Build.MANUFACTURER} ${Build.MODEL}")
                AboutInfoRow("Android Version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                AboutInfoRow("Display Font Scale", "${String.format(Locale.US, "%.2f", density.fontScale)}x")
                AboutInfoRow("Auto Drive Detection", if (userProfile.autoDriveDetectionEnabled) "Active (> ${userProfile.speedTriggerThresholdMph} mph)" else "Manual")
                AboutInfoRow("Battery Optimization", if (isBatteryOptimizationIgnored) "Unrestricted (Safe)" else "Optimized")
                AboutInfoRow("Total Trips Recorded", "${trips.size} trips")
                AboutInfoRow("Tracking Events Buffered", "${diagnosticLogs.take(50).size} / 50")
            }
        }

        // 4. DIAGNOSTIC LOG VIEWER
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Diagnostic Log Preview (Last 50 Events)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = "Works Offline / Signed Out",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Monospace scrollable log snippet
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = debugLog,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // 5. SEND DEBUG LOGS / COPY LOG BUTTONS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { copyDebugLogToClipboard() },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("copy_debug_log_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Log", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { sendDebugLogsEmail() },
                modifier = Modifier
                    .weight(1.3f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("send_debug_logs_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Send Debug Logs", fontWeight = FontWeight.Bold)
            }
        }

        // Privacy Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Debug logs contain no personal passwords or Google credentials.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
