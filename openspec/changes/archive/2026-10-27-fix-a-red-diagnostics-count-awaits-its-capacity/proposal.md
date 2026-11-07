# A red diagnostics count is read once the screen has measured its page

## Why

Item **#675** ("the main build is red", fingerprint `53c0007a7cddde70`, detected by
`tools/sensors/conditions/broken-main.sh`) records a red `test` job for two `:auto` cases:
`com.naviveylin.auto.DiagnosticsScreenTest.thePageRowOpensTheNextSliceOfTheWindow` and
`com.naviveylin.auto.StartupScreensTest.diagnosticsScreenShowsNewestFirst`. The run itself
(`gh run view --job 121789440446 --log`, `Build Debug APK` on `4a4b3a9554dc25fee6fa9cb441b6d2f0ec5c5a46`,
2026-10-26T19:40Z) names three failures in one suite — `880 tests completed, 3 failed`:

```
DiagnosticsScreenTest > thePageRowOpensTheNextSliceOfTheWindow FAILED
    java.lang.AssertionError: the page presents the next capacity-many rows of the window
      expected:<10> but was:<8>            (DiagnosticsScreenTest.kt:267)
DiagnosticsScreenTest > theDiagnosticsListFitsTheHostsCapacity FAILED
    java.lang.AssertionError: the page holds the host's 8 rows between the disclosure and its page row
      expected:<10> but was:<8>            (DiagnosticsScreenTest.kt:228)
StartupScreensTest > diagnosticsScreenShowsNewestFirst FAILED
    java.lang.AssertionError: expected:<4> but was:<8>   (StartupScreensTest.kt:115)
```

**Root cause, one mechanism: `DiagnosticsScreen` decides its row count in a main-looper coroutine the
cases never await, and its two effects both move the count they assert.** `rowCapacity` starts at the
library default 6 (`auto/src/main/java/com/naviveylin/auto/DiagnosticsScreen.kt:58`) and is replaced only
when `readRowCapacity()` — launched right after `loadEntries()`, not before it
(`DiagnosticsScreen.kt:62-63`, `:81-88`) — returns from `Dispatchers.Default`. So:

- the two `DiagnosticsScreenTest` counts read the template while the page is still built from the default
  6, which is `1 + 6 + 1 = 8` rows instead of `1 + 8 + 1 = 10` (`DiagnosticsScreen.kt:126`);
- the same reader writes its measurement into the log file the window is read from
  (`CarListCapacityReader.kt:76`, one line per screen start), so `diagnosticsScreenShowsNewestFirst` — which
  counts the window's total — reads 4 rows (its 3 entries plus the screen's own line) or more, and the CI
  run read 8 because a screen an earlier case never released wrote into this case's file as well:
  `carScreenScope` is cancelled only in `onDestroy` (`DiagnosticsScreen.kt:47`, `:67-72`) and
  `DiagnosticsScreenTest`'s teardown released nothing (`DiagnosticsScreenTest.kt:81-85` before this change).

**Red on HEAD**, the two conditions the CI run met reproduced on the base tree with the assertions
untouched: the fake host's answer held back (`answerGate.await()` in the capacity stub) and one diagnostics
line logged after the case's three entries — the line the screen writes itself:

```
./gradlew --offline :auto:testDebugUnitTest -PforceTests --no-build-cache \
  --tests "com.naviveylin.auto.DiagnosticsScreenTest" --tests "com.naviveylin.auto.StartupScreensTest"
11 tests completed, 3 failed
```

`DiagnosticsScreenTest` `XML tests=5 failures=2 (2026-10-27T05:57:08.201Z)` with `expected:<10> but was:<8>`
twice; `StartupScreensTest` `XML tests=6 failures=1 (2026-10-27T05:57:16.698Z)` with `expected:<4> but was:<5>`
— the same shape as the CI run's `was:<8>`, which counted one foreign line more
(`evidence/red-on-head/`, `evidence/red-command.log`).

