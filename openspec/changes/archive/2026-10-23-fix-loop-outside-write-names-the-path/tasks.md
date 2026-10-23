# Tasks — the run names what changed when a child writes in the project's own tree

## 1. The red case

- [x] `tools/loop-cases.sh:3353`: `run#childWriteOutsideTheWorktreeIsBlocked` rewritten — its stub child writes
  `demo-owned.txt` into the project's own tree and leaves a process of its own working there, and the case asserts
  the status line in the transcript, the path in the item's note, the pid among the processes named, and the path
  in the tracker's own comment; the case then ends the process it started.
  Verify: `bash tools/loop-selftest.sh --case 'run#childWriteOutsideTheWorktreeIsBlocked'` — red on HEAD, for the
  reason the case names; the transcript and the tally are `evidence/red-HEAD.log` and `evidence/red-HEAD.xml`
  (`tests="1" failures="1"`, 2026-10-23T16:49Z, HEAD `f2d2773`).

## 2. The fix

- [x] `tools/loop/run.sh:577`–`618`: `project_state_diff`, `outside_write_clause`, `outside_write_pids` and
  `outside_write_line`; `run_child` appends the line (`tools/loop/run.sh:791`) instead of the constant sentence.
  Verify: the case from task 1 goes green (`bash tools/loop-selftest.sh --case …`, `PASS`).

- [x] `tools/loop/run.sh:1014` and `tools/loop/run.sh:1161`: both block reasons carry the recorded line, so the
  block's escalation names the paths and the processes on the tracker.
  Verify: the same case's assertions on the item's note and on the `gh` stub's record.

## 3. The invariant's own falsification

- [x] `tools/loop-mutations.sh:116`: record `12.3` re-anchored on `outside_write_line "$before" "$after" >> "$2"`.
  Verify: `bash tools/loop-mutations.sh --only 12.3` — `RED` for `run#childWriteOutsideTheWorktreeIsBlocked`, an
  exact restore, and the case green again (`evidence/revert-check-12.3.log`), plus
  `bash tools/loop-mutations.sh --check --files tools/loop/run.sh` — every record that quotes `run.sh` still
  applies (49 of 111).

## 4. The neighbouring cases

- [x] `run#reviewerWriteInTheProjectIsDiscarded` and `run#reviewerWriteInvalidatesTheRound` read the same two
  readings and must be unchanged; the `report#*` group reads the block reasons.
  Verify: `bash tools/loop-selftest.sh --case 'run#*Write*'` and `bash tools/loop-selftest.sh --case 'report#*'` —
  green.

## 5. The guideline

- [x] `guidelines/Backlog.md`, section "7. The loop": the paragraph that says what the run does with a write in the
  project's own tree, with this measurement (#285, and #274's own event).
  Verify: `tools/check-doc-routes.sh` (the gate's own check for a `*.md` path), plus the structural read that the
  paragraph sits under `## 7. The loop`.

## 6. The gate, once

- [x] `./tools/loop/gate.sh` from the change root — no compiled file changed, so the profile owed the three shell
  suites the `tools/loop/**` paths pull in and the documentation check a `*.md` path pulls in (the open change's
  own `openspec/` paths are gitignored — `openspec/changes/*/` — so nothing is owed for them).
  Verify: `evidence/gate.log`'s closing `LOOP-GATE` line (2026-10-23T16:53Z, exit 0) — 336 cases across the four
  checks, zero failures and zero errors, the suites' own seconds in the parentheses — with the four tallies under
  `evidence/gate-tallies/`.

## 7. Evidence and archive

- [x] Copy every cited XML into `openspec/changes/fix-loop-outside-write-names-the-path/evidence/`.
- [x] `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-outside-write-names-the-path --root .`
- [x] `openspec validate fix-loop-outside-write-names-the-path --strict`, then archive the change and confirm the
  delta merged into `openspec/specs/agent-issue-loop/spec.md`.
