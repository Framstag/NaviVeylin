#!/usr/bin/env bash
# The sensor tooling's shell cases: every case drives the real scripts against the fixture registry, the
# fixture sensors and the stub `gh` on PATH, so the whole lifecycle is exercised with no token and no
# network (design D9, D10).
#
# Sourced by `tools/sensor-selftest.sh`, which provides the environment:
#   SENSORS_REPO_ROOT  the repository under test     SENSORS_ENGINE        tools/sensors
#   SENSORS_FIXTURES   tools/sensors/fixtures        SENSORS_ISSUES_ENGINE tools/issues
#   SENSORS_TMP        a scratch directory           ISSUES_CORE_CLASSES   the compiled Java core
# and puts the stub `gh` first on PATH.
#
# A case runs in a subshell; it prints nothing on success and a reason on failure, exiting non-zero.
set -uo pipefail

SENSOR_CASES=(
  'guard#eachCheckHasItsOwnReason'
  'guard#refusesOffMain'
  'guard#refusesARowWithoutThresholdOrConsecutive'
  'sensors#usesTheReservedNamespacesAndAddsNone'
  'Registry#everyRowIsComplete'
  'lib#rejectsAMalformedFinding'
  'run#selectsOnlyDueRows'
  'run#dryRunTouchesNothing'
  'run#errorPostsNothingAndFails'
  'run#errorIsNeverTreatedAsClear'
  'run#noBaselineFileWritten'
  'report#printsACountingLinePerRow'
  'update#commentPostsExactlyOnce'
  'update#commentRefusesOnPolicyFailure'
  'close#sensorScopeRefusesAForeignIssue'
  'close#sensorScopeAllowsItsOwnIssue'
  'reopen#refusesANotPlannedClosure'
  'reopen#neverWritesEligibility'
  'run#dispatchMatrix'
  'run#dispatchMatrixNotPlannedClosure'
  'create#sensorFingerprintIsTheAnchor'
  'run#hysteresisBothDirections'
  'run#hysteresisCountersSurviveARestart'
  'run#escalatesPastKRecurrences'
  'run#escalationNeverSetsEligibility'
  'sensors#buildDurationRow'
  'hotspot#ranksBySizeTimesChanges'
  'sensors#packageSizeConsumesAnArtifact'
  'sensors#coverageRow'
  'sensors#brokenMainFingerprintDistinguishesCauses'
  'sensors#prConflictsOnlyOwnPRs'
  'workflow#onlyTwoTriggers'
  'sensors#engineNamesNoProjectValue'
)

sensor_cases() {
  printf '%s\n' "${SENSOR_CASES[@]}"
}

sensor_run_case() {
  case "$1" in
    'guard#eachCheckHasItsOwnReason') case_guard_eachCheckHasItsOwnReason ;;
    'guard#refusesOffMain') case_guard_refusesOffMain ;;
    'guard#refusesARowWithoutThresholdOrConsecutive') case_guard_refusesARowWithoutThresholdOrConsecutive ;;
    'sensors#usesTheReservedNamespacesAndAddsNone') case_sensors_usesTheReservedNamespacesAndAddsNone ;;
    'Registry#everyRowIsComplete') case_registry_everyRowIsComplete ;;
    'lib#rejectsAMalformedFinding') case_lib_rejectsAMalformedFinding ;;
    'run#selectsOnlyDueRows') case_run_selectsOnlyDueRows ;;
    'run#dryRunTouchesNothing') case_run_dryRunTouchesNothing ;;
    'run#errorPostsNothingAndFails') case_run_errorPostsNothingAndFails ;;
    'run#errorIsNeverTreatedAsClear') case_run_errorIsNeverTreatedAsClear ;;
    'run#noBaselineFileWritten') case_run_noBaselineFileWritten ;;
    'report#printsACountingLinePerRow') case_report_printsACountingLinePerRow ;;
    'update#commentPostsExactlyOnce') case_update_commentPostsExactlyOnce ;;
    'update#commentRefusesOnPolicyFailure') case_update_commentRefusesOnPolicyFailure ;;
    'close#sensorScopeRefusesAForeignIssue') case_close_sensorScopeRefusesAForeignIssue ;;
    'close#sensorScopeAllowsItsOwnIssue') case_close_sensorScopeAllowsItsOwnIssue ;;
    'reopen#refusesANotPlannedClosure') case_reopen_refusesANotPlannedClosure ;;
    'reopen#neverWritesEligibility') case_reopen_neverWritesEligibility ;;
    'run#dispatchMatrix') case_run_dispatchMatrix ;;
    'run#dispatchMatrixNotPlannedClosure') case_run_dispatchMatrixNotPlannedClosure ;;
    'create#sensorFingerprintIsTheAnchor') case_create_sensorFingerprintIsTheAnchor ;;
    'run#hysteresisBothDirections') case_run_hysteresisBothDirections ;;
    'run#hysteresisCountersSurviveARestart') case_run_hysteresisCountersSurviveARestart ;;
    'run#escalatesPastKRecurrences') case_run_escalatesPastKRecurrences ;;
    'run#escalationNeverSetsEligibility') case_run_escalationNeverSetsEligibility ;;
    'sensors#buildDurationRow') case_sensors_buildDurationRow ;;
    'hotspot#ranksBySizeTimesChanges') case_hotspot_ranksBySizeTimesChanges ;;
    'sensors#packageSizeConsumesAnArtifact') case_sensors_packageSizeConsumesAnArtifact ;;
    'sensors#coverageRow') case_sensors_coverageRow ;;
    'sensors#brokenMainFingerprintDistinguishesCauses') case_sensors_brokenMainFingerprintDistinguishesCauses ;;
    'sensors#prConflictsOnlyOwnPRs') case_sensors_prConflictsOnlyOwnPRs ;;
    'workflow#onlyTwoTriggers') case_workflow_onlyTwoTriggers ;;
    'sensors#engineNamesNoProjectValue') case_sensors_engineNamesNoProjectValue ;;
    *) printf 'no such shell case: %s\n' "$1" >&2; exit 2 ;;
  esac
}

# --------------------------------------------------------------------------- helpers

fail() {
  printf '  %s\n' "$*" >&2
  exit 1
}

assert_eq() {
  [ "$1" = "$2" ] || fail "$3: expected '$1', got '$2'"
}

assert_contains() {
  case "$1" in
    *"$2"*) ;;
    *) fail "$3: '$2' not found in: $1" ;;
  esac
}

assert_not_contains() {
  case "$1" in
    *"$2"*) fail "$3: '$2' unexpectedly present in: $1" ;;
    *) ;;
  esac
}

assert_empty() {
  [ -z "$1" ] || fail "$2: expected nothing, got: $1"
}

assert_absent() { # <file>
  [ ! -e "$1" ] || fail "expected $1 not to exist"
}

scratch() { mktemp -d "$SENSORS_TMP/case.XXXXXX"; }

fixture() { printf '%s\n' "$SENSORS_FIXTURES/$1"; }

sensor_engine() { # <script> [args…] — the engine under test
  local script="$1"; shift
  bash "$SENSORS_ENGINE/$script" "$@"
}

issue_engine() { # <script> [args…] — the issue tooling the engine writes through
  local script="$1"; shift
  bash "$SENSORS_ISSUES_ENGINE/$script" "$@"
}

