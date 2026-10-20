# stale-citation-resolution — deltas

## MODIFIED Requirements

### Requirement: A citation is repaired when the repair is mechanical

The resolver SHALL repair a citation whose target can be established as a fact, in this order: a unique suffix
of a tracked path (canonicalised to that path), a rename recorded in the repository's history, or a single
surviving file that declares the cited symbol. A repair SHALL rewrite the citation token and nothing else, and
SHALL preserve every body marker verbatim. A basename repair SHALL NOT rewrite an occurrence that stands inside a
longer path. A repair is a change to the record: the item SHALL count as repaired, and a comment SHALL claim a
repair, only when the produced body differs from the input **and** every rewritten citation re-classifies as no
longer `absent`; a rewrite that fails either test SHALL be reported as unresolved and the item parked instead.

#### Scenario: A suffixed path is canonicalised

- **WHEN** a body cites a class-file name that ends exactly one tracked path
- **THEN** the citation is rewritten to that full path
- **Case**: `resolver#suffixedCitationIsCanonicalised`

#### Scenario: A renamed file is followed

- **WHEN** the cited path no longer exists and the repository's history records a rename or a move of it
- **THEN** the citation is rewritten to its successor
- **Case**: `resolver#renamedFileIsFollowed`

#### Scenario: A removed symbol is re-anchored

- **WHEN** the cited file is gone and exactly one surviving file declares the cited symbol
- **THEN** the citation is rewritten to that file
- **Case**: `resolver#removedSymbolIsReAnchored`

#### Scenario: The item's record survives the repair

- **WHEN** a citation is rewritten
- **THEN** the body's `fingerprint` and `nvid` markers are byte-identical to what they were, and no prose around
  the citation is changed
- **Case**: `resolver#markersSurviveTheRepair`

#### Scenario: A repair is recorded

- **WHEN** a citation is rewritten and the repair is verified
- **THEN** one comment on the item names the citation before and after the repair, and the evidence for it
- **Case**: `resolver#repairPostsOneComment`

#### Scenario: A basename repair leaves a longer path alone

- **WHEN** a body cites a bare file name whose only other occurrence stands inside a longer citation — the
  doubled-directory form — and both are stale in the same body
- **THEN** the bare occurrence is canonicalised and the longer citation is collapsed, not re-doubled, so every
  citation in the written body resolves
- **Case**: `resolver#basenameRepairLeavesALongerPathAlone`

#### Scenario: A rewrite that changes nothing is not a repair

- **WHEN** the repair ladder attempts a rewrite whose result is the input again
- **THEN** nothing is counted as repaired, no comment claims a repair, and the item is parked with the reason
- **Case**: `resolver#repairWithoutAChangeIsNotReported`

#### Scenario: A repair is verified by re-classification

- **WHEN** a rewrite replaces the cited token with a target the repository does not hold
- **THEN** the citation is not counted as repaired and the item is parked with the reason, so the record is not
  claimed to be repaired again
- **Case**: `resolver#repairIsVerifiedByReclassification`
