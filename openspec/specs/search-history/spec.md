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

### Requirement: History selection replays the search

Selecting a recent-search chip SHALL take over the search string and replay the search: the search box SHALL be filled with the entry's search text and the search SHALL run for that text, listing the same results as typing it. This SHALL hold whatever the previous search was — including when the chip's text equals the query of the search the user committed last, and after a search result was selected and the dialog was reopened. Android Auto already replays a tapped recent search (spec: `auto-search-suggestions` — "History tap runs the search"); the phone dialog SHALL match it, from the same history store (parity: same entries, same result for the same text).

#### Scenario: Selection takes over search string

- **WHEN** the user taps a recent-search chip with search text "Café Central"
- **THEN** the search box SHALL contain "Café Central"

#### Scenario: Selection runs the search

- **WHEN** the user taps a recent-search chip with search text "Bochum"
- **THEN** the search SHALL run for "Bochum"
- **AND** the results area SHALL list the same results as typing "Bochum" produces

#### Scenario: Replaying the last committed search

- **WHEN** the user searched "Bochum", selected a result, and reopened the search dialog
- **AND** the user taps the "Bochum" chip
- **THEN** the results area SHALL list the search results for "Bochum"
- **AND** it SHALL NOT remain empty

#### Scenario: Chip after a dismissal without selection

- **WHEN** the user typed "Bochum" without selecting a result and dismissed the search dialog
- **AND** the user clears the search box and taps the "Bochum" chip
- **THEN** the results area SHALL list the search results for "Bochum"

### Requirement: No duplicate entry for the same search text

Recording a search whose text already exists in the history SHALL move that entry to the front of the history instead of adding a second entry, so the chip row never shows the same text twice. The youngest-first order and the 50-entry cap SHALL be unaffected.

#### Scenario: Repeating a search moves its entry to the front

- **WHEN** the history contains "Essen" as the newest entry and "Bochum" as an older one
- **AND** the user searches "Bochum" and selects a result
- **THEN** the history SHALL contain exactly one entry with the text "Bochum"
- **AND** that entry SHALL be the newest entry

#### Scenario: Chip row shows each text once

- **WHEN** the user commits the search "Bochum" three times in one session
- **THEN** the recent-search chip row SHALL show a "Bochum" chip exactly once

#### Scenario: Cap still enforced after a repeat

- **WHEN** the history contains 50 entries and the oldest one is "Bochum"
- **AND** the user searches "Bochum" and selects a result
- **THEN** the history SHALL contain 50 entries
- **AND** no entry SHALL be dropped, because the repeated text reuses the existing entry

### Requirement: Duplicate entries are collapsed when the history is loaded

A history already persisted with repeated texts SHALL be collapsed when it is loaded: one entry per text, carrying the newest recorded date for that text. The stored file format SHALL NOT change.

#### Scenario: Persisted duplicates collapse on load

- **WHEN** the stored history contains "Bochum" twice, with different dates
- **AND** the history is loaded
- **THEN** only one entry with the text "Bochum" SHALL remain
- **AND** it SHALL carry the newer of the two dates

#### Scenario: Order is preserved when collapsing

- **WHEN** the stored history contains "Bochum" as its newest entry and again as an older entry
- **AND** the history is loaded
- **THEN** the remaining "Bochum" entry SHALL be the newest entry of the loaded history

#### Scenario: An out-of-order file cannot keep a stale date

- **WHEN** the stored history holds the same text twice with the newer entry stored after the older one
- **AND** the history is loaded
- **THEN** the remaining entry SHALL carry the newer of the two dates
