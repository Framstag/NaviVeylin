---
name: run-tests
description: Runs NaviVeylin unit and instrumented tests via Gradle — full unit suite, per-module, single test class, or on-device instrumented tests. Use when asked to run tests, verify tests pass, or check that new/modified code is covered (e.g. OpenSpec apply/archive test gates).
---

# Run tests

## When to use

- User asks to run tests or verify tests pass
- OpenSpec apply/archive guidance requires verifying existing tests still pass and new tests exist for new/modified code
- After code changes, before marking a task complete

## Procedure

1. Print a status message before running, e.g.:
   `Running unit tests…`
2. **Run in the FOREGROUND with the output redirected to a log** — redirect the *output* (that is what the
   tool's ~120s output cap applies to), never background the run. A backgrounded Gradle run (`nohup … &`)
   is **killed** when the tool call ends, because the harness kills the process group: the log stops
   mid-build with no verdict, and that looks like a failure while being a harness artifact (`TODO.md` §100;
   `setsid` is refused by the shell allowlist).
   A redirected foreground run has no such limit — a 10-minute four-module `--rerun-tasks` gate completes
   in one call:
   ```bash
   ./gradlew test > /tmp/run-tests.log 2>&1; echo "exit=$?"; \
     grep -E 'BUILD SUCCESSFUL|BUILD FAILED|tests completed' /tmp/run-tests.log | head
   ```
   Give the call a generous `timeout` (600s for a single class, 3000s for the full gate) and only grep the
   verdict back — never dump the whole log into the transcript.

   Before starting, confirm no other run is active — and make the probe able to match: `pgrep -af 'gradle-wrapper\.ja[r]'`. The `GradleWrapper[M]ain` pattern can **never** match, because the main class lives *inside* `gradle-wrapper.jar` and never appears in the argument vector, so such a guard always reports "idle" while a gate is running (`guidelines/Build.md` §2). The bracket keeps the probe from matching its own command line; an idle Gradle daemon is not a run either. Cross-check the `BUILD SUCCESSFUL|BUILD FAILED` verdict line before believing either.
3. The log ends with `BUILD SUCCESSFUL` or `BUILD FAILED` — read the verdict from the log, not from a poll loop.
4. **Evaluate by log content, not the shell tool's exit code** (for a killed run the exit code reflects the
   kill, not Gradle):
   - Log contains `BUILD SUCCESSFUL` → all tests passed
   - Log contains `BUILD FAILED` → failures; extract the `FAILED` lines (test class + method) and report them
   - Log ends mid-run with no verdict → the run was killed, not failed; restart detached and resume polling
   - **Quote tallies, not a verdict**: per-module `tests/failures/errors` from the XML (see Reports below). A bare `BUILD SUCCESSFUL` says nothing about what ran.
   - **The evidence rules are `guidelines/Build.md` §4** — what counts as a run, what a cached or restored
     module means, what a restored mutation looks like, and the per-module freshness check with the one loop
     that prints every module's newest suite `timestamp`. Operationally: force the **test tasks** with
     `-PforceTests --no-build-cache` when the run itself is the evidence (`--rerun-tasks` for the whole graph
     only when the change touched more than the tests), and when a module's XML is older than the run start,
     re-run **that** module with `--rerun-tasks --continue` and quote its fresh counts.
