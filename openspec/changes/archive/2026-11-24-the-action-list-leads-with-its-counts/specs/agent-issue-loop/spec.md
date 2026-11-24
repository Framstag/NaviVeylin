## ADDED Requirements

### Requirement: The caller's action list opens with its counts

The report SHALL print, above the numbered list of actions the caller owes, the count of actions by class beside the
first path or issue of each, taken from the rows the list itself prints. `report.sh --actions` SHALL print the numbered
list alone.

#### Scenario: The list leads with its counts

- **WHEN** the state records escalated items, unlanded branches and cut items
- **THEN** one line above the list names `decide`, `land`, `resume` and `worktree` with the figures the list's own rows
  answer, and the list is printed unchanged below it
- **Case**: `report#theActionListLeadsWithItsCounts`

#### Scenario: The list alone is printed on request

- **WHEN** `report.sh --actions` runs against the same state
- **THEN** the numbered list is printed and no header, no table and no trend block with it
- **Case**: `report#theFullActionListIsPrintedOnRequest`
