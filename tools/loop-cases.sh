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
#   ISSUES_CORE_CLASSES  the Java core, once a case has asked for it (`core_ensure`)
#   LOOP_TMP             a scratch directory under the gitignored `.pi/tmp/`
# and puts the stub `pi` and the stub `gh` first on PATH.
#
# The driver compiles nothing until a case asks for it (issue #261), so **a case that starts the engine or the issue
# tooling calls `core_ensure` first** — `run_loop` does it for the cases that go through it. A case that reaches the
# tooling without asking is served by the tooling's own on-demand compile, at the price of a second compile rather
# than of a red suite.
#
# A case runs in a subshell; it prints nothing on success and a reason on failure, exiting non-zero.
#
# **The first line of a case body declares the files the case can be affected by** — `# files: <token> …`, tokens
# being repository-relative paths, a token ending in `/` for a directory and `*` for "any file at all".
# `tools/loop-selftest.sh --files <path> …` / `--from-git <base>` selects the cases a delta's paths declare, which
# is what lets the gate of a `tools/loop/**` delta run a handful of the 190 cases instead of all of them (723 s,
# measured 2026-10-23; issue #276). The declaration is over-approximated on purpose — a case costs seconds, a
# missed red case costs a landing — and a case that names nothing is declared `*`, so it is owed for any delta.
# `driver#everyCaseDeclaresTheFilesItCanReach` is what keeps a new case from arriving without one.
set -uo pipefail

LOOP_SHELL_CASES=(
  'state#initAndShowRoundTrips'
  'state#nextHonoursEveryStopCondition'
  'state#floorIsDerivedFromTheFinishedItemsWallTimes'
  'state#floorWithNoHistoryIsTheConfiguredMinimum'
  'state#secondRunRefusesWithExit5'
  'state#aRefusedRunLeavesTheLiveStateAlone'
  'state#aDeadOwnersLockIsTakenOver'
  'state#initKeepsTheStateItReplaces'
  'state#initAnnouncesARunWithoutAStop'
  'state#recordsTheBaseBranchNotTheCheckout'
  'state#baseBranchIsAConfigurationKey'
  'state#setBaseRefusesACommitTheBaseBranchDoesNotCarry'
  'state#aBlockedItemNamesTheMechanismThatStoppedIt'
  'state#defaultsComeFromLoopConf'
  'state#projectKeysAreReadFromLoopConf'
  'state#agentCommandIsTheOnlyIndirection'
  'state#agentCommandRefusesAnEmptyValue'
  'prescreen#ranksBugsFirstAndKeepsFeatures'
  'prescreen#oneHarnessImprovementPerFourAppItems'
  'prescreen#aRefusedClassStillStopsTheScreen'
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
  'prescreen#anUndeclaredLeadingIntervalStopsTheScreen'
  'prescreen#escalatedItemKeepsItsBranchAsEvidence'
  'prescreen#refusalLeavesATraceInTheTracker'
  'prescreen#aFixInTheSubmoduleIsAPersonsPatch'
  'worktree#seedsAllFourInputs'
  'worktree#checksOutTheSubmoduleTheLoopConfigNames'
  'worktree#oneBranchPerItemOffTheBase'
  'worktree#refusesWhenMainAdvanced'
  'worktree#keepsByDefaultAndRemovesOnRequest'
  'worktree#keepsSourcesNotBuildOutput'
  'worktree#noPushWithoutTheFlag'
  'worktree#pushSkipsABranchItAlreadyMerged'
  'worktree#reusesABranchThatCarriesNoWorkOfItsOwn'
  'worktree#reusesABranchWhoseWorkAlreadyLanded'
  'worktree#refusesABranchCarryingAPreviousAttempt'
  'worktree#setsAsideABlockedAttemptAndStartsFresh'
  'worktree#doesNotAdoptAChangeCreditedToAnotherIssue'
  'worktree#refusesABranchWhoseReviewFailed'
  'worktree#refusesABranchWhoseReviewRoundNeverAnswered'
  'worktree#refusesToMergeOverADirtyProjectTree'
  'worktree#refusesToReplayABranchTheBaseDoesNotDescendFrom'
  'worktree#refusesToMergeIntoATreeOffTheBaseBranch'
  'labels#verdictToLabelMatrix'
  'labels#holdCarriesItsReason'
  'labels#trapRemovesInProgressOnSignal'
  'labels#neverWritesReservedLabel'
  'labels#writesOnlyBetweenChildren'
  'labels#closeRemovesTheDecisionLabel'
  'prompt#workerNamesEveryPhaseAndSkill'
  'prompt#workerCarriesTheFeaturePath'
  'prompt#workerNamesTheDecisionField'
  'prompt#workerTreatsARecordedDecisionAsSettled'
  'prompt#reviewerCarriesTheEightItems'
  'prompt#reviewerIsBoundedToReading'
  'prompt#iterationProtocolHandsTheReviewerTheSameSkeleton'
  'prompt#workerNamesTheChangeSurfaceBeforeThePlan'
  'prompt#iterationProtocolNamesTheChangeSurface'
  'prompt#packageIsAFileAndNotInlined'
  'prompt#inlineInjectionCannotReachTheInstructionPosition'
  'run#preflightRefusalStopsBeforeAnyWork'
  'run#aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen'
  'run#blockedItemAsksItsOwnQuestion'
  'run#falsifiedSkipClosesWithItsMeasurement'
  'run#falsificationWithoutAMeasurementIsEscalated'
  'run#mayCloseNoKeepsAFalsificationOpen'
  'run#falsificationWithAChangedTreeIsBlocked'
  'run#decisionCapRefusesToStart'
  'run#refusesARepositoryThatIsNotAWorkTree'
  'gate#theDeltaSelectsTheSuitesItTouches'
  'gate#theMapCoversEveryPipelineStep'
  'gate#aPipelineCheckIsOwedByItsPaths'
  'gate#anEnginePathOwesTheRecordCheck'
  'gate#theSuitesOutputReachesTheConsoleWhileItRuns'
  'gate#aFailingSuiteIsReadFromTheSameStream'
  'gate#aFailingSuiteWithNoSummaryInventsNoTest'
  'gate#aCompiledStepsTallyIsNotHidden'
  'gate#theSummaryNamesWhatEachSuiteCost'
  'driver#aGroupFilterRunsOnlyItsOwnCases'
  'driver#aFilterThatNamesNoCaseRefuses'
  'driver#theListIsFilteredTheSameWayASuiteRunIs'
  'gate#anArchivedArtefactOwesNoBuild'
  'gate#aModuleResourceOwesTheBuild'
  'gate#theGateNamesTheProfileItRead'
  'gate#theChildReadsTheProfileOfTheTreeItWorksIn'
  'mutation#everyDeclaredRecordApplies'
  'mutation#aStaleRecordIsNamed'
  'measure#namesTheOwedChecksInOneBlock'
  'measure#printsTheNumbersTheReviewerAsksFor'
  'measure#refusesToGuessWhichChangeItMeasures'
  'measure#saysHowManyTalliesItWithheld'
  'prompt#workerIsHandedTheMeasureBlock'
  'run#oneItemOrderIsFixed'
  'run#childClaimWithoutCommitIsBlocked'
  'run#blockedItemIsEscalatedWithItsReason'
  'run#aFailedEscalationWriteIsNamed'
  'run#aFailedUnpickIsNamed'
  'run#childStartsInTheProjectRoot'
  'run#childReportsWhileItWaits'
  'run#headerNamesTheLiveView'
  'run#prescreenAnnouncesItselfBeforeItReturns'
  'run#everyPhaseBoundaryIsPrinted'
  'run#childOutlastingTheWallClockIsBlocked'
  'run#childIsEndedAtTheRunsDeadline'
  'run#detachedProcessInTheWorktreeIsEndedByTheCap'
  'run#treeOfAChildTheCapEndedIsNotCommitted'
  'run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted'
  'run#childThatNeverStartedIsTheRunsFault'
  'run#hostBelowTheFloorBlocksTheItemAndStops'
  'run#hostAboveTheFloorRunsTheItem'
  'run#hostWithoutTheReadingPasses'
  'run#childWriteOutsideTheWorktreeIsNoted'
  'run#aWriterTheRunCanPlaceBlocksTheItem'
  'run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem'
  'run#dirtyBaseRefusesTheMergeAndNamesThePath'
  'run#strictOutsideWriteBlocksTheItem'
  'run#strictOutsideWriteDiscardsTheReviewRound'
  'run#leftoverBranchIsRecordedNotDropped'
  'run#branchThatAppearsWhileTheRunIsOpenIsSetAside'
  'run#mergesAFinishedAttemptAndClosesTheItem'
  'run#doesNotMergeAnAttemptWithoutAGreenVerification'
  'run#mergesItsCommittedBranchBeforeClosing'
  'run#aMergedBranchIsRemoved'
  'run#theBaseAdvancesWithEveryMerge'
  'run#staleTestResultsAreNotThisChildsGate'
  'run#doesNotCloseOnAMergeTheBaseCannotTake'
  'run#recordsAFailedReviewOnTheBranch'
  'run#announcesAndRecordsTheReviewRound'
  'run#dirtyKeptWorktreeIsARecordedRefusal'
  'run#resetWorktreeDiscardsWhatItNames'
  'run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds'
  'run#aSkipClearsTheInProgressMark'
  'run#stateNamesTheItemInProgress'
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
  'run#aCorrectiveRoundIsHandedTheCorrections'
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
  'report#aKilledRunIsNotALiveRun'
  'report#wallTimeAndReasonInTheTable'
  'report#zeroUsagePrintsDashNotZero'
  'report#actionsNameDecisionAndBranch'
  'report#landedBranchIsNoAction'
  'report#landedBranchesAreNamedAsRemovable'
  'report#emptyRunSaysNone'
  'clean#interruptedSuiteRemovesItsScratchTree'
  'clean#reportsWithoutApplyAndRemovesWithIt'
  'clean#sweepsOnlyWhatIsInTheBase'
  'clean#interruptedSuiteEndsItsProcessGroup'
  'clean#reportsAndEndsAProcessLeftInAScratchTree'
  'clean#reportsTheWorktreesScratchTreesToo'
  'clean#worktreeDotSweepsTheOneRootOnce'
  'clean#endsARunWhoseTreeIsAlreadyDeleted'
  'clean#suiteThatEndsNormallyEndsTheProcessItsCaseLeft'
  'run#interruptedRunEndsTheChildItStarted'
  'usage#bareValueFlagAtTheEndOfArgvIsAUsageError'
  'usage#bareValueFlagDoesNotSwallowTheNextFlag'
  'usage#bareValueFlagInASingleShotHarnessIsAUsageError'
  'usage#noUnguardedValueFlagRemains'
  'driver#aCaseThatAsksForNoCoreCompilesNothing'
  'driver#aCaseThatAsksForTheCoreGetsItOrRefuses'
  'driver#theCoreIsCompiledOncePerSuite'
  'driver#aFileFilterRunsOnlyTheCasesTheDeltaOwns'
  'driver#aFileSetThatNamesNoCaseIsRefused'
  'driver#aCoreFileOwesEveryCase'
  'driver#everyCaseDeclaresTheFilesItCanReach'
  'driver#aSecondSuiteInOneRepositoryIsRefused'
  'gate#theDeltaHandsASuiteTheBaseItMeasured'
)

loop_shell_cases() {
  printf '%s\n' "${LOOP_SHELL_CASES[@]}"
}

# The files a delta must touch for the *whole* case set to be owed, whatever the cases declare. Every engine script
# sources `lib.sh` and the profile, every case is dispatched by this file and run by the driver, so a delta to one of
# them is owed everything: the declaration narrows a script's own cases, never the suite's own machinery.
loop_case_core_files() {
  printf '%s\n' 'tools/loop/lib.sh tools/loop/loop.conf tools/loop-cases.sh tools/loop-selftest.sh'
}

# The engine paths a case inherits by calling one of this file's helpers — `run_worktree` starts `worktree.sh`,
# `new_project` copies these two. The declaration above is what the driver selects on; this map is what
# `driver#everyCaseDeclaresTheFilesItCanReach` compares it against, so a helper that starts a new script, or a case
# that calls a helper and declares nothing it reaches, is named instead of silently narrowing the gate.
loop_case_helper_files() {
  printf '%s\n' \
    'run_state|tools/loop/state.sh' \
    'run_prescreen|tools/loop/prescreen.sh' \
    'run_worktree|tools/loop/worktree.sh' \
    'run_clean|tools/loop/loop-clean.sh' \
    'run_labels|tools/loop/labels.sh' \
    'run_report|tools/loop/report.sh' \
    'run_loop|tools/loop/run.sh' \
    'init_state|tools/loop/state.sh' \
    'new_project|tools/pi-child-events.sh tools/tooling.json'
}

# The repository paths a case body names, with the engine's own variables read as the paths they stand for — the same
# reading the declaration `# files:` line was written with, so the two can be compared. Kept out of the case that
# compares them: a table of substitutions inside a case body is a table of paths that case would have to declare.
loop_case_body_paths() { # stdin: a case body → the paths it names, one per line, unsorted
  sed -e 's#\${LOOP_ENGINE}/#tools/loop/#g' -e 's#\$LOOP_ENGINE/#tools/loop/#g' \
      -e 's#\${LOOP_FIXTURES}/#tools/loop/fixtures/#g' -e 's#\$LOOP_FIXTURES/#tools/loop/fixtures/#g' \
      -e 's#\${LOOP_ISSUE_FIXTURES}/#tools/issues/fixtures/#g' -e 's#\$LOOP_ISSUE_FIXTURES/#tools/issues/fixtures/#g' \
      -e 's#\${LOOP_PROJECT_ROOT}##g' -e 's#\$LOOP_PROJECT_ROOT##g' \
      -e 's#\${root}##g' -e 's#\$root##g' |
    grep -o 'tools/[A-Za-z0-9_./-]*[A-Za-z0-9_-]' | sort -u
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
    'state#aRefusedRunLeavesTheLiveStateAlone') fn=case_state_aRefusedRunLeavesTheLiveStateAlone ;;
    'state#aDeadOwnersLockIsTakenOver') fn=case_state_aDeadOwnersLockIsTakenOver ;;
    'state#initKeepsTheStateItReplaces') fn=case_state_initKeepsTheStateItReplaces ;;
    'state#initAnnouncesARunWithoutAStop') fn=case_state_initAnnouncesARunWithoutAStop ;;
    'state#recordsTheBaseBranchNotTheCheckout') fn=case_state_recordsTheBaseBranchNotTheCheckout ;;
    'state#baseBranchIsAConfigurationKey') fn=case_state_baseBranchIsAConfigurationKey ;;
    'state#setBaseRefusesACommitTheBaseBranchDoesNotCarry') fn=case_state_setBaseRefusesACommitTheBaseBranchDoesNotCarry ;;
    'state#aBlockedItemNamesTheMechanismThatStoppedIt') fn=case_state_aBlockedItemNamesTheMechanismThatStoppedIt ;;
    'state#defaultsComeFromLoopConf') fn=case_state_defaultsComeFromLoopConf ;;
    'state#projectKeysAreReadFromLoopConf') fn=case_state_projectKeysAreReadFromLoopConf ;;
    'state#agentCommandIsTheOnlyIndirection') fn=case_state_agentCommandIsTheOnlyIndirection ;;
    'state#agentCommandRefusesAnEmptyValue') fn=case_state_agentCommandRefusesAnEmptyValue ;;
    'state#inheritedStatePathOutsideTheTreeIsIgnored') fn=case_state_inheritedStatePathOutsideTheTreeIsIgnored ;;
    'prescreen#ranksBugsFirstAndKeepsFeatures') fn=case_prescreen_ranksBugsFirstAndKeepsFeatures ;;
    'prescreen#oneHarnessImprovementPerFourAppItems') fn=case_prescreen_oneHarnessImprovementPerFourAppItems ;;
    'prescreen#aRefusedClassStillStopsTheScreen') fn=case_prescreen_aRefusedClassStillStopsTheScreen ;;
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
    'prescreen#anUndeclaredLeadingIntervalStopsTheScreen') fn=case_prescreen_anUndeclaredLeadingIntervalStopsTheScreen ;;
    'prescreen#escalatedItemKeepsItsBranchAsEvidence') fn=case_prescreen_escalatedItemKeepsItsBranchAsEvidence ;;
    'prescreen#refusalLeavesATraceInTheTracker') fn=case_prescreen_refusalLeavesATraceInTheTracker ;;
    'prescreen#aFixInTheSubmoduleIsAPersonsPatch') fn=case_prescreen_aFixInTheSubmoduleIsAPersonsPatch ;;
    'worktree#seedsAllFourInputs') fn=case_worktree_seedsAllFourInputs ;;
    'worktree#checksOutTheSubmoduleTheLoopConfigNames') fn=case_worktree_checksOutTheSubmoduleTheLoopConfigNames ;;
    'worktree#oneBranchPerItemOffTheBase') fn=case_worktree_oneBranchPerItemOffTheBase ;;
    'worktree#refusesWhenMainAdvanced') fn=case_worktree_refusesWhenMainAdvanced ;;
    'worktree#keepsByDefaultAndRemovesOnRequest') fn=case_worktree_keepsByDefaultAndRemovesOnRequest ;;
    'worktree#keepsSourcesNotBuildOutput') fn=case_worktree_keepsSourcesNotBuildOutput ;;
    'worktree#noPushWithoutTheFlag') fn=case_worktree_noPushWithoutTheFlag ;;
    'worktree#pushSkipsABranchItAlreadyMerged') fn=case_worktree_pushSkipsABranchItAlreadyMerged ;;
    'worktree#reusesABranchThatCarriesNoWorkOfItsOwn') fn=case_worktree_reusesABranchThatCarriesNoWorkOfItsOwn ;;
    'worktree#reusesABranchWhoseWorkAlreadyLanded') fn=case_worktree_reusesABranchWhoseWorkAlreadyLanded ;;
    'worktree#refusesABranchCarryingAPreviousAttempt') fn=case_worktree_refusesABranchCarryingAPreviousAttempt ;;
    'worktree#setsAsideABlockedAttemptAndStartsFresh') fn=case_worktree_setsAsideABlockedAttemptAndStartsFresh ;;
    'worktree#doesNotAdoptAChangeCreditedToAnotherIssue') fn=case_worktree_doesNotAdoptAChangeCreditedToAnotherIssue ;;
    'worktree#refusesABranchWhoseReviewFailed') fn=case_worktree_refusesABranchWhoseReviewFailed ;;
    'worktree#refusesABranchWhoseReviewRoundNeverAnswered') fn=case_worktree_refusesABranchWhoseReviewRoundNeverAnswered ;;
    'worktree#refusesToMergeOverADirtyProjectTree') fn=case_worktree_refusesToMergeOverADirtyProjectTree ;;
    'worktree#refusesToReplayABranchTheBaseDoesNotDescendFrom') fn=case_worktree_refusesToReplayABranchTheBaseDoesNotDescendFrom ;;
    'worktree#refusesToMergeIntoATreeOffTheBaseBranch') fn=case_worktree_refusesToMergeIntoATreeOffTheBaseBranch ;;
    'labels#verdictToLabelMatrix') fn=case_labels_verdictToLabelMatrix ;;
    'labels#holdCarriesItsReason') fn=case_labels_holdCarriesItsReason ;;
    'labels#trapRemovesInProgressOnSignal') fn=case_labels_trapRemovesInProgressOnSignal ;;
    'labels#neverWritesReservedLabel') fn=case_labels_neverWritesReservedLabel ;;
    'labels#writesOnlyBetweenChildren') fn=case_labels_writesOnlyBetweenChildren ;;
    'labels#closeRemovesTheDecisionLabel') fn=case_labels_closeRemovesTheDecisionLabel ;;
    'prompt#workerNamesEveryPhaseAndSkill') fn=case_prompt_workerNamesEveryPhaseAndSkill ;;
    'prompt#workerCarriesTheFeaturePath') fn=case_prompt_workerCarriesTheFeaturePath ;;
    'prompt#workerNamesTheDecisionField') fn=case_prompt_workerNamesTheDecisionField ;;
    'prompt#workerTreatsARecordedDecisionAsSettled') fn=case_prompt_workerTreatsARecordedDecisionAsSettled ;;
    'prompt#reviewerCarriesTheEightItems') fn=case_prompt_reviewerCarriesTheEightItems ;;
    'prompt#reviewerIsBoundedToReading') fn=case_prompt_reviewerIsBoundedToReading ;;
    'prompt#iterationProtocolHandsTheReviewerTheSameSkeleton') fn=case_prompt_iterationProtocolHandsTheReviewerTheSameSkeleton ;;
    'prompt#workerNamesTheChangeSurfaceBeforeThePlan') fn=case_prompt_workerNamesTheChangeSurfaceBeforeThePlan ;;
    'prompt#iterationProtocolNamesTheChangeSurface') fn=case_prompt_iterationProtocolNamesTheChangeSurface ;;
    'prompt#packageIsAFileAndNotInlined') fn=case_prompt_packageIsAFileAndNotInlined ;;
    'prompt#inlineInjectionCannotReachTheInstructionPosition') fn=case_prompt_inlineInjectionCannotReachTheInstructionPosition ;;
    'run#preflightRefusalStopsBeforeAnyWork') fn=case_run_preflightRefusalStopsBeforeAnyWork ;;
    'run#aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen') fn=case_run_aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen ;;
    'run#blockedItemAsksItsOwnQuestion') fn=case_run_blockedItemAsksItsOwnQuestion ;;
    'run#falsifiedSkipClosesWithItsMeasurement') fn=case_run_falsifiedSkipClosesWithItsMeasurement ;;
    'run#falsificationWithoutAMeasurementIsEscalated') fn=case_run_falsificationWithoutAMeasurementIsEscalated ;;
    'run#mayCloseNoKeepsAFalsificationOpen') fn=case_run_mayCloseNoKeepsAFalsificationOpen ;;
    'run#falsificationWithAChangedTreeIsBlocked') fn=case_run_falsificationWithAChangedTreeIsBlocked ;;
    'run#decisionCapRefusesToStart') fn=case_run_decisionCapRefusesToStart ;;
    'run#refusesARepositoryThatIsNotAWorkTree') fn=case_run_refusesARepositoryThatIsNotAWorkTree ;;
    'gate#theDeltaSelectsTheSuitesItTouches') fn=case_gate_theDeltaSelectsTheSuitesItTouches ;;
    'gate#theMapCoversEveryPipelineStep') fn=case_gate_theMapCoversEveryPipelineStep ;;
    'gate#aPipelineCheckIsOwedByItsPaths') fn=case_gate_aPipelineCheckIsOwedByItsPaths ;;
    'gate#anEnginePathOwesTheRecordCheck') fn=case_gate_anEnginePathOwesTheRecordCheck ;;
    'gate#theSuitesOutputReachesTheConsoleWhileItRuns') fn=case_gate_theSuitesOutputReachesTheConsoleWhileItRuns ;;
    'gate#aFailingSuiteIsReadFromTheSameStream') fn=case_gate_aFailingSuiteIsReadFromTheSameStream ;;
    'gate#aFailingSuiteWithNoSummaryInventsNoTest') fn=case_gate_aFailingSuiteWithNoSummaryInventsNoTest ;;
    'gate#aCompiledStepsTallyIsNotHidden') fn=case_gate_aCompiledStepsTallyIsNotHidden ;;
    'gate#theSummaryNamesWhatEachSuiteCost') fn=case_gate_theSummaryNamesWhatEachSuiteCost ;;
    'driver#aGroupFilterRunsOnlyItsOwnCases') fn=case_driver_aGroupFilterRunsOnlyItsOwnCases ;;
    'driver#aFilterThatNamesNoCaseRefuses') fn=case_driver_aFilterThatNamesNoCaseRefuses ;;
    'driver#theListIsFilteredTheSameWayASuiteRunIs') fn=case_driver_theListIsFilteredTheSameWayASuiteRunIs ;;
    'gate#anArchivedArtefactOwesNoBuild') fn=case_gate_anArchivedArtefactOwesNoBuild ;;
    'gate#aModuleResourceOwesTheBuild') fn=case_gate_aModuleResourceOwesTheBuild ;;
    'gate#theGateNamesTheProfileItRead') fn=case_gate_theGateNamesTheProfileItRead ;;
    'gate#theChildReadsTheProfileOfTheTreeItWorksIn') fn=case_gate_theChildReadsTheProfileOfTheTreeItWorksIn ;;
    'mutation#everyDeclaredRecordApplies') fn=case_mutation_everyDeclaredRecordApplies ;;
    'mutation#aStaleRecordIsNamed') fn=case_mutation_aStaleRecordIsNamed ;;
    'measure#namesTheOwedChecksInOneBlock') fn=case_measure_namesTheOwedChecksInOneBlock ;;
    'measure#printsTheNumbersTheReviewerAsksFor') fn=case_measure_printsTheNumbersTheReviewerAsksFor ;;
    'measure#refusesToGuessWhichChangeItMeasures') fn=case_measure_refusesToGuessWhichChangeItMeasures ;;
    'measure#saysHowManyTalliesItWithheld') fn=case_measure_saysHowManyTalliesItWithheld ;;
    'prompt#workerIsHandedTheMeasureBlock') fn=case_prompt_workerIsHandedTheMeasureBlock ;;
    'run#oneItemOrderIsFixed') fn=case_run_oneItemOrderIsFixed ;;
    'run#childClaimWithoutCommitIsBlocked') fn=case_run_childClaimWithoutCommitIsBlocked ;;
    'run#blockedItemIsEscalatedWithItsReason') fn=case_run_blockedItemIsEscalatedWithItsReason ;;
    'run#aFailedEscalationWriteIsNamed') fn=case_run_aFailedEscalationWriteIsNamed ;;
    'run#aFailedUnpickIsNamed') fn=case_run_aFailedUnpickIsNamed ;;
    'run#childStartsInTheProjectRoot') fn=case_run_childStartsInTheProjectRoot ;;
    'run#childReportsWhileItWaits') fn=case_run_childReportsWhileItWaits ;;
    'run#headerNamesTheLiveView') fn=case_run_headerNamesTheLiveView ;;
    'run#prescreenAnnouncesItselfBeforeItReturns') fn=case_run_prescreenAnnouncesItselfBeforeItReturns ;;
    'run#everyPhaseBoundaryIsPrinted') fn=case_run_everyPhaseBoundaryIsPrinted ;;
    'run#childOutlastingTheWallClockIsBlocked') fn=case_run_childOutlastingTheWallClockIsBlocked ;;
    'run#childIsEndedAtTheRunsDeadline') fn=case_run_childIsEndedAtTheRunsDeadline ;;
    'run#detachedProcessInTheWorktreeIsEndedByTheCap') fn=case_run_detachedProcessInTheWorktreeIsEndedByTheCap ;;
    'run#treeOfAChildTheCapEndedIsNotCommitted') fn=case_run_treeOfAChildTheCapEndedIsNotCommitted ;;
    'run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted') fn=case_run_treeOfACapEndedReworkOfARefusingRoundIsNotCommitted ;;
    'run#childThatNeverStartedIsTheRunsFault') fn=case_run_childThatNeverStartedIsTheRunsFault ;;
    'run#hostBelowTheFloorBlocksTheItemAndStops') fn=case_run_hostBelowTheFloorBlocksTheItemAndStops ;;
    'run#hostAboveTheFloorRunsTheItem') fn=case_run_hostAboveTheFloorRunsTheItem ;;
    'run#hostWithoutTheReadingPasses') fn=case_run_hostWithoutTheReadingPasses ;;
    'run#childWriteOutsideTheWorktreeIsNoted') fn=case_run_childWriteOutsideTheWorktreeIsNoted ;;
    'run#aWriterTheRunCanPlaceBlocksTheItem') fn=case_run_aWriterTheRunCanPlaceBlocksTheItem ;;
    'run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem') fn=case_run_aPeerWriteWhileAChildStandsDoesNotDecideTheItem ;;
    'run#dirtyBaseRefusesTheMergeAndNamesThePath') fn=case_run_dirtyBaseRefusesTheMergeAndNamesThePath ;;
    'run#strictOutsideWriteBlocksTheItem') fn=case_run_strictOutsideWriteBlocksTheItem ;;
    'run#strictOutsideWriteDiscardsTheReviewRound') fn=case_run_strictOutsideWriteDiscardsTheReviewRound ;;
    'run#leftoverBranchIsRecordedNotDropped') fn=case_run_leftoverBranchIsRecordedNotDropped ;;
    'run#branchThatAppearsWhileTheRunIsOpenIsSetAside') fn=case_run_branchThatAppearsWhileTheRunIsOpenIsSetAside ;;
    'run#mergesAFinishedAttemptAndClosesTheItem') fn=case_run_mergesAFinishedAttemptAndClosesTheItem ;;
    'run#doesNotMergeAnAttemptWithoutAGreenVerification') fn=case_run_doesNotMergeAnAttemptWithoutAGreenVerification ;;
    'run#mergesItsCommittedBranchBeforeClosing') fn=case_run_mergesItsCommittedBranchBeforeClosing ;;
    'run#aMergedBranchIsRemoved') fn=case_run_aMergedBranchIsRemoved ;;
    'run#theBaseAdvancesWithEveryMerge') fn=case_run_theBaseAdvancesWithEveryMerge ;;
    'run#staleTestResultsAreNotThisChildsGate') fn=case_run_staleTestResultsAreNotThisChildsGate ;;
    'run#doesNotCloseOnAMergeTheBaseCannotTake') fn=case_run_doesNotCloseOnAMergeTheBaseCannotTake ;;
    'run#recordsAFailedReviewOnTheBranch') fn=case_run_recordsAFailedReviewOnTheBranch ;;
    'run#announcesAndRecordsTheReviewRound') fn=case_run_announcesAndRecordsTheReviewRound ;;
    'run#dirtyKeptWorktreeIsARecordedRefusal') fn=case_run_dirtyKeptWorktreeIsARecordedRefusal ;;
    'run#resetWorktreeDiscardsWhatItNames') fn=case_run_resetWorktreeDiscardsWhatItNames ;;
    'run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds') fn=case_run_aDirtyStartNamesTheItemWhoseWorkTheTreeHolds ;;
    'run#aSkipClearsTheInProgressMark') fn=case_run_aSkipClearsTheInProgressMark ;;
    'run#stateNamesTheItemInProgress') fn=case_run_stateNamesTheItemInProgress ;;
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
    'run#aCorrectiveRoundIsHandedTheCorrections') fn=case_run_aCorrectiveRoundIsHandedTheCorrections ;;
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
    'report#aKilledRunIsNotALiveRun') fn=case_report_aKilledRunIsNotALiveRun ;;
    'report#wallTimeAndReasonInTheTable') fn=case_report_wallTimeAndReasonInTheTable ;;
    'report#zeroUsagePrintsDashNotZero') fn=case_report_zeroUsagePrintsDashNotZero ;;
    'report#actionsNameDecisionAndBranch') fn=case_report_actionsNameDecisionAndBranch ;;
    'report#landedBranchIsNoAction') fn=case_report_landedBranchIsNoAction ;;
    'report#landedBranchesAreNamedAsRemovable') fn=case_report_landedBranchesAreNamedAsRemovable ;;
    'report#emptyRunSaysNone') fn=case_report_emptyRunSaysNone ;;
    'clean#interruptedSuiteRemovesItsScratchTree') fn=case_clean_interruptedSuiteRemovesItsScratchTree ;;
    'clean#reportsWithoutApplyAndRemovesWithIt') fn=case_clean_reportsWithoutApplyAndRemovesWithIt ;;
    'clean#sweepsOnlyWhatIsInTheBase') fn=case_clean_sweepsOnlyWhatIsInTheBase ;;
    'clean#interruptedSuiteEndsItsProcessGroup') fn=case_clean_interruptedSuiteEndsItsProcessGroup ;;
    'clean#reportsAndEndsAProcessLeftInAScratchTree') fn=case_clean_reportsAndEndsAProcessLeftInAScratchTree ;;
    'clean#reportsTheWorktreesScratchTreesToo') fn=case_clean_reportsTheWorktreesScratchTreesToo ;;
    'clean#worktreeDotSweepsTheOneRootOnce') fn=case_clean_worktreeDotSweepsTheOneRootOnce ;;
    'clean#endsARunWhoseTreeIsAlreadyDeleted') fn=case_clean_endsARunWhoseTreeIsAlreadyDeleted ;;
    'clean#suiteThatEndsNormallyEndsTheProcessItsCaseLeft') fn=case_clean_suiteThatEndsNormallyEndsTheProcessItsCaseLeft ;;
    'run#interruptedRunEndsTheChildItStarted') fn=case_run_interruptedRunEndsTheChildItStarted ;;
    'driver#aCaseThatAsksForNoCoreCompilesNothing') fn=case_driver_aCaseThatAsksForNoCoreCompilesNothing ;;
    'driver#aCaseThatAsksForTheCoreGetsItOrRefuses') fn=case_driver_aCaseThatAsksForTheCoreGetsItOrRefuses ;;
    'driver#theCoreIsCompiledOncePerSuite') fn=case_driver_theCoreIsCompiledOncePerSuite ;;
    'driver#aFileFilterRunsOnlyTheCasesTheDeltaOwns') fn=case_driver_aFileFilterRunsOnlyTheCasesTheDeltaOwns ;;
    'driver#aFileSetThatNamesNoCaseIsRefused') fn=case_driver_aFileSetThatNamesNoCaseIsRefused ;;
    'driver#aCoreFileOwesEveryCase') fn=case_driver_aCoreFileOwesEveryCase ;;
    'driver#everyCaseDeclaresTheFilesItCanReach') fn=case_driver_everyCaseDeclaresTheFilesItCanReach ;;
    'driver#aSecondSuiteInOneRepositoryIsRefused') fn=case_driver_aSecondSuiteInOneRepositoryIsRefused ;;
    'gate#theDeltaHandsASuiteTheBaseItMeasured') fn=case_gate_theDeltaHandsASuiteTheBaseItMeasured ;;
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
# A case that starts the engine asks for the core first: the engine writes through `tools/issues/*.sh`, whose Java
# core the driver compiles on request rather than before the case list is known (issue #261).
run_loop() { core_ensure; local p="$1"; shift; ( cd "$p" && bash "$LOOP_ENGINE/run.sh" "$@" ); }

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

