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
  'guard#remoteIsConfigurable'
  'sensors#usesTheReservedNamespacesAndAddsNone'
  'Registry#everyRowIsComplete'
  'lib#rejectsAMalformedFinding'
  'run#selectsOnlyDueRows'
  'run#aDelayedTickSelectsTheRowsItWasScheduledFor'
  'run#aSkippedTickLeavesItsRowsForTheNextTick'
  'run#theWindowDoesNotSelectThePreviousTicksRow'
  'run#rowsSelectsSeveralRows'
  'run#rowsRefusesAnUndeclaredId'
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
  'sensors#sourceSizeRow'
  'sensors#brokenMainFingerprintDistinguishesCauses'
  'sensors#prConflictsOnlyOwnPRs'
  'sensors#brokenMainGreenReportsClear'
  'sensors#aRecordedConditionParsesToItsObject'
  'sensors#prConflictsNothingReportsClear'
  'workflow#onlyTwoTriggers'
  'workflow#aRedRecordRunsNoRecordRow'
  'workflow#theTickDeclaresItsWindow'
  'sensors#engineNamesNoProjectValue'
  'sensors#theDocumentedCredentialCoversEveryDeclaredRow'
)

sensor_cases() {
  printf '%s\n' "${SENSOR_CASES[@]}"
}

sensor_run_case() {
  case "$1" in
    'guard#eachCheckHasItsOwnReason') case_guard_eachCheckHasItsOwnReason ;;
    'guard#refusesOffMain') case_guard_refusesOffMain ;;
    'guard#refusesARowWithoutThresholdOrConsecutive') case_guard_refusesARowWithoutThresholdOrConsecutive ;;
    'guard#remoteIsConfigurable') case_guard_remoteIsConfigurable ;;
    'sensors#usesTheReservedNamespacesAndAddsNone') case_sensors_usesTheReservedNamespacesAndAddsNone ;;
    'Registry#everyRowIsComplete') case_registry_everyRowIsComplete ;;
    'lib#rejectsAMalformedFinding') case_lib_rejectsAMalformedFinding ;;
    'run#selectsOnlyDueRows') case_run_selectsOnlyDueRows ;;
    'run#aDelayedTickSelectsTheRowsItWasScheduledFor') case_run_aDelayedTickSelectsTheRowsItWasScheduledFor ;;
    'run#aSkippedTickLeavesItsRowsForTheNextTick') case_run_aSkippedTickLeavesItsRowsForTheNextTick ;;
    'run#theWindowDoesNotSelectThePreviousTicksRow') case_run_theWindowDoesNotSelectThePreviousTicksRow ;;
    'run#rowsSelectsSeveralRows') case_run_rowsSelectsSeveralRows ;;
    'run#rowsRefusesAnUndeclaredId') case_run_rowsRefusesAnUndeclaredId ;;
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
    'sensors#sourceSizeRow') case_sensors_sourceSizeRow ;;
    'sensors#brokenMainFingerprintDistinguishesCauses') case_sensors_brokenMainFingerprintDistinguishesCauses ;;
    'sensors#prConflictsOnlyOwnPRs') case_sensors_prConflictsOnlyOwnPRs ;;
    'sensors#brokenMainGreenReportsClear') case_sensors_brokenMainGreenReportsClear ;;
    'sensors#aRecordedConditionParsesToItsObject') case_sensors_aRecordedConditionParsesToItsObject ;;
    'sensors#prConflictsNothingReportsClear') case_sensors_prConflictsNothingReportsClear ;;
    'workflow#onlyTwoTriggers') case_workflow_onlyTwoTriggers ;;
    'workflow#aRedRecordRunsNoRecordRow') case_workflow_aRedRecordRunsNoRecordRow ;;
    'workflow#theTickDeclaresItsWindow') case_workflow_theTickDeclaresItsWindow ;;
    'sensors#engineNamesNoProjectValue') case_sensors_engineNamesNoProjectValue ;;
    'sensors#theDocumentedCredentialCoversEveryDeclaredRow') case_sensors_theDocumentedCredentialCoversEveryDeclaredRow ;;
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
# `<remote>/main` that points at HEAD — the state the guard requires.
throwaway_project() { # [<registry-file>] [<remote>] → the path
  local registry="${1:-$(fixture registry.json)}" remote="${2:-origin}" dir
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
  git -C "$dir" update-ref "refs/remotes/$remote/main" HEAD
  git -C "$dir" symbolic-ref "refs/remotes/$remote/HEAD" "refs/remotes/$remote/main"
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

  # 4b. the identity could not be read: a tracker that did not answer is not a refusal, so the guard reports the
  # outage and gives no verdict (issue #231, review round 2 — this was the ninth site of the class the change
  # closed, on the CI gate rather than on an item). The failure is the one `policy#preflightReportsAnUnreachableTracker`
  # records: the stub's socket-level failure on every `api user` call.
  local outage; outage=$(scratch); : > "$outage/count"
  out=$( (cd "$dir" && GH_STUB_DIR="$outage" GH_STUB_TRANSIENT_FAIL="api user" GH_STUB_TRANSIENT_TIMES=99 \
          GH_STUB_TRANSIENT_STATE="$outage/count" ISSUES_GH_RETRY_DELAYS="0 0 0" sensor_engine guard.sh) 2>&1 ); status=$?
  assert_eq "75" "$status" "an unreachable tracker is not a policy refusal: $out"
  assert_contains "$out" "tracker-unreachable" "the guard names the outage"
  case "$out" in *author-not-allowlisted*) fail "an outage is still reported as the allowlist verdict: $out" ;; esac
  out=$( (cd "$dir" && GH_STUB_DIR="$outage" GH_STUB_TRANSIENT_FAIL="api user" GH_STUB_TRANSIENT_TIMES=99 \
          GH_STUB_TRANSIENT_STATE="$outage/count" ISSUES_GH_RETRY_DELAYS="0 0 0" sensor_engine guard.sh --json) 2>&1 ); status=$?
  # The JSON form is the first line; the refusal text follows it on the same stream, as every guard's does.
  assert_eq "unknown" "$(printf '%s\n' "$out" | head -1 | jq -r '.decision')" "no verdict is given when none could be established"
  assert_eq "tracker-unreachable" "$(printf '%s\n' "$out" | head -1 | jq -r '.reason')" "the reason a caller can branch on"

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

