# Apply report — `sensor-registry-and-metric-issues`

The change's own record: what landed, the actor decision, the evidence each verification task owes, and the
two integration steps this working tree could not perform.

## What landed

New:

- `tools/sensors/sensors.json` — the registry: six rows (four metrics, two conditions), the fixed labels a
  filing carries, and the escalation's bound and labels
- `tools/sensors/lib.sh` — the contract: row validation, the finding grammar, the cadence, the digests, the
  tracker reads and the body record
- `tools/sensors/run.sh` — selection by cadence, validation, the three outcomes, the dispatch by fingerprint,
  hysteresis and escalation
- `tools/sensors/guard.sh` — five runtime guards, each with its own message and exit code
- `tools/sensors/report.sh` — the per-row counting line and the staleness line
- `tools/sensors/hotspot-report.sh` — size × change frequency from `git log --numstat`
- `tools/sensors/metrics/{build-duration,package-size,coverage}.sh`, `tools/sensors/conditions/{broken-main,pr-conflicts}.sh`
- `tools/sensors/fixtures/**` — the fixture registry, two fixture sensors and the scenario directories
- `tools/sensor-selftest.sh`, `tools/sensor-cases.sh` — 33 cases, offline, no token
- `tools/sensor-mutations.sh` — the falsification harness (the nine mutations of group 8, listed by CI and
  run at change time)
- `tools/issues/reopen.sh` — reopen a completed closure with the returning measurement
- `.github/workflows/sensors.yml`, `.github/CODEOWNERS`

Modified:

- `tools/issues/create.sh` — `--sensor <id>`: the registry's id validated, the ownership label added, the
  condition fingerprint as the dedup anchor, `occurrence: 1` in the body record
- `tools/issues/close.sh` — `--sensor <id>`: the ownership scope, and a sensor closes only a completed fix
- `tools/issues/labels.sh` — the taxonomy reads the `sensor` namespace's values from the check registry, the
  one owner of that namespace (see "added scope" below)
- `tools/issues/lib.sh` — `issues_body_set`, and the registry lookups the label gate uses
- `tools/issues/fixtures/gh` — the stub gained `pr list` and keeps the content of every `--body-file` it saw
- `tools/issue-tooling-cases.sh` — the taxonomy case expects the registry's labels in the diff
- `.github/workflows/build.yml` — one token-free `Check sensor tooling` step (the suite, plus the mutation
  harness's `--list`)
- `tools/feature-list/specs.json` — the `backlog-sensors` classification line
- `guidelines/Backlog.md` §1 (row 4 landed), §13 (the size/frequency row is now half-landed),
  `AGENTS.md` (the backlog row routes to §9 "Checks" as well)

## The actor decision (task 6.5)

A **repository-scoped fine-grained personal access token with `Issues: write` and nothing else**, held as the
repository secret `SENSORS_TOKEN` and passed to the guard, the label step and the runner as `GH_TOKEN`.

The alternative that was rejected is allowlisting `github-actions[bot]`: the gate's author condition is the
only *attributable* term it has (change 1, D2), and allowing the bot would leave the reserved
`agent/eligible` label as the only discriminator for sensor-filed issues — a control that then survives only
because of a rule in another change. A `GITHUB_TOKEN`-derived identity would fail `allowedAuthors` outright,
so the pipeline would produce no issues at all.

The blast radius is the argument, not secrecy: a leaked token can file, comment and close issues in this one
repository — no push, no release, no workflow scope. Rotation is a secret update, and the documented
fallback, if the standing token is judged unacceptable, is to let CI publish a workflow summary and have a
local run file the issues, which costs one manual step per finding. The upgrade path is a GitHub App
installation token (short-lived, no standing secret) at the cost of an app registration and an allowlist
entry. Both are recorded in `design.md` D2.

## Evidence

### Task 9.1 — the host dry run

`--dry-run` is the default; nothing was written to the tracker at any point (the tracker held **159** issues
before and after every run below).

```
$ bash tools/sensors/run.sh --due --dry-run                 # the run's own clock, 2026-10-17T19:51:19Z
no rows selected at 2026-10-17T19:51:19Z

$ bash tools/sensors/run.sh --due --dry-run --now 2026-10-17T06:00:00Z
coverage: fingerprint=- verdict=error measurement=error-no-coverage-report issue=- action=posted nothing, the run fails
broken-main: fingerprint=- verdict=error measurement=error-no-build-result issue=- action=posted nothing, the run fails
sensors: 2 finding(s) reported an error — nothing was written for them and the run fails      (exit 8)
```

The selected rows are `coverage` (`0 6 * * *`) and `broken-main` (`0 */2 * * *`); `hotspot` is weekly, on a
Monday, and the other three run earlier in the day. Both selected rows report **`error`**, because their
input artifacts do not exist on this host — no Gradle gate ran and no build result was produced. That is the
third outcome doing its job: a check that cannot determine anything posts nothing and fails the run, and the
tracker stayed unchanged.

What it would file, from a fixture report and still without a write:

```
$ bash tools/sensors/run.sh --only coverage --input <fixture report>
app: 61.5% (floor 70%)
core: 88.0% (floor 70%)
dry-run: would file a new issue for coverage (fingerprint 47c2c6f8…, measurement 61.5)
coverage: fingerprint=47c2c6f8… verdict=violation measurement=61.5 issue=- action=filed
coverage: fingerprint=2b7ce8a6… verdict=clear measurement=88.0 issue=- action=nothing filed, nothing to clear
```

The guard itself refuses on this checkout, and that is the fail-closed behaviour rather than a defect:

```
$ bash tools/sensors/guard.sh --json
{"guard":"head-is-remote-main","decision":"refuse","reason":"no-remote-main",
 "detail":"there is no refs/remotes/origin/main to compare with HEAD 9f1b1790c14e…","code":5}
```

This working tree has no remote-tracking `origin/main`, so the guard refuses before any sensor runs. The
pipeline therefore fetches that ref by name (`git fetch --no-tags origin +refs/heads/main:refs/remotes/origin/main`)
before the guard, so a dispatch against a branch is still compared against **main**, not against itself.

The full dry-run transcripts are in this change's `evidence/` directory (`host-dry-run.txt`,
`host-dry-run-due.txt`, `host-dry-run-filing.txt`, `selftest-empty-env.txt`), together with the local gate's
transcript (`local-gate.txt`) and the falsification run's (`falsification.txt`).

