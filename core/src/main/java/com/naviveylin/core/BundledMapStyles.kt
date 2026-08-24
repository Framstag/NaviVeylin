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
    val ALL: List<String> = listOf(
        "basemap-render", "boundaries", "coastlines", "cycle", "motorways",
        "public-transport", "railways", "standard", "winter-sports"
    )
}
