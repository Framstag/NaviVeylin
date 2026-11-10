# Tasks — fix-navigation-session-file-size

No requirement changes, so no task changes a spec; each task names the shipped requirement that holds the claim
it verifies. Parent requirement: `backlog-sensors` — "A source file's size is a declared check"
(`openspec/specs/backlog-sensors/spec.md:471`), the row whose clear closes item #835.

## 1. Before the cut — the numbers the delta must move, and the surface it touches

- [x] 1.1 Take the red number with the row's own measurement rather than a second copy of it:
  `SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS='[{"state":"OPEN","condition":{"path":"auto/src/main/java/com/naviveylin/auto/NavigationSession.kt"}}]' bash tools/sensors/metrics/source-size.sh`
  → `"measurement":"1589","verdict":"violation"` under fingerprint `f0611efc…` on base `c0ddb97c`, clean tree
  (`evidence/red-probe-head.log`); the item recorded 1 546 when it was filed, so the claim holds and the file
  grew. Parent: `backlog-sensors` — one item per path, closed when that path drops under the bound.
- [x] 1.2 Record HEAD's `:1329`–`:1589` as the text the split must move and not retype
  (`evidence/seams-head.txt`), and count what it holds: twenty-two top-level declarations, no member
  (`tools/look.sh '^(internal|private) (fun|const val)'` → 22, `evidence/surface.log`). Parent:
  `unit-test-suite-runtime` — every case that reaches a seam keeps reaching it.
- [x] 1.3 Count the surface before the plan was fixed: 20 files in `auto/src` name `NavigationSession`, 1 names
  the file's JVM facade class (`CalculationNoticeDwellTest.kt:49`, `:51`), 0 specs and 0 guidelines and 0 tools
  name the path, and two comments cite a line of the file (`NavigationSessionTripSendTest.kt:29`,
  `MapScreenTest.kt:280`) (`evidence/surface.log`, recounted on the cut tree in
  `evidence/surface-round2.log`). Parent: `documentation-ownership` — a guideline's or a test's `file:line`
  claim is what a split can dangle, and both of these were already stale before the cut: the class file had
  grown under them. Round 2 realigns them to `NavigationSession.kt:740` and `NavigationSession.kt:504`.

## 2. The cut

- [x] 2.1 Create `auto/src/main/java/com/naviveylin/auto/NavigationSessionSeams.kt`: a package line, the eight
  imports the moved declarations read, a header saying why they are top-level and what item moved them, and
  then HEAD's `:1329`–`:1589` verbatim in its own order. Verified by 2.4.
- [x] 2.2 Delete those lines from `NavigationSession.kt` so the file ends at the class's closing brace, and drop
  the three imports the class no longer uses (`Trip`, `CarSessionPresence`, `StringResolver`); the ones the
  class body still reads stay. Verified by 2.3 and 2.4.
- [x] 2.3 Realign the one case that names a facade class: `CalculationNoticeDwellTest`'s
  `Class.forName("com.naviveylin.auto.NavigationSessionKt")` and its `AssertionError` string become
  `…NavigationSessionSeamsKt`, and its `:40` wording follows the seam. No assertion, count or number changes.
  Verified by 3.1.
- [x] 2.4 Prove the move is a move: `diff -u` of `evidence/seams-head.txt` against the new file's body (lines
  24–284) → empty (`evidence/line-accounting.log`).
- [x] 2.5 Compile the source sets: the moved declarations resolve across the package boundary — the focused run
  of 3.1 and the gate's gradle step (`evidence/loop-gate/`) both compiled these sources clean, and the step's
  own `system-out` carries `BUILD SUCCESSFUL` with no `e:` line.

## 3. Verification — scoped by what changed (production sources and one test source of `:auto`)

- [x] 3.1 Run the affected cases: `./gradlew :auto:testDebugUnitTest -PjvmTestsOnly -PforceTests --offline
  --tests 'com.naviveylin.auto.*'` → 98 XMLs, `tests=958 failures=0 errors=0`
  (`evidence/focused-tally.log`, reproducible with `bash evidence/tally.sh`); each XML copied to the run's
  evidence directory at the moment it was produced. Parent: `unit-test-suite-runtime`.
- [x] 3.2 Take the green number with the same probe as 1.1 → `"measurement":"1324","verdict":"clear"` under the
  same fingerprint (`evidence/green-probe.log`): the row's clearing pass is what closes the item. Parent:
  `backlog-sensors`.
- [x] 3.3 Run the project's gate once, on the tree this round reads: the change was already archived and this
  prose was final before the gate ran, so the gate measured the tree it names. (The gate of round 1 ran before
  the archive and before the prose was re-worded, and did not measure it — the round-1 review's blocking
  finding.) Its line and the eight step tallies are in `evidence/evidence.md` and `evidence/loop-gate/`, copied
  as they were produced, and in the run's evidence directory. Parent: `build-test-gate`.

## 4. What the tail owns

- [x] 4.1 Copy every cited XML into this change's `evidence/` directory and quote the gate's tallies in
  `evidence/evidence.md` beside the XML that carries them: the 98 `TEST-*.xml` from the focused run, the
  gate's 8 step tallies in `evidence/loop-gate/`, the sums `evidence/focused-tally.xml`
  (tests="958" failures="0" errors="0") and `evidence/gate-sum.xml` (the sum of the eight step tallies beside
  it), every one of them also copied to the run's evidence directory. Parent: `build-test-gate`.
- [x] 4.2 `.openspec.yaml` declares `skip_specs: true` with the reason in `design.md` (no requirement changes, so
  no delta is invented). The archived directory is no longer a validatable item, so the validation is re-taken
  as it stands: `openspec validate fix-navigation-session-file-size --strict` answers `Unknown item` (the
  change is archived) and `openspec list --changes` names no such change
  (`evidence/openspec-validate-round2.log`).
- [x] 4.3 The change is archived and the directory exists under `openspec/changes/archive/`:
  `openspec/changes/archive/2026-11-10-fix-navigation-session-file-size/` (archived by the round-1 tail with
  `openspec archive fix-navigation-session-file-size --yes`; `skip_specs: true` so the archive merged no delta
  and `openspec/specs/backlog-sensors/spec.md:471` still carries the requirement).
  `tools/loop/evidence-check.sh` re-run over the archived directory after this round's corrections:
  `evidence/evidence-check-round2.log`.
- [x] 4.4 Do not commit, do not push, do not touch the tracker: the run's script owns git and the labels —
  no `git` command and no tracker call was made by this tail.

## 5. Round 2 — the review's corrections, and the gate that measures this tree

- [x] 5.1 Apply the round-1 review's findings: the two test comments' line citations are realigned
  (`NavigationSession.kt:740`, `NavigationSession.kt:504`), the design's own call-site citations are corrected
  to the shortened file (`:224`, `:266`, `:307`, `:741`), the move's arithmetic is quoted one way, the
  `tools/look.sh` quotes carry their scope, and every claim that was stated without a log carries one
  (`evidence/surface-round2.log`, `evidence/line-accounting.log`, `evidence/green-probe-round2.log`,
  `evidence/openspec-validate-round2.log`, `evidence/evidence-check-round2.log`). Parent:
  `documentation-ownership`.
- [x] 5.2 Re-gate the archived tree once, with the prose above final — the round-1 gate measured a tree that
  was archived and re-worded after it ran. The gate's line and its eight step tallies are in
  `evidence/evidence.md` and `evidence/loop-gate/`. Parent: `build-test-gate`.
