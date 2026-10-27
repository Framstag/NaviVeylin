package com.framstag.libosmscout.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Host double for {@link OSMScoutClient} in this module's own suite.
 * <p>
 * The client's JNI entry points are {@code native} declarations, so a case cannot execute them: this subclass
 * replaces the ones the client's own convenience methods call and records the delegation, which is what those
 * methods are. Instantiating it runs {@link OSMScoutClient}'s static initializer, so the host stub in
 * {@code osmscout-client-java/src/test/jniLibs} must be on the test JVM's {@code java.library.path}
 * ({@code guidelines/Build.md} §6h) — no case here calls a native method.
 */
class FakeOSMScoutClient extends OSMScoutClient {

    /** Recorded delegation, in call order, as {@code name} or {@code name:argument}. */
    final List<String> calls = new ArrayList<>();

    /** Answer of {@link #getStyleSheetDirectory()}; null is what an unconfigured client returns. */
    String styleSheetDirectory;

    /** Answer of the search entry points. */
    LocationEntry[] searchResult = new LocationEntry[0];

    /** Answer of {@link #searchPOIsByTypes}. */
    PoiEntry[] poiResult = new PoiEntry[0];

    /** When true the search entry points throw, so the wrapper's {@code finally} is the only path left. */
    boolean searchThrows;

    /** Generation handed out by {@link #beginSearch}, one more on each call. */
    long nextGeneration = 100;

    String[] lastPoiTypes;
    long lastPoiGeneration = -1;
    double lastPoiRadius = -1;

    RoutingProfile lastProfile;
    RouteCallback lastRouteCallback;
    double[] lastRouteEndpoints;
    long lastRouteStartOffset = -1;
    String lastRouteStartType;
    long lastRouteDestOffset = -1;
    String lastRouteDestType;

    long lastRouteHandle = -1;
    Vehicle lastVehicle;
    NavigationListener lastNavigationListener;

    int[] lastRender = new int[] { 1, 2, 3, 4 };

    @Override
    public String getStyleSheetDirectory() {
        calls.add("getStyleSheetDirectory");
        return styleSheetDirectory;
    }

    @Override
    public long beginSearch(String slot) {
        calls.add("beginSearch:" + slot);
        return nextGeneration++;
    }

    @Override
    public boolean cancelSearch(long generation) {
        calls.add("cancelSearch:" + generation);
        return true;
    }

    @Override
    public LocationEntry[] searchLocations(String query, int limit, long adminRegionHandle,
                                           long generation) {
        calls.add("searchLocations:" + generation);
        if (searchThrows) {
            throw new IllegalStateException("search failed");
        }
        return searchResult;
    }

    @Override
    public LocationEntry[] searchLocationByForm(String adminRegion, String postalArea, String location,
                                                String address, int limit, long generation) {
        calls.add("searchLocationByForm:" + generation);
        if (searchThrows) {
            throw new IllegalStateException("search failed");
        }
        return searchResult;
    }

    @Override
    public PoiEntry[] searchPOIsByTypes(String[] typeNames, double lat, double lon, double radiusMeters,
                                        int limit, long generation) {
        calls.add("searchPOIsByTypes:" + generation);
        lastPoiTypes = typeNames;
        lastPoiRadius = radiusMeters;
        lastPoiGeneration = generation;
        return poiResult;
    }

    @Override
    public void calculateRouteWithObjectsWithProfile(
        double startLat, double startLon,
        long startObjOffset, String startObjType,
        double destLat, double destLon,
        long destObjOffset, String destObjType,
        RoutingProfile profile,
        RouteCallback callback) {
        calls.add("calculateRouteWithObjectsWithProfile");
        lastProfile = profile;
        lastRouteCallback = callback;
        lastRouteEndpoints = new double[] { startLat, startLon, destLat, destLon };
        lastRouteStartOffset = startObjOffset;
        lastRouteStartType = startObjType;
        lastRouteDestOffset = destObjOffset;
        lastRouteDestType = destObjType;
    }

    @Override
    public NavigationController startNavigationWithVehicle(
        long routeHandle, Vehicle vehicle, NavigationListener listener) {
        calls.add("startNavigationWithVehicle");
        lastRouteHandle = routeHandle;
        lastVehicle = vehicle;
        lastNavigationListener = listener;
        return null;
    }

    @Override
    public int[] renderWithRouteAndPois(
        int width, int height,
        double lat, double lon, double angle, double magnification,
        double dpi,
        double[] routeLats, double[] routeLons,
        double[] favoriteLats, double[] favoriteLons,
        double searchSelLat, double searchSelLon,
        double[] trackLats, double[] trackLons) {
        calls.add("renderWithRouteAndPois");
        return lastRender;
    }
}
