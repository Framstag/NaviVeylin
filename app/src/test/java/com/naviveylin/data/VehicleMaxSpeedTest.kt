package com.naviveylin.data

import com.framstag.libosmscout.client.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one mapping from a stored per-vehicle maximum speed to a routing profile
 * (spec: `routing-max-speed` — Every calculation path carries the vehicle's maximum speed; and
 * `route-panel-ui` — Each vehicle reads its own stored value): the route panel, the engine's
 * acquisition and its reroute all resolve a vehicle through it, so no call site can hand a vehicle
 * another vehicle's speed.
 */
class VehicleMaxSpeedTest {

    private val settings = AppSettings(
        carMaxSpeedKmh = 90,
        bicycleMaxSpeedKmh = 20,
        footMaxSpeedKmh = 4
    )

    @Test
    fun eachVehicleReadsItsOwnStoredValue() {
        assertEquals(90.0, settings.maxSpeedKmhFor(Vehicle.CAR), 0.0)
        assertEquals(20.0, settings.maxSpeedKmhFor(Vehicle.BICYCLE), 0.0)
        assertEquals(4.0, settings.maxSpeedKmhFor(Vehicle.PEDESTRIAN), 0.0)
    }

    @Test
    fun theShippedDefaultsAreTheSpeedsTheSheetShows() {
        // The sheet and the routing profile must agree on the shipped default of every vehicle
        // (spec: `routing-max-speed` — A per-vehicle maximum speed is a persisted setting).
        val defaults = AppSettings()
        assertEquals(120.0, defaults.maxSpeedKmhFor(Vehicle.CAR), 0.0)
        assertEquals(15.0, defaults.maxSpeedKmhFor(Vehicle.BICYCLE), 0.0)
        assertEquals(5.0, defaults.maxSpeedKmhFor(Vehicle.PEDESTRIAN), 0.0)
    }
}
