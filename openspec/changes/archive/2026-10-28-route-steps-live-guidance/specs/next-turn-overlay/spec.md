# next-turn-overlay

## MODIFIED Requirements

### Requirement: Turn type icon

The overlay SHALL display a visual icon representing the manoeuvre the step ahead describes (left, right,
straight, roundabout, motorway, etc.), chosen from that step's manoeuvre kind and move and never from any text.
For a roundabout exit the icon SHALL additionally identify which exit the route takes: it SHALL show the exit
ordinal on the symbol whenever the step carries one, and SHALL fall back to the plain roundabout icon when it
does not.

#### Scenario: Left turn icon

- **WHEN** the step ahead is a left turn
- **THEN** the overlay SHALL show a left-turn icon

#### Scenario: Roundabout icon

- **WHEN** the step ahead enters a roundabout
- **THEN** the overlay SHALL show a roundabout icon

#### Scenario: Roundabout exit icon carries the exit number

- **WHEN** the step ahead leaves a roundabout at exit 3
- **THEN** the overlay SHALL show a roundabout-exit icon carrying the number 3
- **AND** the number SHALL be the one the overlay's instruction text names
- **Case**: `app: NextTurnOverlayTest` "a roundabout exit icon carries the exit number"

#### Scenario: Roundabout exit without an exit number

- **WHEN** the step ahead leaves a roundabout and carries no exit ordinal
- **THEN** the overlay SHALL show the plain roundabout icon it showed before the number existed
- **Case**: `app: NextTurnOverlayTest` "a roundabout exit without an ordinal keeps the plain leave arrow"

#### Scenario: Next-next roundabout exit carries its own number

- **WHEN** the next-next hint is a roundabout exit carrying an ordinal
- **THEN** the next-next row SHALL show the exit number of *that* manoeuvre
- **AND** it SHALL NOT show the number of the next-manoeuvre row
- **Case**: `app: NextTurnOverlayTest` "the next-next roundabout exit shows its own number"

#### Scenario: A motorway change is not drawn as a plain turn

- **WHEN** the step ahead keeps left onto another motorway
- **THEN** the overlay SHALL show the motorway-change icon rather than a plain left-turn icon
- **Case**: `app: NextTurnOverlayTest` "a motorway change is drawn as a keep, not a turn"

### Requirement: "Next next" hint

When a following manoeuvre is within 200m of the current one, the overlay SHALL show a second row with the
next-next turn hint, built from the nested step the published step ahead carries.

#### Scenario: Next next shown

- **WHEN** the step ahead carries a nested following manoeuvre
- **THEN** the overlay SHALL display a second row with that manoeuvre's icon and localized short description

#### Scenario: Next next hidden

- **WHEN** the step ahead carries no nested following manoeuvre
- **THEN** the overlay SHALL NOT show a second row
