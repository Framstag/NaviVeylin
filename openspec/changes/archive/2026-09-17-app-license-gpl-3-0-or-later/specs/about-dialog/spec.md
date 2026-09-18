# about-dialog Specification

## Purpose

Provides users with app identity information including version, description, and open-source licenses in a standard Android dialog.

## ADDED Requirements

### Requirement: About dialog shows the application's own license with full text

The bundled license list SHALL include an entry for the application itself,
showing the application's SPDX license identifier (`GPL-3.0-or-later`) and the
full license text, reachable without network access. This satisfies the GNU GPL
§5 obligation (a copy of the license accompanies the program) on every
distribution flavor.

#### Scenario: Application license entry is present

- **WHEN** the user opens the About dialog and then the license list
- **THEN** the list SHALL contain an entry for the application itself
- **AND** the entry SHALL display the license name "GNU General Public License,
  version 3 or later" (or equivalent localised text)
- **AND** the entry SHALL display the SPDX identifier `GPL-3.0-or-later`

#### Scenario: Application license text is available offline

- **WHEN** the user opens the application's license entry from the license list
- **THEN** the full GNU GPL version 3 license text SHALL be displayed
- **AND** the text SHALL be available with no network connection

#### Scenario: License text is present in both flavors

- **WHEN** the license list is opened in the phone/Android Auto flavor and in the
  Android Automotive OS flavor
- **THEN** both SHALL provide the application license entry with its full text
