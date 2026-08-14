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
        val state = ViewportState(centerLat = 48.2, centerLon = 16.4, magnification = 12)
        storage.save("germany", state)
        val loaded = storage.load("germany")
        assertNotNull(loaded)
        assertEquals(48.2, loaded!!.centerLat, 1e-9)
        assertEquals(16.4, loaded.centerLon, 1e-9)
        assertEquals(12, loaded.magnification)
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
        storage.save("germany", ViewportState(centerLat = 1.0, centerLon = 2.0, magnification = 5))
        storage.save("germany", ViewportState(centerLat = 3.0, centerLon = 4.0, magnification = 10))
        val loaded = storage.load("germany")
        assertNotNull(loaded)
        assertEquals(3.0, loaded!!.centerLat, 1e-9)
        assertEquals(4.0, loaded.centerLon, 1e-9)
        assertEquals(10, loaded.magnification)
    }

    @Test
    fun mapsAreIsolatedPerKey() = runTest {
        storage.save("germany", ViewportState(centerLat = 1.0, centerLon = 2.0, magnification = 5))
        val other = storage.load("island")
        assertNull(other)
    }
}
