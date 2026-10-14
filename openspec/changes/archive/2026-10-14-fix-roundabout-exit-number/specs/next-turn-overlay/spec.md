# Spec Delta

## MODIFIED Requirements

### Requirement: Turn type icon

The overlay SHALL display a visual icon representing the turn type (left, right, straight, roundabout, etc.).
For a roundabout exit the icon SHALL additionally identify which exit the route takes: it SHALL show the exit
ordinal on the symbol whenever the instruction carries one, and SHALL fall back to the plain roundabout icon
when it does not.

#### Scenario: Left turn icon

- **WHEN** the next instruction has `turnType = LEFT`
- **THEN** the overlay SHALL show a left-turn icon

#### Scenario: Roundabout icon

- **WHEN** the next instruction has `turnType = ROUNDABOUT_ENTER`
- **THEN** the overlay SHALL show a roundabout icon

#### Scenario: Roundabout exit icon carries the exit number

- **WHEN** the next instruction has `turnType = ROUNDABOUT_LEAVE` and carries exit ordinal 3
- **THEN** the overlay SHALL show a roundabout-exit icon carrying the number 3
- **AND** the number SHALL be the one the overlay's instruction text names
- **Case**: `app: NextTurnOverlayTest` "a roundabout exit icon carries the exit number"

#### Scenario: Roundabout exit without an exit number

- **WHEN** the next instruction has `turnType = ROUNDABOUT_LEAVE` and carries no exit ordinal
- **THEN** the overlay SHALL show the plain roundabout icon it showed before the number existed
- **Case**: `app: NextTurnOverlayTest` "a roundabout exit without an ordinal keeps the plain leave arrow"

#### Scenario: Next-next roundabout exit carries its own number

- **WHEN** the next-next hint is a roundabout exit carrying an ordinal
- **THEN** the next-next row SHALL show the exit number of *that* manoeuvre
- **AND** it SHALL NOT show the number of the next-manoeuvre row
- **Case**: `app: NextTurnOverlayTest` "the next-next roundabout exit shows its own number"