# A worker action that differs per *invocation*. The stub is handed one action for every worker process, so a case
# whose second item must not repeat its first — two merges, a stale gate result — needs the tree to carry something
# of its own per item. The action counts its own calls first and the body may name that count as `$n`.
stub_per_item_worker() { # stub_per_item_worker <dir> <worker-report> <reviewer-report> <body>
  local dir="$1"
  printf '%s\n' "$2" > "$dir/worker-report.txt"
  printf '%s\n' "$3" > "$dir/reviewer-report.txt"
  {
    printf 'case "$1" in\n'
    printf '  worker*)\n'
    printf '    n=$(cat "%s" 2>/dev/null || printf 0); n=$((n + 1)); printf "%%s\\n" "$n" > "%s"\n' \
      "$dir/per-item-count" "$dir/per-item-count"
    printf '%s\n' "$4"
    printf '    ;;\n'
    printf 'esac\n'
  } > "$dir/worker-action.sh"
  export PI_STUB_REPORT_WORKER="$dir/worker-report.txt"
  export PI_STUB_REPORT_REVIEWER="$dir/reviewer-report.txt"
  export PI_STUB_ACTION="$dir/worker-action.sh"
  export PI_STUB_LOG="$dir/pi.log"; : > "$PI_STUB_LOG"
  export GH_STUB_RECORD="$dir/gh.log"; : > "$GH_STUB_RECORD"
}

run_one_item() { # run_one_item <project> <dir> [run.sh args…]
  local p="$1" d="$2"; shift 2
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  run_loop "$p" --items 1 --minutes 90 "$@"
}

# ----------------------------------------------------------------------------- cases: state

case_state_initAndShowRoundTrips() {
  # files: tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p block first second status
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  block="$d/fifo"; mkfifo "$block"
  export PI_STUB_WAIT="$block"

  # `--review off`, because PI_STUB_WAIT blocks every child that is started while it is set and this
  # case needs exactly one blocked child (the second run's lock refusal is what is under test).
  core_ensure
  ( cd "$p" && exec bash "$LOOP_ENGINE/run.sh" --items 1 --minutes 90 --review off ) > "$d/first.log" 2>&1 &
  first=$!
  local waited=0
  while [ ! -f "$block.ready" ] && [ "$waited" -lt 300 ]; do sleep 0.1; waited=$((waited + 1)); done
  [ -f "$block.ready" ] || { printf '\n' > "$block"; wait "$first"; fail "the first run never reached its child"; }

  gh_log_content=$(cat "$GH_STUB_RECORD")
  # The second run is bounded: a run that does not refuse must not hang the suite on a child.
  core_ensure
  second=$( ( cd "$p" && timeout 120 bash "$LOOP_ENGINE/run.sh" --items 1 --minutes 90 ) 2>&1); status=$?
  assert_eq "5" "$status" "a second run refuses while the first holds the lock"
  assert_contains "$second" "another run holds" "the refusal names the lock"

  # Nothing was written by the second run: the first run's label bookkeeping is all that is in the log.
  assert_eq "$gh_log_content" "$(cat "$GH_STUB_RECORD")" "the refused run wrote no label"

  printf '\n' > "$block"
  wait "$first"
  unset PI_STUB_WAIT
}

# A run that finds another run's lock is refused — and the refusal has to come *before* the run writes
# anything, so the live run's record is still the live run's. The lock was taken at the end of the
# start-up, so the refused run had already replaced the state, rewritten the queue and merged a branch
# (measured 2026-10-22: the run that started at 19:34 replaced the state of the run that started at 18:46,
# merged that run's unfinished item into main, closed it, and was refused at the lock two minutes into a
# 240-minute budget).
case_state_aRefusedRunLeavesTheLiveStateAlone() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p block first second status live_started live_sum sf
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  block="$d/fifo"; mkfifo "$block"
  export PI_STUB_WAIT="$block"

  core_ensure
  ( cd "$p" && exec bash "$LOOP_ENGINE/run.sh" --items 1 --minutes 90 --review off ) > "$d/first.log" 2>&1 &
  first=$!
  local waited=0
  while [ ! -f "$block.ready" ] && [ "$waited" -lt 300 ]; do sleep 0.1; waited=$((waited + 1)); done
  [ -f "$block.ready" ] || { printf '\n' > "$block"; wait "$first"; fail "the first run never reached its child"; }

  sf=$(state_file "$p")
  [ -f "$sf" ] || fail "the first run wrote no state"
  live_started=$(jq -r '.run.startedAt' "$sf")
  live_sum=$(sha256sum "$sf" | cut -c1-16)

  # The second run is bounded: a run that does not refuse must not hang the suite on a child.
  core_ensure
  second=$( ( cd "$p" && timeout 120 bash "$LOOP_ENGINE/run.sh" --items 1 --minutes 90 ) 2>&1); status=$?
  assert_eq "5" "$status" "a second run refuses while the first holds the lock"
  assert_contains "$second" "another run holds" "the refusal names the lock"

  # The refused run wrote nothing: the live run's own record is the one in place, byte for byte.
  assert_eq "$live_started" "$(jq -r '.run.startedAt' "$sf")" \
    "the refused run left the live run's state in place"
  assert_eq "$live_sum" "$(sha256sum "$sf" | cut -c1-16)" \
    "the refused run wrote nothing into the live run's state"

  printf '\n' > "$block"
  wait "$first"
  unset PI_STUB_WAIT
}

# The lock is a lease, not a tombstone. A run that was killed with a signal no trap can catch leaves its
# lock behind, and a lock whose owner is not running any more must not refuse every run that follows it:
# the next run takes it over and says so (measured 2026-10-22: the run killed at 19:19 left the lock, and
# the run that started at 19:34 was refused by it after having replaced the live run's state).
case_state_aDeadOwnersLockIsTakenOver() {
  # files: tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p lock out status dead
  p=$(new_project)
  init_state "$p" 1 90
  lock="$(dirname "$(state_file "$p")")/lock"
  mkdir -p "$lock"
  # a pid that cannot be running: the ceiling of the pid space
  dead=999999999
  printf '%s\n' "$dead" > "$lock/owner"

  out=$(run_state "$p" lock --owner "$$" 2>&1); status=$?
  assert_eq "0" "$status" "a lock whose owner is gone is taken over"
  assert_contains "$out" "took over" "the takeover is announced"
  assert_contains "$out" "$dead" "the takeover names the process that is gone"
  assert_eq "$$" "$(cat "$lock/owner" 2>/dev/null)" "the lock carries the new owner"

  # Only the holder releases it.
  run_state "$p" unlock --owner "$$" >/dev/null || fail "the unlock failed"
  [ ! -d "$lock" ] || fail "the lock was not released by its holder"
}

# A finished run's counters, verdicts and stop reason live in one file, and `init` writes over it: the file is
# kept first, under the start time it carries itself, so a run that has ended can still be asked what it did
# after the next run has started (issue #207).
case_state_initKeepsTheStateItReplaces() {
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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

# The base a run records is the base *branch*'s commit, not the commit the project tree happens to have checked
# out. A machine somebody works on is on a feature branch, so recording `HEAD` made the run refuse its own first
# item as a stale base — after the whole pre-screen had been paid for (issue #301, measured 2026-10-24: a run on
# `fix/issue-44-…` recorded `a0ca0f35` while `main` was `a0a15fe0`, 12 commits ahead since before the run opened;
# 0 items and 0 steps in 3 minutes of an 8 h budget, with the stop reason claiming `main` had advanced while the
# run was open). Every other reader of the base names the base branch — `run.sh` after a merge, `loop-clean.sh`,
# and `worktree.sh`'s `check_base` that refuses a stale one — so `init` was the only place that read `HEAD`.
case_state_recordsTheBaseBranchNotTheCheckout() {
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
  local p base tip branch out status
  p=$(new_project)
  git -C "$p" commit --allow-empty -qm "a change landed on main" >/dev/null 2>&1 ||
    fail "could not commit on main"
  tip=$(git -C "$p" rev-parse main)
  branch="fix/issue-44-a-feature-branch"
  git -C "$p" switch -c "$branch" >/dev/null 2>&1 || fail "could not create the feature branch"
  git -C "$p" commit --allow-empty -qm "work in progress on the feature branch" >/dev/null 2>&1 ||
    fail "could not commit on the feature branch"
  [ "$(git -C "$p" rev-parse HEAD)" != "$tip" ] || fail "the feature branch is not ahead of the base branch"

  init_state "$p" 1 90
  base=$(run_state "$p" get run.base)
  assert_eq "$tip" "$base" "the base is the base branch's commit, not the checkout's"

  run_worktree "$p" create --branch loop-test >/dev/null || fail "worktree create failed"
  out=$(run_worktree "$p" start-item 21 demo 2>&1); status=$?
  assert_eq "0" "$status" "a checkout on another branch does not refuse the first item: $out"
  assert_not_contains "$out" "base moved" "the run is not refused as a stale base"
  assert_eq "$tip" "$(git -C "$p/.pi/worktrees/loop" rev-parse HEAD)" \
    "the item's tree is cut from the base branch's commit"
}

# The base branch is one of *this project's* values, so it is a `loop.conf` key rather than a literal in the shared
# library: a project whose base branch is not `main` edits the key where it edits its gate command, and an inherited
# `LOOP_BASE_REF` only overrides it, because the configuration is sourced after the environment and fills what the
# environment left unset (issue #303).
case_state_baseBranchIsAConfigurationKey() {
  # files: tools/loop/loop.conf tools/loop/lib.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p other tip env_tip
  [ -n "$(sed -n 's/^LOOP_BASE_REF=//p' "$LOOP_CONF")" ] ||
    fail "loop.conf records no LOOP_BASE_REF — the base branch is then a value of the shared library, and a project whose base branch is named otherwise can change it only through the environment"

  p=$(new_project)
  tip=$(git -C "$p" rev-parse main)
  init_state "$p" 1 90
  assert_eq "$tip" "$(run_state "$p" get run.base)" "the base is the branch the configuration names"

  # An inherited value wins over the recorded one: the key is written as a value that fills only what is unset, and
  # a configuration that assigned the branch outright would silently ignore the caller's environment.
  other="a-base-branch-from-the-environment"
  git -C "$p" switch -q -c "$other" >/dev/null 2>&1 || fail "could not create the environment's branch"
  git -C "$p" commit --allow-empty -qm "a commit on the environment's branch" >/dev/null 2>&1
  env_tip=$(git -C "$p" rev-parse "$other")
  ( export LOOP_BASE_REF="$other"; run_state "$p" init --items 1 --minutes 30 >/dev/null 2>&1 ) ||
    fail "init with an inherited base branch failed"
  assert_eq "$env_tip" "$(run_state "$p" get run.base)" "an inherited LOOP_BASE_REF wins over the recorded key"
}

case_state_defaultsComeFromLoopConf() {
  # files: tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/loop.conf tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf prompt
  d=$(scratch); p=$(new_project)
  conf="$d/loop.conf"
  # The engine's own configuration is what these cases are about, and the caller's `LOOP_CONF` may name another
  # copy of it — the run exports one from the project tree while the engine under test sits in the worktree.
  sed -e 's/^LOOP_AGENT_CMD=.*/LOOP_AGENT_CMD="pi --print"/' \
      -e 's/^LOOP_WORKER_SKILLS=.*/LOOP_WORKER_SKILLS="alpha-skill beta-skill"/' \
      -e 's|^LOOP_GATE_CMD=.*|LOOP_GATE_CMD="my-gate --now"|' \
      -e 's|^LOOP_MEASURE_CMD=.*|LOOP_MEASURE_CMD="my-measure --block"|' "$LOOP_ENGINE/loop.conf" > "$conf"
  export LOOP_CONF="$conf"
  run_one_item "$p" "$d" >/dev/null || fail "the run failed with a substituted configuration"
  prompt="$p/.pi/issue-loop/items/21/worker-prompt.md"
  [ -f "$prompt" ] || fail "the worker prompt was not rendered"
  assert_contains "$(cat "$prompt")" "alpha-skill beta-skill" "the skill list comes from loop.conf"
  assert_contains "$(cat "$prompt")" "my-gate --now" "the gate command comes from loop.conf"
  assert_contains "$(cat "$prompt")" "my-measure --block" "and so does the measuring command"
  assert_not_contains "$(cat "$prompt")" "{{WORKER_SKILLS}}" "every placeholder is substituted"
}

case_state_agentCommandIsTheOnlyIndirection() {
  # files: tools/loop/fixtures/pi tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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

# An item that ends `blocked` records **which mechanism** stopped it — a review round that refused the change,
# or a base the branch could not be merged onto — instead of the single word `blocked` that stood for both
# (issue #280, the decision recorded on it: keep both stop rules, make their spend attributable). Measured over the
# archive on 2026-10-23: the 10 refusing rounds and the 1 unmergeable attempt cost 221.7 M tokens, 19 % of every
# archived run, and the note's prose was the only thing that said which mechanism each one was, so neither a
# report nor a person could total one mechanism's spend without parsing that prose.
case_state_aBlockedItemNamesTheMechanismThatStoppedIt() {
  # files: tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p f out status
  p=$(new_project)
  init_state "$p" 3 90
  run_state "$p" done 21 --result blocked --verdict blocked --stop-reason refused >/dev/null ||
    fail "a blocked item recording a refusing round was refused"
  run_state "$p" done 22 --result blocked --verdict blocked --stop-reason unmergeable >/dev/null ||
    fail "a blocked item recording an unmergeable base was refused"
  run_state "$p" done 23 --result blocked --verdict blocked >/dev/null ||
    fail "a block neither mechanism caused is still a block"
  f=$(state_file "$p")
  assert_eq "refused" "$(jq -r '.items[] | select(.issue == 21) | .stopReason' "$f")" \
    "the refusing round is recorded as its own mechanism"
  assert_eq "unmergeable" "$(jq -r '.items[] | select(.issue == 22) | .stopReason' "$f")" \
    "and a base that refused the merge as the other"
  assert_eq "" "$(jq -r '.items[] | select(.issue == 23) | .stopReason // ""' "$f")" \
    "a block neither mechanism caused carries none"
  out=$(run_state "$p" done 23 --result blocked --verdict blocked --stop-reason abandoned 2>&1); status=$?
  assert_eq "2" "$status" "a mechanism outside the vocabulary is a usage error"
  assert_contains "$out" "refused or unmergeable" "and the refusal names the two the record accepts"
  out=$(run_state "$p" done 23 --result done --verdict closed --stop-reason refused 2>&1); status=$?
  assert_eq "2" "$status" "a stop reason belongs to a block, not to a closure"
}

# A child is started with the run's identity in its environment (`run_child` exports `LOOP_STATE_FILE` and
# `LOOP_WORKTREE` so all of the run's children agree on it), and the gate of any change that touches
# `tools/loop/**` is this very suite. Measured 2026-10-20 (issue #214): a live run's state was replaced five
# times — each time with a fixture's base, queue and budget — while its worker was still working the item, and
# the run then walked the fixture's queue. The rule that stops it is `lib.sh`: a path the environment names
# outside the tree this process resolved is not this tree's run, and is ignored. `run_child` keeps exporting
# them, because a fixture's run is a real run and its stub child reads them too.
case_state_inheritedStatePathOutsideTheTreeIsIgnored() {
  # files: tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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

case_prescreen_ranksBugsFirstAndKeepsFeatures() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario six)
  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed"
  assert_eq "21 22 23 24 25" "$(printf '%s' "$out" | jq -r '[.[] | select(.verdict == "eligible")] | sort_by(.rank) | [.[].issue] | join(" ")')" \
    "three bugs, then the improvement, then the feature"
  assert_eq "5" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 25) | .rank')" "a feature is ranked, not refused"
  assert_eq "eligible" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 25) | .verdict')" "the feature is work"
  assert_eq "in-flight" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 26) | .verdict')" "an in-flight item is dropped"
  assert_contains "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 26) | .reason')" "in-flight" "the in-flight reason"
  assert_eq "5" "$(jq -r '.queue | length' "$(state_file "$p")")" "the queue holds the survivors only"
}

# One harness improvement after every four other items: it is mixed in, never leading, and it does not push an
# app bug back. The fixture has one harness improvement and nine app bugs, so the improvement takes rank 5.
case_prescreen_oneHarnessImprovementPerFourAppItems() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out
  p=$(new_project)
  init_state "$p" 10 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario interleave)
  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed"
  assert_eq "31 32 33 34 30 35 36 37 38 39" \
    "$(printf '%s' "$out" | jq -r '[.[] | select(.verdict == "eligible")] | sort_by(.rank) | [.[].issue] | join(" ")')" \
    "the harness improvement is mixed in after four app items"
  assert_eq "5" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 30) | .rank')" \
    "the harness improvement takes rank 5, not rank 1"
}

# A profile may still refuse a class, and then the pre-screen refuses it exactly as before: the feature path is
# this project's configuration, not an assertion of the engine.
case_prescreen_aRefusedClassStillStopsTheScreen() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario six)
  jq '.roles.refusedClasses = ["feature"]' "$p/tools/tooling.json" > "$p/tools/tooling.json.new" &&
    mv "$p/tools/tooling.json.new" "$p/tools/tooling.json" || fail "could not refuse the feature class"
  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed on a refused class"
  assert_eq "class-feature" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 25) | .verdict')" "the refused class is dropped"
  assert_contains "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 25) | .reason')" "class/feature" "the refusal names the class"
}

case_prescreen_missingPathYieldsStale() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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

# A fix that has to be committed inside the submodule is not work this loop can deliver: the run and
# `worktree.sh end-item` may neither commit nor push there, so the item is a person's patch plus the gitlink bump
# that follows it (issue #254). Measured 2026-10-22: #30, #60 and #122 each spent a worker phase to reach exactly
# that question, and the owner's answer on #60 was a pre-screen rule as the durable guard. The rule reads the
# item's own text — the recorded path, a file under it, or the submodule by name — and nothing fuzzier, because a
# false exclusion costs an item.
case_prescreen_aFixInTheSubmoduleIsAPersonsPatch() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out status reason
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario submodule)
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err"); status=$?
  assert_eq "0" "$status" "the app-side item beside them is still work (stderr: $(tr '\n' ' ' < "$p/prescreen.err"))"
  assert_eq "needs-decision" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 71) | .verdict')" \
    "an item that names the submodule path is its owner's, not the run's"
  reason=$(printf '%s' "$out" | jq -r '.[] | select(.issue == 71) | .reason')
  assert_contains "$reason" "app/src/main/cpp/libosmscout" "the reason names the path the fix has to be committed in"
  assert_contains "$reason" "a person lands the patch" "and the route that remains: a person lands the patch"
  assert_eq "needs-decision" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 72) | .verdict')" \
    "the submodule named without its path is the same question"
  assert_eq "eligible" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 73) | .verdict')" \
    "an item that names neither is queued"
  assert_eq "1" "$(jq -r '.queue | length' "$(state_file "$p")")" "only the app-side item reaches the queue"
}

case_prescreen_doesNotClaimJudgementGates() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  assert_eq "4" "$(jq -r '.queue | length' "$(state_file "$p")")" "the decision is not queued as work"
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/run.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
# category in the classes `firstCategoryClasses` names (here, bugs). A harness improvement is not another
# harness defect: it is mixed in after every `firstCategoryEvery` other item instead of leading.
case_prescreen_harnessItemsLeadTheQueue() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out
  p=$(new_project)
  init_state "$p" 5 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario categories)
  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed"
  assert_eq "22 26 21 28 24 23" \
    "$(printf '%s' "$out" | jq -r '[.[] | select(.verdict == "eligible")] | sort_by(.rank) | [.[].issue] | join(" ")')" \
    "the two harness bugs lead, then the app work, and the harness improvement is mixed in at the end"
  assert_eq "1" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 22) | .rank')" \
    "the harness bug leads the queue"
  assert_eq "2" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 26) | .rank')" \
    "the second harness bug follows by issue number inside the leading group"
  assert_eq "3" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 21) | .rank')" \
    "the app bug follows the harness bugs, because a harness improvement does not lead"
  assert_eq "6" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 23) | .rank')" \
    "the harness improvement is mixed in, not ranked inside the leading group"
}

# A renamed set is ordered on, not merely scanned: the profile declares `ui` as its leading category and the
# fixture's `category/ui` bug leads the queue, above a harness bug — the key is the declared value, not a
# spelling this change happens to ship. Its improvement is mixed in, not led.
case_prescreen_aRenamedLeadingCategoryLeads() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out
  p=$(new_project)
  init_state "$p" 5 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario categories)
  jq '.roles.firstCategories = ["ui"]' "$p/tools/tooling.json" > "$p/tools/tooling.json.new" &&
    mv "$p/tools/tooling.json.new" "$p/tools/tooling.json" || fail "could not rename the leading category"
  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed on a renamed leading set"
  assert_eq "28 21 22 26 23 24" \
    "$(printf '%s' "$out" | jq -r '[.[] | select(.verdict == "eligible")] | sort_by(.rank) | [.[].issue] | join(" ")')" \
    "the declared value's bug leads, the class order decides among the rest, and the ui improvement is mixed in"
  assert_eq "1" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 28) | .rank')" \
    "the ui bug is rank 1 under the renamed set"
}

# The leading categories are the profile's values, read through the roles section like every other decision: an
# empty list is a project that wants no category priority, and an absent key is a half-adapted profile that
# must stop the script rather than silently rank by class.
case_prescreen_priorityCategoriesAreReadFromTheProfile() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out status
  p=$(new_project)
  init_state "$p" 5 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario categories)

  jq '.roles.firstCategories = []' "$p/tools/tooling.json" > "$p/tools/tooling.json.new" &&
    mv "$p/tools/tooling.json.new" "$p/tools/tooling.json" || fail "could not empty the priority list"
  out=$(run_prescreen "$p" --json) || fail "the pre-screen failed on an empty priority list"
  assert_eq "21 22 26 28 23 24" \
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  assert_eq "6" "$(jq -r '.queue | length' "$(state_file "$p")")" \
    "the queue holds the eligible items and not the held one"
}