core_class() { # <Class> [args…] — the core the driver already compiled
  local class="$1"; shift
  java -cp "$ISSUES_CORE_CLASSES" "$class" "$@"
}

# The engine's own digest, so a fixture finding and the engine agree on an identity by construction.
fingerprint_of() {
  local json="{}" names
  while [ $# -ge 2 ]; do
    json=$(jq -c --arg k "$1" --arg v "$2" '. + {($k): $v}' <<< "$json")
    shift 2
  done
  names=$(jq -r 'keys[]' <<< "$json" | tr '\n' ' ')
  (
    # shellcheck source=tools/sensors/lib.sh
    source "$SENSORS_ENGINE/lib.sh"
    sensors_fingerprint "$json" "$names"
  )
}

# One finding in the contract's shape.
finding() { # <verdict> <measurement> <field> <value> [<field> <value>]…
  local verdict="$1" measurement="$2"
  shift 2
  local json="{}" names fingerprint
  while [ $# -ge 2 ]; do
    json=$(jq -c --arg k "$1" --arg v "$2" '. + {($k): $v}' <<< "$json")
    shift 2
  done
  names=$(jq -r 'keys[]' <<< "$json" | tr '\n' ' ')
  fingerprint=$(
    # shellcheck source=tools/sensors/lib.sh
    source "$SENSORS_ENGINE/lib.sh"
    sensors_fingerprint "$json" "$names"
  )
  jq -nc --argjson fields "$json" --arg fingerprint "$fingerprint" --arg verdict "$verdict" \
         --arg measurement "$measurement" \
    '$fields + {fingerprint: $fingerprint, verdict: $verdict, measurement: $measurement}'
}

# A scenario directory for the stub `gh`: the files a case changes, with everything else falling back to
# the issue tooling's base fixtures (an allowlisted login among them).
scenario() { # <name> → the path, created empty
  local dir="$SENSORS_FIXTURES/scenarios/$1"
  mkdir -p "$dir"
  printf '%s\n' "$dir"
}

issues_fixture() { # <scenario-dir> <issues-json>
  printf '%s' "$2" > "$1/issues.json"
}

issue_record() { # <number> <state> <stateReason> <sensor-id> <body> [comments-json]
  local comments="${6:-[]}"
  jq -nc --argjson number "$1" --arg state "$2" --arg reason "$3" --arg label "sensor/$4" \
         --arg body "$5" --argjson comments "$comments" \
    '{number: $number, title: "a fixture issue", state: $state, stateReason: $reason,
      body: $body, author: {login: "Framstag"},
      labels: [{name: "class/bug"}, {name: "category/build-and-harness"}, {name: $label}],
      comments: $comments}'
}

comment_record() { # <createdAt> <body>
  jq -nc --arg createdAt "$1" --arg body "$2" '{createdAt: $createdAt, body: $body}'
}

# The measurement marker the engine posts, as a fixture comment.
marker_comment() { # <createdAt> <sensor-id> <fingerprint> <verdict> <measurement>
  comment_record "$1" "<!-- sensor: $2 fingerprint=$3 verdict=$4 measurement=$5 baseline= -->"
}

# A throwaway project: the profile, the fixture registry and the fixture sensors, committed, with an
# `origin/main` that points at HEAD — the state the guard requires.
throwaway_project() { # [<registry-file>] → the path
  local registry="${1:-$(fixture registry.json)}" dir
  dir=$(scratch)
  mkdir -p "$dir/tools/sensors/fixtures/sensors"
  cp "$SENSORS_REPO_ROOT/tools/tooling.json" "$dir/tools/tooling.json"
  cp "$registry" "$dir/tools/sensors/sensors.json"
  cp "$(fixture sensors/emit.sh)" "$dir/tools/sensors/fixtures/sensors/emit.sh"
  cp "$(fixture sensors/error.sh)" "$dir/tools/sensors/fixtures/sensors/error.sh"
  chmod +x "$dir/tools/sensors/fixtures/sensors/"*.sh
  git -C "$dir" init -q -b main >/dev/null 2>&1 || fail "could not create the throwaway repository"
  git -C "$dir" add -A >/dev/null 2>&1 || fail "could not stage the throwaway repository"
  git -C "$dir" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm init >/dev/null 2>&1 ||
    fail "could not record the throwaway repository's first state"
  git -C "$dir" update-ref refs/remotes/origin/main HEAD
  git -C "$dir" symbolic-ref refs/remotes/origin/HEAD refs/remotes/origin/main
  printf '%s\n' "$dir"
}

git_commit() { # <dir> <message>
  git -C "$1" add -A >/dev/null 2>&1
  git -C "$1" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm "$2" >/dev/null 2>&1
}

write_findings() { # <file> <finding…>
  local file="$1"; shift
  : > "$file"
  for line in "$@"; do
    printf '%s\n' "$line" >> "$file"
  done
  printf '%s\n' "$file"
}

# The number of recorded `gh` writes matching a pattern.
recorded() { # <log> <pattern>
  local count
  if [ ! -f "$1" ]; then
    printf '0\n'
    return 0
  fi
  count=$(grep -c -- "$2" "$1" 2>/dev/null)
  printf '%s\n' "${count:-0}"
}

# The log's text without the shell quoting the stub applies when it records a write, so an assertion can
# name what a call carried without re-implementing `printf %q`'s choice of escaping.
log_text() { # <log>
  [ -f "$1" ] || return 0
  tr -d '\\' < "$1"
}

# The body a run last wrote: the stub `gh` keeps every `--body-file` it saw beside the log, because the
# writer's own temporary file is gone by the time a case inspects it.
last_body_content() { # <log>
  local dir="$1.bodies" latest
  [ -d "$dir" ] || return 0
  latest=$(ls "$dir" 2>/dev/null | sort -n | tail -1)
  [ -n "$latest" ] && cat "$dir/$latest"
}

# ----------------------------------------------------------------------------- 1. the guards

case_guard_eachCheckHasItsOwnReason() {
  local dir out status
  dir=$(throwaway_project)

  out=$( (cd "$dir" && sensor_engine guard.sh) 2>&1 ); status=$?
  assert_eq "0" "$status" "a clean checkout on main passes every guard: $out"
  assert_contains "$out" "guard: on-main" "the on-main line is printed"
  assert_contains "$out" "guard: registry" "the registry line is printed"

  # 1. not on the project's main branch.
  git -C "$dir" checkout -q -b feature
  out=$( (cd "$dir" && sensor_engine guard.sh) 2>&1 ); status=$?
  assert_eq "4" "$status" "the off-main guard's own exit code"
  assert_contains "$out" "off-main" "the off-main reason"

  # 2. HEAD is not the remote main branch's commit — named by both commits.
  git -C "$dir" checkout -q main
  printf 'local work\n' >> "$dir/tools/tooling.json"
  git_commit "$dir" "local work"
  out=$( (cd "$dir" && sensor_engine guard.sh) 2>&1 ); status=$?
  assert_eq "5" "$status" "the ahead-of-origin guard's own exit code"
  assert_contains "$out" "head-is-not-origin" "the ahead-of-origin reason"
  assert_contains "$out" "$(git -C "$dir" rev-parse HEAD)" "the run's own commit is named"
  assert_contains "$out" "$(git -C "$dir" rev-parse refs/remotes/origin/main)" "the remote main commit is named"
  git -C "$dir" reset -q --hard refs/remotes/origin/main

  # 3. a dirty tree.
  printf 'uncommitted\n' > "$dir/stray.txt"
  out=$( (cd "$dir" && sensor_engine guard.sh) 2>&1 ); status=$?
  assert_eq "6" "$status" "the clean-tree guard's own exit code"
  assert_contains "$out" "dirty-tree" "the dirty-tree reason"
  rm -f "$dir/stray.txt"

  # 4. an identity outside the allowlist.
  local outsider
  outsider=$(scratch); printf 'outsider\n' > "$outsider/login.txt"
  out=$( (cd "$dir" && GH_STUB_DIR="$outsider" sensor_engine guard.sh) 2>&1 ); status=$?
  assert_eq "7" "$status" "the identity guard's own exit code"
  assert_contains "$out" "author-not-allowlisted" "the identity reason"

  # 5. an incomplete row.
  out=$( (cd "$dir" && sensor_engine guard.sh --registry "$(fixture registry-no-threshold.json)") 2>&1 ); status=$?
  assert_eq "9" "$status" "the registry guard's own exit code"
  assert_contains "$out" "row-missing-field: threshold" "the registry reason names the missing field"
}

case_guard_refusesOffMain() {
  local dir out status
  dir=$(throwaway_project)

  # A dispatch can select any ref, so the guard reads the ref and refuses before any write.
  out=$( (cd "$dir" && sensor_engine guard.sh --ref refs/heads/feature) 2>&1 ); status=$?
  assert_eq "4" "$status" "a dispatch against a branch is refused"
  assert_contains "$out" "refs/heads/feature" "the refused ref is named"
  assert_contains "$out" "refs/heads/main" "the expected ref is named"

  # A run whose HEAD is one commit ahead of origin/main cannot reach a write either.
  printf 'local\n' > "$dir/local.txt"
  git_commit "$dir" "local commit"
  out=$( (cd "$dir" && sensor_engine guard.sh) 2>&1 ); status=$?
  assert_eq "5" "$status" "a HEAD that is not origin/main is refused"
  assert_contains "$out" "$(git -C "$dir" rev-parse --short=12 HEAD)" "the run's commit is named"
  assert_contains "$out" "$(git -C "$dir" rev-parse --short=12 refs/remotes/origin/main)" "the remote commit is named"
}

case_guard_refusesARowWithoutThresholdOrConsecutive() {
  local dir out status
  dir=$(throwaway_project "$(fixture registry-no-threshold.json)")
  out=$( (cd "$dir" && sensor_engine guard.sh) 2>&1 ); status=$?
  assert_eq "9" "$status" "a row without a bound is refused"
  assert_contains "$out" "row-missing-field: threshold" "the missing bound is named"

  dir=$(throwaway_project "$(fixture registry-no-consecutive.json)")
  out=$( (cd "$dir" && sensor_engine guard.sh) 2>&1 ); status=$?
  assert_eq "9" "$status" "a row without a consecutive count is refused"
  assert_contains "$out" "row-missing-field: consecutive" "the missing count is named"
}

case_sensors_usesTheReservedNamespacesAndAddsNone() {
  local profile out status repo
  profile="$SENSORS_REPO_ROOT/tools/tooling.json"

  # The namespaces this change reads are the profile's: the source the checks file under, and the
  # namespace their own labels live in.
  assert_eq "true" "$(jq -r '.namespaces.source | index("ci") != null' "$profile")" "source/ci is declared"
  assert_eq "true" "$(jq -r '.namespaces | has("sensor")' "$profile")" "the sensor namespace is declared"
  # …and it adds no *value* to it: the registry is the one owner of this namespace's values, so a project
  # without that registry has no sensor labels rather than a second list to keep in step.
  assert_eq "0" "$(jq -r '.namespaces.sensor | length' "$profile")" "the profile keeps the namespace empty"
  assert_eq "sensor" "$(jq -r '.namespace' "$SENSORS_ENGINE/sensors.json")" "the registry names the namespace"

  # A label the registry does not declare is refused, never created.
  repo=$(scratch)
  out=$(printf '%s' "$(jq -nc '{title:"t",class:"bug",category:"build-and-harness",claim:"c",body:"b"}')" |
    (cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$SENSORS_FIXTURES" issue_engine create.sh --sensor fixture-nothing) 2>&1)
  status=$?
  assert_eq "3" "$status" "an undeclared check id is refused"
  assert_contains "$out" "label-opaque" "the refusal reason"
}

# ----------------------------------------------------------------------------- 2. the registry

case_registry_everyRowIsComplete() {
  local out
  out=$(cd "$SENSORS_REPO_ROOT" && jq -e . "$SENSORS_ENGINE/sensors.json" >/dev/null 2>&1; echo $?)
  assert_eq "0" "$out" "the registry is valid JSON"

  out=$(cd "$SENSORS_REPO_ROOT" && bash -c '
    # shellcheck source=tools/sensors/lib.sh
    source "$SENSORS_ENGINE/lib.sh"
    sensors_resolve
    sensors_registry_problems' 2>&1)
  assert_empty "$out" "every row of the shipped registry is complete"

  # The check is load-bearing: a row with no bound is a problem, not a reporter.
  out=$(cd "$(throwaway_project "$(fixture registry-no-threshold.json)")" && bash -c '
    source "$SENSORS_ENGINE/lib.sh"
    sensors_resolve
    sensors_registry_problems' 2>&1)
  assert_contains "$out" "threshold" "an incomplete row is reported by name"
}

case_lib_rejectsAMalformedFinding() {
  local dir findings log out status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  issues_fixture "$dir" '[]'
  findings=$(write_findings "$dir/findings.jsonl" \
    '{"measurement":"x","verdict":"violation"}' \
    "$(finding weird 7 case alpha | jq -c 'del(.fingerprint) + {fingerprint: ""}')")

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" \
    --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "8" "$status" "a malformed finding fails the run"
  assert_contains "$out" "fingerprint(missing)" "the missing fingerprint is named"
  assert_contains "$out" "verdict=weird" "the verdict outside the three is named"
  assert_eq "0" "$(recorded "$log" 'gh issue')" "nothing is filed for a malformed finding"
}

case_run_selectsOnlyDueRows() {
  local dir findings out
  dir=$(scratch); issues_fixture "$dir" '[]'
  findings=$(write_findings "$dir/findings.jsonl" "$(finding clear 1 case alpha)")

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine run.sh --due --now 2026-10-17T03:00:00Z --input "$findings" \
    --registry "$(fixture registry.json)" 2>&1)
  assert_contains "$out" "fixture-emit" "the row whose cron matches 03:00 is selected"
  assert_not_contains "$out" "fixture-error" "the row whose cron does not match is not selected"
  # and no row whose command exits non-zero can have run: that row is the one the second clock selects
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine run.sh --due --now 2026-10-17T04:00:00Z --input "$findings" \
    --registry "$(fixture registry.json)" 2>&1)
  assert_contains "$out" "fixture-error" "the second clock selects the second row"
  assert_not_contains "$out" "fixture-emit" "the first row is not selected by the second clock"
}

case_run_dryRunTouchesNothing() {
  local dir findings log out status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  issues_fixture "$dir" '[]'
  findings=$(write_findings "$dir/findings.jsonl" "$(finding violation 9 case alpha)")

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "0" "$status" "a dry run succeeds"
  assert_contains "$out" "dry-run: would file" "the intended write is printed"
  assert_eq "0" "$(recorded "$log" 'gh issue')" "a dry run writes nothing"
}

case_run_errorPostsNothingAndFails() {
  local dir findings log out status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  issues_fixture "$dir" '[]'
  findings=$(write_findings "$dir/findings.jsonl" "$(finding error unreachable case alpha)")

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" \
    --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "8" "$status" "an error verdict fails the run"
  assert_contains "$out" "verdict=error" "the row's failure is reported"
  assert_eq "0" "$(recorded "$log" 'issue comment')" "nothing is commented"
  assert_eq "0" "$(recorded "$log" 'issue create')" "nothing is filed"
  assert_eq "0" "$(recorded "$log" 'issue close')" "nothing is closed"
}

case_run_errorIsNeverTreatedAsClear() {
  local dir log out status body
  dir=$(scratch); log="$dir/calls.log"; : > "$log"

  body="$(printf 'a fixture issue\n<!-- fingerprint: %s -->' "$(fingerprint_of case alpha)")"
  issues_fixture "$dir" "[$(issue_record 21 OPEN "" fixture-emit "$body")]"

  # An error: nothing is posted, and the run fails.
  local findings error_log
  error_log="$dir/error.log"; : > "$error_log"
  findings=$(write_findings "$dir/error.jsonl" "$(finding error unreachable case alpha)")
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$error_log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "8" "$status" "the error verdict fails the run"
  assert_contains "$out" "verdict=error" "the error is reported"
  assert_empty "$(cat "$error_log")" "an error writes nothing at all"

  # The same row, cleared: the outcomes differ, which is the whole reason the third verdict exists.
  findings=$(write_findings "$dir/clear.jsonl" "$(finding clear 1 case alpha)")
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "0" "$status" "a clear verdict does not fail the run"
  assert_contains "$out" "verdict=clear" "the clear verdict is reported"
  [ "$(recorded "$log" 'gh issue')" -gt 0 ] || fail "a clear verdict records the measurement on the issue"
  assert_not_contains "$out" "verdict=error" "the two outcomes are distinguishable in the report"
}

case_run_noBaselineFileWritten() {
  local dir findings log out status dirty hits
  dir=$(throwaway_project)
  log="$dir/calls.log"; : > "$log"
  local input_dir
  input_dir=$(scratch)   # the case's own input, outside the repository under test
  findings=$(write_findings "$input_dir/findings.jsonl" "$(finding violation 9 case alpha)")
  issues_fixture "$dir" '[]'

  out=$(cd "$dir" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    bash "$SENSORS_ENGINE/run.sh" --only fixture-emit --apply --input "$findings" 2>&1)
  status=$?
  assert_eq "0" "$status" "the apply run succeeds: $out"

  dirty=$(git -C "$dir" status --porcelain)
  # the findings fixture is the case's, so the run's own footprint is what is asserted: nothing new in the
  # work tree, no baseline, no state file
  assert_not_contains "$dirty" "baseline" "no baseline file is written"
  assert_not_contains "$dirty" "metrics" "no metrics file is written"

  hits=$(grep -rn 'git commit\|git add' "$SENSORS_ENGINE" 2>/dev/null || true)
  assert_empty "$hits" "the engine never records a change of its own"
  hits=$(grep -rln 'baseline\.json\|baseline\.tsv\|\.pi/' "$SENSORS_ENGINE" 2>/dev/null || true)
  assert_empty "$hits" "the engine never names a side file"
}

case_report_printsACountingLinePerRow() {
  local dir out lines
  dir=$(scratch)
  local alpha_fp
  alpha_fp=$(fingerprint_of case alpha)
  issues_fixture "$dir" "$(jq -nc \
    --argjson issue "$(issue_record 31 OPEN "" fixture-emit \
      "$(printf 'a fixture issue\n<!-- fingerprint: %s -->' "$alpha_fp")" \
      "[$(marker_comment 2026-10-16T03:00:00Z fixture-emit "$alpha_fp" violation 9),
        $(marker_comment 2026-10-17T03:00:00Z fixture-emit "$alpha_fp" violation 12)]")" \
    '[$issue]')"

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine report.sh --registry "$(fixture registry.json)" --now 2026-10-17T04:00:00Z 2>&1)
  lines=$(printf '%s\n' "$out" | grep -c '^row=')
  [ "$lines" = "2" ] || fail "one counting line per registry row: got $lines from: $out"
  assert_contains "$out" "row=fixture-emit last=2026-10-17T03:00:00Z verdict=violation measurement=12 issue=#31 clear=0 violation=0 occurrences=1 trend=worse" \
    "the seven fields, with the trend against the issue's own previous number"
  assert_contains "$out" "row=fixture-error last=never" "a row with no measurement says so"
  assert_contains "$out" "stale row=fixture-error" "a row that has not fired is flagged"
}

# ----------------------------------------------------------------------------- 3. the three verbs

case_update_commentPostsExactlyOnce() {
  local dir log out status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  issues_fixture "$dir" "[$(issue_record 41 OPEN "" coverage 'a fixture body')]"
  printf '%s\n' 'the measurement, as the run recorded it' > "$dir/comment.md"

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    issue_engine update.sh 41 --comment-file "$dir/comment.md" 2>&1)
  status=$?
  assert_eq "0" "$status" "the comment path succeeds: $out"
  assert_eq "1" "$(recorded "$log" 'issue comment')" "exactly one comment is posted"
  assert_contains "$(cat "$log")" "$(printf '%q' 'the measurement, as the run recorded it')" "the file's body is the comment"
  assert_eq "0" "$(recorded "$log" 'issue create')" "nothing is filed"
  assert_eq "0" "$(recorded "$log" 'issue close')" "nothing is closed"
}

