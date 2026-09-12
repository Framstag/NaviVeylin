## Context

See proposal.md — Why. Current state: `FreeDrivingScreen` gates its GPS viewport commit with a companion `shouldCommitViewport(panning, angle, newZoom)` (`!panning && (angle != null || newZoom != null)`); `NavigationScreen` gates only the heading-up angle (`shouldRotateHeadingUp`) and relies on the assumption that a suspended `AutoZoomController` never returns a zoom. That assumption is wrong: `AutoZoomController.onSpeed` re-engages on a speed-band boundary crossing (documented, intended behavior for the manual zoom buttons — `AutoZoomControllerTest.manualZoomSuspendsUntilBandChange`). While panned, a band crossing therefore yields `zoom != null` in the routing GPS handler, the commit block runs, and `reengageFollow()` re-engages the extrapolation loop, which eases the display back to the GPS fix — the map glides away from the panned position. Free driving is immune because its gate short-circuits on `!panning` before the commit block.

Both screens run their GPS collect on `Dispatchers.Main` (screen `scope`); the renderer and zoom controller are main-thread-only. No new threads or lifecycle components are introduced.

## Goals / Non-Goals

**Goals:**
- Routing pan behaves like free-driving pan: no viewport commit and no follow re-engage while panned, regardless of speed-band crossings.
- One shared commit decision used by both screens (no drift).
- Auto-zoom controller state frozen during pan; resumes on the next band crossing after pan exit.
- Regression tests for the exact composition that failed: panning + band-crossing zoom.

**Non-Goals:**
- Changing `AutoZoomController` suspension semantics (band-crossing re-engage is the documented manual-zoom behavior; the zoom buttons depend on it).
- Refactoring `MapScreen` (phone) — its pan path is separate and not affected by this bug.
- On-device verification of the fix (covered by the existing `auto-pan-during-navigation` on-device tasks 15/16).

## Decisions

### D1: Shared `shouldCommitViewport` helper in `MapPanHandler.kt` (B)

Move the free-driving decision to a top-level function in `MapPanHandler.kt`:

```kotlin
/** Whether a GPS fix should commit a viewport change: never while panned
 *  (spec: auto/map-pan — follow suspended while panned), otherwise when
 *  the heading or the zoom changed. */
fun shouldCommitViewport(panning: Boolean, angle: Double?, newZoom: Int?): Boolean =
    !panning && (angle != null || newZoom != null)
```

Both screens call it with `panHandler.panning`. `FreeDrivingScreen`'s companion function is removed; its tests move to `MapPanHandlerTest`.

Alternatives:
- (a) One-line gate in `NavigationScreen` only (`if (!panHandler.panning && (angle != null || zoom != null))`) — minimal, but leaves the two screens with two different shapes of the same logic; this drift is exactly what caused the bug. Rejected.
- (b) Companion function on `MapPanHandler` — fine, but the helper is a pure decision about the GPS commit, not pan-gesture handling; top-level in the same file keeps it importable and testable without an instance. Chosen.
- (c) Put it on `AutoMapRenderer` — rejected, the renderer is surface/rendering-focused; the decision is screen-level policy.

### D2: Gate the auto-zoom feed on `!panning` (C)

In both screens, do not feed the controller while panned. Extract a testable decision in `NavigationScreen`'s companion (mirrored in `FreeDrivingScreen`):

```kotlin
/** Speed-driven auto-zoom target: never while panned (spec: auto/map-pan —
 *  auto-zoom suspended while panned), otherwise when enabled and speed valid. */
fun autoZoomTarget(panning: Boolean, autoZoomEnabled: Boolean, speedKmH: Double, controller: AutoZoomController): Int? =
    if (!panning && autoZoomEnabled && speedKmH >= 0.0) controller.onSpeed(speedKmH) else null
```

The GPS handler becomes:

```kotlin
val zoom = autoZoomTarget(panHandler.panning, autoZoomEnabled, pos.speedKmH, autoZoomController)
```

Effect: while panned the controller is never fed, so its state (`suspended`, `lastBand`, stability samples) stays frozen at pan entry. On pan exit, the next fix feeds the controller; if the speed is still in the pre-pan band, auto-zoom stays suspended until the next band crossing — identical semantics to the manual zoom buttons (`suspend()` + band-change re-engage). No surprise zoom right after pan exit.

Alternatives:
- (a) Gate only the commit (D1) and keep feeding the controller — works, but the controller re-engages (`suspended = false`) and advances its stability/cooldown state during the pan; the first fix after pan exit can then commit a zoom the driver did not ask for. Rejected.
- (b) Change `AutoZoomController.onSpeed` to return null while suspended regardless of band — breaks the documented manual-zoom re-engage (`AutoZoomControllerTest.manualZoomSuspendsUntilBandChange`); the zoom buttons would never resume auto-zoom. Rejected.
- (c) `suspend()` on pan entry only (current behavior) — the bug. Rejected.

### D3: Regression tests target the composition, not the parts

The existing tests cover the parts (`shouldRotateHeadingUp` panning gate, `AutoZoomController` band re-engage) but never the composition. New/updated tests:

- `MapPanHandlerTest`: `shouldCommitViewport(panning = true, angle = -1.0, newZoom = 8) == false` (the exact failing case — panning + a zoom the controller would return on band crossing), plus the existing free-driving cases moved over.
- `NavigationScreenTest`: `autoZoomTarget(panning = true, enabled = true, speed = 100.0, controller) == null` even when the controller would re-engage on a band crossing (feed a controller suspended in the city band with a highway speed); `autoZoomTarget(panning = false, ...)` delegates to `onSpeed`.
- `FreeDrivingScreenTest`: remove the moved `shouldCommitViewport` cases; add the `autoZoomTarget` panning gate case for symmetry.

Threading: all decisions are pure functions on the main thread; no new dispatchers, no lifecycle changes (guidelines/Design.md §4).

## Risks / Trade-offs

- [Auto-zoom stays suspended after pan exit until a band crossing] → Intended, matches zoom-button semantics; the driver can also re-enter/exit pan or use the zoom buttons. If on-device testing (auto-pan task 16) shows it feels dead, revisit with a re-engage-on-exit rule — would be a spec-visible change, so it stays out of this fix.
- [D1 gate is now partially redundant with D2 (commit can't fire while panned because zoom is null)] → Deliberate defense-in-depth: D2 freezes controller state, D1 guarantees no commit even if a future change adds another zoom source. The redundancy is one boolean and kills the drift class of bugs.
- [Free-driving behavior change: controller not fed while panned] → Only affects the post-pan-exit zoom timing (band-crossing re-engage no longer happens mid-pan); the visible pan behavior is unchanged (commit was already gated).

## Migration Plan

Bug fix, no data migration. Rollback: revert the change — pre-change behavior (jumpy routing pan) returns. No manifest, API, or native changes.

## Open Questions

None — the deferred on-device tuning belongs to the in-progress `auto-pan-during-navigation` change (tasks 15/16).
