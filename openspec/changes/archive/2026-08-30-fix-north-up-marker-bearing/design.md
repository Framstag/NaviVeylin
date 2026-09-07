## Context

See proposal.md — Why. Current state: `MapCanvasViewModel` (follow-mode GPS collect block, ~line 690) computes the marker bearing as `if (!isNorthUp && !markerBearingRaw.isNaN()) markerBearingRaw else -1.0`. The `!isNorthUp` clause is the bug: north-up mode is a map-rotation choice, not a bearing-availability state. Both consumers (`LocationMarkerOverlay` Compose overlay and the native renderer) treat `bearing < 0` as "unavailable → point north", so in north-up mode the arrow always points north regardless of travel direction.

The rendering side is already correct: `ProjectionUtils.screenBearing(bearing, angle) = bearing + angle`, and follow-direction mode sets `angle = -bearing`, so passing the real bearing yields the right arrow in both modes. Only the ViewModel's sentinel injection is wrong.

## Goals / Non-Goals

**Goals:**
- Marker arrow points in travel direction in both orientation modes when bearing is available
- Preserve the arrow-north fallback when bearing is genuinely unavailable
- Make the decision unit-testable (current tests are tautological)
- Lock the behavior in the spec so it cannot regress

**Non-Goals:**
- No changes to map-rotation logic (`compass-settings` behavior is correct)
- No native/JNI changes (native marker path is dead code for NaviVeylin — see proposal Impact)
- No Auto module changes (AutoMapRenderer already feeds raw bearing)

## Decisions

### D1: Pass real bearing in north-up mode (one-line fix)

Change line ~690 to `val markerBearing = if (!markerBearingRaw.isNaN()) markerBearingRaw else -1.0` — drop the `!isNorthUp &&` clause.

- **Alternative A (chosen)**: always pass available bearing; keep `-1.0` sentinel only for unavailable. Minimal diff, both consumers already handle `bearing >= 0` correctly, native and Compose stay consistent.
- **Alternative B**: change the sentinel to `Double.NaN` and update both consumers. More churn, no behavioral gain — `-1.0` already means "unavailable" everywhere.
- **Alternative C**: keep forcing `-1.0` in north-up and instead make consumers draw the arrow at `bearing` when north-up. Inverts the fix into the wrong layer; the ViewModel is the only place that knows orientation mode, and the consumers' `bearing < 0 → north` contract is spec-correct.

### D2: Extract pure function for testability

Extract the decision into a pure function so the tautological `OrientationLogicTest` can be replaced with real tests:

```kotlin
// MapCanvasViewModel.kt (companion or top-level in same file)
internal fun computeMarkerBearing(isNorthUp: Boolean, rawBearing: Double): Double =
    if (!rawBearing.isNaN()) rawBearing else -1.0
```

- **Alternative A (chosen)**: top-level `internal` function in `MapCanvasViewModel.kt`, unit-tested directly. The `isNorthUp` parameter is kept in the signature so the test documents that orientation mode must NOT influence the result — the parameter exists to be ignored, which is the regression guard.
- **Alternative B**: no extraction, test via ViewModel with fake location provider. Heavier harness (Robolectric, coroutine dispatchers) for a one-line decision; the pure function is the cheaper, more direct guard.
- **Alternative C**: put the function in `ProjectionUtils` (core module). It is not a projection concern; it belongs with the marker-state logic in the ViewModel file.

### D3: Replace tautological tests, keep map-angle tests

`OrientationLogicTest` currently tests inline `if (true) 0.0 else ...` expressions — it verifies nothing. Rewrite it to test the real production logic:
- `computeMarkerBearing` cases: north-up + bearing → bearing; follow-direction + bearing → bearing; NaN bearing → `-1.0`
- Map-angle cases (north-up → 0.0, follow-direction → `-Math.toRadians(bearing)`) — these are the actual formulas from the collect block, extracted as pure functions too (`computeMapAngle(isNorthUp, bearing)`), so the existing test intent is preserved but now tests real code

- **Alternative A (chosen)**: extract both `computeMarkerBearing` and `computeMapAngle` as pure functions; rewrite `OrientationLogicTest` against them. Preserves the original test file's intent (orientation logic) while making it meaningful.
- **Alternative B**: only extract `computeMarkerBearing`, leave map-angle tests tautological. Leaves half the file fake.
- **Alternative C**: delete `OrientationLogicTest` entirely. Loses the map-angle coverage intent; the formulas deserve real tests.

## Risks / Trade-offs

- [Regression: arrow-north fallback broken] → `computeMarkerBearing` returns `-1.0` only for NaN input; unit test covers the NaN case explicitly.
- [Native/Compose arrow disagreement after fix] → both consumers use the same `bearing >= 0` contract and the same `screenBearing` formula; the fix feeds both the same value. No divergence path.
- [Stale bearing shown after GPS loss] → unchanged behavior: `markerBearingRaw` falls back to `lastUsedBearing` before the NaN check, so the arrow keeps the last known direction until a fresh fix — same as follow-direction mode today.
- [Tautological tests mask future regressions] → replaced with real assertions on extracted pure functions; spec delta adds the north-up scenario as an additional guard.

## Migration Plan

Single commit, additive, no data migration. Rollback = revert the one-line fix (and the test/spec additions if desired). No feature flags needed.

## Open Questions

None — the behavior contract is unambiguous (spec: gps-location-marker, direction indicator), and the fix is a one-line change with test coverage.
