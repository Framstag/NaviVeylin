#!/usr/bin/env bash
# The shell half of the issue tooling's self-test: every case drives the real wrappers against the stub
# `gh` on PATH, so the whole surface is exercised with no token and no network (design D9).
#
# Sourced by `tools/issue-tooling-selftest.sh`, which provides the environment:
#   ISSUES_REPO_ROOT  the repository under test        ISSUES_ENGINE   tools/issues
#   ISSUES_FIXTURES   tools/issues/fixtures            ISSUES_TMP      a scratch directory
# and puts the stub `gh` first on PATH.
#
# A case runs in a subshell; it prints nothing on success and a reason on failure, exiting non-zero.
set -uo pipefail

ISSUES_SHELL_CASES=(
  'labels#dryRunListsTaxonomy'
  'lib#discoversTheCurrentProject'
  'lib#refusesOutsideAGitTree'
  'lib#noProjectValueInTheEngine'
  'lib#exitCodeConstants'
  'lib#coreIsCompiledNotLaunched'
  'policy#preflightRefusesUnknownLogin'
  'create#duplicateRefusesAndNamesTheIssue'
  'create#writesMarkerAndLabels'
  'update#refusesUnknownLabel'
  'close#requiresReasonAndEvidence'
  'close#recordsStateReason'
  'close#refusesOnPolicyFailure'
  'list#filtersByEachCriterion'
  'list#idResolvesTheMarker'
  'list#idDoesNotMatchASubstring'
  'surface#noDeletePath'
  'surface#reservedLabelRefusesInEveryWritePath'
  'surface#reservationIsNotBypassedByTheOtherLabelFlags'
  'import#dryRunEmitsOnePreviewPerFinding'
  'import#secondRunIsANoOp'
  'adapter#emitsOneFindingPerFixtureSection'
)

issues_shell_cases() {
  printf '%s\n' "${ISSUES_SHELL_CASES[@]}"
}

issues_run_shell_case() {
  case "$1" in
    'labels#dryRunListsTaxonomy') case_labels_dryRunListsTaxonomy ;;
    'lib#discoversTheCurrentProject') case_lib_discoversTheCurrentProject ;;
    'lib#refusesOutsideAGitTree') case_lib_refusesOutsideAGitTree ;;
    'lib#noProjectValueInTheEngine') case_lib_noProjectValueInTheEngine ;;
    'lib#exitCodeConstants') case_lib_exitCodeConstants ;;
    'lib#coreIsCompiledNotLaunched') case_lib_coreIsCompiledNotLaunched ;;
    'policy#preflightRefusesUnknownLogin') case_policy_preflightRefusesUnknownLogin ;;
    'create#duplicateRefusesAndNamesTheIssue') case_create_duplicateRefusesAndNamesTheIssue ;;
    'create#writesMarkerAndLabels') case_create_writesMarkerAndLabels ;;
    'update#refusesUnknownLabel') case_update_refusesUnknownLabel ;;
    'close#requiresReasonAndEvidence') case_close_requiresReasonAndEvidence ;;
    'close#recordsStateReason') case_close_recordsStateReason ;;
    'close#refusesOnPolicyFailure') case_close_refusesOnPolicyFailure ;;
    'list#filtersByEachCriterion') case_list_filtersByEachCriterion ;;
    'list#idResolvesTheMarker') case_list_idResolvesTheMarker ;;
    'list#idDoesNotMatchASubstring') case_list_idDoesNotMatchASubstring ;;
    'surface#noDeletePath') case_surface_noDeletePath ;;
    'surface#reservedLabelRefusesInEveryWritePath') case_surface_reservedLabelRefusesInEveryWritePath ;;
    'surface#reservationIsNotBypassedByTheOtherLabelFlags') case_surface_reservationIsNotBypassed ;;
    'import#dryRunEmitsOnePreviewPerFinding') case_import_dryRunEmitsOnePreviewPerFinding ;;
    'import#secondRunIsANoOp') case_import_secondRunIsANoOp ;;
    'adapter#emitsOneFindingPerFixtureSection') case_adapter_emitsOneFindingPerFixtureSection ;;
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

run_tool() {
  local script="$1"; shift
  bash "$ISSUES_ENGINE/$script" "$@"
}

