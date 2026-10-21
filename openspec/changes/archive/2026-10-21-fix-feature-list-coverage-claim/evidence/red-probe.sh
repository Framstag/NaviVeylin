#!/usr/bin/env bash
# Red/green probe for the change `fix-feature-list-coverage-claim` (issue #199/#133).
#
# Every case name a guideline states in a `Cases:` paragraph must be a case some tool script carries —
# `guidelines/FeatureList.md`'s own maintenance rule ("a rule in this document names the case that enforces it;
# a rule with no such case does not belong here"). Reads text files only: no build, no device, no network.
#
# Usage: bash red-probe.sh [--root <dir>]     # default: the repository root this file's change lives in
# Exit 0 when every stated case is carried by a tool script, 1 when one is not (printed as `MISSING <case>`).
set -uo pipefail

root=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] || { echo "red-probe: --root needs a value" >&2; exit 2; }; root="$2"; shift 2 ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "red-probe: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$root" ] || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)

work=$(mktemp -d) || { echo "red-probe: cannot create a scratch directory" >&2; exit 1; }
trap 'rm -rf "$work"' EXIT

# A `Cases:` paragraph, joined from its wrapped lines, one paragraph per line.
awk '
  FNR == 1 { if (buf != "") print buf; buf = ""; inblock = 0 }
  /^Cases:/ { if (buf != "") print buf; buf = $0; inblock = 1; next }
  inblock && /^[[:space:]]*$/ { print buf; buf = ""; inblock = 0; next }
  inblock { buf = buf " " $0; next }
  END { if (buf != "") print buf }
' "$root"/guidelines/*.md > "$work/cases.txt" || exit 1

grep -o '`[^`]*`' "$work/cases.txt" | tr -d '`' | sort -u > "$work/names.txt" || true

# The suites: every shell script under tools/, plus the buildSrc Kotlin tests' own case names.
mapfile -t suites < <(find "$root/tools" -name '*.sh' -type f 2>/dev/null; find "$root/buildSrc" -name '*.kt' -type f 2>/dev/null)
[ "${#suites[@]}" -gt 0 ] || { echo "red-probe: no suite files under $root/tools" >&2; exit 2; }

missing=0
while IFS= read -r name; do
  [ -n "$name" ] || continue
  grep -qF -- "$name" "${suites[@]}" 2>/dev/null || { printf 'MISSING %s\n' "$name"; missing=$((missing + 1)); }
done < "$work/names.txt"

printf 'stated cases: %s, without a carrying case: %s\n' "$(grep -c . "$work/names.txt" || true)" "$missing"
[ "$missing" -eq 0 ] || exit 1
