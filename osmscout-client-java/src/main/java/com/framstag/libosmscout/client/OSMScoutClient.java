package com.framstag.libosmscout.client;

/**
 * Client object for libosmscout.
 *
 * An instance is created via {@link OSMScoutClientBuilder}.
 * Call {@link #close()} to release native resources when done.
 */
@SuppressWarnings("restricted")
public class OSMScoutClient {

    static {
        // Android debug builds append "d" to native library names.
        // Try the standard name first, then the debug variant.
        try {
            System.loadLibrary("osmscout_client_java");
        } catch (UnsatisfiedLinkError e) {
            System.loadLibrary("osmscout_client_javad");
        }
    }

    /** Native handle referencing the C++ ClientData object. */
    private long nativeHandle;

    /** Package-private constructor, called from JNI. */
    OSMScoutClient() {
    }

    /**
     * Open a map database at the given path.
     *
     * @param path filesystem path to the map database directory
     * @return true if the database was opened successfully
     */
    public native boolean openDatabase(String path);

    /**
     * Reload the basemap database from the configured lookup directory.
     * <p>
     * Picks up basemap downloads, updates, or deletions while the app is
     * running. Reloading is asynchronous on the native render thread;
     * subsequent renders use the new basemap state.
     */
    public native void reloadBasemap();

    /**
     * Return the bounding box of a map database without affecting the
     * currently loaded databases.
     *
     * @param path absolute filesystem path to a directory containing .osmscout map data
     * @return double[]{minLat, minLon, maxLat, maxLon}, or null if the
     *         database cannot be opened or has no bounding box
     */
    public native double[] getDatabaseBoundingBox(String path);

    /**
     * Set a style sheet flag and reload the map style with it.
     * <p>
     * Used to switch between the stylesheet's day/night variants, e.g.
     * {@code setStyleSheetFlag("daylight", false)} renders the dark variant.
     * Reloading is asynchronous on the native render thread; subsequent renders
     * use the new style.
     *
     * @param key   flag name as used in the stylesheet FLAG section
     * @param value flag value
     */
    public native void setStyleSheetFlag(String key, boolean value);

    /**
     * Close the client and release native resources.
     *
     * @return true if closed successfully
     */
    public native boolean close();

    /**
     * Check if the client has been initialised.
     *
     * @return true if the native client is ready
     */
    public native boolean isInitialized();

    /**
     * Render the map to an ARGB pixel buffer.
     *
     * @param width        output image width in pixels
     * @param height       output image height in pixels
     * @param lat          center latitude
     * @param lon          center longitude
     * @param angle        map rotation angle in degrees (0 = north up)
     * @param magnification map magnification level
     * @return ARGB pixel array (width * height), or null on error
     */
    public native int[] render(int width, int height,
                                double lat, double lon,
                                double angle, int magnification);

    /**
     * Sentinel for "no default admin region" — pass to
     * {@link #searchLocations(String, int, long)} for an unconstrained search.
     */
    public static final long NO_ADMIN_REGION = 0L;

    /**
     * Search for locations matching the given query.
     *
     * @param query the search string
     * @param limit maximum number of results
     * @param adminRegionHandle handle of a resolved admin region (see
     *        {@link #resolveAdminRegion(double, double)}) used as default region
     *        fallback for incomplete queries, or {@link #NO_ADMIN_REGION} for an
     *        unconstrained search
     * @return array of matching locations
     */
    public native LocationEntry[] searchLocations(String query, int limit, long adminRegionHandle);

    /**
     * Resolve the admin region containing the given coordinate.
     *
     * @param lat latitude in degrees
     * @param lon longitude in degrees
     * @return opaque admin region handle, or 0 if none found
     */
    public native long resolveAdminRegion(double lat, double lon);

    /**
     * Release a previously resolved admin region handle.
     *
     * @param handle handle returned by {@link #resolveAdminRegion(double, double)}
     */
    public native void releaseAdminRegion(long handle);

