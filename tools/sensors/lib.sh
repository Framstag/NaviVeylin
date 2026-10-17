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
      --profile) profile_arg="${2:-}"; shift 2 ;;
      --registry) registry_arg="${2:-}"; shift 2 ;;
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
  local row id seen="" problems=""
  if ! issues_profile_value namespaces | grep -qx -- "$SENSORS_NAMESPACE"; then
    printf 'namespace-not-declared:%s\n' "$SENSORS_NAMESPACE"
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
# no case has to wait for an hour.
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

sensors_cron_due() { # <cron> <epoch> → 0 when the expression matches that minute
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
  gh issue list --state all --limit 400 --json number,title,body,labels,state,stateReason,author,comments 2>/dev/null |
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

# The newest measurement marker of one row, across its issues — what the report prints as "last run".
sensors_last_marker() { # <issues-json>
  jq -r '[(.[]? | (.comments // [])[] | .body // "") | select(contains("verdict="))] | last // ""' <<< "$1"
}

sensors_marker_field() { # <marker> <name>
  printf '%s' "${1:-}" | sed -n "s/.*[[:space:]]${2}=\([^ ]*\).*/\1/p"
}

# The conditions the row's issues already record, as the sensor's own input: the fingerprint, the
# condition's fields (from the issue's body record), its state and the recorded measurement. A sensor
# needs this to report a *cleared* condition at all — the clearing finding has to carry the identity of
# the condition it clears, and the sensor is the only thing that can decide that.
sensors_conditions_json() { # <issues-json> → a JSON array
  jq -c '[.[]? | . as $issue | {
      number: $issue.number,
      state: ($issue.state // "OPEN"),
      fingerprint: (($issue.body // "") | match("<!-- fingerprint:[ ]*(?<fp>[^ ]+) -->") | .captures[0].string) // "",
      condition: ((($issue.body // "") | match("<!-- condition:[ ]*(?<c>[^>]*?)[ ]*-->") | .captures[0].string) // "{}") | fromjson?,
      measurement: (($issue.body // "") | match("<!-- sensor:[ ]*[^ ]+ fingerprint=[^ ]+ verdict=[^ ]+ measurement=(?<m>[^ ]+)") | .captures[0].string) // ""
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
