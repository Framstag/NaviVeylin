# Pre-cut baseline — guidelines/MapRendering.md

`wc -c guidelines/MapRendering.md` → **46751** (550 lines).

`bash tools/check-doc-routes.sh --sizes | grep 'MapRendering.md'`:

```
check-doc-routes: section-cost MapRendering.md §1 687 bytes 12 lines routed
check-doc-routes: section-cost MapRendering.md §1a 6022 bytes 67 lines routed
check-doc-routes: section-cost MapRendering.md §1b 3105 bytes 38 lines routed
check-doc-routes: section-cost MapRendering.md §1c 1276 bytes 17 lines routed
check-doc-routes: section-cost MapRendering.md §1d 10282 bytes 110 lines routed
check-doc-routes: section-cost MapRendering.md §1e 2262 bytes 25 lines routed
check-doc-routes: section-cost MapRendering.md §2 2780 bytes 33 lines routed
check-doc-routes: section-cost MapRendering.md §4 1468 bytes 18 lines routed
check-doc-routes: section-cost MapRendering.md §5 1142 bytes 14 lines routed
check-doc-routes: section-cost MapRendering.md §12 1748 bytes 21 lines routed
check-doc-routes: section-cost MapRendering.md §13 6298 bytes 78 lines routed
check-doc-routes: section-cost MapRendering.md §14 2459 bytes 22 lines not-routed
check-doc-routes: section-cost MapRendering.md §19 3055 bytes 37 lines routed
check-doc-routes: section-cost MapRendering.md §20 3044 bytes 37 lines routed
check-doc-routes: section-cost MapRendering.md (unnumbered) 519 bytes 10 lines not-routed
```

## Heading numbers (baseline set — the union the cut must preserve)

`grep -n '^## ' guidelines/MapRendering.md`

```
12:## 1. Render Pipeline Architecture
24:## 1a. Follow framing — the anchor applies to the RENDER TARGET (read before touching follow mode)
91:## 1b. The render modes and the path selection
129:## 1c. The overlay stage, the mode switch, the low-zoom risk and the removed helpers
146:## 1d. Marker projection and the overlay viewport
256:## 1e. The follow display contract on phone and Android Auto
281:## 2. Bitmap Lifecycle (CRITICAL — caused "jumps" multiple times)
314:## 4. Render Coalescing / Throttling
332:## 5. Epoch Rules
346:## 12. Front-Buffer Emission
367:## 13. Overrun Display Window (pan and follow)
445:## 14. Rotation Gesture Display-Layer Handoff
467:## 19. Route data — one length for one route
504:## 20. Label wrapping — the bounds the renderer configures
541:## Sections that moved out of this document       (unnumbered)
```

Baseline set: `{1, 1a, 1b, 1c, 1d, 1e, 2, 4, 5, 12, 13, 14, 19, 20}`.

## The clusters the proposal prices, summed here from the table above

| cluster | sections | bytes |
|---|---|---|
| pipeline and its data rules | §1 687 + §1a 6022 + §1b 3105 + §1c 1276 + §1d 10282 + §1e 2262 + §2 2780 + §4 1468 + §5 1142 | **29 024** |
| delivery | §12 1748 + §13 6298 + §14 2459 | **10 505** |
| a route's numbers | §19 3055 | **3 055** |
| label wrapping | §20 3044 | **3 044** |

29 024 + 10 505 + 3 055 + 3 044 = 45 628, plus the preamble (604 B, lines 1–11) and the
unnumbered "Sections that moved out of this document" block (519 B) = **46 751** — `wc -c` agrees.

## Conditions before the cut

`bash tools/sensors/run.sh --only document-size --json` (dry-run): **3** violations —
`Backlog.md` 35890, `MapRendering.md` 46751, `UI.md` 45327.

## Checks before the cut

- `bash tools/check-doc-routes.sh` → `all 308 section references resolve, all 2 skill references resolve` (exit 0)
- `bash tools/check-rule-routes.sh` → `every glob matches the tree and all 575 named rule(s) are reached` (exit 0)
