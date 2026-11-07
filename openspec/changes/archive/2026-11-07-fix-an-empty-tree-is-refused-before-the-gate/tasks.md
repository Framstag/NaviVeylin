# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why, `specs/agent-issue-loop/spec.md` for the
contract.

## 1. The red case

Requirement: "The run verifies rather than trusts", scenario "An empty tree is read at the diff, before the child that
runs the gate".

- [x] 1.1 `tools/loop-cases.sh`: `run#anEmptyTreeIsRefusedBeforeTheGateChild` — `new_project` plus `stub_write` with
      `STUB_CHANGE_REPORT` (the diff marker and no verdict), an action that leaves the tree exactly as it found it, then
      `run_loop --review on` asserted on the recorded item (`blocked`), the reason the outcome block writes, the change
      child being the only child started, the `empty-tree` step and its "before any gate child", no `pre-gate-review` /
      `worker-gate` / `worker-tail` step, no `worker-gate-report.md`, no `worker-tail-report.md`, and the output naming
      no "could not start" stop. Its list entry and its dispatch entry are added with it. Verify: on HEAD `ec6a84a` it
      fails (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`, 2026-11-06T23:48:16Z), and on the change it passes.
- [x] 1.2 The case's first line declares `tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh
      tools/tooling.json` — what `new_project`, `scenario` and `run_loop` reach. Verify:
      `bash tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`.

## 2. The engine

- [x] 2.1 `tools/loop/run.sh`: `empty_tree_reading=""` beside the deferred-gate block's declarations, and — after the
      commit and the HEAD it printed — the reading that sets it, records the item's `empty-tree` step and prints the
      note when HEAD is still the base. Verify: `bash -n tools/loop/run.sh`, then the red case green.
- [x] 2.2 The two guards the reading owns: the tail's (`[ -z "$empty_tree_reading" ]`) and the never-started arm's, so
      an answer that claims a diff is the item's block and not the run's stop. Verify: the new case, and
      `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault` for the answer that claims nothing.
- [x] 2.3 `tools/loop-mutations.sh`: record 91.1 — `tools/loop/run.sh`, case
      `run#anEmptyTreeIsRefusedBeforeTheGateChild`, the reading's `if` replaced with `if false; then`, with the comment
      that names the cost the missing reading left on the archive. Verify: `bash tools/loop-mutations.sh --check` green,
      and 91.1 red-when-mutated and green again on restore.

## 3. The prose the rule lives in

- [x] 3.1 The change's spec delta: "The run verifies rather than trusts" gains the scenario and the body clause that
      says when the tree's state is read, and carries the three scenarios the shipped requirement has, each with the
      case it already has. Verify: `openspec validate fix-an-empty-tree-is-refused-before-the-gate --strict` and
      `tools/check-openspec-hygiene.sh`.
- [x] 3.2 `guidelines/Backlog.recipes.md`, the section "Refusals, records and the landing": the paragraph on what the run does with a child that never started
      says where the empty-tree reading is taken and what it leaves, and why that is the item's block rather than the
      run's stop. Verify: `tools/check-doc-routes.sh` and `tools/check-todo-references.sh`.

## 4. Verification

- [x] 4.1 The declaration case first, then the red case, then the group the reading can move
      (`bash tools/loop-selftest.sh --group run`, 128 cases green). Verify: the tallies in the worker's report and the
      logs beside this change's evidence.
- [x] 4.2 The gate is **not** this worker's (the run deferred it): the diff is left for the reviewer and the gate
      child, and no `tools/loop/gate.sh` run is claimed here.
