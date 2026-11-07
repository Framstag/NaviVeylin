# Tasks — the base branch is a configuration key

## 1. The test that reproduces the defect

- [x] 1.1 `tools/loop-cases.sh`: case `state#baseBranchIsAConfigurationKey` — the configuration records
      `LOOP_BASE_REF`; the run records that branch's commit; an inherited `LOOP_BASE_REF` wins over the recorded
      value. Registered in the case list and the dispatcher, declared for `tools/loop/loop.conf`,
      `tools/loop/lib.sh`, `tools/loop/state.sh`, `tools/pi-child-events.sh` and `tools/tooling.json` — the files
      it reads.
      Verify: red with HEAD's `loop.conf` restored, its message naming the missing key
      (`evidence/red-HEAD.log`, exit 1); green with the change (`evidence/green-case.log`).
      Parent: `agent-issue-loop` — "The base a run records is the base branch's commit", scenarios *The base
      branch is the project's to name* and *An inherited base branch wins over the recorded one*.

## 2. The fix

- [x] 2.1 `tools/loop/loop.conf`: `LOOP_BASE_REF="${LOOP_BASE_REF:-main}"`, with the comment stating that the file
      is sourced after the environment, that the expansion is what lets an inherited value win, and that `main`
      is only the fallback for a configuration that records none.
      Verify: `bash tools/loop-selftest.sh --case state#baseBranchIsAConfigurationKey` → `PASS`
      (`evidence/green-case.log`).
      Parent: the same requirement and its two scenarios.
- [x] 2.2 `tools/loop/lib.sh`: the comment above `loop_base_ref()` names the key as the project's and `main` as the
      fallback for a configuration that records none.
      Verify: the case of 2.1 reads the value through that function; `bash tools/check-portability.sh` stays
      green (`evidence/check-portability.log`).

## 3. The invariant, made permanent

- [x] 3.1 `tools/loop-mutations.sh`: record `56.3` — `loop.conf` written as an outright `LOOP_BASE_REF="main"`
      assignment, which the sourced configuration applies *after* the environment;
      `state#baseBranchIsAConfigurationKey` must go red, restore, green.
      Verify: `bash tools/loop-mutations.sh --only 56.3` → `RED` / `restore … green`
      (`evidence/revert-check-56.3.log`).
      Parent: the same requirement — the record is what keeps the fill-if-unset form from being "simplified".

## 4. The ported project, which is where the omission was invisible

- [x] 4.1 `tools/fixtures/foreign-project/tools/loop/loop.conf`: the fixture names its own base branch, `trunk`,
      through the same key and the same expansion.
      Verify: `evidence/portability-suite.log` (the foreign cases run the whole toolchain in that tree).
- [x] 4.2 `tools/portability-cases.sh`: `git_init` takes the branch its tree is created on; `foreign_tree` passes
      the fixture's recorded value, read by sourcing the fixture's configuration with `LOOP_BASE_REF` empty.
      Case `foreign#baseBranchIsTheFixturesConfiguration` asserts that value is not the engine's default, that it
      is the branch the tree is created on, and that it is the commit the run records.
      Verify: `PASS` inside `evidence/portability-suite.log`; red under record `7.2`.
      Parent: `tooling-portability` — "The synthetic project is a fixture, not a second project", scenario *The
      fixture names its own base branch*.
- [x] 4.3 `tools/portability-mutations.json`: record `7.2` — the fixture's key reverted to `main`, and the case
      must go red.
      Verify: `bash tools/mutate.sh --mutations tools/portability-mutations.json --only 7.2` →
      `RED(exit 1)` / `GREEN(exit 0)` (`evidence/revert-check-7.2.log`).

## 5. The documents that name the configuration

- [x] 5.1 `PORTING.md`: the third file's recipe gains the key, its written form and the reason for it — a project
      on `trunk` or `develop` records it here, so nothing about the branch lives in the engine.
- [x] 5.2 `guidelines/Backlog.md` §10: the `loop.conf` column of the "engine, copied unchanged / the project,
      adapted" table names the branch every item descends from.
- [x] 5.3 `.pi/skills/portability-check/SKILL.md` and `.pi/skills/mutation-runner/SKILL.md`: 28 cases, 15 records.
      Verify: `evidence/gate.log` (`tools/check-doc-routes.sh`, `tools/check-doc-routes-selftest.sh`), and the
      `--list` counts it quotes.

## 6. The suites and the gate

- [x] 6.1 The group the item names: `bash tools/loop-selftest.sh --group state` → `all 16 cases passed`.
      Verify: `evidence/state-group.log`.
- [x] 6.2 The whole engine suite, owed for a change that owns `tools/loop/**`:
      `bash tools/loop-selftest.sh --from-git 30c23b52`.
      Verify: `evidence/full-suite.log`, its summary line quoted.
- [x] 6.3 The declared record set and the harness that guards it: `bash tools/loop-mutations.sh --check`
      (`all 143 cases passed`) and `bash tools/loop-mutations-selftest.sh` (`all 34 cases passed`).
      Verify: `evidence/records.log`.
- [x] 6.4 The portability side, the gate `gate.sh` maps for `tools/fixtures/foreign-project/*`,
      `tools/portability-*` and `PORTING.md`: `bash tools/check-portability-selftest.sh` → `all 28 cases passed`,
      `bash tools/check-portability.sh` → `OK`, `bash tools/mutate.sh … --list` → `15 mutation(s)`.
      Verify: `evidence/portability-suite.log`, `evidence/gate.log`.
- [x] 6.5 The document gates for the `.md` and `openspec/` paths this change edits:
      `tools/check-doc-routes.sh`, `tools/check-doc-routes-selftest.sh`, `tools/check-todo-references.sh`,
      `bash tools/gen-feature-list.sh --check-classification`.
      Verify: `evidence/gate.log`, every exit `0`.
- [x] 6.6 The delta validated before archive: `openspec validate fix-loop-base-ref-is-a-configuration-key
      --strict` → `Change … is valid`; the archived `specs/` directory is the delta it read.
      Verify: the two `specs/*/spec.md` files of this change directory.
