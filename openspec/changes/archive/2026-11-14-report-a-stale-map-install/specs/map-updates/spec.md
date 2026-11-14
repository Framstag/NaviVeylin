# Spec Delta — map-updates

## ADDED Requirements

### Requirement: An installed database that predates the shipped stylesheet is reported

When the app compares the type names an installed database carries in its `types.dat` with the names the
bundled `map.ost` declares, a database missing any of them SHALL be named in the map manager as one whose data
predates the app's stylesheet, together with the download that replaces it. A database carrying every declared
name SHALL be reported as current. A `types.dat` whose format version is not the one this build reads SHALL NOT
be judged at all.

#### Scenario: A database short of the declared types is named

- **WHEN** an installed database's `types.dat` lacks names the bundled `map.ost` declares
- **THEN** the map manager names that map as one whose data predates the app's stylesheet
- **AND** the offered action is the download that replaces it
- **AND** the named count is the number of declared names the database lacks
- **Case**: `StaleInstallTest#aDatabaseShortOfTheDeclaredTypesIsReported` and
  `MapManagerStaleRowTest#theInstalledRowNamesTheStylesheetItPredates`

#### Scenario: Every declared name is carried

- **WHEN** an installed database carries every name the bundled `map.ost` declares
- **THEN** no notice is shown for it
- **AND** the row reads as installed, as before the change
- **Case**: `StaleInstallTest#aDatabaseCarryingEveryDeclaredNameIsNotReported`,
  `MapManagerStaleRowTest#aDatabaseCarryingEveryDeclaredNameCarriesNoShortfall` and
  `MapManagerStaleNoticeComposeTest#aCurrentInstallationRowCarriesNoNotice`

#### Scenario: A foreign format version is not judged

- **WHEN** an installed database's `types.dat` names a format version other than the one this build reads
- **THEN** the report for that database is unknown rather than a shortfall
- **AND** no notice is shown for it
- **Case**: `StaleInstallTest#aForeignFormatVersionIsNotJudged`,
  `StaleInstallTest#theGoldenTypesDataFileYieldsItsNameSet` and
  `MapManagerStaleRowTest#aForeignFormatVersionCarriesNoShortfall`
