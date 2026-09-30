package com.naviveylin.data

import java.io.File

/**
 * The packaged raster POI icon set, and the directory the native renderer is configured with.
 *
 * The map stylesheets reference their POI icons by file name (`NODE.ICON { name: bus_stop; }`),
 * and the renderer resolves that name against the icon directory it was configured with — as
 * `path + name + ".png"` (`MapPainterCairo::HasIcon`). An icon style whose image cannot be
 * loaded falls back to the vector symbol the style declares inline; a style that declares only a
 * name therefore draws nothing at all (`MapPainter::LayoutPointLabels`). Both halves of that rule
 * — the directory and its trailing separator — are produced here, because a path of any other
 * shape fails silently: the loader reports exactly the same "cannot load image" line it reports
 * for a directory that was never configured.
 *
 * The set is packaged from the pinned libosmscout submodule at build time (`syncSubmoduleIcons`,
 * spec `map-render`), the same rule the stylesheets follow. Only the raster leaf is shipped: the
 * renderer cannot read the neighbouring SVG set.
 */
object IconAssets {

    /** Asset root of the packaged icon tree (`app/src/main/assets` + `syncSubmoduleIcons`). */
    const val ASSET_ROOT = "icons"

    /** The raster leaf inside [ASSET_ROOT] — a `<size>/<style>` directory of `*.png` files. */
    const val RASTER_LEAF = "14x14/standard"

    /** Asset path of the raster icon [name] (the file the stylesheets request by name). */
    fun assetPathOf(name: String): String = "$ASSET_ROOT/$RASTER_LEAF/$name.png"

    /** Internal-storage root the packaged icon tree is mirrored to (see [AssetCopier]). */
    fun deviceRoot(filesDir: File): File = File(filesDir, ASSET_ROOT)

    /**
     * The directory handed to the native client, **including** its trailing separator — the
     * renderer concatenates the icon name directly onto it, so a path without one cannot resolve
     * a single icon.
     */
    fun clientDirectory(filesDir: File): String =
        File(deviceRoot(filesDir), RASTER_LEAF).absolutePath + File.separator
}
