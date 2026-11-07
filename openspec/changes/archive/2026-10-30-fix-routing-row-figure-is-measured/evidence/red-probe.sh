#!/usr/bin/env bash
# red-probe.sh — the two cases of this change, over `tools/check-doc-routes.sh`.
#
# The defect is a check that reads a routing row's cost as presence and never compares it with the section's
# measurement: a row stating `0.1 kB` for a 13 102-byte section passes, so the half of the rule a reader acts on
# — how a section is chosen — is the half no check can convict (issue #712). Both cases drive the real check
# over a throwaway fixture tree whose routing table is a fixture `AGENTS.md`, and each case asserts both sides:
# the figure that disagrees is reported with the row, the section and both numbers, and the figure that is the
# measurement passes.
#
#   1. `routed-read#figure` — a figure written after a lettered reference is the section's cost in kB of
#      bytes/1000 rounded to one decimal: `0.1 kB` against a section measuring 1.2 kB is reported, and a row
#      stating the measurement is accepted.
#   2. `routed-read#range` — a range is the cheapest and the costliest of the lettered sections the row names:
#      `1.2–2.0 kB each` against sections measuring 1.2 and 3.0 kB is reported, a row stating `1.2–3.0 kB` is
#      accepted, and a number the row writes as a past measurement (`against 23.2 kB for §1 before the split`) is
#      not one of its figures and is never compared.
#
# No build, no device, no network. Exit 0 when every case passes, 1 when one fails, 2 on a usage error.
#
# Usage: red-probe.sh [--xml <path>] [<case>]
#   cases: routed-read#figure
#          routed-read#range
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
while [ ! -f "$root/AGENTS.md" ]; do
  [ "$root" != "/" ] || { echo "red-probe: no AGENTS.md above $(dirname "${BASH_SOURCE[0]}")" >&2; exit 2; }
  root=$(dirname "$root")
done
check="$root/tools/check-doc-routes.sh"
[ -x "$check" ] || { echo "red-probe: $check is not executable" >&2; exit 2; }

xml=""
case_filter=""
while [ $# -gt 0 ]; do
  case "$1" in
    --xml) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "red-probe: $1 needs a value" >&2; exit 2; }; xml="$2"; shift 2 ;;
    -h|--help) sed -n '2,24p' "${BASH_SOURCE[0]}"; exit 0 ;;
    -*) echo "red-probe: unknown argument '$1'" >&2; exit 2 ;;
    *) case_filter="$1"; shift ;;
  esac
done
case "$case_filter" in
  ''|'routed-read#figure'|'routed-read#range') ;;
  *) echo "red-probe: unknown case '$case_filter'" >&2; exit 2 ;;
esac

