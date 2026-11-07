#!/usr/bin/env bash
# red-probe.sh — the three cases of this change, over `guidelines/UI.md` "Keeping this document honest".
#
# The defect is a documentation shape, so the cases read the document. The honesty rule names two tables
# (§1's parity table and §2's glyph table) and keys its obligation on labels, styling and hierarchy, so a
# number a section restates from a spec requirement or a code constant carries no update obligation and
# names no case that holds it to its source (`TODO.md` §187 — the §8 reroute-policy bullet kept 50 m / 10 s
# through the change that moved it). The cases hold the widened rule to the document:
#
#   1. the honesty section states the restatement obligation and names every case the document names —
#      the set of `*Test` identifiers in the body equals the set in the section, so a restatement that gains
#      a guard without being declared here fails;
#   2. every case the section names resolves to a test source in the tree;
#   3. every section the section names resolves by number **and** heading text in the document.
#
# No build, no device, no network. Exit 0 when every case passes, 1 when one fails, 2 on a usage error.
#
# Usage: red-probe.sh [--xml <path>] [<case>]
#   cases: ui-honesty#a-restatement-names-its-guard
#          ui-honesty#a-listed-guard-is-a-case-in-the-tree
#          ui-honesty#a-listed-section-resolves
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
    -h|--help) sed -n '2,22p' "${BASH_SOURCE[0]}"; exit 0 ;;
    -*) echo "red-probe: unknown argument '$1'" >&2; exit 2 ;;
    *) case_filter="$1"; shift ;;
  esac
done
case "$case_filter" in
  ''|'ui-honesty#a-restatement-names-its-guard'|'ui-honesty#a-listed-guard-is-a-case-in-the-tree'|'ui-honesty#a-listed-section-resolves') ;;
  *) echo "red-probe: unknown case '$case_filter'" >&2; exit 2 ;;
esac

# The honesty section: its `## ` heading to the end of the document (it is the last section).
honesty_section() {
  sed -n '/^## Keeping this document honest/,$p' "$ui"
}

# The case identifiers a block names, one per line, sorted and unique.
case_identifiers() {
  grep -oE '[A-Za-z][A-Za-z0-9]*Test[A-Za-z0-9]*' | sort -u
}

# The section references the honesty section names, as `<number> "<heading text>"`.
stated_sections() {
  honesty_section | grep -oE '§[0-9]+[a-z]? "[^"]+"' | sort -u
}

case_a_restatement_names_its_guard() {
  local body ids missing="" extra="" text
  text=$(honesty_section | tr '\n' ' ' | sed -E 's/[[:space:]]+/ /g')
  for token in 'restates' 'name the case that holds'; do
    case "$text" in
      *"$token"*) ;;
      *) missing="$missing [$token]" ;;
    esac
  done
  body=$(case_identifiers < "$ui")
  ids=$(honesty_section | case_identifiers)
  local unlisted
  unlisted=$(comm -23 <(printf '%s\n' "$body") <(printf '%s\n' "$ids") | sed '/^$/d' | tr '\n' ' ')
  local unresolved
  unresolved=$(comm -13 <(printf '%s\n' "$body") <(printf '%s\n' "$ids") | sed '/^$/d' | tr '\n' ' ')
  if [ -n "$missing" ] || [ -n "$unlisted" ] || [ -n "$unresolved" ]; then
    printf 'FAIL ui-honesty#a-restatement-names-its-guard — the honesty section'
    [ -n "$missing" ] && printf ' does not state:%s' "$missing"
    [ -n "$unlisted" ] && printf ' leaves these guarded restatements undeclared: %s' "$unlisted"
    [ -n "$unresolved" ] && printf ' names cases the document body never names: %s' "$unresolved"
    printf '\n'
    return 1
  fi
  printf 'PASS ui-honesty#a-restatement-names-its-guard — every case the document names is declared by the honesty section, under an obligation that names the case that guards a restatement\n'
  return 0
}

case_a_listed_guard_is_a_case_in_the_tree() {
  local missing="" id path
  for id in $(honesty_section | case_identifiers); do
    path=$(grep -rlF --include='*Test.kt' --include='*Test.java' -- "$id" \
      "$root/app/src/test" "$root/auto/src/test" "$root/core/src/test" "$root/buildSrc/src/test" 2>/dev/null | head -1)
    [ -n "$path" ] || missing="$missing $id"
  done
  if [ -n "$missing" ]; then
    printf 'FAIL ui-honesty#a-listed-guard-is-a-case-in-the-tree — no test source carries:%s\n' "$missing"
    return 1
  fi
  printf 'PASS ui-honesty#a-listed-guard-is-a-case-in-the-tree — every guard the honesty section names resolves to a test source\n'
  return 0
}

case_a_listed_section_resolves() {
  local missing="" ref number heading found
  while IFS= read -r ref; do
    [ -n "$ref" ] || continue
    number=${ref#§}
    number=${number%% *}
    heading=${ref#* }
    heading=${heading#\"}
    heading=${heading%\"}
    [ -n "$number" ] && [ -n "$heading" ] || continue
    found=$(grep -F "## ${number}. " "$ui" | grep -cF -- "$heading")
    [ "$found" -ge 1 ] || missing="$missing §${number} \"${heading}\""
  done <<< "$(stated_sections)"
  if [ -n "$missing" ]; then
    printf 'FAIL ui-honesty#a-listed-section-resolves — the document has no such section:%s\n' "$missing"
    return 1
  fi
  printf 'PASS ui-honesty#a-listed-section-resolves — every section the honesty section names resolves by number and heading text\n'
  return 0
}

ran=0
failed=0
try_case() { ran=$((ran + 1)); if "$1"; then :; else failed=$((failed + 1)); fi; }

try_case case_a_restatement_names_its_guard
try_case case_a_listed_guard_is_a_case_in_the_tree
try_case case_a_listed_section_resolves

printf 'red-probe: %s of %s cases failed\n' "$failed" "$ran"
if [ -n "$xml" ]; then
  {
    printf '<?xml version="1.0" encoding="UTF-8"?>\n'
    printf '<testsuite name="evidence/red-probe.sh" tests="%s" failures="%s" errors="0" timestamp="%s">\n' \
      "$ran" "$failed" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf '  <system-out>red-probe: %s of %s cases failed (guidelines/UI.md "Keeping this document honest")</system-out>\n' \
      "$failed" "$ran"
    printf '</testsuite>\n'
  } > "$xml"
fi
[ "$failed" -eq 0 ] || exit 1
exit 0
