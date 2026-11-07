# Group 2 — the gate document (BuildGate.md)

Commands quoted from this run:

- `wc -c guidelines/BuildGate.md` -> 18884
- `bash tools/check-doc-routes.sh --sizes | grep BuildGate` -> §2 1523 B, §2a 5951, §2b 4662, §2c 1389, §2d 3026, §2e 1764 (all under 12 000)
- `SENSORS_ROW='{"scope":"normative-purity"}' bash tools/sensors/metrics/doc-context.sh` -> no BuildGate finding
- `SENSORS_ROW='{"scope":"normative-document"}' SENSORS_THRESHOLD=48000 bash tools/sensors/metrics/doc-context.sh` -> `doc-context: BuildGate.md bytes=18884 lines=231 (bound 48000 bytes)`, no violation
- `grep -o '\*\*Rule: [^*]*' guidelines/BuildGate.md | sort | uniq -d` -> (empty; no duplicate)
- `bash tools/check-doc-routes.sh` -> `check-doc-routes: all 183 section references resolve, all 4 skill references resolve` (exit 0)
- `bash tools/check-rule-routes.sh` -> `check-rule-routes: every glob matches the tree and all 50 named rule(s) are reached` (exit 0)

## Relocation

Blocks removed from `Build.md` (its §2, §2a-§2e bodies replaced by one-line index entries): 6.
Statements found in `BuildGate.md`: 6 sections carrying the same numbers and headings (§2, §2a-§2e).
Rationale/procedure moved out:
- `BuildGate.why.md` — §2 cost figure, §2a's five measured costs, §2b's dated anecdotes, §2c's baseline
  and aggregate records, §2d's JVM-criteria derivation, §2e's changelog provenance.
- `BuildGate.recipes.md` — the §2d check block and its transcripts.

Relocation count closes: 6 removed = 6 found.

## Purity tokens removed from each moved section

- `issue #N`: 238, 692, 236, 687, 91, 92, 240, 83 (moved to why)
- `(measured YYYY-...)`: the §2 cost figure, §2c baselines, §2d provisioning transcript (moved)
- dated fenced output: the §2d provisioning block (moved to recipes)
