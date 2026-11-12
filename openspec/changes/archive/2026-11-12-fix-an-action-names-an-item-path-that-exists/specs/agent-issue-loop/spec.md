# agent-issue-loop delta

## ADDED Requirements

### Requirement: An action's item path is the one the loop root holds

Every item path the report's action list prints — the transcript a gate line names and the item directory a resume
line names — SHALL be built from the `items` directory of the loop root the state file belongs to: that directory when
it sits beside the state, and its parent's `items` when the state is the copy `state.sh init` archived under `runs/`.
A path the report prints SHALL be one the tree holds, from either shape of the state. A sentence the item's own note
already carries SHALL NOT be appended to the line that prints it.

#### Scenario: An action printed from an archived state names an item path that exists

- **WHEN** the report is printed from the state in place and from the state a later run kept under `runs/`, over a run
  whose item was cut at a bound with a kept patch and whose other item recorded no gate result
- **THEN** each report's `resume` line and `re-run the gate` line name the item under the loop root's `items`
  directory — the same path in both reports, and one the tree holds — and the kept patch's path is named once on the
  resume line, not twice
- **Case**: `report#anActionNamesAnItemPathThatExists`
