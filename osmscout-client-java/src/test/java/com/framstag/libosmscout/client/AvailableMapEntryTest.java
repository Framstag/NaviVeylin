package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the map-list entry the download screen shows and the provider it downloads from.
 * <p>
 * The list the server returns is turned into this tree by the native parse step, and the app walks it by
 * name and reads the size and provider off each leaf on the tap. None of the accessors, the search or the
 * version date was exercised by the module's suite.
 */
class AvailableMapEntryTest {

    @Test
    void aDirectoryEntryCarriesItsPathAndNoDownloadTarget() {
        AvailableMapEntry directory = new AvailableMapEntry(
            "Germany", List.of("Europe", "Germany"), "a region");

        assertTrue(directory.isDirectory());
        assertEquals("Germany", directory.getName());
        assertEquals(List.of("Europe", "Germany"), directory.getPath());
        assertEquals("a region", directory.getDescription());
        assertNull(directory.getProvider());
        assertEquals(0L, directory.getSize());
        assertNull(directory.getServerDirectory());
        assertEquals(0L, directory.getCreationTimestamp());
        assertEquals(-1, directory.getVersion());
        assertTrue(directory.getChildren().isEmpty());
    }

    @Test
    void aLeafEntryCarriesWhatTheDownloadNeeds() {
        MapProvider provider = new MapProvider("test", "https://example.com", "https://example.com/list");
        AvailableMapEntry leaf = new AvailableMapEntry(
            "Bremen", List.of("Bremen"), "a city", provider, 1234L, "Bremen", 1_700_000_000L, 27);

        assertFalse(leaf.isDirectory());
        assertSame(provider, leaf.getProvider());
        assertEquals(1234L, leaf.getSize());
        assertEquals("Bremen", leaf.getServerDirectory());
        assertEquals(1_700_000_000L, leaf.getCreationTimestamp());
        assertEquals(27, leaf.getVersion());
    }

    @Test
    void aMissingPathIsTheEmptyPath() {
        AvailableMapEntry entry = new AvailableMapEntry("Bremen", null, null);

        assertTrue(entry.getPath().isEmpty());
        assertNull(entry.getDescription());
    }

    @Test
    void anEntryFindsItselfByNameAndNothingElse() {
        AvailableMapEntry directory = new AvailableMapEntry("Germany", List.of("Germany"), "");

        assertSame(directory, directory.findEntryByName("Germany"));
        assertSame(directory, directory.findEntryByName("germany"), "the search is case-insensitive");
        assertNull(directory.findEntryByName("Bremen"));
    }

    @Test
    void anEntryNamesItselfAndWhetherItIsADirectory() {
        assertEquals("AvailableMapEntry{name='Germany', dir=true}",
            new AvailableMapEntry("Germany", List.of("Germany"), "").toString());
    }

    @Test
    void aBasemapArchiveNamesItsVariantAndItsVersionDate() {
        BasemapManager.BasemapArchive full = new BasemapManager.BasemapArchive(
            "BaseMap-2026-02-23.tar.gz", 39L * 1024 * 1024, LocalDateTime.of(2026, 2, 24, 0, 16));
        BasemapManager.BasemapArchive minimal = new BasemapManager.BasemapArchive(
            "basemap-260803-minimal.tar.gz", 512, LocalDateTime.of(2026, 8, 3, 12, 0));

        assertEquals("Full — BaseMap-2026-02-23.tar.gz (39.0 MB)", full.getLabel());
        assertEquals("Minimal — basemap-260803-minimal.tar.gz (512 B)", minimal.getLabel());
        assertEquals(LocalDate.of(2026, 2, 23), full.getVersionDate());
        assertEquals(LocalDate.of(2026, 8, 3), minimal.getVersionDate());
        assertEquals("BasemapArchive{name='BaseMap-2026-02-23.tar.gz', size=39.0 MB}", full.toString());
    }

    @Test
    void aProviderNamesItself() {
        MapProvider provider = new MapProvider("test", "https://example.com", "https://example.com/list");

        assertEquals("test", provider.getName());
        assertEquals("https://example.com", provider.getUri());
        assertEquals("https://example.com/list", provider.getListUri());
        assertEquals("MapProvider{name='test'}", provider.toString());
    }
}
