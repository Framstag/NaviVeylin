# Proposal

## Why

A route calculation that fails reports one sentence — "Route calculation failed" — for at least ten
distinct causes inside the router. `RoutingResult` carries no reason at all: `Success()` is
`!route.IsEmpty()` (`AbstractRoutingService.h:52-101`), and `AbstractRoutingService::CalculateRoute`
returns that empty result from six different places (`:1031` target nodes, `:1042` start nodes, `:1149`
`WalkPaths`, `:1164` `WalkToOtherDatabases`, `:1306` open list exhausted, `:1338`
`ResolveRNodesToRouteData`). The user is told nothing they can act on, while the one line that does
identify the cause never leaves the native log.

Two of those causes are not worth reporting at all, because the router can avoid them. The endpoint is
chosen as the **nearest way that is routable by type and carries a route node**
(`SimpleRoutingService.cpp:614-638`), and only then is it required to have a route node reachable in a
permitted direction (`AbstractRoutingService.cpp:194/223` — `GetTargetForwardRouteNode` walks
`i-1 -> 0`, `GetTargetBackwardRouteNode` walks `i -> end`). On a way that is direction-restricted, one
walk is skipped by its gate and the other walks away from the route node, so `GetTargetNodes` fails and
the whole calculation is refused — although a connectable road may be 200 m away. **The observed failure
of this change is that case: "the target had no direct access to the road".**

Why now: the lines that distinguish these causes already exist and already reach Logcat — every branch
writes a distinct `osmscout::log.Error()`, which the app-owned bridge surfaces under tag `NaviVeylin`
(`guidelines/Logging.md` §1-2, `Error` is not gated). The diagnosis is therefore proven from the tree and
reproducible from one `adb logcat -s NaviVeylin` run, not guessed. What is missing is that the user sees
any of it, and that the router stops refusing routes it could calculate.

## What Changes

- **The router attaches endpoints the router itself can attach.** When the nearest routable way of the
  start or the target is not usable in a permitted direction, the calculation SHALL attach the endpoint to
  the nearest routable way that is, instead of failing. This removes the observed failure rather than
  describing it.
- **A failed calculation reports a reason, not a sentence.** `RoutingResult` carries a reason from a closed
  set; the JNI route callback publishes it alongside the existing message; the app turns it into a typed
  error instead of a `String`.
- **Every reason has user-actionable wording, chosen by the surface** from Android string resources —
  never English prose produced in native code — with the same labels on phone and car
  (`guidelines/UI.md` §1) and German alongside English (`i18n-l10n`).
- **Where a reason has an action, the surface offers it.** A missing or incomplete map / routing data
  offers the map manager on the phone; the car names the phone instead, because a car session must not
  launch a phone screen (`guidelines/UI.md` §1 — the precise-location precedent). A destination beside a
  road states where the drive will actually end.
- **The phone presents the errors the engine raises.** `NavigationState.errorMessage` is read only by the
  car today (`NavigationSession.kt:723,834`); `errorAppliesTo(SurfaceOrigin.PHONE)` exists
  (`NavigationState.kt:108`) and is called from nowhere, so an engine-raised error — a shared link, a car
  deep link, a phone "Navigate to" with no panel, a missing GPS fix — is invisible on the phone. That
  contradicts the existing requirement "Engine-wide error reaches every surface" (`navigation-engine`).
- **The cause is recorded where it can be read.** The existing coordinate-free route-calculation
  diagnostics entry gains the reason and the numbers that classify it, so the next occurrence is
  identifiable from the log without a second device session.

Not in scope:

- **Retiring the duplicate native route entry.** `calculateRouteWithObjectsAsync`
  (`OSMScoutClient.cpp:6975`) repeats the whole pipeline and its five messages. The reason mapping is
  shared by one helper so the two copies cannot drift, but consolidating the entry points is its own
  change.
- **The coordinate logging already present in the bridge.** `start.GetDisplayText()` /
  `dest.GetDisplayText()` (`OSMScoutClient.cpp:7145/:7151/:7160/:7166/:7198`) log raw coordinates — a
  violation of the coordinate rule that `checkNoCoordinatesInLogs` does not catch. It sits on the legacy
  entry, which the app does not call. Recorded as a `TODO.md` entry, not fixed here.
- **The all-or-nothing routing open.** `MultiDBRoutingService::Open` (`MultiDBRoutingService.cpp:61-91`)
  fails the whole routing service on the first database whose `router.dat` cannot load. This change
  *reports* that failure specifically; changing the open policy is its own change.
- **The two no-op avoid flags.** `avoidTolls` and `avoidUnpaved` are accepted by the bridge and do
  nothing but log (`OSMScoutClient.cpp:6265-6277`). Unused by the app; recorded as a `TODO.md` entry.

## Capabilities

### New Capabilities

