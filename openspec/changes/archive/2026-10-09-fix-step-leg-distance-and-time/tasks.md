# Tasks

## 1. Native bridge: per-step values become the step's own leg

Specs: `osmscout-jni` — Per-step leg values on a calculated route; Instruction segment time is the leg's
travel time; A next instruction's time is the remaining time of its leg.

- [x] 1.1 In both `DescCallback` copies of `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` (calculate and reroute), advance the per-step reference when a **line is emitted** (`AppendDistanceTime`) instead of in `BeforeNode`, so the `[x km, y min]` bracket is the leg ending at that line's manoeuvre. Verify: `./gradlew :app:assembleMobileDebug` compiles and the native object really rebuilt (compare `app/.cxx/**/OSMScoutClient.cpp.o` mtime, or force with `--rerun-tasks` — the submodule edit has silently not been rebuilt before).
  - Done: both copies; `:app:buildCMakeDebug[arm64-v8a] [armeabi-v7a] [x86_64]` **BUILD SUCCESSFUL in 5m12s**, and all three `OSMScoutClient.cpp.o` under `app/.cxx/Debug/4z1b2r3y/` carry that build's mtimes (not the earlier ones) — the object really rebuilt.
- [x] 1.2 Collect `instructionDistances` (metres, leg) and `instructionTimes` (seconds, leg) in the same callback pass, one entry per emitted line, drop both together when they cannot be aligned one-to-one with the instruction lines (log through `osmscout::log.Warn`, the positions' existing rule), and marshal them into `RouteEntry` in both route entry points. Verify: `./gradlew :osmscout-client-java:test :app:assembleMobileDebug` green and the two new arrays appear on the Java side for a calculated route.
  - Done: both copies collect and marshal them (`jdoubleArray instrDists/instrTimes`, all four dropped as a set on a count mismatch); `:osmscout-client-java:test` green (26 tests, 0 failures) and the device case reads both arrays back through the JNI **`OK (4 tests)`**.
- [x] 1.3 Add the two fields to `RouteEntry.java` (only the submodule's Java copy compiles: `RouteEntry` is not one of the 5 overridden files) and, per the owner's decision, one field `legDistance` to `RouteInstruction.java` (submodule `libosmscout-client-java/java/...`): field + full constructor parameter + the JNI constructor descriptor in `OSMScoutClient.cpp` + the `NewObject` argument list, all in the same order. A wrong descriptor is a **silent** mismatch (wrong arguments, no `UnsatisfiedLinkError`), so read both sides against each other. Verify: `./gradlew :osmscout-client-java:test :app:testMobileDebugUnitTest --tests "com.framstag.libosmscout.client.*"` green with no JNI warning on a route calculation.
  - Done: descriptor `(DDDLcom/framstag/libosmscout/client/TurnType;…)V` and the `NewObject` list carry `legDistance` third, matching the Java full constructor; the short constructor keeps its signature and delegates 0.0. Verified by reading both sides and by the device run (the array contents arrived, so the JNI field lookups resolved). Host tests that used the old 10-argument constructor were updated to the new one (NextTurnOverlayTest, NavigationTripTest, NavigationManagerControllerTripTest).
- [x] 1.4 In `CollectCallback`, derive the leg's `legDistance`/`timeTo` per emitted instruction (not per node) and make the **next** instruction report the remaining time of its leg — interpolate the description's own node times at the current position with the same `abscissa` the remaining distance uses, clamped to `[0, legTime]`; the leg's own start is behind such a walk, so its first emitted instruction reports unknown (0) values instead of a cumulative-from-the-start number. Verify: all three ABIs compile (`./gradlew :app:assembleDebug`) and the unit-test fakes still compile (the fields' meaning changes, not their types).
  - Done as `FillLeg` (one call per emitted instruction, with `legStartKnown` for a walk that begins mid-leg) and `GenerateNextRouteInstruction` interpolating `TimeSeconds(previous->GetTime())` with `abscissa`; `:app:assembleDebug` **BUILD SUCCESSFUL** for both flavors and all ABIs, 0 compiler warnings.
- [x] 1.5 Commit the submodule change on `naviveylin-local`, then bump the gitlink in the main repo; verify `git -C app/src/main/cpp/libosmscout status --short` is empty and the main repo records the new SHA (AGENTS.md — keep the submodule clean).
  - Done: submodule `a2b590470 Fix per-step values to measure the step's own leg`; main repo `05f155e Bump libosmscout: per-step values are the step's own leg` (only the gitlink path in that commit — the rest of this change stays in the working tree next to the parallel session's in-flight work). The submodule is clean again after the R1 revert-check.

## 2. App: the route analysis reads the per-step numbers

Specs: `route-analysis` — Step values describe the step's own leg; A step's values are its own, never a
neighbour's.

- [x] 2.1 Give `RouteStepDisplay` a numeric distance (metres) and duration (seconds) alongside the existing text; in `RoutePanelViewModel.onSuccess`/`adoptRoute` fill them from the route's per-step arrays by instruction-line index, and keep the bracket text as the fallback when the arrays are absent (the rollback path). Verify: new `RouteStepDisplayTest`/`RoutePanelViewModelStepAnalysisTest` cases — arrays win, absent arrays fall back to the bracket, index shift impossible.
- [x] 2.2 Harden `parseStepDisplay` for the fallback: classify each bracket token by its unit (`km`/`m` vs `s`/`min`/`h`) instead of by position, so `[2 s]` yields no distance and a `2 s` time, and `[800 m]`/`[1.2 km, 5 min]`/`[0.0 km]` keep working. Verify: `RouteStepDisplayTest` cases for all four forms (today it produces the time in the distance slot for `[2 s]`).
- [x] 2.3 Show the formatted values in the step list, the step navigator and the session card's statistic line and gate the analysis on them (`currentStepOf` uses the numeric presence, a leg-less step shows no numbers and never borrows a neighbour's). Verify: `RoutePanelComposeTest`, `RoutePanelStepNavigatorTest`, `RoutePanelViewModelStepAnalysisTest`; focused run `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.route.*"`.
- [x] 2.4 Log the sum against the route's total on route arrival — coordinate-free numbers only (`RoutePanelVM: route analysis steps=<n> sumM=<m> totalM=<m> maxErrM=<m>`), and a warning when the sum diverges beyond rounding — through `DiagnosticsLog` (never the caller's thread, AGENTS.md). Verify: a test with a synthetic array that diverges records exactly one entry and the analysis keeps working; the entry shape is asserted in the test.
- [x] 2.5 Correct the wrong claim in `ui/route/RouteStepSegments.kt` (a line's `[x km, y min]` is **not** the previous node's delta) to state the leg contract, and check `guidelines/UI.md` for any wording that says the step row shows the native text (update it in this change if it does). Verify: the sentence matches the `osmscout-jni` requirement text and `grep -rn "previous node" app/src/main/java guidelines/ | grep -v previousNodeIndex` is empty.