    /**
     * Get the name of a previously resolved admin region.
     *
     * @param handle handle returned by {@link #resolveAdminRegion(double, double)}
     * @return region name, or null if the handle is unknown
     */
    public native String getAdminRegionName(long handle);

    /**
     * Get a description of the object at the given coordinates.
     *
     * @param lat latitude
     * @param lon longitude
     * @param magnification current map magnification (zoom level)
     * @return object description, or null if nothing found
     */
    public native ObjectDescription getDescription(double lat, double lon, int magnification);

    /**
     * Get the bounding box of the most reasonable visible object
     * at the given geographic coordinate.
     *
     * @param lat latitude in degrees
     * @param lon longitude in degrees
     * @param magnification current map magnification (zoom level)
     * @return double[]{minLat, maxLat, minLon, maxLon} for area/way objects,
     *         or null if the best match is a node or no object found
     */
    public native double[] getObjectBoundingBox(double lat, double lon, int magnification);

    /**
     * Calculate a route asynchronously.
     *
     * @param startLat  start latitude
     * @param startLon  start longitude
     * @param destLat   destination latitude
     * @param destLon   destination longitude
     * @param profile   routing profile (car, bicycle, pedestrian)
     * @param callback  called on completion or error
     */
    public native void calculateRouteAsync(double startLat, double startLon,
                                            double destLat, double destLon,
                                            RouteCallback callback);

    /**
     * Calculate a route asynchronously with a routing profile (vehicle type).
     * <p>
     * Uniquely-named method to avoid Java overload resolution issues.
     *
     * @param startLat  start latitude
     * @param startLon  start longitude
     * @param destLat   destination latitude
     * @param destLon   destination longitude
     * @param profile   routing profile (vehicle type, avoid flags)
     * @param callback  called on completion or error
     */
    public void calculateRouteWithProfile(double startLat, double startLon,
                                           double destLat, double destLon,
                                           RoutingProfile profile,
                                           RouteCallback callback) {
        calculateRouteWithObjectsWithProfile(startLat, startLon, 0, null,
            destLat, destLon, 0, null,
            profile, callback);
    }

    /**
     * Calculate a route asynchronously using OSM object references for
     * precise start/destination positioning.
     *
     * @param startLat      start latitude
     * @param startLon      start longitude
     * @param startObjOffset OSM object offset for start
     * @param startObjType  OSM object type for start
     * @param destLat       destination latitude
     * @param destLon       destination longitude
     * @param destObjOffset OSM object offset for destination
     * @param destObjType   OSM object type for destination
     * @param profile       routing profile
     * @param callback      called on completion or error
     */
    public void calculateRouteWithObjectsAsync(
        double startLat, double startLon,
        long startObjOffset, String startObjType,
        double destLat, double destLon,
        long destObjOffset, String destObjType,
        RoutingProfile profile,
        RouteCallback callback) {
        calculateRouteWithObjectsWithProfile(startLat, startLon,
            startObjOffset, startObjType,
            destLat, destLon,
            destObjOffset, destObjType,
            profile, callback);
    }

    /**
     * Calculate a route asynchronously using OSM object references with
     * explicit profile parameter.
     *
     * @param startLat      start latitude
     * @param startLon      start longitude
     * @param startObjOffset OSM object offset for start
     * @param startObjType  OSM object type for start
     * @param destLat       destination latitude
     * @param destLon       destination longitude
     * @param destObjOffset OSM object offset for destination
     * @param destObjType   OSM object type for destination
     * @param profile       routing profile
     * @param callback      called on completion or error
     */
    public native void calculateRouteWithObjectsWithProfile(
        double startLat, double startLon,
        long startObjOffset, String startObjType,
        double destLat, double destLon,
        long destObjOffset, String destObjType,
        RoutingProfile profile,
        RouteCallback callback);

