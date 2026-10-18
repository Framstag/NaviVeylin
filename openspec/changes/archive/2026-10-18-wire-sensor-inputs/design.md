# Design — wire-sensor-inputs

Scope: `.github/workflows/{build,sensors}.yml`, `tools/build-inputs.sh`, `tools/fetch-build-inputs.sh`,
their cases/harness/mutations, `tools/sensors/sensors.json`, `guidelines/Backlog.md`. No Kotlin, C++ or
Gradle build-logic change, so no Gradle gate is owed (`guidelines/Build.md` §2, scope block): the
verification is the shell gates plus the two scripts' own cases.

Threading/lifecycle: none. These are CI shell steps; nothing in the app runs them.

## D1 — How the inputs reach the checks' run

| option | cost |
|---|---|
| **(chosen)** the build pipeline publishes one artifact containing the declared paths; the checks' run downloads it for the commit it examines | one upload step in `build.yml`, one fetch step in `sensors.yml`; no new credential scope; the record is one artifact whose content is the paths themselves |
| `gh run download` for each row, with `Actions: read` added to the owner's `SENSORS_TOKEN` (issue #161's candidate (a)) | extends a documented personal-token scope (#160 already records an omission there), so a wider credential sits in the pipeline that writes to the tracker |
| every row queries the API instead of reading a file | impossible for size, coverage and timings — they are measurements of a build, not API fields; and it would replace four declared rows with four bespoke scripts, bypassing "a check is a declared row" |

**Chosen**: (1). The transfer is one artifact and one download, the rows keep declaring file paths, and the
token that writes to the tracker gains nothing.

**Risk**: a `push`ed commit whose `build.yml` run has not finished (or has failed before the publish step)
leaves the record absent. D5 makes that a skip, not an error, and the fetch is keyed on the commit.

## D2 — The record's shape: the declared paths, not a schema

| option | cost |
|---|---|
| **(chosen)** the artifact contains the tree the rows declare (`build/gate-timings.json`, `app/build/gate-result.json`, `app/build/reports/jacoco/coverage.json`, the package) and is unpacked at the repository root | no engine change, no row change, no merge/split logic; a new row's input is satisfied by the publisher writing that path |
| one `sensor-inputs.json`, with the rows reading fields from it | rewrites four row commands, their fixtures and their cases, and makes the engine's `input` a pointer into a document rather than a file |

**Chosen**: (1) — the registry stays the single declaration of where a measurement lives.

**Risk**: the artifact's size carries the package. Accepted: the debug APK is ~tens of MB, uploaded once
per build run on `main`, and GitHub's artifact storage is the same place the debug APK already goes.

## D3 — Which identity downloads the record

**Chosen**: `actions: read` added to the *job's* `permissions:` block, i.e. the ephemeral
`${{ github.token }}` reads artifacts and nothing else, while `SENSORS_TOKEN` — the identity that files,
comments and closes — keeps exactly its issue-write scope.

**Alternative**: the PAT (D1's option 2), rejected above. **Alternative**: no fetch by the sensor pipeline
at all, with the *build* pipeline filing the rows' issues, rejected: it hands the tracker credential to the
pipeline that runs contributor code.

**Risk**: the spec's "given write access to issues only" stays true — `actions: read` is a read, and the
job's write scope is unchanged. The workflow diff is owner-reviewed (CODEOWNERS).

## D4 — What `package-size` measures

| option | cost |
|---|---|
| **(chosen)** the debug APK `build.yml` already assembles, with the row's `input` moved to
  `app/build/outputs/apk/mobile/debug/app-mobile-debug.apk` | the number moves with what CI builds; it is a proxy for the shipped package, not the shipped package |
| assemble `:app:assembleMobileRelease` in CI as well | a second full native release build on every push to `main` for one number; the release APK is unsigned there and still not the Play artifact |
| have the release pipeline publish the shipped AAB/APK for this row | the release pipeline runs only when the owner releases, so the row is unmeasured for most of a release cycle |

**Chosen**: (1). The row's charge is "the package did not grow past its bound"; a proxy that moves with
every code change answers that, at no extra build cost. Reversible in one line (registry `input`) when a
release pipeline publishes the shipped artifact, and the deviation is recorded in the row's own
fingerprint field `artifact` staying `app-mobile-debug.apk`.

**Risk**: debug-only growth (symbols, debug assertions) can trip the bound while the shipped size is flat.
Accepted for now; the measurement is recorded on the issue, so the first tripped bound shows which one it
is.

## D5 — What happens when the record is not there yet

**Chosen**: `tools/fetch-build-inputs.sh` exits **3** when no completed run for that commit carries the
artifact; `sensors.yml` then skips the rows step (`if: … == 'true'`) and still runs the report step. The
run is green, writes nothing, and says the record is not there.

**Alternative**: let the rows run and report `error` (today's behaviour) — that is the red hourly run the
owner turned the schedule off to stop. **Alternative**: wait in the workflow for the build — couples the two
pipelines' runtimes and can still time out.

**Risk**: a permanently missing record would silently stop the checks. The report step always runs and
prints that no row was selected, and a *dispatch* is the manual probe — the same signal the archived design
already chose for "a silently disabled schedule is visible on the next manual dispatch".

## D6 — Where the bound lives

**Chosen**: in the publisher itself (`tools/build-inputs.sh --check`), run by `build.yml` right before the
upload, so the same file that knows the paths it writes refuses a registry that declares another. The
manifest is derived from the publisher's own writing code (`--manifest`), never a second list.

**Alternatives**: a separate check script owning a second list of paths — drifts, which is the defect;
nothing at all — a row declares a path no pipeline writes and only a scheduled run notices.

**Risk**: the bound is checked in the build pipeline, so a registry edited in a branch is only refused once
that branch's build runs — which is one run earlier than today's hourly red, and in the pipeline whose
artifact the declaration is about.

## D7 — The schedule's tick

**Chosen**: one `schedule` cron, `0,30 * * * *`, and the registry's rows keep their declared minutes
(`0` hourly, `30 */4`). The engine matches a row's five-field cron against the run's minute exactly
(`tools/sensors/lib.sh`, `sensors_cron_due`), so the tick's minutes must contain every minute a row
declares.

**Alternative**: a tick per row — six `schedule:` entries, six workflow runs, each selecting one row; more
runs and more concurrency churn for the same measurements. **Alternative**: an hourly tick only — the
`pr-conflicts` row's `30 */4` would never match.

**Risk**: GitHub may fire a scheduled tick a few minutes late; the engine's exact-minute match then selects
nothing for that tick, and the row's next matching minute is up to a cadence away. Filed as its own issue
(new, `class/bug`) rather than folded in: fixing it changes the engine's selection semantics, which this
change does not touch.

## D8 — The build result record's fields

`broken-main` reads `.job`, `.case`, `.result`. The publisher writes:

- `result: success` when every test-result XML is clean **and** the debug APK exists;
- `result: failure`, `job: test`, `case: <FQCN.method>` from the first failing or erroring case when a test
  XML carries one — the finest identity that pipeline produces;
- `result: failure`, `job: build`, `case: apk` when the APK is missing, which is what a failure *before*
  the test step looks like from inside the workspace.

**Alternative**: have the workflow pass each step's `outcome` into the publisher — more faithful to the
step names, but it hard-codes the step list into the workflow *and* the script, and a step renamed in the
YAML silently stops being named. **Alternative**: parse the Angular/reporter output of `gh run view` —
needs the API and a run id for the run that is still executing.

**Risk**: a failure that produces neither a failing test nor a missing APK (a YAML-level step failure after
the APK exists, e.g. the SBOM step) reads as `success`. The verification task records this as the record's
stated limit.

## Decisions taken during apply

Their alternatives were eliminated by what the work measured, not by taste, so they are recorded here rather
than retrofitted into D1–D8.

### D9 — The fetcher's commit identity is enforced in the fetcher, not by `gh --commit`

`gh run list --commit <sha>` filters server-side, and with it the fetcher's own head-SHA comparison was
unreachable: the mutation harness proved it (a mutation of the comparison left the case green, because the
stub `gh` — faithful to the flag — had already filtered). The comparison is therefore the *only* mechanism
(the `--commit` argument is gone), the stub needs no fiction, and mutation 5.5 now fails its case. The
narrowing that matters (`--workflow`, `--status completed`, `--limit 30`) stays.

### D10 — A red build's record is partial, and the result record is written first

A failing test task makes Kover's report task skip, so `app/build/reports/jacoco/coverage.json` cannot exist
on a red build — measured on the host, and the reason `--write` writes the result record **before** it
reports what it could not write. The publish step therefore fails loudly on a red build (naming each
missing input and what would have produced it) while the upload step, `if: always()`, still carries the
result record, so `broken-main` files the regression. The consequence is accepted and deliberate: on a red
build the `coverage` row reports a failed check, which is what "the check could not reach its input" means,
and the run exits 8 — with main red, a red check run is not noise, and the next green run measures again.

### D11 — A condition row with nothing to report says so

**Chosen**: `broken-main` reports a clear for the build's own identity when the build is green and no
condition is recorded; `pr-conflicts` reports a clear for its own identity when no conflict of the owner's
is open. Neither lands on an issue, so nothing is closed and the finding is a statement, not a state change.

**Alternatives**: (a) leave the rows silent and let the runner's "printed no finding" rule pass — rejected:
the three-outcome contract says a check's run ends in exactly one outcome, and a check that has *not* run and
a check with *nothing to report* would then be the same blank; (b) change the runner to read an empty output
from an exit-0 row as "nothing to report" — rejected: it removes the only guard against a row script that
dies before printing, which is precisely the failure the rule exists for, and it would need a per-row kind
field to stay honest; (c) emit the clear only in a wrapper — rejected as an indirection for two rows.

**Risk**: the two rows now quote an identity (`pr: none`, this build's job/case) that no issue can carry, so
a human reading `fingerprint=… issue=-` sees a clear with nothing to clear. That is the same shape the
metric rows already produce on their first run, and the message states it in words.

### D12 — The coverage percentage is the report's own counter, parsed from the document

The first implementation read the XML line by line and took the first `<counter type="INSTRUCTION">` on a
matching line. Gradle's JaCoCo report is a **single line**, so that class counter (`missed=0 covered=12`)
became `osmscout-client-java: 100.0` where the report's own counter says **25.7** — and the 70 % floor then
reports a clear where it must report a violation. The fixture was pretty-printed, which is why no case saw
it (finding R1 of the independent review).

**Chosen**: strip newlines, take the *last* instruction counter (JaCoCo and Kover both put the report's own
counters after every package), then read `missed`/`covered` from that one element. The fixture emits the
minified shape for two modules and the pretty shape for the others, so both are proven; mutation 5.3 flips
`tail` to `head` and must fail the case.

**Alternatives**: keep the line-oriented parse and make the fixture match Kover only — rejected: the
pipeline produces both shapes, and a fixture that does not match the producer's real output is a case that
can never fail for the right reason; use an XML parser — none is available in this toolchain's dependency
set (`python3` is forbidden for output processing, `xmllint` is not guaranteed on the runner).

**Risk**: "the last counter is the report's own" is a property of the JaCoCo/Kover format rather than
googleable-from-the-file; a generator that emitted counters before the packages would break it silently.
Bounded by the two shapes in the fixture and by the file's own comment.

### D13 — The dispatch's `apply` input is compared as the boolean it is declared to be

`inputs.apply == 'true'` is **false for every value** of a `type: boolean` input: the runner coerces the
boolean to a number and the string `'true'` to `NaN`, so a manual dispatch could never write (finding R2).
**Chosen**: `inputs.apply == true`, with the schedule term keeping unattended runs writing.
**Alternative**: compare `github.event.inputs.apply == 'true'` — works, but keeps the string form that
invites the same bug again; **alternative**: a second job-level `if:` per step — same expression, more YAML.
**Risk**: none beyond the expression itself; the case asserts both directions (the boolean comparison present,
the string comparison absent) and mutation 5.12 breaks it.

### D14 — Everything that writes to the tracker waits for the record

The `ready` gate was on the rows only, so a scheduled run with no record still created labels and ran the
citation repair — contradicting the spec scenario "no record yet … nothing is written to the tracker"
(finding R3). **Chosen**: the same gate on the labels step, the repair step and the rows step, asserted as a
count of three (mutation 5.11). **Alternative**: narrow the scenario's wording — rejected: a run that writes
anything to the tracker while it cannot measure anything is the state the interim decision existed to avoid.
**Risk**: a tick whose build has not finished also postpones the citation repair by one cadence; the repair
is idempotent and bounded, and the next tick with a record does it.

### D15 — The manifest is the union of what the script writes and what the pipeline produces

Finding R6: one flat `printf` of four paths looked like a second list. **Chosen**: `written_paths` and
`produced_paths` named separately, the manifest their union, and `--write` asserts that every published path
exists once the writers have run — so a manifest entry nothing writes fails the write, and a writer's path
missing from the manifest is a loud refusal in `--check`. **Alternative**: record each write as it happens —
the manifest is also read by `--check`, which writes nothing, so it would need a second code path for the
same list.

## Verification

- `bash tools/build-inputs-selftest.sh` — 10 cases, all green on the host: the publisher on a throwaway tree
  (every declared input written; a declared input with no producer refused and named; the coverage conversion
  from fixture XMLs; the result record's three shapes; a red build still carrying the result record) and the
  fetcher (stub `gh`: the declared tree restored, another commit's newest run not used, exit 3 when nothing is
  built yet).
- `bash tools/build-inputs-mutations.sh` — 10 mutations, each applied to the shipped script, the named case
  red, restored, the case green again: the manifest, the refusal, the coverage counter, the result record,
  the commit identity, the exit-3 path, the tick minutes, the workflow's own token, and the two
  nothing-to-report clears.
- `bash tools/sensor-selftest.sh` — 36 cases, all green: the neighbouring suite, including the two new cases
  for the condition rows' clears and the narrowed token rule.
- The host row matrix, with the record built from this tree's own build outputs
  (`tools/build-inputs.sh --write`, the release APK copied to the debug path as a stand-in): every selected
  row reports a verdict and none reports `error` — `build-duration` clear 293842 ms, `package-size` clear
  33421503 B, `coverage` clear 77.2 %, `broken-main` clear success, `pr-conflicts` clear none, `hotspot`
  violation 5902008 B (a real finding of this tree, dry-run).
- `bash tools/check-portability.sh` (42 engines, no project value) and `bash tools/check-doc-routes.sh`
  (89 references) — the new scripts are project-side and the sensor engine still names nothing of this
  project.
- YAML: both workflow files parse, and `sensors.yml`'s trigger set is `schedule` + `workflow_dispatch`.
- The end-to-end record (#161's owed verification) is a `sensors.yml` dispatch whose selected rows all
  report `violation`/`clear` and none report `error`, recorded in the change's `report.md` — it cannot run
  before the workflow is on the default branch.
