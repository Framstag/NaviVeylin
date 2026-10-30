#!/usr/bin/env bash
# Resolve every guideline section reference this project writes — the ones the routing table makes and the ones
# a guideline body makes — and every skill path the documentation names.
#
# A reference is written as `<File>.md` §<number> "<heading text>" — the heading text is optional, and a
# number may carry a letter suffix (`§15a`). The number must be a `## ` heading number of that document;
# when heading text is given it must appear in one of the headings carrying that number, so a document that
# numbers two sections alike (`MapRendering.md` has two `## 14.`) is still resolved unambiguously. A bare
# number that its document uses twice is reported as ambiguous — in the routing table and in a guideline
# body alike, which is why a bare `§14` cannot be introduced anywhere a reader follows it. A reference
# inside a fenced code block is quoted, not made, and is skipped.
#
# The documents are taken from `guidelines/*.md` at run time, not from a list inside this script, so a
# document added later is covered without editing the check. A document that the routing table does not name
# at all is reported as unrouted. An archived change and an open change artifact are history: their text is
# neither checked nor rewritten.
#
# A skill path (`.pi/skills/<name>`) named by `AGENTS.md` or by a guideline resolves against the skills under
# `.pi/skills/`, taken from the directories that carry a `SKILL.md` at run time. A path whose skill no longer
# exists — renamed, deleted, or left without its `SKILL.md` — is reported, so a document cannot keep routing a
# reader to a skill that is gone. The rule this serves is `openspec/specs/documentation-ownership/spec.md`,
# "A document that names a skill resolves to a skill that exists".
#
# A row of the table that routes a lettered sub-section of a document — `§15a`, a section a split broke up — must
# state what those sections cost, because a reader picks a section by that number, and a row that names
# sub-sections and prices nothing has lost the measurement the split was made for (`documentation-ownership`,
# "The route names the sub-section").
#
# A figure a row does state must be the measurement it claims (`documentation-ownership`, "A routing row's cost is
# the measurement it claims"): a figure written after a lettered reference must equal that section's cost in kB of
# bytes/1000 rounded to one decimal, and a range must state the cheapest and the costliest of the lettered sections
# the row names. A figure a row writes as a past measurement (`against 23.2 kB for §2 before the split`) is no
# reference's figure and is never compared. The bound a cost is held to is the sensor row's, not this check's.
#
# `--sizes` prints the cost of every section of every document the table names, one line each, marked with
# whether the table routes it. The ruler is the one `documentation-ownership` states — a section's cost is the
# byte count of its lines from the `## ` heading through the line before the next `## ` heading — and the same
# computation feeds the clause above, so the figure a row states and the number this flag prints cannot disagree.
#
# Reads text files only: no build, no device, no network. Exit 0 when every reference resolves, every document is
# routed and every row that names a sub-section states a cost, 1 when one does not, 2 on a usage or layout error.
#
# Usage: check-doc-routes.sh [--root <dir>] [--sizes]   # default root: the repository this script lives in
set -uo pipefail

root=""
sizes=0
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    --sizes) sizes=1; shift ;;
    -h|--help) sed -n '2,43p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "check-doc-routes: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$root" ] || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

agents="$root/AGENTS.md"
gdir="$root/guidelines"

[ -f "$agents" ] || { echo "check-doc-routes: no $agents" >&2; exit 2; }
[ -d "$gdir" ] || { echo "check-doc-routes: no $gdir" >&2; exit 2; }

