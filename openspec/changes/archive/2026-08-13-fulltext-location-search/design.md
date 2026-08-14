## Context

Current state: the submodule at `app/src/main/cpp/libosmscout` already contains
upstream full-text search (PR #1758, commits `fba4dfce3`/`3f8070f96` plus
NaviVeylin local commits). The JNI bridge (`OSMScoutClient.cpp`,
`DoSearchLocations`) merges free-text hits into structured results — dedup by
object file offset, limit enforcement, basemap excluded — but every piece is
gated behind `OSMSCOUT_HAVE_LIB_MARISA`. That macro is produced by
`cmake/features.cmake` from `MARISA_FOUND`, which comes from `find_package(Marisa)`
via the module `cmake/FindMarisa.cmake`. `FindMarisa.cmake` uses
`find_path`/`find_library` with pkg-config hints and system paths only — under
the NDK cross-compile those never resolve, and marisa is not installed anyway.

NaviVeylin's vcpkg flow (`setup-vcpkg.sh`) installs deps into
`vcpkg/installed/<triplet>` per Android triplet and the app-level
`app/src/main/cpp/CMakeLists.txt` pre-sets `find_package` cache variables
pointing into that directory (the established pattern for Cairo, zlib,
libxml2, protobuf, freetype — the built-in find modules fail under NDK
otherwise). The vendored vcpkg already ships the `marisa-trie` port (0.3.1,
pure C++17, `supports: !windows`).

App code needs no changes: `MapCanvasViewModel.searchLocations`,
`RoutePanelViewModel`, and `AutoServiceModule` all call the handle-based
`searchLocations(query, limit, adminRegionHandle)` overload, which routes
through `DoSearchLocations` and picks up free-text search automatically once
the macro is defined.

## Goals / Non-Goals

**Goals:**
- marisa-trie installed for arm64-android, arm-neon-android, x64-android
- `find_package(Marisa)` succeeds in the NDK CMake build → `OSMSCOUT_HAVE_LIB_MARISA` defined
- `TextSearchIndex.cpp` compiled into OSMScout core; marisa linked into `libosmscout_client_java.so`
- Free-text search live in the app with zero Kotlin/JNI source changes

