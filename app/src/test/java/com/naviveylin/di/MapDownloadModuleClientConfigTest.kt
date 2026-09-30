package com.naviveylin.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.OSMScoutClientBuilder
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.IconAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The client-configuration seam: the stylesheet directory and the icon directory the renderer
 * resolves its named POI icons against must both reach the native builder (`TODO.md` §85 — the icon
 * directory was simply never configured, spec `map-render` — Raster icon directory is refreshed on
 * device and configured at the client seam).
 *
 * The native `builder.build()` cannot run in a JVM test, so [configureClient] carries the wiring and
 * this test observes it through a recording builder: nothing native is called, the fluent setters
 * only store the value the JNI build() reads later.
 *
 * JNI/classloader rule: the class init of [OSMScoutClientBuilder] loads the native library, so this
 * class runs under Robolectric with the DEFAULT sandbox — no `@Config(sdk=…)`, no `@GraphicsMode`.
 */
@RunWith(RobolectricTestRunner::class)
class MapDownloadModuleClientConfigTest {

    @Before
    fun setUp() {
        // Isolated asset copies per test (the icon path is asserted through AssetCopier below).
        ApplicationProvider.getApplicationContext<Context>().filesDir.resolve("icons").deleteRecursively()
    }

    @Test
    fun configuresTheIconDirectoryBesideTheStylesheetDirectory() {
        val builder = RecordingBuilder()

        configureClient(
            builder = builder,
            mapsDir = "/files/maps",
            stylesheetsDir = "/files/stylesheets",
            iconsDir = "/files/icons/14x14/standard/",
            physicalDpi = 420.0,
            basemapLookupDir = null
        )

        assertEquals(
            "the renderer cannot load a single named icon without this directory",
            listOf("/files/icons/14x14/standard/"),
            builder.iconDirectories
        )
        assertEquals(listOf("/files/stylesheets"), builder.stylesheetDirectories)
        assertEquals(listOf("420.0"), builder.physicalDpis)
    }

    @Test
    fun theConfiguredIconDirectoryIsTheOneTheLoaderCanResolveThrough() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val iconsDir = AssetCopier(context).ensureIcons()
        val builder = RecordingBuilder()

        configureClient(
            builder = builder,
            mapsDir = "/files/maps",
            stylesheetsDir = "/files/stylesheets",
            iconsDir = iconsDir,
            physicalDpi = 420.0,
            basemapLookupDir = null
        )

        val configured = builder.iconDirectories.single()
        assertEquals(IconAssets.clientDirectory(context.filesDir), configured)
        assertTrue("must end with the separator the loader needs: $configured", configured.endsWith(File.separator))
        // `path + name + ".png"` has to land on the mirrored file.
        assertTrue(File(configured + "bus_stop" + ".png").exists())
    }

    @Test
    fun theBasemapLookupDirectoryIsConfiguredOnlyWhenOneIsInstalled() {
        val builder = RecordingBuilder()
        configureClient(builder, "/files/maps", "/files/stylesheets", "/files/icons/", 420.0, null)
        assertEquals(emptyList<String>(), builder.basemapLookupDirectories)

        val withBasemap = RecordingBuilder()
        configureClient(
            withBasemap, "/files/maps", "/files/stylesheets", "/files/icons/", 420.0, "/files/maps/basemap"
        )
        assertEquals(listOf("/files/maps/basemap"), withBasemap.basemapLookupDirectories)
    }

    /** Records the values the fluent setters receive, then stores them like the real builder. */
    private class RecordingBuilder : OSMScoutClientBuilder() {
        val iconDirectories = mutableListOf<String>()
        val stylesheetDirectories = mutableListOf<String>()
        val basemapLookupDirectories = mutableListOf<String>()
        val physicalDpis = mutableListOf<String>()

        override fun withIconDirectory(iconDirectory: String): OSMScoutClientBuilder {
            iconDirectories += iconDirectory
            return super.withIconDirectory(iconDirectory)
        }

        override fun withStyleSheetDirectory(stylesheetDirectory: String): OSMScoutClientBuilder {
            stylesheetDirectories += stylesheetDirectory
            return super.withStyleSheetDirectory(stylesheetDirectory)
        }

        override fun withBasemapLookupDirectory(basemapLookupDirectory: String): OSMScoutClientBuilder {
            basemapLookupDirectories += basemapLookupDirectory
            return super.withBasemapLookupDirectory(basemapLookupDirectory)
        }

        override fun withPhysicalDpi(physicalDpi: Double): OSMScoutClientBuilder {
            physicalDpis += physicalDpi.toString()
            return super.withPhysicalDpi(physicalDpi)
        }
    }
}
