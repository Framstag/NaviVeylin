# Design

## Context

`RoutingResult` carries no reason: `Success()` is `!route.IsEmpty()` (`AbstractRoutingService.h:52-101`)
and its own file says so — *"TODO: Adapt it to the same style as RoutePointsResult and Co."*
(`:47`), while every sibling result type (`RoutePointsResult`, `RouteDescriptionResult`,
`RouteWayResult`) already carries an explicit `success` field. `AbstractRoutingService::CalculateRoute`
therefore returns an indistinguishable empty result from six sites (`:1031` target nodes, `:1042` start
nodes, `:1149` `WalkPaths`, `:1164` `WalkToOtherDatabases`, `:1306` open list exhausted, `:1338`
`ResolveRNodesToRouteData`), plus one breaker-abort path that is already reported as a cancellation.

The bridge maps all of them to one string (`OSMScoutClient.cpp:6483`), which both surfaces render verbatim:
the phone route panel (`RoutePanel.kt:666`), the phone snackbar (`MapCanvasScreen.kt:247`), the car error
notice (`ErrorOverlayScreen.kt:50`). Each of the six sites already writes a distinct
`osmscout::log.Error()` that reaches Logcat under tag `NaviVeylin` (`guidelines/Logging.md` §1-2), so the
cause is diagnosable from the tree today — it just never reaches the user.

Constraints that shape the approach:

- **The JNI bridge is split and patched in one place, never both.** `RouteCallback.java` is a *submodule*
  file (not one of the six overridden by `:osmscout-client-java`), so an interface change is a submodule
  edit; the app-facing `OSMScoutClient.java` *is* an override.
- **Native routing behaviour is not reachable from JVM unit tests.** The JVM suites run against a stub
  `.so` (`app/src/test/jniLibs`, spec `ci-unit-test-jni`), and the app build sets
  `OSMSCOUT_BUILD_TESTS OFF` (`app/src/main/cpp/CMakeLists.txt:96`). A router fix is verified on device or
  not at all.
- **Both route entry points must stay in step.** `calculateRouteWithObjectsWithProfile`
  (`OSMScoutClient.cpp:6289`) is the one the app calls; `calculateRouteWithObjectsAsync` (`:6975`) is a
  second full copy of the pipeline.
- **`Success()` is consumed elsewhere.** It is the contract the existing specs depend on
  (`osmscout-jni` — *"A calculated route SHALL expose exactly one length"*), so it must not change meaning.

## Goals / Non-Goals

**Goals:**

- A failed calculation names its cause, and the cause is the router's own outcome, not a parsed message.
- The observed defect is removed, not described: a start or target beside a direction-restricted road is
  attached to the nearest road that can carry it, so that calculation succeeds.
- Every reason has user-actionable wording, supplied by the surface from a string resource, identical in
  label on phone and car, complete in English and German.
- The phone presents engine-raised errors; the car keeps presenting them too.
- The cause is identifiable from the diagnostics entry without a second device session.

**Non-Goals:**

- Consolidating the two native route entry points.
- Fixing the coordinate logging already present on the legacy entry
  (`OSMScoutClient.cpp:7145/-7151/-7160/-7166/-7198`) or the `HardcodedStringScanner` blind spot that lets
  an `errorMessage = "…"` literal through — both recorded in `TODO.md`.
- Changing `MultiDBRoutingService::Open`'s all-or-nothing policy; the failure is reported specifically, the
  policy is not changed.
- Making `avoidTolls` / `avoidUnpaved` functional (`OSMScoutClient.cpp:6265-6277`, both log-only).

## Decisions

### D1 — The reason comes from the router, not from the message text

**Chosen:** add `RoutingError` (`None`, `NoStartNode`, `NoTargetNode`, `NoRouteFound`, `DataFailure`,
`Aborted`) to `RoutingResult` and set it at each return site, leaving `Success()` untouched. Six causes, six
reports, and nothing derived from message text.

**The cost-limit sub-case was built, then removed on the device's evidence.** The design first separated a
cost-limit exhaustion from a disconnected graph, using a counter of the extensions `WalkPaths` discarded at
the limit (`RoutingResult::GetCostLimitIgnoredCount()`) as *proof* that the limit cut the search off. The
proof was real and the conclusion from it was wrong: a 416-point sweep of Dortmund's old town
(`nordrhein-westfalen-27-20260820-0826`, task 6.2) produced 24 failures that had all hit the cost limit, on
trips of at most 1.2 km air-line distance — where a route cannot be "longer than the profile allows", and
the real cause is that the two endpoints' parts of the network do not join. Discarding extensions at the
limit is therefore *not* a property of long routes, so the kind, the counter and the wording are gone and
that state reports `NoRouteFound`. This is why the change reports six causes and not seven.

**Alternatives eliminated:**

