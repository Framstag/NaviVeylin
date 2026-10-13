package com.naviveylin.ui.map

/**
 * The speed the phone's auto-zoom is computed from (spec: `auto-speed-zoom` — Auto-zoom speed source
 * per surface).
 *
 * The phone used to read the *navigation engine's* speed (`NavigationViewModel.state.currentSpeedKmH`)
 * unconditionally. That value only exists while route guidance is running, so in free driving it was
 * absent and the zoom fell back to a remembered seed — measured 2026-10-12: a free drive at 90+ km/h
 * kept the 20 km/h magnification target, i.e. the map stayed several levels too close.
 *
 * Each surface now uses a speed it actually knows: the phone the navigation engine's speed while
 * route guidance is active, and its own GPS fix speed otherwise; the car (which has no other source)
 * keeps the engine speed. An unknown speed is passed through as NaN — the table owns what an unknown
 * input means, and no remembered value may stand in for it.
 */
internal fun autoZoomSpeedKmH(
    isNavigating: Boolean,
    engineSpeedKmH: Double,
    ownSpeedKmH: Double
): Double = if (isNavigating) engineSpeedKmH else ownSpeedKmH
