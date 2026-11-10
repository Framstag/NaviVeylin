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
  name the path, and the two comments that cite a line of the file (`NavigationSessionTripSendTest.kt:29`,
  `MapScreenTest.kt:280`) cite lines below the cut (`evidence/surface.log`). Parent: `documentation-ownership`
  — a guideline's or a test's `file:line` claim is what a split can dangle.

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
- [x] 2.5 Compile the source sets: `./gradlew :auto:compileDebugUnitTestKotlin -PjvmTestsOnly --offline` → no
  output and no `e:` line, so a seam is resolvable from the class and the class's call sites from the seams.

## 3. Verification — scoped by what changed (production sources and one test source of `:auto`)

- [x] 3.1 Run the affected cases: `./gradlew :auto:testDebugUnitTest -PjvmTestsOnly -PforceTests --offline
  --tests 'com.naviveylin.auto.*'` → 98 XMLs, `tests=958 failures=0 errors=0`
  (`evidence/focused-tally.log`, reproducible with `bash evidence/tally.sh`); each XML copied to the run's
  evidence directory at the moment it was produced. Parent: `unit-test-suite-runtime`.
- [x] 3.2 Take the green number with the same probe as 1.1 → `"measurement":"1324","verdict":"clear"` under the
  same fingerprint (`evidence/green-probe.log`): the row's clearing pass is what closes the item. Parent:
  `backlog-sensors`.
- [x] 3.3 Run the project's gate once. **Ran in the child the run deferred it to**, once for this item:
  `LOOP-GATE: tests=41 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 335s,
  bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 2s,
  tools/tests/check-doc-routes-selftest.sh 9s, tools/mutate.sh --mutations
  tools/tests/check-doc-routes-mutations.json --list 1s, tools/check-todo-references.sh 1s,
  tools/check-openspec-hygiene.sh 0s, tools/tests/check-openspec-hygiene-selftest.sh 0s)` — the eight step
  tallies under `evidence/loop-gate/`, their sum in `evidence/gate-sum.xml`, both quoted beside them in
  `evidence/evidence.md`. Parent: `build-test-gate`.

## 4. What the tail owns

- [x] 4.1 Copy every cited XML into `openspec/changes/fix-navigation-session-file-size/evidence/` and quote the
  gate's tallies in `evidence/evidence.md` beside the XML that carries them: 98 `TEST-*.xml` from the focused
  run, the gate's 8 step tallies in `evidence/loop-gate/`, the sums `evidence/focused-tally.xml`
  (tests="958" failures="0" errors="0") and `evidence/gate-sum.xml` (tests="41" failures="0" errors="0"),
  every one of them also copied to the run's evidence directory. Parent: `build-test-gate`.
- [x] 4.2 `.openspec.yaml` declares `skip_specs: true` with the reason in `design.md` (no requirement changes, so
  no delta is invented); `openspec validate fix-navigation-session-file-size --strict` reports valid.
- [x] 4.3 Archive the change and confirm the directory exists under `openspec/changes/archive/`:
  `openspec/changes/archive/2026-11-10-fix-navigation-session-file-size/` (archived by this tail with
  `openspec archive fix-navigation-session-file-size --yes`, `skip_specs: true` so the archive merged no delta
  and `openspec/specs/backlog-sensors/spec.md:471` still carries the requirement; `tools/loop/evidence-check.sh`
  re-run over the archived directory: PASS).
- [x] 4.4 Do not commit, do not push, do not touch the tracker: the run's script owns git and the labels —
  no `git` command and no tracker call was made by this tail.
