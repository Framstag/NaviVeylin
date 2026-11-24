# Design

Eight causes, eight decisions. Each decision names the alternatives the root-cause evidence eliminated — a viable
alternative would mean the root cause was not established. The fifth, sixth and seventh causes were found after
the first four were pushed: the fifth sat behind the second cause in the same job, so the run that reported the
first four never reached the step that reports it; the sixth is a second 429 on a later commit; the seventh is a
second failure in the same step as the fifth, on a later commit. The eighth was found after the sixth's fix was
pushed: the retry wrapper's attempts gave up and the case behind them reached the network.

## D1 — Warm the Robolectric jars instead of turning the fetch off

**Chosen.** Add `~/.m2/repository` to the "Cache Gradle" path list in both workflows, and a step "Warm the
Robolectric SDK jars" that `curl`s the three coordinates into
`$HOME/.m2/repository/org/robolectric/android-all-instrumented/` before Gradle runs. A jar already present is
skipped; a completed download is `mv`ed from `.part` into place, because `MavenArtifactFetcher.fetchArtifact`
trusts the file's existence and a truncated file would read as a fetched one. A failed warm is a `::warning::`,
so the test-time fetch stays as the fallback and a Maven Central outage cannot fail the build on its own.

Two halves make the cache work, and neither alone does. The cache key gains a versioned `m2` component
(`key: …-m2-v1-${{ hashFiles(…) }}`): `actions/cache` saves a key only when it is absent, so a key unchanged
since before this change would restore the old, `.m2`-less entry and the jars would never be saved. The version
is bumped when the warm list's coordinates change, because a saved entry keeps the jars it holds. The step's
`mv` is guarded too: `run:` steps execute under `bash -e`, so an unguarded move would abort the step — and the
build — on a read-only or full cache volume, against the step's own "warns and lets the run continue" contract.

**Eliminated.**

