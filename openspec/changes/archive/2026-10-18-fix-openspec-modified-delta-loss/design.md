# Design — fix-openspec-modified-delta-loss

## Context

Motivation and the evidence are in `proposal.md`. The state this design works within, read from the tree
2026-10-18:

- Both failing blocks were compared against their shipped specs by extraction and `diff` (the check D4
  adopts). Before the repair the car block lost 1 sentence and 2 scenarios; the engine block lost 2 sentences
  (the atomic-publication and injected-time-source clauses) and 3 scenarios. Both losses sit in text or
  scenarios the *shipped* spec gained after 2026-09-27 — `car-host-fault-isolation` gained its two frame/tick
  scenarios with `fix-car-render-loop-restart`, `navigation-engine` its three with the phone-side publication
  fixes — so the change's authoring-time `--strict` run (its 6.4) could not see them.
- `openspec validate <change> --type change` compares a MODIFIED block's scenarios against the shipped spec;
  `openspec validate --changes` runs it over every open change. Neither is invoked by CI: the only
  OpenSpec-artifact step, `.github/workflows/build.yml` "Check OpenSpec artifact hygiene", is a shell/awk step
  over `tasks.md` markers and `openspec/config.yaml` quoting, and the workflow installs no OpenSpec CLI (no
  `setup-node`, no `npm` step). On a workstation the CLI is a mise-installed npm package
  (`command -v openspec` → `~/.local/share/mise/installs/npm-fission-ai-openspec/latest/bin/openspec`).
- The change-artifact rules that already have a home live in `openspec/specs/documentation-ownership/spec.md`
  ("The change-artifact guidance reads by section", "A rule has one normative home") and in the entry
  document's `OpenSpec Workflow` section, which states the two CI-gated artifact rules. No shipped requirement
  states the MODIFIED-block rule; it exists only in the CLI's error text.
- `guidelines/Design.md` §4 (engine scope: fault handler, native boundary confined for any throwable) and §8
  (the fault-handler family now including the process-scoped engine; the per-loop-iteration exception) already
  carry the rules the repaired blocks state — checked by reading both sections and grepping
  `fault handler|engineFaultHandler`, so this change edits no guideline.

## Goals / Non-Goals

**Goals**

- The two MODIFIED blocks keep every scenario and every sentence their shipped specs have, so archiving
  `fix-navigation-engine-fault-isolation` drops nothing.
- The rule that was broken has one normative home, so the next writer of a change whose shipped spec moved
  under it knows the block is a copy of the whole requirement.
- The repair is verifiable by a command anyone can re-run, and the guard that found the defect is named where
  it runs.

**Non-Goals**

