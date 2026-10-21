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
  'state#floorIsDerivedFromTheFinishedItemsWallTimes'
  'state#floorWithNoHistoryIsTheConfiguredMinimum'
  'state#secondRunRefusesWithExit5'
  'state#initKeepsTheStateItReplaces'
  'state#initAnnouncesARunWithoutAStop'
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
  'prescreen#aFinishedAttemptBranchIsADecision'
  'prescreen#aBlockedAttemptBranchIsWork'
  'prescreen#landedBranchIsNotADecision'
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
  'worktree#keepsSourcesNotBuildOutput'
  'worktree#noPushWithoutTheFlag'
  'worktree#reusesABranchThatCarriesNoWorkOfItsOwn'
  'worktree#reusesABranchWhoseWorkAlreadyLanded'
  'worktree#refusesABranchCarryingAPreviousAttempt'
  'worktree#setsAsideABlockedAttemptAndStartsFresh'
  'worktree#doesNotAdoptAChangeCreditedToAnotherIssue'
  'worktree#refusesABranchWhoseReviewFailed'
  'worktree#refusesToMergeOverADirtyProjectTree'
  'worktree#refusesToReplayABranchTheBaseDoesNotDescendFrom'
  'labels#verdictToLabelMatrix'
  'labels#holdCarriesItsReason'
  'labels#trapRemovesInProgressOnSignal'
  'labels#neverWritesReservedLabel'
  'labels#writesOnlyBetweenChildren'
  'labels#closeRemovesTheDecisionLabel'
  'prompt#workerNamesEveryPhaseAndSkill'
  'prompt#workerNamesTheDecisionField'
  'prompt#workerTreatsARecordedDecisionAsSettled'
  'prompt#reviewerCarriesTheEightItems'
  'prompt#packageIsAFileAndNotInlined'
  'prompt#inlineInjectionCannotReachTheInstructionPosition'
  'run#preflightRefusalStopsBeforeAnyWork'
  'run#blockedItemAsksItsOwnQuestion'
  'run#falsifiedSkipClosesWithItsMeasurement'
  'run#falsificationWithoutAMeasurementIsEscalated'
  'run#mayCloseNoKeepsAFalsificationOpen'
  'run#falsificationWithAChangedTreeIsBlocked'
  'run#decisionCapRefusesToStart'
  'run#oneItemOrderIsFixed'
  'run#childClaimWithoutCommitIsBlocked'
  'run#blockedItemIsEscalatedWithItsReason'
  'run#childStartsInTheProjectRoot'
  'run#childReportsWhileItWaits'
  'run#headerNamesTheLiveView'
  'run#prescreenAnnouncesItselfBeforeItReturns'
  'run#everyPhaseBoundaryIsPrinted'
  'run#childOutlastingTheWallClockIsBlocked'
  'run#childThatNeverStartedIsTheRunsFault'
  'run#hostBelowTheFloorBlocksTheItemAndStops'
  'run#hostAboveTheFloorRunsTheItem'
  'run#hostWithoutTheReadingPasses'
  'run#childWriteOutsideTheWorktreeIsBlocked'

  'run#reviewerWriteInTheProjectIsDiscarded'
  'run#leftoverBranchIsRecordedNotDropped'
  'run#branchThatAppearsWhileTheRunIsOpenIsSetAside'
  'run#mergesAFinishedAttemptAndClosesTheItem'
  'run#doesNotMergeAnAttemptWithoutAGreenVerification'
  'run#mergesItsCommittedBranchBeforeClosing'
  'run#doesNotCloseOnAMergeTheBaseCannotTake'
  'run#recordsAFailedReviewOnTheBranch'
  'run#dirtyKeptWorktreeIsARecordedRefusal'
  'run#resetWorktreeDiscardsWhatItNames'
  'run#aSkipClearsTheInProgressMark'
  'run#skipEscalationCarriesTheDecision'
  'run#workPackageCarriesTheRecordedDecision'
  'run#claimedGateWithoutEvidenceIsBlocked'
  'run#citedEvidenceMustTravelWithTheChange'
  'run#needsDecisionIsEscalatedWithTheWork'
  'run#noGateStillRuns'
  'run#dryRunTouchesNothing'
  'run#reviewRoundsCapAtTwo'
  'run#reviewBudgetIsARecordedValue'
  'run#aZeroRoundBudgetIsRefused'
  'run#aCorrectiveRefusalEarnsOneMoreRound'
  'run#aZeroBlockingRefusalClosesWithItsNotes'
  'run#aBlockingRefusalStillTakesTheRoundTwoPath'
  'run#aCutReportIsRetriedInsteadOfRefused'
  'run#aReportCutTwiceBlocksAsTheTransportsFault'
  'run#escalationCarriesTheDecision'
  'run#mayCloseNoLeavesTheIssueOpen'
  'run#metricsRecordedPerItem'
  'run#childUsageIsRecordedPerItem'
  'run#plainTextChildStillReports'
  'run#emptyQueueIsNotAFailure'
  'run#stopReasonIsPrintedAndReported'
  'run#staleBaseStopsTheRunWithItsReason'
  'run#awaitingDecisionIsNotEscalatedAgain'
  'run#commitsWithoutAnAmbientIdentity'
  'run#reviewerWriteInvalidatesTheRound'
  'run#reviewReadOnlyOffOnlyForDiagnosis'
  'run#reviewDiffExcludesTheEvidenceArchive'
  'run#engineNamesNoProjectFact'
  'adapter#modeFlagIsItsOwnArgument'
  'state#inheritedStatePathOutsideTheTreeIsIgnored'
  'report#headerAndTableShape'
  'report#yieldIsCountedFromState'
  'report#escalatedSkipNamesTheLabelWritten'
  'report#failedReviewEscalationIsNamed'
  'report#aVerdictAloneIsNoEscalation'
  'report#misdiagnosedIsCountedApart'
  'report#archivedStateIsReported'
  'report#impossibleBudgetNamesAnotherRun'
  'report#wallTimeAndReasonInTheTable'
  'report#zeroUsagePrintsDashNotZero'
  'report#actionsNameDecisionAndBranch'
  'report#landedBranchIsNoAction'
  'report#emptyRunSaysNone'
  'clean#interruptedSuiteRemovesItsScratchTree'
  'clean#reportsWithoutApplyAndRemovesWithIt'
  'clean#interruptedSuiteEndsItsProcessGroup'
  'clean#reportsAndEndsAProcessLeftInAScratchTree'
  'run#interruptedRunEndsTheChildItStarted'
  'usage#bareValueFlagAtTheEndOfArgvIsAUsageError'
  'usage#bareValueFlagDoesNotSwallowTheNextFlag'
  'usage#bareValueFlagInASingleShotHarnessIsAUsageError'
  'usage#noUnguardedValueFlagRemains'
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
    'state#floorIsDerivedFromTheFinishedItemsWallTimes') fn=case_state_floorIsDerivedFromTheFinishedItemsWallTimes ;;
    'state#floorWithNoHistoryIsTheConfiguredMinimum') fn=case_state_floorWithNoHistoryIsTheConfiguredMinimum ;;
    'state#secondRunRefusesWithExit5') fn=case_state_secondRunRefusesWithExit5 ;;
    'state#initKeepsTheStateItReplaces') fn=case_state_initKeepsTheStateItReplaces ;;
    'state#initAnnouncesARunWithoutAStop') fn=case_state_initAnnouncesARunWithoutAStop ;;
    'state#defaultsComeFromLoopConf') fn=case_state_defaultsComeFromLoopConf ;;
    'state#projectKeysAreReadFromLoopConf') fn=case_state_projectKeysAreReadFromLoopConf ;;
    'state#agentCommandIsTheOnlyIndirection') fn=case_state_agentCommandIsTheOnlyIndirection ;;
    'state#agentCommandRefusesAnEmptyValue') fn=case_state_agentCommandRefusesAnEmptyValue ;;
    'state#inheritedStatePathOutsideTheTreeIsIgnored') fn=case_state_inheritedStatePathOutsideTheTreeIsIgnored ;;
    'prescreen#ranksBugsFirstAndDropsFeatures') fn=case_prescreen_ranksBugsFirstAndDropsFeatures ;;
    'prescreen#missingPathYieldsStale') fn=case_prescreen_missingPathYieldsStale ;;
    'prescreen#doesNotClaimJudgementGates') fn=case_prescreen_doesNotClaimJudgementGates ;;
    'prescreen#refusesReservedLabelInSelection') fn=case_prescreen_refusesReservedLabelInSelection ;;
    'prescreen#withoutTheOptInIsNeverQueued') fn=case_prescreen_withoutTheOptInIsNeverQueued ;;
    'prescreen#settledItemIsDropped') fn=case_prescreen_settledItemIsDropped ;;
    'prescreen#deviceItemIsAVerdictNotWork') fn=case_prescreen_deviceItemIsAVerdictNotWork ;;
    'prescreen#readsTheVerdictsFromTheProfile') fn=case_prescreen_readsTheVerdictsFromTheProfile ;;
    'prescreen#aFinishedAttemptBranchIsADecision') fn=case_prescreen_aFinishedAttemptBranchIsADecision ;;
    'prescreen#aBlockedAttemptBranchIsWork') fn=case_prescreen_aBlockedAttemptBranchIsWork ;;
    'prescreen#landedBranchIsNotADecision') fn=case_prescreen_landedBranchIsNotADecision ;;
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
    'worktree#keepsSourcesNotBuildOutput') fn=case_worktree_keepsSourcesNotBuildOutput ;;
    'worktree#noPushWithoutTheFlag') fn=case_worktree_noPushWithoutTheFlag ;;
    'worktree#reusesABranchThatCarriesNoWorkOfItsOwn') fn=case_worktree_reusesABranchThatCarriesNoWorkOfItsOwn ;;
    'worktree#reusesABranchWhoseWorkAlreadyLanded') fn=case_worktree_reusesABranchWhoseWorkAlreadyLanded ;;
    'worktree#refusesABranchCarryingAPreviousAttempt') fn=case_worktree_refusesABranchCarryingAPreviousAttempt ;;
    'worktree#setsAsideABlockedAttemptAndStartsFresh') fn=case_worktree_setsAsideABlockedAttemptAndStartsFresh ;;
    'worktree#doesNotAdoptAChangeCreditedToAnotherIssue') fn=case_worktree_doesNotAdoptAChangeCreditedToAnotherIssue ;;
    'worktree#refusesABranchWhoseReviewFailed') fn=case_worktree_refusesABranchWhoseReviewFailed ;;
    'worktree#refusesToMergeOverADirtyProjectTree') fn=case_worktree_refusesToMergeOverADirtyProjectTree ;;
    'worktree#refusesToReplayABranchTheBaseDoesNotDescendFrom') fn=case_worktree_refusesToReplayABranchTheBaseDoesNotDescendFrom ;;
    'labels#verdictToLabelMatrix') fn=case_labels_verdictToLabelMatrix ;;
    'labels#holdCarriesItsReason') fn=case_labels_holdCarriesItsReason ;;
    'labels#trapRemovesInProgressOnSignal') fn=case_labels_trapRemovesInProgressOnSignal ;;
    'labels#neverWritesReservedLabel') fn=case_labels_neverWritesReservedLabel ;;
    'labels#writesOnlyBetweenChildren') fn=case_labels_writesOnlyBetweenChildren ;;
    'labels#closeRemovesTheDecisionLabel') fn=case_labels_closeRemovesTheDecisionLabel ;;
    'prompt#workerNamesEveryPhaseAndSkill') fn=case_prompt_workerNamesEveryPhaseAndSkill ;;
    'prompt#workerNamesTheDecisionField') fn=case_prompt_workerNamesTheDecisionField ;;
    'prompt#workerTreatsARecordedDecisionAsSettled') fn=case_prompt_workerTreatsARecordedDecisionAsSettled ;;
    'prompt#reviewerCarriesTheEightItems') fn=case_prompt_reviewerCarriesTheEightItems ;;
    'prompt#packageIsAFileAndNotInlined') fn=case_prompt_packageIsAFileAndNotInlined ;;
    'prompt#inlineInjectionCannotReachTheInstructionPosition') fn=case_prompt_inlineInjectionCannotReachTheInstructionPosition ;;
    'run#preflightRefusalStopsBeforeAnyWork') fn=case_run_preflightRefusalStopsBeforeAnyWork ;;
    'run#blockedItemAsksItsOwnQuestion') fn=case_run_blockedItemAsksItsOwnQuestion ;;
    'run#falsifiedSkipClosesWithItsMeasurement') fn=case_run_falsifiedSkipClosesWithItsMeasurement ;;
    'run#falsificationWithoutAMeasurementIsEscalated') fn=case_run_falsificationWithoutAMeasurementIsEscalated ;;
    'run#mayCloseNoKeepsAFalsificationOpen') fn=case_run_mayCloseNoKeepsAFalsificationOpen ;;
    'run#falsificationWithAChangedTreeIsBlocked') fn=case_run_falsificationWithAChangedTreeIsBlocked ;;
    'run#decisionCapRefusesToStart') fn=case_run_decisionCapRefusesToStart ;;
    'run#oneItemOrderIsFixed') fn=case_run_oneItemOrderIsFixed ;;
    'run#childClaimWithoutCommitIsBlocked') fn=case_run_childClaimWithoutCommitIsBlocked ;;
    'run#blockedItemIsEscalatedWithItsReason') fn=case_run_blockedItemIsEscalatedWithItsReason ;;
    'run#childStartsInTheProjectRoot') fn=case_run_childStartsInTheProjectRoot ;;
    'run#childReportsWhileItWaits') fn=case_run_childReportsWhileItWaits ;;
    'run#headerNamesTheLiveView') fn=case_run_headerNamesTheLiveView ;;
    'run#prescreenAnnouncesItselfBeforeItReturns') fn=case_run_prescreenAnnouncesItselfBeforeItReturns ;;
    'run#everyPhaseBoundaryIsPrinted') fn=case_run_everyPhaseBoundaryIsPrinted ;;
    'run#childOutlastingTheWallClockIsBlocked') fn=case_run_childOutlastingTheWallClockIsBlocked ;;
    'run#childThatNeverStartedIsTheRunsFault') fn=case_run_childThatNeverStartedIsTheRunsFault ;;
    'run#hostBelowTheFloorBlocksTheItemAndStops') fn=case_run_hostBelowTheFloorBlocksTheItemAndStops ;;
    'run#hostAboveTheFloorRunsTheItem') fn=case_run_hostAboveTheFloorRunsTheItem ;;
    'run#hostWithoutTheReadingPasses') fn=case_run_hostWithoutTheReadingPasses ;;
    'run#childWriteOutsideTheWorktreeIsBlocked') fn=case_run_childWriteOutsideTheWorktreeIsBlocked ;;
    'run#reviewerWriteInTheProjectIsDiscarded') fn=case_run_reviewerWriteInTheProjectIsDiscarded ;;
    'run#leftoverBranchIsRecordedNotDropped') fn=case_run_leftoverBranchIsRecordedNotDropped ;;
    'run#branchThatAppearsWhileTheRunIsOpenIsSetAside') fn=case_run_branchThatAppearsWhileTheRunIsOpenIsSetAside ;;
    'run#mergesAFinishedAttemptAndClosesTheItem') fn=case_run_mergesAFinishedAttemptAndClosesTheItem ;;
    'run#doesNotMergeAnAttemptWithoutAGreenVerification') fn=case_run_doesNotMergeAnAttemptWithoutAGreenVerification ;;
    'run#mergesItsCommittedBranchBeforeClosing') fn=case_run_mergesItsCommittedBranchBeforeClosing ;;
    'run#doesNotCloseOnAMergeTheBaseCannotTake') fn=case_run_doesNotCloseOnAMergeTheBaseCannotTake ;;
    'run#recordsAFailedReviewOnTheBranch') fn=case_run_recordsAFailedReviewOnTheBranch ;;
    'run#dirtyKeptWorktreeIsARecordedRefusal') fn=case_run_dirtyKeptWorktreeIsARecordedRefusal ;;
    'run#resetWorktreeDiscardsWhatItNames') fn=case_run_resetWorktreeDiscardsWhatItNames ;;
    'run#aSkipClearsTheInProgressMark') fn=case_run_aSkipClearsTheInProgressMark ;;
    'run#skipEscalationCarriesTheDecision') fn=case_run_skipEscalationCarriesTheDecision ;;
    'run#workPackageCarriesTheRecordedDecision') fn=case_run_workPackageCarriesTheRecordedDecision ;;
    'run#claimedGateWithoutEvidenceIsBlocked') fn=case_run_claimedGateWithoutEvidenceIsBlocked ;;
    'run#citedEvidenceMustTravelWithTheChange') fn=case_run_citedEvidenceMustTravelWithTheChange ;;
    'run#needsDecisionIsEscalatedWithTheWork') fn=case_run_needsDecisionIsEscalatedWithTheWork ;;
    'run#noGateStillRuns') fn=case_run_noGateStillRuns ;;
    'run#dryRunTouchesNothing') fn=case_run_dryRunTouchesNothing ;;
    'run#reviewRoundsCapAtTwo') fn=case_run_reviewRoundsCapAtTwo ;;
    'run#reviewBudgetIsARecordedValue') fn=case_run_reviewBudgetIsARecordedValue ;;
    'run#aZeroRoundBudgetIsRefused') fn=case_run_aZeroRoundBudgetIsRefused ;;
    'run#aCorrectiveRefusalEarnsOneMoreRound') fn=case_run_aCorrectiveRefusalEarnsOneMoreRound ;;
    'run#aZeroBlockingRefusalClosesWithItsNotes') fn=case_run_aZeroBlockingRefusalClosesWithItsNotes ;;
    'run#aBlockingRefusalStillTakesTheRoundTwoPath') fn=case_run_aBlockingRefusalStillTakesTheRoundTwoPath ;;
    'run#aCutReportIsRetriedInsteadOfRefused') fn=case_run_aCutReportIsRetriedInsteadOfRefused ;;
    'run#aReportCutTwiceBlocksAsTheTransportsFault') fn=case_run_aReportCutTwiceBlocksAsTheTransportsFault ;;
    'run#escalationCarriesTheDecision') fn=case_run_escalationCarriesTheDecision ;;
    'run#mayCloseNoLeavesTheIssueOpen') fn=case_run_mayCloseNoLeavesTheIssueOpen ;;
    'run#metricsRecordedPerItem') fn=case_run_metricsRecordedPerItem ;;
    'run#childUsageIsRecordedPerItem') fn=case_run_childUsageIsRecordedPerItem ;;
    'run#plainTextChildStillReports') fn=case_run_plainTextChildStillReports ;;
    'run#emptyQueueIsNotAFailure') fn=case_run_emptyQueueIsNotAFailure ;;
    'run#stopReasonIsPrintedAndReported') fn=case_run_stopReasonIsPrintedAndReported ;;
    'run#staleBaseStopsTheRunWithItsReason') fn=case_run_staleBaseStopsTheRunWithItsReason ;;
    'run#awaitingDecisionIsNotEscalatedAgain') fn=case_run_awaitingDecisionIsNotEscalatedAgain ;;
    'run#commitsWithoutAnAmbientIdentity') fn=case_run_commitsWithoutAnAmbientIdentity ;;
    'run#reviewerWriteInvalidatesTheRound') fn=case_run_reviewerWriteInvalidatesTheRound ;;
    'run#reviewReadOnlyOffOnlyForDiagnosis') fn=case_run_reviewReadOnlyOffOnlyForDiagnosis ;;
    'run#reviewDiffExcludesTheEvidenceArchive') fn=case_run_reviewDiffExcludesTheEvidenceArchive ;;
    'run#engineNamesNoProjectFact') fn=case_run_engineNamesNoProjectFact ;;
    'adapter#modeFlagIsItsOwnArgument') fn=case_adapter_modeFlagIsItsOwnArgument ;;
    'usage#bareValueFlagAtTheEndOfArgvIsAUsageError') fn=case_usage_bareValueFlagAtTheEndOfArgvIsAUsageError ;;
    'usage#bareValueFlagDoesNotSwallowTheNextFlag') fn=case_usage_bareValueFlagDoesNotSwallowTheNextFlag ;;
    'usage#bareValueFlagInASingleShotHarnessIsAUsageError') fn=case_usage_bareValueFlagInASingleShotHarnessIsAUsageError ;;
    'usage#noUnguardedValueFlagRemains') fn=case_usage_noUnguardedValueFlagRemains ;;
    'report#headerAndTableShape') fn=case_report_headerAndTableShape ;;
    'report#yieldIsCountedFromState') fn=case_report_yieldIsCountedFromState ;;
    'report#escalatedSkipNamesTheLabelWritten') fn=case_report_escalatedSkipNamesTheLabelWritten ;;
    'report#failedReviewEscalationIsNamed') fn=case_report_failedReviewEscalationIsNamed ;;
    'report#aVerdictAloneIsNoEscalation') fn=case_report_aVerdictAloneIsNoEscalation ;;
    'report#misdiagnosedIsCountedApart') fn=case_report_misdiagnosedIsCountedApart ;;
    'report#archivedStateIsReported') fn=case_report_archivedStateIsReported ;;
    'report#impossibleBudgetNamesAnotherRun') fn=case_report_impossibleBudgetNamesAnotherRun ;;
    'report#wallTimeAndReasonInTheTable') fn=case_report_wallTimeAndReasonInTheTable ;;
    'report#zeroUsagePrintsDashNotZero') fn=case_report_zeroUsagePrintsDashNotZero ;;
    'report#actionsNameDecisionAndBranch') fn=case_report_actionsNameDecisionAndBranch ;;
    'report#landedBranchIsNoAction') fn=case_report_landedBranchIsNoAction ;;
    'report#emptyRunSaysNone') fn=case_report_emptyRunSaysNone ;;
    'clean#interruptedSuiteRemovesItsScratchTree') fn=case_clean_interruptedSuiteRemovesItsScratchTree ;;
    'clean#reportsWithoutApplyAndRemovesWithIt') fn=case_clean_reportsWithoutApplyAndRemovesWithIt ;;
    'clean#interruptedSuiteEndsItsProcessGroup') fn=case_clean_interruptedSuiteEndsItsProcessGroup ;;
    'clean#reportsAndEndsAProcessLeftInAScratchTree') fn=case_clean_reportsAndEndsAProcessLeftInAScratchTree ;;
    'run#interruptedRunEndsTheChildItStarted') fn=case_run_interruptedRunEndsTheChildItStarted ;;
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

