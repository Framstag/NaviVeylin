# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: Every name two engine files share is declared in one place

A name that more than one engine file reads SHALL be declared once, with the file that assigns it and the files that
read it. A name assigned by two files, a read by a file that does not declare it, and a declared name the files no
longer share SHALL each fail the suite. A name local to a function SHALL need no declaration: a read inside the
function that declares the name `local`, or binds it as the variable of its own `for` loop, is that file's own state
and not a read of another file's assignment. A mention inside a single-quoted run SHALL not be read as shell text,
whatever line of the run it is on, and a value a file hands another on a command line SHALL be the receiving
function's own `local` rather than a name the two files share. A function SHALL be read where a file calls it, and a
call SHALL be the name that opens a command: a name that is a stage of a pipeline, or the second operand of `&&`, of
`||` or of `;`, SHALL be a read of that name, and the word the command before it ended with SHALL NOT stand where the
opening word of a command stands.
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

#### Scenario: A local is not a share of another file's name

- **WHEN** one file assigns a name and another file reads it only inside the function that declares the name `local`,
  or binds it as its `for` loop variable
- **THEN** the derivation reports no share, so the declaration owes no row for it and no refusal names it
- **Case**: `driver#aLocalIsNotAShareOfAnotherFilesName`

#### Scenario: A quoted program is not shell text

- **WHEN** a name is mentioned on a line inside a single-quoted run that was opened on an earlier line
- **THEN** the derivation does not read it as a read of that name, so no row and no refusal names it
- **Case**: `driver#aQuotedProgramIsNotAShellRead`

#### Scenario: A call in pipeline position is a read

- **WHEN** a file calls another file's function as a stage of a pipeline, as the second operand of `&&` or of `||`, or
  as the command that follows a `;`
- **THEN** the derivation reports the name as read by that file, so the row such a read owes is accepted by the
  comparison in both directions instead of being refused as a declaration nothing assigns
- **Case**: `driver#aCallInPipelinePositionIsARead`

### Requirement: The source order is declared and cannot be violated silently

The order in which the engine's files are sourced SHALL be declared in the layout table, and the entry script SHALL
source them in that order. A function called by a file that is defined only by a later file SHALL fail the suite. A
call SHALL be the name that opens a command whatever opened it, so a function called as a stage of a pipeline or after
`&&` SHALL be a call of that name for this rule as much as the first word of a line is.

#### Scenario: The entry sources in the declared order

- **WHEN** the suite reads the entry script's source lines
- **THEN** they are the declared order, and a different order fails the case
- **Case**: `driver#theSourceOrderIsDeclared`

#### Scenario: A call to a later file's function fails

- **WHEN** a file calls a function that only a file later in the declared order defines
- **THEN** the case fails and names the caller, the function and the defining file
- **Case**: `driver#aCallToALaterFilesFunctionFails`

#### Scenario: A call in pipeline position is still a call

- **WHEN** a file calls a function only a later file defines as a stage of a pipeline, or as the second operand of `&&`
- **THEN** the case fails and names the caller, the function and the defining file, as it does for a call at the head
  of a line
- **Case**: `driver#aCallToALaterFilesFunctionFails`
