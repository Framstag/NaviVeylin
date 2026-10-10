# Design

## Context

See `proposal.md` — Why. The constraints that shape the approach, as they exist today:

- **One call site, two meanings.** `NavigationEngine.calculateAndStart()`
  (`app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt:366`) is reached from both the
  surface-less acquisition (car favourite, car details, car deep link via
  `NavigationEngine.navigateTo`, `:428`) and from `confirmReroute()` (`:701`, which sets
  `isRerouting`). It is the only place a route is calculated for navigation. The phone's route
  panel calculates separately (`RoutePanelViewModel.calculateRoute()`, `:402`) and never starts
  navigation.
- **The percentage exists and is discarded.** `JavaRoutingProgress::Progress`
  (`app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp:5651`) reports
  `currentMaxDistance / overallDistance * 100`, capped at 99, and is invoked once per successful
  edge relaxation in `AbstractRoutingService.cpp:818` — on the routing worker thread, with an
  `AttachCurrentThread` and a JNI call into Kotlin. Both Kotlin call sites implement `onProgress`
  as an empty method (`NavigationEngine.kt:379`, `RoutePanelViewModel.kt:437`).
- **Cancellation exists and is unused by the engine.** `OSMScoutClient.cancelRoute()` sets the
  cooperative breaker (`OSMScoutClient.cpp:7103`); an aborted calculation fires `onCancel`
  (`:6261`). The engine has no entry point for it — only `RoutePanelViewModel:523` calls it.
- **A new acquisition already aborts the previous one natively** (`data->breaker->Break()` before
  the new breaker is installed, `OSMScoutClient.cpp:5929`), so the *state* — not the native work —
  is what needs supersession protection.
- **Car screens are host-rendered**: there is no determinate progress widget in the Car App
  Library. `ErrorOverlayScreen` (`auto/.../ErrorOverlayScreen.kt`) is the existing precedent for a
  transient `PaneTemplate` notice pushed by the session with `updateMessage()` + `invalidate()`,
  identity-scoped removal and guarded host calls; the session's rules are
  `guidelines/UI.md` §3b ("never an infinite spinner") and AGENTS.md's car-host section.
- **Host traffic must be bounded.** `NavigationTemplateMapper.hasStateChanged` already compares
  display-rounded values (`displayedDistanceBucket`) so a sub-display change never costs a host
  push; the notice's percentage needs the same treatment.

## Goals / Non-Goals

**Goals:**

- The wait for a route is visible on both surfaces, with the percentage the native engine already
  reports, and cancellable where cancellation is safe.
- One owner of the calculation state (the engine) so both surfaces observe the same wait.
- Remove the wasted JNI traffic from the routing critical path rather than hide it.
- Make the notice delay a measured constant, not a guess.

**Non-Goals:**

- No determinate progress bar on the car screen (the host renders templates; only text + spinner
  are available). A bar would require drawing into our own surface — rejected for this change.
- No new JNI method: `cancelRoute` and the progress callback already exist. The native change is a
  rate/early-out change only.
- No change to reroute *trigger* logic (`RerouteConfirmationGate`, noise guards), to the error
  notice flow, or to the navigation notification.
- No automatic timeout/cancel for a hung calculation — the notice's Cancel is the way out.
- No minimum-visible dwell for the notice (declined during exploration; revisit only if the
  measurement shows a populated 400–800 ms band).

## Decisions

### D1. The engine owns the calculation state

The engine, not a surface, publishes the in-flight calculation. It is already the single owner of
`NavigationState` and of the native client call; a second publisher would mean two sources of truth
for one native operation.

*Alternatives:* a separate `RouteCalculationRepository` in `:core` observed by both surfaces —
rejected: it would either duplicate the client call or wrap the engine, adding a layer without
removing one. Keeping the phone panel's own state only (status quo) — rejected: it leaves the car
path silent, which is the bug.

`core/NavigationState.kt` gains

```kotlin
data class RouteCalculation(
    val token: Long,          // increases per request
    val destLat: Double,
    val destLon: Double,
    val percent: Int?         // null until the engine reports progress
)
// on NavigationState:
val calculation: RouteCalculation? = null
```

