package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the installed-basemap half of {@link BasemapManager}: what the settings screen shows and
 * what the delete action removes.
 * <p>
 * The existing {@code BasemapManagerTest} drives the listing parse and the download worker; the installed
 * side — the {@code BasemapInfo} value the settings screen prints, the size, the installed test and the
 * delete — was not exercised at all, so a wrong unit or a delete that leaves the directory behind was
 * invisible to the suite.
 */
class BasemapManagerInstalledTest {

    private static BasemapManager manager(Path mapsDir) {
        return new BasemapManager(new MapProvider("test", "https://example.com", ""), mapsDir);
    }

    @Test
    void theInstalledInfoCountsFilesAndSumsTheirSizes() throws Exception {
        Path mapsDir = Files.createTempDirectory("basemap-installed");
        try {
            Path basemapDir = Files.createDirectories(mapsDir.resolve("basemap"));
            Files.write(basemapDir.resolve("water.idx"), new byte[10]);
            Files.write(basemapDir.resolve("world.osmscout"), new byte[90]);

            BasemapManager.BasemapInfo info = manager(mapsDir).getInstalledBasemapInfo();

            assertNotNull(info);
            assertEquals(2, info.getFileCount());
            assertEquals(100L, info.getSizeBytes());
            assertEquals(basemapDir.toAbsolutePath().toString(), info.getPath());
            assertNotNull(info.getLastModified());
            assertTrue(info.getLastModified().isBefore(LocalDateTime.now().plusMinutes(1)));
            assertEquals("BasemapInfo{size=100 B, files=2}", info.toString());
        } finally {
            deleteTree(mapsDir);
        }
    }

    @Test
    void theHumanSizeNamesItsUnit() {
        assertEquals("512 B", info(512, 1).getSizeHuman());
        assertEquals("2 KB", info(2048, 1).getSizeHuman());
        assertEquals("2.5 MB", info(2_621_440, 1).getSizeHuman());
        assertEquals("3.00 GB", info(3L * 1024 * 1024 * 1024, 1).getSizeHuman());
    }

    @Test
    void noInstalledBasemapMeansNoInfoAndNoSize() throws Exception {
        Path mapsDir = Files.createTempDirectory("basemap-missing");
        try {
            BasemapManager manager = manager(mapsDir);

            assertNull(manager.getInstalledBasemapInfo());
            assertEquals(0L, manager.getInstalledBasemapSize());
            assertFalse(manager.isBasemapInstalled());
        } finally {
            deleteTree(mapsDir);
        }
    }

    @Test
    void anInstalledBasemapIsADirectoryHoldingMapData() throws Exception {
        Path mapsDir = Files.createTempDirectory("basemap-present");
        try {
            Path basemapDir = Files.createDirectories(mapsDir.resolve("basemap"));
            assertFalse(manager(mapsDir).isBasemapInstalled(), "an empty directory is not an installed basemap");

            Files.write(basemapDir.resolve("water.idx"), new byte[7]);
            assertTrue(manager(mapsDir).isBasemapInstalled());
            assertEquals(7L, manager(mapsDir).getInstalledBasemapSize());
        } finally {
            deleteTree(mapsDir);
        }
    }

    @Test
    void deletingTheBasemapRemovesTheWholeDirectory() throws Exception {
        Path mapsDir = Files.createTempDirectory("basemap-delete");
        try {
            Path basemapDir = Files.createDirectories(mapsDir.resolve("basemap").resolve("nested"));
            Files.write(basemapDir.resolve("water.idx"), new byte[3]);
            BasemapManager manager = manager(mapsDir);

            assertTrue(manager.deleteBasemap());

            assertFalse(Files.exists(mapsDir.resolve("basemap")));
            assertFalse(manager.deleteBasemap(), "nothing is left to delete the second time");
        } finally {
            deleteTree(mapsDir);
        }
    }

    @Test
    void anUpdateIsOnlyLookedForWhenABasemapIsInstalled() throws Exception {
        Path mapsDir = Files.createTempDirectory("basemap-update");
        try {
            assertFalse(manager(mapsDir).isUpdateAvailable(),
                "with nothing installed there is nothing to update, and the provider is not asked");
        } finally {
            deleteTree(mapsDir);
        }
    }

    private static BasemapManager.BasemapInfo info(long sizeBytes, int fileCount) {
        return new BasemapManager.BasemapInfo(
            sizeBytes, fileCount, LocalDateTime.now(), "/maps/basemap");
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        } catch (Exception ignored) {
        }
    }
}
