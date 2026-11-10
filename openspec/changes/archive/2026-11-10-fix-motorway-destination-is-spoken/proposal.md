# The motorway manoeuvre speaks its signposted destination

## Why

Issue **#807**. The screens gained the signposted destination in `motorway-destination-hint`
(archived `2026-11-06-motorway-destination-hint`, landed in this base): a motorway change now reads
"A8 Richtung München" on the phone overlay and in the car panel. The spoken instruction never gets
it, so both branches of a motorway fork still sound identical: the app composes the sentence from
the native sample ordinals alone.

**Verified in the tree at this run's base `95efb9036e86b0eb72930085d00027e9949a3273`:**

- `VoiceGuidance.announce(samples: IntArray)` and `internal fun sentenceFor(samples: IntArray)` take
  the sample sequence and nothing else (`app/src/main/java/com/naviveylin/navigation/VoiceGuidance.kt:24`,
  `:55`): no road name and no destination is an input to the sentence.
- The engine calls it at the two places a sequence is spoken:
  `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt:1200` (a turn) and `:952` (a held
  GPS state). Neither reads the manoeuvre ahead.
- The value the screens already use exists and is gated: `Manoeuvre.destination` is the first
  semicolon segment, blank → `null` (`core/src/main/java/com/naviveylin/core/Manoeuvre.kt:129`), and
  `ManoeuvreLabels.carriesDestination` accepts only `MOTORWAY_ENTER` / `MOTORWAY_CHANGE` with a
  record (`core/src/main/java/com/naviveylin/core/ManoeuvreLabels.kt:83`–`:88`).
- The native sample set carries no text: `SampleVoiceInstructionMessage::VoiceSample` is a closed
  enum (`app/src/main/cpp/libosmscout/libosmscout/include/osmscout/navigation/VoiceInstructionAgent.h:49`–`:114`),
  so the destination cannot travel in the sequence and the clause is the app's to compose. The agent
  words a motorway change through `OnMotorwayLeave`, i.e. the `MwExitLeft`/`MwExitRight` samples
  (`VoiceInstructionAgent.cpp:77`–`:84`), which is why both branches of the fork are otherwise the
  same sentence with a side swapped.

**Root cause, in one sentence:** the spoken sentence is composed from the sample ordinals alone —
`sentenceFor` has no destination input and the engine never reads the manoeuvre ahead — so the
signposted destination `Manoeuvre`/`ManoeuvreLabels` already hold for a motorway enter or change
never reaches the one surface that is heard.

**The red case** — `NavigationEngineVoiceGuidanceTest#aMotorwayChangeNamesItsDestination`, run on the
base with only the case added, one
`./gradlew :app:testMobileDebugUnitTest --tests 'com.naviveylin.navigation.NavigationEngineVoiceGuidanceTest' --no-build-cache`
run at 2026-11-10T11:30:00Z:

```
NavigationEngineVoiceGuidanceTest > aMotorwayChangeNamesItsDestination FAILED
    java.lang.AssertionError: … expected:<[After 300 meters leave the motorway to the right towards München]>
    but was:<[After 300 meters leave the motorway to the right]>
13 tests completed, 1 failed
```

`evidence/red-on-HEAD.xml` is that run's tally (`tests="13" failures="1" errors="0"`,
timestamp `2026-11-10T11:30:00.572Z`).

**Owning requirement:** `openspec/specs/voice-guidance/spec.md`, "Navigation speaks the native voice
instruction" (`:9`) — the requirement that owns every word the engine speaks; the delta adds its
sibling requirement for the destination clause.

**Owning guideline section:** `guidelines/Design.md` §1 "Module layering" (`:23`), the rule "a
shared behaviour lives once, in `:core` beside the state it reads" (`:30`): the gate and the value
come from `:core` (`Manoeuvre`, `ManoeuvreLabels`), and no second copy of the destination rule is
written in `:app`. No guideline section states what a spoken sentence contains; that rule is the
spec (`tools/look.sh 'voice-guidance' --path guidelines` → no match in `guidelines/`).

surface: 1 requirement added (3 scenarios), 6 cases added (2 in `NavigationEngineVoiceGuidanceTest`,
4 in the new `VoiceGuidanceTest`), 0 rewritten, 0 fixtures realigned, 2 Kotlin production files and 2
string files touched — counted with `tools/look.sh 'VoiceGuidance'` (33 files, of which the app's own are
`VoiceGuidance.kt`, `NavigationEngine.kt` and the two test files) and
`tools/look.sh 'voice-guidance' --path guidelines` (0 matches, so no guideline edit is owed).

## What Changes

- `VoiceGuidance.announce(samples, destination)` and `internal fun sentenceFor(samples, destination)`
  gain the destination as an optional input; a non-blank value appends one clause resolved from the
  new `R.string.voice_destination` (`"towards %1$s"` / `"Richtung %1$s"`).
- The engine passes the value only for a step `ManoeuvreLabels.carriesDestination` accepts — a
  motorway enter or change that carries a record — so a plain turn or a motorway leave keeps exactly
  today's sentence even though the native agent words the leave with the same samples.
- Two string resources, `app/src/main/res/values/strings.xml` and `values-de/strings.xml`.
- The `voice-guidance` delta and its cases; no native change, no submodule patch, no gitlink bump.

## What this does not do

- It does not add text to the native sequence or change `VoiceInstructionAgent`: the enum stays a
  closed sample set and the destination stays app-side (decision A on #807, 2026-11-07).
- It does not re-word the manoeuvre the agent already emits ("leave the motorway to the right"); the
  clause is added beside it, so a destination-less manoeuvre keeps today's sentence byte for byte.
- It does not touch the route notification's text (already carries `ManoeuvreLabels.leadsOnto`) or the
  screens.
