# Retire the instruction prose path

## Why

`route-panel-from-steps` moved the route list onto the step model and `route-steps-live-guidance` moved guidance
onto it. Nothing in `app`, `auto` or `core` reads guidance prose any more — but the bridge still produces it, on
every route calculation and on every position update:

- a second `RouteDescriptionPostprocessor` walk per calculation builds `RouteEntry.descriptions` plus four
  parallel arrays (`instructionLats`, `instructionLons`, `instructionDistances`, `instructionTimes`) and an
  alignment guard that drops them as a set when they disagree — on both route-calculation paths
  (`OSMScoutClient.cpp:8077`/`:8842`);
- `JavaRouteInstructionBuilder` and its `CollectCallback` (`:3531`, `:3678`) build `JavaRouteInstruction` per
  position update, including `MoveToDescription`/`MoveToTurnType` and a `"Keep "`-prefixed short description
  that exists only to be pattern-matched.

That is a second, unread model kept alive by its own tests, and it is the one JavaScout is being adapted away
from. Keeping it invites a new consumer to read English text and reintroduce the defect class this work removed.

## What Changes

- **BREAKING (submodule API)**: remove `RouteEntry.descriptions`, `instructionLats`, `instructionLons`,
  `instructionDistances`, `instructionTimes` and their alignment guard; remove `JavaRouteInstruction`,
  `JavaRouteInstructionBuilder`, `CollectCallback`, `MoveToDescription`, `MoveToTurnType`; remove the Java
  `RouteInstruction` class and its field tests. `TurnType` **stays** — `RouteStep.Direction` uses it.
- **Prerequisite, outside this change**: JavaScout's own adaptation (tracked as an issue) lands before this pin
  bump, because it reads `RouteEntry.descriptions` and `RouteInstruction`. Owner directive: separate.
- Repair the spec prose the deltas cannot carry: the `## Purpose` of `turn-instruction-localization` and of
  `i18n-l10n` still claim that the native layer generates English text and that the frontend relays it.
- Retire the requirement that described the arrays, re-point the requirement that described a next
  instruction's remaining time at the step, rename the four requirements whose names still say "instruction",
  and drop the one that is fully covered by the per-step requirement.
- Reconcile the feature catalogue: remove the capability keys of retired requirements, add the keys of the ones
  the two preceding changes introduced, and run the catalogue gate.

Not touched: `osmscout::RouteDescriptionPostprocessor` and the description model stay in libosmscout — Qt,
JavaScout-after-adaptation and the other clients use them. Only the Java bridge's own prose projection goes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `osmscout-jni` — **MODIFIED** "Per-step leg values on a calculated route" (the values are the steps' own, and
  there is no index alignment to guard any more) and "A next instruction's time is the remaining time of its
  leg" (re-pointed at the step ahead); **REMOVED** "Instruction segment time is the leg's travel time" (fully
  covered by the per-step requirement); **RENAMED** "A route instruction carries the roundabout exit ordinal" →
  "A route step carries the roundabout exit ordinal", "…circulation direction" → "A route step carries the
  roundabout circulation direction", "An unbuildable instruction never reaches a listener as null" → "An
  unbuildable step never reaches a listener as null".
- `turn-instruction-localization`, `i18n-l10n` — **no requirement delta**: only their `## Purpose` prose is
  repaired in the spec files themselves, because a delta's Purpose is read only when a capability is created.

## Impact

**Modules and files**

| Area | File |
|---|---|
| submodule (fork commit) | `libosmscout-client-java/src/OSMScoutClient.cpp` (both route paths, the legacy builder, the dispatch), `libosmscout-client-java/java/.../RouteEntry.java`, `.../RouteInstruction.java` (deleted) |
| `:osmscout-client-java` | `OSMScoutClient.java` override and `RouteInstructionFieldsTest`/`TurnTypeAndInstructionTest` in the module's own suite |
| app/auto/core | none — they carry no reference after the two preceding changes; this change's grep gate proves it |
| specs | `openspec/specs/osmscout-jni/spec.md` (deltas), `openspec/specs/turn-instruction-localization/spec.md` and `openspec/specs/i18n-l10n/spec.md` (Purpose prose) |
| catalogue | `FEATURES.md` (capability keys + the prose that covers them) |
| docs | `guidelines/Design.md` §5 (what the bridge publishes), §13 (Provenance) |

**Previous specifications changed**: `osmscout-jni` (2 modified, 1 removed, 4 renamed); `turn-instruction-localization`
and `i18n-l10n` Purpose prose.

**Guidelines**: `Design.md` §5 owns the submodule/pin discipline this change follows; `Design.md` §13 records why
the prose path was removed rather than shimmed.

**Native/JNI**: **submodule patch** — deletion only, one fork commit plus a pin bump, no local override.

**Rollback**: pin the submodule back and revert the app's spec/catalogue commits. A rollback restores the arrays
and `RouteInstruction`; no user data depends on them, so nothing is lost.

**Additive or breaking**: **breaking** for the submodule's Java API — the intended end state for JavaScout and its
own change.

## Ordering with the other two changes

```
route-panel-from-steps     app reads steps for the list        (no pin)
route-steps-live-guidance   pin: engine + listener on steps    (pin bump 1)
JavaScout adaptation        separate repository/fork work      (owner: separate)
retire-instruction-prose    pin: delete prose + arrays         (pin bump 2)
```

This change is not started before both preceding changes are archived and JavaScout's adaptation is confirmed.
