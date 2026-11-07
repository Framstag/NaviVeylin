## ADDED Requirements

### Requirement: The change child grades its own diff before it reports

The change child SHALL read the diff it is about to hand over against this contract's blocking predicates before
it prints its diff-ready line, and SHALL answer what it finds there or record it as a note: a scenario the case
that names it does not exercise, a claim in the change's own prose the diff contradicts, an artifact the change
cites that is not where it says it is, and the guideline section phase C named.

#### Scenario: The rendered prompt carries the self-check and what answers each predicate

- **WHEN** the worker prompt is rendered for an item
- **THEN** it carries a block that grades the diff before the line that ends the session, names each of this
  contract's blocking predicates in its own words, and names the one check that answers each
- **AND** it says that a finding made there is fixed in the session or recorded as a note, so the reviewer meets it
  already named rather than spending a round to find it
- **AND** the rule it points at is this contract's, so the prompt states no second definition of *blocking*
- **Case**: `prompt#workerGradesItsOwnDiffBeforeItReports`
