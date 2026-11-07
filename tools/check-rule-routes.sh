#!/usr/bin/env bash
# check-rule-routes.sh — the reading set a change's paths derive from the tree.
#
# A review's reading set is the guideline sections and the named rules that own the paths a change touched,
# derived through the path→rules map (`tools/doc-routes.json`, whose path this check reads from
# `tools/tooling.json` `docRoutes` — no glob is named here). The requirement is `documentation-ownership`,
# "The review's reading set is derived from the change's paths": a review is graded against the derived set
# rather than against the sections the change's own artifacts choose, and a change may *add* a section to its
# set by naming it.
#
# Four invariants make the map hard to rot, and all four are refusals here rather than a reviewer's note:
#
#   * a glob that matches no tracked path — a package or module rename orphans the rules a reader is told to
#     read, and nothing else would say so;
#   * a named rule under `guidelines/` that no glob reaches — every rule has a reader before it has a
#     violation;
#   * a normative document under `guidelines/` that no glob matches — a document no glob names derives
#     nothing for its own edits, which is how six documents were silently orphaned when a route split renamed
#     their coverage away (#733);
#   * a route whose glob sits under `guidelines/` and carries a document its glob does not match — a
#     document's route names that document, so an entry about a document the glob never reaches is the same
#     orphan one level down.
#
# The last two read one fact from either end — a document is reached, and the route that claims it matches it —
# because a named rule being reached says nothing about *which* path derives it: `guidelines/MapCar.md`'s rules
# were all reached, under `app/src/main/java/**`, while no glob matched a `MapCar.md` edit at all.
#
# Modes:
#   (default)            check all four invariants over the tree; exit 1 on any
#   --list               print the map: one line per section and per rule, with the glob that derives it
#   --diff <range>       print the reading set for the paths a git range touched; with `--change <dir>` also
#                        report the derived sections the change's artifacts do not name (the set may only
#                        grow, never shrink)
#
# Reads text files only: no build, no device, no token, no network. Exit 0 when every glob matches a tracked
# path, every named rule is reached, every normative document is named by a glob and every `guidelines/` route
# carries only the documents its glob matches; 1 when one of the four is not; 2 on a usage or layout error.
#
# Usage: check-rule-routes.sh [--root <dir>] [--list] [--diff <range> [--change <dir>]]
set -uo pipefail

root=""
mode="check"
range=""
change_dir=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    --list) mode="list"; shift ;;
    --diff) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; mode="diff"; range="${2:-}"; shift 2 ;;
    --change) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; change_dir="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,36p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) printf 'check-rule-routes: unknown argument %s\n' "$1" >&2; exit 2 ;;
  esac
done
[ -n "$root" ] || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

profile="$root/tools/tooling.json"
[ -f "$profile" ] || { printf 'check-rule-routes: no %s\n' "$profile" >&2; exit 2; }
map_rel=$(jq -r '.docRoutes // empty' "$profile" 2>/dev/null) || { printf 'check-rule-routes: %s is not valid JSON\n' "$profile" >&2; exit 2; }
[ -n "$map_rel" ] || { printf 'check-rule-routes: %s declares no docRoutes path\n' "$profile" >&2; exit 2; }
map_path="$root/$map_rel"
[ -f "$map_path" ] || { printf 'check-rule-routes: no map at %s\n' "$map_rel" >&2; exit 2; }
jq -e '.routes | type == "array"' "$map_path" >/dev/null 2>&1 || { printf 'check-rule-routes: %s has no routes array\n' "$map_rel" >&2; exit 2; }

gdir="$root/guidelines"
[ -d "$gdir" ] || { printf 'check-rule-routes: no %s\n' "$gdir" >&2; exit 2; }

tmp=$(mktemp -d "${TMPDIR:-/tmp}/check-rule-routes.XXXXXX") || { printf 'check-rule-routes: no scratch directory\n' >&2; exit 2; }
trap 'rm -rf "$tmp"' EXIT

# a `**` glob is matched the way a shell `case` matches it: `*` crosses `/`, so `app/**` is the subtree and
# `**/*.kt` is every Kotlin source in the tree. The map's globs are chosen so that reading is unambiguous.
glob_match() { # $1 path, $2 glob
  case "$1" in $2) return 0 ;; *) return 1 ;; esac
}

tracked_paths() { # the tree's own paths, one per line, relative to the root
  if git -C "$root" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git -C "$root" ls-files
  else
    ( cd "$root" && find . -type f -not -path './.git/*' | sed 's|^\./||' ) | sort -u
  fi
}