The trigger of a calculation (a surface-less acquisition versus a reroute) is not part of the
state — nothing renders it — and lives in the measurement entry only (D8).

*Alternatives:* flat `isCalculating` + `calcPercent` + `calcToken` — rejected: three fields can be
inconsistent, and `percent == 0` cannot be told from "unknown"; a nested nullable object makes
"in progress" and its data one atomic fact.

### D2. Supersession is a token the engine compares, not a lock

`calculateAndStart` stamps `token = ++lastToken` into the state before starting the native call;
`onSuccess` / `onError` / `onCancel` clear the state only if `lastToken == myToken`. A superseded
calculation therefore cannot clear the live one's state, and a late `onCancel` from a superseded
calculation is ignored.

*Alternatives:* cancel the running calculation before starting a new one in Kotlin — rejected: the
native side already aborts the previous breaker on the next `calculateRouteWithProfile`, so the
extra Kotlin `cancelRoute()` changes timing without changing the outcome and races the new
calculation's breaker install. Waiting for the aborted worker to finish before starting the next —
rejected: it would delay a driver's second tap by the tail of the aborted search.

### D3. Progress is rate-limited in native and bucketed before it reaches state

`JavaRoutingProgress::Progress` gets an early-out: report only when the percentage actually
**increased** and at least a minimum interval (~100 ms) has passed since the last report. This makes
the callback mean "the percentage changed" instead of "a node was expanded", removes 10^4–10^6 JNI
crossings per long route from the routing thread's path, and makes the reported number monotone in
passing. The 99 cap stays.

*Alternatives:* throttle in Kotlin (the JNI crossing and the Kotlin call already happened — the
routing path pays it; rejected), no throttle (each call from the routing thread would write state
and drive an `invalidate()`; rejected), throttle only by percent delta (a long route at the same
percentage for seconds still floods; rejected).

The engine then publishes the value **only when the integer differs** from the state's current one,
and the car notice renders it in 5 % steps — the same idea as `displayedDistanceBucket`, so a
calculation costs at most ~20 host pushes instead of hundreds. The phone row shows the exact
reported integer (it is recomposed on the Compose side, not pushed to a host).

*Trade-off accepted:* the last percentage before completion can be dropped (interval floor), which
is invisible because the route's arrival ends the wait.

### D4. Threading

| Component | Dispatcher / thread | Notes |
|---|---|---|
| `calculateRouteWithProfile` | `scope` + `Dispatchers.Default` (existing) | native call off the main thread, per `navigation-engine` |
| `onProgress` | native routing thread → `scope.launch(Dispatchers.Main)` | state publication stays main-thread confined |
| `cancelAcquisition()` | engine scope `Dispatchers.Default` | touches the routing thread's breaker; never blocks a host callback |
| delay timer + notice push/update | car session scope (`carSessionScope()`), `Main` | `postTemplateRefresh` for the `invalidate()` |
| diagnostics entry | `DiagnosticsLog` (buffered) | caller never touches the file system |

The progress callback is the only new high-frequency path and it never touches the host, the
filesystem, or the native client; it writes a main-thread-confined state value at most once per
changed integer.

### D5. Car feedback is a new transient notice screen, delayed at the display site

`auto/.../RouteCalculatingScreen.kt` mirrors `ErrorOverlayScreen`: a `PaneTemplate` with a header
(title: the calculation is running), a row carrying the destination when known and the percentage
in 5 % steps, and — only when navigation is **not** active — an action that cancels. Its build goes
through the same guarded `carPaneTemplate` wrapper as every other car screen, its push/pop through
`guardedHostCall`, its removal by identity (`ScreenManager.remove`, never a `popToRoot`) with
`SessionScreenStack` bookkeeping that follows the mutation that succeeded, and its `invalidate()`
through the main-thread path. A second calculation updates the existing notice
(`updateMessage` + `invalidate`) instead of pushing another.

