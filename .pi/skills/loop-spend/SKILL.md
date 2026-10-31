---
name: loop-spend
description: Measure what the agent loop's children spent and on what — the read-it-again context, the fixed prompt baseline, tool-result traffic, repeated file reads, spend per session role and per day, and what each lever would save (tools/loop-spend.py over .pi/issue-loop/items/*/events.jsonl). Use when an item or a run cost more than it used to, when a change is supposed to reduce the loop's spend, when the report's token column has to be explained, or when someone asks whether the loop is getting more expensive.
---

# What the loop spent, and on what

One command answers it, offline, over the children's own event streams:

```bash
python3 tools/loop-spend.py                  # the tables (about 5 s on the 1.8 GB archive)
python3 tools/loop-spend.py --quiet          # only the LOOP-SPEND-* lines, for a prompt or a script
python3 tools/loop-spend.py --since 2026-10-24 --top 0
python3 tools/loop-spend.py --json           # one object, for a caller that does not parse tables
bash tools/loop-spend-selftest.sh            # 23 cases on a fixture the test writes itself
```

A registry row watches the same number without being asked: `loop-spend` in `tools/sensors/sensors.json`
measures the **duplicate-read share** this report prints, files when it stays past 60 % for two consecutive
days (`guidelines/BacklogTooling.md` §9, issue #690), and records `no-archive` on its own record issue where the
stream is absent — which is every CI run, because `.pi/` is machine-local. So a regression reaches the tracker
by itself; what still needs this tool is the *why*, and every number below.

The data source is `.pi/issue-loop/items/<issue>/events.jsonl` — the child's whole event stream, kept by
`tools/pi-child-events.sh` for exactly this. `tools/loop/report.sh` prints one item's tokens and largest
context and flags the items above the band `LOOP_ITEM_TOKENS_WARN` records (`tools/loop/loop.conf`) — it does not
answer *why*, and this skill is the *why*. The tool's own header (`tools/loop-spend.py`)
carries the model; this file is how to read it and what to do next.

## Read the header block first

- **cacheRead is ~97% of the spend.** Everything else is rounding. So the question is never "how many tokens
  did it write", it is "how much context was alive, for how many turns".
- **The prompt baseline is measured, not guessed**: the first turn of a session is the system prompt plus the
  phase prompt before anything was appended (median **17 260 tokens** over the archive). It is charged once
  per turn, so `turns × baseline` is the floor of any session — roughly a fifth of all tokens.
- **Injected content is re-read.** Report `52.5 M chars injected once → 18.7 M tokens, re-read ×40`. A result
  that lands at turn 5 of 200 is paid for ~195 times; the same result at turn 190 ~10 times. This is why the
  order of work matters as much as its volume: **read late, read small.**
- **Spend by position** splits the sessions that ran a gate at their first gate/suite call. The archive reads
  ~79% of those sessions' tokens *after* it, at ~102 k/turn against ~53 k before — the iterate-on-tests tail is
  the expensive half, and the fix has to land there, not in the planning turns.
- **Reads: duplicate=…** counts file reads that repeated a path the same session already had (45% of all reads
  over the archive, flat across the week — the prose rule in `tools/loop/prompts/worker.md` did not move it).

## Use it as a before/after measurement

A change that claims to reduce spend owes the numbers, not the reasoning:

```bash
python3 tools/loop-spend.py --quiet --since <day before the change>   > /tmp/before.txt
# … the change runs for a few items …
python3 tools/loop-spend.py --quiet --since <day of the change>       > /tmp/after.txt
diff /tmp/before.txt /tmp/after.txt
```

Quote the `LOOP-SPEND-*` lines into the change's evidence (the same discipline `measure.sh` gets: *a number a
change quotes is the output of a command in its record*). Compare **per item**, never totals across days — the
number of items a day carries moves for its own reasons (the archive's items/day fell 32 → 5 while the per-item
cost doubled). The per-day table and the per-item table are the two that hold this.

## The levers, with the savings this archive measured

| lever | measured on the archive | where it lands |
|---|---|---|
| cap a tool result (4 k / 8 k / 16 k chars) | −27% / −14% / −2% | the readers and wrappers a child uses (`ctx_read` mode, `tail -n` for a gate log) |
| fewer turns (−15% / −30%) | −23% / −42% | first-pass accuracy, per-phase shell-call budget, fewer correction rounds |
| smaller prompt baseline (−30%) | −8% | what the child is started with (system prompt, project context, phase prompt) |
| split the worker session at its first gate | the 79%-after-gate tail restarts at 17 k instead of 100 k+ | the loop's phase structure, not a prompt |

Order by effort: capping results and trimming the baseline are prompt/pipeline changes; the session split
changes the loop's contract and owes a case in `tools/loop-selftest.sh` plus the falsification that
`revert-check` demands. A finding that survives here is filed as an item (`create-issue`), with the
`LOOP-SPEND-*` lines as its evidence — the numbers are what make it actionable.

## What the numbers are, and are not

- **Exact**: a turn's `input`, `cacheRead` and `output` — the provider's own report. `cost` is printed only
  when the provider reports one; `-` means unreported, never a measured zero (the whole archive ran on a free
  provider and reads `-`).
- **A model, and labelled so**: the split between tool results, model output and baseline — it attributes the
  context at turn *k* by the ratio of what had been appended by then, at a calibrated 2.8 chars/token. Good
  enough to rank levers, not a ledger: never quote one of those three shares as a token count.
- **Overwritten per item**: an item's stream is the *last* run that worked it, so a re-run replaces the
  earlier spend. `--since` is a date filter on the first turn, not an archive.
- **No compaction today**: zero `auto_compaction` events in the archive, so a session's context grows to
  150–220 k and the tail is where the money is.
- Model latency is nearly flat in context (2.0 s at 0–30 k, 3.3–4.2 s at 60–180 k), so token cuts buy headroom
  and provider budget more than wall time — gate runs, not context, dominate an item's clock.

## Extending it

Keep the shape: a section added to the report is a section whose number a reader will act on, so add the
matching case to `tools/loop-spend-selftest.sh` first (the fixture is two items, a worker session with a
repeated read and a gate call, and a reviewer session — every expected value there is arithmetic). A new
`LOOP-SPEND-<NAME>` line is the contract for anything quoting the tool; a table is for a person.
