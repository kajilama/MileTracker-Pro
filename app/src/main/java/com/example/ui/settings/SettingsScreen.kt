package com.example.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Store
import com.example.data.model.AddressSuggestion
import com.example.data.model.AutoClassificationRule
import com.example.data.model.CustomTripType
import com.example.data.model.SavedPlace
import com.example.data.model.UserProfile
import com.example.data.model.Vehicle
import com.example.data.repository.MileageRepository
import com.example.ui.components.GoogleDriveSyncCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.filled.Palette
import com.example.ui.theme.ThemePresets
import com.example.ui.settings.ThemePickerContent

enum class SettingsSubmenu(val title: String) {
    NONE("Settings"),
    PERMISSIONS_HEALTH("Permissions & Tracking Health"),
    TRACKING_DETECTION("Tracking & Drive Detection"),
    TRIP_TYPES("Trip Types & Rates"),
    SAVED_ADDRESSES("Saved Places"),
    AUTO_CLASSIFICATION("Auto Classification Rules"),
    VEHICLE_MAINTENANCE("Vehicle & Maintenance"),
    THEMES("Themes & Color Schemes"),
    FONT_ZOOM("Display Zoom & Font Sizes"),
    DRIVER_PROFILE("Driver Profile"),
    IRS_REPORTS("IRS Mileage Reports & CSV"),
    KNOWLEDGE_BASE("Knowledge Base"),
    ABOUT_APP("About App")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userProfile: UserProfile,
    vehicles: List<Vehicle>,
    repository: MileageRepository,
    onBack: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val savedPlaces by repository.allSavedPlaces.collectAsState(initial = emptyList())
    val tripTypes by repository.allTripTypes.collectAsState(initial = emptyList())
    val rules by repository.allRules.collectAsState(initial = emptyList())

