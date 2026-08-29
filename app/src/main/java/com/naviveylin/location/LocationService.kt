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
import com.naviveylin.core.DiagnosticsLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A processed GPS fix with two bearings:
 * - [smoothedBearing]: for map rotation — stable, no render churn.
 * - [markerBearing]: for the marker arrow — freshest signal, no added lag.
 *
 * Provider-agnostic: consumers receive the same shape whether the fix came
 * from Fused (Play Services) or the raw [LocationManager] fallback.
 */
data class GpsFix(
    val lat: Double,
    val lon: Double,
    /** Horizontal accuracy in meters, or negative when unknown. */
    val accuracy: Double,
    /** Ground speed in km/h, or [Double.NaN] when unknown. */
    val speedKmH: Double,
    /** Smoothed bearing in degrees [0,360), or [Double.NaN] when unknown. */
    val smoothedBearing: Double,
    /** Freshest bearing in degrees [0,360), or [Double.NaN] when unknown. */
    val markerBearing: Double,
    /** Fix timestamp (ms since epoch). */
    val time: Long
)

/** Which location source feeds the [BearingFilter]. */
internal enum class BearingSource { FUSED, MANAGER }

/**
 * Produces the two bearings consumed by the UI from each incoming fix.
 *
 * Provider-aware (knowledge stays inside the location layer):
 * - [BearingSource.FUSED]: Fused already smooths its bearing, so the marker
 *   bearing passes through unchanged (zero added lag) and the smoothed
 *   bearing is only lightly low-pass filtered.
 * - [BearingSource.MANAGER]: the raw [LocationManager] fallback fakes Fused's
 *   quality — course-over-ground is derived from the recent position track
 *   with an EMA low-pass, turn reset, and teleport reset; the marker bearing
 *   is the latest two-point segment bearing (freshest stable signal).
 */
internal class BearingFilter(private val source: BearingSource) {

    // Course-over-ground state (LocationManager path only)
    private val COURSE_HISTORY_SIZE = 10
    private val courseLats = DoubleArray(COURSE_HISTORY_SIZE) { Double.NaN }
    private val courseLons = DoubleArray(COURSE_HISTORY_SIZE) { Double.NaN }
    private var courseIndex = 0
    private val MIN_COURSE_DISTANCE_M = 40.0
    /** Short distance used right after a reset/start so the new direction is established quickly. */
    private val MIN_COURSE_DISTANCE_FAST_M = 10.0
    /** Minimum segment length before it may trigger a turn reset (avoids GPS-noise resets). */
    private val MIN_SEGMENT_FOR_TURN_M = 8.0
    private val COURSE_TURN_RESET_DEG = 45.0
    private val COURSE_LOW_PASS_ALPHA = 0.3
    /** Faster low-pass while the course history is not yet stable (after reset/start). */
    private val COURSE_LOW_PASS_ALPHA_FAST = 0.7
    /** Position jump that counts as a teleport (first fix / GPS drop) — clears the course history. */
    private val TELEPORT_JUMP_M = 500.0
    /** Light EMA alpha for the Fused path — Fused already smooths, only residual jitter is damped. */
    private val FUSED_LIGHT_ALPHA = 0.5

    private var lastCourseBearing = Double.NaN
    private var lastSmoothedBearing = Double.NaN
    private var lastSegmentBearing = Double.NaN
    /** True once at least MIN_COURSE_DISTANCE_M of track is in the history. */
    private var courseStable = false

    /**
     * @return Pair(smoothedBearing, markerBearing); NaN components when no
     * bearing is available yet.
     */
    fun process(lat: Double, lon: Double, bearing: Double, hasBearing: Boolean): Pair<Double, Double> {
        return when (source) {
            BearingSource.FUSED -> processFused(bearing, hasBearing)
            BearingSource.MANAGER -> processManager(lat, lon)
        }
    }

    private fun processFused(bearing: Double, hasBearing: Boolean): Pair<Double, Double> {
        if (!hasBearing || bearing < 0.0) {
            return Pair(lastSmoothedBearing.takeIf { !it.isNaN() } ?: Double.NaN, Double.NaN)
        }
        val marker = bearing
        val smoothed = if (lastSmoothedBearing.isNaN()) {
            bearing
        } else {
            lastSmoothedBearing + normalizeAngleDeg(bearing - lastSmoothedBearing) * FUSED_LIGHT_ALPHA
        }
        lastSmoothedBearing = smoothed
        return Pair(smoothed, marker)
    }

