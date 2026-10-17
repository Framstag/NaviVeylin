# Design

## Context

See `proposal.md` — Why. This document records only what shapes the sensors.

Consumed from the preceding changes in this series:

```
tools/issues/create.sh   create from a structured finding; fingerprint dedup; refuses on a match (exit 4)
tools/issues/close.sh    --reason + --evidence, comment before close, state_reason, no delete path
tools/issues/update.sh   labels / status / body  (no --comment: a gap this change fills)
tools/issues/policy.sh   preflight (fail-closed on a login outside allowedAuthors) + check <issue>
tools/tooling.json   class/ category/ status/ source/ agent/ prio/ , closed vocabularies, bounds
tools/issues/list.sh     --eligible-only, --class, --category, --status, --author, --id
```

Facts that shape the pipeline, verified against the GitHub documentation 2026-10-16/17:

| fact | source | consequence |
|---|---|---|
| `schedule` only triggers if the workflow file exists on the **default branch**, and **only runs on the default branch**; `GITHUB_SHA` is that branch's last commit | events-that-trigger-workflows | the `main` pin is free for the common case |
| `workflow_dispatch` can be **dispatched against a branch**, so `github.ref` is not pinned by the trigger | same | a runtime guard is required, not optional (D3) |
| scheduled workflows are **disabled after 60 days** with no repository activity in a public repo, and can be delayed under load (avoid the top of the hour) | same | cadence is a maintenance item, recorded in the sensor report |
| `pull_request_target` and `workflow_run` are **privileged** — "may have repository write access and access to referenced secrets" — and must not be used with untrusted PR content | secure-use | neither is used; a sensor with `issues: write` never runs near PR code (D1) |
| recommended hardening for workflow files: `CODEOWNERS` on `.github/workflows`, `GITHUB_TOKEN` default read-only with per-job raises, rotate exposed secrets | secure-use | D1's four controls |
| this repo's landing step is its most fragile (repair commits for lost hunks, uncommitted tests, test sources that did not compile) | concept doc §3 | a broken `main` is the condition worth detecting first (D6) |

## Goals / Non-Goals

**Goals**

- A condition that a script can decide becomes an issue without a human noticing first, and stops
  being one when it clears.
- One reviewable file holds every threshold, cadence and lifecycle.
- The pipeline that can write to the tracker is provably not on the path that runs PR code.
- A sensor can never touch an issue it does not own.

**Non-Goals**

- Not categorisation and not prioritisation (`issue-metadata-loop`): a sensor's row declares its
  `class` and `category`, so a sensor-filed issue arrives classified and needs no model.
- Not the human decision lane (`decision-queue`) — a sensor escalates by setting
  `agent/needs-decision`, and stops there.
- Not a metrics store: no committed baseline, no cache, no artifact. The issue timeline is the history.
- Not the agent sandbox: everything here is a host or CI command; nothing depends on `sbx`.
- Not a general CI reorganisation: `build.yml` is untouched, and no sensor is a required check.

## Decisions

### D1 — An isolated pipeline, and four controls that keep the write token away from PR code

```
.github/workflows/sensors.yml      its own file — a PR cannot add a step to the build pipeline
on: schedule + workflow_dispatch    never pull_request (PR code), never pull_request_target or
                                    workflow_run (documented as privileged: write access + secrets)
permissions: contents: read         workflow default stays read-only
jobs.sensors:
  if: github.ref == 'refs/heads/main'
  permissions: { contents: read, issues: write }    raised for this job only
  concurrency: sensors-main         two runs can never race to file or close
.github/CODEOWNERS  /.github/workflows/  @Framstag   a pipeline change needs the owner's approval
```

| alternative | why rejected |
|---|---|
| add a job to `build.yml` | that file runs on pull requests, so the write token would sit in a workflow whose content a contributor can influence; and a required check that files issues would let a sensor failure block a merge. |
| `workflow_run` after the build to read its result | documented as a privileged trigger, and it is exactly the shape that consumes artifacts produced by PR-triggered workflows — the failure mode the docs name. |
| `pull_request_target` | same, worse: it runs the default-branch workflow with a write token and secrets while PR code is present. |
| a separate repository for the tracker | the tracker is where the loop, the skills and `§N` resolution live; splitting it adds a second allowlist and a second credential for no isolation gain. |

### D2 — The actor is a repository-scoped fine-grained token, not the bot

