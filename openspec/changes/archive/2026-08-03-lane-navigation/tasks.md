# Tasks

## 1. Add lane state to NavigationViewModel

- [x] Add `LaneTurn` import
- [x] Add `laneOneway`, `laneCount`, `laneSuggested`, `laneSuggestedFrom`, `laneSuggestedTo`, `laneTurns` fields to `NavigationState`
- [x] Override `onLaneUpdate` in `createListener()` to copy lane data into state

## 2. Rewrite NextTurnOverlay

- [x] Change layout from centered `Row` to left-aligned `Column`
- [x] Row 1: next turn icon + distance + description (wrapping, maxLines=2)
- [x] Row 2: `LaneHintsRow` — per-lane arrows with dividers, suggested lanes highlighted
- [x] Row 3: next-next turn (bodySmall, wrapping)
- [x] Conditional spacers — no gap when lanes absent
- [x] `laneTurnToArrow` mapping matching JavaScout
- [x] Add `laneHintsEnabled` param, hide lane row when disabled

## 3. Update MapCanvasScreen

- [x] Pass lane state props to `NextTurnOverlay`
- [x] Change alignment from `TopCenter` to `TopStart`
- [x] Wire `laneHintsEnabled` from state to `LocationOptionsOverlay` and `NextTurnOverlay`

## 4. Add lane hints toggle

- [x] Add `laneHintsEnabled: Boolean = true` to `AppSettings` in `SettingsStorage.kt`
- [x] Add `laneHintsEnabled` to `MapCanvasUiState`
- [x] Load `laneHintsEnabled` from settings on init
- [x] Add `onToggleLaneHints` function in `MapCanvasViewModel`
- [x] Add lane hints switch in `LocationOptionsOverlay` (visible only during navigation)

## 5. Verify

- [x] Build compiles (`./gradlew :app:compileDebugKotlin`)