**Green on the change**, the same two reproductions still in place: `XML tests=5 failures=0
(2026-10-27T05:58:45.814Z)` and `XML tests=6 failures=0 (2026-10-27T05:58:56.862Z)`
(`evidence/green-on-change/`), and the whole `:auto` suite — the suite the red `test` job failed —
`91 classes, 880 tests, 0 failures, 0 errors`.

**Owning requirement**: `unit-test-suite-runtime` — "A diagnostics assertion is attributed to the lines its
own case wrote" (the sibling mechanism and the same screen, #115) and "A published work state is true when it
is read"; this change adds one requirement next to them: "A row count is read only once the screen's page
capacity is known". **Owning guideline section**: `guidelines/Build.md` §6f "Diagnosing a red run" (a
load-dependent case is diagnosed from its failing record) with §6b "Teardown, the Compose sandbox and
geometry claims" (release what the test starts).

**Surface, before the plan was fixed** — 8 cases over 4 test classes, 3 rewritten, 5 given the release they owe,
0 production files, 0 fixtures, 0 build files:

```
surface: 4 classes construct a DiagnosticsScreen, 8 cases over them, 2 total-count assertions, 57 existing
scenarios in unit-test-suite-runtime — grep -l "DiagnosticsScreen(" auto/src/test/java/com/naviveylin/auto/*.kt
| wc -l  -> 4; grep -c '^#### Scenario:' openspec/specs/unit-test-suite-runtime/spec.md  -> 57
```

- `grep -l "DiagnosticsScreen(" auto/src/test/java/com/naviveylin/auto/*.kt` → `DiagnosticsScreenTest`,
  `StartupScreensTest`, `GermanRenderingTest`, `TemplateFaultIsolationTest`; the eight cases they hold are the
  whole surface, and none released the screen it built.
- `grep -rc "titles.size == 4\|shown.size\|page.size"` over the two red classes → **2** total-count
  assertions (plus the ordering assertions that read the same window).
- `grep -c '^#### Scenario:' openspec/specs/unit-test-suite-runtime/spec.md` → **57** existing scenarios,
  none of which asserts when a page count may be read.

## What Changes

- **Added** a `unit-test-suite-runtime` requirement: a page count is read only once the screen's capacity is
  known, and a case asserts over the rows it wrote rather than over a diagnostics file's total.
- The three red cases await the state they count instead of a proxy of it: the two
  `DiagnosticsScreenTest` counts wait for the page the host's capacity builds, and
  `StartupScreensTest.diagnosticsScreenShowsNewestFirst` awaits its three entries and asserts their
  newest-first order, keeping the line it logged as the reproduction.
- Every case that constructs a `DiagnosticsScreen` releases it (`ON_CREATE`/`ON_START`/`ON_DESTROY`), so no
  screen outlives its case and writes its measurement into a later case's log target.

## Impact

| file | change |
|---|---|
| `auto/src/test/java/com/naviveylin/auto/DiagnosticsScreenTest.kt` | both counts await the page the host's capacity builds; every screen and every page row it opens is released in teardown |
| `auto/src/test/java/com/naviveylin/auto/StartupScreensTest.kt` | the newest-first case awaits its own three entries, keeps the foreign line as its reproduction, and asserts order over the entry rows instead of a window total; the screen is released |
| `auto/src/test/java/com/naviveylin/auto/GermanRenderingTest.kt` | the disclosure case releases the screen it builds |
| `auto/src/test/java/com/naviveylin/auto/TemplateFaultIsolationTest.kt` | the error-template case releases the screen it builds |
| `openspec/specs/unit-test-suite-runtime/spec.md` | one ADDED requirement, three scenarios, one case each |

No production file, no fixture, no build script, no API. Test sources and one guideline section only.
Rollback: revert the four test files and drop the added requirement — nothing else depends on them.
