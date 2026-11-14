# Gate red attribution — item #880, phase F (tail worker, 2026-11-14)

Gate line handed to the tail, never re-run:
`tests=1033 failures=1 errors=0 … — failing: tools/loop/tests/loop-selftest.sh --from-git fcbe328dd1ed75c0863bc43ad7de0f8a734f638e`

## The failing step, named

- **Suite:** `tools/loop/tests/loop-selftest.sh --from-git fcbe328dd1ed75c0863bc43ad7de0f8a734f638e`
- **Tally (this gate, run=1690303):** `tests="427" failures="1" errors="0" time="760"` — system-out
  `loop-selftest: 1 of 427 cases failed — 758.44 s`.
  Copied here as `gate-step-loop-selftest-from-git.xml`.
- **Case name:** not carried in the tail's gate output (the step stream was gate.sh's stdout; its per-case
  lines were not preserved — the gate child's report says so). At the **same base**, the identical suite leg
  printed the failing case: item #862's gate log, `gate-red-before-item-862-gate-run.log:1272`
  `FAIL phase#aResultOverTheBoundIsCutAndNamed`, `:1328` `loop-selftest: 1 of 427 cases failed`,
  `:1329` `gate: tools/loop/tests/loop-selftest.sh --from-git fcbe328dd1ed75c0863bc43ad7de0f8a734f638e — 869s`.
  A `phase#…` case lives in `tools/loop/phase.sh`'s self-test — loop tooling, not this change's files.

## Red before this work

`gate-red-before-item-862-gate-run.log` — item #862's gate, **mtime 2026-11-13T23:53:00Z**, i.e. before this
change existed (first green test of the change 2026-11-14T01:37:27Z, change commit
`a2348691ad9cd340b03ce435a5c9a095731bad72` at 2026-11-14T01:37:45Z): same suite, same base
`fcbe328dd1ed75c0863bc43ad7de0f8a734f638e`, `1 of 427 cases failed`. Also red in item #875's gate at the same
base (`2 of 427`, `items/875/gate-step-loop-selftest-from-git.xml`).

## Red after this work / outside this change's paths

- Stays red at **this** item's gate: `gate-step-loop-selftest-from-git.xml`, `failures="1"`, run=1690303.
- The change's commit `a2348691` touches **no `tools/loop` path** (`git show --stat`: `AGENTS.md`,
  `app/src/main/java/…`, `app/src/main/res/…`, `app/src/test/…`, `core/src/main|test/…`,
  `guidelines/*.md` — 19 files, no `tools/`).
- The suite owes paths `tools/loop/*` only (`loop.conf:798 LOOP_GATE_SUITES`).
- The change's own compiled suites are green in the same gate: `gate-step-gradlew-jvm-tests.xml`
  (`BUILD SUCCESSFUL in 7m 7s`, failures=0).

Waived clause: `LOOP-GATE-WAIVED`, suite: `LOOP-GATE-WAIVED-SUITE` — both in
`.pi/issue-loop/items/880/worker-tail-report.md`.
