# Design: fix-current-street-name

## Context

See proposal.md — Why. Three street-name paths exist, all flawed: auto navigation + free driving use `getAddressAt` (nearest address point in the location index — side-street picks, no ref); phone navigation uses `getDescription` (nearest-way ranking with no bearing term). The native `PositionAgent` already map-matches to the route and resolves `Position::way` (the exact way the vehicle is on), but `DispatchPositionEstimate` (OSMScoutClient.cpp:2489) drops it. The AA `AANavigationController` never populates `state.currentRoadInfo` at all. The phone shows road info only during navigation.

Native purity constraint: libosmscout stays Android-free outside its frozen `Android/` dir (CI-gated). Submodule changes must be minimal and upstreamable.

## Goals / Non-Goals

**Goals:**
- Routing (phone + auto): street name + ref come from the route's resolved way, not an area search.
- Free driving (auto + phone): street name + ref come from a bearing-aware lookup that picks the street actually driven on.
- Ref shown on all surfaces, formatted "ref name" (phone's existing `currentRoadText` format).
- Off-route during navigation falls back to the bearing-aware lookup.

**Non-Goals:**
- Changing the native `PositionAgent`/`findNearest` engine behavior (reroute, off-route state) — the label lookup is a separate, self-contained path.
- Voice instructions, lane guidance, or any other navigation-engine output.
- Phone free-driving speed-limit sign (already exists via `getMaxSpeedAt`); only the street label is new.

## Decisions

### D1: Expose the route way through `NavigationPosition` (wayName/wayRef)

`DispatchPositionEstimate` reads `NameFeature` + `RefFeature` from `positionMessage->position.way` (via `position.typeConfig`) and passes them through the `NavigationPosition` constructor. One dispatch site, one ctor change; both the phone VM and the AA controller already receive `onPositionEstimate`.

- **Alternative**: new `onCurrentWayInfo(name, ref)` listener callback — rejected: more JNI surface (new method id, new dispatch branch), and the way info is inherently per-position-estimate; carrying it on the position object is the natural fit.
- **Alternative**: Kotlin-side from route geometry + instructions — rejected: the polyline carries no way identity, instructions only name the street to turn *into*, and refs are absent; the user explicitly asked for the street id from the route, which only the engine has.

### D2: New JNI `getRoadAt(lat, lon, bearing)` for free driving — not a `findNearest` engine fix

New native method: load ways in radius (~50 m), for each way find the nearest point and the segment direction at it, rank by bearing match (undirected angle diff ≤ 45° when bearing valid) then distance. Returns name, ref, type, max speed. NaN bearing → pure distance ranking (stationary fallback, current behavior).

- **Alternative**: fix `findNearest` in `PositionAgent` to consider bearing — rejected: it changes off-route/reroute engine behavior (risk), and the bearing must come from `BearingAgent` message ordering (complexity). The label is a display concern; keep it out of the engine.
- **Alternative**: Kotlin-side disambiguation with `getDescriptionCandidates` — rejected: candidates expose only description entries, no geometry; way direction cannot be computed in Kotlin.

### D3: Auto `NavigationScreen` consumes `state.currentRoadInfo`; `AANavigationController` starts populating it

The AA controller's `onPositionEstimate` currently stores only `position`. It will build `CurrentRoadInfo(wayRef, typeName, wayName)` from the way fields (D1) and copy it into state — mirroring the phone VM. `NavigationScreen.resolveStreetName` switches from `getAddressAt` to `state.currentRoadInfo` (name+ref), keeping the `StreetNameUpdater` throttle and the ETA-card fallback (design D5 of unify-auto-search) intact. When `currentRoadInfo` is null (off-route, not navigating), fall back to `getRoadAt` with the last known bearing.

- **Alternative**: keep `getAddressAt` in the auto screen and only add ref — rejected: it keeps the side-street bug; the route way is strictly better while on route.

### D4: `getRoadAt` returns a new app-owned `RoadInfo` (name, ref, typeName, maxSpeedKmH)

One native call feeds both the street label and the speed-limit sign, and guarantees they describe the same road. `RoadInfo` lives in the app-owned `osmscout-client-java` module (same pattern as the 6 local overrides); the C++ constructs it via JNI by class name, no submodule java change needed.

- **Alternative**: return `CurrentRoadInfo` + keep `getMaxSpeedAt` separate — rejected: two lookups can disagree on the road (label says street A, sign says street B) and costs a second native call per throttle window.
- **Alternative**: extend submodule `CurrentRoadInfo` with maxSpeed — rejected: upstream-owned class, larger upstream surface; `RoadInfo` is app-owned and free to shape.

### D5: Phone free-driving street label (new UI)

`MapCanvasViewModel` gains `resolveCurrentRoad(lat, lon, bearing)` mirroring the existing `resolveMaxSpeed` pattern (move threshold + cooldown, off-main-thread, updates uiState). A new bottom-center pill overlay on `MapCanvasScreen` (Compose, styled like the auto `StreetNameLabel`: dark pill, "ref name" text) shows it when no route is active; hidden when no road info. During navigation the existing `NavigationStateOverlay` road row remains the display (fed by the route way, D1).

- **Alternative**: reuse the navigation overlay in free driving — rejected: that card carries ETA/next-turn content that is meaningless without a route; a dedicated label is cleaner and matches the auto surface's pattern.
- **Alternative**: no phone free-driving label — rejected by user (Q1: "Free phone free driving, too").

### D6: Off-route fallback uses `getRoadAt` (bearing-aware)

Phone VM + AA controller: when `position.state != OnRoute` (or way info absent), populate `currentRoadInfo` from `getRoadAt(position.lat, position.lon, bearing)` instead of `getDescription`. Bearing from the position estimate (or last known). This replaces the nearest-way `getDescription` pick with the bearing-aware one.

- **Alternative**: keep `getDescription` off-route — rejected by user (Q3: "yes" to the bearing-aware fallback).

### D7: Throttling stays per-surface

- Auto: existing `StreetNameUpdater` (25 m / 2 s) gates both the route-way consumption and the `getRoadAt` fallback.
- Phone: `resolveCurrentRoad` reuses the `resolveMaxSpeed` throttle constants (move + cooldown).
- The native engine already emits position estimates per GPS fix; no new native throttling.

## Risks / Trade-offs

- **[R1] Bearing quality** — GPS bearing is noisy/NaN at low speed; a wrong bearing could pick a parallel street. → 45° threshold + distance tiebreak; NaN bearing falls back to pure distance (today's behavior, no regression).
- **[R2] Way direction ambiguity** — undirected angle diff means a oneway street driven against traffic still matches (same street, correct label). Accepted: the label names the street, not the direction.
- **[R3] Submodule patch surface** — `OSMScoutClient.cpp` (DispatchPositionEstimate + getRoadAt) + `NavigationPosition.java` (fields). → Keep the patch minimal and upstreamable; the CI Android-free gate must pass; no local changes in the frozen `Android/` dir.
- **[R4] AA process parity** — the AA controller must populate `currentRoadInfo`; if it lags the phone VM, auto surfaces show stale/absent names. → Same D1 source, same listener wiring; unit tests assert both populate identically.
- **[R5] Phone label occlusion** — a new bottom-center pill can overlap existing overlays (speed widget column is right-anchored; attribution bottom-left). → Place bottom-center with margins; verify against portrait/landscape layouts (landscape-layout spec).

## Migration Plan

1. Submodule patch (C++ + NavigationPosition.java), commit with upstreamable message, bump submodule pointer in the app repo.
2. App-owned `RoadInfo` + `getRoadAt` declaration in `osmscout-client-java`.
3. Kotlin wiring: phone VM, AA controller, auto screens, phone map label.
4. Rollback: revert submodule pointer + Kotlin changes; the old area-lookup paths remain as fallbacks, so behavior degrades to today's, not to nothing.

## Open Questions

- None blocking. (getRoadAt radius/threshold constants are tunable during on-device verification, per the tasks.)
