# agent-issue-loop

## MODIFIED Requirements

### Requirement: The run acts only on authorised, eligible items

The run SHALL refuse to start when the identity it runs as is not authorised, SHALL select only items that
are eligible for automated work, and SHALL never write the authorisation label itself. A candidate the
pre-screen refuses for a reason the item cannot escape by itself SHALL leave that refusal on the item in the
tracker, so the item's state says why it is not worked after the run's own state is gone.

#### Scenario: An unauthorised identity stops the run before any work

- **WHEN** the identity the run acts as is not on the allowlist
- **THEN** it stops before creating anything, and no branch, label or state is written

#### Scenario: An item without the opt-in is not worked

- **WHEN** the eligible set is selected
- **THEN** an item whose owner has not applied the opt-in is never worked, however defect-shaped it is

#### Scenario: The run cannot authorise itself

- **WHEN** the run writes a label
- **THEN** the authorisation label is not among the labels it can write, and no code path of the run
  writes it

#### Scenario: A gated item is recorded, not silently dropped

- **WHEN** a candidate is rejected
- **THEN** its verdict and the one clause of evidence for it are recorded, so the rejection is part of the
  run's output

#### Scenario: A refusal the item cannot escape is written to the tracker

- **WHEN** the pre-screen refuses a candidate as `stale`, `class-feature` or `settled`
- **THEN** the item carries the verdict's refusal label (`agent/excluded-stale`, `agent/excluded-feature`,
  `agent/excluded-settled`) after the run, so the owner finds it with one query instead of reading a report
- **AND** the authorisation label and the status are left as the owner set them — the opt-in is neither
  withdrawn nor re-written
- **AND** a dry run writes no such label

#### Scenario: A refusal that no longer holds is withdrawn

- **WHEN** a later pre-screen reaches the same item and its verdict is no longer a refusal
- **THEN** the refusal label is removed, so a repaired citation or a re-classified item is worked rather than
  frozen by a label no run re-reads
