# Fix: a round that ends with a difference in the caller's tree takes it back

## Why

`run_child` reads the project's own tree before its child (`tools/loop/child.sh:283`) and after it
(`tools/loop/child.sh:668`); when the two readings differ it writes one transcript line
(`tools/loop/child.sh:679`) and returns. No branch acts on the difference: the engine's only discard,
`discard_kept_worktree` (`tools/loop/leftovers.sh:376`), acts on `$LOOP_WORKTREE`, and the only reader of the
project tree's dirt refuses the next run rather than repairing it (`tools/loop/run.sh:655`), with
"the run cannot tell its own leftover from an edit made while it was not running" (`tools/loop/run.sh:662`). Two
such refusals in a row stop the chain, and it has been measured four times in two days: #1080's
`tools/loop/evidence-check.sh` (+143) and its spec hunk on 2026-11-25, #1034's five files (+126/−3) and its
archive directory on 2026-11-26 08:55 CET — the write that stopped the chain at 08:57 — and `tools/__pycache__`
with a probe directory at 06:04.

- **root cause** — the moment the run knows the caller's tree moved, it has the tree in hand and writes a
  sentence about it, so nothing returns that tree to the state the round found it in and the next round reads
  this round's own leftover as a person's edit and stops.
- **red case** — `run#childWriteOutsideTheWorktreeIsNoted` gains three arms and fails on the base commit
  `9be4a4c1`: `no path a child of the run wrote is left in the caller's tree: expected '0', got '3'`
  (XML tests="1" failures="1", 2026-11-27, `evidence/red-on-base.xml`), and passes on the change
  (tests="1" failures="0", `evidence/green-on-change.xml`).
- **owning requirement** — `agent-issue-loop`, "Each item is worked by a fresh process, reviewed by a different
  one", the scenario "A process that writes outside its tree is caught"
  (`openspec/specs/agent-issue-loop/spec.md:480`).
- **route** — `guidelines/Backlog.recipes.md`, its section `7a` "Running one item: the script, the child and the package",
  whose paragraph "What it does **not** claim" states the rule this change edits: a difference "is recorded and
  not turned into a verdict".

LOOP-ROUTE: guidelines/Backlog.recipes.md §7a

- **surface** — one engine function added (`tools/loop/leftovers.sh`), one call site (`tools/loop/child.sh`),
  one case rewritten (three arms added, the stub's reviewer arm replaced), one `layout.tsv` row and one
  guideline paragraph — counted with `tools/look.sh 'LOOP-OUTSIDE-WRITE'` (151 matches in 56 files) and
  `tools/look.sh 'restore_project_difference|project_state_diff'` (38 matches in 20 files).

## What the owner decided, and what this applies

The item carries the owner's decision of 2026-11-26: **every difference the round's patch carries is removed**,
placed or not, and the restore does not consult `outside_write_decides` (`tools/loop/evidence.sh:406`) — the
writes this was measured on all read `attributed=none`, because a worker's own shell in the project is
deliberately unplaced, so a restore limited to placed differences would remove none of them.

## What changes

At the moment a round ends with a difference, the caller's tree is returned to the state the round found it in:
tracked paths restored with `git -C "$LOOP_ROOT" checkout --`, untracked paths removed
(`restore_project_difference`, `tools/loop/leftovers.sh:422`, called from `tools/loop/child.sh:685`). The paths
come from the round's kept patch `<item dir>/outside-write/<phase>.patch` where one exists (#1092), and from
`project_state_diff` (`tools/loop/evidence.sh:259`) otherwise. The record is written first, so the item keeps
what the round took back, and each path taken back is named on the transcript beside it. See `design.md` for
the two paths that are left where they stand and why, and for the alternative this change eliminates.
