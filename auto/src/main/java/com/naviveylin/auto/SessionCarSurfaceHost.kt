package com.naviveylin.auto

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.naviveylin.core.CarSurfaceHost
import com.naviveylin.core.CarSurfaceOwner
import com.naviveylin.core.DiagnosticsLog
import java.util.Collections
import java.util.WeakHashMap

/**
 * Default [CarSurfaceHost] (spec: car-host-fault-isolation — Single-owner car
 * surface; design D1).
 *
 * Main-thread only: the car-app library dispatches every host callback on the
 * main thread, and the session attaches/detaches owners from screen lifecycle
 * callbacks, which are main-thread too.
 *
 * The host deliberately does **not** implement the ownership release in the
 * screens: [AutoMapRenderer] used to release "its" surface on stop, replace and
 * shutdown, which disconnected the buffer queue that another screen (or the
 * host itself) was using. Here the session releases once per delivered surface.
 */
class SessionCarSurfaceHost : CarSurfaceHost, SurfaceCallback {

    private var carContext: CarContext? = null

    /** The surface (drawable), or null when none was delivered. */
    private var active: Surface? = null
    private var activeWidth = 0
    private var activeHeight = 0
    private var activeDpi = 0.0

    /** The screen that currently draws on [active]; null during a transition. */
    private var owner: CarSurfaceOwner? = null

    /**
     * Surfaces already released, by identity, so a duplicate
     * `onSurfaceDestroyed` (or a destroy after a replace) can never release a
     * surface twice — the release is the one operation whose repetition breaks
     * the host's buffer queue.
     */
    private val released: MutableSet<Surface> =
        Collections.newSetFromMap(WeakHashMap<Surface, Boolean>())

    override fun startSession(context: Context) {
        val newContext = context as? CarContext ?: return
        // Idempotent for the same session (lifecycle re-entry), but a *different* context
        // is a new session: the previous one may have ended without running endSession
        // (the host dropped the connection), and keeping its context would leave this
        // session without a registration and make the app call host APIs through a dead
        // host (spec: car-host-fault-isolation — Session registration follows the host
        // session).
        if (carContext === newContext) return
        carContext?.let { previous ->
            runCatching { previous.getCarService(AppManager::class.java)?.setSurfaceCallback(null) }
                .onFailure { Log.w(TAG, "clearing the previous session's registration failed", it) }
        }
        carContext = newContext
        newContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun endSession() {
        carContext?.let { current ->
            runCatching { current.getCarService(AppManager::class.java)?.setSurfaceCallback(null) }
                .onFailure { Log.w(TAG, "clearing the registration failed", it) }
        }
        carContext = null
        if (active != null) {
            dispatch("onCarSurfaceDestroyed") { owner?.onCarSurfaceDestroyed() }
        }
        owner = null
        releaseActive()
    }

    override fun attach(owner: CarSurfaceOwner) {
        val previous = this.owner
        if (previous != null && previous !== owner) {
            // Revoke before the new owner draws. The car-app host starts the
            // incoming screen before it stops the outgoing one, so the outgoing
            // screen is still started (and still holds this surface) while the
            // incoming one already renders: without this revocation two renderers
            // would lock the one session surface at the same time.
            dispatch("onCarSurfaceRevoked") { previous.onCarSurfaceRevoked() }
        }
        this.owner = owner
        active?.let { surface ->
            dispatch("onCarSurfaceAvailable") {
                owner.onCarSurfaceAvailable(surface, activeWidth, activeHeight, activeDpi)
            }
        }
    }

    override fun detach(owner: CarSurfaceOwner) {
        // Identity-guarded: the outgoing screen's stop must not clear the
        // surface of the screen that superseded it.
        if (this.owner === owner) {
            this.owner = null
        }
    }

    override fun hasSurface(): Boolean = active != null

    // ── SurfaceCallback (registered once per session) ──

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface ?: return
        if (active !== null && active !== surface) {
            // A genuinely new surface: the host expects the previous one released.
            release(active)
        }
        // The car-app contract is per *delivery*: an instance delivered again after
        // its destroy is a new delivery, not the one already released. Its release
        // state is dropped here so it can be adopted, drawn on, and released once
        // again when it is superseded.
        if (released.remove(surface)) {
            android.util.Log.d(TAG, "surface instance re-delivered after its release ${System.identityHashCode(surface)}")
            DiagnosticsLog.log(
                HOST_TAG,
                "surface re-delivered ${System.identityHashCode(surface)} (released before) — treated as a new delivery"
            )
        }
        active = surface
        activeWidth = surfaceContainer.width
        activeHeight = surfaceContainer.height
        activeDpi = surfaceContainer.dpi.toDouble()
        DiagnosticsLog.log(
            HOST_TAG,
            "surface adopt ${System.identityHashCode(surface)} ${activeWidth}x${activeHeight}@${activeDpi}dpi"
        )
        dispatch("onCarSurfaceAvailable") {
            owner?.onCarSurfaceAvailable(surface, activeWidth, activeHeight, activeDpi)
        }
    }

