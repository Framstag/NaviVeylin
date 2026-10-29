# Design — an awaited state that never arrives fails by naming it

## The root cause

One mechanism, in one helper. `StartupScreensTest.awaitRows` (`StartupScreensTest.kt:148-167`) bounds its
looper-driven wait and then drops the answer to that wait:

```kotlin
var titles: List<String> = emptyList()
runBlocking {
    withTimeoutOrNull<Boolean>(5_000) {          // HEAD :154 — the Boolean? is dropped
        while (!done) { … titles = rows(screen) … }
        true
    }
}
return titles                                    // HEAD :166 — returned whatever the window held
```

The two states the window distinguishes — "the load was published" and "the window expired with the load still
pending" (`withTimeoutOrNull` returns `true` for the first, `null` for the second) — collapse into one list at
`:166`. The call site (`:117`) then compares a count of that list (`:126`, `:127`), so the missing state
reaches the reader as `expected:<3> but was:<0>` — the record the item cites
(`.pi/issue-loop/items/679/ci-red-40510522192.log:6-12`). Nothing in the failure says which state did not
arrive, which is what left #679 at `needs-diagnosis`.

The sibling already keeps the two states apart and pays for it with one line
(`DiagnosticsScreenTest.kt:154-174`, assertion at `:172`), so the class family is inconsistent with itself
rather than with any rule the tree lacks.

## The single fix

Adopt the sibling's shape in the one helper, and make the expiry reachable in zero wall-clock time:

```kotlin
private fun awaitRows(
    screen: DiagnosticsScreen,
    what: String,
    windowMs: Long = AWAIT_WINDOW_MS,            // 5_000, unchanged default
    ready: (List<String>) -> Boolean
): List<String> {
    var titles = rowTitles(screen)
    val arrived = runBlocking {
        withTimeoutOrNull<Boolean>(windowMs) {
            while (!ready(titles)) {
                shadowOf(Looper.getMainLooper()).idle()
                titles = rowTitles(screen)
                if (!ready(titles)) yield()
            }
            true
        }
    }
    shadowOf(Looper.getMainLooper()).idle()
    titles = rowTitles(screen)
    assertNotNull("the startup screen never published $what: $titles", arrived)
    return titles
}
```

- `assertNotNull` is the family's form (`DiagnosticsScreenTest.kt:172`, `:186`) and JUnit 4's, so the case
  fails with `java.lang.AssertionError: the startup screen never published three log rows: []` — the state,
  not a count.
- `windowMs` is a **hang guard that is now observable**, not a decision input: expiry fails the case instead of
  producing a value. The spec's "no test helper computes a deadline from the system clock"
  (`openspec/specs/unit-test-suite-runtime/spec.md:160-164`) is about the helper deciding an outcome from elapsed time; a
  guard whose only exit is a named failure decides nothing, and the sibling has kept the same 5 s escape with
  the same justification (`DiagnosticsScreenTest.kt:161`). The default stays 5 s, so a genuine hang still fails
  the case rather than the suite, and no existing timing changes.
- The parameter is what makes the red case honest: the expiry path is entered with `windowMs = 0L` in
  microseconds instead of a case burning 5 s to observe it, and the `:auto` suite's wall time does not move.
- The final `idle()` + re-read mirrors the sibling (`:170-171`): after the loop's last `idle()`, rows written by
  that idle are what the assertion message shows.
- The helper reads the rows through one expression used in both places, so the message and the awaited
  predicate cannot disagree about what "the rows" are.

## Blast radius

- One test helper in one test class (`auto/src/test/java/com/naviveylin/auto/StartupScreensTest.kt`): the six
  rows of the loop, the `assertNotNull`, the new parameter and its default constant.
- One existing call site (`:117`) passes the awaited state's name; no other file calls this helper
  (`tools/look.sh 'awaitRows' --files` → 2 files, 6 matches: 2 definitions, 4 call sites — the sibling's 3
  call sites are untouched).
- One added case in the same class, `awaitRowsNamesTheStateThatNeverArrived`, which constructs one
  `DiagnosticsScreen` and releases it with the `ON_CREATE`/`ON_START`/`ON_DESTROY` sequence the existing case
  already uses (`:130-133`), so no background work outlives it (`unit-test-suite-runtime` — Test-created
  components release their background work).
- No production source, no fixture, no build file, no resource, no dependency, no API. The class's 6 existing
  cases are unaffected: the only behaviour that changes is what happens on expiry, and no green case reaches
  it.

## Rollback

Restore the helper's body to `runBlocking { withTimeoutOrNull<Boolean>(5_000) { … } }` followed by
`return titles`, drop the `what`/`windowMs` parameters and the case's name argument, and delete the added case
and the added requirement. Nothing else depends on either, and the tree returns to the base commit's
behaviour — including the silent expiry.

## Alternatives the evidence eliminated

- **Wait 5 s in the red case instead of parameterizing the window.** The case would then cost 5 s on every run
  of `:auto` to observe one expiry, and it would make the suite's wall time depend on a case that asserts a
  failure — the shape `unit-test-suite-runtime` refuses elsewhere ("no test helper … paces itself with a
  sleep", `:164`). Zero is the same path, taken instantly.
- **Assert at the call site instead of in the helper.** `:126`'s `assertEquals(3, entries.size)` *is* that, and
  it is the defect's symptom: the call site knows a count it expects, not the state the helper awaited, so
  every future call site re-invents a different message. The helper is the one place that knows both the
  awaited predicate and the rows it saw.
- **Raise the window.** The item's own evidence cannot tell a slow load from an absent one; a longer window
  buys the same silence with a slower case, and the ambiguity #679 hit stays.
- **Throw on expiry from the helper's callers only.** The helper's third call site does not exist today
  (1 definition, 1 call site in this class), but the helper is the class's one seam for every future awaiting
  case; fixing it at the call site would leave the next case with the same silent window — the two-ways
  inconsistency the item names.
- **Unify the two classes' helpers into one shared test utility.** That rewrites the sibling's three call
  sites and its `awaitEntries`, widening a one-file fix to a shared test module for no behaviour the item
  asks for. The sibling is already correct; the delta is making this class match it.
