# Design

## Context

See `proposal.md` — Why. What shapes the approach:

- The engine is one process-scoped instance bound to `com.naviveylin.core.NavigationViewModel`
  (`di/NavigationViewModelModule.kt`) and consumed by the phone's `ui/map` ViewModels and by the four
  `:auto` navigation call sites. It has no notion of a surface today; `SurfaceOrigin` exists in
  `core/NavigationState.kt:19` but is used only to attribute failures and errors
  (`navigation/NavigationFailureMapping.kt`).
- The engine already owns navigation policy (`guidelines/Design.md` section 3), already receives the native
  callbacks marshalled to the main dispatcher (section 4), and already reads `SettingsStorage` per operation
  rather than watching it (`routing-max-speed`: "Read, not watched").
- `SettingsStorage.load()` never throws: `readFromDisk()` catches a decode failure, logs, and returns
  `AppSettings()` defaults. A gate that reads per instruction therefore degrades to the defaults, not
  to a crash.
- `EngineTimeSource` and a `CoroutineScope(SupervisorJob() + Dispatchers.Main + engineFaultHandler)` are
  both already injected/driven by `EngineUnderTest` and the existing engine cases, so a hold window can
  be tested on virtual time without new test infrastructure. `EngineUnderTest` already passes a
  `SettingsStorage(context)`.
- The native agent emits GPS samples on edges only and delays `GpsLost` by 10 s
  (`libosmscout/.../VoiceInstructionAgent.cpp:387-417`); the app-side window is a second, shorter filter,
  not a replacement.

## Goals / Non-Goals

**Goals:**

- One decision per instruction, taken where the policy already lives: the engine.
- The phone's and the car's voice switches stay independent, including when both surfaces are live.
- The GPS switch is one value and its hold window is deterministic in unit tests.

**Non-Goals (design level):**

- No audio focus, ducking, volume shaping or `TextToSpeech` lifecycle change.
- No settings watch/StateFlow: nothing re-reads settings between instructions.
- No change to `VoiceGuidance`'s sample-to-sentence mapping or to the native bridge.

## Decisions

### D1 — The surface travels into the engine as a session origin

`com.naviveylin.core.NavigationViewModel.navigateTo` gains
`origin: SurfaceOrigin = SurfaceOrigin.PHONE`; the delegate in `navigation/NavigationViewModel.kt:148`
passes it through, and the four `:auto` sites pass `SurfaceOrigin.CAR`
(`DetailsScreen.kt:328`, `FavoritesScreen.kt:331`, `NavigationSession.kt:655,681`). The engine stores it
in a `sessionOrigin` field on start and resets it on `stopNavigation()`.

Alternatives eliminated:

- **Ask `CarSessionPresence.active` when speaking.** No API change, but wrong in the case this project
  already has a spec for (`guidelines/MapRendering.md` section 18): a phone navigation while a car session is merely
  active would read the car's switch, and the car browsing the map would flip the phone's speech.
- **Move speech into each surface's presenter.** Puts navigation policy into `:auto` and duplicates the
  stop hook; contradicts `guidelines/Design.md` section 3 and splits the one place that must stop an utterance
  with its session.

