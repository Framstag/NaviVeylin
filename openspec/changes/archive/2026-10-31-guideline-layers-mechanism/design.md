# Design — guideline-layers-mechanism

## Context

The mechanism this change builds extends three things that exist and one that does not.

- **The bound machinery exists and is healthy.** `tools/sensors/sensors.json` holds one row per declared check —
  `routed-section-size` (12 000, `greater-than`, `consecutive: 2`, `file+close`) and `entry-document-size`
  (32 000). Both are served by one metric script, `tools/sensors/metrics/doc-context.sh`, whose cost ruler is the
  same one `tools/check-doc-routes.sh --sizes` prints, so a figure a routing row states and the number a sensor
  measures cannot disagree.
- **The address machinery exists.** A reference is written `<File>.md §<number> "<heading text>"` and resolved by
  `tools/check-doc-routes.sh` (`extract_refs`, `L127`), whose document set is derived from `guidelines/*.md` at run
  time, whose self-test is offline, and whose mutations live in `tools/check-doc-routes-mutations.json`.
- **The rule idiom exists but is not readable by a tool.** Five occurrences of `**Rule: <name>**` are already
  written by hand (all 5 in `MapRendering.md`); three `### Why …` rationale headings are already
  written by hand in `UI.md`. Nothing reads either form.
- **The review's reading set is a claim, not a derivation.** `tools/loop/prompts/reviewer.md` takes "the guideline
  section the change names" as an input, so the author of a change chooses what the reviewer reads.
- **Four changes are open and unimplemented** — `shaping-inbox`, `change-readiness-gate`,
  `loop-consumes-a-prepared-change`, `finding-shape-at-filing`. Each cites `guidelines/Backlog.md` (5–9 citations),
  all four name `tools/check-doc-routes.sh`, and `loop-consumes-a-prepared-change` edits
  `tools/loop/prompts/reviewer.md`. Measured by the ruler this change extends:

  ```
  guidelines/                                                646 144 B, 9 documents
  routed sections past the 12 000 bound                            8
  documents past a 48 000 bound                                    5   (Build 166 383, Backlog 112 888,
                                                                        UI 110 793, MapRendering 96 482,
                                                                        Design 74 205)
  AGENTS.md                                        26 646 B of its 32 000 B bound (83 %)
  largest unrouted block                     Backlog.md §7 = 56 602 B (no routing row names it)
  ```

See `proposal.md` — Why for the measurements that motivate this; this document does not restate them.

## Goals / Non-Goals

**Goals.**

- Three layers per concern, so a rule is never read together with the story that produced it.
- A rule with an address finer than its section, so a reference can name it and two documents cannot own one
  name without a case reporting it.
- Every new invariant is a *declared check* with a condition, a bound in the registry and an offline case — the
  shape the tree already uses, not a convention a reviewer must remember.
- A review reads the rules its diff's paths are governed by, derived from the tree, and a change may only *add* to
  that set.

**Non-Goals.**

- Migrating any document. The cut of the nine guidelines is six changes of their own.
- Judging prose. No case in this change reads a sentence for its meaning; where judgement is unavoidable it stays
  with the reviewer (see D10).
- A rule identifier, an alias table or a migration map. A name may change (see D2).
- Enforcing a rule in the build. Named rules make "this gate enforces that rule" expressible; wiring the gates to
  it is a separate change.

## Decisions

### D1 — The rationale lives in a sibling file, not in a directory and not in a new document

| | form | what it costs |
|---|---|---|
| **A (chosen)** | `guidelines/<Concern>.why.md` beside `guidelines/<Concern>.md` | one derivation rule in the route check; **no `§N` moves** |
| B | `guidelines/<Concern>/{rules,why,recipes}.md` | every reference form changes (`§N` → a path), the derivation rule is needed anyway, and grep by concern finds a directory instead of a file |
| C | promote each lettered sub-section to a top-level document | renumbers every address — the four open changes cite `Backlog.md §6`, `§7`, `§9` — and adds ~20 rows to a 26 646-byte entry document whose bound is 32 000 |
| D | keep one file, mark rationale blocks | the rationale stays in the routed read, which is the defect |

A, because the address is the only thing the tree cannot afford to move: the four pending changes are written
against `§N` addresses, and a renumbering buries them behind `tools/issues/resolve-stale.sh`. **Risk:** A separates
by *filename only*, so history drifts back inline the first time someone writes "the rule exists because…" —
**Mitigation:** the purity condition (D4), which reports a dated figure, a `tests=` tally, a fenced output block or
a `### Why …` heading *inside* a normative document with its file and line. A convention cannot be relied on; a
condition can.

### D2 — A rule's name is a bold bullet, and there is no identifier

