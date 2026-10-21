## ADDED Requirements

### Requirement: A recorded guardrail is stated in the section that owns it

A guardrail entry that a backlog record states as a rule — rather than as a pointer to a rule that already
exists — SHALL be stated in the guideline section that owns its concern, adjacent to the rest of that
section's rules and tagged with the record entry that produced it. The record's carriage marker SHALL NOT be
cleared on the strength of a cross-reference to a neighbouring rule: where an entry states a rule no section,
skill or CI step states, the owning section gains the rule and the record keeps the cross-reference.

#### Scenario: A rule stated only in the record is carried into its section

- **WHEN** the record's entries are audited against the tree and an entry states a rule that no guideline
  section, skill or CI step states
- **THEN** the guideline section that owns the concern states the rule, and the text-only probe
  `evidence/red-probe.sh` finds it there without a build, a device or a network call, so a reader reaches
  it from the section rather than from the tracker
  (case `record-carriage#eachRecordedRuleIsStatedInItsSection`)
