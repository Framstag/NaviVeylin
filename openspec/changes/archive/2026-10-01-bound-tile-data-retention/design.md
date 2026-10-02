# Design

## Context

`NativeTileDataCache` (`core/src/main/java/com/naviveylin/core/NativeTileDataCache.kt`) is the single
owner of the cache capacity: `PHONE_TILES = 512` applied by the phone map path
(`MapCanvasViewModel.initMap`), `CAR_TILES = 128` by the car warmup (`AutoServiceModule`), applied to
every open database by `setNativeDataCacheSize`. The policy is raise-only: a higher value is applied, a
lower one reported as `REJECTED`.

The native side already supports lowering: `libosmscout-map/src/osmscoutmap/DataTileCache.cpp:50-59`
calls `CleanupCache()` for a smaller value, which frees least-recently-used tiles. So the release path
needs no JNI change and no submodule patch.

Measurement this design is built on (2026-09-27, Pixel 8, scripted walk: 10 zoom-out steps then 14 pan
swipes, phone in the foreground, no car needed):

```
native heap   115 MB -> 337 MB   saturating (7 further samples flat to +-0.4 MB), retains 324 MB
TOTAL PSS     377 MB -> 600 MB
malloced bitmaps 62 MB RSS       flat throughout -> not the bitmap cache
```

## D1 — Which signal triggers a release

**Finding that decided this (2026-09-27, AOSP `core/java/android/content/ComponentCallbacks2.java`):**
`TRIM_MEMORY_RUNNING_MODERATE` (5), `RUNNING_LOW` (10), `RUNNING_CRITICAL` (15), `MODERATE` (60) and
`COMPLETE` (80) are each marked *"@deprecated Apps are not notified of this level since API level 34"*,
and `ComponentCallbacks.onLowMemory()` is deprecated since API 35. This app targets 36 and the phone
runs Android 17, so a trigger built on those levels would never fire. Still delivered:
`TRIM_MEMORY_UI_HIDDEN` (20) and `TRIM_MEMORY_BACKGROUND` (40). Not deprecated and still meaningful:
`ActivityManager.MemoryInfo.lowMemory` / `availMem` / `threshold`.

| | A: the `onTrimMemory` level ladder (as first designed) | B: poll `ActivityManager.MemoryInfo` | C: the two still-delivered levels only |
|---|---|---|---|
| Fires on modern Android | **no** (never delivered since API 34) | yes | yes, but only when the app leaves the foreground |
| Covers the driving case (foreground, device tight) | no | yes | no |
| Testable without real pressure | `am send-trim-memory` | injectable memory-state seam; observed on a low device | `am send-trim-memory UI_HIDDEN` |
| Deprecation warnings | 30+ | none | none |

**Chosen: B + C.** The poll is the pressure trigger (the app is *running* when it matters), and the two
surviving platform levels are a second, event-driven trigger — scoped to "no car session is live",
because with a car session the phone's hidden UI is exactly when the car keeps rendering from those
caches, so releasing there would buy a refetch storm for a process that is deliberately protected by its
foreground service. `onLowMemory()` stays inert (deprecated, and background-oriented).

The poll runs at a low rate (30 s) and only while a client has a configured capacity; a
`getMemoryInfo` call is cheap, and the loop lives in the same process-scoped scope as the release.

## D2 — How much is released

| trigger | release |
|---|---|
| poll: `availMem <= threshold / 2` | **floor** — the library default (`25`), the value the tuned constants must exceed |
| poll: `lowMemory` (i.e. `availMem <= threshold`) | **half** of the configured capacity |
| `TRIM_MEMORY_UI_HIDDEN`, no car session | **half** |
| `TRIM_MEMORY_BACKGROUND`, no car session | **floor** |
| either platform level **with** a car session | nothing — the car renders from those caches |
| `onLowMemory()`, any other level | nothing (documented inert) |

Graduated rather than "floor on any signal": the capacity is what the map *reuses* while panning, so a
first warning halves it and only a sustained or severe situation takes it to the floor. Repeated signals
converge (each release halves the current value until it reaches the floor, where a further release is a
no-op), which is what the `TRIMMED`/`UNCHANGED` outcomes make visible. Releasing to `0` is not used: the
native layer reads a non-positive value as "library default", which is the same as the floor step but
obscures the intent.

## D3 — Who owns the trim path

