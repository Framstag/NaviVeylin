# Tasks — a JVM-only test run needs no vcpkg checkout

## 1. The toggle and its rule

- [x] 1.1 `buildSrc/src/main/kotlin/com/naviveylin/build/testing/JvmOnlyTestRun.kt`: the property name, the
      `BuildFlags` read, the packaging-task decision and its refusal text.
      Verify: `./gradlew -p buildSrc compileKotlin`.
- [x] 1.2 `buildSrc/src/test/kotlin/com/naviveylin/build/testing/JvmOnlyTestRunTest.kt`: seven cases — absent and
      `false` are off, `""`/`true` are on, an unreadable value is refused naming both forms, a test-only
      invocation is never refused, every packaging entry point is refused, the refusal names task and property,
      `packagingTasksIn` keeps the requested names.
      Verify: `./gradlew -p buildSrc test --tests '*JvmOnlyTestRunTest'` → tests=7 failures=0
      (`evidence/unit-jvm-only-test-run.xml`).
- [x] 1.3 Revert-check for the refusal: delete `packagingTasksIn`'s prefix match (return `emptyList()`), the
      named case `everyPackagingEntryPointIsRefused` must fail, restore. Recorded in `evidence/revert-check.log`.

## 2. The build script

- [x] 2.1 `app/build.gradle.kts`: `jvmTestsOnly` read once, the refusal thrown at configuration when the
      requested task names package, the inventory's asset source left unwired when it is set.
      Verify: `VCPKG_ROOT=<absent> ./gradlew :app:testMobileDebugUnitTest -PjvmTestsOnly --dry-run` → exit 0 and
      no `:app:(generateLicenseAssets|generateSbom|mergeNativeSbom)*` line (`evidence/green-dry-run.log`).
- [x] 2.2 `mergeNativeSbom`: the status file an input only when it exists, the share directories likewise, the
      port list a `Provider` evaluated when the task runs.
      Verify: `VCPKG_ROOT=<absent> ./gradlew :app:mergeNativeSbom -x :app:downloadSbomCli` → fails naming
      `installed-status file not found`, and **not** as `Could not create task` (`evidence/green-merge-task.log`).
- [x] 2.3 Refusal on a packaging graph.
      Verify: `./gradlew :app:assembleMobileDebug -PjvmTestsOnly --dry-run` → exit 1, message names
      `:app:assembleMobileDebug` and `-PjvmTestsOnly` (`evidence/green-refusal.log`).

## 3. The suite

- [x] 3.1 `tools/check-jvm-test-graph-selftest.sh`: the three cases above, each one `--dry-run` configuration,
      `--root` supported, `all N cases passed` summary.
      Verify: `bash tools/check-jvm-test-graph-selftest.sh` → `all 3 cases passed` (`evidence/suite.log`).
- [x] 3.2 Register it: `tools/loop/loop.conf`'s `LOOP_GATE_SUITES` owes it for `app/build.gradle.kts` and
      `buildSrc/*`; `.github/workflows/build.yml` runs it before vcpkg is installed.
      Verify: `tools/loop/gate.sh --plan --base f5f2a2a76e3adc73e377b0bf2747718ac17dca76` names the suite.

## 4. The recipes

- [x] 4.1 `tools/loop/loop.conf`: `LOOP_GATE_COMPILED_CMD` passes `-PjvmTestsOnly`.
      Verify: the gate's own log line names the command it ran.
- [x] 4.2 `.github/workflows/build.yml`: the unit-test step passes the property (the three strict steps above it
      keep the vcpkg tree in their path).
      Verify: `bash tools/check-action-runtimes-selftest.sh` (the workflow parses) — the workflow is text, no
      build reads it.

## 5. The documents

- [x] 5.1 `guidelines/Build.md` §3 (the gate row and the no-vcpkg row), §4b (the forcing bullet and the new
      property bullet), §9 (the asset-source paragraph: the property, the refusal, the lazy read).
      Verify: `tools/check-doc-routes.sh` — no dangling route.

## 6. Evidence and gate

- [x] 6.1 The decision's red command, recorded on the base (`evidence/red-HEAD.log`, exit 1, 26 s) and re-run on
      this tree (`evidence/green-full-run.log`, exit 0) — `VCPKG_ROOT=<absent> ./gradlew test -PforceTests
      -PjvmTestsOnly --no-build-cache`.
- [x] 6.2 `./tools/loop/gate.sh` once, from the change root, its result quoted in `evidence/gate.log`.
- [x] 6.3 `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-jvm-test-run-needs-no-native-tree
      --root .` — every pointer resolves.
- [x] 6.4 Archive the change and confirm the delta merged into `openspec/specs/build-sbom/spec.md`.
