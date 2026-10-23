# Tasks — fix-a-run-pushes-a-branch-it-just-merged

- [x] 1 Red case: `worktree#pushSkipsABranchItAlreadyMerged` in `tools/loop-cases.sh`, driving the real run against
  a bare remote and asserting the skip line, the absence of `does not match any`, and the remote's branch list
  (`evidence/red-on-head.log` — FAIL on HEAD, exit 1).
- [x] 2 `push_branch` (`tools/loop/run.sh`) reads the ref first: a merged-and-released branch is a named skip, a
  real failure gets one line of its own.
- [x] 3 Surface counted before the plan: `grep -rn 'push_branch' tools/ | wc -l` = 5,
  `grep -rn 'noPushWithoutTheFlag\|nothing to publish' tools/ | wc -l` = 6, `grep -rln 'git push' tools/` =
  `tools/loop-cases.sh` — one function, one case file.
- [x] 4 Gate for the change that owns `tools/loop/**`: `bash tools/loop-selftest.sh` — 191 of 191 cases passed,
  exit 0, 14 m 12 s (`evidence/gate.log`).
- [x] 5 Falsification: with the fix reverted the named case fails and names the missing skip line
  (`evidence/red-on-head.log`), restored and re-run green (`evidence/gate.log`).