core() { # the classes the driver already compiled — never the single-file source launcher
  local class="$1"; shift
  java -cp "$ISSUES_CORE_CLASSES" "$class" "$@"
}

scratch() {
  mktemp -d "$ISSUES_TMP/case.XXXXXX"
}

scenario() {
  printf '%s\n' "$ISSUES_FIXTURES/scenarios/$1"
}

profile_path() {
  printf '%s\n' "$ISSUES_REPO_ROOT/tools/tooling.json"
}

reserved_label() {
  core Profile "$(profile_path)" reservedLabels | head -1
}

login() {
  cat "$ISSUES_FIXTURES/login.txt"
}

# The finding every write-path case files.
finding_json() {
  jq -n '{sourceId: "903", title: "§903 a fixture finding", class: "bug", category: "ui",
          claim: "A fixture claim about the phone card header", anchor: "app/src/main/Card.kt:Header",
          labels: ["class/bug", "category/ui", "status/open", "source/agent"],
          body: "- **Observed** ✗: the two numbers disagree."}'
}

# ----------------------------------------------------------------------------- cases

case_labels_dryRunListsTaxonomy() {
  export GH_STUB_DIR="$ISSUES_FIXTURES"
  local profile expected out got
  profile=$(profile_path)
  expected=$(
    core Profile "$profile" namespaces | while read -r namespace; do
      core Profile "$profile" "namespaces.$namespace" | while read -r value; do
        printf '%s/%s\n' "$namespace" "$value"
      done
    done | sort -u
  )
  out=$(run_tool labels.sh --json) || fail "labels.sh --json failed: $out"
  got=$(jq -r '.toCreate[]' <<< "$out" | sort -u)
  assert_eq "$expected" "$got" "the dry-run diff is exactly the taxonomy"

  # Idempotence: apply, rebuild the tracker state from what was requested, then re-run.
  local dir log tracker
  dir=$(scratch); log="$dir/calls.log"; tracker="$dir/labels.json"
  : > "$log"
  GH_STUB_RECORD="$log" run_tool labels.sh --apply >/dev/null || fail "labels.sh --apply failed"
  jq -n --argjson names "$(awk '{print $4}' "$log" | sort -u | jq -R . | jq -s .)" \
    '[ $names[] | {name: ., color: "cccccc", description: ""} ]' > "$tracker"
  out=$(GH_STUB_DIR="$dir" run_tool labels.sh --json) || fail "the second dry run failed: $out"
  assert_eq "0" "$(jq -r '.toCreate | length' <<< "$out")" "a second run prints an empty diff"
}

case_lib_discoversTheCurrentProject() {
  export GH_STUB_DIR="$ISSUES_FIXTURES"
  local dir project out nested wt
  dir=$(scratch); project="$dir/project"
  mkdir -p "$project/tools"
  cp "$(profile_path)" "$project/tools/tooling.json"
  git -C "$project" init -q . >/dev/null 2>&1 || fail "could not create the throwaway repository"
  git -C "$project" add -A >/dev/null 2>&1
  git -C "$project" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm init >/dev/null 2>&1 \
    || fail "could not commit in the throwaway repository"

  out=$(cd "$project" && run_tool lib.sh discover) || fail "discovery failed: $out"
  assert_eq "$project" "$(sed -n 's/^root=//p' <<< "$out")" "the discovered root"
  assert_eq "$project/tools/tooling.json" "$(sed -n 's/^profile=//p' <<< "$out")" "the discovered profile path"
  assert_eq "$(cat "$ISSUES_FIXTURES/repo.txt")" "$(sed -n 's/^repo=//p' <<< "$out")" "the discovered repo"

  # Invoked from a subdirectory of the project, the same project is resolved.
  mkdir -p "$project/tools/issues"
  out=$(cd "$project/tools/issues" && run_tool lib.sh discover) || fail "discovery from a subdirectory failed: $out"
  assert_eq "$project" "$(sed -n 's/^root=//p' <<< "$out")" "the root resolved from a subdirectory"
  assert_eq "$project/tools/tooling.json" "$(sed -n 's/^profile=//p' <<< "$out")" "the profile resolved from a subdirectory"

  # A linked working tree uses its OWN profile, never the main checkout's.
  wt="$dir/worktree"
  git -C "$project" worktree add -q "$wt" -b second >/dev/null 2>&1 || fail "could not create a linked worktree"
  printf '%s\n' '{ "repo": "linked/worktree", "marker": "linked" }' > "$wt/tools/tooling.json"
  out=$(cd "$wt" && run_tool lib.sh discover) || fail "discovery inside a linked worktree failed: $out"
  assert_eq "$wt/tools/tooling.json" "$(sed -n 's/^profile=//p' <<< "$out")" "a linked worktree uses its own profile"

  # The environment does not decide the project: only an explicit argument overrides the working tree.
  out=$(cd "$project" && ISSUES_REPO="other/other" ISSUES_PROFILE="$dir/nope.json" GITHUB_REPOSITORY="other/other" \
    run_tool lib.sh discover) || fail "discovery with a hostile environment failed: $out"
  assert_eq "$project" "$(sed -n 's/^root=//p' <<< "$out")" "an environment variable does not move the root"
  assert_eq "$project/tools/tooling.json" "$(sed -n 's/^profile=//p' <<< "$out")" "an environment variable does not move the profile"
}

