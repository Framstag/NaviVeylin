## ADDED Requirements

### Requirement: A filing's title names the drift it reports

Where a check files an item for a condition, and one run reports several conditions of the same row, every
filing SHALL carry a title composed from that condition's own fields, so two conditions of one row are two
titles a reader can tell apart without opening either item. The title SHALL name the condition — the item and
the disagreement it carries, the file and its measurement, or the failing job and case — SHALL NOT be a
constant string shared by the row's conditions, and the condition's identity SHALL NOT change to obtain it.

#### Scenario: Two drifted items are filed under two titles

- **WHEN** a run of the priority re-read reports two items whose own record and label disagree, and files both
- **THEN** it files two conditions whose titles differ and each names its own item and disagreement, and each
  keeps the fingerprint of the item and the disagreement it carries
- **Case**: `sensors#twoDriftedItemsFileUnderTwoTitles`

#### Scenario: Two files past the size bound are filed under two titles

- **WHEN** a run of the source-size check reports two files past the row's bound, and files both
- **THEN** it files two conditions whose titles differ, each naming its own path and line count
- **Case**: `sensors#twoOversizedFilesFileUnderTwoTitles`

#### Scenario: Two failing cases are filed under two titles

- **WHEN** a red main is reported for one failing case and then for another, and the run files each
- **THEN** the two filings are two items whose titles differ, each naming the failing job and the case
- **Case**: `sensors#twoFailingCasesFileUnderTwoTitles`
