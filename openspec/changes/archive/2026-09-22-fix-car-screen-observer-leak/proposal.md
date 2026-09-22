# Proposal

## Why

Each of the three car map screens (`MapScreen`, `NavigationScreen`, `FreeDrivingScreen`) starts its
shared-state observers in `startObserving()` but `stopObserving()` cancels only the single tracked
`observeJob` — the GPS/navigation-state collector. Every other collector that
`startObserving()` launches (favorites, resolved dark mode, basemap revision, and for
`NavigationScreen` the GPS collector itself, which sits *outside* `observeJob`) is untracked and is
therefore never cancelled while the screen lives. `startObserving()` is called from `onStart`, so
every start adds another full set of live observers:

```
MapScreen.kt:691 startObserving
   observeJob                tracked   :693
   favoritesProvider         UNTRACKED :722  -> setFavoriteLocations -> full native render
   resolvedDark              UNTRACKED :734  -> pushDark (native stylesheet flag)
   basemapReloadNotifier     UNTRACKED :750  -> invalidateData      -> full native render
MapScreen.kt:792 stopObserving -> cancels observeJob only

NavigationScreen.kt:458 startObserving
   observeJob (nav state)    tracked
   locationProvider.position UNTRACKED :519 -> setGpsMarker/setViewport/reengageFollow per fix,
                                                plus a native getRoadAt lookup
   resolvedDark              UNTRACKED :601
   basemapReloadNotifier     UNTRACKED :614

FreeDrivingScreen.kt:416 startObserving
   observeJob (position)     tracked
   basemapReloadNotifier     UNTRACKED :431
```

The count grows monotonically with every push/pop of another screen and every background round trip
(`MapScreen` is the stack root, so a menu visit already stops and restarts it): `+3` per stop/start,
never released. Each round trip permanently adds full native renders (the `TODO.md` §49 path — four
full-size buffers, ~15 MB of transients per render, up to three live renderers, native heap measured
at 122 MB on the AAOS AVD), one native `getRoadAt` per GPS fix, and one extra viewport commit per
fix. On a long drive this is a steadily growing amount of native work and render-buffer churn
against a fixed memory budget — the "fine for a few seconds, degrades after minutes" shape of the
reported car-side instability. The observers also keep running, and keep requesting renders, while
the screen and the session are **stopped**.

Why now: the leak sits in the same car screens `fix-aaos-host-crash` hardened, so it is cheap to fix
while that change's surface/observer gating is fresh in the code, and the app-process memory ledger
(`TODO.md` §49) is the highest-ranked remaining host-stability risk after the native race in §48.

## What Changes

- **One observation scope per started period.** Each car map screen establishes its observations on
  start and cancels **all** of them on stop, so a screen runs exactly one instance of each
  observation (GPS position, navigation state, favorites, resolved dark mode, basemap revision,
  settings re-read) no matter how often it is stopped and started.
- **A single shared seam for the lifecycle.** The tracking moves out of three hand-written copies
  into one small helper in `:auto` that the three screens use, plus the `stopObserving()` call sites.
  The three copies drifting apart is exactly how the defect arose; one seam with one test prevents a
  fourth screen from repeating it.
- **No work of a stopped screen.** While a screen is not started, none of its observations touch the
  renderer gate or the host — no full-render request, no native lookup, no template invalidation.
- **Screen-lifetime work stays on the screen's own lifetime.** The free-driving stale-speed ticker
  (which must decay the speed badge during a stop, spec `gps-speed-priority`) deliberately stays
  outside the observation scope, so a background round trip still zeroes a stale speed.
- **Observations are re-established on every start**, so a change that happened while the screen was
  stopped (favorites, dark mode, basemap revision) is applied exactly once on return.

## Capabilities

### New Capabilities

- `auto/screen-observation`: the state-observation lifecycle of the Android Auto screens — exactly
  one instance of each observation per started period, nothing runs while a screen is stopped, and
  re-established observations apply the current state once.

### Modified Capabilities

No requirement of an existing capability changes. The three screens' visible behaviour, the shared
renderer state they drive, and the car templates they build all stay as specified; only the
multiplicity and lifecycle of the observers that feed them is fixed. The in-flight capability
`car-host-fault-isolation` (change `fix-aaos-host-crash`) covers *session*-scoped host-mutation
gating and is **not** modified here — its "Bounded host-facing traffic while not visible" requirement
stays the session's contract, while this change bounds what a *screen's* observers do; a task
reconciles the wording of the two (see Impact).

Referenced (not changed) specifications: `auto/browse` (browse street lookup),
`auto/free-driving` (free-driving position feed, street lookup, settings re-read on re-visibility),
`auto/navigation-view` (fix feed, route polyline, ETA-card rebuild),
`auto-smooth-follow` (fix feed from every follow-mode screen, per-renderer extrapolation gate),
`auto-map-renderer` (renderer readiness and surface state),
`gps-speed-priority` (stationary speed reads zero — the ticker this change must not cancel),
`basemap-loading` (basemap revision → re-render), `fav-markers` (favorites → renderer markers).

## Impact

**`:auto`** — the change's only module.

- `auto/src/main/java/com/naviveylin/auto/MapScreen.kt` — `startObserving()` (`:691`, observers at
  `:693`/`:722`/`:734`/`:750`), `stopObserving()` (`:792`), the lifecycle observer (`:284-311`).
- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — `startObserving()` (`:458`,
  observers at `:465`/`:519`/`:601`/`:614`), `stopObserving()` (`:661`), lifecycle observer
  (`:384-412`).
- `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` — `startObserving()` (`:416`,
  observers at `:418`/`:431`), `stopObserving()` (`:571`), lifecycle observer (`:350-380`); the
  stale-speed ticker (`:281`) stays where it is.
- New shared seam in `:auto` (observation-scope helper) plus its unit test.
- Screen tests: `auto/src/test/java/com/naviveylin/auto/MapScreenTest.kt`,
  `NavigationScreenTest.kt`, `FreeDrivingScreenTest.kt`; the renderer teardown rule
  `RendererTestRule.kt` stays the teardown contract for screens that build renderers.

**Not affected:** the phone UI, `:app`'s services and notification path, the JNI bridge, the native
render pipeline, the car templates, and the manifest. **No native/JNI change** — no submodule patch
and no bridge-module override: every defect here is Kotlin-side screen lifecycle code.

**Guidelines:** `guidelines/Design.md` §4 (threading and lifecycle per component) is the affected
document; `guidelines/MapRendering.md` §14/§15a (AA renderer ownership and bounded periodic work) is
referenced for the render-cadence consequence and needs no edit unless the observation seam changes
what the renderer is allowed to do (it does not).

**Additive, not BREAKING.** No public API, resource, manifest, Gradle or flavour surface changes.
**Rollback:** revert the change's commits; the previous per-start observer accumulation returns. The
helper and the screen call sites revert together, and nothing else depends on either.

**Scope:** Android Auto projection **and** AAOS — the car screens of `:auto` only. The phone app's
`ViewModel`-based observation (`MapCanvasViewModel`, `NavigationViewModel`) is a different structure
and is untouched.

**Verification:** unit tests for the seam (register/cancel multiplicity across repeated start/stop
cycles, idempotent start, no work while stopped) and a per-screen regression test that drives
start → stop → start and asserts one live instance of each observation and exactly one renderer
update per emitted state change; the existing `:auto` suite; both flavour builds; and an AAOS AVD
run that counts full renders (`lock OK`) and `Diag/MAP` lines across several background round trips
and push/pop cycles against the `guidelines/Build.md` §10 baseline (the counts must not grow with the
number of round trips).
