# Proposal: UI Improvements

## What Changes

Improve the readability and interactivity of the navigation UI:

1. **Bigger turn instruction fonts** — the next-turn instruction and the next-next-turn hint in the `NextTurnOverlay` use small typography (`bodyLarge` / `bodySmall`). Both get bumped up while preserving the existing hierarchy (next-next stays smaller than next-turn).

2. **Word wrap between generic instruction and destination name** — when an instruction does not fit the overlay's max width, the text currently wraps mid-phrase and ellipsizes. Instead, the line break SHALL fall between the generic instruction (e.g. "Turn left") and the actual destination name (e.g. "Hauptstrasse"), so the destination name starts on its own line and wraps independently.

3. **Current road name emphasis** — the current road name row in the `NavigationStateOverlay` (bottom routing status card) uses `bodyMedium` and is left-aligned. It gets a slightly bigger font and becomes centered.

4. **Expandable routing status card** — the routing status card at the bottom becomes clickable. Tapping it expands to a full-screen view that keeps the status content (road name + ETA/time/distance/speed stats) and additionally shows the route description list, styled like the route details view (`RouteSummaryDialog`), with the current navigation step selected and shown at the top of the list.

## Capabilities

### New Capabilities

- `navigation-status-details`: full-screen expansion of the routing status card during active navigation, showing the route description list with the current step highlighted at the top

### Modified Capabilities

- `next-turn-overlay`: next-turn and next-next-turn instruction text uses larger typography; wrapping breaks between the generic instruction and the destination name
- `current-road-info`: current road name row uses a slightly larger font and is centered

## Impact

- `app/src/main/java/com/naviveylin/ui/navigation/NextTurnOverlay.kt` — font sizes, two-line generic/destination rendering
- `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt` — road name font/alignment, clickable card
- New composable for the full-screen status details (e.g. `ui/navigation/NavigationDetailsOverlay.kt`)
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — wire click → full-screen overlay state
- `core/src/main/java/com/naviveylin/core/NavigationState.kt` — no changes expected (instructions + currentStepIndex already exposed)
- No native/JNI changes
