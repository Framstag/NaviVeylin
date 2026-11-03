# fix-the-gate-ends-the-child-that-ran-it — tasks

Parent requirement: `agent-issue-loop` — one requirement **added**, *"The gate ends the child that ran it, and a fresh
child writes the archive"*, with five scenarios. Delta scenarios → cases in the table at the end.

## 1. The red case (before the fix)

- [x] 1.1 `run#childUsageIsRecordedPerItem` (`tools/loop-cases.sh`) extended: the fixture gives the gate child and the
      tail child a stream each, and the case asserts that the worker phase is **two** children in that order
      (`worker-prompt.md`, `worker-tail-prompt.md`), that the tail's prompt carries the gate line, the change, the
      first child's root cause and a file the run wrote from the first child's own transcript, that the item's tool
      counts, tokens and cost are the sum of the two streams (input `2000 + 17056`), and that the largest context
      recorded is still the gate child's compaction (`150000`) rather than the tail inheriting it.
- [x] 1.2 Red on the base: the case run against `git stash`ed engine (`tools/loop/run.sh`, `prompts/`, `phase.sh`,
      `fixtures/pi` at `b5d37f91`) with only this change's `tools/loop-cases.sh` in place — fails at *the worker phase
      is two children, not one: expected '2', got '1'* (`evidence/red-on-HEAD.log`).

## 2. The fix

- [x] 2.1 `tools/loop/run.sh`: `tail_after_gate` starts a second child when the first child's report carries a gate
      line and no verdict of its own; it hands that child the gate line, the change name, the change's `tasks.md`, the
      root cause, the red test and the last 200 lines of the first child's transcript.
- [x] 2.2 The run reads the item's fields from the tail's report when one ran (`final_report`), and its commit and its
      "exited N" path follow the **last** child (`last_exit`, `last_child`).
