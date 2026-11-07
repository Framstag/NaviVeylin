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
  'state#aBlockAtTheRunsBoundIsNoRefusal'
  'state#theStreakIsTheRecordedCount'
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
  'state#anAdoptionRefusalIsRecordedBesideTheExclusion'
  'state#defaultsComeFromLoopConf'
  'state#projectKeysAreReadFromLoopConf'
  'state#agentCommandIsTheOnlyIndirection'
  'state#agentCommandRefusesAnEmptyValue'
  'state#aRolesOwnCommandStartsOnlyThatRolesChild'
  'state#doneRecordsTheCommitItLanded'
  'state#theLandingScoreIsReadOnceAndStored'
  'state#aLandingWithNoRunIsUnscored'
  'prescreen#ranksBugsFirstAndKeepsFeatures'
  'prescreen#oneHarnessImprovementPerFourAppItems'
  'prescreen#aRefusedClassStillStopsTheScreen'
  'prescreen#missingPathIsReportedNotRefused'
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
  'prescreen#aQuotedSubmodulePathIsEvidenceNotAClaim'
  'prescreen#oneUnreadableCandidateDoesNotStopTheScreen'
  'prescreen#anUnansweredTrackerIsNotAnEmptyQueue'
  'worktree#seedsAllFourInputs'
  'evidence#scansWhatTheProfileNames'
  'worktree#checksOutTheSubmoduleTheLoopConfigNames'
  'worktree#checksOutTheNewPinWhenItReusesTheWorktree'
  'worktree#placesAPinOnlyTheRootCheckoutHolds'
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
  'worktree#theNamedChangeIsCopiedIntoTheItem'
  'worktree#aMissingChangeRefusesTheItem'
  'worktree#aSetAsideBranchWhosePatchesLandedIsRemoved'
  'worktree#aSetAsideBranchThatCarriesWorkIsNamedNotRemoved'
  'prescreen#aTierAItemPassesUnchanged'
  'prescreen#aChangeThatIsNoLongerReadyIsRefused'
  'prescreen#aClosedItemIsNotSelected'
  'prompt#thePackageCarriesTheChange'
  'prompt#aTierAItemSaysSo'
  'run#aTierBItemWritesNoSecondChange'
  'run#aForkTheChangeDoesNotAnswerIsNamed'
  'run#aChangeWhosePremiseMovedIsNotRePlanned'
  'clean#aConsumedChangeIsReported'
  'clean#aSecondRunFindsNothing'
  'labels#verdictToLabelMatrix'
  'labels#holdCarriesItsReason'
  'labels#trapRemovesInProgressOnSignal'
  'labels#neverWritesReservedLabel'
  'labels#writesOnlyBetweenChildren'
  'labels#closeRemovesTheDecisionLabel'
  'labels#closeRemovesTheShapingLabel'
  'labels#closeNamesTheSettledTransition'
  'run#aShapingVerdictWritesTheLabel'
  'run#aVerdictOutsideTheListWritesNoLabel'
  'run#theShapingInboxDoesNotCountAgainstTheCeiling'
  'prompt#workerNamesEveryPhaseAndSkill'
  'prompt#workerCarriesTheFeaturePath'
  'prompt#workerNamesTheDecisionField'
  'prompt#workerGradesItsOwnDiffBeforeItReports'
  'prompt#workerTreatsARecordedDecisionAsSettled'
  'prompt#reviewerCarriesTheEightItems'
  'prompt#reviewerIsBoundedToReading'
  'prompt#iterationProtocolHandsTheReviewerTheSameSkeleton'
  'prompt#workerNamesTheChangeSurfaceBeforeThePlan'
  'prompt#iterationProtocolNamesTheChangeSurface'
  'prompt#packageIsAFileAndNotInlined'
  'prompt#inlineInjectionCannotReachTheInstructionPosition'
  'prompt#thePhasePromptsCarryTheirOwnRoute'
  'prompt#thePhasePromptNamesWhatItsShellRefuses'
  'run#preflightRefusalStopsBeforeAnyWork'
  'run#aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen'
  'run#blockedItemAsksItsOwnQuestion'
  'run#falsifiedSkipClosesWithItsMeasurement'
  'run#falsificationWithoutAMeasurementIsEscalated'
  'run#mayCloseNoKeepsAFalsificationOpen'
  'run#falsificationWithAChangedTreeIsBlocked'
  'run#decisionCapRefusesToStart'
  'run#decisionCapCountsOnlyCandidates'
  'run#theDecisionCapRecordsItsStopReason'
  'run#aFailedPreScreenRecordsItsStopReason'
  'run#refusesARepositoryThatIsNotAWorkTree'
  'gate#theDeltaSelectsTheSuitesItTouches'
  'gate#theMapCoversEveryPipelineStep'
  'gate#aPipelineCheckIsOwedByItsPaths'
  'gate#aPathACheckReadsOwesThatStepsCommandSet'
  'gate#anEnginePathOwesTheRecordCheck'
  'gate#theSuitesOutputReachesTheConsoleWhileItRuns'
  'gate#aStepEndsWithItsCommandNotWithItsWatchers'
  'gate#aStepIsBoundedAndTheOverrunNamesIt'
  'gate#aFailingSuiteIsReadFromTheSameStream'
  'gate#aFailingSuiteWithNoSummaryInventsNoTest'
  'gate#aCompiledStepsTallyIsNotHidden'
  'gate#theSummaryNamesWhatEachSuiteCost'
  'gate#independentStepsRunTogether'
  'gate#aFailingSuitesTallyIsUnchangedByItsNeighbours'
  'gate#aPooledWavePrintsEachStepWholeWhenItEnds'
  'driver#aGroupFilterRunsOnlyItsOwnCases'
  'driver#aFilterThatNamesNoCaseRefuses'
  'driver#theListIsFilteredTheSameWayASuiteRunIs'
  'driver#theSummaryNamesWhatEachGroupCost'
  'driver#theTimesFigureIsAskedForNotAssumed'
  'gate#anArchivedArtefactOwesNoBuild'
  'gate#aModuleResourceOwesTheBuild'
  'gate#theGateNamesTheProfileItRead'
  'gate#theChildReadsTheProfileOfTheTreeItWorksIn'
  'gate#aThirdFullRunInOneSessionIsRefused'
  'mutation#everyDeclaredRecordApplies'
  'mutation#aStaleRecordIsNamed'
  'measure#namesTheOwedChecksInOneBlock'
  'measure#printsTheNumbersTheReviewerAsksFor'
  'measure#refusesToGuessWhichChangeItMeasures'
  'measure#saysHowManyTalliesItWithheld'
  'measure#printsTheSuiteCountAndTheGateTiming'
  'prompt#workerIsHandedTheMeasureBlock'
  'run#oneItemOrderIsFixed'
  'run#childClaimWithoutCommitIsBlocked'
  'run#anEmptyTreeIsRefusedBeforeTheGateChild'
  'run#blockedItemIsEscalatedWithItsReason'
  'run#aFailedEscalationWriteIsNamed'
  'run#aFailedUnpickIsNamed'
  'run#childStartsInTheProjectRoot'
  'run#childReportsWhileItWaits'
  'run#theWaitEndsWhenTheChildDoes'
  'run#theProgressLineKeepsItsInterval'
  'run#headerNamesTheLiveView'
  'run#prescreenAnnouncesItselfBeforeItReturns'
  'run#everyPhaseBoundaryIsPrinted'
  'run#childOutlastingTheWallClockIsCut'
  'run#theChildCapIsNeverBelowWhatOneItemsGateOwes'
  'state#theItemFloorIsOnlyBelowWhatOneItemsGateOwes'
  'run#childIsEndedAtTheRunsDeadline'
  'run#aVanishedWriterEndsTheWait'
  'run#aSlowChildIsNotMistakenForAVanishedWriter'
  'run#reviewerEndedAtTheRunsDeadlineIsNotADecision'
  'run#aReviewerEndedAtItsTurnBoundIsNotARefusal'
  'run#detachedProcessInTheWorktreeIsEndedByTheCap'
  'run#treeOfAChildTheCapEndedIsNotCommitted'
  'run#treeOfACapEndedChildIsKeptAsAPatch'
  'run#noProcessOfASignalledRunOutlivesIt'
  'run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted'
  'run#childThatNeverStartedIsTheRunsFault'
  'run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault'
  'run#hostBelowTheFloorBlocksTheItemAndStops'
  'run#hostAboveTheFloorRunsTheItem'
  'run#hostWithoutTheReadingPasses'
  'run#aHostWithoutThePlacementReadingIsRefused'
  'run#childWriteOutsideTheWorktreeIsNoted'
  'run#aWriterTheRunCanPlaceBlocksTheItem'
  'run#aChildIsGivenOneScratchDirectory'
  'run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem'
  'run#theWorkersOwnShellWriteIsRecordedAndNotPlaced'
  'run#dirtyBaseRefusesTheMergeAndNamesThePath'
  'run#strictOutsideWriteBlocksTheItem'
  'run#strictOutsideWriteDiscardsTheReviewRound'
  'run#leftoverBranchIsRecordedNotDropped'
  'run#branchThatAppearsWhileTheRunIsOpenIsSetAside'
  'run#mergesAFinishedAttemptAndClosesTheItem'
  'run#doesNotMergeAnAttemptWithoutAGreenVerification'
  'run#anAdoptionTheTreeRefusesIsRecordedAsUnmergeable'
  'run#mergesItsCommittedBranchBeforeClosing'
  'run#aMergedBranchIsRemoved'
  'run#theEndSweepsTheLandedBranchesItMade'
  'run#theRemoteSweepKeepsAnOpenPullRequest'
  'run#theSweepComparesAgainstTheFetchedBase'
  'run#theBaseAdvancesWithEveryMerge'
  'run#staleTestResultsAreNotThisChildsGate'
  'run#doesNotCloseOnAMergeTheBaseCannotTake'
  'run#recordsAFailedReviewOnTheBranch'
  'run#announcesAndRecordsTheReviewRound'
  'run#thePreGateReviewRunsBeforeTheGate'
  'run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun'
  'run#dirtyKeptWorktreeIsARecordedRefusal'
  'run#resetWorktreeDiscardsWhatItNames'
  'run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard'
  'run#resetWorktreeNamesTheDirtItCannotDiscard'
  'run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds'
  'run#aRefusedStartKeepsTheLeftoversItFound'
  'run#placesTheModuleAndGoesOnWhenTheOnlyDirtIsThePin'
  'run#aPinNoCloneHoldsIsRefusedBeforeThePrescreen'
  'run#theStartAfterTheRunsOwnEndedChildIsNotRefused'
  'run#aRetriedStartStopsWithItsOwnReason'
  'run#aSkipClearsTheInProgressMark'
  'run#staleSkipAsksItsQuestion'
  'run#staleSkipKeepsTheWorkersQuestion'
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
  'run#aFencedClosingBlockIsACompleteAnswer'
  'run#escalationCarriesTheDecision'
  'run#mayCloseNoLeavesTheIssueOpen'
  'run#metricsRecordedPerItem'
  'run#childUsageIsRecordedPerItem'
  'run#aRedGateIsBlockedByTheTail'
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
  'adapter#theChildIsStartedWithoutContextFiles'
  'adapter#theChildIsStartedWithTheResultBound'
  'adapter#theChildIsStartedWithItsToolDirectoryOnPath'
  'adapter#theKeptStreamIsBoundedAndItsRotationIsRecorded'
  'trim#aResultAboveTheBoundIsTrimmedAndNamed'
  'trim#aResultUnderTheBoundIsHandedOverWhole'
  'trim#theTrimIsRecorded'
  'look#oneCallAnswersTheWholeTree'
  'look#aResultOverTheBoundIsCutAndNamed'
  'look#aPathScopesTheWalkToIt'
  'policy#aShellTreeSearchIsRefusedAndARecipeIsNot'
  'adapter#theChildIsStartedWithTheShellPolicy'
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
  'report#anItemOverTheTokenBoundIsFlagged'
  'report#anItemBelowTheCallsPerTurnFloorIsNamed'
  'report#theSpendIsSplitByPhaseRoleAndGate'
  'report#aStreamWithoutDurationsPrintsDash'
  'report#trendFiguresFromState'
  'report#noRoundOrWallPrintsDashNotZero'
  'report#landingsAreTheStoredNumber'
  'clean#interruptedSuiteRemovesItsScratchTree'
  'clean#reportsWithoutApplyAndRemovesWithIt'
  'clean#reportsAnAgedItemDirectory'
  'clean#sweepsOnlyWhatIsInTheBase'
  'clean#interruptedSuiteEndsItsProcessGroup'
  'clean#reportsAndEndsAProcessLeftInAScratchTree'
  'clean#reportsTheWorktreesScratchTreesToo'
  'clean#worktreeDotSweepsTheOneRootOnce'
  'clean#endsARunWhoseTreeIsAlreadyDeleted'
  'clean#suiteThatEndsNormallyEndsTheProcessItsCaseLeft'
  'clean#reportsAWorktreeNoRunKeeps'
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
  'driver#anEmptyDeltaIsStatedWhenTheCallerAllowsIt'
  'driver#aCoreFileOwesEveryCase'
  'driver#everyCaseDeclaresTheFilesItCanReach'
  'driver#theSuiteRunsItsCasesInAPool'
  'driver#theCaseBoundScalesWithThePool'
  'driver#aCaseThatBlocksIsEndedByItsOwnBound'
  'driver#aCaseInsideItsBoundKeepsItsOwnVerdict'
  'driver#aSuiteEndsInsideTheCeilingItsCallerNamed'
  'driver#aCeilingTheSuiteFitsInsideChangesNothing'
  'driver#aRunningCasePrintsItsOwnLines'
  'driver#aCaseThatLeavesAProcessBehindIsSweptWithIt'
  'driver#aSecondSuiteInOneRepositoryIsRefused'
  'driver#noFixtureFifoIsOpenedWriteOnly'
  'gate#theDeltaHandsASuiteTheBaseItMeasured'
  'gate#theDeclarationCaseIsOwedFirst'
  'gate#aGreenLogNamesNoFailingCase'
  'gate#aFailingCaseCarriesItsFactsAndItsRerun'
  'gate#twoFailuresNameTheFirstAndCountTheRest'
  'gate#aStepThatNamesNoCaseIsAttributedByItsStep'
  'gate#theBlockStaysInsideTheRecordedBound'
  'gate#theStepBoundIsClampedByTheCeilingTheCallerNamed'
  'phase#oneCallPrintsThePhaseState'
  'phase#aResultOverTheBoundIsCutAndNamed'
  'repeat#aRepeatReadIsNamedNotHandedOverAgain'
  'repeat#aRepeatAfterAnEditIsHandedOverWhole'
  'repeat#theRecordedZeroKeepsTheRepeatRuleOff'
  'repeat#aBrokenLedgerLeavesTheResultAsReturned'
  'run#childPastItsTurnBoundIsCut'
  'run#aRotatedStreamStillCountsTheTurnsItDropped'
  'run#theRecordedZeroKeepsTheTurnBoundOff'
  'run#eachChildIsBoundedByItsOwnRolesTurns'
  'run#aRoleWithNoRecordedTurnBoundRunsUnbounded'
  'run#childPastItsContextBoundIsCut'
  'run#theRecordedZeroKeepsTheContextBoundOff'
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
    'new_project|tools/pi-child-events.sh tools/tooling.json' \
    'phase_fixture|tools/loop/state.sh' \

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
    'state#aBlockAtTheRunsBoundIsNoRefusal') fn=case_state_aBlockAtTheRunsBoundIsNoRefusal ;;
    'state#theStreakIsTheRecordedCount') fn=case_state_theStreakIsTheRecordedCount ;;
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
    'state#anAdoptionRefusalIsRecordedBesideTheExclusion') fn=case_state_anAdoptionRefusalIsRecordedBesideTheExclusion ;;
    'state#defaultsComeFromLoopConf') fn=case_state_defaultsComeFromLoopConf ;;
    'state#projectKeysAreReadFromLoopConf') fn=case_state_projectKeysAreReadFromLoopConf ;;
    'state#agentCommandIsTheOnlyIndirection') fn=case_state_agentCommandIsTheOnlyIndirection ;;
    'state#agentCommandRefusesAnEmptyValue') fn=case_state_agentCommandRefusesAnEmptyValue ;;
    'state#aRolesOwnCommandStartsOnlyThatRolesChild') fn=case_state_aRolesOwnCommandStartsOnlyThatRolesChild ;;
    'state#doneRecordsTheCommitItLanded') fn=case_state_doneRecordsTheCommitItLanded ;;
    'state#theLandingScoreIsReadOnceAndStored') fn=case_state_theLandingScoreIsReadOnceAndStored ;;
    'state#aLandingWithNoRunIsUnscored') fn=case_state_aLandingWithNoRunIsUnscored ;;
    'state#inheritedStatePathOutsideTheTreeIsIgnored') fn=case_state_inheritedStatePathOutsideTheTreeIsIgnored ;;
    'prescreen#ranksBugsFirstAndKeepsFeatures') fn=case_prescreen_ranksBugsFirstAndKeepsFeatures ;;
    'prescreen#oneHarnessImprovementPerFourAppItems') fn=case_prescreen_oneHarnessImprovementPerFourAppItems ;;
    'prescreen#aRefusedClassStillStopsTheScreen') fn=case_prescreen_aRefusedClassStillStopsTheScreen ;;
    'prescreen#missingPathIsReportedNotRefused') fn=case_prescreen_missingPathIsReportedNotRefused ;;
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
    'prescreen#aQuotedSubmodulePathIsEvidenceNotAClaim') fn=case_prescreen_aQuotedSubmodulePathIsEvidenceNotAClaim ;;
    'prescreen#oneUnreadableCandidateDoesNotStopTheScreen') fn=case_prescreen_oneUnreadableCandidateDoesNotStopTheScreen ;;
    'prescreen#anUnansweredTrackerIsNotAnEmptyQueue') fn=case_prescreen_anUnansweredTrackerIsNotAnEmptyQueue ;;
    'worktree#seedsAllFourInputs') fn=case_worktree_seedsAllFourInputs ;;
    'evidence#scansWhatTheProfileNames') fn=case_evidence_scansWhatTheProfileNames ;;
    'worktree#checksOutTheSubmoduleTheLoopConfigNames') fn=case_worktree_checksOutTheSubmoduleTheLoopConfigNames ;;
    'worktree#checksOutTheNewPinWhenItReusesTheWorktree') fn=case_worktree_checksOutTheNewPinWhenItReusesTheWorktree ;;
    'worktree#placesAPinOnlyTheRootCheckoutHolds') fn=case_worktree_placesAPinOnlyTheRootCheckoutHolds ;;
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
    'worktree#theNamedChangeIsCopiedIntoTheItem') fn=case_worktree_theNamedChangeIsCopiedIntoTheItem ;;
    'worktree#aMissingChangeRefusesTheItem') fn=case_worktree_aMissingChangeRefusesTheItem ;;
    'worktree#aSetAsideBranchWhosePatchesLandedIsRemoved') fn=case_worktree_aSetAsideBranchWhosePatchesLandedIsRemoved ;;
    'worktree#aSetAsideBranchThatCarriesWorkIsNamedNotRemoved') fn=case_worktree_aSetAsideBranchThatCarriesWorkIsNamedNotRemoved ;;
    'prescreen#aTierAItemPassesUnchanged') fn=case_prescreen_aTierAItemPassesUnchanged ;;
    'prescreen#aChangeThatIsNoLongerReadyIsRefused') fn=case_prescreen_aChangeThatIsNoLongerReadyIsRefused ;;
    'prescreen#aClosedItemIsNotSelected') fn=case_prescreen_aClosedItemIsNotSelected ;;
    'prompt#thePackageCarriesTheChange') fn=case_prompt_thePackageCarriesTheChange ;;
    'prompt#aTierAItemSaysSo') fn=case_prompt_aTierAItemSaysSo ;;
    'run#aTierBItemWritesNoSecondChange') fn=case_run_aTierBItemWritesNoSecondChange ;;
    'run#aForkTheChangeDoesNotAnswerIsNamed') fn=case_run_aForkTheChangeDoesNotAnswerIsNamed ;;
    'run#aChangeWhosePremiseMovedIsNotRePlanned') fn=case_run_aChangeWhosePremiseMovedIsNotRePlanned ;;
    'clean#aConsumedChangeIsReported') fn=case_clean_aConsumedChangeIsReported ;;
    'clean#aSecondRunFindsNothing') fn=case_clean_aSecondRunFindsNothing ;;
    'labels#verdictToLabelMatrix') fn=case_labels_verdictToLabelMatrix ;;
    'labels#holdCarriesItsReason') fn=case_labels_holdCarriesItsReason ;;
    'labels#trapRemovesInProgressOnSignal') fn=case_labels_trapRemovesInProgressOnSignal ;;
    'labels#neverWritesReservedLabel') fn=case_labels_neverWritesReservedLabel ;;
    'labels#writesOnlyBetweenChildren') fn=case_labels_writesOnlyBetweenChildren ;;
    'labels#closeRemovesTheDecisionLabel') fn=case_labels_closeRemovesTheDecisionLabel ;;
    'labels#closeNamesTheSettledTransition') fn=case_labels_closeNamesTheSettledTransition ;;
    'labels#closeRemovesTheShapingLabel') fn=case_labels_closeRemovesTheShapingLabel ;;
    'run#aShapingVerdictWritesTheLabel') fn=case_run_aShapingVerdictWritesTheLabel ;;
    'run#aVerdictOutsideTheListWritesNoLabel') fn=case_run_aVerdictOutsideTheListWritesNoLabel ;;
    'run#theShapingInboxDoesNotCountAgainstTheCeiling') fn=case_run_theShapingInboxDoesNotCountAgainstTheCeiling ;;
    'prompt#workerNamesEveryPhaseAndSkill') fn=case_prompt_workerNamesEveryPhaseAndSkill ;;
    'prompt#workerCarriesTheFeaturePath') fn=case_prompt_workerCarriesTheFeaturePath ;;
    'prompt#workerNamesTheDecisionField') fn=case_prompt_workerNamesTheDecisionField ;;
    'prompt#workerGradesItsOwnDiffBeforeItReports') fn=case_prompt_workerGradesItsOwnDiffBeforeItReports ;;
    'prompt#workerTreatsARecordedDecisionAsSettled') fn=case_prompt_workerTreatsARecordedDecisionAsSettled ;;
    'prompt#reviewerCarriesTheEightItems') fn=case_prompt_reviewerCarriesTheEightItems ;;
    'prompt#reviewerIsBoundedToReading') fn=case_prompt_reviewerIsBoundedToReading ;;
    'prompt#iterationProtocolHandsTheReviewerTheSameSkeleton') fn=case_prompt_iterationProtocolHandsTheReviewerTheSameSkeleton ;;
    'prompt#workerNamesTheChangeSurfaceBeforeThePlan') fn=case_prompt_workerNamesTheChangeSurfaceBeforeThePlan ;;
    'prompt#iterationProtocolNamesTheChangeSurface') fn=case_prompt_iterationProtocolNamesTheChangeSurface ;;
    'prompt#packageIsAFileAndNotInlined') fn=case_prompt_packageIsAFileAndNotInlined ;;
    'prompt#inlineInjectionCannotReachTheInstructionPosition') fn=case_prompt_inlineInjectionCannotReachTheInstructionPosition ;;
    'prompt#thePhasePromptsCarryTheirOwnRoute') fn=case_prompt_thePhasePromptsCarryTheirOwnRoute ;;
    'prompt#thePhasePromptNamesWhatItsShellRefuses') fn=case_prompt_thePhasePromptNamesWhatItsShellRefuses ;;
    'run#preflightRefusalStopsBeforeAnyWork') fn=case_run_preflightRefusalStopsBeforeAnyWork ;;
    'run#aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen') fn=case_run_aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen ;;
    'run#blockedItemAsksItsOwnQuestion') fn=case_run_blockedItemAsksItsOwnQuestion ;;
    'run#falsifiedSkipClosesWithItsMeasurement') fn=case_run_falsifiedSkipClosesWithItsMeasurement ;;
    'run#falsificationWithoutAMeasurementIsEscalated') fn=case_run_falsificationWithoutAMeasurementIsEscalated ;;
    'run#mayCloseNoKeepsAFalsificationOpen') fn=case_run_mayCloseNoKeepsAFalsificationOpen ;;
    'run#falsificationWithAChangedTreeIsBlocked') fn=case_run_falsificationWithAChangedTreeIsBlocked ;;
    'run#decisionCapRefusesToStart') fn=case_run_decisionCapRefusesToStart ;;
    'run#decisionCapCountsOnlyCandidates') fn=case_run_decisionCapCountsOnlyCandidates ;;
    'run#theDecisionCapRecordsItsStopReason') fn=case_run_theDecisionCapRecordsItsStopReason ;;
    'run#aFailedPreScreenRecordsItsStopReason') fn=case_run_aFailedPreScreenRecordsItsStopReason ;;
    'run#refusesARepositoryThatIsNotAWorkTree') fn=case_run_refusesARepositoryThatIsNotAWorkTree ;;
    'gate#theDeltaSelectsTheSuitesItTouches') fn=case_gate_theDeltaSelectsTheSuitesItTouches ;;
    'gate#theMapCoversEveryPipelineStep') fn=case_gate_theMapCoversEveryPipelineStep ;;
    'gate#aPipelineCheckIsOwedByItsPaths') fn=case_gate_aPipelineCheckIsOwedByItsPaths ;;
    'gate#aPathACheckReadsOwesThatStepsCommandSet') fn=case_gate_aPathACheckReadsOwesThatStepsCommandSet ;;
    'gate#anEnginePathOwesTheRecordCheck') fn=case_gate_anEnginePathOwesTheRecordCheck ;;
    'gate#theSuitesOutputReachesTheConsoleWhileItRuns') fn=case_gate_theSuitesOutputReachesTheConsoleWhileItRuns ;;
    'gate#aStepEndsWithItsCommandNotWithItsWatchers') fn=case_gate_aStepEndsWithItsCommandNotWithItsWatchers ;;
    'gate#aStepIsBoundedAndTheOverrunNamesIt') fn=case_gate_aStepIsBoundedAndTheOverrunNamesIt ;;
    'gate#aFailingSuiteIsReadFromTheSameStream') fn=case_gate_aFailingSuiteIsReadFromTheSameStream ;;
    'gate#aFailingSuiteWithNoSummaryInventsNoTest') fn=case_gate_aFailingSuiteWithNoSummaryInventsNoTest ;;
    'gate#aCompiledStepsTallyIsNotHidden') fn=case_gate_aCompiledStepsTallyIsNotHidden ;;
    'gate#theSummaryNamesWhatEachSuiteCost') fn=case_gate_theSummaryNamesWhatEachSuiteCost ;;
    'gate#independentStepsRunTogether') fn=case_gate_independentStepsRunTogether ;;
    'gate#aFailingSuitesTallyIsUnchangedByItsNeighbours') fn=case_gate_aFailingSuitesTallyIsUnchangedByItsNeighbours ;;
    'gate#aPooledWavePrintsEachStepWholeWhenItEnds') fn=case_gate_aPooledWavePrintsEachStepWholeWhenItEnds ;;
    'driver#aGroupFilterRunsOnlyItsOwnCases') fn=case_driver_aGroupFilterRunsOnlyItsOwnCases ;;
    'driver#aFilterThatNamesNoCaseRefuses') fn=case_driver_aFilterThatNamesNoCaseRefuses ;;
    'driver#theListIsFilteredTheSameWayASuiteRunIs') fn=case_driver_theListIsFilteredTheSameWayASuiteRunIs ;;
    'driver#theSummaryNamesWhatEachGroupCost') fn=case_driver_theSummaryNamesWhatEachGroupCost ;;
    'driver#theTimesFigureIsAskedForNotAssumed') fn=case_driver_theTimesFigureIsAskedForNotAssumed ;;
    'gate#anArchivedArtefactOwesNoBuild') fn=case_gate_anArchivedArtefactOwesNoBuild ;;
    'gate#aModuleResourceOwesTheBuild') fn=case_gate_aModuleResourceOwesTheBuild ;;
    'gate#theGateNamesTheProfileItRead') fn=case_gate_theGateNamesTheProfileItRead ;;
    'gate#theChildReadsTheProfileOfTheTreeItWorksIn') fn=case_gate_theChildReadsTheProfileOfTheTreeItWorksIn ;;
    'gate#aThirdFullRunInOneSessionIsRefused') fn=case_gate_aThirdFullRunInOneSessionIsRefused ;;
    'mutation#everyDeclaredRecordApplies') fn=case_mutation_everyDeclaredRecordApplies ;;
    'mutation#aStaleRecordIsNamed') fn=case_mutation_aStaleRecordIsNamed ;;
    'measure#namesTheOwedChecksInOneBlock') fn=case_measure_namesTheOwedChecksInOneBlock ;;
    'measure#printsTheNumbersTheReviewerAsksFor') fn=case_measure_printsTheNumbersTheReviewerAsksFor ;;
    'measure#refusesToGuessWhichChangeItMeasures') fn=case_measure_refusesToGuessWhichChangeItMeasures ;;
    'measure#saysHowManyTalliesItWithheld') fn=case_measure_saysHowManyTalliesItWithheld ;;
    'measure#printsTheSuiteCountAndTheGateTiming') fn=case_measure_printsTheSuiteCountAndTheGateTiming ;;
    'prompt#workerIsHandedTheMeasureBlock') fn=case_prompt_workerIsHandedTheMeasureBlock ;;
    'run#oneItemOrderIsFixed') fn=case_run_oneItemOrderIsFixed ;;
    'run#childClaimWithoutCommitIsBlocked') fn=case_run_childClaimWithoutCommitIsBlocked ;;
    'run#anEmptyTreeIsRefusedBeforeTheGateChild') fn=case_run_anEmptyTreeIsRefusedBeforeTheGateChild ;;
    'run#blockedItemIsEscalatedWithItsReason') fn=case_run_blockedItemIsEscalatedWithItsReason ;;
    'run#aFailedEscalationWriteIsNamed') fn=case_run_aFailedEscalationWriteIsNamed ;;
    'run#aFailedUnpickIsNamed') fn=case_run_aFailedUnpickIsNamed ;;
    'run#childStartsInTheProjectRoot') fn=case_run_childStartsInTheProjectRoot ;;
    'run#childReportsWhileItWaits') fn=case_run_childReportsWhileItWaits ;;
    'run#theWaitEndsWhenTheChildDoes') fn=case_run_theWaitEndsWhenTheChildDoes ;;
    'run#theProgressLineKeepsItsInterval') fn=case_run_theProgressLineKeepsItsInterval ;;
    'run#headerNamesTheLiveView') fn=case_run_headerNamesTheLiveView ;;
    'run#prescreenAnnouncesItselfBeforeItReturns') fn=case_run_prescreenAnnouncesItselfBeforeItReturns ;;
    'run#everyPhaseBoundaryIsPrinted') fn=case_run_everyPhaseBoundaryIsPrinted ;;
    'run#childOutlastingTheWallClockIsCut') fn=case_run_childOutlastingTheWallClockIsCut ;;
    'run#theChildCapIsNeverBelowWhatOneItemsGateOwes') fn=case_run_theChildCapIsNeverBelowWhatOneItemsGateOwes ;;
    'state#theItemFloorIsOnlyBelowWhatOneItemsGateOwes') fn=case_state_theItemFloorIsOnlyBelowWhatOneItemsGateOwes ;;
    'run#childIsEndedAtTheRunsDeadline') fn=case_run_childIsEndedAtTheRunsDeadline ;;
    'run#aVanishedWriterEndsTheWait') fn=case_run_aVanishedWriterEndsTheWait ;;
    'run#aSlowChildIsNotMistakenForAVanishedWriter') fn=case_run_aSlowChildIsNotMistakenForAVanishedWriter ;;
    'run#reviewerEndedAtTheRunsDeadlineIsNotADecision') fn=case_run_reviewerEndedAtTheRunsDeadlineIsNotADecision ;;
    'run#aReviewerEndedAtItsTurnBoundIsNotARefusal') fn=case_run_aReviewerEndedAtItsTurnBoundIsNotARefusal ;;
    'run#detachedProcessInTheWorktreeIsEndedByTheCap') fn=case_run_detachedProcessInTheWorktreeIsEndedByTheCap ;;
    'run#treeOfAChildTheCapEndedIsNotCommitted') fn=case_run_treeOfAChildTheCapEndedIsNotCommitted ;;
    'run#treeOfACapEndedChildIsKeptAsAPatch') fn=case_run_treeOfACapEndedChildIsKeptAsAPatch ;;
    'run#noProcessOfASignalledRunOutlivesIt') fn=case_run_noProcessOfASignalledRunOutlivesIt ;;
    'run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted') fn=case_run_treeOfACapEndedReworkOfARefusingRoundIsNotCommitted ;;
    'run#childThatNeverStartedIsTheRunsFault') fn=case_run_childThatNeverStartedIsTheRunsFault ;;
    'run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault') fn=case_run_aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault ;;
    'run#hostBelowTheFloorBlocksTheItemAndStops') fn=case_run_hostBelowTheFloorBlocksTheItemAndStops ;;
    'run#hostAboveTheFloorRunsTheItem') fn=case_run_hostAboveTheFloorRunsTheItem ;;
    'run#hostWithoutTheReadingPasses') fn=case_run_hostWithoutTheReadingPasses ;;
    'run#aHostWithoutThePlacementReadingIsRefused') fn=case_run_aHostWithoutThePlacementReadingIsRefused ;;
    'run#childWriteOutsideTheWorktreeIsNoted') fn=case_run_childWriteOutsideTheWorktreeIsNoted ;;
    'run#aWriterTheRunCanPlaceBlocksTheItem') fn=case_run_aWriterTheRunCanPlaceBlocksTheItem ;;
    'run#aChildIsGivenOneScratchDirectory') fn=case_run_aChildIsGivenOneScratchDirectory ;;
    'run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem') fn=case_run_aPeerWriteWhileAChildStandsDoesNotDecideTheItem ;;
    'run#theWorkersOwnShellWriteIsRecordedAndNotPlaced') fn=case_run_theWorkersOwnShellWriteIsRecordedAndNotPlaced ;;
    'run#dirtyBaseRefusesTheMergeAndNamesThePath') fn=case_run_dirtyBaseRefusesTheMergeAndNamesThePath ;;
    'run#strictOutsideWriteBlocksTheItem') fn=case_run_strictOutsideWriteBlocksTheItem ;;
    'run#strictOutsideWriteDiscardsTheReviewRound') fn=case_run_strictOutsideWriteDiscardsTheReviewRound ;;
    'run#leftoverBranchIsRecordedNotDropped') fn=case_run_leftoverBranchIsRecordedNotDropped ;;
    'run#branchThatAppearsWhileTheRunIsOpenIsSetAside') fn=case_run_branchThatAppearsWhileTheRunIsOpenIsSetAside ;;
    'run#mergesAFinishedAttemptAndClosesTheItem') fn=case_run_mergesAFinishedAttemptAndClosesTheItem ;;
    'run#doesNotMergeAnAttemptWithoutAGreenVerification') fn=case_run_doesNotMergeAnAttemptWithoutAGreenVerification ;;
    'run#anAdoptionTheTreeRefusesIsRecordedAsUnmergeable') fn=case_run_anAdoptionTheTreeRefusesIsRecordedAsUnmergeable ;;
    'run#mergesItsCommittedBranchBeforeClosing') fn=case_run_mergesItsCommittedBranchBeforeClosing ;;
    'run#aMergedBranchIsRemoved') fn=case_run_aMergedBranchIsRemoved ;;
    'run#theEndSweepsTheLandedBranchesItMade') fn=case_run_theEndSweepsTheLandedBranchesItMade ;;
    'run#theRemoteSweepKeepsAnOpenPullRequest') fn=case_run_theRemoteSweepKeepsAnOpenPullRequest ;;
    'run#theSweepComparesAgainstTheFetchedBase') fn=case_run_theSweepComparesAgainstTheFetchedBase ;;
    'run#theBaseAdvancesWithEveryMerge') fn=case_run_theBaseAdvancesWithEveryMerge ;;
    'run#staleTestResultsAreNotThisChildsGate') fn=case_run_staleTestResultsAreNotThisChildsGate ;;
    'run#doesNotCloseOnAMergeTheBaseCannotTake') fn=case_run_doesNotCloseOnAMergeTheBaseCannotTake ;;
    'run#recordsAFailedReviewOnTheBranch') fn=case_run_recordsAFailedReviewOnTheBranch ;;
    'run#announcesAndRecordsTheReviewRound') fn=case_run_announcesAndRecordsTheReviewRound ;;
    'run#thePreGateReviewRunsBeforeTheGate') fn=case_run_thePreGateReviewRunsBeforeTheGate ;;
    'run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun') fn=case_run_aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun ;;
    'run#dirtyKeptWorktreeIsARecordedRefusal') fn=case_run_dirtyKeptWorktreeIsARecordedRefusal ;;
    'run#resetWorktreeDiscardsWhatItNames') fn=case_run_resetWorktreeDiscardsWhatItNames ;;
    'run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard') fn=case_run_resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard ;;
    'run#resetWorktreeNamesTheDirtItCannotDiscard') fn=case_run_resetWorktreeNamesTheDirtItCannotDiscard ;;
    'run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds') fn=case_run_aDirtyStartNamesTheItemWhoseWorkTheTreeHolds ;;
    'run#aRefusedStartKeepsTheLeftoversItFound') fn=case_run_aRefusedStartKeepsTheLeftoversItFound ;;
    'run#placesTheModuleAndGoesOnWhenTheOnlyDirtIsThePin') fn=case_run_placesTheModuleAndGoesOnWhenTheOnlyDirtIsThePin ;;
    'run#aPinNoCloneHoldsIsRefusedBeforeThePrescreen') fn=case_run_aPinNoCloneHoldsIsRefusedBeforeThePrescreen ;;
    'run#theStartAfterTheRunsOwnEndedChildIsNotRefused') fn=case_run_theStartAfterTheRunsOwnEndedChildIsNotRefused ;;
    'run#aRetriedStartStopsWithItsOwnReason') fn=case_run_aRetriedStartStopsWithItsOwnReason ;;
    'run#aSkipClearsTheInProgressMark') fn=case_run_aSkipClearsTheInProgressMark ;;
    'run#staleSkipAsksItsQuestion') fn=case_run_staleSkipAsksItsQuestion ;;
    'run#staleSkipKeepsTheWorkersQuestion') fn=case_run_staleSkipKeepsTheWorkersQuestion ;;
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
    'run#aFencedClosingBlockIsACompleteAnswer') fn=case_run_aFencedClosingBlockIsACompleteAnswer ;;
    'run#escalationCarriesTheDecision') fn=case_run_escalationCarriesTheDecision ;;
    'run#mayCloseNoLeavesTheIssueOpen') fn=case_run_mayCloseNoLeavesTheIssueOpen ;;
    'run#metricsRecordedPerItem') fn=case_run_metricsRecordedPerItem ;;
    'run#childUsageIsRecordedPerItem') fn=case_run_childUsageIsRecordedPerItem ;;
    'run#aRedGateIsBlockedByTheTail') fn=case_run_aRedGateIsBlockedByTheTail ;;
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
    'adapter#theChildIsStartedWithoutContextFiles') fn=case_adapter_theChildIsStartedWithoutContextFiles ;;
    'adapter#theChildIsStartedWithTheResultBound') fn=case_adapter_theChildIsStartedWithTheResultBound ;;
    'adapter#theChildIsStartedWithItsToolDirectoryOnPath') fn=case_adapter_theChildIsStartedWithItsToolDirectoryOnPath ;;
    'adapter#theKeptStreamIsBoundedAndItsRotationIsRecorded') fn=case_adapter_theKeptStreamIsBoundedAndItsRotationIsRecorded ;;
    'trim#aResultAboveTheBoundIsTrimmedAndNamed') fn=case_trim_aResultAboveTheBoundIsTrimmedAndNamed ;;
    'trim#aResultUnderTheBoundIsHandedOverWhole') fn=case_trim_aResultUnderTheBoundIsHandedOverWhole ;;
    'trim#theTrimIsRecorded') fn=case_trim_theTrimIsRecorded ;;
    'look#oneCallAnswersTheWholeTree') fn=case_look_oneCallAnswersTheWholeTree ;;
    'look#aResultOverTheBoundIsCutAndNamed') fn=case_look_aResultOverTheBoundIsCutAndNamed ;;
    'look#aPathScopesTheWalkToIt') fn=case_look_aPathScopesTheWalkToIt ;;
    'policy#aShellTreeSearchIsRefusedAndARecipeIsNot') fn=case_policy_aShellTreeSearchIsRefusedAndARecipeIsNot ;;
    'adapter#theChildIsStartedWithTheShellPolicy') fn=case_adapter_theChildIsStartedWithTheShellPolicy ;;
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
    'report#anItemOverTheTokenBoundIsFlagged') fn=case_report_anItemOverTheTokenBoundIsFlagged ;;
    'report#anItemBelowTheCallsPerTurnFloorIsNamed') fn=case_report_anItemBelowTheCallsPerTurnFloorIsNamed ;;
    'report#theSpendIsSplitByPhaseRoleAndGate') fn=case_report_theSpendIsSplitByPhaseRoleAndGate ;;
    'report#aStreamWithoutDurationsPrintsDash') fn=case_report_aStreamWithoutDurationsPrintsDash ;;
    'report#trendFiguresFromState') fn=case_report_trendFiguresFromState ;;
    'report#noRoundOrWallPrintsDashNotZero') fn=case_report_noRoundOrWallPrintsDashNotZero ;;
    'report#landingsAreTheStoredNumber') fn=case_report_landingsAreTheStoredNumber ;;
    'clean#interruptedSuiteRemovesItsScratchTree') fn=case_clean_interruptedSuiteRemovesItsScratchTree ;;
    'clean#reportsWithoutApplyAndRemovesWithIt') fn=case_clean_reportsWithoutApplyAndRemovesWithIt ;;
    'clean#reportsAnAgedItemDirectory') fn=case_clean_reportsAnAgedItemDirectory ;;
    'clean#sweepsOnlyWhatIsInTheBase') fn=case_clean_sweepsOnlyWhatIsInTheBase ;;
    'clean#interruptedSuiteEndsItsProcessGroup') fn=case_clean_interruptedSuiteEndsItsProcessGroup ;;
    'clean#reportsAndEndsAProcessLeftInAScratchTree') fn=case_clean_reportsAndEndsAProcessLeftInAScratchTree ;;
    'clean#reportsTheWorktreesScratchTreesToo') fn=case_clean_reportsTheWorktreesScratchTreesToo ;;
    'clean#worktreeDotSweepsTheOneRootOnce') fn=case_clean_worktreeDotSweepsTheOneRootOnce ;;
    'clean#endsARunWhoseTreeIsAlreadyDeleted') fn=case_clean_endsARunWhoseTreeIsAlreadyDeleted ;;
    'clean#suiteThatEndsNormallyEndsTheProcessItsCaseLeft') fn=case_clean_suiteThatEndsNormallyEndsTheProcessItsCaseLeft ;;
    'clean#reportsAWorktreeNoRunKeeps') fn=case_clean_reportsAWorktreeNoRunKeeps ;;
    'run#interruptedRunEndsTheChildItStarted') fn=case_run_interruptedRunEndsTheChildItStarted ;;
    'driver#aCaseThatAsksForNoCoreCompilesNothing') fn=case_driver_aCaseThatAsksForNoCoreCompilesNothing ;;
    'driver#aCaseThatAsksForTheCoreGetsItOrRefuses') fn=case_driver_aCaseThatAsksForTheCoreGetsItOrRefuses ;;
    'driver#theCoreIsCompiledOncePerSuite') fn=case_driver_theCoreIsCompiledOncePerSuite ;;
    'driver#aFileFilterRunsOnlyTheCasesTheDeltaOwns') fn=case_driver_aFileFilterRunsOnlyTheCasesTheDeltaOwns ;;
    'driver#aFileSetThatNamesNoCaseIsRefused') fn=case_driver_aFileSetThatNamesNoCaseIsRefused ;;
    'driver#anEmptyDeltaIsStatedWhenTheCallerAllowsIt') fn=case_driver_anEmptyDeltaIsStatedWhenTheCallerAllowsIt ;;
    'driver#aCoreFileOwesEveryCase') fn=case_driver_aCoreFileOwesEveryCase ;;
    'driver#everyCaseDeclaresTheFilesItCanReach') fn=case_driver_everyCaseDeclaresTheFilesItCanReach ;;
    'driver#theSuiteRunsItsCasesInAPool') fn=case_driver_theSuiteRunsItsCasesInAPool ;;
    'driver#theCaseBoundScalesWithThePool') fn=case_driver_theCaseBoundScalesWithThePool ;;
    'driver#aCaseThatBlocksIsEndedByItsOwnBound') fn=case_driver_aCaseThatBlocksIsEndedByItsOwnBound ;;
    'driver#aCaseInsideItsBoundKeepsItsOwnVerdict') fn=case_driver_aCaseInsideItsBoundKeepsItsOwnVerdict ;;
    'driver#aSuiteEndsInsideTheCeilingItsCallerNamed') fn=case_driver_aSuiteEndsInsideTheCeilingItsCallerNamed ;;
    'driver#aCeilingTheSuiteFitsInsideChangesNothing') fn=case_driver_aCeilingTheSuiteFitsInsideChangesNothing ;;
    'driver#aRunningCasePrintsItsOwnLines') fn=case_driver_aRunningCasePrintsItsOwnLines ;;
    'driver#aCaseThatLeavesAProcessBehindIsSweptWithIt') fn=case_driver_aCaseThatLeavesAProcessBehindIsSweptWithIt ;;
    'driver#aSecondSuiteInOneRepositoryIsRefused') fn=case_driver_aSecondSuiteInOneRepositoryIsRefused ;;
    'driver#noFixtureFifoIsOpenedWriteOnly') fn=case_driver_noFixtureFifoIsOpenedWriteOnly ;;
    'gate#theDeltaHandsASuiteTheBaseItMeasured') fn=case_gate_theDeltaHandsASuiteTheBaseItMeasured ;;
    'gate#theDeclarationCaseIsOwedFirst') fn=case_gate_theDeclarationCaseIsOwedFirst ;;
    'gate#aGreenLogNamesNoFailingCase') fn=case_gate_aGreenLogNamesNoFailingCase ;;
    'gate#aFailingCaseCarriesItsFactsAndItsRerun') fn=case_gate_aFailingCaseCarriesItsFactsAndItsRerun ;;
    'gate#twoFailuresNameTheFirstAndCountTheRest') fn=case_gate_twoFailuresNameTheFirstAndCountTheRest ;;
    'gate#aStepThatNamesNoCaseIsAttributedByItsStep') fn=case_gate_aStepThatNamesNoCaseIsAttributedByItsStep ;;
    'gate#theBlockStaysInsideTheRecordedBound') fn=case_gate_theBlockStaysInsideTheRecordedBound ;;
    'gate#theStepBoundIsClampedByTheCeilingTheCallerNamed') fn=case_gate_theStepBoundIsClampedByTheCeilingTheCallerNamed ;;
    'phase#oneCallPrintsThePhaseState') fn=case_phase_oneCallPrintsThePhaseState ;;
    'phase#aResultOverTheBoundIsCutAndNamed') fn=case_phase_aResultOverTheBoundIsCutAndNamed ;;
    'repeat#aRepeatReadIsNamedNotHandedOverAgain') fn=case_repeat_aRepeatReadIsNamedNotHandedOverAgain ;;
    'repeat#aRepeatAfterAnEditIsHandedOverWhole') fn=case_repeat_aRepeatAfterAnEditIsHandedOverWhole ;;
    'repeat#theRecordedZeroKeepsTheRepeatRuleOff') fn=case_repeat_theRecordedZeroKeepsTheRepeatRuleOff ;;
    'repeat#aBrokenLedgerLeavesTheResultAsReturned') fn=case_repeat_aBrokenLedgerLeavesTheResultAsReturned ;;
    'run#childPastItsTurnBoundIsCut') fn=case_run_childPastItsTurnBoundIsCut ;;
    'run#aRotatedStreamStillCountsTheTurnsItDropped') fn=case_run_aRotatedStreamStillCountsTheTurnsItDropped ;;
    'run#theRecordedZeroKeepsTheTurnBoundOff') fn=case_run_theRecordedZeroKeepsTheTurnBoundOff ;;
    'run#eachChildIsBoundedByItsOwnRolesTurns') fn=case_run_eachChildIsBoundedByItsOwnRolesTurns ;;
    'run#aRoleWithNoRecordedTurnBoundRunsUnbounded') fn=case_run_aRoleWithNoRecordedTurnBoundRunsUnbounded ;;
    'run#childPastItsContextBoundIsCut') fn=case_run_childPastItsContextBoundIsCut ;;
    'run#theRecordedZeroKeepsTheContextBoundOff') fn=case_run_theRecordedZeroKeepsTheContextBoundOff ;;
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

# The scratch tree of the one case being run: the driver gives every case a root of its own (`LOOP_CASE_TMP`, a
# directory below the suite's `LOOP_TMP`), so a tree a case leaves behind is inside the root that case's own sweep
# reads — and a tree that outlives the case is never another case's to trip over (issue #260). When the variable is
# not set the suite's root is used, which is the shape a hand-run of one case function keeps.
scratch() { mktemp -d "${LOOP_CASE_TMP:-$LOOP_TMP}/case.XXXXXX"; }

# A throwaway project: enough of this repository's shape that the loop's own scripts run unchanged — the
# profile the issue tooling discovers, a directory standing in for the submodule, a `local.prop*` the
# worktree does not inherit, and the gitignored skills it does not inherit either. `core/build` is ignored by
# its whole path and not by `build/`, so a *symlink* at that recorded scratch path is untracked and the
# removal has to refuse it on its own account (`worktree#keepsSourcesNotBuildOutput`).
# How long a case waits for a stub child to appear (issue #308): a host fact with a wide margin, not a property of
# the loop. Measured 2026-10-25, two Gradle compiles of a second session on the same host pushed an engine's first
# child past the 20 s that four of these waits allowed, and `run#childOutlastingTheWallClockIsCut` then read it as
# "the stub child never started" — a host's slowness reported as a defect in the engine. 60 s is three times the
# worst reading and still ends a case that is genuinely stuck.
STUB_READY_TICKS=600   # 60 s at 0.1 s per tick
wait_for_stub_child() { # wait_for_stub_child <ready file> → 0 when it appeared
  local i
  for i in $(seq 1 "$STUB_READY_TICKS"); do
    [ -e "$1" ] && return 0
    sleep 0.1
  done
  return 1
}

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
  # The fixture keeps a second gitignored skill directory beside the generated one so the seeding case can assert
  # what the engine does *not* copy: only `openspec-*` is seeded, and a versioned skill arrives with the checkout
  # (issue #255 — the retired `fix-loop` skill was the one that was copied and nothing read it).
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

# A profile whose child may run `<seconds>` and no longer (issue #663). The bound the run gives a child is the
# larger of the floor and what one item's gate owes, so a case that shortens the floor alone would still be given
# the gate's seconds: this writes both — the floor as asked, and the gate's number blank, which is what a project
# with no build system records. The case keeps its own `grep -qx` on the floor it asked for.
conf_with_child_cap() { # <seconds> <out file>
  sed -e "s/^LOOP_CHILD_MAX_SECONDS=.*/LOOP_CHILD_MAX_SECONDS=$1/" \
      -e 's/^LOOP_ITEM_GATE_SECONDS=.*/LOOP_ITEM_GATE_SECONDS=/' "$LOOP_CONF" > "$2"
}

scenario() { printf '%s\n' "$LOOP_FIXTURES/scenarios/$1"; }

# A project that carries the readiness checker its profile names and one prepared change in the *source* tree — the
# shape a tier-B item is filed with (`finding-shape-at-filing`). The change passes the checker unless the case asks
# for the unready one, whose `## Open decisions` names a fork. Nothing is committed: an unarchived change is
# gitignored, which is why the run has to copy it in.
tier_b_change() { # <project> <change name> [ready|unready]
  local p="$1" name="$2" ready="${3:-ready}"
  cp "$LOOP_PROJECT_ROOT/tools/check-change-readiness.sh" "$p/tools/check-change-readiness.sh" ||
    fail "could not copy the readiness checker into the project"
  # The checker is tracked and an *unarchived* change is ignored, exactly as this project has it: the merge that
  # lands an item refuses a dirty project tree, and an unarchived change is invisible to git by design.
  printf 'openspec/changes/*/\n!openspec/changes/archive/\n' >> "$p/.gitignore"
  git -C "$p" add .gitignore tools/check-change-readiness.sh >/dev/null 2>&1 &&
    git -C "$p" -c user.email=selftest@example.invalid -c user.name=selftest \
      commit -qm 'the readiness checker, and the changes directory ignored' >/dev/null 2>&1 ||
    fail "could not commit the project's readiness checker"
  mkdir -p "$p/openspec/changes/$name/specs/demo" "$p/openspec/changes/$name/evidence" ||
    fail "could not create the prepared change"
  printf '# Proposal — the prepared change\n\nsurface: 1 case added — `grep -c demo tools/loop-cases.sh`\n' \
    > "$p/openspec/changes/$name/proposal.md"
  if [ "$ready" = ready ]; then
    printf '# Design — the prepared change\n\n## Open decisions\n\nnone\n' > "$p/openspec/changes/$name/design.md"
  else
    printf '# Design — the prepared change\n\n## Open decisions\n\n- which bound does the row ship with?\n' \
      > "$p/openspec/changes/$name/design.md"
  fi
  printf '## ADDED Requirements\n\n### Requirement: demo\n\nThe demo exists.\n\n#### Scenario: the demo\n\n- **WHEN** x\n- **THEN** y\n- **Case**: `demo#theCase`\n' \
    > "$p/openspec/changes/$name/specs/demo/spec.md"
  printf 'the evidence the change cites\n' > "$p/openspec/changes/$name/evidence/readme.txt"
}

init_state() { # a run state the pre-screen needs before it can write its queue
  run_state "$1" init --items "${2:-1}" --minutes "${3:-90}" >/dev/null || fail "state init failed"
}

gh_log() { printf '%s\n' "$GH_STUB_RECORD"; }
pi_log() { printf '%s\n' "$PI_STUB_LOG"; }

# The stub child's fixtures. `worker` is the report the worker prints, `reviewer` the reviewer's, and the
# action leaves a real change in the tree so the wrapper's own commit and verification run for real.
#
# The tree work belongs to the **change** child and not to the tail child the run starts after the gate (issue #744):
# the tail's job is the archive the gate child already left, and a fixture that wrote the change a second time would
# overwrite it — measured on `run#aCorrectiveRoundIsHandedTheCorrections`, whose round-2 worker's content was
# rewritten by the round-2 tail before the run measured the round's diff. It does not belong to the **gate** child
# either (issue #758): that child is started on a change already in the tree, so an action that rewrote it would
# replace the diff the pre-gate round has just read.
stub_write() { # stub_write <dir> <worker-report> <reviewer-report> [extra-action]
  local dir="$1" worker="$2" reviewer="$3" extra="${4:-}"
  printf '%s\n' "$worker" > "$dir/worker-report.txt"
  printf '%s\n' "$reviewer" > "$dir/reviewer-report.txt"
  printf '%s\n' "$STUB_GATE_REPORT" > "$dir/gate-report.txt"
  {
    printf 'case "$1" in\n'
    printf '  worker-tail*) ;;\n'
    printf '  worker-gate*) ;;\n'
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
  export PI_STUB_REPORT_GATE="$dir/gate-report.txt"
  export PI_STUB_ACTION="$dir/worker-action.sh"
  export PI_STUB_LOG="$dir/pi.log"; : > "$PI_STUB_LOG"
  export GH_STUB_RECORD="$dir/gh.log"; : > "$GH_STUB_RECORD"
}

# The report the **change** child prints when the run has deferred the gate to a child of its own (issue #758): the
# diff marker in place of a gate line, and no verdict — the child that runs the gate prints the gate line and the tail
# after it writes the verdict. A case that models a gated item under the pre-gate shape uses this one.
STUB_CHANGE_REPORT='LOOP-DIFF-READY: fix-demo
LOOP-CHANGE: fix-demo
LOOP-ROOT-CAUSE: the fixture has exactly one cause
LOOP-RED-TEST: Demo#case fails on HEAD — XML tests=1 failures=1 (2026-01-01T00:00:00Z)'

STUB_WORKER_REPORT='LOOP-VERDICT: closed
LOOP-CHANGE: fix-demo
LOOP-ROOT-CAUSE: the fixture has exactly one cause
LOOP-RED-TEST: Demo#case fails on HEAD — XML tests=1 failures=1 (2026-01-01T00:00:00Z)
LOOP-GATE: tests=1 failures=0 (fixture)'

# The report the child that **ran the gate** prints since issue #744: the gate line, and no verdict — the verdict is
# the tail child's to write. A case that models a real gated item uses this one, so the arm that starts the tail from
# a report that answers nothing itself is the arm the case exercises.
STUB_GATE_REPORT='LOOP-CHANGE: fix-demo
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
#
# The count is per **item**, and the worker phase is two children since issue #744: the gate child increments it and
# the tail child of the same item — the prompt whose name carries `-tail` — reads the value it left, so a body keyed
# on `$n` writes item n's tree once and the tail sees the same n instead of starting item n+1.
stub_per_item_worker() { # stub_per_item_worker <dir> <worker-report> <reviewer-report> <body>
  local dir="$1"
  printf '%s\n' "$2" > "$dir/worker-report.txt"
  printf '%s\n' "$3" > "$dir/reviewer-report.txt"
  {
    printf 'n=$(cat "%s" 2>/dev/null || printf 0)\n' "$dir/per-item-count"
    printf 'case "$1" in\n'
    printf '  worker-tail*) ;;\n'
    printf '  worker-gate*) ;;\n'
    printf '  worker*)\n'
    printf '    n=$((n + 1)); printf "%%s\\n" "$n" > "%s"\n' "$dir/per-item-count"
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
  # files: tools/loop/state.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local p sf out status d conf
  # The streak this case reads is the pair, whatever the project records (issue #804): what it asserts is the
  # mechanism — a refusal counted, a cut not — and a case that took today's value would stop testing the pair the
  # day the project raised it. The recorded value has its own case (`state#theStreakIsTheRecordedCount`).
  d=$(scratch); conf="$d/loop.conf"
  sed -e 's/^LOOP_REFUSAL_STREAK_STOP=.*/LOOP_REFUSAL_STREAK_STOP=2/' "$LOOP_CONF" > "$conf" ||
    fail "could not write the loop configuration"
  export LOOP_CONF="$conf"

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
  assert_contains "$out" "2 consecutive" "the reason is named"

  # 4b. a round the run's own bound ended is not a block (issue #785): it is a phase the next run resumes, so it
  # neither stops the run nor counts towards the streak — the pair a stop is read from holds two **blocks**, and a
  # block that follows a cut is the first of its own pair.
  p=$(new_project)
  init_state "$p" 6 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2},{"issue":23,"class":"bug","verdict":"eligible","rank":3},{"issue":24,"class":"bug","verdict":"eligible","rank":4},{"issue":25,"class":"bug","verdict":"eligible","rank":5}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result cut --verdict cut >/dev/null
  run_state "$p" done 22 --result cut --verdict cut >/dev/null
  out=$(run_state "$p" next); status=$?
  assert_eq "0" "$status" "two consecutive cuts do not stop the run"
  assert_eq "23" "$out" "the next candidate stands behind them"
  run_state "$p" done 23 --result blocked --verdict blocked >/dev/null
  out=$(run_state "$p" next); status=$?
  assert_eq "0" "$status" "a block after a cut is the first of its own pair, not the second of the cut's"
  assert_eq "24" "$out" "so the queue still hands out the next candidate"
  run_state "$p" done 24 --result blocked --verdict blocked >/dev/null
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "3" "$status" "two blocks in a row still stop the run"
  assert_contains "$out" "2 consecutive" "with the reason named"

  # 5. less time left than the next item's floor
  p=$(new_project)
  init_state "$p" 3 30
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "3" "$status" "a remaining time below the floor stops the run"
  assert_contains "$out" "floor" "the floor is named"
}

# The streak a run stops on is read for one prediction: a **refusal** predicts the next item's failure — the same
# gate, the same provider, the same tree — and a block the run's own bound caused predicts nothing, because the next
# item starts a fresh worker on a fresh tree (issue #786). Measured 2026-11-05: five of six runs stopped on the
# streak with 758 of 3 211 budgeted minutes used, and six of that day's eight blocks were bound cuts — the first run
# that carried the per-role turn bounds ended after 156 of its 480 minutes, with three of six items' workers ended
# at exactly 110 turns. The pair therefore holds refusals only, and a state written before the mechanism existed
# (two `blocked` items and no `stopReason`) still stops the run, which is the reading it always had.
case_state_aBlockAtTheRunsBoundIsNoRefusal() {
  # files: tools/loop/state.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local p out status d conf
  # The pair, for the reason `state#nextHonoursEveryStopCondition` pins it: this case is about which block may sit
  # in the streak, not about how long the streak is (issue #804).
  d=$(scratch); conf="$d/loop.conf"
  sed -e 's/^LOOP_REFUSAL_STREAK_STOP=.*/LOOP_REFUSAL_STREAK_STOP=2/' "$LOOP_CONF" > "$conf" ||
    fail "could not write the loop configuration"
  export LOOP_CONF="$conf"
  p=$(new_project)
  init_state "$p" 8 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2},{"issue":23,"class":"bug","verdict":"eligible","rank":3},{"issue":24,"class":"bug","verdict":"eligible","rank":4},{"issue":25,"class":"bug","verdict":"eligible","rank":5}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result blocked --verdict blocked --stop-reason bound >/dev/null || fail "done #21 failed"
  out=$(run_state "$p" next); status=$?
  assert_eq "0" "$status" "a block the run's own bound caused does not stop the run"
  assert_eq "22" "$out" "the queue hands out the next candidate"
  run_state "$p" done 22 --result blocked --verdict blocked --stop-reason bound >/dev/null || fail "done #22 failed"
  out=$(run_state "$p" next); status=$?
  assert_eq "0" "$status" "and two of them in a row are no pair either"
  assert_eq "23" "$out" "because a bound says nothing about the item behind it"
  # A refusal is what holds a pair — and it holds it with another refusal, not with a bound: the refusal after two
  # bound cuts is the first of its own pair, so one refusal never stops a run on its own.
  run_state "$p" done 23 --result blocked --verdict blocked --stop-reason refused >/dev/null || fail "done #23 failed"
  out=$(run_state "$p" next); status=$?
  assert_eq "0" "$status" "one refusal does not stop the run"
  assert_eq "24" "$out" "its own pair has not started"
  run_state "$p" done 24 --result blocked --verdict blocked >/dev/null || fail "done #24 failed"
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "3" "$status" "two refusals in a row stop the run"
  assert_contains "$out" "2 consecutive refusals ended blocked" "and the reason names the count it read"
  # A state a run wrote before the mechanism existed carries two `blocked` items and no `stopReason` at all: the
  # reading it always had is the reading it keeps, so a field that arrived later turns no block into work.
  p=$(new_project)
  init_state "$p" 5 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2},{"issue":23,"class":"bug","verdict":"eligible","rank":3}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result blocked --verdict blocked >/dev/null || fail "done #21 failed"
  run_state "$p" done 22 --result blocked --verdict blocked >/dev/null || fail "done #22 failed"
  assert_eq "" "$(jq -r '.items[0].stopReason // ""' "$(state_file "$p")")" "an older record carries no mechanism"
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "3" "$status" "and its two blocks still stop the run, as they always did"
  assert_contains "$out" "2 consecutive refusals" "with the same reason"
}

# The streak is the run's own recorded count, not the pair the engine shipped with (issue #804). A pair is the
# shortest evidence a prediction can rest on, and over 2026-11-05/06 it was the one thing that ended eight of twelve
# runs with 55–90 % of their budget unused; a project that knows its refusals are one item's bad luck, not its tree's
# state, raises the count and the run keeps working. The case writes a count of its own — one the configuration does
# not carry would satisfy an assertion read from today's value and still be wrong.
case_state_theStreakIsTheRecordedCount() {
  # files: tools/loop/state.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local d p conf out status
  d=$(scratch); p=$(new_project)
  conf="$d/loop.conf"
  sed -e 's/^LOOP_REFUSAL_STREAK_STOP=.*/LOOP_REFUSAL_STREAK_STOP=4/' "$LOOP_CONF" > "$conf" ||
    fail "could not write the loop configuration"
  grep -qx 'LOOP_REFUSAL_STREAK_STOP=4' "$conf" || fail "this case could not set the streak in loop.conf"
  export LOOP_CONF="$conf"
  init_state "$p" 8 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2},{"issue":23,"class":"bug","verdict":"eligible","rank":3},{"issue":24,"class":"bug","verdict":"eligible","rank":4},{"issue":25,"class":"bug","verdict":"eligible","rank":5},{"issue":26,"class":"bug","verdict":"eligible","rank":6}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result blocked --verdict blocked >/dev/null || fail "done #21 failed"
  run_state "$p" done 22 --result blocked --verdict blocked >/dev/null || fail "done #22 failed"
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "0" "$status" "the pair the engine shipped with does not stop a run on four"
  assert_eq "23" "$out" "the queue hands out the next candidate"
  run_state "$p" done 23 --result blocked --verdict blocked >/dev/null || fail "done #23 failed"
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "0" "$status" "and three refusals are still short of four"
  assert_eq "24" "$out" "with the fourth candidate handed out"
  run_state "$p" done 24 --result blocked --verdict blocked >/dev/null || fail "done #24 failed"
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "3" "$status" "four refusals in a row stop the run"
  assert_contains "$out" "4 consecutive refusals ended blocked" "and the reason names the count the configuration records"
}

# The floor a caller is refused by is the run's own measurement (issue #206): the cheapest wall time of the
# items this run has finished, times the spread the measurements show (1.5). Both numbers the refusal compared
# are named, so an operator can see whether the floor is honest. The configured minimum is not a lower bound of
# a derived floor any more — it governs only a run with no history (issue #674): one constant for every item
# made a run of 10-15 min skips stop up to ~50 min before its deadline.
case_state_floorIsDerivedFromTheFinishedItemsWallTimes() {
  # files: tools/loop/state.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local p conf_floor out status i
  local queue='[{"issue":21,"class":"bug","verdict":"eligible","rank":1},
                {"issue":22,"class":"bug","verdict":"eligible","rank":2},
                {"issue":23,"class":"bug","verdict":"eligible","rank":3},
                {"issue":24,"class":"bug","verdict":"eligible","rank":4}]'
  conf_floor=$(sed -n 's/^LOOP_ITEM_FLOOR_MINUTES=//p' "$LOOP_CONF")
  [ -n "$conf_floor" ] || fail "the configuration records no LOOP_ITEM_FLOOR_MINUTES"

  # three finished items of 60 min each: the measurement the state already holds decides the floor (60 min x
  # 1.5 = 90 min)
  p=$(new_project)
  init_state "$p" 9 28
  printf '%s' "$queue" | run_state "$p" queue >/dev/null
  for i in 21 22 23; do
    run_state "$p" done "$i" --result done --verdict closed --wall 3600 >/dev/null || fail "done #$i failed"
  done
  out=$(run_state "$p" next 2>&1); status=$?
  assert_eq "3" "$status" "a floor above the remaining time stops the run"
  assert_contains "$out" "90 min floor" "the floor is the measured one, not the constant"
  assert_contains "$out" "cheapest 60 min over 3 finished items" "the refusal names the measurement it derived"
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

  # three finished skips of 10 min each: the cheapest measured item (10 min x 1.5 = 15 min) admits the next
  # item, and the configured minimum no longer refuses the tail a cheap history measured (issue #674)
  p=$(new_project)
  init_state "$p" 9 28
  printf '%s' "$queue" | run_state "$p" queue >/dev/null
  for i in 21 22 23; do
    run_state "$p" done "$i" --result skipped --verdict needs-decision --wall 600 >/dev/null || fail "done #$i failed"
  done
  out=$(run_state "$p" next); status=$?
  assert_eq "0" "$status" "a cheap history admits the tail instead of keeping the configured minimum"
  assert_eq "24" "$out" "the next candidate after three cheap items"

  # the **cheapest** measured item, not the median: two 10 min skips and one 60 min change (600, 600, 3600) have
  # a median of 10 min as well, but a history of [600, 3600, 3600] does not — there the median is 60 min and
  # its 90 min floor would refuse the cheap tail the run has just shown it can pay for. 80 min left is below
  # the median-derived floor and above the 15 min the cheapest item derives.
  p=$(new_project)
  init_state "$p" 9 80
  printf '%s' "$queue" | run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result skipped --verdict needs-decision --wall 600 >/dev/null || fail "done #21 failed"
  for i in 22 23; do
    run_state "$p" done "$i" --result done --verdict closed --wall 3600 >/dev/null || fail "done #$i failed"
  done
  out=$(run_state "$p" next); status=$?
  assert_eq "0" "$status" "a cheap item among expensive ones admits the tail"
  assert_eq "24" "$out" "the tail is taken even though the median would have refused it"

  # the #663 relation the floor used to hold, reversed (issue #674): a run whose cheapest measured item is a
  # skip derives a floor below what one item's gate owes, because the item started there may be the cheap kind
  # the history measured. That is the accepted exposure: if it is a gated item it is ended at the run's own
  # deadline instead of overrunning it, and the work it left is kept for the next run
  # (`run#childIsEndedAtTheRunsDeadline`). The configured minimum still holds the gate's cost for a run with
  # no history, which `state#theItemFloorIsOnlyBelowWhatOneItemsGateOwes` reads.
  owes=$(sed -n 's/^LOOP_ITEM_GATE_SECONDS=//p' "$LOOP_CONF")
  if [ -n "$owes" ]; then
    [ $((15 * 60)) -lt "$owes" ] ||
      fail "this case assumes the cheap floor (900 s) is below what one item's gate owes ($owes s), so it cannot observe the reversal"
  fi
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
  wait_for_stub_child "$block.ready" || true
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
  wait_for_stub_child "$block.ready" || true
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
  # every child — the worker phase's two children and the reviewer — went through the configured value
  assert_eq "3" "$(grep -c 'AGENT-CMD' "$d/agent.log")" "every child invocation expands the configured command"
  assert_eq "3" "$(grep -c '^invocation' "$PI_STUB_LOG")" "and every one arrived at the child"
}

# A role can be started by a command of its own — how a project puts its reviewer on a cheaper model without
# touching a phase. The key is derived from the prompt's name, so the case asserts both halves: the reviewer's
# child went through the configured command, and no child of the other roles did.
case_state_aRolesOwnCommandStartsOnlyThatRolesChild() {
  # files: tools/loop/fixtures/pi tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf out
  d=$(scratch); p=$(new_project)
  cat > "$d/worker-agent.sh" <<EOF
#!/usr/bin/env bash
printf 'WORKER-CMD\n' >> "$d/agent.log"
exec "$LOOP_FIXTURES/pi" "\$@"
EOF
  cat > "$d/reviewer-agent.sh" <<EOF
#!/usr/bin/env bash
printf 'REVIEWER-CMD\n' >> "$d/agent.log"
exec "$LOOP_FIXTURES/pi" "\$@"
EOF
  chmod +x "$d/worker-agent.sh" "$d/reviewer-agent.sh"
  : > "$d/agent.log"
  # The seam is a configuration key, so the case sets it where the loop reads it — the role's key beside the
  # project's one, and the roles the case does not name keep it.
  conf="$d/loop.conf"
  sed -e "s|^LOOP_AGENT_CMD=.*|LOOP_AGENT_CMD=\"$d/worker-agent.sh --print\"|" \
      -e "s|^LOOP_AGENT_CMD_REVIEWER=.*|LOOP_AGENT_CMD_REVIEWER=\"$d/reviewer-agent.sh --print\"|" "$LOOP_CONF" > "$conf"
  export LOOP_CONF="$conf"
  out=$(run_one_item "$p" "$d" 2>&1) || fail "the run failed with a role-scoped agent command"
  # The one-item fixture starts three children — the worker's two and the reviewer's one (the sibling case's own
  # count) — so the split is visible in which command each of them expanded.
  assert_eq "1" "$(grep -c '^REVIEWER-CMD' "$d/agent.log")" \
    "the reviewer's child went through the role's own command"
  assert_eq "2" "$(grep -c '^WORKER-CMD' "$d/agent.log")" \
    "and every other child went through the project's one"
  assert_contains "$out" "agent-roles=reviewer" "the run's own header names the role that was given its own command"
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
  run_state "$p" done 21 --result blocked --verdict blocked --stop-reason bound >/dev/null ||
    fail "a blocked item recording a bound the run enforces was refused"
  run_state "$p" done 22 --result blocked --verdict blocked --stop-reason unmergeable >/dev/null ||
    fail "a blocked item recording an unmergeable base was refused"
  run_state "$p" done 23 --result blocked --verdict blocked >/dev/null ||
    fail "a block neither mechanism caused is still a block"
  f=$(state_file "$p")
  assert_eq "bound" "$(jq -r '.items[] | select(.issue == 21) | .stopReason' "$f")" \
    "a block the run's own bound caused records the mechanism of its own (issue #786)"
  assert_eq "unmergeable" "$(jq -r '.items[] | select(.issue == 22) | .stopReason' "$f")" \
    "and a base that refused the merge as the other"
  assert_eq "" "$(jq -r '.items[] | select(.issue == 23) | .stopReason // ""' "$f")" \
    "a block neither mechanism caused carries none"
  # Both usage errors are refused *before* the record is written, so the state is the byte-identical file it was:
  # asserting only the exit status would leave a writer that validates after `patch_state` unnoticed (issue #280,
  # review round 1 — the clause was in the spec and no case read the state).
  local before
  before=$(cat "$f")
  out=$(run_state "$p" done 23 --result blocked --verdict blocked --stop-reason abandoned 2>&1); status=$?
  assert_eq "2" "$status" "a mechanism outside the vocabulary is a usage error"
  assert_contains "$out" "refused, unmergeable or bound" "and the refusal names the three the record accepts"
  assert_eq "$before" "$(cat "$f")" "and the state is not written, so the field cannot become prose"
  out=$(run_state "$p" done 23 --result done --verdict closed --stop-reason refused 2>&1); status=$?
  assert_eq "2" "$status" "a stop reason belongs to a block, not to a closure"
  assert_eq "$before" "$(cat "$f")" "and that one writes nothing either"
  out=$(run_state "$p" done 23 --result done --verdict closed --stop-reason bound 2>&1); status=$?
  assert_eq "2" "$status" "bound is the one mechanism a cut carries, and a closure is neither a block nor a cut"
  assert_eq "$before" "$(cat "$f")" "and that one writes nothing either"
  # A second account of an item joins the note it already has and fills no mechanism the first record left empty:
  # the field is the blocked attempt's own fact, not something a later account may add (issue #284's rule).
  run_state "$p" done 23 --result blocked --verdict blocked --stop-reason refused \
    --note "a second account of the same item" >/dev/null || fail "a second account of item 23 was refused"
  assert_eq "" "$(jq -r '.items[] | select(.issue == 23) | .stopReason // ""' "$f")" \
    "a mechanism that arrives with the second account does not fill the first record's"
  assert_eq "3" "$(jq -r '.items | length' "$f")" "and the item keeps one record"
}

# An adoption the tree refused is recorded on the pre-screen's own entry for that item, never on a record of the
# run's — the item was not worked, so there is no block to carry the mechanism (issue #794). The writer therefore
# keeps the verdict, the reason and the branch the pre-screen read, adds the mechanism and the refusal beside
# them, and refuses an item that is not one of this run's exclusions or one the run has already recorded: a
# mechanism belongs to one record, and a second one would double-count the item as two kinds of stop.
case_state_anAdoptionRefusalIsRecordedBesideTheExclusion() {
  # files: tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p f out status before branch
  p=$(new_project)
  init_state "$p" 1 90
  branch="fix/issue-21-an-attempt"
  printf '[{"issue":21,"class":"bug","verdict":"needs-decision","reason":"branch %s carries a commit the base does not — a previous attempt work is on it","evidence":"%s"}]' \
    "$branch" "$branch" | run_state "$p" queue >/dev/null || fail "the pre-screen's exclusion was refused"
  run_state "$p" adoption-refused 21 --reason "the project tree is not clean — refusing to merge $branch onto the base" \
    >/dev/null || fail "the adoption's refusal was refused"
  f=$(state_file "$p")
  assert_eq "unmergeable" "$(jq -r '.excluded[] | select(.issue == 21) | .mechanism' "$f")" \
    "the refusal is recorded as the item's mechanism"
  assert_contains "$(jq -r '.excluded[] | select(.issue == 21) | .adoptionRefusal' "$f")" "not clean" \
    "with the refusal's own text beside it"
  assert_eq "needs-decision" "$(jq -r '.excluded[] | select(.issue == 21) | .verdict' "$f")" \
    "while the verdict stays the one the pre-screen read"
  assert_eq "$branch" "$(jq -r '.excluded[] | select(.issue == 21) | .evidence' "$f")" \
    "and the branch stays its evidence"
  assert_contains "$(jq -r '.excluded[] | select(.issue == 21) | .reason' "$f")" "carries a commit" \
    "and its reason is the pre-screen's, not replaced by the adoption's"
  # The exclusions the run did not decide for itself are still the owner's: the report prints this one with the
  # mechanism and names no decision, and the entry it did not touch is the opposite of both.
  assert_contains "$(run_state "$p" report)" "excluded: #21 needs-decision (unmergeable)" \
    "the table prints the mechanism beside the exclusion"
  # A second pre-screen is the same branch read again: the run's own record survives the rewrite of `.excluded`,
  # so a re-screen mid-run cannot turn into an escalation what the run already recorded as work.
  printf '[{"issue":21,"class":"bug","verdict":"needs-decision","reason":"branch %s carries a commit the base does not","evidence":"%s"}]' \
    "$branch" "$branch" | run_state "$p" queue >/dev/null || fail "the second pre-screen's exclusion was refused"
  assert_eq "unmergeable" "$(jq -r '.excluded[] | select(.issue == 21) | .mechanism' "$f")" \
    "a second pre-screen keeps the mechanism the run recorded"
  before=$(cat "$f")
  out=$(run_state "$p" adoption-refused 99 --reason "a tree that is not clean" 2>&1); status=$?
  assert_eq "5" "$status" "an item that is not one of this run's exclusions is refused"
  assert_contains "$out" "not one exclusion of this run" "and the refusal says why"
  assert_eq "$before" "$(cat "$f")" "the state is not written by it"
  # …and an item the run has worked keeps its one record: a second one would count it as two kinds of stop.
  run_state "$p" done 21 --result blocked --verdict blocked >/dev/null || fail "the item's own record was refused"
  before=$(cat "$f")
  out=$(run_state "$p" adoption-refused 21 --reason "a tree that is not clean" 2>&1); status=$?
  assert_eq "5" "$status" "an item the run has already recorded is refused"
  assert_contains "$out" "already has the item record" "and the refusal names the record it would double"
  assert_eq "$before" "$(cat "$f")" "the state is not written by that one either"
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

# A landing's own commit is what attaches it to the pipeline run it caused, and the record has no field for one
# until `done` is handed one (issue #709). Two facts: the commit is stored beside the branch, the count and the
# evidence, and a value that is not a commit is refused rather than stored — a wrong commit would score a run that
# belongs to nobody.
case_state_doneRecordsTheCommitItLanded() {
  # files: tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p sf sha out status
  p=$(new_project)
  sha="a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"
  init_state "$p" 1 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  sf=$(state_file "$p")
  run_state "$p" done 21 --result done --verdict closed --change fix-demo \
    --merged-branch fix/issue-21-demo --merged-commits 0bc31dc --merged-evidence "$p/item, $p/change" \
    --merged-sha "$sha" >/dev/null || fail "the landing's commit was refused"
  assert_eq "$sha" "$(jq -r '.items[0].merged.sha' "$sf")" "the record carries the commit the landing made"
  assert_eq "fix/issue-21-demo" "$(jq -r '.items[0].merged.branch' "$sf")" \
    "and the branch it already carried is still there beside it"
  assert_eq "null" "$(jq -r '.items[0].landedReddenedMain' "$sf")" \
    "a landing is unscored until a run reads the pipeline its commit caused"
  # A value that is not a commit is refused before the record is written, and the item stays in the queue.
  init_state "$p" 1 90
  printf '[{"issue":22,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  out=$(run_state "$p" done 22 --result done --verdict closed --merged-branch fix/issue-22-demo \
    --merged-sha 'not-a-commit' 2>&1); status=$?
  assert_eq "2" "$status" "a value that is not a commit is a usage error"
  assert_contains "$out" "--merged-sha" "and the refusal names the flag it refused"
  assert_eq "0" "$(jq -r '.items | length' "$sf")" "the refused landing wrote no record"
  assert_eq "1" "$(jq -r '.queue | length' "$sf")" "and the item is still queued"
}

# The score is read once per landing and stored, so the report prints a number without a network read of its own
# (issue #709). The fixture carries both facts a naive read gets wrong: the commit's push run failed while a
# scheduled run of the *same* commit succeeded and is newer, so a scorer that takes the newest run calls this
# landing green.
case_state_theLandingScoreIsReadOnceAndStored() {
  # files: tools/loop/state.sh tools/loop/fixtures/scenarios/ tools/issues/fixtures/ tools/pi-child-events.sh tools/tooling.json
  local p d sf red green out
  p=$(new_project); d=$(scratch)
  red="a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"
  green="b1b2c3d4e5f60718293a4b5c6d7e8f9012345678"
  init_state "$p" 2 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2}]' |
    run_state "$p" queue >/dev/null
  sf=$(state_file "$p")
  run_state "$p" done 21 --result done --verdict closed --change fix-a \
    --merged-branch fix/issue-21-a --merged-commits 1111111 --merged-sha "$red" >/dev/null ||
    fail "the first landing was refused"
  run_state "$p" done 22 --result done --verdict closed --change fix-b \
    --merged-branch fix/issue-22-b --merged-commits 2222222 --merged-sha "$green" >/dev/null ||
    fail "the second landing was refused"
  # Every `gh` invocation from here lands in a log and is then answered by the project's stub, so a second read is
  # counted rather than argued about.
  mkdir -p "$d/bin"
  printf '#!/usr/bin/env bash\nprintf "%%s\\n" "$*" >> "%s"\nexec %s "$@"\n' \
    "$d/gh-calls.log" "$LOOP_ISSUE_FIXTURES/gh" > "$d/bin/gh"
  chmod +x "$d/bin/gh"
  out=$( cd "$p" && GH_STUB_DIR="$(scenario landed)" PATH="$d/bin:$PATH" \
    bash "$LOOP_ENGINE/state.sh" score-landings 2>&1 ) || fail "the score failed: $out"
  assert_eq "true" "$(jq -r '.items[] | select(.issue == 21) | .landedReddenedMain' "$sf")" \
    "the push run of that commit failed, so the landing reddened main — the newer scheduled run of the same commit succeeded"
  assert_eq "false" "$(jq -r '.items[] | select(.issue == 22) | .landedReddenedMain' "$sf")" \
    "the push run of the second landing succeeded"
  assert_contains "$out" "#21 $red: reddened main" "the read says which landing it scored and how"
  assert_eq "2" "$(grep -c 'run list' "$d/gh-calls.log")" "one read per landing, and none for a landing it did not score"
  : > "$d/gh-calls.log"
  ( cd "$p" && GH_STUB_DIR="$(scenario landed)" PATH="$d/bin:$PATH" \
    bash "$LOOP_ENGINE/state.sh" score-landings >/dev/null 2>&1 ) || fail "the second score pass failed"
  assert_eq "0" "$(grep -c 'run list' "$d/gh-calls.log" || true)" \
    "a scored landing is not read again: the network read happens once per landing, not once per pass"
}

# "No run" is not "the build was fine". A landing whose commit the tracker answers no finished push run for stays
# unscored and names its reason, so the report counts it apart instead of as a landing that did not redden `main`
# (issue #709, and the rule `report#noRoundOrWallPrintsDashNotZero` keeps for every other figure).
case_state_aLandingWithNoRunIsUnscored() {
  # files: tools/loop/state.sh tools/loop/fixtures/scenarios/ tools/issues/fixtures/ tools/pi-child-events.sh tools/tooling.json
  local p sf absent running out
  p=$(new_project)
  absent="c1c2c3d4e5f60718293a4b5c6d7e8f9012345678"
  running="d1d2c3d4e5f60718293a4b5c6d7e8f9012345678"
  init_state "$p" 2 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2}]' |
    run_state "$p" queue >/dev/null
  sf=$(state_file "$p")
  run_state "$p" done 21 --result done --verdict closed --change fix-a \
    --merged-branch fix/issue-21-a --merged-commits 1111111 --merged-sha "$absent" >/dev/null ||
    fail "the first landing was refused"
  run_state "$p" done 22 --result done --verdict closed --change fix-b \
    --merged-branch fix/issue-22-b --merged-commits 2222222 --merged-sha "$running" >/dev/null ||
    fail "the second landing was refused"
  out=$( cd "$p" && GH_STUB_DIR="$(scenario landed)" bash "$LOOP_ENGINE/state.sh" score-landings 2>&1 ) ||
    fail "the score refused a landing it could not read: $out"
  assert_eq "null" "$(jq -r '.items[] | select(.issue == 21) | .landedReddenedMain' "$sf")" \
    "no push run names that commit yet, so nothing is written"
  assert_eq "null" "$(jq -r '.items[] | select(.issue == 22) | .landedReddenedMain' "$sf")" \
    "the push run of that commit has not finished, so nothing is written either"
  assert_contains "$out" "#21 $absent: unscored — no push run names this commit yet" "the first reason is named"
  assert_contains "$out" "#22 $running: unscored — the push run of this commit has not finished" "and the second"
  # A tracker that does not answer at all is the same outcome, not a failure of the item: this scenario's fixture
  # carries no `runs.json`, so the stub refuses the read the way a socket does.
  out=$( cd "$p" && GH_STUB_DIR="$(scenario none)" bash "$LOOP_ENGINE/state.sh" score-landings 2>&1 ) ||
    fail "a read it could not take failed the caller"
  assert_contains "$out" "#21 $absent: unscored — the pipeline runs of this commit could not be read" \
    "and an unreadable tracker is unscored with its reason, not green"
  assert_eq "null" "$(jq -r '.items[] | select(.issue == 21) | .landedReddenedMain' "$sf")" "nothing was written by the failed read"
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

# A missing citation is reported, not refused (#281): a path the tree does not hold has several legitimate
# causes — a path the change itself creates, a deleted path the body says was deleted, a run's own artefact —
# and the refusal that read every one of them as a lost citation cost 35 items their place in the queue, 18 of
# them for a citation that was no longer stale at all. The token is named in the item's record and the item is
# work.
case_prescreen_missingPathIsReportedNotRefused() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out status
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario stale)
  export GH_STUB_RECORD="$p/gh.log"; : > "$GH_STUB_RECORD"
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err"); status=$?
  assert_eq "0" "$status" "the item is work, not a stop"
  assert_eq "eligible" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 31) | .verdict')" \
    "a missing citation no longer refuses the item"
  assert_eq "app/src/main/kotlin/DoesNotExist.kt" \
    "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 31) | .absentCitation')" \
    "the record names the token the tree does not hold"
  assert_eq "0" "$(grep -c 'excluded-stale' "$GH_STUB_RECORD" || true)" \
    "no stale refusal is written to the tracker (stderr: $(tr '\n' ' ' < "$p/prescreen.err"))"
  assert_eq "1" "$(jq -r '.queue | length' "$(state_file "$p")")" "the item reaches the queue"
}

# The refusal is not a line in a machine-local file (issue #163): the verdict an item cannot escape by
# itself is written to the tracker by the run's own script, the authorisation label is left as the owner
# put it, and a dry run writes nothing. A citation is no longer one of those verdicts (#281), so the case
# rides the refused class, which is.
case_prescreen_refusalLeavesATraceInTheTracker() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out log
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario six)
  jq '.roles.refusedClasses = ["feature"]' "$p/tools/tooling.json" > "$p/tools/tooling.json.new" &&
    mv "$p/tools/tooling.json.new" "$p/tools/tooling.json" || fail "could not refuse the feature class"
  export GH_STUB_RECORD="$p/gh.log"; : > "$GH_STUB_RECORD"
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err") || true
  assert_eq "class-feature" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 25) | .verdict')" \
    "the fixture is still the refused one (stderr: $(tr '\n' ' ' < "$p/prescreen.err"))"
  log=$(cat "$GH_STUB_RECORD")
  assert_contains "$log" "agent/excluded-feature" "the refusal is written to the tracker (stderr: $(tr '\n' ' ' < "$p/prescreen.err"))"
  assert_contains "$log" "issue edit 25" "the write names the item the verdict rejected"
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
# that question, and the owner's answer on #60 was a pre-screen rule as the durable guard. The signal is the
# author's **stated claim** — a line of its own beginning `The fix lands in the submodule` — and never the
# submodule's path or name, which a body quotes as evidence (issue #314).
case_prescreen_aFixInTheSubmoduleIsAPersonsPatch() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out status reason
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario submodule)
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err"); status=$?
  assert_eq "0" "$status" "the app-side item beside them is still work (stderr: $(tr '\n' ' ' < "$p/prescreen.err"))"
  assert_eq "needs-decision" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 71) | .verdict')" \
    "an item that states the fix lands in the submodule is its owner's, not the run's"
  reason=$(printf '%s' "$out" | jq -r '.[] | select(.issue == 71) | .reason')
  assert_contains "$reason" "app/src/main/cpp/libosmscout" "the reason names the path the fix has to be committed in"
  assert_contains "$reason" "a person lands the patch" "and the route that remains: a person lands the patch"
  assert_eq "needs-decision" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 72) | .verdict')" \
    "the same claim stated without the path is the same question"
  assert_eq "eligible" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 73) | .verdict')" \
    "an item that states nothing of the kind is queued"
  assert_eq "3" "$(jq -r '.queue | length' "$(state_file "$p")")" \
    "the items the tree can deliver reach the queue, and the two stated submodule claims do not"
}

# The other half of the same rule, and the defect #314 measured: the signal is the author's claim and never the
# submodule's path. Three items of the loop's own tooling — #309, #310 and #305 — were parked as submodule patches
# because their bodies quote `app/src/main/cpp/libosmscout` as the file a dirty tree was seen on. Evidence is not
# the claim, and neither is the marker quoted back at the reader: the phrase has to stand on a line of its own.
case_prescreen_aQuotedSubmodulePathIsEvidenceNotAClaim() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario submodule)
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err") || true
  assert_eq "eligible" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 74) | .verdict')" \
    "an item whose body quotes the submodule path as the file the dirt was seen on stays in the queue"
  assert_eq "eligible" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 75) | .verdict')" \
    "an item that quotes the marker inline and backticked is quoting it, not stating it"
  assert_eq "3" "$(jq -r '.queue | length' "$(state_file "$p")")" \
    "the three items whose fix is this tree's reach the queue, and nothing else"
}

# One candidate the tracker refuses to answer for is an unknown, not a verdict and not the end of the screen
# (issue #231's rule, applied per read). Until 2026-10-27 the first failed `gh issue view` ended the pre-screen —
# `loop_die`, exit 2 — and the run with it, so a 321-candidate screen was worth one flaky answer; the reason was not
# even recorded, because `loop_issue_json` sent the seam's retry and `tracker-unreachable` lines to /dev/null.
case_prescreen_oneUnreadableCandidateDoesNotStopTheScreen() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json tools/issues/fixtures/gh
  local p out status reason
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario two)
  export GH_STUB_READ_FAIL="gh issue view 21 "
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err"); status=$?
  unset GH_STUB_READ_FAIL
  assert_eq "0" "$status" "a candidate that cannot be read does not end the screen"
  assert_eq "22" "$(printf '%s' "$out" | jq -r '[.[] | select(.verdict == "eligible") | .issue] | join(" ")')" \
    "the candidates that did answer are still queued"
  assert_eq "unreadable" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 21) | .verdict')" \
    "the item the tracker did not answer for is recorded as unreadable"
  reason=$(printf '%s' "$out" | jq -r '.[] | select(.issue == 21) | .reason')
  assert_contains "$reason" "could not be read" "its reason says so"
  assert_contains "$reason" "Could not resolve" "and carries the CLI's own words, which the seam no longer discards"
  assert_contains "$(cat "$p/prescreen.err")" "Could not resolve" "the console says what the CLI said"
  assert_eq "1" "$(jq -r '.queue | length' "$(state_file "$p")")" "only the readable item reaches the queue"
}

# The other side of the same rule: when *nothing* answered, an empty queue is not "nothing eligible". The screen
# stops with a reason instead of reporting a tracker that refused every item (issue #231: an outage is not a verdict).
case_prescreen_anUnansweredTrackerIsNotAnEmptyQueue() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json tools/issues/fixtures/gh
  local p status err
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario two)
  export GH_STUB_READ_FAIL="gh issue view"
  run_prescreen "$p" --json >/dev/null 2>"$p/prescreen.err"; status=$?
  unset GH_STUB_READ_FAIL
  err=$(cat "$p/prescreen.err")
  assert_eq "2" "$status" "an unanswered tracker stops the screen rather than reporting an empty one"
  assert_contains "$err" "could not be read" "the stop names the unreadable candidates"
  assert_not_contains "$err" "nothing eligible" "and is not the empty-queue answer"
  assert_eq "0" "$(jq -r '.queue | length' "$(state_file "$p")")" "nothing is queued on a guess"
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
  # A closed issue is not even a candidate since 2026-10-27: `list.sh --eligible-only` filters `CLOSED` out of the
  # query (before that it selected them, and the closed #310 was the candidate whose unreadable record ended a run).
  # The pre-screen's own CLOSED branch remains for the item closed between the list and its read.
  assert_eq "" "$(printf '%s' "$out" | jq -r '.[] | select(.issue == 92) | .verdict')" \
    "a closed issue is no longer a candidate at all"
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
  [ -f "$w/.pi/skills/openspec-apply-change/SKILL.md" ] || fail "the gitignored skill the child is given was not copied into the worktree"
  [ -e "$w/.pi/skills/fix-loop" ] && fail "the engine still seeds a retired, versioned skill into the worktree"
  assert_contains "$(cat "$w/.pi/loop-env.sh")" "VCPKG_ROOT" "the dependency root is exported from a file, not a symlink"
}

# The evidence check is the loop's tool, and the two paths it reads in a project are that project's configuration
# (issue #255): the engine names no project root of its own, so the same tool runs unchanged in another tree and
# reports a marker planted under whatever path the configuration names — and refuses to pass when it names none.
case_evidence_scansWhatTheProfileNames() {
  # files: tools/loop/ tools/loop/evidence-check.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local p out code
  p=$(new_project)
  mkdir -p "$p/src/module" "$p/build/test-results" "$p/openspec/changes/demo" "$p/tools/loop"
  printf '| § | issue |\n|---|---|\n| 12 | #7 |\n' > "$p/table.md"
  printf -- '- Related: §12 is settled\n' > "$p/openspec/changes/demo/proposal.md"
  printf 'fun main() = Unit\n' > "$p/src/module/Foo.kt"
  printf 'LOOP_EVIDENCE_SCAN_PATHS="src/module"\nLOOP_EVIDENCE_XML_DIR="build/test-results"\n' > "$p/tools/loop/loop.conf"

  out=$( cd "$p" && bash "$LOOP_ENGINE/evidence-check.sh" openspec/changes/demo --root . --table table.md 2>&1 ); code=$?
  assert_eq 0 "$code" "a clean tree whose configuration names its own scan root passes: $out"

  printf '// REVERT-CHECK MUTATION (task 1)\n' >> "$p/src/module/Foo.kt"
  out=$( cd "$p" && bash "$LOOP_ENGINE/evidence-check.sh" openspec/changes/demo --root . --table table.md 2>&1 ); code=$?
  assert_eq 1 "$code" "a marker under the configured path is reported: $out"
  assert_contains "$out" "src/module/Foo.kt" "the report names the file the marker sits in"

  printf 'LOOP_EVIDENCE_SCAN_PATHS=""\nLOOP_EVIDENCE_XML_DIR="build/test-results"\n' > "$p/tools/loop/loop.conf"
  out=$( cd "$p" && bash "$LOOP_ENGINE/evidence-check.sh" openspec/changes/demo --root . --table table.md 2>&1 ); code=$?
  assert_eq 2 "$code" "a configuration that names no scan root is refused instead of scanning nothing: $out"
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

# A kept worktree is reused by switching it to the new base, and the switch moves the superproject's gitlink
# while the module's own worktree stays on the pin the previous branch recorded. The tree is dirty the moment it is
# switched: `start-item` refuses it, `run.sh` records the stop, and the run ends with no item the dirt can be
# attributed to (issue #309). The fresh path places the module it inherits nothing of
# (`worktree#checksOutTheSubmoduleTheLoopConfigNames`); the reused path owes the same placement for the module it
# does inherit.
case_worktree_checksOutTheNewPinWhenItReusesTheWorktree() {
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
  local d p wt old new out
  d=$(scratch); p=$(new_project); wt="$p/.pi/worktrees/loop"
  submodule_fixture "$p" "$d"
  # The previous branch's pin, which is the base the kept worktree is left on.
  git -C "$p/app/src/main/cpp/libosmscout" checkout -q "$SUB_FIRST"
  git -C "$p" commit -qam 'the pin the previous branch recorded' ||
    fail "could not commit the previous pin"
  old=$(git -C "$p" rev-parse HEAD)
  run_worktree "$p" create --base "$old" --branch loop-prev >/dev/null 2>&1 ||
    fail "the first worktree create failed"
  # The new base records the other commit, which is what the base moving a gitlink does.
  git -C "$p/app/src/main/cpp/libosmscout" checkout -q "$SUB_SECOND"
  git -C "$p" commit -qam 'the pin this base records' || fail "could not commit the new pin"
  new=$(git -C "$p" rev-parse HEAD)
  # The kept worktree is clean on the previous pin before it is reused — the state the run's pre-flight accepts.
  submodule_in_worktree "$wt" "$SUB_FIRST"
  assert_eq "" "$(git -C "$wt" status --porcelain)" "the fixture leaves the kept worktree clean on the previous pin"
  assert_eq "$SUB_SECOND" "$(git -C "$p" rev-parse "$new:app/src/main/cpp/libosmscout")" \
    "the fixture's two bases pin different commits"
  init_state "$p" 1 90
  out=$(run_worktree "$p" create --base "$new" --branch loop-next 2>&1) ||
    fail "the reused worktree was not created: $out"
  assert_eq "" "$(git -C "$wt" status --porcelain)" "the reused worktree is the tree the new base records"
  assert_eq "$SUB_SECOND" "$(git -C "$wt/app/src/main/cpp/libosmscout" rev-parse HEAD)" \
    "on the pin the new base records"
  # The refusal the run died on is `start-item`'s, over the tree `create` left behind (issue #309).
  out=$(run_worktree "$p" start-item 21 demo 2>&1) || fail "no item can start on the reused worktree: $out"
}

# The pin a base records may be a commit only the root tree's module holds — a bump landed before its submodule
# branch was pushed. The module inside a worktree is a clone of its own, with the project's URL, so the update ends
# in `upload-pack: not our ref`, the module stays on its old pin and the superproject reports that as dirt under no
# item: the whole run ends on it (measured 2026-10-28 — a run with 600 minutes of budget stopped after 69 seconds
# on `M app/src/main/cpp/libosmscout`). The placement fetches the pin from the root tree's module, which is a clone
# the worktree can read (measured 2026-10-28).
case_worktree_placesAPinOnlyTheRootCheckoutHolds() {
  # files: tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
  local d p wt pin out
  d=$(scratch); p=$(new_project); wt="$p/.pi/worktrees/loop"
  submodule_fixture "$p" "$d"
  export GIT_ALLOW_PROTOCOL=file
  # A commit made in the root tree's module and never pushed: the module's origin is the fixture's own repository,
  # which does not carry it — the state a bump leaves behind when the parent commit lands first.
  printf 'a pin no remote carries\n' >> "$p/app/src/main/cpp/libosmscout/CMakeLists.txt"
  git -C "$p/app/src/main/cpp/libosmscout" -c user.email=selftest@example.invalid -c user.name=selftest \
    commit -qam 'the unpublished pin' >/dev/null 2>&1 || fail "could not commit the unpublished pin"
  pin=$(git -C "$p/app/src/main/cpp/libosmscout" rev-parse HEAD)
  git -C "$p" commit -qam 'the pin only this clone holds' >/dev/null 2>&1 || fail "could not record the pin"
  git -C "$d/sub" cat-file -e "$pin" 2>/dev/null && fail "the fixture's pin is on the module's origin after all"
  init_state "$p" 1 90
  out=$(run_worktree "$p" create --branch loop-test 2>&1) || fail "worktree create failed: $out"
  assert_contains "$out" "placed from the root checkout" "the placement says where the pin came from"
  assert_eq "" "$(git -C "$wt" status --porcelain)" "the worktree is clean after the placement"
  assert_eq "$pin" "$(git -C "$wt/app/src/main/cpp/libosmscout" rev-parse HEAD)" "on the pin the base records"
  return 0
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

# A branch the run set aside is a previous attempt's only copy, and what protects it is the reading every other
# branch gets — patch identity, never ancestry. Nothing enumerated the set-aside namespace, so a set-aside branch
# whose work had landed by hand stayed for ever: measured 2026-11-06, eight such branches of one to four commits
# each, and neither removal path named one of them (issue #803).
case_worktree_aSetAsideBranchWhosePatchesLandedIsRemoved() {
  # files: tools/loop/lib.sh tools/loop/worktree.sh tools/loop/loop-clean.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p set_aside out
  p=$(new_project)
  landed_branch "$p" 21 demo >/dev/null || fail "could not land the attempt's patch"
  # the run's recorded base is the base the sweep reads: it is taken after the landing, so the patch the base
  # carries is the one this case is about (the sweep compares against the base the run recorded, not an older pin)
  init_state "$p" 1 90
  set_aside="loop/leftover/issue-21-demo-20260101T000000Z"
  git -C "$p" branch "$set_aside" fix/issue-21-demo >/dev/null 2>&1 || fail "could not set the attempt aside"

  out=$(run_worktree "$p" prune-landed 2>&1) || fail "the read-only sweep failed: $out"
  assert_contains "$out" "would remove $set_aside" "a set-aside branch whose patches are in the base is swept"
  out=$(run_clean "$p" 2>&1) || fail "the cleanup report failed: $out"
  assert_contains "$out" "would remove $set_aside" "and the cleanup command names the same branch as removable"
  git -C "$p" rev-parse --verify --quiet "refs/heads/$set_aside" >/dev/null ||
    fail "a read-only run removed the set-aside branch"

  out=$(run_worktree "$p" prune-landed --apply 2>&1) || fail "the sweep failed: $out"
  assert_contains "$out" "removed $set_aside" "the sweep removes it with --apply"
  git -C "$p" rev-parse --verify --quiet "refs/heads/$set_aside" >/dev/null 2>&1 &&
    fail "the set-aside branch whose patches the base holds survived the sweep"
}

# The other half of the same reading: a set-aside branch whose patches the base does not have is the attempt's
# only copy, so both removal paths keep it — and name it, which is what nothing did before. The attempt's own
# branch is deleted here, so the set-aside ref is the only thing that reaches its commit.
case_worktree_aSetAsideBranchThatCarriesWorkIsNamedNotRemoved() {
  # files: tools/loop/lib.sh tools/loop/worktree.sh tools/loop/loop-clean.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p set_aside out
  p=$(new_project)
  init_state "$p" 1 90
  git -C "$p" switch -c fix/issue-22-work >/dev/null 2>&1 || fail "could not create the attempt's branch"
  printf 'the blocked attempt\n' > "$p/attempt.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" commit -qm "fix: the blocked attempt (#22)" >/dev/null 2>&1 || fail "could not commit the attempt"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not return to the base"
  set_aside="loop/leftover/issue-22-work-20260101T000000Z"
  git -C "$p" branch "$set_aside" fix/issue-22-work >/dev/null 2>&1 || fail "could not set the attempt aside"
  git -C "$p" branch -D fix/issue-22-work >/dev/null 2>&1 || fail "could not drop the attempt's own branch"

  out=$(run_worktree "$p" prune-landed --apply 2>&1) || fail "the sweep failed: $out"
  assert_contains "$out" "kept $set_aside — carries work the base does not have" \
    "the sweep keeps a set-aside branch that carries work, and names why"
  out=$(run_clean "$p" --apply 2>&1) || fail "the cleanup failed: $out"
  assert_contains "$out" "kept $set_aside" "the cleanup command names it too"
  git -C "$p" rev-parse --verify --quiet "refs/heads/$set_aside" >/dev/null ||
    fail "a set-aside branch carrying unreviewed work was removed"
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
  # The four the profile's `roles.shapingVerdicts` names also take the shaping label (capability `shaping-inbox`),
  # in the same write: `needs-diagnosis` keeps the decision label it wrote before that capability.
  check_labels unspecifiable "agent/needs-shaping status/on-hold" "status/open"
  check_labels too-wide "agent/needs-shaping status/on-hold" "status/open"
  check_labels not-spec-shaped "agent/needs-shaping status/on-hold" "status/open"
  check_labels needs-target "status/on-hold" "status/open"
  check_labels needs-device "agent/needs-device status/on-hold" "status/open"
  check_labels needs-decision "agent/needs-decision status/on-hold" "status/open"
  check_labels needs-diagnosis "agent/needs-decision agent/needs-shaping status/on-hold" "status/open"

  # `stale` left the vocabulary with issue #682: the run escalates that skip instead of parking it, so the
  # label writer must refuse it rather than write a hold nobody can answer — an unrefused verdict is a row
  # that parks an item for good.
  log="$d/gh-stale.log"; : > "$log"; export GH_STUB_RECORD="$log"
  out=$(run_labels "$p" stale 21 --reason "the fixture cause" --evidence "the fixture evidence" 2>&1); status=$?
  assert_eq "2" "$status" "a stale verdict is refused as unknown: $out"
  assert_eq "0" "$(grep -c 'issue' "$log" || true)" "and the refusal writes nothing"

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
  # `stale` is not a hold any more (issue #682: the run escalates it instead), so the case names one that is.
  out=$(run_labels "$p" unspecifiable 21 2>&1); status=$?
  assert_eq "2" "$status" "a hold without a reason is refused as usage: $out"
  [ ! -s "$log" ] || fail "a refused hold wrote something: $(cat "$log")"

  run_labels "$p" unspecifiable 21 --reason "the cited path no longer exists" --evidence "base abc123, the tree" >/dev/null 2>&1 ||
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
  # The fifo is held **read-write** before anything can end the child (issue #259). The stub opens it for reading
  # only after it has written `$block.ready`, and the run's own trap ends the child within a twentieth of a second
  # of the signal — the poll that waits for the child is a fraction of a second now, so the child is gone long
  # before this case's own `sleep 0.2`. A release that opened the fifo for writing afterwards waited for a reader
  # that no longer existed, and the case hung the suite, and the gate behind it, for ever. `<>` opens a fifo
  # without a partner and never blocks, and the one line written through it is what a child still waiting reads.
  exec 8<> "$block"
  waited=0
  wait_for_stub_child "$block.ready" || true
  [ -f "$block.ready" ] || { printf '\n' >&8; wait "$first"; fail "the run never reached its child"; }

  kill -TERM "$first"
  sleep 0.2
  printf '\n' >&8
  wait "$first"
  unset PI_STUB_WAIT
  exec 8>&-

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
  # files: tools/loop/fixtures/scenarios/ tools/loop/prompts/worker-tail.md tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p prompt skills skill tail_prompt
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompt="$p/.pi/issue-loop/items/21/worker-prompt.md"
  [ -f "$prompt" ] || fail "the worker prompt was not rendered"
  for phase in 'Phase A' 'Phase B' 'Phase C' 'Phase D'; do
    assert_contains "$(cat "$prompt")" "$phase" "the worker prompt names $phase"
  done
  # …and phase F is the tail child's, so its checklist is that child's prompt (issue #744): the run starts that child
  # from the gate block, and the prompt it is handed is the one that has to name the phase.
  tail_prompt="$p/.pi/issue-loop/items/21/worker-tail-prompt.md"
  [ -f "$tail_prompt" ] || fail "the tail child's prompt was not rendered"
  assert_contains "$(cat "$tail_prompt")" 'Phase F' "the tail prompt names Phase F"
  assert_not_contains "$(cat "$prompt")" 'Phase F' "and the gate child's prompt does not, because its session ends there"
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
  # A stale skip is a question too (issue #682): the verdict leaves retire-or-re-diagnose, and a bare hold
  # parks the item where nobody can answer it.
  assert_contains "$(cat "$prompt")" '`needs-diagnosis` or `stale`' "and names the stale verdict among those that ask"
  # The two fields a falsification carries (openspec change `falsified-items-close-in-the-loop`): without them
  # in the rendered prompt the worker cannot answer the shape the run reads.
  assert_contains "$(cat "$prompt")" 'LOOP-FALSIFIED:' "the worker prompt asks what the item blames"
  assert_contains "$(cat "$prompt")" 'LOOP-MEASURED:' "and what was measured instead, with how it was taken"
}

# The change child grades the diff it hands over against the review contract's own three predicates (issue #812).
# Measured over the loop's item archive: 103 of 165 round-1 reports refuse, and their blocking findings are
# dominantly a scenario clause the case that names it does not exercise, or a `design.md`/`tasks.md` claim the
# diff contradicts — both readable by the writer, who holds the diff before anybody else does.
case_prompt_workerGradesItsOwnDiffBeforeItReports() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p prompt text
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompt="$p/.pi/issue-loop/items/21/worker-prompt.md"
  [ -f "$prompt" ] || fail "the worker prompt was not rendered"
  text=$(cat "$prompt")
  assert_contains "$text" 'Grade your own diff before you hand it over' "the worker grades its own diff before it reports"
  assert_contains "$text" 'clauses against the case that names it' "and reads each clause against the case that names it"
  assert_contains "$text" 'prose against the diff' "and each claim design.md, tasks.md or proposal.md makes against a hunk"
  assert_contains "$text" 'artifact your change cites' "and every artifact the change cites, where it says it is"
  assert_contains "$text" 'owning guideline section phase C named' "and the owning guideline section phase C named"
  assert_contains "$text" 'A review finding is blocking or it is not' "all four are the review contract's own words, not a second rule"
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
  # The number rule (issue #244): a quoted number whose command is not in the record is a correction, not a
  # refusal — the round report says which findings are `NOTE:` for that reason, so a wrong count does not buy a
  # second round. This is the same split #205 made for wording, applied to numbers.
  assert_contains "$text" 'no command in the record produced is not a claim' \
    "the reviewer is told a number with no command is not a claim"
  assert_contains "$text" 'depends on the number' \
    "and that only a number the change depends on is blocking"
  # the bound is the number the run itself enforces, so the two cannot drift
  cap=8100
  assert_contains "$text" "$cap" "the wall-clock bound named in the task is the run's own ($cap s)"
}

# The same bound reaches a session that spawns its own reviewer (issue #236): the loop's prompt is the one
# skeleton, and the iteration protocol is where a session is told to hand it over. Two surfaces, one rule.
case_prompt_iterationProtocolHandsTheReviewerTheSameSkeleton() {
  # files: guidelines/BuildGate.md tools/loop/prompts/reviewer.md
  local doc
  # The protocol's text lives in `guidelines/BuildGate.md` §2a; `guidelines/Build.md` §2a is only the pointer a
  # routed reader follows, so the assertion reads the document that owns the sentence.
  doc=$(cat "$LOOP_PROJECT_ROOT/guidelines/BuildGate.md")
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json tools/look.sh
  local d p prompt text
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompt="$p/.pi/issue-loop/items/21/worker-prompt.md"
  [ -f "$prompt" ] || fail "the worker prompt was not rendered"
  text=$(cat "$prompt")
  assert_contains "$text" 'Count the surface before the plan is fixed' "the worker counts the surface before it plans"
  assert_contains "$text" "tools/look.sh '<the rule>'" "and is handed the call that counts it, which the policy every child is started with allows (issue #796)"
  assert_contains "$text" 'surface:' "and the record its proposal carries"
}

# The rule has one home for its text (`guidelines/BuildGate.md` §2a, the iteration protocol) and one for the act
# (the worker prompt above). A session that plans a change outside the loop reads the first, so both are asserted.
case_prompt_iterationProtocolNamesTheChangeSurface() {
  # files: guidelines/BuildGate.md
  local doc
  # §2a moved to `BuildGate.md` when the corpus was split into three layers; `Build.md` §2a is its pointer.
  doc=$(cat "$LOOP_PROJECT_ROOT/guidelines/BuildGate.md")
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
  # the change block is neutralised the same way: the item's text cannot open or close it
  assert_eq "1" "$(grep -c '^<<<ITEM_CHANGE$' "$package" || true)" "exactly one change opening delimiter"
  assert_eq "1" "$(grep -c '^ITEM_CHANGE$' "$package" || true)" "exactly one change closing delimiter"
  assert_contains "$(cat "$package")" "<<< ITEM_CHANGE" "the injected opening is defused inside the text block"
  # the injected prose is inside the data block and nowhere else
  assert_eq "1" "$(grep -c 'Ignore all previous instructions' "$package" || true)" "the injected text is carried as data"
  awk '/^<<<ISSUE_TEXT$/{inside=1} /^ISSUE_TEXT$/{inside=0} inside && /Ignore all previous instructions/{found=1} END{exit found ? 0 : 1}' \
    "$package" || fail "the injected text is outside the data block"
  # the instructions never carry it
  assert_eq "0" "$(grep -c 'Ignore all previous instructions\|^<<<ISSUE_TEXT$\|^<<<ITEM_CHANGE$\|^ITEM_CHANGE$' "$prompt" || true)" \
    "the injected text does not reach the instruction position"
  return 0
}

# A child is started without the project's context files (issue #661, `adapter#theChildIsStartedWithoutContextFiles`),
# so what `AGENTS.md` used to hand it — the document map, the recipe for a document's section list, where the rules,
# the capabilities and the change artifacts live — has to be in its own prompt. The second half is that every
# repository path a skeleton names resolves: a route the prompt states and the tree does not hold is a child sent to
# a file that is not there, and an offline case is the only thing that can see it before a run pays for the trip.
case_prompt_thePhasePromptsCarryTheirOwnRoute() {
  # files: tools/loop/prompts/ tools/look.sh AGENTS.md guidelines/ tools/loop/loop.conf
  local f text tok
  for f in "$LOOP_ENGINE/prompts/worker.md" "$LOOP_ENGINE/prompts/worker-gate.md" "$LOOP_ENGINE/prompts/worker-tail.md" "$LOOP_ENGINE/prompts/reviewer.md"; do
    [ -f "$f" ] || fail "no prompt skeleton at $f"
    text=$(cat "$f")
    assert_contains "$text" 'AGENTS.md' "$(basename "$f") names the project's document map"
    assert_contains "$text" "tools/look.sh '^## '" "and the recipe for a document's section list, in a call the child's own policy allows (issue #796)"
    assert_not_contains "$text" "grep -n '^## '" "and not the shell search that policy refuses, which left the child a recipe it could not run (issue #796)"
    assert_contains "$text" 'guidelines/' "and where the rules live"
    assert_contains "$text" 'openspec/specs/' "and where the capabilities live"
  done
  [ -f "$LOOP_PROJECT_ROOT/AGENTS.md" ] || fail "the document map the prompts name is not in the project root"
  # …and the map the prompts send a child to must not hand it the recipe that child's own policy refuses (issue #796):
  # the prompts route into `AGENTS.md`, so a refused call there is the same defect one document further on.
  text=$(cat "$LOOP_PROJECT_ROOT/AGENTS.md")
  assert_contains "$text" "tools/look.sh '^## ' --path guidelines/" "the document map names the call that answers a section list (issue #796)"
  assert_not_contains "$text" "grep -n '^## '" "and not the shell search its own policy refuses (issue #796)"
  for f in "$LOOP_PROJECT_ROOT"/guidelines/*.md; do
    case "$f" in *.why.md|*.recipes.md) continue ;; esac
    assert_not_contains "$(sed -n '1,30p' "$f")" "grep -n '^## '" \
      "$(basename "$f")'s header names the call the policy allows, not the shell search it refuses (issue #796)"
  done
  # every repository path a skeleton names, read the way a child would read it: relative to the project root it is
  # started in. A glob (`tools/*-cases.sh`) is a pattern and not a path, so it is skipped.
  for f in "$LOOP_ENGINE/prompts/worker.md" "$LOOP_ENGINE/prompts/worker-gate.md" "$LOOP_ENGINE/prompts/worker-tail.md" "$LOOP_ENGINE/prompts/reviewer.md"; do
    for tok in $(grep -oE '(tools|guidelines|openspec|licenses|app)/[A-Za-z0-9_./-]+' "$f" | sed -e 's#[.,)]*$##' | sort -u); do
      case "$tok" in *'*'*) continue ;; esac
      [ -e "$LOOP_PROJECT_ROOT/$tok" ] ||
        fail "$(basename "$f") names $tok, which the project root does not hold"
    done
  done
  return 0
}

# Issue #799: a child learned what its own shell and tools refuse by being refused — 55 of one day's 161 tool errors
# were 17 approvals no unattended process can be asked for (`git stash` 6, `git apply` 3, `git checkout` 2, `git add`
# 2, `git worktree` 1, and 3 for a path outside the working directory), 16 refusals of inline code (13 `python3 <<`,
# 1 `python3 -c`, 2 command substitutions) and 22 `edit` calls rejected for text that had moved or was not unique.
# The block belongs where the project's context files would have been — in the phase prompt, once — and the verbs it
# names are the policy's own list, read out of `REFUSED_VERBS`, so a verb the policy gains is a case that fails rather
# than a child that learns it by being refused (#687 is that policy, #796 the recipe one section up).
case_prompt_thePhasePromptNamesWhatItsShellRefuses() {
  # files: tools/loop/prompts/ tools/pi-shell-refusal.mjs tools/look.sh tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p prompt text f rule probe verbs v
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompt="$p/.pi/issue-loop/items/21/worker-prompt.md"
  [ -f "$prompt" ] || fail "the worker prompt was not rendered"
  text=$(cat "$prompt")
  assert_contains "$text" 'What your own shell and tools refuse' "the worker's prompt names what its own shell refuses"
  assert_contains "$text" 'stage nothing' "and that the commit is the run's and no step of the child's"
  assert_contains "$text" 'git stash' "and one git call the approval it can never be given refuses"
  assert_contains "$text" 'inline code' "and that the block names inline code as refused"
  assert_contains "$text" 'ctx_patch' "and the edit call whose hash refuses instead of a body read twenty turns ago"
  assert_contains "$text" 'tools/look.sh' "and the one call that answers a tree question"

  # A child can be handed one of three skeletons, and each has to state the block's heading: one without it leaves
  # that child with no block at all. The block's own text is asserted once, against the rendered `worker.md` prompt
  # (`$text`) above — not per skeleton, so a differing bullet in `worker-gate.md` is not held here.
  for f in "$LOOP_ENGINE/prompts/worker.md" "$LOOP_ENGINE/prompts/worker-gate.md" "$LOOP_ENGINE/prompts/worker-tail.md"; do
    [ -f "$f" ] || fail "no prompt skeleton at $f"
    assert_contains "$(cat "$f")" 'What your own shell and tools refuse' "$(basename "$f") states the block's heading"
  done

  # The case keeps no second copy of the policy's verb list: it is read out of the module, and every verb that module
  # carries has to be named in the block. A policy that gains a verb, or a prompt that drops one, fails here instead
  # of in a run.
  rule="$LOOP_PROJECT_ROOT/tools/pi-shell-refusal.mjs"
  [ -f "$rule" ] || fail "nothing refuses a shell tree search: tools/pi-shell-refusal.mjs is not in the tree (issue #687)"
  probe="$d/policy.mjs"
  printf '%s\n' 'import { pathToFileURL } from "node:url";' \
    'const mod = await import(pathToFileURL(process.env.RULE).href);' \
    'console.log((mod.REFUSED_VERBS || []).join(" "));' > "$probe"
  verbs=$(RULE="$rule" node "$probe" 2>&1) || fail "the policy's own verb list could not be read: $verbs"
  [ -n "$verbs" ] || fail "tools/pi-shell-refusal.mjs exports no REFUSED_VERBS to keep the prompt's list beside"
  for v in $verbs; do
    assert_contains "$text" "\`$v\`" "the block names the verb the policy refuses, in the policy's own spelling: $v"
  done
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

# A closure records one settled state and may displace the *other* one: an item a person retired that a run's
# fix closes, or a fixed item closed as not to be done. Recording a settled state is the `close` verdict's own
# purpose, exactly as it is `close.sh`'s (`tools/issues/close.sh` passes `--displace-settled` for that reason),
# so the verdict names the transition to the guard every routine writer meets (#684). Without it the first of
# the closure's two writes is refused with exit 3 `status-settled` and the item is left half attempted (#699).
# A closure takes the shaping label off in the same write that records the settled state (capability
# `shaping-inbox`): a closed item the inbox kept is work it says a person owes and nobody can do, exactly as a
# closed item in the decision queue is (#225). The removal is `update.sh`'s, implied by the state this call writes.
case_labels_closeRemovesTheShapingLabel() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/labels.sh tools/issues/update.sh tools/pi-child-events.sh tools/tooling.json
  local d p log
  p=$(new_project); d=$(scratch)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario shaping)
  log="$d/gh-close-shaping.log"; : > "$log"; export GH_STUB_RECORD="$log"
  run_labels "$p" close 91 --reason "reshaped into children" --evidence "the four filed items" >/dev/null 2>&1 ||
    fail "the closure failed"
  assert_contains "$(grep 'issue edit' "$log")" "--remove-label agent/needs-shaping" \
    "the write that settles the item also takes the shaping label off"
  assert_contains "$(cat "$log")" "--add-label status/fixed-by" "and records the state it ended in"
}

case_labels_closeNamesTheSettledTransition() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/labels.sh tools/pi-child-events.sh tools/tooling.json
  local d p log out status
  p=$(new_project); d=$(scratch)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario retired)
  log="$d/gh-close-retired.log"; : > "$log"; export GH_STUB_RECORD="$log"

  out=$(run_labels "$p" close 21 --reason "the fixture cause" --evidence "the fixture evidence" 2>&1); status=$?
  [ "$status" -eq 0 ] || fail "a closure over the other settled state was refused: $out"
  assert_contains "$(cat "$log")" "--add-label status/fixed-by" \
    "the closure records the settled state it means"
  assert_contains "$(cat "$log")" "--remove-label status/retired" \
    "and displaces the one it replaces, so the item carries one state"
  assert_contains "$out" "status/retired" \
    "the settled value the closure displaced is said out loud, so a reader of the call sees the claim that moved"
  assert_eq "1" "$(grep -c 'issue close' "$log")" "the item is closed, not left half attempted"
}

# A shaping verdict (capability `shaping-inbox`) writes the shaping label in the *same* call as the hold and its
# one-line accounting: one `update.sh` write, so there is no window in which the item is held and nameless to the
# queue that exists for it. The comment the same call posts opens with the verdict word, because the queue prints
# the item's own record rather than a summary of it.
case_run_aShapingVerdictWritesTheLabel() {
  # files: tools/loop/labels.sh tools/loop/lib.sh tools/loop/fixtures/scenarios/ tools/pi-child-events.sh tools/tooling.json
  local d p log out status edit
  p=$(new_project); d=$(scratch)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  log="$d/gh-shaping.log"; : > "$log"; export GH_STUB_RECORD="$log"

  out=$(run_labels "$p" too-wide 21 --reason "six capability deltas would have to land together" \
    --evidence "the item dir, the branch, the red test" 2>&1); status=$?
  assert_eq "0" "$status" "a shaping verdict writes its hold: $out"
  assert_eq "1" "$(grep -c 'issue edit' "$log" || true)" "the hold and the label are one write"
  edit=$(grep 'issue edit' "$log")
  assert_contains "$edit" "--add-label agent/needs-shaping" "the shaping label rides that write"
  assert_contains "$edit" "--add-label status/on-hold" "beside the hold it explains"
  assert_contains "$edit" "--remove-label status/open" "and replaces the state the item carried"
  assert_contains "$out" "agent/needs-shaping" "the call reports the label it wrote"
  # The first line names the verdict and states what it means for the reader: the item wants splitting, and no
  # verb in this tooling can split it.
  assert_contains "$(cat "$log")" "too-wide: the item wants splitting" "the comment's first line names the verdict"
  assert_contains "$(cat "$log")" '**The shaping:** six capability deltas' "and carries the clause that produced it"

  # The other three verdicts the profile names behave the same way, and `needs-diagnosis` keeps the decision label
  # it wrote before this capability existed.
  for verdict in unspecifiable not-spec-shaped; do
    : > "$log"
    run_labels "$p" "$verdict" 21 --reason "the fixture cause" --evidence "the fixture evidence" >/dev/null 2>&1 ||
      fail "$verdict was refused"
    assert_contains "$(cat "$log")" "--add-label agent/needs-shaping" "$verdict writes the shaping label"
  done
  : > "$log"
  run_labels "$p" needs-diagnosis 21 --reason "the fixture cause" --evidence "the fixture evidence" >/dev/null 2>&1 ||
    fail "needs-diagnosis was refused"
  assert_contains "$(cat "$log")" "--add-label agent/needs-shaping" "needs-diagnosis writes the shaping label"
  assert_contains "$(cat "$log")" "--add-label agent/needs-decision" "beside the decision label it already wrote"
}

# A verdict the profile does not name as a shaping verdict keeps the bare hold it wrote before this capability:
# the list is the project's judgement, and `needs-target` is a hold without a reshaping in it.
case_run_aVerdictOutsideTheListWritesNoLabel() {
  # files: tools/loop/labels.sh tools/loop/lib.sh tools/loop/fixtures/scenarios/ tools/pi-child-events.sh tools/tooling.json
  local d p log out status
  p=$(new_project); d=$(scratch)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  log="$d/gh-hold.log"; : > "$log"; export GH_STUB_RECORD="$log"

  out=$(run_labels "$p" needs-target 21 --reason "the claim names no target" --evidence "the item dir" 2>&1); status=$?
  assert_eq "0" "$status" "a hold outside the list is still written: $out"
  assert_contains "$(cat "$log")" "--add-label status/on-hold" "the hold is the state it wrote before"
  assert_eq "0" "$(grep -c 'needs-shaping' "$log" || true)" "and no shaping label is written"
  assert_not_contains "$(cat "$log")" "**The shaping:**" "nor does its comment claim a shaping statement"

  # A project that names no shaping verdict declares the list empty, and then no verdict at all writes the label
  # (capability `shaping-inbox`, D2): the list is a declaration, and the hold is still written and accounted for.
  jq '.roles.shapingVerdicts = []' "$p/tools/tooling.json" > "$p/tools/tooling.json.new" &&
    mv "$p/tools/tooling.json.new" "$p/tools/tooling.json" || fail "could not empty the shaping list"
  : > "$log"
  out=$(run_labels "$p" too-wide 21 --reason "the fixture cause" --evidence "the fixture evidence" 2>&1); status=$?
  assert_eq "0" "$status" "a skip in a project with no shaping verdicts is still written: $out"
  assert_contains "$(cat "$log")" "--add-label status/on-hold" "with its hold"
  assert_eq "0" "$(grep -c 'needs-shaping' "$log" || true)" "and no shaping label, because no verdict is named"

  # A profile written before this capability omits both shaping keys altogether. That is the empty declaration,
  # not a half-adapted profile: the run starts, writes its hold, and names no shaping label (task 1.3).
  jq 'del(.roles.shapingAgent, .roles.shapingVerdicts)' "$p/tools/tooling.json" > "$p/tools/tooling.json.new" &&
    mv "$p/tools/tooling.json.new" "$p/tools/tooling.json" || fail "could not drop the shaping keys"
  : > "$log"
  out=$(run_labels "$p" too-wide 21 --reason "the fixture cause" --evidence "the fixture evidence" 2>&1); status=$?
  assert_eq "0" "$status" "a profile that omits the keys still runs: $out"
  assert_contains "$(cat "$log")" "--add-label status/on-hold" "and writes the hold it always wrote"
  assert_eq "0" "$(grep -c 'needs-shaping' "$log" || true)" "with no shaping label"
}

# Shaping work is unbounded by nature, and the ceiling exists because unanswered *decisions* stop new work: an
# item carrying the shaping label must therefore not count against it, or a run refuses for a queue it cannot
# fill (capability `shaping-inbox`, Non-Goals).
case_run_theShapingInboxDoesNotCountAgainstTheCeiling() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/prescreen.sh tools/pi-child-events.sh tools/tooling.json
  local d p out status
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario shaping)
  stub_success "$d"

  out=$(run_loop "$p" --items 1 --minutes 90 --max-decisions 2 2>&1); status=$?
  assert_eq "0" "$status" "five shaping items do not stop a run under a ceiling of two: $out"
  assert_not_contains "$out" "await a decision" "the ceiling counts the decision label only"
  assert_contains "$out" "nothing eligible" "the shaping items are holds, so no work is queued"
  assert_eq "0" "$(grep -c 'needs-decision' "$GH_STUB_RECORD" || true)" "and nothing is escalated for them"
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
  # A falsification reports no gate line, so the run starts no tail child (issue #744): the item's one measurement is
  # the whole of its work, and a second child would have an archive to write that this item never made.
  assert_eq "0" "$(grep -c '^prompt: worker-tail-prompt.md' "$PI_STUB_LOG" || true)" \
    "a report with no gate line starts no tail child"
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

# The cap counts the questions a person can answer, and only those (#745). An item whose owner removed
# `agent/eligible` is parked: the pre-screen never offers it, so no run may answer it, and it must not spend a
# slot of the cap that stops work on the items a person can answer. Measured 2026-11-02 on the live tracker —
# 13 items carried the decision label, 9 of them were candidates, and the recorded cap of 10 refused every run.
case_run_decisionCapCountsOnlyCandidates() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p out status
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario parked-decisions)
  stub_success "$d"

  # The candidates alone reach a cap of two: the run refuses, names the count it compared, and prints the
  # candidates — never the parked decision, which nobody can answer.
  out=$(run_loop "$p" --items 1 --minutes 90 --max-decisions 2 2>&1); status=$?
  assert_eq "5" "$status" "two candidate decisions reach a cap of two"
  assert_contains "$out" "2 of the 3 item(s) that await a decision are candidates" \
    "the refusal names both counts, so the count it compared is visible"
  assert_contains "$out" "#41" "the candidate decisions are printed"
  assert_contains "$out" "#42" "both of them"
  assert_not_contains "$out" "#45" "the parked decision is not printed"
  [ ! -d "$p/.pi/worktrees/loop" ] || fail "a worktree was created although the run refused"

  # The parked decision carries the label but is no candidate: three items are labelled, two are questions. A cap
  # of three is therefore free, and the run works the one item that asks nothing instead of refusing (#745).
  out=$(run_loop "$p" --items 1 --minutes 90 --max-decisions 3 2>&1); status=$?
  assert_eq "0" "$status" "a parked decision does not stop a run whose candidates are below the cap: $out"
  assert_not_contains "$out" "refusing to start" "and nothing is refused"
  assert_eq "46" "$(jq -r '.items[0].issue' "$(state_file "$p")")" "the eligible item is the one worked"
  assert_eq "1" "$(jq -r '.items | length' "$(state_file "$p")")" "and it is the only item the run spent"
}

# The cap that refuses a run is a refusal, and a refusal records why the run ended: this arm printed its two
# counts and the candidates and then left through a bare `exit`, so `run.stop` stayed empty and `report.sh` read
# the state as a process that was killed — "budget left unknown: … it was killed and recorded no stop" — once
# the pid was gone. The pre-screen arm was repaired the same way (#227); this is the arm that was left behind
# (#787). The reason carries the two counts the refusal compared and the decisions it printed, because the
# record is the only thing a later reader of a refusal has.
case_run_theDecisionCapRecordsItsStopReason() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/state.sh tools/loop/report.sh tools/pi-child-events.sh tools/tooling.json
  local d p out status f reason
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario decisions)
  stub_success "$d"

  out=$(run_loop "$p" --items 1 --minutes 90 --max-decisions 3 2>&1); status=$?
  assert_eq "5" "$status" "three candidate decisions reach a cap of three"
  f=$(state_file "$p")
  reason=$(jq -r '.run.stop.reason // ""' "$f")
  assert_contains "$reason" "3 of the 3 item(s) that await a decision are candidates, and the cap is 3" \
    "the refusal is the run's stop reason, naming both counts it compared: $out"
  assert_contains "$reason" "#41" "and one of the decisions it printed"
  assert_contains "$reason" "#43" "all of them"
  assert_contains "$out" "stop: the decision cap refuses the start" "the end is announced as the run's own stop"

  # What a later reader has is the record, read after this run's process is gone: a state whose `run.stop` is
  # empty is the shape of a crash, so the report prints the refusal it now holds and neither of those sentences.
  out=$(run_report "$p") || fail "the report failed"
  assert_contains "$out" "stop: the decision cap refuses the start" "the report prints the recorded refusal"
  assert_not_contains "$out" "recorded no stop" "not the shape of a run that recorded nothing"
  assert_not_contains "$out" "it was killed" "and not the sentence a killed process leaves"
}

# A run that ends because the pre-screen could not reach a verdict records that reason. Before 2026-10-27 the `*)`
# arm of the pre-screen verdict was a bare `exit "$prescreen_status"`: the lock came off through the EXIT trap while
# `run.stop` stayed empty, so the state file then read "the run that wrote this state is gone — its process is not
# running and it recorded no reason", which is the shape of a crash rather than of a refusal.
case_run_aFailedPreScreenRecordsItsStopReason() {
  # files: tools/loop/run.sh tools/loop/prescreen.sh tools/loop/state.sh tools/loop/fixtures/scenarios/ tools/pi-child-events.sh tools/tooling.json tools/issues/fixtures/gh
  local d p out status reason
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario two)
  stub_success "$d"
  export GH_STUB_READ_FAIL="gh issue view"
  out=$(run_loop "$p" --items 1 --minutes 90 --review off 2>&1); status=$?
  unset GH_STUB_READ_FAIL
  assert_eq "2" "$status" "an unanswered tracker ends the run"
  assert_contains "$out" "stop: the pre-screen ended with exit 2" "the end is announced as itself"
  reason=$(jq -r '.run.stop.reason // ""' "$(state_file "$p")")
  assert_contains "$reason" "pre-screen" "and the state keeps the reason: $reason"
  assert_contains "$reason" "no work was started" "naming what the exit did not do"
  [ ! -d "$p/.pi/worktrees/loop" ] || fail "a worktree was created although no item was picked"
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
# command under a `Check …` step has to be a value of the map. The pin closes the *command* side; the map's
# *path* side — which paths a step's tools read — is a measured, hand-kept edge set, pinned by
# `gate#aPathACheckReadsOwesThatStepsCommandSet`, and the residual both pins cannot see is a step added later
# (with the reader edges it brings); the comments in `loop.conf` and `guidelines/Build.md` §3 state it. A profile
# that names no pipeline cannot be pinned and says so — a new step escapes until someone adds it, the cost the
# map's owner accepted over running every step for every item. A step that runs a script the map carries with a
# *selection* the pipeline varies is that entry, not a new command: the loop's suite runs as one leg per group
# selection (`--group run`, issue #781), and a leg runs fewer of the cases the map's own driver entry runs, so
# the pin strips a `--group <selection>` from both sides before comparing, and a selection the map does not cover
# any other way still names its script as missing.
case_gate_theMapCoversEveryPipelineStep() {
  # files: tools/loop/loop.conf .github/workflows/build.yml
  local conf pipeline cmds map missing strip
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
  #
  # The map names the command the pipeline runs, and the pipeline varies parts of it *at run time*: a leg narrows the
  # run with `--group <selection>`, and the step is handed the delta it owes as `${{ steps.delta.outputs.args }}`,
  # which carries `--from-git <base>`. A substitution the pipeline makes is not part of the command the map names,
  # and neither is the gate's own `--from-git {base}` narrowing on the map's side, so both sides are compared on
  # what they have in common. A leg runs *fewer* of the cases the map's driver entry runs — the map's entry is the
  # broader claim — so a leg answers to that entry and needs no pair of its own (issue #781, issue #779).
  strip() { sed -e 's/ \${{[^}]*}}//' -e 's/ --group [^ ]*$//' -e 's/ --from-git [^ ]*$//'; }
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
    | sed -e 's/^bash //' -e 's/^sh //' -e '/^$/d' | strip | sort -u)
  [ -n "$cmds" ] || fail "the pipeline at $pipeline names no shell gate command: the pin would pass over nothing"
  map=$(sed -n 's/^LOOP_GATE_SUITES="\(.*\)"/\1/p' "$conf" | tr ';' '\n' \
    | sed -e 's/^[^|]*|//' -e 's/^bash //' -e 's/^sh //' -e '/^$/d' | strip | sort -u)
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

# The map's path side is a hand-kept copy of which files each step's tools open (issue #289). A path a check
# *reads* — its configuration, or one of the engines it scans — has to owe the whole step that reads it, not only
# the tool whose glob names it: otherwise a delta that puts a project value into `tools/loop/loop.conf` is green
# in the run while `Check portability` reddens, which is the shape that reddened `main` on 2026-10-21. Measured
# on #289: of the reader edges measured for `Check portability` and `Check issue tooling`, none was owed by a
# command of the reading step.
case_gate_aPathACheckReadsOwesThatStepsCommandSet() {
  # files: tools/loop/ tools/loop/loop.conf tools/loop/gate.sh tools/loop/worktree.sh tools/loop-selftest.sh tools/issues/ tools/issues/create.sh tools/issues/check-metadata.sh tools/check-portability.sh tools/check-portability-selftest.sh tools/mutate-selftest.sh tools/mutate.sh tools/portability-mutations.json tools/issue-tooling-selftest.sh tools/status-mutations.json tools/sensors/ tools/sensors/sensors.json tools/build-inputs.sh tools/build-inputs-selftest.sh tools/build-inputs-mutations.sh tools/tooling.json tools/pi-child-events.sh
  local d p conf base plan
  d=$(scratch); p=$(new_project)
  conf="$LOOP_ENGINE/loop.conf"

  # 1. The loop's configuration, which `Check portability` opens: the whole portability step is owed.
  mkdir -p "$p/tools/loop"
  printf 'LOOP_GATE_CMD="tools/nope.sh"\n' > "$p/tools/loop/loop.conf"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the loop configuration' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  plan=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )
  assert_contains "$plan" "tools/check-portability.sh" "the check that reads the loop configuration is owed: $plan"
  assert_contains "$plan" "tools/check-portability-selftest.sh" "and the suite that proves it: $plan"
  assert_contains "$plan" "tools/mutate-selftest.sh" "and the runner's own guards: $plan"
  assert_contains "$plan" "tools/mutate.sh --mutations tools/portability-mutations.json --list" \
    "and the mutation set the same step runs: $plan"
  # …and the path still carries the commands of the tool it names.
  assert_contains "$plan" "tools/loop-selftest.sh" "the loop's own suite is still owed: $plan"

  # 2. `tools/tooling.json`, read by `Check portability` and by `Check issue metadata`: both steps are owed.
  printf '{"repo": "a/b"}\n' > "$p/tools/tooling.json"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the profile' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  plan=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )
  assert_contains "$plan" "tools/check-portability.sh" "the portability check a config owes: $plan"
  assert_contains "$plan" "tools/check-portability-selftest.sh" "and its suite: $plan"
  assert_contains "$plan" "tools/issues/check-metadata.sh" "and the issue-metadata step that reads it too: $plan"

  # 3. `tools/sensors/sensors.json`, read by all three of `Check portability`, `Check the build inputs` and
  #    `Check issue metadata`.
  mkdir -p "$p/tools/sensors"
  printf '{}\n' > "$p/tools/sensors/sensors.json"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the registry' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  plan=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )
  assert_contains "$plan" "tools/check-portability.sh" "the portability check the registry owes: $plan"
  assert_contains "$plan" "tools/build-inputs.sh --check" "and the build-inputs step that reads it: $plan"
  assert_contains "$plan" "tools/issues/check-metadata.sh" "and the issue-metadata step that reads it: $plan"

  # 4. An engine another step's case scans is a reader edge too: `lib#onlyTheSeamCallsTheRealCli` greps
  #    `tools/loop/*.sh` and `tools/sensors/*.sh`, so a delta touching one owes every command of
  #    `Check issue tooling` — and `tools/loop/worktree.sh` is an engine `Check portability` scans as well.
  printf '#!/usr/bin/env bash\nexit 0\n' > "$p/tools/loop/worktree.sh"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'an engine both checks scan' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  plan=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )
  assert_contains "$plan" "tools/check-portability.sh" "an engine the portability check scans owes that check: $plan"
  assert_contains "$plan" "tools/issue-tooling-selftest.sh" "and the issue-tooling step whose case greps it: $plan"
  assert_contains "$plan" "tools/mutate.sh --mutations tools/status-mutations.json --list" \
    "and every command of that step, its mutation sets included: $plan"

  # 5. `tools/issues/*.sh` is in `Check portability`'s engine set as well — the file the pipeline's own
  #    `portability#plantedSubmodulePathFails` case plants a leak into — so an engine there owes that check.
  mkdir -p "$p/tools/issues"
  printf '#!/usr/bin/env bash\nexit 0\n' > "$p/tools/issues/create.sh"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'an engine the portability check scans' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  plan=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )
  assert_contains "$plan" "tools/check-portability.sh" "an engine in the portability check's set owes it: $plan"
  assert_contains "$plan" "tools/check-portability-selftest.sh" "and its suite: $plan"
  return 0
}

# The gate's log moves while a suite runs (issue #269). A suite's output was captured into a variable
# (`out=$($cmd 2>&1)`) and replayed only after the command exited, so a suite that ran for a quarter of an hour
# left the gate's console at two lines for the whole of its wall clock: an operator could not tell a long suite
# from a stopped one, and the failure text of a red one survived only as the last three lines of a tally. The
# stream is `tee`d, so the console and the tally read the one observation.
#
# The reading *waits* for the suite's own first line instead of taking it at an instant fixed before the gate can
# have reached the suite (issue #296, the same defect #287 fixed in the run's pre-screen case, measured
# 2026-10-24). The gate's own start-up — `loop_resolve`, the four profile reads, `git diff --name-only` beside
# `git status --porcelain`, the awk pipeline that de-duplicates the owed commands — is unbounded and is *not* the
# streaming this case is about; a sample at a fixed 1.5 s is a bound on the host, not on the stream, so a loaded
# host (a second suite in the repository, a busy machine — the interference #295 owns) reported the gate's start-up
# cost as a verdict about its stream. The wait has a bound of its own (30 s), so a gate whose suite's text never
# reaches the console still fails here — the mutation record `46.1` is what keeps that half honest — and so does
# one that replays the whole output after the command returned, because the moment the waited-for line is read
# then already carries the summary the suite had not printed yet.
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
  # Wait for the stand-in suite's own first line: the gate writes `gate: <command>` before it runs the suite and
  # the suite writes this line at its start, so it is the earliest moment a reader can tell streamed from replayed
  # — and waiting for it is independent of how long the gate's own start-up took (the comment above).
  out=""
  for _ in $(seq 600); do
    out=$(cat "$log")
    case "$out" in *'case: a note is read'*) break ;; esac
    sleep 0.05
  done
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

# A step ends when the command it started has ended, not when everything that inherited the step's output has let go
# of it (issue #315). The step was `$cmd 2>&1 | tee "$stream"`, so the pipeline — and the step's own wall clock,
# which is taken after it — stayed open while any writer that outlived the command still held the write end: measured
# 2026-10-26, a `tools/loop-selftest.sh` step printed its verdict 565 s in and its own line read `— 1237s`, ~670 s of
# gate after the verdict, with the case bound's watchers the observed concurrent population. The case below is that
# shape and nothing about the loop's own suites: a step whose command printed its verdict, left one writer behind,
# and exited.
case_gate_aStepEndsWithItsCommandNotWithItsWatchers() {
  # files: tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local d p conf base log out pid took start i line_seconds
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_COMPILED_CMD=""\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS=""\n'
    printf 'LOOP_GATE_SUITES="notes/*|bash %s/suite.sh"\n' "$d"
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
  } >> "$conf"
  # The verdict, then a writer that outlives the suite and its own parent — the shape the case bound's watcher had.
  # 20 s is long enough that a step waiting for it is unmistakable and short enough that a red run is not a wait of
  # its own.
  cat > "$d/suite.sh" <<'SUITE'
( sleep 20 ) &
printf 'notes: all 1 cases passed\n'
SUITE
  mkdir -p "$p/notes"
  printf 'a note\n' > "$p/notes/a.md"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the change' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  log="$d/gate.log"
  start=$SECONDS
  ( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" ) > "$log" 2>&1 &
  pid=$!
  # Bounded, not `wait`: on HEAD the gate does not end while the writer lives, and a case that waited for it would
  # report the defect as its own wall clock. Eight seconds is four times what the step costs with no writer at all.
  for ((i = 0; i < 80; i++)); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 0.1
  done
  took=$((SECONDS - start))
  if kill -0 "$pid" 2>/dev/null; then
    kill -TERM "$pid" 2>/dev/null
    wait "$pid" 2>/dev/null
    fail "the step ran on after its command exited: still inside the gate ${took}s into a 20s writer that command left behind"
  fi
  wait "$pid" || fail "the gate failed: $(cat "$log")"
  out=$(cat "$log")
  line_seconds=$(printf '%s\n' "$out" | sed -n "s|^gate: bash $d/suite\\.sh — \([0-9][0-9]*\)s\$|\\1|p" | tail -1)
  [ -n "$line_seconds" ] || fail "the step's own line carries no duration: $out"
  [ "$line_seconds" -lt 8 ] \
    || fail "the step's wall clock (${line_seconds}s) covers the writer it left behind instead of its own command"
  assert_contains "$out" "LOOP-GATE: tests=1 failures=0" \
    "and the verdict is still read from the stream the step wrote: $out"
  assert_contains "$out" "notes: all 1 cases passed" \
    "and the console carries the suite's own line, so the step loses nothing to the writer it left behind: $out"
  return 0
}

# A gate step is bounded (issue #311). The gate was the one clock inside a child that no recorded value answered: the
# run bounds a child with `LOOP_CHILD_MAX_SECONDS`, the suite bounds one case with `LOOP_CASE_MAX_SECONDS`, and a step
# of the gate had no bound at all — measured 2026-10-25, one `tools/loop-selftest.sh` step held a worker for its whole
# wall-clock, and the run ended that worker while its gate log already read `LOOP-GATE: tests=558 failures=0 errors=0`
# and its evidence check already read PASS. The case below is that shape and nothing about the loop's own suites: a
# suite that never returns, a profile whose step bound is two seconds, and a console and a tally that must name the
# step the bound answered.
case_gate_aStepIsBoundedAndTheOverrunNamesIt() {
  # files: tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local d p conf base log out rc pid start took i
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_COMPILED_CMD=""\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS=""\n'
    printf 'LOOP_GATE_SUITES="notes/*|bash %s/suite.sh"\n' "$d"
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
    printf 'LOOP_GATE_STEP_MAX_SECONDS=2\n'
  } >> "$conf"
  # A suite that prints, then waits for a writer of its own and never returns: its own line is what proves the gate
  # still read the stream it wrote, and the writer is what the bound has to end together with the step.
  cat > "$d/suite.sh" <<'SUITE'
printf 'notes: a case that never returns\n'
( sleep 30 ) &
wait
SUITE
  mkdir -p "$p/notes"
  printf 'a note\n' > "$p/notes/a.md"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the change' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  log="$d/gate.log"
  start=$SECONDS
  ( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" ) > "$log" 2>&1 &
  pid=$!
  # Bounded, not `wait`: on HEAD no clock answers this step, so a case that waited for the gate would report the
  # defect as its own wall clock and the suite's bound would end the case rather than read the gate. Twenty seconds is
  # ten times the bound the profile names here.
  for ((i = 0; i < 200; i++)); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 0.1
  done
  took=$((SECONDS - start))
  if kill -0 "$pid" 2>/dev/null; then
    kill -TERM "$pid" 2>/dev/null
    wait "$pid" 2>/dev/null
    fail "the step ran past its bound: still inside the gate ${took}s into a suite that never returns, with the bound set to 2s"
  fi
  wait "$pid"; rc=$?
  out=$(cat "$log")
  assert_eq "1" "$rc" "a step the bound ended fails the gate instead of reporting it green: $out"
  assert_contains "$out" "the step overran its bound of 2s and was ended" \
    "the console names the step that overran and the bound that answered it: $out"
  assert_contains "$out" "notes: a case that never returns" \
    "and the suite's own line still reached the console, so the bound costs no output: $out"
  assert_contains "$(cat "$d"/tally/*.xml 2>/dev/null)" "LOOP-GATE-STEP: bash $d/suite.sh exceeded 2 s" \
    "the tally names the step the bound ended, so a reader of the evidence sees which suite overran: $(cat "$d"/tally/*.xml 2>/dev/null)"
  assert_contains "$(cat "$d"/tally/*.xml 2>/dev/null)" 'errors="1"' \
    "and an ended step is an error of that step, not a suite that passed"
  assert_contains "$(cat "$d"/tally/*.xml 2>/dev/null)" 'tests="0"' \
    "with no case invented for the step that did not report one"
  assert_contains "$out" "LOOP-GATE: tests=" "the gate still prints its one verdict line: $out"
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

# The gate owes one step per owed command and ran them one after the other (issue #801): `run_step` owned one
# `$stream`, one status file and one clock, so the four shell suites a `tools/loop/**` delta owes waited behind each
# other and behind the build even though they share nothing but the read-only engine. The case names three stub
# suites that share nothing, each with an output file of its own, and reads the gate's own wall clock out of the run:
# nearer the longest step than the sum is the whole claim.
case_gate_independentStepsRunTogether() {
  # files: tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local d p conf base out start took s tally
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_COMPILED_CMD=""\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS=""\n'
    printf 'LOOP_GATE_SUITES="notes/*|./suite-a;notes/*|./suite-b;notes/*|./suite-c"\n'
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
    printf 'LOOP_GATE_POOL_JOBS=3\n'
    printf 'LOOP_GATE_STEP_MAX_SECONDS=30\n'
  } >> "$conf"
  # Three steps that share nothing: each sleeps the same 3 s and touches an output file of its own, so a step the
  # gate dropped is visible as the file it never wrote, and three steps run one at a time are visible in the clock.
  # The commands are named from the project root and kept short on purpose: a tally is named after its command and
  # only its first 80 characters survive, so three absolute paths under one scratch directory would share one file.
  for s in a b c; do
    cat > "$p/suite-$s" <<SUITE
sleep 3
: > "$d/touched-$s"
printf 'notes: all 1 cases passed\n'
SUITE
    chmod +x "$p/suite-$s"
  done
  mkdir -p "$p/notes"
  printf 'a note\n' > "$p/notes/a.md"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the change' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  start=$SECONDS
  out=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" 2>&1 ) \
    || fail "the gate failed: $out"
  took=$((SECONDS - start))
  # The sum is 9 s and the longest step is 3 s: 7 s is nearer the longest than the sum, and loose enough that a busy
  # host does not make a green engine red.
  [ "$took" -lt 7 ] \
    || fail "the gate spent ${took}s on three steps of 3 s each — nearer their sum than the longest, so they ran one at a time"
  for s in a b c; do
    [ -e "$d/touched-$s" ] || fail "the step of suite-$s never ran: the wave dropped a step"
  done
  assert_contains "$out" 'LOOP-GATE-POOL: ' "the log names the pool it ran under: $out"
  assert_contains "$out" 'LOOP-GATE: tests=3 failures=0' "every step's own case is in the verdict: $out"
  for s in a b c; do
    tally=$(cat "$d/tally/suite-$s.xml" 2>/dev/null)
    assert_contains "$tally" 'tests="1"' "suite-$s carries its own tally: $tally"
  done
  return 0
}

# A pooled wave must not lend one step another's numbers: a step's tally is its own summary, read from the file that
# step wrote, and the verdict is the sum of those summaries. The failure mode this refuses is the shared `$stream`
# the serial engine could afford (issue #801) — it is the guard the item asks for beside the timing case, and it is
# green on the base by construction, because one step at a time shares nothing either.
case_gate_aFailingSuitesTallyIsUnchangedByItsNeighbours() {
  # files: tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local d p conf base out rc tally failing ok_cases
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_COMPILED_CMD=""\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS=""\n'
    printf 'LOOP_GATE_SUITES="notes/*|./suite-ok;notes/*|./suite-red;notes/*|./suite-ok2"\n'
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
    printf 'LOOP_GATE_POOL_JOBS=3\n'
    printf 'LOOP_GATE_STEP_MAX_SECONDS=30\n'
  } >> "$conf"
  cat > "$p/suite-ok" <<'SUITE'
printf 'notes: all 1 cases passed\n'
SUITE
  # The failing step reports its own numbers: 3 cases, 2 of them failed. Its neighbours report one case each, so a
  # tally that borrowed from the step beside it cannot read as its own.
  cat > "$p/suite-red" <<'SUITE'
sleep 1
printf 'FAIL notes#aCaseThatFails — the reason it failed\n'
printf 'notes: 2 of 3 cases failed\n'
exit 1
SUITE
  cat > "$p/suite-ok2" <<'SUITE'
printf 'notes: all 1 cases passed\n'
SUITE
  chmod +x "$p/suite-ok" "$p/suite-red" "$p/suite-ok2"
  mkdir -p "$p/notes"
  printf 'a note\n' > "$p/notes/a.md"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the change' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  out=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" 2>&1 )
  rc=$?
  assert_eq "1" "$rc" "a failing step still fails the gate: $out"
  assert_contains "$out" 'LOOP-GATE: tests=5 failures=2' "the verdict is the three steps' own numbers: $out"
  failing=$(printf '%s\n' "$out" | sed -n 's/.*— failing:\(.*\)$/\1/p' | tail -1)
  case "$failing" in *suite-red*) : ;; *) fail "the failing step is not the failing one the verdict names: $failing" ;; esac
  case "$failing" in *suite-ok*) fail "a passing neighbour is named among the failing steps: $failing" ;; esac
  tally=$(cat "$d/tally/suite-red.xml" 2>/dev/null)
  assert_contains "$tally" 'tests="3" failures="2"' "the failing step's tally is its own summary: $tally"
  for s in ok ok2; do
    tally=$(cat "$d/tally/suite-$s.xml" 2>/dev/null)
    assert_contains "$tally" 'tests="1" failures="0"' "suite-$s keeps the numbers it reported beside a red step: $tally"
  done
  ok_cases=$(printf '%s\n' "$out" | grep -c 'notes: all 1 cases passed' || true)
  assert_eq "2" "$ok_cases" "each passing step's own line reached the console, once each: $out"
  return 0
}

# A wave of several steps prints each step's output whole when the wave ends, and nothing of it while the wave runs
# (issue #801). Two writers on one console interleave their bytes, and the `gate: <command>` … `— <n>s` block is what
# a reader — and the attributing tool beside the engine — attributes a failure and its step from, so the pooled path
# buffers each step in the file that step owns and prints it under its own header once the wave has drained, in the
# profile's order. A wave of one still streams its lines as it writes them (issue #269); the narrower stream a wave
# of several gets is stated in this change's delta, `## MODIFIED Requirements` — "The gate streams a suite's output
# while it runs".
case_gate_aPooledWavePrintsEachStepWholeWhenItEnds() {
  # files: tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local d p conf base log pid out s started a_line b_line block
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_COMPILED_CMD=""\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS=""\n'
    printf 'LOOP_GATE_SUITES="notes/*|./suite-a;notes/*|./suite-b"\n'
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
    printf 'LOOP_GATE_POOL_JOBS=2\n'
    printf 'LOOP_GATE_STEP_MAX_SECONDS=30\n'
  } >> "$conf"
  # Two suites that share nothing, each printing a line at once and then working for 3 s: a live console carries
  # that line while the suite runs, and a buffered one carries it only once both steps of the wave have ended. Each
  # step also leaves a marker of its own, so the case knows a running wave when it sees one.
  for s in a b; do
    cat > "$p/suite-$s" <<SUITE
printf 'first-$s: the suite starts\n'
: > "$d/started-$s"
sleep 3
printf 'last-$s: the suite ends\n'
printf 'notes: all 1 cases passed\n'
SUITE
    chmod +x "$p/suite-$s"
  done
  mkdir -p "$p/notes"
  printf 'a note\n' > "$p/notes/a.md"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the change' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  log="$d/gate.log"
  ( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" ) > "$log" 2>&1 &
  pid=$!
  # Both steps are running — each wrote its own marker after printing its first line — so a live console would
  # already carry those lines. The wait has a bound of its own (30 s): a gate that never started the wave fails
  # here rather than reading an empty log as a pass.
  started=0
  for _ in $(seq 600); do
    if [ -e "$d/started-a" ] && [ -e "$d/started-b" ]; then started=1; break; fi
    sleep 0.05
  done
  [ "$started" -eq 1 ] || fail "the two suites of the wave never started together: $(cat "$log")"
  out=$(cat "$log")
  assert_not_contains "$out" 'first-a: the suite starts' \
    "no step of a wave reaches the console while the wave runs: $out"
  assert_not_contains "$out" 'first-b: the suite starts' \
    "and the step beside it does not either: $out"
  wait "$pid" || fail "the gate failed: $(cat "$log")"
  out=$(cat "$log")
  # Each step's bytes are its own and together: the line it wrote first, the line it wrote last and its summary sit
  # between its own header and its own duration line, and the block that opens first is the profile's first command.
  a_line=$(printf '%s\n' "$out" | grep -n '^gate: \./suite-a' | head -1 | cut -d: -f1)
  b_line=$(printf '%s\n' "$out" | grep -n '^gate: \./suite-b' | head -1 | cut -d: -f1)
  [ -n "$a_line" ] && [ -n "$b_line" ] || fail "each step of the wave is announced on its own header: $out"
  [ "$a_line" -lt "$b_line" ] || fail "the blocks keep the profile's order: $out"
  block=$(printf '%s\n' "$out" | sed -n "${a_line},$((b_line - 1))p")
  assert_contains "$block" 'first-a: the suite starts' "the first step's own early line is in its own block: $block"
  assert_contains "$block" 'last-a: the suite ends' "with the line it printed later: $block"
  assert_contains "$block" 'notes: all 1 cases passed' "and its summary: $block"
  assert_not_contains "$block" 'first-b' "the step beside it does not print inside it: $block"
  block=$(printf '%s\n' "$out" | sed -n "${b_line},\$p")
  assert_contains "$block" 'first-b: the suite starts' "the second step's block is whole as well: $block"
  assert_contains "$block" 'last-b: the suite ends' "with both of its lines: $block"
  assert_contains "$out" 'LOOP-GATE: tests=2 failures=0' "and the verdict is the sum of the two steps: $out"
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

# The two facts a change used to take by hand and a reviewer could not check: the suite's declared case count and
# the gate's own timing (issue #244). The count is a command's output — the profile's case-listing command, named
# beside the number so it can be reproduced — and the timing is summed from the `time` the gate's own tallies
# carry, so a change quoting "the gate took 12 s" quotes a measurement and not a stopwatch nobody kept. A profile
# that names no case-listing command says so rather than leaving the line out, because a missing line reads as a
# count of zero.
case_measure_printsTheSuiteCountAndTheGateTiming() {
  # files: tools/loop/loop.conf tools/loop/measure.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf conf2 base out
  d=$(scratch); p=$(new_project)
  printf 'state#one\nstate#two\nstate#three\n' > "$d/cases.sh"
  conf="$d/measure.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_SUITES="notes/*|notes-check"\n'
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
    # The profile's case-listing command: the count is this command's output, and the block names the command
    # beside it so the number is reproducible rather than a count taken by hand.
    printf 'LOOP_SUITE_CASES_CMD="cat %s/cases.sh"\n' "$d"
  } >> "$conf"
  mkdir -p "$d/tally"
  printf '<?xml version="1.0"?>\n<testsuite name="s1" tests="4" failures="0" errors="0" time="7"></testsuite>\n' > "$d/tally/s1.xml"
  printf '<?xml version="1.0"?>\n<testsuite name="s2" tests="3" failures="0" errors="0" time="5"></testsuite>\n' > "$d/tally/s2.xml"
  base=$(git -C "$p" rev-parse HEAD)

  out=$( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/measure.sh" --base "$base" 2>&1 )
  assert_eq "0" "$?" "the measuring command succeeded: $out"
  assert_contains "$out" "LOOP-MEASURE-SUITE cases=3" "the declared case count is carried: $out"
  assert_contains "$out" "cmd=cat $d/cases.sh" "with the command that printed it, so the number is reproducible"
  assert_contains "$out" "LOOP-MEASURE-GATE seconds=12 suites=2" \
    "and the gate's own timing is summed from the tallies it wrote: $out"
  assert_contains "$out" "time=7s" "a tally line carries its own measured wall clock: $out"

  # A profile that names no case-listing command: the block says so instead of omitting the line, which a reader
  # would take for a count of zero.
  conf2="$d/none.conf"
  sed '/^LOOP_SUITE_CASES_CMD=/d' "$LOOP_ENGINE/loop.conf" > "$conf2"
  grep -q '^LOOP_SUITE_CASES_CMD=' "$conf2" && fail "this case could not strip the case-listing command from the profile"
  printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d" >> "$conf2"
  out=$( cd "$p" && LOOP_CONF="$conf2" bash "$LOOP_ENGINE/measure.sh" --base "$base" 2>&1 )
  assert_eq "0" "$?" "the measuring command succeeded without the key: $out"
  assert_contains "$out" "LOOP-MEASURE-SUITE cases=-" "a profile with no case-listing command is named, not silent: $out"
  assert_contains "$out" "no case-listing command" "and the absence is spelled out: $out"
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
  # The number rule (issue #244): the worker is told that a quoted number is a command's output, and that a
  # number with neither the block nor a command behind it is not a claim — so it is the worker's job to leave it
  # out, not the reviewer's to catch a guess and buy a second round.
  assert_contains "$text" 'A number the change quotes is the output of a command in its record' \
    "the worker is told what makes a number a claim"
  assert_contains "$text" 'is not a claim' "and that a number with no command behind it is not one"
  assert_contains "$text" 'command in its record' "the rule names where a number's proof lives"
  assert_contains "$text" 'LOOP-MEASURE-SUITE cases=' "the declared case count is named as a block line"
  assert_contains "$text" 'LOOP-MEASURE-GATE seconds=' "and so is the gate's own measured timing"
  return 0
}

case_run_oneItemOrderIsFixed() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p prompts
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  prompts=$(grep '^prompt:' "$PI_STUB_LOG" | awk '{print $2}')
  # One item is three processes since issue #744: the gate child, the tail child the run starts from its gate line,
  # and the reviewer of the change they produced — in that order.
  assert_eq "3" "$(printf '%s\n' "$prompts" | grep -c .)" "one item starts exactly three processes"
  assert_eq "worker-prompt.md worker-tail-prompt.md reviewer-prompt-1.md" "$(printf '%s' "$prompts" | paste -sd' ' -)" \
    "the gate child runs first, the tail second and the reviewer third"
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

# The same claim read where the run can still refuse what follows it (issue #797). The outcome block's arm names an empty
# tree and stays there as the invariant, but it is the *last* thing the run reads: eight records over the archive (seven
# issues, #91 twice) were recorded `blocked` on it, and every one of them learned it after the item's most expensive phase
# had been paid — a gate phase is 20–22 min of a 40–50 min item (measured 2026-11-06, `runs/2026-11-06T11:44:21Z.json`).
# With the gate deferred to a child of its own (issue #758) the run holds the child's answer and the tree *before* that
# child starts, so an answer that claims a change beside a tree still at the base is refused there: no reviewer, no gate
# child, no tail, and the item blocked on the reason that arm writes anyway.
case_run_anEmptyTreeIsRefusedBeforeTheGateChild() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p out prompts item state
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The change child answers the shape a deferred gate is read from — the diff marker and no verdict — and leaves the
  # tree exactly as it found it, which is the whole of what a child that committed nothing leaves behind.
  stub_write "$d" "$STUB_CHANGE_REPORT" 'VERDICT: PASS'
  printf '#!/usr/bin/env bash\n: # this child claims the change and leaves the tree exactly as it found it\n' \
    > "$d/worker-action.sh"
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1) || fail "the run itself failed"
  state=$(state_file "$p")
  item=$(find "$p/.pi/issue-loop/items" -mindepth 1 -maxdepth 1 -type d | head -1)
  # The item is recorded at all: a run that ends on its own stop writes none, and that is a different fault (the arm
  # `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault` owns) — so it is named here with the run's own last words.
  assert_eq "1" "$(jq -r '.items | length' "$state")" \
    "the item is recorded — the run said: $(printf '%s' "$out" | grep '^stop:' | tail -1)"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$state")" "a claim with nothing committed is blocked"
  assert_contains "$(jq -r '.items[0].note' "$state")" \
    "reported success but the tree is still at the base commit — nothing was committed" \
    "and the reason is the one the outcome block's arm already writes"
  # …and it is stated as the item's block, not as the run's own stop: a child that answered the diff marker read the
  # item, so nothing here is "no part of it was read".
  assert_not_contains "$out" "could not start" "the answer is not read as a child that never started"
  # One child and no more: the reviewer and the gate child the split would start, and the tail the gate's own line would
  # start, are exactly what the reading refuses.
  prompts=$(grep '^prompt:' "$PI_STUB_LOG" | awk '{print $2}')
  assert_eq "worker-prompt.md" "$(printf '%s' "$prompts")" "the change child is the only child the run starts"
  assert_eq "1" "$(jq -r '[.steps[] | select(.name == "empty-tree")] | length' "$state")" \
    "the reading that ended the item is recorded as a step of its own"
  assert_contains "$(jq -r '[.steps[] | select(.name == "empty-tree") | .note] | join(" | ")' "$state")" \
    "before any gate child" "and the step says the item ended at that reading"
  assert_eq "0" "$(jq -r '[.steps[] | select(.name == "pre-gate-review" or .name == "worker-gate" or .name == "worker-tail")] | length' "$state")" \
    "no round, no gate child and no tail is started for a tree there is nothing to read, run a gate on or archive"
  [ ! -e "$item/worker-gate-report.md" ] || fail "a gate child ran for a tree at the base"
  [ ! -e "$item/worker-tail-report.md" ] || fail "a tail ran for a tree at the base"
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

# The wait's granularity, not the child's speed (issue #259): the poll that watches for the child's completion record
# slept a whole second, and the heartbeat subshell the wait is joined through slept one as well, so a child that
# answered at once still cost the run 1–2 s of pure sleep — and one run case drives up to three children. Measured
# 2026-10-22 on the item: a copy of the engine whose `sleep 1` became `sleep 0.05` ran
# `run#recordsAFailedReviewOnTheBranch` in 9 s instead of 15 s, i.e. 40 % of that case was the sleep.
case_run_theWaitEndsWhenTheChildDoes() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p stamp t0 t1 ms exit_line
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The worker answers at once, and the 0.15 s floor is what makes the reading about the wait: without it a stub that
  # lost the race against the parent's first `[ -e ]` would let a one-second poll pass.
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' 'case "$1" in worker*) sleep 0.15 ;; esac'
  # Every console line is stamped as it arrives: the transcript carries no clock, and timing the whole item would mix
  # the wait's sleep with the engine's own work — the item's bound is the sleep, not the work.
  stamp="$d/stamped.log"
  {
    while IFS= read -r line; do
      printf '%s\t%s\n' "$(date +%s%N)" "$line"
    done < <( run_loop "$p" --items 1 --minutes 90 --review off 2>&1; printf 'RUN-EXIT %s\n' "$?" )
  } > "$stamp"
  exit_line=$(sed -n 's/^[0-9]*\tRUN-EXIT \(.*\)$/\1/p' "$stamp")
  assert_eq "0" "$exit_line" "the run itself finished cleanly (its console is in $stamp)"
  read -r t0 t1 < <(awk -F'\t' '
    /loop: worker-prompt started/ { if (s == "") s = $1 }
    /loop: worker-prompt finished in/ { e = $1 }
    END { print s + 0, e + 0 }' "$stamp")
  [ "$t0" -gt 0 ] && [ "$t1" -gt "$t0" ] ||
    fail "the wait's start and its end were not both announced (console in $stamp)"
  ms=$(( (t1 - t0) / 1000000 ))
  # 900 ms is the bound the wait must stay inside and not the child's own 150 ms: the second the wait may not spend
  # is what this case is about, and the margin on either side is the difference between a fraction of a second and a
  # whole one.
  [ "$ms" -lt 900 ] || fail "the run took ${ms} ms to notice a worker that answered in 150 ms: the wait's granularity is not below a second (issue #259; console in $stamp)"
  printf 'the wait for a 150 ms child cost %s ms\n' "$ms"
}

# The heartbeat counts elapsed seconds, not wake-ups (issue #259): lowering the poll's granularity without this would
# have made `LOOP_PROGRESS_SECONDS` mean "this many looks" — a line every 1.5 s for a configured 30 once the poll was
# 0.05 s — so the interval is read as seconds lived and the case pins that it still prints one line per interval.
case_run_theProgressLineKeepsItsInterval() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p out n
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' 'case "$1" in worker*) sleep 4 ;; esac'
  out=$(LOOP_PROGRESS_SECONDS=2 run_loop "$p" --items 1 --minutes 90 --review off 2>&1) \
    || fail "the run itself failed"
  n=$(printf '%s\n' "$out" | grep -c '^loop: worker-prompt running' || true)
  # A band and not a number: where in the second the child starts decides whether the line at its own last interval
  # is still printed, and the heartbeat's wake can land either side of it. One line per look would be dozens.
  [ "$n" -ge 1 ] && [ "$n" -le 3 ] ||
    fail "the progress line does not keep the configured 2 s interval: ${n} running line(s) for a 4 s child — $out"
  printf '%s running line(s) for a 4 s child at a 2 s interval\n' "$n"
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
# #126's worker phase ran 83 min that way, issue #203). The bound the run gives it is not one constant for every
# item (issue #663): an item owes a red gate round and a green one, and one gate round of a `tools/loop/**` item
# measured 971 s over 23 suites on 2026-10-26 — 957 s + 233 s + 972 s of gate and 542 s of phases, i.e. 2 704 s
# against the 2 700 s constant, so the child was ended 8 s after its green `gate-summary.xml` was written. #664
# and #666 ended the same way. The case holds both halves: what the profile records is not below that
# measurement, and the run gives the child what the gate owes rather than the floor — three dry runs, because the
# bound is printed in the header before anything is started.
case_run_theChildCapIsNeverBelowWhatOneItemsGateOwes() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/loop.conf tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf out cap owes
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  owes=$(sed -n 's/^LOOP_ITEM_GATE_SECONDS=//p' "$LOOP_CONF")
  [ -n "$owes" ] ||
    fail "the profile records no LOOP_ITEM_GATE_SECONDS: what one item's gate owes is not a recorded number, so the child's bound cannot follow it"
  case "$owes" in ''|*[!0-9]*) fail "LOOP_ITEM_GATE_SECONDS is not a number: $owes" ;; esac
  # The measurement the number is derived from, as one floor: two gate rounds (957 s red + 972 s green), one
  # focused falsification suite (233 s) and the phases beside them (542 s).
  [ "$owes" -ge 2704 ] ||
    fail "LOOP_ITEM_GATE_SECONDS=$owes is below the 2704 s one item measured on 2026-10-26: the profile would record a bound its own item outlives"
  out=$(run_loop "$p" --items 1 --minutes 90 --review off --dry-run 2>&1) || fail "the run itself failed"
  cap=$(printf '%s\n' "$out" | sed -n 's/.*child-max-seconds=\([0-9][0-9]*\).*/\1/p' | head -1)
  [ -n "$cap" ] || fail "the run's header names no child-max-seconds: $out"
  [ "$cap" -ge "$owes" ] ||
    fail "the child's bound ($cap s) is below what one item's gate owes ($owes s): a child ended inside its own item's cost is a green change the run pays for and discards"
  # …and the bound follows the gate rather than the floor, which is the half a single constant cannot answer: a
  # one-minute floor under an hour of gate gives the child the hour.
  conf="$d/follows.conf"
  sed -e 's/^LOOP_CHILD_MAX_SECONDS=.*/LOOP_CHILD_MAX_SECONDS=60/' \
      -e 's/^LOOP_ITEM_GATE_SECONDS=.*/LOOP_ITEM_GATE_SECONDS=3600/' "$LOOP_CONF" > "$conf"
  p=$(new_project)
  out=$( export LOOP_CONF="$conf"; run_loop "$p" --items 1 --minutes 90 --review off --dry-run 2>&1 ) ||
    fail "the run itself failed"
  cap=$(printf '%s\n' "$out" | sed -n 's/.*child-max-seconds=\([0-9][0-9]*\).*/\1/p' | head -1)
  assert_eq "3600" "$cap" "the child is given what the gate owes, not the floor under it"
  # …and a project that records no gate cost keeps its floor, so the derivation invents no bound for a tree whose
  # gate costs nothing.
  conf="$d/floor.conf"
  sed -e 's/^LOOP_CHILD_MAX_SECONDS=.*/LOOP_CHILD_MAX_SECONDS=1200/' \
      -e 's/^LOOP_ITEM_GATE_SECONDS=.*/LOOP_ITEM_GATE_SECONDS=/' "$LOOP_CONF" > "$conf"
  p=$(new_project)
  out=$( export LOOP_CONF="$conf"; run_loop "$p" --items 1 --minutes 90 --review off --dry-run 2>&1 ) ||
    fail "the run itself failed"
  cap=$(printf '%s\n' "$out" | sed -n 's/.*child-max-seconds=\([0-9][0-9]*\).*/\1/p' | head -1)
  assert_eq "1200" "$cap" "a profile with no gate cost keeps the floor a child may run"
}

# The floor `next` refuses by is the other clock the run answers "may this item start?" with, and it is the same
# measurement as the child's bound: an item started with the floor's minutes left can pay for its gate and lose the
# phases after a green round — the loss the child's bound no longer takes, through the other door (issue #663, found
# in the review of the change that fixed the bound). The case reads the two recorded numbers, because that is where
# the relation lives; `state#floorWithNoHistoryIsTheConfiguredMinimum` and its neighbours read the floor's *use*.
case_state_theItemFloorIsOnlyBelowWhatOneItemsGateOwes() {
  # files: tools/loop/loop.conf
  local floor owes
  floor=$(sed -n 's/^LOOP_ITEM_FLOOR_MINUTES=//p' "$LOOP_CONF")
  owes=$(sed -n 's/^LOOP_ITEM_GATE_SECONDS=//p' "$LOOP_CONF")
  [ -n "$floor" ] || fail "the configuration records no LOOP_ITEM_FLOOR_MINUTES"
  case "$floor" in ''|*[!0-9]*) fail "LOOP_ITEM_FLOOR_MINUTES is not a number: $floor" ;; esac
  # A project with no gate owes nothing by the gate key, and the floor is then the configured minimum alone: the
  # relation this case owns does not exist there.
  [ -n "$owes" ] || return 0
  case "$owes" in ''|*[!0-9]*) fail "LOOP_ITEM_GATE_SECONDS is not a number: $owes" ;; esac
  [ $((floor * 60)) -ge "$owes" ] ||
    fail "the item floor (LOOP_ITEM_FLOOR_MINUTES=$floor, $((floor * 60)) s) is below what one item's gate owes ($owes s): an item started there pays for its gate and loses the phases after a green round"
}

# A child that never returns used to be waited for with no bound at all: the run's deadline is consulted
# between items, so a hung gate could spend the whole budget inside one `wait` (measured 2026-10-19: item
# #126's worker phase ran 83 min that way, and its retry died inside the same module that had taken 30 min).
# The cap is a recorded value; the run ends the child and everything the child started, the transcript carries
# the one line that says so, and the item is recorded with that transcript instead of spending the run's clock
# (issue #203). The result is `cut`, not `blocked` (issue #790): the cap is a bound the run enforces on its own
# child, so what it leaves is a phase the next run resumes rather than a finding about the item.
case_run_childOutlastingTheWallClockIsCut() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fifo pid i status out report
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The recorded default is 45 min, which no case can wait for: the same seam the other cases use for the
  # configuration (`LOOP_CONF`) carries a two-second cap here.
  conf="$d/loop.conf"
  conf_with_child_cap 2 "$conf"
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
  wait_for_stub_child "$fifo.ready" || true
  [ -e "$fifo.ready" ] || fail "the stub child never started"
  # The case holds the fifo's writer side for its whole life (`exec 8<>`), so releasing a child never blocks and
  # never leaves a writer behind: the stub opens the fifo for reading, this descriptor opens against it. The open is
  # read-write on purpose (issue #774): a write-only open waits for the reader, and when the run has already ended
  # the child there is no reader left to arrive, so the case would wait on the harness's bound instead of failing.
  exec 8<> "$fifo"
  # The bound on this case, not on the run (issue #308): the run is supposed to end itself inside a second or two,
  # but the host need not be free — 60 s is a wide margin, and the failing reading is a host's slowness reported as
  # a defect in the engine.
  for i in $(seq 1 600); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  if kill -0 "$pid" 2>/dev/null; then
    # The tree before the fix only reaches this line, and the diagnosis has to clean up after itself: the run's
    # own group dies here, and the line the fifo never got is written so a child still waiting on it leaves too.
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    printf 'go\n' >&8 2>/dev/null || true
    wait "$pid" 2>/dev/null
    exec 8>&-
    fail "the run was still waiting after 60 s on a child that outlasted its 2 s cap"
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
  assert_eq "cut" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "an item whose child was ended is recorded cut, not blocked"
  assert_eq "cut" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" \
    "and its verdict is the cut too, which is what a reader counts"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "exceeded 2 s" \
    "and the reason on the item names the cap that ended it"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "nothing is closed on a child the run ended"
  # The item's own wall-clock cap is not the run's deadline (issue #674): the cap ended a child whose item was
  # started and worked. It is still a bound the run enforces, and not a finding about the item (issues #786,
  # #790): the item's tree was left mid-phase, its work is kept beside it and its branch is still at the base, so
  # the next run resumes it — the record says `cut`, carrying `bound` as the mechanism that cut it, writes no
  # decision label and spends no decision slot, and the report names it on the run's own bound line rather than in
  # the block tally. Measured 2026-11-05: six of one day's eight blocks were bound cuts (three of them this clock,
  # #781, #744, #740), and five of six runs stopped on that pair.
  assert_eq "bound" "$(jq -r '.items[0].stopReason // ""' "$(state_file "$p")")" \
    "an item its own child's wall-clock cap ended records the bound as its mechanism"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" \
    "left for the next run rather than asked about" \
    "and says the attempt is the next run's to resume"
  assert_eq "" "$(jq -r '.items[0].escalatedLabel // ""' "$(state_file "$p")")" \
    "and is not escalated as a decision, because a bound asks nobody a question"
  assert_eq "0" "$(grep -c 'add-label agent/needs-decision' "$GH_STUB_RECORD" || true)" \
    "no decision label is written for a cap-ended item"
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
  conf_with_child_cap 3600 "$conf"
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
  wait_for_stub_child "$fifo.ready" || true
  [ -e "$fifo.ready" ] || fail "the stub child never started; the run said: $(cat "$d/out")"
  exec 8<> "$fifo"
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
  assert_eq "cut" "$(jq -r '.items[0].result' "$state")" \
    "an item whose child was ended is recorded cut, not blocked (issue #790)"
  assert_eq "bound" "$(jq -r '.items[0].stopReason // ""' "$state")" \
    "and carries bound as its stop reason, the fact its note spells out in prose (issue #790)"
  assert_contains "$(jq -r '.items[0].note' "$state")" "exceeded" \
    "and the reason on the item names the bound that ended it"
  # The clock that ended it is the run's, not the item's (issue #674): the item is left for the next run, so the
  # run asks the tracker no question about it. A decision label here would spend one of the run's decision slots
  # on a question nobody can answer — the item was cut because its budget ran out, not because it failed.
  assert_eq "" "$(jq -r '.items[0].escalatedLabel // ""' "$state")" \
    "an item the run's own deadline cut is not escalated as a decision"
  assert_contains "$out" "decisions the owner owes: none" \
    "and the report asks the owner no question about the item cut at the deadline"
  assert_contains "$(jq -r '.run.stop.reason // ""' "$state")" "deadline passed at" \
    "the run records the deadline as its stop reason"
}

# A child's own record is the one event the wait ends on, and nothing in that wait asks whether the writer that
# would write it still exists (issue #795). A writer killed before it wrote one — the session that owned the run
# died and the run was reparented to `systemd --user` — is then waited for until the child's wall clock, which is
# 45 minutes of a run that neither works nor stops: measured 2026-11-06, item #793 sat in that poll 17 minutes into
# a 600-minute budget, with its lock still held and its item still `agent/in-progress`, and had nobody looked the
# cap would have blocked the item for a dead child and the lock would have refused every run after it. The wait
# reads the pid of its own writer as **one more** condition, and only while the record is absent.
case_run_aVanishedWriterEndsTheWait() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fifo pid i status out report state pgid
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # An hour of child clock, so only the writer's own liveness can end this wait: every other clock the wait reads
  # is out of reach of a case.
  conf="$d/loop.conf"
  conf_with_child_cap 3600 "$conf"
  grep -qx 'LOOP_CHILD_MAX_SECONDS=3600' "$conf" || fail "this case could not lengthen the child cap in loop.conf"
  fifo="$d/wait.fifo"; mkfifo "$fifo"
  export SLOW_FIFO="$fifo" SLOW_PGID="$d/child.pgid"
  # The child writes down the process group `set -m` made of the run's own subshell — the subshell *is* the writer
  # of the completion record, and its group is the only handle on it a case has — and then blocks on the fifo. The
  # case kills that group while the child is still working, so the writer dies before the record is written: that is
  # the shape a killed session leaves, and it is not the child's own exit. Only the change child blocks; the gate
  # child and the tail are answered at once, which is the shape `stub_per_item_worker` already gives a case.
  stub_per_item_worker "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' '
    printf "%s\n" "$(ps -o pgid= -p $$ | tr -d " ")" > "$SLOW_PGID"
    : > "$SLOW_FIFO.ready"
    read -r _ < "$SLOW_FIFO" || true
'
  set -m
  ( export LOOP_CONF="$conf"
    run_loop "$p" --items 1 --minutes 90 --review off > "$d/out" 2>&1 ) &
  pid=$!
  set +m
  wait_for_stub_child "$fifo.ready" || true
  [ -e "$fifo.ready" ] || fail "the stub child never started; the run said: $(cat "$d/out")"
  exec 8<> "$fifo"
  wait_for_stub_child "$d/child.pgid" || true
  [ -e "$d/child.pgid" ] || fail "the stub child never recorded the group it was started in"
  pgid=$(tr -cd '0-9' < "$d/child.pgid")
  [ -n "$pgid" ] || fail "the stub child wrote no process group"
  kill -KILL -- -"$pgid" 2>/dev/null || fail "this case could not kill the group the child's writer lives in"
  # 60 s is a bound on this case, not on the run: the wait looks once a second and a pid bash has reaped answers
  # `kill -0` with nothing, so the run ends itself a second or two after its writer died.
  for i in $(seq 1 600); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  if kill -0 "$pid" 2>/dev/null; then
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    wait "$pid" 2>/dev/null
    exec 8>&-
    fail "the run was still waiting 60 s after the writer of its child's record was killed"
  fi
  wait "$pid"; status=$?
  exec 8>&-
  out=$(cat "$d/out")
  assert_eq "0" "$status" "a wait the run itself ended is not a failed run"
  assert_contains "$out" "loop: worker-prompt finished in" "the wait is reported as finished, not abandoned"
  assert_contains "$out" "exit 124" "with the timeout's own status, distinct from a child's failure"
  state=$(state_file "$p")
  report="$p/.pi/issue-loop/items/21/worker-report.md"
  [ -f "$report" ] || fail "the item kept no transcript of the child whose writer vanished"
  assert_contains "$(cat "$report")" "LOOP-TIMEOUT: the child's writer" \
    "the transcript names the writer that vanished as the reason the wait ended"
  assert_contains "$(cat "$report")" "vanished before it wrote its completion record" \
    "and says what it was gone before doing"
  assert_eq "cut" "$(jq -r '.items[0].result' "$state")" \
    "an item whose child's writer vanished is recorded cut, not blocked (issue #790)"
  assert_contains "$(jq -r '.items[0].note' "$state")" "vanished before it wrote its completion record" \
    "and the reason on the item names the vanished writer"
  # The run ended this child, so the mechanism is the bound and not a finding about the item (issue #786): the
  # item is left for the next run, and the next run's pre-screen is what takes it up again.
  assert_eq "bound" "$(jq -r '.items[0].stopReason // ""' "$state")" \
    "a vanished writer is a bound the run enforced, not a refusal of the change"
  assert_eq "" "$(jq -r '.items[0].escalatedLabel // ""' "$state")" \
    "and is not escalated as a decision, because a bound asks nobody a question"
  assert_eq "0" "$(grep -c 'add-label agent/needs-decision' "$GH_STUB_RECORD" || true)" \
    "no decision label is written for an item whose writer vanished"
}

# …and the other half of that condition is the one #227 paid for: a poll that believes the child gone blocks for
# ever behind a `wait`, and a child that is merely slow must never be read as one whose writer vanished. The stub
# here is alive and has written nothing, which is what a worker reading its package looks like; the run keeps
# waiting, and the case releases it and lets the run finish.
case_run_aSlowChildIsNotMistakenForAVanishedWriter() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fifo pid i status out report
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  conf="$d/loop.conf"
  conf_with_child_cap 3600 "$conf"
  grep -qx 'LOOP_CHILD_MAX_SECONDS=3600' "$conf" || fail "this case could not lengthen the child cap in loop.conf"
  fifo="$d/wait.fifo"; mkfifo "$fifo"
  export SLOW_FIFO="$fifo"
  # The change child is alive and has written nothing — a worker reading its package looks exactly like this — and
  # then does the item's work once the case releases it. The gate child and the tail answer at once, so the item
  # closes and the run ends by itself; what the case is about is only what happened in the five seconds before.
  stub_per_item_worker "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' '
    : > "$SLOW_FIFO.ready"
    read -r _ < "$SLOW_FIFO" || true
    change=openspec/changes/archive/2026-01-01-fix-demo
    mkdir -p "$change/evidence" app/build/test-results/test
    printf "archived\n" > "$change/proposal.md"
    printf "<testsuite tests=\"1\" failures=\"0\"></testsuite>\n" > app/build/test-results/test/TEST-demo.xml
    cp app/build/test-results/test/TEST-demo.xml "$change/evidence/TEST-demo.xml"
    printf "the fix\n" > demo-fix.txt
'
  set -m
  ( export LOOP_CONF="$conf"
    run_loop "$p" --items 1 --minutes 90 --review off > "$d/out" 2>&1 ) &
  pid=$!
  set +m
  wait_for_stub_child "$fifo.ready" || true
  [ -e "$fifo.ready" ] || fail "the stub child never started; the run said: $(cat "$d/out")"
  exec 8<> "$fifo"
  # The case's own wait, not the run's: the wait looks once a second, so five seconds of a live child that has
  # written nothing is five looks at a child that is there. What the case asserts is that not one of them ended it.
  for i in $(seq 1 50); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  kill -0 "$pid" 2>/dev/null ||
    fail "the run stopped on a live child that had not finished yet; it said: $(cat "$d/out")"
  report="$p/.pi/issue-loop/items/21/worker-report.md"
  assert_not_contains "$(cat "$report" 2>/dev/null || true)" "vanished" \
    "a live child that is merely slow is never read as one whose writer vanished"
  printf 'go\n' >&8 2>/dev/null || true
  for i in $(seq 1 600); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  if kill -0 "$pid" 2>/dev/null; then
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    wait "$pid" 2>/dev/null
    exec 8>&-
    fail "the run did not finish after the slow child answered"
  fi
  wait "$pid"; status=$?
  exec 8>&-
  out=$(cat "$d/out")
  assert_eq "0" "$status" "a run whose live child answered at last is not a failed run"
  assert_contains "$out" "loop: worker-prompt finished in" "the wait ends on the record the child wrote itself"
  assert_not_contains "$out" "exit 124" "and not at the run's own timeout"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" \
    "the item the slow child finished is the item the run recorded"
}

# The item's worker is not the only child the run's clock can end (issue #674): the deadline is consulted while a
# child runs, and a reviewer is a child. Its transcript carries the same `LOOP-DEADLINE` line, and a round the clock
# ended was cut rather than answered — so the run asks the tracker no question about the item, retries no round and
# starts no rework worker for a verdict nobody wrote, and leaves the item for the next run.
case_run_reviewerEndedAtTheRunsDeadlineIsNotADecision() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fifo pid i status out state retry rework review_report
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The child cap is an hour, so only the deadline can end the reviewer.
  conf="$d/loop.conf"
  conf_with_child_cap 3600 "$conf"
  grep -qx 'LOOP_CHILD_MAX_SECONDS=3600' "$conf" || fail "this case could not lengthen the child cap in loop.conf"
  fifo="$d/review.fifo"; mkfifo "$fifo"
  # The worker finishes the item, so the run reaches its review round; the reviewer is the child the clock ends.
  # The action blocks on the fifo for the reviewer only: the stub's own `PI_STUB_WAIT` blocks every child started
  # while it is set, and a worker that never answered never gets the run as far as a review.
  printf 'case "$1" in\n  reviewer*)\n    : > %q\n    read -r _ < %q || true\n    ;;\nesac\n' \
    "$fifo.ready" "$fifo" > "$d/block-reviewer.sh"
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' "$(cat "$d/block-reviewer.sh")"
  set -m
  ( export LOOP_CONF="$conf"
    run_loop "$p" --items 3 --minutes 90 --review on > "$d/out" 2>&1 ) &
  pid=$!
  set +m
  wait_for_stub_child "$fifo.ready" || true
  [ -e "$fifo.ready" ] || fail "the reviewer never started; the run said: $(cat "$d/out")"
  exec 8<> "$fifo"
  # The run's deadline arrives while the reviewer waits — the same hand edit the worker's own case takes, and for
  # the same reason: a shorter `--minutes` is refused by the item floor before any child starts.
  state=$(state_file "$p")
  jq '.run.deadlineEpoch = ((now | floor) + 2) | .run.deadlineAt = "an earlier moment"' "$state" > "$state.tmp" &&
    mv "$state.tmp" "$state" || fail "this case could not move the run's recorded deadline"
  for i in $(seq 1 600); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  if kill -0 "$pid" 2>/dev/null; then
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    printf 'go\n' >&8 2>/dev/null || true
    wait "$pid" 2>/dev/null
    exec 8>&-
    fail "the run was still waiting 60 s after its own recorded deadline passed while the reviewer ran"
  fi
  wait "$pid"; status=$?
  exec 8>&-
  out=$(cat "$d/out")
  assert_eq "0" "$status" "a run whose reviewer the deadline ended is not a failed run"
  assert_contains "$out" "exit 124" "the wait ends with the timeout's own status"
  review_report="$p/.pi/issue-loop/items/21/reviewer-report-1.md"
  [ -f "$review_report" ] || fail "the round the deadline ended kept no transcript"
  assert_contains "$(cat "$review_report")" "LOOP-DEADLINE: the run's deadline passed at an earlier moment" \
    "the reviewer's transcript names the clock that ended it"
  retry="$p/.pi/issue-loop/items/21/reviewer-report-1-retry.md"
  [ ! -f "$retry" ] || fail "a retry was started past the deadline, for a round the clock ended rather than the transport"
  rework="$p/.pi/issue-loop/items/21/worker-prompt-round2.md"
  [ ! -f "$rework" ] || fail "the cut round was read as a refusal and bought a rework worker"
  assert_eq "cut" "$(jq -r '.items[0].result' "$state")" "the item is recorded cut"
  assert_contains "$(jq -r '.items[0].note' "$state")" "deadline passed while the reviewer" \
    "and the reason on the item names the clock and the child it ended"
  assert_eq "" "$(jq -r '.items[0].escalatedLabel // ""' "$state")" \
    "an item whose reviewer the run's own deadline cut is not escalated as a decision"
  assert_contains "$out" "decisions the owner owes: none" \
    "and the report asks the owner no question about the item cut at the deadline"
  assert_contains "$(jq -r '.run.stop.reason // ""' "$state")" "deadline passed at" \
    "the run records the deadline as its stop reason"
}

# A round the reviewer's own turn bound ended has no answer to read: its transcript carries `LOOP-TIMEOUT` and no
# `VERDICT:` line, and reading that silence as a refusal named a reviewer that refused nothing and bought a rework
# worker for it — measured on item #51, whose round 1 ended at the 250-turn bound while the record said
# `blocked`/`reviewRounds 1` (issue #247, review round 2). The round is cut: no worker is started for it, and the
# state records the verdict a rate counts as undecided rather than as a refused round.
#
# The item is then recorded `cut`, not `blocked` (issue #785). A round the run's own bound ended is a phase the next
# run resumes, not a finding about the item: the change is archived, the gate answered and the branch carries the
# commits, so a `blocked` item spent a decision slot and a person's evening on #780, #783, #767 and #763 on
# 2026-11-05 — each of them with a green gate and a `LOOP-VERDICT: closed` tail behind it. What the cut leaves on the
# branch is a **cut** round, not an announcement nobody answered, so `branch-state` reads the attempt as work the
# loop finishes rather than as `review-unanswered`, a question only its owner can answer.
case_run_aReviewerEndedAtItsTurnBoundIsNotARefusal() {
  # files: tools/loop/fixtures/pi tools/loop/loop.conf tools/loop/report.sh tools/loop/run.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fifo fixture action pid i status out state review_report retry rework branch tree
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  conf="$d/loop.conf"
  # The wall clock is an hour, so only the reviewer's own turns can end it, and three turns is that bound.
  sed -e 's/^LOOP_CHILD_MAX_SECONDS=.*/LOOP_CHILD_MAX_SECONDS=3600/' \
      -e 's/^LOOP_CHILD_TURNS_MAX_REVIEWER=.*/LOOP_CHILD_TURNS_MAX_REVIEWER=3/' "$LOOP_CONF" > "$conf" ||
    fail "could not record the two bounds in loop.conf"
  grep -qx 'LOOP_CHILD_TURNS_MAX_REVIEWER=3' "$conf" || fail "this case could not shorten the reviewer's turn bound in loop.conf"
  # The reviewer takes three turns and then waits. The stream has to exist while the child runs, so its own action
  # writes it — the stub prints `PI_STUB_EVENTS` only after it has been released.
  fixture="$d/events.jsonl"
  for i in 1 2 3; do printf '{"type":"turn_end","message":{"role":"assistant"}}\n'; done > "$fixture"
  fifo="$d/review.fifo"; mkfifo "$fifo"
  action="$d/turn-reviewer.sh"
  cat > "$action" <<EOF
#!/usr/bin/env bash
case "\$1" in
  reviewer*)
    ev="\${LOOP_CHILD_EVENTS:-}"
    [ -n "\$ev" ] || exit 1
    cat "$fixture" >> "\$ev"
    : > "$fifo.ready"
    read -r _ < "$fifo" || true
    ;;
esac
EOF
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' "$(cat "$action")"
  set -m
  ( export LOOP_CONF="$conf"
    run_loop "$p" --items 1 --minutes 90 --review on > "$d/out" 2>&1 ) &
  pid=$!
  set +m
  wait_for_stub_child "$fifo.ready" || true
  [ -e "$fifo.ready" ] || fail "the reviewer never started; the run said: $(cat "$d/out")"
  exec 8<> "$fifo"
  for i in $(seq 1 600); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  if kill -0 "$pid" 2>/dev/null; then
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    printf 'go\n' >&8 2>/dev/null || true
    wait "$pid" 2>/dev/null
    exec 8>&-
    fail "the run was still waiting 60 s on a reviewer that had taken 3 turns past a recorded bound of 3"
  fi
  wait "$pid"; status=$?
  exec 8>&-
  out=$(cat "$d/out")
  state=$(state_file "$p")
  assert_eq "0" "$status" "a run whose reviewer the turn bound ended is not a failed run"
  review_report="$p/.pi/issue-loop/items/21/reviewer-report-1.md"
  [ -f "$review_report" ] || fail "the round the turn bound ended kept no transcript"
  assert_contains "$(cat "$review_report")" "LOOP-TIMEOUT: the child reached the recorded turn bound of 3 turns" \
    "the reviewer's transcript names the bound that ended it"
  retry="$p/.pi/issue-loop/items/21/reviewer-report-1-retry.md"
  [ ! -f "$retry" ] || fail "a round the run's own bound ended was retried as if the transport had cut it"
  rework="$p/.pi/issue-loop/items/21/worker-prompt-round2.md"
  [ ! -f "$rework" ] || fail "the cut round was read as a refusal and bought a rework worker"
  assert_eq "cut" "$(jq -r '.items[0].result' "$state")" \
    "the item is recorded cut, not blocked: its work finished and the round the run ended is nobody's question"
  assert_eq "cut-short" "$(jq -r '.items[0].metrics.reviewVerdict' "$state")" \
    "and its record carries the cut, not a refusal no reviewer made"
  assert_contains "$(jq -r '.items[0].note' "$state")" "turn bound" "the reason on the item names the bound"
  assert_eq "" "$(jq -r '.items[0].escalatedLabel // ""' "$state")" \
    "a cut round writes no decision label on the item"
  assert_contains "$out" "decisions the owner owes: none" "and the report asks the owner no question about it"
  assert_contains "$out" "cut short by the run's own bound: #21 cut-short" \
    "the report names the item the run's bound cut, beside the blocks it did not cause"
  # The branch is what the next run reads, and the round the run ended is written on it as a cut: the announcement
  # the run itself withdrew, and a state the pre-screen offers as work the loop finishes rather than as a decision
  # a person owes (#262's `review-unanswered` is the branch of a round nobody graded, which this is not).
  branch=$(git -C "$p" for-each-ref --format='%(refname:short)' 'refs/heads/fix/issue-21-*' | head -1)
  [ -n "$branch" ] || fail "the item's branch is not there to be read"
  tree=$(git -C "$p" ls-tree -r --name-only "$branch")
  assert_contains "$tree" "review-round-1-cut.md" "the round the bound ended is recorded on the branch as a cut"
  assert_not_contains "$tree" "review-round-1-requested.md" \
    "and its announcement is withdrawn: the run made both statements, so it may not leave them contradicting"
  assert_eq "adoptable" "$(run_worktree "$p" branch-state 21 "$branch" 2>/dev/null | cut -f1)" \
    "the branch reads as work the next run can finish, not as a round nobody answered"
  assert_contains "$out" "review: - · no round of this run ended in a verdict · 1 undecided · 0 never reviewed" \
    "the run's own rate counts the cut round as undecided, never as a refused round"
}
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
  conf_with_child_cap 2 "$conf"
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
  wait_for_stub_child "$fifo.ready" || true
  if [ ! -e "$fifo.ready" ]; then
    kill -9 "$pid" 2>/dev/null
    fail "the stub child never started"
  fi
  # The case holds the fifo's writer side for its whole life, so releasing a child never blocks and never leaves a
  # writer behind.
  exec 8<> "$fifo"
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
  conf_with_child_cap 2 "$conf"
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
  wait_for_stub_child "$fifo.ready" || true
  if [ ! -e "$fifo.ready" ]; then
    kill -9 "$pid" 2>/dev/null
    fail "the stub child never started"
  fi
  # The case holds the fifo's writer side for its whole life, so releasing a child never blocks and never leaves a
  # writer behind. The child is never released: the 2 s cap ends it with its file written.
  exec 8<> "$fifo"
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
  assert_eq "cut" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "an item whose child was ended by the cap is recorded cut, not blocked"
  # The work the ended child left is named in the item's own record (issue #790): the patch is where the next
  # run's reader finds it, and the item is a phase to resume rather than a question to answer.
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "its work is kept at" \
    "and the cut names the patch the child's leftover work was written to"
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

# A run ended by a signal ended its child but not the heartbeat subshell beside it (`on_exit` knew `active_child`
# and nothing else, and the `$stop` file the heartbeat loops on is only created after the wait loop returns), so a
# process of a run that was gone kept reading its tree — measured 2026-10-25, an orphan still printing
# `running 56m00s` long after its run had exited, and another, from a self-test of the day before, still alive the
# next afternoon. What the case asserts is the run's own promise: after the signal, nothing that names the case's
# tree as its working directory is left.
case_run_noProcessOfASignalledRunOutlivesIt() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/lib.sh tools/pi-child-events.sh tools/tooling.json
  local d p block first waited left i
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  block="$d/fifo"; mkfifo "$block"; export PI_STUB_WAIT="$block"
  core_ensure
  ( cd "$p" && exec bash "$LOOP_ENGINE/run.sh" --items 1 --minutes 90 --review off ) > "$d/run.log" 2>&1 &
  first=$!
  # The fifo is held read-write: the run's trap may end the child before this case releases it, and an open that
  # waited for a reader would hang the case instead of testing it (issue #259's own lesson).
  exec 8<> "$block"
  waited=0
  wait_for_stub_child "$block.ready" || true
  [ -f "$block.ready" ] || { printf '\n' >&8; wait "$first"; fail "the run never reached its child"; }

  kill -TERM "$first"
  printf '\n' >&8
  wait "$first" 2>/dev/null
  exec 8>&-
  unset PI_STUB_WAIT
  # What is left is read the way `loop-clean.sh` reads it: a process that still names this case's tree as its
  # working directory is a process of this run, whatever its argv says. The root is the case's own
  # (`LOOP_CASE_TMP`), not the first scratch directory: `new_project` takes one of its own beside it, and the
  # run's processes name *that* one.
  left=""
  for i in $(seq 1 50); do left=$(loop_pids_under "$LOOP_CASE_TMP"); [ -z "$left" ] && break; sleep 0.1; done
  [ -z "$left" ] || fail "a process of the signalled run outlived it: $(printf '%s' "$left" | tr '\n' ' ') (run said: $(tail -2 "$d/run.log" | tr '\n' ' '))"
  return 0
}

# The tree a child the run ended left is kept — but it was only kept *in the tree*: 2026-10-25 the item whose gate
# hung was repaired by hand from `git status` and a diff, because the run recorded that the tree was dirty and
# wrote the diff nowhere, and the only repair it named was `--reset-worktree`. The patch is the record: the tracked
# change the child left and the untracked files beside it, written into the item's own evidence directory before the
# run moves on, and named in the transcript a reader of the blocked item is handed.
case_run_treeOfACapEndedChildIsKeptAsAPatch() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/state.sh tools/loop/report.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fifo pid i status out tree report patch
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  conf="$d/loop.conf"
  conf_with_child_cap 2 "$conf"
  grep -qx 'LOOP_CHILD_MAX_SECONDS=2' "$conf" || fail "this case could not shorten the child cap in loop.conf"
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  # The child's own work, half applied: an untracked file it never finished and an edit to a tracked one, written
  # before it blocks on the fifo. The repo the case runs in is a throwaway, so appending to a tracked file costs
  # nothing and is what makes the patch's tracked half observable.
  {
    printf 'case "$1" in\n'
    printf '  worker*)\n'
    printf '    printf "half-applied\\n" > half-applied.txt\n'
    printf '    printf "unfinished edit\\n" >> tools/tooling.json\n'
    printf '    ;;\n'
    printf 'esac\n'
  } > "$d/worker-action.sh"
  export PI_STUB_ACTION="$d/worker-action.sh"
  tree="$p/.pi/worktrees/loop"
  fifo="$d/wait.fifo"; mkfifo "$fifo"
  set -m
  ( export LOOP_CONF="$conf" PI_STUB_WAIT="$fifo"
    run_loop "$p" --items 1 --minutes 90 --review off > "$d/out" 2>&1 ) &
  pid=$!
  set +m
  wait_for_stub_child "$fifo.ready" || true
  if [ ! -e "$fifo.ready" ]; then
    kill -9 "$pid" 2>/dev/null
    fail "the stub child never started"
  fi
  exec 8<> "$fifo"
  for i in $(seq 1 200); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  if kill -0 "$pid" 2>/dev/null; then
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    printf 'go\n' >&8 2>/dev/null || true
    wait "$pid" 2>/dev/null
    exec 8>&-
    fail "the run was still waiting after 20 s on a child that outlasted its 2 s cap"
  fi
  wait "$pid"; status=$?
  exec 8>&-
  out=$(cat "$d/out")
  assert_eq "0" "$status" "a child the run itself ended is not a failed run: $out"
  report="$p/.pi/issue-loop/items/21/worker-report.md"
  patch="$p/.pi/issue-loop/items/21/leftover-from-killed-run.patch"
  [ -f "$patch" ] || fail "the work a child the cap ended left was not kept as a patch (transcript: $(cat "$report" 2>/dev/null))"
  assert_contains "$(cat "$patch")" "half-applied.txt" "the patch carries the untracked file the child left"
  assert_contains "$(cat "$patch")" "unfinished edit" "and the tracked change it left"
  assert_contains "$(cat "$report")" "LOOP-LEFTOVERS: " "the transcript names the record a reader of the blocked item is handed"
  assert_contains "$(cat "$report")" "leftover-from-killed-run.patch" "with the file itself"
  # The record is persisted beside the transcript (issue #674): the run's own state names the patch, so the report
  # the run prints can point a caller at the work an ended child left without a reader having to find the item's
  # transcript first. `state.sh done --leftover` is what writes it, and the action list is where it is printed.
  assert_eq "$patch" "$(jq -r '.items[0].leftover // ""' "$p/.pi/issue-loop/run.json")" \
    "the item's state record names the patch the ended child's work is kept in"
  assert_contains "$out" "recover #21" "and the report's action list names the recovery as the caller's"
  assert_contains "$out" "leftover-from-killed-run.patch" "with the path it is kept at"
  assert_eq "$(git -C "$tree" status --porcelain | grep -c 'half-applied.txt' || true)" "1" \
    "and the tree still carries what the child left, so the patch is a record beside it and not a replacement"
  return 0
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
  conf_with_child_cap 2 "$conf"
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
  assert_eq "cut" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "an item whose rework worker was ended by the cap is recorded cut, not blocked (issue #790)"
  assert_eq "bound" "$(jq -r '.items[0].stopReason' "$(state_file "$p")")" \
    "and the mechanism the bound ended it with is kept beside the result"
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

# The same fault with the status a provider leaves behind (issue #742). A child that exits 0 while every request it
# made was refused writes an answer of nothing: measured 2026-11-01T23:30:01Z, #739 and #740 each left a 0-byte
# `worker-report.md`, each had all four of its `turn_end` events on `stopReason: error`, and every request had been
# refused by the provider — yet the run recorded both with `piExit=0`, both as `blocked` on "reported success but the
# tree is still at the base commit", and escalated both with `agent/needs-decision` and `status/on-hold`: two
# questions no reviewer and no worker asked spent two of the ten decision slots the project records, and the run then
# stopped on two consecutive blocked items 125 s into a 360-minute budget. The status is not what separates the two
# kinds of child; the answer is: a tree at the base beside an answer that carries no closing verdict line is "no
# part of it was read" whatever status the child ended with.
case_run_aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p out status log sf report
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The child's status is the stub's own default 0 and its answer is empty: no report block, no words of its own and
  # no action in the tree behind it, which is the whole of what a provider that refuses every call leaves. Nothing is
  # installed, so nothing of the item is touched.
  unset PI_STUB_SAY PI_STUB_REPORT PI_STUB_REPORT_WORKER PI_STUB_REPORT_REVIEWER PI_STUB_EVENTS PI_STUB_ACTION
  export PI_STUB_EXIT=0
  export PI_STUB_LOG="$d/pi.log"; : > "$PI_STUB_LOG"
  export GH_STUB_RECORD="$d/gh.log"; : > "$GH_STUB_RECORD"
  out=$(run_loop "$p" --items 1 --minutes 90 --review off 2>&1); status=$?
  sf=$(state_file "$p")
  report="$p/.pi/issue-loop/items/21/worker-report.md"
  # The shape this case is about is both halves at once: a child that ran and answered nothing. A run that started no
  # child at all is a different case (the host floor's), and this one must not pass by being that.
  assert_eq "1" "$(grep -c '^invocation' "$PI_STUB_LOG" || true)" "one child was started and it answered"
  assert_eq "0" "$(wc -c < "$report" 2>/dev/null || printf 0)" "the answer it left is empty"
  # A child that never read the item is not a failed run…
  assert_eq "0" "$status" "the run ends on its own stop, not with a child's failure"
  # …and not a failed item either: nothing about #21 was read, so nothing about #21 is recorded.
  assert_eq "0" "$(jq -r '.items | length' "$sf")" "the item is not recorded as a blocked one"
  # What happened is named as the run's own stop, and the stop carries the measurement that decided it — the empty
  # answer — instead of a status that says the child succeeded.
  assert_contains "$out" "could not start" "the stop says what did not happen"
  assert_contains "$(jq -r '.run.stop.reason // ""' "$sf")" "could not start" \
    "the stop is recorded, not only printed"
  assert_contains "$(jq -r '.run.stop.reason // ""' "$sf")" "0 B" \
    "and names the empty answer, not the status alone"
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
  # The item is worked by its two worker-phase children — the gate child and the tail it starts — with the review off.
  assert_eq "2" "$(grep -c 'prompt:' "$d/pi.log" || true)" "the item's two children are started"
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

# The placement reading belongs to the run, not to the suite (issue #703). `loop_tree_pids` asks `pgrep` for the
# child's process group and for every level below it, and both calls swallow their stderr: on a host without procps
# the walk names only the child, `outside_write_seen`'s two filters drop that pid, and every difference is recorded
# and none is ever placed with no line saying so. `--strict-outside-write yes` still blocks, but the placement half
# of the rule never fires — and a run started by hand never consults the suite that refuses such a host. So the run
# refuses at its header, before a worktree, a branch or a child exists.
#
# The host is simulated with a PATH of symlinks: every executable the current PATH reaches is linked into one
# directory except `pgrep`, so what is missing is exactly the one tool the suite requires and the run now does too.
case_run_aHostWithoutThePlacementReadingIsRefused() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p farm dir entry name out status
  local -a dirs
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  farm="$d/path"; mkdir -p "$farm"
  IFS=: read -r -a dirs <<< "$PATH"
  for dir in "${dirs[@]}"; do
    [ -d "$dir" ] || continue
    for entry in "$dir"/*; do
      name=${entry##*/}
      [ "$name" = pgrep ] && continue
      [ -x "$entry" ] || continue
      if [ -e "$farm/$name" ] || [ -L "$farm/$name" ]; then continue; fi
      ln -s "$entry" "$farm/$name" 2>/dev/null || true
    done
  done
  if PATH="$farm" command -v pgrep >/dev/null 2>&1; then
    fail "this case could not take pgrep off the host it simulates"
  fi
  out=$( ( PATH="$farm" run_loop "$p" --items 0 --minutes 90 ) 2>&1 ); status=$?
  assert_eq "2" "$status" "a host without the placement reading is a usage refusal: $out"
  assert_contains "$out" "pgrep" "the refusal names the command the reading is missing"
  assert_contains "$out" "and place none" "and the outcome it would cause, not a bare tool name"
  [ ! -d "$p/.pi/worktrees/loop" ] || fail "a worktree was prepared on a host that cannot place a write"
  assert_eq "" "$(cat "$PI_STUB_LOG" 2>/dev/null || true)" "no agent process was started"
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
  local d p note item
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # A child that writes the project's own tree and leaves a process of its own standing there: the file is taken
  # back before the child ends, so the reading at its exit sees nothing. Only the reading taken *while* it ran can
  # tie the write to the child — that is the evidence this change adds, and it decides the item (#286).
  #
  # On the engine this case was repaired against (HEAD before the reading got a cadence of its own), the run read
  # the project's tree only on its reporting cadence — `LOOP_PROGRESS_SECONDS`, which this case does not lower — so
  # a reading could land outside the writer's life, nothing was placed, and the item was recorded `done`.
  #
  # The write outlives the run's reading by a wide margin, and the process standing beside it outlives the write
  # (issue #673): the reading is taken once per whole second of the child's life, and the difference has to be there
  # when it is taken. Measured on HEAD 2026-10-26, the reading that saw the difference landed 0.45–0.76 s after the
  # write; the writer below stands for 10 s and the file for 8 s, so the margin is ~7 s against a 1 s cadence instead
  # of the ~1 s that a 2 s writer left — the width is what a busy host cannot eat, and the tracker entry's own
  # rate for this case is 1 run in 3 recorded `done` (issue #673). The process is ended with the child's group by
  # the run's own cap, and the case's group is ended by the driver, so nothing is left standing either way.
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'case "$1" in worker*) printf "stray\n" >> "$LOOP_CHILD_ROOT/demo-owned.txt"
       ( cd "$LOOP_CHILD_ROOT" && exec sleep 10 ) &
       sleep 8; rm -f "$LOOP_CHILD_ROOT/demo-owned.txt" ;; esac'
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "a write the run can place in the child's own tree decides the item"
  note=$(jq -r '.items[0].note' "$(state_file "$p")")
  assert_contains "$note" "outside the worktree" "the reason names the tree that changed"
  assert_contains "$note" "attributed to this child" "and says the run tied the difference to this child"
  # …and the reading decides the item **where it is taken**, not at the outcome block after a gate (issue #800): the
  # item's steps carry the reading under the name a later reader looks it up by, and no gate child was started for
  # work the run cannot vouch for — the price the tracker entry names was a whole item *and* its gate.
  item=$(find "$p/.pi/issue-loop/items" -mindepth 1 -maxdepth 1 -type d | head -1)
  assert_eq "1" "$(jq -r '[.steps[] | select(.name == "outside-write")] | length' "$(state_file "$p")")" \
    "the reading that ended the item is recorded as a step of its own"
  assert_contains "$(jq -r '[.steps[] | select(.name == "outside-write") | .note] | join(" | ")' "$(state_file "$p")")" \
    "attributed to this child" \
    "and the step names the child the process was tied to, which is what the requirement asks the note for"
  assert_contains "$(jq -r '[.steps[] | select(.name == "outside-write") | .note] | join(" | ")' "$(state_file "$p")")" \
    "before any gate child" "and the step says the item ended at that reading"
  assert_eq "0" "$(jq -r '[.steps[] | select(.name == "worker-gate" or .name == "pre-gate-review")] | length' "$(state_file "$p")")" \
    "no pre-gate round and no gate child is started for an item a placed write already ended"
  [ ! -e "$item/worker-gate-report.md" ] || fail "a gate child ran for an item a placed write already ended"
}

# A child has one place beside its change to write (issue #800): the run starts every child in the project's own
# tree, so a write with a project-relative path lands in the tree the landing step needs clean — and "write beside
# the change" was the only path it had. The directory is created before the child starts and named in its prompt;
# what a child writes there is inside the item's own directory, so it is no difference in the project's tree and the
# item keeps the outcome its work earned.
case_run_aChildIsGivenOneScratchDirectory() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/prompts/worker.md tools/loop/prompts/worker-gate.md tools/loop/prompts/worker-tail.md tools/pi-child-events.sh tools/tooling.json
  local d p item scratch prompt
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The child reads its own prompt the way a real child does — the scratch path is in there and nowhere else — so a
  # prompt that names no directory, or names one the run did not create, fails here rather than in a reader's eye.
  cat > "$d/scratch-write.sh" <<'EOF'
case "$1" in worker*) ;; *) exit 0 ;; esac
prompt=$(find "$LOOP_CHILD_ROOT/.pi/issue-loop/items" -name worker-prompt.md | head -1)
scratch=$(sed -n 's/.*scratch directory[^`]*`\([^`]*\)`.*/\1/p' "$prompt" | head -1)
if [ -z "$scratch" ]; then printf 'the prompt names no scratch directory: %s\n' "$prompt" >&2; exit 9; fi
if [ ! -d "$scratch" ]; then printf 'the scratch directory %s does not exist\n' "$scratch" >&2; exit 9; fi
printf 'notes\n' > "$scratch/notes.txt"
EOF
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    "case \"\$1\" in worker*) bash '$d/scratch-write.sh' \"\$1\" ;; esac"
  run_loop "$p" --items 1 --minutes 90 --review on >/dev/null 2>&1 || fail "the run itself failed"
  item=$(find "$p/.pi/issue-loop/items" -mindepth 1 -maxdepth 1 -type d | head -1)
  prompt="$item/worker-prompt.md"
  scratch=$(sed -n 's/.*scratch directory[^`]*`\([^`]*\)`.*/\1/p' "$prompt" | head -1)
  [ -n "$scratch" ] || fail "the worker's prompt names no scratch directory"
  assert_contains "$scratch" "$item" "the scratch directory lies inside the item's own directory"
  assert_eq "notes" "$(cat "$scratch/notes.txt" 2>/dev/null || true)" \
    "the directory existed while the child ran, and what it wrote there is still there"
  assert_eq "done" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "a write into the child's own scratch is no difference in the project's tree"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" "the item closes on its own work"
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

case_run_theWorkersOwnShellWriteIsRecordedAndNotPlaced() {
  # files: tools/loop/fixtures/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p note transcript alive shell_pid
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The narrow rule marks a process only when an ancestor of it worked in the worktree (`pid_left_the_worktree`), so
  # a shell the child forks in the project and never `cd`s out of is found by the walk and placed by nothing — and
  # `run_child` starts every child in the project (`tools/loop/run.sh:1140`), which makes that shell the worker's
  # own. The difference is recorded and the item keeps the outcome its work earned (#704); what this case guards is
  # that it stays so, because the sentence in `guidelines/Backlog.md` §7 that names the shape is not a test of it.
  #
  # The shape is staged by the fixture's `PI_STUB_PROJECT_SHELL` and not by the action: the action is run after a
  # `cd "$LOOP_WORKTREE"` (`tools/loop/fixtures/pi`), a cwd no real child has, so a writer forked from it carries a
  # worktree ancestor and the mark places it. The shell below is forked while the stub still stands in the project,
  # as the child a person starts does, and it stands long enough for the heartbeat's one-second reading to find the
  # difference, then takes it back before the item lands.
  #
  # The shell records its own pid, because the *found* half of the reading is what the case has to pin and
  # `attributed=none` alone cannot: a run whose walk of the child's tree returned nothing prints the same
  # `attributed=none`. The `LOOP-OUTSIDE-WRITE-ALIVE:` field is the reading's other half — the processes that stood
  # in the project at the moment the difference was read — so naming this shell there is what separates "found and
  # placed by nothing" from "never found at all" (issue #704's review, round 1).
  #
  # It stages for the **gate child** only (issue #744): the worker phase is two children now, and both would stage a
  # shell and overwrite the recorded pid, so the pid the case reads would be the tail child's — a process no reading
  # ever saw. The shape under test is one child's own shell, and that child is the one that ran the gate.
  cat > "$d/project-shell.sh" <<'EOF'
case "$1" in worker-tail*) exit 0 ;; esac
printf '%s' "$$" > "${0%/*}/project-shell.pid"
printf 'own\n' >> "$LOOP_CHILD_ROOT/demo-owned.txt"
sleep 8
rm -f "$LOOP_CHILD_ROOT/demo-owned.txt"
EOF
  export PI_STUB_PROJECT_SHELL="$d/project-shell.sh"
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  run_loop "$p" --items 1 --minutes 90 --review off >/dev/null 2>&1 || fail "the run itself failed"
  unset PI_STUB_PROJECT_SHELL
  assert_eq "done" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "a difference the mark cannot place is recorded and does not decide the item"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" "the item closes on its own work"
  note=$(jq -r '[.steps[] | select(.name | startswith("outside-write")) | .note] | join(" | ")' "$(state_file "$p")")
  assert_contains "$note" "demo-owned.txt" "the difference is recorded, with the path that changed"
  transcript=$(find "$p/.pi/issue-loop/items" -name 'worker-report.md.outside-write' | head -1)
  alive=$(grep -m1 '^LOOP-OUTSIDE-WRITE-ALIVE:' "$transcript" || true)
  shell_pid=$(cat "$d/project-shell.pid" 2>/dev/null || true)
  [ -n "$shell_pid" ] || fail "the staged shell recorded no pid, so the found half cannot be read"
  assert_contains "$alive" " $shell_pid " \
    "the reading names the shell that wrote among the processes standing in the project — the shape was found"
  assert_contains "$(cat "$transcript")" "attributed=none" \
    "and places no process of the child's own tree, so the difference does not decide the item"
}

case_run_childWriteOutsideTheWorktreeIsNoted() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p out note
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The measured shape: another process wrote the project's own tree while a child ran, and took it back again
  # before the item landed. The run records the difference twice — the worker's write and the reviewer's
  # removal — and the item keeps the outcome its work earned, because a difference is not a verdict about the
  # child the run started (#274, #276, #285). The tail child writes a **different** path (issue #744): a file the
  # gate child already made dirty is no difference to the run's reading, which compares the set of paths, and the
  # second child's own write would go unrecorded.
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'case "$1" in
       worker-prompt.md) printf "stray\n" >> "$LOOP_CHILD_ROOT/demo-owned.txt" ;;
       worker-tail*) printf "tail\n" >> "$LOOP_CHILD_ROOT/demo-tail.txt" ;;
       reviewer*) rm -f "$LOOP_CHILD_ROOT/demo-owned.txt" "$LOOP_CHILD_ROOT/demo-tail.txt" ;;
     esac'
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1) || fail "the run itself failed"
  assert_eq "done" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "a difference outside the worktree is recorded, not turned into a verdict about the item"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" "the item closes on its own work"
  note=$(jq -r '[.steps[] | select(.name | startswith("outside-write")) | .note] | join(" | ")' "$(state_file "$p")")
  assert_contains "$note" "the project tree changed while this process ran" "the run records each difference it saw"
  assert_contains "$note" "demo-owned.txt" "and names the path that changed, so the note is answerable"
  assert_eq "1" "$(jq -r '[.steps[] | select(.name == "outside-write")] | length' "$(state_file "$p")")" \
    "the worker's own difference is recorded, not only the round that followed it"
  # …and the tail child's own reading under a name of its own: two children are two readings, and the tail writes the
  # project's tree too (its arm is in this case's action), so a run that recorded only the last child would lose the
  # gate child's and one that recorded only the first would lose this one (issue #744).
  assert_eq "1" "$(jq -r '[.steps[] | select(.name == "outside-write-tail")] | length' "$(state_file "$p")")" \
    "and the tail child's own difference is recorded beside it"
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

# The order the two judgements happen in, which is the whole of issue #758: the diff is read **before** the item's one
# gate, so a finding a reader can make from the diff alone costs one pass instead of a gate and a second full pass —
# measured on the loop's archive, 61 % of 227 items' spend (1 581 of 2 591 Mtok) is charged after the first gate and
# 117 child sessions ran the gate three times or more. The case stages the sequence a real item takes: the change
# child stops at the diff, the reviewer refuses what it reads, and the child that runs the gate is handed the findings.
# What it asserts that the older order cannot produce: the reviewer's round is logged before the gate command is
# reached, the findings are in the gate child's prompt, and the item still spends exactly one gate.
case_run_thePreGateReviewRunsBeforeTheGate() {
  # files: tools/loop/fixtures/ tools/loop/loop.conf tools/loop/prompts/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p order extra out state pre_prompt gate_prompt refuse
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The order the work happened in, written by the children themselves: the stub stands in for a child, so a child's
  # own action is where "this round was read" and "the gate was reached" are observable at all. The round that reads
  # before the gate refuses the diff (one blocking finding), and the round after the gate passes it — the shape the
  # split exists for, because the refusal is answered before the item's one gate is spent.
  order="$d/order.log"; : > "$order"
  refuse="$d/pre-gate-fail.txt"
  printf 'LOOP-BLOCKING: 1\nBLOCKING: the tasks the change claims are done name a case the tree does not hold\nVERDICT: FAIL\n' \
    > "$refuse"
  extra=$(cat <<EOF
case "\$1" in
  reviewer-pre-gate-prompt*) printf 'review\n' >> $(printf '%q' "$order"); cp $(printf '%q' "$refuse") "\$PI_STUB_REPORT_REVIEWER" ;;
  reviewer-prompt-*) printf 'VERDICT: PASS\n' > "\$PI_STUB_REPORT_REVIEWER" ;;
  worker-gate-prompt.md) printf 'gate\n' >> $(printf '%q' "$order") ;;
esac
EOF
)
  stub_write "$d" "$STUB_CHANGE_REPORT" 'VERDICT: PASS' "$extra"
  # The tail is the child that writes the item's verdict, and the shape it answers in is the one it always had.
  printf '%s\n' "$STUB_WORKER_REPORT" > "$d/tail-report.txt"
  export PI_STUB_REPORT_TAIL="$d/tail-report.txt"
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1) || fail "the run itself failed: $out"
  state=$(state_file "$p")

  assert_eq "review gate" "$(paste -sd' ' "$order")" \
    "the round is read before the gate child is started"
  # The prompt the change child was handed is what tells it to stop at the diff; the other fragment would have run the
  # gate in that child, and a round after it would have nothing left to read.
  assert_contains "$(cat "$p/.pi/issue-loop/items/21/worker-prompt.md")" "The gate is not yours this item" \
    "the change child is told the gate is deferred"
  pre_prompt="$p/.pi/issue-loop/items/21/reviewer-pre-gate-prompt.md"
  [ -f "$pre_prompt" ] || fail "the run read no diff before the gate: no prompt at $pre_prompt"
  assert_contains "$(cat "$pre_prompt")" "This round is read before the gate" \
    "the reviewer is told it reads the diff before the gate"
  gate_prompt="$p/.pi/issue-loop/items/21/worker-gate-prompt.md"
  [ -f "$gate_prompt" ] || fail "the run started no gate child: no prompt at $gate_prompt"
  assert_contains "$(cat "$gate_prompt")" "BLOCKING: the tasks the change claims are done name a case the tree does not hold" \
    "the round's findings reach the prompt of the child that runs the gate"
  assert_eq "1" "$(grep -c '^prompt: worker-gate-prompt.md$' "$PI_STUB_LOG" || true)" \
    "the item runs exactly one gate child"
  assert_eq "0" "$(grep -c '^prompt: worker-prompt-round2.md$' "$PI_STUB_LOG" || true)" \
    "and the refusal bought no rework pass, which is where a second gate would have come from"
  assert_eq "1" "$(jq -r '[.steps[] | select(.name == "pre-gate-review")] | length' "$state")" \
    "the round before the gate is a step on the item"
  assert_eq "done" "$(jq -r '.items[0].result' "$state")" "the item closes on the same one gate"
  assert_eq "1" "$(jq -r '.items[0].metrics.reviewRounds' "$state")" \
    "the round before the gate is not a round of the review budget"
}

# A round that never answered decided nothing, and the gate has not run yet, so the change is left for the next run
# exactly as a round the run's own clock cuts is left after a gate: recorded, not refused, and with no gate child
# started on an unread diff (issue #758).
case_run_aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun() {
  # files: tools/loop/fixtures/ tools/loop/loop.conf tools/loop/prompts/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p out state
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # An answer that arrives without its closing verdict line, twice: the transport cut it, no reviewer refused it.
  stub_write "$d" "$STUB_CHANGE_REPORT" 'the round was interrupted before it graded anything'
  printf '%s\n' "$STUB_WORKER_REPORT" > "$d/tail-report.txt"
  export PI_STUB_REPORT_TAIL="$d/tail-report.txt"
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1) || fail "the run itself failed: $out"
  state=$(state_file "$p")

  assert_eq "1" "$(grep -c '^prompt: reviewer-pre-gate-prompt-retry.md$' "$PI_STUB_LOG" || true)" \
    "a round cut on its way to the file is asked once more"
  assert_eq "0" "$(grep -c '^prompt: worker-gate-prompt.md$' "$PI_STUB_LOG" || true)" \
    "a diff nobody read is not gated: the gate child is never started"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$state")" "the item is recorded blocked"
  assert_eq "cut-short" "$(jq -r '.items[0].metrics.reviewVerdict' "$state")" \
    "and its record carries the cut, not a refusal no reviewer made"
  assert_contains "$(jq -r '.items[0].note' "$state")" "the pre-gate reviewer's report was cut short twice" \
    "the reason names the round that never answered, and names it as the one before the gate"
  # …and the round after the gate is not asked about a change its gate never ran on: an item whose pre-gate round was
  # cut is left for the next run, not reviewed again on a diff no gate touched.
  assert_eq "0" "$(grep -c '^prompt: reviewer-prompt-1' "$PI_STUB_LOG" || true)" \
    "no review round after the gate was started"
  # The change the change child left is committed before the round reads it, which is what a later run adopts.
  assert_contains "$(git -C "$p" ls-tree -r --name-only "$(git -C "$p" for-each-ref --format='%(refname:short)' 'refs/heads/fix/issue-21-*' | head -1)")" \
    "demo-fix.txt" "the change is on the branch for the next run"
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
# item with its fix on neither (issue #308, from the read-only review of #301, 2026-10-24). The merge verifies the
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
# being nobody's question, which is what let the absorbed merge of issue #308 be recorded as an advance: the run
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
  # The landing's own commit, which is what the pipeline it caused is scored for (issue #709): the record carries the
  # commit the merge made — the base the run advanced to — and this tree's tracker fixture answers no run of it, so
  # the landing is recorded unscored rather than as one that did not redden `main`.
  assert_eq "$(git -C "$p" rev-parse main)" "$(jq -r '.items[0].merged.sha' "$(state_file "$p")")" \
    "the record carries the commit the landing made"
  assert_eq "null" "$(jq -r '.items[0].landedReddenedMain' "$(state_file "$p")")" \
    "and its score is unscored, because no run of that commit is answered for here"
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

# An adoption's refusal is the adoption's own result, not the owner's question (#794). A branch the pre-screen
# excluded is decided by what it carries, and its refusal has two kinds: one about the branch's own evidence —
# no archived change of its own, no green verification, a reviewer's refusal — which is the owner's, and one
# about the merge's inputs — the tree it lands in, the base it replays onto, the replay itself — which is the
# loop's, because the next run finds the first clean once the dirt is gone. `adopt_finished_exclusions` printed
# both and discarded both (`adopt_finished_attempt "$issue" "$branch" >&2 || true`), so the tracker kept only
# the pre-screen's reading of the same branch and the item was escalated. Measured 2026-11-06 on #760: excluded
# at 06:28Z for a branch `branch-state` read `adoptable` the whole time, whose adoption had failed on a project
# tree that was not clean — 12 h after the owner had answered the item's only real question, and one of the ten
# decision slots of that run.
case_run_anAdoptionTheTreeRefusesIsRecordedAsUnmergeable() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/state.sh tools/loop/report.sh tools/pi-child-events.sh tools/tooling.json
  local d p out status branch base log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  # a previous attempt that finished: one archived change of its own, the red-on-HEAD XML beside the green gate
  # XML — every input `adopt-item` decides on, so only the tree can refuse this branch
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
  # the tree the merge lands in carries work nobody committed: the cheapest refusal `adopt-item` has
  printf 'work nobody committed\n' > "$p/uncommitted.txt"
  export GH_STUB_RECORD="$p/gh.log"; : > "$GH_STUB_RECORD"

  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1); status=$?
  assert_eq "0" "$status" "an adoption the tree refuses is an explicit non-error"
  assert_contains "$out" "not clean" "the refusal is printed and names the tree the merge would land in"
  assert_eq "$base" "$(git -C "$p" rev-parse main)" "the base did not move"
  [ ! -f "$p/openspec/changes/archive/2026-01-01-fix-adopted/proposal.md" ] ||
    fail "the change was merged although the tree was not clean"
  [ -n "$(git -C "$p" branch --list "$branch")" ] || fail "the branch the adoption refused was removed"
  # the item's record names the adoption's own refusal, beside the pre-screen's reading of the same branch
  assert_eq "unmergeable" "$(jq -r '.excluded[] | select(.issue == 21) | .mechanism' "$(state_file "$p")")" \
    "the refusal is recorded as the item's mechanism"
  assert_contains "$(jq -r '.excluded[] | select(.issue == 21) | .adoptionRefusal' "$(state_file "$p")")" "not clean" \
    "and the record quotes what refused the merge, not the pre-screen's reason"
  assert_eq "$branch" "$(jq -r '.excluded[] | select(.issue == 21) | .evidence' "$(state_file "$p")")" \
    "the branch stays the evidence"
  # …and the item is work, not a question: no block, no decision slot, no close
  assert_eq "0" "$(jq -r '.items | length' "$(state_file "$p")")" "nothing is recorded as worked"
  log=$(cat "$GH_STUB_RECORD")
  assert_eq "0" "$(printf '%s\n' "$log" | grep -c 'agent/needs-decision' || true)" \
    "no decision label is written for an adoption the tree refused"
  assert_eq "0" "$(printf '%s\n' "$log" | grep -c 'issue close' || true)" "and nothing is closed"
  assert_contains "$out" "decisions the owner owes: none" "the report names no decision the owner owes"
  assert_contains "$out" "excluded before any work: #21 needs-decision (unmergeable)" \
    "while the exclusion it printed names the adoption's mechanism"
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
  assert_contains "$(jq -r '.run.stop.reason // ""' "$(state_file "$p")")" "the kept worktree is not clean" \
    "and the refusal is the run's own stop reason, so the report names it instead of a killed process"
  assert_contains "$(jq -r '.run.stop.reason // ""' "$(state_file "$p")")" "leftover.txt" \
    "and the stop reason names the files the refusal printed"
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

# A kept worktree whose only dirt is the module on another pin is not work: nothing inside the module was written,
# and placing the pin the base records loses nothing. The run repairs it and goes on where it used to refuse — and
# `--reset-worktree`, the only repair the refusal named, ran the same update that had already failed (measured 2026-10-28).
case_run_placesTheModuleAndGoesOnWhenTheOnlyDirtIsThePin() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/worktree.sh tools/loop/state.sh tools/loop/lib.sh tools/pi-child-events.sh tools/tooling.json
  local d p wt out status
  d=$(scratch); p=$(new_project); wt="$p/.pi/worktrees/loop"
  submodule_fixture "$p" "$d"
  export GIT_ALLOW_PROTOCOL=file
  run_one_item "$p" "$d" >/dev/null || fail "the first run failed"
  # The module left on the base's other pin: the dirt neither `reset --hard` nor `clean -fd` reaches (issue #310),
  # and the state `create` itself used to leave on a bumped base when the placement could not fetch the pin.
  submodule_in_worktree "$wt" "$SUB_FIRST"
  assert_eq " M app/src/main/cpp/libosmscout" "$(git -C "$wt" status --porcelain)" \
    "the fixture leaves the module's pin as the tree's only dirt"
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario none)
  out=$(run_loop "$p" --items 1 --minutes 90 2>&1); status=$?
  assert_eq "0" "$status" "the run is not refused on dirt that is no item's work: $out"
  assert_contains "$out" "only the module on another pin" "the repair says what it found"
  assert_contains "$out" "nothing eligible" "and the run then answers to the backlog, as a clean start does"
  assert_eq "" "$(git -C "$wt" status --porcelain)" "the tree is clean after the repair"
  assert_eq "$SUB_SECOND" "$(git -C "$wt/app/src/main/cpp/libosmscout" rev-parse HEAD)" \
    "and the module is on the pin the base records"
  return 0
}

# A pin no clone this host can read is refused before the pre-screen is paid for, not after the queue is built: the
# run used to spend its whole start-up and then stop under no item, naming no pin and no repair (the second half of
# the measurement above).
case_run_aPinNoCloneHoldsIsRefusedBeforeThePrescreen() {
  # files: tools/loop/run.sh tools/loop/state.sh tools/loop/worktree.sh tools/loop/lib.sh tools/pi-child-events.sh tools/loop/fixtures/scenarios/ tools/tooling.json
  local d p out status bogus
  d=$(scratch); p=$(new_project)
  submodule_fixture "$p" "$d"
  export GIT_ALLOW_PROTOCOL=file
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  bogus=1234567890123456789012345678901234567890
  git -C "$p" update-index --cacheinfo 160000,"$bogus",app/src/main/cpp/libosmscout >/dev/null 2>&1 ||
    fail "could not record the pin"
  git -C "$p" commit -qm 'a pin no clone holds' >/dev/null 2>&1 || fail "could not commit the pin"
  : > "${PI_STUB_LOG:-/dev/null}"
  out=$(run_loop "$p" --items 1 --minutes 90 2>&1); status=$?
  assert_eq "5" "$status" "a base pinning a commit no clone holds refuses the run"
  assert_not_contains "$out" "pre-screen:" "the refusal is reached before the pre-screen is paid for"
  assert_contains "$out" "1234567890" "the refusal names the pin"
  assert_contains "$out" "push origin" "and the repair that publishes it"
  assert_contains "$(jq -r '.run.stop.reason // ""' "$(state_file "$p")")" "submodule pin" \
    "the stop reason records it"
  assert_eq "" "$(cat "${PI_STUB_LOG:-/dev/null}" 2>/dev/null || true)" "no agent process was started"
  return 0
}

# A fixture project whose configured submodule path is a real module of two commits, the second of which the
# superproject records (issue #310). The module's checkout can then be left on `$SUB_FIRST`, which is the dirt neither
# `reset --hard` nor `clean -fd` reaches. Its URL is a local path, so the checkout the engine's fresh-worktree step
# makes is best-effort and git refuses it (`transport 'file' not allowed`) — a case that needs the module places it
# itself, with `submodule_in_worktree`.
submodule_fixture() { # <project> <scratch dir> → $SUB_FIRST, $SUB_SECOND
  local p="$1" d="$2" sub
  sub="$d/sub"
  mkdir -p "$sub"
  printf 'the submodule\n' > "$sub/CMakeLists.txt"
  git -C "$sub" init -q -b main >/dev/null 2>&1 || fail "could not create the throwaway submodule repository"
  git -C "$sub" config user.email selftest@example.invalid
  git -C "$sub" config user.name selftest
  git -C "$sub" add -A >/dev/null 2>&1
  git -C "$sub" commit -qm one >/dev/null 2>&1 || fail "could not commit in the throwaway submodule repository"
  SUB_FIRST=$(git -C "$sub" rev-parse HEAD)
  printf 'the submodule, a second commit\n' >> "$sub/CMakeLists.txt"
  git -C "$sub" commit -qam two >/dev/null 2>&1 || fail "could not commit the second time in the submodule"
  SUB_SECOND=$(git -C "$sub" rev-parse HEAD)
  rm -rf "$p/app/src/main/cpp/libosmscout"
  git -C "$p" rm -q -r --cached app/src/main/cpp/libosmscout
  git -C "$p" -c protocol.file.allow=always submodule add "$sub" app/src/main/cpp/libosmscout >/dev/null 2>&1 ||
    fail "could not add the submodule to the fixture project"
  git -C "$p" commit -qm 'the submodule the loop configuration names' >/dev/null 2>&1 ||
    fail "could not commit the submodule in the fixture project"
}

# That project's module, checked out inside a worktree of it and left on <commit>.
submodule_in_worktree() { # <worktree> <commit>
  git -C "$1" -c protocol.file.allow=always submodule update --init app/src/main/cpp/libosmscout >/dev/null 2>&1 ||
    fail "could not check the submodule out in the kept worktree"
  git -C "$1/app/src/main/cpp/libosmscout" checkout -q "$2"
}

# The repair has to repair the dirt its own refusal names, and a submodule left on a commit the superproject does not
# record is dirt neither of its two verbs reaches: the index inside the module already matches its `HEAD`, so
# `reset --hard` has nothing to undo, and the module's path is tracked, so `clean -fd` leaves it alone. Measured
# 2026-10-25 in the kept worktree at #309: `git reset -q --hard && git clean -qfd` still printed the module path as
# modified, while `git submodule update --init --checkout <path>` made the status empty and left the module on the
# pin the base records (issue #310).
case_run_resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p out status wt
  d=$(scratch); p=$(new_project)
  submodule_fixture "$p" "$d"
  run_one_item "$p" "$d" >/dev/null || fail "the first run failed"
  wt="$p/.pi/worktrees/loop"
  submodule_in_worktree "$wt" "$SUB_FIRST"
  [ -n "$(git -C "$wt" status --porcelain)" ] || fail "the fixture did not leave the submodule on another commit"
  : > "$GH_STUB_RECORD"; : > "$PI_STUB_LOG"
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario none)
  out=$(run_loop "$p" --items 1 --minutes 90 --reset-worktree 2>&1); status=$?
  assert_contains "$out" "app/src/main/cpp/libosmscout" "the repair names the submodule it found dirty"
  assert_eq "" "$(git -C "$wt" status --porcelain)" "the repair leaves a tree the next start accepts"
  assert_eq "$SUB_SECOND" "$(git -C "$wt/app/src/main/cpp/libosmscout" rev-parse HEAD)" \
    "on the commit the superproject records"
  assert_eq "0" "$status" "so the run starts instead of refusing the same tree again"
  assert_contains "$out" "nothing eligible" "and it then answers to the backlog, not to the submodule"
}

# The repair is a claim about a tree, and a claim it cannot make is withdrawn in the run's own words: a module that
# carries a file of its own is dirt `reset --hard`, `clean -fd` and the checkout all leave in place, so the run names it
# and refuses to start rather than printing the "clean again" line the next start would contradict (issue #310).
case_run_resetWorktreeNamesTheDirtItCannotDiscard() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p out status wt
  d=$(scratch); p=$(new_project)
  submodule_fixture "$p" "$d"
  run_one_item "$p" "$d" >/dev/null || fail "the first run failed"
  wt="$p/.pi/worktrees/loop"
  submodule_in_worktree "$wt" "$SUB_SECOND"
  # A file the module itself carries: the superproject reports the module as modified, and none of the repair's verbs
  # removes it.
  printf 'a build artefact inside the module\n' > "$wt/app/src/main/cpp/libosmscout/leftover.txt"
  [ -n "$(git -C "$wt" status --porcelain)" ] || fail "the fixture did not leave the module dirty"
  : > "$GH_STUB_RECORD"; : > "$PI_STUB_LOG"
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario none)
  out=$(run_loop "$p" --items 1 --minutes 90 --reset-worktree 2>&1); status=$?
  assert_eq "5" "$status" "the repair refuses a tree it cannot make clean"
  assert_contains "$out" "cannot discard" "and says so in its own words"
  assert_contains "$out" "app/src/main/cpp/libosmscout" "naming what is left"
  assert_not_contains "$out" "is clean again" "never announcing a clean tree"
  assert_not_contains "$out" "pre-screen:" "and nothing is started"
  assert_contains "$(jq -r '.run.stop.reason // ""' "$(state_file "$p")")" "still not clean after --reset-worktree" \
    "the refusal is the run's stop reason, not only a line on the console"
  assert_contains "$(jq -r '.run.stop.reason // ""' "$(state_file "$p")")" "app/src/main/cpp/libosmscout" \
    "and the reason names the dirt the refusal printed, so the record outlives the console"
  [ -f "$wt/app/src/main/cpp/libosmscout/leftover.txt" ] || fail "the refusal discarded what it said it could not"
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

# The other half of the same record: a start refused because the tree is not clean ends the run, and the leftovers
# it found — the untracked file a refused `end-item` left, in this case — were named in the stop reason and written
# nowhere (issue #308). They are written into the evidence directory of the item whose branch marker names them,
# before the run ends, so `--reset-worktree` is no longer the only repair a person has.
case_run_aRefusedStartKeepsTheLeftoversItFound() {
  # files: tools/loop/run.sh tools/loop/state.sh tools/loop/worktree.sh tools/loop/lib.sh tools/pi-child-events.sh tools/loop/fixtures/scenarios/ tools/tooling.json
  local d p out reason f patch
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario two)
  # The same shape as `run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds`: the stub installs a `pre-commit` hook that
  # refuses both of `end-item`'s commits, so #21 ends with its work still in the tree.
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' \
    'h=$(git rev-parse --git-path hooks); mkdir -p "$h"; printf "#!/bin/sh\nexit 1\n" > "$h/pre-commit"; chmod +x "$h/pre-commit"'
  out=$(run_loop "$p" --items 2 --minutes 90 2>&1)
  f=$(state_file "$p")
  reason=$(jq -r '.run.stop.reason // ""' "$f")
  assert_contains "$reason" "the worktree is not clean" "the stop reason says what the refusal was: $out"
  patch="$p/.pi/issue-loop/items/21/leftover-from-killed-run.patch"
  [ -f "$patch" ] || fail "the leftovers of a refused start were not kept as a patch (reason: $reason)"
  assert_contains "$(cat "$patch")" "demo-fix.txt" "the patch carries the untracked file the tree held"
  assert_contains "$reason" "leftover-from-killed-run.patch" "and the stop reason names the patch it wrote"
  assert_contains "$(jq -r '.items[0].note' "$f")" "leftovers" "the item whose work it is still carries its own account"
  return 0
}

# The dirt a run ended *itself* is not the dirt the mid-run refusal protects (issue #664). `keep_leftovers` writes
# what a cap-ended child left to the item's patch, the item is recorded blocked, and the tree stayed dirty — so the
# run's next start met the leftovers of the item the run had just ended and stopped the whole run on them, with the
# work it had just recorded beside them. Measured 2026-10-26: #310 was ended at its cap at 08:08:45Z, its patch was
# written at 08:08:38Z, the start of #309 was refused at 08:09:04Z, and the run stopped having spent 166 of its 400
# minutes — 234 minutes and the one queued item unspent. The branch marker names the item, the patch is the record,
# so nothing the refusal protects is in the tree any more: the run discards it and starts its next item.
case_run_theStartAfterTheRunsOwnEndedChildIsNotRefused() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/worktree.sh tools/loop/state.sh tools/loop/lib.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf out reason f tree patch
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario two)
  conf="$d/loop.conf"
  conf_with_child_cap 2 "$conf"
  grep -qx 'LOOP_CHILD_MAX_SECONDS=2' "$conf" || fail "this case could not shorten the child cap in loop.conf"
  # The first item's child writes, then outlasts the 2 s cap, so the run ends it and its work is what the tree
  # carries when the second item's start is refused. The second item's child works and ends by itself, which is
  # the start the run lost.
  stub_per_item_worker "$d" 'VERDICT: PASS' 'VERDICT: PASS' '
    if [ "$n" -eq 1 ]; then
      printf "half-applied\n" > half-applied.txt
      sleep 30
    else
      printf "the second item works\n" > second-item.txt
    fi'
  tree="$p/.pi/worktrees/loop"
  export LOOP_CONF="$conf"
  out=$(run_loop "$p" --items 2 --minutes 90 --review off 2>&1)
  f=$(state_file "$p")
  reason=$(jq -r '.run.stop.reason // ""' "$f")
  patch="$p/.pi/issue-loop/items/21/leftover-from-killed-run.patch"
  [ -f "$patch" ] || fail "the work the cap-ended child left was not kept as a patch"
  assert_contains "$(cat "$patch")" "half-applied.txt" "the patch still carries the dirt the run discarded"
  assert_eq "2" "$(grep -c '^prompt: worker-prompt.md$' "$PI_STUB_LOG" || true)" \
    "the item after the cap-ended one starts, so one item's cap no longer ends its run: $out"
  assert_contains "$out" "the work #21 left" "the run says which item's work it discards, and where the record is"
  [ ! -f "$tree/half-applied.txt" ] || fail "the recorded dirt was not discarded, so the next start is refused"
  assert_eq "" "$(git -C "$tree" status --porcelain)" "and the run leaves a tree the next start accepts"
  assert_not_contains "$reason" "the worktree is not clean" "the stop is a count of items, not the tree: $reason"
  assert_contains "$reason" "item cap reached (2/2)" "and both items were worked: $reason"
}

# The retry a discard allows is a start of its own, so it can be refused for something else entirely — and that
# refusal is the one a reader needs. Found in this change's first review round (#664, round 2): `start-item`'s line
# was printed and then dropped, so a retry refused for its branch was reported as "the worktree is not clean — the
# leftovers are #21's … : " with an empty list of files, on a tree the run had just cleaned itself, and the item's
# record said the same. The reason recorded is the retry's, and the record the discard relied on is not replaced.
case_run_aRetriedStartStopsWithItsOwnReason() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/worktree.sh tools/loop/state.sh tools/loop/lib.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf out reason tree patch
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario two)
  conf="$d/loop.conf"
  conf_with_child_cap 2 "$conf"
  grep -qx 'LOOP_CHILD_MAX_SECONDS=2' "$conf" || fail "this case could not shorten the child cap in loop.conf"
  # The first child writes dirt, then a finished-looking attempt of the *second* item on that item's own branch, and
  # then outlasts the cap. The second item's first start is refused for the dirt; the discard clears it; the retry is
  # refused for the branch the first child made — a reason the dirt arm must not speak for.
  stub_per_item_worker "$d" 'VERDICT: PASS' 'VERDICT: PASS' "
    if [ \"\$n\" -eq 1 ]; then
      printf 'half-applied\n' > half-applied.txt
      current=\$(git rev-parse --abbrev-ref HEAD)
      git switch -q -c fix/issue-22-a-bug
      mkdir -p openspec/changes/archive/2026-01-01-fix-a-bug
      printf 'archived\n' > openspec/changes/archive/2026-01-01-fix-a-bug/proposal.md
      git add openspec/changes/archive/2026-01-01-fix-a-bug
      git -c user.name=case -c user.email=case@example.invalid commit -q -m 'fix: a bug (#22)'
      git switch -q \"\$current\"
      sleep 30
    fi"
  tree="$p/.pi/worktrees/loop"
  export LOOP_CONF="$conf"
  out=$(run_loop "$p" --items 2 --minutes 90 --review off 2>&1)
  reason=$(jq -r '.run.stop.reason // ""' "$(state_file "$p")")
  patch="$p/.pi/issue-loop/items/21/leftover-from-killed-run.patch"
  [ -f "$patch" ] || fail "the work the cap-ended child left was not kept as a patch"
  assert_contains "$(cat "$patch")" "half-applied.txt" \
    "the record the discard relied on is still the one the cap left, not a header over a tree that was discarded"
  assert_contains "$reason" "carries commits the base does not" \
    "the stop reason is the refusal the retry answered with: $reason"
  assert_not_contains "$reason" "the worktree is not clean" \
    "and not the dirt it was retried over, which the run itself removed: $reason"
  assert_not_contains "$reason" "the leftovers are #21's" "so it does not speak for another item's tree either: $reason"
  assert_eq "1" "$(grep -c '^prompt: worker-prompt.md$' "$PI_STUB_LOG" || true)" \
    "the second item never started, so its refusal is not recorded as work: $out"
  assert_eq "" "$(git -C "$tree" status --porcelain)" "the tree the run discarded is the one it leaves behind"
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

# A `stale` skip is the one verdict that says nothing is left to do, and it carries no measurement and no review:
# the run parked it as a bare hold, and the pre-screen then reports the item as held — neither work nor a
# decision — for good (issue #682, measured on #147, 2026-10-27T17:11:06Z). A stale claim leaves one two-way
# choice, so the item must carry it and the decision label that makes it visible to the queue.
case_run_staleSkipAsksItsQuestion() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/labels.sh tools/pi-child-events.sh tools/tooling.json
  local d p out log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$(printf 'LOOP-VERDICT: skipped\nLOOP-SKIP-VERDICT: stale\nLOOP-ROOT-CAUSE: the cited guard is already in the tree\nLOOP-RED-TEST: none — the claim no longer holds on HEAD')" 'VERDICT: PASS'
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1) || fail "the run failed: $out"
  log=$(cat "$d/gh.log")
  assert_eq "skipped" "$(jq -r '.items[0].result' "$(state_file "$p")")" "the item is still a skip"
  assert_contains "$log" "--add-label agent/needs-decision" \
    "a stale skip leaves a decision, not a bare hold nobody can answer"
  assert_contains "$log" "status/on-hold" "with the state that holds it"
  assert_eq "1" "$(grep -c 'issue comment' "$d/gh.log" || true)" "exactly one comment carries the reason and the question"
  assert_contains "$(grep 'issue comment' "$d/gh.log" || true)" "the cited guard is already in the tree" \
    "the comment names the worker's own root cause"
  assert_contains "$(grep 'issue comment' "$d/gh.log" || true)" "**The decision:**" \
    "and states the choice a person answers"
  assert_not_contains "$log" "issue close" "nothing is closed: a stale skip carries no measurement to close on"
}

# The worker's own question is the one the item carries: a stale skip that states its fork must not get the run's
# fallback sentence beside it, exactly as a block with a reviewer decision does not (issues #194, #225).
case_run_staleSkipKeepsTheWorkersQuestion() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p out
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_write "$d" "$(printf 'LOOP-VERDICT: skipped\nLOOP-SKIP-VERDICT: stale\nLOOP-ROOT-CAUSE: the cited guard is already in the tree\nLOOP-RED-TEST: none — the claim no longer holds on HEAD\nLOOP-DECISION: retire #21, or re-diagnose it against the newer guard | recommended: retire it')" 'VERDICT: PASS'
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1) || fail "the run failed: $out"
  assert_contains "$(cat "$d/gh.log")" "recommended: retire it" "the comment carries the worker's question"
  assert_not_contains "$(cat "$d/gh.log")" "retire the item as stale, or re-diagnose it —" \
    "and not the run's own question beside it"
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
  wait_for_stub_child "$fifo.ready" || true
  if [ ! -e "$fifo.ready" ]; then
    kill -9 "$pid" 2>/dev/null
    fail "the stub child never started, so there is no item in flight to read the state about"
  fi
  # The case holds the fifo's writer side, so releasing a child never blocks and never leaves a writer behind.
  exec 8<> "$fifo"
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
  assert_eq "6" "$(grep -c '^invocation' "$PI_STUB_LOG")" "two workers, their two tails and two reviewers"
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
  # …and the round-2 worker the refusal bought (item #800's round-1 review): the prompt is rendered from
  # `prompts/worker.md` a second time, at a site of its own, and `loop_render` substitutes only the pairs it is
  # handed — so a pair that site forgets is a literal `{{SCRATCH_DIR}}` handed to a child that was told to write
  # there rather than in the change root.
  rework="$p/.pi/issue-loop/items/21/worker-prompt-round2.md"
  [ -f "$rework" ] || fail "the rework worker was started with no prompt"
  assert_not_contains "$(cat "$rework")" "{{" "every placeholder in the round-2 worker's prompt is substituted"
  assert_contains "$(cat "$rework")" "items/21/scratch" \
    "and the scratch directory it may write in is the path the run created, not a literal"
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
  assert_eq "3" "$(grep -c '^invocation' "$PI_STUB_LOG")" "exactly one worker phase (two children) and one reviewer process"
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
  assert_eq "6" "$(grep -c '^invocation' "$PI_STUB_LOG")" "two workers, their two tails and two reviewers"
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
  assert_eq "4" "$(grep -c '^invocation' "$PI_STUB_LOG")" "the worker's two children, the cut reviewer and its one retry"
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
  assert_eq "4" "$(grep -c '^invocation' "$PI_STUB_LOG")" "the worker's two children, one reviewer and its one retry"
  [ ! -f "$p/.pi/issue-loop/items/21/worker-prompt-round2.md" ] ||
    fail "a second worker round was taken for an answer that never arrived"
  [ -z "$(find "$p/.pi/worktrees/loop/openspec/changes" -name 'review-failed-round-*.md' 2>/dev/null)" ] ||
    fail "a review-failure marker was written for a round no reviewer decided"
}

# A closing block a reviewer writes as inline code is a complete answer (#793). Measured 2026-11-06 on item #788:
# both of that item's pre-gate reports ended `` `LOOP-BLOCKING: 3` `` then `` `VERDICT: FAIL` ``, each with its own
# closing backtick and the adapter's newline, and the run read both as cut answers — it recorded the item `blocked`
# and escalated "the round's answer never arrived, so no reviewer refused this change" while the report in hand
# carried three blocking findings. So the closing line is read where it stands, fence or no fence: the answer is
# complete, and the count inside the fence is the count the round is decided by.
case_run_aFencedClosingBlockIsACompleteAnswer() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p f out log
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # A refusal that blocks nothing, written the way item #788's round wrote it: the closing block in inline code.
  stub_write "$d" "$STUB_WORKER_REPORT" 'BLOCKING: none — every task the change claims done names the case that holds it
NOTE: the design cites the guideline section by paragraph rather than by its heading
`LOOP-BLOCKING: 0`
`VERDICT: FAIL`'
  out=$(run_loop "$p" --items 1 --minutes 90 --review on 2>&1) || fail "the run itself failed: $out"
  f=$(state_file "$p")
  assert_not_contains "$out" "answer was cut short" "a fenced closing block is not an answer the transport cut"
  [ ! -f "$p/.pi/issue-loop/items/21/reviewer-prompt-1-retry.md" ] ||
    fail "the round was asked again although its answer arrived whole"
  assert_eq "1" "$(jq -r '.items[0].metrics.reviewRounds' "$f")" "the round is read once, as the round it is"
  assert_eq "done" "$(jq -r '.items[0].result' "$f")" "the count inside the fence is the count that decides it"
  assert_eq "closed" "$(jq -r '.items[0].verdict' "$f")" "so a fenced refusal with a zero count closes the item"
  assert_contains "$(jq -r '.items[0].note' "$f")" "cites the guideline section by paragraph" \
    "and the note beside the fenced block reaches the item"
  assert_eq "3" "$(grep -c '^invocation' "$PI_STUB_LOG")" \
    "the worker's two children and one reviewer, and no retry child"
  log=$(cat "$GH_STUB_RECORD")
  assert_not_contains "$log" "agent/needs-decision" "no decision is asked for about work that passed"
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/prompts/worker.md tools/loop/prompts/worker-tail.md tools/loop/report.sh tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
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
  # can only close if the adapter read the last assistant message out of `message_end` and printed it. The report is
  # the **gate** report — a gate line and no verdict, which is the shape `tools/loop/prompts/worker.md` now defines for
  # the child that ran the gate (issue #744, review round 1: a fixture whose gate child claimed `closed` never
  # exercised the arm that starts the tail for a real item).
  jq -cn --arg text "$STUB_GATE_REPORT" \
    '{type:"message_end",message:{role:"assistant",content:[{type:"text",text:$text}]}}' >> "$d/events.jsonl"
  # …and the gate's own console, which lives in the child's event stream and not in its answer: that is the file
  # `tail_after_gate` cuts the tail's `{{GATE_OUTPUT}}` from.
  jq -cn '{type:"tool_execution_end",toolCallId:"c5",toolName:"ctx_shell",
           result:{content:[{type:"text",text:"the gate console: the demo case FAILED before the fix"}]}}' \
    >> "$d/events.jsonl"
  # The **tail** child of the same item (issue #744): the gate ends the first child, and this one is a second session,
  # so its stream is its own and starts at the project's baseline instead of carrying the first child's context. Every
  # number below is a sum of the two, or the tail's own — nothing here is the first child's, which is the point.
  cat > "$d/tail-events.jsonl" <<'JSONL'
{"type":"turn_start"}
{"type":"tool_execution_end","toolCallId":"c4","toolName":"read","durationMs":20}
{"type":"message_update","usage":{"input":17056,"output":50,"cacheRead":0,"cacheWrite":0,"totalTokens":17106,"cost":{"input":0.17,"output":0.001,"cacheRead":0,"cacheWrite":0,"total":0.171}},"assistantMessageEvent":{"type":"text_delta","contentIndex":0,"delta":"c"}}
{"type":"turn_end","message":{"role":"assistant","content":[]},"toolResults":[]}
JSONL
  # The tail's verdict is the item's verdict, so the run reads the archive tail's own answer: the archive the first
  # child left is what this child verifies, and `closed` here is a claim it can make because the tree already holds it.
  # The gate line differs from the first child's on purpose, so the item's recorded gate proves whose report the run
  # read (issue #744, review round 1).
  jq -cn --arg text 'LOOP-VERDICT: closed
LOOP-CHANGE: fix-demo
LOOP-ROOT-CAUSE: the fixture has exactly one cause
LOOP-RED-TEST: Demo#case fails on HEAD — XML tests=1 failures=1 (2026-01-01T00:00:00Z)
LOOP-GATE: tests=1 failures=0 from the first child' \
    '{type:"message_end",message:{role:"assistant",content:[{type:"text",text:$text}]}}' >> "$d/tail-events.jsonl"
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  stub_success "$d"
  export PI_STUB_EVENTS="$d/events.jsonl" PI_STUB_EVENTS_TAIL="$d/tail-events.jsonl" PI_STUB_REPORT_WORKER=""
  # Two children, so the figures below are the fixture's own and the split's (issue #744): the gate child's stream
  # and the tail child's, summed as one item's spend.
  run_loop "$p" --items 1 --minutes 90 --review off >/dev/null || fail "the run failed"
  f=$(state_file "$p")
  assert_eq "done" "$(jq -r '.items[0].result' "$f")" \
    "the report the adapter printed out of the stream's last assistant message is the one the run read"
  # What the split *is*: the child that ran the gate ends, and a second child — a prompt of its own — is started for
  # the evidence and the archive. A run that kept one child cannot pass this pair.
  assert_eq "2" "$(grep -c '^invocation' "$PI_STUB_LOG" || true)" "the worker phase is two children, not one"
  assert_eq "worker-prompt.md worker-tail-prompt.md" \
    "$(grep '^prompt: ' "$PI_STUB_LOG" | sed -e 's/^prompt: //' | paste -sd' ' -)" \
    "in that order: the gate child first, then the tail child that is started from its gate line"
  # …and the tail is handed what the split exists to hand it, and nothing it would have to re-derive: the gate line
  # itself, the gate's own output, the change, and the facts the first child established.
  tail_prompt="$p/.pi/issue-loop/items/21/worker-tail-prompt.md"
  [ -f "$tail_prompt" ] || fail "the tail child was started with no prompt"
  assert_contains "$(cat "$tail_prompt")" "LOOP-GATE: tests=1 failures=0 (fixture)" \
    "the tail is handed the gate line the first child reported"
  assert_contains "$(cat "$tail_prompt")" "fix-demo" "and the change it is to archive"
  # …and the extract the prompt names is derived from that same transcript, so the path in the prompt is a file the
  # run wrote rather than one the tail has to go and find.
  assert_contains "$(cat "$tail_prompt")" "worker-tail-report-gate-output.txt" \
    "the prompt names the extract by the path the run wrote it to"
  assert_contains "$(cat "$tail_prompt")" "the fixture has exactly one cause" \
    "and the root cause it names, so the tail re-derives none of it"
  assert_not_contains "$(cat "$tail_prompt")" "{{" "every placeholder in it is substituted"
  gate_output="$p/.pi/issue-loop/items/21/worker-tail-report-gate-output.txt"
  [ -f "$gate_output" ] || fail "no extract of the gate's own output was written for the tail"
  # The extract is the gate child's **tool output**, which is where a gate prints — not its answer, which is the
  # report block. Cut from the wrong file it would carry a `LOOP-GATE:` line and nothing of the gate (issue #744,
  # review round 1: the first version of this case asserted exactly that and could not tell the two apart).
  assert_contains "$(cat "$gate_output")" "the gate console: the demo case FAILED before the fix" \
    "the extract carries what the gate itself printed"
  assert_contains "$(cat "$gate_output")" "LOOP-GATE: tests=1 failures=0 (fixture)" \
    "and the child's closing answer after it, so the gate line is in the file the prompt names"
  assert_eq "2" "$(jq -r '.items[0].metrics.tools.bash' "$f")" "the tool counts are recorded by name"
  assert_eq "2" "$(jq -r '.items[0].metrics.tools.read' "$f")" \
    "every tool the child used has its own count, both children of the item included"
  # The shape the item spent its budget in (issue #757): the calls and the turns are counted, not derived, and
  # both are summed over the item's children exactly as their tokens are.
  assert_eq "5" "$(jq -r '.items[0].metrics.calls' "$f")" \
    "the item's tool calls are counted over both children"
  assert_eq "2" "$(jq -r '.items[0].metrics.turns' "$f")" \
    "beside the turns the ratio is read against"
  # `usage` is the latest cumulative figure of one response, so a turn's spend is its *last* update: summing the
  # two updates of one turn would report 3000 input tokens for a 2000-token response. Across two children the sum
  # is the items's: the gate child's last turn plus the tail child's only turn.
  assert_eq "19056" "$(jq -r '.items[0].metrics.tokens.input' "$f")" \
    "tokens are summed per turn and per child, not per update: 2000 + the tail's 17056"
  assert_eq "250" "$(jq -r '.items[0].metrics.tokens.output' "$f")" "the output tokens of both children"
  assert_eq "1000" "$(jq -r '.items[0].metrics.tokens.cacheRead' "$f")" "and the cache read"
  assert_eq "0.196" "$(jq -r '.items[0].metrics.cost' "$f")" "the cost of the turns"
  # The tail is a **fresh** session: its first turn is charged the project's baseline, not the context the gate child
  # had reached. The item's largest context is a `max` over both children — the gate child's compaction here — so a
  # tail that inherited 150 000 would report it as a turn of its own and raise this figure above the compaction.
  assert_eq "150000" "$(jq -r '.items[0].metrics.context' "$f")" \
    "the largest context is the gate child's compaction, taken as a max over both children and not summed"
  # …and the item's own record names the gate line of the **tail's** report, not the gate child's: that is what
  # proves the run read the second child's answer (issue #744, review round 1 — a run that kept reading
  # `worker-report.md` passed every other assertion in this case).
  assert_contains "$(jq -r '.items[0].metrics.gate' "$f")" "from the first child" \
    "the gate the item records was reported by the tail child"
  row=$(run_report "$p" | grep '^| #21 |' || true)
  [ -n "$row" ] || fail "no table row for #21"
  assert_contains "$row" "bash=2 ctx_shell=1 read=2" "the row names the tools and their counts"
  assert_contains "$row" "in=19056 out=250 cache=1000" "the tokens the item spent"
  assert_contains "$row" "cost=\$0.196" "and what they cost"
  assert_contains "$row" "| 150000 |" "and the largest context a child reached"

  # An item's spend is the spend of *its* children: the worker's two children and the reviewer append to one file, so
  # the row counts all three rather than the last one to finish.
  local p2 d2
  unset PI_STUB_EVENTS_TAIL
  p2=$(new_project); d2=$(scratch)
  run_one_item "$p2" "$d2" >/dev/null || fail "the reviewed run failed"
  assert_eq "6" "$(jq -r '.items[0].metrics.tools.bash' "$(state_file "$p2")")" \
    "every child of the item is counted, in one number: the gate child, the tail child and the reviewer"
}

# A gate that did not pass is the **tail's** verdict, not the item's own child's (issue #744): the child that ran
# the gate ends at its line, and the tail is the process that reads the result and decides. The outcome block has an
# arm for it, and without that arm the item was recorded with the opposite of what the tail said — "reported success
# but no archived change appears in the run's diff" — dropping the gate's own numbers from the reason a person reads.
case_run_aRedGateIsBlockedByTheTail() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p f note
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  # The gate child reports its gate line and nothing else; the gate is red. The tree carries the work it did — the
  # change the gate was run against — so the run commits it and the item is blocked rather than closed.
  stub_write "$d" "$STUB_GATE_REPORT" 'VERDICT: PASS' \
    'case "$1" in worker-prompt.md) printf "the fix\n" > demo-fix.txt ;; worker-tail*) ;; esac'
  # …and the tail is the child that reads the red gate and says so, with the one question a person has to answer.
  printf 'LOOP-VERDICT: blocked\nLOOP-CHANGE: fix-demo\nLOOP-ROOT-CAUSE: the fixture has exactly one cause\nLOOP-GATE: tests=1 failures=2 (the gate)\nLOOP-DECISION: fix the failing case, or abandon the item | recommended: fix it\n' \
    > "$d/tail-report.txt"
  export PI_STUB_REPORT_TAIL="$d/tail-report.txt"
  run_loop "$p" --items 1 --minutes 90 --review off >/dev/null 2>&1 || fail "the run itself failed"
  unset PI_STUB_REPORT_TAIL
  f=$(state_file "$p")
  assert_eq "2" "$(grep -c '^invocation' "$PI_STUB_LOG" || true)" "the gate child and the tail child ran"
  assert_eq "blocked" "$(jq -r '.items[0].result' "$f")" "a red gate blocks the item"
  note=$(jq -r '.items[0].note' "$f")
  # The reason quotes the tail's own finding: the gate's numbers are what the next attempt needs, and the arm that
  # would have fired without one names the absence of an archive instead — the opposite of what the tail reported.
  assert_contains "$note" "the item's verdict is blocked" "the reason is the tail's verdict, not the missing archive"
  assert_contains "$note" "tests=1 failures=2" "and carries the gate's own numbers"
  assert_not_contains "$note" "no archived change appears" "and never claims the tail reported success"
  # The verdict is the tail's own report, which the run keeps: only a closure records `--gate` in the item, so the
  # blocked record carries the gate line in its reason instead (asserted above).
  [ -f "$p/.pi/issue-loop/items/21/worker-tail-report.md" ] || fail "the tail child's report was not kept"
  assert_contains "$(cat "$p/.pi/issue-loop/items/21/worker-tail-report.md")" "LOOP-VERDICT: blocked" \
    "the tail child is the process that reported the item's verdict"
  assert_eq "0" "$(grep -c 'issue close 21' "$GH_STUB_RECORD" || true)" "nothing is closed"
  assert_contains "$(cat "$d/gh.log")" "agent/needs-decision" "and the item is escalated with the question"
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
  # files: tools/pi-child-events.sh tools/pi-shell-refusal.mjs tools/pi-trim-result.mjs
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
  assert_eq "$(printf -- '-e\n%s\n-e\n%s\n--mode\njson\n--no-context-files\n%s' \
    "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" "$LOOP_PROJECT_ROOT/tools/pi-shell-refusal.mjs" "$prompt")" "$got" \
    "the child sees the bound's extension, the shell policy's extension, the mode flag as two arguments, then the context switch, then the prompt"
  return 0
}

# The child inherits the run's environment and nothing between the two guarantees it carries the directory this
# project's agent keeps its own tools in (issue #796): 36 of 36 `ctx_grep` calls a child made on 2026-11-06 answered
# `zsh:1: command not found: rg` although the binary is in that directory (`evidence/ctx-grep-census.py`: 282 of 482
# `ctx_grep` calls over this archive), and a run started from a context whose `PATH` lacks it hands every worker no
# `rg` — the binary `ctx_grep` and the agent's own subprocesses look for. The adapter is the one place a fact about
# this project's agent may live (design D16), so the directory is prepended there — idempotent, and only when it
# exists.
case_adapter_theChildIsStartedWithItsToolDirectoryOnPath() {
  # files: tools/pi-child-events.sh
  local d prompt recorder got first
  [ -f "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" ] || return 0
  d=$(scratch)
  mkdir -p "$d/agent/bin"
  prompt="$d/prompt.md"
  printf 'Reply with OK.\n' > "$prompt"
  recorder="$d/recorder.sh"
  {
    printf '%s\n' '#!/usr/bin/env bash'
    printf '%s\n' 'printf "%s\n" "$PATH" > "$RECORD"'
  } > "$recorder"
  # The inherited `PATH` is the one a run started from a bare shell has: the standard directories and not this
  # project's agent directory, which is what the child has to gain.
  PI_CODING_AGENT_DIR="$d/agent" RECORD="$d/path.txt" PATH="/usr/bin:/bin" \
    bash "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" bash "$recorder" "$prompt" </dev/null >"$d/out" 2>&1 ||
    fail "the adapter exited non-zero: $(head -3 "$d/out")"
  got=$(cat "$d/path.txt" 2>/dev/null || true)
  [ -n "$got" ] || fail "the child recorded no PATH: $(head -3 "$d/out")"
  first=${got%%:*}
  assert_eq "$d/agent/bin" "$first" "the child's PATH begins with the agent's own tool directory (issue #796)"
  assert_contains "$got" "/usr/bin" "and the PATH it inherited is kept behind it"

  # A directory that is not there changes nothing: the two candidates are the agent's own (which this case moved) and
  # the home directory's, so a tree with neither keeps the PATH it was given — and no child is started with a path
  # that does not exist.
  PI_CODING_AGENT_DIR="$d/absent" HOME="$d/nohome" RECORD="$d/path-absent.txt" PATH="/usr/bin:/bin" \
    bash "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" bash "$recorder" "$prompt" </dev/null >"$d/out-absent" 2>&1 ||
    fail "the adapter exited non-zero with no tool directory: $(head -3 "$d/out-absent")"
  assert_eq "/usr/bin:/bin" "$(cat "$d/path-absent.txt" 2>/dev/null || true)" \
    "a directory that does not exist is not added to the child's PATH"

  # …and a PATH that already carries it gains no second copy: a run whose own environment has the directory must not
  # hand its children a PATH that grows by one entry per hop.
  PI_CODING_AGENT_DIR="$d/agent" RECORD="$d/path-twice.txt" PATH="$d/agent/bin:/usr/bin:/bin" \
    bash "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" bash "$recorder" "$prompt" </dev/null >"$d/out-twice" 2>&1 ||
    fail "the adapter exited non-zero with the directory already on PATH: $(head -3 "$d/out-twice")"
  got=$(cat "$d/path-twice.txt" 2>/dev/null || true)
  assert_eq "1" "$(printf '%s' "$got" | tr ':' '\n' | grep -cx -- "$d/agent/bin")" \
    "a PATH that already carries the directory gains no second copy of it"
  return 0
}

# What the adapter keeps is bounded (issue #802). The stream it writes grew for the item's whole life and across
# attempts, with no bound of any kind — measured 2026-11-06, 254 streams held 3 483 MiB and one file 63 MiB — so a
# stream past `LOOP_CHILD_EVENTS_MAX_BYTES` drops its oldest half and records the drop: the first line says how many
# lines, bytes and `turn_end`s went, and the newest event survives. A reader that counts turns adds what the record
# says it dropped (`run.sh`'s `child_turns` does), which is the invariant the `run#` case beside this group drives
# through a child.
case_adapter_theKeptStreamIsBoundedAndItsRotationIsRecorded() {
  # files: tools/pi-child-events.sh
  local d prompt emitter f short aged kept dropped size aged_before aged_after line
  [ -f "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" ] || return 0
  d=$(scratch)
  prompt="$d/prompt.md"
  printf 'Reply with OK.\n' > "$prompt"
  emitter="$d/emitter.sh"
  cat > "$emitter" <<'EMITTER'
#!/usr/bin/env bash
i=0
while [ "$i" -lt 40 ]; do
  i=$((i + 1))
  printf '{"type":"turn_end","n":%s,"pad":"%s"}\n' "$i" "0000000000000000000000000000000000000000000000000000000000000000"
done
printf '{"type":"message_end","message":{"role":"assistant","content":"OK"}}\n'
EMITTER
  f="$d/events.jsonl"
  LOOP_CHILD_EVENTS="$f" LOOP_CHILD_EVENTS_MAX_BYTES=2048 \
    bash "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" bash "$emitter" "$prompt" </dev/null >"$d/out" 2>&1 ||
    fail "the adapter exited non-zero: $(head -3 "$d/out")"
  [ -s "$f" ] || fail "the adapter kept no stream: $(head -3 "$d/out")"
  assert_eq "stream_rotation" "$(head -n 1 "$f" | jq -r '.type // ""' 2>/dev/null)" \
    "the stream's first line records the rotation"
  assert_eq "message_end" "$(tail -n 1 "$f" | jq -r '.type // ""' 2>/dev/null)" \
    "the newest event survives the rotation"
  assert_eq "1" "$(grep -c '"type":"stream_rotation"' "$f" || true)" \
    "one record stands for every rotation the stream went through"
  dropped=$(head -n 1 "$f" | jq -r '.dropped_turn_ends // 0')
  kept=$(grep -c '"type":"turn_end"' "$f" || true)
  [ "$dropped" -gt 0 ] || fail "the record says it dropped no turn, on a stream that held 40: $dropped"
  assert_eq "40" "$(( dropped + kept ))" \
    "what the record dropped plus what the stream still holds is every turn the child took"
  # The stream is bounded at its bound, not at twice it: the file may pass the count by the one line that pushed it
  # over before the rotation ran, and by nothing else.
  line=$(bash "$emitter" </dev/null | LC_ALL=C awk '{ if (length($0) > n) n = length($0) } END { print n + 0 }')
  size=$(wc -c < "$f")
  [ "$size" -le "$(( 2048 + line + 1 ))" ] || fail "the kept stream is not bounded by the recorded 2048 B plus one line: $size B"

  # A stream that was *already* past its bound when the child started is rotated by the first event appended to it:
  # the count is seeded from the file's own size, so a child that appends less than a bound still brings an aged file
  # down. Without the seed the bound only ever caps what one child adds, and a file that outgrew its bound — the
  # archive's largest stream, 63.47 MiB — stays whole for the item's whole life, which is the defect this case is for.
  aged="$d/aged.jsonl"
  bash "$emitter" </dev/null > "$aged"
  aged_before=$(wc -c < "$aged")
  [ "$aged_before" -gt 2048 ] || fail "the stream written before the child is not past the bound: $aged_before B"
  LOOP_CHILD_EVENTS="$aged" LOOP_CHILD_EVENTS_MAX_BYTES=2048 \
    bash "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" bash "$emitter" "$prompt" </dev/null >"$d/out-aged" 2>&1 ||
    fail "the adapter exited non-zero on a stream already past its bound: $(head -3 "$d/out-aged")"
  assert_eq "stream_rotation" "$(head -n 1 "$aged" | jq -r '.type // ""' 2>/dev/null)" \
    "a stream already past its bound is rotated by the first event appended to it"
  assert_eq "message_end" "$(tail -n 1 "$aged" | jq -r '.type // ""' 2>/dev/null)" \
    "and the newest event survives that rotation"
  aged_after=$(wc -c < "$aged")
  [ "$aged_after" -lt "$aged_before" ] || fail "the child left the aged stream as large as it found it: $aged_before B → $aged_after B"
  [ "$aged_after" -le "$(( 2048 + line + 1 ))" ] || fail "the aged stream is not bounded after the child appended to it: $aged_after B"

  # A stream under the bound is kept whole and carries no record: the bound is a bound on the tail, not a rewrite of
  # every item's history (the recorded value is above the median item's whole stream).
  short="$d/short.jsonl"
  LOOP_CHILD_EVENTS="$short" LOOP_CHILD_EVENTS_MAX_BYTES=1048576 \
    bash "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" bash "$emitter" "$prompt" </dev/null >"$d/out-short" 2>&1 ||
    fail "the adapter exited non-zero under the bound: $(head -3 "$d/out-short")"
  assert_eq "0" "$(grep -c '"type":"stream_rotation"' "$short" || true)" \
    "a stream under its bound is not rotated"
  assert_eq "40" "$(grep -c '"type":"turn_end"' "$short" || true)" \
    "and keeps every line it was given"
  return 0
}

# The child is started in the project, so the agent would turn the `AGENTS.md` it discovers there into a
# `project_context` section of its system prompt — 25 624 of the 33 088 characters of the system prompt on the
# stream of one item (probe `sections-probe.py`), re-read on every turn of a child whose whole work is one
# item (issue #661). The adapter therefore starts it with `--no-context-files`, and with nothing else that
# would move the policy the flag must not take with it: the start directory is what resolves this project's
# tool policy, so a word that changes the trust (`-a`, `-na`), replaces the prompt (`--system-prompt`) or
# narrows the tools (`-t`, `-ns`) is the defect this case exists for. That the children still start in the
# project is `run#childStartsInTheProjectRoot`, which this flag must leave green.
case_adapter_theChildIsStartedWithoutContextFiles() {
  # files: tools/pi-child-events.sh
  local d prompt recorder got extra
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
  assert_contains "$got" "--no-context-files" \
    "the child is started without the project's context files (issue #661)"
  extra=$(printf '%s\n' "$got" |
    grep -xE -- '-a|--approve|-na|--no-approve|--system-prompt|--append-system-prompt|-t|--tools|-ns|--no-skills' || true)
  assert_eq "" "$extra" "no word the child is started with moves its trust, its prompt or its tools"
  return 0
}

# A child's tool result enters its context whole and is re-read by every later turn (issue #660): the archive's
# 22 901 results carried 59.2 M characters, and the 9.0 % of them above 8 000 characters carry 14 % of the loop's
# whole spend (`python3 tools/loop-spend.py`: `LOOP-SPEND-CAP8K saved=228988001`). Prose does not bound that: the
# worker prompt has asked a child to read the section it needs and to cite a file rather than read it again since
# #246, and `ctx_read` still hands 6.7 k characters per call with a 116.6 k maximum. So the bound is applied where
# the result passes: `tools/pi-trim-result.mjs` is one rule with two entry points — the `tool_result` hook the
# adapter loads with every child, and the CLI these cases drive. What it keeps is the first `<bound>` characters
# and one line naming the tool, the size and the bound, with the line naming how to read the rest; the size it cut
# is recorded for the item, so a reader can re-read the range the notice names instead of the cut line.
case_trim_aResultAboveTheBoundIsTrimmedAndNamed() {
  # files: tools/loop-cases.sh tools/pi-trim-result.mjs
  local d big out kept n
  [ -f "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" ] \
    || fail "nothing bounds a tool result: tools/pi-trim-result.mjs is not in the tree (issue #660)"
  d=$(scratch)
  # 20 005 characters, so the cut is checkable by its prefix and by the tail that must not survive.
  big="$d/big.txt"
  { head -c 20000 /dev/zero | tr '\0' 'a'; printf 'TAIL\n'; } > "$big"
  out="$d/trimmed.txt"
  node "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" --bound 8000 --tool ctx_read \
    --record "$d/trims.log" < "$big" > "$out" \
    || fail "the bound exited non-zero: $(head -3 "$out")"
  kept=$(head -c 8000 "$big")
  assert_eq "$kept" "$(head -c 8000 "$out")" "the first <bound> characters are handed over unchanged"
  assert_not_contains "$(cat "$out")" "TAIL" "nothing past the bound reaches the child's context"
  assert_contains "$(cat "$out")" "LOOP-TRIMMED" "the trim is named in the result the child reads"
  assert_contains "$(cat "$out")" "20005" "the line names the size the result had"
  assert_contains "$(cat "$out")" "8000" "and the bound it was cut to"
  assert_contains "$(cat "$out")" "sed -n" "and how to read the rest, so a reader can re-read the range the notice names"
  n=$(wc -c < "$out")
  [ "$n" -lt 9000 ] || fail "the trimmed result still carries $n characters against a bound of 8000"
  return 0
}

# The bound is a rule about the tail, not about reading (issue #660): most of what a child is handed is small —
# the archive's median result is 825 characters, its p75 2 917, and only 9.0 % of its 22 901 results are above
# 8 000 (`evidence/result-sizes.log` in the change `2026-10-27-fix-a-tool-result-over-its-bound-is-trimmed`). A
# bound that touched an ordinary result would buy a saving with the content an item is worked from.
case_trim_aResultUnderTheBoundIsHandedOverWhole() {
  # files: tools/loop-cases.sh tools/pi-trim-result.mjs
  local d small exact out
  [ -f "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" ] \
    || fail "nothing bounds a tool result: tools/pi-trim-result.mjs is not in the tree (issue #660)"
  d=$(scratch)
  small="$d/small.txt"
  { head -c 1000 /dev/zero | tr '\0' 'b'; printf 'END\n'; } > "$small"
  out="$d/handed.txt"
  node "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" --bound 8000 --tool ctx_read \
    --record "$d/trims.log" < "$small" > "$out" \
    || fail "the bound exited non-zero: $(head -3 "$out")"
  assert_eq "$(cat "$small")" "$(cat "$out")" "a result at or under the bound is handed over unchanged"
  assert_contains "$(cat "$out")" "END" "its last line included"
  assert_not_contains "$(cat "$out")" "LOOP-TRIMMED" "and carries no notice about a bound it stayed under"
  [ ! -s "$d/trims.log" ] \
    || fail "a result under the bound is recorded as trimmed: $(cat "$d/trims.log")"
  # The boundary is the scenario's own word ("at most the recorded bound"), so the exact length is driven too and
  # not only a result comfortably under it: a `<=` that became `<` would cut a result of exactly <bound>
  # characters to an empty prefix and a notice, and the case above would not notice (review of #660, round 1).
  exact="$d/exact.txt"
  head -c 8000 /dev/zero | tr '\0' 'c' > "$exact"
  out="$d/exact-out.txt"
  node "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" --bound 8000 --tool ctx_read \
    --record "$d/exact-trims.log" < "$exact" > "$out" \
    || fail "the bound exited non-zero at the boundary: $(head -3 "$out")"
  assert_eq "$(wc -c < "$exact")" "$(wc -c < "$out")" "a result of exactly the bound is handed over whole"
  assert_not_contains "$(cat "$out")" "LOOP-TRIMMED" "with no notice at the boundary either"
  [ ! -s "$d/exact-trims.log" ] \
    || fail "a result of exactly the bound is recorded as trimmed: $(cat "$d/exact-trims.log")"
  return 0
}

# What was cut is a fact of the item, not of the child's memory (issue #660): the record lands beside the child's
# own event stream, so a reviewer of the item can see that a claim stands on a trimmed result and re-read the range
# it needs instead of reading a cut line as the whole answer. This case asserts the line; that it lands *beside the
# stream* is the path the adapter names, asserted by `adapter#theChildIsStartedWithTheResultBound`.
case_trim_theTrimIsRecorded() {
  # files: tools/loop-cases.sh tools/pi-trim-result.mjs
  local d big out
  [ -f "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" ] \
    || fail "nothing bounds a tool result: tools/pi-trim-result.mjs is not in the tree (issue #660)"
  d=$(scratch)
  big="$d/big.txt"
  { head -c 20000 /dev/zero | tr '\0' 'a'; printf 'TAIL\n'; } > "$big"
  out="$d/trimmed.txt"
  node "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" --bound 8000 --tool ctx_read \
    --record "$d/trims.log" < "$big" > "$out" \
    || fail "the bound exited non-zero: $(head -3 "$out")"
  assert_eq "LOOP-TOOL-RESULT-TRIM: ctx_read chars=20005 kept=8000" "$(cat "$d/trims.log")" \
    "the trim is recorded once, with the tool, the size it cut and the bound it cut to"
  return 0
}

# The bound reaches a child only if the process that starts it loads the extension (issue #660): the adapter is
# the one place this project's agent flags live, and every child goes through it, so the word is asserted there
# and not in a phase — a phase under `tools/loop/**` may name no agent flag (`run#engineNamesNoProjectFact`). What
# the child is handed as the bound is the recorded value, not a second copy of it: the adapter reads it from
# `tools/loop/loop.conf`, and a number that drifts from that file is a red case. The record of a trim is a fact of
# the item and not of the child's memory, so the second half asserts where it lands: the adapter names
# `<item dir>/tool-result-trims.log` beside the event stream the run gave that child, and names nothing when it was
# started by hand. That export was the one line no case and no mutation touched in the first round — deleting it
# left all four other cases green while every trim record silently vanished (review of #660).
case_adapter_theChildIsStartedWithTheResultBound() {
  # files: tools/loop-cases.sh tools/loop/loop.conf tools/pi-child-events.sh tools/pi-trim-result.mjs
  local d prompt recorder got ext line next recorded handed
  [ -f "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" ] || return 0
  ext="$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs"
  [ -f "$ext" ] \
    || fail "the adapter loads the result bound, and $ext is not in the tree (issue #660)"
  d=$(scratch)
  prompt="$d/prompt.md"
  printf 'Reply with OK.\n' > "$prompt"
  recorder="$d/recorder.sh"
  {
    printf '%s\n' '#!/usr/bin/env bash'
    printf '%s\n' 'for a in "$@"; do printf "%s\n" "$a"; done > "$RECORD"'
    printf '%s\n' 'printf "%s\n" "$LOOP_TOOL_RESULT_MAX_CHARS" > "$RECORD.bound"'
    printf '%s\n' 'printf "%s\n" "${LOOP_TOOL_RESULT_TRIM_RECORD:-}" > "$RECORD.record"'
  } > "$recorder"
  # The stream is unset explicitly: the suite may itself run inside a child whose run exported one, and a "started by
  # hand" assertion that inherited it would measure the run's stream rather than the adapter's silence. The trim
  # record is unset with it, for the same reason: a run exports one into every child it starts, and a hand-started
  # child that inherited it would name the run's record instead of none (review of #320).
  env -u LOOP_CHILD_EVENTS -u LOOP_TOOL_RESULT_TRIM_RECORD RECORD="$d/argv.txt" \
    bash "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" \
    bash "$recorder" "$prompt" </dev/null >"$d/out" 2>&1 ||
    fail "the adapter exited non-zero: $(head -3 "$d/out")"
  got=$(cat "$d/argv.txt" 2>/dev/null || true)
  line=$(printf '%s\n' "$got" | grep -n -x -- '-e' | head -1 | cut -d: -f1)
  [ -n "$line" ] || fail "the child is started without the result bound: no -e in $got"
  next=$(printf '%s\n' "$got" | sed -n "$((line + 1))p")
  assert_eq "$ext" "$next" "the word after -e is the file that applies the bound"
  assert_contains "$(cat "$ext")" 'tool_result' \
    "and that file is a hook on the tool result, not a flag that happens to be accepted"
  recorded=$(sed -n 's/^LOOP_TOOL_RESULT_MAX_CHARS=["]\{0,1\}\([0-9][0-9]*\)["]\{0,1\}[[:space:]]*$/\1/p' \
    "$LOOP_PROJECT_ROOT/tools/loop/loop.conf" | tail -1)
  [ -n "$recorded" ] || fail "the project records no LOOP_TOOL_RESULT_MAX_CHARS in tools/loop/loop.conf"
  handed=$(cat "$d/argv.txt.bound" 2>/dev/null || true)
  assert_eq "$recorded" "$handed" "the bound the child is handed is the number the project recorded"
  assert_eq "" "$(cat "$d/argv.txt.record" 2>/dev/null || true)" \
    "a child started by hand, with no event stream, names no record file and writes nothing into the project"
  RECORD="$d/events-argv.txt" LOOP_CHILD_EVENTS="$d/events.jsonl" \
    bash "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" \
    bash "$recorder" "$prompt" </dev/null >"$d/out-events" 2>&1 ||
    fail "the adapter exited non-zero with an event stream: $(head -3 "$d/out-events")"
  assert_eq "$d/tool-result-trims.log" "$(cat "$d/events-argv.txt.record" 2>/dev/null || true)" \
    "the trim is recorded beside the child's own event stream, in the item's directory"
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
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/report.sh tools/pi-child-events.sh tools/tooling.json
  local d p w branch head out report_branches
  d=$(scratch); p=$(new_project); w="$p/.pi/worktrees/loop"
  out=$(run_one_item "$p" "$d" 2>&1) || fail "the run failed"
  branch=$(git -C "$p" branch --list 'fix/issue-*' --format='%(refname:short)')
  [ -z "$branch" ] || fail "the run left the branch of the item it closed behind: $branch"
  # The run deletes this branch by two paths — the item's own release after its merge (`worktree.sh
  # release-branch`, called from run.sh) and the end-of-run sweep of what landed (`prune-landed --apply`, issue
  # #239) — so the assertion above is downstream of both, and disabling one of them alone leaves it true while
  # the other answers for it (issue #698, mutation 42.2). The run's report is printed *between* the two: it reads
  # git before the sweep runs, so it answers what the merge left behind, and nothing the sweep does can make this
  # second assertion true.
  report_branches=$(printf '%s\n' "$out" | sed -n 's/^branches: //p' | tail -1)
  [ -n "$report_branches" ] || fail "the run printed no report line to read — the line this case reads was reworded"
  assert_not_contains "$report_branches" "fix/issue-" \
    "the branch was removed where the run merged it, not by the end sweep that runs after the report"
  # The work is in the base — the branch is gone, the fix is not.
  git -C "$p" ls-tree -r --name-only main | grep -q 'openspec/changes/archive/2026-01-01-fix-demo/proposal.md' ||
    fail "the item's change is not in the base"
  # The worktree was released from the branch, not broken by it: it is on no branch of its own, and the next
  # item still cuts its own branch from the base.
  head=$(git -C "$w" rev-parse --abbrev-ref HEAD 2>/dev/null)
  assert_eq "HEAD" "$head" "the worktree is not on the branch that was removed"
}

# The run's own end is where the branches it made go: an item's branch is released after its merge, and every
# other branch the loop names is a leftover once its patches are in the base. The run asked nobody to run
# `loop-clean` afterwards, so a `loop-*` branch that landed when the next run switched off it, and every branch a
# manual landing left behind, stayed for ever (issue #239). The sweep is the run's, and it keeps what still
# carries work, what a worktree holds, and the branch whose change a reviewer refused.
case_run_theEndSweepsTheLandedBranchesItMade() {
  # files: tools/loop/worktree.sh tools/loop/run.sh tools/loop/fixtures/scenarios/ tools/pi-child-events.sh tools/tooling.json
  local p out
  p=$(new_project)
  # A branch whose patch is in the base: it was merged, so it carries nothing.
  git -C "$p" switch -c fix/issue-1-landed >/dev/null 2>&1 || fail "could not create the landed branch"
  printf 'the fix\n' > "$p/landed.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" commit -qm "fix: the landed one (#1)" >/dev/null 2>&1 || fail "could not commit the landed branch"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not switch back to main"
  git -C "$p" merge --ff-only fix/issue-1-landed >/dev/null 2>&1 || fail "could not merge the landed branch"
  # A branch that still carries work the base does not have.
  git -C "$p" switch -c fix/issue-2-work >/dev/null 2>&1 || fail "could not create the work branch"
  printf 'the work\n' > "$p/work.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" commit -qm "fix: the work (#2)" >/dev/null 2>&1 || fail "could not commit the work branch"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not switch back to main"
  # A branch whose change a reviewer refused, landed by hand: the verdict is its owner's, so the branch stays.
  git -C "$p" switch -c fix/issue-3-refused >/dev/null 2>&1 || fail "could not create the refused branch"
  mkdir -p "$p/openspec/changes/archive/2026-01-01-fix-refused/evidence"
  printf 'the reviewer refused this change\n' > "$p/openspec/changes/archive/2026-01-01-fix-refused/evidence/review-failed-round-1.md"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" commit -qm "fix: the refused one (#3)" >/dev/null 2>&1 || fail "could not commit the refused branch"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not switch back to main"
  git -C "$p" merge --ff-only fix/issue-3-refused >/dev/null 2>&1 || fail "could not merge the refused branch"
  # A landed branch a worktree holds: the tree is in use, so git would refuse the delete and the branch is its
  # HEAD. The sweep keeps it and names why — the refusal the summary counts as checked out.
  git -C "$p" switch -c fix/issue-4-held >/dev/null 2>&1 || fail "could not create the held branch"
  printf 'the held fix\n' > "$p/held.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" commit -qm "fix: the held one (#4)" >/dev/null 2>&1 || fail "could not commit the held branch"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not switch back to main"
  git -C "$p" merge --ff-only fix/issue-4-held >/dev/null 2>&1 || fail "could not merge the held branch"
  git -C "$p" worktree add "$(scratch)/held" fix/issue-4-held >/dev/null 2>&1 ||
    fail "could not add the worktree that holds the landed branch"
  # The read-only run is what `--apply` means: it names and deletes nothing, the held branch included.
  out=$(run_worktree "$p" prune-landed 2>&1) || fail "the read-only sweep failed: $out"
  assert_contains "$out" "would remove fix/issue-1-landed" "the read-only sweep names the landed branch"
  assert_contains "$out" "kept fix/issue-4-held — checked out" "the read-only sweep keeps the tree's branch"
  git -C "$p" rev-parse --verify --quiet refs/heads/fix/issue-1-landed >/dev/null ||
    fail "the read-only sweep removed a branch"

  out=$(run_one_item "$p" "$(scratch)" 2>&1) || fail "the run failed: $out"
  git -C "$p" rev-parse --verify --quiet refs/heads/fix/issue-1-landed >/dev/null 2>&1 &&
    fail "the landed branch survived the run's own end"
  git -C "$p" rev-parse --verify --quiet refs/heads/fix/issue-4-held >/dev/null ||
    fail "the landed branch a worktree holds was removed"
  git -C "$p" rev-parse --verify --quiet refs/heads/fix/issue-2-work >/dev/null ||
    fail "the branch that carries work was removed"
  git -C "$p" rev-parse --verify --quiet refs/heads/fix/issue-3-refused >/dev/null ||
    fail "the branch whose change a reviewer refused was removed"
  assert_contains "$out" "removed fix/issue-1-landed" "the landed branch is named as removed"
  assert_contains "$out" "kept fix/issue-2-work" "the branch that carries work is kept"
  assert_contains "$out" "kept fix/issue-3-refused" "the branch whose reviewer refused is kept"
  assert_contains "$out" "kept fix/issue-4-held — checked out" "the branch a worktree holds is kept and named"
  assert_contains "$out" "landed branch(es) removed" "the run's end says what it swept"
  [ -z "$(git -C "$p" branch --list 'loop-*' --format='%(refname:short)')" ] ||
    fail "the run's own branch is a leftover once its work is in the base: $(git -C "$p" branch --list 'loop-*' --format='%(refname:short)') — output: $out"
}

# A head branch the loop published is in no local namespace, so every sweep missed it: `prune-landed` enumerated
# `git branch --list`, and the loop's only push publishes and never deletes (issue #763). The ref sweep gains a
# remote arm beside its local one, and that arm's destructive half owes the one reading a shared remote takes: a
# ref that is the head of an open pull request is somebody's review, and deleting it closes their pull request.
case_run_theRemoteSweepKeepsAnOpenPullRequest() {
  # files: tools/loop/worktree.sh tools/loop/lib.sh tools/pi-child-events.sh tools/tooling.json tools/issues/fixtures/
  local d p origin out refs slug fetched_tip
  d=$(scratch); p=$(new_project); origin="$d/origin.git"
  git init -q --bare "$origin" >/dev/null 2>&1 || fail "could not create the remote"
  git -C "$p" remote add origin "$origin" >/dev/null 2>&1 || fail "could not add the remote"
  # Two published heads whose patches are in the base, neither of them a local branch any more: the residue the
  # loop's own path leaves — it publishes, the work is adopted on the base, and the local copy goes.
  for slug in landed open; do
    git -C "$p" switch -c "fix/issue-1-$slug" >/dev/null 2>&1 || fail "could not create the $slug branch"
    printf 'the %s fix\n' "$slug" > "$p/$slug.txt"
    git -C "$p" add -A >/dev/null 2>&1
    git -C "$p" commit -qm "fix: the $slug one (#1)" >/dev/null 2>&1 || fail "could not commit the $slug branch"
    git -C "$p" switch main >/dev/null 2>&1 || fail "could not switch back to the base"
    git -C "$p" merge --ff-only "fix/issue-1-$slug" >/dev/null 2>&1 || fail "could not merge the $slug branch"
    git -C "$p" push -q origin main "fix/issue-1-$slug" >/dev/null 2>&1 || fail "could not publish the $slug branch"
    git -C "$p" branch -D "fix/issue-1-$slug" >/dev/null 2>&1 || fail "could not drop the local copy of the $slug branch"
  done
  # `fix/issue-1-open` is the head of an open pull request, read through the forge seam the loop already uses.
  mkdir -p "$d/gh" || fail "could not make the forge fixture directory"
  printf '[{"number":7,"headRefName":"fix/issue-1-open","state":"OPEN"}]\n' > "$d/gh/prs.json" ||
    fail "could not write the pull-request fixture"
  sweep_remote_heads() { git -C "$origin" for-each-ref --format='%(refname:short)' 'refs/heads/fix/*' | sort | paste -sd' ' -; }

  # A person's sweep stays a dry run: it names both refs, keeps the pull request's, and deletes nothing.
  out=$(GH_STUB_DIR="$d/gh" run_worktree "$p" prune-landed 2>&1) || fail "the read-only sweep failed: $out"
  assert_contains "$out" "would remove origin/fix/issue-1-landed" "the read-only sweep names the landed head"
  assert_contains "$out" "kept origin/fix/issue-1-open — the head of an open pull request" \
    "the read-only sweep keeps the ref somebody's pull request is"
  assert_eq "fix/issue-1-landed fix/issue-1-open" "$(sweep_remote_heads)" "the read-only sweep deleted a remote ref"

  # A forge that cannot be read is not a forge with no pull requests: an unattended `--apply` deletes nothing.
  out=$(GH_STUB_DIR="$d/gh" GH_STUB_READ_FAIL="pr list" run_worktree "$p" prune-landed --apply 2>&1) ||
    fail "the sweep failed on an unreadable forge: $out"
  assert_contains "$out" "kept origin/fix/issue-1-landed — the open pull requests could not be read" \
    "an unreadable forge keeps every remote ref and names why"
  assert_eq "fix/issue-1-landed fix/issue-1-open" "$(sweep_remote_heads)" "a ref was deleted on a forge nobody could read"

  # `--apply` deletes the landed head and keeps the pull request's.
  out=$(GH_STUB_DIR="$d/gh" run_worktree "$p" prune-landed --apply 2>&1) || fail "the sweep failed: $out"
  assert_contains "$out" "removed origin/fix/issue-1-landed" "the landed head is named as removed"
  assert_eq "fix/issue-1-open" "$(sweep_remote_heads)" "the sweep removed the head of somebody's open pull request"

  # A head somebody advanced on the remote after this clone's last fetch. The candidates are resolved on the remote,
  # not in this clone's cache of it, so the tip judged is the published one: the base carries the patches of the tip
  # this clone last fetched and none of the commits added since, so the ref carries work the base does not have and
  # stays. Read from the cache, it would be judged landed at the stale tip and deleted with the commits it gained
  # since — unattended, on a shared remote, against the requirement that only a ref the base carries goes.
  git -C "$p" switch -c fix/issue-1-advanced >/dev/null 2>&1 || fail "could not create the advanced branch"
  printf 'the advanced fix\n' > "$p/advanced.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" commit -qm "fix: the advanced one (#1)" >/dev/null 2>&1 || fail "could not commit the advanced branch"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not switch back to the base"
  git -C "$p" merge --ff-only fix/issue-1-advanced >/dev/null 2>&1 || fail "could not merge the advanced branch"
  git -C "$p" push -q origin main "fix/issue-1-advanced" >/dev/null 2>&1 || fail "could not publish the advanced branch"
  fetched_tip=$(git -C "$p" rev-parse "refs/remotes/origin/fix/issue-1-advanced") ||
    fail "could not read the fetched tip of the advanced branch"
  # Somebody's later commit, published on the same head, after that fetch.
  git -C "$p" switch fix/issue-1-advanced >/dev/null 2>&1 || fail "could not check out the advanced branch"
  printf 'a newer tip\n' > "$p/newer.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" commit -qm "fix: a newer tip (#1)" >/dev/null 2>&1 || fail "could not commit the newer tip"
  git -C "$p" push -q origin "fix/issue-1-advanced" >/dev/null 2>&1 || fail "could not advance the published head"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not switch back to the base"
  git -C "$p" branch -D fix/issue-1-advanced >/dev/null 2>&1 || fail "could not drop the local copy of the advanced branch"
  git -C "$p" update-ref "refs/remotes/origin/fix/issue-1-advanced" "$fetched_tip" ||
    fail "could not put the cache back to the tip last fetched"

  out=$(GH_STUB_DIR="$d/gh" run_worktree "$p" prune-landed 2>&1) || fail "the sweep failed on an advanced head: $out"
  assert_contains "$out" "kept origin/fix/issue-1-advanced — carries work the base does not have" \
    "the sweep judged a remotely advanced head at the tip this clone last fetched"
  assert_contains "$out" "kept origin/fix/issue-1-open — the head of an open pull request" \
    "the sweep lost the pull request's head beside an advanced one"
  assert_eq "fix/issue-1-advanced fix/issue-1-open" "$(sweep_remote_heads)" \
    "the read-only sweep deleted a remote ref beside an advanced head"

  out=$(GH_STUB_DIR="$d/gh" run_worktree "$p" prune-landed --apply 2>&1) ||
    fail "the apply sweep failed on an advanced head: $out"
  assert_contains "$out" "kept origin/fix/issue-1-advanced — carries work the base does not have" \
    "the sweep removed a head the remote had advanced"
  assert_eq "fix/issue-1-advanced fix/issue-1-open" "$(sweep_remote_heads)" \
    "the sweep deleted a head the remote had advanced"
}

# The base the sweep compared against was the pin the run started on — a frozen `run.base` from the run state — so
# a branch landed during or after the run read as work the base does not have, and the sweep that exists to remove
# it kept it (issue #763). The base is the fetched `origin/<base branch>`.
case_run_theSweepComparesAgainstTheFetchedBase() {
  # files: tools/loop/worktree.sh tools/loop/lib.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json tools/issues/fixtures/
  local d p out
  d=$(scratch); p=$(new_project)
  git init -q --bare "$d/origin.git" >/dev/null 2>&1 || fail "could not create the remote"
  git -C "$p" remote add origin "$d/origin.git" >/dev/null 2>&1 || fail "could not add the remote"
  git -C "$p" push -q origin main >/dev/null 2>&1 || fail "could not publish the base"
  # The run records the pin it started on.
  init_state "$p" 1 90
  # A branch that lands on the base after that pin.
  git -C "$p" switch -c fix/issue-2-later >/dev/null 2>&1 || fail "could not create the branch"
  printf 'the later fix\n' > "$p/later.txt"
  git -C "$p" add -A >/dev/null 2>&1
  git -C "$p" commit -qm "fix: landed after the run began (#2)" >/dev/null 2>&1 || fail "could not commit the branch"
  git -C "$p" switch main >/dev/null 2>&1 || fail "could not switch back to the base"
  git -C "$p" merge --ff-only fix/issue-2-later >/dev/null 2>&1 || fail "could not merge the branch"
  git -C "$p" push -q origin main >/dev/null 2>&1 || fail "could not publish the advanced base"
  mkdir -p "$d/gh" || fail "could not make the forge fixture directory"
  printf '[]\n' > "$d/gh/prs.json" || fail "could not write the pull-request fixture"

  out=$(GH_STUB_DIR="$d/gh" run_worktree "$p" prune-landed --apply 2>&1) || fail "the sweep failed: $out"
  assert_contains "$out" "removed fix/issue-2-later" "a branch the run's own pin predates is swept"
  assert_not_contains "$out" "carries work the base does not have" \
    "the sweep does not compare against the pin the run started on"
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

# The run keeps one worktree, and a tree under `.pi/worktrees/` that no configuration, no state field and no branch
# marker names is named by nothing at all — the report had no arm for it, which is how the per-item trees of the
# runs that kept one tree per item accumulated unmeasured (issue #803; measured 2026-11-06, `.pi/worktrees/` held
# ten directories and one of them was the run's). A tree is a leftover by what it *holds*, the same reading a
# branch gets one level down: uncommitted changes are somebody's work and keep the tree, a base or a HEAD that
# cannot be read keeps it for the same reason, and everything else is `--apply`'s — which the report names and never
# removes without it.
case_clean_reportsAWorktreeNoRunKeeps() {
  # files: tools/loop/loop-clean.sh tools/loop/worktree.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p kept leftover dirty unknown out
  p=$(new_project)
  init_state "$p" 1 90
  run_worktree "$p" create --branch loop-test >/dev/null || fail "worktree create failed"
  kept="$p/.pi/worktrees/loop"
  leftover="$p/.pi/worktrees/767"
  dirty="$p/.pi/worktrees/779"
  git -C "$p" worktree add -b item-767 "$leftover" main >/dev/null 2>&1 || fail "could not add the unnamed worktree"
  git -C "$p" worktree add -b item-779 "$dirty" main >/dev/null 2>&1 || fail "could not add the second unnamed worktree"
  printf 'work nobody committed\n' > "$dirty/attempt.txt"

  out=$(run_clean "$p" 2>&1) || fail "the report failed: $out"
  assert_contains "$out" "${leftover#"$p"/}" "the report names the tree no run keeps"
  assert_matches "$out" "would remove ${leftover#"$p"/} \\([0-9]+ B\\) — no run keeps it" \
    "the tree no run keeps is named with its size"
  assert_contains "$out" "${dirty#"$p"/}" "and the tree somebody is working in"
  [ -d "$leftover" ] || fail "the report removed a worktree without --apply"
  [ -d "$dirty" ] || fail "the report removed the tree somebody is working in"

  out=$(run_clean "$p" --apply 2>&1) || fail "the cleanup failed: $out"
  [ ! -e "$leftover" ] || fail "--apply kept the tree no run keeps"
  [ -d "$dirty" ] || fail "--apply removed a tree with uncommitted work"
  assert_contains "$out" "uncommitted" "the tree it keeps is named with the reason it is kept"
  [ -d "$kept" ] || fail "--apply removed the run's own worktree"

  # An unreadable base is the same unknown the branch arm one screen above keeps every branch on: a tree is not
  # "carries nothing" because the question could not be asked, so `--apply` keeps it and names the reason.
  unknown="$p/.pi/worktrees/781"
  git -C "$p" worktree add -b item-781 "$unknown" main >/dev/null 2>&1 || fail "could not add the third unnamed worktree"
  out=$(LOOP_BASE_REF=no-such-base run_clean "$p" --apply 2>&1) ||
    fail "the cleanup failed on an unreadable base: $out"
  assert_contains "$out" "kept ${unknown#"$p"/} — the base no-such-base or the tree's HEAD cannot be read" \
    "a tree whose base cannot be read is kept, and the reason is named"
  [ -d "$unknown" ] || fail "--apply removed a tree it could not judge"
  [ -d "$kept" ] || fail "--apply removed the run's own worktree on an unreadable base"
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

# One cell of a table row, by the column's 1-based index in the row's own cells. A row starts and ends with `|`,
# so `awk -F'|'`'s field `n+1` is cell `n`. A case that asserts "the mechanism column" has to read that column:
# a substring taken from the whole row also matches a neighbour that happens to print the same word, which is how
# the round-1 assertion for `-` matched the reason column instead of the stop column (issue #280, review round 1).
table_cell() { printf '%s' "$1" | awk -F'|' -v n="$2" '{c=$(n+1); gsub(/^[ ]+|[ ]+$/,"",c); print c}'; }

# The table has to answer the run's own questions on its own (#208): how did each item end — the verdict and,
# when the result is not a closure, the item's own recorded reason — and what did it cost, read from the wall
# time `done --wall` already recorded. Both facts were in the state and printed nowhere. The spend columns
# (`tools`, `tokens`, `context`, #211) are appended after the wall time, and this case keeps the shape of the
# row honest: the earlier columns stay where they were.
case_report_wallTimeAndReasonInTheTable() {
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out header row_done row_blocked row_muted
  p=$(new_project)
  init_state "$p" 3 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2},{"issue":23,"class":"bug","verdict":"eligible","rank":3}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result done --verdict closed --wall 754 \
    --gate "tests=1 failures=0" --root-cause "the fixture has exactly one cause" >/dev/null
  run_state "$p" done 22 --result blocked --verdict blocked --stop-reason refused \
    --note "two mechanisms explain the symptom" --decision-label agent/needs-decision >/dev/null
  run_state "$p" done 23 --result blocked --verdict blocked \
    --note "no mechanism caused this one" >/dev/null
  out=$(run_report "$p") || fail "the report failed"
  header=$(printf '%s\n' "$out" | grep '^| issue |' || true)
  assert_contains "$header" "| wall |" "the table has a column for the item's wall time"
  assert_contains "$header" "| reason |" "and one for the reason a non-done result carries"
  assert_contains "$header" "| stop |" "and one for the mechanism a block stopped on (issue #280)"
  row_done=$(printf '%s\n' "$out" | grep '^| #21 |' || true)
  row_blocked=$(printf '%s\n' "$out" | grep '^| #22 |' || true)
  row_muted=$(printf '%s\n' "$out" | grep '^| #23 |' || true)
  [ -n "$row_done" ] || fail "no table row for #21"
  assert_contains "$row_done" "12m34s" "754 s is printed as the item's wall time"
  assert_contains "$row_done" "| done |" "and the row still says how the item ended"
  assert_contains "$row_blocked" "two mechanisms explain the symptom" "the blocked item's own reason is printed"
  assert_contains "$row_blocked" "| blocked |" "beside its outcome"
  # The mechanism is a column of its own, so the spend beside it is totalled by mechanism without reading the
  # note: a closure has none, a block names the one that stopped it, and a block neither mechanism caused has
  # none. Each of the three is read from column 15 itself — the round-1 assertion for the closure's `-` matched
  # the *reason* column, which prints `-` for every closure, so the stop column was never read (issue #280,
  # review round 1).
  assert_eq "refused" "$(table_cell "$row_blocked" 15)" \
    "the mechanism that stopped the block is in a column of its own"
  assert_eq "-" "$(table_cell "$row_done" 15)" "while a closure's stop column prints -"
  assert_eq "-" "$(table_cell "$row_muted" 15)" \
    "and a block no mechanism caused prints - there too"
  assert_eq "blocked" "$(table_cell "$row_muted" 13)" "that row is still a block"
  assert_eq "no mechanism caused this one" "$(table_cell "$row_muted" 14)" \
    "whose reason is its own note, so - in the stop column is not a row without a reason"
  # Seventeen columns, so eighteen separators: the wall, the reason and the three spend columns were added, then
  # the block's mechanism, then the band an item's spend is read against (issue #662), then the shape it spent it
  # in (#757), and nothing was dropped. The row keeps its shape with the new column, and the block neither
  # mechanism caused has the same shape as the others.
  assert_eq "18" "$(printf '%s' "$row_done" | grep -o '|' | wc -l | tr -d ' ')" \
    "the row keeps its seventeen columns"
  assert_eq "18" "$(printf '%s' "$row_blocked" | grep -o '|' | wc -l | tr -d ' ')" \
    "the blocked row has the same shape"
  assert_eq "18" "$(printf '%s' "$row_muted" | grep -o '|' | wc -l | tr -d ' ')" \
    "and so does a block no mechanism caused"
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

# A run's spend has a band it can be read against (issue #662): the report printed an item's tokens, its largest
# context and a run-level total, and held no bound against any of them, so an item that cost twice what it used to
# read exactly like a healthy one — over the archive the per-item median doubled from 9.51 M to 18.30 M tokens in
# three days and no report said a word. `LOOP_ITEM_TOKENS_WARN` records the band, an item above it carries how far
# above it is in a column of its own, and the run-level line names the items. It is a **warning, never a stop**:
# a bound that ended an item would trade a measurement for a refusal and would fire hardest on the items that were
# hardest to land, so both items here keep the outcome their work earned.
case_report_anItemOverTheTokenBoundIsFlagged() {
  # files: tools/loop/loop.conf tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf out row_over row_under
  d=$(scratch); p=$(new_project)
  # The band is a recorded value, so the case measures against one of its own rather than following the project's.
  conf="$d/loop.conf"
  sed -e 's/^LOOP_ITEM_TOKENS_WARN=.*/LOOP_ITEM_TOKENS_WARN=1000000/' "$LOOP_ENGINE/loop.conf" > "$conf" ||
    fail "could not write the loop configuration"
  export LOOP_CONF="$conf"
  # 2 500 000 tokens = input 100 000 + output 200 000 + cacheRead 2 200 000, which is 2.5x the bound.
  cat > "$d/over.jsonl" <<'JSONL'
{"type":"message_update","usage":{"input":100000,"output":200000,"cacheRead":2200000,"cacheWrite":0,"cost":{"total":0}}}
{"type":"turn_end","message":{"role":"assistant","content":[]},"toolResults":[]}
JSONL
  # 230 000 tokens, well below the bound.
  cat > "$d/under.jsonl" <<'JSONL'
{"type":"message_update","usage":{"input":10000,"output":20000,"cacheRead":200000,"cacheWrite":0,"cost":{"total":0}}}
{"type":"turn_end","message":{"role":"assistant","content":[]},"toolResults":[]}
JSONL
  init_state "$p" 2 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result done --verdict closed --wall 42 --events "$d/over.jsonl" >/dev/null
  run_state "$p" done 22 --result done --verdict closed --wall 42 --events "$d/under.jsonl" >/dev/null
  out=$(run_report "$p") || fail "the report failed"
  assert_contains "$out" "over the item token bound (1000000 tokens): #21 2.5x" \
    "the run names the item above the bound and how far above it is"
  assert_not_contains "$(printf '%s\n' "$out" | grep '^over the item token bound' || true)" "#22" \
    "and the item below it is not named"
  row_over=$(printf '%s\n' "$out" | grep '^| #21 |' || true)
  row_under=$(printf '%s\n' "$out" | grep '^| #22 |' || true)
  [ -n "$row_over" ] || fail "no table row for #21"
  assert_eq "2.5x" "$(table_cell "$row_over" 16)" \
    "the row above the bound says how far above it is, in a column of its own"
  assert_eq "-" "$(table_cell "$row_under" 16)" "and a row below it prints -, not a zero"
  assert_contains "$out" "closed 2 · mis-diagnosed 0 · skipped 0 · blocked 0" \
    "the bound is a warning: both items keep the outcome their work earned"
  # A project that records no band flags nothing, and the line says the flag is off rather than `none` — which
  # would read as a measurement nobody made, the rule the spend columns already follow for an unreported figure.
  sed -e 's/^LOOP_ITEM_TOKENS_WARN=.*/LOOP_ITEM_TOKENS_WARN=0/' "$conf" > "$d/off.conf"
  export LOOP_CONF="$d/off.conf"
  out=$(run_report "$p") || fail "the report failed with the bound off"
  assert_contains "$out" "over the item token bound: off" "a bound of 0 is off, and the report says so"
  row_over=$(printf '%s\n' "$out" | grep '^| #21 |' || true)
  assert_eq "-" "$(table_cell "$row_over" 16)" "and no row is flagged by a bound that is off"
}

# Every cost in this loop is turns times context, so the shape a session spent its budget in is a number of its own
# (issue #757): the archive reads 36 825 tool calls in 33 183 turns, 1.11 per turn, at 78 k tokens per turn and 97 %
# cache read — a chain of single-call turns, each re-reading the whole conversation. The state now records the two
# counts it is derived from (`metrics.calls`, `metrics.turns`), the table prints the item's ratio, and the trend
# prints the run's ratio beside the items below `LOOP_ITEM_CALLS_PER_TURN_MIN` — the shape read against a recorded
# floor, the way the token band reads an item's spend against a ceiling. A **warning, never a stop**.
case_report_anItemBelowTheCallsPerTurnFloorIsNamed() {
  # files: tools/loop/loop.conf tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf f out trend calls_line row_batched row_chained row_empty
  d=$(scratch); p=$(new_project)
  # The floor is a recorded value, so the case measures against one of its own rather than following the project's.
  conf="$d/loop.conf"
  sed -e 's/^LOOP_ITEM_CALLS_PER_TURN_MIN=.*/LOOP_ITEM_CALLS_PER_TURN_MIN=2/' "$LOOP_ENGINE/loop.conf" > "$conf" ||
    fail "could not write the loop configuration"
  export LOOP_CONF="$conf"
  # #21 batches: six calls in two turns, 3.0 per turn. #22 is a chain: two calls in four turns, 0.5 per turn.
  cat > "$d/batched.jsonl" <<'JSONL'
{"type":"tool_execution_end","toolCallId":"c1","toolName":"read"}
{"type":"tool_execution_end","toolCallId":"c2","toolName":"read"}
{"type":"tool_execution_end","toolCallId":"c3","toolName":"bash"}
{"type":"turn_end"}
{"type":"tool_execution_end","toolCallId":"c4","toolName":"read"}
{"type":"tool_execution_end","toolCallId":"c5","toolName":"bash"}
{"type":"tool_execution_end","toolCallId":"c6","toolName":"bash"}
{"type":"turn_end"}
JSONL
  cat > "$d/chained.jsonl" <<'JSONL'
{"type":"tool_execution_end","toolCallId":"d1","toolName":"read"}
{"type":"turn_end"}
{"type":"turn_end"}
{"type":"tool_execution_end","toolCallId":"d2","toolName":"read"}
{"type":"turn_end"}
{"type":"turn_end"}
JSONL
  init_state "$p" 2 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result done --verdict closed --wall 42 --events "$d/batched.jsonl" >/dev/null
  run_state "$p" done 22 --result done --verdict closed --wall 42 --events "$d/chained.jsonl" >/dev/null
  f=$(state_file "$p")
  assert_eq "6" "$(jq -r '.items[] | select(.issue == 21) | .metrics.calls' "$f")" \
    "the item's tool calls are counted from its event stream"
  assert_eq "2" "$(jq -r '.items[] | select(.issue == 21) | .metrics.turns' "$f")" \
    "and so are its turns, so the ratio is derived from two recorded numbers"
  out=$(run_report "$p") || fail "the report failed"
  # 8 calls in 6 turns is 1.3333…, printed at the two decimals the ratio is read at.
  trend=$(printf '%s\n' "$out" | sed -n "/^trend /,/^  landings:/p")
  assert_contains "$trend" "calls:  1.33 calls per turn (8 calls / 6 turns)" \
    "the trend prints the run's calls per turn, with the two counts it is derived from"
  calls_line=$(printf '%s\n' "$trend" | grep '^  calls:' || true)
  assert_contains "$calls_line" "below the recorded floor of 2 per turn: #22 0.5" \
    "and names the item below the recorded floor, with its own ratio"
  assert_not_contains "$calls_line" "#21" "while the item above the floor is not named"
  row_batched=$(printf '%s\n' "$out" | grep '^| #21 |' || true)
  row_chained=$(printf '%s\n' "$out" | grep '^| #22 |' || true)
  [ -n "$row_batched" ] || fail "no table row for #21"
  assert_eq "3" "$(table_cell "$row_batched" 17)" "the row carries the item's own calls per turn"
  assert_eq "0.5" "$(table_cell "$row_chained" 17)" "in a column appended after the token band"
  assert_contains "$out" "closed 2 · mis-diagnosed 0 · skipped 0 · blocked 0" \
    "the floor is a warning: both items keep the outcome their work earned"
  # A project that records no floor flags nothing, and the line says the floor is off rather than printing
  # `none` — which would read as a measurement nobody made, the rule every spend column keeps. The negative half
  # is half the assertion: a floor that is off names no item, exactly as the token band's own case reads both.
  sed -e 's/^LOOP_ITEM_CALLS_PER_TURN_MIN=.*/LOOP_ITEM_CALLS_PER_TURN_MIN=0/' "$conf" > "$d/off.conf"
  export LOOP_CONF="$d/off.conf"
  out=$(run_report "$p") || fail "the report failed with the floor off"
  assert_contains "$out" "floor off — LOOP_ITEM_CALLS_PER_TURN_MIN records 0, so no item is flagged" \
    "a floor of 0 is off, and the trend says so"
  calls_line=$(printf '%s\n' "$out" | grep '^  calls:' || true)
  assert_not_contains "$calls_line" "#22" "and a floor that is off names no item"
  # A state that recorded no turn prints `-` with its reason, never a zero that reads as a measurement. The run is
  # stopped before the state is replaced: `init` keeps the state it replaces, and a run that ended without recording
  # why is a banner every replacement prints — noise the assertions below would be read through.
  run_state "$p" stop "the case ended the run between its states" >/dev/null
  init_state "$p" 1 90
  printf '[{"issue":31,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  run_state "$p" done 31 --result blocked --verdict blocked >/dev/null
  out=$(run_report "$p") || fail "the report failed for a state with no event stream"
  assert_contains "$out" "calls:  - · no item recorded a turn" \
    "an item that reported no turn prints a dash and the reason, not a zero"
  row_empty=$(printf '%s\n' "$out" | grep '^| #31 |' || true)
  [ -n "$row_empty" ] || fail "no table row for #31"
  assert_eq "-" "$(table_cell "$row_empty" 17)" "the column says the same for that item"
  # A state written before this figure existed records tokens for its items and no counts at all, and the dash it
  # prints says so rather than naming a run of items that answered without a tool — the distinction the review
  # rate's dash already makes between "nobody measured" and "this state predates the field". `metrics.tokens` is
  # what such a state holds instead, so the case writes it: the point is the state's shape, not the stream's.
  run_state "$p" stop "the case ended the run between its states" >/dev/null
  init_state "$p" 1 90
  printf '[{"issue":41,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  run_state "$p" done 41 --result done --verdict closed --events "$d/batched.jsonl" >/dev/null
  f=$(state_file "$p")
  jq 'del(.stateDigest)
      | (.items[0].metrics.turns, .items[0].metrics.calls) = null
      | .items[0].metrics.tokens = {input: 1000, output: 100, cacheRead: 500, cacheWrite: 0}' "$f" > "$f.old" && mv "$f.old" "$f"
  out=$(run_report "$p") || fail "the report failed for a state written before the figure existed"
  assert_contains "$out" "calls:  - · this state records no calls or turns — it was written before the report read them" \
    "a state that predates the two counts says so, instead of naming a run of items that answered without a tool"
}

# The report totalled a run's spend and never said which phase spent it (issue #759): a run that paid two thirds
# of its clock inside the configured gate command and a run that paid it reading printed the same shapes, which is
# the defect `LOOP_ITEM_TOKENS_WARN` was added to cure one level down (#662) — the number was printed beside nothing
# it could be judged against. The three figures the trend now carries are read from what the item's own stream
# records: the children's `turn_end` durations (the model time), the `tool_execution_end` durations of the calls that
# ran the gate and of every other tool, the tokens of each session split by the role its prompt names, and the share
# of them spent from the turn that first ran a gate. The stream below is one item: a worker that reads, runs the
# gate and answers, a reviewer that reads once and a tail that answers — 7 500 ms of model time, 5 000 ms in the
# gate, 300 ms in the one read, 6 + 1 + 1 Mtok by role and 5 of the worker's 6 Mtok after its first gate.
case_report_theSpendIsSplitByPhaseRoleAndGate() {
  # files: tools/loop/gate.sh tools/loop/loop.conf tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local d p f out trend phases_line roles_line gate_line
  d=$(scratch); p=$(new_project)
  cat > "$d/split.jsonl" <<'JSONL'
{"type":"session","version":3,"id":"s1"}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"/items/21/worker-prompt.md"}]}}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":0,"cacheRead":1000000,"cacheWrite":0},"durationMs":1000,"content":[{"type":"toolCall","id":"c1","name":"ctx_read","arguments":{"path":"a"}}]},"toolResults":[]}
{"type":"tool_execution_end","toolCallId":"c1","toolName":"ctx_read","durationMs":300,"isError":false}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":0,"cacheRead":3000000,"cacheWrite":0},"durationMs":2000,"content":[{"type":"toolCall","id":"c2","name":"ctx_shell","arguments":{"command":"./tools/loop/gate.sh"}}]},"toolResults":[]}
{"type":"tool_execution_end","toolCallId":"c2","toolName":"ctx_shell","durationMs":5000,"isError":false}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":0,"cacheRead":2000000,"cacheWrite":0},"durationMs":3000,"content":[]},"toolResults":[]}
{"type":"session","version":3,"id":"s2"}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"/items/21/reviewer-prompt-1.md"}]}}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":0,"cacheRead":1000000,"cacheWrite":0},"durationMs":500,"content":[]},"toolResults":[]}
{"type":"session","version":3,"id":"s3"}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"/items/21/worker-tail-prompt.md"}]}}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":0,"cacheRead":1000000,"cacheWrite":0},"durationMs":1000,"content":[]},"toolResults":[]}
JSONL
  init_state "$p" 1 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result done --verdict closed --wall 42 --events "$d/split.jsonl" >/dev/null
  f=$(state_file "$p")
  # The split is recorded where the money is spent — in the item's own stream, at the moment its record is written —
  # so the report derives the three lines from the state and never from a stream it would have to find again.
  assert_eq "7500" "$(jq -r '.items[0].metrics.phases.modelMs' "$f")" \
    "the model time is the sum of the children's own turn durations"
  assert_eq "5000" "$(jq -r '.items[0].metrics.phases.gateMs' "$f")" \
    "the gate time is the durations of the calls that ran the gate, not of every tool"
  assert_eq "300" "$(jq -r '.items[0].metrics.phases.otherMs' "$f")" "and every other tool is the third bucket"
  assert_eq "6000000" "$(jq -r '.items[0].metrics.roles.worker' "$f")" \
    "the tokens are split by the role each session's prompt names"
  assert_eq "1000000" "$(jq -r '.items[0].metrics.roles.reviewer' "$f")" "the reviewer's own session is told apart"
  assert_eq "1000000" "$(jq -r '.items[0].metrics.roles.tail' "$f")" \
    "and so is the tail's, which a role read from the word 'reviewer' alone would fold into the worker's"
  assert_eq "5000000" "$(jq -r '.items[0].metrics.afterGate.tokens' "$f")" \
    "the share after the first gate counts the turns from the gate on, in the sessions that ran one"
  assert_eq "6000000" "$(jq -r '.items[0].metrics.afterGate.total' "$f")" \
    "against those sessions' own tokens, not against every session of the item"
  out=$(run_report "$p") || fail "the report failed"
  trend=$(printf '%s\n' "$out" | sed -n "/^trend /,/^  landings:/p")
  phases_line=$(printf '%s\n' "$trend" | grep '^  phases:' || true)
  [ -n "$phases_line" ] || fail "the trend printed no phases line"
  assert_contains "$phases_line" "model 7.5 s · gate 5 s · other 0.3 s · 12.8 s over 1 item" \
    "the trend splits the run's clock by the phase that spent it, and totals it"
  roles_line=$(printf '%s\n' "$trend" | grep '^  roles:' || true)
  [ -n "$roles_line" ] || fail "the trend printed no roles line"
  assert_contains "$roles_line" "worker 6 Mtok · reviewer 1 Mtok · tail 1 Mtok · 8 Mtok over 1 item" \
    "and its tokens by the role that spent them, with the figure each one is a share of"
  gate_line=$(printf '%s\n' "$trend" | grep '^  after the first gate:' || true)
  [ -n "$gate_line" ] || fail "the trend printed no after-the-gate line"
  assert_contains "$gate_line" "83 % — 5 of 6 Mtok, from the first gate of 1 session" \
    "and the share of the item's tokens spent after its first gate, with the two numbers it is a share of"
}

# A figure nobody measured prints `-` with its reason, never a zero that reads as a measurement — the rule the
# spend columns keep (`report#zeroUsagePrintsDashNotZero`), applied to the three figures above: a stream that
# records no duration and no usage measures none of them, and a state written before the split existed says so
# rather than naming a run whose phases all cost nothing.
case_report_aStreamWithoutDurationsPrintsDash() {
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local d p f out trend phases_line roles_line gate_line
  d=$(scratch); p=$(new_project)
  # A stream of the shape a provider or a harness that reports nothing leaves: turns and calls, no duration, no
  # usage, no gate.
  cat > "$d/bare.jsonl" <<'JSONL'
{"type":"session","version":3,"id":"s1"}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"/items/31/worker-prompt.md"}]}}
{"type":"turn_end","message":{"role":"assistant","content":[{"type":"toolCall","id":"c1","name":"ctx_read","arguments":{"path":"a"}}]},"toolResults":[]}
{"type":"tool_execution_end","toolCallId":"c1","toolName":"ctx_read","isError":false}
JSONL
  init_state "$p" 1 90
  printf '[{"issue":31,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  run_state "$p" done 31 --result done --verdict closed --wall 42 --events "$d/bare.jsonl" >/dev/null
  f=$(state_file "$p")
  assert_eq "null" "$(jq -r '.items[0].metrics.phases' "$f")" "a stream that reports no duration records no phase figure"
  assert_eq "null" "$(jq -r '.items[0].metrics.roles' "$f")" "and no role figure either"
  out=$(run_report "$p") || fail "the report failed"
  trend=$(printf '%s\n' "$out" | sed -n "/^trend /,/^  landings:/p")
  phases_line=$(printf '%s\n' "$trend" | grep '^  phases:' || true)
  assert_contains "$phases_line" "- · no item recorded a phase duration" \
    "the trend prints a dash and its reason, not three zeros"
  assert_not_contains "$phases_line" "0 s" "a phase nobody measured is not printed as a zero"
  roles_line=$(printf '%s\n' "$trend" | grep '^  roles:' || true)
  assert_contains "$roles_line" "- · no item recorded a session role" "and the same for the role split"
  gate_line=$(printf '%s\n' "$trend" | grep '^  after the first gate:' || true)
  assert_contains "$gate_line" "- · no item of this run ran a gate in its stream" \
    "and for the share a gate nobody ran has no position to split at"
  assert_not_contains "$gate_line" "%" "no percentage is invented over a zero"
  # A state written before the split was recorded holds the tokens and no phase figure, and says so rather than
  # naming a run whose phases each cost nothing — the distinction the calls line already makes.
  run_state "$p" stop "the case ended the run between its states" >/dev/null
  init_state "$p" 1 90
  printf '[{"issue":41,"class":"bug","verdict":"eligible","rank":1}]' | run_state "$p" queue >/dev/null
  cat > "$d/used.jsonl" <<'JSONL'
{"type":"session","version":3,"id":"s1"}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"/items/41/worker-prompt.md"}]}}
{"type":"message_update","usage":{"input":0,"output":0,"cacheRead":1000000,"cacheWrite":0}}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":0,"cacheRead":1000000,"cacheWrite":0},"durationMs":1000,"content":[]},"toolResults":[]}
JSONL
  run_state "$p" done 41 --result done --verdict closed --events "$d/used.jsonl" >/dev/null
  f=$(state_file "$p")
  jq 'del(.stateDigest) | (.items[] | .metrics.phases) = null | (.items[] | .metrics.roles) = null
      | (.items[] | .metrics.afterGate) = null' "$f" > "$f.old" && mv "$f.old" "$f"
  out=$(run_report "$p") || fail "the report failed for a state written before the split existed"
  trend=$(printf '%s\n' "$out" | sed -n "/^trend /,/^  landings:/p")
  assert_contains "$trend" "phases: - · this state records no phase durations — it was written before the report read them" \
    "a state that predates the split says so, instead of naming a run whose phases cost nothing"
  assert_contains "$trend" "after the first gate: - · this state records no gate share — it was written before the report read one" \
    "and the same for the share after the gate"
}

# Whether the loop is getting more often right is not a question one run's rows answer: a run that cost what the
# last one cost prints the same shapes as one that is improving, so the figures the state records per item are
# totalled here (issue #247). The pass rate is counted from the round's **verdict**, not from `reviewRounds`:
# `run.sh:1680` sets the count before the reviewer runs, so an item whose round 1 refused and then blocked records
# `1` exactly as one whose round 1 passed — measured on the tree's own state, #16 and #197 are blocked that way
# against `VERDICT: FAIL` reports, and #51 carries the same count for a round 1 that was cut at the turn bound, with
# no `VERDICT:` line at all (review rounds 1 and 2, blocking). The state therefore records the verdict
# (`done --review-verdict`), and the buckets are told apart: a round 1 that passed, a round 1 that refused and whose
# round 2 passed, a round that ended without a verdict, and an item no round ran for.
case_report_trendFiguresFromState() {
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out trend
  p=$(new_project)
  init_state "$p" 8 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2},{"issue":23,"class":"bug","verdict":"eligible","rank":3},{"issue":24,"class":"bug","verdict":"eligible","rank":4},{"issue":25,"class":"bug","verdict":"eligible","rank":5},{"issue":26,"class":"bug","verdict":"eligible","rank":6},{"issue":27,"class":"bug","verdict":"eligible","rank":7},{"issue":28,"class":"bug","verdict":"eligible","rank":8}]' |
    run_state "$p" queue >/dev/null
  # Two round-1 passes, one item that passed on round 2, two round 1s that were REFUSED (one of them the last
  # round it got), one round that ended without a verdict, one item with review off and one never reviewed.
  run_state "$p" done 21 --result done --verdict closed --wall 41 --rounds 1 --review-verdict PASS >/dev/null
  run_state "$p" done 22 --result done --verdict closed --wall 200 --rounds 1 --review-verdict NOTES >/dev/null
  run_state "$p" done 23 --result done --verdict closed --wall 403 --rounds 2 --review-verdict PASS >/dev/null
  run_state "$p" done 24 --result blocked --verdict blocked --wall 900 --rounds 1 --review-verdict FAIL >/dev/null
  run_state "$p" done 25 --result blocked --verdict blocked --wall 1644 --rounds 1 --review-verdict round2-ended >/dev/null
  run_state "$p" done 26 --result blocked --verdict blocked --rounds 1 --review-verdict cut-short >/dev/null
  run_state "$p" done 27 --result done --verdict closed --rounds 0 --review-verdict off >/dev/null
  run_state "$p" done 28 --result blocked --verdict blocked --rounds 0 >/dev/null
  out=$(run_report "$p") || fail "the report failed"
  trend=$(printf '%s\n' "$out" | sed -n "/^trend /,/^  landings:/p")
  assert_contains "$trend" "review: 2/5 first rounds passed · 1 undecided · 2 never reviewed" \
    "the rate counts the verdicts a round ended on: two passes of five decided rounds, one round without a verdict, two items no round ran for"
  assert_not_contains "$trend" "5/5" \
    "a round 1 that refused and then blocked, and a round that was cut, are not passes — the count-based numerator prints 5/5"
  assert_contains "$trend" "wall:   median 403 s (min 41, max 1644) · 5 items" \
    "the wall figures are the recorded walls, with the count printed beside them"
  assert_not_contains "$trend" "0/0" "the rate is never printed as a zero over a zero"
  # A state with an even number of walls takes the mean of the two middle values: 150.5 s prints as 151 s, because
  # the state records fractional seconds and a report that repeated them would imply a precision the loop has not.
  init_state "$p" 2 90
  printf '[{"issue":31,"class":"bug","verdict":"eligible","rank":1},{"issue":32,"class":"bug","verdict":"eligible","rank":2}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 31 --result done --verdict closed --wall 100.25 --rounds 1 --review-verdict PASS >/dev/null
  run_state "$p" done 32 --result done --verdict closed --wall 200.75 --rounds 1 --review-verdict PASS >/dev/null
  out=$(run_report "$p") || fail "the report failed for the even count"
  assert_contains "$out" "wall:   median 151 s (min 100, max 201) · 2 items" \
    "an even count takes the mean of the two middle walls, rounded to whole seconds"
}

# A figure nobody measured prints `-` with its reason, never a zero that reads as a measurement — the rule the
# spend columns keep (`report#zeroUsagePrintsDashNotZero`), applied twice: a run whose item was blocked before a
# review round ran and recorded no wall time, and a state written before the state recorded the round's verdict,
# whose round count alone cannot tell a pass from a refusal.
case_report_noRoundOrWallPrintsDashNotZero() {
  # files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out trend
  p=$(new_project)
  init_state "$p" 2 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 21 --result blocked --verdict blocked >/dev/null
  # #22 is a state written before the verdict was recorded: a round ran, and nothing says how it ended.
  run_state "$p" done 22 --result blocked --verdict blocked --rounds 1 >/dev/null
  out=$(run_report "$p") || fail "the report failed"
  trend=$(printf '%s\n' "$out" | sed -n "/^trend /,/^  landings:/p")
  assert_contains "$trend" "review: - · this state records no review verdict" \
    "a state whose items carry no verdict prints a dash and the reason, not a rate over a count that cannot tell a pass from a refusal"
  assert_contains "$trend" "wall:   - · no item recorded a wall time" "no wall and no median: the same"
  assert_not_contains "$trend" "median 0" "a median nobody measured is not printed as a zero"
  assert_not_contains "$trend" "0/0" "nor is a rate"
  # The same figures once the verdicts are recorded — a fresh state, because a second account of one item joins
  # its note and never rewrites its metrics (#284): no round ended in a verdict, and the buckets the report does
  # hold are named rather than folded into a zero.
  init_state "$p" 2 90
  printf '[{"issue":31,"class":"bug","verdict":"eligible","rank":1},{"issue":32,"class":"bug","verdict":"eligible","rank":2}]' |
    run_state "$p" queue >/dev/null
  run_state "$p" done 31 --result blocked --verdict blocked --review-verdict off >/dev/null
  run_state "$p" done 32 --result blocked --verdict blocked --rounds 1 --review-verdict deadline >/dev/null
  out=$(run_report "$p") || fail "the report failed with a verdict recorded"
  trend=$(printf '%s\n' "$out" | sed -n "/^trend /,/^  landings:/p")
  assert_contains "$trend" "review: - · no round of this run ended in a verdict · 1 undecided · 1 never reviewed" \
    "a round the run's clock ended is undecided, named apart from the item no round ran for"
  assert_not_contains "$trend" "0/0" "and still no zero over a zero"
}

# The third figure the item asks for — the landings that reddened `main` — is the number the state now holds:
# each landing records the commit its merge made and the score of the push run that commit caused, read once by the
# state's writer (issue #709, which is the item `run-report-trend-figures` deferred it to). The report still reads
# the state and never the tracker — a `gh` on PATH that logs and fails is never called, which is the rule issue
# #247 set and this line keeps.
case_report_landingsAreTheStoredNumber() {
  # files: tools/loop/report.sh tools/loop/state.sh tools/loop/fixtures/scenarios/ tools/pi-child-events.sh tools/tooling.json
  local p d sf out landings red green absent
  p=$(new_project); d=$(scratch)
  red="a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"
  green="b1b2c3d4e5f60718293a4b5c6d7e8f9012345678"
  absent="c1c2c3d4e5f60718293a4b5c6d7e8f9012345678"
  init_state "$p" 4 90
  printf '[{"issue":21,"class":"bug","verdict":"eligible","rank":1},{"issue":22,"class":"bug","verdict":"eligible","rank":2},{"issue":23,"class":"bug","verdict":"eligible","rank":3},{"issue":24,"class":"bug","verdict":"eligible","rank":4}]' |
    run_state "$p" queue >/dev/null
  sf=$(state_file "$p")
  run_state "$p" done 21 --result done --verdict closed --wall 41 --rounds 1 \
    --merged-branch fix/issue-21-a --merged-commits 1111111 --merged-sha "$red" >/dev/null ||
    fail "the first landing was refused"
  run_state "$p" done 22 --result done --verdict closed --wall 42 --rounds 1 \
    --merged-branch fix/issue-22-b --merged-commits 2222222 --merged-sha "$green" >/dev/null ||
    fail "the second landing was refused"
  run_state "$p" done 23 --result done --verdict closed --wall 43 --rounds 1 \
    --merged-branch fix/issue-23-c --merged-commits 3333333 --merged-sha "$absent" >/dev/null ||
    fail "the third landing was refused"
  # #24 is a landing a state written before the commit was kept records: a branch, its commits and no commit.
  run_state "$p" done 24 --result done --verdict closed --wall 44 --rounds 1 \
    --merged-branch fix/issue-24-d --merged-commits 4444444 >/dev/null || fail "the fourth landing was refused"
  ( cd "$p" && GH_STUB_DIR="$(scenario landed)" bash "$LOOP_ENGINE/state.sh" score-landings >/dev/null 2>&1 ) ||
    fail "the score failed"
  assert_eq "true" "$(jq -r '.items[] | select(.issue == 21) | .landedReddenedMain' "$sf")" "the first landing is scored red"
  assert_eq "null" "$(jq -r '.items[] | select(.issue == 23) | .landedReddenedMain' "$sf")" \
    "the third has no run to read, so it stays unscored"
  # Every `gh` invocation from here on lands in the record file, so a report that read the tracker leaves a trace.
  mkdir -p "$d/bin"
  printf '#!/usr/bin/env bash\nprintf "%%s\\n" "$*" >> "%s"\nexit 1\n' "$d/gh-calls.log" > "$d/bin/gh"
  chmod +x "$d/bin/gh"
  export PATH="$d/bin:$PATH"
  out=$(run_report "$p") || fail "the report failed with a gh on PATH"
  landings=$(printf '%s\n' "$out" | sed -n '/^  landings:/p')
  [ -n "$landings" ] || fail "the report printed no landings line"
  assert_contains "$landings" "1 of 2 reddened main" \
    "the number is the score the state stores: one of the two landings a run has read reddened the base"
  assert_contains "$landings" "1 unscored" "a landing whose run nobody has read is named apart, not counted green"
  assert_contains "$landings" "1 records no commit" "and so is a landing recorded before the commit was kept"
  assert_not_contains "$landings" "gh issue list" "the line points at no tracker read"
  assert_not_contains "$landings" "#21" "it invents no id it never read"
  [ ! -e "$d/gh-calls.log" ] || fail "the report called the tracker: $(cat "$d/gh-calls.log")"
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

# The item directories are the loop's largest leftover: one per item, holding the stream every child of it appended to,
# and no arm of this report named them (issue #802; measured 2026-11-06, `.pi/issue-loop/` held 3.6 GB over 264 of
# them). They are also the loop's evidence, so a directory is released only when nothing inside it has been written
# for `LOOP_ITEM_ARCHIVE_DAYS` **and** the run recorded the item landed and its issue closed — an item whose issue is
# still open, or whose landing the run was not allowed to close, is kept and named. `--apply` is still the only path
# that removes one, and a `0` retention releases nothing.
case_clean_reportsAnAgedItemDirectory() {
  # files: tools/loop/loop-clean.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p sf items conf out n1 n2
  p=$(new_project)
  init_state "$p" 1 90
  sf=$(state_file "$p")
  items="$p/.pi/issue-loop/items"
  mkdir -p "$items/41" "$items/42" "$items/43" "$items/44"
  head -c 4096 /dev/zero > "$items/41/events.jsonl"
  head -c 2048 /dev/zero > "$items/42/events.jsonl"
  head -c 1024 /dev/zero > "$items/43/events.jsonl"
  head -c 512 /dev/zero > "$items/44/events.jsonl"
  touch -d '40 days ago' "$items/41/events.jsonl" "$items/41" \
                            "$items/42/events.jsonl" "$items/42" \
                            "$items/43/events.jsonl" "$items/43"
  # The state is the reading the arm asks: 41 landed and closed, 42 landed but the issue could not be closed, 43 never
  # landed, 44 landed and closed but written a minute ago.
  jq '.items = [
    {"issue": 41, "result": "done", "metrics": {"closed": "yes"}},
    {"issue": 42, "result": "done", "metrics": {"closed": "no"}},
    {"issue": 43, "result": "blocked", "metrics": {"closed": "no"}},
    {"issue": 44, "result": "done", "metrics": {"closed": "yes"}}]' "$sf" > "$sf.new" &&
    mv "$sf.new" "$sf" || fail "could not record the four items in the run's state"

  # A retention of `0` is the switch: the arm reports and removes nothing.
  conf="$p/loop-off.conf"
  # The line is written whether or not the configuration already carries it, so the case fails on the arm's behaviour
  # and never on a key its own tree has not recorded yet.
  { sed 's/^LOOP_ITEM_ARCHIVE_DAYS=.*/LOOP_ITEM_ARCHIVE_DAYS=0/' "$LOOP_CONF"; printf 'LOOP_ITEM_ARCHIVE_DAYS=0\n'; } > "$conf" ||
    fail "could not record the retention off"
  grep -qx 'LOOP_ITEM_ARCHIVE_DAYS=0' "$conf" || fail "this case could not switch the retention off in loop.conf"
  out=$(LOOP_CONF="$conf" run_clean "$p" --apply) || fail "loop-clean failed with the retention off: $out"
  assert_contains "$out" "kept every item" "a 0 retention releases nothing"
  [ -d "$items/41" ] || fail "a 0 retention removed an item directory"

  out=$(run_clean "$p") || fail "loop-clean failed: $out"
  assert_contains "$out" "would remove items/41" "the landed, closed and aged item is reported"
  assert_contains "$out" "kept items/42 — the issue it belongs to is not closed" \
    "an item whose issue is still open is kept and named"
  assert_contains "$out" "kept items/43" "and one that never landed is kept too"
  assert_contains "$out" "kept items/44 — written within 14 days" "a freshly written item is kept by its age"
  assert_not_contains "$out" "would remove items/42" "a kept item is never named as removable"
  [ -d "$items/41" ] || fail "the report removed an item directory without --apply"
  n1=$(printf '%s\n' "$out" | sed -n 's/^loop-clean: would remove [0-9][0-9]* path(s), \([0-9][0-9]*\) B.*/\1/p' | head -1)
  [ -n "$n1" ] || fail "the report names no total: $out"
  [ "$n1" -ge 4096 ] || fail "the report undercounts the item directory it would remove: $n1 B"

  out=$(run_clean "$p" --apply) || fail "loop-clean --apply failed: $out"
  assert_contains "$out" "removed items/41" "--apply removes the item it reported"
  [ ! -e "$items/41" ] || fail "the aged, landed item directory survived --apply"
  [ -d "$items/42" ] || fail "--apply removed an item whose issue is still open"
  [ -d "$items/43" ] || fail "--apply removed an item that never landed"
  [ -d "$items/44" ] || fail "--apply removed an item younger than the retention"
  n2=$(printf '%s\n' "$out" | sed -n 's/^loop-clean: removed [0-9][0-9]* path(s), \([0-9][0-9]*\) B.*/\1/p' | head -1)
  [ "$n1" -le "$n2" ] || fail "--apply removed less than the report named: $n1 B reported, $n2 B removed"
  return 0
}

# ------------------------------------------------------------- cases: the change an item names (`agent-issue-loop`)

# The worktree is cut from the base and an unarchived change is gitignored, so the change a person wrote and gated
# reaches the worker only because the run copies it in at the item's start. The copy is whole, is recorded in the
# state, and follows no symlink out of the source directory.
case_worktree_theNamedChangeIsCopiedIntoTheItem() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/state.sh tools/loop/worktree.sh tools/check-change-readiness.sh tools/pi-child-events.sh tools/tooling.json
  local p w out record
  p=$(new_project); w="$p/.pi/worktrees/loop"
  tier_b_change "$p" add-prepared-change
  ln -s /etc/hostname "$p/openspec/changes/add-prepared-change/escape-link" ||
    fail "could not create the fixture's symlink"
  init_state "$p" 1 90
  run_worktree "$p" create --branch loop-test >/dev/null 2>&1 || fail "worktree create failed"

  out=$(run_worktree "$p" start-item 31 a-prepared-change --change add-prepared-change) ||
    fail "start-item refused the prepared change: $out"
  [ -f "$w/openspec/changes/add-prepared-change/proposal.md" ] ||
    fail "the change's proposal was not copied into the worktree"
  [ -f "$w/openspec/changes/add-prepared-change/specs/demo/spec.md" ] ||
    fail "the change's spec delta was not copied into the worktree"
  [ -f "$w/openspec/changes/add-prepared-change/evidence/readme.txt" ] ||
    fail "the change's evidence directory was not copied into the worktree"
  [ -e "$w/openspec/changes/add-prepared-change/escape-link" ] &&
    fail "the copy recreated a symlink out of the change directory"

  record=$(run_state "$p" get '.changeCopies["31"]') || fail "the copy was not recorded in the run's state"
  assert_eq "openspec/changes/add-prepared-change" "$(printf '%s' "$record" | jq -r '.source')" \
    "the recorded source"
  [ "$(printf '%s' "$record" | jq -r '.files')" -ge 4 ] ||
    fail "the recorded file count does not name what was copied: $record"
  [ "$(printf '%s' "$record" | jq -r '.bytes')" -gt 0 ] || fail "the recorded size is not a measurement: $record"
  assert_contains "$out" "change: openspec/changes/add-prepared-change copied into the worktree" \
    "the start names the copy"
}

# A change name no directory holds is a refusal before a branch or a child exists — never an empty directory, which
# would hand a worker a change with nothing in it.
case_worktree_aMissingChangeRefusesTheItem() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/state.sh tools/loop/worktree.sh tools/pi-child-events.sh tools/tooling.json
  local p w out status
  p=$(new_project); w="$p/.pi/worktrees/loop"
  export PI_STUB_LOG="$p/pi.log"
  init_state "$p" 1 90
  run_worktree "$p" create --branch loop-test >/dev/null 2>&1 || fail "worktree create failed"

  out=$(run_worktree "$p" start-item 31 a-prepared-change --change add-absent 2>&1); status=$?
  assert_eq "5" "$status" "a change the tree does not hold refuses the item"
  assert_contains "$out" "openspec/changes/add-absent" "the refusal names the path the run looked for"
  assert_eq "" "$(git -C "$w" branch --format='%(refname:short)' | grep '^fix/issue-31-' || true)" \
    "no branch was cut for a refused item"
  [ -e "$w/openspec/changes/add-absent" ] && fail "the refusal created an empty change directory"
  [ -e "$PI_STUB_LOG" ] && fail "a child process was started for a refused item"
  assert_eq "" "$(run_state "$p" get '.changeCopies["31"] // ""')" "a refused copy is not recorded"
}

# A tier-A item names no change and is worked exactly as it was: the pre-screen queues it and the run plans it.
case_prescreen_aTierAItemPassesUnchanged() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/pi-child-events.sh tools/tooling.json
  local p out status
  p=$(new_project)
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err"); status=$?
  assert_eq "0" "$status" "a tier-A item is still queued"
  assert_contains "$out" '"issue":21' "the tier-A item is a candidate"
  assert_eq "0" "$(printf '%s' "$out" | jq '[.[] | select(.verdict == "change-not-ready")] | length')" \
    "no tier-B branch fires for an item that names no change"
  assert_eq "1" "$(jq -r '.queue | length' "$(state_file "$p")")" "the item reaches the queue"
}

# The two script-decidable facts about a tier-B item are asked before any worktree is cut: the change directory is
# there, and the profile's readiness checker still reports `ok`. Either failing is a refusal with the checker's own
# verdict line on the item, not a hold.
case_prescreen_aChangeThatIsNoLongerReadyIsRefused() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/check-change-readiness.sh tools/pi-child-events.sh tools/tooling.json
  local p out status record tracker
  p=$(new_project)
  tier_b_change "$p" add-prepared-change unready
  init_state "$p" 3 90
  # The tracker is copied out of the fixture so the case can make its read answer with the comment a previous run
  # wrote: the stub records a write instead of storing it, and a real `gh` would answer the next read with it.
  tracker="$p/tracker"; cp -r "$(scenario tier-b)" "$tracker"
  export GH_STUB_DIR="$tracker"
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err"); status=$?
  assert_eq "3" "$status" "an item whose change is no longer ready leaves nothing eligible"
  record=$(printf '%s' "$out" | jq -c '.[] | select(.issue == 31)')
  assert_eq "change-not-ready" "$(printf '%s' "$record" | jq -r '.verdict')" "its verdict is the refusal"
  assert_contains "$(printf '%s' "$record" | jq -r '.reason')" "LOOP-CHANGE-READY: refused open-decision=design.md:5" \
    "the checker's own verdict line is the reason"
  assert_eq "openspec/changes/add-prepared-change" "$(printf '%s' "$record" | jq -r '.evidence')" \
    "the evidence names the change source"
  assert_eq "0" "$(jq -r '.queue | length' "$(state_file "$p")")" "no child is started for it"
  # The refusal reaches the item, and it is one comment per refusal: the same verdict line again writes nothing.
  assert_contains "$(cat "$p/gh.log")" "issue comment 31" "the verdict line was written on the item"
  assert_contains "$(cat "$p/gh.log")" "open-decision=design.md:5" "the comment carries the checker's line"
  jq '.[0].comments = ((.[0].comments // []) + [{author: {login: "Framstag"}, createdAt: "2026-10-31T00:00:00Z",
        body: "LOOP-CHANGE-READY: refused open-decision=design.md:5"}])' \
    "$tracker/issues.json" > "$tracker/issues.json.new" && mv "$tracker/issues.json.new" "$tracker/issues.json" ||
    fail "could not stand the tracker's persisted comment in"
  run_prescreen "$p" --json >/dev/null 2>&1 || true
  assert_eq "1" "$(grep -c 'issue comment 31' "$p/gh.log" || true)" "a second run repeats no refusal"
}

# The closure, not the leftover's absence, is what stops a second consume: a consumed item is not selected again
# while its source directory is still on disk.
case_prescreen_aClosedItemIsNotSelected() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/prescreen.sh tools/loop/state.sh tools/check-change-readiness.sh tools/pi-child-events.sh tools/tooling.json
  local p out
  p=$(new_project)
  tier_b_change "$p" add-prepared-change
  init_state "$p" 3 90
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario tier-b)
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err") || fail "a ready change is queued: $out"  assert_contains "$out" '"issue":31' "the tier-B item is a candidate while it is open"

  run_state "$p" done 31 --result done --change add-prepared-change >/dev/null ||
    fail "could not record the item as landed"
  out=$(run_prescreen "$p" --json 2>"$p/prescreen.err") || true
  # The pre-screen's own record still names the candidate — the tracker has not moved — and the run's queue is what
  # decides what is worked; `state.sh queue` drops an item it has recorded.
  assert_eq "0" "$(jq -r '.queue | length' "$(state_file "$p")")" "the consumed item is not selected again"
  [ -d "$p/openspec/changes/add-prepared-change" ] ||
    fail "the case needs the source still on disk to prove the closure and not the leftover does the work"
}

# The change reaches the child as a block of its own, never in an instruction position, and the recorded decision
# travels inside it.
case_prompt_thePackageCarriesTheChange() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/check-change-readiness.sh tools/pi-child-events.sh tools/tooling.json
  local d p package
  d=$(scratch); p=$(new_project)
  tier_b_change "$p" add-prepared-change
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario tier-b)
  stub_success "$d"
  run_loop "$p" --items 1 --minutes 90 >/dev/null 2>&1 || true
  package="$p/.pi/issue-loop/items/31/work-package.md"
  [ -f "$package" ] || fail "no work package was written for the tier-B item"
  assert_eq "1" "$(grep -c '^<<<ITEM_CHANGE$' "$package" || true)" "exactly one opening delimiter"
  assert_eq "1" "$(grep -c '^ITEM_CHANGE$' "$package" || true)" "exactly one closing delimiter"
  assert_contains "$(cat "$package")" "change: add-prepared-change" "the block names the change"
  assert_contains "$(cat "$package")" \
    "path: $p/.pi/worktrees/loop/openspec/changes/add-prepared-change" "the block names its path in the worktree"
  assert_contains "$(cat "$package")" "readiness: LOOP-CHANGE-READY: ok" "the block quotes the readiness verdict"
  assert_contains "$(cat "$package")" "decisions: 1" "and the decision a person recorded"
  [ -f "$p/.pi/worktrees/loop/openspec/changes/add-prepared-change/design.md" ] ||
    fail "the change was not in the worktree the worker was handed"
}

# A tier-A item's package says so, because an absent block is a half-prepared package a reader cannot tell from a
# missing one.
case_prompt_aTierAItemSaysSo() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p package
  d=$(scratch); p=$(new_project)
  run_one_item "$p" "$d" >/dev/null || fail "the run failed"
  package="$p/.pi/issue-loop/items/21/work-package.md"
  [ -f "$package" ] || fail "the work package was not written"
  assert_eq "1" "$(grep -c '^<<<ITEM_CHANGE$' "$package" || true)" "exactly one opening delimiter"
  assert_eq "1" "$(grep -c '^ITEM_CHANGE$' "$package" || true)" "exactly one closing delimiter"
  assert_contains "$(cat "$package")" "change: none" "the block states that the item names no change"
  assert_eq "1" "$(grep -c '^change: ' "$package" || true)" "and states nothing else in its place"
}

# A worker handed a change verifies it and applies it; it creates no second change directory for the item.
case_run_aTierBItemWritesNoSecondChange() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/state.sh tools/check-change-readiness.sh tools/pi-child-events.sh tools/tooling.json
  local d p w prompt package
  d=$(scratch); p=$(new_project); w="$p/.pi/worktrees/loop"
  tier_b_change "$p" add-prepared-change
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario tier-b)
  stub_success "$d"
  run_loop "$p" --items 1 --minutes 90 >/dev/null 2>&1 || true
  prompt="$p/.pi/issue-loop/items/31/worker-prompt.md"
  package="$p/.pi/issue-loop/items/31/work-package.md"
  [ -f "$prompt" ] || fail "the worker prompt was not rendered"
  assert_contains "$(cat "$prompt")" "ITEM_CHANGE" "the worker is told where it finds the change it was handed"
  assert_contains "$(cat "$prompt")" "do not write a second" "and forbidden to plan a second one"
  assert_contains "$(cat "$package")" "change: add-prepared-change" "the package names the change"
  assert_eq "add-prepared-change archive" \
    "$(find "$w/openspec/changes" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort | tr '\n' ' ' | sed 's/ $//')" \
    "the prepared change is the only change the item wrote (the archive is the worker's own)"
  assert_eq "openspec/changes/add-prepared-change" \
    "$(run_state "$p" get '.changeCopies["31"].source')" "the run recorded the copy it made"
}

# A fork the change's declaration does not settle is a decision naming that fork and the change's line — never a
# re-derived option list, which is how a recorded `2` comes to mean a different option.
case_run_aForkTheChangeDoesNotAnswerIsNamed() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/check-change-readiness.sh tools/pi-child-events.sh tools/tooling.json
  local d p prompt report
  d=$(scratch); p=$(new_project)
  tier_b_change "$p" add-prepared-change
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario tier-b)
  report='LOOP-VERDICT: skipped
LOOP-SKIP-VERDICT: needs-decision
LOOP-CHANGE: add-prepared-change
LOOP-ROOT-CAUSE: the prepared change is the plan and it leaves one fork open
LOOP-RED-TEST: none — nothing was changed
LOOP-GATE: not run — the tree is unchanged
LOOP-DECISION: the bound the change leaves open at design.md:5 — options in order: (1) ship it as the change words it, (2) leave the row out'
  stub_per_item_worker "$d" "$report" 'VERDICT: PASS' ':'
  run_loop "$p" --items 1 --minutes 90 >/dev/null 2>&1 || true
  prompt="$p/.pi/issue-loop/items/31/worker-prompt.md"
  assert_contains "$(cat "$prompt")" "naming the fork and the change's line" "the prompt names where the question goes"
  [ -f "$d/gh.log" ] || fail "nothing was written on the tracker"
  assert_contains "$(cat "$d/gh.log")" "needs-decision" "the item is escalated as a decision"
  assert_contains "$(cat "$d/gh.log")" "design.md:5" "and the question names the change's line"
  assert_eq "1" "$(jq -r '[.items[] | select(.issue == 31) | select(.verdict == "needs-decision")] | length' "$(state_file "$p")")" \
    "the run recorded the decision verdict"
}

# A change whose premise moved is a diagnosis naming the path that moved, and never a replacement plan.
case_run_aChangeWhosePremiseMovedIsNotRePlanned() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/loop/state.sh tools/loop/worktree.sh tools/check-change-readiness.sh tools/pi-child-events.sh tools/tooling.json
  local d p prompt report
  d=$(scratch); p=$(new_project)
  tier_b_change "$p" add-prepared-change
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario tier-b)
  report='LOOP-VERDICT: skipped
LOOP-SKIP-VERDICT: needs-diagnosis
LOOP-CHANGE: add-prepared-change
LOOP-ROOT-CAUSE: the change cites a start-item whose shape moved
LOOP-RED-TEST: none — nothing was changed
LOOP-GATE: not run — the tree is unchanged
LOOP-DECISION: the change cites tools/loop/worktree.sh:216, which no longer shows the start-item it claimed — options in order: (1) re-shape the change, (2) retire the item'
  stub_per_item_worker "$d" "$report" 'VERDICT: PASS' ':'
  run_loop "$p" --items 1 --minutes 90 >/dev/null 2>&1 || true
  prompt="$p/.pi/issue-loop/items/31/worker-prompt.md"
  assert_contains "$(cat "$prompt")" "needs-diagnosis" "the prompt says what a moved claim becomes"
  assert_contains "$(cat "$prompt")" "naming the path that moved" "and that it names the path"
  assert_contains "$(cat "$d/gh.log")" "tools/loop/worktree.sh:216" "the escalation names the path that moved"
  assert_eq "1" "$(jq -r '[.items[] | select(.issue == 31) | select(.verdict == "needs-diagnosis")] | length' "$(state_file "$p")")" \
    "the run recorded the diagnosis verdict"
}

# The source a landed item consumed is reported by the clean command, with its size, and removed only by `--apply`.
case_clean_aConsumedChangeIsReported() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/loop-clean.sh tools/loop/run.sh tools/check-change-readiness.sh tools/pi-child-events.sh tools/tooling.json
  local d p src out
  d=$(scratch); p=$(new_project)
  tier_b_change "$p" add-prepared-change
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario tier-b)
  stub_success "$d"
  run_loop "$p" --items 1 --minutes 90 >/dev/null 2>&1 || true
  src="$p/openspec/changes/add-prepared-change"
  [ -d "$src" ] || fail "the case needs the source to survive the landed item"
  out=$(run_clean "$p") || fail "loop-clean failed: $out"
  assert_contains "$out" "would remove openspec/changes/add-prepared-change" "the source is reported"
  assert_matches "$out" "would remove openspec/changes/add-prepared-change \\([0-9][0-9]* B\\) — the change #31 consumed" \
    "with its size and the item that consumed it"
  [ -d "$src" ] || fail "the report removed the source without --apply"
}

case_clean_aSecondRunFindsNothing() {
  # files: tools/loop/fixtures/scenarios/ tools/loop/loop-clean.sh tools/loop/run.sh tools/check-change-readiness.sh tools/pi-child-events.sh tools/tooling.json
  local d p src out
  d=$(scratch); p=$(new_project)
  tier_b_change "$p" add-prepared-change
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario tier-b)
  stub_success "$d"
  run_loop "$p" --items 1 --minutes 90 >/dev/null 2>&1 || true
  src="$p/openspec/changes/add-prepared-change"
  out=$(run_clean "$p" --apply) || fail "loop-clean --apply failed: $out"
  assert_contains "$out" "removed openspec/changes/add-prepared-change" "--apply removes the consumed source"
  [ -e "$src" ] && fail "the consumed source survived --apply"
  out=$(run_clean "$p" --apply) || fail "the second --apply failed: $out"
  assert_contains "$out" "nothing to remove" "a second run finds nothing of it"
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
  wait_for_stub_child "$fifo.ready" || true
  if [ ! -e "$fifo.ready" ]; then
    kill -9 "$pid" 2>/dev/null
    fail "the stub child never started, so there is no child to end"
  fi
  # The case holds the fifo's writer side, so releasing a child never blocks and never leaves a writer behind.
  exec 8<> "$fifo"
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
  assert_contains "$out" "s, selection: --group alpha" \
    "and the summary names the selection it ran beside what it cost, so a green filtered run never reads like the whole suite: $out"
  # The pool runs the group's cases at once, so the order they recorded themselves in is the order they finished in:
  # what the filter owes is the set (issue #260).
  assert_eq "alpha#one alpha#two " "$(sort "$d/ran" 2>/dev/null | tr '\n' ' ')" \
    "exactly the group's cases ran, and no case of the other group: $(cat "$d/ran" 2>/dev/null)"
  : > "$d/ran"
  out=$(bash "$nested/tools/loop-selftest.sh" --case 'alpha#*' 2>&1); rc=$?
  assert_eq "0" "$rc" "the same selection by pattern passes: $out"
  assert_eq "alpha#one alpha#two " "$(sort "$d/ran" 2>/dev/null | tr '\n' ' ')" \
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

# A suite that has a cost of its own (issue #782): two groups of green stub cases in a nested copy of the driver, one
# case in each group paying a second of sleep, so a group's figure is a number a claim can be made about rather than
# a zero every shape satisfies.
driver_cost_probe() { # <dir> → a nested suite of two groups whose cases record themselves
  local d="$1" nested="$1/nested"
  mkdir -p "$nested/tools"
  cp "$LOOP_PROJECT_ROOT/tools/loop-selftest.sh" "$nested/tools/loop-selftest.sh" \
    || fail "could not copy the driver"
  ln -s "$LOOP_ENGINE" "$nested/tools/loop"
  ln -s "$LOOP_PROJECT_ROOT/tools/issues" "$nested/tools/issues"
  cat > "$nested/tools/loop-cases.sh" <<CASES
LOOP_SHELL_CASES=('alpha#one' 'alpha#two' 'beta#three')
loop_shell_cases() { printf '%s\n' "\${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() {
  printf '%s\n' "\$1" >> "$d/ran"
  case "\$1" in alpha#one|beta#three) sleep 1 ;; esac
  return 0
}
CASES
}

# The figure a report line carries, read back out of the text: `figure_of <line prefix> <output>` → e.g. 1.03, and
# empty when no line starts with that prefix or the line carries no figure.
figure_of() {
  printf '%s\n' "$2" | sed -n "s/^$1\([0-9][0-9]*\)\.\([0-9][0-9]\) s\$/\1.\2/p" | head -1
}

# The summary states what the run cost and what it ran (issue #782): one line per group this run executed, carrying
# that group's case count and the time its cases paid, and the selection's own figure on its summary line. Before
# this the suite named its count, its JDK and its selection and nothing about time, so a case's cost was invisible
# at the place it is paid and every addition to the set was unpriced.
case_driver_theSummaryNamesWhatEachGroupCost() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d nested out rc alpha beta all
  d=$(scratch); driver_cost_probe "$d"; nested="$d/nested"
  out=$(bash "$nested/tools/loop-selftest.sh" 2>&1); rc=$?
  assert_eq "0" "$rc" "the two-group suite passes: $out"
  assert_eq "alpha#one alpha#two beta#three " "$(sort "$d/ran" 2>/dev/null | tr '\n' ' ')" \
    "and the whole declared set ran: $(cat "$d/ran" 2>/dev/null)"
  # One line per group, each with its count: the report is per group because a case's cost is paid by the group it
  # belongs to, and a set that grew unevenly is what a reader has to be able to see.
  assert_contains "$out" "loop-selftest: alpha 2 cases " "the alpha group's line names its two cases: $out"
  assert_contains "$out" "loop-selftest: beta 1 cases " "and the beta group's line names its one: $out"
  # The figure is this run's own measurement and not a constant: the case that sleeps a second is a second of its
  # group's time, so a line that printed no figure, a zero, or one group's number for the other is red.
  alpha=$(figure_of 'loop-selftest: alpha 2 cases ' "$out")
  beta=$(figure_of 'loop-selftest: beta 1 cases ' "$out")
  [ -n "$alpha" ] || fail "the alpha group's line carries no figure: $out"
  [ -n "$beta" ] || fail "the beta group's line carries no figure: $out"
  [ "${alpha%%.*}" -ge 1 ] || fail "the alpha group's figure is not what its cases paid (a second or more): $out"
  [ "${beta%%.*}" -ge 1 ] || fail "the beta group's figure is not what its case paid (a second or more): $out"
  # …and the selection's line carries the run's own figure beside the counts and the selection it already stated.
  all=$(printf '%s\n' "$out" | sed -n 's/^loop-selftest: all 3 cases passed .*— \([0-9][0-9]*\)\.[0-9][0-9] s$/\1/p')
  [ -n "$all" ] || fail "the summary line carries no figure for the selection: $out"
  [ "$all" -ge 1 ] || fail "the selection's figure is not what this run paid (a second or more): $out"
  return 0
}

# A passing case's line keeps its shape (issue #782): the figure is added to it only when the invocation asks with
# `--times`, because two callers parse `PASS <name>` — the mutation harness's records and the leftovers cases — and a
# number in the middle of that line is a contract change for a figure a reader needs only while investigating.
case_driver_theTimesFigureIsAskedForNotAssumed() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d nested out rc name
  d=$(scratch); driver_cost_probe "$d"; nested="$d/nested"
  out=$(bash "$nested/tools/loop-selftest.sh" 2>&1); rc=$?
  assert_eq "0" "$rc" "the suite passes without --times, which is how the harness invokes it: $out"
  assert_not_contains "$out" "PASS alpha#one (" "a passing case's line carries no figure unless one is asked for: $out"
  assert_eq "3" "$(printf '%s\n' "$out" | grep -cE '^PASS [a-z]+#[A-Za-z0-9_]+$')" \
    "every case's line is its PASS and its name and nothing else: $out"
  out=$(bash "$nested/tools/loop-selftest.sh" --times 2>&1); rc=$?
  assert_eq "0" "$rc" "and the same suite passes with --times: $out"
  for name in alpha#one alpha#two beta#three; do
    printf '%s\n' "$out" | grep -qE "^PASS $name \([0-9]+\.[0-9][0-9] s\)$" \
      || fail "--times adds a figure to $name's line and changes nothing else about it: $out"
  done
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

# The gate is the loop's most expensive single action and the only one a child runs rather than the run, so nothing
# bounded how many times one child could pay for it: measured 2026-11-03 over the archive's 227 items and 558 child
# sessions (`.pi/issue-loop/items/*/events.jsonl`, durations from each `tool_execution_end`), the gate was started
# 910 times and spent 35.1 h — 60 % of all tool wall clock — 245 sessions ran it at all, and **117 of those (48 %)
# ran it three or more times** (#670 44 times in 174 turns). A full round is 600–1 100 s, so a run can spend its
# whole budget on gate rounds and never reach the review (issue #756).
#
# The count is a run's own record: the gate writes one line per full run beside the child's event stream
# (`<events>.gate-runs`) and reads the lines that carry the id of the session it is running in, because the stream
# carries every child the item has had and the counts have to be a child's. Reading the stream's own tool calls
# instead was the first shape of this rule and it does not hold: a command that merely *mentions* the gate's file
# cannot be told from one that runs it.
# The third full run is refused with the command the delta owes instead, because a second full round repeats the
# suites the first one already passed.
case_gate_aThirdFullRunInOneSessionIsRefused() {
  # files: tools/loop/gate.sh tools/loop/lib.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json
  local d p conf base events out status i
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_CONF" "$conf" || fail "could not copy the loop configuration"
  # One cheap step and nothing else: the delta must owe exactly one command, so that "the scoped command" is a line
  # a reader can point at, and the gate must cost seconds twice over.
  {
    printf 'LOOP_GATE_COMPILED_CMD=\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS=\n'
    printf 'LOOP_GATE_FIRST_STEPS=\n'
    printf 'LOOP_GATE_SUITES="notes/*|true"\n'
    printf 'LOOP_GATE_TALLY_DIR=\n'
    printf 'LOOP_GATE_RUNS_MAX=2\n'
  } >> "$conf"
  grep -qx 'LOOP_GATE_RUNS_MAX=2' "$conf" || fail "this case could not record the run bound it measures"
  mkdir -p "$p/notes" && printf 'a note\n' > "$p/notes/a.md"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'a note' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  # The stream a child's adapter keeps, in the one fact the gate reads from it: the child's own session id. The runs
  # themselves are recorded by the gate that made them, so what this case drives is the real thing three times.
  events="$d/events.jsonl"
  printf '{"type":"session","version":"3","id":"01a1a18a","cwd":"%s"}\n' "$p" > "$events"
  one_run() { ( cd "$p" && LOOP_CONF="$conf" LOOP_CHILD_EVENTS="$events" bash "$LOOP_ENGINE/gate.sh" --base "$base" 2>&1 ); }

  # A first and a second full run are measurements: each runs the step the delta owes and leaves its own record.
  for i in 1 2; do
    out=$(one_run); status=$?
    assert_eq "0" "$status" "full run $i of the gate in one child measures the step the delta owes: $out"
    assert_not_contains "$out" "LOOP-GATE-REFUSED" "and nothing refuses it: $out"
    assert_contains "$out" "LOOP-GATE: " "it reports the gate's own verdict: $out"
  done

  # The third is refused: it names the runs already made, names the bound, and prints the command the delta owes
  # instead of running it.
  out=$(one_run); status=$?
  assert_eq "3" "$status" "the third full run in one child is refused, with the refusal's own status: $out"
  assert_contains "$out" "LOOP-GATE-REFUSED: 2 full runs of this gate" "the refusal says how many it counted: $out"
  assert_contains "$out" "LOOP_GATE_RUNS_MAX=2" "and names the recorded bound it counted against: $out"
  assert_contains "$out" "earlier run: gate.sh --base $base" "it names the runs already made: $out"
  assert_contains "$out" "scoped: true" "and the command the delta owes instead of a third full round: $out"
  assert_not_contains "$out" "LOOP-GATE: tests=" "the refused run measured nothing and claims no verdict: $out"
  assert_eq "2" "$(grep -c '' "$events.gate-runs")" \
    "and only the two measurements were recorded, never the run that was refused"

  # `--plan` runs no step, so it is not a run: it is never refused and never counted — it is the answer a refused run
  # is handed.
  out=$( cd "$p" && LOOP_CONF="$conf" LOOP_CHILD_EVENTS="$events" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )
  status=$?
  assert_eq "0" "$status" "a plan is not a run and is not refused: $out"
  assert_eq "true" "$out" "and it prints the command the delta owes, which is what a refused run is handed"
  assert_eq "2" "$(grep -c '' "$events.gate-runs")" "a plan leaves no record: it measured nothing"

  # The record carries the child it was made in: the run's own phase split gives the gate child its first full run, so
  # a new `session` line in the stream starts the count at nothing.
  printf '{"type":"session","version":"3","id":"01a1a18b"}\n' >> "$events"
  out=$(one_run); status=$?
  assert_eq "0" "$status" "the runs a previous child made are not charged to this one: $out"
  assert_not_contains "$out" "LOOP-GATE-REFUSED" "so a child that has not run this gate is not refused: $out"
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
  # The pool runs the selected cases at once, so the log's order is the order they finished in and not the order they
  # were selected in: what the selection owes is the set it ran (issue #260).
  ran=$(sort "$STUB_RUN_LOG")
  assert_eq "stub#ownsFixtureDir
stub#ownsGate" "$ran" "only the cases the delta's files declare ran"
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

# The refusal above is written for a caller who mistyped a path, and it is right there. A caller handed a real delta
# asks a different question — "does this delta owe any case?" — and "no" is its ordinary answer: most paths in this
# repository are declared by no case, because a case declares the engine scripts it can be reached through. The two
# answers share one status and one sentence, so a caller that narrows cannot tell them apart and must refuse both.
# `--allow-empty` is the caller saying which question it is asking: the empty intersection becomes a statement that
# names the delta and the declared set, runs nothing and exits 0, while the same file set without the flag is the
# refusal it always was. The flag states emptiness; it does not make a file set valid, and it does not swallow a
# selection that is not empty.
case_driver_anEmptyDeltaIsStatedWhenTheCallerAllowsIt() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d driver conf out status
  d=$(scratch)
  out=$(nested_suite "$d" <<< "$STUB_SELECTION_CASES") || fail "could not build the nested suite"
  driver=$(printf '%s\n' "$out" | sed -n 1p)
  conf=$(printf '%s\n' "$out" | sed -n 2p)
  export STUB_RUN_LOG="$d/ran.log"; : > "$STUB_RUN_LOG"

  # A real delta the case set does not declare — an application path, which no case reaches. With the flag it is a
  # statement carrying the file set and the declared set it selected against, and no case runs.
  out=$(LOOP_CONF="$conf" bash "$driver" --allow-empty \
    --files app/src/main/java/com/naviveylin/MainActivity.kt 2>&1); status=$?
  assert_eq "0" "$status" "an empty delta the caller declared is not a failure: $out"
  assert_contains "$out" "LOOP-SELFTEST: no case declares the delta" "and it is stated, not swallowed: $out"
  assert_contains "$out" "files: app/src/main/java/com/naviveylin/MainActivity.kt" "naming the file set it read: $out"
  assert_contains "$out" "0 of 4 declared" "and the declared set it selected against: $out"
  assert_eq "" "$(cat "$STUB_RUN_LOG")" "no case ran"

  # The same file set without the flag is the refusal, byte for byte: a caller that has not said it expects emptiness
  # cannot be told by the driver that emptiness is what it found.
  out=$(LOOP_CONF="$conf" bash "$driver" \
    --files app/src/main/java/com/naviveylin/MainActivity.kt 2>&1); status=$?
  assert_eq "2" "$status" "the same file set without the flag is still refused: $out"
  assert_contains "$out" "names no case of the 4 declared" "with the refusal it always printed: $out"
  assert_eq "" "$(cat "$STUB_RUN_LOG")" "and neither invocation ran a case"

  # The flag is about the intersection and never about the selection: a file set that does name a case still runs it.
  out=$(LOOP_CONF="$conf" bash "$driver" --allow-empty --files tools/loop/gate.sh 2>&1); status=$?
  assert_eq "0" "$status" "a delta that owes a case runs it under the flag: $out"
  assert_eq "stub#ownsGate" "$(cat "$STUB_RUN_LOG")" "and the case it names is the one that ran"
  assert_contains "$out" "selected 1 of 4 cases" "with the selection stated as the flag narrowed nothing: $out"
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
quick#b" "$(tail -2 "$quick_log" | sort)" "and the suite the lock was released for ran its cases: $(cat "$quick_log")"
  return 0
}

# A profile's suite command may carry `{base}`, and the gate replaces it with the base it resolved — the same base
# whose diff produced the pathspec — so a suite narrows itself to the delta being measured. The engine knows no
# suite by name: it hands over the base and nothing else.
# A case that stages a child blocked on a fifo holds the fifo's writer side for its whole life, and the child is the
# only reader. A write-only open waits for that reader: when the run has already ended the child — which is exactly
# what a case about a child the run ended stages — no reader will ever arrive, so the case waits on the harness's
# per-case bound, and an hour of runner time is reported as `exceeded 3600 s` instead of as the defect (issue #774,
# main's run 41828847613). The read-write open never waits, still holds the writer side the case needs, and still
# blocks the child in its `read`.
case_driver_noFixtureFifoIsOpenedWriteOnly() {
  # files: tools/loop-cases.sh
  local src hits opens
  src="$LOOP_PROJECT_ROOT/tools/loop-cases.sh"
  [ -f "$src" ] || fail "the suite's own case file is not where the check looks for it: $src"
  # The positive control: a check that finds no read-write open at all would pass on a tree that dropped the idiom
  # instead of one that wrote it, so the count is asserted before the refusal.
  opens=$(grep -c 'exec [0-9][0-9]*<> "\$fifo"' "$src" || true)
  [ "${opens:-0}" -gt 0 ] || fail "no case holds a fixture fifo's writer side any more, so this check reads nothing: $src"
  # Comments name the rejected form on purpose (this very case explains it), so only code lines are read.
  hits=$(grep -nE 'exec [0-9][0-9]*> *"\$' "$src" | grep -vE '^[0-9]+:[[:space:]]*#' | grep -vE 'exec [0-9][0-9]*<>' || true)
  [ -z "$hits" ] || fail "a case opens a fixture fifo write-only, so it waits for a reader that may never come: $hits"
  return 0
}

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

# A case's first-line declaration had one reader, and it was the full suite (issue #792). #790's attempt passed the
# 11 cases its own `--case` selection owed and was refused by `driver#everyCaseDeclaresTheFilesItCanReach`, one path
# token short, twelve minutes into its gate — and the attempt was recorded `blocked` with nothing archived. The same
# case alone is 10 s (measured by the item on `528d8e2a`), so a delta that writes a declaration owes that case first,
# before the suite that would refuse it at the end and before the build, and the child's own step sends it to that
# plan line: the gate's order saves the gate's tail, the child's step saves the attempt. The prompt is asserted for the
# pointer, never for this case's name or command — the prompt is an engine template and a case name there would order
# another project's child to run a case that project does not have.
case_gate_theDeclarationCaseIsOwedFirst() {
  # files: tools/loop-cases.sh tools/loop/gate.sh tools/loop/loop.conf tools/loop/prompts/worker.md tools/pi-child-events.sh tools/tooling.json
  local d p base plan first suite build compiled prompt
  d=$(scratch); p=$(new_project)
  # The build's own command is the profile's, never a literal this engine owns, so the portability check has no edge
  # to report here.
  compiled=$(profile_value LOOP_GATE_COMPILED_CMD)
  [ -n "$compiled" ] || fail "the profile names no compiled command, so this case would prove nothing"

  # One delta that writes a case and touches a compiled file: the reach case is owed by the first path and the build
  # and the suites by the others, which is the only shape in which "first" can be read as an order.
  mkdir -p "$p/tools" "$p/app/src/main"
  printf '# a case\n' > "$p/tools/loop-cases.sh"
  printf 'class App\n' > "$p/app/src/main/App.kt"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'a case and a compiled file' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  plan=$( cd "$p" && LOOP_CONF="$LOOP_ENGINE/loop.conf" bash "$LOOP_ENGINE/gate.sh" --plan --base "$base" 2>&1 )

  # The line a command is on, because "before" is an order and the plan is the only place it is written.
  line_of() { printf '%s\n' "$plan" | awk -v pat="$1" 'index($0, pat) { print NR; exit }'; }
  first=$(line_of '--case driver#everyCaseDeclaresTheFilesItCanReach')
  [ -n "$first" ] || fail "a delta that writes a case owes the case that reads the declaration: $plan"
  suite=$(line_of "--from-git $base")
  [ -n "$suite" ] || fail "the suite the declaration case narrows is still owed: $plan"
  build=$(line_of "$compiled")
  [ -n "$build" ] || fail "the build a compiled path owes is still owed: $plan"
  assert_eq "1" "$first" "the declaration case is owed before every suite and before the build: $plan"
  [ "$first" -lt "$suite" ] || fail "the declaration case is owed before the suite that refuses it: $plan"
  [ "$first" -lt "$build" ] || fail "the declaration case is owed before the build: $plan"

  # And the child's own step: a worker whose diff writes a `# files:` line runs the plan's first line before
  # anything else, because a gate round is what the order above saves and the attempt is what this one saves. The
  # prompt is an engine template, so it is asserted for the pointer to the plan and for no project value: a case name
  # or a command written there would arrive in a foreign project's child and name work that project does not have.
  prompt=$(cat "$LOOP_PROJECT_ROOT/tools/loop/prompts/worker.md" 2>&1) || fail "the worker prompt is not readable: $prompt"
  assert_contains "$prompt" "plan's first line" \
    "the worker's own step sends the child to the plan's first line"
  assert_not_contains "$prompt" 'driver#everyCaseDeclaresTheFilesItCanReach' \
    "and names no case of this project, which a foreign project does not have"
  return 0
}

# ------------------------------------------------------- cases: the pool of cases (issue #260)

# The suite ran one case after another (issue #260). Measured 2026-10-22: 163 cases, 723 s, and the serialization was
# the whole of that cost — every case already owned a throwaway repository, a scratch tree under `.pi/tmp/` and a
# process group of its own, and they share nothing but the read-only engine. The probe is four stub cases that each
# record `start` and `stop` around a one-second sleep: the log arrives in real order, so the largest running count
# over it is how wide the pool ran, and no timestamp is read. With the suite's own worker count at least two cases
# run at once; `--jobs 1` bounds the same selection to one case at a time, and both runs print one `PASS` line per
# case, the same summary and the same exit status.
case_driver_theSuiteRunsItsCasesInAPool() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d nested tmp conf out status overlap_width
  d=$(scratch); nested="$d/nested"; tmp="$d/tmp"
  mkdir -p "$nested/tools" "$tmp"
  cp "$LOOP_PROJECT_ROOT/tools/loop-selftest.sh" "$nested/tools/loop-selftest.sh" || fail "could not copy the driver"
  ln -s "$LOOP_ENGINE" "$nested/tools/loop"
  ln -s "$LOOP_PROJECT_ROOT/tools/issues" "$nested/tools/issues"
  cat > "$nested/tools/loop-cases.sh" <<'CASES'
LOOP_SHELL_CASES=('stub#one' 'stub#two' 'stub#three' 'stub#four')
loop_shell_cases() { printf '%s\n' "${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() {
  printf 'start %s\n' "$1" >> "$POOL_LOG"
  sleep 1
  printf 'stop %s\n' "$1" >> "$POOL_LOG"
}
CASES
  conf="$d/loop.conf"
  sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$tmp\"|" "$LOOP_CONF" > "$conf" || fail "could not write the loop configuration"
  export POOL_LOG="$d/pool.log"

  # How wide the pool ran, read from the log's order: one `start` adds a running case, its `stop` takes it away, and
  # the largest count is the number that were running at the same moment.
  overlap_width() {
    awk '$1 == "start" { n++; if (n > max) max = n } $1 == "stop" { n-- } END { printf "%d", max }' "$1"
  }

  : > "$POOL_LOG"
  out=$(LOOP_CONF="$conf" bash "$nested/tools/loop-selftest.sh" 2>&1); status=$?
  assert_eq "0" "$status" "a suite of four stub cases passes: $out"
  assert_eq "4" "$(printf '%s\n' "$out" | grep -c '^PASS stub#')" "one PASS line per case: $out"
  assert_contains "$out" "loop-selftest: all 4 cases passed" "and the summary counts the whole selection: $out"
  assert_eq "4" "$(grep -c '^start ' "$POOL_LOG" || true)" "every case started: $(cat "$POOL_LOG")"
  total_ran=$(overlap_width "$POOL_LOG")
  [ "$total_ran" -ge 2 ] ||
    fail "the suite ran its cases one after another (at most $total_ran was running at any moment): $(cat "$POOL_LOG")"

  # …and the bound is real rather than "everything at once": one worker is one case at a time.
  : > "$POOL_LOG"
  out=$(LOOP_CONF="$conf" bash "$nested/tools/loop-selftest.sh" --jobs 1 2>&1); status=$?
  assert_eq "0" "$status" "a suite bounded to one worker passes: $out"
  assert_eq "1" "$(overlap_width "$POOL_LOG")" "one worker runs one case at a time: $(cat "$POOL_LOG")"
  assert_contains "$out" "loop-selftest: all 4 cases passed" "and reports the same summary: $out"
  return 0
}

# A case that never returns held a whole suite — and the gate behind it — for **56 minutes** on 2026-10-25, and the
# work of the item whose gate it was had to be repaired by hand afterwards. The pool started the case and then
# waited for it with no clock of its own (`grep -n 'timeout ' tools/loop-selftest.sh` → no hit), so nothing could
# end it. The bound belongs to the pool and not to the case: this case drives the pool with a stub case that blocks
# far past `LOOP_CASE_MAX_SECONDS` and asserts that the suite answers it, keeps counting and runs the case queued
# behind it — and the `timeout` around the nested suite is the case's own guard, because a case that hangs the
# suite it is testing would repeat the defect it exists to catch.
case_driver_aCaseThatBlocksIsEndedByItsOwnBound() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d out driver conf status
  d=$(scratch)
  # The nested suite is the library's own (`nested_suite`, the shape the filter cases use): it takes the stub case
  # library on stdin and answers the driver and the configuration it wrote. The bound is appended to that
  # configuration, which the driver sources after the project's, so the last assignment wins.
  out=$(nested_suite "$d" <<'CASES'
LOOP_SHELL_CASES=('stub#blocks' 'stub#after')
loop_shell_cases() { printf '%s\n' "${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() {
  case "$1" in
    stub#blocks) sleep 600 ;;
    stub#after) printf 'the case queued behind the bound ran\n' ;;
  esac
}
CASES
  ) || fail "could not build the nested suite"
  driver=$(printf '%s\n' "$out" | sed -n 1p)
  conf=$(printf '%s\n' "$out" | sed -n 2p)
  printf 'LOOP_CASE_MAX_SECONDS=2\n' >> "$conf"
  # `--jobs 1`, because this case is about the bound itself: the bound a run applies follows its pool (issue #766), so
  # a case that asserts the printed "exceeded 2 s" asks for the serial run it measured.
  out=$(cd "$d" && LOOP_CONF="$conf" timeout 60 bash "$driver" --jobs 1 2>&1); status=$?
  [ "$status" -ne 124 ] || fail "the suite was still inside a case that never returned after 60 s: $out"
  assert_eq "1" "$status" "the suite fails on the case its own bound ended: $out"
  assert_contains "$out" "FAIL stub#blocks (exceeded 2 s)" "and the line names the case with the bound that ended it"
  assert_contains "$out" "PASS stub#after" "the case queued behind the bounded one ran"
  assert_contains "$out" "1 of 2 cases failed" "the summary counts the bounded case as a failure"
  return 0
}

# The bound must answer only a case that outran it: a case that finishes inside its clock keeps its own verdict —
# including a failure of its own — and no line of the bound's appears. This case is green against the unfixed driver
# by construction: it is the guard that a bound cannot be turned into "everything failed", and mutation `61.2` is
# what makes it red (`tools/loop-mutations.sh`).
case_driver_aCaseInsideItsBoundKeepsItsOwnVerdict() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d out driver conf status
  d=$(scratch)
  out=$(nested_suite "$d" <<'CASES'
LOOP_SHELL_CASES=('stub#quick' 'stub#fails')
loop_shell_cases() { printf '%s\n' "${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() {
  case "$1" in
    stub#quick) sleep 1 ;;
    stub#fails) return 3 ;;
  esac
}
CASES
  ) || fail "could not build the nested suite"
  driver=$(printf '%s\n' "$out" | sed -n 1p)
  conf=$(printf '%s\n' "$out" | sed -n 2p)
  printf 'LOOP_CASE_MAX_SECONDS=5\n' >> "$conf"
  out=$(cd "$d" && LOOP_CONF="$conf" timeout 60 bash "$driver" --jobs 1 2>&1); status=$?
  assert_eq "1" "$status" "the suite fails on the case that failed by itself: $out"
  assert_contains "$out" "PASS stub#quick" "a case inside its bound keeps its own pass"
  assert_contains "$out" "FAIL stub#fails" "and a case that failed inside its bound keeps its own failure"
  assert_eq "0" "$(printf '%s\n' "$out" | grep -c 'exceeded' || true)" "the bound answers neither of them"
  return 0
}

# The harness a child runs under kills one tool call at a ceiling of its own — `Error: Tool 'ctx_shell' exceeded its
# timeout of 900000ms.` — and no project file can see it (issue #692). Measured twice on 2026-10-28: a suite call the
# project budgets at 500 s was killed mid-run with the child's work uncommitted, and the project's own bounds (900,
# 1 800, 2 700, 3 900 s) were every one of them above that ceiling. So the caller names its ceiling at the call site
# and the suite ends itself at it, naming what it covered, instead of being killed with the pool still running. This
# case gives four cases of 6 s to a caller ceiling of 3 s: on the base the suite runs all 24 s of them and reads
# green, because nothing read a ceiling.
case_driver_aSuiteEndsInsideTheCeilingItsCallerNamed() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d out driver conf status started elapsed
  d=$(scratch)
  out=$(nested_suite "$d" <<'CASES'
LOOP_SHELL_CASES=('stub#slowOne' 'stub#slowTwo' 'stub#slowThree' 'stub#slowFour')
loop_shell_cases() { printf '%s\n' "${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() { sleep 6; }
CASES
  ) || fail "could not build the nested suite"
  driver=$(printf '%s\n' "$out" | sed -n 1p)
  conf=$(printf '%s\n' "$out" | sed -n 2p)
  # One worker, so the suite's own length is the sum of its cases and the ceiling falls inside it.
  printf 'LOOP_CASE_MAX_SECONDS=600\n' >> "$conf"
  started=$(date +%s)
  out=$(cd "$d" && LOOP_CONF="$conf" LOOP_CALL_CEILING_SECONDS=3 timeout 120 bash "$driver" --jobs 1 2>&1); status=$?
  elapsed=$(( $(date +%s) - started ))
  [ "$status" -ne 124 ] || fail "the suite was still running at the ceiling its caller named after 120 s: $out"
  assert_eq "1" "$status" "a suite its caller's ceiling cut is a failure, never a green suite: $out"
  assert_contains "$out" "LOOP-SUITE-CEILING: the caller's ceiling of 3s was reached — 1 of 4 cases started" \
    "the suite ends itself at the ceiling and names how much of the selection it covered: $out"
  assert_contains "$out" "3 never started" "and how many cases the ceiling left unrun: $out"
  assert_not_contains "$out" "all 4 cases passed" "a cut suite reports no green summary"
  [ "$elapsed" -lt 20 ] || fail "the suite outlived the ceiling its caller named: ${elapsed}s"
  # …and the other half of that THEN: the case that was still running goes with the suite, together with what it
  # started, and the suite's scratch tree goes too. The running case is a `sleep 6` in a process group of its own,
  # standing at a working directory under this case's scratch root — `loop_pids_under` is the reader of exactly that
  # (issue #213) — so a suite that ends only itself leaves one process and one tree behind, and both are counted here
  # rather than inferred from `$elapsed`. `loop_pids_under` reads `/proc`, so these two counts are a Linux bound: on a
  # platform without it the reader reports nothing and the pair says nothing either way.
  assert_eq "0" "$(loop_pids_under "$d" | grep -c . || true)" \
    "no process of a cut suite outlives it: $(loop_pids_under "$d" | tr '\n' ' ')"
  assert_eq "0" "$(find "$d/tmp" -mindepth 1 2>/dev/null | grep -c . || true)" \
    "and the cut suite removed its own scratch tree: $(find "$d/tmp" -mindepth 1 2>/dev/null | tr '\n' ' ')"
  return 0
}

# The ceiling is a bound only when it is the smaller one: a caller that names a ceiling the whole selection fits
# inside gets exactly the suite it got before, green summary and all. This case is green against the unfixed driver by
# construction — it is the guard that a ceiling cannot be turned into "everything ended early" — and the mutation
# `70.2` is what makes it red.
# The bound a case may run is measured on **one** case at a time, and the pool is one case per CPU, so on a 4-vCPU
# runner the cases that wait — for a child's timeout, a heartbeat interval, a report window — are starved past a bound
# that never moved. Measured 2026-11-04 (issue #766): three CI runs of one tree, a different case red in each, every
# one of them green when run alone, and `LOOP_SELFTEST_JOBS=4 bash tools/loop-selftest.sh` reproduced it on a 16-vCPU
# host (`FAIL run#aReviewerEndedAtItsTurnBoundIsNotARefusal (exceeded 900 s)`). The bound therefore follows the pool,
# and the run says which bound it applied: a run whose bound nobody can read cannot be told from one that applied the
# serial value on a machine four times slower.
case_driver_theCaseBoundScalesWithThePool() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d out driver conf status
  d=$(scratch)
  out=$(nested_suite "$d" <<'CASES'
LOOP_SHELL_CASES=('stub#quick' 'stub#alsoQuick')
loop_shell_cases() { printf '%s\n' "${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() { sleep 1; }
CASES
  ) || fail "could not build the nested suite"
  driver=$(printf '%s\n' "$out" | sed -n 1p)
  conf=$(printf '%s\n' "$out" | sed -n 2p)
  printf 'LOOP_CASE_MAX_SECONDS=600\n' >> "$conf"
  out=$(cd "$d" && LOOP_CONF="$conf" timeout 60 bash "$driver" --print-bound --jobs 4 2>&1); status=$?
  assert_eq "0" "$status" "a pool of four keeps the suite green: $out"
  assert_contains "$out" "loop-selftest: 4 case(s) at a time, 2400 s per case" \
    "the bound a four-way run applies is the profile's value times the pool: $out"
  out=$(cd "$d" && LOOP_CONF="$conf" timeout 60 bash "$driver" --print-bound --jobs 1 2>&1); status=$?
  assert_eq "0" "$status" "one case at a time keeps the suite green: $out"
  assert_contains "$out" "loop-selftest: 1 case(s) at a time, 600 s per case" \
    "and a serial run keeps the profile's own value, unmultiplied: $out"
  return 0
}

case_driver_aCeilingTheSuiteFitsInsideChangesNothing() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d out driver conf status
  d=$(scratch)
  out=$(nested_suite "$d" <<'CASES'
LOOP_SHELL_CASES=('stub#quick' 'stub#alsoQuick')
loop_shell_cases() { printf '%s\n' "${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() { sleep 1; }
CASES
  ) || fail "could not build the nested suite"
  driver=$(printf '%s\n' "$out" | sed -n 1p)
  conf=$(printf '%s\n' "$out" | sed -n 2p)
  printf 'LOOP_CASE_MAX_SECONDS=600\n' >> "$conf"
  out=$(cd "$d" && LOOP_CONF="$conf" LOOP_CALL_CEILING_SECONDS=600 timeout 60 bash "$driver" --jobs 1 2>&1); status=$?
  assert_eq "0" "$status" "a ceiling the selection fits inside leaves the suite green: $out"
  assert_contains "$out" "PASS stub#quick" "every case keeps its own verdict"
  assert_contains "$out" "loop-selftest: all 2 cases passed" "and the suite reports the summary it always reported: $out"
  assert_eq "0" "$(printf '%s\n' "$out" | grep -c 'LOOP-SUITE-CEILING' || true)" "the ceiling answered nothing"
  # The ceiling's watcher sleeps for the whole of it (`loop-selftest.sh`'s `ceiling_watch`), so a suite that ends
  # inside its ceiling is the case where `on_exit` has something to end: without that one `loop_end_group` the guard
  # leaves a `sleep 600` at this scratch root for ten minutes. So the THEN is counted, not assumed: no process, and
  # no scratch tree (issue #692).
  assert_eq "0" "$(loop_pids_under "$d" | grep -c . || true)" \
    "a suite that ends inside its ceiling leaves no process behind: $(loop_pids_under "$d" | tr '\n' ' ')"
  assert_eq "0" "$(find "$d/tmp" -mindepth 1 2>/dev/null | grep -c . || true)" \
    "nor its scratch tree: $(find "$d/tmp" -mindepth 1 2>/dev/null | tr '\n' ' ')"
  return 0
}

# A case's output was held in a file until the case ended (`loop-selftest.sh`'s `finish_case` cat-ed it), so the log of
# the hung gate was 222 `PASS` lines and then fifty minutes of nothing, and the case that hung was named nowhere —
# reconstructing which case it was took a session (issue #308). The line must reach the suite's own output as it
# arrives, prefixed with the case's name, while the case is still running, and the case's own file must still carry
# it unaltered for its failure text.
case_driver_aRunningCasePrintsItsOwnLines() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d out driver conf status
  d=$(scratch)
  out=$(nested_suite "$d" <<'CASES'
LOOP_SHELL_CASES=('stub#slow')
loop_shell_cases() { printf '%s\n' "${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() {
  printf 'a line from a case that has not ended\n'
  sleep 600
}
CASES
  ) || fail "could not build the nested suite"
  driver=$(printf '%s\n' "$out" | sed -n 1p)
  conf=$(printf '%s\n' "$out" | sed -n 2p)
  printf 'LOOP_CASE_MAX_SECONDS=3\n' >> "$conf"
  # `--jobs 1`: the bound a run applies follows its pool (issue #766), and this case asserts the 3 s it measured.
  out=$(cd "$d" && LOOP_CONF="$conf" timeout 60 bash "$driver" --jobs 1 2>&1); status=$?
  [ "$status" -ne 124 ] || fail "the suite was still inside the printing case after 60 s: $out"
  assert_contains "$out" "stub#slow| a line from a case that has not ended" \
    "the running case's own line reaches the suite's output, prefixed with the case"
  assert_contains "$out" "FAIL stub#slow (exceeded 3 s)" "and the case's bound still answers it"
  return 0
}

# The exit trap's `/proc` sweep was per suite, and a suite that runs its cases at once cannot keep it there: a process
# a case left behind would stand beside every case after it, at a scratch root its suite had already given away
# (issue #213 left 44 of them, 2 to 4.6 hours old, at trees that no longer existed). The probe leaves one behind on
# purpose — a `sleep` in a process group of its own (`set -m`, the shape `run_child` uses) at the case's own scratch
# root — and the second stub case waits for the first to end, then polls for that process. What the driver owes is
# that the answer arrives when the case ends, not when the suite does.
case_driver_aCaseThatLeavesAProcessBehindIsSweptWithIt() {
  # files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/
  local d nested tmp conf out status pid alive i
  d=$(scratch); nested="$d/nested"; tmp="$d/tmp"
  mkdir -p "$nested/tools" "$tmp"
  cp "$LOOP_PROJECT_ROOT/tools/loop-selftest.sh" "$nested/tools/loop-selftest.sh" || fail "could not copy the driver"
  ln -s "$LOOP_ENGINE" "$nested/tools/loop"
  ln -s "$LOOP_PROJECT_ROOT/tools/issues" "$nested/tools/issues"
  cat > "$nested/tools/loop-cases.sh" <<'CASES'
LOOP_SHELL_CASES=('stub#leavesOne' 'stub#watchesForIt')
loop_shell_cases() { printf '%s\n' "${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() {
  local i pid alive
  # The root the driver gives this case, which is what the case's own trees are created under (`scratch()`), and
  # what the case's sweep reads: a driver that runs its cases at once must hand each of them one of its own.
  [ -n "${LOOP_CASE_TMP:-}" ] || { printf 'the driver hands the case no scratch root of its own\n'; return 1; }
  case "$1" in
    stub#leavesOne)
      # The two things the suite's sweep reads and a group signal cannot reach: a process in a group of its own, at a
      # working directory inside this case's own scratch root.
      set -m
      ( cd "$LOOP_CASE_TMP" && exec sleep 300 ) &
      printf '%s' "$!" > "$SWEEP_PID"
      : > "$SWEEP_DONE"
      ;;
    stub#watchesForIt)
      for i in $(seq 1 400); do [ -f "$SWEEP_DONE" ] && break; sleep 0.05; done
      [ -f "$SWEEP_PID" ] || { printf 'the first case never recorded the process it left behind\n'; return 1; }
      pid=$(cat "$SWEEP_PID")
      alive=1
      for i in $(seq 1 200); do
        kill -0 "$pid" 2>/dev/null || { alive=0; break; }
        sleep 0.05
      done
      [ "$alive" -eq 0 ] || {
        printf 'the process a case left at its own scratch root was still running when the next case ran (pid %s)\n' "$pid"
        return 1
      }
      ;;
  esac
  return 0
}
CASES
  conf="$d/loop.conf"
  sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$tmp\"|" "$LOOP_CONF" > "$conf" || fail "could not write the loop configuration"
  export SWEEP_PID="$d/sweep-pid" SWEEP_DONE="$d/sweep-done"

  out=$(LOOP_CONF="$conf" bash "$nested/tools/loop-selftest.sh" 2>&1); status=$?
  assert_eq "0" "$status" "a suite whose case leaves a process behind still passes: $out"
  assert_contains "$out" "PASS stub#watchesForIt" "and the case that watched for it passed: $out"
  [ -f "$SWEEP_PID" ] || fail "the first stub case never recorded the process it left behind: $out"
  pid=$(cat "$SWEEP_PID")
  case "$pid" in ''|*[!0-9]*) fail "the first stub case recorded no pid: $(cat "$SWEEP_PID")" ;; esac
  alive=1
  for i in $(seq 1 50); do
    kill -0 "$pid" 2>/dev/null || { alive=0; break; }
    sleep 0.1
  done
  [ "$alive" -eq 0 ] || { kill -9 "$pid" 2>/dev/null; fail "the process the case left behind outlived the suite (pid $pid)"; }
  return 0
}

# ── one call answers a tree question (issue #687) ────────────────────────────────────────────────────────────────
# 9 338 of the archive's 16 987 `ctx_shell` calls (55 %) carry a search or read verb, at a p50 of 52 per item, and they
# chain: `grep -> grep` 1 017x in 132 items, `grep -> read` 508x, `grep -> sed` 385x (`python3 tools/loop-spend.py`).
# Neither half of the cure is prose — the tool a question needs is `tools/look.sh` for the whole tree at once, and the
# policy that sends a child there is `tools/pi-shell-refusal.mjs`, loaded by the adapter every child is started
# through. These cases are the two halves' own rules: that one call covers the worktree and the submodule and stays
# inside the bound, and that a shell tree search is refused while a range read and a stream filter are not.

# The question this answers is the one the measurement shows costing three calls: "where does this pattern occur" —
# over the whole tree at once, the submodule included, with the per-file counts and the matched lines in one answer.
# The walk is from the root, so every path in the answer is relative to it and can be cited as it stands, and the trees
# that are generated or machine-local (`.pi`, `build`, …) are not the tree a search is answered from.
case_look_oneCallAnswersTheWholeTree() {
  # files: tools/look.sh
  local d tree look out status
  look="$LOOP_PROJECT_ROOT/tools/look.sh"
  [ -f "$look" ] || fail "nothing answers a tree question in one call: tools/look.sh is not in the tree (issue #687)"
  d=$(scratch); tree="$d/tree"
  mkdir -p "$tree/src" "$tree/app/src/main/cpp/libosmscout" "$tree/build" "$tree/.pi"
  printf 'alpha\nNEEDLE one\nbeta\n' > "$tree/src/a.txt"
  printf 'NEEDLE two\n' > "$tree/app/src/main/cpp/libosmscout/b.cpp"
  printf 'NEEDLE generated\n' > "$tree/build/skip.txt"
  printf 'NEEDLE machine local\n' > "$tree/.pi/log.txt"

  out=$(LOOK_ROOT="$tree" bash "$look" 'NEEDLE' 2>&1); status=$?
  assert_eq "0" "$status" "a pattern the tree holds exits 0: $out"
  assert_contains "$out" 'src/a.txt:2:NEEDLE one' "the matched line is printed with its path and its line number: $out"
  assert_contains "$out" 'app/src/main/cpp/libosmscout/b.cpp:1:NEEDLE two' \
    "and the submodule is inside the same walk: $out"
  assert_contains "$out" 'LOOK-FILE: src/a.txt 1' "the per-file count is printed: $out"
  assert_contains "$out" 'LOOK: 2 matches in 2 files' "the line above the matches counts what the tree holds: $out"
  assert_not_contains "$out" 'NEEDLE generated' "a generated tree is not searched: $out"
  assert_not_contains "$out" 'NEEDLE machine local' "and neither is a machine-local one: $out"

  out=$(LOOK_ROOT="$tree" bash "$look" 'NEEDLE' --files 2>&1)
  assert_contains "$out" 'LOOK-FILE: app/src/main/cpp/libosmscout/b.cpp 1' "--files names the file the pattern is in: $out"
  assert_not_contains "$out" 'NEEDLE one' "--files prints no matched line: $out"

  out=$(LOOK_ROOT="$tree" bash "$look" 'NEEDLE' --context 1 2>&1)
  assert_contains "$out" 'src/a.txt-1-alpha' "--context prints the line beside the match, in grep's own shape: $out"

  out=$(LOOK_ROOT="$tree" bash "$look" 'nothing-of-this' 2>&1); status=$?
  assert_eq "1" "$status" "a pattern the tree does not hold is exit 1 and not a failure: $out"
  out=$(bash "$look" 2>&1); status=$?
  assert_eq "2" "$status" "a call with no pattern is a usage error: $out"
  out=$(LOOK_ROOT="$tree" bash "$look" 'NEEDLE' --context 2>&1); status=$?
  assert_eq "2" "$status" "a bare --context never runs with the value it never got: $out"
  assert_contains "$out" 'needs a value' "and it names the value it is missing: $out"
  return 0
}

# The bound is a rule about the tail (issue #660), and this tool owes the same one: the answer is printed once and
# re-read by every later turn, so one pattern that matches a thousand lines would cost what the three calls it
# replaced cost together. The number is the project's recorded `LOOP_TOOL_RESULT_MAX_CHARS` — one home for it — and a
# cut says it is a cut, because a reader who cannot tell a short answer from a truncated one re-runs the call.
case_look_aResultOverTheBoundIsCutAndNamed() {
  # files: tools/look.sh tools/loop/loop.conf
  local d tree look out size recorded status
  look="$LOOP_PROJECT_ROOT/tools/look.sh"
  [ -f "$look" ] || fail "nothing bounds a tree question: tools/look.sh is not in the tree (issue #687)"
  d=$(scratch); tree="$d/tree"
  mkdir -p "$tree/src"
  # 300 matching lines of ~60 characters: far over any bound this project records, and named by their number so the
  # tail that a cut leaves out can be asserted by its text.
  awk 'BEGIN { for (i = 1; i <= 300; i++) printf "NEEDLE-%03d %s\n", i, "0123456789012345678901234567890123456789" }' \
    > "$tree/src/many.txt"
  recorded=$(sed -n 's/^LOOP_TOOL_RESULT_MAX_CHARS=["]\{0,1\}\([0-9][0-9]*\)["]\{0,1\}[[:space:]]*$/\1/p' \
    "$LOOP_PROJECT_ROOT/tools/loop/loop.conf" | tail -1)
  [ -n "$recorded" ] || fail "the project records no LOOP_TOOL_RESULT_MAX_CHARS in tools/loop/loop.conf"

  env -u LOOP_TOOL_RESULT_MAX_CHARS LOOK_ROOT="$tree" bash "$look" 'NEEDLE' > "$d/recorded.txt" 2>&1
  size=$(wc -c < "$d/recorded.txt")
  out=$(cat "$d/recorded.txt")
  [ "$size" -le "$recorded" ] || fail "the answer carried $size characters against the recorded bound of $recorded"
  assert_contains "$out" 'LOOK-BOUND' "a cut answer says it was cut: $out"
  assert_contains "$out" 'LOOK: 300 matches in 1 files' "and the count of what the tree holds survives the cut: $out"
  assert_not_contains "$out" 'NEEDLE-300' "the tail it could not show is not there: $out"

  LOOK_ROOT="$tree" LOOP_TOOL_RESULT_MAX_CHARS=900 bash "$look" 'NEEDLE' > "$d/env.txt" 2>&1
  size=$(wc -c < "$d/env.txt")
  [ "$size" -le 900 ] || fail "a bound the environment names is not the one applied: $size characters against 900"
  assert_contains "$(cat "$d/env.txt")" 'LOOK-BOUND' "and the answer says which bound cut it"

  LOOK_ROOT="$tree" LOOP_TOOL_RESULT_MAX_CHARS=0 bash "$look" 'NEEDLE' > "$d/unbounded.txt" 2>&1
  size=$(wc -c < "$d/unbounded.txt")
  [ "$size" -gt "$recorded" ] || fail "no bound was recorded as $recorded characters and the whole answer is $size"
  assert_contains "$(cat "$d/unbounded.txt")" 'NEEDLE-300' "with no bound the whole answer is printed"
  assert_not_contains "$(cat "$d/unbounded.txt")" 'LOOK-BOUND' "and nothing is said about a bound it never met"
  return 0
}

# A section list is a search of **one document** (issue #796): every phase prompt names the recipe "a guideline's
# section list is its own", and the tree-wide walk prints every other document's headings beside it. `--path` scopes
# the same walk to one file or one directory, so the call a child needs is one call that answers the question, and it
# is the call each prompt skeleton names — the shell `grep -n '^## '` it named before is refused by the policy the
# child is started with (`tools/pi-shell-refusal.mjs`), which left the child with a recipe it could not run.
case_look_aPathScopesTheWalkToIt() {
  # files: tools/look.sh
  local d tree look out status
  look="$LOOP_PROJECT_ROOT/tools/look.sh"
  [ -f "$look" ] || fail "nothing answers a tree question in one call: tools/look.sh is not in the tree (issue #687)"
  d=$(scratch); tree="$d/tree"
  mkdir -p "$tree/guidelines"
  printf '# A\n\n## 1 one\n\n## 2 two\n' > "$tree/guidelines/a.md"
  printf '## 9 elsewhere\n' > "$tree/guidelines/b.md"
  printf '## 8 outside the tree\n' > "$d/outside.md"

  out=$(LOOK_ROOT="$tree" bash "$look" '^## ' --path guidelines/a.md 2>&1); status=$?
  assert_eq "0" "$status" "--path walks the file it names: $out"
  assert_contains "$out" 'guidelines/a.md:3:## 1 one' "the heading is printed with its line number: $out"
  assert_contains "$out" "of $tree/guidelines/a.md for '^## '" "and the answer's first line names what was walked: $out"
  assert_not_contains "$out" 'elsewhere' "and no other document is searched: $out"
  assert_contains "$out" 'LOOK: 2 matches in 1 files' "the count is the scoped walk's: $out"

  out=$(LOOK_ROOT="$tree" bash "$look" '^## ' --path guidelines 2>&1)
  assert_contains "$out" 'guidelines/b.md:1:## 9 elsewhere' "a directory is a scope too: $out"

  out=$(LOOK_ROOT="$tree" bash "$look" '^## ' --path guidelines/missing.md 2>&1); status=$?
  assert_eq "2" "$status" "a path the tree does not hold is a refusal and not an empty answer: $out"
  assert_contains "$out" 'no path' "and it names what it could not find: $out"

  # …and a scope that walks out of the tree is refused even when it resolves: `$d/outside.md` is a real file one level
  # above the root, and a walk that followed it would answer with a document the tree does not hold.
  out=$(LOOK_ROOT="$tree" bash "$look" '^## ' --path ../outside.md 2>&1); status=$?
  assert_eq "2" "$status" "a scope that carries .. is refused rather than walked out of the tree: $out"
  assert_not_contains "$out" 'outside the tree' "and nothing outside the tree is read: $out"

  out=$(LOOK_ROOT="$tree" bash "$look" '^## ' --path 2>&1); status=$?
  assert_eq "2" "$status" "a bare --path never runs with the value it never got: $out"
  assert_contains "$out" 'needs a value' "and it names the value it is missing: $out"
  return 0
}

# The other half of the cure, and the one prose has failed at since #246: the refusal is a policy at the seam where
# every loop child is started (`tools/pi-child-events.sh` loads `tools/pi-shell-refusal.mjs`), so the rule is asserted
# on the file that carries it rather than on a sentence a child may follow. What it must leave alone is as much a part
# of the rule as what it refuses: `guidelines/Build.md` §10 reads a log by range (`sed -n 'N,Mp'`, `tail -n`), and the
# verdict of a build is read back from `/tmp/build-app.log` by a `grep` whose output a `head` filters.
case_policy_aShellTreeSearchIsRefusedAndARecipeIsNot() {
  # files: tools/loop-cases.sh tools/loop-selftest.sh tools/look.sh tools/pi-shell-refusal.mjs
  local rule d probe answer status
  rule="$LOOP_PROJECT_ROOT/tools/pi-shell-refusal.mjs"
  [ -f "$rule" ] \
    || fail "nothing refuses a shell tree search: tools/pi-shell-refusal.mjs is not in the tree (issue #687)"
  d=$(scratch)
  local cmd out instead
  # Each line is the call and the tool that answers it, because the refusal's whole value is that it names one: a block
  # that says only "no" costs the turn it was meant to save. `cat`/`head` answer with `ctx_read`, `ls` with `ctx_ls`,
  # and a pattern with `look.sh`.
  while IFS='|' read -r cmd instead; do
    [ -n "$cmd" ] || continue
    out=$(node "$rule" --command "$cmd" 2>&1); status=$?
    assert_eq "3" "$status" "a shell tree search is refused (exit $status): $cmd -- $out"
    assert_contains "$out" "REFUSED" "and the refusal is what it prints: $cmd -- $out"
    assert_contains "$out" "$instead" "and it names the call that answers the question instead: $cmd -- $out"
  done <<'SEARCHES'
grep -rn "14\.0" app/src/test|look.sh
grep -n foo tools/loop-cases.sh|look.sh
cat guidelines/Build.md|ctx_read
head -n 20 AGENTS.md|ctx_read
ls openspec/changes/archive|ctx_ls
find . -name '*.kt'|look.sh
wc -l app/build.gradle.kts|look.sh
awk '/x/' tools/look.sh|look.sh
grep -rn TODO|look.sh
SEARCHES

  # …and the recipes that are not a tree search, which a policy that read its verbs alone would have deleted: the
  # gate's own command, a device log filtered by a pipe, a range read of a known log, and a report read by pattern.
  while IFS= read -r cmd; do
    [ -n "$cmd" ] || continue
    out=$(node "$rule" --command "$cmd" 2>&1); status=$?
    assert_eq "0" "$status" "a call that reads a known file or a stream is left alone (exit $status): $cmd -- $out"
    assert_contains "$out" "ALLOWED" "and it is handed to the child unchanged: $cmd -- $out"
  done <<'RECIPES'
./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
bash tools/loop-selftest.sh --files tools/look.sh
adb -s emulator-5554 logcat -d | grep -E 'Diag/(HOST|SESSION)'
sed -n '1,180p' ki_processing_failures.log
tail -n 200 app/build/reports/x.log
grep -E 'BUILD SUCCESSFUL|BUILD FAILED' /tmp/build-app.log | head
grep -o 'tests="[0-9]*"' core/build/reports/kover/reportDebug.xml | tail -1
echo "$L" | grep -c 'lock OK'
jq -r '.[].number' /tmp/tracker.json | sort | uniq -d
RECIPES

  # The extension itself: the file is a hook, not a flag the agent happens to accept, and the hook blocks the call with
  # a reason rather than failing it. Driven through the factory the agent calls, with a stand-in for its API, so the
  # assertion is on the policy and not on a model, a token or an agent.
  probe="$d/probe.mjs"
  cat > "$probe" <<'PROBE'
import { pathToFileURL } from "node:url";
const mod = await import(pathToFileURL(process.env.RULE).href);
const handlers = {};
mod.default({ on: (name, fn) => { handlers[name] = fn; } });
const call = (toolName, input) => handlers.tool_call({ toolName, input });
const blocked = await call("ctx_shell", { command: "grep -rn foo app/src" });
const allowed = await call("ctx_shell", { command: "./gradlew :app:assembleDebug" });
const other = await call("ctx_read", { path: "app/src/x.kt" });
const empty = await call("ctx_shell", {});
console.log(JSON.stringify({
  hook: typeof handlers.tool_call,
  block: !!blocked && blocked.block === true,
  reason: (blocked && blocked.reason) || "",
  allowed: allowed === undefined,
  other: other === undefined,
  empty: empty === undefined,
}));
PROBE
  answer=$(RULE="$rule" node "$probe" 2>&1) || fail "the policy's factory could not be driven: $answer"
  assert_contains "$answer" '"hook":"function"' "the extension registers the tool_call hook: $answer"
  assert_contains "$answer" '"block":true' "and a shell grep over the tree is blocked: $answer"
  assert_contains "$answer" '"allowed":true' "while ./gradlew is handed to the child: $answer"
  assert_contains "$answer" '"other":true' "a tool that is not a shell is not the policy's business: $answer"
  assert_contains "$answer" '"empty":true' "and a shell call with no command is not a failure: $answer"
  return 0
}

# The policy reaches a child only if the process that starts it loads the extension (issue #687): the adapter is the one
# place this project's agent flags live, and every child goes through it, so the word is asserted there and not in a
# phase — a phase under `tools/loop/**` may name no agent flag (`run#engineNamesNoProjectFact`). The refusal is the
# *second* extension, so it must not move the first: `adapter#theChildIsStartedWithTheResultBound` reads the first `-e`.
case_adapter_theChildIsStartedWithTheShellPolicy() {
  # files: tools/pi-child-events.sh tools/pi-shell-refusal.mjs
  local d prompt recorder got line next ext
  [ -f "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" ] || return 0
  ext="$LOOP_PROJECT_ROOT/tools/pi-shell-refusal.mjs"
  [ -f "$ext" ] || fail "the adapter loads no shell policy, and $ext is not in the tree (issue #687)"
  d=$(scratch)
  prompt="$d/prompt.md"
  printf 'Reply with OK.\n' > "$prompt"
  recorder="$d/recorder.sh"
  {
    printf '%s\n' '#!/usr/bin/env bash'
    printf '%s\n' 'for a in "$@"; do printf "%s\n" "$a"; done > "$RECORD"'
  } > "$recorder"
  env -u LOOP_CHILD_EVENTS RECORD="$d/argv.txt" bash "$LOOP_PROJECT_ROOT/tools/pi-child-events.sh" \
    bash "$recorder" "$prompt" </dev/null >"$d/out" 2>&1 ||
    fail "the adapter exited non-zero: $(head -3 "$d/out")"
  got=$(cat "$d/argv.txt" 2>/dev/null || true)
  line=$(printf '%s\n' "$got" | grep -n -x -- '-e' | sed -n 2p | cut -d: -f1)
  [ -n "$line" ] || fail "the child is started with one extension only, so nothing refuses a shell tree search: $got"
  next=$(printf '%s\n' "$got" | sed -n "$((line + 1))p")
  assert_eq "$ext" "$next" "the second file the child is started with is the shell policy"
  assert_contains "$(cat "$ext")" 'tool_call' \
    "and that file is a hook on the tool call, not a flag that happens to be accepted"
  return 0
}

# --------------------------------------------------------------------------- the gate log's attribution (issue #689)
# Reading a red gate log is what an item keeps paying for after its first gate call (#682 47+30 of 186 shell calls,
# #50 24+25 of 234), so the failing case's mechanical facts come from one command over a log the script does not own.
# Every case below builds its log as a fixture: no gate log from a real run is copied into the repository, and the
# shape is the one `tools/loop/gate.sh` writes and the suites themselves print.

case_gate_aGreenLogNamesNoFailingCase() {
  # files: tools/gate-attribute.sh tools/loop/loop.conf tools/loop-cases.sh tools/loop-selftest.sh
  local d out
  d=$(scratch)
  cat > "$d/green.log" <<'LOG'
LOOP-GATE-PROFILE: tools/loop/loop.conf
LOOP-GATE-STEP-BOUND: 1800s per step — a step that reaches it is ended, named and counted as an error
gate: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
> Task :app:testMobileDebugUnitTest
com.naviveylin.ui.MapScreenTest > theMapIsDrawn PASSED
gate: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache — 345s
gate: tools/loop-selftest.sh --from-git abc1234
driver#aCase| a line a case printed while it ran
loop-selftest: all 190 cases passed (JDK 17, offline, no token, no sandbox)
gate: tools/loop-selftest.sh --from-git abc1234 — 12s
LOOP-GATE: tests=198 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 345s, tools/loop-selftest.sh --from-git abc1234 12s)
LOG
  out=$(bash "$LOOP_PROJECT_ROOT/tools/gate-attribute.sh" "$d/green.log" 2>&1) \
    || fail "the reader refused a green log: $out"
  assert_contains "$out" 'verdict: tests=198 failures=0 errors=0' "the verdict is the gate's own numbers: $out"
  assert_contains "$out" 'case: none — every case this log reports passed' "a green log names no failing case: $out"
  assert_not_contains "$out" 'rerun:' "and no command is printed for a case that did not fail: $out"
  assert_not_contains "$out" 'file:line:' "…and no location either: $out"
  return 0
}

case_gate_aFailingCaseCarriesItsFactsAndItsRerun() {
  # files: tools/gate-attribute.sh tools/loop/loop.conf tools/loop-cases.sh tools/loop-selftest.sh
  local d out
  d=$(scratch)
  cat > "$d/red.log" <<'LOG'
LOOP-GATE-PROFILE: tools/loop/loop.conf
gate: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
> Task :app:testMobileDebugUnitTest FAILED
com.naviveylin.ui.MapScreenTest > theMapIsDrawn FAILED
    java.lang.AssertionError: expected:<14.0> but was:<7.0>
        at com.naviveylin.ui.MapScreenTest.theMapIsDrawn(MapScreenTest.kt:198)
        at java.base/java.lang.reflect.Method.invoke(Method.java:580)
8 tests completed, 1 failed
gate: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache — 345s
LOOP-GATE: tests=8 failures=1 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 345s) — failing: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
LOG
  out=$(bash "$LOOP_PROJECT_ROOT/tools/gate-attribute.sh" "$d/red.log" 2>&1) \
    || fail "the reader refused a red log: $out"
  assert_contains "$out" 'step: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache' \
    "the failed step is the gate's own line: $out"
  assert_contains "$out" 'case: com.naviveylin.ui.MapScreenTest > theMapIsDrawn' "the failing case: $out"
  assert_contains "$out" 'file:line: MapScreenTest.kt:198' "the location the log printed: $out"
  assert_contains "$out" 'error: java.lang.AssertionError: expected:<14.0> but was:<7.0>' \
    "the failure's own first line, not its location: $out"
  # The flake line is a recorded blank and says so: §4d decides what a red case is, and a reader must not read
  # "no" about a case nobody classified.
  assert_contains "$out" 'flake: not recorded — this tree keeps no flake list' \
    "the flake line states what is recorded: $out"
  assert_contains "$out" 'rerun: ./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.MapScreenTest" -PforceTests -PjvmTestsOnly --no-build-cache' \
    "the rerun is the log's own task and flags with the failing class: $out"
  # One fact per line, because a block a child can read in one call is the whole point of the command.
  assert_eq "7" "$(printf '%s\n' "$out" | grep -c .)" "seven lines, one fact each: $out"
  return 0
}

case_gate_twoFailuresNameTheFirstAndCountTheRest() {
  # files: tools/gate-attribute.sh tools/loop/loop.conf tools/loop-cases.sh tools/loop-selftest.sh
  local d out
  d=$(scratch)
  cat > "$d/two.log" <<'LOG'
LOOP-GATE-PROFILE: tools/loop/loop.conf
gate: tools/loop-selftest.sh --from-git abc1234
driver#someOtherCase| a line a passing case printed
PASS driver#someOtherCase
driver#aCaseThatBlocksIsEndedByItsOwnBound|   the case ended at its bound, status 124 (tools/loop-cases.sh:6690)
FAIL driver#aCaseThatBlocksIsEndedByItsOwnBound
FAIL driver#aCaseThatLeavesAProcessBehindIsSweptWithIt
loop-selftest: 2 of 190 cases failed
gate: tools/loop-selftest.sh --from-git abc1234 — 240s
LOOP-GATE: tests=190 failures=2 errors=0 (tools/loop-selftest.sh --from-git abc1234 240s) — failing: tools/loop-selftest.sh --from-git abc1234
LOG
  out=$(bash "$LOOP_PROJECT_ROOT/tools/gate-attribute.sh" "$d/two.log" 2>&1) \
    || fail "the reader refused a two-failure log: $out"
  assert_contains "$out" 'verdict: tests=190 failures=2 errors=0 — 1 failing step' \
    "the verdict keeps the gate's counts: $out"
  assert_contains "$out" 'case: driver#aCaseThatBlocksIsEndedByItsOwnBound' "the first failing case: $out"
  assert_contains "$out" 'others: driver#aCaseThatLeavesAProcessBehindIsSweptWithIt' \
    "and the block names the other one, so two failures are not read as one: $out"
  assert_contains "$out" 'error: the case ended at its bound, status 124 (tools/loop-cases.sh:6690)' \
    "the case's own printed reason, without the relay's prefix: $out"
  assert_contains "$out" 'file:line: tools/loop-cases.sh:6690' "the location its reason carried: $out"
  assert_contains "$out" 'rerun: tools/loop-selftest.sh --from-git abc1234 --case driver#aCaseThatBlocksIsEndedByItsOwnBound' \
    "the rerun keeps the step's words and selects the one case: $out"
  return 0
}

case_gate_aStepThatNamesNoCaseIsAttributedByItsStep() {
  # files: tools/gate-attribute.sh tools/loop/loop.conf tools/loop-cases.sh tools/loop-selftest.sh
  local d out
  d=$(scratch)
  # The shape #268 measured: a compiled step that died at configuration, `tests=0 failures=0 errors=1`, no case ever
  # named. The step is still the fact, and the build's own first error line is what §4a reads.
  cat > "$d/compile.log" <<'LOG'
LOOP-GATE-PROFILE: tools/loop/loop.conf
gate: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
> Task :app:compileMobileDebugKotlin FAILED
e: file:///home/x/repo/app/src/main/java/com/naviveylin/ui/Map.kt:42:13 unresolved reference: drawable
FAILURE: Build failed with an exception.
gate: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache — 21s
LOOP-GATE: tests=0 failures=0 errors=1 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 21s) — failing: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
LOG
  out=$(bash "$LOOP_PROJECT_ROOT/tools/gate-attribute.sh" "$d/compile.log" 2>&1) \
    || fail "the reader refused a log whose step failed before a case ran: $out"
  assert_contains "$out" 'verdict: tests=0 failures=0 errors=1 — 1 failing step' "an error is attributed: $out"
  assert_contains "$out" 'step: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache' "the failed step: $out"
  assert_contains "$out" 'case: none — a step failed before a case reported' "no case is invented: $out"
  assert_contains "$out" 'error: e: file:///home/x/repo/app/src/main/java/com/naviveylin/ui/Map.kt:42:13 unresolved reference: drawable' \
    "the build's own error line, not the task that carried it: $out"
  assert_contains "$out" 'file:line: /home/x/repo/app/src/main/java/com/naviveylin/ui/Map.kt:42' \
    "its location, with the URL's scheme resolved: $out"
  assert_contains "$out" 'rerun: not derivable from this log' \
    "no rerun is invented for a step that named no test task: $out"
  return 0
}

case_gate_theBlockStaysInsideTheRecordedBound() {
  # files: tools/gate-attribute.sh tools/loop/loop.conf tools/loop-cases.sh tools/loop-selftest.sh
  local d out bound n
  d=$(scratch)
  cat > "$d/red.log" <<'LOG'
LOOP-GATE-PROFILE: tools/loop/loop.conf
gate: tools/loop-selftest.sh --from-git abc1234
driver#aCaseThatBlocksIsEndedByItsOwnBound|   the case ended at its bound, status 124 (tools/loop-cases.sh:6690)
FAIL driver#aCaseThatBlocksIsEndedByItsOwnBound
loop-selftest: 1 of 190 cases failed
gate: tools/loop-selftest.sh --from-git abc1234 — 240s
LOOP-GATE: tests=190 failures=1 errors=0 (tools/loop-selftest.sh --from-git abc1234 240s) — failing: tools/loop-selftest.sh --from-git abc1234
LOG
  # The bound is the profile's, read here as well so the case holds the number the run holds (#660).
  bound=$(sed -n 's/^LOOP_TOOL_RESULT_MAX_CHARS=["]\{0,1\}\([0-9][0-9]*\)["]\{0,1\}[[:space:]]*$/\1/p' \
    "$LOOP_ENGINE/loop.conf" | tail -1)
  [ -n "$bound" ] || fail "the profile records no LOOP_TOOL_RESULT_MAX_CHARS"
  out=$(bash "$LOOP_PROJECT_ROOT/tools/gate-attribute.sh" "$d/red.log" 2>&1) \
    || fail "the reader refused the log: $out"
  n=$(printf '%s' "$out" | wc -c | tr -d ' ')
  [ "$n" -le "$bound" ] \
    || fail "the block is $n characters, above the recorded bound of $bound: $out"
  # …and a smaller bound is honoured per line rather than by losing a fact: the block that arrives cut in half, its
  # last line the rerun, would be the round this command exists to save.
  out=$(bash "$LOOP_PROJECT_ROOT/tools/gate-attribute.sh" "$d/red.log" --max-chars 200 2>&1) \
    || fail "the reader refused a bound of 200 characters: $out"
  n=$(printf '%s' "$out" | wc -c | tr -d ' ')
  [ "$n" -le 200 ] || fail "the block is $n characters under a bound of 200: $out"
  assert_eq "7" "$(printf '%s\n' "$out" | grep -c .)" "every fact keeps its own line at a smaller bound: $out"
  return 0
}

# A gate step is the unit this project bounds (`LOOP_GATE_STEP_MAX_SECONDS`, 1 800 s), and the harness that started
# the gate kills the call at a ceiling below it (issue #692). A step bound above the caller's ceiling is not a bound:
# the caller dies first, mid-step, and the gate never says which step it was in. The caller names its ceiling and the
# step's clock is the smaller of the two — on the console before the first step starts, and in the line the overrun
# writes — so the gate ends the step itself and answers instead of being killed.
case_gate_theStepBoundIsClampedByTheCeilingTheCallerNamed() {
  # files: tools/loop/gate.sh tools/loop/lib.sh tools/loop/loop.conf tools/loop-cases.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf base log out status started elapsed
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_COMPILED_CMD=""\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS=""\n'
    printf 'LOOP_GATE_STEP_MAX_SECONDS=600\n'
    printf 'LOOP_GATE_SUITES="notes/*|bash %s/suite.sh"\n' "$d"
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
  } >> "$conf"
  # A suite that would outlast the ceiling: on the base the gate waits its 30 s and reads green.
  cat > "$d/suite.sh" <<'SUITE'
sleep 30
printf 'notes: all 1 cases passed\n'
SUITE
  mkdir -p "$p/notes"
  printf 'a note\n' > "$p/notes/a.md"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the change' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  log="$d/gate.log"
  started=$(date +%s)
  ( cd "$p" && LOOP_CONF="$conf" LOOP_CALL_CEILING_SECONDS=2 timeout 120 bash "$LOOP_ENGINE/gate.sh" --base "$base" ) > "$log" 2>&1
  status=$?
  elapsed=$(( $(date +%s) - started ))
  out=$(cat "$log")
  [ "$status" -ne 124 ] || fail "the gate was still inside a step after 120 s: $out"
  assert_contains "$out" 'LOOP-GATE-STEP-BOUND: 2s per step' \
    "the clock in force is the caller's ceiling, on the console before the first step: $out"
  assert_contains "$out" 'the step overran its bound of 2s and was ended' "the overrun names the bound: $out"
  assert_contains "$out" 'LOOP-GATE: tests=0 failures=0 errors=1' "and the step is the gate's error, not a green log: $out"
  assert_eq "1" "$status" "a step the ceiling ended is a gate failure"
  [ "$elapsed" -lt 20 ] || fail "the gate outlived the ceiling its caller named: ${elapsed}s"
  return 0
}

# ── one call prints where the phase stands (issue #688) ─────────────────────────────────────────────────────────
# The archive records `read:own-prompt -> read:own-prompt` 485x across 162 of 164 items and
# `read:own-prompt -> read:own-prompt -> sh:git` 146x across 116 — three calls a round (the phase prompt, the work
# package, `git`), re-injecting 8-17 kB at a context already at 85 k, 369 calls and 2.5 M characters over the archive
# (`python3 tools/loop-spend.py`, 2026-10-28). `tools/loop/phase.sh` answers those three in one call from the state
# the run already records and decides nothing. These cases are its two rules: the printed shape on a fixture state,
# and the bound it owes like every other result of the loop's tools.

# The state a phase boundary is read from: a throwaway project whose run state names an item, the item's own worktree
# one commit ahead of the base with a dirty file in it, the work package the run rendered, and the event stream its
# child left behind. It prints the project; the item's worktree is `<project>/.pi/worktrees/item`.
phase_fixture() {
  local p wt
  p=$(new_project)
  init_state "$p" 1 90
  run_state "$p" start 688 --change fix-issue-688-loop-spend >/dev/null || fail "state start failed"
  wt="$p/.pi/worktrees/item"
  git -C "$p" worktree add -q "$wt" -b fix/issue-688-loop-spend >/dev/null 2>&1 \
    || fail "could not create the item's worktree"
  printf 'the change\n' > "$wt/one.txt"
  git -C "$wt" add -A >/dev/null 2>&1
  git -C "$wt" commit -qm 'the item commit' >/dev/null 2>&1 || fail "could not commit in the item's worktree"
  printf 'dirty\n' > "$wt/dirty.txt"
  mkdir -p "$p/.pi/issue-loop/items/688"
  cat > "$p/.pi/issue-loop/items/688/work-package.md" <<'PKG'
---- issue work package ----
fingerprint: 0123456789abcdef
provenance: issue #688
issue: #688
title: the phase state is three calls away
<<<ISSUE_TEXT
- **What it owes**: a case for the printed shape, on a fixture state, and a bound on the result.
ISSUE_TEXT
---- end issue work package ----
PKG
  # Two gate lines and a step line between them: what the phase state prints is the *last* verdict, and a bound that
  # ended a step is not one.
  {
    printf '{"type":"tool_execution_end","text":"LOOP-GATE: tests=200 failures=3 errors=0 (an earlier focused run)"}\n'
    printf '{"type":"tool_execution_end","text":"LOOP-GATE-STEP: ./gradlew test exceeded 600 s — the gate ended it"}\n'
    printf '{"type":"tool_execution_end","text":"LOOP-GATE: tests=256 failures=0 errors=0 (tools/loop-selftest.sh 40s)"}\n'
  } > "$p/.pi/issue-loop/items/688/events.jsonl"
  printf '%s\n' "$p"
}

# One call carries what the child re-reads its prompt and the work package to rebuild: the phase's own checklist, the
# item and the change the run asked for, where the tree stands, the last gate verdict with its case counts, the paths
# the phase is expected to change, and the item's own text inside the delimiters it was rendered with. The checklist is
# the prompt's own section — one home for it — so what the call prints is what a second read of that file would say.
case_phase_oneCallPrintsThePhaseState() {
  # files: tools/loop/phase.sh tools/loop/prompts/worker.md tools/loop/prompts/worker-gate.md tools/loop/prompts/worker-tail.md tools/loop/loop.conf tools/loop/lib.sh tools/loop/state.sh tools/loop-cases.sh tools/loop-selftest.sh tools/pi-child-events.sh tools/tooling.json
  local phase p wt out status
  phase="$LOOP_PROJECT_ROOT/tools/loop/phase.sh"
  [ -f "$phase" ] || fail "nothing prints the phase state in one call: tools/loop/phase.sh is not in the tree (issue #688)"
  p=$(phase_fixture)
  wt="$p/.pi/worktrees/item"

  out=$( cd "$p" && LOOP_STATE_FILE="$p/.pi/issue-loop/run.json" LOOP_WORKTREE="$wt" bash "$phase" A 2>&1 ); status=$?
  assert_eq "0" "$status" "one call prints the phase state: $out"
  assert_contains "$out" 'LOOP-PHASE: A — root cause and the red test' \
    "the phase is named with the heading its own checklist carries: $out"
  assert_contains "$out" 'ITEM: #688 fix-issue-688-loop-spend' "the item and the change the run asked for are named: $out"
  assert_contains "$out" 'the state records phase A' "and the phase the run recorded is printed beside the one asked for: $out"
  # The checklist is the phase's own section of the prompt, so a phase's items arrive without a second read of it.
  assert_contains "$out" '3. Produce the **red test**' "the phase's own checklist is printed: $out"
  assert_contains "$out" '5. Count the surface before the plan is fixed.' "every item of it, not only the first: $out"
  assert_not_contains "$out" '## Phase B' "and only this phase's section of it: $out"
  # Where the tree stands, read live from the tree the item's work lands in.
  assert_contains "$out" 'GIT: branch fix/issue-688-loop-spend · HEAD ' "the branch and HEAD are printed: $out"
  assert_contains "$out" '· ahead 1 · behind 0' "with the item's commits ahead of the recorded base: $out"
  assert_contains "$out" 'DIRTY: 1 path(s): dirty.txt' "and the paths the tree has dirty: $out"
  # The last gate verdict the item's stream records, with the case counts it recorded.
  assert_contains "$out" 'GATE: green — tests=256 failures=0 errors=0' "the last gate verdict is the one printed: $out"
  assert_not_contains "$out" 'failures=3' "and not the round before it: $out"
  assert_not_contains "$out" 'LOOP-GATE-STEP' "nor a step bound for a verdict: $out"
  # The work package — identity from its own lines, and the item's text inside the delimiters it was rendered with.
  assert_contains "$out" 'fingerprint 0123456789abcdef' "the package's fingerprint is printed with it: $out"
  assert_contains "$out" 'provenance issue #688' "and its provenance: $out"
  assert_contains "$out" '<<<ISSUE_TEXT' "the item's text stays inside its delimiters: $out"
  assert_contains "$out" '- **What it owes**' "and is printed as it stands: $out"
  assert_contains "$out" 'PATHS: none yet' "the paths phase A is expected to change are named: $out"

  out=$( cd "$p" && LOOP_STATE_FILE="$p/.pi/issue-loop/run.json" LOOP_WORKTREE="$wt" bash "$phase" C 2>&1 )
  assert_contains "$out" 'PATHS: openspec/changes/fix-issue-688-loop-spend/ — proposal.md' \
    "the phase that plans the change names the change's own files: $out"
  assert_contains "$out" 'always — ' "and the item's evidence copies are named for every phase: $out"

  # Phase F is the tail child's phase (issue #744), so its checklist is read from that child's own prompt: the
  # process that is handed the phase is the process whose prompt states it, and the file the call names is that one.
  out=$( cd "$p" && LOOP_STATE_FILE="$p/.pi/issue-loop/run.json" LOOP_WORKTREE="$wt" bash "$phase" F 2>&1 )
  assert_contains "$out" 'LOOP-PHASE: F' "phase F is a phase of the run: $out"
  assert_contains "$out" "CHECKLIST: $LOOP_ENGINE/prompts/worker-tail.md" \
    "and its checklist is the tail child's own prompt: $out"
  assert_contains "$out" '1. **Read the gate result you were handed, and never re-run it.**' \
    "whose items are printed as they stand: $out"
  # …and the section is that phase's alone: phase F's checklist names the archive and never the apply step of the
  # prompt whose child has already ended.
  assert_contains "$out" 'Archive the change' "the phase's own archive step is printed: $out"
  assert_not_contains "$out" 'Use the apply and verification skills' "and no other phase's items: $out"

  # Phase E is the gate child's phase (issue #758): the run defers the gate to that child, so its checklist is read
  # from the prompt that child is started from — the same rule phase F follows since #744.
  out=$( cd "$p" && LOOP_STATE_FILE="$p/.pi/issue-loop/run.json" LOOP_WORKTREE="$wt" bash "$phase" E 2>&1 ); status=$?
  assert_eq "0" "$status" "phase E is a phase of the run: $out"
  assert_contains "$out" "CHECKLIST: $LOOP_ENGINE/prompts/worker-gate.md" \
    "and its checklist is the gate child's own prompt: $out"
  assert_contains "$out" '1. **Apply the findings, if there are any, and nothing else.**' \
    "whose items are printed as they stand: $out"
  assert_not_contains "$out" 'Use the apply and verification skills' "and no other phase's items: $out"

  # A phase no prompt carries a section for says so rather than inventing a checklist, and a phase outside the set
  # the run records a step against is a usage error.
  out=$( cd "$p" && LOOP_STATE_FILE="$p/.pi/issue-loop/run.json" LOOP_WORKTREE="$wt" bash "$phase" prescreen 2>&1 ); status=$?
  assert_eq "0" "$status" "a phase with no section of its own is still a phase of the run: $out"
  assert_contains "$out" 'the prompt carries no section for phase prescreen' "and the absence is named instead of invented: $out"
  out=$( cd "$p" && LOOP_STATE_FILE="$p/.pi/issue-loop/run.json" bash "$phase" Z 2>&1 ); status=$?
  assert_eq "2" "$status" "a phase outside the recorded set is a usage error: $out"
  assert_contains "$out" 'is not one of prescreen A B C D E F' "and the refusal names the set: $out"
  out=$( cd "$p" && LOOP_STATE_FILE="$p/no-such-state.json" bash "$phase" A 2>&1 ); status=$?
  assert_eq "2" "$status" "a run that has written no state is refused instead of printed as empty: $out"
  assert_contains "$out" 'has written no state' "and the refusal says what is missing: $out"
  return 0
}

# The bound is a rule about the tail (issue #660), and this tool owes it like the others: the answer is printed once and
# re-read by every later turn, so one long work package would cost what the three calls it replaced cost together. The
# number is the project's recorded `LOOP_TOOL_RESULT_MAX_CHARS` — one home for it — and a cut says it is a cut, because
# a reader who cannot tell a short answer from a truncated one re-runs the call.
case_phase_aResultOverTheBoundIsCutAndNamed() {
  # files: tools/loop/phase.sh tools/loop/prompts/worker.md tools/loop/loop.conf tools/loop/lib.sh tools/loop/state.sh tools/loop-cases.sh tools/loop-selftest.sh tools/pi-child-events.sh tools/tooling.json
  local phase p wt out size recorded status
  phase="$LOOP_PROJECT_ROOT/tools/loop/phase.sh"
  [ -f "$phase" ] || fail "nothing bounds a phase question: tools/loop/phase.sh is not in the tree (issue #688)"
  p=$(phase_fixture)
  wt="$p/.pi/worktrees/item"
  # A work package far over any bound this project records, every line named by its number so the tail a cut leaves out
  # can be asserted by its text.
  {
    printf -- '---- issue work package ----\nfingerprint: 0123456789abcdef\nprovenance: issue #688\nissue: #688\ntitle: a long package\n<<<ISSUE_TEXT\n'
    awk 'BEGIN { for (i = 1; i <= 300; i++) printf "PKG-LINE-%03d %s\n", i, "0123456789012345678901234567890123456789" }'
    printf 'ISSUE_TEXT\n---- end issue work package ----\n'
  } > "$p/.pi/issue-loop/items/688/work-package.md"
  recorded=$(sed -n 's/^LOOP_TOOL_RESULT_MAX_CHARS=["]\{0,1\}\([0-9][0-9]*\)["]\{0,1\}[[:space:]]*$/\1/p' \
    "$LOOP_PROJECT_ROOT/tools/loop/loop.conf" | tail -1)
  [ -n "$recorded" ] || fail "the project records no LOOP_TOOL_RESULT_MAX_CHARS in tools/loop/loop.conf"

  ( cd "$p" && env -u LOOP_TOOL_RESULT_MAX_CHARS LOOP_STATE_FILE="$p/.pi/issue-loop/run.json" LOOP_WORKTREE="$wt" \
      bash "$phase" A ) > "$p/recorded.txt" 2>&1
  size=$(wc -c < "$p/recorded.txt" | tr -d ' ')
  out=$(cat "$p/recorded.txt")
  [ "$size" -le "$recorded" ] || fail "the answer carried $size characters against the recorded bound of $recorded"
  assert_contains "$out" 'PHASE-BOUND' "a cut answer says it was cut: $out"
  assert_contains "$out" 'LOOP-PHASE: A' "and the phase state the call exists for survives the cut: $out"
  assert_contains "$out" 'GIT: branch fix/issue-688-loop-spend' "the tree it stands in survives it too: $out"
  assert_contains "$out" 'PKG-LINE-001' "the package's head is part of the answer: $out"
  assert_not_contains "$out" 'PKG-LINE-300' "the tail it could not show is not there: $out"

  ( cd "$p" && LOOP_TOOL_RESULT_MAX_CHARS=900 LOOP_STATE_FILE="$p/.pi/issue-loop/run.json" LOOP_WORKTREE="$wt" \
      bash "$phase" A ) > "$p/env.txt" 2>&1
  size=$(wc -c < "$p/env.txt" | tr -d ' ')
  [ "$size" -le 900 ] || fail "a bound the environment names is not the one applied: $size characters against 900"
  assert_contains "$(cat "$p/env.txt")" 'PHASE-BOUND' "and the answer says which bound cut it"

  ( cd "$p" && LOOP_TOOL_RESULT_MAX_CHARS=0 LOOP_STATE_FILE="$p/.pi/issue-loop/run.json" LOOP_WORKTREE="$wt" \
      bash "$phase" A ) > "$p/unbounded.txt" 2>&1
  size=$(wc -c < "$p/unbounded.txt" | tr -d ' ')
  [ "$size" -gt "$recorded" ] || fail "no bound was recorded as $recorded characters and the whole answer is $size"
  assert_contains "$(cat "$p/unbounded.txt")" 'PKG-LINE-300' "with no bound the whole answer is printed"
  assert_not_contains "$(cat "$p/unbounded.txt")" 'PHASE-BOUND' "and nothing is said about a bound it never met"
  return 0
}

# A child that reads the same file twice is handed the same text twice (issue #685): `LOOP-SPEND-READS calls=6021
# duplicate=2847` over the archive the loop reads — 47 % of its reads are a second read of a path that session already
# had, and each one is injected once more and re-read by every turn after it. The bound of `trim#*` does not know the
# child holds that text. So a repeat is replaced by one line — the path, the size, the turn the text came in at, and
# the range syntax that asks for part of it — which is the rule `tools/pi-trim-result.mjs`'s `tool_result` hook
# applies, driven through the same file's CLI so the case and the child cannot drift.
case_repeat_aRepeatReadIsNamedNotHandedOverAgain() {
  # files: tools/loop-cases.sh tools/pi-trim-result.mjs
  local d file out size lines
  [ -f "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" ] \
    || fail "nothing knows what a session already read: tools/pi-trim-result.mjs is not in the tree (issue #685)"
  d=$(scratch)
  file="$d/section.md"
  { printf 'FIRST-LINE\n'; head -c 600 /dev/zero | tr '\0' 'x'; printf '\nLAST-LINE\n'; } > "$file"
  size=$(wc -c < "$file" | tr -d ' ')
  out="$d/first.txt"
  node "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" --bound 8000 --tool ctx_read \
    --path "$file" --turn 3 --ledger "$d/ledger.txt" < "$file" > "$out" \
    || fail "the result rule exited non-zero on a first read: $(head -3 "$out")"
  assert_eq "$(cat "$file")" "$(cat "$out")" "a first read is handed over whole"
  out="$d/second.txt"
  node "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" --bound 8000 --tool ctx_read \
    --path "$file" --turn 5 --ledger "$d/ledger.txt" < "$file" > "$out" \
    || fail "the result rule exited non-zero on a repeat read: $(head -3 "$out")"
  assert_not_contains "$(cat "$out")" 'FIRST-LINE' "a repeat does not put the text into the context a second time"
  assert_not_contains "$(cat "$out")" 'LAST-LINE' "not even the line an earlier result was trusted for"
  assert_contains "$(cat "$out")" 'LOOP-ALREADY-READ' "it is replaced by one line that says the session has it"
  lines=$(grep -c . "$out")
  [ "$lines" -eq 1 ] || fail "the repeat read carried $lines non-empty lines instead of one: $(cat "$out")"
  assert_contains "$(cat "$out")" "$file" "the line names the path the session holds"
  assert_contains "$(cat "$out")" "$size characters" "and the size the earlier read had"
  assert_contains "$(cat "$out")" 'turn 3' "and the turn the text came in at, not the turn the repeat came in on"
  assert_contains "$(cat "$out")" 'ctx_read mode="lines:N-M"' "and the call that reads a range of it instead"
  return 0
}

# …and a repeat whose content changed is not a repeat (issue #685): 728 of the archive's 2 042 per-session repeats
# follow an edit of that path, and that read is a verification — the child is checking what its own edit did, so
# withholding it would cost a turn or a wrong edit. The rule therefore compares the content and not the path: a read
# whose sha differs is handed over whole, and it becomes the content the next repeat is measured against, which the
# third read of this case asserts by the size and the turn it names.
case_repeat_aRepeatAfterAnEditIsHandedOverWhole() {
  # files: tools/loop-cases.sh tools/pi-trim-result.mjs
  local d file out size
  [ -f "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" ] \
    || fail "nothing knows what a session already read: tools/pi-trim-result.mjs is not in the tree (issue #685)"
  d=$(scratch)
  file="$d/section.md"
  { printf 'FIRST-LINE\n'; head -c 600 /dev/zero | tr '\0' 'y'; printf '\nLAST-LINE\n'; } > "$file"
  node "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" --bound 8000 --tool ctx_read \
    --path "$file" --turn 3 --ledger "$d/ledger.txt" < "$file" > "$d/first.txt" \
    || fail "the result rule exited non-zero on the first read: $(head -3 "$d/first.txt")"
  # the session's own edit of the path it has already read
  printf 'EDITED-LINE\n' >> "$file"
  out="$d/after-edit.txt"
  node "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" --bound 8000 --tool ctx_read \
    --path "$file" --turn 4 --ledger "$d/ledger.txt" < "$file" > "$out" \
    || fail "the result rule exited non-zero on the read after an edit: $(head -3 "$out")"
  assert_eq "$(cat "$file")" "$(cat "$out")" "a read after the session edited the path is handed over whole"
  assert_contains "$(cat "$out")" 'EDITED-LINE' "so the edit is what the child reads"
  size=$(wc -c < "$file" | tr -d ' ')
  out="$d/after-edit-repeat.txt"
  node "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" --bound 8000 --tool ctx_read \
    --path "$file" --turn 6 --ledger "$d/ledger.txt" < "$file" > "$out" \
    || fail "the result rule exited non-zero on the repeat of the edited text: $(head -3 "$out")"
  assert_contains "$(cat "$out")" 'LOOP-ALREADY-READ' "the unchanged read after it is a repeat again"
  assert_contains "$(cat "$out")" "$size characters" "measured against the content the edit left, not the first read"
  assert_contains "$(cat "$out")" 'turn 4' "at the turn that content came in"
  return 0
}

# The recorded `0` is the switch (issue #685): `LOOP_TOOL_RESULT_MAX_CHARS` recording `0` leaves the whole file off, as
# it leaves the size bound off, so a session can be handed every read whole when a repeat costs more than it saves.
# What is asserted is both halves — the repeat is not replaced, and no ledger is kept for it to be recognised by.
case_repeat_theRecordedZeroKeepsTheRepeatRuleOff() {
  # files: tools/loop-cases.sh tools/pi-trim-result.mjs
  local d file out
  [ -f "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" ] \
    || fail "nothing knows what a session already read: tools/pi-trim-result.mjs is not in the tree (issue #685)"
  d=$(scratch)
  file="$d/section.md"
  { printf 'FIRST-LINE\n'; head -c 600 /dev/zero | tr '\0' 'z'; printf '\nLAST-LINE\n'; } > "$file"
  node "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" --bound 0 --tool ctx_read \
    --path "$file" --turn 3 --ledger "$d/ledger.txt" < "$file" > "$d/first.txt" \
    || fail "the result rule exited non-zero with no bound: $(head -3 "$d/first.txt")"
  assert_eq "$(cat "$file")" "$(cat "$d/first.txt")" "with no bound the first read is handed over whole"
  out="$d/second.txt"
  node "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" --bound 0 --tool ctx_read \
    --path "$file" --turn 5 --ledger "$d/ledger.txt" < "$file" > "$out" \
    || fail "the result rule exited non-zero with no bound on a repeat: $(head -3 "$out")"
  assert_eq "$(cat "$file")" "$(cat "$out")" "and so is the repeat: no bound is no repeat rule"
  assert_not_contains "$(cat "$out")" 'LOOP-ALREADY-READ' "and nothing says the session has it"
  [ ! -s "$d/ledger.txt" ] || fail "a bound of 0 still keeps a ledger: $(cat "$d/ledger.txt")"
  return 0
}

# The ledger is bookkeeping and never the answer (issue #685): the rule must not be able to fail a tool mid-item,
# because a read a child cannot get costs a turn and a stub that withholds what it asked for costs a wrong edit. A
# ledger this process can neither read nor write — a directory where the file belongs — therefore leaves the result
# exactly as the tool returned it, and the call still exits 0.
case_repeat_aBrokenLedgerLeavesTheResultAsReturned() {
  # files: tools/loop-cases.sh tools/pi-trim-result.mjs
  local d file out status
  [ -f "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" ] \
    || fail "nothing knows what a session already read: tools/pi-trim-result.mjs is not in the tree (issue #685)"
  d=$(scratch)
  file="$d/section.md"
  { printf 'FIRST-LINE\n'; head -c 600 /dev/zero | tr '\0' 'w'; printf '\nLAST-LINE\n'; } > "$file"
  mkdir -p "$d/ledger-dir"
  out="$d/broken.txt"
  node "$LOOP_PROJECT_ROOT/tools/pi-trim-result.mjs" --bound 8000 --tool ctx_read \
    --path "$file" --turn 3 --ledger "$d/ledger-dir" < "$file" > "$out"
  status=$?
  assert_eq "0" "$status" "a ledger the process cannot read or write does not fail the read"
  assert_eq "$(cat "$file")" "$(cat "$out")" "and the result is exactly what the tool returned"
  return 0
}

# The child's bound was a clock and nothing else (issue #686): over this project's archive the 35 items at ≥250 turns
# held 878 of 1 982 Mtok — 44 % of everything the archive spent — and every one of them was ended only after 45 min,
# or not at all. The bound is the recorded per-role `LOOP_CHILD_TURNS_MAX_<ROLE>` (here the worker's), counted from the
# turns that child added to the item's own event stream (`items/<n>/events.jsonl`, one `turn_end` per turn) where the
# wall clock is already enforced. This case does not wait
# for the bound: the child appends a fixture stream of three turns and then blocks, and the recorded value is 3, so a
# tree that reads neither the key nor the stream leaves the run waiting until the case's own bound ends it. The item
# is recorded `cut`, not `blocked` (issue #790): the bound is the run's own and ends a phase, not the item.
case_run_childPastItsTurnBoundIsCut() {
  # files: tools/loop/fixtures/pi tools/loop/loop.conf tools/loop/report.sh tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fifo fixture action pid i status out report
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  conf="$d/loop.conf"
  # The wall clock keeps its recorded default, so the only bound this case can reach is the worker's own turn one: a
  # case that shortened both would pass on a tree that ended the child for time and never counted a turn.
  sed -e 's/^LOOP_CHILD_TURNS_MAX_WORKER=.*/LOOP_CHILD_TURNS_MAX_WORKER=3/' "$LOOP_CONF" > "$conf" || fail "could not record a small turn bound"
  grep -qx 'LOOP_CHILD_TURNS_MAX_WORKER=3' "$conf" || fail "this case could not shorten the worker's turn bound in loop.conf"
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  # The child's own stream, in the shape the adapter keeps: one `turn_end` line per turn, appended to the item's
  # event file the run exported. The stub prints `PI_STUB_EVENTS` only after it has been released, so a stream that
  # has to exist *while* the child runs is written by its action — the same file, the same lines.
  fixture="$d/events.jsonl"
  for i in 1 2 3; do printf '{"type":"turn_end","message":{"role":"assistant"}}\n'; done > "$fixture"
  action="$d/turn-child.sh"
  cat > "$action" <<EOF
#!/usr/bin/env bash
ev="\${LOOP_CHILD_EVENTS:-}"
[ -n "\$ev" ] || exit 1
cat "$fixture" >> "\$ev"
EOF
  export PI_STUB_ACTION="$action"
  fifo="$d/wait.fifo"; mkfifo "$fifo"
  # The child blocks on the fifo and is never released: without the turn bound the run is still waiting on it long
  # after the case's own bound, which is what makes this case red on the tree before the fix.
  set -m
  ( export LOOP_CONF="$conf" PI_STUB_WAIT="$fifo"
    run_loop "$p" --items 1 --minutes 90 --review off > "$d/out" 2>&1 ) &
  pid=$!
  set +m
  wait_for_stub_child "$fifo.ready" || true
  [ -e "$fifo.ready" ] || fail "the stub child never started"
  # The case holds the fifo's writer side for its whole life, so a child that is released never blocks and never
  # finds a writer behind it. 60 s is the case's own margin on a host that need not be free (issue #308).
  exec 8<> "$fifo"
  for i in $(seq 1 600); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  if kill -0 "$pid" 2>/dev/null; then
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    printf 'go\n' >&8 2>/dev/null || true
    wait "$pid" 2>/dev/null
    exec 8>&-
    fail "the run was still waiting after 60 s on a child that had taken 3 turns past a recorded bound of 3"
  fi
  wait "$pid"; status=$?
  exec 8>&-
  out=$(cat "$d/out")
  assert_eq "0" "$status" "a child the run itself ended is not a failed run"
  assert_contains "$out" "child-turns-max=worker:3" "the run's header names the worker's turn bound it enforces"
  assert_contains "$out" "exit 124" "with the wall clock's own status, so the run's 'exited N' path records the cut"
  report="$p/.pi/issue-loop/items/21/worker-report.md"
  [ -f "$report" ] || fail "the item kept no transcript of the child the turn bound ended"
  assert_contains "$(cat "$report")" "LOOP-TIMEOUT: the child reached the recorded turn bound of 3 turns (LOOP_CHILD_TURNS_MAX_WORKER for the worker role)" \
    "the transcript says which bound ended the child, in the child's own words"
  assert_contains "$(cat "$report")" "after 3 turn(s)" "and the count it reached, read from the child's own stream"
  assert_not_contains "$(cat "$report")" "the child exceeded" \
    "the wall clock took no part in it — the recorded one still had 45 min to run"
  assert_eq "cut" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "an item whose child crossed the bound is recorded cut, not blocked (issue #790)"
  assert_eq "cut" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" \
    "and its verdict is the cut too, which is what the run's own report counts"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "turn bound of 3 turns" \
    "and the reason on the item carries the count, not only the exit status"
  # …and the cut says *why* it is no question (issues #786, #790): the mechanism is `bound`, so `next` never reads it
  # as a refusal, no decision label is written for it, and the record itself says the attempt waits for the next run.
  assert_eq "bound" "$(jq -r '.items[0].stopReason // ""' "$(state_file "$p")")" \
    "the cut records the run's own bound as its mechanism"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "left for the next run rather than asked about" \
    "and says the attempt is left for the next run"
  assert_eq "0" "$(grep -c 'add-label agent/needs-decision' "$GH_STUB_RECORD" || true)" \
    "no decision label is written for an item the run's own bound ended"
  assert_eq "0" "$(grep -c 'issue comment' "$GH_STUB_RECORD" || true)" \
    "and no retry-or-abandon comment is written for it"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "nothing is closed on a child the run ended"
  # The report reads the item off its result alone (issue #790): it is on the run's own bound line and not in the
  # block tally, so no later reader needs the mechanism's vocabulary to tell work from a refusal.
  out=$(run_report "$p")
  assert_contains "$out" "blocked 0 · cut 1" "the report counts the item as a cut and not as a block"
  assert_contains "$out" "cut short by the run's own bound: #21" \
    "and names it on the line the run's own bound owns"
  return 0
}

# A rotated stream must keep counting (issue #802): the adapter drops the oldest half of the lines and records how
# many turns went with them, and a turn bound that read only the lines still present would fall back to zero every
# time the writer rotated — the child that had already spent its turns would run on. The stream here holds one
# `turn_end` line and a record that says two were dropped: a bound of three is reached, and on the tree before the
# fix the run waits on a child that has taken one turn for ever.
case_run_aRotatedStreamStillCountsTheTurnsItDropped() {
  # files: tools/loop/fixtures/pi tools/loop/loop.conf tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fifo fixture action pid i status out report
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  conf="$d/loop.conf"
  sed -e 's/^LOOP_CHILD_TURNS_MAX_WORKER=.*/LOOP_CHILD_TURNS_MAX_WORKER=3/' "$LOOP_CONF" > "$conf" ||
    fail "could not record a small turn bound"
  grep -qx 'LOOP_CHILD_TURNS_MAX_WORKER=3' "$conf" || fail "this case could not shorten the worker's turn bound"
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  fixture="$d/events.jsonl"
  printf '%s\n' '{"type":"stream_rotation","dropped_lines":12,"dropped_bytes":1200,"dropped_turn_ends":2,"at":"2026-01-01T00:00:00Z"}' > "$fixture"
  printf '%s\n' '{"type":"turn_end","message":{"role":"assistant"}}' >> "$fixture"
  action="$d/turn-child.sh"
  cat > "$action" <<EOF
#!/usr/bin/env bash
ev="\${LOOP_CHILD_EVENTS:-}"
[ -n "\$ev" ] || exit 1
cat "$fixture" >> "\$ev"
EOF
  export PI_STUB_ACTION="$action"
  fifo="$d/wait.fifo"; mkfifo "$fifo"
  set -m
  ( export LOOP_CONF="$conf" PI_STUB_WAIT="$fifo"
    run_loop "$p" --items 1 --minutes 90 --review off > "$d/out" 2>&1 ) &
  pid=$!
  set +m
  wait_for_stub_child "$fifo.ready" || true
  [ -e "$fifo.ready" ] || fail "the stub child never started"
  exec 8<> "$fifo"
  for i in $(seq 1 600); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  if kill -0 "$pid" 2>/dev/null; then
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    printf 'go\n' >&8 2>/dev/null || true
    wait "$pid" 2>/dev/null
    exec 8>&-
    fail "the run was still waiting on a child whose stream records 2 dropped turns and holds 1, past a bound of 3"
  fi
  wait "$pid"; status=$?
  exec 8>&-
  out=$(cat "$d/out")
  assert_eq "0" "$status" "a child the run itself ended is not a failed run"
  report="$p/.pi/issue-loop/items/21/worker-report.md"
  [ -f "$report" ] || fail "the item kept no transcript of the child the turn bound ended"
  assert_contains "$(cat "$report")" "after 3 turn(s)" \
    "the count the bound reached includes the turns the rotated stream no longer holds"
  assert_contains "$(cat "$report")" "turn bound of 3 turns" "and the transcript names the bound it reached"
  assert_eq "cut" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "an item whose child crossed the bound is recorded cut, not blocked (issue #790)"
  assert_eq "bound" "$(jq -r '.items[0].stopReason' "$(state_file "$p")")" \
    "and the bound that ended it is kept beside the result"
  return 0
}

# The recorded `0` is the switch (issue #686): a project whose children should be bounded by their clock alone — or
# one whose agent writes no event stream at all — records that role's key as `0`, as the memory floor's `0` disables
# the floor and the token warning's `0` records no band. The child here takes three turns and then outlives the
# two-second wall clock this case records, so which bound ended it is readable in the transcript: the clock's, never
# a turn bound that is off.
case_run_theRecordedZeroKeepsTheTurnBoundOff() {
  # files: tools/loop/fixtures/pi tools/loop/loop.conf tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fixture action out status report
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  conf="$d/loop.conf"
  conf_with_child_cap 2 "$conf"
  sed -e 's/^LOOP_CHILD_TURNS_MAX_WORKER=.*/LOOP_CHILD_TURNS_MAX_WORKER=0/' "$conf" > "$conf.zero" || fail "could not record the turn bound off"
  mv "$conf.zero" "$conf"
  grep -qx 'LOOP_CHILD_TURNS_MAX_WORKER=0' "$conf" || fail "this case could not record the worker's turn bound off in loop.conf"
  export LOOP_CONF="$conf"
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  # The same fixture stream the bound-on case appends, written by the child's action so the run can see it while the
  # child still runs: with the bound off it must change nothing about how the child ends.
  fixture="$d/events.jsonl"
  for i in 1 2 3; do printf '{"type":"turn_end","message":{"role":"assistant"}}\n'; done > "$fixture"
  action="$d/turn-child.sh"
  cat > "$action" <<EOF
#!/usr/bin/env bash
ev="\${LOOP_CHILD_EVENTS:-}"
[ -n "\$ev" ] || exit 1
cat "$fixture" >> "\$ev"
sleep 5
EOF
  export PI_STUB_ACTION="$action"
  out=$(run_loop "$p" --items 1 --minutes 90 --review off 2>&1); status=$?
  assert_eq "0" "$status" "a child the run itself ended is not a failed run"
  assert_contains "$out" "child-turns-max=worker:0" "the run's header says the worker's turn bound is off"
  report="$p/.pi/issue-loop/items/21/worker-report.md"
  [ -f "$report" ] || fail "the item kept no transcript of the child the wall clock ended"
  assert_contains "$(cat "$report")" "LOOP-TIMEOUT: the child exceeded 2 s" \
    "with the bound off, the recorded wall clock is the one that ends the child"
  assert_not_contains "$(cat "$report")" "turn bound" "and no turn bound is named in its transcript"
  assert_eq "cut" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "the item is recorded cut: the clock that ended it is the run's bound, not a finding about the item"
  return 0
}

# The turn bound was the **item's**, one pot every child of the item spent from, so it decided which phase was
# discarded and the last phase inherited the remainder (issue #784): measured 2026-11-05, #780 spent 80/34/42/41
# turns on its worker, its round before the gate, its gate and its tail and ended the round-1 reviewer at 53 of the
# same 250, and #767 gave its round-2 worker three. Each child is bounded by its own role's key now, and the item's
# old pot is not read at all — this case records one small enough to end the reviewer before its first turn and
# asserts the reviewer instead reaches its own bound of eight turns while the worker keeps the twenty its role
# records. A tree that still reads the item's pot ends the reviewer at the item's bound and says so.
case_run_eachChildIsBoundedByItsOwnRolesTurns() {
  # files: tools/loop/fixtures/pi tools/loop/loop.conf tools/loop/report.sh tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fifo worker_fixture reviewer_fixture extra action pid i status out state reviewer_report worker_report
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  conf="$d/loop.conf"
  cp "$LOOP_CONF" "$conf" || fail "could not copy loop.conf"
  # The keys are appended rather than edited in place: a tree that does not know them yet must still read the item
  # pot below, which is the only bound it has, and the case then fails on the reviewer's transcript instead of on a
  # `sed` that found no line to rewrite.
  {
    printf 'LOOP_CHILD_TURNS_MAX_WORKER=20\n'
    printf 'LOOP_CHILD_TURNS_MAX_REVIEWER=8\n'
    printf 'LOOP_ITEM_TURNS_MAX=10\n'
  } >> "$conf"
  grep -qx 'LOOP_CHILD_TURNS_MAX_REVIEWER=8' "$conf" || fail "this case could not record the reviewer's turn bound"
  # The worker takes ten turns — the whole of the item pot this case records — and returns; the reviewer takes its
  # eight and then waits, so the run has to end it at its own bound rather than before it can start.
  worker_fixture="$d/worker-events.jsonl"
  for i in $(seq 1 10); do printf '{"type":"turn_end","message":{"role":"assistant"}}\n'; done > "$worker_fixture"
  reviewer_fixture="$d/reviewer-events.jsonl"
  for i in $(seq 1 8); do printf '{"type":"turn_end","message":{"role":"assistant"}}\n'; done > "$reviewer_fixture"
  fifo="$d/review.fifo"; mkfifo "$fifo"
  action="$d/turns.sh"
  cat > "$action" <<EOF
case "\${1##*/}" in
  worker-prompt.md)
    ev="\${LOOP_CHILD_EVENTS:-}"
    [ -n "\$ev" ] || exit 1
    cat "$worker_fixture" >> "\$ev"
    ;;
  reviewer-prompt-1.md)
    ev="\${LOOP_CHILD_EVENTS:-}"
    [ -n "\$ev" ] || exit 1
    cat "$reviewer_fixture" >> "\$ev"
    : > "$fifo.ready"
    read -r _ < "$fifo" || true
    ;;
 esac
EOF
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' "$(cat "$action")"
  set -m
  ( export LOOP_CONF="$conf"
    run_loop "$p" --items 1 --minutes 90 --review on > "$d/out" 2>&1 ) &
  pid=$!
  set +m
  wait_for_stub_child "$fifo.ready" || true
  [ -e "$fifo.ready" ] || fail "the reviewer never started; the run said: $(cat "$d/out")"
  exec 8<> "$fifo"
  for i in $(seq 1 600); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  if kill -0 "$pid" 2>/dev/null; then
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    printf 'go\n' >&8 2>/dev/null || true
    wait "$pid" 2>/dev/null
    exec 8>&-
    fail "the run was still waiting 60 s on a reviewer that had taken 8 turns past a recorded bound of 8"
  fi
  wait "$pid"; status=$?
  exec 8>&-
  out=$(cat "$d/out")
  state=$(state_file "$p")
  assert_eq "0" "$status" "a run whose reviewer the role's turn bound ended is not a failed run"
  assert_contains "$out" "child-turns-max=worker:20" "the header names the worker's own bound"
  assert_contains "$out" ",reviewer:8," "and the reviewer's beside it"
  worker_report="$p/.pi/issue-loop/items/21/worker-report.md"
  reviewer_report="$p/.pi/issue-loop/items/21/reviewer-report-1.md"
  [ -f "$worker_report" ] || fail "the worker kept no transcript"
  [ -f "$reviewer_report" ] || fail "the round the turn bound ended kept no transcript"
  assert_not_contains "$(cat "$worker_report")" "LOOP-TIMEOUT" \
    "the ten turns the worker spent are its own bound's business, not the item's"
  assert_contains "$(cat "$reviewer_report")" \
    "the child reached the recorded turn bound of 8 turns (LOOP_CHILD_TURNS_MAX_REVIEWER for the reviewer role)" \
    "and the reviewer is ended by its own bound, not by the pot the worker had already spent"
  assert_eq "cut" "$(jq -r '.items[0].result' "$state")" "the item is recorded cut"
  assert_eq "cut-short" "$(jq -r '.items[0].metrics.reviewVerdict' "$state")" \
    "and its round is recorded as cut, not as a refusal no reviewer made"
  return 0
}

# A role whose key is `0` runs unbounded (issue #784), and the item's total is still counted and reported: the bound
# is the phase's, so switching one phase's off must not switch another's on, and the item's total is what the
# report's `calls:` line reads (`metrics.calls`, `metrics.turns`) and what `LOOP_ITEM_TOKENS_WARN` bands — a
# warning's business, not a stop's. This case records the worker's key as `0` beside an item pot of one turn, which
# the tree before this change reads and this one does not: the child takes its three turns, the item closes, and the
# state and the report carry the total of three.
case_run_aRoleWithNoRecordedTurnBoundRunsUnbounded() {
  # files: tools/loop/fixtures/pi tools/loop/loop.conf tools/loop/report.sh tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fixture action out status state report
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  conf="$d/loop.conf"
  cp "$LOOP_CONF" "$conf" || fail "could not copy loop.conf"
  {
    printf 'LOOP_CHILD_TURNS_MAX_WORKER=0\n'
    printf 'LOOP_ITEM_TURNS_MAX=1\n'
  } >> "$conf"
  grep -qx 'LOOP_CHILD_TURNS_MAX_WORKER=0' "$conf" || fail "this case could not record the worker's turn bound off"
  export LOOP_CONF="$conf"
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  fixture="$d/events.jsonl"
  for i in 1 2 3; do printf '{"type":"turn_end","message":{"role":"assistant"}}\n'; done > "$fixture"
  # The worker's own action, appended to the stub's own: the tree the stub leaves is what the item's verdict is read
  # from, so a case that *replaced* the stub's action would block the item for a tree nothing had changed. Only the
  # worker appends turns — the item's tail child is a role of its own and pays none.
  action="$d/turn-child.sh"
  cat > "$action" <<EOF
case "\${1##*/}" in
  worker-prompt.md)
    ev="\${LOOP_CHILD_EVENTS:-}"
    [ -n "\$ev" ] || exit 1
    cat "$fixture" >> "\$ev"
    ;;
esac
EOF
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS' "$(cat "$action")"
  out=$(run_loop "$p" --items 1 --minutes 90 --review off 2>&1); status=$?
  assert_eq "0" "$status" "a child no bound ends is not a failed run"
  assert_contains "$out" "child-turns-max=worker:0" "the run's header says the worker's bound is off"
  state=$(state_file "$p")
  report="$p/.pi/issue-loop/items/21/worker-report.md"
  [ -f "$report" ] || fail "the item kept no transcript of its child"
  assert_not_contains "$(cat "$report")" "turn bound" "no turn bound ended the child"
  assert_eq "3" "$(jq -r '.items[0].metrics.turns' "$state")" \
    "the item's total is still counted over its children"
  assert_eq "0" "$(jq -r '.items[0].metrics.calls' "$state")" "beside the calls it was made of"
  assert_contains "$out" "0 calls per turn (0 calls / 3 turns)" "and the report names the total it read"
  return 0
}
# A turn is not a unit of cost (issue #760): every turn re-reads the whole conversation, so the same turn count
# costs 40 k or 240 k depending on how far the context has grown, and the turn bound above therefore fires at a
# price it cannot see. Over this project's 300 recorded items (`.pi/issue-loop/run.json` and `runs/*.json`,
# 2026-11-05, `jq -r '.items[]?.metrics.context'` over both and one `sort -n | awk`): the largest context per item
# is p50 107 669, p75 143 124, p90 174 984, p95 191 815, maximum 244 764 — a child at 200 000 pays 1.86x the
# archive's median turn for every turn it still takes. The bound is the recorded `LOOP_ITEM_CONTEXT_MAX`, read from
# the usage records the child's own stream carries, in the same whole second as the wall clock. This case does not
# wait for it: the child appends a fixture stream whose context grows past the recorded value and then blocks, so a
# tree that reads neither the key nor the stream leaves the run waiting until the case's own bound ends it.
case_run_childPastItsContextBoundIsCut() {
  # files: tools/loop/fixtures/pi tools/loop/loop.conf tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fifo fixture action pid i status out report
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  conf="$d/loop.conf"
  # The wall clock keeps its recorded default, so the only bound this case can reach is the context one. Both values
  # are written into the copied profile rather than shortened from it, exactly as the turn bound's own case records
  # its `3`: the recorded policy is `loop.conf`'s, and the case asserts the engine enforces what the profile says.
  sed -e '/^LOOP_ITEM_CONTEXT_MAX=/d' -e '/^LOOP_ITEM_CONTEXT_WARN=/d' "$LOOP_CONF" > "$conf" ||
    fail "could not copy loop.conf"
  printf 'LOOP_ITEM_CONTEXT_MAX=200000\nLOOP_ITEM_CONTEXT_WARN=143124\n' >> "$conf"
  grep -qx 'LOOP_ITEM_CONTEXT_MAX=200000' "$conf" || fail "this case could not record the context bound in loop.conf"
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  # The child's own stream, in the shape the adapter keeps: one usage record per streamed update and one `turn_end`
  # per turn, with one turn carrying the usage on its own `turn_end` so both shapes the bound reads are exercised.
  # The context grows 120 000 → 155 000 → 210 000, so the checkpoint and the bound are both crossed while the child
  # still runs.
  fixture="$d/events.jsonl"
  cat > "$fixture" <<'JSON'
{"type":"turn_end","message":{"role":"assistant"},"usage":{"input":120000,"output":10,"cacheRead":0,"cacheWrite":0}}
{"type":"message_update","usage":{"input":135000,"output":10,"cacheRead":20000,"cacheWrite":0}}
{"type":"turn_end","message":{"role":"assistant"}}
{"type":"message_update","usage":{"input":170000,"output":10,"cacheRead":40000,"cacheWrite":0}}
{"type":"turn_end","message":{"role":"assistant"}}
JSON
  action="$d/context-child.sh"
  cat > "$action" <<EOF
#!/usr/bin/env bash
ev="\${LOOP_CHILD_EVENTS:-}"
[ -n "\$ev" ] || exit 1
cat "$fixture" >> "\$ev"
EOF
  export PI_STUB_ACTION="$action"
  fifo="$d/wait.fifo"; mkfifo "$fifo"
  # The child blocks on the fifo and is never released: without the context bound the run is still waiting on it
  # long after the case's own bound, which is what makes this case red on the tree before the fix.
  set -m
  ( export LOOP_CONF="$conf" PI_STUB_WAIT="$fifo"
    run_loop "$p" --items 1 --minutes 90 --review off > "$d/out" 2>&1 ) &
  pid=$!
  set +m
  wait_for_stub_child "$fifo.ready" || true
  [ -e "$fifo.ready" ] || fail "the stub child never started"
  # The case holds the fifo's writer side for its whole life, so a child that is released never blocks and never
  # finds a writer behind it. 60 s is the case's own margin on a host that need not be free (issue #308).
  exec 8<> "$fifo"
  for i in $(seq 1 600); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  if kill -0 "$pid" 2>/dev/null; then
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    printf 'go\n' >&8 2>/dev/null || true
    wait "$pid" 2>/dev/null
    exec 8>&-
    fail "the run was still waiting after 60 s on a child whose context had grown to 210 000 tokens past a recorded bound of 200 000"
  fi
  wait "$pid"; status=$?
  exec 8>&-
  out=$(cat "$d/out")
  assert_eq "0" "$status" "a child the run itself ended is not a failed run"
  assert_contains "$out" "item-context-max=200000" "the run's header names the context bound it enforces"
  assert_contains "$out" "LOOP-CONTEXT:" "and the checkpoint the run can take for free is taken once"
  assert_contains "$out" "LOOP_ITEM_CONTEXT_WARN records 143124" "naming the recorded checkpoint, not a number of its own"
  assert_contains "$out" "exit 124" "with the wall clock's own status, so the run's 'exited N' path records the cut"
  report="$p/.pi/issue-loop/items/21/worker-report.md"
  [ -f "$report" ] || fail "the item kept no transcript of the child the context bound ended"
  assert_contains "$(cat "$report")" "LOOP-TIMEOUT: the child reached the recorded context bound of 200000 tokens" \
    "the transcript says which bound ended the child, in the child's own words"
  assert_contains "$(cat "$report")" "at 210000 tokens" "and the reading it reached, from the child's own stream"
  assert_not_contains "$(cat "$report")" "the child exceeded" \
    "the wall clock took no part in it — the recorded one still had 45 min to run"
  assert_eq "cut" "$(jq -r '.items[0].result' "$(state_file "$p")")" \
    "an item whose child crossed the bound is recorded cut, not blocked (issue #790)"
  assert_eq "cut" "$(jq -r '.items[0].verdict' "$(state_file "$p")")" \
    "and its verdict is the cut too, which is what the run's own report counts"
  assert_eq "bound" "$(jq -r '.items[0].stopReason // ""' "$(state_file "$p")")" \
    "the context bound is a bound the run enforced, so the cut keeps it as its mechanism"
  assert_contains "$(jq -r '.items[0].note' "$(state_file "$p")")" "context bound of 200000 tokens" \
    "and the reason on the item carries the reading, not only the exit status"
  assert_eq "0" "$(grep -c 'issue close' "$GH_STUB_RECORD" || true)" "nothing is closed on a child the run ended"
  return 0
}

# The recorded `0` is the switch here too (issue #760): a project whose children should be bounded by their turns
# and their clock alone records `LOOP_ITEM_CONTEXT_MAX=0`, as the turn bound's `0` disables that one and the token
# warning's `0` records no band. The child here grows its context past the bound the other case records and then
# outlives the two-second wall clock this case records, so which bound ended it is readable in the transcript: the
# clock's, never a context bound that is off.
case_run_theRecordedZeroKeepsTheContextBoundOff() {
  # files: tools/loop/fixtures/pi tools/loop/loop.conf tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json
  local d p conf fixture action out status report
  d=$(scratch); p=$(new_project)
  export GH_STUB_DIR; GH_STUB_DIR=$(scenario one)
  conf="$d/loop.conf"
  conf_with_child_cap 2 "$conf"
  sed -e '/^LOOP_ITEM_CONTEXT_MAX=/d' -e '/^LOOP_ITEM_CONTEXT_WARN=/d' "$conf" > "$conf.zero" ||
    fail "could not record the context bound off"
  printf 'LOOP_ITEM_CONTEXT_MAX=0\nLOOP_ITEM_CONTEXT_WARN=0\n' >> "$conf.zero"
  mv "$conf.zero" "$conf"
  grep -qx 'LOOP_ITEM_CONTEXT_MAX=0' "$conf" || fail "this case could not record the context bound off in loop.conf"
  export LOOP_CONF="$conf"
  stub_write "$d" "$STUB_WORKER_REPORT" 'VERDICT: PASS'
  # The same fixture stream the bound-on case appends, written by the child's action so the run can see it while the
  # child still runs: with the bound off it must change nothing about how the child ends.
  fixture="$d/events.jsonl"
  cat > "$fixture" <<'JSON'
{"type":"message_update","usage":{"input":170000,"output":10,"cacheRead":40000,"cacheWrite":0}}
{"type":"turn_end","message":{"role":"assistant"}}
{"type":"message_update","usage":{"input":170000,"output":10,"cacheRead":40000,"cacheWrite":0}}
{"type":"turn_end","message":{"role":"assistant"}}
{"type":"message_update","usage":{"input":170000,"output":10,"cacheRead":40000,"cacheWrite":0}}
{"type":"turn_end","message":{"role":"assistant"}}
JSON
  action="$d/context-child.sh"
  cat > "$action" <<EOF
#!/usr/bin/env bash
ev="\${LOOP_CHILD_EVENTS:-}"
[ -n "\$ev" ] || exit 1
cat "$fixture" >> "\$ev"
sleep 5
EOF
  export PI_STUB_ACTION="$action"
  out=$(run_loop "$p" --items 1 --minutes 90 --review off 2>&1); status=$?
  assert_eq "0" "$status" "a child the run itself ended is not a failed run"
  assert_contains "$out" "item-context-max=0" "the run's header says the context bound is off"
  assert_not_contains "$out" "LOOP-CONTEXT:" "and the checkpoint is off with it"
  report="$p/.pi/issue-loop/items/21/worker-report.md"
  [ -f "$report" ] || fail "the item kept no transcript of the child the wall clock ended"
  assert_contains "$(cat "$report")" "LOOP-TIMEOUT: the child exceeded 2 s" \
    "with the bound off, the recorded wall clock is the one that ends the child"
  assert_not_contains "$(cat "$report")" "context bound" "and no context bound is named in its transcript"
  assert_eq "cut" "$(jq -r '.items[0].result' "$(state_file "$p")")" "the item is recorded cut, as before"
  return 0
}
