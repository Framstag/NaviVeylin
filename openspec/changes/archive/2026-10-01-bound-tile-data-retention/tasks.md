# Tasks

## 1. Policy: raise-only configuration, explicit trim

- [x] 1.1 Extend `core/src/main/java/com/naviveylin/core/NativeTileDataCache.kt`: keep the raise-only
  configuration semantics, and add the trim operation (lower the capacity, evict in the native cache)
  with its own reported outcome and the last-applied capacity it released to.
  *(spec: native-tile-data-cache — Native tile data cache capacity configured; Retention is released
  under platform memory pressure)*
- [x] 1.2 Unit-test the policy: a higher request is applied, a lower request is rejected
  (`TileCacheConfig.REJECTED`), an equal request is idempotent, a trim lowers and is reported, a second
  trim with the same target is idempotent, a trim without a native library fails non-fatally.
  *(spec: native-tile-data-cache — Native tile data cache capacity configured; Cache configuration
  failure is non-fatal)*
- [x] 1.3 Test that the two call sites report the difference between a configuration and a trim, and
  that a configuration after a trim is judged against the client's actual value (no silent capacity
  ping-pong). *(spec: native-tile-data-cache — A later higher request is applied)*

## 2. Memory-pressure seam

- [x] 2.1 Add the pressure responder (a process-scoped singleton: `Lazy<OSMScoutClient>`, the car session
  presence signal, and an injectable memory-state read plus a pure release-decision mapping). Two triggers:
  (a) a 30 s poll of `ActivityManager.MemoryInfo` while a client has a configured capacity — `lowMemory`
  halves, `availMem <= threshold / 2` floors at the library default; (b) the platform's two
  **still-delivered** levels (`TRIM_MEMORY_UI_HIDDEN` → half, `TRIM_MEMORY_BACKGROUND` → floor) **only
  while no car session is live**. `onLowMemory()` and every other level stay inert. The deprecated
  `TRIM_MEMORY_*` levels SHALL NOT be referenced (they are not delivered since API 34 and would produce
  deprecation warnings). *(spec: native-tile-data-cache — Retention is released when the device is low on
  memory or the app stops using it; A live car session keeps the cache)*
- [x] 2.2 Register the responder as a `ComponentCallbacks2` in
  `app/src/main/java/com/naviveylin/NaviVeylinApp.kt`, next to the existing native-log-bridge install,
  and dispatch the native call to `Dispatchers.Default` (no native call on the callback thread).
  *(spec: native-tile-data-cache — Retention is released under platform memory pressure)*
- [x] 2.3 Record every release on the diagnostics stream with its trigger (which signal, and the memory
  state or level it was derived from) and the capacity released to, on a dedicated tag, so the app's
  reaction is visible in a triage instead of inferred.
  *(spec: native-tile-data-cache — Retention is released when the device is low on memory or the app stops
  using it)*
- [x] 2.4 Unit tests: the release-decision mapping (poll `lowMemory` → half, severe band → floor,
  `UI_HIDDEN` without a car session → half, `BACKGROUND` without a car session → floor, either level with
  a car session → nothing, `onLowMemory()`/other levels → nothing, an unconfigured client → nothing and
  the client is never built), one record per release and none for a no-op, repetitions converging at the
  floor, and a release that never throws.
  *(spec: native-tile-data-cache — Low memory while the app is running releases retention; Severity and
  repetition converge at the library default; A release never faults or surfaces an error)*
- [x] 2.5 Robolectric test at the Application seam: the registration delivers `onTrimMemory` to the
  client exactly once per signal. *(spec: native-tile-data-cache — Retention is released under platform
  memory pressure)*
- [x] 2.6 Test that rendered output is unaffected: the same viewport rendered before and after a release
  produces the same content (fake-client level), and the requirement
  "Cache sizing must not change rendering output" stays green.
  *(spec: native-tile-data-cache — Rendering is unchanged after a release)*