- *Set `robolectric.offline=true` (or `robolectric.offline` in the test JVM's system properties).* Impossible as
  the fix: it makes the fetch fail hard instead of falling back, so a runner whose cache misses loses the whole
  suite rather than one class. It also requires every developer's `~/.m2` to be populated before the first run,
  which is a machine-local precondition the build does not have today.
- *Retry the fetch inside the test JVM.* `MavenArtifactFetcher` is Robolectric's own class, not this project's;
  the alternative is a patched Robolectric or a `ServiceLoader` override, i.e. a dependency fork for a cache
  miss.
- *Vendor the three jars into the repository.* Each is 144 MB and the three together exceed a sane checkout; the
  project already treats the native toolchain's artifacts as cache content, not source.
- *Cache `~/.m2` alone without a warm step.* The cache only helps from the second run on, and the first run after
  a key change is exactly the run that 429s. Both halves are needed: the cache keeps the jar, the warm step
  creates it on a cold key.

**Threading.** None. The warm step runs in the workflow's shell, before Gradle.

**Risk.** A Robolectric bump renames the artifact, and the warm step's list would then name a jar nothing asks
for — the step warns and the test-time fetch asks Maven Central. The list is three coordinates in one `for` loop
with a comment naming the modules that resolve them (`:core` 21 / `:auto` 34 / `:app` 36), but it is copied into
two workflows, so a bump edits the list in both and bumps the cache key's version in both.

## D2 — Add the missing rows to the route map, not the rules to the documents

**Chosen.** `tools/doc-routes.json` gains the three rules' rows on the routes whose globs reach their documents.
Two of the three are read through two routes each (`app/src/main/java/**` and their own concern's globs), so each
gets a row in both.

**Eliminated.**

- *Rewrite or remove the three rules.* The rules are sound and each is stated in the section that owns it; the
  orphan check exists because a rule nobody reads is a rule nobody enforces, and deleting the rule is the failure
  the check prevents.
- *Widen a glob so it reaches the documents.* A glob wider than its route's concern is refused by the same
  requirement ("a glob over `guidelines/` SHALL name the document … and SHALL NOT name the whole corpus").
- *Exempt the three rules in the check.* A per-rule exemption list turns the check into a list of exceptions and
  leaves the rules unreached.

**Threading.** None.

## D3 — Compare the reader column as a set, on both sides

**Chosen.** The derivation's selection sort starts at the first reader (`i = 1`), and the declared reader column
is normalised into the same order before the two are compared. A row whose readers are written in any order
declares the same share. The new case `driver#theReaderOrderIsNotPartOfTheComparison` writes
`shared_thing\ta.sh\td.sh c.sh b.sh` and asserts no refusal.

**Eliminated.**

- *Rewrite `shared.tsv`'s rows into ascending order.* It hides the defect instead of fixing it: the comparison
  would still depend on the derivation's order, and the next row written in another order would fail again. The
  column is a set by construction — a file's readers are not a sequence — so the comparison is what is wrong.
- *Sort the derivation only (`i = 1`) and leave the declared column as written.* The case would then fail on a
  row written in descending order, which is what the new case proves. Both sides or neither.
- *Sort with a locale-aware tool.* `sort` and `awk`'s `<` are byte-wise; the names are ASCII and the comparison
  must not depend on the runner's locale (`locale` is not in the shell allowlist, and the loop's own bound is
  "offline, no locale"). Byte-wise is the deterministic choice.

**Threading.** None. The comparison is two `awk` programs in one shell function.

**Risk.** The normalisation adds one `awk` pass per row set. The revert-check mutates the declaration-side sort
only: reverting the derivation's `i = 1` cannot fail locally, because the case's fixture row is already in the
derivation's order, so the derivation-side change is proven by the red-on-HEAD slice rather than by a mutation.
`driver#noEngineFileExceedsItsBound` still passes,
but it does not bound this file: `tools/loop/layout.tsv` has no row for `tools/loop/tests/loop-cases.sh`, and
`layout_bound_refusals` iterates table rows only. The bound is unchanged because no row declares one — not
because the case measured it.

## D4 — Give the settings case's storage the rule's dispatcher

**Chosen.** `setUpMap()` builds the controller's `SettingsStorage` and assigns
`ioDispatcher = mainDispatcherRule.dispatcher`, and `awaitStored` drains the scheduler on every poll. The
start-path read is then a queue task `advanceUntilIdle()` drains, so the row renders the persisted document
deterministically; the write the row makes is drained by the same rule.

**Eliminated.**

- *Wait for the row's value in the case's first assertion.* It would make the case tolerant rather than
  deterministic — a 5-second window in which a loaded runner still fails — and it leaves the fixture's
  threading wrong for every other case in the class.
- *Assert against the file instead of the row.* The case's point is that the row shows the change; asserting the
  file would stop proving the surface reads the document.
- *Change the product so the surface renders the file's document synchronously.* The surface deliberately renders
  the controller's published document, and the controller's "the flow's first emission is the defaults this state
  already holds" is the design: the row's write goes through `SettingsStorage.update`, which transforms the
  persisted document, so a stale first frame cannot lose a change. The race is the test's, not the product's.
- *Give the whole class the test dispatcher.* Every other case in the class reads and writes the file through a
  fresh `SettingsStorage` inside `runBlocking`, which needs a real dispatcher; the swap is confined to the
  long-lived storage the controller holds.

**Threading.** The controller's storage runs on the compose rule's `StandardTestDispatcher`; the file reads the
class's own cases make stay on `Dispatchers.IO` through a fresh instance. `awaitStored` drains the rule's queue
before each poll so the row's write lands before the file is read.

**Known limit.** The same seam stays open one line away in this fixture: `darkModeController` and the route
panel's `settingsStorage` are still built on the default `Dispatchers.IO`, so the same first-frame race remains
reachable for those surfaces. They are not failing today; this change fixes the case that failed.

**Risk.** A case that assumed the storage's write was already on disk when the assertion ran now needs the drain;
`awaitStored` provides it on every poll. The class's 23 cases pass, and because this case is a formerly
load-dependent one, `unit-test-suite-runtime` "A formerly flaky case is decided by its subject" is answered by
measurement: three forced repeats of the class (`-PforceTests --no-build-cache`) and the full 2321-case `:app`
module suite, all green, tallies in `evidence/gate-settings-surface.txt`.

## D5 — Raise the row's bound and re-measure its comment

**Chosen.** The `hotspot` row's bound rises from 1 500 000 to 2 000 000, its comment re-measures the largest file
the row watches (1 685 967 bytes, `tools/loop/tests/loop-cases.sh`, 2026-11-24), the growth since the bound was
last taken (266 610 bytes in eight days, 64 commits touching the file) and why the bound stays below the churned
fixture's score (2 800 378), and mutation `8.31` searches for the bound the row now carries instead of the
`1300000` it stopped carrying. 2 000 000 sits 314 033 bytes (18.6 %) above the largest watched file and 800 378
below the fixture's score, so both halves of the case's assertion stay satisfiable — the bound is above one change
of any watched file and below the churned path's seven-change score. The headroom is the point: at the 33 326
bytes a day the file has grown since the bound was last taken, a bound fitted to today's size (1 800 000 leaves
114 033 bytes) would re-break the case within four days.

