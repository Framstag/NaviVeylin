# Proposal

## Why

`AGENTS.md` is injected into every session — 479 lines, 38.8 KB, roughly **10.8k tokens** — and about 60 %
of it is a second copy of rules that already live in a guideline or a spec. Measured 2026-10-07, with the
owner of each block:

```
section                                  bytes   already owned by
### Android Auto / Android Automotive OS  7478   guidelines/Design.md §8 (its MUST list reads near
                                                 1:1 with this section, same eight rules, same order)
                                                 + specs car-host-fault-isolation,
                                                 auto/screen-observation
## Agent iteration loop (measure first)    7113   guidelines/Build.md §2/§4/§6 + TODO.md §40
                                                 + the build-app/run-tests/revert-check skills
### Release versioning                     2261   guidelines/Build.md §5
### vcpkg usage pattern (CI)               1838   no owner yet — the only homeless block
### JNI stub for unit tests                1622   guidelines/Build.md §6 (scoped by
                                                 dedupe-test-rule-ownership)
## Common Patterns                         1240   guidelines/Design.md §2
### License compliance                     1178   guidelines/Build.md §9
## Build & Test (commands)                  500   guidelines/Build.md §3
### Code Style                              120   gradle.properties + the Kotlin style convention
                                        -------
                                         23450  of 38817 bytes
```

The cost is not only size. The copies that live in the always-read file are the ones an agent quotes as
current, which is exactly where the two defects the sibling change found were sitting: the stale
"the same 215 classes (~3 minutes per gate)" and the four-times-repeated Robolectric classloader rule. Both
were *correct in the owner* and wrong in the copy.

The file's own header already promises what this change restores: "project facts, build commands, logging,
stylesheet mechanics". Rules with rationale and history are not facts; they belong where their measurement
and their change history live.

Why now: `scope-guideline-reads` (captured, not yet applied) gives the repository a topic→section routing
table, so a reader who is no longer given the full rule text in `AGENTS.md` can find the owning section
without reading a document. This change removes the second copies; that change is what makes the removal
safe to do.

## What Changes

- **`AGENTS.md` keeps facts, and references rules.** Each block in the table above is reduced to the fact
  it uniquely owns plus a pointer to the owning section:
  - Android Auto: the hard invariants survive as one line each — one surface owner per session, nothing
    native on a host callback, no fault escaping the host path — with pointers to `guidelines/Design.md`
    §8 for the MUST list, the two specs for the contracts, and `guidelines/Build.md` §10 for the
    diagnosis recipe and diagnostics tags.
  - Agent iteration loop: the four rules stay in a sentence each (measure before changing code,
    iterate with focused suites, batch independent questions, one builder per tree) with pointers to
    `guidelines/Build.md` §2/§4/§6 and the skills that execute them.
  - Release versioning, licence compliance and the command list: the `Build.md` §5/§9/§3 content goes, the
    non-obvious fact stays (the version state file is machine-local and gitignored; release fails fast
    without it).
  - Common Patterns: the how-to-sections move under `guidelines/Design.md` §2, which already owns stack and
    UI patterns.
  - JNI stub: keeps the stub facts; the normative classloader rule is scoped in `Build.md` §6 by
    `dedupe-test-rule-ownership`.
- **`guidelines/Build.md` gains a native-build section** so the vcpkg, triplet, overlay-port and ABI facts
  have an owner. They are build facts, not test rules, so they do not go into §6.
- **No rule is deleted.** Every removed block names the section that already states it; a rule that turns
  out to have no owner is filed rather than dropped.
- **Target:** `AGENTS.md` 38.8 KB → roughly 20-22 KB (about 6k tokens per session), with every rule still
  stated exactly once, in its owner.
- **Not changing:** any guideline body other than the one new `Build.md` section, any spec, any source,
  test, Gradle file or workflow.

**Classification:** additive in behaviour (documents only), but it is a rewrite of `AGENTS.md`'s body.
**Rollback:** restore the removed blocks from git; the owning documents were not edited, so a rollback
restores a duplicated state, never a lost rule.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `documentation-ownership` (introduced by `scope-guideline-reads`; that change owns routing, this one adds
  ownership): three requirements.
  1. The always-read entry document states **facts** — what exists, where it lives, how to run it — and
     references normative rules instead of stating them.
  2. A rule has **one normative home**. A document that mentions a rule refers to the owning section;
     when a duplicate is found, the owner survives and the copy becomes a reference.
  3. A rule removed from the entry document is **relocated, not rewritten**: the owning document already
     states it, and the removal is verified by finding the statement there.

### Relationship to the sibling capability deltas

`dedupe-test-rule-ownership` put "a gate rule or measurement has one documented home" under
`build-test-gate`, whose purpose is comparability of gate results — the gate's instance of this rule.
The requirements added here are the general form, applied to every document, and they do not restate the
gate-specific one: `build-test-gate` keeps owning the gate's rules and measurements, this capability owns
what the entry document may state and where a rule lives. Design D1 records why the two are not merged.

## Impact

| File | Change |
|---|---|
| `AGENTS.md` | rewrite of the blocks listed in the table above: AA section (143-218), iteration loop (232-316), JNI stub scoping (383-406), Release versioning (373-382), Build & Test (338-372), Common Patterns (442-470); Native Build Details (407-441) moves to `Build.md`; Code Style (62-66) reduces to the convention reference |
| `guidelines/Build.md` | new §12 "Native build and vcpkg" (the vcpkg/CMake/ABI facts, moved, not rewritten); no existing section edited |
| `TODO.md` | one entry if a rule is found with no owner during the move, or if a block cannot be reduced to a fact plus a pointer |

**Not affected.** No source, test, Gradle, manifest, resource, workflow or native file; `openspec/config.yaml`
is untouched by this change (it is `scope-guideline-reads`'s file); no spec's existing requirement changes.

**Guidelines referenced.** `Build.md` (§2/§3/§4/§5/§6/§9/§10, plus the new §12), `Design.md` §2 and §8,
`UI.md` (car parity and phone surface rules). No guideline's rules are edited — only `Build.md` grows a
section that receives moved facts.

**Scope.** Repository-wide; the always-read developer document only. Nothing is phone-, tablet- or
car-specific, although the largest block being removed is the car/AAOS one.

**Sequencing.** Three changes edit `AGENTS.md`, so they run one at a time in this order:

```
1  dedupe-test-rule-ownership   AGENTS.md:257,263 + guidelines/Build.md + config.yaml
2  scope-guideline-reads        AGENTS.md:9-11 (routing table) + config.yaml + tools/ + CI
3  slim-agents-md               this change: AGENTS.md body sections + Build.md §12
```

This change depends on 2 for the routing table (the pointers it leaves must lead somewhere useful) and on 1
for the reduced wording of the JNI-stub and test blocks. Its line numbers can only be fixed once both have
landed, which is why its `tasks.md` is deferred — see below.

**Verification (planned).** Measured, not asserted: `AGENTS.md`'s size and token count before and after
(`wc -c` ÷ 3.6), and, for every removed block, a `grep` proving its statement exists in the owning document
— one hit per rule, none lost. No build and no test run is owed; the change compiles no code.

## Status of this change's artifacts

`proposal.md` and `design.md` complete the outline. `tasks.md` is deliberately deferred to the point where
changes 1 and 2 have landed: its tasks are line-level edits to `AGENTS.md`, and those line numbers are
invalidated by both predecessors. The spec delta is written now because the CLI rejects a change with no
delta (`skip_specs` would assert that no spec-level behaviour changes, which is false here).
