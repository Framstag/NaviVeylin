package com.naviveylin.auto

import androidx.car.app.model.Action
import androidx.car.app.model.ParkedOnlyOnClickListener

/**
 * Pure factories for the map screen host-strip actions (extracted for
 * testability). Layout: the left map strip is unused (the content menu covers
 * app navigation); the right template strip carries Search (parked-only),
 * Settings (driving-safe) and Zoom in/out (parked-only, unchanged behavior).
 * Host strips are always tappable and the host enforces parked-only.
 */
object MapStripActions {

    /** Search — parked-only (unchanged from previous behavior). */
    fun searchAction(onClick: () -> Unit): Action = Action.Builder()
        .setIcon(CarGlyphs.search)
        .setOnClickListener(ParkedOnlyOnClickListener.create(onClick))
        .build()

    /** Settings — driving-safe: opens the shared settings screen without parking. */
    fun settingsAction(onClick: () -> Unit): Action = Action.Builder()
        .setIcon(CarGlyphs.settings)
        .setOnClickListener(onClick)
        .build()

    /** Zoom in — parked-only (unchanged from previous behavior). */
    fun zoomInAction(onClick: () -> Unit): Action = Action.Builder()
        .setTitle("+")
        .setOnClickListener(ParkedOnlyOnClickListener.create(onClick))
        .build()

    /** Zoom out — parked-only (unchanged from previous behavior). */
    fun zoomOutAction(onClick: () -> Unit): Action = Action.Builder()
        .setTitle("-")
        .setOnClickListener(ParkedOnlyOnClickListener.create(onClick))
        .build()

    /** Licence info — driving-safe: opens the About screen (OSM licence info). */
    fun infoAction(onClick: () -> Unit): Action = Action.Builder()
        .setIcon(CarGlyphs.info)
        .setOnClickListener(onClick)
        .build()
}
