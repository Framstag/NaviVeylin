## Purpose

Provides quick access to all starred favorites via a horizontal scrollable chip bar at the top of the favorites sheet, letting users jump directly to any starred favorite.

## ADDED Requirements

### Requirement: Starred favorites shown in chip bar
The system SHALL display a horizontal scrollable row of chips at the top of the favorites sheet's main view, with one chip per starred favorite across all groups.

#### Scenario: Chip bar visible when starred favs exist
- **GIVEN** one or more favorites are starred
- **WHEN** the favorites sheet opens to the main group grid view
- **THEN** a horizontal scrollable chip bar SHALL appear at the top showing each starred favorite as a chip with its name

#### Scenario: Chip bar hidden when no starred favs
- **GIVEN** no favorites are starred
- **WHEN** the favorites sheet opens
- **THEN** no chip bar SHALL be shown

#### Scenario: Chip bar scrolls horizontally
- **GIVEN** more starred favorites than fit on screen
- **WHEN** the user swipes horizontally on the chip bar
- **THEN** the chip bar SHALL scroll to reveal additional starred favorites

### Requirement: Chip click opens route panel
Tapping a starred favorite chip SHALL close the favorites sheet and open the route panel with current location as start and the tapped favorite as destination.

#### Scenario: Tap chip opens route panel
- **WHEN** user taps a starred favorite chip
- **THEN** the favorites sheet SHALL close
- **AND** the route panel SHALL open with current location as start and the tapped favorite as destination

#### Scenario: Chip shows group context
- **WHEN** the chip bar displays a starred favorite
- **THEN** the chip SHALL show the favorite name
- **AND** optionally SHALL show the group name as secondary text
