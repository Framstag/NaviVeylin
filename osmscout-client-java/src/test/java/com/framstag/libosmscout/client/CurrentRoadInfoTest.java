package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the road the vehicle is on, as the navigation engine reports it.
 * <p>
 * Both the "is there anything to show" decision and the one-line summary live in this class, and the
 * engine hands each field in as null when the way carries no tag — the substitution and the display
 * format were not exercised anywhere in the module's suite before this test.
 */
class CurrentRoadInfoTest {

    @Test
    void aMissingFieldIsStoredAsAnEmptyString() {
        CurrentRoadInfo info = new CurrentRoadInfo(null, null, null);

        assertEquals("", info.ref);
        assertEquals("", info.typeName);
        assertEquals("", info.name);
    }

    @Test
    void hasInfoIsFalseOnlyWhenEveryFieldIsEmpty() {
        assertFalse(new CurrentRoadInfo(null, null, null).hasInfo());
        assertFalse(new CurrentRoadInfo("", "", "").hasInfo());
        assertTrue(new CurrentRoadInfo("A40", "", "").hasInfo());
        assertTrue(new CurrentRoadInfo("", "motorway", "").hasInfo());
        assertTrue(new CurrentRoadInfo("", "", "Ruhrschnellweg").hasInfo());
    }

    @Test
    void theDisplayStringJoinsOnlyThePresentParts() {
        assertEquals("", new CurrentRoadInfo("", "", "").toDisplayString());
        assertEquals("A40", new CurrentRoadInfo("A40", "", "").toDisplayString());
        assertEquals("motorway", new CurrentRoadInfo("", "motorway", "").toDisplayString());
        assertEquals("Ruhrschnellweg", new CurrentRoadInfo("", "", "Ruhrschnellweg").toDisplayString());
        assertEquals("A40 Ruhrschnellweg", new CurrentRoadInfo("A40", "", "Ruhrschnellweg").toDisplayString());
        assertEquals("A40 motorway", new CurrentRoadInfo("A40", "motorway", "").toDisplayString());
        assertEquals("A40 motorway Ruhrschnellweg",
            new CurrentRoadInfo("A40", "motorway", "Ruhrschnellweg").toDisplayString());
    }

    @Test
    void toStringNamesAllThreeFields() {
        assertEquals("CurrentRoadInfo{ref='A40', typeName='motorway', name='Ruhrschnellweg'}",
            new CurrentRoadInfo("A40", "motorway", "Ruhrschnellweg").toString());
    }
}