case_update_commentRefusesOnPolicyFailure() {
  local dir log out status outsider
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  issues_fixture "$dir" "[$(issue_record 41 OPEN "" coverage 'a fixture body')]"
  printf '%s\n' 'the measurement' > "$dir/comment.md"
  outsider=$(scratch); printf 'outsider\n' > "$outsider/login.txt"

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$outsider" GH_STUB_RECORD="$log" \
    issue_engine update.sh 41 --comment-file "$dir/comment.md" 2>&1)
  status=$?
  assert_eq "3" "$status" "a run outside the allowlist is refused"
  assert_contains "$out" "author-not-allowlisted" "the refusal reason"
  assert_eq "0" "$(recorded "$log" 'gh issue')" "nothing is posted"
}

case_close_sensorScopeRefusesAForeignIssue() {
  local dir log out status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  # the issue belongs to another check
  issues_fixture "$dir" "[$(jq -nc --argjson issue "$(issue_record 51 OPEN "" broken-main 'body')" \
    '$issue | .labels = [{name: "class/bug"}, {name: "category/build-and-harness"}, {name: "sensor/broken-main"}]')]"

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    issue_engine close.sh 51 --reason "the condition cleared" --evidence "1" --sensor coverage 2>&1)
  status=$?
  assert_eq "3" "$status" "a check may not close another check's issue"
  assert_contains "$out" "sensor-scope" "the refusal names the rule"
  assert_contains "$out" "sensor/coverage" "the refusal names the label it expected"
  assert_eq "0" "$(recorded "$log" 'gh issue')" "nothing is written"

  # An issue with no ownership label at all — a person's, or one imported from the previous backlog — is
  # outside every check's reach for the same reason.
  issues_fixture "$dir" "[$(jq -nc --argjson issue "$(issue_record 53 OPEN "" human 'body')" \
    '$issue | .labels = [{name: "class/bug"}, {name: "category/build-and-harness"}, {name: "source/human"}]')]"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    issue_engine close.sh 53 --reason "the condition cleared" --evidence "1" --sensor coverage 2>&1)
  status=$?
  assert_eq "3" "$status" "a person's issue is out of reach"
  assert_contains "$out" "sensor-scope" "the refusal names the rule there too"
  assert_eq "0" "$(recorded "$log" 'gh issue')" "and nothing is written"
}

