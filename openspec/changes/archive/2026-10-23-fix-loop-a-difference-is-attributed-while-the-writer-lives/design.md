# Design — the reading is taken during the child's life, because a writer is gone at its end

## D1 — Attribution must be in time, not post-mortem

A child that writes and exits leaves no process to find. Measured while implementing this: a post-mortem
`/proc` sweep attributes *nothing* for a one-shot writer, so a rule of "block only when a process of the child is
implicated" built on the exit reading is vacuous — it can neither block nor be tested red. The heartbeat is the
only place the run is already running beside the child, so it is where the reading goes.

## D2 — The heartbeat writes beside the transcript, not into it

`$2` is the child's own stream (its stdout and stderr redirected there). Appending to it from another process
mid-run would interleave two writers into one file, and the item's transcript is evidence a reviewer reads. The
tick writes `$2.outside-write`; `run_child` appends that file to the transcript once, after the child ends, and the
per-child file is removed when the next child starts.

## D3 — One reading per child

The first difference is the one a person acts on. A tree another process keeps rewriting — a build, a peer
session — would otherwise add a line every 30 s for the child's whole life. `[ ! -e "$2.outside-write" ]` is the
whole mechanism.

## D4 — Placing a process: group first, then ancestors

`set -m` before the child starts makes it the leader of its own process group, so `ps -o pgid= -p <pid>` is the
cheap test and covers everything the child forked normally. A descendant that gave itself its own session is in
no group the cap signals (#272), so the ancestor chain from `/proc/<pid>/stat` is walked as well, bounded at 64
hops. A pid that is neither is not this child's, whatever it wrote.

## D5 — What the placement changes

- the difference is placed in the child's tree → the item is `blocked` (or the review round discarded), in **both**
  modes: this is evidence, not a guess;
- the difference is unplaced and the run is strict → blocked/discarded as before: the flag says the tree is
  exclusive, and an unexplained difference in an exclusive tree is the owner's problem;
- the difference is unplaced and the run is not strict → recorded, and the item keeps its outcome (#286).

## Risks

| risk | why it is acceptable | how it is caught |
|---|---|---|
| a placed write blocks an item that a peer process inside the child's group made | the placement is the child's own process tree, and nothing the run starts but the child is in it | `run#aWriterTheRunCanPlaceBlocksTheItem` |
| the tick's `git status` costs time in a long child | one `git status` per 30 s, and the `/proc` sweep only after a difference | the gate's own suites |
| the heartbeat's own shell is listed as a process inside the project | it passes its own pid as an extra skip, and sweeps from `/` | the note's contents in both cases |
