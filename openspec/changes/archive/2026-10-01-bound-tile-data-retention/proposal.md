# Proposal

## Why

The app's largest own memory term is not the frame render (that is `reduce-render-peak-memory`) but
**retained tile data**. Measured 2026-09-27 on a Pixel 8 by a scripted walk (app in the foreground,
10 zoom-out steps, then 14 pan swipes at a fixed zoom — `adb shell input`, no driving, no head unit
required):

| metric | after a city-zoom session | after the wide-area walk |
|---|---|---|
| native heap (Pss) | 115 MB | **337 MB** (saturates; 7 further samples flat to ±0.4 MB) |
| TOTAL PSS | 377 MB | **600 MB** |
| after returning to the starting zoom | - | 324 MB → **retained** |
| malloced bitmaps | 62 MB RSS | 62 MB RSS (flat — this is not the bitmap cache) |

So a user who has walked a wide area carries a ~600 MB PSS navigation process. In the phone + Android
Auto combination that process shares an 8 GB phone with an Android Auto host family of ~750 MB RSS
(`gearhead:projection` 249 MB, `:car` 197 MB, `:provider`/`:watchdog`/`:shared` ~101 MB each) while
zram swap is 99.5 % full (3,858,880K of 3,877,376K) and only 230-330 MB is genuinely free. The
processes lmkd reaps there are the host's — `exit-info reason=3 (LOW_MEMORY)` kills of every gearhead
process, including twice with this app not running — and that is the user-visible "Android Auto
crashes" symptom.

Two concrete defects make the retention worse than it has to be:

1. **The app never releases retention under memory pressure.** `NativeTileDataCache` is deliberately
   raise-only: `SetCacheSize` in the native cache *does* evict (libosmscout's `DataTileCache::SetSize`
   calls `CleanupCache()` for a smaller value), but the app-side policy refuses to ever lower the
   value, and nothing in the app reacts to the platform's memory-pressure signal
   (`ComponentCallbacks2.onTrimMemory`). The app is told the phone is about to start killing
   processes and does nothing with the one thing it could free.
2. **The spec and the code disagree about the policy.** `native-tile-data-cache` states "the first
   surface to configure it wins, a later configuration that requests a different value SHALL NOT
   change it", while `NativeTileDataCache` implements highest-wins (a higher value is applied, a lower
   one is reported as rejected). Either reading is defensible; the spec must say which, because the
   retention behaviour of a phone + car process depends on it.

## What Changes

- **A retention release path.** The app releases cached tile data when the platform reports memory
  pressure (`onTrimMemory` at the levels where lmkd starts choosing victims) and at the coarse
  boundaries where retention has no value (car session end, navigation end is NOT a boundary — the map
  is still displayed). The release lowers the capacity for the affected databases; the native cache
  evicts least-recently-used tile data immediately.
- **The capacity policy is stated once and honestly**: raise-only for *configuration* (no surface may
  shrink another's working set mid-session), plus an explicit *trim* that may lower it, and the
  spec/code divergence resolved in the spec's favour of the implemented behaviour.
- **The retention ceiling becomes a measured property, not an assumption**: repeated walking beyond the
  configured capacity SHALL NOT grow the footprint, and the walk protocol (scripted zoom-out + pan) is
  the verification, quoted with numbers.
- **The trim is observable**: which consumer trimmed, at which memory level, to which capacity — so a
  future triage can see the app reacting instead of inferring it from `dumpsys`.
- Kotlin-only. **No submodule change, no new JNI entry point**: the existing
  `setNativeDataCacheSize` already evicts on a smaller value. Additive behaviour; rollback is reverting
  the app-side policy/trim wiring (no persisted state).
- **Not in scope**:
  - the per-render frame buffers (`reduce-render-peak-memory`, the ~37 MB/frame transients);
  - the tuned constants' *values* as a first move: the walk measurement with a different constant is
    still owed (this change makes that measurement repeatable and states the ceiling requirement),
    because the native allocator's retention of freed pages confounds the constant's isolated effect;
  - the car host's own lifecycle bugs when the head-unit link drops (host-side, `TODO.md` §93);
  - the `Graphics`/EGL footprint of two live surfaces.

## Capabilities

### New Capabilities
- none.

### Modified Capabilities

- `native-tile-data-cache`: the capacity policy is restated to match the implemented raise-only
  *configuration* semantics and to allow an explicit *trim*; a new requirement covers releasing
  retention under platform memory pressure (including that it is recorded and that it never changes
  rendered output); a new requirement states the retention ceiling as a measurable property of the
  walk protocol.

## Impact

Affected files and modules:

- `core/src/main/java/com/naviveylin/core/NativeTileDataCache.kt` — the policy: keep raise-only for
  configuration, add the trim path (lower the capacity for the client's databases, evicting), and
  report the outcome (an enum like the existing `TileCacheConfig`).
- `app/src/main/java/com/naviveylin/NaviVeylinApp.kt` — register the `ComponentCallbacks2`
  (or implement it on the `Application`) and route `onTrimMemory` into the trim path; the app already
  owns the process-scoped init that installs the native log bridge.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` (`initMap`, the PHONE_TILES
  application site) and `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt` (the car warmup,
  CAR_TILES application site) — the call sites whose reported outcome must distinguish a configuration
  from a trim.
- `app/src/main/java/com/naviveylin/navigation/CarSessionPresenceImpl.kt` or the car session's destroy
  path (`auto/src/main/java/com/naviveylin/auto/NavigationSession.kt`) — the car-session-end boundary,
  if that boundary turns out to be worth trimming at (to be decided in design with the measurement).
- Tests: `NativeTileDataCacheTest` (policy cases: higher wins, lower rejected, trim lowers and is
  reported, idempotent trim, trim without a native library is non-fatal), plus a trim-path test at the
  `Application`/callback seam.
- Android components: no manifest change (a `ComponentCallbacks2` registration on the `Application` is
  not a manifest entry); no Gradle change. The automotive flavor inherits all of it.

Guidelines affected and to be updated in the same change:

- `guidelines/MapRendering.md` — the tile-data cache section (capacity, retention, the trim path and
  when it fires), and `guidelines/Design.md` §3/§4 if the memory-pressure seam belongs with the
  process-scoped ownership rules.

Native/JNI: **no submodule patch and no bridge-module override** — the change uses
`setNativeDataCacheSize`, whose native implementation already evicts
(`libosmscout-map/src/osmscoutmap/DataTileCache.cpp:50-59`, `CleanupCache()`).

Change class: **additive** (a release path and a restated policy; no behaviour removed except a
never-taken "the value can never go down" assumption). Rollback: revert the app-side wiring; the
capacity then stays at the configured constant exactly as today.

Scope: general app behaviour (`:app`, both flavors) — the phone map path and the car session share the
one native client, which is exactly the combination the measurement covers.

Previous specifications changed: `native-tile-data-cache`. Related but untouched:
`render-performance` and `osmscout-jni` (`reduce-render-peak-memory`), `auto-diagnostics` (the trim
line uses the existing diagnostics stream, no requirement change), `shared-resource-arbitration`
(whose "highest wins" policy this change keeps for configuration).
