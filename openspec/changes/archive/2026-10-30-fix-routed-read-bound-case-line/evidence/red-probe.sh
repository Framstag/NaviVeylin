#!/usr/bin/env bash
# red-probe.sh — the one case of this change, `routed-read#the-case-line-names-both-halves`.
#
# The defect is a spec shape, so the case reads the spec: the scenario "A section carrying more than one
# question is split" must name the case that measures the ruler (`routed-read#bound`) AND the case that
# files a routed section still past the bound (`sensors#routedSectionSizeRow`), and must say that the split
# itself is a check's condition rather than a case's failure. Both named cases must be declared by the
# suites the tree runs, so a Case line cannot cite a case nothing executes.
#
# Usage: red-probe.sh [--root <dir>] [--xml <file>]
# Exit:  0 the case passes, 1 it fails, 2 usage or an unreadable spec.
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
[ -r "$spec" ] || { printf 'red-probe: cannot read %s\n' "$spec" >&2; exit 2; }

# The scenario's own block: from its heading through the line before the next `#### Scenario:`.
scenario=$(awk '
  /^#### Scenario: A section carrying more than one question is split$/ { on = 1; next }
  on && /^#### Scenario:/ { exit }
  on { print }' "$spec")
if [ -z "$scenario" ]; then
  printf 'FAIL routed-read#the-case-line-names-both-halves — the scenario is not in %s\n' "$spec" >&2
  exit 1
fi

missing=""
have() { printf '%s' "$scenario" | grep -qF -- "$1" || missing="$missing [$1]"; }
have '`routed-read#bound`'
have '`sensors#routedSectionSizeRow`'
have 'ruler'
have "check's condition"

# …and the two names are live: each is declared by the suite that runs it, not merely written in the spec.
grep -qE '^echo_case routed-read#bound ' "$root/tools/check-doc-routes-selftest.sh" \
  || missing="$missing [declared: routed-read#bound]"
grep -qF "'sensors#routedSectionSizeRow')" "$root/tools/sensor-cases.sh" \
  || missing="$missing [declared: sensors#routedSectionSizeRow]"

status=0
[ -z "$missing" ] || status=1

now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
if [ -n "$xml" ]; then
  {
    printf '<?xml version="1.0" encoding="UTF-8"?>\n'
    printf '<testsuite name="evidence/red-probe.sh" tests="1" failures="%s" errors="0" timestamp="%s">\n' "$status" "$now"
    if [ "$status" = 0 ]; then
      printf '  <system-out>red-probe: 1 of 1 cases passed (documentation-ownership "A section carrying more than one question is split")</system-out>\n'
    else
      printf '  <failure>red-probe: the scenario does not state:%s</failure>\n' "$missing"
    fi
    printf '</testsuite>\n'
  } > "$xml"
fi

if [ "$status" = 0 ]; then
  printf 'PASS routed-read#the-case-line-names-both-halves — the scenario names the ruler case and the sensor case, and says the split is a check condition (%s)\n' "$now"
else
  printf 'FAIL routed-read#the-case-line-names-both-halves — the scenario does not state:%s (%s)\n' "$missing" "$now"
fi
exit "$status"
