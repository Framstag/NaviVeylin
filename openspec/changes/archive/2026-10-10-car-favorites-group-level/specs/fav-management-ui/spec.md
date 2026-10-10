# Spec Delta — fav-management-ui

## MODIFIED Requirements

### Requirement: Favorites management stays on the phone

Moving a favorite between groups SHALL be a phone-only surface. The Android Auto and Android Automotive OS favorites screen SHALL remain a browse-and-select surface without favorites-management actions, because the car templates cannot express the destination-group selection step and the car screen must stay operable while driving. Phone and car surfaces SHALL keep the same browsing hierarchy and the same wording for it — the group level first, then the group's own favorites — so that adding a level to either surface is a change to this requirement rather than a silent deviation; the car surface SHALL NOT gain a phone-derived action.

#### Scenario: Car favorites screen offers no move action

- **WHEN** the favorites screen is displayed on Android Auto or Android Automotive OS
- **THEN** it SHALL NOT offer a "Move to group" action or any other favorites-management action
- **AND** selecting a favorite SHALL keep its existing destination-picker behavior

#### Scenario: A move made on the phone is visible on the car

- **GIVEN** the user moves a favorite into another group on the phone
- **WHEN** the car favorites screen lists the favorites afterwards
- **THEN** the favorite SHALL appear in the destination group's list on both surfaces
