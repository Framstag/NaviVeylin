# The run names what changed when a child writes in the project's own tree

## Why

**Verified in the tree today.** `run_child` reads the project's own tree before its child starts
(`tools/loop/run.sh:707`, `before=$(project_state)`) and again when the child has ended
(`tools/loop/run.sh:787`, `after=$(project_state)`); `project_state` (`tools/loop/run.sh:565`) is the tree's own
answer to "am I a work tree" plus its `git status --porcelain`. On any difference the run appends one fixed
sentence to the child's transcript (`tools/loop/run.sh:791`), and `report_field … LOOP-OUTSIDE-WRITE`
(`tools/loop/run.sh:950`) turns it into the item's block reason (`tools/loop/run.sh:1014`, and
`tools/loop/run.sh:1161` for a round-2 worker), which the block's escalation carries onto the tracker.

**Root cause, one sentence.** The comparison's own content is discarded at the moment it is made: the two
readings differ, and the report replaces that difference with a constant sentence, so neither the path that
appeared nor the line that flipped survives — and the block the run then escalates names nothing a person can act
on.

**Measured, 2026-10-23 (issue #274's round-2 worker, reported as #285).** The item was recorded `blocked` with
`needs-decision` on exactly that sentence. Every write in the child's own transcript
(`.pi/issue-loop/items/274/events.jsonl`) went to `.pi/worktrees/loop/**` or to the gitignored evidence directory,
and `git -C /home/tim/projects/NaviVeylin status --porcelain` is empty before and after the run — so the event
the run reported cannot be reproduced or attributed from anything the run kept, and the round's work was lost to a
question nobody could answer.

**The red test.** `run#childWriteOutsideTheWorktreeIsBlocked` (`tools/loop-cases.sh:3353`), rewritten: its stub
child writes `demo-owned.txt` into the project's own tree *and* leaves a process of its own working there, and the
case asserts what the transcript and the tracker carry. Taken against the engine at
`f2d27735818acd6d05095a9b331f4ee2bae1bb74` (2026-10-23T16:49Z, 7 s wall clock) — the case file of this change,
`tools/loop/run.sh` at HEAD — `bash tools/loop-selftest.sh --case 'run#childWriteOutsideTheWorktreeIsBlocked'`,
exit 1:

```
  the reason the escalation carries names the path that appeared, so the decision is answerable: '?? demo-owned.txt'
  not found in: the worker wrote outside the worktree — the project's own tree changed while it ran
FAIL run#childWriteOutsideTheWorktreeIsBlocked
loop-selftest: 1 of 1 cases failed
```

`evidence/red-HEAD.xml` carries the tally (`tests="1" failures="1"`), `evidence/red-HEAD.log` the transcript.

**Owning requirement.** `agent-issue-loop`, "Each item is worked by a fresh process, reviewed by a different one"
— its scenario "A process that writes outside its tree is caught"
(`openspec/specs/agent-issue-loop/spec.md:396`) states that the item is recorded blocked *with that reason*, and
says nothing about what the reason has to contain. The delta adds the requirement that owns the record's content:
the paths that appeared and the processes the tree still shows.

**Owning guideline section.** `guidelines/Backlog.md`, section "7. The loop" (`guidelines/Backlog.md:235`), which
states what the run does with a child whose start it could not honour and what it does with a child the cap ended,
and says nothing about a write in the project's own tree.

## What Changes

- **`tools/loop/run.sh`** — four functions between `project_state` and the review diff, and three call sites:
  `project_state_diff` (the symmetric difference of the two readings), `outside_write_clause` (one bounded,
  quoted clause out of that list), `outside_write_pids` (the processes the project shows whose working directory is
  inside it and outside the worktree — `loop_pids_under`'s question, which the cap's own sweep already asks), and
  `outside_write_line` (the one line the transcript gains). The line reads
  `LOOP-OUTSIDE-WRITE: the project tree changed while this process ran: ?? demo-owned.txt; process(es) whose
  working directory is inside the project: <pid> <command>`, and the two block reasons carry it verbatim, so the
  escalation's comment on the tracker names the path and the process.
- **`tools/loop-cases.sh`** — the one case, rewritten rather than added: it now leaves a process of the child's in
  the project, and asserts the status line in the transcript, the path in the item's note, the pid among the
  processes named, and the path in the tracker's own comment (the `gh` stub's record). The run names a survivor;
  it does not end one it did not start, so the case ends it.
- **`tools/loop-mutations.sh`** — record `12.3` re-anchored on the call site it now quotes; the invariant it
  removes (the run catching a child's write outside the change root) is unchanged, and so is the case it names.
- **`guidelines/Backlog.md`**, section "7. The loop" — the paragraph that says what the run does with a write in
  the project's own tree, with this measurement.
- **the spec `agent-issue-loop`** — one ADDED requirement, "A write outside the change root is recorded by what
  changed", with its one scenario naming the case above.

surface: 0 cases added, 1 rewritten, 0 fixtures realigned, 1 mutation record re-anchored — the pass before the plan
was fixed: `grep -rn 'LOOP-OUTSIDE-WRITE' tools/` → the append site
(`tools/loop/run.sh:791`), the two block reasons (`:1014`, `:1161`), record `12.3` of the mutation set, and no
prompt or fixture at all; `grep -rn 'project_state' tools/loop/run.sh` → the definition, the two comparisons in
`run_child` and the reviewer's own read-only comparison (`:1060`, `:1096`), which the round's own refusal owns and
this change does not touch. The cases that assert the behaviour are two:
`run#childWriteOutsideTheWorktreeIsBlocked` is rewritten, `run#reviewerWriteInTheProjectIsDiscarded` reads the same
two states and is re-run unchanged — **0 cases added, 1 rewritten, 0 fixtures realigned, 1 mutation record
re-anchored**.
