# Proposal

## Why

Obeying the project's own reading instruction costs a context window. Measured 2026-10-07:

```
openspec/config.yaml context:  "Guidelines: guidelines/Design.md, UI.md, MapRendering.md,
                               Build.md — read before design/apply"
  Build.md          1343 L   98.8 KB   ~27.4k tok
  MapRendering.md   1037 L   81.7 KB   ~22.7k tok
  UI.md             1096 L   78.2 KB   ~21.7k tok
  Design.md          737 L   53.1 KB   ~14.8k tok
  Regulatory.md      337 L   23.9 KB    ~6.7k tok (when it applies)
  ------------------------------------------------------
  reading the four named documents          ~86.6k tok
  all five                                  ~93.3k tok

and the apply guidance repeats the cost: "Verify all new/modified code against every
document in guidelines/ — all conventions and rules defined there must be followed".
```

The guidelines are not bloated. Their bulk is rules interleaved with the measurements that prove them
(`Build.md` §2/§6 tables, `MapRendering.md` §14, `UI.md` §8), and that pairing is load-bearing: the
just-captured `dedupe-test-rule-ownership` change fixes the opposite defect — a measurement quoted in
four places — by making `guidelines/Build.md` its one home. Splitting measurements away from rules would
re-create that drift.

What is missing is a **route** from "the kind of change I am making" to "the section that owns the
conventions it must follow". Without one, the only safe instruction is to read whole documents, which is
the expensive one — and the cost is paid on every design and apply turn, not once.

Two sub-findings shape the design:

- A §-reference is ambiguous today: `guidelines/MapRendering.md` numbers two different sections `## 14.`
  (rotation-gesture handoff at line 553, Android Auto renderer at line 630), and three documents carry
  unnumbered `## ` sections (Design.md's two appendices, UI.md's "Keeping this document honest",
  MapRendering.md's "Known Pitfalls" and "Parameter Overview"). A route that points at "§14" can send a
  reader to the wrong section.
- Enumerating all 80 sections of the five documents in the always-injected `AGENTS.md` would cost roughly
  2.5k tokens permanently — trading a per-turn saving for a per-session one, which is the wrong direction.

## What Changes

- **`AGENTS.md`'s Documentation Map becomes a routing table**: kind of change → the section that owns its
  conventions (architecture, threading, native boundary, render pipeline, follow mode, phone UI, car UI
  and parity, i18n, day/night, stylesheets, logging and diagnostics gates, build, gate and tests, coverage,
  licence and SBOM, regulatory, backlog). A section is identified by number **and** heading text, so an
  ambiguous or unnumbered heading is still unambiguous in the route. Roughly 30 lines in place of the
  present 3, and deliberately not exhaustive — the remaining sections stay reachable with
  `grep -n '^## ' <file>`.
- **`openspec/config.yaml`'s context line directs section reads**: read the section that owns the topic,
  not the document, and name where the routing table lives. The line keeps the existing inventory of the
  guideline documents and stops implying that all four must be read.
- **`openspec/config.yaml`'s apply guidance is scoped the same way**: "verify against every document in
  guidelines/" becomes verification against the sections that own the conventions the change touches.
  The archive guidance's "verify design decisions follow the guidelines/ documents" is scoped likewise.
- **The router cannot silently rot in the dangerous direction**: a new `tools/check-doc-routes.sh`
  (with a device-free `tools/check-doc-routes-selftest.sh`) fails when a route names a section that no
  longer exists, and a CI step runs it beside the existing artifact-hygiene step.
- **The duplicate `## 14.` numbering in `MapRendering.md` is recorded, not renumbered.** Twenty-odd
  `§N` references across `TODO.md`, specs and change archives point at those numbers; renumbering them
  inside a documentation change would be worse than the ambiguity, which routes now resolve by heading
  text. The defect gets a `TODO.md` entry.
- **Not changing:** no rule text moves between documents, no section is deleted, no measurement is
  relocated, and no guideline body is shortened. This change alters *how documents are read*, not what
  they say.

**Classification:** additive. **Rollback:** revert the config, `AGENTS.md` and CI edits and drop the two
`tools/` scripts; nothing at runtime observes them.

## Capabilities

### New Capabilities

- `documentation-ownership`: the project's documents tell a reader which section owns a convention, and a
  route that points at a section resolves to a section that exists. It is the capability the guidelines
  lacked — an inventory of documents (`AGENTS.md` "Documentation Map") existed, but no mapping from work
  to section, and no check on the mapping's soundness. The follow-up change `slim-agents-md` adds the
  capability's other half (which document holds facts versus normative rules, and that a rule has one
  normative home) to this same capability.

### Modified Capabilities

None. No spec's behaviour changes: this change alters reading instructions and adds a check, and the
gate, coverage, suite-runtime and JNI capabilities are untouched.

## Impact

| File | Change |
|---|---|
| `AGENTS.md` | "Documentation Map" (lines 9-11, 408 B) becomes a topic→section routing table (~30 lines) |
| `openspec/config.yaml` | `context` reading instruction scoped to sections; `rules.apply` verification bullet scoped; `rules.archive` scoping bullet scoped |
| `tools/check-doc-routes.sh` | new: resolve every section reference in the routing table against the referenced document |
| `tools/check-doc-routes-selftest.sh` | new: device-free self-test for the above (fixture with one valid and one dangling route) |
| `.github/workflows/build.yml` | new step "Check documentation routes", modelled on "Check OpenSpec artifact hygiene" (`:100`) |
| `TODO.md` | new entry: the duplicate `## 14.` numbering in `guidelines/MapRendering.md` (category `specs-and-process`) |

**Not affected.** No source, test, Gradle, manifest, resource, native or JNI file. `buildSrc` is not
touched — the check is a shell script in `tools/`, matching the existing `declared-cases.sh` /
`prune-native-configs.sh` + `-selftest.sh` pattern rather than adding a build-time scanner.

**Guidelines referenced.** All five are *named* by the routing table; none has its rules edited.
`guidelines/Build.md` §2's rule "quote the timing record, not a bare total" and the measurement-ownership
rule captured in `dedupe-test-rule-ownership` are respected: this change moves no number.

**Scope.** Repository-wide developer documentation and one CI step. Nothing is phone-, tablet- or
car-specific; `guidelines/UI.md`'s parity rules are unaffected.

**Sequencing.** `dedupe-test-rule-ownership` (captured, not yet applied) edits `AGENTS.md` lines 257 and
263 and `guidelines/Build.md`; this change edits `AGENTS.md` lines 9-11. Different regions of one file, so
the two must still not run in one tree (one builder per tree). Apply order:
`dedupe-test-rule-ownership` → this change → `slim-agents-md`. The follow-up `slim-agents-md` is scaffolded
(outline only) as a sibling change and depends on this one for the routing table it will lean on.

**Verification.** Measured, not asserted: for three in-flight changes — one phone-UI, one car, one
render-pipeline — record the token cost of routing-versus-reading (`wc -c` on the documents the old
instruction names against the sections the router names, divided by 3.6), plus the router check's exit
code and its mutation (delete one route row → the check must fail; restore → it must pass).
