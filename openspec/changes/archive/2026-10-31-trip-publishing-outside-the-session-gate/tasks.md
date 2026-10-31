# Tasks

Parent spec for every task below: `car-host-fault-isolation`, the ADDED requirement **Trip metadata publishing
is bounded by the navigation period** (delta:
`specs/car-host-fault-isolation/spec.md` in this change). Scenarios and their cases:

| Scenario | Case |
|---|---|
| Trip keeps updating while the session is not started | `auto: NavigationSessionTripSendTest.tripKeepsUpdatingWhileTheSessionIsNotStarted` |
| No trip send outside the navigation period | `auto: NavigationSessionTripSendTest.noTripSendOutsideTheNavigationPeriod` |
| The trip send is not routed through the session gate | `auto: checkTripSendGate` (`buildSrc: TripSendGateScannerTest.refusesTheGateQuestionOnTheTripCollector`, `…refusesACollectorThatDropsTheDecision`) |

## 1. The decision is named in code

- [x] 1.1 Add the pure seam to
  `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt`: `internal fun tripSendAllowed(navigating:
  Boolean, sessionStarted: Boolean): Boolean` beside the file's existing seam cluster (`shouldRestoreFreeDriving
  :1352`), with KDoc naming the requirement above and stating that `sessionStarted` is deliberately not
  consulted.
  *Verify:* `./gradlew :auto:compileDebugKotlin` compiles without warnings.
- [x] 1.2 Route the trip collector (`NavigationSession.kt:727-733`) through the seam, passing
  `navState.isNavigating` and `hostGate.started`; leave the `scope.launch(CoroutineName("trip"))` shape and the
  `publishTrip` call unchanged.
  *Verify:* `./gradlew :auto:compileDebugKotlin`; a diff read confirms the collector skips a call
  `publishTrip` would also have skipped, so no message can be added.
- [x] 1.3 Extend the `SessionHostGate` KDoc (the paragraph "The session's observers must keep running while the
  app is backgrounded …") with the name of the new requirement.
  *Verify:* read-back of the paragraph; no code change.

## 2. The deviation is asserted

- [x] 2.1 New `auto/src/test/java/com/naviveylin/auto/NavigationSessionTripSendTest.kt`, one case per scenario:
  `tripKeepsUpdatingWhileTheSessionIsNotStarted` (`navigating = true, sessionStarted = false` ⇒ allowed — this is
  the case that fails if someone gates the send) and `noTripSendOutsideTheNavigationPeriod`
  (`navigating = false` ⇒ refused, both session states). Plain JUnit, no Robolectric needed (the seam is pure).
  Each case asserts its scenario's THEN **and** AND lines (the send itself through a real
  `NavigationManagerController`, no start/end call, `SessionHostGate.syncPending == false`) — strengthened after
  the independent review's finding 1.
  *Verify:* `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.NavigationSessionTripSendTest"` —
  expect: 2 tests, 0 failures.
- [x] 2.2 Run the affected existing suites and record the XML tallies:
  `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.NavigationManagerControllerTripTest" --tests
  "com.naviveylin.auto.NavigationManagerControllerTest" --tests "com.naviveylin.auto.SessionHostGateTest"`.
  *Verify:* all green, counts quoted in the apply report. Exclusion: none — these three are the suites the
  collector and the gate reach; the rest of `:auto` and all `:app` unit tests are covered by task 4.2.

## 3. Revert-check for the new invariant

- [x] 3.1 Mutate `tripSendAllowed` to `navigating && sessionStarted`, run
  `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.NavigationSessionTripSendTest"`, confirm
  `tripKeepsUpdatingWhileTheSessionIsNotStarted` fails, restore the source, then re-run the same filter forced
  green: `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.NavigationSessionTripSendTest"
  -PforceTests --no-build-cache`.
  *Verify:* both halves stored as artefacts — `evidence/revert-check-red.xml`
  (`failures="1"`, failing case named) + `evidence/revert-check-red.log`, and `evidence/revert-check-green.xml`
  (`failures="0"`) + `evidence/revert-check-green.log`; the seam line is back to `= navigating` and
  `git diff --stat` shows 20 insertions.

## 4. The change is verified

- [x] 4.1 **Superseded at reintegration** — the guideline reference is *not* added: the parallel
  `guideline-layers` change moves `guidelines/Design.md` §8 to `DesignCrossVariant.md` §8a, so this change leaves
  the guideline untouched and records the sentence to add afterwards (`proposal.md`, "Guideline reference").
  *Verify:* `git status --short` shows no `guidelines/Design.md` modification; the sentence is in the proposal.
- [x] 4.2 Full gate, both flavors, once (`guidelines/Build.md` §2 and §4): the app builds and every unit and
  instrumented suite the gate covers is green.
  *Verify:* the gate's verdict line, plus the run's flavor/suite coverage per `Build.md` §4b. Exclusions: none.
