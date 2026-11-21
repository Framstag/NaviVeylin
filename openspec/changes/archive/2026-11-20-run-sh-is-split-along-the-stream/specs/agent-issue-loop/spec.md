# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: The engine's files are named, and each name has one owner

Each file of the loop's engine SHALL be declared in one layout table with the functions it owns and the event kinds it may emit. A function SHALL belong to exactly one file. A function defined in a file that does not own it, and a kind emitted by a file that does not own it, SHALL fail the suite.

#### Scenario: Every engine file has exactly one owner

- **WHEN** the suite reads the layout table and the engine's files
- **THEN** every file the engine sources has a row, and no row names a file the engine does not source
- **Case**: `driver#everyEngineFileHasOneOwner`

#### Scenario: A function lives in its owner's file

- **WHEN** a function is defined in a file other than the one the table names as its owner
- **THEN** the case fails and names the function, its owner and the file it was found in
- **Case**: `driver#anOwnedFunctionIsInItsOwnersFile`

#### Scenario: A kind is emitted only by its owner

- **WHEN** a file emits an event kind the table does not list for it
- **THEN** the case fails and names the kind, the emitting file and the owning file
- **Case**: `driver#aKindIsEmittedOnlyByItsOwner`

### Requirement: A case's declared files follow the split

A case SHALL declare the engine file its behaviour lives in, using a name the layout table carries, and SHALL NOT declare a file the table does not carry. A delta to one owner's file SHALL select that owner's cases and not the whole suite.

#### Scenario: A declaration naming an unknown file fails

- **WHEN** a case declares a file name the layout table does not carry
- **THEN** the case-list check fails and names the declaration and the case
- **Case**: `driver#aDeclarationNamingAnUnknownFileFails`

#### Scenario: The selection follows the owner

- **WHEN** the suite is asked for the cases a delta to one engine file can reach
- **THEN** the selection is a strict subset of the suite and contains the cases whose behaviour that file carries
- **Case**: `driver#theSelectionFollowsTheOwner`

#### Scenario: The behaviour of a moved function is unchanged

- **WHEN** the whole suite runs against the split engine
- **THEN** every case that passed before the move passes after it
- **Case**: `driver#theMovedEngineBehavesAsItDid`
