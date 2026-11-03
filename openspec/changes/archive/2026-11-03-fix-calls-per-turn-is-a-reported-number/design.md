# Design — fix-calls-per-turn-is-a-reported-number

## The number, and where it is derived

The ratio has two possible homes: recorded as a ratio when the item closes, or derived by the report from two
counts.

**Derived from two counts (chosen).** `child_usage_json` already reads the item's whole event stream once and
records what it finds there; the calls and the turns are two more counts of the same pass. Recording them keeps
the state's own rule — a stored figure is a fact, a derived one is the report's — and lets a reader answer a
question the ratio cannot: an item with 6 calls and 2 turns is cheap, an item with 2 calls and 4 turns is a chain,
and a ratio alone would hide the difference between a short session and an empty one. It also keeps the sum
honest over an item's children: `metrics.tokens` is already a sum of the same children, and the two counts are
summed beside it, not recomputed from a ratio.

**A `callsPerTurn` field (rejected).** It cannot be summed over children (an average of averages), so an item whose
gate child batched and whose tail did not would be recorded as one of the two, and the number would be a claim
about a session rather than about the item.

## The denominator

`turn_end` lines, not the turns that carried a usage record. The two differ on a provider that streams no usage,
and there the ratio read over the reporting turns alone would flatter exactly the provider that measures least.
A turn is every `turn_end`; a call is every `tool_execution_end`, including one whose `toolName` is absent, which
is why the calls count is not the sum of `metrics.tools` (that map is keyed by name and drops the unnamed).

## Where the run's rate is printed, and what it names

The report has two run-level places: the lines before the table (the token band's line lives there) and the trend
block (the review rate, the wall figures, the landings). The rate belongs in the trend, because it is a figure
about the loop's shape rather than about one item's spend, and it is one line there rather than a second line above
the table: the token band names items *above* a band and the calls floor names items *below* a floor, and two
opposite lists on two lines would read as one measurement split in half. The line prints the run's own ratio with
the two counts it came from, so a reader can check it, and names each item below the floor with its own ratio.

The per-item half is a table column appended after `over`, the file's own convention: a column that arrives is
appended so every reader that quoted the earlier shape keeps its indices. `report#wallTimeAndReasonInTheTable` is
the one reader of the column count and is updated with it. Unlike the band column, this one is the item's own
figure and not a multiple of a bound, so it prints whether or not a floor is recorded: the floor is the comparison
the trend makes, and `0` means no comparison rather than no measurement.

## The recorded floor

`LOOP_ITEM_CALLS_PER_TURN_MIN=1.05` is the archive's p25 (measured: p10 0.99, p25 1.05, p50 1.11, p75 1.22, p90
1.32, max 1.61 over 227 items), rounded to the two decimals a ratio is read at. The p25 and not the p50 because a
floor at the median flags half the archive, and a flag on half the items is a banner rather than a measurement —
the argument that kept the token band off its own median and at the p75. The direction is the token band's mirror
— the bad tail is the low end here — so the worst quarter is flagged, which is the band a chain of single-call
turns sits in.

`0`, or no key at all, is no floor, and the trend says the floor is off rather than `none`: the rule every spend
column keeps about an unreported figure. A value that is not a decimal number is treated as `0` before jq sees it,
because `--argjson` on a malformed value fails the whole program and a report whose `calls:` line disappears is
worse than one that says the floor is off.

## Alternatives the evidence eliminated

- **A stop at the floor** — rejected by the item's own text and by the token band's precedent: a bound that ended
  an item would trade a measurement for a refusal and would fire hardest on items whose work was nearly done. The
  stop that already exists bounds the *symptom* (`LOOP_ITEM_TURNS_MAX`), not the shape.
- **Measuring only the worker child** — rejected: an item's spend is all its children's, and the tail and the
  reviewer are exactly the sessions with few calls and many turns. The counts are summed where its tokens are.
- **Reading the ratio in `tools/look.sh` or `phase.sh`** (the levers) — rejected: those are the things the number
  is meant to show the use of, and a measurement taken inside a lever would only ever see the sessions that used
  it.

## Risk

A ported project's `loop.conf` records no key, so its report prints the floor as off and nothing is flagged: the
engine names no project value (D16). A state written before this change prints `-` in the column and `-` plus its
reason in the trend, which is the same answer an item that reported no spend gives. Nothing reads the key, so
there is no behaviour to regress.

## Verification

- `report#anItemBelowTheCallsPerTurnFloorIsNamed` — a stub stream with a known 6 calls / 2 turns and a known 2
  calls / 4 turns: the trend prints `1.33 calls per turn (8 calls / 6 turns)` and names `#22 0.5`; the table's
  column 17 reads `3` and `0.5`; the floor off prints `floor off` **and names no item**; a state with no stream
  prints `- · no item recorded a turn`; a state that predates the two counts says so.
- `run#childUsageIsRecordedPerItem` — the two counts are the item's, summed over its two children (`5` and `2`).
- `tools/loop-mutations.sh --only '78.1 78.2 78.3 78.4'` — each site's mutation turns the case red and the restore
  green again.
- `openspec validate fix-calls-per-turn-is-a-reported-number --strict` — valid before the archive.
- The gate (`tools/loop/gate.sh`), run once, and the records `tools/check-doc-routes.sh` and
  `tools/check-todo-references.sh` owe for the documents.
