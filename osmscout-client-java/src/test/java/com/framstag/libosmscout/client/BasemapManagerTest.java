package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BasemapManager} directory listing parsing.
 */
class BasemapManagerTest {

    private static final String SAMPLE_LISTING =
        "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">\n" +
        "<html><head><title>Index of /basemap</title></head><body>\n" +
        "<h1>Index of /basemap</h1>\n" +
        "<table>\n" +
        "<tr><th valign=\"top\"><img src=\"/icons/blank.gif\" alt=\"[ICO]\"></th><th><a href=\"?C=N;O=D\">Name</a></th><th><a href=\"?C=M;O=A\">Last modified</a></th><th><a href=\"?C=S;O=A\">Size</a></th><th><a href=\"?C=D;O=A\">Description</a></th></tr>\n" +
        "<tr><th colspan=\"5\"><hr></th></tr>\n" +
        "<tr><td valign=\"top\"><img src=\"/icons/back.gif\" alt=\"[PARENTDIR]\"></td><td><a href=\"/\">Parent Directory</a></td><td>&nbsp;</td><td align=\"right\">  - </td><td>&nbsp;</td></tr>\n" +
        "<tr><td valign=\"top\"><img src=\"/icons/compressed.gif\" alt=\"[   ]\"></td><td><a href=\"BaseMap-2026-02-15.tar.gz\">BaseMap-2026-02-15.tar.gz</a></td><td align=\"right\">2026-02-15 23:24  </td><td align=\"right\">769K</td><td>&nbsp;</td></tr>\n" +
        "<tr><td valign=\"top\"><img src=\"/icons/compressed.gif\" alt=\"[   ]\"></td><td><a href=\"BaseMap-2026-02-23.tar.gz\">BaseMap-2026-02-23.tar.gz</a></td><td align=\"right\">2026-02-24 00:16  </td><td align=\"right\"> 39M</td><td>&nbsp;</td></tr>\n" +
        "<tr><td valign=\"top\"><img src=\"/icons/compressed.gif\" alt=\"[   ]\"></td><td><a href=\"BaseMap-minimal-2026-02-23.tar.gz\">BaseMap-minimal-2026-02-23.tar.gz</a></td><td align=\"right\">2026-02-24 00:15  </td><td align=\"right\">2.4M</td><td>&nbsp;</td></tr>\n" +
        "<tr><td valign=\"top\"><img src=\"/icons/compressed.gif\" alt=\"[   ]\"></td><td><a href=\"basemap.tar.gz\">basemap.tar.gz</a></td><td align=\"right\">2026-01-29 12:41  </td><td align=\"right\"> 28M</td><td>&nbsp;</td></tr>\n" +
        "<tr><td valign=\"top\"><img src=\"/icons/unknown.gif\" alt=\"[   ]\"></td><td><a href=\"planet_extract.osm.pbf\">planet_extract.osm.pbf</a></td><td align=\"right\">2026-01-29 11:12  </td><td align=\"right\">1.1G</td><td>&nbsp;</td></tr>\n" +
        "</table>\n" +
        "</body></html>";

    @Test
    void testParseDirectoryListingReturnsTarGzOnly() {
        List<BasemapManager.BasemapArchive> archives = BasemapManager.parseDirectoryListing(SAMPLE_LISTING);
        // Should only include .tar.gz files, not .osm.pbf
        assertEquals(4, archives.size());
    }

    @Test
    void testParseDirectoryListingNames() {
        List<BasemapManager.BasemapArchive> archives = BasemapManager.parseDirectoryListing(SAMPLE_LISTING);
        // Sorted by date desc: BaseMap-2026-02-23 (2026-02-24), BaseMap-minimal-2026-02-23 (2026-02-24), BaseMap-2026-02-15 (2026-02-15), basemap.tar.gz (2026-01-29)
        assertEquals("BaseMap-2026-02-23.tar.gz", archives.get(0).getFileName());
        assertEquals("BaseMap-minimal-2026-02-23.tar.gz", archives.get(1).getFileName());
        assertEquals("BaseMap-2026-02-15.tar.gz", archives.get(2).getFileName());
        assertEquals("basemap.tar.gz", archives.get(3).getFileName());
    }

