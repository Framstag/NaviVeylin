# An awaited state that never arrives fails by naming it, not as a row count

## Why

Item **#700** ("A test helper swallows its own 5 s window, so an unmet precondition reads as
`expected:<3> but was:<0>` instead of naming the state that never appeared"). Verified on HEAD
(`c185e5b4`, the run's base), the claim holds on all three of its halves:

- **The helper swallows its window.** `StartupScreensTest.awaitRows`
  (`auto/src/test/java/com/naviveylin/auto/StartupScreensTest.kt:148-167`) wraps its looper-driven wait in
  `withTimeoutOrNull<Boolean>(5_000)` (`:154`), drops the `Boolean?` the call returns, and `return titles`
  (`:166`) whatever the window held. A row list read at expiry is therefore indistinguishable from a row list
  read at arrival.
- **The call site then compares a count.** `diagnosticsScreenShowsNewestFirst` (`:117`) awaits
  `it.count { … } == 3` and `:126` asserts `assertEquals(3, entries.size)`. With the load missed, the case
  reads `expected:<3> but was:<0>` — the number, never the state that did not appear. The CI record the item
  cites carries exactly that, and the log names the frame:
  `.pi/issue-loop/items/679/ci-red-40510522192.log:6-12` (run `40510522192`, 2026-10-27T13:46:19Z):

  ```
  StartupScreensTest > diagnosticsScreenShowsNewestFirst FAILED
      java.lang.AssertionError: expected:<3> but was:<0>
          at com.naviveylin.auto.StartupScreensTest.diagnosticsScreenShowsNewestFirst(StartupScreensTest.kt:127)
  ```

- **The class family answers the same question two ways.** The sibling
  `DiagnosticsScreenTest.awaitRows` (`auto/src/test/java/com/naviveylin/auto/DiagnosticsScreenTest.kt:154-174`)
  keeps the same 5 s escape and then asserts on its result:
  `assertNotNull("the diagnostics screen never published $what: $titles", ok)` (`:172`). Its other helper,
  `awaitEntries` (`:137-151`), returns the `Boolean` it awaited and each call site asserts it with the
  expected state in the message (`:212`, `:218`, `:230`).

**Root cause, one mechanism:** the helper ends its bounded wait and *returns the rows it has* instead of
asserting that the awaited state arrived, so the expiry is dropped at `StartupScreensTest.kt:154-166` and the
case's own `assertEquals` at `:127` becomes the first thing that notices — as a count. One mechanism, both
halves of the symptom: an unmet precondition is unreadable, and whether a red belongs to the screen or to the
case cannot be told. That ambiguity is why #679 stopped at `needs-diagnosis` at 1-in-880 frequency.

**Red on HEAD** — `StartupScreensTest#awaitRowsNamesTheStateThatNeverArrived`, the case this change adds. It
calls the helper with a predicate that is never satisfied and a window forced to zero (the path the expiry
takes, without waiting 5 s for it), and requires the helper to fail naming the awaited state:

```
./gradlew --offline :auto:testDebugUnitTest -PforceTests --no-build-cache \
  --tests "com.naviveylin.auto.StartupScreensTest"
StartupScreensTest > awaitRowsNamesTheStateThatNeverArrived FAILED
    java.lang.AssertionError: awaitRows returned rows for a state that never arrived, so the case fails later on a count
        at com.naviveylin.auto.StartupScreensTest.awaitRowsNamesTheStateThatNeverArrived(StartupScreensTest.kt:160)

7 tests completed, 1 failed
BUILD FAILED in 12s
```

`XML tests=7 failures=1 errors=0 (2026-10-29T08:09:13.343Z)` (`evidence/red-on-head.xml`,
`evidence/red-on-head.log`). The red tree is HEAD plus the case and the helper's parameter list; the helper's
body is still HEAD's — `withTimeoutOrNull<Boolean>(windowMs) { … }` followed by `return titles` — so the case
fails on exactly the assertion the fix adds. With `windowMs = 0L` the window expires before the predicate is
satisfied and the case reads `awaitRows returned rows for a state that never arrived, so the case fails later
on a count` at `:160`.

**Owning requirement**: `unit-test-suite-runtime` — "A unit test controls the time its subject waits on"
(`openspec/specs/unit-test-suite-runtime/spec.md:146-152`), whose scenario "Awaiting state, not a deadline"
(`:160-164`) this helper's KDoc already cites (`StartupScreensTest.kt:145-147`) while its body does the
opposite of what the helper then reports. The requirement says the case awaits observable state; nothing in it
says the helper must fail *by name* when the state never arrives, so this change adds that one requirement
next to it.

**Owning guideline section**: `guidelines/Build.md` §6f "Diagnosing a red run" — *a red run is diagnosed from
the failure's message, not from its location* (`Build.md:996-1003`): the message is the diagnosis, and a case
whose message is a count carries no diagnosis at all.

**Surface, before the plan was fixed** — 1 test case added, 1 helper's body changed, 1 call site updated, 0
fixtures, 0 production files:

```
surface: 1 case added, 0 rewritten, 0 fixtures, 1 helper + 1 call site —
  tools/look.sh 'awaitRows' --files  -> 2 files / 6 matches: 2 definitions
  (StartupScreensTest.kt:148, DiagnosticsScreenTest.kt:154) and 4 call sites
  (StartupScreensTest.kt:117, DiagnosticsScreenTest.kt:255, :291, :297);
  this change edits 1 definition and its 1 call site, and leaves the sibling's 3 alone.
  tools/look.sh 'fun await[A-Za-z]*\('  -> 35 matches in 26 files (the await-helper family across
  :app, :auto and :core); of the two car-screen row helpers only DiagnosticsScreenTest.awaitRows
  asserts its awaited state (DiagnosticsScreenTest.kt:172), and StartupScreensTest's is the one
  without the assertion.
  tools/look.sh 'never published'  -> 1 match (DiagnosticsScreenTest.kt:172): 0 of them in this class.
  git show c185e5b4:openspec/specs/unit-test-suite-runtime/spec.md | grep -c '^#### Scenario:'
  -> 60 existing scenarios on the base tree, against 1 added by this delta.
```

## What Changes

- **Added** a `unit-test-suite-runtime` requirement: a helper that awaits a state SHALL NOT return the state
  it holds when its hang-guard window expires — the expiry SHALL fail the case with a message naming the
  awaited state and the rows observed. One scenario, one case.
- `StartupScreensTest.awaitRows` gains the sibling's shape: it takes the awaited state's name (`what`), keeps
  the 5 s escape as a hang guard, re-reads the rows after a final looper idle, and
  `assertNotNull("the startup screen never published $what: $titles", arrived)`.
- The helper's window becomes a parameter (`windowMs: Long = AWAIT_WINDOW_MS`), so the expiry path is
  reachable by a case in zero wall-clock time instead of by waiting 5 s for it. The default is unchanged.
- The one existing call site (`:117`) names its state: `"3 log rows"`.
- No production file changes; no fixture, no build script, no dependency, no test count outside this class.

## Impact

| file | change |
|---|---|
| `auto/src/test/java/com/naviveylin/auto/StartupScreensTest.kt` | `awaitRows` asserts the awaited state and names it; its window becomes a parameter with the same 5 s default; one existing call site passes the state's name; one case added, `awaitRowsNamesTheStateThatNeverArrived` |
| `openspec/specs/unit-test-suite-runtime/spec.md` | one ADDED requirement, one scenario, one case (after archive) |

Additive: no production signature, resource, string or preference changes, and the class's green path waits
for the same 5 s escape it waited for before. Rollback: restore the helper's body to `return titles` after the
window and delete the added case and requirement — the tree returns to the base commit's behaviour, the
silent expiry included.
