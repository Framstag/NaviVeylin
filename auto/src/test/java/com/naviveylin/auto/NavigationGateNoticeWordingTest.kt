package com.naviveylin.auto

import androidx.car.app.model.PaneTemplate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The car half of the navigation gate's wording (spec: `location-permissions` —
 * Starting navigation requires precise location / Car shows the message without
 * launching settings): the refusal reaches the driver through the existing,
 * guarded error notice with the same string the phone dialog uses, and the notice
 * offers nothing but a way back — no settings launch, no other host action.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationGateNoticeWordingTest {

    @Test
    fun theRefusalIsShownWithTheSharedWording() {
        val carContext = testCarContext()
        val message = carContext.getString(
            com.naviveylin.core.R.string.location_precise_required_navigation
        )

        val template = ErrorOverlayScreen(carContext, message).onGetTemplate() as PaneTemplate
        val row = template.pane.rows.first()

        assertEquals(
            "the car notice must show the shared refusal wording",
            message,
            row.title.toString()
        )
        assertEquals(
            "the notice is non-blocking: exactly one action (back)",
            1,
            row.actions.size
        )
    }
}
