# fix-loop-skip-clears-in-progress — tasks

## 1. The red case (before the fix)

- [x] 1.1 `run#aSkipClearsTheInProgressMark`: a run whose worker reports `skipped`/`stale` must leave a gh
      record carrying `--remove-label agent/in-progress` and `status/on-hold`.
- [x] 1.2 Red on HEAD, verified against a copy of `tools/loop` as committed (8ab6f6b):
      `a skipped item does not keep the in-progress mark: '--remove-label agent/in-progress' not found in:
      gh issue edit 21 --add-label agent/in-progress …` — `evidence/red-on-head.log`.

## 2. The fix

- [x] 2.1 `run.sh`'s skip path calls `loop_labels unpick "$issue"` before it applies the verdict, so the
      three exit paths (closed, blocked, skipped) all clear the mark.

## 3. The mutation (one per check)

- [x] 3.1 `12.8` removes the `unpick` call → `run#aSkipClearsTheInProgressMark` goes red.
- [x] 3.2 `tools/loop-mutations-selftest.sh` asserts the harness lists what `MUTATIONS=(…)` declares instead
      of a spelled count (the set is `19` today; the number had already been bumped once, to twelve).
      `bash tools/loop-mutations-selftest.sh` green. This is the guard CI's `Check agent issue loop` runs
      beside the loop suite.

## 4. The gates

- [x] 4.1 `bash tools/loop-selftest.sh` — 53 cases, green: `evidence/loop-selftest-final.log`.
- [x] 4.2 `bash tools/loop-mutations.sh` — 19 red, none unnoticed, no failed restore:
      `evidence/loop-mutations-final.log`.
- [x] 4.3 `bash tools/check-portability-selftest.sh` — 21 cases, green:
      `evidence/portability-selftest-final.log`.
- [x] 4.4 `bash tools/loop-mutations-selftest.sh` — green, with the set size derived from the declared set:
      `evidence/loop-mutations-selftest.log`.

## 5. The records

- [x] 5.1 Issue #171 filed with the post-run label state as evidence.
- [x] 5.2 This change: proposal, design, the delta above, tasks, the red-on-HEAD log and the three gate logs.
- [x] 5.3 Issue #109's stale `agent/in-progress` mark removed once (`tools/issues/update.sh`).

## 6. Not done here, and named

- [ ] 6.1 Nothing re-measures the tracker after the next real skip; the label state of the next skipped item
      is what confirms this end to end.
