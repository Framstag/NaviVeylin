## ADDED Requirements

### Requirement: Installed maps stay on top after refresh
The system SHALL keep installed maps visible at the top of the tree view after a provider refresh, so users can easily find and delete them.

#### Scenario: Installed maps remain on top after refresh
- **WHEN** user taps [Refresh] and installed maps exist
- **THEN** the installed maps appear at the top of the tree view with ✅ checkmark and [Delete] button
- **AND** newly discovered available maps appear below the installed section

#### Scenario: Installed maps section labeled
- **WHEN** installed maps are shown at the top after refresh
- **THEN** they are visually grouped or labeled as "Installed Maps"
- **AND** the section is distinct from available maps
