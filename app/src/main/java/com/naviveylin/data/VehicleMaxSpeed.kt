package com.naviveylin.data

import com.framstag.libosmscout.client.Vehicle

/**
 * The stored usual maximum speed of [vehicle], in km/h (spec: `routing-max-speed` — Every
 * calculation path carries the vehicle's maximum speed).
 *
 * One mapping for the route panel, the navigation engine's acquisition and its reroute: a vehicle
 * cannot pick up another vehicle's speed anywhere, because no call site maps the fields itself.
 * The route panel's selected vehicle and the engine's session vehicle are both resolved here.
 */
internal fun AppSettings.maxSpeedKmhFor(vehicle: Vehicle): Double = when (vehicle) {
    Vehicle.CAR -> carMaxSpeedKmh
    Vehicle.BICYCLE -> bicycleMaxSpeedKmh
    Vehicle.PEDESTRIAN -> footMaxSpeedKmh
}.toDouble()
