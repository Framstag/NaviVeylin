#!/usr/bin/env bash
# Case-name traceability — every `**Case**:` line in a shipped spec names a case exactly one declared harness
# runs, and a case no scenario names is counted rather than refused (capability `change-readiness`, change
# `a-case-name-resolves-to-one-harness`).
#
# Two directions, over declared lists, never over sentences:
#   forward  — a `**Case**:` line's name-shaped token resolves against the union of the harnesses' declared
#              names. A name no harness lists is refused, naming the spec file, the line, the scenario and the
#              name; a full name two harnesses list is refused, naming both.
#   backward — a case a declared harness runs that no shipped line names is counted and listed, never refused:
#              a case may prove the harness rather than a requirement.
#
# The declared set is `tools/case-harnesses.json`, one row per suite: the file or command that declares its
# cases, the grammar those names have (`shape`), and the case groups it claims (`groups`). A group two rows
# claim is a DECLARED fact — a shared group is printed once and refuses nothing; only an identical full name in
# two rows is refused. A row whose list is absent, a row that emits a name in a group it did not claim, and a
# row whose `exclude` hides a name no other row declares, are refused rather than trusted.
#
# THE GRAMMAR — what a case line may carry. Every other token on the line is prose, and prose is never read for
# meaning; a line that carries no name-shaped token is counted as `unnamed` and refuses nothing, because a
# command or a source path is not a name this check can resolve:
#   hash        `group#case`                    a shell suite's id, e.g. `run#theStreakIsTheRecordedCount`
#   class       `Class`, `Class#method`, `Class.method`
#   module      `app: Class[.method]`, `app(device): Class`, `auto: Class#method`, …   the Gradle and native
#               test trees, qualified by the module whose tests hold the case
#   class-prose `Class.a display sentence`      the class is the case; the tail is a display name, not a name
# A reference resolves VERBATIM against the declared set — no case is normalised — so a suite that renames a
# form is red on the commit that renames it.
#
# `**Case**: none — <reason>` is the shipped style for a scenario no case can exercise. This check counts those
# lines and judges nothing about them: whether the reason is present is the readiness checker's rule over a
# change's delta; this one owns the shipped set.
#
# Usage: check-case-names.sh [--root <dir>] [--json]
#   --root <dir>  read the registry and the specs under <dir> (default: this repository's top level)
#   --json        print the lists and the counts as JSON instead of the one verdict line; exit status unchanged
# Exit: 0 every name resolves and no full name is duplicated; 1 a refusal (the verdict line, details on stderr);
#       2 usage, or a registry that cannot be read at all.
#
# The verdict is one line, whatever the counts, because a reader scans for `ok` or `refused` first:
#   CASE-NAMES: ok harnesses=26 lines=1524 named=1200 none=123 unnamed=201 unclaimed=61
#   CASE-NAMES: refused unresolved=1 duplicates=0 rows=0 undeclared=0 hinges=0
#
# NOT in the loop's gate, on purpose: a spec-wide invariant must not redden one item's gate, and the loop's
# selection names no spec (design D4). It runs in CI's artifact-hygiene step beside `openspec doctor`, and is
# reachable by name through `tools/tooling.json`'s `checks` map so no tool and no document hard-codes its path.
set -uo pipefail

me=check-case-names
root=""
json=0
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: --root needs a value\n' "$me" >&2; exit 2; }; root=${2:-}; shift 2 ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,50p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) printf '%s: unknown argument %s\n' "$me" "$1" >&2; exit 2 ;;
  esac
done
if [ -z "$root" ]; then
  root=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel 2>/dev/null) \
    || { printf '%s: not inside a git work tree — pass --root <dir>\n' "$me" >&2; exit 2; }
fi
root=$(cd "$root" && pwd) || { printf '%s: cannot read %s\n' "$me" "$root" >&2; exit 2; }
command -v jq >/dev/null 2>&1 || { printf '%s: jq is required\n' "$me" >&2; exit 2; }
reg="$root/tools/case-harnesses.json"