- Archiving `fix-navigation-engine-fault-isolation` or finishing its open task 6.3 (its author's step).
- Touching the two shipped specs: they are the source of the five scenarios, and after the repair the delta is
  a strict superset, so a spec edit would be a second, unrelated change.
- Regenerating the feature catalogue or the feature index: the classification is keyed per shipped spec id
  (`tools/feature-list/specs.json` already names `documentation-ownership`), so an added requirement changes no
  classification.
- New tooling, a new CI dependency or a new guard (see D3).

## Decisions

### D1 — Complete both MODIFIED blocks from their shipped requirement; do not narrow them

Each block becomes the shipped requirement's description plus its scenarios, followed by the sentences and
scenarios the change itself adds. The repaired blocks are strict supersets; `diff` against the shipped
requirement names additions only.

- *Alternatives the evidence eliminated*:
  (a) **Narrow the blocks to the requirements the change really modifies** — impossible: the change modifies
  *both* requirements (car: the confinement scope now includes the process-scoped engine; engine: the scope
  gains a fault handler and a throwable-wide native boundary), so a partial copy is the defect itself, not the
  boundary of the change.
  (b) **Drop the MODIFIED blocks and express the change as ADDED requirements** — impossible: the additions edit
  the existing requirements' text (the engine sentence is a clause of the confinement rule, not a second rule),
  and an ADDED twin would give one rule two homes, contradicting this capability's "A rule has one normative
  home".
  (c) **Restore the scenarios only** — impossible: it leaves the same defect one level up, with the shipped
  description's atomic-publication and injected-time-source sentences still dropped by the block.
  (d) **Leave the change and repair it at archive time** — impossible: `openspec validate` and the archive step
  both refuse it (`exit=1`, `evidence/red-on-head.log`), so the archive never runs and nothing is repaired.
- *Why this one*: it is the change the author wrote, with the shipped requirement preserved; it needs no
  judgement about intent, and the resulting block can be checked mechanically (D4).

### D2 — Home the rule in `documentation-ownership` as one added requirement

The delta adds "A modified requirement carries the scenarios its spec still has" to
`openspec/specs/documentation-ownership/spec.md`, with two scenarios, each naming the command that exercises
it (`openspec validate --changes`, `openspec validate <change> --type change`).

- *Alternatives the evidence eliminated*:
  `spec-feature-index` — its index is keyed per shipped spec id and compares requirement text, so it *reports*
  drift after the fact but owns no authoring rule; the defect is a rule about writing a block, not about
  indexing one. `agent-issue-loop` — its "The run verifies rather than trusts" governs a run's own artifacts,
  and the loop's apply pass is where this defect was found, but the rule binds every change, archived by a run
  or by hand. A **new capability** (`change-artifact-integrity`) — one requirement does not justify a new
  shipped spec id, and a new id needs a classification entry (`tools/feature-list/specs.json`) for a capability
  the catalogue cannot claim.
- *Why this one*: `documentation-ownership` is the only shipped capability that already owns a change-artifact
  requirement ("The change-artifact guidance reads by section") and it owns where a rule's normative home is
  ("A rule has one normative home"), so a rule about a change artifact belongs next to them. Adding it does not
  modify any shipped requirement, which keeps this fix-* delta to the one requirement the defect contradicts.

### D3 — No new mechanical guard; the guard is the repair plus `openspec validate --changes`

The change adds no checker. The guard that found the defect stays the guard, named where it runs: the change's
own validate task (task 3.1 here) and the archive step's delta-spec assessment
(`.pi/skills/openspec-archive-change`, step 4). `.github/workflows/build.yml`'s "Check OpenSpec artifact
hygiene" is the only OpenSpec-artifact CI step; it does not invoke the CLI.

- *Alternatives the evidence eliminated*:
  (a) **Extend `tools/declared-cases.sh`** — it selects test classes from a change's `tasks.md` declarations and
  the diff; its input is not a delta at all, and its own selftest (`bash tools/declared-cases-selftest.sh`,
  12/0) stays green through this defect, so it cannot see the class.
  (b) **Add `openspec validate --changes` to the CI hygiene step** — the runner installs no OpenSpec CLI
  (`grep -n 'setup-node\|npm ' .github/workflows/build.yml` → nothing), so the step would fail with "command
  not found" instead of checking anything; making CI run it is a CI-dependency decision (which CLI, which
  version, which pin) that this defect does not carry.
  (c) **Write a new shell checker for the MODIFIED-block rule** — no runner under `tools/` has that home today,
  so a new tool would need its own cases, fixtures and a CI step: more surface than the defect, and a second
  implementation of a rule the CLI already implements.
- *Why this one*: the project's rule is to add a mechanical guard only where one already has a cheap home. The
  command that found the defect runs offline in one second, is run at the two points the workflow already
  touches (validate the change, assess deltas before archiving), and is what this change's tasks record as the
  verification. What it did not cover — drift that arrives from a sibling change *after* the authoring-time
  validate — is now covered by naming the command in the requirement (D2), not by new code.

### D4 — Verify the repair by comparison against the shipped requirement, not by prose

The check is an extraction of the block and the shipped requirement and a `diff` that must name additions only
(documented in task 2.2, output in `evidence/green.log`). `openspec validate` compares scenarios; the `diff`
also catches a dropped description sentence, which is how the two lost clauses surfaced in this investigation.

## Risks / Trade-offs

- *A future sibling change adds a scenario to a shipped spec after a change's block was written* → the same
  drift can recur; the requirement names the command, and the archive step re-validates the block, so the
  change cannot be archived with the drift (the failure this defect produced).
- *The added requirement is process, not behaviour* → it constrains change artifacts only; it is testable by a
  command and states no user-visible behaviour, which is what makes it cheap to hold.
- *Repairing another change's files from this one* → deliberate and narrow: two block completions in
  `openspec/changes/fix-navigation-engine-fault-isolation/`, no other file of that change touched, and that
  change stays unarchived for its own author to finish.

## Migration Plan

1. Repair the two blocks; run both validate commands green; record the comparison and the tallies in
   `evidence/green.log`.
2. Archive this change: sync the added requirement into `openspec/specs/documentation-ownership/spec.md`, then
   move the change to `openspec/changes/archive/<date>-fix-openspec-modified-delta-loss/`.
3. Rollback: revert this change's commit. The repair and the requirement are one diff; the two blocks return to
   the partial state that `openspec validate --changes` refuses, and the shipped spec loses one requirement.
4. No data, settings, manifest or Gradle effect; no release step. `fix-navigation-engine-fault-isolation`
   becomes archivable and is archived by its own author.

## Open Questions

None that change this design. The five scenario names the vanished blocks drop are the whole subject; the other
unarchived changes have been validated separately and are clean (recorded in `evidence/green.log`), and they are
not touched.
