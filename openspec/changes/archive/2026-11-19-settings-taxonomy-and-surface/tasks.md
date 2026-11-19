# Tasks — settings taxonomy and surface

Each group lands the tests and the documentation its own work calls for. Verification names the
command or the observable behaviour, with a case name where one exists.

This is the remainder of the change. Group 1 landed on 2026-11-14 and its slice is the archive
`2026-11-14-settings-taxonomy-and-surface` — that archive holds the group-1 evidence and applied the
change's spec deltas to `openspec/specs/settings-taxonomy/` and `openspec/specs/settings-surface/`, so
the corpus already states the whole target and a task below only lands it. Group 2 landed on
2026-11-15 carried by the tracker item #899 (commit `905d1dcc`, merged as `2017553d`); its boxes are
ticked here without a second archive, and the cases they name are green on that commit. Groups 3 to 8
are open, one tracker item each (#900–#905).

## 1. Foundations — the row model and the single writer

- [x] 1.1 Add `SettingsController` (`@Singleton`, wrapping `SettingsStorage`) exposing
      `StateFlow<AppSettings>`, a per-setting `update(transform)` and `surfaceState`; verify with
      a unit test that a write reaches `SettingsStorage` and that the flow emits the new value
      once (`SettingsControllerTest#aWriteLandsOnceAndEmits`).
- [x] 1.2 Migrate `MapCanvasViewModel`'s settings writes onto `SettingsController` and delete its
      direct `SettingsStorage` write path; verify the existing view-model settings cases still
      pass and that no reference to `SettingsStorage` remains in `MapCanvasViewModel*`.
- [x] 1.3 Add the `SettingSpec` model (`SettingsTopic`, `SettingsSection`, `SettingScope`,
      `SettingControl`, control payloads) with a table harness; verify a test enumerates the
      registry and asserts every key appears exactly once
      (`SettingsRegistryTest#everyKeyAppearsExactlyOnce`).
- [x] 1.4 Add a scope assertion over the registry: no car surface row is phone-only, no phone
      topic row is car-only, and every `CAR` row carries a car content description; verify
      `SettingsRegistryTest#theCarOffersOnlySharedAndCarRows` and
      `SettingsRegistryTest#everySurfaceSpecificRowIsMarked`.
- [x] 1.5 Add the empty topic tables (Karte, Navigation & Route, Hinweise & Sprache, Karten &
      Daten, Erweitert, Über NaviVeylin) with a root-order assertion; verify
      `SettingsRegistryTest#theRootHoldsSixTopicsInOrder`.

## 2. The surface — rendering, hosts, dismissal

- [x] 2.1 Render the root and a topic page from the registry: one scrolling column, section
      titles, `supportingContent` help line, surface icon for scope-marked rows, chevron for link
      rows; verify `SettingsSurfaceTest#theRootShowsSixTopicsAndNoSettingRow`,
      `#aTopicPageShowsSections`, `#aShortHelpLineSitsUnderTheLabel` and
      `#aSharedRowCarriesNoSurfaceIcon`.
- [x] 2.2 Host the surface in `MapCanvasScreen`'s modal band as a full-height overlay above the
      map chrome, sized without changing the map composable's size; verify the map keeps
      producing frames and follow mode stays engaged while it is open
      (`SettingsSurfaceTest#theMapKeepsRenderingWhileTheSurfaceIsOpen`) and that dismissing it
      leaves camera, mode and follow untouched (`#dismissingReturnsTheUntouchedMap`).
- [x] 2.3 Add the three entry points: `MapMenu` "Settings" entry between Search and About, an
      entry in `ui/MainScreen.kt`'s menu, and the quick sheet's last row; opening the surface
      closes the sheet; verify `SettingsSurfaceTest#theMapMenuOpensTheRoot`,
      `#theStartScreenMenuOpensTheRoot` and `#theSheetRowClosesTheSheetAndOpensTheSurface`.
- [x] 2.4 Add `BackHandler` for level-by-level dismissal and keep the existing topmost-overlay
      order intact; verify `SettingsSurfaceBackTest#backLeavesTheTopicThenTheSurface` and that
      back on the base map still follows default behaviour.
- [x] 2.5 Add the conditional-row and link-row renderers (ambient sensitivity only when dark mode
      is Automatic; Karten & Daten opens `MapManagerScreen`; About opens the existing dialog);
      verify `SettingsSurfaceTest#anInapplicableSectionIsOmitted`,
      `#theMapManagerRowOpensTheMapManager` and `#theAboutRowOpensTheDialog`.
- [x] 2.6 Document the new surface in `guidelines/UIPhoneMap.md` sections 7/8 (the gear opens the quick
      sheet, the settings surface is an overlay, its entry points) and in `guidelines/UIPhone.md`
      (phone surfaces); verify every path and case name the new text names exists.

## 3. Karte topic — presentation and handling

- [ ] 3.1 Move map style and the POI-symbol switch into the registry and the surface, style as a
      `SELECT` sub-surface listing the available styles with the current one marked; verify
      `SettingsSurfaceTest#theStyleRowOpensItsSelectionSurface` and that
      `MapCanvasViewModelStyleTest` cases still pass.
- [ ] 3.2 Move dark mode and the ambient-light sensitivity into the topic as a `CHOICE` of
      Off/On/Automatic with the sensitivity row shown only for Automatic; verify
      `SettingsSurfaceTest#theDarkModeRowListsThreeValues` and that the `dark-mode` cases still
      pass unchanged.
- [ ] 3.3 Move orientation and auto-zoom into the topic (both stay live in the quick sheet);
      verify the three-editor sync case `SettingsSurfaceTest#orientationStaysInSyncAcrossTheSheet`,
      the compass long press and the surface, and the existing orientation cases.
- [ ] 3.4 Move keep screen on, the long-press slider and render mode into the topic's
      "Darstellung"/"Bedienung" sections, moving `LocationOptionsOverlayComposeTest`'s long-press
      cases to `SettingsSurfaceTest` and deleting the old ones in the same task; verify
      `SettingsSurfaceTest#longPressTimeoutSliderShowsAndWritesTheValue` and
      `SettingsSurfaceTest#keepScreenOnToggleReflectsItsState`.
- [ ] 3.5 Add the slider and switch row behaviour rules (whole-row toggle, labelled endpoints,
      live apply, no save action); verify `SettingsSurfaceTest#aSwitchTogglesFromTheWholeRow` and
      `#aSliderShowsBothEndpoints` and that a change survives a restart
      (`#aChangePersistsWithoutASaveAction`).

## 4. Navigation & Route topic — speeds, avoidances, anchors

- [ ] 4.1 Move the three maximum speed sliders into the topic beside the avoidance switches,
      keeping their ranges and defaults; verify the range case for all three vehicles and that a
      change persists (`SettingsSurfaceTest#theThreeSpeedSlidersKeepTheirRanges`,
      `#aSliderChangePersistsAndSurvivesRestart`), and delete the moved sheet cases.
- [ ] 4.2 Move the three avoidance switches into the same section and assert they still feed the
      shared routing-profile builder; verify `RoutingProfileBuilderTest` cases stay green and
      `SettingsSurfaceTest#theAvoidanceSwitchesSitBesideTheSpeeds`.
- [ ] 4.3 Add the phone's anchor rows for routing and free driving with the 5×3 picker unchanged,
      moving the anchor cases off `LocationOptionsOverlayComposeTest`; verify
      `SettingsSurfaceTest#theAnchorPickerMarksTheConfiguredValue` and
      `#theAnchorLabelsMatchTheCarPicker`.
- [ ] 4.4 Add the car's own anchor rows with the tri-state ("uses the phone value" / own value /
      return to phone value) over `autoRoutingAnchorId` and `autoFreeDrivingAnchorId`; verify
      `SettingsSurfaceCarAnchorTest#anInheritingAnchorIsShownAsInheriting`,
      `#returningToThePhoneValueClearsTheCarValue` and
      `#changingThePhoneAnchorChangesAnInheritingCarAnchor`.
