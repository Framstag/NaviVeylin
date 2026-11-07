# map-updates — deltas

## ADDED Requirements

### Requirement: Staging data is never opened or listed as a map

Every consumer that reads the maps tree to open, list, centre on or record a database SHALL apply the
classification the update machinery defines, so the staging directory of a transfer and the previous
installation kept for a swap are never opened or listed as a map. Such a directory SHALL still be named in
the diagnostics together with the installation it belongs to, and the installations beside it SHALL remain
discovered.

#### Scenario: A leftover is not the start map

- **WHEN** the phone resolves its start map in a maps tree where `.nordrhein-westfalen.backup` lies beside
  `nordrhein-westfalen` and both carry a `types.dat`
- **THEN** the resolved start map is the installation, never the leftover
- **Case**: `StartMapResolverTest.a leftover staging directory is never the start map`

#### Scenario: A leftover is not an installed map row

- **WHEN** the map manager refreshes its installed list from a scan that reports `.berlin.backup` beside
  `berlin`
- **THEN** the leftover is not in the installed paths
- **AND** it has no row in the installed list
- **AND** the installation beside it stays installed
- **Case**: `MapManagerSourceLabelTest.leftoverStagingIsNotAnInstalledMap`

#### Scenario: The installations beside a leftover are still discovered

- **WHEN** a scan of the maps tree finds installations at the top level and a level below, a `.backup`, an
  `.incoming`, and a dot directory with no staging suffix
- **THEN** the installations — the dot directory included — are the set the app opens or lists
- **AND** the `.backup` and the `.incoming` are the staging data
- **Case**: `MapTreeDiscoveryTest.theInstallationsAreTheOnlyDirectoriesOpened`

#### Scenario: A leftover is reported with the installation it belongs to

- **WHEN** a scan of the maps tree finds `.berlin.backup`
- **THEN** one diagnostics line names the leftover and the installation it belongs to
- **Case**: `MapTreeDiscoveryTest.aLeftoverIsReportedWithTheInstallationItBelongsTo`

#### Scenario: The native installed list is classified by the same rule

- **WHEN** the map manager is handed an installed list from the native scan that holds a `.backup` and an
  `.incoming`
- **THEN** only the installation is kept
- **AND** each dropped leftover is named in one diagnostics line with the installation it belongs to
- **Case**: `MapTreeDiscoveryTest.aNativeInstalledListIsClassifiedByTheSameRule`
