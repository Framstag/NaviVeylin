# Design — the environment belongs to the tree, not to the process that started it

## Why the gate sources the file itself

`run.sh` sources the file once and exports what it read, so every child it starts inherits the values. A gate is a
*second entry point* to the same engine: nothing about a `bash tools/loop/gate.sh` invocation says which tree it
belongs to, and a person measuring an item by hand stands in exactly that tree. A file the tree carries is the only
reading that survives both entry points, so the gate reads it rather than trusting its caller's environment.

## Why it is sourced from the tree git resolves

The path is `$LOOP_ROOT/.pi/loop-env.sh`, not `./.pi/loop-env.sh`: `LOOP_ROOT` is the tree the engine resolved, so a
gate invoked from a subdirectory of the worktree (a habit while reading one package's tests) finds the same file. The
case asserts this with a second invocation from `tools/`.

## Why a file and not a symlink

`worktree.sh` writes a file because a `vcpkg` symlink is untracked and pollutes the `git status` the run reads as
evidence — and since #798 that status is now read before the pre-screen, which would make the symlink a refusal
rather than a noise.

## Why sourcing it twice is safe

The file is a list of assignments (`export KEY=value`); it never appends to `PATH` or to another list, so a child that
already inherited the values reads the same ones again. The alternative — a marker variable — would be a second rule
about who sourced what, and the first rule is enough.

## Reversal

Delete the `if [ -f … ]` block and the case fails with `expected 'from-the-worktree', got 'unset'`, which is exactly
the red this change answers. Mutation record `257.1` is that deletion, so the suite notices it without a hand edit.