| | A: `Application.onTrimMemory` resolves the client and trims inline | B: a `@Singleton` responder holding `Lazy<OSMScoutClient>` | C: piggyback on the navigation engine's scope |
|---|---|---|---|
| Testability | needs the whole graph in a Robolectric test | seam-testable with a fake client (like `NativeTileDataCache.applyTo`) | couples an unrelated component |
| Cost when idle | none | none (lazy client) | none |
| Consistency | new pattern | matches `NativeLogBridge` / `NativeTileDataCache` | - |

**Chosen: B.** A `MemoryPressureResponder` (in `:app`) with an injected `Lazy<OSMScoutClient>`, the car
session presence signal, and a pure release-decision mapping; the Application registers it as a
`ComponentCallbacks2` in `NaviVeylinApp.onCreate` (one line, next to the existing native-log-bridge
install). The decision mapping and the memory-state read are seams, so the triggers, the steps and the
floor are unit-tested without a device or real pressure.

## D4 — The policy keeps its shape

Configuration stays raise-only (D4 alternative "last-wins" rejected: a surface could shrink another
surface's working set mid-session, which the spec's rationale forbids and which the phone+car case
depends on). The trim is a *separate*, deliberate operation with its own reported outcome, so the
raise-only rule remains true for every caller that is not the pressure responder.

Consequence for the spec: the existing wording ("the first surface to configure it wins, a later
configuration that requests a different value SHALL NOT change it") never matched the code; the delta
restates it as raise-only and adds the trim as the one lowering path.

## D5 — Threading and lifecycle (guidelines/Design.md §4)

- `onTrimMemory` is delivered on the main thread. The trim itself is a short JNI call that frees cached
  entries; it is dispatched to `Dispatchers.Default` (the same rule as every other native call) with
  the result recorded on the diagnostics stream.
- No new scope and no new lifetime: the responder is process-scoped (a singleton), holds `Lazy` client
  and the last applied capacity, and does nothing until a signal arrives. The diagnostics writer is the
  existing buffered worker, so no filesystem work happens on the callback thread.
- A release does not touch the render path's own storage; a render in flight is unaffected (the native
  cache's `SetSize`/`CleanupCache` run under `MapService`'s `stateMutex`).

## Verification

### Device run 2026-09-27 (Pixel 8, Play build `2026-09-27-4` / versionCode 90, DHU session, phone unlocked)

Four `Diag/MEMORY` records, all from the real release path on the device — one per trigger:

```
20:34:32  retention released: trigger=ui-hidden (level=UI_HIDDEN) 512 -> 256 tiles/db     backgrounding, no car
20:34:58  retention released: trigger=background (level=BACKGROUND) 256 -> 25 tiles/db    severe leg, floored
20:43:43  retention released: trigger=poll-low (avail=215MB threshold=216MB) 512 -> 256   the DEVICE poll, mid-walk
20:44:50  retention released: trigger=ui-hidden (level=UI_HIDDEN) 256 -> 128 tiles/db     same action, car session ended
20:45:45  retention released: trigger=ui-hidden (level=UI_HIDDEN) 128 -> 64 tiles/db      repeated
```

- **The graduated rule works on device**: half (512→256), floor to the library default (256→25), then halving
  again from a partial value (256→128→64) — always lowering, never raising, each step recorded with its trigger
  and the capacity it released to.
- **The poll fires** (task 5.2a): `avail=215MB threshold=216MB` is the framework's own `lowMemory` claim at the
  edge, and the device-state poll is deliberately not car-scoped — it released while the car session was live,
  which is the design. An earlier pass saw no poll record at all and TODO §97 was written as "the poll never
  fires"; that was an absence-of-evidence error and is corrected there (the missing ingredient was a walk heavy
  enough to put `availMem` at the threshold **while a 30 s tick lands**).
- **The car-session scoping is proven by contrast** (task 5.4): with the car session live, the cache refilled to
  512 by a walk and the phone mapped via the override, pressing HOME produced **no** release record; the *same*
  action seconds after the session ended produced `256 -> 128`. The car kept drawing throughout
  (`lock OK` up, 0 `surface invalid`/`lockCanvas failed`), so the shared cache stayed for the car as specified.
