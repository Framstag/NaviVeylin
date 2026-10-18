# Design — fix-loop-git-identity

Scope: `tools/loop/lib.sh`, `tools/loop/worktree.sh`, `tools/loop-cases.sh`,
`tools/portability-cases.sh`, `tools/loop-mutations.sh`, one spec scenario, one guideline sentence. No
compiled file changes, so the shell gates are the verification (`guidelines/Build.md` §2, scope block).
Threading/lifecycle: none (shell).

## D1 — Which identity a commit carries

**Chosen**: the login the run's own gate preflight resolves — the identity it *already* acts as. The run
passes `policy.sh preflight` before creating anything and stops if that identity is not authorised
(`run.sh`, "the refusals that must happen before anything exists"), so the commit and the tracker's labels
name the same actor, and the commit cannot exist for an identity the gate would refuse. Rendered as GitHub's
noreply form:

```
git -C <worktree> -c user.name=<login> -c user.email=<login@users.noreply.github.com> commit …
```

| alternative | why not |
|---|---|
| keep relying on the ambient identity (`~/.gitconfig`) | this is the defect: a container or a runner has none, and the whole run blocks on its own commit |
| `git config` the identity into the worktree at `create` time | writes a configuration the repository keeps, in a tree the run removes; and a person reading the worktree later sees a fabricated `user.*` entry rather than the run's naming of it |
| a new `loop.conf` key (`LOOP_GIT_IDENTITY`) | a second place that has to agree with the profile's allowlist; the loop already has exactly one identity, and the preflight is where it is validated |
| `GITHUB_ACTOR` / `GITHUB_REPOSITORY_OWNER` | a CI variable, absent outside CI; and it names whoever triggered the run, not who the run acts as |

**Risk**: on a machine where the preflight's `gh api user` cannot be resolved, the run now stops where it
previously committed under the developer's name — accepted, and the reason is named
("no acting login — a commit could not be attributed to this run"). It is the same refusal the loop already
applies before work, so it fails earlier, not later.

## D2 — How the case reproduces the runner

**Chosen**: the fixture stops inheriting the developer's git configuration. `foreign_env()` exports a
synthetic global config (neutral defaults, **no identity**) plus `GIT_CONFIG_SYSTEM=/dev/null` and a `HOME`
without a `.gitconfig`; the loop case additionally unsets the throwaway repository's local identity. Then the
existing portability case is red on HEAD *on any host*, and the new loop case proves the attributed author.

**Alternatives**: (a) document "run the suite in a clean container" — a rule that is not a check and would
be forgotten exactly as this one was; (b) assert the committed author's shape only — the case would pass
without the fix, because the fixture's local identity supplies it; (c) `git -c user.name` in the case's own
commits only — that is already how the portability fixture initialises its repo, which is why the defect was
invisible there.

**Risk**: a fixture with no identity is stricter than some developer machines, so a *later* engine change
that reintroduces the dependency fails locally as well as on the runner. That is the intent.

## Verification

- Red on HEAD, green after: `bash tools/check-portability-selftest.sh --case foreign#emptyGateCommandStillRunsThePhases`
  (and the whole portability suite: 21 cases).
- `bash tools/loop-selftest.sh` — the loop suite, including the new `loop#commitsWithoutAnAmbientIdentity`.
- `bash tools/loop-mutations.sh` (and `tools/loop-mutations-selftest.sh`) — the new mutation must make the
  case red, restore, and be green again.
- `bash tools/check-portability.sh` — the engine still names no project value.
- `openspec validate fix-loop-git-identity --strict`.
- The change touches no compiled file: no Gradle gate is owed; the targeted runs above are the gate, and the
  push's own CI run is the end-to-end check (`Check portability` must go green).
