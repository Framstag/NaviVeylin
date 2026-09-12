## ADDED Requirements

### Requirement: Re-entry supersedes an in-flight map init

When the map screen starts a new map initialization for a new map path while a previous initialization is still in progress (still suspended before applying its restored viewport), the system SHALL drop all state writes of the superseded initialization once the new one has started, so a stale initialization can neither apply its viewport to the map state, re-arm the lifecycle-save guard, nor persist a viewport under the new map key.

#### Scenario: Re-entry during restore does not clobber the new restore

- **WHEN** the map screen re-enters map initialization (new map path) while the previous initialization is still suspended after the save guard was re-armed
- **THEN** the superseded initialization's viewport and save-guard writes are dropped
- **THEN** the map shows the new map's restored viewport

#### Scenario: Lifecycle save during the re-entry window

- **WHEN** the app is backgrounded while a re-entry initialization is in progress and the map's restored viewport has not yet been applied by the new initialization
- **THEN** the viewport file is not written or overwritten
- **THEN** the previously persisted viewport content remains on disk

#### Scenario: Single initialization unaffected

- **WHEN** map initialization starts once with no re-entry
- **THEN** the restored viewport is applied and lifecycle saves persist exactly as before this change

### Requirement: Restored viewport drives the renderer

When map initialization applies the restored viewport, the system SHALL also apply it to the renderer's viewport before any initialization-time render is submitted, so the first rendered frame shows the restored viewport rather than the renderer's default.

#### Scenario: Init-time renders use the restored viewport

- **WHEN** map initialization completes and the init-time style/dark pushes submit a render
- **THEN** the render uses the restored viewport (center and magnification)
- **THEN** the first displayed frame shows the restored viewport, not the renderer default
