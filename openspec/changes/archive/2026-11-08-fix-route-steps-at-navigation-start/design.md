# Design — fix-route-steps-at-navigation-start

## Root cause

The shared state's step list has exactly two writers, and neither of them runs at a start:
`onRouteSteps` (`app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt:1109-1125`, base-commit line
numbers from here on) copies the native list into the state, and the start path
(`startInternal`, `:375-460`) publishes `steps = emptyList()` (`:421`) — the field's data-class default — beside
the values it does seed from the route it was handed (`remainingDistance = totalDistance` at `:426`, the geometry
at `:430-431`, the destination at `:433-434`). The native list cannot arrive at a start on its own: the bridge
emits it from `RouteInstructionsMessage` (`OSMScoutClient.cpp:3787-3790`), and libosmscout's
`RouteInstructionAgent::Process` returns no message for anything that is not a `PositionAgent::PositionMessage`
(`RouteInstructionAgent.h:106-109`). So a parked session's state carries no steps at all until the first fix, and
every list reader renders nothing: the car route description derives its rows from that list
(`NavigationTemplateMapper.kt:280-283`, `:309-327`) and logs `rows=0 steps=0`.

The route the session was started on does carry the records: `RouteEntry.steps` is a `RouteStep[]` of the same
model (`RouteEntry.java:22-31`), published by the calculation (spec `osmscout-jni` — "A calculated route
publishes structured route steps") and already read by the phone's route panel. The state field's default was
therefore standing in for a value the start path had in hand.

## The fix

One expression in the start publication:

```kotlin
// The route's steps from its start: the native guidance dispatch publishes the live list only with a
// position update, so without this seed a parked session's list is the empty default (spec:
// navigation-engine — One navigation state shared by all surfaces).
steps = routeEntry.steps?.toList() ?: emptyList(),
```

- It replaces `steps = emptyList()` in the same single `_state.update` the start already performs, so the seeded
  list travels in one emission with the route geometry, the total, the destination and the vehicle — no second
  publication, no intermediate state a surface could observe as empty.
- `RouteEntry.steps` is null only for a result that carries no description (`RouteEntry.java:29-31`), so a
  description-less route seeds the same empty list it publishes today.
- The live dispatch keeps ownership of the list afterwards: the first `onRouteSteps` overwrites the seed with the
  engine's remaining window (`NavigationEngine.kt:1109-1125`, base-commit lines: `steps = steps.toList()`), so the
  seed is a start value and never a second source of truth for a running session.
- `nextStep` is deliberately not seeded. The live step is the manoeuvre after the position, with the remaining
  distance and the next-next window, built by the native builder and copied by `guidanceStep`
  (`core/src/main/java/com/naviveylin/core/RouteStepGuidance.kt:45-57`); a seeded copy would be a second owner of
  that rule (and of `isDataOnly` — a route's first list entry can be a facts-only node, which no guidance surface
  may announce). Nothing waits on it: the surfaces' own fallback reads the list's entry at `currentStepIndex`
  when the live step is absent (`NavigationTemplateMapper.kt:213-215`, `:414`;
  `NavigationNotificationContent.kt:151`), which the seed now fills.

## Blast radius

- One production line family: `NavigationEngine.kt`'s start publication (`:421` and its comment). No signature,
  no new field, no new dependency, no native change, no manifest or resource change.
- Two cases in `app/src/test/java/com/naviveylin/navigation/NavigationEngineStepIndexTest.kt`: one new case for
  the parked start, and `startNavigation_clearsStaleStepsFromPreviousRoute` rewritten to assert that a restart
  publishes the *new* route's steps (its old `steps.isEmpty()` assertion only held because its route entry carried
  no steps, and it would have gone on passing vacuously after this fix).
- Readers of `core.NavigationState.steps` see a non-empty list from the start instead of an empty one; each of
  them already treats the list as the route-start set (its comment at `NavigationTemplateMapper.kt:210-212` says
  the list "is frozen at route start"), so no reader's logic changes.
