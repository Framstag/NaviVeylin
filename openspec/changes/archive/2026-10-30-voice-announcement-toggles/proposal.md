# Proposal

## Why

Voice guidance shipped unconditional (change `2026-10-27-add-voice-guidance`, commit `befe40f9`,
spec `voice-guidance`). Its design deliberately deferred the switch that makes speech acceptable:
"A user-facing on/off preference. Rejected for this delta: the item asks for the missing capability,
not a settings surface. A preference can be added later on top of this seam"
(`openspec/changes/archive/2026-10-27-add-voice-guidance/design.md`). Two consequences were never
decided by the owner:

- the engine is one process-scoped instance both variants bind (`di/NavigationViewModelModule.kt`,
  `NavigationEngine`), so a car navigation session speaks through the same `TextToSpeech` — although
  that proposal declared "car-specific audio routing" out of scope, while the capability is
  classified `"surfaces": ["phone", "car"]` in `tools/feature-list/specs.json:132`;
- the same seam speaks the native GPS samples (`GpsFound`/`GpsLost`). The native agent emits them on
  edges only and already holds `GpsLost` for 10 s
  (`app/src/main/cpp/libosmscout/libosmscout/src/osmscout/navigation/VoiceInstructionAgent.cpp:387-417`),
  so a weak-signal flap — lost, 10 s, found, 10 s, lost — still speaks on every genuine edge.

The owner's decision, recorded in this change: **on/off control for both surfaces**, one switch for
voice per surface (both default **on**), one shared switch for GPS announcements (default **off**), and
a hold window so an enabled GPS announcement speaks only after the state held.

## What Changes

- Three additive `AppSettings` fields, all with defaults, so a settings file written before the change
  decodes to today's behaviour:
  - `voiceGuidanceEnabled = true` — speech for navigation on the **phone**;
  - `autoVoiceGuidanceEnabled = true` — speech for navigation on the **car** (mapped through
    `AutoSettings`);
  - `announceGpsState = false` — the **shared** GPS-announcement switch, edited on either surface.
- The navigation engine learns which surface owns the session: `navigateTo(...)` on the `:core`
  `NavigationViewModel` interface gains a `SurfaceOrigin` (default `PHONE`), the four `:auto` call sites
  pass `CAR`. The engine remembers it for the session and clears it on stop.
- Speech is gated per instruction: the voice switch of the session's surface is read when an
  instruction arrives, so a change applies to the next instruction rather than the next session.
- A GPS announcement is spoken only when the state survived a hold window (`FLAP_WINDOW`, 5 s); an
  opposite GPS sample or `stopNavigation()` cancels the pending announcement.
- Phone surface: two switches in the settings bottom sheet (`LocationOptionsOverlay`) — voice guidance
  and GPS announcements.
- Car surface: two rows in the car preferences screen (`auto/PreferencesScreenMapper`), one editing the
  car voice switch and one editing the shared GPS switch.
- Feature-list classification: unchanged capability keys; `voice-guidance` already classifies as
  user-visible on phone and car.

Not in scope: audio focus or ducking (speech still plays over other audio), releasing the
`TextToSpeech` engine, the app-locale-versus-`Locale.getDefault()` mismatch of the spoken voice,
bundled voice recordings, per-distance announcement thresholds, and any change to the native voice
agent.

## Capabilities

### New Capabilities

- none.

### Modified Capabilities

- `voice-guidance`: the speaking requirement becomes conditional on the session surface's switch; new
  requirements for the per-surface switch (persisted, default on, phone and car) and for the shared
  GPS-announcement switch with its hold window (persisted, default off).
- `auto/preferences`: the car preferences screen offers the car voice switch and the shared GPS switch,
  and reflects each value.
- `settings-persistence`: states the ownership of the three new fields — the two voice switches are
  per-surface and a write on one surface never changes the other's value; the GPS switch is shared, so
  a write from either surface is the same field.

## Impact

- **`:core`**: `NavigationViewModel.navigateTo` gains the `origin` parameter
  (`core/src/main/java/com/naviveylin/core/NavigationViewModel.kt`). `SurfaceOrigin` already exists
  (`core/src/main/java/com/naviveylin/core/NavigationState.kt:19`).
- **`:app`**:
  - `data/SettingsStorage.kt` — the three `AppSettings` fields;
  - `data/AutoSettingsMapping.kt` — the two car-editable mappings;
  - `navigation/NavigationEngine.kt` — session origin, the per-instruction gate, the GPS hold window;
  - `navigation/NavigationViewModel.kt:148` — the delegate's `origin` pass-through;
  - `ui/map/LocationOptionsOverlay.kt`, `ui/map/MapCanvasViewModelSettings.kt` — the two phone switches
    and their persistence;
  - `res/values/strings.xml`, `res/values-de/strings.xml` — the two switch labels;
  - `di/` — no new module; the engine keeps its existing constructor seams.
- **`:auto`**: `auto/src/main/java/com/naviveylin/auto/PreferencesScreenMapper.kt` (two rows and their
  toggle keys), `DetailsScreen.kt:328`, `FavoritesScreen.kt:331`, `NavigationSession.kt:655,681` (pass
  `SurfaceOrigin.CAR`), car string resources.
- **Tests**: `app/src/test/java/com/naviveylin/navigation/NavigationEngineVoiceGuidanceTest.kt` (gate,
  surface selection, hold window), settings round-trip and mapping cases, `auto/PreferencesScreenMapper`
  row cases.
- **Guidelines**: `Design.md` section 3 (the engine owns navigation policy), section 4 (native callbacks marshal to
  main; the hold window lives in the engine's scope), section 9 (settings persistence and per-surface
  ownership), `UI.md` sections 7 and 8 (phone settings bottom sheet) and sections 2 and 3 (car template rows). No guideline
  change is required by this delta; the change reads them.
- **Native/JNI**: no submodule change and no bridge override. The sample sequence, its edge-triggered
  GPS emission and its 10 s native hold are consumed as they are.
- **Rollback**: delete the three fields and the two gates; the engine returns to speaking unconditionally
  and to ignoring the GPS samples. No persisted data needs migration — the fields are additive, and a
  settings file containing them decodes into a build that does not read them.
- **Scope**: both surfaces. Phone is the default origin, so a session started by any caller that does not
  name a surface keeps today's phone behaviour.
