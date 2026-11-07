# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why,
`specs/agent-issue-loop/spec.md` for the contract.

## 1. The red cases, before any engine change

Requirement: "A branch the loop made is removed when its work is in the base", scenarios "A set-aside branch whose
work the base carries is swept" and "A set-aside branch that still carries work is kept and named"; and "One command
reports and removes what the loop left", scenario "A worktree no run keeps is reported, and removed only with
`--apply`".

- [x] 1.1 Add `worktree#aSetAsideBranchWhosePatchesLandedIsRemoved`, `worktree#aSetAsideBranchThatCarriesWorkIsNamedNotRemoved`
      and `clean#reportsAWorktreeNoRunKeeps` with their list and dispatch entries, each with its `# files:` line.
      Verify: `tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach` → PASS
      (2026-11-06T17:52:44Z; the first run refused the two set-aside cases for a `tools/loop/state.sh` declaration
      they lacked).
- [x] 1.2 The two set-aside cases fail on the base with the engine change reverted: `the sweep keeps a set-aside
      branch that carries work, and names why` and `a set-aside branch whose patches are in the base is swept`
      (2026-11-06T17:53:09Z–17:53:12Z, `evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`, tests=2 failures=2).
- [x] 1.3 The worktree case fails on the base the same way: `the report names the tree no run keeps` —
      `'.pi/worktrees/767' not found in: loop-clean: nothing to remove — 1 path(s) kept`
      (17:53:16Z–17:53:18Z, `evidence/red-on-HEAD-worktree.log`, `evidence/red-on-HEAD-worktree.xml`, tests=1
      failures=1).

## 2. The engine

- [x] 2.1 `tools/loop/lib.sh`, `loop_own_branches`: enumerate the set-aside namespace in a block beside the two
      patterns it already names, and say on the remote arm why its two patterns stay two. Verify:
      `tools/loop-selftest.sh --case 'worktree#aSetAside*'` → 2 of 2 PASS (2026-11-06T17:53:30Z–17:53:33Z,
      `evidence/green-on-change.log`).
- [x] 2.2 `tools/loop/loop-clean.sh`: the arm for the worktrees under the directory the run's own worktree lives in
      that the run does not keep, judged by what each holds and removed only with `--apply`. Verify:
      `--case 'clean#reportsAWorktreeNoRunKeeps'` → PASS (17:53:33Z–17:53:35Z, `evidence/green-on-change-worktree.log`),
      and again after the arm was made to read that directory from `LOOP_WORKTREE` rather than name it
      (17:56:52Z–17:57:14Z, `evidence/green-after-refactor.log`: the case, `--case 'clean#*'` 12 of 12 and
      `--case 'worktree#aSetAside*'` 2 of 2).
- [x] 2.3 `tools/loop-mutations.sh`: record `88.1`, which puts the set-aside pattern back out of
      `loop_own_branches`. Verify: `bash tools/loop-mutations.sh --only 88.1` → `red-when-mutated: 1 ·
      not-noticed: 0 · restore-failures: 0` (17:55:02Z–17:55:05Z, `evidence/mutation-88.1.log`), and
      `--check --files tools/loop/lib.sh --files tools/loop/loop-clean.sh --files tools/loop-cases.sh` → `all 13
      cases passed — every selected record applies to the file it names` (17:55:08Z, `evidence/mutation-check.log`).
- [x] 2.4 `guidelines/Backlog.recipes.md`, the sections "Refusals, records and the landing" and "Leftovers, the report
      and the queue": one paragraph each — the sweep's candidate set now names the
      set-aside namespace and says which reading decides it, and the report's arms gain the worktrees it judges by
      what they hold. Verify: `grep -n "set-aside" guidelines/Backlog.recipes.md`.

## 3. Verification

- [x] 3.1 The three cases pass together on the change (17:53:30Z–17:53:35Z, the two logs above and their tallies).
- [x] 3.2 The cases that own the sweep and the report are unchanged: `--case 'clean#*'` → 12 of 12 PASS
      (17:53:36Z–17:53:49Z, `evidence/scoped-suite-clean.log`), `--case 'worktree#*'` → 24 of 24 PASS,
      `--case 'report#landed*'` → 2 of 2 PASS, `--case 'run#theEndSweeps*'` → 1 of 1 PASS, and
      `run#aMergedBranchIsRemoved`, `run#theRemoteSweepKeepsAnOpenPullRequest`,
      `run#theSweepComparesAgainstTheFetchedBase` PASS (17:53:50Z–17:54:44Z,
      `evidence/scoped-suite-worktree.log`); `--case 'worktree#*'` again on the final tree → 24 of 24 PASS
      (17:56:52Z–17:57:14Z, `evidence/structural-checks.log`).
- [x] 3.2a The portability and mutation checks the changed files owe: `tools/check-portability.sh` → `OK — 56 engines
      and 7 templates name no project value` (17:56:52Z, and again 17:58:05Z after the header and the arm's
      directory were worded from `LOOP_WORKTREE`) and `loop-mutations.sh --check` scoped to the changed files →
      `all 13 cases passed` (17:57:10Z, 17:58:05Z), both in `evidence/structural-checks.log` and
      `evidence/final-checks.log`.
- [x] 3.3 The delta and the change are ready: `tools/check-change-readiness.sh
      openspec/changes/fix-the-leftovers-nothing-reads` and `openspec validate fix-the-leftovers-nothing-reads
      --strict` (see `evidence/`).
- [x] 3.4 The change owes the structural checks of a `tools/**` roster rather than a build: the case-set declaration
      above, the mutation `--check` scoped to the files it touches (2.3), the portability check (3.2a) and the doc
      routes of the two guideline paragraphs — `bash tools/check-doc-routes.sh` → `all 329 section references
      resolve, all 5 skill references resolve` (17:57:44Z and 17:58:05Z, `evidence/final-checks.log`).
- [x] 3.5 The gate is not this session's: it is deferred to a fresh child after a reviewer has read the diff, so no
      `./tools/loop/gate.sh` was run here.
