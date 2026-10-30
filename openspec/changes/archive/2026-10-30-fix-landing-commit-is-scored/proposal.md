# Proposal — fix-landing-commit-is-scored

## Why

**The condition, verified in the tree at base `0bc31dc` (2026-10-30).**
`tools/loop/state.sh:432`–`:434` accepts exactly three facts about a landing — `--merged-branch`,
`--merged-commits`, `--merged-evidence` — and the record they build carries no commit at all
(`tools/loop/state.sh:492`, `.merged:{branch,commits,evidence}`). The run has the commit in hand at the
moment it lands: `merge_committed_branch` re-reads the base branch's tip into `base` (`tools/loop/run.sh:286`)
and `worktree.sh` prints the same commit as the landing line (`tools/loop/worktree.sh:494`), and both `done`
calls that record a landing (`tools/loop/run.sh:252`, `:1928`) throw it away.

So the report's third trend figure cannot be a number. `tools/loop/report.sh:250` prints

```
  landings: not in the run's state — no record says which commit a landing merged, so a main it reddened is the tracker's answer: gh issue list --label broken-main
```

and the reason it names is the one the tracker cannot fix: `broken-main` identifies its condition by
`{job, case}` (`tools/sensors/conditions/broken-main.sh:24`–`:32`), so a tracker read gives a count nobody can
attribute to a landing. `openspec/changes/archive/2026-10-29-run-report-trend-figures/design.md:80` deferred
exactly this record to this item (option c), because it is three components and a state-schema addition.

**Root cause, one sentence.** A landing's own commit is never recorded, so no landing can be attached to the
pipeline run it caused and the report has no number to print.

**The item's open question, measured before the key was specified** (`evidence/probe-gh-run-list.log`,
2026-10-30T07:32:35Z, `gh` 2.x authenticated as the owner):

```
$ gh run list --commit 2f954a04b3f75e138c529fffe7985ca011375866 --limit 10 --json databaseId,headSha,headBranch,event,workflowName,status,conclusion
[{"conclusion":"success","databaseId":41012101266,"event":"push","headBranch":"main","headSha":"2f954a04…","status":"completed","workflowName":"Build Debug APK"}]
$ gh run list --commit 0bc31dc7ac29c056c8cc2f15cbdf17c33528a4b2 --limit 10 --json …
[]
```

`--commit` names the run a landing caused, and a commit no run exists for answers `[]` — so *"no run yet"* is a
state the score has to record as unscored rather than read as green. The same probe shows why the score cannot
take the newest run for a sha: one headSha carries a scheduled `Sensors` run beside the landing's `push` run
(`2f954a04`'s neighbours `357a4670`, `42d3e000`, `53021e88`), so the score reads the **push** run and nothing
else.

**Surface.** On the base tree:

```
$ grep -rn 'landings:' tools/ openspec/specs/ | grep -v /archive/
tools/loop/report.sh:250        the line, its comment at :206–:211
tools/loop-cases.sh:7907,7942,7957   three trend cases that read up to the `  landings:` prefix
tools/loop-cases.sh:7980–:7985  report#landingsNamedNotInvented, which asserts the line's wording
$ grep -rn -- '--merged-branch|merged\.sha|landedReddenedMain' tools/ openspec/specs/ | grep -v /archive/
tools/loop/run.sh:252, :1928    the two `done` calls that record a landing
tools/loop/state.sh:432         the flag block
$ grep -n 'landingsNamedNotInvented' tools/loop-mutations.sh
697:  '72.6@-@tools/loop/report.sh@report#landingsNamedNotInvented@…'
```

surface: 3 cases added (`state#doneRecordsTheCommitItLanded`, `state#theLandingScoreIsReadOnceAndStored`,
`state#aLandingWithNoRunIsUnscored`), 1 case rewritten (`report#landingsNamedNotInvented` →
`report#landingsAreTheStoredNumber`), 1 case extended (`run#mergesItsCommittedBranchBeforeClosing`), 1 mutation
record renamed (72.6), 1 state flag added (`done --merged-sha`), 1 state command added (`score-landings`), 1
fixture added (`tools/loop/fixtures/scenarios/landed/runs.json`), 1 spec requirement added and 1 scenario
restated, 0 compiled files — `grep -rn 'landings:' tools/ openspec/specs/ | grep -v /archive/` (`4 lines in 2
files`), `grep -rn -- '--merged-branch\|merged\.sha\|landedReddenedMain' tools/ openspec/specs/ | grep -v
/archive/` (`3 lines in 2 files`), `grep -n 'landingsNamedNotInvented' tools/loop-mutations.sh` (`1 record`)

**Owning capability and guideline section.** The capability is `agent-issue-loop`; the rule the report answers
is its requirement *The report states whether the loop is getting better*
(`openspec/specs/agent-issue-loop/spec.md:2691`), whose scenario *The figure the state does not hold is named
where it is* (`:2740`) this change restates. `AGENTS.md:196`–`:200` routes a change to the loop to that spec and
to the suites `tools/loop-selftest.sh`; the guideline section that owns *who may read what* is
`guidelines/Backlog.md` §4 "The invariants" (I12 — one writer of the state; I13 — no project fact in an
engine), and it does not change: the one network read belongs to the state's only writer, never to the report.

## What Changes

- `tools/loop/state.sh`: `done` gains `--merged-sha <sha>`, recorded at `.merged.sha`; a value that is not a
  commit is refused. A new command, `score-landings`, reads `gh run list --commit <sha>` for every item that
  records a commit and no score, and writes `.landedReddenedMain` (`true` reddened, `false` did not, absent
  unscored).
- `tools/loop/run.sh`: both `done` calls that record a landing pass the commit the merge made — the `base` the
  merge function already re-reads — and each calls `state.sh score-landings` once, best-effort, so the run's own
  report prints the number and one read happens per landing rather than per report.
- `tools/loop/report.sh`: the `  landings:` line prints the stored score — how many of the scored landings
  reddened `main`, how many await a run, how many predate the flag — and still makes no tracker read.
- The spec delta restates that scenario and adds *A landing records the commit it landed, and a run scores it*.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-issue-loop` — *The report states whether the loop is getting better*: the one figure the state did not
  hold is now a figure the state holds; and a new requirement states what a landing records and how it is
  scored.

## Impact

`tools/loop/{state,run,report}.sh`, `tools/loop-cases.sh`, `tools/loop-mutations.sh` (record 72.6 names the
rewritten case), `tools/loop/fixtures/scenarios/landed/runs.json`, the shipped spec. No compiled file, no Gradle
case, no app behaviour. A state written before this change carries no `.merged.sha` and is printed as *no
landing records a commit*, never backfilled by guessing.