    /**
     * Calculate a route asynchronously using OSM object references with
     * default profile (car).
     *
     * @param startLat      start latitude
     * @param startLon      start longitude
     * @param startObjOffset OSM object offset for start
     * @param startObjType  OSM object type for start
     * @param destLat       destination latitude
     * @param destLon       destination longitude
     * @param destObjOffset OSM object offset for destination
     * @param destObjType   OSM object type for destination
     * @param callback      called on completion or error
     */
    public void calculateRouteWithObjectsAsync(
        double startLat, double startLon,
        long startObjOffset, String startObjType,
        double destLat, double destLon,
        long destObjOffset, String destObjType,
        RouteCallback callback) {
        calculateRouteWithObjectsAsync(startLat, startLon,
            startObjOffset, startObjType,
            destLat, destLon,
            destObjOffset, destObjType,
            new RoutingProfile(), callback);
    }

    /**
     * Cancel any active route calculation.
     */
    public native void cancelRoute();

    /**
     * Start navigation with explicit vehicle type.
     *
     * @param routeHandle the route handle from the route callback
     * @param vehicle     the vehicle type for navigation
     * @param listener    callback for navigation events
     * @return a NavigationController for controlling the session
     */
    public native NavigationController startNavigationWithVehicle(
        long routeHandle, Vehicle vehicle, NavigationListener listener);

    /**
     * Start navigation with default vehicle (car).
     *
     * @param routeHandle the route handle from the route callback
     * @param listener    callback for navigation events
     * @return a NavigationController for controlling the session
     */
    public NavigationController startNavigation(
        long routeHandle, NavigationListener listener) {
        return startNavigationWithVehicle(routeHandle, Vehicle.CAR, listener);
    }

    /**
     * Import track points from a GPX file.
     *
     * @param filePath path to the GPX file
     * @return array of track points, or empty array on error
     */
    public native TrackPoint[] importGpxTrack(String filePath);

    /**
     * Render the map with a route overlay and POI markers.
     *
     * @param width          output image width
     * @param height         output image height
     * @param lat            center latitude
     * @param lon            center longitude
     * @param angle          map rotation angle
     * @param magnification  map magnification
     * @param routeLats      route polyline latitudes (can be null)
     * @param routeLons      route polyline longitudes (can be null)
     * @param favoriteLats   favorite marker latitudes (can be null)
     * @param favoriteLons   favorite marker longitudes (can be null)
     * @param searchSelLat   search selection latitude (-1 if none)
     * @param searchSelLon   search selection longitude (-1 if none)
     * @param trackLats      track polyline latitudes (can be null)
     * @param trackLons      track polyline longitudes (can be null)
     * @return ARGB pixel array, or null on error
     */
    public native int[] renderWithRouteAndPois(
        int width, int height,
        double lat, double lon, double angle, int magnification,
        double[] routeLats, double[] routeLons,
        double[] favoriteLats, double[] favoriteLons,
        double searchSelLat, double searchSelLon,
        double[] trackLats, double[] trackLons);

    /**
     * Render the map with a route overlay.
     *
     * @param width         output image width
     * @param height        output image height
     * @param lat           center latitude
     * @param lon           center longitude
     * @param angle         map rotation angle
     * @param magnification map magnification
     * @param routeLats     route polyline latitudes (can be null)
     * @param routeLons     route polyline longitudes (can be null)
     * @return ARGB pixel array, or null on error
     */
    public native int[] renderWithRoute(
        int width, int height,
        double lat, double lon, double angle, int magnification,
        double[] routeLats, double[] routeLons);

    /**
     * Set or hide the GPS location marker that is drawn on top of the map during
     * the next render. The marker is rendered in the same native pass as the map,
     * so it always uses the exact same projection and cannot drift relative to the
     * road. Call with {@code Double.NaN} for latitude to hide the marker.
     *
     * @param lat     marker latitude in degrees, or NaN to hide
     * @param lon     marker longitude in degrees
     * @param bearing marker bearing in degrees, 0 = north, clockwise, or negative to hide arrow
     * @param accuracy horizontal accuracy in meters, or negative/zero to hide accuracy circle
     */
    public native void setGpsMarker(double lat, double lon, double bearing, double accuracy);

