package com.naviveylin.auto

import android.content.Context
import androidx.car.app.AppManager
import androidx.car.app.ScreenManager
import androidx.car.app.model.Row
import androidx.car.app.model.ListTemplate
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.DescriptionEntry
import com.framstag.libosmscout.client.FakeMapScreenClient
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.ObjectDescription
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.AutoClientProvider
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.core.AutoLocationProvider
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.NavigationState
import com.naviveylin.core.formatCoordinatePair
import dagger.hilt.EntryPoints
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Tests for the shared details screen (spec: auto-destination-details, and — for the observation
 * lifetime — auto/screen-observation): labeled attribute rows (coordinates/address/area/description),
 * ALL description attributes listed (no row cap — opening hours, phone, …), street/address dedup,
 * title fallback chain (name → address → label → generic), the "Navigate to" / "Show" actions, and
 * the started-period lifetime of the screen's shared-state observations.
 */
@RunWith(RobolectricTestRunner::class)
class DetailsScreenTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val carContext = testCarContext()
    private val entryPoint = mockk<AutoEntryPoint>()
    private val client: OSMScoutClient = FakeMapScreenClient()

    /**
     * The favorites source the screen observes. [MutableStateFlow.subscriptionCount] is the probe for
     * the started-period lifetime: it counts how many collectors the screen currently holds, which is
     * exactly what "one instance per started period, none while stopped" means at the wiring level
     * (spec: auto/screen-observation; design D4).
     */
    private val favoritesFlow = MutableStateFlow<Map<String, List<FavoriteLocation>>>(emptyMap())

    /** The destination both the screen and its favorite fixture use. */
    private val destLat = 51.5
    private val destLon = 7.5

    @Before
    fun setUp() {
        every { carContext.applicationContext } returns ApplicationProvider.getApplicationContext<Context>()
        every { carContext.resources } returns ApplicationProvider.getApplicationContext<Context>().resources
        every { carContext.getCarService(ScreenManager::class.java) } returns mockk(relaxed = true)
        // `Screen.invalidate()` asks the host through AppManager, so an unstubbed service makes the
        // favorite observation fault (and end) instead of applying its state.
        every { carContext.getCarService(AppManager::class.java) } returns mockk(relaxed = true)
        every { entryPoint.autoClientProvider() } returns mockk<AutoClientProvider>().apply {
            every { client() } returns this@DetailsScreenTest.client
        }
        every { entryPoint.autoFavoritesProvider() } returns mockk<AutoFavoritesProvider>().apply {
            every { favoriteLocations() } returns favoritesFlow
        }
        every { entryPoint.autoLocationProvider() } returns mockk<AutoLocationProvider>().apply {
            every { position() } returns MutableStateFlow<AutoPosition?>(null)
        }
        every { entryPoint.basemapReloadNotifier() } returns mockk<BasemapReloadNotifier>().apply {
            every { revision } returns MutableStateFlow(0L)
        }
        every { entryPoint.autoSurfaceHost() } returns SessionCarSurfaceHost()
        mockkStatic(EntryPoints::class)
        every { EntryPoints.get(any(), AutoEntryPoint::class.java) } returns entryPoint
    }

    private fun newScreen() = DetailsScreen(carContext, mockk(relaxed = true), lat = destLat, lon = destLon)

    /** The favorite action row of the built template, i.e. what the screen currently believes. */
    private fun favoriteRowTitle(screen: DetailsScreen): String {
        val template = screen.onGetTemplate() as MapWithContentTemplate
        val content = template.contentTemplate as ListTemplate
        return content.singleList!!.items
            .map { (it as Row).title.toString() }
            .first { it.contains("Favorite") || it.contains("Favoriten") }
    }

    /** The visible row titles of the built template. */
    private fun rowTitles(screen: DetailsScreen): List<String> {
        val template = screen.onGetTemplate() as MapWithContentTemplate
        val content = template.contentTemplate as ListTemplate
        return content.singleList!!.items.map { (it as Row).title.toString() }
    }

    @Test
    fun theDegradedMapIsStatedAndAFreshStartClearsIt() = runTest(mainDispatcher.dispatcher) {
        // Spec: car-host-fault-isolation — A repeatedly faulting car renderer recovers, then degrades
        // visibly, and A fresh screen start re-arms the recovery budget. The driver must see the
        // condition (design D5: the map surface may not be able to carry it), and a start must give
        // the map another chance instead of keeping the screen degraded forever.
        val screen = newScreen()
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)

        assertTrue(
            "a healthy map states nothing",
            rowTitles(screen).none { it == carContext.getString(R.string.map_unavailable) }
        )

        screen.rendererGate.markDegraded()
        advanceUntilIdle()

        assertTrue(
            "the degraded map is stated on the car screen",
            rowTitles(screen).contains(carContext.getString(R.string.map_unavailable))
        )

        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
        advanceUntilIdle()

        assertTrue(
            "a fresh started period clears the degraded state",
            rowTitles(screen).none { it == carContext.getString(R.string.map_unavailable) }
        )
        assertTrue(
            "and re-arms the recovery budget",
            !screen.rendererGate.renderSupervisor.isSuspended
        )
    }

    @Test
    fun oneObservationSetPerStartedPeriodAcrossStopAndStart() = runTest(mainDispatcher.dispatcher) {
        // Spec: auto/screen-observation — One instance of each observation per started period. The
        // host stops and starts a screen on every background round trip and on every push/pop of
        // another screen, so a per-collector job list would accumulate copies (TODO.md §57/§103).
        // [MutableStateFlow.subscriptionCount] is the wiring probe: it counts the collectors this
        // screen currently holds, i.e. exactly what "one per started period, none while stopped"
        // means for the screen (the seam's own contract is covered by
        // [DetailsScreenObservationsTest]).
        val screen = newScreen()
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)

        repeat(10) { cycle ->
            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
            advanceUntilIdle()
            assertEquals(
                "exactly one favorites observation in cycle ${cycle + 1}",
                1,
                favoritesFlow.subscriptionCount.value
            )

            screen.dispatchLifecycleEvent(Lifecycle.Event.ON_STOP)
            advanceUntilIdle()
            assertEquals(
                "no observation survives the stopped period of cycle ${cycle + 1}",
                0,
                favoritesFlow.subscriptionCount.value
            )
        }
    }

    @Test
    fun aStoppedScreenAppliesNothingAndTheStateArrivesOnStart() = runTest(mainDispatcher.dispatcher) {
        // Spec: auto/screen-observation — A stopped screen performs no renderer or host work, and
        // Observations are re-established with the current state on start. The favorite action row is
        // the screen's observable state here: it flips to "Remove from Favorites" only when the
        // current favorite set reached the screen.
        val screen = newScreen()
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
        advanceUntilIdle()
        assertEquals(
            "no favorite yet: the row offers to add",
            carContext.getString(R.string.add_to_favorites),
            favoriteRowTitle(screen)
        )

        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_STOP)
        advanceUntilIdle()

        // The phone adds the very destination the screen shows, while the screen is stopped.
        favoritesFlow.value = mapOf("Default" to listOf(FavoriteLocation("Dest", destLat, destLon)))
        advanceUntilIdle()

        assertEquals(
            "a stopped screen applies nothing to its own state",
            carContext.getString(R.string.add_to_favorites),
            favoriteRowTitle(screen)
        )

        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
        advanceUntilIdle()

        assertEquals(
            "the current favorite state is applied on start",
            carContext.getString(R.string.remove_from_favorites),
            favoriteRowTitle(screen)
        )
    }

    /** The car's generic details title, supplied by the screen from its resource. */
    private val GENERIC_TITLE = "Standort (test)"

    private fun description(vararg entries: DescriptionEntry): ObjectDescription =
        ObjectDescription(entries.toList(), Double.NaN, Double.NaN, "area", "building", 1L)

    private fun entry(section: String, label: String, value: String): DescriptionEntry =
        DescriptionEntry().apply {
            sectionKey = section
            labelKey = label
            this.value = value
        }

    @Test
    fun coordinatesRowIsLocaleStableOnAGermanDevice() {
        // Spec: auto-destination-details — Car coordinates row is locale-stable.
        // A German device used to show "51,51391, 7,47434" here.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)

            val rows = buildAttributeList(
                testCarContext(),
                lat = 51.51391, lon = 7.47434, address = null, description = null
            )

            assertEquals("51.51391, 7.47434", rows[0].texts[0].toString())
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun coordinatesRowMatchesTheNavigationDestinationText() {
        // Spec: auto-destination-details — Destination text and coordinates row
        // agree. Both go through the shared coordinate helper, so the details row
        // and the navigation template can never show different strings.
        val previous = Locale.getDefault()
        try {
            for (locale in listOf(Locale.GERMANY, Locale.US)) {
                Locale.setDefault(locale)

                val lat = 51.51391
                val lon = 7.47434
                val rows = buildAttributeList(
                    testCarContext(), lat = lat, lon = lon, address = null, description = null
                )
                val destinationText = NavigationTemplateMapper.coordinatesText(
                    NavigationState(isNavigating = true, destLat = lat, destLon = lon)
                )

                assertEquals(formatCoordinatePair(lat, lon), rows[0].texts[0].toString())
                assertEquals(rows[0].texts[0].toString(), destinationText)
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun coordinatesRowAlwaysPresentWithLabel() {
        val rows = buildAttributeList(testCarContext(),
            lat = 51.5136, lon = 7.4653, address = null, description = null
        )
        assertEquals(1, rows.size)
        assertEquals("Coordinates", rows[0].title.toString())
        assertTrue(rows[0].texts.any { it.toString().contains("51.51360") })
        assertTrue(rows[0].texts.any { it.toString().contains("7.46530") })
    }

    @Test
    fun addressShownAsLabeledRow() {
        val rows = buildAttributeList(testCarContext(),
            lat = 51.5136, lon = 7.4653,
            address = arrayOf("Kleppingstr.", "22", "Dortmund", "44139"),
            description = null
        )
        assertEquals(3, rows.size)
        assertEquals("Coordinates", rows[0].title.toString())
        assertEquals("Address", rows[1].title.toString())
        assertEquals("Kleppingstr. 22, 44139 Dortmund", rows[1].texts[0].toString())
        assertEquals("Area", rows[2].title.toString())
        assertEquals("Dortmund", rows[2].texts[0].toString())
    }

    @Test
    fun areaFallsBackToPostalArea() {
        val rows = buildAttributeList(testCarContext(),
            lat = 51.5136, lon = 7.4653,
            address = arrayOf("", "", "", "44139"),
            description = null
        )
        assertEquals(2, rows.size)
        assertEquals("Area", rows[1].title.toString())
        assertEquals("44139", rows[1].texts[0].toString())
    }

    @Test
    fun areaFallsBackToDescriptionIsIn() {
        val rows = buildAttributeList(testCarContext(),
            lat = 51.5136, lon = 7.4653, address = null,
            description = description(
                DescriptionEntry().apply {
                    sectionKey = "Location"
                    subsectionKey = "AdminLevel"
                    labelKey = "IsIn"
                    value = "Dortmund, Dortmund, Nordrhein-Westfalen"
                }
            )
        )
        assertEquals(3, rows.size)
        assertEquals("Area", rows[1].title.toString())
        assertEquals("Dortmund, Dortmund, Nordrhein-Westfalen", rows[1].texts[0].toString())
        // The IsIn row itself stays visible as a description entry (phone parity).
        assertEquals("IsIn", rows[2].title.toString())
    }

    @Test
    fun noAreaRowWithoutAreaData() {
        val rows = buildAttributeList(testCarContext(),
            lat = 51.5136, lon = 7.4653, address = arrayOf("", "", "", ""),
            description = description(entry("General", "Type", "hotel"))
        )
        assertEquals(2, rows.size)
        assertEquals("Coordinates", rows[0].title.toString())
        assertEquals("Type", rows[1].title.toString())
    }

    @Test
    fun streetAndAddressEntriesNotDuplicated() {
        // The combined Address row covers Location/Location (street) and
        // Location/Address (house number) — neither reappears as a row.
        val rows = buildAttributeList(testCarContext(),
            lat = 51.5136, lon = 7.4653,
            address = arrayOf("Kleppingstr.", "22", "Dortmund", "44139"),
            description = description(
                entry("Location", "Address", "22"),
                entry("Location", "Location", "Kleppingstr."),
                entry("General", "Type", "hotel")
            )
        )
        assertEquals(4, rows.size)
        assertEquals("Coordinates", rows[0].title.toString())
        assertEquals("Address", rows[1].title.toString())
        assertEquals("Area", rows[2].title.toString())
        assertEquals("Type", rows[3].title.toString())
    }

    @Test
    fun descriptionEntriesShownAsLabeledRows() {
        val rows = buildAttributeList(testCarContext(),
            lat = 51.5136, lon = 7.4653, address = null,
            description = description(
                entry("General", "Name", "Mario's"),
                entry("General", "Type", "restaurant")
            )
        )
        // Coordinates row + 2 description rows
        assertEquals(3, rows.size)
        assertEquals("Name", rows[1].title.toString())
        assertEquals("Mario's", rows[1].texts[0].toString())
        assertEquals("Type", rows[2].title.toString())
    }

    @Test
    fun blankDescriptionEntriesSkipped() {
        val rows = buildAttributeList(testCarContext(),
            lat = 51.5136, lon = 7.4653, address = null,
            description = description(
                entry("General", "Name", "   "),
                entry("General", "Type", "restaurant")
            )
        )
        assertEquals(2, rows.size)
        assertEquals("Type", rows[1].title.toString())
    }

    @Test
    fun allDescriptionAttributesShown() {
        // Every attribute returned by the description API must be listed —
        // no fixed row limit drops attributes like opening hours or phone.
        val rows = buildAttributeList(testCarContext(),
            lat = 51.5136, lon = 7.4653,
            address = arrayOf("Kleppingstr.", "22", "Dortmund", "44139"),
            description = description(
                entry("General", "Name", "Mario's"),
                entry("General", "Type", "restaurant"),
                entry("Contact", "Phone", "+49 231 123456"),
                entry("General", "OpeningHours", "Mo-Fr 09:00-18:00"),
                entry("General", "Website", "https://example.com")
            )
        )
        assertEquals(8, rows.size) // coordinates + address + area + 5 attributes
        assertEquals("OpeningHours", rows[6].title.toString())
        assertEquals("Mo-Fr 09:00-18:00", rows[6].texts[0].toString())
        assertEquals("Phone", rows[5].title.toString())
        assertEquals("Website", rows[7].title.toString())
    }

    @Test
    fun openingHoursShownOnDetailsScreen() {
        val rows = buildAttributeList(testCarContext(),
            lat = 51.5136, lon = 7.4653, address = null,
            description = description(
                entry("General", "OpeningHours", "Mo-Fr 09:00-18:00")
            )
        )
        assertEquals(2, rows.size)
        assertEquals("OpeningHours", rows[1].title.toString())
        assertEquals("Mo-Fr 09:00-18:00", rows[1].texts[0].toString())
    }

    @Test
    fun noRowCapForLongDescriptions() {
        // More entries than any fixed pane cap: every one must be present.
        val rows = buildAttributeList(testCarContext(),
            lat = 51.5136, lon = 7.4653, address = null,
            description = description(
                entry("General", "A", "1"),
                entry("General", "B", "2"),
                entry("General", "C", "3"),
                entry("General", "D", "4"),
                entry("General", "E", "5"),
                entry("General", "F", "6")
            )
        )
        assertEquals(7, rows.size) // coordinates + 6 entries
    }

    @Test
    fun descriptionEntriesKeepNativeOrder() {
        val rows = buildAttributeList(testCarContext(),
            lat = 51.5136, lon = 7.4653,
            address = arrayOf("Kleppingstr.", "22", "Dortmund", "44139"),
            description = description(
                entry("General", "Type", "restaurant"),
                entry("Contact", "Phone", "+49 231 123456")
            )
        )
        assertEquals(5, rows.size)
        assertEquals("Coordinates", rows[0].title.toString())
        assertEquals("Address", rows[1].title.toString())
        assertEquals("Area", rows[2].title.toString())
        assertEquals("Type", rows[3].title.toString())
        assertEquals("Phone", rows[4].title.toString())
    }

    @Test
    fun navigateRowInvokesCallback() {
        var invoked = false
        val row = buildNavigateRow(testCarContext()) { invoked = true }
        assertEquals("▶ Navigate to", row.title.toString())
        assertTrue("row must be clickable", row.onClickDelegate != null)
        row.onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        assertTrue(invoked)
    }

    @Test
    fun showRowInvokesCallback() {
        var invoked = false
        val row = buildShowRow(testCarContext()) { invoked = true }
        assertEquals("◎ Show", row.title.toString())
        assertTrue("row must be clickable", row.onClickDelegate != null)
        row.onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        assertTrue(invoked)
    }

    @Test
    fun attributeRowsAreNotClickable() {
        // The details screen's only navigation trigger is the "Navigate to"
        // row: attribute rows carry no click listeners, so popping the screen
        // (system back) never starts navigation.
        val rows = buildAttributeList(testCarContext(),
            lat = 51.5136, lon = 7.4653, address = null,
            description = description(entry("General", "Type", "restaurant"))
        )
        assertTrue("attribute rows must not be clickable", rows.all { it.onClickDelegate == null })
        // The action rows are clickable by contrast.
        assertTrue(buildNavigateRow(testCarContext()) {}.onClickDelegate != null)
        assertTrue(buildShowRow(testCarContext()) {}.onClickDelegate != null)
    }

    @Test
    fun saveFavoriteRowInvokesCallback() {
        var invoked = false
        val row = buildSaveFavoriteRow(testCarContext()) { invoked = true }
        assertEquals("★ Add to Favorites", row.title.toString())
        assertTrue("row must be clickable", row.onClickDelegate != null)
        row.onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        assertTrue(invoked)
    }

    @Test
    fun removeFavoriteRowInvokesCallback() {
        var invoked = false
        val row = buildRemoveFavoriteRow(testCarContext()) { invoked = true }
        assertEquals("☆ Remove from Favorites", row.title.toString())
        assertTrue("row must be clickable", row.onClickDelegate != null)
        row.onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        assertTrue(invoked)
    }

    @Test
    fun titleShowsObjectName() {
        val title = resolveTitle(
            address = arrayOf("Kleppingstr.", "22", "Dortmund", "44139"),
            description = description(entry("General", "Name", "Mario's")),
            genericTitle = GENERIC_TITLE,
            nameHint = "Mario's"
        )
        assertEquals("Mario's", title)
    }

    @Test
    fun titleShowsCallerNameBeforeAddress() {
        // POI/search results carry the name in the label: with no description
        // name, the caller-provided name wins over the address (bug fix).
        val title = resolveTitle(
            address = arrayOf("Kleppingstr.", "22", "Dortmund", "44139"),
            description = null,
            genericTitle = GENERIC_TITLE,
            nameHint = "Mario's"
        )
        assertEquals("Mario's", title)
    }

    @Test
    fun titleFallsBackToAddressWithoutCallerName() {
        val title = resolveTitle(
            address = arrayOf("Kleppingstr.", "22", "Dortmund", "44139"),
            description = null,
            genericTitle = GENERIC_TITLE,
            nameHint = null
        )
        assertEquals("Kleppingstr. 22, 44139 Dortmund", title)
    }

    @Test
    fun titleFallsBackToLabelWhenNoAddress() {
        // No street/house number → the label wins over the region (phone lead).
        val title = resolveTitle(
            address = arrayOf("", "", "Dortmund", "44139"),
            description = null,
            genericTitle = GENERIC_TITLE,
            nameHint = "Some label"
        )
        assertEquals("Some label", title)
    }

    @Test
    fun titleFallsBackToGenericWhenNoCallerNameAndNoAddress() {
        // No name, no address, no label → the caller's generic title (the
        // resolver owns no display word; spec: i18n-l10n — shared module owns
        // no wording).
        val title = resolveTitle(
            address = arrayOf("", "", "Dortmund", "44139"),
            description = null,
            genericTitle = GENERIC_TITLE,
            nameHint = null
        )
        assertEquals(GENERIC_TITLE, title)
    }

    @Test
    fun titleFallsBackToAddressLikeLabel() {
        // Digit-bearing label is an address; title uses it (phone lead).
        val title = resolveTitle(
            address = null,
            description = null,
            genericTitle = GENERIC_TITLE,
            nameHint = "Hauptstraße 12"
        )
        assertEquals("Hauptstraße 12", title)
    }

    @Test
    fun titleFallsBackToLabel() {
        val title = resolveTitle(
            address = null,
            description = null,
            genericTitle = GENERIC_TITLE,
            nameHint = "Mario's"
        )
        assertEquals("Mario's", title)
    }

    @Test
    fun titleFallsBackToGeneric() {
        val title = resolveTitle(
            address = null,
            description = null,
            genericTitle = GENERIC_TITLE,
            nameHint = null
        )
        assertEquals(GENERIC_TITLE, title)
        // The German form is what a German head unit shows (spec: i18n-l10n —
        // German car favorites titles / generic title parity).
        assertEquals(
            "Standort",
            resolveTitle(address = null, description = null, genericTitle = "Standort", nameHint = null)
        )
    }

    @Test
    fun destinationNameFallsBackToNull() {
        assertNull(resolveDestinationName(address = null, nameHint = null))
        assertEquals("Mario's", resolveDestinationName(address = null, nameHint = "Mario's"))
        assertEquals(
            "Kleppingstr. 22, 44139 Dortmund",
            resolveDestinationName(arrayOf("Kleppingstr.", "22", "Dortmund", "44139"))
        )
    }
}
