# Tasks — route steps for live guidance

## 1. Submodule: the engine publishes steps (spec: `osmscout-jni` — Live guidance publishes route steps)

- [x] 1.1 In the fork, extend `NavigationListener` with `onRouteSteps(RouteStep[])` and
  `onNextRouteStep(RouteStep)`; cache their method ids in `NavigationListenerMethods`; keep every host-facing
  send inside the existing guard. Verification: the bridge builds for all three ABIs; a mutation that removes the
  guard is caught by the `car-host-fault-isolation` device case (recorded, not run here).
- [x] 1.2 Swap the agent in `JavaNavigationController` to
  `osmscout::RouteInstructionAgent<osmscout::client::RouteStep, osmscout::client::RouteStepBuilder>` and dispatch
  `RouteInstructionsMessage<RouteStep>` / `NextRouteInstructionsMessage<RouteStep>` through `CreateJavaRouteSteps`.
  Verification: `RouteStepModelTest` still green; a bridge smoke case publishes a step list for a two-manoeuvre
  route.
- [x] 1.3 Add the dispatch timing line (total milliseconds per publication, step count, coordinate-free).
  Verification: `checkNoCoordinatesInLogs` green; the line appears in a device logcat.
- [x] 1.4 Commit in the fork (message names the JavaScout breakage), verify the submodule tree is clean, bump the
  pin, and confirm `git submodule status` names the new commit. Verification: `./gradlew :app:assembleMobileDebug`
  with the new pin; no file under the submodule left modified.

## 2. App state and matching (spec: `navigation-controller` — Navigation state exposed as StateFlow; `rerouting-visual-feedback` — Rerouting state is exposed in navigation state)

- [x] 2.1 `:core` `NavigationState` carries the step list and the step ahead instead of instructions; the row
  builder from `route-panel-from-steps` becomes the single text entry point. Verification: `:core` compiles with
  no `RouteInstruction` import; `ManoeuvreLabelTest` green.
- [x] 2.2 `NavigationEngine` implements the new listener callbacks and publishes the step state. Verification:
  `NavigationEngineTest` "the step ahead reaches the state", `NavigationEngineRerouteTest` "the new route's steps
  arrive via onRouteSteps and clear isRerouting".
- [x] 2.3 `StepIndexSelection` matches by distance **and** kind. Verification: `StepIndexSelectionTest` "a dual
  node (turn then motorway entry) binds to the right step"; the existing monotonic cases stay green.
- [x] 2.4 `RoundaboutInstructionDiagnostics` reads the step's kind, exit ordinal, circulation direction and nested
  hint. Verification: `RoundaboutInstructionDiagnosticsTest` "the line names the kind and the ordinal, never a
  coordinate".
- [x] 2.5 Revert-check for the matching rule: reduce it to distance only — the dual-node case must fail; restore;
  re-run `-PforceTests --no-build-cache` green.

## 3. Live surfaces (spec: `next-turn-overlay` — Turn type icon and "Next next" hint; `i18n-l10n` — Native-generated navigation text is localized at the frontend)

- [x] 3.1 `NextTurnOverlay` icon from kind + move, text from the row builder, nested hint row.
  Verification: `NextTurnOverlayTest` "a motorway change is drawn as a keep, not a turn", "the next-next
  roundabout exit shows its own number", "the next-next hint renders from the nested step".
- [x] 3.2 `NavigationArrowRenderer` and `NavigationDetailsOverlay` on the step model. Verification: their cases
  compile and pass against steps.
- [x] 3.3 `NavigationNotificationContent` (notification, ongoing notification, HUD text) from the row builder.
  Verification: `NavigationNotificationContentFormatterTest` and `CarHintContentTest` "the notification names the
  German manoeuvre"; the notification's text cases keep their assertions.
