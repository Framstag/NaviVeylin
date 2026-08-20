# Design: UI Improvements

## Context

See proposal.md for motivation. Current state:

- `NextTurnOverlay.kt` renders next-turn description with `bodyLarge` and next-next with `bodySmall`, both `maxLines=2` + `TextOverflow.Ellipsis`. Native descriptions are built as `"<generic> into <street>"` (e.g. "Turn left into Hauptstrasse"); the generic part is also exposed as `shortDescription` and the destination as `streetName` (see `JavaRouteInstructionBuilder` in `OSMScoutClient.cpp`).
- `NavigationStateOverlay.kt` renders the current road name with `bodyMedium`, left-aligned, above the ETA/time/distance/speed stats row.
- `RouteSummaryDialog.kt` is the existing route details view: step list with turn icon, distance, instruction, and `activeStepIndex` highlight.
- During navigation, `NavigationState` already exposes `instructions: List<RouteInstruction>` and `currentStepIndex` (updated in `onNextRouteInstruction` / `onRouteInstructions` in `NavigationViewModel.kt`). No native changes needed.

## Goals / Non-Goals

**Goals**
- Larger, more readable turn instruction typography with a deterministic wrap point between generic instruction and destination name.
- Emphasized, centered current road name in the status card.
- Tap-to-expand full-screen route description during navigation, current step highlighted at top.

**Non-Goals**
- No changes to native description generation (C++/JNI) — the existing `shortDescription` / `streetName` fields are sufficient.
- No changes to `RouteSummaryDialog` behavior for the pre-navigation flow.
- No Android Auto changes.

## Decisions

### D1: Render generic instruction and destination name as separate text lines

Split the instruction into two `Text` composables inside the existing `Column`:

- Line 1: `instruction.shortDescription` (fallback: full `description` when blank).
- Line 2: `instruction.streetName` (only when non-empty), styled bold and slightly larger.

Rationale: guarantees the break point between generic and destination (spec scenario "Break between generic instruction and destination name") without string parsing. Each line wraps independently with `maxLines=2` + ellipsis, satisfying the "destination name wraps independently" scenario. When `streetName` is empty (e.g. "Enter roundabout"), only line 1 renders — matching the "No destination name" scenario.

Alternatives considered:
- Single `Text` with a forced `\n` between generic and destination — simpler but cannot style the destination differently (bold emphasis) and mixes two semantic texts in one composable.
- Parsing `description` with regex to strip " into "/" onto " — fragile across the many native description forms (roundabout exits, motorway entries, start/target nodes).

### D2: Typography scale

- Next-turn description: `bodyLarge` → `titleMedium` (bold destination line).
- Next-next description: `bodySmall` → `bodyMedium`.
- Current road name: `bodyMedium` → `titleMedium`, `textAlign = TextAlign.Center`, `fillMaxWidth`.

Rationale: preserves the existing hierarchy (next-next < next-turn; road name matches the stats row's `titleMedium` per the "same font size and styling as the route status labels" scenario in `current-road-info`). `titleMedium` is the same size as the stats values, so the road name reads as part of the status card.

### D3: New full-screen composable `NavigationDetailsOverlay`

New file `app/src/main/java/com/naviveylin/ui/navigation/NavigationDetailsOverlay.kt`:

- Full-screen `Box` with scrim + `Surface` (mirrors `RouteSummaryDialog` structure but fills the screen).
- Header: current road name + stats row (reuse the same layout as `NavigationStateOverlay`'s card content).
- Body: `LazyColumn` of `navState.instructions`, each item styled like `RouteSummaryDialog` steps (turn icon via `NavigationArrow`/`NavSymbol`, `formatDistance(distanceTo)`, description). Current step (`currentStepIndex`) gets `primaryContainer` background + bold text.
- `LazyListState(initialFirstVisibleItemIndex = currentStepIndex)` puts the current step at the top on open; a `LaunchedEffect(currentStepIndex)` re-scrolls as the user progresses.
- Close button + `BackHandler` for dismissal.

Rationale: `navState.instructions` is the live navigation instruction list and `currentStepIndex` indexes into it (already matched by description in `NavigationViewModel.onNextRouteInstruction`), so no new state plumbing is needed. A separate composable keeps the pre-navigation `RouteSummaryDialog` untouched.

Alternatives considered:
- Reusing `RouteSummaryDialog` with `heightIn(max = 400.dp)` — not full screen, and its stats come from `routeEntry` which may be stale after reroutes.
- Reusing `RouteSummaryDialog` full-screen — couples the pre-navigation flow to navigation state; the expanded view needs live `navState` data, not `routeEntry`.

### D4: Click wiring in `MapCanvasScreen`

- Add `onClick: () -> Unit = {}` parameter to `NavigationStateOverlay`; wrap the card in `Modifier.clickable` (with `indication = null` to avoid ripple over the whole card, or accept ripple — decision: keep default ripple for affordance).
- In `MapCanvasScreen`, add `var showNavDetails by remember { mutableStateOf(false) }`; pass `onClick = { showNavDetails = true }` to `NavigationStateOverlay` and compose `NavigationDetailsOverlay` when `showNavDetails && navState.isNavigating`.
- The stop button inside the card keeps its own `IconButton` (clickable area wins over the card's clickable — Compose handles nested clickables correctly).

Rationale: minimal state, no ViewModel changes. The overlay is composed only during navigation, so `navState.instructions` is non-empty.

## Risks / Trade-offs

- [Two-line instruction layout increases overlay height] → Mitigation: destination line only renders when `streetName` is non-empty; `maxLines=2` + ellipsis caps growth.
- [`currentStepIndex` may not match `instructions` after reroute] → Mitigation: `onRouteInstructions` resets `currentStepIndex = 0` and replaces the list atomically; the overlay re-scrolls via `LaunchedEffect`.
- [Card-wide clickable may swallow drag gestures on the map] → Mitigation: clickable only consumes taps; map gestures are handled by the underlying `Canvas` pointer input, and the card is a small bottom strip.
- [`initialFirstVisibleItemIndex` only applies at first composition] → Mitigation: overlay is composed fresh on each open; `LaunchedEffect(currentStepIndex)` handles in-view progress.

## Migration Plan

- Pure UI change, no data migration. Rollback: revert the three composable files + `MapCanvasScreen` wiring.

## Open Questions

None.