# Every role the script reads is required, not merely the one this change added: a half-adapted profile that
# omits the hold verdict would otherwise report an empty verdict instead of stopping with a named reason.
case_prescreen_anUndeclaredRoleStopsTheScreen() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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

# The mixing interval is a role too: a profile that omits it is half-adapted and stops, rather than ranking
# silently on a default the engine invented.
case_prescreen_anUndeclaredLeadingIntervalStopsTheScreen() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out status
  p=$(new_project)
  init_state "$p" 1 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario categories)
  jq 'del(.roles.firstCategoryEvery)' "$p/tools/tooling.json" > "$p/tools/tooling.json.new" &&
    mv "$p/tools/tooling.json.new" "$p/tools/tooling.json" || fail "could not remove the mixing interval"
  out=$(run_prescreen "$p" --json 2>&1); status=$?
  assert_eq "2" "$status" "an undeclared interval stops the pre-screen"
  assert_contains "$out" "does not declare firstCategoryEvery" "and the refusal names the key"
}

# ----------------------------------------------------------------------------- cases: worktree

# The throwaway repository has no submodule and no vendored dependency, so the seed's submodule step is a
# no-op there (it is exercised only against this repository); what the case asserts is the state after
# seeding — the directory populated, the local properties and the gitignored skills present, none of
# which a fresh worktree inherits.
case_worktree_seedsAllFourInputs() {
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # The branch the run committed on is gone once the run merged it — the work is in the base — and the kept
  # worktree still holds the commit it carried.
  [ -z "$(git -C "$p" branch --list "$branch")" ] || fail "the run's branch $branch is still there"
  assert_eq "$(git -C "$p" rev-parse main)" "$(git -C "$w" rev-parse HEAD)" \
    "the kept worktree still holds the item's commit"
  bytes=$(printf '%s\n' "$out" | sed -n 's/.*removed from the kept worktree: \([0-9][0-9]*\) B.*/\1/p' | head -1)
  [ -n "$bytes" ] || fail "the run does not say how much build output it removed: $out"
  [ "$bytes" -ge 102400 ] || fail "the run reports $bytes B removed, fewer than the 102400 B the child built"
}

case_worktree_noPushWithoutTheFlag() {
  # files: tools/loop/ tools/pi-child-events.sh tools/tooling.json
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

# The push runs after the item is done, and a closed item's branch is gone by then: the run merged it and the
# release deleted it. `git push origin fix/issue-N-…` with no such local ref fails with `src refspec … does not
# match any`, so a run asked to publish printed git's two error lines for the branch it had removed itself
# (measured 2026-10-23 on #277, in a run that read green everywhere else). The flag publishes what still exists;
# a merged and released branch is a named skip.
case_worktree_pushSkipsABranchItAlreadyMerged() {
  # files: tools/loop/worktree.sh tools/loop/lib.sh tools/loop/fixtures/scenarios/ tools/tooling.json tools/pi-child-events.sh
  local d p origin out branch
  d=$(scratch); p=$(new_project); origin="$d/origin.git"
  # A remote that answers: without one, a push of a branch that *does* exist would fail as well, and the case
  # could not tell the skip from a remote that is not there.
  git init -q --bare "$origin" >/dev/null 2>&1 || fail "could not create the remote"
  git -C "$p" remote add origin "$origin" >/dev/null 2>&1 || fail "could not add the remote"
  out="$d/run.txt"
  run_one_item "$p" "$d" --review on --push yes >"$out" 2>&1 || fail "the run itself failed: $(cat "$out")"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" "the reviewed item is closed"
  branch=$(jq -r '.items[0].merged.branch' "$(state_file "$p")")
  assert_contains "$(cat "$out")" "nothing to publish for $branch" \
    "a branch the run merged and released is a named skip, not git's refspec error"
  assert_not_contains "$(cat "$out")" "does not match any" "the run never hands git a refspec it deleted"
  assert_eq "" "$(git -C "$origin" for-each-ref "refs/heads/$branch")" \
    "the remote carries no branch of a closed item"
}

case_worktree_refusesABranchWhoseReviewFailed() {
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
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

# A round the reviewer *never answered* looks exactly like a round that passed, from outside: the run writes a
# verdict on the branch only when the reviewer refuses, so a reviewer killed mid-answer leaves no trace at all.
# Measured 2026-10-22: the round-2 reviewer of item #249 was killed, its report was 0 bytes, the next run read the
# branch as a finished attempt, merged it into `main` and closed the item — although no reviewer had graded the
# change (issue #262). The round is therefore announced on the branch *before* the reviewer starts, so an answer
# that never arrives is a fact a later run reads: the branch is not adopted, and its silence is the owner's.
case_worktree_refusesABranchWhoseReviewRoundNeverAnswered() {
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
  local p out status branch state
  p=$(new_project)
  init_state "$p" 1 90
  branch="fix/issue-24-an-unanswered-round"
  git -C "$p" switch -c "$branch" >/dev/null 2>&1 || fail "could not create the unanswered attempt's branch"
  mkdir -p "$p/openspec/changes/archive/2026-01-01-fix-unanswered/evidence"
  printf 'archived\n' > "$p/openspec/changes/archive/2026-01-01-fix-unanswered/proposal.md"
  printf '<testsuite tests="2" failures="0" errors="0"></testsuite>\n' \
    > "$p/openspec/changes/archive/2026-01-01-fix-unanswered/evidence/TEST-unanswered.xml"
  # Round 1 answered, round 2 was announced and its reviewer never wrote a word: a previous answer does not
  # license the round after it, which is exactly the shape #249's branch had.
  printf 'The reviewer passed this change in review round 1.\n' \
    > "$p/openspec/changes/archive/2026-01-01-fix-unanswered/evidence/review-round-1-passed.md"
  printf 'Review round 2 of this change was announced.\n' \
    > "$p/openspec/changes/archive/2026-01-01-fix-unanswered/evidence/review-round-2-requested.md"
  printf 'the change nobody graded\n' > "$p/unanswered-fix.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "fix: the unanswered change (#24)" >/dev/null 2>&1 ||
    fail "could not commit the unanswered change"
  git -C "$p" switch -q main >/dev/null 2>&1 || fail "could not return to the base"

  state=$(run_worktree "$p" branch-state 24 "$branch" 2>&1 | cut -f1)
  assert_eq "review-unanswered" "$state" "the branch states that its last review round never answered"
  out=$(run_worktree "$p" adopt-item 24 "$branch" 2>&1); status=$?
  assert_eq "5" "$status" "a branch whose last review round never answered is not adopted"
  assert_contains "$out" "never answered" "the refusal says why"
  assert_contains "$out" "round 2" "and names the round that stayed silent"
  [ ! -f "$p/unanswered-fix.txt" ] || fail "the unanswered change was merged onto the base anyway"
}


case_labels_verdictToLabelMatrix() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/labels.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/labels.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  core_ensure
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
  # files: tools/loop/ tools/pi-child-events.sh tools/tooling.json
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
  core_ensure
  out=$( cd "$p" && bash "$LOOP_ISSUES_ENGINE/update.sh" 21 --remove-label agent/eligible 2>&1 ); status=$?
  assert_eq "3" "$status" "the tooling refuses to remove the reserved label"
  assert_contains "$out" "label-reserved" "with the reserved-label reason"
}

case_labels_writesOnlyBetweenChildren() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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

# A feature is work too, and its prompt says how it enters: the run hands the item's class to the child, the
# child plans `add-<slug>` before production code, and the acceptance scenario is its red case.
case_prompt_workerCarriesTheFeaturePath() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p prompt
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompt="$p/.pi/issue-loop/items/21/worker-prompt.md"
  [ -f "$prompt" ] || fail "the worker prompt was not rendered"
  assert_contains "$(cat "$prompt")" "class: **bug**" "the run hands the item's class to the child"
  assert_contains "$(cat "$prompt")" "propose-first" "the feature path is named"
  assert_contains "$(cat "$prompt")" "add-<slug>" "and its change name"
  assert_contains "$(cat "$prompt")" "acceptance scenario" "and the red case a feature owes"
}

case_prompt_workerNamesTheDecisionField() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p prompt
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompt="$p/.pi/issue-loop/items/21/reviewer-prompt-1.md"
  [ -f "$prompt" ] || fail "the reviewer prompt was not rendered"
  local item
  for item in '1. **Scenario coverage**' '2. **Cause or gap scope**' '3. **Gate reality**' '4. **Tasks claiming done**' \
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

# Issue #236: the reviewer's task said what to judge and nothing about what it may run, so the natural-looking
# first move — "run the tests" — is the expensive one. The #232 reviewer held one suite open for 4 min 1 s of a
# 4 h 40 m change and the parent had to steer it back to reading. The case asserts the bound the task states,
# because "read-only" alone was not enough to stop it: the gate and the suite have to be named.
case_prompt_reviewerIsBoundedToReading() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf prompt text cap
  d=$(scratch); p=$(new_project)
  # The bound in the text is the run's own recorded value, so the case writes a value of its own: a template
  # that hardcoded today's 2700 s default would satisfy an assertion read from `loop.conf` and still be wrong.
  conf="$d/loop.conf"
  sed -e 's/^LOOP_CHILD_MAX_SECONDS=.*/LOOP_CHILD_MAX_SECONDS=8100/' "$LOOP_CONF" > "$conf" ||
    fail "could not write the loop configuration"
  grep -qx 'LOOP_CHILD_MAX_SECONDS=8100' "$conf" ||
    fail "this case could not set the child cap in loop.conf"
  export LOOP_CONF="$conf"
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompt="$p/.pi/issue-loop/items/21/reviewer-prompt-1.md"
  [ -f "$prompt" ] || fail "the reviewer prompt was not rendered"
  text=$(cat "$prompt")
  assert_contains "$text" "Your inputs are" "the reviewer is told what it is given"
  assert_contains "$text" "do not run the gate" "the read-only list names the gate it must not run"
  assert_contains "$text" "do not run a test suite" "and the suite beside it"
  assert_contains "$text" "do not use the network" "and the network"
  assert_contains "$text" "quoted from the evidence" "a green gate is quoted from the evidence, not reproduced"
  # Item 3 is the line that invited the suite run (issue #236): it asks for the tally the evidence already
  # carries, not for a second execution of it.
  assert_contains "$text" "never reproduce it by running the gate" "item 3 asks for the tally in the evidence, not a re-run"
  assert_contains "$text" "reading task" "a diff review is stated to be a reading task"
  # the bound is the number the run itself enforces, so the two cannot drift
  cap=8100
  assert_contains "$text" "$cap" "the wall-clock bound named in the task is the run's own ($cap s)"
}

# The same bound reaches a session that spawns its own reviewer (issue #236): the loop's prompt is the one
# skeleton, and the iteration protocol is where a session is told to hand it over. Two surfaces, one rule.
case_prompt_iterationProtocolHandsTheReviewerTheSameSkeleton() {
  # files: tools/loop/prompts/reviewer.md
  local doc
  doc=$(cat "$LOOP_PROJECT_ROOT/guidelines/Build.md")
  # the document is quoted whole, so a `case` is used instead of `assert_contains`: the failure names the rule
  # that is missing rather than dumping 131 KB, and a `printf | grep -q` would fail on its own SIGPIPE under the
  # suite's `pipefail`.
  case "$doc" in
    *tools/loop/prompts/reviewer.md*) ;;
    *) fail "the iteration protocol does not name the skeleton a session-spawned reviewer is handed" ;;
  esac
  case "$doc" in
    *'runs no gate'*) ;;
    *) fail "the iteration protocol does not state the bound that skeleton carries" ;;
  esac
}

# Issue #238: the #232 change was planned as "one attribution fix plus one verdict split" — two functions in
# `tools/loop/worktree.sh` — and landed 292 insertions over six files, ~60 % of it test surface: two cases
# added, two rewritten, one renamed and four fixtures re-aligned. Nobody asked how many places asserted the
# rule before the plan was fixed, and the answer was one `grep -l` away. The protocol measured the symptom and
# the iteration levers, never the surface of the change itself, so this case holds the rule to the two surfaces
# it needs: the worker prompt a run renders, and the guideline that owns the text.
case_prompt_workerNamesTheChangeSurfaceBeforeThePlan() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p prompt text
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompt="$p/.pi/issue-loop/items/21/worker-prompt.md"
  [ -f "$prompt" ] || fail "the worker prompt was not rendered"
  text=$(cat "$prompt")
  assert_contains "$text" 'Count the surface before the plan is fixed' "the worker counts the surface before it plans"
  assert_contains "$text" 'grep -l' "and is handed the command that counts it"
  assert_contains "$text" 'surface:' "and the record its proposal carries"
}

# The rule has one home for its text (`guidelines/Build.md` §2, the iteration protocol) and one for the act (the
# worker prompt above). A session that plans a change outside the loop reads the first, so both are asserted.
case_prompt_iterationProtocolNamesTheChangeSurface() {
  # files: *
  local doc
  doc=$(cat "$LOOP_PROJECT_ROOT/guidelines/Build.md")
  # quoted whole, so a `case` is used instead of `assert_contains`: the failure names the rule that is missing
  # rather than dumping 131 KB.
  case "$doc" in
    *'count the surface'*) ;;
    *) fail "the iteration protocol does not state the surface measurement" ;;
  esac
  case "$doc" in
    *'surface:'*) ;;
    *) fail "the iteration protocol does not state the record the change's proposal carries" ;;
  esac
}

case_prompt_packageIsAFileAndNotInlined() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  core_ensure
  expected=$(printf '%s' "$issue_json" |
    java -cp "$ISSUES_CORE_CLASSES" WorkPackage --profile "$p/tools/tooling.json" --issue - \
      --fingerprint "$(printf '%s' "$issue_json" | jq -r '.body // ""' | sha256sum | cut -c1-16)" \
      --provenance "issue #21")
  assert_eq "$expected" "$(cat "$package")" "the package is exactly the core's rendering"
}

case_prompt_inlineInjectionCannotReachTheInstructionPosition() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/labels.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/issues/fixtures/scenarios/outsider/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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

# A run whose project tree is not checked out on the base branch cannot land anything: the landing step merges
# into that tree (`worktree.sh` merge-item, and adopt-item from the pre-screen), and nothing there verifies it. The
# run is refused before the pre-screen is paid for, and the reason and the remedy are on the record — the two
# alternatives were measured: refusing at the merge spends an item's whole budget, and a checkout that merely
# fast-forwards to the item's branch closes the item with its fix never on the base (issue #301's review,
# 2026-10-24).
case_run_aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen() {
  # files: tools/loop/run.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local d p out status f reason
  d=$(scratch); p=$(new_project)
  git -C "$p" switch -c fix/issue-44-a-feature-branch >/dev/null 2>&1 ||
    fail "could not create the feature branch"
  stub_success "$d"

  out=$(run_loop "$p" --items 1 --minutes 90 2>&1); status=$?
  f=$(state_file "$p")
  assert_eq "5" "$status" "a run whose checkout is not the base branch is refused"
  assert_contains "$out" "fix/issue-44-a-feature-branch" "the refusal names the branch the tree is on"
  assert_contains "$out" "git -C $p switch main" "and the remedy, as a command"
  reason=$(jq -r '.run.stop.reason // ""' "$f")
  assert_contains "$reason" "not the base branch main" "the reason is recorded, so the report can say why"
  assert_eq "0" "$(jq -r '.queue | length' "$f")" "the pre-screen was not paid for"
  assert_eq "0" "$(jq -r '.items | length' "$f")" "and no item was started"
  assert_eq "" "$(cat "$PI_STUB_LOG" 2>/dev/null || true)" "no agent process was started"
  return 0
}

case_run_decisionCapRefusesToStart() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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

# A repository whose `core.bare` is true is not a work tree. `git status` still answers, so the run looks healthy
# and lands nothing: every merge, fast-forward and commit in it is refused, and the run reports a merge it could
# not take (issue #241, measured 2026-10-21: five refused fast-forwards in the project tree, `core.bare` → `true`,
# `rev-parse --is-inside-work-tree` → `false`). The run refuses before it writes, and says what repairs it.
case_run_refusesARepositoryThatIsNotAWorkTree() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p out status
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  git -C "$p" config core.bare true || fail "this case could not put the fixture's repository into the state it tests"
  out=$(run_loop "$p" --items 1 --minutes 90 2>&1); status=$?
  git -C "$p" config core.bare false
  assert_eq "5" "$status" "a repository that is not a work tree refuses the run: $out"
  assert_contains "$out" "core.bare=true" "the refusal names the setting behind it"
  assert_contains "$out" "git config core.bare false" "and the one command that repairs it"
  [ ! -d "$p/.pi/worktrees/loop" ] || fail "a worktree was created although the run refused"
  assert_eq "" "$(cat "$PI_STUB_LOG" 2>/dev/null || true)" "no agent process was started"
}

# The gate a delta owes is the one its pathspec asks for (issue #242). A change that only touches a note runs the
# check that reads notes; a change that touches a compiled path runs the build's gate. The profile owns the
# mapping — `LOOP_GATE_COMPILED_PATTERNS` and `LOOP_GATE_SUITES` in `loop.conf` — and the engine names neither a
# project path nor a suite of its own.
case_gate_theDeltaSelectsTheSuitesItTouches() {
  # files: tools/loop/gate.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf plan base
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_CONF" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_COMPILED_CMD=compiled-gate\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS="*.kt *.cpp"\n'
    printf 'LOOP_GATE_SUITES="notes/*|notes-check;tools/*|tools-check"\n'
  } >> "$conf"

  mkdir -p "$p/notes" && printf 'a note\n' > "$p/notes/a.md"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'a note' >/dev/null 2>&1
  base=$(git -C "$p" rev-parse HEAD~1)
  plan=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )
  assert_contains "$plan" "notes-check" "a note runs the check that reads notes: $plan"
  assert_not_contains "$plan" "compiled-gate" "and asks nothing of the build"

  printf 'fun main() {}\n' > "$p/Main.kt"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'a compiled change' >/dev/null 2>&1
  base=$(git -C "$p" rev-parse HEAD~1)
  plan=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )
  assert_contains "$plan" "compiled-gate" "a compiled path runs the build's gate: $plan"

  # …and a delta the profile gates nothing for says so, instead of reporting an empty gate as a pass.
  base=$(git -C "$p" rev-parse HEAD)
  plan=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" 2>&1 )
  assert_contains "$plan" "nothing owed" "a delta no pattern covers owes nothing: $plan"
}

# The profile's suite map is a *copy* of the shell commands the pipeline's checks run, and a copy nobody
# compares reddens `main` again the next time the pipeline grows a step (issue #242). On 2026-10-21 the
# pipeline ran thirty-one such commands and the map reached ten of them, so a landing the pipeline refused went
# green in the run — a merge passed the build's gate and broke `tools/check-portability-selftest.sh`, and the
# pipeline found the red `main` instead. The profile names the pipeline it is pinned to, and every `bash`/`sh`
# command under a `Check …` step has to be a value of the map. The pin closes the *command* side only: which
# *paths* a step reads is derivable from no workflow, so a path a check reads but names is a residual the map
# cannot close, and the comment in `loop.conf` states it. A profile that names no pipeline cannot be pinned and
# says so — a new step escapes until someone adds it, the cost the map's owner accepted over running every step
# for every item.
case_gate_theMapCoversEveryPipelineStep() {
  # files: tools/loop/loop.conf .github/workflows/build.yml
  local conf pipeline cmds map missing
  conf="$LOOP_ENGINE/loop.conf"
  # The key is recorded even when it is empty, like `LOOP_GATE_CMD`: a profile that names no pipeline has to say
  # so, or "the map is a copy with no alarm" is a state nobody reading the file can see.
  grep -q '^LOOP_GATE_PIPELINE=' "$conf" \
    || fail "the profile records no LOOP_GATE_PIPELINE, so the map is pinned to nothing and nobody can tell"
  pipeline=$(sed -n 's/^LOOP_GATE_PIPELINE="\(.*\)"/\1/p' "$conf")
  if [ -z "$pipeline" ]; then
    # A project with no pipeline (or one that chose not to pin its map) has nothing to compare against.
    return 0
  fi
  [ -f "$LOOP_PROJECT_ROOT/$pipeline" ] || fail "the profile pins the map to $pipeline, which the project does not carry"
  # The commands a check step runs: a step named `Check …` is a check (a publish step writes the tree and is not
  # one), and its commands are the `bash <path> …` lines it runs — inline after `run:` or one per line inside a
  # `run: |` block. A trailing `|| { … }` is the workflow's own error handling, not part of the command.
  cmds=$(awk '
    /^[[:space:]]*- name:/ { name=$0; sub(/^[[:space:]]*- name:[[:space:]]*/, "", name); incheck=(name ~ /^Check /); next }
    incheck {
      line=$0
      if (line ~ /^[[:space:]]*run:[[:space:]]*(bash|sh)[[:space:]]/) {
        sub(/^[[:space:]]*run:[[:space:]]*/, "", line); print line
      } else if (line ~ /^[[:space:]]*(bash|sh)[[:space:]]/) {
        sub(/^[[:space:]]*/, "", line); sub(/[[:space:]]*[|&;].*$/, "", line); print line
      }
    }' "$LOOP_PROJECT_ROOT/$pipeline" \
    | sed -e 's/^bash //' -e 's/^sh //' -e '/^$/d' | sort -u)
  [ -n "$cmds" ] || fail "the pipeline at $pipeline names no shell gate command: the pin would pass over nothing"
  # The gate's own `--from-git {base}` narrowing is not part of the pipeline command, so the pin strips it before
  # comparing: the map names the command the pipeline runs, with the gate's filter appended.
  map=$(sed -n 's/^LOOP_GATE_SUITES="\(.*\)"/\1/p' "$conf" | tr ';' '\n' \
    | sed -e 's/^[^|]*|//' -e 's/^bash //' -e 's/^sh //' -e 's/ --from-git {base}$//' -e '/^$/d' | sort -u)
  [ -n "$map" ] || fail "the profile's suite map is empty"
  missing=$(comm -13 <(printf '%s\n' "$map") <(printf '%s\n' "$cmds"))
  [ -z "$missing" ] || fail "the suite map carries none of these commands the pipeline's Check steps run: $(printf '%s' "$missing" | tr '\n' ' ')"
}

# The map is worth its lines only if the gate reads it: a delta touching a path the pipeline's checks gate owes
# that step's commands. Before the map was completed this path owed *nothing* and the gate exited 0 — the shape
# of the 2026-10-21 landing that reddened `main` (issue #242).
case_gate_aPipelineCheckIsOwedByItsPaths() {
  # files: tools/loop/loop.conf tools/loop/gate.sh tools/build-inputs.sh tools/build-inputs-selftest.sh tools/build-inputs-mutations.sh tools/check-portability.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf base plan
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  mkdir -p "$p/tools"
  printf '#!/usr/bin/env bash\nexit 0\n' > "$p/tools/build-inputs.sh"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the build inputs' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  plan=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )
  # Every command the step runs, not the two that happen to be first in the map: the pipeline runs the step as a
  # unit, and any of its commands can be the one that reddens (issue #242, review round 1).
  assert_contains "$plan" "tools/build-inputs.sh --check" "the check the pipeline runs over that path is owed: $plan"
  assert_contains "$plan" "tools/build-inputs-selftest.sh" "and the suite that proves it: $plan"
  assert_contains "$plan" "tools/build-inputs-mutations.sh --list" "and the mutation set the same step runs: $plan"
  # …and nothing from another step: a glob as coarse as `tools/*` would satisfy the three assertions above while
  # making the map claim every step for every path, which is the decision's rejected option 2.
  assert_not_contains "$plan" "tools/check-portability.sh" "and names no command of another step: $plan"
  return 0
}

# The gate's log moves while a suite runs (issue #269). A suite's output was captured into a variable
# (`out=$($cmd 2>&1)`) and replayed only after the command exited, so a suite that ran for a quarter of an hour
# left the gate's console at two lines for the whole of its wall clock: an operator could not tell a long suite
# from a stopped one, and the failure text of a red one survived only as the last three lines of a tally. The
# stream is `tee`d, so the console and the tally read the one observation.
case_gate_theSuitesOutputReachesTheConsoleWhileItRuns() {
  # files: tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local d p conf base log pid out
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_COMPILED_CMD=""\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS=""\n'
    printf 'LOOP_GATE_SUITES="notes/*|bash %s/suite.sh"\n' "$d"
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
  } >> "$conf"
  # A suite that prints, then works, then prints its summary: the first line is on the console while the suite
  # is still running only if the output is streamed rather than held in a variable.
  cat > "$d/suite.sh" <<'SUITE'
printf 'case: a note is read\n'
printf 'progress: the suite is still working\n' >&2
sleep 3
printf 'notes: all 1 cases passed\n'
SUITE
  # A path the profile's suite map gates, so the gate owes this suite and nothing else.
  mkdir -p "$p/notes"
  printf 'a note\n' > "$p/notes/a.md"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the change' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  log="$d/gate.log"
  ( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" ) > "$log" 2>&1 &
  pid=$!
  sleep 1.5
  out=$(cat "$log")
  assert_contains "$out" "gate: bash" "the suite is announced before it runs"
  assert_contains "$out" "case: a note is read" \
    "the suite's own first line reaches the console while the suite runs: $out"
  assert_not_contains "$out" "notes: all 1 cases passed" \
    "and the summary it has not printed yet does not: the output is streamed, not replayed"
  wait "$pid" || fail "the gate failed"
  out=$(cat "$log")
  assert_contains "$out" "progress: the suite is still working" \
    "the rest of the stream reaches the console as well, its stderr included"
  assert_contains "$out" "LOOP-GATE: tests=1 failures=0" "and the gate's tally is read from the same run"
  assert_contains "$(cat "$d"/tally/*.xml 2>/dev/null)" 'tests="1" failures="0"' \
    "the tally the run's own check reads carries that suite's numbers"
  return 0
}

# The tally is derived from the streamed output and not from a second observation of it (issue #269): the numbers
# the run's check reads and the lines an operator saw are the same bytes. A failing suite keeps the gate's exit
# status and its own failing line, so the verdict is still the suite's and the failure text is not lost.
case_gate_aFailingSuiteIsReadFromTheSameStream() {
  # files: tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local d p conf base out rc
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_COMPILED_CMD=""\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS=""\n'
    printf 'LOOP_GATE_SUITES="notes/*|bash %s/suite.sh"\n' "$d"
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
  } >> "$conf"
  cat > "$d/suite.sh" <<'SUITE'
printf 'FAIL notes#aCaseThatFails — the reason it failed\n'
printf 'notes: 2 of 3 cases failed\n'
exit 1
SUITE
  mkdir -p "$p/notes"
  printf 'a note\n' > "$p/notes/a.md"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the change' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  out=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" 2>&1 )
  rc=$?
  assert_eq "1" "$rc" "a failing suite still fails the gate: $out"
  assert_contains "$out" "FAIL notes#aCaseThatFails — the reason it failed" \
    "the failing case's own line is on the console, which is where an operator reads the failure"
  assert_contains "$out" "LOOP-GATE: tests=3 failures=2" "the verdict carries the summary's numbers: $out"
  assert_contains "$(cat "$d"/tally/*.xml 2>/dev/null)" 'tests="3" failures="2"' \
    "and the tally is the summary the same stream carried, not a re-run's"
  assert_contains "$(cat "$d"/tally/*.xml 2>/dev/null)" "FAIL notes#aCaseThatFails" \
    "with the failure text kept, so the evidence names what went red"
  assert_contains "$(cat "$d"/tally/*.xml 2>/dev/null)" 'errors="0"' \
    "and a suite that reported its own failure count is not also an error"
  return 0
}

# A suite that reported nothing is recorded as an error, never as one failing test (issue #268). The gate's tally
# said `tests="1" failures="1"` for a compiled step that died at configuration (`BUILD FAILED in 21s`, no test task
# reached its action), because `n=1` and `f=1` existed so a failing suite could not contribute zero — a number the
# suite never reported, so a tally meant to prove what ran became evidence that something ran. The two facts are
# kept apart: the case and failure counts are the suite's own, and a suite that failed without a summary carries
# `tests="0"` with `errors="1"`.
case_gate_aFailingSuiteWithNoSummaryInventsNoTest() {
  # files: tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local d p conf base out rc
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_COMPILED_CMD=""\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS=""\n'
    printf 'LOOP_GATE_SUITES="notes/*|bash %s/suite.sh"\n' "$d"
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
  } >> "$conf"
  # A suite that dies before a single case can run: it writes no summary line at all.
  cat > "$d/suite.sh" <<'SUITE'
