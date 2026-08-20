## MODIFIED Requirements

### Requirement: About dialog displays app name and version
The about dialog SHALL display the application name "NaviVeylin" and the current version name (e.g., "2026-08-19-1") sourced from the built app's version metadata.

#### Scenario: Dialog shows correct app name and version
- **WHEN** the about dialog is open
- **THEN** the dialog SHALL show "NaviVeylin" as the app name
- **AND** the dialog SHALL show the version string from the built app's version metadata
