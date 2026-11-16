# agent-issue-loop delta

## ADDED Requirements

### Requirement: A gate to re-run is named only for an item that recorded a change

The report's action list SHALL name `re-run the gate for #<item>` only for an item whose record carries a change —
a field the skip arm writes no value for — beside the gate result that arm already
requires: an item that recorded no gate result and no change planned nothing a gate could measure, so a line naming it
asks the caller for work that cannot exist. An item that recorded a change and no gate result SHALL still be named,
with the transcript beside its id, because its gate is owed. The rule SHALL read the item's own record and SHALL NOT
add a state field, and it SHALL NOT change which arm an item ends on.

#### Scenario: A skipped item is asked for no gate, while an item that reached a change is

- **WHEN** a run's state holds two items it recorded `skipped` — no change and no gate result — and one item that
  recorded a change and no gate result
- **THEN** the report's action list holds no `re-run the gate` line for either skipped item, and holds one for the item
  that recorded a change, naming the transcript beside its id
- **Case**: `run#aSkipIsNoGateToRerun`

## MODIFIED Requirements

### Requirement: The gate's evidence is the tally this item's gate wrote

The run SHALL read an item's gate evidence from the one directory the profile names for the gate's tallies — the
directory the gate writes, cleared before the item's own child starts and again before the gate's first step — and
SHALL NOT read it from every path in the tree whose name happens to hold `test-results`. The count the run records for
an item SHALL be the number of tallies that directory holds once the item's children have ended, so a tree that
carries a test result of its own before the item ran contributes none of it. A report that claims a gate result over a
directory holding no tally SHALL block the item and name the directory it looked at; an item whose children claim no
gate result and leave no tally SHALL record no gate result at all — an empty field, never a count of files that stood
before it — so that the report names among the items whose gate a caller has to run only an item that recorded a
change, and names no item that recorded none. The evidence a closure names
SHALL be a tally from that directory, never a file that stood before the item.

#### Scenario: An item is never closed on an XML that stood before it

- **WHEN** the tree carries a tracked XML under a `test-results` path before the item ran, the first item's children
  claim no gate result and leave no tally in the gate's tally directory, and a second item's gate leaves one
- **THEN** the first item is not blocked for want of a gate result and records none — an empty field rather than a
  count — and, no change being recorded beside that empty field, the report names it on no `re-run the gate for #N`
  line: that line names only an item that recorded a change, while the second records 1 tally, closes on the tally its
  own gate wrote and names that tally as its evidence — the same tree's tracked XML is never counted for either item
- **Case**: `run#theGateColumnNamesThisItemsGateResult`