# ── the grammar, as ERE strings, so one definition serves the shell rows and the spec scan ───────────────────
# A class is CamelCase with a second hump, so a capitalised prose word (`The`, `A`) is never mistaken for one.
MODULES='app|auto|core|buildSrc|architecture|osmscout-client-java|libosmscout'
RE_HASH='[A-Za-z][A-Za-z0-9_-]*#[A-Za-z0-9_]+'
RE_CLASS='[A-Z][a-z0-9_]*[A-Z][A-Za-z0-9_]*(#[A-Za-z0-9_]+|\.[A-Za-z0-9_]+)?'
RE_MODNAME="($MODULES)(\([a-z]+\))?: [A-Z][A-Za-z0-9_]*(#[A-Za-z0-9_]+|\.[A-Za-z0-9_]+)?"
# the same two, written without a backslash escape, because awk's ERE has no `\.` and warns on it
RE_CLASS_AWK='[A-Z][a-z0-9_]*[A-Z][A-Za-z0-9_]*(#[A-Za-z0-9_]+|[.][A-Za-z0-9_]+)?'
RE_MODNAME_AWK="($MODULES)([(][a-z]+[)])?: [A-Z][A-Za-z0-9_]*(#[A-Za-z0-9_]+|[.][A-Za-z0-9_]+)?"

work=$(mktemp -d "${TMPDIR:-/tmp}/case-names.XXXXXX") || { printf '%s: cannot create a work directory\n' "$me" >&2; exit 2; }
trap 'rm -rf "$work"' EXIT
rows="$work/rows"; : > "$rows"                     # name \t caseid \t harness
excluded="$work/excluded"; : > "$excluded"         # name \t harness   (a row printed it and delegated it)
problems="$work/problems"; : > "$problems"         # kind \t detail
problem() { printf '%s\t%s\n' "$1" "$2" >> "$problems"; }

if [ ! -f "$reg" ]; then
  problem rows "tools/case-harnesses.json — the registry is not in the tree"
  harness_count=0
elif ! jq -e '.harnesses | type == "array" and length > 0' "$reg" >/dev/null 2>&1; then
  problem rows "tools/case-harnesses.json — no non-empty harnesses array"
  harness_count=0
else
  harness_count=$(jq '.harnesses | length' "$reg")
fi

# harvest_<kind> <row index> <row id> <outfile> — writes `name \t caseid`, one case per line, deduplicated.
harvest_file() { # <i> <id> <out>
  local i=$1 id=$2 out=$3 path pattern prefix
  path=$(jq -r --argjson i "$i" '.harnesses[$i].path // ""' "$reg")
  pattern=$(jq -r --argjson i "$i" '.harnesses[$i].pattern // ""' "$reg")
  prefix=$(jq -r --argjson i "$i" '.harnesses[$i].prefix // ""' "$reg")
  if [ -z "$path" ] || [ ! -f "$root/$path" ]; then problem rows "$id — $path is not in the tree"; return; fi
  if [ -z "$pattern" ]; then problem rows "$id — a file row with no pattern"; return; fi
  grep -oE "$pattern" "$root/$path" 2>/dev/null | sed "s|^|$prefix|" | sort -u | sed 's|$|\t|' > "$out"
}
harvest_command() { # <i> <id> <out>
  local i=$1 id=$2 out=$3
  local -a argv
  mapfile -t argv < <(jq -r --argjson i "$i" '.harnesses[$i].command // [] | .[]' "$reg")
  if [ "${#argv[@]}" -eq 0 ]; then problem rows "$id — a command row with no command"; return; fi
  if ! ( cd "$root" && "${argv[@]}" ) > "$work/out.$id" 2>/dev/null; then
    problem rows "$id — ${argv[*]} exits non-zero"
    return
  fi
  grep -oE "$RE_HASH" "$work/out.$id" | sort -u | sed 's|$|\t|' > "$out"
}
harvest_classes() { # <i> <id> <out> <groups out>
  local i=$1 id=$2 out=$3 groups=$4 pairs nroots j rel module qual f cls m bare
  # `//` would read a declared false as absent, so the default is spelled out: a row that says `"bare": false`
  # declares no unqualified name at all, and one that stays silent declares them.
  bare=$(jq -r --argjson i "$i" 'if .harnesses[$i].bare == false then "false" else "true" end' "$reg")
  pairs="$work/pairs.$id"; : > "$pairs"
  nroots=$(jq -r --argjson i "$i" '.harnesses[$i].roots | length' "$reg")
  j=0
  while [ "$j" -lt "$nroots" ]; do
    rel=$(jq -r --argjson i "$i" --argjson j "$j" '.harnesses[$i].roots[$j].path // ""' "$reg")
    module=$(jq -r --argjson i "$i" --argjson j "$j" '.harnesses[$i].roots[$j].module // ""' "$reg")
    qual=$(jq -r --argjson i "$i" --argjson j "$j" '.harnesses[$i].roots[$j].qualifier // ""' "$reg")
    if [ -d "$root/$rel" ]; then
      printf '%s\n%s\n' "$module" "$qual" >> "$groups"
      while IFS= read -r f; do
        cls=$(basename -- "$f"); cls=${cls%.*}
        printf '%s\t%s\t%s\t\n' "$module" "$qual" "$cls" >> "$pairs"
        grep -oE '(fun|void)[[:space:]]+(`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)' "$f" 2>/dev/null \
          | sed -E 's/^(fun|void)[[:space:]]+//; s/^`//; s/`$//' \
          | awk 'length($0) > 0 && $0 !~ / /' | sort -u > "$work/m.$id"
        while IFS= read -r m; do
          printf '%s\t%s\t%s\t%s\n' "$module" "$qual" "$cls" "$m" >> "$pairs"
        done < "$work/m.$id"
      done < <(find "$root/$rel" -type f \( -name '*.kt' -o -name '*.java' -o -name '*.cpp' \) 2>/dev/null | sort)
    else
      problem rows "$id — $rel is not in the tree"
    fi
    j=$((j + 1))
  done
  # how many of the row's own modules hold this class name: a bare form is declared only when the answer is one
  awk -F'\t' '{ k=$3; if (!(k in seen)) { seen[k]=1; n[k]=0 } if (index(mods[k], "|" $1 "|") == 0) { mods[k]=mods[k] "|" $1 "|"; n[k]++ } } END { for (k in n) print k "\t" n[k] }' "$pairs" > "$work/clsmods.$id"
  awk -F'\t' -v OFS='\t' -v bareflag="$bare" '
    FNR == NR { nmods[$1]=$2; next }
    { mod=$1; q=$2; cls=$3; m=$4; qual=(q == "" ? "" : "(" q ")")
      cid = mod qual ": " cls (m == "" ? "" : "." m)
      print mod qual ": " cls (m == "" ? "" : "." m), cid
      if (m != "") print mod qual ": " cls "#" m, cid
      if (bareflag == "true" && nmods[cls] + 0 == 1) {
        if (m == "") print cls, cid
        else { print cls "." m, cid; print cls "#" m, cid }
      }
    }' "$work/clsmods.$id" "$pairs" | sort -u > "$out"
}

