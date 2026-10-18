# Proposal

## Why

The mechanism this series builds — issue tooling, a sensor registry, a loop wrapper, a persisted
priority — is meant to be used in other projects. Five changes now state the property in their designs:
one profile holds every project-specific value, no engine names a project value, and the one piece of
this repository's backlog format sits behind a source adapter. **None of that is proven.** A property
that lives only in five design documents is the shape every check in this series exists to falsify, and
without a proof the first foreign project discovers the leaks one at a time.

Two things are missing and both are mechanical:

- **No check forbids a project value in an engine.** A `grep` for the repository name, an allowlist
  entry, a category value or a project path would catch the leak the moment it is introduced — including
  in a later change that has no reason to think about portability.
- **No fixture proves the toolchain works anywhere else.** The existing self-tests run in throwaway git
  repositories, which is *almost* the proof: they are throwaway but they are still this project, with
  this project's profile. A second fixture — a synthetic project with a different repository, a different
  allowlist and a different vocabulary — is the difference between "the tests pass" and "the tool runs
  in someone else's repo".

The change is small and it is the one that retires the lease on portability: after it, a second project
is a copy, a profile and a `PORTING.md`, not a code review.

## What Changes

- **One check, one rule set.** `tools/check-portability.sh` fails when an engine file names a project
  value: the repository (`Framstag/NaviVeylin`), an allowlist entry, a category or class value, a project
  path, or the project's name. It reads what is allowed from `tools/tooling.json` and the per-tool
  configs, so the rule has exactly one definition of "this project's values".
- **A synthetic foreign project, exercised end to end.** `tools/fixtures/foreign-project/` is a complete
  throwaway project: a different repository name, a different allowlist entry, a different category
  vocabulary, a different band set, a different sensor row, and a tiny `findings.json` as its "backlog".
  The self-test runs create → list → update → close → reopen, one sensor row, one metadata pass and a
  dry-run loop there, and asserts every one of them worked **without an edit to an engine**.
- **The discovery rule gets its own cases.** "Always the current project" is asserted where it can fail:
  the tools are run from a subdirectory, from a `git worktree`, with an absolute path, and with
  `GITHUB_REPOSITORY` deliberately set to something wrong — the profile and the repo must come from the
  tree, not from the environment.
- **`PORTING.md` is the recipe with a verified command in it.** What to copy, which three files to edit
  (`tools/tooling.json`, `tools/sensors/sensors.json`, `tools/loop/loop.conf`), what to delete (the
  `TODO.md` adapter), and the one command that proves the copy works. Every command in it is exercised by
  the self-test, so the document cannot drift into fiction.
- **A copy is the distribution model, and it stays one.** No submodule, no package, no version pin.
  `PORTING.md` states what an update from upstream costs (re-copy the engine, keep the profile) and what
  a divergent engine costs (you own the merge).
- **Explicitly not in this change**: extracting the engine to a shared repository or a published CLI
  (rejected in `github-issue-tooling` D11 — premature with one consumer, and the profile seam is what
  would make it cheap), any change to the five changes' designs, and any agent sandbox.

## Capabilities

### New Capabilities

- `tooling-portability`: an engine names no project value and a check fails when one appears; the check's value set is the project's own rather than a list of its own; discovery is exercised where it actually breaks — a nested directory, a linked working tree, an environment that contradicts the tree, and no project at all; a project whose repository, allowlist, vocabulary and bands all differ runs the whole toolchain without an engine edit; the porting recipe's commands are executed by the check so the recipe cannot rot; and the synthetic project is a versioned fixture rather than a second project.

### Modified Capabilities

None.

## Impact

New:
- `tools/check-portability.sh` + `tools/check-portability-selftest.sh`
- `tools/fixtures/foreign-project/` — a synthetic project: `tools/tooling.json`,
  `tools/sensors/sensors.json`, `tools/loop/loop.conf`, a `findings.json`, a fixture `gh` response set
  and a `README.md` that says what it is for
- `PORTING.md` at the repository root

Modified:
- `guidelines/Backlog.md` — the sections this change makes stale: its own row in §1, and the §12 rows for the decisions it lands
- `.github/workflows/build.yml` — one `Check portability` step running the check, token-free
- `AGENTS.md` — one line naming `PORTING.md` as where a copy is described

Not affected: no Kotlin/Java/C++ under `app` / `auto` / `core` / `osmscout-client-java` / `buildSrc`; no
manifest; no Gradle configuration; no native or JNI boundary; no release path; no engine file. This
change adds checks and fixtures and changes no behaviour of the tools it proves.

Scope: agent-harness tooling only — variant-independent; neither phone-only nor car-only work. Guidelines
referenced: `guidelines/Build.md` §1 (harness prerequisites), §2 (the iteration protocol and its scope
block), §3. No `UI.md`, `MapRendering.md`, `Design.md`, `Regulatory.md` or `Logging.md` section is
affected.

**Additive.** Nothing is removed or redefined. **Rollback**: revert the check, its self-test, the fixture
directory, `PORTING.md` and the one CI step. The five changes' engines are untouched, so a rollback costs
the proof and nothing else.

**Verification.** The check's own cases (it must fail on a planted project value and pass on the real
tree); the foreign-project end-to-end run for create, list, update, close, reopen, a sensor row, a
metadata pass and a dry-run loop; the four discovery cases; and every command in `PORTING.md` executed by
the self-test. No device or pixel measurement applies: the change produces no UI.
