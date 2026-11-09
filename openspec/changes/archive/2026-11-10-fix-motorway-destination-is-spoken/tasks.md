# Tasks

The change's own line numbers are the tree's as this change leaves it; the base is `c0ddb97c`. The red run's own
tally is `evidence/red-on-HEAD.xml`, its log `evidence/red-on-HEAD.log`. The gate is not this session's: the run
deferred it to the child that follows the diff review, so no task here runs `./tools/loop/gate.sh`.

## 1. The red case

- [x] 1.1 Added `NavigationEngineVoiceGuidanceTest#motorwayInstructionNamesTheSignpostedDestination`
      (`app/src/test/java/com/naviveylin/navigation/NavigationEngineVoiceGuidanceTest.kt`): a `MOTORWAY_CHANGE` step
      carrying `destination = "München"` is published to the listener, the agent's turn sequence follows, and the
      case asserts the composed sentence. Verify: the case is red on HEAD at 2026-11-09T22:47:49.361Z —
      `expected:<[After 300 meters turn right towards München]> but was:<[After 300 meters turn right]>`
      (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`, `tests=1 failures=1 errors=0`).

## 2. The compositor — `VoiceGuidance.kt`

- [x] 2.1 `announce` and `sentenceFor` take `destination: String?` and append one `R.string.voice_destination`
      fragment after the manoeuvre fragments, only when a fragment was composed. Verify:
      `VoiceGuidanceTest#aDestinationClauseNeedsAnInstruction` reads the clause on a recognised sequence and `""` on
      an empty one.
- [x] 2.2 `voice_destination` in `app/src/main/res/values/strings.xml` (`towards %1$s`) and
      `values-de/strings.xml` (`Richtung %1$s`). Verify: `tools/look.sh 'voice_destination' --files` names both
      resource files.

## 3. The engine — `NavigationEngine.kt`

- [x] 3.1 The turn path resolves the destination from `_state.value.nextStep` through `manoeuvreFromStep` and gates it
      with `ManoeuvreLabels.carriesDestination`; the GPS path passes `null`. Verify: the red case is green
      (`evidence/green-focused.log`).
- [x] 3.2 The sibling cases this rule is read beside still pass: the whole `NavigationEngineVoiceGuidanceTest` class.
      Verify: its XML reads `failures=0 errors=0` at the same run.

## 4. The delta — `spec.md`

- [x] 4.1 The `voice-guidance` delta adds "The motorway manoeuvre names its signposted destination" with two
      scenarios, each naming one case. Verify: `openspec validate fix-motorway-destination-is-spoken --strict` passes.

## 5. Revert-check

- [x] 5.1 One deliberate mutation of the production invariant — the appended clause removed from `sentenceFor`, the
      tree otherwise as this change leaves it — the named case must fail for the expected assertion, the tree restored,
      and a forced green re-run after the restore. Triple: the invariant is "the composed sentence carries the
      destination"; the mutation is the clause fragment; the cases that must fail are
      `VoiceGuidanceTest#aDestinationClauseNeedsAnInstruction` and
      `NavigationEngineVoiceGuidanceTest#motorwayInstructionNamesTheSignpostedDestination`. Verify: both runs are
      quoted in `evidence/revert-check.log`.

## 6. Verification

- [x] 6.1 The focused suites on the finished tree: `NavigationEngineVoiceGuidanceTest` and `VoiceGuidanceTest`, forced,
      with their XML tallies copied beside this change. Verify: `failures=0 errors=0`, neither run cached.
- [x] 6.2 The structural check of the file roster this change edits beside its cases: `openspec validate
      fix-motorway-destination-is-spoken --strict` → `Change 'fix-motorway-destination-is-spoken' is valid`. The two
      new string keys' German completeness is the existing `GermanTranslationCompletenessTest`, part of the gate's
      suite — cited to the gate child rather than run here, because the gate is not this session's.
