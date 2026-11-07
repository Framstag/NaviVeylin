# Evidence — the base branch is a configuration key

Issue **#303**. Every claim below is a command and its output, recorded in this directory. The tree is
`/tmp/nv303b`, a detached worktree of this commit — the portability and engine suites refuse a `/.pi/` root, so
the loop's own worktree cannot host them — and the repository itself for the document gates.

## The missing key, on the base

```
$ grep -n LOOP_BASE_REF tools/loop/loop.conf                 # at 30c23b52 — no match
$ grep -n LOOP_BASE_REF tools/loop/lib.sh
203:loop_base_ref() { printf '%s\n' "${LOOP_BASE_REF:-main}"; }
```

The shared library carries this project's branch name; `loop.conf` carries no key, so a ported project could set
the branch only through its environment. The foreign fixture named none either.

## Red on HEAD's configuration, green with the change

`evidence/red-HEAD.log` — `tools/loop/loop.conf` taken from the base `30c23b52`, the change's case run against it:

```
$ bash tools/loop-selftest.sh --case state#baseBranchIsAConfigurationKey
  loop.conf records no LOOP_BASE_REF — the base branch is then a value of the shared library, and a project whose base branch is named otherwise can change it only through the environment
FAIL state#baseBranchIsAConfigurationKey
loop-selftest: 1 of 1 cases failed
EXIT_CODE=1
```

`evidence/green-case.log` — the same case with the key:

```
$ bash tools/loop-selftest.sh --case state#baseBranchIsAConfigurationKey
PASS state#baseBranchIsAConfigurationKey
EXIT_CODE=0
```

## Revert-check

`evidence/revert-check-56.3.log` — the key written as an outright assignment, which the sourced configuration
applies after the environment:

```
RED    56.3  state#baseBranchIsAConfigurationKey   (the base branch recorded as a value an inherited environment may override (issue #303))
restore 56.3 state#baseBranchIsAConfigurationKey green again

red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0
EXIT_CODE=0
```

The case catches both halves of the item: a missing key, and a key that silently discards an inherited value.

## The suites

`evidence/state-group.log` — the group the item names: `loop-selftest: all 16 cases passed … selection: --group
state`, exit 0.

`evidence/full-suite.log` — the whole engine suite, the gate for a change that owns `tools/loop/**` and touches the
core file `tools/loop-cases.sh`: `loop-selftest: all 216 cases passed (JDK 27, offline, no token, no sandbox) —
selection: --from-git 30c23b52`, exit 0. Its last two lines are the suite's own teardown notices ("ended … — its
working directory is inside loop-selftest.<x>"), not failures.

`evidence/records.log` — `loop-mutations.sh --check`: `all 143 cases passed — every selected record applies to the
file it names`, exit 0; `loop-mutations-selftest.sh`: `all 34 cases passed`, exit 0.

`evidence/portability-suite.log` — `check-portability-selftest: all 28 cases passed (JDK 27, offline, no token, no
Gradle)`, exit 0, the foreign project's whole toolchain run on a tree created on the branch its own configuration
names.

`evidence/check-portability.log` — `check-portability: OK — 47 engines and 2 templates name no project value (105
value(s) read from the project)`, exit 0.

`evidence/revert-check-7.2.log` — `RED(exit 1)` / `GREEN(exit 0)` for
`foreign#baseBranchIsTheFixturesConfiguration`.

## The document gates

`evidence/gate.log` — the document and report gates, one block per command, each with its own exit code:
`check-doc-routes.sh` (`all 153 section references resolve, all 4 skill references resolve`),
`check-doc-routes-selftest.sh` (`19 passed, 0 failed`), `check-todo-references.sh` (`63 citation(s) resolve
through the recorded table`), `gen-feature-list.sh --check-classification` (`stale: 3`, `car-only areas: 1`,
`highlights: 19` — its report, not a failure), `check-openspec-hygiene.sh` and
`check-openspec-hygiene-selftest.sh` (`all 6 cases passed`) — the gate `gate.sh` maps for `openspec/**` since
#45 — `mutate.sh … --list` (`15 mutation(s)`) and `check-portability.sh` (`check-portability: OK — 47 engines
and 2 templates name no project value (105 value(s) read from the project)`). Every command exited `0`.

`evidence/records.log` holds the two engine-harness runs (`--check`: `all 143 cases passed`; the harness's own
selftest: `all 34 cases passed`), and `evidence/revert-check-56.3.log` the red-then-green of the record.

The delta was validated before the archive: `openspec validate fix-loop-base-ref-is-a-configuration-key
--strict` → `Change 'fix-loop-base-ref-is-a-configuration-key' is valid`; the two `specs/` files in this change
directory are exactly the delta it read.

## What this change does not claim

- No rendered surface and no runtime path changes: no device measurement is owed.
- The landing step's missing base-branch check is **#302**, open and not this item.
- The fixture's missing `LOOP_STRICT_OUTSIDE_WRITE_DEFAULT` is **#290**, closed by the commit this change sits on.
