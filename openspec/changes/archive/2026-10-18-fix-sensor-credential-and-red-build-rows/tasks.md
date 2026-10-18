# Tasks

Parent spec for every task below: `backlog-sensors` — delta in `specs/backlog-sensors/spec.md` (three
requirements modified: "A check reports one of three outcomes, and only clearing closes"; "The checks run in
a pipeline of their own, pinned and least-privileged"; "A row's declared input is published, checked against
its producer, and read for the commit under test"). Design: `design.md` (D1 = the pipeline gates on the
conclusion, D2 = the credential's home in §9, D3 = `--rows`, D4 = how the rule is proven). This change
touches no compiled file — no Kotlin, no C++, no Gradle logic — so every verification below is a structural
check or an offline shell suite (`guidelines/Build.md` §2, scope block): **no Gradle build, no test suite, no
device**, and that exclusion is deliberate, not an omission.

## 1. Reproduce the defect

- [x] 1.1 Write the cases that reproduce the defect and record their red-on-HEAD run before touching a
  shipped file. The cases are `run#rowsSelectsSeveralRows`, `run#rowsRefusesAnUndeclaredId`,
  `workflow#aRedRecordRunsNoRecordRow`, `sensors#theDocumentedCredentialCoversEveryDeclaredRow`
  (`tools/sensor-cases.sh`) and `inputs#reportsTheRunsConclusion` (`tools/build-inputs-cases.sh`); the
  defect's own measured command — `run.sh --due --now 2026-10-18T03:00:00Z` against a tree whose record is
  the partial one a red build publishes — is recorded beside them (spec: backlog-sensors — A check reports
  one of three outcomes; design D4).
  Verify: `bash tools/sensor-selftest.sh` = 4 of 40 cases failed, `bash tools/build-inputs-selftest.sh` =
  1 of 11 failed, every pre-existing case green, and the repro command's exit 8 for each of `0 3`, `0 4`,
  `0 6` with `verdict=error` while `broken-main` reads the same record correctly.
  **Done 2026-10-18 17:33, HEAD `bf6f5d7`:** `evidence/red-on-head.log` §1 (the four ticks: `build-duration`
  → `error-no-timing-record`, `package-size` → `error-no-package-artifact`, `coverage` →
  `error-no-coverage-report`, each `exit=8`; `broken-main` → `verdict=violation measurement=failure`,
  `exit=0`) and §2 (`the listed row that is due runs: sensors: run: unknown argument --rows`, `the pipeline
  has one row step per family of rows: expected '2', got '0'`, `the doctrine does not name the tracker's
  write scope`, `fetch-build-inputs: unknown argument --conclusion-file`). TALLY: 5 new cases red, 0
  pre-existing cases red.

## 2. The credential's scope gets one live home (#160)

- [x] 2.1 `guidelines/Backlog.md` §9: replace "holding issue-write and nothing else" with the credential's
  permissions — **Issues: Read and write** plus **Pull requests: Read**, this repository only, an allowlisted
  owner — naming the row that needs the second one, and correct the red-build paragraph it already carries
  (task 5.1 states what replaces it) (spec: backlog-sensors — The checks run in a pipeline of their own;
  design D2).
  Verify: `sed -n '/^## 9\./,/^## 10\./p' guidelines/Backlog.md` names both permissions on their own lines,
  and `bash tools/sensor-selftest.sh --case sensors#theDocumentedCredentialCoversEveryDeclaredRow` passes.
  **Done 2026-10-18:** §9 states "**Issues: Read and write** plus **Pull requests: Read**, its repository
  access limited to this repository and its owner an allowlisted person: Issues is what files, annotates and
  closes an item, and Pull requests is what a row listing pull requests needs"; the case passes (it reads the
  scope out of §9 and the `gh` verbs out of the rows' own commands).
- [x] 2.2 `.github/CODEOWNERS` line 3: the same claim corrected in the file that guards a workflow change,
  pointing at §9 (spec: backlog-sensors — The pipeline's own changes are reviewed; design D2).
  Verify: the file names `Issues: Read and write` and `Pull requests: Read` and `guidelines/Backlog.md` §9,
  and `bash tools/sensor-selftest.sh --case workflow#onlyTwoTriggers` still finds the workflow directory
  covered.
  **Done 2026-10-18:** the comment now reads "`gitignore`d secret, created with `Issues: Read and write` and
  `Pull requests: Read`, this repository only, owned by an allowlisted author — `guidelines/Backlog.md` §9";
  `workflow#onlyTwoTriggers` passes.
- [x] 2.3 The archived `wire-sensor-inputs` artifacts stay untouched (issue #160: they are history).
  Verify: `git status --porcelain openspec/changes/archive/` is empty for that change's files and
  `git diff --stat` names no path under `openspec/changes/archive/2026-10-18-wire-sensor-inputs/`.
  **Done 2026-10-18:** the change's diff names no archived path (`git diff --stat`); #160's decision is
  recorded in this change instead.

## 3. The fetcher reports the run's conclusion (#168, design D1)

- [x] 3.1 `tools/fetch-build-inputs.sh`: a `--conclusion-file <path>` flag — the conclusion of the run it
  chose is selected from `gh run list` (`--json databaseId,headSha,conclusion`) and written after the
  download, so nothing is written when no record was fetched; the exit contract (0/3/1) is unchanged (spec:
  backlog-sensors — The fetcher reports the run's conclusion).
  Verify: `bash tools/fetch-build-inputs.sh --help` prints the flag; the flag appears inside the step's own
  usage block.
  **Done 2026-10-18:** the flag is parsed, the help's range is `sed -n '2,20p'` (unchanged output plus the new
  flag on the usage line), the write happens after `gh run download` succeeds and before the final line.
- [x] 3.2 `tools/build-inputs-cases.sh`: `inputs#reportsTheRunsConclusion` — a `failure` conclusion is
  reported for a red build's record, `success` for a green one, and the exit-3 path writes no file at all
  (spec: backlog-sensors — The fetcher reports the run's conclusion).
  Verify: `bash tools/build-inputs-selftest.sh --case inputs#reportsTheRunsConclusion` passes and the whole
  suite is 11 of 11 (was 1 of 11 red on HEAD).
  **Done 2026-10-18:** `PASS inputs#reportsTheRunsConclusion`, `11 cases, 0 failed`.
- [x] 3.3 Falsify the new report: `tools/build-inputs-mutations.sh` gains mutation 5.13 (`conclusion=success`
  hardcoded), and the named case must go red under it (guidelines/Build.md §2, `revert-check`).
  Verify: `bash tools/build-inputs-mutations.sh --only 5.13` reports "red under the mutation, green after the
  restore", and the full set is 13 behaved / 0 did not.
  **Done 2026-10-18:** `MUTATION 5.13: inputs#reportsTheRunsConclusion red under the mutation, green after
  the restore`; the full run `build-inputs-mutations: 13 behaved, 0 did not`.

## 4. The runner selects the rows a step names (design D3)

- [x] 4.1 `tools/sensors/run.sh`: `--rows <id,id>` (comma-separated and repeatable) restricts the candidates;
  each named row's cadence still decides whether it runs; an id the registry does not declare is refused
  before any row runs, naming the declared ids; `--only <id>` keeps its meaning (spec: backlog-sensors — A
  step selects the rows it names; A row id the registry does not declare is refused).
  Verify: the usage block names the flag; `bash tools/sensor-selftest.sh --case run#selectsOnlyDueRows`
  still passes (the pre-existing selection semantics are unchanged).
  **Done 2026-10-18:** the flag accumulates into `rows_ids`, `rows_list` is the validation-and-selection
  input, and `run#selectsOnlyDueRows` (cadence alone, `--only` untouched) still passes.
- [x] 4.2 `tools/sensor-cases.sh`: `run#rowsSelectsSeveralRows` (a comma list, a repeated flag, a named row
  whose cadence is not due, a due row outside the list, a green exit for a list none of whose rows is due) and
  `run#rowsRefusesAnUndeclaredId` (exit 2, the id named, the declared ids named, nothing written) (spec: A
  step selects the rows it names; A row id the registry does not declare is refused).
  Verify: both cases pass and the whole sensor suite is 40 of 40 (was 4 of 40 red on HEAD).
  **Done 2026-10-18:** `PASS run#rowsSelectsSeveralRows`, `PASS run#rowsRefusesAnUndeclaredId`,
  `sensor-selftest: all 40 cases passed (JDK 27, offline, no token)`.
- [x] 4.3 Falsify the refusal: `tools/sensor-mutations.sh` gains mutation 8.10 (the known-id check's loop is
  emptied, so an undeclared id is accepted) (guidelines/Build.md §2).
  Verify: `bash tools/sensor-mutations.sh --only 8.10` reports "red under the mutation, green after the
  restore".
  **Done 2026-10-18:** `MUTATION 8.10: run#rowsRefusesAnUndeclaredId red under the mutation, green after the
  restore — a row a step names must be a row the registry declares`.

## 5. The pipeline runs two families of rows (#168, design D1/D4)

- [x] 5.1 `.github/workflows/sensors.yml`: the fetch step writes the conclusion to a step output; the single
  row step becomes two — the rows that measure the build (`build-duration,package-size,coverage`) gated on
  `ready == 'true' && conclusion == 'success'`, the condition and repository rows
  (`broken-main,pr-conflicts,hotspot,stale-citations`) gated on `ready` alone — and the credential comment is
  corrected to the scope task 2.1 homed (spec: backlog-sensors — A red build still runs the condition rows; A
  red build is not a failed check).
  Verify: `yq -e '.' .github/workflows/sensors.yml` parses; `yq -r '.jobs.sensors.steps[] | select(.run !=
  null) | .name'` lists the two row steps, and their `if:` values are the two gates above.
  **Done 2026-10-18:** the file parses and the step list is `Fetch this commit's build record` → `Run the rows
  that measure the build` (`ready == 'true' && conclusion == 'success'`) → `Run the condition rows and the
  repository rows` (`ready == 'true'`) → `Report the rows`; no `run.sh --due` invocation without `--rows`
  remains.
- [x] 5.2 `tools/sensor-cases.sh`: `workflow#aRedRecordRunsNoRecordRow` — the two steps' gates and lists read
  out of the file, the lists proven to be exactly the registry (once each), no `--due` invocation without
  `--rows`, and the pipeline's own command lines driven against a red build's record in a throwaway tree
  (exit 0 with `broken-main`'s verdict; the record rows' exit 8 kept as the reason the gate exists) (spec: A
  red build still runs the condition rows; A red build is not a failed check).
  Verify: the case passes, and `workflow#onlyTwoTriggers` — whose gate count moves from three to four — passes
  with the new count named (spec: the labels, the repair and both row steps wait for the record).
  **Done 2026-10-18:** `PASS workflow#aRedRecordRunsNoRecordRow`; `workflow#onlyTwoTriggers` asserts the four
  `if: steps.record.outputs.ready == 'true'` gates (the labels, the citation repair, both row steps).
- [x] 5.3 Falsify the gate: `tools/sensor-mutations.sh` gains mutation 8.11 (`==` becomes `!=` in the
  conclusion gate, so the build's rows run for a red record) (guidelines/Build.md §2).
  Verify: `bash tools/sensor-mutations.sh --only 8.11` reports "red under the mutation, green after the
  restore", and the full set is 11 behaved / 0 did not.
  **Done 2026-10-18:** `MUTATION 8.11: workflow#aRedRecordRunsNoRecordRow red under the mutation, green after
  the restore — the record rows wait for a build that succeeded`; the full run `sensor-mutations: 11 behaved,
  0 did not`.

## 6. The spec delta and its validation

- [x] 6.1 `specs/backlog-sensors/spec.md`: three MODIFIED requirements — the shipped description and every
  shipped scenario copied whole, plus the four added scenarios, each naming the case that exercises it (spec:
  backlog-sensors; design D1–D4).
  Verify: the scenario counts move 5 → 6 (the three outcomes), 6 → 10 (the pipeline), 5 → 6 (the record), and
  no shipped scenario is dropped.
  **Done 2026-10-18:** the delta is a superset of the three shipped requirements; the added scenarios are "A
  red build is not a failed check", "The credential's scope covers every declared row", "A red build still
  runs the condition rows", "A step selects the rows it names", "A row id the registry does not declare is
  refused" and "The fetcher reports the run's conclusion", each with its `- **Case**:` line.
- [x] 6.2 `openspec validate fix-sensor-credential-and-red-build-rows --type change` must exit 0.
  Verify: run the command; record its output here.
  **Done 2026-10-18:** `Change 'fix-sensor-credential-and-red-build-rows' is valid`, `exit=0` (`openspec
  validate fix-sensor-credential-and-red-build-rows --type change`), with no warning of its own; after the sync
  also `Specification 'backlog-sensors' is valid` and `openspec validate --specs` → `Totals: 175 passed, 0
  failed (175 items)` (the `>500 characters` warnings `--specs` prints belong to other, pre-existing specs).

## 7. Verify the whole change (no compiled file: the suites are the gate)

- [x] 7.1 Both owning suites green: `bash tools/sensor-selftest.sh` (40 cases) and
  `bash tools/build-inputs-selftest.sh` (11 cases), offline, no token, no network
  (guidelines/Build.md §2, §6; `Design.md` §11).
  Verify: the tallies, quoted with the command and the timestamp, in `evidence/green.log`.
  **Done 2026-10-18:** `sensor-selftest: all 40 cases passed (JDK 27, offline, no token)`,
  `11 cases, 0 failed`.
- [x] 7.2 Both mutation harnesses: `bash tools/sensor-mutations.sh` (11) and
  `bash tools/build-inputs-mutations.sh` (13) — every new bound falsified, every case green after the
  restore.
  Verify: "11 behaved, 0 did not" and "13 behaved, 0 did not" in `evidence/green.log`.
  **Done 2026-10-18:** both harnesses report no failure; the new mutations 8.10, 8.11 and 5.13 are among them.
- [x] 7.3 The structural checks of the touched surface: `bash tools/check-portability.sh` (the engine files
  name no project value — the two lists live in the workflow, not in `tools/sensors/**`),
  `bash tools/check-doc-routes.sh`, `bash tools/build-inputs.sh --check`, and the CI hygiene step's
  replication for `openspec/**` (`guidelines/Build.md` §2, scope block).
  Verify: each command's own verdict in `evidence/green.log`.
  **Done 2026-10-18:** `check-portability: OK — 42 engines and 2 templates name no project value (133
  value(s) read from the project)`; `check-doc-routes: all 89 section references resolve`;
  `build-inputs: every declared input has a producer (4 rows checked)`; `hygiene fail=0`.
- [x] 7.4 No Gradle run, deliberately: this change touches no compiled file (no Kotlin, no C++, no Gradle
  logic, no resource), so the gate's build and unit-test steps have nothing of this change to fail —
  `guidelines/Build.md` §2, scope block. Named here so the exclusion is a decision, not an oversight.
  Verify: `git diff --name-only` names only `*.sh`, `*.md`, `*.yml` and `CODEOWNERS`.
  **Done 2026-10-18:** the diff's file list is one `CODEOWNERS`, three workflow/doc files, one `*.yml` under
  `.github/`, and six `tools/**/*.sh`; no compiled file.

## 8. Archive, commit, close

- [x] 8.1 Archive the change after the suites are green: sync the delta into
  `openspec/specs/backlog-sensors/spec.md` and move the change to
  `openspec/changes/archive/<date>-fix-sensor-credential-and-red-build-rows/` (the
  `openspec-archive-change` skill).
  Verify: `openspec validate --specs` is clean and the archived path exists; `openspec list --json` no longer
  shows the change as active.
  **Done 2026-10-18:** the delta was merged into `openspec/specs/backlog-sensors/spec.md` (each of the three
  requirement blocks byte-identical to its delta block, scenarios 36 → 42, no shipped scenario dropped and
  only the two description lines the delta replaces removed), `openspec validate backlog-sensors --type spec`
  and `openspec validate --specs` (175/0) and `--changes` (15/0) are clean, and the change moved to
  `openspec/changes/archive/2026-10-18-fix-sensor-credential-and-red-build-rows/` (moved, not copied).
- [x] 8.2 One commit for the whole change: `fix: fix-sensor-credential-and-red-build-rows (#160, #168)`.
  Verify: `git status --porcelain` is clean afterwards (the untracked
  `Agentisches-Coding-NaviVeylin.md` is not this change's and is left alone).
  **Done 2026-10-18:** one commit, `fix: fix-sensor-credential-and-red-build-rows (#160, #168)`, carrying the
  code, the cases, both documents, the archived change (its `evidence/` and the two closure reason files
  included) and the synced main spec; the tree is clean but for the pre-existing untracked
  `Agentisches-Coding-NaviVeylin.md`, which is not this change's and was left alone.
- [x] 8.3 Close what landed: #160 and #168 get `status/fixed-by` and a closure naming the archived change,
  with the reason file under the archived change's `evidence/` (the `close-issue` and `update-issue` skills).
  Verify: `tools/issues/list.sh --id 160` / `--id 168` report the state and the label; the reason file's path
  is cited in the closure comment.
  **Done 2026-10-18:** `evidence/issue-160-close.md` and `evidence/issue-168-close.md` carry the reasons,
  their evidence and the closure runs. #160: `update.sh 160 --status fixed-by` then `close.sh 160` →
  `✓ Closed issue Framstag/NaviVeylin#160`, state CLOSED/completed, label `status/fixed-by`.
  #168: `update.sh 168 --status fixed-by` (`exit=0`) then `close.sh 168` → `missing-eligibility-label:
  agent/eligible absent`, `refusing to close #168 — the gate did not pass, so nothing was posted`, `exit=3`:
  the item does not carry the automation opt-in, which is reserved and only a person may apply, so the
  closure is the owner's step. The same reason and evidence were posted as the item's comment with
  `update.sh 168 --comment-file` (`exit=0`), and the item carries `status/fixed-by` and stays open. This is
  the tooling's own refusal, not a fault, and it is recorded rather than worked around.
