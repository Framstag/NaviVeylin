package com.naviveylin.auto

import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ListTemplate
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Smoke tests for [MapTemplateFactory] (spec: auto-map-layout — the content
 * box is the app menu with header; the right strip carries search, settings
 * and zoom; no map action strip).
 */
@RunWith(RobolectricTestRunner::class)
class MapTemplateFactoryTest {

    private val noop: () -> Unit = {}

    private fun menuContent(): ListTemplate = MapTemplateFactory.buildMenuContent(testCarContext(), noop, noop, noop, noop, noop, noop, noop)

    private fun degradedMenuContent(): ListTemplate =
        MapTemplateFactory.buildMenuContent(testCarContext(), noop, noop, noop, noop, noop, noop, noop, mapUnavailable = true)

    private fun controller(): MapController = MapController.Builder().build()

    @Test
    fun templateHasController() {
        val template = MapTemplateFactory.buildTemplate(controller(), menuContent())
        assertTrue(template.mapController != null)
    }

    @Test
    fun menuHasHeaderWithAppIconAndTitle() {
        val content = menuContent()
        assertEquals("NaviVeylin", content.header!!.title.toString())
        // APP_ICON renders the app icon as the start header action.
        assertEquals(Action.APP_ICON, content.header!!.startHeaderAction)
    }

    @Test
    fun menuHasSevenClickableRowsInOrder() {
        val content = menuContent()
        val rows = content.singleList!!.items
        assertEquals(7, rows.size)
        val titles = rows.map { (it as androidx.car.app.model.Row).title.toString() }
        assertEquals(
            listOf(
                "Free driving", "Starred favorites", "All favorites",
                "Search for POIs", "Search history", "Diagnostics", "About"
            ),
            titles
        )
        assertTrue(rows.all { (it as androidx.car.app.model.Row).onClickDelegate != null })
    }

    @Test
    fun theDegradedNoticeIsADisabledFirstRow() {
        // Spec: car-host-fault-isolation — A repeatedly faulting car renderer recovers, then
        // degrades visibly: when the map cannot be drawn (and the surface therefore cannot be
        // trusted to carry the message), the host-drawn content list states it. Not clickable —
        // it is a state statement, not an action.
        val rows = degradedMenuContent().singleList!!.items.map { it as androidx.car.app.model.Row }

        assertEquals(
            "the notice leads the list",
            testCarContext().getString(R.string.map_unavailable),
            rows.first().title.toString()
        )
        assertEquals("not clickable", null, rows.first().onClickDelegate)
        assertEquals("the normal rows stay", 8, rows.size)
    }

    @Test
    fun aHealthyMapCarriesNoNotice() {
        val titles = menuContent().singleList!!.items
            .map { (it as androidx.car.app.model.Row).title.toString() }

        assertTrue(titles.none { it == testCarContext().getString(R.string.map_unavailable) })
    }

    @Test
    fun noMapActionStrip() {
        val controller = controller()
        assertEquals(null, controller.mapActionStrip)
    }

    @Test
    fun templateCarriesSearchSettingsZoom() {
        val strip = ActionStrip.Builder()
            .addAction(MapStripActions.searchAction {})
            .addAction(MapStripActions.settingsAction {})
            .addAction(MapStripActions.zoomInAction {})
            .addAction(MapStripActions.zoomOutAction {})
            .build()
        val template = MapTemplateFactory.buildTemplate(controller(), menuContent(), strip)
        assertEquals(4, template.actionStrip!!.actions.size)
    }

    @Test
    fun fullScreenTemplateHasNoContentSlot() {
        // Spec: auto/free-driving — the free-driving view fills the surface
        // with no host-rendered content box. NavigationTemplate is the only
        // template without a content slot (MapWithContentTemplate showed a
        // "Free driving" box, MapTemplate a "No items" placeholder).
        // NavigationTemplate's mapActionStrip forbids custom-titled actions,
        // so the map strip carries the icon-only exit action and the right
        // action strip the titled zoom controls.
        val mapStrip = ActionStrip.Builder()
            .addAction(Action.Builder().setIcon(CarGlyphs.exit).build())
            .build()
        val strip = ActionStrip.Builder()
            .addAction(Action.Builder().setTitle("+").build())
            .addAction(Action.Builder().setTitle("-").build())
            .build()
        val template = MapTemplateFactory.buildFullScreenTemplate(mapStrip, strip)
        assertEquals(1, template.mapActionStrip!!.actions.size)
        assertEquals(2, template.actionStrip!!.actions.size)
        // Spec: "No travel estimate shown" / "No turn-by-turn guidance
        // shown" — free driving has no destination, so the template must not
        // carry a travel estimate or navigation info.
        assertEquals(null, template.destinationTravelEstimate)
        assertEquals(null, template.navigationInfo)
    }

    @Test
    fun freeDrivingRowInvokesCallback() {
        // Spec: auto/free-driving — "Free driving entered from map menu". The
        // row's click must reach the MapScreen handler that pushes the
        // free-driving screen (the push itself needs a live CarContext, same
        // constraint as MapScreenTest; the wiring seam is this callback).
        var clicked = false
        val content = MapTemplateFactory.buildMenuContent(
            testCarContext(),
            onFreeDriving = { clicked = true },
            onStarredFavorites = noop,
            onAllFavorites = noop,
            onPoiSearch = noop,
            onSearchHistory = noop,
            onDiagnostics = noop,
            onAbout = noop
        )
        val rows = content.singleList!!.items
        val freeDrivingRow = rows[0] as androidx.car.app.model.Row
        assertEquals("Free driving", freeDrivingRow.title.toString())
        freeDrivingRow.onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        assertTrue("Free driving row must invoke its callback", clicked)
    }
}
