# Tasks — a block names the class of the arm that stopped it

## 1. Measure before changing code

- [x] 1.1 Count `stopReason` over the run archive and name the nulls: 148 states, 476 item records, 165 `done`,
  127 `blocked`, of which `stopReason` is null on 108, `bound` 6, `refused` 8, `unmergeable` 5
  (`evidence/archive-stop-reason.log`, issue #865's 104 of 123 over 145 states grown into rather than out of).
- [x] 1.2 Count the writers on base `5c045e55`:
  `git grep -c -F 'outcome="blocked"' main -- tools` → 18 matches in one file (`tools/loop/run.sh`, every arm), and
  `stop_reason` is initialised empty at `run.sh:2998`, named `refused` at `:3061` and `:3351`, `unmergeable` at
  `:3386`, and overridden to `bound` at `:3012` and `:3338` — so three of the eighteen arms name a class.
- [x] 1.3 Read `state.sh done`'s vocabulary on the base: four values, the empty one accepted
  (`tools/loop/state.sh:666`), which is the door that let the other fifteen arms through unnamed.
- [x] 1.4 Classify what the archive's classless blocks were: one class per mechanism the arms distinguish, and nothing
  guessed — the item's three examples are instances of that rule and not a closed set (design, "Alternatives the
  evidence eliminated").

## 2. One place writes a block

- [x] 2.1 Add `block()` beside `stop_run` (`tools/loop/run.sh:747`): the result, the class and the reason written
  together, so no arm can block without naming the class it stopped on and the class cannot drift from the result.
- [x] 2.2 Rewrite the eighteen arms as calls — see `tools/look.sh '^ *block [a-z]' --path tools` → 18 matches — with
  every reason string kept byte for byte.
- [x] 2.3 Leave the pre-gate arm as a five-line `if … else`: a round the run's own bound ended is recorded `cut`, the
  spec names `bound` on the worker's, the gate child's, the tail's and the rework worker's results, and writing the class
  there would refuse the cut's own state write — verify that the cut branch writes no class.

## 3. The record accepts the vocabulary

- [x] 3.1 Grow `state.sh done --stop-reason`'s vocabulary from four values to twelve — one class per mechanism, the
  empty value still accepted for a state written before the field existed — and name the twelve in its usage error.
- [x] 3.2 State in the same comment what each class means and which arms it belongs to, so the vocabulary is read from
  one place.

## 4. The case and the mutation

- [x] 4.1 Red on HEAD: add the class assertion to `run#anEmptyTreeIsRefusedBeforeTheGateChild` and prove it fails against
  the base engine — `tests=1 failures=1 errors=0`, `expected 'uncommitted', got ''`
  (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`, 2026-11-13T16:23:16Z).
- [x] 4.2 Green on the fix: the same case passes (`evidence/green-red-case.log`).
- [x] 4.3 Extend `state#aBlockedItemNamesTheMechanismThatStoppedIt` so the usage error is read against the vocabulary
  the record now holds, the classes the run's own arms name included.
- [x] 4.4 Record mutation `865.1` (`tools/loop/tests/loop-mutations.sh`): empty the class at the one place it is
  written, the case must go red, the restore green (`evidence/mutation-865.1.log`).
- [x] 4.5 Re-measure the mutation records the rewritten arms invalidated — `58.1` and `58.2` addressed the two calls that
  became `block refused …` and `block unmergeable …` (`evidence/mutation-records.log`).

## 5. The rule and the record's prose

- [x] 5.1 Modify the capability's requirement (`specs/agent-issue-loop/spec.md` delta): the class per arm, one write,
  the empty value still accepted, and the scenario that names an arm's class.
- [x] 5.2 Correct section 7e of `guidelines/Backlog.recipes.md` (`:1287`) in two places — `bound` is one class of the vocabulary and not "the
  third value beside `refused` and `unmergeable`" (`:1376`), and the report names a block's own class for every arm
  instead of "one of the loop's two known mechanisms" (`:1425`).

## 6. Evidence

- [x] 6.1 Carry the measurements and the runs this change cites in `evidence/`: the archive counts, the red and green
  case runs, the mutation's log, the structural checks and the gate's own log.
- [x] 6.2 `evidence/evidence-check.txt` holds `evidence-check: PASS` for the cited results.

## 7. Verification of the files this change edits

- [x] 7.1 The loop's own suite over the changed engine and cases: `tools/loop/tests/loop-selftest.sh --from-git` and the
  `run`, `state` and `report` groups; `tools/loop/tests/loop-mutations.sh --check` for `865.1`, `58.1` and `58.2`.
- [x] 7.2 The structural checks the change owes — `tools/check-doc-routes.sh`, `tools/check-doc-values.sh`,
  `tools/check-openspec-hygiene.sh`, `tools/check-portability.sh`, `tools/tests/check-rule-routes.sh`
  (`evidence/structural-checks-rework.log`).
- [x] 7.3 A full gate run over the finished tree, whose one red — `phase#aResultOverTheBoundIsCutAndNamed` — is the
  pre-existing `tools/loop/phase.sh` 4000-character cut and not this change's (`evidence/gate-run.log`).
