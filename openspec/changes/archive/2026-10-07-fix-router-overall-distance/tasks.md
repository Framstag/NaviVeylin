# Tasks

## 1. Bridge: publish a route length (spec: `osmscout-jni` — The published route length is a length of that route)

- [x] 1.1 Read both route-calculation paths in
      `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` (`:6140`, `:6852`) and
      record where the description's total is available on each relative to the `totalDistance` assignment
      — line numbers plus the enclosing branch. Also record whether the description-less branch is
      reachable in practice or defensive only (design Open Question 1). Verify: the note names both sites
      and the branch each assignment sits in.
- [x] 1.2 In one path, replace `totalDistance = result.GetOverallDistance().AsMeter()` with the route's
      own length: the description's total where the description exists, otherwise the great-circle sum over
      the polyline coordinates that same call publishes. Verify:
      `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` compiles (this is the
      reliable compile path for the JNI TU; `TODO.md` §66 has the meson `-fsyntax-only` workaround) and
      `grep -n 'GetOverallDistance' <file>` shows the remaining uses are the ones the router itself needs.
      (Requirement: *The start/target estimate is not exposed as a route length*)
- [x] 1.3 Apply the same change to the second path, so both publish the same value for the same route.
      Verify: the two sites' surroundings read identically for the length decision (diff-style note), and
      the build in 1.2 still compiles with both changes present.
- [x] 1.4 Correct the false comment above both assignments ("the router's own accumulated distance"): it is
      the start/target air-line estimate (`AbstractRoutingService.cpp:1088-1094`; upstream's
      `Demos/src/Routing.cpp:1291` prints it as "Air-line distance"). Verify: the comment no longer claims
      the value is an accumulated distance, and names what the value is and who needs it. (Scenario: *No
      route field states the estimate as a distance*)
- [x] 1.5 Commit the submodule patch on `naviveylin-local` and bump the main repo's gitlink in the same
      commit as the app-side change. Verify: `git -C app/src/main/cpp/libosmscout status --short` is empty,
      `git ls-remote origin naviveylin-local` was checked immediately before the push (AGENTS: one session
      owns the submodule and its pushes), and the main repo's `git diff` shows the changed gitlink SHA.

## 2. The estimate stops being a length (spec: `osmscout-jni` — The start/target estimate is not exposed as a route length)

- [x] 2.1 Enumerate every consumer of the published route length and of the estimate in one pass
      (`grep -rn 'distance' app/src/main/java/com/naviveylin/ui/route app/src/main/java/com/naviveylin/navigation osmscout-client-java/src/main/java`,
      plus the car trip-summary path) and record the list. Verify: the note says for each consumer whether
      it wants a length or an estimate; the current expectation is that none wants the estimate. (Scenario:
      *A consumer that reads the length gets a length*)
- [x] 2.2 Apply the decision that 2.1 justifies: publish only the route length (design D2 A), or — if a
      consumer wants the estimate — keep it under an accessor named as an estimate. Verify: the app
      compiles (`:app:assembleMobileDebug`), and the note quotes the grep lines behind the decision.
- [x] 2.3 Keep the app's one seam honest: `app/src/main/java/com/naviveylin/ui/route/RouteStepValues.kt`
      keeps preferring the step sum and falls back to the published length, which is now a length. If the
      fallback rule itself changes, add or extend a host case in the route step-values test asserting the
      fallback returns the route's published length for a route without step values. Verify: the named
      host case exists and passes in `./gradlew :app:testMobileDebugUnitTest -PforceTests --no-build-cache`
      (quote the tallies). A host case cannot route (the JNI stub is symbol-free), so this covers the rule,
      not the defect.

## 3. Guard and falsification (spec scenarios: *A published length tracks the route that was drawn*, *A long intercity route carries a real length*, *A route without a description still carries a route length*)

- [x] 3.1 Extend `app/src/androidTest/java/com/naviveylin/route/RouteInstructionPositionDeviceTest.kt`'s
      `routeLengthsAreMeasuredForALongAndAShortRoute` from logging to asserting: per candidate, the
      published total agrees with the description's total (where present) and with the great-circle polyline
      length within the description's own measured tolerance (0.9964-1.0021; state the bound in the
      assertion message). Verify: the assertions name both ratios, and the case fails on a mutated tree in
      3.2 while passing on the fixed one.
