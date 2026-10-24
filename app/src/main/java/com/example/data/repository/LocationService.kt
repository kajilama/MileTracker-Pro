package com.example.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class LocationService(private val context: Context) {
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.IO) {
        try {
            // First try high accuracy fused location provider with 2s timeout
            val cancellationTokenSource = CancellationTokenSource()
            val location = kotlinx.coroutines.withTimeoutOrNull(2000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationTokenSource.token
                    ).addOnSuccessListener { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }.addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (location != null) return@withContext location

            // Fallback to last known location from Fused client with 1s timeout
            val lastLocation = kotlinx.coroutines.withTimeoutOrNull(1000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }.addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (lastLocation != null) return@withContext lastLocation

            // System LocationManager fallback
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val gpsLoc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLoc = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (gpsLoc != null) return@withContext gpsLoc
            if (netLoc != null) return@withContext netLoc

            // Fallback default US location (Bakersfield, CA: 35.3733, -119.0187)
            Location("default_provider").apply {
                latitude = 35.3733
                longitude = -119.0187
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Location("default_provider").apply {
                latitude = 35.3733
                longitude = -119.0187
            }
        }
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var resultAddress: String? = null
                suspendCancellableCoroutine<Unit> { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            resultAddress = formatAddress(addresses.firstOrNull())
                            cont.resume(Unit)
                        }
                        override fun onError(errorMessage: String?) {
                            cont.resume(Unit)
                        }
                    })
                }
                resultAddress ?: formatCoordinates(latitude, longitude)
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                formatAddress(addresses?.firstOrNull()) ?: formatCoordinates(latitude, longitude)
            }
        } catch (e: Exception) {
            formatCoordinates(latitude, longitude)
        }
    }

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

    private fun formatCoordinates(lat: Double, lng: Double): String {
        return "%.5f, %.5f".format(Locale.US, lat, lng)
    }

    companion object {
        fun calculateDistanceMiles(
            startLat: Double?,
            startLng: Double?,
            endLat: Double?,
            endLng: Double?
        ): Double {
            if (startLat == null || startLng == null || endLat == null || endLng == null) return 0.0
            val results = FloatArray(1)
            Location.distanceBetween(startLat, startLng, endLat, endLng, results)
            val meters = results[0]
            val miles = meters * 0.000621371
            return String.format(Locale.US, "%.1f", miles).toDoubleOrNull() ?: miles
        }
    }
}
