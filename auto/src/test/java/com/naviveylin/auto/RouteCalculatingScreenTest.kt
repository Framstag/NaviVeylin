package com.naviveylin.auto

import androidx.car.app.model.PaneTemplate
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The car wait notice a route calculation raises
 * (spec: `route-calculation-feedback` — Car wait notice while a route is being calculated;
 * spec: `auto/navigation-view` — Reroute keeps the navigation view live under the notice).
 *
 * Default Robolectric sandbox: constructing a car screen and asking for its template needs
 * no host, so this needs no `@Config` (and the JNI stub is never loaded here).
 */
@RunWith(RobolectricTestRunner::class)
class RouteCalculatingScreenTest {

    private fun notice(
        destinationName: String? = null,
        percent: Int? = null,
        cancellable: Boolean = true,
        onCancel: () -> Unit = {}
    ): PaneTemplate = RouteCalculatingScreen(
        carContext = testCarContext(),
        destinationName = destinationName,
        percent = percent,
        cancellable = cancellable,
        onCancel = onCancel
    ).onGetTemplate()

    @Test
    fun theNoticeCarriesTheDestinationAndTheBucketedPercentage() {
        val template = notice(destinationName = "Home", percent = 42)

        assertEquals("Calculating route", template.header?.title?.toString())
        val row = template.pane.rows.single()
        assertEquals("the destination names what is being calculated for", "Home", row.title.toString())
        assertEquals("42 is displayed in its 5 % step", "40%", row.texts.single().toString())
    }

    @Test
    fun anUnnamedDestinationFallsBackToTheNoticeTitleAndAnUnknownPercentageToNoText() {
        val template = notice(destinationName = null, percent = null)

        val row = template.pane.rows.single()
        assertEquals("Calculating route", row.title.toString())
        assertTrue("no percentage before the engine reports one", row.texts.isEmpty())
    }

    @Test
    fun aBlankDestinationNameIsNotDisplayed() {
        val row = notice(destinationName = "   ", percent = 5).pane.rows.single()

        assertEquals("Calculating route", row.title.toString())
        assertEquals("5 stays inside its step and is shown as reported", "5%", row.texts.single().toString())
    }

    @Test
    fun theNoticeOffersCancelWhileNavigationIsNotActive() {
        val template = notice(cancellable = true)

        val row = template.pane.rows.single()
        assertEquals("one cancel action", 1, row.actions.size)
        assertEquals("Cancel", row.actions.single().title.toString())
        assertNull(
            "no back affordance: car-app 1.7 has no back callback, so back could only pop the " +
                "notice and leave the calculation running",
            template.header?.startHeaderAction
        )
    }

    @Test
    fun aRerouteNoticeOffersNoCancel() {
        val template = notice(percent = 30, cancellable = false)

        val row = template.pane.rows.single()
        assertTrue("a live reroute is not the driver's to cancel here", row.actions.isEmpty())
        assertNull("and it carries no other exit either", template.header?.startHeaderAction)
        assertEquals("30% is displayed", "30%", row.texts.single().toString())
    }

    @Test
    fun theCancelActionRunsTheCallback() {
        var cancelled = false
        val template = notice(cancellable = true, onCancel = { cancelled = true })

        template.pane.rows.single().actions.single()
            .onClickDelegate?.sendClick(mockk(relaxed = true))

        assertTrue(cancelled)
    }

    @Test
    fun theNoticesProgressIsUpdatedInPlace() {
        val screen = RouteCalculatingScreen(
            carContext = testCarContext(),
            destinationName = null,
            percent = 10,
            cancellable = true
        )

        screen.update(destinationName = "Work", percent = 25)

        val row = screen.onGetTemplate().pane.rows.single()
        assertEquals("Work", row.title.toString())
        assertEquals("25 stays inside its step", "25%", row.texts.single().toString())
    }

    @Test
    fun theDisplayedPercentageIsBucketedToTheStep() {
        assertEquals(0, displayedCalculationPercent(3))
        assertEquals(5, displayedCalculationPercent(5))
        assertEquals(40, displayedCalculationPercent(44))
        assertEquals(40, displayedCalculationPercent(40))
        assertEquals(95, displayedCalculationPercent(99))
        assertNull(displayedCalculationPercent(null))
    }
}
