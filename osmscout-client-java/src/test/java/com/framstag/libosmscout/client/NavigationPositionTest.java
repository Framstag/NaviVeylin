package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the position estimate the navigation engine hands to a listener.
 * <p>
 * The engine delivers the way fields as null when the resolved way is unknown, and the UI reads them
 * without a null check — so the constructor's empty-string substitution is the contract, and it was not
 * exercised anywhere in the module's suite.
 */
class NavigationPositionTest {

    @Test
    void aPositionWithoutWayInfoCarriesEmptyWayFields() {
        NavigationPosition position = new NavigationPosition(
            NavigationState.OnRoute, 51.5, 7.4, 90.0, 5.0);

        assertEquals(NavigationState.OnRoute, position.state);
        assertEquals(51.5, position.lat, 0.0);
        assertEquals(7.4, position.lon, 0.0);
        assertEquals(90.0, position.bearing, 0.0);
        assertEquals(5.0, position.accuracy, 0.0);
        assertEquals("", position.wayName);
        assertEquals("", position.wayRef);
        assertEquals("", position.wayType);
    }

    @Test
    void aMissingWayFieldIsStoredAsAnEmptyString() {
        NavigationPosition position = new NavigationPosition(
            NavigationState.OffRoute, 1.0, 2.0, Double.NaN, -1.0, null, "B 1", null);

        assertEquals("", position.wayName, "a null way name reads as unknown, not as a null");
        assertEquals("B 1", position.wayRef);
        assertEquals("", position.wayType);
        assertTrue(Double.isNaN(position.bearing), "an unknown bearing stays NaN");
        assertTrue(position.accuracy < 0, "an unknown accuracy stays negative");
    }

    @Test
    void theNavigationStateEnumNamesTheStatesTheEngineReports() {
        String[] names = {
            "Uninitialised", "NoGpsSignal", "OnRoute", "OffRoute", "EstimateInTunnel"
        };

        assertEquals(names.length, NavigationState.values().length);
        for (int i = 0; i < names.length; i++) {
            assertEquals(names[i], NavigationState.values()[i].name());
        }
        assertEquals(NavigationState.OnRoute, NavigationState.valueOf("OnRoute"));
    }
}
