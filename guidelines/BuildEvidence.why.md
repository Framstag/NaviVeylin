# Build Evidence — why

Rationale, baselines and case histories for `guidelines/BuildEvidence.md`. Read beside the section it anchors
to.

## §4 Result evaluation

Measured 2026-10-22: the six sub-sections cost 3 074–5 275 bytes each, against 23 380 bytes for §4 before the
split.

## §4a The verdict, the warnings and the failures

- **The qualified-injected-property rule.** Measured 2026-10-20: one site
  (`NavigationNotificationController`) and two `w:` lines, one per `:app` flavor compile of the shared tree; a
  forced recompile of both flavors with the site fixed prints none of the family
  (`QualifierUseSiteTargetTest`, spec `build-test-gate`).
- **The source-scan guard's prune.** The project's own source-scan guards live in the package
  `com.naviveylin.build`, which a `build` prune removes, leaving a green that scanned nothing. Measured
  2026-10-21: three such fixtures, `sources=719` against 716.
- **The unit-test deprecation family.** Measured 2026-10-20 with `-Pkotlin.incremental=false` over the four
  suites (`:app` mobile and automotive, `:auto`, `:core`): HEAD's log carries 109 `w:` lines, of which exactly
  7 were that family — four call sites, counted twice because `:app`'s two flavors compile the same test tree
  (`addSensor(int, Sensor)`, `isBroadcastIntent()`, `isActivityIntent()`, `Notification.defaults`); after
  change `fix-test-source-deprecations` the same four compiles carry none — its `w:` total is 100 rather than
  HEAD's 109 partly because `:app:compileAutomotiveDebugKotlin` was up to date. Of HEAD's 109 lines, 91 are
  the `ExperimentalCoroutinesApi` opt-in family; of the 18 that are neither, 7 are the family above and 2 are a
  deprecation of a different family from production source (`LocationMarkerOverlay.kt:165` —
  `quadraticBezierTo`), the remaining 9 being pre-existing non-deprecation warnings.
- **The `:auto` diagnostics.** Swept by change `fix-auto-test-source-warnings`;
  `AutoTestSourceWarningBaselineTest` now holds that set.
- **The nullability family.** Change `fix-nullability-warnings-in-two-test-sources`: measured 2026-10-29, one
  line per family per `:app` flavor compile, four lines over two call sites, and both compiles print
  `grep -c "^w: "` = 0 after the fix. `UnitTestSourceNullabilityBaselineTest` (`:app` unit tests, 3 cases)
  holds the absence.

## §4b Which flavors and suites a run must cover

- Measured 2026-10-04: both `:app` suites execute the identical 215 test classes / 1635 tests, so the second
  suite is duplicate work unless a flavor input changed — `mobile` 2m58s + `automotive` 2m57s for both against
  2m58s for one (agent attached, §7), a saving of about 3 minutes per gate.
- Declared cases: measured 9 classes / 11.1 s class time for `fix-route-session-stop-path` against 220 classes
  / 252 s for the whole `:app` suite at the declared stage; the full union is 82 classes / 91.1 s for the same
  change. Expect ~30-45 s for a declared stage — the Robolectric fork bootstrap (~11 s per fork), the daemon
  and a test-source compile are the floor.
- **Forcing.** Measured 2026-10-04 on `:core`: `:core:compileDebugKotlin UP-TO-DATE` with `30 actionable
  tasks: 2 executed, 28 up-to-date`, where `--rerun-tasks` re-executes every compile, package and native task.
- **`-PjvmTestsOnly`.** Issue #270: 26 s of configuration failure, no test task reached its action.

## §4c Native scope, pruned configurations and a restored mutation

- Measured 2026-10-04: `configureCMakeDebug[arm64-v8a]` / `buildCMakeDebug[arm64-v8a]` execute and nothing for
  the other ABIs; the APK carries the app's own libraries (`libosmscout*.so`,
  `libnaviveylin_log_bridge.so`, `libomp.so`) only under `lib/arm64-v8a/`.
- A one-file native change costs ~5.5 s of CMake work across the three ABIs (arm64 2982 ms, armv7 2500 ms,
  x86_64 78 ms on 2026-10-05). CCache/sccache are not installed; the measured potential saving is a fraction
  of those 5.5 s, so it was declined. The expensive case is a new configuration hash, which the prune below
  and a stable configuration avoid.
- Prune: measured 2026-10-04/05, `app/.cxx` went from 7.6 GB / 13 trees to 2.1 GB / 4 configuration trees.
- Restoring a mutation: `TODO.md` §17.

