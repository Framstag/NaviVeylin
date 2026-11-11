#!/usr/bin/env bash
# check-glossary.sh — the declared vocabulary, read against the tree.
#
# One file per feature under `tools/glossary/` plus the index declares the project's words: for each row a key
# `(<scope>/)<term>#<kind>`, one sentence, a `not` field, the surfaces per grammatical role, the unit where the
# term carries a physical quantity, the home kind and the anchor that proves it (`domain-vocabulary`, "A row is
# one term in one grammatical kind", "Every row carries an anchor"). The scopes are not declared here: they are
# the `feature` column of `architecture/structure.tsv`, read at run time, and a declaration that cannot be read
# fails the check closed rather than passing with no scopes (`domain-vocabulary`, "The vocabulary reads the
# structure declaration"; design D4).
#
# The refusals are a closed vocabulary, fixed here the way `check-change-readiness.sh` fixes its own, so a second
# spelling of a finding cannot appear (design D2):
#
#   phantom            a declared surface the census never observes
#   drift              a form other than the declared one, in the role the row declares
#   undecided          a token observed with two spellings and no canonical form declared
#   overruled          a declared canonical form contradicted by a ten-to-one majority of the code
#   kind-mismatch      a row declares no surface in the role its kind demands
#   unit-missing       a physical-quantity attribute names no unit
#   bool-prefix        a declared boolean surface carries no is/has/can prefix
#   unanchored         a row carries no anchor, or one that resolves to no declared case and no named rule
#   twin-term          two rows of one kind exclude nothing, so the two words are one word
#   lopsided-exclusion row A excludes row B and row B does not exclude row A
#   unowned-term       a row's home kind is not a coining kind
#   misplaced-term     a type named by a row is declared in a port, platform or shell package
#   bad-name           a declared type carries a suffix the lexicon refuses in its home kind, or a key is malformed
#   undeclared-context a row's scope, or the structure declaration itself, is not declared
#
# A hard refusal exits 1. `drift` is the exception the requirement names: a spelling split with a declared
# canonical form passes and the non-canonical spelling is *reported* as `drift` (`domain-vocabulary`, "The
# declaration settles a split spelling"); a form the row's `forbidden` list names is refused instead.
#
# Every finding is one line carrying the row's key, the file and line a surface was found at, the artefact the
# finding was decided against and exactly one refusal word (`domain-vocabulary`, "A refusal names the row and
# the artefact"). A green run prints the rows declared, the rows anchored, the kinds used and the files scanned.
#
# `--census` prints the census instead — one sorted line per identifier occurrence of `app/src/main`,
# `auto/src/main` and `core/src/main`, `token<TAB>spelling<TAB>role<TAB>file:line<TAB>flags`, with the tallies on
# stderr — and takes no judgement (`domain-vocabulary`, "Surfaces are declared per grammatical role"; design D1).
#
# `--json` prints each finding as one JSON object and the green summary as an object, for the sensor row that
# files under one identity per refusal (`vocabulary-projection`, "The drift measurement files under one identity
# per refusal").
#
# Reads text files only: no build, no device, no network. Exit 0 when the declaration holds, 1 when one refusal
# stands, 2 on a usage or layout error.
#
# Usage: check-glossary.sh [--root <dir>] [--json] [--census]
set -uo pipefail

prog=check-glossary
root=""
json=0
census=0
seed=0
anchor_query=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$prog" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    --json) json=1; shift ;;
    --census) census=1; shift ;;
    --seed) seed=1; shift ;;
    --anchors) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$prog" "$1" >&2; exit 2; }; anchor_query="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,47p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) printf '%s: unknown argument %s\n' "$prog" "$1" >&2; exit 2 ;;
  esac
done
[ -n "$root" ] || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

gdir="$root/tools/glossary"
index="$gdir/index.json"
structure="$root/architecture/structure.tsv"

[ -f "$index" ] || { printf '%s: no %s\n' "$prog" "$index" >&2; exit 2; }
jq -e . "$index" >/dev/null 2>&1 || { printf '%s: %s is not valid JSON\n' "$prog" "$index" >&2; exit 2; }
command -v jq >/dev/null 2>&1 || { printf '%s: jq is required\n' "$prog" >&2; exit 2; }

