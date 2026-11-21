# Design

## Context

See `proposal.md` — Why, for the mechanism and its file:line evidence. The constraint that decides every option
here: the route description is finished before anything reads it, and **three** consumers read its hooks — the
Android bridge (`OSMScoutClient.cpp`, the route-description block), the Qt client
(`RouteDescriptionBuilder.cpp`) and the demos (`Demos/src/Routing.cpp`). The step model is derived from that
description afterwards by `RouteStepCollector`, which sees one node at a time and nothing above it.

The two facts this design reconciles:

```
InstructionPostprocessor::HandleMotorwayLink   the turn and the enter land on the same node
RouteDescriptionPostprocessor::GenerateDescription
  OnTurn                 (:219)      fired before …
  OnMotorwayEnter        (:227)      … and separately, so both report for that node
RouteStepCollector::NoteKind on the pin: `if (!pendingKindSet)` — the first hook wins
RouteStepCollector::DeriveKind on the pin: TurnDescription tested before MotorwayEnterDescription
```

## Goals / Non-Goals

- Goal: a node that carries a motorway enter beside the turn of the link that leads onto it publishes a
  `motorwayEnter` step, with the destination the node carries.
- Goal: the fix lives in the step model — the one place that owns "which manoeuvre is this", upstreamable, so the
  Qt demo and the bridge read the same corrected kind.
- Non-goal: any wording, arrow, template or census-key change — every consumer already words, draws and counts a
  `motorwayEnter` step (`core/src/main/java/com/naviveylin/core/ManoeuvreLabels.kt`,
  `core/src/main/java/com/naviveylin/core/ManeuverSymbols.kt`,
  `app/src/main/java/com/naviveylin/ui/navigation/NavigationArrowRenderer.kt`,
  `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt`,
  `app/src/main/java/com/naviveylin/ui/route/RouteStepValues.kt`). The Kotlin readers were checked one by one in
  `evidence/review-round-1.md` check 4, along with the case's look-ahead exclusion
  (`RouteStepBuilder.cpp:99-103`): none needs a new kind.
