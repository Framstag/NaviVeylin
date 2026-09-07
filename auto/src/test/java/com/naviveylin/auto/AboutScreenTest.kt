package com.naviveylin.auto

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.model.PaneTemplate
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [AboutScreen] (spec: osm-attribution — "Licence info reachable
 * in car app"). Default Robolectric sandbox, no @Config.
 */
@RunWith(RobolectricTestRunner::class)
class AboutScreenTest {

    private val carContext = mockk<CarContext>()

    @Before
    fun setUp() {
        every { carContext.getOnBackPressedDispatcher() } returns mockk(relaxed = true)
        every { carContext.packageName } returns "com.naviveylin"
        val pm = mockk<PackageManager>()
        every { carContext.packageManager } returns pm
        val info = PackageInfo()
        info.versionName = "1.2.3"
        every { pm.getPackageInfo(any<String>(), any<Int>()) } returns info
        // Resolve localized strings against real Robolectric resources (English default)
        val appContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        every { carContext.getString(any()) } answers { appContext.getString(firstArg()) }
    }

    @Test
    fun aboutScreenShowsOsmLicenceStatement() {
        val screen = AboutScreen(carContext)
        val template = screen.onGetTemplate() as PaneTemplate

        val mapDataRow = template.pane.rows.first { it.title.toString() == "Map data" }
        assertTrue(
            mapDataRow.texts.first().toString().contains("Open Database License")
        )
    }

    @Test
    fun aboutScreenHasCopyrightLinkAction() {
        val screen = AboutScreen(carContext)
        val template = screen.onGetTemplate() as PaneTemplate

        val link = template.pane.actions.first { it.title.toString() == "openstreetmap.org/copyright" }
        assertTrue(link.onClickDelegate != null)
    }

    @Test
    fun aboutScreenShowsVersion() {
        val screen = AboutScreen(carContext)
        val template = screen.onGetTemplate() as PaneTemplate

        val versionRow = template.pane.rows.first { it.title.toString() == "Version" }
        assertEquals("1.2.3", versionRow.texts.first().toString())
    }
}
