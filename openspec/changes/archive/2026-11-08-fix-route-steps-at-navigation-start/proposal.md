# Proposal — fix-route-steps-at-navigation-start

## Why

Root cause: the shared navigation state's step list is written **only** by the native guidance dispatch —
`onRouteSteps` (`app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt:1109-1125`) — and the start path
publishes the field's data-class default instead: `steps = emptyList()` in `startInternal`
(`NavigationEngine.kt:421`, inside the start path at `:375`; every line number in this proposal is the base
commit's, before this diff). The native bridge emits no list at start, because
the step list is a `RouteInstructionsMessage` and libosmscout's `RouteInstructionAgent::Process` returns no
message at all for anything that is not a `PositionMessage`
(`app/src/main/cpp/libosmscout/libosmscout/include/osmscout/navigation/RouteInstructionAgent.h:106-109`;
the dispatch is `OSMScoutClient.cpp:3787-3790`). A parked session therefore has an empty list on every surface
until the first fix — the car route description renders `rows=0 steps=0` — while the route it started on carries
the whole step set it was calculated with (`RouteEntry.steps`, the same record the phone route panel derives its
rows from: `app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/RouteEntry.java:22-31`,
read at `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt:806-807`).

Evidence: `com.naviveylin.navigation.NavigationEngineStepIndexTest` fails on HEAD — XML `tests="3" skipped="0"
failures="2" errors="0"`, `timestamp="2026-11-08T01:50:03.391Z"`:
- `startNavigation_publishesTheRoutesStepsBeforeAnyPositionUpdate`:
  `java.lang.AssertionError: expected:<[100.0, 500.0, 900.0]> but was:<[]>`
  (`NavigationEngineStepIndexTest.kt:130`)
- `startNavigation_clearsStaleStepsFromPreviousRoute`:
  `java.lang.AssertionError: expected:<[700.0, 950.0]> but was:<[]>`
  (`NavigationEngineStepIndexTest.kt:110`)

Retained as `evidence/TEST-NavigationEngineStepIndexTest-red-on-HEAD.xml` in the item's directory
(`.pi/issue-loop/items/719`, copied from
`app/build/test-results/testMobileDebugUnitTest/TEST-com.naviveylin.navigation.NavigationEngineStepIndexTest.xml`
at the moment the run produced it). The two `NavigationEngineStepIndexTest.kt` line numbers above are that red
run's own stack frames, before this change's diff moved the file's lines.

Repro:       `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.navigation.NavigationEngineStepIndexTest" -PforceTests --no-build-cache --rerun-tasks`
             (device recipe: `guidelines/BuildNative.md` §10a, `geo:` deep link, tap the route-list action while
             parked, `adb logcat -d -s NaviVeylin:* | grep 'route description rows='` — `rows=0 steps=0` on
             HEAD, `rows=<n> steps=<n>` with a fix.)

Spec:        `navigation-engine` / **One navigation state shared by all surfaces** —
             `openspec/specs/navigation-engine/spec.md:34-40` states the state carries the session's step list so
             every surface renders the same session; `navigation-controller` states the same field for the phone
             (`openspec/specs/navigation-controller/spec.md:93-94`, "The state SHALL carry the route's structured
             steps and the step ahead"). A session whose list is empty until a fix contradicts both.
             Guideline: `guidelines/Design.md` §3 "ViewModel & state", the bullet **"Progress, wall-clock and list
             state start at the invariant, not at the data-class default"** (`guidelines/Design.md:97-102`, the
             title and the step-list clause as this change writes them): the start
             path already seeds `remainingDistance = totalDistance` there for the same reason, and the step list
             is the one start field still left at the data-class default.

surface: 1 case added, 1 case rewritten, 0 fixtures realigned — `tools/look.sh 'steps = emptyList\(\)'`
(2 matches in 2 files: the writer at `NavigationEngine.kt:421` and one test fixture that builds a state value
directly, `CarHintContentTest.kt:157`), `tools/look.sh 'engine\.start\(|vm\.start\(|\.start\(routeEntry'
--path app/src/test` (14 matches in 10 files: every other session-start fixture hands over an entry whose
`steps` is absent, so the seed is empty for them and none of them changes), and
`tools/look.sh 'startNavigation_clearsStaleStepsFromPreviousRoute'` (2 files: this case, and the archived change
whose task hardened it — `openspec/changes/archive/2026-09-27-fix-zero-distance-to-turn/tasks.md:29`).

## Authority — the tree at this change's base commit

- **The start path clears the list and seeds nothing.** `NavigationEngine.kt:415-439` is the state publication a
  start performs; line `:421` is `steps = emptyList()`, and no line in `startInternal` (`:375-460`) reads
  `routeEntry.steps`. The whole file writes the field at exactly two lines: `:421` and `:1112`.
- **The only list publisher is the live dispatch.** `onRouteSteps` (`NavigationEngine.kt:1109-1125`) is fed by
  `NavigationListener.onRouteSteps`, which the bridge calls from `DispatchRouteSteps`
  (`OSMScoutClient.cpp:3870-3907`) on a `RouteInstructionsMessage` (`:3787-3790`).
- **The native agent publishes no list without a position.** `RouteInstructionAgent::Process` returns an empty
  result for any message that is not a `PositionAgent::PositionMessage`
  (`RouteInstructionAgent.h:106-109`); the list (`:118-131`) and its window re-emission (`:151-153`) are both
  produced from that message's route node.
- **The started route already carries the records.** `RouteEntry.steps` is a `RouteStep[]` of the same model the
  state carries (`RouteEntry.java:22-31`; spec `osmscout-jni` — "A calculated route publishes structured route
  steps", `openspec/specs/osmscout-jni/spec.md:598-630`), and the car row policy derives its rows from that
  record set (`auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt:280-327`).
- **Every start, including a reroute re-acquisition, goes through that one path.** `start()` (`:356`) and the
  engine's own re-acquisition both call `startInternal` (`:375`), so the seed is the same line for a fresh
  session and for a reroute restart.
- **The list is the start-frozen set, and its readers already fall back to it.** The car instruction panel reads
  `state.nextStep` and, when the live step is absent, the list's entry at `currentStepIndex`
  (`NavigationTemplateMapper.kt:210-215`, same fallback at `:414`), so a parked panel is fed by the seeded list
  without `nextStep` being seeded too; the route description derives its rows from the same list
  (`:280-283`), the phone's navigation details overlay reads it (`MapCanvasScreenModalBands.kt:393`), and the
  notification's current step falls back to it (`NavigationNotificationContent.kt:151`).

## What Changes

- **The start state carries the steps of the route it started on.** `startInternal` seeds `steps` from
  `routeEntry.steps` instead of the field's default, in the same single state publication that already carries
  the route geometry, the total and the destination — so a parked session's car route description lists the
  decisions the route was calculated with, and the phone's consumers read the same records. The seed is empty
  when the entry carries no description (`RouteEntry.steps` is null there), which is the same empty list those
  sessions have today.
- **The live dispatch still owns the window.** The first `onRouteSteps` replaces the seeded list with the
  engine's remaining window exactly as it does today (`NavigationEngine.kt:1109-1125`), so the seed is a start
  value, never a second source of truth: no new native surface, no cache, no second step model.
- **The step ahead is not seeded.** `nextStep` stays null until the live dispatch publishes it: the live step
  the native builder produces is the manoeuvre after the position with its remaining distance and its next-next
  window (`core/src/main/java/com/naviveylin/core/RouteStepGuidance.kt:27-57`), and a seeded copy would be a
  second owner of that rule (and of `isDataOnly`, which a facts-only node would violate). A surface that shows the
  step ahead already falls back to the seeded list entry when the live step is absent
  (`NavigationTemplateMapper.kt:213-215`), so nothing waits on a position any more.
- **The owning guideline bullet gains the step list.** `guidelines/Design.md` §3's "start at the invariant, not
  at the data-class default" bullet states the rule this fix applies and names `remainingDistance` as its
  example; the diff adds the step list beside it, because the defect was a start field left at the data-class
  default.
- **Not this change:** the native agent's behaviour is untouched (no submodule patch, no bridge change) —
  `RouteInstructionAgent` publishes what it publishes, and the state's start value comes from the app-side record
  set. The car template mapper, the row policy and the route description screen are unchanged: they already
  derive rows from the state's list, and this change is what puts a list there.
- This is a bug fix, additive at the API level: one field's start value changes, no signature, no dependency, no
  configuration. Rollback is the revert of one hunk in `NavigationEngine.kt` plus this change's guideline
  sentence and spec scenario — nothing persisted, nothing migrated.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `navigation-engine`: "One navigation state shared by all surfaces" gains the scenario that the state carries
  the started route's steps before any position update, and states in its requirement text that the step list is
  part of the session from its start. The requirement's other scenarios are restated with their baseline claims.

## Impact

- `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt` — the `steps` value of the start publication
  (`:421`) and its comment; one expression, no new API.
- `app/src/test/java/com/naviveylin/navigation/NavigationEngineStepIndexTest.kt` — the two cases (one new, one
  rewritten) and the route-entry helper that carries steps.
- `guidelines/Design.md` §3 — one bullet's example set.
- `openspec/specs/navigation-engine/spec.md` — via this change's delta, applied at archive.
- Surfaces affected: every reader of `core.NavigationState.steps` — the car route description
  (`auto/…/NavigationTemplateMapper.kt:280-327`), the car instruction panel's fallback for the current step
  (`:210-215`, `:414`), the phone navigation details overlay (`app/…/ui/map/MapCanvasScreenModalBands.kt:393`,
  feeding `ui/navigation/NavigationDetailsOverlay.kt:56-58`), and the ongoing notification's current step
  (`app/…/service/NavigationNotificationContent.kt:151`). The phone route panel is unaffected: it derives its
  rows from `RouteEntry.steps` already.
