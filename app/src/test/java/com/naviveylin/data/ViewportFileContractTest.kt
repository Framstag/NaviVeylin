package com.naviveylin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Holds `viewport-persist`'s storage contract to the layout `ViewportStorage` writes
 * (spec: viewport-persist — Save viewport state / Load viewport state).
 *
 * The shipped spec described **one** file (`filesDir/maps/viewport.json`) while the storage has
 * written one file per map key (`viewport-<key>.json`) since `continuous-pinch-zoom`; nothing compared
 * the two artefacts, so the divergence survived. This case reads the spec file the way
 * `FavAutoZoomClampRangeTest` and `MapMenuBackOrderComposeTest` do (an `:app` unit test's working
 * directory is the module directory) and compares each stated rule with what `save` actually writes:
 * the file pattern, the key derivation and the escaping of characters outside `A-Za-z0-9._-`.
 *
 * Every value is printed before its assertion so the JUnit XML's `system-out` carries the
 * measurement of the run that made it. Robolectric, default sandbox: no `@Config`.
 */
@RunWith(RobolectricTestRunner::class)
class ViewportFileContractTest {

    private lateinit var context: Context
    private lateinit var storage: ViewportStorage

    private val specFile = File("../openspec/specs/viewport-persist/spec.md")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = ViewportStorage(context)
    }

    @Test
    fun `the spec states the per-map file the storage writes`() = runTest {
        val state = ViewportState(centerLat = 48.2, centerLon = 16.4, magnification = 12.0)
        storage.save("germany", state)
        val written = writtenFileNames()
        assertEquals("one save writes one file", 1, written.size)

        val text = specFile.readText()
        val stated = Regex("`filesDir/maps/(viewport-<key>\\.json)`").find(text)?.groupValues?.get(1)
        println("viewportFile spec=[$stated] written=[${written.single()}]")
        assertNotNull(
            "the spec must state the per-map file `filesDir/maps/viewport-<key>.json`: ${specFile.absolutePath}",
            stated
        )
        assertEquals(
            "the file the storage writes must be the file the spec names",
            stated!!.replace("<key>", "germany"),
            written.single()
        )
    }

    @Test
    fun `the spec states the key derivation the storage applies`() = runTest {
        val text = specFile.readText()
        val stated = Regex(
            "the map database path's last segment \\(for `([^`]+)` the key is `([^`]+)`\\)"
        ).find(text)
        println("viewportKey spec=[${stated?.groupValues?.drop(1)}]")
        assertNotNull(
            "the spec must state the key derivation (last segment, with one example): ${specFile.absolutePath}",
            stated
        )
        val (mapPath, key) = stated!!.destructured
        assertEquals(
            "the key the storage derives from the spec's example path must be the spec's example key",
            key,
            ViewportStorage.keyFor(mapPath)
        )
    }

    @Test
    fun `the spec states the escaping the storage applies`() = runTest {
        val text = specFile.readText()
        val rule = Regex("outside `([^`]+)` SHALL be replaced by `([^`]+)`").find(text)
        println("viewportEscape spec=[${rule?.groupValues?.drop(1)}]")
        assertNotNull(
            "the spec must state the character rule for the key (\"outside `…` SHALL be replaced by `…`\")",
            rule
        )
        val (characterClass, replacement) = rule!!.destructured

        // The spec's own example, checked against the storage: a key the rule changes must name the
        // file the storage writes for exactly that key.
        val example = Regex("for the key `([^`]+)` the file is `([^`]+)`").find(text)
        assertNotNull("the spec must carry an example key whose file the rule changes", example)
        val (exampleKey, exampleFile) = example!!.destructured
        assertEquals(
            "the spec's rule and its example must agree",
            "viewport-" + exampleKey.replace(Regex("[^$characterClass]"), replacement) + ".json",
            exampleFile
        )
        storage.save(exampleKey, ViewportState(centerLat = 48.2, centerLon = 16.4, magnification = 12.0))
        println("viewportEscape example=[$exampleKey -> $exampleFile] written=[${writtenFileNames().single()}]")
        assertEquals(
            "the file the storage writes for the spec's example key must be the spec's example file",
            exampleFile,
            writtenFileNames().single()
        )
    }

    /** File names one save left under `filesDir/maps`, asserted to be a populated directory. */
    private fun writtenFileNames(): List<String> {
        val names = File(context.filesDir, "maps").listFiles()?.map { it.name }.orEmpty()
        assertNotNull("the save must create filesDir/maps", names)
        return names
    }
}
