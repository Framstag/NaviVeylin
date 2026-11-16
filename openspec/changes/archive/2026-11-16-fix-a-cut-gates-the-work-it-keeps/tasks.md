# Tasks

Reference: `proposal.md` for the root cause and the red run, `design.md` for why this is the one fix and which
guards it cannot do without, `specs/agent-issue-loop/spec.md` for the contract.

## 1. The red cases

Requirement: "A cut gates the work it keeps, before the item ends", scenarios "The gate runs on the tree the cut
left, and its result is recorded beside the cut" and "A cut that kept no file records no gate result".

- [x] 1.1 Add `run#aCutRunsItsGateOnTheKeptWork`: the profile's context bound is recorded at 200 000 tokens, both the
      worker and the one continuation the #851 ceiling allows cross it and are ended there, and the run's own gate is
      held as a stub that records the tree it was started in and the work that tree holds. The case asserts the gate ran
      in the change root with `demo-fix.txt` and `demo-fix-2.txt` present, `cutGate.result: passed` with a `cutGate.log`
      inside the item's own directory, the note's sentence, `result: cut` with `stopReason: bound`, exactly one
      continuation and no third child, and no `issue close`. Declared `# files:` against the fixture the run stubs
      reach. Verify: on the base `2807ba61` with this change's `tools/loop/run.sh` and `tools/loop/state.sh` hunks taken
      out, the case fails at its first assertion — `FAIL run#aCutRunsItsGateOnTheKeptWork`, `1 of 1 cases failed`,
      the run's own header stamp 2026-11-16T02:38:18Z (`evidence/red-case-on-HEAD.log`, `evidence/red-on-HEAD.xml`).
- [x] 1.2 One assertion in `run#aCutWhosePatchDoesNotApplyIsLeftCut`: a kept patch that carries no file records
      `cutGate: null`, so the arm that has no tree to measure is pinned as well as the arm that has one. It is green on
      both sides of the change by design — the field it reads is the one this change adds a null to. Verify:
      `PASS run#aCutWhosePatchDoesNotApplyIsLeftCut` on this tree (`evidence/cases-r2-green.log`).

## 2. The engine

- [x] 2.1 `tools/loop/run.sh`, `gate_the_kept_work` (`run.sh:763`): count the kept patch's files, resolve the gate the
      profile names from the change root, refuse past the run's own deadline (`run.sh:786`), take
      `LOOP_GATE_STEP_MAX_SECONDS` or the time left whichever is shorter (`run.sh:790`), run it in the change root with
      its output at `<item dir>/cut-gate.log`, and answer `passed`/`failed`/`bound` from the exit status.
- [x] 2.2 `tools/loop/run.sh`, the `cut)` arm: call it before the item is recorded (`run.sh:4144`), add the sentence to
      the note (`run.sh:4147`), and carry `--cut-gate`/`--cut-gate-result` into `loop_state done` (`run.sh:4151`).
- [x] 2.3 `tools/loop/state.sh`: `--cut-gate <log>` and `--cut-gate-result <result>` (`state.sh:719`, `:720`), recorded
      as `cutGate: {result, log}` on the item — `null` for every arm that ran no gate (`state.sh:996`, `:1009`).
- [x] 2.4 `tools/loop/tests/loop-mutations.sh`: `885.1`, the call itself — the arm the whole change is, mutated to
      gate nothing.

## 3. The prose

- [x] 3.1 `guidelines/Backlog.recipes.md`, the section headed "What an item costs, and the bounds on it": the paragraph
      that owns the rule, its four figures the run record's own, its flag spelling `state.sh`'s, and the three guards
      it states.
- [x] 3.2 The capability delta `specs/agent-issue-loop/spec.md`: the requirement and its two scenarios, each naming the
      case that fails once the arm is removed.

## 4. Verification

- [x] 4.1 The named case and the case the delta realigned pass on this tree, from the change root:
      `tools/loop/tests/loop-selftest.sh --case run#aCutRunsItsGateOnTheKeptWork` and `… --case
      run#aCutWhosePatchDoesNotApplyIsLeftCut` — `PASS` twice (`evidence/cases-r2-green.log`); the whole selection the
      delta is owed passed beside them, 243 cases, all passed, 1 128.79 s (`evidence/affected-cases-green.log`).
- [x] 4.2 The declaration the added case narrows is the cheapest check this delta owes, and it was run first —
      `tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach`, `PASS`
      (`evidence/declaration-case.log`).
- [x] 4.3 The mutation goes red and comes back green: `bash tools/loop/tests/loop-mutations.sh --only 885.1` —
      `RED 885.1 run#aCutRunsItsGateOnTheKeptWork`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`
      (`evidence/mutation-885.1.log`).
- [x] 4.4 The two shell files the diff touches parse: `bash -n tools/loop/run.sh` and `bash -n tools/loop/state.sh`, both
      silent. A change that compiles nothing replaces its build task with the structural check of the files it edits,
      and this is that check.
- [x] 4.5 The change's pointers resolve: `bash tools/loop/evidence-check.sh
      openspec/changes/fix-a-cut-gates-the-work-it-keeps` reports `ok`, its quoted `tests=1 failures=1` carried by
      `evidence/red-on-HEAD.xml`.
- [ ] 4.6 The gate: the loop's whole self-test, run once by the child that follows the diff review, over the change
      completed and archived before it starts. It is not run from here — a second gate is a change nobody has read yet.
