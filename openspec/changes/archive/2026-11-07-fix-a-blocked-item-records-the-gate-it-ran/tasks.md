# Tasks — a blocked item keeps the gate it ran

Every task names its own verification, and each verification below was run in the change root at
`/home/tim/projects/NaviVeylin/.pi/worktrees/loop`. No compiled file is touched, so this change owes no build: the
loop's shell suite is the whole gate for it, and this item's gate is deferred to the run's gate child, so no gate tally
is claimed here — `./tools/loop/measure.sh --change fix-a-blocked-item-records-the-gate-it-ran` prints
`LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not
run` (`evidence/measure.txt`).

- [x] 1. Write the red cases first, and register them. `run#aRedGateIsBlockedByTheTail` gains the recorded tally, the
  absence of the report's gate-rerun line and `tools/loop/report.sh` in its `# files:` declaration;
  `run#aBlockedItemRecordsTheRoundThatReadIt` stages the same red gate with `--review on`, so the round before the gate
  reads the diff, and asserts the round's verdict, its count and the report's review line. Both names are registered
  (`LOOP_SHELL_CASES` and the dispatch).
  - verified: `tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`, and both cases
    fail on the base `233cd286` with `tools/loop/run.sh` unmodified (`git diff --stat tools/loop/run.sh` empty at the
    time of the run) — `evidence/red-on-HEAD.log`, `evidence/red-on-HEAD-round.log`, transcribed to
    `evidence/red-on-HEAD.xml` and `evidence/red-on-HEAD-round.xml` (`tests=1 failures=1` each).

- [x] 2. `tools/loop/run.sh`: the four arms that do not close pass `--gate "$gate_field"` (`falsified`, `skipped`,
  `cut`, `blocked`), and the round before the gate keeps its answer in `pre_gate_verdict`, seeded into
  `review_verdict`/`review_rounds` when no refusal took precedence. `tools/loop-mutations.sh` record `79.3` — the site
  that decides which findings the gate child is handed — is realigned to the variable the same reading now writes.
  - verified: `bash -n tools/loop/run.sh tools/loop-cases.sh tools/loop-mutations.sh` → no output, exit 0, and both
    cases → `PASS` (`evidence/green-on-change.log`).

- [x] 3. `tools/loop-mutations.sh` records `97.1` (the tally the four non-closed arms pass, replaced by an empty value)
  and `97.2` (the round's seed dropped), so each case is red on the arm it names.
  - verified: `bash tools/loop-mutations.sh --only 97.1` → `red-when-mutated: 1 · not-noticed: 0 ·
    restore-failures: 0` (`evidence/revert-check-97.1.log`); `--only 97.2` → the same
    (`evidence/revert-check-97.2.log`); `bash tools/loop-mutations.sh --check` → `all 277 cases passed — every
    selected record applies to the file it names`; `--list | wc -l` → `277` (`git show
    HEAD:tools/loop-mutations.sh` counted the same way → `275`).

- [x] 4. The suite the delta owes, scoped by what it touched (two engine paths, no compiled file): the group that owns
  the arms, the declaration check the delta owes because it wrote cases, and the change's own count.
  - verified: `tools/loop-selftest.sh --group run` → `all 131 cases passed` (`evidence/run-group.log`; the group held
    130 before this change); `tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`
    (`evidence/declaration-check.log`); `tools/loop-selftest.sh --list | wc -l` → `358`, and
    `LOOP-MEASURE-SUITE cases=358 cmd=tools/loop-selftest.sh --list` (`evidence/measure.txt`), 357 before.

- [x] 5. The change's own documents: `openspec validate fix-a-blocked-item-records-the-gate-it-ran --strict`, the
  openspec hygiene check and the doc-route check.
  - verified: `Change 'fix-a-blocked-item-records-the-gate-it-ran' is valid` (`evidence/openspec-validate.log`);
    `OpenSpec artifacts are consistent: 9 open change(s) carry task markers, …` (`evidence/openspec-hygiene.log`);
    `check-doc-routes: all 329 section references resolve, all 7 skill references resolve`
    (`evidence/doc-routes.log`); and the block's own count,
    `LOOP-MEASURE-CHANGE fix-a-blocked-item-records-the-gate-it-ran scenarios=2 cases=2 unnamed=0`
    (`evidence/measure.txt`).
