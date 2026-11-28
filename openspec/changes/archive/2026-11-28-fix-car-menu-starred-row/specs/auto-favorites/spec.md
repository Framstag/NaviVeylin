## ADDED Requirements

### Requirement: The group level carries the starred entry first

When the stored starred order is non-empty, the all-favorites group level SHALL carry the starred level's entry point as its first row: part of the ordered sequence the page row partitions, never a fixed header, so a level that pages loses no group to it. The row shows the starred favorites' count under the wording the group rows use, and it opens the starred level, which keeps its own screen and stored order. While nothing is starred, the level carries its groups alone.

#### Scenario: The starred entry leads the group level

- **WHEN** the all-favorites group level is shown and the stored starred order is non-empty
- **THEN** its first row is the starred entry, under the same count wording the group rows use
- **AND** the row is clickable and the group rows follow it in their stored order
- **AND** the row takes part in the page partition, so a level that pages shows it on the first page and loses no group to it
- **Case**: `auto: FavoritesScreenTest.theGroupLevelCarriesTheStarredEntryFirst`

#### Scenario: No starred entry while nothing is starred

- **WHEN** the all-favorites group level is shown and nothing is starred
- **THEN** the level carries its groups alone, with no starred row
- **Case**: `auto: FavoritesScreenTest.noStarredEntryWhileNothingIsStarred`

#### Scenario: The starred entry opens the starred level

- **WHEN** the user taps the starred entry on the group level
- **THEN** the starred level opens, headed with the starred wording and listing the starred favorites in their stored order
- **Case**: `auto: FavoritesScreenTest.theStarredEntryOpensTheStarredLevel`