### Task 9.7 — traceability

`traceability.md` maps every one of the spec delta's **29** scenarios to the case that exercises it and to
the task that owns that case: `grep -c '^#### Scenario:' specs/backlog-sensors/spec.md` = 29, and the table
has 29 rows.

### Task 9.8 — the document

`guidelines/Backlog.md` was corrected for this change: §1's row 4 now reads *applied* with what landed, §1's
status paragraph names which changes have landed, and §13's size/frequency row is amended (the measurement
and its row landed; *ratifying* the bound from recorded baselines is still deferred). `AGENTS.md`'s backlog
row routes to `§9 "Checks"` as well. `bash tools/check-doc-routes.sh` (88 references), its self-test and
`bash tools/check-todo-references.sh` all exit 0.

### Group 8 — the falsification run

Nine mutations, each applied to the shipped script, the named case run, the file restored
(`bash tools/sensor-mutations.sh`, whose list the CI step prints so the harness cannot rot silently):

| task | mutation | named case | result |
|---|---|---|---|
| 8.1 | drop the `HEAD == origin/main` check in `guard.sh` | `guard#refusesOffMain` | red, then green after restore |
| 8.2 | add a `pull_request` trigger to `sensors.yml` | `workflow#onlyTwoTriggers` | red, then green after restore |
| 8.3 | drop the `sensor/<id>` check from `close.sh` | `close#sensorScopeRefusesAForeignIssue` | red, then green after restore |
| 8.4 | make an `error` verdict take the clear path in `run.sh` | `run#errorIsNeverTreatedAsClear` | red, then green after restore |
| 8.5 | close on the first clear run (`run.sh`) | `run#hysteresisBothDirections` | red, then green after restore |
| 8.6 | remove the `K`-recurrence limit (`run.sh`) | `run#escalatesPastKRecurrences` | red, then green after restore |
| 8.7 | let `reopen.sh` reopen a `not_planned` closure | `reopen#refusesANotPlannedClosure` | red, then green after restore |
| 8.8 | use the check id as the dedup anchor (`create.sh`) | `sensors#brokenMainFingerprintDistinguishesCauses` | red, then green after restore |
| 8.9 | hard-code a row's command in `run.sh` | `sensors#engineNamesNoProjectValue` | red, then green after restore |

The harness is `bash tools/sensor-mutations.sh` (its list is printed by the CI step, so a harness that no
longer parses fails the build instead of rotting); the transcript is `evidence/falsification.txt`.

Mutations 8.8 and 8.5 needed the cases strengthened first: the first version of the fingerprint case
asserted a digest that also appears in the finding's own marker line, and the hysteresis case read a body the
writer had already deleted. Both were fixed (an exact record-line assertion, and the stub `gh` keeping the
content of a `--body-file`), and the mutation then turned them red. **A case that cannot be shown red was
not evidence.**

### Task 9.6 — the classification gate

`tools/gen-feature-list.sh --check-classification` exits 0 with the line and, with the line removed and the
spec shipped (a scratch root carrying `openspec/specs/backlog-sensors/spec.md`), exits 1 with:

```
gen-feature-list: unclassified shipped spec id(s):
  backlog-sensors
```

In the tree as it stands the spec is still a *delta* inside this change, so the line currently reads as a
stale entry (printed, not fatal) — after `openspec archive`, the same line is what keeps CI green.

## Deliberately not performed here

The owner's decision on 2026-10-17 is that both steps below stay **owed and documented**: the change is
otherwise complete, and neither step is needed to prove the behaviour it installs — the first scheduled run
after the merge produces 9.2's record, and 9.3 is the owner's first deliberate run.

