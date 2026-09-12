package com.naviveylin.data

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Pure lux → dark-classification pipeline: hysteresis ([classifyLux]) +
 * debounce ([DebouncedSignal]). Returns null when the stable classification
 * did not change, so callers only act on actual flips. Extracted from
 * [AmbientLightMonitor] for unit testing without Android sensors.
 */
class AmbientLightPipeline(
    private val sensitivity: AmbientLightSensitivity = AmbientLightSensitivity.HIGH,
    private val debounced: DebouncedSignal = DebouncedSignal(),
    private val nowMs: () -> Long = SystemClock::elapsedRealtime
) {
    private var lastDark: Boolean? = null

    /** Feed a raw lux reading; returns the new stable classification or null. */
    fun onLux(lux: Float): Boolean? {
        val dark = classifyLux(debounced.value, lux, sensitivity)
        val stable = debounced.update(dark, nowMs())
        return if (stable != lastDark) {
            lastDark = stable
            stable
        } else {
            null
        }
    }
}

/**
 * Reads the ambient light sensor ([Sensor.TYPE_LIGHT]) and reports the
 * debounced dark classification. No permission needed. When no light sensor
 * exists, [available] is false and [start] is a no-op — the controller falls
 * back to the system night mode signal.
 *
 * Lifecycle: [start] on foreground + gated conditions, [stop] otherwise;
 * [stop] reports null so the environment source reverts to the system signal.
 */
class AmbientLightMonitor(
    private val sensorManager: SensorManager,
    private val onClassification: (Boolean?) -> Unit,
    private var pipeline: AmbientLightPipeline = AmbientLightPipeline()
) : SensorEventListener {

    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var running = false
    private var lastLux: Float? = null

    /**
     * Completes a pending flip after the debounce window even when no further
     * events arrive. Real sensors stream continuously (the debounce completes
     * on a later event); the emulator sends bursts on value change, so without
     * this a flip started by the burst would never complete.
     */
    private val handler = Handler(Looper.getMainLooper())
    private val completionRunnable = object : Runnable {
        override fun run() {
            val lux = lastLux ?: return
            val flip = pipeline.onLux(lux)
            if (flip != null) {
                Log.d(TAG, "classification ${if (flip) "dark" else "light"} (lux=$lux, debounced)")
                onClassification(flip)
            }
        }
    }

    /** True when the device has a light sensor. */
    val available: Boolean get() = lightSensor != null

    /**
     * Switch the sensitivity level: swaps in a fresh pipeline (new thresholds,
     * debounce/hysteresis state reset) and re-feeds the last reading so the
     * next event classifies with the new level. Call while stopped or before
     * [start]; when running the fresh state takes effect on the next event.
     */
    fun setSensitivity(sensitivity: AmbientLightSensitivity) {
        pipeline = AmbientLightPipeline(sensitivity = sensitivity)
        lastLux?.let { lux ->
            val flip = pipeline.onLux(lux)
            if (flip != null) {
                Log.d(TAG, "classification after sensitivity change ${if (flip) "dark" else "light"} (lux=$lux)")
                onClassification(flip)
            }
        }
    }

    fun start() {
        if (running) return
        if (lightSensor == null) {
            Log.w(TAG, "start requested but no light sensor available — sensor mode inactive, system signal used")
            return
        }
        val ok = sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        if (ok) {
            running = true
            Log.d(TAG, "ambient light sensor started")
        } else {
            Log.w(TAG, "ambient light sensor register failed")
        }
    }

    fun stop() {
        if (!running) return
        sensorManager.unregisterListener(this)
        running = false
        handler.removeCallbacks(completionRunnable)
        Log.d(TAG, "ambient light sensor stopped")
        onClassification(null)
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LIGHT) return
        val lux = event.values[0]
        lastLux = lux
        val flip = pipeline.onLux(lux)
        if (flip != null) {
            Log.d(TAG, "classification ${if (flip) "dark" else "light"} (lux=$lux)")
            onClassification(flip)
        }
        // Re-evaluate after the debounce window in case events stop (emulator
        // bursts); a newer event reschedules this.
        handler.removeCallbacks(completionRunnable)
        handler.postDelayed(completionRunnable, DEBOUNCE_MS)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val TAG = "AmbientLightMonitor"
    }
}