case_guard_remoteIsConfigurable() {
  local dir out status
  # A checkout whose remote is not named `origin`: the local shape, and the shape of a fork checkout.
  dir=$(throwaway_project "$(fixture registry.json)" upstream)

  # The default refuses, and the message carries the value to pass.
  out=$( (cd "$dir" && sensor_engine guard.sh) 2>&1 ); status=$?
  assert_eq "5" "$status" "without the seam the guard refuses on a differently named remote"
  assert_contains "$out" "no-remote-main" "the refusal reason"
  assert_contains "$out" "refs exist for: upstream" "the refusal names the remote that does have a main ref"
  assert_contains "$out" "SENSORS_REMOTE" "the refusal names the seam"

  # …and the flag and the environment each make it pass, naming what it compared against.
  out=$( (cd "$dir" && sensor_engine guard.sh --remote upstream) 2>&1 ); status=$?
  assert_eq "0" "$status" "--remote resolves the comparison: $out"
  assert_contains "$out" "guard: head-is-remote-main upstream/main" "the resolved remote and branch are reported"

  out=$( (cd "$dir" && SENSORS_REMOTE=upstream sensor_engine guard.sh) 2>&1 ); status=$?
  assert_eq "0" "$status" "SENSORS_REMOTE resolves the comparison: $out"

  # A remote whose HEAD was never resolved still names the branch from its own refs.
  git -C "$dir" symbolic-ref --delete "refs/remotes/upstream/HEAD" 2>/dev/null ||
    git -C "$dir" update-ref -d "refs/remotes/upstream/HEAD"
  out=$( (cd "$dir" && SENSORS_REMOTE=upstream sensor_engine guard.sh) 2>&1 ); status=$?
  assert_eq "0" "$status" "the branch is read from the remote's refs, not from its HEAD symref: $out"
  assert_contains "$out" "guard: head-is-remote-main upstream/main" "the branch is still main"
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

# The schedule's tick is a minute, and GitHub may fire it minutes after that minute: the window a tick
# declares is what makes a late run the same tick rather than a silent no-op (issue #166).
case_run_aDelayedTickSelectsTheRowsItWasScheduledFor() {
  local dir findings out status
  dir=$(scratch); issues_fixture "$dir" '[]'
  findings=$(write_findings "$dir/findings.jsonl" "$(finding clear 1 case alpha)")

  # Seven minutes late, the 03:00 row is still the row this tick was scheduled for.
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine run.sh --due --within 30 --now 2026-10-17T03:07:00Z --input "$findings" \
    --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "0" "$status" "the delayed tick's run is green: $out"
  assert_contains "$out" "fixture-emit" "the row whose minute the tick missed is still selected"
  assert_not_contains "$out" "fixture-error" "a row whose minute is outside the window is not selected"

  # The punctual tick is unchanged whether or not it declares a window.
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine run.sh --due --within 30 --now 2026-10-17T03:00:00Z --input "$findings" \
    --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "0" "$status" "the punctual tick's run is green: $out"
  assert_contains "$out" "fixture-emit" "the punctual tick selects the row whose minute it is"

  # And the defect, kept as the reason the window exists: a caller that asks about one minute alone gets
  # the same green run and nothing measured when the tick is late.
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine run.sh --due --now 2026-10-17T03:07:00Z --input "$findings" \
    --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "0" "$status" "an unwindowed late tick is green: $out"
  assert_contains "$out" "no rows selected" \
    "without a declared window a late tick measures nothing and says nothing was due"
}

# A tick GitHub skipped entirely leaves its minutes in the interval the next tick selects over, so the
# row whose cadence fell into the gap is measured by the next tick that runs, whatever the gap (issue #256).
case_run_aSkippedTickLeavesItsRowsForTheNextTick() {
  local dir findings out status
  dir=$(scratch); issues_fixture "$dir" '[]'
  findings=$(write_findings "$dir/findings.jsonl" "$(finding clear 1 case alpha)")

  # The previous tick started at 01:00 and the next one is at 04:00: the interval (01:00, 04:00] reaches
  # the 03:00 row that no tick in between measured.
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine run.sh --due --since 2026-10-17T01:00:00Z --now 2026-10-17T04:00:00Z --rows fixture-emit \
    --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "0" "$status" "the next tick's run is green: $out"
  assert_contains "$out" "fixture-emit" "the row whose minute fell into the gap is selected"

  # The previous tick's own minute is outside the interval: a row it measured is not measured again.
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine run.sh --due --since 2026-10-17T03:00:00Z --now 2026-10-17T04:00:00Z --rows fixture-emit \
    --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "0" "$status" "the next tick's run is green: $out"
  assert_not_contains "$out" "fixture-emit" "the row the previous tick's interval covered is not selected"
  assert_contains "$out" "no rows selected" "no row is selected from the previous tick's own minute"

  # The second row's own minute is in the interval: it is selected even though the first row's is not.
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine run.sh --due --since 2026-10-17T03:00:00Z --now 2026-10-17T04:00:00Z --rows fixture-error \
    --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  assert_contains "$out" "fixture-error" "the row whose minute is the tick's own minute is selected"
}

# The window's bound is what keeps a late tick from measuring a row the previous tick already measured.
case_run_theWindowDoesNotSelectThePreviousTicksRow() {
  local dir findings out status
  dir=$(scratch); issues_fixture "$dir" '[]'
  findings=$(write_findings "$dir/findings.jsonl" "$(finding clear 1 case alpha)")

  # Thirty minutes wide and ending at the tick's own minute: 03:29 reaches back over the 03:00 row at the
  # window's far edge...
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine run.sh --due --within 30 --now 2026-10-17T03:29:00Z --input "$findings" \
    --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "0" "$status" "the far edge of the window is green: $out"
  assert_contains "$out" "fixture-emit" "the window reaches the row whose minute is 29 minutes back"

  # ...and 03:30 — the next tick — does not, so a row's minute belongs to one tick's window, and a row
  # measured in the previous tick's run is not measured again by the next.
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine run.sh --due --within 30 --now 2026-10-17T03:30:00Z --input "$findings" \
    --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "0" "$status" "the next tick's run is green: $out"
  assert_contains "$out" "no rows selected" \
    "the next tick does not select the row the previous tick's window covered"

  # A window that is not a count of minutes is a declared bound that cannot be used, so it is refused
  # rather than silently narrowed to one minute — the silence that made a late tick a no-op.
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine run.sh --due --within 0 --input "$findings" \
    --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "2" "$status" "a window of no minutes is a usage refusal: $out"
  assert_contains "$out" "--within" "the refusal names the flag it cannot read"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine run.sh --due --within half --input "$findings" \
    --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "2" "$status" "a window that is not a number is a usage refusal: $out"
}

case_run_rowsSelectsSeveralRows() {
  local dir findings out status
  dir=$(scratch); issues_fixture "$dir" '[]'
  findings=$(write_findings "$dir/findings.jsonl" "$(finding clear 1 case alpha)")

  # The list selects the candidates; each one's own cadence still decides whether it runs, so at 03:00 the
  # named row whose cron matches runs and the named row whose cron does not stays out.
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine run.sh --due --now 2026-10-17T03:00:00Z --input "$findings" \
    --rows fixture-emit,fixture-error --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "0" "$status" "the listed row that is due runs: $out"
  assert_contains "$out" "fixture-emit" "the first named row is selected"
  assert_not_contains "$out" "fixture-error" "a named row whose cadence is not due is not selected"

  # A row outside the list is not a candidate even when its own cadence is due — 04:00 is the second row's
  # minute, and nothing at all is selected because the list does not name it.
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine run.sh --due --now 2026-10-17T04:00:00Z --input "$findings" \
    --rows fixture-emit --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "0" "$status" "a list none of whose rows is due selects nothing: $out"
  assert_contains "$out" "no rows selected" "the run says so instead of measuring a row it was not given"
  assert_not_contains "$out" "fixture-error" "the due row outside the list is not selected"

  # Repeating the flag builds the same list as the comma-separated form, and without --due every named row
  # is selected — including the fixture sensor that fails, which is how a case sees that it was attempted.
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" \
    sensor_engine run.sh --now 2026-10-17T09:00:00Z --input "$findings" \
    --rows fixture-emit --rows fixture-error --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "8" "$status" "the failing fixture sensor's error is the run's: $out"
  assert_contains "$out" "fixture-emit" "the first repeated --rows row was attempted"
  assert_contains "$out" "fixture-error" "the second repeated --rows row was attempted"
}

case_run_rowsRefusesAnUndeclaredId() {
  local dir findings log out status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  issues_fixture "$dir" '[]'
  findings=$(write_findings "$dir/findings.jsonl" "$(finding clear 1 case alpha)")

  # A list is a selection of declared rows: an id the registry does not declare is refused before any row
  # runs, so a pipeline step cannot ask for a check that does not exist and read the silence as a clear.
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --rows fixture-emit,no-such-row --input "$findings" \
    --registry "$(fixture registry.json)" 2>&1)
  status=$?
  assert_eq "2" "$status" "an undeclared row id is a usage refusal: $out"
  assert_contains "$out" "no-such-row" "the refusal names the id it does not find"
  assert_contains "$out" "fixture-emit" "the refusal names the ids the registry does declare"
  assert_empty "$(cat "$log")" "nothing runs and nothing is written for an undeclared id"
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
  # A filed item carries the registry's `.labels.status` (`sensors.json`), and `issue_record` writes none, so
  # the state is added here the way a real item has it — otherwise the state half of this write is invisible to
  # the case, which is exactly how an add-only escalation stayed green.
  jq '.[0].labels += [{name: "status/open"}]' "$dir/issues.json" > "$dir/with-state.json" &&
    mv "$dir/with-state.json" "$dir/issues.json"
  local findings
  findings=$(write_findings "$dir/findings.jsonl" "$(finding violation 5 case alpha)")

  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only fixture-emit --apply --input "$findings" --registry "$(fixture registry.json)" 2>&1)
  assert_eq "0" "$(recorded "$log" 'agent/eligible')" "an escalation authorises no work"
  local labels
  labels=$(grep -oE -- '--(add|remove)-label [^ ]*' "$log" | sort -u)
  # The escalation sets the state the registry declares *and* takes the item's own state off in the same edit,
  # so the escalated item carries one `status/*` label and not two (`fix-status-label-replacement`, I16).
  assert_eq "$(printf '%s\n' "--add-label agent/needs-decision" "--add-label status/on-hold" \
    "--remove-label status/open")" "$labels" \
    "the escalation's label write is the two the registry declares, and it replaces the state the item had"
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