i=0
while [ "$i" -lt "$harness_count" ]; do
  id=$(jq -r --argjson i "$i" '.harnesses[$i].id // ""' "$reg")
  kind=$(jq -r --argjson i "$i" '.harnesses[$i].kind // ""' "$reg")
  if [ -z "$id" ]; then problem rows "harnesses[$i] carries no id"; i=$((i + 1)); continue; fi
  mine="$work/names.$id"; : > "$mine"
  groups="$work/groups.$id"; : > "$groups"
  jq -r --argjson i "$i" '.harnesses[$i].groups // [] | .[]' "$reg" 2>/dev/null >> "$groups"
  case "$kind" in
    file)    harvest_file "$i" "$id" "$mine" ;;
    command) harvest_command "$i" "$id" "$mine" ;;
    classes) harvest_classes "$i" "$id" "$mine" "$groups" ;;
    *) problem rows "$id — unknown kind '$kind'"; i=$((i + 1)); continue ;;
  esac
  # a declared exclusion is a delegation: the row prints the names but another row's bodies run them
  exclude=$(jq -r --argjson i "$i" '.harnesses[$i].exclude // ""' "$reg")
  if [ -n "$exclude" ] && [ -s "$mine" ]; then
    grep -E "$exclude" "$mine" | cut -f1 | sed "s|$|\t$id|" >> "$excluded" || true
    grep -vE "$exclude" "$mine" > "$work/kept.$id" || true
    mv "$work/kept.$id" "$mine"
  fi
  # group integrity: every hash name a row emits is in a group that row claimed. A `classes` row's groups are
  # its modules and its bare forms carry none, so the rule is stated for the hash-shaped rows only.
  if [ "$kind" != classes ]; then
    while IFS=$'\t' read -r nm _; do
      [ -n "$nm" ] || continue
      case "$nm" in *:*) continue ;; *'#'*) ;; *) continue ;; esac
      case "${nm%%#*}" in
        ''|*[!A-Za-z0-9_-]*) continue ;;
      esac
      grep -qxF "${nm%%#*}" "$groups" || problem undeclared "$id emits \`$nm\` in a group it did not claim"
    done < "$mine"
  fi
  while IFS=$'\t' read -r nm cid; do
    [ -n "$nm" ] || continue
    [ -n "${cid:-}" ] || cid=$nm
    printf '%s\t%s\t%s\n' "$nm" "$cid" "$id" >> "$rows"
  done < "$mine"
  i=$((i + 1))
