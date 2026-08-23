## Context

See `proposal.md` — Why for motivation. Current state: `:auto` has `MapScreen` (browse, `MapWithContentTemplate`) with a "Free driving" menu row whose callback only stops navigation and re-centers; `NavigationScreen` (turn-by-turn `NavigationTemplate`) already implements surface rendering, GPS marker + follow, heading-up rotation (when `navNorthUp` is off), and the compass rose via `SurfaceIndicators`. `AutoMapRenderer` owns the surface, follow mode (`reCenter()`/`isFollowMode()`), `setGpsMarker()`, viewport angle, and an `overlayDrawer` hook. Street names come from the existing `OSMScoutClient.getAddressAt(lat, lon)` JNI (returns `Array<String>` with street at index 0 — already used by `MapScreen`'s details panel). The `NavigationSession` state machine switches root↔`NavigationScreen` on `isNavigating`.

Constraints:
- A full-screen `NavigationTemplate` with no `NavigationInfo` (no content slot — the map fills the surface, no host-rendered panel/box); the host draws its own compass (the app draws its own rose via `SurfaceIndicators`, cosmetic overlap accepted).
- The surface only renders on the free-driving screen (`ACCESS_SURFACE` already declared).
- Hosts on the target unit class do not forward surface gestures; interactive controls live in host action strips (see `MapScreen` notes).

## Goals / Non-Goals

**Goals:**
- Standalone free-driving screen reachable from the map menu, independent of the `isNavigating` session state machine.
- Reuse `AutoMapRenderer`, `SurfaceIndicators`, and the surface-callback pattern from `NavigationScreen`; keep all new template/overlay logic pure and unit-testable (existing factory pattern).
- Heading-up always, follow always on, GPS marker, compass, street name, exit action, zero turn-by-turn content.

**Non-Goals:**
- No route computation, rerouting, or turn-by-turn behavior — free driving is destination-free by design.
- No changes to `NavigationScreen`, `NavigationHintsOverlay`, `NavigationSession`, `:core`, or the native JNI bridge.
- No phone-side free-driving mode — this change is Android Auto only.

## Decisions

### D1: Standalone `FreeDrivingScreen`, not a mode of `NavigationScreen`
`NavigationScreen` is coupled to turn-by-turn state: it renders hints/lanes/ETA from `NavigationState`, flips template content on `isNavigating`, and is owned by the session's navigation observer. Free driving has none of that and different exit semantics (pop to map, not stop-navigation). A separate screen keeps the session state machine untouched and avoids conditional spaghetti in `NavigationScreen`.

Alternative rejected: a `mode` parameter on `NavigationScreen` — leaks free-driving concerns into the turn-by-turn screen and its tests, and risks accidental hint rendering when state transitions occur.

### D2: Free-driving template is a full-screen `NavigationTemplate`
Build the free-driving view as a `NavigationTemplate` with no `NavigationInfo` — the ONLY template with no content slot, so the map fills the surface and no host-rendered panel/box overlays it. Rejected alternatives: `MapWithContentTemplate` — works, but its mandatory content slot renders a host panel (the header-only list showed a "Free driving" box; even a header-less empty list leaves a host box); `MapTemplate` (deprecated) — full-screen, but its required `ItemList` renders a "No items" placeholder box on the host. The earlier belief that the emulator host locks the `NavigationTemplate` navigation surface predates the renderer fixes: the old `drawToSurface` had no `finally`-unlock, so a single draw exception skipped `unlockCanvasAndPost` and permanently locked the surface — every later `lockCanvas` threw "already locked" and was misattributed to the host. With the finally/release/isValid fixes the surface renders. The map action strip (single "x" exit — icon only, the map strip forbids custom-titled actions) and the right action strip (zoom controls) are carried by the template. The host renders its own compass; the app draws its own rose via `SurfaceIndicators` (cosmetic overlap accepted).

### D3: Follow + heading-up driven by the GPS flow, settings ignored
`AutoMapRenderer` defaults to follow mode; `setGpsMarker` re-centers while `followMode` is true. Free driving keeps follow engaged (no pan listener registered → no gesture can disengage it) and re-issues `reCenter()` on each GPS tick. Heading-up mirrors `NavigationScreen`'s `!navNorthUp` branch unconditionally: when `bearing >= 0`, set viewport angle to `radians(bearing)` and re-center. The shared `navNorthUp` setting is deliberately not consulted (spec: "Heading-up independent of settings").

### D4: Street name via throttled reverse geocode + pure overlay label
On each GPS tick, if the position moved ≥ `STREET_GEOCODE_MIN_MOVE_M` or `STREET_GEOCODE_MIN_INTERVAL_MS` elapsed, call `client.getAddressAt(lat, lon)` on `Dispatchers.Default`; take index 0 as the street name (blank-tolerant — blank result clears the label). Drawing is a new pure helper (`StreetNameLabel`) invoked from the existing `overlayDrawer` hook: horizontally centered at the bottom of the surface, anchored to the stable-area bottom. Keeping it pure mirrors `NavigationHintsOverlay`/`SurfaceIndicators` and makes it unit-testable without a surface.

Alternative rejected: native `CurrentRoadInfo` from the navigation controller — only produced during active routing; free driving has no route.

### D6: Speed readout below the compass, current speed in free driving
`AutoPosition` gains `speedKmH` (filled from `Location.speed` in `AutoServiceModule`). The right-edge indicator block reuses `SurfaceIndicators`: the compass rose is inset 8 dp from the right edge (mirrors the phone's 8.dp end padding — not flush against the border), and the badge below it shows the current speed when no speed limit is known (free driving has no limit data; during navigation the badge keeps showing the limit, red when over).

### D7: Auto-zoom by speed + derived heading for track replay
- Speed → magnification uses the phone's `SpeedZoomTable`, moved to `:core` (single source of truth for both modules). A pure `AutoZoomController` (`:auto`) mirrors the phone's auto-zoom semantics without route-specific parts: 3-sample stability, 2.5 s cooldown, 1-level hysteresis, manual zoom suspends, speed-band change re-engages. Commits via one `setViewport` (keeps the current heading) + `reCenter`. Gated on the shared `autoZoomEnabled` setting, loaded like `NavigationScreen` does.
- GPX track replay usually has no GPS bearing, so the map would stay north-up. `FreeDrivingScreen.effectiveBearing` falls back to the movement direction between consecutive fixes (`movementBearing`, pure companion fn, ignored below a 3 m move threshold) for both the marker arrow and the heading-up rotation.
- Rotation sign: the native and Kotlin projections rotate by R(-angle), so heading-up uses `angle = -bearing` (radians) — the phone app's convention (`MapCanvasViewModel`). `NavigationScreen` had the same `+bearing` sign bug and is fixed identically.
- GPX replay usually has no GPS speed either, which would leave auto-zoom and the speed readout dead. `effectiveSpeed` falls back to the speed implied by the movement between fixes (`movementSpeedKmH`, pure companion fn, ignored below 1 m / 500 ms).
- Fix-flow robustness: `LocationService.shouldEmit` dedupes on (time, position) instead of time alone — emulator GPX replay can deliver fixes with identical timestamps, and the old filter dropped every fix after the first (frozen marker, no speed, no zoom). The screen's GPS collect also wraps `onGpsFix` in `runCatching` so one bad fix never kills the flow.
- Surface robustness (pushed screens): the car-app library starts the new screen before stopping the old one (`ScreenManager.pushInternal`), so the old screen's `onStop` unregisters the surface callback the new screen just registered — the host then destroys the surface and every `lockCanvas` fails ("Failed to draw to surface: null"). Fixes: (1) `AutoMapRenderer.drawToSurface` unlocks in a `finally` block (an exception previously skipped `unlockCanvasAndPost`, permanently locking the surface) and logs the full stack trace; (2) `FreeDrivingScreen`/`NavigationScreen` re-register their surface callback ~500 ms after `onStart`, after the push transition, so the host keeps delivering the surface.
- Surface lock race: `Surface.lockCanvas` throws `IllegalArgumentException` ("surface already locked") when a second renderer locks the same display surface. The host reuses one display surface across screens, and a stopped screen's renderer kept rendering (only `onDestroy` shut it down) — so MapScreen's renderer could lock the surface while FreeDrivingScreen's renderer was drawing. Fix: `AutoMapRenderer.pause()/resume()` — the render loop skips frames while paused; all three screens (`MapScreen`, `NavigationScreen`, `FreeDrivingScreen`) pause on `onStop` and resume on `onStart`.
- Surface callback lifecycle (root cause of the persistent IAE): the car-app library starts the new screen before stopping the old one (`ScreenManager.pushInternal`), so the old screen's `onStop` unregistered the surface callback the new screen just registered. The workaround (delayed re-register) made the host deliver a SECOND surface, which the host then locked — every `lockCanvas` threw IAE on both surfaces. Fix: screens no longer unregister the surface callback in `onStop` — only in `onDestroy`. The new screen's `onStart` registration replaces the old screen's callback cleanly; popping destroys the screen and nulls the callback, and the revealed screen re-registers on its `onStart`. `AutoMapRenderer.surface` is now `@Volatile` (a stale surface read was observed) and `drawToSurface` serializes lock/draw/unlock across renderers via a shared lock.
- Surface release contract (still IAE after the above — the real fix): the car-app API requires every `Surface` received via `onSurfaceAvailable` to be released once replaced or destroyed, and the app never released any. A screen stopped underneath a pushed screen never receives `onSurfaceDestroyed` (the host notifies only the current callback), so the stale surface stayed held, the host's buffer queue stayed alive, and the next surface the host delivered could not be locked (`IllegalArgumentException` from `nativeLockCanvas`, null message — either `isValid()==false` or `lockCanvas` failing). Fixes: (1) `AutoMapRenderer` releases the previous surface in `onSurfaceCreated`, in `onSurfaceDestroyed`, in `shutdown`, and via a new `releaseSurface()` called from every screen's `onStop`; (2) `drawToSurface` checks `Surface.isValid()` first and treats a null canvas as a dead surface; (3) a `surfaceFailed` flag stops the render loop from hammering a dead surface with 1s native renders and the failure is logged throttled (5 s) with `isValid` + surface hash so the next logcat run distinguishes "host destroyed the surface" (`isValid=false`) from "host locked it" (`isValid=true`, `lockCanvas` throws); (4) the screens react to `onSurfaceFailed` with a capped (2/screen start) `Screen.invalidate()` to ask the host for a fresh surface.
- Speed limit: new JNI `OSMScoutClient.getMaxSpeedAt(lat, lon)` (km/h, NaN when undefined) — `DescribeLocationByWay` → nearest way → `MaxSpeedFeature` value. Queried in the same throttled job as the street name. Drawn by `SurfaceIndicators` as a standard EU sign (white circle, big red border, black text) below the speed badge, gated by `drawSpeedLimitSign` (free driving only — the navigation screen already shows the limit in its badge).

### D5: Exit = pop, plus system back
Map action strip gets `Action.BACK` and an explicit "Exit" action (via a new `NavigationScreenActions`-style factory or inline `Action.Builder`) that calls `screenManager.pop()`. `enableBackNavigation()` handles system back the same way. Since `FreeDrivingScreen` is pushed on top of `MapScreen`, pop always lands on the map view — no session observer involvement.

## Risks / Trade-offs

- [Reverse-geocode cost on every GPS tick] → Throttled by move distance + interval; geocode runs on `Dispatchers.Default`, never the main thread; failure logs and keeps last label.
- [Phone starts real navigation while free driving is open] → Session observer pushes `NavigationScreen` on top; when nav stops, `popToRoot()` returns to the map root. Free driving is popped implicitly — acceptable, no state corruption (screen holds no mutable navigation state).
- [Host compass vs surface rose duplication] → Both rotate consistently from the same bearing; the rose reuses the proven `SurfaceIndicators` code path (same as `NavigationScreen`). Cosmetic overlap accepted — host chrome placement varies by head unit.
- [Street label overlaps host chrome] → Label positioned inside the host-reported stable area (`onStableAreaChanged`), same constraint handling as the hint panel.
- [No surface gestures forwarded on some hosts] → Same limitation as `MapScreen`; zoom lives in the right action strip, so free driving stays usable.

## Migration Plan

- Pure additive: new screen + factory function + label helper; `MapScreen` menu callback switches from `stopNavigation()+reCenter()` to `screenManager.push(FreeDrivingScreen(...))`. No data schema, native, or manifest changes. Rollback = revert the callback wiring; the old behavior returns.

## Open Questions

- None that would change specs, approach, or task breakdown. Label placement and throttle constants are tuning details left to implementation.
