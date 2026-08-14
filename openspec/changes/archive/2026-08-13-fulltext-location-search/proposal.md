# Fulltext Location Search

## What Changes

NaviVeylin's search today only performs structured location search
(`LocationService::SearchForLocationByString` via the JNI bridge). Upstream
libosmscout gained full-text search over POIs, locations, regions and other
named objects, based on a MARISA text index (`TextSearchIndex`), and
libosmscout-client-java already integrates it: free-text hits are merged with
structured results, deduplicated by object reference, and truncated to the
requested limit (upstream PR #1758 "Fulltext search in JavaScout", present in
our submodule at `fba4dfce3` / merged `3f8070f96`).

In our Android build this code is compiled out: it is gated behind
`OSMSCOUT_HAVE_LIB_MARISA`, and marisa is not part of the vcpkg cross-compiled
dependency set (`setup-vcpkg.sh`), so `find_package(Marisa)` never succeeds and
`TextSearchIndex.cpp` is excluded from the core library.

This change enables the feature by adding `marisa-trie` to the vcpkg
dependencies for all three Android triplets and wiring it into the NDK CMake
build, so `OSMSCOUT_HAVE_LIB_MARISA` is defined and the existing free-text
search path in the JNI bridge compiles and runs. No JNI bridge or app-code
changes are expected — the submodule already implements the search behavior,
matching JavaScout's `search-free-text` requirements.

## Capabilities

### New Capabilities

- `search-free-text`: Free-text search over the MARISA text index for POIs,
  locations, regions and other objects, in addition to the structured location
  search. Free-text results are merged with structured results, deduplicated by
  object reference, and truncated to the requested limit. Falls back gracefully
  (structured search only, with a warning) when a database has no text index.

### Modified Capabilities

- `osmscout-native`: External dependency cross-compilation extended with
  `marisa-trie` for all three Android triplets, and the NDK CMake build now
  finds and links marisa so the core library compiles with
  `OSMSCOUT_HAVE_LIB_MARISA` and includes `TextSearchIndex`.
- `location-search`: Suggestions-while-type now also surfaces free-text matches
  (POI/business names, region names) that structured search misses, e.g.
  searching "cafe central" finds "Café Central". Behavior contract otherwise
  unchanged.

## Impact

- **`setup-vcpkg.sh`**: add `marisa-trie` to the dependency list; installed for
  `arm64-android`, `arm-neon-android`, `x64-android` (port ships in vendored
  vcpkg as `marisa-trie` 0.3.1, pure C++17, `supports: !windows`).
- **`app/src/main/cpp/CMakeLists.txt`**: pre-set `MARISA_INCLUDE_DIRS`,
  `MARISA_LIBRARIES` and `MARISA_FOUND` cache variables pointing at the vcpkg
  triplet install (same pattern as the existing Cairo/zlib/libxml2 pre-sets),
  so `find_package(Marisa)` in libosmscout's `cmake/features.cmake` succeeds
  and `OSMSCOUT_HAVE_LIB_MARISA` is configured into `CoreFeatures.h`.
- **libosmscout core**: `TextSearchIndex.cpp` joins the build;
  `target_link_libraries(OSMScout ${MARISA_LIBRARIES})` propagates into the
  final `libosmscout_client_java.so`.
- **libosmscout-client-java submodule**: no changes — free-text search, result
  merging, dedup and limit handling already implemented in `OSMScoutClient.cpp`
  behind `OSMSCOUT_HAVE_LIB_MARISA`; reachable through the existing
  `searchLocations(query, limit, adminRegionHandle)` API the app already uses
  (`MapCanvasViewModel`, `RoutePanelViewModel`, AutoSearchProvider).
- **Map data**: free-text hits only occur for databases that contain a text
  index (created at import time). Databases without one fall back to structured
  search with a warning; the basemap is deliberately never searched.
- **Tests**: unit tests unaffected — the JNI stub in `app/src/test/jniLibs/`
  does not exercise marisa.
