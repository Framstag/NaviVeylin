# Design — one order, one name per change

## The root cause, in one sentence

A phase that stands **after** the gate writes the change directory — its own pointers, its `design.md`/`tasks.md` and
the delta it archives — so the digest the gate named and the digest beside the round's diff differ on exactly the
writes the round is then asked to judge.

## The single fix

The order is inverted, and the digest is taught the one rename it must not see:

1. **The change is completed before the gate.** The child that stands before the gate — the gate child — applies the
   reviewer's findings, copies the cited XMLs, runs `tools/loop/evidence-check.sh` and fixes what it reports, and
   archives the change with the delta it merges (`openspec archive --yes`). Then, and only then, it runs the gate.
   (`tools/loop/prompts/worker-gate.md`, Phase E steps 1–3.)
2. **The tail writes no path outside the change's `evidence/`.** It verifies the gate against the archived change,
   copies the XMLs it cites, and records — `proposal.md`, `design.md`, `tasks.md` and a capability spec are named as
   paths it does not touch, and a pointer it would have repaired is `blocked` with the line it printed
   (`tools/loop/prompts/worker-tail.md`, Phase F).
3. **The archive's rename is not a correction.** `loop_tree_identity` enumerates the change family's own files as the
   working tree holds them — tracked, untracked or gitignored — with the archive's date prefix stripped
   (`loop_change_path`, `LOOP_CHANGE_ARCHIVE_PREFIX`), and re-applies `LOOP_REVIEW_EXCLUDE_PATHSPEC` to that half too,
   so the evidence a gate writes after naming its tree stays out of both halves. A file of the change **rewritten**
   after the gate still moves the digest: that is the reviewer's item 3 signal and this change keeps it.
4. **A red gate restores the change to its unarchived name.** The run reads the item's verdict — the closing child's
   own report — and where no verdict exists, the gate's tally through the one predicate its readers share
   (`gate_tally_state`); when the verdict is `blocked`, or the tally is red, or the line says the gate was not run,
   `loop_change_restore` moves the archived directory back under `LOOP_CHANGE_SOURCE_DIR`, before the run commits, so
   an item whose gate did not pass is never left with an archive no gate passed. The verdict is why the decision is
   taken after the closing child rather than before it: a red tally a child waived with `LOOP-GATE-WAIVED` is a close
   the record carries (issue #811), and a restore before that judgement existed unarchived the very change the item
   was about to close over — measured, this change's first gate run: `run#aCloseOverARedGateIsWaivedOrBlocked` failed
   with `expected 'done', got 'blocked'`.

## Why the order and the digest are one change, not two

Archiving before the gate moves the change directory, so without the relocation-invariant digest the fix would hand the
reviewer a tree the gate never saw on every item that archives — the very refusal it removes. Conversely, the digest
alone only makes the *rename* invisible (this change's round-1 review of the earlier attempt refused it on exactly
that): the tail's *rewrite* of `design.md`/`tasks.md` and `openspec archive`'s write into the capability spec still
moved it. The decision's two clauses are one mechanism, and neither half delivers the item's Expected alone.

## Blast radius

- `tools/loop/lib.sh` — `loop_change_family`, `loop_change_path`, `loop_change_archive_dir` and `loop_change_restore`
  added; `loop_tree_identity` enumerates the change family's own files and hashes them under one name.
  `loop_change_identity` is unchanged, so both its callers (`tools/loop/gate.sh:209`, `tools/loop/run.sh:1380`) still
  take one derivation and neither holds a path set of its own.
- `tools/loop/loop.conf` — one key, `LOOP_CHANGE_ARCHIVE_PREFIX`, beside `LOOP_CHANGE_SOURCE_DIR`: the archive's date
  shape is a project fact, and `tools/check-portability.sh` forbids a project path inside the engine. Empty strips
  nothing, which is the old behaviour.
- `tools/loop/run.sh` — the restore, after the closing child and decided from the item's verdict (the gate's tally
  where no child reported one), and the tail's `tasks.md` path, which now resolves under the archived name because the
  change is archived before the tail runs.
- `tools/loop/prompts/worker-gate.md`, `worker-tail.md`, `worker.md`, `gate-deferred.md`, `gate-own.md` — the order
  each child is told.
- `tools/loop/tests/loop-cases.sh` — three cases and their two registration sites; `loop-mutations.sh` — three records.
- `openspec/specs/agent-issue-loop/spec.md` — one requirement removed and its successor added (`:3203`), and one
  digest paragraph with a new scenario (`:3808`); `guidelines/Backlog.recipes.md`, the section "The gate and its suite"
  — the one paragraph that states the rule.

## Rollback

Reverting `run.sh`'s call and the gate child's step restores the old order in one hunk each, and the digest half is
inert when `LOOP_CHANGE_ARCHIVE_PREFIX` is empty and its enumeration only adds paths the two listings lacked: a tree
that wants the old behaviour records the key empty and owes nothing else.

## The alternatives the evidence eliminated

- **The tail re-runs the gate on the corrected tree.** A gate phase is 20–22 min of a 40–50 min item
  (`tools/loop/run.sh:2441`), `LOOP_GATE_RUNS_MAX` exists because 117 of 245 sessions over this loop's archive ran the
  gate three or more times (`tools/loop/tests/loop-cases.sh:13973`), and the runs are the item's whole budget.
- **Excluding the change directory from the digest.** Then no correction of a change's own files is visible to the
  reviewer's item 3 — the finding #721 was refused for. It would delete the signal instead of ordering the phases.
- **Letting the run unarchive at landing when the two digests differ.** That is the hand repair #721 and #90 paid for:
  a reviewer's verdict applied to a tree it never read, and a second gate nobody budgeted.
- **Pinning the round's identity to the gate's recorded value whatever the tree holds.** It claims a tree the round did
  not read, which is the state `openspec/changes/archive/2026-10-22-fix-staging-leftover-discovered-as-map` was
  refused for (issue #819).

## What follows from it, clause by clause

- The gate's `diff=` and the `.identity` beside the round's diff are equal on an item whose tail wrote only evidence —
  the ordinary flow — and unequal when a file of the change outside `evidence/` is rewritten after the gate. Both
  halves are read by one case each (`gate#aMovedChangeNamesTheSameTree`, `gate#theGateCompletesTheChangeBeforeTheGate`).
- A change is one tree before and after its archive, committed or not, so an item lands without a hand repair: the
  gate measured the tree the round reads.
- A gate that did not pass leaves the change unarchived, which is what the tail's report and the run's `blocked`
  record describe (`gate#aRedGateRestoresTheChangeToItsUnarchivedName`).
