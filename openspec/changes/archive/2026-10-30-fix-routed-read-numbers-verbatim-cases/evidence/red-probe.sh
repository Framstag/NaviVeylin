#!/usr/bin/env bash
# red-probe.sh — the one case of this change, `routed-read#the-two-case-lines-resolve`.
#
# The defect is a spec shape: two scenarios of "The routing cost is measured" name a case no harness runs.
# The case reads the shipped spec and the suite that owns the route cases:
#
#   * "A split section keeps its number and its citations" must name `routed-read#numbers`, that id must be
#     declared by `tools/check-doc-routes-selftest.sh`, and running it must pass;
#   * "The split moves text instead of rewriting it" must not point at `evidence/red-probe.sh` or any path in an
#     archived change, and must say the property is a judgement no harness asserts.
#
# Usage: red-probe.sh [--root <dir>] [--xml <file>]
# Exit:  0 the case passes, 1 it fails, 2 usage or an unreadable tree.
set -uo pipefail

root=""
xml=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf 'red-probe: %s needs a value\n' "$1" >&2; exit 2; }; root="$2"; shift 2 ;;
    --xml)  [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf 'red-probe: %s needs a value\n' "$1" >&2; exit 2; }; xml="$2"; shift 2 ;;
    *) printf 'red-probe: unknown argument %s\n' "$1" >&2; exit 2 ;;
  esac
done
if [ -z "$root" ]; then
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  while [ ! -d "$root/openspec" ] && [ "$root" != "/" ]; do root="$(dirname "$root")"; done
fi

spec="$root/openspec/specs/documentation-ownership/spec.md"
selftest="$root/tools/check-doc-routes-selftest.sh"
[ -r "$spec" ] || { printf 'red-probe: cannot read %s\n' "$spec" >&2; exit 2; }
[ -r "$selftest" ] || { printf 'red-probe: cannot read %s\n' "$selftest" >&2; exit 2; }

scenario() { # $1 = the scenario heading text; prints its body through the next `#### Scenario:`
  awk -v want="#### Scenario: $1" '
    $0 == want { on = 1; next }
    on && /^#### Scenario:/ { exit }
    on { print }' "$spec"
}

missing=""

numbers=$(scenario 'A split section keeps its number and its citations')
if [ -z "$numbers" ]; then
  missing="$missing [scenario: A split section keeps its number and its citations]"
else
  printf '%s' "$numbers" | grep -qF -- '`routed-read#numbers`' \
    || missing="$missing [numbers: routed-read#numbers]"
fi
grep -qE '^echo_case routed-read#numbers ' "$selftest" \
  || missing="$missing [declared: routed-read#numbers]"
if ! out=$(cd "$root" && bash tools/check-doc-routes-selftest.sh --case routed-read#numbers 2>&1); then
  missing="$missing [run: routed-read#numbers: $(printf '%s' "$out" | tail -1)]"
fi

verbatim=$(scenario 'The split moves text instead of rewriting it')
if [ -z "$verbatim" ]; then
  missing="$missing [scenario: The split moves text instead of rewriting it]"
else
  printf '%s' "$verbatim" | grep -qF -- 'evidence/red-probe.sh' \
    && missing="$missing [verbatim: names evidence/red-probe.sh]"
  printf '%s' "$verbatim" | grep -qF -- 'openspec/changes/archive' \
    && missing="$missing [verbatim: names an archived change path]"
  printf '%s' "$verbatim" | grep -qF -- 'no harness' \
    || missing="$missing [verbatim: says no harness asserts it]"
fi

status=0
[ -z "$missing" ] || status=1

now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
if [ -n "$xml" ]; then
  {
    printf '<?xml version="1.0" encoding="UTF-8"?>\n'
    printf '<testsuite name="evidence/red-probe.sh" tests="1" failures="%s" errors="0" timestamp="%s">\n' "$status" "$now"
    if [ "$status" = 0 ]; then
      printf '  <system-out>red-probe: 1 of 1 cases passed (documentation-ownership "The routing cost is measured")</system-out>\n'
    else
      printf '  <failure>red-probe: the two Case lines do not resolve:%s</failure>\n' "$missing"
    fi
    printf '</testsuite>\n'
  } > "$xml"
fi

if [ "$status" = 0 ]; then
  printf 'PASS routed-read#the-two-case-lines-resolve — routed-read#numbers is declared and runs, and routed-read#verbatim names no archived harness (%s)\n' "$now"
else
  printf 'FAIL routed-read#the-two-case-lines-resolve — the two Case lines do not resolve:%s (%s)\n' "$missing" "$now"
fi
exit "$status"
