## MODIFIED Requirements

### Requirement: Minimum readable size for the max-speed sign
The round max-speed sign SHALL be at least 64dp in diameter with a red border of at least 6dp and digits of at least 28sp bold.

#### Scenario: Sign at least 56dp
- **WHEN** the max-speed sign is shown
- **THEN** the sign circle is at least 56dp in diameter

#### Scenario: Sign digits at least 22sp
- **WHEN** the max-speed sign is shown
- **THEN** the digit text uses a font size of 22sp or larger with bold weight

#### Scenario: Sign at least 64dp
- **WHEN** the max-speed sign is shown
- **THEN** the sign circle is at least 64dp in diameter

#### Scenario: Sign digits at least 28sp
- **WHEN** the max-speed sign is shown
- **THEN** the digit text uses a font size of 28sp or larger with bold weight

#### Scenario: Reserved slot still matches
- **WHEN** the sign is hidden but the slot is reserved (bottom-anchored placement)
- **THEN** the reserved slot keeps the same footprint as the visible sign (64dp)