- [ ] 4.5 Assert the semantic that `null` still means inherit after a phone edit and after a
      reset, and that a set car value survives a phone anchor change; verify
      `SettingsSurfaceCarAnchorTest#aSetCarValueSurvivesAPhoneAnchorChange`.

## 5. Hinweise & Sprache topic — announcements

- [x] 5.1 Move the phone's voice switch and the GPS-announcement switch onto the surface; verify
      `SettingsSurfaceVoiceSwitchesTest#bothSwitchesAreShownAndPersisted` and that the existing
      `NavigationEngineVoiceGuidanceTest` cases still pass.
- [x] 5.2 Add the car's voice switch as a phone row marked for Android Auto, writing only the car
      value; verify `SettingsSurfaceCarVoiceRowTest#theCarRowWritesOnlyTheCarValue` and
      `NavigationEngineVoiceGuidanceTest#theCarSessionReadsAValueSetOnThePhone`.
- [x] 5.3 Add the lane-hints switch and the overspeed warning slider (0–30 km/h, 1 km/h, default
      5) to the topic, moving the overspeed cases off the sheet; verify
      `SettingsSurfaceTest#theOverspeedSliderOffersOneKilometreSteps` and that the speed widget's
      warning case still passes.
- [x] 5.4 Add the long-help layer for the rows that have one (short text first, long text on a
      sheet); verify `SettingsSurfaceTest#aLongHelpOpensWithoutChangingTheValue`.

