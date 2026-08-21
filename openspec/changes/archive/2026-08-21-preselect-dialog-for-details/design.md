# preselect-dialog-for-details Design

## Context

See proposal.md — Why. Current state:

- **Phone**: long-press → `MapCanvasViewModel.onLongPress(lat, lon)` → `client.getDescription(lat, lon, mag)` (JNI picks single best) → `LocationDetailsSheet` (ModalBottomSheet). JNI `getDescription` in `OSMScoutClient.cpp` already collects candidates with ranking metadata (150 m radius, visibility scoring, `VERY_CLOSE`/`MAX_SMALL_AREA_SIZE` constants) and picks the best.
- **AA**: `MapScreen.onClick` → `onLocationSelected` → details screen calling `getDescription` + `getAddressAt`. `androidx.car.app` 1.7.0 `SurfaceCallback` has no long-press callback — tap is the only selection gesture.
- **Upstream**: libosmscout commit `ace943087` (PR #1771) implements exactly this feature for JavaScout: `getDescriptionCandidates` JNI method, `ObjectDescription` identity fields, `CandidatePickerOverlay`. NaviVeylin submodule (`naviveylin-local` @ `a7840d1d7`) is behind it.

## Goals / Non-Goals

**Goals:**
- Phone: long-press shows ranked candidate picker; selection opens details sheet.
- AA: tap with multiple objects shows candidate picker; selection opens details screen.
- JNI: `getDescriptionCandidates` returns full ranked list with identity; `getDescription` unchanged for other callers.
- Match upstream JavaScout behavior/API where possible.

**Non-Goals:**
- No AA long-press gesture (API doesn't expose it — tap is the trigger).
- No changes to search/POI/favorites details flows (they keep `getDescription`).
- No candidate re-fetch by identity — picker carries full descriptions.

## Decisions

### D1: JNI API — new `getDescriptionCandidates` native method

Add `native List<ObjectDescription> getDescriptionCandidates(double lat, double lon, int magnification)` to `OSMScoutClient.java`, implemented in `OSMScoutClient.cpp`. `ObjectDescription` gains `objectRefType` ("node"/"way"/"area"), `objectTypeName`, `objectFileOffset`. Existing `getDescription` stays as a wrapper returning the top-ranked candidate.

- **Why**: mirrors upstream `ace943087` exactly (same signatures, same identity fields) — future upstream syncs stay clean. Callers that want single-best (search, POI, favorites) keep the existing method.
- **Alternative considered**: add a `returnAll` flag to `getDescription` — rejected: muddies the API, diverges from upstream, and the two call patterns (single vs. list) are genuinely different.

### D2: Submodule strategy — cherry-pick `ace943087` onto `naviveylin-local`

Cherry-pick the upstream commit and resolve conflicts, keeping NaviVeylin's local changes. Dry-run showed conflicts in 6 files: `MainController.java`, `SearchOverlay.java`, `DescriptionEntry.java`, `OSMScoutClient.java`, `ObjectDescription.java`, `OSMScoutClient.cpp` (+ delete/modify on `PoiCategories.java`).

- **Why**: minimal diff; keeps local ranking improvements (150 m radius, visibility scoring, `VERY_CLOSE` constants) and local JavaScout fixes. Upstream's `OSMScoutClient.cpp` refactor (401 lines) restructures the same candidate collection — conflict resolution is mostly merging the ranking constants into the refactored code.
- **Alternative considered**: bump submodule to upstream master — rejected: pulls unrelated upstream changes, risks breaking local customizations.
- **Fallback**: if conflict resolution proves messy, port the JNI changes manually (extract candidate collection/ranking into a helper, add `getDescriptionCandidates` marshaling, keep `getDescription` as top-ranked wrapper).

### D3: Phone UI — ModalBottomSheet candidate picker

New `CandidatePickerSheet` composable (ModalBottomSheet, same pattern as `LocationDetailsSheet`): list of candidate rows in ranking order, each showing name + type + description snippet (search-result format per spec). Tap row → details sheet. Dismiss (swipe/outside/back) → no details.

- **Why**: consistent with the existing details-sheet pattern and `enhanced-details-sheet` spec (map visible behind, draggable). Rows have room for the description text the spec requires.
- **Alternative considered**: `AlertDialog` — rejected: cramped for description text, inconsistent with the M3 sheet pattern used everywhere else.

### D4: Phone state flow

`MapCanvasUiState` gains `candidateDescriptions: List<ObjectDescription> = emptyList()` and `showCandidatePicker: Boolean = false`.

- `onLongPress(lat, lon)`: call `getDescriptionCandidates` on `Dispatchers.Default` → empty list → no picker (existing "no objects" behavior); non-empty → `showCandidatePicker = true`.
- `onCandidateSelected(desc)`: `objectDescription = desc`, `showDetailsSheet = true`, `isLongPress = true`, `showCandidatePicker = false`; marker at `desc.objectLat/objectLon` (fallback to press point on NaN — spec `long-press-details`).
- `dismissCandidatePicker()`: clear candidates, hide picker, no details.
- `dismissDetailsSheet()` unchanged (already clears `objectDescription`/`isLongPress`).

### D5: AA flow — tap → candidates

`MapScreen.onClick` → `getDescriptionCandidates(lat, lon, mag)` off-main (existing `withContext(Dispatchers.Default)` pattern) → size > 1 → push new `CandidatePickerScreen` (ListTemplate, rows in ranking order); size == 1 → details screen directly (existing behavior); size == 0 → details screen with coordinates (existing behavior). Selecting a candidate pushes the details screen passing the selected `ObjectDescription` — no re-query.

- **Why**: tap is the only selection gesture AA exposes; the candidates query replaces the current single `getDescription` call in the details screen. Passing the description avoids a second JNI round-trip.
- **Alternative considered**: keep details screen re-querying `getDescription` — rejected: wasteful, and the picker already has the full description.

### D6: Candidate identity

Identity fields (`objectRefType`, `objectTypeName`, `objectFileOffset`) are carried per candidate for display (type name in the row) and future use (e.g., marker hit-testing). No re-fetch by identity — the picker holds full `ObjectDescription` per candidate.

## Risks / Trade-offs

- [Cherry-pick conflicts in `OSMScoutClient.cpp`] → resolve keeping local ranking constants; verify with a native build (`./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`).
- [Dense areas → many candidates → JNI marshaling cost] → ranking already filters by visibility; run off-main thread. If profiling shows pain, cap the list (would be a spec change — "all reasonable" — so only with user sign-off).
- [AA tap latency from heavier candidates query] → off-main thread, same pattern as existing details screen; single-object case is one extra query vs. today.
- [`getDescriptionCandidates` calls `DescriptionService::GetDescription()` per candidate] → bounded by candidate count in 150 m radius; acceptable for typical urban density.

## Migration Plan

1. Cherry-pick `ace943087` in submodule, resolve conflicts, verify native build.
2. Phone: add state + picker sheet + ViewModel wiring (additive; old direct-details behavior replaced by picker).
3. AA: tap → candidates → picker screen (additive; new screen).
4. Rollback: revert the change commit — JNI method is additive, `getDescription` untouched, so no data/API migration needed.

## Open Questions

- Candidate count cap for very dense areas (perf tuning, non-blocking — would touch the "all reasonable" spec wording).
