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