## 6. The quick sheet shrinks

- [ ] 6.1 Reduce `LocationOptionsSheetContent` to the mode-live group plus the entry row and
      collapse its parameter list to a state object and one `onOpenSettings` callback; verify
      `LocationOptionsSheetTest#theSheetHoldsOnlyAutoZoomOrientationAndTheEntryRow` and
      `#browseShowsOrientationOnly`.
- [ ] 6.2 Update the three call sites in `MapCanvasScreenChromeBand.kt` (lines 151, 331, 545) to
      the collapsed signature and verify the build compiles and the three per-mode sheet cases
      pass.
- [ ] 6.3 Delete the moved control cases from `LocationOptionsOverlayComposeTest` and keep its
      button, sheet-open, orientation, auto-zoom, mode-header and navigation-reachability cases;
      verify the class compiles and its remaining cases pass.
- [ ] 6.4 Update `guidelines/UI.md` §9 (the on-map settings sheet: the location-options-only rule
      becomes a scope rule over the registry, and where the dark-mode control lives); verify
      `tools/check-doc-routes.sh` reports no unpriced or mispriced row. `UI.md` carries no §8 — its
      sections are 1, 6, 8d, 9, 10 and 20, and §8d is cross-variant map presentation.

## 7. Android Auto — grouped sub-screens

- [ ] 7.1 Turn `PreferencesScreen` into a topic list built from the registry's car-visible rows
      and add one topic `ListTemplate` per topic; verify
      `PreferencesScreenMapperTest#theRootListsTopicsAndNoSettingRow` and
      `#aTopicListsItsCarUsableRows`.
- [ ] 7.2 Replace tap-to-cycle for finite value sets with an options `ListTemplate` that marks
      the current value, reusing the anchor picker's pattern; booleans keep tap-to-toggle; verify
      `PreferencesScreenMapperTest#aChoiceRowOpensItsOptions` and that the two announcement rows
      still flip their own values (`#theVoiceRowFlipsOnlyTheCarValue`,
      `#theGpsRowFlipsTheSharedValue`).
- [ ] 7.3 Keep the no-wedge guarantees for the new screens: content replaces the placeholder, a
      dropped refresh recovers, a load failure offers retry, and a return re-reads the settings;
      verify the existing `Settings screens never wedge` cases still pass with the extra level.
- [ ] 7.4 Document the grouped preferences in `guidelines/UICar.md` §3/§3c; verify every path and
      case name the new text names exists.

## 8. Integration check

- [ ] 8.1 Run the gate over the changed modules and both flavors' unit suites; verify the run is
      green and record its evidence (`guidelines/BuildGate.md` §2a, `BuildEvidence.md` §4a).
- [ ] 8.2 Check the surface on a device in portrait, landscape and a 900 dp wide window: open,
      change one value per topic, dismiss during navigation, and confirm the map kept rendering
      (`guidelines/BuildNative.md` §10g/§10b recipes); record the observation.

## Workflow follow-up

- Archive the change (`openspec archive settings-taxonomy-and-surface`) once every box is ticked
  and the independent review the loop requires for a defect-shaped item has happened.
- File the Map Manager rework and the wide two-pane settings layout as separate backlog items;
  this change only links to the first and defers the second (`#726`).
