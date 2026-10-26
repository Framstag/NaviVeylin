# Design — the app stops rendering libosmscout's feature headers

## Root cause

One mechanism: the app rendered libosmscout's four `*Features.h` before `add_subdirectory(libosmscout)` and
prepended that directory, so every translation unit of `libosmscout-map` and `libosmscout-map-cairo` in the
Android build compiled against the app's block (marisa on, nothing else) rather than against what
`cmake/features.cmake` computed for the same configuration. §202's include-order fix (#136) is what let the
app's copy win; the values themselves were never a decision anybody wrote down (the pre-generation predates the
repository, `6b13586e`).

## The fix

Delete the pre-generation. The library already writes each header into its own CMake build tree and puts that
directory on the module's include path (`cmake/ProjectConfig.cmake`), and it does so with the values that its
own detection produced — FreeType and cairo-ft on for this configuration, Pango and Fontconfig off.

Three consequences came with it, each handled where it belongs:

1. **The marisa preset.** `set(OSMSCOUT_HAVE_LIB_MARISA 1 CACHE BOOL "" FORCE)` existed only so the app's own
   `configure_file` emitted the define. `features.cmake` computes the same value from `find_package(Marisa)`,
   which resolves through the `MARISA_*` presets this file already sets for vcpkg, before
   `add_subdirectory(libosmscout)`. Removed; `MapFeatures`/`CoreFeatures` keep marisa on
   (`evidence/feature-headers-after.txt`).
2. **Cache history.** `OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT` is a `check_cxx_source_compiles` result, and CMake
   caches it for the build directory; a directory that configured while a shadowing header said the define
   would keep that answer forever. The app clears the entry before `add_subdirectory`, so the probe runs again
   per configure. Falsified: with the clear removed and the entry forced empty the header says
   `/* #undef */`, with it restored the same forced cache yields `#define`
   (`evidence/falsified-cairo-ft-cache.txt`, `evidence/restored-cairo-ft-cache.txt`).
3. **The static freetype closure.** `libosmscout-map` links `Freetype::Freetype` as soon as the library found
   it — that link existed before this change and resolved to nothing because the compile never called into
   freetype. With FreeType on, `FontNameResolution.cpp` does, `sfnt.c.o` is pulled out of `libfreetype.a`, and
   the PNG/zlib/bzip2/brotli decoders it calls are in other archives. The link failed with
   `undefined symbol: png_create_read_struct` (`evidence/link-before-closure.log`). The app now puts those five
   archives behind `libfreetype.a` on the map libraries' link lines, in the order `CAIRO_LIBRARIES` already
   lists them. The link is unconditional because `Freetype::Freetype` is created inside `add_subdirectory` and
   an imported target is directory-scoped, so this directory cannot ask whether it exists.

## Blast radius

- The map libraries' feature macros change for the Android build only: `OSMSCOUT_MAP_HAVE_LIB_FREETYPE`,
  `OSMSCOUT_MAP_CAIRO_HAVE_LIB_FREETYPE`, `OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT` become defined. Pango and
  Fontconfig stay off (their detection does not resolve), so `MapPainterCairo::Layout` is the same text variant
  the label-wrapping section of `guidelines/MapRendering.md` measured — no wrapping re-measurement is owed.
- What newly compiles is the font-**file** path: `OSMSCOUT_MAP_CAIRO_LOAD_FONT_FILE` (a configured font file
  drawn through cairo-ft) and `FontNameResolution::CanReadFamilyFromFile()`. No packaged stylesheet configures
  a font file — the styles name font families — so the frames the app draws today are unchanged.
- The host build is unaffected: it configures its own tree with its own `features.cmake`.

## Rollback

Restore the removed block from `git show 7856a9d1:app/src/main/cpp/CMakeLists.txt`; the two `unset` lines and
the freetype closure are harmless without it, and the docs revert with the same commit.

## Alternatives the evidence eliminated

- **Keep the forced all-off set and correct the documentation** (option (a) of the item): leaves the Android
  build silently dropping features its own dependency set carries, and leaves the "why" a rule about the past.
- **Decide per feature with hand-written macros** (option (c)): the two libraries' macros already allow it, but
  it keeps a second place that must agree with `features.cmake` — the disagreement is the defect.
