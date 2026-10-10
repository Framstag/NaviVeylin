# Spec Delta

## ADDED Requirements

### Requirement: Starring enters the favorite at the end of the starred order

Starring a favorite SHALL place it at the end of the starred order (spec `starred-ordering`); unstarring SHALL take it out of that order. The flag's persistence in `attributes["starred"]` and the icon behaviour are unchanged.

#### Scenario: Starred favorite enters at the end

- **GIVEN** other favorites are already starred
- **WHEN** the user stars a favorite
- **THEN** it SHALL be the last entry of the starred order

#### Scenario: Unstarred favorite leaves the order

- **WHEN** the user unstars a favorite
- **THEN** it SHALL no longer appear in the starred order
- **AND** the remaining entries SHALL keep their relative order