## §4d Attributing a red run

- Reading the log by hand costs ~20 % of a heavy item's shell calls on suites plus ~18 % on the log
  afterwards, every round (issue #689).
- A flake of unknown origin: this tree keeps no flake list (`:591`);
  `FavoritesSheetReorderComposeTest` is the worked example — the 5 s bound was never the problem and the
  awaited state came from `Dispatchers.Default` (§6d).
- Sweeping for a moved expectation: 2026-10-03, the POI fit's floor changed and only the full gate found
  `PoiSearchViewModelTest` asserting the old `14.0`.
- `Dispatchers.Main is used concurrently with setting it`: observed 2026-09-28, CI runs
  `36449101190`/`36453748630`, 14 and 6 failures over eight different classes. The primary trace when created
  in `NonConcurrentlyModifiable.getValue` (`TestMainDispatcher.kt:73`), or the `Caused by: java.lang.Throwable:
  reader location` when the writer was the caller; the frames were `DispatchedCoroutine.afterResume` ←
  `CoroutineScheduler$Worker.run`.

## §4e Falsification, flakes and scheduling traps

- `UncaughtExceptionsBeforeTest`: one process-wide leak produced failures in three unrelated packages.
- Revert-check with `TODO.md` §17.
- Background publisher: measured 2026-10-05, the navigation engine's stale-speed tick reverted `maxSpeedKmH` to
  its `Double.NaN` default, which made `NavigationEngineTest`'s lane/instruction/max-speed assertions look
  `~1-in-2` flaky (`fix-navigation-engine-test-flakes`, archived 2026-10-06 — `TODO.md` §121). The same shape
  was fixed for the road-info lookup's publish, which now hops with `withContext(Dispatchers.Main)`.
- Dispatcher seam: `com.naviveylin.test.SearchHistoryDispatcherRuleTest` refuses an unwired
  `SearchHistoryRepository` construction (96 sites in 65 classes were unwired when it was added, item §123).

## §4f Artifacts, evidence rules and native verification

- Verify an artifact against its inputs: §40.6.
- Packaged asset / unit-test asset APK: issue #45, `TODO.md` §17; measured 2026-10-24, moving one icon out of
  the submodule's raster leaf re-executed `syncSubmoduleIcons` → `mergeMobileDebugAssets` →
  `packageMobileDebugUnitTestForUnitTest` and the APK stopped carrying it, while an unchanged tree left the task
  `UP-TO-DATE` (7 s). The 2026-09-29 note came from deleting a file from a `Sync` task's *output*. Both halves
  are cases (`tools/check-gate-artifacts-selftest.sh`).
- One mutation per revert check: §40.12. Grep for a moved observable: §40.32 (`lastRenderMag` holds the raw
  scale now, `2^level`).
- New native method: `native-bridge-signature-change` skill; two-`#ifdef` verification, `TODO.md` §40.47/§40.48.
- Hidden visibility: `StringMatcherTransliterateFactory`.
- `ctest` after partial `ninja`: a stale executable run against a new shared library was this repo's false
  positive.
- Marisa: `TODO.md` §40.47.
- Native test binaries: §40.48.
- Host JNI target: §202, issue #38; the durable `JAVA_HOME` fallback is issue #216; symptom
  `fatal error: jni.h: Datei oder Verzeichnis nicht gefunden`.
- `System.loadLibrary`: §40.49. `openDatabase`: §40.50. Gradle JAR: §40.51. JavaScout Maven JAVA_HOME: §40.52.
  Stale meson host builds: §40.53.
- Native index fixtures: §40.34. `DBThread`: §40.35. md5 after stash: §40.36.
- Search scope extent: measured 2026-10-29, the location index of `Tests/data/testregion` is a 77-byte header
  carrying no region; the full import writes a `coord.dat` of several hundred MB (764 MB here), deleted once
  done.

## §7 Code coverage

- **Baseline (2026-09-11)** — merged report: 662 classes, 59.7 % line (8839/14805), 58.2 % instruction; per
  module: `app` 58.9 %, `auto` 53.8 %, `core` 67.3 % (line).
- Re-measured 2026-10-27 (change `fix-auto-module-coverage-floor`): `auto` reads INSTRUCTION 25 064 of 34 566
  (**72.5 %**) against 23 923 of 34 566 (**69.2 %**) before it (the coverage row's arithmetic,
  `tools/build-inputs.sh:91-114`), with the module's suite at 92 classes / 885 cases / 0 failures.
- JNI provenances (2026-10-27, change `fix-client-java-coverage-provenance`; `evidence/jacoco-own.xml`,
  `evidence/jacoco-vendored.xml`): own report INSTRUCTION 2099 of 2854 (73.5 %), LINE 523 of 725 (72.1 %),
  CLASS 10 covered / 0 missed; vendored report INSTRUCTION 1342 of 1356 (99.0 %), LINE 251 of 254 (98.8 %),
  CLASS 23 covered / 1 missed (the unused `RouteCallback` interface), from 23 test classes / 116 cases / 0
  failures. Before the split the single report read 3124 of 4210 (74.2 %), the own half at 62.4 % (1782 of
  2854), below the floor.
- Re-checked 2026-10-04 after the opt-out (`:core` `com/naviveylin/core/AccuracyClass` LINE 0 missed / 3
  covered); the JNI module's suite read 2 classes / 26 tests / 0 failures, unchanged by the opt-out, and grew
  since — 8 / 48 before the 2026-10-27 fix, 21 / 106 after it, 23 / 116 after the split.
- Agent timings: measured 2026-10-04, `:app:testMobileDebugUnitTest` 2m58s attached / 2m40s detached,
  `:app:testAutomotiveDebugUnitTest` 2m57s / 2m36s, 215 classes and 1635 tests both; the agent costs 10-12 %.
- Configuration cache: re-measured 2026-10-22 on `:app:testMobileDebugUnitTest --tests
  "com.naviveylin.build.NativeOverrideParityGuardTest"` — 13 problems, all script-level `DefaultTask`
  registrations: `:app:checkCleartextTransportPolicy`, `:app:checkHardcodedStrings`, `:app:checkNativeParity`,
  `:app:checkNoCoordinatesInLogs`, `:app:checkNoWallClockWaits`, `:app:downloadSbomCli`,
  `:app:generateSbomMobileDebug`, `:app:mergeNativeSbom`, and `:auto:`/`:core:` `checkHardcodedStrings` and
  `checkNoWallClockWaits`; it was 8 when first evaluated. `TODO.md` §22 carries the same conclusion. The
  release-state contract holds: `:app:checkLicensePolicy` on 2026-10-05 left `app/release-version.properties`
  byte-identical (same md5).
- **Gradle deprecation baseline.** Swept 2026-10-20: `./gradlew --offline --warning-mode all help` reports two
  families on Gradle 9.6.1. The project-owned one — ten `val <name> by tasks.registering { }` registrations in
  `app`, `auto` and `core` — is gone (change `fix-gradle10-script-deprecations`); the guard is
  `GradleScriptDeprecationTest` (8 scripts, prints `scripts=`/`findings=`). The remaining family is upstream
  Kover: 0.9.8 passed a `Project` object to `DependencyHandler.add`; the stack frame was inside the plugin
  (`kotlinx.kover.gradle.plugin.appliers.PrepareKoverKt.prepare`); upstream ended that call path in 0.9.9
  (changelog for issue #818). `KoverDeprecationBaselineTest` holds this section and `build.gradle.kts` to that
  threshold.
- **Kotlin deprecation baseline.** Swept 2026-10-20: the `:app` compile printed
  `…/LocationMarkerOverlay.kt:165:14 'fun quadraticBezierTo(…)' is deprecated.` The pinned Compose ui 1.7.6
  (`compose-bom:2024.12.01`) deprecates the `Bezier` spelling and a later release removes it (change
  `fix-deprecated-quadratic-path-builder`). No warning-as-error flag is set: the post-fix gate's compile log
  carries 53 `w:` lines; the guard's red run printed 28. The guard is `ComposeApiDeprecationTest` (805 `.kt`
  files, `files=`/`findings=`). A `build` directory is pruned by the marker a Gradle project root writes
  beside it, never by its name: the name-based prune hid the project's own 37 `com.naviveylin.build` package
  sources, so the count quoted (731) sat beside a scan that read 768 of 805 (change
  `fix-the-scan-skips-sources-under-a-directory-named-build`).

## §11 Measuring a phone UI finding

Change `route-planning-session` spent nine review rounds on a pixel finding (`ki_processing_failures.log`) and
found both real causes only after measuring. The detector is covered device-free by
`bash tools/measure-highlight-selftest.sh` and by the older `.pi/skills/pixel-check/selftest.sh`
(ImageMagick). Measured 2026-10-06 on a frame with no route: `px=18`, `band=[0,2400]`, `verdict: inside` —
the look caught the colour coincidence (`TODO.md` §147).
