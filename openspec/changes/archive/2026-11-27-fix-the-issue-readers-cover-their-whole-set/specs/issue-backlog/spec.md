## ADDED Requirements

### Requirement: A reader that decides on the tracker reads the set it counted

Every issue-side reader that decides on the tracker SHALL read the set its own count reported, not a fixed window
of the newest items: it SHALL count the set it works on (`states: [OPEN, CLOSED]` for the tracker, `states: [OPEN]`
for the backlog), read at that count plus one, and refuse a read that comes back as full as the window it asked for
— `issue-set-uncovered` — rather than report a set it did not cover. A reader offered a narrowing flag SHALL keep
the refusal attached to it, so no flag value can turn a partial read into a claim about the tracker.

#### Scenario: A duplicate pair below the newest page is found and closed

- **WHEN** the tracker holds 405 items and a duplicate pair sits among the five oldest, outside the newest 400
- **THEN** `tools/issues/duplicates.sh` reports the pair, names the younger items in its JSON, and closes the
  authorisable one — case `duplicates#aDuplicateOutsideTheNewestPageIsFound`

#### Scenario: The live metadata check reads the whole tracker

- **WHEN** `tools/issues/check-metadata.sh --live` reads a tracker of 405 items in which the untyped child of a
  container is among the oldest five
- **THEN** it counts 405 issues, exits non-zero and names the child and its container — case
  `check#theLiveReadIsTheWholeSet`

#### Scenario: A read that fills its own window is refused

- **WHEN** the tracker reports a set larger than the items a read comes back with
- **THEN** the reader exits with the `issue-set-uncovered` refusal and prints no count over the set it did not
  cover — case `check#theLiveReadIsTheWholeSet` under its mutation (`evidence/red-when-mutated.log`)

LOOP-ROUTE: guidelines/BacklogTooling.md §9
