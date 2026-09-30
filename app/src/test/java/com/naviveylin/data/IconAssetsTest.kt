package com.naviveylin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shape of the directory the native renderer is configured with (spec `map-render` — The
 * configured directory has the loader's path shape).
 *
 * The renderer builds the icon file name as `path + name + ".png"` (`MapPainterCairo::HasIcon`),
 * so a directory without its trailing separator cannot resolve a single icon — and the failure is
 * silent, because the loader reports the same "cannot load image" line it reports for a directory
 * that was never configured (`TODO.md` §85). These cases pin the shape and the packaged layout
 * string the Gradle `syncSubmoduleIcons` task and `AssetCopier` must agree on.
 *
 * JNI-free by design: no `FakeOSMScoutClient` / `OSMScoutClient` touch, no `Context` (a plain
 * `File` base), no `@Config`.
 */
class IconAssetsTest {

    private val filesDir = File("/data/user/0/com.framstag.naviveylin/files")

    @Test
    fun clientDirectoryIsAbsoluteAndEndsWithTheLoaderSeparator() {
        val dir = IconAssets.clientDirectory(filesDir)

        assertTrue("must be absolute: $dir", File(dir).isAbsolute)
        assertTrue(
            "must end with a separator — the loader concatenates the icon name onto it: $dir",
            dir.endsWith(File.separator)
        )
    }

    @Test
    fun clientDirectoryPointsAtTheRasterLeafUnderTheIconRoot() {
        assertEquals(
            File(IconAssets.deviceRoot(filesDir), IconAssets.RASTER_LEAF).absolutePath + File.separator,
            IconAssets.clientDirectory(filesDir)
        )
    }

    @Test
    fun theLoaderResolvesAPackagedIconNameToTheMirroredFile() {
        // `path + name + ".png"` must be the file the packaged asset is mirrored to.
        assertEquals(
            File(IconAssets.deviceRoot(filesDir), "14x14/standard/bus_stop.png").absolutePath,
            IconAssets.clientDirectory(filesDir) + "bus_stop" + ".png"
        )
    }

    @Test
    fun assetPathUsesThePackagedRasterLeaf() {
        // Contract with `app/build.gradle.kts` (`syncSubmoduleIcons` / `checkSubmoduleIcons`):
        // both build the asset path from this leaf.
        assertEquals("icons/14x14/standard/parking.png", IconAssets.assetPathOf("parking"))
        assertEquals("icons", IconAssets.ASSET_ROOT)
        assertEquals("14x14/standard", IconAssets.RASTER_LEAF)
    }
}