### Task 9.2 — dispatch the pipeline once against `main`

**Not reproducible in this working tree.** `workflow_dispatch` needs the workflow file on the default branch
of `Framstag/NaviVeylin`, the change is uncommitted and unpushed, and the run needs `SENSORS_TOKEN` to exist
as a repository secret. Nothing about the *file* is untested: its trigger set, its `main` pin, its
`concurrency` group, its permission split, the guard's position, the absence of `github.token` and the
CODEOWNERS entry are asserted by `workflow#onlyTwoTriggers` on every CI run. What is owed is the *run
record*: the workflow run URL, the log's guard lines, the token not being echoed, and the fact that no issue
was created. The first scheduled or dispatched run after the merge produces exactly that, and it belongs in
this change's evidence as soon as it exists.

### Task 9.3 — run one sensor for real

**Not performed, deliberately.** It would file (or reopen) a real issue in the public tracker, and the
credential it needs is the standing `SENSORS_TOKEN` that task 9.2 also needs. Two additional reasons this is
the owner's call rather than the session's: the guard refuses in this checkout (no `origin/main`), so the run
would have to bypass the very guard the change exists to install; and filing is the one action here that
leaves a public trace. It is the natural first act after the merge: `workflow_dispatch` with `apply = true`,
then `tools/issues/list.sh --json` for the issue number, the measurement and the `sensor/<id>` label, and the
clearing direction exercised by re-running the same row once the condition is gone.

## Exclusions (task 9.4)

- **No Gradle build and no Gradle suite was run or is owed.** This change edits shell, JSON, YAML and two
  Markdown files: no Gradle source set, no compiled file, no manifest, no native source. The gate is
  `tools/sensor-selftest.sh` (33 cases, offline) plus `tools/issue-tooling-selftest.sh` (69 cases) and
  `tools/loop-selftest.sh` (45 cases), which cover every file this change touched.
- `git diff --name-only` names no file under `app/`, `auto/`, `core/`, `osmscout-client-java/` or
  `buildSrc/`.
- `.github/workflows/build.yml` gained exactly one token-free step (`Check sensor tooling`: the suite and the
  mutation harness's `--list`); no other line in it changed, and the pipeline that can write to the tracker is
  a separate file — the point of the change. A step grants no permission of its own, so the precise property
  is that the build job's permission set contains no `issues: write` at all, and only
  `.github/workflows/sensors.yml` raises it.
- No device, emulator or pixel measurement applies: no UI, no rendering, no logging behaviour changed.

## The change's own numbers (task 9.5)

- Rows selected in the recorded dry runs: 2 of 6 due at the chosen clock; 6 declared in total.
- Findings filed: **0** — every recorded run was a dry run, and the tracker's issue count was 159 before and
  after each one. What the coverage run *would* have filed: one issue, with the module as its fingerprint.
- Findings that were comments rather than issues: 0 in the recorded runs; 2 in the fixture suite
  (`run#dispatchMatrix` annotating an open issue, `run#dispatchMatrixNotPlannedClosure` commenting on a
  person's decision, `run#hysteresisBothDirections` recording a counter below the consecutive count).
- Errors: **2**, both in the recorded dry run (`coverage`, `broken-main` — their artifacts do not exist on
  this host). Both posted nothing and made the run exit 8, which is the behaviour the third verdict exists
  for.
- CI minutes: the sensor pipeline is a scheduled job of its own — one checkout, one fetch, one JDK-free shell
  run and one report step per hour, with no Gradle and no build. The change-validation cost is one added step
  in `build.yml`: the 33-case sensor suite, offline, ~20 s measured locally.

## Added scope, and why

Two things the change's Impact list did not name, both of which the feature needs to work, and both recorded
here rather than absorbed silently:
1. **`tools/issues/labels.sh` reads the registry.** The `sensor/<id>` labels must exist in the tracker before
   a filing can use them, and the registry is the one owner of that namespace's values. The taxonomy is
   therefore the profile's namespace values *plus* the registry's ids, and change 1's
   `labels#dryRunListsTaxonomy` case expects them (it now derives its expectation from both homes). The
   pipeline runs `labels.sh --apply` before the runner. Nothing else about the label gate changed: a value
   that neither home declares is still refused with `label-opaque`.
2. **The camera-ready pipeline details.** `.github/workflows/sensors.yml` fetches `main` by name before the
   guard (otherwise the guard's comparison has no ref to make in CI, and the local behaviour recorded above
   shows it fails closed), and the stub `gh` gained `pr list` and keeps the content of every `--body-file` it
   saw. Both are additive; the stub's log format is unchanged, so change 1's and change 5's cases still pass.

One deviation from the task text: `Registry#everyRowIsComplete` (task 2.1) lives in the sensor harness
(`tools/sensor-cases.sh`) rather than in the issue tooling's Java core. The gate that owns the registry is
`tools/sensor-selftest.sh`, and the registry is not a subject of the issue tooling's core; the case name and
its assertion are as the task states.
