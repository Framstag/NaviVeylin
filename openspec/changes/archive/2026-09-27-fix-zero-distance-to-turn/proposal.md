# Proposal: Fix distance-to-turn stuck at "0 m" (native step distance desync)

## Why

During AA / phone navigation the next-instruction panel can freeze on "0 m" for the upcoming maneuver while the vehicle is still moving — observed shortly before arrival on a route that had been rerouted once. The displayed value is computed natively (`JavaRouteInstructionBuilder::GenerateNextRouteInstruction` in the JNI bridge): `nextAbs − nodeDist − travelled`, clamped to 0. The `travelled` term is the **straight-line** distance from the GPS position to the engine's current route node (`routeNode`). Whenever `routeNode` lags behind the vehicle — degrading GPS, the forward-only 20 m snap search failing, or the forward search hitting the route's last node — `travelled` grows beyond the remaining maneuver distance, the clamp pins the value at 0, and the maneuver name keeps being shown. The "internal data structures" felt broken because the engine's position/node/instruction state desynchronizes; rerouting makes the desync likelier (route restarts off-grid, resets `routeNode` to the route start, and suppressed reroute reports leave the agent chain in OffRoute, which stops emitting fresh instructions entirely).

## What Changes

- **Native step distance becomes a true along-route remaining distance**: the bridge computes progress along the current route segment (segment abscissa, already computed by `PositionAgent::SearchClosestSegment` but not exposed) instead of subtracting a straight-line distance. A maneuver that is still ahead SHALL NOT display 0 m.
- **The engine stops fabricating desync states**: `PositionAgent` no longer resets `routeNode` to `route->Nodes().begin()` when the forward snap search fails (final node reached, temporary >20 m offset) — it keeps the last node and reports the real position state, so it cannot flip to OffRoute spuriously and freeze the panel.
- **Instruction emission survives non-OnRoute states**: `RouteInstructionAgent` keeps emitting the next instruction (and a fresh arrival instruction with the real remaining distance to the destination, replacing the hardcoded 0 m / empty instruction currently produced after the last maneuver) in OffRoute / NoGpsSignal, using the last known route node and position, so a suppressed reroute cannot leave a stale "0 m" instruction on the panel.
- **Reroute restart clears stale display state**: `startNavigation` (phone `NavigationViewModel` and car `AANavigationController`) clears the previous route's `instructions`/`nextInstruction` when starting a new route, so the old route's last step never flashes over the new route.
- **Step index no longer picked by description equality**: the `indexOfFirst { description match }` lookup in both controllers is replaced by a position-ordered match (first instruction at/after the current step), fixing wrong active-step highlighting on routes with repeated descriptions ("Straight on", looped street names).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auto/navigation-view` — the instruction-panel requirements ("showing a turn-type icon, the distance to the turn ... SHALL update it as navigation progresses") gain the acceptance condition that the displayed distance to an upcoming maneuver SHALL count down to the maneuver and SHALL NOT freeze at 0 m while the vehicle is approaching, including after a reroute, and that an off-route / suppressed-reroute state SHALL NOT leave a stale instruction on the panel.
- `next-turn-overlay` (phone) — same distance-freeze and stale-instruction condition for the phone overlay, which consumes the same native distance value.

## Impact

- **Native — submodule patch (minimal, upstreamable)** in `app/src/main/cpp/libosmscout/` (branch `naviveylin-local`, platform-pure, no Android deps):
  - `libosmscout/include/osmscout/navigation/PositionAgent.h` + `libosmscout/src/osmscout/navigation/PositionAgent.cpp` — expose along-segment progress (abscissa) in `PositionMessage`; keep last `routeNode` on search failure instead of resetting to `begin()`.
  - `libosmscout/include/osmscout/navigation/RouteInstructionAgent.h` — emit next-instruction in OffRoute/NoGpsSignal from last-known state.
  - `libosmscout-client-java/src/OSMScoutClient.cpp` — `JavaRouteInstructionBuilder`: along-route progress in `GenerateNextRouteInstruction`; real remaining distance for the arrival instruction instead of the hardcoded `0.0` in `OnTargetReached`.
  - Main repo: bump the submodule gitlink (commit) after the submodule changes land.
- **Kotlin — local app code**:
  - `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt` — clear stale instruction state in `startNavigation`; position-ordered step-index matching in `onNextRouteInstruction`.
  - `app/src/main/java/com/naviveylin/navigation/AANavigationController.kt` — same two changes.
- **Both phone and Android Auto / AAOS** share the native distance source; parity requirement is explicit in the specs (same display semantics; only host rendering differs).
- **Guidelines**: no guideline document contradiction — `guidelines/MapRendering.md` (render pipeline) is untouched; native purity rule already enforced by CI. If the step-index matching semantics change the route-summary "current step" contract, `guidelines/UI.md` parity notes are re-checked during apply (no expected edit).
- **Tests**: unit tests for the mapper/controller state handling (stale-instruction clear, position-ordered index); native-side changes verified behaviorally on-device (logcat + GPX replay per the change's tasks) since the bridge's instruction builder is exercised via the full engine.

## Rollback

Native submodule changes are reverted on the `naviveylin-local` branch (and the gitlink restored) — additive internal refactor, no ABI change, no format change. Kotlin changes revert in place. The change is **additive/behavior-fixing**, not breaking.
