# Design — the base branch is a configuration key

## Alternatives the root-cause evidence eliminated

A `fix-*` change names the options the evidence rules out.

1. **Leave `main` as the library's fallback and tell a port to export `LOOP_BASE_REF`.** Impossible: the value is
   the project's, and `tools/loop/loop.conf` is the file that holds the project's values (`guidelines/Backlog.md`
   D16, `PORTING.md`). An exported variable is not committed, not reviewed and not visible to the next person who
   runs the loop — which is the defect, restated.
2. **Assign the branch outright in `loop.conf`.** Possible, and wrong: `loop_resolve` sources the file *after* the
   environment, so `LOOP_BASE_REF="main"` silently discards an inherited value. A caller who points the loop at a
   release branch would be ignored without a word — mutation `56.3` makes exactly that state and the new case goes
   red, so the choice is measured rather than asserted.
3. **Derive the branch from `origin/HEAD`.** Impossible as a rule: a local-only repository has no remote, and
   #301 deliberately records the *checked-out project's* base branch rather than a remote's default, which can
   differ from the branch the tree's landing target is (`run.sh` merges into the project tree).
4. **Move the value into `tools/tooling.json`.** Impossible: the profile is the project's *vocabulary* — repository
   name, label names, paths — and the portability check reads it as the set of values no engine may name
   (`tools/check-portability.sh`). Run configuration lives in `loop.conf`, which `lib.sh` sources; the profile is
   never sourced by an engine.

What remains is the key. The evidence leaves one fix, so the item is loop-eligible.

## The written form, and why it is not a plain assignment

```
LOOP_BASE_REF="${LOOP_BASE_REF:-main}"
```

The expansion encodes the precedence the item asks to be stated: the environment wins, the recorded value fills
what it left unset, and `main` remains the engine's fallback only for a configuration that records nothing. The
comment above the key says this, because the next editor's instinct is to "simplify" it to a plain assignment —
and that simplification is what record `56.3` catches.

## The fixture, and why it names `trunk`

`tools/fixtures/foreign-project/` is the proof that a fresh project can adopt the tooling. A fixture that named
no base branch (the state on `main`) showed nothing about the key, so the same omission could return unnoticed.
`trunk` is deliberately *not* `main`: the case asserts that the branch the fixture records is the branch its tree
is created on and the commit the run records, so a value equal to the engine's default would prove nothing.

`git_init` gains an optional branch argument for this (`git_init <dir> [branch]`); the portability self-test's own
throwaway trees keep `main`, and only `foreign_tree` passes the fixture's recorded value — read by sourcing the
fixture's `loop.conf` with `LOOP_BASE_REF` empty, so the case asserts the key rather than a pattern of how it is
written.

## Verification

- `bash tools/loop-selftest.sh --case state#baseBranchIsAConfigurationKey` — green, and red with HEAD's
  `loop.conf` restored (`evidence/green-case.log`, `evidence/red-HEAD.log`).
- `bash tools/loop-selftest.sh --group state` — 16 of 16 (`evidence/state-group.log`).
- `bash tools/loop-selftest.sh --from-git 30c23b52` — the whole engine suite, owed because the change owns
  `tools/loop/**` and touches `tools/loop-cases.sh`, a core file (`evidence/full-suite.log`).
- `bash tools/loop-mutations.sh --only 56.3` — red, restore, green (`evidence/revert-check-56.3.log`); `--check`
  for the record set, `tools/loop-mutations-selftest.sh` for the harness (`evidence/records.log`).
- `bash tools/check-portability-selftest.sh` — 28 of 28 (`evidence/portability-suite.log`);
  `bash tools/check-portability.sh` (`evidence/check-portability.log`);
  `bash tools/mutate.sh --mutations tools/portability-mutations.json --only 7.2`
  (`evidence/revert-check-7.2.log`).
- Doc gates for the `.md` files: `tools/check-doc-routes.sh`, `tools/check-doc-routes-selftest.sh`,
  `tools/check-todo-references.sh`, and `bash tools/gen-feature-list.sh --check-classification` for the
  `openspec/` tree (`evidence/gate.log`).

No device measurement: no rendered surface and no runtime path changes.
