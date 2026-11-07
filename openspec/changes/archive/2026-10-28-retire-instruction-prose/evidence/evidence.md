# Evidence — retire the instruction prose path

Run 2026-10-28, one session, after both preceding changes were archived.

## The prerequisite (tasks 1.1, 1.2)

`route-panel-from-steps` and `route-steps-live-guidance` are archived; `openspec list --json` shows neither.

JavaScout still read the prose path in **main** sources when this change started (`RouteRowBuilder` parsed
`route.descriptions` + `instructionLats` for its fallback, `JavaScoutInstructions` rebuilt `RouteInstruction`
from steps), so the deletion would have broken the submodule's own build. The migration landed first, as its
own fork commit:

| commit | what |
|---|---|
| `999acc16f` | `JavaScout: render the route list and the next-turn display from steps` — `NextTurnText` replaces `JavaScoutInstructions`, `RouteRowBuilder` loses the description fallback and its header-alignment rule, `RoutePanel`/`MainController` take `RouteStep[]`, `nextTurnDetail` reads `step.junction`/`step.destination`, the description and instruction tests are deleted |
| `6c8dc6f94` | `libosmscout-client-java: retire the instruction prose path` — this change's deletion |

Tracker #693 was commented with `999acc16f` and its evidence and is **left open**: the tracker's own rule
gives the closure to a person (`tools/issues/close.sh --comment-only`), so the task's literal "the issue is
closed" is the one clause this run cannot satisfy itself. Everything else in 1.1 is verified by the table
above and by the grep gates below.

## The deletion (2.1–2.4)

`OSMScoutClient.cpp` (both route-calculation paths, `calculateRouteWithObjectsWithProfile` and
`calculateRouteWithObjectsAsync`):

- the `RouteDescriptionPostprocessor` **second walk** — its `DescCallback`, the description lines, the four
  arrays (`instructionLats`, `instructionLons`, `instructionDistances`, `instructionTimes`) and the alignment
  guard that dropped them as a set — is gone (Δ 472 lines in that file);
- the route's published length is now read from the step list: `LastStepDistanceMeters(routeSteps.steps)` is
  the largest `distanceFromStart`, i.e. the description's last node, and the geometry fallback
  (`RouteLengthMeters(total, polylineMeters)`) still covers a route without a description;
- `JavaRouteInstruction`, `JavaRouteInstructionBuilder`, `CollectCallback`, `MoveToDescription` and
  `MoveToTurnType` were already gone (deleted by `route-steps-live-guidance`, −654 lines); the grep below
  confirms nothing of them remains;
- the JNI fills of those five fields, their field ids and the four `SetObjectField` writes are gone, and so
  are the five fields of `RouteEntry.java` (`RouteInstruction.java` deleted, listed out of `java/meson.build`
  and `CMakeLists.txt`).

Pin: `git submodule status app/src/main/cpp/libosmscout` →
`6c8dc6f94e050c5915ce31d507f2ab77fad4fabd`, working tree clean.

Two exclusions, named rather than silently kept:

- `osmscout::RouteInstructionAgent` / `RouteInstructionsMessage` / `NextRouteInstructionsMessage` and the
  builders' `GenerateNextRouteInstruction` are **engine** names in libosmscout's navigation API; they carry
  `RouteStep` now and are not this bridge's prose projection.
- the requirement `The published route length is a length of that route` is unchanged and still holds: the
  step list is that description's nodes, so a length read from it is "a length of that route", and the spec's
  "no description" scenario is the polyline fallback. No delta was added for it, because its text names no
  walk.

## Gates

| gate | result |
|---|---|
| native build, three ABIs | `assembleMobileDebug` + `assembleAutomotiveDebug` — `BUILD SUCCESSFUL`, 0 warnings, 0 errors; the rebuilt `.so` carries new timestamps for `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| submodule host suite | `Check navigation agents`, `Check route step model`, `Check route step model does not shadow the Qt route step` — 3/3 OK |
| `:osmscout-client-java` | 112 tests, 0 failed (`ClientValueTypesTest` now asserts a fresh `RouteEntry` has no steps instead of no description lines; `RouteInstructionFieldsTest` and the instruction half of `TurnTypeAndInstructionTest` are gone, the `TurnType` half is `TurnTypeTest`) |
| JavaScout | `mvn compile` green against the retired API; pure suite 49 tests, 0 failed |
| focused app suites | `com.naviveylin.ui.route.*`, `com.naviveylin.navigation.*`, `com.naviveylin.ui.map.*` — `BUILD SUCCESSFUL` |
| full gate | `test -PforceTests -PjvmTestsOnly --no-build-cache --continue` — `BUILD SUCCESSFUL` in 4m48s (`gate.log`) |
| device: model bridge | `RouteStepModelBridgeTest` — `OK (5 tests)` |
| device: live guidance | `RouteStepLiveGuidanceDeviceTest` — `OK (3 tests)`, `RouteDeviceTest: live step list: published=330 calculated=330` |
| device: route list | `RouteRowCensusDeviceTest` — `OK (1 test)`, `route census: steps=330 rows=24 …` (identical to the census taken before this change: the deletion changes no behaviour) |

Grep gates (`grep-gates.log`): clean in `app/src`, `auto/src`, `core/src`, `osmscout-client-java/src` and the
submodule's `libosmscout-client-java`, apart from the engine names listed above. In `openspec/specs` the only
remaining name is the requirement this change's delta **removes** (`Instruction segment time is the leg's
travel time`), which the archive takes away.

## Specs and catalogue (3, 4)

- `turn-instruction-localization` `## Purpose` re-written: the frontend builds the text from the step model's
  values and the native layer publishes values only. Read back after the edit.
- `i18n-l10n` `## Purpose` was already correct — it makes no claim about native-generated text, so nothing was
  changed there (the paragraph was read, line by line).
- citations of removed or renamed things re-pointed: `next-turn-overlay` (`onNextRouteInstruction` →
  `onNextRouteStep`), `roundabout-renderer` ("angle data from `RouteInstruction`" → "from the route step
  ahead"), and nine `Case:` citations in `osmscout-jni` that named the two deleted device tests and the deleted
  field test — they now name `RouteStepModelBridgeTest`, `RouteStepLiveGuidanceDeviceTest` and
  `RouteStepFieldsTest` cases that exist and pass.
- `FEATURES.md`: the retired key `turn-instruction-localization#Fallback to native text` is removed and
  `turn-instruction-localization#Route analysis rows are localized from the step model` (introduced by
  `route-steps-live-guidance`) added.
  `tools/gen-feature-list.sh --check-classification` → `unclassified: 0`, `classified: 182`, `stale: 3`.
  The three stale entries (`search-name-matching`, `search-result-row-icon`, `start-map-selection`) and the
  missing keys of unrelated capabilities (`landscape-layout`, `settings-persistence`, `auto-diagnostics`,
  `build-test-gate`, …) predate this change and are **not** touched here — they are a `cleanup-todo` item, not
  a silent exception.
- `openspec validate retire-instruction-prose --strict` → valid.

## Documentation (7)

`guidelines/Design.md` §5 gained "A calculated route publishes steps, never prose" (what `RouteEntry` carries,
the length derivation, and that `RouteDescription` stays a libosmscout model for the other clients); §13
records the removal and why the prose path was deleted rather than shimmed. The release notes are generated
from the shipped specs (`tools/gen-feature-list.sh`), so the removal is named there by this change's delta —
its REMOVED block carries the reason and the migration — and by the two Design.md sections.
