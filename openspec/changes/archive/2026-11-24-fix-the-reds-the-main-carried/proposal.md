# Proposal

## Why

`main` is red at `d885082d`. Two runs on that commit failed — `Build Debug APK` run `45271114385` and `SonarCloud`
run `45271114427`, both `headSha d885082d582d51b432f6df7f055e6a5ba0675106`, started 2026-11-23T21:34:53Z — and the
reds are **eight independent causes**, not one symptom seen eight times. In none of the eight is a check wrong:
each reported correctly and the tree was short in eight places. That is why the fix is eleven files and not one
repair. The fifth, sixth and seventh were found only after the first four were pushed: the fifth sat
behind the second cause in the same job, so the earlier run never reached the step that reports it; the sixth is a
second 429 on a later commit; the seventh is a second failure in the same step as the fifth, on a later commit. The
eighth surfaced only after the sixth's retry wrapper gave up — a case's own fetch reached the network, and the
sandbox's SSL provider refused to initialise.

### 1. Maven Central answered 429 and 844 of the 979 `:auto` cases died in the fetch

`Build Debug APK` run `45271114385`, step "Build debug APK (all ABIs)", log line 4305:

```
java.io.IOException: Server returned HTTP response code: 429 for URL:
https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/14-robolectric-10818077-i7/android-all-instrumented-14-robolectric-10818077-i7.jar
```

and line 4229:

```
java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

The tally at line 80246 is `979 tests completed, 844 failed`, `BUILD FAILED in 3m 57s` at line 80270. The 844 are
`org.robolectric.internal.dependency.MavenArtifactFetcher` failing before a single assertion ran; the top classes
(`NavigationTemplateMapperTest` 64, `FavoritesScreenTest` 38, `DetailsScreenTest` 33, `SessionCarSurfaceHostTest`
27, `NavigationTripTest` 26) are the class count of the `:auto` module, not a set of related defects.

**Root cause.** The workflow caches Gradle's own directory and nothing else: `.github/workflows/build.yml`'s
"Cache Gradle" step lists `~/.gradle/caches`, `~/.gradle/wrapper` and the build-cache directory, and never
`~/.m2/repository`. `MavenArtifactFetcher.getLocalRepositoryDir()` resolves the local repository from the
`maven.repo.local` system property, else from `~/.m2`, and returns early only when the jar file is already there
— so with no `~/.m2` in the cache, every run asks Maven Central for a 144 MB jar, and a 429 fails the class that
asked for it. The class's own documentation says a repository that fails "for example with a 429 response, is
skipped", so the fetch is designed to be the last resort, not the only one.

### 2. Three named rules were reached by no glob

`tools/check-rule-routes.sh` reads `tools/doc-routes.json` as the declaration of which globs reach which document,
and reports a named rule that no glob reaches as an orphan. It named three, reproducing locally:

- `guidelines/UI.md` §8d "a value a car surface cannot edit is edited on the phone, and a car anchor restores
  from the phone." (line 145)
- `guidelines/BuildGate.md` §2b "a rotated child stream still answers every turn — read the pair before calling a
  figure a lower bound." (line 184)
- `guidelines/MapFrames.md` §13 "One displayed viewport per frame." (line 53)

**Root cause.** The rules are sound and the map was short: three rules were written into documents whose routes
did not name them. The check was right to refuse them.

### 3. `driver#theSharedNamesAreDeclared` failed on the runner's awk

`Loop self-test (tooling)` job, log line 81189: `FAIL driver#theSharedNamesAreDeclared`.

**Root cause.** The case compares `shared.tsv`'s declared rows against the derivation's rows, and the derivation's
sort was a selection sort starting at index 2 — so the first reader stayed in whatever order awk's hash iteration
produced it. The row's text therefore followed the awk build, not the names. The reader column is a set, not a
sequence, and the comparison read it as one. The failing run's log line is quoted without its assertion, so
which side disagreed on the runner is inferred, not shown; the defect reproduces locally against HEAD's
comparison (`evidence/gate-loop-selftest.txt`), where the comparison refuses the case's own fixture.

### 4. `SettingsSurfaceTest.aChangePersistsWithoutASaveAction` lost a race the scan made likely

`SonarCloud` run `45271114427`, step "Scan main", log line 551:

