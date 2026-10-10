package com.naviveylin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.core.mapsource.MapSourceKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [MapSourceRegistry]'s source inventory and active-source resolution.
 *
 * Spec: map-download-infrastructure — "Default provider available" / "Repository source available
 * alongside the built-in provider"; map-source-selection — "Default source on first start" /
 * "Selection survives a restart".
 */
@RunWith(RobolectricTestRunner::class)
class MapSourceRegistryTest {

    private val provider = MapProvider(
        "karry.cz",
        "https://osmscout.karry.cz",
        "https://osmscout.karry.cz/latest.php?fromVersion=%1&toVersion=%2&locale=%3"
    )

    @Test
    fun offersBuiltInAndRepository() = runTest {
        val registry = registry()

        val sources = registry.availableSources()

        assertEquals(2, sources.size)
        assertEquals(MapSource.BuiltInProvider, sources.first())
        assertTrue("the repository source is offered beside it", sources[1].isRepository)
        assertEquals(MapSourceKind.REPOSITORY, sources[1].kind)
    }

    @Test
    fun defaultIsBuiltInProvider() = runTest {
        val registry = registry()

        assertEquals(MapSource.BuiltInProvider, registry.activeSource())
        assertEquals("karry.cz", registry.builtInProvider.name)
        assertEquals("https://osmscout.karry.cz", registry.builtInProvider.uri)
    }

    @Test
    fun repositorySourceIsConfiguredWithTheStoredUrl() = runTest {
        val storage = storageWith(
            AppSettings().copy(
                mapSourceKind = MapSourceKind.REPOSITORY.name,
                mapRepositoryUrl = "https://maps.example.org/repo"
            )
        )
        val registry = registry(storage)

        val active = registry.activeSource()

        assertTrue(active.isRepository)
        assertEquals("https://maps.example.org/repo", active.baseUrl)
        assertEquals("https://maps.example.org/repo", registry.availableSources()[1].baseUrl)
    }

    @Test
    fun repositorySourceWithoutStoredUrlIsOfferedButNeverActive() = runTest {
        val registry = registry()

        val offered = registry.availableSources()[1]

        assertTrue(offered.isRepository)
        assertEquals("", offered.baseUrl)
        assertEquals(MapSource.BuiltInProvider, registry.activeSource())
    }

    @Test
    fun theRegistryUsesTheBridgesDatabaseFormatVersion() = runTest {
        val registry = registry()

        assertEquals(
            com.framstag.libosmscout.client.MapDownloadManager.DATABASE_FORMAT_VERSION,
            registry.databaseFormatVersion
        )
    }

    @Test
    fun theRepositoryListerAndBasemapUseThatVersion() = runTest {
        val registry = registry()
        val source = MapSource.repository("https://maps.example.org/repo")

        assertEquals(source, registry.repositoryLister(source).source)
        assertEquals(
            "a repository basemap reads the same database format version the registry reports",
            registry.databaseFormatVersion,
            registry.repositoryBasemap(source).readableDatabaseVersion
        )
    }

    private fun registry(storage: SettingsStorage = storageWith(AppSettings())): MapSourceRegistry =
        MapSourceRegistry(storage, provider, HttpUrlFetcher())

    private fun storageWith(settings: AppSettings): SettingsStorage {
        SettingsStorage(ApplicationProvider.getApplicationContext<Context>()).let { storage ->
            kotlinx.coroutines.runBlocking { storage.save(settings) }
            return storage
        }
    }
}
