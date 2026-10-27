package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the two spellings the turn type is resolved from and for the instruction the car and the
 * phone overlay read.
 * <p>
 * The engine serialises a turn as camelCase across the bridge and the stylesheet-era code wrote
 * UPPER_CASE, so both spellings have to resolve to the same constant — an unresolved one silently becomes
 * "straight on" and the driver turns the wrong way. The lookup's fallbacks and the constructors of
 * {@link RouteInstruction} were only partly exercised before this test.
 */
class TurnTypeAndInstructionTest {

    @Test
    void everyTurnTypeResolvesFromItsCamelCaseUpperAndLowerSpelling() {
        for (TurnType type : TurnType.values()) {
            String upper = type.name();
            String lower = upper.toLowerCase(java.util.Locale.ROOT);
            String camel = camelCase(upper);

            assertSame(type, TurnType.fromString(upper), upper + " must resolve from its own name");
            assertSame(type, TurnType.fromString(lower), lower + " must resolve case-insensitively");
            assertSame(type, TurnType.fromString(camel), camel + " is the spelling the bridge sends");
        }
    }

    @Test
    void anUnknownOrMissingTurnResolvesToStraightOn() {
        assertSame(TurnType.STRAIGHT_ON, TurnType.fromString(null));
        assertSame(TurnType.STRAIGHT_ON, TurnType.fromString("sideways"));
    }

    @Test
    void theShortConstructorCarriesNoNextNextHint() {
        RouteInstruction instruction = new RouteInstruction(
            120.0, TurnType.LEFT, null, null, null);

        assertEquals(120.0, instruction.distanceTo, 0.0);
        assertSame(TurnType.LEFT, instruction.turnType);
        assertEquals("", instruction.streetName, "a null street name reads as unknown");
        assertEquals("", instruction.description);
        assertEquals("", instruction.shortDescription);
        assertFalse(instruction.hasNextNext());
        assertTrue(instruction.toString().startsWith("RouteInstruction{distanceTo=120.0"));
    }

    @Test
    void aNextNextHintIsPresentOnlyWithADistanceAndADescription() {
        RouteInstruction hinted = new RouteInstruction(
            350.0, 12.0, 350.0, TurnType.LEFT, "Hauptstrasse", "Turn left", "Left",
            180.0, TurnType.RIGHT, "Then right", "Right");
        RouteInstruction withoutDescription = new RouteInstruction(
            350.0, 12.0, 350.0, TurnType.LEFT, "Hauptstrasse", "Turn left", "Left",
            180.0, TurnType.RIGHT, "", "");

        assertTrue(hinted.hasNextNext());
        assertFalse(withoutDescription.hasNextNext(), "a hint without a description is no hint");
        assertEquals(180.0, hinted.nextNextDistanceTo, 0.0);
        assertSame(TurnType.RIGHT, hinted.nextNextTurnType);
        assertEquals(0, hinted.roundaboutExitNumber, "a plain turn takes no roundabout exit");
    }

    private static String camelCase(String name) {
        String[] parts = name.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder camel = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            camel.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return camel.toString();
    }
}