```
SettingsSurfaceTest > aChangePersistsWithoutASaveAction FAILED
    java.lang.AssertionError: Failed to assert the following: (ToggleableState = 'On')
    ToggleableState = 'Off'
    Selector used: ((ToggleableState is defined) && (hasAnyAncestorThat(TestTag = 'settings-row-poi-symbols')))
        at com.naviveylin.ui.settings.SettingsSurfaceTest.aChangePersistsWithoutASaveAction(SettingsSurfaceTest.kt:740)
```

Tally at line 570: `2321 tests completed, 1 failed`.

**Root cause.** The case writes `preferSymbolPoiIcons = true` to the file, opens the settings surface over the map
and asserts on its first frame that the POI-symbol switch is on. The row renders `SettingsController.settings`,
whose value starts at the defaults and only becomes the file's document when the map's start-path `load()`
publishes — and `SettingsStorage.ioDispatcher` defaults to `Dispatchers.IO`, a thread the compose rule never
advances. `advanceUntilIdle()` in `setUpMap()` therefore returned while the read was still in flight, and the
assertion read the defaults whenever the read lost the race. That is what the scan carried: **the same tree passed
the scan at `3b6a4acf` (run `45248404132`) and failed it at `d885082d`**, and no source file under `app/src`,
`core/src` or `auto/src` changed between the two commits (`git diff --stat 3b6a4acf..d885082d -- app/src core/src
auto/src` is empty). The case's last edit is `905d1dcc` (`#899`), four commits before the green run.

### 5. The hotspot row's bound fell below the largest file it watches

`Build Debug APK` run `45356035764` (commit `52eaddc3`), job "Offline gates", step "Check sensor tooling":

```
the row's bound (1500000) is not above the largest file it watches (1685967 bytes), so one change of that file is a hotspot
FAIL hotspot#theBoundIsAboveOneChangedFile
sensor-selftest: 1 of 94 cases failed
```

**Root cause.** `tools/sensors/sensors.json`'s `hotspot` row declares `threshold: 1500000` and a comment that
measured the largest file it watches at 1 419 357 bytes on 2026-11-16. The largest tracked file outside the change
tree is now `tools/loop/tests/loop-cases.sh` at 1 685 967 bytes — 1 684 242 at `d885082d` — so the bound no longer
sits above the file the row watches and one change of that file crosses it. The case is right and the row's bound
was stale: the property the case asserts is the one the row's own comment claims. The violation was already in the
tree at `d885082d`, but the same job failed earlier there — at "Check rule routes", cause 2 — so the sensor step
never ran and the red was masked by the first failure in the job. Fixing cause 2 exposed it. The mutation that
falsifies the case (`8.31`) still searched for `"threshold": 1300000,`, a value the row stopped carrying when the
bound was last taken, so its falsification matched nothing — and its replacement (`5000`) would have failed the
case's first assertion (the 6 000-byte `once.txt`), not the bound-versus-largest-file assertion the CI message
reports. The bound is therefore taken at 2 000 000: 314 033 bytes above the largest watched file, below the
churned fixture's 2 800 378, and clear of the file's own growth — it grew 266 610 bytes in the eight days since
the bound was last taken, so a bound fitted to today's size would re-break the case within days.

### 6. The unit-test classpath's download answered 429 and Gradle does not retry it

Run `45361986255` (`Build Debug APK`, head `82305af6`, created 2026-11-24T09:06:10Z) failed its "Build debug APK
(all ABIs)" job at "Run unit tests" (read with `gh run view 45361986255 --log-failed`):

```
2026-11-24T09:20:46.0623764Z > Task :app:hiltAggregateDepsMobileDebugUnitTest FAILED
2026-11-24T09:21:01.8626579Z > Could not resolve all files for configuration ':app:mobileDebugUnitTestRuntimeClasspath'.
2026-11-24T09:21:01.8628266Z    > Failed to transform nativeruntime-dist-compat-1.0.18.jar (org.robolectric:nativeruntime-dist-compat:1.0.18) to match attributes {artifactType=aggregated-jar-for-hilt, ...}
2026-11-24T09:21:01.8629941Z       > Could not download nativeruntime-dist-compat-1.0.18.jar (org.robolectric:nativeruntime-dist-compat:1.0.18)
2026-11-24T09:21:01.8632794Z             > Could not HEAD 'https://repo.maven.apache.org/maven2/org/robolectric/nativeruntime-dist-compat/1.0.18/nativeruntime-dist-compat-1.0.18.jar'. Received status code 429 from server: Too Many Requests
2026-11-24T09:21:01.8636980Z BUILD FAILED in 1m 40s
```

