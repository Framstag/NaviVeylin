## Context

See `proposal.md` — Why. The details views share `DetailsResolver` (address/area/title) but still duplicate entry filtering and resolved-data assembly, and diverge in actions: AA lacks favorite management, phone lacks an unconditional "Show on map", and phone titles can render raw coordinate labels. Navigation flows stay as-is (phone "Navigate to" → route panel; AA "Navigate to" → immediate nav), but the action labels are now identical in both variants, and a general parity rule (`cross-variant-ui-parity`, documented in `guidelines/UI.md`) requires same labels/styling for similar elements wherever the platform allows.

## Goals / Non-Goals

**Goals:**
- One `DetailsData` bundle in `:core` consumed by both UIs — future changes affect both variants at once.
- AA details: favorite save/remove (phone parity), reactive state.
- Phone: "Show on map" always available; coordinate-label titles → generic "Location".
- Navigation flows untouched.

**Non-Goals:**
- No changes to the navigation flows: phone "Navigate to" opens the route panel; AA "Navigate to" starts navigation immediately.
- No AA group-picker UI — AA saves to a default group (phone keeps the picker).
- No native/JNI changes.

## Decisions

### 1. `DetailsData` bundle in core
`DetailsResolver.resolve(input, nameHint)` returns one `DetailsData(title, address, area, destinationName, displayEntries)` — displayEntries = description entries after shared filtering (blank label/value dropped, `Location/Address` + `Location/Location` dedup against the merged address row). Phone's `displayEntries` mapping and AA's `buildAttributeList` both become thin adapters over this single output. The `COORDINATE_LABEL_REGEX` stays in core.

Rationale: the previous change centralized resolution; this completes it by centralizing the *assembly* both UIs render. Alternatives: keep per-view filtering — rejected (that is the duplication this change removes).

### 2. Title excludes coordinate labels
`resolveTitle` treats a label matching `COORDINATE_LABEL_REGEX` as no title: name → address → non-coordinate label → nameHint → "Location". Phone long-press labels are coordinates, so unnamed objects now show "Location" (AA behavior) instead of "51.50000, 7.40000". AA nameHint labels are search labels (rarely coordinates) — behavior unchanged there, but the rule is shared.

### 3. AA favorites via provider + action rows
`AutoFavoritesProvider` (core interface, app impl) gains `addFavorite(name, lat, lon)` and `removeFavorite(lat, lon)`; `favoriteLocations()` already streams state reactively. `DetailsScreen` already observes favorites for preview markers — the same flow drives the favorite action row: destination (lat, lon) present in the stream → "Remove from Favorites", else "Add to Favorites". Save uses the resolved title as name and a default group constant (phone's group picker stays phone-only). Action rows are glyph-marked like ▶ Navigate to / ◎ Show (e.g. ★ Add to Favorites / ☆ Remove from Favorites) — ListTemplate actions remain FAB-icon-only, so rows it is.

The labels are equal in both variants (spec: `cross-variant-ui-parity`): "Navigate to", "Show", "Add to Favorites", "Remove from Favorites". Phone styling: one primary (filled "Navigate to"), secondary outlined ("Show", "Add to Favorites"), destructive error-colored ("Remove from Favorites").

Rationale: reuse the existing reactive favorites stream instead of a new state channel; matches the phone's `FavoriteRepository` semantics (add/remove by coordinates).

### 4. Phone "Show on map" unconditional
`MapCanvasScreen` passes `onShowOnMap` always (currently gated on `detailsFromPoiSearch`); `viewModel.showOnMap()` already exists and centers the map. The dialog's existing "Show on map" button renders whenever the callback is non-null — no dialog change needed beyond the wiring.

### 5. Tests move with the logic
- `DetailsResolverTest`: coordinate-label title → "Location"; `DetailsData` bundle fields; shared filter/dedup.
- `DetailsScreenTest`: favorite action rows (save/remove/state), glyph titles.
- `LocationDetailsDialogComposeTest`: "Show on map" visible from long-press + search; coordinate-label title.
- New `AutoFavoritesProvider` fake-based tests for add/remove.

## Risks / Trade-offs

- [AA favorite save needs a group name] → Default group constant; phone picker unaffected. If users want AA group choice later, extend provider with a group param (spec-compatible).
- [Coordinate-label exclusion changes phone titles for unnamed long-press objects] → Intended (AA approach wins); covered by new compose test.
- [DetailsData refactor touches both UIs] → Both test suites act as regression nets; run `:app:test` + `:auto:test` before/after.
- [Favorites stream is group-keyed] → `isFavorite` check scans all groups for matching coordinates; fine at favorite counts.

## Migration Plan

1. Core: `DetailsData` + shared filter + title coordinate-label rule; resolver tests.
2. Core: `AutoFavoritesProvider` add/remove; app impl via `FavoriteRepository`; provider tests.
3. AA: `DetailsScreen` consumes `DetailsData`, adds favorite action rows; `DetailsScreenTest`.
4. Phone: `MapCanvasScreen` unconditional Show wiring; `LocationDetailsDialogComposeTest`.
5. Full `./gradlew test --max-workers=1` + single-ABI smoke build.

## Open Questions

None — default-group choice for AA favorites is the only judgment call and is covered by decision 3.
