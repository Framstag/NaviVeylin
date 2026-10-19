# map-download-infrastructure — deltas

## ADDED Requirements

### Requirement: Deleting an installation removes the whole directory

Deleting an installed map SHALL remove the map's directory completely, including the files the native database
delete does not list, and SHALL report the delete as successful once the directory is gone. When the native
delete fails for a directory inside the maps root, the app SHALL remove the remaining tree and SHALL ask the
native map manager to delete the now absent directory again; a directory outside the maps root SHALL NOT be
removed.

#### Scenario: A repository installation's directory is gone after the delete

- **WHEN** the user deletes an installed map whose directory still holds `db.json`, `.source.json` and
  `ptroutes.dat` after the native delete reported failure
- **THEN** the app removes the remaining directory tree
- **AND** the delete reports success and the map manager shows no failure banner
- **Case**: `MapManagerDeleteRepositoryMapTest.deletingARepositoryInstallationRemovesItsDirectory`

#### Scenario: The deleted map leaves the installed list

- **WHEN** the delete of such an installation finished
- **THEN** the map manager is asked to delete the now absent directory once more, which removes the directory
  from its lookup set
- **AND** the map no longer appears in the installed list
- **Case**: `MapManagerDeleteRepositoryMapTest.theDeleteIsRepeatedSoTheMapManagerDropsTheRegistration`
