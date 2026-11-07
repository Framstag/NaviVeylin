package com.naviveylin.data

import com.framstag.libosmscout.client.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one builder that turns stored settings into a routing profile (spec: `routing-avoidance` —
 * One builder produces the profile every calculation uses).
 *
 * A call site that built a profile from individual fields could carry the vehicle's maximum speed and
 * silently drop an avoidance choice — which is exactly how both avoid flags stayed inert while the app
 * asked for neither. These cases pin the builder's contract: every stored value reaches the profile, and
 * the profile is the only shape a calculation can consume.
 */
class RoutingProfileBuilderTest {

    private val defaults = AppSettings()

    /** Spec: `routing-avoidance` — Avoidance is a persisted per-vehicle-independent setting. */
    @Test
    fun defaultSettingsAskForNoAvoidance() {
        val profile = defaults.routingProfileFor(Vehicle.CAR)

        assertFalse(profile.avoidTolls)
        assertFalse(profile.avoidFerries)
        assertFalse(profile.avoidUnpaved)
    }

    /** Spec: `routing-avoidance` — One builder produces the profile every calculation uses. */
    @Test
    fun eachStoredChoiceReachesTheProfile() {
        val settings = defaults.copy(avoidTolls = true, avoidFerries = true, avoidUnpaved = true)

        val profile = settings.routingProfileFor(Vehicle.CAR)

        assertTrue(profile.avoidTolls)
        assertTrue(profile.avoidFerries)
        assertTrue(profile.avoidUnpaved)
    }

    /** The choices are not per vehicle: the same stored values serve every profile. */
    @Test
    fun theChoicesApplyToEveryVehicle() {
        val settings = defaults.copy(avoidTolls = true)

        for (vehicle in Vehicle.values()) {
            val profile = settings.routingProfileFor(vehicle)

            assertEquals(vehicle, profile.vehicle)
            assertTrue("vehicle $vehicle", profile.avoidTolls)
        }
    }

    /** Spec: `routing-max-speed` — Every calculation path carries the vehicle's maximum speed. */
    @Test
    fun theBuilderStillCarriesTheVehiclesOwnSpeed() {
        val settings = AppSettings(carMaxSpeedKmh = 90, bicycleMaxSpeedKmh = 20, footMaxSpeedKmh = 4)

        assertEquals(90.0, settings.routingProfileFor(Vehicle.CAR).maxSpeedKmh, 0.0)
        assertEquals(20.0, settings.routingProfileFor(Vehicle.BICYCLE).maxSpeedKmh, 0.0)
        assertEquals(4.0, settings.routingProfileFor(Vehicle.PEDESTRIAN).maxSpeedKmh, 0.0)
    }

    /** One choice does not leak into another. */
    @Test
    fun choicesAreIndependent() {
        val settings = defaults.copy(avoidFerries = true)

        val profile = settings.routingProfileFor(Vehicle.BICYCLE)

        assertFalse(profile.avoidTolls)
        assertTrue(profile.avoidFerries)
        assertFalse(profile.avoidUnpaved)
    }
}