| | form | why not / why |
|---|---|---|
| **A (chosen)** | `- **Rule: <name>** <statement>.` | already written by hand 5×; the `## `-only section ruler ignores it, so naming a rule changes no section cost; extraction is one regex |
| B | `#### Rule: <name>` heading | ~400 headings across the tree, each inviting a body under it, so sections grow toward the 12 000-byte bound the splits just fixed |
| C | a rule table per section | a table cell cannot carry a refusal path |
| D | `R-MR-002` plus a name | an identifier is exactly the lifetime guard this change is told not to build |

A. A name is prose, so it is *not* enforced for length or style — the check resolves it, nothing judges it. A name
is unique within its document and across `guidelines/*.md`; that single comparison is what turns "A rule has one
normative home" from a reader's memory into a case, because the duplicate is *the token the two statements share*.
A rename is allowed: the check reports the reference that no longer resolves, with the names the section does
carry, and no alias table is maintained.

### D3 — A companion anchors with `## §N <heading>`

- **A (chosen).** The anchor is what makes the companion a *layer* of a numbered section rather than a second
  document, and the number is the one thing that survives a rule being renamed.
- B — no anchor: the companion becomes an unrouted document, which the route check reports by design.
- C — anchor by rule name: cannot be the only anchor, because names may change (D2).

A companion carries no section number its base document does not carry, and the check reports the companion, the
anchor and the base document when it does. This is the one new refusal in the route check that fails CI on
landing, and it costs nothing on the current tree because no companion exists yet.

### D4 — A third bound, on the document, at 32 000 bytes

`tools/sensors/sensors.json` gains `document-size` beside the two existing rows: `greater-than`, 32 000,
`consecutive: 2`, `lifecycle: file+close`, `class: improvement`, `category: specs-and-process`,
`fingerprint.fields: ["document"]`, served by the same `doc-context.sh`.

**Why 32 000 — retuned during apply, 2026-10-31, at the owner's instruction.** The bound was written as 48 000
(4× the section bound, flagging 5 of the 9 documents then present). It was retuned to the entry document's own
bound, on the principle that makes the two numbers one rule: **no document a route names costs more than the
document a session is handed before it reads anything else.** The retune was taken after the six cuts landed and
left `UIPhone.md` 74 bytes under the old bound — a document that the next added sentence would have filed a
condition against, which is the fragility the bound exists to remove rather than to create. The words stay
`greater-than` and the ruler is unchanged; what changes is that four documents now owe a cut of their own
(`UIPhone.md` 47 926, `MapRendering.md` 46 751, `UI.md` 45 324, `Backlog.md` 35 890 at the moment of the retune).
Alternatives considered when the bound was first written, and how the retune changes them:

- **B — no document bound.** Still rejected: without it a 166 383-byte file stays legal, merely unpriced.
- **C — bound the sum of a document's routed sections.** Still rejected: a document with twenty small sections
  passes it and is still a 166 383-byte file, and it doubles the ruler for no gain.
- **D — reuse the 32 000-byte entry bound.** *This was the option first rejected* on the arithmetic that it would
  force documents apart while the routing table had only 5 354 bytes of headroom. **Adopted, and the arithmetic
  re-measured:** the table now has 7 104 bytes of headroom (AGENTS.md 24 896 B of 32 000) and each new document
  needs one row, so the four cuts the retune forces fit — and a bound that is one rule in two places is worth more
  than the rows it costs.

The bound is one object in the registry, so retuning it is one edit — the rule the sibling requirement already
states and the reason the bound is not written into a script.

### D5 — The reading set is a file of globs, derived at review time

- **A (chosen).** `tools/doc-routes.json` maps a path glob to sections and rule names;
  `tools/check-rule-routes.sh --diff <range>` prints the set for a range. The reviewer reads that output instead of
  the change's claim, and a change may *add* a section it names.
- **B — the change declares its own reading set** in its proposal. This is today's behaviour with a field name; the
  author still chooses, and the omission a review exists to catch stays uncatchable.
- **C — the globs live in the routing table in `AGENTS.md`.** Measured: the table is 8 831 B of a 26 646-byte
  document with a 32 000-byte bound, and ~40 globs at ~120 B would consume 4 800 of the 5 354 bytes left. Rejected
  on arithmetic, not taste.

Two invariants keep the map from rotting, and both are cases rather than review notes: a glob that matches no
tracked path is reported (a package or module rename orphans the rules a reader is told to read), and a *named*
rule that no glob reaches is reported (a rule no change is ever told to obey). The second is deliberately phrased
over **named** rules: on the current tree only the 5 hand-written rule names exist, so the map ships with the globs
that reach them and the invariant is green on landing (D9).