- `routing-failure-reporting`: how a failed route calculation is reported — the closed set of reasons, the
  per-reason user-actionable wording supplied by each surface from string resources, the action a reason
  may offer, and which surface presents it.

### Modified Capabilities

- `osmscout-jni`: the routing entry point gains a failure reason from a closed set, and gains the
  requirement that a start or target beside a direction-restricted routable way is still routed.
- `i18n-l10n`: a failure originating in native code is reported as an identifier and worded by the
  surface from a string resource; native code supplies no user-facing text.
- `navigation-engine`: the requirement "Errors carry the surface that caused them" is extended so that
  every surface presenting navigation — the phone included — presents an engine-wide error.
- `route-panel-ui`: the "Calculate route" requirement's failure scenario now requires the reason's own
  message and its action rather than "an error message".
- `route-calculation-feedback`: the "Route calculation is measured without coordinates" requirement's
  entry gains the failure reason and the numbers that classify it.

## Impact

**Native — libosmscout submodule, patch is minimal and upstreamable** (`RoutingResult`'s own TODO asks for
it, `AbstractRoutingService.h:47`; the sibling result types already carry an explicit `success`):

- `app/src/main/cpp/libosmscout/libosmscout/include/osmscout/routing/AbstractRoutingService.h` —
  `RoutingError` enum and the reason on `RoutingResult`, plus the `RouteEndpoint` a position is being
  resolved for.
- `app/src/main/cpp/libosmscout/libosmscout/src/osmscout/routing/AbstractRoutingService.cpp` — the reason
  at each of the six return sites; the endpoint-attachment walk.
- `app/src/main/cpp/libosmscout/libosmscout/src/osmscout/routing/SimpleRoutingService.cpp` and its header
  — the attachability predicate and an **added** overload that takes the `RouteEndpoint`, so resolving a
  position for the start and for the target apply the end's own direction rule. The existing signatures
  keep their behaviour for their existing callers.
- `app/src/main/cpp/libosmscout/libosmscout/src/osmscout/routing/MultiDBRoutingService.cpp` and its header
  — pass the reason and the candidate query through the multi-database service.

**Native — JNI, submodule patch:** `libosmscout-client-java/java/com/framstag/libosmscout/client/RouteCallback.java`
(additive `onErrorDetail(int, String)` with a default that delegates to `onError`, so JavaScout is
unchanged) and `libosmscout-client-java/src/OSMScoutClient.cpp` (one reason-extraction helper used by both
route entries; `GetRouteCallbackMethods`).

**Java — local override in `:osmscout-client-java`:** `OSMScoutClient.java` (expose the reason on the
app-facing entry). Patched in one place, never both.

**Shared state — `:core`:** `NavigationState.kt` (typed error), `NavigationViewModel.kt`,
`src/main/res/values/strings.xml` + `values-de/strings.xml`.

**Phone — `:app`:** `ui/route/RoutePanelViewModel.kt` (typed `RouteError` instead of `String`),
`ui/route/RoutePanel.kt` (reason wording + action), `navigation/NavigationEngine.kt` (publish the reason;
remove the hardcoded English fallbacks at `:453` and `:547/:575`), `ui/map/MapCanvasScreen.kt` (snackbar
carrier; the new engine-error carrier), `src/main/res/values/strings.xml` + `values-de/strings.xml`.

**Car — `:auto`:** `ErrorOverlayScreen.kt`, `NavigationSession.kt`, `src/main/res/values/strings.xml` +
`values-de/strings.xml`.

**Tests:** `app/src/test/java/com/framstag/libosmscout/client/FakeOSMScoutClient.kt` (the reason on the
fake), `app/src/test/java/com/naviveylin/navigation/*` and `app/src/test/java/com/naviveylin/ui/route/*`,
`auto/src/test/java/com/naviveylin/auto/NavigationTripTest.kt`.

**Guidelines:** `guidelines/UI.md` §1 (a parity-table row for the routing-failure message and its action),
`guidelines/Logging.md` (that a failure reason is published as an identifier and worded by the surface),
`guidelines/Design.md` §5 (the bridge's error contract). `TODO.md` gains the three out-of-scope entries
above.

**Android components:** `RouteOverlayAnchor`/route panel state (`:app`), `ErrorOverlayScreen`
(`:auto`, Car App Library `PaneTemplate`), no manifest, permission or Gradle change.

**Scope:** both variants. The failure is reported on the phone (route panel and navigation snackbar) and
on the car (error notice); the phone additionally gains the engine-error carrier. The wording is shared
and localized on both.

**Additive, not breaking.** No JNI signature, Java field or spec requirement is removed: `onError(String)`
keeps its behaviour and its callers, `Success()` keeps its meaning, and JavaScout compiles unchanged.
Rollback: revert the submodule commit and the gitlink bump, the `:osmscout-client-java` override, and the
app/auto commits; no persisted state, schema or migration is involved, and nothing new is stored.
