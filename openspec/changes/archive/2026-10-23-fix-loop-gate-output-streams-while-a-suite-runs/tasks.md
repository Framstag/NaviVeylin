# Tasks — fix-loop-gate-output-streams-while-a-suite-runs

## 1. The red case, before the fix

- [x] `tools/loop-cases.sh`: `gate#theSuitesOutputReachesTheConsoleWhileItRuns` — a suite that writes a line at
  once and works for three seconds; the gate is started in the background and its log read 1.5 s later.
  Verify: the case against the gate restored from HEAD (`git show HEAD:tools/loop/gate.sh`) — `FAIL`, and the
  log holds only the gate's own announcement (`evidence/red-HEAD.log`, `evidence/red-HEAD.xml`,
  `tests="1" failures="1"`, 2026-10-23T09:15Z).
- [x] `tools/loop-cases.sh`: `gate#aFailingSuiteIsReadFromTheSameStream` — a failing suite's failing line, its
  summary, the gate's exit status `1`, the `LOOP-GATE: tests=3 failures=2` line and the tally's
  `tests="3" failures="2"` with the failure text.
  Verify: `bash tools/loop-selftest.sh --case 'gate#aFailingSuiteIsReadFromTheSameStream'` — `PASS`.

## 2. The fix

- [x] `tools/loop/gate.sh`: the suite's output is `tee`d into one file, the console reads the stream, and `out`
  is that file; the status is `${PIPESTATUS[0]}`.
  Verify: the two cases above — the first goes green, the second stays green.

## 3. The invariant's own falsification

- [x] `tools/loop-mutations.sh`: record `46.1` restores the command substitution and names
  `gate#theSuitesOutputReachesTheConsoleWhileItRuns`.
  Verify: `bash tools/loop-mutations.sh --check --only 46.1` (the record applies) and
  `bash tools/loop-mutations.sh --only 46.1` — `RED` for the case, an exact restore, the case green again
  (`evidence/revert-check-46.1.log`).

## 4. The guideline

- [x] `guidelines/Backlog.md`, section "7. The loop": the bounds paragraph says the gate streams each suite's output as
  it is written, that the tally is parsed from that same stream, and the measurement behind it (#269).
  Verify: `tools/check-doc-routes.sh`, plus the structural read that the paragraph still sits under
  `## 7. The loop`.

## 5. The gate, once

- [x] `./tools/loop/gate.sh` from the change root — no compiled file changed, so the profile owes the shell
  suites (`tools/loop-selftest.sh`, `bash tools/loop-mutations.sh --check`) and the two documentation checks
  (`bash tools/gen-feature-list.sh --check-classification`, `tools/check-doc-routes.sh`).
  Verify: the run's own `LOOP-GATE` line with the tallies copied into `evidence/` (`evidence/gate.log`,
  `evidence/gate-tallies/`): 309 tests and 0 failures over the six checks, and the log itself is the
  demonstration — 177 `PASS` lines written as the suite ran, not one block after it.

## 6. Evidence and archive

- [x] Copy every cited XML into `openspec/changes/fix-loop-gate-output-streams-while-a-suite-runs/evidence/`:
  `red-HEAD.xml`, `gate-tallies/` (the five tallies the gate wrote) and `failing-suite-tally.xml` — the tally a
  real failing suite wrote through the new stream (`tests="3" failures="2"` with the failing line in its
  `<system-out>`), which is the pair quoted above and in the spec.
- [x] `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-gate-output-streams-while-a-suite-runs --root .`
- [x] `openspec validate fix-loop-gate-output-streams-while-a-suite-runs --strict`, then archive the change and
  confirm the delta merged into `openspec/specs/agent-issue-loop/spec.md`.
