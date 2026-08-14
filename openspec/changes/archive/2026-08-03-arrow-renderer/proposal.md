## Why

Navigation symbols (turn arrows, lane arrows, roundabouts) are rendered as Unicode text characters. This produces poor visualization — roundabouts show a generic ↳ symbol regardless of exit count or geometry. The mapping logic is duplicated across `NextTurnOverlay.kt` and `RouteSummaryDialog.kt`. A central rendered-symbol system is needed for clear, context-aware navigation visuals.

## What Changes

- Create `NavigationArrowRenderer` — central Compose Canvas composable/function that renders all navigation symbols as drawn graphics instead of Unicode text
- Replace `turnTypeToIcon()` in `NextTurnOverlay.kt` and `RouteSummaryDialog.kt` with calls to the central renderer
- Replace `laneTurnToArrow()` in `NextTurnOverlay.kt` with calls to the central renderer
- Implement roundabout rendering that shows exit count, exit positions on the roundabout, and highlights the correct exit
- Remove duplicated `formatDistance()` — consolidate into a shared utility
- All existing call sites update to use new renderer

## Capabilities

### New Capabilities
- `navigation-symbols`: Central Compose-based renderer for turn type arrows, lane turn arrows, and navigation pictograms. Replaces all Unicode-text-based symbol rendering with explicitly drawn Canvas graphics. Provides a single composable/function API consumed by `NextTurnOverlay`, `RouteSummaryDialog`, and any future navigation UI.
- `roundabout-renderer`: Roundabout visualization that renders the roundabout as a circle with exit markers positioned at correct angles, highlighting the exit the driver should take. Supports variable exit count (2-8+), exit direction/position derived from `RouteInstruction` data, and visual emphasis on the selected exit.

### Modified Capabilities
- *(none — no existing spec-level behavior changes)*

## Impact

- **New file**: `app/src/main/java/com/naviveylin/ui/navigation/NavigationArrowRenderer.kt` — central renderer
- **Modified files**:
  - `app/src/main/java/com/naviveylin/ui/navigation/NextTurnOverlay.kt` — use central renderer
  - `app/src/main/java/com/naviveylin/ui/route/RouteSummaryDialog.kt` — use central renderer
  - `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt` — consolidate `formatDistance`
- **Dependencies**: Jetpack Compose Foundation (Canvas) — already in project
- **No new native/JNI changes** — all rendering is Compose-side
