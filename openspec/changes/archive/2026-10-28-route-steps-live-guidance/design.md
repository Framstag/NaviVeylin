# Design — route steps for live guidance

## Context

`JavaNavigationController`'s engine list ends with
`std::make_shared<osmscout::RouteInstructionAgent<JavaRouteInstruction, JavaRouteInstructionBuilder>>()`
(`OSMScoutClient.cpp:3973`). The agent is a template over the instruction type and its builder, and
`osmscout::client::RouteStepBuilder` implements the builder contract with `RouteStep`
(`libosmscout-client/include/osmscoutclient/routing/RouteStepBuilder.h`): default-constructible, copyable, and
its step reports `GetDistance()`. `RouteStep` already carries `remainingDistance`, `remainingTime` and
`nextNext`, and `CreateJavaRouteStep` already marshals them (`OSMScoutClient.cpp:1371-1394`).

The instruction type exists to carry prose. Its `TurnType` cannot express a motorway change or leave, so the
bridge encodes them in the description string, and the app decodes them back with `startsWith("Keep ")` and
`== "Leave motorway"` (`core/TurnInstructionLocalizer.kt:37-38, :100`).

## D1 — How the engine produces steps

| Option | Cost | Risk |
|---|---|---|
| **A. Change the agent's template arguments to `RouteStep`/`RouteStepBuilder`** (chosen) | two template arguments, deleted builder code | the smallest possible diff; both types satisfy the contract today |
| B. Add a second agent that also consumes positions | two description walks per position | doubles the per-position native work and keeps the prose path alive |
| C. Derive steps from `RouteInstruction` in the bridge | cheap to write | lossy by construction: the instruction has already collapsed motorway change/leave and the exit ordinal is the only surviving structure — the defect would move, not disappear |

Chosen A. It is what the generic agent was made for, and it is the only option that also lets the prose code be
deleted rather than maintained.

## D2 — Listener API shape

| Option | Cost | Risk |
|---|---|---|
| **A. Replace `onRouteInstructions`/`onNextRouteInstruction` with `onRouteSteps`/`onNextRouteStep`** (chosen) | one Java interface change, one dispatch change | breaks JavaScout (accepted, see D3) |
| B. Add the new methods beside the old | both paths marshalled per position | two sources of guidance truth in one library |
| C. Keep the method names, change the parameter type | no Java source change at call sites | fails at **runtime**, not compile time: the bridge caches method ids by descriptor, so a stale listener implementation silently stops receiving guidance |

Chosen A: a compile-time break is the only kind a sibling consumer can act on.

## D3 — What happens to JavaScout

Owner directive: out of scope, adapted separately, and `RouteInstruction` is then removed from the API.
Therefore no compatibility shim is written. The alternatives considered and rejected: a struct→struct shim that
regenerates the English text (it re-creates the very text this change removes, in the one place the next change
wants to delete), and a flag that selects the instruction agent per session (two permanent code paths and a
per-session behaviour difference nobody can test in one run).

The pin bump's commit message names the breakage, so the sibling consumer finds it from `git log` rather than
from a silent absence of callbacks.

## D4 — The "next next" hint

The published step ahead carries the following manoeuvre as a **nested `RouteStep`** instead of five scalars
(`nextNextTurnType`, `nextNextDescription`, `nextNextShortDescription`, `nextNextDistanceTo`,
`nextNextRoundaboutExitNumber`). Alternatives: keeping the scalars (three of the five are text, so the parsing
survives for the hint alone), or flattening the hint to `kind` + ordinal (loses the following street name, which
the hint row shows). The nested step is what `RouteStepBuilder` produces and what the projection already
marshals (`CreateJavaRouteStep` calls itself for `nextNext`), so nothing new is needed.

## D5 — Matching the live step into the route's list

`StepIndexSelection.nextStepIndex` currently compares `description` strings. With steps, the match is
`distanceFromStart` (within a tolerance for the snap) **and** `kind`, because one route node can produce two
instructions — a turn and a motorway entry — so distance alone is ambiguous.

Alternatives rejected: distance only (ambiguous on the dual-node case), text (the thing being removed), and
`pathObject` + `currentNodeIndex` identity (better in principle, but the published list and the live walk are
collected from different node ranges, so index equality is not guaranteed yet — it becomes the natural follow-up
once the native side publishes ranges, and is named as an available improvement, not a requirement here).

## D6 — Pin bump shape