printf 'Could not create task :app:mergeNativeSbom\n' >&2
printf 'BUILD FAILED in 21s\n' >&2
exit 1
SUITE
  mkdir -p "$p/notes"
  printf 'a note\n' > "$p/notes/a.md"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the change' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  out=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" 2>&1 )
  rc=$?
  assert_eq "1" "$rc" "a suite that reported nothing still fails the gate: $out"
  assert_contains "$out" "LOOP-GATE: tests=0 failures=0 errors=1" \
    "the gate's line states the suite reported no case, and one error: $out"
  assert_contains "$out" "failing: bash $d/suite.sh" \
    "and it is still named as the suite that failed: $out"
  assert_contains "$(cat "$d"/tally/*.xml 2>/dev/null)" 'tests="0" failures="0" errors="1"' \
    "the tally the run's own check reads invents no test: $(cat "$d"/tally/*.xml 2>/dev/null)"
  assert_not_contains "$(cat "$d"/tally/*.xml 2>/dev/null)" 'tests="1"' \
    "no phantom case is recorded where none executed"
  return 0
}

# The tally of the profile's own compiled command is a dotfile (issue #278): a tally's name is the command
# sanitised, and `./gradlew test -PforceTests --no-build-cache` keeps its leading `.`, so the one tally that
# records the build was `._gradlew_test_-PforceTests_--no-build-cache.xml` — a plain `ls` printed five files where
# the directory held six, and `tally/*.xml` missed it. The round-3 reviewer of #273 read that copy as short and
# refused a round on a tally that was present. The `./` prefix is stripped before the name is built, so every
# reader that lists or globs finds the file.
case_gate_aCompiledStepsTallyIsNotHidden() {
  # files: tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local d p conf base out visible f globbed
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_COMPILED_CMD="./gradlew test"\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS="*.kt"\n'
    printf 'LOOP_GATE_SUITES=""\n'
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
  } >> "$conf"
  # The command the profile names runs from the project root, which is where `./` resolves: a stub of that shape,
  # so the case reads the name the gate builds and nothing about Gradle.
  cat > "$p/gradlew" <<'GRADLEW'
printf 'notes: all 1 cases passed\n'
GRADLEW
  chmod +x "$p/gradlew"
  printf 'class A\n' > "$p/a.kt"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the change' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  out=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" 2>&1 ) \
    || fail "the gate failed: $out"
  # A reader that lists the directory without `-A`: a dotfile is the one tally a plain `ls` does not print.
  visible=$(ls -1 "$d/tally" 2>/dev/null | grep -c . || true)
  assert_eq "1" "$visible" \
    "a plain ls prints the compiled step's tally (the directory holds: $(ls -A "$d/tally" 2>/dev/null | tr '\n' ' '))"
  # …and the shell glob the round's evidence was read with finds the same file, under the command's own name.
  globbed=""
  for f in "$d"/tally/*.xml; do [ -f "$f" ] && globbed="$globbed$(basename "$f") "; done
  assert_eq "gradlew_test.xml " "$globbed" \
    "a *.xml glob finds the tally of a ./ command, named after the command without its ./ prefix (the directory holds: $(ls -A "$d/tally" 2>/dev/null | tr '\n' ' '))"
  return 0
}

# The gate reports what an iteration cost (this change): a suite's wall clock used to be invisible, so "the
# self-test costs twelve minutes" could only be established with a stopwatch and a `ps` over a running process,
# and an iteration that has to be made cheaper could not say which suite it was paying for. Each suite's seconds
# are printed beside its own command, carried in the summary the run records as its gate claim, and written into
# the tally the run's own check reads.
case_gate_theSummaryNamesWhatEachSuiteCost() {
  # files: tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local d p conf base out tally seconds line_seconds
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_COMPILED_CMD=""\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS=""\n'
    printf 'LOOP_GATE_SUITES="notes/*|bash %s/suite.sh"\n' "$d"
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
  } >> "$conf"
  # A suite whose own wall clock is known to within a second: the gate's number has to carry it, and the number
  # is read out of the run rather than stated in the case — a hard-coded `2s` would pass on a suite that never
  # slept, which is the one thing this case must not do.
  cat > "$d/suite.sh" <<'SUITE'
sleep 2
printf 'notes: all 1 cases passed\n'
SUITE
  mkdir -p "$p/notes"
  printf 'a note\n' > "$p/notes/a.md"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the change' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  out=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" 2>&1 ) \
    || fail "the gate failed: $out"
  assert_matches "$out" "gate: bash $d/suite\.sh — [0-9]+s" \
    "the suite's own line names how long it ran: $out"
  seconds=$(printf '%s\n' "$out" | sed -n 's/.*suite\.sh \([0-9][0-9]*\)s).*/\1/p' | tail -1)
  [ -n "$seconds" ] || fail "the gate's summary carries no duration beside the command: $out"
  [ "$seconds" -ge 2 ] || fail "the duration is below the suite's own sleep: ${seconds}s"
  # All three readers carry the same number, and the suite's own line is read out of the run rather than matched
  # by shape: a line that says the right thing about a wrong number would otherwise pass.
  line_seconds=$(printf '%s\n' "$out" \
    | sed -n "s|^gate: bash $d/suite\\.sh — \([0-9][0-9]*\)s\$|\\1|p" | tail -1)
  assert_eq "$seconds" "$line_seconds" \
    "the suite's own line carries the same number as the gate's summary: $out"
  tally=$(cat "$d"/tally/*.xml 2>/dev/null)
  assert_contains "$tally" "time=\"$seconds\"" \
    "and the tally the run's own check reads carries the same number: $tally"
  return 0
}

# One call answers what the worker used to spend a run of calls finding out (issue #246): the change's pathspec,
# the checks that pathspec owes, and the tallies the tree already carries. The block is what the item's bound
# asks for — one call per measurement, not one call per file.
case_measure_namesTheOwedChecksInOneBlock() {
  # files: tools/loop/ tools/pi-child-events.sh tools/tooling.json
  local d p conf base out canary
  d=$(scratch); p=$(new_project)
  conf="$d/measure.conf"
  # The engine's own configuration, not the copy the caller's environment may name: the run exports
  # `LOOP_CONF` from the project tree while the engine under test sits in the worktree.
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  canary="$d/ran"
  {
    printf 'LOOP_GATE_COMPILED_CMD=compiled-gate\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS="*.kt"\n'
    printf 'LOOP_GATE_SUITES="notes/*|bash %s/notes-check;tools/*|tools-check"\n' "$d"
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
  } >> "$conf"
  # A suite that would leave a mark if anything ran it: the block names the check, it does not execute it.
  printf '#!/usr/bin/env bash\n: > %s\nprintf "notes: all 1 cases passed\\n"\n' "$canary" > "$d/notes-check"
  chmod +x "$d/notes-check"

  mkdir -p "$p/notes" "$p/tools/loop"
  printf 'a note\n' > "$p/notes/a.md"
  printf '# a tool\n' > "$p/tools/loop/tool.sh"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the change' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  out=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/measure.sh" --base "$base" 2>&1 )
  assert_eq "0" "$?" "the measuring command succeeded: $out"
  # one block: every line it prints is part of it, so a stray command's output would be visible as one
  assert_eq "0" "$(printf '%s\n' "$out" | grep -vc '^LOOP-MEASURE' || true)" "the block is the whole output: $out"
  assert_contains "$out" "notes-check" "the check the notes delta owes is named"
  assert_contains "$out" "tools-check" "the check the tools delta owes is named"
  assert_contains "$out" "notes/a.md" "and the path that owes the first one"
  assert_contains "$out" "paths=2 " "the pathspec the block answers for is counted"
  assert_contains "$out" "LOOP-MEASURE-END" "the block says where it ends"
  [ ! -e "$canary" ] || fail "the measuring command ran a check — it names what is owed, the gate runs it once"
  return 0
}

# The numbers a reviewer asks for by item: a scenario that names no case is a plan defect, and the tally a green
# claim quotes has to be in the block the worker holds, or the reviewer has to reconstruct it (issue #246).
case_measure_printsTheNumbersTheReviewerAsksFor() {
  # files: tools/loop-cases.sh tools/loop/fixtures/scenarios/ tools/loop/loop.conf tools/loop/measure.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf base out change
  d=$(scratch); p=$(new_project)
  conf="$d/measure.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_COMPILED_CMD=compiled-gate\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS="*.kt"\n'
    printf 'LOOP_GATE_SUITES="notes/*|notes-check"\n'
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
  } >> "$conf"
  mkdir -p "$d/tally" "$d/evidence"
  printf '<?xml version="1.0"?>\n<testsuite name="g" tests="7" failures="0"></testsuite>\n' > "$d/tally/g.xml"
  printf '<?xml version="1.0"?>\n<testsuite name="r" tests="2" failures="1"></testsuite>\n' > "$d/evidence/red.xml"

  change="$p/openspec/changes/fix-demo"
  mkdir -p "$change/specs/demo" "$change/evidence"
  cp "$d/evidence/red.xml" "$change/evidence/red.xml"
  printf 'surface: 1 case added — grep -l demo tools/loop-cases.sh\n' > "$change/proposal.md"
  {
    printf '## ADDED Requirements\n\n### Requirement: A demo\n\n'
    printf '#### Scenario: One\n\n- **Case**: `demo#one`\n\n'
    printf '#### Scenario: Two\n\n- **Case**: `demo#two`\n'
  } > "$change/specs/demo/spec.md"
  # …and a third scenario that names no case: the block counts it instead of a reviewer adding it up.
  printf '#### Scenario: Three\n\n- **WHEN** nothing\n' >> "$change/specs/demo/spec.md"

  base=$(git -C "$p" rev-parse HEAD)
  out=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/measure.sh" --base "$base" 2>&1 )
  assert_eq "0" "$?" "the measuring command succeeded: $out"
  assert_contains "$out" "scenarios=3 cases=2 unnamed=1" "the delta's scenarios are counted against the cases they name"
  assert_contains "$out" "tests=7 failures=0" "the gate's own tally is carried"
  assert_contains "$out" "tests=2 failures=1" "and the red tally the change's evidence carries"
  assert_contains "$out" "surface: 1 case added" "the proposal's surface record is quoted, not re-derived"
  return 0
}

# Two changes open at once are not the block's to choose between (issue #246, review round 1). It measured the
# alphabetically last open change, so a worker correcting one change read the other's scenario count and was
# never told; and the `--change` it documents resolved to nothing at all, because a bare name is not a directory.
# A name resolves under the open changes and, because a correction round works on a change that is already
# archived, under the archive too.
case_measure_refusesToGuessWhichChangeItMeasures() {
  # files: tools/loop/loop.conf tools/loop/measure.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf base out
  d=$(scratch); p=$(new_project)
  conf="$d/measure.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_SUITES="notes/*|notes-check"\n'
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
  } >> "$conf"
  mkdir -p "$p/openspec/changes/fix-one/specs/demo" "$p/openspec/changes/fix-two/specs/demo"
  printf '#### Scenario: One\n\n- **Case**: `demo#one`\n' > "$p/openspec/changes/fix-one/specs/demo/spec.md"
  {
    printf '#### Scenario: Two\n\n- **Case**: `demo#two`\n\n'
    printf '#### Scenario: Three\n\n- **WHEN** nothing\n'
  } > "$p/openspec/changes/fix-two/specs/demo/spec.md"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'two open changes' >/dev/null 2>&1 \
    || fail "could not commit the two changes in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)

  out=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/measure.sh" --base "$base" 2>&1 )
  assert_eq "0" "$?" "the measuring command succeeded: $out"
  assert_contains "$out" "ambiguous" "two open changes are named as ambiguous, not guessed between: $out"
  assert_contains "$out" "fix-one" "and both are named: $out"
  assert_contains "$out" "fix-two" "and the second of them too: $out"
  assert_not_contains "$out" "scenarios=" "no change's numbers are reported for a change the block could not pick"

  out=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/measure.sh" --base "$base" --change fix-one 2>&1 )
  assert_eq "0" "$?" "the measuring command succeeded with --change: $out"
  assert_contains "$out" "scenarios=1 cases=1 unnamed=0" "the name the worker passed is the change it measured: $out"
  assert_contains "$out" "LOOP-MEASURE-CHANGE fix-one" "and the block says which one it measured: $out"

  out=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/measure.sh" --base "$base" --change fix-nope 2>&1 )
  assert_eq "0" "$?" "a name that resolves nowhere still prints a block: $out"
  assert_contains "$out" "not-found" "and it is named rather than read as 'no change': $out"
  return 0
}

# A directory of more tallies than the block prints is not a directory of twenty (issue #246, review round 1): the
# block stopped at its limit and said nothing, so a reader took the silence for the whole tree. The line that
# says how many it withheld is what makes the cap a fact rather than a truncation.
case_measure_saysHowManyTalliesItWithheld() {
  # files: tools/loop/loop.conf tools/loop/measure.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf base out i
  d=$(scratch); p=$(new_project)
  conf="$d/measure.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_SUITES="notes/*|notes-check"\n'
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
  } >> "$conf"
  mkdir -p "$d/tally"
  for i in $(seq 1 25); do
    printf '<?xml version="1.0"?>\n<testsuite name="s%s" tests="3" failures="0"></testsuite>\n' "$i" \
      > "$d/tally/s$i.xml"
  done
  base=$(git -C "$p" rev-parse HEAD)
  out=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/measure.sh" --base "$base" 2>&1 )
  assert_eq "0" "$?" "the measuring command succeeded: $out"
  assert_eq "20" "$(printf '%s\n' "$out" | grep -c '^LOOP-MEASURE-TALLY ' || true)" \
    "the block lists at most twenty tallies per directory: $out"
  assert_contains "$out" "LOOP-MEASURE-TALLY-MORE" "and says that it withheld some: $out"
  assert_contains "$out" "5 of 25" "with the number withheld against the number it found: $out"
  return 0
}

case_prompt_workerIsHandedTheMeasureBlock() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/loop.conf tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p prompt text measure conf
  d=$(scratch); p=$(new_project)
  conf="$d/loop.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  export LOOP_CONF="$conf"
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompt="$p/.pi/issue-loop/items/21/worker-prompt.md"
  [ -f "$prompt" ] || fail "the worker prompt was not rendered"
  text=$(cat "$prompt")
  measure=$(sed -n 's/^LOOP_MEASURE_CMD="\(.*\)"/\1/p' "$LOOP_ENGINE/loop.conf")
  [ -n "$measure" ] || fail "the configuration names no measuring command"
  assert_contains "$text" "$measure" "the worker is handed the measuring command from loop.conf"
  assert_contains "$text" 'LOOP-MEASURE' "and the prefix the block's lines carry"
  assert_contains "$text" 'It runs none' "and that the block runs no check — the gate runs once"
  assert_contains "$text" 'cited, not read again' "a file read once is cited, never read a second time"
  return 0
}

case_run_oneItemOrderIsFixed() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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

# The block's two tracker writes are the only thing that makes a blocked item found again, and both were
# swallowed: measured 2026-10-23T11:38Z, item #260 of a real run ended `blocked` with `escalatedLabel: ""` while
# its six neighbours carried `agent/needs-decision` and no comment at all was posted — the run's own account and
# the tracker diverged silently, and the decision cap never counted it (issue #279). A write that failed is a fact
# of the run, so the run records it with the item and its report names the item nobody can find on the tracker.
case_run_aFailedEscalationWriteIsNamed() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/state.sh tools/loop/report.sh tools/pi-child-events.sh tools/tooling.json
  local d p out state
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  printf '#!/usr/bin/env bash\n: # this child leaves the tree exactly as it found it\n' > "$d/worker-action.sh"
  # The escalation's own label write fails the way a socket does, on every attempt. `pick` adds the in-progress
  # mark and the unpick removes it, so the decision label is the only write this names — the one the block needs
  # most, because an item with no decision label is an item no reader of the tracker knows a question about.
  export GH_STUB_TRANSIENT_FAIL="--add-label agent/needs-decision"
  export GH_STUB_TRANSIENT_TIMES=99
  export GH_STUB_TRANSIENT_STATE="$d/transient-count"
  export ISSUES_GH_RETRY_DELAYS="0 0 0"
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1) || fail "the run itself failed"
  unset GH_STUB_TRANSIENT_FAIL GH_STUB_TRANSIENT_TIMES GH_STUB_TRANSIENT_STATE ISSUES_GH_RETRY_DELAYS
  state=$(state_file "$p")
  assert_eq "blocked" "$(jq -r '.items[0].result' "$state")" "the item is still recorded blocked"
  assert_eq "" "$(jq -r '.items[0].escalatedLabel // ""' "$state")" "the run claims no label it did not write"
  assert_eq "escalate" "$(jq -r '(.items[0].labelFailures // []) | join(" ")' "$state")" \
    "the write the tracker refused is recorded with the item"
  assert_contains "$out" "set #21's tracker state by hand" "the report names the item whose escalation was not written"
  assert_contains "$out" "could not write escalate" "and says which write the tracker refused"
}

# The other half of the same block: the escalation lands and the in-progress mark cannot be taken off, so the
# tracker shows an item being worked while nothing holds it. The run clears its own `in_progress` variable on that
# path, so no later step retries the write — the record is what a person acts on (issue #279).
case_run_aFailedUnpickIsNamed() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/state.sh tools/loop/report.sh tools/pi-child-events.sh tools/tooling.json
  local d p out state
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  printf '#!/usr/bin/env bash\n: # this child leaves the tree exactly as it found it\n' > "$d/worker-action.sh"
  # Only the write that takes the mark off fails: `pick` adds the label in another call, so the item is worked
  # and marked exactly as usual before this write is refused.
  export GH_STUB_TRANSIENT_FAIL="--remove-label agent/in-progress"
  export GH_STUB_TRANSIENT_TIMES=99
  export GH_STUB_TRANSIENT_STATE="$d/transient-count"
  export ISSUES_GH_RETRY_DELAYS="0 0 0"
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1) || fail "the run itself failed"
  unset GH_STUB_TRANSIENT_FAIL GH_STUB_TRANSIENT_TIMES GH_STUB_TRANSIENT_STATE ISSUES_GH_RETRY_DELAYS
  state=$(state_file "$p")
  assert_eq "agent/needs-decision" "$(jq -r '.items[0].escalatedLabel // ""' "$state")" \
    "the escalation itself was written"
  assert_eq "unpick" "$(jq -r '(.items[0].labelFailures // []) | join(" ")' "$state")" \
    "the write the tracker refused is recorded with the item"
  assert_contains "$out" "set #21's tracker state by hand" "the report names the item whose mark was not cleared"
  assert_contains "$out" "could not write unpick" "and says which write the tracker refused"
}

case_run_childStartsInTheProjectRoot() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
# streaming from capture-and-replay: the console must carry the first line while the stand-in holds and must not
# carry the second, and the kept text must carry both once the run is over (issue #191). Holding the issue
# listing instead left the run's own phase line as the only mid-flight evidence, which a capture-and-replay would
# also print.
#
# The reading *waits* for that first line instead of taking it at an instant fixed before the run can have
# reached it (issue #287, measured 2026-10-24): the run compiles the issue tooling's Java core, writes its state
# and counts the open decisions before it calls the pre-screen — the compile alone is 4.1 s on a cold host, and
# the phase line lands 0.628–0.760 s in even when it is warm — so the `sleep 1.5` this case used to sample at read
# an empty console when the case ran alone and, on a loaded host, a console holding only the header. Both readings
# reported the host's own start-up cost as a red case, on an engine that had printed the line. The wait has a
# bound of its own (30 s), so a phase whose text never reaches the console still fails here — the mutation
# record `34.1` is what keeps that half honest — and so does one that is replayed after the call returns,
# because the moment the first line is read then already carries the line the stand-in had not printed yet (that
# shape is exercised by a hand probe rather than by a record, because the harness's one-line substitution cannot
# restructure the pipeline into a capture; the probe's reading is kept as `replay-probe.log` beside this
# change's evidence).
case_run_prescreenAnnouncesItselfBeforeItReturns() {
  # files: tools/loop/ tools/pi-child-events.sh tools/tooling.json
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
  # Wait for the stand-in's own first line: it is printed after the phase line the run writes before the call and
  # while the call still runs, so it is the earliest a reader can tell streamed from replayed — and waiting for it
  # is independent of how long the run's own start-up took (the comment above).
  out=""
  for _ in $(seq 600); do
    out=$(cat "$log")
    case "$out" in *'pre-screen: first record'*) break ;; esac
    sleep 0.05
  done
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/report.sh tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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

# The child cap is not the only clock a child can outlive (issue #227): the run's deadline is consulted between
# items, so a child started before it holds the run past the budget it was given. Measured 2026-10-20: of fifteen
# stuck runs eleven were past their own `--minutes 90`, the longest heartbeat read `291m30s`, and not one carried
# a `LOOP-TIMEOUT` line although the cap was 2 700 s — the budget was spent invisibly and no stop reason was
# recorded. The wait reads the run's own deadline from the state it wrote, ends the child and everything it
# started when the deadline passes, names the clock that ended it in the transcript, and records the stop.
case_run_childIsEndedAtTheRunsDeadline() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fifo pid i status out report state
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The child cap is an hour, so only the deadline can end this child.
  conf="$d/loop.conf"
  sed -e 's/^LOOP_CHILD_MAX_SECONDS=.*/LOOP_CHILD_MAX_SECONDS=3600/' "$LOOP_CONF" > "$conf"
  grep -qx 'LOOP_CHILD_MAX_SECONDS=3600' "$conf" || fail "this case could not lengthen the child cap in loop.conf"
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  fifo="$d/wait.fifo"; mkfifo "$fifo"
  # The child blocks on the fifo and is never released: only the deadline can end it. The item cap is three, so
  # the run's own stop is the deadline and not a cap it would reach first with a budget of one.
  set -m
  ( export LOOP_CONF="$conf" PI_STUB_WAIT="$fifo"
    run_loop "$p" --items 3 --minutes 90 --review off > "$d/out" 2>&1 ) &
  pid=$!
  set +m
  for i in $(seq 1 300); do [ -e "$fifo.ready" ] && break; sleep 0.1; done
  [ -e "$fifo.ready" ] || fail "the stub child never started; the run said: $(cat "$d/out")"
  exec 8> "$fifo"
  # The run's deadline arrives now. The clock the poll must read is the run's own recorded one — the state file
  # `report.sh` and `next` read — and moving it is the only way to reach the bound without waiting the 90
  # minutes the budget names: a shorter `--minutes` is refused by the item floor before any child starts, which
  # is the run behaving correctly. The hand edit is reported as drift by the state's next reader, as designed.
  state=$(state_file "$p")
  jq '.run.deadlineEpoch = ((now | floor) + 2) | .run.deadlineAt = "an earlier moment"' "$state" > "$state.tmp" &&
    mv "$state.tmp" "$state" || fail "this case could not move the run's recorded deadline"
  # 60 s is a bound on this case, not on the run: the deadline is 2 s away and a poll that reads it ends the
  # child a second or two after it passes.
  for i in $(seq 1 600); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  if kill -0 "$pid" 2>/dev/null; then
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    printf 'go\n' >&8 2>/dev/null || true
    wait "$pid" 2>/dev/null
    exec 8>&-
    fail "the run was still waiting 60 s after its own recorded deadline passed"
  fi
  wait "$pid"; status=$?
  exec 8>&-
  out=$(cat "$d/out")
  assert_eq "0" "$status" "a child the run ended at its own deadline is not a failed run"
  assert_contains "$out" "loop: worker-prompt finished in" "the wait is reported as finished, not abandoned"
  assert_contains "$out" "exit 124" "with the timeout's own status, distinct from a child's failure"
  report="$p/.pi/issue-loop/items/21/worker-report.md"
  [ -f "$report" ] || fail "the item kept no transcript of the child the deadline ended"
  assert_contains "$(cat "$report")" "LOOP-DEADLINE: the run's deadline passed at an earlier moment" \
    "the transcript names the clock that ended the child"
  assert_contains "$(cat "$report")" "LOOP-TIMEOUT: the child exceeded" \
    "and still carries the machine-readable line the run reads as its own timeout"
  assert_not_contains "$(cat "$report")" "3600 s" "the cap is not the bound that ended it"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$state")" \
    "an item whose child was ended is recorded blocked"
  assert_contains "$(jq -r '.items[0].note' "$state")" "exceeded" \
    "and the reason on the item names the bound that ended it"
  assert_contains "$(jq -r '.run.stop.reason // ""' "$state")" "deadline passed at" \
    "the run records the deadline as its stop reason"
}

# The cap ends the child's *process group*, and a descendant that gave itself its own session is not a member of
# it: the run recorded the child as ended at 23:48 and the mutation harness the child had started kept mutating
# and restoring the worktree until 23:55, its last restore being what left that tree dirty, so the next run
# refused to start (measured 2026-10-22, issue #272). A survivor still names the tree it is working in as its
# working directory — the reader `loop_pids_under` already owns — so the case is a stub child that starts a
# writer in the worktree and leaves it in a session of its own. On the tree before the fix that writer outlives
# the run, with the fix it is ended and named before the item's outcome is written.
case_run_detachedProcessInTheWorktreeIsEndedByTheCap() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fifo pid i status report tree writer alive out
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  conf="$d/loop.conf"
  sed -e 's/^LOOP_CHILD_MAX_SECONDS=.*/LOOP_CHILD_MAX_SECONDS=2/' "$LOOP_CONF" > "$conf"
  grep -qx 'LOOP_CHILD_MAX_SECONDS=2' "$conf" || fail "this case could not shorten the child cap in loop.conf"
  tree="$p/.pi/worktrees/loop"
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  # The child's own work: a writer at the worktree's root, in a session of its own, which names itself and then
  # keeps writing the tree the run reads — the shape of the harness that survived the cap in #272.
  {
    printf 'case "$1" in\n'
    printf '  worker*)\n'
    printf '    setsid bash -c %s >/dev/null 2>&1 &\n' \
      "'printf \"%s\\n\" \"\$\$\" > $d/writer.pid; while :; do printf x >> survivor.txt; sleep 0.2; done'"
    printf '    ;;\n'
    printf 'esac\n'
  } > "$d/worker-action.sh"
  fifo="$d/wait.fifo"; mkfifo "$fifo"
  set -m
  ( export LOOP_CONF="$conf" PI_STUB_WAIT="$fifo"
    run_loop "$p" --items 1 --minutes 90 --review off > "$d/out" 2>&1 ) &
  pid=$!
  set +m
  for i in $(seq 1 200); do [ -e "$fifo.ready" ] && break; sleep 0.1; done
  if [ ! -e "$fifo.ready" ]; then
    kill -9 "$pid" 2>/dev/null
    fail "the stub child never started"
  fi
  # The case holds the fifo's writer side for its whole life, so releasing a child never blocks and never leaves a
  # writer behind.
  exec 8> "$fifo"
  writer=""
  for i in $(seq 1 100); do [ -f "$d/writer.pid" ] && { writer=$(cat "$d/writer.pid"); break; }; sleep 0.1; done
  # Twenty seconds is a bound on this case, not on the run: the run is supposed to end itself well inside it.
  for i in $(seq 1 200); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  if kill -0 "$pid" 2>/dev/null; then
    # The diagnosis has to clean up after itself: the run's own group dies here, and the fifo line the child never
    # got is written so a child still waiting on it leaves too.
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    printf 'go\n' >&8 2>/dev/null || true
    wait "$pid" 2>/dev/null
    exec 8>&-
    [ -n "$writer" ] && kill -9 "$writer" 2>/dev/null
    fail "the run was still waiting after 20 s on a child that outlasted its 2 s cap"
  fi
  wait "$pid"; status=$?
  exec 8>&-
  assert_eq "0" "$status" "a child the run itself ended is not a failed run"
  [ -n "$writer" ] || fail "the stub child's detached writer never named itself"
  # The survivor is the whole of what this case is about: an assertion that only read the report would stay green
  # while the process went on writing the tree.
  alive=1
  for i in $(seq 1 100); do kill -0 "$writer" 2>/dev/null || { alive=0; break; }; sleep 0.1; done
  if [ "$alive" -eq 1 ]; then
    kill -9 "$writer" 2>/dev/null
    fail "the process the ended child left in the worktree (pid $writer) outlived the run — the cap must end what the child started, and a session of its own is not a group the cap reaches"
  fi
  report="$p/.pi/issue-loop/items/21/worker-report.md"
  [ -f "$report" ] || fail "the item kept no transcript of the child that was ended"
  assert_contains "$(cat "$report")" "LOOP-ENDED: ended $writer — its working directory is inside" \
    "the transcript names the process the group signal could not reach"
  out=$(loop_pids_under "$tree")
  [ -z "$out" ] || fail "process(es) of the ended child are still working in the worktree: $out"
}