    var activeSubmenu by remember { mutableStateOf(SettingsSubmenu.NONE) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign Out?") },
            text = { Text("Are you sure you want to sign out of ${if (userProfile.isSignedInWithGoogle) userProfile.email else "your account"}? Your cloud backup and sync will be paused until you sign back in.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Intercept hardware and gesture back when inside any submenu
    BackHandler {
        if (activeSubmenu != SettingsSubmenu.NONE) {
            activeSubmenu = SettingsSubmenu.NONE
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeSubmenu.title,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeSubmenu != SettingsSubmenu.NONE) {
                                activeSubmenu = SettingsSubmenu.NONE
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (activeSubmenu) {
                SettingsSubmenu.NONE -> {
                    // Google Drive Sync Card at top of Settings
                    GoogleDriveSyncCard(
                        userProfile = userProfile,
                        repository = repository,
                        onSignInClicked = onSignInWithGoogle,
                        onSignOutClicked = onSignOut,
                        onShowMessage = onShowMessage
                    )

                    // 1. DRIVE TRACKING & TELEMETRY
                    SettingsCategoryHeader("Drive Tracking & Telemetry")
                    SettingsNavigationTile(
                        number = 1,
                        icon = Icons.Default.VerifiedUser,
                        title = "Permissions & Tracking Health",
                        subtitle = "Live permissions checklist • GPS hardware • Diagnostics log • System repair actions",
                        onClick = { activeSubmenu = SettingsSubmenu.PERMISSIONS_HEALTH }
                    )
                    SettingsNavigationTile(
                        number = 2,
                        icon = Icons.Default.Speed,
                        title = "Tracking & Drive Detection",
                        subtitle = "Minutes per stop (${userProfile.minutesPerDropoffStop}m) • Inactivity (${userProfile.inactivityTimeoutMinutes}m) • Speed (${userProfile.speedTriggerThresholdMph} mph)",
                        onClick = { activeSubmenu = SettingsSubmenu.TRACKING_DETECTION }
                    )

                    // 2. TRIP TYPES
                    val resolvedDefaultTripTypeName = tripTypes.firstOrNull { it.id == userProfile.defaultTripTypeId }?.name
                        ?: userProfile.defaultTripTypeName
                    SettingsCategoryHeader("Tax Categories & Classifications")
                    SettingsNavigationTile(
                        number = 2,
                        icon = Icons.Default.Category,
                        title = "Trip Types",
                        subtitle = "${tripTypes.size} types • Default for new drives: $resolvedDefaultTripTypeName",
                        onClick = { activeSubmenu = SettingsSubmenu.TRIP_TYPES }
                    )

                    // 3. SAVED PLACES
                    SettingsNavigationTile(
                        number = 3,
                        icon = Icons.Default.Place,
                        title = "Saved Places",
                        subtitle = "${savedPlaces.size} locations • Home, Walmart Supercenters, Albertsons, Vons, Medical",
                        onClick = { activeSubmenu = SettingsSubmenu.SAVED_ADDRESSES }
                    )

                    // 4. AUTO CLASSIFICATION RULES
                    SettingsNavigationTile(
                        number = 4,
                        icon = Icons.Default.Rule,
                        title = "Auto Classification Rules",
                        subtitle = "${rules.size} active rules • Selectable label buttons with '|' divider & multi-store support",
                        onClick = { activeSubmenu = SettingsSubmenu.AUTO_CLASSIFICATION }
                    )

                    // 5. VEHICLE AND MAINTENANCE
                    SettingsCategoryHeader("Vehicles & Maintenance")
                    SettingsNavigationTile(
                        number = 5,
                        icon = Icons.Default.DirectionsCar,
                        title = "Vehicle and Maintenance",
                        subtitle = "${vehicles.size} vehicles • NHTSA VIN decode • Gas vs EV schedules • Cost/Mi expenses",
                        onClick = { activeSubmenu = SettingsSubmenu.VEHICLE_MAINTENANCE }
                    )

                    // 6. THEMES & COLOR SCHEMES
                    SettingsCategoryHeader("Display & Appearance")
                    val currentThemePreset = ThemePresets.getPreset(userProfile.themePreset)
                    SettingsNavigationTile(
                        number = 6,
                        icon = Icons.Default.Palette,
                        title = "Themes & Color Schemes",
                        subtitle = "${currentThemePreset.name} active • 15 Handcrafted themes with live preview",
                        onClick = { activeSubmenu = SettingsSubmenu.THEMES }
                    )

                    // 7. DISPLAY ZOOM AND FONT SIZES
                    SettingsNavigationTile(
                        number = 7,
                        icon = Icons.Default.FormatSize,
                        title = "Display Zoom and Font Sizes",
                        subtitle = "${userProfile.fontPoints} pt font • ${userProfile.screenZoomPercent}% screen zoom • Anti-overflow layout",
                        onClick = { activeSubmenu = SettingsSubmenu.FONT_ZOOM }
                    )

                    // 8. DRIVER PROFILE
                    SettingsCategoryHeader("Account & Reports")
                    SettingsNavigationTile(
                        number = 8,
                        icon = Icons.Default.Person,
                        title = "Driver Profile",
                        subtitle = "${userProfile.displayName} • ${userProfile.email} • 1099 Contractor status",
                        onClick = { activeSubmenu = SettingsSubmenu.DRIVER_PROFILE }
                    )

                    // 9. IRS MILEAGE REPORTS & CSV
                    SettingsNavigationTile(
                        number = 9,
                        icon = Icons.Default.FileDownload,
                        title = "IRS Mileage Reports & Compliant CSV",
                        subtitle = "Pub 463 notice • 21-column contemporaneous CSV • Annual Odometer • Rate $%.2f/mi".format(userProfile.irsBusinessRate),
                        onClick = { activeSubmenu = SettingsSubmenu.IRS_REPORTS }
                    )

                    // 10. KNOWLEDGE BASE
                    SettingsNavigationTile(
                        number = 10,
                        icon = Icons.Default.Book,
                        title = "Knowledge Base",
                        subtitle = "8 Searchable sections • 100% Offline • IRS rules, EV vs Gas, and diagnostics",
                        onClick = { activeSubmenu = SettingsSubmenu.KNOWLEDGE_BASE }
                    )

                    // 11. ABOUT APP & DIAGNOSTICS
                    SettingsCategoryHeader("About & Diagnostics")
                    SettingsNavigationTile(
                        number = 11,
                        icon = Icons.Default.Info,
                        title = "About App",
                        subtitle = "MileTracker Pro v1.0.6 (Build 6) • Diagnostics & Debug Log Export",
                        onClick = { activeSubmenu = SettingsSubmenu.ABOUT_APP }
                    )

                    SettingsCategoryHeader("Account Session")
                    if (userProfile.isSignedInWithGoogle) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSignOutConfirm = true }
                                .testTag("settings_sign_out_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Sign Out",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Sign Out (${userProfile.email})",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF4285F4).copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSignInWithGoogle() }
                                .testTag("settings_sign_in_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Login,
                                    contentDescription = "Sign In",
                                    tint = Color(0xFF4285F4),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Sign in with Google Account",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4285F4)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }

                SettingsSubmenu.PERMISSIONS_HEALTH -> {
                    PermissionsHealthSubmenu(
                        onShowMessage = onShowMessage
                    )
                }

                // 1. TRACKING & DRIVE DETECTION SUBMENU
                SettingsSubmenu.TRACKING_DETECTION -> {
                    TrackingDetectionSubmenu(
                        userProfile = userProfile,
                        repository = repository,
                        onShowMessage = onShowMessage
                    )
                }

                // 2. TRIP TYPES SUBMENU
                SettingsSubmenu.TRIP_TYPES -> {
                    TripTypesSubmenu(
                        tripTypes = tripTypes,
                        repository = repository,
                        onShowMessage = onShowMessage
                    )
                }

                // 3. SAVED PLACES SUBMENU
                SettingsSubmenu.SAVED_ADDRESSES -> {
                    SavedPlacesSubmenu(
                        savedPlaces = savedPlaces,
                        repository = repository,
                        onShowMessage = onShowMessage
                    )
                }

                // 4. AUTO CLASSIFICATION RULES SUBMENU
                SettingsSubmenu.AUTO_CLASSIFICATION -> {
                    AutoClassificationSubmenu(
                        rules = rules,
                        repository = repository,
                        onShowMessage = onShowMessage
                    )
                }

                // 5. VEHICLE & MAINTENANCE SUBMENU
                SettingsSubmenu.VEHICLE_MAINTENANCE -> {
                    VehicleMaintenanceSubmenu(
                        vehicles = vehicles,
                        repository = repository,
                        onShowMessage = onShowMessage
                    )
                }

                // 6. THEMES & COLOR SCHEMES SUBMENU
                SettingsSubmenu.THEMES -> {
                    ThemePickerContent(
                        currentThemeId = userProfile.themePreset,
                        themeMode = userProfile.themeMode,
                        onApplyTheme = { themeId ->
                            repository.preferencesManager.setThemePreset(themeId)
                            val preset = ThemePresets.getPreset(themeId)
                            onShowMessage("Applied ${preset.name} theme across MileTracker Pro")
                        },
                        onThemeModeChange = { mode ->
                            repository.preferencesManager.setThemeMode(mode)
                            val modeLabel = when (mode) {
                                "DARK" -> "Dark Mode"
                                "LIGHT" -> "Light Mode"
                                else -> "Auto (System) Mode"
                            }
                            onShowMessage("Switched to $modeLabel")
                        }
                    )
                }

                // 7. DISPLAY ZOOM AND FONT SIZES SUBMENU
                SettingsSubmenu.FONT_ZOOM -> {
                    FontZoomSubmenu(
                        userProfile = userProfile,
                        repository = repository,
                        onNavigateToThemes = { activeSubmenu = SettingsSubmenu.THEMES },
                        onShowMessage = onShowMessage
                    )
                }

                // 8. DRIVER PROFILE SUBMENU
                SettingsSubmenu.DRIVER_PROFILE -> {
                    DriverProfileSubmenu(
                        userProfile = userProfile,
                        repository = repository,
                        onSignOut = onSignOut,
                        onShowMessage = onShowMessage
                    )
                }

                // 8. IRS MILEAGE REPORTS & CSV SUBMENU
                SettingsSubmenu.IRS_REPORTS -> {
                    IrsReportsSubmenu(
                        repository = repository,
                        onShowMessage = onShowMessage
                    )
                }

                // 9. KNOWLEDGE BASE SUBMENU
                SettingsSubmenu.KNOWLEDGE_BASE -> {
                    KnowledgeBaseSubmenu()
                }

                // 10. ABOUT APP SUBMENU
                SettingsSubmenu.ABOUT_APP -> {
                    AboutAppSubmenu(
                        userProfile = userProfile,
                        repository = repository,
                        onShowMessage = onShowMessage
                    )
                }
            }

            // Bottom scroll clearance so content is never covered by navigation bar even at 160% zoom
            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Composable
private fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp
        ),
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )
}

@Composable
private fun SettingsNavigationTile(
    number: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("submenu_${number}_tile")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$number. $title",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


// -------------------------------------------------------------
// 3. SUBMENU: SAVED PLACES
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedPlacesSubmenu(
    savedPlaces: List<SavedPlace>,
    repository: MileageRepository,
    onShowMessage: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var editingPlace by remember { mutableStateOf<SavedPlace?>(null) }
    var placeToDelete by remember { mutableStateOf<SavedPlace?>(null) }

    val standardCategories = listOf("Home", "Store", "Office", "Medical", "Friends", "Relatives")

    if (placeToDelete != null) {
        AlertDialog(
            onDismissRequest = { placeToDelete = null },
            title = { Text("Delete Saved Place?") },
            text = { Text("Are you sure you want to delete '${placeToDelete?.name}' (${placeToDelete?.address})? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val p = placeToDelete
                        placeToDelete = null
                        if (p != null) {
                            coroutineScope.launch {
                                repository.deleteSavedPlace(p)
                                onShowMessage("Removed ${p.name}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { placeToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        savedPlaces.forEach { place ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(place.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(place.category, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Text(place.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row {
                        IconButton(onClick = {
                            editingPlace = place
                            showDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            placeToDelete = place
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                editingPlace = null
                showDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add Frequent Place / Store")
        }
    }

    if (showDialog) {
        var name by remember { mutableStateOf(editingPlace?.name ?: "") }
        var address by remember { mutableStateOf(editingPlace?.address ?: "") }
        var category by remember { mutableStateOf(editingPlace?.category.takeIf { !it.isNullOrBlank() && standardCategories.contains(it) } ?: "Store") }
        var categoryExpanded by remember { mutableStateOf(false) }

        var addressSuggestions by remember { mutableStateOf<List<AddressSuggestion>>(emptyList()) }
        var searchJob by remember { mutableStateOf<Job?>(null) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingPlace == null) "Add Saved Place" else "Edit Saved Place") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Category Dropdown
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category (Used in Auto-Classification)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            standardCategories.forEach { cat ->
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

                    // Place Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Place / Store Name (e.g. Walmart, Home, Clinic)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Address Field with Autocomplete
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = address,
                            onValueChange = { newAddr ->
                                address = newAddr
                                searchJob?.cancel()
                                if (newAddr.trim().length >= 2) {
                                    searchJob = coroutineScope.launch {
                                        delay(200)
                                        addressSuggestions = repository.getAddressSuggestions(newAddr, null, null)
                                    }
                                } else {
                                    addressSuggestions = emptyList()
                                }
                            },
                            label = { Text("Address (with Autocomplete)") },
                            placeholder = { Text("Type street, business, or city...") },
                            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Address Autocomplete Suggestions Dropdown
                        if (addressSuggestions.isNotEmpty()) {
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .heightIn(max = 160.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    addressSuggestions.take(4).forEach { sug ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    address = sug.fullAddress
                                                    if (name.isBlank() && sug.title.isNotBlank()) {
                                                        name = sug.title
                                                    }
                                                    addressSuggestions = emptyList()
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (sug.isStoreOrPlace) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = if (sug.isStoreOrPlace) Icons.Default.Store else Icons.Default.Place,
                                                        contentDescription = null,
                                                        tint = if (sug.isStoreOrPlace) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(sug.title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                                                if (sug.subtitle.isNotBlank()) {
                                                    Text(sug.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && address.isNotBlank()) {
                        val place = SavedPlace(
                            id = editingPlace?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            address = address.trim(),
                            category = category.trim()
                        )
                        coroutineScope.launch {
                            repository.saveSavedPlace(place)
                            onShowMessage("Saved place: ${place.name} ($category)")
                        }
                        showDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}









// -------------------------------------------------------------
// -------------------------------------------------------------
// 7. SUBMENU: DISPLAY ZOOM AND FONT SIZES
// -------------------------------------------------------------
@Composable
private fun FontZoomSubmenu(
    userProfile: UserProfile,
    repository: MileageRepository,
    onNavigateToThemes: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    var fontPointsText by remember { mutableStateOf(userProfile.fontPoints.toString()) }
    var zoomPercentText by remember { mutableStateOf(userProfile.screenZoomPercent.toString()) }
    var zoomSliderValue by remember { mutableFloatStateOf(userProfile.screenZoomPercent.toFloat()) }

    val currentPoints = fontPointsText.toIntOrNull() ?: userProfile.fontPoints
    val currentZoom = zoomSliderValue.toInt()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Personalize Colors & Themes Shortcut Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToThemes() }
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Themes & Color Schemes",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "15 handcrafted palettes (Cobalt, Crimson, Mojave...) • Live preview",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Display Zoom & Font Settings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Configure font size in points and screen zoom factor for comfortable readability while driving. Layouts dynamically adapt to prevent text overflow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // Custom Text Field for Font Size in Points
                OutlinedTextField(
                    value = fontPointsText,
                    onValueChange = { fontPointsText = it },
                    label = { Text("Font Size (Points, 10 - 24 pt)") },
                    placeholder = { Text("14") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Points Buttons with safe wrapping layout
                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(12, 14, 16, 18, 20).forEach { pt ->
                        val isSelected = currentPoints == pt
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 60.dp, minHeight = 38.dp)
                                .clickable {
                                    fontPointsText = pt.toString()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Text(
                                    text = "$pt pt",
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Custom Text Field for Screen Zoom Factor (100% to 160%)
                OutlinedTextField(
                    value = zoomPercentText,
                    onValueChange = {
                        zoomPercentText = it
                        val p = it.toFloatOrNull()
                        if (p != null && p in 100f..160f) {
                            zoomSliderValue = p
                        }
                    },
                    label = { Text("Screen Zoom Factor (%)") },
                    placeholder = { Text("100") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Screen Zoom Factor Slider (100% to 160%)
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Screen Zoom Slider", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("${zoomSliderValue.toInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                    }
                    Slider(
                        value = zoomSliderValue,
                        onValueChange = {
                            zoomSliderValue = it
                            zoomPercentText = it.toInt().toString()
                        },
                        valueRange = 100f..160f,
                        steps = 6,
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }

                Button(
                    onClick = {
                        val pts = (fontPointsText.toIntOrNull() ?: 14).coerceIn(10, 24)
                        val zoom = zoomSliderValue.toInt().coerceIn(100, 160)
                        repository.preferencesManager.setDisplayZoom(pts, zoom)
                        onShowMessage("Display set to $pts pt font and $zoom% zoom")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply Typography & Zoom", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        // Live Interactive Preview Card demonstrating resilient button & text scaling
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Live Interactive Preview Card",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                )

                val effectivePoints = (currentPoints * (currentZoom / 100f)).coerceIn(11f, 26f)
                val bodyPoints = ((currentPoints - 2).coerceAtLeast(10) * (currentZoom / 100f)).coerceIn(10f, 22f)

                Text(
                    text = "8400 Rosedale Hwy (Walmart) → 14.8 mi",
                    fontSize = effectivePoints.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Customer delivery with 2 dropoff stops • Est. IRS deduction $9.92",
                    fontSize = bodyPoints.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Resilient action buttons that automatically expand height without clipping
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "✓ Anti-hanging text protection active",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        Text(
                            text = "${currentZoom}% Zoom",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 7. SUBMENU: DRIVER PROFILE
// -------------------------------------------------------------
@Composable
private fun DriverProfileSubmenu(
    userProfile: UserProfile,
    repository: MileageRepository,
    onSignOut: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf(userProfile.displayName) }
    var email by remember { mutableStateOf(userProfile.email) }
    var homeBase by remember { mutableStateOf(userProfile.homeBaseAddress) }
    var taxStatus by remember { mutableStateOf(userProfile.taxStatus) }
    var targetMileage by remember { mutableStateOf(userProfile.annualMileageTarget.toInt().toString()) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    var homeSuggestions by remember { mutableStateOf<List<AddressSuggestion>>(emptyList()) }
    var homeSearchJob by remember { mutableStateOf<Job?>(null) }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out of Google?") },
            text = { Text("Are you sure you want to sign out? Your cloud backup and sync will be paused until you sign in again.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Driver Profile & Tax Information", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Driver / Account Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Google Account Email") }, modifier = Modifier.fillMaxWidth())

                // Home Base Address with Autocomplete
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = homeBase,
                        onValueChange = { newAddr ->
                            homeBase = newAddr
                            homeSearchJob?.cancel()
                            if (newAddr.trim().length >= 2) {
                                homeSearchJob = coroutineScope.launch {
                                    delay(200)
                                    homeSuggestions = repository.getAddressSuggestions(newAddr, null, null)
                                }
                            } else {
                                homeSuggestions = emptyList()
                            }
                        },
                        label = { Text("Home Base Address (with Autocomplete)") },
                        placeholder = { Text("Type street address or city...") },
                        leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (homeSuggestions.isNotEmpty()) {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .heightIn(max = 140.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                homeSuggestions.take(4).forEach { sug ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                homeBase = sug.fullAddress
                                                homeSuggestions = emptyList()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(sug.title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                                            if (sug.subtitle.isNotBlank()) {
                                                Text(sug.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(value = taxStatus, onValueChange = { taxStatus = it }, label = { Text("Tax Status (e.g. 1099 Independent Contractor)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = targetMileage, onValueChange = { targetMileage = it }, label = { Text("Annual Business Mileage Target (miles)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

                Button(
                    onClick = {
                        val updated = userProfile.copy(
                            displayName = name.trim(),
                            email = email.trim(),
                            homeBaseAddress = homeBase.trim(),
                            taxStatus = taxStatus.trim(),
                            annualMileageTarget = targetMileage.toDoubleOrNull() ?: 20000.0
                        )
                        repository.preferencesManager.saveProfile(updated)
                        onShowMessage("Driver profile updated successfully!")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Profile Changes")
                }
            }
        }
    }
}


