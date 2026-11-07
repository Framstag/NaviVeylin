#!/usr/bin/env bash
# The gate's artifacts, measured rather than assumed (issue #45, TODO.md §17). Three cases over two
# shipped requirements of `build-test-gate`:
#
#   "A gate run proves that its tests executed" — `#forcingRunsOnlyTheTestTasks`. `-PforceTests` has to make
#   the test tasks execute *and* leave the rest of the graph alone; both halves are read from one run's
#   output, so dropping the forcing (or widening it to `--rerun-tasks`) reddens the case.
#
#   "The local-test asset APK follows its merged assets" — `#aRealAssetChangeRefreshesTheLocalTestApk` and
#   `#anUnchangedTreeLeavesTheLocalTestApkUpToDate`. A Robolectric case that reads a packaged asset reads
#   the `apk-for-local-test.ap_` AGP builds for the unit-test variant, so that APK has to follow the merged
#   assets: measured 2026-10-24 (issue #45), moving one icon out of the *submodule source* re-executes
#   `syncSubmoduleIcons` → `mergeMobileDebugAssets` → `packageMobileDebugUnitTestForUnitTest`, and the APK
#   stops carrying the icon — while an unchanged tree leaves the task `UP-TO-DATE`. The item's own
#   2026-09-29 evidence for the opposite ("the APK stays stale") came from removing the file from a `Sync`
#   task's *output*, which the sync restores before the test runs: the mutation proved nothing about the
#   APK, and the rule that survives is `guidelines/Build.md` §4f — mutate the real source, and probe the
#   packaged artifact rather than its task graph.
#
# The mutation is applied to the submodule's own raster leaf and restored by a trap, so a case that fails
# still leaves the tree as it found it. One Gradle invocation per case, no test suite beyond `:core`'s in
# the forcing case, and no network beyond what a warm tree already carries.
#
# Usage: check-gate-artifacts-selftest.sh [--root <dir>] [--case <name>]
#        --case <name>  run one case by its id (`gateArtifacts#…`, or the bare suffix) — what a
#                       revert-check names, so one mutation costs one invocation instead of three
# Exit: 0 every case passed, 1 a case failed, 2 usage/environment.
set -uo pipefail

root=""
only_case=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; only_case="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,28p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "$(basename -- "$0" .sh): unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$root" ] || root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -x "$root/gradlew" ] || { echo "$(basename -- "$0" .sh): no $root/gradlew" >&2; exit 2; }

