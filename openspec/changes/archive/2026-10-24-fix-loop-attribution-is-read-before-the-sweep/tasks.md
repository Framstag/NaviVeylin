# Tasks — the attribution is read while the writer is alive, and only a writer that left the worktree decides

## 1. The reading

- [x] 1.1 `tools/loop/run.sh`: `loop_tree_pids` (the child's group and its descendants, one kernel call per
      level), `pid_stands_outside_worktree` (the working-directory test the note and the attribution share),
      `pid_ppid`, and `pid_in_child_tree` removed.
      Verify: `bash -n tools/loop/run.sh` parses; the walk is exercised through the named cases.
- [x] 1.2 The mark: a process counts only when it left the worktree — `pid_left_the_worktree`, an ancestor of the
      candidate working in the worktree.
      Verify: `run#aWriterTheRunCanPlaceBlocksTheItem` green *and*
      `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem` green — the two halves of the rule.
- [x] 1.3 `outside_write_seen` reads the child's own tree first and the host-wide sweep after it.
      Verify: task 3.1's red→green and the neighbours in 3.2.

## 2. The suite and the harness

- [x] 2.1 `tools/loop-cases.sh`: the peer case, registered in the case list and the dispatcher, declared for
      `tools/loop/run.sh` so a delta that touches it owes the case.
      Verify: `bash tools/loop-selftest.sh --case run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem`.
- [x] 2.2 `tools/loop-mutations.sh`: `12.11` (drop the "left the worktree" test) and `12.12` (read the candidates
      from the sweep again), each naming the case that must notice.
      Verify: `bash tools/loop-mutations.sh --check` — every record applies to the file it names.
- [x] 2.3 `tools/loop-selftest.sh`: `pgrep` joins the required tools.
      Verify: the suite refuses a host without it instead of attributing nothing.

## 3. The suites

- [x] 3.1 `run#aWriterTheRunCanPlaceBlocksTheItem` — the case the item was filed on.
      Verify: red on HEAD 3 of 3 (`evidence/red-HEAD.log`), green after the fix 5 of 5
      (`evidence/green-case.log`); the peer case, 5 of 5, is `evidence/green-peer-case.log`.
- [x] 3.2 The neighbours: `run#childWriteOutsideTheWorktreeIsNoted`,
      `run#strictOutsideWriteBlocksTheItem`, `run#strictOutsideWriteDiscardsTheReviewRound`,
      `run#dirtyBaseRefusesTheMergeAndNamesThePath`.
      Verify: `evidence/green-neighbours.log`, all four green.
- [x] 3.3 Revert-check: `bash tools/loop-mutations.sh --only "12.3 12.4 12.9 12.10 12.11 12.12"` — each named
      case red, restored green, byte-identical restore.
      Verify: `evidence/revert-check.md`, `evidence/revert-check.log`.

## 4. The documents

- [x] 4.1 `guidelines/Backlog.md`, section 7 "The loop": the paragraph states the writer rule and records both
      measurements (the sweep's second and the writer's 2 s), and the peer probe that forced the narrow rule.
      Verify: `tools/check-doc-routes.sh`.
- [x] 4.2 The `agent-issue-loop` delta: one `MODIFIED` requirement, one scenario extended, one added, every other
      scenario verbatim.
      Verify: `openspec validate fix-loop-attribution-is-read-before-the-sweep --strict`.

## 5. The gate and the record

- [x] 5.1 The delta's own gate, from the change root: `bash tools/loop/gate.sh --base e12cf971…`, the tip #270
      landed on.
      Verify: `evidence/gate.log`, its `LOOP-GATE:` line quoted in `evidence/evidence.md`; the per-suite tallies
      it sums are copied beside it (`evidence/loop-gate-*.xml`).
- [x] 5.2 The whole engine suite, owed for a change that owns `tools/loop/**`: the gate's own selection line is
      `selected 208 of 208 cases`, so one run is both the delta's suite and the whole set; no Gradle suite is
      owed (no compiled path changed).
      Verify: `evidence/gate.log`, and `evidence/evidence.md`'s "What is not asserted" for the one Gradle test
      that failed in a wider run of the same gate and passes alone.
- [x] 5.3 `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-attribution-is-read-before-the-sweep --root .` — every pointer resolves.
- [x] 5.4 Archive the change and confirm the delta merged into `openspec/specs/agent-issue-loop/spec.md`.
