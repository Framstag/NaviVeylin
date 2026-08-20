# next-turn-overlay Specification (Delta)

## Purpose

Shows the next manoeuvre during active navigation — turn type icon, distance, street name, and a "next next" hint for close-following turns.

## MODIFIED Requirements

### Requirement: Text wrapping

Next-turn description and next-next-turn description SHALL support line wrapping.

#### Scenario: Long description wraps

- **WHEN** the instruction description exceeds the available width
- **THEN** the text SHALL wrap to a second line
- **AND** overflow SHALL be indicated with an ellipsis

#### Scenario: Break between generic instruction and destination name

- **WHEN** the instruction description exceeds the available width
- **THEN** the line break SHALL fall between the generic instruction (e.g. "Turn left") and the actual destination name (e.g. "Hauptstrasse")
- **AND** the destination name SHALL start on its own line
- **AND** the destination name SHALL wrap independently if it still does not fit

#### Scenario: No destination name

- **WHEN** the instruction has no destination name (e.g. "Enter roundabout")
- **THEN** the full description SHALL be shown as a single wrapped line

### Requirement: Next-next turn smaller

Next-next turn text SHALL use smaller typography than the next-turn text.

#### Scenario: Next-next smaller font

- **WHEN** the next-next hint is displayed
- **THEN** its text SHALL use a smaller font size than the next-turn instruction

#### Scenario: Next-turn instruction larger font

- **WHEN** the next-turn instruction is displayed
- **THEN** its description SHALL use a larger font size than the previous `bodyLarge` style (e.g. `titleMedium`)

#### Scenario: Next-next instruction larger font

- **WHEN** the next-next hint is displayed
- **THEN** its description SHALL use a larger font size than the previous `bodySmall` style (e.g. `bodyMedium`)
- **AND** it SHALL remain smaller than the next-turn instruction
