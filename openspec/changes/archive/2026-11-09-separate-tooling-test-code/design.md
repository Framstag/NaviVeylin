# Design

## Context

The tooling under `tools/` is an **engine** copied per project (`guidelines/BacklogTooling.md` §10,
`PORTING.md`). Its production tools and its test code have always shared a directory. The move separates
them by directory without changing any tool's behaviour, so the design is a set of layout decisions and the
mechanical consequences of each.

Two facts bound the design: 27,706 lines of test code is 60% of `tools/`, and the test code is reached only
by path (a driver names `tools/loop/run.sh`; a mutation set names its `case-cmd`), so a move costs reference
rewrites, never a redesign. A third fact constrains the portability check: it currently tells engine from test
by a file-name suffix, which a directory move makes wrong.

## Decision 1 — Layout: per-group `tests/` directory

**Chosen: per-group `tools/<group>/tests/`, with `tools/tests/` for a `tools/`-root tool.**

| alternative | why not |
|---|---|
| one `tools/tests/` for everything | easy to see, but a test of `tools/loop/run.sh` stops sitting near `run.sh`, and the group boundary the engine already carries (`loop`, `issues`, `sensors`) is thrown away |
| top-level `tests/tooling/` | strongest separation, but the tooling is a copy-per-project engine that travels as one `tools/` tree; a test root outside `tools/` breaks "copy one directory" and inflates `PORTING.md` |
| no move | the stated problem — a reader cannot tell engine from test by the path — stays |

## Decision 2 — Naming: file names unchanged

**Chosen: keep `*-selftest.sh`, `*-cases.sh`, `*-mutations.sh`, `*-mutations.json`.**

| alternative | why not |
|---|---|
| shorten to `selftest.sh`, `cases.sh`, `mutations.sh` inside the `tests/` dir | cleaner in isolation, but every one of ~97 live references becomes a rename *and* a move, and a bare `cases.sh` in a flat editor tab is less identifiable, not more |

The rename churn is the cost the move avoids; only the directory changes.

## Decision 3 — Fixtures: one shared root plus per-group local

**Chosen: `tools/fixtures/` → `tools/tests/fixtures/` (shared); each group's local fixtures move beside its
tests.**

The fixtures are referenced across groups: `tools/fixtures/foreign-project` is read by the loop, portability
and mutate tests; `tools/loop/fixtures` is read by the loop and portability tests. Collapsing them all into
`tools/tests/fixtures/` would erase a real group boundary; leaving them put would keep half the test data
outside the `tests/` tree the change exists to create.

## Decision 4 — Root resolution: `git rev-parse --show-toplevel`

**Chosen: every driver resolves the tree from git, not from the driver's own depth.**

| alternative | why not |
|---|---|
| keep `${BASH_SOURCE[0]}/..` and fix each depth | works, but the depth is exactly what the move changed; a later move repeats the edits, and a driver copied to another project's tree must still resolve |
| an exported `*_ROOT` the caller sets | breaks a driver invoked directly by a reader or the CI step, which is how the self-tests are documented to run |

`git rev-parse --show-toplevel` is already a hard prerequisite of the tooling (`PORTING.md`), so it adds no
dependency. The driver keeps its `${BASH_SOURCE[0]}` fallback only where a self-test runs outside a git tree.

## Decision 5 — The portability check excludes by location, not name

**Chosen: derive the engine file set from production tool directories and skip `tests/` by directory.**

| alternative | why not |
|---|---|
| keep the `grep -vE '...-selftest\.sh|...'` suffix filter and add `tools/tests/` | the filter is the bug the move exposes: a test file that does not carry the suffix (a future `helper.sh`) is scanned, and a moved one is caught only because its name still matches — the directory is the durable signal |
| scan everything and whitelist | the check's rule is that its exclusions are deliberate and visible (spec `tooling-portability`); a directory skip is one visible rule, a growing name list is not |

## Decision 6 — Archived evidence is not rewritten

**Chosen: `openspec/changes/archive/**` keeps the old paths byte-identical.**

Archived evidence records what was true when it was written; rewriting a path inside a gate tally or a log
would falsify the record. This follows the project's existing rule for migrated citations (`Backlog.why.md`).
The stale-path concern for *live* files is handled by rewriting them; the concern does not extend to history.

## Risk

| risk | mitigation |
|---|---|
| a missed reference leaves a red CI step or a dangling doc path | the check suite is the detector: each touched self-test and `check-doc-routes` / `check-rule-routes` run in the change's verification, and CI fails on the old path |
| a driver's root resolution silently points one level up | the depth-independent resolver plus the loop self-test, which starts the engine by path |
| the portability check's value set changes and leaks a project value | `tools/check-portability-selftest.sh` runs over the synthetic foreign project after the exclusion change |

## Verification

No compiled file is touched, so the change's verification is the tooling's own checks
(`guidelines/Build.md` §2, the scope block): `bash tools/loop-selftest.sh`,
`bash tools/check-portability-selftest.sh`, every moved `*-selftest.sh`, and `bash tools/check-rule-routes.sh`
after the exclusion change. No device, no emulator, no Gradle target is involved.

The new invariant — "a `tests/` directory is excluded from the value scan" — gets one revert-check: a
mutation that makes the check read a file under `tools/tests/` must turn the named portability case red,
then be restored and re-run green.

## Open decisions

none
