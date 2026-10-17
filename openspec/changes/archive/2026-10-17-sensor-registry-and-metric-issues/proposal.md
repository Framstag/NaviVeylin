# Proposal

## Why

`github-issue-tooling` built the ingestion contract and `migrate-todo-to-github-issues` moved the
backlog into it. Both assume the issues already exist. This change adds the **producers**: checks that
detect a condition on their own and keep the tracker honest without a human noticing first.

Three things make this the right next step and not a new architecture:

- **The consumer side is already loop-shaped.** A metric violation or a broken `main` is a
  falsifiable assertion that is false on HEAD — the `fix-loop`'s Phase B condition 7 exactly. So a
  sensor does not need a new mechanism; it needs to *file a classified issue*, and the loop can act on
  it as soon as the owner applies `agent/eligible`.
- **Three of the intended metrics already have a producer in this repo**: `tools/gate-timing-report.sh`
  (build duration), `tools/project-metrics-report.sh`, and the coverage instrumentation
  (`CoverageInstrumentation`). The new work per metric is a threshold row, not a measurement.
- **The repository's own history argues for it.** Its landing step is the most fragile part of the
  process — several repair commits, one of them for test sources that did not compile (concept doc §3)
  — and a broken `main` build is exactly the condition nobody files an issue for, because the person
  who broke it fixes it locally and the next person inherits a mystery.

The condition today is that the tracker is only as complete as the owner's memory. With a registry,
"what does this project check" becomes a reviewable file, and every check has one place that says its
threshold, its cadence and what it does when the condition clears.

## What Changes

- **A sensor registry.** `tools/sensors/sensors.json` holds one row per sensor: `id`, the command,
  its cron, its **threshold**, its **consecutive** count, the `class/` and `category/` its issues
  carry, and whether it owns an open/close lifecycle. A row is the whole definition; adding a check is
  a row, not a script.
- **A runner.** `tools/sensors/run.sh --due` selects the rows whose cadence is due, runs them,
  and files through `tools/issues/create.sh` — fingerprint-deduped, `source/ci` plus `sensor/<id>`.
  `--dry-run` is the default, as everywhere else in this tooling.
- **The sensor lifecycle, with three outcomes.** Every sensor reports `violation`, `clear` **or
  `error`**; only `clear` may close, and `error` posts nothing and fails the run. A check that crashed
  must never read as "condition gone" — that is how a broken monitor closes every issue it owns.
- **Reopen, not re-file.** A returning condition reopens the issue it already has, with the new
  measurement as a comment and an incrementing `occurrence`. Identity is a **fingerprint below the
  sensor id**: `sensor/<id>` is the ownership scope, the condition fingerprint decides *which* issue.
  Same condition back ⇒ reopen; same sensor, new cause ⇒ a new issue; still open ⇒ comment only.
- **Three rules that keep a reopen honest.** Symmetric hysteresis (close after N consecutive clear
  runs, reopen after N consecutive violations) so a flapping check does not churn; reopen only a
  `completed` closure, never a human's `not_planned` decision; and past K recurrences the sensor stops
  reopening and sets `agent/needs-decision` — concept doc §5, a fix that fails twice is usually a spec
  or design problem.
- **A scope invariant that bounds a subverted pipeline.** `close.sh --sensor <id>` refuses any issue
  that does not carry that sensor's own `sensor/<id>` label. A sensor can therefore never close a
  human's issue, an imported issue, or another sensor's.
- **An isolated CI pipeline, pinned to `main` and to a concrete user.** Its own workflow file
  (`.github/workflows/sensors.yml`) triggered only by `schedule` and `workflow_dispatch` — never
  `pull_request`, and never `pull_request_target` or `workflow_run`, which GitHub documents as
  privileged with write access and secrets. Job-level `issues: write` only, a
  `github.ref == 'refs/heads/main'` guard, a `concurrency` group so two runs cannot race to file or
  close, and `.github/CODEOWNERS` covering `.github/workflows/` so a pipeline change needs the owner's
  approval.
- **Identity comes from a repository-scoped fine-grained token** with `Issues: write` and nothing
  else, so sensor-filed issues are authored by an allowlisted user and pass the tooling's gate. The
  security argument is blast radius, not secrecy: a leaked token can file, comment and close issues in
  this one repository and nothing more. The upgrade path is a GitHub App installation token.
- **Runtime guards, not just YAML.** `workflow_dispatch` can be dispatched against a branch, so the
  trigger alone does not pin the ref: `tools/sensors/guard.sh` asserts on `main`, `HEAD == origin/main`,
  a clean tree, and an ambient login that is on the allowlist (reusing `policy.sh preflight`). Each
  guard is a testable case rather than a line of YAML to be trusted.