```
GITHUB_TOKEN                        -> author = github-actions[bot] -> fails allowedAuthors
fine-grained PAT, this repo only    -> author = Framstag           -> passes; Issues: write only  <- chosen
GitHub App installation token       -> author = the app            -> short-lived; needs allowlisting
CI detects, a local run files       -> no CI credential at all     -> costs a manual step per finding
```

The gate's author condition is the only **attributable** term (`github-issue-tooling` D2), so the
chosen identity must be an allowlisted human. The security argument is deliberately not secrecy: a
leaked PAT can file, comment and close issues in this one repository and nothing else — no push, no
release, no workflow scope. The App is the upgrade path (short-lived tokens, no standing secret) at the
cost of an app registration and an allowlist entry; recorded in `Open Questions`.

| alternative | why rejected |
|---|---|
| allowlist `github-actions[bot]` | the author condition then protects nothing for sensor-filed issues, leaving the reserved `agent/eligible` as the only discriminator — a control that survives only because of a rule in another change. |
| reuse `GITHUB_TOKEN` and give sensors no issue access (publish a workflow summary) | no credential in CI is genuinely attractive, but it makes every finding a human step, which is the condition this change exists to remove. Kept as the documented fallback if the PAT is judged unacceptable. |
| a deploy key or a fine-grained PAT with `contents: write` | unnecessary scope: sensors never push. |

### D3 — `main`-only is a guard, not a YAML line

`tools/sensors/guard.sh`, run by the workflow **and** by the local path, asserts:

```
on main?               github.ref == refs/heads/main            (or, locally, the current branch)
HEAD == origin/main?   git rev-parse HEAD == git rev-parse origin/main
clean tree?            git status --porcelain empty
ambient identity?      gh api user --jq .login in allowedAuthors   (reuses policy.sh preflight)
registry consistent?   every row's command exists and its sensor/<id> label is in the taxonomy
```

| alternative | why rejected |
|---|---|
| rely on the trigger alone | `workflow_dispatch` can be dispatched against a branch, so a sensor could run against a branch's code with the write token. |
| rely on branch protection | branch protection governs merges, not which ref a dispatch selects. |
| guard inside each sensor script | five copies of one rule; the guard is one script with its own cases, and a sensor that needs a different precondition declares it in its row. |

### D4 — Identity is a fingerprint **below** the sensor id

```
sensor/<id>        the OWNERSHIP scope   — who may close it (D6)
fingerprint        the SPECIFIC condition — which issue it is
```

```
condition returns, fingerprint matches a CLOSED issue   -> REOPEN it, comment the measurement,
                                                           increment `occurrence`
same sensor, fingerprint is NEW                         -> FILE a new issue (same class, new cause)
condition returns, issue still OPEN                     -> COMMENT only
condition cleared                                       -> comment, then close once hysteresis is met
```

Reopening is right because these sensors watch a **state**, not an event: "main is broken" recurs as
the same state, and the record of the fix that did not hold is the most valuable thing on the issue.

| alternative | why rejected |
|---|---|
| one new issue per recurrence | a graveyard where every recurrence loses the previous timeline, the loop's evidence and the closure reason — the signal that a fix did not hold is exactly what a new issue discards. |
| always reopen, fingerprint not used | same-class/different-cause conditions get conflated: a second, unrelated build failure reopens an issue whose closure says the first was fixed. |
| the sensor id alone as identity | one issue per sensor per lifetime, which is the same conflation with a shorter name. |
| a timestamped incident id | dedup becomes impossible: every run files. |

### D5 — Three outcomes, and only `clear` may close

```
violation  -> file or reopen, comment the measurement
clear      -> comment the clearing measurement, close when hysteresis is met
ERROR      -> post nothing, fail the run loudly, exit non-zero
```

| alternative | why rejected |
|---|---|
| two outcomes, with a crash counting as clear | the classic monitoring failure: a broken check closes every issue it owns, and the tracker reports health it never verified. |
| a crash counting as a violation | filed issues for the monitor's own defects; that is what `error` is for and why the run fails. |
| treat `error` as a skip and continue silently | indistinguishable from `clear` at the report level; the run must be visibly failed. |

### D6 — Hysteresis, the human-decision rule, and recurrence escalation

