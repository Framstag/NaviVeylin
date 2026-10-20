#!/usr/bin/env bash
# The portability check's self-test: the gate for the change that owns `tools/check-portability.sh`.
#
# Every case drives the real check, the real engines and the real toolchain inside a throwaway git
# repository built from the repository's own engines plus a config — this project's own for the
# value-set and discovery cases, the synthetic foreign project's for the rest. Nothing here needs a
# token, a network, a Gradle build, a device or a screen: the stub `gh` and the stub `pi` answer from
# fixtures (design D1–D6; guidelines/Build.md §2, the scope block).
#
# TWO PRECONDITIONS, refused rather than reported red (issue #172): the suite reads the real tree's check
# (its case `passesOnTheRealTree` drives it) and the fixture's versioned path (its case
# `fixtureIsVersioned` asserts it lives in the tracked tree). Run it from a copy outside a git working tree,
# or from a path under `.pi/` — machine-local state, not a checkout — and it stops with a named reason and
# exit 2: a red case there reads as a failure of the code under test, and cost two sessions a ten-minute hunt
# for a defect that was not theirs. Use the check itself (`tools/check-portability.sh`) from such a copy;
# only this suite needs a checkout.
#
# Usage: bash tools/check-portability-selftest.sh [--list] [--case <group#name>]
# Exit:  0 every case passed (or there are no cases), 1 a case failed, 2 the environment is unusable —
#        the root is not inside a git working tree, or the root path is machine-local state (`.pi/`).
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

case "${1:-}" in
  --list) list_only=1; only_case="" ;;
  --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; list_only=0; only_case="${2:-}" ;;
  "") list_only=0; only_case="" ;;
  -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
  *) echo "check-portability-selftest: unknown argument $1" >&2; exit 2 ;;
esac

# The two preconditions, before anything is read or created — see the header.
if ! git -C "$root" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "check-portability-selftest: not inside a git working tree — the suite reads the real tree's check and the fixture's versioned path; run it from a checkout of the project, not from a copy" >&2
  exit 2
fi
case "$root" in
  *"/.pi/"*)
    echo "check-portability-selftest: the suite's root $root is a /.pi/ path — machine-local state is not a checkout; run it from the project's working tree, or use tools/check-portability.sh from here" >&2
    exit 2
    ;;
esac

for tool in bash git jq java javac grep sed awk sort; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "check-portability-selftest: $tool is required" >&2
    exit 2
  }
done

feature=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
if [ -z "$feature" ]; then
  echo "check-portability-selftest: could not read the JDK feature version from: $(java -version 2>&1 | head -1)" >&2
  exit 2
fi
if [ "$feature" -lt 17 ]; then
  echo "check-portability-selftest: JDK 17 or newer is required (the issue core is compiled with --release 17), found feature version $feature" >&2
  exit 2
fi

# The scratch tree goes under the gitignored `.pi/tmp/`, so a failed run leaves nothing in the diff.
mkdir -p "$root/.pi/tmp" 2>/dev/null || true
tmp=$(mktemp -d "$root/.pi/tmp/check-portability-selftest.XXXXXX") || {
  echo "check-portability-selftest: cannot create a scratch directory under $root/.pi/tmp" >&2
  exit 2
}
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/classes"

javac --release 17 -d "$tmp/classes" "$root"/tools/issues/core/*.java || {
  echo "check-portability-selftest: the issue tooling's Java core does not compile" >&2
  exit 1
}

export PORTABILITY_ROOT="$root"
export PORTABILITY_FIXTURE="$root/tools/fixtures/foreign-project"
export PORTABILITY_TMP="$tmp"
export ISSUES_CORE_CLASSES="$tmp/classes"
export GH_STUB_DIR="$root/tools/issues/fixtures"
chmod +x "$root/tools/issues/fixtures/gh" "$root/tools/loop/fixtures/pi" 2>/dev/null || true

# shellcheck source=tools/portability-cases.sh
source "$root/tools/portability-cases.sh"
shell_cases=$(portability_cases)

if [ "$list_only" -eq 1 ]; then
  printf '%s\n' "$shell_cases" | grep -v '^$'
  exit 0
fi

if [ -n "$only_case" ]; then
  if printf '%s\n' "$shell_cases" | grep -qxF "$only_case"; then
    if ( portability_run_case "$only_case" ); then
      printf 'PASS %s\n' "$only_case"
      exit 0
    fi
    printf 'FAIL %s\n' "$only_case"
    exit 1
  fi
  echo "check-portability-selftest: no such case: $only_case" >&2
  exit 2
fi

total=0
failed=0
for case_name in $shell_cases; do
  total=$((total + 1))
  if ( portability_run_case "$case_name" ); then
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
  echo "check-portability-selftest: $failed of $total cases failed"
  exit 1
fi
echo "check-portability-selftest: all $total cases passed (JDK $feature, offline, no token, no Gradle)"
exit 0
