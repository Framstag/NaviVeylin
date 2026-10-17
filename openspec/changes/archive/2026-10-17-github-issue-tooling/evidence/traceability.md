# Scenario → case → task traceability

`specs/issue-backlog/spec.md` holds **22** scenarios; the table below has **22** rows, one per scenario, each
naming the case that exercises it and the task that owns that case. `grep -c '^#### Scenario:'` agrees:

```
$ grep -c '^#### Scenario:' openspec/changes/github-issue-tooling/specs/issue-backlog/spec.md
22
```

Cases are run by `tools/issue-tooling-selftest.sh` (30 cases: 11 in the Java core, 19 shell cases driven
against the stub `gh`). The task column is the `tasks.md` item that owns the code under test.

| # | requirement | scenario | case | task |
|---|---|---|---|---|
| 1 | One issue per distinct finding | A new finding is filed | `create#writesMarkerAndLabels` | 4.3 |
| 2 | One issue per distinct finding | A repeated finding is refused, not duplicated | `create#duplicateRefusesAndNamesTheIssue` | 4.2 |
| 3 | One issue per distinct finding | A deliberate duplicate is possible and recorded | `create#duplicateRefusesAndNamesTheIssue` (the `--allow-duplicate` half) | 4.2 |
| 4 | Labels carry the item's kind, area, state and provenance | Each fact has its own namespace | `create#writesMarkerAndLabels`, `Taxonomy#mapsEveryEntryInTodoMd` | 4.3, 2.2 |
| 5 | Labels carry the item's kind, area, state and provenance | A value outside the vocabulary is refused | `Taxonomy#unknownValueRefuses`, `update#refusesUnknownLabel` | 2.4, 4.4 |
| 6 | Labels carry the item's kind, area, state and provenance | A priority namespace is reserved | `Taxonomy#vocabulariesAreClosed` | 2.1 |
| 7 | Labels carry the item's kind, area, state and provenance | Provenance is distinguishable | `adapter#emitsTheSameFindingsAsTheDryRun` (`source/todo-import`), `create#writesMarkerAndLabels` (`source/agent`) | 5.2, 4.3 |
| 8 | Only an authorised actor's item may be acted on | An unauthorised author is not acted on | `Policy#gateMatrix`, `close#refusesOnPolicyFailure` | 3.2, 4.6 |
| 9 | Only an authorised actor's item may be acted on | The opt-in is required and is not the author's decision alone | `Policy#gateMatrix` | 3.2 |
| 10 | Only an authorised actor's item may be acted on | The tooling cannot grant the opt-in to itself | `surface#reservedLabelRefusesInEveryWritePath` | 4.9 |
| 11 | Only an authorised actor's item may be acted on | A foreign identity stops the tooling rather than narrowing it | `policy#preflightRefusesUnknownLogin`, `Policy#allowlistIsNotEmptyAndNamesTheAuthenticatedUser` | 3.8, 3.1 |
| 12 | Identity survives its source | A historical reference resolves | `import#secondRunIsANoOp`, `adapter#emitsTheSameFindingsAsTheDryRun` (the `nvid` marker) | 5.3, 5.2 |
| 13 | Identity survives its source | A partial identifier does not match | `import#secondRunIsANoOp` (the partial-identifier half) | 5.3 |
| 14 | Identity survives its source | Re-importing is a no-op | `import#secondRunIsANoOp` | 5.3 |
| 15 | Closure is the only terminal state, and deletion is not offered | A closure without evidence is refused | `close#requiresReasonAndEvidence` | 4.5 |
| 16 | Closure is the only terminal state, and deletion is not offered | The reason is recorded before the closure | `close#requiresReasonAndEvidence` | 4.5 |
| 17 | Closure is the only terminal state, and deletion is not offered | No operation deletes an issue | `surface#noDeletePath` | 4.8 |
| 18 | The tooling runs against the current project without being told | Invoked from a subdirectory | `lib#discoversTheCurrentProject` | 2.5 |
| 19 | The tooling runs against the current project without being told | Invoked inside a separate working tree of the project | `lib#discoversTheCurrentProject` (linked-worktree assertion) | 2.5 |
| 20 | The tooling runs against the current project without being told | The environment does not decide the project | `lib#discoversTheCurrentProject` (hostile-environment assertion) | 2.5 |
| 21 | The tooling runs against the current project without being told | Invoked outside a project | `lib#refusesOutsideAGitTree` | 2.5 |
| 22 | The tooling runs against the current project without being told | A project fact lives in one place | `Profile#everySectionIsPresent`, `lib#noProjectValueInTheEngine` | 2.1, 2.6 |

Notes on two rows:

- **Row 3** and **row 13** are exercised inside a case that names another behaviour, because the two halves
  share their setup (a tracker that already carries the fingerprint / the source id). Each half has its own
  assertion with its own message, so a failure names which half broke.
- **Rows 18-20** are three assertions of one case, for the same reason: they are three invocations of the same
  discovery rule with different working directories and environments.
- **Row 22** is the invariant with two halves: the profile declares every section the engine needs
  (`Profile#everySectionIsPresent`), and the engine names no value of its own
  (`lib#noProjectValueInTheEngine` — the repository/actor names by literal, and every `category/` value read
  from the profile, so the check cannot become a stale hand-written list).
