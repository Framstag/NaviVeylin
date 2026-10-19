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
      installation → the native delete is asked twice and the map is no longer in `installedMapPaths`.
- [x] 1.3 Both fail on the base commit `7ddf7ab`: `tests="2" skipped="0" failures="2" errors="0"
      timestamp="2026-10-19T06:39:52.013Z"`, each with a plain assertion failure (the left-over directory, the
      single delete call) → `evidence/red-on-head/TEST-…MapManagerDeleteRepositoryMapTest.xml`.

## 2. The fix

- [x] 2.1 `MapManagerViewModel.deleteMapDirectory(path)`: native delete; on failure remove the directory tree
      and ask the native side once more, which succeeds on an absent directory and drops the registration;
      report whether the directory is gone.
- [x] 2.2 `MapManagerViewModel.removeDirectoryTree(directory)`: `Files.walk` + `deleteIfExists`, refused
      outside `storageManager.mapsRootDir` — the refusal is `deleteMap`'s failure, and the bound is the clause
      the guard case below falsifies.
- [x] 2.3 Both call sites use it: `deleteMap` and `applySourceSwitch`'s `unregister` lambda (the source switch
      reported a failure per repository map before).
- [x] 2.4 `FakeMapDownloadManager` takes the delete answer as a parameter (`deleteSucceeds`, default `{ true }`)
      so the new case can mirror the native rule; existing callers unchanged.
- [x] 2.5 `MapManagerDeleteRepositoryMapTest.aDirectoryOutsideTheMapsRootIsNotSwept`: a delete refused for
      `<filesDir>/maps/../outside-the-maps-root` (through `deleteMap("../outside-the-maps-root")`) → the
      outside file survives, `downloadManager.deleted` holds exactly one entry, and `uiState.error` is
      `Failed to delete map: ../outside-the-maps-root`. Not a red case: it asserts the bound §2.2 introduces,
      added after the red run, and no base-commit run of it is archived.

## 3. The verification

- [x] 3.1 Focused: `./gradlew :app:testMobileDebugUnitTest --tests "…MapManagerDeleteRepositoryMapTest"
      -PforceTests --rerun-tasks` → `tests="3" skipped="0" failures="0" errors="0"`, three consecutive forced
      runs at `2026-10-19T07:01:17.836Z`, `07:02:19.667Z` and `07:03:18.539Z` (the first one after the
      revert-check restore of §3.5) → the three XMLs in `evidence/green-on-fix/`.
      *(The first two runs of the change, before the flake fix and the guard case, were red-then-green at
      `06:39:52.013Z` and `06:44:10.325Z`; they are superseded by these.)*
- [x] 3.2 Neighbours: the `com.naviveylin.ui.mapmanager.*` slice is part of the gate run's own set (§3.3) —
      every `ui.mapmanager` suite is in `evidence/gate/` green, so no separate slice run was repeated after the
      third case was added.
- [x] 3.3 The run's gate: `./gradlew test -PforceTests --rerun-tasks` → `BUILD SUCCESSFUL in 4m 46s`,
      **704 suites, tests=5678, failures=0, errors=0, skipped=0** across `:app` mobileDebug (276) and
      automotiveDebug (276), `:auto` (85), `:core` (60) and `:osmscout-client-java` (7) →
      `evidence/gate/totals.txt`, `suite-rows.txt`, `suite-paths.txt`, `gate-xml.tar.gz`, `gate-log-tail.txt`.
      The new class is in that set in both flavours: `tests=3 failures=0` at `07:06:06.988Z` (automotive) and
      `07:07:54.541Z` (mobile, `suite-rows.txt`).
- [x] 3.4 The init refresh is joined before the case acts: `viewModel()` sets `ioDispatcher` after the
      constructor, whose own `refreshInstalledMaps()` (`MapManagerViewModel.kt:251`) runs on the default
      `Dispatchers.IO` — its `installedMapPaths` write could otherwise land after the delete's refresh (one such
      failure was seen in the first post-fix run). The two-state wait is gone; the case now joins the view
      model's launched children (`viewModelScope.coroutineContext[Job]`) and only then replaces the dispatcher.
- [x] 3.5 Revert-check on the maps-root bound in §2.2 (one mutation, `revert-check` skill):
      `MapManagerViewModel.kt:478` `startsWith(root)` → `startsWith(root.parent)` → the focused class reports
      `tests="3" failures="1"` at `2026-10-19T07:00:14.452Z`,
      `java.lang.AssertionError: a directory outside the maps root must not be swept` in
      `aDirectoryOutsideTheMapsRootIsNotSwept` (`MapManagerDeleteRepositoryMapTest.kt:125`) → restored
      (`grep -c 'root.parent'` → 0, `git diff` on the file empty) → forced green at `07:01:17.836Z` (§3.1) →
      `evidence/revert-check/TEST-…startsWith-root-parent.xml` plus `evidence/green-on-fix/`.

## 4. Archive

- [x] 4.1 `openspec validate map-download-infrastructure --strict` after the merge → one pre-existing
      long-requirement warning, no error, `EXIT=0` → `evidence/validate-after-merge.txt`.
- [x] 4.2 Archived; the delta merged into `openspec/specs/map-download-infrastructure/spec.md`.