The mutation's replacement matters as much as its search text. The old `5000` would have failed the case's
**first** assertion — the 6 000-byte `once.txt` is a hotspot at 5 000 — so the red would not have been the
bound-versus-largest-file assertion the CI message reports. `8.31` now replaces the bound with 1 000 000: the
fixture's `once.txt` (6 000) stays under it, its `churned.txt` (2 800 378) stays over it, and the case fails on
the largest-file assertion with the CI message's own words.

**Eliminated.**

- *Split `tools/loop/tests/loop-cases.sh` so the largest file shrinks.* A real option and the honest one if the
  file keeps growing — but it is a refactor of the loop suite's case file, with its own diff, its own review and
  its own 543-second full-suite run, not a red-run repair. The row's contract is that its bound sits above the
  largest file it watches; raising the bound is what that contract asks for.
- *Exclude `loop-cases.sh` from the row's watch set.* It would hide the largest source in the tree from the row
  that measures source size. The row's exclusion is the change tree, and widening it to the file that is the
  reason for the bound inverts the row's purpose.
- *Leave the bound and let the row file an item.* The case is the gate: it refuses a bound that one change of a
  watched file can cross, so the row cannot be exercised with a stale bound. The bound is a parameter the case
  owns, not a condition the row reports.
- *Set the bound to 1 700 000 or 1 800 000.* They clear today's largest file by 14 033 and 114 033 bytes — one
  case's worth of headroom and four days' worth — so the next few cases added to the file re-break the same case
  and the same red returns. The bound is raised once, with room for the growth the file has shown.
- *Leave the bound where the file's growth rate points and split the file first.* The split is the honest answer if
  the file keeps growing, but it is a 543-second suite's worth of restructuring, not a red-run repair; it stays
  named here as the next step if the bound is reached again.

**Threading.** None. The bound is a number in the registry and a search text in the mutation list.

**Risk.** A commit that pushes `loop-cases.sh` past 2 000 000 re-breaks the case; that is the case doing its job,
and it fails loudly in the offline gates rather than silently. The headroom buys about nine days at the observed
growth rate; if the bound is reached again, the split named above is the fix, not another raise.

## D6 — Retry the invocation when its log names a 429

**Chosen.** A new wrapper, `tools/gradlew-retry.sh`, runs a command and re-runs it only when the failed attempt's
log names a Maven Central `429`; the attempt count and the backoff are bounded (3 attempts, 90 s doubling). Both
workflows' "Run unit tests" step runs through it. That step is the one Gradle invocation in either workflow that
resolves the unit-test runtime classpath — the fetch that answered 429 in runs `45356035764` and `45361986255`.

**Why a retry and not a cache fix.** The cache restored and the warm step worked; what is missing is a jar no
successful save has held. Making the cache hold it would require a save on a run that resolves the classpath,
which is this step's own run — so the first green run has to happen without the jar. A throttle is answered by
waiting and asking again.

**Eliminated.**

- *Warm the jar like the three SDK jars.* The warm step's `curl` list is read by the test-time fetcher, not by
  Gradle's resolver, and Gradle resolves this coordinate from its own module cache. A file in `~/.m2` would not
  be read.
- *Let the cache hold the jar by saving on a failed run.* `actions/cache` saves a key only when it is absent. The
  key already exists, so a save would be refused — and a run that does not complete does save: the `m2-v1` entry
  the runs restored was written at 08:09:09Z by the APK job of run `45352034292`, which was cancelled at
  08:26:09Z. That partial save is 1 922 727 250 bytes against the pre-change entry's 2 755 978 060, and it is what
  every later run restored and re-downloaded from.
- *Wrap every Gradle invocation.* The assemble, SBOM and licence steps resolve only what the cache already holds
  — they were green in the red run — so the wrapper's extra attempt would buy nothing there and hide a real
  failure behind a second try.
- *Retry on any failure.* A retry that fires on a compile error or a failing test masks a defect and doubles the
  run's cost. The wrapper retries only a log that names a 429.

**Companion action, not a code change.** The partial `m2-v1` entry was deleted (`gh cache delete 11019766323`,
exit 0) so the next run's exact key misses and the step's `restore-keys: gradle-${{ runner.os }}-` fallback takes
the complete pre-change entry. The key's save-once rule then keeps a complete entry from being overwritten by a
partial one. No workflow edit: the fallback was already there, and the alternative — gating the save on success —
would need a restore/save pair in both workflows for a failure mode the deletion already answers.

