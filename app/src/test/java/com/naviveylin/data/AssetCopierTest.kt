package com.naviveylin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.BundledMapStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * AssetCopier refresh tests under Robolectric.
 *
 * These tests exercise the bundled assets as merged by the build, which now come
 * from the libosmscout submodule (source-set wiring) — so they also verify the
 * packaged stylesheet set (e.g. upstream removed include/symbols.oss).
 *
 * JNI-free by design: no FakeOSMScoutClient / OSMScoutClient touch, no @Config.
 */
@RunWith(RobolectricTestRunner::class)
class AssetCopierTest {

    private lateinit var context: Context
    private lateinit var copier: AssetCopier

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        copier = AssetCopier(context)
        // Isolated stylesheets dir per test
        context.filesDir.resolve("stylesheets").deleteRecursively()
        context.filesDir.resolve("icons").deleteRecursively()
    }

    private fun stylesheetsDir(): File = File(context.filesDir, "stylesheets")

    private fun iconsLeafDir(): File =
        File(IconAssets.deviceRoot(context.filesDir), IconAssets.RASTER_LEAF)

    private fun assetBytes(assetPath: String): ByteArray =
        context.assets.open(assetPath).use { it.readBytes() }

    @Test
    fun firstLaunchCopiesFullSet() {
        val dir = File(copier.ensureStylesheets())

        assertTrue(dir.exists())
        assertTrue(dir.resolve("map.ost").exists())
        assertTrue(dir.resolve("standard.oss").exists())
        assertTrue(dir.resolve("basemap.ost").exists())
        assertTrue(dir.resolve("include").isDirectory)
        assertTrue(dir.resolve("include/roads.oss").exists())

        // Content matches the bundled (submodule-sourced) assets
        assertTrue(dir.resolve("map.ost").readBytes().contentEquals(assetBytes("stylesheets/map.ost")))
        assertTrue(dir.resolve("include/roads.oss").readBytes().contentEquals(assetBytes("stylesheets/include/roads.oss")))
    }

    @Test
    fun updateWithChangedContentRefreshesOnlyChangedFiles() {
        copier.ensureStylesheets()

        // Tamper with one file; leave another untouched
        val mapOst = File(context.filesDir, "stylesheets/map.ost")
        val standardOss = File(context.filesDir, "stylesheets/standard.oss")
        val standardBefore = standardOss.readBytes()
        mapOst.writeText("tampered stale content")

        copier.ensureStylesheets()

        // Tampered file refreshed to bundled content
        assertTrue(mapOst.readBytes().contentEquals(assetBytes("stylesheets/map.ost")))
        // Untouched file left alone (same bytes, not rewritten)
        assertTrue(standardOss.readBytes().contentEquals(standardBefore))
    }

    @Test
    fun noChangeStartIsNoOp() {
        copier.ensureStylesheets()
        val mapOst = File(context.filesDir, "stylesheets/map.ost")
        val beforeModified = mapOst.lastModified()
        val beforeBytes = mapOst.readBytes()

        val dir = File(copier.ensureStylesheets())

        assertEquals(beforeModified, mapOst.lastModified())
        assertTrue(mapOst.readBytes().contentEquals(beforeBytes))
        assertTrue(dir.exists())
    }

    @Test
    fun removedBundleFileDeletedFromInternalStorage() {
        copier.ensureStylesheets()

        // Simulate a file left behind by an older APK that no longer bundles it
        val stale = File(context.filesDir, "stylesheets/stale.oss")
        stale.writeText("old file")
        assertTrue(stale.exists())

        copier.ensureStylesheets()

        assertFalse(stale.exists())
    }

    @Test
    fun upstreamRemovedSymbolsOssNotPresent() {
        copier.ensureStylesheets()

        // Upstream deleted include/symbols.oss in cd273c581; the bundled set
        // (from the submodule at build time) must not contain it.
        assertFalse(File(context.filesDir, "stylesheets/include/symbols.oss").exists())
    }

    @Test
    fun bundlesEveryTopLevelStyleSheet() {
        copier.ensureStylesheets()
        val dir = stylesheetsDir()

        // Every top-level *.oss from the libosmscout submodule must be
        // packaged and copied — a submodule bump that drops one of these
        // silently removes a selectable style (spec: map-styles).
        val expected = BundledMapStyles.ALL.map { "$it.oss" }
        val actual = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".oss") }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()

        assertEquals(expected.sorted(), actual)
    }

    // ── Raster POI icons (spec map-render — Raster icon directory is refreshed on device
    //    and configured at the client seam; TODO.md §85) ─────────────────────────────────

    @Test
    fun iconsFirstLaunchCopiesTheRasterSet() {
        val dir = copier.ensureIcons()

        // The returned path is what the native client is configured with: absolute, with the
        // trailing separator its `path + name + ".png"` construction needs.
        assertEquals(IconAssets.clientDirectory(context.filesDir), dir)
        assertTrue("client directory must be absolute: $dir", File(dir).isAbsolute)
        assertTrue("client directory must end with a separator: $dir", dir.endsWith(File.separator))

        val leaf = iconsLeafDir()
        assertTrue("the raster leaf must exist on device: $leaf", leaf.isDirectory)
        for (name in RASTER_ICON_SAMPLES) {
            val file = leaf.resolve("$name.png")
            assertTrue("$name.png must be mirrored from the packaged assets", file.exists())
            assertTrue(
                "$name.png content must match the packaged asset",
                file.readBytes().contentEquals(assetBytes(IconAssets.assetPathOf(name)))
            )
        }
        assertTrue(
            "the whole packaged raster set must be mirrored, not a sample",
            leaf.listFiles()?.count { it.name.endsWith(".png") } ?: 0 >= 20
        )
    }

    @Test
    fun iconsUpdateWithChangedContentRefreshesOnlyChangedFiles() {
        copier.ensureIcons()

        val busStop = iconsLeafDir().resolve("bus_stop.png")
        val parking = iconsLeafDir().resolve("parking.png")
        val parkingBefore = parking.readBytes()
        busStop.writeText("tampered stale content")

        copier.ensureIcons()

        assertTrue(
            "tampered icon must be refreshed to the packaged content",
            busStop.readBytes().contentEquals(assetBytes(IconAssets.assetPathOf("bus_stop")))
        )
        assertTrue("untouched icon must be left alone", parking.readBytes().contentEquals(parkingBefore))
    }

    @Test
    fun iconsNoChangeStartIsNoOp() {
        copier.ensureIcons()
        val file = iconsLeafDir().resolve("bus_stop.png")
        val beforeModified = file.lastModified()
        val beforeBytes = file.readBytes()

        val dir = copier.ensureIcons()

        assertEquals(beforeModified, file.lastModified())
        assertTrue(file.readBytes().contentEquals(beforeBytes))
        assertEquals(IconAssets.clientDirectory(context.filesDir), dir)
    }

    @Test
    fun removedIconFileDeletedFromInternalStorage() {
        copier.ensureIcons()

        // Simulate an icon left behind by an older APK that no longer bundles it.
        val stale = iconsLeafDir().resolve("stale_icon.png")
        stale.writeText("old file")
        assertTrue(stale.exists())

        copier.ensureIcons()

        assertFalse(stale.exists())
    }

    @Test
    fun theTwoAssetTreesDoNotDeleteEachOther() {
        copier.ensureStylesheets()
        copier.ensureIcons()

        // Mirror semantics run per tree with its own asset root: the icons pass must not treat the
        // stylesheets as stale (or the other way round).
        assertTrue(File(context.filesDir, "stylesheets/map.ost").exists())
        assertTrue(iconsLeafDir().resolve("bus_stop.png").exists())

        copier.ensureStylesheets()
        copier.ensureIcons()

        assertTrue(File(context.filesDir, "stylesheets/map.ost").exists())
        assertTrue(iconsLeafDir().resolve("bus_stop.png").exists())
    }

    private companion object {
        /** A small, stable sample of the packaged raster set (the guard test covers coverage). */
        val RASTER_ICON_SAMPLES = listOf("bus_stop", "parking", "bench")
    }
}
