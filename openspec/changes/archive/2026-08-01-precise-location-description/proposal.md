## Why

Location search can return multiple results with identical street/address labels (e.g., same street name in different suburbs, or multiple POIs at one address). Users currently see only `label` + `adminRegionHierarchy` in search results — not enough to tell entries apart. Need richer disambiguation info inline.

## What Changes

- **Search result items** show additional distinguishing fields when multiple results share the same label
- **Disambiguation fields** displayed: `objectTypeName` (e.g., "building", "restaurant"), `postalArea`, and individual `region` components
- **Duplicate grouping**: results with identical `label` are visually grouped with extra detail lines per entry
- **No new native/JNI code** — all needed fields already exist in `LocationEntry` (`objectTypeName`, `postalArea`, `region`, `objectType`)
- **No new dependencies**

## Capabilities

### New Capabilities

- `precise-location-results`: Enhanced search result display that disambiguates same-address hits. When multiple `LocationEntry` results share the same `label`, each item shows additional detail (`objectTypeName`, `postalArea`, region tail) inline. Single-result items unchanged.

### Modified Capabilities

- `location-search`: Search result rendering updated to include disambiguation fields for duplicate-address entries. No change to search logic or backend — only UI presentation.

## Impact

- **Modified composable**: `SearchPanel.kt` — `SearchResultItem` gains disambiguation logic: detect duplicate labels, render extra detail lines
- **No ViewModel changes** — all data already in `LocationEntry`
- **No native/JNI changes**
- **No new dependencies**
