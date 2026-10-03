# NaviVeylin TODO 

**Legend:** ✗ = missing | ⏳ = in progress / blocked | ✅ = done

---

## 116. The phone loads the whole stylesheet set ~13 times during one startup — Found 2026-10-02 while verifying `condense-style-load-warnings` on `emulator-5554` (phone AVD, stale map data)

- **Observed** ℹ: with the condensed report in place, one startup of the debug build emitted **104** `Unknown types in '…'` lines — 8 files (`standard.oss` plus `basemap`, `place`, `religious`, `shop`, `tourism`, `natural`, `amenity`) × **13 loads**, all inside **one** process (`Start proc` count 1, same pid, 13:08:28-13:08:41). The native Info line of every adopted load agrees: `Created new style with …/standard.oss` appears **12** times in the same window (the basemap's own stylesheet is loaded per basemap database open on top).
- **Consequence** ⏳: every load parses `standard.oss` and its ~40 `MODULE` includes, so a startup pays that parse cost a dozen times — and, before `condense-style-load-warnings`, paid 9159 log lines for it. The condensation did not create the repetition; it made it visible by attributing each line to a file (`TODO.md` §89/§90/§91 are the data side of the same lines).
- **Fix candidate**: find what re-applies the stylesheet per startup (the settings emission into `MapCanvasViewModel.applyStyleSheet`, the basemap database open, `reloadBasemap` after the asset refresh, the additional-map `openDatabases` batch) and keep one load per (style file, flag set) per startup — e.g. skip a reload whose stylesheet path and flags equal the active ones, which needs `DBInstance`/`DBThread` to expose the active stylesheet plus its flags. Verify with `adb logcat -s NaviVeylin | grep -c 'Created new style'` (expect 1-2 per surface) and the report count (8, not 104).

---

## 115. The meson suite's per-test timeout kills the threaded-database stress test on this machine — Found 2026-10-02 while running the submodule suite for `condense-style-load-warnings` (harness)

- **Observed** ℹ: a full `meson test -C hostbuild --timeout-multiplier 2` run reports `139 OK, 1 TIMEOUT` — `libosmscout:Check threaded database` (`Tests/ThreadedDatabaseTest --threads 100 --iterations 1000 <testregion> <stylesheets/standard.oss>`) is killed at 60 s (`killed by signal 15 SIGTERM`). Run alone with `--timeout-multiplier 60` it passes in **120 s** (2 m 0 s wall, 13 m 4 s sys). The suite's default budget per test is 30 s, doubled by the multiplier this repo's recipe uses.
- **Why it is not the change under test** ℹ: that change touches the OSS stylesheet parser only, and its effect is a smaller log (one line per parsed style file instead of one per occurrence), i.e. strictly less work per iteration. The test is a 100-thread × 1000-iteration DB stress run whose cost is machine load.
- **Consequence** ⏳: a full-suite verdict cannot be quoted as "all green" on this machine without knowing the one timeout, and a regression that merely makes the test slower would hide behind it. The `build-app`/`run-tests` recipe does not mention a per-test timeout at all.
- **Fix candidate**: raise the budget for that test in `Tests/meson.build` (`timeout:` on the `test()` call), or record the per-test multiplier that makes the whole suite green in `guidelines/Build.md` §6 next to the other suite-budget notes (`TODO.md` §94 is the Gradle half of the same problem).

---

## 114. Requirements that span phone and car keep being proven on one surface — two capabilities were closed on the other surface in the same week — Proof gap of `2026-09-14-smooth-decimal-auto-zoom` and `2026-09-11-tile-cache-perf`, found 2026-10-01 auditing `fix-phone-zoom-animation-parity` and `fix-car-tile-data-cache` (specs `smooth-zoom`, `native-tile-data-cache`)

- **Observed** ℹ: `smooth-zoom`'s "Eased zoom animation on discrete zoom input" was proven from the Android-Auto side (`2026-09-14-smooth-decimal-auto-zoom`, one `verify:` clause) while the phone scaled the *old* magnification across the animation gap; `fix-phone-zoom-animation-parity` had to MODIFY four `smooth-zoom` requirements to say so and added `ZoomWalkTest` plus `ZoomControlsAnimationTest`. The native tile data cache was configured only in `MapCanvasViewModel.initMap` (`2026-09-11-tile-cache-perf` task 1.3 asserted exactly that call) and never by the car warmup (§63); `fix-car-tile-data-cache` needed a shared `NativeTileDataCache.apply` seam before the car case existed at all (its task 1.2/3.2).
- **Consequence** ⏳: `openspec/config.yaml` (`specs` rules) asks for a *parity statement* when a feature affects both surfaces, but nothing asks for each surface's **evidence** — a requirement worded once and proven once reads as proven for both, and the unproven surface is found on a device, weeks later.
- **Fix candidate**: one case per named surface for every requirement that names both (phone case + car case, or an explicit host-substitute note when a surface is device-gated — §66). Drafted rule text for `config.yaml` `rules.tasks` and `guidelines/Build.md` §6 is in the audit's proposal; cross-ref §102 (scenario without a case), §109 (helper proven, wiring not).

---

## 113. Origin changes ship without a falsification step — every later fix change then adds one — Proof gap of the 2026-08-13 … 2026-09-19 origin changes, found 2026-10-01 auditing the 15 newest `fix-*` changes (process)

- **Observed** ℹ: of the origin changes behind those 15 bugfix changes, the ones from `2026-08-13-auto-startup-crash-diagnostics` to `2026-09-19-fav-order-in-group` carry **0** `verify:` clauses in `tasks.md` (`2026-09-11-tile-cache-perf` 11 lines / 0 clauses, `2026-09-08-turn-instructions-not-l10n` 0, `2026-09-04-enlarge-phone-nav-overlays` 0, `2026-09-04-fix-route-refresh-and-auto-zoom-reengage` 0, `2026-08-13-auto-startup-crash-diagnostics` 0; only `2026-09-17-basemap-own-stylesheet` has 13). Every fix that followed added the falsification the origin lacked: `fix-favorite-store-write-race` tasks 1.5 and 3.3 remove one mutex and require the interleaving case to fail, `fix-native-database-open-race` task 1.4 removes six `scoped_lock` lines and requires SIGSEGV (3/3 runs). Where an origin did see the risk it deferred instead of proving it — `2026-09-19-fav-order-in-group` task 3.4: "Append the pre-existing, unrelated risk found while implementing this change — interleaved repository writes …".
- **Consequence** ⏳: an assertion that guards an invariant is never shown to be *capable of failing*, so a case with the wrong arguments, the wrong seam or a tautology passes as proof (the `FollowAnchorFramingTest` anchor case, §111, is exactly that shape). Two capabilities were patched for the same class inside one week (`fav-service` + `osmscout-jni`, `native-tile-data-cache` + `smooth-zoom`).
- **Fix candidate**: rules text — one revert-check task per new invariant in `tasks.md` (remove the guard, one mutation, name the case that must fail; restore it), plus the `guidelines/Build.md` §6 bullet drafted by this audit. Cross-ref §40 (guardrails), §111.
- **Infrastructure landed 2026-10-03** ✅ (rules text + skill; the per-change history stays open until a change
  written under the new rules archives):
  - **`revert-check` skill** (`.pi/skills/revert-check/SKILL.md`, machine-local like the other skills) — the full
    discipline: name the triple (invariant / single mutation / case that must fail) *before* touching code, one
    mutation only (§40.12), run **every** case the guard protects, require the failure to be the expected
    assertion (not a premise or a compile error), restore and grep the mutation marker away, then force the
    green run (`--rerun-tasks`) — because restoring returns the tree to a cached state that would answer
    `UP-TO-DATE` while the XML keeps the previous `timestamp` — and quote both runs in the task. It also covers
    the honest outcome when a guard is structural and cannot be falsified through the public API (the
    `fix-phone-gesture-pan-tracking` task 7.6 precedent).
  - **`openspec/config.yaml` `rules.tasks`** now asks for one revert-check task per new invariant, bound,
    predicate or refusal path, and names the skill — so a change written from here carries the task.
  - **`guidelines/Build.md`** §4 gained the two rules this needed (a restored tree is a cached tree — check the
    XML `timestamp`; sweep the suites for the constants/values a change moves *before* the gate), §1 lists the
    fourth skill, and §2's output contract was corrected to the foreground + redirect pattern (§100).
    `AGENTS.md`, `openspec-apply-change` and `run-tests` point at `revert-check` by name.
  - **Evidence for the discipline**: `fix-area-fit-zoom-rounding` ran four revert-checks this way (loop removed →
    3 cases, favourite call site unverified → 1, floor back to 14 → 1, embedded fit unverified → 2), and one of
    them showed a case was *not* falsifying its guard — which is what the skill exists to catch.

---

## 112. A spec scenario can stay unproven for months — `map-render` "Open invalid map database" had no case from 2026-07-29 until 2026-10-01 — Proof gap of `2026-07-29-draw-map`, found 2026-10-01 auditing `fix-open-database-path-validation` (spec `map-render`)

- **Observed** ℹ: the scenario (merged spec `openspec/specs/map-render/spec.md:81-85`, "THEN `OSMScoutClient.openDatabase()` returns false") was added by `draw-map`, whose tasks carry no case for it (tests cover `ViewportStorage` only, and the manual smoke test 7.3 stayed unchecked). Two months later `fix-open-database-path-validation` had to implement the enforcement at all — its proposal records that the phone error branch (`MapCanvasViewModel.kt:1855-1870`) was dead code for exactly the case it was written for — and did it with native predicate cases (task 1.3) plus the error-state case (task 3.1). The defect itself (a `openDatabase` that registered and reported success for a path that need not exist) is fixed by that change.
- **Consequence** ⏳: a WHEN/THEN the spec demands can go unenforced for months behind a green suite. The archive guidance already asks for scenario→**task** traceability, and a task can cite a scenario without any case exercising it — nothing asks for scenario→**case**.
- **Fix candidate**: a scenario→case mapping task in the `config.yaml` `rules.tasks` list (or the archive guidance): enumerate every delta scenario and name the test case or the device step that exercises it. Same class as §102 (four scenarios of `reorder-favorite-groups`) — fix the pattern once, not per capability.

---

## 111. The follow blit anchor is proven only as pure geometry — no test drives the production call site, and the pipeline was patched twice on 2026-09-27 — Proof gap of `anchor-per-surface-visible-area` (and of `fix-follow-vehicle-jumps`), found 2026-10-01 auditing `fix-phone-follow-blit-anchor-mismatch` (spec `smooth-follow`)

- **Observed** ℹ: `FollowAnchorFramingTest` (`app/src/test/java/com/naviveylin/ui/map/FollowAnchorFramingTest.kt`) proves the framing contract with the anchor fractions it is *handed* — `:203` "resolved anchor keeps the marker on the content with overlays" was green while the follow display block in `MapCanvasScreen.kt` passed `ui.activeFollowAnchor` (raw preset ≈ 0.9) to `FollowPrediction.displayOffsetPx`, so the map content sat at the raw anchor while the marker drew at the resolved one (≈ 0.76) — a `(0.9 − 0.76)·H` offset plus per-500 ms re-render churn. Production now passes `state.resolvedAnchor` (`MapCanvasScreen.kt:1506`), the same day `fix-follow-vehicle-jumps` had already reworked this pipeline without catching it. The mismatch fix's own regression case (`:269`) asserts the *defect geometry* by handing the raw preset in, so nothing observes which anchor the **call site** passes — its task 1.1 closed that by inspection only ("grep `activeFollowAnchor.fx|fy`").
- **Consequence** ⏳: the call site can regress to the raw preset again and the suite stays green; the symptom (marker riding ahead of the route content in the driving direction, permanent render churn) is only visible on device. Two fixes to the same anchor in one day is the evidence that geometry tests do not protect the wiring.
- **Fix candidate**: assert the anchor at the call site — drive the follow display block, or extract a `followOffsetArgs(state, ui)`-style seam the screen must call, with overlays measured and assert the fractions equal `state.resolvedAnchor.fx/fy`; add a guard that fails when `activeFollowAnchor` is read from the follow offset path. Cross-ref §109 (same class: helper proven, wiring not).

---

## 110. The scope filter can leave a scoped search with fewer free-text candidates than the requested limit — Found 2026-10-01 while implementing `fix-cross-database-search-scope` (design Open Question)

- **Observed** ℹ: the free-text walk stops once it has collected `limit` hits (`freeTextEntries.size() >= static_cast<size_t>(limit)`, `OSMScoutClient.cpp:3958-3960` — the per-source budget that keeps the text index from crowding structured results out, spec `search-free-text`), and the scope filter runs **after** the walk (`fix-cross-database-search-scope`). When the scope drops 30 of 60 collected hits, the caller receives fewer than the requested candidate count even though more in-scope hits may exist behind the walk's stopping point.
- **Consequence** ⏳: a scoped search can display fewer results than it should — a recall loss only on installs where a database holds objects outside the scope's extent, so it does not affect the phone's common single-map case. The displayed list stays correct (ranked, no wrong entries), it is just shorter than the candidate budget allows.
- **Fix candidate**: count only *kept* hits against the budget (test the scope inside the collection loop, before `freeTextLimitReached()`), or raise the collection budget by a bounded factor when a scope is active and trim after filtering. Both need a native measurement of the walk's cost on a real index (the reason the budget exists), so this waits for a device run.
- **Not verifiable today** ✗: the difference is only observable with two loaded maps of different regions plus a GPS scope, i.e. the same device setup the change's task 5.3 needs.

---

## 109. The cross-database scope filter is host-tested only as a pure helper — the wiring inside the JNI search has no database-backed test — Found 2026-10-01 while implementing `fix-cross-database-search-scope`

- **Observed** ℹ: the geographic decision itself is covered by host tests (`Tests/src/SearchScopeTest.cpp`, 9 cases / 50 assertions: inside/outside, corner normalization, unset box fail-open, node fallback, the superset property, non-finite positions), but the code that *uses* it — deriving the extent from a region's object and dropping free-text hits in `DoSearchLocations` — runs only inside the JNI translation unit, which the host build cannot compile (TODO §66: `jni.h` is configured against a JDK that is not installed).
- **Consequence** ⏳: a regression that stops calling the filter, or that derives the extent from the wrong region, is not caught by any test — only by the on-device check of that change (task 5.3, device-gated) and by code review.
- **Fix candidate**: a database-backed native test in the submodule's `Tests/` that imports two small maps (the runtime-import pattern of `LocationServiceTest.cpp`, full non-eco imports per §40.34) and asserts that a hit outside the scope's box from the second database is absent and an in-scope one is present; alternatively a narrower seam that exposes the extent derivation for a host test.

---

## 102. Four scenarios added by `reorder-favorite-groups` have no automated test — Found 2026-09-29 (during that change's task 5.5 traceability pass)

- **Coverage gaps** ⏳: the change's specs are behaviour-complete, but these scenarios rest on a library
  contract, on a surface the unit tests cannot compose, or on a failure the test doubles cannot inject:
  1. `group-ordering` — *A group used as a single-group default is the first stored group*: the sheet picks
     the first group of `orderedGroupNames(state.groupOrder, state.groups.keys)` for "Add current location",
     but that line is inline in `FavoritesSheet`'s dialog wiring, so no test asserts which group it picks
     (the helper itself is covered in `:core`). A sheet-level Compose test with a ViewModel fake would
     close it.
  2. `group-grid-display` — *Dragged card follows the finger* and *Nothing is persisted during the drag*:
     the lifted card and the shift-to-make-room are `sh.calvin.reorderable`'s contract; the tests only
     observe that nothing is committed until the release (`FavoritesSheetGroupReorderComposeTest`). The
     same two scenarios are equally untested for the favorite reorder (spec `fav-management-ui`), so this
     is a gap of the pattern, not of this change.
  3. `group-grid-display` — *Sheet dismissed during a drag*: the deferred-commit design covers it (the
     commit is a `LaunchedEffect`, cancelled with the composition), and the code states it, but no test
     dismisses the sheet mid-drag; the favourite equivalent has the same gap.
  4. `group-ordering` — *Persistence failure keeps the app usable*: no test injects a failing
     `saveFavoriteLocations` (the fakes always return `true`), so a persist that fails while the native
     move succeeded is unverified — inherited verbatim from `fav-ordering`'s identical requirement.
- **Fix candidate**: (1) a `FavoritesSheet` Compose test over a fake `FavoritesViewModel`; (2)/(3) accept the
  library contract and record the visual checks in the on-device pass, or drive a dismissal in the Compose
  test; (4) give both fakes a `saveFailure` switch, then assert the snackbar/message path in
  `FavoriteRepositoryTest` and `FavoritesViewModelTest`.

---

## 101. `NavigationEngineTest.listenerCallbacksDriveTheSharedState` flakes inside full-suite runs under load — Found 2026-09-28 during `fix-stale-fix-quality` (verification gate)

- **Observed** ℹ: across four full `:app` suite runs (1336 tests per flavor, `--continue`) the case failed **twice** — once in the
  mobile flavor, once in the automotive one — always on the same assertion,
  `assertTrue("lane guidance mirrored", state.laneSuggested && state.laneCount == 3)`
  (`NavigationEngineTest.kt:114`), with `1336 tests completed, 1 failed` and no other failure in the run. The same four runs
  were green twice, so the rate is roughly one in two under the suite's load.
- **Not attributed to that change** ℹ: the case drives the engine's native-listener mirrors (`onLaneUpdate`, `onRouteInstructions`, …)
  followed by `advanceUntilIdle()`. The change it was found in touches `MapCanvasViewModel`'s fix-quality collector and
  `LocationService.isLocationSourceEnabled` only, and `:app/navigation` has no `GpsFixQuality` consumer. It passes **alone**:
  three consecutive runs of the whole `com.naviveylin.navigation` package (23-26 s each) and a focused five-class run with the
  new test classes were green. The shape matches the load-sensitive `:app` suite flake family (its root cause — the
  `MemoryPressureResponder` leak — was closed 2026-09-27 by `bound-tile-data-retention`).
- **Update 2026-09-28 — the gate of change `fix-diagnostics-stale-coordinate-purge` (task 4.2)** ℹ: the shape is unchanged and the victims still move. One full `:app` mobile run (1336 tests) failed **two** classes — this entry's `NavigationEngineTest.listenerCallbacksDriveTheSharedState` (the assertion above) **and** `MapCanvasViewModelDarkModeTest.ambientSensitivityPersistsInUiState` with `java.lang.IllegalStateException: Dispatchers.Main is used concurrently with setting it` thrown in `MainDispatcherRule.starting` (`app/src/test/java/com/naviveylin/test/MainDispatcherRule.kt:29`); the *next* run of the same suite failed `MapCanvasViewModelCandidatePickerTest` twice instead; the automotive flavor (1336 tests) failed `MapCanvasViewModelViewportRestoreTest` with the same `Dispatchers.Main` race followed by a bare `NullPointerException` — the exact victim class and NPE tail that flake family already recorded for it. All four classes are green when run alone in both flavors (`--tests` filter), so none is attributable to the change under test, which touches `:core` only. The `Dispatchers.Main`-race shape is a **new symptom** for this family: a coroutine from an earlier test is still dispatching on the main dispatcher while a later test's rule replaces it — the same `UncaughtExceptionsBeforeTest` leak mechanism the family root-caused, one more thing for the bisection in the fix candidate below to look for. Evidence runs: `/tmp/suite-app-mobile.log`, `/tmp/suite-app-mobile-2.log`, `/tmp/suite-app-auto.log`, `/tmp/app-victims-alone.log`.
- **Update 2026-09-28 — the `Dispatchers.Main` race in this family is fixed** ✅ (`fix-fix-quality-tick-main-race`, commit
  `dbd4a02`, CI run `36463219672` green): the leaker was not a sibling test but `MapCanvasViewModel`'s fix-quality tick — it ran
  its loop *on* the main dispatcher and hopped to `Dispatchers.Default` only for the delay, so every still-live ViewModel (tests
  build one per case and cancel only the current instance) read `Dispatchers.Main` once per second from a real worker, and
  `TestMainDispatcher` threw the recorded read at whichever rule replaced the main dispatcher next — which is why the victims
  moved. The loop's home is now `Dispatchers.Default` and it reports a tick only when its derivation differs from the published
  quality. Evidence: reproducer 677/2 → 677/0, `./gradlew test` 3807/0/0, three further full `:app` mobile runs green, guard
  `aTickDoesNotDispatchOnTheMainDispatcher` (0 dispatches from another thread, 10 with the pre-fix loop). Signature and rule:
  `guidelines/Build.md` §4, `guidelines/Design.md` §4; full trail: the commit and the change's local OpenSpec dir.
- **Still open here** ⏳: the original observation above — `listenerCallbacksDriveTheSharedState` failing on `"lane guidance
  mirrored"` under load — is a different symptom (mirrored state, no dispatcher involvement) and is not explained by that fix.
- **Update 2026-09-29** ℹ: reproduced twice more while verifying `reorder-favorite-groups` — `./gradlew test`
  failed in the **automotive** flavor (1396 tests, the same `lane guidance mirrored` assertion) on two
  consecutive runs, while the mobile flavor passed; the class passes alone (12/12, 19 s). The rate was
  measured: 3 runs of `com.naviveylin.navigation.*` alone were green, and the same navigation set with
  `FavoritesSheetGroupReorderComposeTest` appended failed once in 3. That experiment was not trustworthy on
  its own (the flake's own rate makes a single-run comparison meaningless); the useful part came from
  bounding that new test's drags — a card dragged 200 px past the grid's edge starts the reorder library's
  drag auto-scroll — after which navigation + that class was green 4 times in a row and the full suite went
  green. Kept as evidence, not as an attribution: see `ki_processing_failures.log` (2026-09-29) for the
  measurement lesson.
- **Update 2026-09-29 (during `order-starred-favorites`)** ℹ: a further victim in the same family, again in the
  **automotive** flavor of a full `./gradlew test` (1437 tests, one failure):
  `NavigationEngineRerouteTest.instructionListUpdatesAfterAReroute` with `AssertionError: State condition not met within 5s`
  — a different class and a 5 s await instead of the `lane guidance mirrored` assertion, i.e. the load-sensitive shape,
  not a specific mirror. The mobile flavor of the same run was green, and the class passes **alone** in the automotive
  flavor 3/3 (4 m 44 s / 3 m 33 s / 2 m 42 s with `--rerun-tasks`). The change under verification touches the favorites
  repository, its two DI providers, the phone chip bar and the car favorites screen; `:app/navigation` consumes none of
  them. Note the run also hit a harness artifact: `java.nio.file.NoSuchFileException:
  app/build/test-results/testMobileDebugUnitTest/binary/in-progress-results-generic.bin` after an earlier run started
  detached and was killed (§100) — removing the stale `test-results/<flavor>` directory before a rerun is what makes the
  next verdict readable.
- **Update 2026-09-30 (during `fix-open-database-path-validation`)** ℹ: the family reproduced twice more in the **automotive** flavor of a full `:app:testAutomotiveDebugUnitTest` (1453 tests each run). Run 1 failed
  `NavigationEngineTest.listenerCallbacksDriveTheSharedState` on the assertion *below* the documented one —
  `AssertionError: instructions mirrored expected:<1> but was:<0>` (`NavigationEngineTest.kt:115`, while the
  original victim was the `lane guidance mirrored` line just above it) — and run 2 failed
  `NavigationEngineRerouteTest.instructionListUpdatesAfterAReroute`, the victim the 2026-09-29 update already
  recorded. The third full run of the same suite was green, and `NavigationEngineTest` alone is 12/12 green (47 s).
  The change under verification adds one path-existence check in the native client, its two JNI entry points and one
  `initMap` test case; `:app/navigation` consumes none of it. The mobile flavor of the same change was green
  (1453/0).
- **Update 2026-10-01 (during `fix-cross-database-search-scope`)** ℹ: the family reproduced once more, again in the **automotive** flavor of a full `:app:testAutomotiveDebugUnitTest --rerun-tasks` (1462 tests), with a **new victim class** and both symptoms at once. Run 1 failed `MapCanvasViewModelModeTest.navigationEndRestoresBrowseMode` and `MapCanvasViewModelModeTest.enterFreeDriveAppliesDrivePreset`, both `java.lang.IllegalStateException: Dispatchers.Main is used concurrently with setting it` — one thrown in `MainDispatcherRule.finished` (`MainDispatcherRule.kt:33`, `resetMain` while another coroutine reads the dispatcher) and one in `TestMainDispatcher.dispatch` from a resumed continuation. Run 2 of the same content was green (1462/0/0), and the class alone was 8/8 green in the same flavor (18 s); the **mobile** flavor of the identical content was green (1462/0/0). The change under verification touches the native search scope (`OSMScoutClient.cpp`), `SearchResultRanker` and its tests, and `MapCanvasViewModelModeTest` exercises mode/preset derivation with no search involved — so this is the load-sensitive shape, not an attribution. Evidence runs: `/tmp/app-auto-full.log` (failing), `/tmp/app-auto-full-2.log` (green), `/tmp/app-auto-alone.log` (green).
- **Update 2026-10-01 (during `fix-car-search-default-admin-region`)** ℹ: reproduced once more in the **automotive** flavor of a full `:app:testAutomotiveDebugUnitTest` (1462 tests): 1 failure, this entry's `NavigationEngineTest.listenerCallbacksDriveTheSharedState` on the `lane guidance mirrored` assertion (`NavigationEngineTest.kt:114`). Evidence: the class alone is 12/12 green (10.1 s, `started=2026-10-01T19:14:44Z`), the rerun of the same suite is green (1462/0/0, `started=2026-10-01T19:17:22Z`, 2 m 25 s, 13 executed tasks), and the **mobile** flavor of the failing run was green (1462/0/0). The change under verification touches the `:core` search-region rule, the `MapCanvasViewModel` region delegation and the car search's DI wiring; `:app/navigation` consumes none of it.
- **Fix candidate for the still-open assertion**: bisect the suite execution order (per-class `timestamp` from
  `test-results/*.xml`, then `--tests` filters — the technique that settled the `MemoryPressureResponder` leak) and check whether the mirroring work is
  dispatched off the test scheduler: the case must then await the mirrored state instead of relying on `advanceUntilIdle()`. Per
  §40 item 38 a flake of unknown origin is recorded with its rerun evidence rather than retried silently; for a
  `Dispatchers.Main is used concurrently` failure, start at `guidelines/Build.md` §4 instead.

---

## 100. A backgrounded Gradle build does not survive the agent shell tool call — the `build-app` / `run-tests` skills' detached flow is unusable in this harness — Found 2026-09-28 during `fix-car-render-coordinate-redaction` (harness)

- **Observed** ℹ: `nohup ./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug > /tmp/x.log 2>&1 &`
  (the exact flow `build-app` §2 prescribes) returns immediately, and the build is then **killed**: the tool call
  ends, the harness kills the process group, `pgrep -f 'GradleWrapper[M]ain'` is empty, and the log stops
  mid-build — at `:app:compileMobileDebugKotlin` with no `BUILD SUCCESSFUL`/`BUILD FAILED`, i.e. the
  "killed, not failed" signature that skill itself warns about. `setsid` (the obvious detach) is refused:
  `'setsid' is not in the shell allowlist` (permanent).
- **Consequence** ⏳: a full build or a suite started this way silently loses its verdict; a session that then
  reads the truncated log or the tool's exit code reports a failure that never happened (or worse, a
  `up-to-date`-looking green from a *cached* task — §17). The `-Pandroid.injected.build.abi=` warning and
  §40.3/§40.4 assume the detached form works.
- **Fix candidate**: change the two skills (`build-app`, `run-tests`; both `.pi/skills/`, machine-local) to the
  pattern that does work here — one **foreground** call with the output redirected and only the verdict grepped
  back (`./gradlew … > /tmp/x.log 2>&1; echo "exit=$?"; grep -E 'BUILD SUCCESSFUL|BUILD FAILED' /tmp/x.log`),
  keeping the rule that the verdict comes from the log. Evidence from this change: `:app` mobile 1318 tests in
  3 m 6 s and automotive 1318 in 2 m 36 s both completed inside one foreground call, so the output cap is not
  the binding constraint once the output is redirected.
- **Half fixed 2026-10-03** ✅/⏳: `run-tests` now prescribes the foreground + `> /tmp/<name>.log 2>&1` pattern
  (its step 2 names this entry and the process-group kill), and `guidelines/Build.md` §2's "output is never
  redirected" contract was corrected to "redirect, keep the console readable" — so the documented convention no
  longer contradicts the harness. Evidence the pattern holds: a four-module `--rerun-tasks` gate (4191 tests,
  182 executed tasks) and a 10 m 12 s run both completed inside one foreground call with only the verdict and
  the `w:` lines grepped back. **Still open**: `build-app` §2 prescribes the same unusable `nohup` flow — same
  three-line fix, not applied with that change because it is a different skill's procedure.
- **Logged** ✅: the failed approach and the working one are in `ki_processing_failures.log` (2026-09-28 entry).

---

## 95. The retention release had no on-device run: the installed build is Play-signed, so a local build needs an uninstall — and the map data goes with it — Found 2026-09-27, **RESOLVED 2026-09-27 20:27** by a Play release (see the update at the end)

- **Observed** ℹ: the phone's installed build (`2026-09-27-3`, versionCode 89, `installerPackageName=com.android.vending`) came from the Play Store, so `adb install -r` with a locally built APK fails on the signing key, and a fresh install means `adb uninstall` first — which deletes the app's files **including the installed map databases** the on-device checks render from (region + basemap; §90/§91 already report that this install's map data is the weak part). Play App Signing cannot be reproduced locally.
- **Consequence** ⏳: `bound-tile-data-retention` tasks 5.1-5.5 (the walk ceiling, the platform-level release, the poll firing, the car-session scoping) cannot run on this phone without a map re-download; the AAOS AVD has the car side but no phone map path, and the walk protocol needs the phone surface.
- **Fix candidate**: decide with the owner — either a second sideloaded variant with its own `applicationId` for measurements (no data loss, maps re-downloaded once), or a dedicated sideloaded test device, or accept an uninstall+re-download for the measurement pass. The measurement itself is scripted already (`guidelines/Build.md` §10: keyevent/walk protocol + the high-water-mark rule).
- **Update 2026-09-27 20:27 — RESOLVED for the phone, with a residual** ✅: the owner published the memory work through Play (`2026-09-27-4`, versionCode **90**, `installerPackageName=com.android.vending`, `lastUpdateTime=2026-09-27 20:27:17`), so the phone now runs the new code — verified by pulling the installed `split_config.arm64_v8a.apk` and finding `OSMScoutClient_renderInto` in `libosmscout_client_java.so`, not by trusting the version string. Device verification therefore works again for **Play-released** builds; the residual is that a *local* build still cannot be installed over it, so an in-build A/B (e.g. one path reverted) still needs the uninstall/re-download or a second variant. Plan the next measurement pass around a release rather than around a sideload.
- **Two device facts from that pass worth keeping** ℹ: (1) `am send-trim-memory <pid> UI_HIDDEN` is *not* a valid level name — the shell's names are `HIDDEN`/`BACKGROUND`/`RUNNING_*`/`MODERATE`/`COMPLETE`, and the platform refuses `BACKGROUND` on a **foreground** process ("Unable to set a background trim level on a foreground process"); the reliable trigger is `HIDDEN` (works on the foreground app) or just pressing HOME, where the platform delivers `UI_HIDDEN` itself. (2) The app's `ActivityManager.MemoryInfo.lowMemory` poll produced **no** record across a 43-render walk and a long session on this chronically-pressured device, but a later pass the same evening caught it firing (`retention released: trigger=poll-low (avail=215MB threshold=216MB)`), so the poll is a late-but-real trigger on this hardware and the band was deliberately not widened. (1) is the trim-level recipe to reuse.

---

## 99. The AAOS AVD's basemap is a format version behind the submodule, so car-side device work on it cannot draw the basemap until it is re-downloaded — Found 2026-09-27 during the emulator A/B

- **Observed** ℹ: on `emulator-5554` (Automotive_Distant_Display, x86_64, SDK 33) the app opened its installed
  region (`openDatabase(nordrhein-westfalen-27-20260820-0826) -> true`) but the **basemap** failed:

  ```
  E NaviVeylin: File '…/files/maps/basemap/basemap/types.dat' does not have the expected format version!
                Actual 26, expected: 27
  W NaviVeylin: Cannot open db '…/files/maps/basemap/basemap'!
  ```

  So map rendering works (the region carries the tiles), but anything needing the basemap (sea/land background,
  borders, country names — and the phone-side "basemap present" paths) silently degrades on that AVD. The
  expected version moves with the libosmscout submodule, so this AVD's data predates a bump.
- **Consequence** ⏳: car-side device checks that involve the basemap (or compare against a phone that has one) are
  not comparable on this AVD until its basemap is re-downloaded through the app's map manager. Region-only checks
  (render counts, surface health, memory) are unaffected — the A/B in `reduce-render-peak-memory` used the region
  and was valid.
- **Fix candidate**: after any submodule bump that changes the data format, re-download the basemap (and ideally
  the region) on the AVDs that carry installed maps; note it in the `update-to-current-libosmscout-master` skill so the
  next person does not read the failure as an app defect.

---

## 94. `./gradlew test` also builds the native CMake target for both flavors and all three ABIs — after a submodule bump one invocation is a >45-minute run, and the aged daemon then OOMs the automotive dex merge — Found 2026-09-27 during `fix-diagnostics-coordinate-redaction` task 9.5 (harness)

- **Observed** ℹ: with the freshly merged libosmscout submodule (`cbbc66d`), a single `./gradlew test` ran `:app:configureCMakeDebug[arm64-v8a|armeabi-v7a|x86_64]` and `:app:buildCMakeDebug[…]` for the mobile flavor and again for the automotive flavor. The invocation exceeded a 45-minute tool window: the `:app` mobile suite (179 classes / 1278 tests, 0 failures) had completed and `:app` automotive had just started, while `:auto` and `:core` had already executed. Splitting the gate (`./gradlew :app:testAutomotiveDebugUnitTest :auto:testDebugUnitTest :core:testDebugUnitTest`) then finished in **1 m 58 s** (11 executed tasks) with the native build warm.
- **Second-order** ⚠: the daemon that had run the suite and both native builds afterwards failed `:app:mergeExtDexAutomotiveDebug` with `ERROR: D8: java.lang.OutOfMemoryError: Java heap space` (`DexArchiveMergerException`); `./gradlew --stop` followed by `:app:assembleAutomotiveDebug -Dorg.gradle.jvmargs=-Xmx4g` merged, dexed and packaged the same variant in **13 s**.
- **Consequence** ⏳: "one `./gradlew test` invocation" is not a dependable gate on this machine after a submodule bump — a session that starts it in one tool window loses the verdict (TODO §17: a log ending mid-run is not evidence) and can then hit the dex OOM while assembling. The product build itself is fine; the cost is the per-flavor native rebuild plus an aged daemon.
- **Fix candidate**: document the budget in `guidelines/Build.md` §6/§10 (after a submodule bump the suite carries the native build; prefer the per-module test tasks for the gate and quote per-module counts from `test-results/*.xml`), and stop/recreate the daemon before a flavor assemble when a long suite ran first. No product change.

---

## 93. The AA start crash of release `2026-09-27-2` has no root cause yet — only its confinement is fixed — Found 2026-09-27 (release 88, versionCode 88, built 08:47)

- **Observed** ℹ: Android Auto crashes directly after start on release `2026-09-27-2`. The release's **only** car/AA-path source change is `d1e94f2` (`one-navigation-engine`, 08:26 — `AutoServiceModule`, `NavigationViewModelModule`, `NavigationNotificationService`, `auto/NavigationSession`), whose own commit message records that its on-device AA checks (tasks 7.5/7.6) were "recorded as done without a device run"; the other change in the window (`09a0e3c`, 08:45) touches the phone renderer only, and everything after 08:47 is docs/archive plus the libosmscout submodule bump (which the release does **not** contain).
- **What is ruled out** ✅ so far: the release artifact itself is sound — `app-mobile-release.aab` carries all three ABIs for all eight native libraries (including `libosmscout_client_java.so` under its release name), the stylesheets and the license assets, `mapping.txt` shows the minified build keeps `OSMScoutClient`/`OSMScoutClientBuilder`/`NaviVeylinCarAppService` unrenamed with the native-method keep rule present, and the manifest carries the FGS types/permissions, the car-app service, `automotive_app_desc` and `minCarApiLevel`. My own AAOS runs today start the car session fine on a debug build of a *newer* tree.
- **Consequence** ⏳: the reported symptom (AA dies immediately) is unexplained. `fix-navigation-engine-fault-isolation` (this change) only guarantees that an engine fault is confined, recorded (`ENGINE_FAULT`, with the faulting coroutine and first stack frame) and raised as an engine error — so the next occurrence becomes diagnosable instead of invisible, and a fault of this family can no longer kill the process. It is **not** a root-cause fix.
- **Evidence still needed** ℹ: the crash stack from the phone (`adb logcat -b crash -d`, or Play Console → Crashes, or `adb shell dumpsys dropbox --print | grep -B5 -A40 naviveylin`) — it decides between (a) an unconfined engine fault, (b) the car-only engine resolution introduced by `one-navigation-engine` (the warmup now resolves the phone Hilt graph), (c) a phone-side rendering regression from `09a0e3c`, and (d) a host/template constraint on projection. Also useful: does the phone UI start normally, and does the previous release (versionCode 87) run AA fine?
- **Fix candidate / follow-up**: re-run the archived `one-navigation-engine` device checks (phone start/stop/reroute; car-only deep-link start + connect/disconnect mid-navigation) plus a **release-variant** AA smoke on a real phone + head unit before the next release, and re-open or supersede that change if the stack points into it. Consider a release gate: build the minified variant for any AA-path change and run the `guidelines/Build.md` §10 recipe against it, because a device run on a debug build does not cover R8.

---

## 92. The car free-driving surface can stay blank for ~30 s after the screen opens — Found 2026-09-27 on `emulator-5554` (AAOS) during the `compass-day-night-palette` on-device pass (low confidence: 1 of 4 runs)

- **Observed** ℹ: when the free-driving screen was pushed (`Freie Fahrt`) right after a session that had toggled the host day/night signal twice, the map surface showed **no frame at all for ≈31 s**: `FreeDrivingScreen: Free driving renderer ready` at 11:36:38 and `AutoMapRenderer: renderer#1 surface created` immediately after, but the first `Diag/MAP: render center=…` only at **11:37:07**; the screenshot at 11:36:52 was uniform `#040505` (black surface, host chrome still drawn), and the log's last event before the frame was a second layout pass (`FreeDrivingScreen: visible area Rect(9, 66 - 1071, 519) -> pillTopInset=66` at 11:36:51). Four later runs on the same build rendered the first free-driving frame within **≤4 s** (content already visible in the first sample), so the delay did not reproduce and is not attributed to `compass-day-night-palette`.
- **Consequence** ⏳: if a driver taps `Freie Fahrt` at a moment the host is re-composing (e.g. after a day/night switch), the map can stay black for tens of seconds with only the template chrome visible — no error, no spinner, nothing to tell the driver whether the app is working. The same blank window was also seen on one day/night switch sample (`fd-day2` after the host re-delivered the surface), then not again on the sampled switch run.
- **Fix candidate**: commit a first frame as soon as the renderer has a usable surface + a viewport (the renderer currently waits for the next fix/layout change), and/or make the free-driving screen show its last frame or a placeholder while no frame has landed. Verify with a repeat of the measurement (`screencap` + `Diag/MAP: render center` per 5 s for 60 s after the push), and with the host deliberately re-composing (`cmd uimode night yes/no`) before the push.

---

## 91. POI name search finds nothing for objects the category index returns — this install's name index does not carry its POIs — Found 2026-09-27 on `emulator-5554` (AAOS) during the `fix-compound-name-matching` car pass (data side, sibling of §89)

- **Observed** ℹ: on the car, `POIs suchen → Restaurants` lists **`Mensa`** (`amenity_restaurant - 600` m), but the free-text search for that exact name answers `D SearchScreen: Search results: 0 for 'Mensa'`. The same asymmetry explains §89's `Hilpert` result: the object can exist in the *category* (location index) while the *name* query (text index) finds nothing. One startup also emits **3078** `W NaviVeylin: Unknown type '…'` warnings, i.e. the stylesheet declares far more types than the installed database's typeconfig carries.
- **Consequence** ⏳: any on-device check that searches a POI by name (the `fix-compound-name-matching` 5.4/5.5 positive cases, `Theater Dortmund`, `Hilpert Theater Lünen`) is blocked by the install, not by the matcher; a name search that returns 0 for an object the category list shows is also a user-visible inconsistency (the app offers a POI it then cannot find by name).
- **Fix candidate**: same as §89 — rebuild/re-download the map set from a typeconfig that matches the shipped stylesheet (which should fill both indexes); and add the small startup diagnostic §89 proposes (compare the stylesheet's declared types against the database's typeconfig once and report the missing set) so this is visible as a data report instead of a wall of per-render warnings.

---

## 90. The installed basemap package is rejected — `types.dat` format 26 vs the library's 27 — the basemap overlay never opens — Found 2026-09-27 on `emulator-5554` (AAOS) (data side, out of every change's scope)

- **Observed** ℹ: every start logs the download layer finding the basemap (`D MapDownloadModule: basemap found at /data/user/10/com.framstag.naviveylin/files/maps/basemap`, then `found valid map 'basemap' …` for **both** `…/maps/basemap` and `…/maps/basemap/basemap` — the package carries a nested directory), followed by `E NaviVeylin: File '…/maps/basemap/types.dat' does not have the expected format version! Actual 26, expected: 27`, `E NaviVeylin: Cannot load 'types.dat'!` and `W NaviVeylin: Cannot open db '…/maps/basemap/basemap'!`. The region database opens normally (`openDatabases -> 1/1 registered`, `openDatabase(nordrhein-westfalen-27-20260820-0826) -> true`), so `Installed map databases: 1` and every render runs **without the basemap overlay**.
- **Consequence** ⏳: after the recent libosmscout data-format bump (expected 27) the installed basemap (26) can never be used; the map therefore lacks the basemap's coverage/context outside the regional tiles, and the failure is silent to the user (only Logcat). It also means any basemap-dependent check (basemap rendering, download/replace flows on this install) is invalid until the data is refreshed.
- **Fix candidate**: re-download the basemap package on the device (or ship a data-version-aware download that detects the mismatch and re-fetches); optionally surface a diagnostics entry when a known package is rejected for a format reason, instead of a per-start `E` line. The nested `maps/basemap/basemap` layout is worth confirming against the downloader's expected layout at the same time.

---

## 89. The installed map set cannot answer the compound-name search check — the loaded database's type config lacks POI types the stylesheet declares — Found 2026-09-27 on `emulator-5554` during `fix-compound-name-matching` (data/import side, out of that change's scope)

- **Observed** ℹ: `Hilpert Theater Lünen` returns the town `Lünen` (12 km) and nothing else, and `Hilpert` alone returns **0 candidates** (`searchLocations: query='Hilpert', adminRegionHandle=1, candidates=0`), although the change's proposal names the POI (`Heinz-Hilpert-Theater Lünen`, OSM way 38028287) as the case it fixes. The renderer explains why: every render logs `W NaviVeylin: Unknown type 'amenity_theatre'` (and `amenity_cinema`, `amenity_fire_station`, `amenity_parking`, …), i.e. the **loaded database's type config does not carry those types**, so their objects were never imported and no matcher can find them. `Theater Dortmund` likewise returns only streets/garages (`Theatergarage`, `Theaterkarree`), never a theatre.
- **Consequence** ⏳: the phone half of `fix-compound-name-matching` task 5.4 is blocked by data, not by code (recorded in that task), and any future on-device search check that targets POIs must first confirm the type is in the installed data. `Erbstollenstraße 10 58454 Witten` (perfect match + house levels) and `Erbstollenstrasse` (transliteration → perfect match) do verify on the same install — road/street types are present.
- **Fix candidate**: rebuild or re-download the map set with a type config that matches the shipped stylesheet (the stylesheet is the single source of truth for which types are `ADDRESS POI`), then re-run task 5.4. Worth a small app-side diagnostic instead of a per-render `W` line: compare the stylesheet's declared types against the database's type config once at open and report the missing set (a missing type is a data question, but the user/developer sees only a wall of warnings today).
- **Adjacent observation** ℹ: on a device with several installed maps, `NavGraph.kt` opens `installed.first()` — the first directory the filesystem lists — as the primary database (here `iceland`, which is what `viewport-iceland.json` and the diagnostics `map=iceland` lines name, while the Dortmund data actually comes from the additional databases). There is no persisted "last used / default map" for the phone's first open; the map manager is the only way to choose deliberately. Same family as the per-surface anchor split (`autoRoutingAnchorId`) and probably its own change.
- **Fix in flight** ⏳ (2026-10-02): `fix-start-map-selection` gives the phone one shared discovery for its start map (`StartMapResolver` + the pure `StartMapSelection` rule in `:core`), records the opened database in `AppSettings.lastMapPath` from `MapCanvasViewModel.initMap` (normalized to the database directory, so a container-root open still matches) and falls back to the lexicographically smallest installed database when the recorded one is gone. Specs: new `start-map-selection`, modified `map-download-infrastructure` ("Installed maps discovered at startup" now binds every caller to the one rule). Verification: rule 8/0, resolver 8/0 (`--tests` runs, three revert-checks quoted in its tasks), recording 5/0, automotive `:app` 196 of 197 classes / 1471 tests / 0 failures, both flavors built with all three ABIs; the phone recipe is in `guidelines/Build.md` §10 ("Start-map selection on the phone") and its on-device half is device-gated (`adb devices` empty, 2026-10-02). This entry is removed when that change is archived.

---

## 117. The `:app` unit-test suites stop producing a verdict: a pre-existing hang in `MapCanvasViewModelViewportRestoreTest` — Found 2026-10-02 while verifying `fix-start-map-selection` (verification gate, not caused by that change)

- **Observed** ℹ: with the change's code in the tree, `./gradlew :app:testAutomotiveDebugUnitTest` ran **more than 20 minutes** without writing one result XML, and a later `./gradlew --no-build-cache :app:testMobileDebugUnitTest` did the same (core finished, 38 classes, the app task never produced a file). The same suites are green as soon as that one class is left out: automotive **196 of 197 classes / 1471 tests / 0 failures in 156 s** (88 `com.naviveylin.ui.map.*` patterns plus the remaining packages enumerated), and the mobile flavor passed **1483/0** in this session before the machine turned.
- **Where it hangs** ℹ: `jstack` on the test worker puts the test thread in `runTest` → `runBlocking` → `MapCanvasViewModelViewportRestoreTest.saveViewport during re-entry window keeps persisted viewport (MapCanvasViewModelViewportRestoreTest.kt:263)`, with Robolectric's `SDK 36 Main Thread` holding ~30 s CPU and `Sandbox.runOnMainThread` on the worker's stack: the test coroutine never completes while the main looper keeps being driven. The class reproduces it alone (automotive, 240 s timeout, no XML; a partial XML records that one case at **79.8 s**). Other classes of the same package run at mobile speed (`MapCanvasViewModelSpeedWidgetTest` 1.05 s, `MiniMapComposeTest` 0.37 s), so it is this class, not the module or the flavor.
- **Not this change** ✅: the same bounded run hangs **with the start-map recording write disabled** (`MapCanvasViewModel.initMap`, the only init-path edit of `fix-start-map-selection`), so the hang is independent of it. The class is already a recorded victim of the flake family documented below (`MapCanvasViewModelViewportRestoreTest`'s NPE tail; the 2026-09-30 automotive `Dispatchers.Main` race) — this is that family's symptom turned into a stall.
- **Consequence** ⏳: every change whose gate is “the `:app` suites are green” loses its verdict on this machine. Working around it: enumerate the classes (all `*Test.kt` under `app/src/test/java`, `--tests <fqn>` each) minus this one, and quote the class count so the omission is visible; `--no-build-cache` is needed to keep a mobile run from being answered by a cached verdict (`TODO.md` §17).
- **Fix candidate**: the class drives `runTest` against a dispatcher that holds its first dispatch (`FirstDispatchGatedDispatcher`, `MapCanvasViewModelViewportRestoreTest.kt:432-453`) and pumps the scheduler around a real-time `Thread.sleep(100)` (`:240-247`); the held block runs only on `release()`, so any ordering change in the initialization can leave a coroutine parked while the looper spins. Replace the gate with `StandardTestDispatcher` ordering (or a `CompletableDeferred` the test completes deterministically) instead of the hold-and-sleep loop, then re-check the whole §101 family against it.
- **Fixed** ✅ by `fix-viewport-restore-test-hang` (2026-10-02): `FirstDispatchGatedDispatcher` records that it has been released and runs every later dispatch inline, so no coroutine can be queued behind a gate that has no drainer left — that was the stall (nothing to release, `runTest`'s timeout unable to fire while the main thread spins). The four cases that assumed a scheduling point now wait for the observable state through a bounded `awaitHeldBlock` that fails with a reason, and the two milder real-time assumptions in the same class are gone (the first-render wait had a 5 s deadline the render reaches at ~5 s under load; the save case dereferenced a file the save had written only once the restore had applied). Verdicts are back: the class **6/6 in both flavors (~27 s)** and the whole gate green in one run — `:core` 437/0 · `:app` mobile 1483/0 · `:app` automotive 1483/0 · `:auto` 754/0 (4157 tests, `BUILD SUCCESSFUL in 5m 31s`, `--no-build-cache`). Falsifying power kept by two revert-checks: remove `initJob?.cancel()` → 1 failure; remove the save guard → 3 failures (51 s / 36 s, both bounded). **Still open**: the §101 load-sensitive family and the two re-entry cases' dispatch-ordinal gates (a state-keyed gate, per `PostInitializationGatedDispatcher`'s KDoc, is the follow-up). This entry is removed when `fix-viewport-restore-test-hang` is archived.

---

## 84. Two surfaces in one process shared their process-global resources without an owner rule — FIXED by `shared-resource-arbitration` (2026-09-26); device verification pending

- **Fixed** ✅ by change `shared-resource-arbitration` (specs: `location-updates-lease`, `settings-persistence`, `native-tile-data-cache`, `car-session-presence`):
  - `LocationService` now hands out named leases (`acquire(consumer)` → `LocationLease`, consumers `phone-map`/`phone-nav`/`car-session`/`car-nav`); updates run while at least one is held and stop with the last release, so one surface's stop can no longer kill another's fixes. The bare `start/stopLocationUpdates` pair is gone. The engine call sites hold their lease only while a navigation attempt/session is active (the old `init` starts are gone), which is the semantics `one-navigation-engine` targets. Tests: `LocationServiceTest` 19/0 (6 lease cases incl. idempotent acquire, one-release-keeps-the-other, last-release-stops, diagnostics naming).
  - `SettingsStorage.update { }` serializes every read-modify-write under one lock (and `load()` takes the same lock so a read cannot see a half-written file); all 13 phone/car writers were converted. Revert-checked: with the lock removed, `concurrentWritersLoseNoUpdate` (barrier-forced interleave) fails. Tests: `SettingsStorageTest` 18/0.
  - `NativeTileDataCache` keeps the **highest** requested capacity and never lowers it, so car-first ordering no longer degrades the phone to 128 tiles/db; a car-only process keeps the car value. Revert-checked: 3 cases fail with first-writer-wins. Tests: `NativeTileDataCacheTest` 11/0.
  - New process-scoped `CarSessionPresence` (in-memory only) published by the car session on start/destroy; the phone shows an advisory pill (`car_session_active_indicator`, de+en) and disables nothing. Tests: `CarSessionPresenceImplTest` 5/0, `CarSessionIndicatorComposeTest` 2/0, German completeness green.
- **Resolves** ✅ the deferred item in `fix-client-dpi-surface-leak` (proposal decision 3): the phone `ON_PAUSE` and the car `stop()` started/stopped one non-refcounted subscription, so the later call won. The lease is that arbitration.
- **Still open on device** ⏳ (tasks 6.5/6.6 of the change): pause the phone during a car drive and confirm car fixes continue; end the car session and confirm phone fixes continue; a car and a phone settings change in the same minute must both persist; `adb logcat -s NaviVeylin` should show `location lease acquire/release: <consumer> (held=N)` and the raised cache value; on the AAOS AVD confirm the car-only process keeps the car capacity and the presence signal has no phone-side effect. Recipe in `guidelines/Build.md` §10.
- **Verification gap** ⏳: no `:auto` test constructs `NavigationSession` (needs a `CarContext` + Hilt entry point), so the two presence publish sites are compile/inspection-verified plus seam-tested; and placement of the phone pill (centre-left) is a UI-review decision open to change without touching the seam.
- **Not addressed here** ℹ: the `CAR_TILES = 128` measurement is still reasoned, not measured — the policy change does not alter the car-only value. The recipe lives in §65, the tile-cache entry this one closed on 2026-09-27.

---

## 83. The rail-widget tap fix is unit-verified but not confirmed on device — Found 2026-09-26 (reported on phone + head unit under Android Auto) and implemented by `fix-car-rail-widget-tap` (2026-09-26)

- **Reported** ℹ: while navigating, switching to another car app keeps the turn hint in the rail widget, but tapping it does nothing — the app never comes back to the car foreground (music players do). Phone + head unit under Android Auto (projection).
- **Cause** ✅: the ongoing notification had only one tap target — `PendingIntent.getActivity` at `openTargetActivity()`, which returned `CarAppActivity` only on automotive hardware and `MainActivity` otherwise, while `CarAppExtender` carried no `setContentIntent` at all. Per the car-app contract the host then falls back to the notification's content intent, so on projection the tap started a phone activity on the phone and the car screen never switched.
- **Fixed** ✅: `NavigationNotificationBuilder.carExtender` now sets a car tap target, built by `NavigationNotificationService.carOpenIntent` as `CarPendingIntent.getCarApp(…, CarAppService component)` — projection: a broadcast the host answers with `startCarApp`; AAOS: the `CarAppActivity` launch (the hand-built AAOS branch is gone, one car path for both). Unit evidence: `NavigationNotificationBuilderTest` 13/0 (4 new cases: car target present and distinct, phone target unchanged, absent without a caller target), `NavigationNotificationServiceTapTargetTest` 2/0 (projection broadcast, AAOS activity via `FEATURE_AUTOMOTIVE`), full suite 3453/0 (`:app` mobile 1191, `:app` automotive 1191, `:auto` 691, `:core` 354, JNI module 26).
- **Still open on device** ⏳: no car/AAOS target was attached at implementation time (`adb devices` empty), so both on-device checks of `fix-car-rail-widget-tap` (tasks 4.2/4.3) are pending. Recipe: phone projecting to a head unit, start navigation, switch to another car app, tap the rail widget → the car screen returns to the app with guidance still running, `adb logcat -s Diag/SESSION Diag/HOST` shows the session handling the incoming intent (`action=android.intent.action.VIEW`) with no `HOST` rejection, and the phone-shade tap still opens the phone UI; then the same tap on an AAOS AVD/head unit (automotive flavor) for the migrated path. Re-close this entry when the runs are recorded in the change's tasks.

---
## 82. The native search-scope diagnostics were dropped with the branch's last diff — Found 2026-09-26 during the libosmscout merge / PR #1773 closure (`update-to-current-libosmscout-master`)

- **Observed** ℹ: `naviveylin-local`'s last difference from upstream `master` was `libosmscout-client-java/src/OSMScoutClient.cpp` (+35/-7) and consisted of 13 `osmscout::log.Info()` diagnostic lines (ResolveSearchScope parent chain + expansion, the `searchLocations: scope for db has …` line, resolveAdminRegion db/bbox/service/reverse-lookup, getAdminRegionScopeName) plus a behaviour-identical restructure of the multi-database scope selection. It was dropped (submodule `a50ae3b15`, parent gitlink `4ae2c13`), so the branch now equals upstream `master` and PR #1773 was closed as superseded (empty diff).
- **Consequence for an existing task** ⏳: `fix-compound-name-matching` task 5.4 asks the on-device run to grep `adb logcat -s NaviVeylin` for the native `searchLocations: scope for db has …` line. That line no longer exists in the pinned submodule; the surviving evidence for the same decision is the Kotlin side (`MapCanvasViewModel` logs `resolveAdminRegion(...) -> handle=…` and `getAdminRegionScopeName(handle=…) -> '…'`, both through `android.util.Log`, not the `NaviVeylin` tag). Update that task's recipe before the device run, or accept the Kotlin-side logs as the evidence.
- **Fix candidate if the detail is wanted again** ℹ: add the per-database scope lines as `osmscout::log.Debug()` (gated, so a release build stays quiet) in a focused upstream PR — not by re-forking the file; the `log.Debug(true)` switch is documented in `AGENTS.md` (native logging) and the app's bridge forwards them to the `NaviVeylin` tag.
- **Related** ℹ: `TODO.md` §81 records the same pattern for the matcher test's allocation-cost case; both are "the merge resolved a file to upstream and a local diagnostic went with it".

---
## 81. The word-matching allocation-cost case did not travel upstream with the matcher work — Found 2026-09-26 during the libosmscout merge of PR #1849 (`update-to-current-libosmscout-master`)

- **Observed** ℹ: PR #1849 merged our `fix-compound-name-matching` matcher work into upstream `master` (`aaa437a16`, commits `7a036fa51` "feat: match query words across separators in names" + `197a5c05c` "fix: make the new matcher tests portable and non-duplicated"). Upstream's `Tests/src/StringMatcherTest.cpp` carries the same case set as our `naviveylin-local` copy, but **not** the allocation-cost case and its counter harness: our copy is a 331-line fork of upstream's 189-line file, and the only content unique to ours is `TEST_CASE("A substring hit does not split the candidate into words")` plus the `STRINGMATCHER_HAVE_ALLOCATION_COUNTER` block that replaces global `operator new`/`delete` to count allocations (so a substring hit must not pay for splitting the candidate into words).
- **Why it is listed** ℹ: the merge resolved that file to upstream to stop it conflicting on every update (`git diff origin/master` for the file is now empty, and the branch's residual dropped from 14 files / +1042 -18 to 1 file / +35 -7). The cost claim is therefore no longer pinned anywhere: `git grep CountAllocations origin/master -- Tests/` returns nothing.
- **Preserved** ✅ at submodule commit `eb0ac0ff2` ("tests: measure the StringMatcher cost claim only where it is measurable"), reachable in the submodule's history; restore locally with `git -C app/src/main/cpp/libosmscout checkout eb0ac0ff2 -- Tests/src/StringMatcherTest.cpp`. The harness needs care in CI (the local version already had `SKIP` paths for sanitizer runtimes and shared-library/DLL builds, which is the likely reason it was not part of the PR).
- **Fix candidate**: re-add it as a focused upstream PR on top of upstream's file (the counter harness + one case), so the cost claim is pinned where the matcher now lives; do not re-fork the whole test file.
- **Adjacent state from the same merge** ℹ: upstream **deleted** `origin/fix-compound-name-matching` in the submodule after merging it, and the submodule's `openspec/changes/fix-compound-name-matching/` artifacts are now upstream's copies (our local stubs were replaced). The parent repo's own copy of that change (16/20, device checks 5.2/5.4-5.6 pending) remains the tracker; when it archives, it must not resurrect the submodule stubs.

---
## 79. The per-module unit-test task names in the run-tests skill are wrong for `:auto` and `:app` — Found 2026-09-25 during `fix-comma-decimal-coordinate-entry` (harness)

- **Observed** ℹ: `./gradlew :auto:test --tests "com.naviveylin.auto.DetailsScreenTest"` fails immediately with `Problem configuring task :auto:test from command line. > Unknown command-line option '--tests'` — `:auto:test` is not filterable (it is not the AGP unit-test task). The working invocation is `./gradlew :auto:testDebugUnitTest --tests "<fqcn>"`. `:core:test` accepts `--tests` (verified with the same change). The `.pi/skills/run-tests` table documents `:app:testDebugUnitTest` (which does not exist — the `dist` flavor dimension splits it, §40 item 30 / §71) and lists no `:auto` task at all.
- **Consequence** ℹ: a single-class run against `:auto` looks like a real build failure and costs a build cycle to diagnose; the skill is gitignored, so the fix has to happen on the machine that owns `.pi/skills/` (not in this repo).
- **Fix candidate**: correct the run-tests table to the flavor/task matrix that actually exists (`:app:testMobileDebugUnitTest`, `:app:testAutomotiveDebugUnitTest`, `:auto:testDebugUnitTest`, `:core:test`), or add a filterable aggregate task per module.
- **Evidence for the working names (2026-09-25)**: `:auto:testDebugUnitTest --tests com.naviveylin.auto.DetailsScreenTest --tests com.naviveylin.auto.NavigationTemplateMapperTest` → BUILD SUCCESSFUL, `DetailsScreenTest` 28 tests / 0 failures, `NavigationTemplateMapperTest` 59 / 0; `:core:test` 339 / 0; `./gradlew test` app mobile 1174 / automotive 1174 / auto 683 / core 339, all 0 failures.
- **Correction 2026-09-26** (found while applying `fix-diagnostics-coordinate-redaction`): `:core:test` does **not** accept `--tests` either — `./gradlew :core:test --tests "com.naviveylin.core.LocationGrantTest"` fails with `Problem configuring task :core:test from command line. > Unknown command-line option '--tests'`; the filterable task is `:core:testDebugUnitTest`. So the corrected matrix is `:app:testMobileDebugUnitTest`, `:app:testAutomotiveDebugUnitTest`, `:auto:testDebugUnitTest`, `:core:testDebugUnitTest` (and `:osmscout-client-java:test`).

---

## 77. Words joined without a separator stay unmatched (`Bahnhof Straße` vs `Bahnhofstraße`) — Found 2026-09-25 during `fix-compound-name-matching` (out of scope, boundary of the word rule)

- **Observed** ℹ: `StringMatcherTransliterateToken` matches whole words across separators (space, hyphen, slash,
  dash), so a name that joins two words with *no* separator still hides from a query that spells them apart —
  `Bahnhofstraße` is one word for the matcher, so `Bahnhof Straße` does not match it (the native test
  "Words joined without a separator do not match" pins this deliberately).
- **Fix candidate**: a word-internal split rule (compare the query's word run against the *concatenation* of a
  candidate's words) — a broader tolerance that needs its own boundary analysis (risk of false positives, e.g.
  `Ost Straße` matching `Oststraße` in a different street than the user meant) and therefore a spec scenario for
  each direction.

---

## 76. The free-text index keys whole names, so a mid-name word is unreachable without a region token — Found 2026-09-25 during `fix-compound-name-matching` (out of scope, index format)

- **Observed** ℹ: `TextSearchIndex` is a MARISA trie whose keys are complete normalized names, looked up with
  `predictive_search` (a prefix search), so a query matching a word in the *middle* of a name only works through
  the structured search, which needs a region or a default admin region. `Heinz` finds "Heinz-Hilpert-Theater"
  through the text index; `Theater` alone cannot.
- **Fix candidate**: token-keyed text index entries (one key per word plus the object reference) — an import-side
  change, so every installed map has to be re-imported server-side and re-downloaded before users benefit; the
  query side would then intersect the per-word candidate sets instead of prefix-matching one name.

---

## 75. Android Auto search passes no default admin region, so POI search needs the city in the query — Found 2026-09-25 during `fix-compound-name-matching` (out of scope, own change)

- **Observed** ℹ: `provideAutoSearchProvider` calls `searchLocations(query, limit, OSMScoutClient.NO_ADMIN_REGION)`,
  and the native structured search visits POIs only inside an admin region matched from the query tokens
  (`LocationService::SearchForLocationByString`). A car query naming only a POI (`Hilpert Theater`, without
  `Lünen`) therefore finds nothing, while the phone searches inside the GPS-derived default region
  (spec: `location-search` — "Search scoped by current admin region").
- **Fix candidate**: give the car search the same default-region treatment (resolve the region from the last car
  GPS fix or the map viewport center), or add a bounded region-less POI pass; both need a cost check against the
  300 ms search debounce, since a region-less pass walks the POI index of the whole database.
- **Fix in flight** ⏳ (2026-10-01): `fix-car-search-default-admin-region` resolves the region for the car search
  through one shared rule (`core/src/main/java/com/naviveylin/core/search/SearchRegionScope.kt`) that the phone's
  `MapCanvasViewModel.searchAdminRegionHandleForFix` now delegates to as well, and passes that handle from
  `AutoServiceModule.provideAutoSearchProvider` instead of `NO_ADMIN_REGION` (car adapter
  `app/src/main/java/com/naviveylin/di/CarSearchRegionSource.kt`). Design decision: the region comes from the
  position fix only, never the map viewport center, so the car scopes exactly like the phone — which leaves
  §34 (car search from the root/history screens has no distance reference) open as the sibling change that would
  introduce the shared car reference the viewport-center fallback needs. This entry is removed when that change
  is archived.
- **Verification** ⏳: unit level green and recorded in the change's `design.md` — shared rule 14/0 with a
  revert-check, the phone's 20 region cases green **unmodified**, car wiring 6/0 with a revert-check, both flavors
  1462/0/0, `:auto` 713/0/0, both debug APKs with all three ABIs. The on-device halves (a car query naming a POI
  without its region, the phone regression) are device-gated: no device was attached on 2026-10-01 (`adb devices`
  empty), and the POI-only positive case additionally waits on the §89/§91 data gap in the installed map sets.
  Recipe with that caveat: `guidelines/Build.md` §10 ("Car search scoped by the driver's region").

---

## 74. The debug app ANRs on a cold emulator start with no map data, and the ANR trace is unreadable without root — Found 2026-09-24 during `compass-day-night-palette` task 7.3 (verification blocker, unverified)

- **Observed** ℹ: on a fresh `Pixel_8` AVD (API 34, `-no-window -gpu swiftshader_indirect`), the freshly installed
  `mobileDebug` APK starts, its native renderer initialises (stylesheet loaded, the usual empty-data
  "Unknown type …" warnings), then the app shows `Application Not Responding: com.framstag.naviveylin` and the map
  screen with its overlay column never appears — a screenshot of the running process contains none of the six
  compass palette fills, so the on-device half of that change could not be verified.
- **Not diagnosed** ✗: `adb root` does not take on this image (shell stays uid 2000) and `/data/anr/*` is
  permission-denied, so the main-thread stack is unavailable; the `am_anr` event was already rotated out of the
  events buffer. Whether this is an emulator/no-data artifact or a product defect is therefore unknown.
- **Fix candidate**: reproduce with a basemap installed and a writable trace (`adb shell setprop` or a `userdebug`
  image), and compare against the previous release build on the same AVD to separate a real regression from the
  environment. Until then treat any on-device colour/lifecycle verification of that change as outstanding.

---

## 73. Two phone overlays still use fixed colors with no presentation branch — Found 2026-09-24 during `compass-day-night-palette` (out of scope, own change)

- **Observed** ℹ: `app/src/main/java/com/naviveylin/ui/map/MiniMap.kt:91` (`gpsMarkerColor = Color(0xFF1A73E8)`) and
  `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt:241-242` (accuracy fill 10 % / border 40 % of
  `#4A90D9`) are literals with no presentation branch, while the vehicle marker right next to them branches through
  `core/VehicleMarkerGeometry`. The compass was the only status-carrying overlay, so `compass-day-night-palette`
  fixed that one and left these alone.
- **Duplication** ℹ: the mini-map's marker blue repeats the vehicle marker's day blue — a single-source-of-truth
  violation (`guidelines/Design.md` §12) that also means a palette change has to be made twice.
- **Not caught** ✗: no spec scenario or test covers overlay colors on the mini map or the accuracy circle in dark
  presentation.
- **Fix candidate**: route both through the established palette-branch convention (the marker geometry object for the
  marker, a small palette function for the accuracy ring) and add the dark case to whichever spec owns them.

---

## 72. The compass needle is stroked in raw pixels, so it thins on high-density screens — Found 2026-09-24 during `compass-day-night-palette` (out of scope, sizing/geometry)

- **Observed** ℹ: `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt` draws both needle halves with
  `strokeWidth = 3f` — device pixels, not dp — while every other dimension in the same file is density-aware
  (`needleLength = 10.dp.toPx()`, rim `1.dp.toPx()`, canvas 48.dp). On a 3.5× density screen the needle is ~0.86 dp
  wide, i.e. visibly thinner than on a 1× screen.
- **Not caught** ✗: no test asserts the needle's stroke width; `CompassButtonComposeTest` only pins the 56 dp layout
  and `CompassPaletteTest` only the colors, so a density regression is invisible to the suite.
- **Fix candidate**: use `3.dp.toPx()` (or a named dp constant) and pin it with a unit-tested geometry helper, the way
  the phone palette now pins contrast instead of leaving it to review.

---
## 71. Zoom-walk follow-ups from `fix-phone-zoom-animation-parity` — Found 2026-09-24 during that change (out of scope / pending device)

- **On-device cost unmeasured** ⏳: the phone now walks a magnification change the frame in hand cannot serve
  (`core/ZoomWalk`, `window = 0.25` levels, one step per landed render, `MapRenderer.requestRenderImmediate`).
  A four-level entry is ~16 renders. The car's measurement (1.5-4 s for the same 16 steps) does not transfer: the
  phone's tile path serves fractional steps from the per-level tile cache, so most steps are cheap and only the
  level crossings render natively - which the on-device task (change tasks 6.3) has to confirm before anyone
  considers design D7 Alt C (a larger `OVERRUN_FACTOR`, i.e. a bigger window and fewer steps) or D1 Alt B
  (clamp the display scale and land the remainder in one render).
- **Pinch commits now walk when their gap exceeds the window** ℹ: `updateMagnification` is the commit path for
  pinch, zoom buttons, keyboard shortcuts and the scroll wheel (only the POI camera fit passes `walk = false`),
  so any of them with a gap above 0.25 levels renders stepped frames instead of one. The spec's requirement is
  unconditional ("a change larger than the window SHALL be applied as a sequence of displayed steps"), and the
  walk's steps are cheap via the tile cache - but a manual large zoom-in (e.g. several wheel ticks) is a visual
  judgement that needs a device, not a unit test.
- **`:app` has no aggregate unit-test task** ℹ: `:app:testDebugUnitTest` does not exist (the `dist` flavor dimension
  splits it into `testMobileDebugUnitTest` / `testAutomotiveDebugUnitTest`), so the run-tests skill's documented
  command fails with "Ambiguous matches". Use the flavor-specific task, or add a `test` aggregate. Not touched here
  because the skill file lives in `.pi/skills/` (gitignored).
- **Before this change no test could obtain a rendered front buffer** ℹ: every ViewModel test was state-level because
  `initMap` + `advanceUntilIdle` never produced an emitted frame (`renderViewport` stayed null; not investigated
  further). The change added the `MapCanvasViewModel.publishRenderedFrameForTest` hook (the same path the frame
  collector uses) so the walk is deterministically testable; a real-render harness for the ViewModel is still worth
  having for render-pipeline behaviour (why no frame is emitted is unexplained).

---

## 70. BROWSE still mixes the free-driving anchor preset into a street-pill placement, and an off-screen vehicle has no cue but the re-center button — Found 2026-09-22 during `fix-browse-recenter-visibility` (out of scope)

- **Observed** ℹ: (a) `MapCanvasScreen.kt` derives `pillAtTop = state.activeFollowAnchor.fy == 0.9` and moves the
  free-driving street pill to the top of the screen "so it never covers the vehicle marker" — in BROWSE too, using the
  **free-driving** anchor preset; browse framing no longer applies anchor presets at all (spec `map-modes` — Browse
  re-center), so the pill can sit at the top in browse for a preset that browse does not use. Cosmetic only; the pill
  and the button do not overlap.
- **Observed** ℹ: (b) when the vehicle is outside the viewport the marker overlay draws nothing (`projectMarker` returns
  null past `MARGIN_PX`, `LocationMarkerOverlay.kt`), so in BROWSE the derived re-center button is the only signal that
  a position exists. No edge arrow, no direction indicator.
- **Why it was left alone** ℹ: found while replacing the sticky `browseDrifted` flag; (a) is a placement question this
  change did not need to touch and a wrong fix would move the pill for the driving modes too, (b) is a new affordance
  with its own questions (where to draw it, how it clears the action/widget columns on phone and foldable, phone/AA
  parity).
- **Fix candidate**: (a) derive `pillAtTop` from the mode — in BROWSE the vehicle sits at the centre, so keep the pill
  bottom-centre (or key it off the centre preset) and leave the anchor-based rule to FREE_DRIVE/NAVIGATION; (b) a small
  viewport-edge indicator pointing at the vehicle, driven by the same projected offset the re-center rule already
  computes, with a `map-modes` spec delta and a phone/AA parity decision.

---

## 66. `hostbuild`'s JNI target is configured against a JDK that is not installed — Found 2026-09-21 during `fix-native-database-open-race` task 1.1 (harness gap)

- **Observed** ℹ: `ninja -C hostbuild libosmscout-client-java/src/libosmscout_client_java.so.1.1.1`
  fails with `fatal error: jni.h: Datei oder Verzeichnis nicht gefunden`; the compile line carries
  `-I/usr/lib/jvm/java-26-openjdk/include`, and this machine has java-11/17/21/27 (no 26). So the
  native JNI translation unit cannot be compiled through the host build, while the rest of
  `hostbuild` (libraries, tests) works.
- **Consequence** ℹ: a JNI-only change can only be checked by the Android build (slow, needs the NDK
  toolchain) or by a hand-rolled compile. Workaround used by the change: the meson compile line with
  `-I/usr/lib/jvm/java-21-openjdk/include ...` plus `-fsyntax-only`, which builds the whole TU in ~4 s
  with `-Wall -Wextra -Wpedantic`.
- **Fix candidate**: re-configure `hostbuild` against an installed JDK (or pin the Java path via
  `local.properties`/`JAVA_HOME` in the setup script) so an incremental JNI compile is available
  locally. Not a product defect — a developer-harness gap.
- **Adjacent** ℹ: no CMake host build directory exists on this machine (only the meson `hostbuild`),
  so a `ctest`-based native test run cannot be exercised locally even though `Tests/CMakeLists.txt`
  registers the tests (see `ki_processing_failures.log`, 2026-09-21).

## 65. Both processes grow by ~50 MB over a 10-minute car drive — Found 2026-09-21 during `fix-host-crash-residual-paths` task 8.2 (post-change baseline)

- **Observed** ℹ: on the AAOS AVD (`emulator-5556`, automotive debug, navigation active with a fix every 2 s for 10.5 minutes) `dumpsys meminfo` reports the app `TOTAL 227 MB -> 278 MB` (native heap `133 -> 187 MB`) and the templates host `TOTAL 299 -> 343 MB` (native heap `162 -> 215 MB`). No pressure symptoms in the same window: 0 `lmkd`/lowmemorykiller lines, 0 host crashes, 0 app fatals, 0 surface failures, 294 full renders.
- **Why it is not attributed to the change** ℹ: the change only *reduces* per-rebuild work (the lane-guidance bitmap is reused while its state is unchanged, the template distance comparisons are bucketed); it adds no buffer, cache or thread. The host-side growth is on Google's side of the IPC.
- **Not measured** ✗: the samples are single snapshots before/after the drive, the vehicle walked past the destination (so routes/tiles across a wide area were loaded), and the car path now runs on the configured car cache (128 tiles per database, `core/NativeTileDataCache.kt`) instead of the library default of 25. A leak therefore cannot be separated from legitimate tile/cache growth from these numbers.
- **Measured 2026-09-27** ✅ (phone, scripted walk, no driving — `guidelines/Build.md` §10): the *app* process' native heap went **115 MB → 337 MB** and `TOTAL PSS` **377 MB → 600 MB** over a wide-area walk, **saturating** under continued panning (7 samples flat to ±0.4 MB) and **retaining 324 MB** after returning to the start viewport, while `Bitmap (malloced)` stayed flat at 62 MB. So the growth this entry went looking for is on the native **tile-data retention** side, not the bitmap cache and not the per-render transients; it is now owned by `bound-tile-data-retention` (release path + the ceiling requirement). Still owed there: the same walk on a build with a different `PHONE_TILES` (512 vs 128 vs the library default 25), which is the only way to separate the cache's share from the allocator's retention of freed pages.
- **Next step**: repeat with a *stationary* session and a *repeat* of the identical route, sampling `dumpsys meminfo` every minute, so growth without new tiles can be told from cache fill. If the app side still grows, the candidates in §49 (per-render transient buffers) and the tile-cache capacity (configured per surface) are the first places to look.
- **Inherited from §63 (closed 2026-09-27)** ⏳: the `CAR_TILES = 128` value is reasoned, not measured — 5.1× the library default and 4× below the phone, chosen because no car/AAOS device or head unit was attached. Follow-up on the AAOS AVD or a head unit (`guidelines/Build.md` §10): enable `osmscout::log.Debug(true)`, confirm `adb logcat -s NaviVeylin` shows `[JNI] setNativeDataCacheSize(128)` and the per-render `applied tile data cache size 128 to N db(s)`, then compare `dumpsys meminfo <pkg>` native heap against the library-default run; re-tune by changing the one constant.
- **Partially addressed 2026-09-21** ✅: the per-screen overrun buffer is now released on every screen stop (`fix-car-surface-ownership-and-host-callbacks`), so the car stack no longer retains up to four extra ~3.7–8 MB buffers while its screens sit stopped. §49 (per-render transient buffers) stays open, and the stationary-route measurement above is still the next step.

## 64. The car template rebuild rate is still bounded by the arrival estimate, not by the distance buckets — Found 2026-09-21 during `fix-host-crash-residual-paths` (task 4.2/4.4)

- **Observation** ℹ: the change buckets the template rebuild to the *displayed* distance (both the distance-to-turn and the remaining route distance use the host's own rounding — 50 m steps below 1 km, 100 m above) and reuses the lane image while the lane state is unchanged, but `hasStateChanged` still compares `etaMillis / 1000`, and the native engine re-emits the arrival estimate on every position update in practice. The residual template rate is therefore the estimate's update rate, i.e. close to the position rate (~1/s while driving), not the distance-bucket rate. The lane-image allocation and its IPC payload per rebuild **are** gone.
- **Why not fixed here** ✗: bucketing the arrival estimate to the displayed minute (what the ETA card shows) would make the host's own countdown authoritative for up to a minute. Whether the host ticks that countdown itself is not known — the change's spec deliberately leaves the arrival estimate out ("a shifted estimate is displayed content") — so this needs a device measurement first: run navigation with the estimate bucketed to the minute and watch the ETA card for a frozen countdown (rail-widget card and the navigation template both).
- **Fix candidate**: `hasStateChanged` compares `etaMillis / 60_000` instead of `/ 1000`; measure `Diag/HOST`-correlated template refreshes per minute before/after and watch the card. If the host does not tick, keep the per-second comparison and consider publishing the remaining time only on manoeuvre/step changes instead.

---

## 46. Phone notification `Stop` action does not end navigation — FIXED by `background-navigation-notification` and closed by `one-navigation-engine` (2026-09-27)

- **Closed 2026-09-27 by `one-navigation-engine`** ✅: the whole class of defect is gone, not just its symptom. `NavigationStateProvider` and `NavigationStopRequests` are deleted; the process-scoped `NavigationEngine` (`app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt`) is the single navigation source every surface observes, so the shade's stop action reaches the one session directly (`NavigationNotificationService.handleAction` → `engine.stopNavigation()`). There is no callback slot to overwrite, no per-source state mirror and no second engine to mis-route to. The phone surface keeps clearing the route panel and the drawn route on every stop path through its adapter (`NavigationViewModel.setRoutePanelViewModel`). Regression tests: `NavigationEngineStopPathTest` (stop clears the panel, notification stop ends navigation/clears the view/leaves nothing for the notification to keep alive, follow released), `NavigationNotificationServiceActionTest` (only the stop-navigation action stops the engine), `NavigationEngineTwoSurfaceTest` (stop from either surface ends it for both). Evidence: `./gradlew test` green — mobile 1255 / automotive 1255 / `:auto` 697 / `:core` 369 / `:osmscout-client-java` 26, 0 failures.
- **Superseded history (kept for the record)** — the fix `background-navigation-notification` task 6.1 shipped on 2026-09-20: (`core/src/main/java/com/naviveylin/core/NavigationStopRequests.kt`), implemented by `NavigationStateProvider`: `stopNavigation()` broadcasts instead of routing to one callback slot, and the phone `NavigationViewModel` + `AANavigationController` each collect it and stop their own navigation (idempotent). The provider's mirror became a per-source registry where the navigating source wins — an idle car registrant can no longer blank live navigation, and `ViewModel.onCleared` unregisters the dead phone surface. `NavigationViewModel.stopNavigation()` also clears the route panel (`setNavigating(false)` + `clearRouteFromMap()`) so the shade stop matches the in-app button, and the service logs `onStartCommand action=…`. New tests: `NavigationStateProviderTest` (5), `NavigationViewModelStopPathTest` (2), `NavigationNotificationServiceActionTest` (2). Evidence: mobile 1068 / automotive 1068 / core 302 / auto 516 tests, 0 failures; both debug APKs assemble; no new compiler warnings.
- **Still open on device** ⏳: tap `Stop` in the phone shade while navigating **with a car session live in the same process** (the original failure scenario) — confirm navigation ends, the notification withdraws and the route panel is left non-navigating. That re-run also re-opens `background-navigation-notification` task 4.1 and unblocks `car-turn-by-turn-rail-widget` task 7.4.
- **Residual, not fixed (same last-wins shape)** ⏳: `NavigationStateProvider.navigateTo` / `reportError` still route to the *last* registrant. Both controllers can route with the same JNI client, so a mis-routed call is not lost (unlike the stop command), which is why it was out of scope here. Fix candidate: reuse the new registry — route `navigateTo` to the navigating source, else the first registered one — or give `navigateTo` its own broadcast seam if double-routing ever becomes a risk.
- **Original finding 2026-09-20 on device during `background-navigation-notification` task 4.1 (phone, ongoing notification visible)** ✗: tapping `Stop` in the notification shade does not end navigation — guidance keeps running and the notification stays. The same path is gated for the car hint by `car-turn-by-turn-rail-widget` task 7.4 ("…the phone stop action still works"), so that task is blocked on this. Two causes are visible without a device:
  - (a) **`NavigationStateProvider` keeps ONE callback slot per command** (`app/src/main/java/com/naviveylin/navigation/NavigationStateProvider.kt:32-46` — `stopCallback`, `navigateToCallback`, `reportErrorCallback`), overwritten by every `observe(source)` call. Two sources register in one process: the phone `NavigationViewModel.init` (`app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt:64`) and the singleton `AANavigationController.init` (`app/src/main/java/com/naviveylin/navigation/AANavigationController.kt:85`). The latter is resolved on **every car-session warmup** (`auto/src/main/java/com/naviveylin/auto/NavigationSession.kt:328`, log step "Activating navigation controller"), so as soon as a head-unit/AAOS session starts after the phone app it owns `stopCallback` and the phone notification's action stops only the car controller's own (idle) engine. The same clobbering affects the mirrored `state`: the car controller's initial empty `NavigationState` is written into the provider when it subscribes, which can trip the service's active gate (`app/src/main/java/com/naviveylin/service/NavigationNotificationService.kt:105-113`) and `stopSelf()` the phone notification mid-navigation.
  - (b) **Not at parity with the in-app stop**: `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt:1991-1993` performs `navigationViewModel.stopNavigation()` **plus** `routePanelViewModel.setNavigating(false)` and `clearRouteFromMap()`; the notification path (`NavigationNotificationService.kt:69-79`) performs only `stateProvider.stopNavigation()` — the route panel keeps its navigating flag and the route stays drawn on the map.
  - **No diagnostics, no test**: the `ACTION_STOP_NAVIGATION` branch logs nothing, and nothing covers `onStartCommand` or the provider's callback routing (`NavigationNotificationBuilderTest` only asserts the `stopIntent` shape), so logcat cannot separate "intent never delivered" from "wrong target".
  - Fix candidates (both taken 2026-09-20, see the first bullet): route the stop to the source that is actually navigating (the provider now keeps a per-source registry where the navigating source wins the mirror and the stop broadcasts), put the shared stop in one place so the route-panel cleanup cannot be skipped, log the incoming action in `onStartCommand`, and cover the provider routing + service action with tests.

## 34. Car search opened from the root/history screens has no distance reference

- **Noticed 2026-09-19 while implementing `search-result-ranking`** ℹ: the spec's distance reference for the car is "last known GPS fix, else the current car map viewport center, else none (order by tier and quality only)". Only `MapScreen` can supply that center (it owns the renderer gate, so it passes `{ rendererGate.renderer.value?.markerViewport() }`); `RootScreen` and `SearchHistoryScreen` push `SearchScreen` without any map, so their results are ordered by tier and quality and show **no distance at all** — spec-legal, but a driver comparing results from the root list has no proximity signal. Fix candidate: publish the last rendered viewport center through a small shared provider (e.g. alongside `AutoLocationProvider`, updated by `NavigationSession`/`AutoMapRenderer` whenever the displayed center changes) and pass it from all three `SearchScreen` call sites. Not a defect of this change — the "neither exists" case is specified and covered by tests.

## 29. AA follow jumping — open points handed over (2026-09-17, stable-version handover)

Status: all four defects fixed and device-verified (2026-09-18, AAOS), including P2 and P3. `overlay-projects-against-displayed-frame` and `aa-follow-framing-and-zoom-parity` (27/27) are both archived. What remains below: one open decision (the auto-zoom first commit), the deferred P4 option, and the device-verification method.

**Open points:**

1. **⏳ The auto-zoom's FIRST commit jumped straight to the speed target — FIXED in `aa-entry-zoom-animation` (2026-09-19), device verification pending:** the measured `mag 13.000 -> 17.000` in ONE frame (16x area) on entering free driving is now walked: a transition-eligible zoom commit farther than `ZOOM_BLIT_LIMIT` becomes a walk target, stepped ≤ the blit window once per LANDED render, ending exactly on the requested value (11 new `AutoMapRendererTest` cases; revert-checked: 6 fail pre-change). The walk has its own loop (`startZoomWalkLoop`) because the extrapolation loop is movement-gated — a parked entry would otherwise stall. It is deliberately NOT a controller seed (fix-paced ≈8 s, no visual gain) and stays render-paced. Remaining: on-device judgement of the walk (change tasks 4.1-4.5: magnitude sequence, `lock OK` render count vs the ~4 s bound, parked entry, navigation start, re-enable). Note for whoever takes it: P3's transition cannot smooth it as-is, because for a zoom-IN the frame carries the *new* magnification and reaching the old displayed one would need the buffer scaled below the overrun limit — the phone solves this by queueing the render while the animation plays (`smooth-zoom`: "the debounced native render SHALL be queued while the animation plays"), i.e. render at the old magnification first and let the displayed scale animate upward.
2. **⏳ P4 (smooth heading-up between fixes) — mechanism recorded, deliberately deferred** (design D4 of the follow-up change): rotate the canvas about the follow anchor by the accumulated heading residual. Bound: the exposed corner sliver is ~`r·θ` with `r` up to ~1000 px on a 1080x600 surface, i.e. ~1.5-2 deg of accumulated rotation before the overrun margin (108/60 px) is exceeded; cost: a filtered 1296x720 rotate per tick on a loop already measured at ~11 Hz (§19); risk: resampling softness and composing the overlay rotation with the marker's own bearing rotation. Revisit only if rotation stepping is still visible after P3.
3. **ℹ Device-verification method (do not re-derive):**
   - full renders = `adb logcat -d -t 2000 | grep -c 'lock OK'` (`blitToSurface` logs nothing, so `lock OK` counts ONLY full native renders); fixes = `grep -c 'FreeDrivingScreen: GPS fix'`. Baseline before the fixes: 101 renders / 105 fixes in 104 s.
   - frame placement = log `dy` + the frame centre + the display per drawn frame, then per consecutive pair compute `(Δdy + Δframe_px) − Δdisp_px` (the display's advance IS the expected content scroll); rms should stay sub-pixel, and any commit step shows as a spike. This is the check that found defect 3.
   - the AVD is `Automotive_Distant_Display_with_Google_Play` (AAOS, x86_64, API 33, 1080x600 main + virtual distant displays); the app id is `com.framstag.naviveylin`; the debug APK is `testOnly`, so install with `adb install -r -t`; build it with `-Pandroid.injected.build.abi=x86_64` (an arm64-only APK will not install); relaunch with `adb shell am start -n com.framstag.naviveylin/androidx.car.app.activity.CarAppActivity` and then tap Free driving (the car UI is not reachable from `uiautomator`).
   - the emulator console has NO `geo gpx`; `geo fix` / `geo nmea` injections are IGNORED by whatever feeds this AVD (the feed reports `bearing+360` at times), so a straight-line measurement must be produced by controlling the feed itself, not by injecting.

---

## 19. Findings from `overlay-projects-against-displayed-frame` (2026-09-17)

Findings detected while fixing the AA follow overlay projection; all out of that change's scope
(it deliberately keeps the follow re-anchor cadence and the overlay frame bookkeeping only).

- **⏳ `reengageFollow` relies on a preceding `setViewport` to have requested the render** ✗: it writes `viewportLat/Lon` + `emitViewportState()` but never calls `requestRender()`; it works only because every current call site calls it immediately after `setViewport` (which does request). A caller that re-engages follow on its own would silently keep the stale frame on the surface. Fix option: request the render in `reengageFollow` when the target actually moved.
- **ℹ Extrapolation loop measured ~11 Hz, not the nominal 30 Hz** (`EXTRAPOLATION_FRAME_MS = 33`): in the 104 s AA window, 38 diagnostic lines at one line per 30 ticks is ~1140 ticks / 104 s ≈ 11 Hz. Each tick locks the shared surface and draws the full 1296×720 overrun bitmap plus the overlays, so the period is dominated by the draw — measure before tuning the constant (a smaller period would not raise the rate).
- **ℹ Diagnostics use the default locale:** `"%.6f".format(...)` in the AA follow log and the `Diag/MAP` render lines prints decimal commas on a German device (`51,513637`), so those lines are not machine-parseable. Use `Locale.ROOT` for diagnostic formatting.

---

## 17. Verification-gate blind spots: Gradle test up-to-date masking + OpenSpec task-marker parsing

- **Gradle unit-test tasks report green without executing (found 2026-09-13 while running the archive gate for `fix-address-lookup-accuracy`)** ℹ: `./gradlew test` printed `BUILD SUCCESSFUL in 5s` with `152 actionable tasks: 4 executed, 145 up-to-date` — **zero tests ran**; the verdict came from the up-to-date check against a previous run's output, not from an execution. Two follow-ups that do NOT fix it: `--rerun` is ignored by AGP's unit-test tasks (only plain tasks such as `:osmscout-client-java:test` honour it), and `--rerun` on the aggregate `test` lifecycle task propagates to no dependent task at all. `--rerun-tasks` works but also forces every compile in the graph. What does work: delete the task outputs, e.g. `rm -rf app/build/test-results/testMobileDebugUnitTest app/build/test-results/testAutomotiveDebugUnitTest core/build/test-results/testDebugUnitTest auto/build/test-results/testDebugUnitTest`, then run the four test tasks — the run then reports real execution time (`BUILD SUCCESSFUL in 1m 59s`, 4 executed). Consequence to guard against: any OpenSpec apply/archive evidence of the form "`./gradlew test` → BUILD SUCCESSFUL" may prove nothing about the current tree; a 5-second "success" is the tell. Fix option: a Gradle `check`-wired task or a wrapper in `.pi/skills/run-tests` that clears `test-results` before invoking the suite, and a rule that the evidence line must quote the executed-task count and elapsed time. Also note the up-to-date check is content-hash based, so a file mtime later than the newest `test-results` XML does not by itself prove the run predates the edit — verify content, not timestamps.
- **OpenSpec silently ignores bare numbered task markers (found 2026-09-13)** ℹ: `fix-address-lookup-accuracy/tasks.md` used `1. [x] …`-style items (no `-` bullet); the task parser only recognises `- [x]`, so `openspec list` reported `completedTasks: 0, totalTasks: 0, status: "no-tasks"` for a change that was in fact 14/15 complete — while `openspec status` simultaneously reported `isPlanningComplete: true` / `isComplete: true`, so nothing errored. A change can therefore look stalled (or, read the other way, look finished) with no diagnostic. Fixed for that change by renumbering to `- [x] N.M` under numbered `## N.` headings; the other 13 open changes already used that form. Fix option: validate task-marker style in CI (every `tasks.md` must contain at least one `- [x]`/`- [ ]` item) so the mismatch fails loudly instead of silently zeroing the counts.
- **A stale unit-test asset APK makes asset-reading Robolectric tests lie (found 2026-09-29 while proving a new packaged-asset gate is not vacuous, change `fix-poi-symbol-icons`)** ⚠: `packageMobileDebugUnitTestForUnitTest` stayed **UP-TO-DATE** after `mergeMobileDebugAssets` produced a tree with one file removed, so `PackagedPoiIconsTest`/`AssetCopierTest` kept reading the *previous* `apk-for-local-test.ap_` — a test asserting a packaged icon passed with the icon absent from every on-disk asset tree. Two consequences: (1) a mutation that removes a file only from a `Sync` task's **output** proves nothing at all — the sync restores it, and the asset APK may not even be rebuilt; mutate the real source under `app/src/main/cpp/libosmscout/`; (2) after any asset change, clear `app/build/intermediates/apk_for_local_test/<variant>` (or run `--rerun-tasks`) before trusting an asset assertion, and when such a test disagrees with the disk, suspect the APK before the code. Fix option: wire the unit-test asset package task to the merged-assets content explicitly, or make the run-tests skill's asset-affecting recipe clear that directory. Measured the same session: `--rerun` is ignored on the aggregate `test`, and deleting `test-results/<flavor>` does **not** force execution when the build cache can restore the outputs (`:core`/`:auto` came back `FROM-CACHE` twice) — `--rerun-tasks` is the only reliable forcing move here.

---

## 16. On-device verification pending: fix-zero-distance-to-turn (native step distance)

- **⏳ Pending device run (change `fix-zero-distance-to-turn`, tasks 5.4/6.3)** ✗: the native instruction-distance fix (submodule `3c761d618` on `naviveylin-local`, gitlink bumped at main `195f278`) is implemented and unit-tested (Kotlin state handling + step-index lookup + mapper arrival step — 14 new tests green, full `./gradlew test` green, both flavors all ABIs build). The **native engine path is not verifiable on the host JVM** (the JNI stub .so is symbol-free, so `PositionAgent`/`RouteInstructionAgent`/`GenerateNextRouteInstruction` run only on-device). On next device/emulator session: boot the Automotive AVD, drive a GPX-replay or mock-GPS route with one deviation → reroute, and check via `adb logcat -s NaviVeylin` (plus a temporary `osmscout::log.Debug()` of `distanceTo`/`nodeDist`/`abscissa`): (a) distance counts down and never shows 0 m while the turn is ahead, (b) no routeNode reset-to-begin on a transient forward-search miss, (c) instruction keeps updating while a reroute is suppressed, (d) final step shows "Arrive — <remaining>" after the last turn. Also re-run AA/phone instruction-panel screens at the destination tail — this is where the 0 m freeze appeared.

- **2026-09-15 emulator attempt: blocked by harness, still unverified** ⏳: on a Pixel_8 AVD (GMS disabled → `LocationManager` fallback; regional NRW map; Decathlon Aplerbeck route, 7,4 km) the fix stream worked (`vel=6.69 m/s`, follow camera tracked every hop), but `geo fix` always emits `bear=0.0` — a bearing cannot be injected — so `PositionAgent` never establishes travel direction: the instruction card froze on the first turn across ~2 km and no reroute fired on a 1 km deviation, i.e. the four criteria could not be exercised. Unconfirmed finding for the real-device pass: the next-turn overlay showed **"0 m" while the route panel showed "40 m" for the *same* turn** ("Links abbiegen → Ruhrallee") — check for a distance-source divergence (overlay vs panel step distance). Needs a real drive, or a mock-GPS app using `setTestProvider` with real timestamps/velocity/bearing.

---

## 1. Route Calculation & Visualization

| Feature | Status | Notes |
|---------|--------|-------|
| Avoid tolls/ferries checkboxes | ✗ | `RoutingProfile` supports avoid flags — no UI yet |

## 2. Turn-by-Turn Navigation

| Feature | Status | Notes |
|---------|--------|-------|
| Voice guidance / audio instructions | ✗ | JavaScout `onVoiceInstruction(int[])` callback exists in JNI |

## 3. GPX Track Import & Playback

> libosmscout submodule (master) already has GPX import/render support (archived change `javascout-gpx-track-import-render`) and JavaScout has `TrackPlayer` — but nothing is wired into the NaviVeylin app yet.

| Feature | Status | Notes |
|---------|--------|-------|
| GPX file import | ✗ | `importGpxTrack()` exists in JNI — no app UI |
| Track rendering on map | ✗ | `renderWithRouteAndPois()` accepts `trackLats`/`trackLons` |
| Track playback (simulated GPS) | ✗ | JavaScout `TrackPlayer.java` with speed multiplier |
| Track playback toolbar (play/pause/stop/speed) | ✗ | JavaScout `trackToolbar` HBox |

## 4. Object Description & Long-Press

| Feature | Status | Notes |
|---------|--------|-------|
| Long-press timeout configuration | ✗ | Hardcoded 500ms — JavaScout configurable |

## 5. UI / Shell

| Feature | Status | Notes |
|---------|--------|-------|
| Responsive layout (small screen support) | ✗ | JavaScout `SMALL_SCREEN_THRESHOLD` (600px) |
| Double-tap to zoom | ✗ | Candidate feature (not in JavaScout either). No double-tap gesture exists (`map-pan-zoom` covers pan/pinch only). If added later, wire it into the smooth-zoom animation path and the continuous fractional magnification (see `continuous-pinch-zoom`). |

## 6. Rendering

| Feature | Status | Notes |
|---------|--------|-------|
| Track rendering on map | ✗ | |

## 8. GPS / Position

| Feature | Status | Notes |
|---------|--------|-------|
| GPS simulation / dead reckoning when no fix (PositionSimulator) | ✗ | Guess vehicle movement from last position + speed + heading (+ route if navigating) when GPS is stale. Separate service from `LocationService` — never talk to raw GPS directly when there is no fix. See detail below. |

### PositionSimulator — aggregated design notes (from GPS jump investigation)

**Problem:** free driving mode shows random GPS/map jumps on real devices. Root cause: `LocationService` ran Fused AND raw `LocationManager` (GPS/NETWORK/PASSIVE) in parallel on Play Services devices; raw fixes bypassed OS smoothing. Fixed by change `gps-strict-fallback` (Fused only when available). This entry covers the *next* gap: when there is no GPS fix at all.

**Goal:** when GPS is lost, estimate vehicle movement instead of freezing the marker at the last fix (current behavior in free mode).

**State machine:**

```
GPS fresh (acc<50m, age<5s)  →  REAL
GPS stale > N s              →  ESTIMATED (extrapolate)
estimate age > M s / drift > D m → LOST (give up, show signal-lost)
GPS back                     →  REAL
```

**Estimation math:** `position(t) = lastFix + ∫ speed(τ) · heading(τ) dτ`

- heading: free mode → course-over-ground from position history (already implemented in `MapCanvasViewModel` for bearing, low-pass alpha 0.3/0.7); nav mode → route bearing
- speed: filtered GPS speed (150 km/h cap, `filterSpeed` exists in `NavigationViewModel`/`AANavigationController`); AAOS: CAN bus speed via Vehicle HAL later (`AutomotiveDevice` is feature-check only today)
- route: nav mode → snap to route geometry; free mode → heading projection (drifts, must be bounded)

**What already exists (reuse):**
- Native `PositionAgent` (`app/src/main/cpp/libosmscout/libosmscout/src/osmscout/navigation/PositionAgent.cpp:267-355`) dead-reckons along the route at vehicle speed (capped by max speed) — but only in tunnels and only during navigation. Outside tunnels → `NoGpsSignal`, estimate holds.
- `GpsFixQuality` enum (NONE/POOR/GOOD) + `GPS_FIX_FRESHNESS_MS = 5000`, `GPS_FIX_MAX_ACCURACY_M = 50f` in `MapCanvasViewModel` — freshness/accuracy thresholds exist but nothing acts on them.
- Course-over-ground history + smoothed bearing in `MapCanvasViewModel` (`addCoursePoint`/`computeCourseBearing`/`smoothCourseBearing`).

**Open design questions:**
1. Scope: free mode only, or also open-road GPS loss during navigation (native agent only covers tunnels)?
2. Give-up bounds: after N s / M m of estimation → LOST (real apps ~10-30 s).
3. Marker UX: ESTIMATED position visually distinct from REAL (color/opacity)?
4. Where: new Kotlin `@Singleton` service feeding a derived position flow with state (REAL/ESTIMATED/LOST); consumers = marker, center, nav engine, AA.

## 9. Viewport Save — Residual Bug

- **Residual: pre-init viewport key mismatch (`mapPath` vs `"default"`) ℹ**: `MapCanvasViewModel.initMap` loads with `viewportStorage.load(currentMapKey ?: mapPath)` (`MapCanvasViewModel.kt:1408`), while the navigation-end save (`:2167`) and `saveViewport()` (`:2811`) persist with `currentMapKey ?: "default"`. Harmless today because `initMap` sets `currentMapKey` (`:1314`) before the load, so the fallbacks never meet — but any future save/load before `initMap` (or after a failed init) would write and read different files. Fix candidate: share one key helper (`currentMapKey ?: mapPath`) across all three call sites. Found 2026-09-13 while triaging `MapCanvasViewModelNavEndRestoreTest` (change `smooth-decimal-auto-zoom`, task 6.2) — test-only fix there, production untouched.

## 14. Kover deprecation on the Gradle 10 path

- **Kover 0.9.8 emits a Gradle 9.6 deprecation** ⏳: Kover's own internals (`kotlinx.kover.gradle.plugin.appliers.PrepareKoverKt`) add a `Project`-object dependency notation — deprecated in Gradle 9.6, hard failure in Gradle 10. Not fixable from our scripts (we use string notation everywhere). Revisit on Gradle 10 upgrade / newer Kover. See `guidelines/Build.md` §7.

## 22. Gradle 10 deprecation sweep (build-level, pre-upgrade audit)

- **Every build prints “Deprecated Gradle features were used in this build, making it incompatible with Gradle 10” (observed 2026-09-15 on the §17-verified `./gradlew test`)** ⏳: Gradle 9.6.1 tolerates the deprecations, Gradle 10 hard-fails; §14 already tracks Kover's Project-object notation (not fixable from our scripts). Sweep the remaining deprecations once before any Gradle 10 upgrade: `./gradlew test :app:assembleMobileDebug --warning-mode all`, triage the emitted list, and separate plugin-owned warnings (AGP/Kover — expect one each, document and ignore) from our own scripts' (`buildSrc/*.gradle.kts`, root/app/auto/core `*.gradle.kts` — fixable locally). Two adjacent, still-untried items: configuration cache (`--configuration-cache`, Gradle suggests it each build) — verify it against the license-assets Variant-API tasks and the `release` version-state bump (config-time execution) before enabling in CI; and `org.gradle.configuration-cache=true` interplay with the `release` task gating (bump runs at configuration time, which config-cache may re-run per invocation).

## 10. Pending On-Device Verification

| Item | Status | Notes |
|------|--------|-------|
| Vehicle anchor visible-area + per-surface parity (change `anchor-per-surface-visible-area`, tasks 6.3–6.6, 8.1–8.3) | ⏳ | Implementation green (suite 2026-09-15: app mobile/automotive 964 each, auto 373, core 216; incl. `markerRidesTheBlittedContentWithABlitOffset` + `hostPaneClampsTheLeadingEdgeAnchorOnly`). On-device: routing/free-driving anchors stay clear of the overlays (turn card, routing-status card, street-name pill, widget column) at large font scale; browse framing unchanged (identity); car↔phone per-surface anchor values independent; upgraded install keeps its pre-split value until the car gets its own; `center/center` on both surfaces frames identically to the previous build; AA: leading-edge preset clears the host pane (LTR + RTL), vehicle marker + destination pin ride the blitted content without lead-then-snap during extrapolation glides. |
| Continuous pinch zoom — real-device sanity check | ⏳ | Emulator pinch is synthetic input; user confirmed pinch on emulator 2026-08-29 (task 5.2 closed), real-device check remains (task 5.2 tail). Verify: pinch in/out continuity, limits, fractional mag persistence, GPS marker anchor, follow-mode pinch, no FATAL. |
| Bounded zoom walk on the phone (change `fix-phone-zoom-animation-parity`, tasks 6.3-6.6) | ⏳ | Implementation + unit tests green (walk arithmetic `:core` 14/14, ViewModel wiring 7/7, renderer immediate path 3/3, screen rules 7/7; full `ui.map` package suite green). On-device: enter free driving from a far browse viewport - no consecutive frame change above 0.25 levels, walk ends exactly on the speed target, render count per entry recorded; bottom-center anchor keeps the vehicle pixel fixed through zoom in/out with no correction jump at landing; a large zoom-out never exposes bare surface color; pinch/buttons/keys still request their first frame immediately; the persisted viewport holds the final target. Blocked 2026-09-24: no device/emulator attached (`adb devices` empty). |

## 15. Kover coverage attribution for Robolectric-tested classes

- **Pre-existing tooling gap found during fix-contact-address-resolution (2026-09-13)** ℹ: in the merged and `:app` Kover XML reports, classes exercised only by Robolectric tests show near-zero instruction coverage while their tests pass and assert behaviour — `ContactsRepository` 5 covered/0 missed, `FavoriteRepository` 16/0, `AddressBookSheetKt` 41 missed/0 covered (its four Compose tests pass). Plain-JUnit-covered classes report plausible numbers. Suspected cause: Robolectric loads app classes in its own sandbox classloader, so JaCoCo/Kover exec data is recorded against a different class identity. Investigate: Kover/Robolectric instrumentation options (offline instrumentation, `kover { }` filters, or `robolectric.properties` sandbox config) before trusting any coverage gate. Until then, use the revert-check (new test fails on pre-change code) as coverage evidence instead.

## 21. README build commands are stale

- **Pre-existing, noticed 2026-09-13 during `license-compliance-baseline` task 8.4** ℹ: `README.md` › Build Commands still lists `./gradlew :app:assembleRelease` and `:osmscout-jni:assembleRelease`, and the project-structure tree lists `osmscout-jni/` as a JNI bridge AAR — neither exists: the module is `:osmscout-client-java`, and the app builds per flavor (`:app:assembleMobileDebug`, `:app:assembleAutomotiveDebug`, `./gradlew release` for both AABs). The license additions from this change were added to the same document, so the two stale commands now sit next to correct ones. Fix: replace the stale commands with the flavor-aware ones and correct the structure tree.

## 20. OpenSpec config rules: add `openspec doctor` to CI

- **Residual hardening after the `config.yaml` rules bug (2026-09-13)** ℹ: three rule sets in `openspec/config.yaml` (proposal/specs/design) were silently dropped — colon+space entries parsed as YAML mappings, the array failed the array-of-strings check; quoting the entries fixed it. No CI guard exists (checked `build.yml`); add `openspec doctor`, or a tasks.md marker-style validation (§17), so a silently dropped rules file fails loudly.

## 23. libosmscout-kotlin port stubs — verify the binding is unused before anyone wires it in

- **Submodule `app/src/main/cpp/libosmscout/libosmscout-kotlin/` carries 7 unfinished port markers (found 2026-09-15)** ℹ: `objecttypes/TypeConfig.kt` skips feature-description handling in `loadFromData` (“TODO Fetch feature”, “TODO: Add description to feature”), `registerType` has “TODO: Calculate wayTypeIdBytes & Co.” plus two “TODO: Fix” lines, and `index/AreaWayIndex.kt:120` has “TODO: Reserve capacity for offsets”. Grep across every `*.gradle*`/`CMakeLists.txt` shows **no module references `libosmscout-kotlin`** — the binding is inert today (the app uses the C++ JNI bridge + `:osmscout-client-java`; a plain-JUnit or Kotlin binding is not on any build path). The submodule is our own fork (`naviveylin-local`), so this is decision material, not urgent: either finish the port to match C++/Java behavior (TypeConfig without feature descriptions would render/query differently), or document the binding as deliberately unbuilt and add a code comment so a future dependency addition fails loudly instead of silently using a stub.

## 24. Sharp-s uppercase form (ẞ U+1E9E) is not folded

- **Known limitation left in place by `fix-sharp-s-transliteration-match` (2026-09-16)** ℹ: the character map row for U+1E9E (capital sharp S) transliterates to itself, so the case-normalized transliterated comparison that change introduced still cannot match a query spelling a name with `ẞ` against an index name spelled `SS`/`ss` (nor the reverse). No name in the NRW or Iceland map databases uses U+1E9E (verified by extracting `location.idx` and counting spellings: `straße` 65 848×, `strasse` 22×, zero `ẞ`), so nothing is unfindable today. Fix candidate: an upstream character-map row that transliterates `ẞ` to `SS` (or `ss`, now equivalent because the comparison is case-normalized) when the table is regenerated. Land upstream when the table is next touched.

## 27. Unconstrained search of a second loaded database returns out-of-area noise

- **Observed 2026-09-16 during `fix-sharp-s-transliteration-match` task 6.5** ℹ: with the map view centered on Iceland and the GPS scope resolved to Regierungsbezirk Arnsberg, the query `Am Birkenbaum 6 Dortmund` returned Iceland-database entries (`Leiðhamrar Dofri`, `Lokinhamrar`, 5,8 km) instead of the Dortmund address. Cause is the documented per-database scope rule: a region handle is database-local, so the database that does *not* own the handle is searched unconstrained (`OSMScoutClient.cpp`, string-search scope comment) and its free-text index answers on short partial tokens ("am" inside "…hamrar…"). Not created by this change — the characters in the returned names (`ð`, `ö`, `í`, `æ`, `á`) are not affected by the transliteration fix, and no pre-change baseline run was made. Investigate: when a scope exists for one database, either skip the other databases' free-text hits or rank them below scoped results (and/or apply a distance limit), so an address query cannot be answered from another map region's data.
- **Fix in flight** ⏳ (2026-10-01): `fix-cross-database-search-scope` takes the recorded mechanism — the text index is scope-blind in **every** database (`OSMScoutClient.cpp:4057` passes no region), so a free-text hit outside the resolved scope's extent is now dropped before the per-source cap, and every structured result carries whether it lies inside the scope (new `LocationEntry.inSearchScope`, spec `osmscout-jni`) so the app's ranker places out-of-scope close matches below in-scope ones (`SearchResultRanker`, spec `search-result-ranking`). Databases keep being searched, so a fully qualified query for another installed map still resolves (spec `location-search`). This entry is removed when that change is archived; the residual test gap is §109 and the candidate-budget question is §110.

## 28. Whole-level rounding in `computeAreaZoom` over-fits area favorites and POI search — **FIXED** by `fix-area-fit-zoom-rounding` (2026-10-03)

- **Found 2026-09-18 during `route-overview-fit` (DPI/`cos(lat)` ground-resolution fix)** ℹ: the shared bbox→magnification helper rounds to whole levels (`Math.round`), so the fitted content can end up larger than the 80%-margin target. The route overview is protected by an explicit projection check (`routeFitsVisibleArea`, design Decision 8), but the other callers had no such verification, so their fitted bbox could overflow the mini map / map viewport (previously masked by the over-zoom the ground-resolution fix removed).
- **Corrected direction 2026-10-03 (during `fix-area-fit-zoom-rounding`)** ℹ: the entry (and the route fit's own comment) said the helper "rounds the exact fit *down* … so the fitted content ends up to ~13% larger" — the two halves do not go together. Rounding the magnitude **down** makes the content *smaller* than the target (more margin, harmless); it is rounding **up** to the next whole level (up to +0.5 level = 2^0.5 ≈ 1.41x) that exceeds the viewport, since 0.8 × 1.41 ≈ 1.13. The clip was real either way.
- **Fixed** ✅ by change `fix-area-fit-zoom-rounding` (2026-10-03): the route fit's private corner-projection check was extracted into one shared seam (`app/src/main/java/com/naviveylin/ui/map/FitVerification.kt`: `fitsVisibleArea` + `verifiedAreaFit`) and every fit caller now uses it — the route overview, the area-favorite fit, the POI fit on the main map and the embedded result map's fit (both its result-extent path and its radius fallback). Two extra causes the entry did not name were fixed with it: the favourite/POI fits park the camera on a point that is **not** the bbox midpoint (so they clipped even at an exact fit), and the POI fit inherited the area-favorites floor (`MIN_AREA_ZOOM = 14`), which made "both the current location and the POI visible" impossible beyond a few hundred metres — it now uses the render-stability minimum, mirroring `route-map-overview`'s long-trip rule. Evidence: `FitVerificationTest` 8/8, `AreaFitViewModelTest` 5/5, `EmbeddedResultMapFitTest` 4/4, `MapCanvasViewModelRouteFitTest` 14/14 unmodified, `PoiSearchViewModelTest` 14/14 (its old `14.0` floor assertion became a visibility assertion — 14.0 → 12.0), full gate `:app` 1500/1500 both flavours · `:core` 437/0 · `:auto` 754/0, four revert-checks recorded. Specs: `fav-auto-zoom` + `poi-search` deltas. This entry is removed when that change is archived.

## 35. On-device re-check of the area-favorites and POI-search fit zooms

- **Created 2026-09-18 by `route-overview-fit`** ℹ: that change corrected `computeAreaZoom`'s ground resolution (display DPI + Mercator `cos(lat)`), which *intentionally* changes the zoom the phone picks when selecting an area favorite and when the POI search fits its results — both now zoom out to the geometrically correct level (previously over-zoomed by `dpi / 96 * (1 / cos(lat))`, ≈2 levels on a 420-dpi phone). Unit tests stay green, but no on-device/visual check of those two flows is part of that change. Follow-up: pick an area favorite and run a POI radius search on a real phone/emulator and confirm the framing is sane (not too far out, markers inside the visible area).

## 36. `public-transport` stylesheet draws no route  — ⏸ ON HOLD (owner decision 2026-09-20)

- **ON HOLD 2026-09-20 — deliberately not the next change.** Re-investigated 2026-09-20 and the blast radius is far larger than this entry first recorded: **5 of the 8 user-selectable styles cannot draw the active route at all**, not one. See the scope block below; the fix is understood and small, but it waits on option A/B/C being chosen.
- **Corrected scope (verified 2026-09-20).** `BundledMapStyles.USER_SELECTABLE` (`core/src/main/java/com/naviveylin/core/BundledMapStyles.kt:38`) is the 8 styles `boundaries, coastlines, cycle, motorways, public-transport, railways, standard, winter-sports`. `stylesheets/include/route.oss` is the ONLY definition site of `[TYPE _route]` (:56-57), `[TYPE _track]` (:62), `[TYPE _route_start]`/`_route_end` (:68-69), `[TYPE _favorite]`/`_search_selected` (:76-77) and of `SYMBOL route_start`/`route_end`/`favorite_marker`/`search_marker` (:26-46). It is included by `standard.oss`, `cycle.oss` and `winter-sports.oss` only — so the other five (`public-transport`, `railways`, `motorways`, `boundaries`, `coastlines`) draw no route line, no start/end pins, no GPX track, no favourite markers and no selected-search marker. `public-transport.oss` is the tell: it declares `GROUP _route` in `ORDER WAYS` (:4) but never wires the rule. No app-side escape hatch: one stylesheet is loaded (`MapCanvasViewModel.applyStyleSheet` → `loadStyleSheet`) and route/POI drawing goes through `MapRenderer.kt:714 renderWithRouteAndPois` with no per-render style override.
- **Three requirements are contradicted as written:** `route-panel-ui` scenario "Route polyline rendered on map" (unconditional: route polyline SHALL be rendered AND `_route_start`/`_route_end` markers SHALL appear), `fav-markers` ("SHALL render every favorite location … via `renderWithRouteAndPois`", and "the existing `_favorite` synthetic node type already defined in the libosmscout stylesheet" — defined only in `include/route.oss`), and `route-appearance` Req "Every map style draws the route with the shared route colors" + scenario "Non-default styles have a route casing" (that change fixed `cycle`/`winter-sports` and left these five behind).
- **Open decision (A/B/C):** **(A)** add `MODULE "include/route"` to all five — minimal, satisfies the three requirements, specialty styles keep their character because `route.oss` adds only route/track/marker rules and no ORDER sections (recommended at the time); **(B)** drop the partial styles from the picker — needs a `map-styles` spec change, users lose those styles; **(C)** make route/marker drawing style-independent — larger, changes the render contract.
- **Fix shape if A:** five `MODULE` lines in the submodule `stylesheets/*.oss` (upstreamable) + submodule commit on `naviveylin-local` + gitlink bump in the main repo + an app-side guard test asserting every `USER_SELECTABLE` stylesheet transitively includes `include/route.oss` (precedent: `StylesheetHexColorCaseTest`, `AssetCopierTest`). Note `ORDER` sections are optional — `coastlines.oss` draws with none, and `_favorite`/`_track`/`_route_start` appear in no `ORDER` group anywhere yet render in `standard`/`cycle`/`winter-sports` — so the `MODULE` line is the load-bearing part.
- **Original note (2026-09-19, `map-marker-route-contrast`), now superseded by the scope block above:** `stylesheets/public-transport.oss` declares `GROUP _route` in its `ORDER WAYS` block but has no `[TYPE _route]` rule, so an active route is not drawn at all while that style is selected (it is user-selectable via `BundledMapStyles.USER_SELECTABLE`). The route rule lives in `stylesheets/include/route.oss`; that change made `cycle.oss` include it like `standard.oss`/`winter-sports.oss`, and left `public-transport` untouched because the missing rule is a pre-existing gap, not one of the reported appearance defects. Fix candidate: add `MODULE "include/route"` to its MODULE block (same pattern) and verify on-device that a route then appears in that style.

## 37. Pre-existing Kotlin deprecation warning in the marker overlay

- **Observed 2026-09-19 during `map-marker-route-contrast` task 4.1** ℹ: every `:app` build prints `w: .../ui/map/LocationMarkerOverlay.kt:167:14 'fun quadraticBezierTo(x1, y1, x2, y2)' is deprecated. Use quadraticTo() for consistency with cubicTo()`. Pre-existing — that change only replaced the gradient color selection in the same function. Fix: rename the call (behavior-identical) in a build-hygiene change, or wait until the Compose version makes it an error.

## 44. Pre-existing Kotlin build warnings beyond the marker overlay

- **Found 2026-09-20 during `fix-favorite-store-write-race` (full-suite build)** ℹ: `./gradlew test --continue --rerun-tasks` prints 66 Kotlin warnings, none of them from that change's files. Beyond `LocationMarkerOverlay.kt:167` (already tracked as §37), three classes are untracked: (a) `app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt:35` — "This annotation is currently applied to the value parameter only, but in the future it will also be applied to field" (annotation-target migration, a hard change in a future Kotlin); (b) `app/src/test/java/com/naviveylin/data/AmbientLightMonitorTest.kt:35` — Robolectric's `ShadowSensorManager.addSensor` is deprecated in Java; (c) the `ExperimentalCoroutinesApi` opt-in warnings spread over ~13 test files (`MapCanvasViewModelAutoZoomCommitTest`, `MapCanvasViewModelRoadInfoTest`, `MapCanvasViewModelSingleFollowCenterTest`, `RoutePanelViewModelSearchRankingTest`, ...). The archiving guidance requires a warning-free build, so this is build-hygiene debt rather than a defect. Fix candidate: one build-hygiene change that adds the missing `@OptIn` annotations, replaces the deprecated shadow call, and sets the annotation target explicitly.
- **Recount 2026-10-03 (four-module forced run during `fix-area-fit-zoom-rounding`)** ℹ: **106** warnings, all of the same debt, and none from a file that change touched. Additions since the 2026-09-20 count: `app/src/test/java/com/naviveylin/data/StartMapResolverTest.kt:118` — `Java type mismatch: inferred type is 'String?', but 'String' was expected` (arrived with `fix-start-map-selection`, still in flight); `service/NavigationNotificationServiceTapTargetTest.kt:40,69` — the deprecated `isBroadcastIntent`/`isActivityIntent` shadows; and in `:auto`: `RendererTestRule.kt:64` ("Type 'AutoMapRenderer' is final, so the value of the type parameter is predetermined"), `RenderLoopSupervisorTest.kt:40` ("Condition is always 'true'"), `TestCarContext.kt:23` (unchecked cast), `CarStyleLoadNotifierTest.kt:88` (deprecated `field defaults: Int`), plus further `ExperimentalCoroutinesApi` opt-ins (`DetailsScreenTest`, `FavoritesScreenTest`, `MapScreenTest`, `SearchScreenTest`, `TemplateFaultIsolationTest`). Recount rather than trust this list: `./gradlew :app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest :core:testDebugUnitTest :auto:testDebugUnitTest --rerun-tasks 2>&1 | grep -c '^w: '`.

## 118. `fav-auto-zoom`'s clamp scenario says 4–18 while the code clamps area-favorite fits to 14–20

- **Found 2026-10-03 during `fix-area-fit-zoom-rounding`** ℹ: the spec's scenario "Magnitude clamped to valid range" says "WHEN computed magnification is outside valid range (4–18) THEN magnification is clamped to the nearest valid value", but `computeAreaZoom` clamps `coerceIn(minZoom, MAX_MAG)` with `maxZoom = MAX_MAG = 20.0` and, for the area-favorite caller, `minZoom = MIN_AREA_ZOOM = 14.0` (`MapCanvasViewModel.kt`, the constant's own comment: "Minimum zoom level for area-type favorites (prevents too-zoomed-out view)"). So the enforced range is **14–20**, not 4–18 — the spec's numbers are stale on both ends (`MIN_MAG`/`GESTURE_MIN_MAG` are 4.0, and `MAX_MAG` is 20.0). Not fixed in that change because its spec delta had to keep the existing requirement block whole, and the change's `design.md` records it as an Open Question. Fix candidate: one spec-only change that states the range the code actually enforces (and, if 4–18 was ever the intent, the corresponding code change) — or split the clamp scenario per caller, since the floor is caller-specific (area favorites 14, route overview and the POI fit 4).

## 39. Real-life and Android Auto verification for the marker/route colors

- **Created 2026-09-19 by `map-marker-route-contrast`** ⏳: the visual checks (daylight route over a primary road and a white residential road, GPX track and search marker alongside, dark-presentation route unchanged, lighter dark marker without a white ring, 38 dp marker size, map-style switch to `cycle`/`winter-sports`) were performed on the phone emulator and reported working, but the user noted the concrete colors still need real-life validation, and the Android Auto half (host day/night, route/marker parity) was accepted by assumption instead of being measured on a head unit or the `Automotive_Distant_Display_with_Google_Play` AVD. Follow-up: check the violet route and the lighter dark marker on a real display outdoors and in a dark car interior, and run the AA day/night comparison once on the automotive AVD or a head unit. If the violet reads too close to the magenta search marker or the blue GPX track in practice, the hue can be adjusted in `stylesheets/include/route.oss` alone (the spec pins the contrast contract, not the hex).

---

## 40. Guardrails extracted from `ki_processing_failures.log` (2026-09-19)

Every entry of that log was processed on 2026-09-19 and removed; each action below is what prevents
re-making the mistake. The parenthetical names the artifact that should carry it. Items marked **[open]**
are not implemented yet; items referencing an existing TODO section are already tracked there and are
listed only so the guardrail is not lost.

Re-run the extraction with the `.pi/skills/process-failure-log` skill (gitignored, like the other
`.pi` skills — copy to `~/.pi/agent/skills/` for cross-project use).

### A. Harness / shell / Gradle invocations

1. Never plan on `python3`, `perl` or `tesseract` — all blocked by the lean-ctx shell allowlist (permanent).
   Use `jq`, `sed -i -E`, `openspec … --json` + grep; there is no OCR route without a config change.
   **[open]** (guidelines/Build.md — shell constraints)
2. Never `pkill -f "gradlew …"`: `-f` matches the calling tool's own command line, kills the shell, and the
   rest of the chained command (e.g. a `git commit`) silently never runs. Kill by PID
   (`ps -o pid,args` → `kill -9 <pid>`). **[open]** (guidelines/Build.md)
3. Long builds/tests (`./gradlew test`, `release`, full CMake) exceed the shell output cap — run detached
   (`nohup ./gradlew … > /tmp/x.log 2>&1 &`) and poll with short `tail`/`grep`. Live in `run-tests`;
   **[open]** for `build-app` and `release-build`.
4. Never start a second Gradle build against the same output directories while an aborted one is still
   running (the daemon keeps rewriting SBOM/assets; the next build then reports bogus parse errors).
   **[open]** (guidelines/Build.md)
5. A `BUILD SUCCESSFUL in 5s` / `… up-to-date` line is not test evidence — see §17. Quote executed-task
   count + elapsed time and verify counts from the result XMLs. (tracked: §17)
6. Verify artifacts by comparing them to their inputs (content/mtime), never by the existence of an output
   path: build-cache-restored APKs in `outputs/apk` and stale `build/outputs/sbom/*` look like product bugs.
   **[open]** (guidelines/Build.md)
7. Do not use `/tmp` as a workspace for large map imports (RAM tmpfs, ~7.7 GB quota); use a disk path.
   **[open]** (guidelines/Build.md)
8. Test-suite heap and class batching: both modules declare their fork budgets (`guidelines/Build.md` §6), so
   batching is a DIAGNOSTIC fallback only (e.g. to isolate one class), never the procedure. When it is used:
   `--tests "com.x.[A-E]*"` is NOT a glob in Gradle (one pattern per prefix or explicit class names), per-batch
   XMLs must be copied out because Gradle cleans `test-results` on each invocation, and coverage runs in a
   separate invocation from the test gate. A build-cache hit (`FROM-CACHE`, no test executor) is not test
   evidence — use `--rerun`.

### B. Editing discipline (edit/ctx_patch)

9. Multi-edit calls are atomic: one ambiguous or missing `oldText` rejects the WHOLE call, silently leaving
   the other edits unapplied. Re-read every changed region before compiling, and make each anchor unique
   with its distinguishing surrounding line. **[open]** (guidelines/Design.md or a skill note)
10. Never retype prose/code from memory into `oldText` — copy it from the read output. Never emit
    overlapping/nested `oldText` regions in one call; for pure insertions keep the entire matched block in
    `newText` and append to it. **[open]**
11. After an edit reports a missing `oldText`, re-read the file before re-issuing: a corrective pass can
    silently duplicate a helper (duplicate blocks then make exact-match edits ambiguous). **[open]**
12. One mutation per revert check — a combined mutation masks the other behaviour and the assertion becomes
    vacuously true. **[open]** (guidelines/Build.md — test evidence)

### C. Kotlin / Compose / car-app build traps

13. `while (isActive)` in a coroutine needs the explicit `kotlinx.coroutines.isActive` import.
    **[open]** (guidelines/Design.md)
14. `KProperty0.isInitialized` works only for `lateinit`; for `by lazy` state use a nullable `var` or a flag.
    **[open]**
15. Test coroutine extensions need a receiver: declare helpers as `private fun TestScope.foo()`;
    `advanceTimeBy(Long)` is an extension, `advanceUntilIdle` a `TestScope`/`TestCoroutineScope` extension.
    **[open]**
16. Lifetime background tickers (`while (true) { delay(1s) }`) must run on a real dispatcher
    (`Dispatchers.Default`) with test hooks — on the test scheduler they hang every `runTest`.
    **[open]**
17. Compose plurals containing `%d` need the count as an explicit format argument
    (`pluralStringResource(id, count, count)`). **[open]**
18. Never call `composeRule.setContent {}` twice in one test; collect all values in the single composition.
    **[open]**
19. Never nest two `verticalScroll` containers; give an embedded scrollable an opt-out parameter.
    **[open]**
20. Robolectric's Compose root is clamped to 320×470 dp — assert against the screen, not hardcoded sizes, and
    read the failing bounds from the assertion message. **[open]**
21. Robolectric's shadow canvas discards `drawBitmap`/`drawPath` (0 opaque pixels) and
    `@GraphicsMode(NATIVE)` does not fix it here — assert the bitmap contract (size/config/caching/no-throw)
    and leave visuals to on-device; do not add a second Robolectric sandbox config. **[open]**
22. Compose dropdown popups do not appear in `uiautomator` dumps — assert the field's text instead.
    **[open]** (guidelines/UI.md)
23. Java types from the JNI bridge: positional constructor args only (no named arguments), read the ctor
    before writing helpers (`RouteInstruction` has 5- and 10-arg forms), and update EVERY call site in the
    same change when such a ctor changes — stale test bytecode surfaces as `NoSuchMethodError`, not a
    compile error. **[open]**
24. Do not mock Kotlin `object` `@JvmStatic` methods (mockk cannot intercept them); stub the real
    `applicationContext` and the Java delegate the object calls (`dagger.hilt.EntryPoints`). **[open]**
25. `Notification.actions` is nullable (`actions?.isEmpty() != false`); use `getIcon()` (the Kotlin field is
    deprecated and breaks warning-free builds). **[open]**
26. Every user-facing number/coordinate formatter takes an explicit locale
    (`String.format(Locale.US, …)`) — default-locale formatting broke tests and reads ambiguously in German
    (the `:core/CoordinateFormat` seam is the fix for that family, `fix-comma-decimal-coordinate-entry`).
    `"%.5f".format()` rounds, it does not truncate. **[open]**
27. `NavigationTemplateMapper.distanceForDisplay` reports display units — assert `displayDistance` plus
    `displayUnit`, not metres. **[open]**
28. Verify constructor-argument edits against the file's imports in the same pass (a rename dropped a
    still-used import and invented a non-existent class). **[open]**
29. For car-app screens, check the real API surface first (getter names, resolved lifecycle version, no
    `Lifecycle.getObservers()`); drive lifecycle through `dispatchLifecycleEvent`, ON_CREATE + ON_START
    before ON_DESTROY. **[open]**

### D. Test / verification discipline

31. Never pace a unit test off a timer or a debounce (`Thread.sleep`): control the loop
    (`asyncLoopsEnabled = false/true`) and land a frame deterministically (`renderFrame()`).
    **[open]** (guidelines/Build.md)
32. After a boundary-signature semantic change, grep the tests for the observable field first
    (`lastRenderMag` now holds the raw scale → compare `2^level`). **[open]**
33. Compute `log2` expectations with a calculator, not mentally. **[open]**
34. Native index test fixtures must be FULL (non-eco) imports — `--eco true` skips the POI indexes.
    **[open]**
35. `DBThread` loads databases sequentially: wait for two consecutive identical results before asserting.
    **[open]**
36. Verify `md5` after any `git stash` cycle before rebuilding — a stash+pop silently reverted a submodule
    patch and the "patched" host library was unpatched. **[open]**
37. Coverage: Kover/JaCoCo per-class numbers are meaningless for Robolectric-only classes (sandbox
    classloader, §15) — use revert-checks as evidence; never apply Kover to a Kotlin-plugin-free module
    (use the Gradle `jacoco` plugin there). (tracked: §14, §15)
38. Test-harness flakes of unknown origin get an entry with the rerun evidence instead of a silent retry.
    `FavoritesSheetReorderComposeTest` was the worked example: the 5 s bound was never the problem —
    the awaited state came from `Dispatchers.Default` and the fix pinned the dispatcher
    (`guidelines/Build.md` §6).

### E. On-device / emulator preconditions

39. Never install an ABI-filtered APK (`-Pandroid.injected.build.abi=…`): packaging strips the other ABIs,
    the install succeeds and the app dies at `System.loadLibrary`. Verify with `unzip -l <apk> | grep <abi>`
    and check the APK mtime against the newest source edit before an on-device run. **[open]**
    (guidelines/Build.md — on-device verification)
40. Emulator GPS cannot inject a bearing: `geo fix` has no bearing argument (always `bear=0.0`), and NMEA RMC
    is dropped by FusedLocationProviderClient ("too close / too fast"). Do not plan bearing/heading on-device
    tests on a GMS emulator — cover them with unit tests. **[open]**
41. Headless emulators need `-dns-server 8.8.8.8` (broken DNS → "Unable to resolve host" while raw IP works) —
    compare `adb shell ping` against the app's network code before debugging the app. **[open]**
42. Check which maps are already installed BEFORE attempting a catalog download — `adb install -r` preserves
    data. **[open]**
43. After toggling `location_mode`, re-send `geo fix` (Fused may need provider re-registration). **[open]**
44. `adb shell input text` goes through the active IME (GBoard rewrote `Erbstollenstrasse` →
    `Er Stollenstraße`): disable the IME for the replay and assert the field's actual value from the
    `uiautomator dump` before interpreting results. **[open]**
45. The AAOS/car AVD is not usable for on-device steps in a headless agent session: car system-UI ANRs swallow
    `input tap`, `uiautomator dump` returns an empty hierarchy, `geo fix` is answered OK but no fix reaches the
    app, and the host `RendererService` disconnects ~40 s after launch. Plan car verification for an
    interactive window or a real head unit, and state the blocker instead of burning a session.
    **[open]** (guidelines/Build.md + the OpenSpec apply guidance for car changes)
46. Before an apply/verify pass, check for a concurrent writer in the tree (`find <module> -newermt "-10 minutes"`,
    active Gradle clients) — one writer per tree; if a peer is mid-edit, quote the evidence already collected and
    stop. **[open]** (AGENTS.md — parallel sessions)

### F. Native / libosmscout

47. Any change inside an `#ifdef OSMSCOUT_HAVE_LIB_MARISA` block must be verified in BOTH configurations — the
    Android build always defines it (vcpkg provides marisa), so CI's non-Marisa path is invisible here.
    Reproduce locally: insert `#undef OSMSCOUT_HAVE_LIB_MARISA` after the include block of
    `OSMScoutClient.cpp`, build `:app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a`, then remove
    the `#undef`. **[open]** (guidelines/Build.md — native verification)
48. Native test binaries are not directly executable (allowlist): run them through `ctest -R <Test>
    --output-on-failure` from the build directory. `ctest` does not build — `ninja -C <build> <Target>` first, and
    filter `ctest -N` output for `Test #` lines. For the meson `hostbuild/` directory this does NOT work: it has no
    `CTestTestfile.cmake`, so `ctest` answers "No tests were found!!!" even for a registered, green test — use
    `meson test -C hostbuild "<test name>" --print-errorlogs` there, and note that meson prefixes its target names
    with the subdirectory (`Tests/FavoriteStoreTest`, not `FavoriteStoreTest`), with one throwaway `ninja` invocation
    needed after a `meson.build` edit so the regeneration lands before the target lookup
    (found 2026-09-20, `fix-favorite-store-write-race`).
    **[open]**
49. `System.loadLibrary` needs the plain-name `.so` — versioned `.so.1` symlinks are not found. **[open]**
50. Plain `openDatabase(containerRoot)` wipes the DBThread (the root has no `types.dat`); load a container of maps
    through the builder's map-lookup scan. **[open]**
51. The `:osmscout-client-java` Gradle JAR excludes `OSMScoutClient.java`/`Builder` — never feed it to JavaScout
    Maven (a stale `~/.m2` JAR breaks the signature); build the JAR from the submodule `java/` sources.
    **[open]**
52. JavaScout Maven tests need `JAVA_HOME=java-21` on this machine (JUnit 5.10.2 on Java 26 discovers but
    executes 0 tests). **[open]**
53. Stale meson host builds: `sed` the ninja link line to a stub path before rebuilding (`/usr/lib` is not
    writable, no sudo). **[open]**
54. Before pushing a submodule branch, run `git ls-remote origin <branch>` (not the local remote-tracking ref)
    immediately before the push, and let ONE session own the submodule update — two sessions created the same
    JNI commits and force-rewrote `naviveylin-local` (reconciled by a merge, no force-push, nothing lost).
    **[open]** (AGENTS.md — submodule workflow)
55. Stylesheet hex literals are lowercase-only (`Color::GetHexValue` asserts), and a stylesheet defect does NOT
    fail the build: run the app once after the first stylesheet change and grep
    `adb logcat -s NaviVeylin | grep -i "style error"`. A rejected stylesheet no longer crashes the renderer —
    the client keeps the previous style and reports the failure (closed 2026-09-27), so the user now sees it;
    the silent-build gap above stays.

### G. Design / scope discipline

56. Verify the premise before building a change on it: the tile-path condition (pinned by
    `TileCacheRenderTest`), the real pixels-per-degree the renderer produces (DPI × `cos(lat)`, cross-checked
    through `ProjectionUtils` rather than re-derived with the formula under test), and the "single source of
    truth" location of a dedup change (removing the duplicate must not remove the only instance). **[open]**
    (guidelines/Design.md)
57. Log BOTH sides of a seam (pending vs displayed frame, both path counters) in one grep-able line — five
    changes shipped while the AA follow defect was live because no log line compared the two frames.
    **[open]** (guidelines/MapRendering.md)
58. When two layers can both own a transition, decide the pacing (render vs fix) before coding; never call a
    stateful controller twice for a check-then-use pair; scope a renderer-wide rule with an explicit opt-in
    flag. **[open]** (guidelines/Design.md)
59. Write down which coordinate frame each number lives in before comparing (pre-shift projection vs post-shift
    visible band). **[open]**
60. In ViewModel tests, assert pre-state through an entry path that does not mutate the snapshot field
    synchronously before the collector resumes. **[open]**
61. Initialize progress state to the "0 % traveled" invariant (`remainingDistance = totalDistance`), not the
    data-class default; state machines with wall-clock state need a "never set" sentinel and an explicit
    first-event branch. **[open]**

## 49. One full car render allocates ~15 MB of transient buffers — app-side FIXED by `fix-render-buffer-reuse` (2026-09-26), native half FIXED by `reduce-render-peak-memory` (2026-09-27); the `Graphics` footprint and the growth measurement stay open

- **Observation** ℹ: a full render at the 1.2× overrun size walks four full-size buffers — C++
  `std::vector<uint32_t>` (`OSMScoutClient.cpp` render entry), the Cairo RGB24 surface, the Java
  `jintArray` from `NewIntArray`, and the `Bitmap` in `MapRenderUtil.renderToBitmap` — plus a
  per-pixel C++ conversion loop. At 1296x720 that is ~3.7 MB per buffer, up to ~5 renders/s while
  the extrapolation loop is clamped, i.e. tens of MB/s of churn (Java heap + native graphics).
  The car overlay draw (`AutoMapRenderer.drawGpsMarker`/`drawDestinationMarker`) also allocates
  `Paint`/`Path`/`LinearGradient`/`BlurMaskFilter` per frame at ~30 fps.
- **Mitigation landed in `fix-aaos-host-crash`** ✅: the full-render request interval is now bounded
  by the measured render duration with one render in flight (design D7), which caps the rate rather
  than the per-render cost.
- **Native half fixed ✅ by `reduce-render-peak-memory`** (2026-09-27; spec `osmscout-jni` — Render
  entry point writing into a caller-supplied pixel buffer; spec `render-performance` — A render
  writes into caller-owned pixel storage, Per-render transient allocation is bounded): the JNI
  bridge gained a buffer-taking entry point (`OSMScoutClient.renderInto`, a direct `ByteBuffer`) and
  the allocating one is unchanged; both run **one** shared render body, so they cannot render
  different frames. The frame is written straight into the caller's pooled storage, so the C++
  `std::vector<uint32_t>`, the `jintArray` and the per-pixel BGRx→ARGB loop this entry names are gone
  from the pooled render path — at the car's overrun size that is the ~37 MB of per-render transient
  buffers. Format contract: `0xAARRGGBB` ints, stride `width*4`, native byte order, which is what
  `Bitmap.copyPixelsFromBuffer` reads. Tests: the buffer-path frame is byte-identical to the
  allocating path's, no frame-sized buffer is allocated after the first render of a size, a failed
  render leaves the caller's target untouched, and the pool's bound/refusal are pinned.
- **App-side half fixed ✅ by `fix-render-buffer-reuse`** (2026-09-26; spec `render-performance` —
  Reusable render target for map frames, A frame handed to the display layer is never overwritten;
  spec `auto-map-renderer` — Marker drawing allocates no per-frame objects): `core/RenderBitmapPool`
  now owns the ARGB_8888 render targets (hand out → release, ≤2 free per size class, ≤2 size classes,
  double release refused), `MapRenderUtil.renderInto` writes into a caller-supplied target, and both
  renderers use it — the phone's tile-composition and full-render targets (`MapRenderer`) and the
  car's displayed overrun frame (`AutoMapRenderer`, which therefore holds two distinct slots while a
  render is in flight, as its blit loop requires). The car's marker draw objects
  (`Path`/`Paint`/`BlurMaskFilter`/`LinearGradient`) are built once per presentation/density/bounds
  instead of per drawn frame. Tests: `RenderBitmapPoolTest` (7), `MapRendererSmokeTest` +3 cases,
  `AutoMapRendererPooledTargetTest` (4), `AutoMapRendererMarkerDrawCacheTest` (4), with three
  revert-checks quoted in that change's tasks. This entry is removed when the change is archived.
- **Still open ✗ — the native half (the bigger two of the four buffers)**: the C++ `argbPixels`
  `std::vector<uint32_t>`, the Cairo RGB24 surface and its per-pixel conversion loop, plus the JNI
  `jintArray`, are still allocated per render (`OSMScoutClient.cpp:1793-1818`). Removing them needs a
  new JNI entry point that renders into a caller-owned buffer (submodule commit on `naviveylin-local`
  + gitlink bump + `:osmscout-client-java` override, per `AGENTS.md`). Deliberately out of scope in
  `fix-render-buffer-reuse` (its Non-Goals).
- **Still open ✗ — the measurement**: no device was attached (`adb devices` empty) and the AAOS AVD is
  unusable for headless sessions (§40.45), so the `dumpsys meminfo` before/after comparison did not
  run. Recipe for a session with a device: note native + Java heap and the `MAP` render count, drive
  ~10 minutes (stationary + repeated identical route, per §65), compare against the pre-change build;
  the pool changes churn, not peak, so expect dampened heap saw-teeth rather than a lower peak.
- **Original fix candidate (2026-09-21, now half taken)**: reuse a caller-supplied pixel buffer across
  renders (bitmap + `int[]`), and hoist the overlay paints/paths into fields. Measure with
  `dumpsys meminfo` before/after; a phone render-path change, not a car-only one.

## 51. The host-crash mechanism: an app-process death takes the templates host down — Found 2026-09-21 (triage frame for the "AA crashes while NaviVeylin drives" report)

- **Mechanism** ℹ: the ordering is proven (pitfall in `guidelines/Build.md` §10) — the app is force-stopped → the host's queued template
  operation runs against an invalidated `CarHost` → `IllegalStateException: Accessed the car host
  after it became invalidated` in `com.google.android.apps.automotive.templates.host:renderer_service`
  (FATAL). The app process going away is *sufficient*; no app code has to run at that instant. So for
  any "AA/AAOS host crashed" report the first question is **what killed or blocked the app process**,
  not which host API was called.
- **Second confirmed path** ℹ: `RemoteUtils.dispatchCallFromHost` (car-app 1.7.0 sources,
  `androidx/car/app/utils/RemoteUtils.java:140-158`) runs every host call on the app's main thread,
  answers the host with a `FailureResponse` when the callback throws — and then **rethrows** on the
  main thread. A throwing host callback therefore kills the process *and* reports the failure to the
  host, which is what makes an app-side exception look like a host fault from the driver's seat.
- **App-process killers still open** ✗, ranked: (1) §49 — ~15 MB of
  transient buffers per full render with up to three live renderers (lmkd kill). ~~the unsynchronised
  `ClientData::knownPaths` vector in `openDatabase` (native SIGSEGV)~~ is **fixed** by
  `fix-native-database-open-race`; ~~the main-thread native work of §52/§53~~ was fixed by
  `fix-aaos-host-crash`; ~~any uncaught exception on a host path~~ is now covered end to end by
  `fix-car-host-mutation-guards` (see the entry below).
- **The remaining unguarded host paths** ✗ (found 2026-09-22 while triaging the "AA crashes after
  seconds to minutes" report, **fixed** by `fix-car-host-mutation-guards`): the 2026-09-21 work
  guarded host *callbacks*, the template builds and the enumerated host sends, but three mutation
  paths were still open, and each one is a process death → host FATAL through the mechanism above.
  (1) **Template-row click listeners** did `ScreenManager.push` directly — seven sites plus the
  back actions — inside the library's `dispatchCallFromHost`, which answers the host with a
  `FailureResponse` and then **rethrows on the app's main thread**
  (`RemoteUtils.java:140-158`, car-app 1.7.0). (2) **The session's own coroutines**
  (`observeJob`/`errorJob`/`tripJob`, `showNavigationScreen`, `showRootScreen`, `restoreDrivingMode`,
  `showError`) called `getCarService(ScreenManager).push/popToRoot` on a
  `CoroutineScope(SupervisorJob() + Dispatchers.Main)` with **no** `CoroutineExceptionHandler` —
  only `CarScreenObservations` had one, so the guarantee was asymmetric in exactly the places that
  mutate the host. (3) **The session lifecycle callbacks** (`onStart`'s host sync, `onDestroy`'s
  cleanup) were not confined, so a throw escaped the lifecycle observer into the library's dispatch.
  Fix: one guarded seam (`CarHostGuards.kt`: `guardedHostCall`, `armScreenPush`,
  `armShowOnMapSwap`, `dismissErrorNotice`), the shared fault handler on the session scope, every
  car screen's own scope (and the renderer's), and every `ScreenManager` mutation routed through it;
  click paths *arm* the navigation and build the target screen after the callback returns. Two
  second-order defects came out of the same review: `showError`'s delayed `popToRoot()` **popped the
  navigation screen** off the stack while the flag claimed it was shown (guidance silently gone,
  `showNavigationScreen` early-returning forever), and the session recorded a screen push *before*
  the host accepted it. Both are fixed by the same change (`SessionScreenStack`). Found and left
  for later: **a confined fault in the renderer's own loops ends that loop** — the map would freeze
  instead of killing the process, and there is no restart path for a dead render loop yet; and
  **the `remove(notice)` dismissal needs the notice to still be on the stack** (a no-op otherwise,
  which is intended but means a notice skipped while stopped is only removed by the next started
  sync).
- **Fix in flight ⏳ (2026-10-02) for the loop-liveness residual:** change
  `fix-car-render-loop-restart` confines every iteration of the renderer's three loops
  (`RenderLoopSupervisor.runIteration`, so a fault skips one frame/tick and the loop keeps running),
  records it under `Diag/MAP`, re-creates the renderer after `3` consecutive faults within `60 s`
  (at most `2` re-creations per started screen period, the gate's state slots replayed and the
  session's surface re-attached by the screen) and shows `map_unavailable` when the budget is spent.
  Evidence: `:auto` 754/0 (new `RenderLoopSupervisorTest` 11, `AutoMapRendererLoopConfinementTest` 5,
  `MapUnavailableOverlayTest` 4, `RendererGateTest` 22, `MapTemplateFactoryTest` 9,
  `DetailsScreenTest` 31), `:app` mobile 1468/0 and automotive 1468/0, `:core` 429/0, both debug APKs
  with all three ABIs, plus three revert-checks recorded in the change's tasks; the on-device pass
  ran on the AAOS AVD on 2026-10-02 (frames + background round trip + degraded-state transition, no
  fault entries, no `HOST` rejections; a screen push/pop could not be driven there).
  **The `remove(notice)` dismissal residual above stays open and is not part of that change.** This
  entry is removed when `fix-car-render-loop-restart` is archived.
- **Evidence recipe** ℹ: `adb logcat -b crash` for the host process, plus
  `adb logcat -d | grep -E 'onPackageUpdateFinished|onHandleForceStop'` for the app and
  `adb logcat -d | grep 'Diag/HOST'` for what the app last sent. A package replace/force-stop in the
  window means the harness artifact (`guidelines/Build.md` §10); no such line means an app-side defect.

## 56. Trip publishing is the one host sender that deliberately keeps firing while the session is stopped — Found 2026-09-21 (same review)

- **Observation** ℹ: `NavigationSession.kt:573-579` collects every navigation-state emission and calls
  `NavigationManagerController.publishTrip` (`NavigationManagerController.kt:88`), which has no
  lifecycle gate — deliberately, so the cluster/heads-up trip keeps updating while backgrounded.
  `NavigationTemplateMapper.hasTripChanged` (`:337-350`) compares `etaMillis / 1000` and rounded
  distances, so while driving this reaches the host's navigation service at roughly 1 Hz, each update
  carrying a maneuver icon in the `Trip`.
- **Why it matters** ℹ: this is the only host-facing send not bounded by `SessionHostGate`, and it runs
  exactly while the host may be tearing the app's surface down. The controller already treats a
  rejected `updateTrip` as "host session ended" (`onFailure { navigating = false }`), so the risk is
  the window before the first rejection.
- **Fix candidate**: either gate it on the session being started (accepting a stale cluster while
  backgrounded) or stop at the first rejection until the next `onNavigationStarted`. Decide with an
  AVD run counting `HOST` trip lines against host stability (`guidelines/Build.md` §10 baseline).

## 57. `DetailsScreen` observers are init-scoped and keep rendering while stopped — Found 2026-09-21 (same review)

- **Observation** ℹ: `DetailsScreen` starts its favorites, basemap and position collectors in `init`
  into `loadScope` (`DetailsScreen.kt:189-219`), cancelled only in `onDestroy` (`:266`). While the
  screen is stopped (backgrounded, or covered by a pushed screen) they keep calling `invalidate()` —
  a library no-op while the screen is not at least STARTED (`androidx/car/app/Screen.java:102-106`) —
  plus `invalidateData()` and `setGpsMarker`, which do request full native renders. Same defect class
  as the observer leak fixed by `fix-car-screen-observer-leak` on the three map screens, but here it
  is "runs while invisible" instead of "grows per start".
- **Fix candidate**: move them onto the same per-start tracking that change introduces, or stop them in
  `onStop` and restart in `onStart`.

## 78. Overrun-window offset math lives in `FollowPrediction` and the follow overlay path is not unified — Found 2026-09-24 (while fixing the phone pan tracking)

- **Debt** ℹ: `FollowPrediction.displayOffsetPx` is now the single offset helper for follow scrolling,
  the pan display window and the renderer's coverage predicate (`MapRenderer.overrunWindowCovers`),
  but it still lives on `FollowPrediction` (a prediction class) and its `anchor` parameter carries the
  follow anchor, so the name misleads. The pan/coverage use is the default (center) anchor — the
  absolute overrun-window shift.
- **Fix candidate**: move it (with `DisplayOffset`) into a neutral home, e.g.
  `core/OverrunWindow.kt` as `OverrunWindow.shiftPx(...)`, and update the follow call site, the pan
  path (`MapCanvasScreen.panWindowOffset`), the renderer predicate and the tests
  (`FollowAnchorFramingTest`, `MapPanDisplayWindowTest`, `MapPanHandlerTest` on the AA side).
- **Debt** ℹ: the follow overlay projection expresses the drift through the anchor center of the
  displayed position (geo), while the pan path translates the overlay layer by the clamped offset
  (`markerDisplayShiftPx`). Both are correct today (and mutually exclusive: a pan disengages follow),
  but two mechanisms for one rule invite a double application. The unified end state is one derived
  **displayed viewport** (frame viewport shifted by the clamped offset via `screenToGeoRotated`) that
  every overlay projects against in both modes.
- **Why deferred** ✗: the follow pipeline (and its blit offset call site) was being edited by the
  in-flight change `fix-phone-follow-blit-anchor-mismatch`; unifying then would have mixed two
  changes in one verified path.
- **Not caught** ✗: `MapPanDisplayWindowTest`/`MarkerDisplayShiftTest` pin the pan-side contract; the
  duplication itself has no test, only the rule that a follow-mode overlay shift must stay zero.

## 79. The Android bridge override trails the submodule's Java favorites API — Found 2026-09-28 (while adding the cross-group favorite move)

- **Debt** ℹ: `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`
  shadows the submodule's Java source (that file is excluded in `osmscout-client-java/build.gradle.kts`)
  and is one API family behind it. `getFavoriteFileFormatVersion` and
  `isFavoriteFileFormatSupported` are declared in
  `app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClient.java`
  (submodule, added by `530ac8768`) and implemented in the linked JNI (`OSMScoutClient.cpp`), but are
  absent from the override, so the app cannot reach them. `moveFavoriteToGroup` was declared while landing
  `move-favorite-between-groups`, `moveGroup` while landing `reorder-favorite-groups` and
  `moveStarredFavorite`/`getStarredFavorites` while landing `order-starred-favorites`; the two file-format
  ones stay unreachable (nothing reads the format version).
- **Fix candidate**: declare the missing natives in the override, and add a buildSrc/CI gate that compares
  the override's `native` declarations with the submodule's Java source and fails on a mismatch — the drift
  is silent otherwise (no compiler error, no failing test: only a `NoSuchMethodError` at the call site).
- **Why deferred** ✗: the in-flight change `move-favorite-between-groups` needed exactly one of them;
  widening it would have mixed an API-sync refactor into a feature change.

## 103. `FavoritesScreen` (car) is the one screen still outside the `CarScreenObservations` pattern — Found 2026-09-29 during `order-starred-favorites`
- **Debt** ℹ: `auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt` collects its favorites, group-order
  and starred-order flows in one `combine` inside `init`, on `carScreenScope("FavoritesScreen")`, and
  cancels it through its own `onDestroy` lifecycle observer. Map, Navigation and FreeDriving screens use
  `CarScreenObservations` plus a `<Screen>Observations` class with started-period lifetime (change
  `fix-car-screen-observer-leak`, spec `auto/screen-observation`). The screen has no accumulation bug
  today — it launches one collector per screen instance and destroys the scope with the screen — so this is
  consistency, not correctness.
- **Fix candidate**: give `FavoritesScreen` a `FavoritesScreenObservations` owned by
  `CarScreenObservations` (favorites map, group order and starred order as its observations),
  `start()`/`stop()` from `onStart`/`onStop`, and a test for the started-period lifetime like the other
  screens' observations tests.
- **Why deferred** ✗: `order-starred-favorites` needed one more input on that existing collector; migrating
  the screen's whole observation lifetime was scope its specs do not require.

## 106. The AAOS AVD's car session lives in user 10 while adb reaches user 0 only, so its favorites storage cannot be seeded — Found 2026-09-29 on `emulator-5554` (automotive distant-display AVD) during `order-starred-favorites`

- **Observed** ℹ: `pm list users` shows `0:Fahrer` and `10:Driver`, `am get-current-user` is `10`, and the car
  session's own warmup log writes to `/data/user/10/com.framstag.naviveylin/files/...`. `run-as`,
  however, resolves to **user 0** (`ls -la files/` reports `u0_a235`), so a fixture written with
  `run-as … cat > files/favorites.json` lands in the `Fahrer` profile and the car screen shows an empty store.
  `run-as --user 10` is rejected (`run-as: unknown package: --user`) on the API 33 toybox, `adb root` fails
  (`adbd cannot run as root in production builds`), and shell-initiated `am start --display 1` is a
  `SecurityException`. **What that blocks:** seeding starred favorites for a car-side pass — the car UI can add
  and remove a favorite but has **no star action**, and the phone UI cannot stand in because the distant-display
  mirror owns display 0 and re-asserts `CarAppActivity` within seconds of `MainActivity` starting.
- **Workarounds that did work** ℹ: the car's *own* UI can create an unstarred favorite (map → POI search →
  result → `Zu Favoriten hinzufügen`), which is enough for the mode-split checks (`Alle Favoriten` keeps its
  group section, `Markierte Favoriten` shows the nothing-starred hint). `tesseract` on `adb exec-out screencap`
  reads the car surface (it exposes no accessibility nodes, so `uiautomator dump` sees an empty window).
- **Fix candidate**: use a **userdebug/eng** AAOS image (`adb root` then write the car session's own
  `files/favorites.json`), or an AAOS AVD whose car session runs in the current user, for any later car-side
  favorites verification. A debug-only test hook that seeds the store would remove the dependency on root.
- **Also observed** ℹ: `am force-stop` of the app while its car session is live crashes the **host's** renderer
  process — `FATAL EXCEPTION: main`, `Process: com.google.android.apps.automotive.templates.host:renderer_service`,
  `IllegalStateException: Accessed the car host after it became invalidated`. Not an app defect (the host's own
  process, its own invariant), but it means a force-stop is not a neutral way to restart the app on this AVD:
  the car screen returns to the launcher and needs a fresh launch.

## 108. Three surfaces of the i18n sweep could not be seen on a device — found 2026-09-30 while landing `fix-remaining-untranslated-strings` (task 5.2)

- **Device-verified** ✅ that pass: the favorites group-card count reads `0 Favoriten` / `2 Favoriten` / `3 Favoriten` on `emulator-5554` (API 37, de-DE) with the change's `mobileDebug` APK installed (`lastUpdateTime 2026-09-30 21:40:30`) — the German plural resource, where the pre-change build showed `2 favorites`. The same dump shows the German resource set loading in that build (`Was ist hier?`, `Ort suchen…`, `Favoriten`, `Aktuellen Kartenstandort hinzufügen`).
- **Not verified on device** ⏳ (unit + revert-check only):
  1. **The generic details title** (`Standort`) — the `geo:` deep link and the map long-press both land in the **`CandidatePickerSheet`** (`Was ist hier?`) on this build, whose candidates are objects; no coordinate-labelled row appeared at the tapped point, so `LocationDetailsDialog` (the sheet whose title the change made localized) was never reached. A coordinate typed into the search field produced no result row, and `ENTER` left the search view rather than committing the coordinate.
  2. **The notification titles** — starting free driving needs the on-map `Freie Fahrt starten` control (bounds `[955,1673][1018,1736]` in the map dump); the tap did not reach it before the session left the map, and no `com.framstag.naviveylin` notification appeared in `dumpsys notification`.
  3. **The map-download channel name** — `dumpsys notification --noredact` still shows `NotificationChannel{mId='map_download', mName=Map Download}` because `MapDownloadService` has not started since the install: per design D4 the name is re-applied on the next `createNotificationChannel`, i.e. the next download start. The German name is `values-de`-parity-verified, not observed.
  4. **The car favorites titles** — fixed 2026-09-30 by `fix-remaining-untranslated-strings` (`R.string.favorites` / `R.string.starred_favorites`, German titles asserted at unit level), but never seen in German: no AAOS AVD or head unit attached; also the outstanding car-side German run for `fix-comma-decimal-coordinate-entry`'s coordinate row.
- **Fix candidate** (a verification pass, not a code change): with a car surface attached, run §10 of `guidelines/Build.md` against the release build for (1)-(3) — a coordinate `geo:` link or long-press with a coordinate candidate, free driving from the phone, and a map download to re-create the channel — plus the car favorites screens in German. The recipes that worked here are worth reusing: `uiautomator dump <path under /data/local/tmp>` (a `/sdcard` path is refused by this harness), `exec-out screencap -p` + `tesseract … -l deu tsv` for coordinates, and the map screen's German `content-desc` nodes (`Favoriten`, `Ort suchen`, `Freie Fahrt starten`) for tap targets — the Compose canvas exposes almost no text nodes.