- [x] 4.3 Offline gate check: `./gradlew checkNoCoordinatesInLogs` still passes. This change alters no log or
  diagnostics line (`NavigationManagerController`'s `HOST` trip line is untouched), so the check is a
  no-regression confirmation, not a new claim.
  *Verify:* task output, no violations.
- [x] 4.4 Structural: `openspec validate trip-publishing-outside-the-session-gate --strict` passes, and
  `openspec doctor` reports no config problem (the artifact-hygiene gates in `.github/workflows/build.yml`).
  *Verify:* both commands' output quoted.
- [x] 4.5 Record the device decision: write into the apply report that **no AVD run is performed** and why —
  the change produces no pixel, no rendering behaviour, no diagnostic-line content and no host-traffic volume
  change, and a stationary emulator cannot produce the backgrounded-navigation state deterministically
  (`guidelines/Build.md` §2 scope block; the "say so explicitly instead of implying device proof" rule).
  *Verify:* the paragraph exists in the report and names this reason.

## 5. The tracker item is settled

- [x] 5.1 Repair the stale citations of issue #71 (`nvid: 56`): run `tools/issues/resolve-stale.sh` for the
  item and record what it rewrites and what it only reports (`NavigationSession.kt:573-579`, `:664`, `:684-690`;
  `NavigationManagerController.kt:88`, `:101`; `NavigationTemplateMapper.kt:337-350`; HEAD has the collector at
  `:727-733`, `publishTrip` at `:100`, `hasTripChanged` at `NavigationTemplateMapper.kt:463`).
  *Verify:* the script's output; for an agent process it is report-only, so an unrepaired citation is named in
  the report instead of being written.
- [x] 5.2 Prepare the closure entry for #71 and hand it over: reason ("the send was already bounded by the
  navigation period; the exemption is now declared in `car-host-fault-isolation` and asserted by
  `NavigationSessionTripSendTest`"), evidence (requirement name, case name, this change's archive path), and the
  note that the loop's `status/on-hold` hold is lifted by the owner. Do **not** call `tools/issues/close.sh`
  from the agent process: `guidelines/Backlog.md` §5 gives the closure to a person or to the loop after a passing
  review.
  *Verify:* the prepared text is in the apply report; the item still open afterwards, with the owner's next
  action named.
- [x] 5.3 After archive, confirm the shipped spec carries the requirement and both cases:
  `grep -n "Trip metadata publishing is bounded by the navigation period" openspec/specs/car-host-fault-isolation/spec.md`
  and `grep -c "NavigationSessionTripSendTest" openspec/specs/car-host-fault-isolation/spec.md` (expect 2).
  *Verify:* both greps' output.

## 6. Independent read-only review (writer-never-reviews)

- [x] 6.1 Finding 1 — scenario 1's THEN/AND lines were unasserted: the case now drives a real send and asserts
  the absence of a start/end call and of a deferred sync.
  *Verify:* focused run green (2/0/0); assertions named in `evidence/apply-report.md`.
- [x] 6.2 Finding 5 — the revert-check's red half existed only as prose: both halves are stored
  (`evidence/revert-check-red.xml`, `revert-check-green.xml`, both run logs).
  *Verify:* `failures="1"` in the red XML with the failing case named; `failures="0"` in the green one.
- [x] 6.3 Finding 5 — the gate tally omitted `:osmscout-client-java:test`: 112 tests, 0 failures added, total
  6095.
  *Verify:* the report's tally table against `osmscout-client-java/build/test-results/test/*.xml`.
- [x] 6.4 Findings 4 and 7 — the overclaims are corrected: `proposal.md` and `evidence/apply-report.md` no
  longer say "pure refactor"; the lazy-controller-creation delta is stated; the design's risk row no longer
  claims a case goes red on the collector variant.
  *Verify:* read-back of the three documents.
- [x] 6.5 The review itself is kept: `evidence/review.md` (its per-item verdicts and the residual-risk list).
  *Verify:* the file exists and names findings 1-7.
- [x] 6.6 **Finding 7 closed (option F)** — a source-level check polices the call site: `:auto`'s
  `checkTripSendGate` (`auto/build.gradle.kts`, wired into `preBuild` beside the two existing checks) with the
  pure buildSrc scanner `com.naviveylin.build.testing.TripSendGateScanner` and its cases
  (`buildSrc/src/test/kotlin/com/naviveylin/build/testing/TripSendGateScannerTest.kt`, 10 cases).
  *Verify:* `./gradlew -p buildSrc test` → `TripSendGateScannerTest` `tests="10" failures="0" errors="0"`, whole
  buildSrc suite 168/0/0; `./gradlew :auto:preBuild` green with `:auto:checkTripSendGate` executed
  (`evidence/check-green.log`).
- [x] 6.7 Revert-check for the new check: insert `if (!hostGate.allowHostMutation()) return@collect` before the
  seam call in the trip collector → `:auto:preBuild` fails, naming
  `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt:732`, the call, the rule and the spec; restore →
  green.
  *Verify:* `evidence/check-revert-red.log` (`BUILD FAILED` + the finding) and `evidence/check-green-after.log`
  (`BUILD SUCCESSFUL`); `git status --short` back to the four intended files plus the two new sources.
- [x] 6.8 Delta scenario added for the check — "The trip send is not routed through the session gate" — with its
  case named in the scenario.
  *Verify:* `openspec validate trip-publishing-outside-the-session-gate --strict`.
- [x] 6.9 Gate re-run after the build-file change (`*.gradle.kts` is a compiled path in the profile's
  `LOOP_GATE_COMPILED_PATTERNS`): `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` →
  **BUILD SUCCESSFUL in 7m 30s**, tallies `:core` 649/0/0 · `:auto` 910/0/0 · `:app` mobile 2212/0/0 ·
  `:app` automotive 2212/0/0 · `:osmscout-client-java` 112/0/0 (6095 total, 0 failures), and
  `:auto:checkTripSendGate` among the executed tasks.
  *Verify:* the verdict line and tallies in `evidence/gate2.log`.
