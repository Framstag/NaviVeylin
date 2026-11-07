# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: The run's state names the item it is working on

The run SHALL record an item in its state when it picks it, before that item's first agent process starts:
`.current` SHALL carry the item's number, the phase the run last recorded and the change the run asked for, every step
the run records SHALL be attributed to that item, and the mark SHALL come off when the item ends — so a state
kept from a killed run names the item that was in flight instead of a phase alone.

#### Scenario: A state read while an item is in flight names that item

- **WHEN** the run has picked an item and its first agent process is still running, so no later state write has
  happened yet
- **THEN** the state file carries that item's number and phase in `.current`, the live view the run's header
  names (`state.sh show`) prints it as its first line, and every step recorded so far is attributed to that
  item rather than to `0`
- **AND** once the item ends, `.current` is `null` again and no step the run recorded carries another item's
  number or `0`
- **Case**: `run#stateNamesTheItemInProgress`
