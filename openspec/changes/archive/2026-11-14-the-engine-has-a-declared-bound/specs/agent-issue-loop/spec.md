# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: Every engine script enters one layout table, and no file exceeds its bound

Every script under the engine directory SHALL have one row in a layout table, and that row SHALL carry a positive line bound the suite compares the file against. A script with no row, a row naming a file the engine does not have, and a row whose bound is absent or not a positive number SHALL each fail the suite.

#### Scenario: Every engine script is in the table

- **WHEN** the suite compares the table's file rows against the engine's scripts
- **THEN** the two sets are equal, and a script with no row fails the case naming it
- **Case**: `driver#everyEngineScriptIsInTheTable`

#### Scenario: A file over its bound fails

- **WHEN** an engine file's line count exceeds the bound the table records for it
- **THEN** the case fails and names the file, its line count and its bound
- **Case**: `driver#noEngineFileExceedsItsBound`

#### Scenario: A row without a usable bound is refused

- **WHEN** a row carries no bound, or a bound that is not a positive number
- **THEN** the case fails rather than reading the file as unbounded
- **Case**: `driver#aFileWithoutABoundIsRefused`

### Requirement: Every name two engine files share is declared in one place

A name that more than one engine file reads SHALL be declared once, with the file that assigns it and the files that read it. A name assigned by two files, a read by a file that does not declare it, and a declared name the files no longer share SHALL each fail the suite. A name local to a function SHALL need no declaration.

#### Scenario: A name assigned twice is refused

- **WHEN** two engine files assign the same name
- **THEN** the case fails and names the name and both files
- **Case**: `driver#aNameAssignedTwiceIsRefused`

#### Scenario: An undeclared read is refused

- **WHEN** a file reads a name the declaration does not list it as a reader of
- **THEN** the case fails and names the name and the file
- **Case**: `driver#anUndeclaredReadIsRefused`

#### Scenario: The declaration covers what the files share

- **WHEN** the suite compares the declared names against the names assigned in one file and read in another
- **THEN** the two sets are equal, so neither a stale row nor an undeclared name survives
- **Case**: `driver#theSharedNamesAreDeclared`

### Requirement: The source order is declared and cannot be violated silently

The order in which the engine's files are sourced SHALL be declared in the layout table, and the entry script SHALL source them in that order. A function called by a file that is defined only by a later file SHALL fail the suite.

#### Scenario: The entry sources in the declared order

- **WHEN** the suite reads the entry script's source lines
- **THEN** they are the declared order, and a different order fails the case
- **Case**: `driver#theSourceOrderIsDeclared`

#### Scenario: A call to a later file's function fails

- **WHEN** a file calls a function that only a file later in the declared order defines
- **THEN** the case fails and names the caller, the function and the defining file
- **Case**: `driver#aCallToALaterFilesFunctionFails`
