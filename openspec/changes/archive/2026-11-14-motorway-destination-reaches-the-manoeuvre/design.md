# Design

## Context

See `proposal.md` — Why, for the mechanism and its file:line evidence. The constraint that decides every option
here: the route description is built once, by a fixed postprocessor list, and **three** consumers read it — the
Android bridge (`OSMScoutClient.cpp`, the list at its route-description block), the Qt client
(`RouteDescriptionBuilder.cpp`) and the demos. A step model is derived from it afterwards by
`RouteStepCollector`, which knows nothing but the node it converts.

The two facts the description states independently, and which this design must reconcile:

```
DestinationPostprocessor  (runs 10th)     InstructionPostprocessor (runs 13th)
  record -> last node with objects          manoeuvre -> the node the move happens at
  RoutePostprocessor.cpp:548                RoutePostprocessor.cpp:800 (link)
                                            RoutePostprocessor.cpp:1104 (direct change)
```

## Goals / Non-Goals

- Goal: a motorway **enter** or **change** step carries the signposted destination whenever the description
  holds one for the way it leads onto.
- Goal: the fix lives where the description is still a description — one place, upstreamable, so the Qt demo,
  the bridge and any future consumer see the same corrected description.
- Non-goal: any wording change. `ManoeuvreLabels.leadsOnto` and the three surfaces stay as they are.
- Non-goal: `MOTORWAY_LEAVE`. The wording gate names enter and change only
  (`core/src/main/java/com/naviveylin/core/ManoeuvreLabels.kt:83`), so a leave record would gain a reader that
  does not exist; if a surface ever words it, that is a separate change.

## Decisions

### D1 — Fix at the manoeuvre placement in `InstructionPostprocessor`, not in the collector

Chosen: when `InstructionPostprocessor` places a motorway enter or change on a node that carries no
`DestinationDescription`, it also attaches the description of the node the manoeuvre was decided at
(`lastNode`), when that node carries one. One private helper on `InstructionPostprocessor`, called from
`HandleMotorwayLink` and from the direct-change branch.

Alternatives eliminated:

- **`RouteStepCollector` inherits the predecessor's record.** It runs after the description is finished and
  sees only the node it converts; carrying "the record of the previous node" there would re-derive a
  description fact from adjacency, guess which way the record belongs to, and repeat the guess in the Java
  bridge's copy of the collector. It also cannot be upstreamed as a description fix.
- **A Kotlin-side fallback over `state.steps`.** Impossible, not merely unattractive: the record never leaves
  the native description, so no published step carries it and nothing above the bridge can recover it.
- **Rewriting `DestinationPostprocessor` to place the record where the manoeuvre will land.** It runs *before*
  the manoeuvre placement exists, and it would redefine what `DestinationDescription` means for the Qt client
  and the demos — a shared description fact changed to serve one consumer.

### D2 — The node's own record always wins

Chosen: the helper attaches only when the target node carries no `DestinationDescription`; an existing record
is never replaced or merged.

Alternatives eliminated:

- **Merge the two (own + neighbouring).** A step would carry two destinations of different ways, and
  `Manoeuvre.destination` keeps the first `;` segment only, so a merge would silently pick one anyway.
- **Neighbour wins (the sign, not the way).** It would overwrite the direct-change case that works today, and
  turn a correct record into a wrong one on every change whose node is tagged.

### D3 — The change is gated on the census, and stops if the count says the model is fine

Chosen: task 1 reads `ROUTE route census:` (keys `motorway`, `motorway_dest`, `dest_empty`) on the driven
route before any native patch is written. `dest_empty > 0` confirms that at least one enter/change step lost a
record the description holds, and the patch proceeds. `dest_empty = 0` means the model already carries every
record, the loss is the surface, and this change **stops** — the evidence note records the three numbers and a
separate change plans the surface cause.

Alternatives eliminated:

- **Patch first, measure after.** The premise was already assumed once: `motorway-destination-hint` design
  Risk 3 named this displacement and its measurement task was archived unchecked, which is how a rule that is
  green in unit tests came to fire on no real route.
- **Patch both the model and the surface blind.** Two causes, one of which does not exist; the second patch
  would change behaviour that is currently correct.

### D4 — Only enter and change are carried; "the node it was decided at" is `lastNode`

Chosen: the helper is called where the manoeuvre is decided — `HandleMotorwayLink`'s `lastNode` (the motorway
the route is on when the ramp begins) and the direct-change branch's `lastNode` — and only for
`MotorwayEnterDescription` / `MotorwayChangeDescription`.

Alternatives eliminated:

- **Also carry it for `MotorwayLeaveDescription`.** The leave's record has no reader (see Goals), so the patch
  would add an unused field on every exit.
- **Walk back to the nearest node carrying a record.** Unbounded: after a long motorway leg without tags it
  would attach an unrelated earlier destination.

## Risks / Trade-offs

- **A wrong record words a destination the driver does not want** — the neighbour's record belongs to the way
  before the ramp on some imports. Mitigation: only when the node carries none, and the D2 scenario pins that
  an own record wins; the census line plus the 3-scenario native case are the evidence.
- **A shared description changes for the Qt client and the demos** — a change line there may now print a
  destination it did not print before. Accepted: that is the same correction the phone and the car gain, and
  the demos are evaluation tooling, not a contract.
- **The census cannot distinguish which node held the record**, only whether the manoeuvre node had one.
  Mitigation: accepted; a zero count stops the change before any patch, which is the direction that matters.
- **A submodule patch cannot be delivered by the loop** — a person applies it and bumps the gitlink
  (`guidelines/Build.md` §12). The tracker item says so on its own line, and the loop's pre-screen parks it.

## Threading and lifecycle

None added. The postprocessor runs inside the route-description build, on the worker thread the calculation
already owns, before the steps are marshalled; no new state, no dispatcher, no lifecycle owner, no cached
value. The helper is pure over the description it is handed.

## Verification

- Native unit: `Tests/src/RouteStepModelTest.cpp` cases named in the delta — the link case red on the current
  submodule pin, green after; the two others green throughout.
- Revert-check (D2 invariant): attach unconditionally, the "keeps its own destination" case must fail, restore,
  forced green.
- Native compile: the submodule's own target builds (`gradle` native build of `:app` is the app's proof that
  the bridge still links).
- App suites that read destinations, named as the unaffected set: `core: ManeuverFromStepTest`,
  `core: ManeuverLabelTest`, `auto: NavigationTemplateMapperTest`, `app: NextTurnOverlayTest`,
  `app: CarHintContentTest`, `app: RoutePanelDiagnosticsTest`.
- Device: the `ROUTE route census:` line before and after on the same Dortmund → Essen route, `motorway_dest`
  and `dest_empty` quoted both times; then the phone overlay's second line and the car panel's target street
  read on that route's first motorway change.

## Open decisions

none
