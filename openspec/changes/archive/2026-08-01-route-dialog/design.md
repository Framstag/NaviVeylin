## Context

Route calculation currently lives in `RoutePanelViewModel` which manages start/dest locations, vehicle selection, and route state (`Idle` → `Calculating` → `Done`/`Error`). On success, turn-by-turn instructions render inline inside the route panel (`RoutePanel.kt`). The route panel is a `ModalBottomSheet` composed in `MapCanvasScreen.kt`.

There is no navigation state — the app has no concept of "active navigation." The route summary dialog is the bridge between route planning and future active navigation.

## Goals / Non-Goals

**Goals:**
- Show route summary dialog on top of route panel after successful calculation
- Display route stats (distance, time) and scrollable step list
- Provide Start Navigation button (Material 3) — no-op for now
- Dismiss dialog → return to route panel with route intact
- Same composable reusable for active navigation with step highlighting

**Non-Goals:**
- Actual navigation logic (guidance, re-routing, arrival detection)
- Voice guidance or audio
- Android Auto integration
- Route editing from the summary dialog

## Decisions

### 1. Full-screen overlay with slide-from-bottom, not `AlertDialog`
**Decision:** Use a full-screen `Box` overlay with `AnimatedVisibility` (slide-in from bottom), composed at the `MapCanvasScreen` level — not `AlertDialog` inside `RoutePanel`.

**Rationale:** The route panel (`ModalBottomSheet`) renders in a separate Android window, always on top of the main composable tree. An `AlertDialog` inside the route panel would still be behind the bottom sheet window. The overlay must live at the top-level screen (`MapCanvasScreen`) to cover the full display. `AnimatedVisibility` with `slideInVertically` gives the desired bottom-slide animation that `AlertDialog` cannot provide.

**Flow:**
1. Route calculated → `showSummaryDialog = true`
2. `LaunchedEffect` fires → route panel dismissed (`dismissRoutePanel()`)
3. Summary overlay slides up from bottom, full width
4. User dismisses → summary hidden, route panel re-opened with full state

**Alternatives considered:**
- `AlertDialog` inside `RoutePanel` — appears behind `ModalBottomSheet` window, not full-width, no slide animation
- `ModalBottomSheet` on top of `ModalBottomSheet` — nested sheets are fragile and non-standard on Android
- New nav route — overkill for a transient overlay; dismiss/back behavior harder to wire

### 2. State in `RoutePanelViewModel`
**Decision:** Add `showSummaryDialog: Boolean` and `activeStepIndex: Int?` to `RoutePanelUiState`. Dialog lifecycle methods live in `RoutePanelViewModel`.

**Rationale:** The route panel VM already owns route state (`routeEntry`, `routeInstructions`, `routeState`). Adding dialog visibility and active step index keeps state co-located with the data it displays. No need for a separate ViewModel.

**Alternatives considered:**
- Separate `RouteSummaryViewModel` — unnecessary indirection; the dialog is purely presentational over existing route data
- State in `MapCanvasViewModel` — would couple map concerns with route dialog; route panel VM is the natural owner

### 3. Dialog composable: `RouteSummaryDialog`
**Decision:** New file `RouteSummaryDialog.kt` in `ui/route/` package. Single composable accepting route data and callbacks.

**Signature:**
```kotlin
@Composable
fun RouteSummaryDialog(
    routeEntry: RouteEntry,
    steps: List<RouteStepDisplay>,  // parsed from native descriptions
    activeStepIndex: Int? = null,    // null = summary mode, non-null = active nav
    onStartNavigation: () -> Unit,
    onDismiss: () -> Unit
)
```

**`RouteStepDisplay` data class:**
```kotlin
data class RouteStepDisplay(
    val instruction: String,   // clean description, no brackets
    val distanceText: String,  // e.g. "1.2 km"
    val timeText: String,      // e.g. "5 min" or ""
    val turnType: TurnType
)
```

**Rationale:** Native `route.descriptions` strings embed distance/time in brackets (`"Turn left into Main Street  [1.2 km, 5 min]"`). Parsing into structured `RouteStepDisplay` enables columnar display with distance and time in separate columns. Pure composable, no ViewModel dependency. Callbacks keep it testable and reusable. `activeStepIndex = null` means summary mode (no highlighting); non-null means active navigation mode with that step highlighted.

### 4. Step highlighting via background color
**Decision:** Highlight the current step with `MaterialTheme.colorScheme.primaryContainer` background + bold text.

**Rationale:** Simple, accessible, follows Material 3 container theming. No custom drawing needed. The `LazyColumn` auto-scrolls to the active index via `LazyListState.animateScrollToItem()`.

### 5. Start Navigation button — no-op placeholder
**Decision:** `onStartNavigation` callback is wired to a no-op lambda initially. The button uses `FilledButton` (Material 3).

**Rationale:** The spec explicitly says "no action" for now. The callback signature is forward-compatible — future navigation logic plugs in without changing the dialog composable.

## Risks / Trade-offs

- **[Risk] Dialog on top of bottom sheet** → `AlertDialog` may appear above the sheet but the sheet peek height remains visible. Mitigation: test on small screens; if the sheet obscures the dialog, consider dimming the sheet or adjusting sheet state to `Hidden` while dialog is shown.
- **[Risk] Active navigation mode not testable yet** → No navigation engine exists to drive `activeStepIndex` updates. Mitigation: the parameter is optional and defaults to `null`; active mode is designed but untested until navigation is implemented.
- **[Trade-off] No swipe-to-dismiss** → `AlertDialog` doesn't support drag-to-dismiss like `ModalBottomSheet`. The close button and system back button are the dismiss mechanisms. Acceptable for a focused summary view.
