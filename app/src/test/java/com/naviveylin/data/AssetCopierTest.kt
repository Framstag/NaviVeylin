package com.naviveylin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
    }

    private fun stylesheetsDir(): File = File(context.filesDir, "stylesheets")

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
}
