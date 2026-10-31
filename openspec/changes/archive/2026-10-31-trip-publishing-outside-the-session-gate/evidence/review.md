# Review — trip-publishing-outside-the-session-gate

Read-only. Base `9ceec64f`, worktree `.pi/worktrees/issue71`, branch `trip-publishing-outside-the-session-gate`.
No Gradle run. `git status --short`: `M NavigationSession.kt`, `M SessionHostGate.kt`, `M guidelines/Design.md`,
`?? NavigationSessionTripSendTest.kt`.

## Per-item verdicts

1. **FAIL (partial)** — both case names exist, but one case's assertions do not cover its scenario's THEN/AND lines.
2. **PASS** — delta is ADDED-only; shipped requirement byte-identical.
3. **PASS** — body 456 chars collapsed / 457 with newlines, ≤ 500.
4. **PASS** — no host-state call added or removed; one internal (non-host) delta noted below.
5. **FAIL (revert-check half only)** — tallies + verdict match the XML/log; the red revert-check run has no artefact.
6. **PASS** — no shipped spec contradicts the addition.
7. **FAIL** — copying `allowHostMutation()` onto the collector turns **no** test red.

---

### 1. Scenario ↔ Case coverage — FAIL (partial)

Delta scenarios at `openspec/changes/trip-publishing-outside-the-session-gate/specs/car-host-fault-isolation/spec.md:11-18` and `:20-24`.
Cases in `auto/src/test/java/com/naviveylin/auto/NavigationSessionTripSendTest.kt:28-40` and `:42-48`.

Scenario 2 (`spec.md:20-24`) ↔ `noTripSendOutsideTheNavigationPeriod` (`test:42-48`):
- WHEN/THEN quote: `**THEN** no trip update is sent to the host, whether or not the car session is started`.
- Assertions quote (`test:46-47`): `assertFalse(tripSendAllowed(navigating = false, sessionStarted = false))` /
  `assertFalse(tripSendAllowed(navigating = false, sessionStarted = true))`. Both session states driven. **Match.**

Scenario 1 (`spec.md:11-18`) ↔ `tripKeepsUpdatingWhileTheSessionIsNotStarted` (`test:28-40`):
- WHEN quote: `navigation is active, the car session is not started, and a displayed trip value (manoeuvre, rounded distance, remaining time) changes`.
- THEN quote: `the trip update is sent to the host`.
- AND quote: `the session records no deferred host sync for it and makes no `navigationStarted` or `navigationEnded` call`.
- Assertions quote (`test:32-39`): `assertTrue("a stopped session does not withdraw the trip send", tripSendAllowed(navigating = true, sessionStarted = false))`
  and `assertTrue("a started session still sends it", tripSendAllowed(navigating = true, sessionStarted = true))`.
- Gap: the case asserts only the guard predicate. It never exercises a displayed-value change, never asserts a send
  (`publishTrip` + `hasTripChanged` + `updateTrip`), and asserts **nothing** about the AND lines (no deferred sync,
  no `navigationStarted`/`navigationEnded`). The implementation does satisfy the AND (the trip collector at
  `NavigationSession.kt:727-736` calls neither `allowHostMutation()` nor `navigationStarted`/`navigationEnded`), but
  the case does not pin it. The second `assertTrue` (`sessionStarted = true`) is extra, not in the scenario.

### 2. ADDED-only, shipped requirement untouched — PASS

Delta file starts `## ADDED Requirements` (`spec.md:1`) and contains no `MODIFIED`/`REMOVED` block.
`git diff 9ceec64f -- openspec/specs/car-host-fault-isolation/spec.md` is empty; md5 of shipped lines 221-242
equals the base-commit md5 (`dc25606e2f63cd168bf7027ea4f24910` both sides). Requirement name not present in the
shipped spec (`grep -c` = 0). Shipped text at `openspec/specs/car-host-fault-isolation/spec.md:221-242` untouched.

### 3. Requirement body ≤ 500 chars — PASS

Body = `spec.md:5-9`. Raw count 457 (incl. line breaks), collapsed single-space count 456. Both ≤ 500.
`openspec validate trip-publishing-outside-the-session-gate --strict` → `Change '...' is valid`.

