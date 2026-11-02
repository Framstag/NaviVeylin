# Proposal

## Why

Live guidance's own step ahead is the **next described route node**, not the manoeuvre: `RouteStepBuilder`
takes `collected[index]` and `collected[index+1]`, while `RouteStepCollector` publishes one step per
described node and `WayNamePostprocessor`/`WayTypePostprocessor` describe almost every node
(`RouteStepBuilder.cpp:167-205`, `RouteStepCollector.cpp:468`). Consequence: the published step carries a
facts-only node (a name, a way type) as its manoeuvre and a plain node as its `nextNext`, so no guidance
surface can read "which exit" from the record; the app worked around it in `core/guidanceStep` with a
second derivation and a second copy of the 200 m window.

The record contradicts `osmscout-jni` — "Live guidance publishes route steps" (the step ahead is the
manoeuvre, its nested step the following manoeuvre), `next-turn-overlay` — "Next next" hint, and
`auto/navigation-view` — Current and next step. Tracker finding: issue #717.

**Root cause and its evidence.** The selector is a node selector, not a manoeuvre selector. Red-on-HEAD
cases (all failing before this change, `libosmscout: RouteStepModelTest`):

- "The step ahead is the next manoeuvre, not the next described node" — the step at 300 m (name + way
  type) is published instead of the turn at 400 m;
- "The following manoeuvre is announced before a roundabout is entered" — `nextNext` is the facts-only
  ring node, so `roundaboutLeave.exitCountTaken` never reaches a surface;
- "A manoeuvre beyond the collect window is still the step ahead" — a 5 km motorway leg falls through to
  the arrival fallback (`kind=target`);
- "A passed manoeuvre is no longer the step ahead" — the fixed-window walk republishes a described node.

Falsification of the premise: `git stash` the native selector on the same test names them again (see
`evidence/native-tests.log`).

The single resulting fix: the builder walks to the first **manoeuvre** after the position, extending its
look-ahead until it finds it or the route ends, and bounds the hint by the next **manoeuvre** rather than
the next collected node. Requirement contradicted: `osmscout-jni` — "Live guidance publishes route steps".

## What Changes

- `RouteStepBuilder` publishes the first manoeuvre after the position and puts the following manoeuvre in
  `nextNext`, bounded by `NextNextWindowMeters`. A described route node is never either row; the look-ahead
  extends until a manoeuvre is found, so a long motorway leg stays a motorway leg instead of announcing the
  arrival.
- `core/guidanceStep` copies the published record and derives nothing: `nextManoeuvreAfter` and
  `HINT_WINDOW_METERS` are removed, so the 200 m window has one owner (the native builder). The copy stays,
  because a surface must not mutate the engine's published state.
- `core/nextManoeuvreFrom` stays: the car's host next-step row is the following manoeuvre **without** a
  distance window (`auto/navigation-view`), which is a different question from the announcement's window.
- `ROW_MERGE_WINDOW_METERS` names its own number with the native constant in its doc, instead of aliasing
  the removed app-side window.
- The device checks assert the native record: the published step is the entry, its hint is the leave with
  its ordinal.
- Submodule patch, upstreamable: `RouteStepBuilder.{h,cpp}` and `Tests/src/RouteStepModelTest.cpp` inside
  `app/src/main/cpp/libosmscout`; the main repository bumps the gitlink. No JNI signature changes.

## Capabilities

### Modified Capabilities

- `osmscout-jni`: "Live guidance publishes route steps" states the step ahead is a manoeuvre and gains the
  scenarios that a described node is never either row, and that a manoeuvre beyond the first look-ahead is
  still found.
- `next-turn-overlay`: "Next next" hint changes its provenance — the hint is the nested step the native
  record publishes, no longer a Kotlin derivation over the route's step list.

`auto/navigation-view` keeps its behaviour unchanged: its next row already asked for the following
manoeuvre of the step list.

## Impact

- `app/src/main/cpp/libosmscout/libosmscout-client/{include/osmscoutclient/routing/RouteStepBuilder.h,src/osmscoutclient/routing/RouteStepBuilder.cpp}`
  — manoeuvre selector and extending look-ahead.
- `app/src/main/cpp/libosmscout/Tests/src/RouteStepModelTest.cpp` — four red-on-HEAD cases.
- `core/src/main/java/com/naviveylin/core/RouteStepGuidance.kt` — `guidanceStep(published)`, no derivation.
- `core/src/main/java/com/naviveylin/core/RouteRows.kt` — the row-merge constant owns its number.
- `app/src/main/java/com/naviveylin/navigation/{NavigationEngine.kt,RoundaboutInstructionDiagnostics.kt}`
  — new call shape, truthful diagnostics doc.
- Tests: `core: ManoeuvreAnnouncementTest` (rewritten to the native contract);
  `app(device): NextNextHintDeviceTest`, `NextTurnHintRowDeviceTest` (assert the native record).
- `guidelines/UIPhoneMap.md` §7 — one owner of the window; the record carries both rows.
- No Gradle, dependency, manifest or resource change. Threading is untouched: the builder runs where it ran
  before (the engine's own call), and the copy stays on the main-confined publication path
  (`guidelines/Design.md` §4).

Additive for consumers: `RouteStep.nextNext` keeps its type and meaning, and no JNI signature changes.
Rollback path: revert this change and the gitlink bump together; the previous node-based record returns and
`core/guidanceStep`'s derivation is restored with it (one commit contains both sides).
