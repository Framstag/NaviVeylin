# Design: move-routing-summary

## Context

Current flow: after route calculation, `RoutePanelViewModel.onSuccess` sets
`showSummaryDialog = !isNavigating`, which triggers a `LaunchedEffect` in
`MapCanvasScreen` that dismisses the route panel and shows the full-screen
`RouteSummaryDialog` overlay. The user loses the routing dialog context.

`RouteSummaryDialog` (full-screen overlay) and `NavigationDetailsOverlay`
(full-screen step list during nav) both render a step list, but from different
data: `RouteSummaryDialog` uses the static `RouteEntry` + parsed
`routeSteps` (with per-step distance AND time), `NavigationDetailsOverlay` uses
live `RouteInstruction`s (distance only — no time field exists).

## Goals / Non-Goals

**Goals:**
- Reusable `RouteSummary` component (distance, duration, step list) extracted
  from `RouteSummaryDialog`.
- Route panel shows the summary inline below the calculate button after
  calculation; panel stays open.
- Start Navigation button between calculate and summary when route calculated.
- Per-step time in `NavigationDetailsOverlay`, matching the route summary.
- Routing status view press behavior unchanged (`NavigationDetailsOverlay`).

**Non-Goals:**
- No changes to the routing status view press target.
- No changes to route calculation, navigation engine, or map rendering.
- No changes to the Android Auto module.

## Decisions

### Decision 1: Extract `RouteSummary` composable, dialog reuses it

Extract the stats block (distance + duration) and the step list from
`RouteSummaryDialog` into a public `RouteSummary` composable in
`ui/route/RouteSummary.kt`. `RouteSummaryDialog` keeps its scrim/surface/header
and embeds `RouteSummary`. `RoutePanel` embeds `RouteSummary` directly.

Signature:

```kotlin
@Composable
fun RouteSummary(
    routeEntry: RouteEntry,
    steps: List<RouteStepDisplay>,
    activeStepIndex: Int? = null
)
```

**Rationale:** single source of truth for summary rendering; both the panel and
the dialog render identical content. Alternative (duplicate layout in panel)
rejected — drift risk.

### Decision 2: Route panel Done state layout

Done state becomes:

1. Calculate button (recalculate — stays visible)
2. Start Navigation / Stop Navigation button
3. `RouteSummary` component (inline)
4. Show Route button (opens full-screen dialog)
5. Clear Route button

**Rationale:** matches the user's requested order (calculate → start nav →
summary). Calculate stays enabled so the user can recalculate after changing
vehicle/fields. Show Route kept per spec (full-screen dialog still available).

### Decision 3: No auto-close, no auto-dialog

- `RoutePanelViewModel.onSuccess`: stop setting `showSummaryDialog` (leave it
  false). The panel stays open with the inline summary.
- `MapCanvasScreen`: remove the `LaunchedEffect(routeState.showSummaryDialog)`
  that dismisses the route panel.
- `showSummaryDialog()` / `dismissSummaryDialog()` stay for the Show Route
  action.

**Rationale:** the panel no longer needs to be dismissed — the summary is
inline. The dialog is now opt-in via Show Route.

### Decision 4: Per-step time via native `RouteInstruction.timeTo`

`RouteInstruction` has no time data. The native `JavaRouteInstructionBuilder`
walks `RouteDescription` nodes which carry `GetTime()` (time from route start).
Add a `timeTo` field (per-step seconds) end to end:

- C++ `JavaRouteInstruction` struct: `double timeTo{0.0}`.
- `CollectCallback`: track `prevTime`/`time` in `BeforeNode`; set
  `instr.timeTo = duration_cast<seconds>(time - prevTime).count()` in each
  On* handler (same segment semantics as the `[1.2 km, 5 min]` description
  suffix).
- `GenerateNextRouteInstruction`: the collected "next" instruction already
  carries its per-step `timeTo` — no extra computation needed.
- `CreateJavaRouteInstruction` + ctor signature: pass `timeTo` (double).
- `RouteInstruction.java`: add `public final double timeTo` (seconds); 5-arg
  constructor defaults it to `0.0` (keeps existing call sites compiling).

**Rationale:** correct data source (native route description nodes), identical
semantics to the summary's per-step time. Alternative (matching live
instructions to parsed route steps in Kotlin) rejected — fragile index/string
matching.

### Decision 5: Display per-step time in NavigationDetailsOverlay

In the step row, under the distance text, render the time formatted like the
summary: `"Xh Ymin"` / `"Y min"` (same formatting as `RouteSummaryDialog`'s
duration text). Hidden when `timeTo <= 0`.

**Rationale:** visual parity with the route summary step list.

## Risks / Trade-offs

- **Native change in submodule**: `libosmscout-client-java` lives inside the
  `libosmscout` submodule (local branch `naviveylin-local`). The JNI bridge
  change is app-owned (not the frozen libosmscout core), consistent with
  existing uncommitted native work. Requires native rebuild for verification.
- **RouteInstruction ctor change**: adding a parameter to the full ctor is
  source-compatible for the 5-arg overload (defaults `timeTo = 0.0`); the JNI
  bridge must pass the new arg or `timeTo` stays 0.
- **Live "next" instruction time**: the live next instruction's `timeTo` is the
  full segment time, not the remaining time to the manoeuvre — acceptable,
  matches the full-list per-step semantics.
- **Panel height**: inline summary + buttons make the panel taller; the
  `ModalBottomSheet` scrolls, and the summary step list is height-capped.
