# Design — fix-workflow-section-names-strict-rules

## D1 — The root cause, in one mechanism

Both rules are enforced by exactly one reporter — `openspec validate --strict` — and an author runs that
command **after** writing the artifact, so the statement that would have been read before the first write is in
no document: `AGENTS.md`'s workflow section lists the three rules CI fails on and nothing else, and a rule whose
only reporter runs at the end of the work is stated nowhere. That single gap explains all three rounds of #232:
round 1 rebuilt the scenario block, rounds 2 and 3 cut the same requirement text twice, and the cheap workaround
(skip `--strict`, meet it at the archive) moves the failure into the step that already merged the delta.

## D2 — The single fix

State the two rules, and the command that reports them, in the section an author reads before writing the
artifact (`AGENTS.md` §"OpenSpec Workflow"), beside the CI-gated rules already there, and own them by name:

```markdown
Two further artifact rules are the validator's own — a plain `openspec validate` reports both as warnings and
exits 0, and `openspec validate <change> --strict` fails on them, so that command is run before the artifact is
finished rather than at the archive, which is the round that already wrote it:

- a `MODIFIED` requirement replaces the whole block, so it repeats **every** scenario of the shipped requirement
  verbatim — the ones the change does not touch included, under their own titles. A renamed scenario reads as a
  removal plus an addition.
- a requirement's text stays within **500 characters** — the requirement body, not its scenarios. An example
  belongs in a scenario.
```

and state the same two properties as requirements in `documentation-ownership`, which already owns the
scenario rule's validator behaviour, so the entry document's bullets are a summary that names its owner (the
capability's *The always-read document states facts and references rules* and *A rule has one normative home*).

## D3 — Blast radius

- 2 documents edited: `AGENTS.md` (+12 lines), the `documentation-ownership` capability spec (via the delta at
  the archive).
- 1 change directory: proposal, delta spec, design, tasks, `evidence/`.
- Nothing executable: no `tools/`, no `gate.sh`, no `loop.conf` entry, no CI step. The reporter of both rules is
  already `openspec validate --strict`, which is installed and named.
- Two properties the entry document is measured by are unaffected: its route check reads only the
  `## Documentation Map` region, and no requirement asserts a size budget for `AGENTS.md` (only that the
  document holds what a session needs before it reads anything else). `bash tools/check-doc-routes.sh` is the
  structural check and it stays green.

## D4 — Rollback

Revert the `AGENTS.md` block and drop the change directory. Nothing else reads either; no command, gate or
generated document changes behaviour.

## D5 — The alternatives the evidence eliminated

- **State the rules only in the capability spec** (where the scenario rule already lives). Eliminated: that is
  the defect. The spec is read when someone follows the route (`AGENTS.md`'s Documentation Map sends "how the
  guidelines themselves are organized" there); #232's author did not, and the two rounds of validator output are
  the measurement of that. The issue's own bound is "readable before the first write", which only the entry
  document satisfies.
- **Add the rules to the CI hygiene step and skip the entry document.** Eliminated: CI runs on the pull request,
  which is after the artifact exists, and an author does not read a workflow file before writing a
  `tasks.md`. It also answers a different question — whether a merged artifact is consistent — and that check
  already exists as `openspec validate --strict` itself.
- **Ship a new checker that greps `AGENTS.md` for the two rules.** Eliminated: it would assert a document's
  prose, and its red case would be the same absence. The statement is what is missing, not a gate; `Build.md`
  §2's scope rule says a delta that touches no compiled file names the structural check it ran instead, and the
  probe in `evidence/probe.sh` is that check.
- **Shorten the entry document's other bullets to make room.** Eliminated: no requirement bounds its length, so
  nothing forces a trade, and touching the CI-gated bullets would make this change responsible for statements it
  was not asked to change.

## D6 — Why the four validator cases are probes and not promises

The mechanism each rule claims is checkable on this change's own delta, so each is checked instead of asserted:
rename one shipped scenario title in the MODIFIED block and `openspec validate --strict` reports that title as
omitted; lengthen a requirement body past the bound and a plain `openspec validate` reports it as a warning
while `--strict` fails on it. Each probe restores the file from a backup and re-runs the validator, so the tree
is left as it was found and the green line is produced in the same run (`evidence/green.txt`).

## D7 — Round 2: what the review refused, and the one fix each correction is

D2's `AGENTS.md` snippet, D6's "reports it as very long", the ADDED requirement and the fourth scenario said
four things the validator does not do; each correction is a narrowing of one sentence, so no alternative was
eliminated by evidence and the single fix stands:

1. **Reporting versus failing.** `openspec validate <change> --strict` does not own the report — a plain
   `openspec validate` prints both findings and exits 0, and `--strict` only makes them fatal. Measured on the
   merged spec: `Specification 'documentation-ownership' is valid` + two `⚠ [WARNING] … very long` lines, exit
   0 (`evidence/round2-checks.log`). Fixed in `AGENTS.md`, in the requirement's WHEN/THEN, and in the retitled
   *An over-long requirement is a warning until the strict command*, whose case now asserts both halves — a
   plain run exits 0 with the `[WARNING]` line and `--strict` exits 1. The round-1 wording, restored on the tree,
   is what turns that case red (`M2`, `6 passed, 1 failed`).
2. **The universal.** "Each artifact rule that `openspec validate --strict` enforces" is not what the entry
   document states, and the validator enforces more than two. The requirement now says "the two artifact rules
   below", which is what it means and what its cases test.
3. **Bare substrings.** Three read cases asserted less than their THEN carried. Each now asserts every clause,
   with a mutation per clause: `under their own titles` (`M3`), `An example belongs in a scenario` (`M4`), and
   the requirement's name in the change-set case. The clause count is the surface: 1 read case was widened per
   clause removed, 0 cases added, and the three mutations each fail exactly one case.

The probe also had to stop assuming an open change: after the archive the delta is under
`openspec/changes/archive/`, so it now resolves either location and drives a temporary
`openspec/changes/probe-<name>` copy that its exit trap removes. Without that the four validator cases fail
with "no delta spec" on the shipped tree, and the red test would be a probe that cannot run.
