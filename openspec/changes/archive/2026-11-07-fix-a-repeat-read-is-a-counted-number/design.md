# Design — a repeat read is a counted number

## Root cause

The event stream an item's children append to carries everything needed to count a repeat read — the `toolCall`
block's `arguments.path` in each `turn_end`, and the `tool_execution_end` result of the same call id — and
`tools/loop-spend.py:234-253` already derives the figure from it. The engine never read those two records together:
`child_usage_json` (`tools/loop/state.sh:755`) counts `tool_execution_end` lines by name for `metrics.tools`, counts
them for `metrics.calls`, and counts `turn_end` lines for `metrics.turns`; the paths a call named, and the characters
a repeat result re-injected, were dropped. So the record an item leaves behind cannot say whether the child obeyed
`tools/loop/prompts/worker.md:106`, and no report line is graded by #246's rule.

## The single fix

`child_usage_json` gains one path map over `$all` — every read call's id to the path its `tool_execution_start`
`args` (or, for a harness that reports the call only with its turn, its `toolCall` block's `arguments`) names — and then
one walk that keeps, per event, the session it belongs to (`session` records open a new one) and, for each
`tool_execution_end` of a read tool whose call named a path, the characters of that result. The map is built first
because a real stream closes the call before it closes the turn (`tool_execution_end` precedes the `turn_end` that
names it). From that list, a small reduce counts, per `(session, path)` pair, how many times
it was seen: the first read is the base, every later one is a repeat, and its characters are added to `dupChars`.
Three figures are recorded in `metrics`: `reads` (the list's length), `dupReads` (the repeats), `dupChars` (their
characters). All three are null, not zero, when the list is empty.

`tools/loop/state.sh report` prints `dupReads/reads` in a column appended after the calls-per-turn one, and
`tools/loop/report.sh` prints the run's line beside `calls:`: the total repeats out of the total file reads, the
percentage, the characters re-injected, and the item that repeated most. Both dashes the other spend figures keep are
there — one for a state whose `metrics` carries no `dupReads` key at all (written before the field), one for a state of
this era whose items opened no file.

Nothing else changes: no key in `tools/loop/loop.conf`, no prompt, no child, no new file. The figure is recorded where
the money is spent (the stream `done` already opens) and printed from the state every reader already holds, so a report
never has to find an item's stream again — the rule `state.sh:713`–`:754` states for every other figure.

## Why per session, and why per path

- **Per session**, because the loop starts a fresh child with a fresh context for each phase: the reviewer that reads
  the file the worker read is not paying for a fact it holds, and an item-level total would report the reviewer's first
  read as the worker's third — inflating exactly the number the change exists to make trustworthy. This is the
  definition `tools/loop-spend.py:251` uses ("a second read of a path the session already had") and the sentence
  `tools/loop-spend.py:329` already prints.
- **Per path**, because that is the rule the prompt states ("a second read of the same path") and the definition the
  item's own evidence was taken with: #798's 49 of 79 and #791's 30 of 55 are `tools/loop-spend.py`'s path-level
  count over `.pi/issue-loop/items/*/events.jsonl`. A narrower definition (path *and* the requested range) would make
  the state's number and the offline reader's disagree, and the paged read the prompt itself asks for (#818) would
  silently vanish from the figure rather than appear in it as what it costs.

## Blast radius

- `tools/loop/state.sh` — one pass and three fields in `child_usage_json` (plus its fallback blob), three fields in the
  `metrics` object `done` writes, one column and its header in `report`. Every existing column keeps its index.
- `tools/loop/report.sh` — one derived line in the trend block, appended after `calls:`.
- `tools/loop-cases.sh` — one case (`report#anItemRecordsItsRepeatReads`), registered in `LOOP_SHELL_CASES` and the
  dispatch; it writes its three fixtures inline, as the neighbouring spend cases do.
- `tools/loop-mutations.sh` — records `96.1` and `96.2`.
- `guidelines/Backlog.recipes.md`:254 — the paragraph that owns what a child re-reads, the sentence in
  `guidelines/Backlog.recipes.md`:892 that called the calls-per-turn column the row's last one, and the trend sentence
  beside it.
- No compiled file, so the change owes no build: the loop's own shell suite is its whole gate.

## Numbers this change quotes

- `LOOP-MEASURE-SUITE cases=356 cmd=tools/loop-selftest.sh --list` — the measured block before this change; after it,
  `tools/loop-selftest.sh --list | wc -l` → `357`.
- `LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has
  not run` — quoted from the same block; this item's gate is the run's gate child's, so no gate figure is claimed here.
- `bash tools/loop-mutations.sh --list | wc -l` → `275` (273 before the two records).
- The archive's repeat-read figure (`LOOP-SPEND-READS calls=4798 duplicate=2174`, 45 %) is quoted from
  `guidelines/Backlog.recipes.md:167`, where it already stands; this change takes no new measurement over the archive.

## Rollback

Delete the pass and the three fields in `child_usage_json`, the three `metrics` fields, the `dup reads` column and the
`reads:` line; drop case `report#anItemRecordsItsRepeatReads` and mutation records `96.1`/`96.2`. Nothing reads the
figure outside the report and the case, so a state written by the reverted engine is one the reverted report prints `-`
for — the dash a pre-field record already gets.

## Alternatives eliminated

- **Reusing `tools/loop-spend.py` from the report.** The report may read the state and git and nothing else
  (`report.sh:240`); a report that re-read 1.8 GB of streams to print one line would be a report nobody runs twice, and
  it would disagree with the item's own record the first time a stream was rotated away.
- **Counting the repeats per item rather than per session.** Rejected above: it prices the reviewer's first read as the
  worker's repeat, which is the one thing the figure must not do.
- **Counting a ranged read as a fresh read** (path + requested range as the key). Rejected above: it disagrees with the
  offline reader the item's evidence came from, and it hides the paged read the prompt asks for.
- **A recorded floor, the way `LOOP_ITEM_CALLS_PER_TURN_MIN` bounds calls per turn.** Not in the issue and not measured
  here: the archive's repeat rate is quoted from a single line taken before #685 tightened the seam, so a floor would be
  a bound standing on one stale number. The figure is printed; a bound is a later item's measurement.
- **Changing the prompt.** `worker.md:106` already states the rule, and a second sentence would be a second place for
  it to drift from the first.
