# Apply report — trip-publishing-outside-the-session-gate

Issue: `Framstag/NaviVeylin` #71 (`nvid: 56`). Base commit: `9ceec64f`.
Tree: git worktree `.pi/worktrees/issue71` on branch `trip-publishing-outside-the-session-gate` — isolated on
purpose, because four pi sessions share the main working tree (`guidelines/Build.md` §2a, rule 4).
Build environment: worktree has no `local.properties` (the permission policy blocks that path), so every Gradle
call passed `ANDROID_HOME=/home/tim/Android/Sdk` inline; the `libosmscout` submodule was initialized in the
worktree at the same pin as the main tree (`b5d045eb40fcadac029685599a30b571e7c1a5a1`, clean).

## What was changed

| File | Change |
|---|---|
| `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` | pure seam `tripSendAllowed(navigating, sessionStarted)`; trip collector (`:727`) consults it and passes `hostGate.started` |
| `auto/src/main/java/com/naviveylin/auto/SessionHostGate.kt` | KDoc: this gate does not bound the trip send, names the new requirement, names the wrong fix |
| `auto/src/test/java/com/naviveylin/auto/NavigationSessionTripSendTest.kt` | new; the two spec scenarios |
| `auto/build.gradle.kts` | `checkTripSendGate`, wired into `preBuild` beside the two existing checks |
| `buildSrc/src/main/kotlin/com/naviveylin/build/testing/TripSendGateScanner.kt` | new; the pure scanner behind that check (no allowlist) |
| `buildSrc/src/test/kotlin/com/naviveylin/build/testing/TripSendGateScannerTest.kt` | new; 10 cases, the shipped shape first |
| `guidelines/Design.md` | **not touched** — the parallel `guideline-layers` change moves its §8 to `DesignCrossVariant.md` §8a; the sentence to add afterwards is recorded in `proposal.md` |
| `openspec/changes/trip-publishing-outside-the-session-gate/` | the change itself (proposal, delta, design, tasks) |

**No host call changes**, with one internal delta (found by the independent review, see "Review" below).
`tripSendAllowed` is `navigating`, so whenever the new guard passes the old code ran: the guarded path is a
subset. In the other direction the guard skips only a no-op — when `navState.isNavigating` is false the trip
build returns `null` (`NavigationTemplateMapper.kt:384`), so `updateTrip` and the `HOST` diagnostics line were
never reached. (Not `publishTrip`'s own guard: the controller's `navigating` flag can be stale-true while
backgrounded, because the navigation-state collector at `:707` defers `onNavigationEnded()`.)

The internal delta: `navigationManagerController()` is created lazily (`:353-357`) and the trip collector was
its only *unconditional* creation site, so a session that never navigates no longer instantiates the
controller and `onDestroy` (`:304-305`) no longer attempts `navigationManagerEnded()` /
`clearNavigationManagerCallback()` on it. Those were best-effort no-ops on a session the host never saw
(`NavigationManagerController.kt:115-120`); not host-observable, but not a literal pure refactor either.

No manifest, resource, Gradle, CMake, vcpkg or ABI input touched; no log line added or changed.

## Scenario → case

| Spec scenario | Case | Tally |
|---|---|---|
| Trip keeps updating while the session is not started | `auto: NavigationSessionTripSendTest.tripKeepsUpdatingWhileTheSessionIsNotStarted` | green |
| No trip send outside the navigation period | `auto: NavigationSessionTripSendTest.noTripSendOutsideTheNavigationPeriod` | green |
| The trip send is not routed through the session gate | `auto: checkTripSendGate` (scanner cases `buildSrc: TripSendGateScannerTest.refusesTheGateQuestionOnTheTripCollector`, `…refusesACollectorThatDropsTheDecision`) | green |

Each case asserts its scenario's THEN **and** its AND lines (the review's finding 1): the send itself
(`verify(exactly = 1) { updateTrip(any()) }` through a real `NavigationManagerController`), the absence of a
start/end call, and `SessionHostGate.syncPending == false` — the trip send asks the gate no mutation question,
so it owes no deferred sync.

Focused run, `:auto:testDebugUnitTest --tests "com.naviveylin.auto.NavigationSessionTripSendTest"`:
`tests="2" skipped="0" failures="0" errors="0"` (`auto/build/test-results/testDebugUnitTest/TEST-com.naviveylin.auto.NavigationSessionTripSendTest.xml`).

Affected existing suites, same filter form, all green:

| Suite | Tally |
|---|---|
| `NavigationManagerControllerTripTest` | `tests="9" failures="0" errors="0"` |
| `NavigationManagerControllerTest` | `tests="9" failures="0" errors="0"` |
| `SessionHostGateTest` | `tests="4" failures="0" errors="0"` |

