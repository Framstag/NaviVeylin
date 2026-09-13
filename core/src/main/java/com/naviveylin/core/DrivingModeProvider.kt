package com.naviveylin.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Surface-independent indicator of "free driving" mode (spec: map-modes —
 * follow active without a route). Published by the phone map view and the
 * Android Auto session; consumed by app-module components that must act when
 * a driving mode is active regardless of which surface started it (e.g. the
 * ongoing navigation notification / foreground service).
 *
 * OR-combined semantics with retain-on-death: each surface publishes its
 * *current* free-driving state; the combined flag is true while any surface
 * is free-driving. A surface that dies (Activity cleared, car session
 * destroyed) retains its last value — destroying a surface must never claim
 * "driving stopped". The flag is cleared only by an explicit
 * [setFreeDriving](surface, false) on an intentional exit.
 *
 * Implemented as an app-module singleton; exposed to the Android Auto module
 * via [AutoEntryPoint.autoDrivingModeProvider] (same pattern as
 * [AutoSettingsProvider]).
 */
interface DrivingModeProvider {

    /** True while any surface is free-driving. */
    val freeDrivingActive: StateFlow<Boolean>

    /**
     * Publish the free-driving state of [surface]. Active surfaces are
     * OR-combined; a false publishes only removes that surface's vote.
     */
    fun setFreeDriving(surface: String, active: Boolean)

    companion object {
        /** Phone map view (MapCanvasViewModel follow/drive-suspended mode). */
        const val SURFACE_PHONE = "phone"

        /** Android Auto session (free-driving screen on the car surface). */
        const val SURFACE_AUTO = "auto"
    }
}
