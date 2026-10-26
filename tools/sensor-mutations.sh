#!/usr/bin/env bash
# The sensor change's falsification harness (`revert-check`): one mutation per assert, each applied to the
# shipped script, the named case run, the file restored, and the case run again. A mutation that leaves its
# case green is a case that is not evidence, so this script fails when that happens.
#
# It mutates the working tree, so it is a **change-time** step and never a CI step: the build pipeline lists
# the mutations (`--list`) and does not run them, exactly as it does for the issue tooling's and the loop's
# mutation harnesses.
#
# Usage: bash tools/sensor-mutations.sh --list          # one line per mutation: <task> <case> <what changes>
#        bash tools/sensor-mutations.sh [--only <task>] # run them all (or one), restoring after each
# Exit:  0 every mutation behaved, 1 one did not, 2 usage/environment.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root" || exit 2

only=""
case "${1:-}" in
  --list) mode=list ;;
  --only) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; mode=run; only="${2:-}" ;;
  ""|--run) mode=run ;;
  -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
  *) echo "sensor-mutations: unknown argument $1" >&2; exit 2 ;;
esac

# <task>@<file>@<sed-expression>@<case>@<what the mutation makes untrue> — `@` separates the fields because
# a sed expression uses `|` as its own delimiter.
MUTATIONS=(
  '8.1@tools/sensors/guard.sh@s/if \[ "$head_sha" != "$remote_sha" \]; then/if false; then/@guard#refusesOffMain@HEAD is the remote main branch'
  '8.2@.github/workflows/sensors.yml@s/^on:$/on:\n  pull_request:/@workflow#onlyTwoTriggers@the trigger cap — schedule and dispatch only'
  '8.3@tools/issues/close.sh@s/if \[ "$owned" != "true" \]; then/if false; then/@close#sensorScopeRefusesAForeignIssue@a check closes only what carries its own label'
  '8.4@tools/sensors/run.sh@s/error) sensors_fail_row "$id" "$(jq -r '"'"'.measurement'"'"' <<< "$finding")" ;;/error) sensors_outcome_clear "$row" "$id" "$finding" "$issues" ;;/@run#errorIsNeverTreatedAsClear@a failed check posts nothing and fails the run'
  '8.5@tools/sensors/run.sh@s/run=$(sensors_counter "$body" clear-run); run=$((run + 1))/run=$(sensors_counter "$body" clear-run); run=$((run + 2))/@run#hysteresisBothDirections@a close needs N consecutive clear runs'
  '8.6@tools/sensors/run.sh@s/if \[ "$occurrence" -ge "$k" \]; then/if false; then/@run#escalatesPastKRecurrences@past K recurrences a check stops reopening'
  '8.7@tools/issues/reopen.sh@s/if \[ "$state" != "CLOSED" \] || \[ "$state_reason" != "completed" \]; then/if [ "$state" != "CLOSED" ]; then/@reopen#refusesANotPlannedClosure@only a completed closure is reopened'
  '8.8@tools/issues/create.sh@s|fingerprint=$(jq -r '"'"'.fingerprint // empty'"'"' <<< "$finding")|fingerprint="$sensor_id"|@sensors#brokenMainFingerprintDistinguishesCauses@the condition fingerprint, not the check id, is the anchor'
  '8.9@tools/sensors/run.sh@s|cmd=$(sensors_row_field "$row" cmd)|cmd="tools/sensors/metrics/build-duration.sh"|@sensors#engineNamesNoProjectValue@every row'"'"'s command comes from the registry'
  '8.10@tools/sensors/run.sh@s/for id in \$rows_list; do/for id in; do/@run#rowsRefusesAnUndeclaredId@a row a step names must be a row the registry declares'
  '8.11@.github/workflows/sensors.yml@s/conclusion == /conclusion != /@workflow#aRedRecordRunsNoRecordRow@the record rows wait for a build that succeeded'
  '8.12@tools/sensors/lib.sh@s/ minutes="${3:-1}" back=0/ minutes=1 back=0/@run#aDelayedTickSelectsTheRowsItWasScheduledFor@the window reaches back over the minutes since the previous tick'
  '8.13@.github/workflows/sensors.yml@s/ --since / /g@workflow#theTickDeclaresItsWindow@every scheduled tick declares the interval it selects over'
  '8.14@tools/sensors/run.sh@s/sensors_die "run: --within/: "run: --within/@run#theWindowDoesNotSelectThePreviousTicksRow@a window that is not a count of minutes is refused'
  '8.15@tools/sensors/lib.sh@s|condition:\[ \]\*\\\\{.*\\\\}|condition:[ ]*[^>]*|@sensors#aRecordedConditionParsesToItsObject@the recorded condition parses to its object, not to the marker tail'
  '8.16@tools/sensors/guard.sh@s|\[ "$status" -eq "$SENSORS_UNREACHABLE" \]|[ "$status" -eq 0 ]|@guard#eachCheckHasItsOwnReason@the identity guard reports an unread tracker instead of turning it into a verdict (issue #231)'
  '8.17@tools/sensors/metrics/source-size.sh@s/sensors_compare "$lines" "$threshold" greater-than/sensors_compare "$lines" "$threshold" below-floor/@sensors#sourceSizeRow@a file past the row bound is a violation'
  '8.18@tools/sensors/run.sh@s/record=$(sensors_record_clear "$row" "$id" "$finding" "$issues")/record=""/@run#aClearWithNoIssueReachesTheRecord@a clear with no condition issue reaches the tracker on the row'"'"'s record (issue #257)'
  '8.19@tools/sensors/metrics/metadata-invariant.sh@s/"$fingerprint" "drifted" violation/"$fingerprint" "drifted" clear/@sensors#metadataDriftRow@a drifted priority pair is a condition, not a clear (issue #292)'
  '8.20@tools/sensors/lib.sh@s#+ \[ (\$issue.body // "") |#+ [ ("") |#@report#aFiledRowIsReported@the filing marker in the issue body is part of the row history (issue #668)'
  '8.21@tools/sensors/run.sh@s|baseline=$(sensors_last_marker "$issues")|baseline=""|@run#aFilingSetsTheBaseline@the baseline is the newest marker the row carries, a filing included (issue #668)'
)

