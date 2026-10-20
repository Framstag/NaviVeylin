# Tasks — fix-gradle10-script-deprecations

## 1. The red case

- [x] 1.1 Add `app/src/test/java/com/naviveylin/build/GradleScriptDeprecationTest.kt` with the two cases:
  "no project build script uses a removed Gradle construct" (scan of every `*.gradle.kts` the project owns,
  asserts zero findings and prints `scripts=`/`findings=`) and "the removal is still detectable" (the scan
  reports a deprecated line and ignores the named form beside a comment naming the delegate). Verification: on
  the base commit `648d2bc` —
  `./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.build.GradleScriptDeprecationTest" -PforceTests`
  fails with `expected:<[]> but was:<[:377: …]`, JUnit XML `tests=2 failures=1`, timestamp
  `2026-10-20T12:14:18.550Z`, `scripts=8 findings=10` (`evidence/TEST-GradleScriptDeprecationTest-red.xml`).
  Parent: `build-jvm-toolchain`, "The build's scripts use no construct Gradle 10 removes".

## 2. The fix

- [x] 2.1 In `app/build.gradle.kts`, rewrite the six registrations at `:377` (`checkHardcodedStrings`), `:406`
  (`checkNoCoordinatesInLogs`), `:444` (`checkCleartextTransportPolicy`), `:474` (`checkNoWallClockWaits`),
  `:606` (`downloadSbomCli`) and `:734` (`mergeNativeSbom`) from `val <name> by tasks.registering {` to
  `val <name> = tasks.register("<name>") {`. The configure block and the task name are unchanged.
  Verification: `grep -c "by tasks.registering" app/build.gradle.kts` prints `0`.
- [x] 2.2 Same rewrite in `auto/build.gradle.kts` (`:118`, `:138`) and `core/build.gradle.kts` (`:104`, `:125`).
  Verification: `grep -rn --include="*.gradle.kts" "by tasks.registering" .` finds nothing (exit 1); the guard
  case above turns green (`scripts=8 findings=0`).
- [x] 2.3 `guidelines/Build.md` §7: record the sweep beside the existing deprecation paragraphs — the
  project-owned delegate family is gone (with the command that measures it), the Kover plugin's Project-object
  warning is measured as plugin-owned and stays, and the `:app` case is named as the guard.
  Verification: the paragraph names the command, the two outcomes and the case; `bash tools/check-doc-routes.sh`
  — `all 89 section references resolve`, exit 0.

## 3. Verification

- [x] 3.1 The guard is not vacuous and the tree is clean of the family:
  `./gradlew --offline --warning-mode all help` reports `0` matches for `val name by registering` and still
  reports the Kover plugin's one (`Project object as a dependency`: count `1`) —
  `evidence/fixed-warning-mode-all.txt`, against `evidence/head-warning-mode-all.txt` (ten).
- [x] 3.2 The ten tasks still exist under their names and run:
  `./gradlew --offline --warning-mode all :app:checkHardcodedStrings :app:checkNoCoordinatesInLogs
  :app:checkCleartextTransportPolicy :app:checkNoWallClockWaits :auto:checkHardcodedStrings
  :auto:checkNoWallClockWaits :core:checkHardcodedStrings :core:checkNoWallClockWaits` — every task line
  executed, `BUILD SUCCESSFUL in 1s`, the only warning the Kover one (`evidence/fixed-checks.log`); and
  `./gradlew --offline :app:tasks --all` still lists `checkHardcodedStrings`, `checkNoCoordinatesInLogs`,
  `checkCleartextTransportPolicy`, `checkNoWallClockWaits`, `downloadSbomCli`, `mergeNativeSbom`
  (`evidence/app-tasks-all.log`).
- [x] 3.3 The red case is green on the fixed tree:
  `./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.build.GradleScriptDeprecationTest"
  -PforceTests` — JUnit XML `tests=2 failures=0`, timestamp `2026-10-20T12:15:36.723Z`,
  `scripts=8 findings=0` (`evidence/TEST-GradleScriptDeprecationTest-green.xml`).
- [x] 3.4 Falsification (revert-check): `app/build.gradle.kts:377` restored to the delegate form makes the
  guard red — `expected:<[]> but was:<[:377: val checkHardcodedStrings by tasks.registering {]>`, exit 1 —
  and the byte-identical restore (`md5 29f1ed11af62b11fb6af63e2ebd9ba54`, unchanged before and after) is
  green again (`evidence/revert-check.txt`, `evidence/revert-check-red.log`).
- [x] 3.5 The documented gate, once: `./gradlew test -PforceTests --no-build-cache` — `BUILD SUCCESSFUL in
  4m 10s`; per module: app mobile `tests=2064 failures=0`, app automotive `tests=2064 failures=0`, auto
  `tests=866 failures=0`, core `tests=642 failures=0`, osmscout-client-java `tests=48 failures=0` — total
  `tests=5684 failures=0` (`evidence/gate-tallies.txt`, `evidence/gate.log`, and the class's XML as the gate
  produced it in both flavors).
- [x] 3.6 The change's declared cases select the new class: `bash tools/declared-cases.sh
  fix-gradle10-script-deprecations` — rule `declared` selects 1 class, `com.naviveylin.build.GradleScriptDeprecationTest`,
  in `app` (`evidence/declared-cases.txt`).
