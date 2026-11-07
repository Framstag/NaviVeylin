# Design — the change-readiness gate

## Context

The loop's eligibility gate is a table of seven conditions a worker applies to an **item** after it was started
(`tools/loop/prompts/worker.md`, "Phase B — the eligibility gate (strict)"). Four of them turn on artefacts the
change itself would contain: one change follows with no fork (gate 3), a falsifiable WHEN/THEN (gate 4), one
capability delta (gate 5), spec-shaped (gate 7). Applied to a change instead of an item, they are a readiness test —
and today nothing applies them there:

| checker | what it already answers | what it does not |
|---|---|---|
| `tools/check-openspec-hygiene.sh` | three *syntactic* silent failures (bare `- [x]`, unquoted guidance, placeholder Purpose) | any question about content |
| `openspec validate --strict` | structure, scenario presence, length, MODIFIED completeness | whether a scenario is exercised, whether a fork remains |
| `tools/loop/evidence-check.sh` | every cited pointer resolves | whether the change is decidable |
| `tools/loop/measure.sh` | the numbers a change owes, the scenarios counted against their cases | it reports, it does not refuse |

Measured before writing this document, on the tree at 2026-10-30:

- `grep -rln 'Open decisions' openspec/ tools/ guidelines/` → nothing. The declaration has no writer and no reader.
- `grep -c` over `openspec/changes/*/design.md`: 15 files, **8** with a non-empty `## Open Questions`, and every
  entry is a deferral (`deliberate`, `deferrable`, `no spec change`, `not part of this change`).
- The case-id prefixes the shipped specs use are suite names (`run#`, `gate#`, `resolver#`, `duplicates#`), so this
  checker's cases are `readiness#…`.

## Goals / Non-Goals

**Goals:**

- One command that answers, for one change or for every open change, whether an unattended worker could work it
  without deciding anything.
- A verdict shape that another tool can quote: one line, named refusals, exit 0/1.
- Every refusal falsified once by the project's own mutation harness.

**Non-Goals:**

- Judging prose. Not "is this design good", not "does this sentence hide a fork".
- Replacing `openspec validate --strict`, `check-openspec-hygiene.sh` or `evidence-check.sh` — it runs beside them.
- Deciding whether the change *should* be worked, or by whom. Authorisation stays `agent/eligible`, a person's
  write.
- Fixing the changes it refuses. A refusal names the artifact and the line; a person or a session edits it.

## Decisions

### D1 — Judge a declaration, never the meaning of a section

The readiness question is "does this change leave a fork?" and the tree's own contract convention answers it
without reading prose: **a value another reader depends on is declared, never omitted.** `tools/tooling.json`
states it for the profile — *"A key another project does not need is declared empty (`""`, `[]`), never omitted: an
absent key is a half-adapted profile"* — and this change applies the same shape to a change: `## Open decisions`
with `none` is a claim the checker can verify; silence is not.

The alternative — reading `design.md` for options, questions or "or" — was **eliminated by measurement**: the
citation extraction that read prose was wrong about 48 of 49 verdicts and excluded two thirds of an authorised set
(`guidelines/Backlog.md` §6). The same reading applied here would refuse the 8 deferral-carrying designs in flight,
i.e. 53 % of the open set.

A third alternative, requiring an empty `## Open Questions`, was eliminated by the same measurement: every one of
those 8 sections is legitimate, and the sections that do matter are the ones a change's own author writes.

### D2 — The checker reads three artifacts and no others

`design.md` (the declaration), the change's `specs/**/spec.md` (each scenario's `**Case**:` line), and
`proposal.md` (the `surface:` line). No source file, no tracker, no git. That keeps the checker runnable offline, in
a scratch tree, by its own self-test, and it means its runtime is a function of the change's size rather than of the
project's.

The `**Case**:` line is a shipped convention: `openspec/specs/agent-issue-loop/spec.md` carries it on all 120
`run#…` scenarios, including the one case that legitimately names none — *"`Case`: none — the separation is
structural"*. Accepting `none` **with a reason** and refusing `none` alone is what turns an omission into a claim.

### D3 — One line, and the refusals are named on it

```
LOOP-CHANGE-READY: ok
LOOP-CHANGE-READY: refused declaration-missing=design.md open-decision=design.md:41 scenario-without-case=specs/x/spec.md:12
```

One line, so a caller can quote it into a comment, a work package or a label without parsing prose; named refusals,
so a reader knows what to fix. Exit 0 for `ok`, 1 for anything else, 2 for usage. The vocabulary
(`declaration-missing`, `open-decision`, `scenario-without-case`, `surface-missing`, `surface-command-missing`,
`change-unreadable`) is fixed here so C2 (`finding-shape-at-filing`) can quote it without inventing a second
spelling.

### D4 — One refusal, one mutation record

Each of the six refusals gets one record in `tools/check-change-readiness-mutations.json`, in the shape
`tools/mutate.sh` already runs, with the case that must go red. The tree's rule is that a new invariant owes a
falsification step (`guidelines/Build.md` §2a, `revert-check`); a refusal is an invariant.

### D5 — Empty means "no gate", and that is the caller's decision, not this one

The path through which C2 reaches this checker is a `tools/tooling.json` key, and the tree's convention for such a
key is that an empty value is a declaration, not an omission. This change does not add the key: it has no caller, so
the key would be a value nothing reads. C2 adds it, with the rule that an empty path refuses the shape rather than
filing it unchecked.

## Open decisions

none

## Risks / Trade-offs

- **A `none` written without thought is a lie the checker cannot see.** Accepted: the reviewer reads the change
  against the item's recorded decision, and C3 hands the reviewer both. The alternative — inferring the fork from
  prose — is the failure measured above. The mitigation is that a *stale* `none` shows up as a review finding whose
  fix is the declaration, not as a silently wrong archive.
- **Six checks, and a seventh refusal appears in a hurry without a mutation record.** Mitigated by D4 and by the
  gate-visible mutation list: `tools/mutate.sh --mutations tools/check-change-readiness-mutations.json --list` is
  the record, and the suite entry makes it run when the checker is edited.
- **A checker that passes when a change is half-written.** A change whose tasks are unwritten has no scenarios, so
  the scenario rule does not fire; that is `openspec validate`'s job (`no-tasks`), which the gate already runs.
  Recorded here so a reader does not mistake silence for approval.
- **Not used by anything yet.** C1 lands a test with no caller. That is deliberate: the refusal vocabulary must be
  fixed before two other changes quote it, and a checker is the cheapest artifact to get right alone.

## Verification

- `bash tools/check-change-readiness-selftest.sh` — 13 cases (six refusals, seven passes: the declared `none`, a
  deferrable `## Open Questions`, `none` with a reason, a named case, a counted surface, the single verdict line,
  and prose that reads like a fork), offline, in a scratch tree created under the loop's recorded temporary root.
- `bash tools/mutate.sh --mutations tools/check-change-readiness-mutations.json --list` — one record per refusal.
- Run over the tree's own open changes at the end of the apply pass: **19** open changes on 2026-10-30, of which
  **4 pass** (this change and its three siblings) and **15 are refused** for the three artifacts they predate —
  `evidence/readiness-on-open-changes.txt` carries the command and one line per change, and the refusals are
  recorded in the proposal's "Not in this change". They are a real gap, not a false positive: nothing wrote these
  declarations before this change, and C2's filing path is what makes a new change carry them.
- No compiled file is touched, so the Gradle gate is not owed; the gate's own pathspec owes the three suite entries
  (`gate#theDeltaSelectsTheSuitesItTouches`).
