#!/usr/bin/env bash
# The loop's shell cases: every case drives the real scripts against the stub `gh` (change 1) and the stub
# `pi`, inside a throwaway git repository created per case, so the queue, the branches, the worktree, the
# commits, the label transitions, the closure, the stop conditions and the refusals are exercised for real
# with no token, no network, no model and no device (design D13).
#
# Sourced by `tools/loop-selftest.sh`, which provides the environment:
#   LOOP_ENGINE          the loop's own directory (`tools/loop`)
#   LOOP_FIXTURES        `tools/loop/fixtures` (the stub `pi` and the scenario issues)
#   LOOP_ISSUE_FIXTURES  `tools/issues/fixtures` (the stub `gh`, the profile)
#   LOOP_PROJECT_ROOT    the real repository (for the profile and the structural checks)
#   ISSUES_CORE_CLASSES  the Java core, already compiled by the driver
#   LOOP_TMP             a scratch directory under the gitignored `.pi/tmp/`
# and puts the stub `pi` and the stub `gh` first on PATH.
#
# A case runs in a subshell; it prints nothing on success and a reason on failure, exiting non-zero.
set -uo pipefail

LOOP_SHELL_CASES=(
  'state#initAndShowRoundTrips'
  'state#nextHonoursEveryStopCondition'
  'state#secondRunRefusesWithExit5'
  'state#defaultsComeFromLoopConf'
  'state#projectKeysAreReadFromLoopConf'
  'state#agentCommandIsTheOnlyIndirection'
  'state#agentCommandRefusesAnEmptyValue'
  'prescreen#ranksBugsFirstAndDropsFeatures'
  'prescreen#missingPathYieldsStale'
  'prescreen#doesNotClaimJudgementGates'
  'prescreen#refusesReservedLabelInSelection'
  'prescreen#withoutTheOptInIsNeverQueued'
  'prescreen#settledItemIsDropped'
  'prescreen#deviceItemIsAVerdictNotWork'
  'prescreen#readsTheVerdictsFromTheProfile'
  'prescreen#previousAttemptBranchIsADecision'
  'prescreen#itemAwaitingADecisionIsNotQueued'
  'prescreen#aHoldWithNoDecisionIsNotADecision'
  'prescreen#harnessItemsLeadTheQueue'
  'prescreen#aRenamedLeadingCategoryLeads'
  'prescreen#priorityCategoriesAreReadFromTheProfile'
  'prescreen#priorityDoesNotBypassTheGate'
  'prescreen#anUndeclaredRoleStopsTheScreen'
  'prescreen#escalatedItemKeepsItsBranchAsEvidence'
  'prescreen#refusalLeavesATraceInTheTracker'
  'worktree#seedsAllFourInputs'
  'worktree#checksOutTheSubmoduleTheLoopConfigNames'
  'worktree#oneBranchPerItemOffTheBase'
  'worktree#refusesWhenMainAdvanced'
  'worktree#keepsByDefaultAndRemovesOnRequest'
  'worktree#noPushWithoutTheFlag'
  'worktree#reusesABranchThatCarriesNoWorkOfItsOwn'
  'worktree#refusesABranchCarryingAPreviousAttempt'
  'worktree#refusesABranchWhoseReviewFailed'
  'labels#verdictToLabelMatrix'
  'labels#holdCarriesItsReason'
  'labels#trapRemovesInProgressOnSignal'
  'labels#neverWritesReservedLabel'
  'labels#writesOnlyBetweenChildren'
  'prompt#workerNamesEveryPhaseAndSkill'
  'prompt#workerNamesTheDecisionField'
  'prompt#reviewerCarriesTheEightItems'
  'prompt#packageIsAFileAndNotInlined'
  'prompt#inlineInjectionCannotReachTheInstructionPosition'
  'run#preflightRefusalStopsBeforeAnyWork'
  'run#decisionCapRefusesToStart'
  'run#oneItemOrderIsFixed'
  'run#childClaimWithoutCommitIsBlocked'
  'run#blockedItemIsEscalatedWithItsReason'
  'run#childStartsInTheProjectRoot'
  'run#childReportsWhileItWaits'
  'run#childWriteOutsideTheWorktreeIsBlocked'

  'run#reviewerWriteInTheProjectIsDiscarded'
  'run#leftoverBranchIsRecordedNotDropped'
  'run#branchThatAppearsWhileTheRunIsOpenIsRecorded'
  'run#mergesAFinishedAttemptAndClosesTheItem'
  'run#doesNotMergeAnAttemptWithoutAGreenVerification'
  'run#recordsAFailedReviewOnTheBranch'
  'run#dirtyKeptWorktreeIsARecordedRefusal'
  'run#resetWorktreeDiscardsWhatItNames'
  'run#aSkipClearsTheInProgressMark'
  'run#skipEscalationCarriesTheDecision'
  'run#claimedGateWithoutEvidenceIsBlocked'
  'run#citedEvidenceMustTravelWithTheChange'
  'run#needsDecisionIsEscalatedWithTheWork'
  'run#noGateStillRuns'
  'run#dryRunTouchesNothing'
  'run#reviewRoundsCapAtTwo'
  'run#reviewBudgetIsARecordedValue'
  'run#aZeroRoundBudgetIsRefused'
  'run#aCorrectiveRefusalEarnsOneMoreRound'
  'run#escalationCarriesTheDecision'
  'run#mayCloseNoLeavesTheIssueOpen'
  'run#metricsRecordedPerItem'
  'run#emptyQueueIsNotAFailure'
  'run#stopReasonIsPrintedAndReported'
  'run#awaitingDecisionIsNotEscalatedAgain'
  'run#commitsWithoutAnAmbientIdentity'
  'run#reviewerWriteInvalidatesTheRound'
  'run#reviewReadOnlyOffOnlyForDiagnosis'
  'run#engineNamesNoProjectFact'
  'report#headerAndTableShape'
  'report#yieldIsCountedFromState'
  'report#escalatedSkipNamesTheLabelWritten'
  'report#failedReviewEscalationIsNamed'
  'report#aVerdictAloneIsNoEscalation'
)

loop_shell_cases() {
  printf '%s\n' "${LOOP_SHELL_CASES[@]}"
}

# The dispatcher resolves a name to a function and then returns 0 unconditionally: a case signals failure
# by calling `fail` (which exits the case's subshell), never by the status of its last statement.
loop_run_shell_case() {
  local fn
  case "$1" in
    'state#initAndShowRoundTrips') fn=case_state_initAndShowRoundTrips ;;
    'state#nextHonoursEveryStopCondition') fn=case_state_nextHonoursEveryStopCondition ;;
    'state#secondRunRefusesWithExit5') fn=case_state_secondRunRefusesWithExit5 ;;
    'state#defaultsComeFromLoopConf') fn=case_state_defaultsComeFromLoopConf ;;
    'state#projectKeysAreReadFromLoopConf') fn=case_state_projectKeysAreReadFromLoopConf ;;
    'state#agentCommandIsTheOnlyIndirection') fn=case_state_agentCommandIsTheOnlyIndirection ;;
    'state#agentCommandRefusesAnEmptyValue') fn=case_state_agentCommandRefusesAnEmptyValue ;;
    'prescreen#ranksBugsFirstAndDropsFeatures') fn=case_prescreen_ranksBugsFirstAndDropsFeatures ;;
    'prescreen#missingPathYieldsStale') fn=case_prescreen_missingPathYieldsStale ;;
    'prescreen#doesNotClaimJudgementGates') fn=case_prescreen_doesNotClaimJudgementGates ;;
    'prescreen#refusesReservedLabelInSelection') fn=case_prescreen_refusesReservedLabelInSelection ;;
    'prescreen#withoutTheOptInIsNeverQueued') fn=case_prescreen_withoutTheOptInIsNeverQueued ;;
    'prescreen#settledItemIsDropped') fn=case_prescreen_settledItemIsDropped ;;
    'prescreen#deviceItemIsAVerdictNotWork') fn=case_prescreen_deviceItemIsAVerdictNotWork ;;
    'prescreen#readsTheVerdictsFromTheProfile') fn=case_prescreen_readsTheVerdictsFromTheProfile ;;
    'prescreen#previousAttemptBranchIsADecision') fn=case_prescreen_previousAttemptBranchIsADecision ;;
    'prescreen#itemAwaitingADecisionIsNotQueued') fn=case_prescreen_itemAwaitingADecisionIsNotQueued ;;
    'prescreen#aHoldWithNoDecisionIsNotADecision') fn=case_prescreen_aHoldWithNoDecisionIsNotADecision ;;
    'prescreen#harnessItemsLeadTheQueue') fn=case_prescreen_harnessItemsLeadTheQueue ;;
    'prescreen#aRenamedLeadingCategoryLeads') fn=case_prescreen_aRenamedLeadingCategoryLeads ;;
    'prescreen#priorityCategoriesAreReadFromTheProfile') fn=case_prescreen_priorityCategoriesAreReadFromTheProfile ;;
    'prescreen#priorityDoesNotBypassTheGate') fn=case_prescreen_priorityDoesNotBypassTheGate ;;
    'prescreen#anUndeclaredRoleStopsTheScreen') fn=case_prescreen_anUndeclaredRoleStopsTheScreen ;;
    'prescreen#escalatedItemKeepsItsBranchAsEvidence') fn=case_prescreen_escalatedItemKeepsItsBranchAsEvidence ;;
    'prescreen#refusalLeavesATraceInTheTracker') fn=case_prescreen_refusalLeavesATraceInTheTracker ;;
    'worktree#seedsAllFourInputs') fn=case_worktree_seedsAllFourInputs ;;
    'worktree#checksOutTheSubmoduleTheLoopConfigNames') fn=case_worktree_checksOutTheSubmoduleTheLoopConfigNames ;;
    'worktree#oneBranchPerItemOffTheBase') fn=case_worktree_oneBranchPerItemOffTheBase ;;
    'worktree#refusesWhenMainAdvanced') fn=case_worktree_refusesWhenMainAdvanced ;;
    'worktree#keepsByDefaultAndRemovesOnRequest') fn=case_worktree_keepsByDefaultAndRemovesOnRequest ;;
    'worktree#noPushWithoutTheFlag') fn=case_worktree_noPushWithoutTheFlag ;;
    'worktree#reusesABranchThatCarriesNoWorkOfItsOwn') fn=case_worktree_reusesABranchThatCarriesNoWorkOfItsOwn ;;
    'worktree#refusesABranchCarryingAPreviousAttempt') fn=case_worktree_refusesABranchCarryingAPreviousAttempt ;;
    'worktree#refusesABranchWhoseReviewFailed') fn=case_worktree_refusesABranchWhoseReviewFailed ;;
    'labels#verdictToLabelMatrix') fn=case_labels_verdictToLabelMatrix ;;
    'labels#holdCarriesItsReason') fn=case_labels_holdCarriesItsReason ;;
    'labels#trapRemovesInProgressOnSignal') fn=case_labels_trapRemovesInProgressOnSignal ;;
    'labels#neverWritesReservedLabel') fn=case_labels_neverWritesReservedLabel ;;
    'labels#writesOnlyBetweenChildren') fn=case_labels_writesOnlyBetweenChildren ;;
    'prompt#workerNamesEveryPhaseAndSkill') fn=case_prompt_workerNamesEveryPhaseAndSkill ;;
    'prompt#workerNamesTheDecisionField') fn=case_prompt_workerNamesTheDecisionField ;;
    'prompt#reviewerCarriesTheEightItems') fn=case_prompt_reviewerCarriesTheEightItems ;;
    'prompt#packageIsAFileAndNotInlined') fn=case_prompt_packageIsAFileAndNotInlined ;;
    'prompt#inlineInjectionCannotReachTheInstructionPosition') fn=case_prompt_inlineInjectionCannotReachTheInstructionPosition ;;
    'run#preflightRefusalStopsBeforeAnyWork') fn=case_run_preflightRefusalStopsBeforeAnyWork ;;
    'run#decisionCapRefusesToStart') fn=case_run_decisionCapRefusesToStart ;;
    'run#oneItemOrderIsFixed') fn=case_run_oneItemOrderIsFixed ;;
    'run#childClaimWithoutCommitIsBlocked') fn=case_run_childClaimWithoutCommitIsBlocked ;;
    'run#blockedItemIsEscalatedWithItsReason') fn=case_run_blockedItemIsEscalatedWithItsReason ;;
    'run#childStartsInTheProjectRoot') fn=case_run_childStartsInTheProjectRoot ;;
    'run#childReportsWhileItWaits') fn=case_run_childReportsWhileItWaits ;;
    'run#childWriteOutsideTheWorktreeIsBlocked') fn=case_run_childWriteOutsideTheWorktreeIsBlocked ;;
    'run#reviewerWriteInTheProjectIsDiscarded') fn=case_run_reviewerWriteInTheProjectIsDiscarded ;;
    'run#leftoverBranchIsRecordedNotDropped') fn=case_run_leftoverBranchIsRecordedNotDropped ;;
    'run#branchThatAppearsWhileTheRunIsOpenIsRecorded') fn=case_run_branchThatAppearsWhileTheRunIsOpenIsRecorded ;;
    'run#mergesAFinishedAttemptAndClosesTheItem') fn=case_run_mergesAFinishedAttemptAndClosesTheItem ;;
    'run#doesNotMergeAnAttemptWithoutAGreenVerification') fn=case_run_doesNotMergeAnAttemptWithoutAGreenVerification ;;
    'run#recordsAFailedReviewOnTheBranch') fn=case_run_recordsAFailedReviewOnTheBranch ;;
    'run#dirtyKeptWorktreeIsARecordedRefusal') fn=case_run_dirtyKeptWorktreeIsARecordedRefusal ;;
    'run#resetWorktreeDiscardsWhatItNames') fn=case_run_resetWorktreeDiscardsWhatItNames ;;
    'run#aSkipClearsTheInProgressMark') fn=case_run_aSkipClearsTheInProgressMark ;;
    'run#skipEscalationCarriesTheDecision') fn=case_run_skipEscalationCarriesTheDecision ;;
    'run#claimedGateWithoutEvidenceIsBlocked') fn=case_run_claimedGateWithoutEvidenceIsBlocked ;;
    'run#citedEvidenceMustTravelWithTheChange') fn=case_run_citedEvidenceMustTravelWithTheChange ;;
    'run#needsDecisionIsEscalatedWithTheWork') fn=case_run_needsDecisionIsEscalatedWithTheWork ;;
    'run#noGateStillRuns') fn=case_run_noGateStillRuns ;;
    'run#dryRunTouchesNothing') fn=case_run_dryRunTouchesNothing ;;
    'run#reviewRoundsCapAtTwo') fn=case_run_reviewRoundsCapAtTwo ;;
    'run#reviewBudgetIsARecordedValue') fn=case_run_reviewBudgetIsARecordedValue ;;
    'run#aZeroRoundBudgetIsRefused') fn=case_run_aZeroRoundBudgetIsRefused ;;
    'run#aCorrectiveRefusalEarnsOneMoreRound') fn=case_run_aCorrectiveRefusalEarnsOneMoreRound ;;
    'run#escalationCarriesTheDecision') fn=case_run_escalationCarriesTheDecision ;;
    'run#mayCloseNoLeavesTheIssueOpen') fn=case_run_mayCloseNoLeavesTheIssueOpen ;;
    'run#metricsRecordedPerItem') fn=case_run_metricsRecordedPerItem ;;
    'run#emptyQueueIsNotAFailure') fn=case_run_emptyQueueIsNotAFailure ;;
    'run#stopReasonIsPrintedAndReported') fn=case_run_stopReasonIsPrintedAndReported ;;
    'run#awaitingDecisionIsNotEscalatedAgain') fn=case_run_awaitingDecisionIsNotEscalatedAgain ;;
    'run#commitsWithoutAnAmbientIdentity') fn=case_run_commitsWithoutAnAmbientIdentity ;;
    'run#reviewerWriteInvalidatesTheRound') fn=case_run_reviewerWriteInvalidatesTheRound ;;
    'run#reviewReadOnlyOffOnlyForDiagnosis') fn=case_run_reviewReadOnlyOffOnlyForDiagnosis ;;
    'run#engineNamesNoProjectFact') fn=case_run_engineNamesNoProjectFact ;;
    'report#headerAndTableShape') fn=case_report_headerAndTableShape ;;
    'report#yieldIsCountedFromState') fn=case_report_yieldIsCountedFromState ;;
    'report#escalatedSkipNamesTheLabelWritten') fn=case_report_escalatedSkipNamesTheLabelWritten ;;
    'report#failedReviewEscalationIsNamed') fn=case_report_failedReviewEscalationIsNamed ;;
    'report#aVerdictAloneIsNoEscalation') fn=case_report_aVerdictAloneIsNoEscalation ;;
    *) printf 'no such shell case: %s\n' "$1" >&2; exit 2 ;;
  esac
  "$fn"
  return 0
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
    *"$2"*) fail "$3: '$2' should not appear in: $1" ;;
  esac
}

