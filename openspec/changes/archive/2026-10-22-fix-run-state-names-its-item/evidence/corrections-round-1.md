# The corrections round 1 asked for

Round 1 refused this change with three blocking findings and `LOOP-CORRECTIONS-ONLY: yes`. The item's owner
answered the escalation with **option 1**: narrow the phase clause to "the phase the run last recorded" and
repoint the dangling evidence pointer — a wording pass, no new case. That is what this round did, by hand, and
the change was merged by hand rather than adopted, because a branch the reviewer refused is the owner's
(`tools/loop/worktree.sh` `adopt-item`, whose refusal names exactly this path).

## What changed

1. `openspec/specs/agent-issue-loop/spec.md` (the merged requirement, line 1866) and the delta's own copy at
   `specs/agent-issue-loop/spec.md` — "the phase the run is in" became **"the phase the run last recorded"**.
   The blocking finding was that `.current.phase` is the constant `"A"` the fix itself writes: `run.sh` calls
   `loop_state step` once, at the worker, so B/C/D/E/F are never written. The contract now claims only what the
   code does.
2. `guidelines/Backlog.md` (the sentence about what a state names) — same narrowing, "the phase it is in"
   became **"the phase it last recorded"**.
3. `openspec/changes/archive/2026-10-22-fix-run-state-names-its-item/tasks.md:12` — the pointer
   `(evidence/focused-cases-green.log)`, which resolves to no file, became
   `(evidence/focused/stateNamesTheItemInProgress-green.log)`, which the archive carries.
4. `tools/loop-cases.sh` — the new case's assertion message now says "the phase the run last recorded", and the
   case carries the `# files:` declaration `driver#everyCaseDeclaresTheFilesItCanReach` owes it. The branch's
   base (522d69a7, 2026-10-22) predates that rule, which landed on `main` afterwards; without the declaration
   the ported case fails that driver case.

Not changed, deliberately: the case's phase assertion itself, and the reviewer's other findings, which were
NOTEs and not blocking. `.current.change` is still unasserted (NOTE), and the item-directory `measure.log` is
still the shorter pre-`surface:` capture (NOTE, machine-local).

## The gate, for this round

From the change root, `bash tools/loop/gate.sh --base f5f2a2a7`, in a clean worktree at this change's own
commit — 326 tests, **1 failure**, and that one is attributed below, not this change's. Its suites ran
`tools/loop-selftest.sh --from-git f5f2a2a7` with tests=203 failures=1, `bash tools/loop-mutations.sh --check`
with tests=123 failures=0, and `tools/check-doc-routes.sh` and the classification check green.

`evidence/gate-corrections.log` carries the whole run, `LOOP-GATE` line included, and
`evidence/gate-tallies-corrections/` the four JUnit tallies it derived its numbers from.

## The one red, attributed

`run#aWriterTheRunCanPlaceBlocksTheItem` fails on `main`'s tip **without** this change: three runs in a clean
detached worktree at `f5f2a2a7` fail identically (`evidence/attributed-red-aWriterTheRunCanPlaceBlocksTheItem.log`),
with `expected 'blocked', got 'done'`. The case was added by `f5f2a2a7` (#286), whose own green evidence was
taken in a *dirty* worktree; on a clean checkout it is red. It also flips between runs when the engine's own
tree is dirty, so the case is unreliable rather than merely red, and that is a finding about #286's case, not
about this change. Filed separately.
