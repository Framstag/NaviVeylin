# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A state the run writes is readable from the item

Every state label the run writes SHALL be accompanied, on the same item and in the same call, by the one line
that accounts for it — the verdict and the clause of evidence that produced it — because the run's own state is
machine-local and is replaced wholesale by the next run, so a state whose reason lives only there cannot be
answered, re-checked or even understood by the item's reader (#192).

#### Scenario: A skip verdict carries its reason

- **WHEN** a worker skips an item and the run puts it on hold
- **THEN** the item carries a comment naming the verdict and the one clause of evidence the worker gave, and no
  decision label is added for it
- **Case**: `labels#holdCarriesItsReason`, `run#aSkipClearsTheInProgressMark`

#### Scenario: Nothing is written when the reason is missing

- **WHEN** a call that must record a reason and its evidence is given neither
- **THEN** it stops as a usage error before it writes anything, so no item is left in a state nobody can account
  for
- **Case**: `labels#holdCarriesItsReason`

