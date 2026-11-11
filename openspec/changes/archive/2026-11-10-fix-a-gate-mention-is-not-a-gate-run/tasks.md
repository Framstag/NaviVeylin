# Tasks — a call that names the gate is not a call that ran it

## 1. The red case

- [x] 1.1 `tools/loop/tests/loop-cases.sh`: the case `report#aCallThatNamesTheGateIsNotAGateRun`, registered in
      `LOOP_SHELL_CASES` and in the dispatcher, declaring the files it reaches in its first line. Two states: a
      session whose only `gate.sh` words are a `sed` read and a `--plan` call (no gate, no gate time, the trend's
      dash), and a session that runs the gate behind `cd … &&` and `timeout 300` (5 000 ms of gate time, the split
      at that turn).
      **Verified** red on the base commit with `state.sh` restored from it: `evidence/red-on-HEAD.log` and its JUnit
      carrier `evidence/red-on-HEAD.xml` (`tests=1 failures=1`; the run's `date -u` was 2026-11-10T00:08:46Z, in the
      item's record), log ending `loop-selftest: 1 of 1 cases failed` — the first assertion answers
      `afterGate = {sessions: 1, tokens: 6 000 000, total: 6 000 000}` where the case requires `null`.
- [x] 1.2 The cheap declaration check the delta owes first — `loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach` — passes with the new case.
      **Verified** `PASS driver#everyCaseDeclaresTheFilesItCanReach`, printed by that command on the worker's tree. The
      check is driver-level, so the gate's own JUnit carrier for it records `tests="0"`; the PASS line is the check's,
      not a suite tally, and §6.5 repeats it for round 2.

## 2. The fix

- [x] 2.1 `tools/loop/state.sh`: `is_gate` reads the command's program — assignments, a `cd …` prefix and the
      `bash`/`sh`/`exec`/`command`/`timeout <n>`/`env`/`nohup` wrappers skipped — and excludes a `--plan` call.
      **Verified** by the case above passing on the changed tree, and by `report#theSpendIsSplitByPhaseRoleAndGate`
      — the case the figure already had — still passing unchanged.
- [x] 2.2 `report#` group: the 30 cases that read the state's spend, run together.
      **Verified** `loop-selftest: all 30 cases passed`, 2026-11-10.

## 3. The mutations

- [x] 3.1 `tools/loop/tests/loop-mutations.sh`: `272.1` (the plan exclusion), `272.2` (the `cd …` prefix) and `272.3`
      (the program word rather than the last word) — one per rule the case pins. Renumbered from `267.1`–`267.3`, which
      the tree had spent on the stream rotation before this landed.
      **Verified** `--only '272.1 272.2 272.3'` on the merged tree, 2026-11-10T20:41:32Z:
      `red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0`. Round 1's run of the same three records (then
      `267.x`) is `evidence/mutations.log`; the round-2 corrections changed no file they reach, and the round-2 gate
      covered their shape with `loop-mutations.sh --check` (315 records, `failures="0"`).

## 4. The prose that owns the rule

- [x] 4.1 `openspec/changes/fix-a-gate-mention-is-not-a-gate-run/specs/agent-issue-loop/spec.md`: the clause on what
      counts as the gate, and the scenario `A call that names the gate is not a call that ran it`, naming the case.
- [x] 4.2 `guidelines/Backlog.recipes.md` (section 7b): one clause in the paragraph that owns the three splits,
      naming the measurement, the issue and what the split was really taken at. The clause names the seven wrappers
      the predicate skips once — `bash`, `sh`, `exec`, `command`, `timeout <n>`, `env`, `nohup` — and it marks the
      `.pi/issue-loop/items/…` streams as the run's own record, because `.pi/*` is gitignored and a reader of the tree
      cannot follow that pointer; the archived probe and its log are what the clause points at instead.
      **Verified** `tools/check-doc-routes.sh` and `tools/check-openspec-hygiene.sh` — the structural checks these
      two files owe; the gate runs them again.
