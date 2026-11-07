# Tasks — retire the instruction prose path

## 1. Prerequisite gate (no spec delta — ordering)

- [x] 1.1 Confirm JavaScout's adaptation is done: the tracking issue is closed and its own pin no longer reads
  `RouteEntry.descriptions` or `RouteInstruction`. Verification: the issue's closure names the commit; without it
  this change is not started.
- [x] 1.2 Confirm both preceding changes are archived (`route-panel-from-steps`, `route-steps-live-guidance`).
  Verification: `openspec list --json` shows no in-progress entry for either.

## 2. Submodule: delete the prose projection (spec: `osmscout-jni` — Per-step leg values on a calculated route, A next instruction's time is the remaining time of its leg)

- [x] 2.1 Delete `JavaRouteInstruction`, `JavaRouteInstructionBuilder`, `CollectCallback`, `MoveToDescription`
  and `MoveToTurnType`; confirm nothing in the bridge references them. Verification: the native library builds for
  all three ABIs; `RouteStepModelTest` and `NavigationAgentTest` green.
- [x] 2.2 Remove `descriptions`, `instructionLats`, `instructionLons`, `instructionDistances`,
  `instructionTimes` and the alignment guard from **both** route-calculation paths, and the fields from
  `RouteEntry.java`. Verification: a calculated route still publishes `steps` and `databaseMapping`; the device
  case from `route-panel-from-steps` (task 7.3) still lists rows.
- [x] 2.3 Delete `RouteInstruction.java`. Verification: `:osmscout-client-java` and the app compile; the deletion
  is a compile error anywhere a reader remained.
- [x] 2.4 Commit in the fork, verify the submodule tree is clean, bump the pin. Verification: `git submodule status`
  names the new commit; `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug` green.

## 3. Spec prose the deltas cannot carry

- [x] 3.1 Repair `openspec/specs/turn-instruction-localization/spec.md` `## Purpose`: text is built from the step
  model in the frontend; the native layer publishes values only. Verification: the paragraph is read after the edit
  and states no native-English premise.
- [x] 3.2 Repair `openspec/specs/i18n-l10n/spec.md` `## Purpose` where it claims native-generated text is relayed.
  Verification: read after the edit; `openspec validate --changes --strict` green.
- [x] 3.3 Update every spec that cites a requirement this change removes or renames — `route-analysis`,
  `routing-summary`, `navigation-status-details` cite "Per-step leg values on a calculated route", which survives
  with its name. Verification: `grep -rn "Instruction segment time" openspec/specs` returns nothing.

## 4. Feature catalogue

- [x] 4.1 `FEATURES.md`: remove the capability key of every retired requirement, update the keys of the renamed
  ones, and add the keys the two preceding changes introduced. Verification: `tools/gen-feature-list.sh` green;
  the diff reviewed line by line against the requirement names.
- [x] 4.2 Confirm one capability key per shipped requirement and no key for a requirement that no longer exists.
  Verification: the generator's own check output, recorded in the change evidence.

## 5. Grep gates

- [x] 5.1 No reference in the app trees: `grep -rn "RouteInstruction\|instructionLats\|instructionLons\|instructionDistances\|instructionTimes" app/src auto/src core/src` returns nothing.
- [x] 5.2 No reference in the bridge trees: the same grep over `osmscout-client-java/src` and
  `app/src/main/cpp/libosmscout/libosmscout-client-java` returns nothing except `TurnType` (which stays).
- [x] 5.3 No shipped spec names the removed type: the same grep over `openspec/specs` returns nothing.
  Verification: the three outputs are recorded in the change evidence.

## 6. Build, suite and behaviour

- [x] 6.1 Build both flavors without warnings; all three ABIs; submodule suite green.
- [x] 6.2 Focused suites (`com.framstag.libosmscout.client.*`, `com.naviveylin.ui.route.*`,
  `com.naviveylin.navigation.*`), then the full both-flavor gate once. Name exclusions.
- [x] 6.3 Re-run the route-list device case from `route-panel-from-steps` and the live-guidance device case from
  `route-steps-live-guidance` after the pin bump. Verification: both still pass — a deletion that changes
  behaviour is a defect.

## 7. Documentation and release

- [x] 7.1 `guidelines/Design.md` §5: state what the bridge publishes (steps, not prose) and that `RouteDescription`
  stays a libosmscout model used by other clients.
- [x] 7.2 `guidelines/Design.md` §13 (Provenance): record the removal and the reason the prose path was deleted
  rather than shimmed.
- [x] 7.3 Name the API removal for the release notes input, so the published artifact's consumers see it.
  Verification: the release-notes source names the removal.
