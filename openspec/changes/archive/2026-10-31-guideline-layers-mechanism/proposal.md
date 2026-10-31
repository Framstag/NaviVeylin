# A guideline document is a layer, and every rule in it has a name

## Why

The routing table prices **sections**, and a session reads **documents**. The check that enforces the price —
`tools/check-doc-routes.sh` plus the `routed-section-size` sensor row — has done its job: the tree carries
`§2a`…`§2e`, `§4a`…`§4f`, `§6a`…`§6h`, `§10a`…`§10k` because the 12 000-byte section bound forced those splits. The
split happens *inside* the container, so the number a reader is told to read and the file they open have drifted
apart by an order of magnitude:

| | routed read | the container it sits in | factor |
|---|---|---|---|
| `Build.md` | §4f 7 016 B | 166 383 B | 24× |
| `MapRendering.md` | §13 6 258 B | 96 482 B | 15× |
| `UI.md` | §7a 13 867 B | 110 793 B | 8× |

Three measurements say the same thing, taken on this tree:

- **Nothing prices a document.** `guidelines/` holds 646 144 bytes across 9 documents; 8 routed sections are
  already past the 12 000-byte bound while the documents that hold them are past it by 10–70×
  (`tools/check-doc-routes.sh --sizes`). A 166 383-byte file is legal today, merely unpriced.
- **The largest single block in the tree has no route at all.** `Backlog.md` §7 measures 56 602 bytes and no row
  of the routing table names it, so it is reachable only by search — 8.7 % of the guideline corpus.
