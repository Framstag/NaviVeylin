#!/usr/bin/env bash
# red-probe.sh — the four cases of this change, over `guidelines/Build.md` and the routing table in `AGENTS.md`.
#
# The defect is a documentation shape, so the case that exercises it reads the documents: a routed section of
# `guidelines/Build.md` must be one read (at most $bound bytes), its number must survive a split, the routing
# table must name the sub-section that answers the reader, and a split must move the text rather than rewrite
# it. No build, no device, no network — text and git only, exactly like `tools/check-doc-routes.sh`.
#
# The bound is this change's number, measured on the four offenders: `§2` 23 233 B, `§4` 23 378 B, `§6`
# 22 051 B, `§10` 31 566 B on the base commit, against `§7` 8 875 B and `§9` 7 292 B, the next largest
# sections of the file, which the split leaves alone. 12 000 B is ~3 k tokens: under a fifth of the 17 334
# tokens a child's first response reports as its whole start-of-session prompt.
#
# Exit 0 when every case passes, 1 when one fails, 2 on a usage error.
#
# Usage: red-probe.sh [--xml <path>] [--base <sha>] [--bound <bytes>] [<case>]
#   cases: bound | numbers | route | verbatim          (no case argument runs all four)
#   --bound lowers the threshold on purpose: the revert-check of `routed-read#bound` runs it at 5 000.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
while [ ! -f "$root/AGENTS.md" ]; do
  [ "$root" != "/" ] || { echo "red-probe: no AGENTS.md above $(dirname "${BASH_SOURCE[0]}")" >&2; exit 2; }
  root=$(dirname "$root")
done
build="$root/guidelines/Build.md"
agents="$root/AGENTS.md"
[ -f "$build" ] || { echo "red-probe: no $build" >&2; exit 2; }
[ -f "$agents" ] || { echo "red-probe: no $agents" >&2; exit 2; }

bound=12000
base=${RED_PROBE_BASE:-3180fff93ada7a530a4c2b2b6966905c743e373c}
xml=""
case_filter=""
while [ $# -gt 0 ]; do
  case "$1" in
    --xml) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "red-probe: $1 needs a value" >&2; exit 2; }; xml="$2"; shift 2 ;;
    --bound) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "red-probe: $1 needs a value" >&2; exit 2; }; bound="$2"; shift 2 ;;
    --base) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "red-probe: $1 needs a value" >&2; exit 2; }; base="$2"; shift 2 ;;
    -h|--help) sed -n '2,18p' "${BASH_SOURCE[0]}"; exit 0 ;;
    -*) echo "red-probe: unknown argument '$1'" >&2; exit 2 ;;
    *) case_filter="$1"; shift ;;
  esac
done

# `## ` headings of Build.md as `<line> <number>`, the number with its letter suffix when it carries one.
headings() {
  awk '/^## / { h = substr($0, 4); num = ""
                if (h ~ /^[0-9]+[a-z]?\./) { p = index(h, "."); num = substr(h, 1, p - 1) }
                print NR "\t" num }' "$build"
}

# The body of one section: from its heading to the line before the next `## ` heading.
body_bytes() { # $1 first line, $2 last line
  sed -n "$1,$2p" "$build" | wc -c
}

# every section as `<number> <bytes>` — the measurement both the `bound` case and the evidence quote.
section_sizes() {
  local out=""
  local prev_line="" prev_num=""
  while IFS=$'\t' read -r line num; do
    [ -n "$num" ] || continue
    if [ -n "$prev_line" ]; then
      out="$out$prev_num $(body_bytes "$prev_line" $((line - 1)))"$'\n'
    fi
    prev_line=$line; prev_num=$num
  done <<< "$(headings)"
  local total
  total=$(wc -l < "$build")
  out="$out$prev_num $(body_bytes "$prev_line" "$total")"$'\n'
  printf '%s' "$out"
}

case_bound() {
  local worst="" bytes num bad=0
  while read -r num bytes; do
    [ -n "$bytes" ] || continue
    if [ "$bytes" -gt "$bound" ]; then
      bad=$((bad + 1))
      worst="$worst §$num=$bytes"
    fi
  done <<< "$(section_sizes | sort -k2 -rn)"
  if [ "$bad" -gt 0 ]; then
    printf 'FAIL routed-read#bound — %s section(s) of Build.md exceed %s bytes:%s\n' "$bad" "$bound" "$worst"
    return 1
  fi
  printf 'PASS routed-read#bound — every section of Build.md is at most %s bytes\n' "$bound"
  return 0
}

