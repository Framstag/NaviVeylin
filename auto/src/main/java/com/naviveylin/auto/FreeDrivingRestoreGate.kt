package com.naviveylin.auto

/**
 * At-most-once bookkeeping for the free-driving restore of one car session
 * (spec: auto/free-driving — Free-driving restore is idempotent).
 *
 * A session restores a still-active free-driving mode by pushing the view on top of
 * the map root, and two session paths can ask for that push: the first screen
 * creation and the warmup completion. The free-driving flag itself is retained
 * across a session destroy (by design — the mode survives a host restart), so
 * without this gate both paths pushed a view: two [FreeDrivingScreen]s, each with its
 * own renderer, and a ghost view left under the top one after the driver pressed back
 * once.
 *
 * Pure state, main-thread only (the session calls it from its lifecycle and observer
 * callbacks). [recordPush] is called only when the push actually happened, so a
 * deferred or refused push is retried instead of being consumed.
 */
internal class FreeDrivingRestoreGate {

    private var restored = false

    /** True once a free-driving view was pushed for this session. */
    val hasRestored: Boolean
        get() = restored

    /**
     * Whether the caller should push the free-driving view: the mode must still be
     * active, navigation must not own the stack, and this session must not have
     * pushed the view yet.
     */
    fun shouldPush(isNavigating: Boolean, freeDrivingActive: Boolean): Boolean =
        !restored && shouldRestoreFreeDriving(isNavigating, freeDrivingActive)

    /** Record that the view was pushed (consumes the session's one restore). */
    fun recordPush() {
        restored = true
    }

    /**
     * Forget the restore, so a later attempt may push again. Used by the startup
     * retry, which pops the stack back to the root (there is no free-driving view
     * left to restore from).
     */
    fun reset() {
        restored = false
    }
}
