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
# `--prio <p0,p1,…>` and `--unranked` are the priority pass's two selectors (`issue-metadata-loop`, D1/D6):
# the band is a *label*, so "the top of the queue" is a label query and no body is read; `--unranked` is
# "the pass has not scored this item yet", which is a legitimate state and not an error.
#
# Usage: list.sh [--id <n>] [--class <value>] [--category <value>] [--status <value>] [--author <login>]
#                [--prio <p0,p1,…>] [--unranked] [--eligible-only] [--json] [--limit <n>] [--profile <path>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

profile_arg=""
json=0
eligible_only=0
limit=400
id_arg=""
prio_filter=""
unranked=0
filters=()
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) profile_arg="${2:-}"; shift 2 ;;
    --id) id_arg="${2:-}"; shift 2 ;;     # one migrated entry, by its nvid marker
    --class) filters+=("class=${2:-}"); shift 2 ;;
    --category) filters+=("category=${2:-}"); shift 2 ;;
    --status) filters+=("status=${2:-}"); shift 2 ;;
    --author) filters+=("author=${2:-}"); shift 2 ;;
    --prio) prio_filter="${2:-}"; shift 2 ;;
    --unranked) unranked=1; shift ;;
    --eligible-only) eligible_only=1; shift ;;
    --limit) limit="${2:-}"; shift 2 ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
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

# A band outside the vocabulary is refused before the query, with the same reason a write path gives.
# The band query is a *label* query, so "the top of the queue" is answered without reading a body; the band
# namespace and the band order both come from the profile's `bands` section, so no band id is written here
# (`issue-metadata-loop`, D1, D6).
band_namespace=$(issues_core Bands meta --profile "$profile" | sed -n 's/^namespace=//p')
[ -n "$band_namespace" ] || issues_die "list: the profile declares no band namespace"
prio_order_json=$(issues_core Bands edges --profile "$profile" | awk '{print $1}' | jq -R . | jq -s .)

if [ -n "$prio_filter" ]; then
  band_labels=()
  IFS=',' read -r -a band_values <<< "$prio_filter"
  for band in "${band_values[@]}"; do
    [ -n "$band" ] || continue
    band_labels+=("${band_namespace}${separator}${band}")
  done
  [ "${#band_labels[@]}" -gt 0 ] || issues_die "list: --prio wants at least one band value"
  issues_check_labels "$profile" "${band_labels[@]}"
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
if [ -n "$prio_filter" ]; then
  bands_query=""
  for band in "${band_values[@]}"; do
    [ -n "$band" ] || continue
    [ -z "$bands_query" ] || bands_query="$bands_query or "
    bands_query="$bands_query(\$labels | index(\"${band_namespace}${separator}${band}\")) != null"
  done
  query="$query | select([.labels[].name] as \$labels | ($bands_query))"
elif [ "$unranked" -eq 1 ]; then
  query="$query | select(([.labels[].name | select(startswith(\"${band_namespace}${separator}\"))] | length) == 0)"
fi
if [ "$eligible_only" -eq 1 ]; then
  query="$query | select([.labels[].name] as \$labels | (\$required | all(. as \$r | (\$labels | index(\$r)) != null)))"
fi
query="$query]"

selected=$(printf '%s' "$issues" | jq -c --argjson required "$required_json" "$query")

# A band query answers in band order (the bands section's own order), then by issue number: the order is the
# tooling's, never the tracker's. `map(. as $band | $order | index($band))` — the binding matters, because a
# bare `index(.)` after the pipe would ask for the position of the whole order inside itself, i.e. always 0.
if [ -n "$prio_filter" ]; then
  selected=$(printf '%s' "$selected" | jq -c --argjson order "$prio_order_json" --arg sep "$separator" \
    --arg ns "$band_namespace" '
    sort_by([([.labels[].name | select(startswith($ns + $sep)) | ltrimstr($ns + $sep)]
              | map(. as $band | $order | index($band)) | min) // 999, .number])')
fi

if [ "$json" -eq 1 ]; then
  printf '%s\n' "$selected"
else
  printf '%s\n' "$selected" | jq -r '.[] | "#\(.number) \(.title) [\([.labels[].name] | join(", "))]"'
  printf '%s match(es)\n' "$(printf '%s' "$selected" | jq 'length')"
fi
exit "$ISSUES_EXIT_OK"
