# issue-priority — deltas

## ADDED Requirements

### Requirement: The marker is read where the pass writes it

The stored marker SHALL be the block comment the pass writes on a line of its own — at the start of the line and closing at its end — outside a fenced code span. Any other occurrence in the body SHALL be prose and SHALL NOT decide whether the item carries a marker or which band it records.

#### Scenario: A quotation of the grammar is prose

- **WHEN** an item's body quotes the marker grammar — inline, or in a fenced span that puts it on a line of its own — and carries no marker of its own
- **THEN** the item is read as carrying no marker, so a band label without a stored record is reported as such instead of being matched against the quotation
- **Case**: `check#aQuotationIsNotAMarker`

#### Scenario: The marker's own line is the record

- **WHEN** an item's body quotes the grammar and also carries a marker line
- **THEN** the marker line is the record and the quotation is not read, so the band, the components and the basis come from what the pass wrote
- **Case**: `check#aMarkerLineBesideAQuotationIsTheRecord`

#### Scenario: The write replaces the marker's line and nothing else

- **WHEN** the pass stores a marker in a body that quotes the grammar
- **THEN** the marker line is replaced, or appended when the body carries none, the quotation survives, and the body carries exactly one marker line
- **Case**: `prioritise#theWriteKeepsAQuotation`
