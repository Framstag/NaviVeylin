# Proposal: a-phase-that-wrote-nothing-is-asked-once-more

## Why

`change_answered` (`tools/loop/run.sh`, issue #827) reads a phase's report and decides whether it carries any line
the item's arms read. A change child that exits 0 and writes an empty report is therefore *seen* — and the run's only
answer to it is to block the item with `child-failed`. The work the phase did is lost with the item: the diagnosis it
wrote, the tree it may have committed, the wall it spent.

- **Observed** ✗: item #892 of the run started 2026-11-18T22:18:25Z spent **2 166 s and 6.29 Mtok**, and its
  `items/892/worker-report.md` is **0 B** (status 0) while `items/892/evidence-diagnosis.md` beside it carries a
  finished diagnosis of the handed change — the phase answered, it just did not write the one line the run reads.
  The gate phase that did run was 86.1 % of that item's clock (1 847 s of 2 145 s) and produced no verdict, and the
  item blocked with no gate at all.
- **Measured** ⊨: `child-failed`/`child-blocked` is the archive's largest stop class — **5 of 18 items** ended by it
  (`analysis.sh rates --n 12`), and 2 of the analysed run's 3 blocks carry it.
- **Not the fix** ⊕: reading the file as an answer when it is empty. #827's rule is right — an empty report is not a
  verdict, and the arms that read one would decide the item on nothing.

## What Changes

- **`tools/loop/run.sh`: the phase is asked once more, from its own checkpoint.** When `change_answered` is false and
  the child exited 0, the phase is relaunched **once** with the checkpoint prompt (`worker-checkpoint`'s composition,
  which already carries what the tree holds) instead of the item being blocked. A child the run itself ended at a
  bound is not retried — `LOOP_CHILD_TIMEOUT` and `gate_exit -eq 0`'s guard (issue #224) already separate that case,
  and the retry inherits both.
- **The item records that it was retried** — one field beside the phase's other bounds, so a reader can tell a phase
  that answered at the second asking from one that answered at the first, and the report can count the retries.
- **`tools/loop/tests/loop-cases.sh`: two cases.** One stages a change child that exits 0 with an empty report and
  asserts exactly one relaunch and an answer at the second asking; one stages a child the run ended at a bound and
  asserts no relaunch.
- **`tools/loop/loop.conf`: the retry is not a new key.** It is one attempt, fixed, so a project that wants none
  records `0` under the phase's existing turn bound rather than a second switch.

## Evidence

- `items/892/worker-report.md` is 0 B with `worker-report.md.status` `0`; `items/892/evidence-diagnosis.md` holds the
  diagnosis the phase did write; `report.sh` records the item's clock as `model 282 s · gate 1 847 s · other 16 s`.
- The archive's stop classes are `analysis.sh rates --n 12`'s `blocked by stop class:` line (8 archived states,
  18 items worked, `child-blocked 5`).

surface: 1 retry path, 2 cases, 1 state field, 1 report count — `tools/look.sh 'change_answered' --path
tools/loop/run.sh` and `tools/look.sh 'worker-checkpoint' --path tools/loop/run.sh`
