# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/sonar-analysis/spec.md` for the contract.

## 1. The red case

Requirement: "The workflow's Gradle steps run a JVM-only graph", scenario "The report step carries the JVM-only
toggle".

- [x] 1.1 Add `sonar-workflow#theCoverageStepCarriesTheJvmOnlyFlag` to `tools/check-sonar-workflow-selftest.sh`:
      copy the repository's own `sonar.yml` into the throwaway tree, drop `-PjvmTestsOnly` from its Gradle `run:`
      lines, and require the checker to fail naming `sonar.yml` and a line; a second planting that only drops a
      report task (`:app:koverXmlReport`) must still pass, so the case is about the flag and not about the task
      list. Add the case name to the file's header list. Verify: on base `55b5d658` the case exits 1 and prints
      `FAIL: sonar-workflow#theCoverageStepCarriesTheJvmOnlyFlag (expected exit 1, got 0)` — recorded as
      `evidence/red-on-HEAD.log` (`2026-11-03T07:04:15Z`, exit 1, the checker green on the unwatched file).

## 2. The assertion the case requires

Requirement: the same requirement.

- [x] 2.1 Add the assertion to `tools/check-sonar-workflow.sh`: for every line of `sonar.yml` that invokes
      `./gradlew` (comments excepted), skip it when it carries `-PjvmTestsOnly`; otherwise take the requested
      task names (tokens after `./gradlew`, flags and the wrapper excepted, module path stripped) and report the
      first whose name is a test or a coverage report (`test`, `Test`, `kover`). Extend the file's header list
      with the group. Verify: `bash tools/check-sonar-workflow-selftest.sh --case
      sonar-workflow#theCoverageStepCarriesTheJvmOnlyFlag` exits 0, and the same script without `--case` passes
      every case — `evidence/green-selftest.log` (`all 9 cases passed`).
- [x] 2.2 Confirm the report invocation itself is allowed by the toggle: `refusalFor` lists only packaging
      prefixes and `:app:koverXmlReport` matches none (`buildSrc/src/main/kotlin/…/JvmOnlyTestRun.kt`). Verify:
      `bash tools/check-jvm-test-graph-selftest.sh` exits 0 — `all 3 cases passed`, and no compiled file
      changes, so nothing is rebuilt for this task.

## 3. The two invocations that failed

Requirement: the same requirement, in the workflow and in the recipe that mirrors it.

- [x] 3.1 Pass `-PjvmTestsOnly` in `.github/workflows/sonar.yml`'s "Generate coverage reports" step, and in the
      row `guidelines/Build.md:121` that documents the same two commands. Verify:
      `grep -c 'gradlew.*-PjvmTestsOnly' .github/workflows/sonar.yml` is `2` and
      `grep -c 'runs exactly these steps' guidelines/Build.md` is still `1`, with the row's second command now
      carrying the flag (lines 113 and 120 of the workflow; `guidelines/Build.md:121`).
- [x] 3.2 State, in the workflow's own comment above the report step, why the flag is there — the runner has no
      vcpkg tree and the step reads what the test step wrote. Verify: `bash tools/check-sonar-workflow.sh` exits
      0 and prints its summary line — `evidence/check-sonar-workflow.log`.

## 4. The gate

- [x] 4.1 Run `./tools/loop/gate.sh` once from the change root and copy every tally XML it writes into
      `evidence/`. Verify: the gate line names the suites the touched paths owe; no compiled path changed, so no
      Gradle build is owed — `LOOP-GATE: tests=53 failures=0 errors=0` (not retained as one XML, because it is
      the sum the tallies carry) over the eight shell steps
      (`evidence/gate.log`, eight tallies in `evidence/`): `tools_check-action-runtimes-selftest.sh.xml` tests=10,
      `tools_check-doc-routes-selftest.sh.xml` tests=34, `tools_check-sonar-workflow-selftest.sh.xml` tests=9, and
      the four check-only steps' XMLs at tests=0, every one of them failures="0" errors="0".
- [x] 4.2 Record the block `./tools/loop/measure.sh --change fix-sonar-coverage-step-omits-jvm-tests-only` prints
      as `evidence/measure-block.txt`. Verify: the block's `LOOP-MEASURE-SUITE cases=` line is quoted from it, not
      counted by hand — `cases=308`, `cmd=tools/loop-selftest.sh --list`, and
      `LOOP-MEASURE-CHANGE … scenarios=1 cases=1 unnamed=0`.
