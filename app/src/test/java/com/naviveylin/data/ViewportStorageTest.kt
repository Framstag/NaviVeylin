package com.naviveylin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewportStorageTest {

    private lateinit var storage: ViewportStorage
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = ViewportStorage(context)
    }

    @Test
    fun saveAndLoad() = runTest {
        val state = ViewportState(centerLat = 48.2, centerLon = 16.4, magnification = 12.0)
        storage.save("germany", state)
        val loaded = storage.load("germany")
        assertNotNull(loaded)
        assertEquals(48.2, loaded!!.centerLat, 1e-9)
        assertEquals(16.4, loaded.centerLon, 1e-9)
        assertEquals(12.0, loaded.magnification, 1e-9)
    }

    @Test
    fun loadMissingFileReturnsNull() = runTest {
        val loaded = storage.load("missing")
        assertNull(loaded)
    }

    @Test
    fun loadCorruptedFileReturnsNull() = runTest {
        val file = java.io.File(context.filesDir, "maps/viewport-corrupt.json")
        file.parentFile?.mkdirs()
        file.writeText("not valid json")
        val loaded = storage.load("corrupt")
        assertNull(loaded)
    }

    @Test
    fun saveOverwritesPreviousState() = runTest {
        storage.save("germany", ViewportState(centerLat = 1.0, centerLon = 2.0, magnification = 5.0))
        storage.save("germany", ViewportState(centerLat = 3.0, centerLon = 4.0, magnification = 10.0))
        val loaded = storage.load("germany")
        assertNotNull(loaded)
        assertEquals(3.0, loaded!!.centerLat, 1e-9)
        assertEquals(4.0, loaded.centerLon, 1e-9)
        assertEquals(10.0, loaded.magnification, 1e-9)
    }

    @Test
    fun mapsAreIsolatedPerKey() = runTest {
        storage.save("germany", ViewportState(centerLat = 1.0, centerLon = 2.0, magnification = 5.0))
        val other = storage.load("island")
        assertNull(other)
    }

    @Test
    fun saveAndLoadFractionalMagnification() = runTest {
        // continuous-pinch-zoom (spec: viewport-persist): a pinch commit stores
        // the fractional magnification and reloads it verbatim.
        storage.save("germany", ViewportState(centerLat = 48.2, centerLon = 16.4, magnification = 17.412))
        val loaded = storage.load("germany")
        assertNotNull(loaded)
        assertEquals(17.412, loaded!!.magnification, 1e-12)
    }

    @Test
    fun loadLegacyIntegerMagnificationFile() = runTest {
        // Spec: viewport-persist — files written by older versions with an
        // integer magnification load unchanged (an int is a valid fractional value).
        val file = java.io.File(context.filesDir, "maps/viewport-legacy-int.json")
        file.parentFile?.mkdirs()
        file.writeText("""{"centerLat":51.5,"centerLon":7.5,"magnification":14,"angle":0.0}""")
        val loaded = storage.load("legacy-int")
        assertNotNull(loaded)
        assertEquals(14.0, loaded!!.magnification, 1e-9)
    }
}
