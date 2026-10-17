# Falsification (revert-checks) — group 9

One mutation per invariant: the named case must go **red** while the mutation is in place, and the case
must go **green** again once the mutation is restored. The gate is `tools/loop-selftest.sh --case <name>`
(no Gradle: `tools/loop/**` is outside every Gradle source set), and the run's exit code is what is
compared — a mutated tree exits 1, a restored one exits 0.

The mutations were applied by a throwaway harness (not committed: it mutates the tree it tests, so it is not
a gate). Each row names the file, the exact edit, and the case it must catch.

| # | invariant | mutation | case | result |
|---|---|---|---|---|
| 9.1 | the lock is one run per repository (D12) | `state.sh`, `cmd_lock`: `if ! mkdir "$LOCK_FILE"` → `if false` | `state#secondRunRefusesWithExit5` | red |
| 9.2 | a stale base stops the next item (D4) | `worktree.sh`, `check_base`: `if [ "$current" != "$base" ]` → `if false` | `worktree#refusesWhenMainAdvanced` | red |
| 9.3 | state is written only between children (D5, invariant 2) | `run.sh`: the worker is started in the background and a loop writes `state.sh step` while it runs, before `wait` | `labels#writesOnlyBetweenChildren` | red |
| 9.4 | an interrupted item leaves nothing in progress (D7) | `run.sh`, `on_exit`: the `loop_labels unpick …` line → `:` | `labels#trapRemovesInProgressOnSignal` | red |
| 9.5 | the run verifies rather than trusts (D3) | `run.sh`: all five verification conditions (`worker_exit`, `verdict_claim`, `head_now`, `change`, gate XMLs) and the cited-evidence condition → `if false` / `elif false` | `run#childClaimWithoutCommitIsBlocked` | red |
| 9.6 | the decision cap stops a run (D9) | `run.sh`: `if [ "${open_decisions:-0}" -ge "$max_decisions" ]` → `if false` | `run#decisionCapRefusesToStart` | red |
| 9.7 | closure can be left to a person (D8) | `run.sh`: `if [ "$may_close" = "no" ]; then close_args+=(--no-close); fi` → `:` | `run#mayCloseNoLeavesTheIssueOpen` | red |
| 9.8 | one branch per item, from the base (D4) | `worktree.sh`, `start-item`: the branch name `fix/issue-$issue-$slug` → `fix/loop-branch` | `worktree#oneBranchPerItemOffTheBase` | red |
| 9.9 | the agent command is one indirection (D3, D15) | `run.sh`, `run_child`: `$LOOP_AGENT_CMD` → a literal `pi --print` | `state#agentCommandIsTheOnlyIndirection` | red |
| 9.10 | a reviewer that wrote is discarded (D3, D15) | `run.sh`: the before/after comparison `if [ "$read_only" = "on" ] && [ "$before" != "$after" ]` → `if false` | `run#reviewerWriteInvalidatesTheRound` | red |
| 9.11 | the project's facts live in `loop.conf` (D16) | `prompts/worker.md`: `{{WORKER_SKILLS}}` → the four literal skill names | `state#projectKeysAreReadFromLoopConf` | red |

**The mutation set is versioned now**: `tools/loop-mutations.sh` holds these eleven mutations (it is where the
table above comes from — `--list` prints label, case and what each removes), applies one, runs the named case,
restores and re-runs it, and reports *its own* two failure modes as failures: a pattern that no longer matches
the line (`MUTATION-DID-NOT-APPLY`) and a case that stays green (`the case did not notice the mutation`). It
restores every file it touches, including on a signal (EXIT trap), and `tools/loop-mutations-selftest.sh`
proves both guards and the restore on a throwaway copy of the engine — so no mutation of the working tree is
needed to trust it. The run below is that tool, against this repository's `tools/loop`.

11 of 11 mutations were caught; every restore left the case green, and the full suite exited 0 afterwards (45
cases; the mutated code paths did not change after the run). Raw output:

```
RED    9.1  state#secondRunRefusesWithExit5   (the lock that keeps one run per repository)
restore 9.1 state#secondRunRefusesWithExit5 green again
RED    9.2  worktree#refusesWhenMainAdvanced   (the stale-base refusal)
restore 9.2 worktree#refusesWhenMainAdvanced green again
RED    9.3  labels#writesOnlyBetweenChildren   (one writer per tree: the child runs while the wrapper writes state)
restore 9.3 labels#writesOnlyBetweenChildren green again
RED    9.4  labels#trapRemovesInProgressOnSignal   (the signal trap that removes the in-progress label)
restore 9.4 labels#trapRemovesInProgressOnSignal green again
RED    9.5  run#childClaimWithoutCommitIsBlocked   (the run verifying instead of trusting the child)
restore 9.5 run#childClaimWithoutCommitIsBlocked green again
RED    9.6  run#decisionCapRefusesToStart   (the decision cap that stops a run)
restore 9.6 run#decisionCapRefusesToStart green again
RED    9.7  run#mayCloseNoLeavesTheIssueOpen   (the closure opt-out)
restore 9.7 run#mayCloseNoLeavesTheIssueOpen green again
RED    9.8  worktree#oneBranchPerItemOffTheBase   (one branch per item)
restore 9.8 worktree#oneBranchPerItemOffTheBase green again
RED    9.9  state#agentCommandIsTheOnlyIndirection   (the single agent-command indirection)
restore 9.9 state#agentCommandIsTheOnlyIndirection green again
RED    9.10  run#reviewerWriteInvalidatesTheRound   (the reviewer read-only comparison)
restore 9.10 run#reviewerWriteInvalidatesTheRound green again
RED    9.11  state#projectKeysAreReadFromLoopConf   (the project facts living in loop.conf)
restore 9.11 state#projectKeysAreReadFromLoopConf green again
red-when-mutated: 11 · not-noticed: 0 · restore-failures: 0
```

Full suite after the last restore:

```
loop-selftest: all 45 cases passed (JDK 27, offline, no token, no sandbox)
```
