# Tasks — the bound case asks a phase the bound leaves room in

Ordered; each task names its own verification. The case is its own red case, so the red on the base is a measurement
and not a claim, and it was taken before the case was touched.

- [x] 1.1 `tools/loop/tests/loop-cases.sh`, the case only: its three legs ask `prescreen` instead of `A`, the state
      assertion names that phase, the package's opening delimiter is asserted beside its first line, and the comment
      above the case states the arithmetic the phase change buys. Verified: red on the base `a1404a6c` with
      `'PKG-LINE-001' not found in … [PHASE-BOUND: cut at 4000 characters …]`
      (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`, tests=1 failures=1, 2026-11-14T18:23:56Z) and green on
      this tree (`evidence/red-case-green.log`, `evidence/red-case-green.xml`, tests=1 failures=0, 2026-11-14T18:27:29Z).
- [x] 1.2 The numbers the comment quotes are the failing answer's own, each with the command that took it, in
      `evidence/window.txt`: the 2 630-byte checklist section (`git show <rev>:tools/loop/prompts/worker.md | awk …`),
      the 262-byte notice, the 3 736-byte window and the 3 696 bytes to the delimiter, both read out of the red run's
      answer by `awk`.
- [x] 2.1 `guidelines/Backlog.recipes.md`, the section its heading numbers "The gate and its suite", gains the paragraph that states the rule: a case
      that asserts what a bound keeps asks a fixture the bound leaves room for. No numeric claim is added to the
      guideline. Verified: `tools/check-doc-values.sh` and `tools/check-doc-routes.sh` pass (`evidence/doc-checks.log`).
- [x] 3.1 The cases this delta reaches still pass: the sibling `phase#oneCallPrintsThePhaseState`, which asserts the
      shape of the same call at phase A, and the modified case. Verified: `--group phase`
      (`evidence/phase-group.log`).
- [x] 3.2 The declaration the driver checks is unchanged and still true — `prescreen` reads the same prompt file — so
      `driver#everyCaseDeclaresTheFilesItCanReach` passes without an edit to the `# files:` line. Verified:
      `evidence/declaration-case.log`.
- [x] 4.1 This change directory is a structural check rather than a build: no compiled file is touched, so the build
      task is replaced by `tools/check-openspec-hygiene.sh`, which passes on this directory
      (`evidence/openspec-hygiene.log`).
- [x] 4.2 `tools/loop/evidence-check.sh` passes on this change directory — C1 cites no `§`, C2 confirms the four quoted
      tally pairs against the two XMLs, C3 cites no `/tmp` pointer, C4 holds no mutation marker, C5 resolves all 152
      `file:line` references (`evidence/evidence-check.log`).
