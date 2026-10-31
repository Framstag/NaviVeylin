package com.naviveylin.core

import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The step a guidance surface announces (spec: `next-turn-overlay` — Turn type icon, "Next next" hint;
 * change `route-steps-live-guidance`).
 *
 * The engine publishes one step per described route node, facts-only nodes included; measured on device
 * 2026-10-28, the overlay announced such a node ("0 m" + a street name, straight-ahead glyph) while the
 * real turn was 31 m ahead.
 */
class ManoeuvreAnnouncementTest {

    private fun step(
        kind: RouteStep.Kind,
        distanceFromStart: Double,
        remaining: Double? = null,
        name: String? = null,
        turn: TurnType? = null,
        exit: Int? = null
    ) = RouteStep().apply {
        this.kind = kind
        this.distanceFromStart = distanceFromStart
        this.remainingDistance = remaining
        this.turn = turn
        if (name != null) this.name = RouteStep.Name(name, "")
        if (exit != null) {
            roundaboutLeave = RouteStep.RoundaboutLeave().apply { exitCountTaken = exit.toLong() }
        }
    }

    @Test
    fun `a manoeuvre is announced as a copy of the published step`() {
        val published = step(RouteStep.Kind.TURN, 500.0, remaining = 120.0, turn = TurnType.LEFT)

        val announced = guidanceStep(published, emptyList())

        assertNotSame("the announced record is a copy, never the engine's state", published, announced)
        // Everything a guidance surface reads survives the copy.
        assertEquals(RouteStep.Kind.TURN, announced.kind)
        assertEquals(TurnType.LEFT, announced.turn)
        assertEquals(120.0, announced.remainingDistance!!, 1e-6)
        // A surface that mutates the announced record cannot reach the engine's published state.
        announced.nextNext = step(RouteStep.Kind.TARGET, 900.0)
        assertNull(published.nextNext)
    }

    @Test
    fun `a facts-only node announces the next manoeuvre, not itself`() {
        val route = listOf(
            step(RouteStep.Kind.TURN, 400.0, turn = TurnType.LEFT, name = "Start Street"),
            step(RouteStep.Kind.INFORMATIVE, 500.0, name = "Hauptstraße"),
            step(RouteStep.Kind.TURN, 531.0, turn = TurnType.SLIGHTLY_RIGHT, name = "Zielweg"),
            step(RouteStep.Kind.TARGET, 900.0)
        )
        // The engine published the facts-only node; the vehicle sits at 500 - 0 m from it.
        val published = step(RouteStep.Kind.INFORMATIVE, 500.0, remaining = 0.0, name = "Hauptstraße")

        val announced = guidanceStep(published, route)

        assertNotSame(published, announced)
        assertEquals(RouteStep.Kind.TURN, announced.kind)
        assertEquals(TurnType.SLIGHTLY_RIGHT, announced.turn)
        assertEquals("Zielweg", announced.name.name)
        // The distance is the manoeuvre's, measured from the vehicle's position (31 m ahead).
        assertEquals(31.0, announced.remainingDistance!!, 1e-6)
    }

    @Test
    fun `the distance shrinks with the vehicle's progress through the node`() {
        val route = listOf(
            step(RouteStep.Kind.INFORMATIVE, 500.0),
            step(RouteStep.Kind.TURN, 600.0, turn = TurnType.RIGHT)
        )

        // The vehicle has driven 450 m of the 500 m to the published node, so the turn is 150 m ahead.
        val published = step(RouteStep.Kind.INFORMATIVE, 500.0, remaining = 50.0)

        assertEquals(150.0, guidanceStep(published, route).remainingDistance!!, 1e-6)
    }