- **The always-read document is 83 % spent.** `AGENTS.md` is 26 646 bytes of its 32 000-byte bound, and 8 831 of
  those bytes are the routing table itself. Its own spec already says what should happen to the rest: "Material
  that a reader needs only while working inside one area SHALL live in that area's document"
  (`openspec/specs/documentation-ownership/spec.md`, "The entry document is bounded by what a session needs before
  it reads anything else"), and measured by section, `Backlog maintenance` 1 777 B, `Agent iteration loop`
  3 351 B and half of `Key Conventions` 5 405 B are exactly that material.

**And a rule cannot be referred to.** The tree already writes rules in a hand-rolled named form — `**Rule: a render
target is released, never recycled**`, all 5 of them in `MapRendering.md` — and already writes rationale in a
hand-rolled separate form — `UI.md` carries `### Why the phone is not locked during a car session`,
`### Why favorite, group and starred reordering are phone-only`, `### Why the license list is phone-only`. Neither
form is machine-readable, so:

- a rule has no address finer than its section: a reference names `§2` and the reader finds five rules there;
- the "A rule has one normative home" requirement — *"When the same rule is found in more than one document, the
  owning statement SHALL survive"* — is checked by a reader's memory, not by a case, because two documents that
  state one rule share no token a check could compare;
- the review's reading set is the author's own claim. `tools/loop/prompts/reviewer.md` says the reviewer's inputs
  are "the project's rules (`AGENTS.md` and **the guideline section the change names**)" — so a change that names
  two sections when its diff touches five is graded against two, and nothing can report the omission.

**Why now.** Four changes are open and unimplemented: `shaping-inbox`, `change-readiness-gate`,
`loop-consumes-a-prepared-change`, `finding-shape-at-filing`. Each cites `guidelines/Backlog.md` (5–9 citations
each), all four name `tools/check-doc-routes.sh`, and `loop-consumes-a-prepared-change` edits
`tools/loop/prompts/reviewer.md` — the file this change rewires. Landing the mechanism first means they gain a
rule-level reading set and a stable address instead of being rebased twice.

**Acceptance scenario.** A routed document whose rules are named, whose rationale and procedure live in its
companions, and which stays under the document bound is accepted; a document that keeps a `(measured 2026-…)`
figure inside a rule, a companion whose `§N` anchor names no section of its base document, two documents owning one
rule name, and a named path glob that matches nothing are each reported, with the file and line that carries it.

**Owning guideline.** `guidelines/Build.md` §2 "Common behavior" and §4a "The verdict, the warnings and the
failures" own what a declared check's condition and verdict mean; the requirement this change extends is
`openspec/specs/documentation-ownership/spec.md` "The routing cost is measured", which already states that the
bounds live in the checks' registry so retuning one is one edit. The routing table's own rules — a row that names a
lettered sub-section states its cost, and the cost is the measurement — stay as they are.

**Surface.** Measured on the tree with
`grep -rln 'Rule: ' guidelines/ && ls guidelines/*.why.md guidelines/*.recipes.md 2>&1`:

```
**Rule: <name>** occurrences today:  MapRendering.md 5, every other document 0
companion documents today:          0 — no *.why.md and no *.recipes.md exists
rule-name uniqueness check today:   none — no tool reads a rule name
path->rules map today:              none — no file maps a changed path to a guideline rule
document-size bound today:          none — the registry holds only routed-section-size
                                    and entry-document-size
obligations that carry no name:     the far larger population this change only makes nameable —
                                    `- **Never …**`/`- **Do not …**` bullets: Design 38, Build 3,
                                    Logging 3, UI 1, MapRendering 1; prose obligations: UI 39,
                                    Design 36, MapRendering 9, Build 3, Backlog 1, Regulatory 0
                                    (`grep -c '\*\*Rule:'`, `grep -cE '^[-*] \*\*(Never|Do not|Always|MUST)'`
                                    and `grep -cE '(SHALL|MUST)'` per document — a line count, not a
                                    rule count, and the only number here that is a judgement)
```

surface: 3 code files created (the rule-route check, its self-test and its mutation list), 2 data files created
(`tools/doc-routes.json`, and the purity condition inside the existing metric script), 3 code files rewritten
(`tools/check-doc-routes.sh`, `tools/sensors/sensors.json`, `tools/sensors/metrics/doc-context.sh`), 1 prompt
rewritten (`tools/loop/prompts/reviewer.md`), 9 guideline paragraphs relocated in `AGENTS.md`, 0 fixtures realigned —
`grep -rn 'doc-routes\|rule-name\|companion' tools/ openspec/specs/ .github/workflows/`

## What Changes

- **Three layers per concern, as sibling files.** `guidelines/<Concern>.md` carries the normative rules;
  `guidelines/<Concern>.why.md` carries the rationale — why the rule exists, the measurement that proved it, the
  reversal, the dated provenance; `guidelines/<Concern>.recipes.md` carries the procedure — commands, sample
  output, device steps, number tables. **No `§N` renumbers**: a companion's `## §N <heading>` anchors to the
  section of that number in its base document, so `Backlog.md §6` resolves to the same place it does today and
  every citation in the four open changes stays valid.
- **A named rule.** `**Rule: <name>** <statement>. <refusal path>.` — the bold span is the name, the bullet is the
  statement. A name is unique within its document and across `guidelines/*.md`, and a reference is written
  `see rule <File>.md §N "<name>"`. A name may change: the check reports a reference that no longer resolves, and
  no id registry, alias table or migration map is introduced.
- **A companion is not a routed document.** `tools/check-doc-routes.sh` stops treating `*.why.md` and
  `*.recipes.md` as documents a route must name, while still reading them: every `## §N` anchor in a companion
  resolves against its base document, and a skill path inside one is still checked.
- **A normative document carries no rationale and no procedure.** A declared condition reports a fenced block
  inside `<Concern>.md` that carries a command's output, a `tests=`/`failures=` tally or a timestamp, a
  `(measured YYYY-…)` figure, an `issue #N` reference or a `### Why …` heading. It is a condition with a file and a
  line, not a review opinion.
- **A document is bounded, not only a section.** `tools/sensors/sensors.json` gains a `document-size` row beside
  `routed-section-size` and `entry-document-size`; a document the routing table names and that is past the bound is
  that row's condition, identified by its path.
- **The review reads a derived set.** `tools/doc-routes.json` maps a path glob to the sections and rules that own
  its conventions; `tools/check-rule-routes.sh` reports a glob that matches no tracked path and a rule no glob
  reaches, and its `--diff` mode prints the rule set for a change's touched paths. `tools/loop/prompts/reviewer.md`
  reads that set instead of the change's own claim, and the change may *add* a section it names itself.
- **`AGENTS.md` is dieted to its spec, by removal only.** `Backlog maintenance`, `Agent iteration loop (measure
  first)` and the per-area half of `Key Conventions` are reduced to one sentence and their route, each statement being
  one the owning document already carries; a block whose statement is found in no guideline stays in place and is
  recorded as a rule without an owner. **Nothing under `guidelines/` is written** — the documents those statements
  live in are cut by the six changes that follow, and an entry-document change may not edit them in flight. The
  routing table gains one row for the companion convention.

Additive. No rule's normative text changes, no `§N` moves, no citation is rewritten, and no behaviour of the
application is touched: this is text, shell under `tools/`, one sensor registry row and one prompt.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `documentation-ownership`: four requirements added — a guideline document is one of three layers and a companion
  anchors to a section that exists; a rule is named and its name resolves; a normative section carries no rationale
  and no procedure; the review's reading set is derived from the change's paths — and two requirements modified:
  "The routing cost is measured" gains the document bound, and "Every guideline document is covered by the route
  check" exempts a companion from being a routed document while keeping it read.

## Impact

| path | what changes |
|---|---|
| `openspec/specs/documentation-ownership/spec.md` | after archive: 4 requirements added, 2 modified |
| `tools/check-doc-routes.sh` | companion derivation, rule extraction, rule-reference resolution; `-selftest.sh` and `-mutations.json` gain a case per new refusal |
| `tools/doc-routes.json` | new — the path-glob → section/rule map |
| `tools/check-rule-routes.sh` | new — dead glob, orphan rule, `--list`, `--diff <range>` |
| `tools/check-rule-routes-selftest.sh` | new — offline cases in a scratch tree |
| `tools/check-rule-routes-mutations.json` | new — one record per refusal |
| `tools/sensors/sensors.json` | `document-size` row; the purity condition's row |
| `tools/sensors/metrics/doc-context.sh` | the purity condition — file, line, token |
| `tools/loop/prompts/reviewer.md` | item 7 reads the derived rule set |
| `tools/loop/run.sh` | `DIFF_RANGE` plumbed into the reviewer prompt, so the derived set has a range to read |
| `.github/workflows/build.yml` | the new check's step beside the route check |
| `.github/workflows/sensors.yml` | the two new registry rows on the sensor schedule |
| `tools/loop/loop.conf`, `tools/tooling.json` | the changed-file → suite edges, and the map's path as project configuration |
| `tools/check-doc-routes-selftest.sh`, `tools/check-doc-routes-mutations.json` | a fixture case and a mutation record per new refusal in the route check |
| `tools/sensor-cases.sh`, `tools/sensor-mutations.sh` | the cases and the condition records for both new rows |
| `AGENTS.md` | the diet, measured: 90 lines removed, 29 added, 26 646 B → 21 796 B; one new row for the companion convention |
| `guidelines/Backlog.md` | three-layer cut and named rules land in `guideline-backlog-layers` |

No Android component is touched: no `:app`, `:auto`, `:core`, `buildSrc`, Gradle file, manifest, resource, native
source, JNI signature or submodule path. Scope: **general process and documentation tooling**, neither phone nor
Android Auto.

**Rollback path.** Revert the commit: the companions are new files, the registry row is one object in a JSON array,
and `AGENTS.md`'s diet is nine paragraphs that return to it. Nothing consumes the derived rule set except the
reviewer prompt, so a half-landed state degrades to today's behaviour (the reviewer reads the section the change
names) rather than breaking a run.

