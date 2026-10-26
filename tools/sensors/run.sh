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
# `SENSORS_TRIGGER` is the trigger the calling pipeline names, when there is one — the sensor pipeline passes
# GitHub's own `event_name`. `schedule` is the unattended tick, and a row with no measurement on record is
# *reported* by it rather than spent: the run prints the count of findings it would have filed and writes
# nothing, so a new bound's blast radius is measured before it reaches the tracker (issue #667). Every other
# run — a dispatch, and a command run by hand, which names no trigger at all — is a run a person started, and
# its first exercise records the measurement later runs are compared against.
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
# The trigger that started this run, as the pipeline that ran it names it: `schedule` is the unattended tick,
# and an empty value is a run a person started (issue #667).
SENSORS_TRIGGER="${SENSORS_TRIGGER:-}"
# The window above is a count of minutes and it is the caller's to declare, so a value that is not one is
# refused before anything is read: narrowing it silently to the tick's own minute is what made a late tick a
# no-op (issue #166).
case "$within" in
  ''|*[!0-9]*|0) sensors_die "run: --within '$within' is not a count of minutes (a whole number, at least 1)" ;;
esac
SENSORS_INPUT="$input"
SENSORS_FAILED=0
SENSORS_CURRENT_ID=""
# Which row's run is its first exercise, and whether the run may spend it (issue #667).
SENSORS_FIRST_EXERCISE=0
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

