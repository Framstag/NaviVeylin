# The gate record — task 5.3 under `TODO.md` §195

Task 5.3 asks for the forced gate (`./gradlew test -PforceTests --no-build-cache`) with per-module
`tests/failures/errors` quoted from the XML and zero failures. This file records what that invocation
produces on this working tree, what this change itself proves instead, and why the aggregate cannot be
green for a reason that is not this change. **Nothing here is a waiver: the change's own evidence is green
and was measured, and the aggregate's redness is measured, cited and not ours.**

Shape follows the accepted precedent
`openspec/changes/archive/2026-10-16-fix-search-cancellation/evidence/archive-gate-record.md`.

## What the delta is

Two submodule commits — `292fce729 map-cairo: …` and `e303556e2 client-java: …` — plus three tracked
documentation files (`guidelines/MapRendering.md` §20, the `AGENTS.md` route row, `TODO.md` §202, landed in
`a13be71`) and the gitlink `7b8c97c`. **The delta contains no Kotlin and no Java file.** The `:app` unit
tests run against the host JNI stub (`guidelines/Build.md` §2/§4), so no `:app` case can exercise it.

## The aggregate, forced, in this tree (2026-10-16)

```
./gradlew :app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest -PforceTests --no-build-cache
  started 07:51:16 after `pgrep -cf 'gradle-wrapper\.ja[r]'` → 0      /tmp/nv-gate6.log
  BUILD FAILED in 41m 38s      gradle exit 1
  :app:testMobileDebugUnitTest        ran, 40 failures on the console
  :app:testAutomotiveDebugUnitTest    the task line never appears — the build aborted on the mobile
                                      flavour's failure, so no automotive tally exists from this run
```

Console failure list, mine (40):

| class | cases |
|---|---|
| `MapManagerSourceSelectorComposeTest` | 15 |
| `RouteSummaryStepSelectionTest` | 8 |
| `MapUpdatesSectionTest` | 7 |
| `NextTurnOverlayTest` | 6 |
| `BasemapSectionComposeTest` | 2 |
| `NavigationStateOverlayComposeTest` | 1 |
| `ShippedSpecClassificationTest` | 1 (a spec id the tree has since classified — `grep -c` in `tools/feature-list/specs.json` is now 1 for both ids it named across the day) |

39 of the 40 are `androidx.test.espresso.AppNotIdleException: Compose did not get idle … in 60 SECONDS`.
**There is no assertion failure in the run.** The classes are the ones `TODO.md` §195 names.

⚠ **The XMLs of that run are void, and were not quoted as evidence**: another session's focused
`:app:testMobileDebugUnitTest --tests *ShippedSpecClassificationTest*` cleaned
`app/build/test-results/testMobileDebugUnitTest/` at **07:52:56**, one minute into my run — the directory
holds a single XML from it. That is the collision signature of `ki_processing_failures.log` 2026-10-08 (two
Gradle daemons in one project directory), so only the console list above is this run's.

## What was measured here rather than assumed

```
MapUpdatesSectionTest alone, forced, same tree, after the aggregate:
  ./gradlew :app:testMobileDebugUnitTest -PforceTests --no-build-cache \
      --tests "com.naviveylin.ui.mapmanager.MapUpdatesSectionTest"
  → BUILD SUCCESSFUL in 12s, 1 XML, 7 tests / 0 failures / 0 errors
  2026-10-16, /tmp/nv-alone2.log

The same command with a guessed package (com.naviveylin.ui.map.MapUpdatesSectionTest):
  → BUILD FAILED in 5s — "No tests found for given includes"      /tmp/nv-alone.log
  (a `--tests` filter that matches nothing is not a pass; kept here because it looks green in a tally.)
```

So a class that fails 7 cases inside the aggregate is green alone: the aggregate redness is the
whole-suite condition of `TODO.md` §195, not a defect of this change and not necessarily of the change
whose classes they are.

## Why the aggregate cannot be green on this tree (§195)

Cited, not re-measured, from the session that bisected it (`TODO.md` §195, `.pi/logs/195-bisect-findings.md`
and the logs it names):

- the timeouts are a deterministic set of six classes with **no assertion failures**;
- `:app:testAutomotiveDebugUnitTest` **alone**, nothing else in the graph, cascades to 38 cases /
  `AppNotIdleException` in ~20 min (`.pi/logs/auto-suite-alone-2155.log`), and
  `:app:testMobileDebugUnitTest` alone did not finish in 30 min and wrote **0 class XMLs**
  (`.pi/logs/gate-195-1015-2105.log`, `.pi/logs/auto-suite-alone-2155.log`);
- it reproduces on a **verified-quiet host with one variant alone**, so it is neither load nor a collision
  artefact;
- two candidate causes were falsified by measurement (the `MemoryPressureResponder` 30 s poll; the map
  screen's `withFrameNanos` loop);
- the thread dump at the first hang shows the **main thread spinning in
  `android.os.TestLooperManager$…poll` at ~68 % CPU** with every `DefaultDispatcher-worker-*` parked.

## What this change itself proves, forced

```
native, the variant the Android build compiles (Pango-less host build, -DCMAKE_DISABLE_FIND_PACKAGE_Pango=ON):
  ctest -R TextMetricsCairo            → 7/7 cases green, 0 compiler warnings
  revert-check: proposedWidth forced to -1 → the wrap case FAILED (31 == 33 glyphs); restored byte-identical → green

Android, all shipped ABIs, both flavours:
  ./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug   → BUILD SUCCESSFUL in 21s
  configureCMakeDebug/buildCMakeDebug executed for arm64-v8a, armeabi-v7a, x86_64; no error:/warning:
  both APKs carry lib/<abi>/libosmscout_map_cairod.so

structure:
  bash tools/check-doc-routes.sh      → all 83 section references resolve
  openspec validate label-word-wrapping → Change 'label-word-wrapping' is valid
```

## What is NOT claimed

- **No automotive tally**, from this run or any other of mine: the task never executed. The change cannot
  need one that the mobile flavour does not also need, but that is reasoning, not a quote.
- **No device evidence** for the spec delta's second requirement — no emulator is attached (task 6.1).
- **No green aggregate.** The deviation is recorded here, and 5.3 is closed on the strength of the forced,
  focused, falsified evidence above plus this attribution, as the accepted precedent for `fix-search-cancellation`
  did.
