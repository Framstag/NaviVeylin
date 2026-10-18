# Proposal — fix-car-diagnostics-row-cap

## Why

Root cause: `DiagnosticsScreen` present its whole recent-entries window as **one fixed-constant pane** —
`MAX_ROWS = 20` rows added to a `Pane.Builder` and never a row capacity asked from the connected host — so
the number of log entries a driver can read is decided by a constant, and on a host whose list/pane budget is
below 20 the entries past it disappear with no signal and no page (the Car App Library's own contract is that
the host ignores the rows over the limit).

Evidence:    `com.naviveylin.auto.DiagnosticsScreenTest#theDiagnosticsListFitsTheHostsCapacity` fails on HEAD
— XML `tests="5" skipped="0" failures="2" errors="0"`, `timestamp="2026-10-18T14:47:27.099Z"`, failure
`java.lang.AssertionError: the diagnostics screen must be a car list to page its entries, was PaneTemplate (spec: car-list-row-limit)`.
The same run's second case, `thePageRowOpensTheNextSliceOfTheWindow`, fails as
`java.lang.AssertionError: the diagnostics screen must be a car list to page its entries, was PaneTemplate (spec: car-list-row-limit)`
— the page row the case taps cannot exist in a pane.
Retained as `evidence/TEST-DiagnosticsScreenTest-red-on-HEAD.xml`. The fake host reports a capacity of **8**,
deliberately neither the library's default 6 nor the screen's own constant 20, so the expected rows and the
`More (12)` count cannot be satisfied by a screen that ignores the host or keeps its constant.

Repro:       `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.DiagnosticsScreenTest" -PforceTests --no-build-cache`

Spec:        `car-list-row-limit` / A car list never presents more rows than the host displays, Items beyond
the capacity stay reachable
             Guideline: `guidelines/UI.md` §3 "Car-host template constraints (platform knowledge)" — its
`PaneTemplate` bullet ("~4–6 visible on most hosts, no scrolling, rows are **not actionable** — actions live
at pane level") and its `ListTemplate` bullet ("A car list screen MUST ask the host for the capacity and
present **at most** that many rows, ending with its own page row, because rows past the limit disappear with
no signal").

Authority — the tree today (commit `184ba56`):

- **The window is rendered into a pane, as one constant.** `auto/src/main/java/com/naviveylin/auto/DiagnosticsScreen.kt:78`
  (HEAD: `loaded.forEach { line -> builder.addRow(...) }` over the loaded window), `:107`
  (HEAD: `takeLast(MAX_ROWS).asReversed()`) and `:114` (HEAD: `private const val MAX_ROWS = 20`); the file
  contains no reference to `CarListCapacityReader`, no page parameter and no page row.
- **The pane cannot page.** `guidelines/UI.md:289-290`: `PaneTemplate` rows are not actionable and do not
  scroll, so a pane can neither present the 20-entry window on a 4–6-row host nor offer the remainder.
- **The shipped seam exists and is screen-generic.** `auto/src/main/java/com/naviveylin/auto/CarListCapacityReader.kt:50`
  (`read(carContext)`, `LIBRARY_DEFAULT = 6` at `:40`) and
  `auto/src/main/java/com/naviveylin/auto/FavoritesScreenMapper.kt:49` (`page(rows, capacity, page)`) are
  pure functions of the host's answer and an ordered sequence; `SearchHistoryScreen.kt:98` applies them to
  a list with the wording `R.string.more_items` (`auto/src/main/res/values/strings.xml:66`).
- **The window itself is deliberate.** The diagnostics file holds the full history under a size cap and a
  7-day retention (`core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt:50`/`:72`, `guidelines/Logging.md`
  §3), so this screen is a bounded recent-entries view of it — the defect is that its bound is a constant and
  its carrier cannot page, not that it is bounded.
- **The capacity facts.** The AAOS templates host measured **100 rows at car API level 8**
  (`car-favorites-group-level` task 1.1); the library's packaged default and this app's fallback is **6**.

## What Changes

- **The diagnostics screen becomes a car list and caps its window at the host's row capacity.** The screen
  asks the connected host for its `CONTENT_LIMIT_TYPE_LIST` capacity once per screen, off the host thread,
  presents at most that many window rows and ends with the clickable `More (n)` row that opens the next slice
  of the same newest-first window — the shipped `CarListCapacityReader` + `FavoritesScreenMapper.page` +
  page-row seam `FavoritesScreen`, `AddressBookScreen` and `SearchHistoryScreen` already apply. Nothing new is
  introduced: this is the existing capability `car-list-row-limit` applied to one more car list.
- **`MAX_ROWS = 20` stays as the window's outer bound.** The screen's pages partition the newest 20 entries;
  the entries past the window remain the file's, which is what the window was for.
- **The page row pushes the screen with a page index.** `page: Int = 0`, following `SearchHistoryScreen`: the
  row pushes another `DiagnosticsScreen` carrying `page + 1`, so the host's own back navigation returns to
  the page that opened it. Both call sites (`RootScreen.kt:163`, `MapScreen.kt:584`) construct the screen
  without the argument, whose default is page 0.
- **The disclosure row stays first on every page.** It is the requirement of `auto-diagnostics` ("Export and
  viewers disclose the log contents") and the first thing the driver must be able to read; it is not a window
  entry, so it is not part of the capacity the host reports for the entries.
- **Not this change:** the sibling entry `§179` / issue #113 (`PoiResultsScreen`) is held for an owner
  decision of its own (its entry records `skipped — needs-decision`); the phone diagnostics viewer, the log
  file's caps and retention, and the `MAX_TITLE_CHARS` truncation are untouched.
- **Guideline and spec**: `guidelines/UI.md` §3 states the capacity rule screen-generically and the pane
  bullet already records why a pane cannot serve this screen; nothing in it contradicts the fix, so no
  guideline edit is owed (stated as a task's verification, not assumed). `car-list-row-limit` gains the
  diagnostics scenarios it was already claimed to cover.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `car-list-row-limit`: the two existing requirements are restated with the scenarios the diagnostics screen
  now satisfies, and each gains the diagnostics scenario naming its case. The requirements' claims are
  unchanged — the defect is the diagnostics screen, not the contract. The scenarios this fix does not
  re-deliver (favourites, address book, search history, back navigation) keep the baseline's claim strength
  from `car-favorites-group-level`, `fix-car-list-row-capacity` and `fix-car-search-history-cap`, and are
  exercised by their existing cases.

## Impact

- `auto/src/main/java/com/naviveylin/auto/DiagnosticsScreen.kt` — the template changes from `PaneTemplate` to
  `ListTemplate`, plus an additive constructor parameter (`page`, defaulted), the capacity read, the page
  slice and the page row (one file).
- `auto/src/test/java/com/naviveylin/auto/DiagnosticsScreenTest.kt` — the red cases and the template-kind
  change in its shared harness; `StartupScreensTest.kt`, `GermanRenderingTest.kt` and
  `TemplateFaultIsolationTest.kt` read the same screen's template kind and are updated to the list.
- `openspec/specs/car-list-row-limit/spec.md` — via this change's delta; the tracker entry #114 is closed by
  the run, not by this change.
- No change to `:core`, `:app`, resources, JNI/native code, the manifest or the AAOS flavour; no new
  dependency, no new string (the `More (n)` wording exists in both locales).
- Additive and non-breaking: both constructor call sites keep their behaviour (page 0). Rollback: revert the
  one production file and the four test files; the delta spec rides with the change.
