## Context

See proposal.md — Why. Code facts that shape the approach:

- **Convention (established, verified, not changing):** `viewport.angle` θ is radians, 0 = north-up, and in heading-up follow mode `computeMapAngle`/`headingAngleRadians` store `θ = -bearing` so the map renders the driving direction pointing up. Ground direction G renders on screen at `G + θ` (degrees, clockwise-positive from screen-up) — proven by the marker-arrow overlay math (`ProjectionUtils.screenBearing(bearing, angle)`), its follow-mode test (`bearing 123 → screen 0`), and the working on-screen arrow. Therefore **north renders at screen `+θ`**.
- **Bug:** phone `CompassButton` computes needle rotation as `-deg(θ)` and AA `SurfaceIndicators.drawRose` does `canvas.rotate(-deg(θ))`. Both render north at `-θ` — 180° off whenever θ ≢ 0 (mod 180°). Invisible at heading 0°/180° and in north-up, exactly the "some cases" reports while driving east or west.
- `:core` (`com.naviveylin.core.ProjectionUtils`) already holds `screenBearing(bearingDegrees, mapAngleRadians) = bearing + deg(angle) mod 360` and is already consumed by BOTH `:app` and `:auto` — this is where the shared convention belongs.
- UI drawing APIs differ (Compose `DrawScope` vs `android.graphics.Canvas`) — draw code cannot be shared cheaply and need not be.

## Goals / Non-Goals

**Goals:**
- North pointer (phone needle, AA rose) points at the screen position where the map renders north, in one shared place.
- Follow-direction triangle points at the on-screen travel direction.
- The sign convention is structurally impossible to get wrong again (single definition in `:core`, tests pin it).
- Both UIs get the same convention (UI.md parity).

**Non-Goals:**
- No change to the map projection / viewport angle convention (θ = -bearing stays).
- No shared drawing layer across the two canvas APIs.
- Not touching the Android Auto host's own compass (cosmetic overlap accepted in auto-map-layout).
- Not reworking the compass animation/tween behavior.

## Decisions

### D1: Rotation convention lives in `:core` as a function, not inline math
Add `ProjectionUtils.compassRotationDegrees(mapAngleRadians: Double): Double = normalizedDegrees(deg(mapAngleRadians))` — the screen direction of north (CW-positive from up, matching `screenBearing`'s `+deg(angle)` term). Phone needle and AA rose both call it; the local negations are deleted.

- Alternatives: (a) status quo — each UI keeps its own sign math; rejected: it is exactly how the convention drifted apart and produced this bug. (b) a shared "rose renderer" abstraction parameterized over canvas types; rejected: Compose `DrawScope` and `android.graphics.Canvas` have incompatible primitives; 99% of the code is drawing, 1% is math — share the 1%.
- Also optionally extract a tiny pure `RoseGeometry` (center, radius, cardinal tick unit vectors, north-tip unit vector) as plain data in `:core`; both drawers render it. Cheap, plain-JUnit testable. Decide during implementation; the convention function is the required part.

### D2: Follow-direction triangle uses `screenBearing(bearing, mapAngle)`; bearing is passed in
`CompassButton` gains a `bearingDegrees: Double?` parameter. North-up branch targets `compassRotationDegrees(θ)` (geographic north); follow branch targets `screenBearing(bearing, θ)` — 0 (up) in pure heading-up, correct after manual map rotation. `MapCanvasScreen` supplies `state.markerBearing` (already in `MapCanvasUiState`, negative when unavailable; when unavailable the follow branch falls back to pointing at map north, i.e. `compassRotationDegrees(θ)`).

- Alternatives: (a) compute the triangle target in the ViewModel and pass the precomputed screen angle — rejected: pushes presentation math up a layer; `screenBearing` is already the shared primitive. (b) hardcode "up" in follow mode — rejected: wrong after manual rotation (rotation is a supported gesture in follow, `onManualRotationStart`).

### D3: AA rose fixed via the core function, not an inline sign flip
`drawRose` replaces `canvas.rotate(-deg(θ))` with `canvas.rotate(compassRotationDegrees(θ).toFloat())`. `SurfaceIndicators.draw` keeps its `angleRadians` input (screens already feed `headingRadians`/`headingAngle`, both θ = -bearing). No signature change, no call-site churn (`FreeDrivingScreen`/`NavigationScreen` untouched).

- Alternative: flip the inline sign only; rejected: fixes the symptom but leaves two divergent sign definitions in the codebase (the root cause).

### D4: Tests pin the convention where the math is testable
The needle/rose geometry is private draw code behind Compose/Canvas; test the pure surface:
- `ProjectionUtilsTest`: `compassRotationDegrees` cases (θ=0→0, θ=+90→90, westbound follow θ=+90→90/right, −90→270/left), plus `screenBearing(bearing, -bearing) = 0`.
- Extract the follow-branch target as a pure function (e.g. in `CompassButton.kt`, `internal fun compassNeedleTarget(isNorthUp, bearing, mapAngle)` or in core) and test east/west/north-up cases; `CompassButtonComposeTest` keeps click/size tests.
- `SurfaceIndicatorsTest`: rose rotation through the same pure fn (assert `compassRotationDegrees` used; geometry tests unchanged).
- Update the misleading "rotated 30 degrees CCW" comment in `ProjectionUtilsTest` to the actual CW-positive convention.

## Risks / Trade-offs

- [Needle animation wraps 360° on bearing wrap (0↔360)] → Existing 300 ms tween already does this; behavior unchanged by this fix; follow-up polish out of scope (note in TODO if it bothers during testing).
- [Convention could drift again if a future contributor re-adds negation "for testing"] → Single core function + direction-pinning tests + convention comment on the function make the flip fail fast.
- [`compassRotationDegrees` semantic confusion with the native CCW(math) convention] → Document on the function that the native angle is math-CCW but the SCREEN result is +angle clockwise from up (the marker-arrow convention); keep the empirical test as ground truth.
- [Follow branch needs bearing; CompassButton API grows] → One nullable param; call sites already carry the value in state.

## Migration Plan

Pure behavior fix + internal refactor: no data/schema/API-break migration. Deploy with normal app/AAB build. Rollback: revert this change (or the two sign lines) — old behavior returns.

## Open Questions

None blocking. `RoseGeometry` extraction depth left to implementation.
