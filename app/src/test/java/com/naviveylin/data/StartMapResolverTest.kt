package com.naviveylin.data

import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.InstalledMaps
import com.naviveylin.navigation.Routes
import java.io.File
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [StartMapResolver] over a real `filesDir/maps` tree (spec `start-map-selection` — the
 * start map comes from the shared installed-map discovery; the basemap package is not a candidate;
 * a nested map directory is found; the last opened map is reopened; a recorded map that is no longer
 * installed falls back deterministically; no map installed shows the entry point) and for the seam
 * the navigation graph consumes (the resolved path is what `Routes.mapCanvas` is built from).
 *
 * Robolectric (default sandbox, per the repo's classloader rule): the resolver needs a `Context`
 * filesDir and reads the real `SettingsStorage` document.
 */
@RunWith(RobolectricTestRunner::class)
class StartMapResolverTest {

    private fun context() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun mapsRoot(): File = File(context().filesDir, "maps")

    /** Create a database directory: the shared discovery requires a `types.dat` marker. */
    private fun database(relativePath: String): File {
        val dir = File(mapsRoot(), relativePath)
        assertTrue("could not create $relativePath", dir.mkdirs())
        File(dir, InstalledMaps.TYPES_DAT).writeText("marker")
        return dir
    }

    /** The download layout: the map name may contain a '/', so the database sits at maps/<n>/<n>/. */
    private fun nestedDatabase(name: String): File = database("$name/$name")

    private fun resolver(): StartMapResolver =
        StartMapResolver(SettingsStorage(context()), MapStorageManager(context()))

    private suspend fun record(path: String) =
        SettingsStorage(context()).update { it.copy(lastMapPath = path) }

    @Test
    fun `the recorded map is reopened when it is installed`() = runTest {
        val region = database("nordrhein-westfalen")
        val nested = nestedDatabase("iceland")

        record(nested.absolutePath)
        assertEquals(nested.absolutePath, resolver().resolve())

        // The other database stays a candidate, but the recorded one wins.
        record(region.absolutePath)
        assertEquals(region.absolutePath, resolver().resolve())
    }

    @Test
    fun `the basemap package is never a candidate`() = runTest {
        // 'basemap' sorts before the region, so a rule that took the first discovered directory
        // would pick the basemap overlay — which the client loads separately and which must never be
        // opened as the map the user sees.
        val basemap = database("basemap")
        val region = database("nordrhein-westfalen")
        assertTrue("the basemap path must sort first for this case", basemap.absolutePath < region.absolutePath)

        assertEquals(region.absolutePath, resolver().resolve())

        // Even a recorded basemap path is refused: it is not in the discovered candidate set.
        record(basemap.absolutePath)
        assertEquals(region.absolutePath, resolver().resolve())
    }

    @Test
    fun `a nested database directory is a candidate`() = runTest {
        val nested = nestedDatabase("iceland")
        assertTrue(nested.absolutePath.endsWith("/maps/iceland/iceland"))

        assertEquals(nested.absolutePath, resolver().resolve())
    }

    @Test
    fun `the settings file is never a candidate`() = runTest {
        // SettingsStorage writes filesDir/maps/settings.json — a file inside the scanned root.
        val region = database("nordrhein-westfalen")
        assertNotNull(SettingsStorage(context()).load())

        val resolved = resolver().resolve()

        assertEquals(region.absolutePath, resolved)
        assertTrue("resolved path must be a directory", File(resolved!!).isDirectory)
    }

    @Test
    fun `a deleted recorded map falls back to the same database every time`() = runTest {
        val region = database("nordrhein-westfalen")
        val nested = nestedDatabase("iceland")
        val deleted = File(mapsRoot(), "deleted-map").absolutePath
        record(deleted)

        val first = resolver().resolve()
        val second = resolver().resolve()

        assertEquals(nested.absolutePath, first)
        assertEquals(first, second)
        assertFalse("the deleted path must not be returned", first == deleted)
        assertTrue(listOf(region.absolutePath, nested.absolutePath).contains(first))
    }

    @Test
    fun `no installed map resolves to nothing`() = runTest {
        // Only the basemap overlay is installed: that is not a map the user opened.
        database("basemap")

        assertNull(resolver().resolve())
    }

    @Test
    fun `the resolved map is what the navigation routes are built from`() = runTest {
        val nested = nestedDatabase("iceland")
        record(nested.absolutePath)

        // The start destination is `Routes.mapCanvas(<resolved>)`; the path survives the route
        // encoding, and with no installed map the destination is the entry point (`Routes.MAIN`).
        val resolved = resolver().resolve()
        assertNotNull(resolved)
        assertEquals(nested.absolutePath, decodeMapRoute(Routes.mapCanvas(resolved!!)))

        File(mapsRoot(), "iceland").deleteRecursively()
        assertNull(resolver().resolve())
    }

    @Test
    fun `resolution runs on the injected background dispatcher`() = runTest {
        database("nordrhein-westfalen")
        val dispatcher = CountingDispatcher(Dispatchers.IO)
        val resolver = resolver()
        resolver.ioDispatcher = dispatcher

        assertNotNull(resolver.resolve())

        assertTrue(
            "the scan and the settings read must not run on the caller's dispatcher",
            dispatcher.dispatches.get() > 0
        )
    }

    private class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        val dispatches = AtomicInteger()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches.incrementAndGet()
            delegate.dispatch(context, block)
        }
    }

    private fun decodeMapRoute(route: String): String =
        String(Base64.getUrlDecoder().decode(route.removePrefix("map_canvas/")))
}
