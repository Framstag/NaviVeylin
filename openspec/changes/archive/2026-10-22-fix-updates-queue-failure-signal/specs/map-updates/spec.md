# map-updates — deltas

## ADDED Requirements

### Requirement: An update that cannot be installed is reported for its own item

An update that does not complete SHALL be reported on that installation's own row, as a failure carrying the text
for the reason it did not complete, and SHALL leave the installation exactly as it was. A one-item update that
completes SHALL leave no failed row for its item. No listed item's row SHALL carry another item's failure — so
"the update failed" and "the update had not finished" are two statements a surface, and a test, can tell apart.

#### Scenario: A swap that cannot complete is reported, and the installation is unchanged

- **WHEN** a one-item update's transfer has completed and verified but the swap of that slot into the installation
  cannot be made
- **THEN** that item's row reports a failure carrying the text for an installation that could not be made
- **AND** the installation is the one the app had
- **AND** no other listed item's row carries a failure
- **Case**: `app: MapManagerUpdatesQueueTest` "aFailedSwapIsReportedAndKeepsTheInstallation"

#### Scenario: A one-item update that finishes leaves no failure row

- **WHEN** a one-item update completes
- **THEN** its row carries no failure, so an assertion on the installed data that follows cannot be read as a
  failed transfer without the failure having been named
- **Case**: `app: MapManagerUpdatesQueueTest` "updateOneItemRunsOnlyThatOne"
