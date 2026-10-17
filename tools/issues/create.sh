#!/usr/bin/env bash
# Create one issue from a structured finding.
#
# The finding arrives as JSON on stdin (or via `--finding <file>`):
#
#   {"sourceId": "129", "title": "§129 …", "class": "bug", "category": "route-and-navigation",
#    "claim": "the first claim, hashed", "anchor": "app/src/main/X.kt:Symbol",
#    "labels": ["class/bug", "category/route-and-navigation", "status/fixed-by", "source/todo-import"],
#    "body": "the text, verbatim"}
#
# The fingerprint is computed over the structured fields and checked against the live tracker; a match
# refuses with exit 4 and names the issue that already exists, unless `--allow-duplicate` says the caller
# means it (the override is recorded in the output). A reserved label in any input is refused with exit 3 —
# no script here can authorise work (design D2, D4).
#
# Exit: 0 created, 2 usage/malformed, 3 policy, 4 duplicate.
#
# Usage: create.sh [--finding <file>] [--label <label>]… [--status <value>] [--allow-duplicate] [--json] [--profile <path>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

profile_arg=""
json=0
allow_duplicate=0
finding_file="-"
extra_labels=()
extra_status=""
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) profile_arg="${2:-}"; shift 2 ;;
    --finding) finding_file="${2:-}"; shift 2 ;;
    --label) extra_labels+=("${2:-}"); shift 2 ;;
    --status) extra_status="${2:-}"; shift 2 ;;
    --allow-duplicate) allow_duplicate=1; shift ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,18p' "$0"; exit 0 ;;
    *) issues_die "create: unknown argument $1" ;;
  esac
done

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
separator=$(issues_profile_value namespaceSeparator)

login=$(issues_login)
issues_preflight "$profile" "$login"

if [ "$finding_file" = "-" ]; then
  finding=$(cat)
else
  [ -f "$finding_file" ] || issues_die "create: no such finding file: $finding_file"
  finding=$(cat "$finding_file")
fi
[ -n "$finding" ] || issues_die "create: no finding on stdin — pass the JSON finding or --finding <file>"
printf '%s' "$finding" | jq -e . >/dev/null 2>&1 || issues_die "create: the finding is not valid JSON"

title=$(jq -r '.title // empty' <<< "$finding")
clazz=$(jq -r '.class // empty' <<< "$finding")
category=$(jq -r '.category // empty' <<< "$finding")
claim=$(jq -r '.claim // .title // empty' <<< "$finding")
anchor=$(jq -r '.anchor // ""' <<< "$finding")
body=$(jq -r '.body // ""' <<< "$finding")
source_id=$(jq -r '.sourceId // ""' <<< "$finding")

[ -n "$title" ] || issues_die "create: the finding has no title"
[ -n "$clazz" ] || issues_die "create: the finding has no class"
[ -n "$category" ] || issues_die "create: the finding has no category"

# Every route a label can arrive through — the finding's own array, --label and --status — lands in
# `labels` before the single guarded check below, so guarding one flag is never the invariant (design D2).
labels=()
while IFS= read -r label; do
  [ -n "$label" ] && labels+=("$label")
done < <(jq -r '.labels[]?' <<< "$finding")
for label in "${extra_labels[@]}"; do
  labels+=("$label")
done
if [ -n "$extra_status" ]; then
  labels+=("status${separator}${extra_status}")
fi

if [ "${#labels[@]}" -gt 0 ]; then
  issues_check_labels "$profile" "${labels[@]}"
fi

fingerprint=$(issues_core Fingerprint "class=$clazz" "category=$category" "claim=$claim" "anchor=$anchor")
[ -n "$fingerprint" ] || issues_die "create: could not compute a fingerprint"

existing=$(issues_issue_list)
match=$(printf '%s' "$existing" | issues_core Fingerprint --match "$fingerprint")
if [ -n "$match" ] && [ "$allow_duplicate" -eq 0 ]; then
  if [ "$json" -eq 1 ]; then
    jq -n --argjson issue "$match" --arg fingerprint "$fingerprint" \
      '{created: false, outcome: "duplicate", issue: $issue, fingerprint: $fingerprint, overridden: false}'
  else
    printf 'duplicate of #%s — fingerprint %s already exists; pass --allow-duplicate to file anyway\n' \
      "$match" "$fingerprint"
  fi
  exit "$ISSUES_EXIT_DUPLICATE"
fi
overridden=false
[ -n "$match" ] && overridden=true

new_body="$body"
if [ -n "$source_id" ]; then
  new_body="${new_body}"$'\n'"<!-- nvid: ${source_id} -->"
fi
new_body="${new_body}"$'\n'"<!-- fingerprint: ${fingerprint} -->"

args=(issue create --title "$title" --body "$new_body")
for label in "${labels[@]}"; do
  args+=(--label "$label")
done

if [ "$json" -eq 1 ]; then
  label_json=$(if [ "${#labels[@]}" -gt 0 ]; then printf '%s\n' "${labels[@]}"; fi | jq -R . | jq -s .)
  jq -n --arg title "$title" --arg fingerprint "$fingerprint" \
        --argjson labels "$label_json" --argjson overridden "$overridden" \
        '{created: true, outcome: "created", title: $title, labels: $labels, fingerprint: $fingerprint, overrideRecorded: $overridden}'
  gh "${args[@]}" >/dev/null
else
  if [ "$overridden" = true ]; then
    printf 'note: --allow-duplicate overrides an existing fingerprint (%s)\n' "$fingerprint"
  fi
  gh "${args[@]}"
fi
exit "$ISSUES_EXIT_OK"
