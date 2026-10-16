package com.framstag.libosmscout.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the roundabout fields of {@link RouteInstruction}.
 *
 * The values are produced by the JNI bridge, which cannot run on the host JVM, so this
 * test pins the Java-side contract the bridge and the Kotlin consumers agree on: the
 * field names and types, the defaults a caller that omits the roundabout fields observes,
 * and the fact that the roundabout fields do not disturb the description strings.
 */
class RouteInstructionFieldsTest {

    /** What the bridge produces for a route that leaves a roundabout at the third exit. */
    private static RouteInstruction roundaboutLeave(int exitNumber, boolean clockwise) {
        return new RouteInstruction(
            350.0, 12.0, 350.0, TurnType.ROUNDABOUT_LEAVE, "Bahnhofstraße",
            "Take exit 3 onto Bahnhofstraße", "Exit 3",
            0.0, TurnType.STRAIGHT_ON, "", "",
            exitNumber, clockwise);
    }

    @Test
    @DisplayName("the next-next hint carries its own exit ordinal")
    void nextNextHintCarriesItsOwnExitOrdinal() {
        RouteInstruction instruction = new RouteInstruction(
            350.0, 12.0, 350.0, TurnType.ROUNDABOUT_LEAVE, "Bahnhofstraße",
            "Take exit 3 onto Bahnhofstraße", "Exit 3",
            180.0, TurnType.ROUNDABOUT_LEAVE, "Take exit 1 onto Marktplatz", "Exit 1",
            3, true, 1);

        assertEquals(3, instruction.roundaboutExitNumber);
        assertEquals(1, instruction.nextNextRoundaboutExitNumber,
            "the hint's ordinal is its own; two roundabouts in a row take different exits");
    }

    @Test
    @DisplayName("a plain turn carries no exit ordinal")
    void plainTurnCarriesNoExitOrdinal() {
        RouteInstruction plain = new RouteInstruction(
            100.0, TurnType.LEFT, "Main St", "Turn left into Main St", "Turn left");

        assertEquals(0, plain.roundaboutExitNumber,
            "a non-roundabout instruction must report no exit ordinal");
    }

    @Test
    @DisplayName("no exit is distinguishable from exit one")
    void noExitOrdinalIsDistinguishableFromExitOne() {
        RouteInstruction plain = new RouteInstruction(
            100.0, TurnType.LEFT, "Main St", "Turn left into Main St", "Turn left");
        RouteInstruction firstExit = roundaboutLeave(1, true);

        assertEquals(0, plain.roundaboutExitNumber);
        assertEquals(1, firstExit.roundaboutExitNumber);
        assertFalse(plain.roundaboutExitNumber == firstExit.roundaboutExitNumber,
            "\"no exit\" and \"exit 1\" must not share a value");
    }

    @Test
    @DisplayName("a counter-clockwise instruction reports its direction")
    void counterClockwiseInstructionReportsItsDirection() {
        assertFalse(roundaboutLeave(3, false).roundaboutClockwise,
            "a counter-clockwise roundabout must not report clockwise");
        assertTrue(roundaboutLeave(3, true).roundaboutClockwise);
    }

    @Test
    @DisplayName("the native roundabout text is unchanged")
    void nativeRoundaboutTextIsUnchanged() {
        RouteInstruction instruction = roundaboutLeave(3, true);

        assertEquals(3, instruction.roundaboutExitNumber);
        assertEquals("Exit 3", instruction.shortDescription,
            "the field must not replace the text a text-only caller reads");
        assertEquals("Take exit 3 onto Bahnhofstraße", instruction.description);
        assertEquals("Bahnhofstraße", instruction.streetName);
    }

    @Test
    void fieldsExistWithTheTypesTheBridgeAndConsumersAgreeOn() throws Exception {
        Field exitNumber = RouteInstruction.class.getField("roundaboutExitNumber");
        Field clockwise = RouteInstruction.class.getField("roundaboutClockwise");

        assertEquals(int.class, exitNumber.getType(),
            "roundaboutExitNumber must be an int: the bridge passes it as a JNI int");
        assertEquals(boolean.class, clockwise.getType(),
            "roundaboutClockwise must be a boolean: the bridge passes it as a JNI boolean");
    }

    @Test
    void constructorsWithoutTheRoundaboutFieldsDefaultToNoRoundabout() {
        RouteInstruction fiveArg = new RouteInstruction(
            100.0, TurnType.ROUNDABOUT_LEAVE, "Main St", "Exit 3", "Exit 3");
        RouteInstruction elevenArg = new RouteInstruction(
            100.0, 12.0, 100.0, TurnType.ROUNDABOUT_LEAVE, "Main St", "Exit 3", "Exit 3",
            0.0, TurnType.STRAIGHT_ON, "", "");

        assertEquals(0, fiveArg.roundaboutExitNumber);
        assertEquals(0, elevenArg.roundaboutExitNumber);
        assertTrue(fiveArg.roundaboutClockwise,
            "the pre-existing direction default is clockwise, the behaviour before the field existed");
        assertTrue(elevenArg.roundaboutClockwise);
        assertEquals(0, fiveArg.nextNextRoundaboutExitNumber);
        assertEquals(0, elevenArg.nextNextRoundaboutExitNumber);
    }
}
