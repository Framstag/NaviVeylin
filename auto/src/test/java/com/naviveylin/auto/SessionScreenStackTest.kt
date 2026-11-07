package com.naviveylin.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the session's screen-stack bookkeeping (spec: car-host-fault-isolation — Host
 * screen-stack mutations are balanced, "Rejected screen push", "Repeated errors";
 * design D4).
 *
 * Both rules here are the difference between "the host refused a push" and "the driver
 * has no navigation view for the rest of the drive": a record must follow the mutation
 * that succeeded, and a notice that is already up must not be stacked again.
 */
class SessionScreenStackTest {

    @Test
    fun aLandedNavigationPushIsRecorded() {
        val stack = SessionScreenStack()

        assertFalse(stack.isNavigationShown)
        stack.onNavigationPush(landed = true)

        assertTrue(stack.isNavigationShown)
    }

    @Test
    fun aRejectedPushIsNotRecordedAsPresent() {
        // A rejected push must leave the record unset: recording it makes
        // showNavigationScreen early-return forever, and the next state emission never
        // re-pushes (the defect this change fixes).
        val stack = SessionScreenStack()

        stack.onNavigationPush(landed = false)

        assertFalse("a refused push is not a shown view", stack.isNavigationShown)
        stack.onNavigationPush(landed = true)
        assertTrue("the retry lands", stack.isNavigationShown)
    }

    @Test
    fun poppingBackToTheRootForgetsTheNavigationViewAndTheNotice() {
        val stack = SessionScreenStack()
        stack.onNavigationPush(landed = true)
        stack.onErrorNoticePushed(Any())

        stack.onRootPopped()

        assertFalse(stack.isNavigationShown)
        assertFalse(stack.hasErrorNotice)
    }

    @Test
    fun theFirstErrorNeedsANewNoticeAndTheSecondDoesNot() {
        val stack = SessionScreenStack()

        assertTrue("nothing is up yet", stack.needsErrorNoticePush())
        stack.onErrorNoticePushed(Any())
        assertFalse("a notice that is up is updated, not stacked", stack.needsErrorNoticePush())
        assertFalse(stack.needsErrorNoticePush())
    }

    @Test
    fun repeatedErrorsDoNotGrowTheStack() {
        val stack = SessionScreenStack()

        // Five errors in one session: exactly one push, four updates.
        var pushes = 0
        repeat(5) {
            if (stack.needsErrorNoticePush()) {
                pushes++
                stack.onErrorNoticePushed(Any())
            }
        }

        assertEquals(1, pushes)
        assertTrue(stack.hasErrorNotice)
    }

    @Test
    fun aDismissalRemovesOnlyTheNotice() {
        val stack = SessionScreenStack()
        stack.onNavigationPush(landed = true)
        val notice = Any()
        stack.onErrorNoticePushed(notice)

        assertTrue(stack.onErrorNoticeDismissed(notice))

        assertFalse("the notice is gone", stack.hasErrorNotice)
        assertTrue("the navigation view underneath is untouched", stack.isNavigationShown)
        assertTrue("a later error needs its own notice", stack.needsErrorNoticePush())
        assertFalse("a dismissal leaves no removal owed", stack.isDismissalOwed)
    }

    /**
     * A reroute keeps the navigation view on screen for the calculation's whole duration: the
     * wait is drawn on the map surface, so no calculation notice is ever pushed — at any
     * percentage, however many updates arrive (spec: `auto/navigation-view` — Reroute keeps the
     * navigation view on screen).
     */
    @Test
    fun aReroutePushesNoScreenHoweverLongItRuns() {
        val stack = SessionScreenStack()
        stack.onNavigationPush(landed = true)

        repeat(101) { percent ->
            assertFalse(
                "a navigating calculation at $percent % pushes no screen",
                stack.needsCalculationNoticePush(navigating = true)
            )
        }

        assertFalse("nothing was recorded on the stack", stack.hasCalculationNotice)
        assertTrue("the navigation view stays the displayed screen", stack.isNavigationShown)
    }

    /**
     * A failed reroute keeps the map on screen: the failure is reported on the surface, so neither
     * a calculation notice nor an error pane is pushed over the navigation view (spec:
     * `route-calculation-feedback` — A failed route calculation during navigation is reported on
     * the map surface).
     */
    @Test
    fun aFailedReroutePushesNoScreen() {
        val stack = SessionScreenStack()
        stack.onNavigationPush(landed = true)

        val shownOnSurface = calculationFailureShownOnSurface(
            isNavigating = true,
            reason = com.naviveylin.core.FailureReason.NO_ROUTE_FOUND
        )
        if (!shownOnSurface) {
            stack.onErrorNoticePushed(Any())
        }

        assertTrue("the failure is the surface's to report", shownOnSurface)
        assertFalse("no error pane was pushed", stack.hasErrorNotice)
        assertFalse("the calculation notice is not used either", stack.hasCalculationNotice)
        assertTrue("the navigation view stays the displayed screen", stack.isNavigationShown)
    }