    /**
     * The host no longer provides the surface it names (spec: car-host-fault-isolation —
     * Single-owner car surface).
     *
     * Scoped to the instance the callback names: only a destroy of the surface this session
     * currently holds clears the session's surface and tells the owner to stop drawing. A
     * destroy of a *superseded* instance (the host delivered a newer surface first — AAOS
     * re-delivers after transitions) must release that instance and nothing else: clearing or
     * releasing the live one would disconnect the buffer queue the host is still compositing
     * through and leave the session without a surface for every later screen.
     */
    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        val destroyed = surfaceContainer.surface
        val current = active
        if (destroyed != null && current != null && destroyed !== current) {
            // A superseded (or never adopted) instance: release it, keep the live surface.
            release(destroyed)
            return
        }
        active = null
        activeWidth = 0
        activeHeight = 0
        activeDpi = 0.0
        if (current != null) {
            // Notified once per surface: a duplicate destroy callback (host
            // restart, double delivery) must not tell the owner twice either.
            dispatch("onCarSurfaceDestroyed") { owner?.onCarSurfaceDestroyed() }
        }
        release(current)
        if (destroyed != null && destroyed !== current) {
            // `current` was null (no surface held) while the host destroyed a delivered
            // instance: still release it once.
            release(destroyed)
        }
    }

    override fun onVisibleAreaChanged(visibleArea: android.graphics.Rect) {
        dispatch("onCarVisibleAreaChanged") { owner?.onCarVisibleAreaChanged(visibleArea) }
    }

    override fun onStableAreaChanged(stable: android.graphics.Rect) {
        dispatch("onCarStableAreaChanged") { owner?.onCarStableAreaChanged(stable) }
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        dispatch("onCarScroll") { owner?.onCarScroll(distanceX, distanceY) }
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        dispatch("onCarFling") { owner?.onCarFling(velocityX, velocityY) }
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        dispatch("onCarScale") { owner?.onCarScale(focusX, focusY, scaleFactor) }
    }

    override fun onClick(x: Float, y: Float) {
        dispatch("onCarClick") { owner?.onCarClick(x, y) }
    }

    /**
     * Run one owner callback, confining a fault to it (spec: car-host-fault-isolation —
     * No fault escapes into the host path). The car-app library dispatches host
     * callbacks on the main thread and rethrows an app exception there, which would
     * kill the process; a screen fault must instead degrade to a logged no-op. This
     * is the single decorator for all four car screens — guarding here means a screen
     * cannot forget it.
     */
    private inline fun dispatch(why: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "owner callback failed ($why) — degraded", t)
        }
    }

    private fun releaseActive() {
        val current = active
        active = null
        activeWidth = 0
        activeHeight = 0
        activeDpi = 0.0
        release(current)
    }

    /** Release [surface] unless this session already released that instance. */
    private fun release(surface: Surface?) {
        if (surface == null) return
        if (!released.add(surface)) return
        val id = System.identityHashCode(surface)
        android.util.Log.d(TAG, "releasing session surface $id")
        DiagnosticsLog.log(HOST_TAG, "surface release $id")
        surface.release()
    }

    private companion object {
        const val TAG = "CarSurfaceHost"

        /** Diagnostics tag for what the session sent the host (spec: car-host-fault-isolation — Host interaction is diagnosable). */
        const val HOST_TAG = "HOST"
    }
}
