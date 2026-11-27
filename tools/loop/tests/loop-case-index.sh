#!/usr/bin/env bash
# The suite's case index: the two regions a case-adding change must read before it can add or select a case — the
# declared set (`LOOP_SHELL_CASES`) and the dispatcher's arm table (`loop_run_shell_case`) — kept out of the 25 900-line
# file that holds every case body, so that read is this file's size and not the suite's (issue #1108).
#
# Sourced once, from the top of `tools/loop/tests/loop-cases.sh`, before anything calls the set or the dispatcher; the
# two regions below are byte-identical to the lines the cases file carried before the move, and a delta to this file
# owes the whole suite, exactly as a delta to the cases file does (`loop_case_core_files`).
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
  'state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst'
  'state#projectKeysAreReadFromLoopConf'
  'state#agentCommandIsTheOnlyIndirection'
  'state#agentCommandRefusesAnEmptyValue'
  'state#aRolesOwnCommandStartsOnlyThatRolesChild'
  'state#aRolesChildInheritsTheCommandOfTheRoleItBelongsTo'
  'state#theReworkRoundIsARoleOfItsOwn'
  'state#anItemsClassSelectsTheWorkerCommand'
  'state#doneRecordsTheCommitItLanded'
  'state#theLandingScoreIsReadOnceAndStored'
  'state#aLandingWithNoRunIsUnscored'
  'state#aCloseOverARedGateRecordsWhyItWasWaived'
  'state#aCloseOverARedGateWithoutAWaiverIsRefused'
  'prescreen#ranksBugsFirstAndKeepsFeatures'
  'prescreen#oneHarnessImprovementPerFourAppItems'
  'prescreen#aRefusedClassStillStopsTheScreen'
  'prescreen#theRefusedVerdictNamesNoClassValue'
  'prescreen#missingPathIsReportedNotRefused'
  'prescreen#doesNotClaimJudgementGates'
  'prescreen#refusesReservedLabelInSelection'
  'prescreen#withoutTheOptInIsNeverQueued'
  'prescreen#settledItemIsDropped'
  'prescreen#aBlockedCandidateWaitsForItsPredecessor'
  'prescreen#theRunHoldsWhatItsBlockedItemShares'
  'prescreen#anEmptyBlockedVerdictBlocksNothing'
  'prescreen#deviceItemIsAVerdictNotWork'
  'prescreen#readsTheVerdictsFromTheProfile'
  'prescreen#aFinishedAttemptBranchIsADecision'
  'prescreen#aBlockedAttemptBranchIsWork'
  'prescreen#landedBranchIsNotADecision'
  'prescreen#itemAwaitingADecisionIsNotQueued'
  'prescreen#aHoldWithNoDecisionIsNotADecision'
  'prescreen#harnessItemsLeadTheQueue'
  'prescreen#theQueueIsWrittenInRankOrder'
  'prescreen#aPinnedItemLeadsTheQueue'
  'prescreen#aProfileWithNoPinPinsNothing'
  'prescreen#aRenamedLeadingCategoryLeads'
  'prescreen#aContainerTypeIsNotWork'
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
  'evidence#aCutAtTheCapKeepsEveryWholeLine'
  'check#aRouteSectionResolvesAgainstItsDocument'
  'worktree#checksOutTheSubmoduleTheLoopConfigNames'
  'worktree#checksOutTheNewPinWhenItReusesTheWorktree'
  'worktree#placesAPinOnlyTheRootCheckoutHolds'
  'worktree#oneBranchPerItemOffTheBase'
  'worktree#refusesWhenMainAdvanced'
  'worktree#keepsByDefaultAndRemovesOnRequest'
  'worktree#keepsSourcesNotBuildOutput'
  'worktree#noPushWithoutTheFlag'
  'worktree#pushSkipsABranchItAlreadyMerged'
  'worktree#publishesTheBaseItLandedOn'
  'worktree#aRejectedPushIsRecordedOnTheItem'
  'worktree#reusesABranchThatCarriesNoWorkOfItsOwn'
  'worktree#reusesABranchWhoseWorkAlreadyLanded'
  'worktree#refusesABranchCarryingAPreviousAttempt'
  'worktree#setsAsideABlockedAttemptAndStartsFresh'
  'worktree#doesNotAdoptAChangeCreditedToAnotherIssue'
  'worktree#refusesABranchWhoseReviewFailed'
  'worktree#refusesABranchWhoseReviewRoundNeverAnswered'
  'worktree#aRepairedRefusalDoesNotKeepALandedBranch'
  'worktree#refusesToMergeOverADirtyProjectTree'
  'worktree#theLandingDecidesByTheReadingItIsHanded'
  'worktree#refusesToReplayABranchTheBaseDoesNotDescendFrom'
  'worktree#refusesToMergeIntoATreeOffTheBaseBranch'
  'worktree#theNamedChangeIsCopiedIntoTheItem'
  'worktree#aMissingChangeRefusesTheItem'
  'worktree#aSetAsideBranchWhosePatchesLandedIsRemoved'
  'worktree#aSetAsideBranchThatCarriesWorkIsNamedNotRemoved'
  'worktree#aShellSuiteLogIsAVerificationRecord'
  'worktree#aSingleCasesLogIsNotTheChangesGate'
  'worktree#anIdeBuildDirectoryDoesNotBlockTheMerge'
  'worktree#aTrackedEditStillBlocksTheMerge'
  'worktree#theGatesTallyTravelsWithTheChangeItMeasured'
  'worktree#aBranchWhoseRunEndedBlockedIsNotAdoptable'
  'prescreen#aTierAItemPassesUnchanged'
  'prescreen#anIssueTwoOpenChangesNameIsRefused'
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
  'labels#closeRefusesACommitTheBaseDoesNotCarry'
  'run#aShapingVerdictWritesTheLabel'
  'run#aVerdictOutsideTheListWritesNoLabel'
  'run#theShapingInboxDoesNotCountAgainstTheCeiling'
  'run#aNeedsDiagnosisSkipCarriesTheLabelsTheProfileNames'
  'prompt#workerNamesEveryPhaseAndSkill'
  'prompt#workerCarriesTheFeaturePath'
  'prompt#workerNamesTheDecisionField'
  'prompt#workerGradesItsOwnDiffBeforeItReports'
  'prompt#workerTreatsARecordedDecisionAsSettled'
  'prompt#reviewerCarriesTheEightItems'
  'prompt#reviewerIsBoundedToReading'
  'prompt#reviewerInputsAreBoundedAndTheCutIsNamed'
  'prompt#iterationProtocolHandsTheReviewerTheSameSkeleton'
  'prompt#workerNamesTheChangeSurfaceBeforeThePlan'
  'prompt#iterationProtocolNamesTheChangeSurface'
  'prompt#packageIsAFileAndNotInlined'
  'prompt#inlineInjectionCannotReachTheInstructionPosition'
  'prompt#thePhasePromptsCarryTheirOwnRoute'
  'prompt#thePhasePromptNamesWhatItsShellRefuses'
  'prompt#theGatePromptNamesTheWallAndTheWayThrough'
  'prompt#aValueArrivesAsTheTextItWasHanded'
  'run#preflightRefusalStopsBeforeAnyWork'
  'run#aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen'
  'run#aDirtyProjectTreeIsRefusedBeforeThePreScreen'
  'run#blockedItemAsksItsOwnQuestion'
  'run#aBlockedItemCarriesTheWorkersDecision'
  'run#aReviewersDecisionOutranksTheWorkers'
  'run#anEditorSwapFileIsNotAChildsWrite'
  'run#falsifiedSkipClosesWithItsMeasurement'
  'run#falsificationWithoutAMeasurementIsEscalated'
  'run#mayCloseNoKeepsAFalsificationOpen'
  'run#falsificationWithAChangedTreeIsBlocked'
  'run#decisionCapRefusesToStart'
  'run#decisionCapCountsOnlyCandidates'
  'run#theDecisionCapRecordsItsStopReason'
  'run#aFailedPreScreenRecordsItsStopReason'
  'run#anUnreadableItemIsNotTheEndOfTheRun'
  'run#refusesARepositoryThatIsNotAWorkTree'
  'gate#theDeltaSelectsTheSuitesItTouches'
  'gate#theMapCoversEveryPipelineStep'
  'gate#aPipelineCheckIsOwedByItsPaths'
  'gate#aPathACheckReadsOwesThatStepsCommandSet'
  'gate#anEnginePathOwesTheRecordCheck'
  'gate#theSuitesOutputReachesTheConsoleWhileItRuns'
  'gate#aStepEndsWithItsCommandNotWithItsWatchers'
  'gate#aStepCannotReadTheCallersStdin'
  'gate#aStepIsBoundedAndTheOverrunNamesIt'
  'gate#aFailingSuiteIsReadFromTheSameStream'
  'gate#aFailingSuiteWithNoSummaryInventsNoTest'
  'gate#aCompiledStepsTallyIsNotHidden'
  'gate#aTestFailureIsAFailureNotAnError'
  'gate#aLoneAssertionErrorIsNotACase'
  'gate#aRedGateWritesTheLogItIsAttributedFrom'
  'gate#theSummaryNamesWhatEachSuiteCost'
  'gate#independentStepsRunTogether'
  'gate#aFailingSuitesTallyIsUnchangedByItsNeighbours'
  'gate#aBaseRedStepIsSubtractedAndNamed'
  'gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt'
  'gate#aStepIsNotHandedTheItemsSession'
  'gate#aPooledWavePrintsEachStepWholeWhenItEnds'
  'gate#aCarrierAnotherRunWroteIsNotReadAsThisOnes'
  'gate#aSecondGateRefusesWhileOneRuns'
  'driver#aGroupFilterRunsOnlyItsOwnCases'
  'driver#aFilterThatNamesNoCaseRefuses'
  'driver#theSuiteDoesNotInheritTheRunsRoots'
  'driver#theListIsFilteredTheSameWayASuiteRunIs'
  'driver#theSummaryNamesWhatEachGroupCost'
  'driver#theTimesFigureIsAskedForNotAssumed'
  'gate#anArchivedArtefactOwesNoBuild'
  'gate#aModuleResourceOwesTheBuild'
  'gate#theGateNamesTheProfileItRead'
  'gate#theChildReadsTheProfileOfTheTreeItWorksIn'
  'gate#aThirdFullRunInOneSessionIsRefused'
  'gate#theGateAndTheRoundNameOneTree'
  'gate#aMovedChangeNamesTheSameTree'
  'gate#aRedGateRestoresTheChangeToItsUnarchivedName'
  'gate#theGateCompletesTheChangeBeforeTheGate'
  'gate#runsUnderTheWorktreeEnvironment'
  'mutation#everyDeclaredRecordApplies'
  'mutation#aStaleRecordIsNamed'
  'mutation#theFileSetLegScopesByTheCheapestFile'
  'measure#namesTheOwedChecksInOneBlock'
  'measure#printsTheNumbersTheReviewerAsksFor'
  'measure#refusesToGuessWhichChangeItMeasures'
  'measure#saysHowManyTalliesItWithheld'
  'measure#printsTheSuiteCountAndTheGateTiming'
  'prompt#workerIsHandedTheMeasureBlock'
  'prompt#theChangeChildCitesTheMeasureBlockAndRunsNoSuite'
  'prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle'
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
  'run#aBoundEndedItemRecordsItsBound'
  'run#aBoundCutItemAtTheWorkerTurnBoundIsHeldAsTooWide'
  'run#aRoundTheRunEndedRecordsItsBound'
  'run#aBoundCutRoundIsRestartedWithItsFindings'
  'run#aReviewRoundBoundIsTheItemsStopClass'
  'run#detachedProcessInTheWorktreeIsEndedByTheCap'
  'run#treeOfAChildTheCapEndedIsNotCommitted'
  'run#treeOfACapEndedChildIsKeptAsAPatch'
  'run#aBoundEndedItemsKeptWorkIsResumedByTheNextStart'
  'run#aKeptPatchThatDoesNotApplyIsRecordedAndTheItemStartsFromTheBase'
  'run#noProcessOfASignalledRunOutlivesIt'
  'run#aHupEndedRunRecordsItsStopAndUnpicksItsItem'
  'run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock'
  'run#theStopWaitIsDecidedByTheStateTheRunWrites'
  'run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted'
  'run#childThatNeverStartedIsTheRunsFault'
  'run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault'
  'run#anEmptyAnswerOverACommitIsTheChangePhasesFailure'
  'run#aPhaseThatWroteNothingIsAskedOnceMore'
  'run#aChildTheRunEndedIsNotAskedAgain'
  'run#aChildThatNeverStartedIsRetriedBeforeTheRunStops'
  'run#theRetryCapIsSpentAndTheRunStops'
  'run#aPhaseThatAnswersNothingIsThatPhasesFailure'
  'run#anEmptyAnswerOverWorkIsRetriedBeforeTheStop'
  'run#hostBelowTheFloorBlocksTheItemAndStops'
  'run#hostAboveTheFloorRunsTheItem'
  'run#hostWithoutTheReadingPasses'
  'run#aHostWithoutThePlacementReadingIsRefused'
  'run#childWriteOutsideTheWorktreeIsNoted'
  'run#aWriterTheRunCanPlaceBlocksTheItem'
  'run#aChildIsGivenOneScratchDirectory'
  'run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem'
  'run#theWorkersOwnShellWriteIsRecordedAndNotPlaced'
  'run#anEmptyTreeWithAnOutsideWriteNamesThePaths'
  'run#aStopThatNamesAChildReadsThatChildsOwnWrite'
  'run#aStopWhoseChildWroteNothingGetsNoClause'
  'run#mergesOverADifferenceItCannotPlace'
  'run#doesNotCloseOnAMergeTheBaseCannotTake'
  'run#strictOutsideWriteBlocksTheItem'
  'run#strictOutsideWriteDiscardsTheReviewRound'
  'run#leftoverBranchIsRecordedNotDropped'
  'run#branchThatAppearsWhileTheRunIsOpenIsSetAside'
  'run#mergesAFinishedAttemptAndClosesTheItem'
  'run#doesNotMergeAnAttemptWithoutAGreenVerification'
  'run#anAdoptionLandsOverADifferenceItCannotPlace'
  'run#mergesItsCommittedBranchBeforeClosing'
  'run#aMergedBranchIsRemoved'
  'run#theEndSweepsTheLandedBranchesItMade'
  'run#theRemoteSweepKeepsAnOpenPullRequest'
  'run#theSweepComparesAgainstTheFetchedBase'
  'run#theBaseAdvancesWithEveryMerge'
  'run#staleTestResultsAreNotThisChildsGate'
  'run#recordsAFailedReviewOnTheBranch'
  'run#aRefusedRoundLeavesAnAnswerBesideItsAnnouncement'
  'run#announcesAndRecordsTheReviewRound'
  'run#thePreGateReviewRunsBeforeTheGate'
  'run#aPreGateRoundItsCapHalvedIsSkipped'
  'run#aPreGateRoundUnderHalfItsCapStillRuns'
  'run#aPreGateReviewCutTwiceIsUnansweredAndTheGateDecides'
  'run#aPreGateReviewEndedAtItsBoundNamesIt'
  'run#aRoundCutTwiceKeepsBothReadingsFindings'
  'run#aRestartQuotesTheRetriedReadingItReplaced'
  'run#aRetriedRestartQuotesTheReadingItRetried'
  'run#dirtyKeptWorktreeIsARecordedRefusal'
  'run#resetWorktreeDiscardsWhatItNames'
  'run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard'
  'run#resetWorktreeNamesTheDirtItCannotDiscard'
  'run#aRecordedItemsLeftoversAreKeptAndTheStartGoesOn'
  'run#placesTheModuleAndGoesOnWhenTheOnlyDirtIsThePin'
  'run#aPinNoCloneHoldsIsRefusedBeforeThePrescreen'
  'run#theStartAfterTheRunsOwnEndedChildIsNotRefused'
  'run#aRetriedStartStopsWithItsOwnReason'
  'run#aSkipClearsTheInProgressMark'
  'run#staleSkipAsksItsQuestion'
  'run#staleSkipKeepsTheWorkersQuestion'
  'run#aSkipPrintsTheReasonTheChildGave'
  'run#stateNamesTheItemInProgress'
  'run#skipEscalationCarriesTheDecision'
  'run#workPackageCarriesTheRecordedDecision'
  'run#claimedGateWithoutEvidenceIsBlocked'
  'run#theGateColumnNamesThisItemsGateResult'
  'run#aSkipIsNoGateToRerun'
  'run#citedEvidenceMustTravelWithTheChange'
  'run#needsDecisionIsEscalatedWithTheWork'
  'run#noGateStillRuns'
  'run#dryRunTouchesNothing'
  'run#reviewRoundsCapAtTwo'
  'run#reviewBudgetIsARecordedValue'
  'run#theWorkerPromptFitsItsBudget'
  'run#aZeroRoundBudgetIsRefused'
  'run#aCorrectiveRefusalEarnsOneMoreRound'
  'run#aCorrectiveRoundIsHandedTheCorrections'
  'run#aZeroBlockingRefusalClosesWithItsNotes'
  'run#aBlockingRefusalStillTakesTheRoundTwoPath'
  'run#aReviewThatNamedNothingTakesNoSecondRound'
  'run#aCutReportIsRetriedInsteadOfRefused'
  'run#aReportCutTwiceBlocksAsTheTransportsFault'
  'run#aFencedClosingBlockIsACompleteAnswer'
  'run#escalationCarriesTheDecision'
  'run#mayCloseNoLeavesTheIssueOpen'
  'run#metricsRecordedPerItem'
  'run#childUsageIsRecordedPerItem'
  'run#aRedGateIsBlockedByTheTail'
  'run#aBlockedItemRecordsTheRoundThatReadIt'
  'run#aCloseOverARedGateIsWaivedOrBlocked'
  'run#aRedGateIsAttributedToTheTreeThatFailsIt'
  'run#aFlakedCaseIsOffTheBlockedPath'
  'run#plainTextChildStillReports'
  'run#emptyQueueIsNotAFailure'
  'run#stopReasonIsPrintedAndReported'
  'run#staleBaseStopsTheRunWithItsReason'
  'run#awaitingDecisionIsNotEscalatedAgain'
  'run#commitsWithoutAnAmbientIdentity'
  'run#reviewerWriteInvalidatesTheRound'
  'run#reviewReadOnlyOffOnlyForDiagnosis'
  'run#reviewDiffExcludesTheEvidenceArchive'
  'run#aReviewDiffNamesTheTreeItWasWrittenFrom'
  'run#engineNamesNoProjectFact'
  'adapter#modeFlagIsItsOwnArgument'
  'adapter#theChildIsStartedWithoutContextFiles'
  'adapter#theChildIsStartedWithTheResultBound'
  'adapter#theResultBoundExemptsGateEvidence'
  'adapter#theChildIsStartedWithTheItemsLedger'
  'adapter#theChildIsStartedWithTheRepeatReadArm'
  'adapter#theRecordedRepeatArmIsRefuse'
  'adapter#aPathIsRefusedAfterItsRepeatBound'
  'adapter#theChildIsStartedWithItsToolDirectoryOnPath'
  'adapter#theKeptStreamIsBoundedAndItsRotationIsRecorded'
  'adapter#aRotatedStreamKeepsEveryTurnEnd'
  'adapter#anUpdateOnlyStreamKeepsItsUpdates'
  'adapter#aStreamStillOverItsBoundAfterTheDeltaPassIsCutAndRecorded'
  'run#aRotatedStreamKeepsEveryTurn'
  'run#theRotationNamesTheTurnsItSummarised'
  'state#theSpendSurvivesTheLossOfItsDeltaLines'
  'trim#aResultAboveTheBoundIsTrimmedAndNamed'
  'trim#aResultUnderTheBoundIsHandedOverWhole'
  'trim#aResultBetweenTheNewBoundAndTheOldOneArrivesCut'
  'trim#theTrimIsRecorded'
  'trim#aResultOlderThanTheAgeIsStubbedAndNamed'
  'look#oneCallAnswersTheWholeTree'
  'look#aResultOverTheBoundIsCutAndNamed'
  'look#aPathScopesTheWalkToIt'
  'look#aSectionIsPrintedAndTheOtherSectionsAreNot'
  'look#aCaseIsPrintedByItsName'
  'policy#aShellTreeSearchIsRefusedAndARecipeIsNot'
  'policy#aRangeReadOfATrackedFileIsRefusedAndALogIsNot'
  'policy#aTailRangeReadOfATrackedFileIsRefusedAndALogTailIsNot'
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
  'report#theActionListLeadsWithItsCounts'
  'report#theFullActionListIsPrintedOnRequest'
  'report#landedBranchIsNoAction'
  'report#anUnmergeableItemNamesItsLanding'
  'report#landedBranchesAreNamedAsRemovable'
  'report#emptyRunSaysNone'
  'report#anItemOverTheTokenBoundIsFlagged'
  'report#theBoundRateIsNamed'
  'state#aCutRecordsTheFilesItsPatchCarries'
  'report#theArchivesClassesAreDerivedNotInvented'
  'report#anItemBelowTheCallsPerTurnFloorIsNamed'
  'report#anItemRecordsItsRepeatReads'
  'report#theRuleRecordsTheSecondReadsItCounted'
  'report#aTruncatedStreamSaysSo'
  'report#theSpendIsSplitByPhaseRoleAndGate'
  'report#thePreGateRoundIsChargedToItsOwnRole'
  'report#aCallThatNamesTheGateIsNotAGateRun'
  'report#aCheckTheGateWouldRunIsTheGateTime'
  'report#aStreamWithoutDurationsPrintsDash'
  'report#aRolesTokensRecordTheModelThatAnswered'
  'report#aSessionThatSwitchedModelRecordsBothTallies'
  'report#aStateWithoutAModelKeepsItsRoleTotals'
  'report#aRoleWhoseTurnNamedNoModelKeepsItsRow'
  'report#aTurnNoSessionRecordReachesIsItsOwnBucket'
  'report#thePhaseLineNamesTheClockItIsAShareOf'
  'report#theTrendBlockNamesTheGateShareAndTheOutsideWindow'
  'report#theRefusedLandingIsReadFromTheRecord'
  'report#theRecordAndTheStateAgree'
  'report#aDifferenceIsNamedBesideTheFigures'
  'report#aRunWhoseRecordIsAbsentSaysSo'
  'report#trendFiguresFromState'
  'report#aCutRoundIsCountedBesideTheRate'
  'report#noRoundOrWallPrintsDashNotZero'
  'report#landingsAreTheStoredNumber'
  'report#aCutItemIsNamedOnceAndItsKeptWorkWithIt'
  'report#anActionNamesAnItemPathThatExists'
  'report#theRunGroupsItsItemsByEpic'
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
  'clean#aTreeWhoseNameIsAPatternIsStillRead'
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
  'driver#aHunkInACaseBodySelectsThatCaseOnly'
  'driver#aHunkOutsideEveryCaseBodyOwesEveryCase'
  'driver#everyCaseDeclaresTheFilesItCanReach'
  'driver#aCaseWithNoSelectionRowIsRefused'
  'driver#anArmTableInASecondFileIsCompared'
  'driver#everyEngineScriptIsInTheTable'
  'driver#noEngineFileExceedsItsBound'
  'driver#aFileWithoutABoundIsRefused'
  'driver#aNameAssignedTwiceIsRefused'
  'driver#anUndeclaredReadIsRefused'
  'driver#aLocalIsNotAShareOfAnotherFilesName'
  'driver#aQuotedProgramIsNotAShellRead'
  'driver#aCallInPipelinePositionIsARead'
  'driver#theSharedNamesAreDeclared'
  'driver#theReaderOrderIsNotPartOfTheComparison'
  'driver#theSourceOrderIsDeclared'
  'driver#aCallToALaterFilesFunctionFails'
  'driver#everyEngineFileHasOneOwner'
  'driver#anOwnedFunctionIsInItsOwnersFile'
  'driver#aKindIsEmittedOnlyByItsOwner'
  'driver#everyPrintOnlySiteIsConverted'
  'driver#aDeclarationNamingAnUnknownFileFails'
  'driver#theSelectionFollowsTheOwner'
  'driver#theMovedEngineBehavesAsItDid'
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
  'gate#theBaseIsReadFromTheRunNotFromTheRemote'
  'gate#theDeclarationCaseIsOwedFirst'
  'gate#aGreenLogNamesNoFailingCase'
  'gate#aFailingCaseCarriesItsFactsAndItsRerun'
  'gate#twoFailuresNameTheFirstAndCountTheRest'
  'gate#aStepThatNamesNoCaseIsAttributedByItsStep'
  'gate#theBlockStaysInsideTheRecordedBound'
  'gate#theStepBoundIsClampedByTheCeilingTheCallerNamed'
  'phase#oneCallPrintsThePhaseState'
  'phase#aResultOverTheBoundIsCutAndNamed'
  'phase#theIdentitySurvivesTheBoundOnAnyRoot'
  'repeat#aRepeatReadIsNamedNotHandedOverAgain'
  'repeat#aVerificationReadIsBoundedNotHandedOverWhole'
  'repeat#aRepeatReadIsCountedAndTheRefusalArmWithholdsIt'
  'repeat#theRecordedZeroKeepsTheRepeatRuleOff'
  'repeat#aBrokenLedgerLeavesTheResultAsReturned'
  'repeat#aRepeatIsRecordedAsWhatItWithheld'
  'repeat#anItemsLedgerSpansItsChildren'
  'repeat#theHookWritesTheItemsLedger'
  'repeat#aPathUnderTheWorktreeIsOneKeyInTwoSpellings'
  'repeat#aShellRangeReadFeedsTheTally'
  'run#childPastItsTurnBoundIsCut'
  'run#childPastItsCheckBoundIsCut'
  'run#aChildCheckSkipsTheGatesOwnSuite'
  'run#aFocusedCaseIsStillAChildCheck'
  'prescreen#aReworkCutBranchIsWork'
  'prescreen#anAnsweredBranchIsWorkNotADecision'
  'prescreen#anAnswerToAnotherAttemptDoesNotReworkTheBranch'
  'prescreen#theScreenRecordsTheItemsEpic'
  'prescreen#aContainerWhoseChildrenAllAwaitADecisionTakesNoSlot'
  'prescreen#aContainerCutAtABoundTakesNoFurtherSlot'
  'worktree#setsAsideAReworkCutBranchAndStartsFresh'
  'run#aRotatedStreamStillCountsTheTurnsItDropped'
  'run#theRecordedZeroKeepsTheTurnBoundOff'
  'run#eachChildIsBoundedByItsOwnRolesTurns'
  'run#eachRoleIsBoundedByItsOwnContext'
  'run#aRoleWithNoRecordedTurnBoundRunsUnbounded'
  'run#childPastItsContextBoundIsCut'
  'run#aContextCutWorkerIsContinuedByItsOwnRun'
  'run#aContinuationEndedOnTheSameCeilingStopsTooWide'
  'run#aWorkerAtItsCheckpointIsContinuedBeforeTheBound'
  'run#theCheckpointIsTheSplitsOwnReading'
  'run#aContinuationRunsToTheStop'
  'run#theContinuationIsToldWhatItsFirstWorkerRead'
  'run#aSecondChildIsNotEndedByAnEarlierSession'
  'run#aChainOfZeroContinuationsEndsAtTheCheckpoint'
  'run#aWorkerBelowItsCheckpointIsNotSplit'
  'run#aWorkerAtTheCheckpointWithNoWorkRunsOn'
  'run#aCutWhosePatchDoesNotApplyIsLeftCut'
  'run#aCutRunsItsGateOnTheKeptWork'
  'run#theRecordedZeroKeepsTheContextBoundOff'
  'state#aQuotaWindowIsAHoldNotAStop'
  'state#aQuotaCheckThatCannotBeMadeIsNotARefusal'
  'state#noQuotaCommandAsksNothing'
  'state#aHoldMovesTheDeadlineWithIt'
  'run#aQuotaHoldPausesTheRunAndResumesIt'
  'run#theQuotaSwitchIsExplicit'
  'report#theSpendIsWorkNotWaiting'
  'census#theCensusIsANumber'
  'census#aClosedPatronWithNoCaseIsCounted'
  'census#anUnreadCountIsNamed'
  'census#everyGuardNamesItsPatron'
  'census#aGuardWithoutAPatronIsCounted'
  'census#theCitationFormIsRead'
  'census#theCensusRefusesNothing'
  'census#aRunDoesNotConsultTheCensus'
  'run#anUndeclaredKindStopsTheRun'
  'run#aDeclaredKindMissingAFieldStopsTheRun'
  'run#aFullRecordRotatesAndSaysSo'
  'run#theRotationKeepsTheOlderFile'
  'run#theBoundIsARecordedValue'
  'run#anEventNamesAnIssueAndAPhaseNeverAPosition'
  'driver#noEventCarriesACoordinate'
  'run#aFactPrintedOnceIsRecordedOnce'
  'run#aConvertedSiteRendersFromItsRecord'
  'run#aFreeTextReasonSurvivesTheRoundTrip'
  'driver#theCaseIndexIsItsOwnFile'
)

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
    'state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst') fn=case_state_theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst ;;
    'state#projectKeysAreReadFromLoopConf') fn=case_state_projectKeysAreReadFromLoopConf ;;
    'state#agentCommandIsTheOnlyIndirection') fn=case_state_agentCommandIsTheOnlyIndirection ;;
    'state#agentCommandRefusesAnEmptyValue') fn=case_state_agentCommandRefusesAnEmptyValue ;;
    'state#aRolesOwnCommandStartsOnlyThatRolesChild') fn=case_state_aRolesOwnCommandStartsOnlyThatRolesChild ;;
    'state#aRolesChildInheritsTheCommandOfTheRoleItBelongsTo') fn=case_state_aRolesChildInheritsTheCommandOfTheRoleItBelongsTo ;;
    'state#theReworkRoundIsARoleOfItsOwn') fn=case_state_theReworkRoundIsARoleOfItsOwn ;;
    'state#anItemsClassSelectsTheWorkerCommand') fn=case_state_anItemsClassSelectsTheWorkerCommand ;;
    'state#doneRecordsTheCommitItLanded') fn=case_state_doneRecordsTheCommitItLanded ;;
    'state#theLandingScoreIsReadOnceAndStored') fn=case_state_theLandingScoreIsReadOnceAndStored ;;
    'state#aLandingWithNoRunIsUnscored') fn=case_state_aLandingWithNoRunIsUnscored ;;
    'state#aCloseOverARedGateRecordsWhyItWasWaived') fn=case_state_aCloseOverARedGateRecordsWhyItWasWaived ;;
    'state#aCloseOverARedGateWithoutAWaiverIsRefused') fn=case_state_aCloseOverARedGateWithoutAWaiverIsRefused ;;
    'state#inheritedStatePathOutsideTheTreeIsIgnored') fn=case_state_inheritedStatePathOutsideTheTreeIsIgnored ;;
    'prescreen#ranksBugsFirstAndKeepsFeatures') fn=case_prescreen_ranksBugsFirstAndKeepsFeatures ;;
    'prescreen#oneHarnessImprovementPerFourAppItems') fn=case_prescreen_oneHarnessImprovementPerFourAppItems ;;
    'prescreen#aRefusedClassStillStopsTheScreen') fn=case_prescreen_aRefusedClassStillStopsTheScreen ;;
    'prescreen#theRefusedVerdictNamesNoClassValue') fn=case_prescreen_theRefusedVerdictNamesNoClassValue ;;
    'prescreen#missingPathIsReportedNotRefused') fn=case_prescreen_missingPathIsReportedNotRefused ;;
    'prescreen#doesNotClaimJudgementGates') fn=case_prescreen_doesNotClaimJudgementGates ;;
    'prescreen#refusesReservedLabelInSelection') fn=case_prescreen_refusesReservedLabelInSelection ;;
    'prescreen#withoutTheOptInIsNeverQueued') fn=case_prescreen_withoutTheOptInIsNeverQueued ;;
    'prescreen#settledItemIsDropped') fn=case_prescreen_settledItemIsDropped ;;
    'prescreen#aBlockedCandidateWaitsForItsPredecessor') fn=case_prescreen_aBlockedCandidateWaitsForItsPredecessor ;;
    'prescreen#theRunHoldsWhatItsBlockedItemShares') fn=case_prescreen_theRunHoldsWhatItsBlockedItemShares ;;
    'prescreen#anEmptyBlockedVerdictBlocksNothing') fn=case_prescreen_anEmptyBlockedVerdictBlocksNothing ;;
    'prescreen#deviceItemIsAVerdictNotWork') fn=case_prescreen_deviceItemIsAVerdictNotWork ;;
    'prescreen#readsTheVerdictsFromTheProfile') fn=case_prescreen_readsTheVerdictsFromTheProfile ;;
    'prescreen#aFinishedAttemptBranchIsADecision') fn=case_prescreen_aFinishedAttemptBranchIsADecision ;;
    'prescreen#aBlockedAttemptBranchIsWork') fn=case_prescreen_aBlockedAttemptBranchIsWork ;;
    'prescreen#aReworkCutBranchIsWork') fn=case_prescreen_aReworkCutBranchIsWork ;;
    'prescreen#anAnsweredBranchIsWorkNotADecision') fn=case_prescreen_anAnsweredBranchIsWorkNotADecision ;;
    'prescreen#anAnswerToAnotherAttemptDoesNotReworkTheBranch') fn=case_prescreen_anAnswerToAnotherAttemptDoesNotReworkTheBranch ;;
    'prescreen#landedBranchIsNotADecision') fn=case_prescreen_landedBranchIsNotADecision ;;
    'prescreen#itemAwaitingADecisionIsNotQueued') fn=case_prescreen_itemAwaitingADecisionIsNotQueued ;;
    'prescreen#aHoldWithNoDecisionIsNotADecision') fn=case_prescreen_aHoldWithNoDecisionIsNotADecision ;;
    'prescreen#harnessItemsLeadTheQueue') fn=case_prescreen_harnessItemsLeadTheQueue ;;
    'prescreen#theQueueIsWrittenInRankOrder') fn=case_prescreen_theQueueIsWrittenInRankOrder ;;
    'prescreen#aPinnedItemLeadsTheQueue') fn=case_prescreen_aPinnedItemLeadsTheQueue ;;
    'prescreen#aProfileWithNoPinPinsNothing') fn=case_prescreen_aProfileWithNoPinPinsNothing ;;
    'prescreen#aRenamedLeadingCategoryLeads') fn=case_prescreen_aRenamedLeadingCategoryLeads ;;
    'prescreen#aContainerTypeIsNotWork') fn=case_prescreen_aContainerTypeIsNotWork ;;
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
    'evidence#aCutAtTheCapKeepsEveryWholeLine') fn=case_evidence_aCutAtTheCapKeepsEveryWholeLine ;;
    'check#aRouteSectionResolvesAgainstItsDocument') fn=case_check_aRouteSectionResolvesAgainstItsDocument ;;
    'worktree#checksOutTheSubmoduleTheLoopConfigNames') fn=case_worktree_checksOutTheSubmoduleTheLoopConfigNames ;;
    'worktree#checksOutTheNewPinWhenItReusesTheWorktree') fn=case_worktree_checksOutTheNewPinWhenItReusesTheWorktree ;;
    'worktree#placesAPinOnlyTheRootCheckoutHolds') fn=case_worktree_placesAPinOnlyTheRootCheckoutHolds ;;
    'worktree#oneBranchPerItemOffTheBase') fn=case_worktree_oneBranchPerItemOffTheBase ;;
    'worktree#refusesWhenMainAdvanced') fn=case_worktree_refusesWhenMainAdvanced ;;
    'worktree#keepsByDefaultAndRemovesOnRequest') fn=case_worktree_keepsByDefaultAndRemovesOnRequest ;;
    'worktree#keepsSourcesNotBuildOutput') fn=case_worktree_keepsSourcesNotBuildOutput ;;
    'worktree#noPushWithoutTheFlag') fn=case_worktree_noPushWithoutTheFlag ;;
    'worktree#pushSkipsABranchItAlreadyMerged') fn=case_worktree_pushSkipsABranchItAlreadyMerged ;;
    'worktree#publishesTheBaseItLandedOn') fn=case_worktree_publishesTheBaseItLandedOn ;;
    'worktree#aRejectedPushIsRecordedOnTheItem') fn=case_worktree_aRejectedPushIsRecordedOnTheItem ;;
    'worktree#reusesABranchThatCarriesNoWorkOfItsOwn') fn=case_worktree_reusesABranchThatCarriesNoWorkOfItsOwn ;;
    'worktree#reusesABranchWhoseWorkAlreadyLanded') fn=case_worktree_reusesABranchWhoseWorkAlreadyLanded ;;
    'worktree#refusesABranchCarryingAPreviousAttempt') fn=case_worktree_refusesABranchCarryingAPreviousAttempt ;;
    'worktree#setsAsideABlockedAttemptAndStartsFresh') fn=case_worktree_setsAsideABlockedAttemptAndStartsFresh ;;
    'worktree#setsAsideAReworkCutBranchAndStartsFresh') fn=case_worktree_setsAsideAReworkCutBranchAndStartsFresh ;;
    'worktree#doesNotAdoptAChangeCreditedToAnotherIssue') fn=case_worktree_doesNotAdoptAChangeCreditedToAnotherIssue ;;
    'worktree#refusesABranchWhoseReviewFailed') fn=case_worktree_refusesABranchWhoseReviewFailed ;;
    'worktree#refusesABranchWhoseReviewRoundNeverAnswered') fn=case_worktree_refusesABranchWhoseReviewRoundNeverAnswered ;;
    'worktree#aRepairedRefusalDoesNotKeepALandedBranch') fn=case_worktree_aRepairedRefusalDoesNotKeepALandedBranch ;;
    'worktree#refusesToMergeOverADirtyProjectTree') fn=case_worktree_refusesToMergeOverADirtyProjectTree ;;
    'worktree#theLandingDecidesByTheReadingItIsHanded') fn=case_worktree_theLandingDecidesByTheReadingItIsHanded ;;
    'worktree#refusesToReplayABranchTheBaseDoesNotDescendFrom') fn=case_worktree_refusesToReplayABranchTheBaseDoesNotDescendFrom ;;
    'worktree#refusesToMergeIntoATreeOffTheBaseBranch') fn=case_worktree_refusesToMergeIntoATreeOffTheBaseBranch ;;
    'worktree#theNamedChangeIsCopiedIntoTheItem') fn=case_worktree_theNamedChangeIsCopiedIntoTheItem ;;
    'worktree#aMissingChangeRefusesTheItem') fn=case_worktree_aMissingChangeRefusesTheItem ;;
    'worktree#aSetAsideBranchWhosePatchesLandedIsRemoved') fn=case_worktree_aSetAsideBranchWhosePatchesLandedIsRemoved ;;
    'worktree#aSetAsideBranchThatCarriesWorkIsNamedNotRemoved') fn=case_worktree_aSetAsideBranchThatCarriesWorkIsNamedNotRemoved ;;
    'worktree#aShellSuiteLogIsAVerificationRecord') fn=case_worktree_aShellSuiteLogIsAVerificationRecord ;;
    'worktree#aSingleCasesLogIsNotTheChangesGate') fn=case_worktree_aSingleCasesLogIsNotTheChangesGate ;;
    'worktree#anIdeBuildDirectoryDoesNotBlockTheMerge') fn=case_worktree_anIdeBuildDirectoryDoesNotBlockTheMerge ;;
    'worktree#aTrackedEditStillBlocksTheMerge') fn=case_worktree_aTrackedEditStillBlocksTheMerge ;;
    'worktree#theGatesTallyTravelsWithTheChangeItMeasured') fn=case_worktree_theGatesTallyTravelsWithTheChangeItMeasured ;;
    'worktree#aBranchWhoseRunEndedBlockedIsNotAdoptable') fn=case_worktree_aBranchWhoseRunEndedBlockedIsNotAdoptable ;;
    'prescreen#aTierAItemPassesUnchanged') fn=case_prescreen_aTierAItemPassesUnchanged ;;
    'prescreen#anIssueTwoOpenChangesNameIsRefused') fn=case_prescreen_anIssueTwoOpenChangesNameIsRefused ;;
    'prescreen#aChangeThatIsNoLongerReadyIsRefused') fn=case_prescreen_aChangeThatIsNoLongerReadyIsRefused ;;
    'prescreen#aClosedItemIsNotSelected') fn=case_prescreen_aClosedItemIsNotSelected ;;
    'prescreen#theScreenRecordsTheItemsEpic') fn=case_prescreen_theScreenRecordsTheItemsEpic ;;
    'prescreen#aContainerWhoseChildrenAllAwaitADecisionTakesNoSlot') fn=case_prescreen_aContainerWhoseChildrenAllAwaitADecisionTakesNoSlot ;;
    'prescreen#aContainerCutAtABoundTakesNoFurtherSlot') fn=case_prescreen_aContainerCutAtABoundTakesNoFurtherSlot ;;
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
    'labels#closeRefusesACommitTheBaseDoesNotCarry') fn=case_labels_closeRefusesACommitTheBaseDoesNotCarry ;;
    'run#aShapingVerdictWritesTheLabel') fn=case_run_aShapingVerdictWritesTheLabel ;;
    'run#aVerdictOutsideTheListWritesNoLabel') fn=case_run_aVerdictOutsideTheListWritesNoLabel ;;
    'run#theShapingInboxDoesNotCountAgainstTheCeiling') fn=case_run_theShapingInboxDoesNotCountAgainstTheCeiling ;;
    'run#aNeedsDiagnosisSkipCarriesTheLabelsTheProfileNames') fn=case_run_aNeedsDiagnosisSkipCarriesTheLabelsTheProfileNames ;;
    'prompt#workerNamesEveryPhaseAndSkill') fn=case_prompt_workerNamesEveryPhaseAndSkill ;;
    'prompt#workerCarriesTheFeaturePath') fn=case_prompt_workerCarriesTheFeaturePath ;;
    'prompt#workerNamesTheDecisionField') fn=case_prompt_workerNamesTheDecisionField ;;
    'prompt#workerGradesItsOwnDiffBeforeItReports') fn=case_prompt_workerGradesItsOwnDiffBeforeItReports ;;
    'prompt#workerTreatsARecordedDecisionAsSettled') fn=case_prompt_workerTreatsARecordedDecisionAsSettled ;;
    'prompt#reviewerCarriesTheEightItems') fn=case_prompt_reviewerCarriesTheEightItems ;;
    'prompt#reviewerIsBoundedToReading') fn=case_prompt_reviewerIsBoundedToReading ;;
    'prompt#reviewerInputsAreBoundedAndTheCutIsNamed') fn=case_prompt_reviewerInputsAreBoundedAndTheCutIsNamed ;;
    'prompt#iterationProtocolHandsTheReviewerTheSameSkeleton') fn=case_prompt_iterationProtocolHandsTheReviewerTheSameSkeleton ;;
    'prompt#workerNamesTheChangeSurfaceBeforeThePlan') fn=case_prompt_workerNamesTheChangeSurfaceBeforeThePlan ;;
    'prompt#iterationProtocolNamesTheChangeSurface') fn=case_prompt_iterationProtocolNamesTheChangeSurface ;;
    'prompt#packageIsAFileAndNotInlined') fn=case_prompt_packageIsAFileAndNotInlined ;;
    'prompt#inlineInjectionCannotReachTheInstructionPosition') fn=case_prompt_inlineInjectionCannotReachTheInstructionPosition ;;
    'prompt#thePhasePromptsCarryTheirOwnRoute') fn=case_prompt_thePhasePromptsCarryTheirOwnRoute ;;
    'prompt#thePhasePromptNamesWhatItsShellRefuses') fn=case_prompt_thePhasePromptNamesWhatItsShellRefuses ;;
    'prompt#theGatePromptNamesTheWallAndTheWayThrough') fn=case_prompt_theGatePromptNamesTheWallAndTheWayThrough ;;
    'prompt#aValueArrivesAsTheTextItWasHanded') fn=case_prompt_aValueArrivesAsTheTextItWasHanded ;;
    'run#preflightRefusalStopsBeforeAnyWork') fn=case_run_preflightRefusalStopsBeforeAnyWork ;;
    'run#aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen') fn=case_run_aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen ;;
    'run#aDirtyProjectTreeIsRefusedBeforeThePreScreen') fn=case_run_aDirtyProjectTreeIsRefusedBeforeThePreScreen ;;
    'run#blockedItemAsksItsOwnQuestion') fn=case_run_blockedItemAsksItsOwnQuestion ;;
    'run#aBlockedItemCarriesTheWorkersDecision') fn=case_run_aBlockedItemCarriesTheWorkersDecision ;;
    'run#aReviewersDecisionOutranksTheWorkers') fn=case_run_aReviewersDecisionOutranksTheWorkers ;;
    'run#anEditorSwapFileIsNotAChildsWrite') fn=case_run_anEditorSwapFileIsNotAChildsWrite ;;
    'run#falsifiedSkipClosesWithItsMeasurement') fn=case_run_falsifiedSkipClosesWithItsMeasurement ;;
    'run#falsificationWithoutAMeasurementIsEscalated') fn=case_run_falsificationWithoutAMeasurementIsEscalated ;;
    'run#mayCloseNoKeepsAFalsificationOpen') fn=case_run_mayCloseNoKeepsAFalsificationOpen ;;
    'run#falsificationWithAChangedTreeIsBlocked') fn=case_run_falsificationWithAChangedTreeIsBlocked ;;
    'run#decisionCapRefusesToStart') fn=case_run_decisionCapRefusesToStart ;;
    'run#decisionCapCountsOnlyCandidates') fn=case_run_decisionCapCountsOnlyCandidates ;;
    'run#theDecisionCapRecordsItsStopReason') fn=case_run_theDecisionCapRecordsItsStopReason ;;
    'run#aFailedPreScreenRecordsItsStopReason') fn=case_run_aFailedPreScreenRecordsItsStopReason ;;
    'run#anUnreadableItemIsNotTheEndOfTheRun') fn=case_run_anUnreadableItemIsNotTheEndOfTheRun ;;
    'run#refusesARepositoryThatIsNotAWorkTree') fn=case_run_refusesARepositoryThatIsNotAWorkTree ;;
    'gate#theDeltaSelectsTheSuitesItTouches') fn=case_gate_theDeltaSelectsTheSuitesItTouches ;;
    'gate#theMapCoversEveryPipelineStep') fn=case_gate_theMapCoversEveryPipelineStep ;;
    'gate#aPipelineCheckIsOwedByItsPaths') fn=case_gate_aPipelineCheckIsOwedByItsPaths ;;
    'gate#aPathACheckReadsOwesThatStepsCommandSet') fn=case_gate_aPathACheckReadsOwesThatStepsCommandSet ;;
    'gate#anEnginePathOwesTheRecordCheck') fn=case_gate_anEnginePathOwesTheRecordCheck ;;
    'gate#theSuitesOutputReachesTheConsoleWhileItRuns') fn=case_gate_theSuitesOutputReachesTheConsoleWhileItRuns ;;
    'gate#aStepEndsWithItsCommandNotWithItsWatchers') fn=case_gate_aStepEndsWithItsCommandNotWithItsWatchers ;;
    'gate#aStepCannotReadTheCallersStdin') fn=case_gate_aStepCannotReadTheCallersStdin ;;
    'gate#aStepIsBoundedAndTheOverrunNamesIt') fn=case_gate_aStepIsBoundedAndTheOverrunNamesIt ;;
    'gate#aFailingSuiteIsReadFromTheSameStream') fn=case_gate_aFailingSuiteIsReadFromTheSameStream ;;
    'gate#aFailingSuiteWithNoSummaryInventsNoTest') fn=case_gate_aFailingSuiteWithNoSummaryInventsNoTest ;;
    'gate#aCompiledStepsTallyIsNotHidden') fn=case_gate_aCompiledStepsTallyIsNotHidden ;;
    'gate#aTestFailureIsAFailureNotAnError') fn=case_gate_aTestFailureIsAFailureNotAnError ;;
    'gate#aLoneAssertionErrorIsNotACase') fn=case_gate_aLoneAssertionErrorIsNotACase ;;
    'gate#aRedGateWritesTheLogItIsAttributedFrom') fn=case_gate_aRedGateWritesTheLogItIsAttributedFrom ;;
    'gate#theSummaryNamesWhatEachSuiteCost') fn=case_gate_theSummaryNamesWhatEachSuiteCost ;;
    'gate#independentStepsRunTogether') fn=case_gate_independentStepsRunTogether ;;
    'gate#aFailingSuitesTallyIsUnchangedByItsNeighbours') fn=case_gate_aFailingSuitesTallyIsUnchangedByItsNeighbours ;;
    'gate#aBaseRedStepIsSubtractedAndNamed') fn=case_gate_aBaseRedStepIsSubtractedAndNamed ;;
    'gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt') fn=case_gate_theBaseStateIsMeasuredWhenTheGateFirstNeedsIt ;;
    'gate#aStepIsNotHandedTheItemsSession') fn=case_gate_aStepIsNotHandedTheItemsSession ;;
    'gate#aPooledWavePrintsEachStepWholeWhenItEnds') fn=case_gate_aPooledWavePrintsEachStepWholeWhenItEnds ;;
    'gate#aCarrierAnotherRunWroteIsNotReadAsThisOnes') fn=case_gate_aCarrierAnotherRunWroteIsNotReadAsThisOnes ;;
    'gate#aSecondGateRefusesWhileOneRuns') fn=case_gate_aSecondGateRefusesWhileOneRuns ;;
    'driver#aGroupFilterRunsOnlyItsOwnCases') fn=case_driver_aGroupFilterRunsOnlyItsOwnCases ;;
    'driver#aFilterThatNamesNoCaseRefuses') fn=case_driver_aFilterThatNamesNoCaseRefuses ;;
    'driver#theSuiteDoesNotInheritTheRunsRoots') fn=case_driver_theSuiteDoesNotInheritTheRunsRoots ;;
    'driver#theListIsFilteredTheSameWayASuiteRunIs') fn=case_driver_theListIsFilteredTheSameWayASuiteRunIs ;;
    'driver#theSummaryNamesWhatEachGroupCost') fn=case_driver_theSummaryNamesWhatEachGroupCost ;;
    'driver#theTimesFigureIsAskedForNotAssumed') fn=case_driver_theTimesFigureIsAskedForNotAssumed ;;
    'gate#anArchivedArtefactOwesNoBuild') fn=case_gate_anArchivedArtefactOwesNoBuild ;;
    'gate#aModuleResourceOwesTheBuild') fn=case_gate_aModuleResourceOwesTheBuild ;;
    'gate#theGateNamesTheProfileItRead') fn=case_gate_theGateNamesTheProfileItRead ;;
    'gate#theChildReadsTheProfileOfTheTreeItWorksIn') fn=case_gate_theChildReadsTheProfileOfTheTreeItWorksIn ;;
    'gate#aThirdFullRunInOneSessionIsRefused') fn=case_gate_aThirdFullRunInOneSessionIsRefused ;;
    'gate#theGateAndTheRoundNameOneTree') fn=case_gate_theGateAndTheRoundNameOneTree ;;
    'gate#aMovedChangeNamesTheSameTree') fn=case_gate_aMovedChangeNamesTheSameTree ;;
    'gate#aRedGateRestoresTheChangeToItsUnarchivedName') fn=case_gate_aRedGateRestoresTheChangeToItsUnarchivedName ;;
    'gate#theGateCompletesTheChangeBeforeTheGate') fn=case_gate_theGateCompletesTheChangeBeforeTheGate ;;
    'gate#runsUnderTheWorktreeEnvironment') fn=case_gate_runsUnderTheWorktreeEnvironment ;;
    'mutation#everyDeclaredRecordApplies') fn=case_mutation_everyDeclaredRecordApplies ;;
    'mutation#aStaleRecordIsNamed') fn=case_mutation_aStaleRecordIsNamed ;;
    'mutation#theFileSetLegScopesByTheCheapestFile') fn=case_mutation_theFileSetLegScopesByTheCheapestFile ;;
    'measure#namesTheOwedChecksInOneBlock') fn=case_measure_namesTheOwedChecksInOneBlock ;;
    'measure#printsTheNumbersTheReviewerAsksFor') fn=case_measure_printsTheNumbersTheReviewerAsksFor ;;
    'measure#refusesToGuessWhichChangeItMeasures') fn=case_measure_refusesToGuessWhichChangeItMeasures ;;
    'measure#saysHowManyTalliesItWithheld') fn=case_measure_saysHowManyTalliesItWithheld ;;
    'measure#printsTheSuiteCountAndTheGateTiming') fn=case_measure_printsTheSuiteCountAndTheGateTiming ;;
    'prompt#workerIsHandedTheMeasureBlock') fn=case_prompt_workerIsHandedTheMeasureBlock ;;
    'prompt#theChangeChildCitesTheMeasureBlockAndRunsNoSuite') fn=case_prompt_theChangeChildCitesTheMeasureBlockAndRunsNoSuite ;;
    'prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle') fn=case_prompt_thePreGateRoundRefusesOnlyWhatTheGateCannotSettle ;;
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
    'run#aBoundEndedItemRecordsItsBound') fn=case_run_aBoundEndedItemRecordsItsBound ;;
    'run#aBoundCutItemAtTheWorkerTurnBoundIsHeldAsTooWide') fn=case_run_aBoundCutItemAtTheWorkerTurnBoundIsHeldAsTooWide ;;
    'run#aRoundTheRunEndedRecordsItsBound') fn=case_run_aRoundTheRunEndedRecordsItsBound ;;
    'run#aBoundCutRoundIsRestartedWithItsFindings') fn=case_run_aBoundCutRoundIsRestartedWithItsFindings ;;
    'run#aReviewRoundBoundIsTheItemsStopClass') fn=case_run_aReviewRoundBoundIsTheItemsStopClass ;;
    'run#detachedProcessInTheWorktreeIsEndedByTheCap') fn=case_run_detachedProcessInTheWorktreeIsEndedByTheCap ;;
    'run#treeOfAChildTheCapEndedIsNotCommitted') fn=case_run_treeOfAChildTheCapEndedIsNotCommitted ;;
    'run#treeOfACapEndedChildIsKeptAsAPatch') fn=case_run_treeOfACapEndedChildIsKeptAsAPatch ;;
    'run#aBoundEndedItemsKeptWorkIsResumedByTheNextStart') fn=case_run_aBoundEndedItemsKeptWorkIsResumedByTheNextStart ;;
    'run#aKeptPatchThatDoesNotApplyIsRecordedAndTheItemStartsFromTheBase') fn=case_run_aKeptPatchThatDoesNotApplyIsRecordedAndTheItemStartsFromTheBase ;;
    'run#noProcessOfASignalledRunOutlivesIt') fn=case_run_noProcessOfASignalledRunOutlivesIt ;;
    'run#aHupEndedRunRecordsItsStopAndUnpicksItsItem') fn=case_run_aHupEndedRunRecordsItsStopAndUnpicksItsItem ;;
    'run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock') fn=case_run_aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock ;;
    'run#theStopWaitIsDecidedByTheStateTheRunWrites') fn=case_run_theStopWaitIsDecidedByTheStateTheRunWrites ;;
    'run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted') fn=case_run_treeOfACapEndedReworkOfARefusingRoundIsNotCommitted ;;
    'run#childThatNeverStartedIsTheRunsFault') fn=case_run_childThatNeverStartedIsTheRunsFault ;;
    'run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault') fn=case_run_aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault ;;
    'run#anEmptyAnswerOverACommitIsTheChangePhasesFailure') fn=case_run_anEmptyAnswerOverACommitIsTheChangePhasesFailure ;;
    'run#aPhaseThatWroteNothingIsAskedOnceMore') fn=case_run_aPhaseThatWroteNothingIsAskedOnceMore ;;
    'run#aChildTheRunEndedIsNotAskedAgain') fn=case_run_aChildTheRunEndedIsNotAskedAgain ;;
    'run#aChildThatNeverStartedIsRetriedBeforeTheRunStops') fn=case_run_aChildThatNeverStartedIsRetriedBeforeTheRunStops ;;
    'run#theRetryCapIsSpentAndTheRunStops') fn=case_run_theRetryCapIsSpentAndTheRunStops ;;
    'run#aPhaseThatAnswersNothingIsThatPhasesFailure') fn=case_run_aPhaseThatAnswersNothingIsThatPhasesFailure ;;
    'run#anEmptyAnswerOverWorkIsRetriedBeforeTheStop') fn=case_run_anEmptyAnswerOverWorkIsRetriedBeforeTheStop ;;
    'run#hostBelowTheFloorBlocksTheItemAndStops') fn=case_run_hostBelowTheFloorBlocksTheItemAndStops ;;
    'run#hostAboveTheFloorRunsTheItem') fn=case_run_hostAboveTheFloorRunsTheItem ;;
    'run#hostWithoutTheReadingPasses') fn=case_run_hostWithoutTheReadingPasses ;;
    'run#aHostWithoutThePlacementReadingIsRefused') fn=case_run_aHostWithoutThePlacementReadingIsRefused ;;
    'run#childWriteOutsideTheWorktreeIsNoted') fn=case_run_childWriteOutsideTheWorktreeIsNoted ;;
    'run#aWriterTheRunCanPlaceBlocksTheItem') fn=case_run_aWriterTheRunCanPlaceBlocksTheItem ;;
    'run#aChildIsGivenOneScratchDirectory') fn=case_run_aChildIsGivenOneScratchDirectory ;;
    'run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem') fn=case_run_aPeerWriteWhileAChildStandsDoesNotDecideTheItem ;;
    'run#theWorkersOwnShellWriteIsRecordedAndNotPlaced') fn=case_run_theWorkersOwnShellWriteIsRecordedAndNotPlaced ;;
    'run#mergesOverADifferenceItCannotPlace') fn=case_run_mergesOverADifferenceItCannotPlace ;;
    'run#anEmptyTreeWithAnOutsideWriteNamesThePaths') fn=case_run_anEmptyTreeWithAnOutsideWriteNamesThePaths ;;
    'run#aStopThatNamesAChildReadsThatChildsOwnWrite') fn=case_run_aStopThatNamesAChildReadsThatChildsOwnWrite ;;
    'run#aStopWhoseChildWroteNothingGetsNoClause') fn=case_run_aStopWhoseChildWroteNothingGetsNoClause ;;
    'run#doesNotCloseOnAMergeTheBaseCannotTake') fn=case_run_doesNotCloseOnAMergeTheBaseCannotTake ;;
    'run#strictOutsideWriteBlocksTheItem') fn=case_run_strictOutsideWriteBlocksTheItem ;;
    'run#strictOutsideWriteDiscardsTheReviewRound') fn=case_run_strictOutsideWriteDiscardsTheReviewRound ;;
    'run#leftoverBranchIsRecordedNotDropped') fn=case_run_leftoverBranchIsRecordedNotDropped ;;
    'run#branchThatAppearsWhileTheRunIsOpenIsSetAside') fn=case_run_branchThatAppearsWhileTheRunIsOpenIsSetAside ;;
    'run#mergesAFinishedAttemptAndClosesTheItem') fn=case_run_mergesAFinishedAttemptAndClosesTheItem ;;
    'run#doesNotMergeAnAttemptWithoutAGreenVerification') fn=case_run_doesNotMergeAnAttemptWithoutAGreenVerification ;;
    'run#anAdoptionLandsOverADifferenceItCannotPlace') fn=case_run_anAdoptionLandsOverADifferenceItCannotPlace ;;
    'run#mergesItsCommittedBranchBeforeClosing') fn=case_run_mergesItsCommittedBranchBeforeClosing ;;
    'run#aMergedBranchIsRemoved') fn=case_run_aMergedBranchIsRemoved ;;
    'run#theEndSweepsTheLandedBranchesItMade') fn=case_run_theEndSweepsTheLandedBranchesItMade ;;
    'run#theRemoteSweepKeepsAnOpenPullRequest') fn=case_run_theRemoteSweepKeepsAnOpenPullRequest ;;
    'run#theSweepComparesAgainstTheFetchedBase') fn=case_run_theSweepComparesAgainstTheFetchedBase ;;
    'run#theBaseAdvancesWithEveryMerge') fn=case_run_theBaseAdvancesWithEveryMerge ;;
    'run#staleTestResultsAreNotThisChildsGate') fn=case_run_staleTestResultsAreNotThisChildsGate ;;
    'run#recordsAFailedReviewOnTheBranch') fn=case_run_recordsAFailedReviewOnTheBranch ;;
    'run#aRefusedRoundLeavesAnAnswerBesideItsAnnouncement') fn=case_run_aRefusedRoundLeavesAnAnswerBesideItsAnnouncement ;;
    'run#announcesAndRecordsTheReviewRound') fn=case_run_announcesAndRecordsTheReviewRound ;;
    'run#thePreGateReviewRunsBeforeTheGate') fn=case_run_thePreGateReviewRunsBeforeTheGate ;;
    'run#aPreGateRoundItsCapHalvedIsSkipped') fn=case_run_aPreGateRoundItsCapHalvedIsSkipped ;;
    'run#aPreGateRoundUnderHalfItsCapStillRuns') fn=case_run_aPreGateRoundUnderHalfItsCapStillRuns ;;
    'run#aPreGateReviewCutTwiceIsUnansweredAndTheGateDecides') fn=case_run_aPreGateReviewCutTwiceIsUnansweredAndTheGateDecides ;;
    'run#aPreGateReviewEndedAtItsBoundNamesIt') fn=case_run_aPreGateReviewEndedAtItsBoundNamesIt ;;
    'run#aRoundCutTwiceKeepsBothReadingsFindings') fn=case_run_aRoundCutTwiceKeepsBothReadingsFindings ;;
    'run#aRestartQuotesTheRetriedReadingItReplaced') fn=case_run_aRestartQuotesTheRetriedReadingItReplaced ;;
    'run#aRetriedRestartQuotesTheReadingItRetried') fn=case_run_aRetriedRestartQuotesTheReadingItRetried ;;
    'run#dirtyKeptWorktreeIsARecordedRefusal') fn=case_run_dirtyKeptWorktreeIsARecordedRefusal ;;
    'run#resetWorktreeDiscardsWhatItNames') fn=case_run_resetWorktreeDiscardsWhatItNames ;;
    'run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard') fn=case_run_resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard ;;
    'run#resetWorktreeNamesTheDirtItCannotDiscard') fn=case_run_resetWorktreeNamesTheDirtItCannotDiscard ;;
    'run#aRecordedItemsLeftoversAreKeptAndTheStartGoesOn') fn=case_run_aRecordedItemsLeftoversAreKeptAndTheStartGoesOn ;;
    'run#placesTheModuleAndGoesOnWhenTheOnlyDirtIsThePin') fn=case_run_placesTheModuleAndGoesOnWhenTheOnlyDirtIsThePin ;;
    'run#aPinNoCloneHoldsIsRefusedBeforeThePrescreen') fn=case_run_aPinNoCloneHoldsIsRefusedBeforeThePrescreen ;;
    'run#theStartAfterTheRunsOwnEndedChildIsNotRefused') fn=case_run_theStartAfterTheRunsOwnEndedChildIsNotRefused ;;
    'run#aRetriedStartStopsWithItsOwnReason') fn=case_run_aRetriedStartStopsWithItsOwnReason ;;
    'run#aSkipClearsTheInProgressMark') fn=case_run_aSkipClearsTheInProgressMark ;;
    'run#staleSkipAsksItsQuestion') fn=case_run_staleSkipAsksItsQuestion ;;
    'run#staleSkipKeepsTheWorkersQuestion') fn=case_run_staleSkipKeepsTheWorkersQuestion ;;
    'run#aSkipPrintsTheReasonTheChildGave') fn=case_run_aSkipPrintsTheReasonTheChildGave ;;
    'run#stateNamesTheItemInProgress') fn=case_run_stateNamesTheItemInProgress ;;
    'run#skipEscalationCarriesTheDecision') fn=case_run_skipEscalationCarriesTheDecision ;;
    'run#workPackageCarriesTheRecordedDecision') fn=case_run_workPackageCarriesTheRecordedDecision ;;
    'run#claimedGateWithoutEvidenceIsBlocked') fn=case_run_claimedGateWithoutEvidenceIsBlocked ;;
    'run#theGateColumnNamesThisItemsGateResult') fn=case_run_theGateColumnNamesThisItemsGateResult ;;
    'run#aSkipIsNoGateToRerun') fn=case_run_aSkipIsNoGateToRerun ;;
    'run#citedEvidenceMustTravelWithTheChange') fn=case_run_citedEvidenceMustTravelWithTheChange ;;
    'run#needsDecisionIsEscalatedWithTheWork') fn=case_run_needsDecisionIsEscalatedWithTheWork ;;
    'run#noGateStillRuns') fn=case_run_noGateStillRuns ;;
    'run#dryRunTouchesNothing') fn=case_run_dryRunTouchesNothing ;;
    'run#reviewRoundsCapAtTwo') fn=case_run_reviewRoundsCapAtTwo ;;
    'run#reviewBudgetIsARecordedValue') fn=case_run_reviewBudgetIsARecordedValue ;;
    'run#theWorkerPromptFitsItsBudget') fn=case_run_theWorkerPromptFitsItsBudget ;;
    'run#aZeroRoundBudgetIsRefused') fn=case_run_aZeroRoundBudgetIsRefused ;;
    'run#aCorrectiveRefusalEarnsOneMoreRound') fn=case_run_aCorrectiveRefusalEarnsOneMoreRound ;;
    'run#aCorrectiveRoundIsHandedTheCorrections') fn=case_run_aCorrectiveRoundIsHandedTheCorrections ;;
    'run#aZeroBlockingRefusalClosesWithItsNotes') fn=case_run_aZeroBlockingRefusalClosesWithItsNotes ;;
    'run#aBlockingRefusalStillTakesTheRoundTwoPath') fn=case_run_aBlockingRefusalStillTakesTheRoundTwoPath ;;
    'run#aReviewThatNamedNothingTakesNoSecondRound') fn=case_run_aReviewThatNamedNothingTakesNoSecondRound ;;
    'run#aCutReportIsRetriedInsteadOfRefused') fn=case_run_aCutReportIsRetriedInsteadOfRefused ;;
    'run#aReportCutTwiceBlocksAsTheTransportsFault') fn=case_run_aReportCutTwiceBlocksAsTheTransportsFault ;;
    'run#aFencedClosingBlockIsACompleteAnswer') fn=case_run_aFencedClosingBlockIsACompleteAnswer ;;
    'run#escalationCarriesTheDecision') fn=case_run_escalationCarriesTheDecision ;;
    'run#mayCloseNoLeavesTheIssueOpen') fn=case_run_mayCloseNoLeavesTheIssueOpen ;;
    'run#metricsRecordedPerItem') fn=case_run_metricsRecordedPerItem ;;
    'run#childUsageIsRecordedPerItem') fn=case_run_childUsageIsRecordedPerItem ;;
    'run#aRedGateIsBlockedByTheTail') fn=case_run_aRedGateIsBlockedByTheTail ;;
    'run#aBlockedItemRecordsTheRoundThatReadIt') fn=case_run_aBlockedItemRecordsTheRoundThatReadIt ;;
    'run#aCloseOverARedGateIsWaivedOrBlocked') fn=case_run_aCloseOverARedGateIsWaivedOrBlocked ;;
    'run#aRedGateIsAttributedToTheTreeThatFailsIt') fn=case_run_aRedGateIsAttributedToTheTreeThatFailsIt ;;
    'run#aFlakedCaseIsOffTheBlockedPath') fn=case_run_aFlakedCaseIsOffTheBlockedPath ;;
    'run#plainTextChildStillReports') fn=case_run_plainTextChildStillReports ;;
    'run#emptyQueueIsNotAFailure') fn=case_run_emptyQueueIsNotAFailure ;;
    'run#stopReasonIsPrintedAndReported') fn=case_run_stopReasonIsPrintedAndReported ;;
    'run#staleBaseStopsTheRunWithItsReason') fn=case_run_staleBaseStopsTheRunWithItsReason ;;
    'run#awaitingDecisionIsNotEscalatedAgain') fn=case_run_awaitingDecisionIsNotEscalatedAgain ;;
    'run#commitsWithoutAnAmbientIdentity') fn=case_run_commitsWithoutAnAmbientIdentity ;;
    'run#reviewerWriteInvalidatesTheRound') fn=case_run_reviewerWriteInvalidatesTheRound ;;
    'run#reviewReadOnlyOffOnlyForDiagnosis') fn=case_run_reviewReadOnlyOffOnlyForDiagnosis ;;
    'run#reviewDiffExcludesTheEvidenceArchive') fn=case_run_reviewDiffExcludesTheEvidenceArchive ;;
    'run#aReviewDiffNamesTheTreeItWasWrittenFrom') fn=case_run_aReviewDiffNamesTheTreeItWasWrittenFrom ;;
    'run#engineNamesNoProjectFact') fn=case_run_engineNamesNoProjectFact ;;
    'adapter#modeFlagIsItsOwnArgument') fn=case_adapter_modeFlagIsItsOwnArgument ;;
    'adapter#theChildIsStartedWithoutContextFiles') fn=case_adapter_theChildIsStartedWithoutContextFiles ;;
    'adapter#theChildIsStartedWithTheResultBound') fn=case_adapter_theChildIsStartedWithTheResultBound ;;
    'adapter#theResultBoundExemptsGateEvidence') fn=case_adapter_theResultBoundExemptsGateEvidence ;;
    'adapter#theChildIsStartedWithTheItemsLedger') fn=case_adapter_theChildIsStartedWithTheItemsLedger ;;
    'adapter#theChildIsStartedWithTheRepeatReadArm') fn=case_adapter_theChildIsStartedWithTheRepeatReadArm ;;
    'adapter#theRecordedRepeatArmIsRefuse') fn=case_adapter_theRecordedRepeatArmIsRefuse ;;
    'adapter#aPathIsRefusedAfterItsRepeatBound') fn=case_adapter_aPathIsRefusedAfterItsRepeatBound ;;
    'adapter#theChildIsStartedWithItsToolDirectoryOnPath') fn=case_adapter_theChildIsStartedWithItsToolDirectoryOnPath ;;
    'adapter#theKeptStreamIsBoundedAndItsRotationIsRecorded') fn=case_adapter_theKeptStreamIsBoundedAndItsRotationIsRecorded ;;
    'adapter#aRotatedStreamKeepsEveryTurnEnd') fn=case_adapter_aRotatedStreamKeepsEveryTurnEnd ;;
    'adapter#anUpdateOnlyStreamKeepsItsUpdates') fn=case_adapter_anUpdateOnlyStreamKeepsItsUpdates ;;
    'adapter#aStreamStillOverItsBoundAfterTheDeltaPassIsCutAndRecorded') fn=case_adapter_aStreamStillOverItsBoundAfterTheDeltaPassIsCutAndRecorded ;;
    'run#aRotatedStreamKeepsEveryTurn') fn=case_run_aRotatedStreamKeepsEveryTurn ;;
    'run#theRotationNamesTheTurnsItSummarised') fn=case_run_theRotationNamesTheTurnsItSummarised ;;
    'state#theSpendSurvivesTheLossOfItsDeltaLines') fn=case_state_theSpendSurvivesTheLossOfItsDeltaLines ;;
    'trim#aResultAboveTheBoundIsTrimmedAndNamed') fn=case_trim_aResultAboveTheBoundIsTrimmedAndNamed ;;
    'trim#aResultUnderTheBoundIsHandedOverWhole') fn=case_trim_aResultUnderTheBoundIsHandedOverWhole ;;
    'trim#aResultBetweenTheNewBoundAndTheOldOneArrivesCut') fn=case_trim_aResultBetweenTheNewBoundAndTheOldOneArrivesCut ;;
    'trim#theTrimIsRecorded') fn=case_trim_theTrimIsRecorded ;;
    'trim#aResultOlderThanTheAgeIsStubbedAndNamed') fn=case_trim_aResultOlderThanTheAgeIsStubbedAndNamed ;;
    'look#oneCallAnswersTheWholeTree') fn=case_look_oneCallAnswersTheWholeTree ;;
    'look#aResultOverTheBoundIsCutAndNamed') fn=case_look_aResultOverTheBoundIsCutAndNamed ;;
    'look#aPathScopesTheWalkToIt') fn=case_look_aPathScopesTheWalkToIt ;;
    'look#aSectionIsPrintedAndTheOtherSectionsAreNot') fn=case_look_aSectionIsPrintedAndTheOtherSectionsAreNot ;;
    'look#aCaseIsPrintedByItsName') fn=case_look_aCaseIsPrintedByItsName ;;
    'policy#aShellTreeSearchIsRefusedAndARecipeIsNot') fn=case_policy_aShellTreeSearchIsRefusedAndARecipeIsNot ;;
    'policy#aRangeReadOfATrackedFileIsRefusedAndALogIsNot') fn=case_policy_aRangeReadOfATrackedFileIsRefusedAndALogIsNot ;;
    'policy#aTailRangeReadOfATrackedFileIsRefusedAndALogTailIsNot') fn=case_policy_aTailRangeReadOfATrackedFileIsRefusedAndALogTailIsNot ;;
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
    'report#theActionListLeadsWithItsCounts') fn=case_report_theActionListLeadsWithItsCounts ;;
    'report#theFullActionListIsPrintedOnRequest') fn=case_report_theFullActionListIsPrintedOnRequest ;;
    'report#landedBranchIsNoAction') fn=case_report_landedBranchIsNoAction ;;
    'report#anUnmergeableItemNamesItsLanding') fn=case_report_anUnmergeableItemNamesItsLanding ;;
    'report#landedBranchesAreNamedAsRemovable') fn=case_report_landedBranchesAreNamedAsRemovable ;;
    'report#emptyRunSaysNone') fn=case_report_emptyRunSaysNone ;;
    'report#anItemOverTheTokenBoundIsFlagged') fn=case_report_anItemOverTheTokenBoundIsFlagged ;;
    'report#anItemBelowTheCallsPerTurnFloorIsNamed') fn=case_report_anItemBelowTheCallsPerTurnFloorIsNamed ;;
    'report#anItemRecordsItsRepeatReads') fn=case_report_anItemRecordsItsRepeatReads ;;
    'report#theRuleRecordsTheSecondReadsItCounted') fn=case_report_theRuleRecordsTheSecondReadsItCounted ;;
    'report#aTruncatedStreamSaysSo') fn=case_report_aTruncatedStreamSaysSo ;;
    'report#theSpendIsSplitByPhaseRoleAndGate') fn=case_report_theSpendIsSplitByPhaseRoleAndGate ;;
    'report#thePreGateRoundIsChargedToItsOwnRole') fn=case_report_thePreGateRoundIsChargedToItsOwnRole ;;
    'report#aCallThatNamesTheGateIsNotAGateRun') fn=case_report_aCallThatNamesTheGateIsNotAGateRun ;;
    'report#aCheckTheGateWouldRunIsTheGateTime') fn=case_report_aCheckTheGateWouldRunIsTheGateTime ;;
    'report#aStreamWithoutDurationsPrintsDash') fn=case_report_aStreamWithoutDurationsPrintsDash ;;
    'report#aRolesTokensRecordTheModelThatAnswered') fn=case_report_aRolesTokensRecordTheModelThatAnswered ;;
    'report#aSessionThatSwitchedModelRecordsBothTallies') fn=case_report_aSessionThatSwitchedModelRecordsBothTallies ;;
    'report#aStateWithoutAModelKeepsItsRoleTotals') fn=case_report_aStateWithoutAModelKeepsItsRoleTotals ;;
    'report#aRoleWhoseTurnNamedNoModelKeepsItsRow') fn=case_report_aRoleWhoseTurnNamedNoModelKeepsItsRow ;;
    'report#aTurnNoSessionRecordReachesIsItsOwnBucket') fn=case_report_aTurnNoSessionRecordReachesIsItsOwnBucket ;;
    'report#thePhaseLineNamesTheClockItIsAShareOf') fn=case_report_thePhaseLineNamesTheClockItIsAShareOf ;;
    'report#theTrendBlockNamesTheGateShareAndTheOutsideWindow') fn=case_report_theTrendBlockNamesTheGateShareAndTheOutsideWindow ;;
    'report#theRefusedLandingIsReadFromTheRecord') fn=case_report_theRefusedLandingIsReadFromTheRecord ;;
    'report#theRecordAndTheStateAgree') fn=case_report_theRecordAndTheStateAgree ;;
    'report#aDifferenceIsNamedBesideTheFigures') fn=case_report_aDifferenceIsNamedBesideTheFigures ;;
    'report#aRunWhoseRecordIsAbsentSaysSo') fn=case_report_aRunWhoseRecordIsAbsentSaysSo ;;
    'report#trendFiguresFromState') fn=case_report_trendFiguresFromState ;;
    'report#aCutRoundIsCountedBesideTheRate') fn=case_report_aCutRoundIsCountedBesideTheRate ;;
    'report#noRoundOrWallPrintsDashNotZero') fn=case_report_noRoundOrWallPrintsDashNotZero ;;
    'report#landingsAreTheStoredNumber') fn=case_report_landingsAreTheStoredNumber ;;
    'report#aCutItemIsNamedOnceAndItsKeptWorkWithIt') fn=case_report_aCutItemIsNamedOnceAndItsKeptWorkWithIt ;;
    'report#anActionNamesAnItemPathThatExists') fn=case_report_anActionNamesAnItemPathThatExists ;;
    'report#theRunGroupsItsItemsByEpic') fn=case_report_theRunGroupsItsItemsByEpic ;;
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
    'clean#aTreeWhoseNameIsAPatternIsStillRead') fn=case_clean_aTreeWhoseNameIsAPatternIsStillRead ;;
    'run#interruptedRunEndsTheChildItStarted') fn=case_run_interruptedRunEndsTheChildItStarted ;;
    'driver#aCaseThatAsksForNoCoreCompilesNothing') fn=case_driver_aCaseThatAsksForNoCoreCompilesNothing ;;
    'driver#aCaseThatAsksForTheCoreGetsItOrRefuses') fn=case_driver_aCaseThatAsksForTheCoreGetsItOrRefuses ;;
    'driver#theCoreIsCompiledOncePerSuite') fn=case_driver_theCoreIsCompiledOncePerSuite ;;
    'driver#aFileFilterRunsOnlyTheCasesTheDeltaOwns') fn=case_driver_aFileFilterRunsOnlyTheCasesTheDeltaOwns ;;
    'driver#aFileSetThatNamesNoCaseIsRefused') fn=case_driver_aFileSetThatNamesNoCaseIsRefused ;;
    'driver#anEmptyDeltaIsStatedWhenTheCallerAllowsIt') fn=case_driver_anEmptyDeltaIsStatedWhenTheCallerAllowsIt ;;
    'driver#aCoreFileOwesEveryCase') fn=case_driver_aCoreFileOwesEveryCase ;;
    'driver#aHunkInACaseBodySelectsThatCaseOnly') fn=case_driver_aHunkInACaseBodySelectsThatCaseOnly ;;
    'driver#aHunkOutsideEveryCaseBodyOwesEveryCase') fn=case_driver_aHunkOutsideEveryCaseBodyOwesEveryCase ;;
    'driver#everyCaseDeclaresTheFilesItCanReach') fn=case_driver_everyCaseDeclaresTheFilesItCanReach ;;
    'driver#aCaseWithNoSelectionRowIsRefused') fn=case_driver_aCaseWithNoSelectionRowIsRefused ;;
    'driver#anArmTableInASecondFileIsCompared') fn=case_driver_anArmTableInASecondFileIsCompared ;;
    'driver#everyEngineScriptIsInTheTable') fn=case_driver_everyEngineScriptIsInTheTable ;;
    'driver#noEngineFileExceedsItsBound') fn=case_driver_noEngineFileExceedsItsBound ;;
    'driver#aFileWithoutABoundIsRefused') fn=case_driver_aFileWithoutABoundIsRefused ;;
    'driver#aNameAssignedTwiceIsRefused') fn=case_driver_aNameAssignedTwiceIsRefused ;;
    'driver#anUndeclaredReadIsRefused') fn=case_driver_anUndeclaredReadIsRefused ;;
    'driver#aLocalIsNotAShareOfAnotherFilesName') fn=case_driver_aLocalIsNotAShareOfAnotherFilesName ;;
    'driver#aQuotedProgramIsNotAShellRead') fn=case_driver_aQuotedProgramIsNotAShellRead ;;
    'driver#aCallInPipelinePositionIsARead') fn=case_driver_aCallInPipelinePositionIsARead ;;
    'driver#theSharedNamesAreDeclared') fn=case_driver_theSharedNamesAreDeclared ;;
    'driver#theReaderOrderIsNotPartOfTheComparison') fn=case_driver_theReaderOrderIsNotPartOfTheComparison ;;
    'driver#theSourceOrderIsDeclared') fn=case_driver_theSourceOrderIsDeclared ;;
    'driver#aCallToALaterFilesFunctionFails') fn=case_driver_aCallToALaterFilesFunctionFails ;;
    'driver#everyEngineFileHasOneOwner') fn=case_driver_everyEngineFileHasOneOwner ;;
    'driver#anOwnedFunctionIsInItsOwnersFile') fn=case_driver_anOwnedFunctionIsInItsOwnersFile ;;
    'driver#aKindIsEmittedOnlyByItsOwner') fn=case_driver_aKindIsEmittedOnlyByItsOwner ;;
    'driver#everyPrintOnlySiteIsConverted') fn=case_driver_everyPrintOnlySiteIsConverted ;;
    'driver#aDeclarationNamingAnUnknownFileFails') fn=case_driver_aDeclarationNamingAnUnknownFileFails ;;
    'driver#theSelectionFollowsTheOwner') fn=case_driver_theSelectionFollowsTheOwner ;;
    'driver#theMovedEngineBehavesAsItDid') fn=case_driver_theMovedEngineBehavesAsItDid ;;
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
    'gate#theBaseIsReadFromTheRunNotFromTheRemote') fn=case_gate_theBaseIsReadFromTheRunNotFromTheRemote ;;
    'gate#theDeclarationCaseIsOwedFirst') fn=case_gate_theDeclarationCaseIsOwedFirst ;;
    'gate#aGreenLogNamesNoFailingCase') fn=case_gate_aGreenLogNamesNoFailingCase ;;
    'gate#aFailingCaseCarriesItsFactsAndItsRerun') fn=case_gate_aFailingCaseCarriesItsFactsAndItsRerun ;;
    'gate#twoFailuresNameTheFirstAndCountTheRest') fn=case_gate_twoFailuresNameTheFirstAndCountTheRest ;;
    'gate#aStepThatNamesNoCaseIsAttributedByItsStep') fn=case_gate_aStepThatNamesNoCaseIsAttributedByItsStep ;;
    'gate#theBlockStaysInsideTheRecordedBound') fn=case_gate_theBlockStaysInsideTheRecordedBound ;;
    'gate#theStepBoundIsClampedByTheCeilingTheCallerNamed') fn=case_gate_theStepBoundIsClampedByTheCeilingTheCallerNamed ;;
    'phase#oneCallPrintsThePhaseState') fn=case_phase_oneCallPrintsThePhaseState ;;
    'phase#aResultOverTheBoundIsCutAndNamed') fn=case_phase_aResultOverTheBoundIsCutAndNamed ;;
    'phase#theIdentitySurvivesTheBoundOnAnyRoot') fn=case_phase_theIdentitySurvivesTheBoundOnAnyRoot ;;
    'repeat#aRepeatReadIsNamedNotHandedOverAgain') fn=case_repeat_aRepeatReadIsNamedNotHandedOverAgain ;;
    'repeat#aVerificationReadIsBoundedNotHandedOverWhole') fn=case_repeat_aVerificationReadIsBoundedNotHandedOverWhole ;;
    'repeat#aRepeatReadIsCountedAndTheRefusalArmWithholdsIt') fn=case_repeat_aRepeatReadIsCountedAndTheRefusalArmWithholdsIt ;;
    'repeat#theRecordedZeroKeepsTheRepeatRuleOff') fn=case_repeat_theRecordedZeroKeepsTheRepeatRuleOff ;;
    'repeat#aBrokenLedgerLeavesTheResultAsReturned') fn=case_repeat_aBrokenLedgerLeavesTheResultAsReturned ;;
    'repeat#aRepeatIsRecordedAsWhatItWithheld') fn=case_repeat_aRepeatIsRecordedAsWhatItWithheld ;;
    'repeat#anItemsLedgerSpansItsChildren') fn=case_repeat_anItemsLedgerSpansItsChildren ;;
    'repeat#theHookWritesTheItemsLedger') fn=case_repeat_theHookWritesTheItemsLedger ;;
    'repeat#aPathUnderTheWorktreeIsOneKeyInTwoSpellings') fn=case_repeat_aPathUnderTheWorktreeIsOneKeyInTwoSpellings ;;
    'repeat#aShellRangeReadFeedsTheTally') fn=case_repeat_aShellRangeReadFeedsTheTally ;;
    'run#childPastItsTurnBoundIsCut') fn=case_run_childPastItsTurnBoundIsCut ;;
    'run#childPastItsCheckBoundIsCut') fn=case_run_childPastItsCheckBoundIsCut ;;
    'run#aChildCheckSkipsTheGatesOwnSuite') fn=case_run_aChildCheckSkipsTheGatesOwnSuite ;;
    'run#aFocusedCaseIsStillAChildCheck') fn=case_run_aFocusedCaseIsStillAChildCheck ;;
    'run#aRotatedStreamStillCountsTheTurnsItDropped') fn=case_run_aRotatedStreamStillCountsTheTurnsItDropped ;;
    'run#theRecordedZeroKeepsTheTurnBoundOff') fn=case_run_theRecordedZeroKeepsTheTurnBoundOff ;;
    'run#eachChildIsBoundedByItsOwnRolesTurns') fn=case_run_eachChildIsBoundedByItsOwnRolesTurns ;;
    'run#eachRoleIsBoundedByItsOwnContext') fn=case_run_eachRoleIsBoundedByItsOwnContext ;;
    'run#aRoleWithNoRecordedTurnBoundRunsUnbounded') fn=case_run_aRoleWithNoRecordedTurnBoundRunsUnbounded ;;
    'run#childPastItsContextBoundIsCut') fn=case_run_childPastItsContextBoundIsCut ;;
    'run#aContextCutWorkerIsContinuedByItsOwnRun') fn=case_run_aContextCutWorkerIsContinuedByItsOwnRun ;;
    'run#aContinuationEndedOnTheSameCeilingStopsTooWide') fn=case_run_aContinuationEndedOnTheSameCeilingStopsTooWide ;;
    'run#aWorkerAtItsCheckpointIsContinuedBeforeTheBound') fn=case_run_aWorkerAtItsCheckpointIsContinuedBeforeTheBound ;;
    'run#theCheckpointIsTheSplitsOwnReading') fn=case_run_theCheckpointIsTheSplitsOwnReading ;;
    'run#aContinuationRunsToTheStop') fn=case_run_aContinuationRunsToTheStop ;;
    'run#theContinuationIsToldWhatItsFirstWorkerRead') fn=case_run_theContinuationIsToldWhatItsFirstWorkerRead ;;
    'run#aSecondChildIsNotEndedByAnEarlierSession') fn=case_run_aSecondChildIsNotEndedByAnEarlierSession ;;
    'run#aChainOfZeroContinuationsEndsAtTheCheckpoint') fn=case_run_aChainOfZeroContinuationsEndsAtTheCheckpoint ;;
    'run#aWorkerBelowItsCheckpointIsNotSplit') fn=case_run_aWorkerBelowItsCheckpointIsNotSplit ;;
    'run#aWorkerAtTheCheckpointWithNoWorkRunsOn') fn=case_run_aWorkerAtTheCheckpointWithNoWorkRunsOn ;;
    'run#aCutWhosePatchDoesNotApplyIsLeftCut') fn=case_run_aCutWhosePatchDoesNotApplyIsLeftCut ;;
    'run#aCutRunsItsGateOnTheKeptWork') fn=case_run_aCutRunsItsGateOnTheKeptWork ;;
    'run#theRecordedZeroKeepsTheContextBoundOff') fn=case_run_theRecordedZeroKeepsTheContextBoundOff ;;
    'state#aQuotaWindowIsAHoldNotAStop') fn=case_state_aQuotaWindowIsAHoldNotAStop ;;
    'run#theQuotaSwitchIsExplicit') fn=case_run_theQuotaSwitchIsExplicit ;;
    'report#theSpendIsWorkNotWaiting') fn=case_report_theSpendIsWorkNotWaiting ;;
    'state#aQuotaCheckThatCannotBeMadeIsNotARefusal') fn=case_state_aQuotaCheckThatCannotBeMadeIsNotARefusal ;;
    'state#noQuotaCommandAsksNothing') fn=case_state_noQuotaCommandAsksNothing ;;
    'state#aHoldMovesTheDeadlineWithIt') fn=case_state_aHoldMovesTheDeadlineWithIt ;;
    'run#aQuotaHoldPausesTheRunAndResumesIt') fn=case_run_aQuotaHoldPausesTheRunAndResumesIt ;;
    'report#theBoundRateIsNamed') fn=case_report_theBoundRateIsNamed ;;
    'report#theArchivesClassesAreDerivedNotInvented') fn=case_report_theArchivesClassesAreDerivedNotInvented ;;
    'state#aCutRecordsTheFilesItsPatchCarries') fn=case_state_aCutRecordsTheFilesItsPatchCarries ;;
    'census#theCensusIsANumber') fn=case_census_theCensusIsANumber ;;
    'census#aClosedPatronWithNoCaseIsCounted') fn=case_census_aClosedPatronWithNoCaseIsCounted ;;
    'census#anUnreadCountIsNamed') fn=case_census_anUnreadCountIsNamed ;;
    'census#everyGuardNamesItsPatron') fn=case_census_everyGuardNamesItsPatron ;;
    'census#aGuardWithoutAPatronIsCounted') fn=case_census_aGuardWithoutAPatronIsCounted ;;
    'census#theCitationFormIsRead') fn=case_census_theCitationFormIsRead ;;
    'census#theCensusRefusesNothing') fn=case_census_theCensusRefusesNothing ;;
    'census#aRunDoesNotConsultTheCensus') fn=case_census_aRunDoesNotConsultTheCensus ;;
    'run#anUndeclaredKindStopsTheRun') fn=case_run_anUndeclaredKindStopsTheRun ;;
    'run#aDeclaredKindMissingAFieldStopsTheRun') fn=case_run_aDeclaredKindMissingAFieldStopsTheRun ;;
    'run#aFullRecordRotatesAndSaysSo') fn=case_run_aFullRecordRotatesAndSaysSo ;;
    'run#theRotationKeepsTheOlderFile') fn=case_run_theRotationKeepsTheOlderFile ;;
    'run#theBoundIsARecordedValue') fn=case_run_theBoundIsARecordedValue ;;
    'run#anEventNamesAnIssueAndAPhaseNeverAPosition') fn=case_run_anEventNamesAnIssueAndAPhaseNeverAPosition ;;
    'driver#noEventCarriesACoordinate') fn=case_driver_noEventCarriesACoordinate ;;
    'run#aFactPrintedOnceIsRecordedOnce') fn=case_run_aFactPrintedOnceIsRecordedOnce ;;
    'run#aConvertedSiteRendersFromItsRecord') fn=case_run_aConvertedSiteRendersFromItsRecord ;;
    'run#aFreeTextReasonSurvivesTheRoundTrip') fn=case_run_aFreeTextReasonSurvivesTheRoundTrip ;;
    'core#theDigestIsTheCompileKey') fn=case_core_theDigestIsTheCompileKey ;;
    'core#theDigestReadsASymlinkedCore') fn=case_core_theDigestReadsASymlinkedCore ;;
    'core#oneCompilePerDigest') fn=case_core_oneCompilePerDigest ;;
    'core#aMissingCompilerIsOneRefusal') fn=case_core_aMissingCompilerIsOneRefusal ;;
    'core#theAnswerIsOneJsonDocument') fn=case_core_theAnswerIsOneJsonDocument ;;
    'core#aMalformedInputIsARefusal') fn=case_core_aMalformedInputIsARefusal ;;
    'core#noPartialParseOnFailure') fn=case_core_noPartialParseOnFailure ;;
    'core#theStateIsWrittenByTheCore') fn=case_core_theStateIsWrittenByTheCore ;;
    'core#theStateAReadCannotParseIsRefused') fn=case_core_theStateAReadCannotParseIsRefused ;;
    'core#thePortabilityCheckReadsTheCore') fn=case_core_thePortabilityCheckReadsTheCore ;;
    'core#theCoresCasesDeclareTheCore') fn=case_core_theCoresCasesDeclareTheCore ;;
    'core#theCoreListsItsOwnCases') fn=case_core_theCoreListsItsOwnCases ;;
    'core#oneSummaryCarriesBothSources') fn=case_core_oneSummaryCarriesBothSources ;;
    'core#aCoreCaseDeclaresItsFiles') fn=case_core_aCoreCaseDeclaresItsFiles ;;
    'driver#theCaseIndexIsItsOwnFile') fn=case_driver_theCaseIndexIsItsOwnFile ;;
    *) printf 'no such shell case: %s\n' "$1" >&2; exit 2 ;;
  esac
  "$fn"
  return 0
}