# A child the run itself ended is unfinished, and the tree at that moment is whatever it was doing: a
# half-applied mutation, a half-written file, a gate's scratch directory. The run committed that tree by calling
# `end-item` before it knew the child was over — measured 2026-10-22 (issue #271): the worker of #262 was ended at
# 23:48 with mutation 19.2 applied (`if false; then` in place of the corrective-round test), `end-item` committed
# the tree 18 s later, and the dead arm stayed on `origin/fix/issue-262-…` until it was amended by hand at 05:05.
# The assertion is the branch, not the report: a commit that carries the child's half-written file is the whole
# defect, and the tree the child left is kept rather than discarded (nothing is discarded without
# `--reset-worktree`, `run.sh`'s own rule for the kept worktree).
case_run_treeOfAChildTheCapEndedIsNotCommitted() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fifo pid i status out tree base report files
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  conf="$d/loop.conf"
  sed -e 's/^LOOP_CHILD_MAX_SECONDS=.*/LOOP_CHILD_MAX_SECONDS=2/' "$LOOP_CONF" > "$conf"
  grep -qx 'LOOP_CHILD_MAX_SECONDS=2' "$conf" || fail "this case could not shorten the child cap in loop.conf"
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  # The child's own work, half applied: a file in the worktree it never finished, written before it blocks on the
  # fifo. The mutation harness of #262 has the same shape — the tree carries the edit and the child has no report.
  {
    printf 'case "$1" in\n'
    printf '  worker*)\n'
    printf '    printf "half-applied\\n" > half-applied.txt\n'
    printf '    ;;\n'
    printf 'esac\n'
  } > "$d/worker-action.sh"
  export PI_STUB_ACTION="$d/worker-action.sh"
  tree="$p/.pi/worktrees/loop"
  base=$(git -C "$p" rev-parse HEAD)
  fifo="$d/wait.fifo"; mkfifo "$fifo"
  set -m
  ( export LOOP_CONF="$conf" PI_STUB_WAIT="$fifo"
    run_loop "$p" --items 1 --minutes 90 --review off > "$d/out" 2>&1 ) &
  pid=$!
  set +m
  for i in $(seq 1 200); do [ -e "$fifo.ready" ] && break; sleep 0.1; done
  if [ ! -e "$fifo.ready" ]; then
    kill -9 "$pid" 2>/dev/null
    fail "the stub child never started"
  fi
  # The case holds the fifo's writer side for its whole life, so releasing a child never blocks and never leaves a
  # writer behind. The child is never released: the 2 s cap ends it with its file written.
  exec 8> "$fifo"
  for i in $(seq 1 200); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  if kill -0 "$pid" 2>/dev/null; then
    # The diagnosis has to clean up after itself: the run's own group dies here, and the fifo line the child never
    # got is written so a child still waiting on it leaves too.
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    printf 'go\n' >&8 2>/dev/null || true
    wait "$pid" 2>/dev/null
    exec 8>&-
    fail "the run was still waiting after 20 s on a child that outlasted its 2 s cap"
  fi
  wait "$pid"; status=$?
  exec 8>&-
  out=$(cat "$d/out")
  assert_eq "0" "$status" "a child the run itself ended is not a failed run"
  report="$p/.pi/issue-loop/items/21/worker-report.md"
  [ -f "$report" ] || fail "the item kept no transcript of the child that was ended"
  assert_contains "$(cat "$report")" "LOOP-TIMEOUT: the child exceeded 2 s" \
    "the report carries the line that says the run ended the child"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "an item whose child was ended by the cap is recorded blocked"
  # The run's own line is part of the contract, not only the branch: a person reads the log to learn that the
  # commit was skipped rather than made, so the sentence the requirement states is asserted here too.
  assert_contains "$out" "end-item skipped" \
    "the run's own line says the commit was skipped — the branch is not the only record of it"
  # The branch is the assertion: `end-item` committed the tree the ended child left, so the item's branch carries a
  # commit of work the child never claimed — and the half-applied file with it.
  assert_eq "$base" "$(git -C "$tree" rev-parse HEAD)" \
    "the tree a child the run ended left behind is not committed — the branch stays at the base"
  files=$(git -C "$tree" status --porcelain)
  assert_contains "$files" "half-applied.txt" \
    "and the tree the ended child left is kept as it left it, named for the person who repairs it"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "nothing is closed on a child the run ended"
}

# The same rule at the second commit site (issue #271, found by that change's round-1 review): a reviewer's refusal
# sends the same change to a rework worker, and the run commits that worker's tree at `end-item` there without
# reading its status either. A rework worker the cap ends leaves the tree in whatever state it was doing, exactly
# as the item's own worker does — and a refusal is not a licence to assert work nobody claimed — so the commit is
# skipped there too, the item is blocked with the round's own transcript, and the tree stays as the child left it.
# Measured 2026-10-23: the shipped requirement was universal while this call site was left as it stood.
case_run_treeOfACapEndedReworkOfARefusingRoundIsNotCommitted() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf status out tree report files
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  conf="$d/loop.conf"
  sed -e 's/^LOOP_CHILD_MAX_SECONDS=.*/LOOP_CHILD_MAX_SECONDS=2/' "$LOOP_CONF" > "$conf"
  grep -qx 'LOOP_CHILD_MAX_SECONDS=2' "$conf" || fail "this case could not shorten the child cap in loop.conf"
  # Round 1's reviewer refuses the change, so the run starts the rework worker; that worker writes its
  # half-applied file and then outlasts the 2 s cap, which is the tree the run must not commit.
  stub_write "$d" "$STUB_WORKER_REPORT" 'BLOCKING: the demo scenario names no case
LOOP-BLOCKING: 1
VERDICT: FAIL' "case \"\$1\" in
  worker-prompt-round2.md)
    printf \"half-applied\\n\" > half-applied.txt
    sleep 30
    ;;
esac"
  tree="$p/.pi/worktrees/loop"
  export LOOP_CONF="$conf"
  run_loop "$p" --items 1 --minutes 90 --review on > "$d/out" 2>&1; status=$?
  out=$(cat "$d/out")
  assert_eq "0" "$status" "a rework worker the run itself ended is not a failed run"
  report="$p/.pi/issue-loop/items/21/worker-report-round2.md"
  [ -f "$report" ] || fail "the rework round kept no transcript of the child that was ended"
  assert_contains "$(cat "$report")" "LOOP-TIMEOUT: the child exceeded 2 s" \
    "the round's report carries the line that says the run ended the child"
  # The branch is the assertion: `end-item` would have committed the tree the ended rework worker left, on top of
  # whatever the item's own worker and the round's announcement already earned, and the half-applied file with it.
  assert_eq "0" "$(git -C "$tree" log --format=%H --name-only | grep -c 'half-applied.txt' || true)" \
    "the tree a rework worker the cap ended left behind is committed nowhere — the branch carries only what the item's own worker earned"
  files=$(git -C "$tree" status --porcelain)
  assert_contains "$files" "half-applied.txt" \
    "and the tree that worker left is kept as it left it, for the person who repairs it"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "an item whose rework worker was ended by the cap is recorded blocked"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "not committed" \
    "and the reason says the round's tree was not committed"
  assert_eq "0" "$(grep -c 'issue close 21' "$GH_STUB_RECORD" || true)" \
    "nothing is closed on a rework worker the run ended"
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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

# The project's own tree is not the child's: the run's own landings fast-forward the base there
# (`worktree.sh merge-item`), a peer session writes it, and the two readings the run compares are taken up to a
# child's whole wall-clock apart. Measured 2026-10-23 (#274, #276, #285): 5 of 139 items were blocked by that
# comparison and the three cases of that day had written nothing there — every write target in the three
# transcripts lay under `.pi/worktrees/loop/` or the item's evidence directory. So the difference is recorded and
# the item keeps the outcome its work earned; `--strict-outside-write yes` restores the verdict for a tree that is
# exclusive, and the two strict cases pin it.
case_run_aWriterTheRunCanPlaceBlocksTheItem() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p note
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # A child that writes the project's own tree and leaves a process of its own standing there: the file is taken
  # back before the child ends, so the reading at its exit sees nothing. Only the reading taken *while* it ran can
  # tie the write to the child — that is the evidence this change adds, and it decides the item (#286).
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'case "$1" in worker*) printf "stray\n" >> "$LOOP_CHILD_ROOT/demo-owned.txt"
       ( cd "$LOOP_CHILD_ROOT" && exec sleep 2 ) &
       sleep 3; rm -f "$LOOP_CHILD_ROOT/demo-owned.txt" ;; esac'
  ( export LOOP_PROGRESS_SECONDS=1
    run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 ) || fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "a write the run can place in the child's own tree decides the item"
  note=$(jq -r '.items[0].note' "$(state_file "$p")")
  assert_contains "$note" "outside the worktree" "the reason names the tree that changed"
  assert_contains "$note" "attributed to this child" "and says the run tied the difference to this child"
}

