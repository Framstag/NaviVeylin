# fix-loop-hold-states-its-reason — tasks

- [x] 1.1 A case over the hold arm that asserts the comment names the verdict and the worker's root cause, and
      that a call without `--reason` writes nothing. Red on HEAD: the label edit was written before the check
      and no comment existed at all.
- [x] 1.2 The case reads the write log rather than the printed output, so the assertion is about what the tracker
      received (`labels#holdCarriesItsReason`).
- [x] 2.1 `labels.sh`: the hold arms set `comment_only`, the two arguments are required before any write, and the
      note goes through `close.sh --comment-only`.
- [x] 2.2 `run.sh`: the skip path hands over `LOOP-ROOT-CAUSE` and the evidence paths it already holds.
- [x] 2.3 `run#aSkipClearsTheInProgressMark` extended: exactly one comment on a skipped item, carrying the root
      cause the worker reported.
- [x] 3.1 Mutation 21.2 (`comment_only=1` → `0`) goes red on `labels#holdCarriesItsReason` and green after the
      restore.
- [x] 3.2 `tools/loop-selftest.sh` 78/78; `tools/issue-tooling-selftest.sh` 83/83; `tools/check-portability.sh`
      OK.
