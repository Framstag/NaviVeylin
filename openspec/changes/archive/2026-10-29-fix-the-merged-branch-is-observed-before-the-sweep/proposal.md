# fix-the-merged-branch-is-observed-before-the-sweep

## Why

**Root cause** — one mechanism. `case_run_aMergedBranchIsRemoved` asserts the branch after the run has ended
(`tools/loop-cases.sh:7228` runs the run, `:7229` reads `git branch --list 'fix/issue-*'`), and the run deletes
that branch by **two independent paths**: the item's own release after its merge (`tools/loop/worktree.sh:698`,
reached from `tools/loop/run.sh:1885`) and the end-of-run sweep (`tools/loop/worktree.sh:664`, `prune-landed
--apply`, reached from `tools/loop/run.sh:344`). The assertion is downstream of both, so the record that removes
either path alone leaves it true: mutation `42.2` (`tools/loop-mutations.sh:343`, `release-branch`'s
`gitr branch -d "$branch"` replaced with a no-op) is not noticed by anything.

The record set's own rule decides the repair: a record a case cannot notice is not evidence, so the record is
repaired rather than withdrawn while the behaviour it names has no observing case. (The sweep's own deletion,
`tools/loop/worktree.sh:664`, carries no record at all — `bash tools/loop-mutations.sh --files
tools/loop/worktree.sh --list` names 16 records and none of them mutates that line — so withdrawing `42.2` would
leave the release observed by nothing.)

**The red test on HEAD** — the named case does not go red, which is the defect. Taken once, on the base tree:

```
$ bash tools/loop-mutations.sh --only '42.2'
loop-mutations: selected 1 of 202 records (labels: 42.2)
GREEN  42.2  run#aMergedBranchIsRemoved   <-- the case did not notice the mutation
restore 42.2 run#aMergedBranchIsRemoved green again

red-when-mutated: 0 · not-noticed: 1 · restore-failures: 0        exit=1
```

This is a shell gate, so the evidence is the harness's own `GREEN`/`RED` line and its exit status rather than a
JUnit XML (`.pi/skills/revert-check`, "When the gate is a shell self-test, not Gradle"). The transcript is
`evidence/red-on-head.log`.

**Why the mutation stays green, measured** — the same record applied by hand to `tools/loop/worktree.sh` and the
run's own output kept (`evidence/probe-42.2-run-output.log`): the report the run prints at its end — before the
sweep runs — still names the item's branch, and the sweep is what removes it afterwards.

```
branches: fix/issue-21-an-eligible-bug-whose-cited-path-still-e loop-20261029-085718
landed, removable: fix/issue-21-an-eligible-bug-whose-cited-path-still-e loop-20261029-085718
…
removed fix/issue-21-an-eligible-bug-whose-cited-path-still-e (its patches are in the base)
worktree: 2 landed branch(es) removed · 0 kept (carries work the base does not) · 0 kept (checked out) · …
```

With the release intact the same report line names only the run's own `loop-<timestamp>` branch, so the line is the
observation `42.2` is missing: it is read before the sweep can answer for it.

**The single fix** — one assertion in the one case the record names, reading the report the run prints *before*
its end sweep: no `fix/issue-*` branch survives the merge that landed the work. The other half of the case (the
worktree released from the branch, the change in the base) is unchanged.

**Owning requirement** — `openspec/specs/agent-issue-loop/spec.md:1635`, "A branch the loop made is removed when
its work is in the base", scenario "A branch the run merged is removed" (`:1641`, case
`run#aMergedBranchIsRemoved`). The delta adds the scenario the case was missing: the removal is observed where the
run merges, not only where the run's end sweeps.

**Owning guideline** — `.pi/skills/revert-check` (the contract's "Scope of the run: **every** case the guard
protects", and "the case does not assert the invariant" as one of the two silent lies of a shell mutation) with
`guidelines/Build.md` §2 "Common behavior" for the iteration/gate discipline the change is verified under.

surface: 1 case rewritten, 0 cases added, 0 fixtures realigned, 0 mutation records added, 0 records rewritten
(42.2 unchanged, newly noticed), 1 spec scenario added — `grep -l -E
'aMergedBranchIsRemoved|release-branch|prune-landed|landed, removable' tools/*-cases.sh tools/loop/*.sh
tools/loop/prompts/* tools/loop/fixtures/scenarios/*/* openspec/specs/*/spec.md .pi/skills/*/SKILL.md
guidelines/*.md` (`5 files`: `tools/loop-cases.sh`, `tools/loop/report.sh`, `tools/loop/run.sh`,
`tools/loop/worktree.sh`, `openspec/specs/agent-issue-loop/spec.md`). Of those five the change edits one —
`tools/loop-cases.sh`; `report.sh` is the line it reads and `run.sh`/`worktree.sh` are the two paths the assertion
sits downstream of.

## What Changes

1. `tools/loop-cases.sh`, `case_run_aMergedBranchIsRemoved`: the run's output is captured and its own report line
   (`branches:`) is asserted to name no `fix/issue-*` branch — the observation is taken before the end sweep, so
   deleting the release's `git branch -d` (record `42.2`) makes the case red instead of green.
2. The change's spec delta records that scenario: the branch the run merged is gone by the time the run prints its
   report, so the sweep cannot answer for the release.

Additive: no engine behaviour changes, no test is removed, no record is re-labelled. Rollback is the revert of one
case body; nothing else depends on the new assertion.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-issue-loop`: the requirement "A branch the loop made is removed when its work is in the base" gains one
  scenario — the merged branch's removal is observable at the run's own report, before the end-of-run sweep.

## Impact

- `tools/loop-cases.sh` — one case body (`run#aMergedBranchIsRemoved`), its `# files:` declaration unchanged.
- `tools/loop-mutations.sh` — unchanged; record `42.2` becomes noticed.
- `openspec/specs/agent-issue-loop/spec.md` — one scenario merged by the archive.
- No engine script, prompt, fixture or Gradle source is touched, so no build is owed: the gate for this delta is
  the shell suite `tools/loop-selftest.sh` plus the record check `bash tools/loop-mutations.sh --check`
  (`tools/loop/loop.conf`, `LOOP_GATE_SUITES`).
