# Post-cut sizes

`wc -c`:

```
30344 guidelines/MapRendering.md
11040 guidelines/MapFrames.md
 3425 guidelines/MapRoute.md
20031 guidelines/MapStyling.md
  978 guidelines/MapRendering.why.md
 4500 guidelines/MapFrames.why.md
 1122 guidelines/MapRendering.recipes.md
 1184 guidelines/MapStyling.why.md
25129 AGENTS.md
```

Every normative document is under the 32 000 B bound.

`bash tools/check-doc-routes.sh --sizes` (the sections this cut produces or keeps):

```
check-doc-routes: section-cost MapFrames.md §12 1748 bytes 21 lines routed
check-doc-routes: section-cost MapFrames.md §13 6298 bytes 78 lines routed
check-doc-routes: section-cost MapFrames.md §14 2459 bytes 22 lines routed
check-doc-routes: section-cost MapRendering.md §1 687 bytes 12 lines routed
check-doc-routes: section-cost MapRendering.md §1a 6022 bytes 67 lines routed
check-doc-routes: section-cost MapRendering.md §1b 3120 bytes 38 lines routed
check-doc-routes: section-cost MapRendering.md §1c 1276 bytes 17 lines routed
check-doc-routes: section-cost MapRendering.md §1d 10282 bytes 110 lines routed
check-doc-routes: section-cost MapRendering.md §1e 2262 bytes 25 lines routed
check-doc-routes: section-cost MapRendering.md §2 2780 bytes 33 lines routed
check-doc-routes: section-cost MapRendering.md §4 1468 bytes 18 lines routed
check-doc-routes: section-cost MapRendering.md §5 1148 bytes 17 lines routed
check-doc-routes: section-cost MapRendering.md (unnumbered) 642 bytes 13 lines not-routed
check-doc-routes: section-cost MapRoute.md §19 3055 bytes 37 lines routed
check-doc-routes: section-cost MapStyling.md §20 3036 bytes 35 lines routed
```

Largest section anywhere in the set: **`MapRendering.md` §1d 10 282 B** (< 12 000). Then
`MapFrames.md` §13 6 298 B and `MapStyling.md` §15 5 081 B.

## Heading numbers — no number moved

| document | `## <n>.` headings |
|---|---|
| `MapRendering.md` | 1, 1a, 1b, 1c, 1d, 1e, 2, 4, 5 |
| `MapFrames.md` | 12, 13, 14 |
| `MapRoute.md` | 19 |
| `MapStyling.md` | 15, 15a, 16, 16a, 16b, 17, **20** |

The documents that hold a piece of the cut document union to
`{1, 1a, 1b, 1c, 1d, 1e, 2, 4, 5, 12, 13, 14, 19, 20}` — **equals the baseline set** recorded in
`pre-cut.md`. `MapStyling.md`'s other numbers are its own and predate the cut.

## Pipeline cluster, before and after

| | before | after |
|---|---|---|
| §1 | 687 | 687 |
| §1a | 6 022 | 6 022 |
| §1b | 3 105 | 3 120 (+15: the `MapFrames.md` name added to its `§13` cross-reference) |
| §1c | 1 276 | 1 276 |
| §1d | 10 282 | 10 282 |
| §1e | 2 262 | 2 262 |
| §2 | 2 780 | 2 780 |
| §4 | 1 468 | 1 468 |
| §5 | 1 142 | 1 148 (+6: the blank line and the `---` that now close the document's last rule section) |
| **cluster** | **29 024** | **29 045** |