One fork commit on `app/src/main/cpp/libosmscout`, containing the agent swap, the two listener methods and the
dispatch, followed by a pin bump. Alternatives: a local override (impossible — the code is C++) and several small
commits (each would be a pin bump, and an intermediate pin would have an engine that publishes steps nobody
listens to). The submodule tree must be clean before and after (`guidelines/Design.md` §5), and the bridge is
compiled for all three ABIs, so the change is verified per ABI.

## D7 — One label builder for every surface

The row builder introduced by `route-panel-from-steps` moves into `:core` as the single text source for the route
list, the overlay, the notification, the car instruction panel and the car description screen.
`TurnInstructionLocalizer` (and its `StringResolver`-based API) is deleted in the same change.
Alternatives: per-surface text (five copies of the wording, the drift the `Shared resources` requirement
forbids) and native text (the thing being removed).

## Performance — the one number to watch

Per position update the bridge now marshals a `RouteStep` where it marshalled a `RouteInstruction`: more fields,
nested records, boxed doubles and a nested `nextNext` step. That is a real risk on the position path (1 Hz or
faster). Plan: measure first, then decide.

- **Measure**: a coordinate-free timing line around the dispatch (total milliseconds per publication, and the
  number of steps in the list), recorded on device for a 19-manoeuvre route.
- **If it shows** (say, a dispatch above a few milliseconds), the mitigation is to cache one Java DTO per
  `currentNodeIndex` and refresh only the progress fields (`remainingDistance`, `remainingTime`, `nextNext`) on
  later publications — the identity fields of a route node never change between two positions of the same route.
  **If it does not show**, no cache is added: the simpler code is the correct one.

Alternative rejected up front: pre-marshalling the whole route once and publishing indices (a large change to the
listener contract; only worth it if the measurement says so).

## Threading and lifecycle

Unchanged. The engine runs on its own background thread; the dispatch still happens on that thread and still
calls into Java through the existing guarded seam (`guidelines/Design.md` §8): every host-facing send is guarded,
a failure is logged and ends the emission, and nothing escapes to the host path. The app receives the callbacks
on the native thread and updates a `StateFlow` exactly as today (`NavigationEngine`), so no new dispatcher,
thread or lifecycle owner appears. The recursive marshalling of `nextNext` is depth-limited to one level by the
builder's contract.

## Risks

| Risk | Mitigation |
|---|---|
| JavaScout receives nothing after the pin bump | named in the pin commit and in the proposal; owner accepted |
| Per-position marshalling cost on the position path | measured before any optimisation; cache only if the measurement demands it |
| A surface keeps reading a field the step does not carry (e.g. `timeTo` as an absolute) | each surface's cases re-pointed explicitly; `NavigationEngine` compiles only when every reader is converted |
| A stale listener implementation silently stops receiving guidance (D2 option C's trap) | the method names change, so the break is at compile time |
| Fault escaping the host path while marshalling nested steps | the existing guard wraps the whole emission; the car device case `car-host-fault-isolation` still runs |
| The highlight binds to the wrong manoeuvre on a dual node | match by distance **and** kind, with a case for the turn-then-motorway-entry node |

## Verification

- **Unit**: `NavigationEngine` step state and progress (`NavigationEngineTest`, `NavigationEngineStepIndexTest`,
  `NavigationEngineRerouteTest`), `ManoeuvreLabelTest` (all kinds, German), `StepIndexSelection` (distance + kind,
  dual node), `RoundaboutInstructionDiagnostics` on step fields, `NextTurnOverlayTest` (icon from kind, nested
  hint), `NavigationNotificationContentFormatterTest`, `NavigationTemplateMapperTest` (car cue and text).
- **Revert-check**: (1) matching rule — mutate it to distance only, the dual-node case must fail; (2) the nested
  hint — drop the nested step, the "next next shown" case must fail; (3) the non-null guarantee — deliver a null
  element, the listener case must fail.
- **Device**: `RouteStepLiveGuidanceDeviceTest` on the phone emulator (step ahead, remaining distance, nested
  hint, list on route change), then the AAOS AVD for the car panel (`guidelines/Build.md` §10a/§10b).
- **Pixels**: one overlay screenshot with `pixel-check` for the icon claim (kind-based, motorway change not drawn
  as a turn) — producible on a stationary emulator with a simulated route.
- **Cost**: the dispatch timing line recorded before and after, with its numbers in the change evidence.
