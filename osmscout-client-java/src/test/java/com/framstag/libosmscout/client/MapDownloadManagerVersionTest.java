package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the provider listing URL's version bounds.
 * <p>
 * Spec: map-download-infrastructure — "Database format version has one source of truth". The version
 * a listing is requested with and the version slot a repository database is addressed with must be
 * the same value, so both bounds come from {@link MapDownloadManager#DATABASE_FORMAT_VERSION}.
 */
class MapDownloadManagerVersionTest {

    private static final String LIST_URI =
        "https://example.com/latest.php?fromVersion=%1&toVersion=%2&locale=%3";

    private static MapProvider provider() {
        return new MapProvider("example.com", "https://example.com", LIST_URI);
    }

    @Test
    void listingRequestCarriesTheConstant() {
        String url = MapDownloadManager.listingUrl(provider());

        assertEquals("https://example.com/latest.php?fromVersion="
                     + MapDownloadManager.DATABASE_FORMAT_VERSION
                     + "&toVersion="
                     + MapDownloadManager.DATABASE_FORMAT_VERSION
                     + "&locale=en", url);
    }

    @Test
    void bothVersionBoundsUseOneValue() {
        String url = MapDownloadManager.listingUrl(provider());

        assertEquals(String.valueOf(MapDownloadManager.DATABASE_FORMAT_VERSION), queryValue(url, "fromVersion"));
        assertEquals(String.valueOf(MapDownloadManager.DATABASE_FORMAT_VERSION), queryValue(url, "toVersion"));
    }

    @Test
    void noPlaceholderSurvivesSubstitution() {
        String url = MapDownloadManager.listingUrl(provider());

        assertTrue(!url.contains("%1") && !url.contains("%2") && !url.contains("%3"),
                   "every placeholder must be substituted: " + url);
    }

    /** @return the value of {@code key} in {@code url}'s query string, or null when absent */
    private static String queryValue(String url, String key) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0) {
            return null;
        }
        for (String pair : url.substring(queryStart + 1).split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && pair.substring(0, equals).equals(key)) {
                return pair.substring(equals + 1);
            }
        }
        return null;
    }
}
