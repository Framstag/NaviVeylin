#!/usr/bin/env bash
# The build graph a JVM-only test run asks Gradle for (change `fix-jvm-test-run-needs-no-native-tree`,
# spec `build-sbom` — "A JVM-only test run does not require the native tree").
#
# The licence inventory is a variant asset source (`guidelines/Build.md` §9), so the unit-test graph used to
# carry `generateLicenseAssets` → `generateSbom` → `mergeNativeSbom`, and `mergeNativeSbom` read vcpkg's
# installed-status file while the task was being *created*. A tree without a vcpkg checkout therefore could
# not configure `./gradlew test` at all (issue #270: 26 s, `Could not create task ':app:mergeNativeSbom'`,
# no test task reached its action).
#
# Three cases, each one Gradle invocation, all `--dry-run` so nothing is built, no test runs and no
# `cyclonedx-cli` is downloaded:
#   buildGraph#aJvmOnlyTestRunNeedsNoNativeTree   a test invocation with `-PjvmTestsOnly` and no vcpkg tree
#                                                 configures, and its graph holds no SBOM/licence task
#   buildGraph#aPackagingGraphRefusesTheProperty  the same invocation asking to package is refused, naming
#                                                 the packaging task and the property (the artefact guarantee)
#   buildGraph#aMissingNativeTreeFailsItsOwnTask  without the property, a missing tree fails `mergeNativeSbom`
#                                                 itself — not the configuration of the graph around it
#
# Prerequisite: the Android SDK, installed and licensed. Every case configures `:app`, whose configuration
# fails without one (`LicenceNotAcceptedException`) before the tree under test matters — so the CI step runs
# after 'Install Android SDK' and before 'Clone vcpkg' (issue #198: five red runs above that step).
#
# Usage: check-jvm-test-graph-selftest.sh [--root <dir>] [--case <name>]
#        --case <name>  run one case by its id (`buildGraph#…`, or the bare suffix) — what a revert-check
#                       names, so one mutation costs one Gradle invocation instead of three
# Exit: 0 every case passed, 1 a case failed, 2 usage/environment.
set -uo pipefail

root=""
only_case=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; only_case="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,27p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "check-jvm-test-graph-selftest: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$root" ] || root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -x "$root/gradlew" ] || { echo "check-jvm-test-graph-selftest: no $root/gradlew" >&2; exit 2; }

