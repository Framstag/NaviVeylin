# Design — fix-car-tile-data-cache

## Context

The native side already implements the spec's mechanism; it is never told a value on the car path.

```
Java  setNativeDataCacheSize(int)      ->  ClientData::tileDataCacheSize = (n > 0) ? n : 0     (OSMScoutClient.cpp:1117)
                                           0 means "library default" (25 tiles per database)

Java  render(...)                      ->  if (tileDataCacheSize > 0) for every open db + basemap:
                                             db->GetMapService()->SetCacheSize(tileDataCacheSize)   (:1530-1545)
```
Two consequences shape the design:

1. The value is **client-global**, not per database and not per surface: one `ClientData`, and the
   render path re-applies the current value to *all* open databases on *every* render.
2. Therefore, in one process, two surfaces writing different values would flip the capacity per render
   — cache thrash, not just a preference — and the last writer before any given render wins.

Process shapes: car-only (AAOS standalone, or projection where the phone UI never opened) → nothing is
set today → 25 tiles; phone-first (projection with the phone UI) → 512 tiles for every database the car
opens; phone-only → 512 (unchanged, not part of the defect).

Existing seam precedents: `core/DistanceFormat.kt`, `core/CoordinateFormat.kt` (one owner per
formatting concern), `guidelines/Design.md` §12 ("shared logic is extracted once into `:core` … never
duplicated per variant, screen, or module"). `:core` already depends on the JNI Java classes
(`DetailsResolver` uses `DescriptionEntry`), so the seam may take an `OSMScoutClient`.

No test touches the car path's cache configuration today (`grep -rln setNativeDataCacheSize auto/src/test`
→ nothing; the fake records the calls, `FakeOSMScoutClient.kt:141-145`).

## Goals / Non-Goals

**Goals:**
- Every surface that opens databases configures the capacity explicitly; the library default is never
  the de-facto car value.
- One owner for the values and for the failure policy; the phone's value does not change.
- The effective capacity is deterministic per process: no flip between renders, no dependence on which
  surface happened to run first *for the value that gets used*.
- The car path is covered by a test that fails without the change.

