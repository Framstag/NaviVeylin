# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: The item's record names the epic it belongs to, and the report groups by it

The run SHALL record the tracker's parent link of an item it works, beside its class and change, and the report SHALL
group the run's items by it on a line of its own: how many of that epic's children the run closed, their tokens, their
wall, and how many items sit in no epic. `n` SHALL be the run's children of that epic, never the epic's own count: the
report reads the state and git, never the tracker, and a figure no child reported SHALL print as absent with its
reason.

#### Scenario: A run's item carries the epic the tracker gives it

- **WHEN** the pre-screen reads a candidate the tracker gives a parent, and an item the tracker gives none, and both
  are worked and recorded
- **THEN** the queue carries each item's parent beside its class, the item's own record carries it beside its change,
  and the item with no parent records `null` — not a parent of its own
- **AND** no second tracker read is made for it: the parent is a field of the answer the screen already holds
- **Case**: `prescreen#theScreenRecordsTheItemsEpic`

#### Scenario: The report groups the run's items by epic

- **WHEN** a run's report is printed for a state whose items record two epics, a child that closed, a child that
  blocked, one item in no epic and a child of an epic whose figures nobody reported
- **THEN** the report prints one group per epic in the record's id order, naming how many of that epic's children the
  run closed, their token spend summed in the unit the report prints every token figure in, and their wall in the
  clock the run's own bounds are read in
- **AND** the figure no child reported prints `-` with its reason, an epic of one child is not called `children`, the
  items in no epic are counted, and a state whose items all sit outside an epic prints `none` rather than a group of
  nulls
- **Case**: `report#theRunGroupsItsItemsByEpic`
