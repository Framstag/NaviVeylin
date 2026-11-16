# Gate attribution — item #916, phase F (tail worker)

Gate ran once, before this record, and was **red**:

```
LOOP-GATE: tests=1100 failures=3 errors=0 (loop suite 443 cases + mutation lists + doc/spec/portability/todo/issue-tooling checks; failing: loop-selftest --from-git 4c4e6819, three run# cases)
```

Not re-run. The three failures, from the gate's own log (`gate.log` in this directory, also in the item dir):

| # | case | message (gate.log) |
|---|------|--------------------|
| 1 | `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` | the run recorded the item its signal interrupted: expected 'blocked', got '' |
| 2 | `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced` | the reading names the shell that wrote among the processes standing in the project — the shape was found: ' 3169298 ' not found in: LOOP-OUTSIDE-WRITE-ALIVE: (none) · attributed=none |
| 3 | `run#childUsageIsRecordedPerItem` | every child of the item is counted, in one number: the gate child, the tail child and the reviewer: expected '6', got '8' |

## The change's own paths are green

The gate's own delta line (`gate.log:8`) names what this change touched:

```
loop-selftest: selected 443 of 443 cases (files: guidelines/Backlog.recipes.md openspec/specs/agent-issue-loop/spec.md tools/loop/gate.sh tools/loop/tests/loop-cases.sh tools/loop/tests/loop-mutations.sh openspec/changes/archive/2026-11-14-fix-a-failing-test-is-a-failure/)
```

- the change's red test passed in this gate: `gate.log:460` — `PASS gate#aTestFailureIsAFailureNotAnError`
- no `FAIL gate#` line exists in `gate.log`
- the failing step's own tally: `gate-selftest-from-git-4c4e6819.xml` — `tests="443" failures="3" errors="0"`

None of the three failing `run#` cases asserts anything in `tools/loop/gate.sh` or in the `gate#` hunks this change added to `tools/loop/tests/loop-cases.sh`; their bodies declare and exercise `tools/loop/run.sh`, `tools/loop/report.sh`, `tools/pi-child-events.sh`, `tools/loop/tests/fixtures/` and `tools/loop/prompts/` — no path in the delta above.

## Red before this work, red after it

Each failure is named with versioned proof that predates item #916's first process (its red test: 2026-11-14T19:12:18Z) and this gate (14 Nov, evening):

1. **`run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`** — identical assertion already red on 2026-11-05, before this work:
   - `prior-red-run-theWorkersOwnShellWrite-2026-11-05.log` (copy of `openspec/changes/archive/2026-11-05-fix-a-child-is-bounded-by-its-context/evidence/gate.log:499` — same message, `' <pid> ' not found in: LOOP-OUTSIDE-WRITE-ALIVE: (none)`; that gate: `tests=772 failures=2`, failing `tools/loop-selftest.sh --from-git 122cc2f9`)
2. **`run#childUsageIsRecordedPerItem`** — red on HEAD before this work:
   - `prior-red-run-childUsage-2026-11-02.log` (copy of `openspec/changes/archive/2026-11-02-fix-the-gate-ends-the-child-that-ran-it/evidence/red-on-HEAD.log` — `FAIL run#childUsageIsRecordedPerItem`, child-count assertion, same family as the gate's `expected '6', got '8'`)
3. **`run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock`** — a signal case the tree itself documents as staged red on purpose (`tools/loop/tests/loop-cases.sh`, comment above the case: "The measured red of issue #860, staged on purpose: the signal the case sends reaches no process"):
   - `prior-red-run-aHup-2026-11-13.log` / `.xml` (copies of that change's red-on-HEAD — `FAIL`, timestamp `2026-11-13T22:52:39Z`, before this work)
   - `prior-gate-aHup-change-from-git-2026-11-14.xml` — the archived gate of `2026-11-14-fix-a-hup-case-ends-on-its-own-clock`, run **before** this item's gate, was itself red on the same selection: `tests="428" failures="2"` on `--from-git fcbe328d`, so this step has been red in this environment independent of item #916

Stays red after the work: the table above is this gate, measured on the archived change.

## Copies

Every result cited here is copied into this `evidence/` directory and into the run's evidence directory `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/916`:

- `gate.log` (gate's own full log; item dir carried it first)
- `gate-selftest-from-git-4c4e6819.xml` (failing step tally, from `app/build/test-results/loop-gate/` of the change root)
- `prior-red-run-theWorkersOwnShellWrite-2026-11-05.log`
- `prior-red-run-childUsage-2026-11-02.log`
- `prior-red-run-aHup-2026-11-13.log`, `prior-red-run-aHup-2026-11-13.xml`
- `prior-gate-aHup-change-from-git-2026-11-14.xml`

No path outside this change's `evidence/` directory was written after the gate; no tree edit, no commit, no second gate run, no `tools/loop/evidence-check.sh` run.
