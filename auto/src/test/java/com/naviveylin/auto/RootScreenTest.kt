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

    init {
        // RootScreen calls enableBackNavigation() in init.
        io.mockk.every { carContext.getOnBackPressedDispatcher() } returns mockk(relaxed = true)
    }

    @Test
    fun onGetTemplateBuildsWithoutThrowing() {
        val screen = RootScreen(carContext, navigationViewModel)

        // Previously threw IllegalArgumentException (click listener on PaneTemplate row).
        val template = screen.onGetTemplate()

        // All seven shortcuts, eagerly delivered on the single list.
        assertEquals(7, template.singleList!!.items.size)
    }
}
