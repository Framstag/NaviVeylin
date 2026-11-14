# Proposal — a block names the class of the arm that stopped it

## Why

**Root cause.** The class a block stopped on was an optional field each arm set for itself, and only three of the
eighteen arms of the run's outcome block set one: `stop_reason` is initialised empty at `tools/loop/run.sh:2998` (base
`5c045e55`), `refused` is written at `:3061` and `:3351` and `unmergeable` at `:3386`, with `bound` overriding the class
at `:3012` and `:3338`, and every other arm writes `outcome="blocked"` and a reason and nothing else — so the record of
a block names no mechanism, and the report's `stop` column prints `-` for it (`tools/loop/state.sh:1370`, `state.sh
report`) because the field is null. `state.sh done` accepts four values, the empty one included
(`tools/loop/state.sh:666` on the base), and its own comment says so: a block none of the three caused "SHALL carry
none" (`openspec/specs/agent-issue-loop/spec.md:2488`).
The check the report exists for — the items a bound ended — therefore counts only what the three arms happened to
write, and a reader cannot tell a cut-short work package from an escalation from a child that broke.

**Evidence, verified against the tree (2026-11-13, issue #865).** Over the run archive
(`evidence/archive-stop-reason.log`, taken with
`jq -rs '[ .[] | .items[]? | select(.result=="blocked") | (.stopReason // "null") ] | group_by(.) | …' .pi/issue-loop/runs/*.json`):
148 states, 476 item records, 165 done, **127 blocked**, of which `stopReason` is **null on 108**, `bound` 6,
`refused` 8, `unmergeable` 5 — 85 % of the blocks carry no class, the figure the item was filed with (then 104 of
123 over 145 states) and one the archive has grown into rather than out of. The `stop` column prints `-` for every one
of the 108.

**The single fix.** One place writes a block: `block <class> <reason>` beside `stop_run`
(`tools/loop/run.sh:747` as this change leaves it) sets the result, the class and the reason together, and the eighteen
arms call it instead of writing `outcome="blocked"` and `blocked_reason` for themselves — the pre-gate arm through a
five-line `if … else` whose cut branch keeps its own result write and no class, because a round the run's own bound ended
is a `cut` and `bound` is not a word a review round's result carries. The class list in
`state.sh` grows from four values to twelve, one per mechanism the arms distinguish, and `run.sh`'s own two overrides
stay where they are: `bound` still replaces the class of a child the run's bound ended, and `cut` still carries it.
Nothing else moves — no reason string changes by one byte, no arm's escalation, label or streak changes, and the empty
value stays accepted, because a state a tree wrote before the field existed is a record this writer does not rewrite.

**The red test.** `run#anEmptyTreeIsRefusedBeforeTheGateChild` (`tools/loop/tests/loop-cases.sh`) already stages one of
the silent arms — the change child answers the diff marker and leaves the tree at the run's base — and one assertion is
added to it: the record carries `uncommitted`. Against the base's engine, with only that assertion in place, one
`bash tools/loop/tests/loop-selftest.sh --case 'run#anEmptyTreeIsRefusedBeforeTheGateChild'` run at
2026-11-13T16:23:16Z (`red-on-HEAD.log`, `red-on-HEAD.xml`):

```
run#anEmptyTreeIsRefusedBeforeTheGateChild|   the block names the class of the arm that stopped it, not a null mechanism: expected 'uncommitted', got ''
FAIL run#anEmptyTreeIsRefusedBeforeTheGateChild
loop-selftest: 1 of 1 cases failed
```

`tests=1 failures=1 errors=0`. The same case against the fix passes (`green-red-case.log`, 2026-11-13T16:26:36Z), and
mutation `865.1` empties the class in the one place it is written and the case goes red again
(`mutation-865.1.log`, 16:24:24Z).

**Owning requirement.** `openspec/specs/agent-issue-loop/spec.md:2486`, "A block records the mechanism that stopped it",
whose last clause for a block none of its three causes caused — "a block none caused SHALL carry none" (`:2490`) — is
the rule this change withdraws: no arm is uncaused, the vocabulary names each one, and a classless block is what the
record keeps for a state written before the field existed.

**Owning guideline section.** `guidelines/Backlog.recipes.md`:1287, section 7e "Leftovers, the report and the queue": the sentence
that calls `bound` "the third value of the vocabulary beside `refused` and `unmergeable`" (`:1376`) and the paragraph
that says the table names which mechanism "when a block stopped on one of the loop's two known mechanisms" (`:1425`) —
two mechanisms and three values, against a run that has eighteen arms. Both are corrected there.

## Surface

surface: 18 block arms replaced by one helper call each (one file, `tools/loop/run.sh`; the pre-gate arm keeps a
five-line `if … else` whose cut branch writes its own result and no class), 1 class list and its usage line
(`tools/loop/state.sh`), 2 cases extended and none added (`run#anEmptyTreeIsRefusedBeforeTheGateChild`,
`state#aBlockedItemNamesTheMechanismThatStoppedIt`), 1 mutation record (`865.1`), 2 paragraphs of
`guidelines/Backlog.recipes.md`:1287 (two
paragraphs), 1 requirement modified with 9 scenarios in the delta — counted with
`git grep -c -F 'outcome="blocked"' main -- tools` → 18 matches in 1 file on the base (`tools/loop/run.sh`, every arm)
and 2 after it (the helper at `run.sh:748` and the pre-gate cut branch at `run.sh:2967`), `git grep -c -F 'stop_reason'
main -- tools` → 26 matches in 4 files and 23, `git grep -c -F 'stop-reason' main -- tools` → 34 matches in 5 files and
33, whose 22 `loop-cases.sh` lines are the places the vocabulary's text stands.

## What Changes

1. **`block`** (`tools/loop/run.sh:747`): the result, the class and the reason written in one place, so an arm cannot
   block without naming a class.
2. **Eighteen arms call it**: `refused` for the round before the gate, a red gate a close does not waive and the last
   round at the cap; `unmergeable` for a base that would not take the branch; `child-blocked` for a child whose own
   answer is a block; `child-failed` for a child that exited short of a bound and for a phase that answered nothing;
   `outside-write` for a child that changed the project's own tree; `reviewer-wrote` for a round discarded over the
   item's own tree; `unanswered` for a round whose answer arrived cut twice; `uncommitted` for a report whose claim the
   tree does not show; `unverified` for a gate result the tally does not hold or a cited result with no evidence; and
   `contradicted` for a falsification whose tree had moved. `bound` keeps overriding the class of a child the run's own
   bound ended.
3. **The class list** (`tools/loop/state.sh`): twelve values, each one sentence of what it means and the arms it
   belongs to, the empty value still accepted, and the usage error naming all twelve.
4. **Two cases extended**: the arm's class asserted where the arm's reason already was
   (`run#anEmptyTreeIsRefusedBeforeTheGateChild`), and the usage error's text read against the vocabulary
   (`state#aBlockedItemNamesTheMechanismThatStoppedIt`).
5. **One mutation record** (`865.1`): the class emptied where it is written, the case must go red, the restore green.
6. **The guideline and the delta**: the two paragraphs of section 7e (`guidelines/Backlog.recipes.md`:1287) and the
   modified requirement.

## What this does not do

- It does not make the class mandatory at the record's door: `state.sh done --result blocked` still accepts an empty
  class. Twenty-eight call sites in the cases and seven in the run write one that way today, and a writer that refused them
  would be a second, wider change — one this item's direction does not ask for. What the change guarantees is the
  run's own path: every arm of the outcome block writes its class, and the record's vocabulary holds it.
- It does not change what any arm records besides the class: every reason string is the one the base wrote, and no
  arm's escalation, decision label, streak count or cut reading moves. The eight new classes are written and read; no
  reader of the field treats one of them differently yet, and none has to — the field exists to be counted and read.
- It does not rewrite the archive. The 108 records that carry no class keep carrying none, and the report's `-` for
  them stays the truth about them.
- It does not touch the report: the `stop` column already prints whatever the record carries
  (`tools/loop/state.sh:1355`), so the class reaches the table the moment a run writes one.
