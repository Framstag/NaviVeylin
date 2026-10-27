package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the client's own convenience methods — the ones that wrap a native entry point rather than
 * being one.
 * <p>
 * These methods are the module's own code, and until this test they were measured as uncovered: every one of
 * them needs an {@link OSMScoutClient} instance, and instantiating the class loads the JNI library. The host
 * stub in {@code osmscout-client-java/src/test/jniLibs} makes that load succeed, and
 * {@link FakeOSMScoutClient} answers the natives, so the delegation can be asserted without executing native
 * code. A wrapper that stops beginning a search request, stops cancelling it, or stops handing the vehicle on
 * is the kind of drift only these assertions see.
 */
class OSMScoutClientWrappersTest {

    @Test
    void theStyleSheetListIsReadFromTheDirectoryTheClientReports() throws Exception {
        Path styles = Files.createTempDirectory("stylesheets-available");
        Files.writeString(styles.resolve("standard.oss"), "/* style */");
        Files.writeString(styles.resolve("cycle.oss"), "/* style */");
        Files.writeString(styles.resolve("notes.txt"), "not a stylesheet");
        Files.createDirectory(styles.resolve("nested.oss"));

        FakeOSMScoutClient client = new FakeOSMScoutClient();
        client.styleSheetDirectory = styles.toString();

        assertEquals(List.of("cycle", "standard"), client.getAvailableStyleSheets(),
            "only regular .oss files are stylesheets, sorted, and their suffix is not part of the name");

        client.styleSheetDirectory = null;
        assertEquals(List.of(), client.getAvailableStyleSheets(),
            "an unconfigured client offers no stylesheets");

        client.styleSheetDirectory = "";
        assertEquals(List.of(), client.getAvailableStyleSheets(),
            "an empty directory setting offers no stylesheets");

        client.styleSheetDirectory = styles.resolve("absent").toString();
        assertEquals(List.of(), client.getAvailableStyleSheets(),
            "a directory that does not exist offers no stylesheets");

        System.out.println("stylesheets read from " + styles);
    }

    @Test
    void aStandaloneSearchBeginsAndEndsItsOwnRequest() {
        FakeOSMScoutClient client = new FakeOSMScoutClient();
        client.nextGeneration = 7;
        LocationEntry[] answer = new LocationEntry[] { new LocationEntry() };
        client.searchResult = answer;

        assertSame(answer, client.searchLocations("Berlin", 5, OSMScoutClient.NO_ADMIN_REGION),
            "the standalone search returns the native answer");
        assertEquals(List.of("beginSearch:places", "searchLocations:7", "cancelSearch:7"), client.calls,
            "it begins a place request, searches with that generation, and ends the request");

        client.calls.clear();
        assertSame(answer, client.searchLocationByForm("Berlin", "", "Berlin", "", 5),
            "the standalone structured search returns the native answer");
        assertEquals(List.of("beginSearch:places", "searchLocationByForm:8", "cancelSearch:8"), client.calls,
            "the structured form begins and ends its own request the same way");
    }

    @Test
    void aSearchRequestIsEndedEvenWhenTheSearchFails() {
        FakeOSMScoutClient client = new FakeOSMScoutClient();
        client.nextGeneration = 11;
        client.searchThrows = true;

        assertThrows(IllegalStateException.class,
            () -> client.searchLocations("Berlin", 5, OSMScoutClient.NO_ADMIN_REGION),
            "the failure reaches the caller");
        assertEquals(List.of("beginSearch:places", "searchLocations:11", "cancelSearch:11"), client.calls,
            "the request is ended on the failing path too, so a throw cannot leave the slot occupied");
    }

    @Test
    void aPoiCategorySearchResolvesItsTypesAndSkipsEmptyQuestions() {
        FakeOSMScoutClient client = new FakeOSMScoutClient();
        client.nextGeneration = 21;

        PoiEntry[] answer = new PoiEntry[] { new PoiEntry() };
        client.poiResult = answer;

        assertSame(answer, client.searchPOIs(PoiCategories.HOTELS, 52.5, 13.4, 500, 10),
            "the category search returns the native answer");
        assertNotNull(client.lastPoiTypes, "the category was resolved to OSM type names");
        assertTrue(client.lastPoiTypes.length > 0, "a known category names at least one OSM type");
        assertEquals(500.0, client.lastPoiRadius,
            "the radius is handed on unchanged");
        assertEquals(21, client.lastPoiGeneration,
            "the search runs under the generation its own request began");
        assertEquals(List.of("beginSearch:poi", "searchPOIsByTypes:21", "cancelSearch:21"), client.calls,
            "the POI search is one request, begun and ended by the wrapper");

        client.calls.clear();
        assertEquals(0, client.searchPOIs("no-such-category", 52.5, 13.4, 500, 10).length,
            "an unknown category answers nothing");
        assertEquals(0, client.searchPOIs(PoiCategories.HOTELS, 52.5, 13.4, 0, 10).length,
            "a radius of zero answers nothing");
        assertEquals(0, client.searchPOIs(PoiCategories.HOTELS, 52.5, 13.4, -1, 10).length,
            "a negative radius answers nothing");
        assertEquals(List.of(), client.calls,
            "a question the native side would answer with nothing is not asked at all");
    }