# The size row: one condition per tracked source file, in the unit and scope the item's recorded decision
# named — lines per file, Kotlin and Java, tests included, the native sources out. The bound is the row's, so
# the case reads it from the registry and proves the script applies it rather than restating it; the unit and
# the scope are the measurement's own constants, named in its header (issue #196, design D9/D10).
case_sensors_sourceSizeRow() {
  local dir row out findings status hits

  row=$(jq -c '.rows[] | select(.id == "source-size")' "$SENSORS_ENGINE/sensors.json")
  [ -n "$row" ] || fail "the registry declares no source-size row"
  assert_eq "1500" "$(jq -r '.threshold' <<< "$row")" "the row's bound is the 1500 lines the decision recorded"
  assert_eq "greater-than" "$(jq -r '.comparison' <<< "$row")" "the row fires above its bound"
  assert_eq '["path"]' "$(jq -c '.fingerprint.fields' <<< "$row")" "the condition's identity is the path"
  assert_eq "improvement" "$(jq -r '.class' <<< "$row")" "the row files an improvement"

  # A fixture repository: one Kotlin file past the bound, one Java file inside it, and two files that are
  # past every bound and are not the row's business — the native sources and prose.
  dir=$(scratch)
  mkdir -p "$dir/app/src/main/java" "$dir/app/src/main/cpp" "$dir/docs"
  seq 1 1600 | sed 's/^/\/\/ line /' > "$dir/app/src/main/java/Big.kt"
  seq 1 10 | sed 's/^/\/\/ line /' > "$dir/app/src/main/java/Small.java"
  seq 1 5000 | sed 's/^/\/\/ line /' > "$dir/app/src/main/cpp/native_thing.cpp"
  seq 1 4000 | sed 's/^/a line of prose /' > "$dir/docs/NOTES.md"
  git -C "$dir" init -q -b main >/dev/null 2>&1 || fail "could not create the fixture repository"
  git_commit "$dir" one

  out=$(cd "$dir" && SENSORS_ID=source-size SENSORS_ROOT="$dir" SENSORS_THRESHOLD=1500 \
    SENSORS_CONDITIONS='[]' SENSORS_PROFILE="$SENSORS_REPO_ROOT/tools/tooling.json" \
    bash "$SENSORS_ENGINE/metrics/source-size.sh" 2>&1)
  status=$?
  assert_eq "0" "$status" "the measurement runs: $out"
  findings=$(printf '%s\n' "$out" | grep '^{' || true)
  assert_contains "$findings" '"path":"app/src/main/java/Big.kt"' "the file past the bound is its own condition"
  assert_contains "$findings" '"measurement":"1600"' "the measurement is the file's own line count"
  assert_contains "$findings" '"verdict":"violation"' "a file past the bound is a violation"
  assert_not_contains "$findings" 'Small.java' "a file inside the bound is no condition"
  assert_not_contains "$findings" 'native_thing.cpp' "a file outside the measured languages is no condition"
  assert_not_contains "$findings" 'NOTES.md' "a file that is neither Kotlin nor Java is outside the row"

  # The bound is the registry's, not the script's: retuning the check is one edit in `sensors.json`.
  hits=$(grep -nE 'threshold=[0-9]|threshold="?[0-9]' "$SENSORS_ENGINE/metrics/source-size.sh" || true)
  assert_empty "$hits" "the script holds no threshold constant"
  assert_contains "$(cat "$SENSORS_ENGINE/metrics/source-size.sh")" 'SENSORS_THRESHOLD' \
    "the bound comes from the row"

  # A tree with nothing past the bound still reaches a verdict: the runner reads a row that printed no
  # finding as a failed check, so the clear has to be printed (`backlog-sensors`, three outcomes).
  git -C "$dir" rm -q app/src/main/java/Big.kt
  git_commit "$dir" two
  out=$(cd "$dir" && SENSORS_ID=source-size SENSORS_ROOT="$dir" SENSORS_THRESHOLD=1500 \
    SENSORS_CONDITIONS='[]' SENSORS_PROFILE="$SENSORS_REPO_ROOT/tools/tooling.json" \
    bash "$SENSORS_ENGINE/metrics/source-size.sh" 2>&1)
  findings=$(printf '%s\n' "$out" | grep '^{' || true)
  assert_contains "$findings" '"verdict":"clear"' "a tree inside the bound reports a clear: $out"
  assert_contains "$findings" '"path":"none"' "the row-level clear carries the row's own identity: $out"
  assert_not_contains "$findings" '"verdict":"violation"' "nothing is past the bound"

  # A recorded open condition whose path has dropped under the bound clears under that path: the issue that
  # carries the condition closes on the identity it holds (design D4), so the clear has to name the path and
  # measure it, rather than fall through to the row-level `none`.
  out=$(cd "$dir" && SENSORS_ID=source-size SENSORS_ROOT="$dir" SENSORS_THRESHOLD=1500 \
    SENSORS_CONDITIONS='[{"state":"OPEN","condition":{"path":"app/src/main/java/Small.java"}}]' \
    SENSORS_PROFILE="$SENSORS_REPO_ROOT/tools/tooling.json" \
    bash "$SENSORS_ENGINE/metrics/source-size.sh" 2>&1)
  findings=$(printf '%s\n' "$out" | grep '^{' || true)
  assert_contains "$findings" '"path":"app/src/main/java/Small.java"' \
    "a recorded condition inside the bound clears under its own path: $out"
  assert_contains "$findings" '"measurement":"10"' "the clear measures the path it names: $out"
  assert_contains "$findings" '"verdict":"clear"' "a recorded condition inside the bound is a clear: $out"
  assert_not_contains "$findings" '"path":"none"' "a cleared condition is not the row-level clear: $out"

  # A tree git cannot list is the third outcome, never a clean one: an unreadable source list would otherwise
  # reach the tracker as "no file is past the bound" ("The check itself failed").
  dir=$(scratch)
  out=$(cd "$dir" && GIT_CEILING_DIRECTORIES="$SENSORS_TMP" SENSORS_ID=source-size SENSORS_ROOT="$dir" \
    SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS='[]' \
    SENSORS_PROFILE="$SENSORS_REPO_ROOT/tools/tooling.json" \
    bash "$SENSORS_ENGINE/metrics/source-size.sh" 2>&1)
  assert_contains "$out" '"verdict":"error"' "a tree git cannot list is a failed check, not a clear: $out"
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

case_sensors_brokenMainGreenReportsClear() {
  # A green build with no condition recorded is a verdict, not silence: the runner reads a row that printed
  # nothing as a failed check, so the row has to say which of the three outcomes it reached.
  local dir log artifact out
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  issues_fixture "$dir" '[]'
  artifact="$dir/gate-result.json"
  jq -n '{result: "success", job: "gate", case: "the-build"}' > "$artifact"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only broken-main --apply --input "$artifact" --json 2>&1)
  assert_contains "$out" '"verdict":"clear"' "a green build reports a clear"
  assert_contains "$out" '"row":"broken-main"' "the finding is the row's own"
  assert_eq "0" "$(recorded "$log" 'issue create')" "a green build files nothing"
  assert_eq "0" "$(recorded "$log" 'issue close')" "and closes nothing, because no condition is recorded"
}