The delay lives in the **session**, not in the engine: the engine publishes the state immediately
(so the phone can show it), and `NavigationSession` starts a ~400 ms timer on the calculation
token; if that token is still live when the timer fires, the notice is pushed. A calculation that
ends first — while the session is stopped or while the notice is up — leaves no notice behind
(the push is deferred by `SessionHostGate` and re-evaluated against the live token on start).

*Alternatives:* engine-side delay (hides the wait from the phone and makes the state lie about when
the work started — rejected); reusing `PlaceListTemplate.setLoading(true)` on the favourites screen
(cheap, but the driver loses the list, gets no percentage and no cancel, and the loading row is
host-owned — rejected); reusing the navigation template's loading trip (`NavigationTrip` requires
an active navigation session, which does not exist yet at this point — rejected).

### D6. Cancel is optimistic, and a cancelled acquisition releases its lease

`NavigationViewModel`/`NavigationEngine` gain `cancelAcquisition()`: it requests
`client.cancelRoute()`, releases the lease the surface-less acquisition took (`releaseNavLease()`,
matching the failure paths), clears the calculation state and starts nothing. The state is cleared
immediately rather than waiting for the native `onCancel`: the notice must not survive its own
cancellation, and a late `onCancel` / `onError` from the aborted worker is swallowed by the token
guard (D2).

*Alternatives:* wait for `onCancel` before clearing (a hung or long-aborted worker leaves the
notice up — exactly the failure the change exists to remove; rejected); pop the notice without
asking native to stop (the calculation would finish and start navigation the driver just declined;
rejected).

The phone panel keeps its own `client.cancelRoute()` (it owns a different, non-navigating
calculation): both paths now cancel the same native operation, which is safe because only one
calculation can be in flight — the native side breaks the previous one on a new request.

### D7. The phone panel mirrors the percentage, it does not acquire

`RoutePanelViewModel` adds a `percent: Int?` to its existing `RouteState.Calculating` and its
`onProgress` writes it; `RoutePanel` renders it next to the existing indicator. The panel keeps its
own calculation and its own cancel.

*Alternatives:* route the panel's calculation through the engine (PH3 during exploration) —
rejected: the panel's calculation is a preview that does not start navigation, so it would bend the
engine's acquisition contract to save one field; PH1 (leave the phone alone) — rejected: the same
number is already available and the car and phone would disagree about what the wait can show.

### D8. Measurement and the stale spec reference

The engine logs one coordinate-free diagnostics entry when a calculation ends —
`ROUTE calc done: source=acquisition duration=812ms percents=17 outcome=ok` — through
`DiagnosticsLog` (buffered; the build gate `checkNoCoordinatesInLogs` polices it like every other
line). `source` is the trigger (`acquisition` or `reroute`), never a position, and `outcome` is
`ok`, `error` or `cancelled` so the delay threshold is set from *successful* durations — a failing
calculation never shows a notice. The 400 ms delay constant is **provisional** and is set from the
duration distribution of one on-device run.

