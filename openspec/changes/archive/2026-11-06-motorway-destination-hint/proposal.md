# Proposal

## Why

Joining a motorway or changing from one motorway to another is the one decision where the sign, not the
geometry, tells the driver which way to go: "A3 → A8 Richtung München" is a direction choice, "keep right
onto A8" is not — both branches of that split are "keep right" on some junctions. The app currently words
such a manoeuvre as the bare move plus the motorway's reference ("Rechts halten auf A8"), so the two
destinations of a motorway fork look identical on the phone overlay and in the car's instruction panel.

The signposted destination is already in the data and already reaches Kotlin: the native step model carries
`destination` (the raw `destination` tag, semicolon separated) beside the motorway records
(`libosmscout-client/include/osmscoutclient/routing/RouteStep.h`), the JNI bridge marshals it
(`OSMScoutClient.cpp`, `fDestination`), and live guidance copies it onto the step it publishes
(`core/src/main/java/com/naviveylin/core/RouteStepGuidance.kt`). The app's own projection of a step,
`Manoeuvre`, drops it — it carries "exactly the fields a step row needs", and the destination was not one of
them (`core/src/main/java/com/naviveylin/core/Manoeuvre.kt`). Nothing therefore words it, on any surface.

## What Changes

- `Manoeuvre` gains `destination: String?` — the first semicolon-separated segment of the step's
  `destination` record, trimmed, `null` when blank. `manoeuvreFromStep` fills it; no other field changes.
- `ManoeuvreLabels` gains `leadsOnto(resolver, manoeuvre)`: the thing a manoeuvre leads onto, which for a
  `MOTORWAY_ENTER` / `MOTORWAY_CHANGE` carrying a destination becomes the motorway's name or ref **plus** the
  signposted destination (`"A8 Richtung München"` / `"A8 towards München"`), and stays the manoeuvre's own
  name or ref otherwise. One new `:core` string, `nav_towards` = `"Richtung %1$s"` / `"towards %1$s"`, with
  its German translation.
- The phone next-turn overlay's second line carries that value instead of the bare ref.
- The car's `Step.Builder` `road` field carries the same value, resolved from the same function, so the two
  variants word the manoeuvre identically.
- Route-analysis rows inherit the value, because `ManoeuvreLabels.description` composes `leadsOnto`. No row
  code changes; the row's wording simply gains the same clause.
- The route census gains `motorway_dest` and `dest_empty` counters, so the presence of the tag on a real
  motorway route is measured rather than assumed (`guidelines/Build.md` §10k).

Additive: a step with no destination record inserts no clause into any surface's text, so every string a
surface shows today it still shows as it does today, with two exceptions the deltas declare — the car panel's
target street now falls back to the manoeuvre's motorway reference where it set no road at all, and the phone
overlay's second line prefers that reference over `displayName`. Rollback is a revert of the touched files — no
data, schema or persisted state is involved.

surface: 4 requirements and 13 scenarios added, 0 requirement bodies rewritten — 5 scenarios in the
`turn-instruction-localization` delta, 3 in the `next-turn-overlay` delta, 3 in the `auto/navigation-view`
delta and 2 in the `auto-navigation-hints` delta, counted by `grep -c '^#### Scenario:'
openspec/changes/motorway-destination-hint/specs/{turn-instruction-localization,next-turn-overlay,auto/navigation-view,auto-navigation-hints}/spec.md`
(5, 3, 3, 2).

Out of scope, filed as its own tracker item once this change exists: the spoken voice instruction, whose
samples are generated natively by `osmscout::VoiceInstructionAgent` and would need a submodule patch. The
navigation notification's own text is in scope here as task group 7: the tracker item that asked for it
(issue #806) is what this change's follow-up became, and its owner-recorded decision fixes the shape — the
hint's title stays the manoeuvre, the destination joins its secondary text, and the body inherits the clause
from `ManoeuvreLabels.description`.

## Capabilities

### New Capabilities

None. The behaviour belongs to capabilities that already own guidance wording and hint layout.

### Modified Capabilities

- `turn-instruction-localization`: a motorway manoeuvre's wording carries the signposted destination; the
  step model's destination record becomes a wording input.
- `next-turn-overlay`: the overlay's second line carries the ref **and** the signposted destination for a
  motorway manoeuvre.
- `auto/navigation-view`: the host instruction panel's target street carries the signposted destination for a
  motorway manoeuvre on both the current and the next step.
- `auto-navigation-hints`: the car hint's secondary text carries the same destination beside the distance and
  the arrival time; its primary text stays the manoeuvre alone.

## Impact

Code and resources:

- `core/src/main/java/com/naviveylin/core/Manoeuvre.kt` — the new field and its KDoc (the "carries exactly the
  fields a step row needs" sentence is what the change edits).
- `core/src/main/java/com/naviveylin/core/ManoeuvreLabels.kt` — `leadsOnto`, and `compositionTarget` folded
  into it.
- `core/src/main/res/values/strings.xml`, `core/src/main/res/values-de/strings.xml` — `nav_towards`. The
  German-completeness gate in `turn-instruction-localization` covers the second file.
- `app/src/main/java/com/naviveylin/ui/navigation/NextTurnOverlay.kt` — `instructionLines`'s second line.
- `app/src/main/java/com/naviveylin/ui/route/RouteStepValues.kt` — the census line's two new counters.
- `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt` — `road` in `stepForStep`.
- `app/src/main/java/com/naviveylin/service/NavigationNotificationContent.kt` — the car hint's secondary text
  (task group 7, issue #806).

No native change: the record is published, marshalled and copied on the live path already, so this is a local
override in `:core` and the Kotlin UI modules only — no submodule patch, and no change to
`OSMScoutClient.cpp` or the `libosmscout` tree.

Tests: `core` label and projection tests, the app overlay's row test, the car mapper's step test, and the
census test. Device measurement is the census line read on a motorway route.

Guidelines: `guidelines/UI.md` §8d "Cross-variant map presentation" is the section that owns what the two
variants must word alike, and the wording rule itself is the spec `turn-instruction-localization`.
`guidelines/UIPhoneMap.md` §8 owns the overlay's *sizing* and stays as it is, and its "Phone overlay layering
(the band stack)" section the same — a longer second line must fit both unchanged, which the design's risk
note covers. The card's content wording is owned by `guidelines/UIPhone.md` §18 "Ongoing navigation
notification (background driving)": its rules "NAVIGATION content is destination, manoeuvre, distance, arrival
and the stop action" and "labels and guidance wording are shared" gain the destination clause of the car
hint's secondary text, stated there and referenced from the spec (`documentation-ownership` "A concern has one
owning document"); `tools/check-doc-routes.sh` and the spec gates keep that claim honest at archive time. That the map data can carry the tag at all is a fact of the import
path (`stylesheets/motorways.ost` lists the `Destination` feature for `highway_motorway`,
`highway_motorway_link` and the trunk types), not a rule this change alters.
