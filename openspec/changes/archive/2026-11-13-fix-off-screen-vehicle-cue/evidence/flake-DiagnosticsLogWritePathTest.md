# A red gate, attributed: an unrelated `:core` flake

The round-2 correction's gate was run twice. The first run was red on a case this change does not touch;
the second run was green. Both numbers are recorded here, with the mechanism the rerun ruled out
(`guidelines/BuildEvidence.md` §4d, "a flake of unknown origin gets an entry with its rerun evidence,
never a silent retry").

## Red run

The gate's headline reported one error and no failure — the failing step `./gradlew test -PforceTests
-PjvmTestsOnly --no-build-cache` (255 s, `BUILD FAILED in 4m 14s`), whose tally in the gate's
directory reads `errors="1"` (`gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`).

The case, from the step's own output:

```
DiagnosticsLogWritePathTest > loggingDoesNotWriteOnTheCallingThread FAILED
    org.junit.ComparisonFailure: the logging call must not touch the file expected:<...6.278] TEST warm-up
    []> but was:<...6.278] TEST warm-up
    [[2026-11-13 19:57:46.280] TEST caller-thread-line
    ]>
        at com.naviveylin.core.DiagnosticsLogWritePathTest.loggingDoesNotWriteOnTheCallingThread(DiagnosticsLogWritePathTest.kt:82)
662 tests completed, 1 failed
```

## The two runs that attribute it

- The failing class **alone**: `./gradlew :core:testDebugUnitTest --tests
  "*DiagnosticsLogWritePathTest*" -PforceTests --no-build-cache` → `BUILD SUCCESSFUL in 11s`, 0 failures.
- The gate **again**, same base, same tree: the headline reported no failure and no error (gradle step
  428 s, `BUILD SUCCESSFUL in 7m 7s`, its tally `errors="0"`).

## What the rerun ruled out, and what it did not

Ruled out: this change. Its paths are `LocationMarkerOverlay.kt`, `MapCanvasMapBand.kt`,
`LocationMarkerEdgeCueTest.kt`, `guidelines/MapFollow.md`, `tools/doc-routes.json` and this change's own
record — no file under `core/`, and the failing case measures `core/…/DiagnosticsLog`'s write path.

Not ruled out: the flake's own mechanism. `DiagnosticsLogWritePathTest` is a class the repository already
records as flaky — `openspec/changes/archive/2026-10-18-fix-parked-state-race` was filed for another case in
it, and `2026-10-09-fix-pinned-band-height/tasks.md:35` records the same command hitting it once on an
unrelated tree. The residual belongs to that class, not to this change.
