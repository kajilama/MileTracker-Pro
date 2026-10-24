package com.example.ui.trips

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.AddressSuggestion
import com.example.data.model.Trip
import com.example.data.model.TripPurpose
import com.example.data.model.TripStop
import com.example.data.model.UserProfile
import com.example.data.model.Vehicle
import com.example.data.repository.DrivingRouteInfo
import com.example.data.repository.DrivingRouteService
import com.example.data.repository.LocationService
import com.example.data.repository.MileageRepository
import com.example.data.service.IrsRateService
import com.example.ui.map.MapWaypoint
import com.example.ui.map.OsmMapView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripAddEditDialog(
    tripToEdit: Trip? = null,
    vehicles: List<Vehicle>,
    userProfile: UserProfile,
    repository: MileageRepository,
    onDismiss: () -> Unit,
    onSaveTrip: (Trip) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val tripTypes by repository.allTripTypes.collectAsState(initial = emptyList())

    // Date & Time states
    var tripDateMillis by remember { mutableStateOf(tripToEdit?.dateTimestamp ?: System.currentTimeMillis()) }
    var endTimeMillis by remember { mutableStateOf(tripToEdit?.endTimeMillis ?: System.currentTimeMillis()) }

    var selectedTripTypeId by remember {
        mutableStateOf(
            if (tripToEdit != null) tripToEdit.tripTypeId else userProfile.defaultTripTypeId
        )
    }

    // Ensure default tripTypeId is valid when tripTypes load for a new trip
    LaunchedEffect(tripTypes) {
        if (tripToEdit == null && tripTypes.isNotEmpty()) {
            val currentMatch = tripTypes.find { it.id == selectedTripTypeId }
            if (currentMatch == null) {
                selectedTripTypeId = tripTypes.find { it.isDefault }?.id ?: tripTypes.first().id
            }
        } else if (tripToEdit != null && tripTypes.isNotEmpty() && selectedTripTypeId != null) {
            if (tripTypes.none { it.id == selectedTripTypeId }) {
                selectedTripTypeId = null // Deleted trip type -> Unclassified
            }
        }
    }

    val selectedTripType = remember(selectedTripTypeId, tripTypes) {
        tripTypes.find { it.id == selectedTripTypeId }
    }

    var startAddress by remember { mutableStateOf(tripToEdit?.startAddress ?: "") }
    var startLat by remember { mutableStateOf(tripToEdit?.startLat) }
    var startLng by remember { mutableStateOf(tripToEdit?.startLng) }

    // Per-trip configurable minutes per dropoff stop (0-15, initialized from Settings or existing trip)
    var perTripMinutesPerStop by remember {
        val initialMins = if (tripToEdit != null && tripToEdit.stopsCount > 0) {
            (tripToEdit.dropoffMinutes / tripToEdit.stopsCount).coerceIn(0, 15)
        } else {
            userProfile.minutesPerDropoffStop.coerceIn(0, 15)
        }
        mutableIntStateOf(initialMins)
    }

    // Multi-stop customer dropoff list
    val stops = remember { mutableStateListOf<TripStop>() }
    var isRoundTrip by remember { mutableStateOf(tripToEdit?.isRoundTrip ?: true) }

    var endAddress by remember { mutableStateOf(tripToEdit?.endAddress ?: "") }
    var endLat by remember { mutableStateOf(tripToEdit?.endLat) }
    var endLng by remember { mutableStateOf(tripToEdit?.endLng) }

    var manualDistanceMiles by remember { mutableStateOf<Double?>(tripToEdit?.distanceMiles) }
    var selectedVehicleId by remember {
        mutableStateOf(
            tripToEdit?.vehicleId
                ?: vehicles.firstOrNull { it.isDefault }?.id
                ?: vehicles.firstOrNull()?.id
        )
    }
    var notes by remember { mutableStateOf(tripToEdit?.notes ?: "") }

    // Current device coordinates
    var currentDeviceLat by remember { mutableStateOf<Double?>(null) }
    var currentDeviceLng by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(Unit) {
        val loc = repository.locationService.getCurrentLocation()
        if (loc != null) {
            currentDeviceLat = loc.latitude
            currentDeviceLng = loc.longitude
        }
    }

    // Initialize stops if editing or preloading 1 dropoff stop for new trip
    LaunchedEffect(Unit) {
        if (tripToEdit != null) {
            if (stops.isEmpty() && tripToEdit.stopsSummary.isNotBlank()) {
                tripToEdit.stopsSummary.split(" | ").forEach { addr ->
                    stops.add(TripStop(address = addr, dropoffMinutes = perTripMinutesPerStop))
                }
            }
        } else if (stops.isEmpty()) {
            stops.add(TripStop(address = "", dropoffMinutes = perTripMinutesPerStop))
        }
    }

    var drivingRouteInfo by remember { mutableStateOf<DrivingRouteInfo?>(null) }
    var isCalculatingRoute by remember { mutableStateOf(false) }

    LaunchedEffect(startLat, startLng, stops.map { "${it.lat},${it.lng}" }, endLat, endLng, isRoundTrip) {
        val sLat = startLat
        val sLng = startLng
        if (sLat != null && sLng != null) {
            isCalculatingRoute = true
            val eLat = if (isRoundTrip) sLat else (endLat ?: sLat)
            val eLng = if (isRoundTrip) sLng else (endLng ?: sLng)
            val route = DrivingRouteService.getDrivingRoute(
                startLat = sLat,
                startLng = sLng,
                stops = stops,
                endLat = eLat,
                endLng = eLng,
                isRoundTrip = isRoundTrip
            )
            drivingRouteInfo = route
            isCalculatingRoute = false
        }
    }

    val calculatedDistance = remember(startLat, startLng, stops.map { "${it.lat},${it.lng}" }, endLat, endLng, isRoundTrip) {
        var total = 0.0
        var prevLat = startLat
        var prevLng = startLng

        for (stop in stops) {
            if (prevLat != null && prevLng != null && stop.lat != null && stop.lng != null) {
                total += LocationService.calculateDistanceMiles(prevLat, prevLng, stop.lat, stop.lng)
            }
            if (stop.lat != null && stop.lng != null) {
                prevLat = stop.lat
                prevLng = stop.lng
            }
        }

        if (isRoundTrip) {
            if (prevLat != null && prevLng != null && startLat != null && startLng != null) {
                total += LocationService.calculateDistanceMiles(prevLat, prevLng, startLat!!, startLng!!)
            }
        } else {
            if (prevLat != null && prevLng != null && endLat != null && endLng != null) {
                total += LocationService.calculateDistanceMiles(prevLat, prevLng, endLat!!, endLng!!)
            }
        }

        if (total > 0.1) String.format(Locale.US, "%.1f", total).toDoubleOrNull() ?: total else null
    }

    val effectiveDistanceMiles = manualDistanceMiles ?: drivingRouteInfo?.distanceMiles ?: calculatedDistance ?: 5.0

    // TIME & DROPOFF AUTO-CALCULATION using configurable perTripMinutesPerStop:
    // Total Route Duration = driving time + stops × minutes per stop
    // Start Time = End Time - Total Route Duration
    val drivingMinutes = drivingRouteInfo?.drivingMinutes ?: remember(effectiveDistanceMiles) {
        ((effectiveDistanceMiles / 24.0) * 60).toInt().coerceAtLeast(3)
    }

    val dropoffMinutes = remember(stops.size, perTripMinutesPerStop) {
        stops.size * perTripMinutesPerStop
    }

    val totalDurationMinutes = remember(drivingMinutes, dropoffMinutes) {
        drivingMinutes + dropoffMinutes
    }

    val startTimeMillis = remember(endTimeMillis, totalDurationMinutes) {
        endTimeMillis - (totalDurationMinutes * 60 * 1000L)
    }

    // Derive TripPurpose and date-effective IRS deduction rate from selected CustomTripType
    val purpose = remember(selectedTripType) {
        when {
            selectedTripType == null -> TripPurpose.OTHER
            !selectedTripType.isTaxDeductible && selectedTripType.name.contains("Commute", ignoreCase = true) -> TripPurpose.COMMUTE
            !selectedTripType.isTaxDeductible -> TripPurpose.PERSONAL
            selectedTripType.name.contains("Medical", ignoreCase = true) -> TripPurpose.MEDICAL
            selectedTripType.name.contains("Charity", ignoreCase = true) -> TripPurpose.CHARITY
            else -> TripPurpose.GIG_DELIVERY
        }
    }

    val deductionRate = remember(selectedTripType, tripDateMillis) {
        val tripYear = Calendar.getInstance().apply { timeInMillis = tripDateMillis }.get(Calendar.YEAR)
        val officialRates = IrsRateService.getOfficialRateForYear(tripYear)
        when {
            selectedTripType == null -> 0.0
            !selectedTripType.isTaxDeductible -> 0.0
            selectedTripType.name.contains("Medical", ignoreCase = true) -> officialRates.medicalRate
            selectedTripType.name.contains("Charity", ignoreCase = true) -> officialRates.charityRate
            else -> officialRates.businessRate
        }
    }
    val totalDeduction = effectiveDistanceMiles * deductionRate

    // Autocomplete & GPS management
    var activeFieldId by remember { mutableStateOf<String?>(null) }
    var searchSuggestions by remember { mutableStateOf<List<AddressSuggestion>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isLocatingField by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            coroutineScope.launch {
                val loc = repository.locationService.getCurrentLocation()
                if (loc != null) {
                    currentDeviceLat = loc.latitude
                    currentDeviceLng = loc.longitude
                    val resolved = repository.locationService.reverseGeocode(loc.latitude, loc.longitude)

                    when (isLocatingField) {
                        "START" -> {
                            startAddress = resolved
                            startLat = loc.latitude
                            startLng = loc.longitude
                        }
                        "END" -> {
                            endAddress = resolved
                            endLat = loc.latitude
                            endLng = loc.longitude
                        }
                        else -> {
                            val stopIdx = stops.indexOfFirst { it.id == isLocatingField }
                            if (stopIdx >= 0) {
                                stops[stopIdx] = stops[stopIdx].copy(address = resolved, lat = loc.latitude, lng = loc.longitude)
                            }
                        }
                    }
                }
                isLocatingField = null
            }
        } else {
            isLocatingField = null
        }
    }

    fun requestGpsForField(fieldId: String) {
        isLocatingField = fieldId
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            coroutineScope.launch {
                val loc = repository.locationService.getCurrentLocation()
                if (loc != null) {
                    currentDeviceLat = loc.latitude
                    currentDeviceLng = loc.longitude
                    val resolved = repository.locationService.reverseGeocode(loc.latitude, loc.longitude)

                    when (fieldId) {
                        "START" -> {
                            startAddress = resolved
                            startLat = loc.latitude
                            startLng = loc.longitude
                        }
                        "END" -> {
                            endAddress = resolved
                            endLat = loc.latitude
                            endLng = loc.longitude
                        }
                        else -> {
                            val stopIdx = stops.indexOfFirst { it.id == fieldId }
                            if (stopIdx >= 0) {
                                stops[stopIdx] = stops[stopIdx].copy(address = resolved, lat = loc.latitude, lng = loc.longitude)
                            }
                        }
                    }
                }
                isLocatingField = null
            }
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    var searchJob by remember { mutableStateOf<Job?>(null) }
    fun onQueryChanged(fieldId: String, text: String) {
        activeFieldId = fieldId
        searchJob?.cancel()
        if (text.trim().length >= 2) {
            isSearching = true
            searchJob = coroutineScope.launch {
                delay(200)
                searchSuggestions = repository.getAddressSuggestions(text, currentDeviceLat, currentDeviceLng)
                isSearching = false
            }
        } else {
            searchSuggestions = emptyList()
            isSearching = false
        }
    }

    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()) }

    val waypoints = remember(stops.toList(), startAddress, isRoundTrip) {
        stops.mapIndexedNotNull { index, stop ->
            if (stop.lat != null && stop.lng != null) {
                MapWaypoint(
                    title = stop.address.ifBlank { "Stop ${index + 1}" },
                    lat = stop.lat,
                    lng = stop.lng,
                    stopNumber = index + 1,
                    isDropoff = true
                )
            } else null
        }
    }

    val performSave = {
        val typeLabel = selectedTripType?.name ?: "Unclassified"
        val finalTitle = (tripToEdit?.title ?: "").ifBlank {
            if (startAddress.isNotBlank()) {
                val startStore = startAddress.substringBefore(",")
                if (stops.isNotEmpty()) "$typeLabel: $startStore (${stops.size} dropoffs)" else "$typeLabel: $startStore"
            } else {
                "$typeLabel Trip"
            }
        }
        val finalEndAddress = if (isRoundTrip) startAddress else endAddress.ifBlank { "Destination" }
        val vehicle = vehicles.firstOrNull { it.id == selectedVehicleId }

        val stopsSummary = stops.filter { it.address.isNotBlank() }.joinToString(" | ") { it.address }
        val routePointsJson = DrivingRouteService.serializeRoadPoints(drivingRouteInfo?.roadPoints ?: emptyList())

        val trip = Trip(
            id = tripToEdit?.id ?: java.util.UUID.randomUUID().toString(),
            title = finalTitle,
            purpose = purpose,
            tripTypeId = selectedTripType?.id,
            tags = typeLabel,
            startAddress = startAddress.ifBlank { "Start Location" },
            endAddress = finalEndAddress,
            startLat = startLat,
            startLng = startLng,
            endLat = if (isRoundTrip) startLat else endLat,
            endLng = if (isRoundTrip) startLng else endLng,
            distanceMiles = effectiveDistanceMiles,
            deductionRate = deductionRate,
            totalDeduction = totalDeduction,
            dateTimestamp = tripDateMillis,
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            isRoundTrip = isRoundTrip,
            stopsCount = stops.size,
            stopsSummary = stopsSummary,
            drivingMinutes = drivingMinutes,
            dropoffMinutes = dropoffMinutes,
            totalDurationMinutes = totalDurationMinutes,
            vehicleId = selectedVehicleId,
            vehicleName = vehicle?.makeModel ?: vehicle?.name,
            notes = notes,
            routePointsJson = routePointsJson,
            isSyncedWithDrive = false
        )
        onSaveTrip(trip)
    }

    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("top_cancel_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = if (tripToEdit == null) "Log Trip" else "Edit Trip",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1
                                )
                                Text(
                                    text = "${stops.size} Dropoffs (${perTripMinutesPerStop}m/stop) • Road Route",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = performSave,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.testTag("top_save_trip_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = performSave,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("save_trip_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Trip", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. DATE & TIME SECTION
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val cal = Calendar.getInstance().apply { timeInMillis = tripDateMillis }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            cal.set(y, m, d)
                                            tripDateMillis = cal.timeInMillis
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = dateFormatter.format(Date(tripDateMillis)),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            Text("Change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Start Time (Auto)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = timeFormatter.format(Date(startTimeMillis)),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                )
                            }

                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))

                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val cal = Calendar.getInstance().apply { timeInMillis = endTimeMillis }
                                        TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                cal.set(Calendar.MINUTE, minute)
                                                endTimeMillis = cal.timeInMillis
                                            },
                                            cal.get(Calendar.HOUR_OF_DAY),
                                            cal.get(Calendar.MINUTE),
                                            false
                                        ).show()
                                    }
                                    .padding(4.dp)
                            ) {
                                Text("End Time (Tap to edit)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = timeFormatter.format(Date(endTimeMillis)),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                // 2. UNIFIED TRIP TYPE SELECTOR (Reads directly from Room CustomTripType table)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalOffer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Trip Type",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tripTypes.forEach { tt ->
                            val isSelected = selectedTripTypeId == tt.id
                            val chipColor = try {
                                Color(android.graphics.Color.parseColor(tt.colorHex))
                            } catch (_: Exception) {
                                MaterialTheme.colorScheme.primary
                            }

                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedTripTypeId = tt.id },
                                label = { Text(tt.name, maxLines = 1, softWrap = false) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = chipColor.copy(alpha = 0.2f),
                                    selectedLabelColor = chipColor,
                                    selectedLeadingIconColor = chipColor
                                ),
                                modifier = Modifier.testTag("trip_type_chip_${tt.id}")
                            )
                        }

                        FilterChip(
                            selected = selectedTripTypeId == null,
                            onClick = { selectedTripTypeId = null },
                            label = { Text("Unclassified", maxLines = 1, softWrap = false) },
                            leadingIcon = if (selectedTripTypeId == null) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }

                // 3. START LOCATION
                AddressInputField(
                    label = "Start Location (Store / Depot / Home)",
                    placeholder = "Type store name or address (e.g. Walmart, Costco, 123 Main)",
                    value = startAddress,
                    onValueChange = {
                        startAddress = it
                        onQueryChanged("START", it)
                    },
                    onGpsClicked = { requestGpsForField("START") },
                    isLocating = isLocatingField == "START",
                    iconColor = Color(0xFF10B981)
                )

                if (activeFieldId == "START" && searchSuggestions.isNotEmpty()) {
                    SuggestionDropdownCard(
                        suggestions = searchSuggestions,
                        onSelect = { sug ->
                            startAddress = sug.fullAddress
                            startLat = sug.latitude
                            startLng = sug.longitude
                            activeFieldId = null
                        }
                    )
                }

                // 4. CUSTOMER DROPOFF STOPS WITH CONFIGURABLE MINUTES PER STOP OVERRIDE
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Customer Dropoff Stops (${stops.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Text(
                                text = "Minutes per stop (Default: ${userProfile.minutesPerDropoffStop}m, override 0–15m)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (perTripMinutesPerStop > 0) {
                                        perTripMinutesPerStop -= 1
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("trip_minutes_per_stop_minus")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease minutes per stop", modifier = Modifier.size(18.dp))
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "${perTripMinutesPerStop}m / stop",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFB45309)),
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .testTag("trip_minutes_per_stop_value")
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (perTripMinutesPerStop < 15) {
                                        perTripMinutesPerStop += 1
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("trip_minutes_per_stop_plus")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase minutes per stop", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                stops.forEachIndexed { index, stop ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFF59E0B),
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Stop ${index + 1} - Customer Dropoff (+${perTripMinutesPerStop}m)",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }

                                IconButton(
                                    onClick = { stops.removeAt(index) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove Stop", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }

                            AddressInputField(
                                label = "Customer Address / Dropoff Point",
                                placeholder = "Enter street address or apartment",
                                value = stop.address,
                                onValueChange = { text ->
                                    stops[index] = stop.copy(address = text, dropoffMinutes = perTripMinutesPerStop)
                                    onQueryChanged(stop.id, text)
                                },
                                onGpsClicked = { requestGpsForField(stop.id) },
                                isLocating = isLocatingField == stop.id,
                                iconColor = Color(0xFFF59E0B)
                            )

                            if (activeFieldId == stop.id && searchSuggestions.isNotEmpty()) {
                                SuggestionDropdownCard(
                                    suggestions = searchSuggestions,
                                    onSelect = { sug ->
                                        stops[index] = stop.copy(address = sug.fullAddress, lat = sug.latitude, lng = sug.longitude, dropoffMinutes = perTripMinutesPerStop)
                                        activeFieldId = null
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        stops.add(TripStop(address = "", dropoffMinutes = perTripMinutesPerStop))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Another Customer Dropoff Stop")
                }

                // 5. ROUND-TRIP OR FINAL DESTINATION
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isRoundTrip = !isRoundTrip },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isRoundTrip,
                                onCheckedChange = { isRoundTrip = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Round-Trip (Return to Start Location / Store)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Routes back to original store or home after dropping off all customer orders.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isRoundTrip) {
                            Spacer(modifier = Modifier.height(10.dp))
                            AddressInputField(
                                label = "Final Destination",
                                placeholder = "Enter end address",
                                value = endAddress,
                                onValueChange = {
                                    endAddress = it
                                    onQueryChanged("END", it)
                                },
                                onGpsClicked = { requestGpsForField("END") },
                                isLocating = isLocatingField == "END",
                                iconColor = Color(0xFFEF4444)
                            )

                            if (activeFieldId == "END" && searchSuggestions.isNotEmpty()) {
                                SuggestionDropdownCard(
                                    suggestions = searchSuggestions,
                                    onSelect = { sug ->
                                        endAddress = sug.fullAddress
                                        endLat = sug.latitude
                                        endLng = sug.longitude
                                        activeFieldId = null
                                    }
                                )
                            }
                        }
                    }
                }

                // 6. TIME & DISTANCE SUMMARY BANNER (AUTO-CALCULATED WITH CONFIGURABLE MIN/STOP)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Auto-Calculated Route Metrics",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                )
                            }
                            Text(
                                text = "%s%.2f IRS deduction".format(userProfile.currencySymbol, totalDeduction),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(modifier = Modifier.defaultMinSize(minWidth = 75.dp)) {
                                Text("Total Distance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                                Text(
                                    text = "%.1f %s".format(effectiveDistanceMiles, userProfile.distanceUnit.take(2).lowercase()),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Column(modifier = Modifier.defaultMinSize(minWidth = 75.dp)) {
                                Text("Drive Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                                Text(
                                    text = "$drivingMinutes mins",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Column(modifier = Modifier.defaultMinSize(minWidth = 85.dp)) {
                                Text("Dropoffs (${stops.size} × ${perTripMinutesPerStop}m)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                                Text(
                                    text = "$dropoffMinutes mins",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFD97706)),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Column(modifier = Modifier.defaultMinSize(minWidth = 75.dp)) {
                                Text("Total Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                                Text(
                                    text = "$totalDurationMinutes mins",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Text(
                            text = "Start: ${timeFormatter.format(Date(startTimeMillis))}  →  End: ${timeFormatter.format(Date(endTimeMillis))}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Text(
                    text = "Road Navigation Route Preview (Free OpenStreetMap)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OsmMapView(
                    startLat = startLat,
                    startLng = startLng,
                    startTitle = startAddress.ifBlank { "Start Location" },
                    endLat = if (isRoundTrip) startLat else endLat,
                    endLng = if (isRoundTrip) startLng else endLng,
                    endTitle = if (isRoundTrip) "Return to Start" else endAddress.ifBlank { "Destination" },
                    waypoints = waypoints,
                    roadPoints = drivingRouteInfo?.roadPoints ?: emptyList(),
                    isRoundTrip = isRoundTrip,
                    currentLat = currentDeviceLat,
                    currentLng = currentDeviceLng,
                    height = 170.dp,
                    interactive = false
                )

                // 7. VEHICLE SELECTION & NOTES
                if (vehicles.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        vehicles.forEach { v ->
                            val isSelected = selectedVehicleId == v.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedVehicleId = v.id },
                                label = { Text("${v.name} (${v.makeModel})") },
                                leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Delivery Notes / Order Receipts (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.85f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                )

                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

@Composable
private fun AddressInputField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    onGpsClicked: () -> Unit,
    isLocating: Boolean,
    iconColor: Color
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = iconColor) },
        trailingIcon = {
            IconButton(onClick = onGpsClicked, modifier = Modifier.testTag("gps_button_${label.take(5).lowercase()}")) {
                if (isLocating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Default.MyLocation, contentDescription = "Use GPS Location", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.85f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SuggestionDropdownCard(
    suggestions: List<AddressSuggestion>,
    onSelect: (AddressSuggestion) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            suggestions.take(5).forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(suggestion) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (suggestion.isStoreOrPlace) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (suggestion.isStoreOrPlace) Icons.Default.Store else Icons.Default.Place,
                                contentDescription = null,
                                tint = if (suggestion.isStoreOrPlace) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = suggestion.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        if (suggestion.subtitle.isNotBlank()) {
                            Text(text = suggestion.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (suggestion.distanceDisplay != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = suggestion.distanceDisplay,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
