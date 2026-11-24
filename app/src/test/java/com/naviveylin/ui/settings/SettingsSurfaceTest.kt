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
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.onChildren
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
import com.naviveylin.core.BundledMapStyles
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
import org.junit.Assert.assertFalse
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
 * - the **registry** (`SettingsRegistry.root`) is the real root the app renders — six topics and, in
 *   this group, no rows at all (the topic tables fill in tasks.md groups 3-5);
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
        // A row is one control (its switch toggles from the whole row), so its merged node carries
        // both texts and the same bounds: the two lines are measured on the unmerged tree, where
        // each keeps its own place under the row's label.
        val label = composeRule.onNodeWithText("Copy", useUnmergedTree = true).getBoundsInRoot()
        val help = composeRule.onNodeWithText("Cancel", useUnmergedTree = true).getBoundsInRoot()
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

    // ---- the announcement rows (tasks.md 5.3, 5.4) ----

    /**
     * The overspeed row is a bounded slider over whole km/h, from 0 to 30, showing its value and
     * both endpoints, with the stored default of 5 (spec: `settings-surface` — "The overspeed delta
     * is a bounded slider"; tasks.md 5.3 `theOverspeedSliderOffersOneKilometreSteps`).
     */
    @Test
    fun theOverspeedSliderOffersOneKilometreSteps() {
        // The rows read the document the host loaded on its start path (design D2).
        runBlocking { controller.load() }
        launchSurface(topic = SettingsTopic.HINTS_AND_LANGUAGE)

        composeRule.onNodeWithText(label(R.string.overspeed_warning_delta)).performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.overspeed_delta_value).format(5)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.settings_slider_endpoint).format(0)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.settings_slider_endpoint).format(30)).assertIsDisplayed()

        val slider = composeRule.onNodeWithTag(SETTINGS_SLIDER_TAG_PREFIX + OVERSPEED_ROW)
        val range = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0f, range.range.start, 0f)
        assertEquals(30f, range.range.endInclusive, 0f)
        assertEquals("one stop per km/h between the endpoints", 29, range.steps)
        assertEquals("the stored default", 5f, range.current, 0f)

        // Two whole values a pointer could land on, and both are written as they are: 7 km/h is not
        // rounded to a coarser step and the range's top is reachable.
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        awaitStored { settings -> settings.overspeedWarningDeltaKmh == 7 }
        composeRule.onNodeWithText(label(R.string.overspeed_delta_value).format(7)).assertIsDisplayed()

        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        awaitStored { settings -> settings.overspeedWarningDeltaKmh == 30 }
    }

    /**
     * A row with a long help text offers it on a sheet of its own, and dismissing that sheet leaves
     * the value as it was (spec: `settings-taxonomy` — "A long explanation opens on its own surface";
     * tasks.md 5.4 `aLongHelpOpensWithoutChangingTheValue`).
     */
    @Test
    fun aLongHelpOpensWithoutChangingTheValue() {
        runBlocking { controller.load() }
        launchSurface(topic = SettingsTopic.HINTS_AND_LANGUAGE)
        val valueBefore = controller.settings.value.autoVoiceGuidanceEnabled

        composeRule.onNodeWithTag(SETTINGS_HELP_TAG_PREFIX + CAR_ROW).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_LONG_HELP_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.settings_help_voice_car_long)).assertIsDisplayed()
        // The short line stays on the row it belongs to; the long one is the sheet's only text.
        composeRule.onNodeWithText(label(R.string.settings_help_voice_car)).assertIsDisplayed()

        composeRule.onNodeWithText(label(R.string.settings_help_close)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_LONG_HELP_TAG).assertDoesNotExist()
        switchOf(CAR_ROW).assertIsOn()
        assertEquals(
            "reading a row's explanation changes no value",
            valueBefore,
            runBlocking { SettingsStorage(context).load() }.autoVoiceGuidanceEnabled
        )
    }

    // ---- the Karte topic's rows and the behaviour rules they follow (tasks.md 3.1, 3.5) ----

    /**
     * The map style row is a `SELECT`: its options live on a surface of their own, every bundled
     * style is listed with the current one marked, and picking one writes the style the map loads
     * (spec: `settings-surface` — "Map style opens a selection surface"; spec: `map-styles` — "All
     * bundled styles are selectable"; tasks.md 3.1 `theStyleRowOpensItsSelectionSurface`).
     */
    @Test
    fun theStyleRowOpensItsSelectionSurface() {
        runBlocking { controller.load() }
        launchSurface(topic = SettingsTopic.MAP)

        // Nothing of the options is on screen until the row is selected: the row is a sub-surface,
        // not an inline list.
        composeRule.onNodeWithTag(SETTINGS_SELECT_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SETTINGS_ROW_TAG_PREFIX + MAP_STYLE_ROW)
            .performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_SELECT_TAG).assertIsDisplayed()
        for (style in BundledMapStyles.USER_SELECTABLE) {
            composeRule.onNodeWithTag(SETTINGS_SELECT_OPTION_TAG_PREFIX + style).assertExists()
        }
        // The basemap's internal stylesheet is not a user-selectable style, so it is not an option.
        composeRule.onNodeWithTag(SETTINGS_SELECT_OPTION_TAG_PREFIX + BundledMapStyles.BASEMAP_STYLE_NAME)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(SETTINGS_SELECT_OPTION_TAG_PREFIX + BundledMapStyles.DEFAULT_STYLE_NAME)
            .assertIsSelected()
        composeRule.onNodeWithTag(SETTINGS_SELECT_OPTION_TAG_PREFIX + "cycle")
            .assertIsNotSelected()

        composeRule.onNodeWithTag(SETTINGS_SELECT_OPTION_TAG_PREFIX + "cycle").performClick()
        awaitStored { settings -> settings.styleSheet == "cycle" }

        // Choosing writes the style and closes the options; the row now names the chosen style.
        composeRule.onNodeWithTag(SETTINGS_SELECT_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("cycle").assertIsDisplayed()
    }

    /**
     * A switch toggles from the whole row: the label is part of the control's target, not decoration
     * beside a small toggle (design D5; tasks.md 3.5 `aSwitchTogglesFromTheWholeRow`).
     */
    @Test
    fun aSwitchTogglesFromTheWholeRow() {
        val section = SettingsSection(SettingsTopic.MAP, android.R.string.ok)
        launchSurface(
            root = listOf(
                SettingsTopicTable(
                    SettingsTopic.MAP,
                    listOf(row(key = "poi", section = section))
                )
            ),
            topic = SettingsTopic.MAP
        )

        switchOf("poi").assertIsOn()
        composeRule.onNodeWithTag(SETTINGS_ROW_TAG_PREFIX + "poi").assertHasClickAction()

        // The tap lands on the label column, not on the toggle: the row is the target.
        composeRule.onNodeWithText(label(android.R.string.copy)).performClick()
        awaitStored { settings -> !settings.keepScreenOn }

        switchOf("poi").assertIsOff()
    }

    /**
     * A change on the surface is written as it is made: there is no save action, and the value is in
     * the settings file while the surface is still open, which is what makes it survive a restart
     * (spec: `settings-surface` — "A change persists without a save action"; tasks.md 3.5
     * `aChangePersistsWithoutASaveAction`).
     */
    @Test
    fun aChangePersistsWithoutASaveAction() {
        // The switch starts on, so the case makes the change the scenario names: POI symbols off —
        // in the file before the map reads it on its start path.
        runBlocking { SettingsStorage(context).save(AppSettings(preferSymbolPoiIcons = true)) }
        val client = setUpMap()
        composeRule.setContent {
            SettingsSurfaceHost(
                controller = viewModel.settingsController,
                onOpenMapManager = { mapManagerOpened++ },
                onOpenAbout = { aboutOpened++ }
            )
        }
        composeRule.runOnIdle { viewModel.settingsController.openTopic(SettingsTopic.MAP) }
        composeRule.waitForIdle()

        switchOf(POI_SYMBOLS_ROW).performScrollTo().assertIsOn()
        composeRule.onNodeWithText(label(R.string.prefer_symbol_icons)).performClick()
        awaitStored { settings -> !settings.preferSymbolPoiIcons }

        // One tap was the whole interaction, and the running map took it: the surface writes
        // through the one writer, so the map follows the document as it is made (no save action,
        // no restart). The wait drains the rule's scheduler between polls because the controller
        // publishes the document only once `SettingsStorage.update` returns — that continuation
        // resumes on this scheduler, while `awaitStored` above waits on the file it wrote, so the
        // two can land in either order.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            !viewModel.uiState.value.preferSymbolPoiIcons
        }
        assertFalse(
            "the map's own state follows the surface as it is made",
            viewModel.uiState.value.preferSymbolPoiIcons
        )
        assertEquals(
            "the renderer is told the new value",
            false,
            client.preferSymbolIconsCalls.last()
        )
        // The row reads the document again and shows the new value, and the file holds it, which is
        // what a restart loads.
        switchOf(POI_SYMBOLS_ROW).assertIsOff()
        assertFalse(
            "a change on the surface is in the file with no save action",
            runBlocking { SettingsStorage(context).load() }.preferSymbolPoiIcons
        )
    }

    // ---- the Navigation & Route topic's rows (tasks.md group 4) ----

    /**
     * The Navigation & Route page carries the routing values, each as its own row: the three maximum
     * speeds, the three avoidance switches and the anchors for routing and free driving — the phone's
     * two and the car's own two (spec: `settings-surface` — "Navigation & Route carries routing
     * values", "The phone edits the settings Android Auto owns").
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
            "free-driving-anchor",
            "car-routing-anchor",
            "car-free-driving-anchor"
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

        // The values are the settings' own defaults, which is what the surface renders before a write.
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
        awaitStored { settings -> settings.carMaxSpeedKmh == 90 }

        assertEquals(
            "the row writes the value the user moved to",
            90,
            runBlocking { SettingsStorage(context).load() }.carMaxSpeedKmh
        )
        assertEquals(
            "the other vehicles' speeds are untouched",
            15,
            runBlocking { SettingsStorage(context).load() }.bicycleMaxSpeedKmh
        )
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

        composeRule.onNodeWithText(label(R.string.max_speed_section))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.avoidance_section))
            .performScrollTo().assertIsDisplayed()

        // One page, one order: the three speed rows come first, the avoidance rows under them (read
        // off the page's own child order, so a section moved elsewhere fails here).
        val order = pageOrder()
        val lastSpeedRow = order.indexOf(SETTINGS_ROW_TAG_PREFIX + "foot-max-speed")
        val firstAvoidanceRow = order.indexOf(SETTINGS_ROW_TAG_PREFIX + "avoid-tolls")
        val lastAvoidanceRow = order.indexOf(SETTINGS_ROW_TAG_PREFIX + "avoid-unpaved")
        assertTrue(
            "the avoidance rows must follow the speed rows on the same page, order=$order",
            lastSpeedRow >= 0 && firstAvoidanceRow > lastSpeedRow &&
                lastAvoidanceRow > firstAvoidanceRow
        )

        // A switch shows the stored value, toggles it, and the shared builder carries the result.
        switchOf("avoid-ferries").assertIsOff().performClick()
        awaitStored { settings -> settings.avoidFerries }

        val stored = controller.settings.value
        assertEquals("the toggle reached the settings document", true, stored.avoidFerries)
        assertEquals(
            "the shared builder carries the choice every calculation reads",
            true,
            stored.routingProfileFor(Vehicle.CAR).avoidFerries
        )
    }

    /**
     * Each phone anchor row opens the 5×3 picker with the configured preset marked, and choosing a
     * cell writes that preset and returns to the topic (spec: `settings-surface` — "A finite value set
     * is presented as options"; tasks.md 4.3 `theAnchorPickerMarksTheConfiguredValue`).
     */
    @Test
    fun theAnchorPickerMarksTheConfiguredValue() {
        launchSurface(topic = SettingsTopic.NAVIGATION_AND_ROUTE)

        composeRule.onNodeWithTag(SETTINGS_ROW_TAG_PREFIX + "routing-anchor")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_SELECT_TAG).assertIsDisplayed()
        // The stored preset is the marked one, not the first cell: a picker that marked the default
        // would fail here.
        composeRule.onNodeWithTag(SETTINGS_SELECT_OPTION_TAG_PREFIX + VehicleAnchorPosition.CENTER.id)
            .assertIsSelected()
        composeRule.onNodeWithTag(
            SETTINGS_SELECT_OPTION_TAG_PREFIX + VehicleAnchorPosition.BOTTOM_RIGHT.id
        ).assertIsNotSelected()

        composeRule.onNodeWithTag(
            SETTINGS_SELECT_OPTION_TAG_PREFIX + VehicleAnchorPosition.BOTTOM_RIGHT.id
        ).performClick()
        awaitStored { settings -> settings.routingAnchorId == VehicleAnchorPosition.BOTTOM_RIGHT.id }

        // Choosing closes the picker: it is a sub-surface of the topic, not a place to be stranded in.
        composeRule.onNodeWithTag(SETTINGS_SELECT_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SETTINGS_TOPIC_TAG).assertIsDisplayed()
    }

    /**
     * The picker's option texts are the shared enum's labels — the ones the car's own picker draws —
     * so the two surfaces name the same preset the same way (label parity; tasks.md 4.3
     * `theAnchorLabelsMatchTheCarPicker`).
     */
    @Test
    fun theAnchorLabelsMatchTheCarPicker() {
        launchSurface(topic = SettingsTopic.NAVIGATION_AND_ROUTE)

        composeRule.onNodeWithTag(SETTINGS_ROW_TAG_PREFIX + "free-driving-anchor")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // One option per preset, each carrying `VehicleAnchorPosition.label` — the literal the car's
        // `VehicleAnchorPickerScreen` draws for the same preset.
        for (anchor in VehicleAnchorPosition.entries) {
            // The merged cell node carries the label text, so a cell showing a key instead of the
            // preset's label fails here.
            composeRule.onNodeWithTag(SETTINGS_SELECT_OPTION_TAG_PREFIX + anchor.id)
                .assertIsDisplayed()
            composeRule.onNodeWithTag(SETTINGS_SELECT_OPTION_TAG_PREFIX + anchor.id)
                .assert(hasText(anchor.label))
        }
    }

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

    /** The one switch a row carries: the row's tag is on the label column, the toggle is below it. */
    private fun switchOf(key: String) = composeRule.onNode(
        isToggleable() and hasAnyAncestor(hasTestTag(SETTINGS_ROW_TAG_PREFIX + key)),
        useUnmergedTree = true
    )

    /**
     * One numeric row's slider as the user can reach it: the value it shows is the stored one and the
     * range it offers is the row's own payload, read off the control rather than off the code.
     */
    private fun assertSliderRange(key: String, current: Int, min: Int, max: Int) {
        val slider = composeRule
            .onNodeWithTag(SETTINGS_SLIDER_TAG_PREFIX + key)
            .performScrollTo()
        slider.assertRangeInfoEquals(
            ProgressBarRangeInfo(current.toFloat(), min.toFloat()..max.toFloat(), steps = max - min - 1)
        )
    }

    /** The tags the topic page shows, in the order it shows them: sections and rows, top to bottom. */
    private fun pageOrder(): List<String> =
        composeRule.onNodeWithTag(SETTINGS_TOPIC_TAG)
            .onChildren()
            .fetchSemanticsNodes()
            .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.TestTag) }

    /**
     * Wait until the settings file holds a document [holds] accepts: a row's write is a coroutine on
     * the rule's scheduler and lands in the file on the storage's own dispatcher.
     */
    private fun awaitStored(holds: (AppSettings) -> Boolean) {
        composeRule.waitForIdle()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            // The write the row made is a coroutine on the rule's dispatcher, so each poll drains it
            // before asking the file: the storage's read is a fresh one on the real dispatcher, which
            // this rule cannot advance, and advancing only once before the loop leaves a write that
            // was queued after that call unanswered (#1048).
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            runBlocking { SettingsStorage(context).load() }.let(holds)
        }
    }

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

    private companion object {
        /** The car's own voice switch: the row whose long help this class opens. */
        const val CAR_ROW = "voice-guidance-car"

        /** The overspeed warning delta the topic's slider edits. */
        const val OVERSPEED_ROW = "overspeed-warning"
        /** The "Karte" topic's map style row: the `SELECT` whose options open on their own surface. */
        const val MAP_STYLE_ROW = "map-style"
        /** The "Karte" topic's POI-symbol switch: the row a change without a save action toggles. */
        const val POI_SYMBOLS_ROW = "poi-symbols"
    }

    /** The map screen's own dependencies, as `MapLayerStackComposeTest` builds them. */
    private fun setUpMap(): FakeOSMScoutClient {
        val client = FakeOSMScoutClient()
        val locationService = LocationService(context)
        // The controller's storage reads the document on the settings dispatcher, and the row shows
        // the document the controller publishes. Left on the default `Dispatchers.IO`, that read is a
        // thread the rule never advances: `advanceUntilIdle` below returns while the read is still in
        // flight, and a case that asserts the row's value on the first frame reads the defaults the
        // controller starts at. That read won its race here and lost it on a loaded runner — the same
        // tree that passed the scan at 3b6a4acf failed it at d885082d with no source change (#1048).
        // On the rule's dispatcher the read is part of the queue `advanceUntilIdle` drains, so the row
        // renders the persisted document deterministically.
        val settingsStorage = SettingsStorage(context)
            .also { it.ioDispatcher = mainDispatcherRule.dispatcher }
        viewModel = MapCanvasViewModel(
            viewportStorage = ViewportStorage(context),
            settingsController = SettingsController(settingsStorage),
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
