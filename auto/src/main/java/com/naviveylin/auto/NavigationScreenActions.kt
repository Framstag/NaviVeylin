package com.naviveylin.auto

import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip

/**
 * Pure factories for the navigation screen strip actions (extracted for
 * testability). The left map action strip holds the route-description
 * action only (no BACK — system back is the sole leave affordance, see
 * NavigationScreen); the right action strip holds the visualisation
 * buttons (zoom).
 *
 * Constraint note (car-app 1.7.0): the `NavigationTemplate` map action strip
 * allows zero actions with custom titles — all map-strip actions must be
 * icon-only, so they carry generated glyphs instead of titles. All actions
 * stay usable while driving — zoom is a map visualisation control.
 */
object NavigationScreenActions {

    /**
     * Leave the current navigation view — free-driving exit or navigation
     * stop — icon-only (map action strip constraint), driving-safe.
     * Returns to the map view (free driving) or stops navigation
     * (target routing).
     */
    fun stopAction(onClick: () -> Unit): Action = Action.Builder()
        .setIcon(CarGlyphs.exit)
        .setOnClickListener(onClick)
        .build()

    /**
     * Map action strip for the navigation view: pan + route-description
     * actions. No stop action — the host ETA card stop button is the single
     * stop affordance (spec: auto/navigation-view — "Leave navigation at any
     * time"); no back button (system back is the secondary leave
     * affordance). The PAN action toggles host pan mode (spec: auto/map-pan).
     */
    fun navigationMapActionStrip(onShowRouteDescription: () -> Unit): ActionStrip =
        ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(routeListAction(onShowRouteDescription))
            .build()

    /**
     * Map action strip for the free-driving view: pan + exit actions (spec:
     * auto/map-pan — pan affordance; auto/free-driving — "Exit free
     * driving"). No back/menu affordance.
     */
    fun freeDrivingMapActionStrip(onExit: () -> Unit): ActionStrip =
        ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(stopAction(onExit))
            .build()

    /** Zoom in — driving-safe visualisation control. */
    fun zoomInAction(onClick: () -> Unit): Action = Action.Builder()
        .setTitle("+")
        .setOnClickListener(onClick)
        .build()

    /** Zoom out — driving-safe visualisation control. */
    fun zoomOutAction(onClick: () -> Unit): Action = Action.Builder()
        .setTitle("-")
        .setOnClickListener(onClick)
        .build()

    /**
     * Change destination — opens the car's own search so a live session can be re-planned
     * (spec: auto/navigation-view — A live session can be re-planned and ended from the car).
     * Driving-safe: the label is a resource the calling screen resolves.
     */
    fun changeDestinationAction(title: String, onClick: () -> Unit): Action = Action.Builder()
        .setTitle(title)
        .setOnClickListener(onClick)
        .build()

    /**
     * End navigation — asks the session to end. Destructive, so it sorts last in the strip, where
     * the host's overflow keeps it away from a stray tap (spec: auto/navigation-view — A live
     * session can be re-planned and ended from the car). Driving-safe.
     */
    fun endNavigationAction(title: String, onClick: () -> Unit): Action = Action.Builder()
        .setTitle(title)
        .setOnClickListener(onClick)
        .build()

    /**
     * The navigation view's action strip: zoom first (the frequent action while driving), then
     * *change destination*, and *end navigation* last (the destructive one). The titles are the
     * calling screen's resources, so this stays a pure factory (spec: auto/navigation-view — A live
     * session can be re-planned and ended from the car).
     */
    fun navigationActionStrip(
        onZoomIn: () -> Unit,
        onZoomOut: () -> Unit,
        onChangeDestination: () -> Unit,
        onEndNavigation: () -> Unit,
        changeDestinationTitle: String,
        endNavigationTitle: String
    ): ActionStrip = ActionStrip.Builder()
        .addAction(zoomInAction(onZoomIn))
        .addAction(zoomOutAction(onZoomOut))
        .addAction(changeDestinationAction(changeDestinationTitle, onChangeDestination))
        .addAction(endNavigationAction(endNavigationTitle, onEndNavigation))
        .build()

    /**
     * Open the route description list — icon-only (map action strip
     * constraint), driving-safe.
     */
    fun routeListAction(onClick: () -> Unit): Action = Action.Builder()
        .setIcon(CarGlyphs.routeList)
        .setOnClickListener(onClick)
        .build()
}
