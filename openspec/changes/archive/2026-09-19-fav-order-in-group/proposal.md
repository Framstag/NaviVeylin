# fav-order-in-group

## Why

Favorites inside a group are shown in the order they were added. There is no way to move a favorite — the important ones (home, work, the next waypoint) cannot be put at the top of the list, and the starred-chip bar inherits that arbitrary order. Users asked for control over the order of favorites within a group.

The data model already keeps order: `FavLocationGroup::favorites` is a `std::vector<FavLocation>` serialized as a JSON array, loaded back in array order. What is missing is a mutator: `FavoriteLocationService` offers `AddFavorite` (append), `DeleteFavorite`, `RenameFavorite`, `SetStarred`, `SetGroupColor` — and nothing that changes a favorite's position. Every layer above (JNI, Java API, `FavoriteRepository`, `FavoritesViewModel`) is missing the operation for the same reason. This change adds it — no data-model or file-format change.

## What Changes

- **Native reorder operation**: `FavoriteLocationService::MoveFavorite(groupName, favName, newIndex)` moves a favorite to a 0-based target index inside its group's vector (remove + insert). Out-of-range indices are clamped to the list bounds; unknown group/favorite returns `false`; moving to the current position is a success without change. `Save()` already writes the vector in order — no JSON change.
- **JNI + Java API**: new `moveFavorite(String groupName, String favName, int newIndex)` native method, declared on both the submodule Java API and the app-owned override, implemented in `OSMScoutClient.cpp` against the existing per-client `favService` instance.
- **Repository + ViewModel**: `FavoriteRepository.moveFavorite(...)` delegates to JNI, refreshes the `StateFlow` (which is an ordered `Map<String, List<FavoriteLocation>>`) and persists once; `FavoritesViewModel` exposes the action and ignores reorder commits while one is in flight.
- **Phone UI — drag and drop**: the group detail list in `FavoritesSheet` becomes reorderable via `sh.calvin.reorderable:reorderable:3.1.0` (Apache-2.0, already a permitted shipped license). Press-and-hold a favorite row, drag it, release to commit; the in-flight drag order lives in composable state (`guidelines/Design.md` §3), only the released position is persisted — one JNI call and one file write per committed reorder, never per drag frame. The "Add favorite" header row is not a draggable item and does not occupy a favorite index.
- **Starred chip bar follows the same order**: chips are listed by group, then by stored favorite order within the group. No chip-specific order storage.
- **Android Auto + desktop**: read-only parity — the AA `PlaceListTemplate` and the desktop `JavaScout` dialog show the same stored order. No reorder interaction is added on the car side: Car App Library templates have no drag-and-drop, and the change deliberately keeps the AA surface free of an interaction the platform cannot express. The desktop dialog can adopt the new API separately.
- **Additive, non-breaking**: no new JSON field, no schema version, no renumbered indices. Existing files load with their current (insertion) order, so users see no reordering after the update. No existing requirement is rewritten — all spec deltas are additions.
- **Out of scope**: group order (blocked by `std::map` group storage — separate change), a chip-specific order independent of favorite order.

## Capabilities

### New Capabilities

- `fav-ordering`: user-defined position of a favorite within its group — settable from the phone favorites sheet, persisted to the favorites JSON, restored on restart, per group, and reflected by every surface that lists favorites (group detail list, starred chip bar, AA place list).

### Modified Capabilities

Additive deltas only — each adds a new requirement, none rewrites an existing one:

- `fav-service`: the repository gains `moveFavorite(groupName, favName, newIndex)` (JNI delegation, ordered state re-emission, exactly one persist per successful move).
- `fav-management-ui`: the favorites sheet group detail list gains drag-and-drop reordering; the search-results list stays ordered by the stored order and is not reorderable.
- `fav-starred-chip-bar`: chip order is defined as group order, then stored favorite order within the group.
- `auto-favorites`: the AA place list reflects the stored favorite order (read-only parity, no reorder affordance on the car screen).

## Impact

- **Native submodule patch (minimal, upstreamable)** — `app/src/main/cpp/libosmscout/`:
  - `libosmscout-client/include/osmscoutclient/FavoriteLocationService.h` — `MoveFavorite` declaration + doc comment
  - `libosmscout-client/src/osmscoutclient/FavoriteLocationService.cpp` — implementation (exclusive lock, `std::vector` erase + insert, index clamping)
  - `libosmscout-client-java/src/OSMScoutClient.cpp` — `Java_com_framstag_libosmscout_client_OSMScoutClient_moveFavorite` wrapper
  - `libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClient.java` — native declaration
  - `Tests/src/FavoriteLocationServiceTest.cpp` — Catch2 tests (see design D1/R4)
  - No Android APIs are introduced (the CI Android-free gate must pass); nothing is touched in the frozen `Android/` directory.
- **App-owned JNI bridge**: `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` — declare `moveFavorite` (the override file, not the submodule copy).
- **App**: `app/src/main/java/com/naviveylin/data/FavoriteRepository.kt` (new suspend `moveFavorite`), `app/src/main/java/com/naviveylin/ui/favorites/FavoritesViewModel.kt` (action + in-flight guard), `app/src/main/java/com/naviveylin/ui/favorites/FavoritesSheet.kt` (reorderable group detail list, drag affordance, strings), `app/src/main/res/values/strings.xml` + `values-de/strings.xml` (drag handle content description).
- **Build**: `app/build.gradle.kts` — new dependency `sh.calvin.reorderable:reorderable:3.1.0` (no version catalog exists in this repo, dependencies are inline).
- **License compliance**: the new dependency is Apache-2.0, already in `licenses/license-policy.json` → `permitted.shipped`; the SBOM gains the component, so `generateLicenseAssets<Variant>` output (texts + `NOTICE`) must be regenerated and `checkLicensePolicy` must pass for both flavors.
- **Tests**: native `Tests/src/FavoriteLocationServiceTest.cpp`; `FakeOSMScoutClient` (`app/src/test/java/com/framstag/libosmscout/client/`) gains `moveFavorite`; `FavoriteRepositoryTest`; `FavoritesSheet` Compose tests (drag commit, header not draggable, dropped/aborted drag, other row actions still work); chip-bar order test; `auto/src/test/java/com/naviveylin/auto/FavoritesScreenTest.kt` order assertion.
- **Guidelines**: `guidelines/UI.md` §1 (parity table) — record that reorder is phone-only by platform constraint (no drag on Car App Library templates) while the resulting order is shared. `guidelines/Design.md` — no new principle expected (native boundary §5, threading §4 and testing §11 are followed as-is); confirm and note "no update required" if unchanged.
- **Specs**: new `openspec/specs/fav-ordering/spec.md`; deltas for `fav-service`, `fav-management-ui`, `fav-starred-chip-bar`, `auto-favorites`.
- **Rollback**: revert the submodule pointer bump, the Java declaration, the Kotlin wiring and the dependency. The JSON file format is unchanged, so data written by the new build stays readable by the old one; the last committed order simply persists as the list order.

**Scope**: the reorder interaction is phone-only (both distribution flavors build unchanged); AA/AAOS and desktop show the stored order read-only. This is a general favorites feature, not an Auto/AAOS feature.

**Classification**: additive (new API + new UI affordance, existing behavior unchanged for users who never drag); no breaking spec change.
