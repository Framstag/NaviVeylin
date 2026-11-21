# Proposal: a-pre-gate-round-its-cap-halved-is-skipped

## Why

The round before the gate is handed the change's diff by `bound_review_inputs` (`tools/loop/run.sh`), each of its
parts capped at `LOOP_REVIEW_INPUT_MAX_BYTES=32000`. When the cap takes most of the whole input, the round reads a
diff with its middle missing and is asked for a verdict on it — a diff it cannot read whole is one it cannot judge.
It is the only phase the run pays for twice: the
item-level round 1 reads the same diff again, cut to the same cap.

- **Observed** ✗: in the run started 2026-11-18T22:18:25Z the cap cut 5 of the 8 review inputs — 68 % of #901's
  (66 698 of 98 662 B), 67 % of #902's, 47 % of #894's, 38 % of #931's, 5 % of #963's — and separately **three of the
  run's eight pre-gate rounds ended at the 110-turn bound with no verdict**: #950, #964 and #894
  (`pre-gate-review-restart.md` beside each item's `pre-gate-review.md`). The record ties neither fact to the other:
  #950's and #964's pre-gate inputs were not cut at all, and #894's was cut 26 %, under the rule's own threshold. The
  scale files on disk name each item's latest cut, and #901's reads 67 %
  (63 485 of 95 465 B): the diff it was taken over is smaller than the one the figure above describes, which is why
  the numbers are quoted from the run's record and the files only for their existence.
- **Measured** ⊨: `reviewer-pre-gate` spent **60.3 Mtok = 25 % of the run's 240.6 Mtok**, and the three items whose
  round the bound ended carry 32.6 Mtok of it (13.6 %). #894's round was 43.6 % of that item's tokens, #950's 50.1 %.
- **Why not raise the cap** ⊕: the parts are already cut at 32 kB because the round's turn budget cannot read more;
  #901's part is 98 662 B and #902's 149 838 B. A larger cap would move the cut, not remove it, and the round would
  end at the same bound with more context to lose.

## What Changes

- **`tools/loop/run.sh`: the pre-gate round reads the share before it starts.** The scale `bound_review_inputs`
  already composes (`review_input_cut_scale`, printed on the item's record as `LOOP-REVIEW-INPUTS`) is read as the
  round's precondition: when more than half of the whole input was cut, the round is **skipped** and the reason is recorded
  on the item — "its input is halved, the gate runs on a diff this round cannot read whole". The gate and the
  item-level round still run, in that order, unchanged.
- **`tools/loop/tests/loop-cases.sh`: two cases.** One stages an input cut past half and asserts the round is not
  launched and the item records why; one stages an input cut under half and asserts the round still runs.
- **The report names a skipped round as skipped** — `review: n/m first rounds passed` already counts undecided
  rounds apart (`2 undecided` in the analysed run), and a skipped pre-gate round adds one more class to that line
  rather than reading as a round that ran and failed.
- **`guidelines/Backlog.recipes.md` section 7a** — the section that owns the round's rule states the skip, because it
  currently says the diff is read before the item's gate and would otherwise describe a round that no longer always
  runs.

## Evidence

- The five cut files are on disk — `items/{901,894}/review-pre-gate.cut.md` and
  `items/{902,894,931,963}/review-1.cut.md` — and the restart markers are
  `items/{950,964,894}/pre-gate-review-restart.md`, each naming `LOOP_CHILD_TURNS_MAX_REVIEWER_PRE_GATE=110`.
  #894's 47 % is its round 1's share: its pre-gate round was cut 26 %, and a rule read on a share past half would
  still have run that round.
- The role totals are the frozen per-item figures in `.pi/issue-loop/run.json`
  (`.items[].metrics.roleModels["reviewer-pre-gate"]`); the run total is `report.sh`'s `roles:` line.

surface: 1 precondition, 2 cases, 1 report class, 1 config comment, 1 guideline paragraph — `tools/look.sh
'review_input_cut_scale' --path tools/loop/run.sh`, `tools/look.sh 'LOOP_REVIEW_INPUT_MAX_BYTES' --path
tools/loop/loop.conf` and `tools/look.sh 'pre-gate' --path guidelines`