    @Test
    void testParseDirectoryListingSizes() {
        List<BasemapManager.BasemapArchive> archives = BasemapManager.parseDirectoryListing(SAMPLE_LISTING);
        // Sorted by date desc: 39M, 2.4M, 769K, 28M
        assertEquals(39L * 1024 * 1024, archives.get(0).getSizeBytes());
        assertEquals(2_516_582L, archives.get(1).getSizeBytes(), 1024); // 2.4M ≈ 2,516,582
        assertEquals(769L * 1024, archives.get(2).getSizeBytes());
        assertEquals(28L * 1024 * 1024, archives.get(3).getSizeBytes());
    }

    @Test
    void testParseDirectoryListingDates() {
        List<BasemapManager.BasemapArchive> archives = BasemapManager.parseDirectoryListing(SAMPLE_LISTING);
        assertNotNull(archives.get(0).getLastModified());
        assertEquals(2026, archives.get(0).getLastModified().getYear());
        assertEquals(2, archives.get(0).getLastModified().getMonthValue());
        assertEquals(24, archives.get(0).getLastModified().getDayOfMonth());
    }

    @Test
    void testParseEmptyListing() {
        List<BasemapManager.BasemapArchive> archives = BasemapManager.parseDirectoryListing("<html></html>");
        assertTrue(archives.isEmpty());
    }

    @Test
    void testParseListingWithNoTarGz() {
        String listing = "<a href=\"readme.txt\">readme.txt</a> 2026-01-01 12:00 1K";
        List<BasemapManager.BasemapArchive> archives = BasemapManager.parseDirectoryListing(listing);
        assertTrue(archives.isEmpty());
    }

    @Test
    void testArchiveIsMinimal() {
        List<BasemapManager.BasemapArchive> archives = BasemapManager.parseDirectoryListing(SAMPLE_LISTING);
        assertFalse(archives.get(0).isMinimal()); // BaseMap-2026-02-23
        assertTrue(archives.get(1).isMinimal());  // BaseMap-minimal-2026-02-23
        assertFalse(archives.get(2).isMinimal()); // BaseMap-2026-02-15
        assertFalse(archives.get(3).isMinimal()); // basemap.tar.gz
    }

    @Test
    void testArchiveLabel() {
        List<BasemapManager.BasemapArchive> archives = BasemapManager.parseDirectoryListing(SAMPLE_LISTING);
        assertTrue(archives.get(0).getLabel().startsWith("Full"));
        assertTrue(archives.get(1).getLabel().startsWith("Minimal"));
    }

    @Test
    void testArchiveSizeHuman() {
        List<BasemapManager.BasemapArchive> archives = BasemapManager.parseDirectoryListing(SAMPLE_LISTING);
        assertEquals("39.0 MB", archives.get(0).getSizeHuman());
    }

    @Test
    void testSortOrderNewestFirst() {
        List<BasemapManager.BasemapArchive> archives = BasemapManager.parseDirectoryListing(SAMPLE_LISTING);
        // Newest first: 2026-02-24, 2026-02-15, 2026-02-24 (minimal), 2026-01-29
        assertTrue(archives.get(0).getLastModified().isAfter(archives.get(1).getLastModified()) ||
                   archives.get(0).getLastModified().isEqual(archives.get(1).getLastModified()));
    }

    @Test
    void testGetBasemapDirectory() {
        BasemapManager mgr = new BasemapManager(
            new MapProvider("test", "https://example.com", ""),
            java.nio.file.Paths.get("/tmp/maps"));
        assertEquals(java.nio.file.Paths.get("/tmp/maps/basemap"), mgr.getBasemapDirectory());
    }

