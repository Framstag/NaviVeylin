# Design

## Context

See `proposal.md` — Why. This document is the wrapper's design; it records only what shapes it.

Inputs it consumes, all fixed by `github-issue-tooling` and `migrate-todo-to-github-issues`:

```
tools/issues/list.sh   --eligible-only -> titles + labels (bodies only for candidates)
tools/issues/policy.sh preflight | check <issue>          exit 3 = refused
tools/issues/update.sh labels/status, refuses reserved labels (label-reserved, exit 3)
tools/issues/close.sh   --reason + --evidence, comment before close, no delete path
tools/issues/core/WorkPackage.java  the delimited, bounded data block for untrusted issue text
```

Constraints that shape the wrapper, measured or documented in this repo:

| fact | source | consequence |
|---|---|---|
| the loop's first run: 12 candidates, 3 archived, **9 gated out**; without a pre-screen 5 of 8 candidates were ineligible | `.pi/skills/fix-loop/REFERENCE.md` | the gate must be cheap and must run before any LLM work; a pre-screen is not optional |
| no gated candidate ever died on its class | same | `class/` is a **ranking** input, not a gate |
| a cold worktree's first build is **11 min**; after it, one test class 1–3 min | same | the worktree is per **run**, not per item |
| a forced both-flavor gate overwrites the previous run's XML evidence | same | evidence is copied out at the moment it is produced |
| a worktree does **not** inherit: the submodule checkout, `local.prop*`, the gitignored skills, and `openspec/changes/*` is gitignored (only `archive/` is versioned) | same, "Running the loop in a worktree" | the wrapper seeds it, and must verify `in-flight` claims against the **main** tree |
| one writer per tree; the orchestrator's bookkeeping during a child's run is a two-writer violation | `SKILL.md` invariant 2 | the wrapper writes state and labels **only while no child runs** |
| `loop-state.sh next` exiting 3 is the only authority on whether an iteration may start | `SKILL.md` invariant 3 | the wrapper never does its own budget arithmetic |
| `AGENTS.md`, archived changes and sessions reference the skill by name `.pi/skills/fix-loop` | repo | the skill keeps its name |

## Goals / Non-Goals

**Goals**

- One command starts the loop; nothing about the start is a dialogue.
- Branch, worktree, commit and push are the wrapper's, never a model's.
- Context isolation is structural: a fresh process per worker and per reviewer.
- Every state write has exactly one writer, and every write survives an interrupt.
- The parts of the gate that *can* be mechanical are mechanical; the parts that cannot are reported as
  a verdict instead of being pretended into a script.

**Non-Goals**

- Not the tracker and not the migration (`github-issue-tooling`, `migrate-todo-to-github-issues`).
- Not parallel loops, no merge queue, no conflict pre-check by Impact-overlap (concept doc §4 step 2).
- Not scheduled unattended runs and not a decision queue of prepared A/B/C issues (§4 step 4).
- Not the hotspot loop (§4 step 3), not a ratchet in CI, not per-loop token accounting beyond what the
  wrapper can observe (wall time, exit status, rounds, verdict).
- Not a general-purpose agent runner: two prompt skeletons, no plugin surface.
- Not the agent sandbox. No `sbx` / Docker Sandbox code, config, kit, secret or network policy is
  added or depended on, and no task may require a sandbox. What this change does owe is a wrapper that
  stays **sandbox-partitionable** by construction — one injectable child command, host-side git and
  labels, a read-only review mode, host-only device work, evidence on the mounted tree (D15).

## Decisions

### D1 — Two changes, not one

`migrate-todo-to-github-issues` (live import, delete `TODO.md`, rewire the 12 skills, `guidelines/*`,
`AGENTS.md`, `openspec/config.yaml`) and this one.

| alternative | why rejected |
|---|---|
| one change containing both | it could not archive until the real import had run, so a long-lived change would hold an irreversible public step; and the wrapper's design would be written against a tracker that does not exist yet. |
| a third change for the wrapper alone, migration split further | two changes is already the smallest split that keeps "tracker exists" before "loop consumes tracker"; a fourth boundary would add handoffs without removing a dependency. |

### D2 — The entry is a script; a skill only teaches how to call it

