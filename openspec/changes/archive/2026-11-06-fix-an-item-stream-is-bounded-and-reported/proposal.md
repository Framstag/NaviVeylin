# The item's kept event stream is bounded, and what it leaves is reported

## Why

The adapter appends every line of every child's event stream to one file per item and never bounds it. On the base
`007ac561`, `tools/pi-child-events.sh:117` is the only line that writes it:

```sh
      [ -z "$events" ] || printf '%s\n' "$line" >> "$events"
```

and the only bound anywhere in that path is `LOOP_TOOL_RESULT_MAX_CHARS`
(`tools/pi-child-events.sh:79`), which bounds a *result the child sees*, not the line that is kept. So the file grows
for the item's whole life and across attempts, and it is the largest thing the loop keeps.

**Root cause, in one sentence:** the writer that keeps the child's stream appends without a bound, and the command
that answers what the loop left has no arm for the item directories — so the largest artefact the loop produces is
both unbounded and unreported.

**Measured on this archive, 2026-11-06** (`evidence/archive-census.py`, output kept as `evidence/archive-census.log`):

```
items=264 events-files=254 total=3483MiB p50=11.84MiB p90=29.88MiB max=63.47MiB
bound=4MiB kept-sum=891MiB
bound=8MiB kept-sum=1584MiB
bound=16MiB kept-sum=2573MiB
days=7 dirs=215 MiB=2757
days=14 dirs=105 MiB=1411
days=30 dirs=0 MiB=0
all item-dirs MiB=3563
```

`du -sh .pi/issue-loop` answers `3,6G`, and `items/242/events.jsonl` alone is 65 MB — the item's own line, re-taken
here rather than quoted.

**The reading half is the same decision one level down.** `run.sh`'s `child_turns`
(`tools/loop/run.sh:1234-1240` on the base) counts `turn_end` lines with one `grep` per second over that whole file,
and `state.sh`'s `child_usage_json` sums it whole; a file that has already run twice is paid for again by every later
turn count.

**The red cases, on the base `007ac561`** — the engine change the only thing absent, the cases added:

- `clean#reportsAnAgedItemDirectory` — `FAIL clean#reportsAnAgedItemDirectory`, `1 of 1 cases failed`
  (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD-clean.log`, XML `evidence/red-on-HEAD.xml`).
- `adapter#theKeptStreamIsBoundedAndItsRotationIsRecorded` — `1 of 1 cases failed`, its first assertion
  `the stream's first line records the rotation: expected 'stream_rotation', got 'turn_end'`.
- `run#aRotatedStreamStillCountsTheTurnsItDropped` — `1 of 1 cases failed` after the case's 60 s wait:
  `the run was still waiting on a child whose stream records 2 dropped turns and holds 1, past a bound of 3`.

The XML carries `tests=3 failures=3`, taken 2026-11-06T18:48:07Z–18:49:24Z (the shell runner writes no JUnit XML, so
the file is the carrier of the tally beside the logs, as the archived `2026-11-06-fix-the-leftovers-nothing-reads`
did).

**Owning capability and guideline.** The spec is `agent-issue-loop`; the guideline section is
`guidelines/Backlog.recipes.md`:212 "What an item costs, and the bounds on it" (the turn bound reads the stream) and
`:764` "Leftovers, the report and the queue" (what `loop-clean` reports).

surface: 3 cases added, 0 rewritten, 4 mutation records added, 2 guideline sections amended, 11 live files named by the rule — `tools/look.sh 'events\.jsonl|LOOP_CHILD_EVENTS|child_turns|LOOP_ITEM_ARCHIVE_DAYS|item directories'` → **153 matches in 49 files**, the live ones `tools/loop/run.sh` 13, `tools/loop-cases.sh` 46, `tools/pi-child-events.sh` 8, `tools/loop/loop-clean.sh` 7, `tools/loop/loop.conf` 7, `tools/loop/measure.sh`, `tools/loop/phase.sh`, `tools/sensor-cases.sh` 4, `tools/loop-spend.py` 2, `tools/loop-spend-selftest.sh` 3 and `tools/loop-mutations.sh`, the rest in `openspec/changes/archive/`. The plan was fixed after this pass: the turn-bound paragraph that says the wait costs "one `grep` per second" and the leftover section that lists what the report answers for are the two guideline sites.

## What changes

- `tools/pi-child-events.sh` bounds what it keeps: past `LOOP_CHILD_EVENTS_MAX_BYTES` it drops the oldest half of the
  lines and writes a `stream_rotation` record first, carrying `dropped_lines`, `dropped_bytes` and a cumulative
  `dropped_turn_ends`.
- `tools/loop/run.sh`'s `child_turns` adds the record's count to the `turn_end` lines still present, so a rotated
  stream cannot reset a child's turn bound.
- `tools/loop/loop-clean.sh` gains one arm for the item directories: a directory is named with its size, and removed
  only with `--apply`, when nothing inside it has been written for `LOOP_ITEM_ARCHIVE_DAYS` **and** the run recorded
  the item `done` with its closure; anything else is kept and named with the reason.