- [x] 2.7 Confirm the car-session scoping (the design's D1 decision) with tests: a platform level with a
  car session live releases **nothing**, a car session ending does **not** release by itself (the
  responder has no session-boundary entry point), and the poll is unaffected by the presence signal.
  *(spec: native-tile-data-cache — A live car session keeps the cache)*

## 3. Build and suites

- [x] 3.1 Build via the `build-app` skill: `:app:assembleMobileDebug` and
  `:app:assembleAutomotiveDebug` compile with no new warnings. *(all specs)*
- [x] 3.2 Run the test gate per module (the `run-tests` skill): `:app` mobile and automotive, `:auto`,
  `:core`, JNI module; quote per-module counts, zero failures. Includes the existing
  `NativeTileDataCacheTest` cases for the constants and the car-only process.
  *(spec: native-tile-data-cache — Native tile data cache capacity configured)* — measured:
  `:core` 379/0/0 · `:auto` 699/0/0 · `:app` mobile 1314/0/0 · `:app` automotive 1314/0/0, both flavors
  built for arm64-v8a with 0 warnings. An earlier pass was red because this change's own
  `MemoryPressureResponder` release coroutine had no fault confinement and leaked an uncaught
  exception on `Dispatchers.Default` (TODO §96) — fixed here: the release body is `runCatching`-wrapped,
  a confined fault is recorded under `MEMORY`, and `aFaultInTheReleaseIsConfinedAndRecorded` pins it.

## 4. Documentation

- [x] 4.1 Update `guidelines/MapRendering.md`'s tile-data-cache section: capacity, retention, the trim
  path, and the level→capacity rule. Update `guidelines/Design.md` §3/§4 if the pressure seam belongs
  with the process-scoped ownership rules. *(spec: native-tile-data-cache — Retention is released under
  platform memory pressure)*
- [x] 4.2 `TODO.md`: update the tile-cache entries (§63/§65) with the measured retention ceiling
  (`native heap 337 MB / PSS 600 MB`, saturating, retaining 324 MB) and what is still owed (the
  constant comparison on a build with a different `PHONE_TILES`, and whether the allocator returns
  freed pages to the OS). *(spec: native-tile-data-cache — Retention stops at the configured
  capacity)*

## 5. On-device verification (no driving required)

**Measurement rule:** the footprint counters (`Graphics`/EGL/GL, native heap, malloced bitmaps) are
**high-water marks** that do not return inside a process — measured 2026-09-27: native heap 115 MB →
337 MB across one walk, GL 80.6 → 130.6 MB across three zoom gestures, both retained. Use a freshly
started process for each measured state, and quote the walk script with the numbers.

- [x] 5.1 Record the pre-change ceiling with the walk protocol (10 zoom-out steps + 14 pan swipes via
  `adb shell input`, phone in the foreground): native heap, TOTAL PSS, and the retained value after
  returning to the start viewport. *(spec: native-tile-data-cache — Retention stops at the configured
  capacity)*
- [x] 5.2 Trigger the platform leg without real pressure — `adb shell am send-trim-memory
  com.framstag.naviveylin UI_HIDDEN` with no car session — and verify the diagnostics record plus the
  measured drop against the ceiling from 5.1; repeat with `BACKGROUND`. Then verify the scoping: with a
  car session live, the same command SHALL produce no release.
  *(spec: native-tile-data-cache — A hidden UI releases only when no car session renders from the cache;
  A live car session keeps the cache)*
- [x] 5.2a Verify the poll leg on this chronically-low device: run a session that walks a wide area and
  watch for a `MEMORY` record appearing **by itself**, quoting its timestamp, the memory state it read and
  the footprint drop. *(spec: native-tile-data-cache — Low memory while the app is running releases
  retention)*
- [x] 5.3 Re-run the walk protocol after the release and confirm the ceiling is re-established (no
  permanent degradation) and that repeated walking does not exceed it.
  *(spec: native-tile-data-cache — Walking beyond the capacity does not keep growing the footprint;
  The ceiling is reproducible)*
- [x] 5.4 With a car session live (DHU or head unit): confirm the release reaches the databases the car
  renders from, the car frame keeps drawing (`lock OK` counting up, no `lockCanvas failed`), and the
  phone+car footprint (`dumpsys meminfo`) drops by the released amount.
  *(spec: native-tile-data-cache — Rendering is unchanged after a release)*
- [x] 5.5 Quote the before/after numbers in the change; if the release does not move the footprint,
  record that finding and treat the retention ceiling as allocator-bound rather than cache-bound —
  which is the input the constant comparison needs. *(spec: native-tile-data-cache — Retention stops at
  the configured capacity)*
