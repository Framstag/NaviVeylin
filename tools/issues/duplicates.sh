#!/usr/bin/env bash
# duplicates.sh — the mechanical half of the tracker's hygiene, run by the scheduled worker.
#
# Two things in the tracker are pure functions of fields the tooling itself wrote, and neither needs a person:
#
#   * **a duplicate by fingerprint.** `create.sh` refuses a duplicate *at filing time* by hashing
#     `class | category | claim | anchor` into the body's `<!-- fingerprint: … -->` marker; nothing ever looked
#     at the markers that already existed, so a duplicate filed before the other was found — or one the import
#     brought along — stayed. The older item survives (its number is immutable, labels and body length are not)
#     and the younger one is closed with a pointer to it.
#   * **an `nvid` collision.** Two items claiming one `<!-- nvid: N -->` make `list.sh --id N` ambiguous, and
#     that is how a `§N` citation resolves. Which item legitimately carries it is recorded in the migration
#     table, not derivable from the tracker, so a collision is **reported and never repaired**.
#
# A third list is printed and never acted on: items still waiting for work whose number appears in a commit subject on `main`.
# A commit naming an id is not proof that the *item* is delivered — today's landing practice cherry-picks, so
# one message names the id on main and on the item's branch — which is why that half stays a person's, or the
# loop's own closure.
#
# The authorisation label is never widened to make the rule fire: `close.sh` refuses an item that does not
# carry it (`missing-eligibility-label`), so such a duplicate gets the pointer as a comment and is left as it is.
#
# Exit: 0 ok (nothing to do is not a failure), 1 a write failed, 2 usage, 3 policy.
#
# Usage: duplicates.sh [--apply|--dry-run] [--json] [--limit N] [--max-items N] [--profile <path>]

set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

apply=0
json=0
limit=400
max_items=25
profile_arg=""
while [ $# -gt 0 ]; do
  case "$1" in
    --apply) apply=1; shift ;;
    # The scheduled workflow passes its own mode word (`sensors.yml`: `--apply` or `--dry-run`), as every other
    # script it runs accepts it. A sweep that rejected the word would fail the step that calls it on every
    # dispatch — measured by the review, not by a test, because no case runs the workflow.
    --dry-run) apply=0; shift ;;
    --json) json=1; shift ;;
    # Two bounds, not one: `--limit` is what is *read* from the tracker (a fingerprint group can pair the newest
    # item with an old one), `--max-items` is what may be *written* in one run, as `resolve-stale.sh` splits it.
    --limit) need_value "$@"; limit="${2:-}"; shift 2 ;;
    --max-items) need_value "$@"; max_items="${2:-}"; shift 2 ;;
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,40p' "$0"; exit "$ISSUES_EXIT_OK" ;;
    --*) issues_die "duplicates: unknown argument $1" ;;
    *) issues_die "duplicates: unexpected argument $1" ;;
  esac
done
case "$limit" in ''|*[!0-9]*) issues_die "duplicates: --limit takes a number" ;; esac
case "$max_items" in ''|*[!0-9]*) issues_die "duplicates: --max-items takes a number" ;; esac
[ "$limit" -gt 0 ] || issues_die "duplicates: --limit must be at least 1"

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
# The authorisation label is the one the policy gate reads (`core/Policy.java`: `missing-eligibility-label`
# names `requiredLabels`), so the sweep and the gate cannot disagree about what authorises a closure.
eligibility_label=$(jq -r '(.requiredLabels // [])[0] // empty' "$profile")
[ -n "$eligibility_label" ] || issues_die "duplicates: the profile declares no requiredLabels entry — nothing says what authorises a closure"

# One read of the tracker. `state` is requested because the sweep decides on it, and a field the query did not
# ask for is a field the filter cannot see (the class of defect `decisions.sh` had until 2026-10-20). The state
# value itself is the profile's: an engine names no status value of a project (tools/check-portability.sh).
open_state=$(jq -r '.roles.openStatus // empty' "$profile")
[ -n "$open_state" ] || issues_die "duplicates: the profile declares no openStatus — the sweep cannot tell a waiting item"
# `--state all`, filtered here rather than by `gh`: the two vocabularies only look alike — `gh` takes its own
# `open|closed|all`, this project's status namespace has its own words — and a profile whose status value is not
# `open` would make the scheduled step die on an argument `gh` never chose (found in review round 2; the stub
# shifts `--state` blind, so no case could see it).
issues=$(gh issue list --state all --limit "$limit" --json number,title,state,labels,body,comments) ||
  issues_die "duplicates: gh could not list the tracker"

