# Groups 5-8 — native document, the remainder of Build.md, the entry document and verification

## Sizes (post-cut)

```
$ wc -c guidelines/Build.md guidelines/BuildGate.md guidelines/BuildEvidence.md guidelines/BuildTests.md guidelines/BuildNative.md
 29455 guidelines/Build.md
 18884 guidelines/BuildGate.md
 23859 guidelines/BuildEvidence.md
 14089 guidelines/BuildTests.md
 14294 guidelines/BuildNative.md
```

Largest section per document (`tools/check-doc-routes.sh --sizes`): Build.md §9 9 130 B, BuildGate.md §2a
5 951 B, BuildEvidence.md §4f 4 843 B, BuildTests.md §6f 2 974 B, BuildNative.md §10j 2 571 B — every
document under 48 000 B with every section under 12 000 B.

## Conditions (before -> after)

- `document-size` (scope `normative-document`, bound 48 000): **4 -> 3**. Before: Build, Design,
  MapRendering, UI. After: Design, MapRendering, UI (Build and the four new documents are inside the bound).

  ```
  $ SENSORS_ROW='{"scope":"normative-document"}' SENSORS_THRESHOLD=48000 bash tools/sensors/metrics/doc-context.sh | grep verdict.*violation
  {"document":"Design"...} {"document":"MapRendering"...} {"document":"UI"...}
  ```

- `normative-purity`: nothing for any Build-family document.

  ```
  $ SENSORS_ROW='{"scope":"normative-purity"}' bash tools/sensors/metrics/doc-context.sh | grep -c '"document":"Build'
  0
  ```

  (Build.md carried 4 issue-token conditions before the §5/§9 clean-up; they are cleared.)

## No heading number lost (the heading-number check — a per-change comparison, not a harness case)

Union of the five documents' `## ` numbers:

```
1 10 10a 10b 10c 10d 10e 10f 10g 10h 10i 10j 10k 11 12 12a 12b 12c 12d 12e 12f 2 2a 2b 2c 2d 2e 3 4 4a 4b 4c 4d 4e 4f 5 6 6a 6b 6c 6d 6e 6f 6g 6h 7 8 9
```

against the baseline set in `pre-cut.md`: identical, plus the six §12a-§12f the §12 promotion adds. No number
was lost.

## Retargeted citations

`evidence/pre-cut-citations.txt` records the pre-cut citations. Each resolves by its new document name:

- `change-readiness-gate/proposal.md` §4a -> `BuildEvidence.md` §4a (exists).
- `change-readiness-gate/design.md` §2a -> `BuildGate.md` §2a (exists).
- `finding-shape-at-filing/design.md` §1 -> `Build.md` §1 (unchanged).
- `AGENTS.md` rows retargeted to `BuildGate.md`, `BuildEvidence.md`, `BuildTests.md`, `BuildNative.md`.
- `tools/doc-routes.json`: `app/src/main/cpp/**` §12 -> `BuildNative.md` §12; `app/build.gradle.kts` and
  `.github/workflows/**` §4 -> `BuildEvidence.md` §4.

External guideline bodies still cite `Build.md §N`; each `N` is kept as an index heading in `Build.md`, so the
citation resolves and the index entry names the document that took the number.

## Index entries

Every moved number in `Build.md` names its destination:

```
$ grep -c 'Moved to `BuildGate.md`' guidelines/Build.md    # 6
$ grep -c 'Moved to `BuildEvidence.md`' guidelines/Build.md # 9
$ grep -c 'Moved to `BuildTests.md`' guidelines/Build.md    # 9
$ grep -c 'Moved to `BuildNative.md`' guidelines/Build.md   # 13
```

## Named rules

Named rules added to `tools/doc-routes.json`, all reached (`guidelines/**` glob): BuildGate 29, BuildEvidence
65, BuildTests 20, BuildNative 8, Build.md 6. Total reached 149.

## Checks

- `bash tools/check-doc-routes.sh` -> `all 235 section references resolve, all 2 skill references resolve` (0)
- `bash tools/check-rule-routes.sh` -> `every glob matches the tree and all 149 named rule(s) are reached` (0)
