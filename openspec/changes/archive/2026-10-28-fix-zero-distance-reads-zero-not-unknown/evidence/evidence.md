# Evidence — issue #694

## Red on HEAD

`c29691865337325c0b293b57b72068d0468540da`, before the fix:

```
NavigationNotificationContentFormatterTest > zeroDistanceReadsZeroWhileAnUnknownDistanceStaysUnknown FAILED
java.lang.AssertionError: zero distance must render 0 m in the notification: in -- · arrive 14:32 · 0 min · 12.4 km
```

`evidence/red-zero-distance.xml`: `tests="1" skipped="0" failures="1" errors="0"
timestamp="2026-10-28T18:08:55.848Z"`.

## Focused green after the fix

`./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.service.NavigationNotificationContentFormatterTest"
--tests "com.naviveylin.service.CarHintContentTest" -PforceTests --no-build-cache`

- `evidence/focused-navigation-notification.xml`: `tests="18" skipped="0" failures="0" errors="0"
  timestamp="2026-10-28T18:20:05.557Z"`.
- `evidence/focused-car-hint.xml`: `tests="6" skipped="0" failures="0" errors="0"
  timestamp="2026-10-28T18:20:05.625Z"`.

## Revert-check

`evidence/revert-check.log`: the single mutation `|| meters <= 0.0` in `formatDistanceCompact` made the case
fail with `in -- · arrive 14:32 · 0 min · 12.4 km`; the marker count after restore was `0`. The restored
single case then ran green — `evidence/revert-restored.xml`: `tests="1" skipped="0" failures="0
errors="0" timestamp="2026-10-28T18:19:58.570Z"`.

## Gate

`./tools/loop/gate.sh`, exit 0, `evidence/gate.log`:

```
LOOP-GATE: tests=0 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 403s, tools/check-compose-sandbox-rule.sh 0s, tools/check-compose-sandbox-rule-selftest.sh 0s)
```

The gate's own wrapper XML carries `tests="0"`; the per-module JUnit XMLs the same run wrote report:

```
app/build/test-results/testMobileDebugUnitTest       2135 cases, 0 failed, 0 errors (skipped 0)
app/build/test-results/testAutomotiveDebugUnitTest   2135 cases, 0 failed, 0 errors (skipped 0)
auto/build/test-results/testDebugUnitTest             885 cases, 0 failed, 0 errors (skipped 0)
core/build/test-results/testDebugUnitTest             642 cases, 0 failed, 0 errors (skipped 0)
osmscout-client-java/build/test-results/test          112 cases, 0 failed, 0 errors (skipped 0)
```

Produced by:

```
for d in app/build/test-results/testMobileDebugUnitTest app/build/test-results/testAutomotiveDebugUnitTest \
         auto/build/test-results/testDebugUnitTest core/build/test-results/testDebugUnitTest \
         osmscout-client-java/build/test-results/test; do
  awk -F'"' '/<testsuite /{t+=$4; s+=$6; f+=$8; e+=$10}
    END{printf "%-52s cases=%s skipped=%s failed=%s errors=%s\n", "'"$d"'", t, s, f, e}' $d/*.xml
done
```

## Measure block

```
LOOP-MEASURE base=c29691865337325c0b293b57b72068d0468540da paths=2 insertions=68 deletions=13
LOOP-MEASURE-CHANGE fix-zero-distance-reads-zero-not-unknown scenarios=1 cases=1 unnamed=0
LOOP-MEASURE-GATE seconds=403 suites=3
LOOP-MEASURE-SURFACE surface: 1 case added, 0 rewritten, 0 fixtures realigned —
```
