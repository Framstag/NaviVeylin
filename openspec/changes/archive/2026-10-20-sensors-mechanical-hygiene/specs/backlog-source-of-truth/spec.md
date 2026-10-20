# backlog-source-of-truth — deltas

## ADDED Requirements

### Requirement: One fingerprint, one item

The tracker SHALL NOT keep two open items that carry the same fingerprint marker, and a duplicate that the
filing path did not catch SHALL be found by a sweep that names the surviving item rather than by a person
remembering to look.

#### Scenario: A duplicate is found after the fact

- **WHEN** two open items carry the same `<!-- fingerprint: … -->` marker
- **THEN** the sweep names both, keeps the older item, and closes the younger one with the survivor named in
  the reason and the evidence
- **Case**: `duplicates#youngerDuplicateIsClosedWithAPointer`

#### Scenario: The sweep never widens the authorisation

- **WHEN** the younger item of a duplicate group does not carry the label that authorises automated work
- **THEN** the sweep writes the pointer comment only and leaves the item open; the closure stays a person's,
  and `close.sh`'s refusal (`missing-eligibility-label`) is not worked around
- **Case**: `duplicates#unauthorisedDuplicateIsCommentedNotClosed`

#### Scenario: A duplicate the fingerprint cannot see stays a person's call

- **WHEN** two items describe one finding but their fingerprints differ
- **THEN** the sweep reports nothing about them — the fingerprint is the only duplicate criterion it applies,
  and a judgement merge stays with `cleanup-todo`
- **Case**: `duplicates#differentFingerprintsAreNotMerged`

#### Scenario: The identity markers are read, never rewritten

- **WHEN** the sweep reads `<!-- fingerprint: … -->` and `<!-- nvid: N -->`
- **THEN** it writes neither, on either item, in either outcome — the fingerprint is what makes a duplicate
  findable and the `nvid` is what a `§N` citation resolves through
- **Case**: `duplicates#collisionIsReportedAndNothingIsWritten`
