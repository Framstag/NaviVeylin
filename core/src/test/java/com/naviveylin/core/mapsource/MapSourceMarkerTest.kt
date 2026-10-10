package com.naviveylin.core.mapsource

import com.framstag.libosmscout.client.MapDownloadManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

/**
 * Unit tests for [MapSourceMarker].
 *
 * Spec: map-source-selection — "Installed map data records the source it came from" / "Downloaded map
 * carries its source" / "Pre-existing map is attributed to the built-in provider".
 */
class MapSourceMarkerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun roundTripsWithoutNetwork() {
        val directory = temporaryFolder.newFolder().toPath()
        val record = MapSourceRecord(MapSourceKind.REPOSITORY, "https://maps.example.org/repo", 27)

        MapSourceMarker.write(directory, record)
        val read = MapSourceMarker.read(directory)

        assertEquals(SourceMarkerRead.Present(record), read)
        assertTrue("the marker is a file inside the directory",
                   Files.isRegularFile(directory.resolve(MapSourceMarker.FILE_NAME)))
    }

    @Test
    fun repositoryMarkerCarriesBaseUrlAndVersion() {
        val directory = temporaryFolder.newFolder().toPath()

        MapSourceMarker.write(directory, MapSourceRecord(MapSourceKind.REPOSITORY, "https://maps.example.org/repo", 27))
        val record = MapSourceMarker.sourceOf(directory)

        assertEquals(MapSourceKind.REPOSITORY, record.sourceKind)
        assertEquals("https://maps.example.org/repo", record.baseUrl)
        assertEquals(27, record.databaseVersion)
        assertTrue(record.toSource().isRepository)
        assertEquals("https://maps.example.org/repo", record.toSource().baseUrl)
    }

    @Test
    fun builtInProviderMarkerHasNoBaseUrl() {
        val directory = temporaryFolder.newFolder().toPath()

        MapSourceMarker.write(directory, MapSourceRecord(MapSourceKind.BUILT_IN_PROVIDER, null, 27))
        val record = MapSourceMarker.sourceOf(directory)

        assertEquals(MapSourceKind.BUILT_IN_PROVIDER, record.sourceKind)
        assertNull(record.baseUrl)
        assertEquals(MapSource.BuiltInProvider, record.toSource())
    }

    @Test
    fun absentMarkerIsAttributedToTheBuiltInProvider() {
        val directory = temporaryFolder.newFolder().toPath()

        assertEquals(SourceMarkerRead.Absent, MapSourceMarker.read(directory))
        assertEquals(MapSourceRecord.BuiltInProvider, MapSourceMarker.sourceOf(directory))
    }

    @Test
    fun unreadableMarkerIsAttributedToTheBuiltInProviderAndReported() {
        val directory = temporaryFolder.newFolder().toPath()
        Files.write(directory.resolve(MapSourceMarker.FILE_NAME), "{not json".toByteArray())

        assertEquals(SourceMarkerRead.Unreadable, MapSourceMarker.read(directory))
        assertEquals(MapSourceRecord.BuiltInProvider, MapSourceMarker.sourceOf(directory))
    }

    @Test
    fun markerNamingAnUnknownSourceIsUnreadable() {
        val directory = temporaryFolder.newFolder().toPath()
        Files.write(
            directory.resolve(MapSourceMarker.FILE_NAME),
            """{"schema": 1, "sourceKind": "SOMETHING_ELSE"}""".toByteArray()
        )

        assertEquals(SourceMarkerRead.Unreadable, MapSourceMarker.read(directory))
    }

    @Test
    fun installingAgainReplacesTheMarker() {
        val directory = temporaryFolder.newFolder().toPath()

        MapSourceMarker.write(directory, MapSourceRecord(MapSourceKind.BUILT_IN_PROVIDER, null, 27))
        MapSourceMarker.write(directory, MapSourceRecord(MapSourceKind.REPOSITORY, "https://maps.example.org/repo", 27))

        assertEquals(
            SourceMarkerRead.Present(
                MapSourceRecord(MapSourceKind.REPOSITORY, "https://maps.example.org/repo", 27)
            ),
            MapSourceMarker.read(directory)
        )
    }

    @Test
    fun repositorySourceIdentityMatchesTheMarkerRecord() {
        val source = MapSource.repository("https://maps.example.org/repo/")

        assertEquals("https://maps.example.org/repo", source.baseUrl)
        assertEquals(MapSourceKind.REPOSITORY, source.kind)
        assertTrue(source.isRepository)
        assertEquals(
            source,
            MapSourceRecord(MapSourceKind.REPOSITORY, source.baseUrl, 27).toSource()
        )
    }

    @Test
    fun markerVersionMatchesTheSupportedDatabaseFormatVersion() {
        val directory = temporaryFolder.newFolder().toPath()
        val version = MapDownloadManager.DATABASE_FORMAT_VERSION

        MapSourceMarker.write(directory, MapSourceRecord(MapSourceKind.REPOSITORY, "https://x.example", version))

        assertEquals(version, MapSourceMarker.sourceOf(directory).databaseVersion)
    }
}
