#!/usr/bin/env bash
# The loop's self-test: the gate for the change that owns `tools/loop/**`.
#
# Every case drives the real scripts — the wrapper, the worktree, the branches, the commits, the label
# transitions and the closure — inside a throwaway git repository created per case, with the stub `gh`
# (change 1) and the stub `pi` on PATH. Nothing here needs a token, a network, a model, a device or a
# sandbox tool: git is never stubbed, because the git logic is part of what is being tested (D13).
#
# Usage: bash tools/loop-selftest.sh [--list] [--case <group#name>]
# Exit:  0 every case passed (or there are no cases), 1 a case failed, 2 the environment is unusable.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

case "${1:-}" in
  --list) list_only=1; only_case="" ;;
  --case) list_only=0; only_case="${2:-}" ;;
  "") list_only=0; only_case="" ;;
  -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
  *) echo "loop-selftest: unknown argument $1" >&2; exit 2 ;;
esac

for tool in bash jq git java javac; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "loop-selftest: $tool is required" >&2
    exit 2
  }
done

feature=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
if [ -z "$feature" ]; then
  echo "loop-selftest: could not read the JDK feature version from: $(java -version 2>&1 | head -1)" >&2
  exit 2
fi
if [ "$feature" -lt 17 ]; then
  echo "loop-selftest: JDK 17 or newer is required (the issue core is compiled with --release 17), found feature version $feature" >&2
  exit 2
fi

# The scratch tree goes under the gitignored `.pi/tmp/`, so a failed run leaves nothing in the diff.
mkdir -p "$root/.pi/tmp" 2>/dev/null || true
tmp=$(mktemp -d "$root/.pi/tmp/loop-selftest.XXXXXX") || {
  echo "loop-selftest: cannot create a scratch directory under $root/.pi/tmp" >&2
  exit 2
}
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/classes"

javac --release 17 -d "$tmp/classes" "$root"/tools/issues/core/*.java || {
  echo "loop-selftest: the issue tooling's Java core does not compile" >&2
  exit 1
}

export LOOP_ENGINE="${LOOP_ENGINE:-$root/tools/loop}"
export LOOP_ISSUES_ENGINE="$root/tools/issues"
export LOOP_FIXTURES="$root/tools/loop/fixtures"
export LOOP_ISSUE_FIXTURES="$root/tools/issues/fixtures"
export LOOP_PROJECT_ROOT="$root"
# Overridable so the mutation harness can point a case at a mutated *copy* of the engine and confirm its own
# guards (`tools/loop-mutations.sh --engine <copy>`); the defaults are this repository.
export LOOP_CONF="${LOOP_CONF:-$root/tools/loop/loop.conf}"
export ISSUES_CORE_CLASSES="$tmp/classes"
export LOOP_TMP="$tmp"
export GH_STUB_DIR="$LOOP_ISSUE_FIXTURES"
chmod +x "$LOOP_FIXTURES/pi" "$LOOP_ISSUE_FIXTURES/gh" 2>/dev/null || true
export PATH="$LOOP_FIXTURES:$LOOP_ISSUE_FIXTURES:$PATH"

# shellcheck source=tools/loop-cases.sh
source "$root/tools/loop-cases.sh"
shell_cases=$(loop_shell_cases)

if [ "$list_only" -eq 1 ]; then
  printf '%s\n' "$shell_cases" | grep -v '^$'
  exit 0
fi

if [ -n "$only_case" ]; then
  if printf '%s\n' "$shell_cases" | grep -qxF "$only_case"; then
    if ( loop_run_shell_case "$only_case" ); then
      printf 'PASS %s\n' "$only_case"
      exit 0
    fi
    printf 'FAIL %s\n' "$only_case"
    exit 1
  fi
  echo "loop-selftest: no such case: $only_case" >&2
  exit 2
fi

total=0
failed=0
for case_name in $shell_cases; do
  total=$((total + 1))
  if ( loop_run_shell_case "$case_name" ); then
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
  echo "loop-selftest: $failed of $total cases failed"
  exit 1
fi
echo "loop-selftest: all $total cases passed (JDK $feature, offline, no token, no sandbox)"
exit 0