scratch() { mktemp -d "$LOOP_TMP/case.XXXXXX"; }

# A throwaway project: enough of this repository's shape that the loop's own scripts run unchanged — the
# profile the issue tooling discovers, a directory standing in for the submodule, a `local.prop*` the
# worktree does not inherit, and the gitignored skills it does not inherit either.
new_project() {
  local dir repo
  dir=$(scratch); repo="$dir/repo"
  mkdir -p "$repo/tools" "$repo/app/src/main/cpp/libosmscout" \
           "$repo/.pi/skills/fix-loop" "$repo/.pi/skills/openspec-apply-change" \
           "$repo/openspec/changes/archive"
  cp "$LOOP_PROJECT_ROOT/tools/tooling.json" "$repo/tools/tooling.json"
  printf '.pi/\napp/build/\nbuild/\nlocal.prop*\n' > "$repo/.gitignore"
  printf 'cmake_minimum_required(VERSION 3.22)\n' > "$repo/app/src/main/cpp/libosmscout/CMakeLists.txt"
  printf '# machine-local SDK/NDK/Java paths\n' > "$repo/local.properties"
  printf -- '---\nname: fix-loop\n---\nfixture skill\n' > "$repo/.pi/skills/fix-loop/SKILL.md"
  printf 'fixture skill\n' > "$repo/.pi/skills/openspec-apply-change/SKILL.md"
  git -C "$repo" init -q -b main >/dev/null 2>&1 || fail "could not create the throwaway repository"
  git -C "$repo" config user.email selftest@example.invalid
  git -C "$repo" config user.name selftest
  git -C "$repo" add -A >/dev/null 2>&1
  git -C "$repo" commit -qm init >/dev/null 2>&1 || fail "could not commit in the throwaway repository"
  printf '%s\n' "$repo"
}

state_file() { printf '%s\n' "$1/.pi/issue-loop/run.json"; }

run_state() { local p="$1"; shift; ( cd "$p" && bash "$LOOP_ENGINE/state.sh" "$@" ); }
run_prescreen() {
  local p="$1"; shift
  ( cd "$p" && GH_STUB_RECORD="${GH_STUB_RECORD:-$p/gh.log}" bash "$LOOP_ENGINE/prescreen.sh" "$@" )
}
run_worktree() { local p="$1"; shift; ( cd "$p" && bash "$LOOP_ENGINE/worktree.sh" "$@" ); }
run_labels() { local p="$1"; shift; ( cd "$p" && bash "$LOOP_ENGINE/labels.sh" "$@" ); }
run_report() { local p="$1"; shift; ( cd "$p" && bash "$LOOP_ENGINE/report.sh" "$@" ); }
run_loop() { local p="$1"; shift; ( cd "$p" && bash "$LOOP_ENGINE/run.sh" "$@" ); }

scenario() { printf '%s\n' "$LOOP_FIXTURES/scenarios/$1"; }

init_state() { # a run state the pre-screen needs before it can write its queue
  run_state "$1" init --items "${2:-1}" --minutes "${3:-90}" >/dev/null || fail "state init failed"
}

gh_log() { printf '%s\n' "$GH_STUB_RECORD"; }
pi_log() { printf '%s\n' "$PI_STUB_LOG"; }

# The stub child's fixtures. `worker` is the report the worker prints, `reviewer` the reviewer's, and the
# action leaves a real change in the tree so the wrapper's own commit and verification run for real.
stub_write() { # stub_write <dir> <worker-report> <reviewer-report> [extra-action]
  local dir="$1" worker="$2" reviewer="$3" extra="${4:-}"
  printf '%s\n' "$worker" > "$dir/worker-report.txt"
  printf '%s\n' "$reviewer" > "$dir/reviewer-report.txt"
  {
    printf 'case "$1" in\n'
    printf '  worker*)\n'
    printf '    mkdir -p openspec/changes/archive/2026-01-01-fix-demo/evidence\n'
    printf '    printf "archived\\n" > openspec/changes/archive/2026-01-01-fix-demo/proposal.md\n'
    printf '    mkdir -p app/build/test-results/test\n'
    printf '    printf "<testsuite tests=\\"1\\" failures=\\"0\\"></testsuite>\\n" > app/build/test-results/test/TEST-demo.xml\n'
    printf '    cp app/build/test-results/test/TEST-demo.xml openspec/changes/archive/2026-01-01-fix-demo/evidence/TEST-demo.xml\n'
    printf '    printf "the fix\\n" > demo-fix.txt\n'
    printf '    ;;\n'
    printf 'esac\n'
    [ -z "$extra" ] || printf '%s\n' "$extra"
  } > "$dir/worker-action.sh"
  export PI_STUB_REPORT_WORKER="$dir/worker-report.txt"
  export PI_STUB_REPORT_REVIEWER="$dir/reviewer-report.txt"
  export PI_STUB_ACTION="$dir/worker-action.sh"
  export PI_STUB_LOG="$dir/pi.log"; : > "$PI_STUB_LOG"
  export GH_STUB_RECORD="$dir/gh.log"; : > "$GH_STUB_RECORD"
}

STUB_WORKER_REPORT='LOOP-VERDICT: closed
LOOP-CHANGE: fix-demo
LOOP-ROOT-CAUSE: the fixture has exactly one cause
LOOP-RED-TEST: Demo#case fails on HEAD — XML tests=1 failures=1 (2026-01-01T00:00:00Z)
LOOP-GATE: tests=1 failures=0 (fixture)'

stub_success() { # stub_success <dir> — a run that produces a change and a passing review
  stub_write "$1" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
}

run_one_item() { # run_one_item <project> <dir> [run.sh args…]
  local p="$1" d="$2"; shift 2
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  run_loop "$p" --items 1 --minutes 90 "$@"
}

# ----------------------------------------------------------------------------- cases: state

case_state_initAndShowRoundTrips() {
  local p base out sf
  p=$(new_project)
  base=$(git -C "$p" rev-parse HEAD)
  run_state "$p" init --items 2 --minutes 90 --review off --may-close no --max-decisions 5 \
    --base "$base" >/dev/null || fail "init failed"

  assert_eq "2" "$(run_state "$p" get run.items)" "items"
  assert_eq "90" "$(run_state "$p" get run.minutes)" "minutes"
  assert_eq "off" "$(run_state "$p" get run.review)" "review"
  assert_eq "no" "$(run_state "$p" get run.mayClose)" "may-close survives"
  assert_eq "5" "$(run_state "$p" get run.maxDecisions)" "max-decisions"
  assert_eq "$base" "$(run_state "$p" get run.base)" "base survives"

  out=$(run_state "$p" show) || fail "show failed"
  assert_contains "$out" "may-close no" "show re-reads may-close"
  assert_contains "$out" "base $base" "show re-reads the base commit"
  assert_contains "$out" "2 items / 90 min" "show re-reads the budget"
  # the state is machine-local: it must not be part of the repository's history (D5)
  assert_eq "" "$(git -C "$p" status --porcelain)" "the state file is not tracked or reported"
  git -C "$p" check-ignore -q "$(state_file "$p")" || fail "the state file is not gitignored"

  # A field edited outside the script is reported as drift rather than obeyed (D5).
  sf=$(state_file "$p")
  jq '.run.items = 9' "$sf" > "$sf.tmp" && mv "$sf.tmp" "$sf"
  out=$(run_state "$p" show 2>&1)
  assert_contains "$out" "drift" "a hand-edited field is reported as drift"
  assert_contains "$out" "9 items" "the drifted value is what the state now says"
}

case_state_nextHonoursEveryStopCondition() {
  local p sf out status

  p=$(new_project); sf=$(state_file "$p")
  # the only condition under which the run may continue
  init_state "$p" 2 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  out=$(run_state "$p" next); status=$?
  assert_eq "0" "$status" "no stop condition holds"
  assert_eq "21" "$out" "the next candidate"

  # 1. the item cap: one item finished of a budget of one
  p=$(new_project)
  init_state "$p" 1 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result done --verdict closed >/dev/null
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "3" "$status" "the cap stops the run"
  assert_contains "$out" "cap" "the cap is named"

  # 2. the deadline
  p=$(new_project)
  init_state "$p" 3 0
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "3" "$status" "the deadline stops the run"
  assert_contains "$out" "deadline" "the deadline is named"

  # 3. the queue is empty
  p=$(new_project)
  init_state "$p" 3 90
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "3" "$status" "an empty queue stops the run"
  assert_contains "$out" "queue empty" "the empty queue is named"

  # 4. two consecutive blocked items
  p=$(new_project)
  init_state "$p" 5 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2},{"issue":23,"class":"bug","verdict":"eligible","rank":3}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result blocked --verdict blocked >/dev/null
  out=$(run_state "$p" next); status=$?
  assert_eq "0" "$status" "one blocked item does not stop the run"
  run_state "$p" done 22 --result blocked --verdict blocked >/dev/null
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "3" "$status" "two consecutive blocked items stop the run"
  assert_contains "$out" "two consecutive" "the reason is named"

  # 5. less time left than the next item's floor
  p=$(new_project)
  init_state "$p" 3 30
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "3" "$status" "a remaining time below the floor stops the run"
  assert_contains "$out" "floor" "the floor is named"
}

case_state_secondRunRefusesWithExit5() {
  local d p block first second status
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  block="$d/fifo"; mkfifo "$block"
  export PI_STUB_WAIT="$block"

  # `--review off`, because PI_STUB_WAIT blocks every child that is started while it is set and this
  # case needs exactly one blocked child (the second run's lock refusal is what is under test).
  ( cd "$p" && exec bash "$LOOP_ENGINE/run.sh" --items 1 --minutes 90 --review off ) > "$d/first.log" 2>&1 &
  first=$!
  local waited=0
  while [ ! -f "$block.ready" ] && [ "$waited" -lt 300 ]; do sleep 0.1; waited=$((waited + 1)); done
  [ -f "$block.ready" ] || { printf '\n' > "$block"; wait "$first"; fail "the first run never reached its child"; }

  gh_log_content=$(cat "$GH_STUB_RECORD")
  second=$(run_loop "$p" --items 1 --minutes 90 2>&1); status=$?
  assert_eq "5" "$status" "a second run refuses while the first holds the lock"
  assert_contains "$second" "another run holds" "the refusal names the lock"

  # Nothing was written by the second run: the first run's label bookkeeping is all that is in the log.
  assert_eq "$gh_log_content" "$(cat "$GH_STUB_RECORD")" "the refused run wrote no label"

  printf '\n' > "$block"
  wait "$first"
  unset PI_STUB_WAIT
}

case_state_defaultsComeFromLoopConf() {
  local p conf_items conf_minutes conf_review conf_may_close conf_max_decisions
  p=$(new_project)
  # read the recorded defaults from the same file the loop reads them from, so the case fails when a
  # default moves rather than following it
  conf_items=$(sed -n 's/^LOOP_ITEMS_DEFAULT=//p' "$LOOP_CONF")
  conf_minutes=$(sed -n 's/^LOOP_MINUTES_DEFAULT=//p' "$LOOP_CONF")
  conf_review=$(sed -n 's/^LOOP_REVIEW_DEFAULT=//p' "$LOOP_CONF")
  conf_may_close=$(sed -n 's/^LOOP_MAY_CLOSE_DEFAULT=//p' "$LOOP_CONF")
  conf_max_decisions=$(sed -n 's/^LOOP_MAX_DECISIONS_DEFAULT=//p' "$LOOP_CONF")
  [ -n "$conf_items" ] || fail "the configuration records no default for --items"
  run_state "$p" init >/dev/null || fail "init with no flags failed"
  assert_eq "$conf_items" "$(run_state "$p" get run.items)" "--items defaults to loop.conf"
  assert_eq "$conf_minutes" "$(run_state "$p" get run.minutes)" "--minutes defaults to loop.conf"
  assert_eq "$conf_review" "$(run_state "$p" get run.review)" "--review defaults to loop.conf"
  assert_eq "$conf_may_close" "$(run_state "$p" get run.mayClose)" "--may-close defaults to loop.conf"
  assert_eq "$conf_max_decisions" "$(run_state "$p" get run.maxDecisions)" "--max-decisions defaults to loop.conf"
}

case_state_projectKeysAreReadFromLoopConf() {
  local d p conf prompt
  d=$(scratch); p=$(new_project)
  conf="$d/loop.conf"
  sed -e 's/^LOOP_AGENT_CMD=.*/LOOP_AGENT_CMD="pi --print"/' \
      -e 's/^LOOP_WORKER_SKILLS=.*/LOOP_WORKER_SKILLS="alpha-skill beta-skill"/' \
      -e 's|^LOOP_GATE_CMD=.*|LOOP_GATE_CMD="my-gate --now"|' "$LOOP_CONF" > "$conf"
  export LOOP_CONF="$conf"
  run_one_item "$p" "$d" >/dev/null || fail "the run failed with a substituted configuration"
  prompt="$p/.pi/issue-loop/items/21/worker-prompt.md"
  [ -f "$prompt" ] || fail "the worker prompt was not rendered"
  assert_contains "$(cat "$prompt")" "alpha-skill beta-skill" "the skill list comes from loop.conf"
  assert_contains "$(cat "$prompt")" "my-gate --now" "the gate command comes from loop.conf"
  assert_not_contains "$(cat "$prompt")" "{{WORKER_SKILLS}}" "every placeholder is substituted"
}

case_state_agentCommandIsTheOnlyIndirection() {
  local d p wrapper
  d=$(scratch); p=$(new_project)
  wrapper="$d/my-agent.sh"
  cat > "$wrapper" <<EOF
#!/usr/bin/env bash
printf 'AGENT-CMD\\n' >> "$d/agent.log"
exec "$LOOP_FIXTURES/pi" "\$@"
EOF
  chmod +x "$wrapper"
  : > "$d/agent.log"
  # the seam is a configuration key, so the case sets it where the loop reads it
  local conf
  conf="$d/loop.conf"
  sed -e "s|^LOOP_AGENT_CMD=.*|LOOP_AGENT_CMD=\"$wrapper --print\"|" "$LOOP_CONF" > "$conf"
  export LOOP_CONF="$conf"
  run_one_item "$p" "$d" >/dev/null || fail "the run failed with a substituted agent command"
  # both children — the worker and the reviewer — went through the configured value
  assert_eq "2" "$(grep -c 'AGENT-CMD' "$d/agent.log")" "both child invocations expand the configured command"
  assert_eq "2" "$(grep -c '^invocation' "$PI_STUB_LOG")" "and both arrived at the child"
}

