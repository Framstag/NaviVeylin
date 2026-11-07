# The child cap ends what the child started, not only the group it started

## Why

**Verified in the tree today.** The cap's whole authority is one signal to the child's process group:
`tools/loop/run.sh:695` (`kill -TERM -"$child"`) and `tools/loop/run.sh:697` (`kill -KILL -"$child"`). A
descendant that called `setsid` is in neither group, so the run records the child as ended while that process
keeps running at its working directory inside the change root. The comment above the wait already claims the
opposite — `tools/loop/run.sh:641`, "A child that outlasts the cap is ended together with everything it
started" — which is the assertion the code does not implement.

**Root cause, one sentence.** The cap reaches the child by process group, and a process that gave itself its own
session is reachable only by the tree it names as its working directory, so the group signal ends the child and
leaves the writer the child had started — which is what kept mutating and restoring the worktree after the run
had ended (`LOOP-TIMEOUT`, run of 2026-10-22 on #262: kill at 23:48, the mutation log's own mtime 23:55, its
last restore leaving `tools/loop/run.sh` modified against the commit the run had just made). The same primitive
the tree already owns for this test is `loop_pids_under` (`tools/loop/lib.sh:274`), used by
`tools/loop-selftest.sh:85` and `tools/loop/loop-clean.sh`.

**The red test.** `run#detachedProcessInTheWorktreeIsEndedByTheCap` (`tools/loop-cases.sh`, added by this
change) starts a stub child that puts a writer at the worktree's root in a session of its own and then blocks on
a fifo, so the 2 s cap ends the child; on HEAD the writer outlived the run. Taken on HEAD
(`b1b8de08c6e1673601adf54638fda18c51a51258`), 2026-10-23, 19 s wall clock:

```
  the process the ended child left in the worktree (pid 1576227) outlived the run — the cap must end what the
  child started, and a session of its own is not a group the cap reaches
FAIL run#detachedProcessInTheWorktreeIsEndedByTheCap
```

`evidence/red-HEAD.xml` carries the tally (`tests="1" failures="1"`), `evidence/red-HEAD.log` the transcript.

**Owning requirement.** `agent-issue-loop`, "One command starts a budgeted run, with no dialogue" — its scenario
"A child that outlasts the configured wall-clock is ended and recorded" (`openspec/specs/agent-issue-loop/spec.md:51`)
states that the run "ends that process and every process it started". The delta adds the requirement that owns
*how* a process outside that group is reached.

**Owning guideline section.** `guidelines/Backlog.md`, the section "7. The loop", the paragraph `guidelines/Backlog.md:261`
("A child that outlasts it is ended together with everything it started").

## What Changes

- **`tools/loop/run.sh`** — the cap branch of `run_child` ends every process still working in the worktree after
  the group signals (`loop_end_pids_under`, the tree test #213/#233 already own) and appends each one to the
  transcript as `LOOP-ENDED: …`, before the item's outcome is written.
- **`tools/loop-cases.sh`** — one case, `run#detachedProcessInTheWorktreeIsEndedByTheCap`: a stub child that
  leaves a writer at the worktree's root in a session of its own, and the assertions that it is gone and named.
- **`tools/loop-mutations.sh`** — record `44.1`, which removes the sweep, so the new invariant is falsified by a
  mutation like every other (red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0).
- **`guidelines/Backlog.md`**, section "7. The loop" — the paragraph that already claims "everything it started"
  gains the mechanism (the tree test, the `LOOP-ENDED` line) and the measurement behind it (#272).
- **the spec `agent-issue-loop`** — one ADDED requirement, "A process the child's group signal cannot reach is
  ended by the tree it works in", with its one scenario naming the case above.
