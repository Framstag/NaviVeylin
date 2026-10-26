#!/usr/bin/env bash
# lib.sh — the sensor engine's shared rules: what a row must carry, what a sensor's output must look
# like, how a cadence is decided, and where a measurement lives.
#
# The engine is portable (design D12): it names no project command, no project path, no threshold and no
# project label value. The rows (`tools/sensors/sensors.json`) carry every project fact, and the issue
# tooling (`tools/issues/`) owns the tracker-facing rules — the exit codes, the profile reader, the
# policy gate, the write paths and the body record — which are reused here rather than re-implemented.
#
# Exit codes, the engine's whole contract for a caller:
#
#   0  the run determined its rows and acted (or, in a dry run, decided what it would do)
#   2  usage, or an input that cannot be read
#   3  a refusal: scope, policy, or a value the vocabulary does not contain
#   4  the run is not on the project's main branch (guard)
#   5  HEAD is not the remote main branch's commit (guard)
#   6  the working tree is not clean (guard)
#   7  the identity the run would write as is not on the allowlist (guard)
#   8  a sensor could not determine anything: nothing was written and the run failed
#   9  the registry is missing, malformed, or a row is incomplete (guard, runner)
#  75  the tracker did not answer: no verdict could be established, and none was given (guard, runner)
#
# Usage: `source lib.sh`, then `sensors_resolve [--profile <file>] [--registry <file>]` once, in the
# caller's own shell — never inside `$( )`, where a failure would exit a subshell and be swallowed.
set -uo pipefail

SENSORS_OK=0
SENSORS_USAGE=2
SENSORS_REFUSED=3
SENSORS_OFF_MAIN=4
SENSORS_HEAD_NOT_REMOTE=5
SENSORS_DIRTY=6
SENSORS_IDENTITY=7
SENSORS_ERROR=8
SENSORS_REGISTRY_BAD=9
# The tracker's outage is not a refusal: an identity nobody could read is no evidence that the author is
# unlisted, so the guard says what it knows and gives no verdict (issue #231, the ninth site of that class).
SENSORS_UNREACHABLE=75

SENSORS_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# The issue tooling is the dependency this engine writes through, shipped beside it.
SENSORS_ISSUES_DIR="$SENSORS_DIR/../issues"

# The tracker-facing rules are the issue tooling's, shipped beside this engine and reused here: the
# profile reader, the policy gate, the body record and the exit-code contract have one home each.
# shellcheck source=tools/issues/lib.sh
source "$SENSORS_ISSUES_DIR/lib.sh"

# The engine's own namespace: the label that scopes what one row owns. It is *declared* in the profile
# (one owner for the vocabulary) and its *values* are the registry's rows.
SENSORS_NAMESPACE_DEFAULT="sensor"

# Every one of these is preserved when the environment already carries it: a sensor script sources this
# library inside the environment `run.sh` set up, and a plain assignment here would wipe it.
SENSORS_ROOT="${SENSORS_ROOT:-}"
SENSORS_PROFILE="${SENSORS_PROFILE:-}"
SENSORS_REGISTRY="${SENSORS_REGISTRY:-}"
SENSORS_NAMESPACE="${SENSORS_NAMESPACE:-}"
SENSORS_SEPARATOR="${SENSORS_SEPARATOR:-}"

sensors_die() {
  printf 'sensors: %s\n' "$*" >&2
  exit "$SENSORS_USAGE"
}

sensors_note() {
  printf '%s\n' "$*" >&2
}

# A refusal with the reason code the caller branches on and the engine's own exit code.
sensors_refuse() { # <exit-code> <reason> <detail>
  local code="$1" reason="$2" detail="${3:-}"
  if [ -n "$detail" ]; then
    printf '%s: %s\n' "$reason" "$detail"
  else
    printf '%s\n' "$reason"
  fi
  exit "$code"
}

