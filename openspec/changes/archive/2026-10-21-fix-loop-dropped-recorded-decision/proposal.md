# fix-loop-dropped-recorded-decision

## Why

Issue #196. Verified on 2026-10-21 in this tree (`HEAD 8a45db6`) and on the issue's own timeline.

**What happened, from the tracker's record.** The item declares a space of fixes ("Bounds to settle: the
number itself, whether the metric is lines or declarations, and whether C++ is in or out"), so the loop
escalated it with three ordered options and a recommendation (`agent/needs-decision` + `status/on-hold`,
2026-10-20T21:49:31Z). A person answered `Decision: 2` at 2026-10-21T04:52:39Z, and `answer.sh` did exactly what it
is specified to do: two seconds later the decision label and the hold were gone and `status/open` was back
(2026-10-21T04:52:41Z). The next run picked the item at 08:47:15Z and, 86 seconds later, escalated **the same
fork a second time** with a fresh option list — 1 = 800 lines, 2 = ratchet, 3 = per-class, recommending the
opposite of what had been answered.

**Root cause, one sentence:** the answer is a comment, the run asks the tracker for `number,title,body,labels,
author,state` and never for `comments`, and `WorkPackage.render` renders title and body only — so the recorded
answer has no path from the tracker into the next worker's package, and a fresh child re-derives the question
the answer settled from a body that still states it as open.

**The measurement that proves the child never saw it.** The child's own event stream
(`.pi/issue-loop/items/196/events.jsonl`) holds **0** occurrences of `gh issue view` and **0** of `Decision: 2`:
it worked from the package alone. The package (`.pi/issue-loop/items/196/work-package.md`) is the issue's body
between `<<<ISSUE_TEXT` and `ISSUE_TEXT`, with no decision field at all.

**The second half, and why it is part of the same mechanism.** Nothing binds an answer to the question it
answered: `answer.sh` records the bare text `Decision: 2`, and the second escalation numbered its own options
from 1. So the recorded `2` now reads as *ratchet* rather than *1500 lines per file* — the answer was not only
unread, it became unreadable.

**Red tests on HEAD**, written before the production change (logs in `evidence/`, timestamps from the runs):

```
FAIL WorkPackage#recordedDecisionIsCarried: the package carries a decision block of its own
  — expected to find '<<<ITEM_DECISION' in: … ---- end issue work package ----
FAIL WorkPackage#noDecisionIsRecorded: the block is present even with no comments
FAIL answer#theAnswerNamesTheQuestion: the comment names the question it answers: 'answers:' not found in:
  gh issue comment 71 --body Decision:\ merge\ them\,\ the\ older\ wording\ is\ redundant
FAIL run#workPackageCarriesTheRecordedDecision
FAIL prompt#workerTreatsARecordedDecisionAsSettled
```

`evidence/red-workpackage-case.log`, `evidence/red-no-decision-case.log`, `evidence/red-answer-case.log`,
`evidence/red-loop-case.log` (2026-10-21T17:13:32Z–17:13:56Z; the prompt case is the fifth and was added with
its section, its red being the section's absence). The green runs of the same harnesses are
`evidence/green-issue-tooling.log` (113 cases, 17:21:16Z) and `evidence/green-loop.log` (143 cases);
the revert-check is `evidence/mutation-36.1.log`.

**Owning capability:** `agent-issue-loop`, requirement "Each item is worked by a fresh process, reviewed by a
different one" — the package is what that process receives. **Owning guideline section:** `guidelines/Backlog.md`
§5 "Who may write what", the block that states the rules keeping the decision queue answerable (routed by
`AGENTS.md`'s Documentation Map: "the backlog itself, or the automation over it → `Backlog.md` §4, §5, §9, §10").

## What changes

- `tools/issues/core/WorkPackage.java` — a second delimited block, `<<<ITEM_DECISION` … `ITEM_DECISION`, carrying
  `recorded: <timestamp>` (or `recorded: none`), `question:` and `decision:`. The question is read from the
  answer's own `<!-- answers: … -->` marker, and for an answer written before that marker existed (this item's)
  from the nearest earlier comment that is not itself an answer — the same first-line convention the decision
  queue prints. Both fields are escaped against the package's delimiters and clipped at one named bound.
- `tools/loop/lib.sh` / `tools/loop/run.sh` — `loop_issue_json <n> --comments`, and only the work-package call
  passes it: the pre-screen reads candidates by the hundred and needs no comments.
- `tools/issues/answer.sh` — the recorded answer carries the question it answered on an `<!-- answers: … -->`
  line, so a later escalation cannot silently re-point a bare option number at a different list.
- `tools/loop/prompts/worker.md` — a recorded decision is settled: implement it, do not re-ask it; an escalation
  that follows one names it and says what it leaves open.
- `guidelines/Backlog.md` §5 — the rule, beside the two that already keep the queue answerable.
- `tools/issues/fixtures/gh` — `issue view --json a,b` now returns only the named fields, as the real CLI does.
  Without that the stub serves every field to every caller and a reader that forgets one is green anyway.

## Impact

Additive: a package gains a block, an answer gains one marker line, a read gains one field. Nothing that exists
changes meaning — an item with no recorded decision renders `recorded: none`, which is a fact a worker can act
on. Rollback is reverting the blocks and the flag; mutation `36.1` is that rollback for the flag, and the four
red cases are the rollback for the rest.

Files: `tools/issues/core/WorkPackage.java`, `tools/issues/core/Cases.java`,
`tools/issues/fixtures/gh`, `tools/issues/answer.sh`, `tools/issue-tooling-cases.sh`,
`tools/loop/lib.sh`, `tools/loop/run.sh`, `tools/loop/prompts/worker.md`,
`tools/loop/fixtures/scenarios/decided/issues.json`, `tools/loop-cases.sh`,
`tools/loop-mutations.sh`, `guidelines/Backlog.md`.

**Gate: none owed.** No compiled file changes (the Java core is the issue tooling, run by `javac --release 17`
inside its own self-test), so the two shell harnesses are the gate; they are green in full, with no exclusion
to name.

## Alternatives eliminated

Recorded in `design.md`, "Alternatives the evidence eliminated": rendering every comment, carrying the whole
answer thread, quoting the question in the answer only (no package change), and leaving option numbering to the
worker's judgement.
