package com.example.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CustomTripType
import com.example.data.model.Trip
import com.example.data.model.TripStop
import com.example.data.repository.DrivingRouteService
import com.example.data.util.AppConstants
import com.example.ui.map.TripStaticMapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripItemCard(
    trip: Trip,
    tripTypes: List<CustomTripType> = emptyList(),
    defaultMinutesPerStop: Int = AppConstants.DEFAULT_MINUTES_PER_STOP,
    distanceUnit: String = "Miles",
    currencySymbol: String = "$",
    onEdit: (Trip) -> Unit,
    onDelete: (Trip) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDetailMapDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val matchedTripType = remember(trip.tripTypeId, tripTypes) {
        trip.tripTypeId?.let { id -> tripTypes.find { it.id == id } }
    }
    val tripTypeLabel = matchedTripType?.name ?: "Unclassified"

    val badgeColor = remember(matchedTripType) {
        if (matchedTripType != null) {
            try {
                Color(android.graphics.Color.parseColor(matchedTripType.colorHex))
            } catch (_: Exception) {
                Color(0xFF0D9488)
            }
        } else {
            Color(0xFF64748B)
        }
    }

    if (showDetailMapDialog) {
        TripDetailMapDialog(
            trip = trip,
            tripTypeLabel = tripTypeLabel,
            defaultMinutesPerStop = defaultMinutesPerStop,
            distanceUnit = distanceUnit,
            currencySymbol = currencySymbol,
            onDismiss = { showDetailMapDialog = false },
            onEdit = onEdit,
            onDelete = onDelete
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Trip?") },
            text = { Text("Are you sure you want to delete '${trip.title.ifBlank { "this trip" }}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete(trip)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val uppercaseDateFormat = remember { SimpleDateFormat("EEE. MMM. d, yyyy", Locale.US) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateHeaderStr = uppercaseDateFormat.format(Date(trip.dateTimestamp)).uppercase()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trip_card_${trip.id}")
            .clickable { showDetailMapDialog = true },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {

            // 1. HEADER: Uppercase Date & Unified Trip Type Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateHeaderStr,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = tripTypeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. PROMINENT STATS ROW: Mileage & IRS Tax Deductions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "%.1f %s".format(trip.distanceMiles, if (distanceUnit == "Miles") "miles" else "km"),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (trip.drivingMinutes > 0 || trip.totalDurationMinutes > 0) {
                        Text(
                            text = "${trip.totalDurationMinutes} mins total • ${trip.stopsCount} customer stops",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (trip.totalDeduction > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "+%s%.2f".format(currencySymbol, trip.totalDeduction),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF059669),
                                fontSize = 20.sp
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "IRS deduction",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF059669),
                                fontSize = 10.sp
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. LIGHTWEIGHT MAP SNAPSHOT PREVIEW
            val perStopMins = if (trip.stopsCount > 0) trip.dropoffMinutes / trip.stopsCount else defaultMinutesPerStop
            val roadPoints = remember(trip, perStopMins) {
                val pts = trip.getRoadPoints()
                if (pts.size >= 2) {
                    pts
                } else {
                    val sLat = trip.startLat ?: 35.3857
                    val sLng = trip.startLng ?: -119.1026
                    val eLat = trip.endLat ?: (sLat + 0.02)
                    val eLng = trip.endLng ?: (sLng + 0.025)

                    val stopsList = if (trip.stopsSummary.isNotBlank()) {
                        trip.stopsSummary.split(" | ").mapIndexed { idx, addr ->
                            val angle = (idx * 1.8) + 0.5
                            val dist = 0.014 + (idx * 0.007)
                            TripStop(
                                id = "stop_$idx",
                                address = addr,
                                lat = sLat + dist * kotlin.math.cos(angle),
                                lng = sLng + dist * kotlin.math.sin(angle),
                                dropoffMinutes = perStopMins
                            )
                        }
                    } else if (trip.isRoundTrip) {
                        listOf(
                            TripStop(id = "s1", address = "Stop 1", lat = sLat + 0.014, lng = sLng + 0.016, dropoffMinutes = perStopMins),
                            TripStop(id = "s2", address = "Stop 2", lat = sLat + 0.020, lng = sLng - 0.010, dropoffMinutes = perStopMins),
                            TripStop(id = "s3", address = "Stop 3", lat = sLat + 0.006, lng = sLng - 0.020, dropoffMinutes = perStopMins)
                        )
                    } else emptyList()

                    DrivingRouteService.generateRealisticGridRoute(
                        startLat = sLat,
                        startLng = sLng,
                        stops = stopsList,
                        endLat = if (trip.isRoundTrip) sLat else eLat,
                        endLng = if (trip.isRoundTrip) sLng else eLng,
                        isRoundTrip = trip.isRoundTrip
                    ).roadPoints
                }
            }

            TripStaticMapView(
                roadPoints = roadPoints,
                isRoundTrip = trip.isRoundTrip,
                stopsCount = trip.stopsCount,
                accentColor = Color(0xFF2563EB),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clickable { showDetailMapDialog = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. CONNECTED ADDRESS TIMELINE
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(18.dp)) {
                        Box(modifier = Modifier.size(10.dp).background(Color(0xFF10B981), CircleShape))
                        Box(modifier = Modifier.width(2.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = trip.startAddress.substringBefore(","),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1
                            )
                            if (trip.startTimeMillis > 0) {
                                Text(
                                    text = timeFormat.format(Date(trip.startTimeMillis)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = trip.startAddress.removePrefix(trip.startAddress.substringBefore(",")).removePrefix(", ").trim().ifBlank { "Origin Location" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                if (trip.stopsSummary.isNotBlank()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(18.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFFF59E0B), CircleShape))
                            Box(modifier = Modifier.width(2.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${trip.stopsCount} Customer Dropoff Stops (+${trip.dropoffMinutes} min)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                            )
                            Text(
                                text = trip.stopsSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.Top) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(18.dp)) {
                        Box(
                            modifier = Modifier.size(10.dp).background(
                                if (trip.isRoundTrip) Color(0xFF10B981) else Color(0xFFEF4444),
                                CircleShape
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = if (trip.isRoundTrip) "Return to: ${trip.startAddress.substringBefore(",")}" else trip.endAddress.substringBefore(","),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1
                            )
                            if (trip.endTimeMillis > 0) {
                                Text(
                                    text = timeFormat.format(Date(trip.endTimeMillis)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = if (trip.isRoundTrip) "Round-trip loop completed" else trip.endAddress.removePrefix(trip.endAddress.substringBefore(",")).removePrefix(", ").trim().ifBlank { "Destination Location" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // 5. ACTION ICONS ROW: Trip Type, Notes, Route View, Edit, Delete
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onEdit(trip) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.LocalOffer, contentDescription = "Trip Type", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(onClick = { onEdit(trip) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Notes", modifier = Modifier.size(18.dp), tint = if (trip.notes.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    IconButton(onClick = { showDetailMapDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Map, contentDescription = "View Map", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onEdit(trip) }, modifier = Modifier.size(36.dp).testTag("edit_trip_${trip.id}")) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Trip", modifier = Modifier.size(18.dp))
                    }

                    IconButton(onClick = { showDeleteConfirmDialog = true }, modifier = Modifier.size(36.dp).testTag("delete_trip_${trip.id}")) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Trip", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