done
sort -u -o "$rows" "$rows"
sort -u -o "$excluded" "$excluded"

# ── declared-set facts a reader of the registry cannot see by reading one row ─────────────────────────────────
# a full name two rows declare is the duplicate the rule refuses; a name one row excludes must be declared by
# another row, or the exclusion is a hiding place rather than a delegation.
awk -F'\t' '{ n[$1]=n[$1] " " $3 } END { for (k in n) { c=split(n[k], a, " "); if (c > 1) print k "\t" n[k] } }' "$rows" \
  | sort > "$work/dup"
if [ -s "$work/dup" ]; then
  while IFS=$'\t' read -r nm who; do problem duplicates "duplicates \`$nm\` in$(printf '%s' "$who" | sed 's/ \([a-z]\)/ \1/g; s/^/ /')"; done < "$work/dup"
fi
if [ -s "$excluded" ]; then
  while IFS=$'\t' read -r nm who; do
    grep -qF "$nm	" "$rows" || problem hinges "$who excludes \`$nm\`, which no other row declares"
  done < "$excluded"
fi

# ── the spec side: the shipped set, its names, and the lines that carry none ──────────────────────────────────
specdir="$root/openspec/specs"
: > "$work/scan"
if [ -d "$specdir" ]; then
  find "$specdir" -type f -name spec.md 2>/dev/null | sort | while IFS= read -r f; do
    awk -v file="${f#"$root"/}" '
      function trim(s) { sub(/^[ \t]+/, "", s); sub(/[ \t]+$/, "", s); return s }
      /^#### Scenario:/ { s=$0; sub(/^#### Scenario:[ \t]*/, "", s); sl=NR; next }
      /^[ \t]*- \*\*Case\*\*: / {
        p=$0; sub(/^[ \t]*- \*\*Case\*\*: /, "", p); p=trim(p)
        print "L\t" file "\t" NR "\t" sl "\t" s
        if (p ~ /^`?none`?([ ]|$)/) { print "N\t" file "\t" NR "\t" sl "\t" s; next }
        n=split(p, a, "`"); ntok=0
        for (k=2; k <= n; k += 2) { t=trim(a[k]); if (t != "") tok[++ntok]=t }
        if (ntok == 0) tok[++ntok]=p
        seen=0
        for (k=1; k <= ntok; k++) {
          t=tok[k]; nm=""
          if (t ~ /^'"$RE_HASH"'$/) nm=t
          else if (t ~ /^'"$RE_MODNAME_AWK"'$/) nm=t
          else if (t ~ /^'"$RE_CLASS_AWK"'$/) nm=t
          else { head=t; sub(/[.].*/, "", head); if (head != t && head ~ /^[A-Z][A-Za-z0-9_]*$/) nm="prose:" t }
          if (nm != "") { print "R\t" file "\t" NR "\t" sl "\t" s "\t" nm; seen=1 }
        }
        if (!seen) print "U\t" file "\t" NR "\t" sl "\t" s
      }
    ' "$f" >> "$work/scan"
  done
fi

# ── resolve, and count what no line claims ────────────────────────────────────────────────────────────────────
# the class-prose form resolves to the class head, so `Class.a display sentence` names `Class`. A head that is
# not declared is simply not a name: the line keeps its `unnamed` count and no refusal is invented from prose.
awk -F'\t' '
  FNR == NR { decl[$1]=1; caseof[$1]=$2; cases[$2]=1; next }
  $1 == "R" {
    nm=$6
    if (nm ~ /^prose:/) { nm=substr(nm, 7); split(nm, a, "[.]"); nm=a[1] }
    if (!(nm in decl)) { print "unresolved\t" $2 "\t" $3 "\t" $4 "\t" $5 "\t" nm; next }
    usedname[nm]=1
  }
  END {
    for (n in usedname) claimed[caseof[n]]=1
    for (c in cases) if (!(c in claimed)) print "unclaimed\t" c
  }
