# The portability fixture declares every budget default the engine reads

## Why

`Check portability` is red on a clean checkout of `main`, so the pipeline fails on a state nobody changed.

**Root cause, measured.** Issue #286 added `LOOP_STRICT_OUTSIDE_WRITE_DEFAULT` to `tools/loop/loop.conf` and to
the engine's `loop_conf_value` reads, and the fixture — `tools/fixtures/foreign-project/tools/loop/loop.conf`,
the foreign project the portability proof builds — was not updated with it. The case that holds a porting
project to declaring each default, `foreign#fixtureDeclaresEveryBudgetDefault`, derives the key list from the
engine and compares it with the fixture, so it has failed since that commit:

```
$ bash tools/check-portability-selftest.sh --case foreign#fixtureDeclaresEveryBudgetDefault   # at 30c23b52
  every budget default the loop's engine reads is declared by the fixture's loop.conf: expected nothing, got:  LOOP_STRICT_OUTSIDE_WRITE_DEFAULT
FAIL foreign#fixtureDeclaresEveryBudgetDefault
```

`git log -S LOOP_STRICT_OUTSIDE_WRITE_DEFAULT -- tools/loop/loop.conf` resolves the commit that introduced the
key: `31e8f0bc`, "a-difference-outside-the-worktree-is-recorded (#286)". The failure is not any one branch's
delta (issue #290's Baseline).

**The single resulting fix**: the missing key in the fixture, `LOOP_STRICT_OUTSIDE_WRITE_DEFAULT=off` — the
value the engine ships and the one a tree a run does not own exclusively takes; the fixture is a throwaway tree
of its own, and the case that already exists is what keeps the fixture in step from now on.

**The requirement the defect contradicts** is `tooling-portability`'s "The synthetic project is a fixture, not a
second project", scenario *The fixture declares every budget the engine reads* — the fixture SHALL declare each
`LOOP_*_DEFAULT` the engine reads, and it did not.

**Repro command** (the state in which the defect is observable, and the revert-check's inverse):

```bash
bash tools/check-portability-selftest.sh --case foreign#fixtureDeclaresEveryBudgetDefault   # RED at 30c23b52
bash tools/mutate.sh --mutations tools/portability-mutations.json --only 7.1                # drops the key again: RED
```

## What Changes

- `tools/fixtures/foreign-project/tools/loop/loop.conf`: the missing budget default, with the comment every
  other key in that file carries.
- `tools/portability-mutations.json`: record `7.1` — delete the key from the fixture, and
  `foreign#fixtureDeclaresEveryBudgetDefault` must go red. The invariant is the shipped one, and until now
  nothing proved the case fails when the fixture loses a default.
- `.pi/skills/mutation-runner/SKILL.md` and `.pi/skills/portability-check/SKILL.md`: the record count the two
  skills state.

No spec delta: the requirement and its scenario already say what the fixture must declare, and they name the
case that enforces it (`openspec/specs/tooling-portability/spec.md`, "The fixture declares every budget the
engine reads"). The change satisfies a shipped requirement; it does not change one.

## Capabilities affected

`tooling-portability` — no requirement changes. The change is a fixture line and a mutation record; the
scenario it satisfies and its `Case:` pointer are already in the shipped spec.

Additive and inert: no engine reads a different value (the fixture is a test tree), no case is renamed, and the
suite's exit statuses are untouched. Rollback path: revert both files; `Check portability` is red on `main`
again, exactly as it is now.

## Impact

| path | what changes |
|---|---|
| `tools/fixtures/foreign-project/tools/loop/loop.conf` | the missing budget default |
| `tools/portability-mutations.json` | record `7.1` |
| `.pi/skills/mutation-runner/SKILL.md`, `.pi/skills/portability-check/SKILL.md` | the record count |

No `:app`, `:auto`, `:core`, `buildSrc`, native, Gradle or `tools/loop/**` path is touched, so no engine case
moves and the loop's own suite is not this change's gate. `guidelines/Build.md` §2a's "a defect fix owes a test
that fails on HEAD before the fix" is what record `7.1` makes permanent. No submodule patch and no bridge
override.

## Not in this change

- **Why the fixture drifted.** `tools/loop/loop.conf` maps to no `Check portability` command in
  `LOOP_GATE_SUITES` (`gate.sh`'s plan), so a change that adds a key to it is not told the fixture must follow —
  filed as **#289**, not fixed here.
- **`LOOP_BASE_REF`.** The base branch is a project value the fixture also has to name, and it is **#303**'s
  item; it lands in the next commit, on top of this one, so this commit's tree is the pre-#303 state.
