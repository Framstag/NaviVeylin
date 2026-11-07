# Tasks

## 1. The defect and its red case (measure before changing code)

- [x] 1.1 Record the trap as it stood: an Android configure had written the four generated headers into the
  submodule source tree (`app/src/main/cpp/libosmscout/libosmscout*/include/**/<Name>Features.h`, mtimes
  2026-10-15), and the include order keeps them winning — `app/.cxx/Debug/4z1b2r3y/arm64-v8a/build.ninja` and
  `app/src/main/cpp/libosmscout/build/build.ninja` both list `-I…/libosmscout-map/include` **before**
  `-I…/<buildTree>/libosmscout-map/include`. Verify: both `INCLUDES` lists quoted in `design.md` (design "Root
  cause").
- [x] 1.2 Red case on HEAD, host build, Pango disabled: with the Android-generated `MapFeatures.h` and
  `MapCairoFeatures.h` in the source tree,
  `cd app/src/main/cpp/libosmscout && cmake -B build -DCMAKE_DISABLE_FIND_PACKAGE_Pango=ON && cmake --build build
  --target TextMetricsCairoTest && cd build && TESTS_TOP_DIR=../Tests TESTS_TMP_DIR=./Tests ctest -R
  "^TextMetricsCairoTest$" --output-on-failure` fails *"Cairo measures a configured font file as the face of that
  file"* (`Tests/src/TextMetricsCairoTest.cpp:272`, `REQUIRE(differs)`) — `label width for the configured file:
  218.154, for the substituted family: 218.154`, 7 cases / 6 passed / 1 failed, with `… this build cannot read
  the family it holds`. Verify: `evidence/host-nopango-red.log` (spec: osmscout-native — A host build in the same
  working tree measures its own feature set).
- [x] 1.3 Measure the two facts that force the shape of the fix, before writing it: (a) the core library's source
  list names the *source-tree* header (`libosmscout/libosmscout/CMakeLists.txt:24-26`) while no build-tree copy
  exists under the CMake 3.22.1 the Android Gradle plugin ships, because `cmake_path(GET … PARENT_PATH …)` is a
  no-op there — `evidence/cmake-path-version-check.log` (3.22.1: `inplace_parent=c/MapFeatures.h.cmake`; 4.4.4:
  `c`); (b) `:app:configureCMakeDebug` runs `preBuild` first, so a gate that only refused would block the very
  configure that writes the build-tree headers — `evidence/gate-precedes-native-configure.log`.

## 2. The fix

- [x] 2.1 `app/src/main/cpp/CMakeLists.txt`: render the four headers into
  `${CMAKE_CURRENT_BINARY_DIR}/osmscout-features/<module>/include/…`, prepend each module's include root
  (`include_directories(BEFORE …)`, the root derived with `string(REGEX REPLACE "/include/.*$" "/include" …)`), and
  write nothing under the submodule. Verify: `./gradlew :app:assembleMobileDebug
  -Pandroid.injected.build.abi=arm64-v8a` → BUILD SUCCESSFUL; the regenerated `build.ninja` lists the four
  `osmscout-features/…` include roots first, and `find app/src/main/cpp/libosmscout -name "*Features.h" -not -path
  "*/build*"` reports only the two tracked `Android/` copies. Evidence: `evidence/native-configure-arm64.log`
  (spec: scenarios 1 and 3).
- [x] 2.2 The gate: `buildSrc/src/main/kotlin/com/naviveylin/build/native/GeneratedFeatureHeaders.kt` (scan rule,
  `find`, `report`, typed task) and its registration in `app/build.gradle.kts` wired into `preBuild`. Verify:
  `./gradlew -p buildSrc test --tests "com.naviveylin.build.native.GeneratedFeatureHeadersTest"` → `tests="6"
  skipped="0" failures="0" errors="0"`.
- [x] 2.3 Submodule patch: `cmake/ProjectConfig.cmake` (`get_filename_component … DIRECTORY` instead of the no-op
  `cmake_path(GET …)`) and `libosmscout/CMakeLists.txt` (the core's feature header in the build tree, named with
  `FEATURE`); commit on the submodule's `naviveylin-local` and bump the gitlink here. Verify:
  `git -C app/src/main/cpp/libosmscout diff --stat` → 2 files, 7 insertions, 2 deletions;
  `evidence/submodule-patch.diff`.
- [x] 2.4 `guidelines/Build.md` §12 gains the fact — where the headers live, why they must not sit in the
  submodule source tree, what the gate does, the two submodule fixes, and issue #305 for the Android feature set
  itself. Verify: `tools/check-doc-routes.sh` → "all 154 section references resolve".

## 3. Verification

- [x] 3.1 Host green after the fix: same tree, same configure, same case —
  `evidence/host-nopango-green.log` → `All tests passed (167 assertions in 7 test cases)`.
- [x] 3.2 The gate, red and green, plus the revert-check of its removal: a planted generated header makes
  `./gradlew :app:checkSubmoduleFeatureHeaders` remove the file and fail naming it (`evidence/gate-red.log`); the
  next run is green (`evidence/gate-green.log`). Mutation for the removal — the `delete()` call disabled: the
  gate fails and the file **survives** (`evidence/gate-revert-check.log`); restored: the file is removed
  (`evidence/gate-revert-check-restored.log`). (spec: scenario 2)
- [x] 3.3 Revert-check of the submodule patch: mutation — the two patched files reverted and a fresh `.cxx` tree →
  the Android configure fails with `Cannot find source file: include/osmscout/lib/CoreFeatures.h` and
  `No SOURCES given to target: OSMScout` (`evidence/submodule-patch-revert-check.log`); restored → configure and
  the arm64 build are green (`evidence/submodule-patch-restored.log`, `evidence/native-configure-arm64.log`).
- [x] 3.4 Every shipped ABI and both flavors: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL in 4m37s
  (`evidence/assembleDebug-all-abis.log`).
- [x] 3.5 The JVM suites: `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` → BUILD SUCCESSFUL in
  6m08s; `:app` automotive 2109/0/0, `:app` mobile 2109/0/0, `:auto` 880/0/0, `:core` 646/0/0,
  `:osmscout-client-java` 48/0/0 (`evidence/jvm-gate.log`). **Attribution note:** an earlier run of the same gate
  died with `java.io.EOFException` in `:app:testAutomotiveDebugUnitTest`; the stack trace names
  `Test.getPreviousFailedTestClasses`, i.e. the truncated `results-generic.bin` of the run whose worker was killed
  under memory pressure (1 GB free), and every later run failed there in 2 s without executing a test. After
  `./gradlew --stop` and clearing `app/build/test-results`, the task alone and then the whole gate were green
  (`evidence/jvm-gate-stacktrace.log`, `…single-class.log`, `…retry.log`) — not a test assertion, not this
  change's failure.
- [x] 3.6 The new gate costs no configuration-cache problem: `./gradlew --configuration-cache :app:preBuild` → the
  same 5 script-level problems, `:app:checkSubmoduleFeatureHeaders` among the executed tasks
  (`evidence/configuration-cache.log`).
- [x] 3.7 The change validates: `openspec validate fix-generated-feature-headers --strict` passes.

**Named exclusions.** No JVM/app source changed, so the whole gate (3.5) *is* the suite rather than a focused
`--tests` selection. The shell checks the plan selects for another agent's uncommitted `tools/loop*` files
(`tools/loop-selftest.sh`, `tools/loop-mutations.sh`, `tools/loop-mutations-selftest.sh`) were not run — they are
not this change's paths. `tools/check-doc-routes.sh` (+ its self-test, 19 passed) and
`tools/check-todo-references.sh` were run and are green.

## 4. Tracker

- [x] 4.1 File the finding this change's analysis produced — the Android build's feature set is the app's forced
  one rather than `features.cmake`'s, and `guidelines/MapRendering.md` §20 states a reason that is false for this
  repository's vcpkg triplets: issue #305 (`tools/issues/create.sh`, `class/bug category/build-and-harness
  source/agent`).
- [x] 4.2 Closed §202 (issue #136) with `tools/issues/close.sh` on 2026-10-25 — "closed #136 (completed)", with the
  reason and the evidence above (red/green pair, the ABI and JVM runs, both revert-checks) and commit `793c88bb`
  named (the working tree's `523b6571` cherry-picked onto `main`, because the shared worktree's push collided with
  an incoming push and another agent's uncommitted files).
