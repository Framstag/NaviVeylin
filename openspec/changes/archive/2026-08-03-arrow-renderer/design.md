## Context

See `proposal.md` for motivation. Current state: `turnTypeToIcon()` and `laneTurnToArrow()` are private functions duplicated in `NextTurnOverlay.kt` and `RouteSummaryDialog.kt`. All symbols are Unicode text — no Canvas drawing. Roundabout shows generic ↳ regardless of exit count/geometry. `formatDistance()` duplicated across 3 files.

## Goals / Non-Goals

**Goals:**
- Single `NavigationArrowRenderer.kt` composable that draws all turn/lane arrows as Canvas paths
- Roundabout renderer that draws circle + exit markers at correct angles
- All existing call sites updated to use central renderer
- `formatDistance()` consolidated into shared utility

**Non-Goals:**
- No changes to JNI/native layer — rendering is pure Compose
- No animation of arrows (static render only)
- No OpenGL/Cairo rendering — Compose Canvas only
- No changes to data flow or `NavigationState` model

## Decisions

### Decision: Compose Canvas over ImageVector/Resource drawable

**Choice**: Draw arrows programmatically with `Canvas` and `Path` APIs.

**Rationale**: Arrows have simple geometry (lines, triangles, arcs). Canvas drawing avoids asset PNGs for every variant (18 LaneTurn + 12 TurnType = 30+ assets). Programmatic paths scale cleanly to any size. Roundabout circle + exit markers are trivial with Canvas.

**Alternatives considered**:
- Material `Icon` with custom `ImageVector` — same complexity, less flexible for roundabout
- Pre-rendered PNG/SVG assets — 30+ assets, no roundabout parameterization
- Unicode (current) — poor roundabout visualization, no color control

### Decision: Single composable with sealed class for input

**Choice**: Accept a sealed class/interface that wraps either `TurnType` or `LaneTurn` or `RoundaboutData`.

```kotlin
sealed interface NavSymbol {
  data class TurnArrow(val type: TurnType) : NavSymbol
  data class LaneArrow(val turn: LaneTurn) : NavSymbol
  data class Roundabout(val exitCount: Int, val exitAngles: List<Float>?, val selectedExit: Int, val entryAngle: Float?) : NavSymbol
}
```

**Rationale**: Single composable `NavigationArrow(symbol: NavSymbol, ...)` is discoverable, type-safe, and extensible. Callers pass the variant they need.

**Alternatives considered**:
- Separate composables per type — more imports, less discoverable
- Overloaded functions — Kotlin doesn't support overloading with same param count well

### Decision: Roundabout geometry from RouteInstruction

**Choice**: Derive exit count and angles from `RouteInstruction` fields. When `RouteInstruction` provides roundabout exit data (exit count, bearing to each exit), use it. Fall back to evenly-spaced exits when data is absent.

**Rationale**: `RouteInstruction` from libosmscout already carries roundabout metadata (see `RoundaboutEnterDescription` in native code). The JNI bridge exposes this. Evenly-spaced fallback is still better than current Unicode ↳.

### Decision: Shared formatDistance in companion/utility file

**Choice**: Extract `formatDistance()` into a top-level function in a shared utility file (e.g., `FormatUtils.kt` or inside `NavigationArrowRenderer.kt`).

**Rationale**: Small function, no state. Top-level function is simplest. Placing it in the renderer file keeps navigation-related utilities together.

## Risks / Trade-offs

- **Canvas path complexity** → Compound lane arrows (e.g., LEFT_AND_STRAIGHT) need two arrows side-by-side. Mitigation: draw each direction as separate path, compose with offset.
- **Roundabout angle data may be absent** → Fallback to evenly-spaced exits. Acceptable — still better than Unicode.
- **Performance** → Canvas redraws on every recomposition. Mitigation: arrows are small (40-60dp), path drawing is cheap. No measurable impact.
- **Testability** → Canvas drawing is hard to unit test. Mitigation: extract angle/position math into pure functions, test those separately.
