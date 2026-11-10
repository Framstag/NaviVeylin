# Design — the delta pass, and where the turn's usage comes from when it is gone

## What the fix has to be a fix of

The measured defect is not that streams are bounded — that is #802's decision and it stands — but that **which** lines a
rotation removes was decided by position in the file. `rotate_events` kept `lines / 2`; nothing in it knew what a line
was. The records `state.sh`'s `child_usage_json` sums (`turn_end` at `:970`, `tool_execution_end` at `:975`) are the
oldest lines of a stream, so the cut took exactly what the cost record is computed from: 1 697 `turn_end` records
dropped against 1 372 recorded turns over nine of fifteen items in the run of 2026-11-09T21:42:41Z.

## The two passes, and why the order is not free

- **The delta pass first**, dropping `message_update` and `tool_execution_update` and keeping every other line,
  including the rotation record the previous rotation wrote (its cumulative turn count is carried into the one this
  rotation writes, so the record stays a single first line). This is where the bytes are: 4 741 310 of the 8 565 562
  bytes of `items/833/events.jsonl` are `message_update`, 255 184 are `tool_execution_update`.
- **The oldest-half cut second, and only if the file is still over `LOOP_CHILD_EVENTS_MAX_BYTES`.** The alternative —
  cutting until under the bound rather than to a half — was refused: it changes how often a rotation runs and therefore
  what the bound costs per event, and the residue it would serve is #841's subject, not this item's. Keeping the
  existing cut also keeps the record's meaning for a stream that overflows after the pass.

**Why the delta pass is not unconditional.** `state.sh`'s `turns` reads a turn's usage from the `message_update` that
precedes its `turn_end`. The archive says that update is redundant — every `turn_end` carries `message.usage`, and over
six streams 746 of 747 turns carry the byte-identical object their last update carried, the 747th having no update at
all — but that is a property of the providers this project has run against, not of the dialect. So the pass reads the
stream once and keeps the updates when any `turn_end` of it carries no `usage`: a provider that reports usage on the
updates alone loses nothing, and `tool_execution_update`, which no reader in this tree touches, goes either way. The
cost is one `grep` pair per rotation, i.e. once per bound's worth of stream and never per event.

## Where a turn's spend is read from when no update survives

`child_usage_json`'s `turns` kept `.cur` — the last `message_update.usage` before a `turn_end` — and emitted the turn
only if it was not null. One expression, `.cur // $e.message.usage`, makes the turn's own record the fallback. The
alternatives were:

- **Read the `turn_end` first and the update as the fallback.** Refused: `usage` is cumulative within a response, and a
  `turn_end` whose `message.usage` lags its last update (a provider that reports the response's usage before its final
  chunk) would have its turn priced one chunk low. The update is the more precise record wherever it exists.
- **Sum the updates.** Refused by the shipped rule and by measurement: it counts the same tokens once per streamed
  chunk.
- **Record the rotation in the state and let the report say "lower bound" instead of reading the turn.** That is #841's
  half of the finding, and it is only needed for a stream that still overflows after this pass.

The one figure that moves on already-recorded streams is a turn that reported usage only on its `turn_end`: it
contributes `tokens` and `context` from now on instead of vanishing (`items/830` has one such turn, 9 363 tokens). That
is the correction the item is about, and no case asserted the old absence.

## The record, and what it now describes

`{"type":"stream_rotation","dropped_lines":…,"dropped_bytes":…,"dropped_turn_ends":…,"at":…}` keeps its shape and its
readers: `run.sh`'s `child_turns` adds `dropped_turn_ends`, cumulatively, to what the stream still holds. `dropped_lines`
and `dropped_bytes` are what *that* rotation removed — both passes together, and computed from the difference between the
file it was handed and the lines it dropped, so the record never counts itself. `run.sh`'s `child_context` needs no
change: its `total >= done_lines` guard resets the reader when a rotation shrinks the file under it (observed: a reader
at 40 lines, the file cut to 6, the next look re-reads all 6).

## Verification

- `adapter#aRotatedStreamKeepsEveryTurnEnd` — the record lines of the stream the child wrote are compared line-for-line
  with the rotation's result, and a one-rotation prefix makes `dropped_bytes` checkable to the byte.
- `state#theSpendSurvivesTheLossOfItsDeltaLines` — the same stream with and without its updates records the same turns,
  tokens and context, as values and not only as an equality of two nulls.
- `adapter#aStreamStillOverItsBoundAfterTheDeltaPassIsCutAndRecorded` — the fallback still cuts, still records
  cumulatively, and one record stands for every rotation.
- Mutations `267.1` (the delta pass) and `267.2` (the fallback to `message.usage`) each turn their case red and restore
  green.