- *Map the English message text to a reason in Kotlin.* Rejected: it makes the wording a load-bearing API
  — any upstream message edit silently changes user-visible behaviour — and it puts routing knowledge in a
  UI layer. It also cannot work at all on the reason we care about, because that path writes *no*
  distinguishable message today beyond the generic one.
- *Classify from the numbers alone, no enum.* Rejected: all six failure sites leave
  `GetCurrentMaxDistance()` at `0 m` when the walk never started, so "start nodes" (`:1042`), "target
  nodes" (`:1031`), `WalkPaths` (`:1149`), `WalkToOtherDatabases` (`:1164`) and `ResolveRNodesToRouteData`
  (`:1338`) are indistinguishable from each other. The observed failure would still be generic.
- *Return the reason as an out-parameter on `CalculateRoute`.* Rejected: `CalculateRoute` is a public
  method with two overloads and several callers (`SimpleRoutingService::CalculateRouteViaCoords`,
  `MultiDBRoutingService::CalculateRoute`), and an out-parameter on a templated virtual path is a wider
  signature change than a field on the result it already returns.

### D2 — The reason crosses JNI additively

**Chosen:** add a default method to the submodule's `RouteCallback`:

```java
default void onErrorDetail(int kind, String detail) { onError(detail); }
```

`GetRouteCallbackMethods` (`OSMScoutClient.cpp:6019`) resolves it once, and the native call keeps invoking
`onError(String)` unchanged for a caller that does not override it. Reason extraction lives in **one**
helper shared by both route entries.

**Alternatives eliminated:**

- *Add the method as abstract.* Rejected: it breaks JavaScout and every existing implementor for no gain;
  the interface is a submodule file shared with the desktop demo.
- *Replace `onError(String)` with `onError(int, String)`.* Rejected: a breaking signature change to a
  public Java API, for a value that is purely additive.
- *Pass a `RouteFailure` object.* Rejected: a new Java class in the submodule and a new JNI construction
  path for one small payload; the enum ordinal plus the existing detail string carries everything the
  surfaces need.

### D3 — One attachability predicate, parameterized by the route end, used by the selection *and* the attachment

**Chosen:** the rule that makes an endpoint attachable to a way is extracted into one predicate on
`AbstractRoutingService` — it reproduces the direction-gated search `GetWayTargetNodes` /
`GetWayStartNodes` perform (`:182-239`, forward/backward walks gated by `CanUseForward` /
`CanUseBackward`) — and `SimpleRoutingService::GetClosestRoutableNode` and
`MultiDBRoutingService::GetClosestRoutableNode` accept a candidate way only when that predicate holds for
the endpoint coordinate. Selection and attachment then cannot disagree, and a way that cannot carry the
endpoint is skipped in favour of the next one within the same radius.

**The predicate needs the route end, and it is added, not substituted.** The two ends attach along the way
in opposite directions: the *start* uses forward `[nodeIndex, end]` and backward `[0, nodeIndex-1]`, the
*target* uses forward `[0, nodeIndex-1]` and backward `[nodeIndex, end]` (each trying the exact attach node
first). So on a one-way way whose route node sits *above* the attach index, the position attaches as a
**start** and fails as a **target** — which is the observed defect. A `RouteEndpoint { Start, Target,
Unspecified }` therefore rides beside `RoutingError`, `Unspecified` meaning "the end is not known" (a via
point, or a caller outside this library), and a **new overload**
`GetClosestRoutableNode(coord, profile, radius, RouteEndpoint)` carries it. The existing signatures are kept
unchanged in both behaviour and implementation-visible contract and delegate with `Unspecified`, so nothing
that builds or routes today changes. The bridge passes `Start`/`Target` from `ResolveRoutePosition`, which
already knows which end it is resolving.

**Alternatives eliminated:**

- *Widen the radius and retry in the bridge.* Rejected: it cannot change the outcome. The larger box
  contains the same way, and `GetClosestRoutableNode` keeps the nearest — so the retry returns the same
  unusable way (`SimpleRoutingService.cpp:628`, `MultiDBRoutingService.cpp:137-152`).
- *A role-agnostic predicate ("both ends could attach", or "the attach node is itself a route node").*
  Rejected: it fixes the target case but **regresses a working start**. A one-way way whose route node lies
  above the attach index resolves as a start today; the role-agnostic rule rejects that way, and when it is
  the only way inside the radius a route that works becomes `No routable node near start` — contradicting
  this change's own scenario *An attachable nearest way is unchanged*. It was written, and it is wrong.
- *A role-agnostic predicate with "at least one end could attach".* Rejected: it is implied by
  `HasNodeWithId` (the way already carries a route-node serial), so it filters nothing and fixes nothing.
