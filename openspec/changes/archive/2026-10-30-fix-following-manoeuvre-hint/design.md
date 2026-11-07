# Design

## Context

Live guidance publishes `RouteStep[]` (`onRouteSteps`) and one step per position update
(`onNextRouteStep`). The step list is collected by `RouteStepCollector::AfterNode`, which builds a step for
every route node that carries any description — and `WayNamePostprocessor` / `WayTypePostprocessor` give
every node a name and a type. Device census of one 17.3 km route:
`steps=501 rows=34 … info=466 … leg_m median=10` (`2026-10-28-route-panel-from-steps/evidence/device-census.log:1`).

Two surfaces read guidance from that model and both are wrong today:

```
engine.onNextRouteStep(step)
  -> core: guidanceStep(step, state.steps)   RouteStepGuidance.kt:29
       published is a facts-only node  -> substitute next real manoeuvre from the route list
       copyForGuidance(route-list entry) -> nextNext = null      :47 :69
  -> state.nextStep -> NextTurnOverlay  row1 = step,  row3 = step.nextNext

auto: NavigationTemplateMapper.routingInfoFromState      :207-210
       current = state.nextStep ?: steps[currentStepIndex]
       next    = state.steps[currentStepIndex + 1]   <- the next NODE
```

The records are immutable-by-convention on the engine's side but are plain Java objects, and the engine
publishes them on the main thread (`guidelines/Design.md` §4), so the derivation is a pure function over
records already confined to that thread — no dispatcher, no lifecycle, no new component.

## Goals / Non-Goals

**Goals**

- The phone's second row shows the following *manoeuvre* (icon, wording, ordinal, gap) before the roundabout
  is entered, and no row at all when no manoeuvre follows within 200 m.
- The car's host "next step" row shows the following *manoeuvre*, carrying a roundabout exit's ordinal.
- One helper, in `:core`, so the two variants cannot drift (`guidelines/UI.md` §1 parity).

**Non-Goals**

- The native record's `nextNext` semantics (next node, 400 m collect window) — filed as a tracker finding;
  `osmscout-jni`'s "following manoeuvre" wording is only fully true after that fix.
- Making the engine publish a manoeuvre-only step ahead, which would need a look-ahead bounded by distance
  rather than the current 400 m window (a long motorway leg has no manoeuvre inside it, and the builder's
  fallback would announce the *arrival* as the manoeuvre ahead).
- Any change in the libosmscout submodule, any JNI signature, any Gradle or manifest change.
- The notification and the car hint text (`NavigationNotificationContent`), whose `nextStep` source is the
  announced step and therefore improves without change.

## Decisions

### D1 — The hint is derived in `:core`, at the announcement, not read from the record

`guidanceStep(published, route)` already decides *which* manoeuvre a surface announces; the following
manoeuvre is the same walk, one step further. The derivation is `nextManoeuvreAfter(route, announced)`:

```kotlin
/** Window inside which the following manoeuvre is announced (spec: next-turn-overlay). */
const val HINT_WINDOW_METERS = 200.0

internal fun nextManoeuvreAfter(route: List<RouteStep>, step: RouteStep): RouteStep? =
    route.firstOrNull {
        !isDataOnly(it) &&
            it.distanceFromStart > step.distanceFromStart &&
            it.distanceFromStart - step.distanceFromStart <= HINT_WINDOW_METERS
    }
```

`>` (not `>=`) skips a manoeuvre that shares the announced one's node — a turn and a motorway entry can be
described at one node (`StepIndexSelection.kt:12-17` records that ambiguity), and two rows at one place are
not a hint.

Both helpers are `public` in `:core`, not `internal`: `:auto` is a separate module and calls the unbounded
one for its own row (D3). `isDataOnly` and `guidanceStep` are already public for the same reason.

Alternatives the evidence eliminated:

- **Read `published.nextNext` (today's path)** — impossible: `RouteStepBuilder.cpp:167-205` sets it to
  `collected[index+1]`, a plain node (`step.kind == informative`), so it carries no roundabout exit and its
  gap is a node gap (median 10 m). Also unreachable in practice, because the published step is itself a
  facts-only node and `guidanceStep` replaces it.
- **Carry `published.nextNext` into the substituted copy** — worse: it would show "then in 10 m
  Hauptstraße" as a turn row, i.e. a wrong instruction, where today there is none.
- **Fix only the native builder** — leaves the phone broken, because the announced record comes from the
  route list and a route-list entry has no progress fields at all; the copy would still carry no hint.

### D2 — The announced record is always a copy, and the hint is always overwritten

`guidanceStep` returns `copyForGuidance(...)` on **every** exit, and then assigns `nextNext = derived` (or
`null`). Two consequences, both asserted:

- a surface can never mutate the engine's published state (today the non-facts-only branch returns the same
  object, and the overlay holds it for the whole approach);
- a node hint can never reach a display, whatever the native side publishes — including on the fallback exit,
  where the route list carries no manoeuvre at all (a list cleared by a start, `NavigationEngine` clearing
  `steps` before the first publication): that exit returns a copy whose hint is `null`, not the record's own
  `nextNext`. The reviewer caught the first version of this, which returned `published` there.

