## Context

See `proposal.md` — Why for motivation. See `specs/long-press-details/spec.md` and `specs/enhanced-details-sheet/spec.md` for requirements.

Current state: `MapCanvasScreen.kt` uses `detectTransformGestures` for pan/zoom and a separate `awaitPointerEventScope` for release detection. `LocationDetailsSheet.kt` is a `ModalBottomSheet` with `skipPartiallyExpanded=true` (not draggable), showing only label, region, coords, and fav controls. `OSMScoutClient.java` declares `native ObjectDescription getDescription(double, double)` but the JNI implementation in `OSMScoutClient.cpp` is missing.

## Goals / Non-Goals

**Goals:**
- Long-press on map → resolve closest OSM object → show structured description in draggable sheet
- Reuse `LocationDetailsSheet` for both search results and long-press
- Implement JNI `getDescription()` with candidate ranking algorithm matching JavaScout's design
- Sheet is draggable (no `skipPartiallyExpanded`) with visible drag handle

**Non-Goals:**
- Not changing the search panel's behavior or appearance
- Not adding Android Auto support
- Not modifying the existing pan/zoom gesture handling
- Not adding OpenGL rendering changes

## Decisions

### Decision 1: Long-press detection via `awaitEachGesture` with timeout
**Choice:** Use Compose's `awaitEachGesture` + `awaitFirstDown` + `withTimeoutOrNull(500L)` + `waitForUpOrCancellation()` in a dedicated `pointerInput` modifier. On down, wait 500ms for up/cancel. If timeout fires, convert screen coords to geo and fire callback.

**Rationale:** Compose's `detectTapGestures(onLongPress = ...)` conflicts with `detectTransformGestures` because both consume pointer events in the same gesture detector pipeline. A separate `pointerInput` block with `awaitEachGesture` avoids this conflict and mirrors the proven JavaScout approach using Compose-native APIs instead of a manual timer.

**Alternatives considered:**
- `detectTapGestures(onLongPress = ...)` — conflicts with existing `detectTransformGestures`
- Manual timer on `PointerEventType.Press` — equivalent but more code; `awaitEachGesture` is cleaner
- `combinedClickable(onLongClick = ...)` modifier — requires clickable, not suitable for canvas
- Third-party gesture library — unnecessary complexity

### Decision 2: JNI `getDescription()` — candidate ranking algorithm
**Choice:** Reuse the algorithm from JavaScout's proposal (see `openspec/changes/archive/2026-07-12-show-description-on-long-press/proposal.md` in libosmscout submodule):

1. Get DBThread database instances via `RunSynchronousJob`
2. For each database, query objects in a ~50m bounding box around (lat, lon)
3. Collect all candidates (nodes, ways, areas) with their types
4. Rank by composite score:
   - Has DescriptionService data (weight: high) — skip objects with no description
   - Visible at current zoom level (weight: medium) — respect type's min/max zoom
   - Proximity to press point (weight: medium) — prefer closer objects
   - Type priority tiebreaker: nodes > ways > areas
5. Call `DescriptionService::GetDescription()` on best candidate
6. Marshal `DescriptionEntry` list to Java `ArrayList<DescriptionEntry>`

**Rationale:** Matches JavaScout behavior exactly. The algorithm is already tested (see `DescriptionServiceTest.cpp` in the libosmscout submodule with 17 assertions on candidate ranking).

**Alternatives considered:**
- Simple closest-by-distance — would pick irrelevant objects (e.g., a road segment instead of the restaurant next to it)
- Always prefer nodes — misses large areas like parks or buildings

### Decision 3: Enhanced `LocationDetailsSheet` — accept `ObjectDescription?`
**Choice:** Add an optional `ObjectDescription?` parameter to `LocationDetailsSheet`. When non-null and non-empty, render structured sections above the existing basic info. When null or empty, fall back to current behavior (label, region, coords, favs).

**Rationale:** Single composable reused for both search results (no description data yet) and long-press (rich description). No breaking change to existing callers.

**Alternatives considered:**
- Separate composable for long-press details — code duplication
- Always require ObjectDescription — breaks search result flow

### Decision 4: Draggable sheet — remove `skipPartiallyExpanded`
**Choice:** Change `rememberModalBottomSheetState(skipPartiallyExpanded = true)` to `rememberModalBottomSheetState(skipPartiallyExpanded = false)`. Material 3's `ModalBottomSheet` renders a built-in drag handle when `skipPartiallyExpanded` is false, so no custom drag handle composable is needed.

**Rationale:** `skipPartiallyExpanded = true` disables the drag-to-dismiss gesture. Removing it restores standard `ModalBottomSheet` drag behavior with the built-in drag handle providing visual affordance.

### Decision 5: Description section rendering — grouped by `sectionKey`
**Choice:** Group `DescriptionEntry` list by `sectionKey` in the composable. Render each group as: section header (bold, `titleSmall`), then for each entry: if `subsectionKey` is non-empty, render as indented sub-header, then label/value row.

**Rationale:** Mirrors JavaScout's `DescriptionOverlay` layout. Section keys like "General", "Location", "Contact" are human-readable English strings from `DescriptionService` and can be used directly as headers.

## Risks / Trade-offs

- **[Risk] Long-press conflicts with pan gesture** → Mitigation: 3px drag threshold cancels long-press. Same approach as JavaScout, proven in production.
- **[Risk] JNI `getDescription()` blocks render thread** → Mitigation: Called from `Dispatchers.Default` coroutine in ViewModel, never from main thread.
- **[Risk] `DescriptionService` not linked in CMake** → Mitigation: `osmscout` library target already includes `DescriptionService`. Verify in `CMakeLists.txt` that `osmscout` is linked to `osmscout_client_java`.
- **[Risk] Empty description for many objects** → Mitigation: Sheet handles null/empty gracefully, falls back to basic info. User still sees coords and can add favorite.
- **[Trade-off] Manual timer vs Compose gesture API** — Manual timer is more code but avoids gesture conflicts. Acceptable for a single-screen feature.

## Open Questions

- Should the long-press timeout be configurable? JavaScout uses `Config.longPressTimeoutMs` (default 500ms). For now, hardcode 500ms in the `pointerInput` block. Can be extracted to a constant or setting later.
- Should the bounding box size for object lookup be configurable? JavaScout uses a small fixed bbox (~50m). Hardcode for now.
