# Tasks

## 1. The defect reproduced on HEAD

- [x] 1.1 `core/src/test/java/com/naviveylin/core/ManoeuvreAnnouncementTest.kt`: add "the roundabout exit is
      the hint before the ring is entered" — a facts-only published node 30 m before a roundabout entry, a
      route step list carrying `[entry@1000, infoNode@1010, leave@1035 (exit 2), infoNode@1050]`; asserts the
      announced kind is `ROUNDABOUT_ENTER` and the announced record's `nextNext` is the leave with
      `roundaboutLeave.exitCountTaken == 2`. Red on HEAD: `java.lang.AssertionError: the exit is announced
      before the ring is entered` (assertNotNull, `nextNext == null`); the run printed `8 tests completed,
      3 failed` and the class XML read `tests=8 skipped=0 failures=3 errors=0`. The XML has since been
      replaced by the forced runs of tasks 5.2 and 5.5, so the tally above is the red run's own console
      output rather than a surviving artifact.
      Trace: spec `next-turn-overlay` — "A roundabout exit is announced before the ring is entered".
- [x] 1.2 Same file: add the three derivation cases "the following manoeuvre within the window is the hint"
      (gap 35 m), "a following manoeuvre beyond the window is not shown" (gap 250 m), "a facts-only node
      between the manoeuvres is not the hint" (two manoeuvres 40 m apart with an info node at +10 m).
      Red on HEAD: the same run's `failures=3` — "the following manoeuvre within the window is the hint" and
      "a facts-only node between the manoeuvres is not the hint" failed; the beyond-the-window case passed
      vacuously (`nextNext` was null on HEAD for every input).
      Trace: spec `next-turn-overlay` — "Next next shown", "Next next hidden", "A facts-only node is never
      the hint".
- [x] 1.3 `auto/src/test/java/com/naviveylin/auto/NavigationTemplateMapperTest.kt`: add "the next step is the
      following manoeuvre, not the next route node" (current = a turn, list carries an info node then the
      manoeuvre) and "the exit ordinal of the following manoeuvre is on the next step" (current =
      roundabout entry, following = leave with ordinal 2). Red on HEAD: `68 tests completed, 2 failed`, both
      new cases, class XML `tests=68 skipped=0 failures=2 errors=0` (that XML, too, has since been replaced
      by the forced gate runs).
      Trace: spec `auto/navigation-view` — "Next-next turn shown when present", "The roundabout exit is the
      next step before the ring is entered".

## 2. The fix in `:core`

- [x] 2.1 `core/src/main/java/com/naviveylin/core/RouteStepGuidance.kt`: add
      `const val HINT_WINDOW_METERS = 200.0`, `public fun nextManoeuvreFrom(route, step): RouteStep?` (the
      first non-facts-only step strictly after `step.distanceFromStart`) and
      `public fun nextManoeuvreAfter(route, step): RouteStep?` (that step, bounded by the window). KDoc names
      the spec requirement and why a facts-only node is skipped (`RouteStepCollector` emits one step per
      described route node; census steps=501 rows=34 in
      `2026-10-28-route-panel-from-steps/evidence/device-census.log`).
      Verifies: `:core:compileKotlin` plus the 1.1/1.2 cases reaching the helper.
- [x] 2.2 Same file: `guidanceStep` returns a `copyForGuidance` record on **every** exit — including the
      fallback where no manoeuvre of the route list is known, which returns a copy whose `nextNext` is null —
      and sets `announced.nextNext = nextManoeuvreAfter(route, announced)` (never the record's own `nextNext`,
      never a fallback to a node). Verifies: 1.1-1.2 green, plus the reviewer's MINOR-1 (the third exit
      returned the published record with its native node hint).
- [x] 2.3 Same file: replace `ManoeuvreAnnouncementTest` "a manoeuvre is announced as published"
      (`assertSame`) with "a manoeuvre is announced as a copy of the published step" (`assertNotSame` + every
      field a surface reads survives + mutating `nextNext` on the announced record leaves the route-list
      entry `null`). `copyForGuidance` becomes a **full clone**, because the copy now replaces the published
      record on every live publication: a field left behind is a value a surface silently lost, and the old
      field list already dropped `nameChanged`, `crossingWays`, `poi`, `maxSpeed`, `lanes` and
      `remainingTime` (a name-change row therefore announced no name). Verifies the engine's published state
      cannot be mutated by a surface (design D2).

## 3. The fix on the car surface

- [x] 3.1 `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt` `routingInfoFromState`: the
      next row becomes `core.nextManoeuvreFrom(state.steps, current)`, replacing
      `state.steps.getOrNull(currentStepIndex + 1)`. Four test fixtures normalised to route order (distances
      non-decreasing), no assertion changed.
      Verifies: 1.3 green; no other behaviour of the function changes (current row, lanes, distance untouched).
      Trace: spec `auto/navigation-view` — "Current and next step in host instruction panel".

## 4. Diagnostics and guideline that name the old provenance

- [x] 4.1 `app/src/main/java/com/naviveylin/navigation/RoundaboutInstructionDiagnostics.kt`: KDoc states that
      `hintExit` is the app-derived following manoeuvre while `kind`/`exit`/`cw` are the native record's
      fields; the line's shape is unchanged (no recipe parse change). Verifies:
      `app:testMobileDebugUnitTest --tests "*RoundaboutInstructionDiagnosticsTest*"` stays green
      (tests=6 failures=0).
- [x] 4.2 `guidelines/UI.md` §7, the live-guidance paragraph (`:795-803`): replace "the overlay's second row
      is the nested step" with the following manoeuvre of the route's step list within 200 m, and note that
      the published record's own `nextNext` is not read by a guidance surface. Verifies: the paragraph names
      the requirement and the constant, no other section changes.