**Risk.** A `429` that outlives three attempts still fails the run; that is the throttle outlasting the wrapper,
and the failure is reported with the same message as before. The wrapper's own failure mode — a non-429 exit that
is retried — cannot happen: the log check is the only path to the next attempt. The window stays at three attempts
because a complete cache resolves nothing over the network: the first rerun of run `45385198727` spent the window
on three 429s for a coordinate the partial cache lacked.

## D7 — Fold a diagnostics line to one line at the log's own seam

**Chosen.** `DiagnosticsLog.timestamped` folds every run of newlines — and the indentation around it — into
` | ` before it stamps the line, so one logged entry is one line in the file. The seam is the one place every
write goes through: the worker's batch, the crash path's direct write and the retention pass's own report.
The invariant cannot be bypassed by a caller.

**Why the seam and not the caller.** The red run's own log shows the mechanism. `CarListCapacityReader.report`
logs `list capacity 6 rows (library default, no car API level yet: <exception>)`, and the exception's
`toString()` is a multi-line MockK message. `readEntries()` reads the file with `readLines()`, so the one
entry became five rows — the head plus four continuation lines — and the diagnostics screen's own page (the
newest 6 entries) filled with those fragments. `StartupScreensTest#diagnosticsScreenShowsNewestFirst` then
could not find its three rows in the page; the assertion's title list is the red run's evidence (run
`45366310469`). Folding in the reader would fix the one caller that happened to be red and leave the next.

**Why it matters beyond the case.** The retention pass ages a line by its own timestamp and drops a line it
cannot parse (`aged++`); a continuation line carries no timestamp, so the pass deletes it and leaves the head
behind — a truncated entry in a log the user is asked to export. The invariant the file already relies on is
"one entry, one line"; the seam now enforces it.

**Eliminated.**

- *Fold in `CarListCapacityReader`.* Fixes the one caller that happened to be red; the crash path and any
  future multi-line message keep the defect.
- *Strip newlines in the retention pass.* The pass runs once a day and a reader sees the fragments before it
  does; the file would still hold a multi-line entry.
- *Give the diagnostics screen a bigger page.* The page is the host's row capacity and the case's three rows
  are the *oldest* of the window, so a bigger page only moves the failure.
- *Change the case's assertion to tolerate the fragments.* The case would stop asserting the thing it is
  named for — that the screen shows the entries newest-first.

**Risk.** A message with a deliberate multi-line layout loses its line breaks in the file; the logcat mirror
keeps them (`Log.d`/`Log.e` are written before the fold), and the file's own format is one line per entry, so
the export stays greppable. `stackDetail` already flattened stack frames with ` | ` for the same reason.

## D8 — Give the source-switch case an offline fetcher

**Chosen.** Build the case's `MapSourceRegistry` with the package's own `OfflineRepositoryFetcher()` instead of
`HttpUrlFetcher()`, and drop the now-unused import.

**Eliminated.** *A fake SSL provider in the test JVM*: the case's subject is the source-switch plan, not the
fetch, so making the sandbox's provider initialise would leave a case that still opens a socket — the same
network dependence, one layer down. *A skip when the provider is uninitialised*: a skip hides the dependence and
reports a green run for a case that did not run. *A retry of the whole step*: the throttle is what the retry
wrapper answers (D6); the `NoClassDefFoundError` is not a throttle, so retrying it would only spend the window
twice and land on the same failure.

**Why the double is the right subject.** The case asserts that a switch keeping the target's basemap keeps it
registered; neither that assertion nor its neighbours in the package needs a listing. The other cases in the same
package already build the registry with `OfflineRepositoryFetcher()` or `FakeRepositoryFetcher`, so the real
fetcher was the outlier.

**Risk.** The case no longer exercises the real fetcher's error path. Nothing in the case's assertions read that
path, and the fetcher's own behaviour is not this change's subject.

## Open decisions

none

## Verification