case_sensors_aRecordedConditionParsesToItsObject() {
  # The body record the engine writes ends with the marker's own `-->` tail, so the parser must take the object
  # between the braces and not everything up to the next `>`: the tail-tolerant pattern swallows ` --`, every
  # condition then parses to `{}`, and a green build's clear lands on a finding that owns no issue (#230).
  local dir log artifact body fp out parsed
  dir=$(scratch); log="$dir/calls.log"; : > "$log"

  # 1. The parse itself: the marker's object, and no condition at all for a body that carries no marker.
  parsed=$(
    # shellcheck source=tools/sensors/lib.sh
    source "$SENSORS_ENGINE/lib.sh"
    sensors_conditions_json '[{"number":198,"state":"OPEN","body":"red\n\n<!-- condition: {\"job\":\"build\",\"case\":\"apk\"} -->"}]' |
      jq -c '.[0].condition'
  )
  assert_eq '{"job":"build","case":"apk"}' "$parsed" "a marker with its --> tail parses to its object"
  parsed=$(
    # shellcheck source=tools/sensors/lib.sh
    source "$SENSORS_ENGINE/lib.sh"
    sensors_conditions_json '[{"number":1,"state":"OPEN","body":"no marker in this body"}]' |
      jq -c '.[0].condition'
  )
  assert_eq '{}' "$parsed" "a body with no marker carries no condition"

  # 2. The consequence: the green run closes the issue the *recorded* condition belongs to, rather than
  # reporting a clear that names the green record's own identity and so owns nothing.
  fp=$(fingerprint_of job build case apk)
  body=$(printf 'the main build is red\n\n<!-- fingerprint: %s -->\n<!-- condition: {"job":"build","case":"apk"} -->' "$fp")
  issues_fixture "$dir" "[$(issue_record 198 OPEN null broken-main "$body")]"
  artifact="$dir/gate-result.json"
  jq -n '{result: "success", job: "gate", case: "the-build"}' > "$artifact"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only broken-main --apply --input "$artifact" --json 2>&1)
  assert_contains "$out" "\"verdict\":\"clear\"" "a green build reports a clear"
  assert_contains "$out" "\"fingerprint\":\"$fp\"" "the clear names the recorded condition's identity"
  assert_eq "1" "$(recorded "$log" 'issue close')" \
    "the issue the recorded condition identifies is the one a green build closes"
}