`tools/loop/run.sh --items N --minutes M [--review on|off] [--may-close yes|no] [--push yes|no]
[--max-decisions N] [--dry-run]`, defaults recorded in `tools/loop/loop.conf` and echoed in the run
header. A script cannot ask a question, so today's `ask_user` budget prompt becomes flags — the same
information, now recordable and reproducible.

| alternative | why rejected |
|---|---|
| keep the skill as the entry (today) | the start stays a dialogue, the budget stays unrecordable, and "who may start a run" stays a convention rather than a file. |
| a GitHub Actions `workflow_dispatch` entry | puts the loop's credentials, git ownership and work tree in CI, where the measured costs (11 min cold build, 40–65 min per item) do not fit and where the device and emulator work cannot happen. Concept doc §4 step 4 is where scheduled runs belong, and it is explicitly deferred. |
| a Gradle task | `./gradlew` for a shell-orchestration step adds a daemon and a configuration phase per call, and a loop task in the build is one more thing the gate's own runs have to avoid triggering. |

The skill `.pi/skills/agent-issue-loop`-style guidance stays, but as *how to call the wrapper*, not as
the mechanism.

### D3 — Two one-shot `pi` processes per item

```
run.sh (bash)                      the only state writer, the only git actor, the only label writer
  |
  +-- pi --print <prompts/worker.md + work package>      cwd = the worktree   (phases A-D)
  +-- pi --print <prompts/reviewer.md + the diff>        cwd = the worktree   (phase E, read-only child)
```

The worker and the reviewer are separate **processes**, not subagents of one session. The wrapper
builds each prompt file from the script-prepared work package and captures stdout, the exit status and
the git diff into the item's evidence directory.

| alternative | why rejected |
|---|---|
| one session that spawns `subagent` children (today) | context isolation becomes a discipline the session must keep ("the orchestrator reads no artifact") rather than a property of the process; and the session also holds the budget, the queue and `TODO.md` bookkeeping, so the one thing that must not be improvised stays inside the model. |
| a separate `pi` process per phase (A, C, D, E) | four cold starts and four re-derivations of the shared state (root cause → change name → tasks); the phases are one argument, and splitting them buys a mid-item restart the wrapper gets more cheaply by resuming from the recorded phase. |
| a long-lived `pi` session driven over RPC | a stateful second process the wrapper must babysit, with its own failure modes on the exact path that must be boring. |

Consequence accepted: a one-shot process cannot ask for permission or clarification. That is already
the loop's rule (only the run start is a prompt), and it is why the wrapper — not the child's word —
decides whether an item closed: it reads `git status`, `git diff --stat`, the archived directory and
the gate XMLs itself.

**The child invocation is a single indirection.** The wrapper never spells out how to start a child:
`tools/loop/loop.conf` carries one command (`LOOP_AGENT_CMD`, default `pi --print`), and `run.sh`
expands it. That is what lets the process boundary later become a **microVM boundary** — the sandbox
variant sets the value to something like `sbx exec loop-<run> -- pi --print` and touches no phase, no
prompt and no state. The self-test stubs the same value, so the seam is exercised on every run rather
than being a hook nobody has called. Implementing the sandbox is a future change (`sandbox-agent-
execution`), named in `Open Questions`; nothing about it is built here.

### D4 — Worktree per run, branch per item, seeded by the wrapper

```
run start  .pi/worktrees/loop          (inside gitignored .pi/ — a sibling path is refused by policy)
           1  git worktree add .pi/worktrees/loop -b loop-<date> <base>
           2  git submodule update --init app/src/main/cpp/libosmscout      (offline; objects already present)
           3  cp local.prop* .                                                 (SDK/NDK/Java paths)
           4  export VCPKG_ROOT=<repo>/vcpkg                                   (never a symlink)
           5  cp -a .pi/skills/openspec-* and the loop skill into $W/.pi/skills/
per item   git switch -c fix/issue-<N>-<slug> <base>     (asserted clean first)
per item   commit the fix, then the archive, with today's message shapes
run end    keep the worktree for the next run; report the branches
```

One branch per item off the run's base commit, so a later merge queue can land items independently;
one worktree per run, because the 11-minute cold build would otherwise be paid per item.
`--push yes` is the only thing that pushes, and this change opens no pull request.

