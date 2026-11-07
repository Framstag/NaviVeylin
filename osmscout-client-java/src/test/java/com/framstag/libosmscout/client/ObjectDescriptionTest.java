package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the structured object description the long-press details sheet reads.
 * <p>
 * The sheet reads the entry list and the object identity without a null check, so the constructor's
 * handling of a null list and of an unknown identity is the contract. The class was not exercised
 * anywhere in the module's suite before this test.
 */
class ObjectDescriptionTest {

    @Test
    void theEntryListDefaultsToEmptyAndIsUnmodifiable() {
        ObjectDescription description = new ObjectDescription(null);

        assertTrue(description.getEntries().isEmpty(), "a null list is the empty description");
        assertThrows(UnsupportedOperationException.class,
            () -> description.getEntries().add(new DescriptionEntry()));
        assertTrue(Double.isNaN(description.getObjectLat()), "an unknown location stays NaN");
        assertTrue(Double.isNaN(description.getObjectLon()), "an unknown location stays NaN");
        assertNull(description.getObjectRefType());
        assertNull(description.getObjectTypeName());
        assertEquals(0L, description.getObjectFileOffset());
    }

    @Test
    void theEntryListIsKeptInTheOrderTheNativeServiceReturnedIt() {
        List<DescriptionEntry> entries = new ArrayList<>();
        DescriptionEntry first = new DescriptionEntry();
        first.sectionKey = "general";
        DescriptionEntry second = new DescriptionEntry();
        second.sectionKey = "name";
        entries.add(first);
        entries.add(second);

        ObjectDescription description = new ObjectDescription(entries, 51.5, 7.4);

        assertEquals(2, description.getEntries().size());
        assertEquals("general", description.getEntries().get(0).sectionKey);
        assertEquals("name", description.getEntries().get(1).sectionKey);
        assertEquals(51.5, description.getObjectLat(), 0.0);
        assertEquals(7.4, description.getObjectLon(), 0.0);
    }

    @Test
    void theIdentityOfTheDescribedObjectIsCarriedThrough() {
        ObjectDescription description = new ObjectDescription(
            List.of(), 51.5, 7.4, "way", "building", 4711L);

        assertEquals("way", description.getObjectRefType());
        assertEquals("building", description.getObjectTypeName());
        assertEquals(4711L, description.getObjectFileOffset());
    }
}
