# Proposal

## Why

On a motorway change that runs through a `highway_motorway_link`, the published step carries the manoeuvre but
no signposted destination, so the wording rule shipped in `motorway-destination-hint` (#806) never fires on the
one manoeuvre where the sign decides the branch: the phone's second line and the car panel's target street show
the bare reference ("Ruhrschnellweg (A 40)") where the sign says "A 40 · Essen". Driven 2026-11-13 in Android
Auto on a Dortmund → Essen route: no destination at any motorway transition, while the map data carries it —
`libosmscout Demos/Routing` over the same coordinates printed
`Change motorway from 'A 45' to 'Ruhrschnellweg (A 40)' destination 'Essen;Bochum;Dortmund-Kley;Dortmund'`.

Mechanism, read from the tree (the description places the record and the manoeuvre on different nodes):

- `RoutePostprocessor::DestinationPostprocessor::Process` attaches the `DestinationDescription` to the last
  node with intersecting objects — `app/src/main/cpp/libosmscout/libosmscout/src/osmscout/routing/RoutePostprocessor.cpp:548`.
- `InstructionPostprocessor::HandleMotorwayLink` attaches the `MotorwayChangeDescription` to the **motorway
  link node**, not to the node the record was placed on —
  `app/src/main/cpp/libosmscout/libosmscout/src/osmscout/routing/RoutePostprocessor.cpp:800`.
- `RouteStepCollector::BuildNodeStep` reads the destination from that node only, so a change step on the link
  node publishes `destination = null` —
  `app/src/main/cpp/libosmscout/libosmscout-client/src/osmscoutclient/routing/RouteStepCollector.cpp:320`.
- `ManoeuvreLabels.carriesDestination` then words the bare reference, because a step with no record is one the
  wording rule leaves alone (`core/src/main/java/com/naviveylin/core/ManoeuvreLabels.kt:83`).

One premise is not yet measured: whether the two nodes actually differ on a real route. `motorway-destination-hint`
design Risk 3 named exactly this displacement ("the record can sit on the junction node before the manoeuvre"),
and its guard — task 1.2, read `motorway_dest`/`dest_empty` from the census on a motorway route — was archived
**unchecked**. This change's task 1 reads that line before any patch is written, and states what a zero count
means (the loss is then the surface, not the model) rather than guessing.

Not loop-eligible, on two counts, and this is stated rather than worked around: the fix lands in the
**submodule** (`RoutePostprocessor.cpp`, a person's patch plus the gitlink bump — the loop may not commit there),
and the root cause is a mechanism read from the tree without a red case that reproduces it, since a case that
routes over a real map database is not a unit test this project has.

## What Changes

- `RoutePostprocessor::InstructionPostprocessor` gains one rule at the site where a motorway manoeuvre is
  placed: the node that receives an **enter** or **change** description also receives the destination
  description of the node the manoeuvre was decided at, when it carries none of its own. A node that already
  carries a record keeps its own, byte for byte.
- Nothing above the bridge changes. `RouteStep.destination`, `Manoeuvre.destination`,
  `ManoeuvreLabels.leadsOnto`, the phone overlay's second line, the car `Step.road` and the notification hint
  are untouched — they already word whatever the step carries.
- The route census keeps its `motorway_dest` / `dest_empty` keys unchanged; they become the before/after
  measurement of this change rather than a one-off premise check.

Additive and reversible: a description that carries no neighbouring record produces the same steps it produces
today. Rollback is a revert of one helper in the submodule plus the gitlink bump; no data, schema, persisted
state, Kotlin file or resource is involved.

surface: 1 requirement and 3 scenarios added, 0 requirement bodies rewritten — the `osmscout-jni` delta, counted
by `grep -c '^#### Scenario:' openspec/changes/motorway-destination-reaches-the-manoeuvre/specs/osmscout-jni/spec.md`.

## Capabilities

### New Capabilities

None. The behaviour belongs to the capability that owns what a published step carries.

### Modified Capabilities

- `osmscout-jni`: a published step of a motorway enter or change carries the signposted destination of the way
  it leads onto, wherever in the description that record was placed.

## Impact

Native (submodule patch — `app/src/main/cpp/libosmscout`, upstreamable, no bridge override):

- `app/src/main/cpp/libosmscout/libosmscout/src/osmscout/routing/RoutePostprocessor.cpp` — the instruction
  postprocessor's motorway-link and direct-change placement, plus one private helper.
- `app/src/main/cpp/libosmscout/libosmscout/include/osmscout/routing/RoutePostprocessor.h` — the helper's
  declaration.
- `app/src/main/cpp/libosmscout/Tests/src/RouteStepModelTest.cpp` — the case that is red on the submodule pin
  and green after.

The app and `:core` are untouched. The consumers that word the value are `core/ManoeuvreLabels.kt`,
`app/.../NextTurnOverlay.kt`, `auto/.../NavigationTemplateMapper.kt` and `app/.../NavigationNotificationContent.kt`;
this change makes the record reach them, it does not change what they say.

Guidelines: the native boundary and the one-place rule are `guidelines/DesignNative.md` §5b and
`guidelines/Design.md` §5, and the rule this patch is graded by — a defect inside the library behind an
unchanged contract is fixed upstream, in a minimal patch that mirrors the surrounding code — is
`guidelines/DesignNative.md` §5a; this is a submodule patch, so nothing there changes. The native build and the
submodule pin are `guidelines/BuildNative.md` §12 (`guidelines/Build.md` §12 routes to it), and the gitlink bump
is its one-commit rule; the census recipe this change measures with is `guidelines/BuildNative.md` §10k. No
guideline sentence is superseded.

Scope: the native step model, both variants equally — the phone overlay and the car panel read one step, so
nothing platform-specific is added. The car Surface, renderer and threading are untouched.