tmp=$(mktemp -d "${TMPDIR:-/tmp}/route-figure-probe.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT

# A line of filler, so a fixture section costs what a case states it costs.
filler() { # $1 = how many lines
  local i=0
  while [ "$i" -lt "$1" ]; do
    printf 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n'
    i=$((i + 1))
  done
}

# A fixture tree: one guideline document, two lettered sub-sections of a section the table routes. `$1` is the
# directory, `$2` the line the table's row carries after the references.
mk_fixture() { # $1 = directory, $2 = the row's tail
  mkdir -p "$1/guidelines"
  {
    printf '# UI Specification\n\n'
    printf '## 1. Cross-variant UI parity (general rule)\n\n'
    printf '## 1a. Parity, measured\n\n'
    printf 'The measured half of the parity rule, and a line long enough to cost more than a kilobyte: '
    filler 12
    printf '\n## 1b. Parity, unmeasured\n\n'
    printf 'The half with no case behind it yet, and a line long enough to cost three kilobytes: '
    filler 38
  } > "$1/guidelines/UI.md"
  {
    printf '# AGENTS\n\n## Documentation Map\n\n'
    printf '| what you are changing | read |\n|---|---|\n'
    printf '| phone UI | `UI.md` §1a "Parity, measured"%s |\n' "$2"
  } > "$1/AGENTS.md"
}

# The cost of a fixture section under the requirement's ruler — the byte count of its lines from the `## `
# heading through the line before the next one — taken here with `wc -c`, independently of the check.
section_cost() { # $1 = fixture document, $2 = section number, $3 = the number of the section after it
  if [ -n "$3" ]; then
    sed -n "/^## $2\\./,/^## $3\\./p" "$1" | sed '$d' | LC_ALL=C wc -c | tr -d ' '
  else
    sed -n "/^## $2\\./,\$p" "$1" | LC_ALL=C wc -c | tr -d ' '
  fi
}

cost_kb() { # $1 = bytes
  LC_ALL=C awk -v b="$1" 'BEGIN { printf "%.1f", b / 1000 }'
}

run_check() { # $1 = fixture root, echoes the check's output; its exit status is the check's
  "$check" --root "$1" 2>&1
}

case_routed_read_figure() {
  local d out rc bytes kb wrong
  d="$tmp/figure"; mk_fixture "$d" ''
  bytes=$(section_cost "$d/guidelines/UI.md" 1a 1b)
  kb=$(cost_kb "$bytes")
  [ "$bytes" -gt 1000 ] || { CASE_WHY="the fixture section measures $bytes bytes, too few for the claim"; return 1; }
  # the figure that disagrees: the item's own shape, a section worth kilobytes priced at a tenth of one
  mk_fixture "$d" ' — 0.1 kB'
  out=$(run_check "$d"); rc=$?
  if [ "$rc" != 1 ] || ! printf '%s' "$out" | grep -qF 'but the section measures'; then
    CASE_WHY="a row stating 0.1 kB for a $bytes-byte section was not reported: exit $rc: $out"
    return 1
  fi
  wrong=$(printf '%s' "$out" | grep -cE '0\.1 kB .*'"$kb"' kB|'"$kb"' kB .*0\.1 kB')
  [ "$wrong" -ge 1 ] || { CASE_WHY="the report does not name both numbers (stated 0.1 kB, measured $kb kB): $out"; return 1; }
  # and the figure that is the measurement passes
  mk_fixture "$d" " — $kb kB"
  out=$(run_check "$d"); rc=$?
  if [ "$rc" != 0 ]; then
    CASE_WHY="a row stating the section's own $kb kB (${bytes} bytes) did not pass: exit $rc: $out"
    return 1
  fi
  printf 'PASS routed-read#figure — a row stating 0.1 kB for a %s-byte (%s kB) section is reported with both numbers, and a row stating %s kB passes\n' \
    "$bytes" "$kb" "$kb"
  return 0
}

case_routed_read_range() {
  local d out rc other bytes_a bytes_b a b
  d="$tmp/range"; mk_fixture "$d" ', §1b "Parity, unmeasured" — 1.2–2.0 kB each'
  bytes_a=$(section_cost "$d/guidelines/UI.md" 1a 1b)
  bytes_b=$(section_cost "$d/guidelines/UI.md" 1b '')
  a=$(cost_kb "$bytes_a"); b=$(cost_kb "$bytes_b")
  [ "$bytes_b" -gt "$bytes_a" ] || { CASE_WHY="the fixture's second section does not cost more than its first"; return 1; }
  out=$(run_check "$d"); rc=$?
  if [ "$rc" != 1 ] || ! printf '%s' "$out" | grep -qF 'states the range'; then
    CASE_WHY="a row stating the range 1.2–2.0 kB for sections measuring $a–$b kB was not reported: exit $rc: $out"
    return 1
  fi
  # the range that is the span passes, and the row's past measurement is not one of its figures
  mk_fixture "$d" ", §1b \"Parity, unmeasured\" — $a–$b kB each, against 23.2 kB for §1 before the split"
  out=$(run_check "$d"); rc=$?
  if [ "$rc" != 0 ]; then
    CASE_WHY="a row stating the span $a–$b kB (${bytes_a} and ${bytes_b} bytes) did not pass, or its 'against … before the split' figure was compared: exit $rc: $out"
    return 1
  fi
  printf 'PASS routed-read#range — a row stating 1.2–2.0 kB for sections measuring %s–%s kB is reported, a row stating %s–%s kB passes, and the 23.2 kB it states as a past measurement is not compared\n' \
    "$a" "$b" "$a" "$b"
  return 0
}

ran=0
failed=0
try_case() { ran=$((ran + 1)); if "$1"; then :; else printf 'FAIL %s — %s\n' "$2" "${CASE_WHY:-no reason recorded}"; failed=$((failed + 1)); fi; }
if [ -z "$case_filter" ] || [ "$case_filter" = 'routed-read#figure' ]; then
  try_case case_routed_read_figure 'routed-read#figure'
fi
if [ -z "$case_filter" ] || [ "$case_filter" = 'routed-read#range' ]; then
  try_case case_routed_read_range 'routed-read#range'
fi

printf 'red-probe: %s of %s cases failed\n' "$failed" "$ran"
if [ -n "$xml" ]; then
  {
    printf '<?xml version="1.0" encoding="UTF-8"?>\n'
    printf '<testsuite name="evidence/red-probe.sh" tests="%s" failures="%s" errors="0" timestamp="%s">\n' \
      "$ran" "$failed" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf '  <system-out>red-probe: %s of %s cases failed (tools/check-doc-routes.sh, rows the table prices)</system-out>\n' \
      "$failed" "$ran"
    printf '</testsuite>\n'
  } > "$xml"
fi
[ "$failed" -eq 0 ] || exit 1
exit 0
