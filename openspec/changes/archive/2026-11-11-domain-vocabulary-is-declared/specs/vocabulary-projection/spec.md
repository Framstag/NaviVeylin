# vocabulary-projection

## Purpose

The declared vocabulary reaches the code it describes: a term's definition is written into the type that
declares it, a declared concept with no type is emitted as a skeleton, and a check mode proves every
projection still matches the declaration instead of trusting whoever ran the generator last.

## ADDED Requirements

### Requirement: The definition reaches the owning type as KDoc

The declared definition SHALL be written into the KDoc of the type declaring the term, so that one text has
two views. The projection SHALL be additive: it SHALL NOT rewrite an existing comment block.

#### Scenario: A type with no KDoc gains the definition

- **WHEN** a term's declaring type carries no KDoc and the type is projected
- **THEN** the type's KDoc holds the declaration's definition text verbatim
- **Case**: `projection#theDefinitionReachesTheType`

#### Scenario: An existing KDoc is left alone

- **WHEN** a term's declaring type already carries a hand-written KDoc
- **THEN** that block is unchanged and the type is reported as not projected
- **Case**: `projection#anExistingKdocIsNotRewritten`

### Requirement: A declared concept with no file is emitted as a skeleton

A row of kind `type` whose home kind is `concept` and whose type no file declares SHALL emit a skeleton type
carrying the definition and a generated marker, with no members. A skeleton SHALL be emitted only when the
file is absent.

#### Scenario: A concept term with no type

- **WHEN** a `concept` row declares a type no file declares and the generator runs
- **THEN** a file is created holding the KDoc definition and the generated marker, and declaring no members
- **Case**: `projection#aSkeletonIsEmittedForAMissingConcept`

#### Scenario: A file that already exists

- **WHEN** the declaring file is present, whether hand-written or previously generated
- **THEN** it is not overwritten
- **Case**: `projection#anExistingSkeletonIsNotOverwritten`

### Requirement: The check mode fails instead of writing

The generator SHALL carry a mode that verifies every projection is present and unmodified and exits non-zero
without writing. A projection whose generated text differs from the declaration SHALL be reported naming the
type and the row.

#### Scenario: A hand-edited projection

- **WHEN** a generated KDoc block or skeleton marker has been edited by hand
- **THEN** the check mode exits non-zero, naming the type and the row, and writes nothing
- **Case**: `projection#aModifiedProjectionFailsTheCheck`

#### Scenario: A tree whose projections are current

- **WHEN** every projection matches the declaration
- **THEN** the check mode exits zero and writes no file
- **Case**: `projection#aCurrentTreeWritesNothing`

### Requirement: The drift measurement files under one identity per refusal

The scheduled measurement SHALL take the refusal word as its identity rather than the term, SHALL bound one
run's filings by a declared maximum, SHALL leave what it does not file as one summary item, and SHALL report
its count without writing while it holds no measurement on record.

#### Scenario: Many terms in one refusal class

- **WHEN** one run finds fifty terms drifting
- **THEN** it produces one condition for `drift`, not fifty, and lists every term in that condition's claim
- **Case**: `projection#fiftyTermsAreOneCondition`

#### Scenario: The first scheduled run

- **WHEN** the row runs on a schedule and holds no measurement on record
- **THEN** it prints the count of conditions it would have filed and writes nothing
- **Case**: `projection#theFirstRunReports`

#### Scenario: A refusal class that keeps returning

- **WHEN** one refusal class has been reopened three times
- **THEN** the row stops reopening it and hands it to a person, holding no eligibility for automated work
- **Case**: `projection#aThirdRecurrenceGoesToAPerson`
