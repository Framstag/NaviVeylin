# Design

See `proposal.md` — **Why** for the measured cost of the current reading instruction and for the two
sub-findings (the duplicated `## 14.` numbering, the 80 sections), and
`specs/documentation-ownership/spec.md` for the requirements this design implements.

## Context

The reading instruction lives in three places, all of which have to change together:

```
openspec/config.yaml:17    context:  "Guidelines: Design.md, UI.md, MapRendering.md, Build.md
                                       — read before design/apply"        -> ~86.6k tok when obeyed
openspec/config.yaml:83    rules.apply:   "Verify all new/modified code against every document
                                            in guidelines/"                -> the same cost again
openspec/config.yaml:111   rules.archive: "Verify design decisions follow the guidelines/
                                            documents (Design.md, UI.md, MapRendering.md)"
```

The only document a session *always* has is `AGENTS.md` (injected, 38.8 KB / ~10.8k tok). Its
"Documentation Map" is three lines (408 B) that name five documents and their one-line purpose — an
inventory, not a route.

Section inventory the route has to work against (measured 2026-10-07):

```
Build.md          11 ## sections   numbered, clean
Design.md         15 ## sections   + "## Appendix A", "## Appendix B"      (unnumbered)
UI.md             20 ## sections   + "## Keeping this document honest"     (unnumbered)
MapRendering.md   24 ## sections   + a DUPLICATE "## 14." (line 553 rotation-gesture handoff,
                                     line 630 Android Auto renderer)
                                   + "## Known Pitfalls", "## Parameter Overview"
Regulatory.md     10 ## sections   numbered, clean
                  === 80 sections
```

Reference pressure on the ambiguous number: `§14` of `MapRendering.md` is cited 11 times across
`guidelines/`, `TODO.md` and archived changes (10 of them meaning the AA renderer at line 630),
`§15` 15 times, `§1` 30 times. That is why this design does not renumber.

## Goals / Non-Goals

**Goals**

- A reader who knows what they are changing can name the sections that own it, without reading a
  document, and the route is available in the document that is always present.
- A route that points at a section is checkable by a command, on a clean checkout, with no build, device
  or network.
- The change-artifact guidance stops instructing a whole-document read, and still survives the CLI's
  guidance-quoting rule.

**Non-Goals**

- Not shortening any guideline, moving any rule or measurement, or deleting a section.
- Not renumbering `MapRendering.md`'s sections; the ambiguity is resolved per reference, and the defect is
  recorded for a separate change.
- Not making the route exhaustive: an 80-row table in the always-read document would cost ~2.5k tokens per
  session and duplicate five documents' own tables of contents.
- Not touching `buildSrc`, any Gradle task, or any test source. No `preBuild` hook.
- Not the second half of the capability (facts vs. normative rules, one normative home) — that is
  `slim-agents-md`, which builds on this change's route.

## Decisions

### D1 — the route lives in `AGENTS.md`'s Documentation Map

**Chosen.** Lines 9-11 become a topic→section routing table of roughly 30 lines.

| Alternative | Why not |
|---|---|
| A new `guidelines/Index.md` | A sixth document nobody reads unless told to; the route must sit where the reader already is. It would also need its own line in the context block, i.e. one more thing to keep in sync. |
| In `openspec/config.yaml`'s context block | Served only while a change artifact is being written, so it is absent during implementation and the on-device/verification passes where the conventions apply. It also duplicates a table that `AGENTS.md` needs anyway. |
| A TOC added to the top of each guideline | Five copies of the same mapping logic, and each document is read on its own terms; the mapping belongs in the one document that is always read. |

Cost accounting: the table adds ~1.2k tokens to a document paid once per session, and removes ~86.6k
tokens from every design/apply turn that previously read four documents. A session that reads at least
one guideline already comes out ahead.

### D2 — a section is identified by number *and* heading text; completeness is not required, soundness is

**Chosen.** Each route row names the document, the section number if it has one, and the heading text.
The check enforces that every reference resolves. It does not enforce that every section is routed.

Rationale: the duplicate `## 14.` means a number alone is not a key (D4). Requiring completeness would
force the 80-row table this change deliberately avoids, and would create a second copy of each
document's own section list — the drift class that `dedupe-test-rule-ownership` is fixing. A section that
no route names is still reachable by listing headings, and the table states that in its own first line.
A dangling route is the dangerous direction (a reader is sent to a section that is gone), and that is the
direction the check covers.

**Alternative considered.** Derive the route from the documents at read time (a script that prints a
topic→section map). Rejected: the mapping is a judgement about which section owns what, and that judgement
is exactly what a human-maintained table records; a generated table would have to be generated from
something, and that something is the table.

### D3 — the soundness check is a shell script in `tools/` with a self-test, run by a new CI step

**Chosen.** `tools/check-doc-routes.sh` + `tools/check-doc-routes-selftest.sh`, called from a new CI step
"Check documentation routes" modelled on "Check OpenSpec artifact hygiene"
(`.github/workflows/build.yml:100-122`).

