# Search History (search-history)

## Purpose

Record every search the user commits to — by selecting a result for display or routing — and let them re-run past searches from a scrollable, most-recent-first history list.

## Requirements

### Requirement: History entry recorded on result selection

A history entry SHALL be recorded whenever the user selects a search result, whether the selection is for display (map search panel: center map, show details) or for routing (route panel: set start or destination). Typing a query or viewing results SHALL NOT record an entry.

#### Scenario: Display selection records entry

- **WHEN** the user searches for "Dortmund Hbf" in the map search panel
- **AND** selects a result
- **THEN** a history entry with search text "Dortmund Hbf" SHALL be recorded

#### Scenario: Routing selection records entry

- **WHEN** the user searches for a destination in the route panel
- **AND** selects a result as the route destination
- **THEN** a history entry with the search text SHALL be recorded

#### Scenario: Typing alone records nothing

- **WHEN** the user types a query in the search box
- **AND** dismisses the search panel without selecting a result
- **THEN** no history entry SHALL be recorded

### Requirement: History entry content

Each history entry SHALL contain the search text and the date of the selection. The date SHALL be the moment the result was selected.

#### Scenario: Entry stores text and date

- **WHEN** a history entry is recorded
- **THEN** the entry SHALL contain the exact search text that produced the selected result
- **AND** the entry SHALL contain the selection date

### Requirement: History capped at 50 entries

The history SHALL hold at most 50 entries. When a new entry would exceed the cap, the oldest entry SHALL be dropped.

#### Scenario: Cap enforced at 50

- **WHEN** the history contains 50 entries
- **AND** a new entry is recorded
- **THEN** the history SHALL contain 50 entries
- **AND** the oldest entry SHALL be removed

#### Scenario: Below cap keeps all entries

- **WHEN** the history contains fewer than 50 entries
- **AND** a new entry is recorded
- **THEN** the new entry SHALL be added
- **AND** no existing entry SHALL be removed

### Requirement: History persists across restarts

The history SHALL survive app restarts. Entries recorded in a previous session SHALL be available when the app is next started.

#### Scenario: Entries survive restart

- **WHEN** the user records history entries
- **AND** the app is fully restarted
- **THEN** the previously recorded entries SHALL still be present

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
