# Proposal — a gate run by hand in the kept worktree runs under that worktree's environment

## Why

`worktree.sh` writes the machine-local values a tree needs — the dependency root above all — into
`<worktree>/.pi/loop-env.sh`, and `run.sh` sources it for every child it starts. Nothing sourced it for the gate
itself, so a `tools/loop/gate.sh` typed by hand in that tree ran **without** the dependency root: Gradle could not
create a task (`vcpkg installed-status file not found`), the gate died at configuration in 21 s having executed no
test, and it wrote a tally that reads `tests=0 failures=0 errors=0` — a red indistinguishable from a real one and
faster than a working gate, so the natural reading of it is "green".

Measured: a hand-run gate in `.pi/worktrees/loop` failed at configuration in 21 s, no test executed; the same tree
through `run.sh` gated normally.

## What changes

1. `tools/loop/gate.sh` sources `$LOOP_ROOT/.pi/loop-env.sh` when the tree carries it — before it plans its steps, so
   every step, the build included, inherits the environment the tree recorded.
2. `tools/loop/worktree.sh` says so where it writes the file: it is written for the children **and** for the gate the
   tree hosts.
3. A case, `gate#runsUnderTheWorktreeEnvironment`, and mutation record `257.1` for the sourcing line.

## What this does not change

`run.sh`'s own sourcing, the file's contents, and the gate's steps: a tree that carries no `loop-env.sh` gates exactly
as before, which is the case's third assertion. Sourcing the file twice is harmless — it assigns, it never appends.

## Blast radius

`tools/loop/gate.sh` (three lines plus a comment), `tools/loop/worktree.sh` (a comment), `tools/loop-cases.sh` (one
case, its list entry and its dispatch arm), `tools/loop-mutations.sh` (one record), and one line of
`.pi/skills/fix-loop/REFERENCE.md`. No compiled file is touched; the gate owed is the loop's own suite.
