# Tasks — a close over a red gate keeps its clause

Every task names its own verification, and each verification below was run in the change root at
`/home/tim/projects/NaviVeylin/.pi/worktrees/loop`. No compiled file is touched, so this change owes no build: the
loop's shell suite is the whole gate for it, and this item's gate is deferred to the run's gate child, so no gate tally
is claimed here — `./tools/loop/measure.sh --change fix-a-close-over-a-red-gate-records-its-waiver` prints
`LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not
run` (`evidence/measure.txt`).

- [x] 1. Write the red cases first, and register them. `state#aCloseOverARedGateRecordsWhyItWasWaived` (a waived close
  keeps its clause and suite, a green close keeps neither, and the report prints the count),
  `state#aCloseOverARedGateWithoutAWaiverIsRefused` (the refusal, and the three waivers it refuses), and
  `run#aCloseOverARedGateIsWaivedOrBlocked` (the clause the child recorded reaches the record; the same red tally
  without one is blocked before the merge and nothing is closed on the tracker). All three names are registered
  (`LOOP_SHELL_CASES` and the dispatch), with their `# files:` declarations.
  - verified: `tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`
    (`evidence/declaration-check.log`), and all three fail on the base `eb513ca5` with `tools/loop/state.sh`, `lib.sh`,
    `report.sh` and `run.sh` restored from that commit — `evidence/red-on-HEAD.log`, transcribed to
    `evidence/red-on-HEAD.xml` (`tests=3 failures=3 errors=0`; the loop's shell runner writes no JUnit XML itself).

- [x] 2. `tools/loop/lib.sh`: `gate_tally_state` reads `red` / `green` / `unknown` out of the tally text.
  `tools/loop/state.sh`: `--gate-waived` and `--gate-waived-suite` are recorded into `metrics.gateWaived` /
  `metrics.gateWaivedSuite`, and a `done` over a `red` tally that records neither is refused before `patch_state`.
  - verified: `bash -n tools/loop/lib.sh tools/loop/state.sh tools/loop/report.sh tools/loop/run.sh tools/loop-cases.sh`
    → no output, exit 0; the two `state#` cases → `PASS` (`evidence/green-on-change.log`); and the group that owns the
    script → `all 29 cases passed` (`evidence/state-group.log`).

- [x] 3. `tools/loop/run.sh`: `LOOP-GATE-WAIVED` / `LOOP-GATE-WAIVED-SUITE` are read from the report the item is decided
  from and passed to the `closed` arm only when they are there, and a `closed` outcome over a `red` tally with no
  clause becomes `blocked` after the outcome chain — before the merge and before the closure on the tracker.
  - verified: `run#aCloseOverARedGateIsWaivedOrBlocked` → `PASS` (`evidence/green-on-change.log`), and
    `all 132 cases passed` for the group that owns the outcome block (`evidence/run-group.log`).

- [x] 4. `tools/loop/report.sh`: the trend's `waived:` line prints the waived closes out of the closed items with each
  named suite and its count, `-` for a waiver that named none, and the two dashes the report already distinguishes (no
  closed item this run, a state written before the field existed).
  - verified: the assertion the case makes on the printed line (`waived: 1 of 2 closed items (<suite> ×1)`),
    `all 23 cases passed` for the group that owns the report (`evidence/report-group.log`), and the dash branch on a
    real state written before the field existed — the run of `2026-11-06T21:19:06Z` the issue measures prints
    `waived: - · this state records no gate waiver — it was written before the report counted one`
    (`evidence/probe-report-shapes.log`).

- [x] 5. `tools/loop/prompts/worker-tail.md`: the report block carries `LOOP-GATE-WAIVED` and
  `LOOP-GATE-WAIVED-SUITE`, and item 2 says the close over an attributed red is recorded there. The tail is the process
  that writes the verdict, so it is the one process a waiver can come from.
  - verified: `./tools/look.sh 'gateWaived|gate-waived|LOOP-GATE-WAIVED|gate_tally_state'` → 4 matches in that prompt,
    and the two lines it names are the two `run.sh` reads (`run.sh:2289`–`:2290`); no case executes a prompt's prose, so
    the field those lines name is what `run#aCloseOverARedGateIsWaivedOrBlocked` drives.

- [x] 6. `tools/loop-mutations.sh` records `98.1` (the refusal dropped), `98.2` (the recorded clause dropped) and `98.3`
  (the clause the run reads dropped), one per case.
  - verified: `bash tools/loop-mutations.sh --only '98.1 98.2 98.3'` → `red-when-mutated: 3 · not-noticed: 0 ·
    restore-failures: 0` (`evidence/revert-check-98.log`); `--check` → `all 280 cases passed — every selected record
    applies to the file it names` (`evidence/mutations-check.log`); `--list | wc -l` → `280`, `277` before.

- [x] 7. The change's own documents and the guideline section that owns the rule: one paragraph in
  `guidelines/Backlog.recipes.md`:710–`:786` "Refusals, records and the landing" (the section `Backlog.md`'s loop index
  reaches as `7d`), which owns what a run records of an item.
  - verified: `openspec validate fix-a-close-over-a-red-gate-records-its-waiver --strict` → `Change
    'fix-a-close-over-a-red-gate-records-its-waiver' is valid` (`evidence/openspec-validate.log`);
    `OpenSpec artifacts are consistent: 8 open change(s) carry task markers, …`
    (`evidence/openspec-hygiene.log`); `check-doc-routes: all 329 section references resolve, all 7 skill references
    resolve` (`evidence/doc-routes.log`); `evidence-check: PASS` (`evidence/evidence-check.txt`); and the block's own
    count, `LOOP-MEASURE-CHANGE fix-a-close-over-a-red-gate-records-its-waiver scenarios=3 cases=3 unnamed=0`
    (`evidence/measure.txt`).
