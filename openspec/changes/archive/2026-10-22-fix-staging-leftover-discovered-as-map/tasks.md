# fix-staging-leftover-discovered-as-map — tasks

Spec requirement the defect contradicts: `openspec/specs/map-updates/spec.md` — **"Data left by an interrupted
update is recognised, not mistaken for a map"** (its general clause; the check half is satisfied, the
discovery half was not). The delta adds **"Staging data is never opened or listed as a map"**, one scenario per
case.

## 1. The red cases (before the fix)

- [x] 1.1 `StartMapResolverTest.a leftover staging directory is never the start map`: a maps tree with
      `nordrhein-westfalen` and `.nordrhein-westfalen.backup` (both with a `types.dat`, the leftover sorting
      first) → `resolve()` returns the installation.
- [x] 1.2 `MapManagerSourceLabelTest.leftoverStagingIsNotAnInstalledMap`: `.berlin.backup` beside `berlin` in
      the list the native scan reports → `installedMapPaths` excludes the leftover, `availableEntries` has no
      row starting with `.`, and `berlin` stays installed.
- [x] 1.3 Both fail on the base commit `9aac52b5d9778a6d5cc2a92cb95a35dc9c7eb00e`:
      `StartMapResolverTest` `tests="9" skipped="0" failures="1"`, `ComparisonFailure
      expected:<…/maps/[nordrhein-westfalen]> but was:<…/maps/[.nordrhein-westfalen.backup]>`;
      `MapManagerSourceLabelTest` `tests="6" skipped="0" failures="1"`, `AssertionError: the leftover is not
      an installed map`; both at `timestamp="2026-10-22T12:18:16.786Z"` →
      `evidence/red-on-head/` and `.pi/issue-loop/items/116/TEST-*.xml`.

## 2. The fix

- [x] 2.1 `core/…/mapsource/MapTreeDiscovery.kt`: `Found(installed, staging)`, `under(mapsRoot,
      excludeDirectory, report)` over the unchanged `InstalledMaps.findDatabaseDirectories`, split with
      `SlotInstall.isStaging`; one report line per leftover through the default `DiagnosticsLog` sink;
      `installedOnly` for a list someone else scanned.
- [x] 2.2 The five shared-discovery consumers read `.installed`: `StartMapResolver`, `MapDataRegistrar`,
      `AutoServiceModule`'s warmup, `MapCanvasViewModel.initMap`, `firstInstalledAutoMapBbox`.
- [x] 2.3 `MapManagerViewModel.refreshInstalledMaps` classifies the native installed list with
      `MapTreeDiscovery.installedOnly` — the phantom row is the symptom the issue names first.
- [x] 2.4 `MapUpdates` is left alone: its discovered set is what `MapUpdateCheck` splits, and the check's own
      report line is the one the shipped requirement promises for it.

## 3. The verification

- [x] 3.1 New cases: `MapTreeDiscoveryTest` — the split (nested, `.backup`, `.incoming`, plain dot), the
      report line, the basemap exclusion, the native list — `tests="4" skipped="0" failures="0" errors="0"` at
      `2026-10-22T12:20:56.588Z` → `evidence/green/`. Not red-first: the class is the seam §2.1 introduces, and
      no base-commit run of it exists.
- [x] 3.2 Focused: `./gradlew :app:testMobileDebugUnitTest --tests "…StartMapResolverTest" --tests
      "…MapManagerSourceLabelTest"` → `tests="9" failures="0"` and `tests="6" failures="0"` at
      `2026-10-22T12:20:58Z` → `evidence/green/`.
      *(An import-order-only tidy of two of the six call sites followed the gate run; the four cases above were
      re-run green after it — `BUILD SUCCESSFUL in 15s`, same four classes — because an import is compiled
      even when it cannot change behaviour.)*
- [x] 3.3 Neighbours, one invocation: `:core:testDebugUnitTest :auto:testDebugUnitTest
      :app:testMobileDebugUnitTest` filtered to `com.naviveylin.core.mapsource.*`,
      `MapDataRegistrarTest`, `AutoMapDatabaseOpenTest`, `MapCanvasViewModelDatabaseOpenTest`,
      `StartMapResolverTest`, `com.naviveylin.ui.mapmanager.*` → `BUILD SUCCESSFUL in 51s`.
- [x] 3.4 Revert-check on §2.1 (one mutation, `revert-check` skill): `MapTreeDiscovery.isStaging` → `false`
      (the classification removed) → both red cases fail again at `2026-10-22T12:20:42Z`, `tests="9"
      failures="1"` and `tests="6" failures="1"` → restored (`grep -c 'REVERT-CHECK'` → 0) → forced green at
      `2026-10-22T12:20:58Z` (§3.2) → `evidence/revert-check/`.
- [x] 3.5 The run's gate: `./tools/loop/gate.sh` once, over the change's pathspec → `GATE-RC=0`,
      `LOOP-GATE: tests=481 failures=0` (nine suites: the Gradle gate plus eight shell checks), and the Gradle
      half's own tallies summed from its JUnit XMLs: **733 suites, 5789 tests, 0 failures, 0 errors** across
      `:app` (576 suites / 4210 tests, both flavours), `:auto` (87 / 880), `:core` (61 / 646),
      `:osmscout-client-java` (8 / 48) and `buildSrc` (1 / 5) — the change's classes in it:
      `StartMapResolverTest` tests=9 failures=0 and `MapManagerSourceLabelTest` tests=6 failures=0 in both
      `:app` flavours, `MapTreeDiscoveryTest` tests=4 failures=0 in `:core` → `evidence/gate/`
      (`totals.txt`, the `loop-gate` tallies, and the three classes' XMLs from that run).

## 4. Archive

- [x] 4.1 `openspec validate fix-staging-leftover-discovered-as-map --strict` → `Change
      'fix-staging-leftover-discovered-as-map' is valid`, `EXIT=0`.
- [x] 4.2 Archived under `openspec/changes/archive/`; the delta merged into
      `openspec/specs/map-updates/spec.md`.
