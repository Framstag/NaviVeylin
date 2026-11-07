#!/usr/bin/env bash
# Red probe for `fix-guardrail-entries-carry-their-tags` (issue #68, nvid §40).
#
# Issue #68's discharge paragraph promises that "the docs tag the rule with its item number (`§40.N`) so the
# mapping stays auditable". Three of its 60 entries name an artifact but carry no tag in the guideline section
# that owns their rule: 38, 46 and 55. Two of them state the rule there (46 at `Build.md` §2, 55 at
# `MapRendering.md` §15a) and 38 states only its worked example (§6d) while the recording rule itself is
# written nowhere.
#
# The probe answers one question per entry: does the section that owns the concern carry the entry's `§40.NN`
# tag? One tag per entry — it proves the entry reaches a reader of the section, not that every clause of a
# multi-clause bullet survived. It reads text files only — no build, no device, no network. Exit 0 when all
# three tags are present in their section, 1 when one is missing (naming it), 2 on a layout error.
#
# Usage: red-probe.sh [--root <dir>] [--junit <file>]   # default root: the nearest directory above this
#                                                     # script that holds guidelines/Build.md
# Case: guardrail-tag#eachCarriedEntryCarriesItsRecordTag
set -uo pipefail

root=""
junit=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "red-probe: --root needs a value" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    --junit) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "red-probe: --junit needs a value" >&2; exit 2; }; junit="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "red-probe: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
if [ -z "$root" ]; then
  d=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd) || exit 2
  while [ "$d" != "/" ] && [ ! -f "$d/guidelines/Build.md" ]; do d=$(dirname "$d"); done
  [ -f "$d/guidelines/Build.md" ] || { echo "red-probe: no guidelines/Build.md above this script" >&2; exit 2; }
  root="$d"
fi

missing=0
total=0
names=""
states=""
messages=""

# entry | file | exact heading of the owning section | the rule's distinguishing token
report() {
  entry="$1"; file="$root/$2"; marker="$3"; token="$4"
  total=$((total + 1))
  names="$names entry${entry}CarriesItsRecordTag"
  if [ ! -f "$file" ]; then
    printf 'MISSING §40.%s  %s does not exist\n' "$entry" "$2"; missing=$((missing + 1))
    states="${states:+$states }fail"; messages="${messages:+$messages|}$2 does not exist"; return
  fi
  start=$(grep -n -F -- "$marker" "$file" | head -1 | cut -d: -f1)
  if [ -z "$start" ]; then
    printf 'MISSING §40.%s  section "%s" not found in %s\n' "$entry" "$marker" "$2"; missing=$((missing + 1))
    states="${states:+$states }fail"; messages="${messages:+$messages|}section \"$marker\" not found in $2"; return
  fi
  end=$(awk -v s="$start" 'NR > s && /^#{2,3} /{print NR; exit}' "$file")
  [ -z "$end" ] && end=$(wc -l < "$file")
  if sed -n "${start},${end}p" "$file" | grep -q -F -- "§40.$entry"; then
    printf 'STATED  §40.%s  %s:%s-%s  "%s"\n' "$entry" "$2" "$start" "$end" "$token"
    states="${states:+$states }pass"; messages="${messages:+$messages|}"
  else
    printf 'MISSING §40.%s  %s:%s-%s ("%s") carries no §40.%s tag\n' \
      "$entry" "$2" "$start" "$end" "$marker" "$entry"
    missing=$((missing + 1)); states="${states:+$states }fail"
    messages="${messages:+$messages|}$2:$start-$end carries no §40.$entry tag"
  fi
}

report 38 guidelines/Build.md '## 4d. Attributing a red run' 'flake'
report 46 guidelines/Build.md '## 2a. Agent iteration protocol' 'newermt'
report 55 guidelines/MapRendering.md '## 15a. Stylesheet load failure' 'style error'

if [ -n "$junit" ]; then
  {
    printf '<?xml version="1.0" encoding="UTF-8"?>\n'
    printf '<testsuite name="guardrail-tag" tests="%s" failures="%s" errors="0" skipped="0" timestamp="%s">\n' \
      "$total" "$missing" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    i=0
    for name in $names; do
      i=$((i + 1))
      state=$(echo "$states" | cut -d' ' -f"$i")
      msg=$(echo "$messages" | cut -d'|' -f"$i")
      if [ "$state" = fail ]; then
        printf '  <testcase classname="guardrail-tag" name="%s">\n' "$name"
        printf '    <failure message="%s">%s</failure>\n' "$msg" "$msg"
        printf '  </testcase>\n'
      else
        printf '  <testcase classname="guardrail-tag" name="%s"/>\n' "$name"
      fi
    done
    printf '</testsuite>\n'
  } > "$junit"
fi

echo "tests=$total failures=$missing"
if [ "$missing" -eq 0 ]; then
  echo "PASS guardrail-tag#eachCarriedEntryCarriesItsRecordTag (exit 0)"
  exit 0
fi
echo "FAIL guardrail-tag#eachCarriedEntryCarriesItsRecordTag — $missing of $total entries carry no §40.N tag (exit 1)"
exit 1
