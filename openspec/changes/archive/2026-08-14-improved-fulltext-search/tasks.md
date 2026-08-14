# Tasks: Improved fulltext search

## 1. Native search fixes (submodule)

- [x] 1.1 In `app/src/main/cpp/libosmscout`, fetch upstream commit `78c2e668` and merge it; resolve conflicts in `OSMScoutClient.cpp` keeping NaviVeylin-local Android changes (AttachCurrentThread casts, render-logging comment)
- [x] 1.2 Verify the four search fixes are present in `OSMScoutClient.cpp`: unresolvable index refs are dropped, entries resolving to (0,0) are dropped, `freeTextEntries.resize()` only shrinks (never pads), and the result loop skips (0,0) entries
- [x] 1.3 Commit the submodule merge and bump the submodule pointer in the parent repo (`git add app/src/main/cpp/libosmscout`)
- [x] 1.4 Guard against invalid UTF-8 in serialized search entries: garbage string data from a corrupt text index/database aborts ART inside `NewStringUTF` (crash `input is not valid Modified UTF-8: illegal start byte 0x82`); added `IsValidUtf8` check in `OSMScoutClient.cpp` and drop invalid entries before serialization

## 2. Shared distance helper

- [x] 2.1 Add `app/src/main/java/com/naviveylin/util/DistanceFormat.kt` with `haversineDistanceMeters(lat1, lon1, lat2, lon2)` (returns meters)
- [x] 2.2 Add `formatDistanceKm(meters)` mirroring upstream: `< 10 km` → one decimal ("0.5 km"), `>= 10 km` → whole km ("12 km"), `Locale.ROOT`, "km" suffix

## 3. Distance in map search panel

- [x] 3.1 Pass current map center (`viewport.centerLat/centerLon` from `MapCanvasUiState`) into `SearchPanel` as parameters
- [x] 3.2 Render distance per `SearchResultItem` row: right-aligned, smaller font than the primary label (e.g. `bodySmall`/`labelSmall`, muted color), computed via `haversineDistanceMeters` + `formatDistanceKm` against the passed center

## 4. Distance in route panel search

- [x] 4.1 Pass current map center into `RoutePanel` from `MapCanvasScreen` (alongside the existing viewModel wiring)
- [x] 4.2 Render distance per route search result row in `RouteSearchResults`, same formatting and alignment as the map search panel

## 5. Tests

- [x] 5.1 Plain-JUnit unit tests for `formatDistanceKm`: sub-km precision, 10 km boundary (one decimal below, whole km at/above), unit suffix, `Locale.ROOT` stability
- [x] 5.2 Unit tests for `haversineDistanceMeters` with known coordinate pairs (e.g. Dortmund center to itself → 0 m; pairs with known approximate distances)
- [ ] 5.3 Verify no existing tests break (`./gradlew test`)

## 6. Verification

- [x] 6.1 Build native + app: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`
- [ ] 6.2 Manual device check with a map that has a text index: garbage free-text entries gone (no empty "(0,0)" rows), distance shown right-aligned in smaller font in both map and route panel search
