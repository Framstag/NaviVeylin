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

    @Test
    fun diagnosticsScreenRendersTheGermanDisclosure() {
        val screen = DiagnosticsScreen(testCarContext())
        val template = screen.onGetTemplate() as PaneTemplate
        val disclosure = testCarContext().getString(
            com.naviveylin.core.R.string.diagnostics_disclosure
        )

        assertEquals(
            "the car disclosure is German too (spec: auto-diagnostics — Disclosure is localized)",
            disclosure,
            template.pane.rows.first().title.toString()
        )
    }

    @Test
    fun aboutScreenShowsThePrivacyStatementInGerman() {
        val screen = AboutScreen(testCarContext())
        val template = screen.onGetTemplate() as PaneTemplate
        val carContext = testCarContext()
        val privacyRow = template.pane.rows.first {
            it.title.toString() == carContext.getString(com.naviveylin.core.R.string.about_privacy_title)
        }

        assertEquals(
            carContext.getString(com.naviveylin.core.R.string.about_privacy_statement),
            privacyRow.texts.first().toString()
        )
    }

    @Test
    fun searchSuggestionsRenderGerman() {
        // New empty-query suggestion strings (change unify-auto-search) must
        // render in German, not fall back to English.
        val rows = SearchScreenMapper.buildModeRows(
            testCarContext(), showContactsRow = true, onPoiSearch = {}, onContactsSearch = {}
        )
        assertEquals("POIs in meiner Nähe suchen", rows[0].title.toString())
        assertEquals("Kontakte suchen", rows[1].title.toString())

        val historyRows = SearchScreenMapper.buildHistoryRows(
            testCarContext(), history = listOf("Dortmund"), onHistorySelected = {}
        )
        assertEquals("Letzte Suchanfragen", historyRows[0].title.toString())
    }

    @Test
    fun theRouteCalculationNoticeRendersGerman() {
        val template = RouteCalculatingScreen(
            carContext = testCarContext(),
            destinationName = "Zuhause",
            percent = 42,
            cancellable = true
        ).onGetTemplate()

        assertEquals("Route wird berechnet", template.header?.title?.toString())
        val row = template.pane.rows.single()
        assertEquals("Zuhause", row.title.toString())
        assertEquals("40 %", row.texts.single().toString())
        assertEquals("Abbrechen", row.actions.single().title.toString())
    }
}