tmp=$(mktemp -d "${TMPDIR:-/tmp}/check-glossary.XXXXXX") || { printf '%s: cannot create a scratch directory\n' "$prog" >&2; exit 2; }
trap 'rm -rf "$tmp"' EXIT
findings="$tmp/findings.tsv"
: > "$findings"

emit() { # $1 hard(1/0), $2 refusal, $3 key, $4 file:line, $5 detail
  printf '%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" "$5" >> "$findings"
}

# 1. the index: the closed kind vocabulary, the vacuous lexicon, the projection switches and the census scope.
jq -r '.kinds[]' "$index" > "$tmp/kinds.txt" 2>/dev/null || { printf '%s: %s carries no kinds\n' "$prog" "$index" >&2; exit 2; }
jq -r '.roles[]' "$index" > "$tmp/roles.txt"
jq -r '.booleanPrefixes[]' "$index" > "$tmp/boolprefixes.txt"
jq -r '.roleOfKind | to_entries[] | "\(.key)\t\(.value)"' "$index" > "$tmp/roleofkind.tsv"
jq -r '.vacuous[] | "\(.suffix)\t\(.refusedKinds | join(","))"' "$index" > "$tmp/vacuous.tsv"
jq -r '.census.splitRatio' "$index" > "$tmp/splitratio.txt"
mapfile -t census_roots < <(jq -r '.census.roots[]' "$index")
mapfile -t census_exts < <(jq -r '.census.extensions[]' "$index")
split_ratio=$(cat "$tmp/splitratio.txt")
[ -n "$split_ratio" ] || split_ratio=10

# 2. the structure declaration. Absent, unparsable or carrying no feature column is a refusal, never an empty
#    scope set: a check that passes with no scopes is a check that measures nothing.
print_findings() {
  while IFS=$'\t' read -r hard refusal key where detail; do
    [ -n "$refusal" ] || continue
    if [ "$json" -eq 1 ]; then
      file=${where%:*}
      line=${where##*:}
      case "$line" in ''|*[!0-9]*) line=0 ;; esac
      jq -cn --arg refusal "$refusal" --arg key "$key" --arg file "$file" --argjson line "$line" --arg detail "$detail" --argjson hard "$([ "$hard" = 1 ] && echo true || echo false)" \
        '{refusal: $refusal, key: $key, file: $file, line: $line, detail: $detail, hard: $hard}'
    else
      printf '%s: %s %s %s — %s\n' "$prog" "$refusal" "$key" "$where" "$detail"
    fi
  done < "$findings"
}

if [ ! -f "$structure" ]; then
  emit 1 undeclared-context - "architecture/structure.tsv:0" "the structure declaration is absent, so no scope can be read"
  structure_missing=1
else
  structure_missing=0
  awk -F'\t' '
    /^#/ { next }
    NF == 0 { next }
    {
      if (NF != 5) { printf "%s: structure.tsv line %d carries %d columns, 5 are declared\n", FILENAME, FNR, NF > "/dev/stderr"; bad = 1; exit 1 }
      print $2 "\t" $3 "\t" $4
      if ($4 != "-" && $4 != "") seen = 1
    }
    END { if (bad) exit 1; if (!seen) { print "structure.tsv carries no feature column" > "/dev/stderr"; exit 1 } }
  ' "$structure" > "$tmp/structure.tsv" 2> "$tmp/structure.err" || {
    why=$(sed -n '1p' "$tmp/structure.err")
    emit 1 undeclared-context - "architecture/structure.tsv:1" "the structure declaration is unreadable: ${why:-no feature column}"
    structure_missing=1
  }
fi

if [ "$structure_missing" -eq 1 ]; then
  print_findings
  exit 1
fi

cut -f3 "$tmp/structure.tsv" | grep -v '^-$' | grep -v '^$' | sort -u > "$tmp/features.txt"

# 3. the census: one pass, no judgement.
: > "$tmp/files.txt"
for r in "${census_roots[@]}"; do
  [ -d "$root/$r" ] || continue
  for e in "${census_exts[@]}"; do
    find "$root/$r" -type f -name "*.$e" >> "$tmp/files.txt" 2>/dev/null
  done
