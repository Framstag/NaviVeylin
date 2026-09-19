package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the per-attribute match quality fields of {@link LocationEntry}.
 *
 * The fields are written by the JNI bridge (native side cannot be exercised on
 * the host JVM), so this test pins the Java-side contract the bridge and the
 * app-side ranker agree on: the field names, their types, and their defaults
 * (null / false) for an entry that was never populated by native code.
 */
class LocationEntryFieldsTest {

    private static Field field(String name) throws NoSuchFieldException {
        return LocationEntry.class.getField(name);
    }

    @Test
    void perAttributeQualityFieldsExistAsStrings() throws Exception {
        for (String name : new String[]{
            "adminRegionMatchQuality",
            "postalAreaMatchQuality",
            "locationMatchQuality",
            "addressMatchQuality",
            "poiMatchQuality",
            "matchedName",
            "matchedComponent",
        }) {
            assertEquals(String.class, field(name).getType(), name + " must be a String field");
        }
    }

    @Test
    void houseNumberFlagExistsAsBoolean() throws Exception {
        assertEquals(boolean.class, field("hasHouseNumber").getType());
    }

    @Test
    void newFieldsDefaultToNullAndFalse() throws Exception {
        LocationEntry entry = new LocationEntry();

        assertNull(entry.adminRegionMatchQuality);
        assertNull(entry.postalAreaMatchQuality);
        assertNull(entry.locationMatchQuality);
        assertNull(entry.addressMatchQuality);
        assertNull(entry.poiMatchQuality);
        assertNull(entry.matchedName);
        assertNull(entry.matchedComponent);
        assertFalse(entry.hasHouseNumber);
    }

    @Test
    void newFieldsAreIndependentOfTheCollapsedQualityField() throws Exception {
        LocationEntry entry = new LocationEntry();
        entry.matchQuality = "match";
        entry.adminRegionMatchQuality = "match";
        entry.locationMatchQuality = "candidate";

        // The collapsed field is intentionally kept for existing consumers
        // (address scoring); the per-attribute fields carry the real signal.
        assertEquals("match", entry.matchQuality);
        assertEquals("candidate", entry.locationMatchQuality);
    }
}
