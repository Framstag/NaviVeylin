# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: Every name two engine files share is declared in one place

A name that more than one engine file reads SHALL be declared once, with the file that assigns it and the files that
read it. A name assigned by two files, a read by a file that does not declare it, and a declared name the files no
longer share SHALL each fail the suite. A name local to a function SHALL need no declaration: a read inside the
function that declares the name `local`, or binds it as the variable of its own `for` loop, is that file's own state
and not a read of another file's assignment. A mention inside a single-quoted run SHALL not be read as shell text,
whatever line of the run it is on, and a value a file hands another on a command line SHALL be the receiving
function's own `local` rather than a name the two files share.

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