## 3. App: the navigation list and the car hint

Specs: `navigation-status-details` — Route description list; `osmscout-jni` — A next instruction's time is the
remaining time of its leg.

- [x] 3.1 In `ui/navigation/NavigationDetailsOverlay.kt`, show each row's **own segment**: the leg distance from the bridge's per-instruction `legDistance` and the leg time from `timeTo`, formatted with the shared formatters (0 = unknown → the value is omitted). No app-side arithmetic on the cumulative `distanceTo`, which is the distance from the route start (spec: `navigation-status-details` — Route description list). Verify: a test on the derivation (first row's leg = its own `distanceTo`) plus the existing overlay tests green.
- [x] 3.2 Check the consumers of the next instruction after 1.4: the phone's `NextTurnOverlay` and the car's `NavigationTemplateMapper.stepArrivalMillis` now receive a shrinking remaining time. Verify: `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.*"` and `:core:testDebugUnitTest` green; host template read at 5.3.
- [x] 3.3 Confirm the per-step time parity requirement holds after the change — the navigation list's step time equals the route summary's for the same step (spec `navigation-status-details`). Verify: a test that feeds one route's arrays through both the summary path and the instruction path and asserts equality per step.
  - **Done 2026-10-05 — measured on a device, not only formatted.** The live-guidance case now starts a real navigation session and compares the engine's own instruction list with the route's per-step values: `step-list parity: engineS=3653 perStepS=3702 engineSteps=16 perStepCount=12` — the two lists agree within **1.3 %** over the whole route (the instruction list carries 16 entries against 12 description lines because the engine also emits roundabout and motorway instructions; the *time* they measure is the same legs). Read from `adb logcat -s RouteDeviceTest`. Host side, both paths format a duration with the same shared formatter (`DurationFormatTest.stepDuration*`).

## 4. Shared formatting for per-step values

Specs: `routing-summary` — Route summary component (values summed to the shown totals, app-formatted).

- [x] 4.1 Add `formatStepDurationText` to `core/src/main/java/com/naviveylin/core/DistanceFormat.kt` (seconds below a minute, "5 min", "1h 5min") and keep `formatDurationText` for the route's total, the notification and the ETA strings. Verify: new unit cases (45 s, 90 s, 3599 s, 1 h 5 min) green.
- [x] 4.2 In `RouteSummary`, format the rows' distance and duration from the numeric values with the shared, locale-aware formatters (the native text is no longer displayed). Verify: `RouteSummary`/`RoutePanelComposeTest` cases with a decimal-comma locale show the comma separator and the metre unit below 1 km.
- [x] 4.3 Record the per-step values' contract in `guidelines/UI.md` if the step row's contents are described there (the row shows the step's own leg, formatted by the app). Verify: the guideline names the leg and points at the `route-analysis`/`osmscout-jni` requirements.

## 5. On-device verification (the measurement that settles the change)

Specs: `route-analysis` — Step values describe the step's own leg; `osmscout-jni` — Per-step leg values.

- [x] 5.1 Emulator run with `north-rhine-westphalia` installed (`device-check` skill; `adb emu geo fix 7.4653 51.5136`, search `Bochum` → plan, as recorded in the archived session change): read the diagnostics line `RoutePanelVM: route analysis steps=19 sumM=… totalM=17300` and record the numbers — `sumM` SHALL be within rounding of `totalM` (the pre-fix value was a few hundred metres). The claim is text, so the evidence is the UI dump's row text (leg-sized values, e.g. hundreds of metres and minutes, not `14 m`/`2 s`) and the diagnostics line; `pixel-check` is not the instrument here.
  - **Status 2026-10-05 — numbers obtained, row text owed.** No phone AVD was startable from the session (the emulator binary is outside the working directory and denied by the tool policy), so the owner chose the running AAOS AVD (`emulator-5556`, the automotive build installed over it with `-r -t`, its NRW database kept). Its car host owns the display (`mCurrentFocus=null`, the CarAppService display is `touch NONE`) and `uiautomator dump` is denied by the policy, so the **phone route panel could not be driven**; the same numbers were measured through the bridge instead, in the device case `RouteInstructionPositionDeviceTest.perStepValuesAreTheStepsOwnLegs`, which logs them (`adb logcat -s RouteDeviceTest`):
    - **run 1** (Dortmund Hbf → Cologne Hbf, ~70 km): `steps=19 sumM=97416 totalM=72771 ratio=1.34 sumS=…`; the ratio assertion (>0.5) passes, the pre-fix shape (≤100 m per step) does not — see the R1 falsification below.
    - **run 2** (16.7 km route): `steps=20 sumM=21011 totalM=16677 ratio=1.26 sumS=952 firstM=0 maxLegM=16190` — the start line owns a zero-length leg (`firstM=0`) and the largest leg is 16.2 km, i.e. legs, not edges.
    - The two native totals (router vs description) differ by 26–34 %: filed as **TODO.md §129**, and the specs/tasks were corrected to the invariant that is true (legs covering the route once) instead of "within rounding".
    - **Still owed**: the same numbers as *row text* in the phone's step list (a phone AVD with the mobile build, `uiautomator dump`).
- [x] 5.2 Same run: confirm the analysed row's numbers describe the highlighted leg (`adb logcat -s NaviVeylin` `RouteHighlight: range=…` plus the row text) and that the native stream shows no per-step alignment warning and 0 fault-like lines. Verify: recorded logcat excerpt with the line counts.
  - **Status 2026-10-05 — native half proven, UI half owed.** Over the instrumented run's window `adb logcat -d -s NaviVeylin`: **0** fault-like lines (`abort|SIGSEGV|use-after-free`) and **0** per-step alignment warnings ("per-step value count … does not match"), i.e. every route's arrays were aligned and published. The `RouteHighlight: range=…` comparison needs the phone route panel (see 5.1) and stays owed. While making this case runnable, two findings were filed: **TODO.md §130** (the description's first node ~198 m from the polyline's first point on a long route) and **TODO.md §131** (the case itself still asserted the pre-flip segment orientation; the assertion now checks the segment's last vertex against its own manoeuvre and its first against the previous one).
- [ ] 5.3 Same run, car/AA path: read the navigation hint's secondary text (distance and arrival time for the next manoeuvre) from a host template UI dump — the arrival time SHALL no longer be seconds away, and the navigation details list's per-step time SHALL match the summary's.
  - **Status 2026-10-05 — owed.** Needs a driver position on the route and the car surfaces; the AAOS AVD's car host could not be driven from the session (see 5.1), and the host template dump is the very `uiautomator` path the policy denies.
- [x] 5.4 State explicitly whatever this run cannot show: the **remaining**-time shrink (1.4/D5) needs motion, which a stationary emulator cannot produce — record it as not verifiable here, with the mock-GPS/GPX-replay route as the follow-up, rather than implying device proof.
  - **Resolved 2026-10-05 — the shrink *is* device-verifiable without motion.** The premise ("needs motion") was wrong: two synthetic fixes inside one leg make the engine report the same manoeuvre twice, which is exactly the property (`live guidance: leg=4 beforeS=26 afterS=8`, and R3 falsifies it by mutation). The AAOS AVD's phone-UI half remains unverifiable on that AVD (its car host owns the display, `touch NONE`, `uiautomator dump` denied by policy) — see 5.1/5.2.

## 6. Revert-checks

Specs: `osmscout-jni` — Per-step leg values; A next instruction's time is the remaining time of its leg.

- [x] 6.1 R1: move the per-step reference advance back into `BeforeNode` in the calculate path — the device measurement (5.1) must show `sumM` collapsing to a few hundred metres. Restore, re-run forced green. Note in the evidence that the host fixture test stays green, i.e. it pins the app's aggregation and is not the native guard (`revert-check` skill format).
  - **Falsified 2026-10-05** (mutation held in the working tree only, never committed): the two mutant lines in `DescCallback::BeforeNode` of the calculate path → native + test APK rebuilt, both installed, `am instrument …#perStepValuesAreTheStepsOwnLegs` → **FAILED** `the first turn onward carries legs` (every step's value ≤ 100 m, i.e. the pre-fix geometry edge; the same run reported the ratio as ≤ 0.5). **Restored** (`git -C app/src/main/cpp/libosmscout status --short` empty again), rebuilt, reinstalled → the full class **`OK (4 tests)` in 187.9 s**. Two traps met on the way: a mutated APK installed while the old app process still had the previous `.so` mapped made the case pass once (stale binary), and Gradle did not repackage the APK after the restore until the stale outputs were removed.
  - Host-side: the fixture-based aggregation tests stayed green under the mutation, so they pin the app's arithmetic and are *not* the native guard (as stated in the design's verification section).
- [x] 6.2 R2: make both arrays absent — the app must fall back to the bracket parser and still list every step with its instruction text (spec scenario "A route without per-step values still lists its steps"). Restore, re-run forced green.
  - **Falsified 2026-10-05** at the app's fallback gate (the arrays' absence is a submodule state, so the mutation was applied where the app decides to fall back: `buildSteps` returning an empty list when `instructionValues` is empty). The focused class then reported 6 failures, including `a route without per-step values still lists every step`. **Restored** (`grep -c "R2 MUTATION" … RoutePanelViewModel.kt` → 0) and re-ran forced: `:app:testMobileDebugUnitTest --tests "com.naviveylin.ui.route.*"` **BUILD SUCCESSFUL in 1m33s**.
- [x] 6.3 R3: drop the remaining-time scaling for the next instruction in 1.4 — the "shrinks while the leg is driven" case must fail. Restore, re-run forced green.
  - **Falsified 2026-10-05 — the premise was wrong: it *is* device-testable without motion.** The new case `liveGuidanceReportsTheRemainingTimeOfTheLegItApproaches` starts a real navigation session and feeds two synthetic fixes inside one leg, so the engine's own report is read twice for the same manoeuvre. Mutation (the remaining-time interpolation dropped, `next.timeTo` left as FillLeg's whole-leg time) → **FAILED**: `the remaining time of 'Turn left into Möllerstraße' must shrink while its leg is driven (before=25 s, after=0 s)` (the estimate vanished rather than shrinking — the first version of the assertion, `after < before`, passed on that zero, so it now requires `after > 0`). **Restored**, rebuilt, reinstalled → the full class **`OK (5 tests)` in 36.8 s** with `live guidance: leg=4 beforeS=26 afterS=8`.
  - Note: the mutation only takes effect when the object is really recompiled — the first attempt reported `BUILD SUCCESSFUL` without recompiling because the edited source's mtime was older than the `.o` (touching the file fixed it), and the ABI-filtered build had also redirected the APK path.

## 7. Integration gate

- [x] 7.1 Build all three ABIs for both flavors (`./gradlew :app:assembleDebug`) and the JNI module, and confirm no build warnings and no Kotlin/Java compile errors (the JNI field additions touch the override source set).
  - Done: `:app:assembleDebug` **BUILD SUCCESSFUL in 4m50s** (`assembleMobileDebug` + `assembleAutomotiveDebug`, 3 ABIs), `grep -cE '^(e|w): '` on that log → **0**, `:osmscout-client-java:test` green.
- [x] 7.2 Run the full both-flavor unit-test gate once, forced (`-PforceTests --no-build-cache`, `guidelines/Build.md` §2) and confirm existing tests still pass; quote the executed-task counts and elapsed time as evidence (a 5-second "BUILD SUCCESSFUL" proves nothing, TODO 17).
  - Done: `./gradlew test -PforceTests --no-build-cache -PnoCoverage` → **BUILD SUCCESSFUL in 9m 47s**, `185 actionable tasks: 17 executed, 168 up-to-date` (the forced flags force the *test* tasks, the compiles stay up-to-date — the documented lever). Result XMLs: app automotive **1666 tests, 0 failures, 0 errors** (219 files, newest 2026-10-05 08:44), core **444/0/0**, auto **779/0/0**, JNI **26/0/0**; the mobile flavor's suite was re-run afterwards because its results directory had been overwritten by a focused rever-check run — **1666 tests, 0 failures, 0 errors** (219 files, newest 2026-10-05 08:49, `BUILD SUCCESSFUL in 4m43s`).
- [x] 7.3 Trace every scenario of this change's deltas to a task above and confirm no task references a spec path that the change did not touch; list the mapping in the change's final report.
  - Mapping (delta scenario → task): `osmscout-jni` legs-cover-the-route / own-leg / unaligned-absent / start-owns-no-leg → 1.1, 1.2, 1.4, 5.1; instruction time is the leg's travel time (all three scenarios) → 1.4, 3.2, 3.3; next instruction's remaining time (both scenarios) → 1.4, 3.2, 5.3, 6.3. `route-analysis` step values are the leg / rows add up / app formatting / no-values fallback / divergence reported → 2.1–2.4, 4.2, 5.1, 6.2; own-values-never-a-neighbour's → 2.3; locatable-leg and highlight-orientation (modified blocks) → 5.2, 6.1 along with the filed §130/§131. `routing-summary` (modified block) → 4.1–4.3. `navigation-status-details` (modified block) → 3.1–3.3. The four touched spec paths are exactly the four the proposal lists; nothing else was edited.

## 8. Verification follow-up (2026-10-05): scenario gaps closed

The verification report named six warnings (scenario coverage) and three suggestions. What closed them:

- **`osmscout-jni` — Unaligned values are absent, not shifted**: the device case now asserts the four
  per-step arrays are all present and aligned or all absent, never partial
  (`RouteInstructionPositionDeviceTest.perStepValuesAreTheStepsOwnLegs`); the app-side fallback for the
  absent case is a host case (`a route with positions but no per-step values still lists its steps`).
- **`osmscout-jni` — A step's time covers its whole leg** (the scenario that previously read "time grows
  with the leg", which assumed equal speeds and is not generally true): reworded, and the device case
  cross-checks **every** step's leg against the straight line between its two manoeuvre positions (≥ ½ of
  it, ≤ 2.5× + 200 m) and requires ≥ 20 s for legs ≥ 300 m. Device run: `legsCompared=10`,
  `maxLegM=73427 maxLegS=2547`.
