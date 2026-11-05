# Proposal — fix-a-child-is-bounded-by-its-context

## Why

**Observed, verified in the tree today.** `tools/loop/run.sh:164` reads `LOOP_ITEM_TURNS_MAX` (recorded at 250 in
`tools/loop/loop.conf:109`) and `run.sh:1301-1310` ends the child that reaches it; that is the item's only bound on
what a child *spends*. A turn is not a unit of cost: every turn re-reads the whole conversation, so the same turn
count costs the archive's median largest context or its maximum (107 669 against 244 764 tokens) depending on how
far the context has grown. The number that varies is already computed —
`tools/loop/state.sh:658`'s `child_usage_json` takes the largest `input + cacheRead` any turn reported — but it is
read by the report *after* the item is over (`LOOP_ITEM_TOKENS_WARN`, `tools/loop/report.sh:140`), and nothing in
the run reads it while the child is running. `grep -n 'LOOP_ITEM_CONTEXT\|item_context' tools/loop/run.sh` prints
nothing on the base commit `122cc2f9`: no live context reading, no context bound, no checkpoint.

**Measured (2026-11-05, this tree).** The largest context per item over the 300 item records of
`.pi/issue-loop/run.json` and `.pi/issue-loop/runs/*.json`:

```
$ { jq -r '.items[]?.metrics.context // empty' .pi/issue-loop/run.json;
    for f in .pi/issue-loop/runs/*.json; do jq -r '.items[]?.metrics.context // empty' "$f"; done; } |
  sort -n | awk '{a[NR]=$1} END {n=NR; printf "n=%d min=%d p50=%d p75=%d p90=%d p95=%d max=%d\n", n, a[1], a[int(n*0.5)], a[int(n*0.75)], a[int(n*0.9)], a[int(n*0.95)], a[n]}'
n=300 min=22710 p50=107669 p75=143124 p90=174984 p95=191815 max=244764
```

A child at 200 000 tokens of context pays 1.86× the archive's median largest context (107 669) for every turn it
still takes, and 10 of those 300 items (3.3 %) recorded a context above it (`evidence/context-distribution.txt`
lists the ten).

**Root cause, in one sentence.** The item's only bound that ends a child is priced in turns, which are not the unit
the spend varies in, and the reading that does vary — the largest context a turn reported — is taken only after the
item is over, so a session whose every further turn costs several times its first is paid for until the turn bound
happens to fire.

**The red test.** Two cases in `tools/loop-cases.sh`, both against the real engine with the stub child, run on the
base commit `122cc2f9` with nothing else changed (2026-11-05T11:52:51Z and 2026-11-05T11:53:03Z):

```
$ ./tools/loop-selftest.sh --case 'run#theRecordedZeroKeepsTheContextBoundOff'
run#theRecordedZeroKeepsTheContextBoundOff|   the run's header says the context bound is off: 'item-context-max=0' not found in: agent-issue-loop: items=1 minutes=90 review=off pre-gate-review=off may-close=yes push=no max-decisions=10 max-review-rounds=2 review-read-only=on strict-outside-write=no child-max-seconds=2 item-turns-max=250 min-free-mib=1024 free-mib=4904 dry-run=0 reset-worktree=0 agent-roles=-
FAIL run#theRecordedZeroKeepsTheContextBoundOff
loop-selftest: 1 of 1 cases failed
```

```
$ ./tools/loop-selftest.sh --case 'run#childPastItsContextBoundIsBlocked'
run#childPastItsContextBoundIsBlocked|   the run was still waiting after 60 s on a child whose context had grown to 210 000 tokens past a recorded bound of 200 000
FAIL run#childPastItsContextBoundIsBlocked
loop-selftest: 1 of 1 cases failed
```

Each selection closed `loop-selftest: 1 of 1 cases failed`, so the pair is 2 cases and 2 failures over the two run
summaries (`evidence/red-summary.txt` derives the roll-up); both transcripts are the one file
`evidence/red-on-head.log` beside this change. The first case is red because the run prints no context bound at
all; the second is red because the run waits on a child whose stream says 210 000 tokens while the profile records a
bound of 200 000 — the defect itself, not a missing line.