assert_matches() { # assert_matches <text> <ere> <message> — the *shape* of a line, not a substring of it
  printf '%s\n' "$1" | grep -qE -- "$2" || fail "$3: no line matching /$2/ in: $1"
}

assert_line_count() { # assert_line_count <text> <ere> <expected> <message> — one line per boundary crossing
  local n
  n=$(printf '%s\n' "$1" | grep -cE -- "$2" || true)
  assert_eq "$3" "$n" "$4"
}

scratch() { mktemp -d "$LOOP_TMP/case.XXXXXX"; }

# A throwaway project: enough of this repository's shape that the loop's own scripts run unchanged — the
# profile the issue tooling discovers, a directory standing in for the submodule, a `local.prop*` the
# worktree does not inherit, and the gitignored skills it does not inherit either. `core/build` is ignored by
# its whole path and not by `build/`, so a *symlink* at that recorded scratch path is untracked and the
# removal has to refuse it on its own account (`worktree#keepsSourcesNotBuildOutput`).
new_project() {
  local dir repo
  dir=$(scratch); repo="$dir/repo"
  mkdir -p "$repo/tools" "$repo/app/src/main/cpp/libosmscout" \
           "$repo/.pi/skills/fix-loop" "$repo/.pi/skills/openspec-apply-change" \
           "$repo/openspec/changes/archive"
  cp "$LOOP_PROJECT_ROOT/tools/tooling.json" "$repo/tools/tooling.json"
  # The child adapter the configuration names is the project's own tooling, started relative to the project
  # root the way `LOOP_GATE_CMD` is: a project that wants the child's event stream copies this file beside
  # its own tools. Absent is a state a case can still run in (a plain-text agent command needs none).
  if [ -f "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" ]; then
    cp "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" "$repo/tools/pi-child-events.sh"
  fi
  printf '.pi/\napp/build/\nbuild/\ncore/build\nlocal.prop*\n' > "$repo/.gitignore"
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
run_clean() { local p="$1"; shift; ( cd "$p" && bash "$LOOP_ENGINE/loop-clean.sh" "$@" ); }
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

# A falsified skip: the worker measured that the item's premise does not hold. Both halves are there — what the
# item blames and what was measured instead, with how it was taken — and the tree is untouched.
STUB_FALSIFIED_REPORT='LOOP-VERDICT: skipped
LOOP-SKIP-VERDICT: falsified
LOOP-FALSIFIED: the acquire await the item blames
LOOP-MEASURED: 12-17 ms measured by MapCanvasViewModelNavEndRestoreTest, probe reverted
LOOP-ROOT-CAUSE: the acquire await the item blames
LOOP-RED-TEST: none — nothing was changed
LOOP-GATE: not run — the tree is unchanged'

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

# The floor a caller is refused by is the run's own measurement (issue #206): the median wall time of the
# items this run has finished, times the spread the measurements show (1.5), with `LOOP_ITEM_FLOOR_MINUTES`
# as its lower bound. Both numbers the refusal compared are named, so an operator can see whether the floor
# is honest.
case_state_floorIsDerivedFromTheFinishedItemsWallTimes() {
  local p conf_floor out status i
  local queue='[{"issue":21,"class":"bug","verdict":"eligible","rank":1},
                {"issue":22,"class":"bug","verdict":"eligible","rank":2},
                {"issue":23,"class":"bug","verdict":"eligible","rank":3},
                {"issue":24,"class":"bug","verdict":"eligible","rank":4}]'
  conf_floor=$(sed -n 's/^LOOP_ITEM_FLOOR_MINUTES=//p' "$LOOP_CONF")
  [ -n "$conf_floor" ] || fail "the configuration records no LOOP_ITEM_FLOOR_MINUTES"

  # three finished items of 60 min each: the median the state already holds decides the floor, and the
  # configured minimum no longer decides on its own (60 min x 1.5 = 90 min)
  p=$(new_project)
  init_state "$p" 9 28
  printf '%s' "$queue" | run_state "$p" queue >/dev/null
  for i in 21 22 23; do
    run_state "$p" done "$i" --result done --verdict closed --wall 3600 >/dev/null || fail "done #$i failed"
  done
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "3" "$status" "a floor above the remaining time stops the run"
  assert_contains "$out" "90 min floor" "the floor is the measured one, not the constant"
  assert_contains "$out" "median 60 min over 3 finished items" "the refusal names the median it derived"
  assert_contains "$out" "configured minimum $conf_floor" "the refusal names the configured minimum too"
  assert_not_contains "$out" "$conf_floor min floor" "the constant alone does not decide the refusal any more"

  # the same measured floor is a bound and not a block: a run with room to pay for an item continues
  p=$(new_project)
  init_state "$p" 9 180
  printf '%s' "$queue" | run_state "$p" queue >/dev/null
  for i in 21 22 23; do
    run_state "$p" done "$i" --result done --verdict closed --wall 3600 >/dev/null || fail "done #$i failed"
  done
  out=$(run_state "$p" next); status=$?
  assert_eq "0" "$status" "a remaining time above the measured floor starts the next item"
  assert_eq "24" "$out" "the next candidate"

  # three finished skips of 10 min each: the derived 15 min is below the configured minimum, which stays
  # the lower bound — a cheap history must not admit an item the documented range says costs 40-65 min
  p=$(new_project)
  init_state "$p" 9 28
  printf '%s' "$queue" | run_state "$p" queue >/dev/null
  for i in 21 22 23; do
    run_state "$p" done "$i" --result skipped --verdict needs-decision --wall 600 >/dev/null || fail "done #$i failed"
  done
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "3" "$status" "the configured minimum still stops a thin tail"
  assert_contains "$out" "$conf_floor min floor" "a cheap history keeps the configured minimum as the floor"
  assert_contains "$out" "median 10 min over 3 finished items" "the cheap median is named rather than hidden"
  # one expensive item among cheap ones does not move the median (600, 600, 6000 -> 10 min), which is the
  # failure a p90 floor would have: after a 100 min item it would refuse the cheap tail of the run
  p=$(new_project)
  init_state "$p" 9 60
  printf '%s' "$queue" | run_state "$p" queue >/dev/null
  for i in 21 22; do
    run_state "$p" done "$i" --result skipped --verdict needs-decision --wall 600 >/dev/null || fail "done #$i failed"
  done
  run_state "$p" done 23 --result done --verdict closed --wall 6000 >/dev/null || fail "done #23 failed"
  out=$(run_state "$p" next); status=$?
  assert_eq "0" "$status" "one expensive item does not raise the floor over a cheap history"
  assert_eq "24" "$out" "the cheap tail of the run is still taken"
}

case_state_floorWithNoHistoryIsTheConfiguredMinimum() {
  local p conf_floor out status
  conf_floor=$(sed -n 's/^LOOP_ITEM_FLOOR_MINUTES=//p' "$LOOP_CONF")
  [ -n "$conf_floor" ] || fail "the configuration records no LOOP_ITEM_FLOOR_MINUTES"

  # nothing finished yet: there is no measurement, so the configured value stands — and it is named
  p=$(new_project)
  init_state "$p" 3 30
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "3" "$status" "the configured floor stops a run with no history"
  assert_contains "$out" "$conf_floor min floor" "with no history the configured minimum is the floor"
  assert_contains "$out" "configured minimum $conf_floor" "the refusal names the number it compared against"
  assert_contains "$out" "no finished item records a wall time" "the refusal says why the floor is the constant"

  # an item recorded without a wall time is no measurement either
  p=$(new_project)
  init_state "$p" 3 30
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result done --verdict closed >/dev/null || fail "done failed"
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "3" "$status" "an item with no wall time leaves the floor at the minimum"
  assert_contains "$out" "$conf_floor min floor" "an absent wall time is not a measurement"
  assert_contains "$out" "no finished item records a wall time" "the absence is named, not silently ignored"
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

# A finished run's counters, verdicts and stop reason live in one file, and `init` writes over it: the file is
# kept first, under the start time it carries itself, so a run that has ended can still be asked what it did
# after the next run has started (issue #207).
case_state_initKeepsTheStateItReplaces() {
  local p sf runs first_started archived out
  p=$(new_project)
  init_state "$p" 2 90
  run_state "$p" stop "queue empty" >/dev/null || fail "stop failed"
  first_started=$(run_state "$p" get run.startedAt)
  sf=$(state_file "$p")

  init_state "$p" 3 30
  runs="$(dirname "$sf")/runs"
  archived="$runs/$first_started.json"
  [ -f "$archived" ] || fail "the replaced state was not kept at $archived (found: $(ls -A "$runs" 2>/dev/null))"
  assert_eq "2" "$(jq -r '.run.items' "$archived")" "the kept file is the run init replaced"
  assert_eq "queue empty" "$(jq -r '.run.stop.reason' "$archived")" "the kept file carries the stop reason"
  assert_eq "$first_started" "$(jq -r '.run.startedAt' "$archived")" "the archive is named by the run's own start time"
  assert_eq "3" "$(run_state "$p" get run.items)" "the new state is the one in place"

  # The kept file is a state like any other; that it can be reported on is `report#archivedStateIsReported`.
  out=$(run_report "$p" --state "$archived") || fail "report --state on the kept state failed"
  assert_contains "$out" "budget 2 items / 90 min" "the kept state is the run it was taken from"
}

# A run that was killed or crashed recorded no stop, and restarting over it must stay possible — a flag to
# confirm would make a crashed run unstartable — but the state it loses is announced on stderr, not dropped
# silently (issue #207).
case_state_initAnnouncesARunWithoutAStop() {
  local p out status
  p=$(new_project)
  init_state "$p" 1 90
  out=$(run_state "$p" init --items 2 --minutes 30 2>&1 >/dev/null); status=$?
  assert_eq "0" "$status" "a run without a stop reason is announced, never refused"
  assert_contains "$out" "never recorded a stop reason" "the announcement names what is being replaced"
  assert_eq "2" "$(run_state "$p" get run.items)" "the announced run is replaced, so the next run can start"

  # A run that did record why it ended is kept without the warning: the warning names a defect, not the norm.
  run_state "$p" stop "queue empty" >/dev/null || fail "stop failed"
  out=$(run_state "$p" init --items 2 --minutes 30 2>&1 >/dev/null)
  assert_not_contains "$out" "never recorded a stop reason" "a finished run is replaced without a warning"
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

# A child is started with the run's identity in its environment (`run_child` exports `LOOP_STATE_FILE` and
# `LOOP_WORKTREE` so all of the run's children agree on it), and the gate of any change that touches
# `tools/loop/**` is this very suite. Measured 2026-10-20 (issue #214): a live run's state was replaced five
# times — each time with a fixture's base, queue and budget — while its worker was still working the item, and
# the run then walked the fixture's queue. The rule that stops it is `lib.sh`: a path the environment names
# outside the tree this process resolved is not this tree's run, and is ignored. `run_child` keeps exporting
# them, because a fixture's run is a real run and its stub child reads them too.
case_state_inheritedStatePathOutsideTheTreeIsIgnored() {
  local d p foreign before after
  d=$(scratch); p=$(new_project)
  foreign="$d/foreign-run.json"
  printf '{"sentinel":"a state file no tree here owns"}\n' > "$foreign"
  before=$(cat "$foreign")
  LOOP_STATE_FILE="$foreign" run_state "$p" init --items 1 --minutes 90 >/dev/null || fail "init failed"
  after=$(cat "$foreign")
  assert_eq "$before" "$after" "a state path named from outside the tree is not written"
  [ -f "$(state_file "$p")" ] || fail "the tree resolved no state file of its own"
  assert_eq "1" "$(jq -r '.run.items' "$(state_file "$p")")" "the tree's own state carries the budget"
  return 0
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

# A branch whose work landed as a *new* commit. The loop lands an item's work as `fix: … (#N)`, and a branch
# that was landed by hand is re-landed as a fresh commit, so the branch is never an ancestor of the base while
# its patch is in it. What decides whether the work is there is the patch, not the commit object (#228).
landed_branch() { # <repo> <issue> <slug> → the branch the base's history carries by patch, not by ancestry
  local p="$1" branch="fix/issue-$2-$3" base
  base=$(git -C "$p" rev-parse HEAD)
  git -C "$p" checkout -q -b "$branch" "$base" || fail "could not create the attempt branch"
  printf 'the landed work\n' > "$p/landed.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" commit -qm "the attempt's own work" >/dev/null 2>&1 || fail "could not commit the attempt"
  git -C "$p" checkout -q main || fail "could not return to the base branch"
  git -C "$p" cherry-pick "$branch" >/dev/null 2>&1 || fail "could not land the attempt's patch as a new commit"
  # a landing is its own commit: `fix: <slug> (#N)`. Amending the message is what makes the object differ from
  # the attempt's commit while its patch stays the same — the case is about identity, not about content.
  git -C "$p" commit -q --amend -m "fix: the landed work (#$2)" >/dev/null 2>&1 || fail "could not amend the landing"
  printf '%s\n' "$branch"
}

# Whether an item was already attempted is held by git, not by the tracker, so the pre-screen asks git the
# same question `worktree.sh start-item` asks — asked on the branch *prefix*, because the slug is not known
# before the title is read (issue #175). A branch that carries a **finished** attempt — one archived change of
# its own plus a green verification record — is the owner's decision: the run may adopt it, and redoing it
# would throw a reviewed change away. The decision is recorded where a person reads it, before any item is
# picked and before a budget slot is spent on it; the start's own check stays the authoritative one.
case_prescreen_aFinishedAttemptBranchIsADecision() {
  local p out base branch
  p=$(new_project)
  init_state "$p" 3 90
  base=$(git -C "$p" rev-parse HEAD)
  branch="fix/issue-21-a-finished-attempt"
  git -C "$p" checkout -q -b "$branch" "$base"
  mkdir -p "$p/openspec/changes/archive/2026-01-01-fix-attempted/evidence"
  printf 'archived\n' > "$p/openspec/changes/archive/2026-01-01-fix-attempted/proposal.md"
  printf '<testsuite tests="2" failures="0" errors="0"></testsuite>\n' \
    > "$p/openspec/changes/archive/2026-01-01-fix-attempted/evidence/TEST-attempted.xml"
  printf 'the previous attempt\n' > "$p/attempt.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the previous attempt (#21)" >/dev/null 2>&1 ||
    fail "could not commit the previous attempt"
  git -C "$p" checkout -q main
  # …and one whose branch carries nothing of its own: the verdict is about the commits, not about the name
  git -C "$p" branch fix/issue-22-carried-nothing "$base"

  export GH_STUB_DIR; GH_STUB_DIR=$(scenario six)
  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed"
  assert_eq "needs-decision" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 21) | .verdict')" \
    "a branch that carries a finished attempt is a decision, not a candidate"
  assert_contains "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 21) | .reason')" \
    "$branch" "the verdict names the branch that carries the work"
  assert_eq "$branch" \
    "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 21) | .evidence')" \
    "the branch is the verdict's own clause of evidence"
  assert_eq "eligible" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 22) | .verdict')" \
    "a branch that carries no commit of its own leaves the item eligible"
  assert_eq "3" "$(jq -r '.queue | length' "$(state_file "$p")")" "the decision is not queued as work"
  assert_eq "needs-decision" "$(jq -r '.excluded[] | select(.issue == 21) | .verdict' "$(state_file "$p")")" \
    "the decision is recorded in the run's exclusion list"
  assert_eq "$branch" \
    "$(jq -r '.excluded[] | select(.issue == 21) | .evidence' "$(state_file "$p")")" \
    "with the branch carried beside it"
}

# A branch that carries commits but no archived change of *its own* is a blocked attempt, not a finished one:
# nothing on it states a question a person could answer, and the work it holds is not thrown away by starting
# over because the run renames the branch aside (`start-item`). The pre-screen therefore queues the item.
# #230 is the measured shape (issue #232): its branch carried four commits, nothing in `openspec/changes/archive`
# of its own — and the one archived change its history did add came in on an unrelated item's commit, which made
# the adoption refusal name a change with no relation to the item.
case_prescreen_aBlockedAttemptBranchIsWork() {
  local p out base branch state
  p=$(new_project)
  init_state "$p" 3 90
  base=$(git -C "$p" rev-parse HEAD)
  branch="fix/issue-23-a-blocked-attempt"
  git -C "$p" checkout -q -b "$branch" "$base"
  mkdir -p "$p/openspec/changes/archive/2026-01-01-fix-foreign/evidence"
  printf 'archived\n' > "$p/openspec/changes/archive/2026-01-01-fix-foreign/proposal.md"
  printf '<testsuite tests="2" failures="0" errors="0"></testsuite>\n' \
    > "$p/openspec/changes/archive/2026-01-01-fix-foreign/evidence/TEST-foreign.xml"
  printf 'another item\n' > "$p/foreign.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: another item's change (#99)" >/dev/null 2>&1 ||
    fail "could not commit the foreign change"
  printf 'the blocked attempt\n' > "$p/attempt.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the blocked attempt (#23)" >/dev/null 2>&1 ||
    fail "could not commit the blocked attempt"
  git -C "$p" checkout -q main

  export GH_STUB_DIR; GH_STUB_DIR=$(scenario six)
  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed"
  state=$(printf '%s' "$out" | jq -r '.[] | select(.issue == 23) | .verdict')
  assert_eq "eligible" "$state" \
    "a branch carrying no archived change of its own is work, not a decision"
  assert_contains "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 23) | .reason')" \
    "$branch" "and the record names the branch the run sets aside"
  assert_eq "$branch" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 23) | .evidence')" \
    "with the branch as the record's evidence"
  assert_eq "0" "$(jq -r '[.excluded[] | select(.issue == 23)] | length' "$(state_file "$p")")" \
    "so it is not recorded as awaiting a decision"
  assert_eq "23" "$(jq -r '.queue[] | select(.issue == 23) | .issue' "$(state_file "$p")")" \
    "it is queued as work instead"
}


