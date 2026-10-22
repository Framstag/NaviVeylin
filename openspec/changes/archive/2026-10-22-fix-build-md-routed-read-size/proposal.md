# fix-build-md-routed-read-size

## Why

**The condition, verified in the tree** at base `3180fff` (2026-10-22):

```
$ wc -c guidelines/Build.md
137276 guidelines/Build.md
$ # per `## ` section, body bytes to the next `## ` heading:
31568  section 10  On-device evidence for Android Auto / AAOS (host-crash triage)
23380  section 4   Result evaluation
23235  section 2   Common behavior
22053  section 6   Test constraints
 8875  section 7   Code coverage
 7292  section 9   License compliance
```

`AGENTS.md`'s routing table names a **section** as the unit of a routed read, so the four sections above are
one routed read each: a worker that follows the map for a build question pays 22–32 kB (5.5–7.8 k tokens) for
one rule, against the 17 334 tokens a child's first response reports as its whole start-of-session prompt. The
table's own build row named sections 2, 3 and 4 together: 48 311 B of prose for one question.

**Root cause, one sentence.** The routing table routes to sections and `guidelines/Build.md`'s twelve sections
are its routing unit, so the four sections that accumulated several questions have no smaller unit the table
can name, and a reader who follows the map takes the whole accumulation.

**The owner's recorded decision settles the shape** (`ITEM_DECISION`, 2026-10-22T14:16:19Z): *option 2 now —
split each offender into lettered sub-sections (§2a–2e, §4a–4f, §6a–6j, §10a–10f) and route rows at those,
which moves no sentence and loses nothing; and follow issues for option 1*. Option 1 (move the measurements into
a companion document) is **not** in this change; option 3 (state and bound each row's cost, keeping the
documents) is **not** in this change either. What ships is the split and the re-routed rows.

**Red test** (`evidence/red-on-head.xml`, `evidence/red-probe.sh`, on the base tree at
2026-10-22T16:55:15Z): the probe's four cases read `guidelines/Build.md` and `AGENTS.md`, and three fail on HEAD:

| case | red on HEAD |
|---|---|
| `routed-read#bound` | `4 section(s) of Build.md exceed 12000 bytes` — sections 10 (31 568), 4 (23 380), 2 (23 235) and 6 (22 053) |
| `routed-read#numbers` | passes on HEAD — the counter-case: sections 1–12 are present and the route check resolves `all 120 section references` |
| `routed-read#route` | `the routing table names no sub-section of: §2a §4a §6a §10a` |
| `routed-read#verbatim` | `the routing table names no sub-section of` … and `carries 0 lettered sub-sections, fewer than 4` for each of the four offenders |

`tests=4 failures=3` on HEAD. Green after the change (`evidence/green-after-fix.log`,
2026-10-22T16:55:11Z): `tests=4 failures=0`, `all 146 section references resolve`.

**Owning capability and requirement.** `documentation-ownership`, requirement *The routing cost is measured* —
it already requires the cost of a routed read to be measured and recorded where the route is documented; this
delta adds what a routed section must then *be*. The check that a reference resolves is the same capability's
*A route resolves to a section that exists* and stays untouched.

**Owning guideline section.** `AGENTS.md` "Documentation Map" (the routing table this change re-points) and
`guidelines/Build.md` §2a "Agent iteration protocol", whose scope block states what a delta that compiles
nothing owes. The sibling bound — a sensor that measures a routed section's cost for every document — is issue
`#197`, which carries no `agent/eligible` and is therefore not this change's to build.

**Related, not in this change.** `#197` (the bound as a sensor row), and the owner's instruction to file a
follow-up for option 1 (moving the measurements into `guidelines/BuildEvidence.md`, which would need
`AGENTS.md`'s "rules live next to the measurement that proved them" amended for `Build.md`).

## What Changes

- `guidelines/Build.md` — sections 2, 4, 6 and 10 are split into lettered sub-sections under their unchanged
  numbers: `§2a–§2e`, `§4a–§4f`, `§6a–§6h`, `§10a–§10j`. Each parent keeps its heading and its text, and gains
  one index paragraph naming its sub-sections and their measured cost. No sentence of the body is rewritten;
  the only removed lines are four promoted `###` headings and the lines whose sole change is a retargeted
  `§N` reference (18 of them, all inside `Build.md`, all pointing at the sub-section that now holds the text
  they named before the split).
- `AGENTS.md` — the three rows that routed sections 2 and 4, 6 and 10 of `Build.md` now name the lettered sub-sections, with
  the measured per-section cost; the "Agent iteration loop" prose names `§2a` instead of the parent.
- `.pi/skills/run-tests/SKILL.md` — its section-2 reference ("Agent iteration protocol") becomes `§2a`, the section
  that now carries the text.
- `openspec/specs/documentation-ownership/spec.md` — the requirement *The routing cost is measured* is MODIFIED
  with four new scenarios and its two shipped scenarios repeated verbatim.

Not breaking for a reader: every existing citation of sections 2, 4, 6 and 10 in the tree (70 references in 21
files, including `guidelines/*.md`, the skills, the specs and `openspec/config.yaml`) still resolves, because
the parent number is unchanged and now reads as an index of its sub-sections.

## Surface

```
$ grep -rnoE 'Build\.md[` ]*§[0-9]+[a-z]?' AGENTS.md guidelines tools .pi/skills openspec/specs \
    openspec/config.yaml PORTING.md | wc -l
70
$ grep -rlE 'Build\.md[` ]*§(2|4|6|10)([^0-9]|$)' AGENTS.md guidelines tools .pi/skills openspec/specs \
    openspec/config.yaml PORTING.md | wc -l
21
```

surface: 4 cases added, 0 cases rewritten, 0 fixtures realigned — the cases are `evidence/red-probe.sh`'s four,
and no `tools/*-cases.sh` suite asserts a routing-table shape (the route check's own self-test builds its
documents from throwaway fixtures and reads no project document). The references the change retargets are 18
inside `guidelines/Build.md` plus the `AGENTS.md` rows and one skill line; the 70 references in 21 files above
are checked, and left alone because the parent numbers survive. The gate for these paths, per
`tools/loop/loop.conf`'s `LOOP_GATE_SUITES`, is `tools/check-doc-routes.sh` — no compiled file is touched.

## Capabilities

### Modified Capabilities

- `documentation-ownership` — *The routing cost is measured* gains the section shape a routed read must have and
  four scenarios; its two shipped scenarios ("Numbers for representative changes", "An unmeasured saving is not
  claimed") are repeated verbatim.

## Impact

`guidelines/Build.md` (structure only: 4 promoted headings, 4 index paragraphs, 18 retargeted references),
`AGENTS.md` (three routing rows and one prose reference), `.pi/skills/run-tests/SKILL.md` (one reference), and
the change's own artifacts plus `evidence/red-probe.sh`. No `tools/` script, no `openspec/config.yaml`, no Gradle,
CMake, Kotlin, Java or C++ file — so the verification is the route check, the probe, and
`openspec validate <change> --strict`.
