# Proposal — a rotation keeps the record lines the spend is computed from

## Why

**Root cause.** `tools/pi-child-events.sh`'s `rotate_events` (base `c0ddb97c`, `:134`–`:150`) kept the newest half of a
stream's lines and dropped the oldest half, and what it drops was therefore decided by **line position**. The oldest
half is where `turn_end` and `tool_execution_end` live — the records `tools/loop/state.sh`'s `child_usage_json` sums an
item's `turns`, `tokens`, `phases`, `roles` and `afterGate` from (`state.sh:970`, `:975`) — so a rotated item's
recorded spend was a silent lower bound, printed as a measurement. `rotate_events` records `dropped_turn_ends`, and the
only reader of that figure is `run.sh`'s `child_turns`, which adds it back to the turn **bound**; no cost figure ever
saw it.

**Evidence, measured in this tree (issue #840).** In the run started 2026-11-09T21:42:41Z nine of fifteen item streams
rotated and dropped **1 697 `turn_end` records against the 1 372 turns the state recorded** — the run's own record held
45 % of the turns it paid for. Per stream: `items/830` 333, `items/833` 277, `items/835` 223, `items/100` 187,
`items/831` 171, `items/722` 158, `items/807` 156, `items/832` 98, `items/829` 94. What fills a stream is not records
but deltas: in `items/833/events.jsonl` 16 662 of the ~17 400 surviving lines are `message_update`, and they are
4 741 310 of the file's 8 565 562 bytes (55 %), `tool_execution_update` another 255 184 B (3 %).

**The measured precondition that made the delta pass lossless.** Every `turn_end` of the archive carries
`message.usage`, and it is the object its last `message_update` carried: over six archived streams (`833`, `830`, `835`,
`100`, `831`, `807`) 747 of 747 turns carry it and 746 are byte-identical to the preceding update — the 747th has no
update at all, so its `turn_end` is the only record of what it spent. `state.sh`'s `turns` reads
`message_update.usage` alone, so dropping the deltas without a fallback would have emptied `metrics.tokens`; one
expression makes the turn's own record the fallback.

**The red test on the base** (`evidence` in this change's tasks; `/tmp/red-840.log` in the run that wrote it):
`adapter#aRotatedStreamKeepsEveryTurnEnd` fails with *every turn_end the child wrote survives the rotation: expected
'30', got '7'*, `state#theSpendSurvivesTheLossOfItsDeltaLines` with *and the input tokens of the turns whose updates are
gone: expected '600', got 'null'*, and `adapter#aStreamStillOverItsBoundAfterTheDeltaPassIsCutAndRecorded` with *no
delta line survives a rotation of this stream: expected '0', got '4'*.

**The single fix.** The rotation becomes two passes: a **delta pass** that drops `message_update` and
`tool_execution_update` and keeps every record line, and — only for a stream still over
`LOOP_CHILD_EVENTS_MAX_BYTES` after that — today's oldest-half cut, unchanged, with its cumulative `dropped_turn_ends`.
`message_update` is dropped only when every `turn_end` of the stream being rotated carries a `usage`; a provider that
reports usage on the updates alone keeps them, and only `tool_execution_update` (which nothing reads) goes. The spend
reads a turn's own `message.usage` when no update survives it. Measured on a copy of `items/833/events.jsonl` at a
4 MiB bound: **8 593 950 B → 3 590 953 B with all 90 `turn_end` records kept** (the line-position cut kept 57), and the
stream's two `session` records survive where the old cut kept one — the fragility #756 noted about `session_key_of`
loses its cause on a stream the delta pass can bring under the bound.

## Blast radius

- **`tools/pi-child-events.sh`** — `rotate_events` only. The record keeps its shape (`dropped_lines`, `dropped_bytes`,
  `dropped_turn_ends`, `at`), its one-record-first-line invariant and its cumulative turn count; `dropped_lines` and
  `dropped_bytes` are what *this* rotation removed, both passes together and never the record they are written in. The
  event loop, the answer path and every other line of the adapter are untouched.
- **`tools/loop/state.sh`** — one expression in `child_usage_json`'s `turns` (`:929`): the turn's usage is
  `.cur // $e.message.usage`. It moves the figure on streams that never rotated, and that is announced rather than
  reverted: on two archived streams a turn whose `turn_end` carries the usage where no `message_update` did is counted
  now, so `.pi/issue-loop/items/85` reads 49 → 53 turns and `items/198` 25 → 26 with their tokens unchanged (a usage
  object of zeros), and `items/830` gains one turn (128 → 129) and 9 363 tokens. A turn no update reported was still a
  turn the item paid for, so the count that rises is the honest one.
- **`run.sh`** — no change: `child_turns` still adds `dropped_turn_ends` cumulatively, and `child_context`'s
  `total >= done_lines` guard resets its reader when a rotation shrinks the file under it (observed: 40 lines, reader at
  40, file cut to 6 → the next look re-reads all 6 and reports the file's own maximum).
- **A stream that still overflows after the delta pass** stays a lower bound — its `dropped_turn_ends` names the turns
  it took, and the state's cost reader gains the figure in issue #841, not here. On the 2 MiB bound of
  `items/833/events.jsonl` that residue took 43 turns where the line-position cut took 33: the cut is the same one, but
  it now cuts records only, and the record says so.
- **Not touched:** the bound's value, `tools/loop/loop.conf`'s other keys, `.pi/**`, `openspec/specs/**`, the report.
  One fact of the bound's block is corrected rather than changed: `dropped_bytes` in a rotation record describes that
  rotation's removals alone, so a stream rotated twice has no byte total left to recover.

## Deferred

The re-read the issue asks for is **not** run by this change and is deferred to the next archived run whose streams the
delta pass keeps whole: whether the phases share rises from 52 % (issue #840, "What it owes"; `tasks.md` 5.4). It needs
a run, not a fixture, and it changes no code, so it is recorded here rather than counted as work this change does.

surface: 3 cases added, 0 rewritten, 2 code sites (one function, one expression), 2 mutation records, 1 requirement
added in `openspec/specs/agent-issue-loop/spec.md` — `grep -c 'rotate_events' tools/pi-child-events.sh` → 2 (definition
and its one call); `grep -n 'message.usage' tools/loop/state.sh` → 2 (the block comment and the expression in `turns`);
`grep -c '267\.' tools/loop/tests/loop-mutations.sh` → 4 (two comment lines, two records); the three case names are
declared in `LOOP_SHELL_CASES`, dispatched in the table and defined, 10 lines in `tools/loop/tests/loop-cases.sh`
