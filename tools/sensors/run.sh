#!/usr/bin/env bash
# run.sh — the runner: select the rows whose cadence is due, run each, interpret its verdict, and
# dispatch to the three verbs.
#
#   run.sh [--due] [--within <minutes>] [--since <time>] [--rows <id,id>] [--only <id>] [--apply] [--json] [--input <file>] [--now <time>]
#          [--registry <file>] [--profile <file>]
#
# `--rows <id,id>` (repeatable) restricts the candidates to the rows it names — a pipeline step runs one
# family of rows this way; each row's own cadence still decides whether it runs, so the flag is combined
# with `--due`. An id the registry does not declare is refused before any row runs. `--only <id>` is the
# same restriction for one id, and `--only` alone runs it whatever its cadence says.
#
# `--within <minutes>` is the width of the window `--due` selects over, in minutes, ending at this tick's
# own minute: a row is due when its cron matches any minute in it, so a tick GitHub fires late still
# selects the rows it was scheduled for. Omitted, the window is that minute alone, which is all a punctual
# tick needs; the pipeline declares a window as wide as its own cadence. A value that is not a count of
# minutes is refused rather than narrowed, because a silently narrowed window is the defect this flag
# exists to fix (issue #166).
#
# `--since <time>` is the interval `--due` selects over, as an exclusive lower bound: a row is due when
# its cron matches any minute in `(since, this tick's minute]`. It is what the pipeline passes — the
# previous tick's start — so a tick GitHub skipped leaves its minutes in the next tick's interval and the
# row whose cadence fell into the gap is measured instead of lost (issue #256). `--since` takes
# precedence over `--within` when both are given.
#
# `--dry-run` is the default, as everywhere else in this tooling: without `--apply` nothing is written to
# the tracker and every intended write is printed instead (design D9).
#
# The three outcomes (design D5):
#
#   violation  file, reopen or annotate the issue the condition's fingerprint identifies, and record the
#              measurement on it
#   clear      record the clearing measurement and close — but only after `consecutive` clear runs
#   error      post nothing, mark the row failed, exit non-zero: a broken check must never read as a
#              cleared condition
#
# Nothing here writes a baseline file, and nothing here touches the work tree: the issue is the metric
# history (design D8). Every write goes through the issue tooling's own verbs, so the reserved-label
# refusal and the closed vocabulary apply to a sensor exactly as they apply to a person.
#
# Exit: 0 acted (or decided, in a dry run), 2 usage, 3 refusal, 8 a sensor could not determine
# anything, 9 an incomplete row, 75 the tracker did not answer — no verdict was given.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/lib.sh"

usage() { sed -n '2,42p' "$0"; }

due=0
only_id=""
rows_ids=""
within=1
since_arg=""
json=0
input=""
now=""
profile_arg=""
registry_arg=""
while [ $# -gt 0 ]; do
  case "$1" in
    --due) due=1; shift ;;
    --within) need_value "$@"; within="${2:-}"; shift 2 ;;
    --since) need_value "$@"; since_arg="${2:-}"; shift 2 ;;
    --only) need_value "$@"; only_id="${2:-}"; shift 2 ;;
    --rows) need_value "$@"; rows_ids="${rows_ids:+$rows_ids,}${2:-}"; shift 2 ;;
    --dry-run) SENSORS_DRY_RUN=1; shift ;;
    --apply) SENSORS_DRY_RUN=0; shift ;;
    --json) json=1; shift ;;
    --input) need_value "$@"; input="${2:-}"; shift 2 ;;
    --now) need_value "$@"; now="${2:-}"; shift 2 ;;
    --registry) need_value "$@"; registry_arg="${2:-}"; shift 2 ;;
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) sensors_die "run: unknown argument $1" ;;
  esac
