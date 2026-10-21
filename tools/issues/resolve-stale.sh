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
# and a parked item is skipped — so a second run finds nothing to do and posts nothing. Verified is the operative
# word (#200): a rewrite is counted as a repair only when the produced body differs from the input and every
# rewritten citation re-classifies as no longer `absent` — an attempt that a later rewrite undoes, or whose
# target the tree does not hold, is unresolved and parks the item rather than being reported as repaired.
#
# The matrix is asked of the title and the body together — the pair the pre-screen asks — but only the body is
# written, so a token that stands in the title alone cannot be repaired by this script: a `suffix` one is
# settled (the class is not stale, and the body already reads as it should), an `absent` one is parked with a
# reason that names the title (#226). Counting such a token's body-identical rewrite as unresolved is how an
# item that had just been repaired was parked on the next run.
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
# The bound is on the items a run **would write**, not on the items it visits: an item nothing can be written for
# (an ambiguous citation, #199) spends no slot, so a non-action cannot exhaust the run's budget.
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
issues_preflight "$profile"

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

# Whether the item's *body* carries this token at all. The matrix is asked of the title and the body together
# — the pair the pre-screen asks — but this script writes the body only, so this is the question a rewrite can
# act on: a token it answers no for is settled or parked below, never rewritten (#226).
body_cites() { # <token>
  printf '%s\n' "$body_matrix" | awk -F'\t' -v t="$1" '$2 == t { found = 1 } END { exit !found }'
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
  # Either makes the item's processing reach a write; `exact`, `submodule`, `generated` and `not-a-path` do not.
  # Being reachable for a write is also what spends the run's bound — see the guard below the two token loops.
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

  new_body="$body"
  # The body's own citation set: both loops below write this body and nothing else, so a token that is not in
  # this set is not theirs to repair (#226).
  body_matrix=$(issues_citation_matrix_of "$body")
  repairs=""
  pairs=""
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
    # A token the body does not carry stands in the title alone: a body write cannot reach it, and a `suffix`
    # citation is not stale in the first place — it is settled, so the item is not due from it and nothing is
    # written (#226).
    body_cites "$tok" || continue
    # `standalone=1`: a basename repair may not rewrite the tail of a longer path — that is how the suffix loop
    # used to give a doubled token a third copy of its directory (#200).
    new_body=$(printf '%s\n' "$new_body" | awk -v old="$tok" -v new="$canon" -v standalone=1 -f "$here/rewrite.awk")
    pairs=$(add_line "$pairs" "$tok"$'\t'"$canon")
  done <<< "$suffix_toks"

  while IFS= read -r tok; do
    [ -n "$tok" ] || continue
    # A citation the tree does not hold, standing in the title alone: the item *is* stale there — the pre-screen
    # asks the title too — and this script writes bodies, so it cannot repair it. Parked with a reason that
    # names where the citation is, rather than a rewrite that was never attempted (#226).
    if ! body_cites "$tok"; then
      unresolved=$(add_line "$unresolved" "- \`${tok}\` — the citation is in the title, which this tool does not rewrite")
      continue
    fi
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
      pairs=$(add_line "$pairs" "$tok"$'\t'"$canon")
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

  # A repair is a change to the record, not the attempt at one (#200). The two loops rewrite independently, so
  # the suffix loop's basename repair can sit inside a doubled token the absent loop then collapses back to
  # exactly what it was — and the history can name a successor the tree no longer holds. A pair therefore counts
  # only when the body differs from the input and both halves re-classify: the target a path the tree holds, the
  # old token no longer an `absent` citation of the new body — asked of the matrix, so a target path that
  # legitimately contains the old name is not mistaken for a surviving citation. What fails becomes
  # `unresolved`, so the item is parked with its reason instead of counted repaired with a comment claiming it:
  # a refusal recorded nowhere is the state the resolver exists to remove (#163).
  repairs=""
  if [ -n "$pairs" ]; then
    matrix_after=$(issues_citation_matrix_of "$new_body")
    while IFS=$'\t' read -r old new; do
      [ -n "$old" ] || continue
      if [ "$new_body" = "$body" ]; then
        unresolved=$(add_line "$unresolved" "- \`${old}\` -> \`${new}\` — the rewrite changed nothing")
        continue
      fi
      if [ "$(issues_citation_class "$new")" = "absent" ]; then
        unresolved=$(add_line "$unresolved" "- \`${old}\` -> \`${new}\` — the target is not a tracked path")
        continue
      fi
      if printf '%s\n' "$matrix_after" | \
         awk -F'\t' -v t="$old" '$1 == "absent" && $2 == t { found = 1 } END { exit !found }'; then
        unresolved=$(add_line "$unresolved" "- \`${old}\` — the citation survives the rewrite, so nothing was repaired")
        continue
      fi
      repairs=$(add_line "$repairs" "- \`${old}\` -> \`${new}\` — $(basis_of "$old" "$new")")
    done <<< "$pairs"
  fi

  # The bound counts the items a run would write, not the items it visits (#199). An item whose only stale
  # citations are ambiguous reports them and reaches neither write branch below, so counting it as `due` spent
  # a slot of every run's bound on a non-action while the items behind it waited — measured 2026-10-19,
  # `due=25 repaired=4 parked=5 ambiguous=22` meant 16 of the 25 visited items produced no write (`ambiguous`
  # counts tokens). It is visited, written for nowhere, and counted `skipped`; the `ambiguous` counter still
  # reports it per token.
  if [ -n "$repairs" ] || [ -n "$unresolved" ]; then due=$((due + 1)); else skipped=$((skipped + 1)); fi

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
