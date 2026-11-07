# Tasks — fix-buildsrc-compiles-on-the-daemon-jvm

## 1. The red case

- [x] 1.1 Add `app/src/test/java/com/naviveylin/build/BuildSrcJvmRequirementTest.kt` with four cases: "the build
  script names no JVM of its own" (comment-aware scan of `buildSrc/build.gradle.kts`, printing `buildScript=`,
  `lines=`, `findings=`), "the criteria still pin the JVM the build logic compiles with"
  (`gradle/gradle-daemon-jvm.properties` carries exactly `toolchainVersion=21` and no `toolchainVendor`), "a
  reintroduced toolchain is refused" (the CI step "Check daemon JVM criteria" must name
  `buildSrc/build.gradle.kts` and refuse a toolchain) and "the scan is still detectable" (a toolchain spec,
  `jvmToolchain(…)` and a `languageVersion` line are reported; a comment and a compile target are not).
  Verification: on the base tree,
  `./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.build.BuildSrcJvmRequirementTest" -PforceTests -PjvmTestsOnly`
  fails with `BuildSrcJvmRequirementTest > the build script names no JVM of its own FAILED` and
  `> a reintroduced toolchain is refused FAILED`, `4 tests completed, 2 failed`, JUnit XML `tests=4 failures=2
  errors=0`, timestamp `2026-10-24T07:44:43.038Z` (`evidence/red-on-head.xml`; the scan's own measurement
  `findings=2`, `:13: toolchain {`, `:14: languageVersion = JavaLanguageVersion.of(17)`).
  Parent: `build-jvm-toolchain`, "The build logic compiles on the JVM the daemon criteria select".

## 2. The fix

- [x] 2.1 `buildSrc/build.gradle.kts`: drop `java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }`
  and state why in its place — `buildSrc` is a build of its own with no `settings.gradle.kts`, so the root
  resolver never reaches it, and the JVM that compiles the build logic is the daemon JVM the criteria select.
  Verification: `./gradlew -Dorg.gradle.java.installations.auto-detect=false help` — `BUILD FAILED` on the base
  tree (`evidence/repro-on-head.log`), `BUILD SUCCESSFUL in 3s` after the edit (`evidence/repro-after.log`).