sensors_file() { # <row> <id> <finding> <baseline> [<body>]
  local row="$1" id="$2" finding="$3" baseline="${4:-}" fp title file body labels code
  fp=$(jq -r '.fingerprint' <<< "$finding")
  title=$(jq -r '.title // empty' <<< "$finding")
  [ -n "$title" ] || title="${id}: $(jq -r '.measurement' <<< "$finding")"
  # The body is the caller's when it built one: a filing summary carries the list of what the run did not file, which
  # the generic finding shape has no room for (issue #666).
  body="${5:-}"
  [ -n "$body" ] || body=$(sensors_issue_body "$row" "$finding" "$baseline")
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
  code=$?
  rm -f "$file"
  return "$code"
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
  # The vocabulary, not only the emptiness: a state outside it would make `create.sh` refuse the filing, and
  # the run would go on reporting a measurement it never wrote — the silence-read-as-a-verdict class this
  # fix exists to close (issue #257). The guard refuses such a registry first; this is the direct-run half.
  if [ -z "$status" ]; then
    sensors_refuse "$SENSORS_REGISTRY_BAD" "registry-record-status-missing" \
      "the registry declares no .record.status, so a row's record would carry no state"
  fi
  if ! issues_core Policy labels --profile "$SENSORS_PROFILE" \
       "status${SENSORS_SEPARATOR}${status}" >/dev/null 2>&1; then
    sensors_refuse "$SENSORS_REGISTRY_BAD" "registry-record-status-outside-the-vocabulary" \
      "the registry's .record.status '$status' is not a declared status value, so a record would not be excluded from every work selector"
  fi
  fp="record-$id"
  title="sensor record: $id"
  claim="the $id check's measurement record"
  body=$(sensors_record_body "$id")
  labels=$(sensors_labels_json "$row" "$id" "$status")
  if [ "$SENSORS_DRY_RUN" -eq 1 ]; then
    # stderr, never stdout: the caller captures this function's stdout as the created issue's number
    # (`sensors_record_number`), and a dry run's message would become the number it reports.
    printf 'dry-run: would file the record issue for %s (fingerprint %s, status %s)\n' "$id" "$fp" "$status" >&2
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
    # What one run of this row may file is bounded (issue #666): an annotation, a reopen or a comment spends nothing,
    # because none of them makes the tracker grow — only the new item this branch would write does.
    if [ "$SENSORS_FILED" -ge "$SENSORS_MAX_FILINGS" ]; then
      SENSORS_UNFILED=$(jq -c --argjson f "$finding" '. + [$f]' <<< "$SENSORS_UNFILED")
      sensors_report "$id" "$fp" violation "$(jq -r '.measurement' <<< "$finding")" "-" \
        "not filed (past the row's bound of $SENSORS_MAX_FILINGS new items in one run)"
      return 0
    fi
    SENSORS_FILED=$((SENSORS_FILED + 1))
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

# --------------------------------------------------------------------------- the bound on one run's filings

# How many of the conditions a run did not file one item still lists: the count is the measurement the item carries,
# so the list may be bounded without the item lying — GitHub refuses a body past 65 536 characters and a run can hold
# any number of conditions (issue #666).
SENSORS_SUMMARY_LIST_MAX=20

# The identity of a row's filing summary: the engine's own, derived from the row alone, so one row owns at most one
# summary item however many conditions a run leaves unfiled — and no finding a check reports can carry it, because a
# finding's identity is the digest of the fields the row declares (issue #666).
sensors_summary_fingerprint() { # <row-id>
  sensors_fingerprint "$(jq -nc --arg s "$1" '{sensor: $s, kind: "filing-summary"}')" "kind sensor"
}

# The row's summary issue, by its own marker: it is found the way the row's record issue is, never by a fingerprint
# that a condition's could collide with.
sensors_summary_issue() { # <issues-json> <row-id> → the issue, or empty
  jq -c --arg marker "<!-- filing-summary: $2 -->" \
    '[.[]? | select((.body // "") | contains($marker))] | if length > 0 then .[0] else empty end' <<< "$1"
}

# The summary as the run's own finding: the count of what it did not file is its measurement, and its identity is
# the row's — so a second run of the same over-full row annotates one summary instead of filing another (#666).
sensors_summary_finding() { # <row-id> <unfiled-array>
  local id="$1" unfiled="$2" count noun fingerprint title claim
  count=$(jq 'length' <<< "$unfiled")
  noun="conditions"; [ "$count" -eq 1 ] && noun="condition"
  fingerprint=$(sensors_summary_fingerprint "$id")
  title="$id: $count $noun not filed (the row files at most $SENSORS_MAX_FILINGS condition items per run)"
  claim="$count of the conditions the check reported were not filed: one run of this check files at most $SENSORS_MAX_FILINGS condition items, and every condition it did not file is named here with what it measured"
  jq -nc --arg fingerprint "$fingerprint" --arg title "$title" --arg claim "$claim" \
        --argjson unfiled "$unfiled" --argjson count "$count" --arg bound "$SENSORS_MAX_FILINGS" \
    '{fingerprint: $fingerprint, verdict: "violation", measurement: ($count | tostring),
      title: $title, claim: $claim, bound: $bound, unfiled: $unfiled}'
}

# What the summary says: the bound, the count, and every condition the run did not file — in the order the check
# reported them, which is that check's own rank — each with the identity it is known by. The marker stands first, so
# the item's own record is read from it rather than from a listed condition's text.
sensors_summary_body() { # <row> <finding>
  local row="$1" finding="$2" count bound fields
  count=$(jq -r '.measurement' <<< "$finding")
  bound=$(jq -r '.bound' <<< "$finding")
  fields=$(sensors_finding_fields "$row")
  printf 'One run of this check files at most %s condition items, and this run found more: %s were not filed.\n\n' \
    "$bound" "$count"
  printf 'They are named below in the order the check reported them — its own rank — each with the identity it is\n'
  printf 'known by. Nothing is lost: widening this row'"'"'s `maxFilings` files them as their own items on the next\n'
  printf 'run.\n\n'
  printf '<!-- condition: {"sensor": "%s", "kind": "filing-summary"} -->\n\n' "$(sensors_row_field "$row" id)"
  printf '<!-- filing-summary: %s -->\n' "$(sensors_row_field "$row" id)"
  jq -r --argjson max "$SENSORS_SUMMARY_LIST_MAX" --arg fields "$fields" '
    .unfiled[:$max][] | . as $f
    | ($fields | split(" ") | map(select(length > 0)) | map("\(.)=\($f[.] // "")") | join(" ")) as $identity
    | "- \($identity) measured \($f.measurement), identity \($f.fingerprint)"' <<< "$finding"
  sensors_unfiled_rest "$count"
}

