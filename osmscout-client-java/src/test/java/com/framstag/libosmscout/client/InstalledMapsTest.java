package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the installed-map scan the phone UI and the car session share.
 * <p>
 * Both surfaces list what this scan returns, and the scan is the only place that decides what an installed
 * map is: a directory holding {@code types.dat}, at any depth, with the basemap overlay skipped. The class
 * was not exercised anywhere in the module's suite before this test.
 */
class InstalledMapsTest {

    @Test
    void everyDirectoryHoldingTypesDatIsAMap() throws Exception {
        Path root = Files.createDirectories(Files.createTempDirectory("installed-maps").resolve("maps"));
        Path flensburg = Files.createDirectories(root.resolve("Flensburg"));
        Files.writeString(flensburg.resolve(InstalledMaps.TYPES_DAT), "types");
        Path nested = Files.createDirectories(root.resolve("de").resolve("Paderborn"));
        Files.writeString(nested.resolve(InstalledMaps.TYPES_DAT), "types");
        Path notAMap = Files.createDirectories(root.resolve("empty"));

        List<String> maps = InstalledMaps.findDatabaseDirectories(root.toString(), null);

        assertEquals(2, maps.size(), "one entry per database directory: " + maps);
        assertTrue(maps.contains(flensburg.toAbsolutePath().toString()));
        assertTrue(maps.contains(nested.toAbsolutePath().toString()));
        assertFalse(maps.contains(notAMap.toAbsolutePath().toString()),
            "a directory without " + InstalledMaps.TYPES_DAT + " is not a map");
    }

    @Test
    void theExcludedDirectoryIsSkippedWithItsWholeSubtree() throws Exception {
        Path root = Files.createDirectories(Files.createTempDirectory("installed-maps").resolve("maps"));
        Path basemap = Files.createDirectories(root.resolve("basemap"));
        Files.writeString(basemap.resolve(InstalledMaps.TYPES_DAT), "types");
        Path world = Files.createDirectories(basemap.resolve("world"));
        Files.writeString(world.resolve(InstalledMaps.TYPES_DAT), "types");
        Path flensburg = Files.createDirectories(root.resolve("Flensburg"));
        Files.writeString(flensburg.resolve(InstalledMaps.TYPES_DAT), "types");

        List<String> maps = InstalledMaps.findDatabaseDirectories(
            root.toString(), basemap.toAbsolutePath().toString());

        assertEquals(List.of(flensburg.toAbsolutePath().toString()), maps);
    }

    @Test
    void aRootThatIsNoDirectoryHoldsNoMap() {
        assertTrue(InstalledMaps.findDatabaseDirectories(
            "/no/such/directory/for/an/installed/map", null).isEmpty());
    }
}