## Not in this change

- **Any document's relocation.** The cut of `Backlog.md`, `Build.md`, `UI.md`, `MapRendering.md`, `Design.md`,
  `Regulatory.md`, `FeatureList.md`, `Metrics.md` and `Logging.md` is one change per document:
  `guideline-backlog-layers`, `guideline-build-layers`, `guideline-ui-layers`,
  `guideline-maprendering-layers`, `guideline-design-layers`, `guideline-small-docs-layers`. This change lands the
  mechanism and the guards; the six then retire what the guards report.
- **The land order of the four loop changes.** They are unaffected in their content and unedited by this change;
  `guideline-backlog-layers` is what unblocks them, and this change's `design.md` records the order and why
  (`finding-shape-at-filing` edits `AGENTS.md` text and `loop-consumes-a-prepared-change` edits
  `tools/loop/prompts/reviewer.md`, so both rebase rather than conflict).
- **Tying a build gate's message to the rule it enforces.** Named rules make it expressible — a gate names
  `Logging.md §3 "<rule name>"` and a check verifies the name resolves — but that is a change of its own, because
  it edits the gates and not the guidelines.
- **`openspec/config.yaml`.** It is read whole by the CLI per artifact and cannot be split; its 5 735-byte
  `operations` block and 6 006-byte `rules` block are reducible only by writing less, which is not this change's
  decision.
