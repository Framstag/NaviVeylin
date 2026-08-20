package com.naviveylin.auto

import androidx.car.app.CarContext
import com.naviveylin.core.NavigationViewModel
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression test: [RootScreen] must build its template without throwing.
 *
 * Previously RootScreen used [androidx.car.app.model.PaneTemplate] with rows
 * that had click listeners — a car-app constraint violation that fails the
 * template build with "A click listener is not allowed on the row", which
 * crashed the Android Auto session on every startup. List-based templates
 * (SectionedItemTemplate + RowSection) allow row click listeners.
 */
@RunWith(RobolectricTestRunner::class)
class RootScreenTest {

    private val carContext = mockk<CarContext>()
    private val navigationViewModel = mockk<NavigationViewModel>()

    @Test
    fun onGetTemplateBuildsWithoutThrowing() {
        val screen = RootScreen(carContext, navigationViewModel)

        // Previously threw IllegalArgumentException (click listener on PaneTemplate row).
        val template = screen.onGetTemplate()

        assertEquals(1, template.sections.size)
        // Section items are delivered lazily via a ListDelegate; the delegate
        // reports the section size, which must cover all seven shortcuts.
        assertEquals(7, template.sections[0].itemsDelegate.size)
    }
}
