# Design — fix-an-item-over-its-token-band-is-flagged

## Root cause

One mechanism, at one place: `tools/loop/report.sh` and `tools/loop/state.sh` print the item's spend as a figure
and compare it with nothing, and `tools/loop/loop.conf` records no value for it to be compared with. A run that
cost twice what the one before it cost therefore prints the same shapes — a table row of plausible numbers and a
run-level total — as a healthy one, and the drift is only visible to a person who reads the children's event
streams by hand (the item's 1.8 GB read).

## The single fix

The figure is already recorded (`metrics.tokens`, `tools/loop/state.sh:481` and `:601`); what is missing is a
recorded number beside it and a comparison. So:

- `tools/loop/loop.conf` gains `LOOP_ITEM_TOKENS_WARN`, read through `loop_conf_value` (the seam every other
  recorded value already uses) with `0` as the fallback, so a configuration that records no key — a ported
  project's `loop.conf`, the synthetic fixture's — flags nothing instead of refusing to report.
- `report.sh` builds one line from the state, above the table, so the column's ratio is read beside its bound.
- `state.sh report` appends one column, `over`, which is the file's stated convention for a new column ("the
  columns are appended rather than interleaved so a reader that quoted the earlier shape keeps its columns in
  order", `tools/loop/state.sh:566`).

## The recorded value, and why that percentile

`LOOP_ITEM_TOKENS_WARN=16858818` — the p75 of the 164 items whose spend the loop's own states record
(`evidence/token-band.py`, log `evidence/token-band.log`: median 8 656 911, p75 16 858 818, p90 22 958 848, max
44 163 688; 40 of 164 items above p75, 16 above p90). p75 and not p90 because the band's p75 is 1.95× its median:
the item's symptom is "an item that costs twice what it used to", and that is where 2× the historical median
sits. p90 would be 2.65× the median and would sit above most of the items of the very run that was missed (its
per-item median had risen to 18.30 M), so it could reproduce the defect it is meant to catch. The cost of the
choice is accepted and stated: a healthy run flags about one item in four, which is one line of a report read by
a person at the end of a run, and the hardest items are among them — which is the point, since a regression
multiplies exactly those.

## Blast radius

- The report's table gains a sixteenth column. `report#wallTimeAndReasonInTheTable` is the only reader of the
  count (`16` pipes) and of the column indices, and its own comment counts them; both are updated, the indices
  of the existing columns are not.
- `report#zeroUsagePrintsDashNotZero` reads a run of four cells ending at `outcome`; a column appended after
  `stop` leaves it untouched.
- Nothing in `run.sh`, `state.sh`'s write path, the prompts, the gate or the pre-screen reads the key: an item
  above it is worked, reviewed, gated and closed exactly as before.
- An engine-side default is deliberately absent: `loop.conf` is the project's half (D16), and `0` in the engine's
  fallback is what makes an absent key mean "no band" in a project that records none.

## Alternatives the evidence eliminated

- **A stop** (end or block the item above the bound): eliminated by the item's own text and by the loop's
  recorded experience with bounds that end work (`guidelines/Backlog.md`, the "The loop" section, the child cap of #663) — it fires
  hardest on the items that were hardest to land, and it turns a measurement into a refusal.
- **A percentile computed at run time** from the archive, instead of a recorded value: the archive is
  machine-local (`.pi/issue-loop/runs/`, gitignored), so the bound would be absent in a fresh clone and would
  drift silently with the very regression it is meant to catch. The recorded value is auditable in the tree, and
  `loop.conf` already says a changing project value is raised there (#187).
- **A warning inside the `tokens` cell** instead of a column: the cell already carries three sub-figures and
  `cost=`, and a marker buried in it cannot be read by a filter. A column is greppable (`| 2.5x |`) and follows
  the file's append rule.
- **A `context`-based bound** (the largest context a child reached): the item names `metrics.tokens`, and context
  is a property of the window rather than of what the item was charged — the archive's context p90 (173 371) is
  already close to the window's own limit, so it measures the harness, not the item.

## Rollback

Revert the five files. The bound lives in one key: setting `LOOP_ITEM_TOKENS_WARN=0` on its own turns the flag
off with no code change, and a run never read it, so nothing about an item's outcome can be stale.