case_run_aPeerWriteWhileAChildStandsDoesNotDecideTheItem() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p note
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The run starts the child **in the project** (`run_child`), so the child itself and every shell it was started
  # through stand in the project's own tree for the whole item. A difference a peer made while such a child is alive
  # must not decide it, or every difference in that tree would: measured 2026-10-24 (issue #288), an attribution
  # that marked the child's own standing chain turned a peer's write into `blocked` for an item whose work was never
  # touched. The child here does nothing to the tree; this case is the peer.
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' 'case "$1" in worker*) sleep 14 ;; esac'
  # The peer keeps the difference in the project's tree until the run has recorded it, and then restores it. The
  # write has to land after the state the run compares against was read (`run_child`'s `before=$(project_state)`,
  # which happens just before the transcript file is created), and it has to still be there at a heartbeat tick;
  # writing once and waiting lands outside that window in about 1 of 40 runs (measured 2026-10-24: the item came
  # back `blocked`, because the peer's own restoration never happened). Writing until the run has recorded it is
  # independent of both, and it is what a peer rewriting a tree looks like from the run's side.
  ( item=""
    for _ in $(seq 300); do
      item=$(ls -d "$p"/.pi/issue-loop/items/* 2>/dev/null | head -1)
      [ -n "$item" ] && break
      sleep 0.2
    done
    [ -n "$item" ] || exit 0
    for _ in $(seq 300); do [ -e "$item/worker-report.md" ] && break; sleep 0.1; done
    for _ in $(seq 60); do
      printf "peer\n" >> "$p/demo-owned.txt"
      [ -e "$item/worker-report.md.outside-write" ] && break
      sleep 0.2
    done
    sleep 0.5; rm -f "$p/demo-owned.txt" ) &
  ( export LOOP_PROGRESS_SECONDS=1
    run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 ) || fail "the run itself failed"
  assert_eq "done" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "a difference the child's own standing shell did not make must not decide the item"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" "the item closes on its own work"
  note=$(jq -r '[.steps[] | select(.name | startswith("outside-write")) | .note] | join(" | ")' "$(state_file "$p")")
  assert_contains "$note" "demo-owned.txt" "the difference is recorded, with the path that changed"
}

case_run_childWriteOutsideTheWorktreeIsNoted() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p out note
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The measured shape: another process wrote the project's own tree while a child ran, and took it back again
  # before the item landed. The run records the difference twice — the worker's write and the reviewer's
  # removal — and the item keeps the outcome its work earned, because a difference is not a verdict about the
  # child the run started (#274, #276, #285).
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'case "$1" in worker*) printf "stray\n" >> "$LOOP_CHILD_ROOT/demo-owned.txt" ;; reviewer*) rm -f "$LOOP_CHILD_ROOT/demo-owned.txt" ;; esac'
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1) || fail "the run itself failed"
  assert_eq "done" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "a difference outside the worktree is recorded, not turned into a verdict about the item"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" "the item closes on its own work"
  note=$(jq -r '[.steps[] | select(.name | startswith("outside-write")) | .note] | join(" | ")' "$(state_file "$p")")
  assert_contains "$note" "the project tree changed while this process ran" "the run records each difference it saw"
  assert_contains "$note" "demo-owned.txt" "and names the path that changed, so the note is answerable"
  assert_eq "1" "$(jq -r '[.steps[] | select(.name == "outside-write")] | length' "$(state_file "$p")")" \
    "the worker's own difference is recorded, not only the round that followed it"
  assert_eq "1" "$(jq -r '[.steps[] | select(.name == "outside-write-review-1")] | length' "$(state_file "$p")")" \
    "and the review round that ran during one is recorded under its own name"
  assert_contains "$out" "loop: note ·" "the difference is on the transcript a person tails, not only in the state"
  assert_contains "$(cat "$(find "$p/.pi/issue-loop/items" -name 'worker-report.md' | head -1)")" "LOOP-OUTSIDE-WRITE:" \
    "the child's own transcript carries it as a machine-readable line, not only the run's note"
  assert_eq "1" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "and the item is closed"
}

case_run_dirtyBaseRefusesTheMergeAndNamesThePath() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p note
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The difference that is still there when the item lands. The run does not invent a verdict about the child for
  # it: the merge declines a base that is not clean — its own safety rule, unchanged by this change — and the
  # reason it records names the path, which is what makes the escalation answerable (#285's naming, folded here).
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'case "$1" in worker*) printf "stray\n" >> "$LOOP_CHILD_ROOT/demo-owned.txt" ;; esac'
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "a base that is still dirty cannot be merged onto"
  note=$(jq -r '.items[0].note' "$(state_file "$p")")
  assert_contains "$note" "is not clean" "the reason is the landing rule, which is the run's own"
  assert_contains "$note" "demo-owned.txt" "and it names the path, so the owner can act on it"
  case "$note" in *"wrote outside the worktree"*)
    fail "the reason still blames the child for a difference the run cannot attribute: $note" ;; esac
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "and the item is not closed"
}

case_run_strictOutsideWriteBlocksTheItem() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p note
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # `--strict-outside-write yes` is the run that asks to judge the tree: the same write blocks the item, as it did
  # for every run before 2026-10-23
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'case "$1" in worker*) printf "stray\n" >> "$LOOP_CHILD_ROOT/demo-owned.txt" ;; esac'
  run_loop "$p" --items 1 --minutes 90 --review on --strict-outside-write yes >/dev/null 2>&1 ||
    fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "a write outside the worktree blocks the item when the run asked to judge it"
  note=$(jq -r '.items[0].note' "$(state_file "$p")")
  assert_contains "$note" "outside the worktree" "the reason names the tree that changed"
  assert_contains "$note" "demo-owned.txt" "and the path, because the strict run is answerable too"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "and the item is not closed"
}

case_run_strictOutsideWriteDiscardsTheReviewRound() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p note
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # the strict run reads a reviewer that wrote anywhere it may not as no reviewer at all
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'case "$1" in reviewer*) printf "the reviewer wrote\n" >> "$LOOP_CHILD_ROOT/demo-owned.txt" ;; esac'
  run_loop "$p" --items 1 --minutes 90 --review on --strict-outside-write yes >/dev/null 2>&1 ||
    fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "a reviewer that wrote in the project cannot have been the reviewer under --strict-outside-write yes"
  note=$(jq -r '.items[0].note' "$(state_file "$p")")
  assert_contains "$note" "the review round was discarded" "the round is discarded with a reason"
  assert_contains "$note" "demo-owned.txt" "and the reason names the path that changed"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "nothing is closed on a discarded round"
}

case_run_leftoverBranchIsRecordedNotDropped() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # Each worker also writes an archived change of its own, named after the branch it found itself on: the base
  # advances with every merge (issue #252), so a second item whose only change repeated the first item's would be a
  # tree that genuinely carries no change of its own — which the run blocks on purpose, and which is not this
  # case's subject.
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'case "$1" in worker*)
       current=$(git rev-parse --abbrev-ref HEAD)
       printf "%s\n" "$current" > "branch-$(printf %s "$current" | tr / _).txt"
       change="openspec/changes/archive/2026-01-01-fix-$(printf %s "$current" | tr / _)"
       mkdir -p "$change/evidence"
       printf "archived\n" > "$change/proposal.md"
       cp app/build/test-results/test/TEST-demo.xml "$change/evidence/TEST-demo.xml"
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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

# The round is readable from the branch in both directions, which is what makes an unanswered one a fact rather
# than a silence: the run announces the round before its reviewer starts and records the answer when it arrives
# (issue #262). Without the announcement the only record of a round is its refusal or its pass, so a reviewer
# killed mid-answer left a branch that read exactly like a passed one.
case_run_announcesAndRecordsTheReviewRound() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p evidence commits
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"

  evidence=$(git -C "$p" ls-tree -r --name-only main -- openspec/changes/archive/2026-01-01-fix-demo/evidence)
  assert_contains "$evidence" "review-round-1-requested.md" \
    "the round is announced on the branch before the reviewer runs"
  assert_contains "$evidence" "review-round-1-passed.md" "and the answer is recorded when it arrives"
  commits=$(git -C "$p" log --format=%s main)
  assert_contains "$commits" "round 1 requested" "the announcement is a commit on the branch"
  assert_contains "$commits" "round 1 passed" "and so is the answer"
}

case_run_mergesAFinishedAttemptAndClosesTheItem() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # The branch the run merged is removed once the run merged it — the work is in the base, and the branch
  # itself was a leftover — so the commits it carried are what the base is asked about.
  [ -z "$(git -C "$p" branch --list "$branch")" ] || fail "the run left the branch it merged behind: $branch"
  for c in $(jq -r '.items[0].merged.commits' "$(state_file "$p")" | tr ',' ' '); do
    git -C "$p" merge-base --is-ancestor "$c" main || fail "the finished attempt's commit $c is not on the base"
  done
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
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
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

# The landing step merges into the project's **own tree**, and what a merge there moves is the branch that tree has
# checked out — never "the base" in the abstract. `check_base` compares the base *ref* with the base this run
# recorded, which answers nothing about the checkout: a person switching the project tree while the run is open
# either gets a failed fast-forward at the merge (an item's whole budget spent for a refusal a guard could have
# taken first) or, when the checkout merely fast-forwards to the item's branch, has the merge absorbed by that
# branch while the base stays where it was — the run then re-reads the unchanged base, records it, and closes the
# item with its fix on neither (issue #302, from the read-only review of #301, 2026-10-24). The merge verifies the
# precondition it depends on and names both branches it found; the pre-screen's adoption merges into the same
# tree, so its refusal is this one and is taken before the evidence it would check the branch for. A detached HEAD
# is the same refusal with no branch to name: the guard reads the checkout, so the state is named instead.
case_worktree_refusesToMergeIntoATreeOffTheBaseBranch() {
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local p w base branch tip out status off
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

  # a person switches the project's own tree while the run is open, and the item's branch is fast-forwardable
  # from where they left it — the shape the silent absorb needs
  git -C "$p" switch -c a-persons-branch >/dev/null 2>&1 || fail "could not switch the project tree"
  off=$(git -C "$p" rev-parse a-persons-branch)

  out=$(run_worktree "$p" merge-item 21 "$branch" 2>&1); status=$?
  assert_eq "5" "$status" "a project tree off the base branch refuses the merge"
  assert_contains "$out" "a-persons-branch" "the refusal names the branch the tree is on"
  assert_contains "$out" "main" "and the base branch it would have landed on"
  assert_eq "$base" "$(git -C "$p" rev-parse main)" "the base did not move"
  assert_eq "$off" "$(git -C "$p" rev-parse a-persons-branch)" \
    "and the branch the tree is on absorbed nothing, so no third branch carries the item's commits unseen"
  [ ! -f "$p/demo-fix.txt" ] || fail "the fix reached a tree off the base branch"

  out=$(run_worktree "$p" adopt-item 21 "$branch" 2>&1); status=$?
  assert_eq "5" "$status" "adoption into a tree off the base branch is refused too"
  assert_contains "$out" "a-persons-branch" "and names the branch the tree is on"
  [ ! -f "$p/demo-fix.txt" ] || fail "adoption landed the work in a tree off the base branch"
  if git -C "$p" merge-base --is-ancestor "$tip" a-persons-branch; then
    fail "the item's commit reached the branch the tree was left on"
  fi

  # The clause has a second arm: a detached HEAD has no branch to name, so the guard names the state. Left there, the
  # tree refuses the same two commands before anything is merged into it.
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not return the project tree to the base branch"
  git -C "$p" switch --detach >/dev/null 2>&1 || fail "could not detach the project tree's HEAD"
  out=$(run_worktree "$p" merge-item 21 "$branch" 2>&1); status=$?
  assert_eq "5" "$status" "a project tree on a detached HEAD refuses the merge"
  assert_contains "$out" "detached HEAD" "the refusal names the state instead of a branch"
  assert_contains "$out" "main" "and the base branch it would have landed on"
  assert_eq "$base" "$(git -C "$p" rev-parse main)" "the base did not move"
  [ ! -f "$p/demo-fix.txt" ] || fail "the fix reached a tree on a detached HEAD"

  out=$(run_worktree "$p" adopt-item 21 "$branch" 2>&1); status=$?
  assert_eq "5" "$status" "adoption into a tree on a detached HEAD is refused too"
  assert_contains "$out" "detached HEAD" "and names the state instead of a branch"
  [ ! -f "$p/demo-fix.txt" ] || fail "adoption landed the work in a tree on a detached HEAD"
  if git -C "$p" merge-base --is-ancestor "$tip" HEAD; then
    fail "the item's commit reached the detached HEAD"
  fi
}

# The base a run records is the commit every later item descends from, and the closure that follows a merge is
# recorded against it — so a commit the base branch does not carry is a closure against a tree that never had the
# fix. `set-base` accepted any commit the run computed (`tools/loop/state.sh`), the ancestry of the base branch
# being nobody's question, which is what let the absorbed merge of issue #302 be recorded as an advance: the run
# re-read the unchanged base, `set-base` took it, and the item closed with `status/fixed-by` on a fix the base never
# had. The command refuses such a commit, naming the branch it asked, and the base it recorded stays where it was.
case_state_setBaseRefusesACommitTheBaseBranchDoesNotCarry() {
  # files: tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p base side out status
  p=$(new_project)
  init_state "$p" 1 90
  base=$(run_state "$p" get run.base)
  # a commit off the base, which is what a merge taken on another branch leaves behind
  git -C "$p" switch -c a-persons-branch >/dev/null 2>&1 || fail "could not create a branch off the base"
  printf 'the owner\n' > "$p/owner.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm "work the base does not have" >/dev/null 2>&1 ||
    fail "could not commit off the base"
  side=$(git -C "$p" rev-parse HEAD)
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not return to the base branch"

  out=$(run_state "$p" set-base "$side" 2>&1); status=$?
  assert_eq "5" "$status" "a commit the base branch does not carry is refused"
  assert_contains "$out" "main" "the refusal names the base branch the commit was asked of"
  assert_eq "$base" "$(run_state "$p" get run.base)" "and the recorded base is unchanged"

  run_state "$p" set-base "$(git -C "$p" rev-parse main)" >/dev/null 2>&1 ||
    fail "the commit the base branch carries was refused"
  assert_eq "$(git -C "$p" rev-parse main)" "$(run_state "$p" get run.base)" \
    "a commit the base branch carries still advances the record"
}

# A run's success path commits the item's work on the item's own branch and then closes the item; the only
# merge in the loop was the adoption of a *previous* attempt, which needs the item to be visited again, so a
# closed item's fix stayed on `fix/issue-N-…` while the tracker read `status/fixed-by` — measured 2026-10-20 on
# #201, whose two commits a person fast-forwarded onto `main` by hand 35 minutes later (issue #229). The run
# merges the branch it committed before it closes the item.
case_run_mergesItsCommittedBranchBeforeClosing() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local d p runlog branch commits c log
  d=$(scratch); p=$(new_project)
  runlog="$d/run.txt"
  run_one_item "$p" "$d" --review on >"$runlog" 2>&1 || fail "the run itself failed: $(cat "$runlog")"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" "the reviewed item is closed"
  # The branch the run committed on is gone once the run merged it: the work is in the base, and the branch
  # itself was the run's own leftover. The commits it carried are what the record keeps.
  branch=$(git -C "$p" for-each-ref --format='%(refname:short)' 'refs/heads/fix/issue-21-*' | head -1)
  [ -z "$branch" ] || fail "the run left the branch of the item it closed behind: $branch"
  commits=$(jq -r '.items[0].merged.commits' "$(state_file "$p")")
  [ -n "$commits" ] && [ "$commits" != "null" ] || fail "the run recorded no merged commits"
  last=$(printf '%s' "$commits" | awk -F, '{print $NF}')
  git -C "$p" merge-base --is-ancestor "$last" main ||
    fail "the item was closed with its fix on the branch it merged, which the base does not have"
  [ -f "$p/demo-fix.txt" ] || fail "the fix the run committed did not reach the base"
  assert_eq "$(git -C "$p" rev-parse "$last")" "$(git -C "$p" rev-parse main)" \
    "the base is the merge of the branch, not a second commit of its own"
  # The closure's own account of the merge, which is what a reader has left once the run's state is replaced by
  # the next run's: the branch it merged, the commits it carried, and the closure's evidence naming both.
  recorded=$(jq -r '.items[0].merged.branch' "$(state_file "$p")")
  case "$recorded" in
    fix/issue-21-*) ;;
    *) fail "the run did not record the branch it merged: $recorded" ;;
  esac
  for c in ${commits//,/ }; do
    git -C "$p" merge-base --is-ancestor "$c" main || fail "the recorded commit $c is not on the base"
  done
  assert_eq "$(git -C "$p" rev-parse main)" "$(run_state "$p" get run.base)" \
    "and the base the run recorded is the merge, so the next item builds on it"
  log=$(cat "$GH_STUB_RECORD")
  assert_contains "$log" "merged $recorded (commits $commits) onto the base" \
    "the closure's evidence names the merge, so the tracker's fixed-by claim carries what backs it"
}

# The base a run hands each item is the run's own variable, and a merge advances it (issue #252).
# `merge_committed_branch` declared `base` in its own `local` list, so `loop_state set-base` moved the state file
# while the run kept the commit it read once at its start: every later item was handed the run-start commit as
# `BASE=`, and its review diff carried every change the run had already merged. Measured 2026-10-22: by item #116
# the round-1 diff was 441 KB and named ten archived changes of items that had closed hours before, and a
# falsification was recorded `skipped` because the tree was compared with a base several merges old. Two items
# closed by merge in one run — the second one's prompt and diff carry nothing of the first.
case_run_theBaseAdvancesWithEveryMerge() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local d p run_log first_merge prompt diff run_start
  d=$(scratch); p=$(new_project)
  run_start=$(git -C "$p" rev-parse main)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario two)
  stub_per_item_worker "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' '
    change="openspec/changes/archive/2026-01-01-fix-item$n"
    mkdir -p "$change/evidence" app/build/test-results/test
    printf "archived\n" > "$change/proposal.md"
    printf "<testsuite tests=\"1\" failures=\"0\"></testsuite>\n" > app/build/test-results/test/TEST-demo.xml
    cp app/build/test-results/test/TEST-demo.xml "$change/evidence/TEST-demo.xml"
    printf "the fix for item %s\n" "$n" > "item$n-fix.txt"
'
  run_log="$d/run.txt"
  run_loop "$p" --items 2 --minutes 90 --review on >"$run_log" 2>&1 ||
    fail "the run itself failed: $(cat "$run_log")"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" "the first item closed"
  assert_eq "closed" "$(jq -r '.items[1].verdict' "$(state_file "$p")")" "the second item closed"
  first_merge=$(jq -r '.items[0].merged.commits' "$(state_file "$p")" | awk -F, '{print $NF}')
  [ -n "$first_merge" ] && [ "$first_merge" != "null" ] || fail "the first item's merge commit was not recorded"
  [ "$first_merge" != "$run_start" ] || fail "the first item's merge is the run's start — nothing advanced"
  prompt=$(cat "$p/.pi/issue-loop/items/22/worker-prompt.md")
  assert_contains "$prompt" "$first_merge" \
    "the second item is handed the first item's merge as its base, not the run-start commit"
  diff=$(cat "$p/.pi/issue-loop/items/22/review-1.diff")
  assert_contains "$diff" "item2-fix.txt" "the round-1 diff is the second item's own change"
  assert_not_contains "$diff" "item1-fix.txt" \
    "and it carries nothing the first item landed, which the base already has"
  assert_eq "$(git -C "$p" rev-parse main)" "$(run_state "$p" get run.base)" \
    "the state's base and the run's own agree"
}

# A kept worktree carries the test results a build wrote, and a child the round cap ended leaves the half-written
# state of its own gate behind (issue #253: item #116's killed Gradle left
# `auto/build/test-results/testDebugUnitTest/binary/in-progress-results-generic.bin`, and the next gate failed with
# `java.io.EOFException` on infrastructure instead of on a case). The run reads the gate's evidence out of the tree
# after a worker, so a directory an earlier item wrote was counted as the next item's gate — and the "no
# test-results XML exists in the tree" refusal could not fire against it. The first item here leaves a green XML in
# the worktree; the second writes a change and claims a gate, but no XML of its own.
case_run_staleTestResultsAreNotThisChildsGate() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p run_log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario two)
  stub_per_item_worker "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' '
    change="openspec/changes/archive/2026-01-01-fix-item$n"
    mkdir -p "$change/evidence"
    printf "archived\n" > "$change/proposal.md"
    printf "the fix for item %s\n" "$n" > "item$n-fix.txt"
    if [ "$n" -eq 1 ]; then
      mkdir -p app/build/test-results/test
      printf "<testsuite tests=\"1\" failures=\"0\"></testsuite>\n" > app/build/test-results/test/TEST-demo.xml
      cp app/build/test-results/test/TEST-demo.xml "$change/evidence/TEST-demo.xml"
    fi
'
  run_log="$d/run.txt"
  run_loop "$p" --items 2 --minutes 90 --review on >"$run_log" 2>&1 ||
    fail "the run itself failed: $(cat "$run_log")"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" \
    "the first item closes on the gate its own worker ran"
  assert_eq "blocked" "$(jq -r '.items[1].verdict' "$(state_file "$p")")" \
    "the second item is not closed on a test result its own gate never wrote"
  assert_contains "$(jq -r '.items[1].note' "$(state_file "$p")")" "no test-results XML exists in the tree" \
    "the block names the gate result it could not find"
  assert_contains "$(cat "$run_log")" "app/build/test-results" \
    "and the run names the stale results it removed before that child"
}

# The merge lands in the project's own tree, where the base branch is checked out, so a tree that is not clean at
# the moment of the closure refuses it. A run that read that refusal as a closure would write `status/fixed-by`
# about a fix only the branch has — the defect #229 reported, with the run's own child as the cause of the dirt.
# Here the dirt is a person's and is there before the run starts, so no write of the run's is being blamed: the
# tree is simply not one the merge may land in, and the item stays open with its branch named (issue #229).
case_run_doesNotCloseOnAMergeTheBaseCannotTake() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p base branch log
  d=$(scratch); p=$(new_project)
  printf 'work in progress\n' > "$p/uncommitted.txt"
  base=$(git -C "$p" rev-parse main)
  run_one_item "$p" "$d" --review on >/dev/null 2>&1 || fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" \
    "a closure whose merge the base cannot take is blocked, not settled"
  assert_eq "unmergeable" "$(jq -r '.items[0].stopReason' "$(state_file "$p")")" \
    "and the record names the base's refusal as the mechanism that stopped it (issue #280)"
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # The repair discards what it names and nothing else: the work the previous run did is in the base, and the
  # base still carries it (the run removed that run's branch, because the run merged it).
  git -C "$p" ls-tree -r --name-only main | grep -q 'openspec/changes/archive/2026-01-01-fix-demo/proposal.md' ||
    fail "the repair discarded the work the previous run did"
}

# A start the worktree refuses mid-run is the *previous* item's leftover, not the candidate's failure:
# `start-item` refuses a dirty tree ("one writer per tree") and the run's catch-all arm ended the whole run on it.
# The item whose work the tree held was already recorded for another reason, the leftover was attributed nowhere,
# and the item that could not start was left in the queue untouched. Measured 2026-10-23: the run stopped at
# 16:24 with `the item could not be started: loop: the worktree is not clean — refusing to start #270:` 166
# minutes into an 8 h budget, 7 eligible items unworked and #270 carrying no state entry at all (#284). The
# tree's own branch marker names its item, so the run records the leftovers there, escalates that item, names
# the files it found, and stops: the tree is still dirty and nothing is discarded without --reset-worktree, so
# no later candidate could start either.
case_run_aDirtyStartNamesTheItemWhoseWorkTheTreeHolds() {
  # files: tools/loop/run.sh tools/loop/state.sh tools/loop/worktree.sh tools/loop/lib.sh tools/pi-child-events.sh tools/loop/fixtures/scenarios/ tools/tooling.json
  local d p out f reason log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario two)
  # The dirt is staged where an item's own commit cannot take it: the stub installs a `pre-commit` hook that
  # refuses both of `end-item`'s commits, so #21 ends with its work still in the tree, as #274's round-2
  # correction was left behind. How the tree came to be dirty is not what this case decides — the refusal is.
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'h=$(git rev-parse --git-path hooks); mkdir -p "$h"; printf "#!/bin/sh\nexit 1\n" > "$h/pre-commit"; chmod +x "$h/pre-commit"'
  out=$(run_loop "$p" --items 2 --minutes 90 2>&1)
  f=$(state_file "$p")
  assert_eq "1" "$(jq -r '.items | length' "$f")" "the run records one item, not one per account of it"
  assert_eq "21" "$(jq -r '.items[0].issue' "$f")" "and that item is the one whose work the tree holds"
  reason=$(jq -r '.run.stop.reason // ""' "$f")
  assert_contains "$reason" "the worktree is not clean" "the stop reason says what the refusal was"
  assert_contains "$reason" "#21" "and names the item whose work the tree holds, not only the candidate"
  assert_contains "$reason" "demo-fix.txt" "with the files a reader has to work from"
  assert_contains "$reason" "#22" "the candidate that could not start is named too"
  # The leftover is recorded against the item that left it, and one item keeps one record: the reason #21 ended
  # and the work it still owes are one account, so the report cannot count one item twice.
  assert_eq "blocked" "$(jq -r '.items[0].result' "$f")" "the item that left the work is recorded blocked"
  assert_contains "$(jq -r '.items[0].note' "$f")" "still at the base commit" "its own reason is kept"
  assert_contains "$(jq -r '.items[0].note' "$f")" "leftovers" "and the leftovers join it instead of replacing it"
  assert_eq "agent/needs-decision" "$(jq -r '.items[0].escalatedLabel' "$f")" \
    "the escalation the run wrote is recorded with the item it belongs to"
  log=$(cat "$GH_STUB_RECORD")
  assert_contains "$log" "agent/needs-decision" "the leftover is escalated to a person"
  assert_contains "$log" "fix/issue-21-" "with the branch that holds it as the evidence"
  assert_eq "0" "$(grep -c 'issue close' <<<"$log" || true)" "nothing is closed on a tree nobody can start on"
  # The candidate did nothing wrong and is not recorded: it stands in the queue as its owner set it.
  assert_eq "1" "$(grep -c '^prompt: worker-prompt.md$' "$PI_STUB_LOG" || true)" \
    "the run starts no second child, because no second item can start"
  [ -f "$p/.pi/worktrees/loop/demo-fix.txt" ] || fail "the leftovers were discarded without --reset-worktree"
  assert_eq "0" "$(git -C "$p/.pi/worktrees/loop" rev-list --count main..HEAD)" \
    "and nothing was committed onto the branch"
}

case_run_aSkipClearsTheInProgressMark() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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

# The state is the only thing a person can open after a run was killed, and it named neither the item nor the
# change in flight: `state.sh start` — the one writer of `.current` — had no call site in `run.sh`, so every
# phase step recorded `issue: 0` and a state kept from a killed run carried `{"phase":"A"}` and nothing else.
# Measured 2026-10-22: the run started at 18:46 was killed at 19:19 while #249's round-2 reviewer was answering,
# and the kept `runs/2026-10-22T16:46:21Z.json` says `current: {"phase":"A"}` — the item was recoverable only
# from the worktree's branch marker, a file the state does not point at (issue #263). The case reads the state
# *while the child is blocked*, which is the state a killed run leaves behind, and again once the item ended.
case_run_stateNamesTheItemInProgress() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/state.sh tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p sf fifo pid i
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  sf=$(state_file "$p")
  fifo="$d/wait.fifo"; mkfifo "$fifo"
  set -m
  ( export PI_STUB_WAIT="$fifo"
    run_loop "$p" --items 1 --minutes 90 --review off > "$d/out" 2>&1 ) &
  pid=$!
  set +m
  for i in $(seq 1 200); do [ -e "$fifo.ready" ] && break; sleep 0.1; done
  if [ ! -e "$fifo.ready" ]; then
    kill -9 "$pid" 2>/dev/null
    fail "the stub child never started, so there is no item in flight to read the state about"
  fi
  # The case holds the fifo's writer side, so releasing a child never blocks and never leaves a writer behind.
  exec 8> "$fifo"
  assert_eq "21" "$(jq -r '.current.issue' "$sf" 2>/dev/null)" \
    "the state names the item while it is being worked"
  assert_eq "A" "$(jq -r '.current.phase' "$sf" 2>/dev/null)" "and the phase the run last recorded"
  assert_contains "$(run_state "$p" show 2>&1 | head -1)" "#21" \
    "the live view the run's own header names reads the same mark"
  printf 'go\n' >&8; exec 8>&-
  wait "$pid" || fail "the run itself failed"
  assert_eq "null" "$(jq -r '.current' "$sf")" "the mark comes off the state when the item ends"
  assert_eq "21" "$(jq -r '[.steps[].issue] | unique | .[0]' "$sf")" \
    "every step the run recorded names the item it was taken for"
  assert_eq "1" "$(jq -r '[.steps[].issue] | unique | length' "$sf")" \
    "no step is recorded against issue 0"
}

case_run_claimedGateWithoutEvidenceIsBlocked() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: FAIL'
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" "two failed rounds block the item"
  assert_eq "refused" "$(jq -r '.items[0].stopReason' "$(state_file "$p")")" \
    "and the record names the refusing round as the mechanism that stopped it (issue #280)"
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/report.sh tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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

# What the round a corrective refusal buys is handed (#245; the decision recorded on the item, option 3). The
# round exists to decide whether the refusing round's findings were answered, and the reading surface that
# answers it is the correction alone — `git diff <the refused round's HEAD>..HEAD` — plus the refusing report,
# not the whole change the earlier rounds already graded (#204's 435 KB / 9 935-line diff at a smaller scale).
# The assertions are the absences that make it so: the round-3 diff carries the correction's hunk and not the
# line it replaced, and the round-3 prompt carries the finding the round exists to decide.
case_run_aCorrectiveRoundIsHandedTheCorrections() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p f diff prompt first
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # Rounds 1 and 2 refuse, and round 2 states that every finding is subtractive, so the run buys a third round;
  # that round accepts the corrections.
  printf 'BLOCKING: the demo scenario names no case\nLOOP-BLOCKING: 1\nLOOP-CORRECTIONS-ONLY: yes\nLOOP-DECISION: merge as it stands, apply the corrections, or abandon it | recommended: apply the corrections\nVERDICT: FAIL\n' \
    > "$d/reviewer-fail.txt"
  printf 'LOOP-BLOCKING: 0\nVERDICT: PASS\n' > "$d/reviewer-pass.txt"
  # `the first pass` is what round 2 leaves in the file; `the correction` is what round 3 replaces it with. The
  # two correction passes carry the same prompt name (`worker-prompt-round2.md`), so the marker that tells them
  # apart lives in `build/`, which is gitignored and therefore in neither diff. The stubs' paths are expanded
  # here, at write time, and only the runtime's own `$1` and `$PI_STUB_REPORT_REVIEWER` reach the action.
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' "case \"\$1\" in
  worker-prompt-round2.md)
    if [ -e build/correction-seen ]; then
      printf \"the fix\\nthe first pass\\nthe correction\\n\" > demo-fix.txt
    else
      printf \"the fix\\nthe first pass\\n\" > demo-fix.txt
      mkdir -p build && : > build/correction-seen
    fi
    ;;
esac
case \"\$1\" in
  reviewer-prompt-1.md|reviewer-prompt-2.md) cp \"$d/reviewer-fail.txt\" \"\$PI_STUB_REPORT_REVIEWER\" ;;
  reviewer-prompt-3.md) cp \"$d/reviewer-pass.txt\" \"\$PI_STUB_REPORT_REVIEWER\" ;;
esac"
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  f=$(state_file "$p")
  assert_eq "3" "$(jq -r '.items[0].metrics.reviewRounds' "$f")" "the corrective refusal bought the third round"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$f")" "and the round that answered the findings closes the item"
  # The round before the extension is the whole change, so the difference the next round's diff shows is the
  # extension's alone.
  diff="$p/.pi/issue-loop/items/21/review-2.diff"
  [ -f "$diff" ] || fail "the second round was handed no diff"
  assert_contains "$(cat "$diff")" "the first pass" "the round at the cap reads the change"
  # …and the round the refusal bought is the correction round alone.
  diff="$p/.pi/issue-loop/items/21/review-3.diff"
  [ -f "$diff" ] || fail "the round the corrective refusal bought was handed no diff"
  assert_contains "$(cat "$diff")" "+the correction" "the extra round reads the corrections"
  assert_not_contains "$(cat "$diff")" "+the first pass" \
    "and not the line the corrections replaced, which the refusing round already graded — as context it may sit beside the hunk, as a change it may not"
  # …and what it must decide travels with it: the refusing round's finding, quoted in the prompt.
  prompt="$p/.pi/issue-loop/items/21/reviewer-prompt-3.md"
  [ -f "$prompt" ] || fail "the extra round's prompt was not rendered"
  assert_contains "$(cat "$prompt")" "the demo scenario names no case" \
    "the prompt carries the finding the round exists to decide"
  assert_contains "$(cat "$prompt")" "reviewer-report-2.md" "and names the report it came from"
  assert_contains "$(cat "$prompt")" "the correction round alone" "and says what the diff is"
  # …and the section's own placeholders are rendered, not literal: `loop_render` substitutes one pair at a time on
  # the accumulated template, so a section carrying `{{DIFF_PATH}}` that is inserted *after* that pair hands the
  # reviewer a file called `{{DIFF_PATH}}` — the defect this assertion exists to catch (item #245's round-1 review).
  assert_contains "$(cat "$prompt")" "review-3.diff" "and names the diff it was handed"
  assert_not_contains "$(cat "$prompt")" "{{" "every placeholder in the correction section is substituted"
  # The earlier rounds are unchanged: the narrowing is the extra round's, not every round's.
  first="$p/.pi/issue-loop/items/21/reviewer-prompt-1.md"
  [ -f "$first" ] || fail "the first round's prompt was not rendered"
  assert_not_contains "$(cat "$first")" "the correction round alone" \
    "a first round is handed the change, not a correction range"
  assert_contains "$(cat "$first")" "review-1.diff" "and it names its own diff"
  assert_not_contains "$(cat "$first")" "{{" "and every placeholder in it is substituted too"
}

# A refusal is a refusal only when something the change depends on is contradicted. A report that grades every
# finding as a note and counts zero blocking findings is a pass whose notes are recorded: the item closes, one
# round was spent, and the notes travel to the item instead of buying a second reviewer process (#205). The
# report also asks for corrections, which is the shape of a refusal: there is none here, so no round is bought.
case_run_aZeroBlockingRefusalClosesWithItsNotes() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/report.sh tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/report.sh tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/issues/fixtures/login.txt tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  # A container or a CI runner has no `user.name`/`user.email` anywhere: git then refuses every commit, and
  # the run would block on its own commit (measured 2026-10-18, issue #167). The throwaway repository's local
  # identity is removed as well, so the case is the runner's environment and not the developer's.
  local d p login work author base_before
  d=$(scratch); p=$(new_project)
  git -C "$p" config --unset user.name >/dev/null 2>&1 || true
  git -C "$p" config --unset user.email >/dev/null 2>&1 || true
  printf '[init]\n\tdefaultBranch = main\n' > "$d/nohome.gitconfig"
  mkdir -p "$d/nohome"
  export GIT_CONFIG_GLOBAL="$d/nohome.gitconfig" GIT_CONFIG_SYSTEM=/dev/null HOME="$d/nohome"

  base_before=$(git -C "$p" rev-parse main)
  run_one_item "$p" "$d" >/dev/null || fail "a machine without an ambient git identity could not run an item"
  assert_eq "done" "$(jq -r '.items[0].result' "$(state_file "$p")")" "the item completed"

  # The two commits carry the run's own identity: the login the gate preflight resolved (the stub `gh` answers
  # it), as that login's noreply address.
  login=$(tr -d '\n' < "$LOOP_ISSUE_FIXTURES/login.txt")
  # The commits are counted from the base the run started on, not from the base branch: the run merges the
  # item's branch before it closes the item and removes that branch, so `main` is the item's own head by the
  # time this is read (#229). The review round's own two commits (#262) sit beside the fix and its archive and
  # carry the same identity, which is why the work commits are counted by their subject and the author check
  # reads every commit the run made, the round's included.
  work=$(git -C "$p" log --format=%s "$base_before..main" 2>/dev/null | grep -c '^fix: ' || true)
  [ "$work" -eq 2 ] || fail "the run did not commit the fix and its archive (fix commits since the run's base: $work)"
  author=$(git -C "$p" log --format='%an <%ae>' "$base_before..main" | sort -u)
  assert_eq "$login <$login@users.noreply.github.com>" "$author" "every commit, the review round's included, is attributed to the run's identity"
}

case_run_emptyQueueIsNotAFailure() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/check-portability.sh tools/loop/fixtures/scenarios/ tools/loop/loop.conf tools/loop/prompts/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/issues/create.sh tools/loop/prescreen.sh tools/loop/run.sh tools/loop/state.sh tools/loop/worktree.sh tools/mutate.sh
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
  # files: tools/issues/create.sh tools/loop-selftest.sh tools/loop/run.sh tools/sensor-selftest.sh
  local pair flag line out status
  for pair in \
    '--push:tools/loop/run.sh --items 100 --push --dry-run' \
    '--finding:tools/issues/create.sh --finding --class bug' \
    '--case:bash tools/sensor-selftest.sh --case --list' \
    '--group:bash tools/loop-selftest.sh --group --list'; do
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
  # files: tools/build-inputs-mutations.sh tools/build-inputs-selftest.sh tools/check-portability-selftest.sh tools/issue-tooling-selftest.sh tools/issues/check-metadata-selftest.sh tools/issues/metadata-mutations.sh tools/loop-selftest.sh tools/resolve-stale-selftest.sh tools/sensor-mutations.sh tools/sensor-selftest.sh
  local line flag out status
  for line in \
    'bash tools/loop-selftest.sh --case' \
    'bash tools/loop-selftest.sh --group' \
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
  # files: *
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
  # files: tools/pi-child-events.sh
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/report.sh tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local d p out f base tip reason
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario six)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' 'git -C "$LOOP_CHILD_ROOT" commit -q --allow-empty -m "advance main"'
  out=$(run_loop "$p" --items 2 --minutes 90 2>&1)
  f=$(state_file "$p")
  assert_contains "$out" "base moved" "the refusal is printed"
  assert_eq "1" "$(jq -r '.items | length' "$f")" "the item that was in flight is recorded"
  reason=$(jq -r '.run.stop.reason // ""' "$f")
  assert_contains "$reason" "the base moved" "and the reason is recorded, so the report can name why the run ended"
  # What the run knows, it names, and what it does not know, it does not assert: both commits are on the record
  # (`check_base` printed both), and the reason adds no claim about *when* the base moved — a run opened from a
  # feature branch was told `main` had advanced "while this run was open" although it had advanced before the
  # run began (issue #301).
  base=$(run_state "$p" get run.base)
  tip=$(git -C "$p" rev-parse main)
  assert_contains "$reason" "${base:0:12}" "the recorded reason names the base the run recorded"
  assert_contains "$reason" "${tip:0:12}" "and the commit the base branch stands at"
  assert_not_contains "$reason" "while this run was open" "it asserts nothing about when the base moved"
  return 0
}

# The pre-screen's decision exclusions reach the tracker through one path (`escalate_prescreen_decisions`), and
# an item that already carries the escalation label is not a new decision: escalating it again posts a second
# identical comment, which is what #110 collected three of (#183).
case_run_awaitingDecisionIsNotEscalatedAgain() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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

# A branch the run committed for an item and merged is done with: the work is in the base, and the branch
# itself is the run's own leftover. Measured 2026-10-22: 43 of the 53 local branches were already merged
# into the base, and nothing removed them — the run creates a branch per item and never took one away.
case_run_aMergedBranchIsRemoved() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p w branch head
  d=$(scratch); p=$(new_project); w="$p/.pi/worktrees/loop"
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  branch=$(git -C "$p" branch --list 'fix/issue-*' --format='%(refname:short)')
  [ -z "$branch" ] || fail "the run left the branch of the item it closed behind: $branch"
  # The work is in the base — the branch is gone, the fix is not.
  git -C "$p" ls-tree -r --name-only main | grep -q 'openspec/changes/archive/2026-01-01-fix-demo/proposal.md' ||
    fail "the item's change is not in the base"
  # The worktree was released from the branch, not broken by it: it is on no branch of its own, and the next
  # item still cuts its own branch from the base.
  head=$(git -C "$w" rev-parse --abbrev-ref HEAD 2>/dev/null)
  assert_eq "HEAD" "$head" "the worktree is not on the branch that was removed"
}

# A branch the loop made is a leftover like any other: the one command that answers "what has the loop left
# behind" removes the branches whose patches are in the base, and keeps the ones that carry work and the ones
# a worktree holds.
case_clean_sweepsOnlyWhatIsInTheBase() {
  # files: tools/loop/loop-clean.sh tools/pi-child-events.sh tools/tooling.json
  local p out
  p=$(new_project)
  # A branch whose patch is in the base: it was merged, so it carries nothing.
  git -C "$p" switch -c fix/issue-1-landed >/dev/null 2>&1 || fail "could not create the branch"
  printf 'the fix\n' > "$p/landed.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" commit -qm "fix: the landed one (#1)" >/dev/null 2>&1 || fail "could not commit"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not switch back"
  git -C "$p" merge --ff-only fix/issue-1-landed >/dev/null 2>&1 || fail "could not merge"
  # A branch that carries work the base does not have.
  git -C "$p" switch -c fix/issue-2-work >/dev/null 2>&1 || fail "could not create the branch"
  printf 'the work\n' > "$p/work.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" commit -qm "fix: the work (#2)" >/dev/null 2>&1 || fail "could not commit"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not switch back"
  # A branch a worktree holds: a tree in use.
  git -C "$p" worktree add -b fix/issue-3-held "$p/.pi/worktrees/held" main >/dev/null 2>&1 || fail "could not add the worktree"

  out=$(run_clean "$p" 2>&1) || fail "the report failed"
  assert_contains "$out" "would remove fix/issue-1-landed" "the landed branch is named as removable"
  assert_contains "$out" "kept fix/issue-2-work" "the branch that carries work is kept"
  assert_contains "$out" "kept fix/issue-3-held" "the branch a worktree holds is kept"
  git -C "$p" rev-parse --verify --quiet refs/heads/fix/issue-1-landed >/dev/null ||
    fail "the report removed a branch"

  out=$(run_clean "$p" --apply 2>&1) || fail "the sweep failed"
  assert_contains "$out" "removed fix/issue-1-landed" "the landed branch is removed"
  git -C "$p" rev-parse --verify --quiet refs/heads/fix/issue-1-landed >/dev/null 2>&1 &&
    fail "the landed branch survived the sweep"
  git -C "$p" rev-parse --verify --quiet refs/heads/fix/issue-2-work >/dev/null ||
    fail "the branch that carries work was removed"
  git -C "$p" rev-parse --verify --quiet refs/heads/fix/issue-3-held >/dev/null ||
    fail "the branch a worktree holds was removed"
}

# The report's branch list is a flat list of names, and a name says nothing: the reader cannot tell the
# leftovers from the work. The report asks the same question the action list asks — what does this branch
# carry — and answers it twice: a branch that carries work is an action, a branch that carries nothing is
# named as removable.
case_report_landedBranchesAreNamedAsRemovable() {
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out
  p=$(new_project)
  git -C "$p" switch -c fix/issue-1-landed >/dev/null 2>&1 || fail "could not create the branch"
  printf 'the fix\n' > "$p/landed.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" commit -qm "fix: the landed one (#1)" >/dev/null 2>&1 || fail "could not commit"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not switch back"
  git -C "$p" merge --ff-only fix/issue-1-landed >/dev/null 2>&1 || fail "could not merge"
  git -C "$p" switch -c fix/issue-2-work >/dev/null 2>&1 || fail "could not create the branch"
  printf 'the work\n' > "$p/work.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" commit -qm "fix: the work (#2)" >/dev/null 2>&1 || fail "could not commit"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not switch back"
  init_state "$p" 1 90

  out=$(run_report "$p" 2>&1) || fail "the report failed"
  assert_contains "$out" "landed, removable:" "the report tells the leftovers from the work"
  assert_contains "$out" "fix/issue-1-landed" "the landed branch is named as removable"
  assert_contains "$out" "land fix/issue-2-work" "the branch that carries work is an action"
  assert_not_contains "$out" "land fix/issue-1-landed" "and the landed branch is not an action"
}

# A run's report of what it did is the run's product, and a branch it made is part of it.
case_report_headerAndTableShape() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/report.sh tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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

# A run that was killed cannot record why it ended, so the report has to tell it apart from a run that is
# going: no budget left is printed for it — the clock since its start is not its spend any more — and its
# stop line names the process that is not running (measured 2026-10-22: the run killed at 19:19 left a
# state that read `not stopped — the run is still going` with 217 minutes of budget left).
case_report_aKilledRunIsNotALiveRun() {
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p sf out dead
  p=$(new_project); sf=$(state_file "$p")
  dead=999999999
  init_state "$p" 2 90
  # the state names the process that wrote it, and that process is not running any more
  jq --argjson pid "$dead" '.run.ownerPid = $pid' "$sf" > "$sf.tmp" && mv "$sf.tmp" "$sf" ||
    fail "could not write the state"

  out=$(run_report "$p" 2>/dev/null) || fail "the report failed"
  assert_not_contains "$out" "the run is still going" "a killed run is not a run that is going"
  assert_contains "$out" "is gone" "the report names the state whose process is not running"
  assert_contains "$out" "$dead" "and names the process"
  assert_not_contains "$out" "budget left 2 items" "a killed run prints no budget left"

  # A run that recorded its end is that run's own report, however long ago it ended.
  run_state "$p" stop "queue empty" >/dev/null || fail "the stop could not be recorded"
  out=$(run_report "$p" 2>/dev/null) || fail "the report failed"
  assert_contains "$out" "stop: queue empty" "a run that recorded its end reports its own end"
  assert_not_contains "$out" "is gone" "and is not called a run that is gone"
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
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out header row_done row_blocked
  p=$(new_project)
  init_state "$p" 2 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result done --verdict closed --wall 754 \
    --gate "tests=1 failures=0" --root-cause "the fixture has exactly one cause" >/dev/null
  run_state "$p" done 22 --result blocked --verdict blocked --stop-reason refused \
    --note "two mechanisms explain the symptom" --decision-label agent/needs-decision >/dev/null
  out=$(run_report "$p") || fail "the report failed"
  header=$(printf '%s\n' "$out" | grep '^| issue |' || true)
  assert_contains "$header" "| wall |" "the table has a column for the item's wall time"
  assert_contains "$header" "| reason |" "and one for the reason a non-done result carries"
  assert_contains "$header" "| stop |" "and one for the mechanism a block stopped on (issue #280)"
  row_done=$(printf '%s\n' "$out" | grep '^| #21 |' || true)
  row_blocked=$(printf '%s\n' "$out" | grep '^| #22 |' || true)
  [ -n "$row_done" ] || fail "no table row for #21"
  assert_contains "$row_done" "12m34s" "754 s is printed as the item's wall time"
  assert_contains "$row_done" "| done |" "and the row still says how the item ended"
  assert_contains "$row_blocked" "two mechanisms explain the symptom" "the blocked item's own reason is printed"
  assert_contains "$row_blocked" "| blocked |" "beside its outcome"
  # The mechanism is a column of its own, so the spend beside it is totalled by mechanism without reading the
  # note: a closure has none, a block names the one that stopped it.
  assert_contains "$row_blocked" "| refused |" "and the mechanism that stopped it, in a column of its own"
  assert_contains "$row_done" "| done | - |" "while a closure carries no mechanism"
  # Fifteen columns, so sixteen separators: the wall, the reason and the three spend columns were added, then
  # the block's mechanism, and nothing was dropped.
  assert_eq "16" "$(printf '%s' "$row_done" | grep -o '|' | wc -l | tr -d ' ')" \
    "the row keeps its fifteen columns"
  assert_eq "16" "$(printf '%s' "$row_blocked" | grep -o '|' | wc -l | tr -d ' ')" \
    "the blocked row has the same shape"
}

# A provider that does not report usage leaves zeros in the stream, and a zero printed as a figure reads as a
# measurement nobody made: the row says `-` where nothing was reported. The event stream in this case is a
# complete turn whose usage is zero, which is what a non-reporting provider leaves behind (issue #211).
case_report_zeroUsagePrintsDashNotZero() {
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/loop-clean.sh tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
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
  # files: tools/loop/loop-clean.sh tools/pi-child-events.sh tools/tooling.json
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

# The scratch trees a suite leaves are made under the root of the tree the suite runs *in*, and the gate runs
# `tools/loop-selftest.sh` inside the kept worktree — so the trees that accumulate are under the worktree's own
# `.pi/tmp`, which a report run from the project never looked in (issue #274). Measured 2026-10-23: four
# `loop-selftest.*` trees there, 305 MB in total, three of them past the 120-minute threshold, and
# `loop-clean` printed `self-test scratch trees under .pi/tmp` with no entry; four `run.sh` poll loops stood in
# them at 6 h 53 min to 8 min of age, three long past their 90-minute budget. The report scans both roots with
# the same age rule and the same process evidence, so a tree a live suite is writing into is still kept.
case_clean_reportsTheWorktreesScratchTreesToo() {
  # files: tools/loop/loop-clean.sh tools/loop/lib.sh tools/loop/loop.conf tools/loop-selftest.sh tools/loop-cases.sh tools/loop/fixtures/scenarios/ tools/tooling.json tools/pi-child-events.sh
  local d p w tmp conf out stale i alive
  d=$(scratch); p=$(new_project); w="$p/.pi/worktrees/loop"; tmp="$d/tmp"
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  mkdir -p "$w/.pi/tmp/loop-selftest.stale" "$w/.pi/tmp/loop-selftest.fresh" "$tmp"
  touch -d '3 hours ago' "$w/.pi/tmp/loop-selftest.stale"
  conf="$d/loop.conf"
  sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$tmp\"|" "$LOOP_CONF" > "$conf" || fail "could not write the loop configuration"
  export LOOP_CONF="$conf"
  # The process half reads the same evidence in the same tree: a process standing inside the worktree's stale
  # tree is what a killed suite left, and one in its fresh tree is a live suite's.
  ( cd "$w/.pi/tmp/loop-selftest.stale" && exec sleep 300 ) &
  stale=$!
  CASE_SLEEPS="$stale"
  trap 'kill -9 $CASE_SLEEPS 2>/dev/null; CASE_SLEEPS=""' EXIT

  out=$(run_clean "$p") || fail "loop-clean failed: $out"
  assert_contains "$out" "self-test scratch trees under .pi/worktrees/loop/.pi/tmp" \
    "the report names the kept worktree's own scratch root beside the invocation's"
  assert_contains "$out" "loop-selftest.stale" "and the tree a killed suite left there"
  assert_contains "$out" "would end process $stale" "and the process standing in it"
  assert_contains "$out" "loop-selftest.fresh" "while the tree a live suite may be writing into is kept"
  [ -d "$w/.pi/tmp/loop-selftest.stale" ] || fail "the report removed a scratch tree without --apply"

  out=$(run_clean "$p" --apply) || fail "loop-clean --apply failed: $out"
  assert_contains "$out" "removed" "--apply removes what it named"
  [ ! -e "$w/.pi/tmp/loop-selftest.stale" ] || fail "the worktree's stale scratch tree survived --apply"
  [ -d "$w/.pi/tmp/loop-selftest.fresh" ] || fail "--apply removed a tree younger than the stale threshold"
  alive=1
  for i in $(seq 1 50); do kill -0 "$stale" 2>/dev/null || { alive=0; break; }; sleep 0.1; done
  [ "$alive" -eq 0 ] || fail "the process inside the worktree's tree survived --apply"
  [ -f "$w/demo-fix.txt" ] || fail "the cleanup removed a source file from the worktree"
}

# `--worktree .` names the tree the report is run from, and then the invocation's scratch root and the one that tree
# resolves are one directory written two ways (`$LOOP_ROOT/.` against `$LOOP_ROOT`). Compared as strings the two never
# match, so the same trees would be listed twice and their bytes counted twice while `--apply` removes them once — a
# report whose total is a lie (issue #274, round-1 review). `--apply` hides it (the second pass finds the tree already
# gone), so the report is what this case reads: one heading, one entry, one count.
case_clean_worktreeDotSweepsTheOneRootOnce() {
  # files: tools/loop/loop-clean.sh tools/loop/lib.sh tools/loop/loop.conf tools/loop-selftest.sh tools/loop-cases.sh tools/loop/fixtures/scenarios/ tools/tooling.json tools/pi-child-events.sh
  local d p out headings entries
  d=$(scratch); p=$(new_project)
  mkdir -p "$p/.pi/tmp/loop-selftest.stale"
  printf 'a killed suite left this\n' > "$p/.pi/tmp/loop-selftest.stale/file"
  touch -d '3 hours ago' "$p/.pi/tmp/loop-selftest.stale/file" "$p/.pi/tmp/loop-selftest.stale"

  out=$(run_clean "$p" --worktree .) || fail "loop-clean --worktree . failed: $out"
  headings=$(printf '%s\n' "$out" | grep -c 'self-test scratch trees under' || true)
  assert_eq 1 "$headings" "--worktree . names this tree, so its one scratch root gets one heading"
  entries=$(printf '%s\n' "$out" | grep -c 'would remove loop-selftest.stale' || true)
  assert_eq 1 "$entries" "and the tree a killed suite left is listed once, not once per way of spelling the root"
  assert_contains "$out" "would remove 1 path(s)" "and counted once"
}

# A run ended by a signal ends the child it started (issue #213). The child is put in a process group of its own
# so the wall-clock cap can end it *and its descendants* (#203), which means the run's own traps — the path a
# person's `kill` takes mid-item — used to end the run and leave the child working. The stub child blocks on a
# fifo, which is a child in the middle of its phase, and both the run and the child must be gone afterwards.
case_run_interruptedRunEndsTheChildItStarted() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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

# A process still standing in a scratch tree is a leftover whatever its tree's state, and the kernel shows a
# deleted tree with ` (deleted)` appended to the cwd it reports. That suffix is why the leftovers of a killed
# suite matched neither `loop-clean.sh`'s tree test nor its process test (issues #213, #233): the run was there,
# at a path that no longer existed, and was invisible to both.
case_clean_endsARunWhoseTreeIsAlreadyDeleted() {
  # files: *
  local d tree pid seen survived
  d=$(scratch); tree="$d/suite.tree/case.1"
  mkdir -p "$tree" || fail "could not create the scratch tree"
  ( cd "$tree" && exec sleep 300 ) &
  pid=$!
  seen=""
  for _ in $(seq 1 100); do
    seen=$( loop_pids_under "$d/suite.tree" )
    [ -n "$seen" ] && break
    sleep 0.1
  done
  [ -n "$seen" ] || { loop_end_pid "$pid"; fail "a process standing in the scratch tree was not reported before the tree was removed"; }

  # …and with the tree gone, the same test still finds it: the cwd `/proc` reports carries the kernel's marker.
  rm -rf "$d/suite.tree"
  seen=$( loop_pids_under "$d/suite.tree" )
  [ -n "$seen" ] || { loop_end_pid "$pid"; fail "a process whose tree is already deleted was not reported — the leftovers of a killed suite are exactly those"; }

  loop_end_pids_under "$d/suite.tree" --quiet
  survived=1
  for _ in $(seq 1 50); do
    kill -0 "$pid" 2>/dev/null || { survived=0; break; }
    sleep 0.1
  done
  [ "$survived" -eq 0 ] || { loop_end_pid "$pid"; fail "the sweep reported the process but did not end it (pid $pid)"; }
}

# A suite that ends *normally* left the `run.sh` its case had started behind (issue #233): `run_case` clears
# `case_pgid` once the last case has been waited for, so the normal exit had no group left to signal, and a process
# the case had put in a group of its own — the shape `run_child` needs for the wall-clock cap — outlived its suite
# by hours at a tree that was then removed (44 of them, 2 to 4.6 h old, a `sleep 1` child each). `on_exit` now sweeps
# the suite's own scratch root on every exit path; this case is the normal one, with no signal at all: the nested
# case starts a process in a group of its own at a working directory inside the suite's scratch root and returns
# while it polls, and the suite must end it before it removes the tree.
case_clean_suiteThatEndsNormallyEndsTheProcessItsCaseLeft() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d nested tmp conf pid status out alive i
  d=$(scratch); nested="$d/nested"; tmp="$d/tmp"
  mkdir -p "$nested/tools" "$tmp"
  cp "$LOOP_PROJECT_ROOT/tools/loop-selftest.sh" "$nested/tools/loop-selftest.sh" || fail "could not copy the driver"
  ln -s "$LOOP_ENGINE" "$nested/tools/loop"
  ln -s "$LOOP_PROJECT_ROOT/tools/issues" "$nested/tools/issues"
  # The probe case leaves the process behind on purpose: `set -m` gives it a process group of its own — the shape
  # `run_child` uses, which the group signal `on_exit` sends for the case cannot reach once the case is gone — and
  # its working directory is the suite's own scratch tree (`LOOP_TMP`), which is what the sweep reads.
  cat > "$nested/tools/loop-cases.sh" <<'PROBE'
LOOP_SHELL_CASES=('stub#leavesAProcessBehind')
loop_shell_cases() { printf '%s\n' "${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() {
  cd "$LOOP_TMP" || exit 1
  set -m
  sleep 300 &
  printf '%s\n' "$!" > "$PROBE_PID"
  exit 0
}
PROBE
  conf="$d/loop.conf"
  sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$tmp\"|" "$LOOP_CONF" > "$conf" || fail "could not write the loop configuration"
  export LOOP_CONF="$conf" PROBE_PID="$d/probe-pid"

  out=$(bash "$nested/tools/loop-selftest.sh" --case 'stub#leavesAProcessBehind' 2>&1); status=$?
  assert_eq "0" "$status" "a suite whose case leaves a process behind still ends normally: $out"
  [ -f "$PROBE_PID" ] || fail "the probe case never recorded the process it left behind: $out"
  pid=$(head -1 "$PROBE_PID")
  case "$pid" in ''|*[!0-9]*) fail "the probe case recorded no pid: $(cat "$PROBE_PID")" ;; esac
  alive=1
  for i in $(seq 1 50); do
    kill -0 "$pid" 2>/dev/null || { alive=0; break; }
    sleep 0.1
  done
  [ "$alive" -eq 0 ] || { kill -9 "$pid" 2>/dev/null; fail "the suite ended normally and left the process its case started (pid $pid) polling at a tree it then removed — the leftover of issue #233"; }
}

# A declared mutation record quotes one line of one engine file, and nothing compared the two until issue #264:
# `9.9` and `12.2` had quoted `run.sh`'s child-start line as #227 wrote it before that line was given the child's
# own completion record, so both records reported `MUTATION-DID-NOT-APPLY` and the two invariants they own — the
# single agent-command indirection, the child started in the project — were asserted by nothing. The check reads
# the set the engine declares and the files that set names. It runs no case, which is what lets the gate owe it.
case_mutation_everyDeclaredRecordApplies() {
  # files: tools/loop-mutations.sh
  local declared out status
  declared=$(sed -n '/^MUTATIONS=(/,/^)/p' "$LOOP_ENGINE/../loop-mutations.sh" | grep -c "^  '")
  [ "$declared" -gt 0 ] || fail "could not read the declared set out of $LOOP_ENGINE/../loop-mutations.sh"
  out=$(bash "$LOOP_ENGINE/../loop-mutations.sh" --check --engine "$LOOP_ENGINE" 2>&1)
  status=$?
  assert_eq "0" "$status" "every declared record applies to the file it names — the check printed: $out"
  assert_not_contains "$out" "MUTATION-PATTERN-STALE" "no record of the set is stale: $out"
  assert_contains "$out" "all $declared cases passed" "and the count is the set the harness declares: $out"
  return 0
}

# …and the check has to be able to fail, on the shape the defect had: a record whose expression names no line of
# the file it is pointed at mutates less than it declares, and `RED` for a partly applied mutation is the silent
# half of the same lie. The stale record is built in a throwaway copy of the set, so no case edits the project's
# own records.
case_mutation_aStaleRecordIsNamed() {
  # files: tools/loop-mutations.sh
  local d harness at label file out status
  d=$(scratch)
  harness="$d/loop-mutations.sh"
  cp "$LOOP_ENGINE/../loop-mutations.sh" "$harness" || fail "could not copy the mutation set"
  # The first record of the declared set, read from the set rather than spelled here: a record that is renumbered
  # moves this case with it instead of turning it red for a reason nobody is looking for.
  at=$(grep -n '^MUTATIONS=(' "$harness" | cut -d: -f1)
  at=$((at + 1))
  label=$(sed -n "${at}s/^  '\([^@]*\)@.*/\1/p" "$harness")
  file=$(sed -n "${at}s/^  '[^@]*@[^@]*@\([^@]*\)@.*/\1/p" "$harness")
  [ -n "$label" ] && [ -n "$file" ] || fail "could not read the first record of the declared set"
  # One more expression, naming a line the file does not have: the record now applies only in part.
  sed -i "${at}s#'\$#;;s|^a line this file does not have\$|  mutation|'#" "$harness" || fail "could not stale the record"
  out=$(TMPDIR="$d" bash "$harness" --check --only "$label" --engine "$LOOP_ENGINE" 2>&1)
  status=$?
  assert_eq "1" "$status" "a record whose expression names no line fails the check: $out"
  assert_contains "$out" "MUTATION-PATTERN-STALE $label" "and the record is named by its own label: $out"
  case "$out" in
    *"MUTATION-PATTERN-STALE $label"*"$file"*) ;;
    *) fail "the stale record's file should be named with it: $out" ;;
  esac
  return 0
}

