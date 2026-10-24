#!/usr/bin/env bash
# red-cases-293.sh — the change's red and green evidence in one re-runnable pair (issue #293, tasks 1.3 and 3.1).
#
# Installs `tools/issues/lib.sh` from the item's base commit, runs the probe and the three cases against it
# (the red half), restores the tree's lib, and runs both again (the green half). The four logs are written next
# to this script, so a later reader can reproduce every quoted line instead of trusting it.
#
# Usage: bash red-cases-293.sh [root] [commit]      (root defaults to `.`; run it from the change root)
# Exit:  0 the red half failed and the green half passed, 1 either half did not behave as claimed.
set -uo pipefail

root="${1:-.}"
commit="${2:-f128f320d59dd1763ebb4354ec1a3274ccd895b4}"
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
lib="$root/tools/issues/lib.sh"
stamp() { date -u +%Y-%m-%dT%H:%M:%SZ; }

cd "$root" || exit 2
[ -f "$lib" ] || { echo "red-cases-293: $lib is missing (run from the change root)" >&2; exit 2; }
backup=$(mktemp "${TMPDIR:-/tmp}/red-cases-293.XXXXXX") || exit 2
cp "$lib" "$backup" || exit 2
trap 'cp "$backup" "$lib"; rm -f "$backup"' EXIT

fails=0

git show "$commit:tools/issues/lib.sh" > "$here/lib-head-293.sh" || exit 2
cp "$lib" "$here/lib-fixed-293.sh" || exit 2

probe() { # probe <log> <lib> <label>
  {
    printf '=== %s: bash red-probe-head.sh %s  (%s)\n' "$3" "$2" "$(stamp)"
    bash "$here/red-probe-head.sh" "$2" 2>&1
    printf 'exit=%s\n' "$?"
  } > "$1"
}

cases() { # cases <log> <label>
  local log="$1" label="$2" suite case_name out code
  : > "$log"
  while IFS='|' read -r suite case_name; do
    {
      printf '=== %s: bash %s --case %s  (%s)\n' "$label" "$suite" "$case_name" "$(stamp)"
      out=$(bash "$suite" --case "$case_name" 2>&1); code=$?
      printf '%s\n' "$out"
      printf 'exit=%s\n' "$code"
    } >> "$log"
    printf '%s:%s:%s\n' "$label" "$case_name" "$code" >> "${log%/*}/.exit-$$"
  done <<'EOF'
tools/issues/check-metadata-selftest.sh|check#aQuotationIsNotAMarker
tools/issues/check-metadata-selftest.sh|check#aMarkerLineBesideAQuotationIsTheRecord
tools/issue-tooling-selftest.sh|prioritise#theWriteKeepsAQuotation
EOF
}

# ---- the red half: the base commit's reader, the tree's cases --------------------------------------------
cp "$here/lib-head-293.sh" "$lib" || exit 2
probe "$here/red-probe-head.log" "$here/lib-head-293.sh" "HEAD $commit"
cases "$here/red-cases-head.log" "HEAD $commit"
red_codes=$(cat "${here}/.exit-$$"); rm -f "${here}/.exit-$$"
case "$red_codes" in
  *:0) printf 'red half: a case passes on HEAD — it does not exercise the defect: %s\n' "$red_codes"; fails=$((fails+1));;
  *) printf 'red half: the three cases fail on HEAD (%s)\n' "$(stamp)";;
esac
grep -q 'RED: ' "$here/red-probe-head.log" && printf 'red half: the probe is red on HEAD\n' ||
  { printf 'red half: the probe is not red on HEAD\n'; fails=$((fails+1)); }

# ---- the green half: the tree's own reader --------------------------------------------------------------
cp "$backup" "$lib" || exit 2
probe "$here/green-probe-head.log" "$lib" "fixed tree"
cases "$here/green-cases.log" "fixed tree"
green_codes=$(cat "${here}/.exit-$$"); rm -f "${here}/.exit-$$"
case "$green_codes" in
  *:0) ;;
  *) printf 'green half: a case fails on the fixed tree: %s\n' "$green_codes"; fails=$((fails+1));;
esac
grep -q 'green: all' "$here/green-probe-head.log" && printf 'green half: the probe is green (%s)\n' "$(stamp)" ||
  { printf 'green half: the probe is not green on the fixed tree\n'; fails=$((fails+1)); }

cmp -s "$lib" "$backup" || { printf 'the tree lib was not restored\n'; fails=$((fails+1)); }
[ "$fails" -eq 0 ] && { printf 'RED-CASES: PASS — red on %s, green on the tree (%s)\n' "$commit" "$(stamp)"; exit 0; }
printf 'RED-CASES: FAIL (%s)\n' "$(stamp)"
exit 1
