#!/usr/bin/env bash
# The issue tooling's self-test: the gate for the change that owns `tools/issues/**`.
#
# It compiles the Java core with `javac --release 17` into a scratch directory, refuses below JDK 17 with a
# named message, then runs every core case (`java -cp … Cases <name>`) and every shell case (the real
# wrappers against the stub `gh` on PATH). No token, no network, no Gradle — and nothing here needs a
# device or a screen (design D9, D10).
#
# Usage: bash tools/issue-tooling-selftest.sh [--list] [--case <Case#name>]
# Exit:  0 every case passed (or there are no cases), 1 a case failed, 2 the environment is unusable.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

case "${1:-}" in
  --list) list_only=1; only_case="" ;;
  --case) list_only=0; only_case="${2:-}" ;;
  "") list_only=0; only_case="" ;;
  -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
  *) echo "issue-tooling-selftest: unknown argument $1" >&2; exit 2 ;;
esac

command -v java >/dev/null 2>&1 || {
  echo "issue-tooling-selftest: java is required — the core runs as single-file Java" >&2
  exit 2
}
command -v javac >/dev/null 2>&1 || {
  echo "issue-tooling-selftest: javac is required — the core is compiled with --release 17" >&2
  exit 2
}

feature=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
if [ -z "$feature" ]; then
  echo "issue-tooling-selftest: could not read the JDK feature version from: $(java -version 2>&1 | head -1)" >&2
  exit 2
fi
if [ "$feature" -lt 17 ]; then
  echo "issue-tooling-selftest: JDK 17 or newer is required (the core is compiled with --release 17), found feature version $feature" >&2
  exit 2
fi

tmp=$(mktemp -d "${TMPDIR:-/tmp}/issue-tooling-selftest.XXXXXX") || {
  echo "issue-tooling-selftest: cannot create a scratch directory" >&2
  exit 2
}
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/classes"

javac --release 17 -d "$tmp/classes" "$root"/tools/issues/core/*.java || {
  echo "issue-tooling-selftest: the Java core does not compile" >&2
  exit 1
}

# the compiled core is handed to every script under test, so nothing compiles twice and every case runs
# exactly the classes this driver verified (`java -cp`, never `java <file>.java` — see `lib.sh`)
export ISSUES_CORE_CLASSES="$tmp/classes"
export ISSUES_REPO_ROOT="$root"
export ISSUES_ENGINE="$root/tools/issues"
export ISSUES_FIXTURES="$root/tools/issues/fixtures"
export ISSUES_TMP="$tmp"
export GH_STUB_DIR="$ISSUES_FIXTURES"
chmod +x "$ISSUES_FIXTURES/gh" 2>/dev/null || true
export PATH="$ISSUES_FIXTURES:$PATH"

core_cases=$(java -cp "$tmp/classes" Cases --list)
# shellcheck source=tools/issue-tooling-cases.sh
source "$root/tools/issue-tooling-cases.sh"
shell_cases=$(issues_shell_cases)

if [ "$list_only" -eq 1 ]; then
  printf '%s\n' "$core_cases" "$shell_cases" | grep -v '^$'
  exit 0
fi

total=0
failed=0

if [ -n "$only_case" ]; then
  if printf '%s\n' "$core_cases" | grep -qxF "$only_case"; then
    java -cp "$tmp/classes" Cases "$only_case" || exit 1
    exit 0
  fi
  if printf '%s\n' "$shell_cases" | grep -qxF "$only_case"; then
    if ( issues_run_shell_case "$only_case" ); then
      printf 'PASS %s\n' "$only_case"
      exit 0
    fi
    printf 'FAIL %s\n' "$only_case"
    exit 1
  fi
  echo "issue-tooling-selftest: no such case: $only_case" >&2
  exit 2
fi

for case_name in $core_cases; do
  total=$((total + 1))
  if ! java -cp "$tmp/classes" Cases "$case_name"; then
    failed=$((failed + 1))
  fi
done

for case_name in $shell_cases; do
  total=$((total + 1))
  if ( issues_run_shell_case "$case_name" ); then
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
  echo "issue-tooling-selftest: $failed of $total cases failed"
  exit 1
fi
echo "issue-tooling-selftest: all $total cases passed (JDK $feature, offline, no token)"
exit 0