    @Test
    void testGetBasemapBaseUrl() {
        BasemapManager mgr = new BasemapManager(
            new MapProvider("test", "https://example.com", ""),
            java.nio.file.Paths.get("/tmp/maps"));
        assertEquals("https://example.com/basemap/", mgr.getBasemapBaseUrl());
    }

    @Test
    void testExtractTarGz() throws Exception {
        // Create a small tar.gz with one file
        java.nio.file.Path tempTar = java.nio.file.Files.createTempFile("test", ".tar.gz");
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("extract-test");
        try {
            // Create a tar file in memory: one file "hello.txt" with content "Hello World"
            byte[] tarData = createSimpleTar("hello.txt", "Hello World!".getBytes());
            // GZip it
            java.io.ByteArrayOutputStream gzOut = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(gzOut)) {
                gz.write(tarData);
            }
            java.nio.file.Files.write(tempTar, gzOut.toByteArray());

            // Extract
            BasemapManager.extractTarGz(tempTar, tempDir);

            // Verify
            java.nio.file.Path extracted = tempDir.resolve("hello.txt");
            assertTrue(java.nio.file.Files.exists(extracted));
            String content = new String(java.nio.file.Files.readAllBytes(extracted));
            assertEquals("Hello World!", content);
        } finally {
            deleteDirectory(tempTar);
            deleteDirectory(tempDir);
        }
    }

    @Test
    void testExtractTarGzWithDirectory() throws Exception {
        java.nio.file.Path tempTar = java.nio.file.Files.createTempFile("test", ".tar.gz");
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("extract-test");
        try {
            // Create tar with a directory entry and a file inside
            byte[] tarData = createSimpleTarWithDir("subdir/", "subdir/file.txt", "Nested content".getBytes());
            java.io.ByteArrayOutputStream gzOut = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(gzOut)) {
                gz.write(tarData);
            }
            java.nio.file.Files.write(tempTar, gzOut.toByteArray());

            BasemapManager.extractTarGz(tempTar, tempDir);

            assertTrue(java.nio.file.Files.isDirectory(tempDir.resolve("subdir")));
            assertTrue(java.nio.file.Files.exists(tempDir.resolve("subdir/file.txt")));
            String content = new String(java.nio.file.Files.readAllBytes(tempDir.resolve("subdir/file.txt")));
            assertEquals("Nested content", content);
        } finally {
            deleteDirectory(tempTar);
            deleteDirectory(tempDir);
        }
    }

    @Test
    void testDeleteBasemap() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("basemap-test");
        java.nio.file.Path subFile = tempDir.resolve("test.txt");
        java.nio.file.Files.write(subFile, "data".getBytes());

        BasemapManager mgr = new BasemapManager(
            new MapProvider("test", "https://example.com", ""),
            tempDir.getParent());

        // The basemap dir is tempDir.getParent()/basemap, not tempDir itself
        // So let's test deleteDirectory via the extract cleanup path
        assertTrue(java.nio.file.Files.exists(tempDir));
        // Use reflection to test the private method... or just test via the public API
        // Actually let's just test that deleteBasemap returns false when no basemap
        assertFalse(mgr.deleteBasemap());
    }

    @Test
    void testIsBasemapInstalledNoDir() {
        BasemapManager mgr = new BasemapManager(
            new MapProvider("test", "https://example.com", ""),
            java.nio.file.Paths.get("/nonexistent/path"));
        assertFalse(mgr.isBasemapInstalled());
    }

    @Test
    void testIsUpdateAvailableNoBasemap() {
        BasemapManager mgr = new BasemapManager(
            new MapProvider("test", "https://example.com", ""),
            java.nio.file.Paths.get("/nonexistent/path"));
        assertFalse(mgr.isUpdateAvailable());
    }

    @Test
    void testDeleteBasemapRemovesDirectory() throws Exception {
        java.nio.file.Path mapsDir = java.nio.file.Files.createTempDirectory("basemap-maps");
        java.nio.file.Path basemapDir = mapsDir.resolve("basemap");
        java.nio.file.Files.createDirectories(basemapDir);
        java.nio.file.Files.write(basemapDir.resolve("water.idx"), "data".getBytes());

        BasemapManager mgr = new BasemapManager(
            new MapProvider("test", "https://example.com", ""),
            mapsDir);

        assertTrue(mgr.isBasemapInstalled());
        assertTrue(mgr.deleteBasemap());
        assertFalse(java.nio.file.Files.exists(basemapDir));
        assertFalse(mgr.isBasemapInstalled());
        // Second delete returns false (nothing installed)
        assertFalse(mgr.deleteBasemap());
    }

    @Test
    void testDownloadFailureCleansUpAndKeepsExisting() throws Exception {
        // Local HTTP server returning 404 for the archive
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/basemap/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            java.nio.file.Path mapsDir = java.nio.file.Files.createTempDirectory("basemap-maps");
            java.nio.file.Path basemapDir = mapsDir.resolve("basemap");
            java.nio.file.Files.createDirectories(basemapDir);
            java.nio.file.Files.write(basemapDir.resolve("water.idx"), "old".getBytes());

            BasemapManager mgr = new BasemapManager(
                new MapProvider("test", "http://127.0.0.1:" + port, ""),
                mapsDir);

            java.util.concurrent.atomic.AtomicReference<String> error = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            MapDownloadListener listener = new MapDownloadListener() {
                @Override
                public void onError(String mapName, String errorMessage) {
                    error.set(errorMessage);
                    done.countDown();
                }
            };

            BasemapManager.BasemapArchive archive = new BasemapManager.BasemapArchive(
                "BaseMap-2026-02-23.tar.gz", 1000, null);
            mgr.downloadBasemap(archive, listener);

            assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS));
            assertNotNull(error.get());
            // Existing basemap untouched
            assertTrue(java.nio.file.Files.exists(basemapDir.resolve("water.idx")));
            assertEquals("old", new String(java.nio.file.Files.readAllBytes(basemapDir.resolve("water.idx"))));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testCancelDownloadCleansUp() throws Exception {
        // Local HTTP server streaming a large payload slowly
        byte[] payload = new byte[8 * 1024 * 1024];
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/basemap/", exchange -> {
            exchange.sendResponseHeaders(200, payload.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) {
                for (int i = 0; i < payload.length; i += 8192) {
                    out.write(payload, i, Math.min(8192, payload.length - i));
                    out.flush();
                    Thread.sleep(5);
                }
            } catch (Exception ignored) {
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            java.nio.file.Path mapsDir = java.nio.file.Files.createTempDirectory("basemap-maps");

            BasemapManager mgr = new BasemapManager(
                new MapProvider("test", "http://127.0.0.1:" + port, ""),
                mapsDir);

            java.util.concurrent.atomic.AtomicReference<String> error = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.CountDownLatch progressSeen = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            MapDownloadListener listener = new MapDownloadListener() {
                @Override
                public void onProgress(String mapName, long bytesDownloaded, long totalBytes) {
                    if (bytesDownloaded > 0) {
                        progressSeen.countDown();
                    }
                }

                @Override
                public void onError(String mapName, String errorMessage) {
                    error.set(errorMessage);
                    done.countDown();
                }
            };

            BasemapManager.BasemapArchive archive = new BasemapManager.BasemapArchive(
                "BaseMap-2026-02-23.tar.gz", payload.length, null);
            String handle = mgr.downloadBasemap(archive, listener);

            assertTrue(progressSeen.await(10, java.util.concurrent.TimeUnit.SECONDS));
            mgr.cancelDownload(handle);

            assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("Download cancelled", error.get());
            // No basemap installed after cancelled download
            assertFalse(mgr.isBasemapInstalled());
            assertFalse(java.nio.file.Files.exists(mgr.getBasemapDirectory()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testLiveServerListingIncludesNewNaming() throws Exception {
        String html = new String(
            BasemapManagerTest.class.getResourceAsStream("/basemap-listing-live.html").readAllBytes(),
            "UTF-8");
        List<BasemapManager.BasemapArchive> archives = BasemapManager.parseDirectoryListing(html);

        // New naming pattern basemap-YYMMDD-dist.tar.gz must be found
        BasemapManager.BasemapArchive newest = archives.get(0);
        assertEquals("basemap-260803-dist.tar.gz", newest.getFileName());
        assertEquals(39L * 1024 * 1024, newest.getSizeBytes());
        assertFalse(newest.isMinimal());
        // Filename-encoded date 260803 -> 2026-08-03
        assertNotNull(newest.getVersionDate());
        assertEquals(2026, newest.getVersionDate().getYear());
        assertEquals(8, newest.getVersionDate().getMonthValue());
        assertEquals(3, newest.getVersionDate().getDayOfMonth());
    }

    @Test
    void testVersionDateParsing() {
        assertEquals(java.time.LocalDate.of(2026, 8, 3),
            BasemapManager.parseVersionDate("basemap-260803-dist.tar.gz"));
        assertEquals(java.time.LocalDate.of(2026, 2, 23),
            BasemapManager.parseVersionDate("BaseMap-2026-02-23.tar.gz"));
        assertNull(BasemapManager.parseVersionDate("basemap.tar.gz"));
    }

    // ---- Helpers ----

    private static void deleteDirectory(java.nio.file.Path dir) {
        try {
            java.nio.file.Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try { java.nio.file.Files.deleteIfExists(p); }
                    catch (Exception ignored) {}
                });
        } catch (Exception ignored) {}
    }

    /** Create a simple tar archive with one file. */
    private static byte[] createSimpleTar(String fileName, byte[] content) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        writeTarHeader(out, fileName, content.length, '0');
        out.write(content);
        padTo512(out, content.length);
        // Two zero blocks for end-of-archive
        out.write(new byte[1024]);
        return out.toByteArray();
    }

    /** Create a tar with a directory entry and a file inside. */
    private static byte[] createSimpleTarWithDir(String dirName, String fileName, byte[] content) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        // Directory entry
        writeTarHeader(out, dirName, 0, '5');
        padTo512(out, 0);
        // File entry
        writeTarHeader(out, fileName, content.length, '0');
        out.write(content);
        padTo512(out, content.length);
        // End-of-archive
        out.write(new byte[1024]);
        return out.toByteArray();
    }

    private static void writeTarHeader(java.io.OutputStream out, String name, long size, char typeFlag) throws Exception {
        byte[] header = new byte[512];
        // Name (bytes 0-99)
        byte[] nameBytes = name.getBytes("ASCII");
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 100));
        // Size (bytes 124-135, octal)
        String sizeStr = Long.toOctalString(size);
        byte[] sizeBytes = sizeStr.getBytes("ASCII");
        System.arraycopy(sizeBytes, 0, header, 124, sizeBytes.length);
        // Type flag (byte 156)
        header[156] = (byte) typeFlag;
        // Checksum placeholder (bytes 148-155, spaces)
        for (int i = 148; i < 156; i++) header[i] = (byte) ' ';
        // Calculate checksum (bytes 0-511)
        int checksum = 0;
        for (int i = 0; i < 512; i++) checksum += (header[i] & 0xFF);
        String chkStr = String.format("%06o", checksum);
        byte[] chkBytes = chkStr.getBytes("ASCII");
        System.arraycopy(chkBytes, 0, header, 148, chkBytes.length);
        header[155] = 0;
        out.write(header);
    }

    private static void padTo512(java.io.OutputStream out, long dataSize) throws Exception {
        long padding = (512 - (dataSize % 512)) % 512;
        if (padding > 0) {
            out.write(new byte[(int) padding]);
        }
    }
}
