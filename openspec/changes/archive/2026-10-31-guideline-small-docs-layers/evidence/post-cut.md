# Post-cut record — guideline-small-docs-layers

Per document: the rules named, the sections routed, and every block moved to a companion with its
receiving document (tasks 3.1, 4.1, 5.2, 6.1, 6.2).

## Logging.md (7 033 → 7 500)

- sections routed: §1, §2, §3 (unchanged). Rules named: 9 (2 pre-existing kept verbatim:
  "libosmscout never uses Android logging outside its frozen directory.",
  "native code never logs a position.").
- moved to `Logging.recipes.md` (§1, §2, §3): the logcat recipes and the NDK bridge's configuration detail.

## Metrics.md (9 551 → 9 731)

- sections routed: §1–§6 (unchanged). Rules named: 8.
- moved to `Metrics.recipes.md` (§1, §2, §7): the invocations and their sample output.

## FeatureList.md (17 221 → 17 321)

- sections routed: §1, §3, §4, §6, §8 (unchanged). Rules named: 9.
- moved to `FeatureList.recipes.md` (§4, §6, §8): the generator's invocations and its gate commands.
- no `FeatureList.why.md`: the classification vocabulary is a closed set a gate reads (a rule),
  not rationale — so it stays in the document.

## Regulatory.md (24 942 → 19 068)

- sections routed: §2, §3, §4, §6, §9 (unchanged). Rules named: 22.
- §9 cut: its rules stay (six named rules on the diagnostics gate, retention, the one-rule-per-shape
  principle, the disclosure and the car render entry); its inventory and its review record move.
  §9 remains routed and is now 1 838 B (was 6 785 B).
- moved to `Regulatory.why.md`:
  - §9 — the location-permission inventory, the audit dates, the diagnostics history (gate second pass,
    stale-coordinate purge, the earlier-recipe impact), the owner-side items and the TODO tracking.
  - §10 — the dated watch list and the "status as of" line.
  - §2/§6 — why the duties were added (the cleartext rationale, the location-policy defect).
- moved to `Regulatory.recipes.md`:
  - §10 — the primary source list.
  - §2 — the licence checklist. §6 — the platform review checklist. §9 — the diagnostics verification recipe.

## Notes carried from the baseline

- `evidence/pre-cut-rule-names.txt` recorded 0 `**Rule:` names in all four documents, and a fresh
  `grep` confirms no `**Rule:` marker existed in the tree baseline. The design's "three rule names
  already exist" (one in `Regulatory.md`, two in `Logging.md`) is therefore read as the two prose
  statements in `Logging.md` ("**Never** use Android logging …", "**Never log a position here either**")
  and the §6/§2 duties in `Regulatory.md`. The two `Logging.md` statements are kept verbatim as names.
  No pre-existing `**Rule:` name had to survive, and none was lost.
