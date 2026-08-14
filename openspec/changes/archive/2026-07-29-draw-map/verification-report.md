## Verification Report: draw-map

### Summary
| Dimension | Status |
|-----------|--------|
| Completeness | 39/40 tasks, 8/8 requirements |
| Correctness | 7/8 reqs fully covered, 1 partial |
| Coherence | 14/15 design decisions followed |

---

### Issues by Priority

#### CRITICAL

**1. Missing retry button for render errors**
- **Spec:** `map-render/spec.md` — "the system displays an error message **and a 'Retry' button**"
- **Code:** `MapCanvasScreen.kt:69-75` — shows error text but no button
- **Action:** Add a `Button` with `onClick = { viewModel.retryRender() }` next to the error text

**2. Manual smoke test not done**
- **Task:** 7.3 — "launch app, navigate to map screen, verify render, pan, zoom, restart at same location"
- **Status:** Not verified by automated test
- **Action:** Run on device and confirm all flows work, then mark task complete

---

#### WARNING

**1. Re-render fires on every gesture event, not on gesture end**
- **Spec:** `map-pan-zoom/spec.md` — "the map SHALL re-render **when the pan gesture ends**"
- **Code:** `MapCanvasScreen.kt:101` — `renderMap()` called inside `detectTransformGestures` which fires continuously during drag
- **Impact:** Multiple renders during single pan = sluggish UX. Current `renderJob?.cancel()` mitigates but still wasteful.
- **Recommendation:** Track gesture active state; only call `renderMap()` when pointers are released (use `awaitPointerEvent` for release detection)

**2. Render resolution differs from spec**
- **Spec:** `map-render/spec.md` — "render target SHALL be the full screen dimensions (minus system bars)"
- **Implementation:** Renders at fixed 864×1152 and scales via `drawImage` with `dstSize`
- **Design:** Decision #8 explicitly chooses 864×1152 as trade-off — spec and design are inconsistent
- **Recommendation:** Update spec to match design: "SHALL render at 864×1152 (3:4 portrait) and scale to fill screen"

---

#### SUGGESTION

**1. Missing `retryRender()` wiring**
- `retryRender()` exists in `MapCanvasViewModel.kt:134` but is never called from UI
- Add retry button when error state is shown (see CRITICAL #1)

**2. Pan conversion uses approximation**
- `MapCanvasScreen.kt:88` — `degPerPx = 0.0001 * (8f / mag) / cosLat` is a rough heuristic
- libosmscout provides `projectToPixel()` JNI method for accurate coordinate projection
- Recommendation: Use `OSMScoutClient.projectToPixel()` for correct pan-to-geographic mapping

---

### Final Assessment

**1 critical, 2 warnings, 2 suggestions.** Fix retry button and run smoke test before archiving. Spec and design have minor inconsistency on render dimensions — update spec to match actual implementation.