done
files_scanned=$(wc -l < "$tmp/files.txt" | tr -d ' ')
: > "$tmp/census.tsv"
if [ "$files_scanned" -gt 0 ]; then
  # shellcheck disable=SC2046
  awk -f "$script_dir/glossary/census.awk" $(cat "$tmp/files.txt") > "$tmp/census.tsv" || {
    printf '%s: the census pass failed\n' "$prog" >&2
    exit 2
  }
fi
sort -k1,1 -k2,2 "$tmp/census.tsv" > "$tmp/census.sorted.tsv"

awk -F'\t' '{ n[$2]++; t[$1] = 1 }
  END { for (s in n) printf "%s\t%d\n", s, n[s] }' "$tmp/census.tsv" | sort > "$tmp/occ_by_spelling.tsv"
awk -F'\t' '{ n[$1 "\t" $3 "\t" $2]++ }
  END { for (k in n) printf "%s\t%d\n", k, n[k] }' "$tmp/census.tsv" | sort > "$tmp/occ_by_token.tsv"
awk -F'\t' '{ n[$2]++ } END { for (s in n) if (n[s] > 1) printf "%s\t%d\n", s, n[s] }' "$tmp/census.tsv" \
  | wc -l | tr -d ' ' > "$tmp/split_tokens.txt"

# the declarations, joined to the structure declaration: the kind of package a type was declared in is what
# `misplaced-term` and `bad-name` are decided against.
awk -F'\t' 'NR == FNR { kind[$1] = $2; next }
  $3 != "use" && $3 != "component" { print }' "$tmp/structure.tsv" "$tmp/census.tsv" > "$tmp/decls.tsv"

if [ "$census" -eq 1 ]; then
  cat "$tmp/census.sorted.tsv"
  {
    printf '%s: census files=%s occurrences=%s split-tokens=%s\n' "$prog" "$files_scanned" "$(wc -l < "$tmp/census.tsv" | tr -d ' ')" "$(cat "$tmp/split_tokens.txt")"
    awk -F'\t' '$6 == "whole"' "$tmp/census.tsv" > "$tmp/whole.tsv"
    awk -F'\t' '{ n[$2]++ } END { for (s in n) printf "%s %d\n", s, n[s] }' "$tmp/census.tsv" | sort -k2,2nr | head -40
    # the two figures the proposal measured: a head word and a unit suffix, counted over whole identifiers per root
    for pre in Basemap BaseMap baseMap; do
      awk -F'\t' -v p="$pre" 'index($2, p) == 1 { n[$4 ~ /\/app\// ? "app" : ($4 ~ /\/auto\// ? "auto" : "core")]++ }
        END { for (r in n) printf "prefix %s %s %d\n", p, r, n[r] }' "$tmp/whole.tsv"
    done
    for suf in KmH Kmh; do
      awk -F'\t' -v s="$suf" 'length($2) > length(s) && substr($2, length($2) - length(s) + 1) == s { n[$4 ~ /\/app\// ? "app" : ($4 ~ /\/auto\// ? "auto" : "core")]++ }
        END { for (r in n) printf "suffix %s %s %d\n", s, r, n[r] }' "$tmp/whole.tsv"
    done
    printf 'bare-booleans %s\n' "$(awk -F'\t' '$5 == "bareboolean"' "$tmp/census.tsv" | wc -l | tr -d ' ')"
    printf 'types-with-a-vacuous-suffix %s\n' "$(awk -F'\t' 'NR == FNR { v[$1] = 1; next }
      $3 == "type" { for (s in v) if (length($2) > length(s) && substr($2, length($2) - length(s) + 1) == s) { print $2; break } }' \
      "$tmp/vacuous.tsv" "$tmp/census.tsv" | wc -l | tr -d ' ')"
  } >&2
  exit 0
fi