if [ "$mode" = "list" ]; then
  for entry in "${MUTATIONS[@]}"; do
    IFS='@' read -r task file expr case_name what <<< "$entry"
    printf '%s %s — %s (%s)\n' "$task" "$case_name" "$what" "$file"
  done
  exit 0
fi

backup_dir=$(mktemp -d) || exit 2
trap 'rm -rf "$backup_dir"' EXIT

pass=0
fail=0
for entry in "${MUTATIONS[@]}"; do
  IFS='@' read -r task file expr case_name what <<< "$entry"
  if [ -n "$only" ] && [ "$only" != "$task" ]; then
    continue
  fi
  backup="$backup_dir/$(printf '%s' "$file" | tr '/' '_')"
  cp "$file" "$backup"
  if ! sed -i "$expr" "$file"; then
    printf 'MUTATION %s: could not apply the mutation to %s\n' "$task" "$file" >&2
    cp "$backup" "$file"
    fail=$((fail + 1))
    continue
  fi
  if cmp -s "$backup" "$file"; then
    printf 'MUTATION %s: the mutation changed nothing — the assert would be vacuous\n' "$task" >&2
    cp "$backup" "$file"
    fail=$((fail + 1))
    continue
  fi
  if bash tools/sensor-selftest.sh --case "$case_name" >/dev/null 2>&1; then
    printf 'MUTATION %s: %s stayed green under the mutation (%s)\n' "$task" "$case_name" "$what" >&2
    cp "$backup" "$file"
    fail=$((fail + 1))
    continue
  fi
  cp "$backup" "$file"
  if ! bash tools/sensor-selftest.sh --case "$case_name" >/dev/null 2>&1; then
    printf 'MUTATION %s: %s is red after the restore\n' "$task" "$case_name" >&2
    fail=$((fail + 1))
    continue
  fi
  printf 'MUTATION %s: %s red under the mutation, green after the restore — %s\n' "$task" "$case_name" "$what"
  pass=$((pass + 1))
done

printf '\nsensor-mutations: %d behaved, %d did not\n' "$pass" "$fail"
[ "$fail" -eq 0 ] || exit 1
exit 0
