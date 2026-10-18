# Design — fix-sensor-credential-and-red-build-rows

## Context

Motivation, the root cause and both measurements are in `proposal.md` (`evidence/red-on-head.log`). The state
this design works within, read from the tree at `bf6f5d7` (2026-10-18):

- `tools/fetch-build-inputs.sh` picks the newest **completed** `build.yml` run for a commit
  (`gh run list --status completed --json databaseId,headSha`) and reports nothing else about it; its exit
  contract is 0 fetched / 3 not ready / 1 cannot tell, and the pipeline's fetch step turns exit 3 into
  `ready=false`.
- `tools/build-inputs.sh --write` writes `app/build/gate-result.json` unconditionally and fails loudly when
  the coverage summary, the package or the timing record are missing — the accepted risk of
  `wire-sensor-inputs` D10, whose cost was measured by issue #168 as four red ticks a day.
- `.github/workflows/sensors.yml` issues exactly one `run.sh --due` for every due row, gated only on
  `steps.record.outputs.ready == 'true'` (asserted by `workflow#onlyTwoTriggers`, which counts three such
  gates: the labels, the citation repair and the rows).
- `tools/sensors/run.sh` selects by cadence (`--due`) or by exactly one id (`--only`); the registry
  (`tools/sensors/sensors.json`) declares seven rows, three of which read a build output the timing record,
  the package and the coverage summary — alongside `broken-main`, which reads the result record the publisher
  always writes, and `pr-conflicts`, `hotspot` and `stale-citations`, which read the API, the repository and
  the tracker. `broken-main`'s fingerprint is `(job, case)`: the *failing test case*, which only
  `app/build/gate-result.json` carries.
- `tools/sensors/conditions/pr-conflicts.sh` calls `gh pr list`, which GitHub's REST documentation puts behind
  **Pull requests: Read**; the live claims that said otherwise are `.github/CODEOWNERS` line 3 and
  `guidelines/Backlog.md` §9 ("holding issue-write and nothing else"). §9 owns the checks' doctrine, states
  the identity rule and is where a person reads how the pipeline is set up.
- The suites that own these files: `tools/sensor-selftest.sh` (40 cases) and `tools/build-inputs-selftest.sh`
  (11 cases), both offline against the stub `gh`, plus the two mutation harnesses. No Gradle, no token, no
  network.

## Goals / Non-Goals

**Goals**

- A red build stops the rows that cannot be measured for it, while the build's redness keeps reaching the
  tracker as exactly one check's verdict.
- The permission the pipeline's credential must be created with has one live home, and the `gh` verbs the
  declared rows call are inside it — proven by a case that reads the rows' own commands.
- One row selection mechanism, exercised by a case per selection: several ids, a due subset, and an id the
  registry does not declare.

**Non-Goals**

- Changing the registry's rows, its bounds or its cadences: the fix is which rows a step runs, not which
  checks exist.
- Making a metric row tolerant of a missing input: the third outcome is the contract that keeps a broken
  check from reading as a cleared condition.
- Touching the archived `wire-sensor-inputs` artifacts, or re-opening its D10: this change supersedes the
  *cost* it accepted, and records that here rather than editing history.
- A new CI dependency or a new guard binary: the guards are the two suites, their mutation harnesses and the
  cases the change adds.

## Decisions

### D1 — The pipeline gates the rows on the run's conclusion; the fetcher reports it (mechanism (a) of #168)

`tools/fetch-build-inputs.sh` gains `--conclusion-file <path>`: the conclusion of the run it chose and
downloaded, written last, so a caller that reads it knows the record it belongs to is in the tree, and no
file when no record was fetched. `sensors.yml` captures it into the fetch step's output (`ready`,
`conclusion`) and runs two row steps instead of one: the rows that measure the build
(`build-duration,package-size,coverage`) gated on `ready == 'true' && conclusion == 'success'`, and the
condition and repository rows (`broken-main,pr-conflicts,hotspot,stale-citations`) gated on `ready` alone.
The exit contract of the fetcher is untouched.