- **The four metric sensors**, each a registry row:
  build duration (reuses `gate-timing-report.sh`), file size / hotspot (a new `tools/hotspot-report.sh`
  over `git log --numstat`), package size (reuses a gate or release artifact — it must not build a
  second time), and code coverage (reuses the coverage instrumentation).
- **The issue is the metric history.** A measurement lives in a comment on the issue, never in a
  committed baseline file. No metrics store, no merge conflict on a TSV, no expiring cache — and the
  previous comment *is* the baseline, which is what makes "still bad" and "worse than last time" both
  answerable.
- **Explicitly not in this change**: the categorisation and prioritisation passes (`issue-metadata-loop`
  — sensors file already-classified issues because their row declares `class` and `category`), the human
  decision lane (`decision-queue`), parallel loops and the merge queue, and any agent sandbox.

## Capabilities

### New Capabilities

- `backlog-sensors`: checks a project declares — a row naming the command, the cadence, the bound and the consecutive count; a three-valued outcome in which only a demonstrably cleared condition closes an issue; identity finer than the check that produced it, so a second cause is a second issue; a recurrence that escalates instead of reopening forever; a scope that lets a check act only on what it owns; a pipeline of its own, pinned to the main branch and least-privileged; the issue as the measurement history; and issues that arrive classified and unauthorised.

### Modified Capabilities

None.

## Impact

New:
- `tools/sensors/sensors.json` — the registry
- `tools/sensors/run.sh`, `lib.sh`, `guard.sh`, `report.sh`
- `tools/sensors/hotspot-report.sh` — the one new measurement (size × change frequency)
- `tools/sensors/fixtures/` — fixture sensor outputs and the stub `gh` cases
- `tools/sensor-selftest.sh` — top-level, matching the `tools/*-selftest.sh` convention
- `.github/workflows/sensors.yml` — the isolated pipeline
- `tools/issues/reopen.sh` — reopen the sensor's own closed issue with evidence
- `.github/CODEOWNERS` — covering `.github/workflows/`

Modified:
- `guidelines/Backlog.md` — the sections this change makes stale: its own row in §1, and the §12 rows for the decisions it lands
- `tools/issues/update.sh` — `--comment`, the measurement path (named as a gap by
  `github-issue-tooling`)
- `tools/issues/close.sh` — `--sensor <id>`, the ownership-scoped close
- `tools/issues/create.sh` — accept `sensor/<id>` and use the condition fingerprint as the dedup anchor
- `TODO.md`-free by construction: this change reads and writes the tracker only

Not affected: no Kotlin/Java/C++ under `app` / `auto` / `core` / `osmscout-client-java` / `buildSrc`;
no manifest; no Gradle configuration; no native or JNI boundary (so no submodule patch and no
bridge-module override); no release state. The existing `build.yml` pipeline is untouched — that
separation is the point of the change.

Scope: agent-harness and process tooling only — variant-independent; neither phone-only nor car-only
work. Guidelines referenced: `guidelines/Build.md` §1 (skills and harness), §2 (the iteration
protocol and its scope block), §3 (commands), §9 (the licence gate's own CI step, whose pattern of a
narrow, token-free job this follows). No `UI.md`, `MapRendering.md`, `Design.md`, `Regulatory.md` or
`Logging.md` section is affected: no UI, rendering, logging or disclosure behaviour changes.

**Additive.** No existing behaviour is removed; the metrics gain a threshold and a filing path, and
the tracker gains producers. **Rollback**: disable the workflow (`workflow_dispatch`-only, or delete
`.github/workflows/sensors.yml`), revert `tools/sensors/**`, `tools/sensor-selftest.sh`,
`tools/issues/reopen.sh`, the three `tools/issues/*.sh` edits and CODEOWNERS. Issues already filed stay
and are closed with a comment; the token secret is revoked independently. Nothing the app does changes,
so no release step is involved.

**Verification.** An offline self-test drives the runner against fixture sensor outputs and a stub
`gh`: selection by cadence, threshold and consecutive rules, `error` never closing, the reopen/
re-file/comment decision by fingerprint, the ownership-scoped close refusal, the hysteresis, and every
guard. Then one real scheduled run in CI is recorded — its workflow run URL, the sensor rows it
selected, and the issue it filed or closed — plus one deliberate dry run on the host. No device or
pixel measurement applies.

**Actor decision recorded in `design.md`**: a repo-scoped fine-grained PAT, not `github-actions[bot]`
in the allowlist, because the author condition is the only attributable term of the gate.
