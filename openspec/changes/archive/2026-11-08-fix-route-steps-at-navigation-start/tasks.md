# Tasks

Parent spec: `navigation-engine` (MODIFIED — "One navigation state shared by all surfaces"). Order: reproduce,
seed, falsify, state the rule in its two owners, bookkeeping. The item's gate is deferred: the run's gate child
runs it after the reviewer, so no task here runs `./tools/loop/gate.sh`.

## 1. Reproduce the defect (red on HEAD)

- [x] 1.1 Write the two cases in `app/src/test/java/com/naviveylin/navigation/NavigationEngineStepIndexTest.kt`
  with a `routeEntryWithSteps(vararg steps)` helper, following the class's existing harness (`engineUnderTest`,
  `FakeOSMScoutClient`, `awaitState`): `startNavigation_publishesTheRoutesStepsBeforeAnyPositionUpdate` starts
  navigation on an entry carrying three steps and asserts the state's list carries them in route order with the
  index at 0 — no position is delivered, so nothing else can fill the field; and
  `startNavigation_clearsStaleStepsFromPreviousRoute` is rewritten to restart on an entry carrying two *different*
  steps and assert the state carries those and no step of the route before it (its old `steps.isEmpty()`
  assertion only held because its entry carried no steps). Spec: `navigation-engine` — The started route's steps
  are in the state before the first position update; A start replaces the previous route's steps. Verification:
  the red-on-HEAD run is retained as `.pi/issue-loop/items/719/TEST-NavigationEngineStepIndexTest-red-on-HEAD.xml`
  — `tests="3" skipped="0" failures="2" errors="0"`, `timestamp="2026-11-08T01:50:03.391Z"`, failing as
  `java.lang.AssertionError: expected:<[100.0, 500.0, 900.0]> but was:<[]>`
  (`NavigationEngineStepIndexTest.kt:130`) and `expected:<[700.0, 950.0]> but was:<[]>`
  (`:110`); command
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.navigation.NavigationEngineStepIndexTest" -PforceTests --no-build-cache --rerun-tasks`.

## 2. Seed the start state with the started route's steps

- [x] 2.1 Replace `steps = emptyList()` in `startInternal`'s state publication
  (`app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt:421`) with the steps the start was handed:
  `steps = routeEntry.steps?.toList() ?: emptyList()`, and state in the comment why the live dispatch cannot
  supply them at a start and that the first dispatch replaces the seed. Spec: `navigation-engine` — One
  navigation state shared by all surfaces. Verification: the forced focused run is green —
  `tests="3" skipped="0" failures="0" errors="0"`, `timestamp="2026-11-08T01:52:14.074Z"`, retained as
  `.pi/issue-loop/items/719/TEST-NavigationEngineStepIndexTest-green-focused.xml`; the same command as 1.1.
- [x] 2.2 Check the siblings the seed could move: every other session-start fixture in `app/src/test` hands
  `start`/`acquire` an entry whose `steps` is absent, so each seeds the empty list it had before.
  Verification: `tools/look.sh 'engine\.start\(|vm\.start\(|\.start\(routeEntry' --path app/src/test` →
  `14 matches in 10 files`, and `tools/look.sh 'steps = arrayOf|steps = \[' --path app/src/test` → the five
  fixtures that build a `RouteEntry` with steps, none of which starts a session
  (`RouteStepFixtures.kt:129`, `app/src/test/java/com/naviveylin/ui/route/RouteGeometryTest.kt:32`, `RoutePanelViewModelSessionTest.kt:112`,
  `RouteRowDerivationTest.kt:178`, plus the two pure-function cases in `NavigationEngineTest.kt:341`, `:367`).

## 3. Falsify the seed (one mutation)

- [x] 3.1 Revert-check, one mutation (`.pi/skills/revert-check`): put the field's default back —
  `steps = emptyList(), // REVERT-CHECK MUTATION: the seed removed (the pre-fix behaviour)` at
  `NavigationEngine.kt:421` — and confirm both guard cases fail for the right reason; restore, confirm no marker
  is left, then re-run forced. Verification: mutation XML
  `.pi/issue-loop/items/719/TEST-NavigationEngineStepIndexTest-revert-check-mutation.xml` —
  `tests="3" skipped="0" failures="2" errors="0"`, `timestamp="2026-11-08T01:53:24.469Z"`, failing as
  `expected:<[700.0, 950.0]> but was:<[]>` and `expected:<[100.0, 500.0, 900.0]> but was:<[]>` (the third case
  asserts the live dispatch, which this mutation does not touch);
  `tools/look.sh 'REVERT-CHECK MUTATION' --path app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt`
  → `0 matches`; restored green run `tests="3" skipped="0" failures="0" errors="0"`,
  `timestamp="2026-11-08T01:54:30.884Z"`, retained as
  `.pi/issue-loop/items/719/TEST-NavigationEngineStepIndexTest-green-after-restore.xml`.

## 4. State the rule in its owners and close the map

- [x] 4.1 The owning guideline section carries the edit this diff owes: `guidelines/Design.md` §3 "ViewModel &
  state", the bullet "Progress, wall-clock and list state start at the invariant, not at the data-class default"
  — the step list joins `remainingDistance = totalDistance` as the bullet's example, with the capability it
  serves. Verification: the bullet's two examples now name both start fields the change touches
  (`Design.md:97-102`), and `./tools/check-doc-routes.sh` still passes over the file (task 4.3).
- [x] 4.2 Every scenario in the delta names a case that exists, and every case the delta names exists with that
  name: `openspec validate fix-route-steps-at-navigation-start --strict` exits 0, and both `**Case**:` lines
  resolve to the two `fun startNavigation_…(` declarations in
  `app/src/test/java/com/naviveylin/navigation/NavigationEngineStepIndexTest.kt`.
- [x] 4.3 The pathspec's checks are the ones `./tools/loop/measure.sh --change
  fix-route-steps-at-navigation-start` prints, and each is run by the gate child, not here (the item's gate is
  deferred). Verification: the block printed for this change is quoted in the worker's report.

## 5. Bookkeeping

- [x] 5.1 Name the one thing this change leaves alone, in the report, so the reviewer meets it named: the live
  step ahead (`NavigationState.nextStep`) is not seeded — its value, its remaining distance and its next-next
  window belong to the native builder, and the surfaces already fall back to the seeded list entry
  (`NavigationTemplateMapper.kt:213-215`, `NavigationNotificationContent.kt:151`). No code comment or spec line
  in this change claims otherwise.
- [x] 5.2 Evidence lands beside the item (`.pi/issue-loop/items/719/`), not in the change directory: the red XML,
  the green focused XML, the mutation XML and the green-after-restore XML are copied there at the moment each
  run produced them. Verification: `ctx_ls .pi/issue-loop/items/719` lists all four with the run's timestamps.
