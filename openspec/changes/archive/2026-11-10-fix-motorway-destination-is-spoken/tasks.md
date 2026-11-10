# Tasks

The base is `95efb9036e86b0eb72930085d00027e9949a3273`; the red run's tally is `evidence/red-on-HEAD.xml`.
The gate is not this worker's: the run defers it to a fresh child, so the last task here is the diff, not a
gate verdict.

## 1. The red case

- [x] 1.1 Add `aMotorwayChangeNamesItsDestination` and `aMotorwayLeaveKeepsItsDestinationUnspoken` to
      `app/src/test/java/com/naviveylin/navigation/NavigationEngineVoiceGuidanceTest.kt`, with the
      motorway fixture (`AFTER, 300 m, MwExitRight` — the samples `OnMotorwayLeave` words the change
      with). Verify: on the base, `./gradlew :app:testMobileDebugUnitTest --tests
      'com.naviveylin.navigation.NavigationEngineVoiceGuidanceTest' --no-build-cache` → the change case
      `FAILED` with `expected:<[… towards München]> but was:<[… the right]>`
      (2026-11-10T11:30:00Z; `evidence/red-on-HEAD.xml`, `tests=13 failures=1 errors=0`).

## 2. The clause

- [x] 2.1 Add `voice_destination` (`"towards %1$s"`) to `app/src/main/res/values/strings.xml` and
      (`"Richtung %1$s"`) to `app/src/main/res/values-de/strings.xml`, beside the other `voice_*`
      fragments. Verify: the resource keys compile in the next step's run.
- [x] 2.2 `VoiceGuidance.announce(samples, destination)` and `internal fun sentenceFor(samples, destination)`
      append one clause for a non-blank destination, and leave the sentence untouched for `null`
      (`VoiceGuidance.kt:24`, `:55`). Verify: `app: VoiceGuidanceTest` cases
      `aMotorwayDestinationJoinsTheSentence`, `aSentenceWithoutADestinationKeepsTodaysWording`,
      `aBlankDestinationAddsNoClause` and `aDestinationWithNoRecognisedSampleIsStillSpoken` green.
- [x] 2.3 `NavigationEngine`'s turn call site passes `spokenDestination()` — `manoeuvreFromStep(nextStep)`
      where `ManoeuvreLabels.carriesDestination` holds (`NavigationEngine.kt:1214`; base `:1200`); the GPS
      call site (`:954`; base `:952`) stays one-argument. Verify: the two engine cases of task 1.1 green,
      and `aMotorwayLeaveKeepsItsDestinationUnspoken` still green (the gate, not the samples, decides).
- [x] 2.4 Revert-check: remove task 2.2's clause (keep the parameter) and confirm
      `aMotorwayChangeNamesItsDestination` fails; restore and re-run the class green with the tally
      quoted (`guidelines/Build.md` §2, revert-check discipline).

## 3. Spec and evidence

- [x] 3.1 The `voice-guidance` delta states the requirement and its three scenarios, one case each
      (`openspec/changes/fix-motorway-destination-is-spoken/specs/voice-guidance/spec.md`). Verify:
      every `- **Case**` names a case that exists in the tree.
- [x] 3.2 Copy the red run's XML and the green run's XML into `evidence/` as they are produced, and
      quote their `tests=`/`failures=`/`errors=` and timestamps in this change. Verify: both files are
      present and their numbers match the quoted ones.
- [x] 3.3 Check the routed documents: `guidelines/Design.md` §1 already owns the layering rule and
      needs no edit; no guideline states a spoken sentence's content (`tools/look.sh 'voice-guidance'
      --path guidelines` → 0 matches). Verify: `tools/check-doc-routes.sh` in the gate child's run.

## 4. The diff

- [x] 4.1 Grade the diff against the review predicates: each `- **THEN**` clause of the delta has a
      failing assertion once the production change is removed; every claim in this change is made true
      by a hunk; the cited XMLs exist. Verify: the reading itself, recorded as this worker's report.
