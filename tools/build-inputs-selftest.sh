#!/usr/bin/env bash
# The build inputs' self-test: the gate for `tools/build-inputs.sh` and `tools/fetch-build-inputs.sh`.
#
# It runs every case offline — the shared stub `gh` first on `PATH`, no token, no network, no Gradle, no
# device — and drives the real scripts against throwaway trees. The fixtures are inputs, never stand-ins for
# the code under test.
#
# Usage: bash tools/build-inputs-selftest.sh [--list] [--case <name>]
# Exit:  0 every case passed (or there are no cases), 1 a case failed, 2 the environment is unusable.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

case "${1:-}" in
  --list) list_only=1; only_case="" ;;
  --case) list_only=0; only_case="${2:-}" ;;
  "") list_only=0; only_case="" ;;
  -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
  *) echo "build-inputs-selftest: unknown argument $1" >&2; exit 2 ;;
esac

command -v jq >/dev/null 2>&1 || { echo "build-inputs-selftest: jq is required — the scripts are JSON all the way down" >&2; exit 2; }
command -v mktemp >/dev/null 2>&1 || { echo "build-inputs-selftest: mktemp is required" >&2; exit 2; }

tmp=$(mktemp -d "${TMPDIR:-/tmp}/build-inputs-selftest.XXXXXX") || { echo "build-inputs-selftest: cannot create a scratch directory" >&2; exit 2; }
trap 'rm -rf "$tmp"' EXIT

export INPUTS_REPO_ROOT="$root"
export INPUTS_FIXTURES="$root/tools/issues/fixtures"
export INPUTS_TMP="$tmp"
chmod +x "$INPUTS_FIXTURES/gh" 2>/dev/null || true
export PATH="$INPUTS_FIXTURES:$PATH"
cd "$root" || exit 2

# shellcheck source=tools/build-inputs-cases.sh
source "$root/tools/build-inputs-cases.sh"
cases=$(inputs_cases)

if [ "$list_only" -eq 1 ]; then
  printf '%s\n' "$cases" | grep -v '^$'
  exit 0
fi

if [ -n "$only_case" ]; then
  if ! printf '%s\n' "$cases" | grep -qxF "$only_case"; then
    echo "build-inputs-selftest: no such case: $only_case" >&2
    exit 2
  fi
  ( inputs_run_case "$only_case" ) && { printf 'PASS %s\n' "$only_case"; exit 0; }
  printf 'FAIL %s\n' "$only_case"
  exit 1
fi

total=0
failed=0
for case_name in $cases; do
  [ -n "$case_name" ] || continue
  total=$((total + 1))
  if ( inputs_run_case "$case_name" ); then
    printf 'PASS %s\n' "$case_name"
  else
    failed=$((failed + 1))
    printf 'FAIL %s\n' "$case_name" >&2
  fi
done

printf '%d cases, %d failed\n' "$total" "$failed"
[ "$failed" -eq 0 ]
