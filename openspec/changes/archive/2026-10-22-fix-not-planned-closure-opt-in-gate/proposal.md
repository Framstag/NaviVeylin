# fix-not-planned-closure-opt-in-gate

## Why

Root cause: `openspec/specs/issue-backlog/spec.md`'s requirement "Only an authorised actor's item may be acted
on" (line 91) states that the tooling SHALL act on an item **only** when it carries the opt-in label, and its
scenario "The opt-in is required and is not the author's decision alone" (line 102) makes the label a condition
of every act — but `tools/issues/close.sh:100` exempts exactly one path from that label,
`state_reason = not_planned`, because that closure records a person's decision not to act and asserts no work.
The exemption therefore lives in the engine and nowhere in the capability it belongs to, so the settled state
`status/retired` (a settled status in `tools/tooling.json` → `roles.settledStatuses`) has no stated closure path,
and the requirement the tooling implements is contradicted by the requirement the tooling is specified by.

Phase A evidence, verbatim:

```
Root cause:   openspec/specs/issue-backlog/spec.md:91 states the opt-in gates every act, and :102 makes it a
              condition of every closure; tools/issues/close.sh:100 exempts not_planned from that one refusal.
              The exemption has no requirement and no scenario.
Red test:     spec#notPlannedExemptionIsStated and spec#notPlannedScenarioNamesItsCase FAIL on HEAD
              (grep -qi 'not-planned' openspec/specs/issue-backlog/spec.md → 0 matches;
              grep -q 'close#notPlannedNeedsNoOptIn' → 0 matches), while the case the scenario must name is
              already green: close#notPlannedNeedsNoOptIn PASS. XML tests=3 failures=2, EXIT=1,
              2026-10-22T06:31:40Z — evidence/red-HEAD.log, evidence/red-HEAD.xml
Spec:         issue-backlog / Only an authorised actor's item may be acted on
Guideline:    guidelines/Backlog.md §5 "Who may write what" (the write table, row "close"; "the authorisation
              label is the only key to unattended work"), §9 "Checks"
Surface:      0 cases added, 0 rewritten, 0 fixtures realigned; 1 requirement modified, 1 scenario added —
              grep -rl notPlannedNeedsNoOptIn tools/ guidelines/ → 1 (tools/issue-tooling-cases.sh);
              grep -c not-planned → tools/issues/close.sh 7, tools/issue-tooling-cases.sh 10, guidelines/ 0
```

The item's code half — the exemption itself and its case — landed in `9aac52b` ("fix: a person's not-planned
closure needs no opt-in label (#251)"); that commit's message also claims the change record
`fix-not-planned-closure-opt-in-gate`, which no directory in the tree carries, and the requirement delta the
item's own **Fix candidate** names was never written. This change is that record: the code is at HEAD, the
requirement is not, and a capability that contradicts its engine is the defect a fresh reader inherits.

## What Changes

- **`openspec/specs/issue-backlog/spec.md`** — the requirement "Only an authorised actor's item may be acted
  on" gains the one clause the engine implements: the opt-in authorises unattended work, so a closure that
  records a person's decision not to act (`--not-planned`) does not need it, while a closure that records a
  completed fix still does. Its existing four scenarios are carried unchanged, and one scenario is added:
  **A not-planned closure is a record write, not an act of work**, whose `Case` is the green
  `close#notPlannedNeedsNoOptIn` in `tools/issue-tooling-cases.sh`.
- **Nothing else.** `tools/issues/close.sh`, `tools/issue-tooling-cases.sh` and
  `tools/issue-tooling-selftest.sh` are already at HEAD and are not touched: the engine, its case and its
  suite are the evidence this delta cites, and a second edit to any of them would make the record disagree
  with the code it records. No guideline changes: `guidelines/Backlog.md` §5 already says a person closes
  (§5's "close" row, "yes") and that the label is the key to *unattended work*, which is what the exemption
  keeps; it states no rule that the delta reverses.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- **issue-backlog** — requirement "Only an authorised actor's item may be acted on": one clause added to the
  SHALL paragraph, one scenario added, the four existing scenarios unchanged.

## Impact

Affected files:

- `openspec/specs/issue-backlog/spec.md` — through the delta; the archive move is the only write.
- This change record (`proposal.md`, `specs/issue-backlog/spec.md`, `design.md`, `tasks.md`, `evidence/`).

Nothing compiled, nothing executable, no data and no Gradle input changes. Blast radius: a reader of the
capability now learns the closure exemption; the gate is the one `*.md` and `openspec/**` owe
(`tools/check-doc-routes.sh`, `bash tools/gen-feature-list.sh --check-classification`), and the item's own case
(`close#notPlannedNeedsNoOptIn` through `tools/issue-tooling-selftest.sh`) is re-run as the delta's witness.
Rollback: delete the archived change directory and restore the requirement's paragraph and scenario list from
git — no state, no migration, no API.