done
# A dry run is the default; only `--apply` writes to the tracker.
SENSORS_DRY_RUN="${SENSORS_DRY_RUN:-1}"
# The window above is a count of minutes and it is the caller's to declare, so a value that is not one is
# refused before anything is read: narrowing it silently to the tick's own minute is what made a late tick a
# no-op (issue #166).
case "$within" in
  ''|*[!0-9]*|0) sensors_die "run: --within '$within' is not a count of minutes (a whole number, at least 1)" ;;
esac
SENSORS_INPUT="$input"
SENSORS_FAILED=0
SENSORS_CURRENT_ID=""
export SENSORS_DRY_RUN

# The record cache: one number per row whose record issue this run filed, so two clear findings of one row
# file one record issue and not two (a row can print an orphan clear for more than one identity in one run).
SENSORS_RECORD_STATE=$(mktemp -d "${TMPDIR:-/tmp}/sensors-record.XXXXXX") ||
  sensors_die "run: cannot create a scratch directory"
trap 'rm -rf "$SENSORS_RECORD_STATE"' EXIT

sensors_resolve --profile "$profile_arg" --registry "$registry_arg"

SENSORS_NOW="${now:-${SENSORS_NOW:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}}"
export SENSORS_NOW
epoch=$(sensors_epoch "$SENSORS_NOW") || sensors_die "run: --now '$SENSORS_NOW' is neither an epoch nor an ISO-8601 instant"
since_epoch=0
if [ -n "$since_arg" ]; then
  since_epoch=$(sensors_epoch "$since_arg") || sensors_die "run: --since '$since_arg' is neither an epoch nor an ISO-8601 instant"
fi

# The identity the run would write as is the gate's only attributable term, so it is checked before any
# row is selected — a run that may not write must not report a working set either (design D2).
issues_preflight "$SENSORS_PROFILE"

# --------------------------------------------------------------------------- selection

# The rows this run was given. `--rows` names them (comma-separated, and repeatable), and every one of
# them has to be a row the registry declares: a step that asks for a check which does not exist must be
# refused rather than run the rest and report nothing (design D9).
rows_list=$(printf '%s' "$rows_ids" | tr ',' ' ')
for id in $rows_list; do
  sensors_row_ids | grep -Fxq -- "$id" ||
    sensors_die "run: no row with id '$id' — the registry declares: $(sensors_row_ids | tr '\n' ' ')"
done

selected=()
while IFS= read -r row; do
  id=$(sensors_row_field "$row" id)
  if [ -n "$rows_list" ]; then
    printf '%s\n' $rows_list | grep -Fxq -- "$id" || continue
  fi
  if [ -n "$only_id" ]; then
    [ "$id" = "$only_id" ] || continue
  elif [ "$due" -eq 1 ]; then
    if [ -n "$since_arg" ]; then
      sensors_cron_since "$(sensors_row_field "$row" cron)" "$since_epoch" "$epoch" || continue
    else
      sensors_cron_due "$(sensors_row_field "$row" cron)" "$epoch" "$within" || continue
    fi
  fi
  selected+=("$id")
done < <(sensors_rows)

if [ -n "$only_id" ] && [ "${#selected[@]}" -eq 0 ]; then
  sensors_die "run: no row with id '$only_id' — the registry declares: $(sensors_row_ids | tr '\n' ' ')"
fi
if [ "${#selected[@]}" -eq 0 ]; then
  printf 'no rows selected at %s\n' "$SENSORS_NOW"
  exit "$SENSORS_OK"
fi

# --------------------------------------------------------------------------- the run

