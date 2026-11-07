# Proposal — fix-decision-cap-counts-candidates

## Why

**The condition, verified in the tree at base `71f7eafb` (2026-11-02).**
`tools/loop/run.sh:698` counts the items that carry the decision label with
`tools/issues/list.sh --json --limit 400` — the whole tracker, *without* `--eligible-only` — and compares that
number with `--max-decisions` at `:700`. So the cap counts every open item carrying the label, whether or not the
item is one the loop may work:

```
$ ./tools/issues/list.sh --json --limit 400 | jq '[.[] | select([.labels[].name]|index("agent/needs-decision"))] | length'
13
$ ./tools/issues/list.sh --json --limit 400 --eligible-only | jq '[.[] | select([.labels[].name]|index("agent/needs-decision"))] | length'
9
```

**Root cause, one sentence.** The ceiling counts a population the run cannot work — `tools/loop/prescreen.sh`
reads `--eligible-only`, so an item whose owner removed `agent/eligible` never reaches the queue — and the run
therefore refuses for questions nobody can answer.

**Why it matters.** Removing `agent/eligible` is the owner's one lever for "not yet, this needs my thinking
first", and the cap punished exactly that lever: with the recorded cap of 10, four parked roadmap decisions
(#47, #49, #52, #725) spent four of the ten slots, so the run that would have produced work stopped instead. The
loop's own escalations keep the opt-in (`tools/loop/labels.sh` writes the decision label and the hold, never
`agent/eligible`), so every question the loop itself asks still counts.

**Surface.** On the base tree:

```
$ grep -rn 'open_decisions' tools/
tools/loop/run.sh:698, :700, :701, :704        the count, the comparison and what the refusal prints
$ grep -rn 'await a decision' tools/ guidelines/
tools/loop/run.sh:701                          the refusal line
tools/loop-cases.sh:3331, :3497                two cases that read the line
$ grep -n 'decisionCapRefusesToStart' tools/loop-mutations.sh
104:  '9.6@-@tools/loop/run.sh@run#decisionCapRefusesToStart@the decision cap that stops a run@s|^if \[ "${open_decisions:-0}" -ge "$max_decisions" \]; then|if false; then|'
```

surface: 1 case added (`run#decisionCapCountsOnlyCandidates`), 1 fixture added
(`tools/loop/fixtures/scenarios/parked-decisions/issues.json`), 2 spec scenarios restated, 0 mutations changed
(record 9.6 anchors on the comparison line, which this change keeps verbatim), 0 compiled files —
`grep -rn 'open_decisions' tools/` (`4 lines in 1 file`), `grep -c 'await a decision' tools/loop/run.sh` (`1`)

**Owning capability and guideline section.** The capability is `agent-issue-loop`; the requirements are *A pile
of open decisions stops the run* (`openspec/specs/agent-issue-loop/spec.md:657`) and *A hold is not a decision*
(`:708`), whose scenario *The cap counts the questions a person can answer* (`:723`) this change restates. The
guideline that states the ceiling's rule is `guidelines/Backlog.md` §6 "How an item becomes work" (the five rules
that keep the queue answerable), and it gains the sentence this change makes true.

## What Changes

- `tools/loop/run.sh`: step 6 counts with `--eligible-only` — the population the pre-screen offers — and the
  refusal prints the candidate items beside both counts (`N of the M item(s) that await a decision are
  candidates, and the cap is X`), so a reader can tell 9 questions from 13 labelled items. The labelled total is
  read only on the refusal path, so a run that starts pays no second tracker read.
- `tools/loop/fixtures/scenarios/parked-decisions/issues.json`: a fifth item shape — the decision label plus
  `status/on-hold` and **no** `agent/eligible` — beside two candidate decisions and one workable item.
- `tools/loop-cases.sh`: `run#decisionCapCountsOnlyCandidates` drives that fixture twice — a cap of two refuses
  and prints neither the parked item nor a candidate count it did not compare; a cap of three starts and spends
  its one slot on the item that asks nothing.
- The spec delta restates *The cap is reached* and *The cap counts the questions a person can answer*.
- `guidelines/Backlog.md` §6 states that the ceiling counts the candidates, and what parking an item does.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-issue-loop` — *A pile of open decisions stops the run* and *A hold is not a decision*: the cap counts the
  items that carry the decision label **and** the loop's opt-in, and the refusal states the count it compared.

## Impact

`tools/loop/run.sh`, `tools/loop-cases.sh`, one fixture directory, the shipped spec, `guidelines/Backlog.md`. No
compiled file, no Gradle case, no app behaviour. A tracker whose decisions are all candidates behaves exactly as
before — the count and the printed list are then the same items.
