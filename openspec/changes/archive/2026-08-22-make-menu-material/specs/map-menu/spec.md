# Map Menu

## Purpose

Defines the Material 3 animated map menu and its toaster trigger button: a hamburger button at the top-left of the map screen that pops a fade/scale-in menu with icon-leading action entries.

## ADDED Requirements

### Requirement: Toaster button at top-left
The system SHALL display a toaster button (hamburger icon) at the top-left of the map screen, positioned below the status bar via system window insets, in both portrait and landscape orientations.

#### Scenario: Toaster button visible at top-left
- **WHEN** the map screen is displayed
- **THEN** the toaster button SHALL appear at the top-left of the screen below the status bar

#### Scenario: Toaster button top-left in landscape
- **WHEN** the device is in landscape orientation
- **THEN** the toaster button SHALL remain at the top-left

### Requirement: Menu animates in and out
The system SHALL open the menu with a Material 3 fade and scale-in animation and close it with a fade-out animation over a short duration.

#### Scenario: Menu fades in on open
- **WHEN** the user taps the toaster button
- **THEN** the menu SHALL fade and scale in smoothly

#### Scenario: Menu fades out on dismiss
- **WHEN** the menu is dismissed
- **THEN** the menu SHALL fade out smoothly

### Requirement: Menu entries with leading icons
The menu SHALL contain the entries Download Maps, Favorites, Search POIs, and About, each with a leading Material icon. Selecting an entry SHALL dismiss the menu and trigger the same action as the previous overflow menu.

#### Scenario: Download Maps entry
- **WHEN** the user taps the "Download Maps" entry
- **THEN** the menu SHALL dismiss
- **AND** the app SHALL navigate to the map manager screen

#### Scenario: Favorites entry
- **WHEN** the user taps the "Favorites" entry
- **THEN** the menu SHALL dismiss
- **AND** the favorites sheet SHALL open

#### Scenario: Search POIs entry
- **WHEN** the user taps the "Search POIs" entry
- **THEN** the menu SHALL dismiss
- **AND** the POI search SHALL open

#### Scenario: About entry
- **WHEN** the user taps the "About" entry
- **THEN** the menu SHALL dismiss
- **AND** the about dialog SHALL show

### Requirement: Menu dismiss behavior
The system SHALL dismiss the menu when the user taps outside it or performs the system back gesture, matching Material 3 menu behavior.

#### Scenario: Outside tap dismisses menu
- **WHEN** the menu is open
- **AND** the user taps outside the menu
- **THEN** the menu SHALL dismiss

#### Scenario: System back dismisses menu
- **WHEN** the menu is open
- **AND** the user performs the system back gesture
- **THEN** the menu SHALL dismiss