# Resolve the project once: the root and the profile come from the working tree, the registry from the
# project (with `--registry` as the explicit override the self-test uses).
sensors_resolve() {
  local profile_arg="" registry_arg=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
      --registry) need_value "$@"; registry_arg="${2:-}"; shift 2 ;;
      *) sensors_die "unknown argument $1" ;;
    esac
  done
  issues_resolve "$profile_arg"
  SENSORS_ROOT="$ISSUES_ROOT"
  SENSORS_PROFILE="$ISSUES_PROFILE"
  SENSORS_REGISTRY="${registry_arg:-${SENSORS_REGISTRY:-$SENSORS_ROOT/tools/sensors/sensors.json}}"
  [ -f "$SENSORS_REGISTRY" ] || sensors_refuse "$SENSORS_REGISTRY_BAD" "registry-missing" "$SENSORS_REGISTRY"
  jq -e . "$SENSORS_REGISTRY" >/dev/null 2>&1 ||
    sensors_refuse "$SENSORS_REGISTRY_BAD" "registry-malformed" "$SENSORS_REGISTRY"
  SENSORS_NAMESPACE=$(jq -r '.namespace // empty' "$SENSORS_REGISTRY")
  [ -n "$SENSORS_NAMESPACE" ] || SENSORS_NAMESPACE="$SENSORS_NAMESPACE_DEFAULT"
  SENSORS_SEPARATOR=$(issues_profile_value namespaceSeparator)
  SENSORS_SEPARATOR="${SENSORS_SEPARATOR:-/}"
  # The issue tooling resolves a `sensor/<id>` label against the same registry the runner read, so the two
  # cannot disagree about which checks exist (one owner for this namespace's values).
  export ISSUES_SENSOR_REGISTRY="$SENSORS_REGISTRY"
}

# --------------------------------------------------------------------------- the registry

sensors_rows() { jq -c '.rows[]?' "$SENSORS_REGISTRY"; }

sensors_row_ids() { jq -r '.rows[]?.id' "$SENSORS_REGISTRY"; }

sensors_row() { jq -c --arg id "$1" '.rows[]? | select(.id == $id)' "$SENSORS_REGISTRY"; }

# One field of a row, as text. A number or an object is printed in its JSON form, so a caller compares
# text; absence is the empty string, which is what the completeness check reports by name.
sensors_row_field() { # <row-json> <key>
  jq -r --arg k "$2" 'if has($k) then (.[$k] | if type == "string" then . else tojson end) else "" end' <<< "$1"
}

sensors_registry_value() { # a jq path, e.g. .labels.source
  jq -r "$1 // empty" "$SENSORS_REGISTRY"
}

# The label that scopes one row's issues.
sensors_label() {
  printf '%s%s%s\n' "$SENSORS_NAMESPACE" "$SENSORS_SEPARATOR" "$1"
}

# The escalation's label set, from the registry (the engine names no project label value).
sensors_escalation_labels() { jq -r '.escalation.labels[]?' "$SENSORS_REGISTRY"; }

# The recurrences after which a row stops reopening; absent means the registry's default, absent again
# means three (design D6).
sensors_row_max_recurrences() { # <row-json>
  local value
  value=$(jq -r '.k // empty' <<< "$1")
  [ -n "$value" ] || value=$(jq -r '.escalation.maxRecurrences // empty' "$SENSORS_REGISTRY")
  [ -n "$value" ] || value=3
  printf '%s\n' "$value"
}

SENSORS_REQUIRED_FIELDS="id cmd cron threshold consecutive class category lifecycle fingerprint"

