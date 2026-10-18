# Proposal — fix-sensor-credential-and-red-build-rows

## Why

Two defects of the same pipeline, both measured on `main` (`bf6f5d7`, 2026-10-18). The first is a claim that
cannot hold; the second is a pipeline that goes red for a reason that says nothing about the checks.

**Defect 1 (#160, a document that contradicts the mechanism).** `.github/CODEOWNERS` says the sensor
pipeline's token can do "`Issues: write` only", and `guidelines/Backlog.md` §9 says "holding issue-write and
nothing else". The registry's `pr-conflicts` row calls `gh pr list --state open --limit 100 --json
number,mergeable,author` (`tools/sensors/conditions/pr-conflicts.sh`), and GitHub's REST documentation puts
"List pull requests" behind the fine-grained permission **Pull requests: Read**. A secret created exactly as
documented therefore reports that row as `error` on every fire — `error` posts nothing and makes the run exit
8, so the schedule is red half a day rather than silent. The owner has taken the decision (2026-10-17): the
repository secret is created with **Issues: Read and write** plus **Pull requests: Read**, its repository
access limited to this repository, owned by an allowlisted author; the registry is unchanged, so every
declared row can run. Correct the two live claims, and give the required scope one live home in
`guidelines/Backlog.md` §9, which already owns the checks' doctrine and states the identity rule. The
archived change's `report.md` and `design.md` are history and stay untouched.

**Defect 2 (#168, a red build reddens the metric rows).**

**Evidence (the defect's own measured command, 2026-10-18 on HEAD `bf6f5d7`, saved in
`evidence/red-on-head.log`):** a tree whose build record is the partial one a red build publishes — the
result record alone, `{"job":"build","case":"apk","result":"failure"}`:

```
$ bash tools/sensors/run.sh --due --now 2026-10-18T00:00:00Z
broken-main: ... verdict=violation measurement=failure ... action=filed                       exit=0
$ bash tools/sensors/run.sh --due --now 2026-10-18T03:00:00Z
build-duration: fingerprint=- verdict=error measurement=error-no-timing-record issue=- action=posted nothing, the run fails
sensors: 1 finding(s) reported an error — nothing was written for them and the run fails          exit=8
$ bash tools/sensors/run.sh --due --now 2026-10-18T04:00:00Z
package-size: ... verdict=error measurement=error-no-package-artifact ...                        exit=8
$ bash tools/sensors/run.sh --due --now 2026-10-18T06:00:00Z
coverage: ... verdict=error measurement=error-no-coverage-report ...                             exit=8
```

**Root cause (one).** `tools/fetch-build-inputs.sh` asks for a *completed* run, not a *successful* one, and
reports nothing about how the run ended, so the pipeline cannot tell the partial record a red build
publishes (`tools/build-inputs.sh --write` writes the result record unconditionally and exits 1 when the
coverage summary, the package or the timing record are missing — by design, `wire-sensor-inputs` D10) from a
complete one — and `sensors.yml` issues one `run.sh --due`, which attempts every due row. A red build cannot
produce the coverage summary (Kover's report task is skipped once its tests fail), the package (if it fails
before the assemble step) or the timing record, so on those three ticks every row that reads one of them
reports the third outcome, nothing is written to the tracker, and the run exits 8: four red ticks a day whose
redness says nothing about the checks, exactly while a size or duration regression is most likely to be
introduced. The condition rows still work from the partial record (`broken-main` reads it correctly, as the
measurement above shows) — the defect is that the pipeline attempts the other rows at all.

**The single resulting fix (mechanism (a) of the issue, measured).** The fetcher reports the conclusion the
build's run ended with beside the record (`--conclusion-file`), `sensors.yml` runs the rows that measure the
build only for a `success` conclusion while always running the condition and repository rows, and
`tools/sensors/run.sh` learns to select several rows (`--rows <id,id>`, repeatable) so one step runs one
family of rows. "The build is red" stays exactly one row's verdict (`broken-main`), and no row reports a
failure a person would have to read as "the build was red, the check was fine".

**The requirement the defect contradicts.** `backlog-sensors` states the pipeline is "given write access to
issues only" (defect 1) and that "a run reads the record of the commit it examines … so its verdict is the
measurement rather than a failed check" (defect 2, whose *failing check* the shipped text accepted as the
price of a red build). Both requirements are modified rather than rewritten; the red-build scenarios are
added to the requirements that already own the third outcome, the two triggers and the record.

**Fix the case, not only the document.** Four new cases in `tools/sensor-cases.sh` (`run#rowsSelectsSeveralRows`,
`run#rowsRefusesAnUndeclaredId`, `workflow#aRedRecordRunsNoRecordRow`,
`sensors#theDocumentedCredentialCoversEveryDeclaredRow`) and one in `tools/build-inputs-cases.sh`
(`inputs#reportsTheRunsConclusion`), plus two mutations in `tools/sensor-mutations.sh` (the new refusal, the
new gate) and one in `tools/build-inputs-mutations.sh` (the reported conclusion). The credential case reads
the scope out of §9 and the `gh` verbs out of the rows' own commands, so a row added later cannot outgrow the
secret; the red-build case reads the workflow's two gates and its two row lists, proves the lists are exactly
the registry, and then drives the pipeline's own commands against a red build's record.

