## MODIFIED Requirements

### Requirement: Reload basemap after download or delete
The system SHALL reload or unload the basemap when it is downloaded, updated, or deleted while the app is running — including the first-time case where no basemap was installed when the app started. The current view SHALL re-render so the change is visible without restarting the app.

#### Scenario: Basemap downloaded while app is running
- **WHEN** user downloads or updates the basemap
- **THEN** the system triggers a reload of the basemap database
- **THEN** the current view re-renders with the basemap overlay active

#### Scenario: First-time basemap installation while app is running
- **WHEN** the app started without a basemap installed
- **WHEN** user downloads and installs the basemap while the app is running
- **THEN** the system registers the basemap for the running session
- **THEN** the system triggers a reload of the basemap database
- **THEN** the current view re-renders with the basemap overlay active without an app restart

#### Scenario: Basemap deleted while app is running
- **WHEN** user deletes the basemap
- **THEN** the system unloads the basemap database
- **THEN** the current view re-renders without the basemap overlay
