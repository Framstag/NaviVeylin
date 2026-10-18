# Attribute the loop's commits to the identity the run acts as

## Why

Issue **#167**: `Check portability` is red on `main` and on every pull request, at
`foreign#emptyGateCommandStillRunsThePhases` — `the item completed: expected 'done', got 'blocked'`
(CI run `39112517535`, commit `59d1f25`), while the same case is green on the developer's host.

**Root cause, reproduced deterministically**: `tools/loop/worktree.sh`'s two commits call
`gitw commit …` with no identity, so they depend on an ambient `user.name`/`user.email`. A fresh machine —
a container, or a GitHub runner — has neither, and git then refuses every commit. The loop's own
verification step reads the tree back and finds "nothing was committed", so the item ends `blocked`, the run
still exits 0, and the case fails.

Evidence on this host, 2026-10-18:

```
$ GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_SYSTEM=/dev/null HOME=/tmp/nohome \
    bash tools/check-portability-selftest.sh --case foreign#emptyGateCommandStillRunsThePhases
  the item completed: expected 'done', got 'blocked'
FAIL foreign#emptyGateCommandStillRunsThePhases            # identical to the CI failure

$ git init -q . && git add f.txt && HOME=/tmp/nohome GIT_CONFIG_GLOBAL=/dev/null git commit -m t
Identität des Autors unbekannt / *** Bitte geben Sie an, wer Sie sind. …
```

The case was green locally only because the developer's `~/.gitconfig` supplied an identity the runner does
not have — so the suite proved nothing about the environment the loop actually runs in (CI, a container, a
second project's machine).

**The requirement the defect contradicts** is `agent-issue-loop`, "Version control belongs to the run's
script, not to the agent": what the run's script owns, it owes an identity, and it may not depend on the
machine it happens to run on.

## What changes

1. **`tools/loop/lib.sh`** gains `loop_commit_identity()`: the acting login, read from the same gate
   preflight the run already passes before any work (`policy.sh preflight --json` → `.login`), rendered as
   GitHub's own noreply address `<login> <login@users.noreply.github.com>`. An unresolved login stops the
   run with a named reason instead of inventing an identity.
2. **`tools/loop/worktree.sh`** passes that identity to both commits (`git -c user.name=… -c user.email=…`),
   so no ambient configuration is needed and none is consulted.
3. **The portability fixture stops inheriting the developer's git configuration**: `foreign_env()` runs with
   a synthetic global config that carries the neutral defaults (`init.defaultBranch = main`) and **no
   identity**, so `foreign#emptyGateCommandStillRunsThePhases` is red on HEAD and green after the fix — the
   environment the runner has, reproduced on any host.
4. **A loop case of its own** (`loop#commitsWithoutAnAmbientIdentity`) drops the throwaway repository's local
   identity as well, runs a full item, and asserts both the outcome and the author of both commits
   (`<login> <login@users.noreply.github.com>`), so the identity is a measured property and not a
   side effect.
5. **A revert-check mutation** (loop set): the identity options are removed from the commit — the case must
   fail.

## Impact

| file | change |
|---|---|
| `tools/loop/lib.sh` | `loop_commit_identity()` |
| `tools/loop/worktree.sh` | both `end-item` commits carry the identity |
| `tools/loop-cases.sh` | `loop#commitsWithoutAnAmbientIdentity` |
| `tools/portability-cases.sh` | `foreign_env()` runs without an ambient git identity |
| `tools/loop-mutations.sh` | mutation `12.x` for the identity |
| `openspec/specs/agent-issue-loop/spec.md` | one scenario added to "Version control belongs to the run's script" |
| `guidelines/Backlog.md` | §7 states who a commit is attributed to (if it does not already) |

Additive: no behaviour changes on a machine that has an identity, because the explicit `-c` options win over
any ambient configuration. **Rollback**: revert the two-line change in `worktree.sh` (the fixture's identity
isolation would then show the defect again, which is the point of keeping it).

No compiled file changes, so the verification is the shell gates (loop suite, portability suite, the mutation
set) rather than the Gradle gate.
