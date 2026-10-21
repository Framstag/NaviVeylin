#!/usr/bin/env bash
# The sensor tooling's self-test: the gate for the change that owns `tools/sensors/**`, the three verbs it
# extends in `tools/issues/**` and `.github/workflows/sensors.yml`.
#
# It compiles the issue tooling's Java core once — the engine reuses that core's profile reader and policy
# gate, so nothing compiles twice — puts the stub `gh` (shared with the issue tooling) first on `PATH`, and
# runs every case offline: no token, no network, no Gradle, no device. Every case drives the real scripts;
# the fixtures are inputs, never stand-ins for the code under test.
#
# Usage: bash tools/sensor-selftest.sh [--list] [--case <name>]
# Exit:  0 every case passed (or there are no cases), 1 a case failed, 2 the environment is unusable.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

case "${1:-}" in
  --list) list_only=1; only_case="" ;;
  --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; list_only=0; only_case="${2:-}" ;;
  "") list_only=0; only_case="" ;;
  -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
  *) echo "sensor-selftest: unknown argument $1" >&2; exit 2 ;;
esac

command -v java >/dev/null 2>&1 || { echo "sensor-selftest: java is required — the issue tooling's core runs on it" >&2; exit 2; }
command -v javac >/dev/null 2>&1 || { echo "sensor-selftest: javac is required — the core is compiled with --release 17" >&2; exit 2; }
command -v jq >/dev/null 2>&1 || { echo "sensor-selftest: jq is required — the engine is JSON all the way down" >&2; exit 2; }

feature=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
if [ -z "$feature" ] || [ "$feature" -lt 17 ]; then
  echo "sensor-selftest: JDK 17 or newer is required, found '${feature:-unknown}'" >&2
  exit 2
fi

tmp=$(mktemp -d "${TMPDIR:-/tmp}/sensor-selftest.XXXXXX") || { echo "sensor-selftest: cannot create a scratch directory" >&2; exit 2; }
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/classes"

javac --release 17 -d "$tmp/classes" "$root"/tools/issues/core/*.java || {
  echo "sensor-selftest: the issue tooling's core does not compile" >&2
  exit 1
}

export ISSUES_CORE_CLASSES="$tmp/classes"
export SENSORS_REPO_ROOT="$root"
export SENSORS_ENGINE="$root/tools/sensors"
export SENSORS_ISSUES_ENGINE="$root/tools/issues"
export SENSORS_FIXTURES="$root/tools/sensors/fixtures"
export SENSORS_TMP="$tmp"
chmod +x "$root/tools/issues/fixtures/gh" 2>/dev/null || true
export PATH="$root/tools/issues/fixtures:$PATH"
cd "$root" || exit 2

# shellcheck source=tools/sensor-cases.sh
source "$root/tools/sensor-cases.sh"
cases=$(sensor_cases)

if [ "$list_only" -eq 1 ]; then
  printf '%s\n' "$cases" | grep -v '^$'
  exit 0
fi

if [ -n "$only_case" ]; then
  if ! printf '%s\n' "$cases" | grep -qxF "$only_case"; then
    echo "sensor-selftest: no such case: $only_case" >&2
    exit 2
  fi
  ( sensor_run_case "$only_case" ) && { printf 'PASS %s\n' "$only_case"; exit 0; }
  printf 'FAIL %s\n' "$only_case"
  exit 1
fi

total=0
failed=0
for case_name in $cases; do
  [ -n "$case_name" ] || continue
  total=$((total + 1))
  if ( sensor_run_case "$case_name" ); then
    printf 'PASS %s\n' "$case_name"
  else
    failed=$((failed + 1))
    printf 'FAIL %s\n' "$case_name"
  fi
done

if [ "$total" -eq 0 ]; then
  echo "no cases"
  exit 0
fi
if [ "$failed" -gt 0 ]; then
  echo "sensor-selftest: $failed of $total cases failed"
  exit 1
fi
echo "sensor-selftest: all $total cases passed (JDK $feature, offline, no token)"
exit 0
