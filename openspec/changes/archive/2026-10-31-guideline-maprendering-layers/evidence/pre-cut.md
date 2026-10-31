# Pre-cut baseline — guidelines/MapRendering.md
Measured with `bash tools/check-doc-routes.sh --sizes` and `wc -c`, 2026-10-31.

## Section cost table

```
check-doc-routes: section-cost MapRendering.md §1 22592 bytes 249 lines routedcheck-doc-routes: section-cost MapRendering.md §2 2781 bytes 34 lines routedcheck-doc-routes: section-cost MapRendering.md §3 532 bytes 10 lines routedcheck-doc-routes: section-cost MapRendering.md §4 1463 bytes 19 lines routedcheck-doc-routes: section-cost MapRendering.md §5 1134 bytes 16 lines routedcheck-doc-routes: section-cost MapRendering.md §6 2088 bytes 26 lines routedcheck-doc-routes: section-cost MapRendering.md §7 3120 bytes 47 lines routedcheck-doc-routes: section-cost MapRendering.md §8 2354 bytes 33 lines routedcheck-doc-routes: section-cost MapRendering.md §9 629 bytes 11 lines routedcheck-doc-routes: section-cost MapRendering.md §10 251 bytes 6 lines routedcheck-doc-routes: section-cost MapRendering.md §11 5825 bytes 65 lines routedcheck-doc-routes: section-cost MapRendering.md §12 1727 bytes 23 lines routedcheck-doc-routes: section-cost MapRendering.md §13 6258 bytes 79 lines routedcheck-doc-routes: section-cost MapRendering.md §14 2460 bytes 23 lines routedcheck-doc-routes: section-cost MapRendering.md (unnumbered) 4163 bytes 54 lines not-routedcheck-doc-routes: section-cost MapRendering.md §14 11744 bytes 139 lines routedcheck-doc-routes: section-cost MapRendering.md §15 5052 bytes 61 lines routedcheck-doc-routes: section-cost MapRendering.md §15a 3149 bytes 38 lines routedcheck-doc-routes: section-cost MapRendering.md §16 1793 bytes 28 lines routedcheck-doc-routes: section-cost MapRendering.md §16a 2432 bytes 31 lines routedcheck-doc-routes: section-cost MapRendering.md §16b 790 bytes 13 lines routedcheck-doc-routes: section-cost MapRendering.md §17 3352 bytes 40 lines routedcheck-doc-routes: section-cost MapRendering.md §18 3257 bytes 54 lines routedcheck-doc-routes: section-cost MapRendering.md §19 3056 bytes 38 lines routedcheck-doc-routes: section-cost MapRendering.md §20 3008 bytes 36 lines routedcheck-doc-routes: section-cost MapRendering.md (unnumbered) 918 bytes 17 lines not-routed```

## `## ` heading-number set

```
   1  1. Render Pipeline Architecture
   2  2. Bitmap Lifecycle (CRITICAL — caused "jumps" multiple times)
   3  3. GPS Deduplication
   4  4. Render Coalescing / Throttling
   5  5. Epoch Rules
   6  6. Angle Handling
   7  7. Bearing Smoothing (location layer)
   8  8. Marker Rules
   9  9. Map Rotation (Follow-Mode)
  10  10. Camera Center (Follow-Mode)
  11  11. Auto-Zoom
  12  12. Front-Buffer Emission
  13  13. Overrun Display Window (pan and follow)
  14  14. Rotation Gesture Display-Layer Handoff   <-- DUPLICATE
(none)  Known Pitfalls (Regression Checklist)
  14  14. Android Auto renderer — smooth follow (overrun + blit + extrapolation)   <-- DUPLICATE
  15  15. Day/Night (daylight flag) contract
 15a  15a. Stylesheet load failure (app side) — never render a rejected style, never crash
  16  16. Basemap stylesheet contract
 16a  16a. POI icons — submodule source, configured directory, symbol fallback
 16b  16b. Stylesheet source and the on-device copy
  17  17. Native tile-data cache — capacity, retention and its release
  18  18. The phone canvas while a car session is active — disposal, not hiding
  19  19. Route data — one length for one route
  20  20. Label wrapping — the bounds the renderer configures
(none)  Parameter Overview
```

## Duplicated number

`14` appears twice: "Rotation Gesture Display-Layer Handoff" and "Android Auto renderer — smooth follow".
The baseline carries 24 numbered `## ` entries over 22 distinct numbers (§14 twice), plus the two
unnumbered blocks Known Pitfalls and Parameter Overview.