# The environment one row's command runs with: the row itself, its bound, its consecutive count, the
# baseline the issue's own record carries, and the clock. The engine names no project value here — the
# command reads what this project declared (design D12).
sensors_exec_cmd() { # <row> <id> → the command's stdout
  local row="$1" id="$2" cmd path
  cmd=$(sensors_row_field "$row" cmd)
  if [ -n "${SENSORS_INPUT:-}" ]; then
    path="$SENSORS_INPUT"
  else
    path=$(sensors_row_field "$row" input)
  fi
  case "$path" in
    ''|/*) : ;;
    *) path="$SENSORS_ROOT/$path" ;;
  esac
  (
    cd "$SENSORS_ROOT" || exit 2
    SENSORS_ID="$id" \
    SENSORS_ROW="$row" \
    SENSORS_THRESHOLD="$(sensors_row_field "$row" threshold)" \
    SENSORS_COMPARISON="$(sensors_row_field "$row" comparison)" \
    SENSORS_CONSECUTIVE="$(sensors_row_field "$row" consecutive)" \
    SENSORS_INPUT="$path" \
    SENSORS_NOW="$SENSORS_NOW" \
    SENSORS_ROOT="$SENSORS_ROOT" \
    SENSORS_REGISTRY="$SENSORS_REGISTRY" \
    SENSORS_PROFILE="$SENSORS_PROFILE" \
    SENSORS_NAMESPACE="$SENSORS_NAMESPACE" \
    SENSORS_SEPARATOR="$SENSORS_SEPARATOR" \
    SENSORS_BASELINE="${SENSORS_BASELINE:-}" \
    SENSORS_CONDITIONS="${SENSORS_CONDITIONS:-[]}" \
    bash "./$cmd"
  )
}

# A report line per finding: the row, the condition's identity, its verdict, the number, the issue, and
# the verb the run chose — the text form is the log, the JSON form is the machine's.
sensors_report() { # <row-id> <fingerprint> <verdict> <measurement> <issue|-> <action>
  if [ "$json" -eq 1 ]; then
    jq -nc --arg row "$1" --arg fingerprint "$2" --arg verdict "$3" --arg measurement "$4" \
           --arg issue "$5" --arg action "$6" \
      '{row: $row, fingerprint: $fingerprint, verdict: $verdict, measurement: $measurement,
        issue: (if $issue == "-" then null else $issue end), action: $action}'
  else
    printf '%s: fingerprint=%s verdict=%s measurement=%s issue=%s action=%s\n' \
      "$1" "$2" "$3" "$4" "$5" "$6"
  fi
  return 0
}

# The one guarded write path: a dry run prints what it would do and touches nothing.
sensors_write() { # <description> <command…>
  local description="$1"; shift
  if [ "$SENSORS_DRY_RUN" -eq 1 ]; then
    printf 'dry-run: would %s\n' "$description"
    return 0
  fi
  "$@" >&2
}

# The condition's own fields, as a body record: the sensor reads them back (through
# `SENSORS_CONDITIONS`) to report the *clearing* of an identity it did not invent.
sensors_condition_record() { # <row> <finding>
  local row="$1" finding="$2" field out="{}"
  for field in $(sensors_finding_fields "$row"); do
    out=$(jq -c --arg k "$field" \
      --arg v "$(jq -r --arg f "$field" '(.[$f] // "") | gsub("[>\n]"; "-")' <<< "$finding")" \
      '. + {($k): $v}' <<< "$out")
  done
  printf '%s\n' "$out"
}

sensors_issue_body() { # <row> <finding> <baseline>
  local row="$1" finding="$2" baseline="${3:-}"
  [ -n "$baseline" ] || baseline="none recorded"
  printf '%s\n\n- **Observed** ✗: %s\n- **Bound**: %s (%s)\n- **Baseline**: %s\n- **Detected by**: `%s`\n\n%s\n<!-- condition: %s -->' \
    "$(jq -r '.claim // .measurement' <<< "$finding")" \
    "$(jq -r '.measurement' <<< "$finding")" \
    "$(sensors_row_field "$row" threshold)" "$(sensors_row_field "$row" comparison)" \
    "$baseline" "$(sensors_row_field "$row" cmd)" \
    "$(sensors_marker "$(sensors_row_field "$row" id)" "$finding")" \
    "$(sensors_condition_record "$row" "$finding")"
}

# The labels a filed issue carries: its kind and area from the row, the ownership label from the row's
# id, and the registry's fixed ones — neither engine names a value.
sensors_labels_json() { # <row> <id> [<status>]
  local row="$1" id="$2" status="${3:-}" labels="[]" namespace value
  labels=$(jq -c --arg l "class${SENSORS_SEPARATOR}$(sensors_row_field "$row" class)" '. + [$l]' <<< "$labels")
  labels=$(jq -c --arg l "category${SENSORS_SEPARATOR}$(sensors_row_field "$row" category)" '. + [$l]' <<< "$labels")
  labels=$(jq -c --arg l "$(sensors_label "$id")" '. + [$l]' <<< "$labels")
  while IFS=$'\t' read -r namespace value; do
    [ -n "$namespace" ] || continue
    # The record issue's state is the registry's, not the fixed set's: a record is not a work item
    # (issue #257).
    if [ -n "$status" ] && [ "$namespace" = "status" ]; then value="$status"; fi
    labels=$(jq -c --arg l "${namespace}${SENSORS_SEPARATOR}${value}" '. + [$l]' <<< "$labels")
  done < <(jq -r '.labels // {} | to_entries[] | [.key, .value] | @tsv' "$SENSORS_REGISTRY")
  printf '%s\n' "$labels"
}

sensors_file() { # <row> <id> <finding> <baseline>
  local row="$1" id="$2" finding="$3" baseline="${4:-}" fp title file body labels
  fp=$(jq -r '.fingerprint' <<< "$finding")
  title=$(jq -r '.title // empty' <<< "$finding")
  [ -n "$title" ] || title="${id}: $(jq -r '.measurement' <<< "$finding")"
  body=$(sensors_issue_body "$row" "$finding" "$baseline")
  labels=$(sensors_labels_json "$row" "$id")
  file=$(mktemp) || sensors_die "run: cannot create a temporary file"
  jq -n --arg title "$title" --arg class "$(sensors_row_field "$row" class)" \
        --arg category "$(sensors_row_field "$row" category)" \
        --arg claim "$(jq -r '.claim // .measurement' <<< "$finding")" \
        --arg fingerprint "$fp" --arg body "$body" --argjson labels "$labels" \
    '{title: $title, class: $class, category: $category, claim: $claim,
      fingerprint: $fingerprint, labels: $labels, body: $body}' > "$file"
  sensors_write "file a new issue for $id (fingerprint $fp, measurement $(jq -r '.measurement' <<< "$finding"))" \
    bash "$SENSORS_ISSUES_DIR/create.sh" --sensor "$id" --finding "$file"
  rm -f "$file"
}

# The body of a row's record issue: it names the row and carries the record's own marker, so the issue is
# found again without a fingerprint and no finding a check reports can match it (issue #257).
sensors_record_body() { # <row-id>
  printf 'The `%s` check has no condition to carry a measurement, so its measurements are kept here.\n\n' "$1"
  printf 'This issue is a record, not a condition: the check files it once, and every measurement it takes\n'
  printf 'while no condition issue exists is posted as a comment below. That is how a check that ran and was\n'
  printf 'green reaches the tracker as a verdict instead of as silence. No finding a check reports carries this\n'
  printf "issue's identity, so the lifecycle never reopens or closes it.\n\n"
  printf '<!-- sensor-record: %s -->\n<!-- occurrence: 0 -->\n' "$1"
}

# The row's record issue, filed once: the issue a green row's measurements go to, so the report can tell a row
# that ran and was green from one that never ran (issue #257). It carries the row's label, kind and area like
# any item, and the registry's own record state (`record.status`) so no run may take it — it is a record, never
# a work item. The created issue's number comes back from the writer's own output, because the measurement that
# made it necessary is posted on it in the same run.
sensors_file_record() { # <row> <id> → the created issue's number, or empty
  local row="$1" id="$2" status fp title claim body labels file out code number
  status=$(sensors_registry_value '.record.status')
  [ -n "$status" ] ||
    sensors_refuse "$SENSORS_REGISTRY_BAD" "registry-record-status-missing" \
      "the registry declares no .record.status, so a row's record would carry no state"
  fp="record-$id"
  title="sensor record: $id"
  claim="the $id check's measurement record"
  body=$(sensors_record_body "$id")
  labels=$(sensors_labels_json "$row" "$id" "$status")
  if [ "$SENSORS_DRY_RUN" -eq 1 ]; then
    printf 'dry-run: would file the record issue for %s (fingerprint %s, status %s)\n' "$id" "$fp" "$status"
    return 0
  fi
  file=$(mktemp) || sensors_die "run: cannot create a temporary file"
  jq -n --arg title "$title" --arg class "$(sensors_row_field "$row" class)" \
        --arg category "$(sensors_row_field "$row" category)" \
        --arg claim "$claim" --arg fingerprint "$fp" --arg body "$body" --argjson labels "$labels" \
    '{title: $title, class: $class, category: $category, claim: $claim,
      fingerprint: $fingerprint, labels: $labels, body: $body}' > "$file"
  out=$(bash "$SENSORS_ISSUES_DIR/create.sh" --sensor "$id" --finding "$file" 2>&1)
  code=$?
  rm -f "$file"
  printf '%s\n' "$out" >&2
  [ "$code" -eq 0 ] || return 0
  number=$(printf '%s' "$out" | grep -oE '/issues/[0-9]+' | tail -1 | tr -dc '0-9')
  [ -n "$number" ] ||
    sensors_die "run: the record issue for $id was filed but the writer printed no number: $out"
  printf '%s\n' "$number"
}

sensors_comment_file() { # <finding> <note> → the path of a temporary comment file
  local finding="$1" note="$2" file
  file=$(mktemp) || sensors_die "run: cannot create a temporary file"
  {
    printf '%s\n\n' "$(jq -r '.measurement' <<< "$finding")"
    printf '%s\n' "$note"
    printf '%s\n' "$(sensors_marker "$SENSORS_CURRENT_ID" "$finding")"
  } > "$file"
  printf '%s\n' "$file"
}

sensors_body_file() { # <body> → the path of a temporary body file
  local file
  file=$(mktemp) || sensors_die "run: cannot create a temporary file"
  printf '%s' "$1" > "$file"
  printf '%s\n' "$file"
}

# One measurement, recorded on the issue, with the body record's counters moved: the issue's own timeline
# is the history (design D8).
sensors_record() { # <row> <number> <finding> <body> <note>
  local row="$1" number="$2" finding="$3" body="$4" note="$5" bodyfile commentfile
  bodyfile=$(sensors_body_file "$body")
  commentfile=$(sensors_comment_file "$finding" "$note")
  sensors_write "record the measurement on #$number ($(jq -r '.measurement' <<< "$finding"))" \
    bash "$SENSORS_ISSUES_DIR/update.sh" "$number" --body "$bodyfile" --comment-file "$commentfile"
  rm -f "$bodyfile" "$commentfile"
}

# One measurement posted as a comment, with no body write: a record issue's body carries no condition
# counters, so there is nothing a run has to move there.
sensors_comment() { # <number> <finding> <note>
  local number="$1" finding="$2" note="$3" commentfile
  commentfile=$(sensors_comment_file "$finding" "$note")
  sensors_write "record the measurement on #$number ($(jq -r '.measurement' <<< "$finding"))" \
    bash "$SENSORS_ISSUES_DIR/update.sh" "$number" --comment-file "$commentfile"
  rm -f "$commentfile"
}

# The number of the row's record issue, filing it when the row has none: the record cache keeps one filing per
# row per run, because a row can report more than one clear identity in one run.
sensors_record_number() { # <row> <id> <issues> → the number, or empty
  local row="$1" id="$2" issues="$3" issue number
  if [ -f "$SENSORS_RECORD_STATE/$id" ]; then
    cat "$SENSORS_RECORD_STATE/$id"
    return 0
  fi
  issue=$(sensors_record_issue "$issues" "$id")
  number=""
  [ -n "$issue" ] && number=$(jq -r '.number // empty' <<< "$issue")
  [ -n "$number" ] || number=$(sensors_file_record "$row" "$id")
  [ -n "$number" ] || return 0
  printf '%s\n' "$number" > "$SENSORS_RECORD_STATE/$id" 2>/dev/null || true
  printf '%s\n' "$number"
}

# A clear whose condition no open issue carries is still a verdict: it reaches the tracker on the row's own
# record issue, which the report reads beside the condition issues (issue #257).
sensors_record_clear() { # <row> <id> <finding> <issues> → the record issue's number, or empty
  local row="$1" id="$2" finding="$3" issues="$4" number
  number=$(sensors_record_number "$row" "$id" "$issues")
  [ -n "$number" ] || return 0
  sensors_comment "$number" "$finding" \
    "the check has no open condition; this measurement is kept on the row's record"
  printf '%s\n' "$number"
}

sensors_close() { # <row> <number> <finding> <body>
  local row="$1" number="$2" finding="$3" body="$4" id bodyfile
  id=$(sensors_row_field "$row" id)
  bodyfile=$(sensors_body_file "$body")
  sensors_write "set the closing counters on #$number" \
    bash "$SENSORS_ISSUES_DIR/update.sh" "$number" --body "$bodyfile"
  rm -f "$bodyfile"
  sensors_write "close #$number ($(jq -r '.measurement' <<< "$finding") clears the condition)" \
    bash "$SENSORS_ISSUES_DIR/close.sh" "$number" \
      --sensor "$id" \
      --reason "the $id condition cleared for $(sensors_row_field "$row" consecutive) consecutive runs" \
      --evidence "$(jq -r '.measurement' <<< "$finding") $(sensors_marker "$id" "$finding")"
}

sensors_reopen() { # <row> <number> <finding> <body>
  local row="$1" number="$2" finding="$3" body="$4" id bodyfile measurementfile status
  id=$(sensors_row_field "$row" id)
  status=$(sensors_registry_value '.labels.status')
  bodyfile=$(sensors_body_file "$body")
  measurementfile=$(mktemp) || sensors_die "run: cannot create a temporary file"
  printf '%s\n\n%s\n' "$(jq -r '.measurement' <<< "$finding")" "$(sensors_marker "$id" "$finding")" > "$measurementfile"
  sensors_write "reopen #$number (the condition returned)" \
    bash "$SENSORS_ISSUES_DIR/reopen.sh" "$number" --body "$bodyfile" --measurement-file "$measurementfile" \
      --status "$status"
  rm -f "$bodyfile" "$measurementfile"
}

sensors_escalate() { # <row> <number> <finding> <body> <occurrence>
  local row="$1" number="$2" finding="$3" body="$4" occurrence="$5" bodyfile commentfile labels=()
  while IFS= read -r label; do
    [ -n "$label" ] && labels+=("$label")
  done < <(sensors_escalation_labels)
  bodyfile=$(sensors_body_file "$body")
  commentfile=$(sensors_comment_file "$finding" \
    "escalated: the condition has recurred $occurrence times, so reopening stops here and a person decides (a fix that does not hold twice is usually a specification or design problem)")
  local args=("$number" --body "$bodyfile" --comment-file "$commentfile")
  for label in "${labels[@]}"; do
    args+=(--label "$label")
  done
  sensors_write "escalate #$number (${labels[*]})" \
    bash "$SENSORS_ISSUES_DIR/update.sh" "${args[@]}"
  rm -f "$bodyfile" "$commentfile"
}

# --------------------------------------------------------------------------- the three outcomes

# `error` posts nothing and fails the run: a check that crashed, could not read its input or printed
# something unreadable has determined nothing, and "nothing" must never read as "cleared" (design D5).
sensors_fail_row() { # <row-id> <detail>
  SENSORS_FAILED=$((SENSORS_FAILED + 1))
  sensors_report "$1" "-" "error" "$2" "-" "posted nothing, the run fails"
}

sensors_outcome_violation() { # <row> <id> <finding> <issues> <baseline>
  local row="$1" id="$2" finding="$3" issues="$4" baseline="$5"
  local fp number state state_reason body occurrence k run n
  fp=$(jq -r '.fingerprint' <<< "$finding")
  issue=$(sensors_issue_for_fingerprint "$issues" "$fp")
  if [ -z "$issue" ]; then
    sensors_file "$row" "$id" "$finding" "$baseline"
    sensors_report "$id" "$fp" violation "$(jq -r '.measurement' <<< "$finding")" "-" "filed"
    return 0
  fi
  number=$(jq -r '.number' <<< "$issue")
  state=$(jq -r '.state // "OPEN"' <<< "$issue")
  body=$(jq -r '.body // ""' <<< "$issue")
  if [ "$state" = "OPEN" ]; then
    run=$(sensors_counter "$body" violation-run); run=$((run + 1))
    body=$(sensors_body_set "$body" violation-run "$run")
    body=$(sensors_body_set "$body" clear-run 0)
    sensors_record "$row" "$number" "$finding" "$body" "the condition is still present"
    sensors_report "$id" "$fp" violation "$(jq -r '.measurement' <<< "$finding")" "$number" "annotated (still open)"
    return 0
  fi
  state_reason=$(jq -r '(.stateReason // "") | ascii_downcase' <<< "$issue")
  if [ "$state_reason" != "completed" ]; then
    # A person closed this as a decision not to act. The measurement is recorded, and the closure is
    # left alone: the tooling must not overrule a recorded owner decision (design D6).
    body=$(sensors_body_set "$body" violation-run "$(( $(sensors_counter "$body" violation-run) + 1 ))")
    sensors_record "$row" "$number" "$finding" "$body" \
      "the issue was closed as '${state_reason:-unknown}', not as a completed fix, so it is not reopened"
    sensors_report "$id" "$fp" violation "$(jq -r '.measurement' <<< "$finding")" "$number" "commented (closure is a person's decision)"
    return 0
  fi
  occurrence=$(issues_occurrence "$body")
  k=$(sensors_row_max_recurrences "$row")
  run=$(sensors_counter "$body" violation-run); run=$((run + 1))
  body=$(sensors_body_set "$body" violation-run "$run")
  body=$(sensors_body_set "$body" clear-run 0)
  if [ "$occurrence" -ge "$k" ]; then
    sensors_escalate "$row" "$number" "$finding" "$body" "$occurrence"
    sensors_report "$id" "$fp" violation "$(jq -r '.measurement' <<< "$finding")" "$number" \
      "escalated (past $k recurrences)"
    return 0
  fi
  n=$(sensors_row_field "$row" consecutive)
  if [ "$run" -ge "$n" ]; then
    sensors_reopen "$row" "$number" "$finding" "$body"
    sensors_report "$id" "$fp" violation "$(jq -r '.measurement' <<< "$finding")" "$number" "reopened"
  else
    sensors_record "$row" "$number" "$finding" "$body" \
      "reopening needs $n consecutive violations ($run so far)"
    sensors_report "$id" "$fp" violation "$(jq -r '.measurement' <<< "$finding")" "$number" "commented (hysteresis)"
  fi
}

sensors_outcome_clear() { # <row> <id> <finding> <issues>
  local row="$1" id="$2" finding="$3" issues="$4"
  local fp issue number state body run n record
  fp=$(jq -r '.fingerprint' <<< "$finding")
  issue=$(sensors_issue_for_fingerprint "$issues" "$fp")
  if [ -z "$issue" ]; then
    # No condition issue carries this identity: the clear still reaches the tracker, on the row's record
    # issue, so a row that ran and was green is not read as one that never ran (issue #257).
    record=$(sensors_record_clear "$row" "$id" "$finding" "$issues")
    sensors_report "$id" "$fp" clear "$(jq -r '.measurement' <<< "$finding")" "${record:--}" \
      "recorded on the row's record"
    return 0
  fi
  number=$(jq -r '.number' <<< "$issue")
  state=$(jq -r '.state // "OPEN"' <<< "$issue")
  if [ "$state" != "OPEN" ]; then
    # Nothing is left to close, but the run is still a measurement: it joins the row's own history.
    record=$(sensors_record_clear "$row" "$id" "$finding" "$issues")
    sensors_report "$id" "$fp" clear "$(jq -r '.measurement' <<< "$finding")" "${record:-$number}" \
      "recorded on the row's record (the condition issue is already closed)"
    return 0
  fi
  body=$(jq -r '.body // ""' <<< "$issue")
  run=$(sensors_counter "$body" clear-run); run=$((run + 1))
  body=$(sensors_body_set "$body" clear-run "$run")
  body=$(sensors_body_set "$body" violation-run 0)
  n=$(sensors_row_field "$row" consecutive)
  if [ "$run" -ge "$n" ]; then
    sensors_close "$row" "$number" "$finding" "$body"
    sensors_report "$id" "$fp" clear "$(jq -r '.measurement' <<< "$finding")" "$number" "closed"
  else
    sensors_record "$row" "$number" "$finding" "$body" \
      "closing needs $n consecutive clears ($run so far)"
    sensors_report "$id" "$fp" clear "$(jq -r '.measurement' <<< "$finding")" "$number" "commented (hysteresis)"
  fi
}

sensors_outcome() { # <row> <id> <finding> <issues> <baseline>
  local row="$1" id="$2" finding="$3" issues="$4" baseline="$5"
  SENSORS_CURRENT_ID="$id"
  case "$(jq -r '.verdict' <<< "$finding")" in
    violation) sensors_outcome_violation "$row" "$id" "$finding" "$issues" "$baseline" ;;
    clear) sensors_outcome_clear "$row" "$id" "$finding" "$issues" ;;
    error) sensors_fail_row "$id" "$(jq -r '.measurement' <<< "$finding")" ;;
  esac
}

sensors_run_row() { # <row>
  local row="$1" id cmd status out baseline issues line problems handled=0
  id=$(sensors_row_field "$row" id)
  problems=$(sensors_row_problems "$row")
  if [ -n "$problems" ]; then
    sensors_refuse "$SENSORS_REGISTRY_BAD" "row-incomplete" "$id: $(printf '%s' "$problems" | tr '\n' ';')"
  fi
  issues=$(sensors_issues_of_row "$id")
  # The baseline is the issue's own record, never a file beside it (design D8): the newest measurement
  # marker one of this row's issues carries.
  baseline=$(sensors_last_marker "$issues")
  baseline=$(sensors_marker_field "$baseline" measurement)
  SENSORS_BASELINE="$baseline"
  SENSORS_CONDITIONS=$(sensors_conditions_json "$issues")

  out=$(sensors_exec_cmd "$row" "$id")
  status=$?
  if [ "$status" -ne 0 ]; then
    sensors_fail_row "$id" "the command exited $status"
    return 0
  fi
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    if ! jq -e . <<< "$line" >/dev/null 2>&1; then
      sensors_fail_row "$id" "unreadable finding: ${line}"
      continue
    fi
    problems=$(sensors_finding_problems "$row" "$line")
    if [ -n "$problems" ]; then
      sensors_fail_row "$id" "malformed finding: $problems"
      continue
    fi
    handled=$((handled + 1))
    sensors_outcome "$row" "$id" "$line" "$issues" "$baseline"
  done <<< "$out"
  if [ "$handled" -eq 0 ]; then
    sensors_fail_row "$id" "the command printed no finding"
  fi
}

for id in "${selected[@]}"; do
  sensors_run_row "$(sensors_row "$id")" || exit $?
done

if [ "$SENSORS_FAILED" -gt 0 ]; then
  sensors_note "sensors: $SENSORS_FAILED finding(s) reported an error — nothing was written for them and the run fails"
  exit "$SENSORS_ERROR"
fi
exit "$SENSORS_OK"
