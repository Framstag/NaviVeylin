# Tasks — the census and the first-step map take the case directory

- [x] 1. The red legs first, taken on the run's base commit `0e278789` as it stands: a **directory leg** in
      `census#theCensusIsANumber` (a scratch directory holding the fixture case text in two files, asked for the same
      counts), a **sibling-cases-file step** in `gate#aPathACheckReadsOwesThatStepsCommandSet` (a delta whose only path
      is `tools/loop/tests/cases/run.sh` owes the declaration's case) and a **core-directory leg** in
      `driver#aFileFilterRunsOnlyTheCasesTheDeltaOwns` (a core list naming a directory selects a path below it). All
      three read `FAIL` on the base; the runs are copied into `evidence/` at the moment they are produced
      (`evidence/red2-directory-leg.log`, `evidence/red2-core-directory-leg.log`,
      `evidence/red2-sibling-cases-file-leg.log`), and each tally is transcribed into a JUnit-shaped XML beside its log
      because a shell self-test prints `PASS`/`FAIL` lines (`.pi/skills/revert-check`, "When the gate is a shell
      self-test"). Verified by the three `FAIL` lines and each run's own `1 of 1 cases failed`.

- [x] 2. `tools/loop/census.sh` reads a set: `--cases` takes a file or a directory, the directory is its `*.sh` files
      resolved once into an array, the dead `case_text` read is deleted, and the phrase test greps the array;
      `tools/loop/census.sh:55`'s file-only refusal becomes a refusal naming an argument that is neither, and
      `tools/loop/census.sh:51` refuses a directory holding no case file. Verified by the directory leg of
      `census#theCensusIsANumber` (`guards: 3`, `no-case: 2`) and by `census#theCensusRefusesNothing` staying green
      (`evidence/green2-census-theCensusIsANumber.log`, `evidence/green2-census-theCensusRefusesNothing.log`).

- [x] 3. `tools/loop/loop.conf:1084` gains the pair `tools/loop/tests/cases/*|tools/loop/tests/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach`, beside the row keyed on the one cases file. Verified by the
      sibling-cases-file step of `gate#aPathACheckReadsOwesThatStepsCommandSet`
      (`evidence/green2-gate-aPathACheckReadsOwesThatStepsCommandSet.log`), and by the same case's existing steps —
      `tools/loop/worktree.sh` still owes what its own globs name, and no other step's command appears.

- [x] 4. `loop_case_core_files` (`tools/loop/tests/loop-cases.sh:616`) names `tools/loop/tests/cases/` beside the one
      cases file, and `files_select` (`tools/loop/tests/loop-selftest.sh:887`) reads a core token ending in `/` as
      owning the paths below it, the rule its own declaration loop already applies at
      `tools/loop/tests/loop-selftest.sh:907`. Verified by the core-directory leg of
      `driver#aFileFilterRunsOnlyTheCasesTheDeltaOwns` (`selected 4 of 4 cases`,
      `evidence/green2-driver-aFileFilterRunsOnlyTheCasesTheDeltaOwns.log`) and by that case's existing four legs
      staying green.

- [x] 5. The repair the item's recorded decision names: `tools/loop/tests/loop-selftest.sh:899` restores the guard
      that skips the cases file when `--from-git` narrowed to one case body, in the non-directory branch, so the
      rewrite keeps #917's narrowing; the directory branch, the `STUB_CORE_DIR_CASES` fixture and the two new
      assertions are untouched. Verified by `driver#aHunkInACaseBodySelectsThatCaseOnly`,
      `driver#aHunkOutsideEveryCaseBodyOwesEveryCase` and `driver#aCoreFileOwesEveryCase` all green on the change
      (`evidence/green2-driver-aHunkInACaseBodySelectsThatCaseOnly.log`, `evidence/green2-hunk-selection-leg.xml`),
      where the first of them was the red leg of the first attempt.

- [x] 6. `guidelines/Backlog.recipes.md`, its gate-and-suite section (the heading "The gate and its suite", issue #1104), realigned: the
      sentence naming `tools/loop/tests/loop-cases.sh` as the path that owes the declaration's case now names a cases
      file of the set. Verified by `bash tools/loop/evidence-check.sh <the change dir> --root .` (its C6 resolves the
      route's section id against the document's own headings) and by reading the changed sentence against the diff.

- [x] 7. The gate, run once (`./tools/loop/gate.sh`, detached and polled). Surface counted before the plan was fixed
      with `tools/look.sh`: `LOOP_GATE_FIRST_STEPS` 30 matches in 16 files, `loop_case_core_files` 24 in 14,
      `files_select` 6 in 4. No compiled file changes, so no build task stands in place of the build: the structural
      checks are tasks 2–6's own cases, and the gate is the suite. Verified by the gate's `LOOP-GATE:` line.
