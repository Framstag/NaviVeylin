## Why

Navigation UI during active guidance is cluttered and hard to read at a glance. Road type labels waste space, instruction text is too small, ETA is buried, and the stop button is oversized. These changes make the nav HUD cleaner, more glanceable, and more compact.

## What Changes

1. **Hide street type in routing status view** — `CurrentRoadInfo.toDisplayString()` includes `typeName` (e.g., "motorway", "primary"). Strip `typeName` from the road label shown in `NavigationStateOverlay`. Show only `ref` + `name`.

2. **Bigger navigation instruction in NextTurnOverlay** — Increase `instruction.description` text from `bodySmall` to `bodyLarge`. Make the turn distance more prominent.

3. **Add ETA to routing status view, compact labels** — ETA already exists in `NavigationStateOverlay` but label text ("ETA", "Dist", "Speed") takes space. Shorten or remove labels, make ETA the primary stat. Use icons or shorter labels to save space.

4. **Stop routing button smaller — replace text with cross icon** — Replace `FilledTonalButton` with text "Stop" with a smaller icon-only button (cross/X icon). Reduces button width from 72dp to ~40dp.

5. **Add remaining time until arrival to routing status** — Add a new stat column showing remaining travel time (e.g., "1h 23min") computed from ETA timestamp minus current time.

## Capabilities

### New Capabilities
- `nav-hud-visuals`: Visual presentation of the navigation HUD — road name display, instruction sizing, ETA prominence, remaining time, stop button compactness

### Modified Capabilities
- *(none — no spec-level behavior changes, only UI presentation)*

## Impact

- `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt` — road label formatting, ETA layout, stop button icon
- `app/src/main/java/com/naviveylin/ui/navigation/NextTurnOverlay.kt` — instruction text sizing
- No JNI, no ViewModel, no data layer changes
