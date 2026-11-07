#!/usr/bin/env bash
# The build graph `:app:checkClientApiInDex<Variant>` asks Gradle for (spec `test-coverage` — "The dex gate waits
# for the variant's dex producer").
#
# The gate reads the variant's whole `build/intermediates/dex/<variant>` tree and fails with "No dex archives to
# scan" when it holds no `*.dex` file. Until 2026-10-28 its dependencies named only the merge tasks of a plain
# variant (`dexBuilder*`, `mergeProjectDex*`, `mergeLibDex*`, `mergeExtDex*`), and a *minified* variant has none of
# them: AGP runs R8 (`minify<Variant>WithR8`) and R8 writes the variant's only dex. So the gate had no producer on
# the two `release` variants, an empty tree at its action, and `./gradlew release` — the command the release
# machine runs — died after 3 s: 9 actionable tasks, `:app:checkClientApiInDexAutomotiveRelease FAILED`, both AABs
# unbuilt. A `--dry-run` graph is what shows the defect: `:app:checkClientApiInDexAutomotiveRelease SKIPPED` alone.
#
# Six cases, each one Gradle invocation, all `--dry-run` so nothing is built, no native tree is touched and no
# network is used:
#   dexGateGraph#mobileDebugGateFollowsTheDexChain        the debug variant's gate names the dex builder and the
#                                                         three merge steps, and no R8 task
#   dexGateGraph#automotiveDebugGateFollowsTheDexChain    the same for the automotive debug variant
#   dexGateGraph#mobileReleaseGateFollowsR8               the minified variant's gate names `minifyMobileReleaseWithR8`
#                                                         — the case that was red on HEAD
#   dexGateGraph#automotiveReleaseGateFollowsR8           the same for the automotive release variant — the failing
#                                                         task of the reported `./gradlew release`
#   dexGateGraph#mobileBundleFollowsTheGate               `bundleMobileRelease` holds the gate and its producer
#   dexGateGraph#automotiveBundleFollowsTheGate           the same for the automotive AAB
#
# Prerequisite: the Android SDK, installed and licensed. Every case configures `:app`, whose configuration fails
# without one (`LicenceNotAcceptedException`) before the wiring under test matters — so the CI step runs after
# 'Install Android SDK'. No vcpkg checkout is needed: a `--dry-run` configures and stops.
#
# Usage: check-dex-gate-graph-selftest.sh [--root <dir>] [--case <name>]
#        --case <name>  run one case by its id (`dexGateGraph#…`, or the bare suffix) — what a revert-check names,
#                       so one mutation costs one Gradle invocation instead of six
# Exit: 0 every case passed, 1 a case failed, 2 usage/environment.
set -uo pipefail

root=""
only_case=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; only_case="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,30p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "check-dex-gate-graph-selftest: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$root" ] || root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -x "$root/gradlew" ] || { echo "check-dex-gate-graph-selftest: no $root/gradlew" >&2; exit 2; }