### 4. Observable behaviour change — PASS (with one internal caveat)

Collector `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt:732-735`.
`publishTrip` (`NavigationManagerController.kt:100`) `if (!navigating) return`, and `tripFromState`
(`NavigationTemplateMapper.kt:384`) `if (!state.isNavigating) return null`.

- No host call can be **added**: `tripSendAllowed` = `navigating` (`NavigationSession.kt:1373`); whenever the new
  guard passes, the old code ran, so the guarded path is a subset.
- No host call is **removed**: when `navState.isNavigating == false`, the old path called `publishTrip`, but the
  trip build returned `null` at `NavigationTemplateMapper.kt:384`, so `updateTrip` was never reached and no `HOST`
  diagnostics line was written (`NavigationManagerController.kt:117-127`). The new guard skips only that no-op.
- The controller's own `navigating` flag can be stale-true while backgrounded (navigation-state collector at
  `:707` defers `onNavigationEnded()` when `!hostGate.started`), so the design's stated reason "`publishTrip`
  already returns when navigation is inactive" is not the general guarantee; the actual guarantee is
  `tripFromState` returning `null`. Conclusion (no host call changes) still holds.

Caveat (internal, not host-visible): `navigationManagerController()` (`NavigationSession.kt:353-357`) is created
lazily. The trip collector was the **only unconditional** creation site (old `:733`; other sites `:712`, `:715`,
`:855`, `:858` need a navigation transition). A session that never navigates therefore no longer instantiates the
controller, so `onDestroy()` (`:304-305`) no longer attempts `navigationManagerEnded()` /
`clearNavigationManagerCallback()` on it. Those were caught best-effort no-ops on a never-started host session
(`NavigationManagerController.kt:115-120`). Not host-observable, but it is a deviation from a literal "pure
refactor" reading.

### 5. Evidence vs artefacts — FAIL (revert-check half)

- Tallies match exactly. XML sums: `core/build/test-results/testDebugUnitTest` 649, `auto/...` 910,
  `app/build/test-results/testMobileDebugUnitTest` 2212, `app/build/test-results/testAutomotiveDebugUnitTest`
  2212; total 5983; failures 0, errors 0 — equals `evidence/apply-report.md` "Gates" table.
- Named suites match: `NavigationSessionTripSendTest` tests=2, `NavigationManagerControllerTripTest` tests=9,
  `NavigationManagerControllerTest` tests=9, `SessionHostGateTest` tests=4, all 0 failures.
- Verdict line matches: `evidence/gate.log:219` `BUILD SUCCESSFUL in 7m 20s`.
- Killed run: `evidence/gate-killed-1037.log` has no `BUILD SUCCESSFUL|BUILD FAILED` (grep -c = 0), mtime
  `2026-10-31 10:37:28`, matching the report.
- Gap: the revert-check's **red** run (`tests="2" failures="1"`, failing case
  `tripKeepsUpdatingWhileTheSessionIsNotStarted`) exists only as prose in the report. No red XML/log artefact is
  stored; every retained results XML is green and `grep -rl '<failure\|<error'` over all four result dirs is
  empty. The red half is therefore unverifiable from artefacts.
- Extra gap: the gate ran `:osmscout-client-java:test` (`evidence/gate.log`) — 112 tests, 0 failures — and the
  report's tally omits it. The override total 5983 is not the whole gate.

### 6. No shipped spec contradiction — PASS

- `openspec/specs/auto-navigation-hints/spec.md` "Trip metadata for cluster and heads-up display": `It SHALL stop
  publishing when navigation ends` — matches `while navigation is inactive it SHALL send none`. "Trip publishing
  cadence": publish only on a displayed-value change — matches scenario 1's WHEN.
