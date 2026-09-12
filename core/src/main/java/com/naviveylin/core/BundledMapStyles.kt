package com.naviveylin.core

/**
 * The top-level `*.oss` stylesheets bundled from the libosmscout submodule at
 * build time (sorted, no `.oss` postfix). This is the single source for:
 * - the fallback style list when the live native enumeration
 *   ([OSMScoutClient.getAvailableStyleSheets]) is unavailable,
 * - the expected packaged set asserted by the bundling guard tests.
 *
 * Keep in sync with the top-level `*.oss` files of
 * `app/src/main/cpp/libosmscout/stylesheets/` (the `AssetCopierTest` guard
 * fails the build when they drift).
 */
object BundledMapStyles {
    /**
     * The basemap's internal stylesheet (basemap-render.oss). Loaded by the
     * native client for the basemap database; never offered as a
     * user-selectable map style (spec: map-styles).
     */
    const val BASEMAP_STYLE_NAME = "basemap-render"

    val ALL: List<String> = listOf(
        "basemap-render", "boundaries", "coastlines", "cycle", "motorways",
        "public-transport", "railways", "standard", "winter-sports"
    )

    /**
     * Styles offered in the map style picker: the full bundled set minus the
     * basemap's internal stylesheet.
     */
    val USER_SELECTABLE: List<String> = ALL.filterNot { it == BASEMAP_STYLE_NAME }
}