- *Bridge-side retry with an exclusion set (ask for the next candidate after a failure).* Rejected as the
  primary: it needs a new native candidate-list query, it runs the whole calculation twice for every
  affected request, and it duplicates the direction rule in the bridge, where it would drift from the
  router's own. It stays the fallback design if the predicate proves too expensive to evaluate during
  selection.
- *Attach to the direction-allowed node of the same way regardless of distance.* Rejected: it silently
  moves the endpoint far along a carriageway, which is a worse user-visible lie than a specific message.

**Cost control:** the predicate runs a route-node lookup per way it evaluates, so it is evaluated only
where a candidate would *improve* the current best distance — the per-way closest segment is found first,
the predicate decides on that index, and only an accepted way updates the result. That bounds it to the
number of record improvements, which is a handful per endpoint.

**Alternatives eliminated:**

- *Widen the radius and retry in the bridge.* Rejected: it cannot change the outcome. The larger box
  contains the same way, and `GetClosestRoutableNode` keeps the nearest — so the retry returns the same
  unusable way (`SimpleRoutingService.cpp:628`, `MultiDBRoutingService.cpp:137-152`).
- *Bridge-side retry with an exclusion set (ask for the next candidate after a failure).* Rejected as the
  primary: it needs a new native candidate-list query, it runs the whole calculation twice for every
  affected request, and it duplicates the direction rule in the bridge, where it would drift from the
  router's own. It stays the fallback design if the predicate proves too expensive to evaluate during
  selection.
- *Attach to the direction-allowed node of the same way regardless of distance.* Rejected: it silently
  moves the endpoint far along a carriageway, which is a worse user-visible lie than a specific message.

### D4 — The app carries a typed error, not a sentence

**Chosen:** a `RouteError` value in `:core` (reason, affected endpoint, nearest-road distance when known,
search radius) published on `NavigationState` and on the route panel's state, replacing the bare
`String`. Each surface maps it to its own resource and its own carrier.

**Alternatives eliminated:**

- *Keep the `String` and map it per surface.* Rejected: it re-parses prose at the display layer, cannot
  localize (the message is already English by then), and cannot decide an action.
- *One boolean per failure kind* — the pattern `preciseLocationRequired` uses today
  (`RoutePanelViewModel.kt:117`). Rejected: it does not generalize past two kinds and every new kind needs
  a new state field, a new reset site and a new test.

### D5 — The phone's engine-error carrier is its own surface

**Chosen:** a snackbar fed from `NavigationState.errorMessage` gated on
`errorAppliesTo(SurfaceOrigin.PHONE)` (`NavigationState.kt:108`), added next to the existing map snackbar.

**Alternatives eliminated:**

- *Fold engine errors into the route panel.* Rejected: the panel is not always open — the failing paths
  are the ones with no panel at all (a shared link, a car deep link, a phone "Navigate to").
- *A dialog.* Rejected: these failures are non-blocking and the user may be driving; the
  precise-location dialog exists because that refusal blocks the requested action irrecoverably.

**No double display:** the two phone carriers are disjoint by construction. The panel path calls the client
directly and never publishes to `NavigationState`, while the engine path publishes only there; the existing
snackbar collector reads the panel's `routeErrorEvent`, the new one reads `NavigationState`. A failed
*automatic* reroute is engine-raised and is invisible on the phone today — it becomes visible through the
new carrier only.

### D6 — Wording is owned by the surface

**Chosen:** one resource per reason in `:app`, `:auto` and `:core` as applicable, English and German, with
values substituted (`%1$s` road name, `%1$d` distance). The car may use a shorter variant of the same
label; it MUST NOT reword. A reason the surface does not know falls back to the unspecified-failure
resource.

Rejected: a single message assembled in `:core` and shared — `i18n-l10n` states that a shared module
"SHALL NOT own the wording: it SHALL take the display text (or a string resource reference) from its
caller".

### D7 — Diagnostics extend the existing entry

**Chosen:** `endCalculation` (`NavigationEngine.kt:687-697`) keeps its shape and gains the reason plus the
classification numbers. No new entry type, no new sink.

Rejected: native-side diagnostics. The native layer has no diagnostics-file access by design (it stays
Android-free outside `Android/`), and the entry already exists on the Kotlin side with the retention policy
(`DiagnosticsLog.RETENTION_MS`).

## Threading and lifecycle

- The reason is produced on libosmscout's database thread inside `RunSynchronousJob` and reported through
  the existing callback on the routing worker, exactly as the current failure message is. No new thread, no
  new dispatcher, no native call added to the main thread — the car rule "nothing native on the host
  thread" is untouched (`guidelines/Design.md` §8).
- Both app callbacks keep marshalling to their own dispatcher: the panel into its `viewModelScope`, the
  engine into `scope.launch(Dispatchers.Main)` (`NavigationEngine.kt:541`), which is where
  `NavigationState` is published (`guidelines/Design.md` §4).
