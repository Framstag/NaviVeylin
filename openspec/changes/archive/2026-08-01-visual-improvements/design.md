## Context

Two Compose overlays render during active navigation: `NavigationStateOverlay` (bottom — road name, ETA, distance, speed, stop button) and `NextTurnOverlay` (top — turn icon, distance, description). Both live in `app/src/main/java/com/naviveylin/ui/navigation/`. No data layer or JNI changes needed — all 4 changes are pure UI presentation in these two files.

See proposal.md — Why for motivation.

## Goals / Non-Goals

**Goals:**
- Remove `typeName` from road label in `NavigationStateOverlay`
- Increase `instruction.description` font size in `NextTurnOverlay`
- Shorten stat labels in `NavigationStateOverlay` to save horizontal space
- Replace "Stop" text button with icon-only button, reducing width
- Add remaining time until arrival stat column

**Non-Goals:**
- No changes to `NavigationViewModel`, `NavigationState`, or JNI bridge
- No changes to data model (`CurrentRoadInfo`, `RouteInstruction`)
- No layout reordering or new composables
- No behavior changes to navigation start/stop logic

## Decisions

### 1. Road label: build display string locally instead of modifying `toDisplayString()`

`CurrentRoadInfo.toDisplayString()` is in the JNI library (`libosmscout-client-java`). Modifying it would require rebuilding the native bridge. Instead, build the display string in `NavigationStateOverlay` using `ref` + `name` fields directly, omitting `typeName`.

- **Alternative considered:** Modify `toDisplayString()` in the JNI library — rejected because it's a shared library used by other clients (JavaScout demo app).
- **Alternative considered:** Add a new method `toShortDisplayString()` to `CurrentRoadInfo` — rejected for same reason; simpler to format locally.

### 2. Instruction description: bump from `bodySmall` to `bodyLarge`

Change the `Text` style for `instruction.description` from `MaterialTheme.typography.bodySmall` to `bodyLarge`. This is a one-line change in `NextTurnOverlay`.

### 3. Stat labels: use Material Icons instead of text labels

Replace label `Text` composables with `Icon` composables using Material Icons:
- "ETA" → `Icons.Default.Schedule` (clock icon)
- "Dist" → `Icons.Default.Place` (pin icon) or `Icons.Default.ArrowForward`
- "Speed" → `Icons.Default.Speed` (speedometer icon)

Icons are universally understood and take less horizontal space than text labels.

- **Alternative considered:** Remove labels entirely — rejected because stats become ambiguous without any label.
- **Alternative considered:** Shorter text like "Arr", "Dst", "Spd" — less clear than icons.

### 4. Stop button: `IconButton` with `Icons.Default.Close`

Replace `FilledTonalButton` with text "Stop" with an `IconButton` using `Icons.Default.Close` (X icon). Remove the 72dp width constraint; let the icon size determine width (~40dp).

- **Alternative considered:** `Icon` inside existing `FilledTonalButton` with no text — still carries button padding; `IconButton` is more compact.
- **Alternative considered:** `SmallFilledTonalButton` — Compose Material 3 has no such variant; `IconButton` is the standard pattern for icon-only actions.

### 5. Remaining time: compute from ETA timestamp

Add a new stat column between ETA and Distance showing remaining travel time. Compute `etaMillis - System.currentTimeMillis()` and format as "Xh Ymin" or "Y min". Uses same clock icon as ETA.

- **Alternative considered:** Derive from remaining distance / avg speed — less accurate than using the ETA timestamp directly.
- **Alternative considered:** Add a new field to `NavigationState` — unnecessary; `etaMillis` already provides the data needed.

## Risks / Trade-offs

- **Icon clarity:** Icons may be less immediately clear than text labels for new users. Mitigation: clock, pin, and speedometer icons are standard Material symbols with high recognition.
- **Stop button discoverability:** Cross icon for "stop" is standard in navigation apps (Google Maps, OSMAnd). Low risk.
- **Road label format change:** Only affects the display string; `CurrentRoadInfo` data model unchanged. No downstream impact.
