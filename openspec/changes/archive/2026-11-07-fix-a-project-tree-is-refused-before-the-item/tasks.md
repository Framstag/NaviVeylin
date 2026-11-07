# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why, `specs/agent-issue-loop/spec.md` for the
contract.

## 1. The red case

Requirement: "The base a run records is the base branch's commit", scenario "A project tree carrying uncommitted work
is refused before the pre-screen".

- [x] 1.1 `tools/loop-cases.sh`: `run#aDirtyProjectTreeIsRefusedBeforeThePreScreen` — a project from `new_project`
      with one file written into it, `stub_success` so a run that is *not* refused would work an item, then the run
      asserted on exit 5, no `loop: pre-screen ·` line in the output, the path printed, the sentence about what the
      run cannot tell, the reason recorded (and printed with it), an empty queue, no item record, no `gh` write (no
      adoption, no escalation), no `pi` process, and the file still in the tree. Its dispatch entry and its
      `LOOP_SHELL_CASES` entry are added with it. Verify: on HEAD `fca2c857` it fails with `expected '5', got '0'`
      (2026-11-07T06:35:48Z, `evidence/red-on-HEAD.log`; tally `tests=1 failures=1 errors=0` off the runner's
      `1 of 1 cases failed` line, the shell runner writing no JUnit XML), and
      `tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` was green before that run.
- [x] 1.2 The case's first line declares `tools/loop/run.sh tools/loop/fixtures/scenarios/ tools/pi-child-events.sh
      tools/tooling.json` — what `run_loop`, `new_project` and `stub_success` reach. Verify: the driver case above,
      which refuses an undeclared reach, `PASS` before and after the case was written.
- [x] 1.3 The four cases the guard reaches are realigned, because the plan's surface count missed them:
      `run#doesNotCloseOnAMergeTheBaseCannotTake` and `run#anAdoptionTheTreeRefusesIsRecordedAsUnmergeable` reached
      the landing step by dirtying the project tree *before* the run, so they now dirty it while the run is live — by
      the item's own child (a `stub_write` extra action) and by the case's own tracker-stub wrapper, which writes on
      reading `issue list` — and `run#awaitingDecisionIsNotEscalatedAgain` and
      `prescreen#aHoldWithNoDecisionIsNotADecision` keep their stub's request log in the case's scratch tree instead
      of inside the project. Verify: each green on its own, and the two group selections in 4.1 green with them.

## 2. The engine

- [x] 2.1 `tools/loop/run.sh`, after the module-pin arm and before the decision cap: read
      `git -C "$LOOP_ROOT" status --porcelain` once, and on a non-empty answer print `refusing to start: the project
      tree carries uncommitted work, and the landing step merges into it`, the lines indented, the sentence that the
      run cannot tell its own leftover from its owner's edit and discards nothing, and the command that lists them
      again; then `stop_run` with the paths in the reason, print the report and exit `$LOOP_REFUSE`. It sits after
      that arm because a bogus gitlink is that arm's refusal (with its own repair) and shows up in this tree's status
      as well. The comment above it names the two landing-step refusals (`worktree.sh:508`, `:548`), why this refusal
      is the pre-flight's and not a cleanup, and that `--reset-worktree` is not offered.
- [x] 2.2 `tools/loop-mutations.sh`: record 90.1 — `tools/loop/run.sh`, case
      `run#aDirtyProjectTreeIsRefusedBeforeThePreScreen`, the guard's `if` replaced with `if false; then`, with the
      comment that names the four archived records the missing guard cost. Verify: `bash tools/loop-mutations.sh
      --check` green (267 records), and 90.1 red-when-mutated (2026-11-07T06:47:18Z) and green again on restore.

## 3. The prose the rule lives in

- [x] 3.1 The change's spec delta: "The base a run records is the base branch's commit" gains the cleanliness clause
      and the scenario, and carries the five scenarios the shipped requirement already has. Verify:
      `openspec validate fix-a-project-tree-is-refused-before-the-item --strict` and
      `tools/check-openspec-hygiene.sh` green.
- [x] 3.2 `guidelines/Backlog.recipes.md` §7d: the paragraph on what the start's guard covers names the tree's
      uncommitted work beside its branch, the exit, the paths, and the one repair it is *not* given. Verify:
      `tools/check-doc-routes.sh` and `tools/check-todo-references.sh` green.

## 4. Verification

- [x] 4.1 The red case and every case the delta declares pass on the change: the declaration case first, then
      `tools/loop-selftest.sh --case 'run#*'` (129 of 129 passed, 2026-11-07), then the selection the change's
      pathspec owes (`tools/loop-selftest.sh --from-git fca2c8576b3e8d4fa0b9717b5a328d134859a0fd`, 349 of 349 passed,
      446.01 s). The logs are `evidence/scoped-run-group.log` and `evidence/scoped-from-git.log`.
- [x] 4.2 The gate is **not** this worker's (the run deferred it): the diff is left for the reviewer and the gate
      child, and no `tools/loop/gate.sh` run is claimed here.
- [x] 4.3 The gate the run then took failed in two portability cases, and the cause was this guard: the run locks and
      initialises its state **before** the arm reads the tree, so in a project that does not ignore `.pi/` the run
      refused itself — the foreign fixture has no `.gitignore` and no owner edits, and it was refused on `.pi/` and
      `nohome.gitconfig` (`evidence/red-foreign-refused-by-its-own-state-dir.log`). Two answers, both here: the arm
      drops the run's own state directory from its reading (the directory this process wrote seconds ago is not the
      owner's work), and the foreign fixture gains the `.gitignore` a real project has, listing `.pi/` and the files
      its own stubs and metadata pass write. Verify: `foreign#toolchainRunsEndToEnd` and
      `porting#everyCommandInTheRecipeRuns` both PASS (`evidence/green-foreign.log`, `evidence/green-porting.log`).
