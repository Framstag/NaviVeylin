# Design — one bound at the writer, one reading at the count, one arm at the report

## Root cause

`tools/pi-child-events.sh` writes the child's event stream with one append and no size reading
(`tools/pi-child-events.sh:117` on the base), and nothing else in the path bounds the kept line: the result bound
belongs to the child's context, not to the file. The same file is then re-read whole every second by the turn bound
(`run.sh`'s `child_turns`, `tools/loop/run.sh:1234-1240`) and once at the item's end by `state.sh`'s
`child_usage_json`, so its growth is paid twice — in disk and in the loop's own reads.

The leftover report (`tools/loop/loop-clean.sh`) names the kept worktree's build paths, the scratch trees of two
roots, the branches whose patches landed, the worktrees no run keeps and the change sources a landed item consumed.
The item directories are none of them.

## The single fix

**Rotate where the stream is written, count what the rotation records, and report the directories by the two
readings that already exist for a leftover.**

1. **The writer.** `tools/pi-child-events.sh` reads `LOOP_CHILD_EVENTS_MAX_BYTES` (environment first, then
   `tools/loop/loop.conf`, the shape `LOOP_TOOL_RESULT_MAX_CHARS` already has). It keeps a running byte count in the
   reader loop — no `wc` per event — seeded once from the size the file already has, because the file outlives every
   child that appends to it: a stream already past its bound is then rotated by the first event the child adds, not
   only once that child has added a bound's worth of its own. When the count passes the bound it calls
   `rotate_events`, which:

   - keeps the newest half of the lines (`lines / 2`), so the newest event always survives even when one line is
     larger than the bound;
   - writes `{"type":"stream_rotation","dropped_lines":…,"dropped_bytes":…,"dropped_turn_ends":…,"at":…}` as the
     first line, so a reader can tell a rotated stream from a short one;
   - carries the previous record's `dropped_turn_ends` forward, because that record is dropped with the events it
     counted — the count in the file is cumulative and never falls.

   A record line is not a `turn_end`, so `jq -s` over the stream, the usage sums and the adapter's own message scan
   are unchanged. `0`, or a project with no key, keeps every line.

2. **The count.** `run.sh`'s `child_turns` adds the sum of the records' `dropped_turn_ends` to the `turn_end` lines
   still present. The baseline `turns_base` is taken with the same function, so the difference a child is bounded by
   is right on both sides of a rotation. This is the one place the bound would otherwise reset: a rotated stream
   would read as zero turns taken and the child would run on.

3. **The arm.** `loop-clean.sh` gains one arm after the consumed-change arm, over
   `dirname(LOOP_STATE_FILE)/items` (so an overridden state path moves it with the state). A directory is released
   only when **both** readings agree:

   - **age** — `find <dir> -mmin -<days*1440>` finds nothing, so nothing inside it has been written for the
     retention; and
   - **the state** — `.items[]` holds a record for the directory's numeric name with `.result == "done"` and
     `.metrics.closed == "yes"`, i.e. the run landed it *and* the tracker closure happened. No record, another
     result, or a landing the run was not allowed to close means the issue is still open and still cites the
     directory, and it is kept and named with that reason.

   `--apply` removes exactly the directories the report named, a state file that cannot be read keeps every one, and
   a `0` retention turns the arm off. It is the last arm, so the summary's path/byte count includes it.

## Blast radius

- `tools/pi-child-events.sh`: the bound reader (10 lines), `rotate_events` (29 lines with its comment) and the
  counter in the reader loop (7 lines).
- `tools/loop/run.sh`: `child_turns` (5 lines added).
- `tools/loop/loop-clean.sh`: one arm (52 lines with its comment), before the summary.
- `tools/loop/loop.conf`: two recorded keys with their measurements.
- `tools/loop-cases.sh`: 3 cases, their list entries, their dispatch entries and one declaration (the `clean#` case
  reaches `tools/pi-child-events.sh` through `new_project`).
- `tools/loop-mutations.sh`: records `89.1`–`89.4`.
- `guidelines/Backlog.recipes.md`:212 "What an item costs, and the bounds on it" and `:764` "Leftovers, the report
  and the queue": the turn-bound paragraph and the leftover section.
- `openspec/`: this change and its delta on `agent-issue-loop`.
- No state field, no new command, no label, no remote write. `--apply` is still the only path that removes anything.

## Alternatives the evidence eliminated

- **Rotate in `run.sh` between children, not in the adapter.** The stream is written by the adapter for the whole
  life of a child, and the largest file in the archive (63.47 MiB) was written inside children, not between them; a
  run-level rotation would not bound a single child's file. It is also the wrong owner: `run.sh` would have to read
  and rewrite a file another process is appending to.
- **Keep a separate `events.jsonl.1`.** The item's directory is what a reader greps for turns and sums for spend, and
  a second file is a second thing every reader must learn to concatenate. The record line keeps one file and one
  reading.
- **Cap with a hard truncate of the oldest bytes.** A byte cut splits a line, so the first record after it is invalid
  JSON and `jq -s` fails on the whole stream: the count would go to zero, which is the failure this change exists to
  prevent.
- **Delete the item directories by age alone.** That is the loop's evidence, and an item whose issue is still open is
  exactly the one whose evidence is cited. The state's `.result`/`.metrics.closed` is the offline reading of "the
  tracker closed it" the run already records; asking the tracker would make an offline cleanup command depend on a
  network and a token.
- **Read a tail of the stream in `child_turns`** rather than bounding the file. A tail is not a count: the turn bound
  needs the whole count and the spend sum needs every usage record, and a tail makes both silently smaller. Bounding
  the writer and carrying the turn count in the record keeps the count exact and the file small.

## Open decisions

none

The item's own text names the key (`LOOP_ITEM_ARCHIVE_DAYS`), the bound and the two readings, and the two retention
figures are the archive's own (14 days releases 105 directories and 1 411 MiB; 30 released none).

## Rollback

Delete the two keys from `tools/loop/loop.conf` (a ported project records neither): the adapter then keeps every
line, `child_turns` counts the lines present (a stream with no record is read exactly as before), and the clean arm
reports nothing. Revert the three files and the change is gone; no state was added, so no migration is owed.
