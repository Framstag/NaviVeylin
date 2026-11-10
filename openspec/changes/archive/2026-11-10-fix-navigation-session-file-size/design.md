# Design — fix-navigation-session-file-size

## Root cause (one)

`NavigationSession.kt` holds the session class *and* every pure decision seam its cases reach, because a seam
was typed in where the rule was read. The class ends at line 1327 of `NavigationSession.kt` on base `c0ddb97c`
(that line no longer exists in the shortened file; the base's block is kept as `evidence/seams-head.txt`);
`:1329`–`:1589`
is twenty-two top-level declarations (`grep '^(internal|private) (fun|const val)'` → 22, `evidence/surface.log`)
that read no session state. So the file's length is the session plus the sum of the rules, and any rule that
lands there is one more line of the same number. The measurement is per file
(`tools/sensors/metrics/source-size.sh` counts `git ls-files -z '*.kt' '*.java'` and compares its `awk` record
number against the row's threshold), so a file carrying both is over the bound exactly when the two together
are.

## The single fix

Cut the file where the class ends: the twenty-two top-level declarations move, in order and unchanged, into
`NavigationSessionSeams.kt`, which carries a package line, the eight imports they need and a header stating why
they are top-level. Nothing else moves.

| file | what it holds | lines |
|---|---|---|
| `NavigationSession.kt` | the class: collaborators, state, the lifecycle observer and the presence publishes' call sites, warmup, deep-link entry, the observation start/stop, the screen-stack presentation, the notice lifecycle, the error notice, the companion | 1 589 → **1 324** |
| `NavigationSessionSeams.kt` (new) | `deepLinkLogMessage`, `noticeNeedsUpdate`, `needsNoticeArm`, `calculationWaitOnSurface`, `FailureReason.isCalculationFailure`, `calculationFailureShownOnSurface`, `surfaceFailureReportElapsed`, `noticeIsStale`, `CALCULATION_NOTICE_MIN_DWELL_MS`, `calculationNoticeDwellRemainingMs`, `noticeIsStillArmedFor`, `isNightUiMode`, `shouldRestoreFreeDriving`, `tripSendAllowed`, `shouldEndNavigationOnSessionEnd`, `endNavigationAfterArrival`, `publishCarSessionPresenceOnStart`, `publishCarSessionPresenceOnDestroy`, `publishCarSessionPresence` (private), `sessionEndNavigationDecisionMessage`, `carTripFor`, `resolveCarDark` | **284** |

**The identity the move keeps.** `evidence/seams-head.txt` is HEAD's `:1329`–`:1589`; `evidence/line-accounting.log`
is `diff -u` of that file against the new file's body — empty. 261 lines moved, one 23-line header added, 284
lines in all. The three
imports the class no longer uses (`androidx.car.app.navigation.model.Trip`,
`com.naviveylin.core.CarSessionPresence`, `com.naviveylin.core.StringResolver`) are dropped from
`NavigationSession.kt` and the same three are the imports the new file needs; `Configuration`,
`stringResolver`, `DeepLinkResult`, `FailureReason`, `RouteCalculation` and `NavigationState` are still used by
the class body (the configuration callback at `:224`, the trip publisher's `carContext.stringResolver()` at
`:741`, and the class's own signatures), so they stay in both.

**Why the seams need no visibility change.** Every declaration that moved is `internal` (or `private` inside
the moved block) and top-level, so an extension-free call resolves across files of the same package with no
edit at the call sites: `carTripFor` at `NavigationSession.kt:741`, the two presence publishes at `:266` and
`:307`, `isNightUiMode` and `resolveCarDark` in the configuration path, `calculationNoticeDwellRemainingMs` in
`scheduleCalculationNoticeRemoval`. The one `private` function that moved, `publishCarSessionPresence`, is
called only by the two publishes that move with it, so it stays private in its new file.

**The one realignment the move forces.** `CalculationNoticeDwellTest` is the only place that names a JVM
facade class: `Class.forName("com.naviveylin.auto.NavigationSessionKt")` at `:49`, repeated in the
`AssertionError` at `:51`. A Kotlin file's facade class is named after the file, so after the move the seam
lives in `com.naviveylin.auto.NavigationSessionSeamsKt`. The case's two strings and its `:40` wording follow
the seam; every assertion, count and number in the case is untouched. The reason the case reflects rather than
calls — a red case must fail in a JUnit run rather than at compile time — is unchanged by the move.

## Alternatives the evidence eliminated

1. **Retune the row, or baseline this path** (`tools/sensors/sensors.json`, the `source-size` row's
   `threshold`/`baseline`): impossible as a fix — the bound is the owner's recorded decision of 2026-10-21
   (1 500 lines, file level, tests included, `guidelines/BacklogTooling.recipes.md` §9), and a baseline makes
   the row stop reporting the condition it exists to report. The item's own lifecycle is "the path is the
   identity, so an item closes when that file drops under the bound".
2. **A guard case in the suite that asserts the file's line count**: impossible — it duplicates the row, whose
   scope is every tracked `.kt`/`.java` file, and it would redden the build for the other files past the bound
   that are other items' work (1 498, 1 367, 1 312, 1 251 lines; the same probe, `evidence/red-probe-head.log`).
   It also gives one file two owners.
3. **Extracting the class's own members into extension files**, the way `AutoMapRenderer` was split: rejected
   for this item. The classes are not alike: `AutoMapRenderer`'s groups (loops, motion, render, markers) are
   topics, while the session's members are one screen-stack machine whose state every member reads, so a
   member cut forces its fields to `internal` and moves the dispatch a mock could intercept — a behaviour
   change for the sake of a number, and not what the item asks. The file's foot is the one part that is already
   independent of the state, and cutting there is 261 lines of pure move against ~250 lines of visibility
   churn.
4. **Deleting or inlining seams** (dropping `noticeNeedsUpdate`, folding `resolveCarDark` into the class):
   impossible — each seam is the acceptance surface of a shipped requirement (`route-calculation-feedback`,
   `car-session-presence`, `auto/navigation-view`, `car-host-fault-isolation`) and its case would lose what it
   asserts, which trades a size bound for a spec delta nobody gated.
5. **Splitting the class file without touching the test's facade string** by leaving one function behind: it
   keeps `NavigationSessionKt` alive and saves the one-string realignment, but it leaves an orphan declaration
   in the file the item is about — the same defect, 261 lines smaller, and a reader would have to ask why that
   one function stayed. A facade class's name is not a rule; the case is realigned instead.

## The guideline this change owes, and why it is none

The owning sections are `guidelines/BacklogTooling.md` §9 "Checks" (`:81`, the row set `source-size` belongs to)
and `guidelines/BacklogTooling.recipes.md` §9 (`:90`–`:98`: the unit, the scope, the bound's owner's decision).
Neither states a rule about how an over-long file is split, and this diff adds no rule, changes no bound, and
names no new condition — it makes the existing rule hold for one path. So the answer this item owes its fourth
grading question is "the section carries no edit this diff owes", and the citation of both sections above is
the claim that was checked, not a promise.

## Blast radius

- 1 file created, 1 file shortened by 265 lines (261 moved out, one blank line, 3 imports dropped), 1 test
  file's two strings and one message realigned.
- 0 spec texts, 0 guidelines, 0 tools, 0 Gradle logic, 0 resources, 0 native sources, 0 new cases.
- The `:auto` suite is unchanged in kind and in count: 98 classes, 958 cases, one result XML per class.
- Two comments cite a line of the class file, and both citations were stale before the cut — the class file
  had grown under them — while the cut dropped three imports, so every class line moved up by three. Both are
  realigned in this round: `NavigationSessionTripSendTest.kt:29` now names `NavigationSession.kt:740` (the
  collector's `publishTrip` call, the call its next sentence replaces) and `MapScreenTest.kt:280` now names
  `NavigationSession.kt:504` (the warmup's `openMapDatabases` call) (`evidence/surface-round2.log`).

## Verification

- The row's own probe before (`1 589`, `violation`, `evidence/red-probe-head.log`) and after (`1 324`,
  `clear`, `evidence/green-probe.log`) under the item's fingerprint `f0611efc…`.
- The moved declarations resolve across the package boundary: the focused run of task 3.1 and the gate's gradle
  step (`evidence/loop-gate/`) both compiled these sources clean, and the step's own `system-out` carries
  `BUILD SUCCESSFUL` with no `e:` line.
- `./gradlew :auto:testDebugUnitTest -PjvmTestsOnly -PforceTests --offline --tests 'com.naviveylin.auto.*'` →
  `evidence/focused-tally.log`: 98 classes, 958 tests, `failures=0 errors=0`, each XML copied to the run's
  evidence directory as it was produced.
- The project's gate is **this** child's. The gate of round 1 measured a tree that was still open and that was
  archived and re-worded afterwards, so it did not measure the tree a round reads (the round-1 review's
  blocking finding). This round's gate ran once, on the archived tree with the prose here final: its line and
  its eight step tallies are in `evidence/evidence.md` and `evidence/loop-gate/`, copied as they were produced,
  and in the run's evidence directory. The gate's own tally directory is the one `tools/loop/loop.conf` names
  (`app/build/test-results/loop-gate/`).
- `.openspec.yaml` sets `skip_specs: true`: the split moves text between two files of one package, changes no
  behaviour, no bound, no condition and no requirement, so there is no delta to write against a capability spec
  (which is why this change carries no `specs/` directory and archiving merges nothing into one). The owning
  requirement is read, not edited — `backlog-sensors`, "A source file's size is a declared check"
  (`openspec/specs/backlog-sensors/spec.md:471`).
- No on-device task: no UI, rendering, surface or diagnostic line changes.
- No new invariant, bound, predicate or refusal path is introduced, so there is no revert-check mutation to
  name (`revert-check`'s question — does this delta state a rule the tree could lose silently — is answered
  "none": the rule is the row's, unchanged, and `evidence/seams-head.txt` against the new file is its byte
  check).

## Rollback

Revert the change's commits: `NavigationSessionSeams.kt` disappears, `NavigationSession.kt` returns to 1 589
lines, `CalculationNoticeDwellTest`'s two strings return to `com.naviveylin.auto.NavigationSessionKt`, the two
test comments return to their old citations, and the row's probe prints `measurement=1589 verdict=violation`
again. Nothing outside `auto/src` moves, so no other rollback step exists.

## Corrections in round 2 (the round-1 review)

The round-1 review's blocking finding was that the gate had run before this change was archived and its prose
re-worded, so the gate did not measure the tree the round read. This round therefore: archived nothing (the
change was already archived), made the prose below final before the gate, and ran the gate once on that tree.

The findings applied, all of them in the record and none of them in the production code's behaviour:

- The two test comments that cite a line of the class file are realigned (`NavigationSession.kt:740` and
  `NavigationSession.kt:504`), and the design's own call-site citations are corrected to the shortened file
  (`:224`, `:266`, `:307`, `:741`).
- The move's arithmetic is quoted one way: 261 lines moved, one blank line and three imports dropped, 265
  lines shorter, 284 lines in the new file (23 of them the header).
- The `tools/look.sh` quotes carry their scope and the tree they were taken on.
- The claims that were stated without a log carry one now: `evidence/surface-round2.log` (the recounted
  surface), `evidence/line-accounting.log` (the byte check, now timestamped), `evidence/green-probe-round2.log`
  (the row's probe on this tree), `evidence/openspec-validate-round2.log` (what the validation says on the
  archived tree) and `evidence/evidence-check-round2.log` (the evidence check over this directory).
