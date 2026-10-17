# Scenario → case → task

Every scenario in `specs/agent-issue-loop/spec.md` with the case that exercises it and the task in
`tasks.md` that owns that case. The row count equals
`grep -c '^#### Scenario:' specs/agent-issue-loop/spec.md` (32).

The gate is `tools/loop-selftest.sh` (45 cases, offline, no token, no model, no device); the revert-checks
in `revert-checks.md` prove that eleven of them fail when the invariant behind them is removed — so no row
below is a case that cannot go red.

| # | requirement | scenario | case | task |
|---|---|---|---|---|
| 1 | One command starts a budgeted run | The budget is arguments, not a prompt | `state#initAndShowRoundTrips`, `state#defaultsComeFromLoopConf` | 1.1, 1.4 |
| 2 | One command starts a budgeted run | The run stops on its own authority | `state#nextHonoursEveryStopCondition` | 1.2 |
| 3 | One command starts a budgeted run | A run is recorded | `run#metricsRecordedPerItem`, `report#headerAndTableShape` | 6.7, 7.1 |
| 4 | Only authorised, eligible items | An unauthorised identity stops the run before any work | `run#preflightRefusalStopsBeforeAnyWork` | 6.1 |
| 5 | Only authorised, eligible items | An item without the opt-in is not worked | `prescreen#withoutTheOptInIsNeverQueued` | 2.1 |
| 6 | Only authorised, eligible items | The run cannot authorise itself | `labels#neverWritesReservedLabel` | 4.3 |
| 7 | Only authorised, eligible items | A gated item is recorded, not silently dropped | `prescreen#ranksBugsFirstAndDropsFeatures` | 2.1 |
| 8 | Version control belongs to the script | One working tree per run | `worktree#seedsAllFourInputs` | 3.1 |
| 9 | Version control belongs to the script | One branch per item, from the run's base | `worktree#oneBranchPerItemOffTheBase` | 3.2 |
| 10 | Version control belongs to the script | A stale base stops the next item | `worktree#refusesWhenMainAdvanced` | 3.3 |
| 11 | Version control belongs to the script | Nothing is pushed or opened by default | `worktree#noPushWithoutTheFlag` | 3.5 |
| 12 | A fresh process per item, another to review | The item arrives as a prepared package | `prompt#packageIsAFileAndNotInlined`, `prompt#inlineInjectionCannotReachTheInstructionPosition` | 5.3, 5.4 |
| 13 | A fresh process per item, another to review | The reviewer is not the writer | `run#oneItemOrderIsFixed` | 6.2 |
| 14 | A fresh process per item, another to review | A reviewer that writes is discarded | `run#reviewerWriteInvalidatesTheRound`, `run#reviewReadOnlyOffOnlyForDiagnosis` | 6.9 |
| 15 | A fresh process per item, another to review | Review rounds are bounded | `run#reviewRoundsCapAtTwo` | 6.5 |
| 16 | The run verifies rather than trusts | A claim without a commit is blocked | `run#childClaimWithoutCommitIsBlocked` | 6.3 |
| 17 | The run verifies rather than trusts | The gate's evidence is read by the run | `run#claimedGateWithoutEvidenceIsBlocked` | 6.3 |
| 18 | The run verifies rather than trusts | Evidence outlives the run | `run#citedEvidenceMustTravelWithTheChange` | 6.3 |
| 19 | Closure follows a passing review | A passing item is closed with evidence | `labels#verdictToLabelMatrix`, `run#metricsRecordedPerItem` | 4.1, 6.7 |
| 20 | Closure follows a passing review | Closure can be left to a person | `run#mayCloseNoLeavesTheIssueOpen` | 6.6 |
| 21 | Closure follows a passing review | A blocked item is never closed | `run#childClaimWithoutCommitIsBlocked`, `run#reviewRoundsCapAtTwo` | 6.3, 6.5 |
| 22 | The run survives interruption | State is written only between agent processes | `labels#writesOnlyBetweenChildren` | 4.4 |
| 23 | The run survives interruption | An interrupted item leaves no item looking in progress | `labels#trapRemovesInProgressOnSignal` | 4.2 |
| 24 | The run survives interruption | State is not committed | `state#initAndShowRoundTrips` (the gitignore assertion) | 1.1 |
| 25 | A pile of open decisions stops the run | The cap is reached | `run#decisionCapRefusesToStart` | 6.1 |
| 26 | A pile of open decisions stops the run | An item needing a decision is escalated, not abandoned | `run#needsDecisionIsEscalatedWithTheWork` | 6.5 |
| 27 | The mechanics are configuration | A project with no gate still runs | `run#noGateStillRuns` | 6.2 |
| 28 | The mechanics are configuration | The agent command is one indirection | `state#agentCommandIsTheOnlyIndirection` | 1.5 |
| 29 | The mechanics are configuration | No phase names a project fact | `run#engineNamesNoProjectFact`, `state#projectKeysAreReadFromLoopConf` | 6.10, 1.4 |
| 30 | Running an agent elsewhere | The run works with no isolation environment installed | `run#engineNamesNoProjectFact` (a `PATH` with no `sbx`) | 6.10 |
| 31 | Running an agent elsewhere | The environment holds no tracker credential | `run#oneItemOrderIsFixed` (the child's argv carries the prompt and nothing else) | 6.2 |
| 32 | Running an agent elsewhere | Work needing a device stays on the host | `prescreen#deviceItemIsAVerdictNotWork`, `labels#verdictToLabelMatrix` | 2.1, 4.1 |

Seven cases were added by this traceability pass (and the coverage check it prompted), because the table is
what made the gap visible:
`prescreen#withoutTheOptInIsNeverQueued` (scenario 5), `prescreen#settledItemIsDropped` (an item the tracker has
already settled — the coverage check for the pre-screen's own verdicts),
`prescreen#deviceItemIsAVerdictNotWork` (32),
`run#claimedGateWithoutEvidenceIsBlocked` (17), `run#citedEvidenceMustTravelWithTheChange` (18),
`run#needsDecisionIsEscalatedWithTheWork` (26) and `run#noGateStillRuns` (27). Four of them needed the
implementation to grow as well: the run now checks that a claimed gate has a `test-results` XML (17) and that
a change citing a test result carries it in its own `evidence/` directory (18); an escalation posts the work
already done as a comment on the item (26); and an empty `LOOP_GATE_CMD` is recorded as
`skipped (no gate configured)` rather than as an invented tally (27).

## Row count

```
$ grep -c '^#### Scenario:' openspec/changes/agent-issue-loop/specs/agent-issue-loop/spec.md
32
```

Rows in the table above (excluding the header and the separator):

```
32
```
