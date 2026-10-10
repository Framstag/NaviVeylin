# Map Styles Specification — delta

## ADDED Requirements

### Requirement: Icon-versus-symbol preference is a persisted phone setting

The app SHALL expose a persisted user setting that selects which of a style entry's two renderings is
used when the active stylesheet defines both a raster icon name and a vector symbol for the same map
object. The default SHALL keep the raster icon in precedence, which is the behavior in effect before
this setting existed, and the value SHALL be persisted across app restarts.

#### Scenario: Default on first start

- **WHEN** the user has never changed the setting
- **THEN** the raster icon keeps precedence when its image is available

#### Scenario: Setting survives restart

- **WHEN** the user enables the preference and restarts the app
- **THEN** the preference is still enabled

#### Scenario: Independent of the selected style

- **WHEN** the user switches the map style while the preference is enabled
- **THEN** the preference stays enabled and applies to the newly selected style

### Requirement: Preference decides which rendering is drawn when both exist

While the preference is enabled the renderer SHALL draw the symbol of an entry that defines both a
raster icon and a symbol. While it is disabled the renderer SHALL draw the raster icon whenever its
image is available and the symbol otherwise. An entry defining only one of the two SHALL keep drawing
that one either way.

#### Scenario: Symbol drawn while the preference is enabled

- **WHEN** the preference is enabled and a style entry defines both a symbol and a raster icon whose image is available
- **THEN** the map draws the symbol for that object

#### Scenario: Icon-only entry keeps its raster icon

- **WHEN** the preference is enabled and a style entry defines a raster icon and no symbol
- **THEN** the map draws the raster icon for that object

#### Scenario: Symbol-only entry is unaffected

- **WHEN** a style entry defines a symbol and no raster icon name, with either value of the preference
- **THEN** the map draws the symbol for that object

#### Scenario: Disabled preference keeps the icon-first order

- **WHEN** the preference is disabled and a style entry defines both, with its image available
- **THEN** the map draws the raster icon for that object

#### Scenario: Disabled preference keeps the symbol fallback

- **WHEN** the preference is disabled and the raster icon's image cannot be loaded
- **THEN** the map draws the symbol for that object

### Requirement: Preference is applied at start and on change

On app start the persisted preference SHALL be applied to the renderer before or as part of the first
map display. Changing it while a map surface is visible SHALL take effect without an app restart, and
SHALL leave no frame or cached map tile rendered under the previous preference.

#### Scenario: Preference applied at startup

- **WHEN** the app starts with the preference enabled
- **THEN** the first map display already uses the symbol rendering

#### Scenario: Change takes effect without restart

- **WHEN** the user changes the preference while the map is visible
- **THEN** the visible map re-renders with the new rendering without an app restart

#### Scenario: No cached rendering from the previous preference survives

- **WHEN** the preference changes
- **THEN** cached map tiles and pending frames from the previous preference are discarded rather than displayed

### Requirement: Phone control for the preference

The phone app SHALL provide the preference as a control in its map options, together with the map
style picker, and SHALL show its current state.

#### Scenario: Control is reachable next to the style picker

- **WHEN** the user opens the phone map options
- **THEN** the preference control is shown with the map style picker

#### Scenario: Control shows the current state

- **WHEN** the preference is enabled
- **THEN** the control shows it as enabled

### Requirement: Car surface follows the shared preference without a control

The Android Auto / AAOS map surface SHALL render with the persisted preference, shared with the phone
as the map style selection is, and SHALL NOT offer a control of its own for it.

#### Scenario: Car renders the phone's choice

- **WHEN** the phone has the preference enabled and a car navigation session renders the map
- **THEN** the car map draws the symbol rendering

#### Scenario: Car offers no control

- **WHEN** the user opens the car variant's settings
- **THEN** no control for this preference is offered there
