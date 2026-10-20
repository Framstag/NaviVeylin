# fix-create-files-class-and-category — tasks

## 1. The red cases (spec: issue-backlog, "One issue per distinct finding" · "Labels carry the item's kind, area, state and provenance")

- [x] 1.1 `tools/issue-tooling-cases.sh`: `create#carriesTheFindingsClassAndCategory` — a finding whose kind and
      area are in its `class`/`category` fields alone must file `--label class/bug --label
      category/build-and-harness`; a finding that names both routes files each label once; a misspelt class is
      refused with exit 3 `label-opaque` and no write. Registered in `ISSUES_SHELL_CASES` and
      `issues_run_shell_case`.
      Verify: `bash tools/issue-tooling-selftest.sh --case create#carriesTheFindingsClassAndCategory` — **red**
      on the base tree (`'--label class/bug' not found in: gh issue create …`, exit 1,
      `evidence/red-carries.txt`), **green** after task 2 (`evidence/green-cases.txt`).
- [x] 1.2 `tools/issue-tooling-cases.sh`: `create#refusesAClassItsLabelsContradict` — `labels: ["class/improvement"]`
      with `class: "bug"`, and the mirror case in the `category` namespace, each refused with exit 2 naming both
      values and no `gh issue create` call.
      Verify: same suite, `--case create#refusesAClassItsLabelsContradict` — **red** on the base tree
      (`a filing naming two classes is a usage error: …: expected '2', got '0'`, exit 1,
      `evidence/red-contradict.txt`), **green** after task 2.

## 2. The fix (spec: same two requirements)

- [x] 2.1 `tools/issues/create.sh`: after the `.labels[]` / `--label` / `--status` merge, append
      `class/<clazz>` and `category/<category>`; de-duplicate the merged set by exact label so a caller that
      already spells them (the sensor path, the fixtures) files one label per value. Document the contract in the
      header and follow the shifted `--help` range.
      Verify: tasks 1.1 and 1.2 go green; the fields then pass the existing vocabulary gate and the existing
      namespace-uniqueness refusal with no new rule.

## 3. The falsification (`.pi/skills/revert-check`)

- [x] 3.1 `tools/create-mutations.json`: mutation `1.3` replaces the join with `labels+=()`, naming
      `create#carriesTheFindingsClassAndCategory`; the set's comment states what it now proves.
      Verify: `bash tools/mutate.sh --mutations tools/create-mutations.json` — 3 of 3 go red and restore green
      (`evidence/mutations-create.txt`), and `bash tools/mutate.sh --mutations tools/create-mutations.json
      --list` — the CI step's call — parses.

## 4. The gate (no compiled file touched)

- [x] 4.1 `bash tools/issue-tooling-selftest.sh` — the gate for `tools/issues/**`, the CI step "Check issue
      tooling". Verify: 102 of 102 cases pass, offline, no token, JDK 27 (`evidence/issue-tooling-selftest.txt`).
      No Gradle gate applies: this change touches no compiled file (`guidelines/Build.md` §2, the scope block).
- [x] 4.2 Neighbouring gates the writer feeds: `bash tools/sensor-selftest.sh` (43 cases, 1 pre-existing failure
      `workflow#onlyTwoTriggers`, which counts `if:` gates in an untouched `sensors.yml` — the file is identical
      in the project tree and in this worktree, so the failure predates this change), `bash
      tools/check-portability.sh` (OK), `bash tools/issues/check-metadata.sh` (self mode, 0 violations), `bash
      tools/loop-selftest.sh` (127 of 128, the failure being this tree's documented
      `.pi/worktrees/` artefact `worktree#keepsByDefaultAndRemovesOnRequest`).
      Verify: `evidence/neighbouring-gates.txt`, `evidence/sensor-selftest.txt`, `evidence/loop-selftest.txt`.
- [x] 4.3 `guidelines/Backlog.md` §6 "How an item becomes work": the intake paragraph states that the filing
      path files the finding's `class` and `category` as labels, so a guideline reader learns the fact where the
      intake route is described. Verify: `bash tools/check-doc-routes.sh` — 89 of 89 section references resolve.

## 5. Evidence and archive

- [x] 5.1 Copy every cited log into `evidence/` (this change) and into the item's evidence directory.
- [x] 5.2 Archive the change (`openspec archive`) and confirm the delta merged into
      `openspec/specs/issue-backlog/spec.md`.