# selected <case id> — the case runs, either because nothing was selected or because it was.
selected() {
  [ -z "$only_case" ] && return 0
  [ "$only_case" = "$1" ] || [ "dexGateGraph#${only_case}" = "$1" ]
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

total=0
failed=0
FAILED_CASES=""

# gradle_case <name> <task> — one `--dry-run` invocation from the repository root, its combined output in
# $tmp/<name>.out and its exit status in GRADLE_RC.
gradle_case() {
  local name="$1" task="$2"
  ( cd "$root" && ./gradlew "$task" --dry-run ) > "$tmp/$name.out" 2>&1
  GRADLE_RC=$?
}

# fail <name> <reason> — prints the case's reason and the tail of its Gradle output, and counts it failed.
fail() {
  local name="$1" reason="$2"
  echo "FAIL $name: $reason"
  printf '%s\n' "$(cat "$tmp/${name#dexGateGraph#}.out" 2>/dev/null | tail -15)" | sed 's/^/       /'
  failed=$((failed + 1))
  FAILED_CASES="$FAILED_CASES $name"
}

# sdk_hint <gradle output file> — "" or a suffix naming the Android SDK, for the same reason as
# `tools/check-jvm-test-graph-selftest.sh` states: a probe configures `:app`, so a machine without an installed,
# licensed SDK fails the configuration for a reason that has nothing to do with the wiring under test.
sdk_hint() {
  grep -qE 'LicenceNotAcceptedException|Failed to install the following Android SDK packages|SDK location not found' "$1" 2>/dev/null &&
    printf '%s' ' — the Android SDK is missing or unlicensed here, which fails the configuration of :app before the wiring under test matters; this check belongs after "Install Android SDK"'
  return 0
}

# has_task <gradle output file> <task name> — the dry run printed `:app:<task> SKIPPED` (or any other status).
has_task() {
  grep -qE ":app:$2( |\$)" "$1"
}

# ── the plain variants ───────────────────────────────────────────────────────────────────────────────────────
# A debug variant dexes through the dex builder and the three merge steps; none of them exists on a minified one.
for variant in MobileDebug AutomotiveDebug; do
  name="${variant,}GateFollowsTheDexChain"
  if selected "dexGateGraph#$name"; then
    total=$((total + 1))
    gradle_case "$name" ":app:checkClientApiInDex$variant"
    if [ "$GRADLE_RC" != 0 ]; then
      fail "dexGateGraph#$name" "the gate's graph must configure (exit 0, got $GRADLE_RC)$(sdk_hint "$tmp/$name.out")"
    else
      missing=""
      for producer in "dexBuilder$variant" "mergeProjectDex$variant" "mergeLibDex$variant" "mergeExtDex$variant"; do
        has_task "$tmp/$name.out" "$producer" || missing="$missing $producer"
      done
      if [ -n "$missing" ]; then
        fail "dexGateGraph#$name" "the gate's graph holds no$missing — it would scan an empty dex tree"
      elif has_task "$tmp/$name.out" "minify${variant}WithR8"; then
        fail "dexGateGraph#$name" "the graph holds minify${variant}WithR8, which a debug variant does not have — the wiring names a task of the other shape"
      fi
    fi
  fi
done

# ── the minified variants ───────────────────────────────────────────────────────────────────────────────────
# `release` sets isMinifyEnabled = true, so the variant's only dex is R8's output, under the same
# `intermediates/dex/<variant>` tree the gate reads (`minify<Variant>WithR8/classes.dex`).
for variant in MobileRelease AutomotiveRelease; do
  name="${variant,}GateFollowsR8"
  if selected "dexGateGraph#$name"; then
    total=$((total + 1))
    gradle_case "$name" ":app:checkClientApiInDex$variant"
    if [ "$GRADLE_RC" != 0 ]; then
      fail "dexGateGraph#$name" "the gate's graph must configure (exit 0, got $GRADLE_RC)$(sdk_hint "$tmp/$name.out")"
    elif ! has_task "$tmp/$name.out" "minify${variant}WithR8"; then
      fail "dexGateGraph#$name" "the gate does not wait for minify${variant}WithR8 — a minified variant has no dex builder or merge step, so the gate runs before any dex exists and fails with 'No dex archives to scan' (the reported './gradlew release' failure)"
    fi
  fi
done

# ── the packaging graphs ──────────────────────────────────────────────────────────────────────────────────────
# The defect surfaced through packaging: `bundle<Release>` must hold the gate, and the gate must hold the
# producer, or the guard is absent from the one command that builds a shippable artifact.
for variant in MobileRelease AutomotiveRelease; do
  name="${variant,}BundleFollowsTheGate"
  if selected "dexGateGraph#$name"; then
    total=$((total + 1))
    gradle_case "$name" ":app:bundle$variant"
    if [ "$GRADLE_RC" != 0 ]; then
      fail "dexGateGraph#$name" "the bundle's graph must configure (exit 0, got $GRADLE_RC)$(sdk_hint "$tmp/$name.out")"
    elif ! has_task "$tmp/$name.out" "checkClientApiInDex$variant"; then
      fail "dexGateGraph#$name" "bundle$variant does not depend on checkClientApiInDex$variant"
    elif ! has_task "$tmp/$name.out" "minify${variant}WithR8"; then
      fail "dexGateGraph#$name" "bundle$variant holds the gate but not its producer minify${variant}WithR8"
    fi
  fi
done

if [ "$total" -eq 0 ]; then
  echo "check-dex-gate-graph-selftest: no case named '$only_case'" >&2
  exit 2
fi

if [ "$failed" -gt 0 ]; then
  echo "check-dex-gate-graph-selftest: $failed of $total cases failed —${FAILED_CASES}"
  exit 1
fi
echo "check-dex-gate-graph-selftest: all $total cases passed (--dry-run configurations, no build, no network)"
