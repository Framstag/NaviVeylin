# Design — a round that ends with a difference in the caller's tree takes it back

## Root cause

The comparison around a child is turned into one transcript line and nothing else. `run_child` takes the
reading before the child (`tools/loop/child.sh:283`) and after it (`tools/loop/child.sh:668`), and on a
difference it writes the line (`tools/loop/child.sh:679`) and returns. The run's own answer to the same dirt at
the *next* round's start is a refusal, not a repair (`tools/loop/run.sh:655`), because
`tools/loop/run.sh:662` cannot tell a leftover from an edit made while no run was live — and two refusals in a
row stop the chain. So the loop pays for its own leftovers twice: once as a stopped run, once as a person
reading the diff by hand.

## The single fix

One function, `restore_project_difference` (`tools/loop/leftovers.sh:422`), called at the one place the run
already has both readings in hand and the child's transcript open (`tools/loop/child.sh:685`):

1. the paths — the round's kept patch `<item dir>/outside-write/<phase>.patch` where one exists (#1092, the patch
   file's own two `git diff` headers), and the lines `project_state_diff` returns
   (`tools/loop/evidence.sh:259`) where there is none;
2. every path is removed, **placed or not**, which is the owner's decision of 2026-11-26: the restore never asks
   `outside_write_decides` (`tools/loop/evidence.sh:406`), because the writes this was measured on all read
   `attributed=none` — a worker's own shell in the project is deliberately unplaced;
3. the removal takes the shape the reading reports: a path the after-reading carries as `??` is removed with
   `git clean -qfd --`, anything else with `git checkout --`, both against `$LOOP_ROOT`;
4. the record is written **before** the removal (`tools/loop/child.sh:679` precedes `tools/loop/child.sh:685`),
   so the item still carries what the round took back and a person can act on it, and each path taken back is
   named under it as `LOOP-OUTSIDE-WRITE-RESTORED:` (`tools/loop/leftovers.sh:462`);
5. what the tree still reads as a difference after the removal is named `LOOP-OUTSIDE-WRITE-KEPT:`
   (`tools/loop/leftovers.sh:458`) instead of assumed clean — the next round's start-time check reads the same
   reading and would refuse on it.

## The two paths left where they stand, and why

- **the run's own state directory** — mirrored from the exclusion the start-time refusal already draws
  (`tools/loop/run.sh:649`): this process wrote it seconds ago, and a project that does not gitignore `.pi/`
  would otherwise have every round delete the record of the round.
- **a path the caller had already changed before the round began** — named `LOOP-OUTSIDE-WRITE-NOT-OURS:`
  (`tools/loop/leftovers.sh:465`) and not touched. A run refuses to start on a dirty tree
  (`tools/loop/run.sh:655`), so such a path is not a previous round's leftover but a person's edit made while
  this round ran — the very ambiguity the start-time refusal exists for — and `checkout --` would discard their
  work with the child's. This is the one arm the owner's "every difference is removed" does not cover: it
  removes nothing of the round's where the round wrote into a file that was already the caller's, and it says
  so on the transcript rather than silently taking the file. Removing it instead needs the reading that tells a
  person's edit from a child's (#988), which this item does not carry.

Out of scope, and named so a reader does not look for it here: paths git ignores are not in the reading at all,
and their scope is #1099 (`tools/__pycache__`, the probe directories); the kept patch itself is #1092.

## Blast radius

`tools/loop/child.sh` (one call), `tools/loop/leftovers.sh` (one function, no existing function edited),
`tools/loop/layout.tsv` (the new function and the row's description), `tools/loop/tests/loop-cases.sh` (one case,
three arms added and the stub's reviewer arm replaced by a third path of its own),
`guidelines/Backlog.recipes.md`, its section `7a` (the paragraph that states the old rule). No compiled file, no app source,
no fixture, no prompt. Every other reader of the difference — the transcript line, the item's note, the strict
mode, the review round's standing — is unchanged: the record is written first and the removal happens after it.

The case's shape had to change with it: the old case relied on the review round's own `rm` to take the two
earlier paths back, which the restore now does at the end of each round, so each child writes a path of its own
and the case reads all three as gone.

## Rollback

Revert the change. The one call in `tools/loop/child.sh` and the one function in `tools/loop/leftovers.sh` are
the whole behaviour; the state, the events and the report vocabulary are untouched, so nothing recorded under
the change needs interpreting after a revert. A round that leaves a difference goes back to recording it and
leaving it standing, and the next round refuses on it as before.

## Alternatives eliminated

- **Repair at the next round's start instead of at this round's end** — the reading at the start cannot tell the
  leftover from a person's edit, which is the reason `tools/loop/run.sh:662` refuses; repairing there means
  discarding an unattended edit, and a person's work is exactly what the refusal protects.
- **Let the existing discard (`tools/loop/leftovers.sh:376`) do it** — it is bound to `$LOOP_WORKTREE`, the tree a
  phase *may* write; the caller's tree is a different tree and the item is about that one.
- **Ask `outside_write_decides` first and remove only what the run can place** — measured to remove nothing: all
  the writes this was measured on read `attributed=none`, because a worker's own shell is deliberately unplaced.
  This is the owner's decision of 2026-11-26, taken against exactly this alternative.
- **Refuse the item and hand the dirt back** — that is today's behaviour one round earlier, and it is what stops
  the chain.
