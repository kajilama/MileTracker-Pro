package com.example.data.service

import com.example.data.util.VehicleFuelTypeLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DecodedVehicleInfo(
    val year: String,
    val make: String,
    val model: String,
    val fuelType: String,
    val displacementL: String,
    val cylinders: String,
    val vehicleType: String,
    val rawResponse: String
)

object NhtsaVinService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val POPULAR_US_MAKES = listOf(
        "Toyota", "Tesla", "Honda", "Ford", "Chevrolet",
        "Hyundai", "Kia", "Nissan", "Volkswagen", "Subaru",
        "Jeep", "BMW", "Mercedes-Benz", "Rivian", "Lucid",
        "Polestar", "Mazda", "GMC", "Ram", "Lexus", "Audi", "Volvo", "Dodge"
    )

    val POPULAR_MODELS_BY_MAKE = mapOf(
        "Toyota" to listOf(
            "RAV4", "RAV4 Hybrid", "RAV4 Prime",
            "Prius", "Prius Prime",
            "Camry", "Camry Hybrid",
            "Corolla", "Corolla Hybrid",
            "Highlander", "Highlander Hybrid",
            "bZ4X", "Sienna", "Tacoma", "Tundra", "Crown"
        ),
        "Tesla" to listOf("Model 3", "Model Y", "Model S", "Model X", "Cybertruck"),
        "Chevrolet" to listOf(
            "Equinox", "Equinox EV",
            "Blazer", "Blazer EV",
            "Silverado 1500", "Silverado EV",
            "Bolt EV", "Bolt EUV",
            "Malibu", "Traverse", "Tahoe", "Volt"
        ),
        "Ford" to listOf(
            "F-150", "F-150 Hybrid", "F-150 Lightning",
            "Mustang Mach-E", "E-Transit",
            "Maverick", "Maverick Hybrid",
            "Escape", "Escape Hybrid", "Escape Plug-in Hybrid",
            "Explorer", "Bronco", "Edge"
        ),
        "Hyundai" to listOf(
            "Ioniq 5", "Ioniq 6", "Ioniq 9",
            "Elantra", "Elantra Hybrid",
            "Tucson", "Tucson Hybrid", "Tucson Plug-in Hybrid",
            "Sonata", "Sonata Hybrid",
            "Santa Fe", "Santa Fe Hybrid", "Kona Electric", "Palisade"
        ),
        "Kia" to listOf(
            "EV6", "EV9", "Niro EV",
            "Niro Hybrid", "Niro Plug-in Hybrid",
            "Sorento", "Sorento Hybrid", "Sorento Plug-in Hybrid",
            "Sportage", "Sportage Hybrid", "Telluride", "Forte", "K5", "Soul"
        ),
        "Honda" to listOf(
            "CR-V", "CR-V Hybrid",
            "Accord", "Accord Hybrid",
            "Civic", "Civic Hybrid",
            "Prologue", "Pilot", "HR-V", "Odyssey", "Ridgeline"
        ),
        "Nissan" to listOf("Leaf", "Ariya", "Rogue", "Altima", "Sentra", "Pathfinder", "Frontier", "Kicks"),
        "Volkswagen" to listOf("ID.4", "ID.Buzz", "Jetta", "Tiguan", "Atlas", "Taos", "Golf"),
        "Rivian" to listOf("R1T", "R1S"),
        "Lucid" to listOf("Air", "Gravity"),
        "Polestar" to listOf("Polestar 2", "Polestar 3"),
        "BMW" to listOf("i4", "iX", "i5", "i7", "X5 xDrive50e", "330e", "330i", "X3", "X5"),
        "Mercedes-Benz" to listOf("EQB", "EQE", "EQE SUV", "EQS", "EQS SUV", "C-Class", "E-Class", "GLC", "GLE"),
        "Subaru" to listOf("Solterra", "Outback", "Forester", "Crosstrek", "Crosstrek Hybrid", "Ascent", "Impreza"),
        "Mazda" to listOf("MX-30", "CX-90 PHEV", "CX-70 PHEV", "CX-5", "CX-50", "CX-30", "Mazda3"),
        "Jeep" to listOf("Wrangler 4xe", "Grand Cherokee 4xe", "Wrangler", "Grand Cherokee", "Compass", "Cherokee", "Gladiator"),
        "Audi" to listOf("Q4 e-tron", "Q8 e-tron", "e-tron", "Q5", "Q7", "A4", "A6"),
        "Volvo" to listOf("XC40 Recharge", "C40 Recharge", "EX30", "EX90", "XC60 Recharge", "XC90 Recharge", "XC60", "XC90")
    )

    val FUEL_TYPES = VehicleFuelTypeLookup.FUEL_TYPES

    val MODEL_YEARS = (2010..2026).map { it.toString() }.reversed()

    suspend fun decodeVin(vin: String): Result<DecodedVehicleInfo> = withContext(Dispatchers.IO) {
        val cleanVin = vin.trim().uppercase()
        if (cleanVin.length < 11) {
            return@withContext Result.failure(IllegalArgumentException("VIN must be at least 11 characters"))
        }

        try {
            val url = "https://vpic.nhtsa.dot.gov/api/vehicles/decodevinvalues/$cleanVin?format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MileTrackerPro-Android")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("NHTSA server HTTP error: ${response.code}"))
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val resultsArray = json.optJSONArray("Results")
            if (resultsArray == null || resultsArray.length() == 0) {
                return@withContext Result.failure(Exception("No vehicle information returned for VIN $cleanVin"))
            }

            val vObj = resultsArray.getJSONObject(0)
            val additionalError = vObj.optString("AdditionalErrorText", "")

            val year = vObj.optString("ModelYear", "").trim()
            val make = vObj.optString("Make", "").trim().capitalizeWords()
            val model = vObj.optString("Model", "").trim()
            val rawFuel = vObj.optString("FuelTypePrimary", "").trim()
            val electrification = vObj.optString("ElectrificationLevel", "").trim()
            val displacement = vObj.optString("DisplacementL", "").trim()
            val cylinders = vObj.optString("EngineCylinders", "").trim()
            val vehicleType = vObj.optString("VehicleType", "").trim()

            val fuelTypeNormalized = when {
                electrification.contains("PHEV", ignoreCase = true) ||
                        rawFuel.contains("Plug-in", ignoreCase = true) -> "Plug-in Hybrid"
                electrification.contains("BEV", ignoreCase = true) ||
                        rawFuel.contains("Electric", ignoreCase = true) -> "Electric"
                electrification.contains("HEV", ignoreCase = true) ||
                        rawFuel.contains("Hybrid", ignoreCase = true) -> "Hybrid"
                else -> VehicleFuelTypeLookup.suggestFuelType(make, model)
            }

            if (year.isBlank() && make.isBlank() && model.isBlank()) {
                val msg = if (additionalError.isNotBlank()) additionalError else "Invalid VIN or unrecognized vehicle"
                return@withContext Result.failure(Exception(msg))
            }

            val info = DecodedVehicleInfo(
                year = year,
                make = make,
                model = model,
                fuelType = fuelTypeNormalized,
                displacementL = displacement,
                cylinders = cylinders,
                vehicleType = vehicleType,
                rawResponse = body
            )
            Result.success(info)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