    private fun processManager(lat: Double, lon: Double): Pair<Double, Double> {
        addCoursePoint(lat, lon)
        val (courseBearing, courseDist) = computeCourseBearing()
        val courseAlpha = if (courseStable || courseDist >= MIN_COURSE_DISTANCE_M) {
            COURSE_LOW_PASS_ALPHA
        } else {
            COURSE_LOW_PASS_ALPHA_FAST
        }
        val smoothed = smoothCourseBearing(courseBearing, courseAlpha)
        // Freshest stable signal for the marker: latest segment, then window
        // course, then the last smoothed value.
        val marker = when {
            !lastSegmentBearing.isNaN() -> lastSegmentBearing
            !courseBearing.isNaN() -> courseBearing
            else -> lastSmoothedBearing.takeIf { !it.isNaN() } ?: Double.NaN
        }
        return Pair(smoothed, marker)
    }

    /** Append a new position to the course-over-ground history. */
    private fun addCoursePoint(lat: Double, lon: Double) {
        val newestIdx = (courseIndex - 1 + COURSE_HISTORY_SIZE) % COURSE_HISTORY_SIZE
        if (!courseLats[newestIdx].isNaN()) {
            val dist = distanceMeters(lat, lon, courseLats[newestIdx], courseLons[newestIdx])
            if (dist > TELEPORT_JUMP_M) {
                // Teleport: clear stale history so the course bearing starts fresh.
                resetCourseHistory()
            } else {
                if (dist >= 2.0) {
                    lastSegmentBearing = bearingFromCourse(courseLats[newestIdx], courseLons[newestIdx], lat, lon)
                }
                if (dist >= MIN_SEGMENT_FOR_TURN_M && !lastSmoothedBearing.isNaN()) {
                    // If the latest segment turns sharply, the old history is from
                    // before the turn and must not influence the new course.
                    val diff = kotlin.math.abs(normalizeAngleDeg(lastSegmentBearing - lastSmoothedBearing))
                    if (diff > COURSE_TURN_RESET_DEG) {
                        // The segment that triggered the reset IS the new direction —
                        // keep it as the freshest marker bearing so the marker points
                        // the new way immediately instead of falling back to the old
                        // direction for a fix.
                        val freshSegment = lastSegmentBearing
                        resetCourseHistory()
                        lastSegmentBearing = freshSegment
                    }
                }
            }
        } else {
            // First point of a fresh history: no segment yet.
            lastSegmentBearing = Double.NaN
        }
        courseLats[courseIndex] = lat
        courseLons[courseIndex] = lon
        courseIndex = (courseIndex + 1) % COURSE_HISTORY_SIZE
    }

    private fun resetCourseHistory() {
        for (i in courseLats.indices) {
            courseLats[i] = Double.NaN
            courseLons[i] = Double.NaN
        }
        courseIndex = 0
        lastCourseBearing = Double.NaN
        lastSmoothedBearing = Double.NaN
        lastSegmentBearing = Double.NaN
        courseStable = false
    }

    /**
     * Compute course-over-ground bearing from the oldest history point that is at
     * least MIN_COURSE_DISTANCE_M (or the fast distance while history is not yet
     * stable) away from the newest point. Returns (bearing, distance). Falls back
     * to the last computed bearing when not enough distance has been accumulated.
     */
    private fun computeCourseBearing(): Pair<Double, Double> {
        val newestIdx = (courseIndex - 1 + COURSE_HISTORY_SIZE) % COURSE_HISTORY_SIZE
        val newestLat = courseLats[newestIdx]
        val newestLon = courseLons[newestIdx]
        if (newestLat.isNaN()) return Pair(Double.NaN, 0.0)

        val minDist = if (courseStable) MIN_COURSE_DISTANCE_M else MIN_COURSE_DISTANCE_FAST_M
        var i = newestIdx
        var totalDist = 0.0
        var usedIdx = -1
        for (step in 1 until COURSE_HISTORY_SIZE) {
            val prev = (i - 1 + COURSE_HISTORY_SIZE) % COURSE_HISTORY_SIZE
            if (courseLats[prev].isNaN()) break
            totalDist += distanceMeters(courseLats[prev], courseLons[prev], courseLats[i], courseLons[i])
            if (totalDist >= minDist) {
                usedIdx = prev
                break
            }
            i = prev
        }
        if (usedIdx >= 0) {
            val bearing = bearingFromCourse(courseLats[usedIdx], courseLons[usedIdx], newestLat, newestLon)
            lastCourseBearing = bearing
            if (totalDist >= MIN_COURSE_DISTANCE_M) courseStable = true
            return Pair(bearing, totalDist)
        }
        return Pair(lastCourseBearing.takeIf { !it.isNaN() } ?: Double.NaN, totalDist)
    }