case_close_sensorScopeAllowsItsOwnIssue() {
  local dir log out status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  issues_fixture "$dir" "[$(issue_record 52 OPEN "" coverage 'body')]"

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    issue_engine close.sh 52 --reason "the condition cleared" --evidence "1" --sensor coverage 2>&1)
  status=$?
  assert_eq "0" "$status" "a check closes its own issue: $out"
  assert_eq "1" "$(recorded "$log" 'issue comment')" "the evidence is posted first"
  assert_contains "$(cat "$log")" "issue close 52 --reason completed" "the closure is a completed fix"
  assert_eq "0" "$(recorded "$log" 'agent/eligible')" "no authorisation label is written"
}

case_reopen_refusesANotPlannedClosure() {
  local dir log out status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  issues_fixture "$dir" "[$(issue_record 61 CLOSED NOT_PLANNED coverage 'body')]"
  printf '%s\n' 'the returning measurement' > "$dir/measurement.md"

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    issue_engine reopen.sh 61 --status open --measurement-file "$dir/measurement.md" 2>&1)
  status=$?
  assert_eq "3" "$status" "a person's decision is not reversed"
  assert_contains "$out" "closure-not-reopenable" "the refusal reason"
  assert_eq "0" "$(recorded "$log" 'issue reopen')" "nothing is reopened"
  assert_eq "1" "$(recorded "$log" 'issue comment')" "the measurement is recorded as a comment"
}