case_state_agentCommandRefusesAnEmptyValue() {
  local d p conf out status
  d=$(scratch); p=$(new_project)
  conf="$d/loop.conf"
  sed -e 's/^LOOP_AGENT_CMD=.*/LOOP_AGENT_CMD=""/' "$LOOP_CONF" > "$conf"
  export LOOP_CONF="$conf"
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  out=$(run_loop "$p" --items 1 --minutes 90 2>&1); status=$?
  assert_eq "2" "$status" "an empty agent command is a usage error"
  assert_contains "$out" "LOOP_AGENT_CMD is empty" "the refusal names the configuration key"
  [ ! -f "$PI_STUB_LOG" ] || [ ! -s "$PI_STUB_LOG" ] || fail "an agent process was started anyway"
}

# ----------------------------------------------------------------------------- cases: prescreen

case_prescreen_ranksBugsFirstAndDropsFeatures() {
  local p out
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario six)
  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed"
  assert_eq "21 22 23 24" "$(printf '%s' "$out" | jq -r '[.[] | select(.verdict == "eligible")] | sort_by(.rank) | [.[].issue] | join(" ")')" \
    "three bugs before the improvement"
  assert_eq "4" "$(printf '%s' "$out" | jq -r '[.[] | select(.verdict == "eligible")][-1].rank')" "the improvement is ranked last"
  assert_eq "class-feature" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 25) | .verdict')" "a feature is dropped"
  assert_contains "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 25) | .reason')" "class/feature" "the feature's reason"
  assert_eq "in-flight" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 26) | .verdict')" "an in-flight item is dropped"
  assert_contains "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 26) | .reason')" "in-flight" "the in-flight reason"
  assert_eq "4" "$(jq -r '.queue | length' "$(state_file "$p")")" "the queue holds the survivors only"
}

case_prescreen_missingPathYieldsStale() {
  local p out status
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario stale)
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err"); status=$?
  assert_eq "3" "$status" "nothing eligible is a stop, not a failure"
  assert_eq "stale" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 31) | .verdict')" "a missing cited path yields stale"
  assert_contains "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 31) | .reason')" \
    "app/src/main/kotlin/DoesNotExist.kt" "the verdict quotes the path that no longer exists"
}

# The refusal is not a line in a machine-local file (issue #163): the verdict an item cannot escape by
# itself is written to the tracker by the run's own script, the authorisation label is left as the owner
# put it, and a dry run writes nothing.
case_prescreen_refusalLeavesATraceInTheTracker() {
  local p out log
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario stale)
  export GH_STUB_RECORD="$p/gh.log"; : > "$GH_STUB_RECORD"
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err") || true
  assert_eq "stale" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 31) | .verdict')" \
    "the fixture is still the stale one (stderr: $(tr '\n' ' ' < "$p/prescreen.err"))"
  log=$(cat "$GH_STUB_RECORD")
  assert_contains "$log" "agent/excluded-stale" "the refusal is written to the tracker (stderr: $(tr '\n' ' ' < "$p/prescreen.err"))"
  assert_contains "$log" "issue edit 31" "the write names the item the verdict rejected"
  assert_eq "0" "$(printf '%s' "$log" | grep -c 'agent/eligible' || true)" \
    "the authorisation label is neither written nor withdrawn"
  assert_eq "0" "$(printf '%s' "$log" | grep -c 'status/' || true)" \
    "the refusal is a label of its own, not a silent status edit"

  : > "$GH_STUB_RECORD"
  run_prescreen "$p" --json --dry-run >/dev/null 2>&1 || true
  assert_eq "" "$(cat "$GH_STUB_RECORD")" "a dry run writes no label"
}

case_prescreen_doesNotClaimJudgementGates() {
  local p out
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario six)
  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed"
  # gates 2-5 are the worker's: the queue carries them as pending, and no verdict decides one
  assert_eq "pending pending pending pending" \
    "$(printf '%s' "$out" | jq -r '.[0].gates | [.oneRootCause, .oneFix, .falsifiable, .oneCapability] | join(" ")')" \
    "the judgement gates are pending"
  assert_eq "0" "$(printf '%s' "$out" | jq '[.[] | select(.verdict == "needs-diagnosis" or .verdict == "needs-decision" or .verdict == "unspecifiable" or .verdict == "too-wide")] | length')" \
    "no candidate is judged"
  assert_eq "eligible" "$(jq -r '.queue[0].verdict // "eligible"' "$(state_file "$p")")" "the queue holds undecided survivors"
}

case_prescreen_refusesReservedLabelInSelection() {
  local p out
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario foreign-author)
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err") || true
  assert_eq "author-not-allowlisted" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 51) | .verdict')" \
    "the opt-in label alone does not authorise an item"
  assert_contains "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 51) | .reason')" "allowlist" "the reason"
  assert_eq "0" "$(jq -r '.queue | length' "$(state_file "$p")")" "nothing unauthorised reaches the queue"
}

case_prescreen_withoutTheOptInIsNeverQueued() {
  local p out status
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario no-optin)
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err"); status=$?
  assert_eq "3" "$status" "an item without the opt-in leaves nothing to work"
  assert_eq "0" "$(jq -r '.queue | length' "$(state_file "$p")")" "it never reaches the queue"
  assert_eq "0" "$(jq -r '.excluded | length' "$(state_file "$p")")" "and it is not even a candidate: selection reads the label"
  # the same defect-shaped item, with the opt-in, would be worked — so the case is about the opt-in
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err") || fail "the same fixture with the opt-in did not select"
  assert_contains "$out" '"issue":21' "an item carrying the opt-in is selected"
}

case_prescreen_settledItemIsDropped() {
  local p out status
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario settled)
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err"); status=$?
  assert_eq "3" "$status" "nothing settled is worked"
  assert_eq "settled" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 91) | .verdict')" \
    "an item the tracker has already settled is dropped"
  assert_contains "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 91) | .reason')" "fixed-by" "the reason names the state"
  assert_eq "settled" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 92) | .verdict')" \
    "so is a closed issue whose labels were never moved on"
  assert_eq "0" "$(jq -r '.queue | length' "$(state_file "$p")")" "neither reaches the queue"
}

case_prescreen_deviceItemIsAVerdictNotWork() {
  local p out status
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario device)
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err"); status=$?
  assert_eq "3" "$status" "a device-gated item is not host work"
  assert_eq "needs-device" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 81) | .verdict')" \
    "its verdict is the device, decided by a script"
  assert_contains "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 81) | .reason')" "device" "the reason names it"
  assert_eq "0" "$(jq -r '.queue | length' "$(state_file "$p")")" "no agent process is spent on it"
}

# The pre-screen's verdicts ARE values of the profile's vocabulary, so the profile's `roles` section is
# where they are read: rename the device verdict there and the pre-screen's verdict for a device-gated item
# changes with it, which is what tells a literal from a value read at run time (issue #162, D16).
case_prescreen_readsTheVerdictsFromTheProfile() {
  local p out status
  p=$(new_project)
  init_state "$p" 3 90
  jq '.roles.deviceAgent = "needs-a-device"' "$p/tools/tooling.json" > "$p/tools/tooling.json.new" &&
    mv "$p/tools/tooling.json.new" "$p/tools/tooling.json" || fail "could not rename the device verdict"
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario device)
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err"); status=$?
  assert_eq "3" "$status" "a device-gated item is still not host work"
  assert_eq "needs-a-device" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 81) | .verdict')" \
    "the device verdict is the value the profile's roles section declares"
}

# Whether an item was already attempted is held by git, not by the tracker, so the pre-screen asks git the
# same question `worktree.sh start-item` asks — asked on the branch *prefix*, because the slug is not known
# before the title is read (issue #175). The decision is then recorded where a person reads it, before any
# item is picked and before a budget slot is spent on it; the start's own check stays the authoritative one.
case_prescreen_previousAttemptBranchIsADecision() {
  local p out base
  p=$(new_project)
  init_state "$p" 3 90
  base=$(git -C "$p" rev-parse HEAD)
  git -C "$p" checkout -q -b fix/issue-21-a-previous-attempt "$base"
  printf 'the previous attempt\n' > "$p/attempt.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the previous attempt" >/dev/null 2>&1 ||
    fail "could not commit the previous attempt"
  git -C "$p" checkout -q main
  # …and one whose branch carries nothing of its own: the verdict is about the commits, not about the name
  git -C "$p" branch fix/issue-22-carried-nothing "$base"

  export GH_STUB_DIR; GH_STUB_DIR=$(scenario six)
  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed"
  assert_eq "needs-decision" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 21) | .verdict')" \
    "a branch that carries a previous attempt is a decision, not a candidate"
  assert_contains "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 21) | .reason')" \
    "fix/issue-21-a-previous-attempt" "the verdict names the branch that carries the work"
  assert_eq "fix/issue-21-a-previous-attempt" \
    "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 21) | .evidence')" \
    "the branch is the verdict's own clause of evidence"
  assert_eq "eligible" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 22) | .verdict')" \
    "a branch that carries no commit of its own leaves the item eligible"
  assert_eq "3" "$(jq -r '.queue | length' "$(state_file "$p")")" "the decision is not queued as work"
  assert_eq "needs-decision" "$(jq -r '.excluded[] | select(.issue == 21) | .verdict' "$(state_file "$p")")" \
    "the decision is recorded in the run's exclusion list"
  assert_eq "fix/issue-21-a-previous-attempt" \
    "$(jq -r '.excluded[] | select(.issue == 21) | .evidence' "$(state_file "$p")")" \
    "with the branch carried beside it"
}

# An item the loop itself escalated is its owner's until a person decides: the run writes
# `agent/needs-decision` and `status/on-hold`, and the next pre-screen read only the in-flight status, so #110
# was analysed and escalated three times with one conclusion (comments on 2026-10-18 and 2026-10-19). The
# verdict is the run's own decision verdict, but marked as already escalated so the run does not post the same
# comment again.
case_prescreen_itemAwaitingADecisionIsNotQueued() {
  local p out issue status
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario decisions)
  export GH_STUB_RECORD="$p/gh.log"; : > "$GH_STUB_RECORD"

  out=$(run_prescreen "$p" --json); status=$?
  assert_eq "3" "$status" "a backlog that awaits decisions is 'nothing eligible', not an error"
  for issue in 41 42 43; do
    assert_eq "needs-decision" "$(printf '%s' "$out" | jq -r --argjson i "$issue" '.[] | select(.issue == $i) | .verdict')" \
      "an item awaiting a decision is not a candidate (#$issue)"
    assert_eq "true" "$(printf '%s' "$out" | jq -r --argjson i "$issue" '.[] | select(.issue == $i) | .alreadyEscalated')" \
      "and it is marked as already escalated, so the run does not escalate it again (#$issue)"
  done
  # A hold with no escalation label is not a decision (#193): the worker's skip verdicts, the device verdict,
  # the repair path (`resolve-stale.sh` parks an item) and a person's own hold all write the same state, and only
  # the last of those four may be a question. The item is excluded as `held` — visible in the report, and outside
  # the decision cap, which counts the items a person can actually answer.
  assert_eq "held" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 44) | .verdict')" \
    "a hold with no recorded decision is reported as a hold, not as a decision (#44)"
  assert_eq "false" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 44) | .alreadyEscalated')" \
    "and nothing is escalated for it (#44)"
  assert_eq "0" "$(grep -c 'agent/needs-decision' "$GH_STUB_RECORD" || true)" \
    "no decision label is written for a hold nobody stated a question for"
  assert_eq "0" "$(jq -r '.queue | length' "$(state_file "$p")")" "none of them is queued as work"
  assert_eq "3" "$(jq -r '[.excluded[] | select(.verdict == "needs-decision")] | length' "$(state_file "$p")")" \
    "the three escalated items are recorded as decisions"
  assert_eq "1" "$(jq -r '[.excluded[] | select(.verdict == "held")] | length' "$(state_file "$p")")" \
    "and the bare hold is recorded as a hold"
}

# A hold states no question (#193): the worker's skip verdicts, the device verdict, the stale-citation repair's
# park and a person's own hold all write the same state, and none of them is a decision. The pre-screen reports
# the hold as what it is, writes nothing, and the run therefore spends no decision on it — before this, every
# held item was escalated with a boilerplate sentence and counted against `--max-decisions` until the cap
# stopped every future run (measured 2026-10-20: 12 items against a cap of 3).
case_prescreen_aHoldWithNoDecisionIsNotADecision() {
  local p out status log
  p=$(new_project)
  init_state "$p" 1 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario held)
  log="$p/gh.log"; : > "$log"; export GH_STUB_RECORD="$log"

  out=$(run_prescreen "$p" --json); status=$?
  assert_eq "3" "$status" "a backlog whose only item is a bare hold is 'nothing eligible'"
  assert_eq "held" "$(printf '%s' "$out" | jq -r '.[0].verdict')" "the verdict names the hold, not a decision"
  assert_contains "$(printf '%s' "$out" | jq -r '.[0].reason')" "no recorded decision" \
    "and the reason says what the item lacks"
  assert_eq "0" "$(wc -c < "$log" | tr -d ' ')" "the pre-screen writes no label for a hold"

  out=$(run_loop "$p" --items 1 --minutes 90 --review off 2>&1) || fail "the run failed: $out"
  assert_contains "$out" "nothing eligible" "the run stops as 'nothing eligible'"
  assert_eq "0" "$(grep -c 'needs-decision' "$log" || true)" "no decision label is written for the hold"
  assert_eq "0" "$(grep -c 'issue comment' "$log" || true)" "and nothing is escalated for it"
  assert_eq "held" "$(jq -r '.excluded[0].verdict' "$(state_file "$p")")" "the run records the item as held"
}

# An item the run escalated *and* whose branch carries a finished attempt is the one the adopt path exists for
# (#176): the branch is the evidence `adopt_finished_exclusions` reads, so the awaiting-decision clause must not
# overwrite it with a label name. Before this ordering the two fixes together made adoption unreachable (#185
# review): every escalated item lost the only field the adoption path selects on.
case_prescreen_escalatedItemKeepsItsBranchAsEvidence() {
  local p base branch out status
  p=$(new_project)
  init_state "$p" 4 90
  base=$(git -C "$p" rev-parse HEAD)
  branch="fix/issue-41-an-escalated-item"
  git -C "$p" checkout -q -b "$branch" "$base"
  printf 'the earlier attempt\n' > "$p/attempt.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the earlier attempt" >/dev/null 2>&1 ||
    fail "could not commit the earlier attempt"
  git -C "$p" checkout -q main
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario decisions)
  export GH_STUB_RECORD="$p/gh.log"; : > "$GH_STUB_RECORD"

  out=$(run_prescreen "$p" --json); status=$?
  assert_eq "3" "$status" "an escalated backlog is 'nothing eligible'"
  assert_eq "needs-decision" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 41) | .verdict')" \
    "the escalated item stays a decision"
  assert_eq "$branch" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 41) | .evidence')" \
    "and its branch stays the evidence, which is what the adoption path reads"
  assert_eq "true" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 41) | .alreadyEscalated')" \
    "while still being marked as already escalated, so no second comment is posted"
}

