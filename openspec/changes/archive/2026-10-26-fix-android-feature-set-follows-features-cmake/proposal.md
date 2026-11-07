# The Android build's feature set becomes features.cmake's

## Why

`app/src/main/cpp/CMakeLists.txt` rendered libosmscout's four `*Features.h` itself — `CoreFeatures.h`,
`MapFeatures.h`, `MapCairoFeatures.h`, `ClientFeatures.h` — from the cache values that stood before
`add_subdirectory(libosmscout)`, and prepended that directory to the include path. The only value the block set
was `OSMSCOUT_HAVE_LIB_MARISA`, so the effective Android feature set was **marisa on, everything else off**,
while `cmake/features.cmake` computed FreeType and the cairo-ft path as available for the same configuration:
the app's `.cxx` tree held both copies, side by side (issue #305, measured 2026-10-26).

Root cause, one sentence: the app pre-empted a header the library generates for the configuration, and the
include order §202 established made the app's copy win for every translation unit of the two map libraries.

Red probe, on HEAD (`evidence/head-feature-set.txt`, the four app-rendered headers read out of the build tree):

- `osmscout-features/libosmscout-map/include/osmscoutmap/MapFeatures.h` — `/* #undef OSMSCOUT_MAP_HAVE_LIB_FREETYPE */`
- `osmscout-features/libosmscout-map-cairo/include/osmscoutmapcairo/MapCairoFeatures.h` — `OSMSCOUT_MAP_CAIRO_HAVE_LIB_FREETYPE`,
  `OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT` and `OSMSCOUT_MAP_CAIRO_HAVE_LIB_FONTCONFIG` all `/* #undef */`
- against `libosmscout/libosmscout-map/include/osmscoutmap/MapFeatures.h` in the same build tree, which says
  `#define OSMSCOUT_MAP_HAVE_LIB_FREETYPE` and `#define OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT`

Owning requirement: `osmscout-native` "Configured feature headers live in the build tree". Owning guideline:
`guidelines/Build.md`, its "Configured feature headers" section; the consequence the label-wrapping section of `guidelines/MapRendering.md`
states about Pango.

The recorded decision on the item is **option (b)**: drop the pre-generation and let `features.cmake` decide
(issue #305, comment 2026-10-26).

## What Changes

- `app/src/main/cpp/CMakeLists.txt` renders no feature header and prepends no feature-header directory; the
  `OSMSCOUT_HAVE_LIB_MARISA` preset goes with it, because `features.cmake` computes that value from the marisa
  presets this file already sets for vcpkg.
- Two cache entries are cleared before `add_subdirectory(libosmscout)`, so the features are a function of the
  configuration and not of the build directory's history: `OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT` (a cached
  `check_cxx_source_compiles` result) and `OSMSCOUT_HAVE_LIB_MARISA` (what the removed block pinned). Without
  the clear, a build directory that configured while the shadowing header said the define keeps the probe's
  old answer — `evidence/falsified-cairo-ft-cache.log`.
- The static freetype port's closure (libpng, zlib, bzip2, brotli) follows `libfreetype.a` on the map libraries'
  link lines: `libosmscout-map` links `Freetype::Freetype` as soon as the library found it, and the compile now
  calls into it, so the archives that freetype itself does not contain have to be there.
- `buildSrc`'s `GeneratedFeatureHeaders` message no longer names `OSMSCOUT_FEATURES_DIR`, and `guidelines/Build.md`
  records the values the Android configure now produces; the label-wrapping section of `guidelines/MapRendering.md` stops claiming the
  Android dependency list has no Pango, and records which features the configure does define.

## Impact

- Affected specs: `osmscout-native` (one requirement modified).
- Affected code: `app/src/main/cpp/CMakeLists.txt`, `buildSrc/src/main/kotlin/com/naviveylin/build/native/GeneratedFeatureHeaders.kt`
  and its test, two guideline sections.
- Behaviour: the Android build compiles `MapFeature`'s FreeType family resolution and the cairo backend's
  `OSMSCOUT_MAP_CAIRO_LOAD_FONT_FILE` path. No packaged stylesheet configures a font file (they name font
  families), so no drawn label changes and the wrapping that section documents stays what it measured; the FreeType/cairo text
  variant is the same one either way, because Pango's detection still does not resolve under the NDK.
