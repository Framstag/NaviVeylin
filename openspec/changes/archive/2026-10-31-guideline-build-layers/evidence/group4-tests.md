# Group 4 — the test document (BuildTests.md)

Commands quoted from this run:

- `wc -c guidelines/BuildTests.md` -> 14089
- `bash tools/check-doc-routes.sh --sizes | grep BuildTests` -> §6 484 B, §6a 1280, §6b 2864, §6c 1818,
  §6d 1012, §6e 1075, §6f 2974, §6g 1277, §6h 910 (all under 12 000)
- `SENSORS_ROW='{"scope":"normative-purity"}' bash tools/sensors/metrics/doc-context.sh` -> 0 BuildTests
  findings
- `bash tools/check-doc-routes.sh` -> `all 217 section references resolve, all 2 skill references resolve`
  (exit 0)
- `bash tools/check-rule-routes.sh` -> `every glob matches the tree and all 135 named rule(s) are reached`
  (exit 0)

## Relocation

Blocks removed from `Build.md` (its §6, §6a-§6h bodies replaced by index entries naming `BuildTests.md`): 9.
Statements found in `BuildTests.md`: 9 sections (§6, §6a-§6h), same numbers and headings.
Relocation count closes: 9 removed = 9 found.

## Index entries name their destination

```
$ grep -n 'Moved to `BuildTests.md`' guidelines/Build.md
213:Moved to `BuildTests.md` §6.
217:Moved to `BuildTests.md` §6a.
... (6b, 6c, 6d, 6e, 6f, 6g)
245:Moved to `BuildTests.md` §6h.
```

## Rule names and reach

20 BuildTests rules added to `tools/doc-routes.json` under `guidelines/**`; no name collides with
`BuildGate.md`, `BuildEvidence.md`, `Backlog.md` or `UI.md` (`check-rule-routes` is green).

## JUnit stub detail

The stub names, the `java.library.path` wiring and the fake classes moved to `guidelines/Build.recipes.md`
§6h (task 4.2).
