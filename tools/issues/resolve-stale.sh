#!/usr/bin/env bash
# resolve-stale.sh — repair a stale citation in an issue body, or park the item when it cannot be repaired.
#
# An issue whose prose cites a file that no longer exists is refused by the loop's pre-screen (`stale`), and
# before this script that refusal was recorded **nowhere** in the tracker: the item kept `agent/eligible` and
# was excluded on every run for a reason that lived only in a machine-local run state (#163). Measured
# 2026-10-18, the crude extraction behind it was wrong about 48 of 49 verdicts (#164).
#
# This is the repair half. The classification is not re-implemented here — it is `tools/issues/lib.sh`'s
# (`stale-citation-resolution` D1), the same functions the pre-screen asks, so the two cannot disagree about a
# body. The ladder (D4) is deliberately only as strong as the repository's own statements:
#
#   1  exact       nothing to do
#   2  suffix      exactly one tracked path ends with the citation → rewrite it to that path
#   3  submodule   it resolves inside a submodule → rewrite it to the submodule-relative path
#   4  rename      a rename/move in the history names the successor → rewrite it to the successor
#   5  symbol      exactly one surviving file declares the cited symbol → rewrite it to that file
#   6  otherwise   park the item (`status/on-hold`) and record what was tried
#
# An ambiguous match is **reported, not repaired**: guessing a target would put a wrong path into a record that
# exists to be evidence. And nothing here is a judgement: the script may rewrite a citation token, park an item
# and post one comment per item per run — it may not authorise (`agent/*`), classify, prioritise, close or file
# anything, and every write goes through `tools/issues/update.sh`, so the reserved-label refusal and the closed
# vocabulary apply to it exactly as to a person (`Backlog.md` §5).
#
# Idempotence needs no bookkeeping: a repair is verified by re-classifying — a canonical citation is `exact`,
# and a parked item is skipped — so a second run finds nothing to do and posts nothing.
#
# Exit: 0 ran (including "nothing to do"), 2 usage or unusable input, 3 policy refusal.
#
# Usage: resolve-stale.sh [--apply] [--max-items <n>] [--issues <file>] [--json] [--profile <path>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

apply=0
json=0
max_items=20
issues_file=""
profile_arg=""
while [ $# -gt 0 ]; do
  case "$1" in
    --apply) apply=1; shift ;;
    --dry-run) apply=0; shift ;;
    --max-items) need_value "$@"; max_items="${2:-}"; shift 2 ;;
    --issues) need_value "$@"; issues_file="${2:-}"; shift 2 ;;
    --json) json=1; shift ;;
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,33p' "$0"; exit 0 ;;
    *) issues_die "resolve-stale: unknown argument $1" ;;
  esac
done
case "$max_items" in ''|*[!0-9]*) issues_die "resolve-stale: --max-items must be a non-negative integer" ;; esac
[ "$max_items" -gt 0 ] || issues_die "resolve-stale: --max-items must be positive"

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
separator=$(issues_profile_value namespaceSeparator)
separator="${separator:-/}"

started_ms=$(issues_now_ms)
scratch=$(mktemp -d) || issues_die "resolve-stale: cannot create a scratch directory"
trap 'rm -rf "$scratch"' EXIT

# One index for the whole run: the suffix test needs the tracked file list, and a `git ls-files` per citation
# would fork per token. The rename map is built once for the same reason — it is only consulted for a citation
# the other classes did not resolve.
export ISSUES_CITATION_ROOT="$ISSUES_ROOT"
export ISSUES_CITATION_INDEX="$scratch/tracked"
issues_citation_index_build "$ISSUES_ROOT" "$ISSUES_CITATION_INDEX" ||
  issues_die "resolve-stale: could not read the tracked file list at $ISSUES_ROOT"
( cd "$ISSUES_ROOT" && git log --all --diff-filter=DR --name-status --format= ) 2>/dev/null |
  awk 'NF == 3 { print $2 "\t" $3 }' > "$scratch/renames" || true