# The check is the gate's, not a hand-run's (issue #264): `tools/loop/gate.sh` ran the harness's own cases for a
# path under the engine and never the records against the files they name, so a record that stopped applying sat
# unnoticed until somebody ran the whole set by hand. The profile's suite map is what owes it for an engine path.
case_gate_anEnginePathOwesTheRecordCheck() {
  # files: tools/loop/ tools/pi-child-events.sh tools/tooling.json
  local d p plan base
  d=$(scratch); p=$(new_project)
  mkdir -p "$p/tools/loop"
  printf '# an engine script\n' > "$p/tools/loop/run.sh"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'an engine change' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  plan=$( cd "$p" && LOOP_CONF="$LOOP_ENGINE/loop.conf" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )
  assert_contains "$plan" "loop-mutations.sh --check" \
    "a path under the engine owes the record check, so a stale record fails the gate: $plan"
  return 0
}

# ------------------------------------------------------- cases: the driver's own Java core

# The driver compiles the issue tooling's Java core when a case asks for it, not before the case list is known
# (issue #261: a `--case` run that narrowed one shell case paid a fixed second for a compile nothing used). The
# probe proves the compile did not happen rather than timing it: `javac` is a shim that records the invocation and
# fails, so a compile the selection does not need shows up as a failed run, and a compile that should have
# happened cannot pass silently.
case_driver_aCaseThatAsksForNoCoreCompilesNothing() {
  # files: tools/loop-selftest.sh
  local d bin log out status
  d=$(scratch); bin="$d/bin"; log="$d/javac.log"
  mkdir -p "$bin"
  cat > "$bin/javac" <<SHIM
#!/usr/bin/env bash
printf '%s\n' "\$*" >> "$log"
echo "javac: this probe does not compile anything" >&2
exit 1
SHIM
  chmod +x "$bin/javac"
  out=$(PATH="$bin:$PATH" bash "$LOOP_PROJECT_ROOT/tools/loop-selftest.sh" --case 'state#initAndShowRoundTrips' 2>&1); status=$?
  assert_eq "0" "$status" "a case that asks for no core runs without one: $out"
  assert_contains "$out" "PASS state#initAndShowRoundTrips" "and the case is the one that passed: $out"
  assert_eq "" "$(cat "$log" 2>/dev/null)" "the driver compiled nothing for it (javac was invoked: $(cat "$log" 2>/dev/null))"
  return 0
}

# The laziness removes neither the classpath nor the refusal: a case that asks for the core is handed the classes
# the driver compiled for it, and when the core cannot compile the suite ends the way it did before — exit 1, with
# the line that names the cause. The case runs in the driver's own scratch tree, so the compile is the only thing
# under test.
case_driver_aCaseThatAsksForTheCoreGetsItOrRefuses() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d nested tmp conf bin out status
  d=$(scratch); nested="$d/nested"; tmp="$d/tmp"
  mkdir -p "$nested/tools" "$tmp"
  cp "$LOOP_PROJECT_ROOT/tools/loop-selftest.sh" "$nested/tools/loop-selftest.sh" || fail "could not copy the driver"
  ln -s "$LOOP_ENGINE" "$nested/tools/loop"
  ln -s "$LOOP_PROJECT_ROOT/tools/issues" "$nested/tools/issues"
  cat > "$nested/tools/loop-cases.sh" <<'CASES'
LOOP_SHELL_CASES=('stub#asksForTheCore')
loop_shell_cases() { printf '%s\n' "${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() {
  declare -F core_ensure >/dev/null || {
    echo "the driver hands a case no way to ask for the core" >&2
    exit 1
  }
  core_ensure
  [ -f "${ISSUES_CORE_CLASSES:-/nonexistent}/Cases.class" ] || {
    echo "the driver handed the case no compiled class path: '${ISSUES_CORE_CLASSES:-}'" >&2
    exit 1
  }
}
CASES
  conf="$d/loop.conf"
  sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$tmp\"|" "$LOOP_CONF" > "$conf" || fail "could not write the loop configuration"
  export LOOP_CONF="$conf"

  # The suite this case runs inside may have compiled a core of its own (`ISSUES_CORE_CLASSES`); the nested
  # driver is asked to compile its own, so what it hands the case is the nested driver's work.
  out=$( ( unset ISSUES_CORE_CLASSES; bash "$nested/tools/loop-selftest.sh" --case 'stub#asksForTheCore' ) 2>&1); status=$?
  assert_eq "0" "$status" "a case that asks for the core is handed the compiled class path: $out"
  assert_contains "$out" "PASS stub#asksForTheCore" "and the case is the one that passed: $out"

  # A scratch root of its own, so the classes the first half compiled are not what this half runs on. The broken
  # configuration is a *second* file: writing it into `$conf` would truncate the file `sed` is reading, and the
  # nested driver would then be handed an empty one (its isolation held by `lib.sh`'s fallback, not by this case).
  broken_conf="$d/loop-broken.conf"
  bin="$d/bin"; mkdir -p "$bin" "$tmp/broken"
  cat > "$bin/javac" <<'SHIM'
#!/usr/bin/env bash
echo "javac: this probe does not compile anything" >&2
exit 1
SHIM
  chmod +x "$bin/javac"
  sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$tmp/broken\"|" "$conf" > "$broken_conf" || fail "could not write the loop configuration"
  out=$( ( unset ISSUES_CORE_CLASSES; LOOP_CONF="$broken_conf" PATH="$bin:$PATH" bash "$nested/tools/loop-selftest.sh" --case 'stub#asksForTheCore' ) 2>&1); status=$?
  assert_eq "1" "$status" "a case that asks for a core that cannot compile fails the suite: $out"
  assert_contains "$out" "the issue tooling's Java core does not compile" "and the refusal names the cause: $out"
  return 0
}

# The compile is once per *suite*, not once per asking case: the marker in the suite's own scratch tree is what a
# later asking case reads, because a case runs in a subshell of the driver and a variable that subshell exports
# does not come back to it. A suite with no marker is still green — every asking case would simply compile again —
# so the number of invocations has to be counted: `javac` on PATH is a probe that records each invocation, writes
# the class file the two stub cases look for, and exits 0.
case_driver_theCoreIsCompiledOncePerSuite() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d nested tmp bin log out status conf invocations
  d=$(scratch); nested="$d/nested"; tmp="$d/tmp"; bin="$d/bin"; log="$d/javac.log"
  mkdir -p "$nested/tools" "$tmp" "$bin"
  cp "$LOOP_PROJECT_ROOT/tools/loop-selftest.sh" "$nested/tools/loop-selftest.sh" || fail "could not copy the driver"
  ln -s "$LOOP_ENGINE" "$nested/tools/loop"
  ln -s "$LOOP_PROJECT_ROOT/tools/issues" "$nested/tools/issues"
  cat > "$nested/tools/loop-cases.sh" <<'CASES'
LOOP_SHELL_CASES=('stub#asksFirst' 'stub#asksSecond')
loop_shell_cases() { printf '%s\n' "${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() {
  declare -F core_ensure >/dev/null || {
    echo "the driver hands a case no way to ask for the core" >&2
    exit 1
  }
  core_ensure
  [ -f "${ISSUES_CORE_CLASSES:-/nonexistent}/Cases.class" ] || {
    echo "the driver handed the case $1 no compiled class path: '${ISSUES_CORE_CLASSES:-}'" >&2
    exit 1
  }
}
CASES
  cat > "$bin/javac" <<SHIM
#!/usr/bin/env bash
printf '%s\n' "\$*" >> "$log"
dir=""
while [ \$# -gt 0 ]; do
  if [ "\$1" = "-d" ]; then dir="\$2"; shift; fi
  shift
done
[ -n "\$dir" ] || { echo "javac: this probe needs a -d directory" >&2; exit 1; }
mkdir -p "\$dir" && : > "\$dir/Cases.class"
SHIM
  chmod +x "$bin/javac"
  conf="$d/loop.conf"
  sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$tmp\"|" "$LOOP_CONF" > "$conf" || fail "could not write the loop configuration"

  # Both stub cases are selected — the driver runs its whole two-case suite, which is the shape the invariant is
  # about. `ISSUES_CORE_CLASSES` is unset so what they are handed is the nested driver's own compile, and the
  # count is read from the probe's log rather than from anything the driver prints about itself.
  out=$( ( unset ISSUES_CORE_CLASSES; LOOP_CONF="$conf" PATH="$bin:$PATH" bash "$nested/tools/loop-selftest.sh" ) 2>&1); status=$?
  assert_eq "0" "$status" "a suite whose selection is two asking cases runs: $out"
  assert_contains "$out" "PASS stub#asksFirst" "and the first asking case ran, handed the compiled class path: $out"
  assert_contains "$out" "PASS stub#asksSecond" "and so did the second: $out"
  assert_contains "$out" "loop-selftest: all 2 cases passed" "and the suite reported both of them: $out"
  invocations=$(wc -l < "$log" 2>/dev/null | tr -d ' ')
  assert_eq "1" "${invocations:-0}" "the core is compiled once for the suite, not once per asking case (javac ran ${invocations:-0} times, recorded in $log)"
  return 0
}

# ------------------------------------------------------- cases: the suite's own selection

# A suite that selected nothing and reported success is the one answer the driver must not give (this change):
# `--group` names a group, `--case` takes a name or a pattern, and a filter that names no case is refused with
# exit 2 rather than running an empty suite that exits 0. The cases below drive a *copy* of the driver over a
# declared set of three cases in two groups, one of which fails if it is ever executed — so a selection that
# leaks is a red case, never a claim about one.
driver_selection_probe() { # <dir> → a nested suite whose cases record themselves and whose beta case fails
  local d="$1" nested="$1/nested"
  mkdir -p "$nested/tools"
  cp "$LOOP_PROJECT_ROOT/tools/loop-selftest.sh" "$nested/tools/loop-selftest.sh" \
    || fail "could not copy the driver"
  ln -s "$LOOP_ENGINE" "$nested/tools/loop"
  ln -s "$LOOP_PROJECT_ROOT/tools/issues" "$nested/tools/issues"
  # The cases file is the driver's only input about the set: every case records that it ran, and the two the
  # group filter must never reach fail loudly.
  cat > "$nested/tools/loop-cases.sh" <<CASES
LOOP_SHELL_CASES=('alpha#one' 'alpha#two' 'beta#three')
loop_shell_cases() { printf '%s\n' "\${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() {
  printf '%s\n' "\$1" >> "$d/ran"
  case "\$1" in beta#*) printf 'the selection leaked into beta\n'; return 1 ;; esac
  return 0
}
CASES
}

# `--group` runs that group's cases and no other group's, and `--case` with a pattern selects the same set: the
# two filters are one mechanism, so a change that narrows an iteration with either pays for what it selected.
case_driver_aGroupFilterRunsOnlyItsOwnCases() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d nested out rc
  d=$(scratch); driver_selection_probe "$d"; nested="$d/nested"
  out=$(bash "$nested/tools/loop-selftest.sh" --group alpha 2>&1); rc=$?
  assert_eq "0" "$rc" "the group's two cases pass: $out"
  assert_contains "$out" "— selection: --group alpha" \
    "and the summary names the selection it ran, so a green filtered run never reads like the whole suite: $out"
  assert_eq "$(printf 'alpha#one\nalpha#two')" "$(cat "$d/ran" 2>/dev/null)" \
    "exactly the group's cases ran, and no case of the other group: $(cat "$d/ran" 2>/dev/null)"
  : > "$d/ran"
  out=$(bash "$nested/tools/loop-selftest.sh" --case 'alpha#*' 2>&1); rc=$?
  assert_eq "0" "$rc" "the same selection by pattern passes: $out"
  assert_eq "$(printf 'alpha#one\nalpha#two')" "$(cat "$d/ran" 2>/dev/null)" \
    "the pattern selected the same two cases: $(cat "$d/ran" 2>/dev/null)"
  # Both filters at once are an intersection, not a union: a caller narrowing a group with a pattern gets the
  # cases that pass both, which is what makes `--group run --case 'run#*Cap*'` mean what it reads as.
  : > "$d/ran"
  out=$(bash "$nested/tools/loop-selftest.sh" --group alpha --case 'alpha#one' 2>&1); rc=$?
  assert_eq "0" "$rc" "a group narrowed by a pattern passes: $out"
  assert_eq "alpha#one" "$(cat "$d/ran" 2>/dev/null)" \
    "the intersection is one case, not the group: $(cat "$d/ran" 2>/dev/null)"
  return 0
}

# A filter that names nothing is a refusal, and it is refused before a case runs: a caller who mistyped a group
# would otherwise read exit 0 as "the cases I asked for all passed" — the same rule `tools/loop-mutations.sh`
# applies to a file filter that names no record.
case_driver_aFilterThatNamesNoCaseRefuses() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d nested out rc
  d=$(scratch); driver_selection_probe "$d"; nested="$d/nested"
  out=$(bash "$nested/tools/loop-selftest.sh" --group nope 2>&1); rc=$?
  assert_eq "2" "$rc" "an unknown group is a usage refusal: $out"
  assert_contains "$out" "no case carries the group: nope" "and it names the group it could not find: $out"
  [ ! -e "$d/ran" ] || fail "a refused group still ran cases: $(cat "$d/ran")"
  out=$(bash "$nested/tools/loop-selftest.sh" --case 'nope#*' 2>&1); rc=$?
  assert_eq "2" "$rc" "a pattern that names no case is a usage refusal: $out"
  assert_contains "$out" "no such case: nope#*" "and it names the pattern: $out"
  [ ! -e "$d/ran" ] || fail "a refused pattern still ran cases: $(cat "$d/ran")"
  return 0
}

