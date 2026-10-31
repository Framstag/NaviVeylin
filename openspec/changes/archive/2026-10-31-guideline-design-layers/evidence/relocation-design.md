# Relocation counts (satisfies documentation-ownership, 'A relocated rule keeps its statement')

- §13 'Superseded decisions' (63 lines) and Appendix B 'Provenance' (14 lines) moved whole to Design.why.md, byte-identical (task 8.3).
- §2's 'Adding to the stack' recipes (20 lines) and Appendix A's apply checklist (14 lines) moved to Design.recipes.md.
- §11's attribution provenance (issue #115) and §12's band-conflict datum (measured 2026-10-06) moved to Design.why.md §11/§12.
- §5b's dex provenance (issue #691) and drift-class provenance (issue #174) moved to DesignNative.why.md §5b, together with the two database-open entries' rationale.
- 4 blocks whose statement was a provenance token: removed from the normative layer, found in a companion (count closes).
- `design.md` D3: the AGENTS.md row that named §2's recipes is retargeted to `Design.recipes.md` (see retargeted-citations.txt).

Statement-finding: every removed block's statement is found in the receiving companion by `grep`; nothing was deleted whose statement exists nowhere.