| Alternative | Why not |
|---|---|
| A `buildSrc` Kotlin scanner with unit tests (the `WallClockWaitScanner` shape) | Scanning markdown has nothing to do with compiling the app, and it would put a documentation check on the build's critical path. `buildSrc`'s scanners exist because they guard *code* patterns that would otherwise reach a device. |
| Fold the call into the existing hygiene step | That step's failure message is about OpenSpec artifacts; a dangling documentation route reported under that name is ambiguous. A separate step costs seconds and names its own failure. |
| No check at all | A dangling route is silent and sends a reader to the wrong section of a correct document — the failure is invisible in the artifact it damages. Unlike the deferred measurement-literal guard of `dedupe-test-rule-ownership`, this check has no false-positive risk: a section either exists or it does not. |
| A `preBuild` hook | Documentation would fail a build that never reads it. |

### D4 — how the check resolves a reference, and why the duplicate `14.` is not renumbered

**Chosen.** The check extracts `^## ` headings from the named document, ignoring lines inside fenced code
blocks (a fenced markdown sample can contain a `## ` line), and accepts a route row when its number
matches a heading *and* — when the row carries heading text — that text matches one of the headings with
that number. So `MapRendering.md §14 (Android Auto renderer)` resolves to line 630, while a bare
`§14` would resolve and be reported as ambiguous.

Renumbering is rejected: `§14` of `MapRendering.md` is cited 11 times outside the document (in
`guidelines/Design.md:427`, 10 archived changes, and `TODO.md`), and 10 of those mean the AA renderer.
Renumbering the AA renderer to `§20` would silently repoint all ten at the rotation-gesture section.
The defect is recorded in `TODO.md` (category `specs-and-process`) with the reference counts, for a change
that can update the references and the numbering together.

### D5 — the guidance edits keep the CLI's quoting contract

**Chosen.** `config.yaml:17` keeps the inventory of the five documents and states the cost and the route;
`config.yaml:83` and `:111` are rewording only. Every edited entry stays a quoted YAML string.

Rationale: an unquoted `KEY: text` entry parses as a mapping, the guidance array fails the
array-of-strings check, and the CLI silently drops that operation's guidance (`AGENTS.md` OpenSpec
Workflow; the CI hygiene step's `awk` at `.github/workflows/build.yml:115`). The verification is the two
commands already used for the sibling change: `openspec doctor` and reading the guidance back through
`openspec instructions … --json | jq`.

### D6 — threading, lifecycle, native boundary

Not applicable: no component, dispatcher, coroutine scope, surface or lifecycle owner is introduced, and
no JNI or native source is touched. `guidelines/Design.md` §4 (threading, lines 128-249) and §5 (native
boundary, 251-297) therefore impose nothing, and the change has no effect on any build flavor.

## Risks / Trade-offs

- **A route that is right today can be wrong after the next guideline edit.** → The check fails on a
  dangling reference; the CI step runs it; and the table's own first line says how to recover the full
  section list when a section is not routed.
- **Scoping the reading instruction could hide a guideline the change actually needs.** A routed read is
  narrower than a document read by construction. → Mitigation: the route names *every* owner of a topic
  that spans documents (the spec requires it), the context block keeps the five-document inventory, and
  the apply instruction stays a requirement to verify the touched conventions rather than a permission to
  skip them. A change whose topic is not in the table falls back to the section listing.
- **`AGENTS.md` grows by ~1.2k tokens in the always-read regime** while saving ~86k only when the reader
  obeys. → Accepted and measured (Requirement "The routing cost is measured"): a session that reads one
  guideline already gains ~19k; the break-even is a single guideline section read.
- **The table can go stale in the *unsound* direction — a section it should have added.** → Accepted by D2
  as the lesser risk; the completeness direction is not enforced and the table says so.
- **Editing `config.yaml` can silently drop an operation's guidance.** → D5's two commands plus the CI
  hygiene step.
- **A reviewer may read "route by topic" as a licence to skip conventions.** → The requirement is written
  as verification against the owning sections, not as an exemption; the spec's first scenario names the
  route, not the omission.

## Migration Plan

```
1  AGENTS.md      Documentation Map -> routing table (number + heading text per row, plus the
                  one-line "how to get the full section list" note)
2  config.yaml:17 context: keep the five-document inventory, add the cost, point at the table
3  config.yaml:83 apply verification bullet -> scoped to the sections that own the touched conventions
4  config.yaml:111 archive bullet -> same scoping
5  tools/         check-doc-routes.sh + check-doc-routes-selftest.sh (fixture: one resolving row,
                  one dangling row; self-test asserts pass then fail)
6  CI             new step "Check documentation routes"
7  TODO.md        entry: duplicate "## 14." in MapRendering.md, with the 11 external references
8  measure        three in-flight changes (one phone-UI, one car, one render): routed sections vs the
                  four documents, in tokens; record both numbers in the change report
9  verify         run the check (exit 0), its self-test, its mutation (delete one row -> non-zero,
                  restore -> 0), openspec doctor, and the guidance read-back
```

**Rollback:** revert steps 1-4 and 7 as text, delete the two `tools/` scripts and the CI step. No rule
text moved, so rollback cannot lose a convention; nothing at runtime reads these files.

## Open Questions

1. **How many route rows, and at what granularity.** The table's size is a judgement: one row per topic a
   change routinely touches, versus one row per *kind of change* (bug fix vs feature). The check does not
   decide this, and the measurement in step 8 can inform it. Deferrable: it changes wording, not the
   specs, the approach or the task breakdown.
2. **Whether the routing table should later be checked for completeness** against each document's
   headings. Deliberately not required now (D2). If the 80-row cost ever looks smaller than the risk of a
   missing route, it can be added as its own requirement without disturbing this design.
