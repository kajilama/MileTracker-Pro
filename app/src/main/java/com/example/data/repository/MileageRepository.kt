package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.AddressSuggestion
import com.example.data.model.AutoClassificationRule
import com.example.data.model.CustomTripType
import com.example.data.model.ExpenseReceipt
import com.example.data.model.SavedPlace
import com.example.data.model.ServiceReminder
import com.example.data.model.Trip
import com.example.data.model.TripPurpose
import com.example.data.model.TripStop
import com.example.data.model.UserProfile
import com.example.data.model.Vehicle
import com.example.data.util.VehicleFuelTypeLookup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.util.Locale

class MileageRepository(
    context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context),
    val preferencesManager: PreferencesManager = PreferencesManager(context),
    val authManager: AuthManager = AuthManager(context, preferencesManager),
    val driveBackupManager: GoogleDriveBackupManager = GoogleDriveBackupManager(context, database, preferencesManager),
    val locationService: LocationService = LocationService(context),
    private val autocompleteService: LocationAutocompleteService = LocationAutocompleteService(context)
) {
    val allTrips: Flow<List<Trip>> = database.tripDao().getAllTrips()
    val allVehicles: Flow<List<Vehicle>> = database.vehicleDao().getAllVehicles()
    val allSavedPlaces: Flow<List<SavedPlace>> = database.savedPlaceDao().getAllSavedPlaces()
    val allTripTypes: Flow<List<CustomTripType>> = database.customTripTypeDao().getAllTripTypes()
    val allRules: Flow<List<AutoClassificationRule>> = database.autoClassificationRuleDao().getAllRules()
    val allReceipts: Flow<List<ExpenseReceipt>> = database.expenseReceiptDao().getAllReceipts()
    val allReminders: Flow<List<ServiceReminder>> = database.serviceReminderDao().getAllReminders()
    val userProfile: StateFlow<UserProfile> = preferencesManager.userProfile

    fun getReceiptsByVehicle(vehicleId: String): Flow<List<ExpenseReceipt>> {
        return database.expenseReceiptDao().getReceiptsByVehicle(vehicleId)
    }

    fun getRemindersByVehicle(vehicleId: String): Flow<List<ServiceReminder>> {
        return database.serviceReminderDao().getRemindersForVehicle(vehicleId)
    }

    suspend fun saveTrip(trip: Trip) {
        val existingTrip = database.tripDao().getTripById(trip.id)
        database.tripDao().insertTrip(trip)

        // Update vehicle odometer if linked
        val vId = trip.vehicleId
        if (!vId.isNullOrBlank()) {
            val vehicles = database.vehicleDao().getAllVehicles().first()
            val vehicle = vehicles.find { it.id == vId }
            if (vehicle != null) {
                val delta = if (existingTrip != null && existingTrip.vehicleId == vId) {
                    trip.distanceMiles - existingTrip.distanceMiles
                } else {
                    trip.distanceMiles
                }
                val newOdometer = when {
                    trip.odometerEnd != null && trip.odometerEnd > vehicle.currentOdometer -> trip.odometerEnd
                    delta != 0.0 -> (vehicle.currentOdometer + delta).coerceAtLeast(vehicle.startingOdometer)
                    else -> vehicle.currentOdometer
                }
                if (newOdometer != vehicle.currentOdometer) {
                    database.vehicleDao().updateVehicle(vehicle.copy(currentOdometer = newOdometer))
                }
            }
        }
    }

    suspend fun deleteTrip(trip: Trip) {
        database.tripDao().deleteTrip(trip)
    }

    suspend fun saveReceipt(receipt: ExpenseReceipt) {
        database.expenseReceiptDao().insertReceipt(receipt)
    }

    suspend fun deleteReceipt(receipt: ExpenseReceipt) {
        database.expenseReceiptDao().deleteReceipt(receipt)
    }

    suspend fun deleteReceiptById(id: String) {
        database.expenseReceiptDao().deleteReceiptById(id)
    }

    suspend fun saveReminder(reminder: ServiceReminder) {
        database.serviceReminderDao().insertReminder(reminder)
    }

    suspend fun deleteReminder(reminder: ServiceReminder) {
        database.serviceReminderDao().deleteReminder(reminder)
    }

    suspend fun setDefaultTripType(typeId: String, typeName: String) {
        database.customTripTypeDao().clearDefaultTripTypes()
        database.customTripTypeDao().setDefaultTripType(typeId)
        preferencesManager.setDefaultTripType(typeName, typeId)
    }

    private data class CanonicalServiceItem(
        val key: String,
        val title: String,
        val intervalMiles: Double,
        val intervalMonths: Int,
        val notes: String
    )

    private fun matchReminderKey(title: String): String? {
        val t = title.lowercase(Locale.US)
        return when {
            t.contains("oil") -> "engine_oil"
            t.contains("transmission") || t.contains("gearbox") -> "transmission_fluid"
            t.contains("spark") -> "spark_plugs"
            t.contains("cabin") -> "cabin_air_filter"
            t.contains("air filter") -> "engine_air_filter"
            t.contains("brake fluid") -> "brake_fluid"
            t.contains("regenerative") -> "regen_brakes"
            t.contains("brake inspection") -> "ev_brake_inspection"
            t.contains("brake pad") || t.contains("brake") || t.contains("caliper") -> "brake_pads"
            t.contains("tire rotation") -> "tire_rotation"
            t.contains("12v") || (t.contains("battery") && !t.contains("hybrid") && !t.contains("high-voltage") && !t.contains("drive-unit")) -> "battery_12v"
            t.contains("hybrid battery") || t.contains("inverter") -> "hybrid_cooling"
            t.contains("charge port") || t.contains("charging port") -> "charge_port"
            t.contains("high-voltage") -> "hv_battery_coolant"
            t.contains("drive-unit") || (t.contains("battery") && t.contains("coolant")) -> "drive_unit_coolant"
            t.contains("coolant") -> "engine_coolant"
            t.contains("wiper") -> "wiper_blades"
            t.contains("suspension") || t.contains("alignment") -> "suspension_alignment"
            t.contains("software") || t.contains("telematics") -> "software_updates"
            else -> null
        }
    }

    fun getCanonicalServiceItemsForFuelType(fuelType: String): List< Pair<String, String> > {
        return buildCanonicalItems(fuelType).map { it.key to it.title }
    }

    private fun buildCanonicalItems(fuelType: String): List<CanonicalServiceItem> {
        val normalized = VehicleFuelTypeLookup.normalizeFuelType(fuelType)
        val isEv = normalized == "Electric"
        val isPlugInHybrid = normalized == "Plug-in Hybrid"
        val isHybrid = normalized == "Hybrid" || isPlugInHybrid

        val items = mutableListOf<CanonicalServiceItem>()
        if (isEv) {
            // Electric ONLY items — NEVER include engine oil, oil filter, transmission fluid, spark plugs, engine air filter, or engine coolant
            items.add(CanonicalServiceItem("tire_rotation", "Tire Rotation", 7500.0, 6, "Rotate tires to even out high-torque EV tread wear"))
            items.add(CanonicalServiceItem("cabin_air_filter", "Cabin Air Filter", 15000.0, 12, "Replace HVAC cabin air & pollen filter"))
            items.add(CanonicalServiceItem("brake_fluid", "Brake Fluid", 25000.0, 24, "Inspect brake fluid moisture and boiling point"))
            items.add(CanonicalServiceItem("ev_brake_inspection", "Brake Inspection", 25000.0, 24, "Inspect brake pads, rotors, and lubricate caliper slide pins"))
            items.add(CanonicalServiceItem("hv_battery_coolant", "High-Voltage Battery Coolant", 50000.0, 48, "High-voltage battery thermal management loop inspection"))
            items.add(CanonicalServiceItem("drive_unit_coolant", "Battery / Drive-Unit Coolant", 50000.0, 48, "Electric drive-unit and power electronics coolant check"))
            items.add(CanonicalServiceItem("battery_12v", "12V Battery Check", 15000.0, 12, "12V auxiliary battery state-of-health check"))
            items.add(CanonicalServiceItem("charge_port", "Charge Port Inspection", 15000.0, 12, "Inspect high-voltage charge port pins, latch, and seal"))
            items.add(CanonicalServiceItem("wiper_blades", "Wiper Blades", 10000.0, 12, "Inspect windshield wiper blades and washer fluid"))
            items.add(CanonicalServiceItem("suspension_alignment", "Suspension / Alignment", 20000.0, 24, "Inspect suspension bushings, struts, and wheel alignment"))
            items.add(CanonicalServiceItem("software_updates", "Software Updates", 10000.0, 12, "Verify BMS, telematics, and vehicle firmware updates"))
        } else {
            // Gas items
            items.add(CanonicalServiceItem("engine_oil", "Engine Oil and Filter", 5000.0, 6, "Full synthetic engine oil and filter replacement"))
            items.add(CanonicalServiceItem("transmission_fluid", "Transmission Fluid Inspection", 30000.0, 36, "Inspect transmission fluid level and condition"))
            items.add(CanonicalServiceItem("engine_coolant", "Coolant", 30000.0, 36, "Engine cooling system fluid inspection and flush"))
            items.add(CanonicalServiceItem("spark_plugs", "Spark Plugs", 60000.0, 60, "Replace ignition spark plugs"))
            items.add(CanonicalServiceItem("engine_air_filter", "Air Filter", 15000.0, 12, "Replace engine intake air filter"))
            items.add(CanonicalServiceItem("cabin_air_filter", "Cabin Air Filter", 15000.0, 12, "Replace HVAC cabin air filter"))
            items.add(CanonicalServiceItem("brake_fluid", "Brake Fluid", 25000.0, 24, "Inspect hydraulic brake fluid moisture"))
            items.add(CanonicalServiceItem("brake_pads", "Brake Pads", 15000.0, 12, "Inspect brake pads and rotors"))
            items.add(CanonicalServiceItem("tire_rotation", "Tire Rotation", 7500.0, 6, "Rotate tires and check tread depth"))
            items.add(CanonicalServiceItem("battery_12v", "Battery (12V)", 15000.0, 12, "Test 12V battery health and terminals"))

            if (isHybrid) {
                items.add(CanonicalServiceItem("hybrid_cooling", "Hybrid Battery Cooling System / Inverter Coolant Inspection", 30000.0, 36, "Inspect hybrid battery cooling fan filter and inverter coolant"))
                items.add(CanonicalServiceItem("regen_brakes", "Regenerative Brakes Inspection", 15000.0, 12, "Inspect regenerative braking hardware and caliper pins"))
            }

            if (isPlugInHybrid) {
                items.add(CanonicalServiceItem("charge_port", "Charging Port Inspection", 15000.0, 12, "Inspect plug-in hybrid charging port pins, latch, and seal"))
            }
        }
        return items
    }

    suspend fun syncServiceRemindersForFuelType(vehicleId: String, fuelType: String) {
        val normalizedFuel = VehicleFuelTypeLookup.normalizeFuelType(fuelType)
        val canonicalItems = buildCanonicalItems(normalizedFuel)
        val allowedKeys = canonicalItems.map { it.key }.toSet()

        val applicabilityLabel = when (normalizedFuel) {
            "Electric" -> "ELECTRIC"
            "Plug-in Hybrid" -> "PHEV"
            "Hybrid" -> "HYBRID"
            else -> "GAS"
        }

        val vehicles = database.vehicleDao().getAllVehicles().first()
        val vehicle = vehicles.find { it.id == vehicleId }
        val defaultLastDoneOdo = vehicle?.startingOdometer ?: 0.0

        val existingList = database.serviceReminderDao().getRemindersForVehicle(vehicleId).first()
        val matchedByKey = mutableMapOf<String, ServiceReminder>()

        // 1. Delete any reminder that does not belong to this fuel type, or duplicate keys
        for (existing in existingList) {
            val key = matchReminderKey(existing.title)
            // Note: brake_pads and ev_brake_inspection both relate to friction brakes, so map them if switching between EV and non-EV
            val resolvedKey = when {
                key == "brake_pads" && allowedKeys.contains("ev_brake_inspection") -> "ev_brake_inspection"
                key == "ev_brake_inspection" && allowedKeys.contains("brake_pads") -> "brake_pads"
                else -> key
            }

            if (resolvedKey == null || !allowedKeys.contains(resolvedKey) || matchedByKey.containsKey(resolvedKey)) {
                database.serviceReminderDao().deleteReminder(existing)
            } else {
                matchedByKey[resolvedKey] = existing
            }
        }

        // 2. Update matched reminders (preserving lastPerformedOdo & lastPerformedDate) and insert missing ones
        for (canonical in canonicalItems) {
            val existing = matchedByKey[canonical.key]
            if (existing != null) {
                // Preserve "last done" data while updating title/applicability to match canonical item
                val updated = existing.copy(
                    title = canonical.title,
                    fuelTypeApplicability = applicabilityLabel,
                    notes = canonical.notes
                )
                if (updated != existing) {
                    database.serviceReminderDao().insertReminder(updated)
                }
            } else {
                database.serviceReminderDao().insertReminder(
                    ServiceReminder(
                        vehicleId = vehicleId,
                        title = canonical.title,
                        intervalMiles = canonical.intervalMiles,
                        intervalMonths = canonical.intervalMonths,
                        lastPerformedOdo = defaultLastDoneOdo,
                        lastPerformedDate = System.currentTimeMillis(),
                        fuelTypeApplicability = applicabilityLabel,
                        notes = canonical.notes,
                        disclaimer = "Generic interval, verify with your owner's manual"
                    )
                )
            }
        }
    }

    suspend fun saveVehicle(vehicle: Vehicle) {
        val normalizedFuel = VehicleFuelTypeLookup.normalizeFuelType(vehicle.fuelType)
        val cleanVehicle = vehicle.copy(fuelType = normalizedFuel)
        if (cleanVehicle.isDefault) {
            database.vehicleDao().clearDefaultFlags()
        }
        database.vehicleDao().insertVehicle(cleanVehicle)
        // Keep reminders strictly synced to vehicle's fuel type
        syncServiceRemindersForFuelType(cleanVehicle.id, cleanVehicle.fuelType)
    }

    suspend fun deleteVehicle(vehicle: Vehicle) {
        database.serviceReminderDao().deleteRemindersForVehicle(vehicle.id)
        database.vehicleDao().deleteVehicle(vehicle)
    }

    suspend fun saveSavedPlace(place: SavedPlace) {
        database.savedPlaceDao().insertPlace(place)
    }

    suspend fun deleteSavedPlace(place: SavedPlace) {
        database.savedPlaceDao().deletePlace(place)
    }

    suspend fun saveTripType(tripType: CustomTripType) {
        if (tripType.isDefault) {
            database.customTripTypeDao().clearDefaultTripTypes()
            preferencesManager.setDefaultTripType(tripType.name, tripType.id)
        }
        database.customTripTypeDao().insertTripType(tripType)
    }

    suspend fun deleteTripType(tripType: CustomTripType) {
        database.customTripTypeDao().deleteTripType(tripType)
        // Trips that used this deleted tripTypeId now become Unclassified (tripTypeId = null)
        val trips = database.tripDao().getAllTrips().first()
        for (t in trips) {
            if (t.tripTypeId == tripType.id) {
                database.tripDao().updateTrip(t.copy(tripTypeId = null))
            }
        }
    }

    suspend fun saveRule(rule: AutoClassificationRule) {
        database.autoClassificationRuleDao().insertRule(rule)
    }

    suspend fun deleteRule(rule: AutoClassificationRule) {
        database.autoClassificationRuleDao().deleteRule(rule)
    }

    suspend fun getAddressSuggestions(
        query: String,
        currentLat: Double?,
        currentLng: Double?
    ): List<AddressSuggestion> {
        val existingTrips = database.tripDao().getAllTrips().first()
        val savedPlaces = database.savedPlaceDao().getAllSavedPlaces().first()
        val savedAddresses = (existingTrips.map { it.startAddress } + existingTrips.map { it.endAddress })
            .filter { it.isNotBlank() }
            .distinct()
        return autocompleteService.getSuggestions(query, currentLat, currentLng, savedPlaces, savedAddresses)
    }

    suspend fun seedSampleDataIfEmpty() {
        val currentTypes = database.customTripTypeDao().getAllTripTypes().first()

        // 1. Seed Custom Trip Types (Walmart Spark, Instacart, DoorDash, UberEats, etc.)
        if (currentTypes.isEmpty()) {
            val defaultTypes = listOf(
                CustomTripType("type_1", "Walmart Spark", "#0D9488", true, 0.70, "Briefcase", isDefault = true),
                CustomTripType("type_2", "Instacart", "#EA580C", true, 0.70, "ShoppingCart", isDefault = false),
                CustomTripType("type_3", "DoorDash", "#DC2626", true, 0.70, "Fastfood", isDefault = false),
                CustomTripType("type_4", "UberEats", "#16A34A", true, 0.70, "Restaurant", isDefault = false),
                CustomTripType("type_5", "Grubhub", "#BE123C", true, 0.70, "LocalDining", isDefault = false),
                CustomTripType("type_6", "Lyft", "#DB2777", true, 0.70, "LocalTaxi", isDefault = false),
                CustomTripType("type_7", "Personal", "#2563EB", false, 0.00, "Person", isDefault = false),
                CustomTripType("type_8", "Medical", "#D97706", true, 0.22, "LocalHospital", isDefault = false),
                CustomTripType("type_9", "Charity", "#7C3AED", true, 0.14, "VolunteerActivism", isDefault = false),
                CustomTripType("type_10", "Commute", "#64748B", false, 0.00, "DirectionsCar", isDefault = false)
            )
            database.customTripTypeDao().insertTripTypes(defaultTypes)
            preferencesManager.setDefaultTripType("Walmart Spark", "type_1")
        }

        val allTypesNow = database.customTripTypeDao().getAllTripTypes().first()

        // 2. Seed Pre-seeded Auto-Classification Rules
        val currentRules = database.autoClassificationRuleDao().getAllRules().first()
        if (currentRules.isEmpty()) {
            val defaultRules = listOf(
                AutoClassificationRule(
                    id = "rule_1",
                    ruleName = "Walmart Stores ⇄ Unsaved Address = Spark",
                    originPattern = "Walmart",
                    destinationPattern = "Unsaved Address",
                    tripTypeId = "type_1",
                    tripTypeName = "Walmart Spark",
                    isEnabled = true,
                    isBidirectional = true
                ),
                AutoClassificationRule(
                    id = "rule_2",
                    ruleName = "Grocery Stores (except Walmart) ⇄ Unsaved Address = Instacart",
                    originPattern = "Albertsons|Vons|Kroger|Safeway|ALDI",
                    destinationPattern = "Unsaved Address",
                    tripTypeId = "type_2",
                    tripTypeName = "Instacart",
                    isEnabled = true,
                    isBidirectional = true
                ),
                AutoClassificationRule(
                    id = "rule_3",
                    ruleName = "Home ⇄ Relatives / Friends / Doctors = Personal",
                    originPattern = "Home",
                    destinationPattern = "Relatives|Friends|Medical|Doctor",
                    tripTypeId = "type_7",
                    tripTypeName = "Personal",
                    isEnabled = true,
                    isBidirectional = true
                ),
                AutoClassificationRule(
                    id = "rule_4",
                    ruleName = "Home ⇄ 8400 Rosedale Hwy (Walmart) = Spark",
                    originPattern = "Home",
                    destinationPattern = "8400 Rosedale Hwy",
                    tripTypeId = "type_1",
                    tripTypeName = "Walmart Spark",
                    isEnabled = true,
                    isBidirectional = true
                )
            )
            database.autoClassificationRuleDao().insertRules(defaultRules)
        } else {
            // Backfill tripTypeId on any existing rules where tripTypeId is null
            for (rule in currentRules) {
                if (rule.tripTypeId.isNullOrBlank()) {
                    val matchedType = allTypesNow.find { it.name.equals(rule.tripTypeName, ignoreCase = true) }
                    if (matchedType != null) {
                        database.autoClassificationRuleDao().insertRule(rule.copy(tripTypeId = matchedType.id))
                    }
                }
            }
        }

        // 3. Seed or Clean Up Vehicles & Fuel-Type Maintenance Reminders
        val currentVehicles = database.vehicleDao().getAllVehicles().first()
        if (currentVehicles.isEmpty()) {
            val defaultVehicle = Vehicle(
                id = "veh_1",
                name = "Primary Car",
                makeModel = "Toyota RAV4 Hybrid",
                year = "2023",
                licensePlate = "7XYZ890",
                fuelType = "Hybrid",
                startingOdometer = 12450.0,
                currentOdometer = 13820.0,
                lastOilChangeOdo = 10000.0,
                oilChangeIntervalMiles = 5000.0,
                tireRotationIntervalMiles = 7500.0,
                lastTireRotationOdo = 10000.0,
                oemBrand = "Toyota",
                isDefault = true
            )
            val secondaryVehicle = Vehicle(
                id = "veh_2",
                name = "EV Delivery Van",
                makeModel = "Ford E-Transit",
                year = "2024",
                licensePlate = "4ABC123",
                fuelType = "Electric",
                startingOdometer = 8300.0,
                currentOdometer = 9450.0,
                lastOilChangeOdo = 0.0,
                tireRotationIntervalMiles = 7500.0,
                lastTireRotationOdo = 8000.0,
                oemBrand = "Ford",
                isDefault = false
            )
            database.vehicleDao().insertVehicles(listOf(defaultVehicle, secondaryVehicle))
            syncServiceRemindersForFuelType("veh_1", "Hybrid")
            syncServiceRemindersForFuelType("veh_2", "Electric")
        } else {
            // Ensure every existing vehicle in the DB has normalized fuelType and strictly matching reminders
            for (veh in currentVehicles) {
                val normalizedFuel = VehicleFuelTypeLookup.normalizeFuelType(veh.fuelType)
                if (normalizedFuel != veh.fuelType) {
                    database.vehicleDao().updateVehicle(veh.copy(fuelType = normalizedFuel))
                }
                syncServiceRemindersForFuelType(veh.id, normalizedFuel)
            }
        }

        // 4. Seed Saved Places
        val currentPlaces = database.savedPlaceDao().getAllSavedPlaces().first()
        if (currentPlaces.isEmpty()) {
            val defaultPlaces = listOf(
                SavedPlace(
                    id = "place_1",
                    name = "Home Base",
                    address = "742 Evergreen Terrace, Springfield, OR",
                    category = "Home",
                    lat = 35.3733,
                    lng = -119.0187
                ),
                SavedPlace(
                    id = "place_2",
                    name = "Walmart Supercenter (8400 Rosedale)",
                    address = "8400 Rosedale Hwy, Bakersfield, CA 93312",
                    category = "Store",
                    lat = 35.3857,
                    lng = -119.1026
                ),
                SavedPlace(
                    id = "place_3",
                    name = "Albertsons Grocery",
                    address = "8200 Stockdale Hwy, Bakersfield, CA 93311",
                    category = "Store",
                    lat = 35.3526,
                    lng = -119.0984
                ),
                SavedPlace(
                    id = "place_4",
                    name = "Vons Grocery",
                    address = "5600 Stockdale Hwy, Bakersfield, CA 93309",
                    category = "Store",
                    lat = 35.3524,
                    lng = -119.0664
                ),
                SavedPlace(
                    id = "place_5",
                    name = "Dentist & Medical Center",
                    address = "Physicians Plaza, 500 Medical Center Dr",
                    category = "Medical",
                    lat = 35.3712,
                    lng = -119.0341
                ),
                SavedPlace(
                    id = "place_6",
                    name = "Relatives & Friends",
                    address = "Family Residence, 1200 Oak Ridge Way",
                    category = "Relatives",
                    lat = 35.3619,
                    lng = -119.0410
                )
            )
            database.savedPlaceDao().insertPlaces(defaultPlaces)
        }

        // 5. Seed Trips
        val currentTrips = database.tripDao().getAllTrips().first()
        val minutesPerStop = preferencesManager.userProfile.value.minutesPerDropoffStop
        if (currentTrips.isEmpty()) {
            val now = System.currentTimeMillis()
            val day = 86400000L
            val trip1Dropoff = 3 * minutesPerStop
            val trip1Total = 22 + trip1Dropoff
            val trip2Dropoff = 1 * minutesPerStop
            val trip2Total = 14 + trip2Dropoff

            val sampleTrips = listOf(
                Trip(
                    id = "trip_1",
                    title = "Walmart Spark Grocery Batch (3 Stops)",
                    purpose = TripPurpose.GIG_DELIVERY,
                    startAddress = "8400 Rosedale Hwy, Bakersfield, CA 93312",
                    endAddress = "8400 Rosedale Hwy, Bakersfield, CA 93312",
                    startLat = 35.3857,
                    startLng = -119.1026,
                    endLat = 35.3857,
                    endLng = -119.1026,
                    distanceMiles = 8.7,
                    deductionRate = 0.70,
                    totalDeduction = 8.7 * 0.70,
                    dateTimestamp = now - (day * 0),
                    startTimeMillis = now - (day * 0) - (trip1Total * 60 * 1000L),
                    endTimeMillis = now - (day * 0),
                    isRoundTrip = true,
                    stopsCount = 3,
                    stopsSummary = "4301 Callaway Dr | 7800 Meany Ave | 9100 Brimhall Rd",
                    drivingMinutes = 22,
                    dropoffMinutes = trip1Dropoff,
                    totalDurationMinutes = trip1Total,
                    vehicleId = "veh_1",
                    vehicleName = "Toyota RAV4 Hybrid",
                    notes = "Walmart Spark Delivery • 3 customer dropoffs • $minutesPerStop min unload per stop",
                    tags = "Walmart Spark",
                    tripTypeId = "type_1",
                    routePointsJson = DrivingRouteService.serializeRoadPoints(
                        DrivingRouteService.generateRealisticGridRoute(
                            startLat = 35.3857,
                            startLng = -119.1026,
                            stops = listOf(
                                TripStop("s1", "4301 Callaway Dr", 35.3995, -119.0880, minutesPerStop),
                                TripStop("s2", "7800 Meany Ave", 35.4050, -119.1120, minutesPerStop),
                                TripStop("s3", "9100 Brimhall Rd", 35.3780, -119.1240, minutesPerStop)
                            ),
                            endLat = 35.3857,
                            endLng = -119.1026,
                            isRoundTrip = true
                        ).roadPoints
                    ),
                    isSyncedWithDrive = true
                ),
                Trip(
                    id = "trip_2",
                    title = "Instacart Grocery Run (Albertsons)",
                    purpose = TripPurpose.GIG_DELIVERY,
                    startAddress = "Albertsons, 8200 Stockdale Hwy",
                    endAddress = "3400 Calloway Dr, Bakersfield, CA",
                    startLat = 35.3526,
                    startLng = -119.0984,
                    endLat = 35.3721,
                    endLng = -119.1120,
                    distanceMiles = 5.2,
                    deductionRate = 0.70,
                    totalDeduction = 5.2 * 0.70,
                    dateTimestamp = now - (day * 1),
                    startTimeMillis = now - (day * 1) - (trip2Total * 60 * 1000L),
                    endTimeMillis = now - (day * 1),
                    isRoundTrip = false,
                    stopsCount = 1,
                    stopsSummary = "Customer Residence",
                    drivingMinutes = 14,
                    dropoffMinutes = trip2Dropoff,
                    totalDurationMinutes = trip2Total,
                    vehicleId = "veh_1",
                    vehicleName = "Toyota RAV4 Hybrid",
                    notes = "Instacart grocery delivery",
                    tags = "Instacart",
                    tripTypeId = "type_2",
                    routePointsJson = DrivingRouteService.serializeRoadPoints(
                        DrivingRouteService.generateRealisticGridRoute(
                            startLat = 35.3526,
                            startLng = -119.0984,
                            stops = listOf(TripStop("s1", "Customer Residence", 35.3620, -119.1050, minutesPerStop)),
                            endLat = 35.3721,
                            endLng = -119.1120,
                            isRoundTrip = false
                        ).roadPoints
                    ),
                    isSyncedWithDrive = true
                ),
                Trip(
                    id = "trip_3",
                    title = "Family & Personal Errands",
                    purpose = TripPurpose.PERSONAL,
                    startAddress = "742 Evergreen Terrace",
                    endAddress = "Family Residence, 1200 Oak Ridge Way",
                    startLat = 35.3733,
                    startLng = -119.0187,
                    endLat = 35.3619,
                    endLng = -119.0410,
                    distanceMiles = 4.1,
                    deductionRate = 0.0,
                    totalDeduction = 0.0,
                    dateTimestamp = now - (day * 2),
                    startTimeMillis = now - (day * 2) - (12 * 60 * 1000L),
                    endTimeMillis = now - (day * 2),
                    isRoundTrip = true,
                    stopsCount = 0,
                    drivingMinutes = 12,
                    dropoffMinutes = 0,
                    totalDurationMinutes = 12,
                    vehicleId = "veh_1",
                    vehicleName = "Toyota RAV4 Hybrid",
                    notes = "Personal drive",
                    tags = "Personal",
                    tripTypeId = "type_7",
                    routePointsJson = DrivingRouteService.serializeRoadPoints(
                        DrivingRouteService.generateRealisticGridRoute(
                            startLat = 35.3733,
                            startLng = -119.0187,
                            stops = listOf(TripStop("s1", "Oak Ridge Way", 35.3619, -119.0410, minutesPerStop)),
                            endLat = 35.3733,
                            endLng = -119.0187,
                            isRoundTrip = true
                        ).roadPoints
                    ),
                    isSyncedWithDrive = true
                )
            )
            database.tripDao().insertTrips(sampleTrips)
        }

        // Automatic IRS standard mileage rate sync on app launch / startup
        try {
            com.example.data.service.IrsRateService.syncIrsRatesOnLaunch(preferencesManager)
        } catch (_: Exception) {
            // Keep existing rate if offline
        }
    }
}
