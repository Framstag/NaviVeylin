# Proposal

## Why

Approaching a roundabout the driver no longer sees which exit the route takes: the phone's next-next
row is empty before the ring and only the *entered* row carries the ordinal once inside it, while voice
guidance still announces "take the 2nd exit" in time. The defect contradicts the requirement
`next-turn-overlay` — "Next next" hint (a following manoeuvre within 200 m must show a second row) and,
on the car, `auto/navigation-view` — "Next-next turn shown when present".

**Root cause.** The route step model that replaced the instruction list (change
`route-steps-live-guidance`, commit `de37a2bd1`) publishes **one step per described route node**, not
one per manoeuvre: every node carries a `NameDescription` and a `TypeNameDescription`
(`RoutePostprocessor.cpp:WayNamePostprocessor` / `WayTypePostprocessor`), and
`RouteStepCollector.cpp:468` builds a step for every described node. Measured on device
(`openspec/changes/archive/2026-10-28-route-panel-from-steps/evidence/device-census.log:1`):
`steps=501 rows=34 … info=466 … leg_m median=10`.

Two consequences produce the symptom:

- `core/src/main/java/com/naviveylin/core/RouteStepGuidance.kt:29-40` — the published step ahead is
  normally a facts-only node, so `guidanceStep` substitutes the next real manoeuvre from the route list;
  the substituted record is built by `copyForGuidance` (`:47`) from a **route-list entry**, whose
  `copy.nextNext` (`:69`) is `null`, because a published step list carries no progress fields. The
  overlay's row 3 reads `step.nextNext`, so the row disappears exactly on approach.
- `app/src/main/cpp/libosmscout/libosmscout-client/src/osmscoutclient/routing/RouteStepBuilder.cpp:167-205`
  makes `nextNext` the *next collected entry* — a plain node (median 10 m ahead), never the leave, so
  even a native hint carries no exit ordinal (`RoundaboutInstructionDiagnostics.hintExit` reads 0).
- `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt:210` — `state.steps[currentStepIndex + 1]`
  is the next *node* for the same reason, so the car's host "next step" panel has the same defect.

Voice is unaffected because `VoiceInstructionAgent.cpp:OnRoundaboutLeave` pairs the enter and leave
descriptions itself and never reads `nextNext`.

## What Changes

- `:core` derives the hint next to the announcement it belongs to: after `guidanceStep` selects the
  manoeuvre to announce, the hint is **the following manoeuvre** in the route's step list when it lies
  within 200 m of the announced one; facts-only nodes between the two are skipped, and the record's own
  `nextNext` is overwritten (never used as a fallback, so a node hint cannot reach a display).
- The announced record is always a copy, so a surface can never mutate the engine's published state.
- The car's host instruction panel derives its "next step" the same way, from a `:core` helper both
  variants share, so the roundabout exit ordinal reaches the car's next-step panel too.
- `guidelines/UI.md` §7's sentence that the overlay's second row *is the nested step the published step
  ahead carries* is corrected to name the following manoeuvre of the route's step list.
- **Not in scope**: the native record's own `nextNext` semantics (a follow-up tracker finding), the
  engine publishing a manoeuvre-only step ahead, and any change inside the libosmscout submodule.

Additive in behaviour for both surfaces, no API removal: `RouteStep.nextNext` keeps its type and meaning
for any consumer, and no JNI signature changes. Rollback path: revert the change's commit; the previous
behaviour (no hint row on approach, node-based car next step) returns, and no persisted state or map data
depends on either.

## Capabilities

### New Capabilities

None — the change corrects two requirements that already exist.

### Modified Capabilities

- `next-turn-overlay`: the "Next next" hint requirement changes its provenance — the hint is the
  following *manoeuvre* of the route's step list within 200 m of the announced manoeuvre, not the nested
  step the published step ahead happens to carry.
- `auto/navigation-view`: the host instruction panel's next-step row SHALL show the following manoeuvre
  (with its own turn type and, for a roundabout exit, its ordinal), not the next route node.

## Impact

- `core/src/main/java/com/naviveylin/core/RouteStepGuidance.kt` — hint derivation and a shared
  `nextManoeuvreAfter` helper; `core/src/test/java/com/naviveylin/core/ManoeuvreAnnouncementTest.kt` —
  the red-on-HEAD cases first.
- `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt` — next-step row from the helper;
  `auto/src/test/java/com/naviveylin/auto/NavigationTemplateMapperTest.kt` — phone/car parity cases.
- `app/src/androidTest/java/com/naviveylin/navigation/NextNextHintDeviceTest.kt` (new) — the announced exit on
  real Andorra map data, and `app/src/androidTest/java/com/naviveylin/ui/navigation/NextTurnHintRowDeviceTest.kt`
  (new) — the rendered hint row's size, ink and wording on the device.
- `app/src/main/java/com/naviveylin/navigation/RoundaboutInstructionDiagnostics.kt` — the `hintExit`
  field becomes app-derived; the line documents that, so device evidence stays truthful.
- `guidelines/UI.md` §7 (live guidance paragraph) and this change's spec deltas.
- No submodule patch, no Gradle/AGP/dependency change, no manifest or resource change. Threading and
  lifecycle are untouched: the derivation is a pure function over records already on the main thread
  (`guidelines/Design.md` §4), called from the engine's own main-confined publication path.
