# Tasks — the red gate travels with the change it blocked

Spec: `agent-issue-loop`, the added requirement "A branch whose run ended on a red gate carries that verdict and is
not adopted" (delta `specs/agent-issue-loop/spec.md`). Scope: `tools/loop/**` and one guideline sentence, so the gate
owes the shell suites of `tools/loop/tests/loop-selftest.sh` and the mutation check, and it is run once by this
session.

## 1. The red case, before the fix

- [x] 1.1 `tools/loop/tests/loop-cases.sh`: `worktree#aBranchWhoseRunEndedBlockedIsNotAdoptable`, declared in
  `LOOP_SHELL_CASES`, dispatched, and carrying its `# files:` declaration — an item that starts, archives, copies one
  case's log, ends with a red tally, and whose branch then answers `gate-red` and is refused.
  *Verified*: red on HEAD — `bash tools/loop/tests/loop-selftest.sh --case worktree#aBranchWhoseRunEndedBlockedIsNotAdoptable`
  fails with `expected 'gate-red', got 'adoptable'`, log in `evidence/red-on-HEAD-aBranchWhoseRunEndedBlockedIsNotAdoptable.log`.

## 2. The writer

- [x] 2.1 `tools/loop/worktree.sh`: `carry_gate_record` copies a red tally as `evidence/gate-red.log` carrying the
  gate's `LOOP-GATE:`/`LOOP-GATE-STEP:` lines only, and keeps the green path as it stands.
  *Verified*: 1.1 asserts the record exists on the blocked branch; `worktree#theGatesTallyTravelsWithTheChangeItMeasured`
  still passes for the green path.

## 3. The reader and the states that act on it

- [x] 3.1 `tools/loop/worktree.sh`: `carried_gate_red <branch> <change>` reads the evidence records whose
  `LOOP-GATE:` line is red, and `branch_state_of` answers `gate-red` before `archived-not-green`; the doc comment lists
  the state.
  *Verified*: 1.1 asserts the state and the record's name; `bash -n tools/loop/worktree.sh`.
- [x] 3.2 `tools/loop/worktree.sh`: `cmd_branch_state` prints the change and the record for `gate-red`, and
  `cmd_adopt_item` refuses it with `ended on a red gate` before the verification refusal.
  *Verified*: 1.1 asserts exit `5`, the sentence, and that the blocked change never landed on the base.
- [x] 3.3 `tools/loop/layout.tsv`: `carried_gate_red` joins `worktree.sh`'s symbol list.
  *Verified*: the layout check runs green in the gate.

## 4. The record and the guard

- [x] 4.1 `tools/loop/tests/loop-cases.sh`: the blocked half of `worktree#theGatesTallyTravelsWithTheChangeItMeasured`
  asserts the red tally travels as `gate-red.log`, that no step XML travels with it, and that the state is `gate-red`.
  *Verified*: green after 2.1–3.2.
- [x] 4.2 `tools/loop/tests/loop-mutations.sh`: one mutation drops the `gate-red` arm of `branch_state_of`, and case 1.1
  reddens.
  *Verified*: the mutation run reddens 1.1 and the tree is restored byte for byte.

## 5. The guideline

- [x] 5.1 `guidelines/Backlog.recipes.md` (the subsection the route line names): the sentence that says a gate that
did not pass carries nothing states instead what is carried and why.
  *Verified*: `tools/look.sh 'gate-red.log' --path guidelines/Backlog.recipes.md` names the line, and the change's
  route line resolves in `evidence-check.sh`'s C6.

## 6. The change's own record

- [x] 6.1 The evidence directory holds the red-on-HEAD log and XML, the green run of the two cases and the mutation
  record; `bash tools/loop/evidence-check.sh openspec/changes/fix-a-blocked-branch-carries-its-red-gate --root .` reports no
  failure.
  *Verified*: `PASS` on its C1–C6, and `bash tools/loop/tests/loop-selftest.sh --group worktree` green over all 34 cases
  (`evidence/green-aBranchWhoseRunEndedBlockedIsNotAdoptable.log`,
  `evidence/green-theGatesTallyTravelsWithTheChangeItMeasured.log`, `evidence/mutation-1035.1.log`).
- [x] 6.2 Archive the change with its delta, then run `./tools/loop/gate.sh` once, detached, and quote its own
  `LOOP-GATE:` line.
  *Verified*: the gate line's tally and `failures=0 errors=0`.