# The same question is asked of *patch* identity. A branch the base's history carries by patch is a leftover,
# and a leftover is not a decision: the item is startable, and a run's own branch that was landed by hand stops
# spending one of the run's decision slots (#228).
case_prescreen_landedBranchIsNotADecision() {
  local p out base branch
  p=$(new_project)
  init_state "$p" 2 90
  branch=$(landed_branch "$p" 21 landed)
  base=$(git -C "$p" rev-parse main)
  run_state "$p" init --items 2 --minutes 90 --base "$base" >/dev/null || fail "init failed"
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario six)
  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed"
  assert_eq "eligible" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 21) | .verdict')" \
    "a branch whose every patch the base carries is not a previous attempt"
  assert_eq "0" "$(jq -r '[.excluded[]? | select(.issue == 21)] | length' "$(state_file "$p")")" \
    "and nothing about $branch is recorded as awaiting a decision"
}

# An item the loop itself escalated is its owner's until a person decides: the run writes
# `agent/needs-decision` and `status/on-hold`, and the next pre-screen read only the in-flight status, so #110
# was analysed and escalated three times with one conclusion (comments on 2026-10-18 and 2026-10-19). The
# verdict is the run's own decision verdict, but marked as already escalated so the run does not post the same
# comment again.
case_prescreen_itemAwaitingADecisionIsNotQueued() {
  local p out issue status base branch
  p=$(new_project)
  init_state "$p" 3 90
  # #41 carries a stated question (`agent/needs-decision` + `status/on-hold`) *and* a blocked attempt's branch.
  # The question decides: nobody starts a second attempt beside one a person was asked about (#110), so the
  # branch is evidence for the same decision rather than a licence to work the item (#232).
  base=$(git -C "$p" rev-parse HEAD)
  branch="fix/issue-41-a-blocked-attempt"
  git -C "$p" checkout -q -b "$branch" "$base"
  printf 'the blocked attempt\n' > "$p/attempt.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the blocked attempt (#41)" >/dev/null 2>&1 ||
    fail "could not commit the blocked attempt"
  git -C "$p" checkout -q main
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
  assert_eq "$branch" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 41) | .evidence')" \
    "a blocked attempt beside a stated question is that decision's evidence"
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
  # an escalated item whose branch carries a *finished* attempt: one archived change of its own plus a green
  # verification, so the branch is what the adoption path would read (#176). A blocked attempt's branch is not
  # a decision at all and is set aside by `start-item` instead (#232).
  mkdir -p "$p/openspec/changes/archive/2026-01-01-fix-escalated/evidence"
  printf 'archived\n' > "$p/openspec/changes/archive/2026-01-01-fix-escalated/proposal.md"
  printf '<testsuite tests="2" failures="0" errors="0"></testsuite>\n' \
    > "$p/openspec/changes/archive/2026-01-01-fix-escalated/evidence/TEST-escalated.xml"
  printf 'the earlier attempt\n' > "$p/attempt.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the earlier attempt (#41)" >/dev/null 2>&1 ||
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

# A branch that carries an archived change of its own is the one `start-item` must not overwrite: the change
# and whatever evidence it holds are an attempt's verdict, and only a finished change may be adopted — a blocked
# one (no green verification, or a reviewer's refusal in the change) is the owner's decision.
# The reuse is decided by patch identity too: a branch whose every commit the base carries by patch carries no
# work of its own, however it was landed, so the item starts instead of blocking for ever (#228).
case_worktree_reusesABranchWhoseWorkAlreadyLanded() {
  local p w base out
  p=$(new_project); w="$p/.pi/worktrees/loop"
  init_state "$p" 1 90
  run_worktree "$p" create --branch loop-test >/dev/null || fail "worktree create failed"
  landed_branch "$p" 21 demo >/dev/null
  base=$(git -C "$p" rev-parse main)
  run_state "$p" init --items 1 --minutes 90 --base "$base" >/dev/null || fail "init failed"
  out=$(run_worktree "$p" start-item 21 demo 2>&1) ||
    fail "a branch whose work the base already carries was refused: $out"
  assert_contains "$out" "reused" "the run says the branch was reused"
  assert_eq "$base" "$(git -C "$w" rev-parse HEAD)" "the item starts exactly at the base"
}

case_worktree_refusesABranchCarryingAPreviousAttempt() {
  local p w out status
  p=$(new_project); w="$p/.pi/worktrees/loop"
  init_state "$p" 1 90
  run_worktree "$p" create --branch loop-test >/dev/null || fail "worktree create failed"
  run_worktree "$p" start-item 21 demo >/dev/null || fail "the first item did not start"
  # the previous attempt archived its change and never verified it: that commit is the attempt's only record,
  # so starting over would throw the change away and the item is refused instead
  mkdir -p "$w/openspec/changes/archive/2026-01-01-fix-attempted"
  printf 'archived\n' > "$w/openspec/changes/archive/2026-01-01-fix-attempted/proposal.md"
  printf 'the previous attempt\n' > "$w/attempt.txt"
  git -C "$w" add -A >/dev/null 2>&1
  git -C "$w" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the previous attempt (#21)" >/dev/null 2>&1 ||
    fail "could not commit the previous attempt"
  out=$(run_worktree "$p" start-item 21 demo 2>&1); status=$?
  assert_eq "5" "$status" "a branch that carries an archived change refuses the item"
  assert_contains "$out" "carries commits the base does not" "the refusal says why"
  assert_contains "$out" "the previous attempt" "and names the commit it would throw away"
  assert_eq "$(git -C "$w" rev-parse fix/issue-21-demo)" "$(git -C "$p" rev-parse fix/issue-21-demo)" \
    "the branch is left where it was"
}

# A branch that carries commits and no archived change of its own is a *blocked* attempt: starting over throws
# nothing away, because the branch is renamed aside rather than deleted — so the item is worked instead of being
# parked on a decision nobody stated (#232). #230 was parked this way from 2026-10-21 and its fix stayed on the
# branch while `main` kept the defect.
case_worktree_setsAsideABlockedAttemptAndStartsFresh() {
  local p w out status attempt left_branch base
  p=$(new_project); w="$p/.pi/worktrees/loop"
  init_state "$p" 1 90
  run_worktree "$p" create --branch loop-test >/dev/null || fail "worktree create failed"
  run_worktree "$p" start-item 21 demo >/dev/null || fail "the first item did not start"
  printf 'the blocked attempt\n' > "$w/attempt.txt"
  git -C "$w" add -A >/dev/null 2>&1
  git -C "$w" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the blocked attempt (#21)" >/dev/null 2>&1 ||
    fail "could not commit the blocked attempt"
  attempt=$(git -C "$w" rev-parse HEAD)
  base=$(run_state "$p" get run.base)

  out=$(run_worktree "$p" start-item 21 demo 2>&1); status=$?
  assert_eq "0" "$status" "a blocked attempt's branch does not park the item: $out"
  assert_contains "$out" "set-aside:" "the run says what it did with the branch"
  assert_eq "$base" "$(git -C "$w" rev-parse HEAD)" "the item starts at the run's base instead"
  left_branch=$(git -C "$p" for-each-ref --format='%(refname:short)' 'refs/heads/loop/leftover/issue-21-*')
  [ -n "$left_branch" ] || fail "the blocked attempt's branch was not kept: $(git -C "$p" branch --list 'fix/issue-21-*')"
  assert_eq "$attempt" "$(git -C "$p" rev-parse "$left_branch")" \
    "the commits are kept under the name the run gives them"
  assert_eq "$base" "$(git -C "$p" rev-parse fix/issue-21-demo)" \
    "and the item's own branch was recreated from the base"
}

# `carried_change` asks which archived change the branch is *credited* with. A stale branch inherits the files
# another item's commits added, so counting every archive directory the base does not have credits the branch
# with work it never did — and the refusal then names a change that belongs to another issue. Measured on #230:
# its branch held #126's commit, and the adoption refusal named 2026-10-19-fix-i18n-gate-state-field-text
# (issue #232).
case_worktree_doesNotAdoptAChangeCreditedToAnotherIssue() {
  local p out status branch
  p=$(new_project)
  init_state "$p" 1 90
  branch="fix/issue-23-a-blocked-attempt"
  git -C "$p" switch -c "$branch" >/dev/null 2>&1 || fail "could not create the blocked attempt's branch"
  mkdir -p "$p/openspec/changes/archive/2026-01-01-fix-foreign/evidence"
  printf 'archived\n' > "$p/openspec/changes/archive/2026-01-01-fix-foreign/proposal.md"
  printf '<testsuite tests="2" failures="0" errors="0"></testsuite>\n' \
    > "$p/openspec/changes/archive/2026-01-01-fix-foreign/evidence/TEST-foreign.xml"
  printf 'another item\n' > "$p/foreign.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: another item's change (#99)" >/dev/null 2>&1 ||
    fail "could not commit the foreign change"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not return to the base"

  out=$(run_worktree "$p" adopt-item 23 "$branch" 2>&1); status=$?
  assert_eq "5" "$status" "a change credited to another issue does not make the branch adoptable"
  assert_contains "$out" "adds no single archived change of its own" "the refusal names what the branch lacks"
  assert_not_contains "$out" "fix-foreign" "and it names no change the branch did not add"
  [ ! -f "$p/foreign.txt" ] || fail "another item's change was merged onto the base"
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
  # The check is the worktree git lists, not the substring: the loop runs its own suite from
  # `.pi/worktrees/loop`, so every scratch project's path contains `worktrees/loop` and the old match failed on
  # the run's own root rather than on a leftover entry (measured 2026-10-21, issue #229 — reproduced on HEAD at
  # a copy whose path held that segment, green at one whose path did not).
  if git -C "$p" worktree list --porcelain | grep -qxF "worktree $p/.pi/worktrees/loop"; then
    fail "git still lists the worktree that was removed"
  fi
  return 0
}

# A kept worktree is the next run's warm *checkout* — the sources and the git state — and not a warm
# *build* (issue #210): the run removes the module build directories the project records
# (`LOOP_WORKTREE_SCRATCH_PATHS`) when it keeps the tree. Measured 2026-10-20: the kept worktree was 3.2 GB,
# of which `app/build` alone was 1.9 GB, and nothing removed it. The removal is confined to the tree: a
# recorded path that is a symlink out of it is left exactly where it is, and so is everything git holds.
case_worktree_keepsSourcesNotBuildOutput() {
  local d p w outside out bytes branch
  d=$(scratch); p=$(new_project); w="$p/.pi/worktrees/loop"
  outside="$d/outside"; mkdir -p "$outside"
  printf 'the file outside the tree\n' > "$outside/keep.txt"
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  # The child builds: one recorded build directory with real bytes, and one recorded path that is a symlink
  # out of the tree. Both are gitignored, so the run's own `git add -A` does not stage them either.
  cat > "$d/worker-action.sh" <<ACTION
case "\$1" in
  worker*)
    mkdir -p openspec/changes/archive/2026-01-01-fix-demo/evidence app/build/test-results/test
    printf 'archived\\n' > openspec/changes/archive/2026-01-01-fix-demo/proposal.md
    printf '<testsuite tests="1" failures="0"></testsuite>\\n' > openspec/changes/archive/2026-01-01-fix-demo/evidence/TEST-demo.xml
    printf '<testsuite tests="1" failures="0"></testsuite>\\n' > app/build/test-results/test/TEST-demo.xml
    head -c 102400 /dev/zero > app/build/blobs.bin
    mkdir -p core
    ln -s "$outside" core/build
    printf 'the fix\\n' > demo-fix.txt
    ;;
esac
ACTION

  out=$(run_loop "$p" --items 1 --minutes 90 2>&1) || fail "the run failed: $out"
  [ -d "$w" ] || fail "the worktree was not kept"
  [ ! -e "$w/app/build" ] || fail "the kept worktree still carries its build output: $(du -sh "$w/app/build" | cut -f1)"
  [ -f "$w/demo-fix.txt" ] || fail "the kept worktree lost the change's source file"
  [ -L "$w/core/build" ] || fail "a recorded path that is a symlink was removed from the kept worktree"
  [ -f "$outside/keep.txt" ] || fail "the removal followed the symlink out of the worktree"
  assert_eq "" "$(git -C "$w" status --porcelain)" "the kept worktree is clean, its git state untouched"
  branch=$(cat "$w/.pi/loop-branch")
  [ -n "$(git -C "$p" branch --list "$branch")" ] || fail "the run's branch $branch is gone"
  assert_eq "$(git -C "$p" rev-parse "$branch")" "$(git -C "$w" rev-parse HEAD)" \
    "the kept worktree still holds the item's commit"
  bytes=$(printf '%s\n' "$out" | sed -n 's/.*removed from the kept worktree: \([0-9][0-9]*\) B.*/\1/p' | head -1)
  [ -n "$bytes" ] || fail "the run does not say how much build output it removed: $out"
  [ "$bytes" -ge 102400 ] || fail "the run reports $bytes B removed, fewer than the 102400 B the child built"
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
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the refused change (#21)" >/dev/null 2>&1 ||
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
  # The two fields a falsification carries (openspec change `falsified-items-close-in-the-loop`): without them
  # in the rendered prompt the worker cannot answer the shape the run reads.
  assert_contains "$(cat "$prompt")" 'LOOP-FALSIFIED:' "the worker prompt asks what the item blames"
  assert_contains "$(cat "$prompt")" 'LOOP-MEASURED:' "and what was measured instead, with how it was taken"
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

# A recorded decision is the owner's policy, not a claim to re-verify (issue #196: a fresh worker spent a run
# re-deriving a fork a person had already answered, and asked it again with a different option list).
case_prompt_workerTreatsARecordedDecisionAsSettled() {
  local d p prompt
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompt="$p/.pi/issue-loop/items/21/worker-prompt.md"
  [ -f "$prompt" ] || fail "no prompt was rendered"
  assert_contains "$(cat "$prompt")" 'A recorded decision is settled' "the worker is told a decision is settled"
  assert_contains "$(cat "$prompt")" '<<<ITEM_DECISION' "and where that decision is delimited"
  assert_contains "$(cat "$prompt")" 'do not re-derive it as a fresh fork' "and that it is not re-asked"
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
  # A falsification reaches the review with an empty diff, and the reviewer is told it is read like a change
  # (openspec change `falsified-items-close-in-the-loop`).
  assert_contains "$(cat "$prompt")" "falsification" "the reviewer is told how a falsification is read"
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

# The answer a person recorded has to reach the worker's package (issue #196). Without it a fresh child
# re-derives the question the answer settled: `Decision: 2` was recorded at 04:52, the same fork was asked again
# at 08:48 with its options renumbered, and the recorded `2` came to mean a different option than the one it
# answered. The item's body still says "bounds to settle", because nothing amends it — the package is where a
# settled question has to be readable.
case_run_workPackageCarriesTheRecordedDecision() {
  local d p package
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario decided)
  stub_success "$d"
  run_loop "$p" --items 1 --minutes 90 >/dev/null 2>&1 || fail "the run failed"
  package="$p/.pi/issue-loop/items/22/work-package.md"
  [ -f "$package" ] || fail "no work package was written for the decided item"
  assert_contains "$(cat "$package")" '<<<ITEM_DECISION' "the package carries a decision block"
  assert_contains "$(cat "$package")" 'recorded: 2026-10-21T04:52:39Z' "with the answer's own timestamp"
  assert_contains "$(cat "$package")" 'question: **The decision:** which bound' "and the question it answered"
  assert_contains "$(cat "$package")" 'decision: 2' "and the answer itself"
  assert_eq "1" "$(grep -c '^ITEM_DECISION$' "$package" || true)" "exactly one closing marker"
}

# A closure ends an item's life as an open question, so the label that made it one has to go with it: an item
# that carries `agent/needs-decision` and is closed stays in the decision queue for ever, and the ceiling that
# counts the queue then refuses runs over an item nobody can answer — measured 2026-10-20 on #81, which was
# closed and still listed (issue #225).
case_labels_closeRemovesTheDecisionLabel() {
  local d p log
  p=$(new_project); d=$(scratch)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario decisions)
  log="$d/gh-close.log"; : > "$log"; export GH_STUB_RECORD="$log"
  run_labels "$p" close 41 --reason "the fixture cause" --evidence "the fixture evidence" >/dev/null 2>&1 ||
    fail "close failed"
  assert_contains "$(cat "$log")" "--remove-label agent/needs-decision" \
    "a closure takes the decision label off, so the item leaves the queue"
  assert_contains "$(cat "$log")" "--add-label status/fixed-by" "and marks the item as fixed"
}

# A block whose cause is the run's own child is not a question the item asked. Rather than escalate a reason
# and call it a decision, the run states the only choice a block leaves — retry the item, or abandon it — so
# the queue holds something a person can answer (issues #225, #194).
case_run_blockedItemAsksItsOwnQuestion() {
  local d p out body
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  printf '#!/usr/bin/env bash\n: # this child leaves the tree exactly as it found it\n' > "$d/worker-action.sh"
  out=$(run_loop "$p" --items 1 --minutes 90 2>&1)
  body=$(grep 'issue comment' "$GH_STUB_RECORD" 2>/dev/null || true)
  assert_contains "$body" 'The decision:' "the escalation carries a decision field, not only a reason"
  assert_contains "$body" 'retry' "the question names the first option"
  assert_contains "$body" 'abandon' "and the second"
}

# A falsification is the one skip whose finding is not a question: the run holds the number that settles the
# item, so closing it as mis-diagnosed is the same two artifacts a fix closure posts (openspec change
# `falsified-items-close-in-the-loop`).
case_run_falsifiedSkipClosesWithItsMeasurement() {
  local d p out log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_FALSIFIED_REPORT" 'VERDICT: PASS'
  printf '#!/usr/bin/env bash\n: # a falsification leaves the tree at the base\n' > "$d/worker-action.sh"
  out=$(run_loop "$p" --items 1 --minutes 90 2>&1) || fail "the run failed: $out"
  log=$(cat "$d/gh.log")
  assert_eq "item cap reached (1/1)" "$(jq -r '.run.stop.reason // "-"' "$(state_file "$p")")" \
    "the run worked its one item and stopped on the cap"
  assert_eq "falsified" "$(jq -r '.items[0].result // "none"' "$(state_file "$p")")" \
    "the item is recorded apart from a fix and from a skip: $(jq -c '.items' "$(state_file "$p")")"
  assert_eq "yes" "$(jq -r '.items[0].metrics.closed' "$(state_file "$p")")" "the closure happened"
  assert_contains "$log" "issue close" "the item is closed"
  assert_contains "$log" "12-17 ms" "the closure carries the measurement"
  assert_contains "$log" "mis-diagnosed" "and names the reason: the named cause does not hold"
  assert_not_contains "$log" "--add-label agent/needs-decision" "no decision label is written"
}

# Without the number the claim is a question again: two of the three halves is an escalation, not a finding.
case_run_falsificationWithoutAMeasurementIsEscalated() {
  local d p out log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$(printf '%s\n' "$STUB_FALSIFIED_REPORT" | grep -v '^LOOP-MEASURED:')" 'VERDICT: PASS'
  printf '#!/usr/bin/env bash\n: # nothing was changed\n' > "$d/worker-action.sh"
  out=$(run_loop "$p" --items 1 --minutes 90 2>&1) || fail "the run failed: $out"
  log=$(cat "$d/gh.log")
  assert_eq "skipped" "$(jq -r '.items[0].result' "$(state_file "$p")")" "without a measurement it is a skip"
  assert_contains "$log" "--add-label agent/needs-decision" "and it is escalated"
  assert_contains "$log" "--add-label status/on-hold" "with the state that holds it"
  assert_not_contains "$log" "issue close" "nothing is closed"
}

# Closure disabled: the evidence is posted and the item stays open — the run does not reopen the question it
# already answered.
case_run_mayCloseNoKeepsAFalsificationOpen() {
  local d p out log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_FALSIFIED_REPORT" 'VERDICT: PASS'
  printf '#!/usr/bin/env bash\n: # a falsification leaves the tree at the base\n' > "$d/worker-action.sh"
  out=$(run_loop "$p" --items 1 --minutes 90 --may-close no 2>&1) || fail "the run failed: $out"
  log=$(cat "$d/gh.log")
  assert_eq "falsified" "$(jq -r '.items[0].result' "$(state_file "$p")")" "the finding is recorded"
  assert_eq "skipped" "$(jq -r '.items[0].metrics.closed' "$(state_file "$p")")" "the closure is left to a person"
  assert_contains "$log" "issue comment" "the evidence is still posted"
  assert_not_contains "$log" "issue close" "the item stays open"
  assert_not_contains "$log" "status/retired" "no settled state is written"
  assert_not_contains "$log" "--add-label agent/needs-decision" "no decision label is written"
}

# A falsification is a claim about an unchanged tree. The review round may run a corrective worker that commits,
# so the claim is read again after the review: a falsified item whose tree moved is a change, and closing it as
# mis-diagnosed would close on evidence nobody reviewed.
case_run_falsificationWithAChangedTreeIsBlocked() {
  local d p out log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_FALSIFIED_REPORT" 'BLOCKING: the measurement does not cover the blamed mechanism
LOOP-BLOCKING: 1
VERDICT: FAIL'
  # the round-2 worker commits; the round-2 reviewer then accepts, so the only thing that changed between the
  # claim and the closure is the tree
  cat > "$d/worker-action.sh" <<'ACTION'
case "$1" in
  worker-prompt-round2.md)
    mkdir -p openspec/changes/archive/2026-01-01-fix-demo/evidence
    printf 'archived\n' > openspec/changes/archive/2026-01-01-fix-demo/proposal.md
    printf 'the fix\n' > demo-fix.txt
    ;;
  reviewer-prompt-2.md)
    printf 'VERDICT: PASS\n' > "$PI_STUB_REPORT_REVIEWER"
    ;;
esac
ACTION
  out=$(run_loop "$p" --items 1 --minutes 90 2>&1) || fail "the run failed: $out"
  log=$(cat "$d/gh.log")
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "a falsification whose tree moved is blocked, not closed"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "the tree moved during its review" \
    "and the note names what contradicted the claim"
  assert_not_contains "$log" "issue close" "nothing is closed"
  assert_contains "$log" "--add-label agent/needs-decision" "the item is escalated instead"
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
  # the child is given a prompt file and nothing else: no tracker credential, no token, no label. The adapter
  # a project names in `LOOP_AGENT_CMD` legitimately adds its own flags (the output mode it selects), so the
  # assertion is on where the prompt sits and on what must be absent — not on a word count a wrapper changes.
  first_argv=$(sed -n 's/^argv://p' "$PI_STUB_LOG" | head -1)
  assert_eq "$p/.pi/issue-loop/items/21/worker-prompt.md" "$(printf '%s' "$first_argv" | awk '{print $NF}')" \
    "the prompt file is the child's last argument, and nothing follows it"
  assert_not_contains "$first_argv" "GH_TOKEN" "no tracker credential reaches the child"
  assert_not_contains "$first_argv" "label" "no label-writing argument reaches the child"
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
  local d p out
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # A child that says nothing while it works is the normal case, not the exception: print mode writes only the
  # final assistant text, so `pick #N` used to be followed by silence for the whole item and a working child
  # was indistinguishable from a hung one. The wait itself therefore has to report.
  #
  # A child that is merely *slow* proves it, and a blocked one does not: the first version of this case held the
  # stub on a fifo, and whenever a suite was killed before the case released the writer, the stub and the run it
  # blocked outlived the suite and held the caller's stdout open — the suite then hung instead of reporting
  # (measured 2026-10-20: four orphaned runs in one worktree, and a suite stalled at this case twice). A sleep
  # needs no second process to rendezvous with.
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  printf '#!/usr/bin/env bash\nsleep 3\n' > "$d/slow-child.sh"
  out=$(PI_STUB_ACTION="$d/slow-child.sh" LOOP_PROGRESS_SECONDS=1 \
    run_loop "$p" --items 1 --minutes 90 --review off 2>&1) || fail "the run itself failed"
  # The child worked for seconds and printed nothing until it exited: every line below is the run's own.
  assert_contains "$out" "loop: worker-prompt started" "the wait is announced when the child starts"
  assert_contains "$out" "loop: worker-prompt running" "the wait reports while the child cannot"
  assert_contains "$out" "changed file(s)" "what it reports is read from the worktree"
  assert_contains "$out" "loop: worker-prompt finished in" "and the end is reported"
  assert_contains "$out" "exit 0" "with the child's own exit status"
}

