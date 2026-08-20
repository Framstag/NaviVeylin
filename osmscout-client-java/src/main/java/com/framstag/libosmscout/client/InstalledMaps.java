package com.framstag.libosmscout.client;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates installed map databases under a root directory.
 *
 * Shared by the phone UI ({@code MapCanvasViewModel.initMap}) and the Android
 * Auto session ({@code AutoServiceModule}) so both see the exact same set of
 * maps. Mirrors the native {@code MapManager::LookupDatabases} scan: a map
 * database is any directory (at any depth) containing a {@code types.dat}.
 * Map names may contain '/', so the layout is not guaranteed to be flat.
 */
public final class InstalledMaps {

    /** OSMScout database marker file (see {@code osmscout::TypeConfig::FILE_TYPES_DAT}). */
    public static final String TYPES_DAT = "types.dat";

    private InstalledMaps() {
    }

    /**
     * Find every database directory under {@code mapsDir}.
     *
     * @param mapsDir    root directory to scan (e.g. {@code <filesDir>/maps})
     * @param excludeDir absolute path of a directory to skip, or {@code null}
     *                   (e.g. the basemap overlay, which the client loads
     *                   separately via its basemap lookup directory)
     * @return absolute paths of all database directories, never {@code null}
     */
    public static List<String> findDatabaseDirectories(String mapsDir, String excludeDir) {
        List<String> result = new ArrayList<>();
        File root = new File(mapsDir);
        if (!root.isDirectory()) {
            return result;
        }
        scan(root, excludeDir, result);
        return result;
    }

    private static void scan(File dir, String excludeDir, List<String> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (!child.isDirectory()) {
                continue;
            }
            if (excludeDir != null && child.getAbsolutePath().equals(excludeDir)) {
                continue;
            }
            if (new File(child, TYPES_DAT).isFile()) {
                out.add(child.getAbsolutePath());
            } else {
                scan(child, excludeDir, out);
            }
        }
    }
}
