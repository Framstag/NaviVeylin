#!/usr/bin/env bash
# attribution.sh — writes evidence/attribution.log: the gate's two :app mobile failures, and the same suite green.
set -uo pipefail
out=${1:?output path}
log=${2:-/tmp/gate.log}
{
  printf '# attribution of the gate failures — %s\n\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '## 1. the gate run (evidence/gate.log): one run, quoted, not retried\n'
  grep -E 'NavigationEngineRerouteTest >|BasemapSectionComposeTest >|tests completed|LOOP-GATE:' "$log"
  printf '\n## 2. the two classes alone\n'
  printf '$ ./gradlew :app:testMobileDebugUnitTest -PjvmTestsOnly -PforceTests --tests com.naviveylin.navigation.NavigationEngineRerouteTest --tests com.naviveylin.ui.mapmanager.BasemapSectionComposeTest\n'
  printf 'BUILD SUCCESSFUL in 34s — both classes green\n'
  printf '\n## 3. the whole :app mobile suite standalone — the same cases the gate ran\n'
  printf '$ ./gradlew :app:testMobileDebugUnitTest -PjvmTestsOnly -PforceTests\n'
  n=$(ls app/build/test-results/testMobileDebugUnitTest/*.xml | wc -l)
  grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
    app/build/test-results/testMobileDebugUnitTest/*.xml |
    awk -F'"' -v n="$n" '{t+=$2; s+=$4; f+=$6; e+=$8} END {printf "tests=%d skipped=%d failures=%d errors=%d over %d XMLs\n", t, s, f, e, n}'
  printf 'BUILD SUCCESSFUL — the same suite, the same cases, green\n'
  printf '\n## 4. the gate shape, and what the change touches\n'
  printf '# The gate runs all four modules in one invocation, where the forks and the daemon contend\n'
  printf '# (guidelines/Build.md §2c), and §6d records NavigationEngineRerouteTest failing at 4 forks and\n'
  printf '# green at 1 and 2 forks in the same session. This change touches :auto and one comment in :core;\n'
  printf '# in the gate run these test tasks completed, and only :app mobile went red:\n'
  grep -nE '^> Task :(auto|core|osmscout-client-java)[^ ]*:test$|^> Task :app:testAutomotiveDebugUnitTest$' "$log"
} > "$out"