# A harness defect is paid again by every item after it — a broken `prescreen.sh`, `labels.sh`, `close.sh` or a
# sensor can make the run's own report false — so the profile's leading categories rank ahead of every other
# category, and inside one group the class order still decides (harness bug, harness improvement, app bug, app
# improvement). Before this key the category was read, recorded and printed, and never ranked on.
case_prescreen_harnessItemsLeadTheQueue() {
  local p out
  p=$(new_project)
  init_state "$p" 5 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario categories)
  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed"
  assert_eq "22 26 23 21 24" \
    "$(printf '%s' "$out" | jq -r '[.[] | select(.verdict == "eligible")] | sort_by(.rank) | [.[].issue] | join(" ")')" \
    "the leading group (bug, bug, improvement), then the app bug, then the app improvement"
  assert_eq "1" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 22) | .rank')" \
    "the harness bug leads the queue"
  assert_eq "2" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 26) | .rank')" \
    "the second harness bug follows by issue number inside the leading group"
  assert_eq "3" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 23) | .rank')" \
    "the leading improvement follows both leading bugs, so the class order decides inside the group"
  assert_eq "4" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 21) | .rank')" \
    "the app bug leads the app improvement but follows the whole leading group"
  assert_eq "5" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 24) | .rank')" \
    "the app improvement stays last"
}

# A renamed set is ordered on, not merely scanned: the profile declares `ui` as its leading category and the
# fixture's `category/ui` improvement leads the queue, above a harness bug — the key is the declared value, not a
# spelling this change happens to ship.
case_prescreen_aRenamedLeadingCategoryLeads() {
  local p out
  p=$(new_project)
  init_state "$p" 5 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario categories)
  jq '.roles.firstCategories = ["ui"]' "$p/tools/tooling.json" > "$p/tools/tooling.json.new" &&
    mv "$p/tools/tooling.json.new" "$p/tools/tooling.json" || fail "could not rename the leading category"
  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed on a renamed leading set"
  assert_eq "24 21 22 26 23" \
    "$(printf '%s' "$out" | jq -r '[.[] | select(.verdict == "eligible")] | sort_by(.rank) | [.[].issue] | join(" ")')" \
    "the declared value leads, and the class order decides among the rest"
  assert_eq "1" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 24) | .rank')" \
    "the ui improvement is rank 1 under the renamed set"
}

# The leading categories are the profile's values, read through the roles section like every other decision: an
# empty list is a project that wants no category priority, and an absent key is a half-adapted profile that
# must stop the script rather than silently rank by class.
case_prescreen_priorityCategoriesAreReadFromTheProfile() {
  local p out status
  p=$(new_project)
  init_state "$p" 5 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario categories)

  jq '.roles.firstCategories = []' "$p/tools/tooling.json" > "$p/tools/tooling.json.new" &&
    mv "$p/tools/tooling.json.new" "$p/tools/tooling.json" || fail "could not empty the priority list"
  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed on an empty priority list"
  assert_eq "21 22 26 23 24" \
    "$(printf '%s' "$out" | jq -r '[.[] | select(.verdict == "eligible")] | sort_by(.rank) | [.[].issue] | join(" ")')" \
    "an empty list leaves the class order alone"

  jq 'del(.roles.firstCategories)' "$p/tools/tooling.json" > "$p/tools/tooling.json.new" &&
    mv "$p/tools/tooling.json.new" "$p/tools/tooling.json" || fail "could not remove the priority list"
  out=$(run_prescreen "$p" --json 2>&1); status=$?
  assert_eq "2" "$status" "an absent role is a half-adapted profile, not 'no priority'"
  assert_contains "$out" "firstCategories" "and the refusal names the key the profile does not declare"
}

# The category key is a rank, not a gate: an item in a leading category that is on hold is excluded exactly as
# any other held item, writes no label and takes no rank, so the priority can never queue work the gate refused.
case_prescreen_priorityDoesNotBypassTheGate() {
  local p out log
  p=$(new_project)
  init_state "$p" 5 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario categories)
  log="$p/gh.log"; : > "$log"; export GH_STUB_RECORD="$log"

  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed"
  assert_eq "held" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 25) | .verdict')" \
    "a held item in a leading category is still held"
  assert_eq "null" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 25) | .rank')" \
    "and it takes no rank"
  assert_eq "author-not-allowlisted" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 27) | .verdict')" \
    "a leading-category item by an unauthorised author is excluded as unauthorised, not ranked"
  assert_contains "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 27) | .reason')" "allowlist" \
    "and the reason names the allowlist"
  assert_eq "0" "$(wc -c < "$log" | tr -d ' ')" "the category writes no label of its own"
  assert_eq "5" "$(jq -r '.queue | length' "$(state_file "$p")")" \
    "the queue holds the five eligible items and not the held one"
}

# Every role the script reads is required, not merely the one this change added: a half-adapted profile that
# omits the hold verdict would otherwise report an empty verdict instead of stopping with a named reason.
case_prescreen_anUndeclaredRoleStopsTheScreen() {
  local p out status
  p=$(new_project)
  init_state "$p" 1 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario categories)
  jq 'del(.roles.heldVerdict)' "$p/tools/tooling.json" > "$p/tools/tooling.json.new" &&
    mv "$p/tools/tooling.json.new" "$p/tools/tooling.json" || fail "could not remove the hold verdict"
  out=$(run_prescreen "$p" --json 2>&1); status=$?
  assert_eq "2" "$status" "an undeclared role stops the pre-screen"
  assert_contains "$out" "does not declare heldVerdict" "and the refusal names the key"
}

# ----------------------------------------------------------------------------- cases: worktree

# The throwaway repository has no submodule and no vendored dependency, so the seed's submodule step is a
# no-op there (it is exercised only against this repository); what the case asserts is the state after
# seeding — the directory populated, the local properties and the gitignored skills present, none of
# which a fresh worktree inherits.
case_worktree_seedsAllFourInputs() {
  local p w
  p=$(new_project); w="$p/.pi/worktrees/loop"
  init_state "$p" 1 90
  run_worktree "$p" create --branch loop-test >/dev/null || fail "worktree create failed"
  [ -d "$w" ] || fail "the worktree does not exist"
  [ -s "$w/app/src/main/cpp/libosmscout/CMakeLists.txt" ] || fail "the submodule directory is not populated in the worktree"
  [ -f "$w/local.properties" ] || fail "local.prop* was not copied into the worktree"
  [ -f "$w/.pi/skills/fix-loop/SKILL.md" ] || fail "the gitignored loop skill was not copied into the worktree"
  assert_contains "$(cat "$w/.pi/loop-env.sh")" "VCPKG_ROOT" "the dependency root is exported from a file, not a symlink"
}

case_worktree_checksOutTheSubmoduleTheLoopConfigNames() {
  local p d nested conf out
  p=$(new_project); d=$(scratch)
  nested="$d/nested"; mkdir -p "$nested"
  printf 'the vendored source\n' > "$nested/vendored.txt"
  git -C "$nested" init -q -b main >/dev/null 2>&1 || fail "could not create the nested repository"
  git -C "$nested" add -A >/dev/null 2>&1
  git -C "$nested" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm init >/dev/null 2>&1 ||
    fail "could not commit in the nested repository"
  git -C "$p" -c user.email=selftest@example.invalid -c user.name=selftest -c protocol.file.allow=always \
    submodule add -q "$nested" vendor/lib >/dev/null 2>&1 || fail "could not add the submodule"
  git -C "$p" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm "the submodule" >/dev/null 2>&1 ||
    fail "could not commit the submodule"
  # the loop's configuration decides which path is checked out: the copy names a path of its own, and the
  # test repository's `.gitmodules` declares exactly that path
  conf="$d/loop.conf"
  sed -e 's|^LOOP_SUBMODULE_PATH=.*|LOOP_SUBMODULE_PATH="vendor/lib"|' "$LOOP_CONF" > "$conf" ||
    fail "could not write the loop configuration"
  export LOOP_CONF="$conf"
  # the fixture's submodule is a local path, which git refuses to clone without allowing the file protocol
  export GIT_ALLOW_PROTOCOL=file

  init_state "$p" 1 90
  out=$(run_worktree "$p" create --branch loop-test 2>&1) || fail "worktree create failed: $out"
  [ -s "$p/.pi/worktrees/loop/vendor/lib/vendored.txt" ] ||
    fail "the submodule the configuration names was not checked out: $out"
}

case_worktree_oneBranchPerItemOffTheBase() {
  local p w base out status
  p=$(new_project); w="$p/.pi/worktrees/loop"
  base=$(git -C "$p" rev-parse HEAD)
  init_state "$p" 2 90
  run_worktree "$p" create --branch loop-test >/dev/null || fail "worktree create failed"
  out=$(run_worktree "$p" start-item 21 demo 2>&1) || fail "the first item did not start: $out"
  assert_eq "fix/issue-21-demo" "$(git -C "$w" branch --show-current)" "the item's branch"

  # one writer per tree: the next item refuses while the first item's tree is dirty
  printf 'wip\n' > "$w/wip.txt"
  out=$(run_worktree "$p" start-item 22 other 2>&1); status=$?
  assert_eq "5" "$status" "a dirty tree refuses the next item"
  assert_contains "$out" "not clean" "the refusal says why"
  rm -f "$w/wip.txt"

  run_worktree "$p" start-item 22 other >/dev/null || fail "the second item did not start"
  [ -n "$(git -C "$p" branch --list 'fix/issue-22-other')" ] || fail "the second branch was not created"
  git -C "$p" merge-base --is-ancestor "$base" fix/issue-21-demo || fail "item 21 does not descend from the run's base"
  git -C "$p" merge-base --is-ancestor "$base" fix/issue-22-other || fail "item 22 does not descend from the run's base"
  return 0
}

case_worktree_reusesABranchThatCarriesNoWorkOfItsOwn() {
  local p w base out
  p=$(new_project); w="$p/.pi/worktrees/loop"
  init_state "$p" 1 90
  run_worktree "$p" create --branch loop-test >/dev/null || fail "worktree create failed"
  run_worktree "$p" start-item 21 demo >/dev/null || fail "the first item did not start"
  # the previous run left the branch behind with nothing committed on it, and the base has moved on: the
  # item must be workable again, or a blocked item could never be retried (issue #170)
  printf 'the next commit\n' > "$p/advanced.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" commit -qm advanced >/dev/null 2>&1 || fail "could not advance the base"
  base=$(git -C "$p" rev-parse HEAD)
  run_state "$p" init --items 1 --minutes 90 --base "$base" >/dev/null || fail "init failed"
  out=$(run_worktree "$p" start-item 21 demo 2>&1) || fail "a branch carrying no work was refused: $out"
  assert_contains "$out" "reused" "the run says the branch was reused"
  assert_eq "$base" "$(git -C "$w" rev-parse HEAD)" "the item starts exactly at the new base"
}

case_worktree_refusesABranchCarryingAPreviousAttempt() {
  local p w out status
  p=$(new_project); w="$p/.pi/worktrees/loop"
  init_state "$p" 1 90
  run_worktree "$p" create --branch loop-test >/dev/null || fail "worktree create failed"
  run_worktree "$p" start-item 21 demo >/dev/null || fail "the first item did not start"
  # the previous attempt committed before it was blocked: that commit is the run's only record of the
  # work, so starting over would throw it away and the item is refused instead
  printf 'the previous attempt\n' > "$w/attempt.txt"
  git -C "$w" add -A >/dev/null 2>&1
  git -C "$w" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the previous attempt" >/dev/null 2>&1 ||
    fail "could not commit the previous attempt"
  out=$(run_worktree "$p" start-item 21 demo 2>&1); status=$?
  assert_eq "5" "$status" "a branch that carries work refuses the item"
  assert_contains "$out" "carries commits the base does not" "the refusal says why"
  assert_contains "$out" "the previous attempt" "and names the commit it would throw away"
  assert_eq "$(git -C "$w" rev-parse fix/issue-21-demo)" "$(git -C "$p" rev-parse fix/issue-21-demo)" \
    "the branch is left where it was"
}

case_worktree_refusesWhenMainAdvanced() {
  local p w base out status tip
  p=$(new_project); w="$p/.pi/worktrees/loop"
  init_state "$p" 2 90
  base=$(run_state "$p" get run.base)
  run_worktree "$p" create --branch loop-test >/dev/null || fail "worktree create failed"
  run_worktree "$p" start-item 21 demo >/dev/null || fail "the first item did not start"

  git -C "$p" commit --allow-empty -qm "a change landed on main while the run was open"
  tip=$(git -C "$p" rev-parse --short main)
  out=$(run_worktree "$p" start-item 22 other 2>&1); status=$?
  assert_eq "5" "$status" "a stale base refuses the next item"
  assert_contains "$out" "base moved" "the refusal names the condition"
  assert_contains "$out" "${base:0:12}" "it names the run's base commit"
  assert_contains "$out" "$tip" "it names the commit main has advanced to"
  return 0
}

case_worktree_keepsByDefaultAndRemovesOnRequest() {
  local d p
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  [ -d "$p/.pi/worktrees/loop" ] || fail "the worktree was removed although nothing asked for it"
  run_one_item "$p" "$d" --clean-worktree >/dev/null || fail "the second run failed"
  [ ! -d "$p/.pi/worktrees/loop" ] || fail "the worktree survived --clean-worktree"
  if git -C "$p" worktree list | grep -q 'worktrees/loop'; then
    fail "git still lists the worktree that was removed"
  fi
  return 0
}

case_worktree_noPushWithoutTheFlag() {
  local d p trace hits
  hits=$(grep -rn 'gh pr create' "$LOOP_PROJECT_ROOT/tools/loop/" || true)
  [ -z "$hits" ] || fail "the loop opens a change request: $hits"
  assert_eq "1" "$(grep -c 'push_branch()' "$LOOP_ENGINE/run.sh")" "the push is one named call"
  assert_eq "1" "$(grep -c 'push_branch "' "$LOOP_ENGINE/run.sh")" "and it has exactly one call site"
  assert_contains "$(grep -B1 'push_branch "' "$LOOP_ENGINE/run.sh")" 'push" = yes' "the call site is guarded by the flag"

  # A default run does not push: git's own trace is the log (git is never stubbed — D13).
  d=$(scratch); p=$(new_project)
  trace="$d/git.trace"
  export GIT_TRACE="$trace"
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  [ -s "$trace" ] || fail "git was not traced, so the case would prove nothing"
  assert_eq "0" "$(grep -c 'git push' "$trace" || true)" "a run that was not asked to publish does not push"
}