# The console named neither the pre-screen nor any view into the run: measured 2026-10-20, the pre-screen of the
# real backlog is a 53 s call (`tools/loop/prescreen.sh --dry-run --json`, 84 records over 188 items) that reached
# the terminal only after it returned, two runs of that day were interrupted by hand inside that window, and
# nothing on the console said where a person could look meanwhile (issue #191). The header therefore names the
# step that follows it, the state directory, and the command that shows what the run is doing.
case_run_headerNamesTheLiveView() {
  local d p out
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  out=$(run_loop "$p" --items 1 --minutes 90 --review off --dry-run 2>&1) || fail "the run itself failed"
  assert_contains "$out" "state.sh show" "the header names the command that shows the run's progress"
  assert_contains "$out" ".pi/issue-loop" "the header names the state directory"
  assert_contains "$out" "pre-screen" "the header names the step that runs next"
  assert_contains "$out" "not instant" "and says that step does not return at once"
}

# The pre-screen's own text has to reach the terminal *while* the call runs and not after it returns, and the
# same bytes have to stay where a path can read them afterwards. Both halves are measured against a stand-in
# *pre-screen* — one line, a 3 s hold, a second line — because that shape is the only one that separates
# streaming from capture-and-replay: read 1.5 s in, the console must carry the first line and must not carry
# the second, and the kept text must carry both once the run is over (issue #191). Holding the issue listing
# instead left the run's own phase line as the only mid-flight evidence, which a capture-and-replay would also
# print.
case_run_prescreenAnnouncesItselfBeforeItReturns() {
  local d p log pid out tools p2 rc
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The copy is of the whole `tools` tree, not of `tools/loop`: `lib.sh` sources `../issues/lib.sh`, and an
  # engine beside no tooling dies in its own first line instead of reaching the pre-screen.
  tools="$d/tools"
  cp -r "$(dirname "$LOOP_ENGINE")" "$tools"
  {
    printf '#!/usr/bin/env bash\n'
    printf 'printf "pre-screen: first record\\n"\n'
    printf 'sleep 3\n'
    printf 'printf "pre-screen: second record\\n"\n'
  } > "$tools/loop/prescreen.sh"
  log="$d/console.log"
  ( LOOP_ENGINE="$tools/loop" run_loop "$p" --items 1 --minutes 90 --review off ) > "$log" 2>&1 &
  pid=$!
  sleep 1.5
  out=$(cat "$log")
  assert_contains "$out" "loop: pre-screen" "the phase is announced on the console"
  assert_contains "$out" "started" "as started, while the pre-screen is still running"
  assert_matches "$out" '^loop: pre-screen · [0-9]+(m[0-9]{2})?s · started — .*prescreen\.log$' \
    "and its start line names the file that keeps the text"
  assert_contains "$out" "pre-screen: first record" \
    "the pre-screen's own first line reaches the console while the call runs"
  assert_not_contains "$out" "pre-screen: second record" \
    "and the line it has not printed yet does not: the text is streamed, not replayed"
  wait "$pid" || fail "the run itself failed"
  out=$(cat "$log")
  assert_contains "$out" "pre-screen: second record" "the rest of it reaches the console too"
  assert_contains "$out" "finished" "and the end of the phase is printed with it"
  # The kept bytes: a path that reads the state directory afterwards sees what the terminal saw.
  assert_contains "$(cat "$(dirname "$(state_file "$p")")/prescreen.log")" "pre-screen: first record" \
    "the pre-screen's text is kept in the state directory"
  assert_contains "$(cat "$(dirname "$(state_file "$p")")/prescreen.log")" "pre-screen: second record" \
    "the whole of it, not only its first line"
  # The status the run decides on is the pre-screen's own and not the stream's: a stand-in pre-screen that fails
  # with 7 has to leave the run with 7 too, which is what `pipefail` on the pipeline buys. Without it the pipeline
  # would report the `tee`'s 0 and the run would walk past a pre-screen that never answered.
  p2=$(new_project)
  printf '#!/usr/bin/env bash\nprintf "pre-screen: a stand-in that fails\\n"\nexit 7\n' > "$tools/loop/prescreen.sh"
  out=$( LOOP_ENGINE="$tools/loop" run_loop "$p2" --items 1 --minutes 90 --review off 2>&1 )
  rc=$?
  assert_eq "7" "$rc" "the run's exit status is the pre-screen's, not the stream's"
}

# Silence never exceeds a bounded interval, and outside a child the run's own boundaries are what bound it: the
# run prints one line where the pre-screen starts and ends, where the worktree comes ready, where the item is
# picked, where the gate's evidence is read and where the item's work is committed (issue #191). One format
# with an elapsed field, one line per crossing, and terminal output only — a line that leaked into the state
# file or into the report would make a heartbeated run a different run from a quiet one.
case_run_everyPhaseBoundaryIsPrinted() {
  local d p out sf report
  d=$(scratch); p=$(new_project)
  out=$(run_one_item "$p" "$d" --review off 2>&1) || fail "the run failed"
  assert_line_count "$out" '^loop: pre-screen · [0-9]+(m[0-9]{2})?s · ' 2 "the pre-screen is announced and reported"
  assert_line_count "$out" '^loop: worktree · [0-9]+(m[0-9]{2})?s · ' 1 "the worktree coming ready"
  assert_line_count "$out" '^loop: item · [0-9]+(m[0-9]{2})?s · ' 1 "the item being picked"
  # The item's line is the live view the item was filed for, so its second half is asserted as well: the phase and
  # the elapsed field alone stay green if the report path is dropped from `run.sh:728`, and the one artifact an
  # operator is told to tail while the item is worked stops being named (issue #191, review round 2, mutation
  # 34.5).
  assert_matches "$out" '^loop: item · [0-9]+(m[0-9]{2})?s · picked #[0-9]+ — its report is written to .*/worker-report\.md$' \
    "the item's line names the report a person can read while the item is worked"
  assert_line_count "$out" '^loop: gate · [0-9]+(m[0-9]{2})?s · ' 1 "the gate's evidence being read"
  assert_line_count "$out" '^loop: commit · [0-9]+(m[0-9]{2})?s · ' 1 "the item's work being committed"
  sf=$(state_file "$p")
  assert_not_contains "$(cat "$sf")" "loop: " "no progress line is written to the state file"
  report=$(run_report "$p")
  assert_not_contains "$report" "loop: " "and none is carried into the report's text"
}

# A child that never returns used to be waited for with no bound at all: the run's deadline is consulted
# between items, so a hung gate could spend the whole budget inside one `wait` (measured 2026-10-19: item
# #126's worker phase ran 83 min that way, and its retry died inside the same module that had taken 30 min).
# The cap is a recorded value; the run ends the child and everything the child started, the transcript carries
# the one line that says so, and the item is recorded `blocked` with that transcript instead of spending the
# run's clock (issue #203).
case_run_childOutlastingTheWallClockIsBlocked() {
  local d p conf fifo pid i status out report
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The recorded default is 45 min, which no case can wait for: the same seam the other cases use for the
  # configuration (`LOOP_CONF`) carries a two-second cap here.
  conf="$d/loop.conf"
  sed -e 's/^LOOP_CHILD_MAX_SECONDS=.*/LOOP_CHILD_MAX_SECONDS=2/' "$LOOP_CONF" > "$conf"
  grep -qx 'LOOP_CHILD_MAX_SECONDS=2' "$conf" || fail "this case could not shorten the child cap in loop.conf"
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  fifo="$d/wait.fifo"; mkfifo "$fifo"
  # The child blocks on the fifo and is never released: with no cap the run is still there after the bound this
  # case gives it, which is what makes this case red on the tree before the fix.
  set -m
  ( export LOOP_CONF="$conf" PI_STUB_WAIT="$fifo"
    run_loop "$p" --items 1 --minutes 90 --review off > "$d/out" 2>&1 ) &
  pid=$!
  set +m
  for i in $(seq 1 200); do [ -e "$fifo.ready" ] && break; sleep 0.1; done
  [ -e "$fifo.ready" ] || fail "the stub child never started"
  # The case holds the fifo's writer side for its whole life (`exec 8>`), so releasing a child never blocks and
  # never leaves a writer behind: the stub opens the fifo for reading, this descriptor opens against it.
  exec 8> "$fifo"
  # Ten seconds is a bound on this case, not on the run: the run is supposed to end itself well inside it.
  for i in $(seq 1 100); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  if kill -0 "$pid" 2>/dev/null; then
    # The tree before the fix only reaches this line, and the diagnosis has to clean up after itself: the run's
    # own group dies here, and the line the fifo never got is written so a child still waiting on it leaves too.
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    printf 'go\n' >&8 2>/dev/null || true
    wait "$pid" 2>/dev/null
    exec 8>&-
    fail "the run was still waiting after 10 s on a child that outlasted its 2 s cap"
  fi
  wait "$pid"; status=$?
  exec 8>&-
  out=$(cat "$d/out")
  assert_eq "0" "$status" "a child the run itself ended is not a failed run"
  assert_contains "$out" "loop: worker-prompt finished in" "the wait is reported as finished, not abandoned"
  assert_contains "$out" "exit 124" "with the timeout's own status, distinct from a child's failure"
  report="$p/.pi/issue-loop/items/21/worker-report.md"
  [ -f "$report" ] || fail "the item kept no transcript of the child that was ended"
  assert_contains "$(cat "$report")" "LOOP-TIMEOUT: the child exceeded 2 s" \
    "the report carries the one machine-readable line that says the child was ended"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "an item whose child was ended is recorded blocked"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "exceeded 2 s" \
    "and the reason on the item names the cap that ended it"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "nothing is closed on a child the run ended"
}

