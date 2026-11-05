# Design — fix-a-child-is-bounded-by-its-context

## Root cause

`tools/loop/run.sh:164` makes the turn count the item's only spend bound, and a turn is a unit of *shape*, not of
*cost*: `run.sh:1301-1310` ends the child that has taken `LOOP_ITEM_TURNS_MAX` turns, whatever each of them cost. The
number that says what a turn cost already exists — `tools/loop/state.sh:658` (`child_usage_json`) takes the largest
`input + cacheRead` a turn reported — but only `tools/loop/report.sh` reads it, after the item is over. So the run
cannot see a session whose context has grown to where every further turn costs several times its first, and such a
session is paid for until the turn bound happens to fire.

## The single fix

Read the same reading the report takes, in the wait that already reads the turn count, and give it the two decisions
the turn bound has:

1. **`child_context()`** beside `child_turns()` (`run.sh:1114`). It answers the largest context in the stream, from
   the bytes appended since the last look, so the wait pays one `wc -l` and one `jq` over the *new* lines per whole
   second rather than a re-parse of the whole stream. The records it reads are the two the adapter writes: a
   `message_update`'s `usage` (cumulative within a response, so the peak is the greatest `input + cacheRead`) and a
   `compaction_end`'s `result.tokensBefore` (the size the window had to hold). A stream that is absent, empty or
   unreadable is no reading rather than an error, exactly as `child_turns` treats one: a child whose command writes
   no events stays bounded by its clock and its turns.
2. **Two decisions in `run_child`'s wait**, in the same whole-second block as the deadline, the cap and the turn
   count: one `LOOP-CONTEXT:` checkpoint line on the run's own output when the running maximum crosses
   `LOOP_ITEM_CONTEXT_WARN`, and `ended_by=context` — the same `TERM`, one-second grace, `KILL`, sweep and
   `keep_leftovers` path the turn bound takes — when it crosses `LOOP_ITEM_CONTEXT_MAX`. The end writes
   `LOOP-TIMEOUT: the child reached the recorded context bound of <max> tokens (LOOP_ITEM_CONTEXT_MAX) at
   <reading> tokens — the run ended it and everything it started`, so the transcript, the item's `blocked` note and
   the report's cut-short reading all carry the number the child was ended at. Every existing consumer of
   `LOOP-TIMEOUT` (reviewer rounds read as `cut-short`, the pre-gate round left for the next run, `end-item`
   skipped) applies unchanged, because the line's shape is the turn bound's.

The two values are the project's recorded numbers, not the engine's constants: `item_context_warn` and
`item_context_max` are validated at the top of the run like `item_turns_max`, named in the run's header beside it,
and default to `0` — no bound — so a project that records neither owes nothing by them (D16).

## Why the numbers are what they are

The archive's per-item largest context over 300 recorded items (`.pi/issue-loop/run.json` and `runs/*.json`,
2026-11-05) is p50 107 669, p75 143 124, p90 174 984, p95 191 815, maximum 244 764.

- **`LOOP_ITEM_CONTEXT_WARN=143124`** — the archive's p75, recorded exactly as measured, the way the token band
  records its own p75. It is a line and never a stop, so its cost is one line on about a quarter of the items.
- **`LOOP_ITEM_CONTEXT_MAX=200000`** — above the p95 (191 815), where a turn costs 1.86× the archive's median
  largest context (200 000 / 107 669), which is the "twice as much" the item is about, and 10 of the 300 recorded
  items (3.3 %) reached it — a bound that ends the tail without ending the p90.

## Blast radius

- `tools/loop/run.sh` — one function added, one read and two decisions added to `run_child`'s wait, two variables and
  their validation, one header field pair, one branch in the `LOOP-TIMEOUT` chain.
- `tools/loop/loop.conf` — two recorded keys with their derivation.
- `tools/loop-cases.sh` — two cases (both listed in `LOOP_SHELL_CASES` and the dispatcher) and no other case changed:
  the turn-bound pair asserts only what the turn bound does and both keep passing.
- `guidelines/Backlog.recipes.md`, section 7b, `guidelines/BacklogTooling.md`, section 11 — prose and the key table.
- No compiled file: no Kotlin, Java, C++, Gradle logic, manifest or resource, so no build is owed.

A child the context bound ends is a child the run already ends today by its clock or its turns, and the item is
recorded `blocked` with the transcript — the same landing, one more reason. A project that records `0` behaves
exactly as it did before.

## Rollback

Revert this change's commit. The function, the two keys, the two cases, both guideline paragraphs and the spec delta
go together; nothing here writes to an item's recorded metrics, so the archive's contexts are what they were.

## Alternatives the evidence eliminated

- **Bound the item's total tokens instead of its largest context.** The archived change that recorded
  `LOOP_ITEM_TOKENS_WARN` measured the item's total as a *post-hoc* number (`report.sh:140`, read from the state
  after the item closed), and a total is only known once it is spent: it cannot end a child that is still running
  without being recomputed from the same stream, and once recomputed, the reading it comes from is the per-turn
  context — the same number with an extra sum over a session that is still growing. The item's own red case names
  `turn_end` events reporting a growing context, so the per-turn reading is the one the change is answerable to.
- **Stop at the warn line.** A stop at the p75 would end a quarter of the archive's items mid-flight, and the work a
  stopped child leaves is a `blocked` item and a leftover patch. The checkpoint is free and the session can still
  finish; only past the p95 does the run decide, and the item's own text asks for exactly that split
  ("a checkpoint line at `LOOP_ITEM_CONTEXT_WARN`, and a stop at `LOOP_ITEM_CONTEXT_MAX`").
- **Split the session at the checkpoint.** The cheaper half of the same idea, and it is not this change: a split means
  a fresh child handed the change and the evidence, which is `tools/loop/prompts/worker-tail.md`'s shape and needs a
  red case of its own that asserts what travels. The checkpoint line this change prints is the signal such a split
  would be triggered from, so the second change is cheaper after this one, not instead of it.
- **Read the whole stream once per second.** Rejected on cost, not correctness: the stream grows with the child's
  run and the wait looks once a second, so a full re-parse through `jq` of a 45-minute child's stream spends what
  the bound saves on tokens. The incremental read over complete lines (`wc -l` for the boundary, `tail | head` for
  the new lines) cannot consume a half-written line, which a byte offset can.
