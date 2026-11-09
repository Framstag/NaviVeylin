# store-listing-rendering Specification

## Purpose
The project publishes, for each release version, a store-listing block for the store field's "What's new" space,
derived from the same version entry the release note carries — one field, one block, held to the field's budget — so
the published copy cannot drift from the specs.

## Requirements

### Requirement: The store listing is derived from the version entry

A run SHALL render, from the same version entry the release note carries, a store-listing block for that version in
`STORE-LISTING.md` at the repository root. The block SHALL be derived from that entry's bullets alone — no second
selection, no model call, and no sentence the note does not carry — and re-running the same version SHALL replace its
block with an identical one.

#### Scenario: A run writes the listing for the version

- **WHEN** a run writes an entry for a version
- **THEN** the listing document carries one block for that version, whose lines are that entry's bullets
- **Case**: `listing: a run writes the listing for the version`

#### Scenario: Re-running a version replaces its block byte for byte

- **WHEN** a run for a version is repeated with no spec change in between
- **THEN** the listing document holds exactly one block for that version, byte-identical to the first
- **Case**: `listing: re-running a version replaces its block byte for byte`

### Requirement: A block fits the store field's character budget

A version's listing block SHALL NOT exceed the character budget of the store field it is pasted into. The budget
SHALL be one recorded value declared in a single place in the tool, with the source of the figure beside it, and the
run SHALL report the budget it enforced.

#### Scenario: The block fits the declared budget

- **WHEN** a listing block is written
- **THEN** its text is no longer than the budget the tool declares
- **Case**: `listing: the block fits the declared budget`

#### Scenario: The run reports the budget it enforced

- **WHEN** a run writes a listing block
- **THEN** its report states the character budget that block was held to
- **Case**: `listing: the run reports the budget it enforced`

### Requirement: An over-budget entry is trimmed by whole bullets and summarised

When the entry's bullets do not fit the budget, the run SHALL drop whole bullets and SHALL NOT cut inside a bullet's
paragraph or inside a capability comment. The changes that were not published SHALL be named by one general bullet,
never dropped silently, and the run's report SHALL state how many changes it published.

#### Scenario: An over-budget entry is trimmed at a sentence boundary

- **WHEN** an entry's bullets exceed the budget
- **THEN** every published line is one whole bullet, ending at its own full stop, and no line is a truncated
  paragraph or a partial capability comment
- **Case**: `listing: an over-budget entry is trimmed, never truncated mid-sentence`

#### Scenario: The changes that did not fit are summarised

- **WHEN** at least one bullet was dropped for the budget
- **THEN** the block ends with one general line naming the remaining changes, and the report states how many of the
  entry's changes it published
- **Case**: `listing: the overflow is summarised in one general bullet`

#### Scenario: A block carries no capability comment

- **WHEN** any block is written
- **THEN** it carries no capability comment, whole or partial
- **Case**: `listing: no block carries a capability comment`

### Requirement: Bullets are published in order of user impact

A bullet SHALL be published in order of user impact: a capability the shortlist names as a selling point SHALL rank
above one it does not, the shortlist's own order breaking ties, and the entry's order after that.

#### Scenario: A shortlisted capability ranks above an unlisted one

- **WHEN** an entry carries both a shortlisted capability and an unlisted one, and the budget publishes only part of
  the entry
- **THEN** the shortlisted capability is published and the unlisted one is the one withheld
- **Case**: `listing: a shortlisted capability ranks above an unlisted one`

### Requirement: A per-area share composes to the entry's budget

When the area catalogue declares a per-area share of the listing budget, the declared shares SHALL sum to no more
than the field's budget, because one store field is one block and not one field per area. Shares that exceed it SHALL
fail the run, naming the sum and the budget, and the run SHALL write no listing.

#### Scenario: A per-area share that does not compose fails the run

- **WHEN** the area catalogue's declared shares sum above the field's budget
- **THEN** the run fails, names the sum and the budget, and writes no listing document
- **Case**: `listing: a per-area share that does not compose fails the run`

### Requirement: Nothing user-visible writes no block

A version whose changed user-visible capabilities are empty SHALL write no listing block and SHALL report that
nothing user-visible changed, exactly as the release note does for that version.

#### Scenario: An internal-only version writes no block

- **WHEN** a version's changed capabilities are all classified internal
- **THEN** the listing document gains no block for that version and the run reports that nothing user-visible changed
- **Case**: `listing: nothing user-visible writes no block`
