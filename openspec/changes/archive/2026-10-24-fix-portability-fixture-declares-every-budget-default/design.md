# Design — the fixture declares every budget default

## Alternatives the root-cause evidence eliminated

A `fix-*` change names the options the evidence rules out, not a menu of viable ones.

1. **Teach the case to ignore keys the fixture omits.** Impossible: the scenario says the fixture declares each
   one, and its point is that "a budget the engine gains fails this case rather than the porting recipe"
   (`openspec/specs/tooling-portability/spec.md`). Making the case lenient deletes the invariant instead of
   satisfying it — and `Check portability` would then be green on a fixture that cannot run a fresh `run.sh`.
2. **Teach the fixture to inherit the engine's configuration.** Impossible: the fixture's whole value is that it
   is a *foreign* project — its own gate command, its own agent command, no build, its own budget values (D2 in
   the portability change). A fixture that reads the host's `loop.conf` proves nothing about a porting project.
3. **Record the value as an engine default instead.** Impossible: the key *is* in `tools/loop/loop.conf` and
   resolved through `loop_conf_value` by `tools/loop/run.sh:90` — the engine has no default for it, which is why
   the case's list contains it. The omission is in the fixture, not in the engine.

The remaining possibility — the key belongs in the fixture — is the one the measurement leaves, so there is one
fix and this item is loop-eligible.

## The value

`off`, the value the engine ships (`tools/loop/loop.conf:37`). The key decides whether a difference in the
project tree *outside* the worktree stops an item (`run.sh:739`). `off` is documented for a tree a run does not
own exclusively; the fixture's tree is a throwaway of its own, so `on` would also be defensible — the case reads
only that the key is declared, and a ported project keeps the engine's default until it has a reason to change
it. Recording `off` keeps the fixture a faithful copy of the shipped default rather than a second opinion.

## The mutation record, and why it is owed

The invariant is shipped, and nothing proved the case that guards it can fail: the case's own body derives the
key list from the engine, so a fixture that loses a key is caught — but that path had been exercised only by
accident (#286's omission). Record `7.1` deletes the line and requires
`foreign#fixtureDeclaresEveryBudgetDefault` to go red, then restores the file and requires it green. That is the
`revert-check` rule (`tools/mutate.sh`, `.pi/skills/revert-check`) applied to a shipped invariant that had no
record.

## Verification

- `bash tools/check-portability-selftest.sh --case foreign#fixtureDeclaresEveryBudgetDefault` — red at
  `30c23b52` (recorded in `evidence/red-HEAD.log`), green with the key (`evidence/green-case.log`).
- `bash tools/check-portability-selftest.sh` — the whole set green (`evidence/full-suite.log`).
- `bash tools/mutate.sh --mutations tools/portability-mutations.json --only 7.1` — red, restore, green
  (`evidence/revert-check.log`).
- `bash tools/check-portability.sh`, `bash tools/mutate.sh --mutations tools/portability-mutations.json --list`,
  `bash tools/check-doc-routes.sh` — the gate `gate.sh` maps for `tools/fixtures/foreign-project/*`, `PORTING.md`
  and `*.md` (`evidence/gate.log`).

No device measurement: the change touches no rendered surface.
