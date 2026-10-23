# A difference is attributed while the writer is alive, and only a placed difference decides an item

## Why

`fix-loop-outside-write-is-recorded-with-what-changed` (#286) stopped the run from turning any difference in the
project's own tree into a verdict about the child that happened to be running. It left one question open: nothing
in the record said **whose** the difference was, so a child that really did write into the project tree was
recorded like a peer session's stray edit.

The obvious answer — look at the processes after the child ends — does not work: the writer is the child itself, and
it is already dead when its transcript is written. A post-mortem sweep attributes nothing, ever.

The reading has to be taken **while the child runs**. The run already ticks a heartbeat beside every child every
`LOOP_PROGRESS_SECONDS`; that tick is where the tree can be read and the processes alive at that moment can be
named and placed.

## What Changes

- `run_child`'s heartbeat reads `project_state` once per tick until it finds the first difference, and writes two
  lines beside the child's transcript: what changed and when, and the processes alive then whose working directory
  is inside the project and outside the worktree, with the ones belonging to the child's own process tree marked.
- `pid_in_child_tree` decides that marking: the child's process group (`set -m` makes it the leader) or an ancestor
  chain that reaches the child (the `setsid` survivor of #272).
- What decides an item is now: a difference the run **can place** in this child's tree, or any difference at all in
  a run that declared the tree exclusive (`--strict-outside-write yes`). A difference it cannot place is recorded
  and the item keeps its outcome.
- The reading happens once per child: the first difference is the one a reader needs, and a tree another process
  keeps rewriting would otherwise fill the item's directory.

## Capabilities affected

`agent-issue-loop` — one `MODIFIED` requirement, "Each item is worked by a fresh process, reviewed by a different
one": one scenario added, one scenario's `THEN` extended.

## Not in this change

The tick costs one `git status` and one `/proc` sweep per `LOOP_PROGRESS_SECONDS` while a child runs, and only when
the tree has changed does it cost the second sweep. A project that sets `LOOP_PROGRESS_SECONDS` very low pays it
more often; the default (30 s) is the recorded one.
