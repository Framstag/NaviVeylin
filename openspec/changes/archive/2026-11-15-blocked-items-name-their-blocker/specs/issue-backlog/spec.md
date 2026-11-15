# issue-backlog delta

## ADDED Requirements

### Requirement: A dependency is the tracker's own relation

An item's predecessors SHALL be the tracker's own dependency edge: the create path SHALL write it with `addBlockedBy`
from `--blocked-by` or the finding's `.blockedBy`, SHALL read every predecessor before the filing, and SHALL refuse a
predecessor the tracker does not hold, a non-numeric one, and the item's own parent. The relation SHALL NOT be carried
by a line of the item's body.

#### Scenario: A filed item's predecessor is the tracker's edge

- **WHEN** a caller files a finding with `--blocked-by 90`, or states `.blockedBy` in the finding's own JSON
- **THEN** the writer reads the predecessor's node id and writes `addBlockedBy` for the filed item, so the relation is
  the tracker's and no line of the body carries it
- **AND** the same predecessor named twice is one edge, and the filing's own result reports the predecessors it wrote
- **Case**: `create#writesTheDependencyItWasGiven`

#### Scenario: An edge that would block nothing is refused before the item exists

- **WHEN** a caller names a predecessor the tracker does not hold, a value that is not an issue number, or the item's
  own parent
- **THEN** the writer refuses with a usage error and writes nothing — no item, no edge — because an edge on an item
  nobody filed blocks nothing and a parent holds an item rather than preceding it
- **Case**: `create#writesTheDependencyItWasGiven`

#### Scenario: The profile declares the verdict a blocked candidate gets

- **WHEN** the profile's `roles.blockedVerdict` names a value, is empty, or is omitted
- **THEN** the pre-screen excludes a candidate the tracker holds blocked under that value followed by `:#N`, and an
  empty value or an omitted key blocks nothing
- **AND** the key is optional, so a profile written before it existed keeps running instead of stopping the screen
- **Case**: `prescreen#anEmptyBlockedVerdictBlocksNothing`