    @Test
    void aRouteRequestIsHandedOnWithItsProfile() {
        FakeOSMScoutClient client = new FakeOSMScoutClient();
        RoutingProfile profile = new RoutingProfile();
        RouteCallback callback = new RouteCallback() {
            @Override
            public void onProgress(int percent) {
            }

            @Override
            public void onSuccess(RouteEntry route) {
            }

            @Override
            public void onError(String message) {
            }

            @Override
            public void onCancel() {
            }
        };

        client.calculateRouteWithProfile(52.5, 13.4, 48.1, 11.6, profile, callback);
        assertSame(profile, client.lastProfile, "the profile reaches the native call");
        assertSame(callback, client.lastRouteCallback, "the callback reaches the native call");
        assertEquals(52.5, client.lastRouteEndpoints[0], "the start latitude is handed on");
        assertEquals(13.4, client.lastRouteEndpoints[1], "the start longitude is handed on");
        assertEquals(48.1, client.lastRouteEndpoints[2], "the destination latitude is handed on");
        assertEquals(11.6, client.lastRouteEndpoints[3], "the destination longitude is handed on");
        assertEquals(0L, client.lastRouteStartOffset,
            "a profile route names no start object, so its offset is zero");
        assertEquals(0L, client.lastRouteDestOffset,
            "a profile route names no destination object, so its offset is zero");

        client.calls.clear();
        client.calculateRouteWithObjectsAsync(52.5, 13.4, 42L, "node", 48.1, 11.6, 43L, "node", callback);
        assertNotNull(client.lastProfile, "the object-based form without a profile uses the default one");
        assertEquals(42L, client.lastRouteStartOffset, "the start object offset is handed on");
        assertEquals(43L, client.lastRouteDestOffset, "the destination object offset is handed on");
        assertEquals(List.of("calculateRouteWithObjectsWithProfile"), client.calls,
            "both public forms are one delegation to the native call");

        client.calls.clear();
        client.calculateRouteWithObjectsAsync(52.5, 13.4, 42L, "node", 48.1, 11.6, 43L, "node", profile,
            callback);
        assertSame(profile, client.lastProfile, "the explicit profile form hands its profile on");
        assertEquals(List.of("calculateRouteWithObjectsWithProfile"), client.calls,
            "the explicit profile form is the same single delegation");
    }

    @Test
    void navigationStartsWithTheCarWhenNoVehicleIsNamed() {
        FakeOSMScoutClient client = new FakeOSMScoutClient();
        NavigationListener listener = new NavigationListener() { };

        client.startNavigation(4711L, listener);
        assertEquals(4711L, client.lastRouteHandle, "the route handle reaches the native call");
        assertEquals(Vehicle.CAR, client.lastVehicle, "the default vehicle is the car");
        assertSame(listener, client.lastNavigationListener, "the listener reaches the native call");
        assertEquals(List.of("startNavigationWithVehicle"), client.calls,
            "the default form is one delegation to the vehicle form");
    }

    @Test
    void aRouteRenderWithoutMarkersDelegatesWithNoMarkerArguments() {
        FakeOSMScoutClient client = new FakeOSMScoutClient();
        double[] lats = new double[] { 52.5, 52.6 };
        double[] lons = new double[] { 13.4, 13.5 };

        int[] pixels = client.renderWithRoute(800, 600, 52.5, 13.4, 0.0, 16.0, 320.0, lats, lons);
        assertEquals(4, pixels.length, "the native pixels are returned unchanged");
        assertEquals(List.of("renderWithRouteAndPois"), client.calls,
            "a route-only render is the marker render with no markers");
    }

    @Test
    void theDownloadManagerIsCreatedOncePerClient() {
        FakeOSMScoutClient client = new FakeOSMScoutClient();

        MapDownloadManager first = client.getMapDownloadManager();
        assertNotNull(first, "the client offers a download manager");
        assertSame(first, client.getMapDownloadManager(),
            "the manager belongs to the client, so a second read is the same instance");

        System.out.println("download manager: " + first.getClass().getSimpleName());
    }
}
