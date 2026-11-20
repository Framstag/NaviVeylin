# Design — issue type census

## Open decisions

none

D1 to D3 below are taken; each one is the smaller of two options the proposal could have carried.

## D1 — The census is a report, not a refusal

`check-metadata.sh` gains a sixth per-item statement and writes it to the same channels the other five use
(`--issues`, `--live`, `--json`), but it does **not** set the exit status. The alternatives were a refusal (the item
must carry a type) and a report.

Refusal costs a red run over 60 legacy items from the first day, and the only repair for most of them is a
judgement about a tracker field they never needed: a band, a class and a category already order the backlog. A
refusal that stays red is switched off, and the measure is lost with it. The report keeps the same information —
`untyped: [numbers]` in the JSON, one line per item in human mode — while the number itself becomes the acceptance
figure this change drives to zero for every item whose labels allow a type (D2). Items the rule cannot type stay
reported, which is the honest residue rather than a silence.

The self-check (`check-metadata.sh` with no argument) asserts that the census is wired at all: it runs the
committed untyped fixture through the same per-issue half a tracker goes through, and a report that stops naming
it is a violation (`census/not-wired`) rather than a quiet zero — so removal of the report line is red in the
token-free mode every CI run takes, before any tracker is read (`evidence/mutations.log`, mutation B).

## D2 — The backfill types an item from its labels

The mapping is mechanical, one row per class:

| the item carries | the type written |
|---|---|
| `class/bug` | `Bug` |
| `class/feature` | `Feature` |
| `class/improvement` | `Task` |
| a `parent` naming another item's number, and children of its own | `Epic` |
| nothing else, or an ambiguous class | nothing — reported by D1 |

The type says what the *tracker* calls the item, and the class says what the *project* calls it; the mapping states
where the two vocabularies meet, so 60 decisions become one table. An item whose body lists children but carries no
`parent` is typed `Epic` only when its children are filed with `--parent` in the same pass — the link is what makes
it a container, and a body that merely lists numbers is prose.

`update.sh --type` is the only writer; nothing here re-files an item.

## D3 — The route is one case, and it reads the skill files

The pointer that was missing is invisible to every existing check: the skills are markdown, the writers accept the
flags, and no test reads a skill's prose. The case therefore reads the two files directly —
`.pi/skills/create-issue/SKILL.md` must contain `--type` and `--parent`, and `.pi/skills/update-issue/SKILL.md` must
contain both as well — inside `tools/issues/tests/issue-tooling-cases.sh`, whose environment already carries the
repository root and which runs offline.

A prose assertion is cheap and blunt on purpose: the alternative (a generated documentation index) is a second
mechanism to keep in sync, and what failed here was not the content of the guideline — `guidelines/Backlog.md` §3
documents the axis in full — but the fact that nothing a filer reads pointed at it. One `grep` per skill file is
the smallest thing that makes that failure loud.

## Deferred

- **A type on every new item by construction.** `create.sh` could require the field the way it requires a shape.
  Deferred: a finding whose shape is `cause` has an obvious type (`Bug` for a defect's fix), but which type a
  `change` item carries is the tracker's judgement and would be guessed by the writer, which is the one thing the
  vocabulary gate exists to prevent.
- **The band and the type in one pass.** `issue-metadata`'s classification queue ranks an item without a kind; a
  queue for items without a type is not worth a second pass while D1's report exists.
