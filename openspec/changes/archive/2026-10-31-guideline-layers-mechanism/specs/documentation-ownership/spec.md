# Spec Delta — documentation-ownership

## ADDED Requirements

### Requirement: A guideline document carries one kind of text

A guideline document SHALL carry one kind of text: the normative rules in `<Concern>.md`, their rationale in
`<Concern>.why.md`, the procedure in `<Concern>.recipes.md`. A rule SHALL be stated in the normative document
only. Every `## §N <heading>` anchor a companion writes SHALL name a section its base document carries, and a
companion SHALL NOT carry a section number its base document does not.

#### Scenario: A rule sits in the normative layer and its rationale in the companion

- **WHEN** a rule is stated beside the measurement that proved it and the reversal that preceded it
- **THEN** the rule and its refusal path are in `<Concern>.md`, the measurement and the reversal are in
  `<Concern>.why.md` under an anchor naming the rule's section, and the procedure that exercises it is in
  `<Concern>.recipes.md`
- **Case**: `layers#aRuleIsNormativeAndItsRationaleIsACompanion` — a fixture concern whose normative document holds
  one named rule and whose `.why.md` holds the dated measurement, resolved by the route check

#### Scenario: A companion anchors to a section that exists

- **WHEN** a companion writes `## §N <heading>` and the base document carries no section numbered `N`
- **THEN** the check exits non-zero and names the companion file, the anchor and the base document, so a companion
  cannot become a document of its own
- **Case**: `layers#aCompanionAnchorThatNoSectionCarriesIsReported`

#### Scenario: A dropped section is reported in its companion

- **WHEN** a base document stops carrying a section a companion anchors to
- **THEN** the check reports that companion and anchor rather than the companion silently becoming unreachable
- **Case**: `layers#aDroppedSectionIsReportedInItsCompanion`

#### Scenario: No section number moves when a layer is cut out

- **WHEN** a concern's rationale or procedure moves into a companion
- **THEN** the set of `## ` heading numbers its normative document carries is the set it carried before, so every
  citation of one of its sections still resolves
- **Case**: none — the per-change comparison is recorded by the change that made the cut, and a change artifact
  is history the route check does not read, so nothing in the tree can assert it over a later tree; the part of
  the claim a later tree *can* assert is the split's own number retention, which `routed-read#numbers` exercises
  over a fixture that keeps its parent number and its citations

### Requirement: A rule is named

A rule SHALL be written `**Rule: <name>** <statement>.` in the normative document, and the name SHALL be unique
within that document and across `guidelines/*.md`. A document that needs the rule SHALL reference it as
`see rule <File>.md §N "<name>"` rather than restating it. A name MAY change; a reference that no longer resolves
SHALL be reported with the file that writes it, and no identifier, alias or migration table SHALL be maintained.

#### Scenario: Two documents do not own one rule name

- **WHEN** two documents under `guidelines/` each state `**Rule: <name>**`
- **THEN** the check exits non-zero and names both documents and the name, because the second statement is the
  duplicate the "one normative home" requirement forbids and no reader can find it
- **Case**: `rules#aNameTwoDocumentsStateIsReported` — the violation of "A rule has one normative home", found by a
  case instead of by a reader's memory

#### Scenario: A reference resolves to a named rule

- **WHEN** a document writes `see rule <File>.md §N "<name>"` and the section carries a rule of that name
- **THEN** the reference resolves, and the check reports it as a reference rather than as a second statement
- **Case**: `rules#aReferenceResolvesToTheNamedRule`

#### Scenario: A renamed rule leaves its references reported

- **WHEN** a rule's name changes and a reference still quotes the old name
- **THEN** the check reports the reference, its file and the names the section does carry, so a rename is a
  find-and-replace rather than a silent dangling pointer
- **Case**: `rules#aStaleRuleReferenceIsReported`

#### Scenario: A name is unique inside the document that states it

- **WHEN** one document states the same rule name in two sections
- **THEN** the check reports the name and both sections
- **Case**: `rules#aNameTwiceInOneDocumentIsReported`

#### Scenario: A rule with no name is reported

- **WHEN** a normative document states `**Rule:**` with no name, or with a name that is only whitespace
- **THEN** the check exits non-zero and names the file, the line and the token `**Rule:**`, so an empty name is
  reported instead of being skipped in silence
- **Case**: `rules#aRuleWithNoNameIsReported` — a fixture whose normative document carries `**Rule:**` is reported
  with its file and line

### Requirement: A normative document carries no rationale and no procedure

A normative document SHALL state rules and their refusal paths, and SHALL NOT carry a command's output, a
`tests=` or `failures=` tally, a timestamp, a `(measured YYYY-…)` figure, an `issue #N` reference or a `### Why …`
heading. A declared check SHALL report each such occurrence in a normative document with the file and the line that
carries it, and SHALL NOT judge prose.

