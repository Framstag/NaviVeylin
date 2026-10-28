package com.framstag.libosmscout.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The manoeuvre facts a <em>route step</em> carries, which is what live guidance publishes
 * (spec: {@code osmscout-jni} — A route instruction carries the roundabout exit ordinal,
 * ... the roundabout circulation direction, The next-next hint carries its own exit ordinal;
 * change {@code route-steps-live-guidance}, task 6.2).
 *
 * These are value-type cases: the record under test is a plain holder, so they run without a native
 * library and pin the shape a surface reads — never a description, because none is published.
 */
class RouteStepFieldsTest {

    /** What the bridge produces for a step that leaves a roundabout at the given exit. */
    private static RouteStep roundaboutLeave(int exitNumber, boolean clockwise) {
        RouteStep step = new RouteStep();
        step.kind = RouteStep.Kind.ROUNDABOUT_LEAVE;
        step.roundaboutLeave = new RouteStep.RoundaboutLeave();
        step.roundaboutLeave.exitCountTaken = exitNumber;
        step.roundaboutLeave.clockwise = clockwise;
        step.name = new RouteStep.Name("Bahnhofstraße", "");
        return step;
    }

    private static RouteStep plainTurn() {
        RouteStep step = new RouteStep();
        step.kind = RouteStep.Kind.TURN;
        step.turn = TurnType.LEFT;
        step.name = new RouteStep.Name("Main St", "");
        return step;
    }

    @Test
    @DisplayName("a plain turn carries no exit ordinal and no roundabout record")
    void plainTurnCarriesNoExitOrdinal() {
        RouteStep plain = plainTurn();

        assertNull(plain.roundaboutLeave,
            "a non-roundabout manoeuvre must not report a roundabout record");
        assertNotEquals(RouteStep.Kind.ROUNDABOUT_LEAVE, plain.kind);
    }

    @Test
    @DisplayName("no exit is distinguishable from exit one")
    void noExitOrdinalIsDistinguishableFromExitOne() {
        RouteStep noExit = plainTurn();
        RouteStep firstExit = roundaboutLeave(1, true);

        assertNull(noExit.roundaboutLeave, "no exit at all");
        assertNotNull(firstExit.roundaboutLeave);
        assertEquals(1L, firstExit.roundaboutLeave.exitCountTaken, "the first exit is a real exit");
    }

    @Test
    @DisplayName("a counter-clockwise roundabout reports its direction")
    void counterClockwiseRoundaboutReportsItsDirection() {
        RouteStep clockwise = roundaboutLeave(3, true);
        RouteStep counterClockwise = roundaboutLeave(3, false);

        assertEquals(true, clockwise.roundaboutLeave.clockwise);
        assertEquals(false, counterClockwise.roundaboutLeave.clockwise);
        // Two routes over differently circulating roundabouts must not report the same direction.
        assertNotEquals(clockwise.roundaboutLeave.clockwise, counterClockwise.roundaboutLeave.clockwise);
    }

    @Test
    @DisplayName("the next-next hint carries its own exit ordinal")
    void nextNextHintCarriesItsOwnExitOrdinal() {
        RouteStep step = roundaboutLeave(3, true);
        step.nextNext = roundaboutLeave(1, true);

        assertEquals(3L, step.roundaboutLeave.exitCountTaken,
            "the enclosing step keeps its own ordinal");
        assertEquals(1L, step.nextNext.roundaboutLeave.exitCountTaken,
            "the hint's ordinal is its own; two roundabouts in a row take different exits");
    }

    @Test
    @DisplayName("a published step carries no guidance text")
    void publishedStepCarriesNoGuidanceText() {
        RouteStep step = roundaboutLeave(3, true);

        // The record has no description field at all: the wording is built by the client from the
        // values above (spec: turn-instruction-localization — the retired native fallback).
        assertEquals("Bahnhofstraße", step.name.name, "the step names the street as data");
        assertEquals("", step.name.ref, "and carries no reference when the map has none");
    }
}
