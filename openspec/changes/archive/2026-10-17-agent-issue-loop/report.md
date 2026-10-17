# Report — `agent-issue-loop`

The loop is a script now. One command starts a budgeted, unattended run; the run owns the budget, the
working tree, the branches, the commits, the labels and the state; a fresh agent process works one item from
a prepared data package; a second, fresh process reviews it; and nothing closes until the run has read the
archive and the gate evidence itself.

## What landed

| path | what it is |
|---|---|
| `tools/loop/run.sh` | the entry: flags, pre-flight, decision cap, lock, traps, the item loop, the report |
| `tools/loop/state.sh` | the state (`.pi/issue-loop/run.json`), the budget authority (`next`, exit 3 = stop), the lock, the metrics |
| `tools/loop/prescreen.sh` | the mechanical half of the gate: class/status/settled filters, the filesystem sensor, the allowlist term, the ranking, gates 2–5 left `pending` |
| `tools/loop/worktree.sh` | one worktree per run (kept for the next run), one branch per item off the run's base, the two commits, the stale-base refusal, `--clean-worktree` |
| `tools/loop/labels.sh` | every label transition, through the tooling's own write paths |
| `tools/loop/report.sh` | the header, the per-item table, the skips, the open decisions, the branches, the budget left |
| `tools/loop/loop.conf` | the recorded defaults **and** `LOOP_AGENT_CMD`, `LOOP_WORKER_SKILLS`, `LOOP_GATE_CMD` |
| `tools/loop/prompts/worker.md`, `reviewer.md` | the two prompt skeletons, filled from `loop.conf` at render time |
| `tools/loop/fixtures/pi` | the stub child (argv, cwd, the state's digest before and after, an optional action, a fifo to block on) |
| `tools/loop-selftest.sh`, `tools/loop-cases.sh` | the gate: 45 cases, offline, in throwaway git repositories |
| `tools/loop-mutations.sh`, `tools/loop-mutations-selftest.sh` | the mutation harness for the invariants above (one mutation, the case must go red, restore, green again) and its own self-test on a throwaway copy of the engine |
| `.github/workflows/build.yml` | one `Check agent issue loop` step |
| `guidelines/Backlog.md` | §1 row 3 applied; one new §12 row (the write paths below) |
| `AGENTS.md` | the `fix-loop` row and the loop's entry |
| `tools/feature-list/specs.json` | the classification line for `agent-issue-loop` (build-tooling, not user-visible) |
| `.pi/skills/fix-loop/**` | the skill ported (see group 8) |

## Gate results

| gate | result |
|---|---|
| `tools/loop-selftest.sh` | **45 of 45 cases passed**, exit 0; also green under `env -i PATH="$PATH" HOME="$HOME"` |
| `tools/loop-mutations-selftest.sh` | exit 0 (the harness reports an unapplied mutation, an unnoticed one and a real red, and restores what it touches) |
| `tools/issue-tooling-selftest.sh` | **36 of 36 cases passed**, exit 0 (34 before this change; two cases added with the two new write-path flags) |
| `.pi/skills/fix-loop/scripts/selftest.sh` | exit 0 (the forwarder still serves the old interface) |
| `tools/check-doc-routes.sh` (+ its selftest) | 87 references resolve, exit 0 |
| `tools/check-todo-references.sh` | exit 0 |
| `tools/gen-feature-list.sh --check-classification` | exit 0 (`stale: 1` — see below) |
| `openspec validate agent-issue-loop --type change` | valid |

`openspec validate --changes` reports 18 passed, 1 failed: the failure is
`fix-navigation-engine-fault-isolation`, a change this one does not own. Its two MODIFIED deltas omit five
scenarios the current specs still have, which would drop them at archive. Filed as **issue #159** rather than
absorbed.

**No Gradle build and no Gradle suite was run, and none is required** (10.4 below). The change's gate is
`tools/loop-selftest.sh`.

## Group 8 — the skill was ported, not renamed (8.4)

`.pi/skills/fix-loop/` keeps its name, because `AGENTS.md`, archived changes and sessions reference it. Its
rules are unchanged — the eligibility gate, the three invariants, the stop conditions, the failure handling,
the report. **Three mechanics changed**, and each is stated in the skill with a "what changed" table:

1. **the entry** — it was a session that asked for the budget with `ask_user`; it is
   `tools/loop/run.sh --items N --minutes M …`, whose defaults are recorded in `tools/loop/loop.conf`;
2. **the state path** — it was `.pi/bugfix-loop/run.json` driven by `.pi/skills/fix-loop/scripts/loop-state.sh`;
   it is `.pi/issue-loop/run.json`, owned by `tools/loop/state.sh`, and `scripts/loop-state.sh` is a
   **forwarder** that still answers the old command names, the old `LOOP_STATE` override and the old
   `--allow-closure` flag (its own selftest proves it). The old file is left where it is, so an open run is
   not orphaned;
3. **Phase F's bookkeeping** — `TODO.md`-era label and status edits by a session became `labels.sh` +
   `tools/issues/close.sh`; the wrapper writes every label, and the child is told to touch neither git nor the
   tracker.

Also changed, in the same port: `REFERENCE.md`'s worktree recipe now states that `worktree.sh create` owns
its five steps (nobody runs them by hand), and the phase contract's normative home is
`tools/loop/prompts/*.md` — one home per rule, so the skill no longer duplicates it.

## Group 9 — falsification (revert-checks)

Eleven mutations, one per invariant, each restored. Every named case went **red** with the mutation in place
(exit 1 from `tools/loop-selftest.sh --case …`) and **green** again after the restore; the full suite then
exited 0. The set is versioned as **`tools/loop-mutations.sh`**: it applies one mutation, runs the named case,
restores and re-runs it, and reports its own two failure modes — a pattern that no longer matches the line
(`MUTATION-DID-NOT-APPLY`) and a case that stays green — as failures rather than as results.
`tools/loop-mutations-selftest.sh` proves those guards, and the restore, on a throwaway copy of the engine, so
trusting the harness needs no mutation of the working tree. The mutations and their results are
`evidence/revert-checks.md`.

Notable: 9.3 (state written while a child runs) and 9.5 (the run taking the child's word) are the two that
first passed the mutation, and both were real gaps in my *test*, not in the wrapper: the fifo that holds a
child open was named with a relative path (so the child never blocked), and the verification chain had grown
a condition the mutation did not neutralise. Both are fixed, and both are recorded here rather than quietly
re-run.

## Group 10 — integration and acceptance

### 10.2 — the dry run against the live tracker

`tools/loop/run.sh --dry-run --items 1 --minutes 90` ran against the live tracker and reported **0 eligible,
0 excluded**; `tools/issues/list.sh --eligible-only --json` answered 0 at the same moment. The transcript is
`evidence/dry-run-live.md`.

### 10.3 — the acceptance, and why it is unproven

The acceptance run (`--items 1 --minutes 90 --review on`) has **no eligible candidate**: 158 issues on the
tracker, none carrying `agent/eligible`. The migration imported them unauthorised on purpose, and the loop may
not authorise its own work, so the pre-screen ends the run with `nothing eligible` (exit 0) and creates
nothing. **The real-item acceptance is therefore unproven and moves to the first run after an owner
authorises an item** — an owner action this apply pass must not perform. What stands in for it: 45 offline
cases driving the real wrapper, worktree, branches, commits, labels and closure in throwaway repositories,
and the 11 revert-checks.

### 10.4 — exclusions

`git status --short` lists **no file under `app/`, `auto/`, `core/`, `osmscout-client-java/` or `buildSrc/`**,
and no manifest, resource, Gradle file or native source changed. The delta is shell, markdown, JSON and one
CI step: `tools/loop/**`, `tools/loop-cases.sh`, `tools/loop-selftest.sh`, `tools/issues/{update,close}.sh`,
`tools/issue-tooling-cases.sh`, `tools/feature-list/specs.json`, `guidelines/Backlog.md`, `AGENTS.md`,
`.github/workflows/build.yml`, `.pi/skills/fix-loop/**`. So: **no Gradle build, no Gradle suite, no device and
no pixel measurement**; the structural checks of the files it edits stand in (`tools/loop-selftest.sh`, the
issue tooling's selftest, the two doc gates, the classification gate).

### 10.5 — the run's first numbers

| number | value |
|---|---|
| yield of the live run | closed 0 · skipped 0 · blocked 0 (no eligible item; the dry run is `evidence/dry-run-live.md`) |
| per-item wall time | **0.126 s** for a complete offline item (stub worker, stub reviewer, archive, labels, closure); `run#metricsRecordedPerItem` asserts the recorded wall time is positive |
| review rounds | 1 on the passing path, 2 on the failed path (`run#reviewRoundsCapAtTwo`); no round has been spent on real work yet |
| the 11-minute cold build | **not paid** — no Gradle build ran, because the change touches no compiled file |
| gate cost | 45 offline cases in ≈50 s on this machine (measured: `real 0m48,594s` for the first full run) |

### 10.6 — sandbox-partitionable but sandbox-free

The five properties of `design.md` D15 hold, each required by another decision:

1. **one injectable child command** — `LOOP_AGENT_CMD` in `loop.conf`, and `state#agentCommandIsTheOnlyIndirection`
   asserts both children go through it; `run#engineNamesNoProjectFact` asserts no script names one;
2. **git, labels, state and closure host-side** — the child gets a prompt file and a working directory; its
   argv carries the flag and the prompt and nothing else (`run#oneItemOrderIsFixed`);
3. **the reviewer has a read-only mode** — `--review-read-only` (default on), enforced by a before/after
   comparison of the tree and exercised by `run#reviewerWriteInvalidatesTheRound`;
4. **device work is host-side only** — a device-gated item is `needs-device`, a verdict, not work
   (`prescreen#deviceItemIsAVerdictNotWork`), and no script invokes a device tool;
5. **evidence lands on the mounted tree** — a change citing a test result must carry it in its own
   `evidence/` directory (`run#citedEvidenceMustTravelWithTheChange`).

No `sbx` invocation, kit, `sbxenv.yaml`, image, template, secret registration, network policy or dependency
exists, and **`sbx` is not installed on this machine** (checked: `command -v sbx` → nothing); the self-test
passes with a `PATH` that has no `sbx` directory in it (`run#engineNamesNoProjectFact`), and `git status
--short` shows no container, kit or sandbox configuration file. Running the children in a Docker Sandbox is
the named future change `sandbox-agent-execution`; this wrapper is built so that change is a configuration
plus an image.

### 10.7 — the capability classification

`tools/feature-list/specs.json` carries
`"agent-issue-loop": { "area": "build-tooling", "userVisible": false, "surfaces": [] }`. The line is
load-bearing, not decorative: with the spec present and the line removed, the gate exits 1 naming
`agent-issue-loop` (verified by copying the delta into `openspec/specs/` for the check and removing it
again). Until the archive ships the spec, the gate reports `stale: 1` and still exits 0 — that is the
intended window, and it closes when `openspec archive` merges the delta.

### 10.8 — traceability

`evidence/traceability.md`: one row per scenario (32 rows, equal to
`grep -c '^#### Scenario:' specs/agent-issue-loop/spec.md`), each naming the case that exercises it and the
task that owns that case.

Building the table found and closed six gaps, four of which needed the implementation to grow: the run now
checks that a claimed gate has a `test-results` XML and that a change citing a test result carries it in its
own `evidence/` directory; an escalation posts the work already done (root cause, the failing case, the
branch) as a comment on the item; and an empty `LOOP_GATE_CMD` is recorded as
`skipped (no gate configured)` instead of as an invented tally. The other two were coverage:
`prescreen#withoutTheOptInIsNeverQueued` and `prescreen#deviceItemIsAVerdictNotWork`.

### 10.9 — the documentation

`guidelines/Backlog.md` §1 now says three changes have landed and marks row 3 applied with what it landed;
§12 gained one row for the decision described below. `AGENTS.md`'s `fix-loop` row names the entry
`tools/loop/run.sh`, and the first `tools/loop/…` path it names exists.
`tools/check-doc-routes.sh` (87 references) and its selftest both exit 0.

## Scope additions and deviations (for the reviewer)

1. **Two write paths were added to the issue tooling**: `update.sh --remove-label` and
   `close.sh --comment-only`. Tasks 4.1 and 6.6 require the loop to take `agent/in-progress` off and to post
   the evidence comment while leaving an issue open, and neither was possible through the tooling; putting a
   raw `gh issue edit`/`gh issue comment` in the loop instead would have bypassed the reserved-label refusal
   and the evidence-before-closure rule. Approved by the owner before implementing (option A), and covered by
   two new cases in the issue tooling's selftest.
2. **The trap case signals SIGTERM, not SIGINT.** POSIX starts background jobs in a non-interactive shell with
   SIGINT and SIGQUIT ignored, so a SIGINT to the job under test is silently dropped and the case would prove
   nothing. The run traps both identically, and the case asserts the SIGINT trap exists structurally. The
   spec's requirement ("the in-progress marking is removed, the item is recorded blocked") is what the case
   proves.
3. **`--review-read-only`** is a flag the tasks introduced (6.9) beyond the flag set in design D2; it is
   documented in the skill's flag table.
4. **Seven cases** were added by the traceability and coverage passes (10.8): the six above plus
   `prescreen#settledItemIsDropped` (an item the tracker has already settled, and a closed issue whose labels
   were never moved on).
5. **The classification line is `stale` until the archive ships the spec** (10.7); the gate reports it and
   exits 0.
6. **The commit message shape changed** from `fix: <slug> (§N)` to `fix: <slug> (#N)` — after the migration
   an item's identity is its issue number, and `§N` is the migration marker inside its title.

## What this change did not do

No parallel loops or merge queue; no hotspot loop; no scheduled runs; no push or change request by default
(`--push yes` is the only publisher, and no code path opens a pull request); no CI integration of the loop
itself; no agent sandbox; no new issue for a decision (the prepared A/B/C queue stays future work, named in
`design.md`); and no change to `app/`, `auto/`, `core/`, `osmscout-client-java/` or `buildSrc/`.

## What the next session should know

- The acceptance run is still owed: an owner authorises one defect-shaped item (`agent/eligible`), then
  `tools/loop/run.sh --items 1 --minutes 90 --review on` is the run that proves the wrapper end to end.
- `--max-decisions 3` is a guess with no measurement behind it (`design.md`, Open Questions). The first real
  runs are what should set it.
- The tracker holds one item this pass filed: **#159** (the other change's MODIFIED deltas dropping five
  scenarios) — work for another change, not this one.
