package com.naviveylin.core

import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's projection of a structured route step (spec: `osmscout-jni` — A calculated route
 * publishes structured route steps; spec: `route-analysis` — Rows are derived from the route's
 * steps).
 *
 * [RouteStep] is a plain Java value holder, so these run as plain JUnit tests with no native library.
 */
class ManeuverFromStepTest {

    /** A step carrying the identity, position and leg values every kind has. */
    private fun step(kind: RouteStep.Kind) = RouteStep().apply {
        this.kind = kind
        lat = 52.52
        lon = 13.405
        legDistance = 120.0
        legTime = 9.0
        distanceFromStart = 1_500.0
        timeFromStart = 120.0
        databaseId = 7L
        pathObjectOffset = 4711L
        currentNodeIndex = 3L
    }

    @Test
    fun `every step kind maps to its manoeuvre fields`() {
        for (kind in RouteStep.Kind.entries) {
            val manoeuvre = manoeuvreFromStep(step(kind))

            assertEquals("kind $kind", kind, manoeuvre.kind)
            assertEquals("position $kind", 52.52, manoeuvre.lat, 1e-9)
            assertEquals("position $kind", 13.405, manoeuvre.lon, 1e-9)
            assertEquals("leg $kind", 120.0, manoeuvre.legDistanceMeters, 1e-9)
            assertEquals("leg $kind", 9.0, manoeuvre.legTimeSeconds, 1e-9)
            assertEquals("node identity $kind", 7L, manoeuvre.databaseId)
            assertEquals("node identity $kind", 4711L, manoeuvre.pathObjectOffset)
            assertEquals("node identity $kind", 3L, manoeuvre.nodeIndex)
        }
    }

    @Test
    fun `an absent record stays absent`() {
        // A plain turn: the route carries a turn and nothing else. Every record the step does not
        // carry has to stay absent, so no row can name a roundabout exit or a motorway it has not got.
        val plain = step(RouteStep.Kind.TURN).apply {
            turn = TurnType.LEFT
            name = RouteStep.Name("Hauptstraße", "B1")
        }

        val manoeuvre = manoeuvreFromStep(plain)

        assertEquals(TurnType.LEFT, manoeuvre.move)
        assertNull("a plain turn has no exit ordinal", manoeuvre.roundaboutExit)
        assertNull("a plain turn has no circulation direction", manoeuvre.clockwise)
        assertNull("a plain turn leaves no motorway", manoeuvre.motorwayFrom)
        assertNull("a plain turn enters no motorway", manoeuvre.motorwayTo)
        assertEquals("Hauptstraße (B1)", manoeuvre.displayName)
    }

    @Test
    fun `a roundabout leave carries its ordinal and its circulation direction`() {
        val leave = step(RouteStep.Kind.ROUNDABOUT_LEAVE).apply {
            roundaboutLeave = RouteStep.RoundaboutLeave().apply {
                exitCountTaken = 3L
                clockwise = false
            }
        }

        val manoeuvre = manoeuvreFromStep(leave)

        assertEquals(3, manoeuvre.roundaboutExit)
        assertEquals(false, manoeuvre.clockwise)
        // A roundabout carries no turn record of its own: its move stays absent.
        assertNull(manoeuvre.move)
    }

    @Test
    fun `a roundabout enter carries the direction and no ordinal`() {
        val enter = step(RouteStep.Kind.ROUNDABOUT_ENTER).apply {
            roundaboutEnter = RouteStep.RoundaboutEnter().apply { clockwise = true }
        }

        val manoeuvre = manoeuvreFromStep(enter)

        assertNull("entering a roundabout takes no exit", manoeuvre.roundaboutExit)
        assertEquals(true, manoeuvre.clockwise)
    }

    @Test
    fun `a motorway change carries both sides and its move`() {
        val change = step(RouteStep.Kind.MOTORWAY_CHANGE).apply {
            motorwayChange = RouteStep.MotorwayChange().apply {
                from = RouteStep.Name("Bundesautobahn 40", "A40")
                to = RouteStep.Name("Bundesautobahn 42", "A42")
                move = TurnType.LEFT
            }
        }

        val manoeuvre = manoeuvreFromStep(change)

        // The reference is what a driver reads on the sign; the long name is the fallback.
        assertEquals("A40", manoeuvre.motorwayFrom)
        assertEquals("A42", manoeuvre.motorwayTo)
        assertEquals(TurnType.LEFT, manoeuvre.move)
    }

    @Test
    fun `a motorway enter names the motorway entered`() {
        val enter = step(RouteStep.Kind.MOTORWAY_ENTER).apply {
            motorwayEnter = RouteStep.MotorwayEnter().apply {
                to = RouteStep.Name("", "A42")
            }
        }

        val manoeuvre = manoeuvreFromStep(enter)

        assertEquals("A42", manoeuvre.motorwayTo)
        assertNull(manoeuvre.motorwayFrom)
        assertNull("entering a motorway is not a turn", manoeuvre.move)
    }

    @Test
    fun `a name change names the street the route continues on`() {
        val change = step(RouteStep.Kind.NAME_CHANGE).apply {
            name = RouteStep.Name("Alte Straße", "")
            nameChanged = RouteStep.NameChanged().apply {
                from = RouteStep.Name("Alte Straße", "")
                to = RouteStep.Name("Neue Straße", "")
            }
        }

        val manoeuvre = manoeuvreFromStep(change)

        assertEquals("Neue Straße", manoeuvre.name)
        assertEquals("Neue Straße", manoeuvre.displayName)
    }

    @Test
    fun `the display name prefers the name and falls back to the reference`() {
        val named = manoeuvreFromStep(step(RouteStep.Kind.TURN).apply {
            turn = TurnType.RIGHT
            name = RouteStep.Name("Hauptstraße", "")
        })
        val referenced = manoeuvreFromStep(step(RouteStep.Kind.TURN).apply {
            turn = TurnType.RIGHT
            name = RouteStep.Name("", "B1")
        })
        val anonymous = manoeuvreFromStep(step(RouteStep.Kind.TURN).apply {
            turn = TurnType.RIGHT
            name = RouteStep.Name("  ", "")
        })

        assertEquals("Hauptstraße", named.displayName)
        assertEquals("B1", referenced.displayName)
        assertNull(anonymous.displayName)
    }

    @Test
    fun `a straight-on turn is the move that collapses`() {
        val straight = manoeuvreFromStep(step(RouteStep.Kind.TURN).apply {
            turn = TurnType.STRAIGHT_ON
        })
        val left = manoeuvreFromStep(step(RouteStep.Kind.TURN).apply {
            turn = TurnType.LEFT
        })

        assertTrue(straight.isStraightOn)
        assertFalse(left.isStraightOn)
    }

    @Test
    fun `a turn without a turn record keeps its move absent`() {
        // Not "straight on": an absent record is not a direction the route described.
        val bare = manoeuvreFromStep(step(RouteStep.Kind.TURN))

        assertNull(bare.move)
        assertFalse(bare.isStraightOn)
    }
}
