# Design

## D1 — the relation is the tracker's, not a line of the body

The tooling already writes two of the tracker's own fields for an item's place in the backlog (`issueType` and
`parent`), so a dependency is the third and not a new kind of record. The relation is live on the tracker:
`AddBlockedByInput` takes `{clientMutationId: String, issueId: ID!, blockingIssueId: ID!}`, `Issue.blockedBy` reads
it, and `gh issue view <n> --json blockedBy` answers it in the CLI this repository pins.

A `Depends-on: #N` line in the body was considered and refused. It is a second reader of prose — the thing the
epic's order sentence already is — and it would have to be validated, kept in step with the tracker and re-read by
every future consumer. The correction is recorded on #919 itself: the item was filed asking for a body line and its
own comment replaced that with the edge.

Measured 2026-11-15: `blockedBy.totalCount` is 0 for every one of #898–#906, so nothing was read from an edge that
already existed; the prose in #906 is the whole of the order those seven items had.

## D2 — one reader, two sources

`blocked_by_of()` in `tools/loop/prescreen.sh` answers a candidate's blocker from two places, in this order:

1. the item's own `blockedBy` nodes, the first whose `state` is not `CLOSED`;
2. the record this run wrote — an item of `.items[]` whose `result` is `blocked` and whose `change` is the
   candidate's, read once from `$LOOP_STATE_FILE`.

The two halves are #919 and #913, and they are one call because they are one question: *what must land before this
item can be worked?* A separate reader for the within-run case would be the second place to keep the verdict's
shape in, and #913's run is exactly what happens when a fact is held in two places and only one is read.

The within-run half deliberately reads a change and not an edge: the six items #913 measured carry no edge to each
other and no edge could have been written for them, because the task list of one change is not a set of issues the
tracker knows about until a person files them.

A `done` item of the same change is not a block — it is a landing, and the items after it are exactly what the
change's task list says comes next. A `cut` item is not a block either: a bound is work the next run resumes, not a
finding, and `state.sh` records it `cut`, so the filter reads `result == "blocked"` with the `stopReason: bound`
exclusion `state.sh`'s own refusal streak already carries.

## D3 — the verdict names its predecessor and writes no label

The verdict is `blocked-by:#N` — the role's value followed by the number, which is the same shape the other
verdicts' evidence lines already use. Naming the predecessor is what makes the exclusion actionable: a reader can
open that item, and the next run re-reads the same edge without a word from anyone.

No label is written. Every other refusal label exists because the run's state is gone afterwards and the item would
otherwise look untouched; the tracker's edge is not gone, so a label would be a *second* copy of a fact the tracker
already shows, and a second copy is a thing to keep in step — the failure mode this change exists to remove. The
evidence field names where the refusal was read from (`blockedBy:#N` or `run-blocked:#N`), which is what a reader
needs and what a label could not say.

## D4 — the verdict is an optional role key

`blockedVerdict` joins `roles` in `tools/tooling.json`. It is declared in `LOOP_OPTIONAL_ROLE_KEYS` beside
`shapingVerdicts` and `containerType`, so a profile that omits it is not a half-adapted profile, and an empty value
declares that this project's tracker blocks nothing.

Two reasons. A copy of this tooling elsewhere may use a tracker whose dependency relation is either unused or absent,
and the same profile shape has to serve it. And a profile written before this change must keep running: the
alternative — a required key — turns an upgrade into a stopped pre-screen on every tree that has not been edited.

The foreign fixture declares `waiting-on`, deliberately different from this project's `blocked-by`, so a suite that
passes cannot be passing by accidental similarity.

## D5 — the predecessor is read before the filing

`gh issue create` has no dependency field, so the edge is written afterwards by a mutation that takes the new item's
node id. That half cannot be atomic, and the change says so rather than pretending otherwise: a mutation that fails
after the filing is reported with the item's number, and the run knows the item exists and its edge does not.

What *can* be prevented is the other order's failure: a create that succeeds and then discovers its predecessor
never existed leaves a filed item nobody meant to file. So every predecessor's handle is read before the filing and a
missing one refuses with nothing written — the same discipline the type name already follows (`gh --type Bogus` files
the item and reports the allowed set afterwards, which is how issue #874 was created).

A parent is refused as a predecessor by the same reasoning: the parent *holds* the item, so the item cannot be
worked before it exists; a `--blocked-by` on it would mean the item waits for its own container to be "landed",
which is a sentence with no meaning.

## D6 — what the reader leaves alone

- An item whose `blockedBy` node is `CLOSED`, and one whose node answers with no `state` field, reads as unblocked
  or blocked respectively: the reader defaults an absent state to `OPEN`, because a tracker that answers the edge
  and omits the state is read conservatively, and a closed predecessor is the one case the edge clears itself.
- The verdict sits after `missing-authorisation` in the ladder and before the change-readiness gate, so an item the
  run may not work at all is refused for that reason first, and a blocked item is not spent on a readiness check.
- The exclusion is a refusal, not a cut: `state.sh next`'s streak counts it like any other refusal, and `run.sh`
  prints it with the other excluded verdicts. It is not escalated — the tracker shows the edge, so there is no
  question to ask a person.

## Risks

- **A change whose blocker never lands holds all its later items forever.** That is the intent (#913 measured 0
  landings for exactly that case) and it is visible: the verdict names the item to repair, and the pre-screen reads
  the tracker again on every run.
- **A tracker that answers an edge with no state** is read as blocked. Conservative by design; the alternative is
  to work an item whose predecessor might be open.
- **The role key is read through the profile**, so an engine that named the value itself would leak a project value
  into `tools/loop/**`. `tools/check-portability.sh` forbids that, and it is green.