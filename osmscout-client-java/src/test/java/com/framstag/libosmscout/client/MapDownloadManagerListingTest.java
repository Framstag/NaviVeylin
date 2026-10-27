package com.framstag.libosmscout.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the parts of {@link MapDownloadManager} that never reach the native side: the provider
 * listing URL, the empty answer a failing listing yields, and the refusal of a directory entry.
 * <p>
 * The listing URL is what a provider's version bounds are addressed with, and the app treats "no maps" as
 * a provider that answered badly rather than as an exception — both were untested, so a template that
 * dropped a placeholder or a failure that escaped as an exception would only show on the phone.
 */
class MapDownloadManagerListingTest {

    @Test
    void listingUrlSubstitutesTheDatabaseFormatVersionAndTheLocale() {
        MapProvider provider = new MapProvider("test", "https://example.com",
            "https://example.com/list?min=%1&max=%2&lang=%3");

        String url = MapDownloadManager.listingUrl(provider);

        assertEquals("https://example.com/list?min=" + MapDownloadManager.DATABASE_FORMAT_VERSION
            + "&max=" + MapDownloadManager.DATABASE_FORMAT_VERSION + "&lang=en", url);
    }

    @Test
    void aProviderThatAnswersWithAnHttpErrorYieldsNoMaps() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/list", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            MapProvider provider = new MapProvider("test",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/list");

            List<AvailableMapEntry> maps = new MapDownloadManager(null).fetchAvailableMaps(provider);

            assertTrue(maps.isEmpty(), "a provider that answered with an error lists no map");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void anUnreachableProviderYieldsNoMaps() {
        MapProvider provider = new MapProvider("test", "http://127.0.0.1:1",
            "http://127.0.0.1:1/list");

        assertTrue(new MapDownloadManager(null).fetchAvailableMaps(provider).isEmpty());
    }

    @Test
    void aDirectoryEntryIsRefusedWithItsOwnName() throws Exception {
        Path target = Files.createTempDirectory("map-download-target");
        AvailableMapEntry directory = new AvailableMapEntry("Germany", List.of("Germany"), "a region");

        AtomicReference<String> reported = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        MapDownloadListener listener = new MapDownloadListener() {
            @Override
            public void onError(String mapName, String errorMessage) {
                reported.set(mapName + ": " + errorMessage);
                done.countDown();
            }
        };

        MapDownloadManager manager = new MapDownloadManager(null);
        manager.downloadMap(directory, target, listener);

        assertTrue(done.await(10, TimeUnit.SECONDS), "the worker reports instead of dying");
        assertEquals("Germany: Cannot download a directory entry: Germany", reported.get());
        manager.cancelDownload("no-such-handle");
    }

    @Test
    void aFailedNativePreparationIsReportedThroughTheListener() throws Exception {
        Path target = Files.createTempDirectory("map-download-target");
        MapProvider provider = new MapProvider("test", "http://127.0.0.1:1", "http://127.0.0.1:1/list");
        AvailableMapEntry leaf = new AvailableMapEntry(
            "Bremen", List.of("Bremen"), "a city", provider, 10L, "Bremen", 0L, 27);

        AtomicReference<String> reported = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        MapDownloadListener listener = new MapDownloadListener() {
            @Override
            public void onError(String mapName, String errorMessage) {
                reported.set(errorMessage);
                done.countDown();
            }
        };

        new MapDownloadManager(null).downloadMap(leaf, target, listener);

        assertTrue(done.await(10, TimeUnit.SECONDS),
            "a worker that dies is a download error the caller sees, not an uncaught throwable");
        assertTrue(reported.get().contains("nativePrepareMapDirectory"),
            "the message names the native step that failed: " + reported.get());
    }
}
