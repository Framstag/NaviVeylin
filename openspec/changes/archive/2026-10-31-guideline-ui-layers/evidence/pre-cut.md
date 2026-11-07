# Pre-cut baseline — `guidelines/UI.md`

Captured before any edit of the change `guideline-ui-layers`. Command and its observed line:

```
$ bash tools/check-doc-routes.sh --sizes | grep 'section-cost UI'
check-doc-routes: section-cost UI.md §1 26015 bytes 258 lines routed
check-doc-routes: section-cost UI.md §2 454 bytes 13 lines routed
check-doc-routes: section-cost UI.md §3 3428 bytes 45 lines routed
check-doc-routes: section-cost UI.md §3a 5103 bytes 74 lines routed
check-doc-routes: section-cost UI.md §3b 1823 bytes 31 lines routed
check-doc-routes: section-cost UI.md §3c 2763 bytes 36 lines routed
check-doc-routes: section-cost UI.md §4 691 bytes 14 lines routed
check-doc-routes: section-cost UI.md §5 826 bytes 16 lines not-routed
check-doc-routes: section-cost UI.md §6 1051 bytes 18 lines routed
check-doc-routes: section-cost UI.md §6a 6043 bytes 80 lines routed
check-doc-routes: section-cost UI.md §6b 4873 bytes 67 lines routed
check-doc-routes: section-cost UI.md §7 1455 bytes 25 lines routed
check-doc-routes: section-cost UI.md §7a 13867 bytes 166 lines routed
check-doc-routes: section-cost UI.md §8 20683 bytes 256 lines routed
check-doc-routes: section-cost UI.md §8a 2528 bytes 40 lines routed
check-doc-routes: section-cost UI.md §9 4622 bytes 65 lines routed
check-doc-routes: section-cost UI.md §10 8302 bytes 122 lines routed
check-doc-routes: section-cost UI.md §10a 2218 bytes 35 lines routed
check-doc-routes: section-cost UI.md §11 4000 bytes 51 lines routed
check-doc-routes: section-cost UI.md (unnumbered) 1728 bytes 20 lines not-routed
```

Total file: `112988` bytes, 1445 lines (`wc -c guidelines/UI.md`).
Document-size condition: `guidelines/UI.md is 112988 bytes ... past the row's bound of 48000 bytes`
(`SENSORS_ROW='{"scope":"normative-document"}' bash tools/sensors/metrics/doc-context.sh`).
Purity conditions: 8 (lines 50, 60, 178 `### Why`; 360, 451, 635 `issue #`; 890, 928 `measured`).

## Heading-number set (`## ` headings)

```
1  2  3  3a  3b  3c  4  5  6  6a  6b  7  7a  8  8a  9  10  10a  11   + "Keeping this document honest" (unnumbered)
```

## The three `### Why …` headings in §1

```
### Why the phone is not locked during a car session
### Why favorite, group and starred reordering are phone-only
### Why the license list is phone-only
```

## The parity rule, exact text (line 14–26)

```
## 1. Cross-variant UI parity (general rule)

Source: spec `cross-variant-ui-parity` (change `align-details-actions-and-shared-data`).

- Similar UI elements in the phone and Android Auto variants SHALL use the same
  **labels** and the same **visual style** wherever the platform constraints of
  both variants allow it.
- When a car-host constraint forces a difference (e.g. rows instead of buttons,
  glyph marks instead of icons), deviate **only as much as required** and keep
  the **same label**.
- Primary/secondary/destructive hierarchy must be reflected in both variants'
  styling where the platform allows.
```

## The arithmetic that forced the design amendment

The design's D1 chosen set was `UI.md = §1, §2, §3, §3a, §3b, §3c, §4, §6, §6b, §9, §10 +`
maintenance. Measured by concatenating those sections with the 515-byte prelude: **61 368 B**.
The phone set (`§5, §6a, §7, §7a, §8, §8a, §10a, §11`): **52 135 B**. Two documents under 48 000
hold at most 96 000 B, so ≥ 17 500 B must leave the normative documents; the content identifiable by
D4's marker list (`measured`, `Measured`, `issue #`, `TODO.md §`, `owner finding`, `reported on
device`) is only 850 B in the kept set and 1 741 B in the phone set. The amendment applied is
`design.md` D1/D4 (recorded there); §1's decisions record (10 303 B) and §1's phone sub-sections
(11 753 B) move, which is what makes the bound reachable.