if [ -n "$issues_file" ]; then
  [ -f "$issues_file" ] || issues_die "resolve-stale: no such issue file: $issues_file"
  issues=$(cat "$issues_file")
else
  gh_err="$scratch/gh.err"
  issues=$(gh issue list --limit 400 --json number,title,body,labels,author 2>"$gh_err") ||
    issues_die "resolve-stale: gh could not read the tracker: $(cat "$gh_err" 2>/dev/null)"
fi
printf '%s' "$issues" | jq -e 'type == "array"' >/dev/null 2>&1 ||
  issues_die "resolve-stale: the tracker answer is not an issue array"

# The gate: this script writes through the tooling, so it runs as an allowlisted identity or not at all.
login=$(issues_login)
issues_preflight "$profile" "$login"

read_count=$(printf '%s' "$issues" | jq 'length')
due=0; repaired=0; parked=0; ambiguous=0; skipped=0; waiting=0
writes=0
report="$scratch/report.jsonl"
: > "$report"

# The status an unresolved item is parked under is a value of the project's vocabulary, so it is read
# from the profile's `roles` section — `update.sh` composes the label from the namespace and this value.
hold_status=$(jq -r '.roles.holdStatus // empty' "$profile")
[ -n "$hold_status" ] || issues_die "resolve-stale: the profile's roles section declares no holdStatus"

# Parked already: the parking state is the marker, so a second run does not re-comment or re-park.
is_parked() { # <labels-json>
  printf '%s' "$1" | jq -e --arg l "status${separator}${hold_status}" 'any(.[]; .name == $l)' >/dev/null 2>&1
}

# A list built line by line: `add_line <current> <line>`, without a stray leading newline.
add_line() {
  if [ -n "$1" ]; then printf '%s\n%s' "$1" "$2"; else printf '%s' "$2"; fi
}

# The successor of a citation the tree does not hold, from the repository's own records: an archived change
# (its directory moved under `archive/` with a date prefix), then the rename map, then a single surviving
# declaration of the symbol.
successor_of() { # <token> → the path, or nothing
  local tok="$1" sym hit n=0 first="" line archived
  archived=$(archived_successor "$tok")
  if [ -n "$archived" ]; then printf '%s\n' "$archived"; return 0; fi
  while IFS=$'\t' read -r from to; do
    if [ "$from" = "$tok" ] || [ "${from##*/}" = "$tok" ]; then printf '%s\n' "$to"; return 0; fi
  done < "$scratch/renames"
  sym="${tok##*/}"; sym="${sym%.*}"
  [ -n "$sym" ] || return 0
  while IFS= read -r hit; do n=$((n + 1)); [ "$n" -eq 1 ] && first="$hit"; done \
    < <( cd "$ISSUES_ROOT" && git grep -l --fixed-strings -- "$sym" -- . 2>/dev/null | sort )
  [ "$n" -eq 1 ] && printf '%s\n' "$first"
  return 0
}

# A change that was archived after the item was written: the directory moved, and the file inside it kept its
# name. The filesystem states it, so it needs no history walk and no guess.
archived_successor() { # <token> → the archive path, or nothing
  local tok="$1" rest name path hit
  case "$tok" in openspec/changes/*/*) ;; *) return 0 ;; esac
  rest="${tok#openspec/changes/}"
  name="${rest%%/*}"
  path="${rest#*/}"
  [ -n "$name" ] && [ -n "$path" ] || return 0
  for hit in "$ISSUES_ROOT/openspec/changes/archive/"*"-$name"; do
    [ -d "$hit" ] || continue
    if [ -e "$hit/$path" ]; then printf 'openspec/changes/archive/%s/%s\n' "${hit##*/}" "$path"; return 0; fi
  done
  return 0
}

