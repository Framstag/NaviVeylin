## ADDED Requirements

### Requirement: The row's own read covers the whole tracker

Every read a check row is decided on SHALL cover the tracker's whole set, not a fixed window of its newest items: the
read SHALL ask for the tracker's own count plus one, and a read that comes back as full as the window it asked for
SHALL be refused with `issue-set-uncovered` rather than reported as the row's set. The label a row declares SHALL
remain the whole of its selection, so that widening the read changes which items the row can reach and not which
items it owns.

#### Scenario: A row's condition list reaches an item older than the newest page

- **WHEN** the tracker holds 401 items and the only item carrying a row's label is the oldest of them
- **THEN** the row's condition list returns that item and the fingerprint lookup over it finds the item's own record —
  case `lib#theRowReadCoversTheWholeSet`

#### Scenario: A row's read that cannot cover the counted set is refused

- **WHEN** the tracker reports a set smaller than the items the row's read comes back with, so the read fills its own
  window
- **THEN** the read exits with the `issue-set-uncovered` refusal and returns no set — case
  `lib#theRowReadCoversTheWholeSet`