```
close   after N consecutive CLEAR runs         (N per row; stops flapping churn)
reopen  after N consecutive VIOLATIONS         (symmetric: the same N)
reopen  only when state_reason == completed    a human's not_planned (wontfix / on-hold) gets a
                                               COMMENT, never a reopen — the tooling must not
                                               overrule a recorded owner decision
past K recurrences (K per row, default 3)      stop reopening; set agent/needs-decision
                                               concept doc §5: a fix failing twice is usually a
                                               spec or design problem, not an implementation one
```

| alternative | why rejected |
|---|---|
| no hysteresis | a check that flaps (flaky build, flaky test — this repo has that history) open/close-churns every run. |
| asymmetric hysteresis | the two directions have the same failure mode; treating one as cheap doubles the churn. |
| reopen regardless of `state_reason` | the sensor would silently reverse an owner decision, and `state_reason` exists precisely to distinguish "it was fixed" from "we chose not to". |
| keep reopening forever | the loop's own escalation rule (two rounds, then `needs-decision`) exists for the same reason; a sensor that never escalates papers over a design problem. |

### D7 — A scope invariant bounds a subverted pipeline

`close.sh --sensor <id>` refuses any issue that does not carry that sensor's own `sensor/<id>` label
(exit 3, nothing written). A sensor can therefore never close a human's issue, an imported issue, or
another sensor's.

| alternative | why rejected |
|---|---|
| trust the sensor script | the sensor runs in the pipeline that holds the write token; the invariant must hold *despite* a subverted or buggy sensor, not because the sensor is well-behaved. |
| a separate token per sensor, scoped by label | GitHub has no label-scoped token; it would be one secret per sensor and the same rule re-implemented N times. |
| let the sensors close anything and review afterwards | closure is visible and reversible, but "who may close what" is exactly the kind of rule this repository enforces mechanically rather than by review. |

### D8 — The issue is the metric history

A measurement lives in a comment on the issue; the previous comment is the baseline. Nothing is
committed.

| alternative | why rejected |
|---|---|
| a committed baseline TSV | a second writer in git beside the loop wrapper (breaking one-writer-per-tree), plus a merge conflict on every run. |
| a GitHub Actions artifact or cache | opaque (not reviewable in a diff), expires, and cannot answer "what were the numbers last month" without a download. |
| a `metrics` branch | no conflicts, but it is still a git writer, and the history is one `git log` away from being unreadable. |
| a GitHub Projects field | a second API surface with its own drift, for a number the issue timeline already carries. |

### D9 — The registry is the policy surface, and one row is the whole definition

```
tools/sensors/sensors.json
  id, cmd, cron, threshold, consecutive, class, category, lifecycle (file | file+close), fingerprint spec
```

`run.sh --due` reads it, selects by cadence, runs each command, and interprets its output. A row
declares `class` and `category` so a sensor-filed issue is classified without a model — the "as much as
possible is script" split.

| alternative | why rejected |
|---|---|
| one script per sensor, thresholds inside it | thresholds end up in N places, so "what do we check and at what bound" cannot be answered by reading one file — and that reviewability is the phase's whole point. |
| the registry as a section of the workflow YAML | the local path (a dry run, a manual re-run) could not read it, and it mixes policy with pipeline mechanics. |
| sensors that file unclassified and leave classification to `issue-metadata-loop` | couples two changes: the loop could not consume sensor output until the metadata pass had run. |

### D10 — The four metric rows, and package size does not build

```
build duration    reuse tools/gate-timing-report.sh                  threshold: a multiple of the baseline
file size/hotspot tools/sensors/hotspot-report.sh (git log --numstat) size x change frequency, per path
package size      reuse a gate or release artifact; never a second build   threshold: growth past a bound
coverage          reuse the coverage instrumentation                  threshold: a floor, per module
```

| alternative | why rejected |
|---|---|
| let the package-size sensor build | it would double CI cost and runtime for a number the pipeline already produces; the row consumes an artifact. |
| a single aggregated "health" metric | one number cannot be a falsifiable WHEN/THEN, so it is not loop food; each metric keeps its own bound and its own issue. |
| thresholds only, no consecutive count | a metric that moves once is not a regression; the count is what separates a trend from a blip. |

### D11 — Sandbox-agnostic, and the sensors never run in the loop

