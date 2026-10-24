# Evidence — fix-build-md-routed-read-size (#249), correction round 2

Base commit `3180fff93ada7a530a4c2b2b6966905c743e373c`. Change root
`/home/tim/projects/NaviVeylin/.pi/worktrees/loop`. Everything below was produced in this tree or in a read-only
`git archive` expansion of the base commit (`evidence/red-on-head.log` records its root); no device, no build, no
network.

## The run's own block

`./tools/loop/measure.sh` (one call, after the change moved):

```
LOOP-MEASURE-CHECK ./gradlew test -PforceTests --no-build-cache
LOOP-MEASURE-CHECK bash tools/gen-feature-list.sh --check-classification
LOOP-MEASURE-CHECK tools/check-doc-routes.sh
LOOP-MEASURE-CHANGE fix-build-md-routed-read-size scenarios=6 cases=4 unnamed=2
LOOP-MEASURE-TALLY app/build/test-results/loop-gate/tools_check-doc-routes.sh.xml tests=0 failures=0
LOOP-MEASURE-TALLY openspec/changes/fix-build-md-routed-read-size/evidence/green-after-fix.xml tests=4 failures=0
LOOP-MEASURE-TALLY openspec/changes/fix-build-md-routed-read-size/evidence/red-on-head.xml tests=4 failures=3
LOOP-MEASURE-SURFACE surface: 4 cases added, 0 cases rewritten, 0 fixtures realigned — the cases are `evidence/red-probe.sh`'s four,
LOOP-MEASURE-END
```