- *Alternatives the evidence eliminated*:
  (a) **Mechanism (b) of #168 — `broken-main` becomes an API row and the fetcher treats a failed build as
  not-ready (exit 3)** — impossible: the API names a run, not the failing *case*, and `broken-main`'s
  fingerprint is `(job, case)`, the very thing `sensors#brokenMainFingerprintDistinguishesCauses` and
  the spec's "A different cause under the same check" depend on; and an exit-3 skip selects **no row at
  all**, so during a red period nothing would be filed — the one signal a red build must produce
  (`broken-main` reads it correctly today, measured in `evidence/red-on-head.log` at 00:00).
  (b) **Make the metric rows report a clear (or no finding) when their input is missing** — impossible: a row
  that cannot read its input has determined nothing, and "Failing is not clearing" is the reason the third
  outcome exists; the mutation that falsifies it is live (`8.4`,
  `run#errorIsNeverTreatedAsClear`), and a clear would *close* the metric's issue on a red build.
  (c) **Keep the token issues-only and drop the `pr-conflicts` row** — eliminated by the owner's decision of
  2026-10-17 (#160): the repository secret is created with Pull requests: Read, so the documentation is what
  is wrong, and the row is one of the two conditions the registry exists for.
- *Why this one*: it changes one projection and one gate per step, it keeps "the build is red" as exactly one
  row's verdict, and it adds no new engine concept — the rows a step names are a decision the file states.

### D2 — The credential's scope gets its live home in `guidelines/Backlog.md` §9

§9's sentence "holding issue-write and nothing else" becomes the credential's actual permissions — **Issues:
Read and write** and **Pull requests: Read**, this repository only, an allowlisted owner — and names *why* the
second one is there (the row that lists pull requests). `.github/CODEOWNERS` line 3, the other live claim, is
corrected to match and points at §9.

- *Alternatives the evidence eliminated*:
  (a) **A comment in `tools/sensors/sensors.json`** — invisible to the person creating a repository secret in
  the GitHub UI, and the registry's own comment is about the rows, not the credential; the reader who needs
  this fact is not reading the registry.
  (b) **A new document (or a section in `Build.md`)** — a second home for a fact §9 already states: §9 owns
  the checks' doctrine, the trigger cap, the identity rule and now the record, and this capability's own
  shipped requirement is the one that must change; `Build.md` §12 owns the native build.
  (c) **Leaving only `CODEOWNERS`** — that file states whose review a pipeline change needs; it made the false
  claim incidentally, and a permission list would live in the file with the least reason to hold it.
  (d) **Editing the archived `wire-sensor-inputs` `report.md` / `design.md` D2** — impossible: an archived
  change's artifacts are the record of that change; issue #160 states they stay as they are.
- *Why this one*: one home, in the section that owns the doctrine, next to the identity rule it belongs with,
  and the file that made the claim now agrees with it.

### D3 — Row selection is `--rows <id,id>` on the runner; the workflow states the two families

`tools/sensors/run.sh` accepts `--rows <id,id>` — comma-separated and repeatable — as a restriction of the
candidates: `--due` still decides whether each named row runs, `--only <id>` keeps its meaning (one id, run
whatever its cadence says), and an id the registry does not declare is refused before any row runs, naming the
ids it does declare. The workflow names the two lists, and `workflow#aRedRecordRunsNoRecordRow` proves that
their union is exactly the registry.

- *Alternatives the evidence eliminated*:
  (a) **One `--only` invocation per row** — each invocation re-resolves the project, re-checks the identity
  and re-runs the preflight; four steps per family instead of one, for no gain.
  (b) **Rely on the cadence alone (no selection)** — impossible: the ticks overlap (the conditions' even hours
  against 03:00, 04:00, 06:00 and the weekly rows), so cadence cannot express "not while the build is red" —
  that is exactly how the defect happened.
  (c) **A registry field naming each row's family, with the engine skipping the ones a red record cannot
  serve** — this moves a *pipeline* decision into the engine's data and needs an engine predicate for it (the
  engine would have to be told the build's state at run time), and the workflow would still have to name the
  gate; it is a registry-schema change outside the direction this change was given, and it would give the
  same drift a second, less visible home than the file the case reads.
- *Why this one*: the selection mechanism is small, testable offline, and reused by a `--only` that already
  existed; the two lists are proven to be the registry by a case, so the duplication cannot drift silently.

### D4 — The red-build rule is proven by the pipeline's own commands, offline

`workflow#aRedRecordRunsNoRecordRow` reads the two row steps, their gates and their lists out of
`.github/workflows/sensors.yml` (a `--due` invocation without `--rows` fails the case, so a step can never go
back to attempting every due row), compares the two lists against the registry (exactly once each), copies
`tools/` into a throwaway git tree that carries the record a red build publishes, and then runs the pipeline's
own command lines: the other rows exit 0 with `broken-main`'s verdict, and the record rows — which the
pipeline does not issue — exit 8, the defect's own measurement kept as the reason the gate exists.

- *Alternatives the evidence eliminated*:
  (a) **Asserting the workflow's text only** — impossible to build tonight as *the* proof: a YAML string is
  the gate's shape, not its effect, and the case would pass while the engine refused `--rows`; the case does
  both, in one reading.
  (b) **Driving the fixture registry instead of the real one** — the fixtures' two sensors declare no input,
  so they cannot stand in for a row whose input a red build did not publish; the throwaway tree copies the
  repository's own `tools/` and registry, and the case then measures the two halves (exit 8 against exit 0).
  (c) **A live `workflow_dispatch`** — impossible from this branch: `tools/sensors/guard.sh` refuses every ref
  but the project's main branch (`guard#refusesOffMain`), so the dispatch is owed after the merge (tasks.md
  §7, "Owed").
