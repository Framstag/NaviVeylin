# Evidence — fix-navigation-session-file-size

Every number below is quoted beside the artifact that carries it: the 98 class XMLs `TEST-com.naviveylin.auto.*.xml`
(the focused run), the gate's eight step tallies under `loop-gate/`, the two sums `focused-tally.xml` / `gate-sum.xml`
that are the arithmetic over them, and this round's logs.

## The gate — run once, on this tree

The gate ran once, in the worker that produced this record, **after** the change was archived and after this
record's prose was final — so the tree it measured is the tree this directory sits in. The gate of round 1 did
not: it ran before the archive and before the prose was re-worded, which is what the round-1 review found
(blocking). Its tallies are superseded by the ones in `loop-gate/`.

The gate's line and its eight step tallies are copied to the run's evidence directory as they are produced
(`/home/tim/projects/NaviVeylin/.pi/issue-loop/items/835/`) and quoted here beside the XMLs that carry them:

```
LOOP-GATE: tests=41 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 312s, bash tools/gen-feature-list.sh --check-classification 3s, tools/check-doc-routes.sh 1s, tools/tests/check-doc-routes-selftest.sh 8s, tools/mutate.sh --mutations tools/tests/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/tests/check-openspec-hygiene-selftest.sh 0s)
LOOP-GATE-TREE: head=b8ec8009f1cbe230d659068a0c3e8263e22e281f diff=fa99c878b1278c1a
```

| step XML (`loop-gate/`) | tally |
|---|---|
| `gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml` | tests="0" failures="0" errors="0" time="312" |
| `bash_tools_gen-feature-list.sh_--check-classification.xml` | tests="0" failures="0" errors="0" time="3" |
| `tools_check-doc-routes.sh.xml` | tests="0" failures="0" errors="0" time="1" |
| `tools_tests_check-doc-routes-selftest.sh.xml` | tests="34" failures="0" errors="0" time="8" |
| `tools_mutate.sh_--mutations_tools_tests_check-doc-routes-mutations.json_--list.xml` | tests="0" failures="0" errors="0" time="0" |
| `tools_check-todo-references.sh.xml` | tests="0" failures="0" errors="0" time="1" |
| `tools_check-openspec-hygiene.sh.xml` | tests="0" failures="0" errors="0" time="0" |
| `tools_tests_check-openspec-hygiene-selftest.sh.xml` | tests="7" failures="0" errors="0" time="0" |
| **sum** (`gate-sum.xml`) | tests="41" failures="0" errors="0" time="325" |

The gradle step's own tally is `tests="0"` because `gate.sh` reads a suite's summary line from its console and
`./gradlew test` prints none of that shape — the step's own `system-out` carries `BUILD SUCCESSFUL` instead.
The 98 class XMLs in this directory are the **focused** run's (task 3.1, timestamp `2026-11-10T03:18:21.560Z`);
the gate's own per-class results are copied under `gate-auto/` from the build directory as they were produced
(98 classes, 958 tests, `failures=0 errors=0`, timestamp `2026-11-10T03:46:38.626Z`, the sum in
`gate-auto-tally.log`), so neither run is read from a build directory alone.

## The focused run the change cites

`./gradlew :auto:testDebugUnitTest -PjvmTestsOnly -PforceTests --offline --tests 'com.naviveylin.auto.*'`
(task 3.1) — 98 class XMLs, each in this directory, sum in `focused-tally.xml`:

- `focused-tally.xml` — tests="958" failures="0" errors="0"
- `focused-tally.log` — one line per class plus `TOTAL classes=98 tests=958 skipped=0 failures=0 errors=0`
- reproducible with `bash evidence/tally.sh` over `auto/build/test-results/testDebugUnitTest`

Same files copied to `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/835/TEST-*.xml` when they were
produced.

## The red case and the number the delta moved

- `red-probe-head.log` — the row's own probe on base `c0ddb97cadaefe10cbf126ba7d19cfb41fb02b97`, clean tree,
  taken 2026-11-10T03:17:10Z: `{"path":"auto/…/NavigationSession.kt", … "measurement":"1589",
  "verdict":"violation"}` under fingerprint `f0611efc…`. This is the red case of item #835: no JUnit case can
  redden a non-defect (XML tests=0 failures=0 for that reason), the probe is the case.
- `green-probe.log` — the same probe after the split: `"measurement":"1324","verdict":"clear"`, same
  fingerprint.
- `green-probe-round2.log` — the same probe re-taken on this round's tree (2026-11-10T03:43:34Z), with the
  changed paths of the round named: `"measurement":"1324","verdict":"clear"` under the same fingerprint.
- `seams-head.txt` + `line-accounting.log` — the base's `:1329`–`:1589` against the new file's body: empty
  diff, the move is a move. The log is timestamped and names both sides.
- `surface.log` — the surface counted before the plan was fixed (20 files name the class, 1 names the facade
  class, 0 specs / 0 guidelines / 0 tools name the path, 2 line comments below the cut).
- `surface-round2.log` — the same surface recounted on this round's tree, with every `tools/look.sh` quote's
  scope and output.
- `gate-auto/` + `gate-auto-tally.log` — the gate's own `:auto` class results (98 classes, 958 tests,
  `failures=0 errors=0`) copied out of the build directory as they were produced.
- `openspec-validate-round2.log` — what the validation says on the archived tree.
- `evidence-check-round2.log` — `tools/loop/evidence-check.sh` over this directory.

## Copies this run reads

- change's own (versioned) copy: this `evidence/` directory.
- the run's copy: `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/835/` — the 98 `TEST-*.xml`, the gate's
  8 step XMLs, the gate's own `:auto` results under `gate-auto/`, `focused-tally.log`, `red-probe-head.log`,
  `green-probe.log`, `green-probe-round2.log`, `line-accounting.log`, `surface.log`, `surface-round2.log`,
  `openspec-validate-round2.log`, `evidence-check-round2.log`, `seams-head.txt`, `gate-round2-console.log`,
  `gate-auto/` with the gate's own `:auto` results and `gate-auto-tally.log`.