case_sensors_prConflictsNothingReportsClear() {
  # The same duty on the other condition row: no conflict of the owner's is a clear, and an empty report is
  # not.
  local dir log out
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  issues_fixture "$dir" '[]'
  jq -n '[{number: 301, mergeable: "MERGEABLE", author: {login: "Framstag"}}]' > "$dir/prs.json"
  out=$(cd "$SENSORS_REPO_ROOT" && GH_STUB_DIR="$dir" GH_STUB_RECORD="$log" \
    sensor_engine run.sh --only pr-conflicts --apply --json 2>&1)
  assert_contains "$out" '"verdict":"clear"' "no conflicting change reports a clear"
  assert_eq "0" "$(recorded "$log" 'issue create')" "a project with no conflict files nothing"
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

  # the trigger block's own keys: only the two the design permits, and a dispatch among them so the pipeline
  # can be run at all. The cap is the invariant — never a trigger that runs near a change under review. The
  # schedule is what makes a declared cadence run without a person; that its minutes cover the rows' own
  # crons is `workflow#tickMinutesCoverTheRegistryCrons` in the build inputs' cases.
  out=$(awk '/^on:/ { inside = 1; next } /^[^[:space:]]/ { inside = 0 } inside && /^[[:space:]][[:space:]]?[a-z_]+:/ { print $1 }' "$file" | sort -u | tr '\n' ' ')
  case "$out" in
    'schedule: '|'workflow_dispatch: '|'schedule: workflow_dispatch: ') ;;
    *) fail "the pipeline's triggers are outside {schedule, workflow_dispatch}: $out" ;;
  esac
  assert_contains "$out" "workflow_dispatch:" "the pipeline can be dispatched"

  out=$(grep -n "github.ref == 'refs/heads/main'" "$file" || true)
  [ -n "$out" ] || fail "the job is not pinned to the main branch"
  out=$(grep -n 'concurrency:' "$file" || true)
  [ -n "$out" ] || fail "two runs could race: no concurrency group"
  out=$(grep -n 'contents: read' "$file" || true)
  [ -n "$out" ] || fail "the workflow does not default to read-only contents"
  out=$(grep -n 'issues: write' "$file" || true)
  [ -n "$out" ] || fail "the job does not raise issues: write for itself"

  # The workflow's own token is read-only and used for exactly one thing: fetching the build pipeline's
  # record of this commit (`actions: read`). Every tracker write is still the repository-scoped
  # `SENSORS_TOKEN`, so the identity that files, comments and closes is never the ephemeral one — the rule
  # the case has always asserted, now narrowed to what the token is used *for*.
  assert_eq "1" "$(grep -c 'github.token' "$file" || true)" "the workflow's own token is used exactly once"
  out=$(grep -n 'actions: read' "$file" || true)
  [ -n "$out" ] || fail "the record fetch has no actions: read"
  fetch_line=$(grep -n "name: Fetch this commit's build record" "$file" | cut -d: -f1)
  token_line=$(grep -n 'github.token' "$file" | cut -d: -f1)
  tracker_line=$(grep -n 'name: Create the declared labels' "$file" | cut -d: -f1)
  [ -n "$fetch_line" ] && [ -n "$token_line" ] && [ -n "$tracker_line" ] ||
    fail "the record fetch step, the tracker's first step and the token are not all present"
  { [ "$fetch_line" -lt "$token_line" ] && [ "$token_line" -lt "$tracker_line" ]; } ||
    fail "the workflow's own token is used outside the record fetch"

  # Everything that writes to the tracker waits for the record, so a scheduled run whose build has not
  # finished writes nothing at all rather than creating labels, repairing a citation or closing a duplicate
  # for a measurement it cannot make (spec `backlog-sensors`). Which rows each step then runs is that step's
  # own gate: `workflow#aRedRecordRunsNoRecordRow` holds the red build's half.
  #
  # The rule is stated over the steps that write, not over how many gates the file carries: the count was
  # pinned at four and the scheduled hygiene step (#198) made it five, so the case failed for a step that did
  # carry its gate — and a count is blind in the direction this invariant cares about anyway, because a step
  # added *without* a gate leaves it unchanged.
  out=$(awk '
    function flush() {
      if (name != "" && writes) printf "%s|%s\n", (gated ? "gated" : "ungated"), name
      name = ""; gated = 0; writes = 0; running = 0; body = ""
    }
    /^[[:space:]]*- name:/ { flush(); name = $0; sub(/^[[:space:]]*- name:[[:space:]]*/, "", name); next }
    name != "" {
      if ($0 ~ /steps\.record\.outputs\.ready == .true./) gated = 1
      if ($0 ~ /^[[:space:]]+run:/) running = 1
      if (running) body = body " " $0
      if (body ~ /tools\/(issues\/(labels|resolve-stale|duplicates|create|update|close)\.sh|sensors\/run\.sh)/) writes = 1
    }
    END { flush() }
  ' "$file")
  assert_empty "$(printf '%s\n' "$out" | grep '^ungated' || true)" \
    "no step writes to the tracker before the record is ready"
  # …and the parse may not pass vacuously: the writers the workflow has are named, so a step that stops being
  # recognised as one fails here instead of silently narrowing the rule.
  for step in 'Create the declared labels' 'Repair the stale citations' \
              'Close what the tracker says about itself' 'Run the rows that measure the build' \
              'Run the condition rows and the repository rows'; do
    assert_contains "$out" "gated|$step" "the step '$step' writes to the tracker and is gated"
  done

  # The dispatch's `apply` is a boolean input, and a boolean is not a string: `inputs.apply == 'true'` is
  # false for every value (the runner coerces the boolean to a number and the string to NaN), so a dispatch
  # could never write. The schedule term is what makes an unattended run write.
  assert_contains "$(cat "$file")" 'inputs.apply == true' "the apply input is compared as the boolean it is"
  assert_not_contains "$(cat "$file")" "inputs.apply == 'true'" "the apply input is not compared as a string"
  assert_contains "$(cat "$file")" "github.event_name == 'schedule'" "a scheduled run writes"

  out=$(grep -n 'guard.sh\|run.sh --due' "$file")
  assert_contains "$out" "guard.sh" "the guard runs"
  [ "$(printf '%s\n' "$out" | sed -n '1p' | cut -d: -f1)" -lt "$(printf '%s\n' "$out" | sed -n '2p' | cut -d: -f1)" ] ||
    fail "the guard does not precede the runner"

  [ -e "$SENSORS_REPO_ROOT/.github/CODEOWNERS" ] || fail "a pipeline change needs an owner's review"
  grep -q '/.github/workflows/' "$SENSORS_REPO_ROOT/.github/CODEOWNERS" ||
    fail "CODEOWNERS does not cover the workflow directory"
}