5. **Before a full gate, grep the suite for what the change moves** — the rule, its one documented
   instance and the reason (`fix-area-fit-zoom-rounding`'s floor change surfaced only in the full gate)
   are `guidelines/Build.md` §4. The method:
   ```bash
   grep -rn "14\.0\|MIN_AREA_ZOOM\|computeAreaZoom" app/src/test core/src/test auto/src/test | head
   ```
   Plan that expectation change (and quote old → new value in the task) instead of discovering it during
   the gate. Prefer re-expressing such an assertion against the spec (what must be visible / equal /
   ordered) over pinning a new magic number.
6. On failure, rerun a single failing test to isolate it:
   ```bash
   ./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.route.RoutePanelComposeTest"
   ```
7. **Attribute before blaming or claiming.** The rule and the full ritual are in `guidelines/Build.md` §4 (tracked) — the short form:
   - a failure that **moves between runs or flavors** and **passes alone** may belong to another in-flight change: rerun the class alone, then together with the classes you added, and quote both numbers;
   - find who ran **before** the victim from the JUnit XML `timestamp` of each `testsuite` (one fork here → a single sequence); bisect that order with `--tests` filters (pass them as **literal** arguments — this shell does not word-split variables);
   - file the residual in `TODO.md` and leave the "zero failures" task item **unchecked** rather than talking the run green.
   - `UncaughtExceptionsBeforeTest` is **not** a flaky assertion: a coroutine threw on a process-wide dispatcher (`Dispatchers.Default`/`IO`, or main) after its test ended, and the next `runTest` in that JVM reports it. Hunt the owner (a process-scoped ticker/`@Singleton` scope, or any coroutine body on a real dispatcher without `runCatching`/`CoroutineExceptionHandler`) — the same escaping throwable kills the app on a device.
8. Report the verdict: per-module tallies, failed test names, and the report location.

## Commands

| Goal | Command |
|---|---|
| All unit tests (all modules) | `./gradlew test` |
| App unit tests, one flavor | `./gradlew :app:testMobileDebugUnitTest` / `./gradlew :app:testAutomotiveDebugUnitTest` |
| Module unit tests (`:auto`, `:core`) | `./gradlew :auto:testDebugUnitTest` / `./gradlew :core:testDebugUnitTest` |
| Single test class | `./gradlew :app:testMobileDebugUnitTest --tests "<fully.qualified.ClassName>"` |
| Force a rerun (evidence) | add `-PforceTests --no-build-cache` (test tasks only) or `--rerun-tasks` (whole graph) |
| A case that fails only under load (CI's 4 vCPU) | `taskset -c 0-3 ./gradlew test -PforceTests --no-build-cache` — see Notes |
| Instrumented tests (device/emulator required) | `./gradlew connectedAndroidTest` |

`:app` has **one test task per flavor** — there is no `:app:testDebugUnitTest`. Run both for a gate:
they are separate suites and a change can pass one and fail the other. **Which flavors a run must cover**
(change `speed-up-build-test-gate`; the measured suite sizes and the reason are in `guidelines/Build.md`
§4/§6): both when the change touches `app/src/automotive/**`, a flavor manifest or resource, the flavor
definitions, or native inputs; one (`mobile`, the base flavor) for a change that touches only shared
sources — the two suites execute the same class set, so the second one is duplicate work unless a flavor
input changed. Completion still requires both flavors and every shipped ABI.

The filterable per-module tasks are **`:auto:testDebugUnitTest`** and **`:core:testDebugUnitTest`** — the
bare `:auto:test` / `:core:test` lifecycle tasks reject `--tests` with `Problem configuring task … from
command line. > Unknown command-line option '--tests'`, and `:osmscout-client-java:test` takes the plain
form. A single-class run therefore filters on the `…DebugUnitTest` task of its own module, e.g.
`./gradlew :core:testDebugUnitTest --tests "com.naviveylin.core.LocationGrantTest"`.

## Reports

- HTML: `app/build/reports/tests/testMobileDebugUnitTest/index.html` (or `testAutomotiveDebugUnitTest`)
- XML: `app/build/test-results/testMobileDebugUnitTest/*.xml` — one `testsuite` per class, with `timestamp`
  (execution order), `tests`, `failures`, `errors`. Sum them with `awk` for the per-module tallies — and
  read the newest `timestamp` as the freshness proof: an XML older than the run means that module was
  restored from the build cache, not executed (step 4).

## Coverage

Report-only, no gate. Kover reads **test-execution data**, so the report tasks reuse the last run
instead of re-running tests — unless their inputs changed, in which case they pull the test tasks (all of
which now have declared fork budgets, so that is no longer an OOM risk).

| Goal | Command |
|---|---|
| Per-module Kover report (reuses execution data) | `./gradlew :core:koverXmlReportDebug` / `:app:koverXmlReportMobileDebug` / `:auto:koverXmlReportDebug` |
| Fresh data for one module, then the report | `./gradlew :core:testDebugUnitTest --rerun-tasks` && `./gradlew :core:koverXmlReportDebug` |
| Merged + JNI module report (heavy) | `./gradlew :koverHtmlReport :koverXmlReport :osmscout-client-java:jacocoTestReport` |
| Tests without the coverage agent (iteration) | add `-PnoCoverage` to the test invocation (see `guidelines/Build.md` §7) |

- **`-PnoCoverage` detaches the Kover agent** (change `speed-up-build-test-gate`; the measured cost, the
  unchanged class set and tallies, and the mechanism are in `guidelines/Build.md` §7). It also **deletes
  that task's execution data**, so the invocation that feeds a report must not pass it — otherwise the report
  is empty or the tests run again. `> Task :core:testDebugUnitTest UP-TO-DATE` during the report task is the
  proof it reused data.
- The variant report is written to `<module>/build/reports/kover/report<...>.xml` — **not** to
  `report.xml` (that one is the older aggregate). Grep the variant file for a class:

  ```bash
  awk '/<class name="com\/naviveylin\/core\/MapStyleLoadReporter"/,/<\/class>/' \
      core/build/reports/kover/reportDebug.xml | grep -o 'type="LINE" missed="[0-9]*" covered="[0-9]*"' | tail -1
  ```
- Do **not** run coverage in the same invocation as the test gate.
- Both test-bearing modules declare their fork heap in their build script (`:app` and `:auto`:
  `maxHeapSize = "1024m"`, see `guidelines/Build.md` §6). No batching is needed for a normal run,
  including the coverage invocation (measured 2026-09-20: `./gradlew test --continue --rerun-tasks` green,
  2952 tests; coverage in one invocation green — TODO.md §33/§43).
- Batching is a **diagnostic fallback only** (isolating one class, or a machine that cannot afford the
  declared fork heap): `bash .pi/skills/run-tests/scripts/run-auto-batches.sh` (4 groups, per-batch XML
  copy-out). Never the documented procedure, and never evidence for how the suite really runs.
- A run that reports `FROM-CACHE` with no test executor did not execute anything — re-run with `--rerun`
  when the run itself is the evidence (the evidence rules: `guidelines/Build.md` §4).
- **A case that reddens only in the loaded multi-module run needs a contended runner, and the core count is the
  lever**: `taskset -c 0-3 ./gradlew test -PforceTests --no-build-cache` pins the whole invocation (daemon and
  every test JVM) to CI's 4 vCPUs, which is the shape the recorded `TODO.md` §148 reds came from
  (`fix-aggregate-run-test-flakes` used it 2026-10-10 for task 7.2's mutation run). `taskset` **is** on this
  shell's allowlist (verified 2026-10-10 with `taskset -c 0-0 echo`; an earlier task note claimed otherwise) —
  check that before declaring a load-dependent case unreproducible locally. One mutation at a time (`revert-check`):
  a run carrying two mutations cannot attribute a red, and an unfalsified guard stays **open** with its rate
  recorded rather than being marked done on a green run with the mutation in place.

## Related skills

- **`revert-check`** — when a task requires falsifying a new guard (mutation → named case must fail →
  restore → forced green). This skill runs the cases; that one owns the mutation discipline and the
  evidence format.

## Notes

- **JNI stub / classloader rule**: the rule, its mechanism and its failure mode are `guidelines/Build.md`
  §6 ("Test constraints"); the stub artifacts themselves are project facts in `AGENTS.md` ("JNI stub for
  unit tests"). Never add `@Config(sdk=...)` or `@GraphicsMode(...)` to a class that triggers that
  `System.loadLibrary` — a second sandbox config in one JVM breaks the full-suite run.
- The full unit suite (Robolectric + Compose UI tests) exceeds the shell tool's ~120s **output** cap — that is
  what the redirect is for. Run it in the foreground with `> /tmp/run-tests.log 2>&1`, never backgrounded
  (`TODO.md` §100). Instrumented tests (`connectedAndroidTest`) need a device and take long: give them a big
  `timeout` and the same redirected pattern.
- Instrumented tests need a connected device/emulator; if none is available, say so instead of running them.
- Make sure tests cover all relevant data combinations and process variants of the code under test.
- This skill is versioned with the project (`.pi/skills/run-tests/`); copy the directory to `~/.pi/agent/skills/run-tests/` to make it available across projects.
