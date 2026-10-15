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
     * Cancel the route calculation that is in flight, if any
     * (spec: `route-calculation-feedback` — Cancelling a calculation aborts it
     * and releases its resources).
     *
     * Aborts the underlying routing work, clears [NavigationState.calculation],
     * releases the location updates an acquisition without a surface UI took and
     * starts nothing. A call while no calculation is in progress changes nothing.
     * Reroutes are cancellable the same way; a surface that must keep guidance live
     * simply does not offer this to the driver.
     */
    fun cancelAcquisition()

    /**
     * Surface an error message on the navigation surfaces (e.g. deep-link
     * geocoding found no match). Raised as a [NavigationFailure] whose [origin] decides which surfaces
     * present it, so a surface shows only the errors that concern it (spec: `navigation-engine` —
     * Errors carry the surface that caused them). Defaults to [SurfaceOrigin.ENGINE] — an error of the
     * navigation engine itself, presented everywhere.
     *
     * The producer supplies the wording itself, so the failure carries it as its
     * [NavigationFailure.surfaceMessage] and its reason is [FailureReason.UNKNOWN]. A producer that
     * knows the cause raises a typed [reportFailure] instead, and every surface words it from the
     * shared resource.
     */
    fun reportError(message: String, origin: SurfaceOrigin = SurfaceOrigin.ENGINE)

    /**
     * Raise a failure the surfaces word themselves (spec: `routing-failure-reporting` — Each reason is
     * worded by the surface).
     *
     * The failure carries its reason and the values a wording needs, never the wording, so the phone
     * and the car name the same cause the same way and neither shows a sentence another layer
     * composed. [NavigationFailure.appliesTo] decides which surfaces present it.
     */
    fun reportFailure(failure: NavigationFailure)
}
