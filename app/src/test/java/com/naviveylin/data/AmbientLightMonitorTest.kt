package com.naviveylin.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Tests the [AmbientLightMonitor] lifecycle contract: start registers the
 * light sensor listener, stop unregisters and reports null (environment
 * reverts to the system signal), and a device without a light sensor is a
 * safe no-op. The classification pipeline itself is covered by
 * [AmbientLightPipelineTest].
 */
@RunWith(RobolectricTestRunner::class)
class AmbientLightMonitorTest {

    private fun sensorManager(): SensorManager {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private fun addLightSensor(sm: SensorManager): Sensor {
        val sensor = newSensor(Sensor.TYPE_LIGHT)
        shadowOf(sm).addSensor(Sensor.TYPE_LIGHT, sensor)
        return sensor
    }

    /** Sensor/SensorEvent constructors are package-private — reflect. */
    private fun newSensor(type: Int): Sensor {
        val ctor = Sensor::class.java.getDeclaredConstructor()
        ctor.isAccessible = true
        val sensor = ctor.newInstance()
        val typeField = Sensor::class.java.getDeclaredField("mType")
        typeField.isAccessible = true
        typeField.setInt(sensor, type)
        return sensor
    }

    private fun newSensorEvent(size: Int): SensorEvent {
        val ctor = SensorEvent::class.java.getDeclaredConstructor(Int::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(size)
    }

    @Test
    fun startRegistersListenerAndStopUnregisters() {
        val sm = sensorManager()
        val shadow = shadowOf(sm)
        addLightSensor(sm)
        val classifications = mutableListOf<Boolean?>()
        val monitor = AmbientLightMonitor(sm, onClassification = { classifications.add(it) })

        assertTrue(monitor.available)
        monitor.start()
        assertTrue("listener must be registered after start", shadow.hasListener(monitor))

        monitor.stop()
        assertFalse("listener must be unregistered after stop", shadow.hasListener(monitor))
        assertEquals("stop must report null (revert to system signal)", listOf<Boolean?>(null), classifications)
    }

    @Test
    fun startIsIdempotent() {
        val sm = sensorManager()
        val shadow = shadowOf(sm)
        addLightSensor(sm)
        val monitor = AmbientLightMonitor(sm, onClassification = {})

        monitor.start()
        monitor.start()
        assertEquals(1, shadow.getListeners().size)
    }

    @Test
    fun stopWithoutStartIsNoOp() {
        val sm = sensorManager()
        addLightSensor(sm)
        val classifications = mutableListOf<Boolean?>()
        val monitor = AmbientLightMonitor(sm, onClassification = { classifications.add(it) })

        monitor.stop()
        assertTrue(classifications.isEmpty())
    }

    @Test
    fun sensorEventFeedsClassification() {
        val sm = sensorManager()
        addLightSensor(sm)
        val classifications = mutableListOf<Boolean?>()
        val monitor = AmbientLightMonitor(sm, onClassification = { classifications.add(it) })

        monitor.start()
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)!!
        val event = newSensorEvent(1)
        event.sensor = sensor
        event.values[0] = 200f // bright reading -> light classification
        monitor.onSensorChanged(event)
        assertEquals(listOf<Boolean?>(false), classifications)
    }

    @Test
    fun pendingFlipCompletesWithoutFurtherEvents() {
        // Emulator sends bursts on value change, not a stream: a single dark
        // reading must still complete the flip after the debounce window.
        val sm = sensorManager()
        addLightSensor(sm)
        val classifications = mutableListOf<Boolean?>()
        val monitor = AmbientLightMonitor(sm, onClassification = { classifications.add(it) })

        monitor.start()
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)!!
        val event = newSensorEvent(1)
        event.sensor = sensor
        event.values[0] = 5f // dark reading
        monitor.onSensorChanged(event)
        assertEquals("first event reports the initial (light) state", listOf<Boolean?>(false), classifications)

        // No further events; the debounce timer completes the flip.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        assertEquals(listOf<Boolean?>(false, true), classifications)
    }

    @Test
    fun noLightSensorIsSafeNoOp() {
        val sm = sensorManager()
        val shadow = shadowOf(sm)
        val classifications = mutableListOf<Boolean?>()
        val monitor = AmbientLightMonitor(sm, onClassification = { classifications.add(it) })

        assertFalse(monitor.available)
        monitor.start() // must not throw, must not register
        assertTrue(shadow.getListeners().isEmpty())
        assertTrue(classifications.isEmpty())
    }

    @Test
    fun setSensitivityRepipelinesAndReclassifiesLastReading() {
        val sm = sensorManager()
        addLightSensor(sm)
        val classifications = mutableListOf<Boolean?>()
        val monitor = AmbientLightMonitor(sm, onClassification = { classifications.add(it) })

        monitor.start()
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)!!

        // Bright reading -> light classification.
        val bright = newSensorEvent(1).apply { this.sensor = sensor; values[0] = 200f }
        monitor.onSensorChanged(bright)

        // Drop to 6 lux under the default HIGH: pending dark flip.
        val dim = newSensorEvent(1).apply { this.sensor = sensor; values[0] = 6f }
        monitor.onSensorChanged(dim)
        // Switch to MEDIUM (6 lux is light there): the fresh pipeline must not
        // carry the HIGH pending flip; the re-classification reports no flip.
        monitor.setSensitivity(AmbientLightSensitivity.MEDIUM)
        assertEquals(listOf<Boolean?>(false, false), classifications)
    }
}
