# Spec Delta

## Purpose

The project's always-read entry document routes a reader from the kind of change they are making to the
guideline sections that own its conventions, so the conventions can be found and followed without reading
whole documents, and a route that points at a section can be checked instead of assumed.

## ADDED Requirements

### Requirement: A change is routed to the sections that own its conventions

The always-read entry document SHALL map each kind of change to the guideline sections that own its
conventions, identifying a section by its number and its heading text together. The map SHALL cover the
topics a change routinely touches, and SHALL NOT be required to enumerate every section of every
guideline.

#### Scenario: A phone UI change is routed without reading a document

- **WHEN** a change touches the phone UI
- **THEN** the entry document names the guideline sections that own phone UI conventions, and a reader
  can reach them without reading a whole guideline document

#### Scenario: A topic is owned in more than one document

- **WHEN** a kind of change is governed by conventions that live in more than one guideline
- **THEN** the route names the owning section in each document, so a reader cannot follow one and miss
  the other

#### Scenario: A heading number is ambiguous or absent

- **WHEN** a route points at a section whose number is used twice in its document, or at a section that
  carries no number at all
- **THEN** the route identifies that section by its heading text as well, so the reader lands on the
  intended section

#### Scenario: Sections outside the map stay reachable

- **WHEN** a guideline section is not named by any route
- **THEN** the map states how the complete section list of a document is obtained, and that section is
  reachable by that means

### Requirement: A route resolves to a section that exists

Every section reference a route makes SHALL resolve to a section that exists in the document it names.
A route that points at a section no longer present SHALL be reported as a defect and repaired, and SHALL
NOT be followed.

#### Scenario: The references are checked without a build or a device

- **WHEN** the route check runs
- **THEN** it resolves every section reference in the routing table against the document each reference
  names, and it requires no build, no test run, no device and no network access

#### Scenario: A dangling reference fails the check

- **WHEN** a route names a section that its document does not contain
- **THEN** the check exits non-zero and names the document and the offending reference

#### Scenario: Removing a route is detected

- **WHEN** one route row is deleted, or its section number is changed to one that does not exist in the
  document
- **THEN** the check reports a dangling or missing reference, and restoring the row makes it pass again

#### Scenario: The check is self-tested

- **WHEN** the check's self-test runs
- **THEN** it accepts a fixture route that resolves and rejects a fixture route that does not, without
  reading the project's own documents

### Requirement: The change-artifact guidance reads by section

The change-artifact guidance SHALL direct its reader to the sections that own the change's conventions
rather than to whole guideline documents. It SHALL name where the routing table lives, and SHALL NOT
instruct a reader to read every document under `guidelines/`.

#### Scenario: The served context instruction is section-scoped

- **WHEN** the project context is served while a change artifact is being written
- **THEN** it directs the reader to the sections that own the change's conventions and names where the
  routing table is documented, instead of listing whole documents as required reading

#### Scenario: The apply instruction is section-scoped

- **WHEN** the apply guidance is served
- **THEN** its verification instruction is scoped to the sections that own the conventions the change
  actually touches, and does not require verification against every guideline document

#### Scenario: The guidance survives the edit

- **WHEN** the context and the operation guidance are read back through the OpenSpec CLI
- **THEN** the context is present and both the apply and archive guidance arrays are non-empty, so no
  entry was silently dropped by a quoting change

### Requirement: The routing cost is measured

The cost of a routed read SHALL be measured against the cost of reading the documents the route replaces,
and the measurement SHALL be recorded where the route is documented.

#### Scenario: Numbers for representative changes

- **WHEN** a routed read is documented for this project
- **THEN** the record quotes, for at least one phone-UI change, one car change and one rendering change,
  the cost of the sections the route names and the cost of the document set the route replaces

#### Scenario: An unmeasured saving is not claimed

- **WHEN** a saving from routing is stated without those measurements
- **THEN** the statement is treated as unmeasured rather than as evidence
