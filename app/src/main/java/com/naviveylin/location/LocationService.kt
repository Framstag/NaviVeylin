package com.naviveylin.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Provides GPS location updates from Fused (Play Services) and/or LocationManager.
 * Both providers are started so the app works with and without Play Services.
 * Duplicate fixes from different providers are filtered by fix timestamp.
 */
@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var fusedCallback: LocationCallback? = null
    private var gpsListener: LocationListener? = null

    // Timestamp of the last emitted fix, used to drop duplicates delivered by
    // both Fused and LocationManager with the same location.time.
    private var lastEmittedFixTimeMs: Long = 0L

    private val useFusedProvider: Boolean = run {
        val availability = GoogleApiAvailability.getInstance()
        val result = try {
            availability.isGooglePlayServicesAvailable(context)
        } catch (e: Exception) {
            Log.w(TAG, "Play Services availability check failed, using LocationManager", e)
            ConnectionResult.SERVICE_MISSING
        }
        val fused = result == ConnectionResult.SUCCESS
        Log.d(TAG, "Google Play Services available: $fused (result=$result)")
        if (fused) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            Log.d(TAG, "FusedLocationProviderClient initialized")
        } else {
            Log.d(TAG, "Falling back to LocationManager")
            val providers = locationManager.allProviders.joinToString(", ")
            Log.d(TAG, "Available location providers: $providers")
            try {
                val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                Log.d(TAG, "GPS provider enabled: $gpsEnabled, Network provider enabled: $networkEnabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error checking providers", e)
            }
        }
        fused
    }

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    @VisibleForTesting
    fun setLocationForTest(location: Location?) {
        _location.value = location
    }

    fun startLocationUpdates() {
        if (!hasPermission) {
            Log.d(TAG, "startLocationUpdates: no permission, skipping")
            return
        }
        if (useFusedProvider) {
            startFusedUpdates()
        }
        startManagerUpdates()
    }

    private fun startFusedUpdates() {
        if (fusedCallback != null) {
            Log.d(TAG, "startFusedUpdates: already running")
            return
        }

        val client = fusedLocationClient
        if (client == null) {
            Log.w(TAG, "startFusedUpdates: client not available, falling back to LocationManager")
            startManagerUpdates()
            return
        }

        val request = LocationRequest.Builder(
            LocationRequest.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            setMinUpdateDistanceMeters(MIN_DISTANCE_M)
        }.build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val bestLocation = result.lastLocation
                if (bestLocation != null && shouldEmit(bestLocation)) {
                    _location.value = bestLocation
                }
            }
        }

        fusedCallback = callback

        try {
            client.requestLocationUpdates(request, callback, null)
            Log.d(TAG, "startFusedUpdates: requested")
        } catch (e: SecurityException) {
            Log.e(TAG, "startFusedUpdates: security exception", e)
            fusedCallback = null
        }
    }

    private fun startManagerUpdates() {
        if (gpsListener != null) {
            Log.d(TAG, "startManagerUpdates: already running")
            return
        }

        Log.d(TAG, "startManagerUpdates: requesting GPS updates")

        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(TAG, "LocationManager onLocationChanged: ${"%.6f".format(location.latitude)},${"%.6f".format(location.longitude)} acc=${location.accuracy}")
                if (shouldEmit(location)) {
                    _location.value = location
                }
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                Log.d(TAG, "LocationManager onStatusChanged: provider=$provider status=$status")
            }

            override fun onProviderEnabled(provider: String) {
                Log.d(TAG, "LocationManager onProviderEnabled: $provider")
            }

            override fun onProviderDisabled(provider: String) {
                Log.d(TAG, "LocationManager onProviderDisabled: $provider")
            }
        }

        gpsListener = listener

        try {
            // Request all providers independently — one failure shouldn't block others.
            requestProvider(LocationManager.GPS_PROVIDER, listener)
            requestProvider(LocationManager.NETWORK_PROVIDER, listener)
            requestProvider(LocationManager.PASSIVE_PROVIDER, listener)
        } catch (e: SecurityException) {
            Log.e(TAG, "startManagerUpdates: security exception", e)
            gpsListener = null
        }
    }

    private fun requestProvider(provider: String, listener: LocationListener) {
        try {
            locationManager.requestLocationUpdates(
                provider,
                UPDATE_INTERVAL_MS,
                MIN_DISTANCE_M,
                listener,
                Looper.getMainLooper()
            )
            Log.d(TAG, "startManagerUpdates: $provider provider requested")
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "startManagerUpdates: $provider provider not available, skipping")
        } catch (e: SecurityException) {
            Log.e(TAG, "startManagerUpdates: $provider security exception", e)
        }
    }

    /**
     * Stop receiving GPS location updates.
     */
    fun stopLocationUpdates() {
        // Always stop both providers
        val cb = fusedCallback
        if (cb != null) {
            fusedLocationClient?.removeLocationUpdates(cb)
            fusedCallback = null
        }
        val l = gpsListener
        if (l != null) {
            locationManager.removeUpdates(l)
            gpsListener = null
        }
        Log.d(TAG, "stopLocationUpdates: stopped")
    }

    /**
     * Emit a location only if it is not a duplicate of the previously emitted fix.
     * Fused and LocationManager deliver the same underlying fix with the same
     * location.time; dropping by timestamp removes the duplicate emission.
     */
    private fun shouldEmit(location: Location): Boolean {
        val fixTime = location.time
        val last = lastEmittedFixTimeMs
        if (fixTime == last && fixTime != 0L) {
            return false
        }
        lastEmittedFixTimeMs = fixTime
        return true
    }

    companion object {
        private const val TAG = "LocationService"
        private const val UPDATE_INTERVAL_MS = 1000L
        private const val FASTEST_INTERVAL_MS = 500L
        private const val MIN_DISTANCE_M = 5.0f
    }
}
