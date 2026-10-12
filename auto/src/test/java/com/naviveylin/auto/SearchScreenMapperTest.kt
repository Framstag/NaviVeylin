package com.naviveylin.auto

import androidx.car.app.OnDoneCallback
import androidx.car.app.model.Row
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.core.search.MergedSearchResult
import com.naviveylin.core.search.SearchReference
import com.naviveylin.core.search.carRowDescription
import com.naviveylin.core.search.rowTitle
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
    fun buildDescription_showsThePostalAreaOnce() {
        val entry = LocationEntry().apply {
            postalArea = "44339"
            // The bridge fills region[] as [adminRegion, postalArea] — not the hierarchy its javadoc
            // promises, which is what made the row print the code twice.
            region = arrayOf("Dortmund", "44339")
        }
        assertEquals("44339", carRowDescription(entry))
    }

    @Test
    fun buildDescription_empty() {
        val entry = LocationEntry().apply {
            postalArea = ""
            region = emptyArray()
        }
        assertEquals("", carRowDescription(entry))
    }

    @Test
    fun resultRowTitle_carriesTheLabelAndTheCity() {
        val entry = LocationEntry().apply {
            label = "Rosenweg 20"
            region = arrayOf("Dortmund", "44339")
        }
        assertEquals("Rosenweg 20 - Dortmund", rowTitle(entry))
    }

    @Test
    fun resultRowTitle_doesNotRepeatTheNameWhenTheLabelIsTheRegion() {
        val entry = LocationEntry().apply {
            label = "Dortmund"
            region = arrayOf("Dortmund", "44139")
        }
        assertEquals("Dortmund", rowTitle(entry))
    }

    @Test
    fun buildResultRow_titleAndDescriptionComeFromTheSharedComposer() {
        val result = MergedSearchResult(
            entry = LocationEntry().apply {
                label = "Rosenweg 20"
                region = arrayOf("Dortmund", "44339")
                postalArea = "44339"
            },
            isFavorite = false,
            isFavoriteHit = false
        )
        val row = SearchScreenMapper.buildResultRow(carContext, result, onClick = {})
        assertEquals("Rosenweg 20 - Dortmund", row.title.toString())
        assertEquals("44339", row.texts.first().toString())
    }

    @Test
    fun buildResultRow_typeIconAndMarkingShareTheSingleImage() {
        val result = MergedSearchResult(
            entry = LocationEntry().apply { label = "Shell" },
            isFavorite = true,
            isFavoriteHit = false,
            isPerfectMatch = false
        )
        val icon = android.graphics.Bitmap.createBitmap(60, 60, android.graphics.Bitmap.Config.ARGB_8888)
        val row = SearchScreenMapper.buildResultRow(carContext, result, typeIcon = icon, onClick = {})
        // One image slot on a car Row: the composite carries the marking and the icon together, so
        // the marking is not pushed out by the icon and the row is not given a second image.
        assertNotNull(row.image)
    }

    @Test
    fun buildResultRow_typeIconAloneStillUsesTheImageSlot() {
        val result = MergedSearchResult(
            entry = LocationEntry().apply { label = "Shell" },
            isFavorite = false,
            isFavoriteHit = false
        )
        val icon = android.graphics.Bitmap.createBitmap(60, 60, android.graphics.Bitmap.Config.ARGB_8888)
        val row = SearchScreenMapper.buildResultRow(carContext, result, typeIcon = icon, onClick = {})
        assertNotNull(row.image)
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

        assertEquals("Home - Eving", row.title.toString())
        assertEquals("44339", row.texts.joinToString(" ") { it.toString() })
        assertNotNull("favorite row must carry a heart image", row.image)
    }

    @Test
    fun buildResultRow_plainNativeResultHasNoImage() {
        val result = MergedSearchResult(resultEntry("Home Street"), isFavorite = false, isFavoriteHit = false)
        val row = SearchScreenMapper.buildResultRow(carContext, result, onClick = {})

        assertEquals("Home Street - Eving", row.title.toString())
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

    // ── Perfect-match marking and distance (spec: search-result-ranking) ──

    private fun perfectEntry(label: String, lat: Double = 51.5, lon: Double = 7.4): LocationEntry =
        resultEntry(label).apply {
            this.lat = lat
            this.lon = lon
            matchedName = label
            matchedComponent = "location"
            locationMatchQuality = "match"
        }

    @Test
    fun buildResultRow_perfectMatchShowsMarkingImage() {
        val result = MergedSearchResult(
            perfectEntry("Waltrop"),
            isFavorite = false,
            isFavoriteHit = false,
            isPerfectMatch = true
        )

        val row = SearchScreenMapper.buildResultRow(carContext, result, onClick = {})

        assertNotNull("perfect-match row must carry the marking glyph", row.image)
    }

    @Test
    fun buildResultRow_favoriteAndPerfectRowShowsOneCompositeMarking() {
        val result = MergedSearchResult(
            perfectEntry("Waltrop"),
            isFavorite = true,
            isFavoriteHit = false,
            isPerfectMatch = true
        )

        val row = SearchScreenMapper.buildResultRow(carContext, result, onClick = {})

        // One image slot on a car Row: both facts live in the composite glyph.
        assertNotNull(row.image)
        assertEquals("Waltrop - Eving", row.title.toString())
    }

    @Test
    fun buildResultRow_closeMatchHasNoMarkingImage() {
        val result = MergedSearchResult(
            resultEntry("Waltroper Straße"),
            isFavorite = false,
            isFavoriteHit = false,
            isPerfectMatch = false
        )

        val row = SearchScreenMapper.buildResultRow(carContext, result, onClick = {})

        assertNull(row.image)
    }

    @Test
    fun buildResultRow_showsDistanceFromTheReferenceAsASecondTextLine() {
        // ~1.0 km north of the reference (0.009° latitude).
        val result = MergedSearchResult(
            perfectEntry("Waltrop", lat = 51.5226, lon = 7.4653),
            isFavorite = false,
            isFavoriteHit = false,
            isPerfectMatch = true
        )

        val row = SearchScreenMapper.buildResultRow(
            carContext,
            result,
            reference = SearchReference(51.5136, 7.4653),
            onClick = {}
        )

        assertEquals(
            listOf("44339", "1.0 km"),
            row.texts.map { it.toString() }
        )
    }

    @Test
    fun buildResultRow_showsNoDistanceWithoutAReference() {
        val result = MergedSearchResult(
            perfectEntry("Waltrop"),
            isFavorite = false,
            isFavoriteHit = false,
            isPerfectMatch = true
        )

        val row = SearchScreenMapper.buildResultRow(carContext, result, reference = null, onClick = {})

        assertEquals(listOf("44339"), row.texts.map { it.toString() })
    }
}