The sensors are host or CI commands that write issues; the loop reads issues. Neither calls the other.
No sensor invokes an agent, so no sensor needs a sandbox, and nothing here presumes the loop's
`LOOP_AGENT_CMD` seam. `run.sh` and `guard.sh` name no container, no `sbx`, and no device tool.

| alternative | why rejected |
|---|---|
| let a sensor call an agent to interpret its output | the whole value is that the sensor's verdict is mechanical and cheap; the model's place is `issue-metadata-loop`. |
| run the sensors inside the loop's sandbox | couples a scheduled producer to a batch consumer, and the sandbox has no tracker credential by design. |

### D12 — The engine is portable, the registry is the project

The runner, the guards, the lifecycle and the three verbs are portable; what is this project's is
`tools/sensors/sensors.json` — commands, cadence, thresholds, consecutive counts, class and category —
plus the shared profile `tools/tooling.json` (repo, allowlist, value sets). `run.sh`, `lib.sh`,
`guard.sh` and `report.sh` therefore name no project command, no project path and no threshold, and a
second project adapts the registry and the profile without editing the engine.

| alternative | why rejected |
|---|---|
| hard-code the four metric rows in `run.sh` | a second project would edit the engine to add a check; the registry exists so a row is the whole definition (D9). |
| a per-project copy of the engine with the rows edited in | every later fix to the lifecycle would have to be applied N times, which the copy-per-project distribution (P1) can afford only if the copy is unmodified below the profile. |
| make the sensor *kinds* (`metric`, `condition`) configurable | the two kinds differ only in what their command prints; the contract is one JSON shape (D9), so a kind field would add configuration without adding capability. |

## Risks / Trade-offs

- **A standing PAT in the repository** → scope is the mitigation (Issues: write, one repository, no
  push/release/workflow); rotate it; the App is the upgrade path. Recorded, not hidden.
- **A sensor that crashes looks like health** → three-valued outcome, `error` fails the run and posts
  nothing, and a case asserts it (D5, revert-check 8.4).
- **A flapping condition churns the tracker** → symmetric hysteresis, and past K recurrences the sensor
  escalates instead of reopening (D6, revert-checks 8.5, 8.6).
- **A subverted pipeline closes anything it likes** → the ownership-scope refusal in `close.sh`
  (D7, revert-check 8.3), plus no delete path at all, plus CODEOWNERS on the workflow file.
- **Thresholds become noise nobody owns** → the registry is one reviewable file under CODEOWNERS'
  spirit, the report prints the number for every sensor every run, and the decision cap in the loop's
  view means a pile of sensor issues is visible as a decision, not as backlog growth.
- **Scheduled workflows stop after 60 days of repository inactivity** → the report prints each row's
  last run, so a silently disabled schedule is visible on the next manual `workflow_dispatch`.
- **A check that reports a metric without a bound** → the registry row requires a threshold and a
  consecutive count; the guard refuses a row missing either, so "sensor" cannot mean "reporter".

## Migration Plan

1. `guard.sh` + its cases (the pin is proven before anything can write);
2. `sensors.json` + `run.sh` + `lib.sh` + the selftest harness, `--dry-run` default;
3. the three issue-script edits: `--comment`, `--sensor` scope, `sensor/<id>` + condition fingerprint;
4. `reopen.sh`, with the `state_reason`, hysteresis and escalation rules;
5. the four metric rows, `hotspot-report.sh` being the only new measurement;
6. `.github/workflows/sensors.yml` + `.github/CODEOWNERS`, then one manual `workflow_dispatch` run
   against `main` with `--dry-run`, recorded;
7. one real scheduled run, recorded.

Rollback: disable the workflow first (`workflow_dispatch`-only or delete the file), revoke the PAT,
then revert the files. Issues already filed stay and are closed with a comment naming the rollback;
nothing in the app or the release path is involved.

## Open Questions

Genuinely deferrable; none changes the approach or the task breakdown:

- PAT today vs GitHub App installation token. The App is strictly better on secret lifetime and needs
  an app registration plus an allowlist entry; the PAT is one secret and one edit. The design works
  with either, and the tooling only sees `gh`'s ambient identity.
- Cadence per row: daily for `broken-main`, weekly for the hotspot and package-size rows is the
  starting point; the first weeks' reports are what should set them.
- Whether a sensor row should also be able to open an issue in a *different* repository (e.g. the
  libosmscout submodule upstream). Out of scope here: this change writes only to this tracker.
