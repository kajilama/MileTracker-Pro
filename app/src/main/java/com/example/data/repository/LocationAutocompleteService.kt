package com.example.data.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.example.data.model.AddressSuggestion
import com.example.data.model.SavedPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.Collections

class LocationAutocompleteService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    // In-memory LRU cache for recent autocomplete queries to ensure lightning-fast debounce response
    private val queryCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<AddressSuggestion>>(30, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<AddressSuggestion>>?): Boolean {
                return size > 50
            }
        }
    )

    // Pre-curated local US stores near the driver with accurate coordinates and realistic addresses
    private val localKnownStores = listOf(
        LocalStoreSeed("Walmart Supercenter", "8400 Rosedale Hwy, Bakersfield, CA 93312", 35.3855, -119.1022, "Spark / Grocery Hub"),
        LocalStoreSeed("Walmart Neighborhood Market", "5075 Gosford Rd, Bakersfield, CA 93313", 35.3090, -119.0680, "Spark Hub"),
        LocalStoreSeed("Walmart Supercenter", "6225 Colony St, Bakersfield, CA 93307", 35.3015, -118.9950, "Spark Hub"),
        LocalStoreSeed("Walmart Supercenter", "2601 Fashion Pl, Bakersfield, CA 93306", 35.3780, -118.9500, "Spark Hub"),
        LocalStoreSeed("Costco Wholesale", "3800 Rosedale Hwy, Bakersfield, CA 93308", 35.3840, -119.0490, "Wholesale Club"),
        LocalStoreSeed("Albertsons Grocery", "8200 Stockdale Hwy, Bakersfield, CA 93311", 35.3530, -119.0980, "Instacart Hub"),
        LocalStoreSeed("Vons Supermarket", "4500 Coffee Rd, Bakersfield, CA 93308", 35.4050, -119.0790, "Instacart Hub"),
        LocalStoreSeed("Target", "3401 Mall View Rd, Bakersfield, CA 93306", 35.3820, -118.9480, "Retail / Grocery"),
        LocalStoreSeed("The Home Depot", "4000 Ming Ave, Bakersfield, CA 93309", 35.3380, -119.0520, "Home Improvement"),
        LocalStoreSeed("Starbucks Coffee", "8200 Rosedale Hwy, Bakersfield, CA 93312", 35.3850, -119.0980, "Coffee & Refreshment"),
        LocalStoreSeed("Trader Joe's", "8200 Stockdale Hwy, Bakersfield, CA 93311", 35.3520, -119.0970, "Grocery Store"),
        LocalStoreSeed("Walgreens Pharmacy", "4100 California Ave, Bakersfield, CA 93309", 35.3620, -119.0530, "Pharmacy"),
        LocalStoreSeed("CVS Pharmacy", "8200 Stockdale Hwy, Bakersfield, CA 93311", 35.3535, -119.0990, "Pharmacy"),
        LocalStoreSeed("McDonald's", "8300 Rosedale Hwy, Bakersfield, CA 93312", 35.3845, -119.1005, "Fast Food Hub"),
        LocalStoreSeed("Chick-fil-A", "2540 Mt Vernon Ave, Bakersfield, CA 93306", 35.3785, -118.9515, "Restaurant"),
        LocalStoreSeed("FedEx Ship Center", "3400 Pegasus Dr, Bakersfield, CA 93308", 35.4290, -119.0580, "Courier / Logistics")
    )

    private data class LocalStoreSeed(
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val category: String
    )

    suspend fun getSuggestions(
        query: String,
        userLat: Double?,
        userLng: Double?,
        savedPlaces: List<SavedPlace> = emptyList(),
        savedAddresses: List<String> = emptyList()
    ): List<AddressSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val cacheKey = "${trimmed.lowercase()}_${userLat ?: 0.0}_${userLng ?: 0.0}"
        queryCache[cacheKey]?.let { cached ->
            return@withContext cached
        }

        // Robust base coordinate: default to user's local operating area (Bakersfield, CA: 35.3733, -119.0187)
        val baseLat = userLat ?: 35.3733
        val baseLng = userLng ?: -119.0187

        val candidates = mutableListOf<CandidateSuggestion>()

        // 1. Check User's Saved Places (Highest Priority)
        for (place in savedPlaces) {
            if (place.name.contains(trimmed, ignoreCase = true) || place.address.contains(trimmed, ignoreCase = true)) {
                val pLat = place.lat ?: baseLat
                val pLng = place.lng ?: baseLng
                val dist = LocationService.calculateDistanceMiles(baseLat, baseLng, pLat, pLng)

                candidates.add(
                    CandidateSuggestion(
                        title = place.name,
                        subtitle = place.address,
                        fullAddress = "${place.name}, ${place.address}",
                        lat = pLat,
                        lng = pLng,
                        isStore = place.category.contains("Store", ignoreCase = true) || place.category.contains("Spark", ignoreCase = true),
                        isSaved = true,
                        distanceMiles = dist,
                        priority = 0
                    )
                )
            }
        }

        // 2. Check Pre-Seeded Local Stores Matching Query (e.g. "walmart", "costco", "albertsons", "target")
        for (store in localKnownStores) {
            if (store.name.contains(trimmed, ignoreCase = true) || store.address.contains(trimmed, ignoreCase = true) || store.category.contains(trimmed, ignoreCase = true)) {
                val dist = LocationService.calculateDistanceMiles(baseLat, baseLng, store.lat, store.lng)
                if (candidates.none { it.fullAddress.equals(store.address, ignoreCase = true) || (it.title.equals(store.name, ignoreCase = true) && (it.distanceMiles ?: 999.0) < 5.0) }) {
                    candidates.add(
                        CandidateSuggestion(
                            title = store.name,
                            subtitle = store.address,
                            fullAddress = "${store.name}, ${store.address}",
                            lat = store.lat,
                            lng = store.lng,
                            isStore = true,
                            isSaved = false,
                            distanceMiles = dist,
                            priority = 1
                        )
                    )
                }
            }
        }

        // 3. Check Past Trip Addresses
        for (addr in savedAddresses) {
            if (addr.contains(trimmed, ignoreCase = true) && candidates.none { it.fullAddress.equals(addr, ignoreCase = true) }) {
                candidates.add(
                    CandidateSuggestion(
                        title = addr.substringBefore(",").trim(),
                        subtitle = if (addr.contains(",")) addr.substringAfter(",").trim() else "Past Trip",
                        fullAddress = addr,
                        lat = baseLat,
                        lng = baseLng,
                        isStore = false,
                        isSaved = true,
                        distanceMiles = 1.0,
                        priority = 2
                    )
                )
            }
        }

        // 4. PHOTON API (photon.komoot.io) with lat/lon bias, limit 8, US country filter
        var photonSuccess = false
        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val photonUrl = "https://photon.komoot.io/api/?q=$encodedQuery&lat=$baseLat&lon=$baseLng&limit=8"

            val request = Request.Builder()
                .url(photonUrl)
                .header("User-Agent", "miletracker6/1.0 (Android; DeliveryTrackingApp)")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                if (!bodyStr.isNullOrEmpty()) {
                    val json = JSONObject(bodyStr)
                    val features = json.optJSONArray("features")
                    if (features != null && features.length() > 0) {
                        for (i in 0 until features.length()) {
                            val feature = features.optJSONObject(i) ?: continue
                            val geometry = feature.optJSONObject("geometry")
                            val coordinates = geometry?.optJSONArray("coordinates")
                            val lon = coordinates?.optDouble(0, 0.0) ?: 0.0
                            val lat = coordinates?.optDouble(1, 0.0) ?: 0.0

                            val props = feature.optJSONObject("properties") ?: continue
                            val countryCode = props.optString("countrycode", "").uppercase(Locale.US)
                            val country = props.optString("country", "")

                            // Strict US filter
                            if (countryCode.isNotBlank() && countryCode != "US") continue
                            if (countryCode.isBlank() && country.isNotBlank() && !country.contains("United States", ignoreCase = true)) continue

                            val name = props.optString("name", "").trim()
                            val houseNumber = props.optString("housenumber", "").trim()
                            val street = props.optString("street", "").trim()
                            val city = props.optString("city", props.optString("town", props.optString("district", props.optString("county", "")))).trim()
                            val state = props.optString("state", "").trim()
                            val postcode = props.optString("postcode", "").trim()

                            // Formulate residential vs store address representations
                            val isResidential = houseNumber.isNotBlank() || (street.isNotBlank() && name.isBlank())
                            val title = when {
                                houseNumber.isNotBlank() && street.isNotBlank() -> "$houseNumber $street"
                                name.isNotBlank() -> name
                                street.isNotBlank() -> street
                                else -> trimmed
                            }

                            val subParts = listOfNotNull(
                                if (street.isNotBlank() && title != street && !title.contains(street)) street else null,
                                city.takeIf { it.isNotBlank() && title != it },
                                state.takeIf { it.isNotBlank() },
                                postcode.takeIf { it.isNotBlank() }
                            )
                            val subtitle = if (subParts.isNotEmpty()) subParts.joinToString(", ") else "United States"
                            val fullAddress = when {
                                houseNumber.isNotBlank() && street.isNotBlank() -> {
                                    listOfNotNull("$houseNumber $street", city.takeIf { it.isNotBlank() }, state.takeIf { it.isNotBlank() }, postcode.takeIf { it.isNotBlank() }).joinToString(", ")
                                }
                                name.isNotBlank() && street.isNotBlank() -> {
                                    listOfNotNull(name, street, city.takeIf { it.isNotBlank() }, state.takeIf { it.isNotBlank() }).joinToString(", ")
                                }
                                else -> "$title, $subtitle"
                            }

                            val dist = if (lat != 0.0 && lon != 0.0) {
                                LocationService.calculateDistanceMiles(baseLat, baseLng, lat, lon)
                            } else null

                            if (candidates.none { it.fullAddress.equals(fullAddress, ignoreCase = true) || (it.title.equals(title, ignoreCase = true) && (it.distanceMiles ?: 999.0) < 0.2) }) {
                                candidates.add(
                                    CandidateSuggestion(
                                        title = title,
                                        subtitle = subtitle,
                                        fullAddress = fullAddress,
                                        lat = lat,
                                        lng = lon,
                                        isStore = !isResidential,
                                        isSaved = false,
                                        distanceMiles = dist,
                                        priority = 3
                                    )
                                )
                                photonSuccess = true
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Photon network/parsing exception handled
        }

        // 5. ANDROID NATIVE GEOCODER (Strictly US Locale & Bounded Around User)
        // Fallback or supplementary residential address resolver
        if (!photonSuccess || candidates.size < 4) {
            try {
                val geocoder = Geocoder(context, Locale.US)
                @Suppress("DEPRECATION")
                val geoList = geocoder.getFromLocationName(
                    trimmed,
                    8,
                    baseLat - 0.60,
                    baseLng - 0.60,
                    baseLat + 0.60,
                    baseLng + 0.60
                )

                if (geoList != null) {
                    for (addr in geoList) {
                        if (addr.countryCode != null && addr.countryCode != "US") continue

                        val full = formatAddress(addr) ?: continue
                        val title = addr.featureName ?: addr.thoroughfare ?: full.substringBefore(",")
                        val subtitle = full.removePrefix(title).removePrefix(", ").trim().ifBlank { "United States" }
                        val dist = LocationService.calculateDistanceMiles(baseLat, baseLng, addr.latitude, addr.longitude)

                        if (candidates.none { it.fullAddress.equals(full, ignoreCase = true) }) {
                            candidates.add(
                                CandidateSuggestion(
                                    title = title,
                                    subtitle = subtitle,
                                    fullAddress = full,
                                    lat = addr.latitude,
                                    lng = addr.longitude,
                                    isStore = addr.featureName != null && addr.featureName != addr.thoroughfare,
                                    isSaved = false,
                                    distanceMiles = dist,
                                    priority = 4
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Native geocoder fallback handled
            }
        }

        // CRITICAL: STRICT SORTING BY DISTANCE FROM USER!
        // Nearest branches and closest residential houses appear first!
        val sorted = candidates.sortedWith(
            compareBy<CandidateSuggestion> {
                it.distanceMiles ?: 9999.0
            }.thenBy {
                it.priority
            }
        )

        // Return top results with clean distance label in miles
        val results = sorted.take(8).map { cand ->
            val distLabel = if (cand.distanceMiles != null && cand.distanceMiles < 500.0) {
                "%.1f mi away".format(cand.distanceMiles)
            } else "Nearby"

            AddressSuggestion(
                title = cand.title,
                subtitle = cand.subtitle,
                fullAddress = cand.fullAddress,
                latitude = cand.lat,
                longitude = cand.lng,
                isStoreOrPlace = cand.isStore,
                isSavedPlace = cand.isSaved,
                distanceDisplay = distLabel
            )
        }

        queryCache[cacheKey] = results
        results
    }

    private data class CandidateSuggestion(
        val title: String,
        val subtitle: String,
        val fullAddress: String,
        val lat: Double?,
        val lng: Double?,
        val isStore: Boolean,
        val isSaved: Boolean,
        val distanceMiles: Double?,
        val priority: Int
    )

    private fun formatAddress(address: Address?): String? {
        if (address == null) return null
        val lines = (0..address.maxAddressLineIndex).mapNotNull { address.getAddressLine(it) }
        return if (lines.isNotEmpty()) {
            lines.joinToString(", ")
        } else {
            val parts = listOfNotNull(
                address.featureName ?: address.thoroughfare,
                address.locality,
                address.adminArea,
                address.postalCode
            )
            if (parts.isNotEmpty()) parts.joinToString(", ") else null
        }
    }
}