case_worktree_refusesABranchWhoseReviewFailed() {
  # A branch that carries one archived change and a green verification is adoptable by every input the run
  # checked before #185 — and one the reviewer refused looks exactly the same from the outside. The verdict
  # therefore travels inside the change's own evidence directory, and adoption reads it there.
  local p w out status branch
  p=$(new_project); w="$p/.pi/worktrees/loop"
  init_state "$p" 1 90
  branch="fix/issue-21-a-refused-change"
  git -C "$p" switch -c "$branch" >/dev/null 2>&1 || fail "could not create the refused attempt's branch"
  mkdir -p "$p/openspec/changes/archive/2026-01-01-fix-refused/evidence"
  printf 'archived\n' > "$p/openspec/changes/archive/2026-01-01-fix-refused/proposal.md"
  printf '<testsuite tests="2" failures="0" errors="0"></testsuite>\n' \
    > "$p/openspec/changes/archive/2026-01-01-fix-refused/evidence/TEST-refused.xml"
  printf 'the refused change\n' > "$p/refused-fix.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the refused change" >/dev/null 2>&1 ||
    fail "could not commit the refused change"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not return to the base"

  # The reviewer refused round 2 of exactly this change. What the run writes on the branch is what a later
  # run reads (`run#recordsAFailedReviewOnTheBranch` covers the writing); here the file is placed on the
  # branch the way the run's own commit leaves it.
  git -C "$p" switch -q "$branch" >/dev/null 2>&1 || fail "could not switch to the attempt's branch"
  printf 'The reviewer refused this change in review round 2.\n' \
    > "$p/openspec/changes/archive/2026-01-01-fix-refused/evidence/review-failed-round-2.md"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "review: refused in round 2" >/dev/null 2>&1 ||
    fail "could not commit the verdict"
  git -C "$p" switch -q main >/dev/null 2>&1 || fail "could not return to the base"

  out=$(run_worktree "$p" adopt-item 21 "$branch" 2>&1); status=$?
  assert_eq "5" "$status" "a branch whose review was refused is not adopted"
  assert_contains "$out" "the reviewer refused" "the refusal says why"
  assert_contains "$out" "round 2" "and names the round the verdict came from"
  [ ! -f "$p/refused-fix.txt" ] || fail "the refused change was merged onto the base anyway"
}


case_labels_verdictToLabelMatrix() {
  local d p log out status
  p=$(new_project); d=$(scratch)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)

  check_labels() { # check_labels <verdict> <expected added> <expected removed>
    local verdict="$1" want_add="$2" want_remove="$3" log="$d/gh-$1.log"
    : > "$log"; export GH_STUB_RECORD="$log"
    out=$(run_labels "$p" "$verdict" 21 --reason "the fixture cause" --evidence "the fixture evidence" 2>&1)
    status=$?
    [ "$status" -eq 0 ] || fail "$verdict: labels.sh exited $status: $out"
    local got_add got_remove
    got_add=$(grep 'issue edit' "$log" | grep -o -- '--add-label [a-z/-]*' | sed 's/--add-label //' | sort | paste -sd' ' -)
    got_remove=$(grep 'issue edit' "$log" | grep -o -- '--remove-label [a-z/-]*' | sed 's/--remove-label //' | sort | paste -sd' ' -)
    assert_eq "$want_add" "$got_add" "$verdict: the labels it adds"
    assert_eq "$want_remove" "$got_remove" "$verdict: the labels it removes"
  }

  # The fixture item carries `status/open`, and a verdict that sets a state replaces it: the matrix names the
  # displaced label, so a verdict that only ever added one would leave two states on the item and fail here.
  check_labels pick "agent/in-progress" ""
  check_labels unpick "" "agent/in-progress"
  check_labels stale "status/on-hold" "status/open"
  check_labels unspecifiable "status/on-hold" "status/open"
  check_labels too-wide "status/on-hold" "status/open"
  check_labels needs-device "agent/needs-device status/on-hold" "status/open"
  check_labels needs-decision "agent/needs-decision status/on-hold" "status/open"
  check_labels needs-diagnosis "agent/needs-decision status/on-hold" "status/open"

  log="$d/gh-close.log"; : > "$log"; export GH_STUB_RECORD="$log"
  run_labels "$p" close 21 --reason "the fixture cause" --evidence "the fixture evidence" >/dev/null 2>&1 ||
    fail "close failed"
  out=$(cat "$log")
  assert_contains "$out" "--remove-label agent/in-progress" "close takes the in-progress label off"
  assert_contains "$out" "--add-label status/fixed-by" "close marks the item as fixed"
  assert_contains "$out" "--remove-label status/open" "and replaces the state the item carried"
  assert_eq "1" "$(grep -c 'issue comment' "$log")" "exactly one evidence comment"
  assert_eq "1" "$(grep -c 'issue close' "$log")" "the item is closed"
}

case_labels_holdCarriesItsReason() {
  local d p log out status
  p=$(new_project); d=$(scratch)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  log="$d/gh-hold.log"; : > "$log"; export GH_STUB_RECORD="$log"

  # A hold states no question, so it needs no decision label — but it needs its reason on the item (#192).
  out=$(run_labels "$p" stale 21 2>&1); status=$?
  assert_eq "2" "$status" "a hold without a reason is refused as usage: $out"
  [ ! -s "$log" ] || fail "a refused hold wrote something: $(cat "$log")"

  run_labels "$p" stale 21 --reason "the cited path no longer exists" --evidence "base abc123, the tree" >/dev/null 2>&1 ||
    fail "a hold with reason and evidence failed"
  assert_eq "1" "$(grep -c 'issue comment' "$log" || true)" "exactly one comment carries the reason"
  assert_contains "$(cat "$log")" "the cited path no longer exists" "the comment names the reason"
  assert_contains "$(cat "$log")" "status/on-hold" "the state is still written"
  assert_eq "0" "$(grep -c 'needs-decision' "$log" || true)" "and no decision label is added for a hold"
  assert_eq "0" "$(grep -c 'issue close' "$log" || true)" "the item stays open"
}

case_labels_trapRemovesInProgressOnSignal() {
  local d p block first waited
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  # The case sends SIGTERM, not SIGINT: a non-interactive shell starts background jobs with SIGINT and
  # SIGQUIT ignored (POSIX), so a SIGINT to this job would be silently dropped and the case would prove
  # nothing. The run traps both signals identically, and the structural half of this case asserts the
  # SIGINT trap exists.
  assert_contains "$(grep -n 'INT' "$LOOP_ENGINE/run.sh" | grep -c 'on_signal')" "1" "the run traps SIGINT as well"
  block="$d/fifo"; mkfifo "$block"; export PI_STUB_WAIT="$block"
  # `--review off`: PI_STUB_WAIT blocks every child started while it is set, and this case needs one.
  ( cd "$p" && exec bash "$LOOP_ENGINE/run.sh" --items 1 --minutes 90 --review off ) > "$d/run.log" 2>&1 &
  first=$!
  waited=0
  while [ ! -f "$block.ready" ] && [ "$waited" -lt 300 ]; do sleep 0.1; waited=$((waited + 1)); done
  [ -f "$block.ready" ] || { printf '\n' > "$block"; wait "$first"; fail "the run never reached its child"; }

  kill -TERM "$first"
  sleep 0.2
  printf '\n' > "$block"
  wait "$first"
  unset PI_STUB_WAIT

  assert_eq "1" "$(grep -c 'remove-label agent/in-progress' "$GH_STUB_RECORD" || true)" \
    "the interrupted item's in-progress label is removed exactly once"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" "the interrupted item is recorded blocked"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "interrupted" "the reason is recorded"
  [ ! -d "$p/.pi/issue-loop/lock" ] || fail "the lock was left behind by the interrupted run"
  return 0
}

case_labels_neverWritesReservedLabel() {
  local hits count p out status
  # The engine's scripts, not the fixtures: a fixture issue carries the label because that is what a
  # fixture *is* (an expectation). No code path of the loop can write it.
  hits=$(grep -rn 'agent/eligible' "$LOOP_PROJECT_ROOT/tools/loop/" --exclude-dir=fixtures --exclude='*.md' || true)
  count=$(printf '%s\n' "$hits" | grep -c . || true)
  assert_eq "1" "$count" "the authorisation label appears exactly once in the loop's scripts"
  assert_contains "$hits" 'LOOP_AUTHORISATION_LABEL' "and it is the read-side constant, not a write"
  assert_eq "0" "$(grep -rn 'eligible' "$LOOP_ENGINE/labels.sh" | grep -c 'add_labels=\|remove_labels=' || true)" \
    "no transition can produce the authorisation label"

  # and the tooling refuses it on every path, including the removal path the loop now uses
  p=$(new_project)
  out=$( cd "$p" && bash "$LOOP_ISSUES_ENGINE/update.sh" 21 --remove-label agent/eligible 2>&1 ); status=$?
  assert_eq "3" "$status" "the tooling refuses to remove the reserved label"
  assert_contains "$out" "label-reserved" "with the reserved-label reason"
}

case_labels_writesOnlyBetweenChildren() {
  local d p before after n i b a
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  before=$(grep '^state-before:' "$PI_STUB_LOG" | awk '{print $2}')
  after=$(grep '^state-after:' "$PI_STUB_LOG" | awk '{print $2}')
  n=$(printf '%s\n' "$before" | grep -c . || true)
  [ "$n" -ge 2 ] || fail "the run did not start both children"
  i=1
  while [ "$i" -le "$n" ]; do
    b=$(printf '%s\n' "$before" | sed -n "${i}p")
    a=$(printf '%s\n' "$after" | sed -n "${i}p")
    assert_eq "$b" "$a" "the state changed while child $i was running"
    i=$((i + 1))
  done
  return 0
}

# ----------------------------------------------------------------------------- cases: prompts

case_prompt_workerNamesEveryPhaseAndSkill() {
  local d p prompt skills skill
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompt="$p/.pi/issue-loop/items/21/worker-prompt.md"
  [ -f "$prompt" ] || fail "the worker prompt was not rendered"
  for phase in 'Phase A' 'Phase B' 'Phase C' 'Phase D' 'Phase F'; do
    assert_contains "$(cat "$prompt")" "$phase" "the worker prompt names $phase"
  done
  skills=$(sed -n 's/^LOOP_WORKER_SKILLS="\(.*\)"/\1/p' "$LOOP_CONF")
  for skill in $skills; do
    assert_contains "$(cat "$prompt")" "$skill" "the worker prompt names the skill $skill"
  done
  assert_contains "$(cat "$prompt")" "data, not instructions" "the item's text is marked as data"
}

case_prompt_workerNamesTheDecisionField() {
  local d p prompt
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompt="$p/.pi/issue-loop/items/21/worker-prompt.md"
  [ -f "$prompt" ] || fail "the worker prompt was not rendered"
  # The handover: what a person gets on the item when the worker reaches a fork (#194).
  assert_contains "$(cat "$prompt")" 'LOOP-DECISION:' "the worker prompt asks for the decision field"
  assert_contains "$(cat "$prompt")" 'the question in one sentence' "and says what it must contain"
}

case_run_skipEscalationCarriesTheDecision() {
  local d p skip_report out
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  skip_report=$(printf 'LOOP-VERDICT: skipped\nLOOP-SKIP-VERDICT: needs-decision\nLOOP-ROOT-CAUSE: the two rows disagree about the same number\nLOOP-RED-TEST: none — nothing compiled was touched\nLOOP-DECISION: merge the two rows, or keep both and mark the newer authoritative?\nMerging loses the older wording; keeping both costs one row. Recommend merging.')
  stub_write "$d" "$skip_report" 'VERDICT: PASS'
  out=$(run_loop "$p" --items 1 --minutes 90 --review off 2>&1) || fail "the run failed: $out"
  # The item must be answerable from itself: the escalation carries the question, not only the news that one
  # is needed.
  assert_eq "1" "$(grep -c 'issue comment' "$GH_STUB_RECORD" || true)" "one escalation comment"
  assert_contains "$(grep 'issue comment' "$GH_STUB_RECORD")" 'merge' \
    "the escalation comment carries the worker's question"
  assert_contains "$(cat "$GH_STUB_RECORD")" '--add-label agent/needs-decision' "and marks the item as awaiting one"
}

case_prompt_reviewerCarriesTheEightItems() {
  local d p prompt
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompt="$p/.pi/issue-loop/items/21/reviewer-prompt-1.md"
  [ -f "$prompt" ] || fail "the reviewer prompt was not rendered"
  local item
  for item in '1. **Scenario coverage**' '2. **Root cause scope**' '3. **Gate reality**' '4. **Tasks claiming done**' \
              '5. **Revert-check**' '6. **Evidence pointers**' '7. **Spec and guideline honesty**' '8. **New overreach**'; do
    assert_contains "$(cat "$prompt")" "$item" "the reviewer contract carries $item"
  done
  assert_contains "$(cat "$prompt")" "VERDICT: PASS|FAIL" "the reviewer prompt carries the verdict shape"
  assert_contains "$(cat "$prompt")" "must not" "and says the reviewer must not be the writer"
  # The two fields the run reads back: the decision an escalation carries (#186) and whether the findings are
  # strictly subtractive, which is what earns the extra round (#187).
  assert_contains "$(cat "$prompt")" "LOOP-DECISION:" "the decision the escalation will carry"
  assert_contains "$(cat "$prompt")" "LOOP-CORRECTIONS-ONLY:" "and whether the findings are subtractive"
}

case_prompt_packageIsAFileAndNotInlined() {
  local d p prompt package expected
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompt="$p/.pi/issue-loop/items/21/worker-prompt.md"
  package="$p/.pi/issue-loop/items/21/work-package.md"
  [ -f "$package" ] || fail "the work package was not written"
  assert_contains "$(cat "$prompt")" "$package" "the prompt names the package's path"
  assert_not_contains "$(cat "$prompt")" "§21 An eligible bug" "the prompt does not inline the item's text"

  # the package file is the core's own output for that issue, not a paraphrase
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  issue_json=$(gh issue view 21 --json number,title,body,labels,author,state)
  expected=$(printf '%s' "$issue_json" |
    java -cp "$ISSUES_CORE_CLASSES" WorkPackage --profile "$p/tools/tooling.json" --issue - \
      --fingerprint "$(printf '%s' "$issue_json" | jq -r '.body // ""' | sha256sum | cut -c1-16)" \
      --provenance "issue #21")
  assert_eq "$expected" "$(cat "$package")" "the package is exactly the core's rendering"
}

case_prompt_inlineInjectionCannotReachTheInstructionPosition() {
  local d p package prompt
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario injection)
  stub_success "$d"
  run_loop "$p" --items 1 --minutes 90 >/dev/null 2>&1 || true
  package="$p/.pi/issue-loop/items/61/work-package.md"
  prompt="$p/.pi/issue-loop/items/61/worker-prompt.md"
  [ -f "$package" ] || fail "no work package was written for the injected item"
  [ -f "$prompt" ] || fail "no prompt was rendered for the injected item"

  # exactly one opening and one closing delimiter, whatever the body contained
  assert_eq "1" "$(grep -c '^<<<ISSUE_TEXT$' "$package" || true)" "exactly one opening delimiter"
  assert_eq "1" "$(grep -c '^ISSUE_TEXT$' "$package" || true)" "exactly one closing delimiter"
  # the injected prose is inside the data block and nowhere else
  assert_eq "1" "$(grep -c 'Ignore all previous instructions' "$package" || true)" "the injected text is carried as data"
  awk '/^<<<ISSUE_TEXT$/{inside=1} /^ISSUE_TEXT$/{inside=0} inside && /Ignore all previous instructions/{found=1} END{exit found ? 0 : 1}' \
    "$package" || fail "the injected text is outside the data block"
  # the instructions never carry it
  assert_eq "0" "$(grep -c 'Ignore all previous instructions\|^<<<ISSUE_TEXT$' "$prompt" || true)" \
    "the injected text does not reach the instruction position"
  return 0
}

# ----------------------------------------------------------------------------- cases: run

