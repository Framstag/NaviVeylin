package com.naviveylin.auto

import androidx.car.app.model.PaneTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * German rendering verification for the :auto module (spec: German is fully
 * supported, phone/Auto label parity). Uses [testCarContext] which resolves
 * strings against the real Robolectric resources; the German qualifier makes
 * those resources resolve to values-de.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de")
class GermanRenderingTest {

    @Test
    fun aboutScreenRendersGerman() {
        val screen = AboutScreen(testCarContext())
        val template = screen.onGetTemplate() as PaneTemplate

        assertEquals("Über", template.header?.title?.toString())
        val mapDataRow = template.pane.rows.first { it.title.toString() == "Kartendaten" }
        assertTrue(
            mapDataRow.texts.first().toString().contains("Open Database License")
        )
    }
}
