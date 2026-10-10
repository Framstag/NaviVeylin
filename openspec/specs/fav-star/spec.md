# fav-star Specification

## Purpose

Lets users mark individual favorites as starred for quick identification, with a visible star icon on starred favorites.

## Requirements

### Requirement: Favorite can be starred/unstarred
The system SHALL allow users to toggle a star state on any favorite location. The star state SHALL be persisted in the favorite's `attributes["starred"]` map.

#### Scenario: Star a favorite
- **WHEN** user taps the star icon on a favorite item (currently unstarred)
- **THEN** the favorite SHALL become starred and the star icon SHALL appear filled

#### Scenario: Unstar a favorite
- **WHEN** user taps the star icon on a starred favorite
- **THEN** the favorite SHALL become unstarred and the star icon SHALL appear unfilled

#### Scenario: Star state persists across app restart
- **WHEN** user stars a favorite, closes the app, and reopens
- **THEN** the favorite SHALL still show as starred

### Requirement: Starred favorites show filled star icon
Starred favorites SHALL display a filled star icon in the favorite item row, visually distinct from unstarred favorites.

#### Scenario: Star icon visible on starred favorite
- **GIVEN** a favorite is starred
- **WHEN** the favorite is displayed in a list
- **THEN** a filled star icon SHALL be shown next to the favorite name

#### Scenario: Unstarred favorite shows outline star
- **GIVEN** a favorite is not starred
- **WHEN** the favorite is displayed in a list
- **THEN** an outline (unfilled) star icon SHALL be shown next to the favorite name

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