    /**
     * Project a geographic coordinate to screen pixel coordinates.
     *
     * @param width        output image width
     * @param height       output image height
     * @param centerLat    center latitude
     * @param centerLon    center longitude
     * @param magnification map magnification
     * @param dpi          screen DPI
     * @param angle        map rotation angle
     * @param lat          latitude to project
     * @param lon          longitude to project
     * @return [pixelX, pixelY] or null on error
     */
    public native double[] projectToPixel(
        int width, int height,
        double centerLat, double centerLon,
        int magnification, double dpi, double angle,
        double lat, double lon);

    /**
     * Load favorite locations from a JSON file.
     *
     * @param filePath path to the favorites JSON file
     * @return true if loaded successfully
     */
    public native boolean loadFavoriteLocations(String filePath);

    /**
     * Save favorite locations to a JSON file.
     *
     * @param filePath path to write the favorites JSON file
     * @param groups   array of favorite location groups
     * @return true if saved successfully
     */
    public native boolean saveFavoriteLocations(
        String filePath, FavoriteLocationGroup[] groups);

    /**
     * Get all favorite location groups.
     *
     * @return array of favorite groups
     */
    public native FavoriteLocationGroup[] getFavoriteGroups();

    /**
     * Add a new favorite group.
     *
     * @param name group name
     * @return true if added successfully
     */
    public native boolean addGroup(String name);

    /**
     * Delete a favorite group.
     *
     * @param name group name
     * @return true if deleted successfully
     */
    public native boolean deleteGroup(String name);

    /**
     * Rename a favorite group.
     *
     * @param oldName current group name
     * @param newName new group name
     * @return true if renamed successfully
     */
    public native boolean renameGroup(String oldName, String newName);

    /**
     * Add a favorite location to a group.
     *
     * @param groupName group name
     * @param favName   favorite name
     * @param lat       latitude
     * @param lon       longitude
     * @return true if added successfully
     */
    public native boolean addFavorite(
        String groupName, String favName, double lat, double lon);

    /**
     * Delete a favorite location from a group.
     *
     * @param groupName group name
     * @param favName   favorite name
     * @return true if deleted successfully
     */
    public native boolean deleteFavorite(
        String groupName, String favName);

    /**
     * Rename a favorite location.
     *
     * @param groupName group name
     * @param oldName   current name
     * @param newName   new name
     * @return true if renamed successfully
     */
    public native boolean renameFavorite(
        String groupName, String oldName, String newName);

    /**
     * Set or clear the starred flag on a favorite.
     *
     * @param groupName group name
     * @param favName   favorite name
     * @param starred   true to star, false to unstar
     * @return true if updated, false if group or fav not found
     */
    public native boolean setStarred(String groupName, String favName, boolean starred);

    /**
     * Check if a favorite is starred.
     *
     * @param groupName group name
     * @param favName   favorite name
     * @return true if starred, false otherwise
     */
    public native boolean isStarred(String groupName, String favName);

    /**
     * Set or clear the color of a group.
     * Color is a 6-character hex RGB string (e.g. "FF5733").
     * Pass empty string to clear.
     *
     * @param groupName group name
     * @param color     6-char hex RGB string, or empty to clear
     * @return true if set, false if group not found
     */
    public native boolean setGroupColor(String groupName, String color);

    /**
     * Get the color of a group.
     *
     * @param groupName group name
     * @return color string (6 hex chars) or empty if no color or group not found
     */
    public native String getGroupColor(String groupName);

    /**
     * Get the {@link MapDownloadManager} for downloading maps from providers.
     *
     * @return the map download manager
     */
    public MapDownloadManager getMapDownloadManager() {
        if (mapDownloadManager == null) {
            mapDownloadManager = new MapDownloadManager(this);
        }
        return mapDownloadManager;
    }

    private MapDownloadManager mapDownloadManager;
}
