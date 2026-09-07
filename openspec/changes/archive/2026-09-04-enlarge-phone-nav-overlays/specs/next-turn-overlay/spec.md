# next-turn-overlay — Delta for enlarge-phone-nav-overlays

## ADDED Requirements

### Requirement: Driver-seat readable font sizes
The next-turn overlay text SHALL be large enough to read from the driver seat: the distance SHALL render at 32sp or larger bold, the description/destination at 22sp or larger, and the next-next hint at 20sp or larger (still smaller than the primary instruction). This supersedes the earlier size progression (24sp distance / 18sp description / 16sp next-next).

#### Scenario: Distance at least 32sp
- **WHEN** the next-turn distance is displayed
- **THEN** it uses a font size of 32sp or larger with bold weight

#### Scenario: Description at least 22sp
- **WHEN** the next-turn description or destination name is displayed
- **THEN** the text uses a font size of 22sp or larger

#### Scenario: Next-next at least 20sp and smaller than primary
- **WHEN** the next-next hint is displayed
- **THEN** its text uses a font size of 20sp or larger
- **AND** it remains smaller than the next-turn instruction text

#### Scenario: Turn icon at least 64dp
- **WHEN** the next-turn icon is displayed
- **THEN** the turn-type icon is at least 64dp
- **AND** the next-next icon is at least 36dp

#### Scenario: Wrapping preserved at larger sizes
- **WHEN** the instruction description exceeds the available width at the enlarged font sizes
- **THEN** the text still wraps with an ellipsis on overflow (existing wrapping behavior unchanged)