case_reopen_neverWritesEligibility() {
  local dir log out status body
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  body=$(printf 'a fixture issue\n<!-- fingerprint: %s -->\n<!-- occurrence: 1 -->' "$(fingerprint_of case alpha)")
  issues_fixture "$dir" "[$(issue_record 62 CLOSED COMPLETED fixture-emit "$body")]"
  printf '%s\n' 'the returning measurement' > "$dir/measurement.md"

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    issue_engine reopen.sh 62 --status open --measurement-file "$dir/measurement.md" 2>&1)
  status=$?
  assert_eq "0" "$status" "a completed closure is reopened: $out"
  assert_eq "1" "$(recorded "$log" 'issue reopen')" "the issue is reopened once"
  assert_eq "1" "$(recorded "$log" 'issue comment')" "the returning measurement is posted"
  assert_eq "0" "$(recorded "$log" 'agent/eligible')" "the authorisation label is never written"
  assert_contains "$(cat "$log")" "--add-label status/open" "the state returns to open"
  assert_contains "$(last_body_content "$log")" "<!-- occurrence: 2 -->" "the recurrence count is incremented in the issue"
}

case_run_dispatchMatrix() {
  local dir log out status body
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  # three conditions: one closed as fixed, one open, one never seen
  local closed_fp open_fp new_fp
  closed_fp=$(fingerprint_of case closed-case)
  open_fp=$(fingerprint_of case open-case)
  new_fp=$(fingerprint_of case new-case)
  issues_fixture "$dir" "$(jq -nc \
    --argjson closed "$(issue_record 71 CLOSED COMPLETED fixture-emit \
      "$(printf 'body\n<!-- fingerprint: %s -->\n<!-- occurrence: 1 -->\n<!-- violation-run: 1 -->' "$closed_fp")" \
      "[$(marker_comment 2026-10-01T03:00:00Z fixture-emit "$closed_fp" violation 4)]")" \
    --argjson open "$(issue_record 72 OPEN "" fixture-emit \
      "$(printf 'body\n<!-- fingerprint: %s -->\n<!-- violation-run: 0 -->' "$open_fp")" \
      "[$(marker_comment 2026-10-10T03:00:00Z fixture-emit "$open_fp" violation 5)]")" \
    '[$closed, $open]')"
  local findings
  findings=$(write_findings "$dir/findings.jsonl" \
    "$(finding violation 7 case closed-case)" \
    "$(finding violation 8 case open-case)" \
    "$(finding violation 9 case new-case)")

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" \
    --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "0" "$status" "the run succeeds: $out"
  assert_eq "1" "$(recorded "$log" 'issue create')" "the new condition files one issue"
  assert_eq "1" "$(recorded "$log" 'issue reopen')" "the closed condition reopens its own issue"
  assert_contains "$(cat "$log")" "issue reopen 71" "the reopened issue is the one whose fingerprint matched"
  assert_contains "$(cat "$log")" "gh issue comment 72" "the open condition is annotated, not refiled"
  assert_contains "$out" "issue=72 action=annotated" "the report names the issue and the verb"
  assert_contains "$out" "issue=71 action=reopened" "the report names the reopened issue"
  assert_contains "$out" "issue=- action=filed" "the report names a filing that has no number yet"

  # The filed issue's own labels: the row's kind and area, the ownership label, the registry's fixed
  # values — and never the automation opt-in, because filing is not authorising.
  local filed
  filed=$(grep -- 'issue create' "$log" | tail -1)
  assert_contains "$filed" "class/bug" "the row's kind is used"
  assert_contains "$filed" "category/build-and-harness" "the row's area is used"
  assert_contains "$filed" "sensor/fixture-emit" "the ownership label is written with the issue"
  assert_contains "$filed" "source/ci" "the registry's source label is written"
  assert_not_contains "$filed" "agent/eligible" "a filed issue arrives unauthorised"
}

case_run_dispatchMatrixNotPlannedClosure() {
  local dir log out
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  local fp
  fp=$(fingerprint_of case declined-case)
  issues_fixture "$dir" "[$(issue_record 73 CLOSED NOT_PLANNED fixture-emit \
    "$(printf 'body\n<!-- fingerprint: %s -->\n<!-- occurrence: 1 -->\n<!-- violation-run: 1 -->' "$fp")" \
    "[$(marker_comment 2026-10-01T03:00:00Z fixture-emit "$fp" violation 4)]")]"
  local findings
  findings=$(write_findings "$dir/findings.jsonl" "$(finding violation 5 case declined-case)")

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" \
    --registry "$(fixture registry.json)" 2>&1)
  assert_eq "0" "$(recorded "$log" 'issue reopen')" "a person's decision is not reversed by the runner"
  assert_contains "$(cat "$log")" "gh issue comment 73" "the measurement is recorded as a comment instead"
  assert_contains "$out" "closure is a person's decision" "the run says why it did not reopen"
}