## Revert-check (the new invariant)

Mutation: `tripSendAllowed(...) = navigating && sessionStarted` — i.e. the fix issue #71 proposed first.

- Red: `:auto:testDebugUnitTest --tests "…NavigationSessionTripSendTest"` → `evidence/revert-check-red.xml`
  `tests="2" skipped="0" failures="1" errors="0"`, failing case
  `tripKeepsUpdatingWhileTheSessionIsNotStarted`, message "a stopped session does not withdraw the trip send";
  run log `evidence/revert-check-red.log` (`BUILD FAILED`).
- Restored: seam line back to `= navigating` (`grep` on the line recorded in the report; `git diff --stat` back
  to 20 insertions).
- Forced green: same filter with `-PforceTests --no-build-cache` → `evidence/revert-check-green.xml`
  `tests="2" skipped="0" failures="0" errors="0"`; run log `evidence/revert-check-green.log`.

Both halves are stored artefacts this time — the first attempt quoted the red tally in prose only, which the
review rejected (finding 5): a red claim needs its test result in the tree.

After the review's finding 1, the test file changed while the production files did not (they are byte-identical
to the gated state: `git diff --stat 9ceec64f` is still 20 insertions in `NavigationSession.kt`). The gate above
predates that edit, so the module was re-run: `:auto:testDebugUnitTest -PforceTests --no-build-cache` →
**BUILD SUCCESSFUL in 1m 54s**, `:auto tests=910 failures=0 errors=0` (`evidence/auto-after-review.log`), plus the
focused 2/0/0 above. A test-only delta needs the changed cases, not a second full gate
(`guidelines/Build.md` §2 scope block); the `:app` and `:core` tallies stand because no production file moved.

## Gates

All commands ran in the worktree with `ANDROID_HOME=/home/tim/Android/Sdk` inline (no `local.properties`
there — the permission policy blocks that path).

- `openspec validate trip-publishing-outside-the-session-gate --strict` → **valid**; `openspec doctor` → root
  ok, resolved to the worktree.
- Full gate, the profile's own command (`tools/loop/loop.conf:333`):
  `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` → **BUILD SUCCESSFUL in 7m 20s** before the
  review's fixes (`evidence/gate.log`) and **BUILD SUCCESSFUL in 7m 30s** after them (`evidence/gate2.log`, the
  run that also carries `:auto:checkTripSendGate`), tallies from the XML of the second run:

  | Module / suite | tests | failures | errors |
  |---|---|---|---|
  | `:core` (`testDebugUnitTest`) | 649 | 0 | 0 |
  | `:auto` (`testDebugUnitTest`) | 910 | 0 | 0 |
  | `:app` mobile | 2212 | 0 | 0 |
  | `:app` automotive | 2212 | 0 | 0 |
  | `:osmscout-client-java` (`test`) | 112 | 0 | 0 |
  | **total** | **6095** | **0** | **0** |

  Not a cached run: `-PforceTests --no-build-cache`, and `grep -l '<failure\|<error'` over every results XML
  returns nothing. The run also carried `:core:checkNoWallClockWaits`, `:auto:checkNoWallClockWaits`,
  `:auto:checkHardcodedStrings`, `:auto:checkTripSendGate` and `:app:checkNoWallClockWaits`.
- `./gradlew checkNoCoordinatesInLogs` → **BUILD SUCCESSFUL in 3s**, `:app:checkNoCoordinatesInLogs`
  executed (1 executed, 3 up-to-date) — a no-regression confirmation: this change adds and changes no log or
  diagnostics line (`evidence/log-gate.log`).
- No shell suite is owed: the profile's `LOOP_GATE_SUITES` maps no glob that this change's paths
  (`auto/src/**`, `buildSrc/**`, `guidelines/Design.md`) matches — checked, not assumed.
