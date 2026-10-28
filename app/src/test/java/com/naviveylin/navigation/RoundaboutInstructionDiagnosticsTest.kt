package com.naviveylin.navigation

import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the roundabout diagnostics on a live step (spec: `osmscout-jni` — A route instruction
 * carries the roundabout exit ordinal and its circulation direction; spec: `auto-diagnostics` — no
 * coordinates in diagnostics; change `fix-roundabout-exit-number`, task 7.2).
 *
 * The fields are filled in native code, so this is what a device run reads to see that the route
 * produced them. The line's *shape* is asserted here; its values on a real route are the device
 * evidence.
 */
class RoundaboutInstructionDiagnosticsTest {

    private fun roundaboutExit(
        exit: Int = 0,
        clockwise: Boolean = true,
        hintExit: Int = 0
    ) = RouteStep().apply {
        kind = RouteStep.Kind.ROUNDABOUT_LEAVE
        roundaboutLeave = RouteStep.RoundaboutLeave().apply {
            exitCountTaken = exit.toLong()
            this.clockwise = clockwise
        }
        name = RouteStep.Name("Bahnhofstraße", "")
        if (hintExit > 0) {
            nextNext = RouteStep().apply {
                kind = RouteStep.Kind.ROUNDABOUT_LEAVE
                roundaboutLeave = RouteStep.RoundaboutLeave().apply {
                    exitCountTaken = hintExit.toLong()
                }
            }
        }
    }

    private fun plainTurn(turn: TurnType = TurnType.LEFT) = RouteStep().apply {
        kind = RouteStep.Kind.TURN
        this.turn = turn
    }

    @Test
    fun `the line names the kind, the exit and the direction`() {
        val line = roundaboutStepLine(roundaboutExit(exit = 3, clockwise = false))

        assertEquals("rbt: kind=ROUNDABOUT_LEAVE exit=3 cw=false hintExit=0", line)
    }

    @Test
    fun `the line carries numbers and identity only, never a coordinate`() {
        val line = roundaboutStepLine(roundaboutExit(exit = 3))

        // The checkNoCoordinatesInLogs gate rejects a decimal pair; assert the shape that keeps it out.
        assertFalse("no decimal pair on the line", Regex("""-?\d+\.\d+,\s*-?\d+\.\d+""").containsMatchIn(line))
        assertTrue("the exit ordinal is on the line", line.contains("exit=3"))
    }

    @Test
    fun `a roundabout exit is worth logging`() {
        assertTrue(logsRoundabout(roundaboutExit(exit = 3)))
    }

    @Test
    fun `a roundabout exit with no ordinal is not worth logging`() {
        // Nothing to report: the ordinal is what the line exists to show.
        assertFalse(logsRoundabout(roundaboutExit(exit = 0)))
    }

    @Test
    fun `a nested roundabout hint is worth logging`() {
        assertTrue(logsRoundabout(roundaboutExit(exit = 0, hintExit = 2)))
    }

    @Test
    fun `a plain turn is not logged`() {
        // The engine is fed on every fix; a non-roundabout step must not produce a line per fix.
        assertFalse(logsRoundabout(plainTurn()))
        assertFalse(logsRoundabout(plainTurn(TurnType.STRAIGHT_ON)))
        assertFalse(logsRoundabout(RouteStep().apply { kind = RouteStep.Kind.TARGET }))
    }
}
