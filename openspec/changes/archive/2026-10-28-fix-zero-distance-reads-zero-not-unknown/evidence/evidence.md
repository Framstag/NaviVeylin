# Evidence — issue #694

## Red on HEAD

`c29691865337325c0b293b57b72068d0468540da`, before the fix:

```
NavigationNotificationContentFormatterTest > zeroDistanceReadsZeroWhileAnUnknownDistanceStaysUnknown FAILED
java.lang.AssertionError: zero distance must render 0 m in the notification: in -- · arrive 14:32 · 0 min · 12.4 km
```

`evidence/red-zero-distance.xml`: `tests="1" skipped="0" failures="1" errors="0"
timestamp="2026-10-28T18:08:55.848Z"`.

## Focused green after the round-1 review's corrections

`./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.service.NavigationNotificationContentFormatterTest"
--tests "com.naviveylin.service.CarHintContentTest" -PforceTests --no-build-cache` → `BUILD SUCCESSFUL in 6s`.

- `evidence/focused-navigation-notification.xml`: `tests="18" skipped="0" failures="0" errors="0"
  timestamp="2026-10-28T18:32:26.404Z"`.
- `evidence/focused-car-hint.xml`: `tests="6" skipped="0" failures="0" errors="0"
  timestamp="2026-10-28T18:32:26.347Z"`.

The case that carries the scenario now asserts both halves on both surfaces: a known zero reads `0 m` in the phone
stats line and `0 m · 14:32` in the car hint; a live step whose `remainingDistance` is `null` and one whose
distance is `NaN` each read `in --` on the phone and `-- · 14:32` in the car hint; and a negative distance — a
passed manoeuvre's rounding artefact — clamps to `0 m` rather than rendering a negative one.

## Revert-check

`evidence/revert-check.log`: the single mutation `|| meters <= 0.0` in `formatDistanceCompact` made the case fail
with `in -- · arrive 14:32 · 0 min · 12.4 km`; the marker count after restore was `0`. The restored single case
then ran green — `evidence/revert-restored.xml`: `tests="1" skipped="0" failures="0 errors="0"
timestamp="2026-10-28T18:19:58.570Z"`.

## Gate

`./tools/loop/gate.sh`, from the change root, exit 0, on the tree the corrections left (`evidence/gate.log`):

```
LOOP-GATE: tests=6 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 274s, …)
```

The wrapper's `tests=6` is its count of the shell self-test's `all 6 cases passed` line; Gradle prints no such line,
so the compiled step contributes `tests=0` there (`tools/loop/gate.sh:274-279`, the wrapper defect the round-1 review
named). What executed in the build is carried by the per-module JUnit XMLs the same run wrote —
`evidence/gate-modules.txt` records their tallies and the command that reads them:

```
app/build/test-results/testMobileDebugUnitTest       2135 cases, 0 failed, 0 errors (skipped 0)
app/build/test-results/testAutomotiveDebugUnitTest   2135 cases, 0 failed, 0 errors (skipped 0)
auto/build/test-results/testDebugUnitTest             885 cases, 0 failed, 0 errors (skipped 0)
core/build/test-results/testDebugUnitTest             642 cases, 0 failed, 0 errors (skipped 0)
osmscout-client-java/build/test-results/test          112 cases, 0 failed, 0 errors (skipped 0)
```

The gate's own per-suite tallies are archived under `evidence/gate-tallies/`; the raw per-class XMLs (770 files,
1.9 MB) are not, and a clean removes them — the digest above is what survives.

## Round-1 review and the corrections it asked for

The review at `.pi/issue-loop/items/694/` (round 1, verdict FAIL, two blocking) named three corrections; this tree
carries them:

1. The requirement no longer names the `:core` extension `remainingMeters` — whose null-to-`0.0` collapse would have
   required `0 m` and `--` for the same unpublished-progress step. It states a known zero and an absent or `NaN`
   distance in terms of the nullable `remainingDistance`, in the archived delta and in the shipped
   `openspec/specs/navigation-ongoing-notification/spec.md`.
2. The scenario's unknown half is exercised on both surfaces for a `null` distance as well as a `NaN` one (see
   "Focused green" above), so the formatter's `null` guard and the car hint's unknown path run in a case.
3. The stale `step?.remainingMeters` call-site text in `proposal.md` and `tasks.md` 2.1 is corrected to
   `step?.remainingDistance`; `design.md` and `proposal.md` no longer claim the on-screen overlay and car session
   surface read `0 m` for an unpublished progress (they read the `remainingMeters` extension and are unchanged);
   and this evidence now archives the gate's per-module tallies plus the `evidence-check` transcript
   (`evidence/evidence-check.log`).

## Measure block

`./tools/loop/measure.sh --change 2026-10-28-fix-zero-distance-reads-zero-not-unknown`, run from the change root
after the corrections; the block is quoted verbatim in `evidence/measure.txt`. The lines that carry a number:

```
LOOP-MEASURE base=c29691865337325c0b293b57b72068d0468540da paths=20 insertions=563 deletions=13
LOOP-MEASURE-SUITE cases=273 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=285 suites=10
LOOP-MEASURE-CHANGE 2026-10-28-fix-zero-distance-reads-zero-not-unknown scenarios=1 cases=1 unnamed=0
LOOP-MEASURE-TALLY openspec/changes/archive/2026-10-28-fix-zero-distance-reads-zero-not-unknown/evidence/focused-car-hint.xml tests=6 failures=0
LOOP-MEASURE-TALLY openspec/changes/archive/2026-10-28-fix-zero-distance-reads-zero-not-unknown/evidence/focused-navigation-notification.xml tests=18 failures=0
LOOP-MEASURE-TALLY openspec/changes/archive/2026-10-28-fix-zero-distance-reads-zero-not-unknown/evidence/red-zero-distance.xml tests=1 failures=1
LOOP-MEASURE-TALLY openspec/changes/archive/2026-10-28-fix-zero-distance-reads-zero-not-unknown/evidence/revert-restored.xml tests=1 failures=0
LOOP-MEASURE-SURFACE surface: 1 case added, 0 rewritten, 0 fixtures realigned —
```

The gate's own timing is `LOOP-MEASURE-GATE seconds=285 suites=10` — the ten steps `evidence/gate.log` records, whose
clocks add up to it (274 s compiled, then 11 s of shell suites).