case_numbers() {
  local num missing=""
  for num in 1 2 3 4 5 6 7 8 9 10 11 12; do
    printf '%s\n' "$(headings | cut -f2)" | grep -qx "$num" || missing="$missing §$num"
  done
  if [ -n "$missing" ]; then
    printf 'FAIL routed-read#numbers — Build.md no longer carries:%s\n' "$missing"
    return 1
  fi
  local out
  if ! out=$(cd "$root" && bash tools/check-doc-routes.sh --root "$root" 2>&1); then
    printf 'FAIL routed-read#numbers — the route check refused the tree: %s\n' "$out"
    return 1
  fi
  printf 'PASS routed-read#numbers — §1-§12 are all present and %s\n' "$out"
  return 0
}

case_route() {
  local region missing="" n
  region=$(awk '/^## Documentation Map$/{f=1;next} f && /^## /{exit} f{print}' "$agents")
  for n in 2a 4a 6a 10a; do
    printf '%s\n' "$region" | grep -qE "Build\.md.*§$n([^0-9a-z]|$)" || missing="$missing §$n"
  done
  if [ -n "$missing" ]; then
    printf 'FAIL routed-read#route — the routing table names no sub-section of:%s\n' "$missing"
    return 1
  fi
  local count
  count=$(printf '%s\n' "$region" | grep -oE 'Build\.md[^|]*' | grep -oE '§[0-9]+[a-z]' | sort -u | wc -l)
  printf 'PASS routed-read#route — the routing table names %s lettered Build.md sub-sections\n' "$count"
  return 0
}

case_verbatim() {
  local num letters bad=0
  for num in 2 4 6 10; do
    letters=$(headings | cut -f2 | grep -cE "^$num[a-z]$")
    if [ "$letters" -lt 4 ]; then
      printf 'FAIL routed-read#verbatim — §%s carries %s lettered sub-sections, fewer than 4\n' "$num" "$letters"
      bad=$((bad + 1))
    fi
  done
  [ "$bad" -eq 0 ] || return 1
  # No sentence is rewritten: every line the split removes is a promoted heading or a line whose only change is
  # a retargeted `§N`, so once both sides are normalised (heading level stripped, `§N` reduced to `§`) every
  # removed line still has a counterpart on the added side.
  local diff added removed lost
  diff=$(cd "$root" && git diff --unified=0 "$base" -- guidelines/Build.md 2>/dev/null)
  normalise() { sed -E 's/^[+-]//; s/^#+ [0-9]+[a-z]?\. /### /; s/§[0-9]+[a-z]?–§[0-9]+[a-z]?/§/g; s/§[0-9]+[a-z]?/§/g' | sort; }
  added=$(printf '%s\n' "$diff" | grep '^+[^+]' | normalise)
  removed=$(printf '%s\n' "$diff" | grep '^-[^-]' | normalise)
  lost=$(comm -23 <(printf '%s\n' "$removed") <(printf '%s\n' "$added") | sed '/^$/d' | wc -l)
  if [ "${lost:-0}" -ne 0 ]; then
    printf 'FAIL routed-read#verbatim — %s line(s) of Build.md are rewritten rather than moved:\n' "$lost"
    comm -23 <(printf '%s\n' "$removed") <(printf '%s\n' "$added") | sed '/^$/d' | head -5 | sed 's/^/  /'
    return 1
  fi
  printf 'PASS routed-read#verbatim — every offender is split into lettered sub-sections and no sentence is rewritten\n'
  return 0
}

ran=0
failed=0
log=""
try_case() { ran=$((ran + 1)); if "$1"; then :; else failed=$((failed + 1)); fi; }

if [ -n "$case_filter" ]; then
  case "$case_filter" in
    bound) try_case case_bound ;;
    numbers) try_case case_numbers ;;
    route) try_case case_route ;;
    verbatim) try_case case_verbatim ;;
    *) echo "red-probe: unknown case '$case_filter'" >&2; exit 2 ;;
  esac
else
  try_case case_bound
  try_case case_numbers
  try_case case_route
  try_case case_verbatim
fi

printf 'red-probe: %s of %s cases failed\n' "$failed" "$ran"
if [ -n "$xml" ]; then
  {
    printf '<?xml version="1.0" encoding="UTF-8"?>\n'
    printf '<testsuite name="evidence/red-probe.sh" tests="%s" failures="%s" errors="0" timestamp="%s">\n' \
      "$ran" "$failed" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf '  <system-out>red-probe: %s of %s cases failed (bound=%s bytes, base=%s)</system-out>\n' \
      "$failed" "$ran" "$bound" "$base"
    printf '</testsuite>\n'
  } > "$xml"
fi
[ "$failed" -eq 0 ] || exit 1
exit 0