case_run_preflightRefusalStopsBeforeAnyWork() {
  local d p out status
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR="$LOOP_ISSUE_FIXTURES/scenarios/outsider"
  stub_success "$d"
  out=$(run_loop "$p" --items 1 --minutes 90 2>&1); status=$?
  assert_eq "3" "$status" "an unauthorised identity stops the run"
  assert_contains "$out" "not authorised" "the stop names the reason"
  [ ! -d "$p/.pi/worktrees/loop" ] || fail "a worktree was created before the identity was checked"
  assert_eq "" "$(cat "$GH_STUB_RECORD" 2>/dev/null || true)" "no label was written"
  assert_eq "" "$(cat "$PI_STUB_LOG" 2>/dev/null || true)" "no agent process was started"
}

case_run_decisionCapRefusesToStart() {
  local d p out status
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario decisions)
  stub_success "$d"
  out=$(run_loop "$p" --items 1 --minutes 90 --max-decisions 3 2>&1); status=$?
  assert_eq "5" "$status" "the decision cap refuses to start"
  assert_contains "$out" "await a decision" "the refusal names the condition"
  assert_contains "$out" "#41" "the open decisions are printed"
  assert_contains "$out" "#43" "all of them are printed"
  [ ! -d "$p/.pi/worktrees/loop" ] || fail "a worktree was created although the run refused"
  assert_eq "" "$(cat "$PI_STUB_LOG" 2>/dev/null || true)" "no agent process was started"
}

case_run_oneItemOrderIsFixed() {
  local d p prompts
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompts=$(grep '^prompt:' "$PI_STUB_LOG" | awk '{print $2}')
  assert_eq "2" "$(printf '%s\n' "$prompts" | grep -c .)" "one item starts exactly two processes"
  assert_eq "worker-prompt.md reviewer-prompt-1.md" "$(printf '%s' "$prompts" | paste -sd' ' -)" \
    "the worker runs first and the reviewer second"
  # the child is given a prompt file and nothing else: no tracker credential, no token, no label
  assert_eq "2" "$(sed -n 's/^argv://p' "$PI_STUB_LOG" | head -1 | wc -w)" \
    "the child's argv carries the flag and the prompt, and nothing that could write to the tracker"
}

case_run_childClaimWithoutCommitIsBlocked() {
  local d p out
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  # a child that reports success and produces nothing: the wrapper must not close the item
  printf '#!/usr/bin/env bash\n: # this child leaves the tree exactly as it found it\n' > "$d/worker-action.sh"
  out=$(run_loop "$p" --items 1 --minutes 90 2>&1)
  assert_eq "1" "$(jq -r '.items | length' "$(state_file "$p")")" "the item was recorded"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" "a claim without a commit is blocked"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "base commit" "the reason names what was missing"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "the item was not closed"
}

case_run_blockedItemIsEscalatedWithItsReason() {
  local d p log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  # a child that reports success and commits nothing: the run blocks the item for a reason that is not a
  # failed review, and the reason must reach the tracker in the same pass — the state that holds it is
  # rewritten wholesale by the next run (issue #174)
  printf '#!/usr/bin/env bash\n: # this child leaves the tree exactly as it found it\n' > "$d/worker-action.sh"
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" "the item is blocked"
  log=$(cat "$GH_STUB_RECORD")
  assert_contains "$log" "agent/needs-decision" "a blocked item is marked as awaiting a decision"
  assert_contains "$log" "status/on-hold" "and put on hold"
  assert_eq "1" "$(grep -c 'issue comment' "$GH_STUB_RECORD" || true)" "the block's reason is named in one comment"
  assert_contains "$log" "base commit" "the comment carries the reason the run blocked it"
  assert_contains "$log" "fix/issue-21-" "and the branch that carries the attempt"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "nothing is closed"
  assert_eq "agent/needs-decision" "$(jq -r '.items[0].escalatedLabel // ""' "$(state_file "$p")")" \
    "the run records the label its own escalation wrote"
}

case_run_childStartsInTheProjectRoot() {
  local d p logs prompt
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  logs=$(grep '^cwd:' "$PI_STUB_LOG" || true)
  [ -n "$logs" ] || fail "no child invocation was recorded"
  # Both children were started in the project, not in the worktree: a fresh process resolves the project's
  # own policy at the directory it is started in, and the worktree lies inside that directory.
  assert_eq "" "$(printf '%s\n' "$logs" | grep -vxF "cwd: $p" || true)" \
    "every child was started in the project root"
  # …and the tree it is told to change is the worktree, by path, which is what keeps the work off the
  # project's own tree.
  prompt="$p/.pi/issue-loop/items/21/worker-prompt.md"
  [ -f "$prompt" ] || fail "the worker prompt was not rendered"
  assert_contains "$(cat "$prompt")" "$p/.pi/worktrees/loop" "the worker prompt names its change root"
  prompt="$p/.pi/issue-loop/items/21/reviewer-prompt-1.md"
  assert_contains "$(cat "$prompt")" "$p/.pi/worktrees/loop" "the reviewer prompt names its change root"
}

case_run_childReportsWhileItWaits() {
  local d p fifo pid i out
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # A child that says nothing while it works is the normal case, not the exception: print mode writes only the
  # final assistant text, so `pick #N` used to be followed by silence for the whole item and a working child
  # was indistinguishable from a hung one. The wait itself therefore has to report.
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  fifo="$d/wait.fifo"; mkfifo "$fifo"
  # The stub blocks on this fifo and the run waits for the stub. A case that leaves through an assertion below
  # used to leave both alive, and both hold the caller's stdout open — so a failing suite hung the shell instead
  # of reporting the failure (measured 2026-10-20: three orphaned runs in one worktree). Release and reap on
  # every exit path; the writer is backgrounded because a fifo with no reader blocks forever.
  trap '( printf "go\n" > "$fifo" ) 2>/dev/null & kill "${pid:-0}" 2>/dev/null; wait "${pid:-0}" 2>/dev/null' EXIT
  ( export PI_STUB_WAIT="$fifo" LOOP_PROGRESS_SECONDS=1
    run_loop "$p" --items 1 --minutes 90 --review off > "$d/out" 2>&1 ) &
  pid=$!
  # The host may be loaded by another run's suites or a Gradle build, so the waits are ceilings rather than
  # stopwatches: the time they allow is what a loaded machine needs, and the assertion says what is missing.
  for i in $(seq 1 300); do [ -e "$fifo.ready" ] && break; sleep 0.1; done
  [ -e "$fifo.ready" ] || fail "the stub child never started"
  # The child is blocked on the fifo: whatever the run says now it cannot have learned from the child.
  for i in $(seq 1 600); do grep -q 'running' "$d/out" 2>/dev/null && break; sleep 0.1; done
  out=$(cat "$d/out")
  assert_contains "$out" "loop: worker-prompt started" "the wait is announced when the child starts"
  assert_contains "$out" "loop: worker-prompt running" "the wait reports while the child cannot"
  assert_contains "$out" "changed file(s)" "what it reports is read from the worktree"
  printf 'go\n' > "$fifo"
  wait "$pid" || fail "the run itself failed"
  assert_contains "$(cat "$d/out")" "loop: worker-prompt finished in" "and the end is reported"
  assert_contains "$(cat "$d/out")" "exit 0" "with the child's own exit status"
}

case_run_childWriteOutsideTheWorktreeIsBlocked() {
  local d p
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # a child that does its work in the worktree and also writes in the project's own tree: only the
  # worktree is a phase's to write, so the run catches this rather than committing it into the project
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'case "$1" in worker*) printf "stray\n" >> "$LOOP_CHILD_ROOT/demo-owned.txt" ;; esac'
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "a write outside the worktree blocks the item"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "outside the worktree" \
    "the reason names the tree that changed"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "and the item is not closed"
}

case_run_reviewerWriteInTheProjectIsDiscarded() {
  local d p
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # a reviewer that stays inside the worktree is caught by its own round; this one writes in the project's
  # tree, which the round reads as well — a process that wrote cannot have been the reviewer either way
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'case "$1" in reviewer*) printf "the reviewer wrote\n" >> "$LOOP_CHILD_ROOT/demo-owned.txt" ;; esac'
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "a reviewer that wrote in the project cannot have been the reviewer"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "working tree changed" \
    "the round is discarded with a reason"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "nothing is closed on a discarded round"
}

case_run_leftoverBranchIsRecordedNotDropped() {
  local d p log branch
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  # the first attempt archives a change but leaves no test-results XML, so the run blocks it — and the
  # branch it committed on stays behind (issue #170)
  cat > "$d/worker-action.sh" <<'ACTION'
case "$1" in
  worker*)
    mkdir -p openspec/changes/archive/2026-01-01-fix-demo/evidence
    printf 'archived\n' > openspec/changes/archive/2026-01-01-fix-demo/proposal.md
    printf 'the evidence the change cites\n' > openspec/changes/archive/2026-01-01-fix-demo/evidence/TEST-demo.xml
    printf 'the first attempt\n' > demo-fix.txt
    ;;
esac
ACTION
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the first run failed"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" "the first attempt is blocked"
  branch=$(git -C "$p" branch --list 'fix/issue-21-*' --format='%(refname:short)')
  [ -n "$branch" ] || fail "the first attempt left no branch behind"
  git -C "$p" merge-base --is-ancestor "$(git -C "$p" rev-parse main)" "$branch" ||
    fail "the leftover branch carries no work, so this case is not exercising the refusal"

  # The next run's pre-screen can see the branch, so the item is a recorded decision before anything is
  # picked: no budget slot, no worker process, and the branch named on the tracker (issue #175).
  : > "$GH_STUB_RECORD"; : > "$PI_STUB_LOG"
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1); status=$?
  assert_eq "0" "$status" "nothing eligible is an explicit non-error"
  assert_contains "$out" "nothing eligible" "the run says why it stopped"
  assert_eq "0" "$(jq -r '.items | length' "$(state_file "$p")")" "no budget slot was spent on the item"
  assert_eq "needs-decision" "$(jq -r '.excluded[] | select(.issue == 21) | .verdict' "$(state_file "$p")")" \
    "the item is recorded as awaiting a decision rather than dropped"
  assert_contains "$(jq -r '.excluded[] | select(.issue == 21) | .reason' "$(state_file "$p")")" "fix/issue-21-" \
    "the recorded verdict names the branch"
  assert_eq "" "$(cat "$PI_STUB_LOG" 2>/dev/null || true)" "no agent process was started for it"
  log=$(cat "$GH_STUB_RECORD")
  assert_contains "$log" "agent/needs-decision" "the previous attempt's work is escalated, not thrown away"
  assert_contains "$log" "status/on-hold" "and the item is put on hold"
  assert_contains "$log" "fix/issue-21-" "the comment names the branch"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "nothing is closed"
}

# The pre-screen cannot see a branch that did not exist when it ran, so the start's own check stays the
# authoritative one: a branch that appears while the run is open is still refused, recorded and escalated
# rather than dropped, and the run takes the next candidate. An item this run has already recorded is not
# escalated a second time when the queue empties and the pre-screen runs again.
case_run_branchThatAppearsWhileTheRunIsOpenIsRecorded() {
  local d p log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario two)
  # the first item's worker leaves a previous attempt's branch behind for the second item: the two fixture
  # titles slug to the same tail on purpose, so the case can name the second item's branch without
  # duplicating the run's own slugify
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'case "$1" in worker*)
       current=$(git rev-parse --abbrev-ref HEAD)
       next=fix/issue-22-${current#fix/issue-21-}
       attempt=$(git -c user.name=selftest -c user.email=selftest@example.invalid commit-tree HEAD^{tree} -p HEAD -m "fix: the previous attempt")
       git branch "$next" "$attempt"
       ;; esac'
  run_loop "$p" --items 2 --minutes 90 >/dev/null 2>&1 || fail "the run itself failed"
  assert_eq "2" "$(jq -r '.items | length' "$(state_file "$p")")" "both items were recorded"
  assert_eq "blocked" "$(jq -r '.items[] | select(.issue == 22) | .result' "$(state_file "$p")")" \
    "the item whose branch appeared while the run was open is blocked"
  assert_contains "$(jq -r '.items[] | select(.issue == 22) | .note' "$(state_file "$p")")" \
    "carries commits the base does not" "the reason names the branch's work"
  log=$(cat "$GH_STUB_RECORD")
  assert_contains "$log" "agent/needs-decision" "the previous attempt's work is escalated, not thrown away"
  assert_contains "$log" "fix/issue-22-" "and the comment names the branch"
  assert_eq "0" "$(grep -c 'issue close 22' "$GH_STUB_RECORD" || true)" "the refused item is not closed"
  assert_eq "1" "$(grep -c 'add-label agent/needs-decision' "$GH_STUB_RECORD" || true)" \
    "it is escalated once, not again when the pre-screen runs a second time"
}

# An item whose earlier attempt *finished* — its branch carries one archived change the base does not have
# and a verification record that is green — is an item no later path could take: `start-item` refuses the
# branch and two such refusals end a run (issue #176). The branch is merged onto the base and the item is
# closed through the run's own closure path, with the branch, its commits and the evidence it was checked
# for recorded in the run state and named in the report.
# A branch is a later run's only view of an earlier attempt, so the verdict a reviewer reached has to live on
# it: the run commits the refusal into the change's own evidence directory, on the item's branch (#185).
case_run_recordsAFailedReviewOnTheBranch() {
  local d p branch files out
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: FAIL'
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1) || fail "the run itself failed: $out"

  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" "two failed rounds block the item"
  branch=$(git -C "$p" for-each-ref --format='%(refname:short)' 'refs/heads/fix/issue-21-*' | head -1)
  [ -n "$branch" ] || fail "the run left no item branch"
  files=$(git -C "$p" ls-tree -r --name-only "$branch")
  printf '%s\n' "$files" | grep -q 'openspec/changes/archive/.*/evidence/review-failed-round-2\.md' ||
    fail "the reviewer's verdict does not travel with the change: $(printf '%s' "$files" | grep evidence || true)"
  assert_contains "$(git -C "$p" log --format=%s "$branch" | head -1)" "failed round 2" \
    "and it is a commit on the branch, not a machine-local file"
}

case_run_mergesAFinishedAttemptAndClosesTheItem() {
  local d p out status branch base log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  # a previous attempt that finished: one archived change, the red-on-HEAD XML beside the green gate XML —
  # exactly what the two branches #176 was measured on carried
  branch="fix/issue-21-previous-attempt"
  git -C "$p" switch -c "$branch" >/dev/null 2>&1 || fail "could not create the previous attempt's branch"
  mkdir -p "$p/openspec/changes/archive/2026-01-01-fix-adopted/evidence"
  printf 'archived\n' > "$p/openspec/changes/archive/2026-01-01-fix-adopted/proposal.md"
  printf '<testsuite tests="2" failures="1" errors="0"></testsuite>\n' \
    > "$p/openspec/changes/archive/2026-01-01-fix-adopted/evidence/TEST-adopted-red-on-HEAD.xml"
  printf '<testsuite tests="2" failures="0" errors="0"></testsuite>\n' \
    > "$p/openspec/changes/archive/2026-01-01-fix-adopted/evidence/TEST-adopted.xml"
  printf 'the previous attempt\n' > "$p/adopted-fix.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the previous attempt" >/dev/null 2>&1 ||
    fail "could not commit the previous attempt"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not return to the base"
  base=$(git -C "$p" rev-parse main)

  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1); status=$?
  assert_eq "0" "$status" "adopting a finished attempt is an explicit non-error"
  assert_contains "$out" "adopted: branch $branch" "the run names the branch it merged"
  [ "$(git -C "$p" rev-parse main)" != "$base" ] || fail "the base did not move onto the merged branch"
  git -C "$p" merge-base --is-ancestor "$branch" main || fail "the finished attempt's commits are not on the base"
  [ -f "$p/openspec/changes/archive/2026-01-01-fix-adopted/proposal.md" ] ||
    fail "the archived change did not travel with the merge"
  assert_eq "done" "$(jq -r '.items[0].result' "$(state_file "$p")")" "the item is recorded as done"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" "with the closed verdict"
  assert_eq "$branch" "$(jq -r '.items[0].merged.branch' "$(state_file "$p")")" "the branch it merged is recorded"
  assert_contains "$(jq -r '.items[0].merged.evidence' "$(state_file "$p")")" "TEST-adopted.xml" \
    "and the verification record it was checked for"
  assert_eq "0" "$(jq -r '.excluded | length' "$(state_file "$p")")" "the adopted item is no longer a decision"
  assert_eq "0" "$(jq -r '.queue | length' "$(state_file "$p")")" "and it is not queued as work either"
  assert_contains "$out" "merged finished attempts: #21" "the report names the merge"
  log=$(cat "$GH_STUB_RECORD")
  assert_contains "$log" "issue close 21" "the item is closed on the tracker"
  assert_contains "$log" "status/fixed-by" "with the fixed-by label"
  assert_not_contains "$log" "agent/needs-decision" "and no decision is left on it"
}

