package com.naviveylin.core

import com.framstag.libosmscout.client.NavigationPosition
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for the navigation controller, shared between the phone UI and Android Auto.
 * Implemented by [com.naviveylin.navigation.NavigationEngine] in the :app module.
 *
 * The implementation is **process-scoped**: exactly one instance per app process
 * owns the native navigation controller, so every surface (phone map, Android
 * Auto, Android Automotive OS) observes the same navigation session
 * (spec: `navigation-engine` — Exactly one navigation engine per process).
 */
interface NavigationViewModel {
    val state: StateFlow<NavigationState>

    /**
     * Navigation position estimates for the map surfaces' follow mode, pushed on
     * every `onPositionEstimate` and null while not navigating. Owned by the
     * process-scoped engine, so phone and car follow the same stream
     * (spec: `navigation-engine` — One navigation state shared by all surfaces).
     */
    val positionFlow: StateFlow<NavigationPosition?>

    /** Stop active navigation. */
    fun stopNavigation()

    /**
     * Navigate to a destination from current GPS position.
     * Calculates route and starts turn-by-turn navigation.
     *
     * @param destinationName optional display name/address of the destination,
     * retained in [NavigationState] for the car screen; null falls back to
     * coordinates.
     */
    fun navigateTo(destLat: Double, destLon: Double, destinationName: String? = null)

    /** Clear any displayed error message. */
    fun clearError()

    /**
     * Surface an error message on the navigation surfaces (e.g. deep-link
     * geocoding found no match). Mirrored into [NavigationState.errorMessage] with
     * [origin] as its [NavigationState.errorOrigin], so only the surfaces the error
     * concerns present it (spec: `navigation-engine` — Errors carry the surface
     * that caused them). Defaults to [SurfaceOrigin.ENGINE] — an error of the
     * navigation engine itself, presented everywhere.
     */
    fun reportError(message: String, origin: SurfaceOrigin = SurfaceOrigin.ENGINE)
}