    /**
     * Low-pass filter the course-over-ground bearing. Returns NaN when no valid
     * bearing is available (caller falls back to the last used bearing).
     */
    private fun smoothCourseBearing(newBearing: Double, alpha: Double): Double {
        if (newBearing.isNaN()) {
            return lastSmoothedBearing.takeIf { !it.isNaN() } ?: Double.NaN
        }
        val prev = lastSmoothedBearing
        val smoothed = if (prev.isNaN()) {
            newBearing
        } else {
            val diff = normalizeAngleDeg(newBearing - prev)
            val raw = prev + diff * alpha
            val norm = raw % 360.0
            if (norm < 0) norm + 360.0 else norm
        }
        lastSmoothedBearing = smoothed
        return smoothed
    }

    /** Normalize an angle in degrees to (-180,180]. */
    private fun normalizeAngleDeg(deg: Double): Double {
        var d = deg
        while (d <= -180.0) d += 360.0
        while (d > 180.0) d -= 360.0
        return d
    }

    /** Bearing in degrees [0,360) from (lat1,lon1) to (lat2,lon2). */
    private fun bearingFromCourse(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2Rad)
        val x = kotlin.math.cos(lat1Rad) * kotlin.math.sin(lat2Rad) -
                kotlin.math.sin(lat1Rad) * kotlin.math.cos(lat2Rad) * kotlin.math.cos(dLon)
        var bearing = Math.toDegrees(kotlin.math.atan2(y, x))
        if (bearing < 0) bearing += 360.0
        return bearing
    }

    /** Approximate great-circle distance in meters (haversine). */
    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusM = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = (kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2))
            .coerceIn(0.0, 1.0)
        return earthRadiusM * 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
    }
}

/**
 * Provides GPS location updates from Fused (Play Services) or LocationManager.
 * Strict fallback: Fused is the sole source when Play Services is available
 * (the OS location service applies its own smoothing); LocationManager
 * (GPS/NETWORK/PASSIVE) is used only when Fused is unavailable, e.g. on
 * GMS-less devices (AAOS head units, Huawei, sideload). The two never run
 * simultaneously. Duplicate fixes from different providers are filtered by
 * timestamp + position.
 *
 * Emits a uniform [GpsFix] with two bearings (smoothed for map rotation,
 * freshest for the marker) — provider knowledge stays inside this class.
 */