The copy is shallow (sub-records and arrays are shared by reference) and that is safe because no production
writer mutates a step or its sub-records: the only assignments are the two in `RouteStepGuidance` itself.

No field is added to `copyForGuidance`: the hint row reads exactly what the first row reads (kind, `turn` or
`roundaboutEnter`/`roundaboutLeave`/`motorway*`, `name`, `junction`, `destination`, `distanceFromStart`), and
all of them are already in its list. The leave's ordinal therefore reaches the hint row through
`roundaboutLeave`, which the copy carries today.

### D3 — The car derives its next row from the same helper, without the 200 m window

`RoutingInfo`'s next-step row is a dedicated host panel row that exists regardless of distance (spec
`auto/navigation-view` — "Next-next turn shown when present": whenever a turn follows the current one), so
the car calls the unbounded `nextManoeuvreFrom(state.steps, current)` while the phone calls
`nextManoeuvreAfter(...)`, which is the same walk bounded by the window. The alternative — teaching the car
the phone's 200 m window — would hide the next turn on the car exactly where the driver reads it (a 300 m
gap is normal on a motorway), and contradicts that spec's own WHEN clause.

Both helpers select by `distanceFromStart`, which is non-decreasing along a route by construction
(`RouteStepCollector` collects in route order), so the walk is the list order without depending on
`currentStepIndex` — which `nextStepIndex` re-binds from the live record. Four test fixtures carried
*decreasing* distances (a list ordered `1000, 800, 200`, and two roundabout leaves at the same 350 m); they
were normalised to route order because a real list cannot be otherwise, and none of their assertions changed.

### D4 — The device evidence line states where the hint comes from

`RoundaboutInstructionDiagnostics.roundaboutStepLine` logs `hintExit` from the step it is given, and
`onNextRouteStep` passes the *announced* step. After this change that value is app-derived, so the line's
KDoc — "the fields are filled in native code … this is what a device run reads to see that the route really
produced an ordinal" — would be false for `hintExit`. The line keeps its shape and gains no field (a new
field would cost a parse change in every recipe); the KDoc names `hintExit` as the app-derived following
manoeuvre and keeps `kind`/`exit`/`cw` as the native values. The device check below reads both.

## Risks / Trade-offs

- **The announced record is a copy everywhere now** — `ManoeuvreAnnouncementTest` asserts `assertSame` for
  the manoeuvre case; that assertion becomes `assertNotSame` plus a mutation check (mutating the announced
  record leaves the route-list entry untouched). Risk: a consumer that relied on object identity with
  `state.steps` entries — there is none (`NavigationNotificationContent` and the mapper read fields).
- **200 m is now a `:core` constant next to a native constant of the same value** (`RouteStepBuilder.h:
  NextNextWindowMeters`). Accepted for now: the native value is the record's, the core value is the
  overlay's contract, and the follow-up finding would make the native side the only producer. Named in the
  finding so a reviewer sees the second source of truth is deliberate and temporary.
- **A roundabout whose ring is longer than 200 m loses the pre-announcement.** Same bound as the shipped
  spec and as the pre-step-model behaviour (`CloseHintDistance = 200.0`), so this is parity, not a new
  bound; a larger ring is a spec decision, not this fix.
- **The car's next row now skips facts-only nodes** — a user who had come to read the panel's "next" as
  "the next street" sees the next *turn*. That is what `auto/navigation-view` specifies, and what the phone
  has always shown.

## Verification

- Red on HEAD first: `core: ManoeuvreAnnouncementTest` — "the roundabout exit is the hint before the ring is
  entered" fails on HEAD (`nextNext == null`), and the four derivation cases fail or are absent.
  `auto: NavigationTemplateMapperTest` — "the next step is the following manoeuvre, not the next route node"
  fails on HEAD (the row is the node); "the exit ordinal of the following manoeuvre is on the next step"
  fails on HEAD (ordinal 0).
- Green: `:core:test --tests "com.naviveylin.core.ManoeuvreAnnouncement*"`, `:auto:testDebugUnitTest
  --tests "*NavigationTemplateMapper*"`, `:app:testDebugUnitTest --tests "*NextTurnOverlay*"`, then the
  full both-flavor gate once (`guidelines/Build.md` §4b).
- On device, in the form the emulator can produce (no GPU drive needed): `app: NextNextHintDeviceTest`
  routes the AVD's Andorra corridor pairs, feeds the route's own vertices into the native controller and pushes
  every published (step list, step ahead) pair through `core/guidanceStep` — on `emulator-5556` this measured
  119 approaches, each announcing the leave with an ordinal and a 30-37 m gap, while the published record's own
  hint read `hintExit=0` (the #717 evidence). `app: NextTurnHintRowDeviceTest` renders that announced record
  through the real overlay and measures the row: a 36 dp arrow node, ink on the rasterised glyph, wording
  "Ausfahrt 2". A GPS-driven drive with the UI live is **not** claimed — it adds nothing the engine-level case
  does not already own, and a stationary emulator cannot produce it.
- Car: `NavigationTemplateMapperTest` is the host substitute (the host template is not observable from a
  test here; `guidelines/Build.md` §10a-§10b is the AAOS device recipe if a host run is wanted).