# 4. the rows. A row is read from a per-feature file; the file names its scope, and the index holds what every
#    row is read against.
: > "$tmp/rows.jsonl"
for f in "$gdir"/*.json; do
  [ -f "$f" ] || continue
  [ "$(basename "$f")" = "index.json" ] && continue
  jq -e '.rows | type == "array"' "$f" >/dev/null 2>&1 || {
    emit 1 bad-name "-" "${f#"$root"/}:0" "the file carries no rows array, so no row can be read"
    continue
  }
  scope=$(basename "$f" .json)
  jq -c --arg scope "$scope" --arg file "${f#"$root"/}" \
    '.rows[] | . + {__scope: $scope, __file: $file}' "$f" >> "$tmp/rows.jsonl"
done

row_count=$(grep -c . "$tmp/rows.jsonl" || true)
anchored_count=0
kinds_used=""

# every anchor a shipped spec declares: a case's text, or the first backticked span of its line
if [ -d "$root/openspec/specs" ]; then
  grep -rh '\*\*Case\*\*:' "$root/openspec/specs" --include=spec.md 2>/dev/null | sed 's/^.*\*\*Case\*\*: *//' > "$tmp/case_lines.txt" || true
else
  : > "$tmp/case_lines.txt"
fi
: > "$tmp/case_names.txt"
while IFS= read -r line; do
  [ -n "$line" ] || continue
  printf '%s\n' "$line" | tr -d '`"' | sed 's/  */ /g; s/ *$//' >> "$tmp/case_names.txt"
  span=$(printf '%s\n' "$line" | sed -n 's/^`\([^`]*\)`.*/\1/p')
  [ -n "$span" ] && printf '%s\n' "$span" >> "$tmp/case_names.txt"
  span2=$(printf '%s\n' "$line" | sed -n 's/^\([A-Za-z][A-Za-z0-9_.:-]*\).*/\1/p')
  [ -n "$span2" ] && printf '%s\n' "$span2" >> "$tmp/case_names.txt"
done < "$tmp/case_lines.txt"
sort -u "$tmp/case_names.txt" > "$tmp/case_names.sorted.txt"
if [ -d "$root/guidelines" ]; then
  grep -rh '^\- \*\*Rule: ' "$root/guidelines" --include='*.md' 2>/dev/null \
    | sed -n 's/^- \*\*Rule: \(.*\)\*\*.*$/\1/p' | sort -u > "$tmp/rule_names.txt" || true
else
  : > "$tmp/rule_names.txt"
fi
jq -r '.census.stoplist[]' "$index" > "$tmp/stoplist.txt"

# 4a. one term's candidate anchors: the case and rule texts whose own words carry the term, so the seed step
#     picks an anchor a reader can check rather than the first substring that happens to match.
if [ -n "$anchor_query" ]; then
  grep -iF -- "$anchor_query" "$tmp/case_names.sorted.txt" | sed -n '1,5p' | sed 's/^/case: /'
  grep -iF -- "$anchor_query" "$tmp/rule_names.txt" | sed -n '1,5p' | sed 's/^/rule: /'
  exit 0
fi