- [x] 2.2 `.github/workflows/build.yml`, step "Check daemon JVM criteria": after the `org.gradle.java.home`
  predicate, refuse a toolchain, a `languageVersion` or a `JavaLanguageVersion` in `buildSrc/build.gradle.kts`,
  with the file to scan named, an existence check, and `sed 's|//.*||'` in front of the `grep` so the file may
  explain the rule it must not violate. 12 lines added, 1 changed (the step's closing echo names the new check).
  Verification: the step body executed against this tree
  (`yq -r '.jobs.build.steps[] | select(.name=="Check daemon JVM criteria") | .run' .github/workflows/build.yml > guard.tmp.sh && bash guard.tmp.sh`)
  prints `daemon JVM criteria: version 21, no vendor, no package id, no buildSrc toolchain.`, exits 0
  (`evidence/ci-step-green.log`), and refuses the mutation with `exit 1` and
  `::error file=buildSrc/build.gradle.kts` (`evidence/ci-step-revert-red.log`).
- [x] 2.3 `guidelines/Build.md` §2d: the closing paragraph states the rule (the build logic compiles with the
  daemon JVM and declares no toolchain, so a machine that satisfies the criteria also configures `buildSrc`),
  names both guards, and the checks block carries the in-repository invocation as the reproduction — its old
  "Do NOT run this in this repository … (TODO.md §146)" warning described exactly this defect.
  Verification: `bash tools/check-doc-routes.sh` after the edits (task 3.5).

## 3. Verification

- [x] 3.1 The red case is green on the fixed tree:
  `./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.build.BuildSrcJvmRequirementTest" -PforceTests -PjvmTestsOnly`
  — JUnit XML `tests=4 failures=0 errors=0`, timestamp `2026-10-24T07:45:19.104Z`,
  `buildScript=build.gradle.kts lines=30 findings=0` (`evidence/green-focused.xml`).
- [x] 3.2 Falsification (revert-check, one mutation — the Java 17 pin returns): `git checkout -- buildSrc/build.gradle.kts`,
  then the CI step refuses it (`exit 1`, `::error file=buildSrc/build.gradle.kts`, `13: toolchain {`, `14:
  languageVersion = JavaLanguageVersion.of(17)`) and the named case fails — XML `tests=4 failures=1 errors=0`,
  timestamp `2026-10-24T07:45:39.250Z` (`evidence/revert-red.xml`; the other three cases stay green because only
  buildSrc is mutated, so the workflow predicate is already in place). The byte-identical restore
  (`md5sum -c` → `buildSrc/build.gradle.kts: OK`, `.github/workflows/build.yml: OK`) is green again, forced,
  XML `tests=4 failures=0 errors=0`, timestamp `2026-10-24T07:46:26.185Z` against a wall clock of
  `2026-10-24T07:46:27Z` (`evidence/green-restored.xml`), and the CI step prints its green line and exits 0
  (`evidence/revert-check.txt`).
- [x] 3.3 The defect's own reproduction, both directions: `./gradlew -Dorg.gradle.java.installations.auto-detect=false help`
  — `BUILD FAILED` on the base tree (`evidence/repro-on-head.log`) and `BUILD SUCCESSFUL in 3s` on the fixed tree
  (`evidence/repro-after.log`). This is the scenario "A machine that satisfies the criteria configures the build
  logic", which no host case can exercise (a nested Gradle invocation inside a Gradle build), and the command
  `guidelines/Build.md` §2d now prints.
- [x] 3.4 The change's declared cases select the new class: `bash tools/declared-cases.sh
  fix-buildsrc-compiles-on-the-daemon-jvm` (`evidence/declared-cases.txt`).
- [x] 3.5 Structural checks, before and after archiving: `bash tools/check-doc-routes.sh` →
  `all 153 section references resolve, all 4 skill references resolve`; `openspec validate
  fix-buildsrc-compiles-on-the-daemon-jvm --strict` → `Change … is valid` (the ADDED requirement first had to be
  cut below the validator's 500-character bound — its body is now four sentences); `openspec validate --specs` →
  `Totals: 177 passed, 0 failed`; the workflow still parses, `yq '.jobs.build.steps | length'` = 41. After
  archiving the same readings repeat and the merged requirement is at
  `openspec/specs/build-jvm-toolchain/spec.md:154` (`evidence/structural-checks.txt`).
- [x] 3.6 The project's gate, once — green on the second run: `LOOP-GATE: tests=13 failures=0 errors=0` over the
  eleven commands this pathspec owes (`./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` 479s, the
  shell suites 0–4s each), and from the XMLs that run left: app mobile `tests=2109 failures=0 errors=0`, app
  automotive `tests=2109 failures=0 errors=0`, auto `tests=880 failures=0 errors=0`, core `tests=646 failures=0
  errors=0`, osmscout-client-java `tests=48 failures=0 errors=0` — 5792 module tests, 0 failures, 0 errors, so
  the gate's `tests=13` counts the shell suites only and the modules are evidenced by the XMLs. The new class
  ran in both app flavors, 4 tests each, 0 failures (timestamps `2026-10-24T07:56:07.723Z` and
  `2026-10-24T08:00:36.270Z`) — `evidence/gate.log`, `evidence/gate-tallies.txt`,
  `evidence/gate-mobile-BuildSrcJvmRequirementTest.xml`, `evidence/gate-automotive-BuildSrcJvmRequirementTest.xml`.

## 4. The gate's first run, and one out-of-scope one-bit fix

- [x] 4.1 The gate's first run is red on two commands, neither of them this change's (`evidence/gate-attempt1-red.log`):
  (a) `tools/check-action-runtimes.sh: Keine Berechtigung` — the loop profile invokes that suite as a bare path
  (`tools/loop/loop.conf`, `LOOP_GATE_SUITES`: `.github/workflows/*|tools/check-action-runtimes.sh`) while the
  file is committed `100644` on main (`git ls-tree HEAD tools/check-action-runtimes.sh`), so the gate cannot
  execute it; CI calls it as `bash tools/check-action-runtimes.sh` and never noticed. (b) `:auto:testDebugUnitTest`
  → `StartupScreensTest > diagnosticsScreenShowsNewestFirst FAILED expected:<4> but was:<8>`, attributed to
  concurrency: the case writes one fixed path in the machine-global temp dir
  (`auto/src/test/java/com/naviveylin/auto/StartupScreensTest.kt:96`,
  `java.io.tmpdir/diag-startup-test/app.log`), and on 2026-10-24 three loop runs were active on this machine
  (`.pi/worktrees/issue-227` `--items 3 --review off`, `.pi/worktrees/issue233` `--items 1`, plus the tree's own),
  so two test JVMs wrote that one file — 4 rows (the disclosure plus three entries) twice is the 8 the case saw.
  Verification: the gate's second run is green (`evidence/gate.log`), and the attribution rests on the fixed path
  and the concurrent runs, not on anything this change compiles: it touches no `:auto` source.
- [x] 4.2 `tools/check-action-runtimes.sh` gets the executable bit back, in its own commit — the missing bit is
  not this change's subject, but this change's pathspec pulls its suite in through `.github/workflows/build.yml`,
  so the gate cannot be green without it. Verification: invoked exactly as the gate does, it prints
  `action runtimes: 10 action pins across 2 workflow files target Node 24.` and exits 0
  (`evidence/gate-action-runtimes-mode.txt`); the base mode is `100644` and its sibling suites are `100755`.
