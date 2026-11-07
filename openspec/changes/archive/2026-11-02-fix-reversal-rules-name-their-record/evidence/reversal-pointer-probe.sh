#!/usr/bin/env bash
# reversal-pointer-probe.sh — count the rules born from a reversal that name the record.
#
# Item #730: `guidelines/Design.why.md` §13 records the reversals, and each of the five rules below exists
# because one of them was decided. The probe reads the rule's own block — from the anchor line to the next
# blank line, bullet or heading — and reports whether that block names `Design.why.md`. It reads text only,
# writes one JUnit-shaped tally, runs no build and touches no document.
#
# Usage: reversal-pointer-probe.sh [--root <dir>] [--out <file>]
# Exit:  0 every rule names the record; 1 at least one does not (the tally names each one); 2 usage.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=""
out=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] || { echo "reversal-pointer-probe: --root needs a value" >&2; exit 2; }; root="$2"; shift 2 ;;
    --out)  [ $# -ge 2 ] || { echo "reversal-pointer-probe: --out needs a value" >&2; exit 2; }; out="$2"; shift 2 ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "reversal-pointer-probe: unknown argument '$1'" >&2; exit 2 ;;
  esac
done

# The default root is the tree that holds `guidelines/`, found by walking up, not a fixed number of levels: an
# archived change sits one directory deeper than an open one, so the four levels that fit
# `openspec/changes/<change>/evidence` resolve to `openspec/` once the change is archived and every rule block
# then reads as missing (measured in review round 2, 2026-11-02). A `--root` still overrides it.
if [ -z "$root" ]; then
  root="$here"
  while [ "$root" != "/" ] && [ ! -d "$root/guidelines" ]; do root=$(dirname "$root"); done
  [ -d "$root/guidelines" ] || {
    echo "reversal-pointer-probe: no directory above $here holds guidelines/ — pass --root" >&2
    exit 2
  }
fi

# file | anchor — the line the rule's own statement starts on. The rule sites were re-derived on base
# e190d872: the 2026-10-31 split moved §7/§8 of MapRendering.md into MapFollow.md and the two-flavor rule
# from Regulatory.md into DesignCrossVariant.md §8b, and the host-rendered panel rule is the host-constraint
# bullet of §8a.
rules=(
  "guidelines/DesignCrossVariant.md|**Every car guidance surface reads the step model**"
  "guidelines/DesignCrossVariant.md|Prefer host-rendered"
  "guidelines/DesignCrossVariant.md|**Rule: phone and AAOS are separate build flavors under one applicationId**"
  "guidelines/MapFollow.md|Bearing smoothing lives in \`LocationService\`"
  "guidelines/MapFollow.md|**Rule: Render target**"
)

tests=0
failures=0
failed_names=""
cases=""
for entry in "${rules[@]}"; do
  file=${entry%%|*}
  anchor=${entry#*|}
  tests=$((tests + 1))
  block=$(awk -v anchor="$anchor" '
    index($0, anchor) && !seen { seen = 1; print; next }
    seen {
      if ($0 == "" || $0 ~ /^- / || $0 ~ /^## /) exit
      print
    }' "$root/$file")
  name=$(printf '%s / %s' "$file" "$anchor")
  if [ -z "$block" ]; then
    failures=$((failures + 1))
    failed_names="$failed_names $(printf '%s' "$name" | tr ' /`*' '----')"
    cases="$cases<testcase classname=\"reversal-pointer\" name=\"$(printf '%s' "$name" | sed 's/[&<>"]/./g')\"><failure message=\"the anchor names no line in the file\"/></testcase>"
    continue
  fi
  if printf '%s\n' "$block" | grep -qF 'Design.why.md'; then
    cases="$cases<testcase classname=\"reversal-pointer\" name=\"$(printf '%s' "$name" | sed 's/[&<>"]/./g')\"/>"
  else
    failures=$((failures + 1))
    failed_names="$failed_names $name"
    cases="$cases<testcase classname=\"reversal-pointer\" name=\"$(printf '%s' "$name" | sed 's/[&<>"]/./g')\"><failure message=\"the rule block names no Design.why.md pointer\"/></testcase>"
  fi
done

xml=$(printf '<testsuite name="reversal-pointer" tests="%s" failures="%s" errors="0"><properties><property name="root" value="%s"/></properties>%s</testsuite>' \
  "$tests" "$failures" "$root" "$cases")
if [ -n "$out" ]; then
  printf '%s\n' "$xml" > "$out"
fi
printf 'reversal-pointer-probe: tests=%s failures=%s\n' "$tests" "$failures"
if [ "$failures" -ne 0 ]; then
  printf 'reversal-pointer-probe: no Design.why.md pointer in:%s\n' "$failed_names" >&2
  exit 1
fi
exit 0
