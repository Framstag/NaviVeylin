# Notes for the review

## NOTE 1 — a follow/GPS parameter table lives in `MapRendering.recipes.md`

`guidelines/MapRendering.recipes.md` now holds exactly one section, `## Parameter Overview`, a table of
follow/GPS constants (`GPS_DEDUPE_MS`, `GPS_FOLLOW_RENDER_INTERVAL_MS`, `centerSmoothMaxJumpM`,
`COURSE_*`, `MIN_BEARING_DELTA_DEG`, `MAX_ANGLE_RATE_DEG_PER_RENDER`, `ANGLE_EPSILON_RAD`). Those are
the follow path's numbers, not the pipeline's, so the table arguably belongs beside `guidelines/MapFollow.md`.

This cut does **not** decide that. Moving it would be more than name-only (a new base document's
companion, a new anchor, an AGENTS.md row). The table was left where it is, unanchored, and its subject is
recorded here so the next reader does not have to rediscover it.

The empty `## §20 Label wrapping — the parameter table` heading that used to introduce it was deleted:
its region held no text, and its title claimed a §20 parameter table that the next line contradicts.
Deleting it removes the claim and leaves `## Parameter Overview` a legal unanchored companion section —
the anchor rule binds `## §N` headings, not every heading. `AGENTS.md`'s row names
"Parameter Overview" as heading text, not as an anchor, so it still resolves.

## NOTE 2 — `guidelines/Backlog.md:391`'s premise is stale in two ways

The repaired line still reads:

> …refuses a bare number that document uses twice — the reason a `§14` of `MapFrames.md` is written with
> its heading text.

1. `MapFrames.md` carries a single `## 14.`, so "a bare number that document uses twice" is not a hazard
   there.
2. Its premise was already stale in the pre-cut tree: `MapRendering.md` carried **one** `## 14.` too —
   the duplicate was resolved when the Android Auto renderer moved to `MapCar.md`.

The pointer is repaired name-only; the sentence is left for `guideline-backlog-cut`, which owns that
document. A sentence rewrite would put two changes' judgement in one line.

## NOTE 3 — the permitted file set, amended during the run

`guidelines/MapStyling.recipes.md` does not exist, and §20's recipe-layer anchor had no in-set home: left
in `MapRendering.recipes.md` it would have been an anchor naming a section its own base document no
longer carries. The supervisor amended the set to include `guidelines/MapStyling.why.md` (and a new
`MapStyling.recipes.md`, if the material were procedure).

The material was placed by its kind, per the amended instruction: the bullet states a **figure**
(`1.58.0`), **when** it was measured (2026-10-26) and **how** (`pkg-config --modversion pangocairo`
against a triplet), which is a dated measurement and therefore the WHY layer. It went to
`guidelines/MapStyling.why.md` as `## §20 Label wrapping — the pango measurement`, and **no
`MapStyling.recipes.md` was created** — nothing remained for the procedure layer, and a companion is not
created to give an anchor a home.

`guidelines/MapRoute.md` has no companion: no anchor anywhere named §19. `guidelines/MapFrames.why.md`
was created because the §13 anchor and `## Known Pitfalls (Regression Checklist)` are delivery material
that moved with their section.
