# Acceptance evidence — the wrapper against the live tracker

Recorded 2026-10-17 by the apply pass of `agent-issue-loop`, against the live tracker
(`Framstag/NaviVeylin`, 158 issues, none carrying `agent/eligible`).

## 10.2 — the dry run (the queue comes from the live tracker)

```
$ tools/loop/run.sh --dry-run --items 1 --minutes 90
agent-issue-loop: items=1 minutes=90 review=on may-close=yes push=no max-decisions=3 review-read-only=on dry-run=1
state: budget 1 items / 90 min · review on · may-close yes · max-decisions 3 · base 3eec054107de
state: deadline 2026-10-17T18:46:02Z
pre-screen: 0 eligible, 0 excluded — gates 2-5 stay the worker's (design D6)
nothing eligible: the pre-screen found no candidate this run may work
**agent-issue-loop — 2026-10-17T17:16:08Z** (budget 1 items / 90 min · review on · may-close yes · max-decisions 3 · base 3eec054107de)

closed 0 · skipped 0 · blocked 0 · budget left 1 items / 90 min · spent 0 min

closed 0 · skipped 0 · blocked 0 of 1 items · budget 90 min

| issue | class | verdict | change | root cause | red test | gate | review | outcome |
|---|---|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — | — | nothing processed |

no exclusions

skipped: none
excluded before any work: none
decisions the owner owes: none
branches: none
worktree: not present at run end (/home/tim/projects/NaviVeylin/.pi/worktrees/loop)
state: /home/tim/projects/NaviVeylin/.pi/issue-loop/run.json
exit=0
```

The queue the run would work and the tracker it read agree: the pre-screen reports **0 eligible, 0
excluded**, and `list.sh --eligible-only --json` answers 0 at the same moment.

```
$ tools/issues/list.sh --eligible-only --json | jq length
0
$ tools/issues/list.sh --json | jq length   # every issue, for the decision cap
158
$ jq ".excluded | length" .pi/issue-loop/run.json
0
```

## 10.3 — acceptance: the one real item, and why it is unproven

The acceptance run — `tools/loop/run.sh --items 1 --minutes 90 --review on` — has **no eligible candidate
to work**: every issue on the tracker is open and **none carries `agent/eligible`**. The migration imported
them unauthorised on purpose, so that a machine cannot authorise its own work, and `agent/eligible` is
reserved and unwritable by the loop itself (change 1, D2). The pre-screen therefore ends the run with
`nothing eligible` (exit 0) and creates no worktree, no branch, no label and no agent process.

Per the change proposal ("Verification"), the acceptance is therefore **unproven by a real item** and moves
to the first run that follows an owner authorising one — an owner action this apply pass must not perform.
What is proven instead is what the change can prove without it: **45 offline cases** driving the real
wrapper, worktree, branches, commits, label transitions and closure in throwaway repositories, and **11
revert-checks** (`revert-checks.md`), all red when mutated and green when restored.

```
$ tools/issues/list.sh --eligible-only   # as of this run
0 match(es)
```

## 10.1 — the self-test in an empty environment

```
$ env -i PATH="$PATH" HOME="$HOME" bash tools/loop-selftest.sh
PASS report#headerAndTableShape
PASS report#yieldIsCountedFromState
loop-selftest: all 45 cases passed (JDK 27, offline, no token, no sandbox)
```
