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
      (`guidelines/Backlog.md:416`). Verify: the MODIFIED block repeats S1–S3 verbatim against
      `openspec/specs/agent-issue-loop/spec.md:1284`–`:1307`, its requirement body is unchanged (≈380 chars), and the
      delta's S4/S5 are merged into the shipped spec at `:1309` and `:1319`; `openspec validate <change> --strict`
      was run before the archive.
- [x] 7. Run the delta's gate once: `./tools/loop/gate.sh`. Verify: its `LOOP-GATE:` line, tallied under the
      profile's tally directory and copied into `evidence/` (`gate.log`; green re-run on the corrected tree after
      review round 1, `gate.log` of `gate-round2.log`).
- [x] 8. Record the evidence and archive the change. Verify:
      `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/<name> --root .` reports no dangling
      pointer, and `openspec/changes/archive/2026-10-24-fix-loop-landing-verifies-the-checkout` exists with the
      delta merged into `openspec/specs/agent-issue-loop/spec.md`.
- [x] 9. Round-1 review corrections: assert the detached-HEAD arm of S4 in
      `worktree#refusesToMergeIntoATreeOffTheBaseBranch` (`tools/loop-cases.sh:4435`–`:4452`), delete the false
      "every case of the `driver` group" sentence from the evidence, and re-run the gate once on the corrected tree.
      Verify: `bash tools/loop-selftest.sh --case 'worktree#refusesToMergeIntoATreeOffTheBaseBranch'` → PASS;
      `bash tools/loop-mutations.sh --only '57.1 57.2'` → each RED names its case and restores green
      (`evidence/revert-check-57.1-57.2-round2.log`); `evidence/gate-round2.log` ends with a green `LOOP-GATE:` line.
