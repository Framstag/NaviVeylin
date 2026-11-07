# Design — fix-retired-loop-skill-still-shipped

## Root cause

`fix-loop` stopped being a session's entry point on 2026-10-22, and nothing was migrated out of it: the worktree
seed still copied the directory (`tools/loop/worktree.sh:96` on `d6deb7e4`), the worker prompt still ran the copy of
its script (`tools/loop/prompts/worker.md:170`), and three comments plus `AGENTS.md` and `guidelines/Backlog.md`
still pointed readers at it. A versioned directory that is copied into the worktree reads as harmless — the file is
already in the checkout — and that is exactly why the copies outlived the entry point.

## The single fix

Move the one thing the skill still owned, delete the skill, and repoint every reference at the home the item names:
the rules at the spec `agent-issue-loop` and `tools/loop/prompts/worker.md`, the measurements at
`guidelines/Backlog.md`, section 7, the evidence check at `tools/loop/evidence-check.sh`, and the gate pair that runs its
self-test at `tools/loop/loop.conf`.

## The one fork, and the alternative the evidence eliminated

The item leaves one choice open: how the moved check stops naming `app/src`.

- **Chosen — the scan roots are the project's configuration.** `LOOP_EVIDENCE_SCAN_PATHS` and
  `LOOP_EVIDENCE_XML_DIR` join `loop.conf`, `tools/check-portability.sh` declares them like the other project paths,
  and the tool reads them (never sources them) with flags for a foreign tree.
- **Eliminated — scan the change's own delta.** It cannot answer the question the check asks. C4 looks for a
  leftover `REVERT-CHECK MUTATION` marker in the source a falsification edited; the delta of an archived change is
  gone by the time phase F re-runs the check on it, and a marker left in a file the change did not touch (a
  hand-run falsification between two items) would be invisible. Scoping a proof to what the change already claims
  is the shape this project refuses — the proof then agrees with the claim by construction.

The configuration keeps the check's meaning *and* makes the portability rule provable: once the value is declared,
`tools/check-portability.sh` reports any engine that writes it — which it did, on `tools/loop/gate.sh`'s comment,
before that comment was reworded.

## Blast radius

Twelve files name the skill on the base (`evidence/surface-head.log`); nine are edited, three are the deleted
skill. Two more are consequences: the portability check's value set (`tools/check-portability.sh`) and the
resource-tree comment it now reports (`tools/loop/gate.sh`). The loop's suite gains one case and rewrites one
assertion; `tools/portability-cases.sh` loses scaffolding for a directory nothing copies any more. No compiled file
changes, so the gate is the shell suites the pathspec owes and no Gradle run.

## Rollback

Revert the branch. Nothing outside the tree was written: a worktree seeded by an earlier run keeps its copy, which
is harmless (`tools/`, not `.pi/skills/`, is where the prompt now points), and the deleted measurements are in the
base commit.

## What a reader loses, and why that is recorded rather than silent

- The measured costs, the floor's derivation, the memory measurement, the first run's tallies and the state/kept-tree
  measurements are **moved** into `guidelines/Backlog.md`, section 7 verbatim in number, so no measurement is dropped.
- The eleven field notes and the "what changed when the loop became a script" table are **dropped**: the notes are
  pointers to rules the spec and the worker prompt state, and the table is provenance whose record the item names
  (`openspec/changes/archive/2026-10-17-migrate-todo-to-github-issues/report.md`). Neither carries a measurement
  that is not also in section 7 now.