# selected <case id> — the case runs, either because nothing was selected or because it was.
selected() {
  [ -z "$only_case" ] && return 0
  [ "$only_case" = "$1" ] || [ "buildGraph#${only_case}" = "$1" ]
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# The tree a run without a vcpkg checkout sees: VCPKG_ROOT points at a directory that is not there.
absent_tree="$tmp/no-vcpkg-here"

total=0
failed=0
FAILED_CASES=""

# gradle_case <name> <VCPKG_ROOT value or -> <args…> — one invocation from the repository root, its combined
# output in $tmp/<name>.out and its exit status in GRADLE_RC.
gradle_case() {
  local name="$1" vcpkg="$2"
  shift 2
  if [ "$vcpkg" = "-" ]; then
    ( cd "$root" && ./gradlew "$@" ) > "$tmp/$name.out" 2>&1
  else
    ( cd "$root" && VCPKG_ROOT="$vcpkg" ./gradlew "$@" ) > "$tmp/$name.out" 2>&1
  fi
  GRADLE_RC=$?
}

# fail <name> <reason> — prints the case's reason and the tail of its Gradle output, and counts it failed.
# The case id carries the `buildGraph#` prefix the file name does not, so the tail is read from the case's
# own file: reading `$tmp/$name.out` printed an empty tail for every failure (a missing file, silenced by
# `2>/dev/null`), which is what made the five red runs of issue #198 report a bare exit code and no reason.
fail() {
  local name="$1" reason="$2"
  echo "FAIL $name: $reason"
  printf '%s\n' "$(cat "$tmp/${name#buildGraph#}.out" 2>/dev/null | tail -15)" | sed 's/^/       /'
  failed=$((failed + 1))
  FAILED_CASES="$FAILED_CASES $name"
}

# sdk_hint <gradle output file> — "" or a suffix naming the Android SDK. A probe configures `:app`, so a
# machine without an installed, licensed SDK fails the configuration for a reason that has nothing to do with
# the tree under test; the reason says so instead of leaving a reader with an exit status (issue #198).
sdk_hint() {
  grep -qE 'LicenceNotAcceptedException|Failed to install the following Android SDK packages|SDK location not found' "$1" 2>/dev/null &&
    printf '%s' ' — the Android SDK is missing or unlicensed here, which fails the configuration of :app before the tree under test matters; this check belongs after "Install Android SDK"'
  return 0
}

# ── case 1 ────────────────────────────────────────────────────────────────────────────────────────────────
# A JVM-only test run in a tree with no vcpkg checkout: the graph must not hold the SBOM or licence tasks,
# because every one of them needs that tree.
case_a=aJvmOnlyTestRunNeedsNoNativeTree
if selected "buildGraph#$case_a"; then
total=$((total + 1))
gradle_case "$case_a" "$absent_tree" :app:testMobileDebugUnitTest -PjvmTestsOnly --dry-run
if [ "$GRADLE_RC" != 0 ]; then
  fail "buildGraph#$case_a" "a test invocation without a vcpkg tree must configure (exit 0, got $GRADLE_RC)$(sdk_hint "$tmp/$case_a.out")"
else
  chain=$(grep -oE ':app:(generateLicenseAssets|generateSbom|mergeNativeSbom)[A-Za-z]*' "$tmp/$case_a.out" | sort -u | tr '\n' ' ')
  if [ -n "$chain" ]; then
    fail "buildGraph#$case_a" "the JVM-only graph still holds the SBOM/licence chain: $chain"
  elif ! grep -q ':app:testMobileDebugUnitTest' "$tmp/$case_a.out"; then
    fail "buildGraph#$case_a" "the test task is not in the graph the dry run printed"
  fi
fi
fi

# ── case 2 ────────────────────────────────────────────────────────────────────────────────────────────────
# The artefact guarantee: the property is refused when the same invocation packages, and the refusal names
# both the packaging task and the property.
case_b=aPackagingGraphRefusesTheProperty
if selected "buildGraph#$case_b"; then
total=$((total + 1))
gradle_case "$case_b" - :app:assembleMobileDebug -PjvmTestsOnly --dry-run
if [ "$GRADLE_RC" = 0 ]; then
  fail "buildGraph#$case_b" "a packaging graph accepted -PjvmTestsOnly (exit 0)"
elif ! grep -q 'assembleMobileDebug' "$tmp/$case_b.out"; then
  fail "buildGraph#$case_b" "the refusal does not name the packaging task it refused"
elif ! grep -q -- '-PjvmTestsOnly was requested' "$tmp/$case_b.out"; then
  fail "buildGraph#$case_b" "the refusal does not name the property it refused"
fi
fi

# ── case 3 ────────────────────────────────────────────────────────────────────────────────────────────────
# The lazy read: without the property, a missing tree is the *task's* failure, naming the status file — not
# the configuration failure of every graph that includes it. `-x downloadSbomCli` keeps the case offline:
# the CLI is not needed to reach the input-property read that fails.
case_c=aMissingNativeTreeFailsItsOwnTask
if selected "buildGraph#$case_c"; then
total=$((total + 1))
gradle_case "$case_c" "$absent_tree" :app:mergeNativeSbom -x :app:downloadSbomCli
if [ "$GRADLE_RC" = 0 ]; then
  fail "buildGraph#$case_c" "mergeNativeSbom succeeded without a vcpkg tree"
elif grep -q 'Could not create task' "$tmp/$case_c.out"; then
  fail "buildGraph#$case_c" "the missing tree failed the task's creation (configuration), not its own action"
elif ! grep -q 'installed-status file not found' "$tmp/$case_c.out"; then
  fail "buildGraph#$case_c" "the failure does not name the missing installed-status file$(sdk_hint "$tmp/$case_c.out")"
fi
fi

if [ "$total" -eq 0 ]; then
  echo "check-jvm-test-graph-selftest: no case named '$only_case'" >&2
  exit 2
fi

if [ "$failed" -gt 0 ]; then
  echo "check-jvm-test-graph-selftest: $failed of $total cases failed —${FAILED_CASES}"
  exit 1
fi
if [ "$total" -eq 1 ]; then
  echo "check-jvm-test-graph-selftest: all 1 case passed (one --dry-run configuration, no build, no network)"
else
  echo "check-jvm-test-graph-selftest: all $total cases passed (--dry-run configurations, no build, no network)"
fi
