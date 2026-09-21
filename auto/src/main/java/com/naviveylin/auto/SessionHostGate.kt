package com.naviveylin.auto

/**
 * Decides whether the car session may mutate host state (push or pop a screen, request a
 * template refresh, change the host's navigation state) right now, and remembers that a
 * sync is owed when it may not (spec: car-host-fault-isolation — Bounded host-facing
 * traffic while not visible; design D6).
 *
 * The session's observers must keep running while the app is backgrounded (guidance and
 * the cluster/HUD trip keep updating), but a backgrounded car app has no visible screen:
 * pushing a screen or invalidating a template there is host traffic for nothing — and on
 * AAOS it happens exactly while the host is tearing the app's surface down. The deferred
 * work is not lost: the first emission of the next started session applies the state it
 * finds.
 *
 * All methods are main-thread (the session's lifecycle and its observers).
 */
internal class SessionHostGate {

    /** True while the session lifecycle is at least STARTED. */
    @Volatile
    var started: Boolean = false
        private set

    private var syncOwed = false

    /** True when a host sync was deferred while the session was not started. */
    val syncPending: Boolean
        get() = syncOwed

    /**
     * Session reached STARTED.
     *
     * @return true when a deferred sync must be applied now (exactly once)
     */
    fun onSessionStart(): Boolean {
        started = true
        val owed = syncOwed
        syncOwed = false
        return owed
    }

    /** Session left STARTED: host mutations are deferred from here on. */
    fun onSessionStop() {
        started = false
    }

    /**
     * @return true when a host mutation may run now; false when it must be skipped and
     *   re-applied on the next session start (the gate records that a sync is owed)
     */
    fun allowHostMutation(): Boolean {
        if (started) return true
        syncOwed = true
        return false
    }
}
