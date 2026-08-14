## Why

Search panel `ModalBottomSheet` resizes every time search results arrive or change because sheet height matches content height. The `LazyColumn` appearing/disappearing causes visible height jump — annoying and disorienting.

## What Changes

- Add `Modifier.heightIn(min = 280.dp)` to the outer `Column` in `SearchPanel.kt` so the sheet has a stable minimum height regardless of content state (empty, loading, results, no-results)
- Sheet no longer resizes when results pop in — content scrolls within the pre-allocated space

## Capabilities

### New Capabilities
- `stable-search-sheet`: Search bottom sheet maintains stable height during query lifecycle — no resize on result arrival, loading spinner, or empty state

### Modified Capabilities
- `location-search`: Search panel UI behavior changes — sheet height is now stable instead of content-fit

## Impact

- `app/src/main/java/com/naviveylin/ui/map/SearchPanel.kt` — one modifier change on outer `Column`
- No API changes, no dependency changes, no native code changes
