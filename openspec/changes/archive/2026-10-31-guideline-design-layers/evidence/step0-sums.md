# STEP 0 — the arithmetic, computed before the edit (ruler: `check-doc-routes.sh --sizes`, the pre-cut `Design.md`)

Command: `for f in ...; grep -b '^## ' "$f" | awk ...` over the pre-cut line ranges, and `bash tools/check-doc-routes.sh --sizes` for the baseline.

Pre-cut section costs (§1+§3+§4+§12) = 1178+3876+9677+8303 = 23034 B (Design.md kept set)
(§2+§9) = 4747+2906 = 7653 B (DesignStack.md)
(§5)   = 12781 B (DesignNative.md — over the 12000 section bound by 781)
(§6+§7+§8+§10+§11) = 1789+969+12028+519+7470 = 22775 B (DesignCrossVariant.md — §8 over by 28)
TOTAL = 66243 B against 4 x 48000 = 192000 B capacity — the DOCUMENT bound is comfortable; the SECTION bound is what bit.
Verdict: every document fits; §5 and §8 each need a split, exactly as D2 says.
