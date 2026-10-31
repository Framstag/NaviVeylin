# Pre-cut baseline — guideline-small-docs-layers

Generated before any edit. Command for the byte counts and headings:
`wc -c guidelines/{Regulatory,FeatureList,Metrics,Logging}.md` and
`grep '^## ' guidelines/<Doc>.md`; the section-cost table is
`bash tools/check-doc-routes.sh --sizes` (the four documents' rows are copied to
`evidence/pre-cut-section-costs.txt`).

## Byte counts (document, no section over 12 000; document bound 48 000)

| document | bytes | over 48 000 | over 12 000 sections |
|---|---|---|---|
| `Regulatory.md` | 24 942 | no | none |
| `FeatureList.md` | 17 221 | no | none |
| `Metrics.md` | 9 551 | no | none |
| `Logging.md` | 7 033 | no | none |

## Heading-number sets (`## ` headings, to be unchanged by the cut)

- `Regulatory.md`: §1, §2, §3, §4, §5, §6, §7, §8, §9, §10
- `FeatureList.md`: §1, §2, §3, §4, §5, §6, §7, §8, §9
- `Metrics.md`: §1, §2, §3, §4, §5, §6, §7
- `Logging.md`: §1, §2, §3

## Routed status (from `--sizes`)

- routed: `Regulatory.md` §2, §3, §4, §6, §9; `FeatureList.md` §1, §3, §4, §6, §8;
  `Metrics.md` §1–§6; `Logging.md` §1, §2, §3
- not routed: `Regulatory.md` §1, §5, §7, §8, §10; `FeatureList.md` §2, §5, §7, §9;
  `Metrics.md` §7

## Baselines recorded

- `evidence/pre-cut-purity.txt` — the three `normative-purity` conditions before the cut.
- `evidence/pre-cut-rule-names.txt` — the existing `**Rule:` names, none in these four documents.
- `evidence/pre-cut-routing-rows.txt` — the four documents' rows in the `AGENTS.md` Documentation Map.
