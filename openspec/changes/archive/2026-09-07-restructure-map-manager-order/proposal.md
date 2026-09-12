## Why

The map manager screen (`MapManagerScreen.kt`) renders its sections in an order that fights the user's task flow: the search field is buried below the basemap section, the active-downloads section sits mid-list (the `map-download-ui` spec already mandates it at the top), the loading spinner and error banner appear between content sections, and a basemap download renders progress twice (once in `BasemapSection`, once in `ActiveDownloadsSection`). All content is correct — the ordering and duplication are the problem.

## What Changes

- Reorder the `LazyColumn` sections in `MapManagerScreen.kt` to: provider row → search field → error banner → active downloads → basemap section → installed maps → available maps.
- Move the loading spinner to the top of the content area (under the provider row) instead of between the downloads and installed sections; when loading with no entries yet, show a centered spinner instead of scattered sections.
- Move the error banner directly under the provider row (errors originate from fetch/delete operations).
- Hide the installed-maps section while a search query is non-blank, so search results (available maps) stay contiguous and the search field's scope is unambiguous.
- Unify the "Installed maps" / "Available maps" section header styling (same `titleSmall` + bold + same color).
- Remove the duplicate basemap progress indication: while the basemap is downloading, `BasemapSection` collapses to a compact status line and the progress row (with cancel) lives in the active-downloads section (per the `map-download-ui` "Basemap download shown with progress" scenario).
- Add/extend Compose UI tests for the new ordering and the basemap collapse behavior.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `map-download-ui`: ordering requirements — active downloads at the top of the content area, search field placement, loading/error placement, installed section hidden while searching, consistent section headers.
- `basemap-ui`: basemap section behavior while downloading — collapses to a compact status line; full progress indication moves to the active-downloads section (no duplicate progress bars).

## Impact

- `app/src/main/java/com/naviveylin/ui/mapmanager/MapManagerScreen.kt` — reorder `LazyColumn` items; move loading/error; hide installed section while searching; unify headers; drop `BasemapDownloadRow` from `ActiveDownloadsSection`.
- `app/src/main/java/com/naviveylin/ui/mapmanager/BasemapSection.kt` — compact status rendering while `isDownloading`.
- `app/src/main/res/values/strings.xml` — possibly one new string for the compact basemap status (e.g. "Downloading world basemap…"); otherwise no string changes.
- `app/src/test/java/com/naviveylin/ui/mapmanager/BasemapSectionComposeTest.kt` — extend with downloading-state test (compact status, no duplicate progress).
- New test file for `MapManagerScreen` section ordering (Robolectric + Compose, per the classloader rule in AGENTS.md).
- Guidelines: `guidelines/UI.md` — check whether section-ordering guidance for the map manager belongs there; update if a principle is superseded.
- Additive UI change, no breaking API changes, no native/JNI impact. Rollback: revert the screen reorder; no data migration.

## Decisions

- Search stays available-only (spec as-is); installed section hides while searching to keep results contiguous.
- Active downloads sit below the provider row + search (top of the content area), not strictly the first item — the provider row and search are the screen's toolbar.
- Basemap keeps its own section (not merged into installed maps), placed after active downloads so it groups with "on device" content.
