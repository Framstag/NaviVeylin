#!/usr/bin/env bash
# report.sh — one counting line per declared row: when it last ran, what it said, the measurement, the
# issue that carries it, both consecutive counters and the recurrence count.
#
# The numbers come from the tracker alone — the issue's own body record and the measurement markers on it
# — because the issue is the metric history (design D8). A row whose newest measurement is old, or which
# has none, gets a line of its own: a scheduled workflow is disabled after 60 days of repository
# inactivity, and that is a fact about the pipeline a report has to be able to show.
#
# Usage: report.sh [--json] [--stale-days <n>] [--now <time>] [--registry <file>] [--profile <file>]
# Exit:  0 the report was printed, 2 usage, 3 policy, 9 an incomplete row.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/lib.sh"

json=0
stale_days=60
now=""
profile_arg=""
registry_arg=""
while [ $# -gt 0 ]; do
  case "$1" in
    --json) json=1; shift ;;
    --stale-days) need_value "$@"; stale_days="${2:-60}"; shift 2 ;;
    --now) need_value "$@"; now="${2:-}"; shift 2 ;;
    --registry) need_value "$@"; registry_arg="${2:-}"; shift 2 ;;
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) sensors_die "report: unknown argument $1" ;;
  esac
done

sensors_resolve --profile "$profile_arg" --registry "$registry_arg"

now_epoch=$(sensors_epoch "${now:-${SENSORS_NOW:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}}") ||
  sensors_die "report: --now is neither an epoch nor an ISO-8601 instant"

entries="[]"
while IFS= read -r row; do
  id=$(sensors_row_field "$row" id)
  issues=$(sensors_issues_of_row "$id")
  # The marker's fields are read with `scan`, never with a named capture: a report must not be able to lose
  # a whole row because one comment's marker lacks a field (`match(...).captures[0]` is an error there, and
  # the error is indistinguishable from "this row has no measurement" once it is captured).
  entry=$(jq -c --arg id "$id" '
    [.[]? | . as $issue | (.comments // [])[] | select((.body // "") | contains("verdict="))
      | {createdAt: (.createdAt // ""), marker: (.body // ""), number: $issue.number,
         body: ($issue.body // ""), state: ($issue.state // "OPEN"),
         fingerprint: ((((.body // "") | [scan("fingerprint=[^ ]+")] | .[0]) // "") | sub("^fingerprint="; ""))}]
    | sort_by(.createdAt) as $m
    | ($m | last) as $last
    | {row: $id, measurements: ($m | length),
       last: $last,
       previous: ([$m[] | select($last != null and .fingerprint == $last.fingerprint)] | .[-2] // null)}
  ' <<< "$issues")
  if [ -z "$entry" ] || ! jq -e . >/dev/null 2>&1 <<< "$entry"; then
    # Never silently drop a row: the reason goes to the log and the row is still counted.
    sensors_note "report: could not read $id's own record — reporting the row as unreadable"
    entry=$(jq -nc --arg id "$id" '{row: $id, measurements: 0, last: null, previous: null, unreadable: true}')
  fi
  entries=$(jq -c --argjson e "$entry" '. + [$e]' <<< "$entries")
done < <(sensors_rows)

print_row() { # <entry-json>
  local id last verdict measurement number clear_run violation_run occurrence createdAt marker body
  local previous trend age stale
  id=$(jq -r '.row' <<< "$1")
  last=$(jq -c '.last' <<< "$1")
  if [ "$last" = "null" ]; then
    printf 'row=%s last=never verdict=- measurement=- issue=- clear=0 violation=0 occurrences=0 trend=-\n' "$id"
    printf 'stale row=%s no measurement on record — the row has never run or never filed\n' "$id"
    return 0
  fi
  marker=$(jq -r '.marker' <<< "$last")
  body=$(jq -r '.body' <<< "$last")
  createdAt=$(jq -r '.createdAt' <<< "$last")
  number=$(jq -r '.number' <<< "$last")
  verdict=$(sensors_marker_field "$marker" verdict)
  measurement=$(sensors_marker_field "$marker" measurement)
  clear_run=$(sensors_counter "$body" clear-run)
  violation_run=$(sensors_counter "$body" violation-run)
  occurrence=$(issues_occurrence "$body")
  trend="-"
  previous=$(jq -c '.previous' <<< "$1")
  if [ "$previous" != "null" ]; then
    trend=$(sensors_trend "$(sensors_marker_field "$(jq -r '.marker' <<< "$previous")" measurement)" "$measurement")
  fi
  printf 'row=%s last=%s verdict=%s measurement=%s issue=#%s clear=%s violation=%s occurrences=%s trend=%s\n' \
    "$id" "${createdAt:-never}" "${verdict:--}" "${measurement:--}" "$number" \
    "$clear_run" "$violation_run" "$occurrence" "$trend"
  age=$(sensors_age_days "$createdAt" "$now_epoch")
  if [ -n "$age" ] && [ "$age" -ge "$stale_days" ]; then
    printf 'stale row=%s last measurement %s days ago (a scheduled trigger that has stopped firing looks like this)\n' \
      "$id" "$age"
  fi
}

# `worse`, `better` or `same` — the trend a stored baseline would have answered, answered from the issue's
# own timeline instead.
sensors_trend() { # <previous> <current>
  local previous current
  previous=$(sensors_number "${1:-}"); current=$(sensors_number "${2:-}")
  [ -n "$previous" ] && [ -n "$current" ] || { printf '%s\n' "-"; return 0; }
  awk -v p="$previous" -v c="$current" \
    'BEGIN { if (c + 0 > p + 0) print "worse"; else if (c + 0 < p + 0) print "better"; else print "same" }'
}

sensors_age_days() { # <ISO instant> <epoch now> → whole days, or empty
  local from
  from=$(sensors_epoch "${1:-}") || { printf '\n'; return 0; }
  [ "$2" -ge "$from" ] || { printf '\n'; return 0; }
  printf '%s\n' "$(( (2 - from) / 86400 ))"
}

if [ "$json" -eq 1 ]; then
  printf '%s\n' "$entries"
else
  while IFS= read -r entry; do
    [ -n "$entry" ] || continue
    print_row "$entry"
  done < <(jq -c '.[]?' <<< "$entries")
fi
exit "$SENSORS_OK"
