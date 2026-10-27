# Tasks

## 1. Red case

- [x] 1.1 Add `NavigationEngineVoiceGuidanceTest.voiceSampleSequenceIsSpokenWhileNavigating`: drive the
  fake native listener with the sample sequence for a turn after a distance while navigating, and
  assert the localized sentence reaches a fake output. It fails on HEAD because the engine has no
  `onVoiceInstruction` consumer. *(spec: voice-guidance — A native voice sequence is spoken while
  navigating)* — XML recorded before production code.
- [x] 1.2 Add `NavigationEngineVoiceGuidanceTest.voiceInstructionAfterStopIsSilent`: stop navigation,
  then deliver a sample; assert nothing is spoken and the output's stop was called. *(spec:
  voice-guidance — A voice instruction after navigation stops is silent)*

## 2. Voice seam

- [x] 2.1 Add `VoiceOutput` plus `AndroidVoiceOutput`: lazy `TextToSpeech`, pending sentence on a
  successful init, stop on request. Verify the structural check of the Kotlin sources (no compiled-file
  task is meaningful before wiring). *(spec: voice-guidance — A native voice sequence is spoken while
  navigating)*
- [x] 2.2 Add `VoiceGuidance`: walk the native sample identifiers in order, map distances, units,
  manoeuvres, `Then` and GPS samples to localized resources, ignore unknown identifiers, and skip an
  empty sequence. Verify by the red cases now turning green. *(spec: voice-guidance — A native voice
  sequence is spoken while navigating)*
- [x] 2.3 Add the spoken wording to `values/strings.xml` and `values-de/strings.xml`; the German
  catalog stays complete. Verify with the repository's string-completeness check (the gate's
  `check-*` scripts run it). *(spec: voice-guidance — A native voice sequence is spoken while
  navigating)*

## 3. Engine wiring

- [x] 3.1 Inject `VoiceGuidance` into `NavigationEngine`, override `onVoiceInstruction` on the main
  dispatcher, and ignore samples when `state.isNavigating` is false. Verify with 1.1.
  *(spec: voice-guidance — A native voice sequence is spoken while navigating)*
- [x] 3.2 Call `VoiceGuidance.stop()` from `stopNavigation()`. Verify with 1.2. *(spec:
  voice-guidance — A voice instruction after navigation stops is silent)*
- [x] 3.3 Bind `AndroidVoiceOutput` in a Hilt module and pass a no-op seam from `EngineUnderTest`;
  update the device test's direct constructor. Verify with a focused test run.

## 4. Classification and build

- [x] 4.1 Add `voice-guidance` to `tools/feature-list/specs.json` (area `routing`, phone surface) and
  run `bash tools/gen-feature-list.sh --check-classification`. *(spec: voice-guidance — all
  scenarios)*
- [x] 4.2 Build via the `build-app` skill (`:app:assembleMobileDebug`) and run the focused voice cases
  plus the affected engine cases. *(spec: voice-guidance — both scenarios)*
- [x] 4.3 Run the project gate once: `./tools/loop/gate.sh`. Copy the XMLs it cites into
  `openspec/changes/add-voice-guidance/evidence/` and the item directory. *(spec: voice-guidance —
  both scenarios)*

## 5. Verification and archive

- [x] 5.1 Run `bash tools/loop/evidence-check.sh openspec/changes/add-voice-guidance --root .` and
  repair every dangling pointer.
- [x] 5.2 Archive the change and confirm the delta merged into
  `openspec/specs/voice-guidance/spec.md`.

## Evidence

- Red before the listener override: `evidence/red-before-wiring.xml`, tests=2 failures=2
  (2026-10-27T18:54:03.855Z).
- Focused green: `evidence/focused-green.xml`, tests=2 failures=0 (2026-10-27T18:54:30.024Z).
- Affected package: 26 classes, 164 cases, zero failures
  (`:app:testMobileDebugUnitTest --tests "com.naviveylin.navigation.*"`).
- Revert check: `evidence/revert-check.txt`, mutated 1 failure, restored forced green
  (`evidence/revert-check-green.xml`).
- Mobile debug build: `evidence/build-mobile.log`, BUILD SUCCESSFUL.
- Gate: `evidence/gate.log` carries the `LOOP-GATE` line; tallies in `evidence/gate-tallies/`.
- Classification: `bash tools/gen-feature-list.sh --check-classification` exit 0 — unclassified 0,
  delta spec ids 1. The reported stale entries are a delta line not yet shipped plus three pre-existing
  entries; stale does not fail the gate.