- [x] 2.3 `run_child`'s stale-test-result arm names `worker-tail*` first, so the tail does not delete the gate's
      results (issue #253's rule, cleared for the one child that must not run it).
- [x] 2.4 The gate child's `outside-write` reading is snapshotted before the tail overwrites it; the tail's own
      reading is recorded as `outside-write-tail`; a gate child whose write decides the item starts no tail.
- [x] 2.5 The rework worker of a refusing round is split the same way (`worker-tail-round2-prompt.md`), with the same
      verdict rule.
- [x] 2.6 `tools/loop/prompts/worker.md`: Phase D ends at the gate, Phase F and its report lines are removed, and the
      report contract says a verdict is the worker's only when it ran no gate.
- [x] 2.7 `tools/loop/prompts/worker-tail.md` (new): Phase F alone — read the handed gate result, never re-run the
      gate, archive nothing on a red gate, copy and check the evidence, archive, and print the final block carrying
      the four handed lines verbatim.
- [x] 2.8 `tools/loop/phase.sh`: phase F's checklist is read from the tail prompt, and
      `phase#oneCallPrintsThePhaseState` asserts the file the call names.

## 3. The fixtures the split needed

- [x] 3.1 `tools/loop/fixtures/pi`: `PI_STUB_EVENTS_TAIL` — a stream for the prompt whose name carries `-tail`.
- [x] 3.2 `stub_write`: the tree work belongs to the gate child; the tail leaves the tree alone (a tail that rewrote
      it is what `run#aCorrectiveRoundIsHandedTheCorrections` measured).
- [x] 3.3 `stub_per_item_worker`: the per-item counter increments for the gate child only, so item n's tail writes
      item n's tree and not item n+1's.
- [x] 3.4 `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`'s staged shell acts for the gate child only, because the
      pid it records is read against the gate child's reading.

## 4. The cases realigned (the count is the contract)

- [x] 4.1 `run#oneItemOrderIsFixed` — three processes, in order (`worker`, `worker-tail`, `reviewer`).
- [x] 4.2 `state#agentCommandIsTheOnlyIndirection` — 3; `run#hostAboveTheFloorRunsTheItem` — 2.
- [x] 4.3 `run#reviewBudgetIsARecordedValue` and `run#aBlockingRefusalStillTakesTheRoundTwoPath` — 6 (two workers,
      their two tails, two reviewers); `run#aZeroBlockingRefusalClosesWithItsNotes` — 3.
- [x] 4.4 `run#aCutReportIsRetriedInsteadOfRefused` and `run#aReportCutTwiceBlocksAsTheTransportsFault` — 4.
- [x] 4.5 `run#falsifiedSkipClosesWithItsMeasurement` — a report with no gate line of its own starts no tail (0).

## 5. The mutation (one per invariant)

- [x] 5.1 `76.1` added: the tail launch's condition made unreachable → `run#childUsageIsRecordedPerItem` red; the
      restore green; `--check` reports the record applying.

## 6. The gate

- [x] 6.1 Scoped runs while iterating: `bash tools/loop-selftest.sh --group run --jobs 4` (111 cases, green) after the
      fixtures and the eight process-count assertions were realigned.
- [x] 6.2 The full set the delta owes, once: `bash tools/loop/gate.sh` — run on the tree this change leaves.
- [x] 6.2a Its own case set: `tools/loop-selftest.sh --from-git b5d37f91` selects 308 of 308 cases and reports
      `tests=308 failures=0 errors=0` in 437 s, offline, no token, no sandbox — carried by
      `evidence/tools_loop-selftest.sh_--from-git_b5d37f91f9f4a726b5d86feb5a5cc628dff36c5b.xml`
      (`evidence/loop-gate.log`, `evidence/gate-summary.txt`). No compiled file is touched, so no Gradle task is owed;
      named exclusion: no build, no instrumentation test.
- [x] 6.2b The gate's own tally is `tests=793 failures=0 errors=1` — not retained as one XML, because it is the sum the
      gate computes over its 23 step tallies, each under `app/build/test-results/loop-gate/`; the step's own pair is in
      `evidence/bash_tools_gen-feature-list.sh_--check-classification.xml`, which is the red below.
- [ ] 6.2c **A gate with zero failing steps** — not claimed. The gate's tally (6.2b) counts no failure and one error,
      and that error is the step 6.2d attributes to two other open changes' unclassified delta specs. Left unchecked
      rather than talked green (`BuildEvidence.md` §4d): it goes green when those changes classify their own ids.
- [x] 6.2d **The one red step, named and not this change's**: after the archive moved the delta into
      `openspec/specs/agent-issue-loop/spec.md`, the plan owes `bash tools/gen-feature-list.sh
      --check-classification`, and it fails on the **two open changes this worktree carries** —
      `auto/off-route-indicator` (`car-reroute-without-losing-the-map`) and `tooling-test-layout`
      (`separate-tooling-test-code`) — which its own rule says are classified in their own change before it
      archives. Measured in `evidence/feature-list-classification.txt`: this worktree fails with `unclassified: 2`;
      `git archive b5d37f91` into a scratch tree (no open change) exits 0 with the same 3 stale warnings. This delta
      introduces no capability id, so nothing in the count is its own.
- [x] 6.3 `bash tools/loop-mutations.sh --only '76.1 76.2'` — both red when mutated, both green after the restore, and
      `--check` reports all 226 records applying (`evidence/loop-mutations.log`, `evidence/mutations-check.log`).
- [x] 6.4 `openspec validate fix-the-gate-ends-the-child-that-ran-it --strict` reports the change valid
      (`evidence/openspec-validate.txt`).
- [x] 6.5 `bash tools/check-portability.sh` — OK, 54 engines and 3 templates (`evidence/evidence-check.txt`).
- [x] 6.6 `bash tools/loop/evidence-check.sh openspec/changes/fix-the-gate-ends-the-child-that-ran-it --root .` —
      PASS (`evidence/evidence-check.txt`).

## 7. The records

- [x] 7.1 `guidelines/Backlog.recipes.md`, its section "Running one item: the script, the child and the package" —
      the phase diagram and the paragraph that states the split.
- [x] 7.2 This change: proposal, design, the delta on `agent-issue-loop`, these tasks, and `evidence/`.
- [x] 7.3 `evidence/` carries the red-on-HEAD log, the gate's own output and summary, the mutation log and the
      evidence check.

## 8. Delta scenarios → the case that exercises each

| delta scenario | case |
|---|---|
| The gate line ends the child that ran the gate | `run#childUsageIsRecordedPerItem`, `run#falsifiedSkipClosesWithItsMeasurement` |
| The tail starts at the baseline, and the item pays for both children | `run#childUsageIsRecordedPerItem` |
| The tail is handed the gate, the tasks and the gate's own output | `run#childUsageIsRecordedPerItem` |
| A gate that did not pass is the tail's verdict, and the item is blocked with it | `run#aRedGateIsBlockedByTheTail` |
| The rework of a refusing round is split the same way | `run#aBlockingRefusalStillTakesTheRoundTwoPath` |
| A phase's checklist is the prompt of the child that is handed the phase | `phase#oneCallPrintsThePhaseState` |

## 9. Review round 1 (requested 2026-11-02, `LOOP-BLOCKING: 5`)

- [x] 9.1 **The round-2 tail had no outside-write snapshot.** `round2_write_note`/`round2_write_decides` are taken
      before the round-2 tail starts, the tail is not started when that reading decides, and the two readings are
      recorded under `outside-write-round-2` and `outside-write-round-2-tail` — the same rule the item's worker
      already followed, at the second of its two call sites.
- [x] 9.2 **The extract was the child's closing answer, not the gate's output.** `tail_after_gate` now cuts the tail's
      `{{GATE_OUTPUT}}` from the item's event file — the gate child's `tool_execution_end` text, which is what the gate
      printed — and then from its closing answer, both bounded. The case stages a gate console line in the stream and
      asserts the extract carries it, so the two sources can no longer be confused.
- [x] 9.3 **The red-gate verdict had no outcome arm.** `LOOP-VERDICT: blocked` from the tail is recorded as `blocked`
      with the tail's own gate line in the reason, and the new case `run#aRedGateIsBlockedByTheTail` stages it; the arm
      is mutated by `76.2`.
- [x] 9.4 **The tail's closure rule blocked an attributed, pre-existing red.** `worker-tail.md` now attributes before
      it blocks: a failing step the change does not touch, named and proven in the change's evidence, does not block;
      a red on the change's own paths does. `worker.md`'s removed sentence ("the affected cases pass") is its
      counterpart, and this change's own `gen-feature-list` red is attributed that way (task 6.2d).
- [x] 9.5 **The case never exercised the shape the change defines.** The gate child in
      `run#childUsageIsRecordedPerItem` now prints `STUB_GATE_REPORT` — a gate line and no verdict — so the arm that
      starts the tail for a real item is the arm under test; the item's recorded gate is asserted to be the tail's
      line, so a run that kept reading the gate child's report cannot pass.
- [x] 9.6 The notes taken: the phase diagram keeps `(E)` beside the new phase-F line; `outside-write-tail` has a case
      (`run#childWriteOutsideTheWorktreeIsNoted`, whose tail now writes a path of its own); the vacuous
      "no `## Phase D`" assertion is replaced by the archive step it was meant to pin; the "child could not start"
      reason names `$last_child`/`$last_exit`; the item's turn bound and the two-child wall clock are stated in the
      guideline paragraph; and the item's `context` assertion says what it measures (a max over both children).