- `openspec/specs/car-host-fault-isolation/spec.md:221-242` "Bounded host-facing traffic while not visible": the
  new requirement names exactly the clause it interprets (`the started/ended pair and the screen and template
  mutations it names`) and carves trip metadata out. The shipped text uses "host navigation state" for
  `navigationStarted`/`navigationEnded` (also listed separately from "publishing trip metadata" in "No fault
  escapes into the host path"), so no contradiction; existing scenario "Guidance progresses while backgrounded"
  already requires the trip update while backgrounded.

### 7. Regression pinned? — FAIL

Copying `hostGate.allowHostMutation()` (`NavigationSession.kt:707`) onto the trip collector would **not** turn any
test red. `NavigationSessionTripSendTest` only calls the pure function `tripSendAllowed` (`test:34,38,46,47`); it
never exercises `startObserving()`/the collector, and `NavigationSession` is not constructible under Robolectric
(design D2B). No test reads `NavigationSession.kt` (the only `NavigationSession.kt` reference in tests is the
comment at `MapScreenTest.kt:280`). Replacing the call with `allowHostMutation()` leaves `tripSendAllowed` defined
and green. The regression is pinned **only if** the mutation is inside the seam body (`navigating && sessionStarted`),
which is what the revert-check does and which fails `tripKeepsUpdatingWhileTheSessionIsNotStarted`. The wiring
decision itself is unprotected.

---

## Holes / overclaims

- `design.md` D2/apply-report: "an author who adds `&& sessionStarted` turns the case red" is true; the broader
  claim that the deviation cannot regress at the call site (proposal "Make the deviation a named decision") is not
  — the call site is test-free (item 7).
- `design.md` "the wiring can only skip a call the controller would also have skipped" is imprecise: the
  controller's `navigating` can be stale-true while backgrounded; the real no-op is `tripFromState` returning null
  (`NavigationTemplateMapper.kt:384`). Conclusion unaffected.
- apply-report "No behavioural change: pure refactor" omits the lazy-controller-creation delta (item 4 caveat).
- Scenario 1's AND lines are not asserted by its Case (item 1).
- Revert-check red run has no stored artefact; report tally omits the executed `:osmscout-client-java:test`
  suite (112 tests).

## Should have touched but did not

- A test that actually reaches the collector decision (or a source-level guard) — otherwise item 7 stays open.
- The evidence directory could hold the red-run result or a log excerpt for the revert-check.
- The gate report's tally could include `:osmscout-client-java:test`.
- No change to `auto-navigation-hints` is needed (it already bounds by the navigation period); the new requirement
  does partially restate that capability's "Trip metadata for cluster and heads-up display". Cross-capability
  duplication is noted, not a defect for this change.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Per-item PASS/FAIL with file:line and quoted scenario/assertion/code lines; holes and missing touches listed."
    }
  ],
  "changedFiles": [],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "git diff 9ceec64f / git status --short / git show 9ceec64f:...",
      "result": "passed",
      "summary": "Read-only diff scope confirmed; shipped spec md5 identical."
    },
    {
      "command": "grep/sed/wc over delta spec, test file, XML results, evidence logs",
      "result": "passed",
      "summary": "Body char count 456/457; tallies 649/910/2212/2212 and 2/9/9/4; verdict line found."
    },
    {
      "command": "openspec validate trip-publishing-outside-the-session-gate --strict",
      "result": "passed",
      "summary": "Change is valid."
    }
  ],
  "validationOutput": [
    "Change 'trip-publishing-outside-the-session-gate' is valid",
    "gate.log: BUILD SUCCESSFUL in 7m 20s",
    "requirement body: 456 chars collapsed, 457 raw",
    "no <failure>/<error> tags in any retained results XML"
  ],
  "residualRisks": [
    "item 7: call-site regression (allowHostMutation on the collector) is not pinned by any test",
    "item 1: scenario 1 THEN/AND lines not asserted",
    "item 5: revert-check red run has no artefact; report omits :osmscout-client-java:test tally"
  ],
  "noStagedFiles": true,
  "diffSummary": "Review only; no files in the reviewed worktree were modified.",
  "reviewFindings": [
    "blocker: auto/src/test/java/com/naviveylin/auto/NavigationSessionTripSendTest.kt:28-40 - scenario 1 AND lines (no deferred sync, no navigationStarted/Ended) are unasserted",
    "blocker: NavigationSession.kt:732 - copying hostGate.allowHostMutation() onto the collector turns no test red",
    "note: evidence/apply-report.md revert-check red run has no stored artefact",
    "note: NavigationSession.kt:733 - lazy controller creation delta for a never-navigating session"
  ],
  "manualNotes": "Reviewer wrote only the requested artifact outside the repo worktree; the worktree was not modified."
}
```
