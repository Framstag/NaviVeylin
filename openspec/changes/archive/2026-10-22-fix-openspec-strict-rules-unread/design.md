# Design

## Context

Issue #237 records the #232 change spending three `openspec validate --strict` rounds on two mechanical
rules: round 1 on `MODIFIED "Version control belongs to the run's script, not to the agent" omits scenario(s)
the current spec still has … The modified block has 10 scenarios; the current spec has 9`, rounds 2 and 3 on
`Requirement text is very long (>500 characters)` until the body was cut from ≈600 to ≈480 characters.

Verified in the tree before this change:

- `AGENTS.md:233` "Three artifact rules are CI-gated …" lists exactly the three rules
  `.github/workflows/build.yml`'s "Check OpenSpec artifact hygiene" step fails on; neither strict rule is
  mentioned in `AGENTS.md` or in any `guidelines/*.md` (`grep -rn '500 characters\|
  MAX_REQUIREMENT_TEXT_LENGTH' AGENTS.md guidelines/ tools/ openspec/specs/` → 0 hits).
- The `MODIFIED` half is stated once, in `openspec/specs/documentation-ownership/spec.md`, "A modified
  requirement carries the scenarios its spec still has" — a capability spec, which is not a document a delta's
  author reads.
- The CLI's severities differ, which is why one rule needs `--strict` and the other does not:
  `MODIFIED … omits scenario(s)` is an `ERROR` (`dist/core/validation/validator.js:677`), the 500-character
  bound is a `WARNING` (same file: `:274` for an `ADDED` block, `:737` for a shipped spec), and
  `createReport` treats a `WARNING` as invalid only when `strictMode` is set (`:883`).

## Decisions

### D1 — the rules live in the capability requirement, and `AGENTS.md` summarises them and names their owner

Chosen because it is the only shape the capability's own requirements allow. "The always-read document states
facts and references rules" requires the entry document to *reference* the document that owns a normative
rule, allows a one-sentence summary, and requires that summary to name the owning section; "A rule has one
normative home" forbids stating the rule twice. So the normative statement is the requirement this change
adds (`documentation-ownership`, "The validator's own strict rules are read before the first write"), and the
`AGENTS.md` block is one sentence per rule plus the clause naming that requirement and its document — which is
also where the three sibling artifact bullets already end. The first version of this change stated the rules in
`AGENTS.md` and named no owner, which the capability's own requirements refuse (correction C2 below).

Alternatives the evidence eliminated:

1. **`guidelines/Build.md` §2 with a one-sentence route in `AGENTS.md`.** Eliminated: §2 is the file a change
   reads while verifying, not the file where an artifact is authored, the three sibling artifact rules already
   live in `AGENTS.md`, and the capability's own requirements place an artifact rule's normative statement in
   the spec that owns the capability, not in a guideline. Nothing else in `guidelines/` states an
   artifact-authoring rule, so there is no section to give the rule to.
