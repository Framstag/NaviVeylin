# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why,
`specs/agent-issue-loop/spec.md` for the contract.

## 1. The case

Requirement: "A gate run in a kept worktree runs under the environment that worktree records", scenario "A hand-run
gate sees the worktree's recorded environment".

- [x] 1.1 `tools/loop-cases.sh`: `gate#runsUnderTheWorktreeEnvironment` — a project carrying `.pi/loop-env.sh`, a
      compiled step whose script writes what it sees into a canary, and three assertions: the gate run from the tree
      root sees the value, a gate started from a subdirectory sees it too, and a tree without the file gates as
      before. Its list entry and dispatch arm are added with it.
      Verify: with the sourcing line removed the case fails at the first assertion,
      `expected 'from-the-worktree', got 'unset'` (`evidence/red-on-HEAD.log`); with it, PASS
      (`evidence/green.log`).

## 2. The engine

- [x] 2.1 `tools/loop/gate.sh` sources `$LOOP_ROOT/.pi/loop-env.sh` when it exists, with the comment that names the
      21-second configuration death this closes.
- [x] 2.2 `tools/loop/worktree.sh` says the file is written for the children and for the gate the tree hosts.
- [x] 2.3 The skill reference `.pi/skills/fix-loop/REFERENCE.md` names the sourcing beside `run.sh`'s.

## 3. Verification

- [x] 3.1 `tools/loop-mutations.sh` record `257.1` replaces the sourcing line's `if` with `if false; then`, so the
      suite notices the deletion; `--check` reports all 270 records apply.
- [x] 3.2 No compiled file is touched, so no build and no Gradle suite is owed: the gate is the loop's own suite over
      this change's pathspec.
- [ ] 3.3 The gate over the whole change, and its tallies, land in `evidence/` with the archive.
