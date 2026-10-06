package com.naviveylin.auto

import com.naviveylin.core.RouteCalculation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decisions the car session makes about the route-calculation wait notice
 * (spec: `route-calculation-feedback` — Car wait notice while a route is being calculated,
 * The car notice never outlives its calculation).
 *
 * These are the pure seams of [NavigationSession]'s notice sync: the session itself needs a
 * host-provided `CarContext` and cannot be constructed in Robolectric, so the delay gate, the
 * one-notice rule and the "never outlives its calculation" rule are asserted here rather than
 * through the session. The host calls around them (push, `invalidate`, `ScreenManager.remove`)
 * are verified by their guards' own suites (`ClickPathDefersAndConfinesTest`,
 * `CarHostGuardsTest`) and by the device check of the change's task 5.2.
 */
class CalculationNoticeDecisionTest {

    private fun calculation(token: Long, percent: Int? = null) =
        RouteCalculation(token = token, destLat = 52.5, destLon = 13.4, percent = percent)

    @Test
    fun theNoticeIsOnlyRefreshedWhenTheDisplayedStepChanges() {
        assertTrue(
            "the first reported percentage always refreshes",
            noticeNeedsUpdate(showingBucket = null, reportedPercent = 3)
        )
        assertFalse(
            "a percent inside the same 5 % step costs no host push",
            noticeNeedsUpdate(showingBucket = 40, reportedPercent = 41)
        )
        assertTrue(
            noticeNeedsUpdate(showingBucket = 40, reportedPercent = 45)
        )
        assertTrue(
            "an unreported percentage must not be mistaken for a step",
            noticeNeedsUpdate(showingBucket = 40, reportedPercent = null)
        )
    }

    @Test
    fun theDelayIsArmedForANewCalculationWithoutANotice() {
        assertTrue(
            needsNoticeArm(
                live = calculation(1L),
                noticeShown = false,
                armedToken = null,
                arming = false
            )
        )
    }

    @Test
    fun aPercentageUpdateDoesNotRestartTheRunningDelay() {
        assertFalse(
            "the same calculation's second percentage must not re-arm the delay",
            needsNoticeArm(
                live = calculation(1L, percent = 20),
                noticeShown = false,
                armedToken = 1L,
                arming = true
            )
        )
    }

    @Test
    fun aNewerCalculationTakesTheDelayOverFromTheOlderOne() {
        assertTrue(
            "the newer request is what the driver waits for",
            needsNoticeArm(
                live = calculation(2L),
                noticeShown = false,
                armedToken = 1L,
                arming = true
            )
        )
    }

    @Test
    fun aFinishedDelayIsNotAnArmAnyMore() {
        assertTrue(
            "the job is gone, so the next emission may arm (and push) again",
            needsNoticeArm(
                live = calculation(1L),
                noticeShown = false,
                armedToken = 1L,
                arming = false
            )
        )
    }

    @Test
    fun nothingIsArmedWithoutACalculationOrWithANoticeAlreadyUp() {
        assertFalse(
            needsNoticeArm(live = null, noticeShown = false, armedToken = null, arming = false)
        )
        assertFalse(
            needsNoticeArm(
                live = calculation(1L),
                noticeShown = true,
                armedToken = null,
                arming = false
            )
        )
    }

    @Test
    fun theNoticeIsStaleOnceItsCalculationIsGone() {
        assertTrue(noticeIsStale(live = null, noticeShown = true))
        assertFalse(
            "a live calculation keeps its notice",
            noticeIsStale(live = calculation(1L), noticeShown = true)
        )
        assertFalse(
            "nothing to remove when no notice is up",
            noticeIsStale(live = null, noticeShown = false)
        )
    }

    @Test
    fun theDelayMayOnlyRaiseTheNoticeOfItsOwnCalculation() {
        assertTrue(noticeIsStillArmedFor(1L, calculation(1L, percent = 40)))
        assertFalse(noticeIsStillArmedFor(1L, calculation(2L)))
        assertFalse(noticeIsStillArmedFor(1L, null))
        assertFalse(noticeIsStillArmedFor(null, calculation(1L)))
    }
}
