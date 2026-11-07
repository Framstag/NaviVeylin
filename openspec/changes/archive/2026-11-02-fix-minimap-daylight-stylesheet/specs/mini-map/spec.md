# Spec Delta — mini-map

## ADDED Requirements

### Requirement: The mini map renders the resolved presentation's stylesheet variant

The mini map SHALL render its bitmap with the stylesheet variant its `isDarkPresentation` input resolves to: it
SHALL push the stylesheet `daylight` flag from that value before its first frame, and it SHALL re-render its own
viewport when the value changes, so neither its frame nor its tile cache keeps the other variant. The push SHALL
happen once per distinct value, and the value SHALL be the caller's resolved presentation, never the platform's
dark-mode state.

#### Scenario: Dark presentation pushes the dark variant before the first frame

- **WHEN** the mini map is embedded with dark presentation active
- **THEN** it pushes the `daylight` stylesheet flag as unset (`false`) before it requests its first frame
- **Case**: `app: MiniMapComposeTest` "darkPresentationPushesTheDarkDaylightFlag"

#### Scenario: Light presentation pushes the daylight variant

- **WHEN** the mini map is embedded with light presentation active
- **THEN** it pushes the `daylight` stylesheet flag as set (`true`), once, before it requests its first frame
- **Case**: `app: MiniMapComposeTest` "lightPresentationPushesTheDaylightFlag"

#### Scenario: A presentation change re-renders the mini map

- **WHEN** the resolved presentation changes while the mini map is displayed
- **THEN** the mini map pushes the new value and requests a full re-render of its own viewport, so its bitmap does
  not stay at the previous variant
- **Case**: `app: MiniMapComposeTest` "presentationChangeReRendersTheMiniMap"