- [x] 3.2 Revert-check (the invariant this change creates — the bridge publishes a route length). The
      mutation must be one the guard can actually catch: **re-adding the estimate at its old spot proves
      nothing** — the description branch overwrites `totalDistance` afterwards (measured 2026-10-07, and
      the reason this task's first wording was wrong). Instead republish the estimate **after** the
      description branch, in the path the app uses: `OSMScoutClient.cpp`, the assignment chain that ends at
      `:6403` inside `Java_..._calculateRouteWithObjectsWithProfile` (both `NavigationEngine.kt:452` and the
      device case call `calculateRouteWithProfile` → that path; `calculateRouteAsync` is unused today). Run
      `routeLengthsAreMeasuredForALongAndAShortRoute` and it MUST fail on the ratio assertion; restore the
      file (`git -C app/src/main/cpp/libosmscout checkout -- libosmscout-client-java/src/OSMScoutClient.cpp`),
      rebuild, re-run green on the same AVD, quote both runs' numbers. Verify: two runs — one failing with
      the expected assertion and one green — plus the grep that the mutation line is gone.
- [x] 3.3 Exercise the description-less path, since no committed case produces it: with a deliberate,
      **uncommitted** mutation that skips description generation in one path, confirm on the AVD that the
      published total still tracks the polyline, and record the numbers; revert the mutation. Verify: the
      recorded run's numbers plus the restored, grep-clean tree. If the path turns out to be unreachable
      (1.1), record that instead and say so here rather than claiming the scenario is covered.

## 4. Documentation (the rule survives a fresh clone)

- [x] 4.1 Record the rule in `guidelines/Build.md` §10 beside the device recipe: a calculated route
      publishes a length of that route (the description's total, else the published polyline's sum), the
      router's `GetOverallDistance()` is the start/target estimate for its cost limit and progress
      denominator and is **not** a route length, and a surface reads a length through
      `RouteStepValues.routeLengthMeters`. Verify: the section states all three, cites
      `AbstractRoutingService.cpp:1088-1094` and the measured ratios (0.748/0.795/0.552 before,
      `descriptionOverPoly` 1.0014/1.0021/0.9964 as the reference), and is readable from a fresh checkout
      (`git show HEAD:guidelines/Build.md`).
- [x] 4.2 Note in this change that `TODO.md` §129 (user-visible half) and §139 (native half) are removed
      when the change archives — the removal itself belongs to `cleanup-todo`. Verify: the note names both
      ids and says who removes them.

## 5. Integration verification

- [x] 5.1 Device run with numbers: on the AAOS AVD (`emulator-5556`) or a phone AVD, run
      `routeLengthsAreMeasuredForALongAndAShortRoute` and quote per candidate
      `routerTotalM` / `descriptionTotalM` / `polylineM` / `routerOverPoly` / `descriptionOverPoly` /
      `descriptionOverRouter`. Expected: `routerOverPoly` and `descriptionOverPoly` both ≈ 1.00 (before:
      router 0.748 / 0.795 / 0.552, description 1.0014 / 1.0021 / 0.9964). The emulator is stationary and
      that is sufficient — the requirement is a number, not a pixel, so no `pixel-check` measurement is
      owed; state that explicitly in the record rather than implying a visual check.
- [x] 5.2 Build and suites: `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a`
      and `./gradlew :app:assembleAutomotiveDebug -Pandroid.injected.build.abi=arm64-v8a` (both flavors
      link the native TU), then one `:app` unit-test suite plus `:auto` and `:core`
      (`-PforceTests --no-build-cache -PnoCoverage`), quoting the executed-task count and per-module
      tallies (`build-test-gate`: a cached run is not evidence). The full both-flavor gate is owed once
      before the change is declared complete.
- [x] 5.3 Record the scenario→case mapping for the delta in this change (a `traceability.md` next to
      `tasks.md`), one row per scenario: S1/S2 → 3.1's assertions + 5.1's numbers; S3 → 3.3 (or its
      unreachable-path record); "No route field states the estimate as a distance" → 1.4 + 2.2's grep;
      "A consumer that reads the length gets a length" → 2.1's enumeration + 2.3's case, with the note that
      both surfaces read the same `RouteEntry` in one process, so the case is a host/device substitute for
      the car trip summary as well (state that substitute rather than claiming a car run).

## Workflow follow-up

- Archive the change after its review, then verify the delta landed in `openspec/specs/osmscout-jni/spec.md`.
- Hand `TODO.md` §129 and §139 to `cleanup-todo` for removal once the change is archived.
- Before writing the delta's final wording, re-read the in-flight `fix-step-leg-distance-and-time`'s
  `osmscout-jni` spec text (it rewrites the same area) and keep this delta additive.
