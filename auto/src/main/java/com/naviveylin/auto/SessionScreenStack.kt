package com.naviveylin.auto

/**
 * Screen-stack bookkeeping for one car session (spec: car-host-fault-isolation — Host
 * screen-stack mutations are balanced; design D4).
 *
 * The session's stack is `map root` → `NavigationScreen` while navigating → optionally
 * one transient error notice on top. Two rules make it stable:
 *
 * - **Bookkeeping follows the mutation that succeeded.** Recording a push before it
 *   lands leaves the session claiming a screen the host refused: `showNavigationScreen`
 *   then early-returns forever and the driver has no navigation view. A rejected push
 *   must leave the record unset so the next state emission tries again.
 * - **At most one error notice.** A notice that is still up is *updated*, not stacked:
 *   repeated errors must not grow the stack, and dismissing a notice must remove only
 *   that notice — `popToRoot()` (which the session used to call) pops the navigation
 *   view away with it while the session still believes it is shown.
 *
 * Pure state, main-thread only, so both rules are testable without a host-provided
 * `CarContext` (a [NavigationSession] cannot be constructed in Robolectric — same
 * reason [SessionHostGate] and [FreeDrivingRestoreGate] are pure).
 */
internal class SessionScreenStack {

    private var navigationShown = false
    private var errorNotice: Any? = null
    private var dismissalOwed = false

    /** True while the session believes its navigation view is on the stack. */
    val isNavigationShown: Boolean
        get() = navigationShown

    /** True while an error notice is on the stack. */
    val hasErrorNotice: Boolean
        get() = errorNotice != null

    /**
     * The notice currently on the stack, or null. Held as an opaque token so the caller
     * can remove exactly that screen (`ScreenManager.remove`) and a stale dismissal can
     * never clear a notice that replaced it.
     */
    val errorNoticeToken: Any?
        get() = errorNotice

    /**
     * True when a notice whose dismissal was skipped (the session was not started, so no
     * host call was allowed) still has to be removed once the session is started again
     * (spec: car-host-fault-isolation — Bounded host-facing traffic while not visible).
     */
    val isDismissalOwed: Boolean
        get() = dismissalOwed

    /**
     * Record the outcome of a navigation-view push attempt.
     *
     * @param landed whether the host accepted the push; a rejected push records nothing,
     *   so a later attempt is not skipped
     */
    fun onNavigationPush(landed: Boolean) {
        if (landed) {
            navigationShown = true
        }
    }

    /**
     * Record that the stack was popped back to the root and the pop landed: the
     * navigation view and any error notice are gone with it.
     */
    fun onRootPopped() {
        navigationShown = false
        errorNotice = null
        dismissalOwed = false
    }

    /**
     * Whether showing an error needs a new overlay screen: false while a notice is
     * already up, in which case the caller updates that notice's message and
     * invalidates it instead of stacking a second screen.
     */
    fun needsErrorNoticePush(): Boolean = errorNotice == null

    /** Record that [notice] is now on the stack. */
    fun onErrorNoticePushed(notice: Any) {
        errorNotice = notice
        dismissalOwed = false
    }

    /**
     * Record that the notice's dismissal was skipped because the session was not started:
     * the notice stays on the stack and its removal is owed to the next started period.
     */
    fun onErrorNoticeDismissalDeferred() {
        dismissalOwed = errorNotice != null
    }

    /**
     * Take the owed dismissal, if there is one. Called from the session's started sync,
     * which is the first moment a host mutation is allowed again.
     *
     * @return the notice to remove, or null when nothing is owed
     */
    fun consumeOwedDismissal(): Any? {
        if (!dismissalOwed) return null
        dismissalOwed = false
        return errorNotice
    }

    /**
     * Record that the error notice was dismissed (or left the stack with a pop back to
     * the root). Identity-guarded: a dismissal of a notice that is no longer the current
     * one changes nothing.
     *
     * @return true when [notice] was the notice on the stack
     */
    fun onErrorNoticeDismissed(notice: Any?): Boolean {
        if (notice == null || errorNotice !== notice) return false
        errorNotice = null
        dismissalOwed = false
        return true
    }
}
