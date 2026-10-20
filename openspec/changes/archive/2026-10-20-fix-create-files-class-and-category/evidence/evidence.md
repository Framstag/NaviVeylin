# Item #201 — evidence

Verdict: **closed**. Change `fix-create-files-class-and-category`, archived as
`openspec/changes/archive/2026-10-20-fix-create-files-class-and-category` (delta merged into
`openspec/specs/issue-backlog/spec.md`).

## Phase A — the claim, verified in the tree

`tools/issues/create.sh` requires a finding's `class` and `category` (`:78-79` at base `db1fe8e`) and composes
the label set from `.labels[]`, `--label` and `--status` only (`:109-118`); the two fields reach the fingerprint
(`:165`) and nothing else. Not fixed on HEAD, and no check catches the consequence.

Root cause (one sentence): the finding's required `class` and `category` never enter the label set, so the two
facts the writer insists on are the two facts it discards, and a finding that states its kind only in those
fields files an item no selector reads.

Owning capability: `issue-backlog`, "One issue per distinct finding" (scenario "A new finding is filed": the
issue's "labels record the kind and the area") and "Labels carry the item's kind, area, state and provenance".
Owning guideline: `guidelines/Backlog.md` §4 "The invariants" / §6 "How an item becomes work" (intake).

## Phase B — the gate

1. claim verified today ✓ · 2. one root cause ✓ · 3. exactly one fix ✓ (the alternatives the evidence eliminates
are in `design.md`) · 4. falsifiable WHEN/THEN that is false on HEAD ✓ · 5. one capability delta, 4 files ✓ ·
6. decidable by a host test (offline shell suite) ✓ · 7. n/a (bug).

## Red tests on HEAD

| case | red on `db1fe8e` | evidence |
|---|---|---|
| `create#carriesTheFindingsClassAndCategory` | `'--label class/bug' not found in: gh issue create …` (exit 1) — the recorded invocation carries no label at all | `red-carries.txt` (2026-10-20T20:03:05Z) |
| `create#refusesAClassItsLabelsContradict` | `expected '2', got '0'` (exit 1) — the contradiction is filed, keeping the array's value | `red-contradict.txt` (2026-10-20T20:03:07Z) |

No JUnit XML: the gate is a bash self-test, so the report is its per-case line and exit code (precedent: item
#200, whose gate is also a shell suite).

## Green

- `green-cases.txt` — both cases PASS (2026-10-20T20:03:20Z).
- `issue-tooling-selftest-final.txt` — 102 of 102 cases pass, offline, no token, JDK 27 (2026-10-20T20:24:40Z).
  This is the gate for `tools/issues/**` (CI step "Check issue tooling"), and no compiled file is touched, so no
  Gradle gate applies (`guidelines/Build.md` §2 scope block).
- `mutations-create-final.txt` + `revert-checks.md` — mutation `1.3` red→green; the whole set 3/3 falsified.
- `neighbouring-gates.txt` — `tools/check-portability.sh` OK (44 engines, 103 values); `check-metadata.sh` self
  mode 0 violations; `tools/check-doc-routes.sh` 89/89.
- `sensor-selftest-full.txt` — 42 of 43, the failure being the pre-existing `workflow#onlyTwoTriggers` (it counts
  `if: steps.record.outputs.ready == 'true'` occurrences in `.github/workflows/sensors.yml`: expects 4, file has
  5, and the file is byte-identical in the project tree and in this worktree — untouched by this change). All 15
  `run#` cases, which file items through `create.sh`, pass.
- `loop-selftest.txt` — 127 of 128, the failure being this tree's documented `.pi/worktrees/` artefact
  `worktree#keepsByDefaultAndRemovesOnRequest`.

## Verification of the change

- `openspec validate … --strict` valid before archive; `openspec archive` reported "2 modified" spec deltas and
  the merged text is in `openspec/specs/issue-backlog/spec.md` (lines 15, 23, 31, 66, 71).
- `openspec validate --specs` — 176 passed, 0 failed.
- Artifact hygiene: every `tasks.md` uses `- [x]` markers, no bare numbered items, `openspec/config.yaml`
  untouched.
- One non-blocking warning: the proposal's `## Why` exceeds 1000 characters. It is the Phase A evidence the
  loop's own rules require there (root cause, both red cases with their lines, the owning requirement), so it
  stays.