# The conditions a run did not file, as an annotation on a summary that already exists names them: the identity each
# will be filed under and the number it measured, in the order the check reported them. The list stops where the
# item's body does (issue #666).
sensors_unfiled_lines() { # <finding>
  jq -r --argjson max "$SENSORS_SUMMARY_LIST_MAX" \
    '.unfiled[:$max][] | "- identity \(.fingerprint) measured \(.measurement)"' <<< "$1"
  sensors_unfiled_rest "$(jq '.unfiled | length' <<< "$1")"
}

# The tail of a list too long for one item: the count is the measurement, the list is bounded like the filings are.
sensors_unfiled_rest() { # <count>
  local over=$(( ${1:-0} - SENSORS_SUMMARY_LIST_MAX ))
  [ "$over" -le 0 ] || printf -- '- … and %s more\n' "$over"
}

# The summary a run leaves behind, and the clearing of one: filed once per row when the bound left conditions over,
# annotated while it still does, reopened when a closure already exists, and closed by the first run that leaves
# nothing over — the count is the run's own integer, so there is nothing a consecutive count would protect here.
# A run that reported an error clears nothing: a check that determined nothing has no measurement about its own
# overflow either (`run#errorIsNeverTreatedAsClear`, issue #666).
sensors_summary_outcome() { # <row> <id> <issues> <failed-before>
  local row="$1" id="$2" issues="$3" failed_before="$4" count fp issue number finding body reason
  [ "$SENSORS_FAILED" -eq "$failed_before" ] || return 0
  count=$(jq 'length' <<< "$SENSORS_UNFILED")
  issue=$(sensors_summary_issue "$issues" "$id")
  number=""
  [ -n "$issue" ] && number=$(jq -r '.number // empty' <<< "$issue")
  if [ "$count" -eq 0 ]; then
    [ -n "$number" ] || return 0
    [ "$(jq -r '.state // "OPEN"' <<< "$issue")" = "OPEN" ] || return 0
    if ! sensors_close_summary "$row" "$number"; then
      sensors_fail_row "$id" "the tracker refused the summary's closure"
      return 0
    fi
    sensors_report "$id" "$(sensors_summary_fingerprint "$id")" clear 0 "$number" \
      "closed (this run left no condition unfiled)"
    return 0
  fi
  finding=$(sensors_summary_finding "$id" "$SENSORS_UNFILED")
  fp=$(sensors_summary_fingerprint "$id")
  if [ -z "$number" ]; then
    body=$(sensors_summary_body "$row" "$finding")
    if ! sensors_file "$row" "$id" "$finding" "" "$body"; then
      sensors_fail_row "$id" "the tracker refused the summary's filing"
      return 0
    fi
    sensors_report "$id" "$fp" violation "$count" "-" \
      "filed one summary for the $count condition(s) it did not file"
    return 0
  fi
  if [ "$(jq -r '.state // "OPEN"' <<< "$issue")" = "OPEN" ]; then
    # The identities are this run's, not the ones the filing named: a row that stays over its bound has new
    # conditions each time, and the comment is the only place they reach the tracker.
    if ! sensors_note_summary "$number" "$(sensors_unfiled_comment "$count" "" "$finding")"; then
      sensors_fail_row "$id" "the tracker refused the summary's annotation"
      return 0
    fi
    sensors_report "$id" "$fp" violation "$count" "$number" "annotated (still past the bound)"
    return 0
  fi
  # A person's closure is not a completed fix, so a returning overflow is annotated and the closure left
  # alone — the same rule the condition path follows (design D6), applied to the item this bound files. A
  # reopen here would be refused by `reopen.sh` while the report claimed one had happened.
  reason=$(jq -r '(.stateReason // "") | ascii_downcase' <<< "$issue")
  if [ "$reason" != "completed" ]; then
    if ! sensors_note_summary "$number" \
      "$(sensors_unfiled_comment "$count" "the issue was closed as '${reason:-unknown}', not as a completed fix, so it is not reopened" "$finding")"; then
      sensors_fail_row "$id" "the tracker refused the summary's annotation"
      return 0
    fi
    sensors_report "$id" "$fp" violation "$count" "$number" "commented (closure is a person's decision)"
    return 0
  fi
  if ! sensors_reopen_summary "$row" "$number" "$finding"; then
    sensors_fail_row "$id" "the tracker refused the summary's reopen"
    return 0
  fi
  sensors_report "$id" "$fp" violation "$count" "$number" "reopened (the row is past its bound again)"
}

