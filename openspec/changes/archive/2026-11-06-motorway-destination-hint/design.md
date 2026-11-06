# Design

## Context

See `proposal.md` — Why. The constraints that shape the approach:

- The step model already carries the value: `RouteStep.destination` is the raw `destination` tag,
  semicolon separated, `null` when the way has none
  (`libosmscout-client/include/osmscoutclient/routing/RouteStep.h:210`, marshalled by
  `OSMScoutClient.cpp` under `fDestination`). Live guidance copies it onto the announced step
  (`core/.../RouteStepGuidance.kt`, `copyForGuidance`). Nothing native changes.
- The record is attached by `RoutePostprocessor::DestinationPostprocessor`
  (`libosmscout/src/osmscout/routing/RoutePostprocessor.cpp:538`) to the junction node where the way
  changes, and the motorway manoeuvre record is attached by `InstructionPostprocessor::HandleMotorwayLink`
  (same file, `:764-820`) to the node where the entering ramp starts — the **same** node, so the two records
  need no correlation.
- The tag reaches the DB: the shipped type definitions list the `Destination` feature for
  `highway_motorway`, `highway_motorway_link` and the trunk types (`stylesheets/motorways.ost:65-107`), and
  `DestinationFeature::Parse` reads `destination`, `destination:forward` and `destination:ref`.
- Two surfaces word a manoeuvre: `app/.../ui/navigation/NextTurnOverlay.kt` (`instructionLines`: short + target
  name) and `auto/.../NavigationTemplateMapper.kt` (`stepForStep`: `Step.cue` = short, `Step.road` = target
  name). Both derive from `Manoeuvre`, and `ManoeuvreLabels` is the one word-source in `:core`.

## Goals / Non-Goals

- Goal: the destination reaches the manoeuvre projection, and one `:core` function states how a manoeuvre's
  target is worded — the overlay and the car both read that function, so the two cannot drift.
- Non-goal: the spoken instruction (its own tracker item), the notification *body* (it inherits the clause from
  `description()`), a third overlay row, and destinations on non-motorway manoeuvres.

## Decisions

### D1 — The destination goes inside the existing target segment

Chosen: the manoeuvre's *target* string becomes `"A8 Richtung München"`. Both surfaces already render a target
separately from the manoeuvre (overlay line 2, `Step.road`), so nothing moves and one string serves both.

Alternatives eliminated:

- **A third overlay row** ("A8" / "Richtung München"). Correct sign metaphor, but `Step` (Car App Library) has
  exactly `cue` and `road` for a manoeuvre; the car would have to fold the two rows into `road` anyway, and the
  phone would then hold a layout the car cannot mirror — a deliberate parity deviation (`UI.md` §8d) bought for
  pure typography.
- **One sentence from `:core`** ("Links halten auf A8 Richtung München"). Breaks both callers: `cue` is the
  manoeuvre alone by contract (`auto/navigation-hints` "Instruction is the primary car text"), and the overlay
  requires the break to fall between the instruction and the target name (`next-turn-overlay` "Text wrapping").

Risk: the second line grows and can wrap to two lines, which `maxLines = 2` already permits with an ellipsis.
Mitigation: a device-measured case at font scale 1.3 and a long German destination (see Verification).

### D2 — `Manoeuvre.destination: String?`, projected once

Chosen: `manoeuvreFromStep` fills a new field, like every other record it projects. `Manoeuvre`'s KDoc states
what it carries and the sentence "carries exactly the fields a step row needs" is amended with the destination.

Alternatives eliminated:

- **Each surface reads `RouteStep.destination` itself.** Four call sites would each need the first-segment
  rule and the gating rule; the projection exists precisely so a surface never derives.
- **`ManoeuvreLabels` takes a `RouteStep`.** The row derivation (`routeRows`) and the announcement path work on
  `Manoeuvre`, so the label layer would need both types or a second overload.

### D3 — Composition: one new key, appended inside the target

Chosen: `ManoeuvreLabels.leadsOnto(resolver, manoeuvre)` returns the target; `nav_towards` =
`"Richtung %1$s"` / `"towards %1$s"` is appended to the ref inside it, and `description()` keeps composing
`nav_keep_onto` / `nav_enter_onto` over that value, so route rows inherit the clause for free.

Alternatives eliminated:

- **A third-argument composite key per kind** (`nav_keep_onto_towards` = `"%1$s auf %2$s Richtung %3$s"`).
  Duplicates each kind's wording and adds one key per manoeuvre kind; a locale needing a different clause
  position can add such a key later without changing the callers.
- **Building the clause in Kotlin as `"Richtung $name"`.** Violates "every word a user reads is a resource"
  (`turn-instruction-localization` "Shared resources for phone and Auto") and would skip the German-parity gate.

### D4 — One destination, the first signposted value

Chosen: `destination` is split on `';'`, the first segment trimmed; blank → `null`.

Alternatives eliminated:

- **Join all values** ("München / Garmisch-Partenkirchen") — the line is a target string at 22sp next to a 64dp
  icon; two long names overflow on exactly the manoeuvres the feature exists for.
- **Show all as separate rows** — layout change rejected in D1.

