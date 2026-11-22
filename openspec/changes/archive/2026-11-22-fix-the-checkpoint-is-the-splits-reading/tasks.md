# Tasks: fix-the-checkpoint-is-the-splits-reading

No compiled file is touched: the delta is one profile value, the derivation that defends it, the cases that pin it
and three comments. The structural check of each file stands in for a build, and the case set the file declarations
select stands in for the suite until the gate runs it once.

## 1. Re-take the derivation over the archive that exists now

- [x] `.pi/issue-loop/items/1003/scratch/split-model.py` reads the archive through `tools/loop-spend.py`'s own reader
      and models a split as a baseline paid again with nothing carried over.
- [x] Its output is `.pi/issue-loop/items/1003/split-model.log` (2026-11-22T05:30:42Z): as-is 1 055 Mtok, 143 124 989,
      90 000 866, 85 000 865, and the rising totals below 85 000.
- **Verify**: `python3 .pi/issue-loop/items/1003/scratch/split-model.py --at 0 --at 143124 --at 90000` prints the two
  rows the change quotes.

## 2. Record the value and its derivation

- [x] `tools/loop/loop.conf:267`: `LOOP_ITEM_CONTEXT_WARN=90000`.
- [x] `tools/loop/loop.conf:243-266`: the paragraph that called the checkpoint the p75 states the split's own
      measurement, the table above it, the plateau's top and the honest ceiling.
- **Verify**: `grep -x 'LOOP_ITEM_CONTEXT_WARN=90000' tools/loop/loop.conf` prints the line, and
  `grep -c '143124' tools/loop/loop.conf` prints 0.

## 3. Realign the pointers that named the p75

- [x] `tools/loop/run.sh:189-197`, `tools/loop/run.sh:1222`, `tools/loop/child.sh:501`.
- [x] `tools/loop/tests/loop-mutations.sh:1281-1287` (the mutation notes that quote the checkpoint).
- [x] `guidelines/BacklogTooling.md:150` (the profile table's row) and `guidelines/Backlog.recipes.md:626-637` (the
      paragraph that records the measurement).
- **Verify**: `tools/look.sh 'the checkpoint is the p75'` prints 0 matches, and
  `tools/look.sh 'LOOP_ITEM_CONTEXT_WARN=90000' --path tools/loop` names `tools/loop/loop.conf` once.

## 4. The red case, and the cases that carry the value

- [x] `tools/loop/tests/loop-cases.sh`: `run#theCheckpointIsTheSplitsOwnReading` added, declared and dispatched; it
      greps the shipped profile for 90000 and holds a 95 000-token worker between it and the old p75 of 143 124.
- [x] The seven cases that write the recorded checkpoint into their copied profile write 90000 beside it.
- [x] `run#aWorkerBelowItsCheckpointIsNotSplit` holds its child at 60 000, below both the old and the new reading.
- **Verify**: `bash tools/loop/tests/loop-selftest.sh --case run#theCheckpointIsTheSplitsOwnReading` passes with the
  value in place, and fails on the base profile (`git show 53a9e431:tools/loop/loop.conf`), which records 143124 and
  leaves a 95 000-token worker unsplit.

## 5. The requirement

- [x] `openspec/changes/fix-the-checkpoint-is-the-splits-reading/specs/agent-issue-loop/spec.md`: the requirement "The
      bound on a child is priced in what it spends, not in how many turns it takes" records where the checkpoint is
      set, and the scenario `run#theCheckpointIsTheSplitsOwnReading` is one of its `**Case**:` lines.
- **Verify**: `bash tools/check-change-readiness.sh openspec/changes/fix-the-checkpoint-is-the-splits-reading` prints
  `LOOP-CHANGE-READY: ok`.
