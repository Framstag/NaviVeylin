package com.naviveylin.auto

import com.naviveylin.auto.stepOf
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [ManeuverGlyphs] (spec: auto/navigation-view — host maneuver
 * requires a CarIcon per turn type; spec: car-host-fault-isolation — a template asset the
 * host receives per rebuild is reused while its state is unchanged).
 */
@RunWith(RobolectricTestRunner::class)
class ManeuverGlyphsTest {

    @Test
    fun providesIconForEveryTurnType() {
        for (type in TurnType.values()) {
            assertNotNull("maneuver icon for $type", ManeuverGlyphs.forTurnType(type))
        }
    }

    @Test
    fun iconsAreCachedPerTurnType() {
        assertSame(
            ManeuverGlyphs.forTurnType(TurnType.LEFT),
            ManeuverGlyphs.forTurnType(TurnType.LEFT)
        )
    }

    // ── lane strip ──

    @Test
    fun laneImageIsReusedWhileTheLaneStateIsUnchanged() {
        // The template is rebuilt on navigation-state updates; a fresh bitmap per rebuild
        // is a fresh bitmap the host receives over IPC every time.
        val turns = listOf(LaneTurn.LEFT, LaneTurn.STRAIGHT_ON)

        val first = ManeuverGlyphs.lanesImage(turns, 0..0)
        val second = ManeuverGlyphs.lanesImage(turns, 0..0)

        assertSame(first, second)
    }

    @Test
    fun laneImageChangesWithTheSuggestedLanes() {
        val turns = listOf(LaneTurn.LEFT, LaneTurn.STRAIGHT_ON)

        val suggestedLeft = ManeuverGlyphs.lanesImage(turns, 0..0)
        val suggestedRight = ManeuverGlyphs.lanesImage(turns, 1..1)

        assertNotSame(suggestedLeft, suggestedRight)
    }

    // --- the numbered roundabout exit (spec: roundabout-renderer, auto-navigation-hints) ---

    private fun roundaboutExit(exitNumber: Int) = stepOf(
        350.0, 12.0, 350.0, TurnType.ROUNDABOUT_LEAVE, "Bahnhofstraße",
        "Take exit $exitNumber onto Bahnhofstraße", "Exit $exitNumber",
        0.0, TurnType.STRAIGHT_ON, "", "",
        exitNumber, true, 0
    )

    @Test
    fun `the car hint icon uses the shared exit symbol`() {
        // One artwork source in `:core`, so a fix to the number's geometry reaches the phone and the
        // car bitmap surfaces at once.
        val numbered = ManeuverGlyphs.forTurnTypeAndExit(TurnType.ROUNDABOUT_LEAVE, 3)
        val plain = ManeuverGlyphs.forTurnType(TurnType.ROUNDABOUT_LEAVE)

        assertNotSame(numbered, plain)
        assertNotSame(numbered, ManeuverGlyphs.forTurnTypeAndExit(TurnType.ROUNDABOUT_LEAVE, 1))
        // and an unchanged exit reuses the same icon (spec: car-host-fault-isolation)
        assertSame(numbered, ManeuverGlyphs.forTurnTypeAndExit(TurnType.ROUNDABOUT_LEAVE, 3))
    }

    @Test
    fun `a numbered exit gets the numbered icon, not nothing`() {
        // The maneuver is handed to the host WITH our numbered glyph as well as the exit number: which
        // of the two the driver sees is host behaviour, so neither may be the only one supplied
        // (spec: auto/navigation-view - A roundabout exit uses the number-bearing host maneuver).
        assertNotSame(
            ManeuverGlyphs.forStep(roundaboutExit(3)),
            ManeuverGlyphs.forTurnType(TurnType.ROUNDABOUT_LEAVE)
        )
        assertSame(
            ManeuverGlyphs.forStep(roundaboutExit(3)),
            ManeuverGlyphs.forTurnTypeAndExit(TurnType.ROUNDABOUT_LEAVE, 3)
        )
    }

    @Test
    fun laneImageChangesWithTheLaneList() {
        val suggested = 0..0

        val twoLanes = ManeuverGlyphs.lanesImage(listOf(LaneTurn.LEFT, LaneTurn.STRAIGHT_ON), suggested)
        val threeLanes = ManeuverGlyphs.lanesImage(
            listOf(LaneTurn.LEFT, LaneTurn.STRAIGHT_ON, LaneTurn.RIGHT),
            suggested
        )

        assertNotSame(twoLanes, threeLanes)
    }

    @Test
    fun laneImageIsReusedAcrossUnchangedRebuildsEvenAfterAnotherState() {
        // The cache is last-state-wins; returning to a previous lane state must still
        // produce an image, not throw or hand back the other state's image.
        val first = listOf(LaneTurn.LEFT)
        val second = listOf(LaneTurn.RIGHT)

        val firstImage = ManeuverGlyphs.lanesImage(first, 0..0)
        ManeuverGlyphs.lanesImage(second, 0..0)
        val rebuilt = ManeuverGlyphs.lanesImage(first, 0..0)

        assertNotNull(rebuilt)
        assertNotSame(firstImage, rebuilt)
    }
}
