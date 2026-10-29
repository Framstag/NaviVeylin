# Round 2 — the two cases the widened scenarios name (#197)

The round-1 review blocked on one finding and left one decision: this change widens `routed-read#bound`'s WHEN
from "a section of `guidelines/Build.md`" to "a section that the routing table names", and rewrites
`routed-read#route`'s Case text from "every row that routes `guidelines/Build.md`" to "every row that routes a
lettered sub-section of a document", while neither case is implemented anywhere in the tree — `grep -rn
'routed-read' tools/ .github/` printed nothing, and both names resolved only inside a spec. The owner recorded
option 2: implement both as cases over every routed section and carry their run. This note is that run.

## What the two cases are, and what they are not

`routed-read#route` — every row of the routing table that names a lettered sub-section of a document states what
those sections cost, as a `kB` figure in the same row; a row that names sub-sections and prices nothing is
reported by `tools/check-doc-routes.sh`, naming the row. The check reads the figure's presence, not its
accuracy: a restated number goes stale by hand, and the measured number is the sensor row's
(`routed-section-size`, `tools/sensors/sensors.json`).

`routed-read#bound` — `tools/check-doc-routes.sh --sizes` prints the cost of every section of every document the
table names, one line each, marked `routed` or `not-routed`, with a section's cost being the byte count of its
lines from its `## ` heading through the line before the next `## ` heading — the ruler the requirement states.
The case asserts that the report is the tree's (one line per section, the table's pairs marked and no others)
and that every printed byte count equals an independent `sed … | wc -c` count of the same span. It asserts no
bound, because the bound is the sensor row's: a routed section past 12 000 bytes is a *condition* of
`routed-section-size`, filed and closed by the pipeline, not a failure of this case. Eight such sections are over
the bound on this tree and each is a condition of its own; the case prints their sizes so the number a row
restates is comparable with the measurement.

Both cases live in `tools/check-doc-routes-selftest.sh`, beside the route resolution they depend on, and both
read this repository's own `AGENTS.md` and `guidelines/*.md` instead of a fixture — the claim is about this
tree's route. The self-test's promise is amended accordingly, and it now also asserts that the guideline files
it reads are byte-identical afterwards, as it already did for `AGENTS.md`.

## The table's own rows

Six rows of `AGENTS.md` routed a lettered sub-section and priced nothing; the clause convicted all six when it
was first run. Each now states the cost of its sub-sections, measured with the ruler above on this tree
(`bash tools/check-doc-routes.sh --sizes`, 2026-10-29):

| row | sub-section | bytes |
|---|---|---|
| stylesheets, day/night, basemap, POI icons | `MapRendering.md` §15a / §16a / §16b | 3 139 / 2 432 / 790 |
| phone UI — modes, overlays, layering, controls | `UI.md` §7a / §8a | 13 102 / 2 528 |
| car screens — templates, wait notices, car search | `UI.md` §3b / §3c / §6b | 1 823 / 2 763 / 4 873 |
| the car Surface, renderer startup, host-thread safety | `UI.md` §3a | 4 411 |
| a surface that must live while both sessions are active | `UI.md` §10a | 2 218 |
| search, favourites, settings, persistence | `UI.md` §6a | 6 043 |

The entry document grew from 25 908 to 26 521 bytes, inside its own 32 000-byte bound (`entry-document-size`).

## The run

Every number below is a command's output in this directory: `round-2-sizes.log` for the sizes (the eight
conditioned sections, the eleven sub-sections the six rows now price, the entry document's 26 521 bytes, the 146
sections and 116 routed pairs), `round-2-first-conviction.log` for the clause's first run against `7b5893fa`'s
table (six rows, `exit=1`).

- **Red on HEAD** (`round-2-red-on-head.log`): both cases fail against `tools/check-doc-routes.sh` at
  `7b5893fa` — `--sizes` is an unknown argument (`exit=1`), and a fixture row that routes a lettered
  sub-section of a non-`Build.md` document and prices nothing is not reported (`exit=1`).
- **Green** (`round-2-green.log`): `check-doc-routes-selftest: all 21 cases passed`; the `#bound` line reads
  `146 section(s) printed, 116 routed pair(s), each equal to the ruler's own sed count`; the check resolves
  `all 169 section references … all 4 skill references`.
- **Falsification** (`round-2-mutations.log`): two records added to `tools/check-doc-routes-mutations.json` —
  `route-states-its-cost` (`routed-read#route`) and `section-cost-ruler` (`routed-read#bound`) — plus the
  existing `skill-path` record; `case-cmd` now runs one named case (`--case {case}`) instead of the whole
  suite. `mutate: 3 mutation(s) run, 3 went red and restored green, 0 problem(s)`.
- **Gate** (`round-2-gate.log`, its nine tallies in `gate-tallies-round-2/`): `LOOP-GATE: tests=37 failures=0
  errors=0` over the suites this pathspec owes — the action runtimes' pair, the feature list's classification
  check, the route check and its self-test and mutation listing, the TODO references' check and the openspec
  hygiene's pair. No compiled path, so no Gradle run is owed (`guidelines/Build.md` §2, scope block). The route
  self-test's 21 cases are in the 37 because its summary line now reads `all N cases passed`, which is the
  shape the gate's tally parses. The gate writes one tally per suite and no combined file, so the summary the
  notes quote is carried by `round-2-gate-sum.xml`, which `round-2-gate-sum.sh` derives from those nine — the
  sum, never a new number.

## The two numbers round 1 could not resolve

- `Backlog.md` §9 said "12 894 bytes before this paragraph was added", a value no command in the record
  produced (`evidence/measure-the-tree.txt` carries `Backlog.md §9 bytes=14634`, taken *with* the paragraph).
  It now reads `15 142`, which is the number `bash tools/check-doc-routes.sh --sizes` prints for that section on
  this tree (`round-2-sizes.log`), after the routing-table clause and its one-sentence reference — and
  `design.md`'s two restatements are corrected to the same value, with their attribution moved from
  `measure-the-tree.txt` (which carries the *first* run's numbers) to this round's log.
- Task 3.2's Verify named `openspec validate 2026-10-29-fix-unbounded-agent-context --type change`, and no
  output of it was ever carried. It cannot be produced now: the change is archived, and the CLI reads
  `openspec/changes/`, so it reports `Change … has issues — No deltas found` (`exit=1`). What holds instead is
  recorded here so the claim is measured rather than assumed: `openspec validate --changes --strict` over the
  open set prints `No items found to validate` (`exit=0`), and `bash tools/check-openspec-hygiene.sh` passes in
  the gate above. The shipped requirement's own strict warnings (three `Requirement text is very long (>500
  characters)`, pre-existing on the archived text) are the validator's, and repairing them is a change of its
  own.