`unnamed=2` is the two shipped scenarios this delta carries verbatim and does not change ("Numbers for
representative changes", "An unmeasured saving is not claimed"); they assert nothing new and no case is added
for them.

## The ruler

One ruler for every byte figure in this change, in `Build.md`'s four index paragraphs and in `AGENTS.md`'s
rows: **a section's cost is the byte count of its lines from the `## ` heading through the line before the next
`## ` heading** — `sed -n '<heading>,<next heading - 1>p' guidelines/Build.md | wc -c`. The whole table is
`bash evidence/red-probe.sh --bound 0 bound`, whose output is retained as `evidence/section-sizes.log`; its four
largest entries are 8 877, 7 294, 6 841 and 5 609 bytes, and the four split sections' largest sub-sections are
5 238, 5 275, 5 271 and 6 841 (`§2c`, `§4f`, `§6b`, `§10i`).

Round 1 quoted per-section costs 2 bytes lower than this ruler reports (section 7 was 8 875, `§10i` 6 839): that
came from dropping each section's trailing separator line. Those numbers are gone from `AGENTS.md`, the index
paragraphs, `design.md` and this file; the `kB` figures in `AGENTS.md` are bytes/1000 at one decimal, and each
row says so.

## Red on HEAD, green on the change

`evidence/red-on-head.log` / `.xml` — the four cases over the base commit's `guidelines/Build.md` and
`AGENTS.md`, 2026-10-22T17:17:56Z, `tests=4 failures=3`:

```
FAIL routed-read#bound — 4 section(s) of Build.md exceed 12000 bytes: §10=31568 §4=23380 §2=23235 §6=22053
PASS routed-read#numbers — check-doc-routes: all 120 section references resolve, all 4 skill references resolve
FAIL routed-read#route — the routing table names no sub-section of: §2a §4a §6a §10a
FAIL routed-read#verbatim — §2 carries 0 lettered sub-sections, fewer than 4   (§4, §6, §10 likewise)
```

`evidence/green-after-fix.log` / `.xml` — the same four cases on the change's final content,
2026-10-22T17:04:18Z, `tests=4 failures=0`:

```
PASS routed-read#bound — every section of Build.md is at most 12000 bytes
PASS routed-read#numbers — check-doc-routes: all 152 section references resolve, all 4 skill references resolve
PASS routed-read#route — the routing table names 29 lettered Build.md sub-sections across 3 priced row(s)
PASS routed-read#verbatim — every offender is split into lettered sub-sections and no sentence is rewritten
```

The `## all four cases, restored` block of `evidence/revert-check.log` (2026-10-22T17:03:35Z) is the run that
covers the merged content after the last restore; `green-after-fix.*` is the standalone equivalent, taken after
it.

## Revert-checks — four mutations, each of production content

One mutation per case (`guidelines/Build.md` §4f), each restored and re-run green:

| case | mutation | the case said | log |
|---|---|---|---|
| `routed-read#verbatim` | one sentence of §6f reworded | `1 line(s) of Build.md are rewritten rather than moved`, naming the line | `evidence/revert-check-verbatim.log` |
| `routed-read#route` (a) | build row back to the bare parents | `the routing table names no sub-section of: §2a §4a` | `evidence/revert-check-route.log` |
| `routed-read#route` (b) | build row keeps its sub-sections, loses its `kB` cost | `1 row(s) name a lettered Build.md sub-section but state no cost: [build, run, force a rerun, …]` | `evidence/revert-check-route.log` |
| `routed-read#bound` | `§10a–§10j` demoted to `###` (section 10's single-heading shape restored) | `1 section(s) of Build.md exceed 12000 bytes: §10=32707` | `evidence/revert-check-bound.log` |

Round 1's `#bound` "falsification" lowered the probe's threshold (`--bound 5000`) instead of mutating
production, and `design.md` claimed a mutation that was never run. Both are replaced by the run above, whose
red number (`§10=32707`) is what the case prints against the demoted production file.

## Surface — what else asserts the shape this change moves

```
$ grep -lE 'Documentation Map|routing table' tools/*-cases.sh ; echo "exit=$?"
exit=1
$ grep -n 'theRepositorysAgentsFileIsUntouched' tools/check-doc-routes-selftest.sh
240:  echo "PASS  routes#theRepositorysAgentsFileIsUntouched — the repository's AGENTS.md is untouched by every case"
```

No `tools/*-cases.sh` suite asserts a routing-table shape, and the route check's self-test builds a fixture
`AGENTS.md` in a throwaway root and asserts the repository's own file is untouched, so no fixture moves when the
table's rows are re-pointed. The four cases this change adds live in `evidence/red-probe.sh`.

## Gate

`./tools/loop/gate.sh`, run from the change root; `evidence/gate.log`:

```
gate: ./gradlew test -PforceTests --no-build-cache
gate: bash tools/gen-feature-list.sh --check-classification
gate: tools/check-doc-routes.sh
LOOP-GATE: tests=0 failures=0 (./gradlew test -PforceTests --no-build-cache, bash tools/gen-feature-list.sh --check-classification, tools/check-doc-routes.sh)
```

Tallies (`evidence/gate-tallies/`): `tools/check-doc-routes.sh` `tests=0 failures=0`, its `system-out` being
`check-doc-routes: all 152 section references resolve, all 4 skill references resolve`; the classification check
`tests=0 failures=0` with `stale: 0 car-only areas: 1 highlights: 19`. No compiled path is touched, so the
Gradle command is listed and not run (`guidelines/Build.md` §2a scope block). The gate was invoked twice inside
one minute — once with its stdout consumed by a shell pipe, once redirected into `evidence/gate.log`; both
printed the same `LOOP-GATE` line and the tallies are byte-identical.

## What round 2 corrected

1. The shipped requirement's scope is `guidelines/Build.md`; applying the bound to the other guidelines is
   issue `#197`'s. At merge `UI.md` §1 (24 896 B), `UI.md` §8 (19 334 B) and `MapRendering.md` §1 (22 617 B)
   are routed and over the bound — the change no longer ships a requirement the tree violates.
2. `routed-read#route` now asserts both clauses of its scenario: the row names the lettered sub-section **and**
   states what it costs (a row naming sub-sections and pricing nothing fails), proven by mutation (b).
3. `routed-read#bound` is falsified by mutating production content, not the probe's threshold.
4. `tasks.md` 3.1–3.3 cite three logs that exist (`evidence/revert-check-{verbatim,route,bound}.log`).
5. The quoted numbers are re-derived: `Build.md` 137 276 → 140 582 B (+3 306), `AGENTS.md` 24 241 → 25 985 B
   (+1 744), 9 promoted `###` headings (5 in §2, 4 in §10), 20 retargeted `§N` lines inside `Build.md`, 70
   `Build.md §N` references in 29 files on both sides, `all 152 section references resolve`, and the build
   row's range 1.7–5.3 kB for the sections it names (§3 1 696 B to §4f 5 275 B).
