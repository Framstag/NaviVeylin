# Proposal

## Why

`github-issue-tooling` builds the tracker surface and `migrate-todo-to-github-issues` moves the
backlog into it. The loop itself is still the old thing: **the session is the orchestrator**, it reads
and writes `TODO.md` in Phase F, and **git — branch, worktree, commit — is done by the model**. The
agentic-coding concept doc (§4 step 2, §5) requires the opposite: branching and the budget outside the
LLM, scripts as sensors and gates, an agent only where judgement is needed and always on a
script-prepared work package.

Two measured facts say this is worth doing rather than restyled:

- **The loop's own reference records its yield and its cost shape.** First run: 12 candidates, 3
  archived, **9 gated out**; a later run: 0 closed, 32 skipped; skip verdicts
  `needs-device 8 · needs-decision 7 · in-flight 7 · refuted 4 · too-wide 2 · stale 2 · unspecifiable 1
  · needs-diagnosis 1` — and *not one* died on the item's class. Today that gate is applied by an LLM
  reading prose. Most of it is mechanical (class, status, author, whether the cited file/symbol still
  exists, whether the run may touch a device) and should cost a script, not a child.
- **The landing step is the repository's most fragile one.** Concept doc §3: several repair commits
  (lost hunks, uncommitted tests, test sources that did not compile), caused partly by hand-driven
  parallel changes. Git ownership is exactly what a wrapper removes from the model.

What the port buys, concretely: the loop's *inputs* become attributable, labelled issues instead of a
hand-maintained prose file; the loop's *start* becomes one command; and the parts that must never be
improvised — branch creation, budget, queue, label transitions, closure — stop being things the model
is trusted to do correctly.

## What Changes

- **The entry becomes a script.** `tools/loop/run.sh --items N --minutes M [--review on|off]
  [--may-close yes|no] [--push yes|no] [--dry-run]` — one command, no dialogue, no session needed. A
  script cannot ask, so every budget is a flag with a recorded default instead of an `ask_user`.
