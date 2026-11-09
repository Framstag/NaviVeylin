# Design — the clause is added where the sentence is composed, from the step the engine already announces

## Root cause

`VoiceGuidance.sentenceFor` (`app/src/main/java/com/naviveylin/navigation/VoiceGuidance.kt:55` at the base) walks the native sample
ordinals and reduces each recognised one to a localized fragment; the fragments are joined with a space and the first
letter is capitalized. It takes the sequence and nothing else. `VoiceGuidance.announce` (`:24` at the base) calls it with the same
argument list, and the engine's turn call site (`NavigationEngine.kt:1200` at the base) calls `announce(samples)`. The step the
engine is announcing is in `_state.value.nextStep` — a `RouteStep` copy that already carries `destination`
(`core/src/main/java/com/naviveylin/core/RouteStepGuidance.kt`, `copy.destination = step.destination`) — and nothing on
the way to the sentence reads it, so the destination the screens word through
`ManoeuvreLabels.carriesDestination` is never spoken.

## The single fix

Add the destination as one more fragment, and pass it from the one call site that announces a manoeuvre.

1. **`VoiceGuidance` gains the argument and the fragment.** `announce(samples: IntArray, destination: String?)` and
   `internal fun sentenceFor(samples: IntArray, destination: String?): String` append
   `context.getString(R.string.voice_destination, destination)` as the last part when a part was composed and the
   value is present. A sequence with no recognised sample returns `""` exactly as before — the clause cannot turn an
   unspoken sequence into a spoken one — and `null` reproduces the base sentence byte for byte.
2. **`NavigationEngine` resolves it once, from the step it is announcing.** On the turn path
   (`NavigationEngine.kt:1210` as this change leaves it) the destination is
   `_state.value.nextStep?.let { manoeuvreFromStep(it) }?.takeIf { ManoeuvreLabels.carriesDestination(it) }?.destination`
   and the announce call passes it. `manoeuvreFromStep` is the same projection every surface uses, and
   `carriesDestination` is the same gate the overlay, the car panel and the notification already read, so the spoken
   clause appears on exactly the manoeuvres whose rows word it. The GPS path (`:955`) passes `null`: a held GPS state
   names no manoeuvre.
3. **The word is a resource.** `voice_destination` = `towards %1$s` in `app/src/main/res/values/strings.xml` and
   `Richtung %1$s` in `values-de/strings.xml`, matching the existing `voice_*` fragments beside them and the
   `:core` `nav_towards` wording the screens already use. Positional format arg, one key per locale, per
   `guidelines/UI.md` §10.

The clause is appended at the end of the composed sentence. The sequence is a flat list of fragments and its only
separator is `Then`, which introduces the *second* manoeuvre; the sequence carries no marker for the end of the first
one, so appending is the one position that needs no new reading of the ordinal stream. Where a sequence carries a
`Then` block, the clause therefore follows the last fragment — the recorded decision settles an appended clause, and
this change does not re-open it.

## Blast radius

- Two production files and two resource files. `VoiceGuidance` changes one signature and one composition step;
  `NavigationEngine` changes two call sites and adds one resolution on the turn path. No other caller of `announce`
  exists (`tools/look.sh 'voiceGuidance\.announce' --files` → `NavigationEngine.kt` is the only production file).
- The sentence every existing case asserts is unchanged when no destination is passed: `NavigationEngineVoiceGuidanceTest`
  drives steps whose `destination` is absent, so `announce(samples, null)` keeps its wording, and the whole class stays
  green without a rewrite.
- The native sequence, the `VoiceSample` enum, the JNI bridge and the submodule pin are untouched; no gitlink moves.
- `:core` is untouched — the gate and the projection already exist, this change only reads them from the app side.
- Rollback: drop the argument and its fragment, pass no destination at the two call sites, delete the two strings and
  the delta. No state, schema or persisted value is involved, and a step without a destination record behaves exactly
  as the base does.

## The alternatives the evidence eliminated

- **A text carrier in the native sequence (a submodule patch to `VoiceSample`).** Eliminated by the enum itself: it is
  a closed set of identifiers with no text carrier (`VoiceInstructionAgent.h:49`–`:110`), so a name cannot travel in
  the `int[]` as it stands, and the recorded decision states the manoeuvre identifiers stay native.
- **A new sample ordinal that stands for the destination.** Eliminated by the same model: an ordinal carries no text,
  so the word would still have to come from another channel; the ordinal would add a native contract without carrying
  the value.
- **Passing the `Manoeuvre`, or its `ManoeuvreLabels.leadsOnto` string, into `VoiceGuidance`.** Eliminated by the
  wording: `leadsOnto` yields the motorway ref *and* the destination ("A8 towards München"), which speaks a road name
  the sample sequence never carried and changes the instruction beyond the item; the recorded decision names
  `announce(samples, destination: String?)`.
- **Re-deriving the destination from the published step list instead of `nextStep`.** Eliminated by duplication: the
  engine's announced step is `_state.value.nextStep`, its `guidanceStep` copy already carries `destination`, and a
  second lookup over `_state.value.steps` would be a second reading of the same record for no gain.