**Non-Goals:**
- The JNI/native side (no submodule patch, no gitlink bump) — the mechanism is already correct.
- The `N × 512` footprint of the *shared* projection process (the phone configures first there; that is
  today's behaviour and it stays).
- A user-facing setting (the spec keeps the capacity a tuned constant).
- Tuning the car number by measurement — no head unit/AAOS device is attached; the number is a named
  constant with the measurement recorded as follow-up (TODO §63/§65).

## Decisions

### D1 — One `:core` seam owns the values

*Chosen:* `core/src/main/java/com/naviveylin/core/NativeTileDataCache.kt` — `PHONE_TILES = 512`,
`CAR_TILES = 128`, `apply(client, tiles): Boolean`.

*Alternatives:*
- (a) Keep `NATIVE_TILE_DATA_CACHE_SIZE` in `MapCanvasViewModel` and add a second constant in
  `AutoServiceModule` — smallest diff, but the spec's rule ("exceeds the default by a meaningful
  margin", per-surface relation car < phone) would live in two unrelated files with nothing keeping
  them consistent, and the next surface would add a third.
- (b) Put the seam in `:osmscout-client-java` (next to the JNI declaration) — closer to the native
  value, but that module is the Java side of the upstream bridge: a NaviVeylin capacity policy does not
  belong there, and it is compiled from submodule sources (the local-override list is fixed at five
  files).

*Rationale:* §12 single source of truth, and the values become unit-testable pure data.

### D2 — First writer wins, per client

*Chosen:* the seam remembers `(client identity → value)`; the first successful `apply` for a client
fixes the value for that client's lifetime. A later call with the **same** value is a no-op (`true`); a
later call with a **different** value does not call the JNI, returns `false` and logs one line, so the
caller can surface it as a diagnostics entry.

*Alternatives:*
- (a) Plain last-writer-wins: in a projection session the phone and the car would each re-apply their
  value on their own open/render cadence — the capacity flips, each flip changing every database's
  cache size mid-drive. Rejected: it is worse than either fixed value.
- (b) Lowest value wins (`min(phone, car)`): the phone's map would silently drop to 128 tiles whenever a
  car session starts in its process — a rendering-performance regression on the primary surface to save
  memory on a secondary one. Rejected.
- (c) Highest value wins: an AAOS head unit would inherit nothing (it is alone) but a shared process
  would sit at 512 for the car's benefit — same footprint problem as (b) mirrored. Rejected.
- (d) Configure once at client *build* time by whoever builds it: the builder does not know which
  surface will render; the phone app and the car service both go through `provideAutoClientProvider`
  (the same singleton client). Rejected: less information than the first-writer rule has.

*Consequence:* in the shared projection process the phone's 512 stays (status quo, no regression); the
car's `CAR_TILES` matters where it is meant to — a car-only process, which today runs on 25.

### D3 — The car's number

*Chosen:* `CAR_TILES = 128` (5.1× the library default, so the spec's "meaningful margin" holds; 4× less
than the phone's 512).

*Alternatives:* 512 (no footprint gain, 20× the default for AAOS — rejected); 64 (2.6× the default: a
weaker reading of "meaningful margin", rejected as the default choice); library default (needs a spec
delta weakening the requirement — rejected while the requirement stands); measure first (blocked: no
device attached).

*Risk accepted:* 128 is reasoned, not measured. It is one constant and one test assertion; the
follow-up is a `dumpsys meminfo` comparison on the AAOS AVD (`guidelines/Build.md` §10), recorded in
`TODO.md` rather than silently assumed.

### D4 — Where the car applies it

*Chosen:* in `AutoServiceModule.provideAutoClientProvider.openMapDatabases`, inside the existing
`withContext(Dispatchers.Default)` block and **before** `client.openDatabases(...)`, next to the
`DiagnosticsLog.log(WARMUP_TAG, …)` lines that already describe the open.

*Alternatives:*
- (a) At the end, after the open returns — the JNI applies the stored value on the next render, so it
  would work; but it reads as "configure after using", and a failure would be recorded after the
  success line.
- (b) In the car *renderer* build path (`AutoMapRenderer.init`) — that runs on the main thread with the
  renderer publication rule, and a JNI call there is exactly what `car-host-fault-isolation` forbids on
  a host-driven path. Rejected.

*Note:* `openMapDatabases` already runs off the host thread; the seam adds no dispatcher of its own.

### D5 — Failure policy lives in the seam

*Chosen:* `apply` catches `Throwable` (the JNI call can throw `UnsatisfiedLinkError` in a host test
without the stub, and any bridge exception in the field), returns `Boolean`, and never propagates. The
phone keeps its `Log.w` on `false`; the car adds a `DiagnosticsLog` `WARMUP` line on `false`.

*Alternatives:* keep the try/catch at each call site (two policies to keep aligned, and the second
caller would have to remember it — rejected); let the exception escape (violates the spec's
"Cache configuration failure is non-fatal" scenario and, on the car path, the host-fault rules).

### Threading, state and lifecycle

The seam is a stateless-looking `object` holding one small piece of state: the configured value per
client. Both callers are already off the main thread (`MapCanvasViewModel.initMap` runs in a
ViewModel coroutine on the injected dispatcher; the car path is inside `withContext(Dispatchers.Default)`),
and the app process has one `@Singleton` `OSMScoutClient`, but the two can race, so the guard is
`synchronized` over a `WeakHashMap<OSMScoutClient, Int>`-style structure (weak keys so a rebuilt client
does not pin the old one). No coroutine, no dispatcher, no `Context`, no lifecycle owner; the state dies
with the client. No native call is added to any host callback path.

## Risks / Trade-offs

- **AAOS car-only heap grows** from the library default to 128 tiles per database. Intended (the spec
  demands a margin and the car's rendering was the *slow* case), bounded (4× below the phone), and the
  measurement follow-up replaces the guess.
- **A car session in a phone-started process still gets 512.** Unchanged from today; the alternative
  (b) in D2 would trade a phone regression for it. Documented in the spec delta so it is a decided
  behaviour, not an accident.
- **Fake clients in tests** become their own key in the guard, so a test that uses two fakes exercises
  two values — no test-only branch; the guard is keyed by identity only.
- **The seam's memory** (weak map) must not keep a closed client alive: weak keys, and entries are
  dropped on `apply` failure.
- **No device** → the on-device half of the verification stays open; the change is still complete in the
  sense that the car path now configures a spec-compliant value, and the recipe to re-tune it is one
  constant.

## Verification

Unit (`:core`):
- `NativeTileDataCacheTest`: `CAR_TILES < PHONE_TILES`; both exceed the library default (25) by a
  meaningful margin; `apply` forwards the value (fake client records it); a second `apply` with the same
  value is a no-op and returns `true`; a second `apply` with a different value returns `false` and does
  **not** reach the client; a throwing client yields `false` and no exception.

Unit (`:app`):
- existing `MapCanvasViewModelStyleTest` cases (value applied after a successful open; not applied when
  the open fails) still pass against the seam constant;
- new provider test: build the provider with a `FakeOSMScoutClient`, a temp `maps/<region>/types.dat`
  tree, call `openMapDatabases(...)`, assert the car value reached the client and that a second call
  with the phone value does not change it (`revert-check`: remove the seam call from the provider → the
  new test fails).

Build/regression: `:app:assembleMobileDebug :app:assembleAutomotiveDebug` plus the flavor-qualified
unit-test tasks for `:app`, `:auto`, `:core` (the `run-tests` skill), no new warnings.

On-device (car, when a device is available): AAOS AVD or head unit — `adb logcat -s NaviVeylin` with
`osmscout::log.Debug(true)` shows `[JNI] setNativeDataCacheSize(128)` and the render line
`[JNI] render: applied tile data cache size 128 to N db(s)`, and `dumpsys meminfo <pkg>` native heap is
compared before/after for the follow-up measurement.
