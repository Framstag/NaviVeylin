# Design — the base a run records is the base branch's commit

## The root cause, and what the evidence eliminated

The defect is one value: `state.sh init` recorded `git rev-parse HEAD` of the project tree, while
`worktree.sh check_base` refuses an item whose base is not the base branch (`git rev-parse main`). Both are
measured: the run recorded `a0ca0f35` (the checked-out branch's commit) while `main` stood at `a0a15fe0`, and
`check_base` printed exactly those two commits before `run.sh` stopped the run (issue #301).

Bug-fix exception: the alternatives below are the ones the evidence eliminates.

**Alternative 1 — compare `check_base` against the checkout's `HEAD` instead of the base branch.** Impossible:
the run's worktree and every item's branch would be cut from a commit `main` does not have, and the landing
merge merges an item's branch *onto the base* before closing it (requirement "The item is closed on a base that
carries its fix"). An item built on an unlanded feature commit would either merge that commit into `main` or be
refused at the merge, after the item's whole budget had been spent. The measured run would then be one that
works an item and cannot land it.

**Alternative 2 — keep recording `HEAD` and add a start-time pre-flight that refuses a checkout not on the base
branch.** Impossible as a fix, and only a symptom guard: the recorded value stays wrong for every other reader
(`loop-clean.sh`'s sweep compares branches against it; `run.sh` advances it to the base branch after an adopted
merge, so the state would hold one value at start and another after the first merge). It keeps its merit in the
other direction, though — see the guard below, which refuses that checkout for a *different* reason (the landing
depends on it) and leaves the recorded base correct.

**Alternative 3 — record `origin/main`.** Impossible: the loop works on the local base branch (`check_base`,
`loop-clean` and the merge all read the local ref), and resolving a remote ref would make a run depend on when
somebody last fetched — a value the run cannot observe and does not own.

## The fix

One definition, in `lib.sh`: `loop_base_ref()` prints `${LOOP_BASE_REF:-main}`. `state.sh init` records that
ref's commit (falling back to `HEAD` only when the ref does not resolve at all, so a project whose configuration
names no base branch is unchanged in behaviour); `worktree.sh base_ref`, `run.sh`'s two merge sites and
`loop-clean.sh` read the same helper instead of their own copy of the expression.

The stop reason is reworded in the same change because the same measurement exposed it: it claimed
`main advanced while this run was open`, which was false. The reason now repeats `check_base`'s line — the base
branch, the commit it stands at, the commit the run recorded — and adds nothing it cannot observe.

## The guard the review forced

The landing step merges into the project's own tree (`worktree.sh` `merge-item`, and `adopt-item` from the
pre-screen) and neither verifies that the tree has the base branch checked out; `check_base` compares the *ref's*
commit, never the checkout. Before this change that gap was unreachable — the wrong base stopped the run at its
first item. With the base recorded correctly it is reachable, and on the measured state it is worse than a
refusal: `git merge --ff-only` on the feature branch fast-forwards **that branch**, `main` does not move,
`run.sh` re-reads the unchanged base, `state.sh set-base` accepts it, and the item is closed claiming a fix the
base does not carry — contradicting the shipped requirement "The item is closed on a base that carries its fix".

So `run.sh` step 4 refuses a project tree that is on any other branch, or on a detached HEAD, before the pre-screen
(the refusal names the branch, the base branch and the `git switch` command, and is recorded as the run's stop
reason). `stop_run` moved above it: a function is a command only once its definition has executed.

What that leaves, deliberately, is the *mid-run* switch of the project tree's branch: nothing re-checks it at
`merge-item`, so that person gets a failed fast-forward instead of a named refusal, and `set-base` accepts any
commit without an ancestry check. Filed as #302, with `LOOP_BASE_REF` having no `loop.conf` key as #303.

## Risks

| risk | why it does not hold |
|---|---|
| a project whose base branch is not `main` behaves differently | the ref is a configuration value (`LOOP_BASE_REF`); unset and unresolvable keeps the previous `HEAD` fallback |
| an explicit `--base` is overruled | it is not: `--base` keeps precedence over both |
| a run started on a branch now builds on `main` and the person's uncommitted work is swept in | the worktree is a separate checkout of the base commit; the project tree's own changes are never read — the merge refuses a dirty project tree, and with the guard no merge is attempted from a tree that is not on the base branch |
| the guard refuses a legitimate run | a run lands by merging into the base branch, which the project tree must have checked out; the refusal names the one command that satisfies it, and it costs no pre-screen and no item |

## Verification

- `state#recordsTheBaseBranchNotTheCheckout`: red on HEAD (assertion 1: recorded base ≠ base branch's commit),
  green after the fix.
- `run#aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen`: the refusal, its recorded reason, its named remedy,
  and that no pre-screen and no item was paid for; red when the guard is disabled (mutation `56.2`).
- `run#staleBaseStopsTheRunWithItsReason`: the recorded reason names both commits and contains no
  `while this run was open`.
- `worktree#refusesWhenMainAdvanced` and `worktree#oneBranchPerItemOffTheBase` unchanged and green — the refusal
  and the branch-from-base rule still hold for a base that really moved.
- Revert-check: `tools/loop-mutations.sh --only '56.1 56.2 32.1'` — all three red, all three restored green.
- The whole `run` group (81 cases) green, so the guard changes no other run path.
- The gate: `tools/loop/gate.sh` for a `tools/loop/**` delta — the engine suite selected by the delta's paths
  plus `tools/loop-mutations.sh --check`.

No device, no emulator, no Gradle suite: nothing compiled is touched.
