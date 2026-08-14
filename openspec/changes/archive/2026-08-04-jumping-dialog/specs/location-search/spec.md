# Location Search — Delta: Stable Sheet Height

## Modified Requirements

### R4: Stable sheet height (replaces "Draggable bottom-sheet search panel")
The search bottom sheet SHALL maintain a fixed minimum height from open to dismiss. Sheet height SHALL NOT change when results load, update, or clear. Content exceeding the allocated space SHALL scroll internally.

#### Scenario: Sheet height stable during search lifecycle
- **WHEN** the search panel opens
- **THEN** the sheet SHALL display at its minimum height immediately
- **AND** the height SHALL remain constant while the user types, results load, results display, or results clear
- **AND** the sheet SHALL NOT resize when transitioning between empty, loading, results, and no-results states

#### Scenario: Many results scroll internally
- **WHEN** search returns more results than fit in the allocated space
- **THEN** the result list SHALL scroll within the sheet
- **AND** the sheet SHALL NOT expand to show additional items

#### Scenario: Map visible behind sheet
- **WHEN** the search panel is open
- **THEN** the map SHALL remain partially visible behind the sheet
- **AND** the sheet height SHALL leave at least 30% of the screen visible for the map
