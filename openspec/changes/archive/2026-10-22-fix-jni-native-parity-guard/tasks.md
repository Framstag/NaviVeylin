# Tasks — fix-jni-native-parity-guard

## 1. The red case

- [x] 1.1 Add `app/src/test/java/com/naviveylin/build/NativeOverrideParityGuardTest.kt` with the two reach cases:
  "the app build registers the parity gate and wires it into preBuild" (the app build script registers
  `checkNativeParity` and wires it into `preBuild`) and "the recorded deviations are the tree's own" (every line of
  `osmscout-client-java/native-parity.txt` reads four fields with a reason, names one of the five overridden
  files, and the list holds five `submodule-only` + five `override-only` lines). The red run below names the first
  case by its round-1 name, `the build refuses an unrecorded deviation`: round 3 renamed it to the claim its two
  assertions make (4.3), and the red XML is the round-1 artifact, so it keeps the old name.
  Verification: on the base tree,
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
  (`evidence/revert-check-mutation-2-stale-recording.log`); the tree restored to byte-identical content
  (`diff` against the copies taken before the mutation, empty) is green again —
  `BUILD SUCCESSFUL` / `> Task :app:checkNativeParity` in `evidence/revert-check-green-after-restore.log`.
  (Round 3 re-ran both mutations with a date line, and added the missing restored-tree run: 4.5.)
- [x] 3.3 The change's declared cases select its two classes: `bash tools/declared-cases.sh
  fix-jni-native-parity-guard`
  — `evidence/structural-checks.txt` carries the run, `1 declared app 0.235 com.naviveylin.build.NativeOverrideParityGuardTest`
  and `2 declared app 0.008 com.naviveylin.build.VersionedGradleJavaHomeTest` (named by the change), `rules
  declared 2 · touched 1 · mention 0`, `selected 2 of 435 test classes`. (`evidence/declared-cases.txt` is an
  earlier run of the same command against the same change and reads 0.013 s and 0.014 s; the times are the last
  run's own measurements, not a property of the change, so the pair quoted is the one in
  `structural-checks.txt`.)
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
  `evidence/gate-automotive-NativeOverrideParityGuardTest.xml`, `evidence/scanner-cases.xml`). The per-module
  sums are quoted from `evidence/gate-tallies.txt`, and the XMLs behind the four modules other than the two the
  delta names stay where the gate wrote them (`<module>/build/test-results/`); `evidence/gate-round2.log` itself
  carries the nine command names and the tally line, and every date is in `gate-tallies.txt` or a copied XML.

## 4. Round 2 — the correction found after the first gate run

- [x] 4.1 `app/build.gradle.kts`: declare `inputs.dir`/`inputs.file` only when the path exists. Measured on a
  throwaway Gradle project, `inputs.dir(file("nope"))` fails the task's configuration with
  `Input file does not exist … Property '$1' specifies directory … which doesn't exist`, i.e. a clone without
  the submodule would have seen a Gradle validation error instead of the scanner's remedy. With the guard the
  task (which declares no outputs, so it always executes) reaches the scanner:
  `evidence/missing-submodule-remedy.log` shows the five `the submodule's copy is missing … initialise the
  submodule (git submodule update --init)` findings, produced by pointing the task at `no-such-submodule` and
  restoring the build script afterwards. The restore is not evidenced by a hash or a diff here: the run that
  rules a leftover marker out is the gate on the corrected tree — `BUILD SUCCESSFUL` over the nine commands, with
  `> Task :app:checkNativeParity` executing (`evidence/gate-round2.log`), which a build script still naming
  `no-such-submodule` could not produce. The unguarded failure itself is re-measured in round 3
  (`evidence/inputs-dir-missing.log`: the task configuration fails with `Input file does not exist … specifies
  directory`, on a throwaway project, 2026-10-22).
- [x] 4.2 The gate re-run on the corrected tree: task 3.5's round 2. Verification of the guard itself: the
  unchanged trees stay green — `./gradlew --offline :app:checkNativeParity --rerun-tasks` →
  `BUILD SUCCESSFUL`, `> Task :app:checkNativeParity` (`evidence/gate-task-green.log`).

## 5. Round 3 — the corrections the review of round 2 required

The review's one BLOCKING finding: the scenario "The build refuses an unrecorded deviation" named a case that
asserts only that the build script registers the gate and wires it into `preBuild` — a different claim from
"fails the build through `preBuild`, naming the file and the list line". Repaired subtractively (the reviewer's
option 2), not by adding an end-to-end case: the refusal is already measured by the scan's five fixture cases
(2.2) and by the two mutation runs (3.2, re-run in 4.5), and no new claim is made.

