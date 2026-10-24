# design — fix-not-planned-closure-opt-in-gate

## D1 — The root cause, and why the fix is a requirement and not a second code edit

`tools/issues/close.sh:100` exempts exactly one refusal for one path:

```sh
if [ "$status" -eq 3 ] && [ "$reason_code" = "missing-eligibility-label" ] \
  && { [ -n "$sensor_label" ] || [ "$state_reason" = "not_planned" ]; }; then
```

`openspec/specs/issue-backlog/spec.md:92` (the requirement heading is `:91`) said the opposite in the
normative voice: the tooling SHALL act on
an issue only when the author is allowlisted **and** the item carries the opt-in label, and its scenario at
`:102` makes non-eligibility follow from a missing label. Before `9aac52b` the engine matched the spec and a
person could not close a `status/retired` item at all; after it, the engine and the spec disagree, and no
check in the tree compares a requirement's scenarios to the cases the tooling ships —
`tools/check-doc-routes.sh` resolves the paths a document cites, `bash tools/gen-feature-list.sh
--check-classification` names an unclassified capability, and `tools/loop-cases.sh` maps the loop's own
scenarios, not a capability's (each read from its own usage line, 2026-10-22).

So the defect is not in the engine: the case `close#notPlannedNeedsNoOptIn` (green, `tools/issue-tooling-cases.sh:1471`)
already asserts both halves — the completed closure of an unlabelled item exits 3 with
`missing-eligibility-label` and posts nothing, the not-planned closure exits 0, posts the reason and reaches the
CLI as `--reason not\ planned`. The defect is that the capability the tooling is specified by never gained the
requirement that case proves, so this change writes that requirement and nothing else.

## D2 — The single fix

One MODIFIED requirement in `issue-backlog`, one clause and one scenario:

- the SHALL paragraph says what the opt-in authorises (unattended **work**), exempts the not-planned closure
  from that one refusal, bounds the exemption to "the gate's only refusal is `missing-eligibility-label`", and
  keeps the opt-in mandatory for a completed closure of an item no declared check owns — the `--sensor` path
  is authorised by the check's own row, not by the label (`tools/sensor-cases.sh:798-809`);
- the new scenario names its case, `close#notPlannedNeedsNoOptIn`, and states both halves so a reader cannot
  read the exemption as "closures need no label".

No second scenario, no new capability, no engine edit, no guideline edit.

## D3 — The alternatives the evidence eliminated

- **Re-edit `close.sh` or its case.** Eliminated: `9aac52b` is at HEAD and the case is green
  (`tools/issue-tooling-selftest.sh --case close#notPlannedNeedsNoOptIn` → PASS, EXIT=0,
  2026-10-22T06:31:56Z). A second edit would make the record disagree with the code it records.
- **Add a check that compares every spec scenario's `Case` to a shipped case.** Eliminated as out of scope: it
  is a new engine (`tools/**`) with its own cases, its own gate mapping and its own change — a different item,
  and the loop's strict eligibility is one root cause and one fix (`Design.md` §12, the loop's gate 5).
- **Change `guidelines/Backlog.md` §5.** Eliminated: §5's write table already grants a person the close row
  ("yes") and says the label is "the only key to unattended work" — the sentence the exemption implements. A
  guideline edit would restate the requirement in two places, which `documentation-ownership` forbids.
- **Revert the exemption so the tree matches the old spec.** Eliminated: it restores the item's symptom (17
  `status/retired` items no command can settle) and it reverses a decision the item's own text records.

## D4 — Blast radius

One capability document (`openspec/specs/issue-backlog/spec.md`, through the delta), plus this record. Nothing
compiled, nothing executable, no data, no Gradle input, no engine behaviour: the tree's behaviour at the end of
this change is exactly the tree's behaviour at `9aac52b`. The gate the delta owes is the documentation pair
(`tools/check-doc-routes.sh`, `bash tools/gen-feature-list.sh --check-classification`), and the item's case is
re-run as the requirement's witness.

## D5 — Rollback

Delete the archived change directory and restore the requirement's paragraph and scenario list from git. The
capability returns to the state it was in at `9aac52b`, which is the state the item records: 17 open
`status/retired` items the tooling could not close (the item adds that two of them, #140 and #143, are in fact
fixed, so a bulk sweep would also misrecord them) — so a rollback is a decision to re-open the item, not a
neutral revert. No state, no migration, no API.

## D6 — The corrections review round 1 forced

Two claims in the first archive were false against the engine the delta cites, and the round refused them
(`.pi/issue-loop/items/251/reviewer-report-1.md`, findings BLOCKING ×2):

- *"A closure that records a completed fix SHALL still require the opt-in"* ignored the second term of the
exemption it cites — `tools/issues/close.sh:100-101` is
`{ [ -n "$sensor_label" ] || [ "$state_reason" = "not_planned" ]; }`, and the shipped, green
`close#sensorScopeAllowsItsOwnIssue` (`tools/sensor-cases.sh:798-809`) closes with `--reason completed`,
exits 0 and writes no `agent/eligible`, which `guidelines/Backlog.md` §5's `close` row grants. The sentence is
now scoped to an item no declared check owns, and the check-row authorisation is named. The claim is measured:
that case is one of the six checks in `evidence/probe-251-correction.sh`, green on both sides of the
correction.
- *"the settled state is written before the close"* was asserted by the added scenario and proved by nothing
it names: `close#notPlannedNeedsNoOptIn`'s fixture is item 41, which already carries `status/retired`, so
`tools/issues/update.sh:149-151` writes nothing ("already carries the state: adding it again is not a
change"), and `close#writesNothingWhenTheItemAlreadyCarriesTheState` asserts exactly that. Ordering is
asserted only on the completed path. The clause is deleted; the THEN states what the case proves — the reason
and its evidence are posted, the CLI receives `not planned`, and the completed closure is still refused.

Nothing executable, no case and no guideline changes: the corrections are two sentences in one requirement,
the archived delta and the merged capability kept byte-identical (probe check
`spec#deltaMatchesTheMergedRequirement`).