**Non-Goals:**
- No changes to libosmscout upstream, the JNI bridge, or app code
- No UI/ranking changes (JavaScout's matchRank/dedup refinements stay in JavaScout; NaviVeylin consumes merged results as-is)
- No map data / import changes (text index must exist in the data — out of app scope)
- No desktop/host build support (Android only, per existing setup)

## Decisions

### 1. Pre-set marisa cache variables in app CMakeLists (chosen)

Follow the exact pattern used for Cairo/zlib/libxml2: in
`app/src/main/cpp/CMakeLists.txt`, inside the `if(VCPKG)` block, add:

```cmake
set(MARISA_FOUND 1 CACHE BOOL "" FORCE)
set(MARISA_INCLUDE_DIRS "${VCPKG}/include" CACHE PATH "")
set(MARISA_LIBRARIES "${VCPKG}/lib/libmarisa.a" CACHE FILEPATH "")
```

`find_package(Marisa)` then short-circuits on the cached result; the
`if(MARISA_FOUND)` blocks in `libosmscout/CMakeLists.txt` (source list +
`target_link_libraries(OSMScout ${MARISA_LIBRARIES})`) and
`cmake/features.cmake` (`OSMSCOUT_HAVE_LIB_MARISA`) all light up. The app
CMakeLists pre-generates `CoreFeatures.h` via `configure_file` before
`add_subdirectory(libosmscout)`, so pre-setting `MARISA_FOUND`/`MARISA_INCLUDE_DIRS`/
`MARISA_LIBRARIES` (and thus `OSMSCOUT_HAVE_LIB_MARISA`) before that loop is
also what makes the define appear in the generated header for the
client-java compile.

- Alternative (rejected): relying on `FindMarisa.cmake`'s pkg-config hints
  (`PKG_CONFIG_PATH` is already set to the triplet's `lib/pkgconfig`). vcpkg
  does generate `marisa.pc` for the port, but pkg-config for Android
  cross-targets is fragile and every other dependency in this build uses the
  pre-set cache-var pattern — consistency wins.
- Alternative (rejected): `set(MARISA_FOUND 1)` uncommented in
  `libosmscout/libosmscout/CMakeLists.txt` (the file's line 1-2 hint). That
  edits the submodule and breaks the "no submodule changes" boundary; the
  app-level CMakeLists is the right layer.

### 2. Static link of marisa into the JNI .so

marisa-trie installs as `libmarisa.a` (static). `OSMScout` is a static
library; `target_link_libraries(OSMScout ${MARISA_LIBRARIES})` is PUBLIC, so
marisa is pulled into the core shared library (`libosmscoutd.so`), which the
JNI bridge `libosmscout_client_java.so` links against and resolves
`TextSearchIndex` from at runtime. Verified: all three ABIs contain 39
`marisa::Trie` symbols plus `TextSearchIndex::Load/Search` in
`libosmscoutd.so`. Static is correct here: it keeps the core `.so`
self-contained, matching how every other vcpkg dep (cairo, protobuf, …) is
already linked. No `SHARED` variant needed.

### 3. `OSMSCOUT_HAVE_LIB_MARISA` in client-java compile

`OSMScoutClient.cpp` includes `<osmscout/db/TextSearchIndex.h>` under
`#ifdef OSMSCOUT_HAVE_LIB_MARISA`. The define arrives via the pre-generated
`CoreFeatures.h` (see Decision 1) which is on the include path through the
OSMScout PUBLIC includes. No per-target define needed in client-java's own
CMakeLists.

## Risks / Trade-offs

- [marisa-trie port may not build for Android triplets] → pure C++17 CMake
  project, `supports: !windows`; verify with a single
  `./vcpkg install marisa-trie:arm64-android` before editing anything else;
  if the port fails, add an overlay port under `vcpkg-overlays/` (pattern
  already used for triplets).
- [vcpkg 2026-07-13 segfaults (SIGSEGV) during post-build validation and the
  install phase for Android triplets] → two workarounds, both in place:
  (1) overlay port `vcpkg-overlays/marisa-trie` sets
  `VCPKG_POLICY_SKIP_ALL_POST_BUILD_CHECKS`; (2) the install-phase crash was
  traced to a corrupt 0-byte `installed/vcpkg/info/libosmscout_1.1.1_arm64-android.list`
  (leftover from an aborted `vcpkg install libosmscout` run) that crashed
  vcpkg's conflict checker — the file was reconstructed and the vcpkg
  libosmscout package purged. Do not re-add `libosmscout` to
  `setup-vcpkg.sh` DEPS.
- [vcpkg-installed libosmscout headers shadow the submodule] → the vcpkg
  libosmscout package installs stale `osmscout/*.h` under
  `installed/<triplet>/include`, which wins over the submodule headers in the
  NDK build (`-I` order) and breaks compilation (e.g. missing
  `ReplaceString`). `libosmscout[cairo]` was removed from `setup-vcpkg.sh`
  DEPS; the app builds libosmscout from the source submodule only.
- [Static libmarisa.a may need extra link flags (C++17, no exceptions issues expected)] → if the final link fails, add missing transitive deps to
  `MARISA_LIBRARIES` pre-set; marisa-trie has no runtime deps beyond the C++
  stdlib, so this is unlikely.
- [Build size grows (marisa trie code in .so)] → acceptable; trie
  implementation is small (~100 KB class), and only linked into the single
  native lib.
- [Free-text hits change result composition; POI hits may rank after structured
  hits by design] → matches upstream/JavaScout behavior; result ordering is
  unchanged for structured matches, free-text fills remaining limit slots.
- [Map data without text index shows no free-text results] → graceful warning
  in native code, structured search unaffected (spec'd fallback scenario).

## Migration Plan

1. `./vcpkg install marisa-trie:arm64-android` (+ other triplets) — or extend
   `setup-vcpkg.sh` DEPS and run it
2. Edit `app/src/main/cpp/CMakeLists.txt` pre-sets
3. `./gradlew :app:assembleDebug` (all 3 ABIs), verify `OSMSCOUT_HAVE_LIB_MARISA`
   in build log and search works on a map with text index
4. Rollback: revert the two file changes; feature compiles out cleanly as
   before (marisa absent → structured search only)

## Open Questions

- Whether the shipped map data / map provider includes text index files —
  affects whether free-text hits appear in practice, but not the code change.
