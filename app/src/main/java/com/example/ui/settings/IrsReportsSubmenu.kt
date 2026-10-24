package com.example.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CustomTripType
import com.example.data.model.Trip
import com.example.data.model.UserProfile
import com.example.data.model.Vehicle
import com.example.data.repository.MileageRepository
import com.example.data.service.IrsRateService
import com.example.data.util.IrsPdfReportGenerator
import com.example.ui.trips.TripAddEditDialog
import com.example.ui.trips.TripDetailMapDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrsReportsSubmenu(
    repository: MileageRepository,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val userProfile by repository.userProfile.collectAsState()
    val trips by repository.allTrips.collectAsState(initial = emptyList())
    val vehicles by repository.allVehicles.collectAsState(initial = emptyList())
    val tripTypes by repository.allTripTypes.collectAsState(initial = emptyList())
    val tripTypesById = remember(tripTypes) { tripTypes.associateBy { it.id } }

    var selectedPeriod by remember { mutableStateOf(IrsPdfReportGenerator.ReportPeriod.CURRENT_YEAR) }
    var customStartMillis by remember {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }
        mutableLongStateOf(cal.timeInMillis)
    }
    var customEndMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    var selectedVehicleFilter by remember { mutableStateOf("All Vehicles") }
    // Empty set = All Trip Types; non-empty = multi-selected Trip Type IDs (including UNCLASSIFIED_TRIP_TYPE_ID)
    var selectedTripTypeIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showInAppReportDialog by remember { mutableStateOf(false) }
    var selectedTripForDetail by remember { mutableStateOf<Trip?>(null) }
    var tripToEdit by remember { mutableStateOf<Trip?>(null) }
    var isRefreshingRates by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }

    // Filter calculations using unified tripTypeId set and custom date range support
    val filteredTrips = remember(
        trips,
        selectedPeriod,
        selectedVehicleFilter,
        selectedTripTypeIds,
        tripTypes,
        customStartMillis,
        customEndMillis
    ) {
        val vehFilter = if (selectedVehicleFilter == "All Vehicles") null else selectedVehicleFilter
        val typeFilter = if (selectedTripTypeIds.isEmpty()) null else selectedTripTypeIds
        IrsPdfReportGenerator.filterTrips(
            trips = trips,
            tripTypes = tripTypes,
            period = selectedPeriod,
            vehicleFilter = vehFilter,
            selectedTripTypeIds = typeFilter,
            customStartEpoch = customStartMillis,
            customEndEpoch = customEndMillis
        ).sortedByDescending { it.dateTimestamp }
    }

    val totalTrackedMiles = filteredTrips.sumOf { it.distanceMiles }
    val businessTrips = filteredTrips.filter { IrsPdfReportGenerator.isBusinessTrip(it, tripTypesById) }
    val businessMiles = businessTrips.sumOf { it.distanceMiles }
    val personalMiles = (totalTrackedMiles - businessMiles).coerceAtLeast(0.0)
    val totalDeduction = filteredTrips.sumOf {
        IrsPdfReportGenerator.computeEffectiveDeductionForTrip(it, tripTypesById)
    }

    // Selected vehicle's IRS Jan 1 & Dec 31 Odometer figures
    val matchedVehicle = vehicles.firstOrNull { it.name == selectedVehicleFilter || it.makeModel == selectedVehicleFilter }
        ?: vehicles.firstOrNull { it.isDefault } ?: vehicles.firstOrNull()

    val odoJan1 = matchedVehicle?.odometerJan1
    val odoDec31 = matchedVehicle?.odometerDec31
    val odoAnnualMiles = if (odoJan1 != null && odoDec31 != null && odoDec31 >= odoJan1) {
        odoDec31 - odoJan1
    } else null

    fun triggerCsvExport() {
        coroutineScope.launch {
            val vehFilter = if (selectedVehicleFilter == "All Vehicles") null else selectedVehicleFilter
            val typeFilter = if (selectedTripTypeIds.isEmpty()) null else selectedTripTypeIds
            val csvContent = repository.driveBackupManager.exportIrsCsvReport(
                vehicleFilter = vehFilter,
                tripTypeIds = typeFilter,
                preFilteredTrips = filteredTrips
            )
            try {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "IRS Mileage Log (Contemporaneous CSV)")
                    putExtra(Intent.EXTRA_TEXT, csvContent)
                }
                val chooser = Intent.createChooser(sendIntent, "Export / Share IRS Mileage CSV")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                onShowMessage("Exported contemporaneous IRS CSV (${filteredTrips.size} entries)")
            } catch (e: Exception) {
                onShowMessage("CSV ready (${csvContent.lines().size} lines)")
            }
        }
    }

    fun triggerPdfExport() {
        coroutineScope.launch {
            val vehFilter = if (selectedVehicleFilter == "All Vehicles") null else selectedVehicleFilter
            val typeFilter = if (selectedTripTypeIds.isEmpty()) null else selectedTripTypeIds
            val pdfFile = IrsPdfReportGenerator.generateIrsPdf(
                context = context,
                userProfile = userProfile,
                trips = trips,
                period = selectedPeriod,
                vehicleFilter = vehFilter,
                selectedTripTypeIds = typeFilter,
                tripTypes = tripTypes,
                preFilteredTrips = filteredTrips
            )
            IrsPdfReportGenerator.sharePdfReport(context, pdfFile)
            onShowMessage("Generated IRS PDF Log: ${pdfFile.name}")
        }
    }

    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = customStartMillis)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { customStartMillis = it }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showEndDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = customEndMillis)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { customEndMillis = it }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // MANDATORY STATUTORY IRS DISCLAIMER NOTE
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Official IRS Publication 463 Notice",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = "This app helps keep records. Consult a tax professional. See IRS Publication 463 for complete documentation requirements.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Live Rate & Quarterly Auto-Check Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active IRS Standard Mileage Rate", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Checked automatically on app launch and quarterly from official sources", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isRefreshingRates = true
                                val schedule = IrsRateService.syncIrsRatesOnLaunch(repository.preferencesManager)
                                isRefreshingRates = false
                                onShowMessage("Verified IRS Rate: $%.2f/mi (%d tax year)".format(schedule.businessRate, schedule.taxYear))
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isRefreshingRates) "Checking..." else "Sync Rate", fontSize = 11.sp)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Business Standard Rate", style = MaterialTheme.typography.labelSmall)
                            Text("$%.2f / mile".format(userProfile.irsBusinessRate), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Medical Travel Rate", style = MaterialTheme.typography.labelSmall)
                            Text("$%.2f / mile".format(userProfile.irsMedicalRate), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // Filters Card (Date Range, Vehicle, Trip Type Multi-Select by ID)
        ReportFiltersSection(
            selectedPeriod = selectedPeriod,
            onSelectPeriod = { selectedPeriod = it },
            customStartMillis = customStartMillis,
            customEndMillis = customEndMillis,
            onPickStartDate = { showStartDatePicker = true },
            onPickEndDate = { showEndDatePicker = true },
            vehicles = vehicles,
            selectedVehicleFilter = selectedVehicleFilter,
            onSelectVehicle = { selectedVehicleFilter = it },
            tripTypes = tripTypes,
            selectedTripTypeIds = selectedTripTypeIds,
            onToggleTripTypeId = { id ->
                selectedTripTypeIds = if (id == null) {
                    emptySet()
                } else {
                    val next = selectedTripTypeIds.toMutableSet()
                    if (next.contains(id)) next.remove(id) else next.add(id)
                    next
                }
            },
            dateFormatter = dateFormatter
        )

        // Summary Statistics Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tax Audit & Deduction Totals", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                if (odoAnnualMiles != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Miles for the Year (from Odometer Start/End)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            Text(
                                "%.0f miles (Jan 1: %.0f mi → Dec 31: %.0f mi)".format(odoAnnualMiles, odoJan1, odoDec31),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Miles", style = MaterialTheme.typography.labelSmall)
                            Text("%.1f mi".format(totalTrackedMiles), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Business Miles", style = MaterialTheme.typography.labelSmall)
                            Text("%.1f mi".format(businessMiles), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Personal Miles", style = MaterialTheme.typography.labelSmall)
                            Text("%.1f mi".format(personalMiles), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Estimated Tax Deduction", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        Text(
                            "$%.2f".format(totalDeduction),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            "Computed using date-effective IRS rates across %d matching trips".format(filteredTrips.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Actions Row: View Report | Export CSV | PDF Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showInAppReportDialog = true },
                modifier = Modifier
                    .weight(1.1f)
                    .testTag("view_report_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("View Report", fontSize = 12.sp, maxLines = 1)
            }

            OutlinedButton(
                onClick = { triggerCsvExport() },
                modifier = Modifier
                    .weight(1f)
                    .testTag("export_csv_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export CSV", fontSize = 12.sp, maxLines = 1)
            }

            OutlinedButton(
                onClick = { triggerPdfExport() },
                modifier = Modifier
                    .weight(1f)
                    .testTag("pdf_summary_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("PDF Summary", fontSize = 12.sp, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    // FULL-SCREEN IN-APP REPORT VIEW
    if (showInAppReportDialog) {
        InAppReportScreenDialog(
            filteredTrips = filteredTrips,
            vehicles = vehicles,
            tripTypes = tripTypes,
            tripTypesById = tripTypesById,
            selectedPeriod = selectedPeriod,
            onSelectPeriod = { selectedPeriod = it },
            customStartMillis = customStartMillis,
            customEndMillis = customEndMillis,
            onPickStartDate = { showStartDatePicker = true },
            onPickEndDate = { showEndDatePicker = true },
            selectedVehicleFilter = selectedVehicleFilter,
            onSelectVehicle = { selectedVehicleFilter = it },
            selectedTripTypeIds = selectedTripTypeIds,
            onToggleTripTypeId = { id ->
                selectedTripTypeIds = if (id == null) {
                    emptySet()
                } else {
                    val next = selectedTripTypeIds.toMutableSet()
                    if (next.contains(id)) next.remove(id) else next.add(id)
                    next
                }
            },
            dateFormatter = dateFormatter,
            onTripClick = { trip -> selectedTripForDetail = trip },
            onExportCsv = { triggerCsvExport() },
            onExportPdf = { triggerPdfExport() },
            onDismiss = { showInAppReportDialog = false }
        )
    }

    // Trip Detail dialog when tapping a trip inside the In-App Report
    if (selectedTripForDetail != null) {
        val currentTrip = selectedTripForDetail!!
        val resolvedTypeLabel = IrsPdfReportGenerator.resolveTripTypeName(currentTrip, tripTypesById)
        TripDetailMapDialog(
            trip = currentTrip,
            tripTypeLabel = resolvedTypeLabel,
            defaultMinutesPerStop = userProfile.minutesPerDropoffStop,
            distanceUnit = userProfile.distanceUnit,
            currencySymbol = userProfile.currencySymbol,
            onDismiss = { selectedTripForDetail = null },
            onEdit = { trip ->
                selectedTripForDetail = null
                tripToEdit = trip
            },
            onDelete = { trip ->
                coroutineScope.launch {
                    repository.deleteTrip(trip)
                    selectedTripForDetail = null
                    onShowMessage("Trip deleted")
                }
            }
        )
    }

    if (tripToEdit != null) {
        TripAddEditDialog(
            tripToEdit = tripToEdit,
            vehicles = vehicles,
            userProfile = userProfile,
            repository = repository,
            onDismiss = { tripToEdit = null },
            onSaveTrip = { updatedTrip ->
                coroutineScope.launch {
                    repository.saveTrip(updatedTrip)
                    tripToEdit = null
                    onShowMessage("Updated trip")
                }
            }
        )
    }
}

@Composable
private fun ReportFiltersSection(
    selectedPeriod: IrsPdfReportGenerator.ReportPeriod,
    onSelectPeriod: (IrsPdfReportGenerator.ReportPeriod) -> Unit,
    customStartMillis: Long,
    customEndMillis: Long,
    onPickStartDate: () -> Unit,
    onPickEndDate: () -> Unit,
    vehicles: List<Vehicle>,
    selectedVehicleFilter: String,
    onSelectVehicle: (String) -> Unit,
    tripTypes: List<CustomTripType>,
    selectedTripTypeIds: Set<String>,
    onToggleTripTypeId: (String?) -> Unit,
    dateFormatter: SimpleDateFormat
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Report Filters & Scope", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))

            // Date Range Period Chips
            Text("Date Range:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IrsPdfReportGenerator.ReportPeriod.values().forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { onSelectPeriod(period) },
                        label = { Text(period.title, fontSize = 11.sp) }
                    )
                }
            }

            if (selectedPeriod == IrsPdfReportGenerator.ReportPeriod.CUSTOM_RANGE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickStartDate,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("From: ${dateFormatter.format(Date(customStartMillis))}", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onPickEndDate,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("To: ${dateFormatter.format(Date(customEndMillis))}", fontSize = 11.sp)
                    }
                }
            }

            // Vehicle Filter Chips
            Text("Vehicle:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val vehicleOptions = listOf("All Vehicles") + vehicles.map { it.name }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                vehicleOptions.forEach { opt ->
                    FilterChip(
                        selected = selectedVehicleFilter == opt,
                        onClick = { onSelectVehicle(opt) },
                        label = { Text(opt, fontSize = 11.sp) }
                    )
                }
            }

            // Trip Type Multi-Select Filter Chips (reads from Room Trip Types table by ID)
            Text(
                "Trip Types (Multi-select • Reads from Settings > Trip Types):",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val allSelected = selectedTripTypeIds.isEmpty()
                FilterChip(
                    selected = allSelected,
                    onClick = { onToggleTripTypeId(null) },
                    label = { Text("All Trip Types", fontSize = 11.sp) },
                    leadingIcon = if (allSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null
                )

                tripTypes.forEach { tt ->
                    val isSelected = selectedTripTypeIds.contains(tt.id)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleTripTypeId(tt.id) },
                        label = { Text(tt.name, fontSize = 11.sp) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }

                val unclassifiedSelected = selectedTripTypeIds.contains(IrsPdfReportGenerator.UNCLASSIFIED_TRIP_TYPE_ID)
                FilterChip(
                    selected = unclassifiedSelected,
                    onClick = { onToggleTripTypeId(IrsPdfReportGenerator.UNCLASSIFIED_TRIP_TYPE_ID) },
                    label = { Text("Unclassified", fontSize = 11.sp) },
                    leadingIcon = if (unclassifiedSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InAppReportScreenDialog(
    filteredTrips: List<Trip>,
    vehicles: List<Vehicle>,
    tripTypes: List<CustomTripType>,
    tripTypesById: Map<String, CustomTripType>,
    selectedPeriod: IrsPdfReportGenerator.ReportPeriod,
    onSelectPeriod: (IrsPdfReportGenerator.ReportPeriod) -> Unit,
    customStartMillis: Long,
    customEndMillis: Long,
    onPickStartDate: () -> Unit,
    onPickEndDate: () -> Unit,
    selectedVehicleFilter: String,
    onSelectVehicle: (String) -> Unit,
    selectedTripTypeIds: Set<String>,
    onToggleTripTypeId: (String?) -> Unit,
    dateFormatter: SimpleDateFormat,
    onTripClick: (Trip) -> Unit,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    onDismiss: () -> Unit
) {
    val totalMiles = filteredTrips.sumOf { it.distanceMiles }
    val businessTrips = filteredTrips.filter { IrsPdfReportGenerator.isBusinessTrip(it, tripTypesById) }
    val businessMiles = businessTrips.sumOf { it.distanceMiles }
    val personalMiles = (totalMiles - businessMiles).coerceAtLeast(0.0)
    val totalDeduction = filteredTrips.sumOf {
        IrsPdfReportGenerator.computeEffectiveDeductionForTrip(it, tripTypesById)
    }

    // Breakdown grouped by Trip Type ID (deleted/missing types -> Unclassified)
    val breakdownByTripType = remember(filteredTrips, tripTypesById) {
        filteredTrips.groupBy { trip ->
            IrsPdfReportGenerator.resolveTripTypeId(trip, tripTypesById)
        }.map { (typeId, groupTrips) ->
            val typeName = if (typeId == IrsPdfReportGenerator.UNCLASSIFIED_TRIP_TYPE_ID) {
                "Unclassified"
            } else {
                tripTypesById[typeId]?.name ?: "Unclassified"
            }
            val miles = groupTrips.sumOf { it.distanceMiles }
            val deduction = groupTrips.sumOf { IrsPdfReportGenerator.computeEffectiveDeductionForTrip(it, tripTypesById) }
            Triple(typeName, groupTrips.size, miles to deduction)
        }.sortedByDescending { it.third.first }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Mileage & Tax Report", fontWeight = FontWeight.Bold)
                            Text(
                                "${filteredTrips.size} trips • %.1f total miles".format(totalMiles),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("in_app_report_back_button")) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close Report")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    ReportFiltersSection(
                        selectedPeriod = selectedPeriod,
                        onSelectPeriod = onSelectPeriod,
                        customStartMillis = customStartMillis,
                        customEndMillis = customEndMillis,
                        onPickStartDate = onPickStartDate,
                        onPickEndDate = onPickEndDate,
                        vehicles = vehicles,
                        selectedVehicleFilter = selectedVehicleFilter,
                        onSelectVehicle = onSelectVehicle,
                        tripTypes = tripTypes,
                        selectedTripTypeIds = selectedTripTypeIds,
                        onToggleTripTypeId = onToggleTripTypeId,
                        dateFormatter = dateFormatter
                    )
                }

                // Summary Cards (Total Miles, Business Miles, Personal Miles, Estimated Deduction)
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Report Summary", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SummaryMetricBox(
                                    label = "Total Miles",
                                    value = "%.1f mi".format(totalMiles),
                                    modifier = Modifier.weight(1f)
                                )
                                SummaryMetricBox(
                                    label = "Business Miles",
                                    value = "%.1f mi".format(businessMiles),
                                    highlight = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SummaryMetricBox(
                                    label = "Personal Miles",
                                    value = "%.1f mi".format(personalMiles),
                                    modifier = Modifier.weight(1f)
                                )
                                SummaryMetricBox(
                                    label = "Est. Deduction",
                                    value = "$%.2f".format(totalDeduction),
                                    highlight = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Export CSV & PDF Summary buttons inside In-App Report using identical filters
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onExportCsv,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("in_app_report_export_csv_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export CSV")
                        }
                        OutlinedButton(
                            onClick = onExportPdf,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("in_app_report_pdf_summary_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF Summary")
                        }
                    }
                }

                // Breakdown by Trip Type
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Breakdown by Trip Type", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            if (breakdownByTripType.isEmpty()) {
                                Text(
                                    "No trips match the selected filters.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                breakdownByTripType.forEachIndexed { idx, (typeName, count, stats) ->
                                    if (idx > 0) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(typeName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            Text("$count ${if (count == 1) "trip" else "trips"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("%.1f mi".format(stats.first), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                                            Text("$%.2f deduction".format(stats.second), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Trips List Header
                item {
                    Text(
                        text = "Matching Trips (${filteredTrips.size}) — Tap a trip for details",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (filteredTrips.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No trips found for the selected date range, trip type, and vehicle filters.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else {
                    items(filteredTrips, key = { it.id }) { trip ->
                        val resolvedTypeName = IrsPdfReportGenerator.resolveTripTypeName(trip, tripTypesById)
                        val effectiveDeduction = IrsPdfReportGenerator.computeEffectiveDeductionForTrip(trip, tripTypesById)
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTripClick(trip) }
                                .testTag("report_trip_row_${trip.id}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateFormatter.format(Date(trip.dateTimestamp)),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = resolvedTypeName,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Text(
                                            text = "%.1f mi".format(trip.distanceMiles),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }

                                Text(
                                    text = "${trip.startAddress.ifBlank { trip.title }} → ${trip.endAddress.ifBlank { "Destination" }}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = trip.vehicleName?.ifBlank { "Primary Vehicle" } ?: "Primary Vehicle",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = if (effectiveDeduction > 0.0) "Est. Deduction: $%.2f".format(effectiveDeduction) else "Personal ($0.00)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (effectiveDeduction > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom navigation bar clearance spacer so bottom items are never covered
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricBox(
    label: String,
    value: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (highlight) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
