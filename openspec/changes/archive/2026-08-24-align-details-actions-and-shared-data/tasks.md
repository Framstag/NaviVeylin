## 1. Core: DetailsData bundle + shared filter

- [x] 1.1 Add `DetailsData(title, address, area, destinationName, displayEntries)` to `core/.../details/` and a `DetailsResolver.resolve(input, nameHint)` returning it; move the shared entry filter (blank label/value drop, `Location/Address` + `Location/Location` dedup) into core — verify `./gradlew :core:compileDebugKotlin` succeeds
- [x] 1.2 Title rule: labels matching `COORDINATE_LABEL_REGEX` are excluded from the title fallback (name → address → non-coordinate label → nameHint → "Location") — verify `./gradlew :core:compileDebugKotlin` succeeds
- [x] 1.3 Extend `DetailsResolverTest`: coordinate-label title → "Location", `DetailsData` bundle fields, shared filter/dedup — verify `./gradlew :core:testDebugUnitTest` passes

## 2. Core: favorites provider add/remove

- [x] 2.1 Add `addFavorite(name, lat, lon)` + `removeFavorite(lat, lon)` to `AutoFavoritesProvider` (core interface); implement in app via `FavoriteRepository`/`AutoServiceModule` (default group constant) — verify `./gradlew :app:compileMobileDebugKotlin` succeeds
- [x] 2.2 Add provider tests (add persists, remove deletes, state flow updates) — verify `./gradlew :app:testMobileDebugUnitTest` passes

## 3. AA details screen

- [x] 3.1 `DetailsScreen` consumes `DetailsData` (single resolver call) and adds favorite action rows: "★ Save to favorites" / "☆ Remove from favorites" (glyph-marked, like ▶ Navigate here), state derived from the existing favorites stream by coordinates — verify `./gradlew :auto:compileDebugKotlin` succeeds
- [x] 3.2 Extend `DetailsScreenTest`: favorite rows shown per state, save/remove invoke provider, glyph titles — verify `./gradlew :auto:testDebugUnitTest` passes

## 4. Phone details dialog

- [x] 4.1 `MapCanvasScreen` passes `onShowOnMap` unconditionally (drop the `detailsFromPoiSearch` gate); `LocationDetailsDialog` consumes `DetailsData` — verify `./gradlew :app:compileMobileDebugKotlin` succeeds
- [x] 4.2 Extend `LocationDetailsDialogComposeTest`: "Show on map" visible from long-press and search; coordinate-label title shows "Location" — verify `./gradlew :app:testMobileDebugUnitTest` passes

## 5. Integration verification

- [x] 5.1 Full test pass: `./gradlew test --max-workers=1` — verify all modules green
- [x] 5.2 Smoke build single ABI: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` — verify APK assembles
- [x] 5.3 Manual: on car display save/remove a favorite from the details list; on phone open details from long-press and confirm "Show on map" + generic "Location" title for unnamed objects — verified
- [x] 5.4 AA details header shows the caller-provided name (POI name) instead of the address when the description lacks a `General/Name` entry (nameHint fed as `input.name`, phone parity; destination name prefers the name too); spec delta updated ("Title shows caller-provided name before address") — core + auto tests green
- [x] 5.5 Rename navigation action to "Navigate to" in BOTH variants (phone "Route" → "Navigate to", AA "▶ Navigate here" → "▶ Navigate to"); phone restyle: Add to Favorites becomes outlined (secondary), "Navigate to" stays the single filled primary; spec deltas (RENAMED + MODIFIED enhanced-details-sheet, MODIFIED auto-destination-details req 2 + visually-marked) — tests green
- [x] 5.6 Favorites labels aligned in both variants ("★ Add to Favorites" / "☆ Remove from Favorites" on AA, matching phone); new capability `cross-variant-ui-parity` (same labels + styling for similar elements in both variants where platform allows); proposal updated — validate + auto tests green
