package com.example.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Trip
import com.example.data.model.UserProfile
import com.example.data.model.Vehicle
import com.example.data.repository.MileageRepository
import com.example.data.service.DriveTrackingService
import com.example.data.util.IrsPdfReportGenerator
import com.example.ui.components.GoogleDriveSyncCard
import com.example.ui.trips.TripAddEditDialog
import com.example.ui.trips.TripItemCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    trips: List<Trip>,
    vehicles: List<Vehicle>,
    userProfile: UserProfile,
    repository: MileageRepository,
    onNavigateToSettings: () -> Unit,
    onNavigateToWelcome: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val tripTypes by repository.allTripTypes.collectAsState(initial = emptyList())
    val tripTypesById = remember(tripTypes) { tripTypes.associateBy { it.id } }

    var showTripDialog by remember { mutableStateOf(false) }
    var tripToEdit by remember { mutableStateOf<Trip?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTripTypeId by remember { mutableStateOf<String?>(null) }
    var selectedPeriod by remember { mutableStateOf(OverviewPeriod.MONTH) }

    val liveDriveState by DriveTrackingService.driveState.collectAsState()
    val liveMotionState by DriveTrackingService.motionState.collectAsState()

    val now = System.currentTimeMillis()
    val periodStartTimestamp = remember(selectedPeriod, now) {
        val oneDay = 86400000L
        when (selectedPeriod) {
            OverviewPeriod.WEEK -> now - (oneDay * 7)
            OverviewPeriod.MONTH -> now - (oneDay * 30)
            OverviewPeriod.YEAR -> now - (oneDay * 365)
            OverviewPeriod.ALL -> 0L
        }
    }

    val periodTrips = remember(trips, periodStartTimestamp) {
        if (periodStartTimestamp == 0L) trips else trips.filter { it.dateTimestamp >= periodStartTimestamp }
    }

    val filteredTrips = remember(periodTrips, searchQuery, selectedFilterTripTypeId, tripTypesById) {
        periodTrips.filter { trip ->
            val resolvedTypeId = IrsPdfReportGenerator.resolveTripTypeId(trip, tripTypesById)
            val resolvedTypeName = IrsPdfReportGenerator.resolveTripTypeName(trip, tripTypesById)
            val matchesTripType = selectedFilterTripTypeId == null || resolvedTypeId == selectedFilterTripTypeId
            val matchesQuery = searchQuery.isBlank() ||
                    trip.title.contains(searchQuery, ignoreCase = true) ||
                    trip.startAddress.contains(searchQuery, ignoreCase = true) ||
                    trip.endAddress.contains(searchQuery, ignoreCase = true) ||
                    trip.notes.contains(searchQuery, ignoreCase = true) ||
                    resolvedTypeName.contains(searchQuery, ignoreCase = true)
            matchesTripType && matchesQuery
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsCar,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "MileTracker Pro",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                if (!userProfile.isSignedInWithGoogle) {
                                    Text(
                                        text = "Guest Mode",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (userProfile.isSignedInWithGoogle) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Drive Synced",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF4285F4).copy(alpha = 0.15f),
                                modifier = Modifier
                                    .clickable { onSignInWithGoogle() }
                                    .padding(end = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Login, contentDescription = null, tint = Color(0xFF4285F4), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Sign in",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF4285F4),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.testTag("open_settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                if (!liveDriveState.isActive) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            tripToEdit = null
                            showTripDialog = true
                        },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("Log Trip") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_trip_fab")
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. AUTOMATIC DRIVE DETECTION & MANUAL START/END DRIVE CONTROLLER
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (liveDriveState.isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                        ),
                        border = if (liveDriveState.isActive) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth().testTag("drive_detection_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (liveDriveState.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.DirectionsCar,
                                                contentDescription = null,
                                                tint = if (liveDriveState.isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (liveDriveState.isActive) "Drive in Progress" else "Drive Tracking & Detection",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = if (liveDriveState.isActive) "Motion: ${liveMotionState.name}" else if (userProfile.autoDriveDetectionEnabled) "Auto-detection: Active (> ${userProfile.speedTriggerThresholdMph} mph)" else "Manual Recording Mode",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (liveDriveState.isActive || userProfile.autoDriveDetectionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Auto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = userProfile.autoDriveDetectionEnabled,
                                        onCheckedChange = {
                                            repository.preferencesManager.setAutoDriveDetection(it)
                                            if (it) {
                                                DriveTrackingService.startService(context, manual = false)
                                            }
                                            val status = if (it) "Automatic Drive Detection enabled! Speed > ${userProfile.speedTriggerThresholdMph} mph will auto-log." else "Manual Drive mode active."
                                            coroutineScope.launch { snackbarHostState.showSnackbar(status) }
                                        },
                                        modifier = Modifier.testTag("auto_drive_switch")
                                    )
                                }
                            }

                            if (liveDriveState.isActive) {
                                val mins = liveDriveState.elapsedSeconds / 60
                                val secs = liveDriveState.elapsedSeconds % 60
                                val timeStr = "%02d:%02d".format(mins, secs)

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("RECORDING GPS ROUTE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error))
                                            }
                                            Text("$timeStr elapsed", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("Distance Tracked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("%.1f mi".format(liveDriveState.distanceMiles), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                                            }

                                            Column {
                                                Text("Speed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("${liveDriveState.currentSpeedMph} mph", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Dropoff Stops", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("${liveDriveState.stops.size} stops", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary))
                                            }
                                        }
                                    }
                                }

                                val density = androidx.compose.ui.platform.LocalDensity.current
                                if (density.fontScale > 1.15f) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                DriveTrackingService.addDropoffStop(context)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Added customer dropoff stop #${liveDriveState.stops.size + 1} (+${userProfile.minutesPerDropoffStop} min)")
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 44.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("+ Dropoff Stop", style = MaterialTheme.typography.labelMedium)
                                        }

                                        Button(
                                            onClick = {
                                                DriveTrackingService.stopDrive(context)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Drive ended and saved successfully!")
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 44.dp)
                                                .testTag("end_drive_button"),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("End Drive", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                } else {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                DriveTrackingService.addDropoffStop(context)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Added customer dropoff stop #${liveDriveState.stops.size + 1} (+${userProfile.minutesPerDropoffStop} min)")
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 44.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("+ Dropoff Stop", style = MaterialTheme.typography.labelMedium)
                                        }

                                        Button(
                                            onClick = {
                                                DriveTrackingService.stopDrive(context)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Drive ended and saved successfully!")
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 44.dp)
                                                .testTag("end_drive_button"),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("End Drive", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            } else {
                                val density = androidx.compose.ui.platform.LocalDensity.current
                                if (density.fontScale > 1.15f) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Ready to drive? Start recording mileage, GPS route, and customer delivery dropoffs in real time.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Button(
                                            onClick = {
                                                DriveTrackingService.startService(
                                                    context,
                                                    manual = true,
                                                    tripTypeName = userProfile.defaultTripTypeName,
                                                    tripTypeId = userProfile.defaultTripTypeId
                                                )
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Drive started! GPS tracking active.")
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("start_drive_button")
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Start Drive", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Ready to drive? Start recording mileage, GPS route, and customer delivery dropoffs in real time.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Button(
                                            onClick = {
                                                DriveTrackingService.startService(
                                                    context,
                                                    manual = true,
                                                    tripTypeName = userProfile.defaultTripTypeName,
                                                    tripTypeId = userProfile.defaultTripTypeId
                                                )
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Drive started! GPS tracking active.")
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("start_drive_button")
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Start Drive", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Mileage Overview Card
                item {
                    MileageOverviewCard(
                        trips = periodTrips,
                        selectedPeriod = selectedPeriod,
                        onPeriodSelected = { selectedPeriod = it },
                        distanceUnit = userProfile.distanceUnit,
                        currencySymbol = userProfile.currencySymbol
                    )
                }

                // 3. Google Drive Cloud Backup Quick Sync Banner
                item {
                    GoogleDriveSyncCard(
                        userProfile = userProfile,
                        repository = repository,
                        onSignInClicked = onSignInWithGoogle,
                        onSignOutClicked = onSignOut,
                        onShowMessage = { msg ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    )
                }

                // 4. Trip Search & Unified Trip Type Filter Bar (reads directly from CustomTripType table in Room)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search trips, locations, trip types...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_trips_input"),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedFilterTripTypeId == null,
                                onClick = { selectedFilterTripTypeId = null },
                                label = { Text("All Trip Types (${periodTrips.size})", maxLines = 1, softWrap = false) }
                            )

                            tripTypes.forEach { tt ->
                                val count = periodTrips.count { IrsPdfReportGenerator.resolveTripTypeId(it, tripTypesById) == tt.id }
                                FilterChip(
                                    selected = selectedFilterTripTypeId == tt.id,
                                    onClick = {
                                        selectedFilterTripTypeId = if (selectedFilterTripTypeId == tt.id) null else tt.id
                                    },
                                    label = { Text("${tt.name} ($count)", maxLines = 1, softWrap = false) }
                                )
                            }

                            val unclassifiedCount = periodTrips.count {
                                IrsPdfReportGenerator.resolveTripTypeId(it, tripTypesById) == IrsPdfReportGenerator.UNCLASSIFIED_TRIP_TYPE_ID
                            }
                            if (unclassifiedCount > 0 || selectedFilterTripTypeId == IrsPdfReportGenerator.UNCLASSIFIED_TRIP_TYPE_ID) {
                                FilterChip(
                                    selected = selectedFilterTripTypeId == IrsPdfReportGenerator.UNCLASSIFIED_TRIP_TYPE_ID,
                                    onClick = {
                                        selectedFilterTripTypeId = if (selectedFilterTripTypeId == IrsPdfReportGenerator.UNCLASSIFIED_TRIP_TYPE_ID) null else IrsPdfReportGenerator.UNCLASSIFIED_TRIP_TYPE_ID
                                    },
                                    label = { Text("Unclassified ($unclassifiedCount)", maxLines = 1, softWrap = false) }
                                )
                            }
                        }
                    }
                }

                // 5. Trips Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Logged Trips & Batches",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${filteredTrips.size} trips",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 6. Trips List
                if (filteredTrips.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank() || selectedFilterTripTypeId != null)
                                        "No trips match your filters"
                                    else
                                        "No trips logged for this period",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Start Drive' above or '+ Log Trip' below to record trips with live map & dropoffs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredTrips,
                        key = { it.id }
                    ) { trip ->
                        TripItemCard(
                            trip = trip,
                            tripTypes = tripTypes,
                            defaultMinutesPerStop = userProfile.minutesPerDropoffStop,
                            distanceUnit = userProfile.distanceUnit,
                            currencySymbol = userProfile.currencySymbol,
                            onEdit = {
                                tripToEdit = it
                                showTripDialog = true
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    repository.deleteTrip(it)
                                    snackbarHostState.showSnackbar("Deleted trip: ${it.title}")
                                }
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }

        if (showTripDialog) {
            TripAddEditDialog(
                tripToEdit = tripToEdit,
                vehicles = vehicles,
                userProfile = userProfile,
                repository = repository,
                onDismiss = {
                    showTripDialog = false
                    tripToEdit = null
                },
                onSaveTrip = { savedTrip ->
                    coroutineScope.launch {
                        repository.saveTrip(savedTrip)
                        showTripDialog = false
                        tripToEdit = null
                        snackbarHostState.showSnackbar("Trip saved: ${savedTrip.title}")
                    }
                }
            )
        }
    }
}
