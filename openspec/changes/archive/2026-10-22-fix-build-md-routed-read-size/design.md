# Design — fix-build-md-routed-read-size

## Root cause

The routing table routes to a **section**, and `guidelines/Build.md`'s twelve `## ` sections are its routing
unit; four of them accumulated several questions each, so one routed read is 22 053–31 568 bytes and the table
has no smaller unit to name. There is no second mechanism: the sections are large because every rule was added
in place, and the map's row for a build question pointed at three of them at once.

## The ruler

Every number here, in `Build.md`'s four index paragraphs, in `AGENTS.md`'s rows and in `evidence/red-probe.sh`
comes from one ruler: **a section's cost is the byte count of its lines from the `## ` heading through the line
before the next `## ` heading** — `sed -n '<heading>,<next heading - 1>p' guidelines/Build.md | wc -c`. The
document is ASCII, so bytes and characters agree here; the requirement says *bytes* for that reason. The whole
table is one command, `bash evidence/red-probe.sh --bound 0 bound`, which prints every section with its number.
The `kB` figures in `AGENTS.md` are bytes/1000 at one decimal, and every row says so.

## The single fix

Split the four offenders into lettered sub-sections under their **unchanged** numbers — `§2a–§2e`, `§4a–§4f`,
`§6a–§6h`, `§10a–§10j` — and re-point the three `AGENTS.md` rows at them. Each parent keeps its heading and its
own text and gains one index paragraph that names its sub-sections and their measured cost, so a bare parent
number (`§10`) that a guideline, a skill or an archived change already writes still resolves and now reads as
the index.

This is the owner's recorded option 2 (`ITEM_DECISION`, 2026-10-22T14:16:19Z) implemented as one mechanical
edit: no sentence of the four bodies is rewritten. The split promotes 9 `###` headings to `##` (5 in §2, 4 in
§10), retargets 20 lines inside `Build.md` whose only change is a `§N` reference, re-points 3 `AGENTS.md` rows,
and retargets the one `Build.md`-section reference in the `AGENTS.md` prose and one in
`.pi/skills/run-tests/SKILL.md`.

## Measured effect

```
section                       before    after, largest sub-section
§2  Common behavior           23 235    5 238 (§2c)
§4  Result evaluation         23 380    5 275 (§4f)
§6  Test constraints          22 053    5 271 (§6b)
§10 On-device evidence        31 568    6 841 (§10i)

Build.md                      137 276   140 582  (+3 306)
AGENTS.md's build row             269       839  (+570)
AGENTS.md                      24 241    25 985  (+1 744)
```

The file grows and the routed read shrinks — and both are stated, because `AGENTS.md` is the document every
session reads whole: the change raises that floor by 1 744 bytes (~450 tokens) by naming sub-sections and their
costs, and lowers the read a build question costs from 23 235 bytes (§2) or 23 380 (§4) to at most 5 275 — the
worst sub-section either of those rows names — or 6 841 anywhere in the four split sections. The sub-section
maximum for the whole file is section 7 at 8 877 bytes, which this change does not touch.

## Blast radius

