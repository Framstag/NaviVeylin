# Proposal — a truncated spend says so

## Why

**Root cause.** A rotated stream's cost record is a lower bound and nothing says so. `tools/pi-child-events.sh`'s
`rotate_events` writes a `stream_rotation` line carrying `dropped_turn_ends` and `dropped_bytes` as the stream's first
line, and exactly one reader adds the figure back: `child_turns` in `tools/loop/run.sh`, which is what keeps a child's
turn **bound** counting after a rotation. The cost reader does not — `tools/loop/state.sh`'s `child_usage_json` sums the
`turn_end` durations and the session usage of what survives — so on a rotated item `metrics.turns`, `metrics.tokens`,
`metrics.phases`, `metrics.roles` and `metrics.afterGate` are short by whatever was dropped, and `tools/loop/report.sh`
prints them as measurements taken.

**Evidence, measured on the run started 2026-11-09T21:42:41Z** (`.pi/issue-loop/run.json`, read-only): nine of its
fifteen item streams carry a `stream_rotation` record — `dropped_turn_ends` 94 to 333, **1 697 in total** against the
1 372 turns the state recorded — and 72 255 266 B in the records' `dropped_bytes`. The report's `phases:` line prints
`3.4 h over 15 items` against `[.items[].metrics.wallSeconds] | add` = 23 498 s of item walls (52 %), and `roles:`
prints `92.5 Mtok over 15 items` against 109 847 773 tokens summed from the same fifteen; neither line says it is a
lower bound, and the two figures a reader would use to notice the gap sit on the same two lines.

**The fix, and what it is not.** #840 removed the cause for a stream the delta pass can bring under its bound. What
survives is a stream whose record lines alone outgrow the bound, and what this change adds is the record of it: the
item's own metrics carry the rotation's figures, and the report's trend names the items whose figures are lower bounds
with the figure each dropped. No figure that exists today changes its computation — this is one recorded field and one
printed line.

**The red test on the base** (`/tmp/red-841.log`): `report#aTruncatedStreamSaysSo` fails on the figure the state does not carry
— *the turns a rotation dropped are recorded beside the spend they are missing from: expected '277', got 'null'* — and on
the line that would print it: *the trend printed no line saying a figure is a lower bound (issue #841)*, which is the
case's own message for its `FAIL` when the trend carries no `truncated:` label.

## Blast radius

- **`tools/loop/state.sh`** — `child_usage_json` reads the stream's `stream_rotation` record into one more figure,
  `streamRotation: {droppedTurnEnds, droppedBytes}`, `null` where the stream holds no record, and `done` records it in
  the item's metrics beside `dupReads`. The function's own header comment gains the rule.
- **`tools/loop/report.sh`** — one `truncated:` line in the trend block, printed after `after the first gate:`. It
  reads nothing but the new field and computes no existing line. The line's two figures are read from two selections and
  say so: the turns and the named items are the items that lost a turn, the bytes are summed over **every** item whose
  stream carries a rotation record, so a rotation that removed only deltas is counted in the bytes and names no item.
  Its `--help` range is the header's own end (`sed -n '2,/^set -uo pipefail/p'`) rather than a line number, so the
  header cannot be printed short again.
- **`tools/loop/tests/loop-cases.sh`** — one case, `report#aTruncatedStreamSaysSo`, plus its registration and dispatch.
- **`tools/loop/tests/loop-mutations.sh`** — one record, `268.1`.
- **`guidelines/Backlog.recipes.md`** §7e — the paragraph that states what the trend prints.
- **Not touched:** `tools/pi-child-events.sh` (the rotation itself, #840's), `run.sh`, `tools/loop/loop.conf`, the
  table's columns, every existing trend line, `.pi/**`, `openspec/specs/**`.

surface: 1 case extended (0 added, 1 rewritten), 2 code sites (one function's return object, one trend line and the
`--help` range), 1 mutation record, 1 requirement added in this change's delta (`specs/agent-issue-loop/spec.md`, whose
archive carries it into the capability; `openspec/specs/**` is untouched while the change is open) —
`grep -c 'stream_rotation' tools/loop/state.sh` → 3 (the `metrics` key's comment, the reader's comment and the
`select`), `grep -c 'truncated' tools/loop/report.sh` → 3 (the comment, the label and the JSON key it prints),
`grep -c '268\.' tools/loop/tests/loop-mutations.sh` → 2 (one comment line, one record), the case name is declared
in `LOOP_SHELL_CASES`, dispatched in the table and defined, 1 line in each
