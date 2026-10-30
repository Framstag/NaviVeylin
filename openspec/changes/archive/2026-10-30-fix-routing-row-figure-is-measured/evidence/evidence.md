# Evidence — fix-routing-row-figure-is-measured

One clause in `tools/check-doc-routes.sh` and the two forms it compares, over the tree that carried two ranges the
measurement does not give. Every number below is a command's output in this directory.

## The red test — the check the base commit carries

`red-on-head.sh` extracts `tools/check-doc-routes.sh` from `2f954a04` into a throwaway tree with a copy of
`red-probe.sh` (the probe reads no document of the repository) and runs the probe's two cases against it:
`red-on-head.log`, tally `red-on-head.xml`, `tests="2" failures="2" errors="0" timestamp="2026-10-30T06:59:57Z"`.

```
FAIL routed-read#figure — a row stating 0.1 kB for a 1065-byte section was not reported: exit 0: check-doc-routes: all 1 section references resolve
FAIL routed-read#range — a row stating the range 1.2–2.0 kB for sections measuring 1.1–3.1 kB was not reported: exit 0: check-doc-routes: all 2 section references resolve
red-probe: 2 of 2 cases failed
```

`green-after-fix.log` is the same probe over the same two fixtures after the clause: `0 of 2 cases failed`.

## The numbers the proposal and the design state — `measure-the-rows.txt`

`measure-the-rows.sh` prints, each under the command that takes it: the eleven figures the six priced rows state
against their sections' measured bytes (3 149, 2 432, 790, 4 411, 1 823, 2 763, 6 043, 4 873, 13 102, 2 528,
2 218 — every figure equal to `bytes/1000` at one decimal); the three ranges against the span their lettered
sections measure (`1.9–7.0` for `Build.md` §2/§4, `1.3–5.3` for §6a–§6h, `0.9–6.8` for §10a–§10k); and the
`0.1 kB` fixture, which passes on HEAD and is convicted here with both numbers.

## The check and its cases

- `suite-green.log` — `bash tools/check-doc-routes-selftest.sh`: `all 23 cases passed` (21 before this change,
  plus `routed-read#figure` and `routed-read#range`).
- `mutations.log` — `bash tools/mutate.sh --mutations tools/check-doc-routes-mutations.json`: `5 mutation(s) run,
  5 went red and restored green, 0 problem(s)`, the two new records being `route-figure-is-measured` and
  `route-range-is-the-span`.
- `openspec-validate.log` — `openspec validate … --type change --strict`: `is valid`.
- `measure.txt` — the run's `LOOP-MEASURE` block taken before the archive: `base=2f954a04… paths=7
  insertions=260 deletions=37`, `LOOP-MEASURE-CHANGE … scenarios=2 cases=2 unnamed=0`, and its seven
  `LOOP-MEASURE-CHECK` lines (the four `*.md` and `tools/**` commands plus the three `openspec/**` ones).
  `measure-round-2.txt` — the same block taken over the archived change: `LOOP-MEASURE-GATE seconds=8 suites=7`,
  the same seven `LOOP-MEASURE-CHECK` lines, and the seven gate tallies.

## The gate — `gate.log`, `gate-tallies/`

Run once from the change root over the tree that carries the archived change: `LOOP-GATE: tests=29 failures=0
errors=0` over the seven suites the archived pathspec owes — the route check, its self-test, the mutation set's
`--list` and `check-todo-references.sh` for `*.md` and `tools/**`, and `check-openspec-hygiene.sh`, its self-test
and `gen-feature-list.sh --check-classification` for `openspec/**`. No compiled file is touched, so the build's gate
is not owed. Each suite's own tally is in `gate-tallies/`, seven files.

The item's first gate ran four of those seven while its own `measure.txt` named all seven, so the three
`openspec/**` checks — the ones that read the ADDED requirement and the archive — had not run; round 1's review
refused the change on that and its request is `review-round-1-requested.md`. The numbers above are the second and
whole run. `evidence-check-round-2.log` is the check over this round's record: PASS, 37 section references, 6 test
tallies, 469 `file:line` pointers, none dangling.

## What is not here

The two stale ranges were the only disagreement the tree carried when the clause first ran, and `AGENTS.md` now
states `1.9–7.0 kB per section` and `1.3–5.3 kB each`. That first run's output was not captured: what the record
carries is the post-edit green (`suite-green.log`: 146 section lines, 169 references) and `routed-read#route`'s
assertion over the project's own table, which fails on any row whose figure its section does not measure — so a
row this change left disagreeing would be a red case rather than an unrecorded one. The four
`against … before the split` figures in those rows are past measurements of sections that no longer exist and are
not compared — `routed-read#range` asserts exactly that, with `23.2 kB` in a row whose span is measured.

One figure this change leaves stale: `guidelines/Backlog.md` states `this §9 (15 389 bytes)` and §9 measures
15 785 bytes after this change's paragraph — `evidence/round-2.log` carries `check-doc-routes: section-cost
Backlog.md §9 15785 bytes 171 lines`. It is the pre-change figure in a sentence about "the tree this landed on",
no check compares it (§9 carries no lettered reference), and correcting it is the §9 split the design leaves to a
change of its own.