# `run_child` returns the child's status and nothing else, so the caller could not tell a child whose argv the
# adapter refused from a child that worked the item and failed. Measured 2026-10-20: every child of a run died
# in under 4 s with the same 35-byte answer (`Error: Unknown option: --mode json`), the run recorded the two
# items `blocked`, escalated both with `agent/needs-decision` and `status/on-hold`, and stopped on "two
# consecutive items ended blocked" 3 minutes into an 8 h budget — two questions nobody had asked, and two of
# the three decision slots the run had (issue #212). A child that ends non-zero, writes no verdict and leaves
# the tree at the base never read the item: that is the run's own fault, recorded once as its stop, and the
# item stays exactly as its owner set it.
case_run_childThatNeverStartedIsTheRunsFault() {
  local d p out status log sf
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The child's own words and a non-zero status, with no report block and no change in the tree behind it —
  # which is the whole of what an adapter that refused its argv leaves. No action is installed, so nothing of
  # the item is touched.
  printf 'Error: Unknown option: --mode json\n' > "$d/child-says.txt"
  export PI_STUB_SAY="$d/child-says.txt" PI_STUB_EXIT=1
  export PI_STUB_LOG="$d/pi.log"; : > "$PI_STUB_LOG"
  export GH_STUB_RECORD="$d/gh.log"; : > "$GH_STUB_RECORD"
  out=$(run_loop "$p" --items 1 --minutes 90 --review off 2>&1); status=$?
  sf=$(state_file "$p")
  # A child that never started is not a failed run…
  assert_eq "0" "$status" "the run ends on its own stop, not with a child's failure"
  # …and not a failed item either: nothing about #21 was read, so nothing about #21 is recorded.
  assert_eq "0" "$(jq -r '.items | length' "$sf")" "the item is not recorded as a blocked one"
  # What happened is named in the child's own words, and recorded once as the run's own stop.
  assert_contains "$out" "could not start" "the stop says what did not happen"
  assert_contains "$out" "Error: Unknown option: --mode json" "and carries the child's own message"
  assert_contains "$(jq -r '.run.stop.reason // ""' "$sf")" "could not start" \
    "the stop is recorded, not only printed"
  # The item stays the owner's: no escalation, no hold, no comment about a question nobody asked.
  log=$(cat "$GH_STUB_RECORD")
  assert_not_contains "$log" "agent/needs-decision" "an item nobody read is not escalated"
  assert_not_contains "$log" "status/on-hold" "and is not put on hold"
  assert_eq "0" "$(grep -c 'issue comment' "$GH_STUB_RECORD" || true)" "nothing is written about it"
  assert_contains "$log" "--remove-label agent/in-progress" "and the in-progress mark comes off again"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "nothing is closed"
}

# ── the host the run works on (issue #209) ──────────────────────────────────────────────────────────
# The reading is driven through the recorded keys — the floor (`LOOP_MIN_FREE_MIB`) and the file the number is
# read from (`LOOP_MEMINFO_PATH`) — so these cases never depend on how much memory the machine running the
# suite happens to have, and the third one can point the reading at a file that does not exist.

case_run_hostBelowTheFloorBlocksTheItemAndStops() {
  local d p conf out note
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # A floor no host reaches: what blocks the item is the reading, not this machine. The key is appended, because
  # `loop.conf` is sourced top to bottom — the last assignment wins — so the case does not depend on the tree it
  # runs against already carrying the key.
  conf="$d/loop.conf"
  { cat "$LOOP_CONF"; printf 'LOOP_MIN_FREE_MIB=99999999\n'; } > "$conf"
  [ "$(tail -1 "$conf")" = 'LOOP_MIN_FREE_MIB=99999999' ] ||
    fail "this case could not raise the memory floor in loop.conf"
  stub_success "$d"
  out=$( ( export LOOP_CONF="$conf"; run_loop "$p" --items 1 --minutes 90 --review off ) 2>&1 ) ||
    fail "a blocked item is not a failed run"
  # Nothing was started: the item is recorded before its first child exists, so there is no transcript and no
  # branch either.
  assert_eq "" "$(cat "$d/pi.log")" "no agent process is started on a host below the floor"
  [ ! -e "$p/.pi/issue-loop/items/21/worker-report.md" ] ||
    fail "the worker's transcript exists although no child was started"
  assert_eq "" "$(git -C "$p" branch --list 'fix/issue-*')" "a starved item creates no branch"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" "the item is recorded blocked"
  note=$(jq -r '.items[0].note' "$(state_file "$p")")
  printf '%s\n' "$note" | grep -qE 'the host has [0-9]+ MiB available' ||
    fail "the item's reason carries the measurement, not a claim: $note"
  assert_contains "$note" "below the recorded floor of 99999999 MiB" \
    "and the number the check compared it against"
  # The run stops on that measurement, and the stop is the run's own record as well as its printed line.
  assert_contains "$out" "stop: " "the run prints the stop it recorded"
  assert_contains "$(jq -r '.run.stop.reason // ""' "$(state_file "$p")")" "99999999" \
    "the recorded stop reason names the floor it was compared against"
  assert_eq "1" "$(jq -r '.items | length' "$(state_file "$p")")" "the run stopped instead of taking the next item"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "nothing is closed"
}

case_run_hostAboveTheFloorRunsTheItem() {
  local d p conf out
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The other direction of the same check, with a floor any host is above: an item on a host that can pay for it
  # runs exactly as before, and the header names the host it ran on.
  conf="$d/loop.conf"
  { cat "$LOOP_CONF"; printf 'LOOP_MIN_FREE_MIB=0\n'; } > "$conf"
  [ "$(tail -1 "$conf")" = 'LOOP_MIN_FREE_MIB=0' ] || fail "this case could not lower the memory floor in loop.conf"
  stub_success "$d"
  out=$( ( export LOOP_CONF="$conf"; run_loop "$p" --items 1 --minutes 90 --review off ) 2>&1 ) ||
    fail "the run failed"
  assert_contains "$out" "min-free-mib=0" "the header names the floor it compared against"
  printf '%s\n' "$out" | grep -q 'free-mib=[0-9]\+' ||
    fail "the header names the free memory beside the budget: $out"
  assert_eq "1" "$(grep -c 'prompt:' "$d/pi.log" || true)" "the item's child is started"
  assert_eq "done" "$(jq -r '.items[0].result' "$(state_file "$p")")" "an item above the floor runs as today"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" "and closes through the same path"
}