Risk: a caller that forgets the parameter silently keeps the phone's switch. Mitigation: the parameter
defaults to `PHONE` (today's only behaviour), all four `:auto` sites are enumerated in the tasks, and
one car case asserts the car's value is the one read (`#theCarSwitchIsTheOneACarSessionReads`).

### D2 — The gate sits in the engine, not in the voice seam or the output

`NavigationEngine.onVoiceInstruction` classifies the sample sequence (a `VoiceGuidance` helper reports
whether a sequence is a GPS sample) and then either skips, holds, or announces.

Alternatives eliminated:

- **A predicate inside `VoiceGuidance`.** The seam would need the session origin and the settings, i.e.
  the engine's policy state, and its unit cases would have to fabricate a surface.
- **The settings inside `AndroidVoiceOutput`.** The output is the platform adapter; teaching it policy
  makes a silent-by-configuration device indistinguishable from a broken one and hides the decision from
  the engine's tests.

### D3 — The switches are read per instruction, not per session

The gate suspends on `settingsStorage.load()` inside the already-main-dispatched announce coroutine; a
toggle therefore applies to the next instruction.

Alternatives eliminated:

- **Resolve at session start.** Cheaper (one read per session), but a driver who silences the app mid-route
  would keep hearing it until the next session — the exact complaint this change answers.
- **A watched `StateFlow<AppSettings>`.** Adds a long-lived collector and a settings-cache contract the
  storage layer does not have today, for a value that changes a handful of times per drive.

Trade-off: one small JSON read per instruction. Instructions arrive per distance band (seconds to minutes
apart), the read runs on the storage's IO dispatcher, and it already takes the write mutex — so the cost
is bounded and serialized with no new contention. If that ever measures badly, D3 is the single place to
change.

### D4 — The hold window is a cancellable `delay` job in the engine scope

A GPS sample starts `gpsHoldJob = scope.launch { delay(FLAP_WINDOW); announce(...) }`; an opposite GPS
sample or `stopNavigation()` cancels it. `FLAP_WINDOW` is a named engine constant (5 s).

Alternatives eliminated:

- **Compare `timeSource.nowMillis()` against the last GPS sample.** Deterministic without virtual time,
  but it only tells the truth when something else ticks; a state that holds would wait for the next fix
  rather than the window, which is a different behaviour and not what the spec states.
- **Patch the native agent to add hysteresis.** The submodule would carry an app-policy constant, needs an
  upstreamable patch plus a pin bump, and changes behaviour for every libosmscout consumer.
- **Deduplicate by remembering the last spoken state.** Suppresses repeats but not a flap: lost/found/lost
  are three distinct states.

`runTest` advances virtual time, so `#gpsFlapInsideTheWindowAnnouncesNothing` and
`#gpsStateThatHoldsIsAnnouncedOnce` are ordinary engine cases with no wall-clock wait
(`guidelines/Build.md` section 6e).

### D5 — Three flat `AppSettings` fields, `auto*` prefix for the car's own

`voiceGuidanceEnabled = true`, `autoVoiceGuidanceEnabled = true`, `announceGpsState = false`; the car's
two are mapped through `AutoSettings`/`AutoSettingsMapping` beside `autoRoutingAnchorId`.

Alternatives eliminated:

- **A nested `Announcements(phoneVoice, carVoice, gps)` object.** Cleaner to read, but a new nested shape
  has no precedent in this file and every existing field is flat and additive.
- **A `Map<String, Boolean>` keyed by surface.** Loses the compile-time default and makes the settings file
  self-describing only by convention.
- **Per-surface GPS pair.** The owner decided the GPS switch is one value (`proposal.md`).

Unlike `autoRoutingAnchorId`, the car field has no `null` = "inherit" semantics: the car's voice switch is
its own value from the first start and defaults to on, so no fallback resolution is needed.

### D6 — UI placement follows each surface's existing settings home

Phone: two switches in `LocationOptionsOverlay` (the sheet `lane-guidance`'s "Lane hints toggle" already
uses), persisted through `MapCanvasViewModelSettings`. Car: two rows in
`auto/PreferencesScreenMapper`, one opening no picker (a plain flip like lane hints), each with its own
`KEY_` constant.

Alternatives eliminated: a phone-only settings screen (none exists; the sheet is where map and navigation
switches live) and a car push-picker for a boolean (the list template flips booleans in place, as lane
hints does).

## Risks / Trade-offs

- [A `:core` interface signature changes] → One defaulted parameter; every existing caller keeps compiling,
  and the four car sites are a closed list. No persisted data is involved.
- [Per-instruction settings read] → Bounded, on the IO dispatcher, already mutex-serialized; D3 names the
  alternative if it measures badly.
- [A settings decode failure reads defaults (voice on, GPS off)] → Same behaviour as a fresh install and
  the storage's documented fallback; the failure is already logged by `SettingsStorage`.
- [The GPS window delays an announcement by up to `FLAP_WINDOW`] → The native lost-state is already delayed
  10 s, so the window shifts the announcement later without swallowing a real loss.
- [Two switches in a sheet that is already dense] → The sheet scrolls and groups navigation switches
  together; the phone measurement task records the geometry of both switches at font scale 1.0.

## Migration Plan

Additive only. Settings files without the fields decode to `true`/`true`/`false`. No migration step, no
version bump for the settings schema. Rollback: delete the fields, the gate and the two UI additions; a
settings file that still carries the keys decodes into a build that ignores them, because unknown keys are
dropped by the serializer's `ignoreUnknownKeys` setting.

## Verification

Unit and JVM:

- `NavigationEngineVoiceGuidanceTest` — the gate per surface, the switch change applying to the next
  instruction, the three hold-window cases, GPS independent of the surface voice switch (all with
  `EngineUnderTest`'s fake output and virtual time).
- `SettingsStorageVoiceSwitchTest` — ownership of the three fields, older-file defaults.
- `PreferencesScreenMapperTest` — the two car rows, the value each flips, the phone value absent.
- `LocationOptionsOverlayVoiceSwitchesTest` — both switches shown and persisted.

On device (`guidelines/Build.md` sections 10 and 11):

- Phone: `:app:assembleMobileDebug`, then the options sheet — a UI-dump geometry measurement of the two
  switches (bounds, and the sheet's inset change when the rows are added), plus a logcat trace that a
  navigation instruction is silent with the switch off.
- Car: AAOS AVD, the Preferences screen — both rows with values, a flip that survives re-entry, and a
  navigation session whose speech follows the car's switch, not the phone's.
