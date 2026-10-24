# The base a run records is the base branch's commit, not the checkout's

## Why

A run opened while the project tree is checked out on a feature branch records **that branch's** commit as
`run.base` and then refuses its own first item as a stale base, after the whole pre-screen has been paid for.

Measured 2026-10-24 (`.pi/issue-loop/run.json` of the run in question):

```
recorded base  a0ca0f35   fix/issue-44-phone-diagnostic-locale (checked out), committed 11:08
base branch    a0a15fe0   main, 12 commits ahead, at that commit since 12:19:58Z
run opened     12:30:01Z  budget 100 items / 480 min
first item     12:33:12Z  #293 → `base moved: main is a0a15fe0ce51 but this run recorded a0ca0f354489`
run ended      12:33:12Z  0 items, 0 steps, 211 min of 480 untouched
```

**Root cause** (`tools/loop/state.sh`, `cmd_init`): when no `--base` is given, `init` recorded
`git rev-parse HEAD` of the project tree — the *checkout's* commit. Every other reader of the base names the
base **branch**: `worktree.sh`'s `check_base` (the stale-base refusal), `run.sh` after a merge
(`rev-parse "${LOOP_BASE_REF:-main}"`), `loop-clean.sh`'s sweep. So `init` wrote a value that `check_base`
compares against `main`, and on any machine whose checkout is not on `main` the two disagree at the first item.
Since a machine somebody works on is on a feature branch by default, the run's most common start state is a
run that does nothing.

**A second defect on the same path, found by the change's review**: the landing step merges into the project's
**own tree** — `worktree.sh`'s `merge-item`, and `adopt-item` from the pre-screen — and neither verifies that the
tree has the base branch checked out (both say "where the base branch is checked out" and check nothing;
`check_base` compares the ref's commit, not the checkout). Before this change that gap was unreachable, because
the wrong base stopped the run at its first item; with the base recorded correctly it is reachable, and worse than
a refusal: on the measured state a merge would have fast-forwarded the *feature branch*, left `main` where it was,
and closed the item with its fix never on the base. So the run now refuses a project tree that is not on the base
branch, before the pre-screen is paid for.

**Second defect, same measurement**: the stop reason said `the base moved: main advanced while this run was
open`, and `main` had in fact advanced *before* the run opened. The reason repeated a speculation the refusal
never observed.

The invariant is contradicted by the `agent-issue-loop` requirement this change adds; the pre-screen's own rules,
the queue, the item cap and the landing rule itself are untouched.

## What Changes

- `tools/loop/lib.sh`: `loop_base_ref()` — one definition of "the branch every item descends from"
  (`LOOP_BASE_REF`, `main` when unset), read by `state.sh`, `worktree.sh`, `run.sh` and `loop-clean.sh`.
- `tools/loop/state.sh`: `init` records that branch's commit; the tree's `HEAD` is only the fallback when the
  base branch does not resolve at all (a repository whose configuration names none and that has no `main`).
- `tools/loop/worktree.sh`, `tools/loop/run.sh`, `tools/loop/loop-clean.sh`: read the ref through the one
  helper instead of their own `${LOOP_BASE_REF:-main}` copy.
- `tools/loop/run.sh`: the stale-base stop reason repeats what `check_base` observed — both commits — and drops
  the clause that claimed the base had advanced during the run. And step 4 of the run: a project tree that is not
  checked out on the base branch (any other branch, or a detached HEAD) stops the run before the pre-screen, with
the branch it is on, the base branch and the `git switch` command on the record. `stop_run` is defined before that
  refusal, because a function is a command only once its definition has executed.
- `tools/loop/state.sh`: a base branch that does not resolve is named on stderr instead of being replaced by the
  checkout's commit without a word.
- `tools/loop-cases.sh`: `state#recordsTheBaseBranchNotTheCheckout` (the recorded value),
  `run#aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen` (the refusal and what it costs: no pre-screen, no
  item), and `run#staleBaseStopsTheRunWithItsReason` extended to assert that the recorded reason names both
  commits and asserts nothing about *when* the base moved.
- `tools/loop-mutations.sh`: `56.1` (the `HEAD` read back in place of the base branch → the base case red), `56.2`
  (the checkout guard disabled → the refusal case red) and `32.1` re-anchored on the reworded stop.

## Capabilities affected

`agent-issue-loop` — one `ADDED` requirement, "The base a run records is the base branch's commit", with three
scenarios (the recorded value, the refusal of a checkout that is not the base branch, and what a stale-base stop
records). No shipped requirement is modified: the stale-base scenario of "Version control belongs to the run's
script, not to the agent" already says the refusal names both commits, and it now does so in the reason as well;
the landing requirement ("The item is closed on a base that carries its fix") is made true by the refusal rather
than changed.

Additive: `--base` keeps its meaning and precedence; the recorded lines, the refusals' exit codes, the queue
and the landing rule are unchanged. Rollback path: revert the change — the new case is red again, as it was
measured on HEAD.

## Impact

| path | what changes |
|---|---|
| `tools/loop/lib.sh` | `loop_base_ref` |
| `tools/loop/state.sh` | `init`'s base, its usage text |
| `tools/loop/worktree.sh` | `base_ref` delegates to the helper |
| `tools/loop/run.sh` | the two merge sites read the helper; the stale-base stop reason; the checkout guard (step 4) and `stop_run` defined before it |
| `tools/loop/loop-clean.sh` | the sweep reads the helper |
| `tools/loop-cases.sh` | two new cases; the stale-base case's assertions |
| `tools/loop-mutations.sh` | records `56.1`, `56.2`, `32.1` |
| `openspec/specs/agent-issue-loop/spec.md` | the new requirement, after archive |

No `:app`, `:auto`, `:core`, `buildSrc`, native or Gradle path is touched, so no Gradle suite is owed; the
gate is the engine's own suite and the record check. No submodule patch and no bridge override.

## Not in this change

- **The landing step still assumes the base branch is checked out, and only the run's start verifies it.** The
  guard above makes the assumption true when a run begins; nothing re-checks it at `merge-item`, so a person who
  switches the project tree's branch while a run is open gets a failed fast-forward rather than a named refusal,
  and `state.sh set-base` accepts any commit without asking whether the base descends from it. Filed as #302.
- **`LOOP_BASE_REF` is an environment value, not a `loop.conf` key** — the one definition `loop_base_ref()` still
  carries this project's branch name as its default, so a ported project changes it only through the environment.
  Filed as #303.
- A run that is *given* an older `--base` deliberately still stops at its first item; the reason now names both
  commits instead of claiming when the base moved.
- The stale-base stop still ends the run rather than skipping the item. That is the shipped rule.
