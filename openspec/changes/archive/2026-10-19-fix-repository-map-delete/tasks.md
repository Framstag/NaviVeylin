# fix-repository-map-delete — tasks

Spec requirement the defect contradicts: `openspec/specs/map-download-infrastructure/spec.md` — **"Delete
removes map manager registration"** (its scenario "Deleted map removed from lookup set") and **"Clean
redownload"**. The delta adds the requirement the tree does not satisfy today, "Deleting an installation
removes the whole directory", with one scenario per new case.

## 1. The red case (before the fix)

- [x] 1.1 `MapManagerDeleteRepositoryMapTest.deletingARepositoryInstallationRemovesItsDirectory`: an installed
      directory holding `db.json`, `ptroutes.dat` and `.source.json`, a manager whose delete answer mirrors the
      native rule, then `viewModel.deleteMap(...)` → the directory is gone and no banner is set.
- [x] 1.2 `MapManagerDeleteRepositoryMapTest.theDeleteIsRepeatedSoTheMapManagerDropsTheRegistration`: the same
      installation → the manager is asked twice and the map is no longer in `installedMapPaths`.
- [x] 1.3 Both fail on the base commit `7ddf7ab`: `tests="2" skipped="0" failures="2" errors="0"
      timestamp="2026-10-19T06:39:52.013Z"`, each with a plain assertion failure (the left-over directory, the
      single delete call) → `evidence/red-on-head/TEST-…MapManagerDeleteRepositoryMapTest.xml`.

## 2. The fix

- [x] 2.1 `MapManagerViewModel.deleteMapDirectory(path)`: native delete; on failure remove the directory tree
      and ask the native side once more, which succeeds on an absent directory and drops the registration;
      report whether the directory is gone.
- [x] 2.2 `MapManagerViewModel.removeDirectoryTree(directory)`: `Files.walk` + `deleteIfExists`, refused
      outside `storageManager.mapsRootDir`.
- [x] 2.3 Both call sites use it: `deleteMap` and `applySourceSwitch`'s `unregister` lambda (the source switch
      reported a failure per repository map before).
- [x] 2.4 `FakeMapDownloadManager` takes the delete answer as a parameter (`deleteSucceeds`, default `{ true }`)
      so the new case can mirror the native rule; existing callers unchanged.

## 3. The verification

- [x] 3.1 Focused: `./gradlew :app:testMobileDebugUnitTest --tests "…MapManagerDeleteRepositoryMapTest"
      -PforceTests` → `tests="2" skipped="0" failures="0" errors="0" timestamp="2026-10-19T06:44:10.325Z"`,
      reproduced three times with `--rerun-tasks` →
      `evidence/green-on-fix/TEST-…MapManagerDeleteRepositoryMapTest.xml`.
- [x] 3.2 Neighbours: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.mapmanager.*"
      -PforceTests --rerun-tasks` → `BUILD SUCCESSFUL in 1m 14s` (no assertion failures; the slice's XMLs are
      part of the gate run's set, `evidence/gate/`).
- [x] 3.3 The run's gate: `./gradlew test -PforceTests --rerun-tasks` → `BUILD SUCCESSFUL in 4m 49s`,
      **704 suites, tests=5676, failures=0, errors=0, skipped=0** across `:app` mobileDebug (276) and
      automotiveDebug (276), `:auto` (85), `:core` (60) and `:osmscout-client-java` (7) →
      `evidence/gate/totals.txt`, `suite-rows.txt`, `suite-paths.txt`, `gate-xml.tar.gz`, `gate-log-tail.txt`.
- [x] 3.4 The behaviour the delta's second scenario names is asserted by the same run: the installed-list state
      the case reads is written by `refreshInstalledMaps` on the test dispatcher, so no wall-clock wait and no
      revert-check beyond §1.3/§3.1 — the red case is the falsifier of the fix and the fix is the falsifier of
      the red case.

## 4. Archive

- [x] 4.1 `openspec validate --change fix-repository-map-delete` clean.
- [x] 4.2 Archived; the delta merged into `openspec/specs/map-download-infrastructure/spec.md`.
