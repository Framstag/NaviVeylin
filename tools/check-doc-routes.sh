#!/usr/bin/env bash
# Resolve every guideline section reference in AGENTS.md's "Documentation Map".
#
# A reference is written as `<File>.md` §<number> "<heading text>" — the heading text is optional, and a
# number may carry a letter suffix (`§15a`). The number must be a `## ` heading number of that document;
# when heading text is given it must appear in one of the headings carrying that number, so a document that
# numbers two sections alike (`MapRendering.md` has two `## 14.`) is still resolved unambiguously. A bare
# number that its document uses twice is reported as ambiguous.
#
# The documents are taken from `guidelines/*.md` at run time, not from a list inside this script, so a
# document added later is covered without editing the check. A document that the routing table does not name
# at all is reported as unrouted.
#
# Reads text files only: no build, no device, no network. Exit 0 when every reference resolves and every
# document is routed, 1 when one does not, 2 on a usage or layout error.
#
# Usage: check-doc-routes.sh [--root <dir>]      # default root: the repository this script lives in
set -uo pipefail

root=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) root="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,18p' "${BASH_SOURCE[0]}"; exit 0 ;;
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

# 4. a §-reference to a document that does not exist, then the references the table makes.
#    A plain `.md` mention with no section reference (`guidelines/<file>.md`, a spec path) is prose and is
#    left alone.
missing_docs=0
for n in $(grep -oE '[A-Za-z0-9_-]+\.md[^|]{0,3}§' "$tmp/region" | sed 's/\.md.*//' | sort -u); do
  if ! printf '%s\n' $files | grep -qx "$n"; then
    printf "::error file=AGENTS.md::%s.md — the routing table references it, but guidelines/%s.md does not exist\n" "$n" "$n"
    missing_docs=$((missing_docs + 1))
  fi
done

sed -E "s/($named)\\.md/@@&@@/g" "$tmp/region" | awk -F'@@' -v pat="$named" '
  BEGIN { filere = "^(" pat ")\\.md$" }
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
        print file "\t" num "\t" txt
      }
    }
  }' > "$tmp/refs"

# 5. resolve them
awk -F'\t' -v unrouted="$unrouted" -v missingdocs="$missing_docs" '
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
      printf "::error file=AGENTS.md::%s.md §%s — no such section\n", $1, $2
      fail++
    } else if ($3 != "") {
      if (index(headings[key], $3) == 0) {
        printf "::error file=AGENTS.md::%s.md §%s \"%s\" — no heading with that number contains that text\n", $1, $2, $3
        fail++
      }
    } else if (count[key] > 1) {
      printf "::error file=AGENTS.md::%s.md §%s — ambiguous, %d sections carry that number: name the heading text\n", $1, $2, count[key]
      fail++
    }
  }
  END {
    if (fail > 0 || unrouted > 0 || missingdocs > 0) {
      printf "check-doc-routes: %d of %d references do not resolve", fail, total
      if (unrouted > 0) printf ", %d document(s) unrouted", unrouted
      if (missingdocs > 0) printf ", %d referenced document(s) missing", missingdocs
      printf "\n"
      exit 1
    }
    if (total == 0) { print "check-doc-routes: the Documentation Map makes no section reference"; exit 2 }
    printf "check-doc-routes: all %d section references resolve\n", total
  }' "$tmp/headings" "$tmp/refs"
