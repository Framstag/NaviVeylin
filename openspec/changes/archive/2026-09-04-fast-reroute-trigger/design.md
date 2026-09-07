# Design: Fast reroute trigger

## Context

See proposal.md — Why. Current gate in `NavigationViewModel.onRerouteRequest` (constants at `NavigationViewModel.kt:533-541`) requires 5 consecutive native `onRerouteRequest` calls (5s cadence from `RouteStateAgent.cpp:84`) plus a 30s hard floor, so reroute fires ~30s after first off-route detection. The 30s floor implicitly serves two purposes — noise filtering and cascade prevention — which this design splits. All changes are app-side; the native pipeline (`PositionAgent` 20m snap, `RouteStateAgent` 5s cadence) is untouched.

## Goals / Non-Goals

**Goals:**
- First reroute trigger ~10s after off-route detection (normal path), ~5s for clear deviations (>50m).
- Preserve cascade protection via an explicit post-reroute cooldown.
- Keep all existing noise guards (accuracy ≤100m, tunnel/no-signal 30s) intact.
- App-only change: no submodule patch, no vcpkg/CMake/ABI impact.

**Non-Goals:**
- No native changes (no `RouteStateAgent` cadence change, no `PositionAgent` snap change).
- No fix for the unmapped-road stall (`findNearest` → `Uninitialised` → no `PositionMessage`); that requires a native change and is tracked separately.
- No bearing-based confirmation logic.

## Decisions

### D1: Split the confirmation gate into fast trigger + explicit cooldown

**Chosen:** Lower first-trigger thresholds (`MIN_REROUTE_CONFIRM_COUNT` 5→2, `MIN_OFF_ROUTE_DURATION_MS` 30s→10s) and add a separate timestamp-based cooldown (`REROUTE_COOLDOWN_MS = 25s`) after each confirmed reroute.

**Alternatives considered:**
- *Single lowered floor (e.g., count=2, 10s, no cooldown)* — rejected: the 30s floor currently prevents cascade reroutes when the vehicle stays off the new route; lowering it without a cooldown invites repeated JNI recalculations and route-panel churn.
- *Native cadence change (5s→2s in `RouteStateAgent`)* — rejected for this change: requires a submodule patch and full native rebuild; app-only tuning reaches competitive latency (5-10s) without it. Revisit only if 5s floor proves insufficient.

**Rationale:** The two concerns have different requirements — first-trigger latency wants to be small, cascade protection wants a floor. Conflating them forces a compromise (the current 30s). Splitting gives 3x faster first trigger with identical cascade protection.

### D2: Distance fast path computed app-side from the route polyline

**Chosen:** On the first `onRerouteRequest` of an off-route episode, compute the distance from the reported position to the active route polyline (`RouteEntry.latitudes/longitudes`, already held by `RoutePanelViewModel`). If > 50m, confirm immediately (count=1). Otherwise fall through to the normal 2-count/10s path.

**Alternatives considered:**
- *Native-exposed distance* — rejected: requires JNI + native changes; the app already has the polyline, so the computation is free of native work.
- *Accuracy-tiering proxy (fast confirm when accuracy ≤20m)* — rejected: accuracy bounds noise but does not measure deviation; a 20m-accurate fix 30m off-route (parallel road) would confirm slowly despite being a real deviation. Distance is the direct signal.

**Rationale:** GPS noise magnitude is bounded by the accuracy gate (≤100m) and typically 5-15m; a >50m deviation is almost certainly deliberate. The distance check runs at most once per off-route episode (only on the first request), so cost is negligible.

**Implementation note:** pure function `distanceToPolyline(lat, lon, lats, lons)` (haversine point-to-segment, mirroring `computeRouteDistance` at `NavigationViewModel.kt:547`). Runs on `Dispatchers.Default`; result posted back to the Main-dispatcher confirmation logic. Full O(n) scan is fine (bounded by route size, once per episode); a moving window around `currentStepIndex` is a later optimization if profiling demands it.

### D3: Cooldown as a timestamp, not a counter

**Chosen:** `lastConfirmedRerouteTime` (Long, ms) set on every confirmed reroute; a new confirmation is rejected while `now - lastConfirmedRerouteTime < REROUTE_COOLDOWN_MS`.

**Alternatives considered:**
- *Count-based cooldown (require N requests after reroute before re-confirming)* — rejected: couples cooldown to the native 5s cadence; a cadence change would silently alter cooldown duration. Timestamp is cadence-independent and directly expresses the 25s contract in the spec.

**Rationale:** Timestamp is simpler, matches the spec's observable timing, and resets naturally with the existing state resets in `startNavigation`/`stopNavigation` (`NavigationViewModel.kt:96-100, 118-124`).

## Threading & Lifecycle

- All confirmation state (counters, `rerouteConfirmStart`, `lastConfirmedRerouteTime`) lives in `NavigationViewModel` — already lifecycle-aware via `viewModelScope`; no new components.
- `onRerouteRequest` handler stays on `Dispatchers.Main` (existing pattern). The pure distance computation is dispatched to `Dispatchers.Default`; the confirmation decision resumes on Main.
- State resets: cooldown timestamp and counters reset in `startNavigation` and `stopNavigation` alongside the existing resets, so a fresh route starts with a clean gate.

## Risks / Trade-offs

- [More false reroutes from 10s window] → Mitigation: native 5s cadence + accuracy gate + tunnel guard unchanged; count=2 still filters glitches up to 10s. Measure via logcat (`onRerouteRequest: pending/rerouting` lines) during real drives; tune constants if needed.
- [Cascade reroutes after fast trigger] → Mitigation: 25s cooldown; behavior equivalent to current 30s floor for the cascade case.
- [Polyline distance disagrees with native snap] → Mitigation: fast path only fires when native already reports OffRoute (>20m) AND app distance >50m; disagreement window is small and only affects fast-path eligibility, never correctness of the normal path.
- [Large route polyline cost] → Mitigation: computed once per off-route episode on `Dispatchers.Default`; O(n) scan of a 50k-point route is sub-millisecond.

## Migration Plan

- Single-file change (`NavigationViewModel.kt`) + new testable helper. Rollback: revert constants and remove cooldown/distance logic — behavior returns to the current 30s gate. No data migration, no native rebuild.

## Verification

- **Unit tests** (`app/src/test/java/com/naviveylin/navigation/`): confirmation state machine — count/duration thresholds, cooldown window, distance fast path (mock `onRerouteRequest` sequences); `distanceToPolyline` against known geometries (straight line, right-angle detour, point beyond route ends).
- **On-device**: GPX replay of a scripted deviation; measure off-route→reroute-trigger latency from logcat timestamps. Verify no cascade reroutes within 25s of a confirmed reroute.
- **Regression**: existing navigation tests and `RoutePanelComposeTest.kt` stay green; `./gradlew :app:assembleMobileDebug` compiles.

## Open Questions

- Whether 50m is the right fast-path threshold in praxis — deferrable: fixed 50m first, tune from logcat data. An accuracy-relative threshold (`max(50m, k × accuracy)`) is a drop-in later change.