# selected <case id> — the case runs, either because nothing was selected or because it was.
selected() {
  [ -z "$only_case" ] && return 0
  [ "$only_case" = "$1" ] || [ "gateArtifacts#${only_case}" = "$1" ]
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

icon_rel="app/src/main/cpp/libosmscout/libosmscout/data/icons/14x14/standard/bus_stop.png"
icon="$root/$icon_rel"
saved="$tmp/bus_stop.png"
apk_rel="app/build/intermediates/apk_for_local_test/mobileDebugUnitTest/packageMobileDebugUnitTestForUnitTest/apk-for-local-test.ap_"
apk="$root/$apk_rel"

# The one mutation this file makes, and its undo: a real asset source moves out of the submodule's raster
# leaf, and the trap puts it back whatever happens to the case. `Sync` deletes it from the synced output on
# the way out and restores it on the way back, so the tree is a correct tree at both ends.
restore_icon() {
  [ -f "$saved" ] && [ ! -f "$icon" ] && mv -f "$saved" "$icon"
  return 0
}
trap 'restore_icon; rm -rf "$tmp"' EXIT

# gradle <name> <args…> — one invocation from the repository root, its combined output in
# $tmp/<name>.out and its exit status in GRADLE_RC.
GRADLE_RC=0
gradle() {
  local name="$1"
  shift
  ( cd "$root" && ./gradlew "$@" ) > "$tmp/$name.out" 2>&1
  GRADLE_RC=$?
}

# state_of <name> <task> — the task's line as the log wrote it (`> Task :x UP-TO-DATE`), or empty.
state_of() {
  local name="$1" task="$2"
  grep -E "^> Task $task( |$)" "$tmp/$name.out" | head -1
}

# apk_carries <asset file name> — 0 when the local-test APK holds the packaged asset.
#
# `grep -c`, not `grep -q`: this file runs under `pipefail`, and a `grep -q` that exits at its first match
# sends `unzip` SIGPIPE, so the pipeline's status is 141 and the test answers "not present" whatever the
# APK holds — measured 2026-10-24, the case that expects the icon *present* failed while the APK held it.
apk_carries() {
  local hits
  hits=$(unzip -l "$apk" 2>/dev/null | grep -c "assets/icons/14x14/standard/$1" || true)
  [ "${hits:-0}" -gt 0 ]
}

total=0
failed=0
FAILED_CASES=""

# fail_case <name> <reason> — prints the case's reason and the tail of every Gradle output the case wrote
# (a case may run two invocations), and counts it failed.
fail_case() {
  local name="$1" reason="$2"
  echo "FAIL $name: $reason"
  for f in "$tmp/$name"*.out; do
    [ -f "$f" ] || continue
    printf '%s\n' "--- $(basename "$f")\n$(tail -12 "$f")" | sed 's/^/       /'
  done
  failed=$((failed + 1))
  FAILED_CASES="$FAILED_CASES $name"
}

# ── case 1 ────────────────────────────────────────────────────────────────────────────────────────────────
# A real asset change: the icon leaves the source the sync reads, so the merged assets change and the
# unit-test APK has to be rebuilt without it.
case_a=aRealAssetChangeRefreshesTheLocalTestApk
if selected "gateArtifacts#$case_a"; then
total=$((total + 1))
if [ ! -f "$icon" ]; then
  fail_case "gateArtifacts#$case_a" "no asset source at $icon_rel — the submodule's raster leaf is not checked out"
else
  mv -f "$icon" "$saved"
  gradle "$case_a" :app:packageMobileDebugUnitTestForUnitTest
  if [ "$GRADLE_RC" != 0 ]; then
    fail_case "gateArtifacts#$case_a" "the local-test APK did not build without the icon (exit $GRADLE_RC)"
  elif [ -z "$(state_of "$case_a" :app:packageMobileDebugUnitTestForUnitTest)" ]; then
    fail_case "gateArtifacts#$case_a" "the run printed no line for :app:packageMobileDebugUnitTestForUnitTest"
  elif state_of "$case_a" :app:packageMobileDebugUnitTestForUnitTest | grep -qE 'UP-TO-DATE|FROM-CACHE'; then
    fail_case "gateArtifacts#$case_a" "the APK task was not re-executed after a real asset change: $(state_of "$case_a" :app:packageMobileDebugUnitTestForUnitTest)"
  elif apk_carries bus_stop.png; then
    fail_case "gateArtifacts#$case_a" "the rebuilt $apk_rel still carries the icon the source no longer has"
  fi
  restore_icon
fi
fi

# ── case 2 ────────────────────────────────────────────────────────────────────────────────────────────────
# The other half: with the icon back and no other change, the APK carries it again and a second invocation
# leaves the task up to date — so the freshness above is the asset's, not a task that never caches.
case_b=anUnchangedTreeLeavesTheLocalTestApkUpToDate
if selected "gateArtifacts#$case_b"; then
total=$((total + 1))
if [ ! -f "$icon" ]; then
  fail_case "gateArtifacts#$case_b" "no asset source at $icon_rel — the submodule's raster leaf is not checked out"
else
  gradle "$case_b" :app:packageMobileDebugUnitTestForUnitTest
  if [ "$GRADLE_RC" != 0 ]; then
    fail_case "gateArtifacts#$case_b" "the warming invocation failed (exit $GRADLE_RC)"
  elif ! apk_carries bus_stop.png; then
    fail_case "gateArtifacts#$case_b" "$apk_rel does not carry the icon its source has"
  else
    gradle "$case_b-again" :app:packageMobileDebugUnitTestForUnitTest
    line=$(state_of "$case_b-again" :app:packageMobileDebugUnitTestForUnitTest)
    if [ "$GRADLE_RC" != 0 ]; then
      fail_case "gateArtifacts#$case_b" "the second invocation failed (exit $GRADLE_RC)"
    elif [ -z "$line" ]; then
      fail_case "gateArtifacts#$case_b" "the second run printed no line for the APK task"
    elif ! printf '%s' "$line" | grep -q 'UP-TO-DATE'; then
      fail_case "gateArtifacts#$case_b" "an unchanged tree re-executed the APK task: $line"
    fi
  fi
fi
fi

# ── case 3 ────────────────────────────────────────────────────────────────────────────────────────────────
# The forcing rule, both halves in one run: the test task executes (so a run is evidence), the main compile
# does not (so forcing stayed on the test tasks), and the result XML is newer than the run's start (so the
# tallies are that run's).
case_c=forcingRunsOnlyTheTestTasks
if selected "gateArtifacts#$case_c"; then
total=$((total + 1))
gradle "$case_c-warm" :core:compileDebugKotlin
if [ "$GRADLE_RC" != 0 ]; then
  fail_case "gateArtifacts#$case_c" "the warming compile failed (exit $GRADLE_RC)"
else
  touch "$tmp/$case_c.marker"
  gradle "$case_c" :core:testDebugUnitTest -PforceTests --no-build-cache
  line=$(state_of "$case_c" :core:testDebugUnitTest)
  compile=$(state_of "$case_c" :core:compileDebugKotlin)
  if [ "$GRADLE_RC" != 0 ]; then
    fail_case "gateArtifacts#$case_c" "the forced test run failed (exit $GRADLE_RC)"
  elif [ -z "$line" ]; then
    fail_case "gateArtifacts#$case_c" "the run printed no line for :core:testDebugUnitTest"
  elif printf '%s' "$line" | grep -qE 'UP-TO-DATE|FROM-CACHE'; then
    fail_case "gateArtifacts#$case_c" "-PforceTests did not force the test task: $line"
  elif [ -z "$compile" ] || ! printf '%s' "$compile" | grep -q 'UP-TO-DATE'; then
    fail_case "gateArtifacts#$case_c" "the forced run re-executed the module's main compile: ${compile:-no line for :core:compileDebugKotlin}"
  elif [ -z "$(find "$root/core/build/test-results/testDebugUnitTest" -name '*.xml' -newer "$tmp/$case_c.marker" 2>/dev/null | head -1)" ]; then
    fail_case "gateArtifacts#$case_c" "no result XML is newer than the run's start, so the tallies are not this run's"
  fi
fi
fi

# The mutation must not outlive the file, whatever the cases did.
restore_icon
if [ ! -f "$icon" ]; then
  echo "$(basename -- "$0" .sh): the icon mutation was not restored — $icon_rel is missing, restore it before anything else runs" >&2
  exit 2
fi

if [ "$total" -eq 0 ]; then
  echo "$(basename -- "$0" .sh): no case named '$only_case'" >&2
  exit 2
fi

if [ "$failed" -gt 0 ]; then
  echo "$(basename -- "$0" .sh): $failed of $total cases failed —${FAILED_CASES}"
  exit 1
fi
if [ "$total" -eq 1 ]; then
  echo "$(basename -- "$0" .sh): all 1 case passed (one Gradle invocation, real asset source, mutation restored)"
else
  echo "$(basename -- "$0" .sh): all $total cases passed (Gradle invocations, a real asset source, the mutation restored)"
fi
