# Tasks — fix-routing-row-figure-is-measured

## 1. The red cases

- [x] 1.1 Write `evidence/red-probe.sh`: the two cases of this change (`routed-read#figure`, `routed-read#range`)
  driving the real `tools/check-doc-routes.sh` over a throwaway fixture tree whose routing table is a fixture
  `AGENTS.md` — a disagreeing figure and a disagreeing range are reported with the row and both numbers, a row
  stating the measurement passes, and a row's `against … before the split` figure is not compared. Verify: against
  the check the base commit carries, `red-on-head.sh` reports `red-probe: 2 of 2 cases failed`
  (`evidence/red-on-head.log`, tally `evidence/red-on-head.xml`, `tests=2 failures=2 errors=0`,
  `timestamp="2026-10-30T06:59:57Z"`), and the same probe after the clause reports `0 of 2`
  (`evidence/green-after-fix.log`).
- [x] 1.2 Record the numbers the proposal states with the command that takes each of them:
  `evidence/measure-the-rows.sh` → `evidence/measure-the-rows.txt` (the eleven figures and their sections, the
  three ranges against the span each row's lettered sections measure, and the `0.1 kB` fixture). Verify: every
  number the proposal and the design quote appears in that file beside its command.

## 2. The clause

- [x] 2.1 In `tools/check-doc-routes.sh`, move the `LC_ALL=C awk` block that builds `$tmp/sizes` out of the
  `--sizes` branch to before the row clauses, so one computation feeds both readers, and let `--sizes` print it
  unchanged. Verify: `bash tools/check-doc-routes.sh --sizes` prints 146 section lines —
  `bash tools/check-doc-routes.sh --sizes | grep -c '^check-doc-routes: section-cost'` prints `146`
  (`evidence/round-2.log`) — which is the count the bound case states, `146 section(s) printed, 116 routed
  pair(s)`, and `routed-read#bound` still passes (`evidence/suite-green.log`).
- [x] 2.2 Add the comparison clause: the row's lettered references paired with their document by the table's own
  backtick tokenisation, a figure after a reference (only non-digits between the two, so a separator and quoted
  heading text are read through) compared with `sprintf("%.1f", bytes / 1000)`, a dash-joined range compared with
  the cheapest and costliest of the row's lettered sections, nothing compared for a figure that is no reference's
  (`against … before the split`), one `::error` per disagreement naming the row and both numbers, and the summary
  line extended. The header comment and the `--help` range state the new clause. Verify:
  `bash -n tools/check-doc-routes.sh` parses; the clause's first run names the two stale ranges and no other
  figure; and the probe's two cases pass.

## 3. The cases and their falsification

- [x] 3.1 Add `routed-read#figure` and `routed-read#range` to `tools/check-doc-routes-selftest.sh` (the header's
  case list, the two fixture helpers, the case bodies, the `echo_case` registrations), each over a fixture tree
  and — for the figure — over the project's own table. The existing `routed-read#route` case's fixture priced its
  section at a made-up `1.2 kB`; it now states the measurement, because the accuracy clause convicts a figure that
  is not one. Verify: `bash tools/check-doc-routes-selftest.sh --case routed-read#figure` and `--case
  routed-read#range` each report `PASS`, and the whole suite reports `all 23 cases passed`
  (`evidence/suite-green.log`).
- [x] 3.2 Add `route-figure-is-measured` and `route-range-is-the-span` to
  `tools/check-doc-routes-mutations.json`, each naming its case, and realign `section-cost-ruler`'s search line to
  the cost block's new indentation. Verify: `bash tools/mutate.sh --mutations
  tools/check-doc-routes-mutations.json` reports `5 mutation(s) run, 5 went red and restored green, 0 problem(s)`
  (`evidence/mutations.log`).

## 4. The documents

- [x] 4.1 In `AGENTS.md`, realign the two stale ranges to their measurement — `1.9–7.0 kB per section` and
  `1.3–5.3 kB each` — and widen the Documentation Map's own sentence: the check reports a row that names
  sub-sections and prices nothing, and a row that prices a section at a figure its measurement does not give.
  Verify: `bash tools/check-doc-routes.sh` exits 0 (`evidence/gate.log`'s route-check tally: all 169 section
  references resolve, all 4 skill references resolve).
- [x] 4.2 In `guidelines/Backlog.md` §9, replace the presence-not-accuracy sentence with the compared rule: the two
  forms, the one-decimal ruler, the report, and that a past measurement is not compared. Verify:
  `bash tools/check-doc-routes.sh` resolves every section reference of the edited document (169 references, 4
  skill references) and `bash tools/check-todo-references.sh` passes (the gate's tally).
- [x] 4.3 Confirm the delta: `openspec validate 2026-10-30-fix-routing-row-figure-is-measured --type change
  --strict` reports the change valid (`evidence/openspec-validate.log`), and the delta's two scenarios name one
  case each (`LOOP-MEASURE-CHANGE … scenarios=2 cases=2 unnamed=0`, `evidence/measure.txt`).

## 5. The gate and the evidence

- [x] 5.1 Copy the cited tallies and logs into this change's `evidence/`, then run
  `bash tools/loop/evidence-check.sh openspec/changes/2026-10-30-fix-routing-row-figure-is-measured --root .`
  before the archive and again over the archived change
  (`bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-30-fix-routing-row-figure-is-measured --root .`).
  Verify: `evidence-check: PASS` both times (`evidence/evidence-check.log` before the archive;
  `evidence/evidence-check-archived.log` over the archived change; and the round-2 run over this round's evidence,
  `evidence/evidence-check-round-2.log`), none dangling.
- [x] 5.2 Run the gate the changed pathspec asks for, once, over the tree that carries the archived change:
  `./tools/loop/gate.sh`. No compiled path is touched, so the build's own gate is not owed (`guidelines/Build.md`
  §2's scope block) and the seven suites are the ones the round-2 `LOOP-MEASURE-CHECK` lines name — the route
  check, its self-test, the mutation set's `--list` and the todo references for `*.md` and `tools/**`, and
  `check-openspec-hygiene.sh`, its self-test and `gen-feature-list.sh --check-classification` for `openspec/**`.
  Verify: `LOOP-GATE: tests=29 failures=0 errors=0` naming all seven suites, every tally green and copied into
  `evidence/gate-tallies/` as seven files (`evidence/gate.log`), and `LOOP-MEASURE-GATE seconds=8 suites=7`
  (`evidence/measure-round-2.txt`) — the same seven `LOOP-MEASURE-CHECK` lines the block names. The item's first
  gate ran four of them and is not the record this task cites.

## 6. Archive

- [x] 6.1 Archive the change, confirm the directory exists under `openspec/changes/archive/` and that the ADDED
  requirement merged into `openspec/specs/documentation-ownership/spec.md`. Verify: the requirement is a `### `
  heading of the shipped spec, the capability's other requirements are unchanged, and
  `bash tools/check-doc-routes.sh` still exits 0 over the archived tree — the round-2 gate runs that check over the
  archived delta's own paths and reports all 169 section references resolving (`evidence/gate.log`), and
  `check-openspec-hygiene.sh` reports the archived tree's 15 open change(s) carrying task markers and every
  capability spec stating its Purpose.
