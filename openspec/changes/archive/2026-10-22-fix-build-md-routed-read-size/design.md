# Design — fix-build-md-routed-read-size

## Root cause

The routing table routes to a **section**, and `guidelines/Build.md`'s twelve `## ` sections are its routing
unit; four of them accumulated several questions each, so one routed read is 22 053–31 568 bytes and the table
has no smaller unit to name. There is no second mechanism: the sections are large because every rule was added
in place, and the map's row for a build question pointed at three of them at once.

## The single fix

Split the four offenders into lettered sub-sections under their **unchanged** numbers — `§2a–§2e`, `§4a–§4f`,
`§6a–§6h`, `§10a–§10j` — and re-point the three `AGENTS.md` rows at them. Each parent keeps its heading and its
own text and gains one index paragraph that names its sub-sections and their measured cost, so a bare parent number (section 10) that a
guideline, a skill or an archived change already writes still resolves and now reads as the index.

This is the owner's recorded option 2 (`ITEM_DECISION`, 2026-10-22T14:16:19Z) implemented as one mechanical
edit: no sentence of the four bodies is rewritten, only 18 intra-document `§N` references inside `Build.md` (and
two in `AGENTS.md`'s prose) are retargeted to the sub-section that now carries the text they named.

## Measured effect

```
section          before    after   the sub-section maximum
section 2  Common behavior        23 235   —   5 236 (§2c)
section 4  Result evaluation      23 380   —   5 273 (§4f)
section 6  Test constraints       22 053   —   5 269 (§6b)
section 10 On-device evidence     31 568   —   6 839 (§10i)
AGENTS.md's build row     48 311   —   26 341 named, 1 902–5 236 per read
```

The build row's named sections still total 26 341 B, because it names seven of them for seven questions; what
changed is that the row can name *one* of them, and the read a route costs is the section it takes — 5 236 B at
the worst, against 23 235 B for section 2 whole.

The file itself is 4 236 bytes larger (4 index paragraphs and the promoted headings); what it saves is the read
a route costs, not the bytes on disk. The next-largest sections of the file are untouched and unchanged: section 7 at
8 875 B, section 9 at 7 292 B.

## Blast radius

- **Every `Build.md §N` citation in the tree still resolves** — the numbers are unchanged. Measured before the
  edit: 70 references in 21 files (`AGENTS.md`, `guidelines/*.md`, `.pi/skills/*`, `openspec/specs/*`,
  `openspec/config.yaml`, `PORTING.md`), of which 19 (inside `Build.md`) plus the two heading-text references in
  `AGENTS.md` and one in `.pi/skills/run-tests/SKILL.md` are retargeted; the rest keep pointing at the parent,
  which is now the index of what they mean.
- **`tools/check-doc-routes.sh` needs no change**: it already accepts a letter-suffixed number
  (`^[0-9]+[a-z]?\.`) because `MapRendering.md` §15a exists, and it reads a bare `§N` as the parent. Its
  self-test is untouched, so no fixture moves.
- **The route check is the gate for these paths** (`tools/loop/loop.conf`, `LOOP_GATE_SUITES`: `*.md` and
  `AGENTS.md` → `tools/check-doc-routes.sh`). No compiled path is touched, so `LOOP_GATE_COMPILED_CMD` is not
  owed.

## Alternatives the evidence eliminated

- **Move the measurements into a companion document, keeping `§N` stubs** (the owner's option 1). Eliminated by
  the decision: only ~5 % of sections 4, 6 and 10 separates as dated lines, the rest is the evidence clause inside each
  rule bullet, so it would rewrite ~100 kB of prose and reverse `AGENTS.md`'s "rules live next to the
  measurement that proved them". It stays a follow-up the owner asked for.
- **Keep the documents and only state and bound each row's cost** (the owner's option 3). Eliminated by the
  decision: it buys no relief today.
- **A shipped check that bounds every routed section** (issue `#197`). Eliminated here: #197 owns the sensor row
  (`tools/sensors/metrics/context-budget.sh`) and carries no `agent/eligible`, so building it inside this item
  would ship an unauthorised capability and duplicate a tracked one. The bound lives in the requirement and in
  the probe; the sensor is #197's.
- **A new numbering scheme** (renumber the sections to `2.1`, `2.2`, …). Eliminated because a `§N` is a citation that
  archived changes, issue bodies and skills still quote: the decision's own constraint is that the numbering
  stays stable.

## Rollback

Revert the commit. The four sections return to their single headings, the three `AGENTS.md` rows return to
sections 2, 4, 6 and 10, the probe's three red cases fail again with the messages quoted in `proposal.md`, and
`routed-read#numbers` keeps passing (it is the counter-case that the numbers survive either way).

## The invariant and its falsification

`routed-read#verbatim` is the new invariant's guard, and it is falsified in the change's evidence round: rewrite
one sentence of section 6 while splitting it and the case names that line. `routed-read#bound` is falsified by
restoring one offender's single-heading shape (its body returns above 12 000 bytes). `routed-read#route` is
falsified by reverting one `AGENTS.md` row to the bare parent.
