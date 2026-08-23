package com.naviveylin.auto

import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon

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
     * Open the route description list — icon-only (map action strip
     * constraint), driving-safe.
     */
    fun routeListAction(onClick: () -> Unit): Action = Action.Builder()
        .setIcon(CarGlyphs.routeList)
        .setOnClickListener(onClick)
        .build()
}
