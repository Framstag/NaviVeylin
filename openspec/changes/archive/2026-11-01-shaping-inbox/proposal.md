# The verdicts that mean "a person must shape this" get an inbox, not a bare hold

## Why

Four of the loop's eligibility verdicts do not state a question — `needs-diagnosis`, `unspecifiable`, `too-wide`,
`not-spec-shaped` (`tools/loop/prompts/worker.md`, gates 2, 4, 5 and 7). The run writes `status/on-hold` and a
comment carrying the reason, and stops. The tree is explicit that this is the right verdict for the *run* and a dead
end for the *person*: a bare hold "SHALL be reported as a hold, SHALL NOT be escalated, and SHALL NOT count against
the decision cap (#193)" (spec `agent-issue-loop`, "A hold is not a decision"), because none of the hold's writers
states a question, and a reason is not something a person can answer.

Measured on the tracker on 2026-10-30:

```
44 items on status/on-hold
  5 carry agent/needs-decision  -> a question with options and a recommendation; answerable today
 39 carry a bare hold           -> a verdict word, no question, no verb that changes anything
  8 of those 39 are sensor items (broken-main, coverage, source-size, stale-citations, …) — a different family
```

The four verdicts have a common meaning that no current state records: **the item is not work until a person
reshapes it** — narrow a scope that spans six capability deltas, re-cut a claim that is not falsifiable, split an
item that is an epic, or diagnose what the claim actually is. Every one of those is a change of shape, which is
exactly the act `finding-shape-at-filing` (C2) makes normal at filing time; the inbox is the same act for an item
that already exists.

**The absence.** `grep -rn 'needs-shaping' tools/ openspec/specs/ guidelines/` returns nothing; the vocabulary in
`tools/tooling.json` declares `agent: eligible, in-progress, needs-decision, needs-device, excluded-stale,
excluded-feature, excluded-settled`, so there is no value for this state and no queue verb that could read one.

**Acceptance scenario.** A run that skips an item with `too-wide` leaves it carrying the shaping label, one comment
stating the verdict and the clause that produced it, and it appears in `tools/issues/shaping.sh` with that statement
— not in the decision queue, and not counted against `LOOP_MAX_DECISIONS`. An explore session that reshapes it
removes the label in the same write that records the outcome.

**Owning guideline.** `guidelines/Backlog.md` §5 "Who may write what" — the paragraph that states a bare hold is
reported and not escalated (#193) — and §6, where an item's road to work is drawn.

**Surface.** Measured with `grep -rn 'needs-shaping\|shaping.sh' tools/ .pi/ openspec/specs/` (no hit), `ls
tools/issues/` (16 verbs, none of them about a state the run leaves), `grep -n 'decisionAgent' tools/tooling.json`
(the roles section this adds beside).

surface: 13 cases added, 1 rewritten (four rows of `labels#verdictToLabelMatrix`), 8 code sites (two of them
comments), 3 mutation records, 2 guideline paragraphs, 2 fixture scenarios —
`grep -hcE '^case_(shaping_|run_aShapingVerdictWritesTheLabel|run_aVerdictOutsideTheListWritesNoLabel|run_theShapingInboxDoesNotCountAgainstTheCeiling|labels_closeRemovesTheShapingLabel)' tools/issue-tooling-cases.sh tools/loop-cases.sh`
prints `9` and `4`

## What Changes

- **`tools/tooling.json`** — one `agent` namespace value, `needs-shaping`, and one roles entry,
  `roles.shapingVerdicts`, naming which verdicts carry it (`needs-diagnosis`, `unspecifiable`, `too-wide`,
  `not-spec-shaped`). A project declares its own list; an empty list means no verdict writes the label.
- **`tools/loop/labels.sh`** — the run's skip path writes the shaping label beside the hold for a verdict the roles
  list names, in the same call that writes the state and the one line accounting for it, and reports the label it
  wrote. `too-wide` additionally states that the item wants splitting.
- **`tools/issues/shaping.sh`** — a read-only queue verb beside `decisions.sh`: every item carrying the label, with
  its verdict and the clause that produced it, and a count. It prints `LOOP-SHAPING-COUNT n`.
- **`guidelines/Backlog.md`** §5 and §6 — the shaping label and its queue, one paragraph each.

## Capabilities

### New Capabilities

- `shaping-inbox`: the state an item carries when a run's verdict was that a person must reshape it, the queue that
  makes those items visible, and the rule that this state is neither a hold nor a decision.

### Modified Capabilities

None. The shipped requirement *"A hold is not a decision"* keeps its text: the shaping label is not the decision
label, it states no question, and it does not count against the decision cap — the three claims that requirement
makes about a bare hold remain true while the item now also carries a label a queue can read. An `ADDED` requirement
states the new label rather than rewriting a requirement whose scenarios this change does not touch.

## Impact

| path | what changes |
|---|---|
| `tools/tooling.json` | `agent/needs-shaping`, `roles.shapingVerdicts` |
| `tools/loop/labels.sh` | the skip path writes the shaping label for the four verdicts; the write is accompanied by its line |
| `tools/issues/shaping.sh` | new — the read-only queue, in `decisions.sh`'s shape |
| `tools/issue-tooling-cases.sh` | the queue's cases; `tools/loop-cases.sh` the run's label case |
| `guidelines/Backlog.md` | §5 and §6 |
| `openspec/specs/shaping-inbox/spec.md` | new capability, after archive |

Additive: a new vocabulary value, a new read-only verb, and one label on a path that already writes a label. No
existing refusal, message, default, cap or rule changes; `LOOP_MAX_DECISIONS` still counts only the decision label.
Rollback path: revert; the label stops being written, the queue verb goes, and the 39 items keep the bare hold they
carry today. No tracker data migration: nothing is re-labelled by this change.

Scope: **general process tooling**. No `:app`, `:auto`, `:core`, `buildSrc`, Gradle, manifest, resource or native
path. No submodule patch, no bridge override.

## Not in this change

- **Re-shaping the 39 items.** The inbox makes them visible; reshaping one is an explore session that ends in
  `file-finding` (C2) and an `update.sh` that removes the label. Retiring a stale one stays `cleanup-todo`'s work.
- **The sensor family.** A sensor item's hold is a recurrence, not a shaping verdict; its lifecycle is
  `backlog-sensors`' and is untouched.
- **Promoting an item to tier B.** That is a write through `create.sh`/`update.sh` from a session, and it needs
  nothing from the run.
- **A cap on the inbox.** The decision cap exists because unanswered decisions stop new work; shaping work is
  unbounded by nature and would make the run refuse for a queue it cannot fill.
