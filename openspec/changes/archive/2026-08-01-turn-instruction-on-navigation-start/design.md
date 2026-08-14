## Context

When navigation starts, `RouteInstructionAgent` (native C++ in libosmscout) only emits `RouteInstructionsMessage` when `PositionState` is `OnRoute` or `EstimateInTunnel`. Initial state is `Uninitialised` — no instructions reach the Kotlin layer until the first GPS position update triggers a state transition. See `proposal.md` for motivation.

## Goals / Non-Goals

**Goals:**
- First turn instruction visible immediately on navigation start, no movement required
- Minimal native C++ change — one guard condition in `RouteInstructionAgent`
- Kotlin-side `showFirstInstructionOnStart` flag for UI state clarity
- Zero regression for subsequent instruction timing

**Non-Goals:**
- No changes to `PositionAgent` or other native agents
- No changes to route calculation or `RouteEntry` marshalling
- No synthetic instruction construction from description strings

## Decisions

### Decision 1: Relax native guard in RouteInstructionAgent

**Chosen:** Move position-state guard to only block per-update instruction trimming, not initial route instruction emission.

**Rationale:** The guard at `RouteInstructionAgent.h:83-87` returns early with empty results when position is not `OnRoute` or `EstimateInTunnel`. This prevents `RouteInstructionsMessage` from being emitted when the route is first set (line 92-96). By moving the guard to only wrap lines 98-115 (instruction trimming + `NextRouteInstructionsMessage`), the full instruction list is emitted on route change regardless of position state.

**Alternatives considered:**
- *Kotlin-side synthetic instruction*: `RouteEntry.descriptions` are plain strings (e.g. "Turn left onto Main St") — no `TurnType` or `distanceTo` available. Parsing is fragile.
- *Pre-fetch instructions via JNI before startNavigation*: Would require new native method, more invasive than relaxing one guard.

### Decision 2: Add `showFirstInstructionOnStart` flag to NavigationState

**Chosen:** Boolean flag set `true` in `startNavigation()`, cleared after first `onPositionEstimate()` callback.

**Rationale:** The UI (`NextTurnOverlay`) already renders when `nextInstruction != null`. The flag is not strictly needed for rendering (instruction non-null is sufficient), but it enables:
- Different visual treatment for the initial instruction (e.g. "Start: Turn left..." vs distance-based)
- Clean state tracking for testing
- Future use by other UI components (e.g. voice prompt on start)

### Decision 3: No changes to NextTurnOverlay composable

**Chosen:** `NextTurnOverlay` already renders any non-null `RouteInstruction`. No UI changes needed.

**Rationale:** Once `onRouteInstructions` fires immediately, `NavigationViewModel.onRouteInstructions()` sets `nextInstruction = instructions[0]`, and `NextTurnOverlay` renders it. The existing composable handles this correctly.

## Risks / Trade-offs

- **[Risk] Native change in libosmscout submodule** → Mitigation: Change is a single guard condition move. Easy to review, test, and revert.
- **[Risk] Instruction shown before user is on route** → Mitigation: This is the desired behavior. The instruction shows the first maneuver from the start point, which is correct guidance.
- **[Risk] `onRouteInstructions` fires twice (once on route change, once on first OnRoute position)** → Mitigation: The `prevRoute` check at line 92 prevents duplicate emission for the same route. After the first position update, `prevRoute == positionMessage->route`, so the block is skipped.
