package com.naviveylin.core

import android.content.Context
import android.graphics.Rect
import android.view.Surface

/**
 * The car screen that currently draws on the session's car surface
 * (spec: car-host-fault-isolation — Single-owner car surface).
 *
 * A screen implements this instead of registering its own [SurfaceCallback]:
 * the car-app host has exactly one surface callback per app, and re-registering
 * it per screen start is what made one screen release the buffer queue another
 * screen was drawing through (spec: car-host-fault-isolation — "Screen pushed or
 * popped", "Registration superseded").
 *
 * [onCarSurfaceAvailable] hands over a surface the caller may draw on until
 * [onCarSurfaceRevoked] or [onCarSurfaceDestroyed]; neither call transfers
 * ownership, so an implementation SHALL NOT call [Surface.release] — the
 * [CarSurfaceHost] owns the surface's lifetime.
 *
 * Exactly one owner draws at a time: the host revokes the surface from the
 * previous owner in the moment another screen takes over, so a screen that is
 * still started underneath a newly started one never draws the surface the new
 * owner already draws through.
 */
interface CarSurfaceOwner {
    /** The session's surface is available for drawing (adopt it in the renderer). */
    fun onCarSurfaceAvailable(surface: Surface, width: Int, height: Int, dpi: Double)

    /**
     * Stop drawing on the session's surface: another screen became the owner, or
     * this screen's ownership was superseded (the surface itself still exists and
     * is not released here).
     */
    fun onCarSurfaceRevoked() {
        onCarSurfaceDestroyed()
    }

    /** Stop drawing: the session's surface is gone or replaced. */
    fun onCarSurfaceDestroyed()
    /** Host-reported currently-visible area of the surface. */
    fun onCarVisibleAreaChanged(visible: Rect) {}

    /** Host-reported stable area of the surface. */
    fun onCarStableAreaChanged(stable: Rect) {}

    /** Scroll gesture on the surface. */
    fun onCarScroll(distanceX: Float, distanceY: Float) {}

    /** Fling gesture on the surface. */
    fun onCarFling(velocityX: Float, velocityY: Float) {}

    /** Scale (pinch) gesture on the surface. */
    fun onCarScale(focusX: Float, focusY: Float, scaleFactor: Float) {}

    /** Tap on the surface. */
    fun onCarClick(x: Float, y: Float) {}
}

/**
 * Session-scoped owner of the car display surface (spec: car-host-fault-isolation
 * — Single-owner car surface; design D1).
 *
 * **One registration per session**: [startSession] registers this host as the
 * car-app surface callback and [endSession] clears it, so the host never
 * re-registers (and never re-delivers) on a screen transition.
 * **One owner at a time**: [attach] makes a screen the owner, revokes the surface
 * from the owner it supersedes, and immediately hands the retained surface to the
 * new owner when one arrived while no screen owned it; [detach] only clears the
 * owner if the caller still *is* the owner, so an outgoing screen's stop cannot
 * clear the surface of the screen that superseded it.
 *
 * **The session owns the surface's lifetime**: the host releases a surface
 * exactly once, when the host reports it destroyed, when it is replaced, or when
 * the session ends — never because a screen stopped. Screens therefore keep
 * drawing through a transition and after a background round trip.
 *
 * Implemented in `:app` (Hilt) and consumed by the car session and its screens.
 */
interface CarSurfaceHost {
    /**
     * Register as the car-app surface callback for this session. Idempotent for
     * the same context: re-entry with the same context does not register again,
     * while a context of a different session replaces the registration, so a
     * session that follows a dropped connection registers its own callback
     * instead of talking to the host through a dead context.
     *
     * Takes a plain [Context] because `:core` does not depend on the car-app
     * library; the car-app implementation performs the registration (and the
     * cast to the host's `CarContext`).
     *
     * @param context the car context of the owning session
     */
    fun startSession(context: Context)

    /** Clear the registration and release a still-held surface (idempotent). */
    fun endSession()

    /** Make [owner] the surface owner, adopting a retained surface if one exists. */
    fun attach(owner: CarSurfaceOwner)

    /** Clear [owner] as the surface owner only if it is still the current owner. */
    fun detach(owner: CarSurfaceOwner)

    /** True while the session holds a surface the host delivered. */
    fun hasSurface(): Boolean
}
