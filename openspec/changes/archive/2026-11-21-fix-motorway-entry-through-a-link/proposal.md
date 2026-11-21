# Proposal

## Why

On the route `51.5726589 7.4649125 → 51.4918664 7.5045441` the entry onto `B 236` at 2.5 km is published as a
bare turn, so the signposted destination, the entry wording and the entry arrow never reach any surface. The map
data holds the record twice: the ramp the route takes is OSM way 8609608 (`highway=trunk_link`, unnamed,
`destination=Dortmund;Schwerte`) and the trunk way is 800743505 (`highway=trunk`, `ref=B 236`, same
destination). Driven on the phone, the row reads "Turn slightly right" where the sign says
"Enter onto B 236 towards Dortmund".

Root cause, read from the tree (the description puts two manoeuvres on one node, and the model picks the weaker):

- `RoutePostprocessor::InstructionPostprocessor::HandleMotorwayLink` calls `HandleDirectionChange` and then adds
  the `MotorwayEnterDescription` on the same node — it places the turn of the link *and* the entry at the node
  where the route joins the motorway
  (`app/src/main/cpp/libosmscout/libosmscout/src/osmscout/routing/RoutePostprocessor.cpp`, the enter branch of
  `HandleMotorwayLink`).
- `RouteDescriptionPostprocessor::GenerateDescription` fires `OnTurn` before `OnMotorwayEnter` and puts the two
  in separate `if`/`else if` blocks, so **both** hooks report for that node
  (`app/src/main/cpp/libosmscout/libosmscout/src/osmscout/routing/RouteDescriptionPostprocessor.cpp:219` and
  `:227`).
- `RouteStepCollector::NoteKind` was first-hook-wins — `if (!pendingKindSet)` — so the step's kind was `turn`
  (`app/src/main/cpp/libosmscout/libosmscout-client/src/osmscoutclient/routing/RouteStepCollector.cpp:137` on the
  pin), and the fallback `DeriveKind` also tested `TurnDescription` before `MotorwayEnterDescription`.
- `ManoeuvreLabels.carriesDestination` admits only `MOTORWAY_ENTER` and `MOTORWAY_CHANGE`
  (`core/src/main/java/com/naviveylin/core/ManoeuvreLabels.kt:83`), and the entry wording, `ManeuverSymbols` and
  `NavigationArrowRenderer` all key on the kind, so a wrong kind drops four readings of one step.

Red-on-pin evidence, the case this change owes (`evidence/red-on-pin.log`, 2026-11-21T12:08:16Z): `RouteStepModelTest`
— "A motorway enter through a link is the entry, not the turn" — fails on submodule `c89db28f7` with
`REQUIRE( enter.kind==RouteStep::Kind::motorwayEnter )` → `3 == 6`, tally `152 assertions | 151 passed | 1 failed`,
suite `Ok: 0 / Fail: 1`. Green after the fix: `156 assertions in 18 test cases`, `Ok: 1 / Fail: 0`
(`evidence/route-step-model-green.log`).

The defect contradicts one requirement: `osmscout-jni`, "A calculated route publishes structured route steps" —
"A step SHALL state which manoeuvre it is, so a client can tell a roundabout leave from a motorway change without
reading text."

**One fix follows**: `RouteStepCollector::NoteKind` ranks the manoeuvres a node carries and keeps the strongest,
and `DeriveKind` tests the motorway descriptions before the turn so both rules agree (issue #993, option A). The
entry keeps its destination because the destination record sits on the enter node already — `CarryDestination`
(#877) and the ramp's own tag both place it there — so this change does not touch `DestinationPostprocessor`.

The route census that the issue lists as "measurement still owed" (the entry must move from `turn` to `motorway`
and add `motorway_dest=1`) is **owed and recorded as owed**: this session had no device attached and no map
database local to the repository, so the line is a task for a driven run, not a number this change measured
(`evidence/evidence.md` §4).

## What Changes

- `RouteStepCollector` gains one private rank function over `RouteStep::Kind`. `NoteKind` keeps the highest-ranked
  kind reported for a node instead of the first, and `DeriveKind`'s order matches the ranks, so a node that
  carries a motorway enter beside the turn of the link that leads onto it publishes `motorwayEnter`.
- Nothing above the bridge changes. `ManoeuvreLabels`, `ManeuverSymbols`, `NavigationArrowRenderer`, the phone
  overlay, the car `Step.road`, the notification and the census keys are untouched — they already word, draw and
  count a `motorwayEnter` step; the step now is one.
- No wording, arrow, template or resource is changed: the row's text comes from the kind it already had a rule
  for.

Additive and reversible: a route whose nodes carry one manoeuvre each publishes exactly the steps it publishes
today, because the rank order is the order the hooks fire in. The one pair the order changes is a motorway
manoeuvre beside a turn on the same node. Rollback is a revert of the submodule commit plus the gitlink bump; no
data, schema, persisted state or Kotlin file is involved.

surface: 1 requirement and 3 scenarios added, 0 requirement bodies rewritten — the `osmscout-jni` delta, counted
by `grep -c '^#### Scenario:' openspec/changes/fix-motorway-entry-through-a-link/specs/osmscout-jni/spec.md`.

## Capabilities

### New Capabilities

None. The behaviour belongs to the capability that owns what a published step is.

### Modified Capabilities

- `osmscout-jni`: a step whose node carries a motorway manoeuvre beside a turn publishes the motorway manoeuvre,
  with the records of that node — including the signposted destination of the entry.

## Impact

Native — **submodule patch** (minimal, upstreamable; `app/src/main/cpp/libosmscout`, no local override in
`:osmscout-client-java`, the bridge is untouched):

- `app/src/main/cpp/libosmscout/libosmscout-client/src/osmscoutclient/routing/RouteStepCollector.cpp` — the new
  rank function, `NoteKind`'s rule, `DeriveKind`'s order. Submodule commit `d1fefc13e`.
- `app/src/main/cpp/libosmscout/libosmscout-client/include/osmscoutclient/routing/RouteStepCollector.h` —
  `NoteKind`'s doc comment.
- `app/src/main/cpp/libosmscout/Tests/src/RouteStepModelTest.cpp` — the fixture
  `MotorwayEnterThroughLinkRoute` and the case that is red on the pin.
- `app/src/main/cpp/libosmscout` (gitlink) — the parent-side pin bump.

The app, `:core`, `:auto` and `:osmscout-client-java` are untouched; the Kotlin consumers that already key on the
kind are `core/src/main/java/com/naviveylin/core/ManoeuvreLabels.kt`,
`app/src/main/java/com/naviveylin/ui/navigation/NextTurnOverlay.kt`,
`auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt`,
`app/src/main/java/com/naviveylin/ui/navigation/RouteStepValues.kt` (the census keys),
`app/src/main/java/com/naviveylin/service/NavigationNotificationContent.kt` and
`core/src/main/java/com/naviveylin/core/ManeuverSymbols.kt`.

Guidelines: the native boundary and the one-place rule are `guidelines/DesignNative.md` §5b and
`guidelines/Design.md` §5; the rule this patch is graded by — a defect inside the library behind an unchanged
contract is fixed upstream, in a minimal patch that mirrors the surrounding code — is `guidelines/DesignNative.md`
§5a. The submodule's suite and its pin rules are `guidelines/BuildTests.md` §6g and `guidelines/BuildNative.md`
§12; the census recipe the owed measurement runs is `guidelines/BuildNative.md` §10k. No guideline sentence is
superseded.

Scope: the native step model, both variants equally — the phone overlay and the car panel read one step, so
nothing platform-specific is added. The car Surface, the renderer and the threading are untouched.