# 4b. the seed: the intersection of a code surface with an anchor (`domain-vocabulary`, "Every row carries an
#     anchor"). A token the code leans on with no anchor is named as such rather than given a definition nobody
#     can check.
if [ "$seed" -eq 1 ]; then
  awk -F'\t' '
    $6 == "part" {
      count[$1]++
      spell[$1 "\t" $2]++
      if ($3 != "component") role[$1] = $3
    }
    END {
      for (k in count) {
        best = ""; bestn = 0
        for (s in spell) {
          split(s, p, "\t")
          if (p[1] == k && spell[s] > bestn) { bestn = spell[s]; best = p[2] }
        }
        printf "%s\t%d\t%s\t%s\n", k, count[k], best, role[k]
      }
    }
  ' "$tmp/census.tsv" | sort -k2,2nr > "$tmp/seed_candidates.tsv"
  surface=0
  anchored=0
  : > "$tmp/seed.tsv"
  : > "$tmp/seed_anchorless.txt"
  while IFS=$'\t' read -r tok count spelling role; do
    [ -n "$tok" ] || continue
    [ "$count" -ge 3 ] || continue
    grep -Fxq "$tok" "$tmp/stoplist.txt" && continue
    [ "${#tok}" -ge 3 ] || continue
    surface=$((surface + 1))
    anchor=""
    if [ -s "$tmp/case_names.sorted.txt" ]; then
      anchor=$(grep -iF -- "$tok" "$tmp/case_names.sorted.txt" | sed -n '1p')
      [ -n "$anchor" ] && anchor="case: $anchor"
    fi
    if [ -z "$anchor" ] && [ -s "$tmp/rule_names.txt" ]; then
      anchor=$(grep -iF -- "$tok" "$tmp/rule_names.txt" | sed -n '1p')
      [ -n "$anchor" ] && anchor="rule: $anchor"
    fi
    if [ -z "$anchor" ]; then
      printf '%s\t%s\n' "$tok" "$count" >> "$tmp/seed_anchorless.txt"
      continue
    fi
    anchored=$((anchored + 1))
    case "$role" in
      type) kind=entity ;;
      property) kind=attribute ;;
      operation) kind=operation ;;
      constant) kind=constant ;;
      *) kind=entity ;;
    esac
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$tok" "$count" "$spelling" "$kind" "$role" "$anchor" >> "$tmp/seed.tsv"
  done < "$tmp/seed_candidates.tsv"
  cat "$tmp/seed.tsv"
  {
    printf '%s: seed surface=%s anchored=%s anchorless=%s\n' "$prog" "$surface" "$anchored" "$(wc -l < "$tmp/seed_anchorless.txt" | tr -d ' ')"
    printf 'anchorless %s %s\n' $(sed -n '1p' "$tmp/seed_anchorless.txt")
  } >&2
  exit 0
fi

seen_keys="$tmp/seen_keys.txt"
: > "$seen_keys"
row_meta="$tmp/row_meta.tsv"
: > "$row_meta"

