# Tasks — fix-loop-iteration-pays-for-selected-cases

Requirement: `agent-issue-loop` — "A suite run selects what it was asked for and the gate records what it cost".

## 1. The red case first (the bound this change adds)

- [x] 1.1 Write `driver#aGroupFilterRunsOnlyItsOwnCases`, `driver#aFilterThatNamesNoCaseRefuses`,
  `driver#theListIsFilteredTheSameWayASuiteRunIs` and `gate#theSummaryNamesWhatEachSuiteCost` in
  `tools/loop-cases.sh`, with the shared `driver_selection_probe` helper. Verification: each case FAILS on the
  driver and gate of the base commit — recorded in `evidence/red-HEAD.log` (4 of 4, `FAIL`). Scenario:
  all four scenarios of the requirement.
- [x] 1.2 Record that run: `git stash push -- tools/loop-selftest.sh tools/loop/gate.sh`, run the four cases,
  capture the `PASS`/`FAIL` lines into `evidence/red-HEAD.log`, `git stash pop`. Verification: the log carries four
  `FAIL` lines and their reasons.

## 2. The selection (requirement, scenarios 1–3)

- [x] 2.1 `tools/loop-selftest.sh`: parse `--group` beside `--case` in one argv loop, each arm on one line, which
  is the shape the sweep in `usage#noUnguardedValueFlagRemains` reads and clears (it selects a line carrying
  `shift 2 ;;` or `${2:-}` and requires `--flag)` on that same line). Verification:
  `bash tools/loop-selftest.sh --group` exits 2 with `--group needs a value`
  (`usage#bareValueFlagInASingleShotHarnessIsAUsageError`), `bash tools/loop-selftest.sh --group --list` reports
  the missing value instead of reading `--list` as one (`usage#bareValueFlagDoesNotSwallowTheNextFlag`), and the
  sweep's own pipeline over `tools` lists neither arm as an offender.
- [x] 2.2 `tools/loop-selftest.sh`: `matches` (glob, with the `SC2254` disable), `is_glob`, `count_matching`, the
  per-filter refusal against the whole declared set, and the intersected selection. Verification: the driver cases
  of 1.1 pass; `--list --group state` prints 14 lines and `--list --case 'run#*'` 69 on the tree of this change.
- [x] 2.3 `tools/loop-selftest.sh`: a filtered run names its selection in the summary line; the
  single-literal-`--case` invocation keeps its exact output (one `PASS` line, no summary) so the mutation harness
  and the leftovers cases read what they read today. Verification: `--case state#defaultsComeFromLoopConf` prints
  one line; `--group usage` prints its summary with `— selection: --group usage`.

## 3. The gate's record (requirement, scenario 4)

- [x] 3.1 `tools/loop/gate.sh`: `SECONDS` delta per suite, printed on the suite's own line, carried in the
  `LOOP-GATE:` summary beside the command, written as the tally's `time` attribute. Verification:
  `gate#theSummaryNamesWhatEachSuiteCost` passes, and the five pre-existing `gate#` cases still pass
  (`bash tools/loop-selftest.sh --group gate`).

## 4. Revert-check — one mutation per new invariant

- [x] 4.1 Record `49.1` (the group filter), `49.2` (the count the refusal depends on), `49.3` (the recorded
  wall clock) and — since review round 1 — `49.4` (the summary that names the selection) in
  `tools/loop-mutations.sh`. Verification: `bash tools/loop-mutations.sh --check --only '49.1 49.2 49.3 49.4'`
  reports all four applying.
- [x] 4.2 Run them: `bash tools/loop-mutations.sh --only '49.1 49.2 49.3 49.4'` — each mutation makes its named
  case fail for the reason it names, every file is restored byte-identically, and each case is green again
  afterwards. Verification: `evidence/revert-check-49.log`, four records, `restore-failures: 0`.

## 5. The gate for this change

- [x] 5.1 `tools/loop/gate.sh --base abeffcad` in this change's worktree: the shell profile only, because no
  compiled path is in the delta. Verification: `evidence/gate.log` ends with
  `LOOP-GATE: tests=319 failures=0 errors=0 (tools/loop-selftest.sh 900s, bash tools/loop-mutations.sh --check 0s,
  tools/loop-mutations-selftest.sh 83s)` after 900+0+83 s of suite time — the gate records each suite's
  number and no total, which is the honest shape of the record — and those three durations are this change's own
  D2 in action.
- [x] 5.2 `tools/loop-mutations.sh --check` over the whole declared set (103 records), and
  `tools/loop-mutations-selftest.sh` (34 cases). Verification: both green in the same run — `all 103 cases passed`
  for the record check and `all 34 cases passed` for the harness self-test, quoted in `evidence/gate.log`.

## 5a. The change measured on itself

The run above is this change's own subject: the self-test took **900 s** for its 182 cases and the two harness
suites together 83 s, so a delta under `tools/loop/**` owes about **16 min** of suite time and the gate now says so.
The same suite took 811 s in the run round 1 read (`evidence/review-1.md`) and 900 s here, on the same tree family —
which is itself the reason a per-suite number belongs in the record and a single "the suite costs twelve minutes"
does not. The three `driver#` cases this change owns cost 6.5 s / 5 s / 5 s, measured one invocation each, about
16 s together; the fourth case it adds, `gate#theSummaryNamesWhatEachSuiteCost`, lives in group `gate`. Before this
change, a tweak to the driver's argument parsing paid all 182. That ratio — not a projected one, a measured one —
is what the selection buys until #260 and #259 move the whole-suite number.

## 6. Documentation

- [x] 6.1 No guideline change: `guidelines/Build.md` §2a already asks for the focused iteration and §2c already
  owns the timing record; this change adds no number to either. Verification: `tools/check-doc-routes.sh`.
- [x] 6.2 The follow-up — a file→case mapping so a change selects the cases its own touched files own — is filed
  as its own tracker item, with the measured bound (a loop delta owes 723 s of self-test for two touched files).

## 7. Review

- [x] 7.1 An independent read-only review of the diff against the four scenarios and the gate XMLs, before archive.
  Verification: `evidence/review-1.md` — round 1 reported one scenario partial and five notes.
- [x] 7.2 Close review round 1 (`evidence/review-1.md`): scenario 1's "selection is named in the summary line"
  is now asserted (and covered by mutation `49.4`); the group and pattern filters are asserted *together* as an
  intersection (run mode, `driver#aGroupFilterRunsOnlyItsOwnCases`; the list case asserts `--list --group` only);
  the timing case reads the suite's own line and compares its number to the summary's; the record count in 5.2 was
  corrected to the measured 103. Verification: `driver#aGroupFilterRunsOnlyItsOwnCases` and
  `gate#theSummaryNamesWhatEachSuiteCost` green.
- [x] 7.4 Close review round 2 (`evidence/review-2.md`): both value-flag arms were two lines long, so the sweep in
  `usage#noUnguardedValueFlagRemains` — which needs `--flag)` and its bound check on one line — could not see
  either, and round 1's claim about it was false. The arms are one line each now and the sweep clears them, and
  the behaviour is asserted directly: `--group` bare and `--group --list` are in the two usage cases.
  `line_seconds` is declared local in the timing case. Verification: `--group usage` green, and the sweep's
  pipeline over `tools` returns nothing.
- [x] 7.3 Review round 3 over the final tree, before archive. Verification: `evidence/review-3.md` — 4 of 4
  scenarios PASS, both round-2 closures PASS, `openspec validate --strict` valid, and the four documentation-precision
  findings of that round corrected in `tasks.md` and in `evidence/review-2.md` before archiving.
