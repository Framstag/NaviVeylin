# An item the tracker does not answer for ends no run

## Why

**The claim, verified in the tree today.** `tools/loop/run.sh:1878-1882` (base
`fc9e0eb012dcfc781bf744d128acfac1cd9138f8`) reads the item it is about to work and ends the run when that read
fails:

```bash
  issue_json=$(loop_issue_json "$issue" --comments) || {
    printf 'run: gh could not read #%s\n' "$issue" >&2
    stop_run "gh could not read #$issue — the tracker did not answer"
    break
  }
```

The read is not cheap: `tools/loop/lib.sh:491-505` asks through the `gh` seam of `tools/issues/lib.sh:94-125`,
which has already retried a transient failure on the recorded backoff (5 s, 20 s, 60 s) and reports
`tracker-unreachable` after the fourth attempt. What is wrong is the **unit**: one unreadable candidate out of a
queued window ends the window.

**Root cause, in one sentence:** the run treats its per-item read — one candidate's record, needed only to start
that candidate — as the run's own precondition, so a failure that decides nothing about the item and touches
nothing in the tree stops the queue and the clock with it.

**The rule already exists one level up and was never carried down.** `tools/loop/prescreen.sh:211-229` records a
candidate the tracker did not answer for as `unreadable`, keeps building the queue from the candidates that did
answer, and its case `prescreen#oneUnreadableCandidateDoesNotStopTheScreen`
(`tools/loop-cases.sh:2400-2419`) is the same rule applied per read. The run's own read of the item it picked
(issue #753) is the one place that rule never reached: measured 2026-11-03, a run stopped after 1 h 9 min with 12
candidates unread and 6 hours of its budget left, and 3 of that day's 86 archived runs ended this way.

**The red case, before any production code.** `run#oneUnreadableItemDoesNotEndTheRun`
(`tools/loop-cases.sh`, the run group) drives the real engine against the stub `gh` with
`GH_STUB_READ_FAIL="21 --json number,title,body,labels,author,state,comments"` — the candidate's number and the one
field only the *run's* read asks for — so the pre-screen's read of #21 answers, the item reaches the queue, and the
run's own read of it does not. Run with the base's engine in the tree (`git show
fc9e0eb012dcfc781bf744d128acfac1cd9138f8:tools/loop/{run,state}.sh` written over this change's files, which were
written back byte-identically after the run):

```
bash tools/loop-selftest.sh --case run#oneUnreadableItemDoesNotEndTheRun
run#oneUnreadableItemDoesNotEndTheRun| run: gh could not read #21
run#oneUnreadableItemDoesNotEndTheRun| stop: gh could not read #21 — the tracker did not answer
...
FAIL run#oneUnreadableItemDoesNotEndTheRun
loop-selftest: 1 of 1 cases failed
```

taken 2026-11-07T21:13:59Z (the run's own header), kept as `evidence/red-on-HEAD.log` and
transcribed to `evidence/red-on-HEAD.xml` (`tests=1 failures=1`); the same case is green on this change
(`evidence/green-on-change.log`, 2026-11-07T21:14:11Z).

**Owning capability:** `agent-issue-loop`. **Owning guideline section:** `guidelines/Backlog.recipes.md` "Refusals,
records and the landing" (section 7d — the section list is `tools/look.sh '^## ' --path guidelines/Backlog.recipes.md`),
the section that owns what the run does with a record it could not take —
reached from `AGENTS.md`'s row "the loop's procedure … the refusals it records" → `Backlog.md` chapter 7, section d.

surface: 1 case added, 0 rewritten, 0 fixtures realigned — `tools/look.sh 'gh could not read|oneUnreadableCandidateDoesNotStopTheScreen' --path tools`

Before the plan was fixed that command printed 11 matches in 7 files: `tools/loop/run.sh` 2 (the message and the
stop reason, `:1879-1880`), `tools/loop-cases.sh` 4 (the pre-screen's sibling case: its comment, its name, its list
entry, its dispatch) and five `tools/issues/*.sh` 1 each, whose `gh could not read` is a *write*'s read in another
command and not this rule. The run's own read of the item it picked was asserted by no case at all. The one case
added drives the `two` scenario and the stub `gh` that `case_run_aFailedPreScreenRecordsItsStopReason` already
uses, so no fixture is realigned; the delta is two engine scripts (`tools/loop/run.sh`, `tools/loop/state.sh`) and
one guideline section.