case_lib_refusesOutsideAGitTree() {
  local dir status out
  dir=$(scratch)
  out=$(cd "$dir" && run_tool lib.sh discover 2>&1)
  status=$?
  assert_eq "2" "$status" "the exit code outside a project"
  assert_contains "$out" "not inside a git working tree" "the message outside a project"
  out=$(cd "$dir" && run_tool lib.sh discover --profile "$(profile_path)" 2>&1)
  status=$?
  assert_eq "2" "$status" "an explicit profile does not remove the need for a working tree"
}

case_lib_noProjectValueInTheEngine() {
  local engine files hits value
  engine="$ISSUES_ENGINE"
  # The mechanism: the wrappers, the reconciler and the core. `Cases.java` is the case runner and the
  # fixtures file under fixtures/ are expectations by construction, so they are not part of the claim.
  files=$(ls "$engine"/*.sh "$engine"/import.sh "$engine"/core/*.java | grep -v '/Cases.java$')

  hits=$(grep -rn 'Framstag\|Framstag/NaviVeylin\|naviveylin\|NaviVeylin' $files 2>/dev/null || true)
  [ -z "$hits" ] || fail "a project value appears in the engine: $hits"

  # The second half of the invariant: no value of the profile's vocabularies is named by the engine either.
  # A comment may quote one; code may not.
  while IFS= read -r value; do
    [ -n "$value" ] || continue
    hits=$(grep -nw -- "$value" $files 2>/dev/null | grep -vE ':[[:space:]]*(#|//|\*|/\*)' || true)
    [ -z "$hits" ] || fail "the engine names the profile value '$value' instead of reading it: $hits"
  done < <(core Profile "$(profile_path)" namespaces.category)
}

case_lib_exitCodeConstants() {
  local values
  values=$(
    # shellcheck source=tools/issues/lib.sh
    source "$ISSUES_ENGINE/lib.sh"
    printf '%s %s %s %s\n' "$ISSUES_EXIT_OK" "$ISSUES_EXIT_USAGE" "$ISSUES_EXIT_POLICY" "$ISSUES_EXIT_DUPLICATE"
  )
  assert_eq "0 2 3 4" "$values" "the exit-code contract"
}

case_policy_preflightRefusesUnknownLogin() {
  export GH_STUB_DIR="$(scenario outsider)"
  local out status
  out=$(run_tool policy.sh preflight 2>&1)
  status=$?
  assert_eq "3" "$status" "preflight against a foreign identity"
  assert_contains "$out" "author-not-allowlisted" "the refusal reason code"
  out=$(run_tool policy.sh preflight --json 2>&1)
  assert_eq "refuse" "$(jq -r '.decision' <<< "$out")" "the JSON decision"

  # …and the allowlisted identity must still pass, so a profile whose allowlist has drifted or been emptied
  # turns this case red rather than being followed.
  out=$(GH_STUB_DIR="$ISSUES_FIXTURES" run_tool policy.sh preflight 2>&1)
  status=$?
  assert_eq "0" "$status" "preflight allows the authenticated actor: $out"
}

case_create_duplicateRefusesAndNamesTheIssue() {
  local dir claim fingerprint finding out status
  dir=$(scratch); mkdir -p "$dir"
  claim="A fixture claim about the phone card header"
  fingerprint=$(core Fingerprint "class=bug" "category=ui" "claim=$claim" "anchor=app/src/main/Card.kt:Header")
  jq -n --arg fp "$fingerprint" \
    '[{number: 42, title: "an issue that already exists",
       body: ("<!-- fingerprint: " + $fp + " -->"), author: {login: "Framstag"},
       labels: [{name: "class/bug"}, {name: "category/ui"}]}]' > "$dir/issues.json"
  export GH_STUB_DIR="$dir"
  finding=$(finding_json)
  out=$(printf '%s' "$finding" | run_tool create.sh 2>&1)
  status=$?
  assert_eq "4" "$status" "a duplicate is reported, not filed"
  assert_contains "$out" "42" "the existing issue is named"

  # A deliberate duplicate is possible, and the override is recorded rather than silent.
  log="$dir/calls.log"; : > "$log"
  export GH_STUB_RECORD="$log"
  out=$(printf '%s' "$finding" | run_tool create.sh --allow-duplicate 2>&1)
  status=$?
  assert_eq "0" "$status" "--allow-duplicate files the finding anyway"
  assert_contains "$out" "--allow-duplicate overrides" "the override is recorded in the output"
  assert_eq "1" "$(grep -c 'issue create' "$log")" "exactly one issue is created with the override"
}

case_create_writesMarkerAndLabels() {
  export GH_STUB_DIR="$ISSUES_FIXTURES"
  local dir log finding line
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  export GH_STUB_RECORD="$log"
  finding=$(jq -n '{sourceId: "129", title: "§129 a fixture finding", class: "bug",
                    category: "route-and-navigation", claim: "A claim", anchor: "app/src/main/X.kt:Y",
                    labels: ["class/bug", "category/route-and-navigation", "status/open", "source/todo-import"],
                    body: "the body, verbatim"}')
  printf '%s' "$finding" | run_tool create.sh --json >/dev/null || fail "create.sh failed"
  line=$(grep 'issue create' "$log" | head -1)
  [ -n "$line" ] || fail "no gh issue create invocation was recorded"
  assert_contains "$line" "nvid: 129" "the source identity marker"
  assert_contains "$line" "fingerprint: " "the fingerprint marker"
  local label
  for label in class/bug category/route-and-navigation status/open source/todo-import; do
    assert_contains "$line" "--label $label" "the label set must carry $label"
  done
  assert_eq "1" "$(grep -c 'issue create' "$log")" "exactly one issue is created"
}

case_update_refusesUnknownLabel() {
  export GH_STUB_DIR="$ISSUES_FIXTURES"
  local dir log out status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  export GH_STUB_RECORD="$log"
  # `wibble` is outside the profile's `category/` vocabulary; the revert-check log mutates exactly that set,
  # so this case reads the same namespace the profile owns.
  out=$(run_tool update.sh 11 --label category/wibble 2>&1)
  status=$?
  assert_eq "3" "$status" "an unknown label is refused"
  assert_contains "$out" "label-opaque" "the refusal reason code"
  [ ! -s "$log" ] || fail "a gh issue edit call was made: $(cat "$log")"
}

case_close_requiresReasonAndEvidence() {
  export GH_STUB_DIR="$(scenario eligible)"
  local dir log out status comment close
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  export GH_STUB_RECORD="$log"

  out=$(run_tool close.sh 21 --reason "fixed" 2>&1); status=$?
  assert_eq "2" "$status" "a closure without evidence is refused"
  out=$(run_tool close.sh 21 --evidence "the case is green" 2>&1); status=$?
  assert_eq "2" "$status" "a closure without a reason is refused"
  [ ! -s "$log" ] || fail "a refused closure posted something: $(cat "$log")"

  out=$(run_tool close.sh 21 --reason "fixed" --evidence "the case is green" 2>&1); status=$?
  assert_eq "0" "$status" "a closure with both succeeds: $out"
  comment=$(grep -n 'issue comment' "$log" | cut -d: -f1)
  close=$(grep -n 'issue close' "$log" | cut -d: -f1)
  [ -n "$comment" ] || fail "the reason was not posted as a comment"
  [ -n "$close" ] || fail "the issue was not closed"
  [ "$comment" -lt "$close" ] || fail "the reason must be recorded before the closure"
}

case_close_recordsStateReason() {
  export GH_STUB_DIR="$(scenario eligible)"
  local dir log
  dir=$(scratch); log="$dir/calls.log"
  export GH_STUB_RECORD="$log"
  run_tool close.sh 21 --reason r --evidence e >/dev/null || fail "the default closure failed"
  grep -q -- '--reason completed' "$log" || fail "the default state reason must be completed"
  : > "$log"
  run_tool close.sh 21 --reason r --evidence e --not-planned >/dev/null || fail "the not-planned closure failed"
  grep -q -- '--reason not_planned' "$log" || fail "the explicit flag must record not_planned"
}

case_close_refusesOnPolicyFailure() {
  export GH_STUB_DIR="$(scenario foreign-author)"
  local dir log out status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  export GH_STUB_RECORD="$log"
  out=$(run_tool close.sh 31 --reason r --evidence e 2>&1)
  status=$?
  assert_eq "3" "$status" "an issue that fails the gate is not closed"
  assert_contains "$out" "author-not-allowlisted" "the refusal names the author condition"
  [ ! -s "$log" ] || fail "nothing may be posted when the gate fails: $(cat "$log")"
}

case_list_filtersByEachCriterion() {
  export GH_STUB_DIR="$(scenario three)"
  local out
  out=$(run_tool list.sh --class bug --json) || fail "list --class failed"
  assert_eq "11" "$(jq -r '.[].number' <<< "$out")" "the class filter"
  out=$(run_tool list.sh --category ui --json) || fail "list --category failed"
  assert_eq "12" "$(jq -r '.[].number' <<< "$out")" "the category filter"
  out=$(run_tool list.sh --status open-device --json) || fail "list --status failed"
  assert_eq "13" "$(jq -r '.[].number' <<< "$out")" "the status filter"
  out=$(run_tool list.sh --author "$(login)" --json) || fail "list --author failed"
  assert_eq "3" "$(jq 'length' <<< "$out")" "the author filter"
  out=$(run_tool list.sh --eligible-only --json) || fail "list --eligible-only failed"
  assert_eq "11" "$(jq -r '.[].number' <<< "$out")" "the eligibility filter"
}

case_surface_noDeletePath() {
  local out
  out=$(grep -rn 'issue delete\|eval' "$ISSUES_ENGINE" 2>/dev/null || true)
  [ -z "$out" ] || fail "the surface must offer no deletion and no eval: $out"
}

case_surface_reservedLabelRefusesInEveryWritePath() {
  export GH_STUB_DIR="$ISSUES_FIXTURES"
  local dir log reserved finding doc out status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  export GH_STUB_RECORD="$log"
  reserved=$(reserved_label)
  finding=$(finding_json)

  out=$(printf '%s' "$finding" | run_tool create.sh --label "$reserved" 2>&1); status=$?
  assert_eq "3" "$status" "create.sh refuses a reserved label"
  assert_contains "$out" "label-reserved" "the refusal reason code from create.sh"

  out=$(run_tool update.sh 11 --label "$reserved" 2>&1); status=$?
  assert_eq "3" "$status" "update.sh refuses a reserved label"
  assert_contains "$out" "label-reserved" "the refusal reason code from update.sh"

  doc="$dir/findings.json"
  jq --arg reserved "$reserved" '.findings[0].labels += [$reserved]' \
    "$ISSUES_FIXTURES/findings.json" > "$doc"
  out=$(run_tool import.sh --findings "$doc" --apply 2>&1); status=$?
  assert_eq "3" "$status" "import.sh refuses a reserved label"
  assert_contains "$out" "label-reserved" "the refusal reason code from import.sh"

  [ ! -s "$log" ] || fail "no write may reach the tracker: $(cat "$log")"
}

case_surface_reservationIsNotBypassed() {
  export GH_STUB_DIR="$ISSUES_FIXTURES"
  local dir log reserved finding out status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  export GH_STUB_RECORD="$log"
  reserved=$(reserved_label)
  finding=$(finding_json)

  out=$(printf '%s' "$finding" | run_tool create.sh --label "$reserved" 2>&1); status=$?
  assert_eq "3" "$status" "the --label route is refused"
  assert_contains "$out" "label-reserved" "the --label route names the reservation"

  # A namespaced string is not a status value, so this route is refused by the vocabulary — the point is
  # that no route can set the reserved label, not that one flag is guarded.
  out=$(printf '%s' "$finding" | run_tool create.sh --status "$reserved" 2>&1); status=$?
  assert_eq "3" "$status" "the --status route is refused"

  out=$(jq --arg reserved "$reserved" '.labels += [$reserved]' <<< "$finding" | run_tool create.sh 2>&1); status=$?
  assert_eq "3" "$status" "the JSON payload route is refused"
  assert_contains "$out" "label-reserved" "the JSON payload route names the reservation"

  [ ! -s "$log" ] || fail "no write may reach the tracker: $(cat "$log")"
}

# `--id N` is the resolution a `§N` citation depends on after the source record is gone (design D3): the
# marker is the identity, so exactly one issue answers, and a number that is a prefix of another entry's
# marker must not answer for it.
# The core must be run from a class path, never handed to the single-file source launcher: `java core/Foo.java`
# resolves Foo's siblings on some JDKs and not on others (JDK 27 yes, JDK 21 no — measured 2026-10-17, when
# every core-backed script passed here and 13 of 33 cases failed in CI). The structural half of this case is
# what fails on either JDK; the behavioural half proves the compiled path really runs.
case_lib_coreIsCompiledNotLaunched() {
  local calls offenders out
  # every `java` invocation in the tooling — the engine, the driver and its cases — is a `java` command word
  # at the start of a line, so a `*.java | sha256sum` data line cannot match it
  calls=$(grep -rnE '^[[:space:]]*java[[:space:]]' "$ISSUES_ENGINE" \
      "$ISSUES_REPO_ROOT/tools/issue-tooling-selftest.sh" \
      "$ISSUES_REPO_ROOT/tools/issue-tooling-cases.sh" 2>/dev/null | grep -vE 'javac' || true)
  [ -n "$calls" ] || fail "no java invocation found in the tooling — the check would be vacuous"
  offenders=$(printf '%s\n' "$calls" | grep -v -- '-cp' || true)
  [ -z "$offenders" ] || fail "the core is not run from a class path: $offenders"
  if printf '%s\n' "$calls" | grep -q '\.java'; then
    fail "the core is handed to the single-file source launcher: $(printf '%s\n' "$calls" | grep '\.java')"
  fi

  # and it really runs: a core-backed script invoked from a directory that is not the core's own
  out=$(cd "$ISSUES_ENGINE" && run_tool list.sh --class bug --json) ||\
    fail "a core-backed script failed when invoked from another directory"
  assert_eq "0" "$(jq 'length' <<< "$out")" "the empty tracker answers with an empty selection"
}

case_list_idResolvesTheMarker() {
  export GH_STUB_DIR="$(scenario nvid)"
  local out status
  out=$(run_tool list.sh --id 129 --json) || fail "list --id 129 failed"
  assert_eq "1" "$(jq 'length' <<< "$out")" "--id 129 answers with exactly one issue"
  assert_eq "129" "$(jq -r '.[0].number' <<< "$out")" "the issue carrying that marker"

  out=$(run_tool list.sh --id 999 2>&1)
  status=$?
  assert_eq "4" "$status" "an id no issue carries is a reported no-op"
  assert_contains "$out" "nvid: 999" "the message names the marker that resolved to nothing"
}

case_list_idDoesNotMatchASubstring() {
  export GH_STUB_DIR="$(scenario nvid)"
  local out status
  # `12` is a prefix of `129` and of nothing else in the fixture: the match is on the delimiters, so only
  # #12 may answer for --id 12.
  out=$(run_tool list.sh --id 12 --json) || fail "list --id 12 failed"
  assert_eq "1" "$(jq 'length' <<< "$out")" "--id 12 answers with exactly one issue"
  assert_eq "12" "$(jq -r '.[0].number' <<< "$out")" "--id 12 must not match <!-- nvid: 129 -->"

  out=$(run_tool list.sh --id 1 2>&1)
  status=$?
  assert_eq "4" "$status" "--id 1 must not match <!-- nvid: 11 --> either"
}

case_import_dryRunEmitsOnePreviewPerFinding() {
  export GH_STUB_DIR="$ISSUES_FIXTURES"
  local dir log out status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  export GH_STUB_RECORD="$log"
  out=$(run_tool import.sh --findings "$ISSUES_FIXTURES/findings.json" --json)
  status=$?
  assert_eq "0" "$status" "a dry run over new findings succeeds"
  assert_eq "2" "$(jq -r '.findings' <<< "$out")" "every finding is counted"
  assert_eq "2" "$(jq -r '.previews' <<< "$out")" "one preview per finding"
  assert_eq "0" "$(jq -r '.skipped' <<< "$out")" "nothing is skipped on a first run"
  assert_eq "0" "$(jq -r --arg r "$(reserved_label)" '[.previewList[].labels[] | select(. == $r)] | length' <<< "$out")" \
    "no imported finding is loop-eligible on arrival"
  [ ! -s "$log" ] || fail "a dry run must not create anything: $(cat "$log")"
}

case_import_secondRunIsANoOp() {
  export GH_STUB_DIR="$(scenario present)"
  local dir log out status
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  export GH_STUB_RECORD="$log"
  out=$(run_tool import.sh --findings "$ISSUES_FIXTURES/findings.json" --json 2>&1)
  status=$?
  assert_eq "4" "$status" "a second run reports already-present, not a failure"
  assert_eq "0" "$(jq -r '.previews' <<< "$out")" "zero previews"
  assert_eq "2" "$(jq -r '.skipped' <<< "$out")" "both findings are already present"
  [ ! -s "$log" ] || fail "a re-import must not create anything: $(cat "$log")"

  # A partial identifier is not a match: the lookup is on the marker, not on its text.
  partial="$dir/partial.json"
  jq -c '{source: "fixture", count: 1, renames: [],
          findings: [(.findings[0] | .sourceId = "90")]}' \
    "$ISSUES_FIXTURES/findings.json" > "$partial"
  out=$(run_tool import.sh --findings "$partial" --json 2>&1)
  status=$?
  assert_eq "0" "$status" "a partial identifier is not already present"
  assert_eq "1" "$(jq -r '.previews' <<< "$out")" "the partial identifier is previewed"
  assert_eq "0" "$(jq -r '.skipped' <<< "$out")" "nothing is skipped for a partial identifier"
}

# The adapter is driven over committed fixtures, never over the live backlog file: that file is what the
# migration removed (`migrate-todo-to-github-issues`, D6), and the grammar it carries is exercised by a
# fixture just as well. `--todo <path>` is the adapter's own input, so no case here depends on a file
# outside the fixtures.
case_adapter_emitsOneFindingPerFixtureSection() {
  export GH_STUB_DIR="$ISSUES_FIXTURES"
  local dir log doc count out fixture rename_doc
  dir=$(scratch); log="$dir/calls.log"; : > "$log"
  export GH_STUB_RECORD="$log"

  fixture="$ISSUES_FIXTURES/todo-status-shapes.md"
  doc=$(run_tool import/todo-md.sh --todo "$fixture") || fail "the adapter failed"
  printf '%s' "$doc" | jq -e '.findings' >/dev/null 2>&1 || fail "the adapter output is not a findings document"
  count=$(jq -r '.count' <<< "$doc")
  assert_eq "$(jq -r '.findings | length' <<< "$doc")" "$count" "count matches the findings array"
  assert_eq "$(grep -cE '^## [0-9]+\.' "$fixture")" "$count" "count matches the fixture's sections"
  assert_eq "$(grep -c '^\*\*id:\*\*' "$fixture")" "$count" "count matches the fixture's metadata lines"
  assert_eq "0" "$(jq -r '.renames | length' <<< "$doc")" "a fixture in the profile's vocabulary needs no rename"

  out=$(run_tool import.sh --json <<< "$doc") || fail "the reconciler failed on the adapter's output"
  assert_eq "$count" "$(jq -r '.previews' <<< "$out")" "one preview per finding through the adapter"
  [ ! -s "$log" ] || fail "the dry run must not create anything: $(cat "$log")"

  rename_doc=$(run_tool import/todo-md.sh --todo "$ISSUES_FIXTURES/todo-renames.md") || fail "the adapter failed on the rename fixture"
  assert_eq "2" "$(jq -r '.renames | length' <<< "$rename_doc")" "the adapter reports the two renames"
}