case_create_sensorFingerprintIsTheAnchor() {
  local dir log out status finding_json
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  local existing_fp other_fp
  existing_fp=$(fingerprint_of case alpha)
  other_fp=$(fingerprint_of case beta)
  issues_fixture "$dir" "[$(issue_record 81 OPEN "" fixture-emit \
    "$(printf 'body\n<!-- fingerprint: %s -->' "$existing_fp")")]"

  finding_json=$(jq -nc --arg fingerprint "$existing_fp" '{title:"t",class:"bug",category:"build-and-harness",
    claim:"the same condition, reported again", fingerprint:$fingerprint, body:"b",
    labels:["class/bug","category/build-and-harness"]}')
  printf '%s' "$finding_json" > "$dir/finding.json"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    ISSUES_SENSOR_REGISTRY="$(fixture registry.json)" \
    issue_engine create.sh --sensor fixture-emit --finding "$dir/finding.json" 2>&1)
  status=$?
  assert_eq "4" "$status" "the same condition under one check is one issue"
  assert_contains "$out" "81" "the existing issue is named"
  assert_eq "0" "$(recorded "$log" 'issue create')" "nothing is filed"

  finding_json=$(jq -nc --arg fingerprint "$other_fp" '{title:"t",class:"bug",category:"build-and-harness",
    claim:"a different condition under the same check", fingerprint:$fingerprint, body:"b",
    labels:["class/bug","category/build-and-harness"]}')
  printf '%s' "$finding_json" > "$dir/finding.json"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    ISSUES_SENSOR_REGISTRY="$(fixture registry.json)" \
    issue_engine create.sh --sensor fixture-emit --finding "$dir/finding.json" 2>&1)
  status=$?
  assert_eq "0" "$status" "a second cause is a second issue: $out"
  assert_eq "1" "$(recorded "$log" 'issue create')" "exactly one filing"
  assert_contains "$(cat "$log")" "sensor/fixture-emit" "the ownership label is written with the issue"
  assert_contains "$(cat "$log")" "occurrence" "the recurrence count starts in the body record"
}

# ----------------------------------------------------------------------------- 4. the lifecycle

case_run_hysteresisBothDirections() {
  local dir log out status body findings
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  body=$(printf 'body\n<!-- fingerprint: %s -->\n<!-- clear-run: 0 -->\n<!-- violation-run: 0 -->' \
    "$(fingerprint_of case alpha)")
  findings=$(write_findings "$dir/findings.jsonl" "$(finding clear 3 case alpha)")

  # one clear run is not a clearance
  issues_fixture "$dir" "[$(issue_record 91 OPEN "" fixture-emit "$body")]"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  assert_eq "0" "$(recorded "$log" 'issue close')" "one clear run does not close"
  assert_contains "$out" "hysteresis" "the run says why it did not close"
  assert_contains "$(last_body_content "$log")" "<!-- clear-run: 1 -->" "the clear run is counted on the issue"

  # the second consecutive clear does — the counter comes from the issue, not from the process
  log="$dir/calls2.log"; : > "$log"
  issues_fixture "$dir" "[$(issue_record 91 OPEN "" fixture-emit "$(last_body_content "$dir/calls.log")")]"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  assert_eq "1" "$(recorded "$log" 'issue close')" "the second consecutive clear closes"
  assert_contains "$(cat "$log")" "--reason completed" "the closure is a completed fix"

  # the violation direction is symmetric: one violation does not reopen, two do
  log="$dir/calls3.log"; : > "$log"
  body=$(printf 'body\n<!-- fingerprint: %s -->\n<!-- occurrence: 1 -->\n<!-- violation-run: 0 -->' \
    "$(fingerprint_of case alpha)")
  issues_fixture "$dir" "[$(issue_record 91 CLOSED COMPLETED fixture-emit "$body")]"
  findings=$(write_findings "$dir/violation.jsonl" "$(finding violation 4 case alpha)")
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  assert_eq "0" "$(recorded "$log" 'issue reopen')" "one violation does not reopen"
  assert_contains "$(last_body_content "$log")" "<!-- violation-run: 1 -->" "the violation run is counted"

  log="$dir/calls4.log"; : > "$log"
  issues_fixture "$dir" "[$(issue_record 91 CLOSED COMPLETED fixture-emit "$(last_body_content "$dir/calls3.log")")]"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  assert_eq "1" "$(recorded "$log" 'issue reopen')" "the second consecutive violation reopens"
  assert_contains "$out" "reopened" "the run reports the reopen"

  # an alternating sequence churns nothing: the counter that is not accumulating is reset
  log="$dir/calls5.log"; : > "$log"
  issues_fixture "$dir" "[$(issue_record 92 OPEN "" fixture-emit \
    "$(printf 'body\n<!-- fingerprint: %s -->\n<!-- clear-run: 1 -->\n<!-- violation-run: 0 -->' "$(fingerprint_of case alpha)")")]"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  assert_eq "0" "$(recorded "$log" 'issue close')" "an alternating sequence closes nothing"
  assert_contains "$(last_body_content "$log")" "<!-- clear-run: 0 -->" "the clear count is reset by a violation"
}

case_run_hysteresisCountersSurviveARestart() {
  local dir log out first second
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  local fp
  fp=$(fingerprint_of case alpha)
  local findings
  findings=$(write_findings "$dir/findings.jsonl" "$(finding clear 3 case alpha)")

  # run 1: the issue carries no counter yet, so this is the first clear run
  issues_fixture "$dir" "[$(issue_record 93 OPEN "" fixture-emit "$(printf 'body\n<!-- fingerprint: %s -->' "$fp")")]"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  first=$(last_body_content "$log")
  assert_contains "$first" "<!-- clear-run: 1 -->" "the first run counts one clear"

  # run 2: a fresh process, the counter read from the issue
  log="$dir/calls2.log"; : > "$log"
  issues_fixture "$dir" "[$(issue_record 93 OPEN "" fixture-emit "$first")]"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  second=$(last_body_content "$log")
  assert_contains "$second" "<!-- clear-run: 2 -->" "the second run continues from the issue's own count"
}

case_run_escalatesPastKRecurrences() {
  local dir log out
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  local fp
  fp=$(fingerprint_of case alpha)
  local body findings
  body=$(printf 'body\n<!-- fingerprint: %s -->\n<!-- occurrence: 3 -->\n<!-- violation-run: 0 -->' "$fp")
  issues_fixture "$dir" "[$(issue_record 95 CLOSED COMPLETED fixture-emit "$body")]"
  findings=$(write_findings "$dir/findings.jsonl" "$(finding violation 5 case alpha)")

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  assert_eq "0" "$(recorded "$log" 'issue reopen')" "past the recurrence bound nothing is reopened"
  assert_contains "$(cat "$log")" "--add-label agent/needs-decision" "the escalation marks the issue as awaiting a decision"
  assert_contains "$(cat "$log")" "--add-label status/on-hold" "the escalation puts the item on hold"
  assert_contains "$out" "escalated" "the run reports the escalation"

  # and a further recurrence does not reopen either
  log="$dir/calls2.log"; : > "$log"
  issues_fixture "$dir" "[$(issue_record 95 CLOSED COMPLETED fixture-emit \
    "$(printf 'body\n<!-- fingerprint: %s -->\n<!-- occurrence: 4 -->\n<!-- violation-run: 0 -->' "$fp")")]"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  assert_eq "0" "$(recorded "$log" 'issue reopen')" "no fourth reopen happens"
}

