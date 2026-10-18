# Design

## Context

See `proposal.md` — Why. This document records only what shapes the proof.

The property being proven is stated in five designs, and this change is where it stops being a claim:

```
github-issue-tooling   D11  one profile; discovery not arguments; the engine names no project value;
                            the backlog format behind a source adapter
migrate-todo-to-...    D14  this is the one project-specific act; the renames stay in the adapter
agent-issue-loop       D16  loop.conf is the adaptation point; the phases name no skill or gate
sensor-registry-...    D12  the engine is portable, sensors.json is the project
issue-metadata-loop    D2   the bands live in the profile
```

What "project value" means, concretely, is the whole rule:

```
NOT allowed in an engine                allowed, and read from a config
  the repository          Framstag/NaviVeylin      tools/tooling.json  repo
  an allowlist entry      Framstag                  tools/tooling.json  allowedAuthors
  a category / class      car, map-rendering        tools/tooling.json  value sets
  a project path          app/src/main/..., tools/  the argument / the profile's root
  the project's name      NaviVeylin                nowhere
  a threshold or cadence  any number                tools/sensors/sensors.json
  this build's gate       ./gradlew ...             tools/loop/loop.conf LOOP_GATE_CMD
```

The existing self-tests are the right vehicle and were designed for this: every one of them builds a
**throwaway git repository** under the gitignored `.pi/tmp/` and puts a stub `gh` on `PATH`. A foreign
project is the same harness with a different fixture — which is why this change is cheap and why its
absence would be indefensible.

## Goals / Non-Goals

**Goals**

- A leak is a **failing check**, on the commit that introduces it, not a discovery made by the second
  project.
- A second project is provably runnable: the whole toolchain works in a synthetic project whose repo,
  allowlist and vocabulary all differ.
- "The current project" is verified by cases, not by reading the discovery code.
- `PORTING.md` is executable documentation: every command in it is run by the self-test.

**Non-Goals**

- Not a shared repository, a submodule or a published CLI. Copy per project is the distribution model;
  extraction is deferred and the profile seam is what would make it cheap.
- Not a general-purpose `--project` abstraction, a plugin system, or a config schema validator. One
  profile, three per-tool configs, one check.
- Not `i18n` of the engine's messages, not alternate version-control hosts, not a non-`gh` tracker.
  The engine assumes `git` + `gh`, and `PORTING.md` says so where a reader will look.
- Not a second project in this repository. The fixture is a fixture.

## Decisions

### D1 — The rule is a check over a declared value set, not a list of banned strings

`tools/check-portability.sh` reads the project's own values from `tools/tooling.json`
(`repo`, `allowedAuthors`, every namespace value set) and the per-tool configs, then fails when any of
those literals appears in an engine file. The engine set is explicit and printed:

```
engines, checked     tools/issues/*.sh  tools/issues/import.sh  tools/issues/core/*.java
                     tools/sensors/{run,lib,guard,report}.sh  tools/loop/*.sh
excluded, printed    tools/issues/import/todo-md.sh      the source adapter (allowed to know TODO.md)
                     tools/issues/fixtures/**  tools/**/fixtures/**   test data
                     .pi/**  openspec/**  guidelines/**  TODO.md
```

| alternative | why rejected |
|---|---|
| a hand-written list of banned strings (`Framstag`, `NaviVeylin`, `car`, …) | it drifts the moment a category or an author is added, and a stale ban list fails open — the check would pass while the value leaks. |
| a per-file allowlist comment (`# portability-ok`) | an escape hatch that will be used for convenience; the one legitimate exception already has a file of its own (the adapter). |
| assert `--project` is never required, by inspecting flag parsing | tests the mechanism, not the property; the literal-in-engine check is what actually catches a leak. |
| no check, rely on the foreign-project fixture | the fixture proves the tools work elsewhere; it does not catch a value that happens to exist in both projects (`car` would, `NaviVeylin` would not). Both are needed. |

### D2 — A synthetic foreign project is a fixture, exercised end to end

```
tools/fixtures/foreign-project/
  tools/tooling.json          repo "acme/widget", allowedAuthors ["ada"], categories
                              ["core", "docs", "infra"], bands p0..p2 only, TTL 30
  tools/sensors/sensors.json  one row, whose command is a fixture script and whose threshold is 1
  tools/loop/loop.conf        LOOP_AGENT_CMD pointing at the stub pi, LOOP_GATE_CMD empty
  findings.json               its "backlog": three findings, one of them a duplicate of another
  gh/                         the stub gh's canned responses for that repository
  README.md                   what this directory is and what it is not
```

The self-test copies the foreign-project directory into a throwaway repository under `.pi/tmp/`, copies
the engines in, and runs the whole toolchain there:

```
create (3 findings, one refused as a duplicate)   -> list --unranked / --prio
update (label + comment)                          -> close (with evidence, scoped)
reopen (the sensor's own issue)                   -> one sensor row: violation, clear, error
one metadata pass (bands p0..p2)                  -> loop --dry-run (empty gate command)
check-todo-references.sh                          -> check-metadata.sh
```

`LOOP_GATE_CMD` empty is deliberate: it is the case that proves the phases are the project's gate's
caller and not its owner.

| alternative | why rejected |
|---|---|
| a second real repository | slower, needs a second token, and it would be the thing under test rather than a fixture — and it would accumulate the test's residue. |
| only the unit-level cases (discovery, no-literal) | they prove the mechanism, not the toolchain: the first foreign project would still find the first place that assumed this repository's shape. |
| a fixture with the *same* vocabulary | it would not exercise the value-set path at all — a category rename, a band that does not exist here, an allowlist with a different login are the cases that matter. |