Risk: the first value is not always the driver's branch (a ramp sign lists several). Accepted: the app shows the
sign's own first block, which is the convention both the German and English strings state, and the driver still
has the ref plus the manoeuvre. Recorded for the follow-up items so the notification and voice can decide
differently without reopening this decision.

### D5 — Gated to the two motorway manoeuvre kinds

Chosen: the clause renders only for `MOTORWAY_ENTER` and `MOTORWAY_CHANGE`. `Manoeuvre.destination` is filled for
any step, so widening later is a one-line change in `leadsOnto`.

Alternatives eliminated:

- **Any manoeuvre that carries the record.** The tag also sits on plain turns onto destination-tagged links and on
  motorway *leaves*, where the sign belongs to the exit and the requirement set says nothing yet — this change's
  specs would then have to justify every kind at once.
- **A configurable switch.** No user-facing requirement exists for one, and it would need persistence on both
  surfaces.

### D6 — Measure the tag's presence with the census before the wording lands

Chosen: `routeCensusLine` (`app/.../ui/route/RouteStepValues.kt`) gains `motorway_dest=<n>` and
`dest_empty=<n>` over the route's motorway manoeuvres, and a Dortmund → Cologne run reads the line
(`guidelines/Build.md` §10k) before the label rule is written.

Alternatives eliminated:

- **Unit fixtures only.** They prove the wording, not that a real German import carries the tag — the import path
  makes it likely, not certain, and the project's rule is to measure the data before claiming a behaviour.
- **A one-off ad-hoc log of raw steps.** The census line already exists, is documented, and is read by a recipe.

### D7 — No new threading or lifecycle

Every function is pure over a `Manoeuvre` and a `StringResolver`; the overlay and the mapper already run on the
main/compose thread and the step they read is the copy live guidance publishes (`guidanceStep`). No dispatcher,
no IO, no new state, no lifecycle owner.

### D8 — The car hint's destination joins its secondary text (issue #806)

Chosen: `carHint`'s primary text stays `ManoeuvreLabels.short` (the manoeuvre, one wording for the car hint
and the on-screen display), and its secondary text — distance and arrival time — gains the value of
`ManoeuvreLabels.leadsOnto` when the manoeuvre carries a signposted destination. The hint therefore words the
destination from the same `:core` function the overlay's second line and the car's `Step.road` read.

Alternatives eliminated:

- **A third text field on `CarHintContent`.** The host renders a hint as an instruction, a secondary line and
  a manoeuvre icon; a third string would have no place to render.
- **Replacing the distance/ETA line with the destination.** Both are what the driver needs at that moment and
  `auto-navigation-hints` "Distance and arrival time as secondary text" requires the pair; a destination that
  displaced them would trade one requirement for another.
- **A second composite resource key for the hint** (`"%1$s · %2$s · Richtung %3$s"`). It would duplicate the
  separator wording and let the hint's destination clause drift from `nav_towards`; the item's own decision
  asks for the `:core` wording, so the clause is reused and only the existing `" · "` separator joins it.

## Risks / Trade-offs

- **A long destination overflows the target line** → existing `maxLines = 2` + ellipsis; a device case at font
  scale 1.3 with `"Garmisch-Partenkirchen"` (see Verification). Accepted degradation, not a layout change.
- **The tag is absent on stretches where the driver expects it** (a motorway entered without a link, or an import
  whose ways lack `destination`) → the wording is then byte-identical to today's, and the census counters make
  the gap measurable instead of invisible.
- **The record can sit on the junction node before the manoeuvre** (`DestinationPostprocessor` attaches to the last
  node with intersecting objects) → `motorway_dest` counts the manoeuvre nodes only, so a systematically displaced
  record shows as a low count and is caught by the measurement task before the label rule is believed.
- **Map-language text in the UI** ("München" on a German map, "Munich" nowhere) → accepted: it is a proper name
  read off a physical sign, and the `retire-instruction-prose` rule governs prose, not names.

## Migration Plan

No data, schema or persisted state. Land in the order the tasks state (measurement, projection, wording, surfaces,
gate). Rollback: revert the touched files; every surface returns to today's string because the clause lives only in
`leadsOnto`.

## Verification

- Unit: `core: ManeuverFromStepTest`, `core: ManeuverLabelTest`, `app: NextTurnOverlayTest`,
  `auto: NavigationTemplateMapperTest`, `app: RoutePanelDiagnosticsTest` — one case per delta scenario, named in
  the specs.
- Device measurement of the data: the `ROUTE route census:` line on a Dortmund → Cologne route, quoting
  `motorway_dest` and `dest_empty`.
- Pixels: an instrumented render of the overlay with a synthetic motorway-change step and a long destination
  (`app/src/androidTest/.../NextTurnHintRowDeviceTest`), screenshot judged with the `pixel-check` skill for "the
  target line stays inside the card and ends with an ellipsis, and the card gains no third row". A stationary
  emulator cannot produce a real motorway route, so the step is a fixture — the claim is about layout, and it says
  so in the task.

D4's "first value only" is a recorded decision (see D4), not an open one: the notification and the voice item may
revisit it without reopening this design.

## Open decisions

none