## What Changes

- **`tools/fetch-build-inputs.sh`** — a `--conclusion-file <path>` flag: the conclusion the chosen run ended
  with is written beside the record, and nothing is written when no record was fetched. Its exit contract is
  unchanged: 0 fetched, 3 not ready, 1 cannot tell.
- **`tools/sensors/run.sh`** — `--rows <id,id>` (comma-separated and repeatable) restricts the candidates to
  the rows it names; each named row's own cadence still decides whether it runs, and an id the registry does
  not declare is refused before any row runs. `--only <id>` keeps its meaning and is the same restriction for
  one id.
- **`.github/workflows/sensors.yml`** — the fetch step writes the conclusion to a step output, and the single
  row step becomes two: the rows that measure the build (`build-duration,package-size,coverage`) gated on
  `conclusion == 'success'`, and the condition and repository rows
  (`broken-main,pr-conflicts,hotspot,stale-citations`) gated on the record alone. Both lists together are
  exactly the registry — proven by the new case, not by the file's prose.
- **`guidelines/Backlog.md` §9** — the credential's permissions replace "issue-write and nothing else" (one
  live home for the scope defect 1 found undocumented), and the red-build paragraph now states what the
  pipeline does instead of reporting a failed check for a red build.
- **`.github/CODEOWNERS`** — the live claim about the token's scope corrected in the same breath.
- **`backlog-sensors`** — three requirements modified, each keeping its shipped text and scenarios: the third
  outcome gains "A red build is not a failed check"; the pipeline requirement gains the credential's scope,
  "A red build still runs the condition rows" and "A step selects the rows it names"; the record requirement
  gains "The fetcher reports the run's conclusion".
- **Additive, no breaking change.** No compiled file, no Kotlin, no C++, no Gradle logic, no manifest, no
  resource. The pipeline's behaviour changes only for a record a build did not complete successfully, where
  the previous behaviour was a red run and nothing written. Rollback path: revert this change's commit — the
  fetcher's flag and `--rows` disappear, the two row steps collapse into `run.sh --due`, and §9 and
  CODEOWNERS return to the issues-only claim.
- **Not a native/JNI change**: no submodule patch, no `:osmscout-client-java` override. No compiled file at
  all, so no Gradle build and no test suite is owed (`guidelines/Build.md` §2, scope block); the verification
  is the two offline shell suites, their mutation harnesses and the structural checks named in `tasks.md`.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `backlog-sensors`: three requirements modified (the three outcomes, the pipeline's own use and its
  least-privileged credential, the row's declared input and the record's transfer) with four added scenarios
  and no shipped scenario removed. The delta lives at `specs/backlog-sensors/spec.md`.

## Impact

Tooling (the checks' engine and its pipeline)
- `tools/fetch-build-inputs.sh` — `--conclusion-file`, the run's conclusion in the `gh run list` projection
- `tools/sensors/run.sh` — `--rows <id,id>`, its refusal, and the selection it feeds
- `.github/workflows/sensors.yml` — the conclusion step output, the two row steps and their gates, the
  corrected credential comment
- `.github/CODEOWNERS` — the corrected scope claim (the file that guards a workflow change)

Cases and their harnesses
- `tools/sensor-cases.sh` — four cases (row selection, the undeclared id, the red record, the credential)
- `tools/build-inputs-cases.sh` — one case (the run's conclusion)
- `tools/sensor-mutations.sh` — mutations 8.10 and 8.11; `tools/build-inputs-mutations.sh` — mutation 5.13

Docs and specs
- `guidelines/Backlog.md` — §9 (the credential's scope, the red build's paragraph)
- `openspec/specs/backlog-sensors/spec.md` — gains the modified requirements when this change archives (the
  delta lives at `specs/backlog-sensors/spec.md`)

Structural checks (no compiled file, no Gradle)
- `bash tools/sensor-selftest.sh` — the sensor tooling's own gate (40 cases)
- `bash tools/build-inputs-selftest.sh` — the publisher's and the fetcher's gate (11 cases)
- `bash tools/sensor-mutations.sh` (11 mutations) and `bash tools/build-inputs-mutations.sh` (13 mutations) —
  the change-time falsification of every bound it adds
- `bash tools/check-portability.sh`, `bash tools/check-doc-routes.sh`, `bash tools/build-inputs.sh --check`,
  `yq -e '.' .github/workflows/sensors.yml`, the CI hygiene step's replication
- Not run, deliberately: no Gradle build or test suite (no compiled file is touched;
  `guidelines/Build.md` §2, scope block), and no live `workflow_dispatch` of `sensors.yml` (its guard refuses
  every ref but `main`, so the dispatch that would exercise the two steps end to end is owed after the merge —
  recorded in `tasks.md` and the report)