report=$(printf '%s' "$issues" | jq -c --arg eligibility "$eligibility_label" --arg open_state "$open_state" '
  def labelnames: [(.labels // [])[] | if type == "string" then . else .name end];
  # The markers are read through the same accessor `lib.sh` gives every other reader, so the sweep and the rest
  # of the tooling cannot drift on what a marker looks like.
  def field($k): (.body // "") | try capture("<!--[[:space:]]*" + $k + ":[[:space:]]*(?<v>[^>]*?)[[:space:]]*-->") .v catch null;
  def fingerprint: field("fingerprint");
  def nvid:        field("nvid");
  def marked:      [(.comments // [])[] | (.body // "") | strings | select(test("<!-- duplicates: #"))] | length;
  [ .[]
    | select(((.state // "OPEN") | ascii_downcase) == $open_state)
    | {issue: .number, title: .title, fp: fingerprint, nvid: nvid, marked: marked,
       authorised: (labelnames | index($eligibility) != null)} ] as $workable
  | {
      groups: ( $workable
        | map(select(.fp != null))
        | group_by(.fp)
        | map(select(length > 1) | sort_by(.issue))
        | map({fingerprint: .[0].fp,
               survivor: .[0].issue,
               duplicates: (.[1:] | map({issue, title, authorised, marked}))}) ),
      collisions: ( $workable
        | map(select(.nvid != null))
        | group_by(.nvid)
        | map(select(length > 1))
        | map({nvid: .[0].nvid, issues: (map(.issue) | sort)}) ),
      candidates: ($workable | map({issue, title}))
    }')

# The third list: a waiting item whose number a commit subject on main names. Read-only, always. The root is the
# one `lib.sh` resolved from the working tree (`ISSUES_REPO_ROOT` is the self-test's override of it).
#
# The ids are read out of the log **once**: a `git log | grep -q` per item is a pipeline whose reader exits
# early, and under `pipefail` the resulting EPIPE on `git` makes the test fail for a reason that has nothing to
# do with the item — measured while writing this script (every item came out "not landed", the log said
# otherwise). `grep -o` reads its whole input, so the pipeline stays whole.
repo_root="${ISSUES_REPO_ROOT:-$ISSUES_ROOT}"
landed_ids=$(git -C "$repo_root" log --format='%s' -400 main 2>/dev/null | grep -oE '#[0-9]+' | tr -d '#' | sort -un || true)
report=$(printf '%s' "$report" | jq -c --arg ids "$landed_ids" '
  ($ids | split("\n") | map(select(length > 0) | tonumber) | unique) as $named
  | . + {landed: [.candidates[] | select(.issue as $i | $named | index($i))]}
  | del(.candidates)')

writes=()
failures=0
# A pointer on the survivor, so the pair is readable from both ends — and so a reader of the older item learns
# that a duplicate of it was closed.
point_survivor() { # <survivor issue> <duplicate issue>
  local survivor="$1" dup="$2"
  # The marker is the pointer's own (`duplicates-absorbed`), not the dedupe marker: a survivor that later becomes
  # an unauthorised duplicate must still receive its pointer, and the dedupe reads only `duplicates:`.
  local body="A duplicate of this item was closed against it (#$dup, same fingerprint).\n\n<!-- duplicates-absorbed: #$dup -->"
  if bash "$here/update.sh" "$survivor" --comment "$(printf '%b' "$body")" >/dev/null 2>&1; then
    writes+=("pointed #$survivor at #$dup")
  else
    printf 'duplicates: could not comment on the survivor #%s\n' "$survivor" >&2
    failures=$((failures + 1))
  fi
}

close_or_comment() { # <duplicate issue> <survivor issue> <authorised true|false> <marked 0|1>
  local dup="$1" survivor="$2" authorised="$3" marked="$4"
  local reason="duplicate of #$survivor — one fingerprint, several items; the older item is the record"
  if [ "$authorised" = "true" ]; then
    if bash "$here/close.sh" "$dup" --reason "$reason" --evidence "#$survivor (the surviving item of the same fingerprint)" --not-planned >/dev/null 2>&1; then
      writes+=("closed #$dup as a duplicate of #$survivor")
      # The pointer is written after the closure, not before: a pointer that outlived a failed close would tell the
      # survivor's reader about an item that is still there.
      point_survivor "$survivor" "$dup"
      return 0
    else
      printf 'duplicates: could not close #%s as a duplicate of #%s\n' "$dup" "$survivor" >&2
      failures=$((failures + 1))
    fi
  elif [ "$marked" = "0" ]; then
    # A duplicate nobody may close is commented **once**: the group survives the run by construction, so an
    # unguarded comment would repeat on every scheduled tick (measured in review: 48 a day). The marker is the
    # dedupe — the same shape the stale-citation resolver uses for its own repeated attempt.
    if bash "$here/update.sh" "$dup" --comment "$(printf '%b' "$reason. This item does not carry $eligibility_label, so the closure is a person's. Nothing was changed.\n\n<!-- duplicates: #$survivor -->")" >/dev/null 2>&1; then
      writes+=("commented #$dup — a duplicate of #$survivor, closure left to a person")
      return 0
    else
      printf 'duplicates: could not comment on #%s\n' "$dup" >&2
      failures=$((failures + 1))
    fi
  fi
  # Nothing was written: an already-commented duplicate, or a write that failed. The caller counts writes, not
  # candidates, so this must not be mistaken for one.
  return 1
}

if [ "$apply" -eq 1 ]; then
  written=0
  while IFS=$'\t' read -r dup survivor authorised marked; do
    [ -n "$dup" ] || continue
    # The write bound, not the read bound: a run closes at most `--max-items` of what it found, so one bad
    # morning cannot produce fifty closures a person has to read back.
    [ "$written" -lt "$max_items" ] || { printf 'duplicates: --max-items %s reached — the rest waits for the next run\n' "$max_items"; break; }
    # The bound counts writes, not candidates: an item that was already commented writes nothing, and must not
    # consume a slot a real closure needed.
    close_or_comment "$dup" "$survivor" "$authorised" "$marked" && written=$((written + 1))
  done < <(printf '%s' "$report" | jq -r '.groups[] | .survivor as $s | .duplicates[] | "\(.issue)\t\($s)\t\(.authorised)\t\(if .marked > 0 then 1 else 0 end)"')
fi

if [ "$json" -eq 1 ]; then
  printf '%s' "$report" | jq -c --argjson applied "$apply" --argjson writes "$(printf '%s\n' "${writes[@]:-}" | jq -R -s 'split("\n") | map(select(length > 0))')" \
    '. + {applied: ($applied == 1), writes: $writes}'
  [ "$failures" -eq 0 ] || exit 1
  exit "$ISSUES_EXIT_OK"
fi

groups=$(printf '%s' "$report" | jq -r '.groups | length')
collisions=$(printf '%s' "$report" | jq -r '.collisions | length')
landed=$(printf '%s' "$report" | jq -r '.landed | length')
if [ "$groups" -eq 0 ] && [ "$collisions" -eq 0 ] && [ "$landed" -eq 0 ]; then
  printf 'nothing to do — one fingerprint per item, every identifier unique, no landed candidate\n'
  exit "$ISSUES_EXIT_OK"
fi

printf 'duplicates: %s group(s)\n' "$groups"
printf '%s' "$report" | jq -r --arg eligibility "$eligibility_label" '.groups[] | "  survivor #\(.survivor) ← \(.duplicates | map("#\(.issue)\(if .authorised then "" else " (not authorisable: no \($eligibility))" end)") | join(", "))"'
printf 'collisions: %s (reported, never repaired)\n' "$collisions"
printf '%s' "$report" | jq -r '.collisions[] | "  nvid \(.nvid) ← \(.issues | map("#\(.)") | join(", "))"'
printf 'items a commit subject on main names: %s (reported, never closed)\n' "$landed"
printf '%s' "$report" | jq -r '.landed[] | "  #\(.issue) \(.title)"'

if [ "$apply" -eq 0 ]; then
  printf '\ndry run — nothing was written; pass --apply to close what the rule says\n'
else
  printf '\n%s write(s):\n' "${#writes[@]}"
  [ "${#writes[@]}" -eq 0 ] || printf '%s\n' "${writes[@]}" | sed 's/^/  /'
fi
[ "$failures" -eq 0 ] || exit 1
exit "$ISSUES_EXIT_OK"
