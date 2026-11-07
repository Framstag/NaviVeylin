package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for the spellings the turn type is resolved from.
 * <p>
 * The engine serialises a turn as camelCase across the bridge and the stylesheet-era code wrote
 * UPPER_CASE, so both spellings have to resolve to the same constant — an unresolved one silently becomes
 * "straight on" and the driver turns the wrong way. The type stays after the prose path was retired: the step
 * model's {@code direction.turn}, {@code motorwayChange.move} and {@code motorwayLeave.move} carry it
 * (change `retire-instruction-prose`).
 */
class TurnTypeTest {

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

    private static String camelCase(String name) {
        String[] parts = name.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder camel = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            camel.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return camel.toString();
    }
}
