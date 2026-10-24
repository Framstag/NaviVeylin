# issue-priority Specification

## Purpose
An item's priority is a fact the tracker holds rather than a number recomputed on every reading: it is
stored in a fixed range with the basis it was computed from, it is reused while that basis is unchanged,
and it is recomputed only for the items whose context actually moved — so ordering a backlog costs a
query, not an interpretation of the whole backlog.

## Requirements

### Requirement: An item's priority is stored, in a fixed range

Every item's priority SHALL be recorded as a value from the project's fixed band range and SHALL be
stored on the item itself together with the components the value was derived from and the basis those
components were computed over.

#### Scenario: The band is selectable

- **WHEN** a reader asks for the highest-priority items
- **THEN** the selection is answered from the stored band, without reading the items' bodies

#### Scenario: The components are recorded beside the value

- **WHEN** an item's priority is inspected
- **THEN** the severity, reach and confidence it was derived from, and the basis they were computed over,
  are recorded with it, so the value can be explained and re-derived

#### Scenario: The range is fixed and total

- **WHEN** the band boundaries are inspected
- **THEN** every value the scoring model can produce falls in exactly one band, and no band is unreachable

#### Scenario: The label and the record agree

- **WHEN** an item's stored band and its recorded components are compared
- **THEN** a disagreement fails a check naming the item, because a hand edit is otherwise invisible

### Requirement: Priority is reused while its basis is unchanged

The pass SHALL recompute an item's priority only when the basis recorded on the item no longer matches, and
SHALL otherwise reuse the stored value without interpreting the item.

#### Scenario: An unchanged item is not re-interpreted

- **WHEN** an item's labels, its body, the code it cites and its recurrence count are unchanged since its
  priority was stored
- **THEN** its stored band is reused and nothing about the item is interpreted

#### Scenario: A repeated run does nothing

- **WHEN** the pass is run twice with nothing changed in between
- **THEN** the second run interprets no item and writes nothing, and it says so in its report

#### Scenario: The basis covers the ways a context moves

- **WHEN** an item's kind, area or state label changes, its body is edited, the code it cites stops
  existing, or a check reopens it
- **THEN** its basis no longer matches and its priority is recomputed

#### Scenario: A surface edit is not a context change

- **WHEN** an item's body text is reformatted without changing its claim
- **THEN** its basis still matches, so a whitespace edit does not trigger a recomputation

#### Scenario: An old value is revisited

- **WHEN** an item's stored priority is older than the configured freshness limit
- **THEN** it is recomputed even though nothing else about it changed, so a stale value is self-healing

### Requirement: Only the changed subset is interpreted, and a run is bounded

A pass SHALL interpret only the items whose basis changed, SHALL bound how many it interprets in one run,
and SHALL report how many are waiting, so a project-wide change cannot turn into an unbounded
interpretation of the whole backlog.

#### Scenario: A bounded run leaves the rest reported

- **WHEN** more items need recomputation than the run's limit allows
- **THEN** the run interprets up to its limit and reports how many remain waiting

#### Scenario: Selection never samples

- **WHEN** the pass determines which items changed
- **THEN** it examines every item, because skipping an item that had changed would leave a wrong priority
  in place indefinitely

#### Scenario: The run explains its cost

- **WHEN** a pass ends
- **THEN** it reports how many items it read, how many it reused, how many it interpreted and how long it
  took

### Requirement: Unclassified items are classified, in the same pass

An item that carries no kind SHALL be classified by the same pass, and the classification SHALL be written
by the tooling rather than by the interpreter that proposed it.

#### Scenario: The unclassified set is the queue

- **WHEN** items without a kind are selected
- **THEN** exactly those items are classified, and an item that already carries a kind is never
  reclassified by this pass

#### Scenario: A proposal outside the vocabulary is refused

- **WHEN** a proposed kind or area is not in the project's vocabulary
- **THEN** nothing is written and the refusal names the value

#### Scenario: An existing value outside the vocabulary is reported

- **WHEN** an item already carries an area the vocabulary does not contain
- **THEN** the pass reports it and does not silently relabel it

#### Scenario: Classification writes nothing else

- **WHEN** an item is classified
- **THEN** only its kind and area change, so classification cannot promote an item or alter its state

### Requirement: Priority is not authorisation

A priority SHALL NOT make an item eligible for automated work, and the pass SHALL NOT write the
automation opt-in.

#### Scenario: A top-priority item is still not eligible

- **WHEN** an item carries the highest band and no opt-in
- **THEN** it is not eligible for automated work

#### Scenario: The pass cannot authorise

- **WHEN** the pass writes to an item
- **THEN** the automation opt-in is not among the labels it can write

### Requirement: Ordering uses the stored priority and derived gates

Ranking SHALL use the stored band and the existing tie-breakers, and the conditions that gate an item —
awaiting a device, awaiting a decision, in flight, unverified — SHALL be derived from the item's labels at
the moment of reading rather than stored with the priority.

#### Scenario: Reading is read-only

- **WHEN** a reader ranks the backlog
- **THEN** it orders by the stored band and by the tie-breakers, and it writes no priority itself

#### Scenario: An item with no stored priority is reported as unranked

- **WHEN** an item carries no band
- **THEN** it is reported as unranked rather than being given one by the reader

#### Scenario: The gates are not stored with the priority

- **WHEN** an item becomes in flight, or starts awaiting a device
- **THEN** its stored priority and basis are unchanged, because those conditions are read from its labels

#### Scenario: A dated deadline outranks a better score

- **WHEN** an item carries a deadline that has the owner's attention
- **THEN** it is placed above its peers inside the ranking, as the project's existing ordering rule says

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
