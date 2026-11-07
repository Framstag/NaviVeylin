# Tasks

## 1. Settings fields and their ownership

- [x] 1.1 Add the three `AppSettings` fields (`voiceGuidanceEnabled = true`,
  `autoVoiceGuidanceEnabled = true`, `announceGpsState = false`) and map the car's two through
  `AutoSettings`/`AutoSettingsMapping`. Verify: `SettingsStorageVoiceSwitchTest#freshSettingsHaveBothVoiceSwitchesOn`
  (`freshSettingsHaveBothVoiceSwitchesOn`) and `#olderSettingsDecodeToTheDefaults`
  (`olderSettingsDecodeToTheDefaults`) pass. *(spec: settings-persistence — The announcement fields
  declare per-surface and shared ownership; voice-guidance — The voice switch is per surface, persisted
  and on by default)*
- [x] 1.2 Add `SettingsStorageVoiceSwitchTest` cases `#aCarWriteLeavesThePhoneVoiceValueAlone` and
  `#theSharedGpsValueIsOneField`: a car write leaves the phone's voice value alone, and the shared GPS
  value is one field after two writes. *(spec: settings-persistence — both scenarios; voice-guidance —
  GPS announcements are one shared switch, off by default)*
- [x] 1.3 Confirm the car's own voice value needs no `null` = inherit fallback (design D5) and that the
  settings round-trip keeps unknown keys out. Verify with 1.2's cases plus
  `SettingsPersistenceTest` (existing) green. *(spec: settings-persistence — A settings file written
  before the fields existed decodes to the defaults)*

## 2. The session surface reaches the engine

- [x] 2.1 Add `origin: SurfaceOrigin = SurfaceOrigin.PHONE` to
  `com.naviveylin.core.NavigationViewModel.navigateTo`, pass it through
  `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt:148`, and pass
  `SurfaceOrigin.CAR` at the four `:auto` sites (`DetailsScreen.kt:328`, `FavoritesScreen.kt:331`,
  `NavigationSession.kt:655,681`). Verify: both flavors compile and the existing navigation suites stay
  green. *(spec: voice-guidance — The voice switch is per surface)*
- [x] 2.2 Store the origin as `sessionOrigin` on start and reset it in `stopNavigation()`. Verify with
  2.3's cases. *(spec: voice-guidance — The voice switch is per surface)*
- [x] 2.3 Add the two surface cases to `NavigationEngineVoiceGuidanceTest`:
  `#thePhoneSessionReadsThePhoneSwitch` and `#theCarSessionReadsTheCarSwitch` — one case per named
  surface. *(spec: voice-guidance — The phone's switch governs a phone session; The car's switch governs
  a car session)*

## 3. The voice gate

- [x] 3.1 Let `VoiceGuidance` report that a sample sequence carries a GPS state (mapping untouched), and
  gate `NavigationEngine.onVoiceInstruction` on the session surface's voice switch. Verify:
  `#voiceSwitchOffKeepsATurnSilent` passes, and the two shipped cases
  `#voiceSampleSequenceIsSpokenWhileNavigating` and `#voiceInstructionAfterStopIsSilent` stay green.
  *(spec: voice-guidance — Navigation speaks the native voice instruction)*
- [x] 3.2 Read the switch per instruction (`settingsStorage.load()` inside the announce coroutine, design
  D3). Verify: `#voiceSwitchChangeAppliesToTheNextInstruction` — a toggle mid-session silences the next
  instruction. *(spec: voice-guidance — A change applies to the next instruction)*

## 4. The GPS hold window

- [x] 4.1 Add `FLAP_WINDOW` (5 s) and the cancellable hold job in the engine scope: a GPS sample is
  announced only after the window, an opposite sample cancels it. Verify:
  `#gpsAnnouncementIsSilentByDefault`, `#gpsFlapInsideTheWindowAnnouncesNothing`,
  `#gpsStateThatHoldsIsAnnouncedOnce`, `#gpsIgnoresTheSurfaceVoiceSwitch` — all on virtual time, no
  wall-clock wait (`guidelines/Build.md` section 6e). *(spec: voice-guidance — GPS announcements are one shared
  switch, off by default; A GPS announcement is spoken only after the state held; The spoken GPS wording
  follows the same rule on both surfaces)*
- [x] 4.2 Cancel a pending announcement in `stopNavigation()`. Verify:
  `#stopCancelsAPendingGpsAnnouncement`. *(spec: voice-guidance — Stopping navigation cancels a pending
  announcement)*

## 5. Phone surface

- [x] 5.1 Add the two switches to `LocationOptionsOverlay` with their persistence in
  `MapCanvasViewModelSettings`, and the labels to `values/strings.xml` and `values-de/strings.xml`.
  Verify: `LocationOptionsOverlayVoiceSwitchesTest#bothSwitchesAreShownAndPersisted` and
  `GermanTranslationCompletenessTest` (gate step) pass. *(spec: voice-guidance — The phone offers both
  switches in its settings sheet)*
- [x] 5.2 Measure the sheet on the device: `:app:assembleMobileDebug`, open the options sheet during
  navigation, record the UI-dump geometry of both switches (bounds, sheet inset) at font scale 1.0, and
  the logcat trace of an instruction that is silent with the switch off. Record the numbers in the change
  (`guidelines/Build.md` sections 10 and 11). *(spec: voice-guidance — The phone offers both switches in its
  settings sheet)*

## 6. Car surface

