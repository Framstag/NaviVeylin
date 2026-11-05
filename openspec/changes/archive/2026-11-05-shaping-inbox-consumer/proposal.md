# The shaping inbox has a queue and no reader

## Why

Capability `shaping-inbox` shipped a third state that is neither a hold nor a decision: `agent/needs-shaping`, one
comment naming the verdict and the clause behind it, and `tools/issues/shaping.sh` as its listing. What it did not
ship is a **reader**. Measured on the tree at 2026-11-02:

```
grep -rln 'shaping' .pi/skills            -> nothing
grep -rn  'shaping.sh' tools/ guidelines/ -> Backlog.md:179,196; decisions.sh:14; answer.sh:74 (a refusal text)
```

So the only path into the inbox's work is a person who remembers `bash tools/issues/shaping.sh` by hand. The
decision queue has the opposite shape: `decisions.sh` is named by the `update-issue` skill, and `answer.sh` is how
an answer is recorded. An item the run handed back ("one root cause does not explain this", "this is not a
falsifiable acceptance condition", "this exceeds one change", "this is not spec-shaped") therefore has no consumer,
no proposal and no next step, and `Backlog.md` §5's promise that it is "visible as work a person owes" holds only
for a reader who already knows the verb.

The absence is felt immediately by the state's own author: the run stops, the label appears, and nothing in the
tooling ever mentions it again.

## What Changes

- **A named consumer.** A new skill `.pi/skills/reshape-issue/SKILL.md` reads the inbox through
  `tools/issues/shaping.sh --json`, and for every open item proposes **either** the single mechanism the item lacks
  **or** the children it splits into, each proposal carrying a `file:line` it verified in the tree.
- **It stops for the owner.** §5 "Who may write what" gives "decide a design question" to a person, so the skill
  proposes, names the cost of each proposal, and writes nothing until the owner picks one.
- **It leaves through the existing writers.** The approved reshape is written by `tools/issues/create.sh` for each
  child and `tools/issues/update.sh` for the item's own shape — the same call that removes `agent/needs-shaping`
  (`shaping-inbox`, "Leaving the inbox is an existing writer's act"). No new verb, no new transition.
- **A child is filed only when it carries a cause.** `create.sh` refuses a finding with no `cause` and no `change`,
  so a proposal that splits an item names, per child, whether it is a tier-A finding (a `file:line` and the case
  that exercises it) or must be planned first; the skill says which and never files a shape-less child.
- **The entry document names it.** `guidelines/Backlog.md` §5 gains the consumer beside the queue, so the state's
  two halves are reachable from the paragraph that owns the state.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `shaping-inbox`: adds "The inbox is read by a consumer that proposes and never decides" — the missing reader, the
  proposal form, the owner's stop, and the writers it is allowed to use. No existing requirement changes; the four
  shipped requirements keep their text and their cases.

## Impact

**Added**: `.pi/skills/reshape-issue/SKILL.md` (the skill), 3 cases in `tools/issue-tooling-cases.sh` under the
`shaping#` prefix.

**Edited**: `guidelines/Backlog.md` §5 (one clause beside `tools/issues/shaping.sh`), the delta's capability spec on
archive.

**Untouched — and deliberately so**: `tools/issues/shaping.sh`, `decisions.sh`, `answer.sh`, `update.sh`,
`create.sh`, `tools/loop/**` and `tools/tooling.json`. This change adds a reader; it changes no state, no label and
no transition, so nothing the loop obeys moves.

**Classification**: additive. **Rollback**: delete the skill directory and its three cases; the state, the queue
and every label are unaffected.

**Guidelines referenced**: `guidelines/Backlog.md` §5 "Who may write what" (the writer table and the shaping
paragraph), §6 "How an item becomes work"; `openspec/specs/backlog-source-of-truth/spec.md` (the two filing shapes a
child must satisfy).

surface: 3 scenarios in one delta file and 3 added cases — counted by
`grep -c '#### Scenario:' openspec/changes/shaping-inbox-consumer/specs/shaping-inbox/spec.md` and
`bash tools/issue-tooling-selftest.sh --list | grep -Ec 'shaping#(theConsumer|theApproved)'`.
