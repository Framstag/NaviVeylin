# Design — consuming a prepared change

## Context

The run's model of an item is: a claim, a fresh process, and a change the worker writes itself
(`tools/loop/prompts/worker.md`, phases A–F). Three facts of the tree meet that model awkwardly once a change can
exist before the run:

| fact | where it is stated | what it costs |
|---|---|---|
| unarchived changes are gitignored | `.gitignore:76` — `openspec/changes/*/`, with `!openspec/changes/archive/` | the worktree, cut from the base, cannot inherit one |
| an unarchived change is the work a killed worker leaves behind | `tools/loop/loop.conf`, `LOOP_UNTRACKED_RECORD_DIR` (issue #308) | committing it to `main` would make every half-written change a tracked artifact of every run |
| the worker plans its own change | `worker.md` phase C: `fix-<slug>`, or `add-<slug>` when the class is `roles.proposeClass` | a recorded decision is a constraint on a re-derived plan, not the plan |

The package already has two delimited blocks (`<<<ITEM_TEXT`, `<<<ITEM_DECISION`) and one recorded field for an
absent one (`recorded: none`), so the third block is a shape the renderer already knows how to be careful about.

## Goals / Non-Goals

**Goals:**

- The change a person wrote and gated is the change that is applied.
- A tier-B item is refused before a child starts when its change is missing or no longer ready.
- A worker that cannot use the change says what is wrong with it, and never writes a replacement.

**Non-Goals:**

- Committing an unarchived change to `main` (see D1).
- Relating one change to another, or one item to another.
- Changing what a tier-A item does. Its path is untouched, block included — the block is emitted with
  `change: none`.
- Deciding who authorises work. `agent/eligible` stays a person's write.

## Decisions

### D1 — Copy the change into the worktree; do not track it

**Chosen:** `worktree.sh` copies `openspec/changes/<name>/` into the item's worktree at item start.

**Eliminated: commit the change to `main`.** It reverses `.gitignore:76`, whose stated reason is that an unarchived
change *is* in-flight work: every run's worktree, every `openspec list` and every peer session would then see every
half-written change of every session, and the killed-run recovery (#308) depends on that directory being the place
where a run's unfinished work is invisible to git. A tracked change would also need a second rule for *when* it
becomes trackable — a new state machine in the file system, which the tracker is for.

**Eliminated: put the plan in the item body.** It would let the package carry the plan with no file work, and it is
what the loop does today for a tier-A item. It loses two things the tree depends on: `openspec validate --strict`
and the readiness checker both read files, so the plan could not be validated before the run; and the change would
have no `evidence/` directory, which is where the run's own rules require a cited test result to live.

**Eliminated: a symlink from the worktree to the project's change directory.** `worktree.sh` already avoids a
symlink for `vcpkg` because it "pollutes `git status`, which the run reads for evidence" (`worktree.sh:170`), and a
symlink would make the project's tree writable from inside the worktree, which is the boundary
`--strict-outside-write` exists to police.

### D2 — The source is cleaned, not deleted, and the closure is the guard

The run writes nothing in the project's own tree, so the copy's source is reported by `loop-clean.sh` and removed by
`--apply`, beside the worktree's recorded build paths — the same mechanism the tree already uses for what the loop
leaves on disk. Double-consume is prevented by the item's closure (`status/fixed-by`), which the pre-screen already
honours, and not by the leftover's absence: a run killed between the landing and the clean must not leave an item
that is silently unworkable.

### D3 — The pre-screen refuses, and the refusal is a finding

The pre-screen is where a script decides what a script can decide (`guidelines/Backlog.md` §6), and "the change
this item names is not there" and "the readiness checker no longer reports ok" are both script-decidable. So the
refusal happens before any worktree, branch or child exists, and it writes the checker's own verdict line on the
item — a finding, not a bare hold, which is the distinction the tree has already paid for (#193). The label that
refusal carries is `shaping-inbox`'s (C4) to define; this change writes the finding and leaves the item open.

### D4 — Phase A re-verifies, Phase C forbids a second change

Re-verifying is not optional: the change was written against a base that may have moved, and the loop's first gate
is that the claim holds *today* (`worker.md`, gate 1). What changes is the consequence — a claim that moved is
`needs-diagnosis` naming the path, and a fork the change does not settle is `needs-decision` naming the change's
line, so neither verdict can turn into a fresh plan.

The prompt carries the rule as a prohibition with its reason, in the shipped style: *the change is what a person
authorised; a second one would be work nobody gated*.

### D5 — The reviewer reads the change the item was handed

The review round already reads a change, not the tree it sits in (spec `agent-issue-loop`, "A review round reads the
change, not the tree it sits in"). For a tier-B item the round additionally reads the item's `ITEM_DECISION` and
`ITEM_CHANGE` blocks, so a stale `## Open decisions: none` shows up as a review finding against the recorded
question rather than as a silently wrong archive. This is the mitigation `change-readiness-gate`'s design named and
handed here.

## Open decisions

none

## Risks / Trade-offs

- **A change edited while its item runs is not the change that lands.** The copy is the authority; a peer's edit to
  the source is recorded by the outside-write reading and is not merged. Accepted: the alternative is a live
  reference into the project's tree, which D1 eliminated.
- **The copy makes the item's evidence directory and the worktree diverge if the worker rewrites the change.** The
  worker is told not to create a second change; a rewrite inside the copied directory is visible in the branch's own
  diff, and the reviewer reads the change, which is where it shows. Not prevented mechanically, and stated.
- **A pre-screen that reads bodies.** It already does, for citations (`Backlog.md` §6). The added read is two body
  fields and one directory stat; the readiness check runs once per candidate that names a change, and only that
  candidate.
- **The clean command becomes load-bearing for disk hygiene.** It already is (`spec agent-issue-loop`, "One command
  reports and removes what the loop left"); the change adds one path family, not a mechanism.

## Verification

- `tools/loop-cases.sh` gains the cases the scenarios name (`worktree#`, `prescreen#`, `run#`, `prompt#`, `clean#`);
  `tools/loop-mutations.sh` one record per new refusal; `tools/loop-selftest.sh --from-git <base>` runs them.
- `tools/loop/evidence-check.sh` over this change resolves the pointers, including the `.gitignore:76` and
  `worktree.sh:170` citations.
- No compiled file is touched → no Gradle gate. The structural checks are the loop suite, its mutation list,
  `tools/check-doc-routes.sh` and `openspec validate loop-consumes-a-prepared-change --strict`.
- The end-to-end reading is one fixture item whose body records a change that exists, run through the suite's
  `--dry-run` path, asserting the copy's file list and the package's block.