# The named rules the normative documents carry: `**Rule: <name>**` per section, the bold span being the
# name. The extraction is the one `tools/check-doc-routes.sh` uses (multi-line names included), reduced to
# the document and the name — the reached-ness is a document+name fact.
extract_rules() { # $1 file, $2 document name → "<doc><TAB><name>", or "!UNNAMED<TAB><doc><TAB><line>"
  awk -v doc="$2" '
    /^```/ { fence = !fence; next }
    fence { next }
    {
      line = $0
      while (1) {
        if (!inrule) { p = index(line, "**Rule:"); if (p == 0) break; inrule = 1; line = substr(line, p + 7); name = "" }
        q = index(line, "**")
        if (q > 0) {
          add = substr(line, 1, q - 1)
          name = (name == "") ? add : name " " add
          gsub(/[ \t]+/, " ", name); sub(/^ /, "", name); sub(/ $/, "", name)
          if (name == "") print "!UNNAMED\t" doc "\t" NR
          else print doc "\t" name
          inrule = 0; name = ""; line = substr(line, q + 2)
        } else { name = (name == "") ? line : name " " line; break }
      }
    }' "$1"
}

base_documents() {
  for path in "$gdir"/*.md; do
    [ -f "$path" ] || continue
    case "$(basename "$path" .md)" in *.why|*.recipes) continue ;; esac
    printf '%s\n' "$(basename "$path" .md)"
  done
}

# The rule names the map reaches: the map's rule entry is `<File>.md §N "<name>"`.
map_reached() { # → "<doc><TAB><name>" per line
  jq -r '.routes[].rules[]?' "$map_path" \
    | sed -E 's/^([A-Za-z0-9_-]+)\.md §[0-9]+[a-z]? "(.*)"$/\1\t\2/'
}

# --------------------------------------------------------------------------------------------- --list

if [ "$mode" = "list" ]; then
  jq -r '.routes[] | .glob as $g | (.sections[]? | "section\t\($g)\t\(.)"), (.rules[]? | "rule\t\($g)\t\(.)")' "$map_path"
  printf 'check-rule-routes: %s glob(s), %s named rule(s) reached\n' \
    "$(jq -r '.routes | length' "$map_path")" "$(map_reached | sort -u | grep -c . || true)"
  exit 0
fi

# --------------------------------------------------------------------------------- the derived reading set

# The section ids a map entry names, normalised to `<Doc>#<number>`.
section_ids() { sed -E 's/^([A-Za-z0-9_-]+)\.md §([0-9]+[a-z]?).*/\1#\2/'; }

# The section ids a change's artifacts name: a `<Doc>.md` mention followed by a `§<number>`. A change may
# name a section the map does not derive (an addition); this is only used to check it names every one it
# must.
change_section_ids() { # $1 directory
  grep -rhoE '[A-Za-z0-9_-]+\.md[^A-Za-z0-9]{0,3}§[0-9]+[a-z]?' "$1" 2>/dev/null \
    | sed -E 's/^([A-Za-z0-9_-]+)\.md.*§([0-9]+[a-z]?)$/\1#\2/' | sort -u
}

if [ "$mode" = "diff" ]; then
  git -C "$root" rev-parse --is-inside-work-tree >/dev/null 2>&1 || {
    printf 'check-rule-routes: --diff needs a git working tree at %s\n' "$root" >&2
    exit 2
  }
  git -C "$root" diff --name-only "$range" > "$tmp/changed" || {
    printf 'check-rule-routes: cannot diff %s\n' "$range" >&2
    exit 2
  }
  : > "$tmp/derived"
  while IFS= read -r p; do
    [ -n "$p" ] || continue
    while IFS= read -r route; do
      [ -n "$route" ] || continue
      glob_match "$p" "$(jq -r '.glob' <<< "$route")" || continue
      jq -r '.sections[]?' <<< "$route" >> "$tmp/derived"
      jq -r '.rules[]?' <<< "$route" >> "$tmp/derived"
    done < <(jq -c '.routes[]' "$map_path")
  done < "$tmp/changed"
  sort -u "$tmp/derived" -o "$tmp/derived"
  printf 'check-rule-routes: the reading set for %s\n' "$range"
  sed 's/^/  /' "$tmp/derived"
  if [ -n "$change_dir" ]; then
    [ -d "$change_dir" ] || { printf 'check-rule-routes: no change directory %s\n' "$change_dir" >&2; exit 2; }
    change_section_ids "$change_dir" > "$tmp/named"
    missing=0
    while IFS= read -r id; do
      [ -n "$id" ] || continue
      case "$id" in *'#'*) ;; *) continue ;; esac
      if ! grep -qxF "$id" "$tmp/named"; then
        printf '::error file=%s::the map derives the section %s for the paths this change touched, and the change does not name it — a change may add to its reading set, never shrink it\n' "$change_dir" "${id//#/ §}"
        missing=$((missing + 1))
      fi
    done < <(grep -E '^[A-Za-z0-9_-]+\.md §' "$tmp/derived" | section_ids | sort -u)
    [ "$missing" -eq 0 ] || exit 1
  fi
  exit 0
fi

# --------------------------------------------------------------------------------- the four invariants

tracked_paths > "$tmp/tracked"
[ -s "$tmp/tracked" ] || { printf 'check-rule-routes: the tree holds no path — nothing to match a glob against\n' >&2; exit 2; }