# The basis a repair records, so the comment says *why* the new path is the right one.
basis_of() { # <token> <canonical>
  local tok="$1" canon="$2" line collapsed
  case "$canon" in openspec/changes/archive/*) printf 'the change was archived: the directory keeps its name under a date prefix'; return 0 ;; esac
  collapsed=$(issues_citation_collapsed "$tok")
  if [ -n "$collapsed" ] && [ "$collapsed" = "$canon" ]; then
    printf 'the citation repeats its own directory: the collapsed path is the tracked one'
    return 0
  fi
  while IFS=$'\t' read -r from to; do
    [ "$to" = "$canon" ] && { printf 'a rename in the history: %s -> %s' "$from" "$to"; return 0; }
  done < "$scratch/renames"
  if [ "$canon" != "$tok" ]; then
    case "$canon" in
      *"/$tok") printf 'exactly one tracked path ends with the citation'; return 0 ;;
      *) printf 'a single surviving declaration of the symbol' ;;
    esac
  fi
}

issue_at=0
while [ "$issue_at" -lt "$read_count" ] && [ "$due" -lt "$max_items" ]; do
  number=$(printf '%s' "$issues" | jq -r ".[$issue_at].number")
  title=$(printf '%s' "$issues" | jq -r ".[$issue_at].title // \"\"")
  body=$(printf '%s' "$issues" | jq -r ".[$issue_at].body // \"\"")
  labels=$(printf '%s' "$issues" | jq -c "[.[$issue_at].labels[]? | if type == \"string\" then {name: .} else . end]")
  issue_at=$((issue_at + 1))

  # A citation the tree does not hold *at all* is the verdict; a citation that resolves by suffix is a
  # courtesy repair (D3) — it writes the full tracked path so the record is exact and the next run is cheap.
  # Both make the item due; `exact`, `submodule`, `generated` and `not-a-path` do not.
  matrix=$(issues_citation_matrix_of "$title
$body")
  suffix_toks=$(printf '%s\n' "$matrix" | awk -F'\t' '$1 == "suffix" { print $2 }')
  absent_toks=$(printf '%s\n' "$matrix" | awk -F'\t' '$1 == "absent" { print $2 }')
  if [ -z "$suffix_toks" ] && [ -z "$absent_toks" ]; then
    skipped=$((skipped + 1))
    continue
  fi
  if is_parked "$labels"; then
    skipped=$((skipped + 1))
    continue
  fi
  due=$((due + 1))

  new_body="$body"
  repairs=""
  unresolved=""
  item_ambiguous=0
  while IFS= read -r tok; do
    [ -n "$tok" ] || continue
    canon=$(issues_citation_resolve "$tok")
    if [ -z "$canon" ]; then
      # Several tracked paths end with it and no suffix names the file: reported, never guessed (D4).
      item_ambiguous=$((item_ambiguous + 1))
      ambiguous=$((ambiguous + 1))
      continue
    fi
    [ "$canon" != "$tok" ] || continue
    new_body=$(printf '%s\n' "$new_body" | awk -v old="$tok" -v new="$canon" -f "$here/rewrite.awk")
    repairs=$(add_line "$repairs" "- \`${tok}\` -> \`${canon}\` — $(basis_of "$tok" "$canon")")
  done <<< "$suffix_toks"

  while IFS= read -r tok; do
    [ -n "$tok" ] || continue
    canon=$(successor_of "$tok")
    if [ -z "$canon" ]; then
      # Before a rename walk or a symbol search: the token said its own directory twice, and the collapsed form
      # is a tracked path. This is a fact about the token, not an inference from the tree (#189).
      collapsed=$(issues_citation_collapsed "$tok")
      if [ -n "$collapsed" ] && [ "$(issues_citation_class "$collapsed")" != "absent" ]; then
        canon=$(issues_citation_resolve "$collapsed")
      fi
    fi
    if [ -n "$canon" ]; then
      new_body=$(printf '%s\n' "$new_body" | awk -v old="$tok" -v new="$canon" -f "$here/rewrite.awk")
      repairs=$(add_line "$repairs" "- \`${tok}\` -> \`${canon}\` — $(basis_of "$tok" "$canon")")
      continue
    fi
    # Ambiguity: several tracked paths end with the citation, and the repository states no successor.
    suffix_hits=$(awk -v t="$tok" -v s="/$tok" '$0 == t || substr($0, length($0) - length(s) + 1) == s { n++ } END { print n + 0 }' "$ISSUES_CITATION_INDEX")
    if [ "$suffix_hits" -gt 1 ]; then
      ambiguous=$((ambiguous + 1))
      unresolved=$(add_line "$unresolved" "- \`${tok}\` — ${suffix_hits} tracked paths end with it, so no target is stated")
      continue
    fi
    unresolved=$(add_line "$unresolved" "- \`${tok}\` — no tracked file, no rename record, no single surviving declaration")
  done <<< "$absent_toks"

  if [ -n "$repairs" ] && [ -z "$unresolved" ]; then
    if [ "$apply" -eq 1 ]; then
      printf '%s' "$new_body" > "$scratch/body-$number"
      bash "$here/update.sh" "$number" --body "$scratch/body-$number" \
        --comment "citation repaired for the repository's own record:

$repairs" >/dev/null || issues_die "resolve-stale: could not rewrite the body of #$number"
      writes=$((writes + 2))
    fi
    repaired=$((repaired + 1))
  elif [ -n "$unresolved" ]; then
    if [ "$apply" -eq 1 ]; then
      bash "$here/update.sh" "$number" --status "$hold_status" \
        --comment "unresolved citation — parked so the pre-screen stops re-deciding this item:

$unresolved${repairs:+

Repaired in the same run:
$repairs}" >/dev/null || issues_die "resolve-stale: could not park #$number"
      writes=$((writes + 2))
    fi
    parked=$((parked + 1))
  fi

  printf '{"issue": %s, "repaired": %s, "parked": %s, "ambiguous": %s, "suffix": %s, "absent": %s, "repairs": %s, "unresolved": %s}\n' \
    "$number" "$([ -n "$repairs" ] && printf true || printf false)" \
    "$([ -n "$unresolved" ] && printf true || printf false)" "$item_ambiguous" \
    "$(printf '%s' "$suffix_toks" | grep -c . || true)" "$(printf '%s' "$absent_toks" | grep -c . || true)" \
    "$(printf '%s' "$repairs" | jq -Rs .)" "$(printf '%s' "$unresolved" | jq -Rs .)" >> "$report"
done

waiting=$((read_count - issue_at))
wall_ms=$(( $(issues_now_ms) - started_ms ))

if [ "$json" -eq 1 ]; then
  jq -n --argjson read "$read_count" --argjson due "$due" --argjson repaired "$repaired" \
        --argjson parked "$parked" --argjson ambiguous "$ambiguous" --argjson skipped "$skipped" \
        --argjson waiting "$waiting" --argjson writes "$writes" --argjson wall "$wall_ms" \
        --argjson applied "$apply" --slurpfile items "$report" \
    '{read: $read, due: $due, repaired: $repaired, parked: $parked, ambiguous: $ambiguous,
      skipped: $skipped, waiting: $waiting, writes: $writes, applied: ($applied == 1),
      wall_ms: $wall, items: [$items[]]}'
else
  printf 'resolve-stale: read=%s due=%s repaired=%s parked=%s ambiguous=%s skipped=%s waiting=%s writes=%s dry_run=%s wall_ms=%s\n' \
    "$read_count" "$due" "$repaired" "$parked" "$ambiguous" "$skipped" "$waiting" "$writes" \
    "$([ "$apply" -eq 1 ] && printf no || printf yes)" "$wall_ms"
  jq -r 'select(.repaired or .parked or (.ambiguous > 0)) | "  #\(.issue) \(if .parked then "PARKED" else "repaired" end) suffix=\(.suffix) absent=\(.absent) ambiguous=\(.ambiguous) \((.repairs // "") | gsub("\n"; " · ") | gsub("- "; ""))\(if (.unresolved // "") != "" then " · UNRESOLVED: \((.unresolved // "") | gsub("\n"; " · ") | gsub("- "; ""))" else "" end)"' "$report" 2>/dev/null | head -40
  [ "$apply" -eq 1 ] || printf 'resolve-stale: a dry run touched nothing — pass --apply to write\n'
fi
exit "$ISSUES_EXIT_OK"