- **Every `Build.md §N` citation in the tree still resolves** — the numbers are unchanged. Measured on both
  sides with `grep -rnoE 'Build\.md[` ]*§[0-9]+[a-z]?' AGENTS.md guidelines tools .pi/skills openspec/specs
  openspec/config.yaml PORTING.md`: 70 references in 29 files at the base and 70 in 29 after the change. Of
  those, 20 lines inside `Build.md` are retargeted to the sub-section that now holds the text they named, as
  are 3 `AGENTS.md` rows, one `AGENTS.md` prose sentence and one line of `.pi/skills/run-tests/SKILL.md`; the
  rest keep pointing at the parent, which is now the index of what they mean.
- **`tools/check-doc-routes.sh` needs no change**: it already accepts a letter-suffixed number
  (`^[0-9]+[a-z]?\.`) because `MapRendering.md` §15a exists, and it reads a bare `§N` as the parent. It reports
  `all 152 section references resolve, all 4 skill references resolve` after the change (120 at the base — the
  new letters are additional references it now resolves). Its self-test is untouched, so no fixture moves.
- **The route check is the gate for these paths** (`tools/loop/loop.conf`, `LOOP_GATE_SUITES`: `*.md` and
  `AGENTS.md` → `tools/check-doc-routes.sh`). No compiled path is touched, so no Gradle task is owed.
- **The new contract is bounded to `guidelines/Build.md`**, deliberately: `UI.md` §1 (24 896 B) and §8
  (19 334 B), and `MapRendering.md` §1 (22 617 B), are routed and over the threshold, and this change does not
  split them. Widening the requirement to "a section a route names" and reading one document would have
  shipped a requirement the tree violates at merge; issue `#197` owns the general sensor and the documents it
  would then have to convict.

## Alternatives the evidence eliminated

- **Move the measurements into a companion document, keeping `§N` stubs** (the owner's option 1). Eliminated by
  the decision: only ~5 % of sections 4, 6 and 10 separates as dated lines, the rest is the evidence clause inside each
  rule bullet, so it would rewrite ~100 kB of prose and reverse `AGENTS.md`'s "rules live next to the
  measurement that proved them". It stays a follow-up the owner asked for.
- **Keep the documents and only state and bound each row's cost** (the owner's option 3). Eliminated by the
  decision: it buys no relief today. (The rows do now state their cost — as a by-product of option 2, not as
  the mechanism.)
- **A shipped check that bounds every routed section** (issue `#197`). Eliminated here: #197 owns the sensor
  row (`tools/sensors/metrics/context-budget.sh`) and carries no `agent/eligible`, so building it inside this
  item would ship an unauthorised capability and duplicate a tracked one. The bound lives in the requirement
  and in the probe; the sensor is #197's.
- **A new numbering scheme** (renumber the sections to `2.1`, `2.2`, …). Eliminated because a `§N` is a citation that
  archived changes, issue bodies and skills still quote: the decision's own constraint is that the numbering
  stays stable.
- **Splitting `UI.md` and `MapRendering.md` in this change.** Eliminated as scope: they are two more documents
  with their own routings and their own citations, the recorded decision names `Build.md`'s sections, and #197
  is the item that decides whether the general bound is enforced at all.

## Rollback

Revert the commit. The four sections return to their single headings, the three `AGENTS.md` rows return to
sections 2, 4, 6 and 10, the probe's three red cases fail again with the messages quoted in `proposal.md`, and
`routed-read#numbers` keeps passing (it is the counter-case that the numbers survive either way).

## The invariant and its falsification

Four checks, one mutation each (`guidelines/Build.md` §4f, "One mutation per revert check"), each a mutation of
**production content** rather than of the probe's threshold, each with its own log:

| case | mutation | the case said | log |
|---|---|---|---|
| `routed-read#verbatim` | one sentence of §6f reworded in place | `1 line(s) of Build.md are rewritten rather than moved`, naming that line | `evidence/revert-check-verbatim.log` |
| `routed-read#route` (a) | the build row reverted to the bare parents | `the routing table names no sub-section of: §2a §4a` | `evidence/revert-check-route.log` |
| `routed-read#route` (b) | the same row keeps its sub-section names and loses the `kB` cost it states | `1 row(s) name a lettered Build.md sub-section but state no cost: [build, run, force a rerun, …]` | `evidence/revert-check-route.log` |
| `routed-read#bound` | `§10a–§10j` demoted to `###`, i.e. section 10's single-heading shape restored | `1 section(s) of Build.md exceed 12000 bytes: §10=32707` | `evidence/revert-check-bound.log` |

Every mutation is restored and the restored tree re-run green — `evidence/revert-check.log` ends with the
`## all four cases, restored` block (`tests=4 failures=0`), which is the run that covers the merged content.
