# fix-loop-leftover-item-branch — tasks

## 1. The red cases (before the fix)

- [x] 1.1 `worktree#reusesABranchThatCarriesNoWorkOfItsOwn` — a leftover branch at an older base, with the
      base advanced and the state re-initialised, must start again from the new base.
- [x] 1.2 `worktree#refusesABranchCarryingAPreviousAttempt` — a branch with a commit the base does not have
      must exit 5, name the branch and the commit, and leave the branch where it was.
- [x] 1.3 `run#leftoverBranchIsRecordedNotDropped` — a second run over an item whose branch carries work must
      record the item (blocked, reason naming the branch) and escalate it, not end with no items at all.
- [x] 1.4 Red on HEAD, verified against a copy of `tools/loop` as committed (8ab6f6b):
      `a branch carrying no work was refused: Schwerwiegend: Branch 'fix/issue-21-demo' existiert bereits`;
      `a branch that carries work refuses the item: expected '5', got '2'`;
      `the leftover branch is recorded as an item rather than dropped: expected '1', got '0'` —
      `evidence/red-on-head.log`, with the real run's `nothing processed` report beside it.

## 2. The fix

- [x] 2.1 `worktree.sh start-item`: `merge-base --is-ancestor <branch> <base>` decides between reuse
      (`switch -C` from the base) and refusal (`loop_refuse`, branch + commits named).
- [x] 2.2 `run.sh`: the refusal on a leftover branch is escalated (`agent/needs-decision`, `status/on-hold`,
      one comment naming the branch), recorded as a blocked item with the refusal as its reason, and the run
      takes the next candidate. `base moved` keeps its own handling.

## 3. The mutations (one per check)

- [x] 3.1 `12.5` the reuse test never true → `worktree#reusesABranchThatCarriesNoWorkOfItsOwn` red.
- [x] 3.2 `12.6` the same test always true → `worktree#refusesABranchCarryingAPreviousAttempt` red.
- [x] 3.3 `12.7` the recorded item dropped → `run#leftoverBranchIsRecordedNotDropped` red.

## 4. The gates

- [x] 4.1 `bash tools/loop-selftest.sh` — the full suite, green.
- [x] 4.2 `bash tools/loop-mutations.sh` — every mutation red, none unnoticed, every restore green.
- [x] 4.3 `bash tools/check-portability-selftest.sh` — the loop against a foreign project, green.
- [x] 4.4 Re-run with the one later fix that landed in the same engine (`53` cases, `19` mutations): the
      suite, the mutation set and the portability suite are green with all three fixes in the tree —
      `evidence/loop-selftest-final.log`, `evidence/loop-mutations-final.log`,
      `evidence/portability-selftest-final.log`.

## 5. The records

- [x] 5.1 Issue #170 filed, carrying the run's own output as evidence.
- [x] 5.2 This change: proposal, design, the delta above, tasks, the red-on-HEAD log and the real run's
      transcript in `evidence/`.

## 6. Not done here, and named

- [ ] 6.1 No item has been closed by a real run since the two fixes: the next `tools/loop/run.sh` is the
      end-to-end proof.
