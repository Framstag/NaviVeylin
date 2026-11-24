# A touched baselined file is migrated

## Why

The structure ratchet is green and it stays green, so it never asks for the work it is holding. Its 45 exemptions
name two changes nobody filed (`package-structure-concept-migration`, `package-structure-feature-migration`), and
the check cannot report that: it fires on a *new* violation and on a *stale* entry, both of which are accounting.
An ordinary change that edits a file the baseline names is already inside the move that file owes, and nothing
tells it so — so the debt outlives the reader who knew why it was taken. The queue's owner is the epic #1049; this
change is the rule that makes the owner's absence visible to the change that could clear an entry for free.

## What Changes

- `guidelines/Structure.md` §5 gains one rule: a change that edits a baselined file migrates it in the same change,
  or states in its proposal why the file stays and which change still owns the entry.
- `guidelines/Structure.why.md` §5 records why the rule is judged by review and not by a gate.
- `openspec/specs/architecture-test-suite/spec.md` gains the requirement, so the spec and the guideline say the
  same thing.
- No check changes. No rule in `LayerRules` or `DeclarationRules` changes. No file moves. The baseline's 45 entries
  stand as they are: this change makes them the subject of a review question, not of a new refusal.

## Capabilities

### New Capabilities

none — the rule belongs to an existing capability.

### Modified Capabilities

none — the requirement is added to `architecture-test-suite`, which is an addition and not a change to a shipped
requirement.

## Impact

- Docs only: `guidelines/Structure.md`, `guidelines/Structure.why.md`, and one delta spec under
  `openspec/changes/a-touched-baselined-file-is-migrated/specs/architecture-test-suite/spec.md`.
- No compiled file changes, so no build and no suite: the verification is the structural checks, named in
  `tasks.md`.
- The rule costs a reviewer one `comm -12` over the diff and the baseline. It costs a change nothing when the
  change touches no baselined file, which is most of them.

surface: 1 delta file carrying 1 requirement and 2 scenarios —
`grep -c '^#### Scenario:' openspec/changes/a-touched-baselined-file-is-migrated/specs/architecture-test-suite/spec.md`
