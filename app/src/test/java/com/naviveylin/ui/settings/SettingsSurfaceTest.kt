package com.naviveylin.ui.settings

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.R
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.data.AppSettings
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.DarkModeController
import com.naviveylin.data.FavoriteRepository
import com.naviveylin.data.SearchHistoryRepository
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.ViewportStorage
import com.naviveylin.data.routingProfileFor
import com.naviveylin.location.LocationService
import com.naviveylin.navigation.NavigationViewModel
import com.naviveylin.share.SharedLocationHandler
import com.naviveylin.test.ComposeSandboxRule
import com.naviveylin.test.MainDispatcherRule
import com.naviveylin.test.engineUnderTest
import com.naviveylin.ui.MainScreen
import com.naviveylin.ui.map.LocationOptionsOverlay
import com.naviveylin.ui.map.MapCanvasViewModel
import com.naviveylin.ui.map.MapMenu
import com.naviveylin.ui.map.MapMode
import com.naviveylin.ui.map.enterFreeDrive
import com.naviveylin.ui.map.openRoutePanelWithStart
import com.naviveylin.ui.map.setNavigationViewModel
import com.naviveylin.ui.map.setRoutePanelViewModel
import com.naviveylin.ui.route.RouteOverlayAnchor
import com.naviveylin.ui.route.RoutePanelViewModel
import com.naviveylin.ui.route.RouteSessionState
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The settings surface as the phone draws it (spec: `settings-surface`, spec: `settings-taxonomy`).
 *
 * Two fixtures stand behind these cases and the difference between them is the point:
 *
 * - the **registry** (`SettingsRegistry.root`) is the real root the app renders — six topics, and the
 *   rows that have landed in them: Navigation & Route carries its routing values (tasks.md group 4),
 *   the other topics are still empty (groups 3, 5-8);
 * - a **fixture table** feeds the topic page, because a page's sections, help lines, scope marks and
 *   link rows have to be asserted before the rows that carry them land. `SettingsRegistryTest` reads
 *   its rules the same way, and neither fixture row is ever declared in the registry.
 *
 * The frame verdict over a rendered map — does the surface leave the renderer drawing — is the
 * device step (`pixel-check`); what is asserted here is what a host test can see: the map band keeps
 * its size and the map's own state (mode, follow, route session) is untouched by the surface
 * (`MapLayerStackComposeTest` splits the same way).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsSurfaceTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var controller: SettingsController
    private var mapManagerOpened = 0
    private var aboutOpened = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        RuntimeEnvironment.setQualifiers("w411dp-h891dp")
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        File(context.filesDir, "maps/search_history.json").delete()
        controller = SettingsController(SettingsStorage(context))
        mapManagerOpened = 0
        aboutOpened = 0
    }

    // ---- the root and a topic page (tasks.md 2.1) ----

    /**
     * The root is the six topics and nothing else (spec: `settings-taxonomy` — "Root lists the six
     * topics"; tasks.md 2.1 `theRootShowsSixTopicsAndNoSettingRow`).
     */
    @Test
    fun theRootShowsSixTopicsAndNoSettingRow() {
        launchSurface()
        composeRule.onNodeWithTag(SETTINGS_SURFACE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_ROOT_TAG).assertIsDisplayed()
        for (topic in SettingsTopic.values()) {
            composeRule.onNodeWithText(context.getString(topic.titleRes)).assertIsDisplayed()
        }
        // No setting row is on the root: a row would be a control, and the root holds none.
        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true).assertCountEquals(0)
    }

    /**
     * A topic page is one column of rows under section titles, and a section title is not a control
     * (spec: `settings-surface` — "A topic page groups its rows under section titles"; tasks.md 2.1
     * `aTopicPageShowsSections`).
     */
    @Test
    fun aTopicPageShowsSections() {
        val presentation = SettingsSection(SettingsTopic.MAP, android.R.string.ok)
        val handling = SettingsSection(SettingsTopic.MAP, android.R.string.cancel)
        launchSurface(
            root = listOf(
                SettingsTopicTable(
                    SettingsTopic.MAP,
                    listOf(
                        row(key = "style", section = presentation, labelRes = R.string.settings_scope_phone),
                        row(key = "poi", section = presentation, labelRes = R.string.settings_scope_car),
                        row(key = "long-press", section = handling, labelRes = R.string.settings_open_surface)
                    )
                )
            ),
            topic = SettingsTopic.MAP
        )

        composeRule.onNodeWithTag(SETTINGS_TOPIC_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(label(android.R.string.ok)).assertIsDisplayed().assertHasNoClickAction()
        composeRule.onNodeWithText(label(android.R.string.cancel)).assertIsDisplayed().assertHasNoClickAction()
        composeRule.onNodeWithText(label(R.string.settings_scope_phone)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.settings_scope_car)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.settings_open_surface)).assertIsDisplayed()
        // The title sits above the rows it owns, not after them.
        val title = composeRule.onNodeWithText(label(android.R.string.ok)).getBoundsInRoot()
        val firstRow = composeRule.onNodeWithText(label(R.string.settings_scope_phone)).getBoundsInRoot()
        assertTrue(
            "the section title must sit above its first row, title=$title row=$firstRow",
            title.bottom <= firstRow.top
        )
    }

    /**
     * A row's short help line renders beneath its label in the supporting style, and a row without
     * help text stays single-line (spec: `settings-taxonomy` — "A short help line sits under the
     * label"; tasks.md 2.1 `aShortHelpLineSitsUnderTheLabel`).
     */
    @Test
    fun aShortHelpLineSitsUnderTheLabel() {
        val section = SettingsSection(SettingsTopic.MAP, android.R.string.ok)
        launchSurface(
            root = listOf(
                SettingsTopicTable(
                    SettingsTopic.MAP,
                    listOf(
                        row(
                            key = "with-help",
                            section = section,
                            labelRes = android.R.string.copy,
                            helpShortRes = android.R.string.cancel
                        ),
                        row(key = "without-help", section = section, labelRes = android.R.string.yes)
                    )
                )
            ),
            topic = SettingsTopic.MAP
        )

        composeRule.onNodeWithText("Copy").assertIsDisplayed()
        val label = composeRule.onNodeWithText("Copy").getBoundsInRoot()
        val help = composeRule.onNodeWithText("Cancel").getBoundsInRoot()
        assertTrue(
            "the help line must render beneath its label, label=$label help=$help",
            label.bottom <= help.top
        )
        // The row that declares no help text carries none: one help line, not two.
        composeRule.onAllNodes(hasText("Cancel")).assertCountEquals(1)
    }

    /**
     * A surface-specific row carries a surface icon whose content description names the surface; a
     * shared row carries none (spec: `settings-taxonomy` — "A car-only row is marked for the car" and
     * "A shared row carries no mark"; tasks.md 2.1 `aSharedRowCarriesNoSurfaceIcon`).
     */
    @Test
    fun aSharedRowCarriesNoSurfaceIcon() {
        val section = SettingsSection(SettingsTopic.HINTS_AND_LANGUAGE, android.R.string.ok)
        launchSurface(
            root = listOf(
                SettingsTopicTable(
                    SettingsTopic.HINTS_AND_LANGUAGE,
                    listOf(
                        row(key = "shared", section = section, labelRes = android.R.string.copy),
                        row(
                            key = "phone-only",
                            section = section,
                            labelRes = android.R.string.yes,
                            scope = SettingScope.PHONE,
                            surfaceContentDescriptionRes = R.string.settings_scope_phone
                        ),
                        row(
                            key = "car-only",
                            section = section,
                            labelRes = android.R.string.no,
                            scope = SettingScope.CAR,
                            surfaceContentDescriptionRes = R.string.settings_scope_car
                        )
                    )
                )
            ),
            topic = SettingsTopic.HINTS_AND_LANGUAGE
        )

        composeRule.onNodeWithContentDescription("Phone only").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Android Auto").assertIsDisplayed()
        // Exactly the two surface-specific rows carry a mark; the shared row carries none.
        composeRule.onAllNodes(
            hasContentDescription("Phone only") or hasContentDescription("Android Auto"),
            useUnmergedTree = true
        ).assertCountEquals(2)
    }

    /**
     * A section whose rows do not apply to the current document is omitted, not greyed (spec:
     * `settings-surface` — "An inapplicable section is omitted"; tasks.md 2.5
     * `anInapplicableSectionIsOmitted`).
     */
    @Test
    fun anInapplicableSectionIsOmitted() {
        val always = SettingsSection(SettingsTopic.MAP, android.R.string.ok)
        val conditional = SettingsSection(SettingsTopic.MAP, android.R.string.cancel)
        launchSurface(
            root = listOf(
                SettingsTopicTable(
                    SettingsTopic.MAP,
                    listOf(
                        row(key = "always", section = always, labelRes = R.string.settings_scope_phone),
                        row(
                            key = "conditional",
                            section = conditional,
                            labelRes = R.string.settings_scope_car,
                            enabled = { false }
                        )
                    )
                )
            ),
            topic = SettingsTopic.MAP
        )

        composeRule.onNodeWithText(label(android.R.string.ok)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.settings_scope_phone)).assertIsDisplayed()
        // The section whose only row does not apply is omitted with it.
        composeRule.onNodeWithText(label(android.R.string.cancel)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.settings_scope_car)).assertDoesNotExist()
    }

    /**
     * A link row leaves the surface instead of editing a value (spec: `settings-taxonomy` — "A link
     * row leaves the surface"; tasks.md 2.5 `theMapManagerRowOpensTheMapManager`).
     */
    @Test
    fun theMapManagerRowOpensTheMapManager() {
        launchSurface(
            root = listOf(
                SettingsTopicTable(
                    SettingsTopic.MAPS_AND_DATA,
                    listOf(
                        row(
                            key = "map-manager",
                            section = SettingsSection(SettingsTopic.MAPS_AND_DATA, android.R.string.ok),
                            labelRes = android.R.string.copy,
                            control = SettingControl.LINK,
                            // A link row carries its destination rather than a write, so the row
                            // leaves the surface for the screen its payload names.
                            payload = SettingControlPayload.Open(SettingsLinkTarget.MAP_MANAGER),
                            write = null
                        )
                    )
                )
            ),
            topic = SettingsTopic.MAPS_AND_DATA
        )

        composeRule.onNodeWithText("Copy").performClick()

        assertEquals("the link row must open the map manager", 1, mapManagerOpened)
        composeRule.onNodeWithTag(SETTINGS_SURFACE_TAG).assertIsDisplayed()
    }

    /**
     * The About row opens the dialog the app already owns (spec: `settings-surface`; tasks.md 2.5
     * `theAboutRowOpensTheDialog`).
     */
    @Test
    fun theAboutRowOpensTheDialog() {
        launchSurface(
            root = listOf(
                SettingsTopicTable(
                    SettingsTopic.ABOUT,
                    listOf(
                        row(
                            key = "about",
                            section = SettingsSection(SettingsTopic.ABOUT, android.R.string.ok),
                            labelRes = android.R.string.copy,
                            control = SettingControl.LINK,
                            payload = SettingControlPayload.Open(SettingsLinkTarget.ABOUT),
                            write = null
                        )
                    )
                )
            ),
            topic = SettingsTopic.ABOUT
        )

        composeRule.onNodeWithText("Copy").performClick()

        assertEquals("the About row must open the existing dialog", 1, aboutOpened)
    }

    // ---- the three entry points (tasks.md 2.3) ----

    /**
     * The map menu's Settings entry opens the surface at its root, above the map (spec:
     * `settings-surface` — "The map menu opens the surface"; spec: `map-menu` — "Settings entry";
     * tasks.md 2.3 `theMapMenuOpensTheRoot`).
     */
    @Test
    fun theMapMenuOpensTheRoot() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                MapMenu(
                    expanded = true,
                    onDismiss = {},
                    onDownloadMaps = {},
                    onOpenFavorites = {},
                    onOpenSearch = {},
                    onOpenSettings = { controller.openRoot() },
                    onOpenAbout = {},
                    toasterTopPadding = 4.dp
                )
                SettingsSurfaceHost(
                    controller = controller,
                    onOpenMapManager = { mapManagerOpened++ },
                    onOpenAbout = { aboutOpened++ }
                )
            }
        }

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(SettingsTopic.MAP.titleRes)).assertIsDisplayed()
    }

    /**
     * The start screen's menu opens the same surface at its root, so the no-map screen has the
     * settings path it never had (spec: `settings-surface` — "The start screen menu opens the
     * surface"; tasks.md 2.3 `theStartScreenMenuOpensTheRoot`).
     */
    @Test
    fun theStartScreenMenuOpensTheRoot() {
        composeRule.setContent {
            MainScreen(
                onNavigateToMapManager = {},
                settingsViewModel = SettingsViewModel(controller)
            )
        }

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(SettingsTopic.MAP.titleRes)).assertIsDisplayed()
    }

    /**
     * The quick sheet's last row closes the sheet and opens the surface, so the two are never open at
     * once (spec: `settings-surface` — "The sheet's entry row opens the surface"; tasks.md 2.3
     * `theSheetRowClosesTheSheetAndOpensTheSurface`).
     */
    @Test
    fun theSheetRowClosesTheSheetAndOpensTheSurface() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                LocationOptionsOverlay(
                    mode = MapMode.BROWSE,
                    // The required rows keep the sheet's signature honest; this case needs none of
                    // them, only the entry row it puts last in every map state.
                    longPressTimeoutMs = 500,
                    onSetLongPressTimeoutMs = {},
                    onOpenSettings = { controller.openRoot() }
                )
                SettingsSurfaceHost(
                    controller = controller,
                    onOpenMapManager = { mapManagerOpened++ },
                    onOpenAbout = { aboutOpened++ }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Location options").performClick()
        composeRule.waitForIdle()
        // The sheet scrolls: its last row is reached the way a user reaches it.
        composeRule.onNodeWithText("All settings").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_ROOT_TAG).assertIsDisplayed()
        // The sheet is gone: its last row is not on screen any more.
        composeRule.onNodeWithText("All settings").assertDoesNotExist()
    }

    // ---- the map behind the surface (tasks.md 2.2) ----

    private lateinit var viewModel: MapCanvasViewModel
    private lateinit var routePanelViewModel: RoutePanelViewModel

    /**
     * The surface is an overlay in the map screen's modal band: the map band keeps its size, follow
     * mode stays engaged and a calculated route survives the surface (spec: `settings-surface` — "The
     * map keeps running behind the surface"; tasks.md 2.2
     * `theMapKeepsRenderingWhileTheSurfaceIsOpen`).
     */
    @Test
    fun theMapKeepsRenderingWhileTheSurfaceIsOpen() {
        val client = setUpMap()
        openSessionOnRoute()
        viewModel.enterFreeDrive()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        var mapSize = IntSize.Zero
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag("map-frame")
                        .onSizeChanged { mapSize = it }
                )
                SettingsSurfaceHost(
                    controller = viewModel.settingsController,
                    onOpenMapManager = { mapManagerOpened++ },
                    onOpenAbout = { aboutOpened++ }
                )
            }
        }
        composeRule.waitForIdle()
        val sizeBefore = mapSize
        assertTrue("the map band measures before the surface opens", sizeBefore.height > 0)

        composeRule.runOnIdle { viewModel.settingsController.openRoot() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_SURFACE_TAG).assertIsDisplayed()
        // It fills the screen height, not a band of it: the surface's bounds are the root's
        // (spec: `settings-surface` — "The surface covers the full height").
        val surfaceHeight = composeRule.onNodeWithTag(SETTINGS_SURFACE_TAG).getBoundsInRoot().height
        val rootHeight = composeRule.onRoot().getBoundsInRoot().height
        assertEquals(
            "the surface fills the screen height",
            rootHeight.value,
            surfaceHeight.value,
            0.5f
        )
        assertEquals("the map band keeps its size while the surface is open", sizeBefore, mapSize)
        assertTrue(
            "the map's own state is untouched: follow mode stays engaged",
            viewModel.uiState.value.followMode
        )
        assertEquals(
            "the route session is still in progress while the surface is open",
            RouteSessionState.REVIEWING,
            routePanelViewModel.sessionState.value
        )
        assertEquals(
            "the surface reads the same client the map renders with",
            client,
            viewModel.osmscoutClient
        )
    }

    /**
     * Dismissing the surface returns the map it was opened over (spec: `settings-surface` —
     * "Dismissing returns the untouched map"; tasks.md 2.2 `dismissingReturnsTheUntouchedMap`).
     */
    @Test
    fun dismissingReturnsTheUntouchedMap() {
        setUpMap()
        viewModel.enterFreeDrive()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().testTag("map-frame"))
                SettingsSurfaceHost(
                    controller = viewModel.settingsController,
                    onOpenMapManager = { mapManagerOpened++ },
                    onOpenAbout = { aboutOpened++ }
                )
            }
        }
        composeRule.waitForIdle()
        val modeBefore = viewModel.mode
        val followBefore = viewModel.uiState.value.followMode
        val viewportBefore = viewModel.uiState.value.viewport
        assertTrue("free driving follows the vehicle", followBefore)

        composeRule.runOnIdle { viewModel.settingsController.openRoot() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SETTINGS_SURFACE_TAG).assertIsDisplayed()

        composeRule.runOnIdle { viewModel.settingsController.close() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_SURFACE_TAG).assertDoesNotExist()
        assertEquals("the mode is the one the surface was opened over", modeBefore, viewModel.mode)
        assertEquals(
            "follow mode is the one the surface was opened over",
            followBefore,
            viewModel.uiState.value.followMode
        )
        assertEquals(
            "the camera is the one the surface was opened over",
            viewportBefore,
            viewModel.uiState.value.viewport
        )
    }

    // ---- the Navigation & Route topic's rows (tasks.md group 4, slice A) ----

    /**
     * The Navigation & Route page carries the routing values, each as its own row: the three maximum
     * speeds, the three avoidance switches and the phone's two vehicle anchors (spec:
     * `settings-surface` — "Navigation & Route carries routing values").
     */
    @Test
    fun theNavigationTopicCarriesTheRoutingValues() {
        launchSurface(topic = SettingsTopic.NAVIGATION_AND_ROUTE)

        for (key in listOf(
            "car-max-speed",
            "bicycle-max-speed",
            "foot-max-speed",
            "avoid-tolls",
            "avoid-ferries",
            "avoid-unpaved",
            "routing-anchor",
            "free-driving-anchor"
        )) {
            composeRule.onNodeWithTag(SETTINGS_ROW_TAG_PREFIX + key)
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    /**
     * The three maximum-speed sliders are rows of the Navigation & Route topic, and each still covers
     * its vehicle's whole range: the range is read off the control, so a range that moved shows up as
     * a slider that cannot reach it (spec: `routing-max-speed` — A per-vehicle maximum speed is a
     * persisted setting; tasks.md 4.1 `theThreeSpeedSlidersKeepTheirRanges`).
     */
    @Test
    fun theThreeSpeedSlidersKeepTheirRanges() {
        launchSurface(topic = SettingsTopic.NAVIGATION_AND_ROUTE)

        // Current values are the settings' own defaults, which is what the surface renders before a
        // write: the row shows the stored number, and the slider offers the range around it.
        assertSliderRange("car-max-speed", current = 120, min = 30, max = 160)
        assertSliderRange("bicycle-max-speed", current = 15, min = 5, max = 40)
        assertSliderRange("foot-max-speed", current = 5, min = 2, max = 15)
    }

    /**
     * A speed moved on the surface reaches the settings file, which is what a restart reads — the row
     * writes through the one writer rather than into a copy the file never sees (spec:
     * `routing-max-speed`; tasks.md 4.1 `aSliderChangePersistsAndSurvivesRestart`).
     */
    @Test
    fun aSliderChangePersistsAndSurvivesRestart() {
        launchSurface(topic = SettingsTopic.NAVIGATION_AND_ROUTE)

        composeRule.onNodeWithTag(SETTINGS_SLIDER_TAG_PREFIX + "car-max-speed")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
        // The row's write is a storage transaction off the UI thread: wait for the document it publishes.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            controller.settings.value.carMaxSpeedKmh == 90
        }

        assertEquals(
            "the row writes the value the user moved to",
            90,
            controller.settings.value.carMaxSpeedKmh
        )
        // A restart is a fresh read of the file, not the running flow's copy.
        val persisted = runBlocking { SettingsStorage(context).load() }
        assertEquals("the moved speed is in the settings file", 90, persisted.carMaxSpeedKmh)
        assertEquals("the other vehicles' speeds are untouched", 15, persisted.bicycleMaxSpeedKmh)
    }

    /**
     * The three avoidance switches stand on the same topic page as the speeds, after them, and still
     * feed the one routing-profile builder every calculation reads (spec: `routing-avoidance` — One
     * builder produces the profile, and the phone edits the choices; tasks.md 4.2
     * `theAvoidanceSwitchesSitBesideTheSpeeds`).
     */
    @Test
    fun theAvoidanceSwitchesSitBesideTheSpeeds() {
        launchSurface(topic = SettingsTopic.NAVIGATION_AND_ROUTE)

        composeRule.onNodeWithText(label(R.string.max_speed_section)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.avoidance_section)).performScrollTo().assertIsDisplayed()

        // One page, one order: the three speed rows come first, the three avoidance rows under them
        // (read off the page's own child order, so a section moved elsewhere fails here).
        val order = pageOrder()
        val lastSpeedRow = order.indexOf(SETTINGS_ROW_TAG_PREFIX + "foot-max-speed")
        val firstAvoidanceRow = order.indexOf(SETTINGS_ROW_TAG_PREFIX + "avoid-tolls")
        val lastAvoidanceRow = order.indexOf(SETTINGS_ROW_TAG_PREFIX + "avoid-unpaved")
        assertTrue(
            "the avoidance rows must follow the speed rows on the same page, order=$order",
            lastSpeedRow >= 0 && firstAvoidanceRow > lastSpeedRow && lastAvoidanceRow > firstAvoidanceRow
        )

        // A switch shows the stored value, toggles it, and the shared builder carries the result.
        switchOf("avoid-ferries").assertIsOff().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { controller.settings.value.avoidFerries }

        val stored = controller.settings.value
        assertEquals("the toggle reached the settings document", true, stored.avoidFerries)
        assertEquals(
            "the shared builder carries the choice every calculation reads",
            true,
            stored.routingProfileFor(Vehicle.CAR).avoidFerries
        )
    }

    /**
     * Each phone anchor row opens the 5×3 picker with the configured preset marked, and choosing a cell
     * writes that preset and returns to the topic (spec: `settings-surface` — "A finite value set is
     * presented as options"; tasks.md 4.3 `theAnchorPickerMarksTheConfiguredValue`).
     */
    @Test
    fun theAnchorPickerMarksTheConfiguredValue() {
        launchSurface(topic = SettingsTopic.NAVIGATION_AND_ROUTE)

        composeRule.onNodeWithTag(SETTINGS_ROW_TAG_PREFIX + "routing-anchor")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_OPTIONS_TAG).assertIsDisplayed()
        // The stored preset is the marked one, not the first cell: a picker that marked the default
        // would fail here.
        anchorCell("routing-anchor", VehicleAnchorPosition.CENTER).assertIsSelected()
        anchorCell("routing-anchor", VehicleAnchorPosition.BOTTOM_RIGHT).assertIsNotSelected()

        anchorCell("routing-anchor", VehicleAnchorPosition.BOTTOM_RIGHT).performClick()
        composeRule.waitForIdle()

        assertEquals(
            "the picked preset is the one persisted",
            VehicleAnchorPosition.BOTTOM_RIGHT.id,
            controller.settings.value.routingAnchorId
        )
        // Choosing returns to the topic the row lives in: the picker is not a place to be stranded in.
        composeRule.onNodeWithTag(SETTINGS_TOPIC_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_ROW_TAG_PREFIX + "routing-anchor").assertIsDisplayed()
    }

    /**
     * The picker's cell texts are the shared enum's labels — the ones the car's own picker draws — so
     * the two surfaces name the same preset the same way (label parity; tasks.md 4.3
     * `theAnchorLabelsMatchTheCarPicker`).
     */
    @Test
    fun theAnchorLabelsMatchTheCarPicker() {
        launchSurface(topic = SettingsTopic.NAVIGATION_AND_ROUTE)

        composeRule.onNodeWithTag(SETTINGS_ROW_TAG_PREFIX + "free-driving-anchor")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // One cell per preset, each carrying `VehicleAnchorPosition.label` — the literal
        // `VehicleAnchorPickerScreen` in the car draws for the same preset.
        for (anchor in VehicleAnchorPosition.entries) {
            val cell = composeRule.onNodeWithTag(
                SETTINGS_CHOICE_TAG_PREFIX + "free-driving-anchor-" + anchor.id,
                useUnmergedTree = true
            )
            cell.assertIsDisplayed()
            cell.onChildren().filterToOne(hasText(anchor.label)).assertIsDisplayed()
        }
    }

    /**
     * One slider row's whole range, read off the control: current value, endpoints and the steps
     * between them are one reading, so the row cannot show a range its slider does not offer.
     */
    private fun assertSliderRange(key: String, current: Int, min: Int, max: Int) {
        composeRule.onNodeWithTag(SETTINGS_SLIDER_TAG_PREFIX + key)
            .performScrollTo()
            .assertRangeInfoEquals(
                ProgressBarRangeInfo(
                    current = current.toFloat(),
                    range = min.toFloat()..max.toFloat(),
                    steps = max - min - 1
                )
            )
    }

    /** The one page's children's test tags, in the order the page lays them out. */
    private fun pageOrder(): List<String> =
        composeRule.onNodeWithTag(SETTINGS_TOPIC_TAG, useUnmergedTree = true)
            .fetchSemanticsNode().children
            .map { child ->
                if (SemanticsProperties.TestTag in child.config) {
                    child.config[SemanticsProperties.TestTag]
                } else {
                    ""
                }
            }

    /** The switch inside one row, reached from the row's own handle. */
    private fun switchOf(key: String) = composeRule.onNode(
        isToggleable() and hasAnyAncestor(hasTestTag(SETTINGS_ROW_TAG_PREFIX + key)),
        useUnmergedTree = true
    )

    /** One cell of one row's option page, found by the row and the preset it offers. */
    private fun anchorCell(rowKey: String, anchor: VehicleAnchorPosition) =
        composeRule.onNodeWithTag(SETTINGS_CHOICE_TAG_PREFIX + rowKey + "-" + anchor.id)

    // ---- fixtures ----

    private fun launchSurface(
        root: List<SettingsTopicTable> = SettingsRegistry.root,
        topic: SettingsTopic? = null
    ) {
        composeRule.setContent {
            SettingsSurfaceHost(
                controller = controller,
                onOpenMapManager = { mapManagerOpened++ },
                onOpenAbout = { aboutOpened++ },
                root = root
            )
        }
        composeRule.runOnIdle {
            if (topic == null) controller.openRoot() else controller.openTopic(topic)
        }
        composeRule.waitForIdle()
    }

    /** A resource-backed label, resolved the way the surface resolves it. */
    private fun label(res: Int): String = context.getString(res)

    private fun row(
        key: String,
        section: SettingsSection,
        labelRes: Int = android.R.string.copy,
        scope: SettingScope = SettingScope.SHARED,
        control: SettingControl = SettingControl.SWITCH,
        helpShortRes: Int? = null,
        surfaceContentDescriptionRes: Int? = null,
        payload: SettingControlPayload? = null,
        enabled: (AppSettings) -> Boolean = { true },
        write: ((AppSettings, String) -> AppSettings)? =
            { settings, value -> settings.copy(keepScreenOn = value == "on") }
    ): SettingSpec = SettingSpec(
        key = key,
        topic = section.topic,
        section = section,
        scope = scope,
        labelRes = labelRes,
        helpShortRes = helpShortRes,
        control = control,
        payload = payload,
        surfaceContentDescriptionRes = surfaceContentDescriptionRes,
        enabled = enabled,
        read = { settings: AppSettings -> if (settings.keepScreenOn) "on" else "off" },
        write = write
    )

    /** The map screen's own dependencies, as `MapLayerStackComposeTest` builds them. */
    private fun setUpMap(): FakeOSMScoutClient {
        val client = FakeOSMScoutClient()
        val locationService = LocationService(context)
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsController = SettingsController(SettingsStorage(context)),
            assetCopier = AssetCopier(context),
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context)
                .also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
            locationService = locationService,
            darkModeController = DarkModeController(SettingsStorage(context)),
            sharedLocationHandler = SharedLocationHandler(),
            basemapReloadNotifier = BasemapReloadNotifier(),
            context = context
        )
        viewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        routePanelViewModel = RoutePanelViewModel(
            client = client,
            favoriteRepository = FavoriteRepository(client),
            searchHistoryRepository = SearchHistoryRepository(context)
                .also { it.defaultDispatcher = mainDispatcherRule.dispatcher },
            locationService = locationService,
            context = context,
            settingsStorage = SettingsStorage(context)
        )
        routePanelViewModel.defaultDispatcher = mainDispatcherRule.dispatcher
        viewModel.setNavigationViewModel(
            NavigationViewModel(engineUnderTest({ client }, LocationService(context), context))
        )
        viewModel.setRoutePanelViewModel(routePanelViewModel)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        return client
    }

    private fun entry(label: String): LocationEntry = LocationEntry().apply {
        this.label = label
        lat = 51.5136
        lon = 7.4653
        matchQuality = "coordinate"
    }

    private fun route(): RouteEntry = RouteEntry().apply {
        distance = 12_400.0
        duration = 1_500.0
        latitudes = doubleArrayOf(51.5136, 51.5226)
        longitudes = doubleArrayOf(7.4653, 7.4653)
    }

    /** Opens the session in REVIEWING on a two-point route, as `RouteSessionCardExitTest` does. */
    private fun openSessionOnRoute() {
        routePanelViewModel.setStartLocation(entry("Start"))
        routePanelViewModel.setDestLocation(entry("Destination"))
        routePanelViewModel.adoptRoute(route(), Vehicle.CAR)
        viewModel.openRoutePanelWithStart(null)
        routePanelViewModel.setOverlayAnchor(RouteOverlayAnchor.EXPANDED)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(RouteSessionState.REVIEWING, routePanelViewModel.sessionState.value)
    }
}