@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Test seam: forces the Play Services availability decision instead of
     * querying [GoogleApiAvailability]. Null (default) = runtime check.
     */
    private var playServicesAvailableOverride: Boolean? = null

    @VisibleForTesting
    internal constructor(context: Context, playServicesAvailable: Boolean) : this(context) {
        this.playServicesAvailableOverride = playServicesAvailable
    }

    private val _location = MutableStateFlow<GpsFix?>(null)
    val location: StateFlow<GpsFix?> = _location.asStateFlow()

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var fusedCallback: LocationCallback? = null
    private var gpsListener: LocationListener? = null

    // Timestamp + position of the last emitted fix, used to drop duplicates
    // delivered by both Fused and LocationManager with the same location.
    private var lastEmittedFixTimeMs: Long = 0L
    private var lastEmittedLat = Double.NaN
    private var lastEmittedLon = Double.NaN

    /**
     * Lazy so the test override ([playServicesAvailableOverride]) set by the
     * secondary constructor is visible before the decision is computed.
     */
    private val useFusedProvider: Boolean by lazy {
        val fused = playServicesAvailableOverride ?: isPlayServicesAvailable()
        Log.d(TAG, "Google Play Services available: $fused")
        if (fused) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            Log.d(TAG, "FusedLocationProviderClient initialized")
            DiagnosticsLog.log(TAG, "GPS source: OS location service (FusedLocationProviderClient)")
        } else {
            Log.d(TAG, "Falling back to LocationManager")
            DiagnosticsLog.log(TAG, "GPS source: direct LocationManager (GPS/NETWORK/PASSIVE providers)")
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

    /** Bearing filter matching the active provider; created once the provider is decided. */
    private val bearingFilter: BearingFilter by lazy {
        BearingFilter(if (useFusedProvider) BearingSource.FUSED else BearingSource.MANAGER)
    }

    private fun isPlayServicesAvailable(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val result = try {
            availability.isGooglePlayServicesAvailable(context)
        } catch (e: Exception) {
            Log.w(TAG, "Play Services availability check failed, using LocationManager", e)
            ConnectionResult.SERVICE_MISSING
        }
        val fused = result == ConnectionResult.SUCCESS
        Log.d(TAG, "Google Play Services available: $fused (result=$result)")
        return fused
    }

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    @VisibleForTesting
    fun setLocationForTest(location: Location?) {
        // Bypasses the bearing filter: a plain lat/lon injection for tests that
        // only check position. Avoids the Play Services availability check in
        // Robolectric (the filter is provider-aware and lazy-initialized).
        _location.value = location?.let {
            GpsFix(
                lat = it.latitude,
                lon = it.longitude,
                accuracy = if (it.hasAccuracy()) it.accuracy.toDouble() else -1.0,
                speedKmH = if (it.hasSpeed()) it.speed * 3.6 else Double.NaN,
                smoothedBearing = Double.NaN,
                markerBearing = Double.NaN,
                time = it.time
            )
        }
    }

    /** Test hook: inject a fully-formed fix, bypassing the bearing filter. */
    @VisibleForTesting
    fun setGpsFixForTest(fix: GpsFix?) {
        _location.value = fix
    }

    /** True when the Fused provider path is active (callback registered). */
    @VisibleForTesting
    internal fun isFusedActive(): Boolean = fusedCallback != null

    /** Test hook: deliver a fix through the registered Fused callback. */
    @VisibleForTesting
    internal fun simulateFusedLocation(location: Location) {
        fusedCallback?.onLocationResult(LocationResult.create(listOf(location)))
    }

    fun startLocationUpdates() {
        if (!hasPermission) {
            Log.d(TAG, "startLocationUpdates: no permission, skipping")
            return
        }
        // Strict fallback: Fused and LocationManager never run simultaneously.
        // On Play Services devices the OS location service (Fused) already
        // applies its own smoothing — raw LocationManager fixes would bypass
        // it and cause marker jumps. LocationManager is used only when Fused
        // is unavailable (GMS-less devices: AAOS head units, Huawei, sideload).
        if (useFusedProvider) {
            startFusedUpdates()
        } else {
            startManagerUpdates()
        }
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
                    _location.value = toGpsFix(bestLocation)
                }
            }
        }

        fusedCallback = callback

        try {
            // Explicit main looper: requestLocationUpdates with a null looper
            // requires the calling thread to have one (fails with
            // "invalid null looper" when called from a background thread,
            // e.g. the AA warmup).
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
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
                    _location.value = toGpsFix(location)
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

    /** Convert a raw [Location] into a [GpsFix], running the bearing filter. */
    private fun toGpsFix(loc: Location): GpsFix {
        val hasBearing = loc.hasBearing()
        val (smoothed, marker) = bearingFilter.process(
            loc.latitude,
            loc.longitude,
            if (hasBearing) loc.bearing.toDouble() else -1.0,
            hasBearing
        )
        return GpsFix(
            lat = loc.latitude,
            lon = loc.longitude,
            accuracy = if (loc.hasAccuracy()) loc.accuracy.toDouble() else -1.0,
            speedKmH = if (loc.hasSpeed()) loc.speed * 3.6 else Double.NaN,
            smoothedBearing = smoothed,
            markerBearing = marker,
            time = loc.time
        )
    }

    /**
     * Emit a location only if it is not a duplicate of the previously emitted fix.
     * Fused and LocationManager deliver the same underlying fix with the same
     * location.time; dropping by timestamp removes the duplicate emission.
     */
    /**
     * Drop only truly identical duplicates (same time AND same position) —
     * e.g. the same fix delivered by both Fused and LocationManager. Moving
     * fixes with identical timestamps (emulator GPX replay) must pass
     * through, otherwise the track freezes after the first fix.
     */
    private fun shouldEmit(location: Location): Boolean {
        val sameTime = location.time == lastEmittedFixTimeMs && location.time != 0L
        val samePos = location.latitude == lastEmittedLat && location.longitude == lastEmittedLon
        if (sameTime && samePos) {
            Log.d(TAG, "shouldEmit: duplicate fix dropped (t=${location.time})")
            return false
        }
        lastEmittedFixTimeMs = location.time
        lastEmittedLat = location.latitude
        lastEmittedLon = location.longitude
        return true
    }

    companion object {
        private const val TAG = "LocationService"
        private const val UPDATE_INTERVAL_MS = 1000L
        private const val FASTEST_INTERVAL_MS = 500L
        private const val MIN_DISTANCE_M = 5.0f
    }
}
