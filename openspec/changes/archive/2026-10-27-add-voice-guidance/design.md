# Design

## Root cause / missing capability

The native bridge reports voice instructions, but the app has no consumer and no audio output:
`NavigationListener.java:94` declares `onVoiceInstruction(int[])`, and
`NavigationEngine.kt:906` implements the listener without that method. The only missing capability is
one seam from the existing sample sequence to speech.

## The single fix

1. `VoiceGuidance` converts the native sample identifiers into one localized sentence and forwards it
   to a `VoiceOutput`. It is the only place that knows the native `VoiceSample` ordering.
2. `AndroidVoiceOutput` owns `TextToSpeech`; it initialises lazily, speaks the pending sentence once
   the engine is ready, and stops on request. `TextToSpeech` is an Android context-bound object, so it
   stays in `:app` (`guidelines/Design.md` §1).
3. `NavigationEngine` owns the session policy: the native callback is marshalled to the main dispatcher
   (`guidelines/Design.md` §4), it is ignored when `state.isNavigating` is false, and
   `stopNavigation()` calls `VoiceGuidance.stop()`. The engine remains the one owner of navigation
   `guidelines/Design.md` §3).
4. The sentence uses string resources, so the phone's English and German catalogs both speak the
   instruction.

## Sample mapping

The identifiers are the ordinal values of `SampleVoiceInstructionMessage::VoiceSample` in
`app/src/main/cpp/libosmscout/libosmscout/include/osmscout/navigation/VoiceInstructionAgent.h`. The
agent emits an optional `After`, a distance sample, a unit sample, the manoeuvre, and optionally
`Then` plus a second manoeuvre. `VoiceGuidance` walks that sequence; unknown identifiers are ignored so
a future native enum value cannot crash navigation.

## Blast radius

- The engine gains one constructor dependency and one listener override; no state field changes.
- Navigation state, route acquisition, reroute policy, surfaces and the native bridge are untouched.
- `EngineUnderTest` passes a no-op output by default, so 25 existing engine test classes keep their
  behaviour; only the new case observes speech.
- `tools/feature-list/specs.json` gains one classification entry because the open change introduces
  one capability.

## Alternatives eliminated

- **Change the native bridge to return text.** Rejected: the bridge already exposes the stable sample
  sequence, and a native change would force a submodule bump and a rebuild for something the app can
  compose. The sample enum is the libosmscout API for exactly this consumer.
- **Play bundled Marble recordings.** Rejected: the repository ships no voice samples, and adding
  recorded assets would be a larger change with no testable host behaviour. Android TTS is already on
  every supported device.
- **Put the sentence builder in `:core`.** Rejected: it needs Android string resources and locale
  lookup, and `:auto` does not own navigation audio; the engine in `:app` is the consumer.
- **A user-facing on/off preference.** Rejected for this delta: the item asks for the missing
  capability, not a settings surface. A preference can be added later on top of this seam.

## Rollback

Remove the listener override and the `stopNavigation()` call, and delete the new classes and strings.
The app returns to silent navigation; no persisted data or public API changes.