- `buildSrc` suite (the check's own cases live there): `./gradlew -p buildSrc test` → **168 tests, 0
  failures**, of which `TripSendGateScannerTest` is `tests="10" failures="0" errors="0"`.
- `checkTripSendGate` in place: `./gradlew :auto:preBuild` → **BUILD SUCCESSFUL**, with
  `> Task :auto:checkTripSendGate` executed (`evidence/check-green.log`).
- `checkTripSendGate` falsified: inserting `if (!hostGate.allowHostMutation()) return@collect` before the seam
  call — issue #71's own first fix candidate — fails the build with
  `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt:732: allowHostMutation( — the trip collector asks
  the session gate; the trip send is bounded by the navigation period, not by the session start` and the
  `no allowlist` line (`evidence/check-revert-red.log`, `BUILD FAILED`); restored → **BUILD SUCCESSFUL**
  (`evidence/check-green-after.log`).
- Gate re-run after the build-file change (`*.gradle.kts` is a compiled path in `LOOP_GATE_COMPILED_PATTERNS`):
  see `evidence/gate2.log` and the tally table below.

## Run hygiene (why two logs exist)

The first gate attempt ran in the foreground of one shell call. The call hit the harness's request cap and the
process group was killed mid-build, during the `:app` test compile: the log stops at 10:37:28 with **no verdict
line**, which is a killed run, not a failure (`guidelines/Build.md` §2b — "A suite run that died mid-flight is
not neutral"). It is kept as `evidence/gate-killed-1037.log`.

Recovery followed that section: the dead run's results were removed
(`rm -rf app/build/test-results auto/build/test-results core/build/test-results`, which also avoids the
`in-progress-results-generic.bin` abort), and the re-run was launched detached (`nohup … &`, `setsid` is not
allowlisted), then polled — the launcher survived the call boundary and the second run produced the verdict.

7m 20s is the cold-worktree price: this worktree compiles every module's main and test sources from scratch
(no build cache), on a machine where another session was running `:app:testMobileDebugUnitTest` in the main tree
at the same time.

## Device measurement

**No AVD run.** This change produces no pixel, no rendering behaviour, no diagnostics-line content and no
host-traffic volume change — there is nothing for a device run to measure, and a stationary emulator cannot
produce the backgrounded-navigation state deterministically (`guidelines/Build.md` §2 scope block; the
"say so explicitly instead of implying device proof" rule).

## Tracker

- The issue body's citations resolve as **paths** but their **line numbers are stale on HEAD**:
  `NavigationSession.kt:573-579` now holds `showStartupFailure` (the trip collector is at `:727`);
  `:664` is blank and `:684-690` sits in `geocodeDeepLink`; `NavigationManagerController.kt:88` is blank
  (`publishTrip` is at `:100`; only `:101`, the `navigating` gate, still holds);
  `NavigationTemplateMapper.kt:337-350` is inside `hasTripChanged`'s body (the function starts at `:463`).
  Repair is the resolver's write (`tools/issues/resolve-stale.sh`, dry-run by default) and a person's row —
  an agent process writes no citation (`guidelines/Backlog.md` §5).
- `agent/eligible` stays: it is the label `tools/issues/close.sh` gates a closure on, and closed items are out
  of `--eligible-only` since 2026-10-27.
- Closure entry prepared, not written — the closure rows are a person's or the loop's:

  > reason: the trip send was already bounded by the navigation period; the exemption is now declared in
  > `car-host-fault-isolation` ("Trip metadata publishing is bounded by the navigation period") and asserted by
  > `auto: NavigationSessionTripSendTest.tripKeepsUpdatingWhileTheSessionIsNotStarted`.
  > evidence: change `trip-publishing-outside-the-session-gate`, gate tallies above; the `status/on-hold` hold
  > lifted by the owner.

## Independent review

A fresh read-only child reviewed the diff, the delta, the design and the artefacts (writer-never-reviews rule).
Its report: `evidence/review.md`. Verdict per item, and what happened to each finding:

| # | Finding | Verdict | Action |
|---|---|---|---|
| 1 | Scenario 1's THEN/AND lines were unasserted — the case only checked the guard predicate | FAIL (partial) | **fixed**: the case now drives a real `NavigationManagerController` send, asserts no start/end call and `syncPending == false` |
| 2 | Delta ADDED-only, shipped requirement byte-identical (md5 match) | PASS | — |
| 3 | Requirement body ≤ 500 chars (456/457) | PASS | — |
| 4 | No host call added or removed | PASS | one internal delta documented above |
| 5 | Revert-check's red half existed only as prose; report omitted `:osmscout-client-java:test` | FAIL | **fixed**: `revert-check-red.xml` + `revert-check-green.xml` + both logs stored; tally row added |
| 6 | No shipped spec contradicts the addition | PASS | — |
| 7 | Copying `hostGate.allowHostMutation()` onto the collector turns **no** test red | FAIL | **fixed (option F)**: `:auto`'s `checkTripSendGate` + the buildSrc scanner now fail the build for that exact edit — falsified in `evidence/check-revert-red.log`; the unit cases alone had not caught it |

Residual risk, stated plainly: the *decision* is pinned by a unit case (the mutation the revert-check performs),
and the *collector's own line* is pinned by the build check added after this review (option F) — including the
shape guards that stop a rename from switching the rule off quietly. What no artefact can pin is a future edit
that moves the trip send somewhere else entirely; that would have to trip the check's "no trip collector found"
failure first.
