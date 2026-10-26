# Tasks — fix-late-dark-mode-settings-restore

## 1. Red test (before any production code)

- [x] 1.1 Add `app/src/test/java/com/naviveylin/ui/map/DarkModeLateRestoreTest.kt` with both scenarios of the new
      requirement; the start-up read is held on a `TestCoroutineScheduler` of its own, so the case decides when
      it completes. Verify: `./gradlew --offline :app:testMobileDebugUnitTest --tests
      "com.naviveylin.ui.map.DarkModeLateRestoreTest" -PforceTests -PjvmTestsOnly` → `tests="2" failures="1"`,
      failing on `the persisted Automatic must not replace the preference the user just set`
      (`evidence/red-on-head.xml`).
- [x] 1.2 Record the XML in `evidence/` and in the run's item directory.

## 2. Production guard

- [x] 2.1 `MapCanvasViewModel.kt` — `darkModePreferenceSetByUser`, `ambientSensitivitySetByUser`, documented like
      `preferSymbolSetByUser`. Verify: compiles in 2.4.
- [x] 2.2 `MapCanvasViewModelSettings.kt` — `onSetDarkModePreference` / `onSetAmbientLightSensitivity` set their
      flag; `wirePersistedSettings()` restores the controller field and the `uiState` field only while the flag is
      unset. Verify: the red case passes, no other case of the class regresses.

## 3. Test-side determinism of the affected class

- [x] 3.1 `MapCanvasViewModelDarkModeTest.kt` — one `SettingsStorage` with `ioDispatcher` on the test's
      scheduler, a seeded `maps/settings.json` per case in `setUp`. Verify: the class passes on three consecutive
      forced runs, mobile flavour, and once in the automotive flavour
      (`./gradlew --offline :app:testAutomotiveDebugUnitTest --tests
      "com.naviveylin.ui.map.*DarkModeTest" -PforceTests -PjvmTestsOnly`) — `evidence/green-cases.log`.

## 4. Change artifacts and gate

- [x] 4.1 `openspec validate fix-late-dark-mode-settings-restore --strict` → passes.
- [x] 4.2 The gate once, from the change root: `./tools/loop/gate.sh`; its XMLs are copied into `evidence/`
      (`evidence/gate-summary.xml`, `evidence/gate.log`). This change touches `app/src/main` (compiled) and
      `app/src/test`, so the compiled gate is owed; the `openspec/**` pathspec adds
      `tools/gen-feature-list.sh --check-classification`, `tools/check-doc-routes.sh` and
      `tools/check-openspec-hygiene.sh`, which the same gate runs.
- [x] 4.3 `bash tools/loop/evidence-check.sh openspec/changes/fix-late-dark-mode-settings-restore --root .` →
      no dangling pointer.
- [x] 4.4 Archive the change and confirm the delta merged into `openspec/specs/dark-mode/spec.md`.