# 1. the documents: derived from the guideline directory, one per file
files=""
for path in "$gdir"/*.md; do
  [ -f "$path" ] || continue
  name=$(basename "$path" .md)
  files="$files $name"
done
files=$(printf '%s\n' $files | sort)
if [ -z "$files" ]; then
  echo "::error file=guidelines::no guideline document found — the routing table has nothing to route to"
  exit 2
fi

# the same list as a regular-expression alternation, for the two places that must recognise a document name
named=$(printf '%s\n' $files | paste -sd '|' -)

tmp=$(mktemp -d "${TMPDIR:-/tmp}/doc-routes.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT

# 2. the routing table: from `## Documentation Map` to the next `## ` heading
awk '/^## Documentation Map$/{f=1;next} f && /^## /{exit} f{print}' "$agents" > "$tmp/region"
if [ ! -s "$tmp/region" ]; then
  echo "::error file=AGENTS.md::no '## Documentation Map' section — nothing to check"
  exit 2
fi

# 3. every document is routed, and its headings are collected: file<TAB>number<TAB>text
: > "$tmp/headings"
unrouted=0
for f in $files; do
  if ! grep -qF "$f.md" "$tmp/region"; then
    printf "::error file=AGENTS.md::guidelines/%s.md — the routing table does not name this document\n" "$f"
    unrouted=$((unrouted + 1))
  fi
  awk -v file="$f" '
    /^```/ { fence = !fence; next }
    fence { next }
    /^## / {
      h = substr($0, 4); num = ""
      if (h ~ /^[0-9]+[a-z]?\./) {
        p = index(h, ".")
        num = substr(h, 1, p - 1)
        h = substr(h, p + 1); sub(/^ /, "", h)
      }
      print file "\t" num "\t" h
    }' "$gdir/$f.md" >> "$tmp/headings"
done

# 4. a §-reference to a document that does not exist, then the references the routing table makes and the
#    references a guideline body makes.
#    A plain `.md` mention with no section reference (`guidelines/<file>.md`, a spec path) is prose and is
#    left alone, and so is a document name that is not a guideline (a spec, a retired backlog): the
#    existence rule below is the routing table's, and it stays there.
missing_docs=0
for n in $(grep -oE '[A-Za-z0-9_-]+\.md[^|]{0,3}§' "$tmp/region" | sed 's/\.md.*//' | sort -u); do
  if ! printf '%s\n' $files | grep -qx "$n"; then
    printf "::error file=AGENTS.md::%s.md — the routing table references it, but guidelines/%s.md does not exist\n" "$n" "$n"
    missing_docs=$((missing_docs + 1))
  fi
done