while IFS= read -r row; do
  [ -n "$row" ] || continue
  scope=$(printf '%s' "$row" | jq -r '.__scope')
  rowfile=$(printf '%s' "$row" | jq -r '.__file')
  key=$(printf '%s' "$row" | jq -r '.key // ""')
  where="$rowfile:0"

  unknown=$(printf '%s' "$row" | jq -r 'keys - ["key","definition","not","home","unit","canonical","anchor","surfaces","forbidden","__scope","__file"] | join(",")')
  if [ -n "$unknown" ]; then
    emit 1 bad-name "${key:--}" "$where" "the row carries field(s) $unknown the closed schema does not declare"
    continue
  fi

  if ! printf '%s' "$key" | grep -Eq '^[^#]+#[^#]+$'; then
    emit 1 bad-name "${key:--}" "$where" "the key is not (<scope>/)<term>#<kind>"
    continue
  fi
  termkey=${key%%#*}
  kind=${key#*#}
  case "$termkey" in */*) declared_scope=${termkey%%/*}; term=${termkey#*/} ;; *) declared_scope=""; term=$termkey ;; esac

  if ! grep -Fxq "$kind" "$tmp/kinds.txt"; then
    emit 1 bad-name "$key" "$where" "kind '$kind' is not one of the declared kinds ($(paste -sd, "$tmp/kinds.txt"))"
    continue
  fi
  if grep -Fxq "$key" "$seen_keys"; then
    emit 1 bad-name "$key" "$where" "a second row carries the same key; the first row and this one both do"
    continue
  fi
  printf '%s\n' "$key" >> "$seen_keys"

  if [ "$scope" != "concept" ]; then
    if ! grep -Fxq "$scope" "$tmp/features.txt"; then
      emit 1 undeclared-context "$key" "$where" "scope '$scope' is not a feature architecture/structure.tsv declares"
    fi
  fi
  if [ -n "$declared_scope" ] && [ "$declared_scope" != "$scope" ]; then
    emit 1 undeclared-context "$key" "$where" "the key's scope '$declared_scope' is not the file's scope '$scope'"
  fi

  home=$(printf '%s' "$row" | jq -r '.home // ""')
  # the file names the scope, and the scope names the home kind: a row of a feature file is homed in that
  # feature, a row of concept.json is global (`domain-vocabulary`, "The concept kind is the only global
  # scope"). An explicit `home` overrides it and is what `unowned-term` is refused against.
  if [ -z "$home" ]; then
    if [ "$scope" = "concept" ]; then home=concept; else home="feature/$scope"; fi
  fi
  if [ "$scope" = "concept" ] && [ "$home" != "concept" ]; then
    emit 1 unowned-term "$key" "$where" "the row is declared global (a concept file) and homed '$home'; a global term's home kind is concept"
  fi
  case "$home" in
    concept) ;;
    feature/*)
      if ! grep -Fxq "${home#feature/}" "$tmp/features.txt"; then
        emit 1 unowned-term "$key" "$where" "home '$home' names a feature the structure declaration does not carry"
      fi
      ;;
    *) emit 1 unowned-term "$key" "$where" "home '$home' is neither concept nor feature/<name>, so no kind coins the term" ;;
  esac

  # anchors
  anchors=$(printf '%s' "$row" | jq -r '(.anchor // []) | .[]')
  if [ -z "$anchors" ]; then
    emit 1 unanchored "$key" "$where" "the row carries neither a case nor a rule anchor; looked in openspec/specs/**/spec.md and guidelines/*.md"
  else
    ok_anchors=0
    all_ok=1
    while IFS= read -r a; do
      [ -n "$a" ] || continue
      case "$a" in
        case:*)
          a_text=${a#case:}
          a_text=$(printf '%s' "$a_text" | sed 's/^ *//; s/ *$//')
          if grep -Fxq "$a_text" "$tmp/case_names.sorted.txt"; then
            ok_anchors=$((ok_anchors + 1))
          else
            all_ok=0
            emit 1 unanchored "$key" "$where" "anchor '$a_text' names no case a shipped spec declares; looked in openspec/specs/**/spec.md"
          fi
          ;;
        rule:*)
          a_text=${a#rule:}
          a_text=$(printf '%s' "$a_text" | sed 's/^ *//; s/ *$//')
          if grep -Fxq "$a_text" "$tmp/rule_names.txt"; then
            ok_anchors=$((ok_anchors + 1))
          else
            all_ok=0
            emit 1 unanchored "$key" "$where" "anchor '$a_text' names no rule a guideline declares; looked in guidelines/*.md"
          fi
          ;;
        *)
          all_ok=0
          emit 1 unanchored "$key" "$where" "anchor '$a' is neither case:<name> nor rule:<name>"
          ;;
      esac
    done <<< "$anchors"
    [ "$all_ok" -eq 1 ] && [ "$ok_anchors" -gt 0 ] && anchored_count=$((anchored_count + 1))
  fi

  notfield=$(printf '%s' "$row" | jq -r '.not // ""')
  surfaces=$(printf '%s' "$row" | jq -r '(.surfaces // {}) | to_entries[] | "\(.key)=\(.value)"')
  forbidden=$(printf '%s' "$row" | jq -r '(.forbidden // {}) | to_entries[] | .key as $r | .value[] | "\($r)=\(.)"')
  canonical=$(printf '%s' "$row" | jq -r '.canonical // ""')
  unit=$(printf '%s' "$row" | jq -r '.unit // ""')

  printf '%s\t%s\t%s\t%s\t%s\n' "$key" "$term" "$kind" "$scope" "$notfield" >> "$row_meta"

  # the role the kind demands
  expected_role=$(awk -F'\t' -v k="$kind" '$1 == k { print $2 }' "$tmp/roleofkind.tsv")
  surfaces_text=$(printf '%s' "$surfaces" | paste -sd' ' -)
  if [ -n "$expected_role" ]; then
    if ! printf '%s\n' "$surfaces" | grep -q "^$expected_role="; then
      emit 1 kind-mismatch "$key" "$where" "kind '$kind' demands a '$expected_role' surface; the row declares ${surfaces_text:-none}"
    fi
  fi

  if [ "$kind" = "attribute" ] && [ -z "$unit" ]; then
    emit 1 unit-missing "$key" "$where" "the attribute declares no unit, so its quantity has no scale"
  fi

  if [ -z "$notfield" ]; then
    emit 1 twin-term "$key" "$where" "the row's not field is empty, so it excludes no neighbour"
  fi

  while IFS= read -r sv; do
    [ -n "$sv" ] || continue
    role=${sv%%=*}
    name=${sv#*=}
    count=$(awk -F'\t' -v s="$name" '$1 == s { print $2; exit }' "$tmp/occ_by_spelling.tsv")
    [ -n "$count" ] || count=0
    if [ "$count" -eq 0 ]; then
      emit 1 phantom "$key" "$where" "the declared $role surface '$name' is observed nowhere in the census"
      continue
    fi
    if [ "$role" = "property" ] && [ "$kind" = "boolean" ]; then
      case "$name" in
        is[A-Z]*|has[A-Z]*|can[A-Z]*) ;;
        *) emit 1 bool-prefix "$key" "$where" "the boolean surface '$name' carries no prefix; accepted: $(paste -sd, "$tmp/boolprefixes.txt")" ;;
      esac
    fi
    if [ "$role" = "type" ]; then
      while IFS=$'\t' read -r suffix refused; do
        [ -n "$suffix" ] || continue
        case "$home" in
          concept|feature/*) ;;
          *) continue ;;
        esac
        case ",$refused," in
          *,concept,*|*,feature,*) ;;
          *) continue ;;
        esac
        if [ "${name%"$suffix"}" != "$name" ] && [ "$name" != "$suffix" ]; then
          reason=$(jq -r --arg s "$suffix" '.vacuous[] | select(.suffix == $s) | .reason' "$index")
          emit 1 bad-name "$key" "$where" "the declared type '$name' carries the suffix $suffix in kind '$home': $reason"
        fi
      done < "$tmp/vacuous.tsv"
      # a declared term declared in a package that does not coin it: the declaring type's own name is the
      # term, not a word it happens to be written with
      while IFS=$'\t' read -r tok sp role2 where2 flags scope6; do
        [ "$role2" = "type" ] || continue
        [ "$scope6" = "whole" ] || continue
        [ "$sp" = "$name" ] || continue
        pkg=${where2%:*}
        rel=${pkg#"$root"/}
        pkgname=$(printf '%s\n' "$rel" | sed -n 's#.*/src/main/\(java\|kotlin\)/\(.*\)/[^/]*$#\2#p')
        pkgname=$(printf '%s' "$pkgname" | tr '/' '.')
        pkgkind=$(awk -F'\t' -v p="$pkgname" 'index(p, $1) == 1 { if (length($1) > best) { best = length($1); k = $2 } } END { print k }' "$tmp/structure.tsv")
        case "$pkgkind" in
          port|platform|shell)
            emit 1 misplaced-term "$key" "${where2}" "the declared term is declared in a '$pkgkind' package ($pkgname); only concept and feature/<name> coin a term"
            ;;
        esac
      done < "$tmp/decls.tsv"
    fi
    # forbidden forms in the role they are forbidden in
    while IFS= read -r fv; do
      [ -n "$fv" ] || continue
      [ "${fv%%=*}" = "$role" ] || continue
      form=${fv#*=}
      dline=$(awk -F'\t' -v s="$form" '$2 == s && $3 != "use" { print $4; exit }' "$tmp/census.tsv")
      if [ -n "$dline" ]; then
        emit 1 drift "$key" "$dline" "declared=$name observed=$form in the $role role, which the row forbids"
      fi
    done <<< "$forbidden"
    # a split spelling: undecided, overruled, or a declaration that agrees and reports drift
    if [ "$role" = "$expected_role" ]; then
      tok=$(printf '%s' "$name" | tr '[:upper:]' '[:lower:]')
      spellings=$(awk -F'\t' -v t="$tok" -v r="$role" '$1 == t && $2 == r' "$tmp/occ_by_token.tsv")
      nspellings=$(printf '%s\n' "$spellings" | grep -c . || true)
      if [ "$nspellings" -ge 2 ]; then
        top=$(printf '%s\n' "$spellings" | sort -k4,4nr | sed -n '1p')
        topform=$(printf '%s' "$top" | cut -f3)
        topcount=$(printf '%s' "$top" | cut -f4)
        if [ -z "$canonical" ]; then
          second=$(printf '%s\n' "$spellings" | sort -k4,4nr | sed -n '2p')
          emit 1 undecided "$key" "$where" "the token is observed as $topform $topcount and $(printf '%s' "$second" | cut -f3) $(printf '%s' "$second" | cut -f4) in the $role role, and the row declares no canonical form"
        else
          ccount=$(awk -F'\t' -v s="$canonical" '$1 == s { print $2; exit }' "$tmp/occ_by_spelling.tsv")
          [ -n "$ccount" ] || ccount=0
          if [ "$ccount" -gt 0 ] && [ "$topform" != "$canonical" ] && [ "$topcount" -ge $((ccount * split_ratio)) ]; then
            emit 1 overruled "$key" "$where" "declared=$canonical($ccount) observed=$topform($topcount), a majority the declaration contradicts"
          fi
          while IFS=$'\t' read -r _tok _role sform scount; do
            [ -n "$sform" ] || continue
            [ "$sform" = "$canonical" ] && continue
            listed=0
            printf '%s\n' "$forbidden" | grep -Fxq "$role=$sform" && listed=1
            [ "$listed" -eq 1 ] && continue
            dline=$(awk -F'\t' -v s="$sform" '$2 == s && $3 != "use" { print $4; exit }' "$tmp/census.tsv")
            [ -n "$dline" ] || dline="$where"
            emit 0 drift "$key" "$dline" "declared=$canonical($ccount) observed=$sform($scount) in the $role role; reported, not refused"
          done <<< "$spellings"
        fi
      fi
    fi
  done <<< "$surfaces"
done < "$tmp/rows.jsonl"

# 5. the exclusions, read across rows: an exclusion is mutual, and two rows of one kind that exclude nothing
#    are one word written twice.
awk -F'\t' '
  { key[NR] = $1; kind[NR] = $3; not[NR] = $5; n = NR }
  END {
    for (i = 1; i <= n; i++) {
      if (not[i] != "") {
        for (j = 1; j <= n; j++) {
          if (i == j) continue
          if (index(not[i], key[j]) > 0 && index(not[j], key[i]) == 0)
            printf "1\tlopsided-exclusion\t%s\trow:%d\t%s names %s and %s does not name %s\n", key[i], i, key[i], key[j], key[j], key[i]
        }
      }
      for (j = i + 1; j <= n; j++)
        if (kind[i] == kind[j] && not[i] == not[j])
          printf "1\ttwin-term\t%s\trow:%d\t%s and %s are both kind %s and exclude the same nothing\n", key[i], i, key[i], key[j], kind[i]
    }
  }
' "$row_meta" | while IFS=$'\t' read -r hard refusal key where detail; do
  emit "$hard" "$refusal" "$key" "$where" "$detail"
done

kinds_used=$(cut -f3 "$row_meta" | grep -v '^$' | sort -u | paste -sd, -)

print_findings
hard_count=$(awk -F'\t' '$1 == 1' "$findings" | wc -l | tr -d ' ')
refusals=$(awk -F'\t' '{ print $2 }' "$findings" | sort -u | paste -sd, -)

if [ "$hard_count" -gt 0 ]; then
  if [ "$json" -eq 1 ]; then
    jq -cn --argjson hard "$hard_count" --arg refusals "$refusals" '{summary: "red", hard: $hard, refusals: $refusals}'
  else
    printf '%s: red %s refusal(s) — %s\n' "$prog" "$hard_count" "${refusals:-none}"
  fi
  exit 1
fi

if [ "$json" -eq 1 ]; then
  jq -cn --argjson rows "$row_count" --argjson anchored "$anchored_count" --arg kinds "$kinds_used" --argjson files "$files_scanned" --arg refusals "$refusals" \
    '{summary: "green", rows: $rows, anchored: $anchored, kinds: $kinds, files: $files, reported: $refusals}'
else
  printf '%s: green rows=%s anchored=%s kinds=%s files=%s\n' "$prog" "$row_count" "$anchored_count" "${kinds_used:-none}" "$files_scanned"
fi
exit 0