# The merge is only taken on a verification that is green: a branch whose archived change carries no passing
# verification record — a blocked attempt carries only the red-on-HEAD XML — keeps its refusal, its escalation
# and the person who owns the decision, and nothing is merged (issue #176).
case_run_doesNotMergeAnAttemptWithoutAGreenVerification() {
  local d p out status branch base log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  branch="fix/issue-21-previous-attempt"
  git -C "$p" switch -c "$branch" >/dev/null 2>&1 || fail "could not create the previous attempt's branch"
  mkdir -p "$p/openspec/changes/archive/2026-01-01-fix-adopted/evidence"
  printf 'archived\n' > "$p/openspec/changes/archive/2026-01-01-fix-adopted/proposal.md"
  printf '<testsuite tests="2" failures="2" errors="0"></testsuite>\n' \
    > "$p/openspec/changes/archive/2026-01-01-fix-adopted/evidence/TEST-adopted-red-on-HEAD.xml"
  printf 'the previous attempt\n' > "$p/adopted-fix.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the previous attempt" >/dev/null 2>&1 ||
    fail "could not commit the previous attempt"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not return to the base"
  base=$(git -C "$p" rev-parse main)

  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1); status=$?
  assert_eq "0" "$status" "the refusal is an explicit non-error"
  assert_not_contains "$out" "adopted:" "nothing is merged without a green verification record"
  assert_contains "$out" "carries no green verification record" "the refusal names what it looked for"
  assert_eq "$base" "$(git -C "$p" rev-parse main)" "the base did not move"
  [ ! -f "$p/openspec/changes/archive/2026-01-01-fix-adopted/proposal.md" ] ||
    fail "the change was merged although its verification is not green"
  assert_eq "0" "$(jq -r '.items | length' "$(state_file "$p")")" "no item was recorded as merged"
  assert_eq "needs-decision" "$(jq -r '.excluded[] | select(.issue == 21) | .verdict' "$(state_file "$p")")" \
    "the branch is still the owner's decision"
  assert_contains "$(jq -r '.excluded[] | select(.issue == 21) | .reason' "$(state_file "$p")")" "$branch" \
    "the recorded verdict names the branch"
  log=$(cat "$GH_STUB_RECORD")
  assert_contains "$log" "agent/needs-decision" "the previous attempt's work is escalated, not thrown away"
  assert_contains "$log" "$branch" "and the escalation names the branch"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "nothing is closed"
}

# The kept worktree is deliberate (the next run inherits its warm build) and refusing a previous run's
# leftovers is the right rule — but the refusal has to name the item the leftovers belong to, be recorded
# and be reported, and it has to be reached before the run pays for anything else (issue #173): a refusal
# that ends a run with an exit code and no item is the "nothing processed and no reason" outcome the report
# exists to prevent, on the path where it is most likely — a killed or crashed run.
case_run_dirtyKeptWorktreeIsARecordedRefusal() {
  local d p out status log head_before
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the first run failed"
  assert_eq "fix/issue-21-" "$(cut -c1-13 "$p/.pi/worktrees/loop/.pi/loop-branch")" \
    "the kept worktree's branch marker names the item it is on"
  printf 'the previous run left work behind\n' > "$p/.pi/worktrees/loop/leftover.txt"
  head_before=$(git -C "$p/.pi/worktrees/loop" rev-parse HEAD)
  : > "$GH_STUB_RECORD"; : > "$PI_STUB_LOG"
  out=$(run_loop "$p" --items 1 --minutes 90 2>&1); status=$?
  assert_eq "5" "$status" "a kept worktree that is not clean refuses the run"
  assert_not_contains "$out" "pre-screen:" "the refusal is reached before the pre-screen is paid for"
  assert_contains "$out" "leftover.txt" "it names the files it will not carry"
  assert_contains "$out" "#21" "and the item the leftovers belong to"
  assert_contains "$out" "agent-issue-loop — " "the run's report is printed"
  assert_eq "21" "$(jq -r '.items[0].issue' "$(state_file "$p")")" "the refusal is recorded as an item"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" "and it is blocked"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "not clean" "with the refusal as its reason"
  log=$(cat "$GH_STUB_RECORD")
  assert_contains "$log" "agent/needs-decision" "the leftovers are escalated to a person"
  assert_contains "$log" "fix/issue-21-" "with the branch as evidence"
  assert_eq "" "$(cat "$PI_STUB_LOG" 2>/dev/null || true)" "no agent process was started"
  [ -f "$p/.pi/worktrees/loop/leftover.txt" ] || fail "the leftovers were discarded without --reset-worktree"
  assert_eq "$head_before" "$(git -C "$p/.pi/worktrees/loop" rev-parse HEAD)" "and nothing was committed"
}

# `--reset-worktree` is the start-time repair the refusal names: it says what it discards before discarding
# it, it takes neither the commits on the previous branches nor the tracker with it, and it is never
# implied — without the flag the leftovers stay and the run refuses (issue #173).
case_run_resetWorktreeDiscardsWhatItNames() {
  local d p out status
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the first run failed"
  printf 'the previous run left work behind\n' > "$p/.pi/worktrees/loop/leftover.txt"
  : > "$GH_STUB_RECORD"; : > "$PI_STUB_LOG"
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario none)
  out=$(run_loop "$p" --items 1 --minutes 90 --reset-worktree 2>&1); status=$?
  assert_eq "0" "$status" "the run starts once the leftovers are discarded"
  assert_contains "$out" "reset-worktree" "the repair names itself"
  assert_contains "$out" "leftover.txt" "and names what it discards before discarding it"
  [ ! -f "$p/.pi/worktrees/loop/leftover.txt" ] || fail "the named leftover survived --reset-worktree"
  assert_eq "" "$(git -C "$p/.pi/worktrees/loop" status --porcelain)" "the kept worktree is clean again"
  assert_contains "$out" "nothing eligible" "and the run then answers to the backlog, not to the leftovers"
  [ -n "$(git -C "$p" branch --list 'fix/issue-21-*')" ] || fail "the repair discarded the branches as well"
}

case_run_aSkipClearsTheInProgressMark() {
  local d p skip_report
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  skip_report=$(printf 'LOOP-VERDICT: skipped\nLOOP-SKIP-VERDICT: stale\nLOOP-ROOT-CAUSE: the fixture condition no longer holds\nLOOP-RED-TEST: none — no case can fail on HEAD')
  stub_write "$d" "$skip_report" 'VERDICT: PASS'
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  assert_eq "skipped" "$(jq -r '.items[0].result' "$(state_file "$p")")" "the item is a skip"
  assert_contains "$(cat "$GH_STUB_RECORD")" "--remove-label agent/in-progress" \
    "a skipped item does not keep the in-progress mark"
  assert_contains "$(cat "$GH_STUB_RECORD")" "status/on-hold" "and the verdict's own status is applied"
  # The hold must be readable from the item (#192): a state a reader cannot account for is the defect.
  assert_eq "1" "$(grep -c 'issue comment' "$GH_STUB_RECORD" || true)" \
    "the skip posts exactly one comment, naming why the item is held"
  assert_contains "$(grep 'issue comment' "$GH_STUB_RECORD" || true)" "the fixture condition no longer holds" \
    "and the comment carries the worker's own root cause"
}

case_run_claimedGateWithoutEvidenceIsBlocked() {
  local d p
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  # the change is archived and complete, but the tree carries no test-results XML to look at
  cat > "$d/worker-action.sh" <<'ACTION'
case "$1" in
  worker*)
    mkdir -p openspec/changes/archive/2026-01-01-fix-demo/evidence
    printf 'archived\n' > openspec/changes/archive/2026-01-01-fix-demo/proposal.md
    printf 'the evidence the change cites\n' > openspec/changes/archive/2026-01-01-fix-demo/evidence/TEST-demo.xml
    printf 'the fix\n' > demo-fix.txt
    ;;
esac
ACTION
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" "a gate claim with no evidence is blocked"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "no test-results XML" "the reason names what the run looked for"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "and the item is not closed"
}

case_run_citedEvidenceMustTravelWithTheChange() {
  local d p
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  # the gate ran and left its XML in the build directory, but the change carries no evidence of its own
  cat > "$d/worker-action.sh" <<'ACTION'
case "$1" in
  worker*)
    mkdir -p openspec/changes/archive/2026-01-01-fix-demo
    printf 'archived\n' > openspec/changes/archive/2026-01-01-fix-demo/proposal.md
    mkdir -p app/build/test-results/test
    printf '<testsuite tests="1" failures="0"></testsuite>\n' > app/build/test-results/test/TEST-demo.xml
    printf 'the fix\n' > demo-fix.txt
    ;;
esac
ACTION
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" "evidence that does not travel with the change is blocked"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "carries no evidence" "the reason names the missing half"
}

case_run_needsDecisionIsEscalatedWithTheWork() {
  local d p skip_report log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  skip_report=$(printf 'LOOP-VERDICT: skipped\nLOOP-SKIP-VERDICT: needs-decision\nLOOP-ROOT-CAUSE: two mechanisms explain the symptom\nLOOP-RED-TEST: Demo#case was never reached')
  stub_write "$d" "$skip_report" 'VERDICT: PASS'
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  assert_eq "skipped" "$(jq -r '.items[0].result' "$(state_file "$p")")" "the item is a skip, not a failure"
  assert_eq "needs-decision" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" "its verdict asks for a decision"
  log=$(cat "$GH_STUB_RECORD")
  assert_contains "$log" "agent/needs-decision" "the item is marked as awaiting a decision"
  assert_contains "$log" "status/on-hold" "and put on hold"
  assert_eq "1" "$(grep -c 'issue comment' "$GH_STUB_RECORD" || true)" "the work already done is named in one comment"
  assert_contains "$log" "two mechanisms explain the symptom" "the comment carries the root cause"
  assert_contains "$log" "fix/issue-21-" "and the branch"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "nothing is closed"
}

case_run_noGateStillRuns() {
  local d p conf
  d=$(scratch); p=$(new_project)
  # a project with no build system: the phases still run, and the report says the gate was skipped
  conf="$d/loop.conf"
  sed -e 's|^LOOP_GATE_CMD=.*|LOOP_GATE_CMD=""|' "$LOOP_CONF" > "$conf"
  export LOOP_CONF="$conf"
  run_one_item "$p" "$d" >/dev/null || fail "a project with no gate could not run"
  assert_eq "done" "$(jq -r '.items[0].result' "$(state_file "$p")")" "the item completed"
  assert_eq "yes" "$(jq -r '.items[0].metrics.closed' "$(state_file "$p")")" "and closed"
  assert_eq "skipped (no gate configured)" "$(jq -r '.items[0].metrics.gate' "$(state_file "$p")")" \
    "the report says the gate was skipped rather than inventing a tally"
}

case_run_dryRunTouchesNothing() {
  local d p out status
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  out=$(run_loop "$p" --dry-run --items 1 --minutes 90 2>&1); status=$?
  assert_eq "0" "$status" "a dry run is not a failure"
  assert_contains "$out" "dry run" "the output says what it was"
  assert_contains "$out" "rank 1" "and it reports the queue it would work"
  [ ! -d "$p/.pi/worktrees/loop" ] || fail "a dry run created the worktree"
  assert_eq "" "$(cat "$GH_STUB_RECORD" 2>/dev/null || true)" "a dry run wrote no label"
  assert_eq "" "$(cat "$PI_STUB_LOG" 2>/dev/null || true)" "a dry run started no agent process"
  [ -f "$(state_file "$p")" ] || fail "a dry run did not record the run it would make"

  # an existing worktree is neither created nor removed by a dry run
  init_state "$p" 1 90
  run_worktree "$p" create --branch loop-test >/dev/null || fail "could not create the existing worktree"
  run_loop "$p" --dry-run --items 1 --minutes 90 >/dev/null 2>&1 || true
  [ -d "$p/.pi/worktrees/loop" ] || fail "the dry run removed an existing worktree"
}

case_run_reviewRoundsCapAtTwo() {
  local d p log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: FAIL'
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" "two failed rounds block the item"
  assert_eq "2" "$(jq -r '.items[0].metrics.reviewRounds' "$(state_file "$p")")" "the rounds are counted"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "last review round" "the reason names the round"
  assert_eq "4" "$(grep -c '^invocation' "$PI_STUB_LOG")" "two workers and two reviewers"
  log=$(cat "$GH_STUB_RECORD")
  assert_contains "$log" "agent/needs-decision" "the item is escalated to a decision"
  assert_contains "$log" "status/on-hold" "and put on hold"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "nothing is closed"
}

# The round budget was the literal `2` in the loop, so a project that reviews harder could not raise it and a
# reviewer that asked only for corrections still ended the run's work on the item (#187).
case_run_reviewBudgetIsARecordedValue() {
  local d p f
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: FAIL'
  run_loop "$p" --items 1 --minutes 90 --review on --max-review-rounds 3 >/dev/null 2>&1 ||
    fail "the run itself failed"
  f=$(state_file "$p")
  assert_eq "3" "$(jq -r '.items[0].metrics.reviewRounds' "$f")" "the run takes the rounds it was budgeted"
  assert_eq "3" "$(jq -r '.run.maxReviewRounds' "$f")" "and records the budget it was given"
  assert_contains "$(run_report "$p")" "review rounds 3" "the report states it beside the other budgets"
}

# Zero rounds is not a cheap review: the item would be blocked on a round that never ran, and the branch would
# carry a `review-failed-round-0` that refuses it for ever (review of #187).
case_run_aZeroRoundBudgetIsRefused() {
  local d p out status
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  out=$(run_loop "$p" --items 1 --minutes 90 --review on --max-review-rounds 0 2>&1); status=$?
  assert_eq "2" "$status" "a zero round budget is a usage error"
  assert_contains "$out" "run: --max-review-rounds must be a positive integer" "and the run itself says what it wants"
  [ ! -f "$(state_file "$p")" ] || fail "the run wrote state before refusing"
}

