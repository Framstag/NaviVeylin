#!/usr/bin/env bash
# The stale-citation resolver's self-test: the gate for `tools/issues/resolve-stale.sh` and for the classifier
# it shares with the loop's pre-screen (`stale-citation-resolution`).
#
# Every case drives the real classifier, the real resolver and the real pre-screen inside a throwaway git
# repository, against the stub `gh` on PATH: no token, no network, no tracker, no Gradle, no device. The
# classifier cases need no tracker at all — they ask `tools/issues/lib.sh` directly — and the resolver cases
# assert on the writes the stub recorded, which is how "one comment per repair" and "it never writes the
# authorisation" are checkable.
#
# Usage: bash tools/resolve-stale-selftest.sh [--list] [--case <group#name>]
# Exit:  0 every case passed (or there are no cases), 1 a case failed, 2 the environment is unusable.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

case "${1:-}" in
  --list) list_only=1; only_case="" ;;
  --case) list_only=0; only_case="${2:-}" ;;
  "") list_only=0; only_case="" ;;
  -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
  *) echo "resolve-stale-selftest: unknown argument $1" >&2; exit 2 ;;
esac

for tool in bash git jq java javac grep sed awk sort; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "resolve-stale-selftest: $tool is required" >&2
    exit 2
  }
done

feature=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
if [ -z "$feature" ]; then
  echo "resolve-stale-selftest: could not read the JDK feature version from: $(java -version 2>&1 | head -1)" >&2
  exit 2
fi
if [ "$feature" -lt 17 ]; then
  echo "resolve-stale-selftest: JDK 17 or newer is required (the issue core is compiled with --release 17), found feature version $feature" >&2
  exit 2
fi

mkdir -p "$root/.pi/tmp" 2>/dev/null || true
tmp=$(mktemp -d "$root/.pi/tmp/resolve-stale-selftest.XXXXXX") || {
  echo "resolve-stale-selftest: cannot create a scratch directory under $root/.pi/tmp" >&2
  exit 2
}
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/classes"

javac --release 17 -d "$tmp/classes" "$root"/tools/issues/core/*.java || {
  echo "resolve-stale-selftest: the issue tooling's Java core does not compile" >&2
  exit 1
}

export RESOLVE_ROOT="$root"
export RESOLVE_TMP="$tmp"
export ISSUES_CORE_CLASSES="$tmp/classes"
export GH_STUB_DIR="$root/tools/issues/fixtures"
chmod +x "$root/tools/issues/fixtures/gh" 2>/dev/null || true
export PATH="$root/tools/issues/fixtures:$PATH"

# shellcheck source=tools/resolve-stale-cases.sh
source "$root/tools/resolve-stale-cases.sh"
shell_cases=$(resolve_shell_cases)

if [ "$list_only" -eq 1 ]; then
  printf '%s\n' "$shell_cases" | grep -v '^$'
  exit 0
fi

if [ -n "$only_case" ]; then
  if printf '%s\n' "$shell_cases" | grep -qxF "$only_case"; then
    if ( resolve_run_case "$only_case" ); then
      printf 'PASS %s\n' "$only_case"
      exit 0
    fi
    printf 'FAIL %s\n' "$only_case"
    exit 1
  fi
  echo "resolve-stale-selftest: no such case: $only_case" >&2
  exit 2
fi

total=0
failed=0
for case_name in $shell_cases; do
  total=$((total + 1))
  if ( resolve_run_case "$case_name" ); then
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
  echo "resolve-stale-selftest: $failed of $total cases failed"
  exit 1
fi
echo "resolve-stale-selftest: all $total cases passed (JDK $feature, offline, no token, no Gradle)"
exit 0
