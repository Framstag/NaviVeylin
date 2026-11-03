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
      within a retry budget" (three scenarios at this step, each naming one case; the fourth arrived with task
      6.2) and `MODIFIED` "No fault escapes into the
      host path" with the shipped text and all 11 scenarios repeated, the rejected-call scenario reworded, and
      `- **Case**: none — …` on the ten scenarios this change does not re-case. Verify:
      `diff <(sed -n '62,137p' openspec/specs/car-host-fault-isolation/spec.md | grep -v '^#### Scenario:\|^$')
      <(the delta's modified block, Case lines removed)` shows only the reworded `WHEN`/`THEN` of the
      rejected-call scenario (task 5.2 reworded the `WHEN` after this check was first written).
- [x] 3.2 Run the artifact checks the gate owes the `openspec/*` paths. Verify: `openspec validate
      fix-rejected-trip-update-rearms-publishing --strict` passes, `bash tools/check-openspec-hygiene.sh`
      passes, `bash tools/gen-feature-list.sh --check-classification` passes, and `bash
      tools/check-change-readiness.sh fix-rejected-trip-update-rearms-publishing` reports
      `LOOP-READINESS ok` (the delta's 15 scenarios each carry a `**Case**:` line — 14 when this step ran, the
      fourth ADDED one arrived with task 6.2 —, the design declares
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

## 5. Correction round 2 (the bound is consecutive refusals, and the modified scenario's `WHEN`)

Review round 1 refused the change on two findings, both wording and mapping: the merged contract and both
KDocs bounded a *navigation period* while the code bounds a *run of consecutive refusals* (`rejectedUpdates = 0`
on every delivered send, `NavigationManagerController.kt:142`), and the modified *Host navigation call rejected*
scenario kept a `WHEN` (`a host navigation-state call is rejected`) broader than the trip update its named case
exercises. No new claim, case or assertion.

- [x] 5.1 Narrow the bound to consecutive refusals in the merged requirement and the three KDocs that restate
      it. Verify: `grep -in 'consecutive' openspec/specs/car-host-fault-isolation/spec.md
      auto/src/main/java/com/naviveylin/auto/NavigationManagerController.kt` names the requirement sentence
      (`spec.md:476`), the class KDoc (`:32`), the `publishTrip` KDoc (`:114`) and the
      `MAX_REJECTED_TRIP_UPDATES` KDoc (`:169`, "Consecutive", the reason the grep is case-insensitive), and a
      `grep -n 'refusals before publishing\|one period has spent'` over both files finds no surviving
      per-period claim, and the proposal's *attempt budget* bullet says "three consecutive refusals (a successful
      send ends the run)" instead of "per navigation period", so no record still states the rejected bound. The delta's own copy of the requirement carries the same sentence
      (`specs/car-host-fault-isolation/spec.md:7-11`), so the archive's record matches the merged spec.
- [x] 5.2 Narrow the modified scenario's `WHEN` to a rejected trip update, in the merged spec and the delta
      (`:59-63`), so the case the `**Case**:` line names exercises it. Verify: the scenario reads
      *"a host trip update is rejected because the host's navigation session is no longer active"*, and
      `NavigationManagerControllerTest.aRepeatedlyRejectedHostStopsPublishingAfterTheRetryBudget` sends trip
      updates only (`verify(exactly = 3) { nm.updateTrip(any()) }`).
- [x] 5.3 Re-run the focused cases after the KDoc edits, so the green side is post-correction. Verify:
      `./gradlew :auto:testDebugUnitTest -PforceTests -PjvmTestsOnly --tests
      'com.naviveylin.auto.NavigationManagerController*'` → `BUILD SUCCESSFUL in 11s`,
      `NavigationManagerControllerTest` JUnit `tests=11 failures=0 errors=0` at `2026-11-03T12:34:05.152Z`
      (`evidence/green-round2-controller-cases.xml`) and `NavigationManagerControllerTripTest` `tests=9
      failures=0 errors=0` at `2026-11-03T12:34:10.373Z` (`evidence/green-round2-controller-trip-cases.xml`).
