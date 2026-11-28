## ADDED Requirements

### Requirement: The run's report prints the share of items whose route resolves

`tools/loop/report.sh` SHALL print once per run the items whose record names a change carrying a `LOOP-ROUTE:` line,
the number of those whose every line resolves against the document it names, and that share beside the archive's.
The resolution SHALL be the one `tools/loop/evidence-check.sh` applies, read from `tools/loop/lib.sh`. An item naming
no change, and a change carrying no route line, SHALL be outside the counted set.

#### Scenario: The line carries the numerator, the denominator and the share

- **WHEN** a run's state names three items — one whose change carries a route a document carries, one whose change
  carries a route naming `§99` that its document does not, and one whose record names no change
- **THEN** the report prints `route: 1 of 2` and its share of those two items, and the item naming no change is in
  neither side of the figure
- **Case**: `report#theRouteShareIsPrinted`

#### Scenario: An item whose change carries no route line is not a resolution

- **WHEN** an item names a change whose markdown carries no `LOOP-ROUTE:` line, and no other item is counted
- **THEN** the figure's denominator is zero and the share prints as `-`, so a run that routed nothing does not read
  as a run that routed nothing wrong
- **Case**: `report#anItemWithNoRouteIsOutsideTheCount`

#### Scenario: A landed change is counted from its archived directory

- **WHEN** an item's change name resolves to no live directory and to one under the archive
- **THEN** the route line is read from the archived copy, so the figure counts the work that landed rather than
  losing every item to a directory the landing removed
- **Case**: `report#theRouteShareNamesItsDenominator`

#### Scenario: The gate and the report read one resolution

- **WHEN** the comparison that decides whether a cited id is one its document carries is broken so that every id
  resolves
- **THEN** both the check's arm and the report's case fail, naming the two readers of the one function
- **Case**: `check#aRouteSectionResolvesAgainstItsDocument`