    @Test
    fun `the following manoeuvre within the window is the hint`() {
        val route = listOf(
            step(RouteStep.Kind.TURN, 500.0, turn = TurnType.LEFT, name = "Hauptstraße"),
            step(RouteStep.Kind.TURN, 535.0, turn = TurnType.RIGHT, name = "Zielweg"),
            step(RouteStep.Kind.TARGET, 900.0)
        )
        val published = step(RouteStep.Kind.INFORMATIVE, 500.0, remaining = 0.0)

        val announced = guidanceStep(published, route)

        assertEquals(RouteStep.Kind.TURN, announced.kind)
        val hint = announced.nextNext
        assertNotNull("the announced manoeuvre carries its following manoeuvre", hint)
        assertEquals(RouteStep.Kind.TURN, hint!!.kind)
        assertEquals(TurnType.RIGHT, hint.turn)
        assertEquals("Zielweg", hint.name.name)
        // The row's distance is the gap between the two manoeuvres, not a node's gap.
        assertEquals(35.0, announced.nextNextGapMeters, 1e-6)
    }

    @Test
    fun `a following manoeuvre beyond the window is not shown`() {
        val route = listOf(
            step(RouteStep.Kind.TURN, 500.0, turn = TurnType.LEFT),
            step(RouteStep.Kind.TURN, 750.0, turn = TurnType.RIGHT)
        )
        val published = step(RouteStep.Kind.INFORMATIVE, 500.0, remaining = 0.0)

        assertNull("a 250 m gap is not a hint", guidanceStep(published, route).nextNext)
    }

    @Test
    fun `a facts-only node between the manoeuvres is not the hint`() {
        // The engine's step list carries one step per described route node, so the entry after the
        // announced manoeuvre is normally a name/way-type node (census: steps=501, rows=34).
        val route = listOf(
            step(RouteStep.Kind.TURN, 500.0, turn = TurnType.LEFT),
            step(RouteStep.Kind.INFORMATIVE, 530.0, name = "Hauptstraße"),
            step(RouteStep.Kind.TURN, 535.0, turn = TurnType.RIGHT, name = "Zielweg")
        )
        val published = step(RouteStep.Kind.INFORMATIVE, 500.0, remaining = 0.0)

        val announced = guidanceStep(published, route)

        val hint = announced.nextNext
        assertNotNull("the following manoeuvre is the hint", hint)
        assertEquals(RouteStep.Kind.TURN, hint!!.kind)
        assertEquals("Zielweg", hint.name.name)
        assertEquals(35.0, announced.nextNextGapMeters, 1e-6)
    }

    @Test
    fun `the roundabout exit is the hint before the ring is entered`() {
        // The reported defect: approaching a roundabout the row showed the entry and no exit, and the
        // ordinal only appeared once the ring was entered.
        val route = listOf(
            step(RouteStep.Kind.ROUNDABOUT_ENTER, 1000.0, name = "Kreisverkehr"),
            step(RouteStep.Kind.INFORMATIVE, 1010.0, name = "Ringstraße"),
            step(RouteStep.Kind.ROUNDABOUT_LEAVE, 1035.0, name = "Bahnhofstraße", exit = 2),
            step(RouteStep.Kind.INFORMATIVE, 1050.0)
        )
        val published = step(RouteStep.Kind.INFORMATIVE, 1000.0, remaining = 0.0)

        val announced = guidanceStep(published, route)

        assertEquals(RouteStep.Kind.ROUNDABOUT_ENTER, announced.kind)
        val hint = announced.nextNext
        assertNotNull("the exit is announced before the ring is entered", hint)
        assertEquals(RouteStep.Kind.ROUNDABOUT_LEAVE, hint!!.kind)
        assertEquals(2, hint.roundaboutLeave.exitCountTaken.toInt())
        assertEquals(35.0, announced.nextNextGapMeters, 1e-6)
    }

    @Test
    fun `no manoeuvre ahead keeps the published fields and drops the node hint`() {
        val route = listOf(step(RouteStep.Kind.INFORMATIVE, 500.0))
        val published = step(RouteStep.Kind.INFORMATIVE, 500.0, remaining = 10.0)
        // The engine's own record may carry the next described route node as its hint; the surface shows none.
        published.nextNext = step(RouteStep.Kind.INFORMATIVE, 510.0, name = "Hauptstraße")

        val announced = guidanceStep(published, route)

        assertNotSame("the announced record is a copy, never the engine's state", published, announced)
        assertEquals(500.0, announced.distanceFromStart, 1e-6)
        assertNull("a route node is never the hint", announced.nextNext)
    }
}
