# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: History selection fills search box`
- TO: `### Requirement: History selection replays the search`

## MODIFIED Requirements

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

## ADDED Requirements

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