**Root cause.** The cache restored and the warm step worked — `Cache hit for gradle-Linux-m2-v1-707a67…` and all
three SDK jars `present:` — but the restored Gradle module cache does not hold `nativeruntime-dist-compat:1.0.18`,
a unit-test runtime dependency the cache's last successful save never resolved. Gradle's resolver does not retry
HTTP 429: a rate-limited artifact download blacklists the repository and fails the invocation
(gradle/gradle#39083). So the throttle became a red run. The same coordinate answered 429 in the prior run
`45356035764`; this run's other jobs were green, so this is the only red left in the workflow.

**Why the cache was short, measured later.** The `m2-v1` entry the run restored was created 2026-11-24T08:09:09Z
by the APK job of run `45352034292` (`@272395a0`), which was **cancelled** at 08:26:09Z — a partial save, 1 922 727
250 bytes against 2 755 978 060 for the pre-change entry. It lacks the coordinates the unit-test classpath
resolves, so every run since re-downloaded them and the throttle answered. The wrapper's window was then exhausted
in run `45385198727`'s first rerun: three attempts, ~5 minutes, three 429s on
`nativeruntime-dist-compat:1.0.16`. The partial entry was deleted (`gh cache delete 11019766323`, exit 0) so the
next run's `restore-keys` fallback takes the complete pre-change entry; the wrapper's window stays at three
attempts because a complete cache resolves nothing over the network.

### 7. A diagnostics entry that spans lines became several, and pushed the case's rows out of the page

Run `45366310469` (`Build Debug APK`, head `a11c32a6`, created 2026-11-24T09:53Z) failed its "Run unit tests" step
in `:auto:testDebugUnitTest` — `979 tests completed, 1 failed` — on
`StartupScreensTest#diagnosticsScreenShowsNewestFirst`. The retry wrapper behaved correctly: `gradlew-retry:
attempt 1 failed (exit 1) for a reason other than a 429; not retrying`. The assertion carried the whole row list
(read with `gh run view 45366310469 --log-failed`):

```
java.lang.AssertionError: the startup screen never published 3 log rows: [This log stays on this device. …,
CarContext(#4427).getOnBackPressedDispatcher()))), CarContext(#4427).getString(any(), VarargMatcher(all=true,
…)), CarContext(#4427).getString(any())), CarContext(#4427).getResources()), [2026-11-24 09:53:19.991] SCREEN
list capacity 6 rows (library default, no car API level yet: io.mockk.MockKException: no answer found for
CarContext(#4427).getCarAppApiLevel() among the configured an, [2026-11-24 09:53:19.976] SCREEN list capacity 6
rows (library default, below car API 2), More (3)]
```

**Root cause.** The screen's own capacity measurement is a diagnostics line, and the exception it quotes is a
multi-line MockK message. `DiagnosticsLog.readEntries()` reads the file with `readLines()`, so that one entry
became five lines — its head plus four continuation lines — and the file's newest six lines were two `SCREEN`
lines and four fragments. The screen's first page is the newest six entries, so the case's three rows (`A first`,
`B second`, `C third`) fell behind `More (3)` and the awaited state never arrived. The same defect truncates a
log the user exports: the retention pass ages a line by its own timestamp and drops a line it cannot parse, so a
continuation line is deleted and its head survives alone.

## What Changes

- **`.github/workflows/build.yml` and `.github/workflows/sonar.yml`** — the "Cache Gradle" path list gains
  `~/.m2/repository` and its key gains a versioned `m2` component (a key is saved only when absent, so an
  unchanged key would restore the old, `.m2`-less entry), and a new step "Warm the Robolectric SDK jars" fetches
  the three coordinates the suite asks for (`5.0.2_r3-robolectric-r0-i7`, `14-robolectric-10818077-i7`,
  `16-robolectric-13921718-i7`) into the local repository with `curl --retry`, skipping a jar that is already
  present. A failed warm is a `::warning::`, never a failure: the test-time fetch stays as the fallback, and the
  `mv` into place is guarded so a read-only cache volume warns rather than aborting the build under `run:`'s
  `bash -e`.
- **`tools/doc-routes.json`** — the three rules gain the rows of the routes whose globs reach their documents; two
  of the three are read through two routes each, so each gets a row in both.
- **`tools/loop/tests/loop-cases.sh`** — the derivation sorts from the first reader and the declared reader column
  is normalised into the same order, so the comparison reads the column as the set it is; a new case,
  `driver#theReaderOrderIsNotPartOfTheComparison`, writes a row in descending order and asserts no refusal.
- **`core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt`** — `timestamped` folds each run of newlines, and
  the indentation around it, into ` | ` before it stamps the line: the seam every write goes through (the
  worker's batch, the crash path's direct write, the retention pass's own report), so one logged entry is one
  line in the file.
- **`core/src/test/java/com/naviveylin/core/DiagnosticsLogTest.kt`** — the case that fails on HEAD: a message
  carrying `\n` and `\r\n` is one entry whose parts are all kept.
- **`app/src/test/java/com/naviveylin/ui/settings/SettingsSurfaceTest.kt`** — the controller's storage runs on the
  compose rule's dispatcher, so the start-path read is part of the queue `advanceUntilIdle()` drains, and
  `awaitStored` drains on every poll instead of once before the loop.
- **`tools/sensors/sensors.json`** — the `hotspot` row's bound rises from 1 500 000 to 2 000 000 and its comment
  re-measures the largest watched file (1 685 967 bytes, 2026-11-24), the growth since the bound was last taken,
  and why the bound stays below the churned fixture's score.
- **`tools/sensors/tests/sensor-mutations.sh`** — mutation `8.31` searches for the bound the row now carries and
  replaces it with 1 000 000, so the red lands on the bound-versus-largest-file assertion the CI message reports
  rather than on the fixture's first assertion.

**Not changing:** no native source, no dependency, no persisted schema. The one compiled production file the
change edits is the diagnostics log's write seam (cause 7), and its case is the suite's own: the fold is the
behaviour the file's line format already assumed. The settings
file keeps its shape and the surface's behaviour is unchanged — the row renders the controller's published
document, which is the design; the test now waits for the document instead of racing it. No device evidence is
claimed for that sentence: the change records a named exclusion for device verification.

## Capabilities

No requirement's text changes. Each of the eight checks enforces a requirement that is already correct, and each
failure was the tree not meeting it: `unit-test-suite-runtime`'s "A module's failure set is independent of host
load and concurrency" (causes 1 and 4), `documentation-ownership`'s "The review's reading route names every rule"
(cause 2), the loop suite's own determinism contract (cause 3), `backlog-sensors`' "One change of a file the row
watches is not a hotspot" (cause 5). Causes 6, 7 and 8 are not a check refusing a tree: cause 6 is an external
throttle on a workflow's own dependency download, cause 7 is the `auto-diagnostics` storage and reading
requirements' own assumption — one logged entry is one line in the file — which the write seam did not meet, and
cause 8 is the same independence requirement as cause 4, this time in a case whose subject is a registry refresh:
the case's registry carried a real fetcher, so the refresh left the sandbox. The
delta is the tree, not the contract, so
this change carries no `specs/` delta — the same shape as `fix-map-data-version-mismatch` and
`fix-phone-pan-display-window`.

Cause 4's case is a formerly load-dependent one, so the same capability's "A formerly flaky case is decided by
its subject" scenario is answered by measurement rather than by argument: three forced repeats of the class and
the full 2321-case `:app` module suite, all green, tallies in `evidence/gate-settings-surface.txt`.

One invariant is genuinely new and carries no gate: the warm step's presence in both workflows. It cannot be
falsified without a 429, so its falsification is the CI run's own evidence rather than a local case; that is
recorded as a named exclusion in `design.md` and `tasks.md`.

Cause 6 is not a check refusing a tree: it is an external throttle on a workflow's own dependency download, and
the answer is a retry wrapper rather than a tree edit. The wrapper's invariant — a `429` is retried and any other
failure is not — is falsifiable offline, and its five stub runs are quoted in `evidence/gate-gradle-retry.txt`.

Cause 7 is the one compiled production change: the diagnostics log's write seam. Its invariant — one logged
entry is one line in the file — is what the file's own reader (`readLines()`) and its retention pass already
assume, so the requirement is the shipped `auto-diagnostics` one and the tree is what did not meet it. The new
case fails on HEAD and passes after the fold.

The scan's red item #1024 (`broken-scan`, condition `{job: sonar, step: Run unit tests}`) is the same settings
failure cause 4 fixes; its closure follows the next green scan, not a separate change.

### 8. The source-switch case's registry reached the network

`Build Debug APK` run `45380057626` at `5e8ebbd6` failed its `Run unit tests` step on
`MapManagerSourceSwitchPlanTest > aSwitchThatKeepsTheTargetsBasemapKeepsItRegistered` with
`java.lang.NoClassDefFoundError: Could not initialize class sun.security.ssl.NamedGroup$SupportedGroups`, thrown
from `SSLConfiguration.<init>` via `SSLSocketFactoryImpl.createSocket` and `HttpsClient` and
`HttpURLConnection.getResponseCode` in `HttpUrlFetcher$text$2.invokeSuspend` (`HttpUrlFetcher.kt:65`). The case's
fixture built `MapSourceRegistry(settings, provider, HttpUrlFetcher())` — a real fetcher — so the registry's
refresh of the repository's region index opened a live HTTPS connection to the repository's host, and the
sandbox's SSL provider could not initialise. The red was hidden behind cause 6: the run's own log shows the
retry wrapper's two attempts (`retrying in 90s (attempt 1 of 3)`, `retrying in 180s (attempt 2 of 3)`), and the
case only reached the network once those attempts gave up.

The case needs no listing: it asserts that a switch keeping the target's basemap keeps it registered. The fix is
the double the same package's other cases already use — `OfflineRepositoryFetcher()` — so the refresh never leaves
the sandbox. The `HttpUrlFetcher` import the fixture no longer needs is removed.

## Impact

**Files**

- `.github/workflows/build.yml` — one cache path, one key component, one step, one wrapped invocation.
- `.github/workflows/sonar.yml` — one cache path, one key component, one step, one wrapped invocation.
- `tools/gradlew-retry.sh` — new, 58 lines: the retry wrapper.
- `tools/doc-routes.json` — 6 added lines, 1 changed.
- `tools/loop/tests/loop-cases.sh` — 33 added lines, 2 changed.
- `app/src/test/java/com/naviveylin/ui/settings/SettingsSurfaceTest.kt` — 1 changed fixture helper, 1 changed
  poll helper.
- `tools/sensors/sensors.json` — 1 changed bound, 1 re-measured comment.
- `tools/sensors/tests/sensor-mutations.sh` — 1 changed search text and replacement.
- `core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt` — 1 changed seam: the fold in `timestamped`.
- `core/src/test/java/com/naviveylin/core/DiagnosticsLogTest.kt` — 1 added case.
- `app/src/test/java/com/naviveylin/ui/mapmanager/MapManagerSourceSwitchPlanTest.kt` — 1 changed fixture call and
  1 removed import: the registry's fetcher is the offline double.

**One** compiled production file (`DiagnosticsLog.kt`), no manifest, no dependency, no ABIs, no strings, and no
Gradle input. The change's own case for that file runs in its module's suite; the gate is run because the
delta touches a compiled production file.

**Rollback** is one `git revert` of the twelve commits (`git rev-list --count d885082d..HEAD`): the cache path and the warm step are additive, the route
rows are additive, the sort is a comparison, the test's dispatcher swap is confined to one fixture, the sensor
row's bound is one number its comment and its mutation name, the retry wrapper is one new file two workflow
steps call, and the diagnostics fold is one expression in one seam.

**Landing.** At `c76b38f9` both workflows are green: `Build Debug APK` run `45385198727` and `SonarCloud` run
`45385198606`, each `conclusion: success`, each started 2026-11-24T11:33:03Z for that `headSha`. The base
`d885082d` was red in both. The runs' facts are `evidence/gate-green-runs.txt`.

**Issue:** `#1048` "the main build is red" (sensor `broken-main`, fingerprint `{job, case}`); the settings red is
`#1024`'s "the scan of main is red" (sensor `broken-scan`).

surface: 11 files, 276 insertions, 14 deletions, 1 compiled production file — 2 workflows, 1 retry wrapper, 1 route map, 1 loop test file, 1 sensor registry, 1 sensor mutation list, 3 test files, 1 diagnostics source file. Counted with `git diff --numstat d885082d..HEAD`.
