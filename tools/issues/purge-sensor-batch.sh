#!/usr/bin/env bash
# purge-sensor-batch.sh — delete (or close) the issues one sensor row filed in one run.
#
# A sensor row whose bound does not fit the tree files one issue per condition, and it files them for every
# condition at once: the 2026-10-26 hotspot run wrote 337 items in 36 minutes (#321–#657), because
# `size × changes > 5000` over a 200-commit window holds for hundreds of paths. This is the one-off that
# takes such a batch back out of the tracker; it is not part of the loop and nothing in CI calls it.
#
# Selection is by *identity*, never by number range: the row's label, the exact title the row writes, the
# body marker the row stamps (`<!-- sensor: <namespace> fingerprint=… -->`) and a creation cutoff. A number
# range would take whatever else happened to be filed in the same minutes.
#
# Deleting a GitHub issue is permanent — there is no restore through the API, and support does not
# undelete on request. So: dry run unless `--apply` is given, a snapshot of every selected issue (number,
# title, body, labels, state) written before anything is removed, and `--close` as the reversible
# alternative (closes as `NOT_PLANNED` with a reason, keeps the bodies).
#
# Usage: purge-sensor-batch.sh [--label sensor/hotspot] [--title 'the path is past the hotspot bound']
#                              [--row hotspot] [--since <ISO-8601>] [--max <n>]
#                              [--snapshot <path>] [--close] [--apply] [--yes] [--profile <path>]
#
# Exit: 0 ok (including a batch that is already gone), 2 usage, 3 refused (batch larger than `--max`),
#       4 nothing selected, 5 a delete failed.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

label="sensor/hotspot"
title="the path is past the hotspot bound"
row=""
since=""
max=500
snapshot=""
mode="dry-run"
assume_yes=0
profile_arg=""
while [ $# -gt 0 ]; do
  case "$1" in
    --label) need_value "$@"; label="${2:-}"; shift 2 ;;
    --title) need_value "$@"; title="${2:-}"; shift 2 ;;
    --row) need_value "$@"; row="${2:-}"; shift 2 ;;
    --since) need_value "$@"; since="${2:-}"; shift 2 ;;
    --max) need_value "$@"; max="${2:-}"; shift 2 ;;
    --snapshot) need_value "$@"; snapshot="${2:-}"; shift 2 ;;
    --close) mode="close"; shift ;;
    --apply) mode="delete"; shift ;;
    --yes) assume_yes=1; shift ;;
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    *) issues_die "purge: unknown argument $1" ;;
  esac
done

case "$max" in ''|*[!0-9]*) issues_die "purge: --max wants a count" ;; esac
issues_resolve "$profile_arg"
repo="$ISSUES_REPO"
# The row id is the label's suffix (`sensor/hotspot` → `hotspot`) and is what the body marker carries.
[ -n "$row" ] || row="${label#*/}"
[ -n "$snapshot" ] || snapshot="${TMPDIR:-/tmp}/purge-${row}-$(date -u +%Y%m%dT%H%M%SZ).json"

# One paged read. The filter is identity: the row's label, the row's exact title, the row's body marker and
# the cutoff. `--json` carries the node id the GraphQL delete needs, so no second read per issue.
raw=$(gh issue list --repo "$repo" --label "$label" --state all --limit 1000 \
        --json id,number,title,body,labels,state,stateReason,createdAt) ||
  issues_die "purge: cannot read the tracker"

selected=$(printf '%s' "$raw" | jq -c \
  --arg title "$title" --arg marker "<!-- sensor: $row " --arg since "$since" '
    [ .[] | select(.title == $title)
          | select(.body != null and (.body | contains($marker)))
          | select($since == "" or .createdAt >= $since) ]
    | sort_by(.number)')

count=$(printf '%s' "$selected" | jq 'length')
if [ "$count" -eq 0 ]; then
  printf 'purge: nothing selected (label %s, title "%s", row %s, since %s)\n' \
    "$label" "$title" "$row" "${since:-any}" >&2
  exit 4
fi

printf 'purge: %s issue(s) selected — %s to #%s\n' "$count" \
  "$(printf '%s' "$selected" | jq -r 'first.number')" \
  "$(printf '%s' "$selected" | jq -r 'last.number')"
printf '%s' "$selected" | jq -r '.[] | "  #\(.number) \(.createdAt) \(.state)"' | head -10
[ "$count" -le 10 ] || printf '  … %s more\n' "$((count - 10))"

if [ "$count" -gt "$max" ]; then
  issues_die "purge: $count is more than --max $max; nothing was written (raise --max deliberately)"
fi

mkdir -p "$(dirname "$snapshot")" && printf '%s' "$selected" | jq '.' > "$snapshot" ||
  issues_die "purge: cannot write the snapshot to $snapshot"
printf 'purge: snapshot written to %s\n' "$snapshot"

if [ "$mode" = "dry-run" ]; then
  printf 'purge: dry run — nothing was written. Re-run with --apply to delete, or --close to close them.\n'
  exit 0
fi

if [ "$assume_yes" -eq 0 ]; then
  if [ "$mode" = "delete" ]; then
    printf 'purge: deleting %s issue(s) permanently. Type "delete %s" to continue: ' "$count" "$count"
  else
    printf 'purge: closing %s issue(s). Type "close %s" to continue: ' "$count" "$count"
  fi
  read -r answer || answer=""
  if [ "$answer" != "$mode $count" ]; then
    printf 'purge: not confirmed, nothing was written\n' >&2
    exit 2
  fi
fi

failed=0
done_n=0
while IFS=$'\t' read -r node number; do
  [ -n "$node" ] || continue
  if [ "$mode" = "delete" ]; then
    if gh api graphql -f query='mutation($id: ID!) { deleteIssue(input: {issueId: $id}) { clientMutationId } }' \
         -f id="$node" >/dev/null 2>&1; then
      done_n=$((done_n + 1))
    else
      failed=$((failed + 1))
      printf 'purge: #%s could not be deleted\n' "$number" >&2
    fi
  else
    if gh issue close "$number" --repo "$repo" --reason "not planned" \
         --comment "Withdrawn: filed by a sensor row whose bound did not fit the tree; the batch is withdrawn as one (see the run's purge snapshot)." \
         >/dev/null 2>&1; then
      done_n=$((done_n + 1))
    else
      failed=$((failed + 1))
      printf 'purge: #%s could not be closed\n' "$number" >&2
    fi
  fi
  [ $((done_n % 25)) -ne 0 ] || printf 'purge: %s/%s\n' "$done_n" "$count"
done < <(printf '%s' "$selected" | jq -r '.[] | "\(.id)\t\(.number)"')

printf 'purge: %s of %s %s, %s failed\n' "$done_n" "$count" \
  "$([ "$mode" = delete ] && echo deleted || echo closed)" "$failed"
[ "$failed" -eq 0 ] || exit 5
exit 0
