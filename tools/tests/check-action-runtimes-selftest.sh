#!/usr/bin/env bash
# Self-test for check-action-runtimes.sh: the checker must accept a pin at or above an action's recorded Node 24
# floor, reject a pin below it (naming file and line), reject an action its table does not record, reject a ref
# it cannot resolve offline, and skip the refs that carry no Node runtime of their own. It also runs the checker
# against this repository's own workflows, which is the case that would have caught issue #240.
#
# Everything runs against a throwaway tree: no network, no build, no device, no fixture in the repository.
#
# Usage: check-action-runtimes-selftest.sh [--root <dir>]   # default root: the repository this script lives in
# Exit: 0 every case passed, 1 a case failed, 2 usage.
set -uo pipefail

root=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,11p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "check-action-runtimes-selftest: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$root" ] || root=$(git rev-parse --show-toplevel 2>/dev/null) || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
checker="$root/tools/check-action-runtimes.sh"
[ -f "$checker" ] || { echo "check-action-runtimes-selftest: no $checker" >&2; exit 2; }

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/.github/workflows"
subject="$tmp/.github/workflows/probe.yml"

write_workflow() { # write_workflow <uses lines…> — one step per line
  {
    printf 'name: Probe\non: [push]\njobs:\n  probe:\n    runs-on: ubuntu-24.04\n    steps:\n'
    for ref in "$@"; do
      printf '      - uses: %s\n' "$ref"
    done
  } > "$subject"
}

write_runs_on() { # write_runs_on <runs-on value…> — one job per value, so a label is read on its own line
  local value n=0
  {
    printf 'name: Probe\non: [push]\njobs:\n'
    for value in "$@"; do
      n=$((n + 1))
      printf '  probe%d:\n    runs-on: %s\n    steps:\n      - uses: actions/checkout@v5\n' "$n" "$value"
    done
  } > "$subject"
}

total=0
failed=0
expect() { # expect <0|1|2> <label> [<needle in the output>]
  local want="$1" label="$2" needle="${3:-}" got=0 out
  total=$((total + 1))
  out=$(bash "$checker" --root "$tmp" 2>&1) || got=$?
  if [ "$got" != "$want" ]; then
    echo "FAIL: $label (expected exit $want, got $got)"
    printf '%s\n' "$out" | sed 's/^/       /'
    failed=$((failed + 1))
    return 1
  fi
  if [ -n "$needle" ] && ! printf '%s' "$out" | grep -qF -- "$needle"; then
    echo "FAIL: $label (output does not name '$needle')"
    printf '%s\n' "$out" | sed 's/^/       /'
    failed=$((failed + 1))
    return 1
  fi
  echo "ok: $label"
}

# The red case of issue #240: this repository's own pins, checked as they stand. It is the case
# `sonar-analysis` names for the pin this change adds — a workflow pin that no floor row records is what turns
# this green run red.
total=$((total + 1))
if repo_out=$(bash "$checker" --root "$root" 2>&1); then
  echo "ok: action-runtimes#theRepositorysOwnPinsPass — this repository's workflows pin a Node 24 major"
else
  echo "FAIL: action-runtimes#theRepositorysOwnPinsPass — this repository's workflows pin a Node 24 major"
  printf '%s\n' "$repo_out" | sed 's/^/       /'
  failed=$((failed + 1))
fi

write_workflow actions/checkout@v5 actions/setup-java@v5 actions/cache@v5 actions/upload-artifact@v7
expect 0 "pins at or above the Node 24 floor pass"

write_workflow actions/upload-artifact@v5
expect 1 "a Node 20 upload-artifact pin fails" ".github/workflows/probe.yml,line=7"

write_workflow actions/checkout@v4
expect 1 "a Node 20 checkout pin fails" ".github/workflows/probe.yml,line=7"

# A subpath shares its action's runtime: `actions/cache/restore` is `actions/cache`.
write_workflow actions/cache/restore@v5
expect 0 "a subpath of a recorded action resolves to the action's floor"

write_workflow actions/never-heard-of@v1
expect 1 "action-runtimes#anActionWithNoRecordedRuntimeFails — an action with no recorded runtime fails" "has no recorded Node runtime"

write_workflow actions/checkout@main
expect 1 "an unverifiable ref fails" "is not a released major"

write_workflow 'docker://alpine:3.20' './.github/actions/local'
expect 0 "a container action and a local action are skipped"

# The runner image is the second pin this checker reads (issue #761): a released image passes, a `-latest` label
# is reported where it stands, and the two values the workflow itself resolves — an expression and `self-hosted`
# — are skipped rather than guessed at.
write_runs_on ubuntu-24.04 windows-2022 ubuntu-22.04
expect 0 "runtimes#aHostedRunnerImageIsPinned — released runner images pass"

write_runs_on ubuntu-24.04 ubuntu-latest
expect 1 "runtimes#aHostedRunnerImageIsPinned — an ubuntu-latest label is reported" "is a moving label"
expect 1 "runtimes#aHostedRunnerImageIsPinned — the report names the file and line" ".github/workflows/probe.yml,line=9"

write_runs_on macos-latest
expect 1 "runtimes#aHostedRunnerImageIsPinned — every hosted -latest label is reported" "'runs-on: macos-latest' is a moving label"

write_runs_on '${{ matrix.os }}' self-hosted
expect 0 "runtimes#aHostedRunnerImageIsPinned — an expression and self-hosted are skipped"

printf 'name: Probe\non: [push]\n' > "$subject"
expect 0 "a workflow with no action pin passes"

rm -rf "$tmp/.github"
total=$((total + 1))
rc=0
bash "$checker" --root "$tmp" >/dev/null 2>&1 || rc=$?
if [ "$rc" = 2 ]; then
  echo "ok: a tree with no workflow directory is a layout error"
else
  echo "FAIL: a tree with no workflow directory is a layout error (expected exit 2, got $rc)"
  failed=$((failed + 1))
fi

if [ "$failed" -gt 0 ]; then
  echo "check-action-runtimes-selftest: $failed of $total cases failed"
  exit 1
fi
echo "check-action-runtimes-selftest: all $total cases passed (offline, no token, no build)"