# Every reason a row is not a check — each naming the field or the value at fault, so a row without a
# bound is refused by name rather than silently read as a reporter (design D9, task 1.3).
sensors_row_problems() { # <row-json> → one reason per line, empty when the row is complete
  local row="$1" field value missing="" separator
  separator="$SENSORS_SEPARATOR"; [ -n "$separator" ] || separator="/"
  for field in $SENSORS_REQUIRED_FIELDS; do
    if [ "$(jq -r --arg k "$field" 'has($k) | tostring' <<< "$row")" != "true" ]; then
      missing="$missing $field"
    fi
  done
  if [ -n "$missing" ]; then
    printf 'row-missing-field: %s\n' "${missing# }"
    return 0
  fi
  value=$(sensors_row_field "$row" id)
  case "$value" in
    ''|*[!a-z0-9-]*) printf 'row-id-not-a-name: %s\n' "$value" ;;
  esac
  value=$(sensors_row_field "$row" cmd)
  case "$value" in
    /*) printf 'row-cmd-not-relative: %s\n' "$value" ;;
    '') : ;;
    *) [ -e "$SENSORS_ROOT/$value" ] || printf 'row-cmd-missing: %s\n' "$value" ;;
  esac
  value=$(sensors_row_field "$row" cron)
  [ "$(printf '%s' "$value" | awk '{print NF}')" = "5" ] || printf 'row-cron-not-five-fields: %s\n' "$value"
  value=$(sensors_row_field "$row" threshold)
  case "$value" in
    ''|*[!0-9.]*|*.*.*) printf 'row-threshold-not-a-number: %s\n' "$value" ;;
  esac
  value=$(sensors_row_field "$row" consecutive)
  case "$value" in
    ''|*[!0-9]*) printf 'row-consecutive-not-a-count: %s\n' "$value" ;;
    0) printf 'row-consecutive-not-a-count: %s\n' "$value" ;;
  esac
  value=$(sensors_row_field "$row" lifecycle)
  case "$value" in
    file|file+close) : ;;
    *) printf 'row-lifecycle-unknown: %s\n' "$value" ;;
  esac
  if [ "$(jq -r '.fingerprint | if type == "object" and has("fields") then (.fields | length) else 0 end' <<< "$row")" -eq 0 ]; then
    printf 'row-fingerprint-without-fields\n'
  fi
  # The kind and the area the issue will carry are the profile's closed vocabulary's — checked with the
  # tooling's own reader, so a row cannot introduce a label by declaring it.
  value=$(sensors_row_field "$row" class)
  if [ -n "$value" ]; then
    issues_core Policy labels --profile "$SENSORS_PROFILE" "class${separator}${value}" >/dev/null 2>&1 ||
      printf 'row-class-outside-the-vocabulary: %s\n' "$value"
  fi
  value=$(sensors_row_field "$row" category)
  if [ -n "$value" ]; then
    issues_core Policy labels --profile "$SENSORS_PROFILE" "category${separator}${value}" >/dev/null 2>&1 ||
      printf 'row-category-outside-the-vocabulary: %s\n' "$value"
  fi
}

# The registry as a whole: every row complete, every id distinct, and the label each row owns inside the
# taxonomy the profile declares (design D9, task 1.1).
sensors_registry_problems() {
  local row id seen="" problems="" record_status
  if ! issues_profile_value namespaces | grep -qx -- "$SENSORS_NAMESPACE"; then
    printf 'namespace-not-declared:%s\n' "$SENSORS_NAMESPACE"
  fi
  # The state a row's record issue carries is the registry's, and it is validated with the profile's own
  # reader: a record with no state, or one outside the vocabulary, is one no selector could exclude
  # (issue #257).
  record_status=$(sensors_registry_value '.record.status')
  if [ -z "$record_status" ]; then
    printf 'registry-record-status-missing\n'
  elif ! issues_core Policy labels --profile "$SENSORS_PROFILE" "status${SENSORS_SEPARATOR}${record_status}" >/dev/null 2>&1; then
    printf 'registry-record-status-outside-the-vocabulary:%s\n' "$record_status"
  fi
  while IFS= read -r row; do
    id=$(sensors_row_field "$row" id)
    case " $seen " in
      *" $id "*) printf 'row-id-duplicated:%s\n' "$id" ;;
    esac
    seen="$seen $id"
    problems=$(sensors_row_problems "$row")
    if [ -n "$problems" ]; then
      printf '%s\n' "$problems" | sed "s|^|${id}: |"
    fi
  done < <(sensors_rows)
}

# --------------------------------------------------------------------------- the cadence

# A five-field cron expression against one instant, in UTC: `*`, `n`, `a-b`, `a,b,c` and `*/n`. The
# engine reads the clock the caller gives it (`SENSORS_NOW`), so a fixture clock decides a selection and
# no case has to wait for an hour. A tick is a *window* of minutes rather than one minute: GitHub fires a
# scheduled tick late, and a tick that asks about its own minute alone measures nothing when it is late.
sensors_epoch() { # <ISO-8601 UTC | epoch seconds> → epoch seconds
  local spec="$1"
  case "$spec" in
    ''|*[!0-9]*) : ;;
    *) printf '%s\n' "$spec"; return 0 ;;
  esac
  date -u -d "$spec" +%s 2>/dev/null && return 0
  date -u -j -f "%Y-%m-%dT%H:%M:%SZ" "$spec" +%s 2>/dev/null && return 0
  return 1
}

sensors_time_fields() { # <epoch> → "minute hour day-of-month month day-of-week"
  date -u -d "@$1" '+%M %H %-d %-m %w' 2>/dev/null && return 0
  date -u -r "$1" '+%M %H %-d %-m %w' 2>/dev/null && return 0
  return 1
}

sensors_cron_minute() { # <cron> <epoch> → 0 when the expression matches exactly that minute
  local cron="$1" epoch="$2" fields
  fields=$(sensors_time_fields "$epoch") || return 2
  awk -v cron="$cron" -v now="$fields" '
    function field_match(spec, value, lo, hi, i, n, parts, part, sp, step, range) {
      if (spec == "*") return 1
      n = split(spec, parts, ",")
      for (i = 1; i <= n; i++) {
        part = parts[i]
        step = 1
        if (index(part, "/") > 0) { split(part, sp, "/"); part = sp[1]; step = sp[2] + 0 }
        if (part == "*") { lo = 0; hi = 100000 }
        else if (index(part, "-") > 0) { split(part, range, "-"); lo = range[1] + 0; hi = range[2] + 0 }
        else { lo = part + 0; hi = lo }
        if (value >= lo && value <= hi && step > 0 && ((value - lo) % step) == 0) return 1
      }
      return 0
    }
    BEGIN {
      if (split(cron, f, " ") != 5) exit 2
      split(now, t, " ")
      if (!field_match(f[1], t[1] + 0)) exit 1
      if (!field_match(f[2], t[2] + 0)) exit 1
      if (!field_match(f[3], t[3] + 0)) exit 1
      if (!field_match(f[4], t[4] + 0)) exit 1
      if (f[5] != "*" && !field_match(f[5], t[5] + 0)) exit 1
      exit 0
    }'
}

sensors_cron_due() { # <cron> <epoch> [<minutes>] → 0 when the expression matches a minute in the window
  # The window is <minutes> wide and ends at <epoch>'s own minute: the tick's minute and the <minutes> - 1
  # minutes before it. A window as wide as the tick's cadence therefore covers every minute since the
  # previous tick, which is what a tick GitHub fired late selects over, while the default of one minute is
  # the punctual single-minute question this call has always asked. 2 = cannot tell: a malformed expression,
  # or a window that is not a count of minutes.
  local cron="$1" epoch="$2" minutes="${3:-1}" back=0
  case "$minutes" in ''|*[!0-9]*|0) return 2 ;; esac
  while [ "$back" -lt "$minutes" ]; do
    sensors_cron_minute "$cron" "$((epoch - back * 60))"
    case $? in
      0) return 0 ;;
      2) return 2 ;;
    esac
    back=$((back + 1))
  done
  return 1
}

# The minutes a cron's minute field names, newest first, so a backward search stops at the latest match.
sensors_cron_minutes() { # <minute-field> → the minutes 0-59, one per line, newest first
  awk -v spec="$1" '
    function field_match(spec, value, lo, hi, i, n, parts, part, sp, step, range) {
      if (spec == "*") return 1
      n = split(spec, parts, ",")
      for (i = 1; i <= n; i++) {
        part = parts[i]; step = 1
        if (index(part, "/") > 0) { split(part, sp, "/"); part = sp[1]; step = sp[2] + 0 }
        if (part == "*") { lo = 0; hi = 59 }
        else if (index(part, "-") > 0) { split(part, range, "-"); lo = range[1] + 0; hi = range[2] + 0 }
        else { lo = part + 0; hi = lo }
        if (value >= lo && value <= hi && step > 0 && ((value - lo) % step) == 0) return 1
      }
      return 0
    }
    BEGIN { for (m = 59; m >= 0; m--) if (field_match(spec, m)) print m }'
}

# The interval a late or a skipped tick selects over: (since, now]. The tick GitHub fires late (or the tick
# GitHub skipped entirely) measures every row whose cadence fell in the interval since the previous tick,
# so a missing tick delays a measurement instead of losing it (issue #256). The search walks backwards a
# day at a time and only enters the hours of a matching day, so a gap of months costs a bounded number of
# minute conversions rather than one per minute; the lower bound is exclusive, so a row measured by the
# previous tick's interval is not measured twice. 2 = cannot tell: a malformed expression, or an interval
# that is not a forward span.
sensors_cron_since() { # <cron> <since-epoch> <now-epoch> → 0 when the expression matches a minute in (since, now]
  local cron="$1" since="$2" now="$3" day_cron hour_cron candidates now_day day_limit day_start last_hour h hour_start m epoch
  # A malformed expression cannot answer; a valid one answers 0 or 1 for this single minute.
  sensors_cron_minute "$cron" "$now" >/dev/null 2>&1
  [ $? -ne 2 ] || return 2
  case "$since" in ''|*[!0-9]*) return 2 ;; esac
  case "$now" in ''|*[!0-9]*) return 2 ;; esac
  [ "$since" -le "$now" ] || return 2
  # Cron names minutes, not seconds.
  now=$((now - now % 60))
  since=$((since - since % 60))
  [ "$since" -lt "$now" ] || return 1
  # The day-only and day+hour predicates, and the minutes the minute field names, newest first.
  day_cron=$(printf '%s\n' "$cron" | awk '{$1="*"; $2="*"; print}')
  hour_cron=$(printf '%s\n' "$cron" | awk '{$1="*"; print}')
  candidates=$(sensors_cron_minutes "$(printf '%s\n' "$cron" | awk '{print $1}')") || return 2
  # Walk backwards a day at a time. A five-field expression matches at least once in four years (29 February
  # is the coarsest day), so the search is bounded by four years and a day.
  now_day=$((now - now % 86400))
  day_limit=$((since - since % 86400))
  day_start=$now_day
  while [ "$day_start" -ge "$day_limit" ]; do
    if sensors_cron_minute "$day_cron" "$day_start" >/dev/null 2>&1; then
      last_hour=$(( (now - day_start) / 3600 ))
      [ "$last_hour" -gt 23 ] && last_hour=23
      h=$last_hour
      while [ "$h" -ge 0 ]; do
        hour_start=$((day_start + h * 3600))
        if sensors_cron_minute "$hour_cron" "$hour_start" >/dev/null 2>&1; then
          while IFS= read -r m; do
            [ -n "$m" ] || continue
            epoch=$((hour_start + m * 60))
            [ "$epoch" -gt "$now" ] && continue
            [ "$epoch" -le "$since" ] && continue
            return 0
          done <<< "$candidates"
        fi
        h=$((h - 1))
      done
    fi
    [ $((now_day - day_start)) -ge $((1461 * 86400)) ] && break
    day_start=$((day_start - 86400))
  done
  return 1
}

# --------------------------------------------------------------------------- a finding

# The condition's identity: the digest of the fields the row declares. A sensor computes it with this
# function, so a run and its fixture agree by construction, and the runner recomputes it before it
# files anything.
sensors_fingerprint() { # <finding-json> <space-separated field names>
  local finding="$1" fields="$2" field line=""
  # The field order is canonical (sorted), so the identity does not depend on the order a caller happens to
  # list its fields in — the runner and a sensor must agree by construction, not by coincidence.
  for field in $(printf '%s\n' $fields | sort); do
    line="$line$(jq -r --arg f "$field" '"\($f)=\(.[$f] // "")"' <<< "$finding")"$'\n'
  done
  printf '%s' "$line" | issues_sha256
}

sensors_finding_fields() { # <row-json>
  jq -r '.fingerprint.fields[]?' <<< "$1"
}

# Every reason one finding is not the contract's — a named reason for a missing field, a verdict outside
# the three, or a fingerprint that is not the digest of its own fields (design D5, D9).
sensors_finding_problems() { # <row-json> <finding-json>
  local row="$1" finding="$2" verdict field fields computed reasons=""
  verdict=$(jq -r '.verdict // empty' <<< "$finding")
  case "$verdict" in
    violation|clear|error) : ;;
    *) reasons="$reasons verdict=${verdict:-(missing)}" ;;
  esac
  [ -n "$(jq -r '.measurement // empty' <<< "$finding")" ] || reasons="$reasons measurement(missing)"
  [ -n "$(jq -r '.fingerprint // empty' <<< "$finding")" ] || reasons="$reasons fingerprint(missing)"
  fields=$(sensors_finding_fields "$row")
  for field in $fields; do
    [ -n "$(jq -r --arg f "$field" '.[$f] // empty' <<< "$finding")" ] || reasons="$reasons field:${field}(missing)"
  done
  if [ -n "$fields" ] && [ -n "$(jq -r '.fingerprint // empty' <<< "$finding")" ]; then
    computed=$(sensors_fingerprint "$finding" "$fields")
    [ "$computed" = "$(jq -r '.fingerprint' <<< "$finding")" ] || reasons="$reasons fingerprint-not-from-its-own-fields"
  fi
  printf '%s\n' "${reasons# }"
}

# --------------------------------------------------------------------------- the tracker

# The issues one row owns: the label it declares is the whole selection, so a check can reach nothing
# else (design D7).
sensors_issues_of_row() { # <row-id> → a JSON array
  local label
  label=$(sensors_label "$1")
  # `createdAt` is the instant a filing's own marker carries, so it is requested with the rest: one write
  # produces the issue and the marker, so the issue's creation time is the marker's (issue #668).
  gh issue list --state all --limit 400 --json number,title,body,labels,state,stateReason,author,comments,createdAt 2>/dev/null |
    jq -c --arg label "$label" '[.[]? | select([(.labels // [])[]?.name] | index($label) != null)]'
}

# The one issue a fingerprint identifies, or nothing. The marker is matched with its delimiters, never as
# a substring: the fingerprint of one condition cannot match another's.
sensors_issue_for_fingerprint() { # <issues-json> <fingerprint> → the issue, or empty
  jq -c --arg marker "<!-- fingerprint: $2 -->" \
    '[.[]? | select((.body // "") | contains($marker))] | if length > 0 then .[0] else empty end' <<< "$1"
}

# The measurement marker every run posts, so the issue's own timeline answers "what were the numbers".
sensors_marker() { # <row-id> <finding-json>
  local id="$1" finding="$2"
  printf '<!-- %s: %s fingerprint=%s verdict=%s measurement=%s baseline=%s -->\n' \
    "$SENSORS_NAMESPACE" "$id" \
    "$(jq -r '.fingerprint // empty' <<< "$finding")" \
    "$(jq -r '.verdict // empty' <<< "$finding")" \
    "$(jq -r '.measurement // empty' <<< "$finding")" \
    "$(jq -r '.baseline // empty' <<< "$finding")"
}

# The measurement the issue's own record carries for one fingerprint: the newest marker, so the previous
# number is the baseline and nothing is committed (design D8).
sensors_recorded_measurement() { # <issue-json> <fingerprint> → the newest measurement, or empty
  jq -r --arg fp "$2" '
    [(.comments // [])[] | select((.body // "") | contains("fingerprint=" + $fp))]
    | sort_by(.createdAt // "") | last | (.body // "")' <<< "$1" |
    sed -n 's/.*measurement=\([^ ][^ ]*\).*/\1/p'
}

# A measurement's number, when it is one: the trend comparison and the report's "worse than last time"
# both read it, and a measurement that carries no number simply has no trend.
sensors_number() { # <text> → the first number, or empty
  printf '%s' "${1:-}" | grep -oE '[-+]?[0-9]+(\.[0-9]+)?' | head -1
}

# The body record's counters. `occurrence` is the tooling's own key (the metadata pass reads it too);
# the two run counters are this lifecycle's, and they live in the issue, never in a side file (D6, D8).
sensors_counter() { # <body> <key> → the count, defaulting to 0
  local value
  value=$(issues_body_field "${1:-}" "$2")
  value=$(printf '%s' "$value" | grep -oE '[0-9]+' | head -1)
  printf '%s\n' "${value:-0}"
}

sensors_body_set() { # <body> <key> <value> → the body with that record entry set
  issues_body_set "$1" "$2" "$3"
}

# The row's record issue: the one issue per row that carries the measurements taken while the row has no
# condition to carry them, so a healthy check reaches the tracker as a verdict rather than as silence
# (issue #257). It is identified by its own marker, never by a fingerprint: no finding a check reports
# matches it, so the lifecycle never reopens or closes it.
sensors_record_issue() { # <issues-json> <row-id> → the issue, or empty
  jq -c --arg marker "<!-- sensor-record: $2 -->" \
    '[.[]? | select((.body // "") | contains($marker))] | if length > 0 then .[0] else empty end' <<< "$1"
}

# The newest measurement marker of one row, across its issues — what the report prints as "last run" and what
# a baseline-multiple row compares against. Sorted by the marker's own instant: the issues arrive
# newest-first, and a row carries a record issue beside its condition issues, so the last of the unsorted list
# is the newest marker of the oldest issue rather than the newest marker of the row.
#
# A run has two places to write a marker and only one of them is a comment: a run that *files* embeds the same
# marker in the new issue's body (`sensors_issue_body`). Both are read here, because a row whose newest event is
# a filing carries no comment at all — and reading comments only made such a row report that it never ran, and
# handed the next comparison an empty baseline, three minutes after it filed (issue #668). A filing's marker has
# no instant of its own (the issue and the marker are one write), so the issue's `createdAt` is its instant.
sensors_markers_json() { # <issues-json> → every marker the row's issues carry, oldest first
  jq -c '
    [ .[]? | . as $issue
      | ( [ (.comments // [])[] | select((.body // "") | contains("verdict="))
            | {createdAt: (.createdAt // ""), marker: (.body // "")} ]
          + [ ($issue.body // "") | [scan("<!--[^>]*verdict=[^>]*-->")] | .[0] // empty
              | {createdAt: ($issue.createdAt // ""), marker: .} ] )
      | .[]
      | . + {number: ($issue.number // 0), body: ($issue.body // ""), state: ($issue.state // "OPEN"),
             fingerprint: (((($issue.body // "") | [scan("fingerprint=[^ ]+")] | .[0]) // "") | sub("^fingerprint="; ""))}
    ]
    | sort_by(.createdAt)' <<< "$1"
}

sensors_last_marker() { # <issues-json>
  sensors_markers_json "$1" | jq -r 'last | .marker // ""'
}

sensors_marker_field() { # <marker> <name>
  printf '%s' "${1:-}" | sed -n "s/.*[[:space:]]${2}=\([^ ]*\).*/\1/p"
}

# The conditions the row's issues already record, as the sensor's own input: the fingerprint, the
# condition's fields (from the issue's body record), its state and the recorded measurement. A sensor
# needs this to report a *cleared* condition at all — the clearing finding has to carry the identity of
# the condition it clears, and the sensor is the only thing that can decide that.
sensors_conditions_json() { # <issues-json> → a JSON array
  # Read with `scan`, never with a named capture: a marker missing one field must degrade to an empty value
  # rather than error, because an error here would silently empty the whole conditions list. The pattern takes
  # the object between the braces, never everything up to the next `>`: the marker the engine writes carries a
  # ` -->` tail, so a tail-tolerant pattern yields `{"job":"build","case":"apk"} --` and every condition
  # parses to `{}` — which turns a green build's clear into a finding that owns no issue (#230).
  jq -c '[.[]? | select(((.body // "") | contains("<!-- sensor-record:")) | not) | . as $issue | {
      number: $issue.number,
      state: ($issue.state // "OPEN"),
      fingerprint: (((($issue.body // "") | [scan("fingerprint=[^ ]+")] | .[0]) // "") | sub("^fingerprint="; "")),
      condition: ((((($issue.body // "") | [scan("condition:[ ]*\\{[^}]*\\}")] | .[0]) // "") | sub("^condition:[ ]*"; "") | fromjson?) // {}),
      measurement: (((($issue.body // "") | [scan("measurement=[^ ]+")] | .[0]) // "") | sub("^measurement="; ""))
    }]' <<< "$1"
}

# --------------------------------------------------------------------------- comparing a measurement

# The bound a row's threshold stands for when the row measures against a recorded baseline rather than an
# absolute number: the baseline is the issue's own previous measurement (design D8), and an absent
# baseline means there is nothing to compare yet, which the caller reports as no condition.
sensors_bound_from_baseline() { # <baseline> <threshold> → the bound, or empty
  awk -v b="${1:-0}" -v t="${2:-0}" 'BEGIN { if (b + 0 <= 0 || t + 0 <= 0) exit 0; printf "%.6f\n", b * t }'
}

# True when the measurement violates the bound under the row's comparison.
sensors_compare() { # <measurement> <bound> <comparison>
  awk -v m="${1:-}" -v b="${2:-}" -v c="${3:-greater-than}" '
    BEGIN {
      if (b == "" || m == "") exit 2
      if (c == "greater-than") exit !(m + 0 > b + 0)
      if (c == "below-floor") exit !(m + 0 < b + 0)
      if (c == "boolean") exit !(m + 0 >= b + 0)
      exit 2
    }'
}

# One finding, in the contract's shape: the fingerprint and the measurement, the verdict, the fields the
# fingerprint is derived from (at the top level), and the baseline the issue's record carried.
sensors_emit() { # <fingerprint> <measurement> <verdict> <fields-json> [baseline] [title] [claim]
  jq -nc --arg fp "$1" --arg m "$2" --arg v "$3" --argjson f "$4" \
         --arg b "${5:-}" --arg t "${6:-}" --arg c "${7:-}" \
    '$f + {fingerprint: $fp, measurement: $m, verdict: $v, baseline: $b,
           title: (if $t == "" then null else $t end),
           claim: (if $c == "" then null else $c end)}'
}

# A finding that says nothing could be determined: it still carries the condition's own fields — so it is
# the contract's shape and the runner reads it as the third outcome rather than as a malformed line — and
# the reason as its measurement. The run fails on it and writes nothing (design D5).
sensors_emit_error() { # <fields-json> <reason-token>
  local fields="$1" reason="$2" names fp
  names=$(jq -r 'keys[]' <<< "$fields" | tr '\n' ' ')
  fp=$(sensors_fingerprint "$fields" "$names")
  sensors_emit "$fp" "error-${reason}" error "$fields" "" "" \
    "the check could not determine anything: ${reason}"
}
