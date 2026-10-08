#!/bin/bash
# DIAGNOSTIC ONLY (2026-09-20). Runs the :auto unit suite in class-sized batches:
# one JVM per batch, results copied out per batch because Gradle cleans test-results
# on every invocation.
#
# Not the procedure any more: `:auto` runs in ONE invocation at its declared fork
# budget (`auto/build.gradle.kts`, maxHeapSize 1024m; TODO.md §33 closed,
# guidelines/Build.md §6). Use this script only to isolate a single class on a
# machine that cannot afford the declared fork heap, or when a batch boundary is
# itself being investigated.
#
# Usage: run-auto-batches.sh <change-name-for-the-log>

set -u
# The script lives in .pi/skills/run-tests/scripts/, so the repo root is four
# levels up.
cd "$(dirname "$0")/../../../.." || exit 1

CLASSES=$(ls auto/src/test/java/com/naviveylin/auto/*Test.kt 2>/dev/null | xargs -n1 basename | sed 's/\.kt$//' | sort)
TOTAL=$(echo "$CLASSES" | wc -l)
BATCHES=4
PER=$(( (TOTAL + BATCHES - 1) / BATCHES ))

echo "auto test classes: $TOTAL, $BATCHES batches of <= $PER"

rm -rf /tmp/auto-batches
mkdir -p /tmp/auto-batches

i=0
batch=0
ARGS=""
flush() {
  batch=$((batch + 1))
  [ -z "$ARGS" ] && return
  echo "--- batch $batch: $(echo $ARGS | grep -o '\-\-tests' | wc -l) classes"
  rm -rf auto/build/test-results/testDebugUnitTest
  # shellcheck disable=SC2086
  ./gradlew :auto:testDebugUnitTest $ARGS > "/tmp/auto-batches/batch-$batch.log" 2>&1
  echo "    gradle exit=$?"
  mkdir -p "/tmp/auto-batches/xml-$batch"
  cp auto/build/test-results/testDebugUnitTest/*.xml "/tmp/auto-batches/xml-$batch/" 2>/dev/null
  echo "    xml files: $(ls /tmp/auto-batches/xml-$batch | wc -l)"
  ARGS=""
}

for c in $CLASSES; do
  ARGS="$ARGS --tests com.naviveylin.auto.$c"
  i=$((i + 1))
  if [ $((i % PER)) -eq 0 ]; then flush; fi
done
flush

echo "=== aggregate ==="
grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' /tmp/auto-batches/xml-*/*.xml |
  awk -F'"' '{t+=$2; s+=$4; f+=$6; e+=$8} END {print "tests="t" skipped="s" failures="f" errors="e}'