- **The queue becomes deterministic.** `tools/loop/prescreen.sh` selects from
  `tools/issues/list.sh --eligible-only`, drops `class/feature` and `status/in-flight`, applies a
  **filesystem sensor** (does the entry's cited path/symbol still exist?), ranks bugs before
  improvements, and writes a queue. The LLM receives a pre-filtered queue, never the whole backlog.
- **Branching and the worktree move out of the LLM.** The wrapper creates
  `.pi/worktrees/issue-<N>` on branch `fix/issue-<N>-<slug>`, seeds it (the submodule, `local.prop*`,
  the gitignored skills), commits each step with today's message shapes, and removes the worktree at
  the end. No phase prompts a model to run `git branch`, `git checkout`, `git worktree` or `git push`.
- **Two `pi` processes per item, not one session.** A worker (`pi --print`, one-shot, its prompt file
  built by the wrapper) runs the phases against the prepared work package; a **fresh** reviewer
  process then checks it and must not be the writer — the same separation as today, but structurally
  isolated instead of context-disciplined.
- **State is machine-local and the wrapper is its only writer**
  (`.pi/issue-loop/run.json`, gitignored), as today. The existing `.pi/bugfix-loop/run.json` is left
  untouched rather than migrated: a run in flight must not be orphaned by a rename.
- **Label transitions belong to the wrapper**, through `github-issue-tooling`'s tooling: on pick
  `agent/in-progress`; on a gated skip the matching `agent/<verdict>` plus `status/on-hold` on the
  **same** issue; on closure the reason and evidence via `close.sh`. The model never writes a label,
  and `agent/eligible` stays reserved and unwritable (change 1, D2).
- **Closure after review PASS**, with one line of root cause and the evidence paths, posted as a
  comment before the close. `--may-close no` leaves closure to a human; the default closes.
- **A decision cap that stops a run.** `--max-decisions N` (default 3): if that many `agent/needs-decision` issues are already open, the run **refuses to start** (exit 5) and prints the list. During a run a `needs-decision` verdict is recorded on the issue it belongs to, and **no new issue is created for a decision** — the prepared A/B/C decision queue of the concept doc §4 step 4 is future work and is named as such in `design.md`.
- **Per-item metrics are recorded by the wrapper**: wall time, `pi` exit status, review rounds,
  verdict — the numbers concept doc §6 asks a loop to justify itself with.
- **The child invocation is one indirection, so the loop is sandbox-partitionable.** The wrapper never
  spells out how to start a child: `tools/loop/loop.conf` carries one command (`LOOP_AGENT_CMD`, default
  `pi --print`) and `run.sh` expands it, the self-test stubs it, and a structural check asserts no agent
  command is named anywhere else. Git, labels, state and closure stay host-side, the reviewer needs no
  write access, and device work is recorded rather than performed — so a future agent sandbox changes
  *where the child runs* and nothing else (`design.md` D15).
- **The skill is ported, not renamed.** `.pi/skills/fix-loop/` keeps its name (identity: `AGENTS.md`,
  archived changes and this session reference it) and its rules; what changes is the mechanics it
  points at: entry, state path, queue source, label transitions and the `TODO.md` bookkeeping in
  Phase F, which becomes `update.sh`/`close.sh`.
- **Explicitly not in this change**: parallel loops or a merge queue (concept doc step 2), the hotspot
  loop (step 3), scheduled unattended runs (step 4), pushing or opening pull requests by default, any
  CI integration of the loop, and **the agent sandbox** — no `sbx` invocation, kit, image, secret,
  network policy or dependency, and the wrapper's tests pass on a machine where `sbx` does not exist.
  Running the children in a Docker Sandbox is the named future change `sandbox-agent-execution`
  (`design.md` D15, `Open Questions`); the wrapper is built so that change is a configuration plus an
  image, not a rewrite.

## Capabilities

### New Capabilities

- `agent-issue-loop`: an item is closed by an unattended budgeted run — one command with no dialogue, only authorised and eligible items, version control owned by the run's script and never by an agent, one fresh process per item and a different one to review it, closure only after a passing review with evidence, state with a single writer that survives interruption, a pile of open decisions that stops the run, and mechanics (the agent command, the skills, the gate) taken from configuration so the run can be pointed at another environment or another project without a rewrite.

### Modified Capabilities

None.

## Impact

New:
- `tools/loop/run.sh`, `tools/loop/prescreen.sh`, `tools/loop/worktree.sh`, `tools/loop/state.sh`,
  `tools/loop/labels.sh`, `tools/loop/report.sh`
- `tools/loop/loop.conf` — the recorded run defaults **and the three project-specific keys**:
  `LOOP_AGENT_CMD` (the single seam through which every child is started, default `pi --print`, and
  what a future sandbox change sets), plus `LOOP_WORKER_SKILLS` and `LOOP_GATE_CMD`, so the portable
  phases name no skill and no build command of this project (`design.md` D16). The allowlist and the
  vocabulary come from the shared profile `tools/tooling.json`
- `tools/loop/prompts/worker.md`, `tools/loop/prompts/reviewer.md` — the two prompt skeletons the
  wrapper fills with a prepared work package
- `tools/loop/fixtures/pi` — a stub `pi` that records its argv and exits with a canned report
- `tools/loop-selftest.sh` — top-level, matching the `tools/*-selftest.sh` convention
- `.pi/issue-loop/run.json` — state (gitignored, created at run time)

Modified:
- `guidelines/Backlog.md` — the sections this change makes stale: its own row in §1, and the §12 rows for the decisions it lands
- `.pi/skills/fix-loop/SKILL.md`, `REFERENCE.md`, `scripts/loop-state.sh` — entry, state path, queue
  source, label transitions and Phase F bookkeeping
- `AGENTS.md` — the `fix-loop` row and the `.pi/skills/fix-loop/scripts/loop-state.sh` path it names

Not affected: no Kotlin/Java/C++ under `app` / `auto` / `core` / `osmscout-client-java` / `buildSrc`,
no manifest, no Gradle configuration, no native or JNI boundary (so no submodule patch and no
bridge-module override — the wrapper *checks out* the submodule, it does not modify it), no CI
workflow, no container or sandbox configuration, neither distribution flavour.

Scope: agent-harness tooling only — variant-independent; neither phone-only nor car-only work.
Guidelines referenced: `guidelines/Build.md` §1 (skills and harness prerequisites), §2 (the iteration
protocol and its scope block), §3 (commands), §5 (versioning — the loop does not release). No `UI.md`,
`MapRendering.md`, `Design.md`, `Regulatory.md` or `Logging.md` section is affected.

**Additive for the app, breaking for the loop's entry.** The loop's start changes from an instruction
to `tools/loop/run.sh`; the old skill-driven start still works until the port lands, because nothing is
deleted. **Rollback**: revert `tools/loop/**`, `tools/loop-selftest.sh`, the skill edits and the
`AGENTS.md` row; delete `.pi/issue-loop/`. Issues can be reopened, so no public state is
irreversible; the branches the wrapper created remain and are named in its report.

**Verification.** The wrapper is tested offline against a stub `gh` (change 1) **and a stub `pi`** in a
throwaway git repository created by the self-test: queue selection, branch and worktree creation and
removal, one-writer-per-tree, label transitions, closure, stop conditions and the reservation
refusals. Then one **real single-item run** (`--items 1`) on the live tracker is the acceptance
evidence, recorded with its report, because a wrapper that has never run is unproven. No device
measurement applies: the change produces no UI.
