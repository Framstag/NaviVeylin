package com.naviveylin.auto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The session's calculation notice under a reroute (spec: `rerouting-visual-feedback` — Reroute
 * progress notice, `auto/navigation-view` — Reroute keeps the navigation view on screen).
 *
 * A reroute runs with guidance still live: the wait is drawn on the map surface and no screen is
 * pushed — however long the calculation runs and however many percentage updates arrive. The
 * session itself needs a host-provided `CarContext` and cannot be constructed in Robolectric, so
 * the rule is asserted through the pure seams the session's notice sync consults.
 */
class CalculationNoticeSessionTest {

    @Test
    fun noScreenForARerouteHoweverLongItRuns() {
        val stack = SessionScreenStack()
        stack.onNavigationPush(landed = true)

        repeat(201) { percent ->
            assertTrue(
                "a navigating calculation is the surface's wait at $percent %",
                calculationWaitOnSurface(isNavigating = true)
            )
            assertFalse(
                "and it pushes no screen at $percent %",
                stack.needsCalculationNoticePush(navigating = calculationWaitOnSurface(true))
            )
        }

        assertFalse("no calculation notice is on the stack", stack.hasCalculationNotice)
        assertTrue("the navigation view stays the displayed screen", stack.isNavigationShown)
    }

    @Test
    fun aCalculationWhileNotNavigatingStillGetsItsNotice() {
        // The cold-start path is unchanged: with no map to protect the pane is still pushed.
        assertFalse(calculationWaitOnSurface(isNavigating = false))
        assertTrue(SessionScreenStack().needsCalculationNoticePush(navigating = false))
    }
}
