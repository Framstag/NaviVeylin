# Tasks — the engine's five scripts and the table that holds them

Spec: `agent-issue-loop` (delta `specs/agent-issue-loop/spec.md`). Gate for every task:
`bash tools/loop/tests/loop-selftest.sh --files tools/loop/…` while iterating, one full
`bash tools/loop/tests/loop-selftest.sh` before the change is complete. No Gradle build is owed — no compiled
file changes (`guidelines/Build.md` §2, the scope block). Depends on `run-event-stream-is-typed` (the kinds the
layout table names must exist).

## 1. The declaration layer, describing today

- [x] 1.1 `tools/loop/layout.tsv` already exists (#890); extend it: one row per engine file, and for the three new
  files the `file`, `bound`, `order`, `owner functions` and `owned kinds` columns. `bound` = the file's line count
  today + 25 %, rounded up. Verify: `wc -l` per file against the bound, printed in the change's evidence.
  Satisfies `...no engine file exceeds the line bound it declares`.
- [x] 1.2 Harvest the shared names into `tools/loop/shared.tsv` from a read of the function bodies: one row per
  name with the assigning file and the reading files. Nothing is moved in this task — the declaration describes
  the current single file, so its `assigned by` is `run.sh` for every row. Verify: the suite is green with
  nothing moved. Satisfies `Every name two files share is declared in one place`.
- [x] 1.3 Cases `driver#everyEngineFileHasOneOwner`, `driver#everyEngineScriptIsInTheTable`,
  `driver#aFileWithoutABoundIsRefused`: read the table, compare against the engine's actual files, refuse a row
  with no usable bound. Each declares `# files: tools/loop/ tools/loop/layout.tsv`.
- [x] 1.4 Cases `driver#aNameAssignedTwiceIsRefused`, `driver#anUndeclaredReadIsRefused`,
  `driver#theSharedNamesAreDeclared`: read `shared.tsv`, one fixture per refusal, and the set comparison.
- [x] 1.5 Case `driver#theSourceOrderIsDeclared`: the table's order rows equal the entry script's source lines.
  Case `driver#aCallToALaterFilesFunctionFails`: parse each file's defined and called names, compare against the
  order — a static check, so no case path has to be driven.

## 2. The moves, one owner per task

Each task moves one owner's functions verbatim, re-points the declarations that now read across the boundary, and
ends with the full suite green. A case changed for any reason other than its `# files:` declaration is a defect
of the task, not of the case.

- [x] 2.1 `leftovers.sh`: `keep_leftovers`, `adopt_finished_attempt`, `adopt_finished_exclusions`,
  `merge_committed_branch`, `discard_kept_worktree`, `submodule_only_dirt`, `place_submodule_pin`,
  `retry_start_item`, `worktree_is_dirty`, `owner_of_marker`, `own_leftovers_of`, `recorded_here`,
  `recorded_leftovers_of`. Verify: `bash tools/loop/tests/loop-selftest.sh --files tools/loop/leftovers.sh`
  plus a full run. Satisfies `The behaviour of a moved function is unchanged`.
- [x] 2.2 `evidence.sh`: `outside_write_*` (all six), `attribute_red_gate`, `review_correction_scope`,
  `write_review_diff`, `project_state`, `project_state_diff`, `loop_tree_pids`, `pid_*` (all five),
  `blocking_count`, `review_notes`, `clear_stale_test_results`.
- [x] 2.3 `child.sh`: `run_child`, `child_cap`, `child_turn_bound`, `child_context_bound`,
  `child_context_bound_key`, `child_ended_by_context_bound`, `child_agent_cmd_key`, `child_turns`,
  `child_context`, `child_refusal`, `child_role`, `tail_after_gate`, `record_cut_round`, `record_rework_cut`,
  `host_free_mib`, `retry_delay_at`.
- [x] 2.4 `run.sh` keeps the entry: flags, the header, `queue_passes`, the item loop, the stop arms,
  `on_exit`/`on_signal`/`stop_run`/`end_run`, and the four `source` lines in the declared order.
- [x] 2.5 Cases `driver#anOwnedFunctionIsInItsOwnersFile` and `driver#aKindIsEmittedOnlyByItsOwner`: read the
  table's owner columns against the definitions and the emit sites. Both green after 2.1–2.4.

## 3. The narrower selection

- [x] 3.1 Re-point the `# files:` declarations of the cases whose behaviour now lives in a named file: one
  declaration per case, naming the owner. Verify: `driver#aDeclarationNamingAnUnknownFileFails` (new case: a
  fixture declaration naming `tools/loop/nope.sh`) and `driver#everyCaseDeclaresTheFilesItCanReach` (shipped).
- [x] 3.2 Case `driver#theSelectionFollowsTheOwner`: assert the selection for a delta naming `evidence.sh` is a
  strict subset of the suite and contains that owner's cases. Record the selected count and the wall time in the
  change's evidence, beside the 502-case full run.
- [x] 3.3 Tighten each file's bound to what it now is plus 25 %, and record the before/after table
  (`wc -l` per file) in the change's evidence.

## 4. Documents the code now owns

- [x] 4.1 `guidelines/Backlog.md` section 7 names the engine's layout and where a person fixing the engine finds the
  file a behaviour lives in. Verify `bash tools/check-doc-routes.sh`.
- [x] 4.2 `guidelines/BuildGate.md` §2a states that a `tools/loop/**` delta narrows its selection through the
  case declarations, and names the table as their vocabulary.
- [x] 4.3 `PORTING.md` "The three files to edit" says `layout.tsv` is engine and not profile, so a copy carries it
  unchanged. Verify `bash tools/check-portability.sh --quiet` and the portability self-test.

## 5. Verification

- [x] 5.1 Full suite green, with its case count and wall time recorded.
- [x] 5.2 Revert-check for the file bound: raise a file's declared bound by one line below its actual length and
  `driver#noEngineFileExceedsItsBound` must fail; restore, forced green.
- [x] 5.3 Revert-check for the ownership rule: define one function in `run.sh` that the table assigns to
  `child.sh` and `driver#anOwnedFunctionIsInItsOwnersFile` must fail; restore, forced green.
- [x] 5.4 Revert-check for the shared-name rule: define one name `lib.sh` assigns a second time in `child.sh`
  and `driver#theSharedNamesAreDeclared` must fail; restore, forced green. The derivation's own fixture
  refusal (`driver#aNameAssignedTwiceIsRefused`) is not the arm a tree mutation can reach.
- [x] 5.5 Revert-check for the source order: replace the entry's `child.sh` source line with a second
  `evidence.sh` and `driver#theSourceOrderIsDeclared` must fail; restore, forced green.
- [x] 5.6 Revert-check for the call order: call a `child.sh` function from `evidence.sh`, which the entry
  sources before it, and `driver#aCallToALaterFilesFunctionFails` must fail; restore, forced green.
- [x] 5.7 Revert-check for the selection: answer a file set with every case, as the widened selection did, and
  `driver#theSelectionFollowsTheOwner` must fail; restore, forced green. One case's re-pointed `tools/loop/**`
  declaration stays consistent with the selection it produces, so the widening is the mutation, not one
  declaration.
- [x] 5.8 File every unrelated defect this work finds with `.pi/skills/create-issue`.
