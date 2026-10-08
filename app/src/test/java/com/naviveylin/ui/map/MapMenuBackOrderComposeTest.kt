package com.naviveylin.ui.map

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The back gesture with the menu band in place (spec: `map-canvas-screen` — System back dismisses
 * topmost overlay, The map menu is composed above the map chrome).
 *
 * Back priority in Compose comes from the order the handlers are registered, which is the order the
 * composables are composed in. The menu band is composed **before** the modal band, so a surface
 * that is open over the menu keeps the back gesture — the menu's own handler (the real
 * `MapMenu`) must not swallow it. A change that moved the menu band behind the modal band would
 * flip this case.
 */
@RunWith(RobolectricTestRunner::class)
class MapMenuBackOrderComposeTest {

    @get:Rule
    // createAndroidComposeRule so the case can dispatch a system back through the activity.
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun menu(onDismiss: () -> Unit) {
        MapMenu(
            expanded = true,
            onDismiss = onDismiss,
            onDownloadMaps = {},
            onOpenFavorites = {},
            onOpenSearch = {},
            onOpenAbout = {},
            toasterTopPadding = 4.dp
        )
    }

    @Test
    fun `a surface composed after the menu band takes the back gesture`() {
        var menuDismissed = 0
        var surfaceDismissed = 0
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().zIndex(MapLayer.MENU.z)) {
                    menu { menuDismissed++ }
                }
                Box(Modifier.fillMaxSize().zIndex(MapLayer.MODAL.z)) {
                    Box(Modifier.fillMaxSize().testTag("harness-surface"))
                    BackHandler { surfaceDismissed++ }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()

        assertEquals("the surface must take the back gesture", 1, surfaceDismissed)
        assertEquals("the menu must stay open behind the surface", 0, menuDismissed)
    }

    @Test
    fun `the menu takes the back gesture when nothing is composed over it`() {
        var menuDismissed = 0
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().zIndex(MapLayer.MENU.z)) {
                    menu { menuDismissed++ }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()

        assertEquals("the menu must close on back", 1, menuDismissed)
    }

    @Test
    fun `the menu band is declared after the chrome band and before the modal band`() {
        // The source order of the bands is what fixes the back-handler order (see the class KDoc);
        // the band *values* decide the paint order and are asserted in `MapLayerBandsTest`.
        val source = java.io.File(
            "src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt"
        ).readText()
        val chrome = source.indexOf("zIndex(MapLayer.CHROME.z)")
        val menu = source.indexOf("zIndex(MapLayer.MENU.z)")
        val modal = source.indexOf("zIndex(MapLayer.MODAL.z)")
        assertTrue("all three bands must be composed", chrome > 0 && menu > 0 && modal > 0)
        assertTrue("the menu band must follow the chrome band", chrome < menu)
        assertTrue("the modal band must follow the menu band", menu < modal)
    }
}
