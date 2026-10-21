## Why

`openspec archive` writes, for a capability it creates, a `## Purpose` section it cannot fill:

```
## Purpose
TBD - created by archiving change <change>. Update Purpose after archive.
```

That sentence is the archive's note to a human, and nothing in the repository reads it. `openspec
validate <capability> --type spec` prints it only as a warning and still **exits 0**
(`openspec/specs/navigation-ongoing-notification/spec.md:4`, `openspec/specs/render-mode-switch/spec.md:4`,
2026-10-21T11:22:17Z). The CLI's own warning states the second half of the mechanism: a `## Purpose` in a
delta is read only when the capability is created, so no later change can repair one — only a hand edit of
the main spec can.

**Root cause.** The section is written by the archive and read by nobody: no check in `tools/`, no CI step
and no validator exit status looks at a spec's `## Purpose`, so the placeholder survives indefinitely. Two
capability specs still carry it, and `highlight-measurement` carried it until commit `b3dfc78` wrote that one
by hand (`git log --oneline -1 -- openspec/specs/highlight-measurement/spec.md`) — the same workaround that has
now failed twice.

**Red test.** No compiled file is in this delta, so there is no JUnit XML: the cases are a structural check
(`guidelines/Build.md` §2, its scope block). The diagnosis probe, on base `44b673f`, 2026-10-21T11:22:13Z:

```
$ grep -rln 'TBD - created by archiving' openspec/specs/*/spec.md
openspec/specs/navigation-ongoing-notification/spec.md
openspec/specs/render-mode-switch/spec.md
EXIT=1
```

Two offending specs: the property was false on HEAD. After the fix, no path and `EXIT=0`
(`evidence/red-probe-green.txt`).

The check that ships is that probe narrowed to the lines between a `## Purpose` heading and the next `## `
heading, because this change's own requirement quotes the sentence in its prose and a whole-file grep then
reports the spec that states the rule. It is a `find openspec/specs -name spec.md` pass, not a
`openspec/specs/*/spec.md` glob: 7 of the 176 capability specs sit one level deeper, in `auto/*` and
`share/*`, and a flat glob cannot see them (`evidence/nested-spec-coverage.txt`: `ls
openspec/specs/*/spec.md | wc -l` = 169, `find openspec/specs -name spec.md | wc -l` = 176). Its red half is
therefore the revert-check in `evidence/revert-check.txt`: the placeholder put back under `render-mode-switch`'s
`## Purpose` makes the check print `openspec/specs/render-mode-switch/spec.md:4` and exit 1; the placeholder put
back under the nested `share/location-receiving`'s `## Purpose` makes it print
`openspec/specs/share/location-receiving/spec.md:4` and exit 1 (`evidence/revert-check-nested.txt`); restored,
it prints `Every capability spec states its Purpose.` and exits 0.

**Owning requirement.** `openspec/specs/documentation-ownership/spec.md` — the capability that owns the
project's documents and the properties of them that a command can check. This change adds one requirement
to it, *A capability spec states what the capability is for*.

**Owning guideline section.** `AGENTS.md` "Documentation Map" routes "how the guidelines themselves are
organized" to that capability spec, and its "OpenSpec Workflow" section owns the list of CI-gated artifact
rules; the no-gate rule for a delta that touches no compiled file is `guidelines/Build.md` §2, its scope
block.

## What Changes

- `openspec/specs/navigation-ongoing-notification/spec.md` — the archive placeholder is replaced by one
  sentence saying what the capability is for.
- `openspec/specs/render-mode-switch/spec.md` — the same.
- `openspec/changes/fix-capability-spec-purpose-placeholder/specs/documentation-ownership/spec.md` — one
  ADDED requirement: a capability spec's `## Purpose` is a written sentence, and the artifact-hygiene check
  names every spec that still carries the archive placeholder.
- `.github/workflows/build.yml` — the step `Check OpenSpec artifact hygiene` gains that check over every
  `spec.md` under `openspec/specs/` at any depth, and prints `Every capability spec states its Purpose.` when
  the tree passes, so the mechanism is reported instead of waiting for a reader.
- `AGENTS.md` — its "OpenSpec Workflow" section says **two** artifact rules are CI-gated; it names three
  once this check exists.

Additive: two spec sentences, one requirement, one CI check, one sentence in the entry document. No
application code, no native code, no behaviour of the app.

## Impact

Files: the four above plus this change's own artifacts. Modules: none — no Gradle module, no compiled file.
Behaviour: unchanged at runtime. Rollback: revert the four file edits; the capability specs return to the
placeholder, which is what the check then reports.

Affected previous specifications: `documentation-ownership` (one requirement added; no shipped requirement
modified). `navigation-ongoing-notification` and `render-mode-switch` keep every requirement and scenario
they have — only the placeholder line above them changes.

Guidelines affected: `AGENTS.md` (fact: three CI-gated rules, not two). No `guidelines/*.md` change: the
rules this touches live in the OpenSpec workflow section of the entry document and in this capability spec.
