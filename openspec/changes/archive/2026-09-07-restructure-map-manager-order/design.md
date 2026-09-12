## Context

`MapManagerScreen.kt` renders one `LazyColumn` with items in this order: provider row, basemap section, search field, error banner, active downloads, loading spinner, installed maps, available maps. All content is correct; the ordering deviates from the `map-download-ui` spec (active downloads "at the top") and a basemap download renders progress twice (`BasemapSection` + `BasemapDownloadRow` in `ActiveDownloadsSection`). See proposal.md — Why.

Constraints: single screen, no ViewModel state changes needed (pure recomposition reorder), Compose + Material 3, Robolectric Compose tests with the classloader rule from AGENTS.md (any test instantiating `FakeOSMScoutClient` must run under `RobolectricTestRunner` with the default sandbox).

## Goals / Non-Goals

**Goals:**
- Fixed, task-oriented section order: provider → search → error → downloads → basemap → installed → available.
- Single progress indication for basemap downloads (active-downloads section).
- Loading and error feedback at the top of the content area.
- Installed section hidden while searching so results stay contiguous.

**Non-Goals:**
- No changes to `MapManagerViewModel` / `BasemapViewModel` state or download logic.
- No provider-selection UI changes (still fixed "Karry" label).
- No search behavior change beyond hiding the installed section (search still filters available maps only).
- No changes to the map-download infrastructure or native code.

## Decisions

### 1. Direct LazyColumn item reorder (no section abstraction)
Reorder the `item {}` / `items()` blocks in `MapManagerScreen.kt` to the spec order. The screen is the only consumer; a section-ordering abstraction (enum list, ordered map) would add indirection for one screen.
- **Alternative considered**: extract a `MapManagerSection` enum + ordered list rendered by a loop. Rejected: no reuse, harder to read, keys already stable.

### 2. Installed section hidden while searching
Gate the installed section on `searchQuery.isBlank()`. Search stays available-only (spec `map-download-ui` "Search/filter available maps"); hiding installed removes the ambiguity of a search box that filters a list below another list.
- **Alternative considered**: filter installed maps by the query too. Rejected: changes search scope beyond the spec; installed lists are short and users manage them by sight, not search.

### 3. Basemap collapse during download
`BasemapSection`: when `state.isDownloading`, render a single compact status line (e.g. "Downloading world basemap…" + cancel) with no progress bar. `ActiveDownloadsSection` keeps the `basemapDownloading` / `basemapProgress` / `onCancelBasemap` parameters and the `BasemapDownloadRow` composable — the basemap progress row lives in the active-downloads section (spec `map-download-ui` "Basemap download shown with progress"). The dedup is achieved by `BasemapSection` no longer rendering its own progress bar; progress appears exactly once, in the active-downloads section.
- **Alternative considered**: keep progress in both places. Rejected: duplicate progress bars for one download, spec violation.
- **Alternative considered**: remove basemap from active downloads entirely (compact status only, no progress anywhere). Rejected: the spec scenario explicitly requires basemap in the active-downloads section with progress; without it a basemap download would show no percentage at all.

### 4. Loading indicator at top
Move the loading `item {}` to directly below the provider row. When `isLoading && availableEntries.isEmpty() && installedEntries.isEmpty()`, render a centered spinner as the only content (spec "Centered loading on first fetch").
- **Alternative considered**: full-screen `CircularProgressIndicator` overlay. Rejected: hides the provider row and refresh button the user needs to cancel/retry.

### 5. Error banner below search
Move the error `item {}` to directly below the search field, above active downloads. Errors originate from fetch/delete/refresh — top placement makes them visible without scrolling.
- **Alternative considered**: keep error at current position (below search, above downloads — effectively same slot after reorder). The reorder already achieves this; no separate change needed beyond moving the block with the search field.

### 6. Unified section headers
Both "Installed maps" and "Available maps" headers use `titleSmall` + `FontWeight.Bold` + `colorScheme.primary` (installed already does; available switches from `onSurfaceVariant`). Matches the basemap section header weight.
- **Alternative considered**: both `onSurfaceVariant`. Rejected: section headers are primary navigation landmarks; primary color reads as a header, variant reads as muted metadata.

### 7. Test strategy: mocked ViewModels
`MapManagerScreenOrderingTest` mocks `MapManagerViewModel` / `BasemapViewModel` (mockito-core + mockito-kotlin, new test-only dependencies) so the active-downloads / loading / error states can be driven directly. The real download path cannot run under Robolectric: `downloadMap` starts the Hilt `MapDownloadService`, whose component build calls `OSMScoutClientBuilder.build()` — a native call that crashes on the host stub .so. Mocking avoids that entirely; the mocked classes never touch `FakeOSMScoutClient`, so the AGENTS.md classloader rule (default sandbox) is unaffected.
- **Alternative considered**: real VMs with a `FakeMapDownloadManager` subclass (package-private constructor forces the test into `com.framstag.libosmscout.client`). Rejected: still cannot populate `activeDownloads` without triggering the service start in `MapManagerViewModel.downloadMap`.
- **Alternative considered**: no active-downloads coverage in the ordering test. Rejected: the spec scenario "Sections render in fixed order" explicitly includes active downloads.

## Risks / Trade-offs

- [Installed section hidden while searching] → User may momentarily lose sight of installed maps → Search is a deliberate action; clearing the field restores the section (spec scenario "Search cleared restores installed section").
- [Basemap section shows no percentage while downloading] → Progress detail moves to active downloads → Active-downloads section is at the top of the content area, visible without scrolling; compact status line still confirms the download started.
- [LazyColumn reorder breaks item keys] → Keys are per-item strings (`"basemap-section"`, `"installed-header"`, `"avail-${id}"`), not positional → no key changes needed; reorder is safe.
- [Test flakiness with async basemap state] → Existing tests use `waitUntil` with 10s timeout → new tests follow the same pattern.

## Migration Plan

Pure UI reorder — no data migration, no API changes. Rollback: revert the screen changes; specs and behavior return to the previous order. Deploy with the next normal app release.

## Open Questions

None — the three decision points from exploration (search scope, downloads-vs-search order, basemap grouping) are resolved in the proposal's Decisions section and encoded in the specs.
