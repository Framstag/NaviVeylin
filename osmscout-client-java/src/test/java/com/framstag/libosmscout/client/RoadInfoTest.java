package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the resolved-road value the app asks the client for on every position update.
 * <p>
 * The app hides the road line when {@link RoadInfo#hasInfo()} is false, so "a road with a type but no name
 * and no ref" is a case the app relies on: it has to be reported as no road, not as an empty line. The class
 * was not exercised anywhere in the module's suite before this test.
 */
class RoadInfoTest {

    @Test
    void aMissingFieldIsStoredAsAnEmptyString() {
        RoadInfo road = new RoadInfo(null, null, null, Double.NaN);

        assertEquals("", road.name);
        assertEquals("", road.ref);
        assertEquals("", road.typeName);
        assertTrue(Double.isNaN(road.maxSpeedKmH), "an undefined speed stays NaN");
    }

    @Test
    void hasInfoNeedsANameOrARef() {
        assertFalse(new RoadInfo(null, null, "highway_residential", 50).hasInfo());
        assertTrue(new RoadInfo("Hauptstrasse", null, null, 50).hasInfo());
        assertTrue(new RoadInfo(null, "B 1", null, 50).hasInfo());
    }
}
