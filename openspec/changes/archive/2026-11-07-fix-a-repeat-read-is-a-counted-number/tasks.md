# Tasks — a repeat read is a counted number

Every task names its own verification, and each verification below was run in the change root at
`/home/tim/projects/NaviVeylin/.pi/worktrees/loop`. The case was written first and is red on the base `49afe102`: with
the engine restored to the base for the run, `report#anItemRecordsItsRepeatReads` fails at `the item's file reads are
counted from its event stream, read and ctx_read alike: expected '5', got 'null'` and the runner ends `1 of 1 cases
failed` — `evidence/red-on-HEAD.log` (2026-11-07T15:51:44Z, `git show 49afe102:tools/loop/{state,report}.sh` with
`git diff --stat` empty for both at the time of the run and the change's versions copied back after it), transcribed to
`evidence/red-on-HEAD.xml`. No compiled file is touched, so this change owes no build: the loop's shell suite is the
whole gate for it, and this item's gate is deferred to the run's gate child, so no gate tally is claimed here.

- [x] 1. Write the red case first, and register it. `report#anItemRecordsItsRepeatReads` writes three item streams —
  one session that reads a path three times and another once plus a second session that re-reads it, one that reads a
  path twice, one that opens no file — and asserts the three recorded figures, the row's `repeat/total` cell, the
  trend's line, and both dashes; the two registrations (`LOOP_SHELL_CASES` and the dispatch) gain the name.
  - verified: `tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`, and the case
    fails on the base — `evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`.

- [x] 2. `tools/loop/state.sh` `child_usage_json`: one pass over the stream that keeps the session each event belongs
  to, the path each `ctx_read`/`read` `toolCall` named (`arguments.path`, else `arguments.file_path`) and the characters
  of the matching `tool_execution_end` result, then one reduce counting a `(session, path)` pair's later reads as
  repeats; record `reads`, `dupReads` and `dupChars` — null when the stream measured no read — and write them into the
  item's `metrics` in `done` beside `calls`/`turns`, plus the function's fallback blob.
  - verified: `bash -n tools/loop/state.sh tools/loop/report.sh tools/loop-cases.sh tools/loop-mutations.sh` → no
    output, exit 0, and `tools/loop-selftest.sh --case 'report#anItemRecordsItsRepeatReads'` → `PASS`
    (`evidence/green-on-change.log`).

- [x] 3. `tools/loop/state.sh report` prints the item's figure as `repeat/total` in a column appended after the
  calls-per-turn one, `-` where the stream measured no read; every existing column keeps its index, and
  `report#wallTimeAndReasonInTheTable` — the one case that counts a row's columns — moves from seventeen to eighteen.
  - verified: the same case asserts cell `18` of three rows (`2/5`, `1/2`, `-`) and the neighbouring cases' cells 16
    and 17 are unmoved — `evidence/report-group.log`.

- [x] 4. `tools/loop/report.sh` prints the trend's `reads:` line beside `calls:` — the repeats, the file reads they are
  a share of, the percentage, the characters re-injected and the worst item — with the two dashes, the state's missing
  key told apart from a `null` it wrote itself.
  - verified: the same case asserts the line's text, both dashes, and the row's dash — `evidence/green-on-change.log`.

- [x] 5. `guidelines/Backlog.recipes.md`:254 states the rule and why the figure is per session, in the paragraph that
  already owns what a child re-reads of its own work; `guidelines/Backlog.recipes.md`:892 stops calling the
  calls-per-turn column the row's last one and names the appended one; no sentence is restated in the prompts or in `tools/loop/loop.conf`.
  - verified: `./tools/look.sh 'dupReads' --files` → `16 matches in 6 files` (`tools/loop/state.sh` 5,
    `tools/loop/report.sh` 4, `tools/loop-cases.sh` 4, `tools/loop-mutations.sh` 1, `guidelines/Backlog.recipes.md` 1,
    one archived change's `design.md` 1 with this change's own `proposal.md`); `tools/check-doc-routes.sh` → all
    section and skill references resolve.

- [x] 6. `tools/loop-mutations.sh` records `96.1` (the counting branch dropped) and `96.2` (the characters the line
  prints renamed), so the case that names the invariant is red on the same stream.
  - verified: `bash tools/loop-mutations.sh --only 96.1` → `red-when-mutated: 1 · not-noticed: 0 ·
    restore-failures: 0` (`evidence/revert-check-96.1.log`); `--only 96.2` → the same (`evidence/revert-check-96.2.log`);
    `bash tools/loop-mutations.sh --check` → every selected record applies to the file it names; `--list | wc -l` →
    `275` records (273 before).

- [x] 7. The suites the delta owes, scoped by what it touched (engine paths, no compiled file).
  - verified: `tools/loop-selftest.sh --group report` → `all 23 cases passed` (`evidence/report-group.log`);
    `--group state` → `all 27 cases passed` (`evidence/state-group.log`); `--group driver` → `all 23 cases passed`
    (`evidence/driver-group.log`); `--group run` → `all 130 cases passed` (`evidence/run-group.log`); `--list | wc -l`
    → `357` (356 before, the `LOOP-MEASURE-SUITE cases=356` line the block printed before this change, `cases=357`
    after it); `openspec validate fix-a-repeat-read-is-a-counted-number --strict` → `Change … is valid`
    (`evidence/openspec-validate.log`); `tools/check-openspec-hygiene.sh`, `tools/check-portability.sh` and
    `tools/check-doc-routes.sh` → consistent / OK / all references resolve (`evidence/openspec-hygiene.log`,
    `evidence/portability.log`, `evidence/doc-routes.log`).
