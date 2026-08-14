# Lane Guidance

## REQUIREMENTS

### Requirement: Lane arrows in next-turn overlay

The `NextTurnOverlay` SHALL include a lane guidance row positioned between the next-turn instruction and the next-next-turn hint.

- Each lane SHALL display a directional arrow matching the `LaneTurn` enum value
- Non-oneway roads SHALL show a vertical bar `|` divider between lanes
- Suggested lanes (per `suggestedFrom`/`suggestedTo`) SHALL render in primary/accent color with bold weight
- Non-suggested lanes SHALL render in muted color with normal weight
- When lane data is absent (`count <= 0` or empty `turns`), the lane row SHALL NOT render and SHALL NOT leave a visual gap

### Requirement: Lane data flow

`NavigationViewModel` SHALL store lane guidance state from `NavigationListener.onLaneUpdate` and expose it via `NavigationState` fields:

- `laneOneway: Boolean`
- `laneCount: Int`
- `laneSuggested: Boolean`
- `laneSuggestedFrom: Int`
- `laneSuggestedTo: Int`
- `laneTurns: List<LaneTurn>`

### Requirement: Lane hints toggle

The app SHALL provide a user-facing toggle to enable/disable lane hints.

- Toggle SHALL be in the settings bottom sheet (`LocationOptionsOverlay`), visible only during active navigation
- Toggle state SHALL be persisted in `AppSettings.laneHintsEnabled`
- When disabled, the lane hints row SHALL NOT render and SHALL NOT leave a visual gap
- Default SHALL be enabled

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