case_run_escalationNeverSetsEligibility() {
  local dir log out
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  local fp
  fp=$(fingerprint_of case alpha)
  issues_fixture "$dir" "[$(issue_record 96 CLOSED COMPLETED fixture-emit \
    "$(printf 'body\n<!-- fingerprint: %s -->\n<!-- occurrence: 3 -->' "$fp")")]"
  local findings
  findings=$(write_findings "$dir/findings.jsonl" "$(finding violation 5 case alpha)")

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  assert_eq "0" "$(recorded "$log" 'agent/eligible')" "an escalation authorises no work"
  local labels
  labels=$(grep -oE -- '--(add|remove)-label [^ ]*' "$log" | sort -u)
  assert_eq "$(printf '%s\n' "--add-label agent/needs-decision" "--add-label status/on-hold")" "$labels" \
    "the escalation's label write is exactly the two the registry declares"
}

# ----------------------------------------------------------------------------- 5. the metric rows

case_sensors_buildDurationRow() {
  local dir log out status findings timing
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  timing="$dir/gate-timings.json"
  jq -n '{durationMs: 542000, startedAt: "2026-10-17T03:00:00Z", finishedAt: "2026-10-17T03:09:02Z",
          taskFailures: 0, invocation: [":app:testMobileDebugUnitTest"],
          tasks: [{path: ":app:testMobileDebugUnitTest", outcome: "executed", durationMs: 195000}]}' > "$timing"

  # first run: no baseline on record, so no condition — and nothing written
  issues_fixture "$dir" '[]'
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only build-duration --apply --input "$timing" 2>&1)
  status=$?
  assert_eq "0" "$status" "the first run succeeds: $out"
  assert_eq "0" "$(recorded "$log" 'gh issue')" "no baseline means no issue"

  # second run: the issue's own record carries 100000ms, and 542000ms is past 100000 × 1.5
  log="$dir/calls2.log"; : > "$log"
  local fp
  fp=$(fingerprint_of job gate)
  issues_fixture "$dir" "[$(issue_record 101 OPEN "" build-duration \
    "$(printf 'body\n<!-- fingerprint: %s -->' "$fp")" \
    "[$(marker_comment 2026-10-16T03:00:00Z build-duration "$fp" violation 100000)]")]"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only build-duration --apply --input "$timing" 2>&1)
  status=$?
  assert_eq "0" "$status" "the second run succeeds: $out"
  assert_contains "$out" "verdict=violation" "a wall time past the bound is a violation"
  assert_contains "$(cat "$log")" "542000" "the measurement is posted"
  assert_contains "$(cat "$log")" "baseline=100000" "the baseline quoted is the earlier comment's number"
}

case_hotspot_ranksBySizeTimesChanges() {
  local dir out first hits
  dir=$(scratch)
  cd "$dir" || fail "cannot enter the fixture repository"
  printf 'x%.0s' $(seq 1 1000) > big.txt
  printf 'y%.0s' $(seq 1 100) > small.txt
  git init -q -b main . >/dev/null 2>&1
  git add -A >/dev/null 2>&1
  git -c user.email=selftest@example.invalid -c user.name=selftest commit -qm one >/dev/null 2>&1
  printf 'more\n' >> big.txt
  git add -A >/dev/null 2>&1
  git -c user.email=selftest@example.invalid -c user.name=selftest commit -qm two >/dev/null 2>&1
  printf 'more\n' >> big.txt
  git add -A >/dev/null 2>&1
  git -c user.email=selftest@example.invalid -c user.name=selftest commit -qm three >/dev/null 2>&1

  out=$(SENSORS_ID=hotspot SENSORS_ROOT="$dir" SENSORS_THRESHOLD=1 SENSORS_CONDITIONS='[]' \
    SENSORS_PROFILE="$SENSORS_REPO_ROOT/tools/tooling.json" \
    bash "$SENSORS_ENGINE/hotspot-report.sh" 2>&1)
  first=$(printf '%s\n' "$out" | grep '^hotspot: ' | head -1)
  assert_contains "$first" "path=big.txt" "the larger, more often changed path ranks first"
  assert_contains "$out" "size=1010" "the size is the path's own"

  # the bound is the registry's, not the script's
  hits=$(grep -nE 'threshold=[0-9]|threshold="?[0-9]' "$SENSORS_ENGINE/hotspot-report.sh" || true)
  assert_empty "$hits" "the script holds no threshold constant"
  assert_contains "$(cat "$SENSORS_ENGINE/hotspot-report.sh")" 'SENSORS_THRESHOLD' "the bound comes from the row"
}

