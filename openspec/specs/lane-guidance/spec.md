# lane-guidance Specification

## Purpose

Renders per-lane turn arrows in the navigation overlay so the driver can choose the correct lane at complex junctions. Suggested lanes are highlighted.

## Requirements

### Requirement: Lane arrows in next-turn overlay

The `NextTurnOverlay` SHALL include a lane guidance row positioned between the next-turn instruction and the next-next-turn hint.

- Each lane SHALL display a directional arrow matching the `LaneTurn` enum value
- Non-oneway roads SHALL show a vertical bar `|` divider between lanes
- Suggested lanes (per `suggestedFrom`/`suggestedTo`) SHALL render in primary/accent color with bold weight
- Non-suggested lanes SHALL render in muted color with normal weight
- When lane data is absent (`count <= 0` or empty `turns`), the lane row SHALL NOT render and SHALL NOT leave a visual gap

#### Scenario: Lane arrows shown with data
- **WHEN** `NavigationListener.onLaneUpdate()` is called with `count > 0` and non-empty `turns`
- **THEN** the lane guidance row SHALL be visible
- **AND** each lane SHALL display its directional arrow
- **AND** suggested lanes SHALL be highlighted

#### Scenario: Lane arrows hidden without data
- **WHEN** `NavigationListener.onLaneUpdate()` is called with `count <= 0` or empty `turns`
- **THEN** the lane guidance row SHALL NOT be visible
- **AND** there SHALL be no visual gap

### Requirement: Lane data flow

`NavigationViewModel` SHALL store lane guidance state from `NavigationListener.onLaneUpdate` and expose it via `NavigationState` fields:

- `laneOneway: Boolean`
- `laneCount: Int`
- `laneSuggested: Boolean`
- `laneSuggestedFrom: Int`
- `laneSuggestedTo: Int`
- `laneTurns: List<LaneTurn>`

#### Scenario: Lane state updated on callback
- **WHEN** `NavigationListener.onLaneUpdate()` is called
- **THEN** `NavigationState` SHALL reflect the new lane data

### Requirement: Lane hints toggle

The app SHALL provide a user-facing toggle to enable/disable lane hints.

- Toggle SHALL be in the settings bottom sheet (`LocationOptionsOverlay`), always visible
- Toggle state SHALL be persisted in `AppSettings.laneHintsEnabled`
- When disabled, the lane hints row SHALL NOT render and SHALL NOT leave a visual gap
- Default SHALL be enabled

#### Scenario: Toggle disables lane hints
- **WHEN** user turns off "Lane instructions" in settings
- **THEN** lane hints SHALL NOT be shown during navigation
- **AND** the setting SHALL persist across app restarts

#### Scenario: Toggle enables lane hints
- **WHEN** user turns on "Lane instructions" in settings
- **THEN** lane hints SHALL be shown during navigation when data is available

### Requirement: Arrow mapping

Lane turn arrows SHALL use the same Unicode symbols as JavaScout's `laneTurnToArrow`:

| LaneTurn | Arrow |
|----------|-------|
| LEFT | ← |
| SLIGHTLY_LEFT | ↖ |
| SHARP_LEFT | ↩ |
| RIGHT | → |
| SLIGHTLY_RIGHT | ↗ |
| SHARP_RIGHT | ↪ |
| LEFT_AND_STRAIGHT | ↞← |
| STRAIGHT_AND_RIGHT | →↞ |
| MERGE_TO_LEFT | ←→ |
| MERGE_TO_RIGHT | →← |
| STRAIGHT_ON | ↑ |
| STRAIGHT_AND_SLIGHTLY_LEFT | ↖↑ |
| STRAIGHT_AND_SHARP_LEFT | ↩↑ |
| STRAIGHT_AND_SLIGHTLY_RIGHT | ↑↗ |
| STRAIGHT_AND_SHARP_RIGHT | ↑↪ |
| LEFT_AND_RIGHT | ←→ |
| NONE | — |
| NULL | (empty) |
| UNKNOWN | ? |

#### Scenario: Arrow mapping matches JavaScout
- **WHEN** a `LaneTurn` value is received
- **THEN** the displayed arrow SHALL match the JavaScout reference mapping