#### Scenario: A measurement inside a rule is a condition

- **WHEN** a normative document carries a dated or measured figure inside a rule's body
- **THEN** a declared check files that occurrence as a condition naming the file, the line and the token, and the
  condition closes when the figure is stated in the companion instead
- **Case**: `layers#aFigureInANormativeDocumentIsACondition` — the condition is the sensor row's, and the case
  asserts the row's token, file and line over a fixture tree

#### Scenario: A rationale heading in a normative document is a condition

- **WHEN** a normative document carries a `### Why …` heading or a fenced block holding a command's output
- **THEN** the same condition reports it, so a rationale cannot re-enter the layer it left
- **Case**: `layers#aWhyHeadingInANormativeDocumentIsACondition`

#### Scenario: A rule that needs a command keeps it

- **WHEN** a rule's own statement is a command a reader must run, with no output and no measurement
- **THEN** no condition is reported, because the rule is the normative text and only the evidence that proved it is
  the companion's
- **Case**: `layers#aCommandThatIsTheRuleIsNotACondition`

### Requirement: The review's reading set is derived from the change's paths

A review's reading set SHALL be derived from the paths a change touched, through a map from a path glob to the
guideline sections and rule names that own that path's conventions. The map SHALL be a file in the tree, a glob
that matches no tracked path SHALL be reported, and a rule that no glob reaches SHALL be reported. A change MAY add
a section to its derived set by naming it.

#### Scenario: The reading set follows the diff

- **WHEN** a change touches paths whose globs the map names
- **THEN** the derived set lists those sections and rule names, and a review is graded against that set rather than
  against the sections the change's own artifacts name
- **Case**: `routes#theReadingSetFollowsTheDiff` — `tools/check-rule-routes.sh --diff <range>` over a fixture range
  prints the sections and rules, and a change naming fewer sections than the map derives is reported

#### Scenario: A glob that matches nothing is reported

- **WHEN** the map names a glob under which the tree holds no file
- **THEN** the check exits non-zero and names the glob, because a moved package or module otherwise orphans the
  rules a reader is told to read
- **Case**: `routes#aGlobMatchingNoTrackedPathIsReported`

#### Scenario: A rule no glob reaches is reported

- **WHEN** a named rule under `guidelines/` is reachable from no glob in the map
- **THEN** the check reports the rule and its section, so every rule has a reader before it has a violation
- **Case**: `routes#aRuleNoGlobReachesIsReported`

## MODIFIED Requirements

### Requirement: The routing cost is measured

The cost of a routed read SHALL be measured against reading the documents the route replaces, and the
measurement SHALL be recorded where the route is documented. A section that a route names SHALL cost at most
12 000 bytes and answer one question: a larger or wider section SHALL be split into lettered sub-sections under
its unchanged number, and the table SHALL route the sub-section that answers the question. The always-read entry
document SHALL cost at most 32 000 bytes. A document that a route names SHALL cost at most 32 000 bytes — the same
bound as the entry document, so no document a route names costs more than the document a session is handed before it
reads anything else — and a document past that bound is the document's own condition rather than its sections'.
document is read by section and its bound is a multiple of the section's, and a document past that bound is the
document's own condition rather than its sections'. A section, and a document, that the table does not route is not
part of the start cost and SHALL NOT be bounded by this requirement. Each bound SHALL be the bound of the check that
measures it, so retuning either one is one edit in the checks' registry, and a section's cost SHALL be the byte
count of its lines from the `## ` heading through the line before the next `## ` heading.

#### Scenario: Numbers for representative changes

- **WHEN** a routed read is documented for this project
- **THEN** the record quotes, for at least one phone-UI change, one car change and one rendering change,
  the cost of the sections the route names and the cost of the document set the route replaces

#### Scenario: An unmeasured saving is not claimed

- **WHEN** a saving from routing is stated without those measurements
- **THEN** the statement is treated as unmeasured rather than as evidence

#### Scenario: A section carrying more than one question is split

- **WHEN** a section that the routing table names costs more than 12 000 bytes, or answers more than one question
- **THEN** the document splits it into lettered sub-sections under its unchanged number, the section's own
  body becomes an index of them, and every resulting sub-section is at most 12 000 bytes
- **AND** the split is a check's condition, not a case's failure: a routed section still past the bound is the
  condition `sensors#routedSectionSizeRow` files under the identity of its document and section, and
  `routed-read#bound` is the ruler and the print the cost is read with — while the second trigger, a section
  that answers more than one question, is a judgement no case asserts
