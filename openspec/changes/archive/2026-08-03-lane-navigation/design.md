# Lane Navigation — Design

## Context

Upstream libosmscout JNI bridge already provides:
- `NavigationListener.onLaneUpdate(oneway, count, suggested, suggestedFrom, suggestedTo, turn, turns[])`
- `LaneTurn` enum with all directional values
- JavaScout reference implementation in `MainController.updateNextTurnLanes()`

No native/JNI changes needed. Pure Kotlin/Compose work.

## Layout

```
┌──────────────────────────────────────────┐
│ ←  500 m                                 │
│     Turn left onto Hauptstrasse          │  ← next turn (wrapping)
│                                          │
│     ← | ↑ | →                            │  ← lane hints (optional, no gap)
│                                          │
│     200 m                                │  ← next-next turn (smaller, wrapping)
│     Right onto Bahnhofstrasse            │
└──────────────────────────────────────────┘
```

All content left-aligned. Card fills width with horizontal padding.

## Data Flow

```
Native LaneAgent
  → JNI onLaneUpdate()
    → NavigationListener.onLaneUpdate()
      → NavigationState.lane* fields
        → NextTurnOverlay(laneOneway, laneCount, ...)
          → LaneHintsRow()
```

## Files Changed

| File | Change |
|------|--------|
| `NavigationViewModel.kt` | Add 6 lane fields to `NavigationState`, override `onLaneUpdate` in listener |
| `NextTurnOverlay.kt` | Rewrite: Column layout, LaneHintsRow composable, left-aligned, wrapping text, `laneHintsEnabled` param |
| `MapCanvasScreen.kt` | Pass lane props to NextTurnOverlay, change alignment to TopStart, wire lane hints toggle |
| `SettingsStorage.kt` | Add `laneHintsEnabled` to `AppSettings` |
| `MapCanvasViewModel.kt` | Add `laneHintsEnabled` to `MapCanvasUiState`, load/save toggle, `onToggleLaneHints` |
| `LocationOptionsOverlay.kt` | Add lane hints switch (visible during navigation) |
