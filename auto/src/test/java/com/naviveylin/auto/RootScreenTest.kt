package com.naviveylin.auto

import android.content.pm.PackageManager
import androidx.car.app.model.Row
import androidx.car.app.CarContext
import com.naviveylin.core.NavigationViewModel
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        // Address-book entry visibility reads READ_CONTACTS via ContextCompat
        // (default: denied). ContextCompat is a real library class, so it is
        // stubbed statically; CarContext's inherited Context methods cannot be
        // mocked (android.jar stub classes).
        io.mockk.mockkStatic("androidx.core.content.ContextCompat")
        io.mockk.every {
            androidx.core.content.ContextCompat.checkSelfPermission(any(), any())
        } returns PackageManager.PERMISSION_DENIED
    }

    @Test
    fun onGetTemplateBuildsWithoutThrowing() {
        val screen = RootScreen(carContext, navigationViewModel)

        // Previously threw IllegalArgumentException (click listener on PaneTemplate row).
        val template = screen.onGetTemplate()

        // Seven shortcuts: address book hidden while READ_CONTACTS is denied.
        assertEquals(7, template.singleList!!.items.size)
    }

    @Test
    fun addressBookEntryVisibleWhenPermissionGranted() {
        io.mockk.every {
            androidx.core.content.ContextCompat.checkSelfPermission(any(), any())
        } returns PackageManager.PERMISSION_GRANTED
        val screen = RootScreen(carContext, navigationViewModel)

        val template = screen.onGetTemplate()

        assertEquals(8, template.singleList!!.items.size)
        val titles = template.singleList!!.items.map { (it as Row).title.toString() }
        assertEquals("Address book", titles[4])
        // The entry is wired to open the address-book screen (click delegate
        // present; the push itself needs a live CarContext + Hilt entry point,
        // same constraint as the other root shortcuts).
        assertNotNull((template.singleList!!.items[4] as Row).onClickDelegate)
    }
}
