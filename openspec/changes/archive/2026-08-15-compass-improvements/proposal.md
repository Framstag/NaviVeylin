## Why

The compass button deviates from the other map overlay buttons: its icon is larger, its GPS fix indicator (a thin outer ring) is hard to see, and the follow-direction needle is difficult to read while driving. These inconsistencies hurt glanceability and visual coherence of the map UI.

## What Changes

- **Compass icon size aligned with other overlay buttons**: shrink the compass needle/icon to the same size as the icons in the menu, search, and location-option buttons (Material 3 icon size, 24dp), keeping the button itself consistent with `FilledTonalIconButton` sizing.
- **Consistent shadow**: compass button shadow matches the other overlay buttons (Material 3 style, same elevation/shape as `FilledTonalIconButton` usage elsewhere).
- **GPS fix quality via button fill color**: replace the outer GPS fix status ring with the button's fill (background) color — light red (no fix), light yellow (poor accuracy), light green (good fix) — for better visibility.
- **Readable follow-direction needle**: replace the current line-and-arrowhead indicator with a compass-needle-like triangle whose base line is smaller than its height, pointing in the travel direction.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `compass-button`: GPS fix indication changes from a colored ring to the button fill color; compass icon size and shadow change to match other overlay buttons; follow-direction needle becomes a triangle shape (base < height).

## Impact

- `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt` — icon sizing, shadow, ring→fill color, needle drawing.
- `openspec/specs/compass-button/spec.md` — requirement "GPS fix status ring" replaced by fill-color requirement; sizing/needle requirements updated.
- No native code, no new dependencies, no API changes.
