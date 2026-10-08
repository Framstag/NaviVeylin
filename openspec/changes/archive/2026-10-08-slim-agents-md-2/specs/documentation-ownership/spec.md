# Spec Delta

## ADDED Requirements

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
