# Pre-cut state — guideline-ui-cut

Measured 2026-10-31 in the parent, before any edit (`/tmp/ui-sections.py` over `guidelines/UI.md`).

Baseline: **45 327 B**, 617 lines, 13 numbered sections.

| section | bytes | lines | cluster |
|---|---|---|---|
| preamble | 1 038 | 1–22 | — |
| §1 Cross-variant UI parity | 1 716 | 23–49 | shared |
| §2 Action glyphs on the car display | 532 | 50–62 | car |
| §3 Car-host template constraints | 3 690 | 63–107 | car |
| §3a Map renderer startup | 5 379 | 108–181 | car |
| §3b Settings-screen loading | 2 036 | 182–212 | car |
| §3c Car wait notices | 2 912 | 213–248 | car |
| §4 Details attribute list (AA) | 933 | 249–262 | car |
| §6 Shared data resolution | 1 313 | 263–280 | shared |
| §6b AA search surface | 5 169 | 281–347 | car |
| §8d Cross-variant map presentation | 5 539 | 348–412 | **unclaimed — see below** |
| §9 Dark mode | 4 877 | 413–476 | shared |
| §10 Internationalisation | 8 816 | 477–600 | shared |
| §20 Keeping this document honest | 1 377 | 601–617 | shared |

Car cluster: **20 651 B** across seven sections. Shared cluster without §8d: **18 099 B**.
Shared cluster with §8d: **23 638 B**. Preamble 1 038 B is rewritten for both halves.

Ruler: each figure is the byte count of the section's lines from its `## ` heading to the line before
the next one, trailing blank included — the ruler `check-doc-routes.sh --sizes` uses. The 13 sections
sum to 44 289 B, which with the 1 038 B preamble is the baseline's 45 327 B.

## §8d is the section neither cluster claimed

`design.md`'s decision D1 named two clusters — car (§2, §3, §3a, §3b, §3c, §4, §6b) and shared
(§1, §6, §9, §10, §20) — and **§8d belonged to neither**, so the first worker run could not close
task 2.3's heading union and stalled on a survey it had no answer for. Measured after the fact:

- **Nine rules, seven of them dual-surface**: per-surface anchors with shared labels and hierarchy,
  one reroute policy for the session, the row rule for street-name labels, one route and one marker
  palette per presentation, the opaque casing, lowercase stylesheet hexes, the per-surface
  voice/GPS switch pair.
- One is AA-only (the surface indicators' fixed palettes) and one is phone-only (the
  location-options-only settings) — single-surface bullets inside a dual section, not a section that
  belongs to one variant.
- It is **7/9 parity material**, so it stays in `UI.md`, the parity document. Splitting it mid-section
  would renumber, which no cut in this set may do.

The reason the omission was invisible: **no row of AGENTS.md's Documentation Map named §8d either**
(`check-doc-routes.sh --sizes` marked it `not-routed`), so a design that read the table's clusters
could not have seen what the table does not route. Corrected here: the parity row now names
`UI.md` §8d.

Arithmetic after the correction, in the ruler above: 20 651 (car) + 23 638 (shared) = 44 289 B of
sections, plus 1 038 B of preamble = the baseline's 45 327 B. Both halves fit with room to spare:
the car half is 21 562 B in the finished file and the shared half 24 881 B, the difference being the
second preamble (1 048 B of grown preamble across the two, and 68 B of named edits inside three moved
sections and §20) — see `post-cut.md` and `relocation.txt`.
