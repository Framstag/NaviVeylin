# fix-loop-git-identity — report and evidence

Closes issue **#167** (`Check portability` red on `main` and on every pull request).

No compiled file changed (shell + fixtures + one spec scenario), so the shell gates are the verification
(`guidelines/Build.md` §2, scope block); the push's own CI run is the end-to-end check.

## Root cause

`tools/loop/worktree.sh`'s two `end-item` commits called `gitw commit …` with no identity, so they depended
on an ambient `user.name`/`user.email`. A fresh machine — a container, a GitHub runner — has neither, git
refuses every commit, the run's verification step finds "nothing was committed", the item ends `blocked`, the
run still exits 0, and the case fails. The case was green on the developer's host only because `~/.gitconfig`
supplied an identity the runner does not have.

## Red on HEAD (before the fix)

With the fixture's environment isolated (no ambient git identity — `tools/portability-cases.sh`,
`foreign_env()`), the case reproduces the CI symptom on any host:

```
$ bash tools/check-portability-selftest.sh --case foreign#emptyGateCommandStillRunsThePhases
  the item completed: expected 'done', got 'blocked'
FAIL foreign#emptyGateCommandStillRunsThePhases
```

and git's own message, in a throwaway repository with the same environment:

```
$ HOME=/tmp/nohome GIT_CONFIG_GLOBAL=/dev/null git commit -m t
Identität des Autors unbekannt
*** Bitte geben Sie an, wer Sie sind.
```

The same case in CI: run `39112517535`, step `Check portability` (`the item completed: expected 'done', got
'blocked'`, then `check-portability-selftest: 1 of 21 cases failed`).

## The fix

- `tools/loop/lib.sh` → `loop_commit_identity()`: the acting login, from the same gate preflight the run
  passes before any work (`policy.sh preflight --json` → `.login`), as `<login> <login@users.noreply.github.com>`;
  an unresolved login stops the run with a named reason.
- `tools/loop/worktree.sh`: both commits pass `-c user.name=… -c user.email=…`, so no ambient configuration
  is consulted (design D1).

## Green after the fix

```
bash tools/check-portability-selftest.sh --case foreign#emptyGateCommandStillRunsThePhases   PASS
bash tools/check-portability-selftest.sh             all 21 cases passed (JDK 27, offline, no token, no Gradle)
bash tools/loop-selftest.sh                          all 46 cases passed
bash tools/loop-mutations.sh                         red-when-mutated: 12 · not-noticed: 0 · restore-failures: 0
bash tools/loop-mutations-selftest.sh                the harness reports an unapplied mutation, an unnoticed one and a real red
bash tools/check-portability.sh                      OK — 42 engines and 2 templates name no project value (133 values)
bash tools/build-inputs-selftest.sh                  10 cases, 0 failed
bash tools/sensor-selftest.sh                        all 36 cases passed
openspec validate fix-loop-git-identity --strict     valid
```

## Traceability — scenario to case

| requirement (delta) | scenario | case |
|---|---|---|
| MODIFIED — version control belongs to the run's script | *a commit carries the run's own identity* (the run completes on a machine with no identity; the identity is the run's own) | `loop#commitsWithoutAnAmbientIdentity`, and the existing `foreign#emptyGateCommandStillRunsThePhases` with the fixture's environment isolated |
| MODIFIED — version control belongs to the run's script | *one working tree per run*, *one branch per item*, *a stale base stops the next item*, *nothing is pushed by default* | unchanged cases: `worktree#seedsAllFourInputs`, `worktree#oneBranchPerItemOffTheBase`, `worktree#refusesWhenMainAdvanced`, `worktree#noPushWithoutTheFlag` |

## Revert-check

Mutation `12.1` (`tools/loop-mutations.sh`): the identity options are removed from the commit —

```
RED    12.1  run#commitsWithoutAnAmbientIdentity   (the commit is attributed to the run's own identity)
restore 12.1 run#commitsWithoutAnAmbientIdentity green again
```

The harness's own expectation of the set's size moved from eleven to twelve entries
(`tools/loop-mutations-selftest.sh`), which is the guard that keeps the listed set honest.

## End-to-end (CI)

The push's own `Build Debug APK` run must show `Check portability` green — the first green `main` run since
the step landed. The sensor rows' green-path dispatch (owed on issue #161) follows from it, since the record
for a green commit carries all four inputs.
