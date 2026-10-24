# Tasks — fix-loop-landing-verifies-the-checkout

- [x] 1. Read the claim against the base: `cmd_merge_item` (`tools/loop/worktree.sh:403`) and `cmd_adopt_item`
      (`:350`) merge into the project tree and assert the checkout in a comment; `check_base` (`:115`) compares
      the base ref, not the checkout; `cmd_set_base` (`tools/loop/state.sh:362`) accepted any commit.
      Verify: `grep -n 'symbolic-ref' tools/loop/*.sh` found `run.sh:389` only.
- [x] 2. Write the red cases and register them (`tools/loop-cases.sh:4397`, `:4441`, list at `:43`, `:89`,
      dispatch at `:309`, `:356`). Verify: `bash tools/loop-selftest.sh --case
      'worktree#refusesToMergeIntoATreeOffTheBaseBranch'` → exit 1 on the base
      (`evidence/red-on-head.log`); same for `state#setBaseRefusesACommitTheBaseBranchDoesNotCarry`.
- [x] 3. Add `check_checkout` and call it from both merge commands (`tools/loop/worktree.sh:137`, `:357`, `:410`).
      Verify: case 2 of task 2 green.
- [x] 4. Refuse a commit the base branch does not carry in `cmd_set_base` (`tools/loop/state.sh:369`–`:373`).
      Verify: case 1 of task 2 green, and `state#recordsTheBaseBranchNotTheCheckout` stays green.
- [x] 5. Add the two mutation records (`57.1`, `57.2` in `tools/loop-mutations.sh`) and falsify them.
      Verify: `bash tools/loop-mutations.sh --only 57.1,57.2` → each mutates, the named case goes red, the
      restore is green; `bash tools/loop-mutations.sh --check` applies both.
- [x] 6. Extend the capability delta (`specs/agent-issue-loop/spec.md`) and the owning guideline paragraph
      (`guidelines/Backlog.md:416`). Verify: `openspec validate fix-loop-landing-verifies-the-checkout --strict`.
- [x] 7. Run the delta's gate once: `./tools/loop/gate.sh`. Verify: its `LOOP-GATE:` line, tallied under the
      profile's tally directory and copied into `evidence/`.
- [x] 8. Record the evidence and archive the change. Verify:
      `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/<name> --root .` reports no dangling
      pointer, and `openspec/changes/archive/2026-10-24-fix-loop-landing-verifies-the-checkout` exists with the
      delta merged into `openspec/specs/agent-issue-loop/spec.md`.
