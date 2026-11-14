# Spec Delta — location-options-ui

## MODIFIED Requirements

### Requirement: Location options button

The system SHALL display a location options button on the map screen. The button SHALL be positioned near the zoom controls (bottom area of the screen). Tapping the button SHALL open a full-width Material 3 quick settings sheet whose content is bounded by `settings-surface`.

#### Scenario: Button visible on map screen

- **WHEN** the map screen is displayed
- **THEN** a location options button SHALL be visible in the bottom area of the screen
- **THEN** the button SHALL be rendered on top of the map canvas
- **Case**: none — the quick-sheet rows exist, and their case is written when the sheet shrinks (tasks.md group 6)

#### Scenario: Button opens bottom sheet

- **WHEN** the user taps the location options button
- **THEN** a full-width Material 3 bottom sheet SHALL slide up from the bottom of the screen
- **THEN** the bottom sheet SHALL contain at least one toggle control
- **Case**: none — the quick-sheet rows exist, and their case is written when the sheet shrinks (tasks.md group 6)

### Requirement: The car exposes no maximum speed control

Android Auto and AAOS SHALL NOT offer an edit control for a vehicle maximum speed; the car SHALL read the
value the phone stores. The phone's settings surface is the only editor, so this is a deliberate deviation
from cross-variant UI parity (`guidelines/UI.md` §1) rather than an omission.

#### Scenario: No car-side editor

- **WHEN** the car settings or preferences surface is opened
- **THEN** it SHALL NOT contain a maximum speed control
- **AND** car routing still uses the phone's stored car value
- **Case**: none — the quick-sheet rows exist, and their case is written when the sheet shrinks (tasks.md group 6)

## REMOVED Requirements

### Requirement: Overspeed warning delta control

**Reason**: The control moves to the phone's settings surface (`settings-surface`), which exposes every
persisted phone setting in its topic. Keeping it in the quick sheet would contradict the sheet's bound to the
current mode's live controls.

**Migration**: Read the row's requirement and its three scenarios — the 0–30 km/h range at 1 km/h precision,
the 5 km/h default, immediate application to the speed widget, and the persisted shared value — in
`settings-surface`, "Every persisted phone setting is editable from the settings surface".

### Requirement: Vehicle anchor position controls

**Reason**: The anchor rows move to `settings-surface`, topic "Navigation & Route", together with the car's own
anchors the phone now also edits. The 5×3 grid, its labels and its persistence are unchanged and are carried by
`settings-surface`.

**Migration**: See `settings-surface`, "The phone edits the car's own settings" and "Every persisted phone
setting is editable from the settings surface". The picker component and its labels stay as they are.

### Requirement: Vehicle maximum speed sliders in the options sheet

**Reason**: The three speed sliders move to `settings-surface`, topic "Navigation & Route", beside the
avoidance switches they belong with. Ranges, defaults and persistence are unchanged.

**Migration**: See `settings-surface`, "Every persisted phone setting is editable from the settings surface";
`routing-max-speed` continues to own the values, their ranges and their consumption.

### Requirement: A slider change persists immediately

**Reason**: The requirement is not specific to the sheet — it states how any settings slider behaves. It moves
to `settings-surface` unchanged in substance so that the surface's sliders inherit it.

**Migration**: See `settings-surface`, "A change applies immediately and persists".

### Requirement: Long-press timeout control

**Reason**: The control moves to `settings-surface`, topic "Karte", section "Bedienung". The 250–1000 ms range,
the 50 ms step, the phone-only scope and the absence of a car editor are unchanged and are carried by
`settings-surface` and `map-long-press-timeout`.

**Migration**: See `settings-surface`, "Every persisted phone setting is editable from the settings surface";
the three per-mode rendering scenarios lose their subject because the surface is not mode-dependent.
