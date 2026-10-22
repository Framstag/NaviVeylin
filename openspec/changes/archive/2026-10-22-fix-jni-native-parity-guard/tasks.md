# Tasks — fix-jni-native-parity-guard

## 1. The red case

- [x] 1.1 Add `app/src/test/java/com/naviveylin/build/NativeOverrideParityGuardTest.kt` with the two reach cases:
  "the build refuses an unrecorded deviation" (the app build script registers `checkNativeParity` and wires it
  into `preBuild`) and "the recorded deviations are the tree's own" (every line of
  `osmscout-client-java/native-parity.txt` reads four fields with a reason, names one of the five overridden
  files, and the list holds five `submodule-only` + five `override-only` lines). Verification: on the base tree,
  `./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.build.NativeOverrideParityGuardTest" -PforceTests --no-build-cache`
  fails both — JUnit XML `tests=2 failures=2`, timestamp `2026-10-22T11:30:13.144Z`
  (`evidence/red-HEAD-NativeOverrideParityGuardTest.xml`). Parent: `osmscout-jni`, "The override mirrors the
  submodule's natives, except the recorded deviations".

## 2. The guard

- [x] 2.1 `buildSrc/src/main/kotlin/com/naviveylin/build/jni/NativeParityScanner.kt`: parse each side's `native`
  declarations as `name(type,type)` with comments stripped and package qualifiers removed, compute the
  per-file symmetric difference, and hold it to the recorded list — an unrecorded divergence in either
  direction, a stale recording and a malformed line are findings. Verification:
  `./gradlew --offline -p buildSrc test --tests "com.naviveylin.build.jni.NativeParityScannerTest"` — 5 tests,
  0 failures (task 2.2 adds the cases).
