# map-download-infrastructure — deltas

## ADDED Requirements

### Requirement: Deleting an installation removes the whole directory

Deleting an installed map SHALL remove the map's directory completely, including the files the native database
delete does not list, and SHALL report the delete as successful once the directory is gone. When the native
delete fails for a directory inside the maps root, the app SHALL remove the remaining tree and SHALL ask the
native delete once more for the now absent directory. A directory outside the maps root SHALL NOT be removed,
and a refusal there SHALL be reported as the delete's failure.

#### Scenario: A repository installation's directory is gone after the delete

- **WHEN** the user deletes an installed map whose directory still holds `db.json`, `.source.json` and
  `ptroutes.dat` after the native delete reported failure
- **THEN** the app removes the remaining directory tree
- **AND** the delete reports success and the map manager shows no failure banner
- **Case**: `MapManagerDeleteRepositoryMapTest.deletingARepositoryInstallationRemovesItsDirectory`

#### Scenario: The deleted map leaves the installed list

- **WHEN** the delete of such an installation finished
- **THEN** the native delete is asked a second time, for the now absent directory
- **AND** the map no longer appears in the installed list
- **Case**: `MapManagerDeleteRepositoryMapTest.theDeleteIsRepeatedSoTheMapManagerDropsTheRegistration`

#### Scenario: A directory outside the maps root is not swept

- **WHEN** a delete is refused for a path outside the maps root
- **THEN** nothing outside the maps root is removed
- **AND** the native delete is not asked a second time
- **AND** the app reports the delete as failed
- **Case**: `MapManagerDeleteRepositoryMapTest.aDirectoryOutsideTheMapsRootIsNotSwept`
