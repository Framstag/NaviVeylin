# agent-issue-loop delta

## ADDED Requirements

### Requirement: The declared set agrees with the bodies and the dispatch arms of the cases file

The suite's driver SHALL derive the case names the cases file it sourced defines — a body is a `case_<group>_<Name>()
{` line and an arm is a `'<group>#<Name>') fn=case_<group>_<Name> ;;` line — and SHALL compare them with the declared
set `loop_shell_cases` answers before it lists, selects or runs anything. A body with no row, a row with no body or no
dispatch arm, an arm with no row, and an arm whose function is not the body its name derives SHALL each be a refusal
that names the case and the set it is missing from, with exit status 1 and no case executed. The names SHALL be read
from the file that was just sourced, never from a second copy of them. A cases file that defines no body SHALL NOT be
held to the bodies (it dispatches by convention), a body or an arm inside a heredoc SHALL NOT be read as this suite's
own, and the four `stub#owns*` bodies a nested fixture's own quoted `LOOP_SHELL_CASES` string carries SHALL be exempt
by name.

#### Scenario: A row a delta replaced instead of inserting is refused before any case runs

- **WHEN** the driver is run over a cases file that declares one row and defines the bodies `case_stub_kept` and
  `case_stub_lost`, each with a dispatch arm
- **THEN** the driver exits 1 naming `stub#lost` as a body with no row in `LOOP_SHELL_CASES`, no case has been
  executed, and the one row it declares is not read as a whole suite
- **Case**: `driver#aCaseWithNoSelectionRowIsRefused`
