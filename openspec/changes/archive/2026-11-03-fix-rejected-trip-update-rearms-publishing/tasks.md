# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/car-host-fault-isolation/spec.md` for the
contract.

## 1. The red case

Requirement: "A rejected trip update re-arms publishing within a retry budget" — its three scenarios.

- [x] 1.1 Add `aTransientTripRejectionRearmsPublishing` and `aRejectedTripIsNotResentUntilItsContentChanges` to
      `auto/src/test/java/com/naviveylin/auto/NavigationManagerControllerTest.kt`, and rewrite
      `aRejectedTripUpdateEndsTripPublishing` as `aRepeatedlyRejectedHostStopsPublishingAfterTheRetryBudget`
      (five changed trip contents against a host that refuses every send; the case asserts exactly three
      sends, so a budget of one or of five turns it red). Verify: on base `4367cb4d` with the controller
      unchanged the class reads JUnit `tests=11 failures=3 errors=0` at `2026-11-03T12:21:34Z`, the three
      cases finding one `updateTrip` call where they need two, two and three — `evidence/red-on-HEAD.xml`
      (captured from `auto/build/test-results/testDebugUnitTest/TEST-…NavigationManagerControllerTest.xml`).
- [x] 1.2 Confirm the distances the throttle compares are distinct buckets, not a rounded value:
      `core/src/main/java/com/naviveylin/core/DistanceFormat.kt:12-16` rounds to 50 m below 1 km, so the
      budget case steps by 100 m. Verify: the three cases fail for the missing send and not for the throttle
      (`red-on-HEAD.xml`'s three failures all name `updateTrip`).

## 2. The re-arm

Requirement: the same requirement.

- [x] 2.1 `NavigationManagerController.publishTrip`: on a rejected `updateTrip` write `lastPublishedState =
      state`, increment `rejectedUpdates`, and set `navigating = false` only once it reaches
      `MAX_REJECTED_TRIP_UPDATES` (3); reset the counter on a delivered trip, in `onNavigationStarted` and in
      `onDestroy`. Verify: `./gradlew :auto:testDebugUnitTest -PforceTests -PjvmTestsOnly --tests
      'com.naviveylin.auto.NavigationManagerController*'` → `BUILD SUCCESSFUL`,
      `NavigationManagerControllerTest` JUnit `tests=11 failures=0 errors=0` at `2026-11-03T12:21:43Z`
      (`evidence/green-controller-cases.xml`) and `NavigationManagerControllerTripTest` `tests=9 failures=0
      errors=0` (`evidence/green-controller-trip-cases.xml`), so the unchanged cadence,
      teardown and restart cases still hold.
- [x] 2.2 State the rule in both KDocs (class and `publishTrip`) and keep both log lines coordinate-free: the
      sub-budget line says "re-arming for the next changed trip", the spent-budget line says the host session
      is treated as ended. Verify: `grep -n 're-arming\|budget is spent' auto/src/main/java/com/naviveylin/auto/NavigationManagerController.kt`
      names the two messages and no position, distance, ETA or bearing beyond the pre-existing diagnostics
      line.

## 3. The spec

Requirement: `car-host-fault-isolation` — the new requirement and the modified *Host navigation call rejected*
scenario.

- [x] 3.1 Write `specs/car-host-fault-isolation/spec.md`: `ADDED` "A rejected trip update re-arms publishing
      within a retry budget" (three scenarios, each naming one case) and `MODIFIED` "No fault escapes into the
      host path" with the shipped text and all 11 scenarios repeated, the rejected-call scenario reworded, and
      `- **Case**: none — …` on the ten scenarios this change does not re-case. Verify:
      `diff <(sed -n '62,137p' openspec/specs/car-host-fault-isolation/spec.md | grep -v '^#### Scenario:\|^$')
      <(the delta's modified block, Case lines removed)` shows only the reworded `THEN`.
- [x] 3.2 Run the artifact checks the gate owes the `openspec/*` paths. Verify: `openspec validate
      fix-rejected-trip-update-rearms-publishing --strict` passes, `bash tools/check-openspec-hygiene.sh`
      passes, `bash tools/gen-feature-list.sh --check-classification` passes, and `bash
      tools/check-change-readiness.sh fix-rejected-trip-update-rearms-publishing` reports
      `LOOP-READINESS ok` (the delta's 14 scenarios each carry a `**Case**:` line, the design declares
      `## Open decisions` `none`, and the proposal carries its `surface:` line).

## 4. The gate

- [x] 4.1 Record the block `./tools/loop/measure.sh --change fix-rejected-trip-update-rearms-publishing`
      prints. Verify: its `LOOP-MEASURE-CHANGE` line reads `scenarios=14 cases=14 unnamed=0`, and the
      `LOOP-MEASURE-SURFACE` line quotes the proposal's `surface:` statement.
- [x] 4.2 Run `./tools/loop/gate.sh` once from the change root, against the run's own base
      (`--base 4367cb4debdba8d99a1cbedf3daf5cf0d06bf4fd`; without it the tree's stale `origin/main`
      at `55b5d658` makes the gate measure the already-landed #750 delta — see the worker's report).
      A compiled file changed, so the JVM-only Gradle gate is the step the pathspec owes. Verify: one gate
      line — `LOOP-GATE: tests=0 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly
      --no-build-cache 298s)`, `BUILD SUCCESSFUL in 4m 58s` — quoted in the worker's report and its tally
      copied into the item's evidence as `gate-gradlew-test.xml`. This task ends the worker's session; the
      evidence files and the archive are the tail worker's.
- [x] 4.3 Record that the AAOS measurement the recorded decision recommended before Option 1 was not taken,
      and why (no `emulator` binary on `PATH`, no attached device, no deterministic rebind recipe) — stated in
      `design.md`, "Not taken: the AAOS measurement the decision recommended". Verify: the section names the
      commands that were checked and claims no device evidence.