case_run_hostWithoutTheReadingPasses() {
  local d p conf out
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # A host that publishes no `MemAvailable`: the file the recorded key names does not exist. The floor is the
  # unreachable one of the first case, so the only thing that can let the item run is the missing reading — a
  # check that cannot be made is not a refusal, and the engine has to run in a container without procfs.
  conf="$d/loop.conf"
  { cat "$LOOP_CONF";
    printf 'LOOP_MEMINFO_PATH="%s"\n' "$d/no-such-meminfo";
    printf 'LOOP_MIN_FREE_MIB=99999999\n'; } > "$conf"
  grep -qxF "LOOP_MEMINFO_PATH=\"$d/no-such-meminfo\"" "$conf" ||
    fail "this case could not point the memory reading at an absent file"
  stub_success "$d"
  out=$( ( export LOOP_CONF="$conf"; run_loop "$p" --items 1 --minutes 90 --review off ) 2>&1 ) ||
    fail "the run failed"
  assert_contains "$out" "free-mib=unknown" "the header says the reading is unavailable instead of inventing one"
  assert_eq "done" "$(jq -r '.items[0].result' "$(state_file "$p")")" "a host without the reading is not refused"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" "and the item closes as usual"
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
# authoritative one — and what that check does with the branch is decided by what the branch carries: a blocked
# attempt (commits, no archived change of its own) is renamed aside and the item worked, so an attempt that
# appears while the run is open cannot park the item on a decision nobody stated (#232).
case_run_branchThatAppearsWhileTheRunIsOpenIsSetAside() {
  local d p log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario two)
  # the first item's worker leaves a previous attempt's branch behind for the second item: the two fixture
  # titles slug to the same tail on purpose, so the case can name the second item's branch without
  # duplicating the run's own slugify. The branch points at the base — every patch it carries is the base's —
  # so #228 reads it as a leftover to reuse rather than as work, and the guarantee this case owns is that the
  # run works #22 instead of parking it (the set-aside path for an unarchived *unlanded* attempt is
  # `worktree#setsAsideABlockedAttemptAndStartsFresh`'s). Each worker also writes a branch-named file, so the
  # two items' patches differ: `merge-item` (#229) reads patch identity, and two byte-identical changes would
  # otherwise be one patch the base already carries.
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'case "$1" in worker*)
       current=$(git rev-parse --abbrev-ref HEAD)
       printf "%s\n" "$current" > "branch-$(printf %s "$current" | tr / _).txt"
       case "$current" in
         fix/issue-21-*)
           git branch "fix/issue-22-${current#fix/issue-21-}" HEAD
           ;;
       esac
       ;; esac'
  out=$(run_loop "$p" --items 2 --minutes 90 2>&1) || { printf '%s\n' "$out" | tail -25 >&2; fail "the run itself failed"; }
  assert_eq "2" "$(jq -r '.items | length' "$(state_file "$p")")" "both items were recorded"
  assert_eq "done" "$(jq -r '.items[] | select(.issue == 22) | .result' "$(state_file "$p")")" \
    "the item whose leftover branch appeared while the run was open is worked, not parked"
  assert_eq "0" "$(git -C "$p" for-each-ref --format='%(refname:short)' 'refs/heads/loop/leftover/issue-22-*' | wc -l | tr -d ' ')" \
    "a branch whose patches the base carries needs no set-aside"
  log=$(cat "$GH_STUB_RECORD")
  assert_eq "0" "$(grep -c 'add-label agent/needs-decision' "$GH_STUB_RECORD" || true)" \
    "no decision is stated for a branch that carries no patch of its own"
  assert_contains "$log" "issue close 22" "the item is closed on its own work"
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
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the previous attempt (#21)" >/dev/null 2>&1 ||
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

# The success path's merge is a fast-forward in the project's own tree, where the base branch is checked out,
# so it is refused when that tree is not clean — merging over uncommitted work would attribute it to the item —
# and when the branch does not descend from the base the run recorded. A refusal moves nothing, and a run that
# read the refusal as a closure would say `status/fixed-by` about a fix only a branch has; the same command on a
# clean tree is the merge the run's own success path takes (issue #229).
case_worktree_refusesToMergeOverADirtyProjectTree() {
  local p w base out status branch tip
  p=$(new_project); w="$p/.pi/worktrees/loop"
  init_state "$p" 1 90
  base=$(run_state "$p" get run.base)
  run_worktree "$p" create --branch loop-test >/dev/null || fail "worktree create failed"
  run_worktree "$p" start-item 21 demo >/dev/null || fail "the item did not start"
  branch=fix/issue-21-demo
  printf 'the fix\n' > "$w/demo-fix.txt"
  git -C "$w" add -A >/dev/null 2>&1
  git -C "$w" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: demo (#21)" >/dev/null 2>&1 ||
    fail "could not commit the fix"
  tip=$(git -C "$p" rev-parse "$branch")

  printf 'the work left behind\n' > "$p/stray.txt"
  out=$(run_worktree "$p" merge-item 21 "$branch" 2>&1); status=$?
  assert_eq "5" "$status" "a project tree that is not clean refuses the merge"
  assert_contains "$out" "not clean" "the refusal says why"
  assert_eq "$base" "$(git -C "$p" rev-parse main)" "the base did not move"
  [ ! -f "$p/demo-fix.txt" ] || fail "the fix reached the base although the merge was refused"
  rm -f "$p/stray.txt"

  out=$(run_worktree "$p" merge-item 21 "$branch" 2>&1) || fail "the merge was refused on a clean tree: $out"
  assert_contains "$out" "merged: branch $branch" "the merge names the branch it landed"
  assert_contains "$out" "· commits " "and the commits it carried"
  assert_eq "$tip" "$(git -C "$p" rev-parse main)" "the base is the branch, fast-forwarded"
}

# The refusal's other arm: a branch whose commits are not on top of the base the run recorded is a tree nobody
# reviewed. The recorded base is what decides, so a base that moved under the branch — a person's commit, a
# hand-merged attempt — refuses it instead of replaying the change onto the moved tree (issue #229).
case_worktree_refusesToReplayABranchTheBaseDoesNotDescendFrom() {
  local p w moved out status branch tip
  p=$(new_project); w="$p/.pi/worktrees/loop"
  init_state "$p" 1 90
  run_worktree "$p" create --branch loop-test >/dev/null || fail "worktree create failed"
  run_worktree "$p" start-item 21 demo >/dev/null || fail "the item did not start"
  branch=fix/issue-21-demo
  printf 'the fix\n' > "$w/demo-fix.txt"
  git -C "$w" add -A >/dev/null 2>&1
  git -C "$w" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: demo (#21)" >/dev/null 2>&1 ||
    fail "could not commit the fix"
  tip=$(git -C "$p" rev-parse "$branch")

  # the base moves after the run recorded it, and the record follows the tree — which is what the run itself
  # does after a merge, so the branch is measured against the base that is actually checked out
  printf 'the owner\n' > "$p/owner.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "the owner's commit" >/dev/null 2>&1 ||
    fail "could not move the base"
  moved=$(git -C "$p" rev-parse main)
  run_state "$p" set-base "$moved" >/dev/null || fail "could not record the moved base"

  out=$(run_worktree "$p" merge-item 21 "$branch" 2>&1); status=$?
  assert_eq "5" "$status" "a branch the base does not descend from is refused"
  assert_contains "$out" "does not descend" "the refusal says why"
  assert_eq "$moved" "$(git -C "$p" rev-parse main)" "the base did not move"
  [ ! -f "$p/demo-fix.txt" ] || fail "the branch was replayed onto a tree nobody reviewed"
  if git -C "$p" merge-base --is-ancestor "$tip" main; then
    fail "the refused branch's commit reached the base"
  fi
  assert_eq "$moved" "$(run_state "$p" get run.base)" \
    "and the run's record still names the base it compared the branch against"
}

# A run's success path commits the item's work on the item's own branch and then closes the item; the only
# merge in the loop was the adoption of a *previous* attempt, which needs the item to be visited again, so a
# closed item's fix stayed on `fix/issue-N-…` while the tracker read `status/fixed-by` — measured 2026-10-20 on
# #201, whose two commits a person fast-forwarded onto `main` by hand 35 minutes later (issue #229). The run
# merges the branch it committed before it closes the item.
case_run_mergesItsCommittedBranchBeforeClosing() {
  local d p runlog branch commits c log
  d=$(scratch); p=$(new_project)
  runlog="$d/run.txt"
  run_one_item "$p" "$d" --review on >"$runlog" 2>&1 || fail "the run itself failed: $(cat "$runlog")"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" "the reviewed item is closed"
  branch=$(git -C "$p" for-each-ref --format='%(refname:short)' 'refs/heads/fix/issue-21-*' | head -1)
  [ -n "$branch" ] || fail "the run left no item branch"
  git -C "$p" merge-base --is-ancestor "$branch" main ||
    fail "the item was closed with its fix on $branch, which the base does not have"
  [ -f "$p/demo-fix.txt" ] || fail "the fix the run committed did not reach the base"
  assert_eq "$(git -C "$p" rev-parse "$branch")" "$(git -C "$p" rev-parse main)" \
    "the base is the merge of the branch, not a second commit of its own"
  # The closure's own account of the merge, which is what a reader has left once the run's state is replaced by
  # the next run's: the branch it merged, the commits it carried, and the closure's evidence naming both.
  commits=$(jq -r '.items[0].merged.commits' "$(state_file "$p")")
  [ -n "$commits" ] && [ "$commits" != "null" ] || fail "the run recorded no merged commits"
  assert_eq "$branch" "$(jq -r '.items[0].merged.branch' "$(state_file "$p")")" \
    "the run records the branch it merged"
  assert_eq "$(git -C "$p" rev-parse "$branch")" "$(printf '%s' "$commits" | awk -F, '{print $NF}')" \
    "and the commits it recorded end at the branch's tip"
  for c in ${commits//,/ }; do
    git -C "$p" merge-base --is-ancestor "$c" main || fail "the recorded commit $c is not on the base"
  done
  assert_eq "$(git -C "$p" rev-parse main)" "$(run_state "$p" get run.base)" \
    "and the base the run recorded is the merge, so the next item builds on it"
  log=$(cat "$GH_STUB_RECORD")
  assert_contains "$log" "merged $branch (commits $commits) onto the base" \
    "the closure's evidence names the merge, so the tracker's fixed-by claim carries what backs it"
}

# The merge lands in the project's own tree, where the base branch is checked out, so a tree that is not clean at
# the moment of the closure refuses it. A run that read that refusal as a closure would write `status/fixed-by`
# about a fix only the branch has — the defect #229 reported, with the run's own child as the cause of the dirt.
# Here the dirt is a person's and is there before the run starts, so no write of the run's is being blamed: the
# tree is simply not one the merge may land in, and the item stays open with its branch named (issue #229).
case_run_doesNotCloseOnAMergeTheBaseCannotTake() {
  local d p base branch log
  d=$(scratch); p=$(new_project)
  printf 'work in progress\n' > "$p/uncommitted.txt"
  base=$(git -C "$p" rev-parse main)
  run_one_item "$p" "$d" --review on >/dev/null 2>&1 || fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" \
    "a closure whose merge the base cannot take is blocked, not settled"
  branch=$(git -C "$p" for-each-ref --format='%(refname:short)' 'refs/heads/fix/issue-21-*' | head -1)
  [ -n "$branch" ] || fail "the run left no item branch"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "not clean" \
    "the reason names the tree the merge would have landed in"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "$branch" \
    "and the branch it could not merge"
  assert_eq "$base" "$(git -C "$p" rev-parse main)" "the base did not move"
  [ ! -f "$p/demo-fix.txt" ] || fail "the fix reached the base although the merge was refused"
  assert_eq "" "$(jq -r '.items[0].merged.branch // ""' "$(state_file "$p")")" \
    "no merge is recorded for an item whose merge was refused"
  log=$(cat "$GH_STUB_RECORD")
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" \
    "the item is not closed on a fix the base does not have"
  assert_contains "$log" "agent/needs-decision" "it is escalated instead"
  assert_contains "$log" "$branch" "and the escalation names the branch a person must merge"
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
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the previous attempt (#21)" >/dev/null 2>&1 ||
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

# The kept worktree is deliberate (the next run inherits its warm checkout) and refusing a previous run's
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

# A refusal is a refusal only when something the change depends on is contradicted. A report that grades every
# finding as a note and counts zero blocking findings is a pass whose notes are recorded: the item closes, one
# round was spent, and the notes travel to the item instead of buying a second reviewer process (#205). The
# report also asks for corrections, which is the shape of a refusal: there is none here, so no round is bought.
case_run_aZeroBlockingRefusalClosesWithItsNotes() {
  local d p f notes log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'BLOCKING: none — no scenario is left unexercised and the gate tally is the one the run read
NOTE: the script calls the round budget round_cap while the design names it maxReviewRounds
NOTE: the tasks list cites the design section before the spec it belongs to
LOOP-BLOCKING: 0
LOOP-CORRECTIONS-ONLY: yes
VERDICT: FAIL'
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  f=$(state_file "$p")
  assert_eq "done" "$(jq -r '.items[0].result' "$f")" "a refusal with no blocking finding does not block the item"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$f")" "it closes"
  assert_eq "1" "$(jq -r '.items[0].metrics.reviewRounds' "$f")" "and one review round was spent, though the report asked for corrections"
  [ ! -f "$p/.pi/issue-loop/items/21/reviewer-prompt-2.md" ] ||
    fail "a second reviewer round ran for a report with no blocking finding"
  assert_eq "2" "$(grep -c '^invocation' "$PI_STUB_LOG")" "exactly one worker and one reviewer process"
  notes=$(jq -r '.items[0].note' "$f")
  assert_contains "$notes" "round_cap while the design names it maxReviewRounds" "the state records the notes"
  log=$(cat "$GH_STUB_RECORD")
  assert_contains "$log" "issue comment 21" "the closure comment is posted"
  assert_contains "$log" "the tasks list cites the design section before the spec it belongs to" \
    "and the notes reach the item"
  assert_eq "1" "$(grep -c 'issue close 21' "$GH_STUB_RECORD" || true)" "the item is closed"
}

# The other half of the same line: a report that counts blocking findings refuses exactly as the loop has always
# refused — the change goes back for a second round, and a second refusal blocks the item (#205).
case_run_aBlockingRefusalStillTakesTheRoundTwoPath() {
  local d p f
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'BLOCKING: the scenario "the budget is arguments, not a prompt" has no asserting case
BLOCKING: the red case is quoted from a build directory the next run overwrites
LOOP-BLOCKING: 2
VERDICT: FAIL'
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  f=$(state_file "$p")
  [ -f "$p/.pi/issue-loop/items/21/reviewer-prompt-2.md" ] || fail "no second review round was taken"
  assert_eq "2" "$(jq -r '.items[0].metrics.reviewRounds' "$f")" "two rounds are taken"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$f")" "and the second refusal blocks the item"
  assert_contains "$(jq -r '.items[0].note' "$f")" "last review round" "the reason names the round"
  assert_eq "4" "$(grep -c '^invocation' "$PI_STUB_LOG")" "two workers and two reviewers"
  assert_eq "0" "$(grep -c 'issue close 21' "$GH_STUB_RECORD" || true)" "nothing is closed"
}

# A report that stops mid-sentence is an answer that never arrived, and the run used to read it as a refusal
# (#224). Item #81's round-2 report ended `… and the post-restore gre` — no `VERDICT:`, no count line, no final
# newline — while its own text recorded all seven delta scenarios PASS and round 1's blocking finding as closed;
# the run took another worker round and recorded the item `blocked`, which is the same record a reviewer that
# refused the change leaves, and the same escalation a reviewer that said nothing leaves. The shape of the
# answer decides it, not its content: a cut answer ends mid-word and lacks the line the prompt asks for last, so
# the round is retried once, the item closes on the answer that arrived, and the cut attempt stays as evidence.
case_run_aCutReportIsRetriedInsteadOfRefused() {
  local d p f out log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The answer as it reached the file: every scenario PASS, the earlier finding closed, then the cut.
  printf 'Scenario 1 PASS, 2 PASS, 3 PASS, 4 PASS, 5 PASS, 6 PASS, 7 PASS, round 1 closed, and the post-restore gre' \
    > "$d/reviewer-cut.txt"
  printf 'VERDICT: PASS\n' > "$d/reviewer-complete.txt"
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  # Round 1's reviewer is cut on its way to the file, and so is round 2 — the path HEAD takes, where the cut
  # round reads as a refusal and buys a second worker. The one retry after round 1 is the answer that arrives.
  cat >> "$d/worker-action.sh" <<ACTION
case "\$1" in
  reviewer-prompt-1.md|reviewer-prompt-2.md) cp "$d/reviewer-cut.txt" "$PI_STUB_REPORT_REVIEWER" ;;
  reviewer-prompt-1-retry.md) cp "$d/reviewer-complete.txt" "$PI_STUB_REPORT_REVIEWER" ;;
esac
ACTION
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1) || fail "the run itself failed: $out"
  f=$(state_file "$p")
  # The answer that did arrive decides the round.
  assert_eq "done" "$(jq -r '.items[0].result' "$f")" "an answer cut in transport does not block the item"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$f")" "the item closes on the retried answer"
  assert_eq "1" "$(jq -r '.items[0].metrics.reviewRounds' "$f")" \
    "a retried answer is the same round, not a new one"
  # What the run did about it, in words a person reads without opening the transcript.
  assert_contains "$out" "the round 1 answer was cut short" "the run says why it read no verdict"
  assert_contains "$out" "retrying the round once" "and that it retried instead of escalating"
  # Both answers stay: the cut one is the evidence that the transport lost it, the other decided the round.
  [ -f "$p/.pi/issue-loop/items/21/reviewer-report-1.md" ] || fail "the cut answer was not kept as evidence"
  [ -f "$p/.pi/issue-loop/items/21/reviewer-report-1-retry.md" ] || fail "the retried answer was not kept"
  assert_eq "3" "$(grep -c '^invocation' "$PI_STUB_LOG")" "one worker, the cut reviewer and its one retry"
  # Nothing is escalated: the owner is not asked to decide about work that passed.
  log=$(cat "$GH_STUB_RECORD")
  assert_not_contains "$log" "agent/needs-decision" "no decision is asked for"
  assert_not_contains "$log" "status/on-hold" "and the item is not put on hold"
  assert_eq "1" "$(grep -c 'issue close 21' "$GH_STUB_RECORD" || true)" "the item is closed"
}

# The retry is one, and it is not an unbounded wait: an answer cut twice is the transport's failure, so the
# round produced no verdict at all and the item must say that. What it must not say is what a reviewer's refusal
# leaves — "the change failed its last review round" — and the branch must carry no review-failure marker, or a
# later run refuses to adopt a change no reviewer ever refused (#224).
case_run_aReportCutTwiceBlocksAsTheTransportsFault() {
  local d p f out note
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  printf 'Scenario 1 PASS, 2 PASS, 3 PASS, and then the answer stop' > "$d/reviewer-cut.txt"
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  # Every reviewer invocation is cut, so the one retry is cut too and there is no third attempt.
  cat >> "$d/worker-action.sh" <<ACTION
case "\$1" in
  reviewer*) cp "$d/reviewer-cut.txt" "$PI_STUB_REPORT_REVIEWER" ;;
esac
ACTION
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1) || fail "the run itself failed: $out"
  f=$(state_file "$p")
  note=$(jq -r '.items[0].note' "$f")
  assert_eq "blocked" "$(jq -r '.items[0].result' "$f")" "an answer cut twice blocks the item"
  assert_contains "$note" "cut short twice" "and the reason is the transport, not a refusal"
  assert_not_contains "$note" "last review round" "the item is not recorded as a change its reviewer refused"
  assert_eq "1" "$(jq -r '.items[0].metrics.reviewRounds' "$f")" \
    "the retry is one, and it is not a second review round"
  assert_eq "3" "$(grep -c '^invocation' "$PI_STUB_LOG")" "one worker, one reviewer and its one retry"
  [ ! -f "$p/.pi/issue-loop/items/21/worker-prompt-round2.md" ] ||
    fail "a second worker round was taken for an answer that never arrived"
  [ -z "$(find "$p/.pi/worktrees/loop/openspec/changes" -name 'review-failed-round-*.md' 2>/dev/null)" ] ||
    fail "a review-failure marker was written for a round no reviewer decided"
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
  # The run's own retry/abandon sentence belongs to the block with no reviewer decision, and the two are
  # mutually exclusive: appending it here would put two questions on one item (issues #225, #194).
  assert_not_contains "$log" "retry the item, or abandon it" "and not the run's own question beside it"
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

# What a child spent lives in its own event stream — the tools it called, the usage each response reported, the
# largest context it reached — and a print-mode child's stream carries the final text and nothing else, so a
# run could name an item's verdict, change, gate and wall time and not one tool call or token (issue #211).
# `run.sh` keeps the stream beside the item's reports and records the sums with the wall time; the table prints
# them. The fixture is a hand-written event stream: no model, no network, and every number below is asserted.
case_run_childUsageIsRecordedPerItem() {
  local d p f row
  d=$(scratch); p=$(new_project)
  cat > "$d/events.jsonl" <<'JSONL'
{"type":"turn_start"}
{"type":"tool_execution_end","toolCallId":"c1","toolName":"bash","durationMs":1200}
{"type":"tool_execution_end","toolCallId":"c2","toolName":"bash","durationMs":800}
{"type":"tool_execution_end","toolCallId":"c3","toolName":"read","durationMs":10}
{"type":"message_update","usage":{"input":1000,"output":100,"cacheRead":500,"cacheWrite":0,"totalTokens":1600,"cost":{"input":0.01,"output":0.002,"cacheRead":0.0005,"cacheWrite":0,"total":0.0125}},"assistantMessageEvent":{"type":"text_delta","contentIndex":0,"delta":"a"}}
{"type":"message_update","usage":{"input":2000,"output":200,"cacheRead":1000,"cacheWrite":0,"totalTokens":3200,"cost":{"input":0.02,"output":0.004,"cacheRead":0.001,"cacheWrite":0,"total":0.025}},"assistantMessageEvent":{"type":"text_delta","contentIndex":0,"delta":"b"}}
{"type":"turn_end","message":{"role":"assistant","content":[]},"toolResults":[]}
{"type":"compaction_end","reason":"threshold","result":{"summary":"the conversation so far","tokensBefore":150000},"aborted":false,"willRetry":false}
JSONL
  # …and the answer itself has to come out of the stream: this child prints no plain text at all, so the item
  # can only close if the adapter read the last assistant message out of `message_end` and printed it.
  jq -cn --arg text "$STUB_WORKER_REPORT" \
    '{type:"message_end",message:{role:"assistant",content:[{type:"text",text:$text}]}}' >> "$d/events.jsonl"
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  export PI_STUB_EVENTS="$d/events.jsonl" PI_STUB_REPORT_WORKER=""
  # One child, so every figure below is the fixture's own: with the review off the worker is the item's only
  # process.
  run_loop "$p" --items 1 --minutes 90 --review off >/dev/null || fail "the run failed"
  f=$(state_file "$p")
  assert_eq "done" "$(jq -r '.items[0].result' "$f")" \
    "the report the adapter printed out of the stream's last assistant message is the one the run read"
  assert_eq "2" "$(jq -r '.items[0].metrics.tools.bash' "$f")" "the tool counts are recorded by name"
  assert_eq "1" "$(jq -r '.items[0].metrics.tools.read' "$f")" "every tool the child used has its own count"
  # `usage` is the latest cumulative figure of one response, so a turn's spend is its *last* update: summing the
  # two updates of one turn would report 3000 input tokens for a 2000-token response.
  assert_eq "2000" "$(jq -r '.items[0].metrics.tokens.input' "$f")" "tokens are summed per turn, not per update"
  assert_eq "200" "$(jq -r '.items[0].metrics.tokens.output' "$f")" "the output tokens"
  assert_eq "1000" "$(jq -r '.items[0].metrics.tokens.cacheRead' "$f")" "and the cache read"
  assert_eq "0.025" "$(jq -r '.items[0].metrics.cost' "$f")" "the cost of the turns"
  assert_eq "150000" "$(jq -r '.items[0].metrics.context' "$f")" \
    "the largest context: the compaction's tokensBefore, above the turn's own 3000"
  row=$(run_report "$p" | grep '^| #21 |' || true)
  [ -n "$row" ] || fail "no table row for #21"
  assert_contains "$row" "bash=2 read=1" "the row names the tools and their counts"
  assert_contains "$row" "in=2000 out=200 cache=1000" "the tokens the item spent"
  assert_contains "$row" "cost=\$0.025" "and what they cost"
  assert_contains "$row" "| 150000 |" "and the largest context the child reached"

  # An item's spend is the spend of *its* children: the worker and the reviewer append to one file, so the row
  # counts both rather than the last one to finish.
  local p2 d2
  p2=$(new_project); d2=$(scratch)
  run_one_item "$p2" "$d2" >/dev/null || fail "the reviewed run failed"
  assert_eq "4" "$(jq -r '.items[0].metrics.tools.bash' "$(state_file "$p2")")" \
    "both of the item's children are counted, in one number"
}

# A project whose agent command writes plain text — no event stream at all — must keep working exactly as it
# did: nothing about the run may depend on the stream. The item is worked, recorded and reported, and the three
# new columns say `-` rather than a zero nobody measured.
case_run_plainTextChildStillReports() {
  local d p conf f row
  d=$(scratch); p=$(new_project)
  conf="$d/loop.conf"
  sed -e 's|^LOOP_AGENT_CMD=.*|LOOP_AGENT_CMD="pi --print"|' "$LOOP_CONF" > "$conf"
  export LOOP_CONF="$conf"
  run_one_item "$p" "$d" >/dev/null || fail "a plain-text agent command could not run an item"
  f=$(state_file "$p")
  assert_eq "done" "$(jq -r '.items[0].result' "$f")" "the item completed"
  [ ! -e "$p/.pi/issue-loop/items/21/events.jsonl" ] || fail "an event file appeared for a child that writes none"
  row=$(run_report "$p" | grep '^| #21 |' || true)
  [ -n "$row" ] || fail "no table row for #21"
  assert_contains "$row" "| - | - | - | done |" "tools, tokens and context are -, not zero"
}

case_run_commitsWithoutAnAmbientIdentity() {
  # A container or a CI runner has no `user.name`/`user.email` anywhere: git then refuses every commit, and
  # the run would block on its own commit (measured 2026-10-18, issue #167). The throwaway repository's local
  # identity is removed as well, so the case is the runner's environment and not the developer's.
  local d p login branch count author base_before
  d=$(scratch); p=$(new_project)
  git -C "$p" config --unset user.name >/dev/null 2>&1 || true
  git -C "$p" config --unset user.email >/dev/null 2>&1 || true
  printf '[init]\n\tdefaultBranch = main\n' > "$d/nohome.gitconfig"
  mkdir -p "$d/nohome"
  export GIT_CONFIG_GLOBAL="$d/nohome.gitconfig" GIT_CONFIG_SYSTEM=/dev/null HOME="$d/nohome"

  base_before=$(git -C "$p" rev-parse main)
  run_one_item "$p" "$d" >/dev/null || fail "a machine without an ambient git identity could not run an item"
  assert_eq "done" "$(jq -r '.items[0].result' "$(state_file "$p")")" "the item completed"

  # Both commits carry the run's own identity: the login the gate preflight resolved (the stub `gh` answers
  # it), as that login's noreply address.
  login=$(tr -d '\n' < "$LOOP_ISSUE_FIXTURES/login.txt")
  branch=$(git -C "$p" for-each-ref --format='%(refname:short)' 'refs/heads/fix/*' | head -n 1)
  [ -n "$branch" ] || fail "the item's branch does not exist"
  # The two commits are counted from the base the run started on, not from the base branch: the run merges the
  # item's branch before it closes the item, so `main` is the item's own head by the time this is read (#229).
  count=$(git -C "$p" rev-list --count "$base_before..$branch" 2>/dev/null || printf '0')
  [ "$count" -eq 2 ] || fail "the run did not commit twice on $branch (commits since the run's base: $count)"
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

# The reviewer reads the change, not the evidence archive (#204): item #126's round-3 diff was 435 KB and
# 9 935 changed lines, of which 9 364 (94.3 %) were archived gate HTML and XML, against 112 lines of Kotlin.
# So the round's diff excludes the evidence trees by *directory*, which leaves a text file outside one in the
# diff where an extension filter would have dropped it, and the prompt names the exclusion so the reviewer
# reads neither the absence nor a missing artefact.
case_run_reviewDiffExcludesTheEvidenceArchive() {
  local d p diff prompt
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The worker leaves a code change, a text note a reviewer must read that is *not* under an evidence tree, and a
  # gate report whose absence from the diff is what this case asserts.
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' 'case "$1" in
  worker*)
    printf "val fixed = 1\n" > Demo.kt
    printf "a note the reviewer has to read\n" > notes-for-the-reviewer.txt
    { i=0; while [ "$i" -lt 300 ]; do printf "<tr><td>gate row %s</td></tr>\n" "$i"; i=$((i+1)); done; } \
      > openspec/changes/archive/2026-01-01-fix-demo/evidence/gate-report.html
    ;;
esac'
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 ||
    fail "the run itself failed"
  diff="$p/.pi/issue-loop/items/21/review-1.diff"
  [ -f "$diff" ] || fail "the review round was handed no diff"
  # the change is all there — including the file an extension filter would have removed
  assert_contains "$(cat "$diff")" "Demo.kt" "the diff carries the change's code"
  assert_contains "$(cat "$diff")" "val fixed = 1" "with its hunk"
  assert_contains "$(cat "$diff")" "notes-for-the-reviewer.txt" \
    "and a text file outside an evidence tree, which an extension filter would have dropped"
  # …and none of the generated output is
  assert_not_contains "$(cat "$diff")" "gate-report.html" \
    "the generated gate report is not part of the review's reading surface"
  assert_not_contains "$(cat "$diff")" "gate row 0" "nor any of its content"
  # …and the reviewer is pointed at the evidence by path instead
  prompt="$p/.pi/issue-loop/items/21/reviewer-prompt-1.md"
  [ -f "$prompt" ] || fail "the reviewer prompt was not rendered"
  assert_contains "$(cat "$prompt")" "$p/.pi/issue-loop/items/21" \
    "the prompt points at the evidence directory the change cites"
  assert_contains "$(cat "$prompt")" "**/evidence/**" "and names the exclusion its diff was built with"
}

case_run_engineNamesNoProjectFact() {
  local hits sbxdir newpath d p
  hits=$(grep -rn 'pi --print' "$LOOP_ENGINE"/*.sh 2>/dev/null || true)
  [ -z "$hits" ] || fail "a script names the agent command instead of reading it: $hits"
  hits=$(grep -rn 'openspec-apply-change\|build-app\|run-tests\|revert-check\|gradlew\|adb\|emulator\|sbx' \
    "$LOOP_ENGINE"/*.sh "$LOOP_ENGINE/prompts/" 2>/dev/null || true)
  [ -z "$hits" ] || fail "a phase or a prompt names a project fact of its own: $hits"
  assert_contains "$(cat "$LOOP_ENGINE/loop.conf")" "LOOP_AGENT_CMD" "the configuration is where those facts live"
  # the module build directories a kept worktree loses are this project's paths too, so they are recorded
  # there as well — `tools/check-portability.sh` reads the list as a value no engine may name (issue #210)
  assert_contains "$(cat "$LOOP_ENGINE/loop.conf")" "LOOP_WORKTREE_SCRATCH_PATHS" \
    "the build directories the loop removes are recorded, not named by a phase"

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

# ── a value flag's value (issue #190) ──────────────────────────────────────────────────────────────
# A value flag whose value is missing is a usage error, named, *before* the arm consumes the two words. The
# shape it replaces was `--push) push="${2:-}"; shift 2 ;;`: `shift 2` on a one-word tail fails, leaves `$#`
# at 1 and re-enters the same iteration for ever, so `run.sh --items 100 --minutes 480 --push` printed nothing
# and never returned (measured 2026-10-20: `timeout 5 …` exited 124 with empty stdout and stderr), and the
# validation that would have refused the empty value sits after the loop. Every call below is bounded, so a
# parser that hangs again fails this case instead of taking the suite with it. Each assertion names the flag
# too: `needs a value` alone would also pass if the refusal named the *next* flag.
case_usage_bareValueFlagAtTheEndOfArgvIsAUsageError() {
  local line flag out status
  for line in \
    'tools/loop/run.sh --items 100 --minutes 480 --push' \
    'tools/loop/state.sh init --items' \
    'tools/loop/prescreen.sh --limit' \
    'tools/loop/worktree.sh create --base' \
    'tools/issues/create.sh --finding' \
    'tools/mutate.sh --only'; do
    flag=${line##* }
    out=$( cd "$LOOP_PROJECT_ROOT" && timeout 20 bash -c "$line" 2>&1 ); status=$?
    [ "$status" -eq 2 ] ||
      fail "'$line' exited $status instead of 2 — a bare value flag is a usage error, never a hang and never a default: $out"
    assert_contains "$out" "$flag needs a value" "'$line' names the value it is missing"
  done
  return 0
}

case_usage_bareValueFlagDoesNotSwallowTheNextFlag() {
  local pair flag line out status
  for pair in \
    '--push:tools/loop/run.sh --items 100 --push --dry-run' \
    '--finding:tools/issues/create.sh --finding --class bug' \
    '--case:bash tools/sensor-selftest.sh --case --list'; do
    flag=${pair%%:*}; line=${pair#*:}
    out=$( cd "$LOOP_PROJECT_ROOT" && timeout 20 bash -c "$line" 2>&1 ); status=$?
    [ "$status" -eq 2 ] || fail "'$line' exited $status instead of 2: $out"
    # The second word (`--dry-run`, `--class`, `--list`) is the flag that must *not* be read as a value, so the
    # assertion names the first flag rather than accepting any refusal.
    assert_contains "$out" "$flag needs a value" "'$line' reports $flag, not the flag's neighbour as its value"
  done
  return 0
}

# The harnesses that are engines in their own right — seven self-tests and the three mutation harnesses — parse
# `--case`/`--only` once, with no loop and no library, and the same bare flag used to run the whole set instead
# of failing: measured 2026-10-20, `bash tools/sensor-selftest.sh --case` exited 0 after all 43 cases, and a bare
# `--only` runs the whole mutation set, which rewrites and restores the tree. The assertion is the refusal
# before anything starts, and it is separate from the sweep because the sweep can only read the shape.
case_usage_bareValueFlagInASingleShotHarnessIsAUsageError() {
  local line flag out status
  for line in \
    'bash tools/loop-selftest.sh --case' \
    'bash tools/sensor-selftest.sh --case' \
    'bash tools/build-inputs-selftest.sh --case' \
    'bash tools/issues/check-metadata-selftest.sh --case' \
    'bash tools/issue-tooling-selftest.sh --case' \
    'bash tools/check-portability-selftest.sh --case' \
    'bash tools/resolve-stale-selftest.sh --case' \
    'bash tools/sensor-mutations.sh --only' \
    'bash tools/build-inputs-mutations.sh --only' \
    'bash tools/issues/metadata-mutations.sh --only'; do
    flag=${line##* }
    out=$( cd "$LOOP_PROJECT_ROOT" && timeout 20 bash -c "$line" 2>&1 ); status=$?
    [ "$status" -eq 2 ] ||
      fail "'$line' exited $status instead of 2 — a bare value flag never runs the whole set: $out"
    assert_contains "$out" "$flag needs a value" "'$line' names the value it is missing"
  done
  return 0
}

# The rule is one line per value flag, so the sweep is a check over the tree rather than a case per script: a
# new value flag written the old way is a red case on the next run, not a hang discovered by an operator. It
# reads *arm* lines only (`:--flag) …`), so a function's `local x="${2:-}"` is not a flag, and it scans the
# harnesses too — the earlier version excluded every `-selftest.sh` and so could not see the ten arms that
# still swallowed a bare `--case` (review of #190, B1). Only the `*-cases.sh` files are excluded: they are
# harness, and this file quotes the shape it looks for. An arm is guarded when it carries `need_value "$@"`,
# the inline `[ $# -ge 2 ]` check, or the message it produces.
case_usage_noUnguardedValueFlagRemains() {
  local offenders n
  offenders=$(grep -rnF -e 'shift 2 ;;' -e '${2:-}' "$LOOP_PROJECT_ROOT/tools" --include='*.sh' 2>/dev/null |
    grep -v -- '-cases.sh:' |
    grep -E ':[[:space:]]*--[a-z0-9-]+\)' |
    grep -v 'need_value' | grep -v 'needs a value' | grep -v '\$# -ge 2' || true)
  n=$(printf '%s' "$offenders" | grep -c . || true)
  [ "$n" -eq 0 ] ||
    fail "$n value flags still consume their value before checking it: $(printf '%s\n' "$offenders" | head -3 | cut -d: -f1,2 | tr '\n' ' ')"
  return 0
}

# The adapter the configuration names (`tools/pi-child-events.sh`) is the one place this project's agent and
# its output mode are a fact, and it appends that flag immediately before the prompt. The child has to see it
# as **words of its own** — `--mode`, then `json` — because that is what an option parser reads. Handed over as
# one quoted string the agent rejects the whole argv, exits in seconds, and the run records an item it never
# worked: measured 2026-10-20, every child of a run died with `Error: Unknown option: --mode json`, two in a
# row, and the run stopped after 3 min of an 8 h budget with nothing merged (its state: `.pi/issue-loop/run.json`).
#
# The case drives the real adapter against a child that only records its argv, so the assertion is on the
# adapter and not on an agent, a model or a token. A project that has no adapter — the porting recipe deletes
# it and names a plain command — has nothing to assert here.
case_adapter_modeFlagIsItsOwnArgument() {
  local d prompt recorder got
  [ -f "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" ] || return 0
  d=$(scratch)
  prompt="$d/prompt.md"
  printf 'Reply with OK.\n' > "$prompt"
  recorder="$d/recorder.sh"
  {
    printf '%s\n' '#!/usr/bin/env bash'
    printf '%s\n' 'for a in "$@"; do printf "%s\n" "$a"; done > "$RECORD"'
  } > "$recorder"
  RECORD="$d/argv.txt" bash "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" \
    bash "$recorder" "$prompt" </dev/null >"$d/out" 2>&1 ||
    fail "the adapter exited non-zero: $(head -3 "$d/out")"
  got=$(cat "$d/argv.txt" 2>/dev/null || true)
  assert_eq "$(printf -- '--mode\njson\n%s' "$prompt")" "$got" \
    "the child sees the mode flag as two arguments, then the prompt"
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

# A base that moved under the run stops it **with a reason**: the alternative is an exit that records nothing, and
# then the report reads "ended without recording a reason" while its counters belong to a run nobody can name
# (#184). Measured 2026-10-20 on a live run whose base advanced while it worked.
case_run_staleBaseStopsTheRunWithItsReason() {
  local d p out f
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario six)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' 'git -C "$LOOP_CHILD_ROOT" commit -q --allow-empty -m "advance main"'
  out=$(run_loop "$p" --items 2 --minutes 90 2>&1)
  f=$(state_file "$p")
  assert_contains "$out" "base moved" "the refusal is printed"
  assert_eq "1" "$(jq -r '.items | length' "$f")" "the item that was in flight is recorded"
  assert_contains "$(jq -r '.run.stop.reason // ""' "$f")" "the base moved" \
    "and the reason is recorded, so the report can name why the run ended"
  return 0
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
  assert_contains "$out" "closed 0 · mis-diagnosed 0 · skipped 1 · blocked 1" "the yield is counted from the state file"
  rows=$(printf '%s' "$out" | grep -c '^| #2' || true)
  assert_eq "2" "$rows" "every recorded item has a row"
  assert_contains "$out" "#21 needs-decision" "a skip is reported with its verdict"
}

# A falsified item is closed, but not by a change: the run measured that the item's premise does not hold. It is
# counted and listed apart from the closures, so a run that only falsified does not read as a run that
# delivered — and it is not asked for a gate it never had (openspec change
# `falsified-items-close-in-the-loop`).
case_report_misdiagnosedIsCountedApart() {
  local p out
  p=$(new_project)
  init_state "$p" 3 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result falsified --verdict falsified \
    --root-cause "the acquire await the item blames" --red-test "12-17 ms, probe reverted" >/dev/null
  out=$(run_report "$p") || fail "the report failed"
  assert_contains "$out" "closed 0 · mis-diagnosed 1 · skipped 0 · blocked 0" \
    "a falsified item is counted apart from a closure and from a skip"
  assert_contains "$out" "mis-diagnosed: #21 the acquire await the item blames" \
    "and listed with what it falsified"
  assert_not_contains "$out" "re-run the gate for #21" \
    "a falsified item is not asked for a gate it never had"
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

# `--state` reports on a state the next run replaced, so what a finished run attempted, spent and recorded as
# its end is still readable afterwards; without it the report is the run in place, as before (issue #207).
case_report_archivedStateIsReported() {
  local p archived out
  p=$(new_project)
  init_state "$p" 4 480
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result skipped --verdict needs-diagnosis --decision-label agent/needs-decision >/dev/null
  run_state "$p" stop "queue empty" >/dev/null || fail "stop failed"
  archived="$(dirname "$(state_file "$p")")/runs/$(run_state "$p" get run.startedAt).json"

  # the later run that used to destroy the record of the one above
  init_state "$p" 1 30
  out=$(run_report "$p" --state "$archived") || fail "report --state failed"
  assert_contains "$out" "budget 4 items / 480 min" "the archived run's own budget is reported"
  assert_contains "$out" "closed 0 · mis-diagnosed 0 · skipped 1 · blocked 0" "the archived run's counters are reported"
  assert_contains "$out" "stop: queue empty" "the archived run's stop reason is reported"
  assert_contains "$out" "| #21 |" "the archived run's item is tabled"
  assert_contains "$out" "#21 needs-diagnosis" "the archived run's skip is reported"

  out=$(run_report "$p") || fail "the default report failed"
  assert_contains "$out" "budget 1 items / 30 min" "without --state the report is still the run in place"

  # a relative name is relative to the project, and `--json` reports the same file
  out=$(run_report "$p" --state ".pi/issue-loop/runs/$(basename "$archived")") || fail "report --state with a relative name failed"
  assert_contains "$out" "budget 4 items / 480 min" "a relative --state is resolved against the project"
  out=$(run_report "$p" --json --state "$archived") || fail "report --json --state failed"
  assert_contains "$out" '"budgetLeft":3' "--json reports the state --state named"
}

# The budget left subtracts one run's spend from that same run's budget. A state whose window closed without a
# recorded stop cannot describe the tree it is read in — the measured case was a later 30-minute run that had
# re-initialised the file, reported as `budget left -421 min` about a 480-minute run (issue #207) — so the line
# names whose numbers these are instead of printing an impossible one.
case_report_impossibleBudgetNamesAnotherRun() {
  local p sf started deadline started_at deadline_at out
  p=$(new_project); sf=$(state_file "$p")
  init_state "$p" 2 30
  # the run's window closed 423 minutes ago and it recorded no stop: what the clock says now is not its spend
  started=$(( $(date +%s) - 27120 ))
  deadline=$(( started + 1800 ))
  started_at=$(date -u -d "@$started" +%Y-%m-%dT%H:%M:%SZ)
  deadline_at=$(date -u -d "@$deadline" +%Y-%m-%dT%H:%M:%SZ)
  jq --argjson started "$started" --argjson deadline "$deadline" \
     --arg startedAt "$started_at" --arg deadlineAt "$deadline_at" \
     '.run.startedEpoch = $started | .run.deadlineEpoch = $deadline
      | .run.startedAt = $startedAt | .run.deadlineAt = $deadlineAt | del(.run.stop)' "$sf" > "$sf.tmp" &&
    mv "$sf.tmp" "$sf" || fail "could not write the stale state"

  out=$(run_report "$p" 2>/dev/null) || fail "the report failed"
  assert_contains "$out" "budget left unknown: these numbers belong to another run — its window closed at $deadline_at and it recorded no stop" \
    "an impossible budget names the run its numbers belong to"
  assert_not_contains "$out" "budget left -" "no negative budget is printed"

  # The guard is the missing stop, not the closed window: the same run, having recorded that it ended 25 minutes
  # into its window, reports its own numbers however long ago that was.
  jq --arg at "$(date -u -d "@$((started + 1500))" +%Y-%m-%dT%H:%M:%SZ)" '.run.stop = {reason: "queue empty", at: $at}' "$sf" > "$sf.tmp" &&
    mv "$sf.tmp" "$sf" || fail "could not record the stop"
  out=$(run_report "$p" 2>/dev/null) || fail "the report failed"
  assert_contains "$out" "budget left 2 items / 5 min" "a run that recorded its end reports its own numbers"
}

# The report's tail, read from its `actions the caller owes:` heading to its end: the tail is what a caller
# acts on, so a case about the tail has to look at the tail and not at a line that happens to echo it.
actions_block() { printf '%s\n' "$1" | sed -n '/^actions the caller owes:/,$p'; }

# The table has to answer the run's own questions on its own (#208): how did each item end — the verdict and,
# when the result is not a closure, the item's own recorded reason — and what did it cost, read from the wall
# time `done --wall` already recorded. Both facts were in the state and printed nowhere. The spend columns
# (`tools`, `tokens`, `context`, #211) are appended after the wall time, and this case keeps the shape of the
# row honest: the earlier columns stay where they were.
case_report_wallTimeAndReasonInTheTable() {
  local p out header row_done row_blocked
  p=$(new_project)
  init_state "$p" 2 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result done --verdict closed --wall 754 \
    --gate "tests=1 failures=0" --root-cause "the fixture has exactly one cause" >/dev/null
  run_state "$p" done 22 --result blocked --verdict blocked \
    --note "two mechanisms explain the symptom" --decision-label agent/needs-decision >/dev/null
  out=$(run_report "$p") || fail "the report failed"
  header=$(printf '%s\n' "$out" | grep '^| issue |' || true)
  assert_contains "$header" "| wall |" "the table has a column for the item's wall time"
  assert_contains "$header" "| reason |" "and one for the reason a non-done result carries"
  row_done=$(printf '%s\n' "$out" | grep '^| #21 |' || true)
  row_blocked=$(printf '%s\n' "$out" | grep '^| #22 |' || true)
  [ -n "$row_done" ] || fail "no table row for #21"
  assert_contains "$row_done" "12m34s" "754 s is printed as the item's wall time"
  assert_contains "$row_done" "| done |" "and the row still says how the item ended"
  assert_contains "$row_blocked" "two mechanisms explain the symptom" "the blocked item's own reason is printed"
  assert_contains "$row_blocked" "| blocked |" "beside its outcome"
  # Fourteen columns, so fifteen separators: the wall, the reason and the three spend columns were added,
  # nothing was dropped.
  assert_eq "15" "$(printf '%s' "$row_done" | grep -o '|' | wc -l | tr -d ' ')" \
    "the row keeps its fourteen columns"
  assert_eq "15" "$(printf '%s' "$row_blocked" | grep -o '|' | wc -l | tr -d ' ')" \
    "the blocked row has the same shape"
}

# A provider that does not report usage leaves zeros in the stream, and a zero printed as a figure reads as a
# measurement nobody made: the row says `-` where nothing was reported. The event stream in this case is a
# complete turn whose usage is zero, which is what a non-reporting provider leaves behind (issue #211).
case_report_zeroUsagePrintsDashNotZero() {
  local d p row
  d=$(scratch); p=$(new_project)
  cat > "$d/zero.jsonl" <<'JSONL'
{"type":"message_update","usage":{"input":0,"output":0,"cacheRead":0,"cacheWrite":0,"totalTokens":0,"cost":{"input":0,"output":0,"cacheRead":0,"cacheWrite":0,"total":0}},"assistantMessageEvent":{"type":"text_delta","contentIndex":0,"delta":"a"}}
{"type":"turn_end","message":{"role":"assistant","content":[]},"toolResults":[]}
JSONL
  init_state "$p" 1 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result done --verdict closed --wall 42 --events "$d/zero.jsonl" >/dev/null
  row=$(run_report "$p" | grep '^| #21 |' || true)
  [ -n "$row" ] || fail "no table row for #21"
  assert_contains "$row" "| 42s | - | - | - | done |" \
    "a turn whose usage is zero prints -, never a zero that reads as a measurement"
  assert_not_contains "$row" "=0" "no zero is printed as a count"
  assert_not_contains "$row" "in=0" "nor as a token figure"
  # An item recorded with no event file at all says the same, because absence and a zeroed stream are one
  # answer: nothing was measured.
  init_state "$p" 2 90
  printf '[{"issue":22,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  run_state "$p" done 22 --result done --verdict closed --wall 42 >/dev/null
  row=$(run_report "$p" | grep '^| #22 |' || true)
  assert_contains "$row" "| 42s | - | - | - | done |" "an item with no event stream prints the same three dashes"
}

# The tail is what the caller acts on (#208): an owed decision with the question the run wrote, and every
# branch that carries a patch the base does not have, named with the item that owns it. `none` may not stand
# in for a branch that carries work.
case_report_actionsNameDecisionAndBranch() {
  local p out block
  p=$(new_project)
  init_state "$p" 2 90
  # A branch of the run's own shape, one commit ahead of the state's base — the work a finished attempt
  # left behind and the caller has to land or drop.
  printf 'demo work\n' > "$p/demo.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" commit -qm "demo work" >/dev/null 2>&1 || fail "could not commit in the throwaway repository"
  git -C "$p" branch fix/issue-21-two-mechanisms >/dev/null 2>&1 || fail "could not create the attempt branch"
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result blocked --verdict blocked \
    --note "two mechanisms explain the symptom" --decision-label agent/needs-decision >/dev/null
  run_state "$p" done 22 --result done --verdict closed --wall 100 --change fix-demo \
    --gate "tests=1 failures=0" >/dev/null
  out=$(run_report "$p") || fail "the report failed"
  block=$(actions_block "$out")
  assert_contains "$block" "decide #21" "the escalated item is an action of its own"
  assert_contains "$block" "two mechanisms explain the symptom" "carrying the question the run wrote beside it"
  assert_contains "$block" "land fix/issue-21-two-mechanisms" "the branch that carries unmerged work is named"
  assert_contains "$block" "1 commit(s)" "with what it carries"
  assert_contains "$block" "item #21" "and the item that owns it"
  assert_not_contains "$block" "none — no decision" "a branch carrying a commit is never hidden behind none"
}

# The action list is read from what a branch carries, and a branch whose every patch the base carries carries
# nothing to land: naming it sends a person to delete a leftover that needs no decision at all (#228).
case_report_landedBranchIsNoAction() {
  local p out block branch base
  p=$(new_project)
  init_state "$p" 1 90
  branch=$(landed_branch "$p" 21 landed)
  base=$(git -C "$p" rev-parse main)
  run_state "$p" init --items 1 --minutes 90 --base "$base" >/dev/null || fail "init failed"
  out=$(run_report "$p") || fail "the report failed"
  block=$(actions_block "$out")
  assert_not_contains "$block" "land $branch" "a branch whose patches are in the base is not an action"
  assert_contains "$block" "none — no decision" "the action list says none, because there is nothing to land"
}

# An action list is empty only when the state and git say so — and it says it, rather than leaving the
# question unanswered by omission.
case_report_emptyRunSaysNone() {
  local p out block lines
  p=$(new_project)
  init_state "$p" 1 90
  out=$(run_report "$p") || fail "the report failed"
  block=$(actions_block "$out")
  assert_contains "$block" "actions the caller owes:" "the action list is printed even when it is empty"
  assert_contains "$block" "none — no decision" "and says none, with what that covers"
  lines=$(printf '%s\n' "$block" | grep -c . || true)
  assert_eq "2" "$lines" "the heading and the one word, not an omitted section"
}

# ----------------------------------------------------------------------------- cases: the leftovers

# A suite ended by a signal removes its scratch tree the way a failing one does (issue #210): INT, TERM and
# HUP are trapped beside EXIT. The suite is this project's own driver, driven with a cases file of its own
# whose one case sleeps — a suite in the middle of a case is exactly that — and it is ended with TERM in that
# window. The scratch root is the loop's recorded one (`LOOP_TMP_DIR`, a temporary directory of this case's
# own), which is also the root `loop-clean` reports; `SIGKILL` is not tested because nothing can trap it, and
# those trees are what `loop-clean` is for.
case_clean_interruptedSuiteRemovesItsScratchTree() {
  local d nested suite pid status tree i tmp conf
  d=$(scratch); nested="$d/nested"; tmp="$d/tmp"
  mkdir -p "$nested/tools" "$tmp"
  # the driver is this project's; the engine and the issue tooling are the ones under test, so the nested
  # suite reads the same `lib.sh` (`LOOP_TMP_DIR`, `LOOP_SELFTEST_PREFIX`) the real one does
  cp "$LOOP_PROJECT_ROOT/tools/loop-selftest.sh" "$nested/tools/loop-selftest.sh" || fail "could not copy the driver"
  ln -s "$LOOP_ENGINE" "$nested/tools/loop"
  ln -s "$LOOP_PROJECT_ROOT/tools/issues" "$nested/tools/issues"
  cat > "$nested/tools/loop-cases.sh" <<'CASES'
LOOP_SHELL_CASES=('stub#sleeps')
loop_shell_cases() { printf '%s\n' "${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() { sleep 30; }
CASES
  conf="$d/loop.conf"
  sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$tmp\"|" "$LOOP_CONF" > "$conf" || fail "could not write the loop configuration"
  export LOOP_CONF="$conf"

  bash "$nested/tools/loop-selftest.sh" --case 'stub#sleeps' >/dev/null 2>&1 &
  pid=$!
  tree=""
  for i in $(seq 1 100); do
    tree=$(ls -d "$tmp/${LOOP_SELFTEST_PREFIX:-loop-selftest.}"* 2>/dev/null | head -1)
    [ -n "$tree" ] && [ -d "$tree" ] && break
    sleep 0.1
  done
  [ -n "$tree" ] || { kill -9 "$pid" 2>/dev/null; fail "the suite never created a scratch tree under the recorded root $tmp"; }
  sleep 0.3   # the suite is inside its case now
  kill -TERM "$pid"
  wait "$pid"; status=$?
  [ "$status" -ne 0 ] || fail "a suite ended by TERM reported success (exit 0) — a false PASS"
  [ ! -e "$tree" ] || fail "the scratch tree survived TERM: $tree ($(du -sh "$tree" | cut -f1))"
}

# `loop-clean` is the one command that answers "what did the loop leave, and how much is it": it reports
# without removing, removes with `--apply`, removes only the recorded build paths and only scratch trees old
# enough that no suite can be writing into them, and finds nothing on a second run (issue #210).
case_clean_reportsWithoutApplyAndRemovesWithIt() {
  local d p w tmp conf out n1 n2
  d=$(scratch); p=$(new_project); w="$p/.pi/worktrees/loop"
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  # a build the next run would pay for, and one stale scratch tree beside one a suite is writing into
  mkdir -p "$w/app/build/test-results/test" "$w/app/build/blobs"
  head -c 65536 /dev/zero > "$w/app/build/blobs/blobs.bin"
  tmp="$d/tmp"
  mkdir -p "$tmp/loop-selftest.stale/sub" "$tmp/loop-selftest.fresh"
  printf 'the killed suite left this\n' > "$tmp/loop-selftest.stale/sub/file"
  touch -d '3 hours ago' "$tmp/loop-selftest.stale/sub/file" "$tmp/loop-selftest.stale/sub" "$tmp/loop-selftest.stale"
  conf="$d/loop.conf"
  sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$tmp\"|" "$LOOP_CONF" > "$conf" || fail "could not write the loop configuration"
  export LOOP_CONF="$conf"

  out=$(run_clean "$p") || fail "loop-clean failed: $out"
  assert_contains "$out" "would remove" "without --apply it reports what it would remove"
  assert_contains "$out" "app/build" "naming the kept worktree's build output"
  assert_contains "$out" "loop-selftest.stale" "and the tree a killed suite left"
  assert_contains "$out" "loop-selftest.fresh" "and the tree it leaves alone"
  [ -d "$w/app/build" ] || fail "the report removed the build output without --apply"
  [ -d "$tmp/loop-selftest.stale" ] || fail "the report removed a scratch tree without --apply"
  n1=$(printf '%s\n' "$out" | sed -n 's/^loop-clean: would remove [0-9][0-9]* path(s), \([0-9][0-9]*\) B.*/\1/p' | head -1)
  [ -n "$n1" ] || fail "the report names no total: $out"
  [ "$n1" -ge 65536 ] || fail "the report undercounts what it would reclaim: $n1 B for 65536 B of build output"

  out=$(run_clean "$p" --apply) || fail "loop-clean --apply failed: $out"
  assert_contains "$out" "removed 2 path(s)" "--apply removes both what it named"
  [ ! -e "$w/app/build" ] || fail "the build output survived --apply"
  [ ! -e "$tmp/loop-selftest.stale" ] || fail "the stale scratch tree survived --apply"
  [ -d "$tmp/loop-selftest.fresh" ] || fail "--apply removed a scratch tree younger than the stale threshold"
  [ -f "$w/demo-fix.txt" ] || fail "the cleanup removed a source file from the worktree"
  assert_eq "" "$(git -C "$w" status --porcelain)" "and left the worktree's git state untouched"
  n2=$(printf '%s\n' "$out" | sed -n 's/^loop-clean: removed [0-9][0-9]* path(s), \([0-9][0-9]*\) B.*/\1/p' | head -1)
  assert_eq "$n1" "$n2" "what it reported and what it removed are the same bytes"

  out=$(run_clean "$p" --apply) || fail "the second --apply failed: $out"
  assert_contains "$out" "nothing to remove" "a second run is a no-op"
}

# ------------------------------------------------------------------- cases: the processes a suite leaves

# A suite ended by a signal ends the case *and every process that case started* (issue #213). The scratch-tree
# case above proves the tree goes; the processes still polling inside it did not. Measured 2026-10-20: five
# `tools/loop/run.sh --items 1 --minutes 90 --review off` processes, each with its `sleep 1` child, still running
# 55 to 100 min after their suites had ended, every one of them at a working directory that had already been
# deleted. The driver starts a case in a process group of its own (`set -m`) and signals that group, so a case
# that leaves a child of its own behind is the proof: both pids it wrote down must be gone when the suite is.
case_clean_interruptedSuiteEndsItsProcessGroup() {
  local d nested tmp conf pid status i p survivors driver_alive
  d=$(scratch); nested="$d/nested"; tmp="$d/tmp"
  mkdir -p "$nested/tools" "$tmp"
  cp "$LOOP_PROJECT_ROOT/tools/loop-selftest.sh" "$nested/tools/loop-selftest.sh" || fail "could not copy the driver"
  ln -s "$LOOP_ENGINE" "$nested/tools/loop"
  ln -s "$LOOP_PROJECT_ROOT/tools/issues" "$nested/tools/issues"
  # The case writes down the two pids it must not leave behind — its own process and the child it started — and
  # then waits: a suite in the middle of a case is exactly that.
  cat > "$nested/tools/loop-cases.sh" <<'PROBE'
LOOP_SHELL_CASES=('stub#sleepsWithAChild')
loop_shell_cases() { printf '%s\n' "${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() {
  sleep 300 &
  printf '%s\n' "$BASHPID" "$!" > "$PROBE_PIDS"
  wait
}
PROBE
  conf="$d/loop.conf"
  sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$tmp\"|" "$LOOP_CONF" > "$conf" || fail "could not write the loop configuration"
  export LOOP_CONF="$conf" PROBE_PIDS="$d/probe-pids"

  bash "$nested/tools/loop-selftest.sh" --case 'stub#sleepsWithAChild' >/dev/null 2>&1 &
  pid=$!
  for i in $(seq 1 200); do
    [ -f "$PROBE_PIDS" ] && [ "$(grep -c . "$PROBE_PIDS")" -ge 2 ] && break
    sleep 0.1
  done
  if [ ! -f "$PROBE_PIDS" ] || [ "$(grep -c . "$PROBE_PIDS")" -lt 2 ]; then
    kill -9 "$pid" 2>/dev/null
    fail "the case never wrote the two pids it runs with — the probe cannot see the case at all"
  fi
  kill -TERM "$pid"
  # A bound on the check, not on the case: a suite that has taken TERM is supposed to be gone at once, and the
  # tree it removes at exit is the tree of a case that is still running on the tree before the fix.
  for i in $(seq 1 100); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  driver_alive=0
  kill -0 "$pid" 2>/dev/null && driver_alive=1
  survivors=""
  for p in $(cat "$PROBE_PIDS"); do
    for i in $(seq 1 50); do kill -0 "$p" 2>/dev/null || break; sleep 0.1; done
    kill -0 "$p" 2>/dev/null && survivors="$survivors $p"
  done
  if [ "$driver_alive" -eq 1 ] || [ -n "$survivors" ]; then
    # The case's own leftovers go, so a red run leaves the tree exactly as it found it — a `sleep` or a subshell
    # of this case's own would be the very leftover this case is about.
    kill -9 "$pid" $survivors 2>/dev/null
    wait "$pid" 2>/dev/null
    fail "10 s after TERM the suite was still running its case (driver alive: $driver_alive) and left $(printf '%s' "$survivors" | wc -w) process(es) of it running:$survivors — a suite ended by a signal must end the case and everything the case started, at once"
  fi
  wait "$pid"; status=$?
  [ "$status" -ne 0 ] || fail "a suite ended by TERM reported success (exit 0) — a false PASS"
}

# A tree is not all a killed suite leaves: the processes working in it keep running, and nothing reported or ended
# them (issue #213). `loop-clean` reads a process's working directory, reports the ones standing inside a scratch
# tree it would remove, and ends them with `--apply` — and never signals a process inside a tree it keeps, which
# is a live suite's.
case_clean_reportsAndEndsAProcessLeftInAScratchTree() {
  local d p tmp conf out stale fresh i alive
  d=$(scratch); p=$(new_project); tmp="$d/tmp"
  mkdir -p "$tmp/loop-selftest.stale" "$tmp/loop-selftest.fresh"
  touch -d '3 hours ago' "$tmp/loop-selftest.stale"
  conf="$d/loop.conf"
  sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$tmp\"|" "$LOOP_CONF" > "$conf" || fail "could not write the loop configuration"
  export LOOP_CONF="$conf"
  # Two processes this case starts, each at a working directory inside one of the trees: the stale one is what a
  # killed suite leaves, the fresh one is what a live suite looks like.
  ( cd "$tmp/loop-selftest.stale" && exec sleep 300 ) &
  stale=$!
  ( cd "$tmp/loop-selftest.fresh" && exec sleep 300 ) &
  fresh=$!
  # The case's own cleanup, so a red run leaves no `sleep` of its own: the very leftover this case is about. The
  # pids are global because the trap fires when this case's subshell exits, after this function has returned.
  CASE_SLEEPS="$stale $fresh"
  trap 'kill -9 $CASE_SLEEPS 2>/dev/null; CASE_SLEEPS=""' EXIT

  out=$(run_clean "$p") || fail "loop-clean failed: $out"
  assert_contains "$out" "would end process $stale" "the report names the process standing inside the tree it would remove"
  assert_contains "$out" "loop-selftest.stale" "and the tree that process is in"
  assert_not_contains "$out" "would end process $fresh" "a process inside a tree it keeps is a live suite's and is not named"
  kill -0 "$stale" 2>/dev/null || fail "the report ended a process without --apply"
  [ -d "$tmp/loop-selftest.stale" ] || fail "the report removed the tree without --apply"

  out=$(run_clean "$p" --apply) || fail "loop-clean --apply failed: $out"
  assert_contains "$out" "ended process $stale" "--apply names the process it ends"
  assert_contains "$out" "and ended 1 process(es)" "and counts it in the summary"
  alive=1
  for i in $(seq 1 50); do kill -0 "$stale" 2>/dev/null || { alive=0; break; }; sleep 0.1; done
  [ "$alive" -eq 0 ] || fail "the process survived --apply — the tree it was working in was removed under it"
  kill -0 "$fresh" 2>/dev/null || fail "--apply ended a process inside a tree it kept"
  [ ! -e "$tmp/loop-selftest.stale" ] || fail "the stale tree survived --apply"
  [ -d "$tmp/loop-selftest.fresh" ] || fail "--apply removed a tree younger than the stale threshold"
}

# A run ended by a signal ends the child it started (issue #213). The child is put in a process group of its own
# so the wall-clock cap can end it *and its descendants* (#203), which means the run's own traps — the path a
# person's `kill` takes mid-item — used to end the run and leave the child working. The stub child blocks on a
# fifo, which is a child in the middle of its phase, and both the run and the child must be gone afterwards.
case_run_interruptedRunEndsTheChildItStarted() {
  local d p conf fifo pid i status child alive
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  conf="$d/loop.conf"
  cp "$LOOP_CONF" "$conf" || fail "could not copy the loop configuration"
  export LOOP_CONF="$conf"
  stub_success "$d"
  # The child names the process the run started for it: the stub `pi`, which is this action's parent.
  printf 'printf "%%s\\n" "$PPID" > %s\n' "'$d/child.pid'" >> "$d/worker-action.sh"
  fifo="$d/wait.fifo"; mkfifo "$fifo"
  set -m
  ( export PI_STUB_WAIT="$fifo"
    run_loop "$p" --items 1 --minutes 90 --review off > "$d/out" 2>&1 ) &
  pid=$!
  set +m
  for i in $(seq 1 200); do [ -e "$fifo.ready" ] && break; sleep 0.1; done
  if [ ! -e "$fifo.ready" ]; then
    kill -9 "$pid" 2>/dev/null
    fail "the stub child never started, so there is no child to end"
  fi
  # The case holds the fifo's writer side, so releasing a child never blocks and never leaves a writer behind.
  exec 8> "$fifo"
  child=""
  for i in $(seq 1 100); do [ -f "$d/child.pid" ] && { child=$(cat "$d/child.pid"); break; }; sleep 0.1; done
  if [ -z "$child" ]; then
    printf 'go\n' >&8 2>/dev/null || true
    wait "$pid" 2>/dev/null
    exec 8>&-
    fail "the child never named the process the run started"
  fi
  kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
  wait "$pid"; status=$?
  assert_eq "143" "$status" "a run ended by TERM reports the signal it took"
  # The child is checked while it is still blocked on the fifo: releasing it first would end it here, and a child
  # this case ended is no evidence about the run.
  for i in $(seq 1 50); do kill -0 "$child" 2>/dev/null || break; sleep 0.1; done
  alive=0
  kill -0 "$child" 2>/dev/null && alive=1
  if [ "$alive" -eq 1 ]; then
    # Still blocked on the fifo: release it so it can leave on its own, then make sure it is gone. Writing to the
    # fifo is what a reader there makes safe — with the reader gone this case would take SIGPIPE instead.
    printf 'go\n' >&8 2>/dev/null || true
    kill -9 "$child" 2>/dev/null
  fi
  exec 8>&-
  [ "$alive" -eq 0 ] || fail "the run ended but the child it started (pid $child) is still running — an interrupted run must end its child"
}
