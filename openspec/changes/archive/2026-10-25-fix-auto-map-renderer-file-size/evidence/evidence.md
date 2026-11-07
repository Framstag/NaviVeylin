# Evidence — fix-auto-map-renderer-file-size (item #299)

Everything below is a number taken in `$(git rev-parse --short HEAD)` = `d846c741` plus this change's
uncommitted tree, and each line names where the artifact sits beside this file.

## 1. The row's measurement — red on HEAD, clear after the split

| | command | finding |
|---|---|---|
| HEAD (clean tree, 2026-10-25T18:39:28Z) | `SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS='[{…the entry's condition…}]' bash tools/sensors/metrics/source-size.sh` | `"measurement":"2307","verdict":"violation"` — `red-probe-head.log` |
| after the split (2026-10-25T18:44:22Z, 7 changed paths) | the same probe, the same condition | `"measurement":"1009","verdict":"clear"` — `green-probe.log` |

The fingerprint is the entry's own in both:
`499ec362fe8f67b31894593536b12b284388b8e033fefd16dcf6a156593790ac`, so the clearing finding is the finding that
closes the item (`tools/sensors/metrics/source-size.sh`, the clearing pass over the open conditions). The
`red-probe-head.log` also carries the ranking the probe prints: the same run's other violation is
`MapCanvasScreen.kt` at 3 384 lines, which is another item's work.

The seven files and their sizes, taken with `wc -l`:

| file | lines |
|---|---|
| `AutoMapRenderer.kt` | 1 009 |
| `AutoMapRendererLoops.kt` | 191 |
| `AutoMapRendererMotion.kt` | 275 |
| `AutoMapRendererFollow.kt` | 143 |
| `AutoMapRendererRender.kt` | 529 |
| `AutoMapRendererOverlays.kt` | 95 |
| `AutoMapRendererMarkers.kt` | 323 |

## 2. The member roster — the invariant the split keeps

`member-roster.log` compares HEAD's 79 member functions against the union of the class's own members and the
extension functions of the six new files: 79 before, 79 after, `diff` empty. `member-roster-head.txt` is the
HEAD half as it was taken.

## 3. The code-line accounting — nothing was retyped

`line-accounting.log`, re-runnable from the project root with
`python3 evidence/line-accounting.py evidence/AutoMapRenderer-head.kt .`:

```
# member functions: before 79, after 79
roster: identical (79 names)
# code lines (non-blank, non-comment): before 1164, after 1164
code lines: multiset identical
```

The normalisation the accounting allows is the split's own transformations: the `AutoMapRenderer.` receiver
prefix of a moved declaration, the `private` → `internal` visibility of the class's own members, and the one
nested type a topic file qualifies (`AutoMapRenderer.ViewportState`). `AutoMapRenderer-head.kt` is the HEAD
copy the script reads.

## 4. The one design constraint, falsified once

`member-dispatch-probe.log`: `committedViewportCenter` — which this split keeps a member — was moved into
`AutoMapRendererOverlays.kt` as an extension, and `CarMapViewportTest` failed with
`io.mockk.MockKException: no answer found for AutoMapRenderer(#1).getOverrunLat$auto()`. Restored, the case is
green in §5. That is why the 21 members the test doubles intercept stay member functions (`design.md`, "The
constraint that decides which members stay").

## 5. The focused suite — the module the change touches

`focused-tally.log` plus the 91 `TEST-*.xml` files beside this file:
`./gradlew :auto:testDebugUnitTest -PjvmTestsOnly -PforceTests --tests 'com.naviveylin.auto.*'` → 91 XMLs
whose `tests` attributes sum to 880 cases, none failing and none erroring (the per-class numbers are in each
XML; `focused-TEST-com.naviveylin.auto.AutoMapRendererTest.xml` is the class this change's code serves most
directly). The compile step before it
(`./gradlew :auto:compileDebugUnitTestKotlin -PjvmTestsOnly --offline`) printed neither an `e:` nor a `w:` line.

## 6. The gate — one run, quoted, red on two `:app` mobile cases that are not this change's

`gate.log` is the run's own output; `gate-gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml` is the
tally the gate wrote for it:

```
LOOP-GATE: tests=0 failures=0 errors=1 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 378s) — failing: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
```

The tally's `tests=0 failures=0 errors=1` is the gate's own rule for a suite that died without a summary line
(`tools/loop/gate.sh`, the `err` fallback): a failed suite's absence of a summary is an error, not a case. What
the suite itself reported is in the same log: `2109 tests completed, 2 failed`, naming
`NavigationEngineRerouteTest.aConfirmedRerouteWritesTheLatencyDiagnosticLine` and
`BasemapSectionComposeTest.installedShowsDeleteButton`. The gate run completed the other test tasks —
`:osmscout-client-java:test`, `:core:test`, `:auto:test` (this change's module) and
`:app:testAutomotiveDebugUnitTest` — and only `:app` mobile went red.

`attribution.log` is the attribution the project's `guidelines/Build.md` §4d asks for, and
`attribution.sh` regenerates it: both classes pass alone (`BUILD SUCCESSFUL`, 34 s) —
`standalone-TEST-com.naviveylin.navigation.NavigationEngineRerouteTest.xml` records
`tests="17" failures="0" errors="0"` and `standalone-TEST-com.naviveylin.ui.mapmanager.BasemapSectionComposeTest.xml`
records `tests="4" failures="0" errors="0"` — and the whole `:app` mobile suite standalone is green over 289
XMLs, the same 2 109 cases the gate's log reports. So neither failure
is a statement about this change's code; the gate's shape is the documented contention (§2c, §6d), where
`NavigationEngineRerouteTest` is recorded failing at 4 forks and green at 1 and 2 in the same session. Each
`standalone-TEST-*.xml` carries the two classes' results from that green run; the gate's own per-module XMLs
were superseded when the same output directory was reused for the attribution runs, which is why `gate.log`
carries the gate's failing names.
