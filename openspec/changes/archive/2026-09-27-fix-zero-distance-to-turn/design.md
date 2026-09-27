# Design: Fix distance-to-turn stuck at "0 m"

## Context

See proposal.md — Why. The displayed step distance is computed per GPS update by the JNI bridge `JavaRouteInstructionBuilder::GenerateNextRouteInstruction` (OSMScoutClient.cpp:1935), driven by the upstream agent chain `PositionAgent` → `RouteInstructionAgent` (libosmscout submodule). Current math: `distanceTo = nextAbs − nodeDist − travelled`, where `travelled` is the **straight-line** ellipsoidal distance from the snapped `coord` to the last passed `routeNode`. This equals along-route progress only when the vehicle is immediately past `routeNode`; any lag of `routeNode` behind the moving vehicle (degraded GPS, forward-only 20 m snap search failing, search hitting the route's last node) makes `raw ≤ 0` and the clamp `(raw > 0) ? raw : 0` pins the value at 0 m while the maneuver name still renders. `PositionAgent` aggravates this by resetting `routeNode` to `route->Nodes().begin()` and flipping to OffRoute whenever the forward search fails, and `RouteInstructionAgent` stops emitting next-instructions outside OnRoute/EstimateInTunnel — so a suppressed reroute (phone gates: `REROUTE_COOLDOWN_MS`, `MAX_REROUTE_ACCURACY` 25 m, tunnel guard) leaves the panel frozen on the last (0 m) instruction.

## Goals / Non-Goals

**Goals:**

- Distance to an upcoming maneuver is a true along-route remaining distance that never clamps to 0 while the maneuver is ahead.
- No spurious engine state resets: `routeNode` survives transient forward-search failures; the instruction emission continues in OffRoute/NoGpsSignal so the panel never freezes.
- Reroute restarts are clean on the Kotlin side: no stale instruction flash, correct step-index advancement.
- Fix lands as platform-pure submodule patches (upstreamable to Framstag/libosmscout) plus local Kotlin edits — no Android deps in native code (CI-gated).

**Non-Goals:**

- No change to reroute *triggering* policy (`reroute-trigger` spec, gates, cooldowns) — only to the engine state that feeds the display.
- No change to the phone's route-drawing/panel flows (`routeResultFlow`), lanes, ETA, or street-name rendering.
- No rework of `NavigationState`'s public shape (additive field on the native `PositionMessage` only, C++-internal).

## Decisions

### D1: Publish the segment abscissa; compute along-route progress in the bridge

**Chosen:** `PositionAgent::SearchClosestSegment` already computes `foundAbscissa` (fraction of the current segment where the snapped point lies) but discards it. Expose it on `PositionAgent::Position` (and propagate through `PositionMessage`). The bridge then computes progress as `segmentLen * abscissa` where `segmentLen = |routeNode→(routeNode+1)|` (the bridge derives the next node from `previous+1`), and uses `distanceTo = nextAbs − nodeDist − progress`. Remaining distance is then measured along the route, immune to cross-track GPS error and curves.

- `libosmscout/include/osmscout/navigation/PositionAgent.h` — add `double abscissa` (and, for completeness, the segment-end node reference is derivable, so keep the change minimal: abscissa only) to `Position`; set it in the `SearchClosestSegment` call site; default 0 in `findNearest`.
- `libosmscout-client-java/src/OSMScoutClient.cpp` — `GenerateNextRouteInstruction`: `progress = segmentLen * previousAbscissa`; keep the `raw > 0` guard as a defensive clamp but it can no longer trigger while on-route with correct `routeNode`.

**Alternatives considered:**

- *Bridge walks nodes itself to project the coord* — rejected: duplicates the projection math the engine just did, drifts from the engine's notion of "current segment", and costs a node walk per GPS tick.
- *Clamp straight-line travelled to the segment length* — rejected: still wrong on curved segments and when cross-track error dominates; masks the symptom without fixing the semantics.
- *Leave `travelled` as-is and only fix the clamp* — rejected: the frozen-0 symptom literally IS the clamp firing; the input must be fixed.

**Risk:** the abscissa adds a field to `Position`/`PositionMessage` — internal C++ structs consumed only by `DispatchPositionEstimate` and agents in the same submodule; no JNI signature change. Low.

### D2: Stop resetting `routeNode` to the route start on search failure

**Chosen:** In `PositionAgent::Process` (Good-GPS branch), when `SearchClosestSegment` fails, keep the last `routeNode` (do not reset to `begin()`) and keep `findNearest` only for the *snap/way* resolution, not for the route-node position. State still becomes OffRoute when the vehicle is beyond snap (so `reroute-trigger` semantics are unchanged — a real deviation still reroutes), but the route-node position no longer teleports to the route start, which was a fabricated desync that both caused cascading reroutes and left `GenerateNextRouteInstruction` computing nonsense distances on the "new" position.

**Alternatives considered:**

- *Status quo (reset to begin)* — rejected: demonstrated to produce reroute storms and the frozen-instruction panel.
- *Hold routeNode but keep state OnRoute* — rejected: hides genuine off-route conditions; `reroute-trigger` and `off-route-indicator` depend on the OffRoute state being truthful.

**Risk:** between a deviation and the reroute, the bridge now computes the next instruction against the last-known route node (a plausible, if stale, value) instead of nothing — this is the display behavior the specs require ("SHALL either show an up-to-date instruction or clear"). The off-route indicator still shows the truth. Low.

### D3: Emit next-instruction in OffRoute and NoGpsSignal

**Chosen:** `RouteInstructionAgent::Process` currently emits live next-instructions only for OnRoute/EstimateInTunnel. Change to emit for every published `PositionMessage` (all states except Uninitialised), using `position.coord` + `position.routeNode` (made stable by D2). With D2, the math stays valid while off-route; the panel keeps updating instead of freezing on the last 0 m value, and the "stale instruction" spec scenario passes.

**Alternatives considered:**

- *Emit a clearing/empty instruction in degenerate states* — rejected: an empty maneuver rendered by the host looks like a crash ("blank step"); better to keep showing the (possibly slightly stale, but monotonic) remaining distance plus the separate off-route indicator.
- *Keep silence* — rejected: that is the freeze bug.

**Risk:** during a long tunnel (NoGpsSignal), the emitted instruction uses the last known coord — distance freezes by definition until GPS returns, which is truthful (no fix = no progress). Acceptable; matches user expectation.

### D4: Real remaining distance for the arrival instruction

**Chosen:** `CollectCallback::OnTargetReached` hardcodes `instr.distanceTo = 0.0` (absolute), which the `while (it->distanceTo <= nodeDist)` skip in `GenerateNextRouteInstruction` then discards, so after the final maneuver the bridge returns an **empty** instruction (`turnType=""`, description "") instead of an "Arrive — X m" step. Set the arrival instruction's distance to the node's `GetDistance()` like every other instruction; the relative conversion in `GenerateNextRouteInstruction` then yields the true remaining distance to the destination, and the final panel step is "Arrive — <distance>".

**Alternatives considered:**

- *Status quo (empty instruction after last maneuver)* — rejected: shows a blank/0 m panel instead of arrival.
- *Synthesize the arrival step in Kotlin when `nextInstruction` is empty* — rejected: masks the native gap and adds divergence between phone and AA.

**Risk:** the full-route description list's last row changes from "Arrive — 0 m" (a wrong absolute 0 at the route start) to the destination's actual distance — strictly more correct. Low.

### D5: Kotlin — clear stale instruction state on `startNavigation`; step index by position-ordered search

**Chosen (A):** Both `NavigationViewModel.startNavigation` (NavigationViewModel.kt:146) and `AANavigationController.startNavigation` (AANavigationController.kt:218) already reset `currentStepIndex = 0`; extend the state copy to also clear `instructions` and `nextInstruction` so the previous route's last step cannot render over the new route before the new engine's first `onRouteInstructions` emission.

**Chosen (B):** Replace `indexOfFirst { it.description == instruction.description }` in both `onNextRouteInstruction` handlers with a search beginning at `currentStepIndex` (first match at/after the current step; unchanged index if none). This prevents the index jumping *backward* to an earlier duplicate of the description ("Straight on", looped street names), which currently mis-labels the active step in the route summary and the route-description screen. The live instruction is always an upcoming step, so its description occurrence is at index ≥ current index; `onRouteInstructions` re-sets the index to 0 on route change.

**Alternatives considered:**

- *Match by distance instead of description* — rejected: the live `distanceTo` is relative while the list's values are absolute-from-route-start; not comparable without the engine's route-node, which Kotlin does not expose.
- *Let Kotlin maintain the list via the agent's re-emitted (popped) full lists* — rejected: larger surface for marginal gain; the popped list is transient and the live `nextInstruction` is already the authoritative display source.

**Risk:** duplicate descriptions *within* the still-ahead portion can still bind to the first duplicate ahead of the current index — cosmetic (highlight only), bounded, and strictly better than today.

## Threading & Lifecycle

- **Native:** no new threads. `PositionAgent`/`RouteInstructionAgent`/bridge builder all run on the existing navigation-engine background thread (messages processed in order; `JavaNavigationController::Run` dispatches position updates with the engine's agent chain). New state is plain fields on `Position`/`PositionMessage` written and read on that same thread; `abscissa` is read-only within the message.
- **Bridge:** `GenerateNextRouteInstruction` stays stateless (inputs: route iterators + coord + abscissa) — no new lifetime concerns (submodule `libosmscout-client-java/AGENTS.md`: single TU; JNI locals released as today).
- **Kotlin:** existing `viewModelScope`/`scope` + `Dispatchers.Main` marshaling untouched; the `startNavigation` state copy and step-index change are synchronous state updates on Main. No new coroutine scopes, no lifecycle holders.

## Risks / Trade-offs

- [Bridge shows a plausible-but-stale distance for a few seconds during a genuine deviation] → Mitigation: monotonic countdown from the last-known route node; the off-route indicator (spec `off-route-indicator`) shows the truth; the reroute replaces the values shortly after.
- [D2 changes `PositionAgent` behavior that `reroute-trigger` tests depend on] → Mitigation: OffRoute state still surfaces for any >20 m deviation, so timing specs are untouched; only the route-node *position* reset is removed. The existing reroute-trigger unit/on-device coverage is re-run during apply.
- [Submodule patch must stay upstreamable and Android-free] → Mitigation: only platform-pure C++ (no logging API changes, no Android headers); `osmscout::log` used for any diagnostics; CI `Check libosmscout Android-free outside Android/` gate is re-run.
- [PositionMessage field addition ripples to `DispatchPositionEstimate`] → Mitigation: additive, C++-internal; compile-time checked by the CMake build; no JNI signature change.
- [Kotlin step-index binding still ambiguous for ahead-duplicates] → Mitigation: cosmetic (active-step highlight only); documented limitation, no user-facing distance impact.

## Migration Plan

1. Submodule (branch `naviveylin-local`): D1 (`PositionAgent.h`/`.cpp` abscissa), D2 (no routeNode reset), D3 (`RouteInstructionAgent.h` emission), D4 (arrival distance in bridge builder) — commit in the submodule, push, bump the gitlink in the main repo (`git submodule` + commit), per AGENTS.md "bump the submodule SHA after any submodule commit".
2. Kotlin: D5 in `NavigationViewModel.kt` and `AANavigationController.kt`.
3. Rollback: revert the submodule commit (restore gitlink) + revert the two Kotlin files; behavior returns to the current freeze — no data migration, no ABI change, no stylesheet impact. No asset/manifest changes.

## Verification

- **Unit (host JVM, JNI-stub pattern per AGENTS.md):** AANavigationController/NavigationViewModel tests: `startNavigation` clears stale `instructions`/`nextInstruction`; `onNextRouteInstruction` with duplicate descriptions advances the step index monotonically from the current position; index keeps old value on no-match. Mapper: `routingInfoFromState` renders the live `nextInstruction` distance (0-free when non-zero), arrival step shown for the destination instruction. Must run under Robolectric DEFAULT sandbox (classloader rule).
- **Native formula + agent behavior:** host JVM cannot load the Android `.so` (stub is symbol-free) — the engine path must be verified on-device: logcat (`adb logcat -s NaviVeylin` plus a temporary `Log.d`/`osmscout::log.Debug()` of `distanceTo`, `nodeDist`, `progress` per tick) during a scripted deviation (GPX replay or mock-GPS app): observe (a) countdown stays non-zero until the actual turn, (b) no routeNode reset to start on a short forward-search miss, (c) instruction keeps updating while a reroute is suppressed, (d) final step shows "Arrive — <remaining>" after the last turn.
- **Build:** `./gradlew :app:assembleMobileDebug` and `assembleAutomotiveDebug` (all three ABIs for the native change) via the build-app skill; `./gradlew test` via run-tests; existing `reroute-trigger`, `off-route-indicator`, `navigation-view`, and `NextTurnOverlay` test suites stay green.
