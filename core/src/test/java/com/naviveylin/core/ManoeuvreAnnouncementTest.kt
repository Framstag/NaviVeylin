package com.naviveylin.core

import com.framstag.libosmscout.client.RouteStep
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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
        turn: TurnType? = null
    ) = RouteStep().apply {
        this.kind = kind
        this.distanceFromStart = distanceFromStart
        this.remainingDistance = remaining
        this.turn = turn
        if (name != null) this.name = RouteStep.Name(name, "")
    }

    @Test
    fun `a manoeuvre is announced as published`() {
        val published = step(RouteStep.Kind.TURN, 500.0, remaining = 120.0, turn = TurnType.LEFT)

        assertSame(published, guidanceStep(published, emptyList()))
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
    fun `no manoeuvre ahead keeps the published step`() {
        val route = listOf(step(RouteStep.Kind.INFORMATIVE, 500.0))
        val published = step(RouteStep.Kind.INFORMATIVE, 500.0, remaining = 10.0)

        assertSame(published, guidanceStep(published, route))
    }
}
