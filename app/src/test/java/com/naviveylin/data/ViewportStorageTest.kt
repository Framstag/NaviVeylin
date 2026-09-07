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

    @Test
    fun saveRejectsNaNCoordinates() = runTest {
        // Viewport-save race guard: an uninitialized (NaN) viewport must never
        // be persisted, or it would clobber a previously restored viewport.
        storage.save("germany", ViewportState(centerLat = Double.NaN, centerLon = 16.4, magnification = 12.0))
        assertNull(storage.load("germany"))
    }

    @Test
    fun saveRejectsOutOfRangeCoordinates() = runTest {
        storage.save("germany", ViewportState(centerLat = 95.0, centerLon = 16.4, magnification = 12.0))
        assertNull(storage.load("germany"))
        storage.save("germany", ViewportState(centerLat = 48.2, centerLon = 200.0, magnification = 12.0))
        assertNull(storage.load("germany"))
    }

    @Test
    fun saveRejectsInvalidMagnification() = runTest {
        storage.save("germany", ViewportState(centerLat = 48.2, centerLon = 16.4, magnification = Double.NaN))
        assertNull(storage.load("germany"))
        storage.save("germany", ViewportState(centerLat = 48.2, centerLon = 16.4, magnification = 0.0))
        assertNull(storage.load("germany"))
    }

    @Test
    fun saveRejectedStateDoesNotClobberExistingFile() = runTest {
        // The race scenario: a valid restored viewport exists on disk; an early
        // invalid save must leave it untouched.
        storage.save("germany", ViewportState(centerLat = 48.2, centerLon = 16.4, magnification = 12.0))
        storage.save("germany", ViewportState(centerLat = Double.NaN, centerLon = Double.NaN, magnification = 12.0))
        val loaded = storage.load("germany")
        assertNotNull(loaded)
        assertEquals(48.2, loaded!!.centerLat, 1e-9)
        assertEquals(16.4, loaded.centerLon, 1e-9)
    }

    @Test
    fun isValidRejectsUninitializedState() {
        assertTrue(ViewportState().isValid())
        assertFalse(ViewportState(centerLat = Double.NaN).isValid())
        assertFalse(ViewportState(centerLon = Double.POSITIVE_INFINITY).isValid())
        assertFalse(ViewportState(centerLat = 91.0).isValid())
        assertFalse(ViewportState(centerLon = -181.0).isValid())
        assertFalse(ViewportState(magnification = 0.0).isValid())
        assertFalse(ViewportState(magnification = Double.NaN).isValid())
        assertFalse(ViewportState(angle = Double.NaN).isValid())
    }
}
