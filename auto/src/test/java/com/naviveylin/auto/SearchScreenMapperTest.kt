package com.naviveylin.auto

import androidx.car.app.OnDoneCallback
import androidx.car.app.model.Row
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.search.MergedSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchScreenMapperTest {

    private val carContext = testCarContext()

    private fun titles(rows: List<Row>): List<String> = rows.map { it.title.toString() }

    /** Invoke a row's click delegate (host-side callback, no-op response). */
    private fun click(row: Row) = row.onClickDelegate!!.sendClick(object : OnDoneCallback {})

    @Test
    fun buildDescription_withPostalAreaAndRegion() {
        val entry = LocationEntry().apply {
            postalArea = "44339"
            region = arrayOf("Eving", "Dortmund", "NRW")
        }
        assertEquals("44339 — Eving, Dortmund, NRW", SearchScreenMapper.buildDescription(entry))
    }

    @Test
    fun buildDescription_postalAreaOnly() {
        val entry = LocationEntry().apply {
            postalArea = "10115"
            region = emptyArray()
        }
        assertEquals("10115", SearchScreenMapper.buildDescription(entry))
    }

    @Test
    fun buildDescription_regionOnly() {
        val entry = LocationEntry().apply {
            postalArea = ""
            region = arrayOf("Mitte", "Berlin")
        }
        assertEquals("Mitte, Berlin", SearchScreenMapper.buildDescription(entry))
    }

    @Test
    fun buildDescription_empty() {
        val entry = LocationEntry().apply {
            postalArea = ""
            region = emptyArray()
        }
        assertEquals("", SearchScreenMapper.buildDescription(entry))
    }

    @Test
    fun buildDescription_singleRegion() {
        val entry = LocationEntry().apply {
            postalArea = ""
            region = arrayOf("Berlin")
        }
        assertEquals("Berlin", SearchScreenMapper.buildDescription(entry))
    }

    @Test
    fun buildDescription_withPostalAndSingleRegion() {
        val entry = LocationEntry().apply {
            postalArea = "80331"
            region = arrayOf("München")
        }
        assertEquals("80331 — München", SearchScreenMapper.buildDescription(entry))
    }

    // ── Suggestion rows (spec: auto-search-suggestions) ──

    @Test
    fun buildModeRows_showsPoiRowAlways() {
        val rows = SearchScreenMapper.buildModeRows(
            carContext, showContactsRow = false, onPoiSearch = {}, onContactsSearch = {}
        )
        assertEquals(listOf("Search POIs near me"), titles(rows))
    }

    @Test
    fun buildModeRows_contactsRowGatedOnPermission() {
        val withPermission = SearchScreenMapper.buildModeRows(
            carContext, showContactsRow = true, onPoiSearch = {}, onContactsSearch = {}
        )
        assertEquals(
            listOf("Search POIs near me", "Search contacts"),
            titles(withPermission)
        )

        val withoutPermission = SearchScreenMapper.buildModeRows(
            carContext, showContactsRow = false, onPoiSearch = {}, onContactsSearch = {}
        )
        assertEquals(listOf("Search POIs near me"), titles(withoutPermission))
    }

    @Test
    fun buildModeRows_clickListenersInvokeCallbacks() {
        var poiTapped = false
        var contactsTapped = false
        val rows = SearchScreenMapper.buildModeRows(
            carContext,
            showContactsRow = true,
            onPoiSearch = { poiTapped = true },
            onContactsSearch = { contactsTapped = true }
        )

        rows[0].onClickDelegate?.let { click(rows[0]) }
        assertEquals(true, poiTapped)
        click(rows[1])
        assertEquals(true, contactsTapped)
    }

    @Test
    fun buildHistoryRows_headerThenEntries() {
        val rows = SearchScreenMapper.buildHistoryRows(
            carContext, history = listOf("Dortmund Hbf", "Café Central"), onHistorySelected = {}
        )
        assertEquals(
            listOf("Recent searches", "Dortmund Hbf", "Café Central"),
            titles(rows)
        )
    }

    @Test
    fun buildHistoryRows_tapInvokesCallbackWithQuery() {
        var selected: String? = null
        val rows = SearchScreenMapper.buildHistoryRows(
            carContext, history = listOf("Dortmund"), onHistorySelected = { selected = it }
        )
        rows[1].onClickDelegate?.let { click(rows[1]) }
        assertEquals("Dortmund", selected)
    }

    @Test
    fun buildHistoryRows_emptyHistoryShowsOnlyHeader() {
        val rows = SearchScreenMapper.buildHistoryRows(
            carContext, history = emptyList(), onHistorySelected = {}
        )
        assertEquals(listOf("Recent searches"), titles(rows))
    }

    @Test
    fun buildNoResultsRows_noResultsRowThenModeRows() {
        val rows = SearchScreenMapper.buildNoResultsRows(
            carContext, showContactsRow = true, onPoiSearch = {}, onContactsSearch = {}
        )
        assertEquals(
            listOf("No results found", "Search POIs near me", "Search contacts"),
            titles(rows)
        )
    }

    @Test
    fun buildNoResultsRows_contactsRowGatedOnPermission() {
        val rows = SearchScreenMapper.buildNoResultsRows(
            carContext, showContactsRow = false, onPoiSearch = {}, onContactsSearch = {}
        )
        assertEquals(
            listOf("No results found", "Search POIs near me"),
            titles(rows)
        )
    }

    // ── Result rows (spec: favorite-search) ──

    private fun resultEntry(label: String): LocationEntry = LocationEntry().apply {
        this.label = label
        postalArea = "44339"
        region = arrayOf("Eving", "Dortmund")
    }

    @Test
    fun buildResultRow_favoriteShowsHeartImage() {
        val result = MergedSearchResult(resultEntry("Home"), isFavorite = true, isFavoriteHit = true)
        val row = SearchScreenMapper.buildResultRow(carContext, result, onClick = {})

        assertEquals("Home", row.title.toString())
        assertEquals("44339 — Eving, Dortmund", row.texts.joinToString(" ") { it.toString() })
        assertNotNull("favorite row must carry a heart image", row.image)
    }

    @Test
    fun buildResultRow_plainNativeResultHasNoImage() {
        val result = MergedSearchResult(resultEntry("Home Street"), isFavorite = false, isFavoriteHit = false)
        val row = SearchScreenMapper.buildResultRow(carContext, result, onClick = {})

        assertEquals("Home Street", row.title.toString())
        assertNull("non-favorite row must not carry an image", row.image)
    }

    @Test
    fun buildResultRow_clickInvokesCallback() {
        var tapped = false
        val result = MergedSearchResult(resultEntry("Home"), isFavorite = true, isFavoriteHit = true)
        val row = SearchScreenMapper.buildResultRow(carContext, result, onClick = { tapped = true })

        click(row)
        assertEquals(true, tapped)
    }
}