| alternative | why rejected |
|---|---|
| worktree per item | pays the measured 11-minute cold build per item and re-seeds the submodule and `local.prop*` each time, for isolation the sequential run does not need. |
| work in the main tree (today's option when there is no WIP) | the loop's `git add -A` mixes with another change's WIP — the failure the recipe's step 1 exists to prevent, and one of the concept doc's landing-repair causes. |
| a sibling worktree outside the repo | refused by this machine's path policy, and it does not share the gitignored `.pi/` (no skills, no state). |

The wrapper also refuses to start an item when `main` has advanced past the run's base commit: a
stale base is how an item silently lands on a tree nobody reviewed. The Impact-overlap pre-check of
concept doc §4 step 2 is deferred.

**The worktree must stay mountable.** A future sandbox mounts the run's worktree as its workspace, so
D4's choices are deliberately the ones a mount can express: the worktree is a normal directory under
`.pi/worktrees/` (not a private clone), the base-commit check is a plain git query, and the reviewer's
read-only requirement is a **mode** the wrapper already needs (`--review-read-only`, exercised by the
self-test) so that a mount can later enforce structurally what a prompt enforces today. Recorded
constraint for the future change: `sbx`'s clone mode is rejected for a worktree other than the main
one, so a sandbox variant must use a direct mount of this path, not a private clone.

### D5 — State: machine-local, single writer, exit 3 is the authority

`tools/loop/state.sh` owns `.pi/issue-loop/run.json` (gitignored, machine-local, as today) and keeps
the existing contract: `next` exits 3 when the run may not start another iteration. The old
`.pi/bugfix-loop/run.json` is left alone; a run in flight must not be orphaned by a rename.

| alternative | why rejected |
|---|---|
| GitHub-native state (labels, a state comment, the budget as an issue) | survives any machine, but every write is a public API call, the budget becomes public noise, and the "one writer" rule would then span two systems. |
| a committed state file under `.pi/skills/` | two parallel runs would write the same tracked file — merge conflicts on the file that arbitrates the budget. |
| the wrapper holding the state in shell variables | no resume after an interrupt, and no mechanical answer to "may another item start". |

State is written **only between children**, never while a `pi` process runs (`SKILL.md` invariant 2).

### D6 — The pre-screen is mechanical, and says so about the part that is not

`tools/loop/prescreen.sh`, no LLM:

```
mechanical, applied here            the cited path/symbol still exists (git grep)      -> verdict stale
                                    class/feature dropped, status/in-flight dropped
                                    status/open-device -> needs-device (a device is a fact, not a judgement)
                                    ranking: bugs before improvements, then impact
judgement, reported by the worker   one root cause explains the symptom               -> needs-diagnosis
                                    exactly one fix follows, no decision owed         -> needs-decision
                                    a falsifiable WHEN/THEN that is false on HEAD     -> unspecifiable
                                    one capability, bounded files                     -> too-wide
```

The queue the worker receives is therefore *pre-filtered but not pre-judged*: the wrapper runs the
cheap sensors to avoid spending a child on a candidate a grep already refuted, and the worker owns
gates 2–5 exactly as today. Claiming the whole Phase B gate as deterministic would be false —
"exactly one fix follows" is not a property a script can decide — and a design that pretends
otherwise would silently drop items the owner could have decided.

| alternative | why rejected |
|---|---|
| an LLM pre-screen over the whole backlog (today's `scout`) | that is the cost this change removes: the backlog stops being one 100 k-token file, and the mechanical filters are exactly what a script does better. |
| no pre-screen, let the worker refuse | measured: without it 5 of 8 candidates were ineligible, each rejection costing ~10 min against a ~40 min analysis. |
| a fully deterministic gate | impossible for gates 2–5, and the attempt would convert "needs a decision" into "silently skipped", which is the failure the loop's report exists to prevent. |

### D7 — Labels are the wrapper's, through change 1's tooling

```
pick            + agent/in-progress                      (label removed on **every** exit path, incl. SIGINT)
skip: stale     + status/on-hold                          (on the same issue; no new issue)
skip: needs-*   + agent/<verdict> + status/on-hold
review FAIL→2   + agent/needs-decision + status/on-hold, recorded as blocked
close           − agent/in-progress, + status/fixed-by via update.sh, then close.sh --reason --evidence
```

Every write goes through `tools/issues/update.sh` / `close.sh`, so the reserved-label refusal and the
closed vocabulary apply to the loop as much as to a human. The model writes no label and no state.

| alternative | why rejected |
|---|---|
| let the worker child write its own labels | it would be the second writer in two systems at once — and it would put the loop's own state behind a prompt. |
| a GitHub Actions workflow reacting to label events | an event-driven second writer running concurrently with the wrapper is exactly the two-writer violation `SKILL.md` invariant 2 names. |
| a status comment instead of labels | a comment cannot be selected on, so the decision list and the in-progress list stop being one query. |

An `EXIT`/`SIGINT` trap removes `agent/in-progress` and records the item as `blocked`, so a killed run
leaves no issue looking in-progress, and the next run's prescreen does not skip it.

### D8 — Closure after review PASS, with the opt-out kept

`close.sh --reason "<one line of root cause>" --evidence <paths>` runs after the reviewer returns
PASS and after the change is archived. `--may-close no` leaves it to a human and marks the issue
`status/fixed-by` instead.

| alternative | why rejected |
|---|---|
| comment-only, never close (proposal S2's alternative) | safe, and rejected only because "done" would then mean two things — `closed` and `fixed-by` — and every run would end with a manual pass. |
| close with no evidence requirement | the reason a closure is auditable is the comment the close posts first; dropping it makes the tracker claim more than it can show. |
| close before archive | archive is the versioned half; closing on the writer's word is the failure `SKILL.md` Phase E's independent review exists to prevent. |

### D9 — The decision cap stops a run; it does not file issues

`--max-decisions N` (default 3). At the **start** of a run, if the open `agent/needs-decision` count
is ≥ N, the wrapper **refuses to start** (exit 5) and prints the decision list. During a run, a
`needs-decision` verdict is recorded on the issue it belongs to. No new issue is created for a
decision by this change.

| alternative | why rejected |
|---|---|
| one new `agent/needs-decision` issue per decision, with prepared A/B/C options (concept doc §4 step 4) | that is step 4 of the concept doc, and it is explicitly deferred: it needs an option-preview format, a WIP policy and a way to read the answer back, none of which the tracker tooling has yet. |
| no cap | the failure mode the concept doc names: the loop keeps producing work while nothing gets decided, and the queue becomes the bottleneck it was meant to remove. |
| a cap counted per run | the pile-up is across runs; a per-run count cannot see it. |

### D10 — The untrusted-content boundary sits at the file, not in a prompt sentence

The wrapper renders the work package with `WorkPackage.java` (change 1's bounded, delimited data
block) into the item's evidence directory and the prompt file *references* it, saying what it is. Issue
text is never interpolated into an instruction position and never reaches a shell unquoted.

| alternative | why rejected |
|---|---|
| pass the body inline in the prompt | the same text then sits in every prompt position the wrapper writes, and the boundary depends on the model reading a sentence correctly. |
| pass only the issue URL and let the child fetch it | the wrapper can no longer bound or sanitise what the child consumes, and the fetch happens inside the process under test. |
| sanitise the body | changing 1, D7: an unbounded filter that fails open and destroys the finding. |

### D11 — Metrics per item, recorded by the wrapper

Per item: wall time, `pi` exit status, review rounds, verdict, gate tally if the child reported one,
and whether the close happened. The report prints yield (`closed / skipped / blocked`) and the decision
list. Concept doc §6 asks a loop to justify itself with numbers within two weeks; the wrapper is the
only component that can observe them without trusting the model.

| alternative | why rejected |
|---|---|
| no metrics | the concept doc's value gate cannot be applied, and the loop becomes unfalsifiable. |
| metrics pushed to GitHub (a comment per run) | public noise for a private accounting question; the report is the interface. |
| token accounting | not observable from outside the process; wall time and exit status are the honest proxies here. |

### D12 — One run per repository

`state.sh` takes a lock (`.pi/issue-loop/lock`). A second `run.sh` refuses with exit 5.

| alternative | why rejected |
|---|---|
| parallel runs over one queue | needing to serialise is the one-writer rule at run scale, and the concept doc's parallelism (step 2) comes with a merge queue this change does not build. |
| no lock | two runs would both create worktrees, both switch branches in the same tree, and both write state. |

### D13 — A stub `pi` makes the wrapper testable offline

`tools/loop/fixtures/pi` records its argv and cwd and exits 0 with a canned report; the self-test drives
`run.sh` against the stub `gh` (change 1) and this stub `pi` in a **throwaway git repository** it
creates under the gitignored `.pi/tmp/`, where real `git worktree`, branch and commit behaviour is
exercised for real.

| alternative | why rejected |
|---|---|
| run the real `pi` in tests | slow, non-deterministic, costs tokens, and cannot assert what the wrapper passed. |
| stub git as well | the branch and worktree logic is the part being changed; stubbing the thing under test proves nothing. |
| test only the pure helpers | leaves the ordering (branch → label → work → commit → close) untested, which is where the two-writer and orphan-label bugs live. |

### D14 — The skill keeps its name; only its mechanics change

`.pi/skills/fix-loop/` stays `fix-loop`. Its rules, phases, invariants and gate stay; what it points
at changes (entry, state path, queue source, label transitions, Phase F bookkeeping).

| alternative | why rejected |
|---|---|
| rename to `agent-issue-loop` | `AGENTS.md`, archived changes, sessions and `TODO.md` reference the old name; a rename makes every one of them dangle for no gain. |
| keep both, one per tracker | two homes for one rule set is precisely `TODO.md` §172's "one normative home" problem, and it would drift on the first fix. |

### D15 — Sandbox-partitionable by construction, sandbox-implemented by nobody yet

The loop is designed so that a future agent sandbox (`sbx` / Docker Sandboxes) changes **where the
child runs** and nothing else. Five properties carry that, each already required by another decision,
so none of them is sandbox-specific work:

```
1  one injectable child command          LOOP_AGENT_CMD in loop.conf        (D2, D3)
2  git, labels, state, closure host-side the child holds no tracker credential (D7, D8)
3  the reviewer has a read-only mode     a mount can enforce it             (D3)
4  device work is host-side only         recorded as a device verdict       (D6)
5  evidence lands on the mounted tree    XMLs are archived from there       (invariant 1)
```

What is explicitly **not** done here: no `sbx` invocation, no kit or `sbxenv.yaml`, no image or
template, no secret registration, no network policy, no MCP-gateway registration, and no dependency on
`sbx` being installed. The wrapper's own tests must pass on a machine where `sbx` does not exist.

| alternative | why rejected |
|---|---|
| build the sandbox variant now | it cannot be validated before a working wrapper exists, and it adds an external dependency (`sbx`, a login, a per-machine network policy) the way `gh` arrived in `github-issue-tooling`. |
| add the seam anyway but leave it unexercised | a hook that no test calls is a hook that is wrong when it is first used; the self-test stubs it, so it is exercised every run. |
| design directly for the sandbox (hard-code `sbx exec`) | it makes an optional execution environment mandatory, and it breaks the wrapper on every machine that has no sandbox — including CI, which is where the self-test must run. |
| ignore the sandbox entirely | two decisions would have hardened into blockers: a bare `pi --print` call and a reviewer whose read-only property exists only in prose. |

### D16 — The wrapper is portable too: `loop.conf` is the adaptation point

The loop's engine (queue, worktree, phases, gate discipline, stop conditions, report) is portable; only
three things are this project's, and all three live in `tools/loop/loop.conf`:

```
LOOP_AGENT_CMD        how a child starts            pi --print   (or an sbx exec invocation later)
LOOP_WORKER_SKILLS    which of THIS project's skills the worker is told to use
                      openspec-apply-change, build-app, run-tests, revert-check
LOOP_GATE_CMD         how this project's gate is run  (empty for a project with no build)
budget defaults       items, minutes, review, may-close, max-decisions
```

Consequently `run.sh`, `prescreen.sh`, `worktree.sh`, `labels.sh` and `report.sh` must name **no**
skill, command, path or project value, and the prompt skeletons get those from `loop.conf` and the
project profile (`tools/tooling.json`, which is also where the allowlist lives). A project with no
Gradle has an empty `LOOP_GATE_CMD` and its phases still run — the gate is the project's, not the
loop's.

| alternative | why rejected |
|---|---|
| hard-code this project's gate and skills in the prompt skeletons | a second project would edit the loop's phases instead of a config file, and the phases are the part that should never be edited to port the tool. |
| a second `loop.conf` per project *and* a project profile | two adaptation files for one tool; the loop reads the profile for the allowlist and `loop.conf` for its own mechanics, which is the same "one profile + per-tool config" split as P2/D11. |
| make the phases configurable | the phases are the portable contract; what varies is which commands they call, and that is what the three keys carry. |

## Risks / Trade-offs

- **A wrapper that has never run is unproven** → the acceptance evidence is one real `--items 1` run
  on the live tracker, recorded with its report, branch and archived change. Until that exists this
  change is not archived.
- **A one-shot child cannot ask, and a denied tool would look like a quiet failure** → the wrapper never
  takes a child's word for closure; it re-reads `git status`, `git diff --stat`, the archived directory
  and the gate XMLs itself, and a child that produced a diff it did not commit is reported as blocked.
- **Per-item branches plus the base commit check leave the merge queue unbuilt** → `main` advancing
  mid-run makes the wrapper refuse the next item rather than build on a stale base; the impact-overlap
  pre-check and the landing step are concept doc §4 step 2 and are deferred, named in `Open Questions`.
- **The 11-minute cold build dominates a short run** → the run-scoped worktree pays it once; the
  report prints per-item wall time so a run that spent most of its budget on the build says so.
- **Two writers in one tree, still** → the wrapper is the only writer *and* writes only between
  children; the `pi` children are given the worktree but the wrapper commits, so a child that runs
  `git add` would be visible in the report as a status difference. Revert-check covers the ordering.
- **A killed run leaves a stale label** → the exit trap removes `agent/in-progress` and records
  `blocked`; the resume path (`state.sh next`) reads the same state, so the next run continues rather
  than restarting.
- **Gate 2–5 stay a model judgement, so the queue's quality is the child's** → the verdict is recorded
  per item and printed, so a run whose skips are all `needs-decision` is visible as a diagnosis of the
  backlog, not as a defect of the loop.
- **A seam nobody exercises is a seam that is wrong when it is first used** → the self-test stubs
  `LOOP_AGENT_CMD` rather than a hard-coded path, and a structural check asserts the wrapper names no
  agent command outside `loop.conf`. The seam is therefore on the tested path from the first commit
  (D15).
- **The sandbox variant could tempt a rewrite of the phases instead of a swap of the command** → D15
  states the five properties a sandbox consumes and the constraint that clone mode is unavailable for a
  non-main worktree, so the future change is a configuration plus an image, and the failure mode is
  named before it is reached.

## Migration Plan

1. `tools/loop/state.sh`, `prescreen.sh`, `worktree.sh`, `labels.sh`, `report.sh`, `run.sh`;
2. prompt skeletons + the `pi` stub + `tools/loop-selftest.sh` + one CI step;
3. port `.pi/skills/fix-loop/**` (entry, state path, queue source, Phase F, `AGENTS.md` row);
4. run the self-test green;
5. one real `--items 1 --minutes 90` run on the live tracker; record the report, the branch, the
   archived change and the issue's closure.

Rollback: revert `tools/loop/**`, `tools/loop-selftest.sh`, the skill edits and the `AGENTS.md` row;
delete `.pi/issue-loop/`. Branches created by the run stay, named in its report; an issue the run
closed can be reopened with its evidence comment intact. The old skill-driven start keeps working
throughout, because nothing is deleted until the port's edits land.

## Open Questions

Genuinely deferrable; none changes the approach or the task breakdown:

- **`sandbox-agent-execution`, the named future change** — run each child in an `sbx` / Docker Sandbox
  microVM instead of the host. It consumes D15's five properties as they are: it sets
  `LOOP_AGENT_CMD` to an `sbx exec` invocation, adds the kit / `sbxenv.yaml`, the image with the
  toolchain and a warm Gradle cache, the secret and network policies, the MCP-gateway registration for
  the `ctx_*` tools, a read-only mount for the reviewer, and the device routing. Two things must be
  verified inside it and cannot be answered from the docs today: whether the model provider's key can
  be supplied by the sandbox proxy (`sbx secret set-custom`) rather than as a guest-visible variable,
  and whether the Gradle gate works under the `Balanced` default-deny network policy or needs a
  per-host allow list. Neither is built, configured or depended on by this change.
- Concept doc §4 step 2: the Impact-overlap pre-check (two changes planning the same files) and the
  merge queue / landing script.
- Concept doc §4 step 4: the async decision queue — prepared A/B/C options, answer by label or comment,
  and the budget read from a config file for scheduled runs.
- Concept doc §3: the hotspot score script and the ratified size threshold in CI.
- The default of `--max-decisions` (3 is a guess with no measurement behind it; the first runs are what
  will set it).
