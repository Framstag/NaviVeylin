# A served context is a pointer, not a second copy

## Why

`openspec/config.yaml` carries a `context:` block that the CLI injects verbatim into
`openspec instructions <artifact|apply|archive> --change <name> --json` (`.context`). Nothing reads that block
against any other file, so it is a second written authority for facts the repository already states once — and it
had already drifted: its `Guidelines:` line priced whole-document reads (`Build.md ~29k tokens`,
`MapRendering.md ~23k`) at figures the routing table's own split measurement no longer gives, and it restated the
tech stack, the ABI list, the Android Auto wiring and the distribution tracks that `AGENTS.md` carries in its Tech
Stack table and its Module Structure section.

**Why now:** the owner is replacing the OpenSpec `explore` skill with one of their own. Explore's own instruction
is to read `<root>/openspec/config.yaml` for `context` and `rules`; a custom skill inherits that read only if the
block is worth reading, and a drifting duplicate is not.

**Scope:** repository tooling and documentation only — phone, car and native behaviour are untouched; the change is
additive (a check gains a class) with no runtime effect.

## What Changes

1. **The block becomes a pointer** (`openspec/config.yaml`): three lines that name `AGENTS.md` and the routing
   table's role, instead of the stack/modules/constraints restatement. `rules:` and `operations.*.guidance` stay
   where they are.
2. **`AGENTS.md` absorbs the four facts the block alone carried** — the upstream repository
   (`https://github.com/Framstag/libosmscout`), libosmscout's Android-free property outside its frozen `Android/`
   directory, the `python3` ban for OpenSpec interaction, and the loop's eligibility rule (an item enters the loop
   only when exactly one fix follows from a proven root cause).
3. **The artifact-hygiene check gains a sixth class** (`tools/check-openspec-hygiene.sh`): a `context:` block that
   does not name `AGENTS.md`, or that carries more than three non-empty lines, is reported; an absent block is not
   a finding.
4. **The class is held by a case** (`tools/tests/check-openspec-hygiene-selftest.sh`,
   `artifact-hygiene#aContextBlockThatCopiesTheFactsIsRefused`), and the clean tree now carries the passing pointer.
5. **Three skills' citations follow the rule** (`triage-todo`, `cleanup-todo`, `openspec-open-changes`): each
   pointed at `openspec/config.yaml` for the `python3` ban, which no longer lives there.

## Impact

- `openspec/config.yaml`, `AGENTS.md`
- `tools/check-openspec-hygiene.sh`, `tools/tests/check-openspec-hygiene-selftest.sh`
- `.pi/skills/triage-todo/SKILL.md`, `.pi/skills/cleanup-todo/SKILL.md`, `.pi/skills/openspec-open-changes/SKILL.md`
- no Gradle module, manifest, resource or C++ file; no ABI; no Android surface — 0 compiled files changed

## Capabilities

- **Modified**: `documentation-ownership` — ADDED requirement "The project context has one owner".

## Prior specifications this change touches

`documentation-ownership`, "The change-artifact guidance reads by section"
(`openspec/specs/documentation-ownership/spec.md:72`): its scenario "The served context instruction is
section-scoped" is the one the pointer still has to satisfy, and its scenario "The guidance survives the edit" is
the read-back this change re-runs. Nothing in that requirement is modified.

## Guidelines affected

- `AGENTS.md` — "Documentation Map" (the pointer's target), "Native Integration", "Agent iteration loop", "OpenSpec
  Workflow" (the four absorbed facts and the pointer statement).
- `guidelines/Build.md` §2 (the scope block that decides a delta touching no compiled file runs no Gradle suite) and
  §9 of the same document's hygiene step; `guidelines/BuildEvidence.md` §4f for the evidence rules this change's
  records follow.

## Rollback

Revert the five files and re-run `tools/tests/check-openspec-hygiene-selftest.sh`; the config block returns as it
was and class 6 leaves with the script. No data, schema or on-disk format changes, so nothing migrates.