# The list answers the same question the run does (this change): `--list --group` prints what that group would
# run, so a worker can size an iteration before paying for it, and an unfiltered list still prints the whole
# declared set.
case_driver_theListIsFilteredTheSameWayASuiteRunIs() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d nested out
  d=$(scratch); driver_selection_probe "$d"; nested="$d/nested"
  out=$(bash "$nested/tools/loop-selftest.sh" --list --group alpha 2>&1)
  assert_eq "$(printf 'alpha#one\nalpha#two')" "$out" \
    "the list is the group's cases and only them: $out"
  out=$(bash "$nested/tools/loop-selftest.sh" --list 2>&1)
  assert_eq "$(printf 'alpha#one\nalpha#two\nbeta#three')" "$out" \
    "and an unfiltered list is still the whole declared set: $out"
  [ ! -e "$d/ran" ] || fail "listing runs no case, and one ran: $(cat "$d/ran")"
  return 0
}

# One value of the profile the engine under test carries. A case that asked the gate with a profile of its own
# could not catch a wrong pattern, and the mutation harness points `LOOP_ENGINE` at a mutated copy of the engine.
profile_value() { # <key>
  ( . "$LOOP_ENGINE/loop.conf"; eval "printf '%s' \"\${$1:-}\"" )
}

# A compiled file is one a tree the build reads holds, so where a path sits decides it — never what its name ends
# in (issue #273). `*.xml` matched `openspec/changes/archive/<x>/evidence/gate-tallies/a.xml` and the gate-timings
# fixture that mimics a test report, so a delta that touches no compiled file owed the full Kotlin suite: measured
# 2026-10-23 finishing #262, `LOOP-GATE: tests=202 failures=0 (tools/loop-selftest.sh,
# tools/loop-mutations-selftest.sh, tools/check-doc-routes.sh)` in 13 min 15 s before the archive commit, and
# `./gradlew test -PforceTests --no-build-cache` first on the same tree after it, whose only matching paths were
# four evidence `.xml`.
case_gate_anArchivedArtefactOwesNoBuild() {
  # files: tools/fixtures/ tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local d p base plan compiled
  d=$(scratch); p=$(new_project)
  compiled=$(profile_value LOOP_GATE_COMPILED_CMD)
  [ -n "$compiled" ] || fail "the profile names no compiled command, so this case would prove nothing"

  mkdir -p "$p/openspec/changes/archive/x/evidence/gate-tallies"
  printf '<testsuite name="a" tests="1" failures="0"/>\n' \
    > "$p/openspec/changes/archive/x/evidence/gate-tallies/a.xml"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'an archived tally' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  plan=$( cd "$p" && LOOP_CONF="$LOOP_ENGINE/loop.conf" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )
  assert_not_contains "$plan" "$compiled" "an archived evidence tally owes no compiled command: $plan"
  assert_contains "$plan" "check-classification" "and the check the delta does owe is still named: $plan"

  # The fixture beside the build is the same defect in a second tree, which is why an exclusion for the archive
  # would have been an instance patched rather than the rule read. It is added with `-f`: the throwaway project
  # ignores every `build/` directory, and this path is one — in this repository the fixture is tracked.
  mkdir -p "$p/tools/fixtures/gate-timings/root/app/build/test-results/x"
  printf '<testsuite name="b" tests="1" failures="0"/>\n' \
    > "$p/tools/fixtures/gate-timings/root/app/build/test-results/x/TEST-b.xml"
  git -C "$p" add -f tools/fixtures >/dev/null 2>&1 && git -C "$p" commit -qm 'a gate fixture' >/dev/null 2>&1 \
    || fail "could not commit the second change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  plan=$( cd "$p" && LOOP_CONF="$LOOP_ENGINE/loop.conf" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )
  assert_not_contains "$plan" "$compiled" "a fixture that mimics a test report owes no compiled command: $plan"
  return 0
}

# The other half of the same rule: bounding the patterns to the trees the build reads must lose no tree a module
# compiles — and an anchored pattern is only a pattern when the matcher does not expand the list against the
# directory the gate runs in. Measured 2026-10-23 (issue #273): with the profile's own `app/src/main/res/*` in a
# tree that holds that directory, the plan owed `fake-build` with `set -f` in the matcher and nothing at all
# without it, because `for glob in $1` had already replaced the pattern with the names it matched.
case_gate_aModuleResourceOwesTheBuild() {
  # files: tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local d p base plan compiled
  d=$(scratch); p=$(new_project)
  compiled=$(profile_value LOOP_GATE_COMPILED_CMD)
  [ -n "$compiled" ] || fail "the profile names no compiled command, so this case would prove nothing"

  mkdir -p "$p/app/src/main/res/values"
  printf '<resources/>\n' > "$p/app/src/main/res/values/strings.xml"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'a module resource' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  plan=$( cd "$p" && LOOP_CONF="$LOOP_ENGINE/loop.conf" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )
  assert_contains "$plan" "$compiled" "a module's resource still owes the build: $plan"

  printf '<lint><issue id="UnusedResources"/>\n' > "$p/app/lint.xml"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'a lint configuration' >/dev/null 2>&1 \
    || fail "could not commit the second change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  plan=$( cd "$p" && LOOP_CONF="$LOOP_ENGINE/loop.conf" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )
  assert_contains "$plan" "$compiled" "a module's lint configuration still owes the build: $plan"
  return 0
}

# The gate's log names the profile it read (issue #277). A gate measures a rule, and its log said nothing about
# which one: the round-3 log of #273 carried no pin and named the compiled command first, while the very tree it
# was gating owed five shell checks and no build, so only a reader who knew the replaced profile by heart could
# tell the numbers came from it. The pin is a line of the log, and a profile read from outside the tree the engine
# sits in is named as pinned beside the one the engine ships.
case_gate_theGateNamesTheProfileItRead() {
  # files: tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local d p conf base out
  d=$(scratch); p=$(new_project)
  conf="$d/pinned.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  base=$(git -C "$p" rev-parse HEAD)

  # A profile pinned from outside the engine: the log names that file and says it is not the engine's own.
  out=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" 2>&1 )
  assert_contains "$out" "LOOP-GATE-PROFILE: $conf" "the log names the profile the gate read: $out"
  assert_contains "$out" "not the profile beside this engine" \
    "and says a pinned profile is not the one this engine ships: $out"

  # The profile beside the engine is the tree whose paths the gate measures, and naming it is not a pin.
  out=$( cd "$p" && LOOP_CONF="$LOOP_ENGINE/loop.conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" 2>&1 )
  assert_contains "$out" "LOOP-GATE-PROFILE: $LOOP_ENGINE/loop.conf" \
    "a gate reads the profile beside its own engine by default: $out"
  assert_not_contains "$out" "pinned" "and does not call its own profile a pinned one: $out"
  return 0
}

# A child works in the worktree and runs the worktree's own engine, so the profile its gate reads is the one beside
# that engine — never the run's (issue #277). Measured 2026-10-23 finishing #273: the run exported the profile of the
# project it was started in, so an item whose delta rewrote `LOOP_GATE_COMPILED_PATTERNS` was gated by the rule it was
# replacing (its round-3 log owed the build where the tree's own profile owed five shell checks and no build), the
# log could not say so, and the repair was a hand-pinned re-run.
case_gate_theChildReadsTheProfileOfTheTreeItWorksIn() {
  # files: tools/issues/ tools/loop/ tools/pi-child-events.sh tools/tooling.json
  local d p log pin
  d=$(scratch); p=$(new_project)
  # The fixture's worktree carries an engine of its own, the way the project's does: a profile beside it, and the
  # library every one of the engine's readers shares.
  mkdir -p "$p/tools/loop" "$p/tools/issues"
  cp -r "$LOOP_ENGINE"/. "$p/tools/loop/" || fail "could not copy the engine into the throwaway project"
  cp "$LOOP_PROJECT_ROOT/tools/issues/lib.sh" "$p/tools/issues/lib.sh" \
    || fail "could not copy the issue tooling's library into the throwaway project"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'an engine of its own' >/dev/null 2>&1 \
    || fail "could not commit the engine in the throwaway repository"

  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  export LOOP_CHILD_PROFILE_LOG="$d/child.log"; : > "$LOOP_CHILD_PROFILE_LOG"
  # The worker's own two facts: the profile the run handed its environment, and the profile the gate of the tree it
  # works in resolves when it runs there. Nothing else of the item's action matters to this case.
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'if [ "$1" = worker-prompt.md ]; then
       printf "handed=%s\n" "${LOOP_CONF:-<none>}" >> "$LOOP_CHILD_PROFILE_LOG"
       bash ./tools/loop/gate.sh --base "$(git rev-parse HEAD)" >> "$LOOP_CHILD_PROFILE_LOG" 2>&1 || true
     fi'
  run_loop "$p" --items 1 --minutes 90 >/dev/null || fail "the run failed"

  log=$(cat "$LOOP_CHILD_PROFILE_LOG")
  pin="$p/.pi/worktrees/loop/tools/loop/loop.conf"
  assert_contains "$log" "LOOP-GATE-PROFILE: $pin" \
    "the gate of the tree the child works in reads the profile beside that tree's engine: $log"
  assert_contains "$log" "handed=<none>" "and the run hands the child no profile of its own: $log"
  return 0
}

# ── the file set a delta's paths select (issue #276) ─────────────────────────────────────────────────────────
# A case declares the files it can be affected by in the first line of its body (`# files: …`, see the header), and
# `tools/loop-selftest.sh --files <path> …` / `--from-git <base>` selects what declares one of those paths. The
# three cases below observe the selection through a nested copy of the driver whose cases file is a stub of four
# cases with declarations of their own — the shape `driver#aCaseThatAsksForNoCoreCompilesNothing` established — so
# what is asserted is the rule and not the cost of running the real 190.

# A nested copy of the driver beside a cases file of the case's own: the engine and the issue tooling are symlinks
# into this tree, and the configuration is this suite's with a scratch root inside `<dir>`. Prints the driver and the
# configuration it must be run with; the cases file is read from stdin.
nested_suite() { # nested_suite <dir> → "<driver>\n<conf>"
  local d="$1"
  mkdir -p "$d/nested/tools" "$d/tmp" || fail "could not create the nested suite's directory"
  cp "$LOOP_PROJECT_ROOT/tools/loop-selftest.sh" "$d/nested/tools/loop-selftest.sh" || fail "could not copy the driver"
  ln -s "$LOOP_ENGINE" "$d/nested/tools/loop"
  ln -s "$LOOP_PROJECT_ROOT/tools/issues" "$d/nested/tools/issues"
  cat > "$d/nested/tools/loop-cases.sh"
  sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$d/tmp\"|" "$LOOP_CONF" > "$d/loop.conf" \
    || fail "could not write the loop configuration"
  printf '%s\n%s\n' "$d/nested/tools/loop-selftest.sh" "$d/loop.conf"
}

# Four stub cases, one declaration each: a script, a second script, a directory and a path no case owns. `*` is not
# among them, because a case declared `*` is owed for any delta and would make every selection below read as `4 of 4`.
STUB_SELECTION_CASES='LOOP_SHELL_CASES=("stub#ownsGate" "stub#ownsRun" "stub#ownsFixtureDir" "stub#ownsNothing")
loop_shell_cases() { printf "%s\n" "${LOOP_SHELL_CASES[@]}"; }
loop_case_core_files() { printf "%s\n" "tools/loop/lib.sh"; }
stub_ran() { printf "%s\n" "$1" >> "$STUB_RUN_LOG"; }
case_stub_ownsGate() {
  # files: tools/loop/gate.sh
  stub_ran "stub#ownsGate"
}
case_stub_ownsRun() {
  # files: tools/loop/run.sh
  stub_ran "stub#ownsRun"
}
case_stub_ownsFixtureDir() {
  # files: tools/loop/fixtures/scenarios/
  stub_ran "stub#ownsFixtureDir"
}
case_stub_ownsNothing() {
  # files: tools/elsewhere.sh
  stub_ran "stub#ownsNothing"
}
loop_run_shell_case() { "case_${1%%#*}_${1#*#}"; }'

case_driver_aFileFilterRunsOnlyTheCasesTheDeltaOwns() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d driver conf ran out status
  d=$(scratch)
  out=$(nested_suite "$d" <<< "$STUB_SELECTION_CASES") || fail "could not build the nested suite"
  driver=$(printf '%s\n' "$out" | sed -n 1p)
  conf=$(printf '%s\n' "$out" | sed -n 2p)
  export STUB_RUN_LOG="$d/ran.log"; : > "$STUB_RUN_LOG"

  # Two paths a delta could carry: the script one case declares, and a file below the directory another declares.
  out=$(LOOP_CONF="$conf" bash "$driver" --files 'tools/loop/gate.sh tools/loop/fixtures/scenarios/one' 2>&1); status=$?
  assert_eq "0" "$status" "a file set that names cases runs them: $out"
  ran=$(cat "$STUB_RUN_LOG")
  assert_eq "stub#ownsGate
stub#ownsFixtureDir" "$ran" "only the cases the delta's files declare ran"
  assert_contains "$out" "selected 2 of 4 cases" "and the selection is stated against the declared set: $out"
  assert_contains "$out" "all 2 cases passed" "with the count the run actually made: $out"

  # …and the same selection is what `--list` answers, without running anything.
  : > "$STUB_RUN_LOG"
  out=$(LOOP_CONF="$conf" bash "$driver" --list --files tools/loop/run.sh 2>/dev/null)
  assert_eq "stub#ownsRun" "$out" "the list is the file filter's cases and no others"
  assert_eq "" "$(cat "$STUB_RUN_LOG")" "and listing runs none of them"
  return 0
}

case_driver_aFileSetThatNamesNoCaseIsRefused() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d driver conf out status
  d=$(scratch)
  out=$(nested_suite "$d" <<< "$STUB_SELECTION_CASES") || fail "could not build the nested suite"
  driver=$(printf '%s\n' "$out" | sed -n 1p)
  conf=$(printf '%s\n' "$out" | sed -n 2p)
  export STUB_RUN_LOG="$d/ran.log"; : > "$STUB_RUN_LOG"

  # A path that exists nowhere in the declaration — the typo a caller makes — and one that exists in the tree and
  # belongs to no case: both are the same refusal, because the answer that must not be a pass is "nothing was
  # selected".
  out=$(LOOP_CONF="$conf" bash "$driver" --files tools/nobody-owns-this.sh 2>&1); status=$?
  assert_eq "2" "$status" "a file set that names no case is refused: $out"
  assert_contains "$out" "names no case of the 4 declared" "and the refusal names what it could not resolve: $out"
  assert_eq "" "$(cat "$STUB_RUN_LOG")" "no case ran"

  out=$(LOOP_CONF="$conf" bash "$driver" --files tools/loop-cases.sh 2>&1); status=$?
  assert_eq "2" "$status" "a path in the tree that no case declares is the same refusal: $out"
  return 0
}

case_driver_aCoreFileOwesEveryCase() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d driver conf ran out status
  d=$(scratch)
  out=$(nested_suite "$d" <<< "$STUB_SELECTION_CASES") || fail "could not build the nested suite"
  driver=$(printf '%s\n' "$out" | sed -n 1p)
  conf=$(printf '%s\n' "$out" | sed -n 2p)
  export STUB_RUN_LOG="$d/ran.log"; : > "$STUB_RUN_LOG"

  # Every engine script sources the library and the profile, so a delta to one of them is owed the whole set however
  # narrow the declarations are — the rule the nested cases file declares with `loop_case_core_files`.
  out=$(LOOP_CONF="$conf" bash "$driver" --files tools/loop/lib.sh 2>&1); status=$?
  assert_eq "0" "$status" "a core file runs the suite: $out"
  assert_eq "4" "$(wc -l < "$STUB_RUN_LOG")" "every declared case ran for a core file: $(cat "$STUB_RUN_LOG")"
  assert_contains "$out" "selected 4 of 4 cases" "and the statement says the whole set: $out"
  return 0
}

# The declaration is only worth what it covers: a case that declares `tools/loop/gate.sh` and then starts the
# worktree is owed for a `gate.sh` delta and skipped for a `worktree.sh` one, and a gate narrowed that way is green
# on the delta that broke it (issue #276). So the declaration is checked against the case and not trusted: the
# driver's `--case-files` is the declaration, the case's own body is the case, and the two must agree on every path
# the body names — after the same `$LOOP_ENGINE/…` normalisation the declaration was written with — and on every
# engine script one of the helpers it calls starts. A path the body names and the declaration lacks is the defect;
# a declaration wider than the body is the over-approximation the rule is allowed to make.
case_driver_everyCaseDeclaresTheFilesItCanReach() {
  # files: tools/loop-cases.sh tools/loop-selftest.sh
  local declared name files body p tok helper hfiles missing=""
  declared=$(bash "$LOOP_PROJECT_ROOT/tools/loop-selftest.sh" --case-files 2>&1) \
    || fail "the driver could not print the declared file set: $declared"

  covers() { # covers <declared set> <path>
    local t
    for t in $1; do
      [ "$t" = '*' ] && return 0
      case "$t" in
        */) case "$2" in "$t"*) return 0 ;; esac ;;
        *) [ "$t" = "$2" ] && return 0 ;;
      esac
    done
    return 1
  }
  # A path with a `..` segment is read as the normalised form the declaration was written with.
  normalise() {
    local q="$1"
    while case "$q" in *'/../'*) true ;; *) false ;; esac; do
      q="${q%%/../*}/${q#*/../}"
    done
    printf '%s' "$q"
  }

  while IFS=$'\t' read -r name files; do
    [ -n "$name" ] || continue
    case "$files" in '<undeclared>'|'') missing="$missing $name(no-declaration)"; continue ;; esac
    body=$(awk -v fn="case_${name%%#*}_${name#*#}" '
      $0 == fn "() {" { seen = 1; next }
      seen && /^\}$/ { exit }
      seen { print }
    ' "$LOOP_PROJECT_ROOT/tools/loop-cases.sh")
    # What the body names, read the way the declaration was: the engine's own variables resolved to their paths, a
    # directory named as one (a case that builds a throwaway project owns the fixture directory, not its files).
    while IFS= read -r p; do
      [ -n "$p" ] || continue
      p=$(normalise "$p")
      if [ -d "$LOOP_PROJECT_ROOT/$p" ]; then p="$p/"; fi
      [ -e "$LOOP_PROJECT_ROOT/${p%/}" ] || continue
      covers "$files" "$p" || missing="$missing $name:$p"
    done < <(printf '%s\n' "$body" | loop_case_body_paths)
    # What it reaches by calling a helper, which starts a script the body never names.
    while IFS='|' read -r helper hfiles; do
      [ -n "$helper" ] || continue
      printf '%s\n' "$body" | grep -qE "(^|[^A-Za-z0-9_.-])$helper([^A-Za-z0-9_-]|\$)" || continue
      for p in $hfiles; do covers "$files" "$p" || missing="$missing $name:$p($helper)"; done
    done < <(loop_case_helper_files)
    # …and every token it declares is a path this tree has, so a renamed script is a red case rather than a
    # selection that silently matches nothing.
    for tok in $files; do
      [ "$tok" = '*' ] && continue
      [ -e "$LOOP_PROJECT_ROOT/${tok%/}" ] || missing="$missing $name:$tok(no-such-path)"
    done
  done <<< "$declared"

  [ -z "$missing" ] || fail "a case does not declare what it can be reached by:$missing"
  return 0
}

# ------------------------------------------------------- cases: one suite per repository

# Two suites in one repository redden a different set of cases each run — measured 2026-10-24, a run of 210 cases
# and a control run of the same suite from a pristine base, same host and same day, failed two triples of cases
# whose intersection was one case, and that one was another item's stable red (issue #295). The victim moves
# between the runs, so the failing set of a suite depends on the other suite, and a red gate reads as a defect in
# the change under test. The driver therefore refuses the second suite in one repository and names the lock it found
# — the interference is never silently attributed to the change — takes a lock over when its owner is gone, and
# keeps the shape that runs one named case working, which is what the mutation harness and the cases that start a
# nested suite use.
case_driver_aSecondSuiteInOneRepositoryIsRefused() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d driver conf own_conf lock hold_log quick_log holder pid_a dead out status i
  d=$(scratch)
  # A nested suite over stub cases, run with this repository's *own* scratch root: that root, and the lock beside
  # it, is what two suites of one repository share and what the second one is refused by.
  mkdir -p "$d/nested/tools" "$d/tmp"
  cp "$LOOP_PROJECT_ROOT/tools/loop-selftest.sh" "$d/nested/tools/loop-selftest.sh" || fail "could not copy the driver"
  ln -s "$LOOP_ENGINE" "$d/nested/tools/loop"
  ln -s "$LOOP_PROJECT_ROOT/tools/issues" "$d/nested/tools/issues"
  hold_log="$d/hold.log"; quick_log="$d/quick.log"
  cat > "$d/nested/tools/loop-cases.sh" <<CASES
LOOP_SHELL_CASES=('hold#sleeps' 'quick#a' 'quick#b')
loop_shell_cases() { printf '%s\n' "\${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() {
  case "\$1" in
    hold#*) printf '%s\n' "\$1" >> "$hold_log"; sleep 30 ;;
    *) printf '%s\n' "\$1" >> "$quick_log" ;;
  esac
  return 0
}
CASES
  driver="$d/nested/tools/loop-selftest.sh"
  # This repository's own scratch root, so the nested suite is a suite of *this* repository; and a root of its own
  # for the lease below, so a locked root is never one another suite is standing in.
  conf="$d/loop.conf"
  sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$LOOP_TMP_DIR\"|" "$LOOP_CONF" > "$conf" || fail "could not write the loop configuration"
  own_conf="$d/own.conf"
  sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$d/tmp\"|" "$LOOP_CONF" > "$own_conf" || fail "could not write the loop configuration"
  lock="$LOOP_TMP_DIR/.loop-selftest.lock"

  # A *whole* suite holds this repository's lock, and only a live owner is one: when this invocation is such a
  # suite that is this suite, and when it runs one named case it holds nothing and starts the suite it needs — a
  # second suite in this repository, which is the deliberate reproduction the issue asks for.
  holder=""
  if [ -f "$lock/owner" ]; then
    holder=$(cut -d' ' -f1 < "$lock/owner" 2>/dev/null)
    case "$holder" in ''|*[!0-9]*) holder="" ;; esac
    [ -z "$holder" ] || kill -0 "$holder" 2>/dev/null || holder=""
  fi
  pid_a=""
  if [ -z "$holder" ]; then
    LOOP_CONF="$conf" bash "$driver" --group hold >/dev/null 2>&1 &
    pid_a=$!
    # The suite's own way out sweeps what stood under *its* scratch root, and this suite is parked in the
    # repository's root on purpose: a case that fails between here and the lease half below would leave it holding
    # this repository's lock, so the case ends it on every way out rather than on the one path that reaches the kill.
    # The pid is written into the trap rather than read from it: the trap runs when the case's subshell exits, which
    # is after the function that declared it returned, and a `local` read there is unset (the same trap the driver
    # documents for its own `tmp`).
    trap "kill -TERM $pid_a 2>/dev/null || true" EXIT
    # 200 × 0.1 s: about twenty times the start of a nested driver (a java version check and sourcing), measured
    # 2026-10-24 — the bound a red poll has to be read against.
    for i in $(seq 1 200); do
      holder=""
      [ -f "$lock/owner" ] && holder=$(cut -d' ' -f1 < "$lock/owner")
      [ "$holder" = "$pid_a" ] && break
      sleep 0.1
    done
    [ -n "$holder" ] || { kill -9 "$pid_a" 2>/dev/null; fail "the suite this case started never took the lock at $lock"; }
  fi

  # The second suite of this repository refuses before it runs a case, and the refusal names the process that holds
  # the lock, with the moment it took it.
  out=$(LOOP_CONF="$conf" bash "$driver" --group quick 2>&1); status=$?
  assert_eq "2" "$status" "a second suite in this repository refuses instead of running: $out"
  assert_contains "$out" "one suite per repository" "and the refusal names the rule: $out"
  # The owner as the lock records it: the refusal repeats the file rather than reformatting it, so this is the same
  # reading `cut` made above.
  assert_eq "$holder" "$(printf '%s\n' "$out" | sed -n 's/.*(owner \([0-9][0-9]*\)[ )].*/\1/p' | head -1)" \
    "and the refusal names the suite that holds $lock: $out"
  assert_eq "" "$(cat "$quick_log" 2>/dev/null)" "a refused suite ran no case"
  [ -f "$lock/owner" ] || fail "the refused suite removed the lock it did not take"

  # The selection decides, never a case's cost: one named case is one behaviour and runs while the lock is held,
  # and a pattern may select more than one, so it is a suite like any other.
  out=$(LOOP_CONF="$conf" bash "$driver" --case 'quick#a' 2>&1); status=$?
  assert_eq "0" "$status" "one named case runs while a suite holds this repository: $out"
  assert_eq "quick#a" "$(cat "$quick_log")" "and it is the case that ran: $(cat "$quick_log")"
  out=$(LOOP_CONF="$conf" bash "$driver" --case 'quick#*' 2>&1); status=$?
  assert_eq "2" "$status" "a pattern that may select more than one case is a suite: $out"
  assert_eq "quick#a" "$(cat "$quick_log")" "and it ran no case either: $(cat "$quick_log")"

  # A suite ended by a signal no trap can catch leaves its lock behind, and a lock whose owner is gone is taken
  # over and named rather than refusing every suite that follows it — the lease `state#aDeadOwnersLockIsTakenOver`
  # already asserts of the run's own lock. This half stands in the root the nested suite resolves for itself, and
  # the suite that held this repository's lock is ended first.
  if [ -n "$pid_a" ]; then
    kill -TERM "$pid_a" 2>/dev/null
    wait "$pid_a" 2>/dev/null
    trap - EXIT   # the suite above is ended and waited for; a second signal to a reaped pid could be a reused one
  fi
  lock="$d/tmp/.loop-selftest.lock"
  mkdir -p "$lock"
  dead=999999999   # the ceiling of the pid space: a process that cannot be running
  printf '%s\n' "$dead" > "$lock/owner"
  out=$(LOOP_CONF="$own_conf" bash "$driver" --group quick 2>&1); status=$?
  assert_eq "0" "$status" "a lock whose owner is gone is taken over and the suite runs: $out"
  assert_contains "$out" "took over the lock" "the takeover is announced: $out"
  assert_contains "$out" "$dead" "and it names the process that is gone: $out"
  [ ! -e "$lock" ] || fail "the suite that took the lock over did not release it"
  assert_eq "quick#a
quick#b" "$(tail -2 "$quick_log")" "and the suite the lock was released for ran its cases: $(cat "$quick_log")"
  return 0
}

# A profile's suite command may carry `{base}`, and the gate replaces it with the base it resolved — the same base
# whose diff produced the pathspec — so a suite narrows itself to the delta being measured. The engine knows no
# suite by name: it hands over the base and nothing else.
case_gate_theDeltaHandsASuiteTheBaseItMeasured() {
  # files: tools/loop/ tools/loop/gate.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf base plan
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_CONF" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_COMPILED_CMD=compiled-gate\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS=""\n'
    printf 'LOOP_GATE_SUITES="tools/loop/*|tools/narrow.sh --from-git {base};notes/*|notes-check"\n'
  } >> "$conf"

  mkdir -p "$p/tools/loop" && printf 'a tool\n' > "$p/tools/loop/a.sh"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'an engine path' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  plan=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )
  assert_contains "$plan" "tools/narrow.sh --from-git $base" \
    "the suite is handed the base the gate resolved: $plan"
  assert_not_contains "$plan" "{base}" "and no token reaches a shell: $plan"
  assert_not_contains "$plan" "notes-check" "a suite the delta does not owe is not handed it: $plan"
  return 0
}