#    One extraction for both places. A body is read with its fenced code blocks skipped, because a block
#    that quotes a reference shows an example; the fourth field is the file the error is reported against.
extract_refs() { # $1 file to read, $2 name to report, $3 1 to skip fenced blocks
  sed -E "s/($named)\\.md/@@&@@/g" "$1" | awk -F'@@' -v pat="$named" -v src="$2" -v fences="${3:-0}" '
  BEGIN { filere = "^(" pat ")\\.md$" }
  fences == 1 && /^[[:space:]]*```/ { fence = !fence; next }
  fences == 1 && fence { next }
  {
    file = ""
    for (i = 1; i <= NF; i++) {
      if ($i ~ filere) { file = substr($i, 1, length($i) - 3); continue }
      if (file == "") continue
      chunk = $i
      while ((p = index(chunk, "§")) > 0) {
        chunk = substr(chunk, p + 1)
        if (match(chunk, /^[0-9]+[a-z]?/) == 0) continue
        num = substr(chunk, 1, RLENGTH); chunk = substr(chunk, RLENGTH + 1)
        txt = ""
        sub(/^[ \t]+/, "", chunk)
        if (substr(chunk, 1, 1) == "\"") {
          rest = substr(chunk, 2); q = index(rest, "\"")
          if (q > 0) { txt = substr(rest, 1, q - 1); chunk = substr(rest, q + 1) }
        }
        print file "\t" num "\t" txt "\t" src
      }
    }
  }'
}

: > "$tmp/refs"
extract_refs "$tmp/region" AGENTS.md 0 >> "$tmp/refs"
for f in $files; do
  extract_refs "$gdir/$f.md" "guidelines/$f.md" 1 >> "$tmp/refs"
done

# 5. the skills: derived from the directories under `.pi/skills/` that carry a SKILL.md, not from a list
#    inside this script, so a skill added later is covered without editing the check.
sdir="$root/.pi/skills"
skills=""
if [ -d "$sdir" ]; then
  for path in "$sdir"/*/SKILL.md; do
    [ -f "$path" ] || continue
    dir=$(dirname "$path")
    skills="$skills $(basename "$dir")"
  done
fi
skills=$(printf '%s\n' $skills | sed '/^$/d' | sort)

# 6. every skill path the documentation names resolves to one of them. The path is matched as a whole name,
#    so `.pi/skills/pixel-check` is not satisfied by a directory `pixel-check-extra`.
skill_total=0
skill_fail=0
for doc in "$agents" "$gdir"/*.md; do
  [ -f "$doc" ] || continue
  for ref in $(grep -oE '\.pi/skills/[A-Za-z0-9._-]+' "$doc" | sort -u); do
    skill_total=$((skill_total + 1))
    name=${ref#.pi/skills/}
    if ! printf '%s\n' $skills | grep -qx -- "$name"; then
      printf "::error file=%s::%s — no such skill: `.pi/skills/%s` is not a skill directory carrying a SKILL.md (renamed, deleted, or left without its contract)\n" \
        "${doc#$root/}" "$ref" "$name"
      skill_fail=$((skill_fail + 1))
    fi
  done
done

# 7. the cost of every section of every document the table names: the ruler the two row clauses below read, and
#    the number `--sizes` prints. A section's cost is the byte count of its lines from the `## ` heading through
#    the line before the next one — the requirement's ruler — so the figure a row states and the flag's number are
#    one computation and cannot disagree. The bound is the registry's, and nothing here compares with it.
: > "$tmp/sizes"
for f in $files; do
  LC_ALL=C awk -v file="$f" '
    /^```/ { fence = !fence }
    !fence && /^## / {
      if (have) printf "%s\t%s\t%d\t%d\n", file, num, bytes, lines
      h = substr($0, 4); num = ""
      if (h ~ /^[0-9]+[a-z]?\./) { p = index(h, "."); num = substr(h, 1, p - 1) }
      bytes = length($0) + 1; lines = 1; have = 1; next
    }
    have { bytes += length($0) + 1; lines++ }
    END { if (have) printf "%s\t%s\t%d\t%d\n", file, num, bytes, lines }
  ' "$gdir/$f.md" >> "$tmp/sizes"
done

# 7a. a row that routes a lettered sub-section of a document states what those sections cost. The row is the
#     unit, because a cost belongs beside the reference it prices; a row whose references are all whole sections
#     needs no figure and carries one only if it wants to.
cost_fail=0
while IFS= read -r row; do
  case "$row" in '|'*) ;; *) continue ;; esac   # a row, not the paragraph that introduces the table
  printf '%s' "$row" | grep -qE '§[0-9]+[a-z]' || continue
  printf '%s' "$row" | grep -qE '[0-9]+([.,][0-9]+)?[[:space:]]?kB' && continue
  label=$(printf '%s' "$row" | awk -F'|' '{ label = $2; gsub(/^ +| +$/, "", label); print label }')
  printf "::error file=AGENTS.md::the routing row '%s' names a lettered sub-section and states no cost — state the sections' cost in kB (bytes/1000, one decimal) beside the reference, because that number is what a reader picks a section by\n" "$label"
  cost_fail=$((cost_fail + 1))
done < "$tmp/region"

# 7b. the figure a row does state is the section's measurement. Two forms are compared, and they are the two the
#     table writes: a figure after a lettered reference (through the heading text it quotes, if any), which must
#     equal that section's cost in kB of bytes/1000 rounded to one decimal; and a dash-joined range, which must
#     state the cheapest and the costliest of the lettered sections the row names. A figure a row writes as a past
#     measurement (`against 23.2 kB for §2 before the split`) is no reference's figure and matches neither form,
#     so the history a row keeps is left alone by construction rather than by a rule that guesses at it.
cost_wrong=0
cost_reports=$(LC_ALL=C awk -F'\t' '
  FNR == NR { cost[$1 SUBSEP $2] = $3; next }
  /^\|/ {
    row = $0
    n = split(row, cells, "|"); label = cells[2]
    gsub(/^[ \t]+|[ \t]+$/, "", label)
    doc = ""; nref = 0
    n = split(row, tok, "`")
    for (i = 1; i <= n; i++) {
      t = tok[i]
      if (t ~ /^[A-Za-z0-9_-]+\.md$/) { doc = t; sub(/\.md$/, "", doc); continue }
      if (doc == "") continue
      rest = t
      while (match(rest, /§[0-9]+[a-z]/)) {
        ref = substr(rest, RSTART, RLENGTH); sub(/^§/, "", ref)
        rest = substr(rest, RSTART + RLENGTH)
        nref++; key[nref] = doc SUBSEP ref
        # the figure this row writes for the reference: only non-digits may stand between the two (a separator, the
        # heading text the reference quotes), so anything before the first digit is skipped and `kB` must follow
        # the number. A row that writes no figure for this reference matches nothing and is caught by 7a instead.
        p = rest
        sub(/^[^0-9]*/, "", p)
        if (!match(p, /^[0-9]+([.,][0-9]+)?[ ]?kB/)) continue
        stated = substr(p, 1, RLENGTH); sub(/[ ]?kB$/, "", stated); gsub(/,/, ".", stated)
        if (!(key[nref] in cost)) continue
        measured = sprintf("%.1f", cost[key[nref]] / 1000)
        if ((stated + 0) != (measured + 0))
          printf "the routing row \"%s\" states %s kB for %s.md §%s, but the section measures %s kB (%d bytes at bytes/1000, one decimal) — state the measurement\n", label, stated, doc, ref, measured, cost[key[nref]]
      }
    }
    # the range, if the row states one: two numbers joined by a dash, and kB after the second
    if (!match(row, /[0-9]+([.,][0-9]+)?[ \t]*[^0-9A-Za-z., \t]{1,4}[ \t]*[0-9]+([.,][0-9]+)?[ \t]?kB/)) next
    rng = substr(row, RSTART, RLENGTH)
    n = split(rng, ends, /[^0-9.,]+/)
    if (n < 2) next
    lo = ""; hi = ""
    for (i = 1; i <= nref; i++) {
      if (!(key[i] in cost)) continue
      v = cost[key[i]] + 0
      if (lo == "" || v < lo) lo = v
      if (hi == "" || v > hi) hi = v
    }
    if (lo == "") next
    lo = sprintf("%.1f", lo / 1000); hi = sprintf("%.1f", hi / 1000)
    gsub(/,/, ".", ends[1]); gsub(/,/, ".", ends[2])
    if ((ends[1] + 0) != (lo + 0) || (ends[2] + 0) != (hi + 0))
      printf "the routing row \"%s\" states the range %s-%s kB, but the lettered sections it names measure %s-%s kB (bytes/1000, one decimal) — state the measurement\n", label, ends[1], ends[2], lo, hi
  }' "$tmp/sizes" "$tmp/region")
