# Design — one recorded field, one printed line

## What the defect is, and what it is not

The defect is not that a stream is bounded — that is #802's decision and it stands — and not that a rotation drops
records: #840 made the rotation drop the streamed deltas first and the oldest-half cut a fallback for a stream whose
records alone outgrow `LOOP_CHILD_EVENTS_MAX_BYTES`. What is left is that the residual rotation is **recorded** and
read by one reader only, the turn bound. A cost figure summed from what survives is a lower bound, and the report
printed it beside the two figures a reader would compare it against (the items' wall sum and the same items' token
sum) without saying which of the three was short.

## Where the rotation is read, and by whom

`rotate_events` writes `{"type":"stream_rotation","dropped_lines":…,"dropped_bytes":…,"dropped_turn_ends":…,"at":…}`
as the stream's first line, one record per stream: a rotation replaces the record it finds, and carries that record's
cumulative `dropped_turn_ends` into the one it writes. Two figures of different shapes therefore live in it — a
cumulative turn count and the bytes **that** rotation removed — and the state's reader keeps them apart: the greatest
`dropped_turn_ends` over the records (the newest, because the count is cumulative) and the sum of `dropped_bytes`
(each record states its own rotation's bytes). Under the adapter's one-record invariant both are the record's own
figures; a stream holding two degrades sanely rather than double-counting the turns, and the case's #24 pins it — 12
then 20 turns is 20, and 400 000 + 100 000 bytes is 500 000.

`child_usage_json` is the one place an item's spend is read, and it already holds the whole stream in one `jq -s`
slurp, so the record costs the reader one more expression and no second pass over the file.

## Why the field is `streamRotation` and not two top-level keys

The two figures are one fact and always arrive together: a stream with a rotation record has both, a stream without
one has neither. `metrics.streamRotation` is `null` for the second case, and the key is absent only in a state written
before this change — the convention the report already keeps for `dupReads` (`null`: this era records it and the item
has none; absent: the record predates the field) and for `gateWaived`. The alternatives were:

- **Two keys (`droppedTurnEnds`, `droppedBytes`)** — two nulls that must be read together as one answer, and a report
  that has to test both before it can say "no rotation".
- **A boolean `truncated`** — says a figure is short without saying how much, which is the defect the item is about.

## Why the report names every item and not only the worst

The `reads:` line names the item that repeated most, because a repeat count is a shape a rate already describes. A
lower bound is not: a run whose totals are short by one item and a run short by nine print the same totals, and the
reader's next question — which items do I not trust — is the one the line has to answer. So the third part of the line
is the list the `calls:` line prints for the items below its floor: `#830 333 · #833 277 · …`, ordered by the figure
dropped, the turns being the cumulative count the record states.

The extent is printed in turns and in the bytes the records state, because the turns are what the short figures
(`turns`, `phases`, `afterGate`) are summed from and the bytes are what the bound was about — and the two figures are
read from **two** selections, which the line states: the turns and the item list are the items that lost a turn, the
bytes are summed over every item whose stream carries a record, so a rotation that removed only deltas is counted in
the bytes and is no lower bound. A run whose streams were never rotated prints `- · no item had its stream rotated`; a
run whose one rotation took no turn prints `no figure of this run is a lower bound — 0.1 MiB removed by the 1 rotated
stream, no turn dropped · #51`; a state that predates the field prints the third dash, with the reason — the
distinction the `reads:` line already makes.

## What is deliberately not changed

No existing line's computation, no column, no bound, and nothing that reads the new field to decide anything: the
line is a warning the way the calls-per-turn floor and the repeat-reads count are. `LOOP_CHILD_EVENTS_MAX_BYTES`
keeps its value — a run that wants fewer lower bounds rotates less, and #840 already made what a rotation costs
proportional to the deltas.

## Verification

- `report#aTruncatedStreamSaysSo` — three states in one case: two items of which one's stream carries a rotation
  record, so the line must name that item with its figure and not the other; a state whose `streamRotation` key was
  removed, which must print the "predates" form rather than a zero; and the `metrics.streamRotation` values
  themselves, as numbers, so the case cannot pass on two nulls.
- Mutation `268.1` prints the line without the items it names, and the case goes red.
