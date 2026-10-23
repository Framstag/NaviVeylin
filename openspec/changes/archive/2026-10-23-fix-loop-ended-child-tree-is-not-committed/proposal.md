# The run stops committing the tree of a child it ended itself

## Why

**Verified in the tree today.** The run commits an item's work before it has read the child's status:
`tools/loop/run.sh:898` (the comment that states the rule, "the run verifies rather than trusts … it commits the
child's work itself") is followed by the unconditional `bash "$LOOP_DIR/worktree.sh" end-item` at
`tools/loop/run.sh:900`, and the status `run_child` returned is only consulted at `tools/loop/run.sh:948`. So a
child the wall-clock cap ended — `tools/loop/run.sh:709`–`722`, which sets `rc="$LOOP_CHILD_TIMEOUT"` and appends
`LOOP-TIMEOUT` to the report — is committed at the instant the cap fired, together with whatever its half-finished
work left in the tree.

**Root cause, one sentence.** `end-item` runs before the run reads the child's status, so the tree is committed as
the child's work while the child is unfinished — a tree whose contents are whatever the child happened to be doing
when the cap killed it.

**Measured, 2026-10-22 (item #271's own report).** The worker of #262 exceeded its 2700 s cap while
`tools/loop-mutations.sh --files 'tools/loop/worktree.sh tools/loop/run.sh'` had mutation `19.2` applied
(`if false; then` in place of `[ "$corrective_round_used" -eq 0 ] && [ "$corrections_only" = "yes" ]`). The run
ended the child at 23:48, `end-item` committed the tree at 23:48:18, and the commit therefore carries the mutation:
`git -C .pi/worktrees/loop show 7db22f9:tools/loop/run.sh | grep -n 'if false; then'` → line 1063. It stayed on
`origin/fix/issue-262-…` until it was amended by hand at 05:05 the next morning.

**The red test.** `run#treeOfAChildTheCapEndedIsNotCommitted` (`tools/loop-cases.sh`, added by this change): a stub
child writes `half-applied.txt` into the change root and then blocks on a fifo, so the 2 s cap ends it with the file
in the tree; on HEAD the run committed that tree and the branch left the base. Taken against the engine at
`214e62e0d4d3b74ef8b12d65eda5ad689b51e480`, 2026-10-23T08:22Z, ~20 s wall clock:

```
  the tree a child the run ended left behind is not committed — the branch stays at the base: expected
  'ffd482d56819939e9170cc035d64ea761930137a', got '3cab8fb3abc5263ccda786c3e0873bb182098d98'
FAIL run#treeOfAChildTheCapEndedIsNotCommitted
```

`evidence/red-HEAD.xml` carries the tally (`tests="1" failures="1"`), `evidence/red-HEAD.log` the transcript.

**Owning requirement.** `agent-issue-loop`, "One command starts a budgeted run, with no dialogue" — its scenario "A
child that outlasts the configured wall-clock is ended and recorded" (`openspec/specs/agent-issue-loop/spec.md:51`)
states that the run "ends that process and every process it started" and that the item "is recorded `blocked` with
that transcript". The delta adds the requirement that owns what such a child leaves: a commit is the run's statement
about a child that finished, and a child the run ended did not.

**Owning guideline section.** `guidelines/Backlog.md`, section "7. The loop", the paragraph
`guidelines/Backlog.md:260` ("What bounds it: …"), which states what the cap leaves behind.

## What Changes

- **`tools/loop/run.sh`** — one guard around the commit: when the child the run ended is the item's worker
  (`worker_exit` is `LOOP_CHILD_TIMEOUT`, or the transcript carries `LOOP-TIMEOUT`), `end-item` is not called and
  the branch stays at the base. The tree is left exactly as the child left it — the run's own rule that nothing is
  discarded without `--reset-worktree` (`tools/loop/run.sh:391`) — and the run prints the line that says so, so the
  next `start-item` refuses it and names it rather than a silent commit carrying it.
- **`tools/loop-cases.sh`** — one case, `run#treeOfAChildTheCapEndedIsNotCommitted`: a stub child the cap ends with
  a file in the change root, and the assertions that the branch is still at the base, that the tree still carries
  the file (kept, not discarded), and that the item is `blocked` with its `LOOP-TIMEOUT` transcript.
- **`tools/loop-mutations.sh`** — record `45.1`, which restores the unconditional commit, so the new invariant is
  falsified by a mutation like every other (red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0).
- **`guidelines/Backlog.md`**, section "7. The loop" — the cap's paragraph gains what the ended child's tree does
  *not* get (a commit) and the measurement behind it (#271); the sentence about a run-ended child as a blocked item
  says it carries no commit.
- **the spec `agent-issue-loop`** — one ADDED requirement, "A child the run ended itself leaves no commit", with
  its one scenario naming the case above.

Surface, before the plan was fixed: `grep -rn 'end-item' tools/loop/run.sh` → two call sites, and only the worker's
is reached by a child the run ended (the corrective round's is a call the run reaches with no status at all — see
design.md, "Not changed"); `grep -rn 'LOOP_CHILD_TIMEOUT' tools/` → the definition in `tools/loop/lib.sh:27` and
the one assignment in `run_child`; the cases that drive a capped child are `run#childOutlastingTheWallClockIsBlocked`
and `run#detachedProcessInTheWorktreeIsEndedByTheCap`, and neither asserts anything about the commit (the first
installs no action, the second asserts only that the survivor is gone), so no existing case is rewritten and no
fixture is realigned — **1 case added, 0 rewritten, 0 fixtures realigned**.

A new mutation record is not one line: `grep -rn 'MUTATIONS=(' tools/` names the two checks that *derive* the set by
reading the array's text (`tools/loop-mutations-selftest.sh:130` and the case
`mutation#everyDeclaredRecordApplies`), so `45.1` is declared inside `MUTATIONS=( … )` rather than appended after it.
The first gate run was red on exactly that, and is kept and attributed in `evidence/gate-attribution.md` —
**1 record added, 0 checks rewritten, 2 derived counts realigned by declaring the record where they read it**.
