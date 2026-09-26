package com.naviveylin.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide signal that a car session (Android Auto projection or Android
 * Automotive OS) is live (spec: `car-session-presence`).
 *
 * **Informational only.** The phone surface uses it to tell the driver that
 * navigation is presented on the car screen; it never disables a control and
 * never gates an action. Navigation has a single owner per process
 * (`one-navigation-engine`), so a competing phone command — the thing a lock
 * would have had to prevent — cannot exist; browsing the phone map and setting a
 * destination there stay available while a car session is live.
 *
 * **In-memory only**, never persisted: a fresh process always starts inactive, so
 * a session that ended (or died with the process) cannot leave a stale "live"
 * value behind.
 *
 * **One instance per process**, resolved by the car session for publishing through
 * [com.naviveylin.core.AutoEntryPoint] and by the phone UI for consumption. Under
 * Android Auto projection both live in the same process, which is exactly the case
 * where the phone can display the indication; an Android Automotive OS standalone
 * process publishes it with no phone consumer.
 */
interface CarSessionPresence {

    /** True while a car session is live. */
    val active: StateFlow<Boolean>

    /**
     * Publish the car session state. Idempotent: setting the current value again
     * changes nothing, so a lifecycle step may publish unconditionally.
     */
    fun setActive(active: Boolean)
}