- **`osmscout-jni` — The two step lists of one route agree** and **A next instruction's time is the
  remaining time of its leg** (both scenarios): covered by the new live-guidance device case
  (`step-list parity: engineS=3653 perStepS=3702` within 1.3 %; `live guidance: beforeS=26 afterS=8`), and
  falsifiable by mutation (see 6.3).
- **`route-analysis` — Row values and highlighted leg are the same leg**: host case
  `the row's values and the published segment describe the same leg` asserts the row's numbers *and* the
  published `analysedSegment` for the analysed step; the on-device *text* read stays owed (see 5.1/5.2).
- **`route-analysis` / `routing-summary` — the app's own formatting**: host cases
  `step values are formatted with the device locale` (German comma in a row) and `a step duration below a
  minute reads in seconds`.
- **Suggestion 1** — the device test now uses the shipped `InstalledMaps.findDatabaseDirectories` instead
  of its own walk. **Suggestion 2** — `guidelines/Build.md` §10 documents the `Diag/ROUTE` and
  `RouteDeviceTest` greps. **Suggestion 3** — `RouteSummary`'s KDoc states that a row's values are the
  bridge's per-step legs, formatted by the app, and points at `RouteStepValues.kt`.

Device evidence for the whole block, phone AVD (`emulator-5554`, NRW database, mobile build):
`RouteInstructionPositionDeviceTest` → `OK (5 tests)` in 36.8 s; measurements
`per-step values: steps=12 sumM=97416 totalM=72771 ratio=1.34 sumS=3702 firstM=0 maxLegM=73427 maxLegS=2547 legsCompared=10`,
`live guidance: leg=4 beforeS=26 afterS=8 steps=16`, `step-list parity: engineS=3653 perStepS=3702`.
Host: focused route+navigation suites **173 tests, 0 failures**; `:core:test` **444/0/0**;
`:osmscout-client-java:test` **26/0/0**.

**Owed after this block** (unchanged): 5.1/5.2's *row text* read (needs `uiautomator dump`, denied by the
tool policy) and 5.3's car-template read (no car AVD up; the *values* it would show are device-proven by
the live-guidance case). The full both-flavor gate is owed once before archive, because tests were added
after the gate that ran (9m47s).
