# Spec Delta

## MODIFIED Requirements

### Requirement: Section ordering
The system SHALL render the map manager screen sections in a fixed order: provider row, search field, error banner, active downloads, updates, basemap section, installed maps, available maps. Sections with no content SHALL be omitted without leaving gaps.

#### Scenario: Sections render in fixed order
- **WHEN** user opens the map manager screen with downloads active, updates available, maps installed, and maps available
- **THEN** the sections appear in the order: provider row, search field, active downloads, updates, basemap section, installed maps, available maps

#### Scenario: Empty sections omitted
- **WHEN** no downloads are active and no maps are installed
- **THEN** the active-downloads and installed-maps sections are not shown
- **AND** the remaining sections keep their relative order
