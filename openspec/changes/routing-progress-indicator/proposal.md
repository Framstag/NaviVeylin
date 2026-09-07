## Why

During active navigation the route summary dialog shows the step list with the
current step highlighted, but gives no sense of how far along the route the
driver is. The driver must infer progress from the remaining-distance/ETA stats
in the routing status card. A compact, glanceable progress visualization in the
dialog fixes this: two small lines showing percent distance and percent time
traveled, with a small dot marking the current position on each line.

## What Changes

- The routing status card (`NavigationStateOverlay`, always visible during
  active navigation) gains two small progress lines between the road name and
  the stats row:
  - **Distance line** — percent of the route distance traveled, computed from
    `totalDistance` and `remainingDistance`:
    `(totalDistance - remainingDistance) / totalDistance * 100`, clamped to
    0–100.
  - **Time line** — percent of the estimated travel time elapsed, computed from
    the navigation start time and the arrival estimate:
    `(now - navigationStartTimeMillis) / (etaMillis - navigationStartTimeMillis) * 100`,
    clamped to 0–100.
- The lines are small in height, show no labels and no percent values — just
  the line with a small icon at the start for differentiation (distance icon,
  time icon).
- `NavigationState` (core module) gains `navigationStartTimeMillis: Long`,
  set in `NavigationViewModel.startNavigation()` when navigation begins. This
  is the reference point for the time percentage; without it the elapsed time
  cannot be computed.
- `MapCanvasScreen` passes the progress values (distance percent, time percent)
  into `NavigationStateOverlay` from `navState`.
- No native/JNI changes — all progress data derives from state the navigation
  controller already reports (`onArrivalEstimate` → `remainingDistance` +
  `etaMillis`, `totalDistance` computed at start).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `navigation-status-details` — new requirement: the routing status card shows
  route progress as two small lines (percent distance, percent time) with a
  small icon for differentiation, always visible during active navigation.

## Impact

- `core/src/main/java/com/naviveylin/core/NavigationState.kt` — add
  `navigationStartTimeMillis: Long = 0L`.
- `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt` — set
  `navigationStartTimeMillis = System.currentTimeMillis()` and
  `remainingDistance = totalDistance` in `startNavigation()`; reset in
  `stopNavigation()`.
- `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt` —
  two small progress lines (distance, time) with differentiation icons,
  rendered between the road name and the stats row; new parameters for the
  progress values.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — compute and
  pass distance/time percent into `NavigationStateOverlay`.
- Tests: new Compose test for `NavigationStateOverlay` progress lines;
  `RouteProgressTest` covers the percent math.
- Spec: `openspec/specs/navigation-status-details/spec.md` — new requirement
  with scenarios.
