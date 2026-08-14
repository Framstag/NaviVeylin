## Verification Report: zoom-pan

### Summary
| Dimension    | Status |
|--------------|--------|
| Completeness | 32/32 tasks complete, 7/7 spec files |
| Correctness  | 6/7 specs verified, 1 with deviations |
| Coherence    | Design mostly followed, 2 deviations |

---

### CRITICAL (Must fix before archive)

**1. Canvas overrun spec not implemented**
- **Spec:** `canvas-overrun/spec.md` requires render at `screenWidth × canvasOverrun` (default 2.5×) with sub-region blit for pan
- **Implementation:** `MapRenderer.kt:244-245` hardcodes `renderW = screenWidth`, `renderH = screenHeight` — overrun multiplier ignored. Comment says "Overrun buffer disabled for now"
- **Impact:** Sub-region blit never triggers (buffer same size as screen), every pan triggers full re-render
- **Fix:** Either implement overrun rendering using `canvasOverrun` multiplier, or update spec to reflect current state (deferred)

**2. Scroll-wheel zoom not implemented**
- **Task 5.5:** "Add `pointerInput` for scroll-wheel zoom (for emulator/testing)" — marked complete
- **Implementation:** No scroll-wheel handler exists in `MapCanvasScreen.kt`
- **Impact:** Cannot test zoom with mouse wheel on emulator
- **Fix:** Implement scroll-wheel zoom handler or mark task incomplete

---

### WARNING (Should fix)

**3. Zoom placeholder not visible in practice**
- **Spec:** `adaptive-zoom/spec.md` requires immediate scaled placeholder on zoom change
- **Implementation:** `MapRenderer.trySubRegionBlit()` handles zoom scaling and sets `_frontBufferFlow.value = scaled`, but returns `false` to trigger full render. The full render completes quickly at screen resolution (~50-100ms), overwriting the placeholder before it's displayed
- **Impact:** User reported "still no scale up/down images" — placeholder too brief to notice
- **Fix:** Add minimum display time for placeholder (e.g., `delay(50)` before emitting full render result when mag changed)

**4. Render timing adaptive quality not implemented**
- **Spec:** `adaptive-zoom/spec.md` requires "system may reduce overrun multiplier or quality for subsequent renders" when render is slow
- **Implementation:** Timing is logged but no adaptive quality adjustment exists
- **Fix:** Add logic to reduce `canvasOverrun` when consecutive renders exceed threshold

---

### SUGGESTION (Nice to fix)

**5. Epoch in ViewModel vs MapRenderer**
- **Design Decision 6:** "Epoch as AtomicLong in ViewModel"
- **Implementation:** Epoch is in `MapRenderer` (not ViewModel). The ViewModel has an unused `epoch` field
- **Impact:** Minor — epoch works correctly in MapRenderer. ViewModel's `epoch` field is dead code
- **Fix:** Remove unused `epoch` from `MapCanvasViewModel`

**6. Canvas overrun default mismatch**
- **Spec:** Default overrun multiplier is 2.5×
- **Implementation:** `canvasOverrun = 1.2` in MapRenderer, but render ignores it entirely
- **Fix:** Align default or remove field if unused

---

### Final Assessment

**2 critical issues found.** Fix before archiving:
1. Canvas overrun spec not implemented (render at screen size, not overrun size)
2. Scroll-wheel zoom task marked complete but not implemented

**2 warnings** to consider: zoom placeholder too brief, adaptive quality missing.

**2 suggestions:** dead code cleanup, default alignment.
