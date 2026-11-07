package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the plain value types the client API carries across the JNI boundary: a track point, a
 * favourite, a favourite group, a starred favourite, a POI hit, a description entry and a route.
 * <p>
 * Each of these is a field bag whose constructors initialise the collections the app writes into before the
 * native side fills the fields. A constructor that forgot a collection is a {@link NullPointerException} on
 * the first favourite the user saves, and none of the classes was loaded by the module's suite before this
 * test.
 */
class ClientValueTypesTest {

    @Test
    void aTrackPointCarriesItsCoordinatesAndOptionalTimestamp() {
        TrackPoint defaults = new TrackPoint();
        assertEquals(0.0, defaults.lat, 0.0);
        assertEquals(0.0, defaults.lon, 0.0);
        assertNull(defaults.timestamp, "a point imported without a time has no timestamp");

        TrackPoint timed = new TrackPoint(51.5, 7.4, "2026-10-27T08:00:00Z");
        assertEquals(51.5, timed.lat, 0.0);
        assertEquals(7.4, timed.lon, 0.0);
        assertEquals("2026-10-27T08:00:00Z", timed.timestamp);

        TrackPoint untimed = new TrackPoint(1.0, 2.0);
        assertEquals(1.0, untimed.lat, 0.0);
        assertEquals(2.0, untimed.lon, 0.0);
        assertNull(untimed.timestamp);
    }

    @Test
    void aNewFavoriteStartsWithAnEmptyAttributeMap() {
        FavoriteLocation favorite = new FavoriteLocation();

        assertTrue(favorite.attributes.isEmpty());
        favorite.attributes.put("colour", "red");
        assertEquals("red", favorite.attributes.get("colour"));

        FavoriteLocation placed = new FavoriteLocation("Home", 51.5, 7.4);
        assertEquals("Home", placed.name);
        assertEquals(51.5, placed.lat, 0.0);
        assertEquals(7.4, placed.lon, 0.0);
        assertTrue(placed.attributes.isEmpty());
    }

    @Test
    void aNewFavoriteGroupStartsWithAnEmptyListAndMap() {
        FavoriteLocationGroup group = new FavoriteLocationGroup();

        assertTrue(group.favorites.isEmpty());
        assertTrue(group.attributes.isEmpty());
        group.favorites.add(new FavoriteLocation("Home", 51.5, 7.4));
        assertEquals(1, group.favorites.size());

        FavoriteLocationGroup named = new FavoriteLocationGroup("Trips");
        assertEquals("Trips", named.name);
        assertTrue(named.favorites.isEmpty());
        assertTrue(named.attributes.isEmpty());
    }

    @Test
    void aStarredFavoriteCarriesItsGroupAndFavorite() {
        StarredFavoriteLocation starred = new StarredFavoriteLocation();

        assertNull(starred.groupName);
        assertNull(starred.favorite);

        FavoriteLocation home = new FavoriteLocation("Home", 51.5, 7.4);
        StarredFavoriteLocation placed = new StarredFavoriteLocation("Favourites", home);
        assertEquals("Favourites", placed.groupName);
        assertEquals("Home", placed.favorite.name);
    }

    @Test
    void aNewPoiEntryAndDescriptionEntryCanBeFilledFieldByField() {
        PoiEntry poi = new PoiEntry();
        poi.label = "Cafe";
        poi.operator = "Betreiber";
        poi.brand = "Marke";
        poi.objectType = "amenity_cafe";
        poi.lat = 51.5;
        poi.lon = 7.4;
        poi.distance = 12.5;

        assertEquals("Cafe", poi.label);
        assertEquals("Betreiber", poi.operator);
        assertEquals("Marke", poi.brand);
        assertEquals("amenity_cafe", poi.objectType);
        assertEquals(51.5, poi.lat, 0.0);
        assertEquals(7.4, poi.lon, 0.0);
        assertEquals(12.5, poi.distance, 0.0);

        DescriptionEntry entry = new DescriptionEntry();
        entry.sectionKey = "general";
        entry.subsectionKey = "name";
        entry.hasIndex = true;
        entry.index = 3;
        entry.labelKey = "name";
        entry.value = "Hauptstrasse";

        assertEquals("general", entry.sectionKey);
        assertEquals("name", entry.subsectionKey);
        assertTrue(entry.hasIndex);
        assertEquals(3, entry.index);
        assertEquals("name", entry.labelKey);
        assertEquals("Hauptstrasse", entry.value);
    }

    @Test
    void aNewRouteEntryCarriesNoRoute() {
        RouteEntry route = new RouteEntry();

        assertNull(route.latitudes);
        assertNull(route.longitudes);
        assertNull(route.steps);
        assertEquals(0.0, route.distance, 0.0);
        assertEquals(0.0, route.duration, 0.0);
        assertEquals(0L, route.routeHandle);

        route.latitudes = new double[]{51.5, 51.6};
        route.steps = new RouteStep[]{new RouteStep(), new RouteStep()};
        assertEquals(2, route.latitudes.length);
        assertEquals(2, route.steps.length);
    }

    @Test
    void theCallbackInterfacesCarryDefaultMethodsTheClientCanLeaveAlone() {
        MapDownloadListener download = new MapDownloadListener() {
        };
        download.onProgress("Berlin", 10, 100);
        download.onComplete("Berlin", "/maps/berlin");
        download.onError("Berlin", "no route to host");

        NavigationListener navigation = new NavigationListener() {
        };
        navigation.onPositionEstimate(new NavigationPosition(NavigationState.OnRoute, 51.5, 7.4, 0, 5));
        navigation.onRerouteRequest(51.5, 7.4, 90, 52.0, 8.0);
        navigation.onTargetReached(90, 0);
        navigation.onArrivalEstimate(1_700_000_000_000L, 1200);
        navigation.onCurrentSpeed(50);
        navigation.onMaxAllowedSpeed(100);
        navigation.onLaneUpdate(true, 3, true, 0, 1, "left",
            new LaneTurn[]{LaneTurn.LEFT, LaneTurn.STRAIGHT_ON, LaneTurn.NONE});
        navigation.onVoiceInstruction(new int[]{1, 2});
        navigation.onError("no gps");
        navigation.onRouteSteps(new RouteStep[0]);
        navigation.onNextRouteStep(null);
    }
}