- [x] 4.3 `guidelines/Backlog.md` §1's table gains no row: it lists the changes that land, and §14 prices the table only
      for its own seven targets. This change edits one §7b paragraph of `Backlog.recipes.md` and ships no engine change
      that a reader would look for there — confirmed, not an omission.

## 5. The gate

- The gate is `./tools/loop/gate.sh`, and it is this change's own: the whole `loop-selftest.sh` set — which a delta to
  `tools/loop/tests/loop-cases.sh` is owed — `loop-mutations.sh --check`, and the structural checks of the files this
  change edits. Round 1's gate (`tests=978 failures=0 errors=0`) is in `evidence/gate-run-line-round1.md` and
  `evidence/gate-summary-round1.xml`; round 2 ran it again on the tree round 2's corrections produced and is
  `evidence/gate-run-line-round2.md` (`LOOP-GATE-TREE head=03439a42 diff=e88e3015`).
- Neither of those records attests the tree this change lands. After both gates the round wrote four paths outside
  `evidence/` — `guidelines/Backlog.recipes.md` and the archived change's `design.md`, `proposal.md`, `tasks.md` — so
  the digest a later reader would compute names a tree no gate measured. The merge onto `main` put the delta's clause
  into `openspec/specs/agent-issue-loop/spec.md` before the gate this change now ran, so the tail's Phase F had nothing
  left to move on the path set.
- The attesting run: `evidence/gate-run-line.md` (its step lines) and `evidence/gate.log` (its whole console, which the
  step lines and the aggregate are read from) — `LOOP-GATE-TREE head=6f67ca18 diff=7f2fdad8`,
  `tests=997 failures=0 errors=0`, longest steps `loop-selftest.sh --from-git` 597 s and `loop-mutations-selftest.sh`
  629 s. The first run on that tree was red for a collision, not for the change: `--from-git` found
  `.pi/tmp/.loop-selftest.lock` held by another suite (owner 2800790) and exited in 1 s (`tests=598 failures=0
  errors=1`); the re-run is the one above. Per-suite tallies are not copied this round: `app/build/test-results/loop-gate/`
  is shared with any other suite on the host and two files in it carried another run's 1800 s step, so the gate's own
  console is the record. If a later reader's digest of this tree still differs from the diff's, the difference is item
  #837 (the engine's post-gate move), and the two trees are named here rather than the record being called attested.

## 6. Round 2 — the corrections the review asked for

- [x] 6.1 The review's blocking finding — the gate's record did not attest the tree the round read. Round 2's gate ran
      on the tree round 2's corrections produced; it did not become the attesting record, because the round went on
      writing the four paths §5 names. Corrected here to state that, and the attesting run is the one on the merged
      tree (§5), not the two before it.

- [x] 6.2 The measurements the record did not carry: the five records' split points, their tokens and the tail's
      session are read by a probe this change versions (`evidence/split-point.jq`, its output
      `evidence/split-point.log`, both read from the run's own gitignored record — `.pi/issue-loop/items/` — so the log
      is the pointer a reader of the tree can follow). The surface count was re-taken on the tree that lands rather
      than inherited: `evidence/look-surface.log` now carries `git grep -c -E 'afterGate|gateMs'`, 54 matched lines in
      18 tracked files, 2026-11-10T20:41:49Z at `bf350dd0`. The earlier `LOOK: 47 matches in 13 files` and the
      `git diff --numstat` figures are dropped with it: neither could be re-derived from the tree.
- [x] 6.3 §5 above states the gate this change ran, not a gate deferred to a child that follows.
- [x] 6.4 The case re-run on this round's tree: `evidence/green-on-change-round2.log` — `PASS
      report#aCallThatNamesTheGateIsNotAGateRun`, run 2026-11-10T00:39:30Z.
- [x] 6.5 The declaration check the delta owes first, re-run on this round's tree: `PASS
      driver#everyCaseDeclaresTheFilesItCanReach` (`bash tools/loop/tests/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach`). It is driver-level, so the gate's carrier for it records
      `tests="0" failures="0" errors="0"`; the PASS is that command's own line.