**The owning capability and the owning guideline section.** Capability `agent-issue-loop` — the requirement this
change adds is "The bound on a child is priced in what it spends, not in how many turns it takes"; the section is
`guidelines/Backlog.recipes.md`, section 7b "What an item costs, and the bounds on it", whose turn-bound paragraph
is the one this sits beside, and the key table in `guidelines/BacklogTooling.md`, section 11 "Where an agent runs".

surface: 2 cases added, 0 rewritten, 0 fixtures realigned, 1 engine function added and 1 read added to `run_child`'s
wait, 2 keys recorded in `tools/loop/loop.conf`, 2 guideline sections extended, 1 requirement added with 2 scenarios
— `grep -rln 'LOOP_ITEM_TURNS_MAX' tools/loop guidelines` names the six files the rule lives in today
(`tools/loop/run.sh`, `tools/loop/loop.conf`, `tools/loop-cases.sh`, `tools/loop/prompts/worker.md`,
`guidelines/Backlog.recipes.md`, `guidelines/BacklogTooling.md`), and this change touches five of them — the
worker's prompt keeps its sentence, which is about batching and not about what ends a child. Counts from that
`grep -rln` and `bash tools/loop-selftest.sh --list`.

## What Changes

- **`tools/loop/run.sh`** — `child_context` beside `child_turns`: the largest context the child's own stream
  reported, read from the bytes appended since the last look so the wait never re-parses the stream, and read in the
  same whole second as the two clocks and the turn count. `run_child` keeps the running maximum and takes two
  decisions from it: one `LOOP-CONTEXT:` checkpoint line on the run's own output when it crosses
  `LOOP_ITEM_CONTEXT_WARN`, and an end — `ended_by=context`, `LOOP-TIMEOUT: the child reached the recorded context
  bound of <n> tokens (LOOP_ITEM_CONTEXT_MAX) at <m> tokens …` — when it crosses `LOOP_ITEM_CONTEXT_MAX`. Both keys
  are read once at the top of the run like `LOOP_ITEM_TURNS_MAX`, validated the same way, and named in the run's
  header (`item-context-warn=`, `item-context-max=`). `0`, or no key at all, is no bound.
- **`tools/loop/loop.conf`** — the two recorded numbers with the measurement that derived them:
  `LOOP_ITEM_CONTEXT_WARN=143124` (the archive's p75) and `LOOP_ITEM_CONTEXT_MAX=200000` (above its p95 of 191 815,
  where a turn costs 1.86× the archive's median largest context).
- **`tools/loop-cases.sh`** — the two cases above, registered in `LOOP_SHELL_CASES` and the dispatcher.
- **`guidelines/Backlog.recipes.md`**, section 7b — the paragraph beside the turn bound: the unit, the measurement, the two
  recorded numbers, the checkpoint and the stop, the `0` switch, and what it does not replace (splitting the session
  at its gate is the cheaper half).
- **`guidelines/BacklogTooling.md`**, section 11 — the two keys in the profile table.
- **`agent-issue-loop`** — one requirement ADDED, two scenarios, each naming one case.

## What This Does Not Do

- It does not end the item when the *item's* total tokens pass a bound: that is `LOOP_ITEM_TOKENS_WARN`'s number,
  it is read after the item is over, and the item asks for a bound on a child that is still running.
- It does not split the session at the checkpoint. The checkpoint line names the reading; moving the change into a
  fresh child at it is the cheaper half of the same idea and it is a second change with its own red case.
- It does not reverse the turn bound. The two bounds sit beside each other and either one ends a child; the turn
  count is still what ends a session that keeps batching badly at a small context.
- It changes no compiled file: no Kotlin, Java, C++, Gradle logic, manifest or resource — so no build is owed
  (`guidelines/Build.md`, section 2, scope block). Verification is the loop's offline suite and the structural checks the
  change's own pathspec owes.
- Rollback path: revert this change's commit — the function, the two keys, the two cases, both guideline paragraphs
  and the spec delta go together; the archive's recorded contexts stay what they are, because nothing here writes to
  an item's metrics.
