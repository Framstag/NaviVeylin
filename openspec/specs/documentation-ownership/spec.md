# documentation-ownership Specification

## Purpose

The project's always-read entry document routes a reader from the kind of change they are making to the
guideline sections that own its conventions, so the conventions can be found and followed without reading
whole documents, and a route that points at a section can be checked instead of assumed.

## Requirements

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

### Requirement: The always-read document states facts and references rules

The always-read entry document SHALL state facts — what exists, where it lives, how to run it — and SHALL
reference the document that owns a normative rule instead of stating the rule's normative text. Where it
summarises a rule, the summary SHALL be one sentence and SHALL name the owning section, and it SHALL carry
no measurement.

#### Scenario: A rule mention names its owning section

- **WHEN** the entry document mentions a rule that governs code, a process or another document
- **THEN** the mention names the owning document and section, and the normative text — its rationale, its
  refusal path and its measurement — is found in that section

#### Scenario: A rule summary carries no measurement

- **WHEN** the entry document summarises a rule in one sentence
- **THEN** that sentence contains no measured number, and the measurement is read from the owning section

#### Scenario: A fact is stated where it is used

- **WHEN** the entry document names a path, a module, a command, a log tag or a distribution fact
- **THEN** that statement remains in the entry document, because it constrains nothing and is needed before
  any other document is read

### Requirement: A rule has one normative home

A normative rule SHALL be stated in exactly one document. A document that needs the rule SHALL reference
the owning section instead of restating it. When the same rule is found in more than one document, the
owning statement SHALL survive and every other occurrence SHALL become a reference.

#### Scenario: Editing a rule touches one document

- **WHEN** a rule is changed
- **THEN** the normative text is edited in its owning document only, and every other document that mentions
  it continues to read correctly

#### Scenario: A duplicate is removed rather than synchronised

- **WHEN** two documents state the same rule
- **THEN** the owning statement is kept, the other is replaced by a reference to it, and the two are not
  maintained in parallel

#### Scenario: A rule that spans documents names one home

- **WHEN** a kind of change is governed by rules that more than one document needs to mention
- **THEN** exactly one of them states the rule normatively and the others reference it, while the routing
  table still names every section a reader must consult

### Requirement: A relocated rule keeps its statement

A rule removed from the entry document SHALL already be stated in the document that receives it. A block
whose statement exists in no other document SHALL NOT be deleted: it is kept in place and recorded as a
rule without an owner, until a document owns it.

#### Scenario: The removal is verified by finding the statement

- **WHEN** a block is removed from the entry document
- **THEN** the statement it carried is found, by search, in the owning document or in the section that
  received it, and that finding is recorded with the block

#### Scenario: The relocation count closes

- **WHEN** the entry document has been slimmed
- **THEN** the number of removed blocks equals the number of statements found in owning documents, and the
  two counts are reported together

#### Scenario: A homeless rule is filed, not dropped

- **WHEN** a block's statement is found in no other document
- **THEN** the block is not removed, and the rule is recorded for a document to own

### Requirement: A concern has one owning document

A normative rule SHALL be added to the document that owns its concern. A recurring concern that has no
owning document SHALL be given one, and the routing table SHALL name that document, so a reader reaches it
from the kind of change they are making rather than from a search.

#### Scenario: A rule is added to the document that owns its concern

- **WHEN** a new rule is written for a concern that a guideline document already owns
- **THEN** it is stated there, and every other document that mentions it references that section

#### Scenario: A recurring concern without a document gets one

- **WHEN** rules for one concern keep being added while no document owns that concern
- **THEN** a document is created for it, the concern's existing rules move into it, and the entry document
  keeps one sentence per rule plus the route

#### Scenario: A new document is reachable

- **WHEN** a guideline document is added to the repository
- **THEN** the routing table names it, the change-artifact guidance lists it, and the route check resolves
  references into it

### Requirement: The entry document is bounded by what a session needs before it reads anything else

The always-read entry document SHALL hold what a reader needs before any other document is read — the facts
that identify the system and how to run it, the routes to the rest, and one sentence per rule it must not
break. Material that a reader needs only while working inside one area SHALL live in that area's document,
however generally useful it appears.

#### Scenario: A fact needed before any other read stays

- **WHEN** a fact is needed to run the project, to name a component, or to choose which document to read
- **THEN** it is stated in the entry document

#### Scenario: A fact needed only inside one area moves

- **WHEN** a block in the entry document is useful only to a reader working inside one area — a rendering
  rule, a test-suite fact, a logging convention, a packaging step
- **THEN** that area's document states it and the entry document keeps the route

#### Scenario: Relocating keeps the entry document readable

- **WHEN** material leaves the entry document
- **THEN** a reader who follows no route still finds the system identified, the commands to run it, and one
  sentence per invariant that must not be broken

### Requirement: Every guideline document is covered by the route check

The route check SHALL resolve references against the documents the repository contains, derived at run
time rather than from a maintained list. A document added to `guidelines/` SHALL be covered without editing
the check, and a document that no route names SHALL be reported.

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