- The new phone carrier is a Compose snackbar driven by state; it holds no session and outlives nothing.
  The car notice keeps its existing lifecycle (`ErrorOverlayScreen` + `errorDismissalJob`,
  `guardedHostCall` for every host mutation) — the change alters the row's text, not the notice's
  lifecycle.
- The `RouteError` in the route panel state is cleared by the same sites that clear `error` today
  (`:466`, `:524`, `:557`), so no carrier can show a stale reason.

## Risks / Trade-offs

- **The selection change can move where a working route attaches.** Mitigated by the predicate being the
  attach rule itself, so a way that *can* carry the endpoint is still chosen and only an unusable nearest
  way is skipped; pinned by the scenario "An attachable nearest way is unchanged". Residual risk: routes
  that previously attached to a further node of the *same* way now attach to a nearer way. That is the
  intended behaviour, and the geometry change is the fix, not a regression.
- **Evaluating the predicate during selection costs work per candidate way.** It reproduces a search the
  attach step performs anyway, and it happens once per endpoint, not per route node. Watched with the
  existing `route-calculation-feedback` duration diagnostics — if the duration for an affected route moves
  outside its noise band, the fallback design in D3 is the escape hatch.
- **Upstream divergence.** The submodule patch is additive and matches the file's own TODO, so it is
  upstreamable; the gitlink bump and the submodule pin discipline are the usual cost
  (`guidelines/Design.md` §5).
- **Car row width.** A long sentence is truncated by the host (`car-list-row-limit`). The car uses a short
  label; the measurement below checks the whole row, not the string length.
- **i18n completeness.** Every new string needs its German counterpart; the parity check is the existing
  `i18n-l10n` completeness gate.
- **The scanner blind spot stays.** `HardcodedStringScanner` matches `Text(|text =|title =|…`, so an
  `errorMessage = "…"` literal is invisible to it (`NavigationEngine.kt:453` is the live example). This
  change removes the two routing literals but does not close the hole — recorded in `TODO.md`, not fixed
  here.

## Verification

- **JVM unit tests** (`:app`, `:auto`, `:core`) — reason-to-wording mapping per reason per surface, the
  unknown-reason fallback, the endpoint name in the message, the action offered per reason, the car's
  no-phone-screen rule, the phone engine-error carrier gated on `errorAppliesTo(PHONE)`, the diagnostics
  entry fields. Drive the reason through `FakeOSMScoutClient`'s new reason field.
- **Native behaviour — on device only.** The JVM suites run against a stub `.so`, so the attachment fix is
  measured on the AVD with the same map set the existing device tests use (spec `ci-unit-test-jni` makes
  the stub mandatory). Instrumented case: a destination whose nearest routable way is one-way with its
  route node behind the attach point, asserted through the calculated route and the destination's
  published position, plus `adb logcat -s NaviVeylin` showing no `No route node found for target way`.
  **This cannot be produced on a stationary emulator without that map fixture**: if the fixture cannot be
  reproduced, the task records the blocker explicitly rather than implying device proof
  (`guidelines/Build.md` §10).
- **What no JVM case covers, named so it is not read as covered** (spec `ci-unit-test-jni` — the stub is
  the reason): `osmscout-jni`'s *Destination beside a one-way road*, *Start beside a direction-restricted
  road*, *Nothing attachable within the search radius* and *An attachable nearest way is unchanged*; and
  `routing-failure-reporting`'s *The distance to the nearest usable road is carried when known* when the
  distance comes from a real routing graph. Their proof is the device run in group 6, and the mapping
  between a real `RoutingError` and a callback kind is proven there end to end. What **is** covered on the
  host is everything on this side of the JNI boundary: the kind → reason mapping, the wording per reason,
  the action per cause, the diagnostics line, and the callback interface's compatibility default.
- **The phone carrier's snackbar itself is device-gated.** `:app`'s screens need three Hilt view models and
  a renderer initialised, which the existing suites do not stand up (the note on
  `RoutePanelViewModelRerouteTest` says so). The unit cases prove the value the carrier gates on — a
  failure that applies to `SurfaceOrigin.PHONE`; task 6.4 shows the snackbar.
- **The revert-check for the attachment predicate is on device**: mutate the predicate to accept every way,
  the new device case must fail again (calculation fails / the reason is the target-node one), restore,
  then re-run green.
- **The revert-check for the app-side mapping is a forced green JVM run**: mutate the reason→resource
  mapping to one fixed resource, the per-reason case must fail, restore, re-run
  `-PforceTests --no-build-cache`.
- **No pixel measurement is claimed.** The change adds and rewords text in existing containers; the visual
  claim is that a message is readable and an action is reachable, which is asserted by UI-dump geometry in
  the device task rather than by a screenshot verdict.