- [x] 5.4 Run the gate once for this round, against the same base, and attribute its one failing step. Verify:
      the gate line (quoted verbatim in `evidence/gate-round2-attribution.md`) is green on this change's
      paths — the compiled step, `tools/check-doc-routes.sh`, its self-test, `tools/check-todo-references.sh`,
      both `tools/check-openspec-hygiene.sh` checks and the mutation list — and its single red step,
      `bash tools/gen-feature-list.sh --check-classification`, is attributed there: two unclassified ids that
      are other open changes' deltas (`car-reroute-without-losing-the-map`, `separate-tooling-test-code`) and
      three stale entries the run's base already carries
      (`git show 4367cb4d:tools/feature-list/specs.json` → `3` keys, the base's `openspec/specs/` → `0` of
      those ids), so the step is red before this work and red after it, on paths this change does not edit.
      This change's own capability row is `tools/feature-list/specs.json:49`. This task ends the worker's
      session; the archive is the tail worker's.

## 6. After the base moved

- [x] 6.1 Merge the moved base into the item branch and re-run the gate, so the round-2 red step is answered
      rather than attributed. Base: `main` = `e63f6b45`, whose `00bdd407` classified the two delta spec ids the
      round-2 gate reported (`auto/off-route-indicator`, `tooling-test-layout`) in
      `tools/feature-list/specs.json`. Verify: `bash tools/loop/gate.sh --base main` reads
      `LOOP-GATE: tests=41 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
      514s, …)`, quoted verbatim in `evidence/gate-post-merge.md`, with the tallies
      `evidence/gate-post-merge-gradlew-test.xml` (`tests="0" failures="0" errors="0" time="514"`, `BUILD
      SUCCESSFUL in 8m 34s`, 148 executed, `--no-build-cache`) and
      `evidence/gate-post-merge-gen-feature-list.xml` (`tests="0" failures="0"`, system-out `stale: 5 car-only
      areas: 1 highlights: 19`). The merge touches none of this change's paths: `git diff --name-only
      4367cb4d..e63f6b45` lists `tools/feature-list/specs.json`, `tools/loop/**`, two `app/src/test` files and
      `openspec/specs/unit-test-suite-runtime/spec.md`.

- [x] 6.2 Pin the contract's "a run with no accepted send in between", which review round 2 recorded as the one
      residual the change left: all three ADDED scenarios refuse without an accepted send, so a counter that
      never resets passed them identically. Add `aDeliveredTripResetsTheRejectionBudget` (refuse, accept, then
      refuse three more changed contents: five sends, a never-resetting counter stops after four) and the
      scenario *An accepted trip ends the run and resets the budget* that names it, in the delta and in the
      merged `openspec/specs/car-host-fault-isolation/spec.md`. Verify: with the delivered send's
      `rejectedUpdates = 0` removed (the `REVERT-CHECK MUTATION` line) the case reads
      `Verification failed: … 4 matching calls found, but needs at least 5 and at most 5 calls` @
      `2026-11-03T19:52:37Z` (`evidence/revert-check-red-reset-budget.xml`, `tests="1" failures="1"`); restored,
      `NavigationManagerControllerTest` reads `tests=12 failures=0` @ `2026-11-03T19:52:51Z`
      (`evidence/green-reset-budget-controller-cases.xml`) and `NavigationManagerControllerTripTest`
      `tests=9 failures=0` @ `2026-11-03T19:52:58Z`
      (`evidence/green-reset-budget-controller-trip-cases.xml`), and `bash tools/loop/measure.sh --change
      fix-rejected-trip-update-rearms-publishing` reads
      `LOOP-MEASURE-CHANGE … scenarios=15 cases=15 unnamed=0`, superseding task 4.1's 14.