- [x] 2.2 `buildSrc/src/test/kotlin/com/naviveylin/build/jni/NativeParityScannerTest.kt`: five fixture cases
  ("a new submodule native without a record is a finding", "a new override native without a record is a
  finding", "a recorded deviation that no longer deviates is a finding", "a malformed deviation line is a
  finding", "package qualifiers and prose are not divergences"). Verification: the command above,
  `tests=5 failures=0` (`evidence/scanner-cases.xml`).
- [x] 2.3 `osmscout-client-java/native-parity.txt`: the ten recorded divergences with a reason each — five
  `submodule-only` (`calculateRouteWithObjectsAsync`, `getRegion`, `searchLocations(String,int,String,long)`,
  `setGpsMarker`, `startNavigation`) and five `override-only` (`getAddressAt`, `getDatabaseBoundingBox`,
  `getMaxSpeedAt`, `reloadBasemap`, `searchLocationByForm`). Verification: `./gradlew --offline
  :app:checkNativeParity` exits 0 on the unchanged trees (`evidence/gate-task-green.log`).
- [x] 2.4 `app/build.gradle.kts`: register `checkNativeParity` over the submodule's client directory, the
  override directory and the list, and add the `preBuild` dependency beside the eight existing gates.
  Verification: `./gradlew --offline :app:checkNativeParity` runs the task and exits 0 (task 2.3's command), and
  `./gradlew --offline :app:preBuild` reaches it.
- [x] 2.5 `guidelines/Design.md` §5: the paragraph that lists the five overridden files now states the pair's
  rule, the gate and the recorded list, so a new submodule native is a build failure in the bump's own build.
  `guidelines/Build.md` §2's configuration-cache measurement re-counts the script-level tasks it names (13,
  re-measured 2026-10-22, of which `:app:checkNativeParity` is one). Verification: `bash
  tools/check-doc-routes.sh` (task 3.4).

## 3. Verification

- [x] 3.1 The red cases are green on the fixed tree: `./gradlew --offline :app:testMobileDebugUnitTest --tests
  "com.naviveylin.build.NativeOverrideParityGuardTest" -PforceTests --no-build-cache` — JUnit XML
  `tests=2 failures=0` (`evidence/green-NativeOverrideParityGuardTest.xml`), with the printed measurement
  `deviationLines=10`, `byDirection={submodule-only=5, override-only=5}`.
- [x] 3.2 Falsification (revert-check), both directions the gate must catch:
  (a) a native added to the override (`mutatedParityProbe`) makes `:app:checkNativeParity` fail with the named
  finding and the exact list line to add (`evidence/revert-check-mutation-1-unrecorded-override-native.log`);
  (b) a recording the trees no longer carry (`staleProbe(int)`) makes it fail naming the stale line
  (`evidence/revert-check-mutation-2-stale-recording.log`); the tree restored to byte-identical content is
  green again (same file, last lines).
- [x] 3.3 The change's declared cases select its two classes: `bash tools/declared-cases.sh
  fix-jni-native-parity-guard` — rules `declared 2 · touched 1 · mention 0` select
  `com.naviveylin.build.NativeOverrideParityGuardTest` (0.235 s) and `com.naviveylin.build.VersionedGradleJavaHomeTest`
  (0.008 s, named by the change) in `app` (`evidence/declared-cases.txt`).
- [x] 3.4 Structural checks: `bash tools/check-doc-routes.sh` → `all 120 section references resolve, all 5 skill
  references resolve`; `bash tools/gen-feature-list.sh --check-classification` → `unclassified: 0, delta spec
  ids: 1, stale: 0`; `openspec validate fix-jni-native-parity-guard --strict` → `Change
  'fix-jni-native-parity-guard' is valid` (`evidence/structural-checks.txt`).
- [x] 3.5 The project's gate: `./tools/loop/gate.sh` run once per tree state. Round 1 (before 4.1) — exit 0,
  `LOOP-GATE: tests=481 failures=0` over the nine commands this pathspec owes (`evidence/gate-round1.log`).
  Round 2 (the final tree) — exit 0, the same `LOOP-GATE: tests=481 failures=0`, and from the XMLs the run left:
  app mobile `tests=2103 failures=0`, app automotive `tests=2103 failures=0`, auto `tests=880 failures=0`, core
  `tests=642 failures=0`, osmscout-client-java `tests=48 failures=0`, buildSrc `tests=5 failures=0` — `5781`
  module tests in total, `481` of them the shell suites the gate's own line counts. The new host class ran in
  both `:app` flavours (timestamps `2026-10-22T11:55:28.104Z` and `2026-10-22T11:53:46.364Z`), the scanner's
  five cases in `buildSrc` (`2026-10-22T11:34:40.670Z`, forced rerun; the gate's gradle runs left it up-to-date)
  (`evidence/gate-round2.log`, `evidence/gate-tallies.txt`,
  `evidence/gate-mobile-NativeOverrideParityGuardTest.xml`,
  `evidence/gate-automotive-NativeOverrideParityGuardTest.xml`, `evidence/scanner-cases.xml`).

## 4. Round 2 — the correction found after the first gate run

- [x] 4.1 `app/build.gradle.kts`: declare `inputs.dir`/`inputs.file` only when the path exists. Measured on a
  throwaway Gradle project, `inputs.dir(file("nope"))` fails the task's configuration with
  `Input file does not exist … Property '$1' specifies directory … which doesn't exist`, i.e. a clone without
  the submodule would have seen a Gradle validation error instead of the scanner's remedy. With the guard the
  task (which declares no outputs, so it always executes) reaches the scanner:
  `evidence/missing-submodule-remedy.log` shows the five `the submodule's copy is missing … initialise the
  submodule (git submodule update --init)` findings, produced by pointing the task at `no-such-submodule` and
  restoring the build script afterwards (`grep -c no-such-submodule app/build.gradle.kts` → 0;
  `git diff --stat app/build.gradle.kts` → 45 insertions before and after).
- [x] 4.2 The gate re-run on the corrected tree: task 3.5's round 2. Verification of the guard itself: the
  unchanged trees stay green — `./gradlew --offline :app:checkNativeParity --rerun-tasks` →
  `BUILD SUCCESSFUL`, `> Task :app:checkNativeParity` (`evidence/gate-task-green.log`).