| Cause | Verification | Where |
|---|---|---|
| 1 | `yq -e .` on both workflows; `tools/check-sonar-workflow.sh`; `tools/check-action-runtimes.sh`; the warm step extracted, `bash -n`ed and run twice — the skip branch over the real `~/.m2`, the fetch branch over a scratch base with the step's own text — plus `curl --head` on the three URLs; the cache key's `m2-v1` component | `evidence/gate-workflows.txt` |
| 2 | `tools/check-rule-routes.sh` — every glob matches the tree and all 658 named rules are reached; `tools/tests/check-rule-routes-selftest.sh` — 13 cases | `evidence/gate-rule-routes.txt` |
| 3 | `tools/loop/tests/loop-selftest.sh` — 548 cases; the two named cases; the red-on-HEAD and mutation proofs, reproduced by `/tmp/red-proof.sh` | `evidence/gate-loop-selftest.txt` |
| 4 | `:app:testMobileDebugUnitTest --tests '*SettingsSurfaceTest'` — 23 cases, 0 failures; three forced repeats of the class and the full 2321-case module suite, all green | `evidence/gate-settings-surface.txt` |
| 5 | `tools/sensors/tests/sensor-selftest.sh` — all 94 cases; `tools/sensors/tests/sensor-mutations.sh --only 8.31` — the case red under the mutation, green after the restore; `jq -e .` on the registry; the largest watched file measured | `evidence/gate-sensors.txt` |
| 6 | `bash -n tools/gradlew-retry.sh`; five stub runs — a plain success, a 429 followed by success, a non-429 failure, a 429 that outlives the attempts, and no argument; `yq -e .` on both workflows; `tools/check-sonar-workflow.sh`; `tools/check-action-runtimes.sh`; `tools/check-portability.sh` | `evidence/gate-gradle-retry.txt` |
| 7 | `:core:testDebugUnitTest --tests '*DiagnosticsLogTest'` — the new case red on HEAD (`8 tests completed, 1 failed`) and green after the fold; `:auto:testDebugUnitTest --tests '*StartupScreensTest'` — green; the red run's title list, quoted | `evidence/gate-diagnostics-line.txt` |
| 8 | the full gate, `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` — BUILD SUCCESSFUL, 6 449 tests in 860 result files across the five modules the run executed, 0 failures, 0 errors | `evidence/gate-full.txt` |
| 9 | `:app:testMobileDebugUnitTest --tests '*MapManagerSourceSwitchPlanTest'` and `:app:testAutomotiveDebugUnitTest --tests '*MapManagerSourceSwitchPlanTest'` — 9 tests, 0 failures, 0 errors in each flavor; the red run's failure title and stack quoted | `evidence/gate-source-switch.txt` |
| — | the landing: `Build Debug APK` run `45385198727` and `SonarCloud` run `45385198606`, each `success` at `c76b38f9`; the Build run's five jobs all green, its three previously-red steps green, its step-23 census 6 449 tests / 0 failures / 0 errors, `grep -c 'status code 429'` and `grep -c 'NamedGroup'` both `0` | `evidence/gate-green-runs.txt` |

**Named exclusions.** The warm step's invariant (D1) has no local falsifier: deleting the step is only observable
as a 429, so its evidence is the CI run's own — run `45385198727`, whose "Cache Gradle" step missed the deleted
`m2-v1` key and whose warm step fetched all three jars without a warning (`evidence/gate-green-runs.txt`). The settings case's race (D4) cannot be reproduced on this
machine — the read wins here — so its red evidence is the two runs' outcomes on the same tree, not a local run.

The `:app` module suite is the measured one; `testAutomotiveDebugUnitTest`, the same source set which the
workflow's `test` job also runs, is not run locally — the changed test file is shared, so the CI's automotive run
is the proof for that variant.

No device verification: nothing here changes a pixel, a template, a frame or a string.

The hotspot bound's red direction is proven by mutation `8.31`'s red cycle rather than by restoring the old
bound: the mutation sets the bound to 1 000 000 and the case fails with the CI message's own words — the bound is
not above the largest file it watches — then the restore makes it pass. The CI message is quoted as the report
that found the red, not as its proof.

The diagnostics case's own red is a CI-only red: it passes locally before and after the fold (two runs on the
same tree), so the fold's red-first proof is the unit case, and the case's mechanism is the red run's title
list — the five rows the screen published instead of its three entries.

The retry wrapper's *behaviour* is falsifiable offline (the five stub runs in `evidence/gate-gradle-retry.txt`);
its *effect* — that a throttled fetch no longer reddens the run — is only observable in CI, and that sentence's
evidence is the workflow's next green run: run `45385198727` ran the suite once through the wrapper and printed
`BUILD SUCCESSFUL in 9m 46s` with no 429 in its log.

The source-switch case's red direction (D8) is CI-only: the case passes on this machine before and after the
fix, because nothing here throttles the request the real fetcher makes. Its red evidence is the failing run's
title and stack, quoted in `evidence/gate-source-switch.txt`; its green evidence is the two filtered runs of the
class in both `:app` flavors.