- Non-goal: moving the enter off the ramp node (the issue's option B), and the `NAME_CHANGE`-node record the issue
  records as a second, separate loss. On this route the ramp carries the destination tag itself, so the record the
  wording needs is on the enter node already; a ramp without a tag is a different finding and stays out.

## Decisions

### D1 — Rank the manoeuvres in `RouteStepCollector`, in both rules it has

Chosen: one private `Precedence(RouteStep::Kind)`; `NoteKind` keeps a kind when
`Precedence(kind) > Precedence(pendingKind)` instead of only when none is set, and `DeriveKind` tests the
motorway descriptions before the turn. Both rules then answer the same way for the same node.

Alternatives eliminated, each by the root-cause evidence rather than by taste:

- **Move the enter to the first non-ramp node (`InstructionPostprocessor`).** The issue names it as option B and
  declines it; the design agrees, for a reason the code shows: it changes the *description* that the Qt client and
  the demos print — a new nameless turn row on every entry through a link — to serve one consumer's wording, and
  it re-derives "where the entry really is" from node adjacency, which the description does not state.
- **Fire `OnMotorwayEnter` before `OnTurn` in `RouteDescriptionPostprocessor::GenerateDescription`.** Refused: the
  hook order is the contract three consumers read, so a reorder re-renders both lines of every node that carries
  both records, in the Qt client and in the demos, not just the kind the model derives. The collector would also
  still see two hooks for one node and need an arbiter — the rank would have to exist anyway.
- **Admit `turn` in `ManoeuvreLabels.carriesDestination` and leave the kind.** Impossible as this defect's fix: the
  published step's contract is that it names its manoeuvre
  (`openspec/specs/osmscout-jni/spec.md`, "A calculated route publishes structured route steps"), the kind also
  drives the arrow, the car template and the notification, and the census counts steps by kind — so a lost entry
  would stay counted as a `turn`, which is exactly the blindness the issue records.
- **Fix `NoteKind` only and leave `DeriveKind`'s order.** Refused: two rules would disagree about one node, and
  which one answers depends on whether the hooks noted a manoeuvre for it — the same node would flip kind.

### D2 — The ranks are the order the hooks fire in, with one swap

Chosen: `start`/`target` 6, `informative` 5, `roundaboutEnter`/`roundaboutLeave` 4,
`motorwayEnter`/`motorwayChange`/`motorwayLeave` 3, `turn` 2, `nameChange` 1, everything else 0 — the order of
`GenerateDescription`'s hook blocks, except that the motorway group outranks the turn.

Alternatives eliminated:

- **"Motorway outranks turn" as a special case in `NoteKind`.** The same rule with less to check: with a table,
  every other pair keeps today's outcome by construction, and a later kind is added by inserting a rank rather
  than by adding another condition. The table is also what `DeriveKind` is ordered against.
- **Highest kind wins by enum order.** Arbitrary — `RouteStep::Kind`'s declaration order is a data layout, not a
  statement about which manoeuvre a node is.

The cost the issue states for option A is kept: the ramp's "slightly right" no longer *is* the row. It survives on
the step (`RouteStep.turn`, `RouteStep.direction` keep the node's records — `BuildNodeStep` fills every field
regardless of kind), and no surface words `turn` alone today, so nothing regresses; a surface that wants
"Enter onto B 236, keep slightly right" can read it without a model change.

### D3 — Nothing above the bridge changes

Chosen: ship the model fix alone. The look-ahead selection excludes only `informative` and `unknown`
(`RouteStepBuilder.cpp:101`), so a `turn` that becomes a `motorwayEnter` stays a manoeuvre for "the step ahead";
the wording, arrow, car panel and census keys are all keyed on kinds that already exist.

Alternatives eliminated:

- **A Kotlin fallback in `ManoeuvreLabels`.** Would word the entry while the car template, the arrow and the census
  kept reading the wrong kind — three of the four readings the issue lists would stay lost.
- **Adding a new kind for "entry through a link".** No consumer needs a fifth motorway kind, and the bridge's DTO,
  the JNI mapping and both surfaces would grow for a distinction nothing words differently.

## Risks

- **A node whose turn record carried the only direction information.** After the change the kind is the entry and
  the arrow is the entry's. Bounded by the fact that `InstructionPostprocessor` places the two records on one node
  only where the entry happens, and the turn record stays on the step. No case in the tree relied on the kind of a
  turn-beside-enter node before this change — that is why the suite stayed green (the issue says so, and
  `Tests/src/RouteStepModelTest.cpp` had no such node).
- **The census cannot show the fix.** `RouteStepValues.kt` counts `MOTORWAY_*` only, which is the blindness the
  issue records; the owed line is the before/after of the same route, not a new key. No key is added here.

- **The case pins retention, not placement.** The fixture carries the node's `DestinationDescription` itself, so
  what it guards is that a `motorwayEnter` step keeps the record, not that
  `InstructionPostprocessor::CarryDestination`'s enter branch places it there. That branch has no case of its own
  (the postprocessor suite drives a link as a leave only) — a pre-existing gap, named as a follow-up in
  `evidence/evidence.md` §7 and no part of this change.

## Threading and lifecycle

No new component, thread, dispatcher or lifecycle. `RouteStepCollector::Collect` runs where the description is
read today — on the route worker's path, off the main thread, once per description — and the rank function is a
pure switch over an enum. The car host path and the renderer are untouched (`guidelines/Design.md` §4, §8).

## Verification

- The case owed by the issue, red on submodule `c89db28f7` and green after: `RouteStepModelTest` — "A motorway
  enter through a link is the entry, not the turn" (`evidence/red-on-pin.log`, `evidence/route-step-model-green.log`).
- The five suites that own the collector's neighbours (`Check route postprocessor`, `Check route step time`,
  `Check route step model`, `Check route failure kind`, `Check route start`): 5/5 Ok, 0 Fail, 357 assertions
  (`evidence/focused-cases.log`).
- The device ABI: `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` with the collector's
  arm64-v8a object removed first — `BUILD SUCCESSFUL`, `:app:buildCMakeDebug[arm64-v8a]` executed rather than up to
  date (`evidence/assemble-mobile-debug.log`; the log carries no compile line of its own, so the claim is that the
  task ran — `evidence/evidence.md` §6).
- Revert-check of the introduced rank: `NoteKind` mutated back to first-hook-wins, the case fails (`3 == 6`),
  restored, green (`evidence/revert-check.log`).
- **Owed**: the route census line for `51.5726589 7.4649125 → 51.4918664 7.5045441` before and after — the entry
  must move from `turn` to `motorway` and add `motorway_dest=1` (`guidelines/BuildNative.md` §10k). No device was
  attached and no map database is local to this repository, so this is recorded as owed rather than claimed
  (`evidence/evidence.md` §4).