    @Test
    fun aDismissedNoticeMayBeShownAgain() {
        val stack = SessionScreenStack()
        val notice = Any()
        stack.onErrorNoticePushed(notice)

        assertTrue("dismissing the notice that is up", stack.onErrorNoticeDismissed(notice))

        assertFalse(stack.hasErrorNotice)
        assertTrue("a later error needs its own notice", stack.needsErrorNoticePush())
    }

    @Test
    fun dismissingAStaleNoticeChangesNothing() {
        val stack = SessionScreenStack()
        val older = Any()
        val current = Any()
        stack.onErrorNoticePushed(current)

        assertFalse("a notice that is no longer current", stack.onErrorNoticeDismissed(older))
        assertFalse(stack.onErrorNoticeDismissed(null))

        assertTrue("the notice that is up stays", stack.hasErrorNotice)
        assertSame(current, stack.errorNoticeToken)
    }

    @Test
    fun anErrorNoticeLeavesTheNavigationViewOnTheStack() {
        // The defect this pins: showError pushed the notice and dismissed it with
        // popToRoot(), which took the navigation view with it while the session still
        // believed it was shown — guidance disappeared for the rest of the drive.
        val stack = SessionScreenStack()
        stack.onNavigationPush(landed = true)
        val notice = Any()

        stack.onErrorNoticePushed(notice)
        assertTrue(stack.isNavigationShown)

        assertTrue(stack.onErrorNoticeDismissed(notice))

        assertTrue("the navigation view survives the notice", stack.isNavigationShown)
        assertFalse(stack.hasErrorNotice)
    }

    @Test
    fun aDeferredDismissalIsOwedAndTakenOnce() {
        val stack = SessionScreenStack()
        val notice = Any()
        stack.onErrorNoticePushed(notice)

        stack.onErrorNoticeDismissalDeferred()

        assertTrue("the removal is owed", stack.isDismissalOwed)
        assertSame("the owed removal names the notice", notice, stack.consumeOwedDismissal())
        assertFalse("taken once, not repeatedly", stack.isDismissalOwed)
        assertSame(null, stack.consumeOwedDismissal())
    }

    @Test
    fun nothingIsOwedWhenNoNoticeWasUp() {
        val stack = SessionScreenStack()

        stack.onErrorNoticeDismissalDeferred()

        assertFalse(stack.isDismissalOwed)
        assertSame(null, stack.consumeOwedDismissal())
    }

    @Test
    fun poppingToTheRootForgetsADeferredDismissal() {
        val stack = SessionScreenStack()
        stack.onErrorNoticePushed(Any())
        stack.onErrorNoticeDismissalDeferred()

        stack.onRootPopped()

        assertFalse("the notice went with the stack", stack.isDismissalOwed)
        assertSame(null, stack.consumeOwedDismissal())
    }

    @Test
    fun aCalculationNoticeIsRecordedAndNeverStackedTwice() {
        val stack = SessionScreenStack()
        val notice = Any()

        assertTrue("the first notice needs a push", stack.needsCalculationNoticePush())
        stack.onCalculationNoticePushed(notice)

        assertTrue(stack.hasCalculationNotice)
        assertFalse(
            "a second progress value updates the notice that is up",
            stack.needsCalculationNoticePush()
        )
    }

    @Test
    fun aCalculationNoticesRemovalIsIdentityGuarded() {
        val stack = SessionScreenStack()
        val first = Any()
        val second = Any()
        stack.onCalculationNoticePushed(first)

        assertFalse("a removal of another notice changes nothing", stack.onCalculationNoticeDismissed(second))
        assertTrue(stack.hasCalculationNotice)

        stack.onCalculationNoticePushed(second)
        assertFalse(
            "the superseded notice's removal must not clear the live one",
            stack.onCalculationNoticeDismissed(first)
        )
        assertTrue(stack.hasCalculationNotice)
        assertTrue(stack.onCalculationNoticeDismissed(second))
        assertFalse(stack.hasCalculationNotice)
    }

    @Test
    fun aCalculationNoticesRemovalSkippedWhileStoppedIsOwedToTheNextStart() {
        val stack = SessionScreenStack()
        val notice = Any()
        stack.onCalculationNoticePushed(notice)

        stack.onCalculationNoticeDismissalDeferred()

        assertSame("the owed removal names the notice", notice, stack.consumeOwedCalculationDismissal())
        assertSame("taken once, not repeatedly", null, stack.consumeOwedCalculationDismissal())
    }

    @Test
    fun nothingIsOwedForACalculationNoticeThatWasNeverUp() {
        val stack = SessionScreenStack()

        stack.onCalculationNoticeDismissalDeferred()

        assertSame(null, stack.consumeOwedCalculationDismissal())
    }

    @Test
    fun poppingToTheRootForgetsTheCalculationNoticeAndItsOwedRemoval() {
        val stack = SessionScreenStack()
        stack.onCalculationNoticePushed(Any())
        stack.onCalculationNoticeDismissalDeferred()

        stack.onRootPopped()

        assertFalse("the notice went with the stack", stack.hasCalculationNotice)
        assertSame(null, stack.consumeOwedCalculationDismissal())
    }
}
