# The base branch is a configuration key, not an environment-only value

## Why

`loop_base_ref()` — the one definition of the branch every item descends from, added by #301 — reads
`${LOOP_BASE_REF:-main}` (`tools/loop/lib.sh:203`). The fallback is this project's branch name carried in the
shared library, and `tools/loop/loop.conf`, the file that holds every value which *is* this project's
(`guidelines/Backlog.md` D16, `PORTING.md`'s three-file recipe), records no `LOOP_BASE_REF`. A project whose base
branch is `trunk` or `develop` can therefore change it only through the environment — nothing it commits, and
nothing a reviewer of its tree can read.

**Root cause, measured.** Two readers of the value, one of them the shared library, none of them a project file:

```
$ grep -rn LOOP_BASE_REF tools/loop/loop.conf            # at 30c23b52: no match
$ grep -n  LOOP_BASE_REF tools/loop/lib.sh               # the engine's own expansion
203:loop_base_ref() { printf '%s\n' "${LOOP_BASE_REF:-main}"; }
```

The foreign fixture — the ported project that proves a fresh project can adopt the tooling — named no base
branch either, so nothing in the repository showed what a ported project is supposed to do.

**The red case, on HEAD's configuration.** `state#baseBranchIsAConfigurationKey` (added here) asserts the key
exists, is what the run records, and is overridable by the environment. Restoring `tools/loop/loop.conf` to HEAD's
version and running it:

```
  loop.conf records no LOOP_BASE_REF — the base branch is then a value of the shared library, and a project whose
  base branch is named otherwise can change it only through the environment
FAIL state#baseBranchIsAConfigurationKey
loop-selftest: 1 of 1 cases failed
```

The second half of the same case fails when the key is written as an outright assignment, which is what mutation
record `56.3` does (`evidence/revert-check-56.3.log`).

**The single resulting fix**: the branch becomes a `loop.conf` key, written
`LOOP_BASE_REF="${LOOP_BASE_REF:-main}"` because the file is sourced *after* the environment
(`lib.sh`'s `loop_resolve`) — the expansion is what lets an inherited value win while the key names the project's
own branch for everyone who has not set one.

**The requirement the defect contradicts** is `agent-issue-loop`'s "The base a run records is the base branch's
commit": it SHALL record `LOOP_BASE_REF`, and the branch a run descends from is a value of the project's
configuration, not of the shared library.

**Repro command**:

```bash
git checkout 30c23b52 -- tools/loop/loop.conf && bash tools/loop-selftest.sh --case state#baseBranchIsAConfigurationKey   # RED
bash tools/loop-mutations.sh --only 56.3                                                                                   # RED, restored green
```

## What Changes

- `tools/loop/loop.conf`: `LOOP_BASE_REF="${LOOP_BASE_REF:-main}"` with the precedence stated — the
  fill-if-unset form is what lets an inherited value win, a plain assignment would silently ignore it.
- `tools/loop/lib.sh`: the comment above `loop_base_ref()` names the key and says `main` is only the fallback for
  a configuration that records none.
- `tools/loop-cases.sh`: case `state#baseBranchIsAConfigurationKey` — the key exists, the run records that
  branch's commit, and an inherited `LOOP_BASE_REF` wins over the recorded one.
- `tools/loop-mutations.sh`: record `56.3` — the key written as an outright assignment, and the case must go red.
- `tools/fixtures/foreign-project/tools/loop/loop.conf`: the fixture names its own base branch, `trunk`, through
  the same key and the same expansion.
- `tools/portability-cases.sh`: `git_init` takes the branch the tree is created on (the fixture's own); case
  `foreign#baseBranchIsTheFixturesConfiguration` asserts that the branch the fixture records is the branch its
  tree is created on and the commit the run records, and that it is not the engine's default.
- `tools/portability-mutations.json`: record `7.2` for that case.
- `PORTING.md`: the third file's recipe gains the key, its written form and the reason for it.
- `guidelines/Backlog.md` §10: the `loop.conf` column of the "engine, copied unchanged / the project, adapted"
  table names the base branch.
- `.pi/skills/portability-check/SKILL.md`, `.pi/skills/mutation-runner/SKILL.md`: the case and record counts.

## Capabilities affected

`agent-issue-loop` — one `MODIFIED` requirement, "The base a run records is the base branch's commit": its body
states that the branch is a key of the project's configuration (487 characters, under the validator's 500), its
three scenarios are repeated verbatim, and two are added — the base branch is the project's to name, and an
inherited value wins over the recorded one.

`tooling-portability` — one `MODIFIED` requirement, "The synthetic project is a fixture, not a second project":
its four scenarios are repeated verbatim and one is added, the fixture names its own base branch.

Additive, no interface change: `loop_base_ref()`'s signature and the `state`/`worktree`/`run` call sites are
untouched, and a project that records no key keeps `main` exactly as today. Rollback path: revert the change; the
branch is reachable through the environment alone again, and the fixture names no base branch.

## Impact

| path | what changes |
|---|---|
| `tools/loop/loop.conf` | the `LOOP_BASE_REF` key and its precedence comment |
| `tools/loop/lib.sh` | the comment above `loop_base_ref()` |
| `tools/loop-cases.sh` | case `state#baseBranchIsAConfigurationKey`, list and dispatcher entries |
| `tools/loop-mutations.sh` | record `56.3` |
| `tools/fixtures/foreign-project/tools/loop/loop.conf` | the fixture's own base branch |
| `tools/portability-cases.sh` | `git_init <dir> [branch]`, case `foreign#baseBranchIsTheFixturesConfiguration` |
| `tools/portability-mutations.json` | record `7.2` |
| `PORTING.md`, `guidelines/Backlog.md` §10 | the key in the recipe and in the adaptation table |
| `.pi/skills/{portability-check,mutation-runner}/SKILL.md` | the counts |
| `openspec/specs/{agent-issue-loop,tooling-portability}/spec.md` | the two modified requirements, after archive |

No `:app`, `:auto`, `:core`, `buildSrc`, native or Gradle path is touched. No guideline is superseded beyond
`Backlog.md`'s table row, and no submodule patch or bridge override is involved.

## Not in this change

- **The landing step still merges into the project tree without verifying it is on the base branch** — open item
  **#302**; a run started on the right base can still land into a tree somebody switched away.
- **`LOOP_MEASURE_CMD` and the other project keys** already have their `loop.conf` entries; no other value of the
  engine is environment-only, so this change adds one key, not a sweep.
- **The fixture's other deviations** (`LOOP_STRICT_OUTSIDE_WRITE_DEFAULT`, #290) landed in the commit this one
  sits on.