## 5. Suites, gate and device evidence

- [x] 5.1 Focused suites: `:core:testDebugUnitTest --tests "com.naviveylin.core.ManoeuvreAnnouncement*"`
      (tests=8 failures=0), `:auto:testDebugUnitTest --tests "*NavigationTemplateMapper*"` (tests=68
      failures=0), `:app:testMobileDebugUnitTest --tests "*NextTurnOverlay*"` (tests=10 failures=0) and
      `--tests "*RoundaboutInstructionDiagnostics*"` (tests=6 failures=0).
      Verifies: every case from section 1 and 4.1 green, no excluded case.
- [x] 5.2 One full both-flavor gate run on the final tree (`guidelines/Build.md` §4b), read by §4a:
      `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache --continue` → BUILD SUCCESSFUL in
      5m 21s, 166 actionable tasks (41 executed); `:app:testMobileDebugUnitTest` and
      `:app:testAutomotiveDebugUnitTest` both present. XML tallies: core 65 files tests=649, auto 92 files
      tests=893, app 618 files tests=4420, osmscout-client-java 23 files tests=112 — tests=6074
      failures=0 errors=0 skipped=0, and zero `w:` compiler warnings. The same command on the pre-review
      tree was green in 7m 50s with identical tallies; this one is the run that covers the reviewer's
      corrections. Exclusions: none (buildSrc keeps no test-results dir of its own).
- [x] 5.3 Device evidence (phone) — **taken**, in the form the emulator can actually produce: two
      instrumented cases on `emulator-5556` with the app's Android/Andorra map set, no GPS and no drive.
      - `app: NextNextHintDeviceTest` (real map data): routes Andorra-Corridor pairs, drives the native
        controller with the route's own vertices, and feeds each published (step list, step ahead) pair
        through `core/guidanceStep`. Result: **119 observations**, every one an announced
        `ROUNDABOUT_ENTER` whose hint is the leave, ordinal in {1,2,3}, gap 30-37 m inside the 200 m window;
        `OK (1 test)` in 14.6 s. Sample lines:
        `published rbt: kind=INFORMATIVE exit=0 cw=false hintExit=0 | announced rbt: kind=ROUNDABOUT_ENTER
        exit=0 cw=false hintExit=2 gap=30m` — and `approach count=119`.
      - `app: NextTurnHintRowDeviceTest` (the pixels): renders that announced record through the real
        overlay and measures the row — `OK (1 test)` in 3.5 s, log
        `hint row: arrow=27x27px (36 dp, density=0.75) ink=729 box=27x27px exit=2 label=Ausfahrt 2`. So the
        hint arrow is the 36 dp node, its rasterised glyph draws ink, and the row words the ordinal.
      - The same run measured the native record's own hint on real data: **`hintExit=0` on every
        published step**, which is the tracker finding's (#717) evidence.
      - Not measured: a full drive through the ring with the UI live (the engine path is covered by the
        two cases above; nothing here claims a GPS-driven screenshot).
- [x] 5.4 Car: `NavigationTemplateMapperTest` stands as the host substitute (the host draws the template) —
      the two added cases exercise the next-step row through `routingInfoFromState`, and no DHU/AAOS host run
      was taken (the host's rendering is not observable from a test here; `guidelines/Build.md` §10a-§10b is
      the recipe if one is wanted).
- [x] 5.5 Revert-check for the one invariant this fix introduces beyond the symptom — the hint never comes
      from a facts-only node: mutated `nextManoeuvreFrom` to drop its `!isDataOnly` term and ran
      `:core:testDebugUnitTest --tests "com.naviveylin.core.ManoeuvreAnnouncementTest" -PforceTests
      --no-build-cache` → `tests=8 failures=2`, the failures being "a facts-only node between the manoeuvres
      is not the hint" and "the roundabout exit is the hint before the ring is entered"; restored, re-ran
      forced → BUILD SUCCESSFUL, `tests=8 failures=0`.

## 6. Independent review and archive

- [x] 6.1 A read-only reviewer (not the writer) checked each delta scenario against the diff and the gate
      XMLs: `next-turn-overlay` 4/4 PASS and `auto/navigation-view` 8/8 PASS (the reroute scenario PASS-weak,
      a pre-existing gap: no case drives a reroute and the diff does not touch the current row).
      `copyForGuidance` verified lossless field-by-field against `RouteStep.java` (37/37). **VERDICT: PASS**,
      0 blockers, with two minors that were then fixed: MINOR-1 (the fallback exit returned the published
      record with its native node hint, contradicting D2's "always a copy" — fixed, and the case
      `no manoeuvre ahead keeps the published fields and drops the node hint` now asserts the copy and the
      null hint) and MINOR-2 (the diagnostics KDoc said "filled in native code" and "nested" in two places
      after gaining the app-derived paragraph — fixed). Notes taken: the `informativeNode` fixture no longer
      sets `remainingDistance` (a list entry carries none), the dead `windowMeters` parameter of
      `nextManoeuvreAfter` was dropped, and the unused `assertSame` import removed. Notes left as filed-only:
      `core/nextNextExitOrdinal` and `navSymbolForNextNext` have test-only callers (pre-existing), and the
      copy is shallow (sub-records shared by reference; no writer exists that mutates them).
- [ ] 6.2 Archive the change with the red-on-HEAD case, the final gate run, the two device cases' numbers
      (119 approaches, 36 dp / ink 729 px) and the reviewer's PASS quoted; name the follow-up tracker finding
      #717 (the native `nextNext` semantics) as out of scope and filed, and say that a GPS-driven screenshot
      of a live drive was not taken.
