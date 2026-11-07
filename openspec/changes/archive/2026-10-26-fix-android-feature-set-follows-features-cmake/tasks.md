# Tasks — the Android build's feature set becomes features.cmake's

- [x] 1. Remove the app's pre-generation from `app/src/main/cpp/CMakeLists.txt` (the `foreach(_rel …)`
  `configure_file` loop, the `OSMSCOUT_FEATURES_DIR` variable and the `include_directories(BEFORE …)` that
  followed it), and the `OSMSCOUT_HAVE_LIB_MARISA` preset that only fed it; keep the `_sources_` note
  explaining why a copy in the submodule source tree is still removed by the build gate.
  *Verification:* `grep -n "configure_file\|OSMSCOUT_FEATURES_DIR\|include_directories(BEFORE"
  app/src/main/cpp/CMakeLists.txt` names none of the four templates.

- [x] 2. Clear the two cache entries that would outlive their configuration before `add_subdirectory(libosmscout)`:
  `OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT` (a cached `check_cxx_source_compiles` result) and
  `OSMSCOUT_HAVE_LIB_MARISA`.
  *Verification:* the falsification pair — with the `unset` removed and the cache entry forced empty the header
  says `/* #undef */`; with it restored, the same forced-empty entry yields `#define`
  (`evidence/falsified-cairo-ft-cache.txt`, `evidence/restored-cairo-ft-cache.txt`).

- [x] 3. Put the static freetype port's closure (libpng, zlib, bzip2, brotli) behind `libfreetype.a` on the map
  libraries' link lines — `libosmscout-map` links `Freetype::Freetype` and now calls into it.
  *Verification:* the link fails without it (`evidence/link-before-closure.log`, `undefined symbol:
  png_create_read_struct`) and succeeds with it (`evidence/native-build-arm64.log`).

- [x] 4. Reconfigure the arm64-v8a build tree and read the four headers it now holds.
  *Verification:* `MapFeatures.h` defines `OSMSCOUT_MAP_HAVE_LIB_FREETYPE`; `MapCairoFeatures.h` defines
  `OSMSCOUT_MAP_CAIRO_HAVE_LIB_FREETYPE` and `OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT`, and leaves Pango and
  Fontconfig undef; `build.ninja` names no app-rendered feature-header directory
  (`evidence/feature-headers-after.txt`, `evidence/include-path-and-link-line.txt`).

- [x] 5. Prove the compiled FreeType path is in the shipped library.
  *Verification:* `nm -C --defined-only …/libosmscout_mapd.so | grep " FT_"` lists the entry points
  (`evidence/nm-freetype-symbols.txt`).

- [x] 6. `buildSrc`: the `GeneratedFeatureHeaders` message no longer names `OSMSCOUT_FEATURES_DIR`, and its
  unit test asserts the new wording.
  *Verification:* `./gradlew -p buildSrc test --tests "*GeneratedFeatureHeadersTest*"` green.

- [x] 7. Update the owning prose: `guidelines/Build.md`, its "Configured feature headers" section (the values the Android
  configure produces, the two cleared cache entries, the freetype closure) and the label-wrapping section of `guidelines/MapRendering.md`
  (Pango's detection is what fails, not the dependency list; and which features the configure does define).
  *Verification:* `grep -n "no Android vcpkg triplet installs pango" guidelines/MapRendering.md` finds nothing.

- [x] 8. Run the change's gate once (`tools/loop/gate.sh --base 7856a9d1`) and copy its report into `evidence/`.
  *Verification:* `evidence/gate.log`.