' "$rows" "$work/scan" > "$work/verdicts"

unresolved=$(awk -F'\t' '$1 == "unresolved"' "$work/verdicts" | wc -l | tr -d ' ')
unclaimed=$(awk -F'\t' '$1 == "unclaimed"' "$work/verdicts" | wc -l | tr -d ' ')
duplicates=$(wc -l < "$work/dup" | tr -d ' ')
rows_bad=$(awk -F'\t' '$1 == "rows"' "$work/problems" | wc -l | tr -d ' ')
undeclared=$(awk -F'\t' '$1 == "undeclared"' "$work/problems" | wc -l | tr -d ' ')
hinges=$(awk -F'\t' '$1 == "hinges"' "$work/problems" | wc -l | tr -d ' ')
lines=$(awk -F'\t' '$1 == "L"' "$work/scan" | wc -l | tr -d ' ')
named=$(awk -F'\t' '$1 == "R"' "$work/scan" | awk -F'\t' '{print $2"\t"$3}' | sort -u | wc -l | tr -d ' ')
nones=$(awk -F'\t' '$1 == "N"' "$work/scan" | wc -l | tr -d ' ')
unnamed=$((lines - named - nones))
refused=0
[ "$unresolved" -gt 0 ] && refused=1
[ "$duplicates" -gt 0 ] && refused=1
[ "$rows_bad" -gt 0 ] && refused=1
[ "$undeclared" -gt 0 ] && refused=1
[ "$hinges" -gt 0 ] && refused=1

if [ -s "$work/dup" ]; then
  awk -F'\t' '{ printf "duplicates: %s is declared by%s\n", $1, $2 }' "$work/dup" >&2
fi
awk -F'\t' '$1 == "rows" { printf "row: %s\n", $2 }' "$work/problems" >&2
awk -F'\t' '$1 == "undeclared" { printf "group: %s\n", $2 }' "$work/problems" >&2
awk -F'\t' '$1 == "hinges" { printf "exclusion: %s\n", $2 }' "$work/problems" >&2
awk -F'\t' '$1 == "unresolved" { printf "unresolved: %s:%s  %s  (%s)  — no harness lists this name\n", $2, $3, $6, $5 }' "$work/verdicts" >&2

if [ "$json" -eq 1 ]; then
  jq -n \
    --arg verdict "$([ "$refused" -eq 1 ] && echo refused || echo ok)" \
    --argjson harnesses "$harness_count" --argjson lines "$lines" --argjson named "$named" \
    --argjson none "$nones" --argjson unnamed "$unnamed" --argjson unclaimed "$unclaimed" \
    --argjson unresolved "$unresolved" --argjson duplicates "$duplicates" \
    --argjson rowsbad "$rows_bad" --argjson undeclared "$undeclared" --argjson hinges "$hinges" \
    --rawfile scanx "$work/verdicts" --rawfile dupx "$work/dup" '
    def tsv: split("\n")[] | select(length > 0) | split("\t");
    { verdict: $verdict,
      counts: { harnesses: $harnesses, lines: $lines, named: $named, none: $none, unnamed: $unnamed,
                unclaimed: $unclaimed, unresolved: $unresolved, duplicates: $duplicates,
                rows: $rowsbad, undeclared: $undeclared, hinges: $hinges },
      unresolved: [$scanx | tsv | select(.[0] == "unresolved")
                   | { file: .[1], line: (.[2] | tonumber), scenario: .[4], name: .[5] }],
      unclaimed: [$scanx | tsv | select(.[0] == "unclaimed") | .[1]],
      duplicates: [$dupx | tsv | { name: .[0], harnesses: (.[1] | split(" ") | map(select(length > 0))) }] }'
else
  if [ "$refused" -eq 1 ]; then
    printf 'CASE-NAMES: refused unresolved=%s duplicates=%s rows=%s undeclared=%s hinges=%s\n' \
      "$unresolved" "$duplicates" "$rows_bad" "$undeclared" "$hinges"
  else
    printf 'CASE-NAMES: ok harnesses=%s lines=%s named=%s none=%s unnamed=%s unclaimed=%s\n' \
      "$harness_count" "$lines" "$named" "$nones" "$unnamed" "$unclaimed"
  fi
fi
[ "$refused" -eq 1 ] && exit 1
exit 0