case_sensors_packageSizeConsumesAnArtifact() {
  local dir artifact out hits
  dir=$(scratch)
  artifact="$dir/package.bin"
  printf 'x%.0s' $(seq 1 2048) > "$artifact"

  out=$(SENSORS_ID=package-size SENSORS_ROOT="$dir" SENSORS_INPUT="$artifact" \
    SENSORS_THRESHOLD=1.1 SENSORS_BASELINE=1000 SENSORS_COMPARISON=baseline-multiple \
    SENSORS_PROFILE="$SENSORS_REPO_ROOT/tools/tooling.json" \
    bash "$SENSORS_ENGINE/metrics/package-size.sh" 2>&1)
  assert_contains "$out" '"verdict":"violation"' "a package past its baseline bound is a violation"
  assert_contains "$out" '"measurement":"2048"' "the size is the measurement"

  hits=$(grep -n 'gradlew\|assemble' "$SENSORS_ENGINE"/*.sh 2>/dev/null || true)
  assert_empty "$hits" "no sensor builds anything of its own"
}

case_sensors_coverageRow() {
  local dir log out report status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  report="$dir/coverage.json"
  jq -n '{modules: {"app": 61.5, "core": 88.0}}' > "$report"
  issues_fixture "$dir" '[]'

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only coverage --apply --input "$report" 2>&1)
  status=$?
  assert_eq "0" "$status" "the coverage run succeeds: $out"
  assert_eq "1" "$(recorded "$log" 'issue create')" "the module under its floor is its own condition"
  assert_contains "$out" "measurement=61.5" "the violating module's number is the measurement"

  # the same number again: the second run annotates the issue it already has
  log="$dir/calls2.log"; : > "$log"
  local fp
  fp=$(fingerprint_of module app)
  issues_fixture "$dir" "[$(issue_record 111 OPEN "" coverage \
    "$(printf 'body\n<!-- fingerprint: %s -->' "$fp")")]"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only coverage --apply --input "$report" 2>&1)
  assert_eq "0" "$(recorded "$log" 'issue create')" "the same module is not filed twice"
  assert_contains "$(cat "$log")" "issue comment 111" "the measurement is recorded on the existing issue"
}

case_sensors_brokenMainFingerprintDistinguishesCauses() {
  local dir log out status first second
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  issues_fixture "$dir" '[]'
  local artifact
  artifact="$dir/gate-result.json"
  jq -n '{result: "FAILURE", job: "app:testMobileDebugUnitTest", case: "com.naviveylin.FooTest.bar"}' > "$artifact"
  first=$(SENSORS_ID=broken-main SENSORS_ROOT="$dir" SENSORS_INPUT="$artifact" \
    SENSORS_PROFILE="$SENSORS_REPO_ROOT/tools/tooling.json" \
    bash "$SENSORS_ENGINE/conditions/broken-main.sh" 2>/dev/null)
  jq -n '{result: "FAILURE", job: "app:testMobileDebugUnitTest", case: "com.naviveylin.FooTest.baz"}' > "$artifact"
  second=$(SENSORS_ID=broken-main SENSORS_ROOT="$dir" SENSORS_INPUT="$artifact" \
    SENSORS_PROFILE="$SENSORS_REPO_ROOT/tools/tooling.json" \
    bash "$SENSORS_ENGINE/conditions/broken-main.sh" 2>/dev/null)
  assert_not_contains "$first" "$(jq -r '.fingerprint' <<< "$second")" "two failing cases are two identities"

  # …and each becomes its own issue, while the same cause twice is one issue. The anchor the record
  # carries is the *condition's* fingerprint, not the check that reported it (design D4).
  local expected_bar expected_baz
  expected_bar=$(fingerprint_of job app:testMobileDebugUnitTest case com.naviveylin.FooTest.bar)
  expected_baz=$(fingerprint_of job app:testMobileDebugUnitTest case com.naviveylin.FooTest.baz)
  jq -n '{result: "FAILURE", job: "app:testMobileDebugUnitTest", case: "com.naviveylin.FooTest.bar"}' > "$artifact"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only broken-main --apply --input "$artifact" 2>&1)
  assert_contains "$(log_text "$log")" "<!-- fingerprint: $expected_bar -->" \
    "the first cause is filed under its own fingerprint"
  jq -n '{result: "FAILURE", job: "app:testMobileDebugUnitTest", case: "com.naviveylin.FooTest.baz"}' > "$artifact"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only broken-main --apply --input "$artifact" 2>&1)
  assert_eq "2" "$(recorded "$log" 'issue create')" "two causes, two issues"
  assert_contains "$(log_text "$log")" "<!-- fingerprint: $expected_baz -->" \
    "the second cause is filed under its own fingerprint"
}

case_sensors_prConflictsOnlyOwnPRs() {
  local dir log out status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  issues_fixture "$dir" '[]'
  jq -n '[{number: 201, mergeable: "CONFLICTING", author: {login: "Framstag"}},
          {number: 202, mergeable: "CONFLICTING", author: {login: "someone-else"}},
          {number: 203, mergeable: "MERGEABLE", author: {login: "Framstag"}}]' > "$dir/prs.json"

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" SENSORS_ID=pr-conflicts \
    SENSORS_ROOT="$SENSORS_REPO_ROOT" SENSORS_PROFILE="$SENSORS_REPO_ROOT/tools/tooling.json" \
    SENSORS_CONDITIONS='[]' bash "$SENSORS_ENGINE/conditions/pr-conflicts.sh" 2>&1)
  assert_contains "$out" '"pr":"201"' "the owner's conflicting change is a condition"
  assert_not_contains "$out" '"pr":"202"' "a contributor's conflict is not a condition"
  assert_contains "$out" "reported, not filed — pull request #202" "…and it is reported"

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only pr-conflicts --apply 2>&1)
  status=$?
  assert_eq "0" "$status" "the run succeeds: $out"
  assert_eq "1" "$(recorded "$log" 'issue create')" "only the owner's change is filed"
}

# ----------------------------------------------------------------------------- 6. the pipeline

case_workflow_onlyTwoTriggers() {
  local file out
  file="$SENSORS_REPO_ROOT/.github/workflows/sensors.yml"
  [ -f "$file" ] || fail "the sensor pipeline has no file"

  out=$(grep -n 'pull_request\|workflow_run' "$file" || true)
  assert_empty "$out" "neither trigger is named anywhere in the file"

  # the trigger block's own keys: exactly a schedule and a dispatch
  out=$(awk '/^on:/ { inside = 1; next } /^[^[:space:]]/ { inside = 0 } inside && /^[[:space:]][[:space:]]?[a-z_]+:/ { print $1 }' "$file" | sort -u | tr '\n' ' ')
  assert_eq "schedule: workflow_dispatch: " "$out" "exactly two triggers"

  out=$(grep -n "github.ref == 'refs/heads/main'" "$file" || true)
  [ -n "$out" ] || fail "the job is not pinned to the main branch"
  out=$(grep -n 'concurrency:' "$file" || true)
  [ -n "$out" ] || fail "two runs could race: no concurrency group"
  out=$(grep -n 'contents: read' "$file" || true)
  [ -n "$out" ] || fail "the workflow does not default to read-only contents"
  out=$(grep -n 'issues: write' "$file" || true)
  [ -n "$out" ] || fail "the job does not raise issues: write for itself"
  assert_eq "0" "$(grep -c 'github.token' "$file" || true)" "the workflow never uses the workflow's own token"

  out=$(grep -n 'guard.sh\|run.sh --due' "$file")
  assert_contains "$out" "guard.sh" "the guard runs"
  [ "$(printf '%s\n' "$out" | sed -n '1p' | cut -d: -f1)" -lt "$(printf '%s\n' "$out" | sed -n '2p' | cut -d: -f1)" ] ||
    fail "the guard does not precede the runner"

  [ -e "$SENSORS_REPO_ROOT/.github/CODEOWNERS" ] || fail "a pipeline change needs an owner's review"
  grep -q '/.github/workflows/' "$SENSORS_REPO_ROOT/.github/CODEOWNERS" ||
    fail "CODEOWNERS does not cover the workflow directory"
}

# ----------------------------------------------------------------------------- 7. the engine's portability

case_sensors_engineNamesNoProjectValue() {
  local hits rows commands
  hits=$(grep -rn 'broken-main\|pr-conflicts\|gradlew\|NaviVeylin\|Framstag' \
    "$SENSORS_ENGINE/run.sh" "$SENSORS_ENGINE/lib.sh" "$SENSORS_ENGINE/guard.sh" "$SENSORS_ENGINE/report.sh" 2>/dev/null || true)
  assert_empty "$hits" "the engine names no project value"

  # every row's command comes from the registry, and every command the registry names exists
  commands=$(jq -r '.rows[].cmd' "$SENSORS_ENGINE/sensors.json")
  [ -n "$commands" ] || fail "the registry declares no command"
  while IFS= read -r command; do
    [ -e "$SENSORS_REPO_ROOT/$command" ] || fail "the registry names a command that does not exist: $command"
    assert_not_contains "$(cat "$SENSORS_ENGINE/run.sh")" "$command" "the runner hard-codes $command"
  done <<< "$commands"

  # the repository and the allowlist come from the profile, read by the tooling that owns it
  assert_contains "$(cat "$SENSORS_ENGINE/guard.sh")" 'policy.sh' "the guard asks the tooling's gate"
  assert_contains "$(cat "$SENSORS_ENGINE/conditions/pr-conflicts.sh")" 'SENSORS_PROFILE' "the allowlist is read from the profile"
  rows=$(jq -r '.rows | length' "$SENSORS_ENGINE/sensors.json")
  [ "$rows" -ge 6 ] || fail "the registry declares fewer rows than the change's four metrics and two conditions"

  # No deletion anywhere: a mistaken or subverted check can close, and cannot destroy.
  hits=$(grep -rn 'issue delete\|graphql' "$SENSORS_ENGINE" "$SENSORS_ISSUES_ENGINE/reopen.sh" 2>/dev/null || true)
  assert_empty "$hits" "no check can delete an issue"
}
