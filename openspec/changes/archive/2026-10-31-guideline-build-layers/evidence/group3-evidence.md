# Group 3 — the evidence document (BuildEvidence.md)

Commands quoted from this run:

- `wc -c guidelines/BuildEvidence.md` -> 23859
- `bash tools/check-doc-routes.sh --sizes | grep BuildEvidence` -> §4 413 B, §4a 2018, §4b 3113, §4c 1657,
  §4d 2756, §4e 2701, §4f 4843, §7 3325, §11 2537 (all under 12 000)
- `SENSORS_ROW='{"scope":"normative-purity"}' bash tools/sensors/metrics/doc-context.sh` -> 0 BuildEvidence
  findings
- `SENSORS_ROW='{"scope":"normative-document"}' SENSORS_THRESHOLD=48000 bash tools/sensors/metrics/doc-context.sh`
  -> no BuildEvidence violation (BuildEvidence is 23859 B)
- `bash tools/check-doc-routes.sh` -> `all 199 section references resolve, all 2 skill references resolve`
  (exit 0)
- `bash tools/check-rule-routes.sh` -> `every glob matches the tree and all 115 named rule(s) are reached`
  (exit 0)

## Relocation

Blocks removed from `Build.md` (its §4, §4a-§4f, §7, §11 bodies replaced by index entries naming
`BuildEvidence.md`): 9.
Statements found in `BuildEvidence.md`: 9 sections (§4, §4a-§4f, §7, §11), same numbers and headings.
Rationale/baselines moved to `BuildEvidence.why.md`; tallies and XML samples to `Build.recipes.md`.
Relocation count closes: 9 removed = 9 found.

## Index entries name their destination

```
$ grep -n 'Moved to `BuildEvidence.md`' guidelines/Build.md
155:Moved to `BuildEvidence.md` §4.
159:Moved to `BuildEvidence.md` §4a.
... (4b, 4c, 4d, 4e, 4f)
502:Moved to `BuildEvidence.md` §7.
1238:Moved to `BuildEvidence.md` §11.
```

Each moved number resolves as a heading of `Build.md` and points at the document that took it.

## Rule names and reach

65 BuildEvidence rules extracted (multi-line bold spans included) and added to `tools/doc-routes.json` under
`guidelines/**`, so every one is reached. No name collides with `BuildGate.md`, `Backlog.md` or `UI.md`
(`check-rule-routes` is green).