- Every other session-start fixture in `app/src/test` hands `start`/`acquire` a route entry whose `steps` is
  absent (`tools/look.sh 'engine\.start\(|vm\.start\(|\.start\(routeEntry' --path app/src/test`: 14 matches in 10
  files, none of them sets `steps`), so they seed the empty list and stay green for the same reason as before.
- One guideline bullet (`guidelines/Design.md` §3) and one spec requirement (`navigation-engine`) state the rule
  the diff now applies.

## Alternatives the evidence eliminated

- **Publish the list natively at start** (a call to `DispatchRouteSteps` from the controller's start path, or an
  initial publication in `RouteInstructionAgent`). Eliminated twice: `RouteInstructionsMessage` carries a
  `Timestamp` that `Process` takes from the position message it never receives at a start
  (`RouteInstructionAgent.h:106-117`), so it means a patch to the vendored submodule that both the Java and the Qt
  clients build — and its verification is a device run, not a host test (gate 6, `needs-device`), while the record
  set the state needs is already in the app process.
- **Seed the state from a second native read at start** (re-asking the controller for the description). Eliminated
  by the same evidence plus cost: a second native call and a second marshalling of every step to obtain what
  `RouteEntry.steps` already holds.
- **Fix the car mapper instead** (derive the parked rows from something else). Eliminated by the seam: the car
  surface never holds the `RouteEntry` — the shared state is the only thing it reads, and the phone notification
  and the phone details overlay read the same empty field. A car-side fix would leave the same `steps=0` on two
  phone surfaces.
- **Seed `nextStep` as well.** Eliminated: the live step's value — the manoeuvre after the position, its
  remaining distance and its next-next window — is the native builder's (`RouteStepGuidance.kt:45-57`), and a
  parked session is already served by the surfaces' fallback to the list's entry at `currentStepIndex`
  (`NavigationTemplateMapper.kt:213-215`). Seeding it would duplicate that rule for no surface that still lacks a
  value, and would let a facts-only first entry reach a guidance row the `isDataOnly` rule exists to keep out.

## Guideline and spec ownership

- Owning guideline: `guidelines/Design.md` §3 "ViewModel & state", the bullet **"Progress, wall-clock and list
  state start at the invariant, not at the data-class default"** (`guidelines/Design.md:97-102` as this change
  writes it; it read "Progress and wall-clock state…" at `:97-100` before). It states the rule this
  diff applies — a start publishes the invariant, not the data class's default — and names
  `remainingDistance = totalDistance` as its example. The diff **owes** it the step list as a second example: it
  is the start field the bullet's rule was not applied to, which is exactly the defect. Nothing else in §3 states
  what a start owes; the neighbouring bullet "navigation state is process-owned by exactly one engine" is about
  ownership, not about which values a start publishes.
- Owning capability: `navigation-engine`, requirement "One navigation state shared by all surfaces"
  (`openspec/specs/navigation-engine/spec.md:34-40`), which states the state carries the session's step list for
  every surface. The delta adds the parked-start scenario and says in the requirement text that the list is part
  of the session from its start. `navigation-controller`'s "Navigation state exposed as StateFlow"
  (`openspec/specs/navigation-controller/spec.md:89-94`) states the same field for the phone surface; this change
  contradicts nothing there, so no delta is written for it.
- `auto/navigation-view`'s "Route description screen" is where the symptom is visible but states nothing about
  where the rows come from beyond "the remaining steps" (`openspec/specs/auto/navigation-view/spec.md:122-158`);
  the mapper is unchanged, so no delta is written there either.

## Rollback

Revert the one hunk in `NavigationEngine.kt` (the seeded expression back to `emptyList()`), the case rewrite and
the new case, the bullet sentence in `guidelines/Design.md` §3 and this change's spec delta. Nothing persisted, no
migration, no API: a parked session goes back to publishing an empty list.
