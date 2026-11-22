# Proposal: fix-the-checkpoint-is-the-splits-reading

## Why

**Root cause.** `LOOP_ITEM_CONTEXT_WARN` (`tools/loop/loop.conf:267`) records 143 124, the archive's **p75** of the
largest context per item as measured on 2026-11-05, and that derivation was written when the key only printed a
line. Since #856 the same key is the reading at which the item's **first worker is ended and continued**
(`tools/loop/child.sh:501`, the split whose step `run.sh` records as `worker-checkpoint`). A split pays the session's
baseline a second time and saves only the turns after the crossing, so a checkpoint drawn at the p75 saves the turns
the same measurement already counted as the cheaper half: three items in four never reach it and pay the whole
window, and the one in four that does has paid all of it before the split.

**Evidence** ⊨ (change root at base `53a9e431`):

- the recorded line is the p75 and nothing else: `git show 53a9e431:tools/loop/loop.conf | grep '^LOOP_ITEM_CONTEXT_WARN='`
  prints `LOOP_ITEM_CONTEXT_WARN=143124`, and the derivation beside it (`tools/loop/loop.conf:233-235`) is the
  2026-11-05 `run.json`/`runs/*.json` distribution.
- the split is the child's end, not a line: `tools/loop/child.sh:501` takes it, and `tools/loop/run.sh:1220` records
  that the continuation is the one #851 landed.
- re-taken over the archive that exists now (2026-11-22, the 53 items under `.pi/issue-loop/items` whose stream
  carries a worker session, through `tools/loop-spend.py`'s reader with a split modelled as a baseline paid again and
  nothing carried over — `.pi/issue-loop/items/1003/scratch/split-model.py`, whose output is
  `.pi/issue-loop/items/1003/split-model.log`): as-is p50 147 413, p90 164 316, maximum 171 361 and 1 055 Mtok; at
  143 124, 989 Mtok with 28 items split; at 90 000, 866 Mtok — 18 % below as-is — with 41 items split. Read against
  the neighbouring readings (110 000: 887; 100 000: 871; 95 000: 868; 85 000: 865; 80 000: 867; 70 000: 875;
  50 000: 907), 90 000 is on the plateau's top: within 1 Mtok of the lowest (85 000's 865) with fewer items split.
- **red on HEAD** ⊨ (`run#theCheckpointIsTheSplitsOwnReading`): its first assertion greps the recorded line and HEAD
  records 143124, so it fails before the engine is started; and with the base's profile in place the run does not
  split the case's 95 000-token worker at all, because 95 000 is below 143 124 — the crossing the new reading takes
  is the one the p75 let run on to the stop. `run#aWorkerBelowItsCheckpointIsNotSplit` holds 60 000, below both.
- the ceiling is the item's own: `.pi/issue-loop/items/1003/split-model.log` counts the baseline twice and nothing
  else — not the continuation prompt, not the work a continuation re-reads — so 18 % is an upper bound on the gain,
  and the split count (41 of 53 against 28) is the figure a run's report is read beside.

**Consequence** ⊨: the item in four that pays the expensive window pays all of it, and the split the profile already
takes saves only what falls after 143 k — the share the same distribution counted as the cheap part.

## What changes

- `tools/loop/loop.conf:267`: `LOOP_ITEM_CONTEXT_WARN=143124` → `90000`, with the derivation beside it re-taken over
  the archive that exists now (as-is, the p75, 90 000, the neighbouring readings, the plateau's top, the ceiling).
  `LOOP_ITEM_CONTEXT_MAX=200000` and the two role bounds at 250 000 stay where they are; no engine change —
  `run.sh` reads the key and the continuation is the one #851 landed.
- `tools/loop/tests/loop-cases.sh`: `run#theCheckpointIsTheSplitsOwnReading` added (the red case); the seven cases
  that write the recorded checkpoint into their own copied profile carry 90000 beside it, and
  `run#aWorkerBelowItsCheckpointIsNotSplit` holds its child at 60 000 so it stays below the reading it is about.
- `tools/loop/run.sh:189-197` and `:1222`, `tools/loop/child.sh:501`, `tools/loop/tests/loop-mutations.sh:1281-1287`:
  the comments that called the checkpoint the p75, or named 143 124 as the recorded reading, state the split's own
  reading.
- `guidelines/BacklogTooling.md:150` and `guidelines/Backlog.recipes.md:626-637`: the two places that record the
  value and its measurement (the section 7b table and the paragraph beside it) carry 90 000 and the re-taken derivation.
- `openspec/specs/agent-issue-loop/spec.md`: one requirement modified — "The bound on a child is priced in what it
  spends, not in how many turns it takes" records where the checkpoint is set and adds the scenario the new case
  names.

surface: 1 case added, 7 cases rewritten, 0 fixtures realigned, 4 comment sites and 2 guideline sites realigned —
`tools/look.sh 'LOOP_ITEM_CONTEXT_WARN=90000' --path tools/loop` (12 matches in 2 files: the profile's one and the case
file's eleven) and `tools/look.sh "printf 'LOOP_ITEM_CONTEXT_MAX=200000"` (7 matches in 1 file, one per case that copies
the profile).

## Non-goals

- Not the stop. `LOOP_ITEM_CONTEXT_MAX` stays 200 000 — #833 recorded that refusal and #836 re-proposed it and was
  closed `NOT_PLANNED`; this change moves the checkpoint below it and never raises it.
- Not the engine. `tools/loop/run.sh` and `tools/loop/child.sh` read the key as they already do; the delta is the
  recorded value, the derivation that defends it and the case that pins it.
