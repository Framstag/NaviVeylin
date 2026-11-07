package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for the lane-turn enum the navigation engine's lane updates carry.
 * <p>
 * The numeric id is the value the C++ {@code osmscout::LaneTurn} is marshalled with, so a shifted id does
 * not fail to compile — it turns "keep left" into "turn right" on the road. The enum was loaded nowhere in
 * the module's suite before this test.
 */
class LaneTurnTest {

    @Test
    void everyLaneTurnRoundTripsThroughItsNumericId() {
        for (LaneTurn turn : LaneTurn.values()) {
            assertSame(turn, LaneTurn.fromId(turn.getId()),
                turn + " must come back from its own id " + turn.getId());
        }
    }

    @Test
    void theIdsFollowTheNativeEnumOrder() {
        assertEquals(0, LaneTurn.NULL.getId());
        assertEquals(1, LaneTurn.NONE.getId());
        assertEquals(2, LaneTurn.LEFT.getId());
        assertEquals(9, LaneTurn.STRAIGHT_ON.getId());
        assertEquals(13, LaneTurn.RIGHT.getId());
        assertEquals(17, LaneTurn.UNKNOWN.getId());
        assertEquals(18, LaneTurn.LEFT_AND_RIGHT.getId());
        assertEquals(19, LaneTurn.values().length, "one constant per native enum value");
    }

    @Test
    void anIdTheNativeSideDoesNotKnowIsUnknown() {
        assertSame(LaneTurn.UNKNOWN, LaneTurn.fromId(-1));
        assertSame(LaneTurn.UNKNOWN, LaneTurn.fromId(99));
    }
}
