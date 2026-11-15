# Tasks — a step red on the run's base is subtracted from the item's tally and named

The behaviour shipped before this change directory was written (commit `8cd97718`, merged by `3eb63c95`); the tasks
below are the steps that landed, each with the evidence that reads them back from the tree, and the artifact tasks
that record the change. No task in this round was executed: nothing was run, built or mutated here, and task 5.1
names what that leaves unestablished.

- [x] 1.1 `tools/loop/tests/loop-cases.sh`: `gate#aBaseRedStepIsSubtractedAndNamed` written first, registered in the
      case list (`:187`) and the dispatcher (`:932`), declaring its files (`tools/loop/gate.sh`, `state.sh`, `run.sh`,
      `loop.conf`, `tools/pi-child-events.sh`, `tools/tooling.json`). Verified: the case is the one every scenario of
      this delta names, and its body asserts the pre-flight's own lines, the two halves of the subtraction and the
      record the gate writes (`tools/loop/tests/loop-cases.sh:6237`–`6323`).
- [x] 1.2 `tools/loop/gate.sh`: `--base-red` as a mode of the same gate (`:64`), the mode switch that turns the
      pre-flight off inside a pre-flight (`:82`), the tally directory left unclaimed in that mode (`:106`), the whole
      step set owed rather than a delta's (`:340`), the run's record read as a step-keyed map (`:528`–`547`), the
      subtraction by pair (`:712`–`746`), the `LOOP-GATE-BASERED:<step>:<n>` line on the console and in the gate log
      (`:742`), the `— base-red: …` note beside the verdict (`:790`) and the pairs written to the run's record
      (`:794`). Verified: `gate#aBaseRedStepIsSubtractedAndNamed` asserts each of those lines and the record.
- [x] 1.3 `tools/loop/run.sh`: block 4b, the pre-flight before the run works anything (`:833`), the switch read from
      the profile (`:843`, `LOOP_BASE_RED` at `tools/loop/loop.conf:403`), `take_base_red` (`:845`) with its exit
      status split into *measured* (`0|1`) and *not measured* (anything else), and the four reasons a pre-flight is
      not taken (`:868`–`877`). Verified: the case's pre-flight half; the reasons themselves are printed lines and
      carry no case of their own (task 5.1).
- [x] 1.4 `tools/loop/state.sh`: `base-red --from <file> [--base <sha>]` (`:579`) reading the pre-flight's own
      `LOOP-BASERED-STEP:`/`LOOP-BASERED-CASE:` lines into `run.baseRed` and `run.baseRedBase` (`:605`), and
      `base-red-used --step … --count …` (`:610`) writing `run.baseRedUsed` (`:626`), both dispatched at `:1633`.
      Verified: the case asserts `run.baseRed` and `run.baseRedUsed.steps` in the throwaway project's state.
- [x] 2.1 `guidelines/BuildEvidence.md` §4d gains the rule the engine implements (`:137`–`147`): the pre-flight, the
      pairs it records, the pair-wise subtraction, the two lines it prints, and the two cases that stay red — a step
      red for a case the base never had, and a step that fails without naming a case where the base named none.
      Verified: `bash tools/check-doc-routes.sh` reports every section reference resolving (`evidence/structural-checks.txt`).
- [x] 3.1 This delta: `## ADDED Requirements`, one requirement and four scenarios, every scenario naming
      `gate#aBaseRedStepIsSubtractedAndNamed`. Verified: `openspec validate fix-a-base-red-step-is-subtracted-and-named
      --strict` (`evidence/validator-strict.txt`).
- [x] 4.1 `design.md` records the decision the artifact needed: the owning capability is `agent-issue-loop` and not
      `build-test-gate` (`build-test-gate` states the project gate's flavors and evidence and names no run tally), the
      requirement is `ADDED` because no shipped requirement states a base red is subtracted, and the alternatives the
      evidence eliminates — taking the pre-flight per gate rather than once per run, subtracting per step, reading the
      base from a working copy with uncommitted work. Verified: the readings in `evidence/readings.md`, which name each
      shipped site the requirement rests on.
- [x] 5.1 What this change does **not** cover, stated so a reviewer reads it as a gap and not as an omission: the
      change carries **no mutation record** — `tools/loop/tests/loop-mutations.sh` has no `921.*` entry, so the
      falsification exists only as the commit's prose (`8cd97718`: the case fails on a tree without `--base-red` with
      `unknown argument --base-red`, and with the subtraction guard removed a step red for a new case reads green,
      `expected '1', got '0'`), and `--check` cannot replay it. The four "not taken" arms of `tools/loop/run.sh:868`
      are asserted by no case. The probe that caught the shapes in the shipped run was a flake under sibling suites
      (`run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`, green alone), and this delta does not re-run it.