# Design — fix-a-reused-worktree-keeps-the-previous-submodule-pin

## Root cause

The reused-worktree path of `cmd_create` switches the kept tree to the new base and stops there
(`tools/loop/worktree.sh:84`). A switch rewrites the superproject's gitlink and leaves the submodule's worktree on
the commit the previous branch recorded, so the index and the module's `HEAD` disagree: `git status` prints the
module path as modified, `start-item` refuses it (`tools/loop/worktree.sh:169`), and the run stops under no item —
`create`'s branch names no item, so nothing is attributed and nothing is escalated.

## The single fix

One step, `sync_submodule` (`tools/loop/worktree.sh:53`): the configured `LOOP_SUBMODULE_PATH` is placed with
`git submodule update --init --checkout <path>` when the tree carries a `.gitmodules`, best-effort with the note the
fresh path already printed (D16). The fresh path calls it where its inline block stood (`:100`); the reused path
calls it immediately after its switch (`:90`). Both paths of `create` now owe the same placement, from one copy of
the rule.

`--checkout` is safe here because the reused path asserts the tree is clean *before* the switch (`:81-83`), so there
is no previous run's work in the module for it to discard; what it discards is the stale pin.

## Blast radius

- `tools/loop/worktree.sh` — the helper plus two call sites; the fresh path's behaviour is unchanged except that the
  `.gitmodules` it tests is the worktree's own (the file that governs that tree) rather than the project root's,
  which is the same file for a base that tracks it.
- `tools/loop-cases.sh` — one case in the declared set, the dispatch table and the `worktree` group's block.
- `tools/loop-mutations.sh` — one record, `13.12`.
- `tools/loop/loop.conf`, `PORTING.md` — comments only, naming the third place that places the module.
- `openspec/specs/agent-issue-loop/spec.md` — one added requirement after archive.

Nothing else calls `create` except `tools/loop/run.sh:675`, whose only interest is the tree it inherits.

## Rollback

`git revert <the fix commit>`: the helper is the fresh path's own block moved into a function, and the reused path
returns to a single `switch`. No state, no schema and no recorded run field is touched, so a run started by the
reverted engine reuses a kept tree exactly as it did before this change.

## Alternatives the evidence eliminated

- **Refuse the pin difference before the switch** (the item's second candidate). Eliminated by measurement, not
  ranked: the dirt is reachable with the verb the tree already carries one path over — the red case's own fixture
  shows `git submodule update --init --checkout <path>` emptying the status and leaving the module on the recorded
  pin (`evidence/red-on-head.log`, and the same call is what mutation `13.12` removes). Refusing would decline to
  place a module the loop can place, and hand a person the repair the loop measured as the expensive one.
- **Sync before the switch.** Not viable: before the switch the index still carries the old base's gitlink, so an
  `update --checkout` would place the pin the tree is leaving.
- **A second refusal after the sync naming remaining dirt.** Not taken: it is a claim about a tree `start-item`
  already refuses with the same paths, and the item asks for one of the two outcomes, not both. The note is what a
  project whose module cannot be checked out gets, exactly as on the fresh path.
