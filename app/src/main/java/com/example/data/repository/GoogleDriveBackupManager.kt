package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.CustomTripType
import com.example.data.model.Trip
import com.example.data.model.TripPurpose
import com.example.data.model.Vehicle
import com.example.data.service.IrsRateService
import com.example.data.util.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DriveBackupInfo(
    val id: String,
    val fileName: String,
    val timestamp: Long,
    val formattedDate: String,
    val tripCount: Int,
    val totalMiles: Double,
    val totalDeduction: Double,
    val fileSizeKb: Int,
    val accountEmail: String
)

class GoogleDriveBackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager
) {
    private val backupDir = File(context.filesDir, "google_drive_backups").apply { mkdirs() }
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    suspend fun backupToGoogleDrive(): Result<DriveBackupInfo> = withContext(Dispatchers.IO) {
        try {
            val userProfile = preferencesManager.userProfile.value
            val trips = database.tripDao().getAllTrips().first()
            val vehicles = database.vehicleDao().getAllVehicles().first()

            val now = System.currentTimeMillis()
            val fileStamp = fileDateFormat.format(Date(now))
            val fileName = "miletracker_backup_${fileStamp}.json"
            val backupFile = File(backupDir, fileName)

            val rootJson = JSONObject().apply {
                put("app", "miletracker6")
                put("version", "1.0.6")
                put("backupTimestamp", now)
                put("accountEmail", userProfile.email)
                put("irsRates", JSONObject().apply {
                    put("business", userProfile.irsBusinessRate)
                    put("medical", userProfile.irsMedicalRate)
                    put("charity", userProfile.irsCharityRate)
                })

                val tripsArray = JSONArray()
                trips.forEach { trip ->
                    tripsArray.put(JSONObject().apply {
                        put("id", trip.id)
                        put("title", trip.title)
                        put("purpose", trip.purpose.name)
                        put("tripTypeId", trip.tripTypeId ?: JSONObject.NULL)
                        put("startAddress", trip.startAddress)
                        put("endAddress", trip.endAddress)
                        put("startLat", trip.startLat ?: JSONObject.NULL)
                        put("startLng", trip.startLng ?: JSONObject.NULL)
                        put("endLat", trip.endLat ?: JSONObject.NULL)
                        put("endLng", trip.endLng ?: JSONObject.NULL)
                        put("distanceMiles", trip.distanceMiles)
                        put("deductionRate", trip.deductionRate)
                        put("totalDeduction", trip.totalDeduction)
                        put("dateTimestamp", trip.dateTimestamp)
                        put("odometerStart", trip.odometerStart ?: JSONObject.NULL)
                        put("odometerEnd", trip.odometerEnd ?: JSONObject.NULL)
                        put("vehicleId", trip.vehicleId ?: JSONObject.NULL)
                        put("vehicleName", trip.vehicleName ?: JSONObject.NULL)
                        put("notes", trip.notes)
                        put("tags", trip.tags)
                    })
                }
                put("trips", tripsArray)

                val vehiclesArray = JSONArray()
                vehicles.forEach { vehicle ->
                    vehiclesArray.put(JSONObject().apply {
                        put("id", vehicle.id)
                        put("name", vehicle.name)
                        put("makeModel", vehicle.makeModel)
                        put("year", vehicle.year)
                        put("fuelType", vehicle.fuelType)
                        put("licensePlate", vehicle.licensePlate)
                        put("startingOdometer", vehicle.startingOdometer)
                        put("isDefault", vehicle.isDefault)
                    })
                }
                put("vehicles", vehiclesArray)
            }

            backupFile.writeText(rootJson.toString(2))

            database.tripDao().markAllAsSynced()
            preferencesManager.updateLastSyncTime(now)

            val totalMiles = trips.sumOf { it.distanceMiles }
            val totalDeduction = trips.sumOf { it.totalDeduction }
            val sizeKb = (backupFile.length() / 1024).toInt().coerceAtLeast(1)

            val info = DriveBackupInfo(
                id = backupFile.name,
                fileName = backupFile.name,
                timestamp = now,
                formattedDate = dateFormat.format(Date(now)),
                tripCount = trips.size,
                totalMiles = totalMiles,
                totalDeduction = totalDeduction,
                fileSizeKb = sizeKb,
                accountEmail = userProfile.email
            )
            Result.success(info)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getDriveBackups(): List<DriveBackupInfo> = withContext(Dispatchers.IO) {
        val files = backupDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        val list = mutableListOf<DriveBackupInfo>()

        for (file in files.sortedByDescending { it.lastModified() }) {
            try {
                val json = JSONObject(file.readText())
                val timestamp = json.optLong("backupTimestamp", file.lastModified())
                val email = json.optString("accountEmail", AppConstants.SUPPORT_EMAIL)
                val tripsArray = json.optJSONArray("trips")
                val count = tripsArray?.length() ?: 0

                var miles = 0.0
                var deduction = 0.0
                if (tripsArray != null) {
                    for (i in 0 until count) {
                        val t = tripsArray.optJSONObject(i) ?: continue
                        miles += t.optDouble("distanceMiles", 0.0)
                        deduction += t.optDouble("totalDeduction", 0.0)
                    }
                }

                list.add(
                    DriveBackupInfo(
                        id = file.name,
                        fileName = file.name,
                        timestamp = timestamp,
                        formattedDate = dateFormat.format(Date(timestamp)),
                        tripCount = count,
                        totalMiles = miles,
                        totalDeduction = deduction,
                        fileSizeKb = (file.length() / 1024).toInt().coerceAtLeast(1),
                        accountEmail = email
                    )
                )
            } catch (_: Exception) {
            }
        }
        list
    }

    suspend fun restoreFromGoogleDrive(fileName: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val file = File(backupDir, fileName)
            if (!file.exists()) return@withContext Result.failure(Exception("Backup file not found"))

            val rootJson = JSONObject(file.readText())
            val tripsArray = rootJson.optJSONArray("trips")
            val vehiclesArray = rootJson.optJSONArray("vehicles")

            val restoredTrips = mutableListOf<Trip>()
            if (tripsArray != null) {
                for (i in 0 until tripsArray.length()) {
                    val t = tripsArray.getJSONObject(i)
                    val purposeStr = t.optString("purpose", "BUSINESS")
                    val purpose = try { TripPurpose.valueOf(purposeStr) } catch (_: Exception) { TripPurpose.BUSINESS }

                    restoredTrips.add(
                        Trip(
                            id = t.optString("id"),
                            title = t.optString("title"),
                            purpose = purpose,
                            tripTypeId = if (t.isNull("tripTypeId")) null else t.optString("tripTypeId"),
                            startAddress = t.optString("startAddress"),
                            endAddress = t.optString("endAddress"),
                            startLat = if (t.isNull("startLat")) null else t.optDouble("startLat"),
                            startLng = if (t.isNull("startLng")) null else t.optDouble("startLng"),
                            endLat = if (t.isNull("endLat")) null else t.optDouble("endLat"),
                            endLng = if (t.isNull("endLng")) null else t.optDouble("endLng"),
                            distanceMiles = t.optDouble("distanceMiles", 0.0),
                            deductionRate = t.optDouble("deductionRate", 0.67),
                            totalDeduction = t.optDouble("totalDeduction", 0.0),
                            dateTimestamp = t.optLong("dateTimestamp", System.currentTimeMillis()),
                            odometerStart = if (t.isNull("odometerStart")) null else t.optDouble("odometerStart"),
                            odometerEnd = if (t.isNull("odometerEnd")) null else t.optDouble("odometerEnd"),
                            vehicleId = if (t.isNull("vehicleId")) null else t.optString("vehicleId"),
                            vehicleName = if (t.isNull("vehicleName")) null else t.optString("vehicleName"),
                            notes = t.optString("notes", ""),
                            tags = t.optString("tags", ""),
                            isSyncedWithDrive = true
                        )
                    )
                }
            }

            val restoredVehicles = mutableListOf<Vehicle>()
            if (vehiclesArray != null) {
                for (i in 0 until vehiclesArray.length()) {
                    val v = vehiclesArray.getJSONObject(i)
                    restoredVehicles.add(
                        Vehicle(
                            id = v.optString("id"),
                            name = v.optString("name"),
                            makeModel = v.optString("makeModel"),
                            year = v.optString("year", ""),
                            fuelType = v.optString("fuelType", "Gas"),
                            licensePlate = v.optString("licensePlate", ""),
                            startingOdometer = v.optDouble("startingOdometer", 0.0),
                            isDefault = v.optBoolean("isDefault", false)
                        )
                    )
                }
            }

            if (restoredVehicles.isNotEmpty()) {
                database.vehicleDao().insertVehicles(restoredVehicles)
            }
            if (restoredTrips.isNotEmpty()) {
                database.tripDao().insertTrips(restoredTrips)
            }

            preferencesManager.updateLastSyncTime(System.currentTimeMillis())
            Result.success(restoredTrips.size)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun exportIrsCsvReport(
        vehicleFilter: String? = null,
        tripTypeIds: Set<String>? = null,
        startEpoch: Long? = null,
        endEpoch: Long? = null,
        preFilteredTrips: List<Trip>? = null
    ): String = withContext(Dispatchers.IO) {
        val tripTypes = database.customTripTypeDao().getAllTripTypes().first()
        val tripTypesById = tripTypes.associateBy { it.id }

        val trips = if (preFilteredTrips != null) {
            preFilteredTrips.sortedByDescending { it.dateTimestamp }
        } else {
            val allTrips = database.tripDao().getAllTrips().first()
            allTrips.filter { t ->
                val matchesVeh = vehicleFilter.isNullOrBlank() || t.vehicleName == vehicleFilter || t.vehicleId == vehicleFilter
                val resolvedTypeId = if (t.tripTypeId != null && tripTypesById.containsKey(t.tripTypeId)) {
                    t.tripTypeId
                } else {
                    "UNCLASSIFIED"
                }
                val matchesType = tripTypeIds.isNullOrEmpty() || tripTypeIds.contains(resolvedTypeId)
                val matchesStart = startEpoch == null || t.dateTimestamp >= startEpoch
                val matchesEnd = endEpoch == null || t.dateTimestamp <= endEpoch
                matchesVeh && matchesType && matchesStart && matchesEnd
            }.sortedByDescending { it.dateTimestamp }
        }

        val sb = StringBuilder()
        sb.append("# Note: This app helps keep records. Consult a tax professional. See IRS Publication 463.\n")
        sb.append("Date,Start time,End time,Vehicle,Origin name,Origin address,Destination name,Destination address,Stops,Miles,Trip Type,Business/Personal,Business purpose,Rate used,Deduction,Odometer start,Odometer end,Source (auto/manual),Estimated times (Y/N),Edited (Y/N),Trip ID\n")

        val csvDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val csvTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

        for (t in trips) {
            val dateStr = csvDateFormat.format(Date(t.dateTimestamp))
            val startTimeStr = if (t.startTimeMillis > 0) csvTimeFormat.format(Date(t.startTimeMillis)) else ""
            val endTimeStr = if (t.endTimeMillis > 0) csvTimeFormat.format(Date(t.endTimeMillis)) else ""
            val vehicle = escapeCsv(t.vehicleName ?: "Primary Vehicle")

            val originName = escapeCsv(if (t.startAddress.contains(",")) t.startAddress.substringBefore(",") else t.startAddress)
            val originAddr = escapeCsv(t.startAddress)
            val destName = escapeCsv(if (t.endAddress.contains(",")) t.endAddress.substringBefore(",") else t.endAddress)
            val destAddr = escapeCsv(t.endAddress)

            val stops = t.stopsCount
            val miles = "%.2f".format(Locale.US, t.distanceMiles)
            val matchedType: CustomTripType? = t.tripTypeId?.let { tripTypesById[it] }
            val tripTypeLabel = escapeCsv(matchedType?.name ?: "Unclassified")
            val isBusiness = matchedType?.isTaxDeductible ?: (t.purpose == TripPurpose.BUSINESS || t.purpose == TripPurpose.GIG_DELIVERY)
            val bizPersonal = if (isBusiness) "Business" else "Personal"
            val bizPurpose = escapeCsv(if (t.notes.isNotBlank()) t.notes else t.title)

            val tripYear = Calendar.getInstance().apply { timeInMillis = t.dateTimestamp }.get(Calendar.YEAR)
            val officialRates = IrsRateService.getOfficialRateForYear(tripYear)
            val effectiveRate = when {
                matchedType != null && !matchedType.isTaxDeductible -> 0.0
                matchedType != null && matchedType.name.contains("Medical", ignoreCase = true) -> officialRates.medicalRate
                matchedType != null && matchedType.name.contains("Charity", ignoreCase = true) -> officialRates.charityRate
                matchedType != null && matchedType.isTaxDeductible -> officialRates.businessRate
                isBusiness -> officialRates.businessRate
                else -> 0.0
            }
            val effectiveDeduction = t.distanceMiles * effectiveRate

            val rateUsed = "%.2f".format(Locale.US, effectiveRate)
            val deduction = "%.2f".format(Locale.US, effectiveDeduction)
            val odoStart = if (t.odometerStart != null) "%.1f".format(Locale.US, t.odometerStart) else ""
            val odoEnd = if (t.odometerEnd != null) "%.1f".format(Locale.US, t.odometerEnd) else ""
            val source = if (t.notes.contains("manual", ignoreCase = true)) "Manual" else "Auto"
            val estTimes = if (t.drivingMinutes > 0 && t.startTimeMillis == t.endTimeMillis) "Y" else "N"
            val edited = if (t.notes.contains("edited", ignoreCase = true)) "Y" else "N"
            val tripId = escapeCsv(t.id)

            sb.append("\"$dateStr\",\"$startTimeStr\",\"$endTimeStr\",\"$vehicle\",\"$originName\",\"$originAddr\",\"$destName\",\"$destAddr\",$stops,$miles,\"$tripTypeLabel\",\"$bizPersonal\",\"$bizPurpose\",$rateUsed,$deduction,\"$odoStart\",\"$odoEnd\",\"$source\",\"$estTimes\",\"$edited\",\"$tripId\"\n")
        }
        sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"")
    }
}
