- [x] 1. Write the red case first, in `tools/loop/tests/loop-cases.sh`: `worktree#publishesTheBaseItLandedOn`,
  registered in the case list and the dispatch table, with its `# files:` declaration, beside the fixture remote
  `worktree#pushSkipsABranchItAlreadyMerged` already builds. Verification: on the base commit it fails with
  `a run asked to publish leaves the remote's base carrying its last landing, not where a person left it: expected
  '1149c77…', got 'ca04c78…'` — `loop-selftest: 1 of 1 cases failed`
  (`tools/loop/tests/loop-selftest.sh --case worktree#publishesTheBaseItLandedOn`, `evidence/red-on-base.log`,
  2026-11-22T15:08Z).
- [x] 2. Publish the base in `tools/loop/run.sh`: `push_branch "$(loop_base_ref)"` beside the item-branch push, with
  the comment that says why the base travels with it, and the paragraph above `push_branch`
  (`tools/loop/leftovers.sh:414`) widened from "the only push" to the two refs one function publishes. Verification:
  `PASS worktree#publishesTheBaseItLandedOn`, both arms: the landed base reaching the remote, and a remote whose own
  hook refuses the push answered with one `the push of main failed` line and no retry
  (`evidence/green-case.log`).
- [x] 3. Rewrite the one-call-site assertion in `worktree#noPushWithoutTheFlag` (`tools/loop/tests/loop-cases.sh:4461`)
  to count two call sites, and keep the assertion that the call site is guarded by the flag. Verification:
  `PASS worktree#noPushWithoutTheFlag` — with `--push no` git's own trace carries no push
  (`evidence/no-push-case.log`); `PASS worktree#pushSkipsABranchItAlreadyMerged` (`evidence/push-skips-case.log`).
- [x] 4. Write the delta — the requirement restated, both shipped scenarios repeated verbatim and the published base
  added as one scenario with one case — in
  `openspec/changes/fix-the-run-publishes-the-base-it-landed-on/specs/agent-issue-loop/spec.md`. Verification:
  `./tools/loop/measure.sh --change fix-the-run-publishes-the-base-it-landed-on` reports `scenarios=3 cases=2
  unnamed=1`, the unnamed one being the shipped "A push that fails says what it means", which the tree ships with no
  case and this delta repeats byte for byte.
- [x] 5. Run the structural checks this delta owes. It edits no compiled file, so it owes no build: the delta is two
  engine scripts, one shell case file and this change's markdown, and the checks that read them here are the case
  declaration check (`tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach`, PASS) and
  the evidence pointers (`bash tools/loop/evidence-check.sh <the change directory> --root .`). The gate over
  `tools/loop/**` is the run's own step, not a task of this change.
