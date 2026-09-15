package com.naviveylin.auto

import com.framstag.libosmscout.client.FakeClientWithBbox
import com.framstag.libosmscout.client.OSMScoutClient
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Tests for the initial-viewport resolution (spec: auto-map-renderer —
 * "Renderer initialization off the car-app main thread"; design D2):
 * priority explicit center → saved phone viewport → first installed map bbox
 * → default. Per-DB bbox failures fall through.
 *
 * Robolectric default sandbox (mock `OSMScoutClient` needs the JNI stub).
 */
@RunWith(RobolectricTestRunner::class)
class AutoInitialViewportTest {

    private val defaultCenter = 51.5136 to 7.4653
    private val bbox = doubleArrayOf(50.0, 6.0, 52.0, 8.0) // center 51,7

    private fun tempMapsRoot(): File {
        val root = File(RuntimeEnvironment.getApplication().cacheDir, "maps-test-${System.nanoTime()}")
        root.mkdirs()
        return root
    }

    private fun mapDirWithTypesDat(root: File, name: String) {
        val dir = File(root, name)
        dir.mkdirs()
        File(dir, "types.dat").writeText("types")
    }

    private fun writeViewport(root: File, name: String, lat: Double, lon: Double, mag: Int) {
        File(root, name).writeText(
            """{"centerLat":$lat,"centerLon":$lon,"magnification":$mag}"""
        )
    }

    private fun clientWithBbox(bbox: DoubleArray?): OSMScoutClient = FakeClientWithBbox { bbox }

    private fun resolve(
        root: File,
        client: OSMScoutClient,
        initialCenter: Pair<Double, Double>? = null,
        initialZoom: Int = 13
    ) = runBlocking {
        resolveInitialAutoViewport(
            mapsRootDir = root,
            client = client,
            initialCenter = initialCenter,
            initialZoom = initialZoom,
            defaultZoom = 13,
            defaultCenter = defaultCenter
        )
    }

    @Test
    fun `initialCenter wins over saved viewport and bbox`() {
        val root = tempMapsRoot()
        writeViewport(root, "viewport-a.json", 48.0, 2.0, 10)
        mapDirWithTypesDat(root, "dortmund")

        val result = resolve(root, clientWithBbox(bbox), initialCenter = 51.0 to 7.0, initialZoom = 14)

        assertEquals(51.0, result.lat, 0.0001)
        assertEquals(7.0, result.lon, 0.0001)
        assertEquals(14, result.zoom)
    }

    @Test
    fun `saved phone viewport wins over map bbox`() {
        val root = tempMapsRoot()
        writeViewport(root, "viewport-a.json", 48.0, 2.0, 10)
        mapDirWithTypesDat(root, "dortmund")

        val result = resolve(root, clientWithBbox(bbox))

        assertEquals(48.0, result.lat, 0.0001)
        assertEquals(2.0, result.lon, 0.0001)
        assertEquals(10, result.zoom)
    }

    @Test
    fun `newest saved viewport file wins`() {
        val root = tempMapsRoot()
        writeViewport(root, "viewport-a.json", 48.0, 2.0, 10)
        Thread.sleep(5)
        writeViewport(root, "viewport-b.json", 49.0, 3.0, 11)

        val result = resolve(root, clientWithBbox(bbox))

        assertEquals(49.0, result.lat, 0.0001)
        assertEquals(11, result.zoom)
    }

    @Test
    fun `map bbox used when no saved viewport`() {
        val root = tempMapsRoot()
        mapDirWithTypesDat(root, "dortmund")

        val result = resolve(root, clientWithBbox(bbox))

        assertEquals(51.0, result.lat, 0.0001)
        assertEquals(7.0, result.lon, 0.0001)
        assertEquals(13, result.zoom)
    }

    @Test
    fun `first map db bbox failure falls through to default`() {
        val root = tempMapsRoot()
        mapDirWithTypesDat(root, "dortmund")
        val failing: OSMScoutClient = FakeClientWithBbox { throw IllegalStateException("no db") }

        val result = resolve(root, failing)

        assertEquals(defaultCenter.first, result.lat, 0.0001)
        assertEquals(defaultCenter.second, result.lon, 0.0001)
        assertEquals(13, result.zoom)
    }

    @Test
    fun `default used when nothing installed and no saved viewport`() {
        val root = tempMapsRoot()

        val result = resolve(root, clientWithBbox(bbox))

        assertEquals(defaultCenter.first, result.lat, 0.0001)
        assertEquals(defaultCenter.second, result.lon, 0.0001)
        assertEquals(13, result.zoom)
    }

    @Test
    fun `corrupt saved viewport is ignored`() {
        val root = tempMapsRoot()
        File(root, "viewport-a.json").writeText("not json")
        mapDirWithTypesDat(root, "dortmund")

        val result = resolve(root, clientWithBbox(bbox))

        assertEquals(51.0, result.lat, 0.0001) // bbox fallback, viewport ignored
    }

    @Test
    fun `latestSavedAutoViewport is null when no usable file`() {
        val root = tempMapsRoot()
        writeViewport(root, "viewport-a.json", Double.NaN, 2.0, 10)

        assertNull(latestSavedAutoViewport(root, 13))
    }
}