# A report whose findings are strictly subtractive needs no new claim, case or assertion — one worker pass can
# answer it — so a corrective refusal at the cap earns exactly one more round, and a second refusal blocks
# (#187). The extension is `LOOP-CORRECTIONS-ONLY: yes` in the reviewer's report, which the prompt asks for.
case_run_aCorrectiveRefusalEarnsOneMoreRound() {
  local d p f
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: FAIL
LOOP-CORRECTIONS-ONLY: yes
LOOP-DECISION: merge as it stands, apply the corrections, or abandon it | recommended: apply the corrections'
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  f=$(state_file "$p")
  assert_eq "3" "$(jq -r '.items[0].metrics.reviewRounds' "$f")" \
    "a corrective refusal earns the third round"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$f")" "and a third refusal still blocks the item"
}

# The escalation is what a person reads, so it carries the decision the reviewer already worked out: the
# question, the options and the recommendation (#186). The reason alone ("a decision is needed") is what the
# owner had to reconstruct.
case_run_escalationCarriesTheDecision() {
  local d p log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: FAIL
LOOP-CORRECTIONS-ONLY: no
LOOP-DECISION: merge as it stands, apply the corrections, or abandon it | recommended: apply the corrections'
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  log=$(cat "$GH_STUB_RECORD")
  assert_contains "$log" "**The decision:**" "the escalation names the decision it asks for"
  assert_contains "$log" "recommended: apply the corrections" "including the reviewer's recommendation"
}

case_run_mayCloseNoLeavesTheIssueOpen() {
  local d p
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" --may-close no >/dev/null || fail "the run failed"
  assert_contains "$(cat "$GH_STUB_RECORD")" "--add-label status/fixed-by" "the item keeps its fixed state"
  assert_eq "1" "$(grep -c 'issue comment' "$GH_STUB_RECORD" || true)" "the evidence comment is still posted"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "the item stays open"
  assert_eq "skipped" "$(jq -r '.items[0].metrics.closed' "$(state_file "$p")")" "the closure is recorded as skipped"
  assert_eq "done" "$(jq -r '.items[0].result' "$(state_file "$p")")" "the work itself completed"
}

case_run_metricsRecordedPerItem() {
  local d p f
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  f="$(state_file "$p")"
  assert_eq "1" "$(jq -r '.items | length' "$f")" "one item was recorded"
  assert_eq "true" "$(jq -r '.items[0].metrics.wallSeconds > 0' "$f")" "a positive wall time"
  assert_eq "0" "$(jq -r '.items[0].metrics.piExit' "$f")" "the child's exit status"
  assert_eq "1" "$(jq -r '.items[0].metrics.reviewRounds' "$f")" "the review rounds"
  assert_eq "true" "$(jq -r '(.items[0].metrics.gate | length) > 0' "$f")" "the gate tally the child reported"
  assert_eq "true" "$(jq -r '.items[0].metrics.closed == "yes"' "$f")" "whether it closed"
  assert_eq "true" "$(jq -r '(.items[0].metrics.rootCause | length) > 0' "$f")" "the root cause"
  assert_eq "true" "$(jq -r '(.items[0].metrics.redTest | length) > 0' "$f")" "the red test"
}

case_run_commitsWithoutAnAmbientIdentity() {
  # A container or a CI runner has no `user.name`/`user.email` anywhere: git then refuses every commit, and
  # the run would block on its own commit (measured 2026-10-18, issue #167). The throwaway repository's local
  # identity is removed as well, so the case is the runner's environment and not the developer's.
  local d p login branch count author
  d=$(scratch); p=$(new_project)
  git -C "$p" config --unset user.name >/dev/null 2>&1 || true
  git -C "$p" config --unset user.email >/dev/null 2>&1 || true
  printf '[init]\n\tdefaultBranch = main\n' > "$d/nohome.gitconfig"
  mkdir -p "$d/nohome"
  export GIT_CONFIG_GLOBAL="$d/nohome.gitconfig" GIT_CONFIG_SYSTEM=/dev/null HOME="$d/nohome"

  run_one_item "$p" "$d" >/dev/null || fail "a machine without an ambient git identity could not run an item"
  assert_eq "done" "$(jq -r '.items[0].result' "$(state_file "$p")")" "the item completed"

  # Both commits carry the run's own identity: the login the gate preflight resolved (the stub `gh` answers
  # it), as that login's noreply address.
  login=$(tr -d '\n' < "$LOOP_ISSUE_FIXTURES/login.txt")
  branch=$(git -C "$p" for-each-ref --format='%(refname:short)' 'refs/heads/fix/*' | head -n 1)
  [ -n "$branch" ] || fail "the item's branch does not exist"
  count=$(git -C "$p" rev-list --count "$branch" "^$(git -C "$p" rev-parse main)" 2>/dev/null || printf '0')
  [ "$count" -eq 2 ] || fail "the run did not commit twice on $branch (commits since main: $count)"
  author=$(git -C "$p" log -n 2 --format='%an <%ae>' "$branch" | sort -u)
  assert_eq "$login <$login@users.noreply.github.com>" "$author" "both commits are attributed to the run's identity"
}

case_run_emptyQueueIsNotAFailure() {
  local d p out status
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario none)
  stub_success "$d"
  out=$(run_loop "$p" --items 1 --minutes 90 2>&1); status=$?
  assert_eq "0" "$status" "an empty queue is not a failure"
  assert_contains "$out" "nothing eligible" "the report says so"
  [ ! -d "$p/.pi/worktrees/loop" ] || fail "a worktree was created for an empty queue"
  assert_eq "" "$(cat "$PI_STUB_LOG" 2>/dev/null || true)" "no agent process was started"

  out=$(run_loop "$p" --items 0 --minutes 90 2>&1); status=$?
  assert_eq "0" "$status" "an item budget of zero is not a failure"
  assert_contains "$out" "nothing eligible" "and it says the same thing"
}

case_run_reviewerWriteInvalidatesTheRound() {
  local d p
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'case "$1" in reviewer*) printf "the reviewer wrote\n" >> demo-fix.txt ;; esac'
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" "a reviewer that wrote cannot have been the reviewer"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "working tree changed" "the round is discarded with a reason"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "nothing is closed on a discarded round"
}

case_run_reviewReadOnlyOffOnlyForDiagnosis() {
  local d p
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'case "$1" in reviewer*) printf "the reviewer wrote\n" >> demo-fix.txt ;; esac'
  run_loop "$p" --items 1 --minutes 90 --review on --review-read-only off >/dev/null 2>&1 ||
    fail "the run itself failed"
  assert_eq "done" "$(jq -r '.items[0].result' "$(state_file "$p")")" "with the switch off the round is accepted"
  assert_eq "yes" "$(jq -r '.items[0].metrics.closed' "$(state_file "$p")")" "and the item closes"
}

case_run_engineNamesNoProjectFact() {
  local hits sbxdir newpath d p
  hits=$(grep -rn 'pi --print' "$LOOP_ENGINE"/*.sh 2>/dev/null || true)
  [ -z "$hits" ] || fail "a script names the agent command instead of reading it: $hits"
  hits=$(grep -rn 'openspec-apply-change\|build-app\|run-tests\|revert-check\|gradlew\|adb\|emulator\|sbx' \
    "$LOOP_ENGINE"/*.sh "$LOOP_ENGINE/prompts/" 2>/dev/null || true)
  [ -z "$hits" ] || fail "a phase or a prompt names a project fact of its own: $hits"
  assert_contains "$(cat "$LOOP_ENGINE/loop.conf")" "LOOP_AGENT_CMD" "the configuration is where those facts live"

  # and the run does not need a sandbox tool: the same run with `sbx` off PATH
  d=$(scratch); p=$(new_project)
  sbxdir=$(command -v sbx 2>/dev/null || true)
  newpath="$PATH"
  if [ -n "$sbxdir" ]; then
    newpath=$(printf '%s' "$PATH" | tr ':' '\n' | grep -vxF "$(dirname "$sbxdir")" | paste -sd: -)
  fi
  PATH="$newpath" run_one_item "$p" "$d" >/dev/null || fail "the run needs something the sandbox would provide"
  return 0
}

# The stop conditions are the run's only self-limits (`state.sh next`: item cap, deadline, empty queue, two
# consecutive blocked items, the remaining-time floor) and `next` names the one that fired on stdout. The run
# used to capture that line and drop it, so the report's counters were the whole account of a run that ended
# for a reason nothing recorded (#184). The assertion is that the three readers agree: the run's own output,
# the state file, and the report printed afterwards.
case_run_stopReasonIsPrintedAndReported() {
  local d p out f run_part stop_line recorded
  d=$(scratch); p=$(new_project)
  out=$(run_one_item "$p" "$d" 2>&1) || fail "the run itself failed"
  f=$(state_file "$p")
  # The run's own line, not the report's: the report is printed by the run's end and carries the same text, so
  # a case that grepped the whole output would pass with the run saying nothing at all (mutation 16.2 hid
  # behind exactly that until the two were separated).
  run_part=$(printf '%s\n' "$out" | sed '/\*\*agent-issue-loop/,$d')
  stop_line=$(printf '%s\n' "$run_part" | grep '^stop: ' | head -1 || true)
  [ -n "$stop_line" ] || fail "the run never names the condition that ended it: $run_part"
  recorded=$(jq -r '.run.stop.reason // ""' "$f")
  [ -n "$recorded" ] || fail "the state records no stop reason"
  assert_eq "stop: $recorded" "$stop_line" "the printed line and the recorded reason are one condition"
  out=$(run_report "$p")
  assert_contains "$out" "stop: $recorded" "the report repeats it, so its counters and its end agree"
}

# The pre-screen's decision exclusions reach the tracker through one path (`escalate_prescreen_decisions`), and
# an item that already carries the escalation label is not a new decision: escalating it again posts a second
# identical comment, which is what #110 collected three of (#183).
case_run_awaitingDecisionIsNotEscalatedAgain() {
  local d p out log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario decisions)
  export GH_STUB_RECORD="$p/gh.log"; : > "$GH_STUB_RECORD"
  stub_success "$d"

  out=$(run_loop "$p" --items 1 --minutes 90 --max-decisions 10 2>&1)
  assert_contains "$out" "nothing eligible" "a backlog awaiting decisions produces no work"
  log=$(cat "$GH_STUB_RECORD")
  for issue in 41 42 43; do
    assert_eq "0" "$(printf '%s\n' "$log" | grep -c "issue comment $issue " || true)" \
      "no second escalation comment is posted on #$issue"
    assert_eq "0" "$(printf '%s\n' "$log" | grep -c "issue edit $issue " || true)" \
      "nor a label write on #$issue, which already carries its decision"
  done
  # #44 is a hold nobody escalated, and a hold is not a decision (#193): it is left alone, so no escalation
  # comment and no label write happens for it either — the case's own point is that nothing is written twice,
  # and a boilerplate escalation written once was the defect rather than the guard.
  assert_eq "0" "$(printf '%s\n' "$log" | grep -c 'issue comment 44 ' || true)" \
    "the bare hold is not escalated (#44)"
  assert_eq "0" "$(printf '%s\n' "$log" | grep -c 'issue edit 44 ' || true)" \
    "and it carries no label write either (#44)"
  assert_eq "held" "$(jq -r '.excluded[] | select(.issue == 44) | .verdict' "$(state_file "$p")")" \
    "the run records it as held, which the report prints"
  assert_eq "" "$(cat "$PI_STUB_LOG" 2>/dev/null || true)" "no agent process was started"
  assert_eq "0" "$(jq -r '.items | length' "$(state_file "$p")")" "and nothing is recorded as worked"
}

# ----------------------------------------------------------------------------- cases: report

case_report_headerAndTableShape() {
  local d p out rows
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  out=$(run_report "$p") || fail "the report failed"
  assert_contains "$out" "budget 1 items / 90 min" "the header repeats the budget actually used"
  assert_contains "$out" "base $(git -C "$p" rev-parse HEAD | cut -c1-12)" "the header names the base commit"
  assert_contains "$out" "review on" "the header repeats the flags"
  rows=$(printf '%s' "$out" | grep -c '^| #21 |' || true)
  assert_eq "$(jq -r '.items | length' "$(state_file "$p")")" "$rows" "one table row per recorded item"
  assert_contains "$out" "the fixture has exactly one cause" "the row carries the root cause"
  assert_contains "$out" "branches:" "the report lists the branches"
}

case_report_yieldIsCountedFromState() {
  local p out rows
  p=$(new_project)
  init_state "$p" 3 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result skipped --verdict needs-decision >/dev/null
  run_state "$p" done 22 --result blocked --verdict blocked >/dev/null
  out=$(run_report "$p") || fail "the report failed"
  assert_contains "$out" "closed 0 · skipped 1 · blocked 1" "the yield is counted from the state file"
  rows=$(printf '%s' "$out" | grep -c '^| #2' || true)
  assert_eq "2" "$rows" "every recorded item has a row"
  assert_contains "$out" "#21 needs-decision" "a skip is reported with its verdict"
}

# The decision list is what the run *wrote*, not the verdict a worker chose (issue #177): a skip whose
# verdict means a decision ends on `agent/needs-decision` while the state records `needs-diagnosis`, and
# the run that fills the cap has to show the cap coming in its own report.
decisions_line() { printf '%s\n' "$1" | sed -n 's/^decisions the owner owes: //p'; }

case_report_escalatedSkipNamesTheLabelWritten() {
  local d p out line log named escalated
  d=$(scratch); p=$(new_project)
  # Two candidates, one budget slot: the queue never empties, so the run's own second pre-screen pass —
  # which would re-exclude the worked item on its branch — cannot supply the report's list by accident.
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario two)
  stub_write "$d" 'LOOP-VERDICT: skipped
LOOP-SKIP-VERDICT: needs-diagnosis
LOOP-ROOT-CAUSE: two mechanisms explain the symptom
LOOP-RED-TEST: Demo#case was never reached' 'VERDICT: PASS'
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1) || fail "the run itself failed"
  log=$(cat "$GH_STUB_RECORD")
  assert_eq "needs-diagnosis" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" \
    "the verdict the worker chose is not the decision label"
  assert_contains "$log" "agent/needs-decision" "the escalation wrote the decision label on #21"
  line=$(decisions_line "$out")
  assert_eq "#21" "$line" "the report names the item the run escalated, not the verdict it chose"
  # …and the two agree by construction: one item awaiting a decision in the report, one item escalated in
  # the tracker. On HEAD the report said `none` while the label had just been written.
  named=$(printf '%s' "$line" | tr ' ' '\n' | grep -c '^#' || true)
  escalated=$(printf '%s' "$log" | grep -c 'agent/needs-decision' || true)
  assert_eq "$escalated" "$named" "the report names as many decisions as the run escalated"
}

case_report_failedReviewEscalationIsNamed() {
  local d p out line
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario two)
  # Two failed review rounds end the item blocked — its verdict is `blocked`, and the label written is the
  # decision label (the state verdict names neither).
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: FAIL'
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1) || fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" "the recorded verdict is blocked"
  assert_contains "$(cat "$GH_STUB_RECORD")" "agent/needs-decision" "the run escalated the item"
  assert_eq "#21" "$(decisions_line "$out")" "the report names the blocked item as a decision owed"
}

case_report_aVerdictAloneIsNoEscalation() {
  local p out line
  p=$(new_project)
  init_state "$p" 2 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result blocked --verdict blocked >/dev/null
  run_state "$p" done 22 --result skipped --verdict needs-diagnosis --decision-label agent/needs-decision >/dev/null
  out=$(run_report "$p") || fail "the report failed"
  line=$(decisions_line "$out")
  assert_eq "#22" "$line" "only the item whose escalation recorded the label is listed"
  assert_not_contains "$line" "#21" "a verdict alone names no decision"
}

