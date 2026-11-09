# The spoken motorway instruction names its signposted destination

## Why

Issue **#807**. The screens word a motorway fork as "A8 towards München": `Manoeuvre.destination` carries the first
segment of the step's signposted record, and `ManoeuvreLabels.carriesDestination` gates the clause to a motorway enter
or change. The spoken instruction words nothing of it, so both branches of the fork sound the same — "turn right" and
"turn right" — while the overlay beside them says which one to take.

**Verified in the tree at the run's base `c0ddb97c`:**

- The sentence is composed from sample ordinals and takes no name:
  `app/src/main/java/com/naviveylin/navigation/VoiceGuidance.kt:24` — `fun announce(samples: IntArray)` — and
  `:55` — `internal fun sentenceFor(samples: IntArray): String`. `KeepLeft`/`KeepRight` fall through
  `else -> index += 1`, so the spoken manoeuvre set carries no road or destination word at all.
- Both announce call sites pass the sample sequence only: `NavigationEngine.kt:952` (a held GPS state) and `:1200`
  (the turn). Neither reads the announced step.
- The value is already in Kotlin and already gated by the same rule the screens use:
  `core/src/main/java/com/naviveylin/core/Manoeuvre.kt:129` — `destination = step.destination?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }`
  — and `core/src/main/java/com/naviveylin/core/ManoeuvreLabels.kt:83`–`:88`, `carriesDestination` (a
  `MOTORWAY_ENTER` or `MOTORWAY_CHANGE` that carries the record, and nothing else).
- Nothing speaks it: `tools/look.sh 'voice_destination' --files` → 0 matches in 0 files on the base.

**Root cause, in one sentence:** the app-side sentence compositor takes the native sample ordinals and no name, and the
turn call site hands it nothing from the step it is already announcing, so the destination the screens gained is never
spoken.

**The red case** — `NavigationEngineVoiceGuidanceTest#motorwayInstructionNamesTheSignpostedDestination`, one
`./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.navigation.NavigationEngineVoiceGuidanceTest.motorwayInstructionNamesTheSignpostedDestination" -PforceTests --no-build-cache`
run at **2026-11-09T22:47:49.361Z** (`evidence/red-on-HEAD.xml`, `tests=1 failures=1 errors=0`):

```
NavigationEngineVoiceGuidanceTest > motorwayInstructionNamesTheSignpostedDestination FAILED
    java.lang.AssertionError: both branches of a motorway fork must not sound the same
        expected:<[After 300 meters turn right towards München]> but was:<[After 300 meters turn right]>
```

The case drives the engine's own announce path: a `MOTORWAY_CHANGE` step carrying `destination = "München"` is
published to the listener, then the agent's turn sequence arrives. It is red on the base because the sentence the
engine speaks carries no destination, and it can only go green once the clause is composed.

**Owning requirement:** `openspec/specs/voice-guidance/spec.md`, "Navigation speaks the native voice instruction" — the
requirement whose sentence is the one gaining the clause. The delta adds one requirement beside it.

**Owning guideline section:** `guidelines/UI.md` §10 "Internationalisation / Localisation" — all user-facing text lives
in Android string resources, positional format args (`%1$s`) are used so translators can reorder, and every key exists
in both locales. It carries **no edit** this change owes: the clause is a new `R.string.voice_destination` in
`values/` and `values-de/`, the existing rules already state that form, and no guideline sentence states what a
spoken sentence contains (`tools/look.sh 'spoken' --path guidelines/UI.md` → the spoken-announcement switches rule at
`:133`–`:136` only).

**The run's recorded decision is what this change implements.** The package's `ITEM_CHANGE` block still names
`motorway-destination-hint`, but that change is archived and landed in the base
(`openspec/changes/archive/2026-11-06-motorway-destination-hint`, whose `Manoeuvre.destination`,
`ManoeuvreLabels.carriesDestination` and their cases are all present at the base); applying it again would be a
duplicate. The item's recorded decision `1` (2026-11-08) re-points the item's change marker to `none` and says the next
run plans `fix-motorway-destination-is-spoken`, so that landed copy is the stale pointer the decision anticipated, not
the plan for this item.

surface: **2 cases added, 0 rewritten, 0 fixtures realigned** — 4 production and resource files edited (`VoiceGuidance.kt`,
`NavigationEngine.kt`, `values/strings.xml`, `values-de/strings.xml`) and 2 test files (one new, one extended), 1
requirement added to `voice-guidance`, counted
before the delta with `tools/look.sh 'voice_destination' --files` (0 matches in 0 files),
`tools/look.sh 'sentenceFor' --files` (3 matches in 2 files: `VoiceGuidance.kt` 2, one archived log) and
`tools/look.sh 'voiceGuidance\.announce' --files` (4 matches in 2 files: `NavigationEngine.kt` 3, one archived log);
the record is `evidence/measure-surface.txt`.

## What Changes

1. **`VoiceGuidance` takes the destination clause.** `announce(samples: IntArray, destination: String?)` and
   `sentenceFor(samples: IntArray, destination: String?)` append one `R.string.voice_destination`
   (`"towards %1$s"` / `"Richtung %1$s"`) fragment when the value is present, so the sentence keeps its one localized
   composition and a sequence that carries no recognised instruction stays silent rather than speaking a dangling
   clause.
2. **`NavigationEngine` resolves it from the step it is already announcing.** The turn path reads
   `_state.value.nextStep`, projects it with `manoeuvreFromStep`, and passes its `destination` only while
   `ManoeuvreLabels.carriesDestination` holds — the screens' gate, so the phone and the car speak the clause on exactly
   the manoeuvres their rows word. The GPS path passes `null`, because a GPS state names no manoeuvre.
3. **Two strings and one requirement.** `voice_destination` in `app/src/main/res/values/strings.xml` and
   `values-de/strings.xml`, and the `voice-guidance` requirement "The motorway manoeuvre names its signposted
   destination" with its unit case, plus the engine case above.
4. **One unit case** — `VoiceGuidanceTest#aMotorwayInstructionNamesTheDestination` — asserts the compositor's own rule:
   a sequence with a recognised instruction gains the clause, an empty sequence stays silent.

The native sample sequence is unchanged: its `VoiceSample` enum carries identifiers, not text
(`VoiceInstructionAgent.h`), and the decision records that the manoeuvre identifiers stay native.

## What this does not do

- It does not change the wording of any sequence that carries no destination: `sentenceFor(samples, null)` is the
  string the base speaks, byte for byte, and the string resources it already used are untouched.
- It does not add a road name. The clause is the signposted destination only; the samples never carried the motorway's
  ref and this change does not manufacture one.
- It does not patch the submodule, move a gitlink, or add a native text carrier.
- It does not apply the landed `motorway-destination-hint` copy — the recorded decision re-points that marker.
