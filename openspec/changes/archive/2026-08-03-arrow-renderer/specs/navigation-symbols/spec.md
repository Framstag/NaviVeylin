## Purpose

Provides a central Compose-based renderer that draws all navigation pictograms (turn arrows, lane arrows, and direction indicators) as explicit Canvas graphics instead of Unicode text, ensuring consistent visual quality across all navigation UI surfaces.

## ADDED Requirements

### Requirement: Turn type arrow rendering

The renderer SHALL draw a directional arrow for each `TurnType` value. Arrows SHALL be rendered as Compose Canvas paths/shapes, not Unicode characters.

#### Scenario: All turn types render distinct arrows
- **WHEN** the renderer receives any `TurnType` value
- **THEN** it SHALL produce a visually distinct arrow for each: SHARP_LEFT, LEFT, SLIGHTLY_LEFT, STRAIGHT_ON, SLIGHTLY_RIGHT, RIGHT, SHARP_RIGHT, START, TARGET_REACHED, ROUNDABOUT_ENTER, ROUNDABOUT_LEAVE, MOTORWAY_ENTER
- **AND** the arrow SHALL be drawn using Compose `Canvas` or `Path` API

#### Scenario: Null turn type renders default arrow
- **WHEN** the renderer receives `null` turn type
- **THEN** it SHALL render a default straight-right arrow

#### Scenario: Arrow respects color parameter
- **WHEN** the renderer is called with a `color: Color` parameter
- **THEN** the arrow SHALL be drawn in that color

#### Scenario: Arrow respects size parameter
- **WHEN** the renderer is called with a `size: Dp` parameter
- **THEN** the arrow SHALL fit within the specified size bounds

### Requirement: Lane turn arrow rendering

The renderer SHALL draw a directional arrow for each `LaneTurn` value, supporting compound arrows (e.g., LEFT_AND_STRAIGHT shows both directions).

#### Scenario: All lane turn types render distinct arrows
- **WHEN** the renderer receives any `LaneTurn` value
- **THEN** it SHALL produce a visually distinct arrow for each: LEFT, SLIGHTLY_LEFT, SHARP_LEFT, RIGHT, SLIGHTLY_RIGHT, SHARP_RIGHT, LEFT_AND_STRAIGHT, STRAIGHT_AND_RIGHT, MERGE_TO_LEFT, MERGE_TO_RIGHT, STRAIGHT_ON, STRAIGHT_AND_SLIGHTLY_LEFT, STRAIGHT_AND_SHARP_LEFT, STRAIGHT_AND_SLIGHTLY_RIGHT, STRAIGHT_AND_SHARP_RIGHT, LEFT_AND_RIGHT, NONE, NULL, UNKNOWN

#### Scenario: Compound arrows show both directions
- **WHEN** the renderer receives a compound `LaneTurn` (e.g., LEFT_AND_STRAIGHT)
- **THEN** the rendered arrow SHALL visually indicate both directions

#### Scenario: NONE renders as dash
- **WHEN** the renderer receives `LaneTurn.NONE`
- **THEN** it SHALL render an em-dash or horizontal line

#### Scenario: NULL renders empty
- **WHEN** the renderer receives `LaneTurn.NULL`
- **THEN** it SHALL render nothing (zero-width)

#### Scenario: UNKNOWN renders as question mark
- **WHEN** the renderer receives `LaneTurn.UNKNOWN`
- **THEN** it SHALL render a question mark symbol

### Requirement: Single composable API entry point

The renderer SHALL expose a single `@Composable` function that accepts turn type or lane turn, size, color, and optional modifier, and draws the corresponding symbol.

#### Scenario: Composable renders turn type
- **WHEN** the composable is called with a `TurnType` parameter
- **THEN** it SHALL draw the corresponding turn arrow

#### Scenario: Composable renders lane turn
- **WHEN** the composable is called with a `LaneTurn` parameter
- **THEN** it SHALL draw the corresponding lane arrow

### Requirement: Duplicated code removal

All existing `turnTypeToIcon()` and `laneTurnToArrow()` functions SHALL be replaced with calls to the central renderer. The `formatDistance()` utility SHALL be consolidated into a single shared function.

#### Scenario: NextTurnOverlay uses central renderer
- **WHEN** `NextTurnOverlay` displays a turn type or lane turn
- **THEN** it SHALL use the central renderer composable instead of `turnTypeToIcon()` or `laneTurnToArrow()`

#### Scenario: RouteSummaryDialog uses central renderer
- **WHEN** `RouteSummaryDialog` displays a turn type
- **THEN** it SHALL use the central renderer composable instead of `turnTypeToIcon()`

#### Scenario: formatDistance consolidated
- **WHEN** any UI component needs distance formatting
- **THEN** it SHALL use a single shared `formatDistance()` utility function