- [x] 3.4 Car surfaces: `NavigationTemplateMapper`, `ManeuverGlyphs`, `RouteDescriptionScreen`, the car
  `NavigationScreen` state. Verification: `NavigationTemplateMapperTest` (cue, text, hint), `ManeuverGlyphsTest`,
  `RouteDescriptionScreenTest` green; `CarSessionSurface` renders the localized cue (`CarSessionSurfaceComposeTest`).
- [x] 3.5 Delete `core/TurnInstructionLocalizer.kt` and its test file's fallback cases; keep the surviving label
  cases under `ManoeuvreLabelTest`. Verification: `grep -rn "TurnInstructionLocalizer" app auto core` returns
  nothing; the German cases still assert the same strings.
- [x] 3.6 Revert-check for the nested hint: stop publishing `nextNext` — "the next-next hint renders from the
  nested step" must fail; restore; forced green.

## 4. No instruction type left (spec: `turn-instruction-localization` — Instruction text is rebuilt from structured fields, All native instruction kinds are covered)

- [x] 4.1 Remove every `RouteInstruction` import and reference from `app`, `auto` and `core`. Verification:
  `grep -rn "RouteInstruction" app/src/main app/src/test auto/src core/src | grep -v libosmscout` returns nothing;
  both flavors build.
- [x] 4.2 Rewrite the localizer cases the removal of the native fallback retires, in `ManoeuvreLabelTest`.
  Verification: "an unmapped manoeuvre renders the neutral wording" green; no case asserts native text.

## 5. Cost measurement (spec: `osmscout-jni` — Live guidance publishes route steps)

- [x] 5.1 Record the dispatch cost with the instruction agent (before) and with the step agent (after) on one
  19-manoeuvre route, on device, with the timing line. Verification: both numbers in the change evidence.
- [x] 5.2 Decide from 5.1: if the step dispatch exceeds a few milliseconds, cache one DTO per `currentNodeIndex`
  and refresh only the progress fields; otherwise record "no cache needed" and the number that justified it.
  Verification: either the cache with its own timing line, or the recorded decision.

## 6. Contract tests for the listener (spec: `osmscout-jni` — An unbuildable instruction never reaches a listener as null)

- [x] 6.1 Non-null guarantee for steps: extend the bridge case so a list with an unbuildable element is delivered
  empty instead. Verification: the case fails when the guard is removed (mutate once, restore).
- [x] 6.2 Roundabout, circulation direction and the nested hint's own ordinal re-pointed at steps.
  Verification: `RouteInstructionFieldsTest` cases renamed to the step model and green.

## 7. Build, suite and device verification

- [x] 7.1 Build both flavors without warnings, all three ABIs of the native library.
- [x] 7.2 Focused suites first (`--tests "com.naviveylin.navigation.*"`, `--tests "com.naviveylin.auto.*"`,
  `--tests "com.naviveylin.core.*"`), then the full both-flavor gate once. Name exclusions.
- [x] 7.3 Phone device: `RouteStepLiveGuidanceDeviceTest` — step ahead with remaining distance, nested hint, list
  on route change.
- [x] 7.4 Car: AAOS AVD per `guidelines/Build.md` §10a, judged per §10b — instruction panel, cue and hint. (2026-10-28: driven on the automotive AVD; cue and hint read from `Diag/HOST`, the surface judged from a frame; see `evidence/evidence.md`)
- [x] 7.5 Pixels: one overlay screenshot judged with `pixel-check` for the kind-based icon claim. (2026-10-28: `evidence/device-overlay-step-cue.png` — `50 m` + `Rechts abbiegen` + a right arrow, with the host post beside it)

## 8. Guidelines and handover

- [x] 8.1 Update `guidelines/Design.md` §5 (the submodule patch and its pin) and §8 (the car/cross-variant wiring
  after the listener change).
- [x] 8.2 Update `guidelines/UI.md` §7/§8 where they describe how guidance text is produced.
- [x] 8.3 File an issue for JavaScout's adaptation so the breakage is tracked outside this change.
- [x] 8.4 Record in `guidelines/Design.md` §13 (Provenance) that live guidance moved to the step model and why the
  prose path was removed rather than shimmed.
