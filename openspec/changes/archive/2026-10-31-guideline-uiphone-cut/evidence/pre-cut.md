# Pre-cut baseline — guidelines/UIPhone.md

`wc -c guidelines/UIPhone.md` → **47926**.

## Section cost table (`bash tools/check-doc-routes.sh --sizes | grep 'UIPhone\.md'`, pre-cut)

```
check-doc-routes: section-cost UIPhone.md §5 1203 bytes 18 lines routed
check-doc-routes: section-cost UIPhone.md §6a 6133 bytes 78 lines routed
check-doc-routes: section-cost UIPhone.md §7 1704 bytes 25 lines routed
check-doc-routes: section-cost UIPhone.md §7a 3393 bytes 43 lines routed
check-doc-routes: section-cost UIPhone.md §7b 7978 bytes 91 lines routed
check-doc-routes: section-cost UIPhone.md §8 1078 bytes 17 lines routed
check-doc-routes: section-cost UIPhone.md §8b 3301 bytes 41 lines routed
check-doc-routes: section-cost UIPhone.md §8c 3493 bytes 44 lines routed
check-doc-routes: section-cost UIPhone.md §8a 1869 bytes 27 lines routed
check-doc-routes: section-cost UIPhone.md §10a 2413 bytes 35 lines routed
check-doc-routes: section-cost UIPhone.md §11 3403 bytes 43 lines routed
check-doc-routes: section-cost UIPhone.md §12 2094 bytes 26 lines routed
check-doc-routes: section-cost UIPhone.md §13 655 bytes 9 lines routed
check-doc-routes: section-cost UIPhone.md §14 763 bytes 11 lines routed
check-doc-routes: section-cost UIPhone.md §15 975 bytes 14 lines routed
check-doc-routes: section-cost UIPhone.md §16 1041 bytes 15 lines routed
check-doc-routes: section-cost UIPhone.md §17 985 bytes 16 lines routed
check-doc-routes: section-cost UIPhone.md §18 4503 bytes 53 lines routed
check-doc-routes: section-cost UIPhone.md §19 289 bytes 5 lines routed
```

## Heading-number set (baseline)

`grep -n '^## ' guidelines/UIPhone.md` → numbers:
`5, 6a, 7, 7a, 7b, 8, 8b, 8c, 8a, 10a, 11, 12, 13, 14, 15, 16, 17, 18, 19`

## STEP 0 — the two clusters, summed from `--sizes`

| cluster | sections | bytes |
|---|---|---|
| phone **map** | §7 1704 + §7a 3393 + §7b 7978 + §8 1078 + §8a 1869 + §8b 3301 + §8c 3493 + §10a 2413 + §11 3403 | **28 632** |
| phone **surfaces** | §5 1203 + §6a 6133 + §12 2094 + §13 655 + §14 763 + §15 975 + §16 1041 + §17 985 + §18 4503 + §19 289 | **18 641** |

The brief's cross-check gave the surface half as **19 294**. That figure is the surface sections (18 641) **plus the
653-byte preamble** (lines 1–16, which `--sizes` excludes because it counts from the first `## ` heading). The sum
closes: 28 632 + 18 641 + 653 = 47 926 = `wc -c guidelines/UIPhone.md`. The brief's figures agree with the
measurement once the preamble is placed in the surface half; no material disagreement.

## Baseline conditions

`document-size` (scope `normative-document`, bound 32 000, greater-than): **4** —
UIPhone.md 47 926, MapRendering.md 46 751, UI.md 45 324, Backlog.md 35 890.

## Baseline checks

- `bash tools/check-rule-routes.sh` → `every glob matches the tree and all 575 named rule(s) are reached` (0)
- `bash tools/check-doc-routes.sh` → `all 307 section references resolve, all 2 skill references resolve` (0)
