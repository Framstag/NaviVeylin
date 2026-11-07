# fix-a-run-pushes-a-branch-it-just-merged

## Why

A run started with `--push yes` printed git's own failure for the branch it had deleted itself:

```
error: src refspec fix/issue-277-… does not match any
error: failed to push some refs to 'https://github.com/Framstag/NaviVeylin.git'
```

Measured 2026-10-23, on #277, inside a run that read green everywhere else — the two error lines were the only
red text of that run. The cause is an order, not a mistake in the push: the run's only push (`push_branch`,
`tools/loop/run.sh`) is called after the item's outcome is written, and by then `merge_committed_branch` has
merged the branch and `release-branch` (`tools/loop/worktree.sh`) has deleted the local ref. A refspec whose
local ref no longer exists is exactly what git refuses, so the run reported its own cleanup as a publishing
failure.

What that costs is the run's report as a whole: a caller who greps a run log for errors finds two lines that
describe a successful closure, and a real push failure is indistinguishable from them.

## What changes

`push_branch` reads the ref before it hands git anything:

- no local ref → a named skip on stderr
  (`run: nothing to publish for <branch> — the run merged it and released the branch, so its work is in the base`),
  and exit 0: the closure it belongs to is unaffected.
- a push that fails for another reason → one line that says what it means
  (`run: the push of <branch> failed — its commits stay in the run tree, not on the remote`), instead of leaving a
  bare git error.

The case `worktree#pushSkipsABranchItAlreadyMerged` (`tools/loop-cases.sh`) drives the real run against a **bare
remote** — without a reachable remote the case could not tell the skip from a missing remote — and asserts the
skip line, the absence of `does not match any`, and that the remote carries no branch of a closed item.

## Evidence

- red on HEAD: `evidence/red-on-head.log` — `FAIL worktree#pushSkipsABranchItAlreadyMerged`, exit 1, the missing
  skip line named in the assertion.
- green after the fix: `evidence/gate.log` — `loop-selftest: all 191 cases passed (JDK 27, offline, no token, no
  sandbox)`, exit 0, 14 m 12 s.

## Owning capability and guideline section

The capability is `agent-issue-loop`; the delta is a new requirement, "The publish publishes what still exists",
beside the shipped "Version control belongs to the run's script, not to the agent" whose scenario "Nothing is
pushed or opened by default" already owns the flag's other half (`openspec/specs/agent-issue-loop/spec.md`). The
routing is `AGENTS.md` → Backlog maintenance → skill `fix-loop`; no guideline section states the push's ordering.

## Scope

`surface: 1 case added, 1 function narrowed, 0 fixtures realigned` — `grep -rn 'push_branch' tools/ | wc -l` = 5
(definition, the one call site, its two comments), `grep -rn 'noPushWithoutTheFlag\|nothing to publish' tools/ | wc
-l` = 6, and `grep -rln 'git push' tools/` names `tools/loop-cases.sh` alone: the push lives in one function and
one case file, and no other case asserts it.
