# Design — a block names the class of the arm that stopped it

## Root cause

The class of a block was written by the arms that happened to remember it. `stop_reason` is initialised empty
(`tools/loop/run.sh:2998` on base `5c045e55`) and the outcome block has eighteen arms that record a block; three of them
name a class (`refused` at `:3061` and `:3351`, `unmergeable` at `:3386`, beside the two `bound` overrides at `:3012`
and `:3338`) and the rest set only `outcome="blocked"` and `blocked_reason`. The record's own vocabulary kept that shape
honest — it accepted the empty value (`tools/loop/state.sh:666`), and the requirement
says a block none of the three mechanisms caused "SHALL carry none" (`openspec/specs/agent-issue-loop/spec.md:2490`) —
so nothing anywhere could notice a block that named no mechanism. Measured over the archive: `stopReason` is null on
108 of 127 blocked items, and the report's `stop` column prints `-` for each.

## The single fix

One place writes a block, beside `stop_run` (`tools/loop/run.sh:747` as this change leaves it):

```bash
block() { # <class> <reason> — the item is blocked, with the class of the arm and the reason beside it
  outcome="blocked"
  stop_reason="$1"
  blocked_reason="$2"
}
```

Eighteen arms call it, each naming the class of its own mechanism, and the arms that already named one keep the word
they wrote. The pre-gate arm is the one that cannot be a single call — a round the run's own bound ended is a `cut` and
cannot carry `bound` (`tools/loop/run.sh:2966`–`:2970`) — so it keeps a five-line `if … else` whose cut branch writes
the result and the reason it already holds and no class. The class list in `state.sh` grows from four values to twelve,
the empty one still accepted, and `state.sh`'s usage error names the twelve. `run.sh`'s own override stays where it is —
a child the run's bound ended has its class replaced by `bound` (`:2980`), which is what `cut` carries — so the one rule
"a bound is not a refusal" keeps the single spelling it has.

The classes are the mechanisms the arms distinguish, and nothing more:

| class | the arm it names |
|---|---|
| `refused` | the round before the gate; the last round at the cap; a red gate a close records no waiver over |
| `unmergeable` | a base that would not take the branch |
| `bound` | a child the run's own bound ended (with `cut`, and on an older `blocked` record) |
| `child-blocked` | the child's own answer is `LOOP-VERDICT: blocked` |
| `child-failed` | a child that exited short of a bound; a phase that answered nothing |
| `outside-write` | a child that changed the project's own tree |
| `reviewer-wrote` | a round discarded because the item's own tree changed under its reviewer |
| `unanswered` | a round whose answer arrived cut twice |
| `uncommitted` | a report whose claim the tree does not show — no commit, no archived change |
| `unverified` | a gate result the report claims and the tally does not hold; a cited result with no evidence |
| `contradicted` | a falsification whose unchanged tree had moved by the end of its review |

Nothing else moves. The result, the class and the reason are one write, so the field cannot drift from the result it
belongs to — the cost the item named — and no arm can block without one, because there is no other way left to block.

## Blast radius

`tools/loop/run.sh` (one helper; eighteen arms, each a call, the pre-gate arm a five-line `if … else` that keeps its cut
branch's own result write, and their reason strings unchanged byte for byte),
`tools/loop/state.sh` (the vocabulary case, its comment and the usage line), `tools/loop/tests/loop-cases.sh` (two
cases extended), `tools/loop/tests/loop-mutations.sh` (one record, `865.1`), `guidelines/Backlog.recipes.md`:1287 (section 7e, two
sentences) and this change's delta. The delta compiles nothing, so no Gradle build is owed: the structural checks of
the files it edits stand in for one, and the loop's own gate is the gate child's, over the finished tree.

What a reader of the record sees change: the `stop` column of `state.sh report` and of `report.sh` carries a class for
every block a run writes. What no reader sees change: no reason string, no decision label, no streak — `state.sh next`
keeps every block in the two-blocked streak exactly as it did, because it reads `!= "bound"` and no block the run
writes carries `bound` any more (a bound leaves a `cut`).

## Rollback

Revert the arms to `outcome="blocked"; blocked_reason=…`, delete `block`, restore the four-value vocabulary case and
its usage line, and drop the two guideline sentences. A state written by the reverted run reads exactly as it did,
because the vocabulary still accepts what the three arms wrote and the empty value beside them.

## Alternatives the evidence eliminated

- **Making the class mandatory at the record's door** (`state.sh done --result blocked` refuses an empty one). It is
  the stronger rule and it is the wider change: twenty-eight call sites in the cases and seven `loop_state done` calls
  in the run write a block without a class today, and each would have to be given one — including the arms that block
  inside a signal handler, where there is no arm to name. The item's own direction is the run's arms and the class
  list, and a door that refuses is a second change with its own evidence.
- **Three classes, the three the item names** (a worker-reported escalation, a falsified premise, a gate that could not
  run). The archive does not bear it out: the arms block for a child that exited, for a tree a child changed under the
  run, for a reviewer that wrote in the tree it read, for a round that never answered, for a report whose claim the
  tree contradicts — six mechanisms none of the three names, and an item's `outside-write` classed as an escalation is
  a class a person acts on wrongly. A class per mechanism is what the vocabulary already was (`refused` and
  `unmergeable` are two mechanisms, not two kinds of one).
- **Deriving the class from the item's `verdict`** instead of writing it beside the result: the verdict a block
  carries is `blocked` for every arm, so the two fields would agree and say nothing — which is the drift the item
  warns about, arrived at from the other side.
- **A second field beside `stopReason`** (an arm's name, a mechanism code): the vocabulary *is* the second field, and
  the item's cost — two fields that can drift — is what one write removes. A record would then carry an arm's name
  nothing reads and a class beside it.
- **Reading the class out of the reason's prose in the report**: the prose is the reason, not a field, and #280's
  measurement (198.8 M of refusing rounds against 22.9 M of merge refusals, told apart only by the note) is the
  reading that failed at exactly that.

## Open decisions

none

The item's direction settles the shape — `run.sh` plus the class list in `state.sh`, one case and one mutation, the two
fields written from one place — and the classes it names as examples are instances of the per-mechanism rule, not a
closed three; the alternatives above are the ones the tree and the archive eliminated. What the change leaves open is
not a decision but a measurement: how many of the archive's 108 classless records a re-run of the same arms would now
classify, which nothing can answer without re-running the runs.
