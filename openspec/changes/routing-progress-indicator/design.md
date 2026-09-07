## Context

See proposal.md — Why. The routing status card (`NavigationStateOverlay`) is
always visible during active navigation and already shows the road name, ETA,
remaining time and remaining distance. Progress data already flows through the
shared `NavigationState` (core module, consumed by phone UI and Android Auto):
`totalDistance` (set at `startNavigation`), `remainingDistance` and
`etaMillis` (from the native `onArrivalEstimate` callback). The overlay is
invoked from `MapCanvasScreen`, which already observes `navState`.

## Goals / Non-Goals

**Goals:**
- Two small progress lines (distance %, time %) in the routing status card,
  always visible during active navigation.
- Small height, no labels, no percent values — a small icon per line for
  differentiation.
- Progress derived entirely from existing navigation state — no native/JNI
  changes, no new callbacks.

**Non-Goals:**
- No progress indicator on the Android Auto surface (car host has its own ETA
  card; this change is phone-only).
- No progress lines in the route summary dialog (they live in the status card).
- No smooth animation of the line — position updates arrive frequently enough
  during navigation that recomposition alone reads as live.

## Decisions

**1. Compute progress in `MapCanvasScreen`, pass values into the overlay.**
`NavigationStateOverlay` stays presentational: new parameters
`distanceProgressPercent: Int?` and `timeProgressPercent: Int?` (null when not
navigating). The screen derives them from `navState`:
- distance % = `(totalDistance - remainingDistance) / totalDistance * 100`
- time % = `(now - navigationStartTimeMillis) / (etaMillis - navigationStartTimeMillis) * 100`
both clamped to 0–100.
Rationale: keeps the overlay unit-testable without a ViewModel, and keeps the
derivation next to the state it reads. Alternative (compute inside the overlay)
rejected — the overlay would need `NavigationState` and a clock, coupling UI to
state plumbing.

**2. Add `navigationStartTimeMillis: Long` to `NavigationState` (core).**
Set in `NavigationViewModel.startNavigation()` to
`System.currentTimeMillis()`, reset to 0 in `stopNavigation()`. The time
percentage needs a start reference; `etaMillis` alone (arrival epoch) is not
enough. Rationale: `NavigationState` is the shared source of truth for both
phone and car; the field is additive and harmless to existing consumers.
Alternative (track start time in `RoutePanelViewModel`) rejected — that VM is
not the navigation owner and is not wired in the car-only path.

**3. Render each line as a thin track Box with a filled portion.**
A thin rounded track (2–3 dp high, full width) with a filled portion up to the
current percent (`fillMaxWidth(percent / 100f)`), preceded by a small icon
(16 dp: `Place` for distance, `Schedule` for time — both already used by the
stats row). No text, no dot — the filled portion itself marks the position.
Rationale: plain Compose primitives, minimal height, matches the card's
compact layout.

**4. No dedicated ticker for the time line.**
The time percent updates on each `navState` emission. During navigation the
native controller emits position estimates continuously (the same stream that
drives map centering), so the value refreshes without a separate
`LaunchedEffect` loop. Rationale: avoids a battery-draining timer; the card is
a glanceable overlay, not a live clock.

## Risks / Trade-offs

- **Time % stalls when position estimates pause** (e.g. long tunnel, no GPS)
  → Acceptable: the card is glanceable and the distance line still updates
  from `onArrivalEstimate`; a ticker would add battery cost for marginal gain.
- **`etaMillis`/`remainingDistance` may be 0 briefly at start** → Guard:
  treat `totalDistance <= 0` or `etaMillis <= navigationStartTimeMillis` as
  "no progress yet" (0 %), never divide by zero. Additionally,
  `startNavigation` initializes `remainingDistance = totalDistance` so the
  distance line starts at 0 % instead of 100 % (the stale 0 default would
  otherwise read as "arrived" until the first arrival estimate).
- **Reroute changes the route** → `startNavigation` is called again on
  confirmed reroute, resetting `navigationStartTimeMillis` and
  `totalDistance`; progress restarts cleanly. No extra handling needed.
- **`NavigationState` is shared with Android Auto** → new field is additive
  with a default; car consumers ignore it. No parity impact.

## Migration Plan

Single-commit change, no data migration. Rollback: revert the overlay
parameters and the `navigationStartTimeMillis` field — the field default (0)
keeps existing consumers compiling and behaving unchanged.

## Open Questions

None.
