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
 * spec: `osmscout-jni` — Live guidance publishes route steps).
 *
 * Live guidance publishes the manoeuvre ahead — the first manoeuvre after the position, never a
 * described route node — and its `nextNext` is the following manoeuvre within the close-manoeuvre
 * window. The surface copies that record and derives neither row; the native builder owns both.
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
    fun `the announced manoeuvre is a copy of the published step`() {
        val published = step(RouteStep.Kind.TURN, 500.0, remaining = 120.0, turn = TurnType.LEFT)

        val announced = guidanceStep(published)

        assertNotSame("the announced record is a copy, never the engine's state", published, announced)
        // Everything a guidance surface reads survives the copy.
        assertEquals(RouteStep.Kind.TURN, announced.kind)
        assertEquals(TurnType.LEFT, announced.turn)
        assertEquals(120.0, announced.remainingDistance!!, 1e-6)
        assertEquals(500.0, announced.distanceFromStart, 1e-6)
        // A surface that mutates the announced record cannot reach the engine's published state.
        announced.nextNext = step(RouteStep.Kind.TARGET, 900.0)
        assertNull(published.nextNext)
    }

    @Test
    fun `the published step's own hint is announced unchanged`() {
        // The shape live guidance publishes: the roundabout entry at 1000 m, the leave at 1035 m as
        // the following manoeuvre, with the exit ordinal the route takes.
        val published = step(
            RouteStep.Kind.ROUNDABOUT_ENTER, 1000.0, remaining = 100.0, name = "Kreisverkehr"
        ).apply {
            nextNext = step(
                RouteStep.Kind.ROUNDABOUT_LEAVE, 1035.0, name = "Bahnhofstraße", exit = 2
            )
        }

        val announced = guidanceStep(published)

        assertEquals(RouteStep.Kind.ROUNDABOUT_ENTER, announced.kind)
        val hint = announced.nextNext
        assertNotNull("the engine's hint reaches the surface", hint)
        assertEquals(RouteStep.Kind.ROUNDABOUT_LEAVE, hint!!.kind)
        assertEquals(2, hint.roundaboutLeave.exitCountTaken.toInt())
        assertEquals("the row's distance is the gap between the manoeuvres", 35.0, announced.nextNextGapMeters, 1e-6)
    }

    @Test
    fun `a step without a following manoeuvre announces no hint`() {
        val published = step(RouteStep.Kind.TURN, 500.0, remaining = 80.0, turn = TurnType.RIGHT)

        assertNull(guidanceStep(published).nextNext)
    }

    @Test
    fun `the car's unbounded next manoeuvre steps over described nodes`() {
        // The host's next-step row is not bounded by the phone's 200 m window (spec:
        // auto/navigation-view — Next-next turn shown when present), so it walks the list.
        val route = listOf(
            step(RouteStep.Kind.TURN, 500.0, turn = TurnType.LEFT),
            step(RouteStep.Kind.INFORMATIVE, 530.0, name = "Hauptstraße"),
            step(RouteStep.Kind.TURN, 750.0, turn = TurnType.RIGHT, name = "Zielweg")
        )

        val next = nextManoeuvreFrom(route, route[0])

        assertNotNull(next)
        assertEquals(RouteStep.Kind.TURN, next!!.kind)
        assertEquals("Zielweg", next.name.name)
    }
}
