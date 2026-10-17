#!/usr/bin/env bash
# List issues by label criteria.
#
# Selection is the cheap operation — titles and labels first, bodies only for candidates — so this reads one
# paged call and prints one line per match (design D3, D8).
#
# Exit: 0 ok, 2 usage, 3 policy, 4 `--id` found no issue carrying that marker.
#
# `--id <n>` resolves one migrated entry by the marker its body carries (`<!-- nvid: n -->`) and prints
# that one issue, so a `§n` citation written against the old record still finds its entry after the record is
# gone (design D3). The match is on the marker's delimiters, never on a substring: `--id 12` does not match
# `<!-- nvid: 129 -->`. An id no issue carries is a reported no-op (exit 4, the tooling's "already absent"
# code), because a citation that resolves to nothing must be visible rather than silently empty.
#
# Usage: list.sh [--id <n>] [--class <value>] [--category <value>] [--status <value>] [--author <login>]
#                [--eligible-only] [--json] [--limit <n>] [--profile <path>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

profile_arg=""
json=0
eligible_only=0
limit=400
id_arg=""
filters=()
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) profile_arg="${2:-}"; shift 2 ;;
    --id) id_arg="${2:-}"; shift 2 ;;     # one migrated entry, by its nvid marker
    --class) filters+=("class=${2:-}"); shift 2 ;;
    --category) filters+=("category=${2:-}"); shift 2 ;;
    --status) filters+=("status=${2:-}"); shift 2 ;;
    --author) filters+=("author=${2:-}"); shift 2 ;;
    --eligible-only) eligible_only=1; shift ;;
    --limit) limit="${2:-}"; shift 2 ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) issues_die "list: unknown argument $1" ;;
  esac
done

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
separator=$(issues_profile_value namespaceSeparator)

if [ -n "$id_arg" ]; then
  case "$id_arg" in
    ''|*[!0-9]*) issues_die "list: --id wants the number of a migrated entry, not '$id_arg'" ;;
  esac
  # the marker is the identity: `<!-- nvid: 129 -->` and nothing shorter
  issues=$(gh issue list --state all --limit "$limit" --json number,title,labels,author,body)
  selected=$(printf '%s' "$issues" | jq -c --arg marker "<!-- nvid: ${id_arg} -->" \
    '[.[] | select(.body // "" | contains($marker))]')
  if [ "$(printf '%s' "$selected" | jq 'length')" -eq 0 ]; then
    # an absent id is a reported no-op (the tooling's exit 4), not an argument error: the caller asked for
    # an entry and the answer is "this entry does not exist"
    issues_note "list: no issue carries <!-- nvid: ${id_arg} --> — the citation of §${id_arg} resolves to nothing"
    exit "$ISSUES_EXIT_DUPLICATE"
  fi
  if [ "$json" -eq 1 ]; then
    printf '%s\n' "$selected"
  else
    printf '%s\n' "$selected" | jq -r '.[] | "#\(.number) \(.title) [\([.labels[].name] | join(", "))]"'
  fi
  exit "$ISSUES_EXIT_OK"
fi

label_filters=()
for filter in "${filters[@]}"; do
  namespace="${filter%%=*}"
  value="${filter#*=}"
  case "$namespace" in
    author) continue ;;
    class|category|status) label_filters+=("${namespace}${separator}${value}") ;;
    *) issues_die "list: unknown filter '$namespace'" ;;
  esac
done

if [ "${#label_filters[@]}" -gt 0 ]; then
  issues_check_labels "$profile" "${label_filters[@]}"
fi

required_json=$(issues_profile_value requiredLabels | jq -R . | jq -s .)
issues=$(gh issue list --state all --limit "$limit" --json number,title,labels,author)

query='[.[]'
for filter in "${filters[@]}"; do
  namespace="${filter%%=*}"
  value="${filter#*=}"
  if [ "$namespace" = "author" ]; then
    query="$query | select(.author.login == \"$value\")"
  else
    query="$query | select(([.labels[].name] | index(\"${namespace}${separator}${value}\")) != null)"
  fi
done
if [ "$eligible_only" -eq 1 ]; then
  query="$query | select([.labels[].name] as \$labels | (\$required | all(. as \$r | (\$labels | index(\$r)) != null)))"
fi
query="$query]"

selected=$(printf '%s' "$issues" | jq -c --argjson required "$required_json" "$query")

if [ "$json" -eq 1 ]; then
  printf '%s\n' "$selected"
else
  printf '%s\n' "$selected" | jq -r '.[] | "#\(.number) \(.title) [\([.labels[].name] | join(", "))]"'
  printf '%s match(es)\n' "$(printf '%s' "$selected" | jq 'length')"
fi
exit "$ISSUES_EXIT_OK"
