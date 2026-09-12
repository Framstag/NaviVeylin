# search-history Delta

## MODIFIED Requirements

### Requirement: Select from history entry on empty search box

When the search box is empty in Places mode, the search dialog SHALL show recent searches as suggestion chips in addition to the favorite rows and the "Current Location" row. Typing a query SHALL hide them; clearing the query SHALL restore them.

#### Scenario: Entry visible on empty query

- **WHEN** the search dialog opens in Places mode with an empty search box
- **THEN** recent-search chips SHALL be visible

#### Scenario: Entry hidden while typing

- **WHEN** the user types a query
- **THEN** the recent-search chips SHALL be hidden
- **AND** only location search results SHALL be listed

#### Scenario: Entry restored on clear

- **WHEN** the user clears the search box
- **THEN** the recent-search chips SHALL reappear

### Requirement: History view lists entries youngest first

The recent-search chips SHALL be ordered youngest first. The chip row SHALL scroll horizontally when the list exceeds the available space.

#### Scenario: History view opens

- **WHEN** the search dialog shows recent-search chips
- **THEN** the most recently recorded entry SHALL be shown first

#### Scenario: Long history scrolls

- **WHEN** the history contains more entries than fit in the chip row
- **THEN** the chip row SHALL scroll to reveal all entries

### Requirement: History selection fills search box

Selecting a recent-search chip SHALL take over the search string: the search box SHALL be filled with the entry's search text.

#### Scenario: Selection takes over search string

- **WHEN** the user taps a recent-search chip with search text "Café Central"
- **THEN** the search box SHALL contain "Café Central"
