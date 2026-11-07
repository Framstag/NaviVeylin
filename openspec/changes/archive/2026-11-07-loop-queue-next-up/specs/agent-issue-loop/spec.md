## ADDED Requirements

### Requirement: A next-up label leads the queue and decides nothing else

The pre-screen SHALL rank an item carrying the profile's `roles.nextUpAgent` label ahead of every other item,
in the class order and issue-number order the other streams keep, and SHALL leave every other item's relative
order unchanged. The label SHALL be a rank, never a gate, and SHALL be read from the profile, so a project that
declares it empty ranks exactly as it does without the key.

#### Scenario: A next-up item leads the queue the interval would end

- **WHEN** the pre-screen ranks a backlog whose eligible items include an improvement in a leading category that
  carries the next-up label, beside a leading-category defect and an unpinned app bug
- **THEN** the labelled item takes rank 1 — the position the class order and the issue number give it among the
  labelled items only — and the unpinned items keep their own relative order behind it
- **Case**: `prescreen#aPinnedItemLeadsTheQueue`

#### Scenario: The label is a rank, not a gate

- **WHEN** an item carrying the next-up label is on hold, or its author is not authorised
- **THEN** it is excluded exactly as it would be without the label, writes no other label and takes no rank
- **Case**: `prescreen#aPinnedItemLeadsTheQueue`

#### Scenario: A project that declares no next-up label ranks on the keys alone

- **WHEN** the profile declares the key empty, or omits it
- **THEN** the category key, the class order and the issue number decide the queue alone, without stopping the
  pre-screen on a key the project never declared
- **Case**: `prescreen#aProfileWithNoPinPinsNothing`
