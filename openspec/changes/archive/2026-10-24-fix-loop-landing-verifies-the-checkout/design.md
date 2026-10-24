# Design — fix-loop-landing-verifies-the-checkout

## Root cause

The landing step asserts a precondition in a comment and never verifies it: `merge-item` and `adopt-item` merge
into the project's own tree, where "the base branch is checked out", while `check_base` compares the recorded
base against the base *ref's* commit — a different question — and `set-base` accepts any commit `rev-parse`
resolves. The merge therefore acts on whichever branch the working tree holds, while the run's state records a
ref, and nothing ties the two.

## The single fix

1. **`check_checkout`** (`tools/loop/worktree.sh:137`) reads `git -C "$LOOP_ROOT" symbolic-ref --quiet --short
   HEAD` and refuses (`LOOP_REFUSE`, 5) unless it equals `loop_base_ref`. It names both branches, because the
   repair is `switch <base>`. It is called by `cmd_adopt_item` (`:357`, before the branch is even looked at: the
   tree is the precondition of the merge, not of the evidence) and by `cmd_merge_item` (`:410`), immediately
   beside the `check_base` each already runs.
2. **`cmd_set_base`** (`tools/loop/state.sh:362`) refuses a commit the base branch does not carry:
   `git merge-base --is-ancestor "$sha" "$branch"`. The branch must resolve, and a missing one is refused with
   its own reason rather than compared against. A commit that fails the test leaves `.run.base` untouched, so the
   state and the tree keep agreeing about what every later item descends from.

## Why the alternatives the evidence eliminated are not in this change

- **A start-time-only guard** is the state before this change (`tools/loop/run.sh:389`, landed with #301). It is
  verified as insufficient: the tree can be switched after it, mid-run, and `adopt-item` runs from the
  pre-screen — a different process from the one that took the start guard's reading but the same repository.
- **Relying on the fast-forward to fail** was measured to not fail in the case that matters: a checkout that is an
  ancestor of the item's branch fast-forwards, and the absorb is silent. Refusing at the merge is therefore not a
  substitute for verifying the merge, which is why the check precedes it.
- **`set-base` refusing nothing but a non-commit** is what HEAD does; it accepted a commit the base branch never
  had, which is precisely the record the closure reads. The ancestry test is the smallest rule that makes the
  recorded base a commit the base branch has.
- **Refusing a detached HEAD / off-branch tree in `set-base` instead of in the merge commands** was rejected: the
  state has no business reading the checkout (that is what #301's review established for `init`), and the
  command that depends on the checkout is the one that must check it.

## Blast radius

Two engine scripts, one case file, one mutation record set, one guideline paragraph, one capability delta. The
behaviour a green run takes is unchanged: on a tree that has the base branch checked out, `check_checkout`
returns 0 and prints nothing, and `set-base` is handed the base branch's own commit by both callers
(`run.sh:209`, `run.sh:257`), which is an ancestor of itself.

## Rollback

Revert the two guards: `check_checkout`'s body to nothing and its two call sites dropped, and `set-base` back to
`rev-parse` alone. No record, no branch and no state file gains a field, so the rollback is the two hunks and
nothing else; the two cases and their mutation records would go red exactly as they are red on the base.

## Verification

- Red on the base: both cases, `evidence/red-on-head.xml` (`tests="2" failures="2"`).
- Green after: `bash tools/loop-selftest.sh --case 'worktree#refusesToMergeIntoATreeOffTheBaseBranch'` and
  `--case 'state#setBaseRefusesACommitTheBaseBranchDoesNotCarry'`.
- Falsified: mutation records `57.1` and `57.2` in `tools/loop-mutations.sh`, each applied by
  `tools/loop-mutations.sh --only 57.1,57.2` — the named case must go red, the exact restore green.
- The gate of the delta: `./tools/loop/gate.sh`, which owes the whole loop suite because `tools/loop-cases.sh` is
  a core file.