- **Dead levels are inert** (spec-required): `am send-trim-memory … RUNNING_LOW` produced no record at all.
- `am send-trim-memory` notes for the next run: the shell's level names are `HIDDEN`/`BACKGROUND`/`RUNNING_*`/
  `MODERATE`/`COMPLETE` (there is no `UI_HIDDEN`), and the platform refuses a *background* level for a
  foreground process — the reliable triggers are `HIDDEN` or simply pressing HOME.

### Walk ceiling (task 5.1/5.3)

Same device, fresh process per sample, script = 10 zoom-out steps + 14 pan swipes:

| | native heap | `TOTAL PSS` | retained at the start viewport |
|---|---|---|---|
| old build (recorded baseline) | 115 → 337 MB | 377 → 600 MB | 324 MB |
| new build | 139 → 244 MB | 318 → 511 MB | 227 MB |

The ceiling and the retention are both well below the baseline (−93 MB native, −89 MB PSS, −97 MB retained),
but the commit ships this change together with `reduce-render-peak-memory`, so the two are **not separable**
without reverting one path — measured together, attributed together.


Unit (no device):

- `NativeTileDataCacheTest` extended: raise-only (`APPLIED` on higher, `REJECTED` on lower,
  `UNCHANGED` on equal), a trim lowers and reports its own outcome, an idempotent second trim,
  `FAILED` without a native library.
- New `MemoryPressureResponderTest`: the release-decision mapping over a memory state and over the two
authoring platform levels (poll `lowMemory` → half, `availMem <= threshold/2` → floor, `UI_HIDDEN` without
a car session → half, `BACKGROUND` without a car session → floor, **either level with a car session →
nothing**, `onLowMemory()` and any other level → nothing, an unconfigured client does nothing and never
builds the client), one diagnostics record per release and none for a no-op, and that a release never
throws.
- A Robolectric test at the Application seam: registering the responder and delivering
  `onTrimMemory` reaches the client once.

On-device (the decisive one, and it does not need driving):

- `adb shell am send-trim-memory com.framstag.naviveylin UI_HIDDEN` (with no car session) → the
  diagnostics record appears and the retained footprint drops, measured per the rule above (fresh process
  per state). The command injects the level directly, which is what makes the still-delivered trigger
  verifiable; the deprecated levels are deliberately not used.
- The poll trigger on this device: the phone is chronically low (`zram` 3.84 GB of 3.88 GB), so a session
  that has walked a wide area is expected to show a `MEMORY` record *by itself* — quote the timestamp,
  the state that triggered it and the footprint drop.
- The **walk protocol** (10 zoom-out steps + 14 pan swipes via `adb shell input`) before and after, so
  the retention ceiling is quoted with numbers: baseline this build
  `native heap 337 MB / PSS 600 MB`, retained `324 MB` after returning to the start viewport.
- Rendering unchanged: the same viewport renders the same content after a release (the existing
  "Cache sizing must not change rendering output" requirement, checked visually on the device and by
  the pure test).
- The phone+car case: with a car session live, a platform level does **not** release (the scoping rule)
  while the poll still may, the release reaches the databases the car renders from (the same client), and
  the car frame continues without a `lockCanvas` failure.

## Risks

| Risk | Mitigation |
|---|---|
| Trimming during active panning causes a visible stall (refetch) | graduated release; the poll halves first and only floors in the severe band; a platform level releases nothing at all while a car session is live |
| The trigger is inert on modern Android (the discovery that changed this design) | the pressure trigger is a non-deprecated `ActivityManager.MemoryInfo` poll; the two levels used are the ones the platform still delivers, and the deprecated ones are never referenced (no deprecation warnings either) |
| The poll keeps a process busy while nothing needs it | it runs only while a client has a configured capacity, at a 30 s interval, on the process-scoped scope |
| The trim lowers the capacity and a later configuration "restores" it repeatedly | the responder records the last applied capacity; a configuration request after a trim is judged against the client's actual value, and the phone/car call sites keep requesting their constant — this is the case the diagnostics make visible |
| The change is measured on a phone that is under pressure for unrelated reasons (this device's zram is 99.5 % full without our app) | the walk protocol is a controlled measurement; the ceiling is reported as a delta against the pre-trim plateau on the same session |
| The responder holds a stale client after a client rebuild | the client is injected `Lazy` and the trim re-reads it per call, exactly as the existing application sites do |
| The constants remain unjustified (the unproven 512 effect) | the change states the ceiling requirement and makes the walk repeatable; the constant comparison is the follow-up the proposal names as out of scope |
