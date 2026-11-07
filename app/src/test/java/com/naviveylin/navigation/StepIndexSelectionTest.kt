package com.naviveylin.navigation

import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure unit tests for the step-index lookup shared by the phone and car
 * navigation handlers (spec: auto/navigation-view — "Current step shown
 * during navigation", next-turn-overlay). Plain JUnit: no native client, no
 * Robolectric sandbox requirement.
 *
 * The lookup matches on the step's distance from the route start **and** its manoeuvre kind (one route
 * node can describe two manoeuvres — a turn and a motorway entry), and it must advance monotonically: a
 * repeated manoeuvre must never re-bind the active step to an earlier, already-passed occurrence.
 */
class StepIndexSelectionTest {

    private fun step(kind: RouteStep.Kind, distanceFromStart: Double, turn: TurnType? = null) =
        RouteStep().apply {
            this.kind = kind
            this.distanceFromStart = distanceFromStart
            legDistance = 100.0
            if (turn != null) this.turn = turn
        }

    private fun leftTurn(distanceFromStart: Double) =
        step(RouteStep.Kind.TURN, distanceFromStart, TurnType.LEFT)

    @Test
    fun `matches first occurrence at or after current index`() {
        val list = listOf(leftTurn(0.0), leftTurn(100.0), leftTurn(200.0))
        assertEquals(1, nextStepIndex(list, 0, leftTurn(100.0)))
    }

    @Test
    fun `advances forward with the live step`() {
        val list = listOf(leftTurn(0.0), leftTurn(100.0), leftTurn(200.0))
        // Current step is the first turn; the live step is the second.
        assertEquals(2, nextStepIndex(list, 1, leftTurn(200.0)))
    }

    @Test
    fun `a repeated manoeuvre does not jump backward`() {
        // Two identical turns, 400 m apart: starting from the second one, the lookup must not fall
        // back to the passed occurrence.
        val list = listOf(leftTurn(100.0), leftTurn(500.0))
        assertEquals(1, nextStepIndex(list, 1, leftTurn(500.0)))
    }

    @Test
    fun `no match ahead keeps the current index`() {
        val list = listOf(leftTurn(100.0))
        assertEquals(0, nextStepIndex(list, 0, leftTurn(9_000.0)))
    }

    @Test
    fun `a dual node binds to the step with the same kind`() {
        // One node describes two manoeuvres: a turn and a motorway entry at the same distance from the
        // start. Distance alone is ambiguous, so the kind decides (design D5 of
        // `route-steps-live-guidance`).
        val list = listOf(
            leftTurn(1000.0),
            step(RouteStep.Kind.MOTORWAY_ENTER, 1000.0),
            step(RouteStep.Kind.TARGET, 1500.0)
        )
        val liveMotorwayEntry = step(RouteStep.Kind.MOTORWAY_ENTER, 1000.0).apply {
            motorwayEnter = RouteStep.MotorwayEnter().apply { to = RouteStep.Name("", "A42") }
        }

        assertEquals(1, nextStepIndex(list, 0, liveMotorwayEntry))
    }

    @Test
    fun `a snap that moves the published distance still matches`() {
        // The live step reports its node's distance from the start; the snap moves it by a handful of
        // metres. The window absorbs that without letting a different manoeuvre match.
        val list = listOf(leftTurn(100.0), leftTurn(500.0))
        assertEquals(1, nextStepIndex(list, 0, leftTurn(512.0)))
    }
}
