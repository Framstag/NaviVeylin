# Design

## Context

`RouteStepBuilder::GenerateNextRouteInstruction` collects the described nodes from the position to
`previousDistance + CollectWindowMeters` (400 m), skips the ones at or behind the position and publishes
the next collected entry, with the entry after it as `nextNext` when the gap is at most
`NextNextWindowMeters` (200 m). The collector builds one step per described node
(`RouteStepCollector::AfterNode`), and `WayNamePostprocessor`/`WayTypePostprocessor` describe almost every
node — the device census of one 17.3 km route reads `steps=501 rows=34 info=466`, `leg_m median=10`.

Two independent facts follow, and the second is why the window has to change:

1. The first collected entry is normally a facts-only node (`kind=informative`), so the record's "manoeuvre
   ahead" is a street name a few metres ahead.
2. A long leg may carry no manoeuvre inside 400 m at all. Then `collected` holds only facts-only nodes or
   nothing, and the arrival fallback announces the destination as the manoeuvre ahead.

The app hid (1) by re-deriving the announced manoeuvre and the hint from the route's step list
(`core/guidanceStep`, change `fix-following-manoeuvre-hint`), which left two copies of the 200 m window and
two sources of truth for the hint.

## Goals / Non-Goals

**Goals**

- The record's step ahead is the first manoeuvre after the position; `nextNext` is the following manoeuvre
  within `NextNextWindowMeters`.
- A manoeuvre beyond the initial 400 m look-ahead is found; the arrival is the fallback only when the route
  has no manoeuvre left.
- One owner of the announcement window; every guidance surface reads the record.
- The behaviour is provable without a device: the submodule's `RouteStepModelTest` runs in the desktop build.

**Non-Goals**

- The route **list** keeps one entry per described node (`GenerateRouteInstructions`): the route panel's rows
  are a different contract (`route-analysis`), and the list is the car's source for its unbounded next row.
- The car's host next-step row keeps `core/nextManoeuvreFrom` (no window), because `auto/navigation-view`
  shows the next turn whenever there is one.
- No change to the instruction model, JNI signatures, threading or JNI ownership.

## Decisions

### D1 — Select manoeuvres, not nodes, by an extending look-ahead

The builder classifies a collected step the way `RouteStep::Kind` already does: `informative` and `unknown`
are facts of a node (`isDataOnly` in `core/RouteStepGuidance.kt` applies the same rule app-side), everything
else is a manoeuvre. It then

1. looks ahead with `CollectWindowMeters` as the first window;
2. if no manoeuvre lies after the position, doubles the window until one is found or the route's remainder
   is covered (`std::prev(last)->GetDistance()` is the route's end);
3. then, if the collection does not yet reach `step.distanceFromStart + NextNextWindowMeters`, collects once
   more up to that bound and takes the first manoeuvre strictly after the step as `nextNext`.

The doubling keeps the common case at one collection (a manoeuvre is usually within 400 m) and bounds the
worst case at about twice the walked nodes. The alternative — collecting the whole remainder always — would
walk every node of a 200 km route on every position update, and the engine publishes on every fix.

Distance arithmetic is unchanged: `FillProgress` still subtracts `nodeMeters` and the abscissa progress from
`step.distanceFromStart`, so the reported remaining distance belongs to the manoeuvre that is published, not
to the node the walk passed.

Risk: on a route whose last manoeuvre is far behind and whose target node carries no description, no
manoeuvre is found; the existing arrival fallback (`BuildNodeStep` of the last node, else the last collected
entry) still returns a step. That path is unchanged and covered by
"A passed manoeuvre is no longer the step ahead".

### D2 — The hint is bounded by the following manoeuvre's distance, not by the collection's

`nextNext` must be the following **manoeuvre**, so after selecting the step the walk continues past
facts-only nodes until a manoeuvre is found, and only then is the gap compared with `NextNextWindowMeters`.
A node between the two manoeuvres cannot become the hint; a manoeuvre beyond the window is not collected as
the hint even when the collection window reaches further.

### D3 — `core/guidanceStep` keeps only the copy, not a fallback derivation

With the record correct, the phone's overlay, notification and HUD read `step` and `step.nextNext` directly.
`guidanceStep(published)` therefore returns `copyForGuidance(published)` — the copy is a safety rule (a
surface must never mutate the engine's published record), not a derivation. `nextManoeuvreAfter` and
`HINT_WINDOW_METERS` are deleted; `ROW_MERGE_WINDOW_METERS` keeps its own documented number because the
row-merge question ("does this name change restate the following manoeuvre") is not the announcement window.

Alternatives eliminated:

- **Keep the Kotlin derivation as a fallback** — it cannot be reached after the native fix, and keeping it
  would preserve the second source of truth the change removes; its tests would assert a path no product
  code takes.
- **Read the list's next manoeuvre only when `published.nextNext` is null** — reintroduces the window in
  Kotlin for the case "no manoeuvre within 200 m", i.e. exactly the case the native bound answers.
- **Give the car the phone's window** — contradicts `auto/navigation-view` ("Next-next turn shown when
  present"), where a 300 m motorway gap is normal; the car's unbounded helper stays.

### D4 — The submodule patch is the change, the gitlink bump is its delivery

The fix belongs to `libosmscout-client` and is upstreamable as-is: a selector change in
`RouteStepBuilder.cpp`, a documentation-only change in its header, and tests in
`Tests/src/RouteStepModelTest.cpp`. The app repository carries it by bumping `app/src/main/cpp/libosmscout`,
so both halves land in one commit and roll back together.

## Threading / lifecycle

Unchanged. The builder is called where it was called (the navigation engine's own publication path), and
the Kotlin side stays a pure function over records on the main-confined path
(`guidelines/Design.md` §4). No dispatcher, no lifecycle, no new component.

## Verification

- Submodule, JVM-independent: `cmake --build build --target RouteStepModelTest` then
  `ctest --test-dir build -R RouteStepModelTest` — the four new cases, red before the selector change
  (`evidence/native-tests.log`).
- `core: ManoeuvreAnnouncementTest` — the copy and the native hint, including the roundabout ordinal.
- `auto: NavigationTemplateMapperTest` — unchanged, proves the car row still steps over described nodes.
- Device (`app(device): NextNextHintDeviceTest`): a real roundabout approach publishes `kind=roundaboutEnter`
  with `nextNext.kind=roundaboutLeave` and `hintExit>=1`; `RoundaboutInstructionDiagnostics` logs both
  ordinals from the record (`adb logcat -s NaviVeylin | grep 'rbt:'`, recipes in `guidelines/Build.md` §10).