- [x] 6.1 Add the two rows and their `KEY_` constants to `auto/PreferencesScreenMapper` (a plain flip for
  each, like lane hints) with the car string resources. Verify:
  `PreferencesScreenMapperTest#announcementRowsCarryTheirValues`,
  `#theVoiceRowFlipsOnlyTheCarValue`, `#theGpsRowFlipsTheSharedValue`,
  `#thePhoneVoiceValueHasNoCarRow`. *(spec: auto/preferences — The car offers the announcement switches)*
- [ ] 6.2 Verify on the AAOS AVD: both rows with their values, a flip that survives leaving and
  re-entering the screen, and a car navigation session whose speech follows the car's switch while the
  phone's is off. Record the observation. **Open — open-device evidence.** The AVD could not be given a
  routable database: its release-signed install was replaced with the owner's approval (the maps went
  with it), the automotive flavour has no map downloader, `MainActivity` forwards to `CarAppActivity` on
  automotive hardware in both flavours, `adb root` is refused on that production image, and the car host
  binds the session in user 10 while `run-as` addresses user 0. The chain, the executed attempt and what
  the car half rests on until a car device exists are in `evidence/car-attempt.md`. *(spec:
  auto/preferences — The car offers the announcement switches; voice-guidance — The car's switch governs
  a car session)*

## 7. Revert checks

- [x] 7.1 Mutate the gate to always read the phone's switch; `#theCarSessionReadsTheCarSwitch` must fail.
  Restore, re-run green with `-PforceTests --no-build-cache`. *(spec: voice-guidance — The car's switch
  governs a car session)*
- [x] 7.2 Mutate `FLAP_WINDOW` to 0 ms; `#gpsFlapInsideTheWindowAnnouncesNothing` must fail. Restore,
  re-run green. *(spec: voice-guidance — A GPS announcement is spoken only after the state held)*
- [x] 7.3 Mutate `announceGpsState`'s default to `true`; `#gpsAnnouncementIsSilentByDefault` must fail.
  Restore, re-run green. *(spec: voice-guidance — GPS announcements are one shared switch, off by
  default)*
- [x] 7.4 Mutate the car mapping to write the phone's voice field; `#aCarWriteLeavesThePhoneVoiceValueAlone`
  must fail. Restore, re-run green. *(spec: settings-persistence — The announcement fields declare
  per-surface and shared ownership)*

## 8. Build, catalogue and gate

- [x] 8.1 Build both flavors (`:app:assembleMobileDebug`, `:app:assembleAutomotiveDebug`) and run the
  focused suites named above. *(spec: all deltas — compile and focused tests)*
- [ ] 8.2 Run `bash tools/gen-feature-list.sh --check-classification` and the catalogue check; regenerate
  `FEATURES.md` if the `voice-guidance` capability is still absent from it (it is absent on the tree this
  change starts from). *(spec: voice-guidance — all requirements; FeatureList.md section 6)*
- [x] 8.3 Run the project gate once (`./tools/loop/gate.sh`) and record its verdict with the tallies.
  *(spec: all deltas — the gate's suites)*

## Evidence

- Focused cases, all green: `NavigationEngineVoiceGuidanceTest` (13 cases: the 2 shipped, the gate per
  surface, the next-instruction change, and the 4 GPS cases), `SettingsStorageVoiceSwitchTest` (5),
  `LocationOptionsVoiceSwitchesTest` (2), `LocationOptionsOverlayComposeTest#theVoiceAndGpsSwitchesAreShownAndReportAGesture`,
  `PreferencesScreenMapperTest` (4 new), `GermanTranslationCompletenessTest`.
- Revert checks, mutate → red, restore → `-PforceTests --no-build-cache` green:
  - gate reads the phone's switch for a car session → `theCarSessionReadsTheCarSwitch` (11 tests, 1 failed);
  - `FLAP_WINDOW_MILLIS = 0` → `gpsFlapSpeaksOnlyTheStateThatHeld` + `stopCancelsAPendingGpsAnnouncement` (2 failed);
  - `announceGpsState = true` default → `gpsAnnouncementIsSilentByDefault` + 2 settings cases (3 failed);
  - the car mapping writes the phone's voice field → `aCarWriteLeavesThePhoneVoiceValueAlone`,
    `theSharedGpsValueIsOneField`, `carMappingNeverWritesThePhoneVoiceField` (3 failed).
- Builds: `:app:assembleMobileDebug`, `:app:assembleAutomotiveDebug` — BUILD SUCCESSFUL.
- Gate: `LOOP-GATE: tests=0 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly
  --no-build-cache 360s, tools/check-compose-sandbox-rule.sh 1s,
  tools/check-compose-sandbox-rule-selftest.sh 0s)`.
- Classification: `bash tools/gen-feature-list.sh --check-classification` exit 0 — unclassified 0,
  classified 185, delta spec ids 34, stale 5 (pre-existing).
- Still open: `FEATURES.md` carries no `voice-guidance` prose — the catalogue is regenerated by a
  release run (`--release <version>` plus the phrasing step), which is outside this change; the
  classification gate this change owes is green.
- Phone measurement (5.2), recorded in `evidence/phone-measurement.md`: both switch rows with their
  measured bounds and the spec's defaults (voice on, GPS off); `Diag/VOICE` lines on the device for
  `outcome=spoken`, `outcome=suppressed reason=voice-switch-off` (after the switch was toggled
  mid-session) and `outcome=suppressed reason=gps-switch-off`; `settings.json` carries
  `"voiceGuidanceEnabled":false`. The `VOICE` tag was added in this change so the silence is
  measurable at all (`VoiceAnnouncementDiagnostics`, 4 JVM cases, coordinate gate green).

- To be recorded as the tasks complete: the focused XMLs per case, the phone UI-dump geometry, the AAOS
  observation, the four revert-check mutations and their restorations, the build logs and the gate line.