### D3 — Discovery is asserted where it can fail

```
from a subdirectory of the repository      root and profile must resolve upward
from `.pi/worktrees/<x>` (a git worktree)  the worktree's root, its own profile
with an absolute path argument             unchanged behaviour
with GITHUB_REPOSITORY set to something wrong
                                           the repo must come from the tree, not the environment
outside any git tree                       a clear message and a non-zero exit, never a silent default
```

The worktree case is not hypothetical: `agent-issue-loop` puts the run's tree in
`.pi/worktrees/loop`, and a tool that resolves the profile from the *main* checkout while operating on
the worktree would read the wrong profile and write to the wrong repository.

| alternative | why rejected |
|---|---|
| assert only the happy path from the repository root | the two ways this actually breaks are a subdirectory invocation and a worktree, and both are already in this series' workflows. |
| let `GITHUB_REPOSITORY` override the tree | CI convenience that would silently retarget the tools in a workflow that sets it; the profile's `repo` is the source of truth and `--repo` is the explicit override. |
| resolve the profile by searching upward for a marker file | a second discovery rule beside `git rev-parse --show-toplevel`; the git root is unambiguous and already required. |

### D4 — `PORTING.md` is executable documentation

It carries: what to copy, the three files to edit, what to delete (the `TODO.md` adapter), the two
prerequisites (`git` + an authenticated `gh`), the assumption list (GitHub Issues, the label taxonomy's
namespace *names*, `bash`, `jq`, a JDK for the core), what an upstream update costs, and one command per
step. Every command in it is extracted and run by the self-test against the foreign-project fixture.

| alternative | why rejected |
|---|---|
| a README section | the recipe is long enough to deserve its own file, and a fresh project reads `PORTING.md` before it reads `AGENTS.md`. |
| prose without the verification | a recipe that is never run drifts; the fixture run is what keeps it true, and it is the same reason the guidelines in this repository quote measurements. |
| a template repository with the profile pre-filled | it would have to be maintained beside the copy, with no mechanism keeping them in step — and it contradicts the copy-per-project decision (P1). |

### D5 — The check is on the commit that introduces a leak

One token-free CI step in `.github/workflows/build.yml`, beside the existing hygiene and classification
steps, plus the local self-test. The check reads only repository files, so it needs no credential and no
network — the same shape as the other narrow gates this repository already runs.

| alternative | why rejected |
|---|---|
| run it only in the self-test | the self-test is run when someone remembers; the leak arrives with an unrelated change. |
| a pre-commit hook | not versioned with the repository (`.git/hooks` is not), so it would not exist in CI or in a fresh clone. |
| fold it into `check-todo-references.sh` | two rules with different scopes and different fixtures; one file each keeps each check's failure message specific. |

### D6 — Copy per project, and the cost is stated

`PORTING.md` says plainly: copy the engines and the skills, write the profile, delete the adapter, run the
check and the fixture; keep the profiles in your own history; an upstream engine fix is a re-copy, and a
local engine edit means you own the merge. Extraction to a shared repository remains deferred
(`github-issue-tooling` D11), and the reason it is cheap to do later is that the profile seam already
exists.

| alternative | why rejected |
|---|---|
| document nothing and let the second project figure it out | the cost of a copy is only visible after someone has done it once; writing it down is what makes the debt visible before it is paid. |
| promise a shared repository "later" without a seam | the promise is only cheap if the engines are already value-free, which is what D1's check enforces. |

## Risks / Trade-offs

- **The check fails on a legitimate literal** → the value set it reads is the project's own, and the one
  legitimate exception (the adapter) has a file of its own; a false positive is fixed by moving the value
  into the profile, which is the intended outcome.
- **The foreign-project fixture drifts from the engines** → it copies the engines at test time rather
  than carrying its own versions, so it cannot drift; only its *config* is its own.
- **A fixture that passes while a real second project would fail** → the fixture deliberately differs in
  the three ways a project differs (repo, allowlist, vocabulary) and exercises all six tools; a
  difference it does not model is a defect to add to it, and its `README.md` lists what it does not model
  (a non-GitHub tracker, a non-`bash` shell, no `jq`).
- **`PORTING.md`'s commands rot** → they are extracted and executed by the self-test, so a stale command
  is a failing case rather than a wrong instruction.
- **The check becomes a nuisance in this repository** → it has one rule and one message per class of
  leak, it runs in under a second with no network, and its output names the value and the file.

## Migration Plan

1. `tools/check-portability.sh` with the engine set and the value set read from the profile; its cases;
2. the four discovery cases, which belong with it because they test the same seam;
3. `tools/fixtures/foreign-project/` — config first, then the end-to-end run;
4. `PORTING.md`, written after the fixture runs, with its commands extracted by the self-test;
5. one CI step;
6. one line in `AGENTS.md` naming `PORTING.md`.

Rollback: revert the check, the self-test, the fixture, `PORTING.md` and the CI step. No engine file
changes, so the rollback removes the proof and nothing else.

## Open Questions

Genuinely deferrable; none changes the approach or the task breakdown:

- Whether the extraction to a shared repository (or a `git subtree`) becomes worthwhile after the second
  project — measured, that is a question of how many engine fixes the second project has to re-copy.
- Whether `PORTING.md` should later carry a small "projects using this tooling" list, which is the
  natural place to notice that a third variant has appeared.
