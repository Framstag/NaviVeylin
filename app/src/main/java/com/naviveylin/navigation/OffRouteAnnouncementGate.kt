package com.naviveylin.navigation

/**
 * The once-per-departure rule for the spoken off-route cue (spec: `voice-guidance` — A departure
 * from the planned route is announced once).
 *
 * The same shape as the held GPS state beside it: the cue is spoken for the transition onto the
 * off-route condition, never for a position update that merely repeats it, and the rule re-arms
 * only when the vehicle is back on a route. Off-route flickers at a junction or in a tunnel guard,
 * which is why "announce every detection" is not the rule.
 *
 * Silent outside a session (no navigation view to be off route from) and silent when the session
 * surface's voice switch is off. A suppressed departure still counts as announced: turning the
 * switch on while the condition persists must not replay a departure the vehicle has already left
 * behind.
 *
 * Pure state, main-thread only, so the rule is asserted without an engine or a platform TTS
 * (`OffRouteAnnouncementTest`).
 */
internal class OffRouteAnnouncementGate {

    private var announced = false

    /**
     * The vehicle was detected off the planned route.
     *
     * @param isNavigating whether a navigation session is active
     * @param voiceEnabled whether the session surface's voice switch is on
     * @return true exactly once per departure, when a session is active and voice is on
     */
    fun onDeparture(isNavigating: Boolean, voiceEnabled: Boolean): Boolean {
        if (!isNavigating) {
            announced = false
            return false
        }
        if (announced) return false
        announced = true
        return voiceEnabled
    }

    /** The vehicle is back on a route (or the session ended): the next departure is announced again. */
    fun rearm() {
        announced = false
    }
}
