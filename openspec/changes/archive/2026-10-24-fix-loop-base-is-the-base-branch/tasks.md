# Tasks — the base a run records is the base branch's commit

## 1. The base

- [x] 1.1 `tools/loop/lib.sh`: `loop_base_ref()` — the one definition of the branch every item descends from.
      Verify: `bash -n tools/loop/lib.sh`; the four readers below name it and no `${LOOP_BASE_REF:-main}` copy
      is left in `tools/loop/*.sh` (`grep -rn 'LOOP_BASE_REF' tools/loop/*.sh` shows the helper and one comment).
- [x] 1.2 `tools/loop/state.sh`: `init` records that ref's commit; `HEAD` stays only as the fallback for a
      project whose base branch does not resolve, and that case is named on stderr rather than silent.
      Verify: `state#recordsTheBaseBranchNotTheCheckout` — red on HEAD, green after.
- [x] 1.3 `tools/loop/worktree.sh` (`base_ref`), `tools/loop/run.sh` (both merge sites, and the "could not be
      recorded after the merge" text), `tools/loop/loop-clean.sh` read the helper.
      Verify: `worktree#refusesWhenMainAdvanced`, `worktree#oneBranchPerItemOffTheBase`, the `run` and `clean`
      groups.

## 2. The reason a stale base is recorded with, and the checkout the landing needs

- [x] 2.1 `tools/loop/run.sh`: the stale-base stop repeats `check_base`'s line — both commits — and drops the
      claim that the base advanced during the run.
      Verify: `run#staleBaseStopsTheRunWithItsReason`, extended to assert both short commits are on the record
      and that `while this run was open` is not.
- [x] 2.2 `tools/loop/run.sh` step 4: a project tree that is not on the base branch (any other branch, or a
      detached HEAD) stops the run before the pre-screen, with the branch, the base branch and the `git switch`
      command on the record; `stop_run` defined above that refusal.
      Verify: `run#aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen`; the whole `run` group (81 cases) green.

## 3. The suite and the harness

- [x] 3.1 `tools/loop-cases.sh`: `state#recordsTheBaseBranchNotTheCheckout`, registered in the case list and the
      dispatcher, declared for `tools/loop/state.sh` and `tools/loop/worktree.sh`.
      Verify: `bash tools/loop-selftest.sh --case state#recordsTheBaseBranchNotTheCheckout`.
- [x] 3.2 `tools/loop-mutations.sh`: `56.1` (the `HEAD` read back in place of the base branch), `56.2` (the
      checkout guard disabled) and `32.1` re-anchored on the reworded stop.
      Verify: `bash tools/loop-mutations.sh --check`; `--only '56.1 56.2 32.1'` — all red, all restored green.

## 4. The documents

- [x] 4.1 The `agent-issue-loop` delta: one `ADDED` requirement with three scenarios, no shipped requirement
      modified; merged into the shipped spec before this change was committed, because it was archived on the
      stale base and then rebased onto `main`'s tip.
      Verify: `openspec validate agent-issue-loop --type spec --strict` (warnings only, on shipped requirements);
      `git diff a0a15fe0 -- openspec/specs/agent-issue-loop/spec.md` names one added requirement and no rewrite.
- [x] 4.2 No guideline states the old rule (`grep -rn 'base commit' guidelines/*.md` — one hit in
      `guidelines/Backlog.md`, about a child that left the tree at the base commit), so no guideline changes.
      `openspec/config.yaml` and `AGENTS.md` route the loop to the spec, which owns it.
      Verify: that grep, plus `tools/check-doc-routes.sh` for the documents written here.

## 5. The gate and the record

- [x] 5.1 The delta's gate, from the change root: `bash tools/loop/gate.sh --base a0a15fe0` — `main`'s tip, the commit this change was rebased onto; the same command ran against `a0ca0f35` before the rebase, which is why the change was rebased at all (`main` had changed four of the files it edits).
      started from>` — the engine suite the delta's paths select, plus `tools/loop-mutations.sh --check`.
      Verify: `evidence/gate.log`.
- [x] 5.2 The whole engine suite, owed for a change that owns `tools/loop/**`: one run of the suite over the
      full set (no compiled path changed, so no Gradle suite is owed).
      Verify: `evidence/full-suite.log` — `loop-selftest: all N cases passed`.
- [x] 5.3 `.pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-base-is-the-base-branch
      --root .` — every pointer resolves.
- [x] 5.4 The tracker: #301 filed with the measurement; the two gaps this change leaves are filed as #302 (the
      landing step assumes the checkout) and #303 (`LOOP_BASE_REF` is not a `loop.conf` key).
- [x] 5.5 The follow-ups and the closure are the archive's: #301 is closed on the archived change, not before.
      Verify: `openspec list --specs | grep agent-issue-loop` and the issue's own timeline.