2. **`openspec/config.yaml` operation guidance.** Eliminated: the CLI loads guidance only for `apply` and
   `archive` (the file's own comment, "openspec 1.10.0 loads guidance only for 'apply' and 'archive'
   operations"), so an author writing `proposal.md`, `specs/`, `design.md` and `tasks.md` never sees it.
3. **Leave it to the CLI's diagnostics** (the status quo). Eliminated: the diagnostics are read in the round
   after the artifact is written — that is the measured cost this item removes — and they live in a vendored
   install (`~/.local/share/mise/.../openspec`), not in this tree.
4. **A new prose check (`tools/check-*.sh`) wired into CI and the loop's gate.** Eliminated: the project's doc
   gates resolve structural facts (section references, skill paths, archive placeholders); a check asserting
   a sentence of prose would need its own fixture suite and a `guidelines/Build.md` §2 entry, and the sibling
   change `2026-10-18-fix-openspec-modified-delta-loss` eliminated the same option for the same reason (its
   design D3: "a new checker has no cheap home", and CI installs no OpenSpec CLI). This change carries the
   probe instead, which fails the moment the sentence is deleted.

### D2 — the case is a probe over the strict-rules passage, and it names the absent statement

No harness reads `AGENTS.md`'s prose: `tools/check-doc-routes.sh` reads the routing table's references, and
the routing table is untouched by this change. `evidence/probe.sh` therefore reads the `OpenSpec Workflow`
section's strict-rules passage — the unit the requirement names, not the whole file — and prints `FAIL <case>`
naming the absent statement, so a red run says which half was dropped. It reads the passage rather than the
section because the section's CI-gated bullets already name a spec path: a needle satisfied by a sibling
bullet would not show that *these* rules name their owner (correction C1 below). It reads text only: no build,
no device, no network, mirroring `tools/check-doc-routes.sh`'s contract.

### D3 — the delta is ADDED, never MODIFIED

The change documents the rule that a `MODIFIED` block must repeat every shipped scenario, so writing its own
delta as a `MODIFIED` block would put the change in the position of the defect it describes. The delta carries
one `## ADDED Requirements` block appended to `documentation-ownership`; `openspec validate <change> --strict`
then has no `MODIFIED` block to complete, and the archive appends the requirement instead of replacing one.

### D4 — the document states the bound's scope, because a broader sentence would be wrong

The 500-character limit is read on an `ADDED` block and on a shipped spec, and the validator deliberately
leaves `MODIFIED` alone — "its text is the existing requirement, which the specs instruction says to keep
whole" (`dist/core/validation/validator.js:267-277`). A bullet saying "a requirement is at most 500
characters" would tell an author to shorten a `MODIFIED` body, i.e. to do the one thing the first rule
forbids. The bullet therefore says *the body, not its scenarios*, and names `ADDED` and the shipped spec as
the places it is read, with `MODIFIED` exempt.

## Corrections after review round 1

The first review refused two blockings; both are corrections to what this change already claimed, not new
scope.

### C1 — the case asserts both conjuncts of the command scenario

The scenario's `THEN` names two invocations and the case asserted one: deleting `— or `openspec validate
--changes --strict` over the open set` left `theEntryDocumentNamesTheStrictValidationCommand` green. The case
now asserts both strings, and the second half is demonstrably load-bearing — `evidence/revert-check.sh` in its
`changes` mutation removes that clause, the case fails, and the restore is byte-identical
(`evidence/revert-check.log`). The case also reads the section's strict-rules passage rather than
the whole section, because the sibling CI-gated bullet already names a spec path and would satisfy a
section-wide needle.

### C2 — the block names the requirement that owns both rules

The first version stated both rules in `AGENTS.md` and named no owner, which contradicts two shipped
requirements of the capability this delta extends: "The always-read document states facts and references
rules" (a summary names the owning section) and "A rule has one normative home". The requirement's body now
states both rules normatively and requires the entry document to name it; the `AGENTS.md` block is one
sentence per rule, plus the clause naming `openspec/specs/documentation-ownership/spec.md` and "The validator's
own strict rules are read before the first write". The probe asserts that clause, and removing it is
`evidence/red-on-correction.log` (`1 of 2 cases failed`) and the `owning` mutation of
`evidence/revert-check.sh`.

## Blast radius

One block in `AGENTS.md` "OpenSpec Workflow": three CI-gated bullets untouched, then two rule sentences, the
command line and the clause naming the owning requirement. Nothing reads that block programmatically except
the routing check (which reads the `## Documentation Map` region only) and this change's probe.

## Rollback

Delete the two rule sentences, the command line and the owning clause; the probe's two cases fail again, which
is the red state recorded in `evidence/red-on-head.log` and, for the owning clause alone, in
`evidence/red-on-correction.log`. No state, schema or command depends on the text.

## Verification

- `evidence/probe.sh` — 2 of 2 cases fail on HEAD (`evidence/red-on-head.log`, `2026-10-22T11:55:44+02:00`),
  0 of 2 after the edit (`evidence/green-after-fix.log`, `2026-10-22T11:55:48+02:00`).
- Correction round: the owning clause absent → `1 of 2 cases failed` (`evidence/red-on-correction.log`,
  `2026-10-22T12:18:59+02:00`); present → `0 of 2` (`evidence/green-after-correction.log`,
  `2026-10-22T12:19:01+02:00`), both with the current probe.
- Revert-check (`evidence/revert-check.sh`, three mutations, each restored byte-identically): the owning
  clause, the `--changes` form, and the whole passage — `1 of 2`, `1 of 2`, `2 of 2` cases failed
  (`evidence/revert-check.log`, `2026-10-22T12:19:01+02:00`).
- `openspec validate fix-openspec-strict-rules-unread --strict` → valid, and
  `openspec validate documentation-ownership --type spec` → valid with the two pre-existing long-requirement
  warnings only (`evidence/openspec-validate.log`); the requirement's own body measures 473 characters.
- `tools/check-doc-routes.sh` and `bash tools/gen-feature-list.sh --check-classification` — the structural
  pair the loop's `gate.sh` maps `AGENTS.md` and `openspec/*` to (`guidelines/Build.md` §2, scope block) —
  green (`evidence/structural-checks.log`).
- No Gradle target, no device, no emulator: the change compiles nothing.

## Risks

- **The sentence is deleted later and nothing fails.** Accepted and bounded: the probe is versioned with the
  archived change and fails on deletion (the revert-check demonstrates it), but it is not wired into CI. D1's
  alternative 4 records why a wired prose check was not bought here.
- **The 500-character figure drifts from the CLI's constant.** The bullet names the CLI's own message text
  (`Requirement text is very long (>500 characters)`), so a reader who sees the new wording in the validator
  finds the bullet's phrase by search rather than by trusting a bare number.