- **Case**: `routed-read#bound` — the ruler and the print: the cost of a section is the byte count of its lines
  from the `## ` heading through the line before the next `## ` heading, printed for every section of the
  document, and the case fails when a printed cost is not that count; `sensors#routedSectionSizeRow` — the
  filing: a routed section past the bound is that row's condition, carrying the document and the section, and it
  closes when the section shrinks under the bound

#### Scenario: A split section keeps its number and its citations

- **WHEN** a section is split into lettered sub-sections
- **THEN** the section's own number is still a heading of its document, every citation of that number still
  resolves, and the route check accepts the document set
- **Case**: `routed-read#numbers` — a fixture document keeps `## 1.` beside `## 1a.` and `## 1b.`, its body and
  the routing table cite all three, and the route check accepts the set; removing either the parent heading or a
  sub-section heading makes the citation that names it fail

#### Scenario: The route names the sub-section

- **WHEN** the routing table routes a document whose section has been split
- **THEN** the row names the lettered sub-section that answers the reader's question, and states what the
  sections cost
- **Case**: `routed-read#route` — every row that routes a lettered sub-section of a document must also carry the
  cost it states, so a row naming sub-sections and pricing nothing fails the case

#### Scenario: The split moves text instead of rewriting it

- **WHEN** a section is split
- **THEN** every line the split stops carrying is either a promoted heading or a line whose only change is a
  retargeted section reference, so no statement is dropped or reworded
- **Case**: none — the split's diff is a one-off judgement made when the split lands, recorded by the change that
  made it; no harness in the tree can assert it over a later tree, so this scenario names no case rather than a
  file in an archived change

#### Scenario: A routed section past the bound is a check's condition

- **WHEN** a section the routing table names costs more than the section bound
- **THEN** a declared check reports it as its own condition, carrying the section's byte count and an identity
  derived from the document and the section, without a person counting the document by hand — and the condition
  closes when that section shrinks under the bound
- **Case**: `sensors#routedSectionSizeRow`

#### Scenario: An unrouted section is not the start cost

- **WHEN** a section larger than the bound is one the routing table does not name, or a document larger than the
  bound is one no row of the table names
- **THEN** it is not a condition of the check, because a session does not read it before it works — and a routed
  section past the bound beside it still is one
- **Case**: `sensors#routedSectionSizeSkipsUnrouted`

#### Scenario: The entry document is bounded with the sections

- **WHEN** the always-read entry document costs more than its own bound
- **THEN** it is a condition of a check of its own, identified by its path, and it is not reported against the
  section bound nor a routed section against its own
- **Case**: `sensors#entryDocumentSizeRow`

#### Scenario: A routed document past the bound is its own condition

- **WHEN** a document the routing table names costs more than the document bound, while every section of it is
  under the section bound
- **THEN** a declared check reports it as the document's own condition, carrying its byte count and its path, and
  the condition closes when the document shrinks under the bound — it is not reported against the section bound
  nor against the entry document's
- **Case**: `sensors#documentSizeRow` — the row beside `routedSectionSizeRow` and `entryDocumentSizeRow`, whose
  threshold is the bound this requirement states

### Requirement: Every guideline document is covered by the route check

The route check SHALL resolve references against the documents the repository contains, derived at run
time rather than from a maintained list. A document added to `guidelines/` SHALL be covered without editing
the check, and a document that no route names SHALL be reported. A companion document is not a document a route
names: the check SHALL read it, resolve the anchors and the skill paths it carries, and SHALL NOT report it as
unrouted.

#### Scenario: The document set is derived

- **WHEN** the route check runs
- **THEN** it takes the documents to check from the repository's guideline directory, not from a list
  maintained inside the check

#### Scenario: A new guideline is covered without a code change

- **WHEN** a guideline document is added and the routing table references one of its sections
- **THEN** the check resolves that reference, and its document list grew with the directory

#### Scenario: An unrouted document is reported

- **WHEN** a guideline document exists that the routing table does not name
- **THEN** the check reports that document as unrouted

#### Scenario: The derivation is self-tested

- **WHEN** the check's self-test runs
- **THEN** a fixture whose extra guideline holds an unresolvable route fails, and a fixture whose extra
  guideline is named by the table passes

#### Scenario: A companion is read but not routed

- **WHEN** a companion document exists under `guidelines/` that no row of the routing table names
- **THEN** the check reads it, resolves its anchors and any skill path it carries, and reports nothing — while a
  normative document under `guidelines/` that no row names is still reported as unrouted
- **Case**: `routes#aCompanionIsReadAndNotRouted` — a fixture tree with an unrouted `*.why.md` passes and one with
  an unrouted `<Concern>.md` fails