# The comment a summary carries while the row stays over its bound, or after a closure the run will not reverse: the
# count this run did not file, why the closure is left alone, and the identities the run measured. Plain text, never
# a measurement marker: a marker on one of the row's items becomes that row's last measurement in the report, and for
# a `baseline-multiple` row the baseline the next bound is computed from — a count of unfiled conditions is no
# measurement of the row.
sensors_unfiled_comment() { # <count> <why-not-reopened> <finding>
  printf 'still past the bound of %s condition items per run: this run filed its bound and did not file %s condition(s).\n' \
    "$SENSORS_MAX_FILINGS" "$1"
  [ -z "$2" ] || printf '%s\n' "$2"
  printf '\n'
  sensors_unfiled_lines "$3"
}

# The one comment a summary gets: its body is the filing's, so a later run's report of what it did not file — and the
# reason a closure is left alone — reach the tracker here and nowhere else.
sensors_note_summary() { # <number> <text>
  local file code
  file=$(mktemp) || sensors_die "run: cannot create a temporary file"
  printf '%s\n' "$2" > "$file"
  sensors_write "comment on #$1 ($2)" \
    bash "$SENSORS_ISSUES_DIR/update.sh" "$1" --comment-file "$file"
  code=$?
  rm -f "$file"
  return "$code"
}

# A summary is closed by the run that leaves nothing over, naming what it filed against the bound.
sensors_close_summary() { # <row> <number>
  local row="$1" number="$2" id code
  id=$(sensors_row_field "$row" id)
  sensors_write "close #$number ($id left no condition unfiled)" \
    bash "$SENSORS_ISSUES_DIR/close.sh" "$number" \
      --sensor "$id" \
      --reason "the $id check filed every condition it reported, inside its bound of $SENSORS_MAX_FILINGS condition items per run" \
      --evidence "0 conditions were left unfiled by this run (the bound is $SENSORS_MAX_FILINGS condition items per run)"
  code=$?
  return "$code"
}

# A closure a returning overflow reopens, with plain text and no measurement marker, for the reason the annotation
# above states.
sensors_reopen_summary() { # <row> <number> <finding>
  local row="$1" number="$2" finding="$3" status file code
  status=$(sensors_registry_value '.labels.status')
  file=$(mktemp) || sensors_die "run: cannot create a temporary file"
  {
    printf '%s condition(s) were not filed by this run (the row files at most %s condition items per run):\n\n' \
      "$(jq -r '.measurement' <<< "$finding")" "$SENSORS_MAX_FILINGS"
    sensors_unfiled_lines "$finding"
  } > "$file"
  sensors_write "reopen #$number ($(sensors_row_field "$row" id) is past its bound again)" \
    bash "$SENSORS_ISSUES_DIR/reopen.sh" "$number" --measurement-file "$file" --status "$status"
  code=$?
  rm -f "$file"
  return "$code"
}

