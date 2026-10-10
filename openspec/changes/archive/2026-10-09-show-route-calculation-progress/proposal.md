# Proposal: show-route-calculation-progress

## Why

Starting navigation from an Android Auto favourite — or from the car details screen, a car
deep link, or an automatic reroute — calculates the route **silently**: the driver keeps
looking at the previous screen, with no indicator, no error and no way out, until the route
happens to arrive and swaps the screen. A calculation can take tens of seconds on a long
route or a thin map set, and that is exactly when the wait is longest and the driver most
needs to know the app is working.

The information already exists and is thrown away: libosmscout reports a 0–100 % progress
callback during the calculation, and **both** of the app's call sites implement `onProgress`
as an empty method. The phone's route panel does show a spinner, but never the percentage.

## What Changes

- **Calculation state**: the shared navigation state exposes the in-flight route calculation
  (`isCalculating`), its percentage (`calcPercent`, 0–99, bucketed before it reaches state)
  and a monotonically increasing request token, so a calculation superseded by a newer one
  cannot clear the live calculation's state.
- **Cancel**: a new engine entry point `cancelAcquisition()` aborts the in-flight native
  calculation (`OSMScoutClient.cancelRoute()` — the cooperative breaker), clears the
  calculation state and releases the navigation lease the surface-less acquisition took.
  Native abort delivers `onCancel`, so the engine is told, not guessing.
- **Android Auto**: a new `RouteCalculatingScreen` (`PaneTemplate`, built as a mirror of the
  existing `ErrorOverlayScreen`) is pushed by the car session while an acquisition runs. It
  shows the percentage as text plus a Cancel action, and it appears only if the calculation is
  still running after a short delay (~400 ms), so a fast route never flashes a screen. A
  reroute shows the same notice over the live navigation view — **without** a Cancel action,
  because guidance stays live.
- **Phone**: the route panel's existing calculating row gains the percentage (one state field
  on the panel view model, one row edit). No new screen and no delay — the row is already on
  screen and already covers a "progress indicator" today.
- **Native** (`JavaRoutingProgress`, `libosmscout-client-java` submodule, branch
  `naviveylin-local`): an early-out so the callback reports a *changed* percentage (with
  roughly a 100 ms floor) instead of one JNI crossing per A\* edge relaxation. Today a long
  route makes 10^5–10^6 crossings from the routing thread into Kotlin methods that discard
  the value, lengthening the very calculation the driver is waiting for.
- **Measurement**: one coordinate-free diagnostics line when a calculation ends (source,
  duration in ms, number of percent samples, cancelled) so the delay threshold is set from
  observed durations rather than guessed.
- **BREAKING**: none. All state additions are additive, the new screen is new, and the
  existing `isRerouting` / `isOffRoute` semantics are unchanged.

## Capabilities

### New Capabilities
- `route-calculation-feedback` — the wait for a route becomes observable: what the shared
  state exposes (in-flight calculation, percentage, supersession token), what cancelling
  guarantees, and what each surface shows while a calculation runs (car notice with delay and
  Cancel rules, phone panel percentage), including the coordinate-free measurement line.

### Modified Capabilities
- `navigation-engine` — the engine owns route acquisition and the shared navigation state; it
  gains the calculation state (in-flight, percentage, request token), the cancel entry point,
  and the rule that an aborted surface-less acquisition releases its location lease.
- `route-panel-ui` — its "Progress indicator during calculation" contract is extended from an
  indicator to an indicator carrying the percentage, on the same already-visible row.
- `auto/navigation-view` — an acquisition in flight is shown as a notice instead of silence,
  the notice never outlives the calculation, and a reroute keeps the navigation view live
  underneath it.
- `rerouting-visual-feedback` — the reroute state it owns (`isRerouting`) now carries the
  calculation percentage, and the reroute notice obeys the same delay rule (with no Cancel).

Deliberately **not** modified, so the boundary is explicit rather than drifting:
`car-host-fault-isolation` (the notice must comply with its guarded-host-callback,
main-thread-invalidate, screen-stack-bookkeeping and deferred-mutation rules — nothing in it
changes), `auto-diagnostics` (the new line complies with its coordinate-free rules and adds no
new category), `auto-favorites` (its "list is replaced by the `NavigationTemplate`" scenario
keeps its end state; the notice is transient in between).

## Impact

Affected modules and files:

- `:core` — `NavigationState.kt` (calculation fields), `NavigationViewModel.kt` (cancel entry
  point on the shared abstraction).
- `:app` — `navigation/NavigationEngine.kt` (set/clear calculation state, request token,
  `cancelAcquisition()`, lease release, marshalling the native progress callback to the main
  thread), `navigation/NavigationViewModel.kt` (delegate), `ui/route/RoutePanelViewModel.kt`
  and `ui/route/RoutePanel.kt` (percentage in the panel row), `res/values/strings.xml`.
- `:auto` — new `RouteCalculatingScreen.kt`; `NavigationSession.kt` (delayed push, in-place
  update instead of a second notice, identity-scoped removal on success/error/cancel,
  behaviour while the session is stopped); `SessionScreenStack.kt` (bookkeeping for the new
  transient notice); `res/values/strings.xml`.
- Native — `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp`
  (`JavaRoutingProgress::Progress` early-out) as a **submodule patch** (minimal, upstreamable,
  committed on `naviveylin-local` with the gitlink bumped afterwards). No local override in the
  `:osmscout-client-java` module: the change is in C++, not in the overridden Java files.

Android components touched: the car app screens and session (`CarAppService` side), the
existing `:auto` screen stack, strings, and no manifest or dependency change.

Guidelines: `guidelines/UI.md` gains a short rule for car wait notices (when a notice may be
pushed, and that it never outlives the calculation it describes); `guidelines/Design.md`
already requires main-thread-confined state publication, which is what the progress callback
must obey — no change expected, to be confirmed in design; `MapRendering.md` and `Build.md`
are unaffected.

Scope: a general feature, not car-only — the state and the cancel contract are shared, the
car gets the notice, the phone gets the percentage on its existing panel row.

Rollback: revert the change. The state fields and the screen are additive, the panel row
falls back to the plain indicator, and the native early-out is reverted by a submodule revert
plus a gitlink bump.

Open question carried into design: `core/NavigationState.kt:38` references a spec
`routing-progress-indicator` that does not exist anywhere in `openspec/specs/`. Either the
comment is repointed at `route-calculation-feedback` or it is dropped.
