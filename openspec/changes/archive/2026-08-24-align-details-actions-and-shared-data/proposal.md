## Why

The details views still diverge in actions and edge-case data. Phone lacks a "Show" button outside POI search; Android Auto lacks favorite management entirely; phone titles can show raw coordinate labels where AA shows a generic "Location"; and shared logic (entry filtering, resolved-data assembly) is still duplicated between the two views instead of living in `:core`.

## What Changes

- **AA details: favorite management** — the AA details list gains "Save to favorites" / "Remove from favorites" action rows (phone parity). `AutoFavoritesProvider` (core interface, app implementation) gains add/remove operations; the details list shows the current favorite state and toggles it.
- **Navigation flow unchanged** — phone keeps "Route" (route panel, destination prefilled, start = current position); AA keeps "Navigate here" (immediate navigation). No changes to either flow.
- **Phone "Show" button always available** — the phone details dialog shows "Show on map" unconditionally (currently only when opened from POI search), matching AA's "Show" (closes details, centers the map on the object).
- **Title/area alignment (AA approach wins)** — the shared resolver excludes coordinate labels from the title fallback so unnamed objects show the generic "Location" instead of "51.50000, 7.40000" (AA behavior); area resolution is already shared — display verified and aligned.
- **Further centralization in `:core`** — a shared `DetailsData` bundle (title, address, area, destination name, filtered display entries) produced by one resolver call, plus shared entry filter/dedup (blank skip, street/address merge) and a shared action model — both UIs consume the same output, so future changes affect both variants at once.
- **Equal action labels + styling (general rule)** — navigation action renamed "Navigate to" and favorite actions "Add to Favorites"/"Remove from Favorites" in BOTH variants (phone "Route" renamed); phone restyled so the primary/secondary hierarchy matches (single primary action). A new general capability (`cross-variant-ui-parity`) requires same labels and styling for similar elements in both variants wherever the platform allows.

## Capabilities

### New Capabilities
- `cross-variant-ui-parity`: similar UI elements (actions, labels, styling) SHALL be identical in the phone and Android Auto variants wherever platform constraints allow.

### Modified Capabilities
- `auto-destination-details`: AA details list gains favorite add/remove actions; navigate/show semantics confirmed shared with phone.
- `enhanced-details-sheet`: phone details dialog gains an always-available "Show on map" button; title fallback excludes coordinate labels (generic "Location"). Navigation flow ("Route" → route panel) unchanged.
- `auto-favorites`: AA favorite provider gains add/remove operations used by the details list.

## Impact

- `core/.../details/DetailsResolver.kt` + new `DetailsData` bundle + shared entry filter — title coordinate-label exclusion, single resolved output.
- `core/.../AutoFavoritesProvider.kt` — add/remove API (interface); app implementation in `FavoriteRepository`/`AutoServiceModule`.
- `auto/.../DetailsScreen.kt` — favorite action rows, state observation, shared `DetailsData` consumption.
- `app/.../ui/map/LocationDetailsSheet.kt` + `MapCanvasScreen.kt` — "Show on map" unconditional, shared `DetailsData` consumption.
- `app/.../ui/map/MapCanvasViewModel.kt` — "Show on map" wiring for all entry points (no navigation-flow changes).
- Tests: `DetailsResolverTest`, `DetailsScreenTest`, `LocationDetailsDialogComposeTest`, new `AutoFavoritesProvider` tests.
- No native/JNI changes, no manifest changes.
