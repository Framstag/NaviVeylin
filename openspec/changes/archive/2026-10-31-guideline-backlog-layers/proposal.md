# `Backlog.md` becomes a rules document with a rationale companion and a procedure companion

## Why

`Backlog.md` is four documents in one file: measured by the ruler the mechanism change defines, it costs
112 888 bytes, of which one section — §7 "The loop" — is 56 602 bytes. That section is the largest single block in
the guideline corpus and it is **not routed**: no row of the routing table names it, so a reader reaches it only by
search, and a session that opens the document to read §6 pays 112 888 bytes for the 2 951 it needed.

Four open changes are written against this document — `shaping-inbox` (6 citations), `change-readiness-gate` (5),
`loop-consumes-a-prepared-change` (8), `finding-shape-at-filing` (9) — and between them they touch §1, §4, §5, §6,
§7, §9 and §10. This change is what makes that reading cheap, and it is the change the mechanism change names as the
one that unblocks them.

By section, what the 112 888 bytes hold:

| section | bytes | routed | what it actually is |
|---|---|---|---|
| §7 The loop | 56 602 | **no** | procedure — phases, prompts, commands, transcripts |
| §9 Checks | 15 785 | yes | normative rules + the command transcripts that exercise them |
| §1 Status, and what this document owns | 7 050 | yes | facts, plus the history of what it used to own |
| §12 The decisions, and why | 5 770 | no | rationale |
| §5 Who may write what | 5 197 | yes | normative |
| §4 The invariants | 4 982 | yes | normative |
| §10 Portability | 3 195 | yes | a pointer plus the commands it runs |
| §6 How an item becomes work | 2 951 | no | normative |
| §2 The participants and the flow | 2 538 | no | procedure |
| §3 What the tracker holds | 2 049 | no | facts |
| §11 Where an agent runs | 1 966 | no | normative |
| §13 Deferred, and why | 1 524 | no | rationale |
| §8 The human lane | 1 502 | no | procedure |
| §14 Keeping this document honest | 1 362 | no | normative |

Two of the four open changes cite §6 and §9 and none of them moves a section number, because this change moves no
number either: the cut takes text out of a section and leaves the section heading in place.

**Acceptance scenario.** After the cut, `Backlog.md` is under 48 000 bytes with every section under 12 000, its
rules are named, its rationale is in `Backlog.why.md`, its procedure is in `Backlog.recipes.md`, and §7 still
exists as an index of sub-sections whose bodies are in the companion — so `Backlog.md §6`, `§7` and `§9` still
resolve for the four open changes.

**Owning guideline.** `guidelines/Backlog.md` §1 "Status, and what this document owns" is the section that states
what this document owns; the layer rule it is being cut under is
`openspec/specs/documentation-ownership/spec.md` "A guideline document carries one kind of text", landed by
`guideline-layers-mechanism`.

**Surface.** Measured with `tools/check-doc-routes.sh --sizes` and `wc -c`:

```
Backlog.md today               112 888 B, 14 sections, §7 unrouted at 56 602 B
document-size conditions it carries              1 (the row lands in guideline-layers-mechanism)
sections past the 12 000 bound                   1 (§9, 15 785 B)
rule names it carries today                      0
```

surface: 0 cases added (the guards land in `guideline-layers-mechanism`, this change only clears conditions),
1 guideline document cut into 3 files, 0 fixtures realigned — `tools/check-doc-routes.sh --sizes | grep Backlog.md`

## What Changes

- **`Backlog.md`** keeps the rules: §1, §3, §4, §5, §6, §10, §11, §14 in place, §9 cut so that its rules stay in
  the document and its transcripts move out, and **§7 becomes an index of `§7a`…`§7e`** — one sub-section per phase
  of the loop, each numbering a heading the base document carries, with the bodies in the companion. §2, §8 and the
  rule tables that are procedure move out.
- **`Backlog.why.md`** — `§12` "The decisions, and why" and `§13` "Deferred, and why" move here whole, and every
  section of the base document contributes its rationale under an anchor of the same number: the history in §1, the
  derivation of the invariants in §4, the case that produced a rule in §9.
- **`Backlog.recipes.md`** — `§7a`…`§7e` (the loop's phases, prompts, commands and their transcripts), §2, §8,
  §9's command transcripts, §10's porting commands, and the tracker recipes.
- **Named rules** throughout the normative layer: `**Rule: <name>**`, each name unique in the document and across
  `guidelines/*.md`, replacing the prose that states an obligation implicitly today.
- **`AGENTS.md`** keeps one sentence plus the route for the `Backlog maintenance` block (1 777 B) that moves into
  this document, and gains the routing row for §7 — a sub-section index no row names is a sub-section a reader
  cannot reach.

No requirement changes: this change moves text and names rules. The layer rule and the guards it is judged by
landed in `guideline-layers-mechanism`; **`skip_specs: true`** is set for that reason.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — no behavior changes. The document's own rules keep their meaning; only their location and their name change.
`documentation-ownership` is unmodified by this change, and `agent-issue-loop` and `backlog-source-of-truth` keep
their text.

## Impact

| path | what changes |
|---|---|
| `guidelines/Backlog.md` | 112 888 B → under 48 000 B; §7 becomes an index of `§7a`…`§7e`; rules named |
| `guidelines/Backlog.why.md` | new — §12, §13 and every section's rationale under a same-numbered anchor |
| `guidelines/Backlog.recipes.md` | new — `§7a`…`§7e`, §2, §8, and the transcripts |
| `AGENTS.md` | the `Backlog maintenance` block reduced to one sentence plus its route; the §7 row added |

No code, no tool, no check, no sensor row and no Android component is touched: this is three text files and one
entry-document block. Scope: **documentation**, neither phone nor Android Auto.

**Rollback path.** Revert the commit: the two companions are new files and every line the base document gave up is
in them, so the revert is a text restore with no state to migrate.

## Not in this change

- **The four loop changes.** They are read against this document and are not edited here; the section numbers they
  cite do not move, and the `guideline-layers-mechanism` design records the land order.
- **The other five documents.** `guideline-build-layers`, `guideline-ui-layers`,
  `guideline-maprendering-layers`, `guideline-design-layers` and `guideline-small-docs-layers` each cut one
  document, and each retires its own condition.
- **A generated sub-section index.** `Backlog.md` §7's index of `§7a`…`§7e` is written by hand here; generating it
  from the companion is worth its own change once three documents carry one.