# The interval is not a constant the engine assumes: the pipeline declares its lower bound, the previous
# tick's start time, so a tick GitHub skipped leaves its minutes in the next tick's interval (#256).
case_workflow_theTickDeclaresItsWindow() {
  local workflow steps total declared prev_query
  workflow="$SENSORS_REPO_ROOT/.github/workflows/sensors.yml"
  [ -f "$workflow" ] || fail "no sensor pipeline file"

  # Every scheduled invocation of the runner names the interval it selects over, and the interval's lower
  # bound is the previous tick's start time rather than the tick's own minute.
  steps=$(grep '^[[:space:]]*run: .*run\.sh --due' "$workflow" || true)
  total=$(printf '%s\n' "$steps" | grep -c . || true)
  [ "$total" -ge 1 ] || fail "no scheduled tick runs the runner"
  declared=$(printf '%s\n' "$steps" | grep -c -- '--since "\$SENSORS_SINCE"' || true)
  assert_eq "$total" "$declared" \
    "every scheduled tick passes --since the previous tick's start time"

  # The workflow obtains that start time from its own run history, with a fallback of the schedule's own
  # cadence when there is no previous run.
  prev_query=$(grep -n 'gh run list' "$workflow" || true)
  [ -n "$prev_query" ] || fail "the workflow does not read the previous run's start time"
  grep -q 'SENSORS_SINCE' "$workflow" ||
    fail "the workflow does not pass the previous tick's start time to the runner"
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

# ----------------------------------------------------------------------------- 8. the red build, and the credential

# The rows a *red* build's record must not reach: the build's own outputs (the timing, the package, the
# coverage) are the record rows' inputs, and a build that did not succeed cannot produce them. This case
# holds both halves — the pipeline's own gates, and what the pipeline's own commands then do.
case_workflow_aRedRecordRunsNoRecordRow() {
  local file steps record_step other_step record_rows other_rows declared selected
  local dir out status hits
  file="$SENSORS_REPO_ROOT/.github/workflows/sensors.yml"
  [ -f "$file" ] || fail "the sensor pipeline has no file"

  # Each row step with the gate that stands above it: the record rows wait for a conclusion of `success`,
  # the other rows do not (a red build is exactly what one of them reports).
  steps=$(awk '
    /^ +- name: / { gate = "" }
    /^ +if: / { gate = $0 }
    /run: bash tools\/sensors\/run\.sh --due .*--rows / { printf "%s\t%s\n", gate, $0 }
  ' "$file")
  assert_eq "2" "$(printf '%s\n' "$steps" | grep -c .)" "the pipeline has one row step per family of rows"

  record_step=$(printf '%s\n' "$steps" | grep "conclusion == 'success'" || true)
  other_step=$(printf '%s\n' "$steps" | grep -v "conclusion == 'success'" || true)
  [ -n "$record_step" ] ||
    fail "the rows that read the build's own outputs are not gated on a record a successful build published"
  [ -n "$other_step" ] || fail "no row step runs for a record the build did not complete successfully"

  # Every row step names the rows it selects, so what runs for a red record is a decision in the file
  # rather than "everything whose cadence is due".
  hits=$(grep -n 'run\.sh --due' "$file" | grep -v -- '--rows' || true)
  assert_empty "$hits" "a row step selects every due row, so a red build's record reaches a row that reads it"

  # The two lists are the registry's rows: one home for which check exists, and the file's two sets must
  # cover it exactly once — a row added to the registry and left out of both steps would otherwise never run.
  record_rows=$(printf '%s' "$record_step" | sed -n 's/.*--rows \([^ ]*\).*/\1/p')
  other_rows=$(printf '%s' "$other_step" | sed -n 's/.*--rows \([^ ]*\).*/\1/p')
  [ -n "$record_rows" ] || fail "the record rows' step names no rows"
  [ -n "$other_rows" ] || fail "the other rows' step names no rows"
  declared=$(jq -r '.rows[].id' "$SENSORS_ENGINE/sensors.json" | sort)
  selected=$(printf '%s\n%s\n' "$(printf '%s' "$record_rows" | tr ',' '\n')" \
    "$(printf '%s' "$other_rows" | tr ',' '\n')" | grep -v '^$' | sort)
  assert_eq "$declared" "$selected" "the two steps select every declared row"
  assert_eq "$(printf '%s\n' "$selected" | sort -u)" "$selected" "no row is selected by both steps"

  # The pipeline's own commands, run against the record a red build publishes: the result record alone
  # (the publisher always writes that one), in a tree that has the tools the pipeline runs.
  dir=$(scratch)
  cp -R "$SENSORS_REPO_ROOT/tools" "$dir/tools"
  mkdir -p "$dir/app/build"
  printf '{"job":"build","case":"apk","result":"failure"}\n' > "$dir/app/build/gate-result.json"
  git -C "$dir" init -q -b main >/dev/null 2>&1 || fail "could not create the throwaway repository"
  git -C "$dir" add -A >/dev/null 2>&1 || fail "could not stage the throwaway repository"
  git -C "$dir" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm init >/dev/null 2>&1 ||
    fail "could not record the throwaway repository's first state"

  # What the pipeline does issue: the other rows — and the build's redness reaches the tracker as that one
  # row's verdict, so the run exits 0 with nothing written to the tracker for the failed build.
  out=$(cd "$dir" && GH_STUB_DIR="$SENSORS_REPO_ROOT/tools/issues/fixtures" \
    bash tools/sensors/run.sh --due --rows "$other_rows" --now 2026-10-18T00:00:00Z 2>&1)
  status=$?
  assert_eq "0" "$status" "the rows a red build does not touch run green: $out"
  assert_contains "$out" "broken-main" "the failing build is reported as the row whose verdict it is"
  assert_not_contains "$out" "verdict=error" "no row reports a failed check for the red build"

  # What it does not, and why: the same record through the record rows is the defect this change removes —
  # a row that cannot read the build's output reports a failed check, so the pipeline must not issue it.
  out=$(cd "$dir" && GH_STUB_DIR="$SENSORS_REPO_ROOT/tools/issues/fixtures" \
    bash tools/sensors/run.sh --due --rows "$record_rows" --now 2026-10-18T03:00:00Z 2>&1)
  status=$?
  assert_eq "8" "$status" "a record row over a red build's record reports a failed check: $out"
  assert_contains "$out" "verdict=error" "that row's third outcome is what the gate above keeps off the run"
}

# The credential the pipeline runs with is a repository secret, so the permissions it must be created with
# have one live home — and every `gh` verb a declared row's command calls must be inside them.
case_sensors_theDocumentedCredentialCoversEveryDeclaredRow() {
  local doc scope registry command verb need
  doc="$SENSORS_REPO_ROOT/guidelines/Backlog.md"
  registry="$SENSORS_ENGINE/sensors.json"
  [ -f "$doc" ] || fail "the checks' doctrine has no file"

  # §9 owns the checks' doctrine, so the scope its credential is created with is stated there: Issues is
  # what files, annotates and closes an issue, Pull requests is what a row that lists pull requests needs.
  scope=$(awk '/^## 9\./ { inside = 1 } /^## 10\./ { inside = 0 } inside' "$doc")
  [ -n "$scope" ] || fail "guidelines/Backlog.md §9 could not be read"
  assert_contains "$scope" "Issues: Read and write" "the doctrine does not name the tracker's write scope"
  assert_contains "$scope" "Pull requests: Read" "the doctrine does not name the scope the pull-request row needs"

  while IFS= read -r command; do
    [ -n "$command" ] || continue
    [ -f "$SENSORS_REPO_ROOT/$command" ] || fail "the registry names a command that does not exist: $command"
    while IFS= read -r verb; do
      [ -n "$verb" ] || continue
      case "$verb" in
        pr) need="Pull requests" ;;
        issue|label) need="Issues" ;;
        *) continue ;;
      esac
      assert_contains "$scope" "$need" \
        "$command calls 'gh $verb', and the documented scope does not name '$need'"
    done < <(grep -o 'gh [a-z][a-z-]*' "$SENSORS_REPO_ROOT/$command" 2>/dev/null | awk '{ print $2 }' | sort -u)
  done <<< "$(jq -r '.rows[].cmd' "$registry")"
}
