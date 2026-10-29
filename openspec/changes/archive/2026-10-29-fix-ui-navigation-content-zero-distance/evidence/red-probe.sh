#!/usr/bin/env bash
# red-probe.sh — the one case of this change, over `guidelines/UI.md` §1's NAVIGATION content bullet.
#
# The defect is a documentation shape, so the case reads the document: the content list must state the
# zero-versus-unknown distance rule in the wording the shipped requirement uses — a known zero renders
# `0 m`, an absent or `NaN` distance renders `--`, and the phone stats line and the car hint's secondary
# text follow it — and name the owning requirement. No build, no device, no network.
#
# Exit 0 when the case passes, 1 when it fails, 2 on a usage error.
#
# Usage: red-probe.sh [--xml <path>] [<case>]
#   case: ui-navigation-content#zero-versus-unknown   (the only case)
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
while [ ! -f "$root/AGENTS.md" ]; do
  [ "$root" != "/" ] || { echo "red-probe: no AGENTS.md above $(dirname "${BASH_SOURCE[0]}")" >&2; exit 2; }
  root=$(dirname "$root")
done
ui="$root/guidelines/UI.md"
[ -f "$ui" ] || { echo "red-probe: no $ui" >&2; exit 2; }

xml=""
case_filter=""
while [ $# -gt 0 ]; do
  case "$1" in
    --xml) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "red-probe: $1 needs a value" >&2; exit 2; }; xml="$2"; shift 2 ;;
    -h|--help) sed -n '2,13p' "${BASH_SOURCE[0]}"; exit 0 ;;
    -*) echo "red-probe: unknown argument '$1'" >&2; exit 2 ;;
    *) case_filter="$1"; shift ;;
  esac
done
case "$case_filter" in
  ''|'ui-navigation-content#zero-versus-unknown') ;;
  *) echo "red-probe: unknown case '$case_filter'" >&2; exit 2 ;;
esac

# The NAVIGATION content bullet: from the line that opens it to the next top-level bullet or heading.
navigation_bullet() {
  awk '
    /^- NAVIGATION content:/ { inside = 1 }
    inside && /^- / && !/^- NAVIGATION content:/ { exit }
    inside && /^### / { exit }
    inside { print }
  ' "$ui" | tr '\n' ' ' | sed -E 's/[[:space:]]+/ /g'
}

case_zero_versus_unknown() {
  local text missing=""
  text=$(navigation_bullet)
  for token in 'known zero' '`0 m`' 'absent or `NaN`' '`--`' 'stats line' "hint's secondary text" \
               'navigation-ongoing-notification'; do
    case "$text" in
      *"$token"*) ;;
      *) missing="$missing [$token]" ;;
    esac
  done
  if [ -n "$missing" ]; then
    printf 'FAIL ui-navigation-content#zero-versus-unknown — the NAVIGATION content bullet does not state:%s\n' "$missing"
    return 1
  fi
  printf 'PASS ui-navigation-content#zero-versus-unknown — the NAVIGATION content bullet states the zero-versus-unknown distance rule on both surfaces\n'
  return 0
}

ran=0
failed=0
try_case() { ran=$((ran + 1)); if "$1"; then :; else failed=$((failed + 1)); fi; }

try_case case_zero_versus_unknown

printf 'red-probe: %s of %s cases failed\n' "$failed" "$ran"
if [ -n "$xml" ]; then
  {
    printf '<?xml version="1.0" encoding="UTF-8"?>\n'
    printf '<testsuite name="evidence/red-probe.sh" tests="%s" failures="%s" errors="0" timestamp="%s">\n' \
      "$ran" "$failed" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf '  <system-out>red-probe: %s of %s cases failed (guidelines/UI.md §1 NAVIGATION content)</system-out>\n' \
      "$failed" "$ran"
    printf '</testsuite>\n'
  } > "$xml"
fi
[ "$failed" -eq 0 ] || exit 1
exit 0
