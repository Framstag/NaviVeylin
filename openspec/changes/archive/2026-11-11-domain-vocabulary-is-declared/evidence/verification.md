# Verification — domain-vocabulary-is-declared (issue #839)

Base: `7032ef915b24450845d415854d3953c05466367e`. Work tree: `.pi/worktrees/loop`. Every log named below is
beside this file and was copied at the moment it was produced.

## What the delta is

The handed change, applied as written, plus the three things applying it required:

| landed | where |
|---|---|
| the declaration: index, eleven vacuous suffixes, per-feature row files | `tools/glossary/*.json` |
| the check — fourteen refusals, closed schema, anchors, kinds, census | `tools/check-glossary.sh` |
| the generator — KDoc projection, concept skeletons, the generated reading copy, `--check` | `tools/gen-glossary.sh` |
| the reading copy, generated and held to the declaration by `--check` | `tools/glossary/GLOSSARY.md` |
| the census program the check reads | `tools/glossary/census.awk` |
| one case per refusal plus the green-run figures (25 cases) | `tools/tests/check-glossary-selftest.sh` |
| the two projections, the check mode, the drift row (9 cases) | `tools/tests/gen-glossary-selftest.sh` |
| one mutation per refusal (14) | `tools/tests/check-glossary-mutations.json` |
| the drift measurement | `tools/sensors/metrics/vocabulary-drift.sh`, row in `tools/sensors/sensors.json` |
| the row's step in the sensor pipeline | `.github/workflows/sensors.yml` |
| the guideline and its rationale companion | `guidelines/Glossary.md`, `guidelines/Glossary.why.md` |
| the routing row, its route and its check path | `AGENTS.md`, `tools/doc-routes.json`, `tools/tooling.json` |
| the placement pointer | `guidelines/Design.md` §1 |
| the gate's suite map, so the delta's own checks run | `tools/loop/loop.conf` `LOOP_GATE_SUITES` |

## Red on the base

`red-on-HEAD.log`: at `7032ef91` neither the check, the generator, the suite that carries the case nor the
declaration exists (`git cat-file -e` returns 128 for each), so
`tools/tests/check-glossary-selftest.sh --case glossary#aRowWithAnUnknownFieldIsRefused` cannot pass — the suite
exits 2 on a tree whose check is absent. The case is green on the change (see below). This change lands new
tooling, so its red proof is an absence, not a failing assertion.

## Green on the change

| run | result | log |
|---|---|---|
| `tools/check-glossary.sh` over the whole tree | `green rows=53 anchored=53 kinds=attribute,entity,operation,value files=469`, 2 `drift` reports (reported, not refused) | `glossary-check-green.log` |
| the census the check prints on stderr | `census files=462 occurrences=333554`, `prefix Basemap core 39 / app 209 / auto 8`, `suffix KmH 35 / 159 / 84` vs `suffix Kmh 1 / 121 / 22`, `bare-booleans 0`, `types-with-a-vacuous-suffix 36` | `census.log` |
| `tools/gen-glossary.sh --check` | `green current=40 skeletons=0` — every projection is the declaration's text | `gen-glossary-check-green.log` |
| `tools/tests/check-glossary-selftest.sh` | 25 passed, 0 failed | `check-glossary-selftest.log` |
| `tools/tests/gen-glossary-selftest.sh` | 9 cases passed | `gen-glossary-selftest.log` |
| `tools/mutate.sh --mutations tools/tests/check-glossary-mutations.json` | 14 mutations run, 14 red then restored green | `mutations.log` |
| `SENSORS_TRIGGER=schedule bash tools/sensors/run.sh --only vocabulary-drift` | one condition (`drift`, 2 terms), `action=not filed (first exercise: no measurement on record)` | `sensor-first-run.log` |
| the same row over fifty injected findings | one condition carrying all fifty terms, `measurement=50` | `sensor-fifty-terms.log` |
| `tools/sensors/tests/sensor-selftest.sh` | 84 of 85 pass; the one failure is `hotspot#theBoundIsAboveOneChangedFile`, which is red on the base (see the note) | `sensor-selftest.log` |
| the structural checks of the scope block | `check-doc-routes`, `check-doc-values`, `check-openspec-hygiene`, `check-portability`, `check-todo-references`, `gen-feature-list --check-classification` all exit 0; `check-rule-routes` exits 1 for the five globs of the new files, which are untracked until the run commits — it re-reads `git ls-files` | `structural-checks.log` |

## Deviations from the change as it was written, and why

1. **The delta touches compiled files, and the change's own prose said it would not.** `tasks.md` 4.1 and
   `specs/vocabulary-projection` require the definition in the declaring type's KDoc and a skeleton type for a
   `concept` row no file declares; `proposal.md`'s "Unchanged" block and `tasks.md` 8.2 claimed no production
   file changes. The tasks and the spec scenarios are the contract, so they were applied: one KDoc block in
   `core/src/main/java/com/naviveylin/core/DeepLinkParser.kt` and seven skeletons under
   `app/src/main/java/com/naviveylin/concept/`. Both prose claims are corrected in place, and the build's gate
   is owed because of it.
2. **A structure row for the package the skeletons create.** `architecture/structure.tsv` is 1:1 with the tree;
   the seven skeletons make `app/src/main/java/com/naviveylin/concept` a package, so
   `app	com.naviveylin.concept	concept	-	-` was added, with the date and the reason in the file's header.
   This is task 1.2's file and its own rule ("one row per package as the tree stands today").
3. **One case the spec declares no scenario for.** The `phantom` refusal had no case, and task 7.1 owes one
   mutation per refusal; the mutation needs a case that fails when the guard is removed. The suite therefore
   carries `glossary#aSurfaceTheCensusNeverObservesIsRefused`. No scenario is left without a case.
4. **`sensor-selftest` is 84 of 85 on this base.** `hotspot#theBoundIsAboveOneChangedFile` fails because the
   largest tracked file is `openspec/changes/archive/2026-11-07-car-reroute-without-losing-the-map/evidence/
   device-logcat-full.log` at 4 349 247 bytes against the row's 1 000 000 bound. It fails on the base, in a
   file this change does not touch; the hotspot row's bound belongs to whoever added that log.
5. **The check the gate now runs.** `tools/loop/loop.conf` `LOOP_GATE_SUITES` gained the pairs the new paths
   owe (`tools/check-glossary*`, `tools/glossary/*`, `tools/gen-glossary*`, the mutation set and both
   self-tests), so `gate.sh --plan` lists four glossary checks for this delta instead of none.