if [ -n "$cost_reports" ]; then
  printf '%s\n' "$cost_reports" | while IFS= read -r report; do
    printf '::error file=AGENTS.md::%s\n' "$report"
  done
  cost_wrong=$(printf '%s\n' "$cost_reports" | wc -l | tr -d ' ')
fi

# 8. --sizes: the cost of every section of every document the table names, each marked with whether the table
#    routes it. The ruler is the requirement's, so this is the number a restated figure claims and the number
#    the sensor row compares with its bound; nothing is compared here, because the bound is the registry's.
if [ "$sizes" -eq 1 ]; then
  awk -F'\t' '$4 == "AGENTS.md" { print $1 SUBSEP $2 }' "$tmp/refs" | sort -u > "$tmp/routed-keys"
  LC_ALL=C awk -F'\t' -v keys="$tmp/routed-keys" '
    BEGIN { while ((getline line < keys) > 0) routed[line] = 1 }
    {
      key = $1 SUBSEP $2
      label = ($2 == "" ? "(unnumbered)" : "§" $2)
      printf "check-doc-routes: section-cost %s.md %s %s bytes %s lines %s\n", \
        $1, label, $3, $4, ((key in routed) ? "routed" : "not-routed")
    }' "$tmp/sizes"
fi

# 9. resolve the section references
awk -F'\t' -v unrouted="$unrouted" -v missingdocs="$missing_docs" -v skillfail="$skill_fail" -v skilltotal="$skill_total" -v costfail="$cost_fail" -v costwrong="$cost_wrong" '
  FNR == NR {
    key = $1 SUBSEP $2
    count[key]++
    headings[key] = headings[key] (headings[key] == "" ? "" : " || ") $3
    next
  }
  {
    total++
    key = $1 SUBSEP $2
    if (!(key in headings)) {
      printf "::error file=%s::%s.md §%s — no such section\n", $4, $1, $2
      fail++
    } else if ($3 != "") {
      if (index(headings[key], $3) == 0) {
        printf "::error file=%s::%s.md §%s \"%s\" — no heading with that number contains that text\n", $4, $1, $2, $3
        fail++
      }
    } else if (count[key] > 1) {
      printf "::error file=%s::%s.md §%s — ambiguous, %d sections carry that number: name the heading text\n", $4, $1, $2, count[key]
      fail++
    }
  }
  END {
    if (fail > 0 || unrouted > 0 || missingdocs > 0 || skillfail > 0 || costfail > 0 || costwrong > 0) {
      printf "check-doc-routes: %d of %d references do not resolve", fail, total
      if (skillfail > 0) printf ", %d of %d skill references do not resolve", skillfail, skilltotal
      if (costfail > 0) printf ", %d row(s) name a sub-section and state no cost", costfail
      if (costwrong > 0) printf ", %d stated figure(s) do not equal the section measurement", costwrong
      if (unrouted > 0) printf ", %d document(s) unrouted", unrouted
      if (missingdocs > 0) printf ", %d referenced document(s) missing", missingdocs
      printf "\n"
      exit 1
    }
    if (total == 0) { print "check-doc-routes: the Documentation Map makes no section reference"; exit 2 }
    printf "check-doc-routes: all %d section references resolve", total
    if (skilltotal > 0) printf ", all %d skill references resolve", skilltotal
    printf "\n"
  }' "$tmp/headings" "$tmp/refs"
