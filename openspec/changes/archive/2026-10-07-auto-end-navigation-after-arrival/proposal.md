# Proposal

## Why

Navigation never ends by itself. The native engine already reports the destination reached
(`osmscout::TargetReachedMessage`, on route within 30 m of the target), the JNI bridge already
forwards it to `NavigationListener.onTargetReached`, and the app's listener drops it
(`NavigationEngine.kt:864`, a no-op). So a driver who arrives, parks and leaves the car keeps a
running navigation session: the car host keeps its ETA card, the phone keeps the ongoing
notification, and the engine keeps a location lease on a route that was already driven.

The first step is the case the driver notices on the car display: the destination was reached and
the car session ended (the phone left the car, the session was destroyed) — then navigation should
end with it. The fact that the destination was reached belongs to the navigation session, not to the
car, so it is captured in the shared navigation state; the car end rule is the first consumer, the
phone surface a later one.

## What Changes

- **Arrival becomes part of the shared navigation state.** `NavigationState` gains
  `hasReachedDestination` (in-memory, coordinate-free, false by default). The engine sets it when the
  native engine reports the target reached, keeps it across a reroute (the reroute still targets the
  destination already reached) and clears it on a new navigation start and on a stop.
- **The car session ends navigation when it ends after arrival.** In `NavigationSession.onDestroy`,
  if navigation is active and the destination was reached, the session stops navigation; otherwise
  navigation keeps running unchanged. The exit goes through the existing `stopNavigation()` path, so
  the car navigation view pops, the host navigation session ends, the route guidance stops, the
  location lease is released and the ongoing notification is removed by the already-specified
  reaction to `isNavigating == false`.
- **No setting.** The behaviour is unconditional (owner decision, 2026-10-07).
- **No reroute behaviour change.** Reaching the destination does not suppress the engine's reroute
  path (owner decision); the arrival fact is instead retained across a reroute.
- **No native, JNI or submodule change.** `onTargetReached` is already delivered to Java; only the
  Kotlin listener's no-op changes.

Additive, no **BREAKING** change. Rollback: revert the commit — the arrival fact is in-memory only
and nothing is persisted, so no data or state migration is involved.

## Capabilities

### New Capabilities

None. Both behaviours belong to capabilities that already own them.

### Modified Capabilities

- `navigation-engine`: the shared navigation state gains the arrival fact — set on the native
  target-reached report, retained across a reroute, cleared on a new start and on a stop.
- `auto/navigation-view`: gains the automatic exit as a new requirement beside "Leave navigation
  at any time" (whose user-initiated behaviour is unchanged) — when the car session ends and the
  destination was reached, navigation ends; when it ends without arrival, navigation keeps running
  for the phone. Reaching the destination while the session is live does not end navigation.

Related capabilities whose requirements already cover the consequences, and which this change
therefore does not modify (`navigation-ongoing-notification` — "Notification hidden when driving
ends"; `map-modes` — navigation end restores the prior mode): the exit uses the established stop
path, so their scenarios hold unchanged and are named as the cases to exercise.

## Impact

Affected code:

- `core/src/main/java/com/naviveylin/core/NavigationState.kt` — one new field
  (`hasReachedDestination: Boolean = false`).
- `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt` — `onTargetReached` sets the
  fact; `startInternal` clears it on a fresh start and keeps it on a reroute.
- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` — `onDestroy` evaluates the end
  predicate and stops navigation; one pure predicate helper next to the file's existing ones
  (`shouldRestoreFreeDriving`).
- Tests: a new engine arrival test in `app/src/test/java/com/naviveylin/navigation/`, a predicate
  test in `auto/src/test/java/com/naviveylin/auto/`.

Modules: `:core`, `:app`, `:auto`. No manifest, resource, Gradle or CMake change; no ABI-specific
build. No `osmscout-client-java` override and no `libosmscout` submodule patch (the listener
interface and its JNI call site already exist upstream-local).

Guidelines: `guidelines/Design.md` §4 (state publication is marshalled to the main dispatcher — the
arrival write follows the other `NavigationListener` callbacks) and the `:auto` host rules in
`AGENTS.md` (a lifecycle step is fault-confined; no bare host mutation, and this exit performs
none). No UI or rendering rule is touched.

Scope: the arrival fact is general shared state; the automatic exit is **car only** (Android Auto
projection and Android Automotive OS) in this change. The phone surface keeps navigation running
until the driver stops it — a later change may add a phone-side end rule on the same fact.