### D6 — The entry document is dieted before the table grows

The routing table must gain a row per new document, and six document changes will add rows. `AGENTS.md` has
5 354 bytes of headroom. **A (chosen):** reduce `Backlog maintenance` (1 777 B), `Agent iteration loop (measure
first)` (3 351 B) and the per-area half of `Key Conventions` (5 405 B) to one sentence and their route — which is what
`documentation-ownership` "The entry document is bounded by what a session needs before it reads anything else"
already requires — and do it **by removal only, writing nothing under `guidelines/`**. Two shipped requirements
force that shape: "A relocated rule keeps its statement" forbids removing a block whose statement exists nowhere,
and the six documents the statements live in are each in flight as their own change, so an entry-document change
that edited them would either conflict or pre-empt their cut. A block whose statement is found in no guideline stays
in place and is recorded as a rule without an owner, which is the third clause of the same requirement. **B — move the routing table's prices into the documents:** rejected; a spec requirement prices
the row where the route is documented. **C — leave it:** under the bound today, and the diet is what buys the
headroom the six changes need.

### D7 — The land order, and what it does to the four open changes

`guideline-layers-mechanism` lands first, then `guideline-backlog-layers`, then the other five document changes;
only then do the four loop changes have a stable base. The mechanism is landed *first* because two of the four edit
files this change touches: `finding-shape-at-filing` edits `AGENTS.md`, and `loop-consumes-a-prepared-change` edits
`tools/loop/prompts/reviewer.md`. Neither is edited here and neither changes its meaning — they **rebase**: the
diet moves text out of `AGENTS.md`, and the reviewer prompt's item 7 changes what an input is. Every `§N`
citation the four write stays valid, which is the whole reason D1 keeps the numbers still. Recorded here rather
than in the four, because this change owns the order and editing four unimplemented proposals to say "wait" costs
four rebases later.

**Scope of that guarantee.** "No citation is rewritten" holds for this change only, which moves no section's
file; a cut that promotes a numbered section into a new top-level document — D1's rejected option C, which
`guideline-maprendering-layers` and `guideline-ui-layers` nonetheless took without renumbering — moves that
section's *file*, and so must retarget the citations that named the section it moved.

### D8 — What fails CI on landing, and what only files a condition

The single most important decision in this change: a guard that turns CI red on the tree it lands on is not a
guard, it is an outage. The split, and the reason:

| guard | on landing | why |
|---|---|---|
| a companion anchor no section carries | **CI failure** (`check-doc-routes.sh` exits 1) | no companion exists on the current tree, so it is green today and stays green until a companion is written wrong |
| a rule reference that does not resolve | **CI failure** | derived from the 5 hand-written names, all distinct and all in one document |
| one rule name in two documents (or twice in one) | **CI failure** | the 5 hand-written names are distinct; a duplicate is the defect, and the tree has none |
| an unrouted normative document | **CI failure** (already is) | unchanged behaviour |
| a glob matching no tracked path | **CI failure** | the map ships with globs that match today's tree |
| a named rule no glob reaches | **CI failure** | the map ships reaching the 5 names |
| a document past the document bound | **condition**, filed by the sensor row | 5 documents carry this condition on landing — that is the *point*: the six document changes retire them. The bound is **32 000** after the apply-phase retune (48 000 when this table was written) |
| a section past 12 000 bytes | **condition** (already is) | 8 sections carry it today |
| a dated figure / tally / `### Why …` inside a normative document | **condition**, filed per file and line | present in the tree today; a one-off occurrence files nothing, because the row is `consecutive: 2` |

Consequence for verification: the change's own landing run must show `check-doc-routes.sh` and
`check-rule-routes.sh` **exiting 0** on the pre-migration tree, and the metric printing exactly 5 document
conditions and the purity conditions it finds. A green CI with red conditions is the intended end state of this
change, and the proof that the guards are fitted to the tree rather than to the migration.

### D9 — Threading, lifecycle and the shell contract

Nothing in this change runs inside the application: no dispatcher, no coroutine, no host thread, no surface, no
lifecycle owner. `guidelines/Design.md` §4's threading rule has no subject here, and the three new tools are
read-only text readers in the shape their siblings already use — no build, no device, no network, exit 0 when
every reference resolves, 1 when one does not, 2 on a usage or layout error. The sensor condition runs where its
siblings run: the registry's cadence, `file+close`, on the second consecutive measurement.

### D10 — Judgement stays with the reviewer

Naming every rule in a section is not decidable from text: "is this sentence normative?" has no token. So no case
in this change tries. The pipeline is:

```
mechanical (a case, every run)        judgement (one round, one change)
  anchor resolves                       a normative sentence with no name
  a name is unique                      a name that lies about its rule
  a glob matches something              a rule the map reaches that does not govern the diff
  a document is under its bound
  no rationale re-entered the layer
```

The reviewer sees the derived rule set *and* the section, which is what makes the last column possible at all —
it is the sharpening of `tools/loop/prompts/reviewer.md` item 7, and the reason the reading set is rules rather
than documents.

### D11 — Verification

- **Offline cases**: `tools/check-rule-routes-selftest.sh` in a scratch tree (one per refusal and one pass per
  glob/rule invariant); the route check's own self-test gains the companion cases; both need no build, no device,
  no network.
- **Mutations**: `tools/check-rule-routes-mutations.json` and an entry in `tools/check-doc-routes-mutations.json`,
  run by the project's own mutation harness, so a refusal that stops firing is caught.
- **The no-renumber check**: the set of `## ` heading numbers per document is compared before and after, by the
  change that makes the cut, and recorded in that change's evidence — a comparison, **not a case**: the only
  baseline is the cutting change's own record, and a case may not read a change's directory. The delta's scenario
  carries `Case: none` for exactly this reason, and the part of the claim a later tree *can* assert is the split's
  own number retention, which `routed-read#numbers` exercises — a split keeps its number and its citations.
- **No device measurement applies**, and the change says so instead of implying one: no UI, rendering, GPS, native
  or Android Auto path is touched. There is no pixel to read and no diagnostics line to print.
- **`revert-check` list**, one mutation per new invariant: (1) write a companion anchor naming a section its base
  document lacks — the route check must fail naming the anchor; (2) state one rule name in two documents — the
  route check must fail naming both; (3) name a glob under which no file exists — `check-rule-routes.sh` must fail
  naming the glob; (4) put a `(measured …)` figure inside a normative document — the metric must print the
  condition with its line. Each restores, then re-runs green with `-PforceTests --no-build-cache`.

## Risks / Trade-offs

- **The purity condition produces false positives.** A rule that must quote a command, or a normative document
  that legitimately carries a number, is reported. → The row is `consecutive: 2`, so a one-off files nothing; the
  condition names file, line and token, so the second occurrence is read in one look; and D8 keeps it out of CI.
- **The six document changes are what actually shrink the tree.** This change alone removes no byte from a
  guideline. → Filed as conditions, not promised: the sensor row carries the identity, the bound closes it, and
  each document change is a `docs-` change whose acceptance criterion is its own document's condition clearing.
- **Rule names are English prose and will be renamed.** A rename breaks every reference to it. → Accepted, and
  the reason there is no identifier: the check reports the stale reference with the names the section does carry,
  and `tools/issues/resolve-stale.sh` already repairs the tracker's bodies.
- **The glob map is a second routing table.** Two tables can disagree. → They answer different questions (where a
  reader is sent vs which rules a diff obeys) and only the map is derived from paths; the map's rot is a case, and
  a disagreement between the two is a review note rather than a contradiction.
- **Two of the four open changes must rebase.** → Named in D7 with the files; the addresses they cite do not move,
  so the rebase is textual in `AGENTS.md` and `reviewer.md` only.
- **The derived reading set can under-report.** A glob that is too wide sends a reviewer to rules that do not
  govern the diff, and one too narrow hides a rule. → A too-wide glob is bounded by the section cost rule (a
  reading set of routed sections is at most 12 000 bytes each), a too-narrow one is what the "a rule no glob
  reaches" case catches at the *rule* level, and the reviewer's judgement (D10) is the only remaining gap — stated,
  not papered over.

## Migration Plan

1. Land this change: the guards, the bounds, the map, the prompt, the `AGENTS.md` diet. CI green; 5 document
   conditions open.
2. `guideline-backlog-layers` retires the `Backlog.md` condition and unblocks the four loop changes.
3. `guideline-build-layers`, `guideline-ui-layers`, `guideline-maprendering-layers`, `guideline-design-layers`,
   `guideline-small-docs-layers` retire the remaining conditions, one document each, in that order (largest
   first, so the tree shrinks where the bound is most violated).
4. Then the four loop changes are implemented on a stable base.

**Rollback**: revert the commit. The companions do not exist yet on this change's side; the registry row is one
object; `AGENTS.md`'s diet is nine paragraphs that return to it. Nothing consumes the derived reading set except
the reviewer prompt, so a half-landed state degrades to today's behaviour instead of breaking a run.

## Open decisions

none — every decision above is settled, and the one that was open during exploration (whether all nine documents
are migrated inside the core change) is settled in the proposal's "Not in this change": six changes of their own,
because naming every rule across 646 144 bytes is not one change and would hold the four loop changes behind it.
