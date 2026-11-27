## ADDED Requirements

### Requirement: The set a pass reads is counted before it is read

A whole-set read SHALL ask the tracker how many items it holds — all states, no label scope — and SHALL ask the list for exactly that count plus one, so the window is the tracker's own number rather than a constant of the tooling, and a read that comes back full is refused as `issue-set-uncovered` instead of being reported as coverage of the set.

#### Scenario: The pass reads every item the tracker holds

- **WHEN** the tracker holds more items than the window the pass used to ask for, and the oldest of them is due for a recomputation
- **THEN** the read covers every item the tracker counted, the pass reports that number as what it read, and the oldest item is among the examined records
- **Case**: `prioritise#readsEveryIssueNotTheNewestPage`

#### Scenario: A read the counted set cannot cover is refused

- **WHEN** items are filed between the count and the read, so the list comes back as full as the window it was asked for
- **THEN** nothing is examined and the refusal names `issue-set-uncovered`, stating the counted set and the read
- **Case**: `prioritise#readsEveryIssueNotTheNewestPage`
