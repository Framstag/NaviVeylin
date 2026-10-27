# Proposal

## Why

Turn-by-turn navigation has no audio output. The native bridge already reports the instruction the
libosmscout voice agent prepared, but the app never consumes it: `NavigationListener.onVoiceInstruction`
exists at `app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/NavigationListener.java:94`,
and the engine's listener (`app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt:906`) does
not override it. A scan of the app sources finds exactly one `onVoiceInstruction` occurrence — the
native callback declaration — and no `TextToSpeech` use:

```
$ grep -R "onVoiceInstruction" -n app/src/main app/src/test app/src/androidTest --include='*.kt' --include='*.java' | wc -l
1
$ grep -R "TextToSpeech" -n app/src --include='*.kt' --include='*.xml' | wc -l
0
```

The missing capability is one seam: turn a native `int[]` sample sequence into a spoken sentence and
send it to the device's TTS engine, while the navigation session owns when it may speak.

The red case is the acceptance scenario below before the capability exists:
`NavigationEngineVoiceGuidanceTest.voiceSampleSequenceIsSpokenWhileNavigating` drives the native
listener with the sample sequence for "After 300 meters turn left" and observes no utterance on HEAD.

## What Changes

- Add a process-scoped `VoiceGuidance` seam. It converts the native sample identifiers into one
  localized sentence and hands it to a `VoiceOutput`.
- Add `AndroidVoiceOutput`, the production `VoiceOutput`, backed by Android `TextToSpeech` and the
  default locale.
- Wire the engine's `NavigationListener.onVoiceInstruction` to `VoiceGuidance` on the main dispatcher,
  only while a session is navigating.
- Stop the current utterance when navigation stops, and ignore a sample that arrives after the session
  ended.
- Add the spoken wording to `values/` and `values-de/`; the German catalog stays complete.
- Classify the new `voice-guidance` capability in `tools/feature-list/specs.json` so the catalogue gate
  accepts the open change.

Not in scope: a voice on/off preference, ducking or audio focus, car-specific audio routing, bundled
Marble recordings, and changing the native voice agent or its sample enum.

## Capabilities

### New Capabilities

- `voice-guidance` — turn-by-turn navigation announces the native voice sample sequence through the
  device's text-to-speech output, and stops speaking when navigation stops.

### Modified Capabilities

- none.

## Impact

- **App module**:
  - `app/src/main/java/com/naviveylin/navigation/VoiceGuidance.kt` (new): sample-to-sentence mapping and
    the session-facing seam.
  - `app/src/main/java/com/naviveylin/navigation/VoiceOutput.kt` (new): output contract plus
    `AndroidVoiceOutput`.
  - `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt`: constructor seam, the native
    listener override, and the stop hook.
  - `app/src/main/java/com/naviveylin/di/VoiceModule.kt` (new): bind `AndroidVoiceOutput`.
  - `app/src/main/res/values/strings.xml`, `values-de/strings.xml`: the spoken wording.
- **Tests**:
  - `app/src/test/java/com/naviveylin/navigation/NavigationEngineVoiceGuidanceTest.kt` (new): the two
    spec scenarios.
  - `app/src/test/java/com/naviveylin/test/EngineUnderTest.kt`: pass a no-op voice seam by default.
  - `app/src/androidTest/java/com/naviveylin/navigation/NavigationArrivalDeviceTest.kt`: pass the
    production voice seam to the engine it constructs.
- **Tooling**: `tools/feature-list/specs.json` gains the `voice-guidance` classification.
- **Guidelines**: no new rule. The code follows `guidelines/Design.md` §1 (the app owns Android
  context-bound work), §3 (the engine owns navigation policy) and §4 (native callbacks marshal to
  main); the case follows `guidelines/Build.md` §6a (default Robolectric sandbox for a class that
  builds the fake client).
- **Native/JNI**: no submodule change. The bridge already emits `int[]` sample identifiers.
- **Rollback**: remove the listener override and the stop hook; the app returns to silent navigation.
  No persisted state, no migration, no API change.
