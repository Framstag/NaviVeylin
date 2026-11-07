# Tasks — a run records the bounds its numbers were measured against

Every task names its own verification, and each verification below was run in the change root at
`/home/tim/projects/NaviVeylin/.pi/worktrees/loop`. The case was written first and is red on the base `f47316cd`: with
the engine as the base left it, `state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst` fails at `the record
names the character bound its items' results were cut at: expected '8000', got 'null'` and the runner ends `1 of 1
cases failed` — `evidence/red-on-HEAD.log` (2026-11-07T15:12:58Z, taken with the case as it now stands and the engine
at the base: `git diff --stat tools/loop/state.sh tools/loop/report.sh` was empty for that run), transcribed to
`evidence/red-on-HEAD.xml`. No compiled file is touched, so this change owes no build: the shell suite is the whole
gate for it.

- [x] 1. Write the red case first, and register it. `state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst`
  reads the three numbers out of `tools/loop/loop.conf` (never out of a literal), initialises a run, and asserts the
  record, the live view and the report each name them; it also asserts the two absences the scenario states — a
  configuration that records no result bound is recorded as `0` rather than as the number the last run used, and a
  state that predates the field prints `?` — and the two registrations in `LOOP_SHELL_CASES` and the dispatch gain the
  name.
  - verified: `tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS` (twice: the first
    run named the two files `new_project` starts, which the declaration then carried), and the case fails on the
    base — `evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`.

- [x] 2. `tools/loop/state.sh` `init`: read `LOOP_TOOL_RESULT_MAX_CHARS`, `LOOP_ITEM_CONTEXT_WARN` and
  `LOOP_ITEM_CONTEXT_MAX` from the configuration file the run resolved (`LOOP_CONF_PATH`) with the `sed` read the
  adapter and `gate-attribute.sh` make of the keys they apply — read, never sourced, and never through
  `loop_conf_value`, because a worker child carries `LOOP_TOOL_RESULT_MAX_CHARS=8000` in its own environment and a run
  started from inside one would record the parent's bound — normalise a missing or non-numeric value to `0`, write
  them as `toolResultMaxChars`, `itemContextWarn`, `itemContextMax`, and print them on a `state: bounds …` line.
  - verified: `bash -n tools/loop/state.sh tools/loop/report.sh tools/loop-cases.sh tools/loop-mutations.sh` → no output,
    exit 0, and `tools/loop-selftest.sh --case 'state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst'` → `PASS`
    (`evidence/green-on-fix.log`).

- [x] 3. `tools/loop/state.sh show` prints a `bounds` line beside `budget`/`flags`, and `tools/loop/report.sh` names
  the same three in its header, `?` where a state predates the fields; no table column is added, renamed or moved.
  - verified: the same case asserts both readers (`result bound 8000`, `context max 200000`), `evidence/green-on-fix.log`.

- [x] 4. `guidelines/Backlog.recipes.md`:878 "Leftovers, the report and the queue" states the rule and why the numbers travel with it, in the paragraph
  that already owns what the report names; the sentence is not restated anywhere else.
  - verified: `./tools/look.sh 'toolResultMaxChars' --files` → `10 matches in 5 files` (`tools/loop/state.sh` 3,
    `tools/loop/report.sh` 1, `tools/loop-cases.sh` 4, `tools/loop-mutations.sh` 1,
    `guidelines/Backlog.recipes.md` 1); `./tools/look.sh 'itemContextMax' --files` → `7 matches in 4 files`. The same
    two commands report `16 matches in 8 files` and `11 matches in 7 files` over the whole tree, because this
    change's own `proposal.md`, `design.md` and `tasks.md` name the keys too; the figures above are the rule files
    with those three set aside. `tools/check-doc-routes.sh` → `all 329 section references resolve, all 7 skill
    references resolve`.

- [x] 5. `tools/loop-mutations.sh` record `95.1` writes a result bound no configuration holds, so the case that names
  the invariant is red when the record stops following the configuration.
  - verified: `bash tools/loop-mutations.sh --check` → `all 273 cases passed — every selected record applies to the
    file it names`; `--only 95.1` → `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`
    (`evidence/revert-check-95.1.log`, 2026-11-07T15:07Z); `--list | wc -l` → `273` records (272 before).

- [x] 6. The suites the delta owes, scoped by what it touched (engine paths, no compiled file).
  - verified: `tools/loop-selftest.sh --group state` → `all 27 cases passed … 21.02 s` (`evidence/state-group.log`);
    `--group report` → `all 22 cases passed … 18.30 s` (`evidence/report-group.log`); `--group run` → `all 130 cases
    passed … 217.39 s` (`evidence/run-group.log`); `--group driver` → `all 23 cases passed … 25.73 s`
    (`evidence/driver-group.log`). The case count moves by one: `tools/loop-selftest.sh --list | wc -l` → `356` (355
    before). The project's gate is deferred to the run's gate child for this item, so no gate tally is claimed here.

- [x] 7. The change is valid and its delta replaces nothing it does not restate.
  - verified: `openspec validate fix-the-tool-result-cap-is-8-000-characters --strict` → `Change
    'fix-the-tool-result-cap-is-8-000-characters' is valid`; `tools/check-openspec-hygiene.sh` → `OpenSpec artifacts
    are consistent`; `tools/check-portability.sh` → `OK — 56 engines and 7 templates name no project value`.

## Still owed by #815, not by this change

This change makes a run's bounds readable, which is what a cap experiment is judged against; it does not take that
experiment. #815 still owes the one run at another `LOOP_TOOL_RESULT_MAX_CHARS` and the assertion that pairs an
item's reached `metrics.context` with the trim bound it was measured under. Both need their own round; until they
land the cap is unmeasured, not priced.