`core/NavigationState.kt:38` currently cites a spec `routing-progress-indicator` that does not exist
in `openspec/specs/`. The citation is removed and the field is described by what it actually is
(wall clock when navigation started, consumed by the phone's elapsed-time display); the field has
nothing to do with calculation progress, so pointing it at the new spec would be wrong.

## Risks / Trade-offs

- **The percentage is search-frontier progress, not route-length progress** → it is monotone but can
  stall for seconds and then jump. Mitigation: the host's own spinner carries "still working", the
  number is a hint, and Cancel is always available; the diagnostics entry records `percents=N` so a
  stalling run is visible after the fact. If it proves misleading in the field, dropping the number
  (keeping the notice) is a one-line UI change that touches no spec behaviour other than D5's
  content.
- **The notice flashes in the 400–800 ms band** → **Measured 2026-10-05**: the band is populated — an
  847 ms town route pushed the notice and the navigation view replaced it 443 ms later. Mitigation: the
  delay stays 400 ms (no successful route below it was observed, so nothing is missed), and the deferred
  minimum-visible dwell is filed as `TODO.md` §133 rather than built here. This is the trade-off accepted
  when D1 (delay only) was chosen during exploration — now with evidence instead of an assumption.
- **A duplicate or orphan notice on the host stack** (push/pop asymmetry around a stop) →
  Mitigation: `SessionScreenStack` bookkeeping that follows the successful mutation, removal by
  identity (`ScreenManager.remove`), `guardedHostCall` around every mutation, and the explicit rule
  that a calculation which ended while the session was stopped is never shown.
- **An invalidate storm from percentage updates** → Mitigation: 5 % display bucket for the host,
  integer-difference check in the engine, main-thread-only `invalidate()`.
- **The native early-out drops the final percentage before completion** → harmless: the route's
  arrival removes the notice; no state depends on the last value.
- **Cancel while the worker is deep in the search, then an immediate second tap** → the breaker is
  cooperative, so the old worker may hold the routing mutex briefly while the new request installs
  its own breaker and waits. This is the existing native behaviour (a second
  `calculateRouteWithProfile` already broke the previous one); the token guard keeps the *state*
  correct in the meantime.
- **Reroute notice over the navigation view could hide the instruction panel** (host-rendered
  notice is a full template) → Mitigation: it is only pushed after the delay and reroutes usually
  resolve fast; the measurement will show whether the band is populated, and that is the trigger to
  revisit (minimum-visible dwell, or no notice during a reroute).
- **Submodule patch drift** → Mitigation: a five-line change in app-bridge code, upstreamable as a
  real fix, committed on `naviveylin-local` with the gitlink bumped after (per AGENTS.md).
- **Robolectric/Compose test surface** → the notice's template build must stay constructible in a
  unit test (like `ErrorOverlayScreen`); anything needing a real host (the session itself) is
  covered by the pure helpers it delegates to (the delay gate, the bucket, the notice decision).
- **Platform constraint found in implementation (deviation from the exploration's C1)**: car-app 1.7's
  `androidx.car.app.Screen` exposes **no** back callback (`onBackPressed` does not exist — verified against the
  1.7.0 sources and by compilation), because back is a host-driven stack pop. A back affordance could therefore
  only *pop* the notice while the routing work keeps running invisibly, which is exactly what C1 wanted to
  prevent. The notice now has **one exit**, the row's Cancel action: it does not enable back navigation and
  carries no header back action (`guidelines/UI.md` §3c updated accordingly). Device check: pressing back while
  the notice was up did not pop it and the calculation ran on.
- **The mirrored car UI has no accessibility tree** (AAOS distant-display AVD): `uiautomator dump` on the
  internal display returns an empty hierarchy for the templated UI, so the notice's on-screen text cannot be
  asserted by node. Mitigation used: the session logs what it gave the host
  (`Diag/HOST: calculation notice push/update pct=…`), which is what the 5 %-step bound was verified with.

## Verification

- **Unit, `:app` (`app/src/test`)**: engine state machine — begin / success / error / cancel; the
  token guard (a superseded `onCancel` leaves the live calculation in progress); the lease release
  on cancel and on failure; the diagnostics entry content (source, duration, count, cancelled,
  no coordinates); the panel's `percent` state.
- **Unit, `:auto` (`auto/src/test`)**: `RouteCalculatingScreen` template — percentage text,
  destination when known, a cancel action only when not navigating; the session's delay gate
  (no notice before the delay, no notice when the calculation ended first, exactly one notice,
  identity-scoped removal); the stack bookkeeping; the "ended while stopped" case.
- **Build gates**: `checkNoCoordinatesInLogs` covers the new line; the existing both-flavour test
  gate runs once before the commit.
- **On device** (`device-check` / `pixel-check` skills): tap car favourites for 5–10 destinations
  spanning a short town route and a long one, read the `ROUTE calc done:` durations from
  `adb logcat -s NaviVeylin`/the diagnostics screen, and confirm (a) fast routes never show a
  notice, (b) slow ones do, with a percentage that updates in 5 % steps, (c) Cancel aborts and
  leaves no navigation running and no navigation notification, (d) a reroute shows the notice over
  a live navigation view with no cancel. The delay constant is then fixed from (a)/(b), and the
  populated-band question decides whether the deferred dwell is still needed.
