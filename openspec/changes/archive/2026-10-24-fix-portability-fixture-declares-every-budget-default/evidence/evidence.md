# Evidence — the portability fixture declares every budget default

Issue **#290**. The change is three lines of fixture and one mutation record; every claim below is a command
and its output, recorded in this directory.

## Red on the base, before any edit

`evidence/red-HEAD.log` — `/home/tim/projects/NaviVeylin`, a clean checkout of `30c23b52`:

```
$ bash tools/check-portability-selftest.sh --case foreign#fixtureDeclaresEveryBudgetDefault
  every budget default the loop's engine reads is declared by the fixture's loop.conf: expected nothing, got:  LOOP_STRICT_OUTSIDE_WRITE_DEFAULT
FAIL foreign#fixtureDeclaresEveryBudgetDefault
EXIT_CODE=1
```

The case derives its key list from the engine (every `LOOP_*_DEFAULT` the engine resolves through
`loop_conf_value`), so the omission is the fixture's, not the case's.

## Attributed to the commit that caused it

```
$ git log --oneline -S LOOP_STRICT_OUTSIDE_WRITE_DEFAULT -- tools/loop/loop.conf tools/fixtures/foreign-project/tools/loop/loop.conf tools/portability-cases.sh
31e8f0bc fix: a-difference-outside-the-worktree-is-recorded (#286)
```

One commit, the one that added the key to the engine's own `loop.conf`; the fixture was not updated with it, so
`Check portability` has been red on `main` since #286 landed rather than since any branch's delta (the item's
*Baseline*).

## Green with the key

`evidence/green-case.log` — the same case, the change applied:

```
$ bash tools/check-portability-selftest.sh --case foreign#fixtureDeclaresEveryBudgetDefault
PASS foreign#fixtureDeclaresEveryBudgetDefault
EXIT_CODE=0
```

`evidence/full-suite.log` — the whole set, which is the gate for this path:

```
check-portability-selftest: all 27 cases passed (JDK 27, offline, no token, no Gradle)
EXIT_CODE=0
```

The tree that produced the green runs is `/tmp/nv290b`, a detached worktree of this commit; the suite refuses a
`/.pi/` root (machine-local state is not a checkout), so the loop's own worktree cannot host it — that refusal is
the suite's own precondition case `preconditions#refusesUnderMachineLocalState`.

## Revert-check

`evidence/revert-check-7.1.log`:

```
7.1	foreign#fixtureDeclaresEveryBudgetDefault	a budget default the engine reads is declared by the fixture's configuration, not left to the host's (issue #290)
  RED(exit 1)  1 occurrence(s) replaced in tools/fixtures/foreign-project/tools/loop/loop.conf
  GREEN(exit 0)  restored, and the case passes again

mutate: 1 mutation(s) run, 1 went red and restored green, 0 problem(s)
```

The record deletes the key, and the case fails: the fixture's completeness is now an asserted invariant, not a
line the next engine default can silently pass by.

## The rest of the mapped gate

`evidence/check-portability.log` — `check-portability: OK — 47 engines and 2 templates name no project value
(105 value(s) read from the project)`, exit 0. `evidence/mutation-list.log` — `15 mutation(s)`, exit 0 (the set
carries the next change's record too; this one adds `7.1`). `evidence/doc-gates.log` —
`tools/check-doc-routes.sh` (`all 153 section references resolve, all 4 skill references resolve`) and
`tools/check-todo-references.sh` (`63 citation(s) resolve through the recorded table`), exit 0 each: this change
edits `.pi/skills/mutation-runner/SKILL.md`, and the OpenSpec artifacts it adds are change artifacts, which the
route check does not inspect.

## What this change does not claim

- No engine behaviour changed: the fixture is a test tree, and no `tools/loop/**` file is touched.
- The reason the fixture could drift — `tools/loop/loop.conf` maps to no `Check portability` command in
  `LOOP_GATE_SUITES` — is **#289**, open and not this item.
- `LOOP_BASE_REF`, the other project value the fixture must name, is **#303** and lands in the next commit.
