# documentation-ownership — deltas

## MODIFIED Requirements

### Requirement: The routing cost is measured

The cost of a routed read SHALL be measured against reading the documents the route replaces, and the
measurement SHALL be recorded where the route is documented. A section that a route names SHALL cost at most
12 000 bytes and answer one question: a larger or wider section SHALL be split into lettered sub-sections under
its unchanged number, and the table SHALL route the sub-section that answers the question. The always-read entry
document SHALL cost at most 32 000 bytes. A section, and a document, that the table does not route is not part of
the start cost and SHALL NOT be bounded by this requirement. Each bound SHALL be the bound of the check that
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
- **Case**: `routed-read#bound` — the cost of a section is the byte count of its lines from the `## ` heading
  through the line before the next `## ` heading, and the case prints it for every section of the document

#### Scenario: A split section keeps its number and its citations

- **WHEN** a section is split into lettered sub-sections
- **THEN** the section's own number is still a heading of its document, every citation of that number still
  resolves, and the route check accepts the document set
- **Case**: `routed-read#numbers`

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
- **Case**: `routed-read#verbatim` — `evidence/red-probe.sh` compares the diff's removed and added lines with
  the heading level and the section numbers normalised, and names the first five it cannot pair

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
