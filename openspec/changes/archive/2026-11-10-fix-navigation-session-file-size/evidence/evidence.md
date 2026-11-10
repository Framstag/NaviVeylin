# Evidence — fix-navigation-session-file-size

Every number below is quoted beside the XML that carries it, and every XML it cites is in this directory:
98 class XMLs `TEST-com.naviveylin.auto.*.xml` (the focused run), the gate's eight step tallies under
`loop-gate/`, and the two sums `focused-tally.xml` / `gate-sum.xml` that are the arithmetic over them.

## The gate — run once by the first process's gate child, quoted here, never re-run

Gate line, verbatim from the run:

```
LOOP-GATE: tests=41 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 335s, bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 2s, tools/tests/check-doc-routes-selftest.sh 9s, tools/mutate.sh --mutations tools/tests/check-doc-routes-mutations.json --list 1s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/tests/check-openspec-hygiene-selftest.sh 0s)
```

`tests=41 failures=0 errors=0` is the sum of the eight step tallies `tools/loop/gate.sh` wrote, each copied
into `loop-gate/` and quoted here:

| step XML (`loop-gate/`) | tally |
|---|---|
| `gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml` | tests="0" failures="0" errors="0" time="335" |
| `bash_tools_gen-feature-list.sh_--check-classification.xml` | tests="0" failures="0" errors="0" time="4" |
| `tools_check-doc-routes.sh.xml` | tests="0" failures="0" errors="0" time="2" |
| `tools_tests_check-doc-routes-selftest.sh.xml` | tests="34" failures="0" errors="0" time="9" |
| `tools_mutate.sh_--mutations_tools_tests_check-doc-routes-mutations.json_--list.xml` | tests="0" failures="0" errors="0" time="1" |
| `tools_check-todo-references.sh.xml` | tests="0" failures="0" errors="0" time="1" |
| `tools_check-openspec-hygiene.sh.xml` | tests="0" failures="0" errors="0" time="0" |
| `tools/tests/check-openspec-hygiene-selftest.sh.xml` | tests="7" failures="0" errors="0" time="0" |
| **sum** (`gate-sum.xml`) | tests="41" failures="0" errors="0" |

The gate's own console (last 200 lines) is the run's copy at
`/home/tim/projects/NaviVeylin/.pi/issue-loop/items/835/worker-tail-report-gate-output.txt`; the same eight
step XMLs are copied to `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/835/` (flat, beside the 98 class
XMLs that are already there).

The gradle step's own tally is `tests="0"` because gate.sh reads a suite's summary line from its console and
`./gradlew test` prints none of that shape — the step exited 0 (`BUILD SUCCESSFUL in 5m 35s 167 actionable
tasks`, in the step XML's `system-out`). What the gradle run executed is counted by the class XMLs below.

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
- `seams-head.txt` + `line-accounting.log` — HEAD's `:1329`–`:1589` against the new file's body: empty diff,
  the move is a move.
- `surface.log` — the surface counted before the plan was fixed (20 files name the class, 1 names the facade
  class, 0 specs / 0 guidelines / 0 tools name the path, 2 line comments below the cut).

## Copies this run reads

- change's own (versioned) copy: this `evidence/` directory.
- the run's copy: `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/835/` — 98 `TEST-*.xml`,
  `loop-gate/`'s 8 step XMLs, `focused-tally.log`, `red-probe-head.log`, `green-probe.log`,
  `line-accounting.log`, `surface.log`, `seams-head.txt`.