- [x] 4.3 Narrow the scenario to the claim its case asserts, in both copies (the change delta and the shipped
  `openspec/specs/osmscout-jni/spec.md`, which stay byte-identical): "The build refuses an unrecorded deviation"
  → "The app build registers the parity gate and wires it into preBuild", its WHEN/THEN restated as the wiring,
  and the case renamed to match. Verification: the requirement block of the two copies compared with `diff` —
  exit 0 (`evidence/structural-checks-round3.txt`, last line); `openspec validate --specs` → `Totals: 177 passed,
  0 failed (177 items)`, exit 0 (same file).
- [x] 4.4 The renamed case is green, and the other case in its class with it:
  `./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.build.NativeOverrideParityGuardTest"
  -PforceTests --no-build-cache` → `BUILD SUCCESSFUL`; JUnit XML `tests=2 failures=0`, timestamp
  `2026-10-22T12:13:00.165Z`, test names "the app build registers the parity gate and wires it into preBuild" and
  "the recorded deviations are the tree's own" (`evidence/green-round3-NativeOverrideParityGuardTest.xml`,
  `evidence/green-round3.log`). The red XML keeps the pre-rename name (1.1) — it is the round-1 artifact.
- [x] 4.5 Revert-check re-run with a timestamp and with the restored-tree run the round-1 logs lacked, in both
  directions: (a) the override declaring `mutatedParityProbe(double,double)` → `> Task :app:checkNativeParity
  FAILED`, `BUILD FAILED in 555ms`, the finding naming the exact list line
  (`evidence/revert-check-mutation-1-unrecorded-override-native-round3.log`, dated 2026-10-22T12:13:06Z);
  (b) the list recording `staleProbe(int)` → `FAILED`, `BUILD FAILED in 582ms`, "no longer exists — the two
  sides now agree on it, so remove the line" (`evidence/revert-check-mutation-2-stale-recording-round3.log`,
  dated 2026-10-22T12:13:10Z);
  after each, the file restored from the copy taken before the mutation (`diff` empty) and the task green again —
  `BUILD SUCCESSFUL in 552ms` / `in 554ms`, `> Task :app:checkNativeParity`
  (`evidence/revert-check-green-after-restore.log`). The round-1 pair is kept beside these.
- [x] 4.6 The two measurements round 2 quoted without a capture are captured: `./gradlew --offline
  --configuration-cache :app:testMobileDebugUnitTest --tests "com.naviveylin.build.NativeOverrideParityGuardTest"`
  → `13 problems were found storing the configuration cache` / `Configuration cache entry discarded with 13
  problems`, listing exactly the tasks `guidelines/Build.md` §2 names (`evidence/configuration-cache-problems.log`,
  which confirms the 13 the guideline states, so the guideline text is unchanged); and
  `./gradlew --offline -p /tmp/cc-probe probe` on a one-task throwaway project declaring
  `inputs.dir(file("nope"))` → `Input file does not exist … specifies directory '/tmp/cc-probe/nope' which
  doesn't exist` (`evidence/inputs-dir-missing.log`).
- [x] 4.7 The parity probe re-run, so the two names the item's memory had wrong are visible in its own output:
  `python3 evidence/native_parity_probe.py` → `OSMScoutClient.java: submodule 59 natives, override 59 natives`,
  `total deviations: 10`, the ten listed and neither `cancelSearch` nor `renderInto` among them
  (`evidence/parity-probe-round3.txt`).
- [x] 4.8 Structural checks re-run with the documented commands and no failed invocation above the result
  (round 1's `structural-checks.txt` opens with `zsh:1: keine Berechtigung: ./openspec`): `openspec validate
  --specs` → 177 passed; `bash tools/check-doc-routes.sh` → `all 120 section references resolve, all 5 skill
  references resolve`; `bash tools/gen-feature-list.sh --check-classification` → `unclassified: 0, delta spec ids:
  0, stale: 0` (`evidence/structural-checks-round3.txt`).
- [x] 4.9 No gate re-run, on purpose: this round touches no compiled source except one test method's name, so the
  scope is the affected case (4.4) plus the structural checks (4.8); the gate's own result stands as round 2
  recorded it (3.5), and re-running it to "refresh" evidence would destroy rather than refresh it.
