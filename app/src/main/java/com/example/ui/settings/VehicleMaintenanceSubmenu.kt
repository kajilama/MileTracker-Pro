package com.example.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ExpenseReceipt
import com.example.data.model.ServiceReminder
import com.example.data.model.Vehicle
import com.example.data.repository.MileageRepository
import com.example.data.service.NhtsaVinService
import com.example.data.util.VehicleFuelTypeLookup
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleMaintenanceSubmenu(
    vehicles: List<Vehicle>,
    repository: MileageRepository,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allReceipts by repository.allReceipts.collectAsState(initial = emptyList())
    val allReminders by repository.allReminders.collectAsState(initial = emptyList())

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedVehicleId by remember { mutableStateOf<String?>(vehicles.firstOrNull { it.isDefault }?.id ?: vehicles.firstOrNull()?.id) }

    val activeVehicle = vehicles.firstOrNull { it.id == selectedVehicleId } ?: vehicles.firstOrNull()

    // Ensure the active vehicle's reminders strictly match its canonical fuel type
    LaunchedEffect(activeVehicle?.id, activeVehicle?.fuelType) {
        if (activeVehicle != null) {
            repository.syncServiceRemindersForFuelType(activeVehicle.id, activeVehicle.fuelType)
        }
    }

    var showVehicleDialog by remember { mutableStateOf(false) }
    var vehicleToEdit by remember { mutableStateOf<Vehicle?>(null) }
    var vehicleToDelete by remember { mutableStateOf<Vehicle?>(null) }

    var showReminderDialog by remember { mutableStateOf(false) }
    var reminderToEdit by remember { mutableStateOf<ServiceReminder?>(null) }
    var reminderToComplete by remember { mutableStateOf<ServiceReminder?>(null) }

    var showExpenseDialog by remember { mutableStateOf(false) }

    // Deletion Dialog
    if (vehicleToDelete != null) {
        AlertDialog(
            onDismissRequest = { vehicleToDelete = null },
            title = { Text("Delete Vehicle?") },
            text = { Text("Are you sure you want to remove '${vehicleToDelete?.name}'? Associated service reminders will also be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        val v = vehicleToDelete
                        vehicleToDelete = null
                        if (v != null) {
                            coroutineScope.launch {
                                repository.deleteVehicle(v)
                                onShowMessage("Removed vehicle: ${v.name}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { vehicleToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Vehicle Selector Chips
        if (vehicles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vehicles.forEach { v ->
                    val isSelected = v.id == selectedVehicleId
                    val normalizedFuel = VehicleFuelTypeLookup.normalizeFuelType(v.fuelType)
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedVehicleId = v.id },
                        label = {
                            Text(
                                text = if (v.isDefault) "${v.name} (${normalizedFuel}) ★" else "${v.name} (${normalizedFuel})",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (normalizedFuel == "Electric" || normalizedFuel == "Plug-in Hybrid") Icons.Default.ElectricCar else Icons.Default.DirectionsCar,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        // Two Tab Navigation: [Vehicles & Service Reminders] / [Expenses & Cost Per Mile]
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Vehicles & Schedules") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Expenses & Cost/Mi") }
            )
        }

        when (selectedTabIndex) {
            0 -> {
                // TAB 0: VEHICLES & SERVICE REMINDERS
                if (activeVehicle != null) {
                    VehicleProfileCard(
                        vehicle = activeVehicle,
                        onEdit = {
                            vehicleToEdit = activeVehicle
                            showVehicleDialog = true
                        },
                        onDelete = { vehicleToDelete = activeVehicle },
                        onSetDefault = {
                            coroutineScope.launch {
                                repository.saveVehicle(activeVehicle.copy(isDefault = true))
                                onShowMessage("${activeVehicle.name} set as primary vehicle")
                            }
                        },
                        onUpdateOdometer = { newOdo ->
                            coroutineScope.launch {
                                repository.saveVehicle(activeVehicle.copy(currentOdometer = newOdo))
                                onShowMessage("Updated odometer to %.0f mi".format(newOdo))
                            }
                        }
                    )

                    // Reminders Section for Active Vehicle only, strictly filtered by fuel type
                    val normalizedActiveFuel = VehicleFuelTypeLookup.normalizeFuelType(activeVehicle.fuelType)
                    val allowedTemplates = repository.getCanonicalServiceItemsForFuelType(normalizedActiveFuel)
                    val allowedTitlesLower = allowedTemplates.map { it.second.lowercase() }.toSet()
                    val vehicleReminders = allReminders.filter { reminder ->
                        if (reminder.vehicleId != activeVehicle.id) return@filter false
                        val titleLower = reminder.title.lowercase()
                        if (normalizedActiveFuel == "Electric") {
                            // Never show engine oil, oil filter, transmission fluid, spark plugs, engine air filter, or engine coolant for an EV
                            val isDisallowedForEv = titleLower.contains("oil") ||
                                titleLower.contains("transmission") ||
                                titleLower.contains("spark plug") ||
                                titleLower == "air filter" ||
                                titleLower.contains("engine air") ||
                                titleLower == "coolant" ||
                                titleLower.contains("engine coolant") ||
                                titleLower.contains("inverter coolant")
                            !isDisallowedForEv
                        } else {
                            allowedTitlesLower.contains(titleLower) || reminder.fuelTypeApplicability.equals(normalizedActiveFuel, ignoreCase = true)
                        }
                    }
                    ServiceRemindersCard(
                        vehicle = activeVehicle,
                        reminders = vehicleReminders,
                        onAddReminder = {
                            reminderToEdit = null
                            showReminderDialog = true
                        },
                        onMarkDone = { reminder ->
                            reminderToComplete = reminder
                        },
                        onDeleteReminder = { reminder ->
                            coroutineScope.launch {
                                repository.deleteReminder(reminder)
                                onShowMessage("Removed reminder: ${reminder.title}")
                            }
                        }
                    )

                    // Official Online Manuals Card
                    OfficialManualsCard(
                        vehicle = activeVehicle,
                        onShowMessage = onShowMessage
                    )
                }

                Button(
                    onClick = {
                        vehicleToEdit = null
                        showVehicleDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Another Vehicle (VIN & US Models)")
                }
            }

            1 -> {
                // TAB 1: EXPENSES & COST PER MILE
                val vehicleReceipts = if (activeVehicle != null) {
                    allReceipts.filter { it.vehicleId == activeVehicle.id }
                } else allReceipts

                VehicleExpensesCard(
                    vehicle = activeVehicle,
                    receipts = vehicleReceipts,
                    onAddExpense = { showExpenseDialog = true },
                    onDeleteExpense = { receipt ->
                        coroutineScope.launch {
                            repository.deleteReceipt(receipt)
                            onShowMessage("Deleted expense receipt")
                        }
                    }
                )
            }
        }
    }

    // Vehicle Add/Edit Dialog with NHTSA VIN Decoder & US Dropdowns
    if (showVehicleDialog) {
        VehicleAddEditDialog(
            vehicleToEdit = vehicleToEdit,
            onDismiss = { showVehicleDialog = false },
            onSave = { vehicle ->
                coroutineScope.launch {
                    repository.saveVehicle(vehicle)
                    selectedVehicleId = vehicle.id
                    onShowMessage("Saved vehicle profile: ${vehicle.name}")
                }
                showVehicleDialog = false
            }
        )
    }

    // Reminder Add Dialog
    if (showReminderDialog && activeVehicle != null) {
        ServiceReminderEditDialog(
            vehicle = activeVehicle,
            existing = reminderToEdit,
            onDismiss = { showReminderDialog = false },
            onSave = { reminder ->
                coroutineScope.launch {
                    repository.saveReminder(reminder)
                    onShowMessage("Saved reminder: ${reminder.title}")
                }
                showReminderDialog = false
            }
        )
    }

    // Complete Reminder Done Dialog -> Logs service & optional expense
    if (reminderToComplete != null && activeVehicle != null) {
        CompleteReminderDialog(
            reminder = reminderToComplete!!,
            vehicle = activeVehicle,
            onDismiss = { reminderToComplete = null },
            onLogDone = { updatedReminder, expense ->
                coroutineScope.launch {
                    repository.saveReminder(updatedReminder)
                    if (expense != null) {
                        repository.saveReceipt(expense)
                        onShowMessage("Logged service '${updatedReminder.title}' & expense $%.2f".format(expense.amount))
                    } else {
                        onShowMessage("Logged service '${updatedReminder.title}' done at %.0f mi".format(updatedReminder.lastPerformedOdo))
                    }
                }
                reminderToComplete = null
            }
        )
    }

    // Log Expense Dialog
    if (showExpenseDialog && activeVehicle != null) {
        ExpenseAddDialog(
            vehicle = activeVehicle,
            onDismiss = { showExpenseDialog = false },
            onSave = { expense ->
                coroutineScope.launch {
                    repository.saveReceipt(expense)
                    onShowMessage("Logged expense: ${expense.category} ($%.2f)".format(expense.amount))
                }
                showExpenseDialog = false
            }
        )
    }
}

// -------------------------------------------------------------
// VEHICLE PROFILE CARD (IRS Jan 1 / Dec 31 Odometer support)
// -------------------------------------------------------------
@Composable
private fun VehicleProfileCard(
    vehicle: Vehicle,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    onUpdateOdometer: (Double) -> Unit
) {
    var isUpdatingOdo by remember { mutableStateOf(false) }
    var newOdoText by remember { mutableStateOf("%.0f".format(vehicle.currentOdometer)) }

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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (vehicle.fuelType.contains("EV", ignoreCase = true)) Icons.Default.ElectricCar else Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(vehicle.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            if (vehicle.isDefault) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary) {
                                    Text("Primary", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Text("${vehicle.year} ${vehicle.makeModel} • ${vehicle.fuelType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Vehicle", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Vehicle", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }

            HorizontalDivider()

            // Profile Specs Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("License Plate", style = MaterialTheme.typography.labelSmall)
                        Text(vehicle.licensePlate.ifBlank { "Not set" }, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("VIN Number", style = MaterialTheme.typography.labelSmall)
                        Text(vehicle.vin.take(11).ifBlank { "Not set" }, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            // Odometer Row & Quick Update
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Current Odometer", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                            Text("%.0f miles".format(vehicle.currentOdometer), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        }
                        OutlinedButton(
                            onClick = { isUpdatingOdo = !isUpdatingOdo },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(if (isUpdatingOdo) "Done" else "Update Odo", fontSize = 11.sp)
                        }
                    }

                    if (isUpdatingOdo) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newOdoText,
                                onValueChange = { newOdoText = it },
                                label = { Text("New Odometer (miles)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val o = newOdoText.toDoubleOrNull()
                                    if (o != null) {
                                        onUpdateOdometer(o)
                                        isUpdatingOdo = false
                                    }
                                }
                            ) {
                                Text("Save")
                            }
                        }
                    }

                    // IRS Jan 1 and Dec 31 Odometers
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "IRS Jan 1 Odo: ${vehicle.odometerJan1?.let { "%.0f mi".format(it) } ?: "Not logged"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Dec 31 Odo: ${vehicle.odometerDec31?.let { "%.0f mi".format(it) } ?: "Not logged"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!vehicle.isDefault) {
                OutlinedButton(
                    onClick = onSetDefault,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Set as Primary Vehicle for Drives")
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SERVICE REMINDERS CARD (Gas vs EV generic starter templates)
// -------------------------------------------------------------
@Composable
private fun ServiceRemindersCard(
    vehicle: Vehicle,
    reminders: List<ServiceReminder>,
    onAddReminder: () -> Unit,
    onMarkDone: (ServiceReminder) -> Unit,
    onDeleteReminder: (ServiceReminder) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Maintenance & Service Reminders", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("Generic intervals — verify with your owner's manual", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onAddReminder) {
                    Icon(Icons.Default.Add, contentDescription = "Add Reminder", tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (reminders.isEmpty()) {
                Text(
                    "No reminders set. Tap '+' to create custom service intervals for this vehicle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                reminders.forEach { reminder ->
                    val milesSince = (vehicle.currentOdometer - reminder.lastPerformedOdo).coerceAtLeast(0.0)
                    val milesRemaining = (reminder.intervalMiles - milesSince)
                    val isDue = milesRemaining <= 0

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDue)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = if (isDue)
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (isDue) Icons.Default.Warning else Icons.Default.Build,
                                        contentDescription = null,
                                        tint = if (isDue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(reminder.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        Text(
                                            "Interval: %.0f mi or %d mos • Last at %.0f mi".format(
                                                reminder.intervalMiles,
                                                reminder.intervalMonths,
                                                reminder.lastPerformedOdo
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = { onMarkDone(reminder) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Mark Done", fontSize = 11.sp)
                                    }
                                    IconButton(onClick = { onDeleteReminder(reminder) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isDue) "SERVICE DUE NOW" else "Due in %.0f miles".format(milesRemaining),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    text = reminder.disclaimer,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// OFFICIAL MANUALS FAST ACCESS CARD
// -------------------------------------------------------------
@Composable
private fun OfficialManualsCard(
    vehicle: Vehicle,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Official Manufacturer Manuals & Portals", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
            Text(
                "Access online owner's manuals, warranty booklets, and maintenance schedules directly from the manufacturer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val oemPortals = listOf(
                "Toyota" to "https://www.toyota.com/owners/resources/warranty-owners-manuals",
                "Tesla" to "https://www.tesla.com/ownersmanual",
                "Ford" to "https://www.ford.com/support/owner-manuals/",
                "Honda" to "https://owners.honda.com/vehicle-information/manuals",
                "Chevrolet" to "https://www.chevrolet.com/support/vehicle/manuals-guides",
                "Hyundai" to "https://owners.hyundaiusa.com/us/en/resources/manuals-warranties.html",
                "Nissan" to "https://www.nissanusa.com/owners/ownership/manuals-guides.html"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                oemPortals.forEach { (oem, url) ->
                    val isCurrentOem = vehicle.oemBrand.equals(oem, ignoreCase = true) || vehicle.makeModel.contains(oem, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrentOem) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isCurrentOem) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    onShowMessage("Opened $oem Official Manual Portal: $url")
                                }
                            }
                            .padding(horizontal = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = oem,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isCurrentOem) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrentOem) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// EXPENSES & COST PER MILE CARD
// -------------------------------------------------------------
@Composable
private fun VehicleExpensesCard(
    vehicle: Vehicle?,
    receipts: List<ExpenseReceipt>,
    onAddExpense: () -> Unit,
    onDeleteExpense: (ExpenseReceipt) -> Unit
) {
    val totalExpenseAmount = receipts.sumOf { it.amount }
    val totalMilesDriven = if (vehicle != null && vehicle.currentOdometer > vehicle.startingOdometer) {
        (vehicle.currentOdometer - vehicle.startingOdometer)
    } else 1.0

    val costPerMile = totalExpenseAmount / totalMilesDriven.coerceAtLeast(1.0)
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Vehicle Expenses & Cost Per Mile", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("Fuel, repairs, insurance, tires, maintenance & receipts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onAddExpense,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Log Expense")
                }
            }

            // Summary Metrics Box
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Logged Expenses", style = MaterialTheme.typography.labelSmall)
                        Text("$%.2f".format(totalExpenseAmount), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Vehicle Cost Per Mile", style = MaterialTheme.typography.labelSmall)
                        Text("$%.2f/mi".format(costPerMile), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            HorizontalDivider()

            // Receipts List
            if (receipts.isEmpty()) {
                Text("No expenses logged yet. Tap 'Log Expense' to record fuel/charging, repairs, or insurance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                receipts.forEach { receipt ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(receipt.category, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("$%.2f".format(receipt.amount), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                                }
                                Text(
                                    "${dateFormat.format(Date(receipt.dateTimestamp))} • ${receipt.merchant.ifBlank { "Direct" }} ${receipt.odometer?.let { "• %.0f mi".format(it) } ?: ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (receipt.notes.isNotBlank()) {
                                    Text(receipt.notes, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { onDeleteExpense(receipt) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// VEHICLE ADD / EDIT DIALOG (WITH NHTSA VIN DECODER & US DROPDOWNS)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleAddEditDialog(
    vehicleToEdit: Vehicle?,
    onDismiss: () -> Unit,
    onSave: (Vehicle) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(vehicleToEdit?.name ?: "") }
    var vin by remember { mutableStateOf(vehicleToEdit?.vin ?: "") }
    var year by remember { mutableStateOf(vehicleToEdit?.year ?: "2024") }
    var make by remember { mutableStateOf(vehicleToEdit?.make ?: "Toyota") }
    var model by remember { mutableStateOf(vehicleToEdit?.model ?: "RAV4 Hybrid") }
    var fuelType by remember {
        mutableStateOf(
            if (vehicleToEdit != null) {
                VehicleFuelTypeLookup.normalizeFuelType(vehicleToEdit.fuelType)
            } else {
                VehicleFuelTypeLookup.suggestFuelType("Toyota", "RAV4 Hybrid")
            }
        )
    }
    var userManuallyChangedFuelType by remember { mutableStateOf(false) }
    var licensePlate by remember { mutableStateOf(vehicleToEdit?.licensePlate ?: "") }
    var startingOdo by remember { mutableStateOf(vehicleToEdit?.startingOdometer?.toInt()?.toString() ?: "15000") }
    var currentOdo by remember { mutableStateOf(vehicleToEdit?.currentOdometer?.toInt()?.toString() ?: "15000") }
    var odoJan1 by remember { mutableStateOf(vehicleToEdit?.odometerJan1?.toInt()?.toString() ?: "") }
    var odoDec31 by remember { mutableStateOf(vehicleToEdit?.odometerDec31?.toInt()?.toString() ?: "") }
    var oemBrand by remember { mutableStateOf(vehicleToEdit?.oemBrand ?: "Toyota") }

    var isDecodingVin by remember { mutableStateOf(false) }
    var vinDecodeStatus by remember { mutableStateOf<String?>(null) }

    // Dropdown expanded states
    var makeExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }
    var fuelExpanded by remember { mutableStateOf(false) }

    val availableModels = NhtsaVinService.POPULAR_MODELS_BY_MAKE[make] ?: listOf("Sedan", "SUV", "Truck", "Van")

    fun autoSuggestFuelIfNotOverridden(newMake: String, newModel: String) {
        if (!userManuallyChangedFuelType) {
            fuelType = VehicleFuelTypeLookup.suggestFuelType(newMake, newModel)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (vehicleToEdit == null) "Add Vehicle Profile" else "Edit Vehicle Profile") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Vehicle Nickname
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Vehicle Nickname (e.g. Primary Car, Delivery EV)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // NHTSA VIN Field with Automatic Decoder Button
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = vin,
                            onValueChange = { vin = it.uppercase() },
                            label = { Text("VIN (Vehicle Identification Number)") },
                            placeholder = { Text("17-character VIN") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (vin.length >= 11) {
                                    isDecodingVin = true
                                    vinDecodeStatus = "Contacting NHTSA vPIC..."
                                    coroutineScope.launch {
                                        val result = NhtsaVinService.decodeVin(vin)
                                        isDecodingVin = false
                                        result.onSuccess { info ->
                                            if (info.year.isNotBlank()) year = info.year
                                            if (info.make.isNotBlank()) {
                                                make = info.make
                                                oemBrand = info.make
                                            }
                                            if (info.model.isNotBlank()) model = info.model
                                            if (!userManuallyChangedFuelType) {
                                                fuelType = if (info.fuelType.isNotBlank()) {
                                                    VehicleFuelTypeLookup.normalizeFuelType(info.fuelType)
                                                } else {
                                                    VehicleFuelTypeLookup.suggestFuelType(make, model)
                                                }
                                            }
                                            vinDecodeStatus = "✓ Decoded: ${info.year} ${info.make} ${info.model} ($fuelType)"
                                        }.onFailure { err ->
                                            vinDecodeStatus = "Lookup notice: ${err.message}"
                                        }
                                    }
                                } else {
                                    vinDecodeStatus = "Enter at least 11 VIN characters to decode"
                                }
                            },
                            enabled = !isDecodingVin && vin.length >= 11,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isDecodingVin) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Text("Decode VIN", fontSize = 11.sp)
                            }
                        }
                    }
                    if (vinDecodeStatus != null) {
                        Text(
                            text = vinDecodeStatus!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (vinDecodeStatus!!.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Make Dropdown (supports free-text + dropdown selection)
                ExposedDropdownMenuBox(expanded = makeExpanded, onExpandedChange = { makeExpanded = it }) {
                    OutlinedTextField(
                        value = make,
                        onValueChange = {
                            make = it
                            oemBrand = it
                            autoSuggestFuelIfNotOverridden(it, model)
                        },
                        label = { Text("Make (Pre-filled US Models or Free Text)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = makeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                    )
                    ExposedDropdownMenu(expanded = makeExpanded, onDismissRequest = { makeExpanded = false }) {
                        NhtsaVinService.POPULAR_US_MAKES.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    make = m
                                    oemBrand = m
                                    val firstModel = NhtsaVinService.POPULAR_MODELS_BY_MAKE[m]?.firstOrNull() ?: ""
                                    if (firstModel.isNotBlank()) model = firstModel
                                    autoSuggestFuelIfNotOverridden(m, model)
                                    makeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Model Dropdown (supports full model name lookup + free-text keyword matching)
                ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = it }) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = {
                            model = it
                            autoSuggestFuelIfNotOverridden(make, it)
                        },
                        label = { Text("Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                    )
                    ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        availableModels.forEach { md ->
                            DropdownMenuItem(
                                text = { Text(md) },
                                onClick = {
                                    model = md
                                    autoSuggestFuelIfNotOverridden(make, md)
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }

                // Year & Fuel Type Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Year
                    ExposedDropdownMenuBox(
                        expanded = yearExpanded,
                        onExpandedChange = { yearExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = year,
                            onValueChange = {
                                year = it
                                autoSuggestFuelIfNotOverridden(make, model)
                            },
                            label = { Text("Year") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryEditable)
                        )
                        ExposedDropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                            NhtsaVinService.MODEL_YEARS.forEach { yr ->
                                DropdownMenuItem(
                                    text = { Text(yr) },
                                    onClick = {
                                        year = yr
                                        autoSuggestFuelIfNotOverridden(make, model)
                                        yearExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Fuel Type (Gas, Hybrid, Plug-in Hybrid, Electric)
                    ExposedDropdownMenuBox(
                        expanded = fuelExpanded,
                        onExpandedChange = { fuelExpanded = it },
                        modifier = Modifier.weight(1.3f)
                    ) {
                        OutlinedTextField(
                            value = fuelType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Fuel Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fuelExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = fuelExpanded, onDismissRequest = { fuelExpanded = false }) {
                            VehicleFuelTypeLookup.FUEL_TYPES.forEach { ft ->
                                DropdownMenuItem(
                                    text = { Text(ft) },
                                    onClick = {
                                        fuelType = ft
                                        userManuallyChangedFuelType = true
                                        fuelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = if (userManuallyChangedFuelType) {
                        "Fuel Type manually set to $fuelType (will not be overwritten by Make/Model edits)."
                    } else {
                        "Fuel Type auto-selected as $fuelType for $make $model (you can change it manually above)."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // License Plate
                OutlinedTextField(
                    value = licensePlate,
                    onValueChange = { licensePlate = it.uppercase() },
                    label = { Text("License Plate") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Starting & Current Odometer
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startingOdo,
                        onValueChange = { startingOdo = it },
                        label = { Text("Start Odo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = currentOdo,
                        onValueChange = { currentOdo = it },
                        label = { Text("Current Odo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                // IRS Jan 1 & Dec 31 Odometer figures
                Text("IRS Tax Year Mileage (Optional):", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = odoJan1,
                        onValueChange = { odoJan1 = it },
                        label = { Text("Jan 1 Odo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = odoDec31,
                        onValueChange = { odoDec31 = it },
                        label = { Text("Dec 31 Odo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val effectiveName = name.trim().ifBlank { "$year $make $model".trim() }
                    val v = Vehicle(
                        id = vehicleToEdit?.id ?: UUID.randomUUID().toString(),
                        name = effectiveName,
                        make = make.trim(),
                        model = model.trim(),
                        makeModel = "$make $model".trim(),
                        year = year.trim(),
                        fuelType = VehicleFuelTypeLookup.normalizeFuelType(fuelType.trim()),
                        licensePlate = licensePlate.trim(),
                        vin = vin.trim(),
                        startingOdometer = startingOdo.toDoubleOrNull() ?: 0.0,
                        currentOdometer = currentOdo.toDoubleOrNull() ?: startingOdo.toDoubleOrNull() ?: 0.0,
                        odometerJan1 = odoJan1.toDoubleOrNull(),
                        odometerDec31 = odoDec31.toDoubleOrNull(),
                        oemBrand = oemBrand,
                        isDefault = vehicleToEdit?.isDefault ?: false
                    )
                    onSave(v)
                }
            ) {
                Text("Save Vehicle")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// -------------------------------------------------------------
// SERVICE REMINDER ADD / EDIT DIALOG
// -------------------------------------------------------------
@Composable
private fun ServiceReminderEditDialog(
    vehicle: Vehicle,
    existing: ServiceReminder?,
    onDismiss: () -> Unit,
    onSave: (ServiceReminder) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var intervalMilesText by remember { mutableStateOf(existing?.intervalMiles?.toInt()?.toString() ?: "5000") }
    var intervalMonthsText by remember { mutableStateOf(existing?.intervalMonths?.toString() ?: "6") }
    var lastOdoText by remember { mutableStateOf(existing?.lastPerformedOdo?.toInt()?.toString() ?: vehicle.currentOdometer.toInt().toString()) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Service Reminder" else "Edit Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Service Title (e.g. Tire Rotation, Brake Fluid)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = intervalMilesText,
                        onValueChange = { intervalMilesText = it },
                        label = { Text("Interval (Miles)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = intervalMonthsText,
                        onValueChange = { intervalMonthsText = it },
                        label = { Text("Months") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = lastOdoText,
                    onValueChange = { lastOdoText = it },
                    label = { Text("Last Done at Odometer (Miles)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Part numbers, fluid specs)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val reminder = ServiceReminder(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            vehicleId = vehicle.id,
                            title = title.trim(),
                            intervalMiles = intervalMilesText.toDoubleOrNull() ?: 5000.0,
                            intervalMonths = intervalMonthsText.toIntOrNull() ?: 6,
                            lastPerformedOdo = lastOdoText.toDoubleOrNull() ?: vehicle.currentOdometer,
                            lastPerformedDate = System.currentTimeMillis(),
                            fuelTypeApplicability = vehicle.fuelType,
                            notes = notes.trim()
                        )
                        onSave(reminder)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// -------------------------------------------------------------
// COMPLETE REMINDER DIALOG (LOG SERVICE & OPTIONAL EXPENSE)
// -------------------------------------------------------------
@Composable
private fun CompleteReminderDialog(
    reminder: ServiceReminder,
    vehicle: Vehicle,
    onDismiss: () -> Unit,
    onLogDone: (ServiceReminder, ExpenseReceipt?) -> Unit
) {
    var completedOdoText by remember { mutableStateOf("%.0f".format(vehicle.currentOdometer)) }
    var recordExpense by remember { mutableStateOf(false) }
    var expenseAmountText by remember { mutableStateOf("") }
    var merchantText by remember { mutableStateOf("") }
    var expenseNotes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Service Done") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Logging '${reminder.title}' as completed will reset the reminder interval from this odometer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = completedOdoText,
                    onValueChange = { completedOdoText = it },
                    label = { Text("Service Odometer (miles)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = recordExpense,
                        onCheckedChange = { recordExpense = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Also log as a vehicle tax expense", style = MaterialTheme.typography.bodyMedium)
                }

                if (recordExpense) {
                    OutlinedTextField(
                        value = expenseAmountText,
                        onValueChange = { expenseAmountText = it },
                        label = { Text("Cost / Amount ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = merchantText,
                        onValueChange = { merchantText = it },
                        label = { Text("Shop / Store / Mechanic (e.g. Valvoline, JiffyLube, DIY)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = expenseNotes,
                        onValueChange = { expenseNotes = it },
                        label = { Text("Notes / Invoice #") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val odo = completedOdoText.toDoubleOrNull() ?: vehicle.currentOdometer
                    val updated = reminder.copy(
                        lastPerformedOdo = odo,
                        lastPerformedDate = System.currentTimeMillis()
                    )

                    val expense = if (recordExpense && (expenseAmountText.toDoubleOrNull() ?: 0.0) > 0) {
                        ExpenseReceipt(
                            id = UUID.randomUUID().toString(),
                            vehicleId = vehicle.id,
                            category = if (reminder.title.contains("Tire", ignoreCase = true)) "Tires" else "Maintenance",
                            amount = expenseAmountText.toDoubleOrNull() ?: 0.0,
                            dateTimestamp = System.currentTimeMillis(),
                            odometer = odo,
                            serviceReminderId = reminder.id,
                            merchant = merchantText.trim(),
                            notes = "Service: ${reminder.title}. ${expenseNotes.trim()}".trim()
                        )
                    } else null

                    onLogDone(updated, expense)
                }
            ) {
                Text("Confirm Done")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// -------------------------------------------------------------
// EXPENSE ADD DIALOG
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseAddDialog(
    vehicle: Vehicle,
    onDismiss: () -> Unit,
    onSave: (ExpenseReceipt) -> Unit
) {
    var category by remember { mutableStateOf("Fuel / Charging") }
    var amountText by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var odoText by remember { mutableStateOf("%.0f".format(vehicle.currentOdometer)) }
    var notes by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Vehicle Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Expense Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        ExpenseReceipt.ALL_CATEGORIES.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant / Gas Station / Vendor") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = odoText,
                    onValueChange = { odoText = it },
                    label = { Text("Odometer Reading") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Gallons, kWh, oil type)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        val receipt = ExpenseReceipt(
                            id = UUID.randomUUID().toString(),
                            vehicleId = vehicle.id,
                            category = category,
                            amount = amt,
                            dateTimestamp = System.currentTimeMillis(),
                            odometer = odoText.toDoubleOrNull(),
                            merchant = merchant.trim(),
                            notes = notes.trim()
                        )
                        onSave(receipt)
                    }
                }
            ) {
                Text("Save Expense")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
