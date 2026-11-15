# Proposal

## Why

The change `package-structure-as-five-kinds` landed its first slice on 2026-11-14: the `:architecture`
module, the declaration `architecture/structure.tsv`, the reader, and the three rules the tree now
enforces — one row per package, a row the tree lacks, and the default deny on an undeclared package.
Its archive holds that slice, with the rationale and the design of the whole plan.

What did not land is the rest of that plan, and it is the part that makes the structure enforceable
rather than merely declared: the rules that hold between the five kinds, the check's place in the
build and in the gate, the documents that own the structure, and the pilot migration that proves the
rules on a real feature. The rationale for all of it — the feature cake the plan exists to end, and
the 2026-11-09 baseline it was measured against — is in the archived change's `proposal.md` and
`design.md`; the numbers there are that proposal's own baseline, and this change reproduces none of
them.

## What Changes

- The kind rules land: `concept` imports nothing of ours and no Android type, `port` holds abstraction
  only, a `feature/<name>` imports no other feature and no Android type, a `platform/<name>` mirrors its
  feature and no sibling platform, and `shell` is the only kind that may host several features.
- The check grows a ratchet: a named baseline entry exempts a file that does not yet satisfy a rule, an
  entry whose file no longer violates the rule fails, and no in-code suppression is honoured.
- The check reads every source set of the three modules, names every violation with its file, its
  import and the rule it contradicts, reports what a green run verified, and fails closed on a missing,
  unparsable or empty declaration.
- The check becomes a prerequisite of every module's unit-test task and a suite the project's gate runs,
  so a violating tree cannot pass a focused test run and cannot reach the main branch.
- The guideline that owns the structure lands: `guidelines/Structure.md` with its rationale companion,
  the `AGENTS.md` routes, and the rewrite of `guidelines/Design.md` §1 that stops restating the package
  set in prose.
- The pilot migrates one feature end to end — `favourites`, with the two port interfaces it needs — so
  the rules are proven on a real slice and the baseline loses its first entries.

## Capabilities

### New Capabilities

- `architecture-test-suite`: the test-time enforcement of those rules — where the suite runs, which
  source sets it reads, the ratchet baseline and its shrink-only property, and the rule that a
  violation is reported with the file, the import and the declaration row it contradicts.

### Modified Capabilities

- `package-structure`: the requirements this change adds to the capability the archived slice shipped —
  the kind rules, and the declaration as the single machine-readable description. The requirements the
  archived slice shipped (one row per package, the row the tree lacks, the default deny) stay as they
  are.

## Impact

**Production code.** The pilot migration moves eight files and rewrites their imports, and two port
interfaces are added for it. Every other step of this change adds tests, documents and build wiring.

**Build.** `:architecture:test` becomes a prerequisite of the unit-test tasks of `:app`, `:auto` and
`:core`, and a suite of the project's gate. A tree that violates a rule fails the build instead of
passing it.

**Guidelines.** `guidelines/Design.md` §1, `AGENTS.md`, `guidelines/BuildEvidence.md` §4b,
`guidelines/Metrics.md` and `guidelines/UI.md` §8d.

## Verification

- Each rule is falsified by one mutation that must fail it and be restored — the discipline the
  archived change's falsification 3.4 already followed.
- A green run reports what it verified: the declaration rows read, the files classified, the
  exemptions outstanding and the rules evaluated.
- The pilot's outcome is recorded with the baseline's drop.

## Open decisions

none

surface: 5 scenarios across the 2 delta files of this slice — `grep -c '^#### Scenario:' openspec/changes/archive/2026-11-15-package-structure-rules-and-suite/specs/*/spec.md` (4 in `package-structure`, 1 in `architecture-test-suite`). The plan's other 25 scenarios belong to the change's open tasks and are not recorded here: a delta names a case the tree holds, and those cases do not exist yet.

range: first slice only — tasks 1.6, 2.1, 3.2 and 3.3 of the change; the change's other tasks remain open.
