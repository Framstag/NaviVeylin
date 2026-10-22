package com.naviveylin.ui.navigation

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.R
import com.naviveylin.test.ComposeSandboxRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.content.Context

/**
 * Compose UI tests for the routing status card's progress rings
 * (spec: navigation-status-details — "Route progress rings on the routing status row").
 * Robolectric with the default sandbox — no @Config(sdk=...) here,
 * per the JNI stub classloader rule.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationStateOverlayComposeTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()

    private fun launchOverlay(
        distanceProgress: Int? = null,
        timeProgress: Int? = null,
        onStop: () -> Unit = {},
        onClick: () -> Unit = {}
    ) {
        composeRule.setContent {
            NavigationStateOverlay(
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 600_000,
                distanceProgressPercent = distanceProgress,
                timeProgressPercent = timeProgress,
                onStopNavigation = onStop,
                onClick = onClick
            )
        }
    }

    /** The value glyph slots the row draws, each in the same fixed slot. */
    private fun childrenOf(tag: String): Int =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .onChildren().fetchSemanticsNodes().size

    @Test
    fun ringsShownWhenPercentsPassed() {
        launchOverlay(distanceProgress = 42, timeProgress = 50)

        composeRule.onNodeWithTag("distanceProgressRing", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("timeProgressRing", useUnmergedTree = true).assertExists()
        // …and the full-width bars they replaced are gone, not merely unused: the card renders
        // no bar row any more (spec: the lines requirement is removed by this change).
        composeRule.onNodeWithTag("distanceProgressLine", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("timeProgressLine", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun noRingsWhenPercentsNull() {
        launchOverlay(distanceProgress = null, timeProgress = null)

        composeRule.onNodeWithTag("distanceProgressRing", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("timeProgressRing", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun etaSlotCarriesNoRing() {
        launchOverlay(distanceProgress = 42, timeProgress = 50)

        // A ringed slot holds its ring and its glyph; the arrival-time slot holds the glyph
        // alone (spec: the two rings are told apart from the plain glyph).
        assertEquals("the remaining-time slot must hold its ring", 2, childrenOf("timeGlyphSlot"))
        assertEquals("the remaining-distance slot must hold its ring", 2, childrenOf("distanceGlyphSlot"))
        assertEquals("the arrival-time slot must carry no ring", 1, childrenOf("etaGlyphSlot"))
    }

    @Test
    fun rowShowsNoPercentText() {        launchOverlay(distanceProgress = 42, timeProgress = 50)

        val values = composeRule.onAllNodes(
            hasText("", substring = true) and hasAnyAncestor(hasTestTag("navStatusDetailsStatsRegion")),
            useUnmergedTree = true
        )
        // Exactly the three values: no label and no percent value was added to the row
        // (spec: No labels or percent values shown).
        values.assertCountEquals(3)
        composeRule.onAllNodes(hasText("%", substring = true), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    // --- The card's own height ---------------------------------------------------
    // The card sits in the bottom band of a driving screen, so its height is map the driver
    // does not see. This is the measured bound the ring change is held against (design D1,
    // guidelines/Build.md §2 "measure first"): the card measured 145.0 dp with the two progress
    // lines and 120.0 dp with the rings, so the bound sits between the two rather than on
    // either number — an assertion pinned to the current value breaks on the next 1 dp change
    // (measured 2026-10-13).

    @Test
    fun cardHeightStaysWithinTheBound() {
        launchOverlay(distanceProgress = 42, timeProgress = 50)

        val cardPx = composeRule.onNodeWithTag("routingStatusCard", useUnmergedTree = true)
            .fetchSemanticsNode().size.height
        val boundPx = with(composeRule.density) { 130.dp.toPx() }
        val density = composeRule.density.density
        println("RoutingStatusCardHeight ${cardPx / density}dp (bound 130dp)")

        assertTrue(
            "the routing status card must stay within 130 dp (measured ${cardPx / density} dp)",
            cardPx <= boundPx
        )
    }

    // --- The three value slots' shared geometry -----------------------------------
    // One size, one top edge, whether or not a ring is drawn in them: a value whose progress is
    // unknown keeps its place instead of shifting the row
    // (spec: navigation-status-details — A glyph without progress keeps its place in the row).

    @Test
    fun glyphSlotsShareOneSizeAndAlignment() {
        // One value without progress and one with: the ringed slot must not outgrow the plain one.
        launchOverlay(distanceProgress = null, timeProgress = 50)

        val eta = composeRule.onNodeWithTag("etaGlyphSlot", useUnmergedTree = true).fetchSemanticsNode()
        val time = composeRule.onNodeWithTag("timeGlyphSlot", useUnmergedTree = true).fetchSemanticsNode()
        val distance = composeRule.onNodeWithTag("distanceGlyphSlot", useUnmergedTree = true)
            .fetchSemanticsNode()

        assertEquals("the arrival-time slot must match the remaining-time slot", eta.size, time.size)
        assertEquals("…and the remaining-distance slot", eta.size, distance.size)
        assertEquals(
            "the slots must share one top edge",
            eta.boundsInRoot.top.toDouble(), time.boundsInRoot.top.toDouble(), 0.5
        )
        assertEquals(
            "the slots must share one top edge (distance)",
            eta.boundsInRoot.top.toDouble(), distance.boundsInRoot.top.toDouble(), 0.5
        )
    }

    // --- Stop control as a tap target of its own --------------------------------
    // Spec: navigation-status-details — "Routing status card is clickable": the control's
    // hit area is its own, and it never overlaps the card's details tap area.

    @Test
    fun stopControlHitAreaIsAtLeast48Dp() {
        launchOverlay()

        val control = composeRule.onNodeWithTag("stopNavigation", useUnmergedTree = true)
        control.assertWidthIsAtLeast(48.dp)
        control.assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun stopControlBoundsLieOutsideDetailsRegionBounds() {
        launchOverlay()

        val control = composeRule.onNodeWithTag("stopNavigation", useUnmergedTree = true)
            .getBoundsInRoot()
        val statsRegion = composeRule.onNodeWithTag("navStatusDetailsStatsRegion", useUnmergedTree = true)
            .getBoundsInRoot()
        val blockRegion = composeRule.onNodeWithTag("navStatusDetailsRegion", useUnmergedTree = true)
            .getBoundsInRoot()

        // The stats strip ends at the control's leading edge and the road-name/progress
        // block sits above it, so neither region covers the control.
        assertTrue(
            "stats region $statsRegion overlaps control $control",
            statsRegion.right <= control.left
        )
        assertTrue(
            "details block $blockRegion overlaps control $control",
            blockRegion.bottom <= control.top
        )
    }

    @Test
    fun detailsRegionClickOpensDetailsAndDoesNotStop() {
        var detailsClicks = 0
        var stopClicks = 0
        launchOverlay(onStop = { stopClicks++ }, onClick = { detailsClicks++ })

        composeRule.onNodeWithTag("navStatusDetailsRegion", useUnmergedTree = true).performClick()

        assertEquals(1, detailsClicks)
        assertEquals(0, stopClicks)
    }

    @Test
    fun detailsRegionClickLeavesStopCallbackUntouched() {
        var detailsClicks = 0
        var stopClicks = 0
        launchOverlay(onStop = { stopClicks++ }, onClick = { detailsClicks++ })

        composeRule.onNodeWithTag("navStatusDetailsStatsRegion", useUnmergedTree = true).performClick()

        assertEquals(1, detailsClicks)
        assertEquals(0, stopClicks)
    }

    @Test
    fun stopControlCentreEndsNavigationWithoutOpeningDetails() {
        var detailsClicks = 0
        var stopClicks = 0
        launchOverlay(onStop = { stopClicks++ }, onClick = { detailsClicks++ })

        composeRule.onNodeWithTag("stopNavigation", useUnmergedTree = true).performClick()

        assertEquals(1, stopClicks)
        assertEquals(0, detailsClicks)
    }

    @Test
    fun stopControlIsTheOnlyActionableTargetOverItsOwnBox() {
        launchOverlay()

        val stopLabel = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.stop_navigation)

        // The merged tree is what a tap and a screen reader resolve against: the control is a target
        // of its own, carrying the stop action and its own label
        // (spec: navigation-status-details — The tap is received by the control, not by the card).
        val control = composeRule.onNodeWithTag("stopNavigation")
            .assertHasClickAction()
            .assertContentDescriptionEquals(stopLabel)
            .fetchSemanticsNode()

        // …and it is the only actionable target over its own box. A card-wide clickable added around
        // the row claims the same pixels and is invisible to the geometry cases above, because those
        // read the two details regions instead of every target that carries an action
        // (spec: … — the two hit areas do not overlap; each is a target of its own).
        val overlapping = composeRule.onAllNodes(hasClickAction())
            .fetchSemanticsNodes()
            .filter { it.boundsInRoot.overlaps(control.boundsInRoot) }
        assertEquals(
            "only the stop control may be actionable over its own box (found $overlapping)",
            1,
            overlapping.size
        )
    }

    @Test
    fun noStopControlWhenCallbackIsNull() {
        // Host substitute for the car surfaces, which render the same shared row with no
        // stop action (spec scenario: The car card carries no stop control).
        composeRule.setContent {
            NavigationStatsRow(
                remainingDistance = 5000.0,
                etaMillis = System.currentTimeMillis() + 600_000,
                onStopNavigation = null
            )
        }

        composeRule.onNodeWithTag("stopNavigation", useUnmergedTree = true).assertDoesNotExist()
    }
}