glob_fail=0
while IFS= read -r route; do
  [ -n "$route" ] || continue
  glob=$(jq -r '.glob' <<< "$route")
  matched=0
  while IFS= read -r p; do
    if glob_match "$p" "$glob"; then matched=1; break; fi
  done < "$tmp/tracked"
  if [ "$matched" -eq 0 ]; then
    printf '::error file=%s::the glob %s matches no tracked path — a moved package or module otherwise orphans the rules a reader is told to read\n' "$map_rel" "$glob"
    glob_fail=$((glob_fail + 1))
  fi
done < <(jq -c '.routes[]' "$map_path")

: > "$tmp/reached"
map_reached >> "$tmp/reached"
: > "$tmp/allrules.raw"
while IFS= read -r doc; do
  [ -n "$doc" ] || continue
  extract_rules "$gdir/$doc.md" "$doc" >> "$tmp/allrules.raw"
done < <(base_documents)

# a rule whose name is empty or whitespace-only is reported with its file and line, instead of being skipped
# in silence by an extraction that asked only for `**Rule: `.
unnamed_rules=0
while IFS=$'\t' read -r doc nline; do
  [ -n "$doc" ] || continue
  printf '::error file=guidelines/%s.md::line %s states the token **Rule:** with no name — a rule is named `**Rule: <name>** <statement>.`, so the empty case is reported rather than skipped\n' "$doc" "$nline"
  unnamed_rules=$((unnamed_rules + 1))
done < <(grep '^!UNNAMED' "$tmp/allrules.raw" | cut -f2,3)
grep -v '^!UNNAMED' "$tmp/allrules.raw" | sort -u -o "$tmp/allrules"

rule_fail=0
while IFS=$'\t' read -r doc name; do
  [ -n "$name" ] || continue
  grep -qxF "$doc	$name" "$tmp/reached" && continue
  printf '::error file=%s::the rule "%s" in %s.md is reached by no glob — a rule no change is told to obey\n' "$map_rel" "$name" "$doc"
  rule_fail=$((rule_fail + 1))
done < "$tmp/allrules"

# The documents a route's own entries name: the `<Doc>.md` prefix of every section and rule it carries.
route_documents() { # $1 = one route object → one document name per line
  jq -r '[.sections[]?, .rules[]?] | .[]?' <<< "$1" | sed -nE 's/^([A-Za-z0-9_-]+)\.md .*/\1/p' | sort -u
}

# The map's globs, one per line.
route_globs() { jq -r '.routes[].glob' "$map_path"; }

# (3) every normative document is matched by at least one glob. A document no glob names is read by no
# change of its own — its rules are reached only through some other path's route, so the check above stays
# green while the document itself derives nothing.
doc_fail=0
while IFS= read -r doc; do
  [ -n "$doc" ] || continue
  named_glob=0
  while IFS= read -r glob; do
    [ -n "$glob" ] || continue
    glob_match "guidelines/$doc.md" "$glob" && { named_glob=1; break; }
  done < <(route_globs)
  if [ "$named_glob" -eq 0 ]; then
    printf '::error file=%s::no glob matches guidelines/%s.md — a normative document no glob names derives nothing for its own edits, so its rules are read only by whatever other path happens to route them\n' "$map_rel" "$doc"
    doc_fail=$((doc_fail + 1))
  fi
done < <(base_documents)

# (4) a route whose glob sits under `guidelines/` carries only documents its glob matches.
route_fail=0
while IFS= read -r route; do
  [ -n "$route" ] || continue
  glob=$(jq -r '.glob' <<< "$route")
  case "$glob" in guidelines/*) ;; *) continue ;; esac
  while IFS= read -r doc; do
    [ -n "$doc" ] || continue
    glob_match "guidelines/$doc.md" "$glob" && continue
    printf '::error file=%s::the glob %s carries a %s.md entry it does not match — a guideline route names the document whose sections and rules it carries\n' "$map_rel" "$glob" "$doc"
    route_fail=$((route_fail + 1))
  done < <(route_documents "$route")
done < <(jq -c '.routes[]' "$map_path")

if [ "$glob_fail" -gt 0 ] || [ "$rule_fail" -gt 0 ] || [ "$unnamed_rules" -gt 0 ] ||
   [ "$doc_fail" -gt 0 ] || [ "$route_fail" -gt 0 ]; then
  printf 'check-rule-routes: %d glob(s) match no tracked path, %d rule(s) are reached by no glob, %d rule(s) state no name\n' "$glob_fail" "$rule_fail" "$unnamed_rules"
  printf 'check-rule-routes: %d document(s) are matched by no glob, %d guideline route(s) carry a document their glob does not match\n' "$doc_fail" "$route_fail"
  exit 1
fi
printf 'check-rule-routes: every glob matches the tree and all %s named rule(s) are reached\n' \
  "$(grep -c . "$tmp/allrules" || true)"
exit 0
