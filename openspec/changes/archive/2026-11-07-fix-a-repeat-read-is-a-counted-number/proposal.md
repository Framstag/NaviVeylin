# fix-a-repeat-read-is-a-counted-number

## Why

The worker prompt has stated the rule since #246 — `tools/loop/prompts/worker.md:106`, "**A file you have read once is
cited, not read again** … a second read of the same path is a second model turn and buys nothing" — and #685 closed
only the size half of it (`tools/pi-trim-result.mjs:21` caps what a repeat is handed over). Nothing counts the
repeats: `tools/loop/state.sh` records `metrics.tools` by tool name, `metrics.calls` and `metrics.turns`
(`state.sh:618`), and no field says how many of those calls read a path the item had already read, so the report
prints the same for a child that follows the rule and one that ignores it. The number exists, but only offline:
`tools/loop-spend.py:234-253` derives it per session from the streams, and `guidelines/Backlog.recipes.md:167` quotes
it from there (`LOOP-SPEND-READS calls=4798 duplicate=2174` over the archive) — a figure a reader needs a second tool
and the raw streams to get.

**Root cause, in one sentence:** the item's own record carries no count of the file reads whose path the same session
had already read, so the rule `worker.md:106` states and the seam #685 tightened are printed beside no number a run
can be graded against.

**The red case, on the base `49afe102`** — `tools/loop-selftest.sh --case
'report#anItemRecordsItsRepeatReads'`, 2026-11-07T15:51:44Z, taken with the case as it now stands and
`tools/loop/state.sh` and `tools/loop/report.sh` restored to the base for the run (`git show
49afe102:tools/loop/<file>`; `git diff --stat` empty for both at the time of the run, the change's own versions
copied back immediately after):

```
report#anItemRecordsItsRepeatReads|   the item's file reads are counted from its event stream, read and ctx_read alike: expected '5', got 'null'
FAIL report#anItemRecordsItsRepeatReads
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-HEAD.xml` is that run transcribed into a `testsuite` (`tests=1 failures=1 errors=0`), because the
loop's shell runner writes no JUnit XML; `evidence/red-on-HEAD.log` is the runner's own output.

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md` — added, "A run counts the reads its sessions
repeated". It sits beside "A report reports the shape a run spent its budget in" (`spec.md:3222`, issue #757), the
requirement whose trend line already prints calls per turn and whose state fields the new ones are recorded with.

**Owning guideline section:** `guidelines/Backlog.recipes.md`:254 "What an item costs, and the bounds on it" — the
paragraph `:154`–`:170` owns what a child re-reads of its own work and what bounds it, and quotes the offline count;
the paragraph `:456` owns the shape the spend was made in, which is the trend line the new figure is printed beside.

surface: 4 rule files carry the new figure (`./tools/look.sh 'dupReads' --files` — 16 matches in 6 files: the four
rule files below plus one archived change's `design.md` and this change's own `proposal.md`; `./tools/look.sh
'dupChars' --files` — 9 matches in 4 files, the same four rule files), 1 case added (`tools/loop-selftest.sh --list |
wc -l` → 357, 356 before), 1 case rewritten (`report#wallTimeAndReasonInTheTable`, whose row-shape assertion counts the
table's columns and so owes the appended one), 2 mutation records added (`bash tools/loop-mutations.sh --list | wc -l`
→ 275, 273 before), 0 fixtures realigned, 1 requirement added with 1 scenario, 3 guideline sentences touched.

## What Changes

1. **`tools/loop/state.sh` counts the repeats from the stream `done` already opens.** `child_usage_json` resolves each
   `ctx_read`/`read` call's path — from the `args` of its `tool_execution_start`, or the `arguments` of its `toolCall`
   block — over the whole stream first, then walks the stream for the sessions and for the `tool_execution_end` result
   of each read, and records three figures per item: `metrics.reads` (the read calls), `metrics.dupReads` (those whose
   path the **session** had already read) and `metrics.dupChars` (the characters those repeats re-injected) — `null`
   rather than `0` where the stream measured no read.
2. **`tools/loop/state.sh report` prints the item's figure** in a column appended after the calls-per-turn one, as
   `repeat/total`, and `-` for an item whose stream measured no read.
3. **`tools/loop/report.sh` prints the run's line** — `reads:  3 of 7 file reads were a second read of a path the
   session already had (43 %) · 90 chars re-injected · worst #21 2 of 5` — beside `calls:`, with the two dashes the
   other figures keep: one reason for a state written before the field existed, another for a run of items that opened
   no file.
4. **`tools/loop-cases.sh` asserts it**: `report#anItemRecordsItsRepeatReads` writes one item's stream with three
   reads of one path and one of another (plus a second session that re-reads the first session's path, which is a read
   and not a repeat) and asserts the recorded counts, the row's cell and the trend's line, and both dashes.
5. **`tools/loop-mutations.sh` records `96.1` and `96.2`** — the counting branch dropped, and the characters the line
   prints renamed — so the case that names the invariant is red on the same stream.
6. **`guidelines/Backlog.recipes.md`:254** states the rule and stops calling the row's calls-per-turn column its last
   one.

## What this does not do

- It does not change the prompts. `worker.md:106` already states the rule; the change gives it a number to be graded
  against, and no sentence is added to a prompt that would be a second place for the rule to live.
- It does not add a floor, a band or any key to `tools/loop/loop.conf`: the issue asks for the figure, not for a
  comparison, and a bound invented here would be a number nobody measured. Nothing in the run reads the figure.
- It does not re-count a paged read as a repeat of its own path — the count is per path, the definition
  `tools/loop-spend.py` uses and the one the item's own evidence was taken with, so the state's number and the offline
  reader's are the same number. See `design.md`, "Alternatives eliminated".
- It does not touch `tools/loop-spend.py`: the offline reader stays the tool for the archive, and the state's figure is
  the per-item one a report can print without it.
