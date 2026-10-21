#!/usr/bin/env bash
# Red probe for `fix-uncarried-guardrail-entries` (issue #69, nvid §134).
#
# Issue #69 keeps the guardrails extracted from `ki_processing_failures.log`: `[open]` marks an entry whose
# named artifact does not carry the rule yet, and an entry *without* the marker is a cross-reference to a
# rule that already exists. Four entries state a rule that no guideline section, skill or CI step states —
# three of them cross-reference a neighbouring §40 trap, and the fourth (65) names skills that use `--continue`
# without stating when a whole-suite run needs it — so the record reads as carried while the rule is readable
# only in the tracker.
#
# The probe answers, per entry, one question: does the guideline section that owns the concern carry one
# distinguishing token of the entry's rule? **One token per entry** — it proves the rule reached the section,
# not that every clause of a multi-clause bullet survived, so a partial deletion of a bullet can still pass it.
# It reads text files only — no build, no device, no network. Exit 0 when all four tokens are present in their
# section, 1 when one is missing (naming it), 2 on a layout error.
#
# Usage: red-probe.sh [--root <dir>]        # default root: the nearest directory above this script that
#                                            # holds guidelines/Build.md
# Case: record-carriage#eachRecordedRuleIsStatedInItsSection (the record's shorthand; the assertion is the
#        one token per entry, described above)
set -uo pipefail

root=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "red-probe: --root needs a value" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
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

# entry | file | section marker (exact line prefix of the owning section) | token that states the rule
report() {
  file="$root/$2"; marker="$3"; token="$4"
  total=$((total + 1))
  if [ ! -f "$file" ]; then
    printf 'MISSING §134.%s  %s does not exist\n' "$1" "$2"; missing=$((missing + 1)); return
  fi
  start=$(grep -n -F -- "$marker" "$file" | head -1 | cut -d: -f1)
  if [ -z "$start" ]; then
    printf 'MISSING §134.%s  section "%s" not found in %s\n' "$1" "$marker" "$2"; missing=$((missing + 1)); return
  fi
  end=$(awk -v s="$start" 'NR > s && /^#{2,3} /{print NR; exit}' "$file")
  [ -z "$end" ] && end=$(wc -l < "$file")
  if sed -n "${start},${end}p" "$file" | grep -q -F -- "$token"; then
    printf 'STATED  §134.%s  %s:%s  "%s"\n' "$1" "$2" "${start}-${end}" "$token"
  else
    printf 'MISSING §134.%s  %s:%s ("%s") does not state "%s"\n' "$1" "$2" "${start}-${end}" "$marker" "$token"
    missing=$((missing + 1))
  fi
}

report 64 guidelines/Build.md  "A suite run that died mid-flight"                    --max-workers=1
report 65 guidelines/Build.md  "## 3. Commands reference"                            --continue
report 73 guidelines/Design.md "### Kotlin / Compose / Robolectric traps"            KotlinNothingValueException
report 76 guidelines/Design.md "### Kotlin / Compose / Robolectric traps"            PartiallyExpanded

printf '\nentries=%s missing=%s\n' "$total" "$missing"
case=record-carriage#eachRecordedRuleIsStatedInItsSection
if [ "$missing" -eq 0 ]; then
  printf 'PASS %s (exit 0)\n' "$case"
  exit 0
fi
printf 'FAIL %s — %s of %s recorded rules are stated only in the tracker (exit 1)\n' "$case" "$missing" "$total"
exit 1