- *Why this one*: it is the closest the offline gate can get to the CI path, it exercises the refusal and the
  selection of both suites, and it names the defect's measurement rather than describing it.

## Risks / Trade-offs

- *The row ids are stated in two places (the registry and `sensors.yml`)* → deliberate: the registry is the
  only home for which check exists, and the pipeline's two lists are what a case proves to be exactly that
  registry. A row added and left out of both steps fails `workflow#aRedRecordRunsNoRecordRow`.
- *A conclusion other than `success` (cancelled, timed_out, action_required) skips the rows that measure the
  build* → intended: any non-success run may publish a partial record; the condition rows still run, so the
  redness is visible, and the metric is measured on the next tick after a green build. The accepted cost of
  `wire-sensor-inputs` D10 was the opposite trade (measure the partial record) and is what #168 measured.
- *A long red period delays a metric beyond its cadence* → accepted: a metric measured off a partial record
  says nothing about the project (it reports `error`), so the delay costs no signal that exists.
- *`--conclusion-file` is an interface the workflow now depends on* → held by
  `inputs#reportsTheRunsConclusion` (both conclusions, and the exit-3 path that writes nothing) and by
  mutation `5.13` (a hardcoded conclusion turns it red).
- *The credential case reads prose (`guidelines/Backlog.md` §9)* → it asserts the two permission names and
  derives the required set from the rows' own `gh` verbs, so the false claim of #160 fails it today and a row
  that outgrows the secret fails it tomorrow.
- *Nothing here is a component with a lifecycle* → no threading model, no dispatcher, no main/background
  pair: this change is shell, a workflow file and two documents, and no Android API, manifest, resource or
  Gradle file is touched.

## Migration Plan

1. Land the change (the fetcher's flag, `--rows`, the two row steps, §9 and `CODEOWNERS`), with the two suites
   and both mutation harnesses green and `openspec validate fix-sensor-credential-and-red-build-rows --type
   change` at 0.
2. Archive: sync the delta into `openspec/specs/backlog-sensors/spec.md`, move the change under
   `openspec/changes/archive/<date>-fix-sensor-credential-and-red-build-rows/`.
3. Owed after the merge: one `workflow_dispatch` of `sensors.yml` on `main` with `apply: false`, which is the
   end-to-end record of the two steps (the guard forbids dispatching from this branch), and the repository
   secret's recreation with the scope §9 now states — a person's step, named in the closure's reason.
4. Rollback: revert this change's commit. The fetcher loses its flag, `--rows` disappears, the two steps
   collapse into `run.sh --due`, and §9 and `CODEOWNERS` return to the issues-only claim — the state in which
   `evidence/red-on-head.log` reproduces.

## Open Questions

None that change this design. Whether a future check reads a build output a red build *can* produce is a row's
own declaration: the case that proves the two lists are the registry is where that judgement is recorded.
