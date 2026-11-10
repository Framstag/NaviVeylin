# Spec Delta — store-listing-rendering

## ADDED Requirements

### Requirement: A store listing fits the field it is pasted into

The run SHALL derive a third generated document, the store listing, from the same version entry as the release note:
one section per release version, at most `listing_budget` characters of bullet text, the changes ordered by user
impact, and every change either published, trimmed to its whole leading sentences, or summarised under one general
bullet that states how many it stands for. A `<!-- cap: … -->` line SHALL be published whole or not at all, and the
document SHALL state the budget it was fitted to.

#### Scenario: An over-budget entry is trimmed at a sentence boundary

- **WHEN** a version entry's bullets exceed the budget and one bullet's later sentences do not fit
- **THEN** the listing publishes that bullet's whole leading sentences and no fragment of the sentence that did not
  fit, and every published bullet ends at a sentence boundary
- **Case**: `listing: an over-budget entry is trimmed at a sentence boundary`

#### Scenario: A source marker is never truncated

- **WHEN** a bullet is shortened to fit the budget
- **THEN** the `<!-- cap: … -->` lines under it are published whole — the marker the item's probe found `head -c 500`
  stopping inside
- **Case**: `listing: a trimmed bullet keeps its source comment whole`

#### Scenario: The listing leads with the changes a user can see

- **WHEN** the entry lists a change whose spec the shortlist does not name before one whose spec it does
- **THEN** the listing publishes the shortlisted change first, because the shortlist is the tree's record of what a
  user can see or feel, and publishes the other after it when the budget still holds it
- **Case**: `listing: the listing leads with the shortlisted change`

#### Scenario: A change that does not fit is summarised, not dropped

- **WHEN** neither a change's whole bullet nor any leading sentence of it fits the budget
- **THEN** the listing publishes one general bullet, `General improvements and optimisations`, carrying the count of
  the changes it stands for, and the section remains at or under the budget
- **Case**: `listing: a change that does not fit is summarised not dropped`

#### Scenario: The listing is derived, and a version is replaced in place

- **WHEN** the same version is generated again
- **THEN** that version's section is replaced byte for byte
- **Case**: `listing: re-running a version replaces its section byte for byte`

#### Scenario: A version with no entry writes no section

- **WHEN** a version's changes are all internal, so the release note owes no entry
- **THEN** the listing gains no section, the sections it already carries are left byte for byte, and the run reports
  the empty version
- **Case**: `listing: a version with no entry writes no section`
