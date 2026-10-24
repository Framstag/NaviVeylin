# Evidence — the base a run records is the base branch's commit

Issue: #301. Change: `fix-loop-base-is-the-base-branch`. Follow-ups this change leaves: #302 (the landing step
assumes the checkout), #303 (`LOOP_BASE_REF` is not a `loop.conf` key). All commands from the repository root.

**This change was rebased onto `main`'s tip (`a0a15fe0`) before it was committed.** It had been built on
`fix/issue-44-phone-diagnostic-locale`'s commit (`a0ca0f35`), twelve commits behind, and `main` had meanwhile
changed four of the files it edits (`tools/loop/run.sh`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`, the
`agent-issue-loop` spec — 468 lines from #227, #287, #288 and #295). Its records were renumbered `56.1`/`56.2`
because `main` had taken `55.1`–`55.4` for #295. Everything in this directory was measured on the rebased tree —
case counts of 215 (whole suite) and 81 (`run` group) are `main`'s set, not the stale one.

| what it settles | file |
|---|---|
| the base case is red on the pre-fix engine | `red-HEAD.log` |
| the whole engine suite, rebased onto `main`'s tip | `full-suite.log` |
| every record this change adds or touches: red when mutated, restored green | `revert-check.log` |
| the gate: its plan for this tree, the commands this delta owes, and the two it does not | `gate.log` |
| the document checks (`gen-feature-list --check-classification`, `check-doc-routes`, `check-todo-references`) | `gate.log` |

## What the measurement was

`.pi/issue-loop/run.json` of the run this item was filed on:

```
startedAt  2026-10-24T12:30:01Z   budget 100 items / 480 min
run.base   a0ca0f354489…   the commit of fix/issue-44-phone-diagnostic-locale, the checked-out branch
main       a0a15fe0ce51…   12 commits ahead, at that commit since 12:19:58Z (before the run opened)
stop.at    2026-10-24T12:33:12Z
stop.reason  the base moved: main advanced while this run was open — base moved: main is a0a15fe0ce51 but this
             run recorded a0ca0f354489 — refusing to build on a stale base
items 0 · steps 0 · queue 21 eligible / 229 excluded
```

`state.sh init` recorded `git rev-parse HEAD`; `worktree.sh check_base` compares that value against
`git rev-parse main`. On a checkout that is not `main` the two disagree at the first item, i.e. after the
pre-screen has been paid for. The reason asserted that `main` had advanced *during* the run, which was false.

And the landing step merges into the project's own tree without checking the checkout (`merge-item`, and
`adopt-item` from the pre-screen), so the state above cannot land: on the measured tree a fast-forward of the
item's branch would have landed on the *feature* branch, left `main` where it was, and closed the item with its fix
never on the base. That is why the run refuses that checkout at its start — see #302 for what remains mid-run.

## What is asserted, and where

- `run#aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen` — the refusal names the branch, the base branch and
  the `git switch` command, records its reason, and costs no pre-screen and no item; red when the guard is
  disabled (mutation `56.2`).
- `state#recordsTheBaseBranchNotTheCheckout` — the base is the base branch's commit, the worktree is cut from
  it, and the first item starts (`red-HEAD.log`).
- `run#staleBaseStopsTheRunWithItsReason` — the refusal still stops the run, the recorded reason names both
  commits, and it carries no claim about when the base moved.
- `worktree#refusesWhenMainAdvanced` — unchanged: a base that really moved is still refused, both commits named.
- The whole `run` group, 81 cases: the guard changes no other run path.

## Falsification

`tools/loop-mutations.sh --only '56.1 56.2 32.1'` (`revert-check.log`): `56.1` puts the `HEAD` read back in place of
the base branch and `state#recordsTheBaseBranchNotTheCheckout` goes red; `56.2` disables the checkout guard and
`run#aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen` goes red; `32.1` removes the stale-base stop's
recording and `run#staleBaseStopsTheRunWithItsReason` goes red. All three restore green and byte-identical.

## Review

The change was read by an independent read-only reviewer before archive. It raised one P1 — the landing step
merging into the checkout — which this change answers with the start-time guard and #302 for the residual, and
three P2 notes, of which two changed this change (the named fallback in `state.sh`, and the reproducing grep in
tasks 4.2) and one is filed (#303).

## What is not asserted

- No Gradle suite: no compiled path is touched (`gate.log` names the two commands the `#44` work owes — it is
  committed as a WIP on its own branch, `6d8dbd17`, and is not this change's).
- No device and no emulator run: nothing about the app is changed.
- The loop was **not** relaunched on this change: the repair is the recorded base, the refusal's reason and their
  records. Whether the next run takes items is that run's own evidence.
- The conflict resolution after the rebase was by hand; the checks above are what asserts it did not lose any of
  `main`'s lines in the four files it touched (`215` cases and `142` records apply, both `main`'s count plus this
  change's).
