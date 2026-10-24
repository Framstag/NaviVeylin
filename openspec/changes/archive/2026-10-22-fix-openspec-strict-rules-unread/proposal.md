# Proposal — the two validator strict rules get a home an author reads

## Why

**Root cause.** `openspec validate --strict` fails on two artifact rules that no document an author opens
states, so an author learns them from the validator's refusal in the round after the artifact is written:

- a `MODIFIED` block replaces its requirement when the change archives, so every scenario the shipped spec
  still has must be repeated verbatim; a renamed scenario reads as a removal plus an addition. The rule is
  stated only in `openspec/specs/documentation-ownership/spec.md` ("A modified requirement carries the
  scenarios its spec still has"), a capability spec an author of a delta does not open.
- a requirement's body is at most 500 characters — a `WARNING` in the CLI
  (`Requirement text is very long (>500 characters)`, `MAX_REQUIREMENT_TEXT_LENGTH = 500`,
  `dist/core/validation/constants.js:10`) that strict mode counts as a failure. It is stated nowhere in the
  tree: `grep -rn 'REQUIREMENT_TOO_LONG\|500 characters\|MAX_REQUIREMENT_TEXT_LENGTH' AGENTS.md guidelines/
  tools/ openspec/specs/` returned 0 hits before this change.

The entry document had the shape of the gap: `AGENTS.md:233` opens "Three artifact rules are CI-gated" and
lists the three the pipeline gates, and closed there — the two the validator itself gates were absent from
every document, so the author met them as three `--strict` rounds on the #232 change (issue #237).

**Red test** (`evidence/red-on-head.log`, 2026-10-22T11:55:44+02:00): `evidence/probe.sh` reads the entry
document's `OpenSpec Workflow` section and fails while either rule or the command is absent —
`probe: 2 of 2 cases failed`, `tests=2 failures=2` (`evidence/red-on-head.xml`). Both cases fail on HEAD
because the text they assert is absent; neither reads a build, a device or the network.

**Owning capability**: `documentation-ownership` — the capability whose purpose is the always-read entry
document's routing and whose existing requirements already own the artifact-hygiene rules
("A capability spec states what the capability is for", "A modified requirement carries the scenarios its
spec still has"). **Owning section**: `AGENTS.md` "OpenSpec Workflow", the block that already states the
three CI-gated artifact rules; the structural check a docs-only delta owes is `guidelines/Build.md` §2, the
scope block ("No compiled file changed").

**Surface** (measured before the plan was fixed): `grep -rn '500 characters\|MAX_REQUIREMENT_TEXT_LENGTH'
AGENTS.md guidelines/ tools/ openspec/specs/` → 0 hits; `grep -rn 'omits scenario\|repeated verbatim' tools/`
→ 0 cases and 0 fixtures assert either rule; `grep -rl theChangeSetIsValidatedByACommand tools/` → 0, so the
requirement that owns the `MODIFIED` half names no case that a harness implements. One guideline document and
one capability spec are touched, and one block in `AGENTS.md` is extended.

surface: 2 probe cases added, 0 rewritten, 0 fixtures realigned — the two `tools/` greps above return 0, and
the probe is the change's only case (`evidence/surface.log`, re-measured 2026-10-22 after the correction)

## What Changes

- `AGENTS.md` "OpenSpec Workflow" gains two bullets beside the three CI-gated artifact rules — the
  `MODIFIED` block repeats every shipped scenario verbatim, and a requirement's body is at most 500
  characters excluding its scenarios — the line that names `openspec validate <change> --strict` as the check
  run before the artifact is finished, and the clause naming the capability requirement that owns both rules.
- The capability delta `documentation-ownership` states both rules normatively and requires the always-read
  entry document to name it, with one scenario per half and the probe case that exercises each.
- `evidence/probe.sh` is the case of both scenarios: it reads the section, passes on the edited tree
  (`evidence/green-after-fix.log`, 2026-10-22T11:55:48+02:00) and fails on HEAD.

Not breaking: no compiled file, no command, no module and no behaviour changes — a reader who never writes a
change artifact sees no difference.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `documentation-ownership` — one ADDED requirement, "The validator's own strict rules are read before the
  first write", with two scenarios. No existing requirement changes, no scenario of the shipped spec is
  removed, and the requirement is appended rather than merged into a neighbour, so the shipped `MODIFIED`
  semantics this change documents cannot bite the change itself.

## Impact

`AGENTS.md` only (one block in "OpenSpec Workflow"), plus the change's own artifacts and its
`evidence/probe.sh`. No guideline document, no `openspec/config.yaml`, no `tools/` script, no Gradle file, no
source file. Verification is the structural pair `guidelines/Build.md` §2 names for a docs delta — the
documentation check and the feature-list classification gate the loop's `gate.sh` maps these paths to — and
the CLI's own `openspec validate <change> --strict`. No build, no test suite, no device, no emulator.