sensors_run_row() { # <row>
  local row="$1" id cmd status out baseline issues line problems handled=0 failed_before
  failed_before=$SENSORS_FAILED
  id=$(sensors_row_field "$row" id)
  problems=$(sensors_row_problems "$row")
  if [ -n "$problems" ]; then
    sensors_refuse "$SENSORS_REGISTRY_BAD" "row-incomplete" "$id: $(printf '%s' "$problems" | tr '\n' ';')"
  fi
  # The bound on this run's filings, and the direct-run half of its own check: the guard refuses such a registry
  # first, exactly as it does for the record's state (issue #666).
  SENSORS_MAX_FILINGS=$(sensors_row_max_filings "$row")
  case "$SENSORS_MAX_FILINGS" in
    ''|*[!0-9]*|0) sensors_refuse "$SENSORS_REGISTRY_BAD" "row-max-filings-not-a-count" \
      "$id: ${SENSORS_MAX_FILINGS:-no bound declared}" ;;
  esac
  SENSORS_FILED=0
  SENSORS_UNFILED='[]'
  SENSORS_FIRST_EXERCISE=0
  issues=$(sensors_issues_of_row "$id")
  # The baseline is the issue's own record, never a file beside it (design D8): the newest measurement
  # marker one of this row's issues carries.
  baseline=$(sensors_last_marker "$issues")
  baseline=$(sensors_marker_field "$baseline" measurement)
  SENSORS_BASELINE="$baseline"
  SENSORS_CONDITIONS=$(sensors_conditions_json "$issues")
  # A row with no measurement on record has never exercised its bound, so nothing it would file has been fitted
  # against the tracker yet — the state `report.sh` prints as `stale row=<id> no measurement on record`. The
  # unattended tick reports that first exercise instead of spending it, so a new bound's blast radius is a count in
  # the log before it is items on the tracker; a run a person started is the acknowledgement that records the first
  # measurement, and every run after it finds one (issue #667).
  if [ "$SENSORS_TRIGGER" = "schedule" ] && [ -z "$baseline" ]; then
    SENSORS_FIRST_EXERCISE=1
  fi

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
    if [ "$SENSORS_FIRST_EXERCISE" -eq 1 ]; then
      sensors_report "$id" "$(jq -r '.fingerprint' <<< "$line")" "$(jq -r '.verdict' <<< "$line")" \
        "$(jq -r '.measurement' <<< "$line")" "-" "not filed (first exercise: no measurement on record)"
      continue
    fi
    sensors_outcome "$row" "$id" "$line" "$issues" "$baseline"
  done <<< "$out"
  if [ "$handled" -eq 0 ]; then
    sensors_fail_row "$id" "the command printed no finding"
    return 0
  fi
  # The first exercise is the run's report about itself: it carries the count the tick would have filed and writes
  # nothing — not a condition, not the record issue, not a summary — so the number reaches a reader before the
  # tracker grows (issue #667).
  if [ "$SENSORS_FIRST_EXERCISE" -eq 1 ]; then
    sensors_note "first exercise: row=$id findings=$handled — no measurement on record, so this run wrote nothing (issue #667); a dispatch with inputs.apply=true records the first one"
    return 0
  fi
  # What the row reported and the run did not file leaves as one item, and a summary an earlier run left over is
  # closed by this run — so a mis-fitted bound costs the tracker one item rather than one per condition it held
  # (issue #666). A run in which any finding failed clears nothing: the count is a measurement of this row's own
  # output, and a row that could not determine anything has taken none.
  sensors_summary_outcome "$row" "$id" "$issues" "$failed_before"
}

for id in "${selected[@]}"; do
  sensors_run_row "$(sensors_row "$id")" || exit $?
done

if [ "$SENSORS_FAILED" -gt 0 ]; then
  sensors_note "sensors: $SENSORS_FAILED finding(s) reported an error — nothing was written for them and the run fails"
  exit "$SENSORS_ERROR"
fi
exit "$SENSORS_OK"
