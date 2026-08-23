## Why

The Android Auto screens do not follow the layout conventions the phone UI has recently adopted: all map controls live in a single top action strip, the full application menu is not reachable while driving, and navigation hints are host-positioned in the centre of the display. Aligning the Auto variant with the phone layout (actions left, visualisations right, hints inset right of the action column) improves consistency and keeps settings reachable during driving without opening the app menu.

## What Changes

- **Settings dialog on the map (driving-safe)**: Add a settings action on the Auto map screen that opens a settings dialog whose content mirrors the phone's location-options dialog (follow mode, orientation, auto-zoom, dark mode, lane hints, render mode). The settings dialog is reachable while the vehicle is moving; the application menu stays parked-only.
- **Action buttons vertical, left border**: Move the action buttons (menu, search, settings) into a vertical strip on the left edge of the map display, mirroring the phone's action column.
- **Visualisation buttons vertical, right border**: Move the visualisation buttons (zoom in/out) into a vertical strip on the right edge of the map display, mirroring the phone's view column.
- **Navigation hints left-oriented**: During navigation, hints (next-turn instruction, distance, lane guidance) are left-aligned, positioned immediately to the right of the action strip, and must not overlap the visualisation strip on the right.
- **Template migration**: The map screen currently uses the deprecated `MapTemplate`; the split left/right strip layout is expected to require `MapWithContentTemplate` (or equivalent) with separate map and content action strips.

## Capabilities

### New Capabilities
- `auto-map-layout`: Layout and interaction model for the Android Auto map and navigation screens — vertical action strip on the left edge, vertical visualisation strip on the right edge, a driving-safe settings dialog with phone-equivalent content, and left-oriented navigation hints that sit right of the action strip without overlapping the visualisation strip.

### Modified Capabilities
<!-- No existing requirement changes: the current auto spec requirements (menu parked-only,
     Preferences entry on root screen, NavigationTemplate turn-by-turn display) remain
     true; the new layout is additive. -->

## Impact

- `auto/src/main/java/com/naviveylin/auto/MapScreen.kt` — template build, action strip split, settings entry point
- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — action/visualisation strips, hint placement
- `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt` — hint-related mapping helpers (may change)
- New screen/dialog for Auto settings (reuses `PreferencesScreenMapper` rows and `AutoSettings`; `keepScreenOn` stays phone-only)
- Car App Library `androidx.car.app:1.7.0` — `MapWithContentTemplate`, `NavigationTemplate.setMapActionStrip`/`setActionStrip` semantics; possible removal of deprecated `MapTemplate` usage
- Tests: `MapScreenTest`, `NavigationTemplateMapperTest`, `PreferencesScreenMapperTest`; new tests for settings dialog and strip layout
