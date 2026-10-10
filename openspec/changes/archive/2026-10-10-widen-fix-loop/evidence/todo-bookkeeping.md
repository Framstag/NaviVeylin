# `TODO.md` bookkeeping — task 6.5

## Filed

**§172** — a skill document has no owner for "one normative home": `documentation-ownership` bounds
`AGENTS.md` and `guidelines/`, never `.pi/skills/`, which is why `SKILL.md` had grown to 479 lines with its
copy-out rule stated five times. `category: specs-and-process`, `class: improvement`, `status: open`, with the
`specs-and-process — improvement` Clusters row regenerated to carry the new id.

## Repaired: a real id collision, found by this task's own structure check

The check "no id collision" failed on the first run: **two `## 163.` sections**. It pre-dates this change —
`git show HEAD:TODO.md` contains zero `id:** 163` lines, so the collision arrived with the uncommitted WIP:

| id | entry | status in the file |
|---|---|---|
| §163 | "A cleartext repository download has no integrity protection…" (`data-and-maps`, improvement) | open |
| §163 | "The FREE_DRIVE ongoing notification showed a fallback road…" (`ui`, improvement) | `fixed-by 2026-10-10-fix-free-drive-background-liveness` |

Both trees were at §162 and each filed a §163 — the exact failure the `fix-loop` skill's worktree rule warns
about ("allocate new §ids from the max over **both** trees… otherwise the merge produces two sections per id",
which is also how §79 → §119 happened). The `fix-free-drive-background-liveness` change is archived in this
tree (`openspec/changes/archive/2026-10-10-…`), so the second entry is the parallel tree's record.

**Repair, and why this direction.** The cleartext entry keeps §163 and the FREE_DRIVE record became **§173**:

- `guidelines/Regulatory.md:172` — a live normative document — cites `TODO.md` §163 for the cleartext gap, and
  the archived change `2026-10-09-allow-lan-http-map-repository` records §163 as the entry it filed. Renumbering
  that side would require a guideline edit.
- The FREE_DRIVE entry is cited by id nowhere: the archived change's artifacts contain **no** `§163` (their `§N`
  hits are guideline sections §1 §3 §4 §7 §13 §46), and inside `TODO.md` the only `§163` reference was the
  `data-and-maps — improvement` Clusters row. Its `ui — improvement` row now carries §173.
- The entry is `fixed-by` (a closed record), so nothing awaits it.

A §-repair touches another session's record, so it is recorded here rather than done silently. Nothing else in
the file moves.

## One slip, caught by the same check

The first insert of §172 used an `oldText` that included the following `## 163.` heading and did not restore it,
leaving `--- — the declared size and CRC-32 travel…`. The structure check ran immediately afterwards and the
heading was restored from its own line-60 text; `## 163.` is present exactly once now. The lesson is the
skill's: the check is worth running because the edit that breaks it looked correct.

## Structure checks, final

```
headings (^## ):            95
metadata lines (**id:**):   95     -> equal
heading -> metadata pairing: 0 mismatches (each heading's own id on the next line)
duplicate ids:              none
§163 / §172 / §173:         1 / 1 / 1
max id:                     173
```
